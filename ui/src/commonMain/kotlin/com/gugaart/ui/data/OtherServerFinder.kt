package com.gugaart.ui.data

import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyException
import com.gugugaga.core.EmbyJson
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.PlaybackPrefs
import com.gugugaga.core.QueryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeSource

/*
 * 跨服务器找同一部片的真实现（1.5.0 B2）。只读：只发 GET（条目查询、单集列表、公开的 /System/Info/Public）。
 * 上游 `LibraryQuery` 没有 `AnyProviderIdEquals` → 和 C1 一样用上游公开的 `EmbyClient.getJson` 拼查询，不改上游。
 */

private val PROVIDER_KEYS_MOVIE = listOf("tmdb", "imdb")
private val PROVIDER_KEYS_SERIES = listOf("tmdb", "imdb", "tvdb")

private fun providerValue(ids: Map<String, String?>, key: String): String? =
    ids.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim()?.takeIf { it.isNotEmpty() }

/** `AnyProviderIdEquals` 的值（有哪个写哪个；都没有 = null）。 */
internal fun anyProviderValue(ids: Map<String, String?>, keys: List<String>): String? =
    keys.mapNotNull { k -> providerValue(ids, k)?.let { "$k.$it" } }.joinToString(",").takeIf { it.isNotEmpty() }

/** [keys] 里有没有一个两边都有、而且相同的（不区分大小写）。纯函数。 */
internal fun sharesAnyId(a: Map<String, String?>, b: Map<String, String?>, keys: List<String>): Boolean =
    keys.any { k -> providerValue(a, k)?.lowercase()?.let { it == providerValue(b, k)?.lowercase() } == true }

/** 两边都有、但值不同的 provider id（= 肯定不是同一部）。纯函数。 */
internal fun conflictingIds(a: Map<String, String?>, b: Map<String, String?>): Boolean =
    PROVIDER_KEYS_SERIES.any { k ->
        val x = providerValue(a, k)?.lowercase()
        val y = providerValue(b, k)?.lowercase()
        x != null && y != null && x != y
    }

private fun normTitle(s: String?): String? = s?.trim()?.lowercase()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }

/**
 * 「片名 + 年份」精确匹配（没有 provider id 时的退路，纯函数）：类型相同、年份相同、片名（或原名）完全相同（不分大小写、空白合并）、
 * provider id 不冲突；**只有一个**才算——两个以上拿不准，一个都不列。年份或片名不知道 = 不列。
 */
fun matchByNameYear(candidates: List<BaseItemDto>, type: String, name: String, originalTitle: String?, year: Int?, ids: Map<String, String?>): List<BaseItemDto> {
    if (year == null) return emptyList()
    val names = setOfNotNull(normTitle(name), normTitle(originalTitle))
    if (names.isEmpty()) return emptyList()
    val hits = candidates.filter { c ->
        c.type == type && c.productionYear == year &&
            (normTitle(c.name) in names || normTitle(c.originalTitle) in names) &&
            !conflictingIds(ids, c.providerIds)
    }.distinctBy { it.id }
    return if (hits.size == 1) hits else emptyList()
}

/**
 * 在一台别的服务器上找同一部片（[findOn]）。[pool] 给客户端和 ping；[prefs] 是当前设备的播放偏好（直连 / 转码预估，同 C1）；
 * 每台 [timeoutMs]（默认 3 秒）。
 */
class OtherServerSourceFinder(
    private val pool: OtherServerPool,
    private val prefs: (itemId: String, mediaSourceId: String) -> PlaybackPrefs? = { _, _ -> null },
    private val timeoutMs: Long = OTHER_SEARCH_TIMEOUT_MS,
) {
    suspend fun participants(): List<OtherServer> = pool.participants()

    fun needsLogin(serverId: String): Boolean = pool.health.value[serverId] == OtherServerHealth.NeedsLogin

    /** 一台的结论：找到（源）/ 没有 / 离线 / 超时 / 要重新登录。不抛异常（取消除外）。 */
    suspend fun findOn(server: OtherServer, target: SourceTarget): ServerOutcome {
        val id = server.serverId
        if (needsLogin(id)) return ServerOutcome(ServerSearchState.NeedsLogin)
        val c = pool.client(id) ?: return ServerOutcome(if (needsLogin(id)) ServerSearchState.NeedsLogin else ServerSearchState.Offline)
        return try {
            withTimeout(timeoutMs) {
                coroutineScope {
                    val known = pool.latencyMs(id)
                    val ping = if (known == null) async { pingMs(c) } else null
                    val items = when (target) {
                        is SourceTarget.Movie -> findMovie(c, target)
                        is SourceTarget.Episode -> findEpisode(c, target)
                    }
                    val lat = known ?: ping?.await()
                    if (known == null && lat != null) pool.report(id, lat)
                    val sources = ArrayList<PlaySource>()
                    for (d in items) {
                        val ms = d.mediaSources.ifEmpty { c.getItem(d.id).mediaSources }
                        for (m in ms) sources += PlaySource(id, server.name, d.id, m, SourceKind.OtherServer, lat, estimatePlayable(c, d.id, m, prefs(d.id, m.id)))
                    }
                    if (sources.isEmpty()) ServerOutcome(ServerSearchState.NotFound, latencyMs = lat) else ServerOutcome(ServerSearchState.Found, sources, lat)
                }
            }
        } catch (_: TimeoutCancellationException) {
            ServerOutcome(ServerSearchState.TimedOut, latencyMs = pool.latencyMs(id))
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbyException) {
            when (e.kind) {
                EmbyException.Kind.Unauthorized -> ServerOutcome(ServerSearchState.NeedsLogin)
                EmbyException.Kind.Network -> {
                    pool.report(id, null)
                    ServerOutcome(ServerSearchState.Offline)
                }
                EmbyException.Kind.Timeout -> ServerOutcome(ServerSearchState.TimedOut)
                else -> ServerOutcome(ServerSearchState.NotFound, latencyMs = pool.latencyMs(id))
            }
        } catch (_: Exception) {
            ServerOutcome(ServerSearchState.NotFound, latencyMs = pool.latencyMs(id))
        }
    }

    /** 公开端点的往返毫秒（不带令牌）；失败 = null。 */
    private suspend fun pingMs(c: EmbyClient): Int? {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            c.getPublicInfo()
            mark.elapsedNow().inWholeMilliseconds.toInt().coerceAtLeast(0)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun query(c: EmbyClient, type: String, extra: Map<String, Any?>): List<BaseItemDto> {
        val el = c.getJson(
            "/Users/${c.requireUserId()}/Items",
            mapOf(
                "Recursive" to true,
                "IncludeItemTypes" to type,
                "Fields" to "MediaSources,ProviderIds,ProductionYear,OriginalTitle",
                "EnableImages" to false,
                "EnableUserData" to false,
                "Limit" to 20,
            ) + extra,
        )
        return EmbyJson.decodeFromJsonElement(QueryResult.serializer(BaseItemDto.serializer()), el).items
    }

    /** 电影：provider id 查；没有 / 对方没认出来 → 片名 + 年份。结果再按 provider id 过一遍（服务器不认这个参数时不会把整个片库当结果）。 */
    private suspend fun findMovie(c: EmbyClient, t: SourceTarget.Movie): List<BaseItemDto> {
        val q = anyProviderValue(t.providerIds, PROVIDER_KEYS_MOVIE)
        if (q != null) {
            val hits = query(c, "Movie", mapOf("AnyProviderIdEquals" to q)).filter { it.type == "Movie" && sharesAnyId(t.providerIds, it.providerIds, PROVIDER_KEYS_MOVIE) }
            if (hits.isNotEmpty()) return hits.distinctBy { it.id }
        }
        return byNameYear(c, "Movie", t.name, t.originalTitle, t.year, t.providerIds)
    }

    /** 单集：先找剧（provider id → 片名 + 年份），再按季号 + 集号找那一集。 */
    private suspend fun findEpisode(c: EmbyClient, t: SourceTarget.Episode): List<BaseItemDto> {
        val q = anyProviderValue(t.seriesProviderIds, PROVIDER_KEYS_SERIES)
        var series = if (q != null) {
            query(c, "Series", mapOf("AnyProviderIdEquals" to q)).filter { it.type == "Series" && sharesAnyId(t.seriesProviderIds, it.providerIds, PROVIDER_KEYS_SERIES) }.distinctBy { it.id }
        } else {
            emptyList()
        }
        if (series.isEmpty()) series = byNameYear(c, "Series", t.seriesName, null, t.seriesYear, t.seriesProviderIds)
        val out = ArrayList<BaseItemDto>()
        for (s in series) {
            val eps = c.getEpisodes(
                s.id,
                season = t.season,
                o = ItemsRequestOptions(fields = listOf("MediaSources", "ProviderIds"), enableImages = false, enableUserData = false),
            ).items
            out += eps.filter { it.parentIndexNumber == t.season && it.indexNumber == t.episode }
        }
        return out.distinctBy { it.id }
    }

    private suspend fun byNameYear(c: EmbyClient, type: String, name: String, originalTitle: String?, year: Int?, ids: Map<String, String?>): List<BaseItemDto> {
        if (year == null || name.isBlank()) return emptyList()
        val found = query(c, type, mapOf("SearchTerm" to name.trim(), "Years" to year))
        return matchByNameYear(found, type, name, originalTitle, year, ids)
    }
}

/**
 * 详情页 / 播放器面板用的仓库（1.5.0 B2）：本台的（[local]，C1 的 [EmbySourceRepository]）+ 别台的（[finder]）。
 * 没有别台参与（开关关了 / 只存了一台）= 和 [local] 一模一样。
 * 剧集单集 / 缺年份的电影要的「剧的 provider id、季号集号、年份」从当前服务器（[localClient]）补取，取不到就只有本台的。
 */
class CrossServerSourceRepository(
    private val local: SourceRepository,
    private val localClient: EmbyClient?,
    private val finder: OtherServerSourceFinder,
) : SourceRepository {
    override suspend fun sourcesFor(item: BaseItemDto): List<PlaySource> = search(item).last().sources

    override fun search(item: BaseItemDto): Flow<SourceSearch> = channelFlow {
        val servers = try {
            finder.participants()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (servers.isEmpty()) {
            send(SourceSearch(local.sourcesFor(item)))
            return@channelFlow
        }
        val target = async {
            try {
                targetOf(item)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
        streamSourceSearch(
            local = { local.sourcesFor(item) },
            servers = servers,
            precheck = { s -> if (finder.needsLogin(s.serverId)) ServerOutcome(ServerSearchState.NeedsLogin) else null },
        ) { s ->
            val t = target.await() ?: return@streamSourceSearch ServerOutcome(ServerSearchState.NotFound)
            finder.findOn(s, t)
        }.collect { send(it) }
    }

    /** 要找的东西。电影 / 单集以外的 = null（不找）。 */
    private suspend fun targetOf(item: BaseItemDto): SourceTarget? = when (item.type) {
        "Movie" -> {
            val full = if (item.productionYear == null && localClient != null) localClient.getItem(item.id) else item
            SourceTarget.Movie(item.providerIds.ifEmpty { full.providerIds }, item.name.ifBlank { full.name }, full.originalTitle, full.productionYear)
        }
        "Episode" -> {
            val c = localClient
            val ep = if (item.seriesId != null && item.parentIndexNumber != null && item.indexNumber != null) item else c?.getItem(item.id)
            val season = ep?.parentIndexNumber
            val no = ep?.indexNumber
            val sid = ep?.seriesId
            if (c == null || season == null || no == null || sid == null) {
                null
            } else {
                val series = c.getItem(sid)
                SourceTarget.Episode(series.providerIds, series.name, series.productionYear, season, no)
            }
        }
        else -> null
    }
}
