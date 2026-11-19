package com.gugaart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaFonts
import com.gugaart.ui.theme.feel
import com.gugugaga.ds.physics.LocalMotionPrefs

/** 落章的参数（docs/design-language.md §8）：420 ms，`cubic-bezier(.2, 1.5, .4, 1)`；2.6 倍 +8° → 0.9 倍 −9°（55 %）→ 1 倍 −7°。 */
object SealMotion {
    const val STAMP_MS = 420
    const val FADE_MS = 350
    const val REST_DEG = -7f
    val EASE = CubicBezierEasing(0.2f, 1.5f, 0.4f, 1f)

    /** 进度 [t]（0..1，线性时间）→ (缩放, 角度, 不透明度)。纯函数，有单测。 */
    fun pose(t: Float): Triple<Float, Float, Float> {
        val p = t.coerceIn(0f, 1f)
        return if (p < 0.55f) {
            val k = p / 0.55f
            Triple(2.6f + (0.9f - 2.6f) * k, 8f + (-9f - 8f) * k, k.coerceAtMost(1f))
        } else {
            val k = (p - 0.55f) / 0.45f
            Triple(0.9f + (1f - 0.9f) * k, -9f + (REST_DEG + 9f) * k, 1f)
        }
    }
}

/**
 * 「藏」字收藏章：朱红实底方章、白色宋体「藏」字、内圈一道白细线、旋转 −7°。**固定朱红**，不跟专色（任何海报上都认得出）。
 * 只画章本身；落在哪由 [SealSlot] 管。
 */
@Composable
fun Seal(modifier: Modifier = Modifier, size: Dp = 34.dp) {
    val colors = Guga.colors
    Box(
        modifier.size(size).drawBehind {
            drawRect(colors.seal)
            val inset = this.size.minDimension * 0.09f
            drawRect(colors.ink.copy(alpha = 0.9f), Offset(inset, inset), Size(this.size.width - inset * 2, this.size.height - inset * 2), style = Stroke(this.size.minDimension * 0.03f))
        },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("藏", style = Guga.song((size.value * 0.56f).sp, FontWeight.Black, colors.ink).copy(fontFamily = GugaFonts.song))
    }
}

/**
 * 印章位：虚线方格，章落在里面。[sealed] 从 false 变 true = 落章（从 2.6 倍砸下来）；变回 false = 350 ms 淡出。
 * 减弱动效：直接出现 / 消失。位置是版面上预留的一格，所以落章不挤别的东西。
 */
@Composable
fun SealSlot(sealed: Boolean, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = Guga.colors
    val reduced = LocalMotionPrefs.current.reducedMotion
    // 0 = 没有章，1 = 章盖好了；首次组合直接是终值（进页面时已经收藏的不重放动画）
    val t = remember { Animatable(if (sealed) 1f else 0f) }
    val fade = remember { Animatable(if (sealed) 1f else 0f) }
    val host = com.gugaart.ui.theme.LocalPhoneHost.current
    LaunchedEffect(sealed) {
        if (sealed) {
            if (t.value >= 1f && fade.value >= 1f) return@LaunchedEffect
            // 触感（M4-3）：落章 = 成功；擦章 = 轻。进页面时已经收藏的不响（上面已经返回了）
            host.feel(com.gugaart.ui.theme.HapticStyle.Success)
            fade.snapTo(1f)
            if (reduced) t.snapTo(1f) else {
                t.snapTo(0f)
                t.animateTo(1f, tween(SealMotion.STAMP_MS, easing = SealMotion.EASE))
            }
        } else {
            if (fade.value <= 0f) return@LaunchedEffect
            host.feel(com.gugaart.ui.theme.HapticStyle.Light)
            if (reduced) fade.snapTo(0f) else fade.animateTo(0f, tween(SealMotion.FADE_MS))
            t.snapTo(0f)
        }
    }
    Box(
        modifier
            .size(size)
            .drawBehind {
                drawRect(colors.line, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            }
            .semantics { stateDescription = if (sealed) "已收藏" else "未收藏" }
            .testTag(if (sealed) "seal-on" else "seal-off"),
        contentAlignment = Alignment.Center,
    ) {
        if (fade.value > 0f) {
            Seal(
                Modifier.graphicsLayer {
                    val (s, deg, a) = SealMotion.pose(t.value)
                    scaleX = s
                    scaleY = s
                    rotationZ = deg
                    alpha = a * fade.value
                },
                size = size * 0.85f,
            )
        }
    }
}

/** 校对勾的参数：一笔画出，380 ms，`cubic-bezier(.5, 0, .3, 1)`。 */
object TickMotion {
    const val DRAW_MS = 380
    val EASE = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)
}

/**
 * 校对格 + 校对勾（已看）：行尾一个小方格，勾是一笔朱红的手写勾，**画过方格**（比格子大，出头）。
 * [checked] 变 true = 一笔画出；变 false = 擦掉。集号的删除线由调用方跟着 [checked] 画。电视没有方格（[box] = false，勾画在卡片角上）。
 */
@Composable
fun ProofTick(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 14.dp, box: Boolean = true) {
    val colors = Guga.colors
    val reduced = LocalMotionPrefs.current.reducedMotion
    val t = remember { Animatable(if (checked) 1f else 0f) }
    val host = com.gugaart.ui.theme.LocalPhoneHost.current
    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (t.value == target) return@LaunchedEffect
        // 触感（M4-3）：打勾 / 擦掉都是轻的一下
        host.feel(com.gugaart.ui.theme.HapticStyle.Light)
        if (reduced) t.snapTo(target) else t.animateTo(target, tween(if (checked) TickMotion.DRAW_MS else 200, easing = TickMotion.EASE))
    }
    Canvas(modifier.size(size * 1.9f).testTag(if (checked) "tick-on" else "tick-off")) {
        val s = size.toPx()
        val ox = (this.size.width - s) / 2
        val oy = (this.size.height - s) / 2
        if (box) drawRect(colors.dim, Offset(ox, oy), Size(s, s), style = Stroke(1.dp.toPx()))
        if (t.value > 0f) {
            // 手写的勾：起笔在格子左侧中间，折在下沿偏左，收笔甩出格子右上角
            val path = Path().apply {
                moveTo(ox - s * 0.12f, oy + s * 0.52f)
                quadraticTo(ox + s * 0.22f, oy + s * 0.70f, ox + s * 0.38f, oy + s * 1.02f)
                quadraticTo(ox + s * 0.78f, oy + s * 0.18f, ox + s * 1.42f, oy - s * 0.34f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val shown = Path()
            measure.getSegment(0f, measure.length * t.value, shown, true)
            drawPath(shown, colors.seal, style = Stroke(s * 0.17f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
