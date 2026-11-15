package com.gugaart.ui.danmaku

import com.gugugaga.app.platform.LocalStore
import com.gugugaga.core.KeyValueStore
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.platformHttpClient
import java.security.SecureRandom

/** 本机记中转端口 / 口令的键（不是秘密：口令只是让本机别的程序用不了中转）。 */
private const val RELAY_PORT_KEY = "gugaart.danmaku.relayPort"
private const val RELAY_TOKEN_KEY = "gugaart.danmaku.relayToken"

/**
 * 壳建 PlatformDeps 时调一次：装好多弹幕服务器的清单和中转（DanmakuServers.kt / DanmakuRelay.kt），返回交给上游的安全存储
 * （[RelaySecureStore]：两台以上服务器时，上游读主弹幕地址读到的是中转）。[debugDefault] = debug 包的默认弹幕地址（正式包 null）。
 */
fun withDanmakuRelay(real: KeyValueStore, local: LocalStore, debugDefault: String?): KeyValueStore {
    DanmakuServers.install(real, debugDefault)
    val token = local.get(RELAY_TOKEN_KEY)?.takeIf { it.length >= 16 } ?: randomToken().also { local.set(RELAY_TOKEN_KEY, it) }
    val port = local.get(RELAY_PORT_KEY)?.toIntOrNull()?.takeIf { it in 1024..65535 }
    DanmakuRelay.install(
        LoopbackRelayHost(port, token) { p -> if (p != port) local.set(RELAY_PORT_KEY, p.toString()) },
        KtorTransport(platformHttpClient()),
    )
    return RelaySecureStore(real)
}

private fun randomToken(): String {
    val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it) }
}
