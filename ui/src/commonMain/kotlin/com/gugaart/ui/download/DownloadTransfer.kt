package com.gugaart.ui.download

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/*
 * 下载一个文件（1.6 批次 E）：断点续传（HTTP Range：`bytes=<已有>-`）、失败重试 3 次、30 秒没有数据算断了。
 * 网络这一层是 [DownloadTransport]（应用里 = ktor，和 Emby 请求同一套 HTTP 栈、同一个系统代理；测试里给假的）。
 * 「怎么看服务器的回答」是纯函数 [decideOpen]，有单测。
 */

/** 一次要下的地址和头。地址每一次尝试都现算（strm 的签名链接会过期；Emby 的地址带令牌，不存盘、不打印）。 */
class DownloadRequest(val url: String, val headers: Map<String, String>) {
    override fun toString(): String = "DownloadRequest(headers=${headers.keys})"
}

/** 服务器的回答：状态码、`Content-Length`、`Content-Range`，和读正文的办法（读到头 = -1）。 */
class DownloadResponse(
    val status: Int,
    val contentLength: Long?,
    val contentRange: String?,
    val read: suspend (ByteArray) -> Int,
)

interface DownloadTransport {
    /** 从第 [from] 个字节开始要 [url]（[from] > 0 时带 `Range: bytes=<from>-`），回答交给 [use]。 */
    suspend fun <T> open(url: String, headers: Map<String, String>, from: Long, use: suspend (DownloadResponse) -> T): T
}

/** ktor（上游 `platformHttpClient()`：安卓 / 电脑是 OkHttp，iPhone 是 Darwin）。跟随跳转（strm 的 302 链）。 */
class KtorDownloadTransport(private val client: HttpClient) : DownloadTransport {
    override suspend fun <T> open(url: String, headers: Map<String, String>, from: Long, use: suspend (DownloadResponse) -> T): T =
        client.prepareGet(url) {
            headers.forEach { (k, v) -> header(k, v) }
            if (from > 0) header(HttpHeaders.Range, "bytes=$from-")
        }.execute { resp ->
            val channel = resp.bodyAsChannel()
            use(
                DownloadResponse(resp.status.value, resp.contentLength(), resp.headers[HttpHeaders.ContentRange]) { buf ->
                    val n = channel.readAvailable(buf, 0, buf.size)
                    if (n <= 0 && channel.isClosedForRead) -1 else n.coerceAtLeast(0)
                },
            )
        }
}

/** 看服务器怎么回答「从第 have 个字节起」。 */
sealed interface Opened {
    /** 接着写（206，起点就是 have）。[total] = 整个文件多大（知道的话）。 */
    data class Append(val total: Long?) : Opened

    /** 从头写（200：服务器不认 Range，或者 have = 0）。 */
    data class Restart(val total: Long?) : Opened

    /** 416：have 已经是整个文件了。 */
    data object Complete : Opened

    /** 不给。[retriable]：等一下再试有用（5xx、408、429、起点对不上的 206）。 */
    data class Refused(val status: Int, val retriable: Boolean) : Opened
}

/** `Content-Range: bytes 100-999/1000` → (100, 999, 1000)；总长是 `*` → 第三项 null；读不懂 = null。 */
fun parseContentRange(v: String?): Triple<Long, Long, Long?>? {
    val m = Regex("""^\s*bytes\s+(\d+)-(\d+)/(\d+|\*)\s*$""", RegexOption.IGNORE_CASE).find(v ?: return null) ?: return null
    val start = m.groupValues[1].toLongOrNull() ?: return null
    val end = m.groupValues[2].toLongOrNull() ?: return null
    if (end < start) return null
    return Triple(start, end, m.groupValues[3].toLongOrNull())
}

/** 纯函数：要了「从 [have] 起」，服务器回 [status]（+ 两个头），接下来怎么办。 */
fun decideOpen(status: Int, have: Long, contentLength: Long?, contentRange: String?): Opened = when {
    status == 206 -> {
        val r = parseContentRange(contentRange)
        when {
            r == null -> if (have == 0L) Opened.Restart(contentLength) else Opened.Refused(status, retriable = true)
            r.first == have -> Opened.Append(r.third ?: contentLength?.let { have + it })
            r.first == 0L -> Opened.Restart(r.third ?: contentLength)
            else -> Opened.Refused(status, retriable = true)
        }
    }
    status in 200..299 -> Opened.Restart(contentLength?.takeIf { it >= 0 })
    status == 416 -> if (have > 0) Opened.Complete else Opened.Refused(status, retriable = false)
    status == 408 || status == 429 || status >= 500 -> Opened.Refused(status, retriable = true)
    else -> Opened.Refused(status, retriable = false)
}

/** 不重试的拒绝给用户的一句话。 */
fun refusalReason(status: Int): String = when (status) {
    401 -> "登录过期"
    403 -> "没有下载权限"
    404, 410 -> "服务器上没有这个文件"
    else -> "服务器拒绝（$status）"
}

/** 下载的结果。 */
sealed interface TransferResult {
    data class Done(val total: Long) : TransferResult
    data class Failed(val reason: String, val status: Int? = null) : TransferResult
}

/** 第 [failures] 次失败后等多久再试：2 s、4 s、8 s。 */
fun retryDelayMs(failures: Int): Long = 2_000L shl (failures - 1).coerceIn(0, 4)

/** 一次尝试里写够这么多才算「有进展」（重试计数清零）：网很差、断断续续也能下完，又不会被一个只给几个字节就断的服务器拖住不放。 */
const val PROGRESS_RESETS_RETRIES = 1L shl 20

private sealed interface Attempt {
    data object Complete : Attempt
    data class Retry(val reason: String, val progressed: Boolean) : Attempt
    data class Fatal(val reason: String, val status: Int?) : Attempt
}

/**
 * 把 [request] 下到 [part]（已有的部分接着下），下完改名成 [finalName]。
 * [retries]：连续失败（没有进展）几次后放弃——「失败重试 3 次」= 最多试 4 次。[onProgress] 在写盘的线程上叫（调用方自己节流）。
 */
suspend fun runTransfer(
    transport: DownloadTransport,
    storage: DownloadStorage,
    part: String,
    finalName: String,
    knownTotal: Long,
    request: suspend () -> DownloadRequest,
    onProgress: (done: Long, total: Long) -> Unit,
    retries: Int = 3,
    stallMs: Long = 30_000,
    sleep: suspend (Long) -> Unit = { delay(it) },
): TransferResult {
    var total = knownTotal.coerceAtLeast(0)
    var failures = 0
    while (true) {
        val have = storage.length(part).coerceAtLeast(0)
        if (total > 0 && have > total) storage.delete(part)
        val attempt: Attempt = try {
            val req = request()
            val start = storage.length(part).coerceAtLeast(0)
            transport.open(req.url, req.headers, start) { r -> attemptBody(r, storage, part, start, total, stallMs, onProgress) { total = it } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Attempt.Retry("网络断了", progressed = false)
        }
        when (attempt) {
            Attempt.Complete -> return if (storage.rename(part, finalName)) TransferResult.Done(total) else TransferResult.Failed("存不下来")
            is Attempt.Fatal -> return TransferResult.Failed(attempt.reason, attempt.status)
            is Attempt.Retry -> {
                failures = if (attempt.progressed) 0 else failures + 1
                if (failures > retries) return TransferResult.Failed(attempt.reason)
                sleep(retryDelayMs(failures.coerceAtLeast(1)))
            }
        }
    }
}

private suspend fun attemptBody(
    r: DownloadResponse,
    storage: DownloadStorage,
    part: String,
    have: Long,
    knownTotal: Long,
    stallMs: Long,
    onProgress: (Long, Long) -> Unit,
    setTotal: (Long) -> Unit,
): Attempt {
    val d = decideOpen(r.status, have, r.contentLength, r.contentRange)
    val total: Long
    val start: Long
    when (d) {
        Opened.Complete -> return if (knownTotal <= 0 || have == knownTotal) {
            setTotal(have)
            Attempt.Complete
        } else {
            storage.delete(part)
            Attempt.Retry("文件不完整", progressed = false)
        }
        is Opened.Refused -> return if (d.retriable) Attempt.Retry("服务器忙（${d.status}）", progressed = false) else Attempt.Fatal(refusalReason(d.status), d.status)
        is Opened.Append -> {
            start = have
            total = d.total?.takeIf { it > 0 } ?: knownTotal
        }
        is Opened.Restart -> {
            start = 0
            total = d.total?.takeIf { it > 0 } ?: knownTotal
        }
    }
    if (total > 0) {
        setTotal(total)
        val free = storage.freeBytes()
        if (free != null && free < total - start) return Attempt.Fatal("空间不够", null)
    }
    val sink = storage.open(part, append = d is Opened.Append)
    var written = start
    try {
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = withTimeoutOrNull(stallMs) { r.read(buf) } ?: return Attempt.Retry("网络断了", written - start >= PROGRESS_RESETS_RETRIES)
            if (n < 0) break
            if (n == 0) continue
            sink.write(buf, 0, n)
            written += n
            onProgress(written, total)
        }
    } finally {
        sink.close()
    }
    val progressed = written - start >= PROGRESS_RESETS_RETRIES
    return when {
        total > 0 && written < total -> Attempt.Retry("网络断了", progressed)
        total > 0 && written > total -> {
            storage.delete(part)
            Attempt.Retry("文件不完整", progressed = false)
        }
        else -> {
            setTotal(written)
            Attempt.Complete
        }
    }
}
