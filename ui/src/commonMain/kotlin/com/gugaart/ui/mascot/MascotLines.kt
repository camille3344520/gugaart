package com.gugaart.ui.mascot

/** 一天里的四段（台词可以按它变）。 */
enum class DayBand {
    Morning, Noon, Evening, Night;

    companion object {
        /** 早 5–11，午 11–17，晚 17–23，深夜 23–5。 */
        fun of(hour: Int): DayBand = when (((hour % 24) + 24) % 24) {
            in 5..10 -> Morning
            in 11..16 -> Noon
            in 17..22 -> Evening
            else -> Night
        }
    }
}

/** 台词表里的一条（生成自 docs/mascot-lines.md）。[band] / [minPct] / [maxPct] 是出场条件，null = 不限。 */
class MascotLine(val key: String, val text: String, val band: DayBand?, val minPct: Int?, val maxPct: Int?)

/**
 * 一句话里能填的事实（docs/mascot.md §4：事实来自真实数据，口气是模板）。null = 这一刻没有这个事实，
 * 用到它的模板不参加挑选——**没有合适的就不说**。
 */
data class LineFacts(
    val title: String? = null,
    /** 第几集。 */
    val ep: Int? = null,
    /** 还剩多久 / 片长：「19 分」。 */
    val remain: String? = null,
    /** 进度百分比 0..100。 */
    val pct: Int? = null,
    /** 停在哪：「26:30」。 */
    val time: String? = null,
    /** 数量（集数等）。 */
    val n: Int? = null,
    /** 下一集的集号。 */
    val next: Int? = null,
) {
    fun value(name: String): String? = when (name) {
        "title" -> title
        "ep" -> ep?.toString()
        "remain" -> remain
        "pct" -> pct?.toString()
        "time" -> time
        "n" -> n?.toString()
        "next" -> next?.toString()
        else -> null
    }
}

/** 她的台词（docs/mascot-lines.md，decisions Q7）。时刻的键见那份文件的小标题。 */
object MascotLines {
    private val byKey: Map<String, List<MascotLine>> by lazy { MASCOT_LINE_TABLE.groupBy { it.key } }

    val keys: Set<String> get() = byKey.keys

    fun count(key: String): Int = byKey[key]?.size ?: 0

    /** 一句模板里用到的事实名：`{ep}` → ep。 */
    fun placeholders(text: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (true) {
            val a = text.indexOf('{', i)
            if (a < 0) break
            val b = text.indexOf('}', a)
            if (b < 0) break
            out += text.substring(a + 1, b)
            i = b + 1
        }
        return out
    }

    /** 这条在这一刻能不能说：时间段、进度条件满足，而且它用到的事实都有。 */
    fun eligible(line: MascotLine, facts: LineFacts, band: DayBand): Boolean {
        if (line.band != null && line.band != band) return false
        if (line.minPct != null && (facts.pct == null || facts.pct < line.minPct)) return false
        if (line.maxPct != null && (facts.pct == null || facts.pct >= line.maxPct)) return false
        return placeholders(line.text).all { facts.value(it) != null }
    }

    fun fill(text: String, facts: LineFacts): String {
        var out = text
        for (name in placeholders(text)) out = out.replace("{$name}", facts.value(name).orEmpty())
        return out
    }

    /**
     * 挑一句。[seed] 决定挑哪一条：同一个时刻、同一部片、同一天传同一个种子，得到的是同一句（不会每次重组换一句）。
     * 没有能说的 → null（那一格保持默认句或不出现）。
     */
    fun pick(key: String, facts: LineFacts = LineFacts(), hour: Int = 12, seed: Int = 0, table: List<MascotLine>? = null): String? {
        val band = DayBand.of(hour)
        val pool = (table?.filter { it.key == key } ?: byKey[key].orEmpty()).filter { eligible(it, facts, band) }
        if (pool.isEmpty()) return null
        return fill(pool[((seed % pool.size) + pool.size) % pool.size].text, facts)
    }
}
