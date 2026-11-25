package com.gugaart.ui.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/*
 * 跨服务器找同一部片（1.5.0 B2，docs/plan-multi-server.md §2.2）——接口和状态类型。
 *
 * 详情页 / 播放器「版本」面板要的是一条**流**：先给当前服务器的源，别台的到一台补一台（[SourceRepository.search]）。
 * 每一次发出的 [SourceSearch] 都是**累计**的：[SourceSearch.sources] 只往后接（本台的在前，别台按到达先后），
 * [SourceSearch.servers] 是每台别的服务器此刻的进展（界面画「加载中」「离线」「要重新登录」用）。
 *
 * - 电影：`AnyProviderIdEquals=tmdb.<id>,imdb.<id>`；没有 provider id（或对方没认出来）退回「片名 + 年份」精确匹配，拿不准就不列。
 * - 剧集单集：先按剧的 provider id（tmdb / imdb / tvdb）找到对方的剧，再按季号 + 集号找到那一集。
 * - 所有参与的服务器并行查，每台 3 秒超时；`latencyMs` 用 ping 的结果，`playable` 用 C1 的预估（[estimatePlayable]）。
 *
 * 真实现：[OtherServerSourceFinder] + [CrossServerSourceRepository]；假实现：[FakeOtherServerSources]（三台：快的、慢 2 秒的、离线的）。
 */

/** 一台别的服务器这一次找片源走到哪了。 */
enum class ServerSearchState {
    /** 正在找（界面画加载中占位）。 */
    Searching,

    /** 找到了（这台的源已经接进 [SourceSearch.sources]）。 */
    Found,

    /** 问到了，但这台上没有这部片（或者拿不准，不列）。 */
    NotFound,

    /** 连不上。 */
    Offline,

    /** 3 秒内没答完。 */
    TimedOut,

    /** 令牌失效（401），要用户回到那台重新登录；不发请求、不重试。 */
    NeedsLogin,
}

/** 一台别的服务器的进展。[latencyMs] = ping 的往返毫秒（不知道 = null）；[found] = 这台贡献了几个源。 */
@Immutable
data class OtherServerProgress(
    val server: OtherServer,
    val state: ServerSearchState,
    val latencyMs: Int? = null,
    val found: Int = 0,
)

/**
 * 一次找片源的累计结果。[sources]：本台的在前，别台的按到达先后接在后面（不重排）；
 * [servers]：参与的别台（顺序固定 = 参与顺序）；[done]：所有台都有结论了（找到 / 没有 / 离线 / 超时 / 要重新登录）。
 */
@Immutable
data class SourceSearch(
    val sources: List<PlaySource>,
    val servers: List<OtherServerProgress> = emptyList(),
    val done: Boolean = true,
)

/** 要在别的服务器上找的东西。 */
sealed interface SourceTarget {
    /** 一部电影：provider id 优先；没有就靠 [name]（或 [originalTitle]）+ [year] 精确匹配。 */
    data class Movie(
        val providerIds: Map<String, String?>,
        val name: String,
        val originalTitle: String? = null,
        val year: Int? = null,
    ) : SourceTarget

    /** 一集：先按剧（[seriesProviderIds]，没有就 [seriesName] + [seriesYear]）找到对方的剧，再按 [season] + [episode] 找那一集。 */
    data class Episode(
        val seriesProviderIds: Map<String, String?>,
        val seriesName: String,
        val seriesYear: Int? = null,
        val season: Int,
        val episode: Int,
    ) : SourceTarget
}

/** 每台服务器找片源的时限（3 秒）。 */
const val OTHER_SEARCH_TIMEOUT_MS: Long = 3_000L

/** 一台别的服务器这一次的结论（[streamSourceSearch] 的输入）。 */
data class ServerOutcome(val state: ServerSearchState, val sources: List<PlaySource> = emptyList(), val latencyMs: Int? = null)

/**
 * 把「本台的源」和「各台别的服务器」拼成一条累计的流（真实现和假实现共用，顺序规矩只写在这里）：
 * - 别台的查询和本台的**同时**开始；但第一条发出的一定是本台的（别台先答完的先攒着）；
 * - 之后每有一台答完就发一次：它的源接在最后，不重排已有的；
 * - [precheck] 给出结论的台（比如要重新登录的）不发请求，第一条里就是那个结论；
 * - 本台的查询失败 = 本台 0 个源（别台照样补）；[find] 自己负责超时和把异常变成结论，抛出来的一律当 [ServerSearchState.Offline]。
 */
fun streamSourceSearch(
    local: suspend () -> List<PlaySource>,
    servers: List<OtherServer>,
    precheck: (OtherServer) -> ServerOutcome? = { null },
    find: suspend (OtherServer) -> ServerOutcome,
): Flow<SourceSearch> = channelFlow {
    val progress = servers.map { s ->
        precheck(s)?.let { OtherServerProgress(s, it.state, it.latencyMs, 0) } ?: OtherServerProgress(s, ServerSearchState.Searching)
    }.toMutableList()
    val pending = servers.indices.filter { progress[it].state == ServerSearchState.Searching }
    val results = Channel<Pair<Int, ServerOutcome>>(Channel.UNLIMITED)
    for (i in pending) launch {
        val out = try {
            find(servers[i])
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ServerOutcome(ServerSearchState.Offline)
        }
        results.send(i to out)
    }
    val acc = try {
        local()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }.toMutableList()
    var left = pending.size
    send(SourceSearch(acc.toList(), progress.toList(), done = left == 0))
    while (left > 0) {
        val (i, out) = results.receive()
        left--
        progress[i] = OtherServerProgress(servers[i], out.state, out.latencyMs, out.sources.size)
        acc += out.sources
        send(SourceSearch(acc.toList(), progress.toList(), done = left == 0))
    }
}
