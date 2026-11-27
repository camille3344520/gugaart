package com.gugaart.ui.cast

import com.gugaart.ui.net.fill
import com.gugaart.ui.net.ipv4
import com.gugaart.ui.net.socketOptions
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.TransportRequest
import com.gugugaga.core.platformHttpClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.posix.AF_INET
import platform.posix.EACCES
import platform.posix.EHOSTUNREACH
import platform.posix.ENETUNREACH
import platform.posix.EPERM
import platform.posix.SOCK_DGRAM
import platform.posix.close
import platform.posix.errno
import platform.posix.recv
import platform.posix.sendto
import platform.posix.sockaddr_in
import platform.posix.socket

/**
 * DLNA 投屏的网络部分，iOS 版（D-lead-30；手机 / 电脑是 jvmShared 的 DlnaCastHost，行为一样）：SSDP 用 UDP（BSD 套接字），
 * 设备描述和 SOAP 控制走上游的 HTTP（Ktor / Darwin）。不用第三方库。
 *
 * iOS 14 起往组播地址发包要 `com.apple.developer.networking.multicast` 权利（要 Apple 审批，自签的包大概率没有）：没有时 `sendto`
 * 直接失败，[emptyHint] 写明原因（D-lead-30：搜不到时面板写原因，记进已知问题）。另外第一次找设备时系统会问「本地网络」权限。
 */
@OptIn(ExperimentalForeignApi::class)
class IosDlnaCastHost : CastHost {
    private val http by lazy { KtorTransport(platformHttpClient()) }

    /** 上一次找设备时的毛病（发不出组播 = 多半是没有组播权利）；null = 发出去了。 */
    private var problem: String? = null

    override fun emptyHint(): String? = problem
        ?: " · 需允许本地网络：设置 › 隐私与安全性 › 本地网络"

    override suspend fun discover(timeoutMs: Long): List<CastDevice> {
        val locations = withContext(Dispatchers.IO) { ssdp(timeoutMs) }
        return locations.mapNotNull { loc -> runCatching { get(loc)?.let { parseDeviceDescription(it, loc) } }.getOrNull() }.distinctBy { it.id }
    }

    /** 往 239.255.255.250:1900 发 M-SEARCH（AVTransport 服务、MediaRenderer 设备各两次），收 [timeoutMs] 毫秒的回应。 */
    private fun ssdp(timeoutMs: Long): Set<String> = memScoped {
        problem = null
        val locations = LinkedHashSet<String>()
        val s = socket(AF_INET, SOCK_DGRAM, 0)
        if (s < 0) {
            problem = " · 无法打开 UDP 套接字"
            return locations
        }
        try {
            socketOptions(s, recvTimeoutMs = 120)
            val group = alloc<sockaddr_in>()
            group.fill(ipv4(239, 255, 255, 250), 1900)
            var sent = 0
            var lastErr = 0
            for (target in listOf(AV_TRANSPORT, "urn:schemas-upnp-org:device:MediaRenderer:1")) {
                val data = ssdpSearch(target).encodeToByteArray()
                repeat(2) {
                    val n = data.usePinned { sendto(s, it.addressOf(0), data.size.convert(), 0, group.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) }
                    if (n >= 0) sent++ else lastErr = errno
                }
            }
            if (sent == 0) {
                problem = when (lastErr) {
                    EHOSTUNREACH, ENETUNREACH, EPERM, EACCES ->
                        " · 组播包发不出：自签包可能没有组播权限，或本地网络权限未开"
                    else -> " · 组播包发不出：错误 $lastErr"
                }
                return locations
            }
            val end = NSDate().timeIntervalSince1970 + timeoutMs / 1000.0
            val buf = ByteArray(8192)
            while (NSDate().timeIntervalSince1970 < end) {
                val n = buf.usePinned { recv(s, it.addressOf(0), buf.size.convert(), 0) }.toInt()
                if (n > 0) ssdpLocation(buf.copyOf(n).decodeToString())?.let { locations += it }
            }
        } finally {
            close(s)
        }
        locations
    }

    private suspend fun get(url: String): String? = withTimeoutOrNull(5_000) {
        val r = http.execute(TransportRequest(url, "GET", emptyMap()))
        if (r.status in 200..299) r.text else null
    }

    override suspend fun action(device: CastDevice, action: String, args: List<Pair<String, String>>): String? = runCatching {
        withTimeoutOrNull(10_000) {
            val r = http.execute(
                TransportRequest(
                    device.avTransportUrl,
                    "POST",
                    mapOf("Content-Type" to "text/xml; charset=\"utf-8\"", "SOAPACTION" to "\"$AV_TRANSPORT#$action\""),
                    soapBody(action, args),
                ),
            )
            if (r.status in 200..299) r.text else null
        }
    }.getOrNull()
}
