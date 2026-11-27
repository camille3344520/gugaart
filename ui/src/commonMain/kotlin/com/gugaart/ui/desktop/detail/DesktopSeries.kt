package com.gugaart.ui.desktop.detail

import com.gugaart.ui.desktop.deskFocusRing
import com.gugaart.ui.components.OutlineTag
import com.gugaart.ui.desktop.DeskButton
import com.gugaart.ui.desktop.PaperDialog
import com.gugaart.ui.screens.detail.seasonMarkCopy
import com.gugaart.ui.screens.detail.wholeMarkCopy
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import com.gugaart.ui.Today
import com.gugaart.ui.components.Chip
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Season
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.data.getItemShared
import com.gugaart.ui.desktop.DesktopContextMenu
import com.gugaart.ui.desktop.DesktopPage
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.Overview
import com.gugaart.ui.screens.detail.SeriesHead
import com.gugaart.ui.screens.detail.dropCapLength
import com.gugaart.ui.screens.detail.initialSeason
import com.gugaart.ui.screens.detail.loadEpisodes
import com.gugaart.ui.screens.detail.loadSeriesHead
import com.gugaart.ui.screens.detail.mediaRows
import com.gugaart.ui.screens.detail.pagesOf
import com.gugaart.ui.screens.detail.resumeLabel
import com.gugaart.ui.screens.detail.seriesNote
import com.gugaart.ui.screens.detail.EPISODE_PAGE
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp

/**
 * 详情页的骨架：整幅铺满（[DesktopPage] 的 bleed），拿着键盘焦点（`V` `W` `[` `]` `Enter` 走 [DesktopItemActions]），
 * 可以一进来就滚到某个区块（[anchor]：区块名；用 `mark(名字)` 给区块做记号）。
 */
@Composable
internal fun DetailScaffold(tag: String, anchor: String?, content: @Composable ColumnScope.(mark: (String) -> Modifier) -> Unit) {
    val scroll = rememberScrollState(LocalPreset.current.scroll)
    val anchors = remember { mutableStateMapOf<String, Float>() }
    val offset = with(LocalDensity.current) { Guga.rem(DetailSpec.ANCHOR).toPx() }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val y = anchor?.let { anchors[it] }
    LaunchedEffect(anchor, y) { if (y != null) scroll.scrollTo((y - offset).toInt().coerceAtLeast(0)) }
    DisposableEffect(Unit) { onDispose { DesktopItemActions.clear() } }
    DesktopPage(
        Modifier.focusRequester(focus).focusable().onKeyEvent { e -> e.type == KeyEventType.KeyDown && DesktopItemActions.handle(e.key) }.testTag(tag),
        bleed = true,
        scroll = scroll,
        bottom = Guga.rem(12f),
    ) {
        content { name -> Modifier.onGloballyPositioned { anchors[name] = it.positionInParent().y } }
    }
}

/** 详情还没到 / 出错：普通页面骨架里的一句话（返回圆钮在玻璃排里，永远能退出去）。 */
@Composable
internal fun DetailMessage(content: @Composable () -> Unit) {
    DesktopPage { content() }
}

/** 剧集详情的入口。[selected]：从单集进来（`Route.Item(单集)`）→ 单集详情并入剧集页，在目录上方展开着。 */
@Composable
fun DesktopSeries(id: String, today: Today, selected: Title? = null) {
    val head = rememberLoad("series", id) { loadSeriesHead(it, id) }
    val h = head.value
    when {
        h != null -> SeriesBody(h, today, selected)
        head.error != null -> com.gugaart.ui.desktop.DesktopErrorPage(head.error!!, head::retry)
        else -> DetailMessage { if (head.showSkeleton) LoadingView() }
    }
}

/** 这部剧用哪些季排标尺 / 下拉：有季号的季；一个有季号的都没有（季名全是乱码）→ 原样全部。纯函数。 */
fun rulerSeasons(seasons: List<Season>): List<Season> = seasons.filter { it.number != null }.ifEmpty { seasons }

/** 标尺上哪些格子印季号：不超过 12 季全印；多了只印第 1 季、5 的倍数和当前季。纯函数。 */
fun rulerLabelled(s: Season, count: Int, current: Season?): Boolean = count <= 12 || s.number == 1 || (s.number ?: 1) % 5 == 0 || s.id == current?.id

/** 这一页应该有多少集（最后一页不满 50）。纯函数。 */
fun expectedOnPage(total: Int, page: Int): Int = (total - page * EPISODE_PAGE).coerceIn(0, EPISODE_PAGE)

@Composable
private fun SeriesBody(head: SeriesHead, today: Today, selected: Title?) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val colors = Guga.colors
    val hour = LocalHour.current
    val voice = rememberNoteVoice()
    val series = head.series
    // 剧照：页底那一栏，也给没有分集图的集顶上（episodeStillFallback）
    val seriesStills = com.gugaart.ui.screens.detail.rememberItemStills(series.id)
    val m = Guga.dimens.margin
    val gut = Guga.dimens.gutter
    val width = dWidth()
    val state = preset.state

    val poster = rememberImage(series.art.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }

    val seasons = remember(head) { rulerSeasons(head.seasons) }
    val multi = seasons.size > 1
    var season by remember(head) { mutableStateOf(initialSeason(head.copy(seasons = seasons), preset.season ?: selected?.seasonNo)) }
    var page by remember(head) { mutableIntStateOf(preset.episodePage) }
    var open by remember { mutableStateOf(state == "seasons") }
    // P0-3：整季标已看 / 未看先弹确认框（截图预置 `markseason` 直接打开）
    var markSeason by remember { mutableStateOf(state == "markseason") }
    // 「整部已看 / 整部未看」也先确认（照 P0-3）
    var markAll by remember { mutableStateOf(state == "markall") }
    var sel by remember(head) { mutableStateOf(selected?.id) }
    val slice = rememberLoad("episodes", series.id, season?.id, page) { loadEpisodes(it, series.id, season?.id, page) }
    val episodes = slice.value?.episodes
    val pages = pagesOf(slice.value)
    val fav = chrome.isFavorite(series)
    val allSeen = chrome.isPlayed(series.id, series.count != null && series.unplayed == 0)
    val seasonSeen = season?.let { chrome.isPlayed(it.id, it.unplayed == 0) } == true

    // 截图预置：`ep` 一开始展开正在看的那一集，`mark` 展开第 7 集
    LaunchedEffect(episodes, state) {
        if (sel == null && episodes != null && episodes.isNotEmpty()) {
            if (state == "ep") sel = (episodes.firstOrNull { it.id == head.upNext?.id } ?: episodes.first()).id
            if (state == "mark") sel = episodes.getOrNull(6)?.id
        }
    }
    val chosen = sel?.let { id -> episodes?.firstOrNull { it.id == id } ?: selected?.takeIf { it.id == id } ?: head.upNext?.takeIf { it.id == id } }
    val target = chosen ?: head.upNext

    fun pick(s: Season) {
        season = s
        page = 0
        sel = null
        open = false
    }
    fun step(d: Int) {
        val i = seasons.indexOfFirst { it.id == season?.id }
        seasons.getOrNull(i + d)?.takeIf { i >= 0 }?.let(::pick)
    }
    val playNow = { target?.let { graph.play(it.id, it.positionSec?.takeIf { _ -> it.progress > 0f }?.times(1000)) } ?: episodes?.firstOrNull { !it.unaired }?.let { graph.play(it.id) } ?: Unit }
    val toggleFav = {
        graph.toggleFavorite(chrome, series.id, fav)
        voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = series.id.hashCode() + today.day))
    }
    DesktopItemActions.favorite = toggleFav
    DesktopItemActions.played = { (chosen ?: head.upNext)?.let { e -> graph.togglePlayed(chrome, e.id, chrome.isPlayed(e.id, e.played && e.progress <= 0f)) } ?: graph.togglePlayed(chrome, series.id, allSeen) }
    DesktopItemActions.previousSeason = if (multi) ({ step(-1) }) else null
    DesktopItemActions.nextSeason = if (multi) ({ step(1) }) else null
    DesktopItemActions.play = { playNow() }

    val anchor = when (state) {
        "eps", "mark", "ep", "seasons", "op21" -> "eps"
        "cast" -> "cast"
        else -> if (selected != null) "eps" else null
    }
    Box(Modifier.fillMaxSize()) {
    DetailScaffold("desktop-series", anchor) { mark ->
        DHero(
            series,
            DetailSpec.SERIES_HERO,
            caption = "剧照 ·《${series.name}》" + if (seasons.size > 1) "共 ${seasons.size} 季" else "第 1 季",
            kicker = (series.kicker("剧集") + listOfNotNull("连载中".takeIf { series.status == "连载中" })).joinToString(" · "),
            ratingNote = ratingNote(series),
        )
        // ---- 正文 8 : 4（正文 / 资料）；3168 宽 7 : 3 : 3.4（多「接下来」）
        Row(Modifier.padding(horizontal = m).padding(top = Guga.rem(2.4f)).fillMaxWidth()) {
            Column(Modifier.weight(if (width == DWidth.Xl) 7f else 8f)) {
                val (label, time) = if (chosen != null) {
                    "播放 第 ${chosen.episodeNo ?: 0} 集 · ${chosen.name}" to (chosen.runtimeSec ?: series.runtimeSec)?.let { Rules.minutes(it) }
                } else resumeLabel(head.upNext)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(2f))) {
                    DButton(label, { playNow() }, Modifier.weight(1f, fill = false), trailing = time, progress = if (chosen != null) chosen.progress else head.upNext?.progress ?: 0f, tag = "d-cta")
                    DAction("收藏", toggleFav, tag = "d-fav")
                    SealSlot(fav, size = Guga.rem(3.6f))
                    DAction("整部已看", { markAll = true }, tag = "d-all-seen") { ProofTick(allSeen, size = Guga.rem(1.4f)) }
                    // Trakt 追更（连着 Trakt 才有）
                    com.gugaart.ui.components.rememberTraktFollow(series.id)?.let { f ->
                        DAction(if (f.following) "已追更" else "追更", f.toggle, tag = "d-follow") { Icon(if (f.following) GuguIcon.Check else GuguIcon.Plus, colors.ink, Guga.rem(1.6f)) }
                    }
                    var at by remember { mutableStateOf(Offset.Zero) }
                    DAction("更多", { DesktopContextMenu.open(series, at) }, Modifier.onGloballyPositioned { at = it.positionInWindow() }, tag = "d-more") { Icon(GuguIcon.Ellipsis, colors.ink, Guga.rem(1.8f)) }
                }
                DOverview(series.overview, Modifier.padding(top = Guga.rem(3.6f)))
                Column(Modifier.padding(top = Guga.rem(3.2f))) {
                    Rule()
                    EditorNote(seriesNote(head, hour, today.day), Modifier.padding(vertical = Guga.rem(1.4f)), label = "看点", english = "HIGHLIGHTS", voice = voice, photo = Guga.rem(5.2f))
                    Rule()
                }
            }
            Spacer(Modifier.width(gut * 2))
            DFacts(Modifier.weight(if (width == DWidth.Xl) 3f else 4f)) {
                DPeople("导演", series.people.filter { it.type == "Director" }.take(3)) { graph.open(it.id) }
                DPeople("主演", series.people.filter { it.type == "Actor" || it.type == null }.take(4)) { graph.open(it.id) }
                val total = series.count
                val seen = if (total != null && series.unplayed != null) (total - series.unplayed).coerceAtLeast(0) else null
                if (total != null) DFact("集数") {
                    Column {
                        BasicText(
                            listOfNotNull("${seasons.size} 季".takeIf { multi }, "${thousands(total)} 集", series.runtimeSec?.let { "每集约 ${Rules.minutes(it)}" }).joinToString(" · "),
                            style = Guga.song(rsp(1.4f), FontWeight.Bold, lineHeight = rsp(2.1f)),
                            maxLines = 2,
                        )
                        // 资料栏里的细线进度；0 集不画专色
                        val f = if (seen != null && total > 0) (seen.toFloat() / total).let { if (seen > 0) maxOf(it, 0.01f) else 0f } else 0f
                        Box(Modifier.padding(top = Guga.rem(0.8f)).fillMaxWidth().height(Guga.rem(0.3f)).background(colors.line)) {
                            if (f > 0f) Box(Modifier.fillMaxWidth(f).height(Guga.rem(0.3f)).background(colors.spot))
                        }
                    }
                }
                if (seen != null) DFact("进度", "已看 $seen 集" + (head.upNext?.takeIf { it.progress > 0f }?.episodeNo?.let { " · 停在第 $it 集" } ?: ""))
                DFact("首播", cnDate(series.premiere) ?: series.year?.toString())
                DFact("状态", series.status)
                DFact("出品", series.studios.take(2).joinToString(" / "))
                DFact("类型", series.genres.take(6).joinToString(" / "))
            }
            if (width == DWidth.Xl) {
                Spacer(Modifier.width(gut * 2))
                Column(Modifier.weight(3.4f)) {
                    DSec(null, "接下来", "UP NEXT")
                    val cur = head.upNext?.episodeNo
                    val next = episodes.orEmpty().filter { cur == null || (it.episodeNo ?: 0) > cur }.take(4)
                    for (e in next) {
                        DRow(Rules.episodeNo(e.episodeNo ?: 0), e.name, listOfNotNull((e.runtimeSec ?: series.runtimeSec)?.let { Rules.minutes(it) }, "已看".takeIf { e.played && e.progress <= 0f }).joinToString(" · "), { sel = e.id }, context = e)
                    }
                }
            }
        }

        // ---- 01 分集目录
        Column(mark("eps").padding(horizontal = m).padding(top = Guga.rem(DetailSpec.SECTION)).testTag("d-eps")) {
            val total = series.count
            val seen = if (total != null && series.unplayed != null) (total - series.unplayed).coerceAtLeast(0) else null
            DSec("01", "分集", "EPISODES", trailing = if (multi) listOfNotNull("${seasons.size} 季", total?.let { "共 ${thousands(it)} 集" }).joinToString(" · ") else listOfNotNull(total?.let { "共 $it 集" }, seen?.let { "已看 $it" }).joinToString(" · ").takeIf { it.isNotEmpty() })
            // 选季：下拉 + 标尺（每格宽 = 这一季的集数）+ [ ] 换季
            Row(Modifier.fillMaxWidth().padding(bottom = Guga.rem(1.6f)), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.6f))) {
                val name = season?.let { s -> if (s.number != null) "第 ${s.number} 季" else s.name } ?: "第 1 季"
                val count = slice.value?.total ?: season?.episodes
                DBox(if (multi) ({ open = !open }) else null, open = open, label = "选季", tag = "d-season-dd") {
                    BasicText(name, style = Guga.song(rsp(1.35f), FontWeight.SemiBold), maxLines = 1)
                    if (count != null) BasicText("$count 集", style = Guga.song(rsp(1.2f), color = colors.faint), maxLines = 1)
                    if (multi) Icon(GuguIcon.ChevronDown, colors.ink, Guga.rem(1.4f))
                }
                if (multi) {
                    SeasonRuler(seasons, season, head.upNext?.seasonNo, ::pick, Modifier.weight(1f))
                    DKbd("[ ] 换季", Modifier.padding(bottom = Guga.rem(0.8f)))
                } else Spacer(Modifier.weight(1f))
                DBox({ if (season != null) markSeason = true }, label = "本季全部标为已看", tag = "d-season-seen") {
                    ProofTick(seasonSeen, size = Guga.rem(1.4f))
                    BasicText("本季全部标为已看", style = Guga.song(rsp(1.35f), FontWeight.SemiBold), maxLines = 1)
                }
            }
            // 下拉的纸片：盖在目录上面（不是玻璃）；多于 8 季排两列
            if (open && multi) {
                Box(Modifier.zIndex(4f).fillMaxWidth().height(0.dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
                    val two = seasons.size > 8
                    DSlip(Modifier.width(Guga.rem(if (two) 44f else 23f)).testTag("d-season-list")) {
                        val row: @Composable (Season) -> Unit = { s -> DSlipRow(if (s.number != null) "第 ${s.number} 季" else s.name, s.episodes?.let { "$it 集" }, s.id == season?.id, { pick(s) }) }
                        if (two) {
                            val half = (seasons.size + 1) / 2
                            Row {
                                Column(Modifier.weight(1f)) { for (s in seasons.take(half)) row(s) }
                                Column(Modifier.weight(1f)) { for (s in seasons.drop(half)) row(s) }
                            }
                        } else for (s in seasons) row(s)
                    }
                }
            }
            // 单集详情：并入这一页，在目录上方展开
            if (chosen != null) EpisodePanel(chosen, series, season, head.upNext?.id == chosen.id, { sel = null }, Modifier.padding(bottom = Guga.rem(2.8f)))
            // 每 50 集一页（标签 = 绝对集号）
            if (pages.isNotEmpty()) {
                Row(Modifier.padding(bottom = Guga.rem(2f)).testTag("d-ranges"), horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f)), verticalAlignment = Alignment.CenterVertically) {
                    for (p in pages.take(30)) Chip(p.label, p.index == page, { page = p.index; sel = null })
                }
            }
            val expected = expectedOnPage(slice.value?.total ?: 0, page)
            when {
                episodes == null && slice.error != null -> ErrorView(slice.error!!, slice::retry)
                episodes == null -> DGrid(List(4) { it }, 4, gapY = Guga.rem(2.8f)) { Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
                episodes.isEmpty() && expected <= 0 -> BasicText("没有分集", Modifier.padding(vertical = Guga.rem(4f)), style = Guga.song(rsp(1.3f), color = colors.faint), maxLines = 1)
                else -> {
                    // 这一页一张分集图都没有 → 换成目录行（不拿整部剧的同一张图顶几十次）
                    val rows = episodes.none { it.art.still != null }
                    val avail = (LocalWindowDp.current.width - m * 2).value
                    val missing = (expected - episodes.size).coerceAtLeast(0)
                    if (rows) {
                        val cols = autoColumns(avail, Guga.rem(DetailSpec.EP_ROW_MIN).value, (gut * 2).value)
                        val cells: List<Title?> = episodes + List(minOf(missing, 9)) { null }
                        DGrid(cells, cols, Modifier.testTag("d-ep-rows"), gapX = gut * 2) { e ->
                            if (e == null) Skeleton(Modifier.padding(vertical = Guga.rem(1.2f)).fillMaxWidth().height(Guga.rem(3.8f)))
                            else EpisodeLine(e, series, e.id == (sel ?: head.upNext?.id), { sel = e.id })
                        }
                    } else {
                        val cols = when (width) {
                            DWidth.Sm -> 3
                            DWidth.Md -> autoColumns(avail, Guga.rem(DetailSpec.EP_MIN).value, gut.value)
                            DWidth.Xl -> 8
                        }
                        val cells: List<Title?> = episodes + List(minOf(missing, 4)) { null }
                        DGrid(cells, cols, Modifier.testTag("d-ep-grid"), gapY = Guga.rem(2.8f)) { e ->
                            if (e == null) Column {
                                Skeleton(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                Skeleton(Modifier.padding(top = Guga.rem(1.2f)).fillMaxWidth(0.7f).height(Guga.rem(1.4f)))
                            } else EpisodeCard(e, series, e.id == (sel ?: head.upNext?.id), { sel = e.id }, fallback = com.gugaart.ui.screens.detail.episodeStillFallback(e.episodeNo, seriesStills, series.art.backdrop))
                        }
                    }
                    if (missing > 0) BasicText("还有 $missing 集未加载", Modifier.padding(top = Guga.rem(2f)).testTag("d-ep-missing"), style = Guga.song(rsp(1.2f), color = colors.faint), maxLines = 1)
                }
            }
        }

        // ---- 02 演职人员
        if (series.people.isNotEmpty()) {
            Column(mark("cast").padding(top = Guga.rem(DetailSpec.SECTION))) {
                DSec("02", "演职人员", "CAST & CREW", Modifier.padding(horizontal = m), trailing = "${series.people.size} 位")
                DCastRow(series.people, { graph.open(it.id) })
            }
        }
        // ---- 剧照（演职人员之后；点开看大图，盖住整个窗口）
        val stills = seriesStills
        val stillsShown = com.gugaart.ui.screens.detail.showStills(stills)
        var sec = if (series.people.isNotEmpty()) 3 else 2
        if (stillsShown) {
            Column(mark("stills").padding(top = Guga.rem(DetailSpec.SECTION))) {
                DSec("0$sec", "剧照", "STILLS", Modifier.padding(horizontal = m), trailing = "${stills.size} 张")
                DStills(stills)
            }
            sec++
        }
        DSimilar(series, "0$sec")
        DEnd(listOfNotNull(series.name, series.year?.toString()).joinToString(" · "), Modifier.padding(horizontal = m))
    }
    season?.takeIf { markSeason }?.let { s ->
        MarkDialog("desktop-mark-season", seasonMarkCopy(s, toPlayed = !seasonSeen), { markSeason = false }) { graph.togglePlayed(chrome, s.id, seasonSeen) }
    }
    if (markAll) {
        MarkDialog("desktop-mark-all", wholeMarkCopy(series.name, toPlayed = !allSeen), { markAll = false }) { graph.togglePlayed(chrome, series.id, allSeen) }
    }
    }
}

/** 整季 / 整部标已看、未看之前的确认框（P0-3）：标题 + 后果一句 + 取消 / 标为已看（未看）。确认之后才写服务器（和 Trakt）。 */
@Composable
private fun MarkDialog(tag: String, copy: com.gugaart.ui.screens.detail.SeasonMarkCopy, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = Guga.colors
    PaperDialog(tag, onDismiss = onDismiss, width = 44f) {
        BasicText(copy.title, style = Guga.song(rsp(2.6f), FontWeight.Black, letterSpacing = 0.06.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
        copy.note?.let { BasicText(it, Modifier.padding(top = Guga.rem(1f)), style = Guga.song(rsp(1.35f), color = colors.dim), maxLines = 1) }
        Row(Modifier.fillMaxWidth().padding(top = Guga.rem(2.8f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f), Alignment.End)) {
            DeskButton("取消", onDismiss, icon = null, small = true, outline = true)
            DeskButton(
                copy.confirm,
                {
                    onDismiss()
                    onConfirm()
                },
                Modifier.testTag("$tag-ok"),
                icon = null,
                small = true,
            )
        }
    }
}

/** 「1,215」。 */
fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** 选季标尺：一格一季，**宽度 = 这一季的集数**；当前季专色加高，正在看的季上面一个朱红点；悬停加高。 */
@Composable
private fun SeasonRuler(seasons: List<Season>, current: Season?, watching: Int?, onPick: (Season) -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Row(modifier.height(Guga.rem(3.6f)).testTag("d-ruler"), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (s in seasons) {
            val on = s.id == current?.id
            val hover = rememberHover()
            val press = rememberPressState()
            Column(
                Modifier.weight((s.episodes ?: 1).coerceAtLeast(1).toFloat()).hover(hover).deskFocusRing(press).pressable(press, label = "第 ${s.number} 季 · ${s.episodes ?: 0} 集", onClick = { onPick(s) }).testTag(if (on) "d-ruler-on" else "d-ruler-cell"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (rulerLabelled(s, seasons.size, current)) {
                    BasicText(s.number?.toString() ?: "·", style = Guga.numeral(rsp(1.05f), if (on) colors.ink else colors.faint), maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
                }
                Box(
                    Modifier.padding(top = Guga.rem(0.3f)).fillMaxWidth().height(Guga.rem(if (on) 2f else if (hover.value) 1.8f else 1.2f))
                        .background(if (on) colors.spot else if (hover.value) colors.faint else colors.ink.copy(alpha = 0.2f)),
                ) {
                    if (s.number != null && s.number == watching && !on) Box(Modifier.align(Alignment.TopCenter).width(Guga.rem(0.4f)).height(Guga.rem(0.4f)).background(colors.seal))
                }
            }
        }
    }
}

private fun subOf(e: Title, series: Title, current: Boolean): String = listOfNotNull(
    (e.runtimeSec ?: series.runtimeSec)?.let { Rules.minutes(it) },
    cnDate(e.premiere)?.substringAfter("年 "),
    if (e.progress > 0f && e.positionSec != null) "看到 ${Rules.clock(e.positionSec * 1000)}" else null,
).joinToString(" · ")

/** 集号：斜体数字；看过 = 朱红删除线划掉；当前 = 专色。 */
@Composable
private fun EpisodeNo(text: String, em: Float, seen: Boolean, current: Boolean, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    BasicText(
        text,
        modifier.drawWithContent {
            drawContent()
            if (seen) drawLine(colors.seal, Offset(0f, size.height * 0.52f), Offset(size.width, size.height * 0.52f), 1.5.dp.toPx())
        },
        style = Guga.numeral(rsp(em), if (current) colors.spot else colors.faint),
        maxLines = 1,
        softWrap = false,
    )
}

/** 分集卡：16 : 9 分集图（看过的压暗、看到一半的带进度线）+ 集号 / 集名 / 时长 · 日期 + 校对格 + 两行简介。点 = 在目录上方展开单集详情。 */
@Composable
private fun EpisodeCard(e: Title, series: Title, current: Boolean, onClick: () -> Unit, fallback: String? = null) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val seen = chrome.isPlayed(e.id, e.played && e.progress <= 0f)
    val hover = rememberHover()
    val press = rememberPressState()
    val no = e.episodeNo ?: 0
    // 未播出（op1137b）：置灰、不响应点击（右键菜单也不出）、校对格换成「未播出」小签
    Column(Modifier.fillMaxWidth().alpha(if (e.unaired) com.gugaart.ui.theme.GugaColors.UNAVAILABLE_ALPHA else 1f).hover(hover).then(if (e.unaired) Modifier else Modifier.contextClick(e)).deskFocusRing(press).pressable(press, enabled = !e.unaired, label = "第 $no 集 ${e.name}", onClick = onClick).testTag(if (e.unaired) "d-ep-card-unaired" else if (current) "d-ep-card-cur" else "d-ep-card")) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).border(1.dp, colors.line)) {
            val zoom = if (hover.value) 1.04f else 1f
            CoverImage(e.art.still ?: fallback, Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; clip = false }, Framing(0.5f, 0.35f), alpha = if (seen) 0.6f else 1f) {
                HatchPlate("第 $no 集", Modifier.fillMaxSize(), caption = "NO STILL", textSize = rsp(2f))
            }
            if (e.progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(e.progress.coerceIn(0f, 1f)).height(Guga.rem(0.3f)).background(colors.spot))
        }
        Row(Modifier.padding(top = Guga.rem(1.1f)), verticalAlignment = Alignment.CenterVertically) {
            EpisodeNo(Rules.episodeNo(no), 2.6f, seen, current, Modifier.padding(end = Guga.rem(1.2f)))
            Column(Modifier.weight(1f)) {
                BasicText(e.name, style = Guga.song(rsp(1.55f), FontWeight.Bold, if (current || hover.value) colors.spot else colors.ink, lineHeight = rsp(2.2f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(subOf(e, series, current), style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (e.unaired) OutlineTag("未播出", rsp(1.1f), 1.dp, colors.dim) else TickButton(seen, "第 $no 集") { graph.togglePlayed(chrome, e.id, seen) }
        }
        if (e.overview.isNotEmpty()) BasicText(e.overview, Modifier.padding(top = Guga.rem(0.8f)), style = Guga.song(rsp(1.3f), color = colors.dim, lineHeight = rsp(2.15f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** 没有分集图的季：目录行（集号 | 集名 / 副行 | 校对格）。 */
@Composable
private fun EpisodeLine(e: Title, series: Title, current: Boolean, onClick: () -> Unit) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val seen = chrome.isPlayed(e.id, e.played && e.progress <= 0f)
    val hover = rememberHover()
    val press = rememberPressState()
    val no = e.episodeNo ?: 0
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().alpha(if (e.unaired) com.gugaart.ui.theme.GugaColors.UNAVAILABLE_ALPHA else 1f).hover(hover).then(if (e.unaired) Modifier else Modifier.contextClick(e)).deskFocusRing(press).pressable(press, enabled = !e.unaired, label = "第 $no 集 ${e.name}", onClick = onClick).padding(vertical = Guga.rem(1.2f)).testTag(if (e.unaired) "d-ep-line-unaired" else "d-ep-line"), verticalAlignment = Alignment.CenterVertically) {
            EpisodeNo(no.toString(), 2.2f, seen, current, Modifier.width(Guga.rem(5.6f)))
            Spacer(Modifier.width(Guga.rem(1.2f)))
            Column(Modifier.weight(1f)) {
                BasicText(e.name, style = Guga.song(rsp(1.5f), FontWeight.Bold, if (current || hover.value) colors.spot else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(subOf(e, series, current), style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (e.unaired) OutlineTag("未播出", rsp(1.1f), 1.dp, colors.dim) else TickButton(seen, "第 $no 集") { graph.togglePlayed(chrome, e.id, seen) }
        }
        Rule()
    }
}

/** 行尾 / 卡片上的校对格：点 = 标记已看（写服务器，走 `togglePlayed`）。 */
@Composable
internal fun TickButton(checked: Boolean, what: String, onClick: () -> Unit) {
    val press = rememberPressState()
    Box(Modifier.deskFocusRing(press).pressable(press, label = if (checked) "$what 改成未看" else "$what 标记已看", onClick = onClick).testTag("d-tick")) { ProofTick(checked, size = Guga.rem(1.4f)) }
}

/**
 * 单集详情（并入剧集页，在目录上方展开，上下各一条主线）：左 5 这一集的图（没有分集图 → 剧照），右 7 栏目行 / 集名 / 时长 · 首播 · 看到哪 /
 * 简介 / 播放这一集 · 从头看 · 标记已看 / 规格行（真实媒体流；没有就不出现）。右上角 × 收起。
 */
@Composable
private fun EpisodePanel(e: Title, series: Title, season: Season?, resuming: Boolean, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val seen = chrome.isPlayed(e.id, e.played && e.progress <= 0f)
    val no = e.episodeNo ?: 0
    // 规格：分集列表不带媒体流，展开时单取这一集（只读）
    val full = rememberLoad("episode-full", e.id) { c -> c.titleOf(c.getItemShared(e.id)) }
    val tech = mediaRows(full.value?.sources?.firstOrNull())
    Column(modifier.testTag("d-ep-panel")) {
        Rule(strong = true)
        Row(Modifier.fillMaxWidth().padding(vertical = Guga.rem(2.4f))) {
            Box(Modifier.weight(5f).aspectRatio(16f / 9f).border(1.dp, colors.line)) {
                CoverImage(e.art.still ?: series.art.backdrop, Modifier.fillMaxSize(), Framing(0.5f, 0.3f)) { HatchPlate("第 $no 集", Modifier.fillMaxSize(), caption = "NO STILL", textSize = rsp(2.4f)) }
                if (e.progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(e.progress.coerceIn(0f, 1f)).height(Guga.rem(0.3f)).background(colors.spot))
            }
            Spacer(Modifier.width(Guga.dimens.gutter * 1.5f))
            Column(Modifier.weight(7f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            listOfNotNull(season?.let { s -> if (s.number != null) "第 ${s.number} 季" else s.name }, "第 $no 集", "已看".takeIf { seen }).joinToString(" · "),
                            style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em),
                            maxLines = 1,
                        )
                        BasicText(e.name.ifEmpty { "第 $no 集" }, Modifier.padding(top = Guga.rem(0.8f), bottom = Guga.rem(0.6f)), style = Guga.song(rsp(3f), FontWeight.Black, lineHeight = rsp(3.9f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    val press = rememberPressState()
                    Box(Modifier.deskFocusRing(press).pressable(press, label = "收起单集详情", onClick = onClose).padding(Guga.rem(0.4f)).testTag("d-ep-close")) { Icon(GuguIcon.X, colors.dim, Guga.rem(2f)) }
                }
                BasicText(
                    listOfNotNull(
                        (e.runtimeSec ?: series.runtimeSec)?.let { Rules.minutes(it) },
                        cnDate(e.premiere)?.let { "$it 首播" },
                        if (e.progress > 0f && e.positionSec != null) "看到 ${Rules.clock(e.positionSec * 1000)}（${e.pct ?: 0}%）" else null,
                    ).joinToString(" · "),
                    style = Guga.note(rsp(1.3f), colors.faint),
                    maxLines = 1,
                )
                BasicText(e.overview.ifEmpty { "没有简介" }, Modifier.padding(top = Guga.rem(1.4f), bottom = Guga.rem(2f)), style = Guga.song(rsp(1.5f), color = if (e.overview.isEmpty()) colors.faint else colors.dim, lineHeight = rsp(2.8f)), maxLines = 5, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(2f))) {
                    DButton(if (e.progress > 0f) "续看" else "播放这一集", { graph.play(e.id, e.positionSec?.takeIf { e.progress > 0f }?.times(1000)) }, small = true, tag = "d-ep-play")
                    if (e.progress > 0f || resuming) DButton("从头播放", { graph.play(e.id, 0L) }, small = true, line = true, icon = GuguIcon.RotateCcw, tag = "d-ep-restart")
                    DAction(if (seen) "改成未看" else "标记已看", { graph.togglePlayed(chrome, e.id, seen) }, tag = "d-ep-seen") { ProofTick(seen, size = Guga.rem(1.4f)) }
                }
                if (tech.isNotEmpty()) {
                    Rule(Modifier.padding(top = Guga.rem(1.6f)))
                    BasicText(tech.joinToString("　") { "${it.label} ${it.text}" }, Modifier.padding(top = Guga.rem(1.2f)).testTag("d-ep-tech"), style = Guga.song(rsp(1.2f), color = colors.faint), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Rule(strong = true)
    }
}

/**
 * 简介（电脑）：首字下沉（专色宋体大字）+ 正文；超过 330 字折成 6 行 +「展开全文」；不足 60 字放大一号；没有简介写明。版心不超过 86 rem。
 * 折叠、首字的排法复用手机的 [Overview]（同一套规则），只换门槛。
 */
@Composable
internal fun DOverview(text: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val box = modifier.widthIn(max = Guga.rem(86f))
    when {
        text.isEmpty() -> BasicText("没有简介", box.testTag("d-overview-none"), style = Guga.song(rsp(2.2f), color = colors.faint, lineHeight = rsp(3.96f)), maxLines = 2)
        text.length < DetailSpec.SHORT -> {
            val cap = dropCapLength(text)
            Row(box.testTag("d-overview-short"), verticalAlignment = Alignment.CenterVertically) {
                BasicText(text.substring(0, cap), Modifier.padding(end = Guga.rem(1.4f)), style = Guga.song(rsp(7.4f), FontWeight.Black, colors.spot, lineHeight = rsp(7.4f)), maxLines = 1)
                BasicText(text.substring(cap), Modifier.weight(1f), style = Guga.song(rsp(2.2f), lineHeight = rsp(3.96f)), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        }
        else -> Overview(Rules.fold(text, DetailSpec.FOLD), box)
    }
}

/** 相似（上游 `getSimilar`，只读）。服务器没有 / 取不到 → 整个区块不出现。 */
@Composable
internal fun DSimilar(of: Title, number: String) {
    val graph = LocalGraph.current
    val similar = rememberLoad("similar", of.id) { c ->
        try {
            c.getSimilar(of.id, limit = 14).items.map { c.titleOf(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    val list = similar.value.orEmpty()
    if (list.isEmpty()) return
    val m = Guga.dimens.margin
    val w = LocalWindowDp.current.width
    val n = when (dWidth()) {
        DWidth.Sm -> 6
        DWidth.Md -> 7
        DWidth.Xl -> 11
    }
    val card = (w - m * 2 - Guga.dimens.gutter * (n - 1)) / n
    Column(Modifier.padding(top = Guga.rem(DetailSpec.SECTION)).testTag("d-similar")) {
        DSec(number, "相似", "MORE LIKE THIS", Modifier.padding(horizontal = m))
        Row(Modifier.fillMaxWidth().then(Modifier).padding(horizontal = m), horizontalArrangement = Arrangement.spacedBy(Guga.dimens.gutter)) {
            for (t in list.take(n)) com.gugaart.ui.components.PosterCard(t, card, { graph.open(t.id) }, Modifier.contextClick(t))
        }
    }
}
