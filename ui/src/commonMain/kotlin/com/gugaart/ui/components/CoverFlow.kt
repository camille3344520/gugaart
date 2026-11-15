package com.gugaart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gugaart.ui.data.Framing
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.LocalMotionPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 海报流的数（docs/design-language.md §7）。 */
object CoverFlowSpec {
    /** 两侧的海报向后转的角度。 */
    const val SIDE_DEG = 54f

    /** demo 的弹簧：刚度 170、阻尼比 0.82。 */
    const val STIFFNESS = 170f
    const val DAMPING = 0.82f

    /** 两侧压暗。 */
    const val SIDE_DIM = 0.5f

    /** 第一张侧面的海报离正中多远（× 正中那张的宽）；再往外每张再加多少。 */
    const val FIRST_STEP = 0.66f
    const val NEXT_STEP = 0.30f

    /** 甩得越快越斜：速度（页 / 秒）× 这个系数 = 额外的度数，封顶 [FLING_TILT_MAX]。 */
    const val FLING_TILT = 5f
    const val FLING_TILT_MAX = 14f

    /** 手指在正中那张上移动时海报朝它倾的最大角度，和 logo 反向走的最大距离（dp）。 */
    const val PARALLAX_DEG = 7f
    const val PARALLAX_LOGO_DP = 7f

    /** 惯性：松手速度（页 / 秒）× 这个时间 = 多滑出去的页数。 */
    const val FLING_SECONDS = 0.22f
}

/** 一张海报在流里的姿势。[d] = 它的下标 − 翻页位置（0 = 正中，−1 = 左边第一张）。纯函数，有单测。 */
data class CoverPose(val translate: Float, val rotationY: Float, val dim: Float, val z: Float, val scale: Float)

fun coverPose(d: Float, velocity: Float = 0f): CoverPose {
    val a = abs(d)
    val near = a.coerceAtMost(1f)
    val far = (a - 1f).coerceAtLeast(0f)
    val t = sign(d) * (near * CoverFlowSpec.FIRST_STEP + far * CoverFlowSpec.NEXT_STEP)
    // 左边的海报（d < 0）内侧（右缘）退到后面
    val base = -sign(d) * near * CoverFlowSpec.SIDE_DEG
    val fling = (velocity * CoverFlowSpec.FLING_TILT).coerceIn(-CoverFlowSpec.FLING_TILT_MAX, CoverFlowSpec.FLING_TILT_MAX) * (1f - near)
    return CoverPose(
        translate = t,
        rotationY = (base + fling).let { if (it == 0f) 0f else -it },
        dim = near * CoverFlowSpec.SIDE_DIM + (far * 0.18f).coerceAtMost(0.3f),
        z = -a,
        scale = 1f - near * 0.12f,
    )
}

/** 松手后吸附到哪一张：现在的位置 + 惯性滑出去的量，取整，夹在范围里；一次最多翻 [maxJump] 张。 */
fun coverSnapTarget(position: Float, velocityPagesPerSec: Float, count: Int, maxJump: Int = 3): Int {
    if (count <= 0) return 0
    val from = position.roundToInt()
    val projected = (position + velocityPagesPerSec * CoverFlowSpec.FLING_SECONDS).roundToInt()
    return projected.coerceIn(from - maxJump, from + maxJump).coerceIn(0, count - 1)
}

/**
 * 海报流的状态：一个连续的翻页位置驱动全部（海报的姿势、期号里程表、专色、背后的整幅剧照）。
 * [settled] = 吸附到的那一张（翻过半张就换：导语、按钮、手记跟着它换）。
 */
@Stable
class CoverFlowState internal constructor(private val scope: CoroutineScope, initial: Int) {
    internal val anim = Animatable(initial.toFloat())
    var count by mutableStateOf(0)
        internal set

    /** 连续的翻页位置，0 起。 */
    val position: Float get() = anim.value

    /** 现在算哪一张（过半就换）。 */
    val settled: Int get() = position.roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))

    /** 松手时的速度（页 / 秒）；静止 = 0。 */
    var velocity by mutableStateOf(0f)
        internal set
    var dragging by mutableStateOf(false)
        internal set

    /** 手指在正中那张上的位置（−1..1）；没按着 = 0。海报朝它倾，logo 反向走。 */
    var pointer by mutableStateOf(Offset.Zero)
        internal set

    internal var reduced = false

    fun animateTo(page: Int) {
        scope.launch { settle(page.coerceIn(0, (count - 1).coerceAtLeast(0)).toFloat(), 0f) }
    }

    internal suspend fun settle(target: Float, initialVelocity: Float) {
        if (reduced) anim.snapTo(target) else anim.animateTo(target, spring(CoverFlowSpec.DAMPING, CoverFlowSpec.STIFFNESS), initialVelocity) { this@CoverFlowState.velocity = this.velocity }
        velocity = 0f
    }
}

@Composable
fun rememberCoverFlowState(count: Int, initial: Int = 0): CoverFlowState {
    val scope = rememberCoroutineScope()
    val state = remember { CoverFlowState(scope, initial.coerceIn(0, (count - 1).coerceAtLeast(0))) }
    state.count = count
    state.reduced = LocalMotionPrefs.current.reducedMotion
    return state
}

/**
 * 三维海报流（首页封面）。正中一张正对、清楚；两侧各两张向后转 54°、压暗；直角、细米白边，没有圆角。
 * - 跟手：按住左右拖，海报连续移动；松手带惯性吸附；拖得越快甩得越斜。
 * - 视差：手指在正中那张上移动，海报朝它倾并有一道高光（[CoverFlowState.pointer] 也给片名 logo 用，反向走）。
 * - 只有 1 张：不能拖，两侧不画。加载中（[posters] 里是 null）：斜纹空版。
 * - 电视：[draggable] = false（左右键翻，由调用方 `animateTo`），没有视差。
 *
 * [card]：画一张海报的内容（盖满自己的盒子）；[cardWidth] × 1.5 是正中那张的尺寸。
 */
@Composable
fun CoverFlow(
    state: CoverFlowState,
    posters: List<String?>,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 220.dp,
    draggable: Boolean = true,
    names: List<String> = emptyList(),
    onClick: (Int) -> Unit = {},
) {
    val colors = Guga.colors
    val density = LocalDensity.current
    val cardH = cardWidth * 1.5f
    val widthPx = with(density) { cardWidth.toPx() }
    val stepPx = widthPx * CoverFlowSpec.FIRST_STEP
    val scope = rememberCoroutineScope()
    val n = posters.size
    Box(
        modifier
            .fillMaxWidth()
            .height(cardH)
            .semantics {
                contentDescription = "封面 ${state.settled + 1} / $n" + (names.getOrNull(state.settled)?.let { " · $it" } ?: "")
                customActions = listOf(
                    CustomAccessibilityAction("下一部") { state.animateTo(state.settled + 1); true },
                    CustomAccessibilityAction("上一部") { state.animateTo(state.settled - 1); true },
                )
            }
            .testTag("cover-flow")
            .then(
                if (!draggable || n <= 1) Modifier else Modifier.pointerInput(n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        // 手指落在正中那张上：视差
                        val cx = size.width / 2f
                        fun aim(p: Offset) {
                            val dx = ((p.x - cx) / (widthPx / 2f)).coerceIn(-1f, 1f)
                            val dy = ((p.y - size.height / 2f) / (size.height / 2f)).coerceIn(-1f, 1f)
                            state.pointer = if (abs(p.x - cx) <= widthPx / 2f) Offset(dx, dy) else Offset.Zero
                        }
                        aim(down.position)
                        var moved = false
                        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            moved = true
                            state.dragging = true
                            scope.launch { state.anim.snapTo((state.anim.value - over / stepPx).coerceIn(-0.35f, n - 1 + 0.35f)) }
                        }
                        if (slop != null) {
                            state.pointer = Offset.Zero
                            horizontalDrag(slop.id) { change ->
                                val dx = change.positionChange().x
                                tracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                                scope.launch { state.anim.snapTo((state.anim.value - dx / stepPx).coerceIn(-0.35f, n - 1 + 0.35f)) }
                                state.velocity = -tracker.calculateVelocity().x / stepPx
                            }
                        }
                        state.pointer = Offset.Zero
                        state.dragging = false
                        if (moved) {
                            val v = -tracker.calculateVelocity().x / stepPx
                            val target = coverSnapTarget(state.position, v, n)
                            scope.launch { state.settle(target.toFloat(), v) }
                        } else if (isTap(down.id, down.position)) {
                            // 没拖：点的是哪一张。上下滑（被页面的竖向滚动接走了）不算点——用户 2026-11-15：「安卓首页没办法下滑了」：
                            // 手指按在海报上往上滑，页面滚了，松手时这里却当成点了一下，打开了详情 / 翻了一期
                            val tapAt = down.position.x - cx
                            val idx = when {
                                abs(tapAt) <= widthPx / 2f -> state.settled
                                tapAt < 0 -> state.settled - 1
                                else -> state.settled + 1
                            }
                            if (idx == state.settled) onClick(idx) else if (idx in 0 until n) state.animateTo(idx)
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 海报背后一小片专色的晕（纸还是墨黑的，不整屏染色）
        Box(
            Modifier.size(cardWidth * 1.7f, cardH * 1.15f).drawBehind {
                drawRect(Brush.radialGradient(listOf(colors.spot.copy(alpha = 0.20f), Color.Transparent), center, size.minDimension * 0.62f))
            },
        )
        val pos = state.position
        for (i in posters.indices) {
            val d = i - pos
            if (abs(d) > 2.6f) continue
            val pose = coverPose(d, if (state.reduced) 0f else state.velocity)
            val center = abs(d) < 0.5f
            Box(
                Modifier
                    .zIndex(pose.z)
                    .offset { androidx.compose.ui.unit.IntOffset((pose.translate * widthPx).roundToInt(), 0) }
                    .graphicsLayer {
                        cameraDistance = 14f * density.density
                        rotationY = pose.rotationY + if (center && !state.reduced) state.pointer.x * CoverFlowSpec.PARALLAX_DEG else 0f
                        rotationX = if (center && !state.reduced) -state.pointer.y * CoverFlowSpec.PARALLAX_DEG * 0.6f else 0f
                        scaleX = pose.scale
                        scaleY = pose.scale
                    }
                    .size(cardWidth, cardH)
                    .border(1.dp, colors.ink.copy(alpha = 0.34f))
                    .drawWithContent {
                        drawContent()
                        if (pose.dim > 0f) drawRect(colors.paper.copy(alpha = pose.dim))
                        // 高光：手指在哪，哪边亮一道
                        if (center && state.pointer != Offset.Zero) {
                            val x = size.width * (0.5f + state.pointer.x * 0.5f)
                            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent), x - size.width * 0.45f, x + size.width * 0.45f))
                        }
                    }
                    .testTag(if (center) "cover-center" else "cover-side"),
            ) {
                CoverImage(posters[i], Modifier.matchParentSize(), Framing(0.5f, 0.5f)) {
                    HatchPlate(names.getOrNull(i)?.take(4).orEmpty(), Modifier.matchParentSize())
                }
            }
        }
    }
}

/** 期号指示：海报流下一排短线，当前一段专色。 */
@Composable
fun PageDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    if (total <= 1) return
    val colors = Guga.colors
    Canvas(modifier.size((total * 24).dp, 3.dp).testTag("page-dots")) {
        val seg = 18.dp.toPx()
        val gap = 6.dp.toPx()
        val w = total * seg + (total - 1) * gap
        var x = (size.width - w) / 2
        for (i in 0 until total) {
            drawRect(if (i == current) colors.spot else colors.faint, Offset(x, 0f), androidx.compose.ui.geometry.Size(seg, size.height))
            x += seg + gap
        }
    }
}

/**
 * 这一下是不是「点」：没被别人接走（页面的竖向滚动会把上下滑接走并消费掉）、离按下的地方不超过触摸阈值。
 * 横向拖动在调用之前已经走了拖动分支。
 */
private fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.isTap(id: androidx.compose.ui.input.pointer.PointerId, down: Offset): Boolean {
    val c = currentEvent.changes.firstOrNull { it.id == id } ?: return false
    if (c.isConsumed) return false
    return (c.position - down).getDistance() <= viewConfiguration.touchSlop
}
