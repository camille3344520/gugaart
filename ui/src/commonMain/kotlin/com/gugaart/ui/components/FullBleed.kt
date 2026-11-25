package com.gugaart.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Framing
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.ds.theme.UiMode

/** 整幅图的来源（决定图注怎么写）。[Poster]：手机详情页的竖版海报（[FullBleed] 的 `poster` 模式）。 */
enum class BleedSource { Backdrop, PosterBlur, Poster, None }

/** [poster] = 手机详情页：有海报就用竖版海报（用户 2026-11-24：「手机端，进入影片详情最好是显示竖版的海报，横版的会看不全内容」）。 */
fun bleedSourceOf(art: Art, poster: Boolean = false): BleedSource = when {
    poster && art.poster != null -> BleedSource.Poster
    art.backdrop != null -> BleedSource.Backdrop
    art.poster != null -> BleedSource.PosterBlur
    else -> BleedSource.None
}

/**
 * 手机详情页头图的高：有海报 = 竖版海报整张（宽 × 1.5，2:3），但不超过屏高的 [BleedSpec.POSTER_MAX_SCREEN]（超了按顶部对齐、裁掉海报底部一点）；
 * 没有海报 = [fallback]（横版剧照那一套原来的高）。
 */
@Composable
fun phoneHeroHeight(art: Art, fallback: Dp): Dp {
    if (art.poster == null) return fallback
    val win = com.gugugaga.ds.theme.LocalWindowDp.current
    return minOf(win.width * 1.5f, win.height * BleedSpec.POSTER_MAX_SCREEN)
}

/** 整幅图的几个数（docs/design-language.md §6.2）。 */
object BleedSpec {
    /** 顶部暗角的高度。 */
    val VIGNETTE = 130.dp

    /** 底部渐隐带占图高的比例：下 58 % 渐隐进纸色。 */
    const val FADE = 0.58f

    /** 文字从渐隐的这个位置（已接近纸色）才开始。 */
    const val TEXT_FROM = 0.70f

    /** 顶部暗角的浓度：普通 / 亮图（平均亮度 > 46 %）加深一档。 */
    const val VIGNETTE_ALPHA = 0.62f
    const val VIGNETTE_ALPHA_BRIGHT = 0.82f

    /** 图注在滚动这么多 dp 之内淡出。 */
    val CAPTION_FADE = 50.dp

    /** 竖版海报模式：只有底部这么多渐隐进纸色（片名区压在这里），上面的海报完整露出。 */
    const val POSTER_FADE = 0.34f

    /** 竖版海报模式：头图最高占屏高的比例。 */
    const val POSTER_MAX_SCREEN = 0.72f
}

/**
 * 整幅铺满的头图（docs/design-language.md §6）。图从屏幕最顶端铺起；顶部暗角和底部渐隐是**画进图里的**（玻璃折射到的就是压暗后的图）。
 *
 * - 有剧照 → 按 [framing] 取景盖满；
 * - 没有剧照 → 海报放大虚化铺满，再贴一张清晰的海报（图注写「没有剧照 · 用海报虚化代替」）；
 * - 连海报也没有 → 纸色 + 斜纹空版；
 * - 加载中 → 保留上一张（`rememberImage(keepPrevious)`），解码完交叉淡入；
 * - 亮图 → 暗角自动加深。
 *
 * [caption]：图注（手机 / 电脑在右缘竖排，电视右上横排），随 [scrollPx] 在 50 dp 内淡出。[content] 压在图上（按钮只放两个上角）。
 */
@Composable
fun FullBleed(
    art: Art,
    height: Dp,
    modifier: Modifier = Modifier,
    framing: Framing = Framing.DEFAULT,
    caption: String? = null,
    scrollPx: Float = 0f,
    /** 首页封面区：有剧照也虚化、压暗（海报流和文字浮在上面）。 */
    soften: Boolean = false,
    /** 手机详情页：有海报就用竖版海报整张铺开（顶部对齐），只底部 [BleedSpec.POSTER_FADE] 渐隐；没海报照旧。 */
    poster: Boolean = false,
    dim: Float = 0f,
    onImage: ((ImageBitmap) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val colors = Guga.colors
    val source = bleedSourceOf(art, poster)
    val url = if (source == BleedSource.Poster) art.poster else art.backdrop ?: art.poster
    // 竖版海报：横向居中、竖向贴顶（高被封顶时裁的是海报底部，片名区本来就压在那里）
    val frame = if (source == BleedSource.Poster) Framing(0.5f, 0f) else framing
    val bmp = rememberImage(url, keepPrevious = true)
    val cf = rememberCrossfade(bmp)
    val softNow = rememberSoft(cf.current.takeIf { soften || source == BleedSource.PosterBlur })
    val softPrev = rememberSoft(cf.previous.takeIf { soften || source == BleedSource.PosterBlur })
    val current = cf.current
    val bright = remember(current) { current != null && current.meanLuma() > BRIGHT_IMAGE_LUMA }
    if (onImage != null && current != null) androidx.compose.runtime.LaunchedEffect(current) { onImage(current) }
    Box(modifier.fillMaxWidth().height(height).clipToBounds().testTag("full-bleed-${source.name.lowercase()}")) {
        Canvas(Modifier.matchParentSize()) {
            drawRect(colors.paper)
            if (source == BleedSource.None || current == null) {
                hatch(colors.line.copy(alpha = 0.08f), 9.dp.toPx(), 1.dp.toPx())
            }
            val blurred = soften || source == BleedSource.PosterBlur
            if (blurred) {
                softPrev?.let { drawCover(it, frame, 1f, quality = FilterQuality.High) }
                softNow?.let { drawCover(it, frame, cf.alpha, quality = FilterQuality.High) }
            } else {
                cf.previous?.let { drawCover(it, frame) }
                current?.let { drawCover(it, frame, cf.alpha, quality = if (source == BleedSource.Poster) FilterQuality.High else FilterQuality.Medium) }
            }
            // 压暗（首页封面区、虚化的海报）
            val veil = if (blurred) maxOf(dim, 0.34f) else dim
            if (veil > 0f) drawRect(colors.paper.copy(alpha = veil))
            // 顶部暗角
            val v = BleedSpec.VIGNETTE.toPx().coerceAtMost(size.height)
            val va = if (bright) BleedSpec.VIGNETTE_ALPHA_BRIGHT else BleedSpec.VIGNETTE_ALPHA
            drawRect(Brush.verticalGradient(listOf(colors.paper.copy(alpha = va), Color.Transparent), 0f, v), size = androidx.compose.ui.geometry.Size(size.width, v))
            // 底部渐隐带：下 58 % 渐隐进纸色，70 % 处已接近纸色（竖版海报只渐隐下 34 %，海报上面整张露出来）
            val top = size.height * (1f - if (source == BleedSource.Poster) BleedSpec.POSTER_FADE else BleedSpec.FADE)
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to colors.paper.copy(alpha = 0.62f),
                    BleedSpec.TEXT_FROM to colors.paper.copy(alpha = 0.94f),
                    1f to colors.paper,
                    startY = top,
                    endY = size.height,
                ),
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
            )
        }
        // 没有剧照：贴一张清晰的海报
        if (source == BleedSource.PosterBlur && !soften && current != null) {
            Box(Modifier.align(Alignment.TopCenter).padding(top = height * 0.16f).width(height * 0.36f).aspectRatio(2f / 3f).border(1.dp, colors.ink.copy(alpha = 0.5f))) {
                Canvas(Modifier.matchParentSize()) { drawCover(current, Framing(0.5f, 0.5f), cf.alpha) }
            }
        }
        if (caption != null) {
            val fade = with(androidx.compose.ui.platform.LocalDensity.current) { (1f - scrollPx / BleedSpec.CAPTION_FADE.toPx()).coerceIn(0f, 1f) }
            if (fade > 0f) {
                if (Guga.uiMode == UiMode.Tv) {
                    BasicText(caption, Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 96.dp).graphicsLayer { alpha = fade }, style = Guga.song(Guga.type.fine, color = colors.dim), maxLines = 1)
                } else {
                    VerticalCaption(
                        caption,
                        Modifier.align(Alignment.TopEnd).padding(top = BleedSpec.VIGNETTE + height * frame.captionDown * 0.5f).graphicsLayer { alpha = fade },
                    )
                }
            }
        }
        content()
    }
}

/**
 * 竖排图注：一列一个字，垫一条 52 % 墨底（任何图上都读得清）。第一段（「剧照」）用专色。只给非必读信息。
 * 拉丁字母和数字连在一起的一段转 90° 不做——直接逐字竖排，数字两位以内并排。
 */
@Composable
fun VerticalCaption(text: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val parts = text.split(" · ", limit = 2)
    Column(
        modifier.background(colors.inkWell).padding(horizontal = 5.dp, vertical = 8.dp).testTag("bleed-caption"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for ((i, part) in parts.withIndex()) {
            for (cell in verticalCells(part)) {
                BasicText(cell, style = Guga.song(Guga.type.fine, if (i == 0 && parts.size > 1) FontWeight.Bold else FontWeight.Normal, if (i == 0 && parts.size > 1) colors.spot else colors.ink.copy(alpha = 0.86f), lineHeight = Guga.type.fine * 1.5f), maxLines = 1)
            }
            if (i == 0 && parts.size > 1) Box(Modifier.padding(vertical = 5.dp).width(8.dp).height(1.dp).background(colors.line))
        }
    }
}

/** 竖排的格子：汉字一格一个；连续的数字 ≤ 2 位并成一格（「第 5 集」的 5、「12」），空格跳过。纯函数，有单测。 */
fun verticalCells(text: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            ch.isWhitespace() -> i++
            ch.isDigit() -> {
                var j = i
                while (j < text.length && text[j].isDigit()) j++
                val run = text.substring(i, j)
                if (run.length <= 2) out += run else run.forEach { out += it.toString() }
                i = j
            }
            else -> {
                out += ch.toString()
                i++
            }
        }
    }
    return out
}

/** 滚动位置 → 顶栏的实心程度（0 透明、1 实心纸色）。[solidAt]：第一行字到达顶栏下沿之前的阈值（px）。 */
fun bandSolidity(scrollPx: Float, solidAt: Float): Float = if (solidAt <= 0f) 1f else if (scrollPx >= solidAt) 1f else 0f

/** 16:9 的图版（搜索最佳匹配、只有一部作品的人物页）：不是全窗，带细边。 */
@Composable
fun Plate(url: String?, modifier: Modifier = Modifier, fallback: String = "", framing: Framing = Framing.DEFAULT) {
    val colors = Guga.colors
    Box(modifier.aspectRatio(16f / 9f).border(1.dp, colors.line)) {
        CoverImage(url, Modifier.fillMaxSize(), framing) { HatchPlate(fallback, Modifier.fillMaxSize(), textSize = 20.sp) }
    }
}
