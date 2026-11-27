package com.gugaart.ui.screens.detail

import androidx.compose.runtime.Immutable
import com.gugaart.ui.data.EpisodePage
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Season
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.data.getItemShared
import com.gugaart.ui.data.toSeason
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.mascot.MascotLines
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.MediaStream
import kotlinx.coroutines.sync.withLock

/** 一季分集超过这么多就分页。 */
const val EPISODE_PAGE = 50

/** 剧集详情的头部数据：这部剧、它的季、正在看 / 下一集。 */
@Immutable
data class SeriesHead(
    val series: Title,
    val seasons: List<Season>,
    /** 续看的那一集（看到一半的）或下一集（上一集看完了）；一集都没看过 = null。 */
    val upNext: Title?,
)

/**
 * 一页分集。[total] 是这一季真实的总集数（服务器给的）；[firstNumber] 是这一季第一集的集号（绝对集号）；
 * [index] = 这一季每一集的编号和集号（按服务器的顺序，超过一页才取；分页标签和「翻到正在看的那一集」都按它，不按推算）。
 */
@Immutable
data class EpisodeSlice(val episodes: List<Title>, val total: Int, val firstNumber: Int?, val index: List<EpisodeRef>? = null)

/** 一季里的一集：编号 + 集号（没有集号 = null）。 */
@Immutable
data class EpisodeRef(val id: String, val number: Int?)

/** 这一季的集号表，按（剧, 季, 总集数）记几份（总集数变了 = 上了新集，重取）。 */
private val seasonIndexCache = LinkedHashMap<String, List<EpisodeRef>>()
private val seasonIndexLock = kotlinx.coroutines.sync.Mutex()

/** 这一季每一集的编号和集号：一次只读请求，不要图、不要观看记录。失败 = null（标签退回推算）。 */
private suspend fun seasonIndex(client: EmbyClient, seriesId: String, seasonId: String?, total: Int): List<EpisodeRef>? {
    val key = "$seriesId|$seasonId|$total"
    seasonIndexLock.withLock { seasonIndexCache[key] }?.let { return it }
    val list = try {
        client.getEpisodes(seriesId, seasonId = seasonId, isMissing = false, o = ItemsRequestOptions(fields = listOf("IndexNumber"), enableImages = false, enableUserData = false))
            .items.map { EpisodeRef(it.id, it.indexNumber) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        return null
    }
    seasonIndexLock.withLock {
        seasonIndexCache[key] = list
        while (seasonIndexCache.size > 8) seasonIndexCache.remove(seasonIndexCache.keys.first())
    }
    return list
}

/** [itemId] 在这一季的第几页（看集号表）；不知道 = null。纯函数，有单测。 */
fun pageOf(slice: EpisodeSlice?, itemId: String): Int? =
    slice?.index?.indexOfFirst { it.id == itemId }?.takeIf { it >= 0 }?.div(EPISODE_PAGE)

private val EPISODE_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Overview"))

/** 真实的代码路径（全部只读）：`getItem` + `getSeasons` + `getResume` / `getNextUp`。 */
suspend fun loadSeriesHead(client: EmbyClient, id: String): SeriesHead {
    val series = client.titleOf(client.getItemShared(id))
    val seasons = client.getSeasons(id).items.map { it.toSeason() }
    val resume = client.getResume(parentId = id, limit = 1, o = EPISODE_FIELDS).items.firstOrNull()
    val next = resume ?: try {
        client.getNextUp(seriesId = id, limit = 1, o = EPISODE_FIELDS).items.firstOrNull()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    return SeriesHead(series, seasons, next?.let { client.titleOf(it) })
}

/**
 * 一季里的一页分集（`getEpisodes`，分页：一次 [EPISODE_PAGE] 集）。
 * `IsMissing=false`：Emby 按元数据补出来的缺集（`LocationType=Virtual`，没有文件；用户开了「显示季中缺少的剧集」才会列出）不列——
 * 点开放不了，哪个内核都是「Item has no playable media source」（fix/op1137）。和上游详情页、播放器自己的分集表同一个取法。
 */
suspend fun loadEpisodes(client: EmbyClient, seriesId: String, seasonId: String?, page: Int): EpisodeSlice {
    val r = client.getEpisodes(seriesId, seasonId = seasonId, isMissing = false, startIndex = page * EPISODE_PAGE, limit = EPISODE_PAGE, o = EPISODE_FIELDS)
    val list = r.items.map { client.titleOf(it) }
    val first = list.firstOrNull()?.episodeNo?.let { it - page * EPISODE_PAGE }
    val total = maxOf(r.totalRecordCount, page * EPISODE_PAGE + list.size)
    // 超过一页：取这一季的集号表（分页标签按真实集号）
    val index = if (total > EPISODE_PAGE) seasonIndex(client, seriesId, seasonId, total) else null
    return EpisodeSlice(list, total, first, index)
}

/** 集号分页的标签：用**绝对集号**（第 21 季从 892 起 → 「892–941」…）。不超过一页不出现。 */
fun pagesOf(slice: EpisodeSlice?): List<EpisodePage> {
    if (slice == null || slice.total <= EPISODE_PAGE) return emptyList()
    // 有集号表：按真实集号（第 0 集、中间缺集的剧，推算出来的标签会对不上）
    slice.index?.takeIf { it.size == slice.total }?.let { idx -> return Rules.episodePagesOf(idx.map { it.number }, EPISODE_PAGE) }
    val first = slice.firstNumber ?: 1
    return Rules.episodePages(List(slice.total) { first + it }, EPISODE_PAGE)
}

/** 一开始选哪一季：正在看的那一季 → 第一个有季号的季 → 第一个。 */
fun initialSeason(head: SeriesHead, wanted: Int? = null): Season? {
    val seasons = head.seasons
    wanted?.let { w -> seasons.firstOrNull { it.number == w }?.let { return it } }
    head.upNext?.seasonNo?.let { n -> seasons.firstOrNull { it.number == n }?.let { return it } }
    return seasons.firstOrNull { it.number != null && it.number > 0 } ?: seasons.firstOrNull()
}

/** 卷册格子上印什么：有季号 → 斜体数字；没有季号（季名乱码）→ 原样显示季名，不替用户改名。 */
fun seasonLabel(s: Season): Pair<String, Boolean> = if (s.number != null) s.number.toString() to true else s.name to false

/** 区块标题右端：「第 1 季 · 40 集」；没有季号 → 季名原样。 */
fun seasonTally(s: Season?, total: Int?): String? {
    if (s == null) return total?.takeIf { it > 0 }?.let { "$it 集" }
    val name = if (s.number != null) "第 ${s.number} 季" else s.name
    val n = (total ?: s.episodes)?.takeIf { it > 0 }
    return if (n != null) "$name · $n 集" else name
}

/** 分集行的副行：「45 分 · 看到 26:30」「24 分 · 已看」「约 45 分」。没有时长就只写状态。 */
fun episodeSubtitle(e: Title, played: Boolean, fallbackRuntimeSec: Long?): String? {
    val runtime = e.runtimeSec?.let { Rules.minutes(it) } ?: fallbackRuntimeSec?.let { "约 ${Rules.minutes(it)}" }
    val state = when {
        e.progress > 0f && e.positionSec != null -> "看到 ${Rules.clock(e.positionSec * 1000)}"
        played -> "已看"
        else -> null
    }
    return listOfNotNull(runtime, state).joinToString(" · ").takeIf { it.isNotEmpty() }
}

/** 续看按钮上的字和右端的时间：「续看 第 5 集 · 江心白南霁风组队」+「26:30 / 45:01」。 */
fun resumeLabel(up: Title?): Pair<String, String?> {
    if (up == null) return "从第 1 集看起" to null
    val verb = if (up.progress > 0f) "续看" else "播放"
    val no = up.episodeNo?.let { " 第 $it 集" }.orEmpty()
    val text = "$verb$no" + (up.name.takeIf { it.isNotEmpty() && up.kind == Kind.Episode }?.let { " · $it" }.orEmpty())
    val time = if (up.progress > 0f && up.positionSec != null && up.runtimeSec != null) "${Rules.clock(up.positionSec * 1000)} / ${Rules.clock(up.runtimeSec * 1000)}" else up.runtimeSec?.let { Rules.minutes(it) }
    return text to time
}

/** 「主编按」在剧集详情说什么。 */
fun seriesNote(head: SeriesHead, hour: Int, day: Int): String? {
    val s = head.series
    val up = head.upNext
    val seed = s.id.hashCode() + day
    return when {
        up != null && up.progress > 0f -> MascotLines.pick(
            "detail.resume",
            LineFacts(ep = up.episodeNo, time = up.positionSec?.let { Rules.clock(it * 1000) }, remain = up.runtimeSec?.let { Rules.minutes((it * (1f - up.progress)).toLong()) }, pct = up.pct),
            hour,
            seed,
        )
        up != null -> MascotLines.pick("detail.next", LineFacts(ep = up.episodeNo), hour, seed)
        s.count != null && s.unplayed == 0 -> MascotLines.pick("detail.done", LineFacts(n = s.count), hour, seed)
        else -> MascotLines.pick("detail.fresh", LineFacts(n = s.count), hour, seed)
    }
}

fun movieNote(m: Title, hour: Int, day: Int): String? {
    val seed = m.id.hashCode() + day
    val remain = m.runtimeSec?.let { Rules.minutes((it * (1f - m.progress)).toLong()) }
    return if (m.progress > 0f) {
        MascotLines.pick("detail.movie.resume", LineFacts(time = m.positionSec?.let { Rules.clock(it * 1000) }, remain = remain, pct = m.pct), hour, seed)
    } else {
        MascotLines.pick("detail.movie", LineFacts(remain = remain), hour, seed)
    }
}

/* ---- 电影：规格、版本、音轨字幕（docs/data-edge-cases.md §7） ---- */

/** 规格行：「1080p · SDR」「4K · HDR」。没有媒体流信息 → null（规格行不出现）。 */
fun specLine(source: MediaSourceInfo?): String? {
    val v = source?.mediaStreams?.firstOrNull { it.type == "Video" } ?: return null
    val h = v.height ?: return null
    val w = v.width ?: 0
    val res = when {
        w >= 3800 || h >= 2000 -> "4K"
        w >= 1900 || h >= 1000 -> "1080p"
        w >= 1200 || h >= 700 -> "720p"
        else -> "${h}p"
    }
    val range = v.videoRange?.trim()?.takeIf { it.isNotEmpty() } ?: "SDR"
    return "$res · $range"
}

/** 音轨 / 字幕的一行：名字（「国语 · AAC 2.0」）+ 小签（默认 / 外挂 / 强制）。 */
@Immutable
data class StreamRow(val name: String, val note: String)

/** 名字照 [com.gugaart.ui.data.trackName]（P1-2：详情页、播放器同一个函数）；没语言没名字写「未命名」。 */
fun streamRow(s: MediaStream): StreamRow = com.gugaart.ui.data.trackName(s).let { StreamRow(it.text, it.tags.joinToString(" · ")) }

/** 「主演 A   联合主演 B」：前两位演员；导演另算。 */
fun leads(t: Title): List<String> = t.people.filter { it.type == "Actor" || it.type == null }.take(2).map { it.name }

/* ---- 人物 ---- */

/** 人物页的一部作品。[role]：「饰 至尊宝 / 孙悟空」或「导演」。 */
@Immutable
data class Work(val title: Title, val role: String?, val behind: Boolean)

@Immutable
data class PersonData(val person: Title, val works: List<Work>) {
    val years: Pair<Int, Int>? get() = works.mapNotNull { it.title.year }.let { ys -> if (ys.isEmpty()) null else ys.min() to ys.max() }
    val best: String? get() = works.mapNotNull { w -> w.title.rating?.toDoubleOrNull()?.let { it to w.title.rating } }.maxByOrNull { it.first }?.second

    /** 身份行：「演员 / 导演 / 监制」（按作品里出现的类型）。 */
    val roles: String
        get() = works.flatMap { it.kinds }.distinct().sortedBy { if (it == "演员") 0 else 1 }.joinToString(" / ").ifEmpty { "演员" }
}

private val Work.kinds: List<String> get() = listOf(if (behind) (role ?: "幕后") else "演员")

private val PERSON_TYPES = mapOf("Actor" to "演员", "Director" to "导演", "Writer" to "编剧", "Producer" to "监制", "GuestStar" to "客串")

/** 人物和他 / 她在片库里的作品（只读）：`getItem` + `getItems(personIds)`；每部作品带演职员，从里面找出这个人的角色。 */
suspend fun loadPerson(client: EmbyClient, id: String): PersonData {
    val person = client.titleOf(client.getItemShared(id))
    val r = client.getItems(
        LibraryQuery(personIds = listOf(id), includeItemTypes = listOf("Movie", "Series"), recursive = true, sortBy = listOf("ProductionYear", "SortName"), sortOrder = "Descending", limit = 200),
        ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "People", "Overview", "CommunityRating")),
    )
    val works = r.items.map { dto ->
        val t = client.titleOf(dto)
        val me = dto.people.firstOrNull { it.id == id }
        val behind = me?.type != null && me.type != "Actor" && me.type != "GuestStar"
        val role = when {
            behind -> PERSON_TYPES[me?.type] ?: me?.type
            !me?.role.isNullOrBlank() -> "饰 ${me?.role?.trim()}"
            else -> null
        }
        Work(t, role, behind)
    }
    return PersonData(person, works)
}

/** P0-3：整季标已看 / 未看之前的确认框文案（三端同一份）。[note] 只在标为未看时有：后果在确认的那一刻说清楚。 */
@Immutable
data class SeasonMarkCopy(val title: String, val note: String?, val confirm: String)

/** 「把第 1 季标为已看？」/「把第 1 季标为未看？」+「会清掉播放进度」；没有季号的季用季名。纯函数，有单测。 */
fun seasonMarkCopy(season: Season, toPlayed: Boolean): SeasonMarkCopy {
    val name = if (season.number != null && season.number > 0) "第 ${season.number} 季" else season.name
    val verb = if (toPlayed) "标为已看" else "标为未看"
    return SeasonMarkCopy("把${name}$verb？", if (toPlayed) null else "会清掉播放进度", verb)
}

/** 电脑「整部已看 / 整部未看」的确认框（照 P0-3）：「把《片名》全部标为已看？」；标为未看加「会清掉播放进度」。纯函数，有单测。 */
fun wholeMarkCopy(title: String, toPlayed: Boolean): SeasonMarkCopy {
    val verb = if (toPlayed) "标为已看" else "标为未看"
    return SeasonMarkCopy("把《$title》全部$verb？", if (toPlayed) null else "会清掉播放进度", verb)
}
