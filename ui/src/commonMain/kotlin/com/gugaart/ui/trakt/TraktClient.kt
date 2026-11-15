package com.gugaart.ui.trakt

import com.gugaart.ui.data.GugaPrefs
import com.gugugaga.core.KeyValueStore
import com.gugugaga.core.Transport
import com.gugugaga.core.TransportRequest
import com.gugugaga.core.TransportResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * Trakt（用户 2026-11-15：「添加 连接trakt功能」→ 问过：「看了就记到 Trakt」+「Trakt 已看同步回 Emby」；
 * Key：用户在 trakt.tv 注册应用，Client ID / Secret 放 .env，正式包内嵌）。
 *
 * 连接用 Trakt 的设备码登录（三端一样，电视也方便）：拿到一串短码 → 用户在手机 / 电脑浏览器打开 trakt.tv/activate 输入 →
 * 应用每隔几秒问一次，拿到令牌。令牌存在系统安全存储（[TraktTokens.KEY]），快过期了自动续。
 * 地址固定是 api.trakt.tv；令牌、Client Secret 从不进日志。
 */

const val TRAKT_API = "https://api.trakt.tv"

/** 编进包里的 Trakt 应用（壳从 .env 的 TRAKT_CLIENT_ID / TRAKT_CLIENT_SECRET 来；没配 = 空 = 这一版不能连 Trakt）。 */
data class TraktApp(val clientId: String, val clientSecret: String) {
    val configured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}

/** 存着的令牌 + 用户名。[expiresAtMs] = 过期时刻（UTC 毫秒）。 */
data class TraktTokens(val access: String, val refresh: String, val expiresAtMs: Long, val user: String?) {
    fun needsRefresh(nowMs: Long): Boolean = nowMs > expiresAtMs - 7L * 86_400_000L

    fun encode(): String = buildJsonObject {
        put("access", access)
        put("refresh", refresh)
        put("expiresAt", expiresAtMs)
        if (user != null) put("user", user)
    }.toString()

    override fun toString(): String = "TraktTokens(user=$user, expiresAt=$expiresAtMs)"

    companion object {
        const val KEY = "gugaart.trakt.v1"

        fun parse(raw: String?): TraktTokens? {
            val o = GugaPrefs.parseObject(raw) ?: return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            val access = s("access")?.takeIf { it.isNotEmpty() } ?: return null
            return TraktTokens(access, s("refresh").orEmpty(), (o["expiresAt"] as? JsonPrimitive)?.longOrNull ?: 0L, s("user"))
        }

        suspend fun load(secure: KeyValueStore): TraktTokens? = parse(secure.get(KEY))
        suspend fun save(secure: KeyValueStore, t: TraktTokens?) = if (t == null) secure.remove(KEY) else secure.set(KEY, t.encode())
    }
}

/** 设备码：[userCode] 给人输入，[verificationUrl] 是要打开的网址。 */
data class TraktDeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val expiresInSec: Int, val intervalSec: Int)

/** 问一次「授权了没有」的结果。 */
sealed interface TraktPoll {
    data class Done(val tokens: TraktTokens) : TraktPoll
    data object Pending : TraktPoll
    data object SlowDown : TraktPoll
    /** 码过期 / 被拒 / 用过了 / 不认识：要重新开始。 */
    data class Failed(val reason: String) : TraktPoll
}

/** Trakt 回的令牌 → [TraktTokens]。纯函数，有单测。 */
fun parseTraktTokens(o: JsonObject, user: String?, nowMs: Long): TraktTokens? {
    fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    val access = s("access_token") ?: return null
    val created = (o["created_at"] as? JsonPrimitive)?.longOrNull?.times(1000) ?: nowMs
    val expiresIn = (o["expires_in"] as? JsonPrimitive)?.longOrNull ?: 7_776_000L
    return TraktTokens(access, s("refresh_token").orEmpty(), created + expiresIn * 1000, user)
}

/** 轮询的状态码 → 结果（Trakt 的设备码约定）。纯函数，有单测。 */
fun traktPollOf(status: Int): TraktPoll? = when (status) {
    400 -> TraktPoll.Pending
    429 -> TraktPoll.SlowDown
    404 -> TraktPoll.Failed("这个码不对，重新开始")
    409 -> TraktPoll.Failed("这个码已经用过了")
    410 -> TraktPoll.Failed("码过期了，重新开始")
    418 -> TraktPoll.Failed("在 Trakt 上点了拒绝")
    else -> null
}

/** 极简 Trakt API（只用到的几个）。 */
class TraktClient(private val transport: Transport, private val app: TraktApp) {
    private fun headers(token: String? = null): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("trakt-api-version", "2")
        put("trakt-api-key", app.clientId)
        if (token != null) put("Authorization", "Bearer $token")
    }

    private suspend fun post(path: String, body: JsonObject, token: String? = null): TransportResponse =
        transport.execute(TransportRequest(TRAKT_API + path, "POST", headers(token), body.toString(), 20_000))

    private suspend fun get(path: String, token: String): TransportResponse =
        transport.execute(TransportRequest(TRAKT_API + path, "GET", headers(token), null, 30_000))

    private fun obj(r: TransportResponse): JsonObject? = try {
        Json.parseToJsonElement(r.text) as? JsonObject
    } catch (_: Exception) {
        null
    }

    private fun arr(r: TransportResponse): JsonArray = try {
        Json.parseToJsonElement(r.text) as? JsonArray ?: JsonArray(emptyList())
    } catch (_: Exception) {
        JsonArray(emptyList())
    }

    /** 第一步：拿设备码。失败 = null。 */
    suspend fun deviceCode(): TraktDeviceCode? {
        val r = post("/oauth/device/code", buildJsonObject { put("client_id", app.clientId) })
        if (r.status !in 200..299) return null
        val o = obj(r) ?: return null
        fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
        return TraktDeviceCode(
            deviceCode = s("device_code") ?: return null,
            userCode = s("user_code") ?: return null,
            verificationUrl = s("verification_url") ?: "https://trakt.tv/activate",
            expiresInSec = (o["expires_in"] as? JsonPrimitive)?.intOrNull ?: 600,
            intervalSec = (o["interval"] as? JsonPrimitive)?.intOrNull ?: 5,
        )
    }

    /** 第二步：问一次授权了没有。 */
    suspend fun poll(code: TraktDeviceCode, nowMs: Long): TraktPoll {
        val r = post("/oauth/device/token", buildJsonObject {
            put("code", code.deviceCode)
            put("client_id", app.clientId)
            put("client_secret", app.clientSecret)
        })
        if (r.status in 200..299) {
            val t = obj(r)?.let { parseTraktTokens(it, null, nowMs) } ?: return TraktPoll.Failed("Trakt 回的东西读不懂")
            return TraktPoll.Done(t.copy(user = username(t.access)))
        }
        return traktPollOf(r.status) ?: TraktPoll.Failed("Trakt 回了 HTTP ${r.status}")
    }

    /** 续令牌。失败 = null（要重新连接）。 */
    suspend fun refresh(t: TraktTokens, nowMs: Long): TraktTokens? {
        if (t.refresh.isEmpty()) return null
        val r = post("/oauth/token", buildJsonObject {
            put("refresh_token", t.refresh)
            put("client_id", app.clientId)
            put("client_secret", app.clientSecret)
            put("redirect_uri", "urn:ietf:wg:oauth:2.0:oob")
            put("grant_type", "refresh_token")
        })
        if (r.status !in 200..299) return null
        return obj(r)?.let { parseTraktTokens(it, t.user, nowMs) }
    }

    /** 断开：吊销令牌（失败也算断开了，本地照删）。 */
    suspend fun revoke(t: TraktTokens) {
        try {
            post("/oauth/revoke", buildJsonObject {
                put("token", t.access)
                put("client_id", app.clientId)
                put("client_secret", app.clientSecret)
            })
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun username(token: String): String? = try {
        val r = get("/users/settings", token)
        ((obj(r)?.get("user") as? JsonObject)?.get("username") as? JsonPrimitive)?.contentOrNull
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** 记一次观看动作：[action] = start / pause / stop，[body] = { movie | episode | show+episode, progress }。返回状态码（409 = 刚记过，也算成功）。 */
    suspend fun scrobble(token: String, action: String, body: JsonObject): Int = post("/scrobble/$action", body, token).status

    /** 看过的电影 / 剧（/sync/watched/movies|shows）。令牌不能用（401）= null。 */
    suspend fun watched(token: String, kind: String): JsonArray? = get("/sync/watched/$kind", token).let { if (it.status == UNAUTHORIZED) null else arr(it) }

    /** 观看记录：加（/sync/history）或删（/sync/history/remove）。返回状态码。 */
    suspend fun history(token: String, body: JsonObject, remove: Boolean): Int = post(if (remove) "/sync/history/remove" else "/sync/history", body, token).status

    /** 追更（Trakt 的「想看」剧集清单）：现在追的剧（/sync/watchlist/shows）。401 = null。 */
    suspend fun watchlistShows(token: String): JsonArray? = get("/sync/watchlist/shows", token).let { if (it.status == UNAUTHORIZED) null else arr(it) }

    /** 加入 / 取消追更。返回状态码。 */
    suspend fun watchlist(token: String, body: JsonObject, remove: Boolean): Int = post(if (remove) "/sync/watchlist/remove" else "/sync/watchlist", body, token).status

    /** 追更日历：从 [startDate]（yyyy-MM-dd）起 [days] 天里，我的剧要播的集（/calendars/my/shows）。401 = null。 */
    suspend fun calendar(token: String, startDate: String, days: Int): JsonArray? = get("/calendars/my/shows/$startDate/$days", token).let { if (it.status == UNAUTHORIZED) null else arr(it) }

    companion object {
        const val UNAUTHORIZED = 401
    }
}

/** Trakt 回应里一个 ids 对象 → 各家编号（只要 Emby 也有的几种）。 */
fun traktIds(ids: JsonElement?): Map<String, String> {
    val o = ids as? JsonObject ?: return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (k in listOf("tmdb", "imdb", "tvdb")) {
        val v = (o[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" } ?: continue
        out[k] = v
    }
    return out
}
