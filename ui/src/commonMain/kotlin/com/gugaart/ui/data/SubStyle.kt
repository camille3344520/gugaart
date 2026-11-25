package com.gugaart.ui.data

import com.gugugaga.app.player.SubtitleStyle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 字幕样式（1.4.1 P3 / U1；主理人：「字幕、弹幕也支持一些设置，比如字体大小，粗体什么的，然后也选一些比较好看的字体可以选择」）。
 * 存在画报自己的键 `gugaart.settings.v1` 的 `subStyle` 里（不动上游 `gugu.settings.v1` 的任何键）；下发到内核的是上游的 [SubtitleStyle]
 * （PlayerEngine.subtitleStyle：mpv 全部即时生效，ExoPlayer 大部分即时生效，VLC 下次开播生效）。docs/subtitle-style.md。
 */

/** 描边：无 / 细 / 标准（内核默认）/ 粗。[factor] 乘在内核自己的默认宽度上。 */
enum class SubOutline(val id: String, val label: String, val factor: Double) {
    None("none", "无", 0.0), Thin("thin", "细", 0.5), Normal("normal", "标准", 1.0), Thick("thick", "粗", 2.0);

    companion object {
        fun of(id: String?): SubOutline = entries.firstOrNull { it.id == id } ?: Normal
    }
}

/** 位置（最下面一行离画面底边多远，占画面高度的比例）：贴底 / 默认（内核自己的）/ 抬高 / 更高。 */
enum class SubPosition(val id: String, val label: String, val marginY: Double?) {
    Bottom("bottom", "贴底", 0.02), Default("default", "默认", null), Raised("raised", "抬高", 0.08), High("high", "更高", 0.15);

    companion object {
        fun of(id: String?): SubPosition = entries.firstOrNull { it.id == id } ?: Default
    }
}

/** 字号的档位：50 % – 200 %，每档 10 %。 */
const val SUB_SCALE_MIN = 0.5
const val SUB_SCALE_MAX = 2.0
const val SUB_SCALE_STEP = 0.1

/** 字号往上 / 往下一档（夹在范围里，两位小数，免得 0.1 加十次不是 1）。 */
fun subScaleStep(v: Double, dir: Int): Double = (((v + dir * SUB_SCALE_STEP) * 100).roundToInt() / 100.0).coerceIn(SUB_SCALE_MIN, SUB_SCALE_MAX)

/** 50 % – 200 % 的全部档位（电视的选择器 / 页边一行用）。 */
val SUB_SCALES: List<Double> = (0..((SUB_SCALE_MAX - SUB_SCALE_MIN) / SUB_SCALE_STEP).roundToInt()).map { ((SUB_SCALE_MIN + it * SUB_SCALE_STEP) * 100).roundToInt() / 100.0 }

/** 字体的选择：`system` / [StyleFont] 的 id / `imported:<导入字体的 id>`。 */
const val IMPORTED_PREFIX = "imported:"

data class SubStylePrefs(
    /** 字号比例，1 = 内核默认。 */
    val scale: Double = 1.0,
    val bold: Boolean = false,
    /** `system` / [StyleFont.id] / `imported:<id>`。 */
    val font: String = StyleFont.System.id,
    val outline: SubOutline = SubOutline.Normal,
    val shadow: Boolean = false,
    val position: SubPosition = SubPosition.Default,
    /** 也改 ASS / SSA 字幕自带的样式（默认不改：特效字幕保持原样，只跟着字号缩放）。 */
    val overrideAss: Boolean = false,
) {
    val isDefault: Boolean get() = this == SubStylePrefs()

    /** 选的是哪一款内置 / 下载的字体（导入的 → null）。 */
    val styleFont: StyleFont? get() = StyleFont.of(font)

    /** 选的是导入的哪一款（id）。 */
    val importedId: String? get() = font.takeIf { it.startsWith(IMPORTED_PREFIX) }?.removePrefix(IMPORTED_PREFIX)

    fun encode(): JsonObject = buildJsonObject {
        put("scale", scale)
        put("bold", bold)
        put("font", font)
        put("outline", outline.id)
        put("shadow", shadow)
        put("position", position.id)
        put("overrideAss", overrideAss)
    }

    /**
     * 交给内核的样式。[font]：这一款字体在磁盘上的文件（家族名 + 主文件 + 所在目录）；还没有（系统字体 / 下载中 / 失败 / 平台放不了文件）→ null，
     * 内核用它自己的默认字体，别的样式照样生效。
     */
    fun toEngine(font: ResolvedFont?): SubtitleStyle = SubtitleStyle(
        scale = scale,
        bold = bold,
        fontFamily = font?.family,
        fontFile = font?.file,
        fontsDir = font?.dir,
        outline = outline.factor,
        shadow = shadow,
        marginY = position.marginY,
        overrideAss = overrideAss,
    )

    companion object {
        fun parse(o: JsonObject?): SubStylePrefs {
            if (o == null) return SubStylePrefs()
            fun bool(k: String, d: Boolean) = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: d
            fun str(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val scale = (o["scale"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it.isFinite() }
            val font = str("font")?.takeIf { StyleFont.of(it) != null || (it.startsWith(IMPORTED_PREFIX) && it.length > IMPORTED_PREFIX.length) }
            return SubStylePrefs(
                scale = scale?.let { (it * 100).roundToInt() / 100.0 }?.coerceIn(SUB_SCALE_MIN, SUB_SCALE_MAX) ?: 1.0,
                bold = bool("bold", false),
                font = font ?: StyleFont.System.id,
                outline = SubOutline.of(str("outline")),
                shadow = bool("shadow", false),
                position = SubPosition.of(str("position")),
                overrideAss = bool("overrideAss", false),
            )
        }
    }
}

/** 一款字体落到磁盘上的样子：文件里的家族名、主文件、所在目录（mpv 的 `sub-fonts-dir`）。 */
data class ResolvedFont(val family: String, val file: String, val dir: String)

/** 字号的写法：「100%」。 */
fun subScaleText(v: Double): String = "${(v * 100).roundToInt()}%"

/** 两个字号是不是同一档。 */
fun sameScale(a: Double, b: Double): Boolean = abs(a - b) < 0.001
