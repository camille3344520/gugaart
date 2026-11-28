package com.gugaart.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.rememberBrightImage
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.rememberCrossfade
import com.gugaart.ui.components.rememberSoft
import com.gugugaga.app.data.rememberImage
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.Seal
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.components.meanLuma
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugugaga.ds.theme.UiMode

/** 电视共用部件的版式数（设计稿 `tv/css/tv.css` 的 `.card` `.sec-h` `.hs`）。 */
object TvKitSpec {
    /** 海报卡 214 × 321；横卡 312 × 176（续看）/ 400 × 225（分集）。 */
    val POSTER_W = 214.dp
    val POSTER_H = 321.dp
    val WIDE_W = 312.dp
    val WIDE_H = 176.dp
    val EPISODE_W = 400.dp
    val EPISODE_H = 225.dp
    /** 卡片下的片名：离图 14；片名 25 / 行高 1.3、最多两行；副行 22。 */
    val CAPTION_GAP = 14.dp
    val CAPTION = 25.sp
    val CAPTION_SUB = 22.sp
    /** 栏目标题一行的高度；标题到卡片顶 14。 */
    val SECTION_H = 56.dp
    val SECTION_TO_ROW = 14.dp
    /** 横排的列距；一栏到下一栏。 */
    val ROW_GAP = 32.dp
    val SECTION_GAP = 44.dp
    /** 横排的尾巴：让最后一张也能滑到左起第一格。 */
    val ROW_TAIL = 1700.dp
    /** 横排里靠左的卡：抬起时从左缘偏上放大（不出安全边）。 */
    val LEAD_ORIGIN = TransformOrigin(0f, 0.4f)
    /** 图上的进度线高 6。 */
    val PROGRESS = 6.dp
    const val FADE_MS = 200
}

/**
 * 「把焦点项滚到固定的位置」（`docs/platforms.md` §3）：横排 = 焦点卡永远停在左起第一格；竖向 = 焦点所在的一栏 / 一排吸到顶。
 * 上游的滚动容器（`horizontalScroll` / `verticalScroll` / lazy 列表）在焦点移动时都会问 `LocalBringIntoViewSpec`：这里的回答是
 * 「让焦点项的前缘落在离容器前缘 [leadPx] 的地方」。
 */
@OptIn(ExperimentalFoundationApi::class)
class TvSnapSpec(private val leadPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = tvSnapDistance(offset, leadPx)
}

/** 焦点项前缘现在在 [offset]（相对容器前缘，px），要它落在 [leadPx]：滚多少。不到 1 px 不动。纯函数，有单测。 */
fun tvSnapDistance(offset: Float, leadPx: Float): Float = (offset - leadPx).let { if (kotlin.math.abs(it) < 1f) 0f else it }

/** [content] 里的滚动容器：焦点项的前缘吸到离容器前缘 [lead] 处。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvSnap(lead: Dp, content: @Composable () -> Unit) {
    val px = with(LocalDensity.current) { lead.toPx() }
    val spec = remember(px) { TvSnapSpec(px) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/** 一栏横排的状态：现在停在第几张（焦点离开这一栏之后还记着，上下换栏回来还是它）。 */
@Stable
class TvRowState(lead: Int = 0) {
    var lead by mutableIntStateOf(lead)
    var hasFocus by mutableStateOf(false)
        internal set
    internal val scroll = ScrollState(0)
}

/**
 * 电视的横排（`docs/platforms.md` §3）：**焦点卡永远停在左起第一格**（屏幕 x = [TvSpec.LEFT]），栏内容滑动，滑过去的卡淡出
 * （不在侧栏底下留半个字）；右边的下一张可以出安全边。上下换栏回到这一栏上次停的卡：滑过去的卡在焦点不在这一栏时不接焦点，
 * 所以从上 / 下进来，正下方那张就是上次停的。
 *
 * 通栏放置（自己带左边 136 的页边）。[content] 画一张卡：`index`、条目、`lead`（是不是停在左起第一格的那张）。
 * 卡片用 [TvPosterCard] / [TvLandscapeCard]（或任何 `TvFocusable`）。
 */
@Composable
fun <T> TvRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    state: TvRowState = remember { TvRowState() },
    gap: Dp = TvKitSpec.ROW_GAP,
    start: Dp = TvSpec.LEFT,
    tag: String = "tv-row",
    content: @Composable (index: Int, item: T, lead: Boolean) -> Unit,
) {
    TvSnap(start) {
        Row(
            modifier
                .fillMaxWidth()
                .onFocusChanged { state.hasFocus = it.hasFocus }
                .focusGroup()
                .horizontalScroll(state.scroll, enabled = false)
                .padding(start = start, end = TvKitSpec.ROW_TAIL)
                .testTag(tag),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            for ((i, item) in items.withIndex()) {
                val past = i < state.lead
                val a by animateFloatAsState(if (past) 0f else 1f, tween(remember(past) { if (TvPerfGovernor.degraded) 0 else TvKitSpec.FADE_MS }), label = "tv-row-fade")
                Box(
                    Modifier
                        .onFocusChanged { if (it.hasFocus) state.lead = i }
                        .focusProperties { canFocus = state.hasFocus || i >= state.lead }
                        .then(if (past || a < 1f) Modifier.graphicsLayer { alpha = a } else Modifier)
                        .drawWithContent {
                            if (i >= state.lead - 1 && i <= state.lead + 8) drawContent()
                        },
                ) { content(i, item, i == state.lead) }
            }
        }
    }
}

/** 栏目标题：斜体编号（专色）+ 宋体 38 + 英文小标（专色）+ 一条细线 + 右端小字。通栏放置（自己带页边）。 */
@Composable
fun TvSectionHeader(title: String, english: String, modifier: Modifier = Modifier, number: String? = null, trailing: String? = null) {
    val colors = Guga.colors
    Row(
        modifier.fillMaxWidth().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT).height(TvKitSpec.SECTION_H).testTag("tv-section"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (!number.isNullOrEmpty()) BasicText(number, Modifier.alignByBaseline(), style = Guga.numeral(36.sp), color = { colors.spot }, maxLines = 1)
        BasicText(title, Modifier.alignByBaseline(), style = Guga.song(Guga.type.section, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
        if (english.isNotEmpty()) BasicText(english, Modifier.alignByBaseline(), style = Guga.numeral(20.sp, letterSpacing = 0.24.em), color = { colors.spot }, maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
        if (!trailing.isNullOrEmpty()) BasicText(trailing, Modifier.alignByBaseline(), style = Guga.note(23.sp, colors.dim), maxLines = 1)
    }
}

/** 卡片下的两行：片名（灰 → 焦点时白，最多两行）+ 副行。[pre]：片名前的斜体小号（分集的集号，专色）。 */
@Composable
fun TvCardCaption(title: AnnotatedString, sub: String?, focused: Boolean, modifier: Modifier = Modifier, pre: String? = null, titleLines: Int = 2) =
    TvCardCaption(title, sub, { focused }, modifier, pre, titleLines)

/** 绘制期读取 [focused] 的版本：焦点切换只改绘制着色，不触发重组与文本重新排版。 */
@Composable
fun TvCardCaption(title: AnnotatedString, sub: String?, focused: () -> Boolean, modifier: Modifier = Modifier, pre: String? = null, titleLines: Int = 2) {
    val colors = Guga.colors
    val ink = colors.ink
    val dim = colors.dim
    val faint = colors.faint
    val titleColor = remember(focused, ink, dim) { { if (focused()) ink else dim } }
    val subColor = remember(focused, dim, faint) { { if (focused()) dim else faint } }
    Column(modifier.padding(top = TvKitSpec.CAPTION_GAP)) {
        val titleStyle = Guga.song(TvKitSpec.CAPTION, FontWeight.Bold, lineHeight = TvKitSpec.CAPTION * 1.3f)
        if (pre != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(pre, style = Guga.numeral(TvKitSpec.CAPTION), color = { colors.spot }, maxLines = 1)
                BasicText(title, style = titleStyle, color = titleColor, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
            }
        } else {
            BasicText(title, style = titleStyle, color = titleColor, maxLines = titleLines, overflow = TextOverflow.Ellipsis)
        }
        if (!sub.isNullOrEmpty()) {
            BasicText(sub, Modifier.padding(top = 4.dp), style = Guga.song(TvKitSpec.CAPTION_SUB, FontWeight.SemiBold), color = subColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 没有图的卡：斜纹渐变底 + 细边 + 片名（海报）/ 斜体大集号 + 片名（横卡）。 */
@Composable
private fun BoxScope.TvNoPicture(name: String, number: String?) {
    val colors = Guga.colors
    Box(
        Modifier.matchParentSize()
            .drawBehind { drawRect(Brush.linearGradient(listOf(Color(0xFF2A251C), Color(0xFF17140F)), Offset(0f, 0f), Offset(size.width * 0.5f, size.height))) }
            .border(1.dp, colors.line)
            .padding(18.dp)
            .testTag("tv-nopic"),
    ) {
        if (!number.isNullOrEmpty()) BasicText(number, Modifier.align(Alignment.TopStart), style = Guga.numeral(66.sp), color = { colors.spot }, maxLines = 1)
        BasicText(
            name,
            Modifier.align(Alignment.BottomStart),
            style = Guga.song(if (number != null) 25.sp else 30.sp, FontWeight.Black, lineHeight = if (number != null) 33.sp else 38.sp),
            maxLines = if (number != null) 2 else 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 图下沿的进度线（专色，垫一条纸色 60 %）。 */
@Composable
private fun BoxScope.TvCardProgress(progress: Float) {
    if (progress <= 0f) return
    val colors = Guga.colors
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(TvKitSpec.PROGRESS).background(colors.paper.copy(alpha = 0.6f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0.03f, 1f)).height(TvKitSpec.PROGRESS).drawBehind { drawRect(colors.spot) })
    }
}

/**
 * 海报卡（214 × 321）：图 + 片名（最多两行，全名在长按菜单和详情里）+ 副行（默认「年份 · ★ 评分」）。
 * 焦点：米白框 + 校对角 + 抬起 ×1.08，片名由灰变白。已收藏 → 右上一枚小印；[progress] > 0 → 图下沿进度线。
 */
private const val DEFAULT_SUB = "\u0000default\u0000"

@Composable
fun TvPosterCard(
    title: Title,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: String? = title.id,
    default: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    width: Dp = TvKitSpec.POSTER_W,
    sub: String? = DEFAULT_SUB,
    name: AnnotatedString? = null,
    progress: Float = 0f,
    origin: TransformOrigin = TransformOrigin(0.5f, 0.4f),
    knownSize: androidx.compose.ui.unit.IntSize? = null,
) {
    val chrome = LocalChrome.current
    val subText = if (sub !== DEFAULT_SUB) sub else remember(title.id, title.year, title.rating) {
        listOfNotNull(title.year?.toString(), title.rating?.let { "★ $it" }).joinToString(" · ")
    }
    val shownName = name ?: remember(title.name) { AnnotatedString(title.name) }
    val boxSize = knownSize ?: with(LocalDensity.current) { androidx.compose.ui.unit.IntSize(width.roundToPx(), (width * 1.5f).roundToPx()) }
    val press = com.gugugaga.ds.physics.rememberPressState()
    val tv = LocalWideNav.current.tv
    val isFocused = remember(press) { { press.focused } }
    val picCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val focusCb = remember(picCoords, onFocus, tv) {
        {
            picCoords[0]?.takeIf { it.isAttached }?.let { tv.focusedPicture = it.boundsInRoot() }
            onFocus?.invoke()
            Unit
        }
    }
    val fav = chrome.isFavorite(title)
    val played = chrome.isPlayed(title.id, title.played)
    TvFocusable(onClick, modifier.width(width).testTag("tv-poster"), TvFocusKind.Card, key, default, title.name, onLongClick = onLongClick, press = press, origin = origin, onFocus = focusCb, frame = false, leaf = true) { _ ->
        // 无障碍：整张卡是一片叶子（leaf），里面不单独报
        Column {
            TvPosterPicture(title, boxSize, width * 1.5f, fav, played, progress, isFocused, picCoords, tv)
            TvCardCaption(shownName, subText, isFocused)
        }
    }
}

@Composable
private fun TvPosterPicture(
    title: Title,
    boxSize: androidx.compose.ui.unit.IntSize,
    height: Dp,
    fav: Boolean,
    played: Boolean,
    progress: Float,
    isFocused: () -> Boolean,
    picCoords: Array<LayoutCoordinates?>,
    tv: TvShellState,
) {
    Box(
        Modifier.fillMaxWidth().height(height)
            .onGloballyPositioned { c ->
                picCoords[0] = c
                if (isFocused()) tv.focusedPicture = c.boundsInRoot()
            }
            .tvCardFrame(isFocused)
            .background(Color(0xFF1C1914))
            .border(1.dp, Guga.colors.ink.copy(alpha = 0.16f)),
    ) {
        CoverImage(
            title.art.poster,
            Modifier.fillMaxSize(),
            Framing(0.5f, 0.5f),
            knownSize = boxSize,
            placeholder = if (title.art.poster == null) ({ Box(Modifier.fillMaxSize()) { TvNoPicture(title.name, null) } }) else null,
        )
        if (fav) Seal(Modifier.align(Alignment.TopEnd).padding(10.dp).graphicsLayer { rotationZ = -7f }, size = 46.dp)
        if (played) ProofTick(true, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp), size = 44.dp, box = false)
        TvCardProgress(progress)
    }
}

/**
 * 横卡（续看 312 × 176、分集 400 × 225）：图 + 片名 + 副行。没有图（服务器上没有这一集的剧照）= 排版的小条：斜体大集号 + 片名。
 * [seen] → 右上一笔朱红校对勾（长按确定菜单里的「标记已看」）。
 */
@Composable
fun TvLandscapeCard(
    image: String?,
    title: String,
    sub: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    key: String? = null,
    default: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    width: Dp = TvKitSpec.WIDE_W,
    height: Dp = TvKitSpec.WIDE_H,
    number: String? = null,
    pre: String? = null,
    progress: Float = 0f,
    seen: Boolean = false,
    caption: Boolean = true,
    framing: Framing = Framing.DEFAULT,
    origin: TransformOrigin = TransformOrigin(0.5f, 0.4f),
) {
    val boxSize = with(LocalDensity.current) { androidx.compose.ui.unit.IntSize(width.roundToPx(), height.roundToPx()) }
    val shownName = remember(title) { AnnotatedString(title) }
    val press = com.gugugaga.ds.physics.rememberPressState()
    val tv = LocalWideNav.current.tv
    val isFocused = remember(press) { { press.focused } }
    val picCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val focusCb = remember(picCoords, onFocus, tv) {
        {
            picCoords[0]?.takeIf { it.isAttached }?.let { tv.focusedPicture = it.boundsInRoot() }
            onFocus?.invoke()
            Unit
        }
    }
    TvFocusable(onClick, modifier.width(width).testTag("tv-wide"), TvFocusKind.Card, key, default, title, onLongClick = onLongClick, press = press, origin = origin, onFocus = focusCb, frame = false, leaf = true) { _ ->
        Column {
            TvWidePicture(image, title, number, boxSize, height, framing, seen, progress, isFocused, picCoords, tv)
            // 首页封面状态下目录只露图不露字：字还占着位置（版面不跳），只是不画
            TvCardCaption(shownName, sub, isFocused, Modifier.graphicsLayer { alpha = if (caption) 1f else 0f }, pre = pre, titleLines = 1)
        }
    }
}

@Composable
private fun TvWidePicture(
    image: String?,
    title: String,
    number: String?,
    boxSize: androidx.compose.ui.unit.IntSize,
    height: Dp,
    framing: Framing,
    seen: Boolean,
    progress: Float,
    isFocused: () -> Boolean,
    picCoords: Array<LayoutCoordinates?>,
    tv: TvShellState,
) {
    Box(
        Modifier.fillMaxWidth().height(height)
            .onGloballyPositioned { c ->
                picCoords[0] = c
                if (isFocused()) tv.focusedPicture = c.boundsInRoot()
            }
            .tvCardFrame(isFocused)
            .background(Color(0xFF1C1914))
            .border(1.dp, Guga.colors.ink.copy(alpha = 0.16f)),
    ) {
        CoverImage(
            image,
            Modifier.fillMaxSize(),
            framing,
            knownSize = boxSize,
            placeholder = if (image == null) ({ Box(Modifier.fillMaxSize()) { TvNoPicture(title, number.orEmpty()) } }) else null,
        )
        if (seen) ProofTick(true, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 8.dp), size = 44.dp, box = false)
        TvCardProgress(progress)
    }
}

/**
 * ControlledGl 的退路（电视 API 28–32，`docs/glass.md` §4、decisions Q12）：这个后端只折射 `GlassScene` 的 `backdrop` 那张图，
 * 看不见页面内容。整幅铺满的电视页面调它，把「整屏的小图」（纸色 + 剧照铺在它在屏幕上的位置 + 和页面一样的压暗）放到
 * [com.gugaart.ui.wide.WideNav.bleedBackdrop]，根把它交给场景：侧栏正好压在剧照的左缘，折射的就是它。离开这一页时清掉（= 纸色）。
 *
 * [image]：现在这张整幅图（解码好的；null = 还没有 → 纸色）。[heightFraction]：图占屏幕高度的多少（从最顶端铺起）。
 * [dimLeft] / [dimRight]：左、右两端压暗多少（页面上左侧的暗角比右边重；侧栏在左缘）。
 */
@Composable
fun TvBleedBackdrop(image: ImageBitmap?, heightFraction: Float = 1f, framing: Framing = Framing.DEFAULT, dimLeft: Float = 0.9f, dimRight: Float = 0.46f, active: Boolean = true) {
    if (Guga.uiMode != UiMode.Tv) return
    val nav = LocalWideNav.current
    val paper = Guga.colors.paper
    if (com.gugaart.ui.idle.LocalIdleClock.current == null) {
        val small = remember(image, heightFraction, framing, dimLeft, dimRight) { image?.let { tvBackdropOf(it, paper, heightFraction, framing, dimLeft, dimRight) } }
        DisposableEffect(small, active) {
            if (active) nav.bleedBackdrop = small
            onDispose { if (nav.bleedBackdrop === small) nav.bleedBackdrop = null }
        }
        return
    }
    var small by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image, heightFraction, framing, dimLeft, dimRight, paper) {
        val out = if (image == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            tvBackdropOf(image, paper, heightFraction, framing, dimLeft, dimRight)
        }
        small = out
        if (active) nav.bleedBackdrop = out
    }
    DisposableEffect(active, small) {
        if (active && small != null) nav.bleedBackdrop = small
        onDispose { if (!active && nav.bleedBackdrop === small) nav.bleedBackdrop = null }
    }
    DisposableEffect(Unit) {
        onDispose { if (nav.bleedBackdrop === small) nav.bleedBackdrop = null }
    }
}

/** 整屏的小图（480 × 270）：纸色底，[image] 按 [framing] 盖满上面 [heightFraction] 的高度，左右压暗、底部渐隐进纸色。 */
fun tvBackdropOf(image: ImageBitmap, paper: Color, heightFraction: Float, framing: Framing, dimLeft: Float, dimRight: Float, w: Int = 480, h: Int = 270): ImageBitmap {
    val out = ImageBitmap(w, h)
    val ih = (h * heightFraction.coerceIn(0.05f, 1f))
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(w.toFloat(), h.toFloat())) {
        drawRect(paper)
        // 画进上面那一块：先把画布裁到图的范围
        drawContext.canvas.save()
        drawContext.canvas.clipRect(0f, 0f, w.toFloat(), ih)
        val full = CanvasDrawScope()
        full.draw(Density(1f), LayoutDirection.Ltr, drawContext.canvas, Size(w.toFloat(), ih)) { drawCover(image, framing) }
        drawContext.canvas.restore()
        drawRect(Brush.horizontalGradient(listOf(paper.copy(alpha = dimLeft), paper.copy(alpha = dimRight))), size = Size(w.toFloat(), ih))
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to paper, startY = ih * 0.6f, endY = ih), topLeft = Offset(0f, ih * 0.6f), size = Size(w.toFloat(), ih * 0.4f))
    }
    return out
}

/** 电视整幅图上的两层暗角（设计稿 `tv/css/home.css` 的 `.sh1` `.sh2`）：左侧重、右侧轻；顶上压一条、下面渐隐进纸色。文字只放在暗角里。 */
object TvBleedSpec {
    /** 横向：0 % → 95 %、28 % → 88 %、50 % → 52 %、100 % → 46 %。 */
    val SIDE = arrayOf(0f to 0.95f, 0.28f to 0.88f, 0.5f to 0.52f, 1f to 0.46f)
    /** 纵向：0 % → 80 %、17 % → 20 %、46 % → 0、82 % → 70 %、100 % → 纸色。 */
    val DOWN = arrayOf(0f to 0.8f, 0.17f to 0.2f, 0.46f to 0f, 0.82f to 0.7f, 1f to 1f)
    /** 取景：横向居中、纵向 22 %（人脸多在上面）。 */
    val FRAMING = Framing(0.5f, 0.22f)
    /** 亮图（平均亮度 > 46 %）：整体再压一层。 */
    const val BRIGHT_EXTRA = 0.18f
}

/**
 * 电视的整幅图（首页封面区背后、首页焦点预览、发现的「今日一篇」）：图从屏幕最顶端铺起，报头 / 页眉压在图上；左侧和底部各一层暗角。
 * - 有剧照 → 盖满；没有剧照 → 海报放大虚化铺满（清晰的那张由调用方贴在右边）；都没有 → 纸色。
 * - 换图：保留上一张，解码完交叉淡入（不闪）。亮图自动把暗角加深。
 * - 顺手把这张图交给 ControlledGl 的退路（[TvBleedBackdrop]）。
 *
 * [grey]：网络断开时的旧版面（图去色）。返回值不用；[onImage] 拿到现在这张图（取专色）。
 */
@Composable
fun TvBleed(
    backdrop: String?,
    poster: String?,
    /** 会动的高（电视首页封面 ↔ 目录换版）：只在这里读，调用的那一页不跟着每帧重组（1.5.1 性能）。 */
    height: androidx.compose.runtime.State<Dp>,
    modifier: Modifier = Modifier,
    framing: Framing = TvBleedSpec.FRAMING,
    grey: Boolean = false,
    screenHeight: Dp = 1080.dp,
    reportBackdrop: Boolean = true,
) = TvBleed(backdrop, poster, height.value, modifier, framing, grey, screenHeight, reportBackdrop)

@Composable
fun TvBleed(
    backdrop: String?,
    poster: String?,
    height: Dp,
    modifier: Modifier = Modifier,
    framing: Framing = TvBleedSpec.FRAMING,
    grey: Boolean = false,
    screenHeight: Dp = 1080.dp,
    reportBackdrop: Boolean = true,
) {
    val colors = Guga.colors
    val blurred = backdrop == null
    val rawUrl = backdrop ?: poster
    // 压到 TV_BLEED_W（1280）宽；探针 bleed full 要原图（看压宽前后的差别）
    val bleedUrl = remember(rawUrl) { rawUrl?.let(::tvBleedUrl) }
    val bmp = rememberImage(bleedUrl, keepPrevious = true)
    val cf = rememberCrossfade(bmp)
    val softNow = rememberSoft(cf.current.takeIf { blurred })
    val softPrev = rememberSoft(cf.previous.takeIf { blurred })
    val current = cf.current
    val bright = rememberBrightImage(current)
    TvBleedBackdrop(if (blurred) softNow else current, height / screenHeight, framing, active = reportBackdrop)
    val filter = remember(grey) { if (grey) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.15f) }) else null }
    val paper = colors.paper
    // 候选修法 bleedcache：图 + 亮图压暗 + 两层暗角先在后台合成一张「版」，每帧只贴这一张（版换的时候两张交叉淡入）
    val usePlate = TvFlipProbe.bleedCache && filter == null && TvFlipProbe.bleed != "nograd"
    var boxPx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val plate = if (usePlate) rememberBleedPlate(if (blurred) softNow else current, blurred, boxPx, framing, paper) else null
    val plateCf = rememberCrossfade(plate)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .then(if (usePlate) Modifier.onSizeChanged { if (it != boxPx) boxPx = it } else Modifier)
            .testTag(if (current == null) "tv-bleed-empty" else if (blurred) "tv-bleed-poster" else "tv-bleed")
            .drawWithCache {
                val brushes = tvBleedBrushes(paper)
                onDrawBehind {
                    val cur = plateCf.current
                    if (usePlate && cur != null && cur.width == size.width.roundToInt() && cur.height == size.height.roundToInt()) {
                        plateCf.previous?.takeIf { it.width == cur.width && it.height == cur.height }?.let { drawImage(it) }
                        drawImage(cur, alpha = plateCf.alpha)
                    } else if (blurred) {
                        drawTvBleed(softPrev, softNow, cf.alpha, framing, filter, FilterQuality.High, true, paper, brushes)
                    } else {
                        drawTvBleed(cf.previous, current, cf.alpha, framing, filter, FilterQuality.Medium, bright, paper, brushes.takeIf { TvFlipProbe.bleed != "nograd" })
                    }
                }
            },
    )
}

/**
 * [TvBleed] 要的图宽：1280（int151e 协调人定）。perf/tv 压到过 480，全屏放大偏糊；1280 在 1920 宽的屏上看不出糊，
 * 解码 1280 × 720 × 4 B ≈ 3.7 MB，比原图（1920 宽）省一半多。首页预取、合成的「版」、详情首帧垫图都用这一档。
 */
const val TV_BLEED_W = 1280

/** [TvBleed] 实际要的图地址：压到 [TV_BLEED_W] 宽；探针 `bleed full` = 原地址。 */
fun tvBleedUrl(url: String): String = if (TvFlipProbe.bleed == "full") url else com.gugaart.ui.data.ImageSizing.capWidth(url, TV_BLEED_W)

/** [TvBleed] 的两层暗角（横、竖）；铺满当前画布。 */
fun tvBleedBrushes(paper: Color): Pair<Brush, Brush> = Pair(
    Brush.horizontalGradient(*TvBleedSpec.SIDE.map { (at, a) -> at to paper.copy(alpha = a) }.toTypedArray()),
    Brush.verticalGradient(*TvBleedSpec.DOWN.map { (at, a) -> at to paper.copy(alpha = a) }.toTypedArray()),
)

/**
 * [TvBleed] 的画法（每帧现画和后台合成的「版」共用这一份，所以两条路画出来一样）：纸色底、[previous] 在下 / [current] 按 [alpha] 淡入，
 * 都按 [framing] 盖满；[dimmed]（亮图或虚化的海报）再压一层；最后 [vignette] 两层暗角（null = 不画，只给探针用）。
 */
fun DrawScope.drawTvBleed(
    previous: ImageBitmap?,
    current: ImageBitmap?,
    alpha: Float,
    framing: Framing,
    filter: ColorFilter?,
    quality: FilterQuality,
    dimmed: Boolean,
    paper: Color,
    vignette: Pair<Brush, Brush>? = tvBleedBrushes(paper),
) {
    drawRect(paper)
    clipRect {
        previous?.let { drawCover(it, framing, 1f, filter, quality) }
        current?.let { drawCover(it, framing, alpha, filter, quality) }
    }
    if (dimmed) drawRect(paper.copy(alpha = TvBleedSpec.BRIGHT_EXTRA))
    if (vignette == null) return
    drawRect(vignette.first)
    drawRect(vignette.second)
}

/** [TvBleed] 的「版」：后台合成，[size] 变了 / 图换了重做；新版好之前一直给旧版。 */
@Composable
private fun rememberBleedPlate(image: ImageBitmap?, blurred: Boolean, size: androidx.compose.ui.unit.IntSize, framing: Framing, paper: Color): ImageBitmap? {
    var plate by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image, blurred, size, framing, paper) {
        if (image == null || size.width <= 0 || size.height <= 0) return@LaunchedEffect
        val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { tvBleedPlate(image, blurred, size.width, size.height, framing, paper) }
        out.prepareToDraw()
        plate = out
    }
    return plate
}

/** [TvBleed] 的「版」：和每帧现画的同一份 [drawTvBleed]（一张图、不淡入），画进 [w] × [h] 的位图。 */
fun tvBleedPlate(image: ImageBitmap, blurred: Boolean, w: Int, h: Int, framing: Framing, paper: Color): ImageBitmap {
    val out = ImageBitmap(w, h)
    val dimmed = blurred || image.meanLuma() > com.gugaart.ui.components.BRIGHT_IMAGE_LUMA
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(out), Size(w.toFloat(), h.toFloat())) {
        drawTvBleed(null, image, 1f, framing, null, if (blurred) FilterQuality.High else FilterQuality.Medium, dimmed, paper)
    }
    return out
}

/**
 * 筛选格 / 索引格：线框小格；选中 = 专色框 + 专色字 + 一个小方块（选中不只靠颜色）；焦点 = 米白实底、墨字 + 小号校对角。
 * [trailing]：格子里靠右的斜体小字（排序方向 ↓）。
 */
@Composable
fun TvChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, key: String? = null, default: Boolean = false, trailing: String? = null) {
    val colors = Guga.colors
    TvFocusable(onClick, modifier.testTag(if (selected) "tv-chip-on" else "tv-chip"), TvFocusKind.Chip, key, default, text) { focused ->
        val ink = if (focused) colors.onInk else if (selected) colors.spot else colors.ink
        Row(
            Modifier.then(if (focused) Modifier else Modifier.border(1.5.dp, if (selected) colors.spot else colors.faint)).height(52.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selected) Box(Modifier.size(10.dp).background(if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot))
            BasicText(text, style = Guga.song(23.sp, FontWeight.SemiBold, ink), maxLines = 1)
            if (trailing != null) BasicText(trailing, style = Guga.numeral(21.sp, ink.copy(alpha = 0.7f)), maxLines = 1)
        }
    }
}
