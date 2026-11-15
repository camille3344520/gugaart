package com.gugaart.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.gugaart.ui.res.Res
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * 一份字体文件的字节 → 平台的 [Font]。[variable] = 可变字重文件（按 wght 轴取 [weight]）。平台做不了就返回 null（退回平台字体）。
 * 这是 :ui 里唯一的 expect：字体的加载方式是平台机制，不是版式分支。
 */
internal expect fun platformFont(identity: String, bytes: ByteArray, weight: FontWeight, style: FontStyle, variable: Boolean): Font?

/**
 * 字体提供层（docs/design-language.md §2，docs/decisions.md Q3）。界面代码只通过 `Guga.song()` / `Guga.numeral()` 取字体，不直接碰这里。
 *
 * - 中文：Noto Serif SC 的子集（400 / 600 / 900 三个静态字重，各 3.4 MB、8084 个字；`third_party/fonts/noto-serif-sc/subset/`，
 *   构建时合进资源目录 `files/fonts/`）。启动时按 [plan] 找：每个字重先找 `NotoSerifSC-<字重>-subset.ttf`，再找 `NotoSerifSC-Regular` 这类全量静态文件；
 *   一个静态文件都没有时才看可变字重的 `NotoSerifSC-VF.ttf`；都没有 → 平台衬线字体。**换文件不用改代码。**
 *   子集里没有的字（生僻片名）由平台字体逐字兜底。
 * - 数字和英文小标：Georgia 斜体的位置。可选的 `Numeral-Italic.ttf`；没有 → 平台衬线斜体。
 * - 画面里的字（字幕）：黑体。弹幕的字体是上游 `:danmaku` 自己的（系统 / 思源黑体 / 霞鹜文楷 / 得意黑），不走这里。
 */
object GugaFonts {
    const val DIR = "files/fonts"
    const val VARIABLE = "NotoSerifSC-VF.ttf"
    const val NUMERAL = "Numeral-Italic.ttf"

    /** 界面用到的三档字重：正文 / 行标题 / 标题（decisions Q3：600 和 900，需要时加 400）。 */
    val WEIGHTS = listOf(FontWeight.Normal, FontWeight.SemiBold, FontWeight.Black)

    /** 静态字重的文件名。 */
    val STATIC = mapOf(FontWeight.Normal to "NotoSerifSC-Regular", FontWeight.SemiBold to "NotoSerifSC-SemiBold", FontWeight.Black to "NotoSerifSC-Black")

    /** 中文正文与标题：宋体。加载到真字体之后换掉（读它的界面自动重排）。 */
    var song: FontFamily by mutableStateOf(FontFamily.Serif)
        private set

    /** 数字、英文小标：斜体衬线（用的时候配 [FontStyle.Italic]）。 */
    var numeral: FontFamily by mutableStateOf(FontFamily.Serif)
        private set

    /** 画面里的字（字幕）：黑体。 */
    val screen: FontFamily = FontFamily.SansSerif

    /** 真字体已经换上（false = 平台衬线字体在顶着）。 */
    var bundled: Boolean by mutableStateOf(false)
        private set

    private val lock = Mutex()
    private var tried = false

    /**
     * 界面要的字重 → 实际用的那一档：≤ 450 → 400，≤ 750 → 600，其余 → 900。平台字体顶着的时候原样返回（它有自己的粗体）。
     * 设计稿的行标题 700 落在 600 上，标题 900 是 900。
     */
    fun songWeight(asked: FontWeight): FontWeight = if (bundled) snap(asked) else asked

    fun snap(asked: FontWeight): FontWeight = when {
        asked.weight <= 450 -> FontWeight.Normal
        asked.weight <= 750 -> FontWeight.SemiBold
        else -> FontWeight.Black
    }

    /** 一个字重的候选文件名，按优先顺序。 */
    fun candidates(w: FontWeight): List<String> {
        val base = STATIC.getValue(w)
        return listOf("NotoSerifSC-${w.weight}-subset.ttf", "$base.ttf", "$base.otf")
    }

    /** 要读的文件：(文件名, 字重, 是否可变)。[has] = 这个文件名在资源里有没有。纯函数，有单测。 */
    fun plan(has: (String) -> Boolean): List<Triple<String, FontWeight, Boolean>> {
        val out = ArrayList<Triple<String, FontWeight, Boolean>>()
        for (w in WEIGHTS) {
            val file = candidates(w).firstOrNull(has) ?: continue
            out += Triple(file, w, false)
        }
        if (out.isEmpty() && has(VARIABLE)) return WEIGHTS.map { Triple(VARIABLE, it, true) }
        // 只有 600 和 900 两份时，正文也用 600（没有 400 的文件就不假装有）
        if (out.isNotEmpty() && out.none { it.second == FontWeight.Normal }) {
            out.firstOrNull { it.second == FontWeight.SemiBold }?.let { out.add(0, Triple(it.first, FontWeight.Normal, false)) }
        }
        return out
    }

    /** 找一次资源里的字体文件并换上；重复调用是空操作。读不到 / 平台做不了 → 保持平台字体，不抛。 */
    @OptIn(ExperimentalResourceApi::class)
    suspend fun ensureLoaded() = lock.withLock {
        if (tried) return@withLock
        tried = true
        val cache = HashMap<String, ByteArray?>()
        suspend fun bytes(name: String): ByteArray? = cache.getOrPut(name) {
            try {
                Res.readBytes("$DIR/$name")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }
        val present = HashSet<String>()
        // 每个字重读到第一个有的就停；一个静态文件都没有才去读 24 MB 的可变字体
        for (w in WEIGHTS) candidates(w).firstOrNull { bytes(it) != null }?.let { present += it }
        if (present.isEmpty() && bytes(VARIABLE) != null) present += VARIABLE
        val fonts = plan { it in present }.mapNotNull { (file, weight, variable) ->
            bytes(file)?.let { platformFont("guga-song-$file-${weight.weight}", it, weight, FontStyle.Normal, variable) }
        }
        if (fonts.isNotEmpty()) {
            song = FontFamily(fonts)
            bundled = true
        }
        bytes(NUMERAL)?.let { platformFont("guga-numeral", it, FontWeight.Normal, FontStyle.Italic, false) }?.let { numeral = FontFamily(it) }
    }
}
