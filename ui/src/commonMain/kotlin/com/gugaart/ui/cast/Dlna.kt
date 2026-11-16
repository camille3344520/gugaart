package com.gugaart.ui.cast

import androidx.compose.runtime.Immutable

/*
 * DLNA 投屏（用户 2026-11-16：「投屏只要DLNA就行」）。协议是公开的 UPnP AV：
 *  - 找电视：往 239.255.255.250:1900 发 SSDP 的 M-SEARCH，电视回一个 LOCATION（设备描述 XML 的地址）；
 *  - 设备描述里找 AVTransport 服务的 controlURL；
 *  - 控制：往 controlURL 发 SOAP——SetAVTransportURI（片源地址 + DIDL-Lite 元数据）、Play、Pause、Stop、Seek、GetPositionInfo、GetTransportInfo。
 * 这个文件只放纯函数（拼消息、读回应），网络在 jvmShared 的 DlnaCastHost 里。不用第三方库。
 */

@Immutable
data class CastDevice(val id: String, val name: String, val model: String?, val avTransportUrl: String)

@Immutable
data class CastStatus(val state: String, val positionSec: Long?, val durationSec: Long?) {
    val playing: Boolean get() = state == "PLAYING"
    val paused: Boolean get() = state == "PAUSED_PLAYBACK"
    val stopped: Boolean get() = state == "STOPPED" || state == "NO_MEDIA_PRESENT"
}

const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"

/** M-SEARCH 报文（找 [target]：AVTransport 服务 / MediaRenderer 设备）。 */
fun ssdpSearch(target: String, mx: Int = 2): String =
    "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: $mx\r\nST: $target\r\n\r\n"

/** SSDP 回应里的 LOCATION（不分大小写）；没有 = null。纯函数，有单测。 */
fun ssdpLocation(response: String): String? =
    response.lineSequence().firstOrNull { it.trim().startsWith("location:", ignoreCase = true) }?.substringAfter(':')?.trim()?.takeIf { it.startsWith("http") }

/** XML 里第一个 <tag>…</tag>（不管命名空间前缀）。 */
fun xmlTag(xml: String, tag: String): String? =
    Regex("""<(?:[A-Za-z0-9_]+:)?$tag(?:\s[^>]*)?>([\s\S]*?)</(?:[A-Za-z0-9_]+:)?$tag>""").find(xml)?.groupValues?.get(1)?.trim()

/** 相对地址 → 绝对地址（以 [base] 为准：URLBase 或者 LOCATION）。纯函数，有单测。 */
fun resolveUrl(base: String, path: String): String {
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val origin = Regex("""^(https?://[^/]+)""").find(base)?.groupValues?.get(1) ?: return path
    if (path.startsWith("/")) return origin + path
    val dir = base.substringBeforeLast('/', origin).let { if (it.length < origin.length) origin else it }
    return "$dir/$path"
}

/** 设备描述 XML → 设备（有 AVTransport 才算能投）。纯函数，有单测。 */
fun parseDeviceDescription(xml: String, location: String): CastDevice? {
    val base = xmlTag(xml, "URLBase")?.takeIf { it.startsWith("http") } ?: location
    val services = Regex("""<(?:[A-Za-z0-9_]+:)?service>([\s\S]*?)</(?:[A-Za-z0-9_]+:)?service>""").findAll(xml).map { it.groupValues[1] }
    val av = services.firstOrNull { (xmlTag(it, "serviceType") ?: "").startsWith("urn:schemas-upnp-org:service:AVTransport") } ?: return null
    val control = xmlTag(av, "controlURL") ?: return null
    val name = xmlDecode(xmlTag(xml, "friendlyName") ?: "电视")
    val model = listOfNotNull(xmlTag(xml, "manufacturer"), xmlTag(xml, "modelName")).map(::xmlDecode).filter { it.isNotBlank() }.distinct().joinToString(" ").ifBlank { null }
    val udn = xmlTag(xml, "UDN") ?: location
    return CastDevice(udn, name, model, resolveUrl(base, control))
}

fun xmlEscape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

fun xmlDecode(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

/** SOAP 请求体：AVTransport 的 [action]，参数按顺序。纯函数，有单测。 */
fun soapBody(action: String, args: List<Pair<String, String>>): String = buildString {
    append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
    append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
    append("<u:$action xmlns:u=\"$AV_TRANSPORT\">")
    for ((k, v) in args) append("<$k>").append(xmlEscape(v)).append("</$k>")
    append("</u:$action></s:Body></s:Envelope>")
}

/** 投出去的片子的元数据（DIDL-Lite）：片名 + 地址 + 类型。很多电视不带它就不放。纯函数，有单测。 */
fun didl(title: String, url: String, mime: String): String =
    "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
        "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${xmlEscape(title)}</dc:title><upnp:class>object.item.videoItem</upnp:class>" +
        "<res protocolInfo=\"http-get:*:$mime:*\">${xmlEscape(url)}</res></item></DIDL-Lite>"

/** 容器 → MIME（电视靠它决定能不能放）。纯函数，有单测。 */
fun mimeOf(container: String?): String = when ((container ?: "").split(',').first().trim().lowercase()) {
    "mkv", "matroska", "webm" -> "video/x-matroska"
    "mp4", "m4v", "mov" -> "video/mp4"
    "ts", "m2ts", "mts" -> "video/mp2t"
    "avi" -> "video/x-msvideo"
    "wmv" -> "video/x-ms-wmv"
    "flv" -> "video/x-flv"
    else -> "video/*"
}

/** 秒 → 「01:02:03」。 */
fun hms(sec: Long): String {
    val s = sec.coerceAtLeast(0)
    fun two(n: Long) = n.toString().padStart(2, '0')
    return "${two(s / 3600)}:${two((s % 3600) / 60)}:${two(s % 60)}"
}

/** 「01:02:03」「1:02:03.500」→ 秒；读不出 = null（电视有时回 NOT_IMPLEMENTED）。纯函数，有单测。 */
fun parseHms(s: String?): Long? {
    val m = Regex("""^(\d+):(\d{1,2}):(\d{1,2})""").find(s?.trim() ?: return null) ?: return null
    return m.groupValues[1].toLong() * 3600 + m.groupValues[2].toLong() * 60 + m.groupValues[3].toLong()
}

/** GetTransportInfo + GetPositionInfo 的回应 → 状态。纯函数，有单测。 */
fun parseStatus(transportXml: String?, positionXml: String?): CastStatus? {
    val state = transportXml?.let { xmlTag(it, "CurrentTransportState") } ?: return null
    return CastStatus(state, parseHms(positionXml?.let { xmlTag(it, "RelTime") }), parseHms(positionXml?.let { xmlTag(it, "TrackDuration") })?.takeIf { it > 0 })
}

/** 能投屏的那一层（壳装上；手机、电脑都是 jvmShared 的 DlnaCastHost）。 */
interface CastHost {
    suspend fun discover(timeoutMs: Long): List<CastDevice>
    suspend fun action(device: CastDevice, action: String, args: List<Pair<String, String>>): String?
}
