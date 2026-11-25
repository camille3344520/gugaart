package com.gugaart.ui.servers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.session.SavedServers
import com.gugugaga.app.session.ServerPatch
import com.gugugaga.app.session.SessionManager
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyException
import com.gugugaga.core.EmbyHttp
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.NO_RETRY
import com.gugugaga.core.PublicSystemInfo
import com.gugugaga.core.StoredServer
import com.gugugaga.core.Transport
import com.gugugaga.core.apiBaseOf
import com.gugugaga.core.buildEmbyHeaders
import com.gugugaga.core.currentTimeMillis
import com.gugugaga.core.describeEmbyError
import com.gugugaga.core.identityForServer
import com.gugugaga.core.platformHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

/**
 * 真实现（任务 A1）：接上游会话 [SessionManager]，把已保存服务器排好序、并行 ping、搜索、切换、改备注 / 删除。
 *
 * - **排序**：当前那台第一，其余按最近使用。最近使用取 `StoredAccount.lastLoginAt`、上游 `StoredServer.lastUsedAt`
 *   和 gugaart 自己的本地键 `gugaart.serverRecent.v1` 三者里最大的那个；切换成功时往自己的键里记一笔（**不改**上游 `gugu.servers.v1`）。
 * - **ping**：`GET /System/Info/Public`，每台 2 秒超时、各台并行，结果陆续写进 [entries]。离线的那台照样列出来。
 * - **切换**：调上游 `SessionManager.switchAccount`（用已存令牌，不重新登录）；401 → [SwitchResult.NeedsLogin]，连不上 → [SwitchResult.Offline]。
 * - **搜索**：复用 [ServerSearch]（1.4.1 搜索引擎的折叠 / 拼音工具）。
 *
 * [transport] 是测试缝（假 HTTP）；不给就用平台的 OkHttp 栈。[clock]  likewise 可注入。
 */
class ServerDirectory(
    private val session: SessionManager,
    private val local: LocalStore,
    private val scope: CoroutineScope,
    private val transport: Transport? = null,
    private val clock: () -> Long = ::currentTimeMillis,
    private val pingTimeoutMs: Long = 2000,
) : ServerSwitcher {
    private val recent = ServerRecentStore(local)
    private var recentMap: Map<String, Long> = recent.load()
    private val pings = MutableStateFlow<Map<String, PingState>>(emptyMap())

    @Volatile
    private var snapshot: SavedServers? = null

    private val _entries = MutableStateFlow<List<ServerEntry>>(emptyList())
    override val entries: StateFlow<List<ServerEntry>> = _entries.asStateFlow()
    private val lazyTransport: Transport by lazy { transport ?: KtorTransport(platformHttpClient()) }

    init {
        // 保存的服务器 / 账号变了（登录、切换、删除、改备注）→ 重读；会话变了（含 401 被收掉）→ 重排当前标记。
        scope.launch { session.revision.collect { reload() } }
        scope.launch { session.session.collect { publish() } }
    }

    override fun refreshPings() {
        scope.launch {
            val saved = session.saved()
            snapshot = saved
            if (saved.servers.isEmpty()) return@launch
            pings.update { m -> m + saved.servers.associate { it.id to PingState.Checking } }
            publish()
            coroutineScope {
                saved.servers.map { s ->
                    async {
                        val r = pingOne(s)
                        pings.update { it + (s.id to r) }
                        publish()
                    }
                }.awaitAll()
            }
        }
    }

    override fun search(query: String): List<ServerEntry> = ServerSearch.match(entries.value, query)

    override suspend fun switchTo(serverId: String, userId: String): SwitchResult {
        val e = entries.value.firstOrNull { it.serverId == serverId && it.userId == userId }
        if (e != null && e.ping == PingState.Offline) return SwitchResult.Offline
        return try {
            session.switchAccount(serverId, userId)
            recent.record(serverId, userId, clock())
            recentMap = recent.load()
            reload()
            SwitchResult.Ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbyException) {
            when (e.kind) {
                EmbyException.Kind.Unauthorized -> SwitchResult.NeedsLogin
                EmbyException.Kind.Network, EmbyException.Kind.Timeout -> SwitchResult.Offline
                else -> SwitchResult.Failed(describeEmbyError(e))
            }
        } catch (e: Exception) {
            SwitchResult.Failed(e.message ?: "切换失败")
        }
    }

    override suspend fun rename(serverId: String, note: String?) {
        // 上游 updateServer 的 remark 为 null 表示「不改」；要清空得传空串（它 trim 后落 null）。
        session.updateServer(serverId, ServerPatch(remark = note ?: ""))
        reload()
    }

    override suspend fun remove(serverId: String) {
        session.forgetServer(serverId)
        recent.forgetServer(serverId)
        recentMap = recent.load()
        pings.update { it - serverId }
        reload()
    }

    private suspend fun reload() {
        snapshot = session.saved()
        publish()
    }

    /** 按当前快照 + ping + 最近使用 + 当前会话重排列表（纯计算，输入都是当前值）。 */
    private fun publish() {
        val s = snapshot ?: return
        val cur = session.session.value
        val list = s.servers.flatMap { srv ->
            srv.accounts.map { acc ->
                val isCurrent = if (cur != null) {
                    cur.serverId == srv.id && cur.userId == acc.userId
                } else {
                    val active = s.active
                    active != null && active.serverId == srv.id &&
                        (active.userId == acc.userId || (active.userId == null && srv.activeUserId == acc.userId))
                }
                val last = maxOf(acc.lastLoginAt, srv.lastUsedAt ?: 0L, recentMap[ServerRecentStore.key(srv.id, acc.userId)] ?: 0L)
                    .takeIf { it > 0 }
                ServerEntry(
                    serverId = srv.id,
                    userId = acc.userId,
                    name = srv.name,
                    note = srv.remark,
                    address = apiBaseOf(srv.address),
                    userName = acc.userName,
                    isCurrent = isCurrent,
                    lastUsedAt = last,
                    ping = pings.value[srv.id] ?: PingState.Unknown,
                )
            }
        }
        _entries.value = sortServerEntries(list)
    }

    /** 一台的 ping：公开端点，不带令牌；2 秒超时、不重试。任何失败都算离线。 */
    private suspend fun pingOne(server: StoredServer): PingState {
        val http = EmbyHttp(
            transport = lazyTransport,
            apiBase = apiBaseOf(server.address),
            headers = { buildEmbyHeaders(identityForServer(session.identity, server)) },
            timeoutMs = pingTimeoutMs,
            retry = NO_RETRY,
        )
        val mark = TimeSource.Monotonic.markNow()
        return try {
            http.get<PublicSystemInfo>("/System/Info/Public")
            PingState.Online(mark.elapsedNow().inWholeMilliseconds.toInt().coerceAtLeast(0))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PingState.Offline
        }
    }
}

/**
 * gugaart 自己的「最近使用」本地键（**不碰**上游 `gugu.servers.v1`）。键 `gugaart.serverRecent.v1`，
 * 值是 `{ "serverId/userId": epochMs }`。读不懂的内容当空。规划 §2.1 已定这个键名。
 */
internal class ServerRecentStore(private val local: LocalStore) {
    fun load(): Map<String, Long> {
        val raw = local.get(KEY) ?: return emptyMap()
        return try {
            Json.parseToJsonElement(raw).jsonObject.entries.mapNotNull { (k, v) ->
                v.jsonPrimitive.longOrNull?.let { k to it }
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun record(serverId: String, userId: String, at: Long) {
        val next = load().toMutableMap()
        next[key(serverId, userId)] = at
        save(next)
    }

    fun forgetServer(serverId: String) {
        save(load().filterKeys { !it.startsWith("$serverId/") })
    }

    private fun save(map: Map<String, Long>) {
        local.set(KEY, buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString())
    }

    companion object {
        const val KEY = "gugaart.serverRecent.v1"
        fun key(serverId: String, userId: String): String = "$serverId/$userId"
    }
}

/**
 * 界面拿真实现的入口（数据层接线，和 `data/Loads.kt` 的 `rememberLoad` 同一种）：跟着 [LocalGraph] 记一份，
 * 收集器挂在 app 生命周期的 scope 上。界面只认 [ServerSwitcher]，要对假数据就换 [FakeServerSwitcher]。
 */
@Composable
fun rememberServerSwitcher(): ServerSwitcher {
    val graph = LocalGraph.current
    return remember(graph) {
        ServerDirectory(graph.session, graph.deps.local, graph.scope, transport = graph.deps.transport)
    }
}
