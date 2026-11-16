package com.gugaart.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.MediaStream
import com.gugugaga.core.audioCodecLabel
import com.gugugaga.core.defaultVideoStream
import com.gugugaga.core.hdrLabel
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugaart.ui.res.Res
import com.gugaart.ui.res.av_4k
import com.gugaart.ui.res.av_atmos
import com.gugaart.ui.res.av_dolby_vision
import com.gugaart.ui.res.av_dtshdma
import com.gugaart.ui.res.av_dtsx
import com.gugaart.ui.res.av_hdr10
import com.gugaart.ui.res.av_hdr10plus
import com.gugaart.ui.res.av_hlg
import com.gugaart.ui.res.av_truehd
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlinx.coroutines.delay

/*
 * 开播时显示片源的效果（用户 2026-11-16：「播放影片的时候 开播的时候可以显示下支持的效果，比如 hdr，杜比视界、杜比全景声」）。
 *
 * 识别用上游的 `hdrLabel` / `audioCodecLabel`（Emby 的 ExtendedVideoType、颜色传输、音轨名里的 Atmos / DTS:X）。
 * 标签只负责「说清楚」，不负责「打开」：效果由设备自己决定（HDR 片子电视自己切 HDR；杜比视界要芯片支持；全景声要音箱 / 功放解码）。
 * 壳在启动时报上这台设备的屏幕能显示什么（[DeviceAv]，安卓读 Display 的 HDR 能力和有没有杜比视界解码器；电脑上不知道 = 不写备注）：
 * 设备不支持的标签变灰，写明实际会怎么放（「这台设备按 HDR10 显示」）；全景声 / DTS:X 应用查不到有没有接音箱，写「由音箱 / 功放解码」。
 * 4K / 杜比视界 / HDR10+ / HDR10 / HLG / 杜比全景声 / DTS:X / DTS-HD MA / TrueHD 是九枚复古电影质感的徽章图（用户 2026-11-16：「加点电影和或者画报质感」；
 * Gemini 生成，scripts/art/av_badges.py 抠成透明 PNG）：图上是名字的文字，不是杜比 / DTS 的官方 Logo 图案（那是注册商标）。
 * 说不清是哪种的「HDR」没有图，用文字小签。详情页也挂同一排（不写备注，用户 2026-11-16：「影片详情页也可以打上对应的图标」）。
 */

/** 这台设备的屏幕能显示哪些 HDR（壳在启动时填；不知道 = null）。 */
@Immutable
data class AvCaps(val hdr10: Boolean, val hdr10Plus: Boolean, val hlg: Boolean, val dolbyVision: Boolean)

object DeviceAv {
    var caps: AvCaps? = null
}

/** 画面：4K、各种 HDR；声音：全景声、DTS:X、两种无损（用户 2026-11-16：「看下还有没有其他什么格式需要弄的呢」）。 */
enum class AvKind { Uhd4k, DolbyVision, Hdr10Plus, Hdr10, Hlg, Hdr, Atmos, DtsX, DtsHdMa, TrueHd }

/** 一个效果标签。[supported] null = 不知道（不写备注）；false = 这台设备显示不了，[note] 写实际会怎么放。 */
@Immutable
data class FormatBadge(val kind: AvKind, val zh: String, val en: String?, val supported: Boolean?, val note: String?)

private const val SOUND_NOTE = "由音箱 / 功放解码"

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
            val note = when {
                caps == null || caps.dolbyVision -> null
                fallback == "HDR10" && caps.hdr10 -> "这台设备按 HDR10 显示"
                fallback == "HLG" && caps.hlg -> "这台设备按 HLG 显示"
                fallback == null && caps.hdr10 -> "这台设备按 HDR 显示"
                else -> plain()
            }
            out += FormatBadge(AvKind.DolbyVision, "杜比视界", "DOLBY VISION", caps?.dolbyVision, note)
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

/** 有现成徽章图的效果（复古电影质感，中英文名都在图上）。 */
private fun imageOf(kind: AvKind): DrawableResource? = when (kind) {
    AvKind.Uhd4k -> Res.drawable.av_4k
    AvKind.DolbyVision -> Res.drawable.av_dolby_vision
    AvKind.Hdr10Plus -> Res.drawable.av_hdr10plus
    AvKind.Hdr10 -> Res.drawable.av_hdr10
    AvKind.Hlg -> Res.drawable.av_hlg
    AvKind.Atmos -> Res.drawable.av_atmos
    AvKind.DtsX -> Res.drawable.av_dtsx
    AvKind.DtsHdMa -> Res.drawable.av_dtshdma
    AvKind.TrueHd -> Res.drawable.av_truehd
    AvKind.Hdr -> null
}

/** 文字小签上的小图标。 */
private fun iconOf(kind: AvKind): GuguIcon = when (kind) {
    AvKind.Uhd4k -> GuguIcon.Monitor
    AvKind.DolbyVision -> GuguIcon.Eye
    AvKind.Hdr10Plus, AvKind.Hdr10, AvKind.Hdr, AvKind.Hlg -> GuguIcon.Sun
    AvKind.Atmos, AvKind.DtsX, AvKind.DtsHdMa, AvKind.TrueHd -> GuguIcon.AudioLines
}

/**
 * 开播那几秒的效果标签：画面真的开始放（Ready 且不在缓冲）后出现 [showMs]，然后淡出；换一集 / 换版本再来一次。
 * [scale]：手机 1、电脑 1.15、电视 1.6。没有效果的片子什么都不画。
 */
@Composable
fun FormatFlash(snap: PlayerSnapshot, scale: Float, modifier: Modifier = Modifier, showMs: Long = 5_000) {
    val badges = remember(snap.resolved?.mediaSource, snap.audioIndex) { formatBadges(snap.resolved?.mediaSource, snap.audioIndex, DeviceAv.caps) }
    if (badges.isEmpty()) return
    val started = snap.phase == PlayPhase.Ready && !snap.buffering
    var visible by remember(snap.itemId, snap.sourceId) { mutableStateOf(false) }
    var shown by remember(snap.itemId, snap.sourceId) { mutableStateOf(false) }
    // 第一次真的开始放：记一下「该亮了」
    LaunchedEffect(started, snap.itemId, snap.sourceId) {
        if (started && !shown) shown = true
    }
    // 亮 [showMs] 再淡出——单独计时，不挂在 started 上：开播头几秒常常再缓冲一下，以前这一下把计时取消了，
    // 徽章已经亮出来、又记成展示过，就再也不隐去（用户 2026-11-17：「4k hdr这种角标不消失？只在起播的时候显示5s就行了」）
    LaunchedEffect(shown, snap.itemId, snap.sourceId) {
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

/** 一排效果徽章（设备显示不了的变灰）。
 * [notes] = 徽章下面一行小字写实际怎么放（「杜比视界：这台设备按 HDR10 显示 · TrueHD：由音箱 / 功放解码」；开播时写，详情页上不写，只变灰）。
 * 备注合成一行排在整排下面，不挂在各自的徽章下（挂在下面会把徽章撑开、间距不齐）。
 */
@Composable
fun FormatBadgeRow(badges: List<FormatBadge>, scale: Float, modifier: Modifier = Modifier, notes: Boolean = true) {
    if (badges.isEmpty()) return
    val colors = Guga.colors
    val noteLine = if (notes) badges.filter { it.note != null }.joinToString("　·　") { "${it.zh}：${it.note}" }.takeIf { it.isNotEmpty() } else null
    Column(modifier.testTag("format-badges")) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale), verticalAlignment = Alignment.CenterVertically) {
            for (b in badges) {
                val dim = b.supported == false
                val image = imageOf(b.kind)
                Box(Modifier.alpha(if (dim) 0.62f else 1f).testTag("format-${b.kind.name.lowercase()}")) {
                    if (image != null) {
                        // 徽章图：高 44dp（× 端的倍数），宽按图
                        androidx.compose.foundation.Image(painterResource(image), listOfNotNull(b.zh, b.en).joinToString(" "), Modifier.height(44.dp * scale))
                    } else Row(
                        Modifier.background(colors.inkWell.copy(alpha = 0.78f)).border(1.dp, colors.ink.copy(alpha = if (dim) 0.35f else 0.7f))
                            .padding(horizontal = 9.dp * scale, vertical = 5.dp * scale),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp * scale),
                    ) {
                        Icon(iconOf(b.kind), colors.ink, 15.dp * scale)
                        BasicText(b.zh, style = Guga.song(13.sp * scale, FontWeight.Bold, colors.ink, letterSpacing = 0.06.em), maxLines = 1)
                        if (b.en != null) BasicText(b.en, style = Guga.numeral(10.sp * scale, colors.dim, letterSpacing = 0.16.em), maxLines = 1)
                    }
                }
            }
        }
        if (noteLine != null) BasicText(noteLine, Modifier.padding(top = 5.dp * scale), style = Guga.note(10.sp * scale, colors.dim), maxLines = 2)
    }
}
