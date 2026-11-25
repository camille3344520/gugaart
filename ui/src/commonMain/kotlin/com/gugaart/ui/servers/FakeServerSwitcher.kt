package com.gugaart.ui.servers

import com.gugugaga.core.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 假实现：给界面（A1-UI）和离屏截图用，不碰网络、不碰上游会话。
 *
 * 造 [count] 台服务器（1 / 3 / 20 都行），里头有离线的、慢的（高延迟）、要重新登录的、从没用过的，
 * 名字 / 备注 / 地址够搜索和拼音首字母演示。当前那台排第一。[refreshPings] 会先全置 [PingState.Checking]，
 * 再错开 [pingDelayMs] 陆续落回各自的状态（截图能抓到「正在检查」的中间态）。
 *
 * 默认 [scope] 自带一个，界面直接 `FakeServerSwitcher(3)` 就能用；测试传自己的（如 `runTest` 的 backgroundScope）好控制虚拟时间。
 */
class FakeServerSwitcher(
    count: Int = 3,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val pingDelayMs: Long = 350,
    private val clock: () -> Long = ::currentTimeMillis,
) : ServerSwitcher {
    private val resolvedPing = LinkedHashMap<String, PingState>()
    private val needsLogin = HashSet<String>()
    private var items: List<ServerEntry> = generate(count.coerceAtLeast(1))
    private val _entries = MutableStateFlow(sortServerEntries(items))
    override val entries: StateFlow<List<ServerEntry>> = _entries.asStateFlow()

    override fun refreshPings() {
        val snapshot = items
        scope.launch {
            items = items.map { it.copy(ping = PingState.Checking) }
            publish()
            snapshot.forEachIndexed { i, e ->
                launch {
                    delay(pingDelayMs + i * 40L)
                    mutateOne(e.serverId) { it.copy(ping = resolvedPing[e.serverId] ?: PingState.Unknown) }
                }
            }
        }
    }

    override fun search(query: String): List<ServerEntry> = ServerSearch.match(entries.value, query)

    override suspend fun switchTo(serverId: String, userId: String): SwitchResult {
        val e = items.firstOrNull { it.serverId == serverId && it.userId == userId }
            ?: return SwitchResult.Failed("没有这台服务器")
        if (e.ping == PingState.Offline) return SwitchResult.Offline
        if (serverId in needsLogin) return SwitchResult.NeedsLogin
        val now = clock()
        items = items.map {
            val mine = it.serverId == serverId && it.userId == userId
            it.copy(isCurrent = mine, lastUsedAt = if (mine) now else it.lastUsedAt)
        }
        publish()
        return SwitchResult.Ok
    }

    override suspend fun rename(serverId: String, note: String?) {
        items = items.map { if (it.serverId == serverId) it.copy(note = note) else it }
        publish()
    }

    override suspend fun remove(serverId: String) {
        items = items.filterNot { it.serverId == serverId }
        resolvedPing.remove(serverId)
        needsLogin.remove(serverId)
        publish()
    }

    private fun publish() {
        _entries.value = sortServerEntries(items)
    }

    private fun mutateOne(serverId: String, fn: (ServerEntry) -> ServerEntry) {
        items = items.map { if (it.serverId == serverId) fn(it) else it }
        publish()
    }

    private fun generate(count: Int): List<ServerEntry> = (0 until count).map { i ->
        val id = "fake-$i"
        val ping = pingFor(i, count)
        resolvedPing[id] = ping
        if (i != 0 && i % 7 == 6) needsLogin += id
        ServerEntry(
            serverId = id,
            userId = "u$i",
            name = NAMES.getOrElse(i) { "片库 ${i + 1}" },
            note = NOTES.getOrElse(i) { null },
            address = addressFor(i),
            userName = USERS.getOrElse(i) { "demo" },
            isCurrent = i == 0,
            lastUsedAt = when {
                i == 0 -> BASE
                i % 4 == 3 -> null // 从没用过
                else -> BASE - i * 3_600_000L
            },
            ping = ping,
        )
    }

    private fun pingFor(i: Int, count: Int): PingState = when {
        i == 0 -> PingState.Online(24)
        count >= 2 && i == count - 1 -> PingState.Offline // 最后一台离线
        count >= 3 && i == 1 -> PingState.Online(1800) // 慢的
        i % 5 == 4 -> PingState.Offline
        i % 5 == 3 -> PingState.Online(1500 + i * 10)
        else -> PingState.Online(30 + i * 13)
    }

    private fun addressFor(i: Int): String = when (i % 3) {
        0 -> "http://192.168.1.${10 + i}:8096"
        1 -> "https://emby$i.example.com"
        else -> "http://10.0.0.${2 + i}:8096/emby"
    }

    private companion object {
        const val BASE = 1_700_000_000_000L
        val NAMES = listOf("家里 NAS", "书房小主机", "香港中转", "云端片库", "公司服务器", "爸妈家", "宿舍 NAS", "备份库", "影音室", "老笔记本", "客厅盒子", "网盘挂载", "朋友共享", "出差便携", "地下室仓库", "阁楼旧机", "书房二号", "客厅二号", "阳台盒子", "储藏间")
        val NOTES = listOf<String?>("客厅 · 千兆", "外网走代理", "只放电影", null, "剧集为主", "备用", "4K 原盘", null, "纪录片", "动画", null, "中转", "共享给朋友", "便携", null, "旧片", null, "二号机", null, "仓库")
        val USERS = listOf("admin", "demo", "guest", "家人")
    }
}
