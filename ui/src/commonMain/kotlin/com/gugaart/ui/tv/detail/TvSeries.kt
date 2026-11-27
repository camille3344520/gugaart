package com.gugaart.ui.tv.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Season
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.SeriesHead
import com.gugaart.ui.screens.detail.initialSeason
import com.gugaart.ui.screens.detail.seasonMarkCopy
import com.gugaart.ui.screens.detail.loadEpisodes
import com.gugaart.ui.screens.detail.loadSeriesHead
import com.gugaart.ui.screens.detail.pagesOf
import com.gugaart.ui.screens.detail.rememberItemStills
import com.gugaart.ui.screens.detail.seriesNote
import com.gugaart.ui.screens.detail.showStills
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvLongPressMenu
import com.gugaart.ui.tv.TvPage
import com.gugaart.ui.tv.TvSpec
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon
import kotlinx.coroutines.CancellationException

/** 相似（上游 `getSimilar`，只读）。取不到（老服务器没有这个接口）= 空，这一栏不出现。 */
suspend fun loadSimilar(client: EmbyClient, id: String): List<Title> = try {
    client.getSimilar(id, limit = 12).items.map { client.titleOf(it) }.filter { it.id != id }
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    emptyList()
}

/** 「2024-06-21T…」→「2024 年 6 月 21 日」；不是日期 = null。纯函数，有单测。 */
fun tvDay(iso: String?): String? {
    val s = iso?.take(10) ?: return null
    val p = s.split('-')
    if (p.size != 3) return null
    val y = p[0].toIntOrNull() ?: return null
    val m = p[1].toIntOrNull() ?: return null
    val d = p[2].toIntOrNull() ?: return null
    return "$y 年 $m 月 $d 日"
}

/** 分集卡的副行：「45 分 · 看到 59% · 上次看到这里」「43 分 · 已看」。纯函数，有单测。 */
fun tvEpisodeSub(e: Title, played: Boolean, current: Boolean): String =
    listOfNotNull(
        e.runtimeSec?.let { Rules.minutesOnly(it) },
        e.pct?.let { "看到 $it%" } ?: "已看".takeIf { played },
        "上次看到这里".takeIf { current },
    ).joinToString(" · ")

/** 分集下面那一栏的小标：「第 5 集 · 45 分钟 · 2024 年 6 月 21 日」；缺的那一段不写。纯函数，有单测。 */
fun tvEpisodeKick(e: Title): String =
    listOfNotNull(e.episodeNo?.let { "第 $it 集" }, e.runtimeSec?.let { "${((it + 30) / 60).coerceAtLeast(1)} 分钟" }, tvDay(e.premiere)).joinToString(" · ")

/** 这一屏上的组名。 */
private object G {
    const val CTA = "cta"
    const val SEASONS = "seasons"
    const val PAGES = "pages"
    const val EPS = "eps"
    const val CAST = "cast"
    const val SIMILAR = "similar"
    const val MORE = "more"
}

/**
 * 剧集详情的方向键路径（`docs/platforms.md` §3）：[from] 组按 [dir] 到头以后去哪个组。纯函数，有单测。
 * [has]：这个组现在有没有成员（只有一页分集就没有「分页」这一行）。
 */
fun seriesEdge(from: String, dir: TvDir, has: (String) -> Boolean): String? {
    val order = listOf("cta", "seasons", "pages", "eps", "cast", "similar", "stills")
    return when (dir) {
        TvDir.Left -> null
        TvDir.Right -> TvGroups.STAY
        // 第一屏按下 = 直接到分集（季在分集上面，要换季再按上）
        TvDir.Down -> if (from == "cta") listOf("eps", "seasons", "cast", "similar", "stills").firstOrNull(has) ?: TvGroups.STAY
        else order.drop(order.indexOf(from) + 1).firstOrNull(has) ?: TvGroups.STAY
        TvDir.Up -> order.take(order.indexOf(from).coerceAtLeast(0)).lastOrNull(has) ?: TvGroups.STAY
    }
}

/** 组 → 第几屏。 */
fun seriesScreenOf(group: String?): Int = when (group) {
    "seasons", "pages", "eps" -> 1
    "cast", "similar" -> 2
    "stills" -> 3
    else -> 0
}

@Composable
fun TvSeriesScreen(id: String, today: Today, preview: Title? = null) {
    val head = rememberLoad("tv-series", id) { loadSeriesHead(it, id) }
    val h = head.value ?: preview?.let { SeriesHead(series = it, seasons = emptyList(), upNext = null) }
    when {
        h != null -> TvSeries(h, today)
        head.error != null -> TvPage("", "FEATURE", today) { ErrorView(head.error!!, head::retry) }
        else -> TvPage("", "FEATURE", today) { if (head.showSkeleton) Skeleton(Modifier.padding(top = 60.dp).width(620.dp).height(120.dp)) }
    }
}

/**
 * 剧集详情（电视，设计稿 `p=series`）：**一屏一件事，上下键整屏翻**。
 * 第一屏整幅剧照 + 片名 + 导语第一句 + 按钮行；第二屏 季 → 分页 → 分集 + 这一集的简介 + 主编按；第三屏 演职人员 → 相似；
 * 第四屏 剧照（≥ 2 张才有，[TvStillsScreen]）。返回 = 回上一屏。
 */
@Composable
fun TvSeries(head: SeriesHead, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val colors = Guga.colors
    val hour = LocalHour.current
    val series = head.series
    val voice = rememberNoteVoice()
    val groups = remember { TvGroups() }
    val back = LocalBackDispatcher.current

    val poster = rememberImage(series.art.poster, keepPrevious = true)
    val tvSpot = com.gugaart.ui.components.rememberSpotColor(poster, tv = true)
    LaunchedEffect(tvSpot) { chrome.spot = tvSpot }
    LaunchedEffect(series.id) { com.gugaart.ui.tv.TvPerfTiming.onDetailContent(series.id) }
    var season by remember(head) { mutableStateOf(initialSeason(head, preset.season)) }
    var page by remember(head) { mutableIntStateOf(preset.episodePage) }
    var more by remember { mutableStateOf(preset.state == "more") }
    // P0-3：整季标已看 / 未看先在按钮行原地换成确认（标题 + 「标为已看」「取消」）；截图预置 `markseason` 直接打开
    var markSeason by remember { mutableStateOf(preset.state == "markseason") }
    val slice = rememberLoad("tv-episodes", series.id, season?.id, page) { loadEpisodes(it, series.id, season?.id, page) }
    val pages = pagesOf(slice.value)
    val similar = rememberLoad("tv-similar", series.id) { loadSimilar(it, series.id) }
    val stills = rememberItemStills(series.id)
    val hasStills = showStills(stills)
    val fav = chrome.isFavorite(series)
    val up = head.upNext
    val episodes = slice.value?.episodes.orEmpty()

    // 焦点到哪季，分集就换成哪季（不用按确定）；分页同理
    val cur = groups.current
    LaunchedEffect(cur) {
        if (cur == null) return@LaunchedEffect
        if (cur.startsWith("season-")) {
            head.seasons.firstOrNull { "season-${it.id}" == cur }?.let {
                if (it.id != season?.id) {
                    season = it
                    page = 0
                    groups.forget(G.EPS)
                    groups.forget(G.PAGES)
                }
            }
        } else if (cur.startsWith("page-")) {
            cur.removePrefix("page-").toIntOrNull()?.let {
                if (it != page) {
                    page = it
                    groups.forget(G.EPS)
                }
            }
        }
    }

    val screen = if (more) 0 else seriesScreenOf(groups.currentGroup)
    groups.edge = { from, dir -> if (from == G.MORE) TvGroups.STAY else seriesEdge(from, dir, groups::has) }

    // 返回 = 收起最上面一层：简介全文 → 回上一屏（第三屏 → 第二屏 → 第一屏）→ 上一页（外壳）
    DisposableEffect(more, screen, markSeason) {
        val un = when {
            markSeason -> back.push {
                markSeason = false
                true
            }
            more -> back.push {
                more = false
                true
            }
            screen == 3 -> back.push { groups.enter(listOf(G.SIMILAR, G.CAST, G.EPS).firstOrNull(groups::has) ?: G.CTA, graph.focus) }
            screen == 2 -> back.push { groups.enter(if (groups.has(G.EPS)) G.EPS else G.CTA, graph.focus) }
            screen == 1 -> back.push { groups.enter(G.CTA, graph.focus) }
            else -> null
        }
        onDispose { un?.invoke() }
    }
    // 打开 / 收起整季确认时焦点跟过去：打开 → 落在「取消」；收起 → 回到「整季已看」
    var markSeen by remember { mutableStateOf(false) }
    LaunchedEffect(markSeason) {
        if (markSeason) {
            markSeen = true
            groups.forget(G.CTA)
            groups.enterWhenReady(G.CTA, graph.focus)
        } else if (markSeen) {
            repeat(40) {
                if (graph.focus.request("cta-played")) return@LaunchedEffect
                kotlinx.coroutines.delay(25)
            }
        }
    }
    // 打开 / 合上简介全文时焦点跟过去
    var moreSeen by remember { mutableStateOf(false) }
    LaunchedEffect(more) {
        if (more) {
            moreSeen = true
            groups.enterWhenReady(G.MORE, graph.focus)
        } else if (moreSeen) {
            groups.enterWhenReady(G.CTA, graph.focus)
        }
    }

    val play: () -> Unit = {
        val t = up ?: episodes.firstOrNull { !it.unaired }
        if (t != null) graph.play(t.id, t.positionSec?.times(1000))
    }
    val primaryText = when {
        up == null -> "从第 1 集看起"
        up.progress > 0f -> "续看" + (up.episodeNo?.let { " 第 $it 集" } ?: "")
        else -> "播放" + (up.episodeNo?.let { " 第 $it 集" } ?: "")
    }
    val primarySub = up?.let { if (it.progress > 0f) Rules.remaining(it.progress, it.runtimeSec) else it.runtimeSec?.let(Rules::minutesOnly) }

    TvGroupsRoot(groups, Modifier.testTag("tv-series")) {
        TvBackdrop(series.art, deep = screen > 0 || more)
        if (more) {
            TvSynopsisPage(series, today, primaryText, { more = false }, play)
        } else TvScreens(screen, if (hasStills) 4 else 3) { index ->
            when (index) {
                0 -> Box(Modifier.fillMaxSize()) {
                    TvRunHead("FEATURE", if (series.art.backdrop != null) "剧照｜${series.name}" else "海报｜${series.name}", today)
                    Column(Modifier.align(Alignment.BottomStart).padding(start = TvSpec.LEFT, bottom = TvDetailSpec.HERO_BOTTOM).width(TvDetailSpec.HERO_WIDTH)) {
                        TvKick(tvKickOf(series, "剧集"))
                        TvTitle(series, Modifier.padding(top = 14.dp, bottom = 16.dp))
                        TvMeta(
                            series.rating,
                            listOfNotNull(
                                listOfNotNull(series.count?.let { "全 $it 集" }, series.runtimeSec?.let { "每集约 ${Rules.minutesOnly(it)}" }).joinToString(" · ").takeIf { it.isNotEmpty() },
                                series.officialRating,
                                series.studios.firstOrNull(),
                            ),
                        )
                        val lead = tvFirstSentence(series.overview)
                        if (lead.isNotEmpty()) {
                            BasicText(lead, Modifier.padding(top = 18.dp).width(980.dp), style = Guga.song(28.sp, FontWeight.SemiBold, lineHeight = 43.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        // Trakt 追更（连着 Trakt 才有）
                        val follow = com.gugaart.ui.components.rememberTraktFollow(series.id)
                        val seasonPlayed = season?.let { chrome.isPlayed(it.id, it.unplayed == 0) } == true
                        val confirming = season?.takeIf { markSeason }
                        if (confirming != null) {
                            val copy = seasonMarkCopy(confirming, toPlayed = !seasonPlayed)
                            TvGroup(G.CTA, listOf("cta-mark-ok", "cta-mark-cancel"), default = "cta-mark-cancel")
                            BasicText(copy.title, Modifier.padding(top = 26.dp).testTag("tv-mark-season"), style = Guga.song(34.sp, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            copy.note?.let { BasicText(it, Modifier.padding(top = 8.dp), style = Guga.song(25.sp, color = colors.dim), maxLines = 1) }
                            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                TvActionButton("cta-mark-ok", copy.confirm, {
                                    markSeason = false
                                    graph.togglePlayed(chrome, confirming.id, seasonPlayed)
                                }, icon = GuguIcon.Check)
                                TvActionButton("cta-mark-cancel", "取消", { markSeason = false }, icon = GuguIcon.X)
                            }
                        } else {
                        val keys = listOfNotNull("cta-play", "cta-restart".takeIf { up != null }, "cta-fav", "cta-played", "cta-follow".takeIf { follow != null }, "cta-more".takeIf { series.overview.isNotEmpty() })
                        TvGroup(G.CTA, keys, default = "cta-play")
                        Row(Modifier.padding(top = 26.dp).wrapContentWidth(Alignment.Start, unbounded = true), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            TvActionButton("cta-play", primaryText, play, icon = GuguIcon.Play, primary = true, sub = primarySub, progress = up?.progress ?: 0f, default = true)
                            if (up != null) TvActionButton("cta-restart", "从第 1 集看", { (episodes.firstOrNull { !it.unaired } ?: up).let { graph.play(it.id, 0) } }, icon = GuguIcon.RotateCcw)
                            TvActionButton("cta-fav", "收藏", {
                                graph.toggleFavorite(chrome, series.id, fav)
                                voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = series.id.hashCode() + today.day))
                            }, icon = GuguIcon.Heart) { TvSealSlot(fav, it) }
                            TvActionButton("cta-played", if (seasonPlayed) "整季已看 ✓" else "整季已看", { if (season != null) markSeason = true }, icon = GuguIcon.Check)
                            if (follow != null) TvActionButton("cta-follow", if (follow.following) "已追更 ✓" else "追更", follow.toggle, icon = if (follow.following) GuguIcon.Check else GuguIcon.Plus)
                            if (series.overview.isNotEmpty()) TvActionButton("cta-more", "简介全文", { more = true }, icon = GuguIcon.List)
                        }
                        }
                    }
                    TvDownHint(listOfNotNull(series.count?.let { "分集 $it" } ?: "分集", "演职人员 ${series.people.size}".takeIf { series.people.isNotEmpty() }, "相似".takeIf { similar.value?.isNotEmpty() == true }, "剧照 ${stills.size}".takeIf { hasStills }))
                }

                1 -> Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        TvSectionTop(
                            series,
                            listOfNotNull(
                                "${head.seasons.size} 季".takeIf { head.seasons.size > 1 },
                                series.count?.let { "共 ${grouped(it)} 集" },
                                up?.episodeNo?.let { "看到第 $it 集" },
                            ).joinToString(" · "),
                            today,
                        )
                        // ---- 季：一排可横滑的格子（季名 + 集数）----
                        Spacer(Modifier.height(42.dp))
                        val seasonKeys = head.seasons.map { "season-${it.id}" }
                        TvChipRow(G.SEASONS, seasonKeys, default = season?.let { "season-${it.id}" }) { i, key ->
                            val s = head.seasons[i]
                            TvChip(key, seasonName(s), s.id == season?.id, { groups.enter(G.EPS, graph.focus) }, count = s.episodes?.toString())
                        }
                        // ---- 分页：一季超过 50 集才有，标签是绝对集号 ----
                        if (pages.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            TvChipRow(G.PAGES, pages.map { "page-${it.index}" }, default = "page-$page", trailing = {
                                BasicText("每页 50 集 · 共 ${slice.value?.total ?: 0} 集", Modifier.padding(start = 16.dp), style = Guga.song(22.sp, color = colors.faint), maxLines = 1)
                            }) { i, key ->
                                TvChip(key, pages[i].label.replace("–", " – "), pages[i].index == page, { groups.enter(G.EPS, graph.focus) }, numeral = true)
                            }
                            Spacer(Modifier.height(30.dp))
                        } else {
                            groups.remove(G.PAGES)
                            Spacer(Modifier.height(48.dp))
                        }
                        // ---- 分集：400 × 225 横排，焦点卡停在左起第一格 ----
                        val currentKey = up?.let { "ep-${it.id}" }
                        when {
                            slice.value == null && slice.error != null -> ErrorView(slice.error!!, slice::retry, Modifier.padding(start = TvSpec.LEFT).height(320.dp))
                            slice.value == null -> Row(Modifier.padding(start = TvSpec.LEFT).height(320.dp), horizontalArrangement = Arrangement.spacedBy(TvDetailSpec.CARD_GAP)) {
                                repeat(5) { Skeleton(Modifier.width(TvSpec.EPISODE_W).height(TvDetailSpec.EP_H)) }
                            }
                            episodes.isEmpty() -> {
                                groups.remove(G.EPS)
                                BasicText(
                                    if ((slice.value?.total ?: 0) > 0) "分集未加载" else "没有分集",
                                    Modifier.padding(start = TvSpec.LEFT).height(320.dp),
                                    style = Guga.song(26.sp, color = colors.dim),
                                )
                            }
                            else -> TvLeadRow(G.EPS, episodes, { "ep-${it.id}" }, TvSpec.EPISODE_W, 320.dp, default = currentKey, visible = 5) { e, key, _ ->
                                val played = chrome.isPlayed(e.id, e.played && e.progress <= 0f)
                                TvEpisodeCard(
                                    e, key, played, current = key == currentKey,
                                    fallback = com.gugaart.ui.screens.detail.episodeStillFallback(e.episodeNo, stills, series.art.backdrop),
                                    subtitle = tvEpisodeSub(e, played, key == currentKey),
                                    onClick = { graph.play(e.id, e.positionSec?.times(1000)) },
                                    // 长按确定 = 这一集的菜单（标记已看 = 校对勾）；菜单本身是外壳画的
                                    onLongClick = { TvLongPressMenu.open(e) },
                                )
                            }
                        }
                        // ---- 焦点那一集的简介 + 主编按 ----
                        val focusedEp = (groups.lastOf(G.EPS) ?: currentKey)?.let { k -> episodes.firstOrNull { "ep-${it.id}" == k } } ?: episodes.firstOrNull()
                        Row(
                            Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 14.dp).fillMaxWidth().testTag("tv-episode-detail"),
                            horizontalArrangement = Arrangement.spacedBy(70.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                                if (focusedEp != null) {
                                    BasicText(tvEpisodeKick(focusedEp), Modifier.padding(top = 26.dp), style = Guga.song(22.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.14.em), maxLines = 1)
                                    BasicText(focusedEp.name.ifEmpty { "第 ${focusedEp.episodeNo ?: "?"} 集" }, Modifier.padding(top = 8.dp, bottom = 12.dp), style = Guga.song(40.sp, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BasicText(
                                        focusedEp.overview.ifEmpty { "没有简介" },
                                        style = Guga.song(26.sp, FontWeight.Medium, colors.dim, lineHeight = 42.sp),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Column(Modifier.width(560.dp)) {
                                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                                EditorNote(episodeNote(head, focusedEp, hour, today.day), Modifier.padding(top = 36.dp), label = "看点", english = "HIGHLIGHTS", voice = voice, photo = 68.dp)
                            }
                        }
                    }
                    TvDownHint(listOfNotNull("演职人员 ${series.people.size}".takeIf { series.people.isNotEmpty() }, "相似".takeIf { similar.value?.isNotEmpty() == true }, "剧照 ${stills.size}".takeIf { hasStills }))
                }

                2 -> Box(Modifier.fillMaxSize()) {
                    TvCastScreen(series, similar.value.orEmpty(), today, "演职人员 ${series.people.size} 位")
                    if (hasStills) TvDownHint(listOf("剧照 ${stills.size}"))
                }

                else -> if (hasStills) TvStillsScreen(series, stills, today) else groups.remove(TV_STILLS)
            }
        }
    }
}

/** 季的名字：有季号写「第 N 季」；季名乱码、没有季号（「G【古@语】」）→ 原样显示，不替用户改名。 */
private fun seasonName(s: Season): String = if (s.number != null && s.number > 0) "第 ${s.number} 季" else s.name

private fun grouped(n: Int): String = if (n < 1000) n.toString() else "${n / 1000},${(n % 1000).toString().padStart(3, '0')}"

/** 「主编按」跟着焦点那一集说一句（台词表 `MascotLines`，不是这里编的）：看到一半 → 停在哪；别的 → 这部剧的那一句。 */
private fun episodeNote(head: SeriesHead, e: Title?, hour: Int, day: Int): String? {
    if (e != null && e.progress > 0f && e.positionSec != null) {
        return MascotLines.pick(
            "detail.resume",
            LineFacts(ep = e.episodeNo, time = Rules.clock(e.positionSec * 1000), remain = e.runtimeSec?.let { Rules.minutes((it * (1f - e.progress)).toLong()) }, pct = e.pct),
            hour,
            e.id.hashCode() + day,
        )
    }
    return seriesNote(head, hour, day)
}

/** 第三屏（剧集、电影共用下半）：演职人员（170 × 226，彩色）→ 相似。[top]：演职人员上面还要排的东西（电影的印刷规格）。 */
@Composable
fun TvCastScreen(title: Title, similar: List<Title>, today: Today, note: String, top: (@Composable () -> Unit)? = null) {
    val graph = LocalGraph.current
    val groups = LocalTvGroups.current
    Column(Modifier.fillMaxSize().testTag("tv-cast-screen")) {
        TvSectionTop(title, note, today)
        top?.invoke()
        if (title.people.isNotEmpty()) {
            TvSecHead("演职人员", "CAST", Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 28.dp), trailing = "${title.people.size} 位")
            Spacer(Modifier.height(24.dp))
            TvLeadRow("cast", title.people.distinctBy { it.id }, { "person-${it.id}" }, TvSpec.PERSON_W, if (top == null) 370.dp else 336.dp, visible = 10) { p, key, _ ->
                TvPersonCard(p, key, { graph.open(p.id) })
            }
        } else {
            groups.remove("cast")
        }
        if (similar.isNotEmpty()) {
            TvSecHead("相似", "MORE LIKE THIS", Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = if (top == null) 12.dp else 0.dp))
            Spacer(Modifier.height(24.dp))
            TvLeadRow("similar", similar, { "similar-${it.id}" }, TvSpec.POSTER, 420.dp, visible = 8) { t, key, _ ->
                TvPosterCard(t, key, { graph.open(t.id) }, onLongClick = { TvLongPressMenu.open(t) })
            }
        } else {
            groups.remove("similar")
        }
    }
}

/**
 * 简介全文页（设计稿 `st=more`）：左 海报 + 评分 + 题材 / 首播 / 出品（截 5 家 +「等 N 家」）；右 双栏正文 + 首字下沉；
 * 下面「合上」（默认焦点）和续看。返回 = 合上。
 */
@Composable
fun TvSynopsisPage(title: Title, today: Today, playText: String, onClose: () -> Unit, onPlay: () -> Unit) {
    val colors = Guga.colors
    Box(Modifier.fillMaxSize().testTag("tv-synopsis")) {
        TvSectionTop(title, "简介全文", today)
        Column(Modifier.padding(start = TvSpec.LEFT, top = 170.dp).width(380.dp)) {
            Box(Modifier.width(280.dp).height(420.dp).border(1.dp, colors.line)) { CoverImage(title.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) }
            TvMeta(title.rating, emptyList(), Modifier.padding(top = 26.dp))
            val facts = listOfNotNull(
                title.genres.take(6).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { "题材" to it },
                tvDay(title.premiere)?.let { "首播" to it },
                Rules.studios(title.studios)?.let { "出品 · ${title.studios.size} 家" to it },
            )
            for ((k, v) in facts) {
                BasicText(k, Modifier.padding(top = 14.dp), style = Guga.song(20.sp, color = colors.spot, letterSpacing = 0.16.em), maxLines = 1)
                BasicText(v, Modifier.padding(top = 4.dp), style = Guga.song(23.sp, FontWeight.SemiBold, lineHeight = 34.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.padding(start = 600.dp, end = TvSpec.RIGHT, top = 166.dp).fillMaxSize()) {
            TvSecHead("简介", "SYNOPSIS", trailing = "${title.overview.length} 字")
            TvTwoColumns(title.overview, Modifier.padding(top = 18.dp).fillMaxWidth().height(690.dp))
            TvGroup("more", listOf("more-close", "more-play"), default = "more-close")
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                TvActionButton("more-close", "收起", onClose, icon = GuguIcon.ChevronLeft)
                TvActionButton("more-play", playText, onPlay, icon = GuguIcon.Play, primary = true)
            }
        }
    }
}

/** 双栏正文 + 首字下沉（专色大字占两行）。装不下的部分在第二栏末尾省略。 */
@Composable
fun TvTwoColumns(text: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val style: TextStyle = Guga.song(26.sp, FontWeight.Medium, lineHeight = 44.sp).copy(textAlign = TextAlign.Justify)
    val capStyle = Guga.song(104.sp, FontWeight.Black, colors.spot, lineHeight = 96.sp)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        val gap = 56.dp
        val colW = (maxWidth - gap) / 2
        val lines = (maxHeight.value / 44f).toInt().coerceAtLeast(3)
        val capW = 118.dp
        val body = text.trim()
        if (body.isEmpty()) return@BoxWithConstraints
        val cap = body.take(1)
        val rest = body.drop(1)
        val parts = remember(body, colW, lines) {
            with(density) {
                val a = measurer.measure(rest, style, constraints = Constraints(maxWidth = (colW - capW).roundToPx()), maxLines = 2)
                val endA = if (a.lineCount < 2) rest.length else a.getLineEnd(1, visibleEnd = false)
                val afterA = rest.substring(endA)
                val b = measurer.measure(afterA, style, constraints = Constraints(maxWidth = colW.roundToPx()), maxLines = lines - 2)
                val endB = if (b.lineCount < lines - 2) afterA.length else b.getLineEnd(b.lineCount - 1, visibleEnd = false)
                Triple(rest.substring(0, endA), afterA.substring(0, endB), afterA.substring(endB))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.width(colW)) {
                Row {
                    BasicText(cap, Modifier.width(capW).height(88.dp), style = capStyle, maxLines = 1)
                    BasicText(parts.first, style = style, maxLines = 2)
                }
                BasicText(parts.second, style = style, maxLines = lines - 2)
            }
            BasicText(parts.third, Modifier.width(colW), style = style, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
    }
}
