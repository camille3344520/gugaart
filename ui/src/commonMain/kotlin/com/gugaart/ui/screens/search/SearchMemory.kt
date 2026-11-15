package com.gugaart.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.platform.localUtcOffsetMs
import com.gugugaga.app.search.SearchHistoryStore
import com.gugugaga.app.ui.LocalGraph
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/*
 * 搜索页的「找过」和词云（用户 2026-11-16：「那随便把搜索也做一下，之前demo有词云，和历史搜索记录，你看看实现下」）。
 *
 * 找过：词本身用上游的搜索记录（`gugu.searchHistory`，和网页版同一个键、同一种形状，最新的在前，最多 12 条）；
 * 什么时候搜的另记在 [TIMES_KEY]（{词: 毫秒}），只用来写「今天 / 昨天 / 周二」。只在本机，不上服务器。
 * 词云：设计稿的「大家在找」是编的（没有别人的搜索数据），这里换成**片库里的词**——常见的题材、最近的年份、收藏 / 在看 / 高分的短片名，
 * 每天换一批（[searchCloud]，按日子定种子，不会一进页面就跳）。点一个词就搜它。
 */

/** 找过的一条：词 + 什么时候（没记到时间 = null）。 */
@Immutable
data class RecentSearch(val term: String, val at: Long?)

object SearchMemory {
    const val TIMES_KEY = "gugaart.search.times.v1"
    const val DAY_MS = 86_400_000L

    fun load(local: LocalStore): List<RecentSearch> {
        val times = times(local)
        return SearchHistoryStore(local).load().map { RecentSearch(it, times[it]) }
    }

    fun add(local: LocalStore, term: String, now: Long = nowMs()) {
        val t = term.trim()
        if (t.isEmpty()) return
        val kept = SearchHistoryStore(local).add(t)
        val times = times(local).filterKeys { k -> kept.any { it == k } } + (t to now)
        local.set(TIMES_KEY, JsonObject(times.mapValues { JsonPrimitive(it.value) }).toString())
    }

    fun clear(local: LocalStore) {
        SearchHistoryStore(local).clear()
        local.remove(TIMES_KEY)
    }

    private fun times(local: LocalStore): Map<String, Long> = try {
        (Json.parseToJsonElement(local.get(TIMES_KEY) ?: "{}") as? JsonObject).orEmpty()
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }.toMap()
    } catch (_: Exception) {
        emptyMap()
    }

    /** 「今天」「昨天」「周二」「上周」「11月3日」；不知道 = ""。[offsetMs] = 本地时区。纯函数，有单测。 */
    fun whenLabel(at: Long?, now: Long, offsetMs: Long): String {
        if (at == null || at <= 0L) return ""
        val day = (at + offsetMs).floorDiv(DAY_MS)
        val today = (now + offsetMs).floorDiv(DAY_MS)
        val diff = today - day
        return when {
            diff <= 0L -> "今天"
            diff == 1L -> "昨天"
            diff < 7L -> "周" + "一二三四五六日"[((day + 3).mod(7L)).toInt()] // 1970-01-01 是周四
            diff < 14L -> "上周"
            else -> monthDay(day)
        }
    }

    /** 本地日 → 「11月3日」（公历，civil-from-days）。 */
    private fun monthDay(epochDay: Long): String {
        val z = epochDay + 719_468
        val era = z.floorDiv(146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return "${m}月${d}日"
    }

    @OptIn(ExperimentalTime::class)
    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    fun offsetMs(now: Long = nowMs()): Long = localUtcOffsetMs(now)
}

/** 搜索页用的「找过」：读写本机，改了马上重画。 */
@Stable
class SearchRecents(private val local: LocalStore) {
    var items by mutableStateOf(SearchMemory.load(local))
        private set

    fun add(term: String) {
        SearchMemory.add(local, term)
        items = SearchMemory.load(local)
    }

    fun clear() {
        SearchMemory.clear(local)
        items = emptyList()
    }
}

@Composable
fun rememberSearchRecents(): SearchRecents {
    val g = LocalGraph.current
    return remember(g) { SearchRecents(g.deps.local) }
}

/* ------------------------------------------------------------------ 词云 */

/** 「中文字幕」这类片源标签有的库也放在题材里：不当题材（设计稿也去掉了它）。纯函数。 */
fun isRealGenre(name: String): Boolean = !name.contains("字幕")

enum class CloudSize { S, M, L }

/** 词云里的一个词：字号三档；[accent] = 印专色的那一个（设计稿里只有一个是红的）。 */
@Immutable
data class CloudWord(val text: String, val size: CloudSize, val accent: Boolean = false)

/**
 * 片库里的词：题材（按部数）、年份（最近一个至少有 3 部的年份）、片名（收藏 / 在看 / 高分优先，名字短的：2 – 6 个字宽）。
 * 按 [seed]（一般是今天是第几天）挑，一天之内不变；[skip] 里的（刚搜过的）不再出现。排法学设计稿：大小相间，第四个印专色。纯函数，有单测。
 */
fun searchCloud(titles: List<Title>, seed: Int, skip: Set<String> = emptySet(), max: Int = 11): List<CloudWord> {
    if (titles.isEmpty()) return emptyList()
    val skipLow = skip.map { it.lowercase() }.toSet()
    fun ok(s: String) = s.isNotBlank() && s.lowercase() !in skipLow
    val rnd = kotlin.random.Random(seed)

    val genres = titles.flatMap { it.genres }.filter { ok(it) && isRealGenre(it) }.groupingBy { it }.eachCount()
        .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
    val genrePick = genres.take(8).shuffled(rnd).take(4)

    val years = titles.mapNotNull { it.year }.groupingBy { it }.eachCount()
    val year = years.filter { it.value >= 3 }.keys.maxOrNull()?.toString()?.takeIf(::ok)

    val score = { t: Title ->
        (if (t.favorite) 3 else 0) + (if (t.progress > 0f) 2 else 0) + (if ((t.rating?.toFloatOrNull() ?: 0f) >= 8f) 1 else 0)
    }
    val short = titles.filter { Rules.titleUnits(it.name) in 2..6 && ok(it.name) }.distinctBy { it.name }
    val top = short.sortedWith(compareByDescending<Title> { score(it) }.thenBy { it.name }).take(16).shuffled(rnd)
    val names = (top.take(5) + short.shuffled(rnd)).distinctBy { it.name }.take(6).map { it.name }

    // 大小相间：片名大、题材中、年份小
    val out = ArrayList<CloudWord>()
    val n = names.iterator()
    val g = genrePick.iterator()
    val pattern = listOf('t', 'g', 'y', 't', 'g', 'g', 't', 'g', 't', 't', 't')
    var yearUsed = false
    for (p in pattern) {
        if (out.size >= max) break
        when (p) {
            't' -> if (n.hasNext()) out += CloudWord(n.next(), if (out.size % 3 == 0) CloudSize.L else CloudSize.M)
            'g' -> if (g.hasNext()) out += CloudWord(g.next(), if (out.size % 2 == 0) CloudSize.M else CloudSize.S)
            'y' -> if (year != null && !yearUsed) {
                out += CloudWord(year, CloudSize.S)
                yearUsed = true
            }
        }
    }
    while (out.size < max && n.hasNext()) out += CloudWord(n.next(), CloudSize.S)
    val accentAt = out.indexOfFirst { it.size == CloudSize.L && out.indexOf(it) >= 2 }.takeIf { it >= 0 } ?: out.indices.firstOrNull { it == 3 }
    return out.mapIndexed { i, w -> if (i == accentAt) w.copy(accent = true) else w }
}
