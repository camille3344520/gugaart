package com.gugaart.ui.search

import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Title
import kotlin.concurrent.Volatile

/*
 * 片库搜索的匹配与排序（docs/search.md §3–§5）。纯函数、commonMain；索引时把 fold / 全拼 / 首字母都预算好（[SearchDoc]），一次查询只做字符串比较。
 *
 * 分档（越小越前）：
 *   0 整词相等 > 1 前缀 > 2 子串 > 3 拼音全拼（aganzhengzhuan，只从一个字的音节开头认）> 4 拼音首字母（agzz）
 *   > 5 中文按序缩写（复联 → 复仇者联盟：至少 2 个字、按顺序、跨度不超过字数的 4 倍，间隔越少越好）
 *   > 6 英文单词前缀 / 错一个字母（iron 2 → Iron Man 2；≥ 5 个字母的词允许错一个：avater → Avatar）。
 * 比较之前两边都 fold（[SearchFold]）：繁 → 简、全角 → 半角、去标点空白、中文数字 / 罗马数字 ↔ 阿拉伯数字。
 * 排序：档位 → 命中字段（片名 > 原名 > 排序名）→ 类型（电影 / 剧集 > 合集 > 其它视频 > 单集 > 人物）→ 名字短（更贴近）→ 评分高 → 名字。
 * 同档名字更贴近、更短的排在评分前（所有档统一，协调者 2026-11 定）：复联 → 复仇者联盟 7.7 在 终局之战 8.2 前；与激 → 速度与激情 在 速度与激情10 前。
 * 命中范围映射回原文（专色）；原名 / 排序名命中时片名不高亮（range = null）。
 */

/** 档位。 */
object Tier {
    const val EXACT = 0
    const val PREFIX = 1
    const val SUBSTRING = 2
    const val PINYIN = 3
    const val INITIALS = 4
    const val ABBREV = 5
    const val FUZZY = 6
}

/** 命中的是哪个字段（越小越前）。 */
enum class FieldKind { Name, Original, Sort }

/** 一个可搜的字段：原文 + 预算好的两种 fold、拼音、英文单词。 */
class SearchField(val kind: FieldKind, val text: String) {
    val fold: FoldPair = SearchFold.fold(text)
    val pinyin: PinyinKey? = if (text.any { SearchPinyin.syllable(it) != null || SearchPinyin.syllable(SearchFold.simplify(it)) != null }) SearchPinyin.key(text) else null
    /** 原文里的拉丁字母 / 数字词（小写、去调号）和它们在原文里的起止。 */
    val words: List<Word> = wordsOf(text)

    class Word(val text: String, val from: Int, val to: Int)

    companion object {
        fun wordsOf(s: String): List<Word> {
            val out = ArrayList<Word>(4)
            var i = 0
            while (i < s.length) {
                val c = SearchFold.foldChar(s[i])
                if (c.code != 0 && c.code < 0x80) {
                    val sb = StringBuilder()
                    val from = i
                    while (i < s.length) {
                        val d = SearchFold.foldChar(s[i])
                        if (d.code == 0 || d.code >= 0x80) break
                        sb.append(d)
                        i++
                    }
                    out += Word(sb.toString(), from, i - 1)
                } else i++
            }
            return out
        }
    }
}

/** 索引里的一条（片 / 单集 / 人物）。 */
class SearchDoc<T>(
    val item: T,
    val id: String,
    val name: String,
    val fields: List<SearchField>,
    /** 类型的先后：0 电影 / 剧集，1 合集，2 其它视频，3 单集，4 人物。 */
    val kindRank: Int,
    val rating: Float,
)

/**
 * 一条命中：[range] 是片名（[SearchDoc.name]）里该上专色的字；不是片名命中 → null。
 * [fieldText] / [fieldRange]：命中的那个字段的原文和其中该上专色的字（原名命中时界面写「原名命中：Iron Man」用）。
 */
class SearchHit<T>(
    val doc: SearchDoc<T>,
    val tier: Int,
    val field: FieldKind,
    val range: IntRange?,
    val score: Int,
    val fieldText: String = doc.name,
    val fieldRange: IntRange? = range,
) {
    val item: T get() = doc.item
}

/** 查询词预处理一次。 */
class SearchQuery(raw: String) {
    val text: String = raw.trim()
    val fold: FoldPair = SearchFold.fold(text)
    val hasNumber: Boolean = SearchFold.hasNumber(fold)
    /** 折完只剩 a–z0–9：可以当拼音（全拼 / 首字母）。 */
    val ascii: Boolean = fold.plain.text.isNotEmpty() && fold.plain.text.all { it in 'a'..'z' || it in '0'..'9' }
    /** 折完全是汉字（中文按序缩写用）。 */
    val han: Boolean = fold.plain.text.length >= 2 && fold.plain.text.all { it.code in 0x3400..0x9FFF }
    val words: List<String> = SearchField.wordsOf(text).map { it.text }
    val isEmpty: Boolean get() = fold.plain.text.isEmpty()
}

object SearchMatch {
    /** [q] 在一个字段里的最好命中；没有 → null。返回 (档位, 原文里的范围, 档内分数)。 */
    fun field(f: SearchField, q: SearchQuery): Triple<Int, IntRange?, Int>? {
        if (q.isEmpty) return null
        // 0–2：整词 / 前缀 / 子串。带数字的查询先按「数字形状」比，比不上再按原样比（「十而」→ 三十而已）
        if (q.hasNumber) text(f.fold.num, q.fold.num)?.let { return it }
        text(f.fold.plain, q.fold.plain)?.let { return it }
        val py = f.pinyin
        if (q.ascii && py != null) {
            val qs = q.fold.plain.text
            // 3：全拼（从一个字的音节开头认；至少两个字母）。先按主读音一次 indexOf，不中再按多音字逐字试
            if (qs.length >= 2) {
                var at = py.full.indexOf(qs)
                while (at >= 0) {
                    if (py.starts[at]) return Triple(Tier.PINYIN, py.fullSrc[at]..py.fullSrc[at + qs.length - 1], 0)
                    at = py.full.indexOf(qs, at + 1)
                }
                py.units?.let { u -> SearchPinyin.matchFull(u, qs)?.let { return Triple(Tier.PINYIN, it, 1) } }
            }
            // 4：首字母（多音字的其它读音排在常用读音后面）
            val at = py.initials.indexOf(qs)
            if (at >= 0) return Triple(Tier.INITIALS, py.initialsSrc[at]..py.initialsSrc[at + qs.length - 1], 0)
            py.units?.let { u -> SearchPinyin.matchInitials(u, qs)?.let { return Triple(Tier.INITIALS, it, 1) } }
        }
        // 5：中文按序缩写
        if (q.han && q.fold.plain.text.length <= 8) abbrev(f.fold.plain, q.fold.plain.text)?.let { return it }
        // 6：英文单词前缀 / 错一个字母
        if (q.words.isNotEmpty() && f.words.isNotEmpty() && q.words.joinToString("") == q.fold.plain.text) fuzzy(f.words, q.words)?.let { return it }
        return null
    }

    private fun text(f: Folded, q: Folded): Triple<Int, IntRange?, Int>? {
        val qt = q.text
        val ft = f.text
        if (qt.isEmpty() || qt.length > ft.length) return null
        if (ft == qt) return Triple(Tier.EXACT, f.range(0, ft.length), 0)
        if (ft.startsWith(qt)) return Triple(Tier.PREFIX, f.range(0, qt.length), 0)
        val at = ft.indexOf(qt)
        if (at >= 0) return Triple(Tier.SUBSTRING, f.range(at, at + qt.length), 0)
        return null
    }

    /** 中文按序缩写：[q] 的字按顺序出现在 [f] 里，跨度最小的那一处；跨度 > 字数 × 4 不算。分数 = 跨度 − 字数（间隔）。 */
    private fun abbrev(f: Folded, q: String): Triple<Int, IntRange?, Int>? {
        val t = f.text
        var best: Pair<Int, Int>? = null
        var start = t.indexOf(q[0])
        while (start >= 0) {
            var pos = start
            var ok = true
            for (k in 1 until q.length) {
                pos = t.indexOf(q[k], pos + 1)
                if (pos < 0) {
                    ok = false
                    break
                }
            }
            if (!ok) break // 从更后面开始也凑不齐
            if (best == null || pos - start < best.second - best.first) best = start to pos
            start = t.indexOf(q[0], start + 1)
        }
        val b = best ?: return null
        val span = b.second - b.first + 1
        if (span > q.length * 4) return null
        return Triple(Tier.ABBREV, f.range(b.first, b.second + 1), span - q.length)
    }

    /** 英文：每个查询词各对上一个不同的词（前缀；≥ 5 个字母的允许错一个字母）。分数 = 错字的词数 × 100 + 没对上的词数（Avatar 比 Avatar: The Way of Water 更像 avater）。 */
    private fun fuzzy(words: List<SearchField.Word>, q: List<String>): Triple<Int, IntRange?, Int>? {
        val used = BooleanArray(words.size)
        var typos = 0
        var from = Int.MAX_VALUE
        var to = -1
        for (qw in q) {
            var hit = -1
            var typo = false
            for ((i, w) in words.withIndex()) {
                if (used[i]) continue
                if (w.text.startsWith(qw)) {
                    hit = i
                    typo = false
                    break
                }
                if (hit < 0 && qw.length >= 5 && qw.all { it in 'a'..'z' } && (within1(qw, w.text) || (w.text.length > qw.length && within1(qw, w.text.substring(0, qw.length))))) {
                    hit = i
                    typo = true
                }
            }
            if (hit < 0) return null
            used[hit] = true
            if (typo) typos++
            from = minOf(from, words[hit].from)
            to = maxOf(to, words[hit].to)
        }
        return Triple(Tier.FUZZY, from..to, typos * 100 + (words.size - q.size))
    }

    /** 编辑距离 ≤ 1（替换 / 增 / 删 / 相邻对调一处）。 */
    fun within1(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length
        val lb = b.length
        if (kotlin.math.abs(la - lb) > 1) return false
        var i = 0
        while (i < la && i < lb && a[i] == b[i]) i++
        if (la == lb) {
            if (a.regionMatches(i + 1, b, i + 1, la - i - 1)) return true
            // 相邻对调
            return i + 1 < la && a[i] == b[i + 1] && a[i + 1] == b[i] && a.regionMatches(i + 2, b, i + 2, la - i - 2)
        }
        return if (la > lb) a.regionMatches(i + 1, b, i, lb - i) else a.regionMatches(i, b, i + 1, la - i)
    }

    /** 一条的最好命中（各字段里档位最小的；同档片名优先）。 */
    fun <T> doc(d: SearchDoc<T>, q: SearchQuery): SearchHit<T>? {
        var best: SearchHit<T>? = null
        for (f in d.fields) {
            val m = field(f, q) ?: continue
            if (best == null || m.first < best.tier || (m.first == best.tier && m.third < best.score)) {
                best = SearchHit(d, m.first, f.kind, if (f.kind == FieldKind.Name) m.second else null, m.third, f.text, m.second)
                if (m.first == Tier.EXACT && f.kind == FieldKind.Name) break
            }
        }
        return best
    }

    /** 档位 → 命中字段 → 档内的好坏（缩写的间隔、英文的错字；别的档都是 0）→ 类型 → 名字短（更贴近）→ 评分 → 名字。
     *  同档名字更贴近、更短的在评分前（所有档统一，协调者 2026-11 定）。 */
    private val ORDER: Comparator<SearchHit<*>> = compareBy<SearchHit<*>> { it.tier }
        .thenBy { it.field.ordinal }
        .thenBy { it.score }
        .thenBy { it.doc.kindRank }
        // 同档名字更短（更贴近）的在前，再看评分（所有档统一）。
        .thenBy { it.doc.name.length }
        .thenByDescending { it.doc.rating }
        .thenBy { it.doc.name }

    /** 在 [docs] 里找，排好序取前 [limit] 条。 */
    fun <T> search(docs: List<SearchDoc<T>>, q: SearchQuery, limit: Int = Int.MAX_VALUE): List<SearchHit<T>> {
        if (q.isEmpty) return emptyList()
        val out = ArrayList<SearchHit<T>>()
        for (d in docs) doc(d, q)?.let { out += it }
        out.sortWith(ORDER)
        return if (out.size > limit) out.subList(0, limit).toList() else out
    }

    /** 一个名字里 [query] 该上专色的字（单集行、人名这些只有一个字段的地方用）。 */
    fun range(name: String, query: String): IntRange? {
        val q = SearchQuery(query)
        if (q.isEmpty) return null
        return field(SearchField(FieldKind.Name, name), q)?.second
    }
}

/** 「剧名 + 集号」：仙逆 12、仙逆第十二集、仙逆 E12、仙逆 S01E12、S01E02。 */
data class EpisodeAsk(val series: String, val season: Int?, val episode: Int)

object EpisodeAsks {
    private const val CN = "零〇一二两三四五六七八九十百千"
    private val SXE = Regex("""^(.*?)[\s._-]*[sS](\d{1,2})\s*[eE][pP]?(\d{1,4})$""")
    private val DI = Regex("""^(.+?)\s*第\s*([0-9$CN]+)\s*[集话話回]$""")
    private val EP = Regex("""^(.+?)\s+[eE][pP]?\s*(\d{1,4})$""")
    private val EP_HAN = Regex("""^(.*[㐀-鿿])\s*[eE][pP]?\s*(\d{1,4})$""")
    private val SPACED = Regex("""^(.+?)\s+([0-9$CN]+)$""")
    private val GLUED = Regex("""^(.*[㐀-鿿])(\d{1,4})$""")

    fun parse(raw: String): EpisodeAsk? {
        val s = buildString { for (c in raw.trim()) append(if (c.code in 0xFF01..0xFF5E) (c.code - 0xFEE0).toChar() else if (c == '　') ' ' else c) }
        if (s.isEmpty()) return null
        SXE.matchEntire(s)?.let { m -> return EpisodeAsk(m.groupValues[1].trim(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }
        for (re in listOf(DI, EP, EP_HAN, SPACED, GLUED)) {
            val m = re.matchEntire(s) ?: continue
            val series = m.groupValues[1].trim()
            val n = number(m.groupValues[2]) ?: continue
            if (series.isEmpty() || n <= 0) continue
            return EpisodeAsk(series, null, n)
        }
        return null
    }

    private fun number(s: String): Int? = s.toIntOrNull() ?: SearchFold.cnNumber(s)
}

/**
 * 一个账号的片库索引：片（电影 / 剧集 / 合集 / 其它视频）、单集、人物，各自预算好的 [SearchDoc]。
 * 建一次（在后台线程），查很多次。
 */
class LibraryEngine(titles: List<Title>, episodes: List<Title>, people: List<Person>) {
    val titleDocs: List<SearchDoc<Title>> = titles.distinctBy { it.id }.map { titleDoc(it) }
    val episodeDocs: List<SearchDoc<Title>> = episodes.distinctBy { it.id }.map { episodeDoc(it) }
    val personDocs: List<SearchDoc<Person>> = people.distinctBy { it.id }.map { personDoc(it) }
    private val bySeries: Map<String, List<SearchDoc<Title>>> = episodeDocs.filter { it.item.seriesId != null }.groupBy { it.item.seriesId!! }

    val size: Int get() = titleDocs.size + episodeDocs.size + personDocs.size

    fun titles(query: String, limit: Int = 30): List<SearchHit<Title>> = SearchMatch.search(titleDocs, SearchQuery(query), limit)

    fun people(query: String, limit: Int = 12): List<SearchHit<Person>> = SearchMatch.search(personDocs, SearchQuery(query), limit)

    /** 单集：先是「剧名 + 集号」直达的那一集（[direct]），再是集名命中的。 */
    fun episodes(query: String, limit: Int = 30): List<SearchHit<Title>> {
        val direct = direct(query)
        val named = SearchMatch.search(episodeDocs, SearchQuery(query), limit)
        val seen = direct.map { it.doc.id }.toHashSet()
        return (direct + named.filter { seen.add(it.doc.id) }).take(limit)
    }

    /** 「剧名 + 集号」→ 那一集（剧名按片名的前三档或拼音认；没有这一集 → 空）。同集号多季的：指定了季就那一季，没指定第 1 季在前。 */
    fun direct(query: String): List<SearchHit<Title>> = directWithSeries(query)?.second.orEmpty()

    /** 直达的剧（只打了 S01E02、没写剧名 → null）和它的那几集。 */
    fun directWithSeries(query: String): Pair<SearchHit<Title>?, List<SearchHit<Title>>>? {
        val ask = EpisodeAsks.parse(query) ?: return null
        val seriesDocs = titleDocs.filter { it.item.kind == Kind.Series }
        val candidates: List<Pair<SearchHit<Title>?, List<SearchDoc<Title>>>> = if (ask.series.isEmpty()) {
            listOf(null to episodeDocs)
        } else {
            SearchMatch.search(seriesDocs, SearchQuery(ask.series), 5).filter { it.tier <= Tier.PINYIN }.map { it to bySeries[it.doc.id].orEmpty() }
        }
        for ((series, eps) in candidates) {
            val hits = eps.filter { it.item.episodeNo == ask.episode && (ask.season == null || it.item.seasonNo == ask.season) }
                .sortedWith(compareBy<SearchDoc<Title>> { if (it.item.seasonNo == 1) 0 else 1 }.thenBy { it.item.seasonNo ?: 0 })
            if (hits.isEmpty()) continue
            val out = hits.take(if (series == null) 30 else 6).map { SearchHit(it, Tier.EXACT, FieldKind.Name, null, 0) }
            return series to out
        }
        return null
    }

    companion object {
        fun titleDoc(t: Title): SearchDoc<Title> {
            val fields = ArrayList<SearchField>(3)
            val name = SearchField(FieldKind.Name, t.name)
            fields += name
            val seen = hashSetOf(name.fold.plain.text)
            t.originalTitle?.let { SearchField(FieldKind.Original, it) }?.takeIf { seen.add(it.fold.plain.text) }?.let { fields += it }
            t.sortName?.let { SearchField(FieldKind.Sort, it) }?.takeIf { seen.add(it.fold.plain.text) }?.let { fields += it }
            val rank = when (t.kind) {
                Kind.Movie, Kind.Series -> 0
                Kind.BoxSet -> 1
                Kind.Episode -> 3
                Kind.Person -> 4
                else -> 2
            }
            return SearchDoc(t, t.id, t.name, fields, rank, t.rating?.toFloatOrNull() ?: 0f)
        }

        fun episodeDoc(t: Title): SearchDoc<Title> = SearchDoc(t, t.id, t.name, listOf(SearchField(FieldKind.Name, t.name)), 3, t.rating?.toFloatOrNull() ?: 0f)

        fun personDoc(p: Person): SearchDoc<Person> = SearchDoc(p, p.id, p.name, listOf(SearchField(FieldKind.Name, p.name)), 4, 0f)
    }
}

/**
 * 旧入口（`searchTitles(List<Title>)`、`searchPeopleLocal(List<Person>)`）用的预算结果：按列表对象记住最近一份，
 * 同一个列表连着查不用每个字都重算 fold / 拼音。
 */
internal object DocMemo {
    private class Entry<T>(val list: List<T>, val docs: List<SearchDoc<T>>)

    @Volatile
    private var titles: Entry<Title>? = null

    @Volatile
    private var people: Entry<Person>? = null

    fun titles(list: List<Title>): List<SearchDoc<Title>> {
        titles?.takeIf { it.list === list }?.let { return it.docs }
        val docs = list.distinctBy { it.id }.map { LibraryEngine.titleDoc(it) }
        titles = Entry(list, docs)
        return docs
    }

    /** 索引建好时把人物那一份直接放进来（搜索页的人名单就是这个列表对象，不用在界面线程上再算一遍）。 */
    fun seedPeople(list: List<Person>, docs: List<SearchDoc<Person>>) {
        people = Entry(list, docs)
    }

    fun people(list: List<Person>): List<SearchDoc<Person>> {
        people?.takeIf { it.list === list }?.let { return it.docs }
        val docs = list.distinctBy { it.id }.map { LibraryEngine.personDoc(it) }
        people = Entry(list, docs)
        return docs
    }
}
