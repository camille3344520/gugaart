package com.gugaart.ui.subtitle

import com.gugugaga.core.platformHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/*
 * 字幕搜索的网络缝（docs/subtitle-search.md §3、§7）：迅雷 / 射手的请求、字幕下载、取指纹的 Range 读。
 * 应用里是 Ktor + 平台的 HTTP 栈（`platformHttpClient`，系统代理照常生效）；测试换成脚本化的假的（不联网）。
 * Emby 自己的接口（服务器字幕源）不走这里，走上游的 `EmbyClient`（同一套鉴权、重试、脱敏）。
 */

/** 一次请求的回答：状态、正文、`Content-Type`（小写）。网络失败不抛，[failed] 写一句中文。 */
class NetText(val status: Int, val text: String, val contentType: String? = null, val failed: String? = null) {
    val ok: Boolean get() = failed == null && status in 200..299
}

class NetBytes(val status: Int, val bytes: ByteArray, val contentType: String? = null, val failed: String? = null) {
    val ok: Boolean get() = failed == null && status in 200..299
}

/**
 * 一次 Range 读：[bytes] 只在服务器回 206 时有；[total] = `Content-Range` 里的总长；[location] = 3xx 的跳转地址（**不自动跟随**，
 * 由调用方决定跳到别的主机时带什么头）。服务器回 200（不认 Range、要把整个文件发过来）时不读正文就断开。
 */
class RangeAnswer(val status: Int, val bytes: ByteArray?, val total: Long?, val location: String? = null)

interface SubtitleNet {
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Long = 20_000): NetText

    suspend fun postForm(url: String, form: List<Pair<String, String>>, timeoutMs: Long = 20_000): NetText

    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap(), timeoutMs: Long = 20_000): NetBytes

    suspend fun range(url: String, headers: Map<String, String>, span: ByteSpan, timeoutMs: Long = 8_000): RangeAnswer?

    /**
     * 下载但最多 [maxBytes]（弹幕预查用，§4.5）：超过 = `failed = "文件太大"`。默认实现下完整个再看大小（测试的假网络用它）；
     * Ktor 那一个读到上限就断开。
     */
    suspend fun getBytesCapped(url: String, maxBytes: Long, timeoutMs: Long): NetBytes {
        val r = getBytes(url, emptyMap(), timeoutMs)
        return if (r.ok && r.bytes.size > maxBytes) NetBytes(r.status, ByteArray(0), r.contentType, failed = "文件太大") else r
    }
}

/** 发给字幕站的 User-Agent（只写应用名，不带任何用户信息）。 */
const val SUBTITLE_USER_AGENT = "gugaart"

/** 正式的那一个：Ktor。[follow] 跟随重定向（字幕站、下载）；[noFollow] 不跟随（Range 读：跳到别的主机时不能带 Emby 的东西）。 */
class KtorSubtitleNet(
    private val follow: HttpClient = platformHttpClient(),
    private val noFollow: HttpClient = follow.config { followRedirects = false },
) : SubtitleNet {
    override suspend fun getText(url: String, headers: Map<String, String>, timeoutMs: Long): NetText = guard({ NetText(0, "", failed = it) }) {
        withTimeout(timeoutMs) {
            follow.prepareGet(url) {
                header(HttpHeaders.UserAgent, SUBTITLE_USER_AGENT)
                for ((k, v) in headers) header(k, v)
            }.execute { r -> NetText(r.status.value, r.bodyAsText(), r.headers[HttpHeaders.ContentType]?.lowercase()) }
        }
    }

    override suspend fun postForm(url: String, form: List<Pair<String, String>>, timeoutMs: Long): NetText = guard({ NetText(0, "", failed = it) }) {
        withTimeout(timeoutMs) {
            val r = follow.post(url) {
                header(HttpHeaders.UserAgent, SUBTITLE_USER_AGENT)
                setBody(FormDataContent(Parameters.build { for ((k, v) in form) append(k, v) }))
            }
            NetText(r.status.value, r.bodyAsText(), r.headers[HttpHeaders.ContentType]?.lowercase())
        }
    }

    override suspend fun getBytes(url: String, headers: Map<String, String>, timeoutMs: Long): NetBytes = guard({ NetBytes(0, ByteArray(0), failed = it) }) {
        withTimeout(timeoutMs) {
            follow.prepareGet(url) {
                header(HttpHeaders.UserAgent, SUBTITLE_USER_AGENT)
                for ((k, v) in headers) header(k, v)
            }.execute { r ->
                // 字幕最多几 MB；超过 16 MB 的不是字幕
                val len = r.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (len != null && len > MAX_SUBTITLE_BYTES) NetBytes(r.status.value, ByteArray(0), failed = "文件太大")
                else NetBytes(r.status.value, r.bodyAsBytes(), r.headers[HttpHeaders.ContentType]?.lowercase())
            }
        }
    }

    override suspend fun getBytesCapped(url: String, maxBytes: Long, timeoutMs: Long): NetBytes = guard({ NetBytes(0, ByteArray(0), failed = it) }) {
        withTimeout(timeoutMs) {
            follow.prepareGet(url) { header(HttpHeaders.UserAgent, SUBTITLE_USER_AGENT) }.execute { r ->
                val len = r.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                val ct = r.headers[HttpHeaders.ContentType]?.lowercase()
                if (len != null && len > maxBytes) return@execute NetBytes(r.status.value, ByteArray(0), ct, failed = "文件太大")
                // 没写长度（分块）：读到上限 + 1 就停，不把一个大文件整个拉下来
                val ch = r.bodyAsChannel()
                val out = ByteArrayOutputBuffer()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = ch.readAvailable(buf, 0, buf.size)
                    if (n < 0) break
                    out.write(buf, n)
                    if (out.size > maxBytes) return@execute NetBytes(r.status.value, ByteArray(0), ct, failed = "文件太大")
                }
                NetBytes(r.status.value, out.toByteArray(), ct)
            }
        }
    }

    override suspend fun range(url: String, headers: Map<String, String>, span: ByteSpan, timeoutMs: Long): RangeAnswer? = try {
        withTimeout(timeoutMs) {
            noFollow.prepareGet(url) {
                for ((k, v) in headers) header(k, v)
                header(HttpHeaders.Range, "bytes=${span.offset}-${span.end - 1}")
            }.execute { r ->
                val status = r.status.value
                val total = contentRangeTotal(r.headers[HttpHeaders.ContentRange])
                when {
                    status == 206 -> RangeAnswer(status, r.bodyAsBytes(), total)
                    status in 300..399 -> RangeAnswer(status, null, null, r.headers[HttpHeaders.Location])
                    // 200 = 不认 Range：不读正文（execute 结束时 Ktor 丢掉没读的部分、断开）
                    else -> RangeAnswer(status, null, total)
                }
            }
        }
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun <T> guard(fail: (String) -> T, block: suspend () -> T): T = try {
        block()
    } catch (e: TimeoutCancellationException) {
        fail("超时了")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        fail("连不上")
    }

    companion object {
        const val MAX_SUBTITLE_BYTES = 16L shl 20
    }
}

/** 攒字节（commonMain 没有 ByteArrayOutputStream）。 */
private class ByteArrayOutputBuffer {
    private var data = ByteArray(64 * 1024)
    var size = 0
        private set

    fun write(src: ByteArray, n: Int) {
        if (size + n > data.size) data = data.copyOf(maxOf(data.size * 2, size + n))
        src.copyInto(data, size, 0, n)
        size += n
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
}

/** `bytes 0-20479/14148643` → 14148643；`bytes *` 斜杠 `14148643` 也认；没有 / 总长是 `*` → null。纯函数，有单测。 */
fun contentRangeTotal(value: String?): Long? {
    val v = value?.trim() ?: return null
    val slash = v.lastIndexOf('/')
    if (slash < 0) return null
    return v.substring(slash + 1).trim().toLongOrNull()?.takeIf { it > 0 }
}
