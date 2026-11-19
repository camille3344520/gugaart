package com.gugaart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 左边缘右滑返回（M4-2，design/ios/notes.md §1 第 5 条；只在 `PhoneHost.edgeBack` 时装）。
 *
 * 跟手：当前页随手指平移；底下那一页（拿得到时）从 −30 % 跟回来、压暗 45 % 退去。松手过 1/3 或甩得快 → 走完、调 [onBack]（上游的返回）；
 * 否则弹回、路由不变。没拖（点按）不吃事件，照常传给页面。**不画投影**（D-lead-22：系统淡影也不开）。
 */
@Stable
class EdgeBackState {
    /** 当前页平移了多少（px）；0 = 没在拖。 */
    val offset = Animatable(0f)

    /** 页宽（px），由 [edgeBackGesture] 量到。 */
    var width = 1f
        internal set

    /** 拖动的进度（0 … 1）。 */
    val fraction: Float get() = (offset.value / width).coerceIn(0f, 1f)

    /** 正在拖或在回弹 / 走完的动画里：底下那一页该露出来。 */
    val active: Boolean get() = offset.value > 0f

    companion object {
        /** 左缘多宽的一条接手势。 */
        val EDGE = 22.dp

        /** 过了宽度的这一份就算返回。 */
        const val COMMIT_FRACTION = 1f / 3f

        /** 甩得比这个快（px / 秒，按 dp 换算前是 dp / 秒）也算返回。 */
        const val FLING_DP_PER_S = 800f

        /** 底下那一页的视差：从 −30 % 跟回来。 */
        const val UNDER_PARALLAX = 0.3f

        /** 底下那一页的压暗：开始时 45 % 纸色，拖完是 0。 */
        const val UNDER_DIM = 0.45f

        const val SETTLE_MS = 220

        /** 松手时走完还是弹回。纯函数，有单测。 */
        fun commits(offsetPx: Float, widthPx: Float, velocityPxPerS: Float, density: Float): Boolean =
            offsetPx >= widthPx * COMMIT_FRACTION || (offsetPx > 0f && velocityPxPerS >= FLING_DP_PER_S * density)

        /** 底下那一页的平移（px）：拖到 [offsetPx] 时在 −30 % × (宽 − 拖距)。 */
        fun underOffset(offsetPx: Float, widthPx: Float): Float = -UNDER_PARALLAX * (widthPx - offsetPx).coerceAtLeast(0f)
    }
}

/**
 * 装在页面区的外层：从左缘 [EdgeBackState.EDGE] 以内按下、横着拖过触摸阈值才接手（之后的事件吃掉，页面的纵向滚动收不到）；
 * 别的触摸一概不碰。[enabled] = false 时什么都不做（安卓、没有上一页、在播放器里）。
 */
fun Modifier.edgeBackGesture(state: EdgeBackState, enabled: Boolean, scope: CoroutineScope, onBack: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(state, onBack) {
        val edge = EdgeBackState.EDGE.toPx()
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.width = size.width.toFloat().coerceAtLeast(1f)
            if (down.position.x > edge) return@awaitEachGesture
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                tracker.addPosition(ch.uptimeMillis, ch.position)
                val dx = ch.position.x - down.position.x
                val dy = ch.position.y - down.position.y
                if (!dragging) {
                    if (!ch.pressed) break
                    // 横着走过阈值才算（竖着先走的是页面的滚动）
                    if (dx > slop && dx > kotlin.math.abs(dy)) dragging = true else if (kotlin.math.abs(dy) > slop) break
                }
                if (dragging) {
                    ch.consume()
                    if (!ch.pressed) {
                        val v = tracker.calculateVelocity().x
                        val go = EdgeBackState.commits(state.offset.value, state.width, v, density)
                        scope.launch {
                            if (go) {
                                state.offset.animateTo(state.width, tween(EdgeBackState.SETTLE_MS))
                                onBack()
                                state.offset.snapTo(0f)
                            } else {
                                state.offset.animateTo(0f, tween(EdgeBackState.SETTLE_MS))
                            }
                        }
                        break
                    }
                    scope.launch { state.offset.snapTo(dx.coerceIn(0f, state.width)) }
                }
            }
        }
    }
