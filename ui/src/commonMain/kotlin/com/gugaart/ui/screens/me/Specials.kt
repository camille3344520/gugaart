package com.gugaart.ui.screens.me

import androidx.compose.runtime.Immutable
import com.gugaart.ui.components.civilFromEpochDay
import com.gugaart.ui.data.GugaPrefs
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/*
 * 特别称号（用户 2026-11-15：「设置一些特殊称号，在特定情况下触发获得」→ 方案「可以啊都按你的来」）。
 *
 * 四十八个（第一批十二个；「特别称号再多一些」加到二十四；「称号太少了，再搞多一些，48个吧」加到四十八），和十级的等级称号分开：等级看「看过多少部」，特别称号看「怎么看的」——凌晨看、一天连着看、二刷、跨年……
 * 全部从服务器的「已看」记录里算（只读）：每一条的最后播放时间、入库时间、题材、年份、评分、播放次数；剧集的题材 / 年份 / 评分用整部剧的。
 * 所以换设备也算数，以前看过的也算（装上后第一次检查会一次报出已经拿到的）。
 * 服务器只记每一条「最后一次」播放的时间，按时间算的几个（夜猫子、一口气、连场王、周末全勤、跨年放映）是按这个时间判断的。
 */

/** 一条看完的记录：电影或单集。[unit] = 算「部」的键（电影 = 自己，单集 = 季）。时间是 UTC 毫秒。 */
@Immutable
data class PlayedRecord(
    val id: String,
    val movie: Boolean,
    val unit: String,
    val seriesId: String? = null,
    val lastPlayedMs: Long? = null,
    val addedMs: Long? = null,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val rating: Double? = null,
    val playCount: Int = 1,
    /** 片长（分钟）；没有 = null。 */
    val runtimeMin: Int? = null,
)

/**
 * 判断特别称号要的全部事实：看完的记录、看完的长剧（100 集以上）有几部、整季看完的有几季、收藏了几部、本地时区（UTC 毫秒 → 本地偏移）。
 */
class WatchFacts(
    val records: List<PlayedRecord>,
    val longSeriesDone: Int = 0,
    val utcOffsetMs: (Long) -> Long = { 0L },
    val seasonsDone: Int = 0,
    val favorites: Int = 0,
) {
    /** 本地的「第几天」（1970-01-01 起）。 */
    fun localDay(utcMs: Long): Long = (utcMs + utcOffsetMs(utcMs)).floorDiv(DAY_MS)

    /** 本地的几点（0–23）。 */
    fun localHour(utcMs: Long): Int = ((utcMs + utcOffsetMs(utcMs)).mod(DAY_MS) / HOUR_MS).toInt()

    /** 满足 [pred] 的有几部（电影一部，剧集一季）。 */
    fun units(pred: (PlayedRecord) -> Boolean): Int = records.filter(pred).mapTo(HashSet()) { it.unit }.size

    /** 看完的电影 + 剧集一季算一部（和等级同一个数）。 */
    val total: Int get() = records.mapTo(HashSet()) { it.unit }.size

    /** 本地的（月, 日）。 */
    fun monthDay(utcMs: Long): Pair<Int, Int> = civilFromEpochDay(localDay(utcMs)).let { (_, m, d) -> m to d }

    /** 本地的年。 */
    fun localYear(utcMs: Long): Int = civilFromEpochDay(localDay(utcMs)).first

    /** 题材里含 [keys] 之一（中文 / 英文刮削都认，不分大小写）的有几部。 */
    fun genreUnits(keys: List<String>): Int = units { r -> r.genres.any { g -> keys.any { g.contains(it, ignoreCase = true) } } }
}

private const val HOUR_MS = 3_600_000L
private const val DAY_MS = 86_400_000L

/** 恐怖片的题材写法。 */
private val HORROR = listOf("恐怖", "Horror")

/** 题材里算「动画」的几种写法（中文刮削 / 英文刮削）。 */
private val ANIME = setOf("动画", "动漫", "Animation", "Anime")

/** 一个特别称号：id（存在本机）、名字、怎么拿（纸片上写的）、拿到时的一句话、判断。 */
class SpecialTitle(val id: String, val title: String, val how: String, val line: String, val check: (WatchFacts) -> Boolean)

object Specials {
    val ALL: List<SpecialTitle> = listOf(
        SpecialTitle("night", "夜猫子", "凌晨 0–5 点看完一部", "夜深了，屏幕还亮着。") { f ->
            f.records.any { r -> r.lastPlayedMs?.let { f.localHour(it) in 0..4 } == true }
        },
        SpecialTitle("binge", "一口气", "一天里看完同一部剧的 6 集", "说好看一集的。") { f ->
            f.records.filter { !it.movie && it.seriesId != null && it.lastPlayedMs != null }
                .groupingBy { it.seriesId to f.localDay(it.lastPlayedMs!!) }.eachCount().values.any { it >= 6 }
        },
        SpecialTitle("triple", "连场王", "一天看完 3 部电影", "一天三场，比影院排得还满。") { f ->
            f.records.filter { it.movie && it.lastPlayedMs != null }.groupingBy { f.localDay(it.lastPlayedMs!!) }.eachCount().values.any { it >= 3 }
        },
        SpecialTitle("rewatch", "二刷", "同一部电影看了两遍", "好片子，值得再看一遍。") { f ->
            f.records.any { it.movie && it.playCount >= 2 }
        },
        SpecialTitle("fresh", "尝鲜客", "入库 24 小时内就看完", "刚上架就被你看完了。") { f ->
            f.records.any { r -> val p = r.lastPlayedMs; val a = r.addedMs; p != null && a != null && p >= a && p - a <= DAY_MS }
        },
        SpecialTitle("classic", "怀旧派", "看过 10 部 1990 年以前的片子", "老片子，越看越有味道。") { f ->
            f.units { (it.year ?: 9999) < 1990 } >= 10
        },
        SpecialTitle("anime", "动画迷", "看过 30 部动画（剧集一季算一部）", "二次元的门，你推开了。") { f ->
            f.units { r -> r.genres.any { it in ANIME } } >= 30
        },
        SpecialTitle("marathon", "长跑选手", "看完一部 100 集以上的剧", "一百多集，一集不落。") { f ->
            f.longSeriesDone >= 1
        },
        SpecialTitle("highscore", "高分猎手", "看过 20 部 8.5 分以上的片子", "好片子都逃不过你的眼睛。") { f ->
            f.units { (it.rating ?: 0.0) >= 8.5 } >= 20
        },
        SpecialTitle("omnivore", "题材通吃", "看过 10 种不同题材", "什么类型都来得。") { f ->
            f.records.flatMapTo(HashSet()) { it.genres }.size >= 10
        },
        SpecialTitle("weekends", "周末全勤", "连着 4 个周末都看了片", "周末不出门，在家看片。") { f ->
            consecutiveWeekends(f) >= 4
        },
        SpecialTitle("newyear", "跨年放映", "跨年夜 23:00 到 1:00 在看片", "新年第一部，和你一起看。") { f ->
            f.records.any { r -> r.lastPlayedMs?.let { isNewYearsEve(f, it) } == true }
        },
        // ---- 第二批（用户 2026-11-15：「特别称号再多一些」）
        SpecialTitle("morning", "早场观众", "早上 6–9 点看完一部", "一大早就进场了。") { f ->
            f.records.any { r -> r.lastPlayedMs?.let { f.localHour(it) in 6..8 } == true }
        },
        SpecialTitle("daily", "天天见", "连着 7 天都看了片", "一天都没落下。") { f ->
            consecutiveDays(f) >= 7
        },
        SpecialTitle("seasondone", "完结撒花", "看完一整季", "一季看完，撒花。") { f ->
            f.seasonsDone >= 1
        },
        SpecialTitle("seasons10", "追剧王", "看完 10 季", "十季了，剧荒找你就对了。") { f ->
            f.seasonsDone >= 10
        },
        SpecialTitle("epic", "史诗观众", "看完一部 3 小时以上的电影", "三个钟头，一口气坐完。") { f ->
            f.records.any { it.movie && (it.runtimeMin ?: 0) >= 180 }
        },
        SpecialTitle("brave", "烂片勇士", "看完一部 5 分以下的片子", "这么难看也看完了，佩服。") { f ->
            f.records.any { r -> r.rating?.let { it > 0.0 && it < 5.0 } == true }
        },
        SpecialTitle("horror", "胆大包天", "看过 10 部恐怖片", "关灯看的吧？") { f ->
            f.genreUnits(listOf("恐怖", "Horror")) >= 10
        },
        SpecialTitle("comedy", "笑口常开", "看过 20 部喜剧", "笑一笑，片单也年轻。") { f ->
            f.genreUnits(listOf("喜剧", "Comedy")) >= 20
        },
        SpecialTitle("docu", "求知者", "看过 10 部纪录片", "看片也长知识。") { f ->
            f.genreUnits(listOf("纪录", "Documentary")) >= 10
        },
        SpecialTitle("scifi", "科幻迷", "看过 15 部科幻片", "星辰大海，你都去过。") { f ->
            f.genreUnits(listOf("科幻", "Science Fiction", "Sci-Fi")) >= 15
        },
        SpecialTitle("detective", "神探", "看过 15 部悬疑 / 犯罪片", "凶手是谁，你早就猜到了。") { f ->
            f.genreUnits(listOf("悬疑", "犯罪", "Mystery", "Crime")) >= 15
        },
        SpecialTitle("collector", "收藏家", "收藏了 50 部", "好片子都收进来了。") { f ->
            f.favorites >= 50
        },
        // ---- 第三批（用户 2026-11-15：「称号太少了，再搞多一些，48个吧」）
        SpecialTitle("noon", "午休一集", "中午 12–14 点看完一部", "午饭配一集，刚刚好。") { f ->
            f.records.any { r -> r.lastPlayedMs?.let { f.localHour(it) in 12..13 } == true }
        },
        SpecialTitle("valentine", "情人节", "2 月 14 日看片", "今天的片子，适合两个人看。") { f ->
            f.records.any { r -> r.lastPlayedMs?.let { f.monthDay(it) == 2 to 14 } == true }
        },
        SpecialTitle("christmas", "圣诞放映", "12 月 24、25 日看片", "圣诞快乐，片子也是礼物。") { f ->
            f.records.any { r -> r.lastPlayedMs?.let { f.monthDay(it).let { (m, d) -> m == 12 && d in 24..25 } } == true }
        },
        SpecialTitle("goldenweek", "黄金周", "国庆假期（10 月 1–7 日）看完 5 部", "假期都交给了片单。") { f ->
            f.records.filter { r -> r.lastPlayedMs?.let { f.monthDay(it).let { (m, d) -> m == 10 && d in 1..7 } } == true }
                .groupBy { f.localYear(it.lastPlayedMs!!) }.values.any { list -> list.mapTo(HashSet()) { it.unit }.size >= 5 }
        },
        SpecialTitle("halloween", "万圣夜", "10 月 31 日看完一部恐怖片", "不给糖就放片。") { f ->
            f.records.any { r -> r.lastPlayedMs?.let { f.monthDay(it) == 10 to 31 } == true && r.genres.any { g -> HORROR.any { g.contains(it, ignoreCase = true) } } }
        },
        SpecialTitle("yearly100", "年度百部", "一年里看完 100 部", "这一年，一百部。") { f ->
            f.records.filter { it.lastPlayedMs != null }.groupBy { f.localYear(it.lastPlayedMs!!) }.values.any { list -> list.mapTo(HashSet()) { it.unit }.size >= 100 }
        },
        SpecialTitle("streak30", "雷打不动", "连着 30 天都看了片", "一个月，一天没断。") { f ->
            consecutiveDays(f) >= 30
        },
        SpecialTitle("allnight", "通宵追剧", "一天看完 10 集", "天亮了，再看一集就睡。") { f ->
            f.records.filter { !it.movie && it.lastPlayedMs != null }.groupingBy { f.localDay(it.lastPlayedMs!!) }.eachCount().values.any { it >= 10 }
        },
        SpecialTitle("festival", "电影节", "一天看完 5 部电影", "一个人的电影节。") { f ->
            f.records.filter { it.movie && it.lastPlayedMs != null }.groupingBy { f.localDay(it.lastPlayedMs!!) }.eachCount().values.any { it >= 5 }
        },
        SpecialTitle("homebody", "宅一整天", "一天看了 6 个小时以上", "沙发已经有你的形状了。") { f ->
            f.records.filter { it.lastPlayedMs != null && it.runtimeMin != null }.groupBy { f.localDay(it.lastPlayedMs!!) }.values.any { list -> list.sumOf { it.runtimeMin ?: 0 } >= 360 }
        },
        SpecialTitle("romance", "恋爱脑", "看过 15 部爱情片", "糖都嗑到了。") { f -> f.genreUnits(listOf("爱情", "Romance")) >= 15 },
        SpecialTitle("action", "动作派", "看过 20 部动作片", "拳拳到肉，看得过瘾。") { f -> f.genreUnits(listOf("动作", "Action")) >= 20 },
        SpecialTitle("war", "铁血", "看过 10 部战争片", "硝烟里的故事，你都看过。") { f -> f.genreUnits(listOf("战争", "War")) >= 10 },
        SpecialTitle("history", "历史迷", "看过 10 部历史片", "古往今来，一部一部看过来。") { f -> f.genreUnits(listOf("历史", "History")) >= 10 },
        SpecialTitle("family", "合家欢", "看过 10 部家庭片", "一家人一起看的片子。") { f -> f.genreUnits(listOf("家庭", "Family")) >= 10 },
        SpecialTitle("music", "音乐迷", "看过 5 部音乐 / 歌舞片", "片尾曲也要听完。") { f -> f.genreUnits(listOf("音乐", "歌舞", "Music")) >= 5 },
        SpecialTitle("thriller", "心跳加速", "看过 10 部惊悚片", "手心出汗了吧？") { f -> f.genreUnits(listOf("惊悚", "Thriller")) >= 10 },
        SpecialTitle("fantasy", "奇幻迷", "看过 10 部奇幻片", "魔法世界，你常去。") { f -> f.genreUnits(listOf("奇幻", "Fantasy")) >= 10 },
        SpecialTitle("variety", "综艺咖", "看过 10 季综艺 / 真人秀", "笑点都让你承包了。") { f -> f.genreUnits(listOf("综艺", "真人秀", "脱口秀", "Reality", "Talk")) >= 10 },
        SpecialTitle("oldies", "老古董", "看过 5 部 1970 年以前的片子", "黑白片也看得津津有味。") { f -> f.units { (it.year ?: 9999) < 1970 } >= 5 },
        SpecialTitle("masterpiece", "神作巡礼", "看过 5 部 9 分以上的片子", "神作一部一部打卡。") { f -> f.units { (it.rating ?: 0.0) >= 9.0 } >= 5 },
        SpecialTitle("obscure", "冷门猎人", "看过 10 部没有评分的片子", "没人打过分，你先看了。") { f -> f.units { it.movie && (it.rating ?: 0.0) <= 0.0 } >= 10 },
        SpecialTitle("rewatch5", "倒背如流", "同一部看了 5 遍", "台词都能背出来了。") { f -> f.records.any { it.playCount >= 5 } },
        SpecialTitle("dusty", "压箱底", "入库一年后才看完", "放了一年，终于轮到它了。") { f ->
            f.records.any { r -> val p = r.lastPlayedMs; val a = r.addedMs; p != null && a != null && p - a >= 365L * DAY_MS }
        },
    )

    val COUNT: Int get() = ALL.size

    fun of(id: String): SpecialTitle? = ALL.firstOrNull { it.id == id }

    /** 拿到了哪几个（按表里的顺序）。纯函数，有单测。 */
    fun unlocked(facts: WatchFacts): List<String> = ALL.filter { it.check(facts) }.map { it.id }
}

/** 连着几天都看了片（本地日期）。 */
internal fun consecutiveDays(f: WatchFacts): Int {
    val days = f.records.mapNotNull { it.lastPlayedMs }.map { f.localDay(it) }.toSortedSet()
    var best = 0
    var run = 0
    var prev: Long? = null
    for (d in days) {
        run = if (prev != null && d - prev == 1L) run + 1 else 1
        best = maxOf(best, run)
        prev = d
    }
    return best
}

/** 连着几个周末都看了片（周六或周日看过一部就算这个周末）。 */
internal fun consecutiveWeekends(f: WatchFacts): Int {
    val saturdays = f.records.mapNotNull { it.lastPlayedMs }.mapNotNull { t ->
        val day = f.localDay(t)
        // 1970-01-01 是星期四：(day + 3) mod 7 → 0 = 周一 … 5 = 周六，6 = 周日
        when ((day + 3).mod(7L).toInt()) {
            5 -> day
            6 -> day - 1
            else -> null
        }
    }.toSortedSet()
    var best = 0
    var run = 0
    var prev: Long? = null
    for (s in saturdays) {
        run = if (prev != null && s - prev == 7L) run + 1 else 1
        best = maxOf(best, run)
        prev = s
    }
    return best
}

/** 本地时间在 12 月 31 日 23 点到 1 月 1 日 1 点之间。 */
internal fun isNewYearsEve(f: WatchFacts, utcMs: Long): Boolean {
    val (_, m, d) = civilFromEpochDay(f.localDay(utcMs))
    val h = f.localHour(utcMs)
    return (m == 12 && d == 31 && h >= 23) || (m == 1 && d == 1 && h < 1)
}

/** 公历日期 → 1970-01-01 起的天数（Howard Hinnant 的 days_from_civil）。 */
internal fun epochDayOf(y: Int, m: Int, d: Int): Long {
    val yy = (if (m <= 2) y - 1 else y).toLong()
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

/** Emby 的时间（「2026-11-14T13:05:22.0000000Z」，UTC）→ 毫秒；读不懂 = null。纯函数，有单测。 */
fun embyTimeMs(s: String?): Long? {
    if (s == null || s.length < 19) return null
    return try {
        val y = s.substring(0, 4).toInt()
        val mo = s.substring(5, 7).toInt()
        val d = s.substring(8, 10).toInt()
        val h = s.substring(11, 13).toInt()
        val mi = s.substring(14, 16).toInt()
        val se = s.substring(17, 19).toInt()
        if (mo !in 1..12 || d !in 1..31 || h !in 0..23) return null
        // 带了 +08:00 这类偏移的（少见）：换回 UTC
        val tz = Regex("([+-])(\\d{2}):?(\\d{2})$").find(s)?.takeIf { !s.endsWith("Z") }
        val offset = tz?.let { (if (it.groupValues[1] == "-") -1 else 1) * (it.groupValues[2].toLong() * HOUR_MS + it.groupValues[3].toLong() * 60_000L) } ?: 0L
        epochDayOf(y, mo, d) * DAY_MS + h * HOUR_MS + mi * 60_000L + se * 1000L - offset
    } catch (_: NumberFormatException) {
        null
    }
}

/** 服务器的一条看完的记录 → [PlayedRecord]；[series] = 这一集所属的剧（题材、年份、评分用它的）。 */
fun playedRecordOf(item: BaseItemDto, series: BaseItemDto? = null): PlayedRecord {
    val movie = item.type == "Movie"
    val unit = if (movie) item.id else item.seasonId ?: item.parentId ?: item.seriesId?.let { "$it#${item.parentIndexNumber ?: 1}" } ?: item.id
    return PlayedRecord(
        id = item.id,
        movie = movie,
        unit = unit,
        seriesId = item.seriesId,
        lastPlayedMs = embyTimeMs(item.userData?.lastPlayedDate),
        addedMs = embyTimeMs(item.dateCreated),
        genres = (series?.genres?.takeIf { it.isNotEmpty() } ?: item.genres),
        year = series?.productionYear ?: item.productionYear,
        rating = series?.communityRating ?: item.communityRating,
        playCount = item.userData?.playCount ?: 1,
        runtimeMin = item.runTimeTicks?.let { (it / 600_000_000L).toInt() },
    )
}

/** 一次取剧的信息最多多少个 id（网址不要太长）。 */
private const val IDS_PER_REQUEST = 60

/**
 * 只读：看完的电影和单集（只要算称号用的几个字段，不要图）、这些单集所属的剧（题材 / 年份 / 评分）、看完的剧里 100 集以上的有几部。
 * [utcOffsetMs] = 本地时区。
 */
suspend fun loadWatchFacts(client: EmbyClient, utcOffsetMs: (Long) -> Long): WatchFacts {
    val fields = listOf("DateCreated", "Genres", "ProductionYear", "CommunityRating", "ParentId")
    val opts = ItemsRequestOptions(fields = fields, enableImages = false, enableUserData = true)
    val movies = client.getItems(LibraryQuery(includeItemTypes = listOf("Movie"), recursive = true, status = listOf("IsPlayed"), limit = EPISODE_SCAN), opts).items
    val episodes = client.getItems(LibraryQuery(includeItemTypes = listOf("Episode"), recursive = true, status = listOf("IsPlayed"), limit = EPISODE_SCAN), opts).items
    val seriesIds = episodes.mapNotNull { it.seriesId }.distinct()
    val series = seriesIds.chunked(IDS_PER_REQUEST).flatMap { ids ->
        client.getItemsByIds(ids, ItemsRequestOptions(fields = listOf("Genres", "ProductionYear", "CommunityRating"), enableImages = false, enableUserData = false))
    }.associateBy { it.id }
    val longDone = client.getItems(
        LibraryQuery(includeItemTypes = listOf("Series"), recursive = true, status = listOf("IsPlayed"), limit = 2000),
        ItemsRequestOptions(fields = listOf("RecursiveItemCount"), enableImages = false, enableUserData = false),
    ).items.count { (it.recursiveItemCount ?: 0) >= LONG_SERIES }
    // 整季看完的季数、收藏数：只要总数
    val count = ItemsRequestOptions(fields = listOf("ParentId"), enableImages = false, enableUserData = false)
    val seasonsDone = client.getItems(LibraryQuery(includeItemTypes = listOf("Season"), recursive = true, status = listOf("IsPlayed"), limit = 1), count).totalRecordCount
    val favorites = client.getItems(LibraryQuery(includeItemTypes = listOf("Movie", "Series"), recursive = true, status = listOf("IsFavorite"), limit = 1), count).totalRecordCount
    val records = movies.map { playedRecordOf(it) } + episodes.map { playedRecordOf(it, it.seriesId?.let(series::get)) }
    return WatchFacts(records, longDone, utcOffsetMs, seasonsDone, favorites)
}

/** 「长跑选手」要的集数。 */
const val LONG_SERIES = 100

/** 要出的特别称号纸片：[ids] = 这次新拿到的（或点开看的那一个）；[first] = 装上后第一次检查（一次报出已经拿到的）；[browse] = 自己点开看的；[owned] = 一共拿到几个。 */
@Immutable
data class SpecialNews(val ids: List<String>, val first: Boolean = false, val browse: Boolean = false, val owned: Int = 0)

/** 这一次新拿到的特别称号：[announced] = 这个账号已经报过的（null = 还没检查过）。纯函数，有单测。 */
fun specialNews(announced: Set<String>?, unlocked: List<String>): SpecialNews? {
    val fresh = unlocked.filter { announced == null || it !in announced }
    return if (fresh.isEmpty()) null else SpecialNews(fresh, first = announced == null)
}

/** 每个账号拿到了哪些特别称号，存在本机的 `gugaart.specials.v1`（`{"<服务器 id>/<用户 id>": ["night", …]}`）；只往上加。 */
class SpecialStore(private val local: LocalStore) {
    fun announced(account: String): Set<String>? =
        (GugaPrefs.parseObject(local.get(KEY))?.get(account) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }?.toSet()

    fun mark(account: String, ids: Collection<String>) {
        val old = GugaPrefs.parseObject(local.get(KEY))
        val keep = (announced(account).orEmpty() + ids).let { all -> Specials.ALL.map { it.id }.filter { it in all } + (all - Specials.ALL.map { it.id }.toSet()) }
        local.set(KEY, buildJsonObject {
            old?.forEach { (k, v) -> if (k != account) put(k, v) }
            putJsonArray(account) { keep.forEach { add(JsonPrimitive(it)) } }
        }.toString())
    }

    companion object {
        const val KEY = "gugaart.specials.v1"
    }
}
