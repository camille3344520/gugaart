package com.gugaart.ui.danmaku

import com.gugaart.ui.net.fill
import com.gugaart.ui.net.ipv4
import com.gugaart.ui.net.ntoh16
import com.gugaart.ui.net.socketOptions
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import platform.Foundation.NSLock
import platform.posix.AF_INET
import platform.posix.EINTR
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar

/*
 * 弹幕中转在本机回环上的那一层，iOS 版（D-lead-31；桌面 / 安卓是 jvmShared 的 LoopbackRelayHost，行为一样）：一个只听 127.0.0.1 的极小
 * HTTP/1.1 服务，每个连接一个请求（Connection: close），交给 DanmakuRelayHandler。地址里一段随机口令；端口和口令记在本机。
 * 只认 GET / POST，请求体按 Content-Length 读。iOS 在应用挂起时可能收回监听的套接字：accept 出错就在同一个端口重新听。
 */
@OptIn(ExperimentalForeignApi::class, DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
class IosLoopbackRelayHost(
    /** 上次用的端口（null / 被占 = 系统给一个空的）。 */
    private val preferredPort: Int?,
    /** 地址里的随机口令。 */
    private val token: String,
    /** 起好了：实际端口（壳记下来，下次优先用）。 */
    private val onPort: (Int) -> Unit = {},
) : RelayHost {
    private val lock = NSLock()
    private var base: String? = null
    private var stopped = false
    private var fd = -1
    private var port = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val acceptThread = newSingleThreadContext("gugaart-danmaku-relay")

    override fun start(handle: suspend (RelayRequest) -> RelayResponse): String {
        lock.lock()
        try {
            base?.let { return it }
            val (s, p) = openListener(preferredPort) ?: openListener(0) ?: error("danmaku relay: cannot listen on 127.0.0.1")
            fd = s
            port = p
            onPort(p)
            val prefix = "/$token/api/v2"
            CoroutineScope(acceptThread).launch { acceptLoop(prefix, handle) }
            return "http://127.0.0.1:$p$prefix".also { base = it }
        } finally {
            lock.unlock()
        }
    }

    /** socket → bind 127.0.0.1:[want]（0 = 系统给）→ listen；返回（套接字, 实际端口）。 */
    private fun openListener(want: Int?): Pair<Int, Int>? = memScoped {
        val s = socket(AF_INET, SOCK_STREAM, 0)
        if (s < 0) return null
        socketOptions(s, recvTimeoutMs = null, reuse = true)
        val addr = alloc<sockaddr_in>()
        addr.fill(ipv4(127, 0, 0, 1), want ?: 0)
        if (bind(s, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0 || listen(s, 50) != 0) {
            close(s)
            return null
        }
        val len = alloc<socklen_tVar>()
        len.value = sizeOf<sockaddr_in>().convert()
        getsockname(s, addr.ptr.reinterpret(), len.ptr)
        s to ntoh16(addr.sin_port)
    }

    private fun acceptLoop(prefix: String, handle: suspend (RelayRequest) -> RelayResponse) {
        while (true) {
            val listening = fd
            if (listening < 0) return
            val c = accept(listening, null, null)
            if (c < 0) {
                if (stopped) return
                if (errno == EINTR) continue
                // the system took the listening socket back (the app was suspended): listen again on the same port
                close(listening)
                val again = openListener(port)
                if (again == null) {
                    fd = -1
                    lock.lock()
                    base = null
                    lock.unlock()
                    return
                }
                fd = again.first
                continue
            }
            socketOptions(c, recvTimeoutMs = 60_000, sendTimeoutMs = 60_000)
            scope.launch { serve(c, prefix, handle) }
        }
    }

    private suspend fun serve(c: Int, prefix: String, handle: suspend (RelayRequest) -> RelayResponse) {
        try {
            val req = readRequest(c) ?: return
            val (head, body) = req
            val lines = head.split("\r\n")
            val parts = lines.firstOrNull()?.split(' ') ?: return
            if (parts.size < 2) return
            val method = parts[0]
            val target = parts[1]
            val headers = HashMap<String, String>()
            for (h in lines.drop(1)) {
                val i = h.indexOf(':')
                if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
            }
            val resp = when {
                method != "GET" && method != "POST" -> RelayResponse(405, """{"errorCode":405,"success":false}""")
                !target.startsWith(prefix) -> RelayResponse(404, """{"errorCode":404,"success":false}""")
                else -> handle(RelayRequest(method, target.removePrefix(prefix).ifEmpty { "/" }, body, headers))
            }
            val bytes = resp.body.encodeToByteArray()
            val top = "HTTP/1.1 ${resp.status} ${reason(resp.status)}\r\n" +
                "Content-Type: ${resp.contentType}\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"
            sendAll(c, top.encodeToByteArray() + bytes)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            // one connection's trouble stays with it
        } finally {
            close(c)
        }
    }

    /** 读到头部结束（\r\n\r\n），再按 Content-Length 读请求体。返回（头部文字, 请求体 / null）。 */
    private fun readRequest(c: Int): Pair<String, String?>? {
        val buf = ByteArray(16 * 1024)
        var data = ByteArray(0)
        var headEnd = -1
        while (headEnd < 0) {
            val n = buf.usePinned { recv(c, it.addressOf(0), buf.size.convert(), 0) }.toInt()
            if (n <= 0) return null
            data += buf.copyOf(n)
            headEnd = indexOfCrlfCrlf(data)
            if (data.size > 64 * 1024 && headEnd < 0) return null
        }
        val head = data.copyOf(headEnd).decodeToString()
        val len = Regex("""(?im)^content-length:\s*(\d+)""").find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        var body = data.copyOfRange(headEnd + 4, data.size)
        while (body.size < len) {
            val n = buf.usePinned { recv(c, it.addressOf(0), buf.size.convert(), 0) }.toInt()
            if (n <= 0) break
            body += buf.copyOf(n)
        }
        return head to (if (len > 0) body.copyOf(minOf(len, body.size)).decodeToString() else null)
    }

    private fun indexOfCrlfCrlf(d: ByteArray): Int {
        for (i in 0..d.size - 4) if (d[i] == 13.toByte() && d[i + 1] == 10.toByte() && d[i + 2] == 13.toByte() && d[i + 3] == 10.toByte()) return i
        return -1
    }

    private fun sendAll(c: Int, bytes: ByteArray) {
        var off = 0
        bytes.usePinned { pin ->
            while (off < bytes.size) {
                val n = send(c, pin.addressOf(off), (bytes.size - off).convert(), 0).toInt()
                if (n <= 0) return
                off += n
            }
        }
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
        lock.lock()
        try {
            stopped = true
            val s = fd
            fd = -1
            base = null
            if (s >= 0) close(s)
        } finally {
            lock.unlock()
        }
    }
}
