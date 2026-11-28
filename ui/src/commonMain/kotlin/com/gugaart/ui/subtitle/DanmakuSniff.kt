package com.gugaart.ui.subtitle

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 弹幕文件冒充字幕（1.5.2 subdm，docs/subtitle-search.md §4.5）：有人把 B 站视频的弹幕导出成 ASS / SRT 传进迅雷的字幕库，
 * 迅雷当字幕返回（「围棋少年 13」前 8 条里 4 条是：`AcplayDefault` 样式、每一条 Dialogue 都是 `{\move(2048, 64, -128, 64)}弹幕护体`）。
 * 这里按**内容**判（纯函数，有单测）；名字只用来在查完之前先压后（[nameLooksLikeDanmaku]）。
 *
 * 判定要保守：正经的特效字幕（`\pos`、`\fad`、卡拉 OK、少量 `\move`）不能误判——
 * 弹幕的特征是「绝大多数对白都是同一高度、从画面一边外面飘到另一边外面」，正常字幕（连特效字幕）都没有这么多横向飘过的行；
 * SRT 没有位置，看密度和重叠：正常字幕一句接一句，几乎不重叠。
 */

/** 判定结果。[reason]：一句中文（日志 / 汇报用，界面上不写）。 */
data class DanmakuVerdict(val isDanmaku: Boolean, val reason: String)

/** 行上写的短标签（点的时候判成弹幕：不加载、不上传）。 */
const val DANMAKU_LABEL = "这是弹幕，不是字幕"

object DanmakuSniff {
    /** ASS / SSA：对白少于这么多条不判（太少看不出规律）。 */
    const val MIN_ASS_LINES = 20

    /** ASS / SSA：横向飘过的对白**超过**这个比例 = 弹幕。 */
    const val FLY_SHARE = 0.5

    /** ASS / SSA：有弹幕工具签名时，横向飘过的对白**达到**这个比例就算。 */
    const val SIGNED_FLY_SHARE = 0.3

    /** `PlayResX` 没写时按 ASS 规范的默认宽 384（libass 同）——宽度未知时取最小的常见值，「右边外」放宽判断。 */
    const val DEFAULT_PLAY_RES_X = 384.0

    /** SRT：条目少于这么多不判。 */
    const val MIN_SRT_CUES = 20

    /** SRT：每分钟**不少于**这么多条。 */
    const val SRT_PER_MINUTE = 30.0

    /** SRT：和前一条重叠的条目**超过**这个比例。 */
    const val SRT_OVERLAP_SHARE = 0.5

    /** SRT：和前一条重叠至少这么久才算（正常字幕首尾差几十毫秒的不算）。 */
    const val SRT_OVERLAP_MS = 500L

    /**
     * 弹幕转换工具在样式名 / `[Script Info]`（含 `;` 注释）里留下的签名（不分大小写，包含即算）。
     * AcplayDefault（弹弹play / Acplay 系）、Danmaku2ASS、DanmakuFactory、biliass、Niconvert、BiliLocal、
     * 「Bilibili ASS Danmaku Downloader」脚本、danmu2ass / DanmakuConverter 这类。
     */
    val SIGNATURES = listOf(
        "AcplayDefault",
        "Danmaku2ASS",
        "DanmakuFactory",
        "biliass",
        "Niconvert",
        "BiliLocal",
        "ASS Danmaku",
        "danmu2ass",
        "DanmakuConverter",
    )

    /** 按内容判（看内容认 ASS / SRT，不只信扩展名：迅雷的 `.srt` 里也可能是 ASS）。 */
    fun judge(text: String, format: SubFormat? = null): DanmakuVerdict {
        val ass = text.contains("[Events]", ignoreCase = true) || DIALOGUE_HEAD.containsMatchIn(text)
        return when {
            ass -> judgeAss(text)
            "-->" in text -> judgeSrt(text)
            format == SubFormat.Srt -> judgeSrt(text)
            else -> DanmakuVerdict(false, "认不出格式，不判")
        }
    }

    // ---------------------------------------------------------------- ASS / SSA

    fun judgeAss(text: String): DanmakuVerdict {
        var section = ""
        var playResX: Double? = null
        val info = StringBuilder()
        val styles = ArrayList<String>()
        var fields = 10
        var dialogues = 0
        var flying = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('\uFEFF')
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.lowercase()
                continue
            }
            when {
                section == "[script info]" -> {
                    info.append(line).append('\n')
                    if (line.startsWith("PlayResX", ignoreCase = true)) {
                        playResX = line.substringAfter(':').trim().toDoubleOrNull()?.takeIf { it > 0 }
                    }
                }
                section.contains("styles") -> if (line.startsWith("Style:", ignoreCase = true)) {
                    styles += line.substringAfter(':').substringBefore(',').trim()
                }
                // 没有 [Events] 段头的残缺文件：Dialogue 行照样算
                else -> when {
                    line.startsWith("Format:", ignoreCase = true) && section == "[events]" ->
                        fields = line.substringAfter(':').split(',').size.coerceAtLeast(1)
                    line.startsWith("Dialogue:", ignoreCase = true) -> {
                        dialogues++
                        val body = line.substringAfter(':').split(',', limit = fields).last()
                        if (isFlyingLine(body, playResX ?: DEFAULT_PLAY_RES_X)) flying++
                    }
                }
            }
        }
        val signature = signatureOf(info.toString(), styles)
        if (dialogues == 0) return DanmakuVerdict(false, "ASS：没有对白")
        val share = flying.toDouble() / dialogues
        val pct = "${(share * 100).roundToInt()}%"
        val res = if (playResX != null) "PlayResX ${playResX.toInt()}" else "没写 PlayResX（按 384 放宽）"
        return when {
            dialogues >= MIN_ASS_LINES && share > FLY_SHARE ->
                DanmakuVerdict(true, "ASS：$dialogues 条对白里 $flying 条横向飘过（$pct，$res）" + (signature?.let { "，签名 $it" } ?: ""))
            signature != null && flying > 0 && share >= SIGNED_FLY_SHARE ->
                DanmakuVerdict(true, "ASS：弹幕工具签名 $signature，横向飘过 $flying / $dialogues（$pct）")
            dialogues < MIN_ASS_LINES && signature == null ->
                DanmakuVerdict(false, "ASS：对白只有 $dialogues 条（少于 $MIN_ASS_LINES 条不判）")
            else -> DanmakuVerdict(false, "ASS：$dialogues 条对白，横向飘过 $flying 条（$pct）" + (signature?.let { "，签名 $it 但飘过的不够三成" } ?: ""))
        }
    }

    /** 弹幕工具的签名（样式名、脚本信息）；没有 = null。 */
    fun signatureOf(scriptInfo: String, styleNames: List<String>): String? =
        SIGNATURES.firstOrNull { sig -> styleNames.any { it.contains(sig, ignoreCase = true) } || scriptInfo.contains(sig, ignoreCase = true) }

    /**
     * 一条对白是不是「横向飘过」：有 `\move(x1, y1, x2, y2[, t1, t2])`，y1 = y2（同一高度），从右往左走、走过半个画面以上，
     * 并且起点在画面右边外（x1 ≥ [resX]）或终点在左边外（x2 < 0）。带卡拉 OK（`\k` `\kf` `\ko` `\K`）的不算（逐字特效）。
     */
    fun isFlyingLine(body: String, resX: Double): Boolean {
        if (KARAOKE.containsMatchIn(body)) return false
        val m = MOVE.find(body) ?: return false
        val (x1, y1, x2, y2) = m.destructured.toList().map { it.toDouble() }
        return isFlyBy(x1, y1, x2, y2, resX)
    }

    fun isFlyBy(x1: Double, y1: Double, x2: Double, y2: Double, resX: Double): Boolean {
        if (abs(y1 - y2) > 0.5) return false
        if (x1 <= x2) return false
        if (x1 - x2 < resX * 0.5) return false
        return x1 >= resX || x2 < 0
    }

    // ---------------------------------------------------------------- SRT

    /** 一条 SRT 的时间（毫秒）。 */
    data class Cue(val start: Long, val end: Long)

    fun judgeSrt(text: String): DanmakuVerdict {
        val cues = SRT_TIME.findAll(text).map { m ->
            val g = m.groupValues
            Cue(clock(g[1], g[2], g[3], g[4]), clock(g[5], g[6], g[7], g[8]))
        }.filter { it.end >= it.start }.sortedBy { it.start }.toList()
        // 双语字幕常常把中文、英文写成两条时间一模一样的：当一条
        val groups = ArrayList<Cue>(cues.size)
        for (c in cues) if (groups.lastOrNull() != c) groups += c
        val n = groups.size
        if (n < MIN_SRT_CUES) return DanmakuVerdict(false, "SRT：只有 $n 条（少于 $MIN_SRT_CUES 条不判）")
        val span = (groups.maxOf { it.end } - groups.first().start).coerceAtLeast(60_000L)
        val perMinute = n * 60_000.0 / span
        var overlaps = 0
        for (i in 1 until n) {
            val prev = groups[i - 1]
            val cur = groups[i]
            if (cur.start > prev.start && prev.end - cur.start >= SRT_OVERLAP_MS) overlaps++
        }
        val share = overlaps.toDouble() / (n - 1)
        val why = "SRT：$n 条，每分钟 ${perMinute.roundToInt()} 条，${(share * 100).roundToInt()}% 和前一条重叠"
        return DanmakuVerdict(perMinute >= SRT_PER_MINUTE && share > SRT_OVERLAP_SHARE, why)
    }

    private fun clock(h: String, m: String, s: String, ms: String): Long =
        ((h.toLong() * 60 + m.toLong()) * 60 + s.toLong()) * 1000 + ms.padEnd(3, '0').take(3).toLong()

    // ---------------------------------------------------------------- 名字

    /**
     * 名字像弹幕文件（查完之前先压到后面）：有 B 站视频号（`av` + 至少 5 位数字——`AV1` 是编码名不算；`BV1` 开头的 12 位）、
     * 「弹幕」、`danmaku`。纯函数，有单测。
     */
    fun nameLooksLikeDanmaku(name: String): Boolean =
        "弹幕" in name || "彈幕" in name || name.contains("danmaku", ignoreCase = true) || AV_ID.containsMatchIn(name) || BV_ID.containsMatchIn(name)

    private const val NUM = """(-?\d+(?:\.\d+)?)"""
    private val MOVE = Regex("""\\move\(\s*$NUM\s*,\s*$NUM\s*,\s*$NUM\s*,\s*$NUM""", RegexOption.IGNORE_CASE)
    private val KARAOKE = Regex("""\\[kK][fo]?\d""")
    private val DIALOGUE_HEAD = Regex("""^\s*Dialogue:""", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
    private val SRT_TIME = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")
    private val AV_ID = Regex("""(^|[^A-Za-z0-9])[aA][vV]\d{5,}""")
    private val BV_ID = Regex("""(^|[^A-Za-z0-9])BV1[0-9A-Za-z]{9}([^0-9A-Za-z]|$)""")
}

/**
 * 判过的结果按下载地址记在内存里（进程内，最多 [MAX] 条）：同一次播放里后台那一次（自动加载）和打开栏的那一次不重复下载。
 * 只记判出来的（下载失败的不记，下次再试）。
 */
object DanmakuCache {
    const val MAX = 300
    private val map = LinkedHashMap<String, DanmakuVerdict>()

    fun get(url: String): DanmakuVerdict? = runCatching { map[url] }.getOrNull()

    fun put(url: String, v: DanmakuVerdict) {
        // 只是缓存：万一两个线程同时写坏了，大不了再下一次
        runCatching {
            map.remove(url)
            map[url] = v
            while (map.size > MAX) map.remove(map.keys.first())
        }
    }

    fun clear() {
        runCatching { map.clear() }
    }
}
