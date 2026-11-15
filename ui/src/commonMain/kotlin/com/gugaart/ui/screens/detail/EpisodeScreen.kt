package com.gugaart.ui.screens.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.components.BleedSource
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.Kicker
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.bleedSourceOf
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.BackRow
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.rememberPresetScroll
import com.gugaart.ui.screens.PageEnd
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 单集详情整幅图的高度（手机）。 */
internal val EPISODE_BLEED = 430.dp

/** 这一集的前后两集（同一季里）。没有 = null（第一集没有上一集）。 */
@Immutable
data class EpisodeNeighbours(val previous: Title?, val next: Title?)

/** 在一季的分集里找前后两集。纯函数。 */
fun neighboursOf(episodes: List<Title>, id: String): EpisodeNeighbours {
    val at = episodes.indexOfFirst { it.id == id }
    if (at < 0) return EpisodeNeighbours(null, null)
    return EpisodeNeighbours(episodes.getOrNull(at - 1), episodes.getOrNull(at + 1))
}

/** 这一季的分集（只读，`getEpisodes`）→ 前后两集。没有所属的剧 / 取不到 → 两边都空，这一栏不出现。 */
suspend fun loadNeighbours(client: EmbyClient, episode: Title): EpisodeNeighbours {
    val series = episode.seriesId ?: return EpisodeNeighbours(null, null)
    val list = client.getEpisodes(series, season = episode.seasonNo, o = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio"))).items.map { client.titleOf(it) }
    return neighboursOf(list, episode.id)
}

/** 「文件」一栏的一行：专色的小标签 + 内容。 */
@Immutable
data class MediaRow(val label: String, val text: String)

private fun fps(v: Double?): String? = v?.takeIf { it > 0 }?.let { f ->
    val r = kotlin.math.round(f * 1000) / 1000
    (if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString()) + " 帧"
}

/** 「2.61 GB」「812 MB」。 */
fun sizeText(bytes: Long?): String? {
    val b = bytes?.takeIf { it > 0 } ?: return null
    val gb = b / (1024.0 * 1024 * 1024)
    return if (gb >= 1) "${kotlin.math.round(gb * 100) / 100} GB" else "${kotlin.math.round(b / (1024.0 * 1024)).toLong()} MB"
}

/**
 * 「文件」一栏：视频 / 音轨 / 字幕 / 文件 四行，每一段都来自真实的媒体流，没有的那一段不写、整行没有内容就不出现
 * （docs/data-edge-cases.md §7）。纯函数。
 */
fun mediaRows(source: MediaSourceInfo?): List<MediaRow> {
    if (source == null) return emptyList()
    val streams = source.mediaStreams
    val v = streams.firstOrNull { it.type == "Video" }
    val video = v?.let {
        listOfNotNull(specLine(source)?.substringBefore(" · "), it.codec?.uppercase(), it.videoRange?.trim()?.takeIf { r -> r.isNotEmpty() && r != "SDR" }, fps(it.realFrameRate ?: it.averageFrameRate)).joinToString(" · ")
    }
    fun names(type: String) = streams.filter { it.type == type }.map { s ->
        val row = streamRow(s)
        val tag = listOfNotNull(if (type == "Subtitle") (if (s.isExternal == true) "外挂" else "内封") else null, if (type == "Subtitle") s.codec?.uppercase() else null, "默认".takeIf { s.isDefault == true && type == "Audio" }).joinToString(" ")
        if (tag.isEmpty()) row.name else "${row.name}（$tag）"
    }
    val file = listOfNotNull(source.container?.lowercase()?.takeIf { it.isNotEmpty() }, sizeText(source.size)).joinToString(" · ")
    return listOfNotNull(
        video?.takeIf { it.isNotEmpty() }?.let { MediaRow("视频", it) },
        names("Audio").takeIf { it.isNotEmpty() }?.let { MediaRow("音轨", it.joinToString(" · ")) },
        names("Subtitle").takeIf { it.isNotEmpty() }?.let { MediaRow("字幕", it.joinToString(" · ")) },
        file.takeIf { it.isNotEmpty() }?.let { MediaRow("文件", it) },
    )
}

/** 「2024.06.20」（首播日期，ISO → 年.月.日）；读不懂 → null。 */
fun premiereText(iso: String?): String? {
    val d = iso?.take(10) ?: return null
    return if (Regex("""\d{4}-\d{2}-\d{2}""").matches(d)) d.replace('-', '.') else null
}

/**
 * 单集详情（手机，设计稿 `p=episode`）：这一集自己的剧照整幅铺满（没有分集图 → 剧的剧照，图注写明）→ 栏目行「剧名 · 第 1 季 · 第 5 集」
 * → 宋体的集名 → 时长 / 首播 / 看没看过 → 续看按钮 → 收藏（印章位）/ 标记已看 → 简介（没有就写明「服务器上没有这一集的简介」）
 * → 文件 → 前后两集 → 回到这部剧。
 */
@Composable
fun EpisodeScreen(episode: Title, day: Int) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val dimens = Guga.dimens
    val type = Guga.type
    val colors = Guga.colors
    val hour = LocalHour.current
    val scroll = rememberPresetScroll()
    val voice = rememberNoteVoice()
    val poster = rememberImage(episode.art.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }
    val fav = chrome.isFavorite(episode)
    // 看到一半的不算「已看」
    val played = chrome.isPlayed(episode.id, episode.played && episode.progress <= 0f)
    val near = rememberLoad("episode-near", episode.id) { loadNeighbours(it, episode) }
    val solidAt = with(LocalDensity.current) { (EPISODE_BLEED - dimens.statusBar - 50.dp - 70.dp).toPx() }
    // 整幅图：这一集自己的剧照优先
    val art = episode.art.copy(backdrop = episode.art.still ?: episode.art.backdrop)
    val where = listOfNotNull(episode.seriesName, episode.seasonNo?.let { "第 $it 季" })
    val no = episode.episodeNo

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("episode-phone")) {
            FullBleed(
                art,
                EPISODE_BLEED,
                framing = Framing(0.5f, 0.3f),
                caption = when {
                    episode.art.still != null -> (listOf("本集剧照") + where).joinToString(" · ")
                    bleedSourceOf(art) == BleedSource.Backdrop -> "没有分集图 · 用剧照代替"
                    bleedSourceOf(art) == BleedSource.PosterBlur -> "没有剧照 · 用海报虚化代替"
                    else -> null
                },
                scrollPx = scroll.value.toFloat(),
            ) {
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = dimens.margin).padding(bottom = 4.dp).fillMaxWidth()) {
                    Kicker(where + listOfNotNull(no?.let { "第 $it 集" }))
                    val name = episode.name.ifEmpty { no?.let { "第 $it 集" } ?: "单集" }
                    // 集名是宋体字（不是剧的 logo）；长了降一号、允许折两行，不裁字
                    val size = if (name.length <= 9) type.title else if (name.length <= 14) type.title * 0.82f else type.title * 0.68f
                    BasicText(name, Modifier.padding(top = 8.dp), style = Guga.song(size, FontWeight.Black, lineHeight = size * 1.28f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(Modifier.padding(horizontal = dimens.margin)) {
                Rule(Modifier.padding(top = 12.dp), strong = true)
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("episode-facts"), verticalAlignment = Alignment.CenterVertically) {
                    val facts = listOfNotNull(
                        episode.runtimeSec?.let { "时长" to Rules.minutes(it) },
                        premiereText(episode.premiere)?.let { "首播" to it },
                        (if (played) "已看过" else if (episode.progress > 0f) "看到 ${episode.pct ?: 0}%" else "还没看").let { "" to it },
                    )
                    facts.forEachIndexed { i, (label, value) ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        if (label.isNotEmpty()) BasicText("$label  ", style = Guga.song(type.caption, color = colors.dim), maxLines = 1)
                        BasicText(value, style = Guga.song(type.caption, FontWeight.Bold), maxLines = 1)
                    }
                }
                Rule()
                PrimaryButton(
                    (if (episode.progress > 0f) "续看" else "播放") + (no?.let { " 第 $it 集" }.orEmpty()),
                    { graph.play(episode.id, episode.positionSec?.times(1000)) },
                    Modifier.padding(top = 18.dp).fillMaxWidth(),
                    progress = episode.progress,
                    trailing = if (episode.progress > 0f && episode.positionSec != null && episode.runtimeSec != null) "${Rules.clock(episode.positionSec * 1000)} / ${Rules.clock(episode.runtimeSec * 1000)}" else episode.runtimeSec?.let { Rules.clock(it * 1000) },
                )
                ActionsRow(
                    fav,
                    {
                        graph.toggleFavorite(chrome, episode.id, fav)
                        voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = episode.id.hashCode() + day))
                    },
                    "标记已看",
                    played,
                    { graph.togglePlayed(chrome, episode.id, played) },
                    Modifier.padding(top = 14.dp),
                )
                if (episode.overview.isEmpty()) {
                    BasicText("服务器上没有这一集的简介。", Modifier.padding(top = 22.dp).testTag("episode-no-overview"), style = Guga.note(type.lead * 0.94f, colors.faint), maxLines = 1)
                } else {
                    Overview(Rules.fold(episode.overview, PHONE_FOLD_CHARS), Modifier.padding(top = 18.dp))
                }

                // ---- 文件：来自真实的媒体流，没有就整栏不出现
                val source = episode.sources.firstOrNull()
                val rows = mediaRows(source)
                if (rows.isNotEmpty()) {
                    SectionHeader("文件", "MEDIA", Modifier.padding(top = 30.dp), trailing = "${episode.sources.size} 个版本")
                    for (r in rows) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(vertical = 8.dp).testTag("media-row"), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(r.label, Modifier.width(64.dp), style = Guga.song(type.caption, color = colors.spot, letterSpacing = 0.3.em), maxLines = 1)
                            BasicText(r.text, Modifier.weight(1f), style = Guga.song(type.row * 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Rule()
                    }
                }

                // ---- 前后两集 + 回到这部剧
                val n = near.value
                if (n != null && (n.previous != null || n.next != null)) {
                    SectionHeader("前后", "NEXT TO IT", Modifier.padding(top = 30.dp))
                    n.previous?.let { NeighbourRow(it, "上一集") { graph.nav.replace(com.gugugaga.app.nav.Route.Item(it.id)) } }
                    n.next?.let { NeighbourRow(it, "下一集") { graph.nav.replace(com.gugugaga.app.nav.Route.Item(it.id)) } }
                }
                episode.seriesId?.let { sid ->
                    val press = rememberPressState()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = "回到《${episode.seriesName.orEmpty()}》", onClick = { graph.open(sid) }).padding(vertical = 12.dp).testTag("episode-series"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TitleLogo(episode.seriesName.orEmpty(), episode.art.logo, 104.dp, 54.dp, textOnly = preset.textTitles, baseSize = type.row)
                        Column(Modifier.weight(1f).padding(start = 18.dp)) {
                            BasicText("回到《${episode.seriesName.orEmpty()}》", style = Guga.song(type.row, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            episode.seasonNo?.let { BasicText("第 $it 季", Modifier.padding(top = 4.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1) }
                        }
                        Icon(GuguIcon.ChevronRight, colors.dim, 14.dp)
                    }
                    Rule()
                }
                PageEnd("本集")
            }
            Spacer(Modifier.height(72.dp))
        }
        BackRow(
            no?.let { "第 $it 集" } ?: episode.name,
            solid = scroll.value > solidAt,
            onBack = { graph.nav.backOrHome() },
            favorite = fav,
            onFavorite = { graph.toggleFavorite(chrome, episode.id, fav) },
        )
    }
}

@Composable
private fun NeighbourRow(e: Title, label: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    val no = e.episodeNo ?: 0
    Row(
        Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = "$label ${e.name}", onClick = onClick).padding(vertical = 14.dp).testTag("episode-neighbour"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(no.toString(), Modifier.width(52.dp), style = Guga.numeral(com.gugaart.ui.components.episodeNoSize(no), colors.spot), maxLines = 1, softWrap = false)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(e.name, style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(listOfNotNull(label, e.runtimeSec?.let { Rules.minutes(it) }).joinToString(" · "), Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1)
        }
        Thumb(e.art.still, Rules.episodeNo(no), 104.dp)
    }
    Rule()
}
