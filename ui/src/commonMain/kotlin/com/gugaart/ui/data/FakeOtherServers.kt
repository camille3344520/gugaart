package com.gugaart.ui.data

import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.ClientInfo
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyException
import com.gugugaga.core.EmbyJson
import com.gugugaga.core.EmbySession
import com.gugugaga.core.NO_RETRY
import com.gugugaga.core.QueryResult
import com.gugugaga.core.ServerAddress
import com.gugugaga.core.Transport
import com.gugugaga.core.TransportRequest
import com.gugugaga.core.TransportResponse
import com.gugugaga.core.currentTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/*
 * 假实现（1.5.0 B1 / B2）：界面（B2-UI）和单测用，**不连任何服务器**。
 *
 * - [FakeEmbyServer]：一台假的 Emby（上游 `Transport` 缝）：按夹具目录答 `/System/Info/Public`、`/Users/{u}/Items`
 *   （`AnyProviderIdEquals` / `SearchTerm` / `Years` / `IncludeItemTypes`）、`/Users/{u}/Items/{id}`、`/Shows/{id}/Episodes`；
 *   可以慢、离线、401。
 * - [FakeOtherServerPool]：池的假实现，客户端是上游真的 `EmbyClient` 接在 [FakeEmbyServer] 上（401 照样会把那台标「要重新登录」）。
 *   默认四台：快的、慢 2 秒的、离线的、401 的。
 * - [FakeOtherServerSources]：B2 的假仓库，三台：快的（0.3 秒）、慢的（2 秒）、离线的；协调人照它做 B2-UI。
 */

/** 假服务器的脾气。 */
sealed interface FakeServerBehavior {
    /** 正常答，每个请求先等 [delayMs]。 */
    data class Ok(val delayMs: Long = 0) : FakeServerBehavior

    /** 连不上（每个请求都是网络错）。 */
    data object Offline : FakeServerBehavior

    /** 令牌被拒：要令牌的请求一律 401（公开的 `/System/Info/Public` 照常答）。 */
    data object Unauthorized : FakeServerBehavior
}

/** 一台假服务器：[server] 是它在池里的身份，[catalog] 是它的片子（电影、剧、单集都放这里），[latencyMs] = 假 ping 的结果。 */
data class FakeServerSpec(
    val server: OtherServer,
    val behavior: FakeServerBehavior = FakeServerBehavior.Ok(),
    val catalog: List<BaseItemDto> = emptyList(),
    val latencyMs: Int? = null,
)

/** 假 Emby（上游 [Transport] 缝）。[requests] 记下每个请求的路径（不含主机，单测看「没发请求」用）。 */
class FakeEmbyServer(private val spec: FakeServerSpec) : Transport {
    val requests = mutableListOf<String>()

    override suspend fun execute(req: TransportRequest): TransportResponse {
        val afterHost = req.url.substringAfter("://").substringAfter('/', "")
        val path = "/" + afterHost.substringBefore('?')
        val query = parseQuery(afterHost.substringAfter('?', ""))
        requests += path
        when (val b = spec.behavior) {
            FakeServerBehavior.Offline -> throw EmbyException(EmbyException.Kind.Network, "Network error: connection refused", url = req.url)
            is FakeServerBehavior.Ok -> if (b.delayMs > 0) delay(b.delayMs)
            FakeServerBehavior.Unauthorized -> if (!path.endsWith("/System/Info/Public")) return TransportResponse(401, emptyMap(), "", req.url)
        }
        val body = answer(path, query) ?: return TransportResponse(404, emptyMap(), "", req.url)
        return TransportResponse(200, mapOf("content-type" to "application/json"), body, req.url)
    }

    private fun answer(path: String, q: Map<String, String>): String? {
        if (path.endsWith("/System/Info/Public")) return """{"Id":"${spec.server.serverId}","ServerName":"${spec.server.name}","Version":"4.8.0"}"""
        Regex("""/Users/[^/]+/Items/([^/]+)$""").find(path)?.let { m ->
            val item = spec.catalog.firstOrNull { it.id == m.groupValues[1] } ?: return null
            return EmbyJson.encodeToString(BaseItemDto.serializer(), item)
        }
        if (Regex("""/Users/[^/]+/Items$""").containsMatchIn(path)) return list(itemsQuery(q))
        Regex("""/Shows/([^/]+)/Episodes$""").find(path)?.let { m ->
            val season = q["Season"]?.toIntOrNull()
            return list(spec.catalog.filter { it.type == "Episode" && it.seriesId == m.groupValues[1] && (season == null || it.parentIndexNumber == season) })
        }
        return null
    }

    private fun itemsQuery(q: Map<String, String>): List<BaseItemDto> {
        var items = spec.catalog
        q["IncludeItemTypes"]?.split(',')?.filter { it.isNotEmpty() }?.toSet()?.let { types -> items = items.filter { it.type in types } }
        q["AnyProviderIdEquals"]?.let { raw ->
            val wanted = raw.split(',').mapNotNull { p -> p.indexOf('.').takeIf { it > 0 }?.let { p.substring(0, it).lowercase() to p.substring(it + 1).lowercase() } }
            items = items.filter { i -> i.providerIds.any { (k, v) -> wanted.any { it.first == k.lowercase() && it.second == v?.lowercase() } } }
        }
        q["SearchTerm"]?.let { term -> items = items.filter { it.name.contains(term, ignoreCase = true) || it.originalTitle?.contains(term, ignoreCase = true) == true } }
        q["Years"]?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()?.let { ys -> items = items.filter { it.productionYear in ys } }
        return items
    }

    private fun list(items: List<BaseItemDto>): String =
        EmbyJson.encodeToString(QueryResult.serializer(BaseItemDto.serializer()), QueryResult(items, items.size))

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').filter { it.isNotEmpty() }.associate { kv -> decode(kv.substringBefore('=')) to decode(kv.substringAfter('=', "")) }

    /** 百分号解码（`+` 当空格）。只给假服务器用。 */
    private fun decode(s: String): String {
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '%' && i + 2 < s.length -> {
                    bytes += s.substring(i + 1, i + 3).toInt(16).toByte()
                    i += 3
                }
                c == '+' -> { bytes += ' '.code.toByte(); i++ }
                else -> { c.toString().encodeToByteArray().forEach { bytes += it }; i++ }
            }
        }
        return bytes.toByteArray().decodeToString()
    }
}

/**
 * 池的假实现：每台一个上游真的 `EmbyClient`，接在 [FakeEmbyServer] 上（令牌是假的占位）。懒建 / 过期 / 401 和真实现走同一份
 * [OtherPoolState]。[clock] 可注入（单测推时间看 5 分钟过期）。[servers] 记着每台的假服务器，单测看请求。
 */
class FakeOtherServerPool(
    specs: List<FakeServerSpec> = defaultSpecs(),
    private val clock: () -> Long = ::currentTimeMillis,
    idleMs: Long = OTHER_CLIENT_IDLE_MS,
) : OtherServerPool {
    private val state = OtherPoolState(clock, idleMs)
    private val specs = specs.associateBy { it.server.serverId }
    val servers: Map<String, FakeEmbyServer> = specs.associate { it.server.serverId to FakeEmbyServer(it) }

    /** 一共新建过几个客户端。 */
    val built: Int get() = state.built

    /** 池里现在有几个客户端。 */
    val size: Int get() = state.size

    init {
        specs.forEach { s -> s.latencyMs?.let { state.report(s.server.serverId, it) } }
    }

    override suspend fun participants(): List<OtherServer> = specs.values.map { it.server }

    override suspend fun client(serverId: String): EmbyClient? = clientNow(serverId)

    override fun clientNow(serverId: String): EmbyClient? {
        val spec = specs[serverId] ?: return null
        return state.getOrBuild(serverId) {
            EmbyClient(
                transport = servers.getValue(serverId),
                address = ServerAddress("http://$serverId.fake"),
                identity = ClientInfo(device = "fake", deviceId = "fake-device", version = "0.0.0"),
                session = EmbySession(userId = spec.server.userId, accessToken = "fake-token-$serverId"),
                retry = NO_RETRY,
                onUnauthorized = { state.unauthorized(serverId) },
            )
        }
    }

    override fun latencyMs(serverId: String): Int? = state.latencyMs(serverId)

    override fun report(serverId: String, ms: Int?) = state.report(serverId, ms)

    override val health: StateFlow<Map<String, OtherServerHealth>> get() = state.health

    override fun sweep(): Int = state.sweep()

    companion object {
        /** 默认四台：云端片库（快）、香港中转（慢 2 秒）、办公室 NAS（离线）、朋友家（令牌失效 401）。目录都是 [FakeSources] 的电影换个条目 id。 */
        fun defaultSpecs(): List<FakeServerSpec> {
            fun catalog(suffix: String) = FakeSources.items.filter { it.providerIds.isNotEmpty() }.map { it.copy(id = "${it.id}@$suffix", productionYear = 2017) }
            return listOf(
                FakeServerSpec(OtherServer("fake-cloud", "云端片库", "u-cloud"), FakeServerBehavior.Ok(), catalog("cloud"), latencyMs = 48),
                FakeServerSpec(OtherServer("fake-hk", "香港中转", "u-hk"), FakeServerBehavior.Ok(delayMs = 2_000), catalog("hk"), latencyMs = 180),
                FakeServerSpec(OtherServer("fake-office", "办公室 NAS", "u-office"), FakeServerBehavior.Offline),
                FakeServerSpec(OtherServer("fake-friend", "朋友家", "u-friend"), FakeServerBehavior.Unauthorized, catalog("friend"), latencyMs = 35),
            )
        }
    }
}

/** [FakeOtherServerSources] 里一台假的别的服务器：[delayMs] 后给出 [state]；Found 时它的源 = 本台的源换成这台（条目 id 加后缀、标 OtherServer）。 */
data class FakeRemote(
    val server: OtherServer,
    val delayMs: Long,
    val state: ServerSearchState,
    val latencyMs: Int? = null,
    val playable: Playable = Playable.Direct,
)

/**
 * B2 的假仓库（B2-UI 照它做）：本台的源来自 [local]（默认 [FakeSourceRepository]），别台按 [remotes] 的剧本陆续到。
 * 默认三台：云端片库 0.3 秒到（找到，48 ms）、香港中转 2 秒到（找到，180 ms，要转码）、办公室 NAS 0.6 秒后判离线。
 * 时间用协程 `delay`（离屏测试里是虚拟时间）。顺序规矩和真实现一样（[streamSourceSearch]）。
 */
class FakeOtherServerSources(
    private val local: SourceRepository = FakeSourceRepository(),
    private val remotes: List<FakeRemote> = DEFAULT,
) : SourceRepository {
    override suspend fun sourcesFor(item: BaseItemDto): List<PlaySource> = local.sourcesFor(item)

    override fun search(item: BaseItemDto): Flow<SourceSearch> = streamSourceSearch(
        local = { local.sourcesFor(item) },
        servers = remotes.map { it.server },
        precheck = { s -> remotes.first { it.server == s }.takeIf { it.state == ServerSearchState.NeedsLogin }?.let { ServerOutcome(it.state) } },
    ) { s ->
        val r = remotes.first { it.server == s }
        delay(r.delayMs)
        if (r.state != ServerSearchState.Found) return@streamSourceSearch ServerOutcome(r.state, latencyMs = r.latencyMs.takeIf { r.state != ServerSearchState.Offline })
        val mine = local.sourcesFor(item).filter { it.kind != SourceKind.Duplicate }
        val suffix = s.serverId.substringAfterLast('-')
        ServerOutcome(
            ServerSearchState.Found,
            mine.map { it.copy(serverId = s.serverId, serverName = s.name, itemId = "${it.itemId}@$suffix", kind = SourceKind.OtherServer, latencyMs = r.latencyMs, playable = r.playable) },
            r.latencyMs,
        )
    }

    companion object {
        val CLOUD = OtherServer("fake-cloud", "云端片库", "u-cloud")
        val HK = OtherServer("fake-hk", "香港中转", "u-hk")
        val OFFICE = OtherServer("fake-office", "办公室 NAS", "u-office")

        val DEFAULT: List<FakeRemote> = listOf(
            FakeRemote(CLOUD, delayMs = 300, state = ServerSearchState.Found, latencyMs = 48),
            FakeRemote(HK, delayMs = 2_000, state = ServerSearchState.Found, latencyMs = 180, playable = Playable.Transcode),
            FakeRemote(OFFICE, delayMs = 600, state = ServerSearchState.Offline),
        )
    }
}
