package com.gugaart.ui.data

import com.gugugaga.core.MediaStream

/*
 * 音轨 / 字幕的名字（审计 P1-2，1.5.1）：三端、详情页和播放器都用这一个函数。
 *
 * 写法「语言中文名 · 编码 · 声道」：「国语 · AAC 2.0」「中文（简体）· SRT」「英语 · ASS」。默认那条加小签「默认」，外挂的加小签「外挂」。
 * 语言不知道（und / 空）时用 Emby 给的名字（DisplayTitle / Title），再没有写「未命名」。
 */

/** 一条轨的名字和小签（「默认」「外挂」「强制」）。 */
data class TrackName(val text: String, val tags: List<String>) {
    /** 名字后面跟着小签，用在只有一行字的地方（「国语 · AAC 2.0 · 默认」）。 */
    val line: String get() = (listOf(text) + tags).let { joinTrackParts(it) }
}

private val SIMPLIFIED_HINT = Regex("""(^|[^a-z])(chs|zhs|zh-hans|zh_hans|zh-cn|zh_cn|zh-sg|gb|gbk|gb2312|sc|simplified)([^a-z]|$)""")
private val TRADITIONAL_HINT = Regex("""(^|[^a-z])(cht|zht|zh-hant|zh_hant|zh-tw|zh_tw|zh-hk|zh_hk|big5|tc|traditional)([^a-z]|$)""")

/** 语言码（ISO 639-1 / 639-2，Emby 给的多是 chi / eng / jpn）→ 中文名；不认识 / und = null。 */
fun languageName(code: String?): String? = when (code?.trim()?.lowercase()?.replace('_', '-')) {
    null, "", "und", "unknown", "mul", "zxx" -> null
    "chi", "zho", "zh", "cmn", "chs", "cht" -> "中文"
    "zh-hans", "zh-cn", "zh-sg" -> "中文（简体）"
    "zh-hant", "zh-tw", "zh-hk" -> "中文（繁体）"
    "yue" -> "粤语"
    "eng", "en" -> "英语"
    "jpn", "ja" -> "日语"
    "kor", "ko" -> "韩语"
    "fre", "fra", "fr" -> "法语"
    "ger", "deu", "de" -> "德语"
    "rus", "ru" -> "俄语"
    "spa", "es" -> "西班牙语"
    "por", "pt" -> "葡萄牙语"
    "ita", "it" -> "意大利语"
    "tha", "th" -> "泰语"
    "vie", "vi" -> "越南语"
    "ind", "id" -> "印尼语"
    "may", "msa", "ms" -> "马来语"
    "ara", "ar" -> "阿拉伯语"
    "hin", "hi" -> "印地语"
    "tur", "tr" -> "土耳其语"
    "dut", "nld", "nl" -> "荷兰语"
    "pol", "pl" -> "波兰语"
    "swe", "sv" -> "瑞典语"
    "ukr", "uk" -> "乌克兰语"
    "heb", "he" -> "希伯来语"
    else -> null
}

/** 编码的写法：SRT / ASS / PGS / AAC / AC3 / DTS / TrueHD …；没有 = null。 */
fun codecName(codec: String?): String? = when (val c = codec?.trim()?.lowercase()) {
    null, "" -> null
    "subrip", "srt" -> "SRT"
    "ass" -> "ASS"
    "ssa" -> "SSA"
    "pgssub", "hdmv_pgs_subtitle", "pgs" -> "PGS"
    "dvdsub", "dvd_subtitle", "vobsub" -> "VobSub"
    "webvtt", "vtt" -> "VTT"
    "mov_text", "tx3g" -> "TX3G"
    "truehd" -> "TrueHD"
    "eac3" -> "EAC3"
    "opus" -> "Opus"
    "vorbis" -> "Vorbis"
    else -> if (c.startsWith("pcm")) "PCM" else c.uppercase()
}

/** 声道：2.0 / 5.1 / 7.1 / 单声道；没有 = null。 */
fun channelsName(channels: Int?): String? = when (channels) {
    null, 0 -> null
    1 -> "单声道"
    2 -> "2.0"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$channels 声道"
}

/** 「中文（简体）· SRT」：全角括号后面不再空一格；其余用「 · 」。 */
fun joinTrackParts(parts: List<String>): String {
    val sb = StringBuilder()
    for ((i, p) in parts.withIndex()) {
        if (i > 0) sb.append(if (parts[i - 1].endsWith("）")) "· " else " · ")
        sb.append(p)
    }
    return sb.toString()
}

/** 一条轨的语言名：中文字幕再分简体 / 繁体（看标题里的「简 / 繁 / chs / cht …」）；中文音轨写「国语」。 */
private fun trackLanguage(s: MediaStream): String? {
    val base = languageName(s.language) ?: return null
    val title = listOfNotNull(s.title, s.displayTitle).joinToString(" ").lowercase()
    if (base != "中文") return base
    if (s.type == "Audio") return if ('粤' in title || "cantonese" in title) "粤语" else "国语"
    return when {
        '简' in title || '簡' in title || SIMPLIFIED_HINT.containsMatchIn(title) || s.language.equals("chs", true) -> "中文（简体）"
        '繁' in title || TRADITIONAL_HINT.containsMatchIn(title) || s.language.equals("cht", true) -> "中文（繁体）"
        else -> "中文"
    }
}

/** 音轨 / 字幕的名字（P1-2）。纯函数，有单测。 */
fun trackName(s: MediaStream): TrackName {
    val lang = trackLanguage(s) ?: (s.displayTitle ?: s.title)?.trim()?.takeIf { it.isNotEmpty() && it != "und" } ?: "未命名"
    val codec = codecName(s.codec)
    val tail = when (s.type) {
        "Audio" -> listOfNotNull(codec, channelsName(s.channels)).joinToString(" ").takeIf { it.isNotEmpty() }
        else -> codec
    }
    // 语言不知道时名字是 Emby 的 DisplayTitle，里面常常已经带了编码（「Chinese (SRT)」）：不再重复
    val parts = listOfNotNull(lang, tail?.takeIf { !lang.contains(it, ignoreCase = true) })
    val tags = listOfNotNull(
        "默认".takeIf { s.isDefault == true },
        "外挂".takeIf { s.type == "Subtitle" && s.isExternal == true },
        "强制".takeIf { s.isForced == true },
    )
    return TrackName(joinTrackParts(parts), tags)
}
