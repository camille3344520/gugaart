package com.gugaart.ui.screens.login

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Emby UDP 7359 发现协议。 */
const val EMBY_DISCOVERY_PORT = 7359
const val EMBY_DISCOVERY_QUERY = "who is EmbyServer?"

/** 局域网里发现的一台 Emby 服务器。 */
@Immutable
data class DiscoveredServer(
    val id: String,
    val name: String,
    val address: String,
    val version: String = "",
)

interface LanDiscovery {
    fun discover(timeoutMs: Long = 1600L): Flow<DiscoveredServer>
}

expect fun platformLanDiscovery(): LanDiscovery?

/** 测试缝：离屏 / 集成测试注入假的或指向 Mock 端口的局域网发现；null = 用平台默认实现。 */
object LanDiscoveryOverride {
    var current: LanDiscovery? = null
    /** 测试夹具禁用真实 UDP 7359 广播（避免打到宿主机器上的真 Emby）；只有显式设了 [current] 才走自定义的。 */
    var disablePlatformDefault: Boolean = false
}

internal fun resolveLanDiscovery(explicit: LanDiscovery?): LanDiscovery? =
    explicit ?: LanDiscoveryOverride.current ?: if (LanDiscoveryOverride.disablePlatformDefault) null else platformLanDiscovery()

/** 解析 Emby UDP 7359 回包的 JSON（真 Emby 有 Address / Id / Name；Mock 还会带 Version）。纯函数，有单测。 */
fun parseDiscoveryPacket(raw: String): DiscoveredServer? {
    val obj = try {
        Json.parseToJsonElement(raw.trim()).jsonObject
    } catch (_: Exception) {
        return null
    }
    return parseDiscoveryJson(obj)
}

private fun parseDiscoveryJson(obj: JsonObject): DiscoveredServer? {
    val lower = HashMap<String, String>(obj.size)
    for ((k, v) in obj) {
        val p = v as? JsonPrimitive ?: continue
        if (p.isString) lower[k.lowercase()] = p.content.trim()
    }
    val rawAddr = lower["address"]?.takeIf { it.isNotEmpty() }
        ?: lower["endpointaddress"]?.takeIf { it.isNotEmpty() }?.let { ep ->
            if (ep.contains("://")) ep else "http://$ep"
        }
        ?: return null
    val id = lower["id"]?.takeIf { it.isNotEmpty() } ?: rawAddr
    val name = lower["name"]?.takeIf { it.isNotEmpty() } ?: "Emby 服务器"
    val version = lower["version"]?.takeIf { it.isNotEmpty() }?.let(::shortVersion).orEmpty()
    return DiscoveredServer(id = id, name = name, address = rawAddr, version = version)
}

/** 一行副标题：「Emby 4.8.8 · http://192.168.1.10:8096」（没有版本就不写版本）。纯函数，有单测。 */
fun discoverySubtitle(s: DiscoveredServer): String = listOfNotNull(
    s.version.takeIf { it.isNotBlank() }?.let { "Emby ${shortVersion(it)}" },
    s.address,
).joinToString(" · ")
