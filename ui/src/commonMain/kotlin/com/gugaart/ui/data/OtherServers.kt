package com.gugaart.ui.data

import androidx.compose.runtime.Immutable
import com.gugaart.ui.servers.PingState
import com.gugaart.ui.servers.ServerEntry
import com.gugugaga.app.AppGraph
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.session.SessionManager
import com.gugugaga.core.ConnectOptions
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.NO_RETRY
import com.gugugaga.core.StoredAccount
import com.gugugaga.core.StoredServer
import com.gugugaga.core.Transport
import com.gugugaga.core.apiBaseOf
import com.gugugaga.core.clientFromStored
import com.gugugaga.core.currentTimeMillis
import com.gugugaga.core.platformHttpClient
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.Volatile

/*
 * 其它服务器的只读客户端池（1.5.0 B1，docs/plan-multi-server.md §2.2）。
 *
 * 详情页和播放器要去别的服务器找同一部片（B2）、从别台的源接着放（B3），就要同时握着几台服务器的客户端。
 * 上游 `AppGraph` 只有一个活动会话，**不动它**：这里按上游公开的构造（`clientFromStored` + `ConnectOptions`）
 * 给每台参与的服务器另造一个 `EmbyClient`，令牌用那台已保存的 `StoredAccount`（做法同 `update/OwnVerifier.kt`
 * 用公开构造另造 `UpdateController`）。令牌只在内存里流转：不落盘、不进日志（上游 `StoredAccount.toString` 已打码）。
 *
 * - 用到时才建；5 分钟没用就丢（下次用再建，不重新登录，令牌还是存着的那个）。
 * - 令牌失效（401）：这台标成「要重新登录」，丢掉它的客户端；不弹框、不打断当前播放、不重试。
 *   要重新登录的服务器照样出现在 [OtherServerPool.participants] 里（界面要画「要重新登录」），但找片源时不再发请求。
 * - 参与哪几台：[filterOtherServers] + 设置键 `gugaart.otherServers.v1`（[OtherServersPrefs]）；开关关了 = 一台都没有。
 *
 * 界面 / B2 只认 [OtherServerPool]；真实现 [OtherServerClients]，假实现 [FakeOtherServerPool]（慢的一台、离线的一台、401 的一台）。
 */

/** 一台参与找片源的别的服务器。[serverId] = 上游已保存服务器的本地 id（和 [PlaySource.serverId] 同一种）。 */
@Immutable
data class OtherServer(val serverId: String, val name: String, val userId: String)

/** 池里一台此刻的情况（界面画在线点 / 「要重新登录」）。 */
enum class OtherServerHealth {
    /** 这次还没连过。 */
    Unknown,

    /** 最近一次请求有回应。 */
    Online,

    /** 最近一次请求连不上 / 超时。 */
    Offline,

    /** 令牌被拒（401）：要用户回到那台重新登录。不自动重试。 */
    NeedsLogin,
}

/** 其它服务器的只读客户端池。 */
interface OtherServerPool {
    /**
     * 现在要去找片源的别台（[filterOtherServers]：开关关 = 空；不含当前那台；白名单只含勾选的）。
     * 顺序跟已保存的服务器列表。要重新登录的那台也在里面（看 [health]），找片源时跳过它。
     */
    suspend fun participants(): List<OtherServer>

    /**
     * 这台的只读客户端：懒建，用一次续 5 分钟。不认识的 / 要重新登录的 = null。
     * 客户端遇到 401 会自己把这台标成 [OtherServerHealth.NeedsLogin]（不用调用方处理）。
     */
    suspend fun client(serverId: String): EmbyClient?

    /**
     * 不挂起的版本（播放器在组合里开控制器时用）：池里有就给，没有就按上次 [participants] 读到的已保存记录现建。
     * 从没读过 / 要重新登录 = null。
     */
    fun clientNow(serverId: String): EmbyClient?

    /** 这台最近一次 ping 的往返毫秒（不知道 = null）。 */
    fun latencyMs(serverId: String): Int?

    /** 记下一次 ping / 请求的结果（B2 找片源时顺手量的）：[ms] = 往返毫秒；null = 连不上。 */
    fun report(serverId: String, ms: Int?)

    /** 各台的情况（没出现在表里的 = [OtherServerHealth.Unknown]）。 */
    val health: StateFlow<Map<String, OtherServerHealth>>

    /** 丢掉闲了 5 分钟以上的客户端（每次 [client] 都会顺手做一次；测试也直接调）。返回丢掉了几个。 */
    fun sweep(): Int
}

/** 池里闲多久就丢（5 分钟）。 */
const val OTHER_CLIENT_IDLE_MS: Long = 5 * 60_000L

/**
 * 真实现：已保存的服务器（上游 `SessionManager.saved()`，令牌在安全存储里、读到内存）+ 设置键 `gugaart.otherServers.v1`。
 * 客户端用上游公开的 `clientFromStored(server, account, ConnectOptions(...))` 造：身份 / User-Agent / 自定义头和上游主会话
 * 同一套（`identityForServer`），不重试（[NO_RETRY]），401 回调只标这一台，碰不到主会话（主会话的 401 回调认的是它自己的客户端）。
 *
 * [latency]：外面已有的 ping 结果（服务器切换器 `ServerDirectory` 的在线点）；没有就用 B2 找片源时顺手量的（[report]）。
 * [transport] / [clock] 是测试缝。
 */
class OtherServerClients(
    private val session: SessionManager,
    private val local: LocalStore,
    private val transport: Transport? = null,
    private val clock: () -> Long = ::currentTimeMillis,
    idleMs: Long = OTHER_CLIENT_IDLE_MS,
    private val latency: (serverId: String) -> Int? = { null },
) : OtherServerPool {
    private val state = OtherPoolState(clock, idleMs)
    private val lazyTransport: Transport by lazy { transport ?: KtorTransport(platformHttpClient()) }

    /** 上次 [participants] 读到的已保存记录（[clientNow] 现建用）。 */
    @Volatile
    private var stored: Map<String, Pair<StoredServer, StoredAccount>> = emptyMap()

    /** 401 时那台用的令牌：存着的令牌换了（用户重新登录过）就撤掉「要重新登录」。只在内存，不落盘。 */
    @Volatile
    private var deadTokens: Map<String, String> = emptyMap()

    /** 一共新建过几个客户端（单测）。 */
    val built: Int get() = state.built

    override suspend fun participants(): List<OtherServer> {
        val saved = session.saved().servers
        val accounts = saved.mapNotNull { s -> accountOf(s)?.let { s to it } }
        stored = accounts.associate { (s, a) -> s.id to (s to a) }
        for ((s, a) in accounts) {
            val dead = deadTokens[s.id]
            if (dead != null && dead != a.accessToken) {
                deadTokens = deadTokens - s.id
                state.clearNeedsLogin(s.id)
            }
        }
        val entries = accounts.map { (s, a) ->
            ServerEntry(s.id, a.userId, s.name, s.remark, apiBaseOf(s.address), a.userName, isCurrent = false, lastUsedAt = null, ping = PingState.Unknown)
        }
        val prefs = OtherServersPrefs.parse(local.get(OtherServersSettings.KEY))
        return filterOtherServers(entries, session.session.value?.serverId, prefs).map { OtherServer(it.serverId, it.name, it.userId) }
    }

    override suspend fun client(serverId: String): EmbyClient? {
        if (stored[serverId] == null) participants()
        return clientNow(serverId)
    }

    override fun clientNow(serverId: String): EmbyClient? {
        val (server, account) = stored[serverId] ?: return null
        return state.getOrBuild(serverId) {
            val opts = ConnectOptions(
                identity = session.identity,
                transport = lazyTransport,
                retry = NO_RETRY,
                onUnauthorized = {
                    deadTokens = deadTokens + (serverId to account.accessToken)
                    state.unauthorized(serverId)
                },
            )
            clientFromStored(server, account, opts)
        }
    }

    override fun latencyMs(serverId: String): Int? = latency(serverId) ?: state.latencyMs(serverId)

    override fun report(serverId: String, ms: Int?) = state.report(serverId, ms)

    override val health: StateFlow<Map<String, OtherServerHealth>> get() = state.health

    override fun sweep(): Int = state.sweep()

    private fun accountOf(s: StoredServer): StoredAccount? =
        s.accounts.firstOrNull { it.userId == s.activeUserId } ?: s.accounts.firstOrNull()
}

/**
 * 每个应用图一个池（同 [com.gugaart.ui.update.OwnVerifier] 的做法：挂在 `AppGraph` 旁边，不改上游）。
 * 详情页的「源」和播放器换源都从这里拿同一个池（播放器开别台的源要用找片源时已经建好的客户端）。
 */
object OtherServers {
    private var graph: AppGraph? = null
    private var pool: OtherServerPool? = null

    /** 测试 / 截图换成假的（[FakeOtherServerPool]）；null = 用真的。 */
    var override: OtherServerPool? = null

    fun poolFor(g: AppGraph): OtherServerPool {
        override?.let { return it }
        pool?.takeIf { graph === g }?.let { return it }
        val p = OtherServerClients(g.session, g.deps.local, transport = g.deps.transport)
        graph = g
        pool = p
        return p
    }
}
