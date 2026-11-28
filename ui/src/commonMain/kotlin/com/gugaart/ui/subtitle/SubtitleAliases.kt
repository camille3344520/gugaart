package com.gugaart.ui.subtitle

import com.gugaart.ui.player.danmakuCore
import com.gugaart.ui.player.normTitle
import com.gugugaga.app.AppGraph
import com.gugugaga.danmaku.match.DanmakuAnime
import com.gugugaga.danmaku.match.t2s
import com.gugugaga.danmaku.settings.parseDanmakuPrefsFromBlob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/*
 * 剧的别名（字幕智能匹配用）：字幕站上的名字常常是另一种语言——《头文字D》的字幕大半叫「Initial D First Stage 03」「Initial D Final Stage - 03」，
 * 第 6 季（Final Stage）几乎只有英文名的。Emby 里只有中文名（没配 TMDB 时也拿不到英文名），片名一个 token 都对不上，这些对的字幕就过不了门槛。
 *
 * 别名从弹幕服务器来：搜剧名，正名就是这部剧的条目（去掉年份、【类型】、季标记以后 = 剧名，「头文字D Second Stage」「头文字D 第五季」都算）
 * 自带的别名（dandan 的「Initial D」「Initial D: Fourth Stage」「頭文字D」）去掉季标记后去重。没配弹幕服务器、搜不到、超时 = 没有别名（照旧只用中文名）。
 * 只读：一次搜索；搜到的按剧名记在本机（[KEY_PREFIX]，非密钥），以后（重启也一样）直接用，不再等弹幕服务器。
 * 2026-11-28 真机：弹幕服务器冷的时候第一次搜要好几秒，6 秒等不到——这一次的字幕就少了英文名那一批。
 */
object SubtitleAliases {
    private val cache = HashMap<String, List<String>>()

    /** 等弹幕服务器最多这么久（和字幕搜索同时进行；结果出来就重排一次）。 */
    const val TIMEOUT_MS = 15_000L

    /** 本机记别名的键前缀（后面接剧名）。 */
    const val KEY_PREFIX = "gugaart.subtitle-aliases.v1:"

    suspend fun of(g: AppGraph, series: String): List<String> {
        val key = series.trim()
        if (key.isEmpty()) return emptyList()
        cache[key]?.let { return it }
        runCatching { g.deps.local.get(KEY_PREFIX + key) }.getOrNull()?.let { saved ->
            val list = saved.split('\n').filter { it.isNotBlank() }
            cache[key] = list
            return list
        }
        val hits = try {
            val m = g.danmaku.matcher(parseDanmakuPrefsFromBlob(g.settings.value).sourcePriority) ?: return emptyList()
            withTimeoutOrNull(TIMEOUT_MS) { m.searchAnime(key) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return emptyList() // 失败不记：下次再问
        val found = aliasesFrom(hits, key)
        cache[key] = found
        // 搜到了才记在本机（搜到条目但没有别名也记：这部剧就是没有别名，不必每次问）
        if (hits.isNotEmpty()) runCatching { g.deps.local.set(KEY_PREFIX + key, found.joinToString("\n")) }
        return found
    }

    /** 季标记以外，「5th stage」这种数字序数 + stage 也去掉。 */
    private val NUM_STAGE = Regex("""(?i)\b[0-9]{1,2}(?:st|nd|rd|th)\s*stage\b""")

    private fun key(s: String): String = normTitle(t2s(s))

    /** 一个名字去掉年份、【类型】、季标记、尾巴的标点：「Initial D: First Stage」→「Initial D」。 */
    fun clean(name: String): String =
        danmakuCore(name).replace(NUM_STAGE, " ").trim().trimEnd(':', '：', '-', '·', ' ').trim()

    /** 搜到的条目 → 这部剧的别名（不含剧名本身、繁简只差字形的也不算）。纯函数，有单测。 */
    fun aliasesFrom(hits: List<DanmakuAnime>, series: String): List<String> {
        val want = key(series)
        if (want.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, String>()
        for (a in hits) {
            val names = listOf(a.title) + a.aliases
            if (names.none { key(clean(it)) == want }) continue
            for (n in names) {
                val c = clean(n)
                val k = key(c)
                if (k.isNotEmpty() && k != want) out.getOrPut(k) { c }
            }
        }
        return out.values.toList()
    }

    /** 测试用。 */
    fun reset() = cache.clear()
}
