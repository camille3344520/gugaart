package com.gugaart.ui.chrome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.glass.GlassModel
import com.gugugaga.ds.glass.MaterialName
import com.gugugaga.ds.glass.glass
import com.gugugaga.ds.glass.glassThemeHere
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.IndicatorMath
import com.gugugaga.ds.physics.LocalMotionPrefs
import com.gugugaga.ds.physics.PressConfig
import com.gugugaga.ds.physics.PressKind
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.physics.rememberSlidingIndicator
import com.gugugaga.ds.theme.LocalOnGlass
import kotlin.math.abs
import kotlin.math.roundToInt

/** 手机底部的四个标签。「片库」「我的」在上游还没有路由（ADR 0002 §7.4）：是标签页的本地状态，不进返回栈。 */
enum class PhoneTab(val label: String) { Home("首页"), Discover("发现"), Library("片库"), Me("我的") }

/** 标签栏的数（docs/design-language.md §3.1）：292 × 56 四格 + 搜索圆钮 56，离底 18。 */
object PhoneTabBarSpec {
    val WIDTH = 292.dp
    val HEIGHT = 56.dp
    val SEARCH = 56.dp
    val GAP = 12.dp
    val BOTTOM = 18.dp

    /** 往回滚这么多就回来。 */
    val RETURN_DP = 8.dp
    const val SINK_MS = 320
}

/**
 * 往下读时标签栏下沉（docs/platforms.md §1）：向下滚 → 沉；往回滚 8 dp → 回来；滚到底 → 回来。纯函数，有单测。
 * [was] 上一次的结果；[anchor] 上一次变向时的滚动位置（px）。返回 (沉不沉, 新的 anchor)。
 */
fun tabBarSunk(was: Boolean, anchor: Float, scroll: Float, max: Float, returnPx: Float): Pair<Boolean, Float> {
    if (scroll <= 2f || (max > 0f && scroll >= max - 1f)) return false to scroll
    return if (was) {
        // 沉着：记住滚到过的最深处，往回超过 8 dp 才回来
        val deepest = maxOf(anchor, scroll)
        if (deepest - scroll >= returnPx) false to scroll else true to deepest
    } else {
        // 露着：记住最浅处，往下超过一点就沉
        val shallowest = minOf(anchor, scroll)
        if (scroll - shallowest >= returnPx) true to scroll else false to shallowest
    }
}

/**
 * 手机的固定导航：一条四格的玻璃标签栏 + 旁边一个搜索圆钮。**玻璃是上游的**（`Modifier.glass` 的 `Bar` / `Clear` 材质、透镜 `GlassModel.restingLens`、
 * 透镜的滑动弹簧 `SlidingIndicator`）；这里只排字和记号：
 * - 字：宋体、正文色；选中 = **加粗 + 专色小圆点**（decisions Q11，有意偏离上游旧规范的「图标蓝」），不靠颜色单独表达；
 * - 透镜：选中的那一格是一块透镜；按住拖，透镜跟手，松手落到哪格就去哪格；
 * - [sunk]：往下读时和搜索圆钮一起沉到屏幕外。
 */
@Composable
fun PhoneTabBar(active: PhoneTab?, onSelect: (PhoneTab) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier, sunk: Boolean = false, searchSelected: Boolean = false) {
    val colors = Guga.colors
    val density = LocalDensity.current
    val reduced = LocalMotionPrefs.current.reducedMotion
    val tabs = PhoneTab.entries
    val index = active?.let { tabs.indexOf(it) } ?: -1
    val ind = rememberSlidingIndicator(index.coerceAtLeast(0).toFloat(), tabs.size)
    LaunchedEffect(index) { if (index >= 0 && ind.targetValue != index.toFloat()) ind.animateTo(index.toFloat()) }
    val sink by animateFloatAsState(if (sunk) 1f else 0f, tween(if (reduced) 0 else PhoneTabBarSpec.SINK_MS), label = "sink")
    val travel = with(density) { (PhoneTabBarSpec.HEIGHT + PhoneTabBarSpec.BOTTOM + 24.dp).toPx() }
    val onSelectNow = rememberUpdatedState(onSelect)
    val indexNow = rememberUpdatedState(index)
    Row(
        modifier.padding(bottom = PhoneTabBarSpec.BOTTOM).graphicsLayer { translationY = sink * travel }.testTag(if (sunk) "tab-bar-sunk" else "tab-bar"),
        horizontalArrangement = Arrangement.spacedBy(PhoneTabBarSpec.GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val barPress = rememberPressState(PressKind.TabBar)
        Box(
            Modifier
                .width(PhoneTabBarSpec.WIDTH)
                .height(PhoneTabBarSpec.HEIGHT)
                .glass(MaterialName.Bar, 999.dp, barPress)
                .padding(4.dp),
        ) {
            BoxWithConstraints(
                Modifier.fillMaxSize().pointerInput(tabs.size, ind) {
                    val slop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val tabPx = size.width.toFloat() / tabs.size
                        val last = (tabs.size - 1).toFloat()
                        ind.press()
                        var dragging = false
                        var value = ind.targetValue
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            if (!dragging && abs(ch.position.x - down.position.x) > slop) dragging = true
                            if (dragging) {
                                value = (ch.position.x / tabPx - 0.5f).coerceIn(0f, last)
                                ind.animateTo(value)
                                ch.consume()
                            }
                        }
                        ind.release()
                        if (dragging) {
                            val target = value.roundToInt().coerceIn(0, tabs.size - 1)
                            ind.animateTo(target.toFloat())
                            if (target != indexNow.value) onSelectNow.value(tabs[target])
                        }
                    }
                },
            ) {
                val tabW = maxWidth / tabs.size
                val expandX = with(density) { PressConfig.INDICATOR_EXPAND_X_DP.dp.toPx() }
                val expandY = with(density) { PressConfig.INDICATOR_EXPAND_Y_DP.dp.toPx() }
                // 透镜：选中的那一格。位置由布局给（不是图层位移），图层只带抬起 / 果冻的缩放
                Box(
                    Modifier
                        .offset { IntOffset((tabW * ind.value).roundToPx(), 0) }
                        .graphicsLayer {
                            ind.applyTo(this, expandX, expandY)
                            alpha = if (index >= 0) 1f else 0f
                        }
                        .width(tabW)
                        .fillMaxHeight()
                        .glass(GlassModel.restingLens(glassThemeHere()), 999.dp)
                        .testTag("tab-lens"),
                )
                Row(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalOnGlass provides true) {
                        for ((i, tab) in tabs.withIndex()) {
                            val on = i == index
                            val press = rememberPressState()
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .graphicsLayer {
                                        val s = IndicatorMath.magnification(ind.lift, IndicatorMath.proximity(ind.value - i))
                                        scaleX = s
                                        scaleY = s
                                    }
                                    .semantics { selected = on }
                                    .pressable(press, role = Role.Tab, label = tab.label, focusable = false) { if (!on) onSelect(tab) }
                                    .testTag(if (on) "tab-on" else "tab-off"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    BasicText(tab.label, style = Guga.song(15.sp, if (on) FontWeight.Black else FontWeight.Medium, colors.ink.copy(alpha = if (on) 1f else 0.82f)), maxLines = 1)
                                    // 选中记号：专色小圆点（没选的格子留同样的高度，字不跳）
                                    Box(Modifier.padding(top = 3.dp).size(4.dp).then(if (on) Modifier.background(colors.spot, CircleShape) else Modifier))
                                }
                            }
                        }
                    }
                }
            }
        }
        val searchPress = rememberPressState()
        Box(
            Modifier
                .size(PhoneTabBarSpec.SEARCH)
                .glass(MaterialName.Clear, 999.dp, searchPress)
                .pressable(searchPress, label = "搜索", onClick = onSearch)
                .testTag("tab-search"),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalOnGlass provides true) {
                Icon(GuguIcon.Search, colors.ink, 22.dp, strokeWidth = if (searchSelected) 2.6f else 2f)
            }
        }
    }
}
