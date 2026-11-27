package com.gugaart.ui.data

import kotlin.math.ceil
import kotlin.math.max

/**
 * 竖版封面按屏幕上画多大来要图（1.5.1 性能批次）。
 *
 * 封面地址一律带 `maxWidth=600`（[artOf]），片库海报墙在手机上一张只画约 290 px 宽、电视上约 216 px，
 * 解出来却是 600 × 900 × 4 B = 2.1 MB 一张；电视片库翻 10 页本机堆涨到约 290 MB。这里把 Emby 地址里的 `maxWidth`
 * 换成够用的最小一档（屏幕宽 × [MARGIN]，留给聚焦放大），由服务器缩好再发过来：画出来一样，解码内存和下载都少。
 *
 * 只动「竖的盒子里的 Primary 图」（海报、人物照，都是约 2:3）：这时铺满盒子要的源图宽 = max(盒宽, 盒高 × 2/3)。
 * 横的盒子（剧照、背景、Primary 也可能是 16:9 的单集剧照）不动——源图比例不知道，宁可多要也不能糊。
 * 换出来的一档永远不超过原来的 `maxWidth`；地址里没有 `maxWidth` 的不动。
 */
object ImageSizing {
    /** 宽度档：同一张图在不同大小的卡片上尽量落在同一档，磁盘缓存能复用。 */
    val BUCKETS = intArrayOf(240, 360, 480, 600, 800, 960, 1280, 1600, 1920)

    /** 聚焦 / 按压会把卡片放大一点（上游按压物理、电视焦点框），多要 25 %。 */
    const val MARGIN = 1.25f

    private fun hasPrimary(url: String): Boolean {
        val idx = url.indexOf("/Images/Primary", ignoreCase = true)
        if (idx < 0) return false
        val after = idx + "/Images/Primary".length
        return after == url.length || url[after] == '/' || url[after] == '?'
    }

    private fun replaceMaxWidth(url: String, choose: (asked: Int) -> Int?): String {
        val q = url.indexOf('?')
        if (q < 0) return url
        var pos = q
        while (pos >= 0 && pos < url.length) {
            val idx = url.indexOf("maxWidth=", pos + 1)
            if (idx < 0) return url
            val sep = url[idx - 1]
            if (sep == '?' || sep == '&') {
                val valStart = idx + "maxWidth=".length
                var valEnd = valStart
                while (valEnd < url.length && url[valEnd] in '0'..'9') valEnd++
                if (valEnd > valStart) {
                    val asked = url.substring(valStart, valEnd).toIntOrNull() ?: return url
                    val target = choose(asked) ?: return url
                    if (target == asked) return url
                    return buildString(url.length) {
                        append(url, 0, valStart)
                        append(target)
                        append(url, valEnd, url.length)
                    }
                }
            }
            pos = idx
        }
        return url
    }

    /** [url] 画进 [boxW] × [boxH] 像素的盒子（铺满）时该要的地址；不该动 / 动不了 → 原样返回。 */
    fun forBox(url: String, boxW: Int, boxH: Int): String {
        if (boxW <= 0 || boxH <= 0 || boxH <= boxW) return url
        if (!hasPrimary(url)) return url
        val need = ceil(max(boxW.toFloat(), boxH * 2f / 3f) * MARGIN).toInt()
        val bucket = BUCKETS.firstOrNull { it >= need } ?: return url
        return replaceMaxWidth(url) { asked -> if (bucket >= asked) null else bucket }
    }

    // 原地址 → 上次量出来的地址：同一张卡片重新进组合（翻回来、返回上一页）时第一帧就用它，不等一次布局、不闪占位。
    // 只在组合里读写（界面线程）；最多记 [REMEMBER_MAX] 条，最久没用的先丢。
    // 用插入序 LinkedHashMap 手动挪到队尾当 LRU（JVM 的 accessOrder + removeEldestEntry 在 Kotlin/Native 没有，1.5.1 合并时 iOS 编不过）。
    private const val REMEMBER_MAX = 4096
    private val last = LinkedHashMap<String, String>()

    /** 上次 [sized] 给 [url] 的地址；没量过 = null（调用者等一次布局）。 */
    fun lastFor(url: String): String? {
        val v = last.remove(url) ?: return null
        last[url] = v // 读一次也算用过：挪到队尾
        return v
    }

    /** [forBox]，并记下来给 [lastFor]。 */
    fun sized(url: String, boxW: Int, boxH: Int): String = forBox(url, boxW, boxH).also {
        last.remove(url)
        last[url] = it
        if (last.size > REMEMBER_MAX) last.entries.iterator().let { i -> i.next(); i.remove() }
    }

    /** 把已有 `maxWidth=` 的地址压到不超过 [maxW]（电视 TvBleed 整幅背景用，1280 宽：全屏不糊、比原图少占显存）。 */
    fun capWidth(url: String, maxW: Int): String =
        replaceMaxWidth(url) { asked -> if (asked <= maxW) null else maxW }
}
