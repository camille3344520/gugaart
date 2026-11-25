package com.gugaart.ui.subtitle

/*
 * 下载回来的字幕：认编码、校验像不像字幕（docs/subtitle-search.md §1.1 / §4.3）。
 *
 * 规则移植自 MeiamSubtitles（Apache License 2.0，Copyright Meiam）`Shared/SubtitleContentValidator.cs`：
 * BOM（UTF-16LE / UTF-16BE / UTF-8）→ 否则依次试 严格 UTF-8、UTF-16LE、UTF-16BE、GB18030，解出来像字幕的那一种就是；
 * 压缩包（`PK`、`Rar!`）和 HTML / JSON 错误页拒收。改动：GB18030 是平台缝（[decodeGb18030]：JVM 的 Charset、iOS 的 NSString）；
 * 结果是 Kotlin 字符串（本机字幕直接以 UTF-8 交给内核）。
 */

/** 下载的字幕格式（这一版收的三种）。 */
enum class SubFormat(val ext: String) {
    Srt("srt"),
    Ass("ass"),
    Ssa("ssa");

    companion object {
        /** `"srt"` / `".ASS"` → 格式；别的（smi、sub、idx…）= null。和 MeiamSub 的 NormalizeFormat 一样。 */
        fun of(s: String?): SubFormat? = when (s?.trim()?.trimStart('.')?.lowercase()) {
            "srt" -> Srt
            "ass" -> Ass
            "ssa" -> Ssa
            else -> null
        }
    }
}

/** 解码的结果。 */
sealed interface DecodedSubtitle {
    data class Ok(val text: String, val encoding: String) : DecodedSubtitle

    /** [why] 是给人看的中文。 */
    data class Bad(val why: String) : DecodedSubtitle
}

/** GB18030 严格解码（解不了 = null）。JVM：`Charset.forName("GB18030")`；iOS：`NSString` + `kCFStringEncodingGB_18030_2000`。 */
expect fun decodeGb18030(bytes: ByteArray): String?

object SubtitleText {
    private const val BOM = '﻿'

    /** 像不像 [format] 的字幕：SRT 有 `-->`；ASS / SSA 有 `[Script Info]` 或 `Dialogue:`。 */
    fun looksLike(text: String, format: SubFormat): Boolean = if (format == SubFormat.Srt) "-->" in text
    else text.contains("[Script Info]", ignoreCase = true) || text.contains("Dialogue:", ignoreCase = true)

    /** 字节 → 文本。纯逻辑（GB18030 那一步走平台缝），有单测。 */
    fun decode(data: ByteArray, format: SubFormat, contentType: String? = null): DecodedSubtitle {
        if (data.size < 8) return DecodedSubtitle.Bad("下载到的字幕是空的")
        if ((data[0] == 'P'.code.toByte() && data[1] == 'K'.code.toByte()) || data.startsWith("Rar!\u001a\u0007")) {
            return DecodedSubtitle.Bad("下载到的是压缩包，不是字幕")
        }
        val ct = contentType?.lowercase().orEmpty()
        if ("html" in ct || "json" in ct) return DecodedSubtitle.Bad("字幕站回的是一张错误页")
        var errorDoc = false
        for ((name, text) in candidates(data)) {
            val t = text.trimStart(BOM, ' ', '\r', '\n', '\t')
            if (t.startsWith("<") || t.startsWith("{")) {
                errorDoc = true
                continue
            }
            if (looksLike(t, format)) return DecodedSubtitle.Ok(t, name)
            if (t.startsWith("[")) errorDoc = true
        }
        return if (errorDoc) DecodedSubtitle.Bad("字幕站回的是一张错误页") else DecodedSubtitle.Bad("下载到的不是 ${format.ext.uppercase()} 字幕")
    }

    /** 依次要试的解码（有 BOM 就只有那一种）。 */
    private fun candidates(data: ByteArray): Sequence<Pair<String, String>> = sequence {
        val b0 = data[0].toInt() and 0xff
        val b1 = data[1].toInt() and 0xff
        if (b0 == 0xFF && b1 == 0xFE) {
            utf16(data, 2, littleEndian = true)?.let { yield("UTF-16LE" to it) }
            return@sequence
        }
        if (b0 == 0xFE && b1 == 0xFF) {
            utf16(data, 2, littleEndian = false)?.let { yield("UTF-16BE" to it) }
            return@sequence
        }
        if (b0 == 0xEF && b1 == 0xBB && (data[2].toInt() and 0xff) == 0xBF) {
            utf8(data, 3)?.let { yield("UTF-8" to it) }
            return@sequence
        }
        utf8(data, 0)?.let { yield("UTF-8" to it) }
        utf16(data, 0, littleEndian = true)?.let { yield("UTF-16LE" to it) }
        utf16(data, 0, littleEndian = false)?.let { yield("UTF-16BE" to it) }
        decodeGb18030(data)?.let { yield("GB18030" to it) }
    }

    private fun utf8(data: ByteArray, from: Int): String? = try {
        data.decodeToString(from, data.size, throwOnInvalidSequence = true)
    } catch (_: CharacterCodingException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /** 严格 UTF-16：字节数是双数、代理对成对；否则 null。 */
    internal fun utf16(data: ByteArray, from: Int, littleEndian: Boolean): String? {
        val n = data.size - from
        if (n <= 0 || n % 2 != 0) return null
        val chars = CharArray(n / 2)
        for (i in chars.indices) {
            val a = data[from + i * 2].toInt() and 0xff
            val b = data[from + i * 2 + 1].toInt() and 0xff
            chars[i] = (if (littleEndian) (b shl 8) or a else (a shl 8) or b).toChar()
        }
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            if (c.isHighSurrogate()) {
                if (i + 1 >= chars.size || !chars[i + 1].isLowSurrogate()) return null
                i += 2
                continue
            }
            if (c.isLowSurrogate()) return null
            i++
        }
        return chars.concatToString()
    }

    private fun ByteArray.startsWith(ascii: String): Boolean {
        if (size < ascii.length) return false
        for (i in ascii.indices) if (this[i] != ascii[i].code.toByte()) return false
        return true
    }
}
