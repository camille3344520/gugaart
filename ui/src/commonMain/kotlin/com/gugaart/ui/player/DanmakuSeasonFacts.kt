package com.gugaart.ui.player

import com.gugugaga.core.DanmakuMatchInput
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.getReleaseYear

/**
 * 这一季在 Emby 的事实，给季守卫认定「第 N 季」用（`DanmakuSeasonGuard`）：
 * 首播年（条目的 startDate 年份要和它对）、集数（差太多降分）、这部剧最大的季号（「Final Stage / 最终季」= 最后一季）。
 * 取不到 = 各字段 null，守卫退回只看名字的老路子。
 */
data class SeasonFacts(
    val year: Int? = null,
    val episodes: Int? = null,
    val lastSeason: Int? = null,
)

/**
 * 一部剧取一次（`/Shows/<id>/Seasons` 一次拿全：季号、首播日、集数），后面几集直接用缓存——只读查询，不写服务器。
 * 取不到（没登录、服务器不认、超时）= null，不拦播放。
 */
object DanmakuSeasonFacts {
    private val cache = HashMap<String, SeasonFacts?>()

    suspend fun of(client: EmbyClient, input: DanmakuMatchInput): SeasonFacts? {
        val seriesId = input.seriesId ?: return null
        val season = input.seasonNumber ?: return null
        if (cache.containsKey(seriesId)) return cache[seriesId]
        val facts = runCatching {
            val seasons = client.getSeasons(
                seriesId,
                o = ItemsRequestOptions(fields = listOf("PremiereDate", "ChildCount", "IndexNumber"), enableImages = false, enableUserData = false),
            ).items
            val mine = seasons.firstOrNull { it.indexNumber == season }
            SeasonFacts(
                // 季的首播年优先；季上没有就用这一集自己的年份（同一季里集的年份就是季的首播年）
                year = mine?.let { getReleaseYear(it) } ?: input.year,
                episodes = mine?.childCount,
                lastSeason = seasons.mapNotNull { it.indexNumber }.filter { it > 0 }.maxOrNull(),
            )
        }.getOrNull()
        cache[seriesId] = facts
        return facts
    }

    /** 测试用。 */
    fun reset() {
        cache.clear()
    }
}