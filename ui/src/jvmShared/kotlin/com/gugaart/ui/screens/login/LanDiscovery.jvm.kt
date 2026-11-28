package com.gugaart.ui.screens.login

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/** 壳挂的多播锁钩子（Android 上部分机型收 UDP 广播需要 Wi-Fi MulticastLock；默认 null）。 */
object JvmLanDiscoveryLock {
    var acquire: () -> AutoCloseable? = { null }
}

/**
 * JVM 共用（安卓手机、安卓电视、Windows、Mac）的 UDP 7359 局域网发现。
 * [targets] 为 null 时向 `255.255.255.255` 及各活动网卡的广播地址发包；测试可传 `listOf("127.0.0.1")` + Mock 端口，绝不碰真 Emby。
 */
class JvmLanDiscovery(
    private val port: Int = EMBY_DISCOVERY_PORT,
    private val targets: List<String>? = null,
) : LanDiscovery {
    override fun discover(timeoutMs: Long): Flow<DiscoveredServer> = channelFlow {
        withContext(Dispatchers.IO) {
            val lock = runCatching { JvmLanDiscoveryLock.acquire() }.getOrNull()
            try {
                val sockets = if (targets == null) {
                    localSockets().ifEmpty { listOfNotNull(openWildcardSocket()) }
                } else {
                    listOfNotNull(openWildcardSocket())
                }
                if (sockets.isEmpty()) return@withContext
                try {
                    val query = EMBY_DISCOVERY_QUERY.encodeToByteArray()
                    val dests: List<InetAddress> = if (targets != null) {
                        targets.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
                    } else {
                        broadcastAddresses()
                    }
                    fun broadcastOnce() {
                        for (sock in sockets) {
                            for (dst in dests) {
                                runCatching { sock.send(DatagramPacket(query, query.size, dst, port)) }
                            }
                        }
                    }
                    broadcastOnce()
                    launch {
                        delay(250L)
                        broadcastOnce()
                    }
                    val seen = LinkedHashSet<String>()
                    val end = System.currentTimeMillis() + timeoutMs
                    val buf = ByteArray(4096)
                    while (System.currentTimeMillis() < end) {
                        for (sock in sockets) {
                            val pkt = DatagramPacket(buf, buf.size)
                            try {
                                sock.receive(pkt)
                                val text = pkt.data.decodeToString(0, pkt.length)
                                val parsed = parseDiscoveryPacket(text) ?: continue
                                val key = "${parsed.id}|${parsed.address}"
                                if (seen.add(key)) trySend(parsed)
                            } catch (_: SocketTimeoutException) {
                            } catch (_: Exception) {
                            }
                        }
                    }
                } finally {
                    sockets.forEach { runCatching { it.close() } }
                }
            } finally {
                runCatching { lock?.close() }
            }
        }
    }

    private fun openWildcardSocket(): DatagramSocket? = runCatching {
        DatagramSocket().apply {
            broadcast = true
            soTimeout = 100
        }
    }.getOrNull()

    private fun localSockets(): List<DatagramSocket> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { runCatching { it.isUp && !it.isLoopback }.getOrDefault(false) }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>() }
            .mapNotNull { addr ->
                runCatching {
                    DatagramSocket(InetSocketAddress(addr, 0)).apply {
                        broadcast = true
                        soTimeout = 100
                    }
                }.getOrNull()
            }
    }.getOrDefault(emptyList())

    private fun broadcastAddresses(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            for (ni in NetworkInterface.getNetworkInterfaces().toList()) {
                if (!runCatching { ni.isUp && !ni.isLoopback }.getOrDefault(false)) continue
                for (ia in ni.interfaceAddresses) {
                    val b = ia.broadcast ?: continue
                    if (b is Inet4Address) out.add(b)
                }
            }
        }
        return out.toList()
    }
}

actual fun platformLanDiscovery(): LanDiscovery? = JvmLanDiscovery()
