package com.gugaart.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.gugaart.ui.idle.LocalIdleClock
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.NoteVoice
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.settings.LocalGugaSettings
import com.gugugaga.ds.physics.LocalMotionPrefs
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.delay

/*
 * 「目录在下面」（用户 2026-11-15：「开屏左下角的目录会不会不够醒目，初次使用的用户可能观察不到」）。
 *
 * 两层：
 * 1. 一直都在的：目录的入口本身做醒目（电脑页脚的「目录」两个字加大加黑、带一个朝下的箭头，栏目名从灰字改成正文色；
 *    手机封面的期号指示旁边多一个「目录 ↓」；电视封面流下面写「▼ 按下键看目录」）。
 * 2. 只有第一次的：那个入口换成反白的小纸签、箭头上下轻轻动，她在主编手记那一格说一句目录在哪。
 *    用户往下翻过一次（或者点过入口）就算看过了，记在 `gugaart.settings.v1` 的 `tocHintSeen`，以后不再出现。
 */

/** 第一次的提示现在要不要出现；[seen] = 用户已经翻到目录了，记下来。 */
class TocHint(val show: Boolean, val seen: () -> Unit)

/** 预置状态（截图 / 测试）：`ScreenPreset(state = "tochint")` = 第一次的样子。 */
const val TOC_HINT_STATE = "tochint"

/** 她在手记那一格说的那一句（每端按自己的操作方式说）。 */
fun tocHintLine(mode: UiMode): String = when (mode) {
    UiMode.Phone -> "头一回来？往下就是片单，续看的都排在那儿。"
    UiMode.Desktop -> "头一回来？滚一下滚轮就是片单，点页脚那一排也行。"
    UiMode.Tv -> "头一回来？按遥控器的下键，下面就是片单。"
}

/**
 * 首页用：第一次的提示。没有画报设置的 store（离屏测试）时不出现——除非预置状态点名要（[TOC_HINT_STATE]）。
 * 出现时过一会儿让她说一句（[voice]）。
 */
@Composable
fun rememberTocHint(mode: UiMode, voice: NoteVoice): TocHint {
    val store = LocalGugaSettings.current
    val forced = LocalPreset.current.state == TOC_HINT_STATE
    val show = forced || (store != null && !LocalGugaPrefs.current.tocHintSeen)
    LaunchedEffect(show) {
        if (show) {
            if (!forced) delay(1_200)
            voice.say(tocHintLine(mode))
        }
    }
    return remember(show, store) { TocHint(show) { if (show) store?.update { it.copy(tocHintSeen = true) } } }
}

/**
 * 手机封面上的「目录 ↓」（期号指示的右边）：点一下滚到目录。[first] = 第一次：反白的小纸签，多写「往下翻」，箭头会动。
 */
@Composable
fun TocJump(first: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    val bob = tocBob(first)
    val ink = if (first) colors.onInk else colors.ink
    Row(
        modifier
            .pressGraphics(press)
            .pressable(press, label = "片单", onClick = onClick)
            .then(if (first) Modifier.background(colors.ink) else Modifier)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("home-toc-jump"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // 第一次：反白纸签 + 箭头轻动就够醒目。不加「往下翻 ·」：加了会盖住期号指示的最后一格（8 期，手机 412dp 宽，截图实测）
        BasicText("片单", style = Guga.song(type.caption, FontWeight.Black, ink), maxLines = 1)
        BasicText("↓", Modifier.graphicsLayer { translationY = 4.dp.toPx() * bob() }, style = Guga.song(type.caption, FontWeight.Black, if (first) ink else colors.spot), maxLines = 1)
    }
}

/**
 * 箭头上下轻轻动的那一点位移（0..1 来回），返回一个**在绘制层读**的函数：调用方放进 `graphicsLayer { translationY = … }`，
 * 动画只让箭头自己那一层重画，不在组合期读值、不带整页重组（1.5.1 m151：以前整页玻璃跟着每帧重拍 + 预模糊，前台空闲 42–48 % CPU）。
 * **跳 [BOB_TIMES] 次就停在原位**（不再无限要帧）。只在应用里动（有 [LocalIdleClock]）；离屏测试、减弱动效、电视掉帧降级时停在 0——画面是定死的。
 * 窗口不在前台 / 最小化 / 应用在后台 / 首页被盖住时也停；回到前台重新跳三下。
 */
@Composable
fun tocBob(active: Boolean): () -> Float {
    // 首页被别的页盖住（手机首页留在组合里不拆）时也不动：看不见的动画会一直要帧
    if (!active || LocalIdleClock.current == null || LocalMotionPrefs.current.reducedMotion || !com.gugaart.ui.idle.LocalPageShown.current || com.gugaart.ui.tv.TvPerfGovernor.degraded) return BOB_REST
    if (!androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused) return BOB_REST
    val anim = remember { Animatable(0f) }
    LaunchedEffect(anim) {
        repeat(BOB_TIMES) {
            anim.animateTo(1f, tween(BOB_MS, easing = FastOutSlowInEasing))
            anim.animateTo(0f, tween(BOB_MS, easing = FastOutSlowInEasing))
        }
    }
    return { anim.value }
}

/** 跳三下就停（协调人 2026-11-27：无限动画带着玻璃整页重拍，前台空闲烧掉近半个核）。 */
const val BOB_TIMES = 3
private const val BOB_MS = 700
private val BOB_REST: () -> Float = { 0f }

/**
 * 隔离重组的晃动箭头（电视首页用）：只在这个小组件自身的绘制层（`graphicsLayer`）读动画进度，不触发首页父容器重组和 relayout。
 * 动法同 [tocBob]：跳三下就停。
 */
@Composable
fun TocBobGlyph(glyph: String, active: Boolean, maxOffset: androidx.compose.ui.unit.Dp, style: androidx.compose.ui.text.TextStyle) {
    val bob = tocBob(active)
    BasicText(glyph, Modifier.graphicsLayer { translationY = maxOffset.toPx() * bob() }, style = style, maxLines = 1)
}
