package com.gugaart.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 专色：从一张海报的像素里取「最强的色相」，推到在墨黑纸上读得清的亮度（docs/design-language.md §1.2）。
 *
 * 设计稿 `design/phone/js/app.js` 的 `APP.palette` / `APP.tone` / `APP.spotOf` 的逐步移植，纯函数、不依赖 Compose 的绘制：
 * 1. 每个像素按色相分进 12 个 30° 的桶，权重 = 饱和度² ×（0.25 + 明度）—— 灰的、暗的像素几乎不算数；
 * 2. 取权重最大的桶的加权平均色（[Palette.vibrant]）；
 * 3. 以 (max+min)/2 为轴把饱和度拉到 [saturation] 倍，再整体缩放到亮度 [lightness]；
 * 4. 对比度兜底：感知亮度低于 128 的（深蓝、深紫）向白色混一点；
 * 5. 灰海报兜底：最强色的 max−min < 26（几乎没有颜色）→ 朱红 [GugaColors.SPOT_FALLBACK]。
 *
 * 手机 / 电脑用 lightness 0.60、saturation 1.25；电视用 0.62 / 1.30（大屏、远看，略亮略艳）。
 */
object SpotColor {
    const val LIGHTNESS = 0.60f
    const val SATURATION = 1.25f
    const val TV_LIGHTNESS = 0.62f
    const val TV_SATURATION = 1.30f

    /** 最强色的通道差小于它 = 灰海报。 */
    const val GREY_SPREAD = 26f

    /** 感知亮度（0.3 R + 0.59 G + 0.11 B）低于它就向白色提亮。 */
    const val MIN_LUMA = 128f

    class Palette(val average: FloatArray, val vibrant: FloatArray)

    /** [argb]：一张缩小后的海报的像素（建议 ≤ 64 × 96；alpha 忽略）。空数组 = 没有图。 */
    fun palette(argb: IntArray): Palette? {
        if (argb.isEmpty()) return null
        val w = DoubleArray(12)
        val br = DoubleArray(12)
        val bg = DoubleArray(12)
        val bb = DoubleArray(12)
        var sr = 0.0
        var sg = 0.0
        var sb = 0.0
        for (p in argb) {
            val r = (p shr 16 and 0xFF).toDouble()
            val g = (p shr 8 and 0xFF).toDouble()
            val b = (p and 0xFF).toDouble()
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val s = if (mx > 0) (mx - mn) / mx else 0.0
            val v = mx / 255.0
            sr += r; sg += g; sb += b
            var h = 0.0
            if (mx != mn) {
                h = when (mx) {
                    r -> (g - b) / (mx - mn)
                    g -> 2 + (b - r) / (mx - mn)
                    else -> 4 + (r - g) / (mx - mn)
                }
                h = (h * 60 + 360) % 360
            }
            val weight = s * s * (0.25 + v)
            val i = (h / 30).toInt() % 12
            w[i] += weight; br[i] += r * weight; bg[i] += g * weight; bb[i] += b * weight
        }
        val n = argb.size.toDouble()
        val avg = floatArrayOf((sr / n).toFloat(), (sg / n).toFloat(), (sb / n).toFloat())
        val top = w.indices.maxByOrNull { w[it] } ?: 0
        val vib = if (w[top] > 0) floatArrayOf((br[top] / w[top]).toFloat(), (bg[top] / w[top]).toFloat(), (bb[top] / w[top]).toFloat()) else avg
        return Palette(avg, vib)
    }

    /** 以中值为轴调饱和度，再缩放到目标亮度（0..1）。输入输出都是 0..255 的 RGB。 */
    fun tone(rgb: FloatArray, lightness: Float, saturation: Float): FloatArray {
        val mx = max(rgb[0], max(rgb[1], rgb[2]))
        val mn = min(rgb[0], min(rgb[1], rgb[2]))
        val mid = ((mx + mn) / 2f).let { if (it == 0f) 1f else it }
        val o = FloatArray(3) { mid + (rgb[it] - mid) * saturation }
        val l = ((max(o[0], max(o[1], o[2])) + min(o[0], min(o[1], o[2]))) / 2f).let { if (it == 0f) 1f else it }
        val k = lightness * 255f / l
        return FloatArray(3) { (o[it] * k).coerceIn(0f, 255f).roundToInt().toFloat() }
    }

    /** 一张海报的专色；没有图 / 灰海报 = 朱红。 */
    fun of(argb: IntArray, lightness: Float = LIGHTNESS, saturation: Float = SATURATION): Color {
        val p = palette(argb) ?: return GugaColors.SPOT_FALLBACK
        val vib = p.vibrant
        if (max(vib[0], max(vib[1], vib[2])) - min(vib[0], min(vib[1], vib[2])) < GREY_SPREAD) return GugaColors.SPOT_FALLBACK
        var c = tone(vib, lightness, saturation)
        val luma = 0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2]
        if (luma < MIN_LUMA) {
            val k = (MIN_LUMA - luma) / 190f
            c = FloatArray(3) { c[it] + (255f - c[it]) * k }
        }
        return Color(c[0].roundToInt().coerceIn(0, 255), c[1].roundToInt().coerceIn(0, 255), c[2].roundToInt().coerceIn(0, 255))
    }

    /** WCAG 相对亮度的对比度（专色压在纸色上必须 ≥ 4.5，测试守着）。 */
    fun contrast(a: Color, b: Color): Float {
        fun lin(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        fun lum(c: Color): Double = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)
        val la = lum(a)
        val lb = lum(b)
        return ((max(la, lb) + 0.05) / (min(la, lb) + 0.05)).toFloat()
    }
}
