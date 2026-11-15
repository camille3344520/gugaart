package com.gugaart.ui.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/*
 * 弹幕的画报默认值（用户 2026-11-15：「默认设置显示区域10% 150%字体大小」）。
 *
 * 区域和字号存在上游的 `gugu.settings.v1`（`danmaku.render.area` / `fontScale`），键名和含义都不动，上游的默认是 0.6 / 1.0。
 * 画报不改上游的默认，只在第一次启动时看一眼：这两个值还是上游的默认（= 没存过，或者存的就是 0.6 / 1.0）就写成画报的默认，
 * 然后在自己的键里记一笔 `danmakuSeeded`，以后用户怎么改都不再动。
 */
object DanmakuDefaults {
    /** 画报的默认：显示区域 10 %（占弹幕那一格的高）、字号 150 %。 */
    const val AREA = 0.1
    const val FONT_SCALE = 1.5

    /** 上游的默认（`DanmakuRenderPrefs`）。 */
    const val UPSTREAM_AREA = 0.6
    const val UPSTREAM_FONT_SCALE = 1.0

    private fun untouched(v: JsonElement?, upstream: Double): Boolean {
        if (v == null) return true
        val d = (v as? JsonPrimitive)?.doubleOrNull ?: return false
        return abs(d - upstream) < 1e-9
    }

    /**
     * 第一次启动时要写回去的 `render` 那一块；什么都不用改 = null。[stored] = 现在存着的 `danmaku.render`（没有 = null）。
     * 只动还是上游默认的那一项；别的键（不认识的也算）原样留着。
     */
    fun seed(stored: JsonObject?): JsonObject? {
        val cur = stored ?: JsonObject(emptyMap())
        val next = cur.toMutableMap()
        if (untouched(cur["area"], UPSTREAM_AREA)) next["area"] = JsonPrimitive(AREA)
        if (untouched(cur["fontScale"], UPSTREAM_FONT_SCALE)) next["fontScale"] = JsonPrimitive(FONT_SCALE)
        return if (next == cur) null else JsonObject(next)
    }
}
