package com.gugaart.ui.danmaku

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/*
 * 弹幕中转在本机回环上的那一层（桌面和安卓共用的 JVM 代码）：一个只听 127.0.0.1 的极小 HTTP/1.1 服务，
 * 每个连接一个请求（Connection: close），交给 [DanmakuRelayHandler]。
 *  - 地址里有一段随机口令（[token]），本机别的程序用不了它；端口和口令记在本机，下次启动尽量不变——上游的弹幕缓存按地址算键，
 *    地址一变缓存就作废；
 *  - 只认 GET / POST，请求体按 Content-Length 读；不支持分块上传（上游从不发）。
 */
class LoopbackRelayHost(
    /** 上次用的端口（null / 被占 = 系统给一个空的）。 */
    private val preferredPort: Int?,
    /** 地址里的随机口令（壳从本机存储里读；没有就生成一个存下）。 */
    private val token: String,
    /** 起好了：实际端口（壳记下来，下次优先用）。 */
    private val onPort: (Int) -> Unit = {},
) : RelayHost {
    @Volatile
    private var base: String? = null
    private var server: ServerSocket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Synchronized
    override fun start(handle: suspend (RelayRequest) -> RelayResponse): String {
        base?.let { return it }
        val loopback = InetAddress.getByName("127.0.0.1")
        val ss = preferredPort?.let { p ->
            try {
                ServerSocket(p, 50, loopback)
            } catch (_: Exception) {
                null
            }
        } ?: ServerSocket(0, 50, loopback)
        server = ss
        onPort(ss.localPort)
        val prefix = "/$token/api/v2"
        thread(isDaemon = true, name = "gugaart-danmaku-relay") {
            while (!ss.isClosed) {
                val c = try {
                    ss.accept()
                } catch (_: Exception) {
                    break
                }
                scope.launch { serve(c, prefix, handle) }
            }
        }
        return "http://127.0.0.1:${ss.localPort}$prefix".also { base = it }
    }

    private suspend fun serve(c: Socket, prefix: String, handle: suspend (RelayRequest) -> RelayResponse) {
        try {
            c.use { sock ->
                sock.soTimeout = 60_000
                val input = BufferedInputStream(sock.getInputStream())
                val line = readLine(input) ?: return
                val parts = line.split(' ')
                if (parts.size < 2) return
                val method = parts[0]
                val target = parts[1]
                val headers = HashMap<String, String>()
                while (true) {
                    val h = readLine(input) ?: break
                    if (h.isEmpty()) break
                    val i = h.indexOf(':')
                    if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
                }
                val len = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (len > 0) String(readExactly(input, len), Charsets.UTF_8) else null
                val resp = when {
                    method != "GET" && method != "POST" -> RelayResponse(405, """{"errorCode":405,"success":false}""")
                    !target.startsWith(prefix) -> RelayResponse(404, """{"errorCode":404,"success":false}""")
                    else -> handle(RelayRequest(method, target.removePrefix(prefix).ifEmpty { "/" }, body, headers))
                }
                val bytes = resp.body.toByteArray(Charsets.UTF_8)
                val head = "HTTP/1.1 ${resp.status} ${reason(resp.status)}\r\n" +
                    "Content-Type: ${resp.contentType}\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
                val out = sock.getOutputStream()
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.write(bytes)
                out.flush()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // 一个连接出错只影响它自己
        }
    }

    private fun readLine(input: InputStream): String? {
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString(Charsets.ISO_8859_1.name())
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
            if (buf.size() > 64 * 1024) return null
        }
        return buf.toString(Charsets.ISO_8859_1.name())
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(out, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) out else out.copyOf(off)
    }

    private fun reason(status: Int): String = when (status) {
        200 -> "OK"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        502 -> "Bad Gateway"
        503 -> "Service Unavailable"
        else -> "Status"
    }

    /** 测试用。 */
    fun stop() {
        server?.close()
        base = null
    }
}
