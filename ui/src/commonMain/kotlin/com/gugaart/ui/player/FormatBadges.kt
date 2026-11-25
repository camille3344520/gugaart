package com.gugaart.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaColors
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.MediaStream
import com.gugugaga.core.audioCodecLabel
import com.gugugaga.core.defaultVideoStream
import com.gugugaga.core.hdrLabel
import kotlinx.coroutines.delay

/*
 * 开播时显示片源的效果（用户 2026-11-16：「播放影片的时候 开播的时候可以显示下支持的效果，比如 hdr，杜比视界、杜比全景声」）。
 *
 * 识别用上游的 `hdrLabel` / `audioCodecLabel`（Emby 的 ExtendedVideoType、颜色传输、音轨名里的 Atmos / DTS:X）。
 * 标签只负责「说清楚」，不负责「打开」：效果由设备自己决定（HDR 片子电视自己切 HDR；杜比视界要芯片支持；全景声要音箱 / 功放解码）。
 * 壳在启动时报上这台设备的屏幕能显示什么（[DeviceAv]，安卓读 Display 的 HDR 能力和有没有杜比视界解码器；Mac 读窗口所在屏幕有没有 EDR 余量；Windows 上不知道 = 不写备注）：
 * 徽章哪里都照常亮（不变灰）；这台设备激活不了的，只在播放器里开播那几秒、徽章下面写一行实际会怎么放（「这台设备按 HDR10 显示」）；
 * 全景声 / DTS:X 应用查不到有没有接音箱，写「由音箱 / 功放解码」。
 * 每种效果一枚矢量徽记（1.4.1 U3，换掉 1.2 的九张 Gemini 复古徽章 PNG）：印刷品的字标签——墨底、直角、外粗内细两道米白框线，
 * 上面一行是这个格式叫得出的名字（HDR10、DTS:X；杜比视界、杜比全景声用中文），下面一行小字是另一种叫法。只有字，不画杜比 / DTS 的官方图形（注册商标）。
 * 路径由 scripts/art/AvMarks.java 从仓库里的 Noto Serif SC 子集生成（`AvMarkArt.kt`），颜色从主题取；说不清是哪种的「HDR」也有一枚。
 * 详情页也挂同一排（不看设备能力、不写备注，用户 2026-11-16：「影片详情页也可以打上对应的图标」）。
 */

/**
 * 这台设备的屏幕能显示哪些 HDR（壳在启动时填；不知道 = null）。
 * [dolbyVisionDualLayer]：双层的杜比视界（profile 7，蓝光原盘那种，动态元数据在增强层里）能不能按杜比视界放。安卓跟着解码器走（默认 true）；
 * Mac 的 libmpv 不读增强层，这类片子只放得出 HDR10 的基础层（实测《沙丘》，ADR 0004 §4「实测」十三）= false。
 */
@Immutable
data class AvCaps(val hdr10: Boolean, val hdr10Plus: Boolean, val hlg: Boolean, val dolbyVision: Boolean, val dolbyVisionDualLayer: Boolean = true)

/**
 * 这台设备的屏幕能显示什么。**只有播放器里开播那几秒的提示用它**（[FormatFlash]）；播放器之外（详情页、卡片）的徽章不看它，
 * 一律照常亮、不写备注（主理人 2026-11-18：「显示签原来的就行，里面激活不了给个提示就行」「徽章三端统一」，`decisions.md` D-mac-6）。
 */
object DeviceAv {
    var caps: AvCaps? = null
}

/** 开播的格式徽章亮多久（然后淡出）。 */
const val FORMAT_FLASH_MS = 5_000L

/** 画面：4K、各种 HDR；声音：全景声、DTS:X、两种无损（用户 2026-11-16：「看下还有没有其他什么格式需要弄的呢」）。 */
enum class AvKind { Uhd4k, DolbyVision, Hdr10Plus, Hdr10, Hlg, Hdr, Atmos, DtsX, DtsHdMa, TrueHd }

/** 一个效果标签。[supported] null = 不知道（不写备注）；false = 这台设备激活不了，[note] 写实际会怎么放（徽章本身不因此变样）。 */
@Immutable
data class FormatBadge(val kind: AvKind, val zh: String, val en: String?, val supported: Boolean?, val note: String?)

private const val SOUND_NOTE = "由音箱 / 功放解码"

/** 是不是双层的杜比视界（profile 7）。纯函数。 */
fun doviDualLayer(stream: MediaStream?): Boolean =
    Regex("""(?:dovi)?profile\s*7""").containsMatchIn((stream?.extendedVideoSubType ?: "").lowercase())

/** 杜比视界的「底层」：7、8.1 带 HDR10，8.4 带 HLG，5 没有可退的画面。不知道 = null。纯函数。 */
fun doviFallback(stream: MediaStream?): String? {
    val sub = (stream?.extendedVideoSubType ?: "").lowercase()
    val profile = Regex("""(?:dovi)?profile\s*(\d)(\d)?""").find(sub)?.let { it.groupValues[1] + it.groupValues[2] }
    return when {
        profile == null -> null
        profile.startsWith("7") || profile == "81" || profile == "8" -> "HDR10"
        profile == "84" -> "HLG"
        profile.startsWith("5") -> "SDR"
        else -> null
    }
}

/**
 * 画面的 HDR 种类：先用上游的 `hdrLabel`；它认不出（或只说得出笼统的「HDR」）时再看 Emby 的 VideoRange 文字——
 * 有的服务器只在 VideoRange 里写「HDR 10」「HDR 10+」「Dolby Vision」「HLG」（真实数据包里见过「HDR 10」），上游只认「HDR」。纯函数。
 */
fun hdrKind(video: MediaStream?): String? {
    val label = hdrLabel(video)
    if (label != null && label != "HDR") return label
    val r = (video?.videoRange ?: "").lowercase().replace(" ", "")
    return when {
        "dolbyvision" in r || "dovi" in r -> "Dolby Vision"
        "hdr10+" in r || "hdr10plus" in r -> "HDR10+"
        "hdr10" in r -> "HDR10"
        "hlg" in r -> "HLG"
        else -> label
    }
}

/** 片源的效果标签（4K、画面一个、声音一个）；没有就是空。[caps] = 这台设备的屏幕（null = 不知道）。纯函数，有单测。 */
fun formatBadges(source: MediaSourceInfo?, audioIndex: Int?, caps: AvCaps?): List<FormatBadge> {
    if (source == null) return emptyList()
    val streams = source.mediaStreams
    val out = ArrayList<FormatBadge>(2)
    val video = defaultVideoStream(streams)
    fun plain() = "这台设备按普通画面显示"
    // 4K：宽够 3800 或者高够 2000（宽银幕的 4K 高度只有 1600 左右，看宽）
    if ((video?.width ?: 0) >= 3800 || (video?.height ?: 0) >= 2000) out += FormatBadge(AvKind.Uhd4k, "4K 超高清", "ULTRA HD", null, null)
    when (hdrKind(video)) {
        "Dolby Vision" -> {
            val fallback = doviFallback(video)
            // 这台设备放不放得出这一种杜比视界：屏幕 / 解码器不行，或者是双层的而这台设备只读基础层
            val shown = caps?.let { it.dolbyVision && (it.dolbyVisionDualLayer || !doviDualLayer(video)) }
            val note = when {
                shown == null || shown -> null
                // 屏幕和内核都放得了杜比视界，只是读不了双层的增强层
                caps.dolbyVision && fallback == "HDR10" -> "这台设备不支持双层，按 HDR10 播放"
                fallback == "HDR10" && caps.hdr10 -> "这台设备按 HDR10 显示"
                fallback == "HLG" && caps.hlg -> "这台设备按 HLG 显示"
                fallback == null && caps.hdr10 -> "这台设备按 HDR 显示"
                else -> plain()
            }
            out += FormatBadge(AvKind.DolbyVision, "杜比视界", "DOLBY VISION", shown, note)
        }
        "HDR10+" -> out += FormatBadge(AvKind.Hdr10Plus, "HDR10+", null, caps?.hdr10Plus, if (caps == null || caps.hdr10Plus) null else if (caps.hdr10) "这台设备按 HDR10 显示" else plain())
        "HDR10" -> out += FormatBadge(AvKind.Hdr10, "HDR10", null, caps?.hdr10, if (caps == null || caps.hdr10) null else plain())
        "HLG" -> out += FormatBadge(AvKind.Hlg, "HLG", null, caps?.hlg, if (caps == null || caps.hlg) null else plain())
        "HDR" -> out += FormatBadge(AvKind.Hdr, "HDR", null, caps?.hdr10, if (caps == null || caps.hdr10) null else plain())
    }
    val audios = streams.filter { it.type == "Audio" }
    val audio = audios.firstOrNull { it.index == audioIndex } ?: audios.firstOrNull { it.isDefault == true } ?: audios.firstOrNull()
    val a = audio?.let { audioCodecLabel(it) }.orEmpty()
    when {
        a.contains("Atmos") -> out += FormatBadge(AvKind.Atmos, "杜比全景声", "ATMOS", null, SOUND_NOTE)
        a == "DTS:X" -> out += FormatBadge(AvKind.DtsX, "DTS:X", null, null, SOUND_NOTE)
        a == "DTS-HD MA" -> out += FormatBadge(AvKind.DtsHdMa, "DTS-HD MA", "无损", null, SOUND_NOTE)
        a == "TrueHD" -> out += FormatBadge(AvKind.TrueHd, "TrueHD", "无损", null, SOUND_NOTE)
    }
    return out
}

/**
 * 一枚格式徽记的矢量图：[AvMarkArt] 的路径 + 主题的颜色（墨底 [GugaColors.badgePlate]、框线和大字米白、内框细线和小字 [GugaColors.dim]）。
 * 视口高 [AV_MARK_HEIGHT]，宽按字长；画的时候只给高，宽跟着比例走。
 */
internal fun avMarkVector(art: AvMarkArt, colors: GugaColors): ImageVector {
    val b = ImageVector.Builder("av-mark", art.width.dp, AV_MARK_HEIGHT.dp, art.width, AV_MARK_HEIGHT)
    fun fill(d: String, color: Color, type: PathFillType = PathFillType.NonZero) {
        b.addPath(PathParser().parsePathString(d).toNodes(), pathFillType = type, fill = SolidColor(color))
    }
    fill(art.plate, colors.badgePlate)
    fill(art.frame, colors.ink, PathFillType.EvenOdd)
    fill(art.hairline, colors.dim, PathFillType.EvenOdd)
    fill(art.word, colors.ink)
    fill(art.flank, colors.dim)
    fill(art.caption, colors.dim)
    return b.build()
}

/**
 * 开播那几秒的效果标签：画面真的开始放（Ready 且不在缓冲）后出现 [showMs]，然后淡出；换一集 / 换版本再来一次。
 * [scale]：手机 1、电脑 1.15、电视 1.6。没有效果的片子什么都不画。
 */
@Composable
fun FormatFlash(snap: PlayerSnapshot, scale: Float, modifier: Modifier = Modifier, showMs: Long = FORMAT_FLASH_MS) {
    val badges = remember(snap.resolved?.mediaSource, snap.audioIndex) { formatBadges(snap.resolved?.mediaSource, snap.audioIndex, DeviceAv.caps) }
    FormatFlash(badges, started = snap.phase == PlayPhase.Ready && !snap.buffering, key = snap.itemId + "|" + snap.sourceId, scale = scale, modifier = modifier, showMs = showMs)
}

/** 同上，徽章和「开始放了没有」由调用方给（[key] 换了 = 换了一集 / 换了版本，再来一次）。离屏测试直接用这个。 */
@Composable
fun FormatFlash(badges: List<FormatBadge>, started: Boolean, key: String, scale: Float, modifier: Modifier = Modifier, showMs: Long = FORMAT_FLASH_MS) {
    if (badges.isEmpty()) return
    var visible by remember(key) { mutableStateOf(false) }
    var shown by remember(key) { mutableStateOf(false) }
    // 第一次真的开始放：记一下「该亮了」
    LaunchedEffect(started, key) {
        if (started && !shown) shown = true
    }
    // 亮 [showMs] 再淡出——单独计时，不挂在 started 上：开播头几秒常常再缓冲一下，以前这一下把计时取消了，
    // 徽章已经亮出来、又记成展示过，就再也不隐去（用户 2026-11-17：「4k hdr这种角标不消失？只在起播的时候显示5s就行了」）
    LaunchedEffect(shown, key) {
        if (shown) {
            visible = true
            delay(showMs)
            visible = false
        }
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(260)), exit = fadeOut(tween(600))) {
        FormatBadgeRow(badges, scale)
    }
}

/** 提示那一行的字号：10 × 端的倍数；电视上不小于 22（`platforms.md` §3）。纯函数。 */
fun formatNoteSp(scale: Float, tv: Boolean): Float = (10f * scale).let { if (tv) maxOf(it, 22f) else it }

/** 徽章下面那一行小字：实际怎么放（「杜比视界：这台设备不支持双层，按 HDR10 播放　·　TrueHD：由音箱 / 功放解码」）；没有要说的 = null。纯函数。 */
fun formatNoteLine(badges: List<FormatBadge>): String? =
    badges.filter { it.note != null }.joinToString("　·　") { "${it.zh}：${it.note}" }.takeIf { it.isNotEmpty() }

/**
 * 一排效果徽章。徽章不看设备能力，一律照常亮。
 * [notes] = 徽章下面一行小字写这台设备实际怎么放（「杜比视界：这台设备不支持双层，按 HDR10 播放 · TrueHD：由音箱 / 功放解码」；
 * 只有播放器里开播时写，详情页上不写）。备注合成一行排在整排下面，不挂在各自的徽章下（挂在下面会把徽章撑开、间距不齐）。
 * 电视上这一行不小于 22 px（`platforms.md` §3 的 10 英尺字号）。
 */
@Composable
fun FormatBadgeRow(badges: List<FormatBadge>, scale: Float, modifier: Modifier = Modifier, notes: Boolean = true) {
    if (badges.isEmpty()) return
    val colors = Guga.colors
    val noteLine = if (notes) formatNoteLine(badges) else null
    Column(modifier.testTag("format-badges")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            for (b in badges) {
                // 每种效果都有一枚（AvMarkArtTest 守着）；万一将来加了种类没出图，宁可不画也不画错
                val art = AV_MARK_ART[b.kind] ?: continue
                val mark = remember(art, colors) { avMarkVector(art, colors) }
                // 徽记：高 44dp、宽 art.width dp，两边都 × 端的倍数。只给高不行：宽落回矢量的固有宽度（没乘倍数），
                // ContentScale.Fit 被宽卡住、放大倍数 = 1，多出来的高变成空白（Windows Pi 量到电视详情页节点 66 px、墨底只有 44 px）
                Image(mark, listOfNotNull(b.zh, b.en).joinToString(" "), Modifier.size(art.width.dp * scale, AV_MARK_HEIGHT.dp * scale).testTag("format-${b.kind.name.lowercase()}"))
            }
        }
        if (noteLine != null) {
            val size = formatNoteSp(scale, Guga.uiMode == com.gugugaga.ds.theme.UiMode.Tv)
            BasicText(noteLine, Modifier.padding(top = 5.dp * scale).testTag("format-note"), style = Guga.note(size.sp, colors.dim), maxLines = 2)
        }
    }
}
