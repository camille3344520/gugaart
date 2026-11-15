package com.gugaart.ui.tv

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverFlow
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.EndOfPage
import com.gugaart.ui.components.IssueOdometer
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.rememberCoverFlowState
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.LibraryEntry
import com.gugaart.ui.data.Load
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.home.HomeData
import com.gugaart.ui.screens.home.rememberTocHint
import com.gugaart.ui.screens.home.tocBob
import com.gugaart.ui.screens.home.coverAction
import com.gugaart.ui.screens.home.coverKicker
import com.gugaart.ui.screens.home.coverNote
import com.gugaart.ui.screens.home.loadHome
import com.gugaart.ui.screens.home.tocSubtitle
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.ImageType
import com.gugugaga.core.ImageUrlOptions
import com.gugugaga.ds.focus.RemoteKey
import com.gugugaga.ds.focus.remoteKeyOf
import com.gugugaga.ds.icons.GuguIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 电视首页的版式数（设计稿 `tv/css/home.css`，单位 = 1920 × 1080 的 px）。 */
object TvHomeSpec {
    /** 背后整幅图的高：封面状态 790；焦点进了目录（上半屏 = 焦点预览）560。 */
    val BLEED = 790.dp
    val BLEED_PREVIEW = 560.dp
    /** 目录区从哪开始：封面状态 792（只露第 01 栏的图，不露字）；焦点预览 478。 */
    val ROWS_TOP = 792.dp
    val ROWS_TOP_PREVIEW = 478.dp
    /** 海报流的舞台：x 816 起、y 112、1104 × 600；正中那张 368 × 552，中心 x = 1340。 */
    val STAGE_X = 816.dp
    val STAGE_Y = 112.dp
    val STAGE_W = 1104.dp
    val STAGE_H = 600.dp
    val COVER_W = 368.dp
    val FLOW_W = 1048.dp
    /** 左栏：期号 y 112、栏目行 278、logo 322（540 × 140）、导语 478（宽 590）、按钮 590、主编手记 690（620 × 82）。 */
    val ISSUE_Y = 96.dp
    val KICKER_Y = 278.dp
    val LOGO_Y = 322.dp
    val LOGO_W = 540.dp
    val LOGO_H = 140.dp
    val LEAD_Y = 478.dp
    val LEAD_W = 590.dp
    val CTA_Y = 590.dp
    val NOTE_Y = 690.dp
    val NOTE_W = 620.dp
    val NOTE_H = 82.dp
    /** 期号指示：海报流正下方 y 716。 */
    val TICKS_Y = 708.dp
    /** 焦点预览：栏目行 128、logo 172（520 × 126）、分级 · 出品 312、导语 350（宽 820）；没有剧照时右边贴一张海报 224 × 336。 */
    val PV_KICKER_Y = 128.dp
    val PV_LOGO_Y = 172.dp
    val PV_LOGO_W = 520.dp
    val PV_LOGO_H = 126.dp
    val PV_META_Y = 312.dp
    val PV_LEAD_Y = 350.dp
    val PV_LEAD_W = 820.dp
    /** 网络断开的号外条占的高度（封面区整体下移）。 */
    val OFFLINE_SHIFT = 96.dp
    const val SWITCH_MS = 350
    /** 付印新刊：她摇印刷机这么久。 */
    const val PRESS_MS = 1700L
}

/** 焦点进了目录之后，上半屏预览的那一条。 */
@Immutable
private data class TvPreview(
    val key: String,
    val name: String,
    val art: Art,
    val kicker: String,
    val meta: String,
    val lead: String,
    /** 没有剧照时右边贴的图：竖的海报 / 横的图（媒体库）。 */
    val sheetWide: Boolean = false,
)

/**
 * 电视首页（设计稿 `tv/index.html?p=home`）：横向封面——左栏期号 / logo / 导语 / 按钮 / 主编手记，右栏三维海报流，背后整幅剧照；
 * 焦点进目录后上半屏收成「焦点预览」。取数复用 `screens/home/HomeScreen.kt` 的 `loadHome`。
 */
@Composable
fun TvHomeScreen(today: Today) {
    val load: Load<HomeData> = rememberLoad("home") { loadHome(it) }
    com.gugaart.ui.screens.home.RefreshOnReturn(load)
    val data = load.value
    when {
        data != null -> TvHome(data, today, offline = load.error != null, onRefresh = { com.gugaart.ui.screens.home.CoverDeck.requestNew(); load.retry() })
        load.error != null -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = TvSpec.TOP)) { TvMasthead(today) }
            TvStateView("tv-home-error", "摊手", load.error!!, "稍等一下再试；一直不行就换一条线路。", action = "再试一次", onAction = load::retry)
        }
        else -> Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = TvSpec.TOP)) { TvMasthead(today) }
            if (load.showSkeleton) TvStateView("tv-home-loading", "摆稿纸", "正在排片", MascotLines.pick("wall.loading", hour = LocalHour.current, seed = today.day))
        }
    }
}

@Composable
private fun TvHome(data: HomeData, today: Today, offline: Boolean, onRefresh: () -> Unit) {
    val g = LocalGraph.current
    val nav = LocalWideNav.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val preset = LocalPreset.current
    val hour = LocalHour.current
    val scope = rememberCoroutineScope()
    val voice = rememberNoteVoice()
    val session by g.session.session.collectAsState()

    val covers = data.covers
    val start = preset.homeIssue.coerceIn(0, (covers.size - 1).coerceAtLeast(0))
    val flow = rememberCoverFlowState(covers.size, start)
    var want by remember { mutableIntStateOf(start) }
    val issue = covers.getOrNull(flow.settled)

    var inRows by remember { mutableStateOf(false) }
    var flowFocused by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<TvPreview?>(null) }
    val pv = preview.takeIf { inRows }
    // 付印新刊（报头 VOL. 上按确定）：她摇印刷机 → 期号 +1、封面回到第一期
    var pressing by remember { mutableStateOf(preset.state == "press") }
    var reprints by remember { mutableIntStateOf(0) }

    val firstRow = remember { TvRowState() }
    val flowFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val volFocus = remember { FocusRequester() }

    // 专色：封面状态跟正中那张海报；焦点预览跟焦点那张卡
    val spotArt = pv?.art ?: issue?.art
    val spotImage = rememberImage(spotArt?.poster ?: spotArt?.thumb ?: spotArt?.backdrop, keepPrevious = true)
    LaunchedEffect(spotImage) { chrome.spot = spotImage?.spot(tv = true) }
    DisposableEffect(Unit) { onDispose { chrome.spot = null } }

    // 返回：焦点在目录里 → 回到封面
    TvBack(inRows) { runCatching { flowFocus.requestFocus() } }

    val shift = if (offline && !inRows) TvHomeSpec.OFFLINE_SHIFT else 0.dp
    val bleedH by animateDpAsState(if (inRows) TvHomeSpec.BLEED_PREVIEW else TvHomeSpec.BLEED, tween(TvHomeSpec.SWITCH_MS), label = "tv-home-bleed")
    val rowsTop by animateDpAsState(if (inRows) TvHomeSpec.ROWS_TOP_PREVIEW else TvHomeSpec.ROWS_TOP + shift, tween(TvHomeSpec.SWITCH_MS), label = "tv-home-rows")
    val coverAlpha by animateFloatAsState(if (inRows) 0f else 1f, tween(250), label = "tv-home-cover")

    // 第一次的「目录在下面」：封面流下面那一行换成反白纸签、她说一句；焦点进过目录就算看过了
    val tocHint = rememberTocHint(com.gugugaga.ds.theme.UiMode.Tv, voice)
    LaunchedEffect(tocHint.show, inRows) { if (tocHint.show && inRows) tocHint.seen() }
    // 焦点停在封面流上、没人按键时封面自己翻；每 10–20 分钟换一波（期号跟着 +1，和「付印新刊」一样回到第一期）
    com.gugaart.ui.idle.CoverAutoFlip(flow, visible = { flowFocused && !inRows }, flip = {
        want = it
        flow.animateTo(it)
    })
    com.gugaart.ui.idle.CoverRotation {
        reprints++
        onRefresh()
        want = 0
        flow.animateTo(0)
    }
    Box(Modifier.fillMaxSize().testTag(if (inRows) "tv-home-preview" else "tv-home-cover")) {
        // ---- 背后的整幅图：这一期（或焦点那部片）的剧照；没有剧照 = 海报虚化 ----
        TvBleed(spotArt?.backdrop, spotArt?.poster ?: spotArt?.thumb, bleedH, grey = offline)

        // ---- 报头；VOL. 可聚焦 = 付印新刊 ----
        Box(Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = TvSpec.TOP)) {
            TvMasthead(today.copy(volume = today.volume + reprints))
            TvFocusable(
                onClick = {
                    if (!pressing) {
                        pressing = true
                        scope.launch {
                            delay(TvHomeSpec.PRESS_MS)
                            pressing = false
                            reprints++
                            onRefresh()
                            want = 0
                            flow.animateTo(0)
                            runCatching { flowFocus.requestFocus() }
                            voice.say(REPRINT_DONE)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.CenterStart).focusRequester(volFocus).focusProperties { down = flowFocus },
                kind = TvFocusKind.Row,
                key = "home-vol",
                label = "换一批片单",
                frame = false,
            ) { focused ->
                if (focused) {
                    Row(Modifier.offset(x = (-12).dp).background(colors.ink).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("VOL. ${today.volume + reprints}", style = Guga.numeral(Guga.type.kicker, colors.onInk, letterSpacing = 0.14.em), maxLines = 1)
                        BasicText("　换一批片单", style = Guga.song(23.sp, FontWeight.Bold, colors.onInk, letterSpacing = 0.1.em), maxLines = 1)
                    }
                } else {
                    // 没有焦点时不画东西（报头自己的 VOL. 就在下面），只占着这一格的位置
                    Box(Modifier.size(150.dp, 42.dp))
                }
            }
        }

        // ---- 网络断开：号外条（下面是上次印好的版面） ----
        if (offline && !inRows) {
            Row(
                Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 118.dp).fillMaxWidth().height(84.dp)
                    .drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.testTag("tv-home-offline"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BasicText("断线", Modifier.background(colors.seal).padding(start = 22.dp, end = 14.dp, top = 8.dp, bottom = 8.dp), style = Guga.song(30.sp, FontWeight.Black, colors.ink, letterSpacing = 0.3.em), maxLines = 1)
                BasicText("与服务器的连接断开了。下面是上次缓存的片单，海报可能不是最新。", Modifier.weight(1f), style = Guga.song(25.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvButton("立即重试", onRefresh, icon = GuguIcon.RefreshCw, key = "home-retry")
                TvButton("换服务器", { g.nav.navigate(Route.Servers) }, icon = GuguIcon.Server, key = "home-servers")
            }
        }

        // ---- 封面区（焦点进了目录就淡出；还占着位置、还接焦点：从第 01 栏按上回到「续看」） ----
        if (issue != null) {
            Box(Modifier.fillMaxSize().offset(y = shift).graphicsLayer { alpha = coverAlpha }.testTag("tv-cover")) {
                // 右栏：三维海报流。整个舞台是一个焦点：左右 = 翻期号（第一期再按左 = 进导航），上 = VOL.，下 = 续看
                TvFocusable(
                    onClick = { g.open(issue.seriesId ?: issue.id) },
                    modifier = Modifier
                        .offset(x = TvHomeSpec.STAGE_X, y = TvHomeSpec.STAGE_Y)
                        .size(TvHomeSpec.STAGE_W, TvHomeSpec.STAGE_H)
                        .focusRequester(flowFocus)
                        .focusProperties {
                            down = playFocus
                            up = volFocus
                        }
                        .onFocusChanged { flowFocused = it.isFocused }
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (remoteKeyOf(e.key)) {
                                RemoteKey.Left -> {
                                    if (want <= 0) nav.openRail(g.focus.lastFocusedPress) else {
                                        want--
                                        flow.animateTo(want)
                                    }
                                    true
                                }
                                RemoteKey.Right -> {
                                    if (want < covers.size - 1) {
                                        want++
                                        flow.animateTo(want)
                                    }
                                    true
                                }
                                else -> false
                            }
                        },
                    kind = TvFocusKind.Row,
                    key = "home-flow",
                    default = true,
                    label = "封面 · ${issue.displayName}",
                    onLongClick = { TvLongPressMenu.open(issue) },
                    frame = false,
                ) { focused ->
                    Box(Modifier.padding(top = 14.dp).width(TvHomeSpec.FLOW_W)) {
                        CoverFlow(flow, covers.map { it.art.poster }, cardWidth = TvHomeSpec.COVER_W, draggable = false, names = covers.map { it.displayName })
                        // 焦点卡（正中那张）：米白框 + 校对角
                        Box(
                            Modifier.align(Alignment.Center).size(TvHomeSpec.COVER_W, TvHomeSpec.COVER_W * 1.5f)
                                .focusFrame(focused, colors.ink).proofCorners(focused, colors.seal, 30.dp, 4.dp, 16.dp).tvHeldPicture(focused),
                        )
                    }
                }
                // 期号指示：‹ 一排短线 ›（当前一段专色；海报流有焦点时两个箭头变亮）
                Row(
                    Modifier.offset(x = TvHomeSpec.STAGE_X, y = TvHomeSpec.TICKS_Y).width(TvHomeSpec.FLOW_W).height(24.dp).testTag("tv-home-ticks"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val arrow = Guga.numeral(34.sp, if (flowFocused) colors.ink else colors.faint).copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Normal)
                    BasicText("‹", Modifier.padding(end = 14.dp).offset(y = (-4).dp), style = arrow, maxLines = 1)
                    for (i in covers.indices) Box(Modifier.size(if (i == flow.settled) 46.dp else 22.dp, 4.dp).background(if (i == flow.settled) colors.spot else colors.faint))
                    BasicText("›", Modifier.padding(start = 14.dp).offset(y = (-4).dp), style = arrow, maxLines = 1)
                }

                // 目录的入口提示（遥控器没有「点」）：封面流下面一行「▼ 按下键看目录」；第一次是反白的纸签、箭头会动
                if (data.resume.isNotEmpty() || data.shelves.isNotEmpty() || data.libraries.isNotEmpty()) {
                    Box(Modifier.offset(x = TvHomeSpec.STAGE_X, y = TvHomeSpec.TICKS_Y + 32.dp).width(TvHomeSpec.FLOW_W).testTag("tv-home-toc-hint"), contentAlignment = Alignment.Center) {
                        val first = tocHint.show
                        val ink = if (first) colors.onInk else colors.dim
                        Row(
                            (if (first) Modifier.background(colors.ink) else Modifier).padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            BasicText("▼", Modifier.offset(y = (5 * tocBob(first)).dp), style = Guga.song(Guga.type.fine, FontWeight.Black, if (first) ink else colors.spot), maxLines = 1)
                            BasicText("按下键看片单", style = Guga.song(Guga.type.fine, FontWeight.Bold, ink), maxLines = 1)
                        }
                    }
                }

                // 左栏：期号里程表 / 栏目行 / logo / 导语 / 按钮 / 主编手记
                IssueOdometer(flow.position, covers.size, Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.ISSUE_Y))
                BasicText(
                    (coverKicker(issue) + listOfNotNull(issue.rating?.let { "★ $it" })).joinToString(" · "),
                    Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.KICKER_Y).width(640.dp),
                    style = Guga.song(24.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.16.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TitleLogo(
                    issue.displayName, issue.art.logo, TvHomeSpec.LOGO_W, TvHomeSpec.LOGO_H,
                    Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.LOGO_Y), baseSize = 100.sp, textOnly = preset.textTitles,
                )
                issue.lead?.let {
                    BasicText(
                        it,
                        Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.LEAD_Y).width(TvHomeSpec.LEAD_W),
                        style = Guga.song(Guga.type.lead, FontWeight.SemiBold, lineHeight = 45.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // 续看（余 N 分 + 进度线）/ 详情 / 收藏（按钮里留着虚线印章格，章落在里面）
                val favId = issue.seriesId ?: issue.id
                val fav = chrome.isFavorite(favId, issue.favorite && issue.kind != Kind.Episode)
                Row(Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.CTA_Y).focusGroup().testTag("tv-home-cta"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val up = Modifier.focusProperties { up = flowFocus }
                    val pad = PaddingValues(horizontal = 24.dp)
                    TvButton(
                        coverAction(issue), { g.play(issue.id, issue.positionSec?.times(1000)) }, up.focusRequester(playFocus),
                        icon = GuguIcon.Play, trailing = if (issue.progress > 0f) Rules.remaining(issue.progress, issue.runtimeSec) else null,
                        key = "home-play", progress = issue.progress, padding = pad,
                    )
                    TvButton("详情", { g.open(issue.seriesId ?: issue.id) }, up, icon = GuguIcon.Info, key = "home-detail", padding = pad)
                    TvButton(
                        if (fav) "已收藏" else "收藏",
                        {
                            g.toggleFavorite(chrome, favId, fav)
                            voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = issue.id.hashCode() + today.day))
                        },
                        up, icon = GuguIcon.Heart, key = "home-fav", padding = pad,
                        slot = { SealSlot(fav, Modifier.padding(start = 4.dp), size = 42.dp) },
                    )
                }
                // 主编手记：她的小格（版面上的一栏，不遮挡）；付印新刊时这一格换成她摇印刷机
                Box(
                    Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.NOTE_Y).size(TvHomeSpec.NOTE_W, TvHomeSpec.NOTE_H).drawBehind {
                        drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                        drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (pressing) {
                        Row(Modifier.fillMaxWidth().testTag("tv-home-press"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            MascotPoseNamed("摇印刷机", 74.dp)
                            BasicText(REPRINTING, style = Guga.song(24.sp, color = colors.dim), maxLines = 1)
                            Box(Modifier.weight(1f).height(3.dp).background(colors.line)) { Box(Modifier.fillMaxWidth(0.64f).height(3.dp).background(colors.spot)) }
                        }
                    } else {
                        EditorNote(coverNote(issue, hour, today.day), voice = voice, photo = 68.dp)
                    }
                }
            }
        }

        // ---- 焦点预览（焦点在目录里）：焦点到哪张卡，上半屏换成那部片 ----
        val pvAlpha by animateFloatAsState(if (pv != null) 1f else 0f, tween(250), label = "tv-home-pv")
        val shown = pv ?: preview
        if (shown != null && pvAlpha > 0f) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = pvAlpha }.testTag("tv-home-pv")) {
                BasicText(shown.kicker, Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.PV_KICKER_Y).width(1100.dp), style = Guga.song(24.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.16.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TitleLogo(shown.name, shown.art.logo, TvHomeSpec.PV_LOGO_W, TvHomeSpec.PV_LOGO_H, Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.PV_LOGO_Y), baseSize = 92.sp, textOnly = preset.textTitles)
                if (shown.meta.isNotEmpty()) BasicText(shown.meta, Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.PV_META_Y), style = Guga.song(22.sp, color = colors.dim, letterSpacing = 0.1.em), maxLines = 1)
                if (shown.lead.isNotEmpty()) {
                    BasicText(shown.lead, Modifier.offset(x = TvSpec.LEFT, y = TvHomeSpec.PV_LEAD_Y).width(TvHomeSpec.PV_LEAD_W), style = Guga.song(28.sp, FontWeight.SemiBold, lineHeight = 42.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // 没有剧照：背后是虚化的海报，右边贴一张清晰的
                if (shown.art.backdrop == null && (shown.art.poster != null || shown.art.thumb != null)) {
                    val wide = shown.sheetWide
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(top = if (wide) 132.dp else 122.dp, end = 140.dp)
                            .graphicsLayer { rotationZ = if (wide) 1.2f else 2f }
                            .size(if (wide) 560.dp else 224.dp, if (wide) 315.dp else 336.dp)
                            .border(1.dp, colors.ink.copy(alpha = 0.4f))
                            .testTag("tv-home-sheet"),
                    ) { CoverImage(if (wide) shown.art.thumb else shown.art.poster ?: shown.art.thumb, Modifier.fillMaxSize(), Framing(0.5f, 0.3f)) }
                }
            }
        }

        // ---- 目录区：一栏一栏的横排；焦点所在的一栏吸到顶 ----
        TvSnap(TvKitSpec.SECTION_H + TvKitSpec.SECTION_TO_ROW) {
            Column(
                Modifier.padding(top = rowsTop).fillMaxSize().onFocusChanged { inRows = it.hasFocus }
                    .onKeyEvent { e ->
                        // 第 01 栏按上 = 回按钮行的「续看」（焦点预览时目录上移，按钮行不在它正上方，就近搜索会找到报头去）
                        if (e.type == KeyEventType.KeyDown && remoteKeyOf(e.key) == RemoteKey.Up && firstRow.hasFocus) runCatching { playFocus.requestFocus() }.getOrDefault(false) else false
                    }
                    .focusGroup().verticalScroll(rememberScrollState()).testTag("tv-home-rows"),
            ) {
                var no = 0
                var first = true
                fun rowState(s: TvRowState): TvRowState = if (first) firstRow.also { first = false } else s
                fun number() = (++no).let { if (it < 10) "0$it" else it.toString() }
                if (data.resume.isNotEmpty()) {
                    TvHomeSection(number(), "继续观看", "CONTINUE", "${data.resumeTotal} 部在看") {
                        TvRow(data.resume, state = rowState(remember { TvRowState() }), tag = "tv-row-resume") { _, t, _ ->
                            TvLandscapeCard(
                                image = t.art.thumb ?: t.art.backdrop,
                                title = t.displayName,
                                sub = tocSubtitle(t, phone = false) + (t.pct?.let { " · $it%" } ?: ""),
                                onClick = { g.play(t.id, t.positionSec?.times(1000)) },
                                key = "cw-${t.id}",
                                onLongClick = { TvLongPressMenu.open(t) },
                                onFocus = { preview = previewOfResume(t) },
                                number = t.episodeNo?.let { "E$it" },
                                progress = t.progress,
                                caption = inRows,
                                origin = TvKitSpec.LEAD_ORIGIN,
                            )
                        }
                    }
                }
                // 按类排的栏：电视剧 / 电影 / 动漫 / 综艺（空的不出现）
                for ((b, band) in data.shelves.withIndex()) {
                    TvHomeSection(number(), "${band.name}", band.kind.english, band.library?.titles?.let { "$it 部" } ?: "最近添加") {
                        TvRow(band.titles, state = rowState(remember { TvRowState() }), tag = "tv-row-new$b") { _, t, _ ->
                            TvPosterCard(
                                t, { g.open(t.id) },
                                key = "new$b-${t.id}",
                                onLongClick = { TvLongPressMenu.open(t) },
                                onFocus = { preview = previewOfTitle(t) },
                                origin = TvKitSpec.LEAD_ORIGIN,
                            )
                        }
                    }
                }
                if (data.libraries.isNotEmpty()) {
                    TvHomeSection(number(), "媒体库", "LIBRARIES", "${data.libraries.size} 个") {
                        TvRow(data.libraries, state = rowState(remember { TvRowState() }), tag = "tv-row-libs") { _, lib, _ ->
                            val image = session?.client?.imageUrl(lib.id, ImageType.Primary, ImageUrlOptions(maxWidth = 960))
                            TvLandscapeCard(
                                image = image,
                                title = lib.name,
                                sub = lib.titles?.let { "$it 部" },
                                onClick = { g.nav.navigate(Route.Library(lib.id)) },
                                key = "lib-${lib.id}",
                                onFocus = { preview = previewOfLibrary(lib, image) },
                                framing = Framing(0.5f, 0.3f),
                                origin = TvKitSpec.LEAD_ORIGIN,
                            )
                        }
                    }
                }
                // 末尾：她打盹（不是无限加载）
                Column(Modifier.fillMaxWidth().height(900.dp).padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 30.dp)) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(colors.ink))
                    EndOfPage(seed = today.day)
                }
            }
        }
    }
}

/** 目录的一栏：栏目标题 + 一排卡；栏与栏之间 44。 */
@Composable
private fun TvHomeSection(number: String, title: String, english: String, trailing: String?, row: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = TvKitSpec.SECTION_GAP)) {
        TvSectionHeader(title, english, number = number, trailing = trailing)
        Spacer(Modifier.height(TvKitSpec.SECTION_TO_ROW))
        row()
    }
}

/** 付印新刊时她说的两句（设计稿 `s-home.js` 的 pressbox / 付印之后；不带编的数字）。 */
private const val REPRINTING = "换片中……新片单马上到。"
private const val REPRINT_DONE = "新片单到了，封面换过一轮。"

/** 继续观看的一条 → 预览：「续看 · 第 1 季 第 5 集《…》 · 停在 26:30」。 */
private fun previewOfResume(t: Title): TvPreview {
    val where = when {
        t.kind == Kind.Episode -> "续看 · " + listOfNotNull(t.seasonNo?.let { "第 $it 季" }, t.episodeNo?.let { "第 $it 集" }).joinToString(" ") + if (t.name.isNotEmpty() && t.name != t.seriesName) "《${t.name}》" else ""
        else -> "续看 · 电影"
    }
    val at = if (t.progress > 0f && t.positionSec != null) " · 停在 ${Rules.clock(t.positionSec * 1000)}" else " · 还没开始"
    return TvPreview("cw-${t.id}", t.displayName, t.art, where + at, listOfNotNull(t.officialRating, t.studios.firstOrNull()).joinToString(" · "), cut(t.overview, 86))
}

/** 最近添加的一部 → 预览：栏目行 + 评分；简介截 86 字。 */
private fun previewOfTitle(t: Title): TvPreview = TvPreview(
    t.id,
    t.name,
    t.art,
    (t.kicker(if (t.kind == Kind.Movie) "电影" else "剧集", t.count?.takeIf { t.kind == Kind.Series }?.let { "全 $it 集" }) + listOfNotNull(t.rating?.let { "★ $it" })).joinToString(" · "),
    listOfNotNull(t.officialRating, t.studios.firstOrNull()).joinToString(" · "),
    cut(t.overview, 86),
)

/** 媒体库 → 预览：库名（宋体）+ 部数；右边贴库的横图。 */
private fun previewOfLibrary(lib: LibraryEntry, image: String?): TvPreview = TvPreview(
    "lib-${lib.id}",
    lib.name,
    Art(thumb = image),
    "媒体库 · LIBRARY",
    "",
    (lib.titles?.let { "$it 部。" } ?: "") + "按确定进入海报墙。",
    sheetWide = true,
)

private fun cut(text: String, max: Int): String = if (text.length > max) text.take(max - 1) + "…" else text

/**
 * 电视上的状态视图（加载 / 空 / 出错）：她的一格（150）+ 宋体标题 + 一句话 + 一个能接焦点的出路按钮。排在页面流里，不是浮层。
 */
@Composable
fun TvStateView(tag: String, pose: String, title: String, line: String?, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null, size: Dp = 150.dp, inset: Boolean = true) {
    val colors = Guga.colors
    Row(modifier.fillMaxWidth().padding(start = if (inset) TvSpec.LEFT else 0.dp, end = if (inset) TvSpec.RIGHT else 0.dp, top = 60.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        MascotPoseNamed(pose, size)
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Guga.song(Guga.type.section, FontWeight.Black), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (line != null) BasicText(line, Modifier.padding(top = 8.dp), style = Guga.song(28.sp, color = colors.dim, lineHeight = 42.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (action != null && onAction != null) TvButton(action, onAction, Modifier.padding(top = 24.dp), key = "$tag-action", default = true)
        }
    }
}
