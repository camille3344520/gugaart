package com.gugaart.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.ui.LocalGraph
import com.gugaart.ui.servers.ServerEntry
import com.gugaart.ui.servers.ServerSwitcher
import com.gugaart.ui.servers.rememberServerSwitcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 「从其它服务器找片源」（规划 docs/plan-multi-server.md §2.2 设置 / 任务 B4）。
 * 存画报自己的键 `gugaart.otherServers.v1`，**不碰**上游 `gugu.servers.v1`。写法照 [GugaPrefs]：
 * 读不懂的值回默认；未知的键写回时原样保留。
 *
 * - [enabled] 默认 true；
 * - [servers] = null（字段缺失）= **全部已保存的**参与，读时动态算，新加的服务器默认也参与；
 *   非 null = 白名单，只含勾选的。设置里逐台关时把白名单物化出来（见 [withIncluded]）。
 */
data class OtherServersPrefs(
    val enabled: Boolean = true,
    val servers: Set<String>? = null,
) {
    /** 纯函数：勾选 / 取消一台。缺失时勾选 = 保持全部；缺失时取消 = 按 [savedIds]（当前已保存）物化白名单再剔掉它。 */
    fun withIncluded(serverId: String, on: Boolean, savedIds: List<String>): OtherServersPrefs {
        val cur = servers
        val next = when {
            cur == null && on -> null
            cur == null -> savedIds.toSet() - serverId
            on -> cur + serverId
            else -> cur - serverId
        }
        return copy(servers = next)
    }

    companion object {
        fun parse(raw: String?): OtherServersPrefs {
            val o = GugaPrefs.parseObject(raw) ?: return OtherServersPrefs()
            return OtherServersPrefs(
                enabled = (o["enabled"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: true,
                servers = (o["servers"] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                    ?.toSet(),
            )
        }
    }

    /** 写回：在 [previous]（存着的原文）上只改自己认识的键；servers 为 null 时要真的拿掉旧值。 */
    fun encode(previous: String? = null): String = buildJsonObject {
        GugaPrefs.parseObject(previous)?.forEach { (k, v) -> if (k != "servers") put(k, v) }
        put("enabled", enabled)
        servers?.let { set -> put("servers", buildJsonArray { set.forEach { add(JsonPrimitive(it)) } }) }
    }.toString()
}

/**
 * 过滤（纯函数，有单测）：从已保存列表里挑「要去找片源的其它服务器」。
 * 开关关 → 空；不含当前那台；有白名单只含勾选的；已删除的服务器（不在 [saved] 里）自然剔掉；
 * 同一台多个账号只留第一条（按服务器聚合，挑账号是 B1 的事）。顺序跟 [saved]。
 */
fun filterOtherServers(saved: List<ServerEntry>, currentServerId: String?, prefs: OtherServersPrefs): List<ServerEntry> {
    if (!prefs.enabled) return emptyList()
    return saved
        .filter { it.serverId != currentServerId }
        .filter { prefs.servers == null || it.serverId in prefs.servers }
        .distinctBy { it.serverId }
}

/** 界面（B4-UI 设置页）用的读写接口。真实现 [OtherServersSettings]，假实现 [FakeOtherServersSettings]，形状不变。 */
interface OtherServersPrefsStore {
    val prefs: StateFlow<OtherServersPrefs>
    fun setEnabled(on: Boolean)
    fun setIncluded(serverId: String, on: Boolean)
}

/**
 * 真实现：照 [GugaSettingsStore] 的写法（壳给的 [LocalStore] + StateFlow）。
 * [savedIds] 给当前已保存的服务器 id，字段缺失时取消勾选要用它物化白名单。
 */
class OtherServersSettings(
    private val local: LocalStore,
    private val savedIds: () -> List<String> = { emptyList() },
) : OtherServersPrefsStore {
    private val _prefs = MutableStateFlow(OtherServersPrefs.parse(local.get(KEY)))
    override val prefs: StateFlow<OtherServersPrefs> = _prefs.asStateFlow()

    override fun setEnabled(on: Boolean) = update { it.copy(enabled = on) }

    override fun setIncluded(serverId: String, on: Boolean) = update { it.withIncluded(serverId, on, savedIds()) }

    private fun update(change: (OtherServersPrefs) -> OtherServersPrefs) {
        val next = change(_prefs.value)
        local.set(KEY, next.encode(local.get(KEY)))
        _prefs.value = next
    }

    companion object {
        const val KEY = "gugaart.otherServers.v1"
    }
}

/** 假实现：给设置页和单测用，不碰存储。[savedIds] 可随时换，模拟新增 / 删除服务器。 */
class FakeOtherServersSettings(
    initial: OtherServersPrefs = OtherServersPrefs(),
    private val savedIds: () -> List<String> = { emptyList() },
) : OtherServersPrefsStore {
    private val _prefs = MutableStateFlow(initial)
    override val prefs: StateFlow<OtherServersPrefs> = _prefs.asStateFlow()

    override fun setEnabled(on: Boolean) {
        _prefs.value = _prefs.value.copy(enabled = on)
    }

    override fun setIncluded(serverId: String, on: Boolean) {
        _prefs.value = _prefs.value.withIncluded(serverId, on, savedIds())
    }
}

/** 界面拿真实现的接线（和 [rememberServerSwitcher] 同一种）：已保存 id 从切换器列表取。 */
@Composable
fun rememberOtherServersPrefs(switcher: ServerSwitcher = rememberServerSwitcher()): OtherServersPrefsStore {
    val graph = LocalGraph.current
    return remember(graph, switcher) {
        OtherServersSettings(graph.deps.local) { switcher.entries.value.map { it.serverId }.distinct() }
    }
}