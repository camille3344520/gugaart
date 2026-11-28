package com.gugaart.ui.download

import com.gugugaga.core.EmbyClient
import com.gugugaga.core.PlayMethod
import com.gugugaga.core.PlaybackProgressInfo
import com.gugugaga.core.PlaybackStopInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/*
 * 放本地文件时的播放进度（1.6 批次 E）：先记在下载目录的 `reports.json`，联网后**按顺序**回报给 Emby
 * （`/Sessions/Playing/Progress`、`/Sessions/Playing/Stopped`，用记下时的位置）。
 * 一次播放（[PendingReport.session]）在记录里最多两条：最近的一条进度（每次心跳覆盖）+ 停下时的一条。
 * 只回报给记下它的那台服务器的那个账号（换了账号就等换回来再报）。
 */

const val REPORTS_FILE = "reports.json"

/** 等着回报的一条。[stopped] = `Stopped`，否则 = `Progress`。 */
data class PendingReport(
    val serverId: String,
    val userId: String,
    val itemId: String,
    val mediaSourceId: String?,
    val positionTicks: Long,
    val paused: Boolean,
    val stopped: Boolean,
    val session: String,
    val at: Long,
) {
    fun encode(): JsonObject = buildJsonObject {
        put("serverId", serverId)
        put("userId", userId)
        put("itemId", itemId)
        mediaSourceId?.let { put("mediaSourceId", it) }
        put("positionTicks", positionTicks)
        put("paused", paused)
        put("stopped", stopped)
        put("session", session)
        put("at", at)
    }

    companion object {
        fun parse(o: JsonObject): PendingReport? {
            fun s(k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            fun l(k: String): Long? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            fun b(k: String): Boolean? = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
            return PendingReport(
                serverId = s("serverId") ?: return null,
                userId = s("userId") ?: return null,
                itemId = s("itemId") ?: return null,
                mediaSourceId = s("mediaSourceId"),
                positionTicks = l("positionTicks")?.coerceAtLeast(0) ?: return null,
                paused = b("paused") ?: false,
                stopped = b("stopped") ?: false,
                session = s("session") ?: "",
                at = l("at") ?: 0,
            )
        }
    }
}

/** 记录的增删（纯函数，有单测）。 */
object ReportLog {
    /** 记一条进度：记录末尾就是这次播放的进度 → 换成新的；否则接在后面。这次播放已经停了 = 不记。 */
    fun progress(log: List<PendingReport>, r: PendingReport): List<PendingReport> {
        if (log.any { it.session == r.session && it.stopped }) return log
        val last = log.lastOrNull()
        return if (last != null && last.session == r.session && !last.stopped) log.dropLast(1) + r else log + r
    }

    /** 记停下：接在后面（同一次播放只记一次）。 */
    fun stop(log: List<PendingReport>, r: PendingReport): List<PendingReport> =
        if (log.any { it.session == r.session && it.stopped }) log else log + r.copy(stopped = true)

    /**
     * 没等到「停下」的播放（除了 [except] 这一次）：紧跟在它那条进度后面补一条停下，位置不变。
     * 补在原地、不补在末尾：同一部片后面还有一次新的播放时，旧的位置不能盖过新的。
     */
    fun closeOpen(log: List<PendingReport>, except: String?): List<PendingReport> {
        val closed = log.filter { it.stopped }.map { it.session }.toSet()
        val out = ArrayList<PendingReport>(log.size + 2)
        for (r in log) {
            out += r
            if (!r.stopped && r.session != except && r.session !in closed) out += r.copy(stopped = true)
        }
        return out
    }

    /**
     * 按顺序回报属于（[serverId]，[userId]）的那些，送出一条删一条；有一条没送出去就停（保持顺序，下次从它接着报）。
     * 别的账号的原样留着。返回剩下的记录。
     */
    suspend fun flush(log: List<PendingReport>, serverId: String, userId: String, send: suspend (PendingReport) -> Unit): List<PendingReport> {
        val left = ArrayList<PendingReport>()
        var blocked = false
        for (r in log) {
            if (blocked || r.serverId != serverId || r.userId != userId) {
                left += r
                continue
            }
            try {
                send(r)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                blocked = true
                left += r
            }
        }
        return left
    }

    fun encode(log: List<PendingReport>): String = buildJsonObject {
        put("v", 1)
        put("reports", buildJsonArray { log.forEach { add(it.encode()) } })
    }.toString()

    fun parse(raw: String?): List<PendingReport> = try {
        val o = raw?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject } ?: return emptyList()
        (o["reports"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(PendingReport::parse) }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

/** 一条记录送给 Emby（上游 `EmbyClient` 的公开方法）。 */
suspend fun EmbyClient.sendPending(r: PendingReport) {
    if (r.stopped) {
        reportPlaybackStopped(PlaybackStopInfo(itemId = r.itemId, mediaSourceId = r.mediaSourceId, positionTicks = r.positionTicks))
    } else {
        reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = r.itemId,
                mediaSourceId = r.mediaSourceId,
                positionTicks = r.positionTicks,
                isPaused = r.paused,
                canSeek = true,
                playMethod = PlayMethod.DirectPlay,
                eventName = "TimeUpdate",
            ),
        )
    }
}
