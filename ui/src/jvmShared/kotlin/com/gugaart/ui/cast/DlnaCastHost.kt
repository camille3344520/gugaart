package com.gugaart.ui.cast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL

/**
 * DLNA 投屏的网络部分（手机、电脑共用；电视不用）。纯 Java 网络：SSDP 用 UDP，描述和控制用 HTTP。不用第三方库。
 * [onDiscover]：找设备前要做的事（安卓拿 Wi-Fi 的组播锁，找完关掉）。
 */
class DlnaCastHost(private val onDiscover: () -> AutoCloseable? = { null }) : CastHost {
    override suspend fun discover(timeoutMs: Long): List<CastDevice> = withContext(Dispatchers.IO) {
        val lock = runCatching { onDiscover() }.getOrNull()
        try {
            val locations = LinkedHashSet<String>()
            // 每块真网卡各发一次：电脑上常有虚拟网卡 / VPN，只从默认网卡发可能找不到电视
            val sockets = localIpv4().mapNotNull { addr -> runCatching { DatagramSocket(InetSocketAddress(addr, 0)).apply { soTimeout = 120 } }.getOrNull() }
                .ifEmpty { listOfNotNull(runCatching { DatagramSocket().apply { soTimeout = 120 } }.getOrNull()) }
            try {
                val group = InetAddress.getByName("239.255.255.250")
                for (sock in sockets) for (target in listOf(AV_TRANSPORT, "urn:schemas-upnp-org:device:MediaRenderer:1")) {
                    val data = ssdpSearch(target).toByteArray(Charsets.US_ASCII)
                    repeat(2) { runCatching { sock.send(DatagramPacket(data, data.size, group, 1900)) } }
                }
                val end = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(8192)
                while (System.currentTimeMillis() < end) {
                    for (sock in sockets) {
                        val p = DatagramPacket(buf, buf.size)
                        try {
                            sock.receive(p)
                            ssdpLocation(String(p.data, 0, p.length, Charsets.UTF_8))?.let { locations += it }
                        } catch (_: SocketTimeoutException) {
                        } catch (_: Exception) {
                        }
                    }
                }
            } finally {
                sockets.forEach { runCatching { it.close() } }
            }
            locations.mapNotNull { loc -> runCatching { httpGet(loc)?.let { parseDeviceDescription(it, loc) } }.getOrNull() }.distinctBy { it.id }
        } finally {
            runCatching { lock?.close() }
        }
    }

    override suspend fun action(device: CastDevice, action: String, args: List<Pair<String, String>>): String? = withContext(Dispatchers.IO) {
        runCatching {
            val body = soapBody(action, args).toByteArray(Charsets.UTF_8)
            val conn = (URL(device.avTransportUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 10_000
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPACTION", "\"$AV_TRANSPORT#$action\"")
                setRequestProperty("Connection", "close")
            }
            try {
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }
                if (code in 200..299) text.orEmpty() else null
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3_000
            readTimeout = 5_000
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } else null
        } finally {
            conn.disconnect()
        }
    }

    /** 开着的、不是回环的、支持组播的网卡上的 IPv4 地址。 */
    private fun localIpv4(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback && it.supportsMulticast() }.getOrDefault(false) }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>() }
    }.getOrDefault(emptyList())
}
