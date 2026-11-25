package com.gugaart.ui.servers

import kotlinx.coroutines.flow.StateFlow

/**
 * 服务器切换的数据层（规划 docs/plan-multi-server.md §2.1 / 任务 A1）。
 *
 * 这一层只管数据：已保存服务器的排序、在线状态（ping）、搜索、切换、改备注 / 删除。
 * 界面（纸片 / 下拉 / 入口）由协调人另写，只认 [ServerSwitcher] 这个接口；先用 [FakeServerSwitcher] 对假数据做，
 * 真实现 [ServerDirectory] 好了直接换上来，形状不变。
 *
 * 复用上游会话（`SessionManager` / `SavedServers` / `StoredServer` / `StoredAccount`），不改上游的 `gugu.servers.v1`；
 * 「最近使用时间」另记在 gugaart 自己的本地键 `gugaart.serverRecent.v1`。
 */

/** 列表里的一台服务器（的一个账号）。[serverId] 是上游 `StoredServer.id`（本地 id），切换时传给 `SessionManager.switchAccount`。 */
data class ServerEntry(
    val serverId: String,
    val userId: String,
    /** 服务器名（上游 `StoredServer.name`）。 */
    val name: String,
    /** 用户备注（上游 `StoredServer.remark`），没有为 null。 */
    val note: String?,
    /** 展示 / 搜索用的地址（API 根，含可选 `/emby` 前缀）。 */
    val address: String,
    /** 当前账号名（上游 `StoredAccount.userName`）。 */
    val userName: String,
    /** 是不是正在用的那台。 */
    val isCurrent: Boolean,
    /** 最近一次使用的 epoch 毫秒；从没用过为 null。 */
    val lastUsedAt: Long?,
    val ping: PingState,
)

/** 在线状态。打开列表时并行 ping `/System/Info/Public`，每台 2 秒超时。 */
sealed interface PingState {
    /** 还没 ping 过。 */
    data object Unknown : PingState

    /** 正在 ping。 */
    data object Checking : PingState

    /** 连上了，[ms] 是往返毫秒。 */
    data class Online(val ms: Int) : PingState

    /** 连不上（超时 / 拒绝 / 网络错）。照样列出来，但对它 [ServerSwitcher.switchTo] 返回 [SwitchResult.Offline]，不切。 */
    data object Offline : PingState
}

/** [ServerSwitcher.switchTo] 的结果。 */
sealed interface SwitchResult {
    /** 切过去了（用已存的令牌，不重新登录）。 */
    data object Ok : SwitchResult

    /** 那台连不上，没有切。 */
    data object Offline : SwitchResult

    /** 令牌失效（401）：要重新登录，不弹框、不打断。 */
    data object NeedsLogin : SwitchResult

    /** 别的失败，[reason] 是一句人话（不带地址 / 令牌）。 */
    data class Failed(val reason: String) : SwitchResult
}

/**
 * 界面要用的全部入口。实现：[FakeServerSwitcher]（假数据，给界面和离屏截图）、[ServerDirectory]（真实现，接上游会话）。
 */
interface ServerSwitcher {
    /** 排好序的列表：当前那台第一，其余按最近使用（从没用过的排最后）。ping 结果会写进这里。 */
    val entries: StateFlow<List<ServerEntry>>

    /** 并行 ping 每一台（每台 2 秒超时），结果陆续写进 [entries]。非阻塞：发起后立即返回。 */
    fun refreshPings()

    /** 搜索：名字 / 备注 / 地址做子串匹配，加拼音首字母。空查询返回全部（仍按 [entries] 的顺序）。 */
    fun search(query: String): List<ServerEntry>

    /** 切到某台某账号。离线返回 [SwitchResult.Offline]（不切）；401 返回 [SwitchResult.NeedsLogin]；成功记最近使用时间。 */
    suspend fun switchTo(serverId: String, userId: String): SwitchResult

    /** 改备注（[note] 为 null 清空）。 */
    suspend fun rename(serverId: String, note: String?)

    /** 删除一台（连同它的账号）。删的是当前那台时上游会退出登录。 */
    suspend fun remove(serverId: String)
}

/**
 * 排序（纯函数，有单测）：当前那台第一，其余按最近使用降序，从没用过（[ServerEntry.lastUsedAt] 为 null）的排最后；
 * 同档按名字、再按 id 稳定排。fake 和真实现共用。
 */
fun sortServerEntries(entries: List<ServerEntry>): List<ServerEntry> =
    entries.sortedWith(
        compareByDescending<ServerEntry> { it.isCurrent }
            .thenByDescending { it.lastUsedAt ?: Long.MIN_VALUE }
            .thenBy { it.name }
            .thenBy { it.serverId }
            .thenBy { it.userId },
    )
