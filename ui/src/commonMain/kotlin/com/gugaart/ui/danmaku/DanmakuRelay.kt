package com.gugaart.ui.danmaku

import com.gugugaga.core.Transport
import com.gugugaga.core.TransportRequest
import com.gugugaga.core.TransportResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.time.TimeSource

/*
 * 多台弹幕服务器的中转（DanmakuServers.kt 说了为什么）。上游的弹幕客户端以为自己在跟一台 dandanplay 兼容的服务器说话，
 * 中转在本机回环上听着（壳里的 LoopbackRelayHost），每个请求这样处理：
 *  - POST /match：同时问所有服务器，谁先回「匹配上了」就用谁（「先匹配出来的优先显示」）；都没匹配上 = 把各台的候选合在一起；
 *  - /search/...：同时问，先回来的先排；第一份有结果的回来后再等 [graceMs]，晚到的就不等了；
 *  - /bangumi/{编号}、/comment/{编号}：从编号里解出是哪一台，原样发给那一台；
 *  - 回来的 animeId / episodeId / bangumiId 都编码成「编号 × 16 + 槽位号」（[encodeId]），所以各台的编号不会撞，下一个请求也知道发给谁。
 * 地址（带令牌）从不进日志、不进错误信息。
 */

/** 中转收到的一个请求：[target] = 去掉中转前缀后的路径 + 查询串（`/search/anime?keyword=…`）。 */
data class RelayRequest(val method: String, val target: String, val body: String?, val headers: Map<String, String>)

/** [contentType]：弹幕中转都是 JSON；字幕搜索的本机字幕（subtitle/LocalSubtitles.kt）借同一种回环服务回字幕文本。 */
data class RelayResponse(val status: Int, val body: String, val contentType: String = "application/json; charset=utf-8")

/** 中转在本机听着的那一层（壳里实现：回环上的一个小 HTTP 服务）。返回中转的 API 根地址。 */
fun interface RelayHost {
    fun start(handle: suspend (RelayRequest) -> RelayResponse): String
}

/** 编号编码：原编号 × 16 + 槽位号。纯函数，有单测。 */
fun encodeId(slot: Int, id: Long): Long = id * DANMAKU_SLOTS + slot

/** 解码：（槽位号, 原编号）。 */
fun decodeId(n: Long): Pair<Int, Long> = n.mod(DANMAKU_SLOTS.toLong()).toInt() to n.floorDiv(DANMAKU_SLOTS.toLong())

/** 字符串编号（bangumiId 可能不是数字）：数字照编码；不是数字 = 前面加「g<槽位>-」。 */
fun encodeTextId(slot: Int, id: String): String = id.toLongOrNull()?.let { encodeId(slot, it).toString() } ?: "g$slot-$id"

fun decodeTextId(id: String): Pair<Int, String>? {
    id.toLongOrNull()?.let { n -> return decodeId(n).let { (s, v) -> s to v.toString() } }
    val m = Regex("^g(\\d{1,2})-(.*)$").matchEntire(id) ?: return null
    return m.groupValues[1].toInt() to m.groupValues[2]
}

private val ID_KEYS = setOf("animeId", "episodeId", "bangumiId")

/** 把回应里的 animeId / episodeId / bangumiId 都编码成这一台的（数字还是数字，字符串还是字符串）。纯函数，有单测。 */
fun rewriteIds(e: JsonElement, slot: Int): JsonElement = when (e) {
    is JsonObject -> JsonObject(e.mapValues { (k, v) -> if (k in ID_KEYS && v is JsonPrimitive) rewriteId(v, slot) else rewriteIds(v, slot) })
    is JsonArray -> JsonArray(e.map { rewriteIds(it, slot) })
    else -> e
}

private fun rewriteId(v: JsonPrimitive, slot: Int): JsonPrimitive = when {
    v.isString -> JsonPrimitive(encodeTextId(slot, v.content))
    v.longOrNull != null -> JsonPrimitive(encodeId(slot, v.longOrNull!!))
    else -> v
}

private fun parseObject(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (_: Exception) {
    null
}

private fun ok(r: TransportResponse?): Boolean = r != null && r.status in 200..299

/** 一份回应里 [key] 那个数组有没有东西。 */
private fun hasItems(r: TransportResponse?, key: String): Boolean = ok(r) && ((parseObject(r!!.text)?.get(key) as? JsonArray)?.isNotEmpty() == true)

private fun error(status: Int, message: String) = RelayResponse(status, """{"errorCode":$status,"success":false,"errorMessage":"$message"}""")

/**
 * 中转的处理（纯逻辑，经 [Transport] 发出去；单测用假的 Transport）。[servers] = 现在的清单（有先后）。
 */
class DanmakuRelayHandler(
    private val servers: suspend () -> List<DanmakuServer>,
    private val transport: Transport,
    /** 搜索：第一份有结果的回来后再等多久。 */
    private val graceMs: Long = 1_200,
    /** 搜索 / 匹配：每台最多等多久（上游自己等 30 秒，中转要在那之前回话）。 */
    private val searchTimeoutMs: Long = 27_000,
    /** 弹幕：上游等 45 秒。 */
    private val commentTimeoutMs: Long = 44_000,
) {
    suspend fun handle(req: RelayRequest): RelayResponse {
        val list = servers()
        if (list.isEmpty()) return error(503, "no danmaku server")
        val path = req.target.substringBefore('?')
        return when {
            path == "/match" && req.method.equals("POST", ignoreCase = true) -> race(list, req)
            path.startsWith("/search/") -> gather(list, req, "animes")
            path.startsWith("/bangumi/") -> routed(list, req, "/bangumi/", rewrite = true)
            path.startsWith("/comment/") -> routed(list, req, "/comment/", rewrite = false)
            // 不认识的路由：交给第一台（编号照样编码）
            else -> one(list.first(), req, req.target, searchTimeoutMs, rewrite = true)
        }
    }

    private suspend fun send(s: DanmakuServer, req: RelayRequest, target: String, timeoutMs: Long): TransportResponse? = try {
        val headers = LinkedHashMap<String, String>()
        req.headers["user-agent"]?.let { headers["User-Agent"] = it }
        req.headers["content-type"]?.let { headers["Content-Type"] = it }
        headers["Accept"] = "application/json"
        withTimeoutOrNull(timeoutMs) {
            transport.execute(TransportRequest(url = s.url.trimEnd('/') + target, method = req.method.uppercase(), headers = headers, body = req.body, timeoutMs = timeoutMs))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun one(s: DanmakuServer, req: RelayRequest, target: String, timeoutMs: Long, rewrite: Boolean): RelayResponse {
        val r = send(s, req, target, timeoutMs) ?: return error(502, "danmaku server unreachable")
        if (!rewrite || !ok(r)) return RelayResponse(r.status, r.text)
        val j = try {
            Json.parseToJsonElement(r.text)
        } catch (_: Exception) {
            return RelayResponse(r.status, r.text)
        }
        return RelayResponse(r.status, rewriteIds(j, s.slot).toString())
    }

    /** /bangumi/{编号}、/comment/{编号}：解出是哪一台，发给它。 */
    private suspend fun routed(list: List<DanmakuServer>, req: RelayRequest, prefix: String, rewrite: Boolean): RelayResponse {
        val path = req.target.substringBefore('?')
        val query = req.target.substringAfter('?', "")
        val (slot, id) = decodeTextId(decodePath(path.removePrefix(prefix))) ?: return error(404, "unknown id")
        val s = list.firstOrNull { it.slot == slot } ?: return error(404, "unknown id")
        val target = prefix + encodePath(id) + if (query.isNotEmpty()) "?$query" else ""
        return one(s, req, target, if (prefix == "/comment/") commentTimeoutMs else searchTimeoutMs, rewrite)
    }

    /** 搜索：同时问，先回来的先排；第一份有结果的回来后再等 [graceMs]。 */
    private suspend fun gather(list: List<DanmakuServer>, req: RelayRequest, key: String): RelayResponse = coroutineScope {
        val ch = Channel<Pair<DanmakuServer, TransportResponse?>>(list.size)
        val jobs = list.map { s -> launch { ch.send(s to send(s, req, req.target, searchTimeoutMs)) } }
        val got = ArrayList<Pair<DanmakuServer, TransportResponse?>>()
        var firstHit: TimeSource.Monotonic.ValueTimeMark? = null
        while (got.size < list.size) {
            val hit = firstHit
            val next = if (hit == null) {
                ch.receive()
            } else {
                val left = graceMs - hit.elapsedNow().inWholeMilliseconds
                if (left <= 0) break
                withTimeoutOrNull(left) { ch.receive() } ?: break
            }
            got += next
            if (firstHit == null && hasItems(next.second, key)) firstHit = TimeSource.Monotonic.markNow()
        }
        jobs.forEach { it.cancel() }
        merge(got, key, extra = emptyMap())
    }

    /** /match：谁先回「匹配上了」就用谁；都没有 = 各台的候选合在一起。 */
    private suspend fun race(list: List<DanmakuServer>, req: RelayRequest): RelayResponse = coroutineScope {
        val ch = Channel<Pair<DanmakuServer, TransportResponse?>>(list.size)
        val jobs = list.map { s -> launch { ch.send(s to send(s, req, req.target, searchTimeoutMs)) } }
        val got = ArrayList<Pair<DanmakuServer, TransportResponse?>>()
        var winner: RelayResponse? = null
        while (got.size < list.size) {
            val next = ch.receive()
            got += next
            val (s, r) = next
            val o = if (ok(r)) parseObject(r!!.text) else null
            if (o != null && (o["isMatched"] as? JsonPrimitive)?.booleanOrNull == true && (o["matches"] as? JsonArray)?.isNotEmpty() == true) {
                winner = RelayResponse(r!!.status, rewriteIds(o, s.slot).toString())
                break
            }
        }
        jobs.forEach { it.cancel() }
        winner ?: merge(got, "matches", extra = mapOf("isMatched" to JsonPrimitive(false)))
    }

    /** 把几台的回应合成一份：第一份成功的做底，[key] 数组按到达先后接起来（各自编码）。一份成功的都没有 = 第一份原样。 */
    private fun merge(got: List<Pair<DanmakuServer, TransportResponse?>>, key: String, extra: Map<String, JsonElement>): RelayResponse {
        val okOnes = got.mapNotNull { (s, r) -> if (ok(r)) parseObject(r!!.text)?.let { s to it } else null }
        if (okOnes.isEmpty()) {
            val first = got.firstNotNullOfOrNull { it.second }
            return if (first != null) RelayResponse(first.status, first.text) else error(502, "danmaku server unreachable")
        }
        val (baseSlot, base) = okOnes.first()
        val items = okOnes.flatMap { (s, o) -> (o[key] as? JsonArray).orEmpty().map { rewriteIds(it, s.slot) } }
        val merged = LinkedHashMap<String, JsonElement>()
        for ((k, v) in base) merged[k] = if (k == key) JsonArray(items) else rewriteIds(v, baseSlot.slot)
        if (key !in merged) merged[key] = JsonArray(items)
        merged.putAll(extra)
        return RelayResponse(200, JsonObject(merged).toString())
    }
}

private fun decodePath(s: String): String = com.gugugaga.core.decodeURIComponentLenient(s)

private fun encodePath(s: String): String = com.gugugaga.core.encodeURIComponent(s)

/**
 * 中转本身（进程里一个）：壳 [install] 回环上的那一层和发请求的 [Transport]；两台以上服务器时 [activeBase] 起中转、给出地址。
 * 起不来（端口、权限）= null：上游照旧直连第一台，弹幕至少还有。
 */
object DanmakuRelay {
    private var host: RelayHost? = null
    private var handler: DanmakuRelayHandler? = null
    private var base: String? = null
    private val lock = Mutex()

    fun install(host: RelayHost, transport: Transport) {
        this.host = host
        handler = DanmakuRelayHandler({ DanmakuServers.current() }, transport)
    }

    suspend fun activeBase(): String? {
        if (DanmakuServers.current().size < 2) return null
        val h = host ?: return null
        val handle = handler ?: return null
        return lock.withLock {
            base ?: try {
                h.start { req -> handle.handle(req) }.also { base = it }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
}
