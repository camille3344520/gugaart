package com.gugaart.ui.danmaku

import com.gugugaga.app.platform.LocalStore
import com.gugugaga.core.KeyValueStore
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.platformHttpClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/** 本机记中转端口 / 口令的键（同 jvmShared 的 RelaySetup：不是秘密，口令只是让本机别的程序用不了中转）。 */
private const val RELAY_PORT_KEY = "gugaart.danmaku.relayPort"
private const val RELAY_TOKEN_KEY = "gugaart.danmaku.relayToken"

/**
 * iOS 的壳建 PlatformDeps 时调一次（D-lead-31，和桌面 / 安卓的 jvmShared 版同一个行为）：装好多弹幕服务器的清单和中转，返回交给
 * 上游的安全存储（两台以上服务器时，上游读主弹幕地址读到的是本机回环上的中转：同时问各台、先配上的用、编号回原服务器）。
 */
fun withDanmakuRelay(real: KeyValueStore, local: LocalStore, debugDefault: String?): KeyValueStore {
    DanmakuServers.install(real, debugDefault)
    val token = local.get(RELAY_TOKEN_KEY)?.takeIf { it.length >= 16 } ?: randomToken().also { local.set(RELAY_TOKEN_KEY, it) }
    val port = local.get(RELAY_PORT_KEY)?.toIntOrNull()?.takeIf { it in 1024..65535 }
    DanmakuRelay.install(
        IosLoopbackRelayHost(port, token) { p -> if (p != port) local.set(RELAY_PORT_KEY, p.toString()) },
        KtorTransport(platformHttpClient()),
    )
    return RelaySecureStore(real)
}

@OptIn(ExperimentalForeignApi::class)
private fun randomToken(): String {
    val bytes = ByteArray(16)
    val ok = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, 16u, it.addressOf(0)) } == 0
    if (!ok) for (i in bytes.indices) bytes[i] = kotlin.random.Random.nextInt(256).toByte()
    return bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
