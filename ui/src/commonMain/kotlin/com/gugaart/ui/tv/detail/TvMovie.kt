package com.gugaart.ui.tv.detail

import com.gugaart.ui.player.formatBadges
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.rememberItemStills
import com.gugaart.ui.screens.detail.showStills
import com.gugaart.ui.screens.detail.SourceDot
import com.gugaart.ui.screens.detail.SourceProgress
import com.gugaart.ui.screens.detail.SourceTags
import com.gugaart.ui.screens.detail.rememberSourcesState
import com.gugaart.ui.screens.detail.streamRow
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.TvSpec
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.MediaStream
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon

/** 「2.1 GB」「11.6 Mbps」。 */
fun tvGb(bytes: Long?): String? = bytes?.takeIf { it > 0 }?.let { "${kotlin.math.round(it / 1073741824.0 * 10) / 10} GB" }
fun tvMbps(bits: Long?): String? = bits?.takeIf { it > 0 }?.let { "${kotlin.math.round(it / 1_000_000.0 * 10) / 10} Mbps" }

/** 「版本」按钮里的小字：选中那个源的名字，太长截到 12 个字（按钮排在一行里）。纯函数。 */
fun tvVersionSub(title: String): String = if (title.length > 12) title.take(11) + "…" else title

/** 按钮里的小字：默认音轨 / 默认字幕的名字（「关 · 有 3 条」「无」）。纯函数，有单测。 */
fun tvSubtitleSummary(subs: List<MediaStream>, selected: Int?): String = when {
    subs.isEmpty() -> "无"
    selected == null || selected < 0 -> "关 · 有 ${subs.size} 条"
    else -> subs.firstOrNull { it.index == selected }?.let { streamRow(it).name } ?: "关 · 有 ${subs.size} 条"
}

/**
 * 电影详情的方向键路径（`docs/platforms.md` §3）。[side]：右边页边选项栏现在开着哪一个（ver / tracks / null）。纯函数，有单测。
 * 返回 [CLOSE_SIDE] = 收起页边栏、焦点回按钮行。
 */
fun movieEdge(from: String, dir: TvDir, has: (String) -> Boolean): String? = when (from) {
    "cta" -> when (dir) {
        TvDir.Down -> listOf("cast", "similar", "stills").firstOrNull(has) ?: TvGroups.STAY
        TvDir.Left -> null
        else -> TvGroups.STAY
    }
    "cast" -> when (dir) {
        TvDir.Up -> "cta"
        TvDir.Down -> listOf("similar", "stills").firstOrNull(has) ?: TvGroups.STAY
        TvDir.Left -> null
        else -> TvGroups.STAY
    }
    "similar" -> when (dir) {
        TvDir.Up -> if (has("cast")) "cast" else "cta"
        TvDir.Down -> if (has("stills")) "stills" else TvGroups.STAY
        TvDir.Left -> null
        else -> TvGroups.STAY
    }
    // 最后一屏：剧照（≥ 2 张才有）
    "stills" -> when (dir) {
        TvDir.Up -> listOf("similar", "cast").firstOrNull(has) ?: "cta"
        TvDir.Left -> null
        else -> TvGroups.STAY
    }
    // 页边选项：上下走；左 = 收起；音轨和字幕两段上下相接
    "side-ver", "side-audio", "side-sub" -> when (dir) {
        TvDir.Left -> CLOSE_SIDE
        TvDir.Down -> if (from == "side-audio" && has("side-sub")) "side-sub" else TvGroups.STAY
        TvDir.Up -> if (from == "side-sub" && has("side-audio")) "side-audio" else TvGroups.STAY
        else -> TvGroups.STAY
    }
    else -> null
}

const val CLOSE_SIDE = "!close-side"

/**
 * 电影详情（电视，设计稿 `p=movie`）：整幅剧照同一种铺法。默认焦点「播放」；左右：播放 → 版本 → 音轨 → 字幕 → 收藏 → 已看；
 * 在版本 / 音轨 / 字幕上按确定，**右边页边出选项**（上下走、确定选中、返回或左收起），剧照和片名不被盖；下 = 规格与演职人员 → 相似 → 剧照（≥ 2 张才有）。
 * 选中的版本 / 音轨 / 字幕交给上游的播放路由（`Route.Play(mediaSourceId, audio, subtitle)`）。单集（`Kind.Episode`）也用这一版。
 */
@Composable
fun TvMovie(movie: Title, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val colors = Guga.colors
    val hour = LocalHour.current
    val groups = remember { TvGroups() }
    val back = LocalBackDispatcher.current
    val poster = rememberImage(movie.art.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot(tv = true) }
    val similar = rememberLoad("tv-similar", movie.id) { loadSimilar(it, movie.id) }
    val stills = rememberItemStills(movie.id)
    val hasStills = showStills(stills)

    // 版本 = 「源」（本台的版本 + 没被合并的重复片）：选中的那个决定音轨 / 字幕 / 徽章 / 开播
    val sources = rememberSourcesState(movie)
    val picked = sources.selected
    val source = picked?.mediaSource ?: movie.sources.firstOrNull()
    val audios = source?.mediaStreams.orEmpty().filter { it.type == "Audio" }
    val subs = com.gugaart.ui.player.chineseSubtitlesFirst(source?.mediaStreams.orEmpty().filter { it.type == "Subtitle" })
    var audio by remember(movie.id, picked?.itemId, source?.id) { mutableStateOf((audios.firstOrNull { it.isDefault == true } ?: audios.firstOrNull())?.index) }
    var sub by remember(movie.id, picked?.itemId, source?.id) { mutableStateOf(subs.firstOrNull { it.isDefault == true }?.index) }
    // 页边选项栏：ver / tracks / null
    var side by remember { mutableStateOf(preset.state?.takeIf { it == "ver" || it == "tracks" }) }
    var sideFrom by remember { mutableStateOf("cta-audio") }

    val fav = chrome.isFavorite(movie)
    val played = chrome.isPlayed(movie.id, movie.played)
    val screen = when (groups.currentGroup) {
        "cast", "similar" -> 1
        TV_STILLS -> 2
        else -> 0
    }

    fun closeSide() {
        side = null
        graph.focus.request(sideFrom)
    }
    groups.edge = { from, dir ->
        val to = movieEdge(from, dir, groups::has)
        if (to == CLOSE_SIDE) {
            closeSide()
            TvGroups.STAY
        } else to
    }
    DisposableEffect(side, screen) {
        val un = when {
            side != null -> back.push {
                closeSide()
                true
            }
            screen == 2 -> back.push { groups.enter(listOf("similar", "cast").firstOrNull(groups::has) ?: "cta", graph.focus) }
            screen == 1 -> back.push { groups.enter("cta", graph.focus) }
            else -> null
        }
        onDispose { un?.invoke() }
    }
    // 页边栏打开后焦点进去：落在选中的那一项
    LaunchedEffect(side) {
        when (side) {
            // 「版本」每次打开都落在选中的那一行（不是上次停的那一行）
            "ver" -> {
                groups.forget("side-ver")
                groups.enterWhenReady("side-ver", graph.focus)
            }
            "tracks" -> {
                kotlinx.coroutines.delay(1)
                groups.enterWhenReady(if (sideFrom == "cta-sub" && groups.has("side-sub")) "side-sub" else if (groups.has("side-audio")) "side-audio" else "side-sub", graph.focus)
            }
            else -> Unit
        }
    }
    val play: () -> Unit = {
        graph.playback.play(sources.playRoute(movie.positionSec?.times(1000), audio = audio, subtitle = sub))
    }

    TvGroupsRoot(groups, Modifier.testTag("tv-movie")) {
        TvBackdrop(movie.art, deep = screen > 0, sided = side != null)
        TvScreens(screen, if (hasStills) 3 else 2) { index ->
            if (index == 0) Box(Modifier.fillMaxSize()) {
                TvRunHead(badges = formatBadges(source, audio, null), english = if (movie.kind == Kind.Episode) "EPISODE" else "FILM", caption = if (movie.art.backdrop != null) "剧照｜主视觉" else "没有剧照｜用海报虚化代替", today = today)
                Column(Modifier.align(Alignment.BottomStart).padding(start = TvSpec.LEFT, bottom = TvDetailSpec.HERO_BOTTOM).width(TvDetailSpec.HERO_WIDTH)) {
                    TvKick(tvKickOf(movie, tvKindName(movie.kind)))
                    TvTitle(movie, Modifier.padding(top = 14.dp, bottom = 16.dp))
                    TvMeta(movie.rating, listOfNotNull(movie.runtimeSec?.let { Rules.minutes(it) }, movie.officialRating, movie.studios.firstOrNull()))
                    val lead = tvFirstSentence(movie.overview)
                    if (lead.isNotEmpty()) BasicText(lead, Modifier.padding(top = 18.dp).width(980.dp), style = Guga.song(28.sp, FontWeight.SemiBold, lineHeight = 43.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // 版本只有一个时不出现入口（真实数据里通常只有一个）；没有音轨 / 字幕信息的同理
                    val keys = listOfNotNull("cta-play", "cta-ver".takeIf { sources.sources.size > 1 }, "cta-audio".takeIf { audios.isNotEmpty() }, "cta-sub".takeIf { source != null }, "cta-fav", "cta-played")
                    TvGroup("cta", keys, default = "cta-play")
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                        TvActionButton(
                            "cta-play", if (movie.progress > 0f) "续看" else "播放", play, icon = GuguIcon.Play, primary = true,
                            sub = if (movie.progress > 0f) Rules.remaining(movie.progress, movie.runtimeSec) else null, progress = movie.progress, default = true,
                        )
                        if (sources.sources.size > 1) TvActionButton("cta-ver", sources.sectionTitle, { sideFrom = "cta-ver"; side = "ver" }, icon = GuguIcon.Layers, sub = picked?.let { tvVersionSub(sources.rowText(it).title) })
                        if (audios.isNotEmpty()) TvActionButton("cta-audio", "音轨", { sideFrom = "cta-audio"; side = "tracks" }, icon = GuguIcon.AudioLines, sub = audios.firstOrNull { it.index == audio }?.let { streamRow(it).name })
                        if (source != null) TvActionButton("cta-sub", "字幕", { if (subs.isNotEmpty()) { sideFrom = "cta-sub"; side = "tracks" } }, icon = GuguIcon.Captions, sub = tvSubtitleSummary(subs, sub))
                        TvActionButton("cta-fav", "收藏", {
                            graph.toggleFavorite(chrome, movie.id, fav)
                            MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = movie.id.hashCode() + today.day)
                        }, icon = GuguIcon.Heart) { TvSealSlot(fav, it) }
                        TvActionButton("cta-played", if (played) "已看" else "标记已看", { graph.togglePlayed(chrome, movie.id, played) }, icon = GuguIcon.Check)
                    }
                }
                TvDownHint(listOfNotNull("规格".takeIf { source != null }, "演职人员 ${movie.people.size}".takeIf { movie.people.isNotEmpty() }, "相似".takeIf { similar.value?.isNotEmpty() == true }, "剧照 ${stills.size}".takeIf { hasStills }))
            } else if (index == 1) Box(Modifier.fillMaxSize()) {
                TvCastScreen(movie, similar.value.orEmpty(), today, "规格 · 演职人员 · 相似") { if (source != null) TvSpecTable(source, audios, subs) }
                if (hasStills) TvDownHint(listOf("剧照 ${stills.size}"))
            } else {
                TvStillsScreen(movie, stills, today)
            }
        }
        // ---- 右边页边的选项栏（纸面，不是玻璃）----
        if (side != null) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .width(1920.dp - TvDetailSpec.SIDE_LEFT)
                    .fillMaxHeight()
                    .background(Color(0xFF14120E))
                    .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                    .padding(start = 48.dp, end = TvSpec.RIGHT, top = 120.dp)
                    .testTag("tv-movie-side-$side"),
            ) {
                if (side == "ver") {
                    // 每个源一行（只往后接、不重排，所以按位置编键）；焦点先落在选中的那一行；确定 = 选中 + 记住 + 收起。
                    // 有别的服务器参与时叫「源」：每行前面是服务器名和小圆点，单子最后是别台的进展；行多了在这一块里滚（焦点带着走）
                    val rows = sources.sources
                    TvSecHead(sources.sectionTitle, sources.sectionEnglish, trailing = "${rows.size} 个")
                    val keys = rows.indices.map { "ver-$it" }
                    TvGroup("side-ver", keys, horizontal = false, default = "ver-${rows.indexOfFirst(sources::isSelected).coerceAtLeast(0)}")
                    Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        rows.forEachIndexed { i, s ->
                            val r = sources.rowText(s)
                            TvOption(
                                "ver-$i", r.title, r.spec, sources.isSelected(s),
                                tags = { focused -> SourceTags(r, 20.sp, 2.dp, onInk = focused, gap = 12.dp) },
                                lead = if (r.server == null) null else { focused -> SourceDot(r, 12.dp, onInk = focused, gap = 12.dp) },
                            ) {
                                sources.pick(s)
                                closeSide()
                            }
                        }
                        SourceProgress(sources, 21.sp, 10.dp, Modifier.padding(top = 18.dp, start = 20.dp), lineGap = 8.dp)
                    }
                    BasicText("码率超过设置里的上限时，会先问你要不要服务器转码。", Modifier.padding(top = 34.dp), style = Guga.song(21.sp, color = colors.faint, lineHeight = 34.sp))
                } else {
                    if (audios.isNotEmpty()) {
                        TvSecHead("音轨", "AUDIO", trailing = "${audios.size} 条")
                        TvGroup("side-audio", audios.map { "audio-${it.index}" }, horizontal = false, default = "audio-$audio")
                        for (s in audios) {
                            val r = streamRow(s)
                            TvOption("audio-${s.index}", r.name, r.note, s.index == audio) { audio = s.index }
                        }
                        Box(Modifier.height(34.dp))
                    } else groups.remove("side-audio")
                    if (subs.isNotEmpty()) {
                        TvSecHead("字幕", "SUBTITLES", trailing = "${subs.size} 条")
                        TvGroup("side-sub", listOf("sub-off") + subs.map { "sub-${it.index}" }, horizontal = false, default = if (sub == null) "sub-off" else "sub-$sub")
                        TvOption("sub-off", "关", "", sub == null) { sub = null }
                        for (s in subs) {
                            val r = streamRow(s)
                            TvOption("sub-${s.index}", r.name, r.note, s.index == sub) { sub = s.index }
                        }
                    } else groups.remove("side-sub")
                }
            }
        } else {
            groups.remove("side-ver"); groups.remove("side-audio"); groups.remove("side-sub")
        }
    }
}

/** 页边选项（设计稿 `.li.opt`）：左 小方块（选中 = 专色实心），名字 + 第二行的区分；焦点 = 整行米白。[tags]：名字后面的小签（参数 = 有没有焦点）。 */
@Composable
fun TvOption(key: String, name: String, note: String, selected: Boolean, modifier: Modifier = Modifier, tags: (@Composable (focused: Boolean) -> Unit)? = null, dim: Boolean = false, lead: (@Composable (focused: Boolean) -> Unit)? = null, onClick: () -> Unit) {
    val colors = Guga.colors
    TvBlockFocus(key, onClick, modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, label = name) { focused ->
        // [dim]：灰着的一项（按了不变，例如「参与的服务器」里正在用的那一台）
        val ink = (if (focused) colors.onInk else colors.ink).let { if (dim) it.copy(alpha = 0.45f) else it }
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(horizontal = 20.dp, vertical = 8.dp).testTag(if (selected) "tv-option-on" else "tv-option"), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(18.dp).then(if (dim) Modifier.alpha(0.45f) else Modifier).then(
                    if (selected) Modifier.background(if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot) else Modifier.border(2.dp, ink.copy(alpha = 0.6f)),
                ),
            )
            Column(Modifier.padding(start = 22.dp)) {
                if (tags == null && lead == null) BasicText(name, style = Guga.song(27.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                else Row(verticalAlignment = Alignment.CenterVertically) {
                    lead?.invoke(focused)
                    BasicText(name, Modifier.weight(1f, fill = false), style = Guga.song(27.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    tags?.invoke(focused)
                }
                if (note.isNotEmpty()) BasicText(note, style = Guga.song(21.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 印刷规格（设计稿 `.spec`）：文件 / 容器 · 大小 / 视频 / 音轨 / 字幕，全部来自真实媒体流；没有的那一行不出现。 */
@Composable
fun TvSpecTable(source: MediaSourceInfo, audios: List<MediaStream>, subs: List<MediaStream>) {
    val colors = Guga.colors
    val v = source.mediaStreams.firstOrNull { it.type == "Video" }
    val strm = source.container.equals("strm", ignoreCase = true) || source.isRemote == true
    val rows = listOfNotNull(
        source.name?.takeIf { it.isNotBlank() }?.let { "文件" to it + if (strm) "　· strm 直链" else "" },
        listOfNotNull(source.container?.uppercase(), tvGb(source.size), tvMbps(source.bitrateOrNull())).joinToString(" · ").takeIf { it.isNotEmpty() }?.let { "容器 · 大小" to it },
        v?.let { "视频" to listOfNotNull(if (it.width != null && it.height != null) "${it.width} × ${it.height}" else null, it.codec?.uppercase(), it.videoRange?.takeIf { r -> r.isNotBlank() }).joinToString(" · ") },
        audios.takeIf { it.isNotEmpty() }?.let { a -> "音轨" to a.joinToString("　/　") { s -> streamRow(s).let { r -> listOf(r.name, r.note).filter(String::isNotEmpty).joinToString(" · ") } } },
        "字幕" to if (subs.isEmpty()) "没有字幕（服务器上这部片子一条都没有）" else subs.joinToString("　/　") { s -> streamRow(s).name },
    )
    Column(Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 20.dp).testTag("tv-spec")) {
        TvSecHead("放映规格", "SPECS")
        for ((k, value) in rows) {
            Row(Modifier.fillMaxWidth().height(52.dp).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, verticalAlignment = Alignment.CenterVertically) {
                BasicText(k, Modifier.width(220.dp), style = Guga.song(25.sp, color = colors.dim), maxLines = 1)
                BasicText(value, style = Guga.song(25.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun MediaSourceInfo.bitrateOrNull(): Long? = mediaStreams.mapNotNull { it.bitRate }.takeIf { it.isNotEmpty() }?.sum()

@Suppress("unused")
private val keepKind = TvFocusKind.Block
