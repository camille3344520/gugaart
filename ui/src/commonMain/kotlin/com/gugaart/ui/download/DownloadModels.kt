package com.gugaart.ui.download

import com.gugaart.ui.data.Sha256
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/*
 * 离线下载（1.6 批次 E，docs/downloads.md）的数据：一条下载 = 一台服务器上一个账号的一部电影 / 一集，下原画（Emby `/Items/{id}/Download`，
 * strm 按 strm 的目标地址）。三端共用（commonMain）；平台只给存储和后台（[DownloadHost]）。
 * 记录存在下载目录里的 `index.json`（应用私有目录，卸载即清），里面没有令牌、没有带令牌的地址（下载地址开下载时现算）。
 */

/** 存下来的状态。正在下的那一条在内存里另算（[DownloadRow.running]），存盘时仍是 [Queued]。 */
enum class DlStatus(val id: String) {
    Queued("queued"),
    Done("done"),
    Failed("failed");

    companion object {
        fun of(id: String?): DlStatus = entries.firstOrNull { it.id == id } ?: Queued
    }
}

/**
 * 一条下载。[key] = 「服务器|账号|条目」；文件名由 key 算出来（[fileNameFor]），不带片名（片名可能有文件系统不认的字）。
 * [itemJson]：开下载那一刻的条目（`BaseItemDto` 原文）：断网时详情 / 播放器用它（媒体流、时长、片名）。
 */
data class DownloadEntry(
    val key: String,
    val serverId: String,
    val userId: String,
    val itemId: String,
    val kind: String,
    val name: String,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonNo: Int? = null,
    val episodeNo: Int? = null,
    val mediaSourceId: String? = null,
    val fileName: String,
    /** 文件总大小（字节）；0 = 还不知道（strm 常常没有 Size，开始下了才从响应头知道）。 */
    val total: Long = 0,
    /** 已经写到盘上的字节。 */
    val done: Long = 0,
    val status: DlStatus = DlStatus.Queued,
    /** 失败的原因（一句短话）；不是失败 = null。 */
    val error: String? = null,
    val addedAt: Long = 0,
    /** 离线看到的位置（Ticks）：断网时播放器从这里接着放（服务器那边要等联网后回报）。 */
    val positionTicks: Long? = null,
    val itemJson: String = "",
) {
    /** 0..1；不知道总大小 = 0。 */
    val fraction: Float get() = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f

    /** 列表上的片名：单集写剧名（集号和集名在副行）。 */
    val displayName: String get() = if (kind == "Episode") seriesName ?: name else name

    /** 单集的「第 1 季 第 3 集 · 集名」（集名就是「第 3 集」或者空 = 不重复写）；电影 = null。 */
    val episodeLine: String?
        get() = if (kind != "Episode") null else listOfNotNull(
            seasonNo?.let { if (it == 0) "特别篇" else "第 $it 季" },
            episodeNo?.let { "第 $it 集" },
        ).joinToString(" ").let { head ->
            when {
                head.isEmpty() -> name
                name.isBlank() || name.trim() == "第 $episodeNo 集" || name.trim() == "第${episodeNo}集" -> head
                else -> "$head · $name"
            }
        }

    /** 确认框里说的是哪一个：单集 =「剧名 第 3 集」，电影 = 片名。 */
    val fullName: String
        get() = if (kind == "Episode" && episodeNo != null) "${seriesName ?: name} 第 $episodeNo 集" else displayName

    fun encode(): JsonObject = buildJsonObject {
        put("key", key)
        put("serverId", serverId)
        put("userId", userId)
        put("itemId", itemId)
        put("kind", kind)
        put("name", name)
        seriesId?.let { put("seriesId", it) }
        seriesName?.let { put("seriesName", it) }
        seasonNo?.let { put("seasonNo", it) }
        episodeNo?.let { put("episodeNo", it) }
        mediaSourceId?.let { put("mediaSourceId", it) }
        put("fileName", fileName)
        put("total", total)
        put("done", done)
        put("status", status.id)
        error?.let { put("error", it) }
        put("addedAt", addedAt)
        positionTicks?.let { put("positionTicks", it) }
        put("item", itemJson)
    }

    companion object {
        fun keyOf(serverId: String, userId: String, itemId: String): String = "$serverId|$userId|$itemId"

        fun parse(o: JsonObject): DownloadEntry? {
            fun s(k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun l(k: String): Long? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            fun i(k: String): Int? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
            val serverId = s("serverId") ?: return null
            val userId = s("userId") ?: return null
            val itemId = s("itemId") ?: return null
            val fileName = s("fileName")?.takeIf { safeFileName(it) } ?: return null
            return DownloadEntry(
                key = keyOf(serverId, userId, itemId),
                serverId = serverId,
                userId = userId,
                itemId = itemId,
                kind = s("kind") ?: "Movie",
                name = s("name") ?: "",
                seriesId = s("seriesId"),
                seriesName = s("seriesName"),
                seasonNo = i("seasonNo"),
                episodeNo = i("episodeNo"),
                mediaSourceId = s("mediaSourceId"),
                fileName = fileName,
                total = l("total")?.coerceAtLeast(0) ?: 0,
                done = l("done")?.coerceAtLeast(0) ?: 0,
                status = DlStatus.of(s("status")),
                error = s("error"),
                addedAt = l("addedAt") ?: 0,
                positionTicks = l("positionTicks"),
                itemJson = s("item") ?: "",
            )
        }
    }
}

/** 下载目录里的文件名：key 的 SHA-256 前 24 位 + 容器扩展名（mkv / mp4 …；认不得的写 bin）。 */
fun fileNameFor(key: String, container: String?): String {
    val ext = container?.lowercase()?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() && it.length <= 5 && it.all { c -> c in 'a'..'z' || c in '0'..'9' } } ?: "bin"
    return Sha256.hex(key.encodeToByteArray()).take(24) + "." + ext
}

/** 只认我们自己起的文件名（防一份坏的 index.json 把路径指到目录外面去）。 */
internal fun safeFileName(name: String): Boolean =
    name.isNotEmpty() && name.length <= 40 && name.all { it in 'a'..'z' || it in '0'..'9' || it == '.' } && !name.startsWith(".")

/** 没下完的那一份文件（下完改名成 [DownloadEntry.fileName]）。 */
fun partNameOf(fileName: String): String = "$fileName.part"

const val INDEX_FILE = "index.json"

/** 整个记录（`index.json`）。读不懂的条目丢掉；整份读不懂 = 空。 */
object DownloadIndex {
    fun encode(entries: List<DownloadEntry>): String = buildJsonObject {
        put("v", 1)
        put("entries", buildJsonArray { entries.forEach { add(it.encode()) } })
    }.toString()

    fun parse(raw: String?): List<DownloadEntry> = try {
        val o = raw?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject } ?: return emptyList()
        (o["entries"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(DownloadEntry::parse) }.orEmpty().distinctBy { it.key }
    } catch (_: Exception) {
        emptyList()
    }
}

/* ---- 界面上的字 ---- */

/** 下载按钮 / 列表右端的那个词（三态：下载 / 进度 / 已下载；失败 = 重试）。[row] = null：还没下。 */
fun downloadLabel(row: DownloadRow?): String = when {
    row == null -> "下载"
    row.entry.status == DlStatus.Done -> "已下载"
    row.entry.status == DlStatus.Failed -> "重试"
    row.waiting == Waiting.Wifi -> "等 Wi-Fi"
    row.waiting == Waiting.Network -> "等网络"
    row.running && row.entry.total > 0 -> "${percentOf(row.entry)}%"
    row.running -> sizeLabel(row.entry.done) ?: "0%"
    else -> "排队"
}

/** 0..100（四舍五入；没下完不到 100）。 */
fun percentOf(e: DownloadEntry): Int {
    if (e.total <= 0) return 0
    val p = (e.done.toDouble() * 100 / e.total).roundToInt().coerceIn(0, 100)
    return if (p == 100 && e.done < e.total) 99 else p
}

/** 「1.25 GB」「340 MB」「0.8 MB」；0 / 负 = null。 */
fun sizeLabel(bytes: Long): String? {
    if (bytes <= 0) return null
    val mb = bytes / (1024.0 * 1024)
    val gb = mb / 1024
    return when {
        gb >= 1 -> "${(gb * 100).roundToInt() / 100.0} GB"
        mb >= 10 -> "${mb.roundToInt()} MB"
        else -> "${(mb * 10).roundToInt().coerceAtLeast(1) / 10.0} MB"
    }
}

/** 整季下载的确认框标题：「下载第 1 季，共 12 集？」；特别篇 / 没有季号的同理。 */
fun seasonDownloadTitle(seasonNo: Int?, count: Int): String {
    val season = when (seasonNo) {
        null -> "这一季"
        0 -> "特别篇"
        else -> "第 $seasonNo 季"
    }
    return "下载$season，共 $count 集？"
}

/** 整季那个链接上的字：还有没下的 =「下载这一季」；都下了（或都在队列里）= 「这一季已下载」。 */
fun seasonDownloadLink(missing: Int, total: Int): String = if (total > 0 && missing == 0) "这一季已下载" else "下载这一季"

/** 「我的 › 下载」右端的小字 / 合计：「3 项 · 4.2 GB」；没有 = 「还没有」。 */
fun downloadsHint(entries: List<DownloadEntry>): String {
    if (entries.isEmpty()) return "还没有"
    val size = sizeLabel(entries.sumOf { if (it.status == DlStatus.Done) maxOf(it.total, it.done) else it.done })
    return listOfNotNull("${entries.size} 项", size).joinToString(" · ")
}
