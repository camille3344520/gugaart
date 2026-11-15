package com.gugaart.ui.data

/**
 * 拼音首字母搜索（docs/decisions.md Q8）：本地汉字表 [PINYIN_INITIALS]（U+4E00–U+9FA5），三端共用，不联网。
 * 「颜心记」→ `yxj`；拉丁字母和数字原样（小写）；其它符号跳过。多音字取常用读音（表是按 ICU 的拼音排序生成的）。
 */
object Pinyin {
    /** 一个字符的首字母；不是汉字 / 字母 / 数字 → null。 */
    fun initialOf(ch: Char): Char? {
        val c = ch.code
        return when {
            c in PINYIN_FIRST until PINYIN_FIRST + PINYIN_INITIALS.length -> PINYIN_INITIALS[c - PINYIN_FIRST]
            ch in 'a'..'z' || ch in '0'..'9' -> ch
            ch in 'A'..'Z' -> ch.lowercaseChar()
            else -> null
        }
    }

    /** 整个名字的首字母串。 */
    fun initials(name: String): String = buildString { for (ch in name) initialOf(ch)?.let(::append) }

    /** 查询词是不是「只有字母数字」（这时才当首字母去匹配）。 */
    fun isInitialsQuery(q: String): Boolean = q.isNotEmpty() && q.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }

    /**
     * [query] 在 [name] 里命中的字符下标范围（给命中的字上专色用）；没命中 → null。
     * 先按原文找（不分大小写）；查询词全是字母数字时再按首字母找：`yxj` 命中「颜心记」的 0..2。
     */
    fun match(name: String, query: String): IntRange? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val at = name.indexOf(q, ignoreCase = true)
        if (at >= 0) return at until at + q.length
        if (!isInitialsQuery(q)) return null
        // 名字里每个「有首字母的字符」的位置
        val pos = ArrayList<Int>(name.length)
        val ini = StringBuilder(name.length)
        for ((i, ch) in name.withIndex()) initialOf(ch)?.let { pos += i; ini.append(it) }
        val hit = ini.indexOf(q.lowercase())
        if (hit < 0) return null
        return pos[hit]..pos[hit + q.length - 1]
    }

    /** 排序用：命中在越前面越好，原文命中优先于首字母命中；没命中 → null。 */
    fun score(name: String, query: String): Int? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val at = name.indexOf(q, ignoreCase = true)
        if (at >= 0) return at
        val r = match(name, q) ?: return null
        return 1000 + r.first
    }
}
