package com.gugaart.ui.screens.discover

import androidx.compose.runtime.Immutable
import com.gugaart.ui.components.civilFromEpochDay
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import kotlinx.coroutines.CancellationException
import kotlin.random.Random

/*
 * 发现页的数据（docs/fabricated-content.md §2）。设计稿里的「热播 / 冷门 / 升降 / 本周 N 次播放」需要全站播放统计，Emby 没有
 * （没装统计插件时），**砍掉**；「主编片单」没有编辑后台，也不做。留下的三样都是真实数据、只读查询：
 *   - 新到榜（入库顺序，排在前面）、高分榜（真实评分；评分前 [TOP_POOL] 部里每次随机抽 [CHART_SIZE] 部，用户 2026-11-15：
 *     「新到排在前面，高分排后面，高分要随机一点，不要一直不变」）；
 *   - 年代柱状图（按年份聚合）；
 *   - 随手翻（服务器随机取一部）。
 * 上游的发现页（`app/discover/`）是 TMDB 的推荐流，读取入口是 internal；这里只用 :core `EmbyClient` 的公开方法。
 */

/** 两张榜（按这个顺序排：新到在前）。 */
enum class Chart(val id: String, val label: String, val title: String, val note: String) {
    Fresh("fresh", "新到", "新到榜", "按入库时间"),
    Top("top", "高分", "高分榜", "评分前 $TOP_POOL · 随机");

    companion object {
        fun of(id: String?): Chart = entries.firstOrNull { it.id == id } ?: Fresh
    }
}

/** 一个年代的一格：「1990s」+ 部数。 */
@Immutable
data class Decade(val start: Int, val count: Int) {
    val label: String get() = "${start}s"
}

@Immutable
data class DiscoverData(val top: List<Title>, val fresh: List<Title>, val decades: List<Decade>, val total: Int) {
    fun chart(c: Chart): List<Title> = if (c == Chart.Top) top else fresh
}

/** 榜单取几名。 */
const val CHART_SIZE = 10

/** 高分榜从评分最高的这么多部里抽。 */
const val TOP_POOL = 40

/**
 * 高分榜的骰子。应用里（[live]，GugaartApp 启动时打开）每次取数都从评分前 [TOP_POOL] 部里重新抽；离屏测试 / 截图不抽，
 * 还是按评分的前 [CHART_SIZE] 名（画面定得住）。
 */
object DiscoverDice {
    var live: Boolean = false
    var random: Random = Random.Default
}

/**
 * 高分榜：从 [pool]（按评分从高到低）里随机抽 [n] 部，抽到的仍按原来的先后排（评分高的在前，名次照常编）。
 * [random] = null 或池子不够大 → 不抽，取前 [n]。纯函数，有单测。
 */
fun pickTop(pool: List<Title>, n: Int, random: Random?): List<Title> {
    if (random == null || pool.size <= n) return pool.take(n)
    return pool.indices.shuffled(random).take(n).sorted().map { pool[it] }
}

/** 年代图最多数这么多部（再大的库只看前这么多，图注写明）。 */
const val DECADE_SCAN = 3000

private val TYPES = listOf("Movie", "Series")
private val CARD = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "ProductionYear", "Overview", "ChildCount", "CommunityRating"))

/** 按年份聚合成年代。纯函数：没有年份的不算；按年代从早到晚；空的年代不补。 */
fun decadesOf(years: List<Int?>): List<Decade> =
    years.filterNotNull().filter { it in 1880..2199 }.groupingBy { it / 10 * 10 }.eachCount().entries.sortedBy { it.key }.map { Decade(it.key, it.value) }

/** 榜单行的副行：「剧集 · 2026 · 9.5 分」；新到榜不强调评分时也照写（有就写）。 */
fun chartLine(t: Title): String =
    listOfNotNull(if (t.kind == Kind.Series) "剧集" else "电影", t.year?.toString(), t.rating?.let { "$it 分" }).joinToString(" · ")

/** 第 1 名大图下面的一行：「1999 · 8.8 分 · 全 1,215 集」/「2026 · 10 分 · 2 小时 14 分」。 */
fun heroLine(t: Title): String =
    listOfNotNull(t.year?.toString(), t.rating?.let { "$it 分" }, if (t.kind == Kind.Series) t.count?.let { "全 ${thousands(it)} 集" } else t.runtimeSec?.let { Rules.minutes(it) }).joinToString(" · ")

suspend fun loadDiscover(client: EmbyClient): DiscoverData {
    val top = client.getItems(LibraryQuery(includeItemTypes = TYPES, recursive = true, sortBy = listOf("CommunityRating", "SortName"), sortOrder = "Descending", minCommunityRating = 0.1, limit = TOP_POOL), CARD)
    val fresh = client.getItems(LibraryQuery(includeItemTypes = TYPES, recursive = true, sortBy = listOf("DateCreated"), sortOrder = "Descending", limit = CHART_SIZE), CARD)
    // 年代：只要年份，不要图
    val years = try {
        client.getItems(
            LibraryQuery(includeItemTypes = TYPES, recursive = true, limit = DECADE_SCAN),
            ItemsRequestOptions(fields = listOf("ProductionYear"), enableImages = false, enableUserData = false),
        ).items.map { it.productionYear }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    val pool = top.items.map { client.titleOf(it) }
    return DiscoverData(pickTop(pool, CHART_SIZE, DiscoverDice.random.takeIf { DiscoverDice.live }), fresh.items.map { client.titleOf(it) }, decadesOf(years), top.totalRecordCount)
}

/** 随手翻：服务器随机取一部（`SortBy=Random`）。库是空的 → null。 */
suspend fun loadLucky(client: EmbyClient): Title? =
    client.getItems(LibraryQuery(includeItemTypes = TYPES, recursive = true, sortBy = listOf("Random"), limit = 1), CARD).items.firstOrNull()?.let { client.titleOf(it) }

private val MONTH_DAYS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

/** 这一天是这一年的第几周（ISO 8601：周一起，含周四的那一周算当年）。纯函数；[epochDay] = 本地日历日距 1970-01-01 的天数。 */
fun isoWeek(epochDay: Long): Int {
    // 1970-01-01 是周四：周一 = 0
    val dow = (epochDay + 3).mod(7L)
    val thursday = epochDay - dow + 3
    val (y, m, d) = civilFromEpochDay(thursday)
    val leap = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
    var dayOfYear = d
    for (i in 0 until m - 1) dayOfYear += MONTH_DAYS[i] + if (i == 1 && leap) 1 else 0
    return (dayOfYear - 1) / 7 + 1
}

/** 「1,215」：千位分隔。 */
fun thousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()
