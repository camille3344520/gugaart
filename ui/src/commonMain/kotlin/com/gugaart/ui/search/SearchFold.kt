package com.gugaart.ui.search

import com.gugaart.ui.data.Pinyin

/*
 * 搜索的规范化（docs/search.md §2）。片名和查询词走同一套 fold，两边折成一样的形状再比：
 * - 小写；全角 → 半角（ＡＢＣ１２３ → abc123）；常见带调号的拉丁字母去调号（Amélie → amelie）；
 * - 繁 → 简（[SearchTables] 的 OpenCC 单字表：速度與激情 → 速度与激情）；
 * - 去掉空白和标点（：:·・-—_.,，。！？、/《》()（）[]【】“”‘’ …，凡不是字母、数字、汉字的都去掉）：阿凡达：水之道 → 阿凡达水之道；
 * - 数字写法（只在「带数字的形状」[Folded.num] 里）：中文数字 ↔ 阿拉伯数字（钢铁侠二 → 钢铁侠2、第十二集 → 第12集），
 *   独立的大写罗马数字 II–XXXIX（Rocky II → rocky2）。
 * 每个折出来的字记着它来自原文的第几个字（[Folded.src]），命中范围能映射回原文给专色。纯函数，有单测。
 */

/** 折过的一段文字：[text] 的第 i 个字来自原文的第 [src]\[i] 个字。 */
class Folded(val text: String, val src: IntArray) {
    /** 折后的 [from, to) 映射回原文的闭区间。 */
    fun range(from: Int, to: Int): IntRange? = if (from < 0 || to <= from || to > src.size) null else src[from]..src[to - 1]

    override fun toString(): String = text
}

/** 一段文字的两种折法：[plain] 不动数字写法，[num] 把中文数字 / 罗马数字换成阿拉伯数字（没有可换的就是同一个对象）。 */
class FoldPair(val plain: Folded, val num: Folded)

object SearchFold {
    /** 繁 → 简（单字）；不在表里的原样。 */
    fun simplify(c: Char): Char {
        if (c.code < 0x3400) return c
        var lo = 0
        var hi = T2S_FROM.length - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val m = T2S_FROM[mid]
            when {
                m < c -> lo = mid + 1
                m > c -> hi = mid - 1
                else -> return T2S_TO[mid]
            }
        }
        return c
    }

    /** 一个字规范化成什么：全角 → 半角、小写、去调号、繁 → 简；该去掉的（空白、标点、符号）→ 0。 */
    fun foldChar(ch: Char): Char {
        var c = ch
        if (c.code in 0xFF01..0xFF5E) c = (c.code - 0xFEE0).toChar()
        if (c.isHighSurrogate() || c.isLowSurrogate()) return c // 扩展区的汉字：原样留着
        if (!c.isLetterOrDigit()) return 0.toChar()
        if (c.code < 0x80) return c.lowercaseChar()
        if (c.code < 0x250) return unaccent(c.lowercaseChar())
        return simplify(c)
    }

    private fun unaccent(c: Char): Char = when (c) {
        'à', 'á', 'â', 'ã', 'ä', 'å', 'ā' -> 'a'
        'ç' -> 'c'
        'è', 'é', 'ê', 'ë', 'ē' -> 'e'
        'ì', 'í', 'î', 'ï', 'ī' -> 'i'
        'ñ' -> 'n'
        'ò', 'ó', 'ô', 'õ', 'ö', 'ø', 'ō' -> 'o'
        'ù', 'ú', 'û', 'ü', 'ū' -> 'u'
        'ý', 'ÿ' -> 'y'
        else -> c
    }

    /** 两种折法一起出（索引时预算好）。 */
    fun fold(s: String): FoldPair {
        val plain = foldPlain(s)
        val num = foldNumbers(s, plain)
        return FoldPair(plain, num ?: plain)
    }

    fun foldPlain(s: String): Folded {
        val out = StringBuilder(s.length)
        val src = IntArray(s.length)
        var n = 0
        for (i in s.indices) {
            val c = foldChar(s[i])
            if (c.code == 0) continue
            out.append(c)
            src[n++] = i
        }
        return Folded(out.toString(), src.copyOf(n))
    }

    /** 带数字的形状；原文里没有可换的数字写法 → null（就用 plain）。 */
    private fun foldNumbers(s: String, plain: Folded): Folded? {
        val roman = romanTokens(s)
        if (roman.isEmpty() && plain.text.none { it in CN_DIGITS }) return null
        val out = StringBuilder(plain.text.length)
        val src = ArrayList<Int>(plain.text.length)
        var i = 0
        val t = plain.text
        while (i < t.length) {
            val origin = plain.src[i]
            val r = roman[origin]
            if (r != null) {
                // 罗马数字：原文里这个词的几个字母在 plain 里是连着的
                val (len, value) = r
                for (d in value.toString()) {
                    out.append(d)
                    src += origin
                }
                i += len
                continue
            }
            if (t[i] in CN_DIGITS) {
                var j = i
                while (j < t.length && t[j] in CN_DIGITS) j++
                val value = cnNumber(t, i, j)
                if (value != null) {
                    val digits = value.toString()
                    for ((k, d) in digits.withIndex()) {
                        out.append(d)
                        src += plain.src[minOf(i + k * (j - i) / digits.length, j - 1)]
                    }
                    i = j
                    continue
                }
            }
            out.append(t[i])
            src += origin
            i++
        }
        return Folded(out.toString(), src.toIntArray())
    }

    private const val CN_DIGITS = "零〇一二两三四五六七八九十百千"

    private fun cnDigit(c: Char): Int = when (c) {
        '零', '〇' -> 0
        '一' -> 1
        '二', '两' -> 2
        '三' -> 3
        '四' -> 4
        '五' -> 5
        '六' -> 6
        '七' -> 7
        '八' -> 8
        '九' -> 9
        else -> -1
    }

    /**
     * 一串中文数字 → 数值：「十二」12、「二十」20、「一百零五」105、「三」3、「二〇二四」2024（逐位读）。
     * 只有「百 / 千 / 零」、读不通的（「十十」）→ null（原样留着）。
     */
    fun cnNumber(t: CharSequence, from: Int = 0, to: Int = t.length): Int? {
        if (to <= from) return null
        var hasDigit = false
        var hasUnit = false
        for (k in from until to) {
            val c = t[k]
            if (cnDigit(c) > 0 || c == '十') hasDigit = true
            if (c == '十' || c == '百' || c == '千') hasUnit = true
        }
        if (!hasDigit) return null
        if (!hasUnit) {
            // 逐位读：二〇二四 → 2024；一 → 1
            if (to - from > 6) return null
            var v = 0
            for (k in from until to) v = v * 10 + cnDigit(t[k])
            return v
        }
        var total = 0
        var digit = -1
        var lastUnit = 10_000
        for (k in from until to) {
            val c = t[k]
            val d = cnDigit(c)
            if (d >= 0) {
                if (digit > 0) return null // 两个数字连着又带单位（「二三十」）：不认
                digit = d
                continue
            }
            val unit = when (c) {
                '十' -> 10
                '百' -> 100
                '千' -> 1000
                else -> return null
            }
            if (unit >= lastUnit) return null
            if (digit == 0) return null
            total += (if (digit < 0) 1 else digit) * unit
            if (digit < 0 && unit != 10) return null // 「百」「千」前面要有数字
            lastUnit = unit
            digit = -1
        }
        if (digit > 0) total += digit
        return total
    }

    /** 原文里独立的大写罗马数字（II–XXXIX，两个字母起）：起点 → (字母数, 数值)。 */
    private fun romanTokens(s: String): Map<Int, Pair<Int, Int>> {
        var out: HashMap<Int, Pair<Int, Int>>? = null
        var i = 0
        while (i < s.length) {
            if (s[i] in "IVX" && (i == 0 || !s[i - 1].isLetterOrDigit())) {
                var j = i
                while (j < s.length && s[j] in "IVX") j++
                if (j - i >= 2 && (j == s.length || !s[j].isLetterOrDigit())) {
                    romanValue(s, i, j)?.let { v -> (out ?: HashMap<Int, Pair<Int, Int>>().also { out = it })[i] = (j - i) to v }
                }
                i = j
            } else i++
        }
        return out ?: emptyMap()
    }

    private val ROMAN = (1..39).associateBy { roman(it) }

    private fun roman(n: Int): String {
        val tens = arrayOf("", "X", "XX", "XXX")
        val ones = arrayOf("", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")
        return tens[n / 10] + ones[n % 10]
    }

    private fun romanValue(s: String, from: Int, to: Int): Int? = ROMAN[s.substring(from, to)]

    /** 查询词里有没有数字写法（阿拉伯 / 中文数字 / 罗马数字）：有就拿带数字的形状比，没有就拿 plain 比。 */
    fun hasNumber(q: FoldPair): Boolean = q.num !== q.plain || q.plain.text.any { it in '0'..'9' }
}

/**
 * 拼音（docs/search.md §3）：全拼（[SearchTables] 的 Han-Latin 读音表，不带声调；常见多音字另有 [PINYIN_ALT] 里的读音）和首字母（沿用 [Pinyin] 的表）。
 * 汉字给音节；拉丁字母和数字原样（小写）；其它跳过。每个拼音字母都记着它来自原文的第几个字。
 */
class PinyinKey(
    /** 「aganzhengchuan」：每个字取主读音拼起来 + 原样的字母数字（快速路径：一次 indexOf）。 */
    val full: String,
    /** [full] 的第 i 个字母来自原文第 fullSrc[i] 个字。 */
    val fullSrc: IntArray,
    /** [full] 里每个「字」（一个汉字的音节，或一个字母 / 数字）的起点：全拼只从这些位置开始认。 */
    val starts: BooleanArray,
    /** 「agzz」：首字母（[Pinyin] 的表）。 */
    val initials: String,
    val initialsSrc: IntArray,
    /** 有多音字时才有：逐字的全部读音和全部首字母（「传」= chuan / zhuan，c / z），慢速路径用。 */
    val units: List<PinyinUnit>?,
)

/** 一个字的全部读音（主读音在前）、全部可能的首字母、在原文里的下标。 */
class PinyinUnit(val readings: List<String>, val initials: String, val src: Int)

object SearchPinyin {
    private val syllables: List<String> by lazy { listOf("") + PINYIN_SYLLABLES.split(' ') }

    private val alt: Map<Char, List<String>> by lazy {
        PINYIN_ALT.split(' ').filter { it.length > 1 }.associate { it[0] to it.substring(1).split(',') }
    }

    /** 一个汉字的全拼（主读音，不带声调）；不是表里的汉字 → null。 */
    fun syllable(c: Char): String? {
        val i = c.code - PINYIN_FULL_FIRST
        if (i < 0 || i * 2 + 1 >= PINYIN_FULL.length) return null
        val idx = PINYIN_FULL[i * 2].digitToInt(36) * 36 + PINYIN_FULL[i * 2 + 1].digitToInt(36)
        return if (idx == 0) null else syllables[idx]
    }

    /** 一个汉字的全部读音（主读音在前）；不是汉字 → 空。 */
    fun readings(c: Char): List<String> {
        val main = syllable(c) ?: syllable(SearchFold.simplify(c))
        val more = alt[c] ?: alt[SearchFold.simplify(c)]
        return when {
            main == null -> more.orEmpty()
            more == null -> listOf(main)
            else -> listOf(main) + more
        }
    }

    fun key(s: String): PinyinKey {
        val full = StringBuilder(s.length * 3)
        val fullSrc = ArrayList<Int>(s.length * 3)
        val starts = ArrayList<Boolean>(s.length * 3)
        val ini = StringBuilder(s.length)
        val iniSrc = ArrayList<Int>(s.length)
        val units = ArrayList<PinyinUnit>(s.length)
        var multi = false
        for ((i, raw) in s.withIndex()) {
            val c = if (raw.code in 0xFF01..0xFF5E) (raw.code - 0xFEE0).toChar() else raw
            val rs = readings(c)
            val initial = Pinyin.initialOf(c)
            if (rs.isNotEmpty()) {
                for ((k, ch) in rs[0].withIndex()) {
                    full.append(ch)
                    fullSrc += i
                    starts += k == 0
                }
            } else if (c.code < 0x80 && c.isLetterOrDigit()) {
                full.append(c.lowercaseChar())
                fullSrc += i
                starts += true
            }
            if (initial != null) {
                ini.append(initial)
                iniSrc += i
                val readingsHere = rs.ifEmpty { listOf(c.lowercaseChar().toString()) }
                val inis = buildString {
                    append(initial)
                    for (r in readingsHere) if (r[0] !in this) append(r[0])
                }
                if (readingsHere.size > 1 || inis.length > 1) multi = true
                units += PinyinUnit(readingsHere, inis, i)
            }
        }
        return PinyinKey(full.toString(), fullSrc.toIntArray(), starts.toBooleanArray(), ini.toString(), iniSrc.toIntArray(), units.takeIf { multi })
    }

    /** 全拼（考虑多音字）：从某个字的开头起，[q] 依次是各字的某个读音，最后一个字可以只打了一半。命中 → 原文里的范围。 */
    fun matchFull(units: List<PinyinUnit>, q: String): IntRange? {
        for (i in units.indices) {
            val end = from(units, i, q, 0)
            if (end >= 0) return units[i].src..units[end].src
        }
        return null
    }

    private fun from(u: List<PinyinUnit>, i: Int, q: String, at: Int): Int {
        if (i >= u.size) return -1
        val rest = q.length - at
        for (r in u[i].readings) {
            if (rest <= r.length) {
                if (r.regionMatches(0, q, at, rest)) return i
            } else if (q.regionMatches(at, r, 0, r.length)) {
                val e = from(u, i + 1, q, at + r.length)
                if (e >= 0) return e
            }
        }
        return -1
    }

    /** 首字母（考虑多音字）：连续几个字的首字母依次是 [q]。 */
    fun matchInitials(units: List<PinyinUnit>, q: String): IntRange? {
        if (q.length > units.size) return null
        outer@ for (i in 0..units.size - q.length) {
            for (k in q.indices) if (q[k] !in units[i + k].initials) continue@outer
            return units[i].src..units[i + q.length - 1].src
        }
        return null
    }
}
