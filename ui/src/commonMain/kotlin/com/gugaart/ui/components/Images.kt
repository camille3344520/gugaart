package com.gugaart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.gugaart.ui.data.Framing
import com.gugaart.ui.theme.GugaMotion
import com.gugaart.ui.theme.SpotColor
import com.gugugaga.app.data.rememberImage
import com.gugugaga.ds.physics.LocalMotionPrefs
import kotlin.math.max
import kotlin.math.roundToInt

/** 把图缩成不超过 [maxW] × [maxH] 的小图，读出 ARGB 像素（专色取色、黑笔画 logo 判定、虚化底都用它）。 */
fun ImageBitmap.smallPixels(maxW: Int, maxH: Int): Triple<IntArray, Int, Int> {
    val k = minOf(maxW.toFloat() / width, maxH.toFloat() / height, 1f)
    val w = max(1, (width * k).roundToInt())
    val h = max(1, (height * k).roundToInt())
    val small = shrink(w, h)
    val map = small.toPixelMap()
    val out = IntArray(w * h)
    map.buffer.copyInto(out, 0, map.bufferOffset, map.bufferOffset + w * h)
    return Triple(out, w, h)
}

/** 缩成 [w] × [h]（面积平均的近似：双线性一次到位，够取色和做虚化底用）。 */
fun ImageBitmap.shrink(w: Int, h: Int): ImageBitmap {
    val small = ImageBitmap(w, h)
    val canvas = Canvas(small)
    canvas.drawImageRect(this, IntOffset.Zero, IntSize(width, height), IntOffset.Zero, IntSize(w, h), Paint().apply { filterQuality = FilterQuality.Medium })
    return small
}

/** 一张海报的专色（[SpotColor.of]）。取色在缩小后的 ≤ 64 × 96 上做。 */
fun ImageBitmap.spot(tv: Boolean = false): Color {
    val (px, _, _) = smallPixels(64, 96)
    return if (tv) SpotColor.of(px, SpotColor.TV_LIGHTNESS, SpotColor.TV_SATURATION) else SpotColor.of(px)
}

/** 图的平均感知亮度 0..1（亮图 > 0.46 时暗角加深一档，docs/design-language.md §6.2）。 */
fun ImageBitmap.meanLuma(): Float {
    val (px, _, _) = smallPixels(32, 32)
    return meanLuma(px)
}

fun meanLuma(argb: IntArray): Float {
    if (argb.isEmpty()) return 0f
    var sum = 0.0
    for (p in argb) sum += 0.3 * (p shr 16 and 0xFF) + 0.59 * (p shr 8 and 0xFF) + 0.11 * (p and 0xFF)
    return (sum / argb.size / 255.0).toFloat()
}

/** 亮图的门槛。 */
const val BRIGHT_IMAGE_LUMA = 0.46f

/**
 * 黑笔画 logo 的判定（docs/data-edge-cases.md §1）：把 logo 缩到 48 × 24，在不透明像素（alpha ≥ 90）里数「暗像素」（感知亮度 < 62）的占比，
 * **> 30 %** → 墨黑纸上看不见，要垫一张米白衬纸。
 */
fun isDarkLogo(argb: IntArray): Boolean {
    var opaque = 0
    var dark = 0
    for (p in argb) {
        if ((p ushr 24) < 90) continue
        opaque++
        val luma = 0.3 * (p shr 16 and 0xFF) + 0.59 * (p shr 8 and 0xFF) + 0.11 * (p and 0xFF)
        if (luma < 62) dark++
    }
    return opaque > 0 && dark * 10 > opaque * 3
}

fun ImageBitmap.isDarkLogo(): Boolean = isDarkLogo(smallPixels(48, 24).first)

/** 把 [image] 按 [framing]「盖满」画进当前画布。 */
fun DrawScope.drawCover(image: ImageBitmap, framing: Framing = Framing.DEFAULT, alpha: Float = 1f, colorFilter: ColorFilter? = null, quality: FilterQuality = FilterQuality.Medium) {
    val (scale, left, top) = framing.cover(image.width.toFloat(), image.height.toFloat(), size.width, size.height)
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
        alpha = alpha,
        colorFilter = colorFilter,
        filterQuality = quality,
    )
}

/** 把 [image] 整张放进当前画布（保持比例，居中或靠 [alignX]）。logo、演员小图用。 */
fun DrawScope.drawFit(image: ImageBitmap, alignX: Float = 0.5f, alignY: Float = 0.5f, alpha: Float = 1f) {
    val scale = minOf(size.width / image.width, size.height / image.height)
    val w = image.width * scale
    val h = image.height * scale
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(((size.width - w) * alignX).roundToInt(), ((size.height - h) * alignY).roundToInt()),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
        filterQuality = FilterQuality.High,
    )
}

/**
 * 交叉淡入的状态：新图**解码完**才换（上游 `rememberImage(keepPrevious = true)` 保证旧图留着），换的时候旧图在下、新图 420 ms 淡入。
 * 减弱动效时直接换。
 */
class Crossfade internal constructor() {
    var current: ImageBitmap? by mutableStateOf(null)
        internal set
    var previous: ImageBitmap? by mutableStateOf(null)
        internal set
    internal val fade = Animatable(1f)
    val alpha: Float get() = fade.value
}

@Composable
fun rememberCrossfade(image: ImageBitmap?, durationMs: Int = GugaMotion.COVER_FADE_MS): Crossfade {
    val state = remember { Crossfade() }
    val reduced = LocalMotionPrefs.current.reducedMotion
    LaunchedEffect(image) {
        if (image == null || image === state.current) return@LaunchedEffect
        val first = state.current == null
        state.previous = state.current
        state.current = image
        if (first || reduced) {
            state.fade.snapTo(1f)
        } else {
            state.fade.snapTo(0f)
            state.fade.animateTo(1f, tween(durationMs))
        }
        state.previous = null
    }
    return state
}

/**
 * 一张远程图，盖满自己的盒子。没到 → 画 [placeholder]（默认什么都不画，露出下面的纸 / 斜纹）；到了 → 交叉淡入。
 * [url] 换了以后旧图留到新图解码完（不闪）。
 */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    framing: Framing = Framing.DEFAULT,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    onImage: ((ImageBitmap) -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
) {
    val bmp = rememberImage(url, keepPrevious = true)
    val cf = rememberCrossfade(bmp)
    if (onImage != null && bmp != null) LaunchedEffect(bmp) { onImage(bmp) }
    Box(modifier.clipToBounds()) {
        if (cf.current == null && placeholder != null) placeholder()
        Canvas(Modifier.matchParentSize()) {
            cf.previous?.let { drawCover(it, framing, alpha, colorFilter) }
            cf.current?.let { drawCover(it, framing, alpha * cf.alpha, colorFilter) }
        }
    }
}

/** 一张图虚化后的样子：缩到很小再放大（跨平台一致，不依赖系统的模糊；这是页面内容，不是玻璃）。 */
@Composable
fun rememberSoft(image: ImageBitmap?, width: Int = 28): ImageBitmap? = remember(image) {
    image?.let { it.shrink(width, max(1, (width.toFloat() * it.height / it.width).roundToInt())) }
}
