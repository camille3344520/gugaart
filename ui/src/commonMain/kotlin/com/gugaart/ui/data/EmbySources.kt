package com.gugaart.ui.data

import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyJson
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.PlaybackInfoResponse
import com.gugugaga.core.PlaybackMode
import com.gugugaga.core.PlaybackPrefs
import com.gugugaga.core.QueryResult
import kotlinx.coroutines.CancellationException

/*
 * 真实现（1.5.0 C1）：同一台服务器上的源。
 * - Emby 已经合并好的版本（条目的 `MediaSources`）→ SameItem；
 * - 没被合并的重复片：用这部片的 TMDB / IMDB id 在**同一台**上再查一次 `AnyProviderIdEquals`（只查电影对电影），
 *   id 不同但 provider id 相同的 → Duplicate（docs/plan-multi-server.md §2.3）。剧集的单集不做这一步。
 * 上游 `LibraryQuery` 没有 `AnyProviderIdEquals` 参数 → 用上游公开的 `EmbyClient.getJson` 在这里拼查询（规划 §2.2），不改上游。
 * 直连 / 转码的预估复用上游的播放判定（`resolvePlayback` + 当前引擎的 `PlaybackPrefs`），不另写一套。
 * 全部只读；重复片查询失败不影响本条目的源（列表照出，只是少几行）。
 */

/**
 * 直连 / 转码预估（C1 的做法，B2 别台的源也用它）：上游 `resolvePlayback` 对这一个片源按当前引擎的判定走一遍（不发请求）。
 * [prefs] null = 判不了（Unknown）。
 */
fun estimatePlayable(client: EmbyClient, itemId: String, ms: MediaSourceInfo, prefs: PlaybackPrefs?): Playable {
    val p = prefs ?: return Playable.Unknown
    return try {
        val r = client.resolvePlayback(
            PlaybackInfoResponse(mediaSources = listOf(ms), playSessionId = null),
            p.copy(itemId = itemId, mediaSourceId = ms.id),
        )
        if (r.mode == PlaybackMode.Transcode) Playable.Transcode else Playable.Direct
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Playable.Unknown
    }
}

class EmbySourceRepository(
    private val client: EmbyClient,
    private val serverId: String,
    private val serverName: String,
    /** 当前设备 / 引擎的播放偏好（itemId、mediaSourceId 由这里填）；null = 判不了直连 / 转码（Unknown）。 */
    private val prefs: (itemId: String, mediaSourceId: String) -> PlaybackPrefs? = { _, _ -> null },
    /** 这台服务器最近一次探测的延迟（不知道 = null）。 */
    private val latencyMs: () -> Int? = { null },
) : SourceRepository {

    override suspend fun sourcesFor(item: BaseItemDto): List<PlaySource> {
        val out = ArrayList<PlaySource>()
        val own = item.mediaSources.ifEmpty { fetchSources(item.id) }
        for (ms in own) out += toSource(item.id, ms, SourceKind.SameItem)
        // 只有电影找重复片；剧集的单集不做（规划 §2.3）
        if (item.type == "Movie") {
            val q = providerQueryValue(item.providerIds)
            if (q != null) {
                for (d in queryByProvider(q)) {
                    if (!isDuplicateItem(item, d)) continue
                    val sources = d.mediaSources.ifEmpty { fetchSources(d.id) }
                    for (ms in sources) out += toSource(d.id, ms, SourceKind.Duplicate)
                }
            }
        }
        return out
    }

    private suspend fun fetchSources(itemId: String): List<MediaSourceInfo> = try {
        client.getItemShared(itemId).mediaSources
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    /** 同一台上按 provider id 找同类条目（电影）。失败 = 空（本条目的源不受影响）。 */
    private suspend fun queryByProvider(anyProviderIdEquals: String): List<BaseItemDto> = try {
        val el = client.getJson(
            "/Users/${client.requireUserId()}/Items",
            mapOf(
                "Recursive" to true,
                "IncludeItemTypes" to "Movie",
                "AnyProviderIdEquals" to anyProviderIdEquals,
                "Fields" to "MediaSources,ProviderIds",
                "EnableImages" to false,
                "EnableUserData" to false,
                "Limit" to 20,
            ),
        )
        EmbyJson.decodeFromJsonElement(QueryResult.serializer(BaseItemDto.serializer()), el).items
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }

    private fun toSource(itemId: String, ms: MediaSourceInfo, kind: SourceKind): PlaySource =
        PlaySource(serverId, serverName, itemId, ms, kind, latencyMs(), playableOf(itemId, ms))

    private fun playableOf(itemId: String, ms: MediaSourceInfo): Playable = estimatePlayable(client, itemId, ms, prefs(itemId, ms.id))

    companion object {
        /** `AnyProviderIdEquals` 的值：`tmdb.<id>,imdb.<id>`（有哪个写哪个；都没有 = null，不查）。 */
        fun providerQueryValue(ids: Map<String, String?>): String? {
            val parts = ArrayList<String>(2)
            for (k in listOf("tmdb", "imdb")) {
                ids.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += "$k.$it" }
            }
            return parts.joinToString(",").takeIf { it.isNotEmpty() }
        }
    }
}