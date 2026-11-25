package com.gugaart.ui.data

import com.gugaart.ui.player.AvCaps
import com.gugaart.ui.player.DeviceAv
import com.gugaart.ui.player.doviDualLayer
import com.gugaart.ui.player.doviFallback
import com.gugaart.ui.player.hdrKind
import com.gugaart.ui.screens.detail.specLine
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.defaultVideoStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * 默认版本规则 + 重复片合并判定 + 「用户选过的版本」存储（1.5.0 C1，docs/plan-multi-server.md §2.3）。
 * 全是纯函数 / 只碰本地键，不发请求；单测在 commonTest（SourceRulesTest）。
 */

/** 屏幕的像素尺寸（横屏方向）。null = 不知道（分辨率不封顶）。 */
data class ScreenSpec(val widthPx: Int, val heightPx: Int)

private fun providerValue(ids: Map<String, String?>, key: String): String? =
    ids.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.trim()?.takeIf { it.isNotEmpty() }

/** 这部片的本地键：TMDB 优先、IMDB 兜底；都没有 = null（不记选择、不合并重复片）。 */
fun providerKeyOf(ids: Map<String, String?>): String? =
    providerValue(ids, "tmdb")?.let { "tmdb.$it" } ?: providerValue(ids, "imdb")?.let { "imdb.$it" }

/** 两个条目的 TMDB / IMDB 有没有相同的（值不区分大小写）。没有 provider id 的不算。纯函数。 */
fun sharesProviderId(a: Map<String, String?>, b: Map<String, String?>): Boolean {
    for (k in listOf("tmdb", "imdb")) {
        val x = providerValue(a, k)?.lowercase() ?: continue
        if (x == providerValue(b, k)?.lowercase()) return true
    }
    return false
}

/** 重复片判定（纯函数）：条目 id 不同、类型相同（电影对电影）、provider id 相同。 */
fun isDuplicateItem(item: BaseItemDto, other: BaseItemDto): Boolean =
    item.id != other.id && item.type != null && item.type == other.type && sharesProviderId(item.providerIds, other.providerIds)

/** 版本的「规格签名」：分辨率档 + HDR 种类（「4K|HDR10」）。跨服务器认用户选过的版本用它。纯函数。 */
fun versionSignature(source: MediaSourceInfo): String {
    val res = specLine(source)?.substringBefore(" · ") ?: "?"
    return "$res|${hdrKind(defaultVideoStream(source.mediaStreams)) ?: "SDR"}"
}

/** 用户在这部片上选过的版本（键 = provider id，跨服务器也认）。 */
data class VersionPick(val serverId: String, val itemId: String, val sourceId: String, val signature: String)

/**
 * 「用户选过的版本」存本地键 `gugaart.versionPick.v1`（画报自己的键，不碰上游的）：
 * JSON 对象，provider key → {serverId, itemId, sourceId, signature}。读不懂的项当没选过。
 */
class VersionPickStore(private val local: LocalStore) {
    companion object {
        const val KEY = "gugaart.versionPick.v1"
    }

    private fun read(): JsonObject = try {
        local.get(KEY)?.let { Json.parseToJsonElement(it) as? JsonObject } ?: JsonObject(emptyMap())
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }

    private fun str(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

    fun pickFor(providerIds: Map<String, String?>): VersionPick? {
        val key = providerKeyOf(providerIds) ?: return null
        val o = read()[key] as? JsonObject ?: return null
        return VersionPick(
            serverId = str(o, "serverId") ?: return null,
            itemId = str(o, "itemId") ?: return null,
            sourceId = str(o, "sourceId") ?: return null,
            signature = str(o, "signature") ?: return null,
        )
    }

    fun remember(providerIds: Map<String, String?>, source: PlaySource) {
        val key = providerKeyOf(providerIds) ?: return
        val map = read().toMutableMap()
        map[key] = buildJsonObject {
            put("serverId", source.serverId)
            put("itemId", source.itemId)
            put("sourceId", source.mediaSource.id)
            put("signature", versionSignature(source.mediaSource))
        }
        local.set(KEY, JsonObject(map).toString())
    }
}

/** 选过的版本和这个源有多像：3 = 同台同条目同片源；2 = 同条目同片源；1 = 规格签名相同（跨服务器）；0 = 不认。纯函数。 */
fun pickMatchLevel(pick: VersionPick, source: PlaySource): Int = when {
    pick.serverId == source.serverId && pick.itemId == source.itemId && pick.sourceId == source.mediaSource.id -> 3
    pick.itemId == source.itemId && pick.sourceId == source.mediaSource.id -> 2
    pick.signature == versionSignature(source.mediaSource) -> 1
    else -> 0
}

/**
 * 这个源在这台设备上实际显示得出的 HDR 档：杜比视界 4 > HDR10+ 3 > HDR10 2 > HLG 1 > SDR 0。
 * 屏幕 / 解码器放不出的降到底层（杜比视界 profile 7 双层读不了 → HDR10；HDR10+ 不支持 → HDR10……），
 * 判断复用 `FormatBadges` 的 [hdrKind] / [doviFallback] / [doviDualLayer]。[caps] null = 不知道，按片源原样。纯函数。
 */
fun effectiveHdrRank(source: MediaSourceInfo, caps: AvCaps?): Int {
    val video = defaultVideoStream(source.mediaStreams)
    val kind = hdrKind(video)
    if (caps == null) return when (kind) {
        "Dolby Vision" -> 4
        "HDR10+" -> 3
        "HDR10", "HDR" -> 2
        "HLG" -> 1
        else -> 0
    }
    return when (kind) {
        "Dolby Vision" -> when {
            caps.dolbyVision && (caps.dolbyVisionDualLayer || !doviDualLayer(video)) -> 4
            doviFallback(video) == "HDR10" && caps.hdr10 -> 2
            doviFallback(video) == "HLG" && caps.hlg -> 1
            else -> 0
        }
        "HDR10+" -> if (caps.hdr10Plus) 3 else if (caps.hdr10) 2 else 0
        "HDR10", "HDR" -> if (caps.hdr10) 2 else 0
        "HLG" -> if (caps.hlg) 1 else 0
        else -> 0
    }
}

private fun playableRank(p: Playable): Int = when (p) {
    Playable.Direct -> 2
    Playable.Unknown -> 1
    Playable.Transcode -> 0
}

/** 分辨率在屏幕内 = 1（屏幕 / 流不知道 = 1，不罚）。 */
private fun resFits(source: PlaySource, screen: ScreenSpec?): Int {
    val v = defaultVideoStream(source.mediaSource.mediaStreams) ?: return 1
    if (screen == null) return 1
    val w = v.width ?: return 1
    val h = v.height ?: return 1
    return if (w <= screen.widthPx && h <= screen.heightPx) 1 else 0
}

/** 屏幕内的越大越好；超出屏幕的越小越好；不知道屏幕 = 越大越好。 */
private fun resScore(source: PlaySource, screen: ScreenSpec?): Long {
    val v = defaultVideoStream(source.mediaSource.mediaStreams) ?: return 0
    val pixels = (v.width ?: 0).toLong() * (v.height ?: 0)
    return if (screen != null && resFits(source, screen) == 0) -pixels else pixels
}

/** 码率在上限内 = 1（没设上限 / 不知道码率 = 1）。 */
private fun bitrateFits(source: PlaySource, maxBitrateBps: Long?): Int {
    if (maxBitrateBps == null || maxBitrateBps <= 0) return 1
    val b = source.mediaSource.bitrate ?: return 1
    return if (b <= maxBitrateBps) 1 else 0
}

/** 同分怎么取：当前服务器的优先，再延迟低的（不知道 = 最慢），再保持列表顺序。 */
private fun tieBreak(list: List<PlaySource>, currentServerId: String?): PlaySource? =
    list.maxWithOrNull(
        compareBy<PlaySource> { if (it.serverId == currentServerId) 1 else 0 }
            .thenBy { -(it.latencyMs ?: Int.MAX_VALUE) },
    )

/**
 * 默认版本规则（纯函数，docs/plan-multi-server.md §2.3）：
 * 1. 用户在这部片上选过的优先（[pick]，按 provider id 记在 [VersionPickStore]，跨服务器按规格签名认）；
 * 2. 没选过按设备能力打分：能直连 > 要转码 → 屏幕支持的最高 HDR（杜比视界 > HDR10+ > HDR10 > SDR，[caps]）→
 *    分辨率不超过屏幕（[screen]）→ 码率不超过设置里的上限（[maxBitrateBps]，null / 0 = 不限）；
 * 3. 同分取当前服务器（[currentServerId]）的、延迟低的。
 * 别的服务器上的源（[SourceKind.OtherServer]）**不当默认**——只要本台有源，默认就在本台的里面挑；别台的要用户自己选
 * （协调者 2026-11-25 定：进度只报给正在播的那台、已看不跨台同步，自动跑到别台会把「看到哪了」打散）。用户选过的（第 1 条）照旧认。
 * 空列表 = null。
 */
fun defaultSource(
    sources: List<PlaySource>,
    pick: VersionPick? = null,
    caps: AvCaps? = DeviceAv.caps,
    screen: ScreenSpec? = null,
    maxBitrateBps: Long? = null,
    currentServerId: String? = null,
): PlaySource? {
    if (sources.isEmpty()) return null
    if (pick != null) {
        val matched = sources.map { it to pickMatchLevel(pick, it) }.filter { it.second > 0 }
        if (matched.isNotEmpty()) {
            val top = matched.maxOf { it.second }
            return tieBreak(matched.filter { it.second == top }.map { it.first }, currentServerId)
        }
    }
    val pool = sources.filter { it.kind != SourceKind.OtherServer }.ifEmpty { sources }
    val cmp = compareBy<PlaySource> { playableRank(it.playable) }
        .thenBy { effectiveHdrRank(it.mediaSource, caps) }
        .thenBy { resFits(it, screen) }
        .thenBy { resScore(it, screen) }
        .thenBy { bitrateFits(it, maxBitrateBps) }
        .thenBy { if (it.serverId == currentServerId) 1 else 0 }
        .thenBy { -(it.latencyMs ?: Int.MAX_VALUE) }
    return pool.maxWithOrNull(cmp)
}