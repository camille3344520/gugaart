package com.gugaart.ui.player

import com.gugaart.ui.data.GugaPrefs
import com.gugugaga.app.platform.LocalStore
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.abs

/*
 * 字幕时间（主理人 2026-11-26：「字幕需要可以调整 + - 秒数，有些字幕会差点时间」，审计批次 P）。
 *
 * 三端一行「字幕时间」：− / + 每次 0.1 秒，按住连续调、按满 2 秒后每次 0.5 秒；不是 0 时出「复位」。
 * 值按「这一集 + 这条字幕」记在本地（键 `gugaart.subtitleTiming.v1`），同一集同一条字幕再开时自动用上；换了字幕就用那条自己记的值（没记过 = 0）。
 * 真正改字幕时间的是上游 `PlayController.setSubtitleDelay`；上游换集时把它清零，这一层在开播 / 换字幕时把记下的值放回去。
 */

/** 字幕时间的上下限（和上游旧皮肤的 ±30 秒一样）。 */
const val SUBTITLE_TIMING_LIMIT_MS = 30_000L

/** 按住多久之后换大步。 */
const val SUBTITLE_TIMING_FAST_AFTER_MS = 2_000L

/** 按住多久开始连续调、之后每隔多久走一步。 */
const val SUBTITLE_TIMING_HOLD_START_MS = 400L
const val SUBTITLE_TIMING_HOLD_TICK_MS = 100L

/** 「+0.5 秒」「-1.2 秒」「0.0 秒」：带正负号，一位小数。 */
fun subtitleTimingText(ms: Long): String {
    val tenths = (abs(ms) + 50) / 100
    val sign = when {
        tenths == 0L -> ""
        ms > 0 -> "+"
        else -> "-"
    }
    return "$sign${tenths / 10}.${tenths % 10} 秒"
}

/** 按住 [heldMs] 时每一步走多少：2 秒以内 0.1 秒，之后 0.5 秒。点一下 = 0.1 秒。 */
fun subtitleTimingStep(heldMs: Long): Long = if (heldMs >= SUBTITLE_TIMING_FAST_AFTER_MS) 500L else 100L

/** 走一步并夹在 ±30 秒里。 */
fun subtitleTimingNext(ms: Long, deltaMs: Long): Long = (ms + deltaMs).coerceIn(-SUBTITLE_TIMING_LIMIT_MS, SUBTITLE_TIMING_LIMIT_MS)

/** 电脑键盘：`[` 减、`]` 加（画报覆盖上游 `PlayerKeys` 里这两个键的倍速，倍速在面板里）；别的键 = null。 */
fun subtitleTimingKey(key: String?): Int? = when (key) {
    "[" -> -1
    "]" -> 1
    else -> null
}

/** 「这一集 + 这条字幕」的键；字幕关着 = null（没有可记的）。 */
fun subtitleTimingKeyOf(itemId: String, sourceId: String?, subtitleIndex: Int?): String? =
    subtitleIndex?.let { "$itemId|${sourceId.orEmpty()}|$it" }

/** 每一集每一条字幕记下的字幕时间（毫秒，0 不记）。最多记 [MAX] 条，多了丢掉最早的。 */
class SubtitleTimingMemory(private val local: LocalStore) {
    private val map = LinkedHashMap<String, Long>()

    init {
        GugaPrefs.parseObject(local.get(KEY))?.forEach { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { map[k] = it } }
    }

    fun get(key: String): Long = map[key] ?: 0L

    fun put(key: String, ms: Long) {
        map.remove(key)
        if (ms != 0L) map[key] = ms
        while (map.size > MAX) map.remove(map.keys.first())
        local.set(KEY, buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString())
    }

    companion object {
        const val KEY = "gugaart.subtitleTiming.v1"
        const val MAX = 500
    }
}

/**
 * 开播 / 换字幕时把记下的值放回去：每来一次快照（内核已经 Ready）问一次 [restore]，「这一集 + 这条字幕」变了就给出要设的值
 * （和现在的一样 = null，不用设）。纯逻辑，有单测。
 */
class SubtitleTimingSync(private val memory: SubtitleTimingMemory) {
    private var last: String? = null
    private var seen = false

    fun restore(itemId: String, sourceId: String?, subtitleIndex: Int?, currentMs: Long): Long? {
        val key = subtitleTimingKeyOf(itemId, sourceId, subtitleIndex)
        if (seen && key == last) return null
        seen = true
        last = key
        val want = key?.let(memory::get) ?: 0L
        return want.takeIf { it != currentMs }
    }

    /** 用户调了：记到现在这一集这一条字幕上。 */
    fun remember(itemId: String, sourceId: String?, subtitleIndex: Int?, ms: Long) {
        subtitleTimingKeyOf(itemId, sourceId, subtitleIndex)?.let { memory.put(it, ms) }
    }
}
