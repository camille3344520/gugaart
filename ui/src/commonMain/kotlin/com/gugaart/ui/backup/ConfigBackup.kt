package com.gugaart.ui.backup

import androidx.compose.runtime.staticCompositionLocalOf
import com.gugaart.ui.danmaku.DanmakuServers
import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.data.GugaSettingsStore
import com.gugugaga.app.AppGraph
import com.gugugaga.core.KeyValueStore
import com.gugugaga.core.StoredServer
import com.gugugaga.core.Transport
import com.gugugaga.core.TransportRequest
import com.gugugaga.core.isSameAddress
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * 配置备份与还原（用户 2026-11-15：「配置备份还原功能，一份配置支持3端」「配置备份恢复 支持本地和webdav」；
 * 问过：带「设置 + 服务器列表，不带凭据」）。
 *
 * 一份 JSON（[BACKUP_FILE_NAME]），三端通用（设置的键和形状三端本来就一样）：
 *  - settings：上游的设置（gugu.settings.v1 整块）；prefs：画报自己的设置（gugaart.settings.v1）；
 *  - danmakuServers：弹幕服务器清单（地址里带令牌：备份文件别外传）；tmdbToken：TMDB 令牌；
 *  - servers：Emby 服务器（名字、地址、备注、UA、各条线路）+ 登录过的用户名；**不带**登录令牌、不带自定义请求头（可能是反代的凭据）。
 * 还原：设置按分区写回（不认识的键原样留着）；服务器清单里没有的服务器加进去（已经有的不动，登录还在）；还原后要登录的服务器在账号页里登。
 * 存放：本地文件（壳给的 [BackupFileHost]：Windows 的保存 / 打开对话框，安卓的系统文件选择器）或 WebDAV（[WebDavClient]）。
 */

const val BACKUP_FILE_NAME = "gugaart-backup.json"
const val BACKUP_VERSION = 1

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

/** 备份里一台 Emby 服务器（去掉了令牌和请求头）+ 登录过的用户名。 */
private fun serverEntry(s: StoredServer): JsonObject {
    val raw = json.encodeToJsonElement(StoredServer.serializer(), s) as JsonObject
    val out = LinkedHashMap<String, JsonElement>()
    for ((k, v) in raw) if (k !in setOf("accounts", "headers", "activeUserId", "lastUsedAt")) out[k] = v
    out["userNames"] = buildJsonArray { s.accounts.map { it.userName }.distinct().forEach { add(JsonPrimitive(it)) } }
    return JsonObject(out)
}

/** 备份的内容。纯数据，有单测。 */
fun backupJson(
    settings: JsonObject,
    prefs: String?,
    servers: List<StoredServer>,
    danmakuServers: List<String>,
    tmdbToken: String,
    createdAt: String,
    platform: String,
): String = buildJsonObject {
    put("app", "gugaart")
    put("kind", "config")
    put("version", BACKUP_VERSION)
    put("createdAt", createdAt)
    put("platform", platform)
    put("settings", settings)
    put("prefs", GugaPrefs.parseObject(prefs) ?: JsonObject(emptyMap()))
    put("servers", JsonArray(servers.map(::serverEntry)))
    put("danmakuServers", buildJsonArray { danmakuServers.forEach { add(JsonPrimitive(it)) } })
    put("tmdbToken", tmdbToken)
}.toString()

/** 读出来的备份。 */
class ParsedBackup(
    val settings: JsonObject,
    val prefs: JsonObject,
    val servers: List<Pair<StoredServer, List<String>>>,
    val danmakuServers: List<String>,
    val tmdbToken: String?,
    val createdAt: String?,
    val platform: String?,
)

/** 认不出来的原因（给人看的话）。 */
enum class BackupError(val text: String) {
    NotBackup("这不是咕嘎画报的配置备份"),
    TooNew("这份备份是更新的版本做的，先把应用升级到最新"),
    Unreadable("备份文件读不出来"),
}

/** 解析一份备份；不是 = [BackupError]。纯函数，有单测。 */
fun parseBackup(text: String): Result<ParsedBackup> {
    val o = try {
        Json.parseToJsonElement(text) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return Result.failure(BackupException(BackupError.Unreadable))
    fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    if (str("app") != "gugaart" || str("kind") != "config") return Result.failure(BackupException(BackupError.NotBackup))
    if (((o["version"] as? JsonPrimitive)?.intOrNull ?: 0) > BACKUP_VERSION) return Result.failure(BackupException(BackupError.TooNew))
    val servers = (o["servers"] as? JsonArray).orEmpty().mapNotNull { e ->
        val so = e as? JsonObject ?: return@mapNotNull null
        val names = (so["userNames"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val clean = JsonObject(so.filterKeys { it != "userNames" })
        try {
            json.decodeFromJsonElement(StoredServer.serializer(), clean).copy(accounts = emptyList(), headers = null, activeUserId = null) to names
        } catch (_: Exception) {
            null
        }
    }
    return Result.success(
        ParsedBackup(
            settings = o["settings"] as? JsonObject ?: JsonObject(emptyMap()),
            prefs = o["prefs"] as? JsonObject ?: JsonObject(emptyMap()),
            servers = servers,
            danmakuServers = (o["danmakuServers"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } },
            tmdbToken = str("tmdbToken"),
            createdAt = str("createdAt"),
            platform = str("platform"),
        ),
    )
}

class BackupException(val error: BackupError) : Exception(error.text)

/** 还原了什么（给人看的一句）。 */
data class RestoreSummary(val sections: Int, val serversAdded: Int, val serversKept: Int, val danmakuServers: Int, val tmdb: Boolean) {
    val text: String get() = buildList {
        add("设置已还原")
        if (danmakuServers > 0) add("弹幕服务器 $danmakuServers 台")
        if (tmdb) add("TMDB 令牌")
        if (serversAdded > 0) add("新加 $serversAdded 台 Emby 服务器（要重新登录）")
        if (serversKept > 0) add("$serversKept 台已经有了，没动")
    }.joinToString(" · ")
}

/** 这台设备上的配置做成一份备份。 */
suspend fun makeBackup(g: AppGraph, platform: String, createdAt: String): String {
    val secrets = g.secrets.load()
    DanmakuServers.bind(g.deps.secure, g.deps.debug.defaultSecrets?.danmakuApiUrl)
    return backupJson(
        settings = g.settings.value,
        prefs = g.deps.local.get(GugaSettingsStore.KEY),
        servers = g.session.saved().servers,
        danmakuServers = DanmakuServers.load().map { it.url },
        tmdbToken = secrets.tmdbToken,
        createdAt = createdAt,
        platform = platform,
    )
}

/** 把一份备份还原到这台设备上。 */
suspend fun restoreBackup(g: AppGraph, prefsStore: GugaSettingsStore, backup: ParsedBackup): RestoreSummary {
    // 设置：按分区写回（上游的 patch：并进去，不认识的键原样留着）
    for ((section, v) in backup.settings) if (v is JsonObject) g.settings.patch(section, v)
    if (backup.prefs.isNotEmpty()) prefsStore.update { GugaPrefs.parse(backup.prefs.toString()) }
    // 弹幕服务器、TMDB 令牌
    DanmakuServers.bind(g.deps.secure, g.deps.debug.defaultSecrets?.danmakuApiUrl)
    if (backup.danmakuServers.isNotEmpty()) DanmakuServers.save(backup.danmakuServers)
    val tmdb = backup.tmdbToken?.takeIf { it.isNotBlank() }
    if (tmdb != null) g.secrets.save(tmdbToken = tmdb)
    g.secrets.load()
    // Emby 服务器：没有的加进去（不带登录），已经有的不动
    val existing = g.session.saved().servers
    var added = 0
    var kept = 0
    for ((s, _) in backup.servers) {
        if (existing.any { isSameAddress(it.address, s.address) }) {
            kept++
            continue
        }
        val id = if (existing.any { it.id == s.id }) "${s.id}-restored" else s.id
        g.session.store.saveServer(s.copy(id = id))
        added++
    }
    return RestoreSummary(backup.settings.size, added, kept, backup.danmakuServers.size, tmdb != null)
}

/* ---------------------------------------------------------------- 本地文件 */

/** 平台那一半（壳给；null = 没有文件选择器，例如电视）：存一份文件、选一份文件读出来。用户取消 = false / null。 */
class BackupFileHost(
    val save: suspend (suggestedName: String, text: String) -> Boolean,
    val open: suspend () -> String?,
)

val LocalBackupFiles = staticCompositionLocalOf<BackupFileHost?> { null }

/* ---------------------------------------------------------------- WebDAV */

/** WebDAV 的地址 / 用户名 / 密码，存在系统安全存储里（[KEY]）。 */
data class WebDavConfig(val url: String, val user: String, val password: String) {
    /** 填的是文件夹 = 文件夹下的 [BACKUP_FILE_NAME]；填的就是 .json 文件 = 那个文件。 */
    val fileUrl: String get() = url.trim().let { if (it.endsWith(".json", ignoreCase = true)) it else it.trimEnd('/') + "/" + BACKUP_FILE_NAME }

    val complete: Boolean get() = url.isNotBlank()

    companion object {
        const val KEY = "gugaart.webdav.v1"

        fun parse(raw: String?): WebDavConfig? {
            val o = GugaPrefs.parseObject(raw) ?: return null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull.orEmpty()
            return WebDavConfig(s("url"), s("user"), s("password"))
        }

        suspend fun load(secure: KeyValueStore): WebDavConfig = parse(secure.get(KEY)) ?: WebDavConfig("", "", "")

        suspend fun save(secure: KeyValueStore, c: WebDavConfig) {
            secure.set(KEY, buildJsonObject { put("url", c.url.trim()); put("user", c.user.trim()); put("password", c.password) }.toString())
        }
    }
}

/** WebDAV 失败的原因（给人看的话，不带地址和密码）。 */
fun webDavFailure(status: Int?): String = when (status) {
    null -> "连不上 WebDAV 服务器"
    401, 403 -> "用户名或密码不对（HTTP $status）"
    404 -> "这个地址下还没有备份（HTTP 404）"
    405, 409 -> "文件夹不存在或不能写（HTTP $status）"
    507 -> "WebDAV 空间满了（HTTP 507）"
    else -> "WebDAV 服务器回了 HTTP $status"
}

/** 极简 WebDAV：上传（PUT）、下载（GET）、测一下（PROPFIND 文件夹）。 */
class WebDavClient(private val transport: Transport) {
    @OptIn(ExperimentalEncodingApi::class)
    private fun headers(c: WebDavConfig, extra: Map<String, String> = emptyMap()): Map<String, String> = buildMap {
        if (c.user.isNotEmpty() || c.password.isNotEmpty()) put("Authorization", "Basic " + Base64.encode("${c.user}:${c.password}".encodeToByteArray()))
        putAll(extra)
    }

    /** 上传；成功 = null，失败 = 原因。 */
    suspend fun upload(c: WebDavConfig, text: String): String? = call {
        val r = transport.execute(TransportRequest(c.fileUrl, "PUT", headers(c, mapOf("Content-Type" to "application/json; charset=utf-8")), text, 30_000))
        if (r.status in 200..299) null else webDavFailure(r.status)
    }

    /** 下载；成功 = 内容，失败 = 原因。 */
    suspend fun download(c: WebDavConfig): Result<String> = try {
        val r = transport.execute(TransportRequest(c.fileUrl, "GET", headers(c), null, 30_000))
        if (r.status in 200..299) Result.success(r.text) else Result.failure(Exception(webDavFailure(r.status)))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.failure(Exception(webDavFailure(null)))
    }

    /** 测一下：文件夹能不能列（PROPFIND，Depth 0）。成功 = null。 */
    suspend fun test(c: WebDavConfig): String? = call {
        val folder = c.fileUrl.substringBeforeLast('/') + "/"
        val r = transport.execute(TransportRequest(folder, "PROPFIND", headers(c, mapOf("Depth" to "0", "Content-Type" to "application/xml")), "", 15_000))
        if (r.status in 200..299 || r.status == 207) null else webDavFailure(r.status)
    }

    private suspend fun call(block: suspend () -> String?): String? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        webDavFailure(null)
    }
}
