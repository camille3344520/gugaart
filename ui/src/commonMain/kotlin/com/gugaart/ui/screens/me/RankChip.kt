package com.gugaart.ui.screens.me

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.delay

/** 报头上那枚称号的几个数。 */
object RankChipSpec {
    /** 「+1 部」停留多久。 */
    const val GAIN_MS = 4_000L
}

/**
 * 报头右端的称号（用户 2026-11-15：「放主页展示合适吗？不是没有互动性」→ 方案「都按你的来」）：徽章 + 称号，
 * 下面一道细进度线（离下一级走了多少）。展示哪个称号 = [RankBoard.featured]：每次打开应用从最近拿到的三个里随机一个
 * （「主页的称号重新打开软件时随机展示最近新获得的3个称号之一」），可能是等级称号，也可能是特别称号（盾形章）。看完一部回来、部数涨了：进度线往前走，旁边浮出「+1 部」几秒。
 * 手机 / 电脑点它 = 称号纸片（十级都在上面）；电视上不进焦点（只是看的，详情在「我的」）。
 * 部数还没数出来（刚装、还没登录）→ 画 [fallback]（原来那一格的日期），不空着。
 */
@Composable
fun RankChip(fallback: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier) {
    val count = RankBoard.count
    if (count == null) {
        BasicText(fallback, modifier, style = style, maxLines = 1)
        return
    }
    val tv = Guga.uiMode == UiMode.Tv
    val colors = Guga.colors
    val level = Ranks.levelOf(count)
    val featured = RankBoard.featured
    val specialId = featured?.let(::specialOfKey)
    val special = specialId?.let(Specials::of)
    // 等级称号：挑中的那一级（不会比现在这一级高）；没挑 = 现在这一级
    val shownLevel = featured?.let(::levelOfKey)?.takeIf { it in 1..level } ?: level
    val rank = Ranks.of(shownLevel)
    val title = special?.title ?: rank?.title ?: "还没有称号"
    val badge: Dp = if (tv) 40.dp else 24.dp
    val progress by animateFloatAsState(Ranks.progress(count), tween(1200), label = "rank-progress")
    // 「+N 部」：部数涨了的那一次浮出来，几秒后淡掉
    var gainShown by remember { mutableStateOf(0) }
    val gain = RankBoard.gained
    LaunchedEffect(RankBoard.gainToken) {
        if (gain > 0) {
            gainShown = gain
            delay(RankChipSpec.GAIN_MS)
            gainShown = 0
        }
    }
    val gainAlpha by animateFloatAsState(if (gainShown > 0) 1f else 0f, tween(400), label = "rank-gain")
    val press = rememberPressState()
    val label = if (special != null) "特别称号 ${special.title}，看过 $count 部" else "称号 ${Ranks.badge(count)}，看过 $count 部"
    val click = if (tv) Modifier else Modifier.pressGraphics(press).pressable(press, label = label, onClick = {
        if (special != null) RankBoard.special = SpecialNews(listOf(special.id), browse = true) else RankBoard.open(RankNews(level, count, browse = true))
    })
    Row(modifier.heightIn(min = if (tv) 48.dp else 32.dp).then(click).testTag("rank-chip"), verticalAlignment = Alignment.CenterVertically) {
        if (gainAlpha > 0f) {
            BasicText("+${gainShown.coerceAtLeast(1)} 部", Modifier.padding(end = 6.dp).alpha(gainAlpha).testTag("rank-gain"), style = style.copy(color = colors.spot, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        if (special != null) SpecialBadge(special.id, badge) else RankBadge(shownLevel, badge)
        Column(Modifier.padding(start = if (tv) 10.dp else 6.dp)) {
            BasicText(title, Modifier.testTag("rank-chip-title"), style = style.copy(color = colors.ink, letterSpacing = 0.08.em), maxLines = 1)
            // 进度线：和称号一样宽
            Box(Modifier.padding(top = 3.dp).width(textWidthOf(style.fontSize, title.length)).height(if (tv) 3.dp else 2.dp).background(colors.line)) {
                Box(Modifier.fillMaxWidth(progress).height(if (tv) 3.dp else 2.dp).background(colors.spot))
            }
        }
    }
}

/** 汉字一个字大约一个字号宽（加上 0.08 em 字距）：进度线的宽度。 */
private fun textWidthOf(size: TextUnit, chars: Int): Dp = (size.value * 1.08f * chars).dp
