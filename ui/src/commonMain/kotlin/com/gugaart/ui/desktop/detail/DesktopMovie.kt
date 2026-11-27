package com.gugaart.ui.desktop.detail

import com.gugaart.ui.player.formatBadges
import com.gugaart.ui.player.FormatBadgeRow
import com.gugaart.ui.desktop.deskFocusRing
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.desktop.DesktopContextMenu
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.movieNote
import com.gugaart.ui.screens.detail.sizeText
import com.gugaart.ui.screens.detail.SourceDot
import com.gugaart.ui.screens.detail.SourceProgress
import com.gugaart.ui.screens.detail.SourceRowText
import com.gugaart.ui.screens.detail.SourceTags
import com.gugaart.ui.screens.detail.rememberSourcesState
import com.gugaart.ui.screens.detail.rememberTrackPicks
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.MediaStream
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp

/**
 * 选择器的一项（版本 / 音轨 / 字幕）：名字 + 一行小字。[index]：媒体流的 Index（版本用不上 = -1；字幕「关闭」= -1）。
 * [source]：版本这一项的小签（「推荐」「另一份文件」）；音轨 / 字幕 = null。
 */
data class PickOption(val name: String, val note: String, val index: Int = -1, val source: SourceRowText? = null)

/** 版本的一项（1.5.0「源」）：名字 = Emby 给这个版本起的名（没有 → 规格头一段），小字 = 「4K · HDR10 · DTS-HD MA · 直连」，带小签。 */
fun versionOption(r: SourceRowText): PickOption = PickOption(r.title, r.spec, source = r)

/** 音轨：「国语 · AAC 2.0」+ 小签「默认」（P1-2，[com.gugaart.ui.data.trackName]）。纯函数。 */
fun audioOption(s: MediaStream): PickOption = com.gugaart.ui.data.trackName(s).let { n -> PickOption(n.text, n.tags.joinToString(" · "), s.index) }

/** 字幕：「中文（简体）· SRT」+ 小签「默认」「外挂」（P1-2）。纯函数。 */
fun subtitleOption(s: MediaStream): PickOption = com.gugaart.ui.data.trackName(s).let { n -> PickOption(n.text, n.tags.joinToString(" · "), s.index) }

/**
 * 电影详情（设计稿 `p=movie`）：剧照从窗口顶端铺到 66 % 屏高；海报压在渐隐处（悬停时朝鼠标倾）；
 * 正文 = 海报 / 正文 / 资料（1100 宽资料掉到下一行；3168 宽多「同题材」）；版本 / 音轨 / 字幕是三行选择器（下拉 = 纸片），
 * 下面是完整的媒体信息表、演职人员、相似。没有上级剧集的单集也走这里。
 */
@Composable
fun DesktopMovie(movie: Title, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val colors = Guga.colors
    val hour = LocalHour.current
    val voice = rememberNoteVoice()
    val m = Guga.dimens.margin
    val gap = Guga.dimens.gutter * 2
    val width = dWidth()
    val state = preset.state
    val poster = rememberImage(movie.art.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }
    val fav = chrome.isFavorite(movie)
    val played = chrome.isPlayed(movie.id, movie.played)

    // 版本 = 「源」（本台的版本 + 没被合并的重复片）：先列条目自带的，重复片到了接在下面；选中的决定音轨 / 字幕 / 徽章 / 开播
    val sources = rememberSourcesState(movie)
    val source = sources.selected?.mediaSource ?: movie.sources.firstOrNull()
    // 音轨 / 字幕：和手机详情页同一份状态（screens/detail/TrackPickers.kt）——音轨默认 = 标了默认的那条，字幕「关闭」排在最前、
    // 默认 = 中文优先的第一条；开播只带用户点过的；换版本回到默认
    val picks = rememberTrackPicks(source)
    val audios = picks.audios
    val subs = picks.subs
    var openPick by remember { mutableStateOf(if (state == "ver") "ver" else null) }

    val playNow = {
        graph.playback.play(
            sources.playRoute(
                resumeMs = movie.positionSec?.takeIf { movie.progress > 0f }?.times(1000),
                audio = picks.audioIndex,
                subtitle = picks.subtitleIndex,
            ),
        )
    }
    val toggleFav = {
        graph.toggleFavorite(chrome, movie.id, fav)
        voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = movie.id.hashCode() + today.day))
    }
    DesktopItemActions.favorite = toggleFav
    DesktopItemActions.played = { graph.togglePlayed(chrome, movie.id, played) }
    DesktopItemActions.previousSeason = null
    DesktopItemActions.nextSeason = null
    DesktopItemActions.play = { playNow() }

    // 栏宽：海报 2.6 / 正文 6 / 资料 3.4（1100：3 / 6；3168：2.2 / 5.6 / 3.2 / 3.4）
    val content = LocalWindowDp.current.width - m * 2
    val cols = when (width) {
        DWidth.Sm -> floatArrayOf(3f, 6f)
        DWidth.Md -> floatArrayOf(2.6f, 6f, 3.4f)
        DWidth.Xl -> floatArrayOf(2.2f, 5.6f, 3.2f, 3.4f)
    }
    val posterW = (content - gap * (cols.size - 1)) * (cols[0] / cols.sum())

    val facts: @Composable (Modifier) -> Unit = { mod ->
        DFacts(mod) {
            DPeople("导演", movie.people.filter { it.type == "Director" }.take(3)) { graph.open(it.id) }
            DPeople("编剧", movie.people.filter { it.type == "Writer" }.take(2)) { graph.open(it.id) }
            DPeople("主演", movie.people.filter { it.type == "Actor" || it.type == null }.take(4)) { graph.open(it.id) }
            DFact("片长", movie.runtimeSec?.let { Rules.minutes(it) })
            DFact("上映", cnDate(movie.premiere) ?: movie.year?.toString())
            DFact("出品", movie.studios.take(2).joinToString(" / "))
            DFact("类型", movie.genres.take(6).joinToString(" / "))
            DFact("分级", movie.officialRating ?: "未分级")
        }
    }

    val anchor = when (state) {
        "streams" -> "streams"
        "cast" -> "cast"
        else -> null
    }
    DetailScaffold("desktop-movie", anchor) { mark ->
        DHero(
            movie,
            DetailSpec.MOVIE_HERO,
            caption = "剧照 ·《${movie.name}》",
            kicker = (listOf(if (movie.kind == Kind.Episode) "单集" else "电影") + listOfNotNull(movie.genres.take(3).joinToString(" / ").takeIf { it.isNotEmpty() }, movie.year?.toString(), movie.runtimeSec?.let { Rules.minutes(it) })).joinToString(" · "),
            ratingNote = ratingNote(movie),
            startPad = posterW + gap,
            under = {
                movie.lead?.takeIf { it.length <= 40 && !movie.overview.startsWith(it) }?.let { lead ->
                    BasicText("「$lead」", Modifier.padding(bottom = Guga.rem(1.6f)).testTag("d-tagline"), style = Guga.song(rsp(1.8f), lineHeight = rsp(3.06f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // 片源的效果徽章（跟着选中的版本 / 音轨走；用户 2026-11-16：「影片详情页也可以打上对应的图标」）
                FormatBadgeRow(formatBadges(source, audios.getOrNull(picks.audio)?.index, null), Guga.rem(6f).value / 44f, Modifier.padding(bottom = Guga.rem(1.6f)), notes = false)
            },
        )
        Row(Modifier.padding(horizontal = m).padding(top = Guga.rem(2.4f)).fillMaxWidth()) {
            // 海报：压在剧照的渐隐处（往上提 14 rem），悬停朝鼠标倾
            TiltPoster(movie, Modifier.width(posterW).offset(y = -Guga.rem(DetailSpec.POSTER_LIFT)))
            Spacer(Modifier.width(gap))
            Column(Modifier.weight(cols[1])) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(2f))) {
                    DButton(
                        if (movie.progress > 0f) "续看" else "播放",
                        { playNow() },
                        Modifier.weight(1f, fill = false),
                        trailing = if (movie.progress > 0f && movie.positionSec != null && movie.runtimeSec != null) "${Rules.clock(movie.positionSec * 1000)} / ${Rules.clock(movie.runtimeSec * 1000)}" else movie.runtimeSec?.let { Rules.minutes(it) },
                        progress = movie.progress.takeIf { it > 0f },
                        tag = "d-cta",
                    )
                    DAction("收藏", toggleFav, tag = "d-fav")
                    SealSlot(fav, size = Guga.rem(3.6f))
                    DAction("已看", { graph.togglePlayed(chrome, movie.id, played) }, tag = "d-seen") { ProofTick(played, size = Guga.rem(1.4f)) }
                    var at by remember { mutableStateOf(Offset.Zero) }
                    DAction("更多", { DesktopContextMenu.open(movie, at) }, Modifier.onGloballyPositioned { at = it.positionInWindow() }, tag = "d-more") { Icon(GuguIcon.Ellipsis, colors.ink, Guga.rem(1.8f)) }
                }
                // 版本 / 音轨 / 字幕：三行选择器。整组压在后面的简介之上（zIndex）：版本一多，推开的纸片会伸进简介那一块
                Column(Modifier.padding(top = Guga.rem(2.8f)).zIndex(1f).testTag("d-pickers")) {
                    Rule(strong = true)
                    val rows = sources.sources
                    val versions = rows.map { versionOption(sources.rowText(it)) }.ifEmpty { listOf(PickOption("默认版本", "没有媒体信息")) }
                    // 有别的服务器参与时这一栏叫「源」，每行前面是服务器名；单子最后是别台的进展（B2-UI）
                    PickRow(
                        sources.sectionTitle, versions, rows.indexOfFirst(sources::isSelected).coerceIn(0, versions.lastIndex), openPick == "ver",
                        { openPick = if (openPick == "ver") null else "ver" }, { i -> rows.getOrNull(i)?.let(sources::pick); openPick = null }, force = state == "ver",
                        footer = { SourceProgress(sources, rsp(1.15f), Guga.rem(0.55f), Modifier.padding(horizontal = Guga.rem(1.6f), vertical = Guga.rem(0.8f)), lineGap = Guga.rem(0.4f)) },
                    )
                    if (audios.isNotEmpty()) PickRow("音轨", audios, picks.audio.coerceIn(0, audios.lastIndex), openPick == "audio", { openPick = if (openPick == "audio") null else "audio" }, { picks.audio = it; openPick = null })
                    PickRow("字幕", subs, picks.sub.coerceIn(0, subs.lastIndex), openPick == "sub", { openPick = if (openPick == "sub") null else "sub" }, { picks.sub = it; openPick = null })
                }
                DOverview(movie.overview, Modifier.padding(top = Guga.rem(3.6f)))
                Column(Modifier.padding(top = Guga.rem(3.2f))) {
                    Rule()
                    EditorNote(movieNote(movie, hour, today.day), Modifier.padding(vertical = Guga.rem(1.4f)), label = "看点", english = "HIGHLIGHTS", voice = voice, photo = Guga.rem(5.2f))
                    Rule()
                }
                if (width == DWidth.Sm) facts(Modifier.padding(top = Guga.rem(3.2f)).fillMaxWidth())
            }
            if (width != DWidth.Sm) {
                Spacer(Modifier.width(gap))
                facts(Modifier.weight(cols[2]))
            }
            // 3168 宽的第四栏「同题材」要相似条目的数据；服务器给不出时这一栏空着（不编）
            if (width == DWidth.Xl) {
                Spacer(Modifier.width(gap))
                Column(Modifier.weight(cols[3])) { SeeAlso(movie) }
            }
        }

        // ---- 01 媒体信息
        var n = 0
        if (source != null && source.mediaStreams.isNotEmpty()) {
            n++
            Column(mark("streams").padding(horizontal = m).padding(top = Guga.rem(DetailSpec.SECTION)).testTag("d-streams")) {
                DSec("0$n", "媒体信息", "STREAMS", trailing = listOfNotNull(source.container?.uppercase(), sizeText(source.size), "${source.mediaStreams.size} 条流").joinToString(" · "))
                StreamTable(source.mediaStreams)
            }
        }
        if (movie.people.isNotEmpty()) {
            n++
            Column(mark("cast").padding(top = Guga.rem(DetailSpec.SECTION))) {
                DSec("0$n", "演职人员", "CAST & CREW", Modifier.padding(horizontal = m), trailing = "${movie.people.size} 位")
                DCastRow(movie.people, { graph.open(it.id) })
            }
        }
        // ---- 剧照（演职人员之后；点开看大图，盖住整个窗口）
        val stills = com.gugaart.ui.screens.detail.rememberItemStills(movie.id)
        if (com.gugaart.ui.screens.detail.showStills(stills)) {
            n++
            Column(mark("stills").padding(top = Guga.rem(DetailSpec.SECTION))) {
                DSec("0$n", "剧照", "STILLS", Modifier.padding(horizontal = m), trailing = "${stills.size} 张")
                DStills(stills)
            }
        }
        DSimilar(movie, "0${n + 1}")
        DEnd(listOfNotNull(movie.name, movie.year?.toString()).joinToString(" · "), Modifier.padding(horizontal = m))
    }
}

/** 压在渐隐处的海报：米白细边；悬停时朝鼠标倾（最多 ±7°）。没有海报 → 斜纹空版。 */
@Composable
private fun TiltPoster(t: Title, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    var tilt by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.aspectRatio(2f / 3f).zIndex(2f)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        when (e.type) {
                            PointerEventType.Move, PointerEventType.Enter -> {
                                val p = e.changes.first().position
                                tilt = Offset((p.x / size.width - 0.5f).coerceIn(-0.5f, 0.5f), (p.y / size.height - 0.5f).coerceIn(-0.5f, 0.5f))
                            }
                            PointerEventType.Exit -> tilt = Offset.Zero
                        }
                    }
                }
            }
            .graphicsLayer {
                rotationY = tilt.x * 14f
                rotationX = -tilt.y * 14f
                cameraDistance = 16f * density
            }
            .border(1.dp, colors.ink.copy(alpha = 0.4f))
            .testTag("d-float-poster"),
    ) {
        CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), caption = t.name) }
    }
}

/** 选择器的一行：标签 | 当前项（名字 + 小字）| 下拉箭头（只有一项时没有箭头、点不开）。推开的纸片盖在下面的行上。 */
@Composable
private fun PickRow(label: String, options: List<PickOption>, selected: Int, open: Boolean, onToggle: () -> Unit, onPick: (Int) -> Unit, force: Boolean = false, footer: (@Composable () -> Unit)? = null) {
    val colors = Guga.colors
    val cur = options[selected]
    val many = options.size > 1
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().then(if (many || force) Modifier.deskFocusRing(press).pressable(press, label = "$label：${cur.name}", onClick = onToggle) else Modifier).padding(vertical = Guga.rem(1.3f)).testTag("d-pick-$label"), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.width(Guga.rem(7.2f)), style = Guga.song(rsp(1.3f), color = colors.faint), maxLines = 1)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            cur.source?.let { SourceDot(it, Guga.rem(0.7f), gap = Guga.rem(0.7f)) }
            BasicText(cur.name, Modifier.weight(1f, fill = false), style = Guga.song(rsp(1.5f), FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            cur.source?.let { SourceTags(it, rsp(1.05f), 1.dp, gap = Guga.rem(0.6f)) }
            if (cur.note.isNotEmpty()) BasicText(cur.note, Modifier.padding(start = Guga.rem(0.8f)).then(if (cur.source != null) Modifier.weight(1f, fill = false) else Modifier), style = Guga.song(rsp(1.2f), color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (many) Icon(if (open) GuguIcon.ChevronUp else GuguIcon.ChevronDown, colors.ink, Guga.rem(1.4f))
    }
    Rule()
    if (open && (many || force)) {
        Box(Modifier.zIndex(4f).fillMaxWidth().height(0.dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
            DSlip(Modifier.padding(start = Guga.rem(7.2f)).fillMaxWidth().testTag("d-pick-list")) {
                options.forEachIndexed { i, o ->
                    val r = o.source
                    DSlipRow(
                        o.name, null, i == selected, { onPick(i) }, note = o.note,
                        tags = if (r == null) null else { onInk -> SourceTags(r, rsp(1.05f), 1.dp, onInk = onInk, gap = Guga.rem(0.6f)) },
                        lead = if (r?.server == null) null else { onInk -> SourceDot(r, Guga.rem(0.65f), onInk = onInk, gap = Guga.rem(0.65f)) },
                    )
                }
                footer?.invoke()
            }
        }
    }
}

/** 媒体信息表：一条流一行（类型 / 标题 / 编码 / 规格 / 语言 / 来源 / 默认）。全部是真实媒体流。 */
@Composable
private fun StreamTable(streams: List<MediaStream>) {
    val colors = Guga.colors
    val weights = floatArrayOf(1f, 4.4f, 1.4f, 2.6f, 1.4f, 1.6f, 0.8f)
    Row(Modifier.fillMaxWidth().padding(bottom = Guga.rem(1.1f))) {
        listOf("类型", "标题", "编码", "规格", "语言", "来源", "默认").forEachIndexed { i, h ->
            BasicText(h, Modifier.weight(weights[i]), style = Guga.note(rsp(1.1f), colors.spot), maxLines = 1)
        }
    }
    Rule(strong = true)
    for (s in streams) {
        val hover = rememberHover()
        Row(Modifier.fillMaxWidth().hover(hover).padding(vertical = Guga.rem(1.1f)).testTag("d-stream-row"), verticalAlignment = Alignment.CenterVertically) {
            val cells = listOf(
                when (s.type) {
                    "Video" -> "视频"
                    "Audio" -> "音轨"
                    "Subtitle" -> "字幕"
                    else -> s.type
                },
                (s.displayTitle ?: s.title)?.trim()?.takeIf { it.isNotEmpty() } ?: "—",
                s.codec?.uppercase() ?: "—",
                when (s.type) {
                    "Video" -> if (s.width != null && s.height != null) "${s.width} × ${s.height} · ${s.videoRange?.takeIf { it.isNotBlank() } ?: "SDR"}" else "—"
                    "Audio" -> s.channels?.let { "$it 声道" } ?: "—"
                    else -> "—"
                },
                s.language?.takeIf { it.isNotEmpty() && it != "und" } ?: "未标注",
                if (s.isExternal == true) "外挂文件" else "内封",
                if (s.isDefault == true) "✓" else "",
            )
            cells.forEachIndexed { i, c ->
                val style = when (i) {
                    0 -> Guga.note(rsp(1.4f), colors.faint)
                    1 -> Guga.song(rsp(1.4f), FontWeight.Bold, if (hover.value) colors.spot else colors.ink)
                    else -> Guga.song(rsp(1.4f))
                }
                BasicText(c, Modifier.weight(weights[i]).padding(end = Guga.rem(1.2f)), style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Rule()
    }
}

/** 3168 宽的第四栏「同题材」：相似条目里评分最高的四部（海报 + 片名 + 评分）。取不到 → 不出现。 */
@Composable
private fun SeeAlso(of: Title) {
    val graph = LocalGraph.current
    val similar = com.gugaart.ui.data.rememberLoad("see-also", of.id) { c ->
        try {
            c.getSimilar(of.id, limit = 12).items.map { c.titleOf(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
    val list = similar.value.orEmpty().sortedByDescending { it.rating?.toDoubleOrNull() ?: 0.0 }.take(4)
    if (list.isEmpty()) return
    DSec(null, "同题材", "SEE ALSO")
    list.forEachIndexed { i, t ->
        DRow(Rules.episodeNo(i + 1), t.name, listOfNotNull(t.year?.toString(), t.genres.firstOrNull()).joinToString(" · "), { graph.open(t.id) }, context = t, middle = { DMiniPoster(t) }, trailing = {
            t.rating?.let { BasicText(it, style = Guga.numeral(rsp(2.4f)), maxLines = 1) }
        })
    }
}
