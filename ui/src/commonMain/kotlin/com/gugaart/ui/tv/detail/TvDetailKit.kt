package com.gugaart.ui.tv.detail

import androidx.compose.foundation.layout.heightIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.BRIGHT_IMAGE_LUMA
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.Seal
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.components.hatch
import com.gugaart.ui.components.isDarkLogo
import com.gugaart.ui.components.meanLuma
import com.gugaart.ui.components.rememberCrossfade
import com.gugaart.ui.components.rememberSoft
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvClock
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.TvFocusable
import com.gugaart.ui.tv.TvSpec
import com.gugugaga.app.data.rememberImage
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.LocalMotionPrefs
import kotlin.math.roundToInt

/**
 * 电视详情 / 系统页的版式数（设计稿 `tv/css/pages.css` `tv.css`，电视密度按 1：dp = 设计稿的 px）。
 */
object TvDetailSpec {
    /** 第一屏文字块：左 136、底 112、宽 1080。 */
    val HERO_WIDTH = 1080.dp
    val HERO_BOTTOM = 112.dp
    /** 片名盒子：620 × 180（logo 贴左下）。 */
    val LOGO_W = 620.dp
    val LOGO_H = 180.dp
    /** 宋体片名的四档字号（≤ 5 字 100，≤ 7 字 72，≤ 10 字 52，更长 44 两行）。 */
    val TITLE_TIERS = listOf(100.sp, 72.sp, 52.sp, 44.sp)
    /** 评分的大数字。 */
    val RATING = 68.sp
    /** 下面那行「分集 40 · 演职人员 14 · 相似」离屏幕底。 */
    val DOWN_BOTTOM = 42.dp
    /** 第二 / 三屏的页眉：高 136（含上安全边 40），下面 2 px 主线。 */
    val EH_HEIGHT = 136.dp
    val EH_LOGO_W = 300.dp
    val EH_LOGO_H = 76.dp
    /** 横排卡片的间距。 */
    val CARD_GAP = 32.dp
    /** 分集卡 400 × 225；演员 170 × 226；海报 214 × 321。 */
    val EP_H = 225.dp
    val PERSON_H = 226.dp
    val POSTER_H = 321.dp
    /** 格子（季、分页）：高 58。 */
    val CHIP_H = 58.dp
    /** 翻屏的时间。 */
    const val PAGE_MS = 320
    /** 焦点进了第二屏以后，整幅剧照退到这个浓度。 */
    const val DEEP_ALPHA = 0.13f
    /** 电影：右边页边选项栏从 x = 1280 起。 */
    val SIDE_LEFT = 1280.dp
}

/** 字数 → 四档（设计稿 `U.logo`：≤ 5 / ≤ 7 / ≤ 10 / 更长）。纯函数，有单测。 */
fun tvTitleTier(name: String): Int {
    val n = name.trim().length
    return when {
        n <= 5 -> 0
        n <= 7 -> 1
        n <= 10 -> 2
        else -> 3
    }
}

/** 导语只取第一句（电视详情第一屏；全文在「简介全文」页）。没有句号就按 [max] 字截断加省略号。纯函数，有单测。 */
fun tvFirstSentence(overview: String, max: Int = 92): String {
    val t = overview.trim()
    if (t.isEmpty()) return ""
    val end = t.indexOfFirst { it == '。' || it == '！' || it == '？' || it == '!' || it == '?' }
    val first = if (end in 0 until t.length) t.substring(0, end + 1) else t
    return if (first.length <= max) first else first.take(max - 1).trimEnd('，', '、', ' ') + "…"
}

/**
 * 详情页的整幅剧照（设计稿 `.dt-bd`）：图从屏幕最顶端铺起（取景 50 % / 12 %），**左侧和底部各一层暗角**，文字只放在暗角里；
 * 亮图（平均亮度 > 46 %）自动把暗角加深一档。没有剧照 → 海报虚化铺满。焦点进了下一屏（[deep]）→ 整幅退到 13 %。
 * 暗角是画进图里的，不是叠在内容上的遮罩。
 */
@Composable
fun TvBackdrop(art: Art, deep: Boolean, modifier: Modifier = Modifier, sided: Boolean = false, heavy: Boolean = false, framing: Framing = Framing(0.5f, 0.12f)) {
    val colors = Guga.colors
    val url = art.backdrop ?: art.poster
    val blur = art.backdrop == null
    val bmp = rememberImage(url, keepPrevious = true)
    val cf = rememberCrossfade(bmp)
    val soft = rememberSoft(cf.current.takeIf { blur })
    val bright = remember(cf.current) { cf.current?.let { it.meanLuma() > BRIGHT_IMAGE_LUMA } == true }
    val reduced = LocalMotionPrefs.current.reducedMotion
    val a by animateFloatAsState(if (deep) TvDetailSpec.DEEP_ALPHA else 1f, tween(if (reduced) 0 else 350), label = "tv-bd")
    Canvas(modifier.fillMaxSize().testTag(if (bright) "tv-backdrop-bright" else "tv-backdrop")) {
        drawRect(colors.paper)
        val img = if (blur) soft else cf.current
        if (img == null) {
            hatch(colors.line.copy(alpha = 0.06f), 9.dp.toPx(), 1.dp.toPx())
        } else {
            if (!blur) cf.previous?.let { drawCover(it, framing) }
            drawCover(img, if (blur) Framing(0.5f, 0.5f) else framing, if (blur) 1f else cf.alpha, quality = if (blur) FilterQuality.High else FilterQuality.Medium)
            if (blur) drawRect(colors.paper.copy(alpha = 0.34f))
        }
        val p = colors.paper
        // 左侧暗角（文字栏）
        val side = when {
            heavy -> arrayOf(0f to p.copy(alpha = 0.95f), 0.30f to p.copy(alpha = 0.88f), 0.50f to p.copy(alpha = 0.55f), 0.68f to p.copy(alpha = 0.1f), 1f to Color.Transparent)
            bright -> arrayOf(0f to p.copy(alpha = 0.94f), 0.26f to p.copy(alpha = 0.84f), 0.54f to p.copy(alpha = 0.4f), 0.78f to p.copy(alpha = 0.12f), 1f to p.copy(alpha = 0.12f))
            else -> arrayOf(0f to p.copy(alpha = 0.9f), 0.24f to p.copy(alpha = 0.72f), 0.52f to p.copy(alpha = 0.18f), (if (sided) 0.66f else 0.72f) to Color.Transparent, 1f to Color.Transparent)
        }
        drawRect(Brush.horizontalGradient(*side))
        // 顶部 + 底部暗角（页眉压在图上；底部渐隐进纸面）
        val vert = if (bright) {
            arrayOf(0f to p.copy(alpha = 0.9f), 0.12f to p.copy(alpha = 0.4f), 0.30f to p.copy(alpha = 0.1f), 0.70f to p.copy(alpha = 0.86f), 0.90f to p, 1f to p)
        } else {
            arrayOf(0f to p.copy(alpha = 0.72f), 0.14f to p.copy(alpha = 0.05f), 0.36f to Color.Transparent, 0.72f to p.copy(alpha = 0.8f), 0.92f to p, 1f to p)
        }
        drawRect(Brush.verticalGradient(*vert))
        if (a < 1f) drawRect(p.copy(alpha = 1f - a))
    }
}

/** 压在整幅图上的页眉（设计稿 `.rhead`）：左「咕咕嘎嘎」+ 英文小标，右 图注（这是哪一集的剧照，电视上横排）+ 时钟。 */
@Composable
fun TvRunHead(english: String, caption: String?, today: Today, modifier: Modifier = Modifier, badges: List<com.gugaart.ui.player.FormatBadge> = emptyList()) {
    val colors = Guga.colors
    // 右上角（用户 2026-11-17：「右上角的 剧照|主视觉 时间 太小了，看不清。把 4k hdr那些放到右上角会不会好点」）：
    // 有片源格式就放徽章（1.5 倍；不看设备能力，照常亮），没有才写说明；说明 22 → 28，时间 27 → 34
    Row(
        modifier.fillMaxWidth().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = TvSpec.TOP).heightIn(min = 52.dp).testTag("tv-run-head"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("咕咕嘎嘎", style = Guga.song(24.sp, FontWeight.Black, letterSpacing = 0.3.em), maxLines = 1)
        Box(Modifier.padding(start = 4.dp, end = 18.dp).width(1.dp).height(22.dp).background(colors.line))
        BasicText(english, style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (badges.isNotEmpty()) com.gugaart.ui.player.FormatBadgeRow(badges, 1.5f, Modifier.padding(end = 30.dp), notes = false)
        else if (caption != null) BasicText(caption, Modifier.padding(end = 30.dp), style = Guga.song(28.sp, FontWeight.SemiBold, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TvClock(today, 34.sp)
    }
}

/**
 * 详情页的片名（设计稿 `.hb .logo-w`）：clearlogo 贴左下；黑笔画 logo 垫**略微倾斜的**米白衬纸；没有 logo → 宋体，按字数四档
 * 100 / 72 / 52 / 44（最长一档两行）。[small]：第二 / 三屏页眉里的小号（300 × 76，宋体 44 单行）。
 */
@Composable
fun TvTitle(title: Title, modifier: Modifier = Modifier, small: Boolean = false, width: Dp = if (small) TvDetailSpec.EH_LOGO_W else TvDetailSpec.LOGO_W, height: Dp = if (small) TvDetailSpec.EH_LOGO_H else TvDetailSpec.LOGO_H) {
    val textOnly = LocalPreset.current.textTitles
    val logo = if (textOnly) null else rememberImage(title.art.logo, keepPrevious = false)
    if (title.art.logo != null && !textOnly) {
        // 上游图片到了以后由 TitleLogo 处理（黑笔画 → 衬纸，电视倾斜）；没到之前先出宋体片名
        val dark = remember(logo) { logo?.isDarkLogo() == true }
        Box(modifier.testTag(if (logo == null) "tv-title-text" else if (dark) "tv-title-backed" else "tv-title-logo")) {
            TitleLogo(title.name, title.art.logo, width, if (dark && !small) height - 10.dp else height, baseSize = if (small) 44.sp else TvDetailSpec.TITLE_TIERS[tvTitleTier(title.name)] / Rules.TITLE_SCALE[Rules.titleTier(title.name)])
        }
    } else {
        val tier = tvTitleTier(title.name)
        val size = if (small) 44.sp else TvDetailSpec.TITLE_TIERS[tier]
        BasicText(
            title.name,
            modifier.then(if (small) Modifier else Modifier.width(TvDetailSpec.HERO_WIDTH)).testTag("tv-title-text"),
            style = Guga.song(size, FontWeight.Black, lineHeight = size * 1.12f, letterSpacing = 0.04.em),
            maxLines = if (small) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 评分 + 「TMDB 评分」+ 一行资料（「全 40 集 · 每集约 45 分 · KR-15 · iQiyi」）。没有评分写「暂无评分」，不占大数字的位置。 */
@Composable
fun TvMeta(rating: String?, facts: List<String>, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (rating != null) {
            BasicText(rating, style = Guga.numeral(TvDetailSpec.RATING), maxLines = 1)
            BasicText("TMDB\n评分", Modifier.padding(start = 14.dp), style = Guga.song(18.sp, color = colors.dim, lineHeight = 24.sp), maxLines = 2)
        } else {
            BasicText("暂无\n评分", style = Guga.song(18.sp, color = colors.dim, lineHeight = 24.sp), maxLines = 2)
        }
        if (facts.isNotEmpty()) {
            BasicText(facts.joinToString("　·　"), Modifier.padding(start = 32.dp), style = Guga.song(25.sp, FontWeight.SemiBold, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 屏幕左下那行灰字：「⌄ 分集 40 · 演职人员 14 · 相似」——下面还有什么。 */
@Composable
fun BoxScope.TvDownHint(parts: List<String>) {
    if (parts.isEmpty()) return
    val colors = Guga.colors
    Row(Modifier.align(Alignment.BottomStart).padding(start = TvSpec.LEFT, bottom = TvDetailSpec.DOWN_BOTTOM), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(GuguIcon.ChevronDown, colors.spot, 26.dp)
        BasicText(parts.joinToString("　·　"), style = Guga.song(22.sp, color = colors.faint, letterSpacing = 0.06.em), maxLines = 1)
    }
}

/** 区块标题（设计稿 `.sec-h`）：「演职人员 CAST ———— 14 位」。 */
@Composable
fun TvSecHead(title: String, english: String, modifier: Modifier = Modifier, trailing: String? = null, small: Boolean = false) {
    val colors = Guga.colors
    Row(modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        BasicText(title, style = Guga.song(if (small) 30.sp else 38.sp, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
        BasicText(english, Modifier.padding(top = 10.dp), style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
        if (trailing != null) BasicText(trailing, style = Guga.note(23.sp, colors.dim), maxLines = 1)
    }
}

/** 第二 / 三屏的页眉（设计稿 `.eh`）：左 小号片名，右 一句斜体 + 时钟；下面 2 px 米白主线。 */
@Composable
fun TvSectionTop(title: Title, note: String, today: Today, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = TvSpec.LEFT, end = TvSpec.RIGHT)
            .height(TvDetailSpec.EH_HEIGHT)
            .drawBehind { drawLine(colors.ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
            .padding(top = TvSpec.TOP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvTitle(title, small = true)
        Spacer(Modifier.weight(1f))
        BasicText(note, style = Guga.note(24.sp, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.width(24.dp))
        TvClock(today)
    }
}

/**
 * 「一屏一件事，上下键整屏翻」：[count] 屏竖着排，[screen] 是现在这一屏；翻屏 320 ms（减弱动效直接换）。
 * 每一屏都是整屏大小，都在组合里（焦点可以直接交给下一屏的成员）；屏外的不画。
 */
@Composable
fun TvScreens(screen: Int, count: Int, modifier: Modifier = Modifier, content: @Composable (index: Int) -> Unit) {
    val reduced = LocalMotionPrefs.current.reducedMotion
    val at by animateFloatAsState(screen.toFloat(), tween(if (reduced) 0 else TvDetailSpec.PAGE_MS), label = "tv-screens")
    Layout(
        content = { for (i in 0 until count) Box(Modifier.fillMaxSize().testTag("tv-screen-$i")) { content(i) } },
        modifier = modifier.fillMaxSize().clipToBounds().testTag("tv-screens-$screen"),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p -> p.place(0, ((i - at) * constraints.maxHeight).roundToInt()) }
        }
    }
}

/**
 * 一排横着的卡片（设计稿 `.hs[data-lead]`）：**焦点卡永远停在左起第一格**，栏内容滑动，滑过去的卡淡出（不在侧栏底下留半个字）。
 * 这个组上次停的那张卡在第一格；只有第一格前后一小段在组合里（一季 50 集不会一次取 50 张图）。
 * 左右 / 上下怎么走由 [TvGroups] 管（这一排自己登记成一个横向的组）。
 */
@Composable
fun <T> TvLeadRow(
    group: String,
    items: List<T>,
    keyOf: (T) -> String,
    itemWidth: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = TvDetailSpec.CARD_GAP,
    start: Dp = TvSpec.LEFT,
    default: String? = null,
    /** 这一排能看到几张（窗口）；页边栏打开时会更少。 */
    visible: Int = 6,
    content: @Composable (item: T, key: String, focused: Boolean) -> Unit,
) {
    val groups = LocalTvGroups.current
    val keys = remember(items) { items.map(keyOf) }
    TvGroup(group, keys, horizontal = true, default = default)
    val leadKey = groups.lastOf(group)?.takeIf { it in keys } ?: default?.takeIf { it in keys }
    val lead = (if (leadKey != null) keys.indexOf(leadKey) else 0).coerceAtLeast(0)
    val reduced = LocalMotionPrefs.current.reducedMotion
    val at by animateFloatAsState(lead.toFloat(), tween(if (reduced) 0 else 220), label = "tv-lead")
    val from = (lead - 2).coerceAtLeast(0)
    val to = (lead + visible + 1).coerceAtMost(items.size - 1)
    Layout(
        content = {
            for (i in from..to) key(keys[i]) {
                val past by animateFloatAsState(if (i < lead) 0f else 1f, tween(if (reduced) 0 else 200), label = "tv-past")
                Box(Modifier.graphicsLayer { alpha = past }) { content(items[i], keys[i], groups.current == keys[i]) }
            }
        },
        modifier = modifier.fillMaxWidth().height(height).testTag("tv-row-$group"),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val step = (itemWidth + gap).toPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { n, p -> p.place((start.toPx() + (from + n - at) * step).roundToInt(), 0) }
        }
    }
}

/** 图片卡的焦点块：米白 4 px 框 + 校对角 + 抬起（`TvFocusable(Card)`），并把焦点报给这一屏的 [TvGroups]。 */
@Composable
fun TvCardFocus(key: String, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, default: Boolean = false, onLongClick: (() -> Unit)? = null, content: @Composable BoxScope.(focused: Boolean) -> Unit) {
    val groups = LocalTvGroups.current
    TvFocusable(onClick, modifier.tvMember(groups, key), TvFocusKind.Card, key, default, label, onLongClick = onLongClick, content = content)
}

/** 整块变米白的焦点块（按钮、行、格子、选项），同样报给 [TvGroups]。 */
@Composable
fun TvBlockFocus(key: String, onClick: () -> Unit, modifier: Modifier = Modifier, label: String? = null, default: Boolean = false, kind: TvFocusKind = TvFocusKind.Block, onLongClick: (() -> Unit)? = null, content: @Composable BoxScope.(focused: Boolean) -> Unit) {
    val groups = LocalTvGroups.current
    TvFocusable(onClick, modifier.tvMember(groups, key), kind, key, default, label, onLongClick = onLongClick, content = content)
}

/**
 * 电视的按钮（设计稿 `.btn`）：高 76、米白 50 % 线框、压在图上垫 50 % 墨底；焦点 = 米白实底墨字 + 校对角。
 * [sub]：按钮里的斜体小字（「余 19 分」「1080p」）；[progress]：按钮下沿外面的一条进度线（续看）；[slot]：靠右的一格（收藏的印章位）。
 */
@Composable
fun TvActionButton(
    key: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: GuguIcon? = null,
    primary: Boolean = false,
    sub: String? = null,
    progress: Float = 0f,
    default: Boolean = false,
    slot: (@Composable (focused: Boolean) -> Unit)? = null,
) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val focusedNow = groups.current == key
    TvFocusable(
        onClick,
        modifier
            .tvMember(groups, key)
            .then(if (focusedNow) Modifier else Modifier.background(colors.paper.copy(alpha = 0.5f)).border(2.dp, colors.ink.copy(alpha = 0.5f)))
            .drawBehind {
                if (progress > 0f) {
                    val y = size.height + 10.dp.toPx()
                    drawLine(colors.line, Offset(0f, y), Offset(size.width, y), 5.dp.toPx())
                    drawLine(colors.spot, Offset(0f, y), Offset(size.width * progress.coerceIn(0f, 1f), y), 5.dp.toPx())
                }
            },
        TvFocusKind.Block, key, default, text,
    ) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(Modifier.height(76.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (icon != null) Icon(icon, if (focused) colors.onInk else if (primary) colors.spot else colors.ink, 30.dp, filled = icon == GuguIcon.Play)
            BasicText(text, style = Guga.song(30.sp, FontWeight.Bold, ink, letterSpacing = 0.06.em), maxLines = 1)
            if (sub != null) BasicText(sub, style = Guga.note(23.sp, if (focused) colors.onInk.copy(alpha = 0.66f) else colors.dim), maxLines = 1)
            slot?.invoke(focused)
        }
    }
}

/** 收藏按钮里的印章位（42 的虚线格，章落在里面；设计稿 `.fav-slot`）。 */
@Composable
fun TvSealSlot(sealed: Boolean, focused: Boolean) {
    com.gugaart.ui.components.SealSlot(sealed, size = 42.dp)
}

/** 格子（季 / 分页 / 筛选，设计稿 `.chip`）：高 58、细线框；选中 = 专色框 + 专色小方块；焦点 = 米白实底。[count]：斜体小数字。 */
@Composable
fun TvChip(key: String, text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, count: String? = null, numeral: Boolean = false, leading: (@Composable () -> Unit)? = null, height: Dp = TvDetailSpec.CHIP_H) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val focusedNow = groups.current == key
    TvFocusable(
        onClick,
        modifier.tvMember(groups, key).then(if (focusedNow) Modifier else Modifier.border(1.5.dp, if (selected) colors.spot else colors.faint)),
        TvFocusKind.Block, key, false, text,
    ) { focused ->
        val ink = if (focused) colors.onInk else if (selected) colors.spot else colors.ink
        Row(Modifier.height(height).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            leading?.invoke()
            if (selected) Box(Modifier.size(10.dp).background(if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot))
            BasicText(text, style = if (numeral) Guga.numeral(25.sp, ink) else Guga.song(25.sp, FontWeight.SemiBold, ink), maxLines = 1)
            if (count != null) BasicText(count, style = Guga.numeral(21.sp, ink.copy(alpha = 0.7f)), maxLines = 1)
        }
    }
}

/** 一排格子，焦点格滑进视野（季多的时候：航海王 22 季）。成员登记成一个横向的组。 */
@Composable
fun TvChipRow(group: String, keys: List<String>, modifier: Modifier = Modifier, default: String? = null, gap: Dp = 14.dp, trailing: (@Composable RowScope.() -> Unit)? = null, chip: @Composable (index: Int, key: String) -> Unit) {
    val groups = LocalTvGroups.current
    TvGroup(group, keys, horizontal = true, default = default)
    val leadKey = groups.lastOf(group)?.takeIf { it in keys } ?: default
    val lead = keys.indexOf(leadKey).coerceAtLeast(0)
    // 焦点格左边留三格：往回走时看得见前面的
    val reduced = LocalMotionPrefs.current.reducedMotion
    var widths = remember(keys) { IntArray(keys.size) }
    val firstShown = (lead - 3).coerceAtLeast(0)
    val at by animateFloatAsState(firstShown.toFloat(), tween(if (reduced) 0 else 220), label = "tv-chips")
    Layout(
        content = {
            for (i in keys.indices) key(keys[i]) { Box { chip(i, keys[i]) } }
            if (trailing != null) Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        },
        modifier = modifier.fillMaxWidth().height(TvDetailSpec.CHIP_H).testTag("tv-chips-$group"),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        if (widths.size != keys.size) widths = IntArray(keys.size)
        val g = gap.roundToPx()
        val xs = IntArray(placeables.size)
        var x = 0
        placeables.forEachIndexed { i, p ->
            xs[i] = x
            x += p.width + g
        }
        val i0 = at.toInt().coerceIn(0, (keys.size - 1).coerceAtLeast(0))
        val frac = at - i0
        val shift = if (keys.isEmpty()) 0f else xs[i0] + (xs.getOrElse(i0 + 1) { xs[i0] } - xs[i0]) * frac
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { i, p ->
                val px = TvSpec.LEFT.roundToPx() + xs[i] - shift.roundToInt()
                // 滑到安全边左边的不画（不在侧栏底下留半个字）
                if (px >= TvSpec.LEFT.roundToPx() - 2) p.place(px, (constraints.maxHeight - p.height) / 2)
            }
        }
    }
}

/**
 * 分集卡（400 × 225，设计稿 `.card.wd.ep`）：剧照；**没剧照的集 = 斜体大集号 + 片名排版**（不拿整部剧同一张图顶 155 次）；
 * 进度线在图的下沿；已看 = 右上角一笔朱红校对勾（**没有方格**）。下面：斜体集号 + 集名（焦点时由灰变白）+ 副行。
 */
@Composable
fun TvEpisodeCard(e: Title, key: String, played: Boolean, current: Boolean, subtitle: String?, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier, fallback: String? = null) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val focused = groups.current == key
    val no = e.episodeNo
    val name = e.name.ifEmpty { no?.let { "第 $it 集" } ?: "" }
    Column(modifier.width(TvSpec.EPISODE_W)) {
        TvCardFocus(key, onClick, Modifier.graphicsLayer { translationX = if (focused) TvSpec.EPISODE_W.toPx() * 0.04f else 0f }, label = name, onLongClick = onLongClick) {
            Box(Modifier.width(TvSpec.EPISODE_W).height(TvDetailSpec.EP_H).border(1.dp, colors.ink.copy(alpha = 0.16f)).testTag(if (e.art.still == null) "tv-ep-nopic" else "tv-ep-pic")) {
                // 没有自己的分集图：用这部剧的剧照顶上（episodeStillFallback）；连剧照都没有才是集号排版
                val img = e.art.still ?: fallback
                if (img != null) {
                    CoverImage(img, Modifier.fillMaxSize(), Framing(0.5f, 0.3f))
                } else {
                    // 没有剧照：集号排版
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A251C), Color(0xFF17140F)))).padding(18.dp)) {
                        BasicText(no?.toString() ?: "—", style = Guga.numeral(92.sp, colors.spot), maxLines = 1)
                        BasicText(name, Modifier.align(Alignment.BottomStart), style = Guga.song(28.sp, FontWeight.Black, lineHeight = 35.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (e.progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.dp).background(colors.paper.copy(alpha = 0.6f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(e.progress.coerceIn(0f, 1f)).height(6.dp).background(colors.spot))
                }
                if (played) ProofTick(true, Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 10.dp), size = 26.dp, box = false)
            }
        }
        Row(Modifier.padding(top = 14.dp).graphicsLayer { translationX = if (focused) 4.dp.toPx() else 0f; translationY = if (focused) 10.dp.toPx() else 0f }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (no != null) BasicText(no.toString(), style = Guga.numeral(25.sp, colors.spot), maxLines = 1)
            BasicText(name, style = Guga.song(25.sp, FontWeight.Bold, if (focused) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (!subtitle.isNullOrEmpty()) {
            BasicText(
                subtitle,
                Modifier.padding(top = 4.dp).graphicsLayer { translationX = if (focused) 4.dp.toPx() else 0f; translationY = if (focused) 10.dp.toPx() else 0f },
                style = Guga.song(22.sp, color = if (current) colors.spot else if (focused) colors.dim else colors.faint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 演员格（170 × 226，**彩色**；没照片 = 姓名首字母）。名字两行、角色两行截断。 */
@Composable
fun TvPersonCard(p: Person, key: String, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = TvSpec.PERSON_W, height: Dp = TvDetailSpec.PERSON_H) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val focused = groups.current == key
    Column(modifier.width(width)) {
        TvCardFocus(key, onClick, label = p.name) {
            Box(Modifier.width(width).height(height).border(1.dp, colors.ink.copy(alpha = 0.16f)).testTag(if (p.photo == null) "tv-person-nopic" else "tv-person-pic")) {
                if (p.photo != null) {
                    CoverImage(p.photo, Modifier.fillMaxSize(), Framing(0.5f, 0.2f)) { TvMonogram(p.name) }
                } else {
                    TvMonogram(p.name)
                }
            }
        }
        BasicText(p.name, Modifier.padding(top = 14.dp), style = Guga.song(23.sp, FontWeight.Bold, if (focused) colors.ink else colors.dim, lineHeight = 30.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        p.role?.let { BasicText("饰 $it", Modifier.padding(top = 4.dp), style = Guga.song(20.sp, color = colors.faint, lineHeight = 26.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun TvMonogram(name: String) {
    val colors = Guga.colors
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A251C), Color(0xFF17140F)))), contentAlignment = Alignment.Center) {
        BasicText(Rules.monogram(name, person = true), style = Guga.song(64.sp, FontWeight.Black, colors.faint), maxLines = 1)
    }
}

/** 海报卡（214 × 321）：没海报 = 片名排版；已收藏 = 右上一枚小印；已看 = 校对勾。下面片名两行 + 副行。 */
@Composable
fun TvPosterCard(t: Title, key: String, onClick: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = listOfNotNull(t.year?.toString(), t.rating?.let { "★ $it" }).joinToString(" · "), number: String? = null, onLongClick: (() -> Unit)? = null, favorite: Boolean = t.favorite, default: Boolean = false, width: Dp = TvSpec.POSTER) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val focused = groups.current == key
    val height = width * 1.5f
    Column(modifier.width(width)) {
        TvCardFocus(key, onClick, label = t.name, default = default, onLongClick = onLongClick) {
            Box(Modifier.width(width).height(height).border(1.dp, colors.ink.copy(alpha = 0.16f))) {
                val plate: @Composable () -> Unit = {
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A251C), Color(0xFF17140F)))).padding(18.dp), contentAlignment = Alignment.BottomStart) {
                        BasicText(t.name, style = Guga.song(30.sp, FontWeight.Black, lineHeight = 38.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (t.art.poster != null) CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f), placeholder = plate) else plate()
                if (favorite) Seal(Modifier.align(Alignment.TopEnd).padding(10.dp).graphicsLayer { rotationZ = -7f }, size = 46.dp)
                if (t.progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(6.dp).background(colors.paper.copy(alpha = 0.6f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(t.progress.coerceIn(0f, 1f)).height(6.dp).background(colors.spot))
                }
            }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (number != null) BasicText(number, style = Guga.numeral(25.sp, colors.spot), maxLines = 1)
            BasicText(t.name, style = Guga.song(25.sp, FontWeight.Bold, if (focused) colors.ink else colors.dim, lineHeight = 32.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!subtitle.isNullOrEmpty()) BasicText(subtitle, Modifier.padding(top = 4.dp), style = Guga.song(22.sp, color = if (focused) colors.dim else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 栏目行：「剧集 · 悬疑 喜剧 剧情 · 2024」（专色、字距 0.16 em）。 */
@Composable
fun TvKick(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Guga.song(24.sp, FontWeight.Bold, Guga.colors.spot, letterSpacing = 0.16.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 栏目行的字：种类 · 题材（空格隔开，最多三个）· 年份 · 原名。 */
fun tvKickOf(t: Title, kind: String): String =
    listOfNotNull(kind, t.genres.take(3).joinToString(" ").takeIf { it.isNotEmpty() }, t.year?.toString()).joinToString(" · ")

/** 一屏的根：给这一屏一份自己的 [TvGroups]，方向键先问它。 */
@Composable
fun TvGroupsRoot(groups: TvGroups, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val registry = com.gugugaga.ds.focus.LocalFocusRegistry.current
    CompositionLocalProvider(LocalTvGroups provides groups) {
        Box(modifier.fillMaxSize().tvGroupKeys(groups, registry), content = content)
    }
}

/** 种类的中文名（栏目行的第一段）。 */
fun tvKindName(kind: Kind): String = when (kind) {
    Kind.Movie -> "电影"
    Kind.Series -> "剧集"
    Kind.Episode -> "单集"
    Kind.BoxSet -> "合集"
    Kind.Person -> "人物"
    else -> "条目"
}

@Suppress("unused")
private val keepSize = Size.Zero
