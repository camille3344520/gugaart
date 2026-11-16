package com.gugaart.ui.screens.detail

import androidx.compose.foundation.Canvas
import com.gugaart.ui.components.ScrollArrows
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.model.Still
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * 剧照（用户 2026-11-15：「看不到剧照了，这个能否看下排在什么位置合适」——详情页）。
 *
 * 旧版咕咕嘎嘎的详情页在「演员」下面有一排横版剧照（上游 `StillsRail`），新界面漏了。数据照旧用上游的 `rememberStills`：
 * 服务器自己的背景图 / 截图 / 缩略图在前；一张都没有、又填了 TMDB 令牌时，从 TMDB 补。
 *
 * 放在哪：演职员之后。详情页从上往下是「这是什么（整幅剧照、片名、简介）→ 看哪一集（分集目录）→ 谁演的 → 更多画面」，
 * 剧照是看完信息之后的「翻画册」，放在最后一栏不打断选集；也和旧版的位置一致，老用户找得到。
 * 剧照少于 2 张时整栏不出现（只有 1 张的话，它已经是页顶那幅铺满的图了）。
 */

/** 这部片的剧照（上游 `rememberStills`）。没登录 / 还没取到 = 空。 */
@Composable
fun rememberItemStills(id: String): List<Still> {
    val load = rememberLoad("stills", id) { it.getItem(id) }
    val item = load.value ?: return emptyList()
    return com.gugugaga.app.ui.rememberStills(item)
}

/**
 * 没有自己分集图的那一集用什么顶上（用户 2026-11-15：「电脑端 有些剧照不显示呢」——新播的剧，后面几集服务器上还没有分集图，
 * 以前整排「NO STILL」）：这部剧的剧照按集号轮着用（每张卡不一样），没有剧照就用剧的横图；都没有 = null（还是集号排版）。纯函数，有单测。
 */
fun episodeStillFallback(episodeNo: Int?, stills: List<Still>, seriesImage: String?): String? {
    if (stills.isEmpty()) return seriesImage
    val n = ((episodeNo ?: 1) - 1).mod(stills.size)
    return stills[n].thumb
}

/** 剧照少于这个数整栏不出现。 */
const val MIN_STILLS = 2

fun showStills(stills: List<Still>): Boolean = stills.size >= MIN_STILLS

/**
 * 剧照一栏：标题「剧照 STILLS · n 张」，下面一排 16:9 的图，横滑；点一张 → [onOpen]（第几张）。
 * [cardWidth]：手机 232dp，电脑按窗口宽传更大的。[arrows]：电脑上两头放翻页箭头（用户 2026-11-16：「剧照也是一样的，也看不到右边的」）。
 */
@Composable
fun StillsBand(stills: List<Still>, onOpen: (Int) -> Unit, modifier: Modifier = Modifier, cardWidth: Dp = 232.dp, margin: Dp = Guga.dimens.margin, header: Boolean = true, arrows: Boolean = false) {
    if (!showStills(stills)) return
    val colors = Guga.colors
    Column(modifier.testTag("detail-stills")) {
        if (header) SectionHeader("剧照", "STILLS", Modifier.padding(horizontal = margin), trailing = "${stills.size} 张")
        val scroll = rememberScrollState()
        Box(Modifier.padding(top = if (header) 16.dp else 0.dp).fillMaxWidth()) {
            Row(Modifier.horizontalScroll(scroll).padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                stills.forEachIndexed { i, s ->
                    val press = rememberPressState()
                    Box(
                        Modifier.width(cardWidth).aspectRatio(16f / 9f)
                            .pressGraphics(press)
                            .pressable(press, label = "剧照 ${i + 1}", onClick = { onOpen(i) })
                            .background(colors.inkWell)
                            .testTag("still-$i"),
                    ) {
                        CoverImage(s.thumb, Modifier.fillMaxSize())
                    }
                }
            }
            if (arrows) {
                val page = with(androidx.compose.ui.platform.LocalDensity.current) { (cardWidth + 10.dp).toPx() * 3 }
                ScrollArrows(scroll, page, top = cardWidth * (9f / 16f) / 2 - cardWidth * 0.09f, inset = margin / 2, size = cardWidth * 0.18f, tag = "d-stills")
            }
        }
    }
}

/**
 * 看大图：整屏黑底，图按原比例放进屏幕（不裁）；左右滑 / ← → / 遥控器左右翻，点一下、返回键、Esc 关。
 * 左上角是返回（三端的规矩：返回永远在左上角）；底下写「03 / 12」和图注。先画已经在缓存里的小图，大图到了再换（不闪）。
 */
@Composable
fun StillsViewer(stills: List<Still>, start: Int, onClose: () -> Unit) {
    if (stills.isEmpty()) return
    val colors = Guga.colors
    val type = Guga.type
    val pager = rememberPagerState(initialPage = start.coerceIn(0, stills.size - 1)) { stills.size }
    val scope = rememberCoroutineScope()
    val close = rememberUpdatedState(onClose)
    val back = LocalBackDispatcher.current
    DisposableEffect(back) {
        val off = back.push {
            close.value()
            true
        }
        onDispose { off() }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun go(delta: Int) {
        val to = (pager.currentPage + delta).coerceIn(0, stills.size - 1)
        if (to != pager.currentPage) scope.launch { pager.animateScrollToPage(to) }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { go(-1); true }
                    Key.DirectionRight -> { go(1); true }
                    // 电视：上下键吃掉，不让系统的焦点搜索把焦点挪到大图后面的页面上
                    Key.DirectionUp, Key.DirectionDown -> true
                    Key.Escape -> { close.value(); true }
                    else -> false
                }
            }
            .focusable()
            .testTag("stills-viewer"),
    ) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { stills[it].key }) { page ->
            val s = stills[page]
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close.value() } }) {
                StillPicture(s)
            }
        }
        // 左上角：返回
        val press = rememberPressState()
        Box(
            Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = Guga.dimens.statusBar + 12.dp)
                .pressGraphics(press)
                .pressable(press, label = "关闭剧照", onClick = { close.value() })
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("stills-close"),
        ) {
            BasicText("‹ 返回", style = Guga.song(type.caption, FontWeight.Bold, colors.ink), maxLines = 1)
        }
        // 底下：第几张 + 图注
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
            val s = stills[pager.currentPage]
            BasicText(
                "${(pager.currentPage + 1).toString().padStart(2, '0')} / ${stills.size.toString().padStart(2, '0')}  ·  剧照 STILLS",
                style = Guga.note(type.caption, colors.dim),
                maxLines = 1,
            )
            s.caption?.takeIf { it.isNotBlank() }?.let {
                BasicText(it, Modifier.padding(top = 4.dp), style = Guga.song(type.caption, color = colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 一张大图：小图先顶上（已经在缓存里），大图解码完再换；按原比例放进盒子。 */
@Composable
private fun StillPicture(still: Still) {
    val small = rememberImage(still.thumb, keepPrevious = true)
    val large = rememberImage(still.src, keepPrevious = true)
    val img = large ?: small
    Canvas(Modifier.fillMaxSize()) { img?.let { drawFit(it) } }
}

/** 按原比例放进画布正中（不裁）。 */
fun DrawScope.drawFit(image: ImageBitmap) {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    if (iw <= 0f || ih <= 0f) return
    val scale = min(size.width / iw, size.height / ih)
    val w = (iw * scale).roundToInt()
    val h = (ih * scale).roundToInt()
    val x = ((size.width - w) / 2f).roundToInt()
    val y = ((size.height - h) / 2f).roundToInt()
    drawImage(image, dstOffset = IntOffset(x, y), dstSize = IntSize(w, h), filterQuality = FilterQuality.Medium)
}

/** 纯函数：一张 [w]×[h] 的图放进 [box] 时的位置和尺寸（测试用）。 */
fun fitRect(w: Float, h: Float, box: Size): Pair<Offset, Size> {
    val scale = min(box.width / w, box.height / h)
    val s = Size(w * scale, h * scale)
    return Offset((box.width - s.width) / 2f, (box.height - s.height) / 2f) to s
}

/**
 * 电脑 / 电视的看大图浮层：详情页是一条长滚动列，大图要盖住整个窗口，所以画在根上（和右键菜单一样，GugaartApp 里 [Host] 一次）。
 * 手机的详情页自己就是整屏，直接在页里画 [StillsViewer]。
 */
object StillsOverlay {
    var stills: List<Still> by androidx.compose.runtime.mutableStateOf(emptyList())
        private set
    var start: Int? by androidx.compose.runtime.mutableStateOf(null)
        private set

    fun open(list: List<Still>, index: Int) {
        stills = list
        start = index
    }

    fun close() {
        start = null
    }

    @Composable
    fun Host() {
        val s = start ?: return
        StillsViewer(stills, s) { close() }
    }
}

/** 截图测试用：详情页一打开就看第几张大图（和上游 `LocalAutoOpenStill` 同一个意思）；应用里永远是 null。 */
val LocalAutoOpenStill = androidx.compose.runtime.compositionLocalOf<Int?> { null }
