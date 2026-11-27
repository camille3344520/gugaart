package com.gugaart.ui.player

import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.DanmakuMatchInput
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.getReleaseYear

/**
 * 这一季在 Emby 的事实，给季守卫认定「第 N 季」用（`DanmakuSeasonGuard`）：
 * 首播年（条目的 startDate 年份要和它对）、集数（差太多降分）、这部剧最大的季号（「Final Stage / 最终季」= 最后一季）、
 * 季名（手动搜索默认填「剧名 季名」；只是「第 N 季」时填「剧名 第N季」）。
 * 取不到 = 各字段 null，守卫退回只看名字的老路子。
 */
data class SeasonFacts(
    val year: Int? = null,
    val episodes: Int? = null,
    val lastSeason: Int? = null,
    val name: String? = null,
)

/**
 * 从这部剧的季列表（`/Shows/<id>/Seasons`）算出第 [season] 季的事实。[episodeYear] = 这一集自己的年份（季上没有首播年时用）。
 * 纯函数，有单测。
 */
fun seasonFactsOf(seasons: List<BaseItemDto>, season: Int, episodeYear: Int?): SeasonFacts {
    val mine = seasons.firstOrNull { it.indexNumber == season }
    return SeasonFacts(
        // 季的首播年优先；季上没有就用这一集自己的年份（同一季里集的年份就是季的首播年）
        year = mine?.let { getReleaseYear(it) } ?: episodeYear,
        episodes = mine?.childCount,
        lastSeason = seasons.mapNotNull { it.indexNumber }.filter { it > 0 }.maxOrNull(),
        name = mine?.name?.trim()?.takeIf { it.isNotEmpty() },
    )
}

/**
 * 一部剧取一次季列表（`/Shows/<id>/Seasons` 一次拿全：季号、首播日、集数），后面几集、别的季都从缓存里按季号算——只读查询，不写服务器。
 * 取不到（没登录、服务器不认、超时）= null，不拦播放。
 *
 * 2026-11-25（tvfreeze）：以前缓存的是**第一次问到的那一季**的事实、键只有剧的 id——同一次运行里先看第 1 季、再看第 3 / 6 季，
 * 后面几季拿到的都是第 1 季的首播年和集数（《头文字D》第 3 季拿到 1998 / 26 集），季守卫就按错的年份把对的条目全筛掉。
 * 现在缓存季列表本身（键 = 服务器地址 + 剧 id：1.5.0 起弹幕可能问的是别台），每一季各算各的。
 */
object DanmakuSeasonFacts {
    private val cache = HashMap<String, List<BaseItemDto>?>()

    suspend fun of(client: EmbyClient, input: DanmakuMatchInput): SeasonFacts? = of(client.address.baseUrl, input) {
        client.getSeasons(
            it,
            o = ItemsRequestOptions(fields = listOf("PremiereDate", "ChildCount", "IndexNumber"), enableImages = false, enableUserData = false),
        ).items
    }

    /** 同上，取季列表的那一步换成 [fetch]（参数 = 剧的 id）。单测用。 */
    internal suspend fun of(server: String, input: DanmakuMatchInput, fetch: suspend (String) -> List<BaseItemDto>): SeasonFacts? {
        val seriesId = input.seriesId ?: return null
        val season = input.seasonNumber ?: return null
        val key = "$server|$seriesId"
        val seasons = if (cache.containsKey(key)) {
            cache[key]
        } else {
            // 被取消（换集、退出）不记：下次再问。取不到才记 null，这一次运行里不再问
            val got = try {
                fetch(seriesId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            got.also { cache[key] = it }
        }
        return seasons?.let { seasonFactsOf(it, season, input.year) }
    }

    /** 测试用。 */
    fun reset() {
        cache.clear()
    }
}
