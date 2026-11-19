package com.gugaart.desktop.mac.accept

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage

/*
 * macOS 真窗口验收（开发开关 `--accept=…`，发版不带）里不碰窗口的那一半：结论的模型、报告（JSON / Markdown）、退出码、
 * 字符 → 按键、截图的像素读数。全是纯函数，单测在 `AcceptCoreTest`。
 */

/** 一条检查的结论。needs-human = 自动化看不了，要人看；env-skip = 环境缺权限（如屏幕录制）拿不到证据，不算没过。两者都不算过、也不算没过。 */
enum class Verdict(val word: String) {
    Pass("pass"), Fail("fail"), Skip("skip"), EnvSkip("env-skip"), NeedsHuman("needs-human");

    companion object {
        fun of(word: String): Verdict = entries.firstOrNull { it.word == word } ?: Fail
    }
}

/** 一条检查：结论 + 说明 + 证据（数值、截图的文件名，相对于输出目录）。 */
data class Check(
    val id: String,
    val title: String,
    val verdict: Verdict,
    val detail: String = "",
    val values: Map<String, String> = emptyMap(),
    val shots: List<String> = emptyList(),
)

/** 一个场景：对应对齐清单的哪几行（`feature-parity-macos-ios.md` 的 B01 … R08）、各条检查、跑崩了的话是什么错。 */
data class ScenarioResult(
    val id: String,
    val title: String,
    val rows: List<String>,
    val checks: List<Check>,
    val error: String? = null,
    val millis: Long = 0,
) {
    /** 场景的结论：有一条没过（或跑崩了）= fail；否则有过的 = pass；否则有要人看的 = needs-human；都跳过 = skip。 */
    val verdict: Verdict
        get() = when {
            error != null || checks.any { it.verdict == Verdict.Fail } -> Verdict.Fail
            checks.any { it.verdict == Verdict.Pass } -> Verdict.Pass
            checks.any { it.verdict == Verdict.NeedsHuman } -> Verdict.NeedsHuman
            else -> Verdict.Skip
        }
}

/** 一次运行的报告。 */
data class AcceptReport(
    val startedAt: String,
    val finishedAt: String,
    val machine: Map<String, String>,
    val scenarios: List<ScenarioResult>,
) {
    /** 按条数数：pass / fail / skip / needs-human 各几条（跑崩的场景记一条 fail）。 */
    fun totals(): Map<Verdict, Int> {
        val out = Verdict.entries.associateWith { 0 }.toMutableMap()
        for (s in scenarios) {
            for (c in s.checks) out[c.verdict] = out.getValue(c.verdict) + 1
            if (s.error != null) out[Verdict.Fail] = out.getValue(Verdict.Fail) + 1
        }
        return out
    }

    /** 脚本的退出码：0 = 没有一条没过（skip / needs-human 不拦）；1 = 有没过的。 */
    fun exitCode(): Int = if (totals().getValue(Verdict.Fail) > 0) 1 else 0

    /** 把另一次运行的场景并进来（同名的以新的为准，顺序：旧的在前）。 */
    fun merge(other: AcceptReport): AcceptReport {
        val ids = other.scenarios.map { it.id }.toSet()
        return copy(finishedAt = other.finishedAt, scenarios = scenarios.filter { it.id !in ids } + other.scenarios)
    }

    fun toJson(): JsonObject = buildJsonObject {
        put("startedAt", startedAt)
        put("finishedAt", finishedAt)
        put("machine", JsonObject(machine.mapValues { JsonPrimitive(it.value) }))
        put("totals", JsonObject(totals().entries.associate { it.key.word to JsonPrimitive(it.value) }))
        put("exitCode", exitCode())
        put(
            "scenarios",
            buildJsonArray {
                for (s in scenarios) add(
                    buildJsonObject {
                        put("id", s.id)
                        put("title", s.title)
                        put("rows", JsonArray(s.rows.map(::JsonPrimitive)))
                        put("verdict", s.verdict.word)
                        s.error?.let { put("error", it) }
                        put("millis", s.millis)
                        put(
                            "checks",
                            buildJsonArray {
                                for (c in s.checks) add(
                                    buildJsonObject {
                                        put("id", c.id)
                                        put("title", c.title)
                                        put("verdict", c.verdict.word)
                                        put("detail", c.detail)
                                        put("values", JsonObject(c.values.mapValues { JsonPrimitive(it.value) }))
                                        put("shots", JsonArray(c.shots.map(::JsonPrimitive)))
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    fun toJsonText(): String = PRETTY.encodeToString(JsonElement.serializer(), toJson()) + "\n"

    /** 给人看的报告：总数、每个场景一张表、要人看的清单、联系表。 */
    fun toMarkdown(contactSheets: List<String> = emptyList(), humanOnly: List<String> = emptyList()): String = buildString {
        val t = totals()
        appendLine("# macOS 真窗口验收报告")
        appendLine()
        appendLine("- 开始 $startedAt · 结束 $finishedAt")
        machine.forEach { (k, v) -> appendLine("- $k：$v") }
        appendLine()
        if (t.getValue(Verdict.EnvSkip) > 0) {
            appendLine("> ⚠️ **本次缺屏幕录制权限**：像素读数类检查记 `env-skip`（不算没过）。需主理人给 PI-Desktop 授权「屏幕录制」后复跑。")
            appendLine()
        }
        appendLine("| pass | fail | skip | env-skip | needs-human | 退出码 |")
        appendLine("|---|---|---|---|---|---|")
        appendLine("| ${t[Verdict.Pass]} | ${t[Verdict.Fail]} | ${t[Verdict.Skip]} | ${t[Verdict.EnvSkip]} | ${t[Verdict.NeedsHuman]} | ${exitCode()} |")
        appendLine()
        appendLine("| 场景 | 对齐清单 | 结论 | 条数（过 / 没过 / 跳过 / 环境跳 / 要人看） | 用时 |")
        appendLine("|---|---|---|---|---|")
        for (s in scenarios) {
            val n = Verdict.entries.map { v -> s.checks.count { it.verdict == v } }
            appendLine("| `${s.id}` ${s.title} | ${s.rows.joinToString(" ")} | **${s.verdict.word}** | ${n.joinToString(" / ")} | ${s.millis / 1000} s |")
        }
        for (s in scenarios) {
            appendLine()
            appendLine("## `${s.id}` ${s.title} — ${s.verdict.word}")
            appendLine()
            appendLine("对齐清单：${s.rows.joinToString(" ")}")
            s.error?.let { appendLine(); appendLine("**跑崩了**：${md(it)}") }
            appendLine()
            appendLine("| 结论 | 检查 | 说明 | 数 | 截图 |")
            appendLine("|---|---|---|---|---|")
            for (c in s.checks) {
                val values = c.values.entries.joinToString("<br>") { "${md(it.key)}=${md(it.value)}" }
                val shots = c.shots.joinToString(" ") { "[${it.substringAfterLast('/')}]($it)" }
                appendLine("| ${badge(c.verdict)} | ${md(c.title)} | ${md(c.detail)} | $values | $shots |")
            }
        }
        if (humanOnly.isNotEmpty()) {
            appendLine()
            appendLine("## 需要人看（没有自动化，也没有假装自动化）")
            appendLine()
            humanOnly.forEach { appendLine("- $it") }
        }
        if (contactSheets.isNotEmpty()) {
            appendLine()
            appendLine("## 截图联系表")
            appendLine()
            contactSheets.forEach { appendLine("![]($it)") }
        }
    }

    companion object {
        private val PRETTY = Json { prettyPrint = true }

        private fun badge(v: Verdict) = when (v) {
            Verdict.Pass -> "✅ pass"
            Verdict.Fail -> "❌ **fail**"
            Verdict.Skip -> "⏭ skip"
            Verdict.NeedsHuman -> "👀 needs-human"
            Verdict.EnvSkip -> "🌫 env-skip"
        }

        /** 表格里的一格：竖线和换行不能原样进去。 */
        internal fun md(s: String): String = s.replace("|", "\\|").replace("\r", "").replace("\n", "<br>")

        fun fromJson(text: String): AcceptReport {
            val o = Json.parseToJsonElement(text).jsonObject
            fun str(e: JsonElement?) = e?.jsonPrimitive?.content.orEmpty()
            fun map(e: JsonElement?) = (e as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            return AcceptReport(
                startedAt = str(o["startedAt"]),
                finishedAt = str(o["finishedAt"]),
                machine = map(o["machine"]),
                scenarios = o["scenarios"]?.jsonArray.orEmpty().map { se ->
                    val s = se.jsonObject
                    ScenarioResult(
                        id = str(s["id"]),
                        title = str(s["title"]),
                        rows = s["rows"]?.jsonArray.orEmpty().map { str(it) },
                        error = s["error"]?.let { str(it) },
                        millis = s["millis"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
                        checks = s["checks"]?.jsonArray.orEmpty().map { ce ->
                            val c = ce.jsonObject
                            Check(
                                id = str(c["id"]),
                                title = str(c["title"]),
                                verdict = Verdict.of(str(c["verdict"])),
                                detail = str(c["detail"]),
                                values = map(c["values"]),
                                shots = c["shots"]?.jsonArray.orEmpty().map { str(it) },
                            )
                        },
                    )
                },
            )
        }
    }
}

/** `--accept=` 的值：`all` 或用 `+` 连起来的场景名（`-Pgugaart.args` 用逗号分参数，所以不用逗号）。不认识的名字原样留着，由运行器报 fail。 */
fun parseScenarioList(raw: String?, known: List<String>): List<String> {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty() || v == "all") return known
    return v.split('+', ',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** 一个字符在美式键盘上怎么按出来：键码 + 要不要 Shift。打不出来的 = null。 */
data class Stroke(val keyCode: Int, val shift: Boolean, val char: Char)

fun strokeOf(c: Char): Stroke? {
    if (c in 'a'..'z') return Stroke(KeyEvent.VK_A + (c - 'a'), false, c)
    if (c in 'A'..'Z') return Stroke(KeyEvent.VK_A + (c - 'A'), true, c)
    if (c in '0'..'9') return Stroke(KeyEvent.VK_0 + (c - '0'), false, c)
    val plain = mapOf(
        '/' to KeyEvent.VK_SLASH, '.' to KeyEvent.VK_PERIOD, ',' to KeyEvent.VK_COMMA, ';' to KeyEvent.VK_SEMICOLON,
        '=' to KeyEvent.VK_EQUALS, '-' to KeyEvent.VK_MINUS, '[' to KeyEvent.VK_OPEN_BRACKET, ']' to KeyEvent.VK_CLOSE_BRACKET,
        '\'' to KeyEvent.VK_QUOTE, '\\' to KeyEvent.VK_BACK_SLASH, '`' to KeyEvent.VK_BACK_QUOTE, ' ' to KeyEvent.VK_SPACE,
    )
    plain[c]?.let { return Stroke(it, false, c) }
    val shifted = mapOf(
        '?' to KeyEvent.VK_SLASH, '>' to KeyEvent.VK_PERIOD, '<' to KeyEvent.VK_COMMA, ':' to KeyEvent.VK_SEMICOLON,
        '+' to KeyEvent.VK_EQUALS, '_' to KeyEvent.VK_MINUS, '{' to KeyEvent.VK_OPEN_BRACKET, '}' to KeyEvent.VK_CLOSE_BRACKET,
        '"' to KeyEvent.VK_QUOTE, '|' to KeyEvent.VK_BACK_SLASH, '~' to KeyEvent.VK_BACK_QUOTE,
        '!' to KeyEvent.VK_1, '@' to KeyEvent.VK_2, '#' to KeyEvent.VK_3, '$' to KeyEvent.VK_4, '%' to KeyEvent.VK_5,
        '^' to KeyEvent.VK_6, '&' to KeyEvent.VK_7, '*' to KeyEvent.VK_8, '(' to KeyEvent.VK_9, ')' to KeyEvent.VK_0,
    )
    shifted[c]?.let { return Stroke(it, true, c) }
    return null
}

/** 窗口里的一个矩形（点 = Compose 的 dp，左上为原点）。 */
data class R(val x: Double, val y: Double, val w: Double, val h: Double) {
    val right: Double get() = x + w
    val bottom: Double get() = y + h
    val cx: Double get() = x + w / 2
    val cy: Double get() = y + h / 2

    /** 每条边和 [o] 差多少（取最大的）。 */
    fun maxEdgeDiff(o: R): Double = maxOf(kotlin.math.abs(x - o.x), kotlin.math.abs(y - o.y), kotlin.math.abs(right - o.right), kotlin.math.abs(bottom - o.bottom))

    fun inside(o: R, slack: Double = 0.5): Boolean = x >= o.x - slack && y >= o.y - slack && right <= o.right + slack && bottom <= o.bottom + slack

    fun scaled(k: Double) = R(x * k, y * k, w * k, h * k)

    fun short(): String = "%.1f,%.1f %.1fx%.1f".format(java.util.Locale.ROOT, x, y, w, h)
}

/** 截图的像素读数。[r] 是图上的像素矩形（已经乘过倍数），越界的部分裁掉。 */
object Pixels {
    fun luma(rgb: Int): Double {
        val r = (rgb shr 16) and 0xff
        val g = (rgb shr 8) and 0xff
        val b = rgb and 0xff
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun chroma(rgb: Int): Int {
        val r = (rgb shr 16) and 0xff
        val g = (rgb shr 8) and 0xff
        val b = rgb and 0xff
        return maxOf(r, g, b) - minOf(r, g, b)
    }

    private inline fun each(img: BufferedImage, r: R, f: (Int) -> Unit): Int {
        val x0 = r.x.toInt().coerceIn(0, img.width)
        val y0 = r.y.toInt().coerceIn(0, img.height)
        val x1 = r.right.toInt().coerceIn(0, img.width)
        val y1 = r.bottom.toInt().coerceIn(0, img.height)
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            f(img.getRGB(x, y))
            n++
        }
        return n
    }

    fun meanLuma(img: BufferedImage, r: R): Double {
        var sum = 0.0
        val n = each(img, r) { sum += luma(it) }
        return if (n == 0) 0.0 else sum / n
    }

    /** 最亮的那 [fraction] 部分像素的平均亮度——深底上的浅色字「有多亮」，不受字多字少影响。 */
    fun brightMean(img: BufferedImage, r: R, fraction: Double = 0.1): Double {
        val all = ArrayList<Double>()
        each(img, r) { all += luma(it) }
        if (all.isEmpty()) return 0.0
        all.sortDescending()
        val k = (all.size * fraction).toInt().coerceAtLeast(1)
        return all.take(k).average()
    }

    fun meanChroma(img: BufferedImage, r: R): Double {
        var sum = 0.0
        val n = each(img, r) { sum += chroma(it) }
        return if (n == 0) 0.0 else sum / n
    }

    fun lumaStdDev(img: BufferedImage, r: R): Double {
        val all = ArrayList<Double>()
        each(img, r) { all += luma(it) }
        if (all.isEmpty()) return 0.0
        val m = all.average()
        return kotlin.math.sqrt(all.sumOf { (it - m) * (it - m) } / all.size)
    }
}

/** `VOL. 123` → 123。 */
fun volumeNumber(text: String): Int? = Regex("""VOL\.\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()

/** 物理占用的增长线（D-mac-4）：第 10 轮之后比第 1 轮之后多不超过 [limitMb] MB。 */
fun footprintGrowthOk(afterFirst: Long, afterLast: Long, limitMb: Long = 50): Boolean = afterLast - afterFirst <= limitMb * 1024 * 1024

/**
 * 全屏退出后还留着的系统全屏窗口（P20）：系统全屏时 AppKit 给应用另开一扇放标题栏的 `NSToolbarFullScreenWindow`、过渡时还有一扇
 * `_NSFullScreenTransitionOverlayWindow`；退出全屏以后这两种都不该还在可见窗口里。[visible] = 可见 NSWindow 的类名（KVO 前缀照原样）。
 */
fun fullscreenLeftovers(visible: List<String>): List<String> = visible.filter { "NSToolbarFullScreenWindow" in it || "FullScreenTransition" in it }

/**
 * 控制器那一行「播放启动耗时 … 打开媒体 67–329（起点 3279 s） · 首个字节 @329 · seek 完成 @622 · 首帧 @622 | …」里的三段：
 * 打开媒体（毫秒）/ 首个字节（从开播算）/ 首帧（解码出第一帧、从开播算）。认不出的段写「?」；整行认不出 = null。
 */
fun startupPhases(line: String?): String? {
    if (line == null || !line.startsWith("播放启动耗时")) return null
    val open = Regex("""打开媒体 (\d+)–(\d+)""").find(line)?.let { it.groupValues[2].toLong() - it.groupValues[1].toLong() }
    val bytes = Regex("""首个字节 @(\d+)""").find(line)?.groupValues?.get(1)
    val frame = Regex("""首帧 @(\d+)""").find(line)?.groupValues?.get(1)
    return "打开 ${open ?: "?"} · 首字节 @${bytes ?: "?"} · 首帧 @${frame ?: "?"}"
}

/** 联系表的排法：每张缩略图多宽、一行几张、一页最多几张。 */
data class SheetLayout(val thumbW: Int = 360, val perRow: Int = 4, val perPage: Int = 24, val labelH: Int = 34, val gap: Int = 10)

/** [n] 张图分成几页，每页几张。 */
fun sheetPages(n: Int, layout: SheetLayout = SheetLayout()): List<IntRange> =
    if (n <= 0) emptyList() else (0 until n step layout.perPage).map { it until minOf(n, it + layout.perPage) }
