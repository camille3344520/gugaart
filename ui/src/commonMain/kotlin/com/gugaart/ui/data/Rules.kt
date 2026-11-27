package com.gugaart.ui.data

import kotlin.math.roundToInt

/**
 * 真实数据的兜底规则（docs/data-edge-cases.md）。全部是纯函数、有单测；版式函数拿到的是处理好的结果，三端一致。
 * 数值（字号档位对应的 sp）不在这里：这里只给「第几档」，字号在 `theme/`。
 */
object Rules {
    /* ---- 集号（§2：四位数集号不撑破编号栏） ---- */

    /** 集号的字号档：0 = 1–2 位，1 = 3 位，2 = 4 位及以上（手机 28 / 21 / 16.5）。 */
    fun episodeNoTier(n: Int): Int = when {
        n < 100 -> 0
        n < 1000 -> 1
        else -> 2
    }

    /** 集号的排法：1–9 补一个 0（「01」），其余原样；绝对集号（第 1089 集就写 1089）。 */
    fun episodeNo(n: Int): String = if (n in 0..9) "0$n" else n.toString()

    /** 一季超过 [size] 集分页；标签用**绝对集号**（第 21 季从 892 起 → 「892–941」）。[numbers] 是这一季的集号，已按播出顺序。 */
    fun episodePages(numbers: List<Int>, size: Int = 50): List<EpisodePage> {
        if (numbers.size <= size) return emptyList()
        return numbers.chunked(size).mapIndexed { i, chunk -> EpisodePage(i, i * size, chunk.size, "${chunk.first()}–${chunk.last()}") }
    }

    /**
     * 按这一季**真实的集号**分页（用户 2026-11-15：「选集的这个窗口有问题啊，显示的200-249 可以选到270，用完美世界测试」——
     * 有第 0 集、中间缺集的剧，按「第一集 + 序号」推出来的标签和这一页里真的集号对不上）。[numbers] = 按服务器顺序每一集的集号
     * （没有集号 = null）；每页 [size] 集，标签 = 这一页里最小到最大的集号，一页都没有集号就用序号。纯函数，有单测。
     */
    fun episodePagesOf(numbers: List<Int?>, size: Int = 50): List<EpisodePage> {
        if (numbers.size <= size) return emptyList()
        return numbers.chunked(size).mapIndexed { i, chunk ->
            val nums = chunk.filterNotNull()
            val label = if (nums.isEmpty()) "${i * size + 1}–${i * size + chunk.size}" else "${nums.min()}–${nums.max()}"
            EpisodePage(i, i * size, chunk.size, label)
        }
    }

    /* ---- 片名（§1） ---- */

    /** 字数（一个汉字算 1；连续的拉丁字母 / 数字两个算 1，因为它们窄一半）。 */
    fun titleUnits(name: String): Int {
        var half = 0
        var full = 0
        for (ch in name) {
            when {
                ch.isWhitespace() -> half++
                ch.code < 0x2E80 -> half++
                else -> full++
            }
        }
        return full + (half + 1) / 2
    }

    /** 无 logo 时宋体片名的四档：≤ 4 字 → 0（×1），≤ 7 → 1（×0.78），≤ 10 → 2（×0.6），更长 → 3（×0.47，允许两行）。 */
    fun titleTier(name: String): Int {
        val n = titleUnits(name)
        return when {
            n <= 4 -> 0
            n <= 7 -> 1
            n <= 10 -> 2
            else -> 3
        }
    }

    val TITLE_SCALE = floatArrayOf(1f, 0.78f, 0.6f, 0.47f)

    /** 片名主要是拉丁字母（英文长名用斜体衬线、缩一号）。 */
    fun isLatinTitle(name: String): Boolean {
        val letters = name.count { !it.isWhitespace() }
        if (letters == 0) return false
        return name.count { it.code in 0x21..0x24F } * 10 >= letters * 7
    }

    /* ---- 数字（§4） ---- */

    /** 评分：一位小数；满分写「10」；没有 / 0 → null（不占位）。 */
    fun rating(r: Double?): String? {
        if (r == null || r <= 0.0 || r.isNaN()) return null
        val tenths = (r * 10).roundToInt()
        if (tenths >= 100) return "10"
        return "${tenths / 10}.${tenths % 10}"
    }

    /** 0..1 的进度；没有时长 / 没有位置 → 0。 */
    fun progress(positionTicks: Long?, runtimeTicks: Long?): Float {
        if (positionTicks == null || runtimeTicks == null || runtimeTicks <= 0 || positionTicks <= 0) return 0f
        return (positionTicks.toDouble() / runtimeTicks).toFloat().coerceIn(0f, 1f)
    }

    /** 「19 分」「1 小时 46 分」；不足一分钟算 1 分。 */
    fun minutes(sec: Long): String {
        val m = ((sec + 30) / 60).coerceAtLeast(1)
        return if (m < 60) "$m 分" else if (m % 60 == 0L) "${m / 60} 小时" else "${m / 60} 小时 ${m % 60} 分"
    }

    /** 只用分钟：「78 分」「106 分」（进度里的「余 78 分」不换算成小时，和设计稿一致）。 */
    fun minutesOnly(sec: Long): String = "${((sec + 30) / 60).coerceAtLeast(1)} 分"

    /** 「26:30」「1:02:03」。 */
    fun clock(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        fun two(v: Long) = if (v < 10) "0$v" else v.toString()
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
    }

    /**
     * 进度的一句话：0 % → 「未看」（观看状态只有未看 / 已看两种，D-watch），**不画进度线**；
     * 进行中 → 「余 19 分 · 59%」；没有时长只写百分比。
     */
    fun progressLabel(progress: Float, runtimeSec: Long?, phone: Boolean): String {
        if (progress <= 0f) return "未看"
        val pct = (progress * 100).roundToInt().coerceIn(1, 99)
        if (runtimeSec == null || runtimeSec <= 0) return "$pct%"
        return "余 ${minutesOnly((runtimeSec * (1f - progress)).toLong())} · $pct%"
    }

    /** 只要「余 19 分」这一半（目录行）。 */
    fun remaining(progress: Float, runtimeSec: Long?): String? {
        if (runtimeSec == null || runtimeSec <= 0) return null
        return "余 ${minutesOnly((runtimeSec * (1f - progress.coerceIn(0f, 1f))).toLong())}"
    }

    /* ---- 文字（§3） ---- */

    /** 栏目行：最多 4 段，空的不要。 */
    fun kicker(parts: List<String?>): List<String> = parts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.take(4)

    private val DATE = Regex("""\d{4}\s*[-/.年]\s*\d{1,2}\s*[-/.月]\s*\d{1,2}""")

    /** 发行信息词：出现了就说明这条 tagline 不是宣传语（真实样本：「配信開始日 2025-11-15」）。可加。 */
    val DIRTY_WORDS = listOf("配信", "発売", "发售", "發售", "上映日", "公開日", "release date", "coming soon", "http://", "https://", "www.")

    /** 脏宣传语：带日期样式或发行信息词。 */
    fun isDirtyTagline(tagline: String): Boolean {
        val t = tagline.trim()
        if (t.isEmpty()) return true
        if (DATE.containsMatchIn(t)) return true
        val lower = t.lowercase()
        return DIRTY_WORDS.any { lower.contains(it) }
    }

    /** 简介原文带换行和全角空格：只去换行和全角空格，保留英文单词之间的空格。 */
    fun cleanOverview(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val sb = StringBuilder(raw.length)
        var lastSpace = false
        for (ch in raw) {
            val c = when (ch) {
                '\r', '\n', '\t', '　', ' ' -> ' '
                else -> ch
            }
            if (c == ' ') {
                lastSpace = true
                continue
            }
            // 空格只在两个拉丁字符之间保留
            if (lastSpace && sb.isNotEmpty() && sb.last().code < 0x2E80 && c.code < 0x2E80) sb.append(' ')
            lastSpace = false
            sb.append(c)
        }
        return sb.toString()
    }

    private const val ENDERS = "。！？!?…"

    /** 简介的头一两句（不超过 [maxChars]；第一句就超了 → 整句都要，交给版面按行数截）。 */
    fun firstSentences(overview: String, maxChars: Int = 60): String {
        val text = cleanOverview(overview)
        if (text.isEmpty()) return ""
        var end = -1
        var i = 0
        while (i < text.length) {
            if (text[i] in ENDERS) {
                // 把连着的结束标点和后引号一起带上
                var j = i + 1
                while (j < text.length && (text[j] in ENDERS || text[j] in "”」』\"")) j++
                if (end < 0 || j <= maxChars) end = j
                if (j > maxChars) break
                i = j
            } else {
                i++
            }
        }
        return if (end < 0) text else text.substring(0, end)
    }

    /** 导语的来源：tagline（过了脏词检查）→ 简介的头一两句 → null（这一栏不出现）。 */
    fun lead(taglines: List<String>, overview: String?): String? {
        taglines.firstOrNull { !isDirtyTagline(it) }?.let { return it.trim() }
        return firstSentences(overview.orEmpty()).takeIf { it.isNotEmpty() }
    }

    /** 长简介折叠：超过 [limit] 字折起来，按钮写「展开全文（N 字）」；不超过就不出现「展开」。 */
    fun fold(overview: String?, limit: Int): Fold {
        val text = cleanOverview(overview)
        return Fold(text, text.length, text.length > limit)
    }

    /** 出品方太多：截 [keep] 家 +「等 N 家」。 */
    fun studios(names: List<String>, keep: Int = 5): String? {
        val list = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty()) return null
        return if (list.size <= keep) list.joinToString(" · ") else list.take(keep).joinToString(" · ") + " 等 ${list.size} 家"
    }

    /** 斜纹空版上的字：演员取姓（第一个字；拉丁名取首字母大写），片名取前两个字。 */
    fun monogram(name: String, person: Boolean): String {
        val t = name.trim()
        if (t.isEmpty()) return "?"
        val first = t.first()
        return if (first.code < 0x2E80) first.uppercase() else if (person) first.toString() else t.take(2)
    }

    /**
     * 题材名统一成中文：TMDB / TVDB 刮削来的英文题材名（「Sci-Fi & Fantasy」）映射成中文；已经是中文的、表里没有的保留原文。
     * 比较时不分大小写、不看空格和 `&` / `and` 的写法。只改显示用的名字（界面模型里的 `genres`），不拿它去查服务器。
     */
    fun genreZh(name: String): String {
        val t = name.trim()
        if (t.isEmpty() || t.any { it.code >= 0x2E80 }) return t
        return GENRE_ZH[genreKey(t)] ?: t
    }

    private fun genreKey(s: String): String = s.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }

    private val GENRE_ZH: Map<String, String> = listOf(
        "Action" to "动作", "Adventure" to "冒险", "Action & Adventure" to "动作冒险", "Animation" to "动画", "Anime" to "动画",
        "Comedy" to "喜剧", "Crime" to "犯罪", "Documentary" to "纪录", "Drama" to "剧情", "Family" to "家庭", "Fantasy" to "奇幻",
        "History" to "历史", "Horror" to "恐怖", "Music" to "音乐", "Musical" to "歌舞", "Mystery" to "悬疑", "Romance" to "爱情",
        "Science Fiction" to "科幻", "Sci-Fi" to "科幻", "SciFi" to "科幻", "Sci-Fi & Fantasy" to "科幻奇幻", "Science Fiction & Fantasy" to "科幻奇幻",
        "TV Movie" to "电视电影", "Thriller" to "惊悚", "Suspense" to "悬疑", "War" to "战争", "War & Politics" to "战争政治", "Western" to "西部",
        "Kids" to "儿童", "Children" to "儿童", "News" to "新闻", "Reality" to "真人秀", "Reality TV" to "真人秀", "Soap" to "肥皂剧",
        "Talk" to "脱口秀", "Talk Show" to "脱口秀", "Game Show" to "竞赛节目", "Biography" to "传记", "Sport" to "运动", "Sports" to "运动",
        "Short" to "短片", "Adult" to "成人", "Erotic" to "情色", "Film Noir" to "黑色电影", "Martial Arts" to "武侠", "Mini-Series" to "迷你剧",
        "Home and Garden" to "家居", "Food" to "美食", "Travel" to "旅行", "Indie" to "独立", "Disaster" to "灾难", "Superhero" to "超级英雄",
        "Supernatural" to "超自然", "Psychological" to "心理", "Slice of Life" to "日常", "School" to "校园", "Historical" to "历史",
    ).associate { (en, zh) -> genreKey(en) to zh }

    /** Emby 的 ticks（100 ns）→ 秒。 */
    fun ticksToSec(ticks: Long?): Long? = ticks?.takeIf { it > 0 }?.let { it / 10_000_000 }
}

data class EpisodePage(val index: Int, val start: Int, val count: Int, val label: String)

data class Fold(val text: String, val chars: Int, val folded: Boolean)
