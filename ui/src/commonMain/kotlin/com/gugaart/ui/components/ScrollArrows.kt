package com.gugaart.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gugaart.ui.desktop.hover
import com.gugaart.ui.desktop.rememberHover
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.launch

/**
 * 横排两头的翻页箭头（电脑：用户 2026-11-16「演员超过数量的时候没办法查看右边的演员了」「剧照也是一样的」）。
 * 右边还有才出「→」、翻过去左边才出「←」，点一下翻 [pagePx]；Shift + 滚轮 / 触控板照旧能横着滑。手机靠手指滑，不用它。
 * [top] = 箭头上沿离这一排顶上多远（一般立在图片的竖直中间）；[inset] = 离两边多远。
 */
@Composable
fun BoxScope.ScrollArrows(scroll: ScrollState, pagePx: Float, top: Dp, inset: Dp, size: Dp, tag: String) {
    val scope = rememberCoroutineScope()
    if (scroll.canScrollBackward) PageArrowButton(GuguIcon.ChevronLeft, "往左翻", size, Modifier.align(Alignment.TopStart).offset(x = inset, y = top).testTag("$tag-prev")) {
        scope.launch { scroll.animateScrollBy(-pagePx) }
    }
    if (scroll.canScrollForward) PageArrowButton(GuguIcon.ChevronRight, "往右翻", size, Modifier.align(Alignment.TopEnd).offset(x = -inset, y = top).testTag("$tag-next")) {
        scope.launch { scroll.animateScrollBy(pagePx) }
    }
}

/** 一个翻页箭头：墨底方块 + 细框，悬停亮一点，按下放大（统一的按压物理）。 */
@Composable
private fun PageArrowButton(icon: GuguIcon, label: String, size: Dp, modifier: Modifier, onClick: () -> Unit) {
    val colors = Guga.colors
    val hover = rememberHover()
    val press = rememberPressState()
    Box(
        modifier.size(size).hover(hover).pressGraphics(press)
            .background(colors.inkWell.copy(alpha = if (hover.over) 0.95f else 0.82f))
            .border(1.dp, colors.ink.copy(alpha = if (hover.over) 0.8f else 0.45f))
            .pressable(press, label = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, colors.ink, size * 0.5f)
    }
}
