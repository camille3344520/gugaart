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
import com.gugaart.ui.idle.LocalIdleClock
import com.gugaart.ui.tv.TvPerfGovernor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.ImageSizing
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
    val sysReduced = LocalMotionPrefs.current.reducedMotion
    val reduced = remember(image, sysReduced) { sysReduced || TvPerfGovernor.degraded || !com.gugaart.ui.tv.TvFlipProbe.xfade }
    if (image != null && (state.current == null || reduced || durationMs <= 0)) {
        state.current = image
        state.previous = null
    }
    if (image != null && image !== state.current && !reduced && durationMs > 0) {
        LaunchedEffect(image, durationMs) {
            state.previous = state.current
            state.current = image
            state.fade.snapTo(0f)
            state.fade.animateTo(1f, tween(durationMs))
            state.previous = null
        }
    }
    return state
}

/**
 * 一张远程图，盖满自己的盒子。没到 → 画 [placeholder]（默认什么都不画，露出下面的纸 / 斜纹）；到了 → 交叉淡入。
 * [url] 换了以后旧图留到新图解码完（不闪）。[knownSize] 已知盒子的像素尺寸时首帧直接按它算 `ImageSizing`，免一次 `onSizeChanged` 二次重组。
 */
@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    framing: Framing = Framing.DEFAULT,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
    onImage: ((ImageBitmap) -> Unit)? = null,
    knownSize: IntSize? = null,
    /** 裁到自己的盒子（默认）；false 只给性能探针用（[com.gugaart.ui.tv.TvFlipProbe.clip]）。 */
    clip: Boolean = true,
    placeholder: (@Composable () -> Unit)? = null,
) {
    // 竖版封面按盒子的像素大小要图（ImageSizing）：已知尺寸首帧即用；否则先用上次量过的地址或等一次布局
    var box by remember { mutableStateOf(IntSize.Zero) }
    val effective = knownSize?.takeIf { it.width > 0 && it.height > 0 } ?: box
    val want = remember(url, effective.width, effective.height) {
        url?.let { u -> if (effective.width > 0 && effective.height > 0) ImageSizing.sized(u, effective.width, effective.height) else ImageSizing.lastFor(u) }
    }
    val bmp = rememberImage(want, keepPrevious = true)
    val cf = rememberCrossfade(bmp)
    if (onImage != null && bmp != null) LaunchedEffect(bmp) { onImage(bmp) }
    val sizeMod = if (knownSize != null && knownSize.width > 0 && knownSize.height > 0) Modifier else Modifier.onSizeChanged { if (it != box) box = it }
    Box(
        (if (clip) modifier.clipToBounds() else modifier).then(sizeMod).drawBehind {
            cf.previous?.let { drawCover(it, framing, alpha, colorFilter) }
            cf.current?.let { drawCover(it, framing, alpha * cf.alpha, colorFilter) }
        },
    ) {
        if (cf.current == null && placeholder != null) placeholder()
    }
}

private class RefLru<K : Any, V>(private val limit: Int = 32) {
    private val entries = ArrayList<Pair<K, V>>()

    fun get(key: K): V? {
        val idx = entries.indexOfFirst { it.first === key }
        if (idx < 0) return null
        val entry = entries.removeAt(idx)
        entries.add(entry)
        return entry.second
    }

    fun put(key: K, value: V): V {
        val idx = entries.indexOfFirst { it.first === key }
        if (idx >= 0) entries.removeAt(idx)
        entries.add(key to value)
        while (entries.size > limit) entries.removeAt(0)
        return value
    }
}

private object ImageEffectsCache {
    val soft = RefLru<ImageBitmap, Pair<Int, ImageBitmap>>(24)
    val bright = RefLru<ImageBitmap, Boolean>(64)
    val darkLogo = RefLru<ImageBitmap, Boolean>(64)
    val spotNormal = RefLru<ImageBitmap, Color>(64)
    val spotTv = RefLru<ImageBitmap, Color>(64)
}

/** 一张图虚化后的样子：缩到很小再放大（跨平台一致，不依赖系统的模糊；这是页面内容，不是玻璃）。真运行在后台算并缓存。 */
@Composable
fun rememberSoft(image: ImageBitmap?, width: Int = 28): ImageBitmap? {
    if (image == null) return null
    val cached = ImageEffectsCache.soft.get(image)?.takeIf { it.first == width }?.second
    if (cached != null) return cached
    if (LocalIdleClock.current == null) {
        return remember(image, width) {
            val out = image.shrink(width, max(1, (width.toFloat() * image.height / image.width).roundToInt()))
            ImageEffectsCache.soft.put(image, width to out).second
        }
    }
    var computed by remember(image, width) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image, width) {
        val out = withContext(Dispatchers.Default) {
            image.shrink(width, max(1, (width.toFloat() * image.height / image.width).roundToInt()))
        }
        ImageEffectsCache.soft.put(image, width to out)
        computed = out
    }
    return computed
}

/** 亮图判定（后台算 + 缓存，不阻塞 UI 线程）。 */
@Composable
fun rememberBrightImage(image: ImageBitmap?): Boolean {
    if (image == null) return false
    ImageEffectsCache.bright.get(image)?.let { return it }
    if (LocalIdleClock.current == null) {
        return remember(image) { ImageEffectsCache.bright.put(image, image.meanLuma() > BRIGHT_IMAGE_LUMA) }
    }
    var bright by remember(image) { mutableStateOf(false) }
    LaunchedEffect(image) {
        val v = withContext(Dispatchers.Default) { image.meanLuma() > BRIGHT_IMAGE_LUMA }
        ImageEffectsCache.bright.put(image, v)
        bright = v
    }
    return bright
}

/** 黑笔画 logo 判定（后台算 + 缓存，不阻塞 UI 线程）。 */
@Composable
fun rememberDarkLogo(image: ImageBitmap?): Boolean {
    if (image == null) return false
    ImageEffectsCache.darkLogo.get(image)?.let { return it }
    if (LocalIdleClock.current == null) {
        return remember(image) { ImageEffectsCache.darkLogo.put(image, image.isDarkLogo()) }
    }
    var dark by remember(image) { mutableStateOf(false) }
    LaunchedEffect(image) {
        val v = withContext(Dispatchers.Default) { image.isDarkLogo() }
        ImageEffectsCache.darkLogo.put(image, v)
        dark = v
    }
    return dark
}

/** 专色取色（后台算 + 缓存，不阻塞 UI 线程）。 */
@Composable
fun rememberSpotColor(image: ImageBitmap?, tv: Boolean = false): Color? {
    if (image == null) return null
    val cache = if (tv) ImageEffectsCache.spotTv else ImageEffectsCache.spotNormal
    cache.get(image)?.let { return it }
    if (LocalIdleClock.current == null) {
        return remember(image, tv) { cache.put(image, image.spot(tv = tv)) }
    }
    var color by remember(image, tv) { mutableStateOf<Color?>(null) }
    LaunchedEffect(image, tv) {
        val c = withContext(Dispatchers.Default) { image.spot(tv = tv) }
        cache.put(image, c)
        color = c
    }
    return color
}
