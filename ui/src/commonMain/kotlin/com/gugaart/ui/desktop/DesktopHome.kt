package com.gugaart.ui.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverFlow
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.IssueOdometer
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.progressLine
import com.gugaart.ui.components.rememberCoverFlowState
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.LibraryEntry
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.home.HomeData
import com.gugaart.ui.screens.home.HomeShelf
import com.gugaart.ui.screens.home.rememberTocHint
import com.gugaart.ui.screens.home.tocBob
import kotlinx.coroutines.flow.first
import com.gugaart.ui.screens.home.coverAction
import com.gugaart.ui.screens.home.coverKicker
import com.gugaart.ui.screens.home.coverNote
import com.gugaart.ui.screens.home.loadHome
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** 电脑首页的数（设计稿 `d.css`「首页」一节，rem）。 */
object DeskHomeSpec {
    /** 封面跨页：高 = 一整屏，最矮 62；上边让出报头 + 玻璃排（11.6），下边留给页脚目录条（8.4）。 */
    const val SPREAD_MIN = 62f
    const val NAV = 11.6f
    const val SPREAD_BOTTOM = 8.4f

    /**
     * 正中那张海报：宽不超过 46、不超过左页宽的 [COVER_OF_COLUMN]，且高不超过跨页里能用的高
     * （用户 2026-11-15：「画报比例会不会小了点，还有很大的空间……可以的话就大一些比较好」；以前封顶 32，左页留了一大圈空）。
     */
    const val COVER_MAX = 46f

    /** 海报流两侧的海报铺开约是正中那张宽的 2.5 倍：正中取左页宽的 0.46，最外两张在边上露一半。 */
    const val COVER_OF_COLUMN = 0.46f

    /** 两页的比例：7 : 5；≥ 2200 宽 = 8 : 4.2 : 3.8 三栏。 */
    const val LEFT = 7f
    const val RIGHT = 5f
    const val LEFT_XL = 8f
    const val RIGHT_XL = 4.2f
    const val THIRD_XL = 3.8f

    /** 页脚目录条：高 4.4，离底 2。 */
    const val FOLD = 4.4f
    const val FOLD_BOTTOM = 2f

    /** 目录区的区块滚到这个位置停（玻璃排之下再让 6 dp）。 */
    val SECTION_NUDGE = 6.dp

    /** 继续观看的头条：进度过半、有图的头一条。 */
    const val FEATURE_FROM = 0.5f

    /** 头条右边排几行；跨页第三栏「接着看」排几行。 */
    const val FEATURE_SIDE_ROWS = 5
    const val THIRD_ROWS = 4

    /** 吸附：停在离封面 / 目录这么近（占跨页高的比例）以内就吸过去。 */
    const val SNAP_NEAR = 0.3f
}

/** 目录行 / 头条的副行：「第 5 集 · 江心白南霁风组队 · 余 18 分」「电影 · 2 小时 14 分 · 余 78 分」；没开始写「还没开始」。纯函数，有单测。 */
fun deskTocSub(t: Title): String {
    val head = when {
        t.kind == Kind.Episode && t.episodeNo != null -> listOfNotNull("第 ${t.episodeNo} 集", t.name.takeIf { it.isNotEmpty() && it != t.seriesName })
        t.kind == Kind.Movie -> listOfNotNull("电影", t.runtimeSec?.let { Rules.minutes(it) })
        else -> listOf("剧集")
    }
    val tail = if (t.progress <= 0f) "还没开始" else Rules.remaining(t.progress, t.runtimeSec) ?: "${t.pct}%"
    return (head + tail).joinToString(" · ")
}

/** 继续观看里谁上头条：进度过半、有横图的头一条；没有就第一条。纯函数，有单测。 */
fun featureOf(resume: List<Title>): Title? =
    resume.firstOrNull { it.progress > DeskHomeSpec.FEATURE_FROM && (it.art.thumb ?: it.art.backdrop) != null } ?: resume.firstOrNull()

/** 媒体库格子上的英文小标（装饰）：按库的种类，不按库名猜。 */
fun libraryEnglish(kind: String?): String = when (kind) {
    "tvshows" -> "SERIES"
    "movies" -> "FILM"
    "boxsets" -> "SETS"
    "music" -> "MUSIC"
    else -> "LIBRARY"
}

/** 滚动停下来以后吸到哪：离封面近 → 0；离目录近 → [tocY]；中间 / 已经过了目录 → 不动（null）。纯函数，有单测。 */
fun homeSnapTarget(scroll: Float, tocY: Float): Float? {
    if (tocY <= 0f || scroll <= 0f || scroll >= tocY) return null
    return when {
        scroll < tocY * DeskHomeSpec.SNAP_NEAR -> 0f
        scroll > tocY * (1f - DeskHomeSpec.SNAP_NEAR) -> tocY
        else -> null
    }
}

/**
 * 电脑首页（设计稿 `desktop/board.html?p=home`）：封面跨页（左页三维海报流，右页期号 / logo / 导语 / 续看 / 印章位 / 主编手记，3168 宽时三栏）+ 目录区。
 * 取数复用 `screens/home/HomeScreen.kt` 的 `loadHome`。状态（`LocalPreset.state`）：`mid` `fav` `toc` `hover` `focus` `rows` `end` `load`。
 */
@Composable
fun DesktopHomeScreen(today: Today) {
    val load = rememberLoad("home") { loadHome(it) }
    com.gugaart.ui.screens.home.RefreshOnReturn(load)
    val data = load.value
    val forceLoading = LocalPreset.current.state == "load"
    when {
        data != null && !forceLoading -> DesktopHome(data, today, onRefresh = { com.gugaart.ui.screens.home.CoverDeck.requestNew(); load.retry() })
        load.error != null && !forceLoading -> DesktopErrorPage(load.error!!, load::retry)
        else -> DesktopHomeLoading(quiet = !load.showSkeleton && !forceLoading)
    }
}

@Composable
private fun DesktopHome(data: HomeData, today: Today, onRefresh: () -> Unit) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val nav = LocalWideNav.current
    val shell = LocalDesktopShell.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val preset = LocalPreset.current
    val density = LocalDensity.current
    val win = LocalWindowDp.current
    val hour = LocalHour.current
    val xl = win.width.value >= DeskSpec.XL_FROM
    val scroll = rememberScrollState()
    val covers = data.covers
    val flow = rememberCoverFlowState(covers.size, preset.homeIssue)
    val issue = covers.getOrNull(flow.settled)
    val voice = rememberNoteVoice()
    val scope = rememberCoroutineScope()

    // 专色跟正中那一张的海报走
    val poster = rememberImage(issue?.art?.poster, keepPrevious = true)
    LaunchedEffect(poster, issue?.id) { chrome.spot = poster?.spot() }

    // 各区块在滚动内容里的位置（px）：页脚目录条点了能跳过去，预置状态和吸附也用
    val anchors = remember { mutableStateMapOf<String, Float>() }
    val navPx = with(density) { (Guga.rem(DeskHomeSpec.NAV) + DeskHomeSpec.SECTION_NUDGE).toPx() }
    fun Modifier.anchor(key: String): Modifier = onGloballyPositioned { anchors[key] = it.positionInRoot().y + scroll.value }
    fun jump(key: String) {
        val y = anchors[key] ?: return
        scope.launch { scroll.animateScrollTo((y - navPx).toInt().coerceAtLeast(0)) }
    }

    // 键盘：← → 翻封面（还在封面那一屏时），Enter 播放这一期，V / W 作用在这一期；F5 / 点 VOL. = 付印新刊
    DisposableEffect(issue?.id) {
        DesktopKeys.pageTarget = issue
        DesktopKeys.onArrow = { d ->
            if (scroll.value < 200) {
                flow.animateTo(flow.settled + d)
                true
            } else false
        }
        DesktopKeys.onEnter = {
            if (scroll.value < 200 && issue != null) {
                graph.play(issue.id, issue.positionSec?.times(1000))
                true
            } else false
        }
        onDispose {
            DesktopKeys.pageTarget = null
            DesktopKeys.onArrow = null
            DesktopKeys.onEnter = null
        }
    }
    DisposableEffect(Unit) {
        nav.onReprint = {
            // 期号滚上去、封面回到第一期、重新取数；她说一句
            shell.reprints++
            flow.animateTo(0)
            scope.launch { scroll.animateScrollTo(0) }
            onRefresh()
            voice.say(MascotLines.pick("home.fresh", hour = hour, seed = today.day + shell.reprints))
        }
        onDispose { nav.onReprint = null }
    }

    // 没人动时封面自己翻（还在封面那一屏时）；每 10–20 分钟换一波（期号 +1、回到第一期，不抢滚动位置、她不说话）
    // 第一次的「目录在下面」：页脚的「目录」换成反白纸签、她说一句；往下翻过（或者点过栏目名）就算看过了
    val tocHint = rememberTocHint(com.gugugaga.ds.theme.UiMode.Desktop, voice)
    LaunchedEffect(tocHint.show) {
        if (tocHint.show) {
            snapshotFlow { scroll.value > 200 }.first { it }
            tocHint.seen()
        }
    }
    com.gugaart.ui.idle.CoverAutoFlip(flow, visible = { scroll.value < 200 })
    com.gugaart.ui.idle.CoverRotation {
        shell.reprints++
        flow.animateTo(0)
        onRefresh()
    }

    // 预置状态（测试 / 截图）
    val state = preset.state
    LaunchedEffect(state) { if (state == "mid") flow.anim.snapTo((preset.homeIssue + 0.45f).coerceAtMost((covers.size - 1).toFloat())) }
    LaunchedEffect(state, issue?.id) { if (state == "fav" && issue != null) chrome.favorites[issue.seriesId ?: issue.id] = true }
    val stateAnchor = when (state) {
        "toc" -> "toc"
        "hover", "focus" -> "new"
        "rows" -> "libs"
        else -> null
    }
    if (stateAnchor != null || state == "end") {
        val target = if (state == "end") Int.MAX_VALUE else anchors[stateAnchor]?.let { (it - navPx).toInt() }
        LaunchedEffect(target, scroll.maxValue) { if (target != null) scroll.scrollTo(target.coerceIn(0, scroll.maxValue)) }
    } else {
        // 封面跨页和目录区之间的吸附：滚一下翻一页（只在停下来、且离其中一头够近时）
        LaunchedEffect(scroll) {
            snapshotFlow { scroll.isScrollInProgress to scroll.value }.collectLatest { (moving, v) ->
                if (moving) return@collectLatest
                val toc = anchors["toc"]?.let { it - navPx } ?: return@collectLatest
                val to = homeSnapTarget(v.toFloat(), toc) ?: return@collectLatest
                delay(140)
                scroll.animateScrollTo(to.toInt())
            }
        }
    }

    val spreadH = win.height.coerceAtLeast(Guga.rem(DeskHomeSpec.SPREAD_MIN))
    val sections = remember(data) { homeSections(data) }

    DesktopPage(bleed = issue != null, scroll = scroll, bottom = Guga.rem(12f), modifier = Modifier.testTag("desktop-home")) {
        if (issue != null) {
            // ---- 封面跨页 ----
            Box(Modifier.fillMaxWidth().height(spreadH).testTag("desktop-spread")) {
                // 背后的整幅图：这一期的剧照（没有就海报），虚化压暗；翻期交叉淡入。右半边再压一层，右页的字读得清
                FullBleed(issue.art, spreadH, Modifier.testTag("home-backdrop"), framing = Framing.of(issue.seriesId ?: issue.id), soften = true, dim = 0.5f) {
                    Box(Modifier.matchParentSize().drawBehind { drawRect(Brush.horizontalGradient(0.4f to Color.Transparent, 0.75f to colors.paper.copy(alpha = 0.7f), 1f to colors.paper.copy(alpha = 0.7f))) })
                }
                Row(Modifier.fillMaxSize().padding(top = Guga.rem(DeskHomeSpec.NAV), bottom = Guga.rem(DeskHomeSpec.SPREAD_BOTTOM)).padding(horizontal = dimens.margin)) {
                    // 左页：三维海报流
                    val coverByHeight = (spreadH - Guga.rem(DeskHomeSpec.NAV) - Guga.rem(16f)) * 0.6667f
                    Box(Modifier.weight(if (xl) DeskHomeSpec.LEFT_XL else DeskHomeSpec.LEFT).fillMaxHeight().clipToBounds()) {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val coverW = min(min(Guga.rem(DeskHomeSpec.COVER_MAX), coverByHeight), maxWidth * DeskHomeSpec.COVER_OF_COLUMN)
                            CoverFlow(
                                flow,
                                covers.map { it.art.poster },
                                Modifier.align(Alignment.Center).offset(y = -Guga.rem(1.6f)),
                                cardWidth = coverW,
                                names = covers.map { it.displayName },
                                onClick = { i -> covers.getOrNull(i)?.let { graph.open(it.seriesId ?: it.id) } },
                            )
                        }
                        // 期号指示 + 两端的箭头键
                        Row(Modifier.align(Alignment.BottomCenter).height(Guga.rem(2.4f)).testTag("cover-ticks"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f))) {
                            ArrowKey("←", "上一部") { flow.animateTo(flow.settled - 1) }
                            Spacer(Modifier.width(Guga.rem(0.2f)))
                            for (i in covers.indices) {
                                val on = i == flow.settled
                                val press = rememberPressState()
                                Box(Modifier.height(Guga.rem(1.6f)).pressable(press, label = "第 ${i + 1} 部", focusable = false) { flow.animateTo(i) }, contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(Guga.rem(if (on) 3.4f else 1.8f), Guga.rem(0.3f)).background(if (on) colors.spot else colors.faint))
                                }
                            }
                            Spacer(Modifier.width(Guga.rem(0.2f)))
                            ArrowKey("→", "下一部") { flow.animateTo(flow.settled + 1) }
                        }
                    }
                    Spacer(Modifier.width(dimens.gutter))
                    // 书脊
                    Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line).testTag("desktop-spine"))
                    Spacer(Modifier.width(dimens.gutter))
                    // 右页：期号 / 片名 / 导语 / 续看 / 印章位 / 主编手记
                    Column(Modifier.weight(if (xl) DeskHomeSpec.RIGHT_XL else DeskHomeSpec.RIGHT).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            IssueOdometer(flow.position, covers.size)
                            Spacer(Modifier.weight(1f))
                            Column(Modifier.padding(bottom = Guga.rem(1.4f)), horizontalAlignment = Alignment.End) {
                                BasicText("COVER STORY · 今日封面", style = Guga.note(rsp(1.1f), colors.faint, letterSpacing = 0.24.em), maxLines = 1)
                                BasicText(
                                    coverKicker(issue).joinToString(" · "),
                                    Modifier.padding(top = Guga.rem(0.6f)),
                                    style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        val logoPress = rememberPressState()
                        TitleLogo(
                            issue.displayName,
                            issue.art.logo,
                            width = Guga.rem(40f),
                            height = Guga.rem(11f),
                            modifier = Modifier.padding(top = Guga.rem(2.2f), bottom = Guga.rem(1.8f)).pressable(logoPress, label = issue.displayName, focusable = false) { graph.open(issue.seriesId ?: issue.id) },
                            textOnly = preset.textTitles,
                        )
                        BasicText(
                            issue.lead.orEmpty(),
                            Modifier.widthIn(max = Guga.rem(54f)).heightIn(min = Guga.rem(8.925f)),
                            style = Guga.song(rsp(1.7f), lineHeight = rsp(1.7f * 1.75f)),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // 续看 + 收藏 + 印章位 + 详情 +「余 18 分 · 已看 59%」
                        val favId = issue.seriesId ?: issue.id
                        val fav = chrome.isFavorite(favId, issue.favorite && issue.kind != Kind.Episode)
                        Row(Modifier.fillMaxWidth().padding(top = Guga.rem(2.4f), bottom = Guga.rem(2.2f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.8f))) {
                            DeskButton(coverAction(issue), { graph.play(issue.id, issue.positionSec?.times(1000)) }, Modifier.progressLine(issue.progress, colors.spot))
                            DeskLink("收藏", {
                                graph.toggleFavorite(chrome, favId, fav)
                                voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = issue.id.hashCode() + today.day))
                            }, active = fav)
                            SealSlot(fav, size = Guga.rem(3.6f))
                            DeskLink("详情", { graph.open(favId) })
                            Spacer(Modifier.weight(1f))
                            BasicText(Rules.progressLabel(issue.progress, issue.runtimeSec, phone = false), style = Guga.note(rsp(1.3f), colors.dim), maxLines = 1)
                        }
                        // 主编手记：她的一格（版面里的一栏，不遮挡）
                        if (LocalGugaPrefs.current.showMascot) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                            EditorNote(coverNote(issue, hour, today.day), Modifier.padding(vertical = Guga.rem(1.4f)), voice = voice, photo = Guga.rem(5.6f))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                        }
                    }
                    if (xl) {
                        // 第三栏「接着看」（只在很宽的窗口）
                        Spacer(Modifier.width(dimens.gutter))
                        Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
                        Spacer(Modifier.width(dimens.gutter))
                        Column(Modifier.weight(DeskHomeSpec.THIRD_XL).fillMaxHeight().testTag("desktop-spread-third"), verticalArrangement = Arrangement.Center) {
                            DeskSection(null, "接着看", "CONTINUE")
                            for ((i, t) in data.resume.filter { it.progress > 0f }.take(DeskHomeSpec.THIRD_ROWS).withIndex()) {
                                ResumeRow(i + 1, t)
                            }
                        }
                    }
                }
                // 页脚目录条 + 快捷键提示
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = dimens.margin)
                        .padding(bottom = Guga.rem(DeskHomeSpec.FOLD_BOTTOM))
                        .fillMaxWidth()
                        .height(Guga.rem(DeskHomeSpec.FOLD))
                        .drawBehind { drawLine(colors.ink, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
                        .testTag("desktop-fold"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Guga.rem(2.4f)),
                ) {
                    FoldTitle(tocHint.show) {
                        tocHint.seen()
                        sections.firstOrNull()?.let { jump(it.key) }
                    }
                    BasicText("LINEUP", style = Guga.numeral(rsp(1.1f), colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                    for (s in sections) FoldEntry(s.number, s.foldLabel) {
                        tocHint.seen()
                        jump(s.key)
                    }
                    Spacer(Modifier.weight(1f))
                    FoldHint("滚轮", "往下看")
                    FoldHint("← →", "翻封面")
                    FoldHint("Enter", "播放")
                }
            }
            Spacer(Modifier.height(Guga.rem(12f)))
        }

        // ---- 目录区 ----
        Column(Modifier.fillMaxWidth()) {
            var first = true
            for (s in sections) {
                val top = if (first) 0.dp else dimens.sectionGap
                first = false
                when (s) {
                    is HomeSection.Resume -> Column(Modifier.padding(top = top).padding(horizontal = dimens.margin).anchor(s.key)) {
                        DeskSection(s.number, "继续观看", "CONTINUE WATCHING") { SectionNote("${data.resumeTotal} 部在看", { graph.nav.navigate(Route.History()) }) }
                        ResumeBlock(data.resume, xl)
                    }
                    is HomeSection.Latest -> Column(Modifier.padding(top = top).anchor(s.key)) {
                        var picked by remember { mutableIntStateOf(0) }
                        val band = data.arrivals.getOrNull(picked) ?: data.arrivals.first()
                        DeskSection(s.number, "最近添加", "NEW ARRIVALS", Modifier.padding(horizontal = dimens.margin)) {
                            Row(Modifier.alignByBaseline(), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.8f))) {
                                for ((i, a) in data.arrivals.withIndex()) SegTab(a.library.name, i == picked) { picked = i }
                            }
                        }
                        PosterRow(band.titles, hoverIndex = if (state == "hover") 2 else -1, focusIndex = if (state == "focus") 2 else -1, key = band.library.id)
                    }
                    is HomeSection.Libraries -> Column(Modifier.padding(top = top).padding(horizontal = dimens.margin).anchor(s.key)) {
                        DeskSection(s.number, "媒体库", "SECTIONS") { SectionNote("${data.libraries.size} 个媒体库") }
                        LibraryCells(data, mini = win.width.value > 1280f)
                    }
                    is HomeSection.Shelf -> Column(Modifier.padding(top = top).anchor(s.key)) {
                        DeskSection(s.number, s.foldLabel, s.shelf.kind.english, Modifier.padding(horizontal = dimens.margin)) {
                            // 这一栏只来自一个库 = 能翻到那个库；来自好几个库就只写「最近添加」
                            val lib = s.shelf.library
                            if (lib != null) SectionNote(lib.titles?.let { "全部 $it 部 →" } ?: "全部 →", { graph.nav.navigate(Route.Library(lib.id)) }) else SectionNote("最近添加")
                        }
                        PosterRow(s.shelf.titles, key = s.key)
                    }
                }
            }
            // 末尾：她打盹
            Row(
                Modifier
                    .padding(top = Guga.rem(7f))
                    .padding(horizontal = dimens.margin)
                    .fillMaxWidth()
                    .drawBehind { drawLine(colors.ink, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }
                    .padding(top = Guga.rem(2.4f))
                    .testTag("desktop-home-end"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MascotPoseNamed("打盹", Guga.rem(9f))
                Spacer(Modifier.width(Guga.rem(1.6f)))
                Column(Modifier.weight(1f)) {
                    val total = data.libraries.sumOf { it.titles ?: 0 }
                    BasicText("片单到此。" + (if (total > 0) "共 $total 部，" else "") + "用你的片库自动排片。", style = Guga.song(rsp(1.3f), color = colors.dim, lineHeight = rsp(2.2f)))
                    if (LocalGugaPrefs.current.mascotSpeaks) {
                        MascotLines.pick("home.end", hour = hour, seed = today.day)?.let { BasicText(it, style = Guga.note(rsp(1.3f), colors.dim)) }
                    }
                }
                BasicText("咕咕嘎嘎 · VOL. ${today.volume + shell.reprints}\n${today.date}", style = Guga.note(rsp(1.2f), colors.faint).copy(textAlign = TextAlign.End, lineHeight = rsp(2.16f)))
            }
        }
    }
}

/** 目录区有哪几个区块（编号跟着实际有的走：没有继续观看就不占 01）。 */
private sealed interface HomeSection {
    val key: String
    val number: String
    val foldLabel: String

    data class Resume(override val number: String, val count: Int) : HomeSection {
        override val key = "toc"
        override val foldLabel = "继续观看 $count"
    }

    data class Latest(override val number: String) : HomeSection {
        override val key = "new"
        override val foldLabel = "最近添加"
    }

    data class Libraries(override val number: String, val count: Int) : HomeSection {
        override val key = "libs"
        override val foldLabel = "媒体库 $count"
    }

    data class Shelf(override val number: String, val shelf: HomeShelf) : HomeSection {
        override val key = "shelf-${shelf.key}"
        override val foldLabel = "${shelf.name}"
    }
}

private fun homeSections(data: HomeData): List<HomeSection> {
    val out = ArrayList<HomeSection>()
    fun no() = Rules.episodeNo(out.size + 1)
    if (data.resume.isNotEmpty()) out += HomeSection.Resume(no(), data.resumeTotal)
    if (data.arrivals.isNotEmpty()) out += HomeSection.Latest(no())
    if (data.libraries.isNotEmpty()) out += HomeSection.Libraries(no(), data.libraries.size)
    // 按类排的栏：电视剧 / 电影 / 动漫 / 综艺（空的不出现）
    for (sh in data.shelves) out += HomeSection.Shelf(no(), sh)
    return out
}

@Composable
private fun ArrowKey(text: String, label: String, onClick: () -> Unit) {
    val press = rememberPressState()
    Box(Modifier.pressGraphics(press).pressable(press, label = label, focusable = false, onClick = onClick)) { Kbd(text) }
}

@Composable
private fun FoldEntry(number: String, label: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Row(Modifier.hover(hover).deskFocusRing(press).pressable(press, label = label, onClick = onClick), horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.6f))) {
        // 栏目名用正文色（以前是灰字，初次用的人看不见这里有目录），编号用专色；悬停时加粗
        BasicText(number, Modifier.alignByBaseline(), style = Guga.numeral(rsp(1.35f), colors.spot), maxLines = 1)
        BasicText(label, Modifier.alignByBaseline(), style = Guga.song(rsp(1.45f), if (hover.over) FontWeight.Black else FontWeight.SemiBold, colors.ink), maxLines = 1)
    }
}

/**
 * 页脚目录条最左边的「目录 ↓」：点一下滚到第一栏。[first] = 第一次：反白的纸签，多写「往下翻」，箭头会动（screens/home/TocHint.kt）。
 */
@Composable
private fun FoldTitle(first: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val bob = tocBob(first)
    val ink = if (first) colors.onInk else colors.ink
    Row(
        Modifier
            .deskFocusRing(press)
            .pressable(press, label = "片单", onClick = onClick)
            .then(if (first) Modifier.background(colors.ink).padding(horizontal = Guga.rem(1.2f), vertical = Guga.rem(0.4f)) else Modifier)
            .testTag("fold-title"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.9f)),
    ) {
        BasicText("片单", style = Guga.song(rsp(2f), FontWeight.Black, ink, letterSpacing = 0.2.em), maxLines = 1)
        BasicText("↓", Modifier.offset(y = Guga.rem(0.4f) * bob), style = Guga.song(rsp(1.8f), FontWeight.Black, if (first) ink else colors.spot), maxLines = 1)
        if (first) BasicText("往下看", style = Guga.song(rsp(1.45f), FontWeight.Bold, ink), maxLines = 1)
    }
}

@Composable
private fun FoldHint(key: String, what: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f))) {
        Kbd(key)
        BasicText(what, style = Guga.song(rsp(1.3f), color = Guga.colors.dim), maxLines = 1)
    }
}

/** 区块标题右端的页签（「电视剧 / 电影 / 动漫」）：选中 = 米白 + 专色下划线。 */
@Composable
internal fun RowScope.SegTab(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    BasicText(
        text,
        Modifier
            .hover(hover)
            .deskFocusRing(press)
            .pressable(press, label = text, onClick = onClick)
            .drawBehind { if (on) drawLine(colors.spot, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(bottom = Guga.rem(0.3f))
            .testTag(if (on) "seg-on" else "seg-off"),
        style = Guga.song(rsp(1.25f), FontWeight.SemiBold, if (on || hover.over) colors.ink else colors.faint),
        maxLines = 1,
    )
}

/** 继续观看的一行（右键 = 右键菜单；点 = 打开这部片）。 */
@Composable
private fun ResumeRow(number: Int, t: Title, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    DeskTocRow(Rules.episodeNo(number), t.displayName, deskTocSub(t), t.art.thumb ?: t.art.backdrop, t.progress, { graph.open(t.seriesId ?: t.id) }, modifier, menu = t)
}

/**
 * 继续观看：头条（大图 + 大号编号 + 续看）+ 右边 5 行，再往下两栏对排（3 + 3）；很宽的窗口 = 头条 + 两列目录。
 */
@Composable
private fun ResumeBlock(resume: List<Title>, xl: Boolean) {
    val feat = featureOf(resume) ?: return
    val rest = resume.filter { it !== feat }
    val gap = Guga.dimens.gutter * 2
    if (xl) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            FeatureCard(feat, Modifier.weight(5f))
            Column(Modifier.weight(7f)) { TwoColumns(rest, 2, gap) }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            FeatureCard(feat, Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                for ((i, t) in rest.take(DeskHomeSpec.FEATURE_SIDE_ROWS).withIndex()) ResumeRow(i + 2, t)
            }
        }
        TwoColumns(rest.drop(DeskHomeSpec.FEATURE_SIDE_ROWS), DeskHomeSpec.FEATURE_SIDE_ROWS + 2, gap)
    }
}

@Composable
private fun TwoColumns(rows: List<Title>, firstNumber: Int, gap: Dp) {
    for ((r, pair) in rows.chunked(2).withIndex()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for ((c, t) in pair.withIndex()) ResumeRow(firstNumber + r * 2 + c, t, Modifier.weight(1f))
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** 继续观看的头条：16:9 大图（下沿进度线）+ 大号斜体「01」+ 片名 / 副行 + 续看。 */
@Composable
private fun FeatureCard(t: Title, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Column(modifier.hover(hover).onRightClick { DesktopContextMenu.open(t, it) }.testTag("desktop-feature")) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).deskFocusRing(press).pressGraphics(press).pressable(press, label = t.displayName) { graph.open(t.seriesId ?: t.id) }
                .border(1.dp, colors.line).progressLine(t.progress, colors.spot, thickness = Guga.rem(0.4f), outside = false),
        ) {
            CoverImage(t.art.thumb ?: t.art.backdrop, Modifier.fillMaxSize(), Framing(0.5f, 0.35f)) { HatchPlate(Rules.monogram(t.displayName, person = false), Modifier.fillMaxSize()) }
        }
        Row(Modifier.fillMaxWidth().padding(top = Guga.rem(1.6f)), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Guga.rem(2f))) {
            BasicText("01", style = Guga.numeral(rsp(6f)).copy(lineHeight = rsp(5.4f)), maxLines = 1)
            Column(Modifier.weight(1f)) {
                BasicText(t.displayName, style = Guga.song(rsp(2.4f), FontWeight.Black, if (hover.over) colors.spot else colors.ink, letterSpacing = 0.04.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(deskTocSub(t), Modifier.padding(top = Guga.rem(0.4f)), style = Guga.note(rsp(1.35f), colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DeskButton(if (t.progress > 0f) "续看" else "播放", { graph.play(t.id, t.positionSec?.times(1000)) }, small = true)
        }
    }
}

/**
 * 横向海报行：一屏排 [DeskSpec.rowCount] 张，第一张对齐页边，最后露出下一张的边提示可滑（Shift + 滚轮 / 触控板横滑）。出血到窗口两边。
 */
@Composable
internal fun PosterRow(titles: List<Title>, hoverIndex: Int = -1, focusIndex: Int = -1, key: Any? = null, numbered: Boolean = true) {
    val graph = LocalGraph.current
    val dimens = Guga.dimens
    val win = LocalWindowDp.current
    val n = DeskSpec.rowCount(win.width.value)
    val cardW = (win.width - dimens.margin * 2 - dimens.gutter * (n - 1)) / n
    val scroll = remember(key) { ScrollState(0) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = dimens.margin).padding(top = Guga.rem(0.4f), bottom = Guga.rem(1f)).testTag("desktop-poster-row"),
        horizontalArrangement = Arrangement.spacedBy(dimens.gutter),
    ) {
        for ((i, t) in titles.withIndex()) {
            DeskPosterCard(t, cardW, number = if (numbered) Rules.episodeNo(i + 1) else null, forceHover = i == hoverIndex, forceFocus = i == focusIndex) { graph.open(it.id) }
        }
    }
}

/** 媒体库的格子：英文小标 / 库名 / 斜体部数 / 四张小海报（窄窗口不排小海报）。合集库 → 合集页，其余 → 海报墙。 */
@Composable
private fun LibraryCells(data: HomeData, mini: Boolean) {
    val graph = LocalGraph.current
    val nav = LocalWideNav.current
    val colors = Guga.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind { drawLine(colors.ink, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) }.testTag("desktop-lib-cells")) {
        for ((i, lib) in data.libraries.withIndex()) {
            val press = rememberPressState()
            val hover = rememberHover()
            val posters = data.arrivals.firstOrNull { it.library.id == lib.id }?.titles.orEmpty().take(4)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .hover(hover)
                    .drawBehind {
                        if (hover.over) drawRect(colors.ink.copy(alpha = 0.05f))
                        val w = 1.dp.toPx()
                        if (i == 0) drawLine(colors.line, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
                        drawLine(colors.line, Offset(size.width - w / 2, 0f), Offset(size.width - w / 2, size.height), w)
                        drawLine(colors.line, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
                    }
                    .deskFocusRing(press)
                    .pressable(press, label = lib.name) { if (lib.kind == "boxsets") nav.tab = WideTab.Colls else graph.nav.navigate(Route.Library(lib.id)) }
                    .padding(start = Guga.rem(2f), end = Guga.rem(2f), top = Guga.rem(2f), bottom = Guga.rem(1.8f))
                    .testTag("desktop-lib-cell"),
            ) {
                BasicText(libraryEnglish(lib.kind), style = Guga.numeral(rsp(1.05f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                BasicText(lib.name, Modifier.padding(top = Guga.rem(0.8f), bottom = Guga.rem(1.4f)), style = Guga.song(rsp(2.6f), FontWeight.Black, if (hover.over) colors.spot else colors.ink, letterSpacing = 0.1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 部数没给就不写（不写 0）
                Row(verticalAlignment = Alignment.Bottom) {
                    lib.titles?.let {
                        BasicText(it.toString(), style = Guga.numeral(rsp(4f)).copy(lineHeight = rsp(4f)), maxLines = 1)
                        BasicText("部", Modifier.padding(start = Guga.rem(0.6f), bottom = Guga.rem(0.5f)), style = Guga.song(rsp(1.2f), color = colors.faint), maxLines = 1)
                    }
                }
                if (mini && posters.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = Guga.rem(1.6f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.6f))) {
                        for (p in posters) CoverImage(p.art.poster, Modifier.weight(1f).aspectRatio(2f / 3f), Framing(0.5f, 0.5f), alpha = 0.85f) { HatchPlate("", Modifier.fillMaxSize()) }
                        repeat(4 - posters.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** 首次加载：跨页的骨架（形状 = 要出现的版面）+ 她低头摆稿纸。[quiet]：150 ms 之内不出骨架。 */
@Composable
private fun DesktopHomeLoading(quiet: Boolean) {
    val colors = Guga.colors
    val dimens = Guga.dimens
    val win = LocalWindowDp.current
    val spreadH = win.height.coerceAtLeast(Guga.rem(DeskHomeSpec.SPREAD_MIN))
    Box(Modifier.fillMaxWidth().height(spreadH).testTag("desktop-home-loading")) {
        if (quiet) return@Box
        Row(Modifier.fillMaxSize().padding(top = Guga.rem(DeskHomeSpec.NAV), bottom = Guga.rem(DeskHomeSpec.SPREAD_BOTTOM)).padding(horizontal = dimens.margin)) {
            val coverW = min(Guga.rem(DeskHomeSpec.COVER_MAX), (spreadH - Guga.rem(DeskHomeSpec.NAV) - Guga.rem(16f)) * 0.6667f)
            Box(Modifier.weight(DeskHomeSpec.LEFT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Skeleton(Modifier.offset(y = -Guga.rem(1.6f)).size(coverW, coverW * 1.5f))
            }
            Spacer(Modifier.width(dimens.gutter))
            Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
            Spacer(Modifier.width(dimens.gutter))
            Column(Modifier.weight(DeskHomeSpec.RIGHT).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Skeleton(Modifier.size(Guga.rem(16f), Guga.rem(9f)))
                Skeleton(Modifier.padding(vertical = Guga.rem(2.4f)).fillMaxWidth(0.6f).height(Guga.rem(8f)))
                Skeleton(Modifier.fillMaxWidth(0.9f).height(Guga.rem(1.6f)))
                Skeleton(Modifier.padding(vertical = Guga.rem(1.2f)).fillMaxWidth(0.84f).height(Guga.rem(1.6f)))
                Skeleton(Modifier.fillMaxWidth(0.5f).height(Guga.rem(1.6f)))
                Spacer(Modifier.height(Guga.rem(3.2f)))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                Row(Modifier.padding(vertical = Guga.rem(1.4f)), verticalAlignment = Alignment.CenterVertically) {
                    MascotPoseNamed("摆稿纸", Guga.rem(9f))
                    Spacer(Modifier.width(Guga.rem(1.6f)))
                    Column {
                        BasicText("正在排片", style = Guga.song(rsp(2f), FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
                        if (LocalGugaPrefs.current.mascotSpeaks) {
                            MascotLines.pick("wall.loading", hour = LocalHour.current, seed = 0)?.let {
                                BasicText(it, Modifier.padding(top = Guga.rem(0.4f)), style = Guga.song(rsp(1.3f), color = colors.dim, lineHeight = rsp(2.2f)))
                            }
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            }
        }
        Row(
            Modifier.align(Alignment.BottomStart).padding(horizontal = dimens.margin).padding(bottom = Guga.rem(DeskHomeSpec.FOLD_BOTTOM)).fillMaxWidth().height(Guga.rem(DeskHomeSpec.FOLD))
                .drawBehind { drawLine(colors.ink, Offset(0f, 0.5.dp.toPx()), Offset(size.width, 0.5.dp.toPx()), 1.dp.toPx()) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Guga.rem(2.4f)),
        ) {
            BasicText("片单", style = Guga.song(rsp(1.6f), FontWeight.Black, letterSpacing = 0.2.em), maxLines = 1)
            BasicText("LINEUP", style = Guga.numeral(rsp(1.1f), colors.spot, letterSpacing = 0.2.em), maxLines = 1)
            Skeleton(Modifier.size(Guga.rem(38f), Guga.rem(1.2f)))
        }
    }
}
