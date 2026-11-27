package com.gugaart.ui.danmaku

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gugugaga.app.settings.SecretKeys
import com.gugugaga.core.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/*
 * 多个弹幕服务器（用户 2026-11-15：「弹幕支持多源，先匹配出来的优先显示」→ 问过：是「多个弹幕服务器」）。
 *
 * 上游的匹配器只认一个地址（安全存储里的 `gugu.danmakuApiUrl`）。画报不改上游：
 *  - 服务器清单存在画报自己的键 [DanmakuServers.KEY]（同一个安全存储：地址里带令牌）。第一台同时写回上游那个键，含义不变
 *    （主弹幕服务器）；
 *  - 只有一台：上游直连它，和以前完全一样；
 *  - 两台以上：上游从安全存储读那个键时读到的是本机回环上的「中转」地址（[RelaySecureStore] / [DanmakuRelay]），中转把每个
 *    请求同时发给所有服务器——/match 谁先匹配上用谁，搜索先回来的排前面，分集 / 弹幕按编号发回原来那一台（[DanmakuRelayHandler]）。
 *    各台的编号不会撞：编号 × 16 + 这一台的槽位号（[encodeId]）。槽位号跟着服务器走，增删不影响别的服务器。
 */

/** 一台弹幕服务器：[slot] = 槽位号（0–15，编号编码用，跟着这台服务器走），[url] = API 地址（带令牌，不打印）。 */
data class DanmakuServer(val slot: Int, val url: String)

/** 最多几台（槽位号 0–15 里留出余量）。 */
const val MAX_DANMAKU_SERVERS = 8

/** 编号编码的进制（槽位号 < 16）。 */
const val DANMAKU_SLOTS = 16

/** 存着的清单 → 列表；读不懂 = null。纯函数，有单测。 */
fun parseDanmakuServers(raw: String?): List<DanmakuServer>? {
    if (raw.isNullOrBlank()) return null
    val arr = try {
        (Json.parseToJsonElement(raw) as? JsonObject)?.get("servers") as? JsonArray
    } catch (_: Exception) {
        null
    } ?: return null
    return arr.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val slot = (o["slot"] as? JsonPrimitive)?.intOrNull ?: return@mapNotNull null
        val url = (o["url"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (slot !in 0 until DANMAKU_SLOTS || url.isEmpty()) null else DanmakuServer(slot, url)
    }.distinctBy { it.slot }.distinctBy { it.url }
}

fun danmakuServersJson(list: List<DanmakuServer>): String = buildJsonObject {
    put("servers", buildJsonArray { list.forEach { s -> add(buildJsonObject { put("slot", s.slot); put("url", s.url) }) } })
}.toString()

/** 空着的最小槽位号。 */
fun nextDanmakuSlot(taken: Collection<DanmakuServer>): Int = (0 until DANMAKU_SLOTS).first { s -> taken.none { it.slot == s } }

/**
 * 按用户填的地址（有先后）排出清单：去空、去重、最多 [MAX_DANMAKU_SERVERS] 台；还在的服务器保留原来的槽位号，新加的拿空着的。纯函数，有单测。
 */
fun arrangeDanmakuServers(urls: List<String>, old: List<DanmakuServer>): List<DanmakuServer> {
    val out = ArrayList<DanmakuServer>()
    for (u in urls.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_DANMAKU_SERVERS)) {
        val keep = old.firstOrNull { it.url == u && out.none { o -> o.slot == it.slot } }
        out += keep ?: DanmakuServer(nextDanmakuSlot(out + old.filter { o -> urls.any { it.trim() == o.url } }), u)
    }
    return out
}

/**
 * 存着的清单和上游那个键对一对（上游那个键可能在别处被改过：上游自己的设置页、电视的键盘）：
 * 上游的值就是第一台；空了 = 第一台被清掉。纯函数，有单测。
 */
fun reconcileDanmakuServers(stored: List<DanmakuServer>?, primary: String): List<DanmakuServer> {
    val list = stored.orEmpty()
    return when {
        list.isEmpty() -> if (primary.isEmpty()) emptyList() else listOf(DanmakuServer(0, primary))
        list.first().url == primary -> list
        primary.isEmpty() -> list.drop(1)
        else -> arrangeDanmakuServers(listOf(primary) + list.drop(1).map { it.url }, list.drop(1))
    }
}

/** 弹幕服务器清单（进程里一份）。壳在建 PlatformDeps 时用**真的**安全存储 [install]；离屏测试没装就用页面给的那一个（[bind]）。 */
object DanmakuServers {
    const val KEY = "gugaart.danmaku.servers.v1"

    private var store: KeyValueStore? = null
    private var defaultPrimary: String? = null
    private var fromShell = false
    private val _state = MutableStateFlow<List<DanmakuServer>?>(null)

    /** null = 还没读过。 */
    val state: StateFlow<List<DanmakuServer>?> = _state.asStateFlow()

    /** 壳：真的安全存储（不是 [RelaySecureStore]）+ debug 包的默认地址（正式包 null）。 */
    fun install(real: KeyValueStore, debugDefault: String?) {
        store = real
        defaultPrimary = debugDefault
        fromShell = true
        _state.value = null
    }

    /** 页面：壳没装（离屏测试，每个测试一个应用）就用这个应用的那一个；换了就重读。 */
    fun bind(fallback: KeyValueStore, debugDefault: String?) {
        if (fromShell || store === fallback) return
        store = fallback
        defaultPrimary = debugDefault
        _state.value = null
    }

    /** 测试用。 */
    fun uninstall() {
        store = null
        defaultPrimary = null
        fromShell = false
        _state.value = null
    }

    /** 现在的清单（第一台 = 上游那个键）。 */
    suspend fun load(): List<DanmakuServer> {
        val s = store ?: return emptyList()
        // 和上游 SecretsStore 一样：存着的值（哪怕是空的 = 用户清掉了）优先，没存过才用 debug 的默认
        val primary = (s.get(SecretKeys.DANMAKU_API_URL) ?: defaultPrimary ?: "").trim()
        val list = reconcileDanmakuServers(parseDanmakuServers(s.get(KEY)), primary)
        _state.value = list
        return list
    }

    /** 已经读过就用读过的。 */
    suspend fun current(): List<DanmakuServer> = _state.value ?: load()

    /** 按用户填的顺序存（空的去掉）。第一台写回上游那个键；一台都没有 = 上游那个键存成空（用户清掉了，debug 默认不回来）。 */
    suspend fun save(urls: List<String>) {
        val s = store ?: return
        val list = arrangeDanmakuServers(urls, current())
        s.set(KEY, danmakuServersJson(list))
        s.set(SecretKeys.DANMAKU_API_URL, list.firstOrNull()?.url.orEmpty())
        _state.value = list
    }

    /** 上游那个键被别处改了（[RelaySecureStore] 转过来的）：清单跟着对一下。 */
    internal suspend fun primaryChanged() {
        if (store != null) load()
    }
}

/**
 * 交给上游 AppGraph 的安全存储：读 `gugu.danmakuApiUrl` 时，两台以上就给中转地址（[DanmakuRelay.activeBase]）；别的键原样。
 * 写还是写进真的存储（上游只写它被要求写的那一项），写了主地址清单跟着对一下。
 */
class RelaySecureStore(private val real: KeyValueStore) : KeyValueStore {
    override suspend fun get(key: String): String? {
        if (key == SecretKeys.DANMAKU_API_URL) DanmakuRelay.activeBase()?.let { return it }
        return real.get(key)
    }

    override suspend fun set(key: String, value: String) {
        real.set(key, value)
        if (key == SecretKeys.DANMAKU_API_URL) DanmakuServers.primaryChanged()
    }

    override suspend fun remove(key: String) {
        real.remove(key)
        if (key == SecretKeys.DANMAKU_API_URL) DanmakuServers.primaryChanged()
    }
}

@androidx.compose.runtime.Composable
fun rememberDanmakuConfigured(): Boolean {
    val g = com.gugugaga.app.ui.LocalGraph.current
    val dmServers by DanmakuServers.state.collectAsState()
    val secrets by g.secrets.state.collectAsState()
    androidx.compose.runtime.LaunchedEffect(g) {
        g.secrets.load()
        DanmakuServers.bind(g.deps.secure, g.deps.debug.defaultSecrets?.danmakuApiUrl)
        DanmakuServers.load()
    }
    return when {
        dmServers != null -> dmServers!!.isNotEmpty()
        secrets != null -> secrets!!.danmakuApiUrl.isNotBlank()
        else -> true
    }
}
