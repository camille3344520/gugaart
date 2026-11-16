package com.gugaart.ui.player

import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.core.DanmakuMatchInput
import com.gugugaga.danmaku.match.DanmakuAnime
import com.gugugaga.danmaku.match.DanmakuPhase

/*
 * 分季的剧不要配到别的季（用户 2026-11-16：「从零开始的异世界，这个有好多部，我库里面的是第四季，为啥匹配不到。如果没有的话不要误匹配。
 * 就这种按季的要看下怎么处理」）。
 *
 * 实测（上游匹配器 + 用户的弹幕服务器）：TMDB 把《Re：从零开始的异世界生活》记成「共 1 季」，没有第 4 季的年份，上游就拿整部剧的
 * 首播年（2016）比年份——第一季的条目（2016，无季标记，69 集）+20、第四季的条目（2026）-25，于是自动采用了第一季，
 * 再把第 4 季第 1 集「按集号直接映射」成第一季第 1 集：弹幕全是错的。上游只读，这里在画报这一层补一道检查：
 *
 * 匹配好以后（Ready），这一集是第 N 季（N ≥ 2），配上的却是**不带季标记、名字就是剧名本身的条目**（= 第一季）或者**别的季**：
 *  1. 去服务器搜「剧名 第N季」，有专门的第 N 季条目 → 换成它（[PlayerDanmaku.pickManual]，上游会记住这一季，后面几集直接用）；
 *  2. 没有：不放错的弹幕（清空这一集的弹幕，面板里写「没有第 N 季的弹幕」）。
 * 篇章名的季（「鬼灭之刃 锻刀村篇」这种名字里带别的字的条目）不管——上游用 TMDB 的季年份和集数判断那种。用户在弹幕来源里自己挑过的这一集也不管。
 */
object DanmakuSeasonGuard {
    /** 这一集交给匹配器的条目信息（[record] 在 PlaySession 的 matchInput 里记下）。 */
    private val inputs = HashMap<String, DanmakuMatchInput>()
    private val checked = HashSet<String>()
    private val blocked = HashMap<String, String>()
    /** 已经替用户换过一次的那一集：不再换（防止来回换）。 */
    private val corrected = HashSet<String>()
    /** 用户自己在弹幕来源里挑过的那一集。 */
    var userChose: String? = null

    fun record(itemId: String, input: DanmakuMatchInput) {
        inputs[itemId] = input
    }

    /** 这一集的季号（第 2 季起才有意义）。 */
    fun targetSeason(itemId: String?): Int? = itemId?.let { inputs[it] }?.takeIf { it.kind == "episode" && !it.isSpecial }?.seasonNumber

    /** 这一集被拦下时，面板里那一行（null = 没拦）。 */
    fun status(itemId: String?): String? = itemId?.let { blocked[it] }

    /** 匹配好以后看一眼；换了 / 拦了返回 true。 */
    suspend fun check(d: PlayerDanmaku, itemId: String?): Boolean {
        val st = d.state
        if (itemId == null || st.phase != DanmakuPhase.Ready || userChose == itemId || itemId in corrected) return false
        val input = inputs[itemId] ?: return false
        val season = targetSeason(itemId)?.takeIf { it >= 2 } ?: return false
        val matched = st.title ?: return false
        if (!checked.add("$itemId|$matched")) return false
        if (!wrongSeason(matched, input.title, season)) return false
        val hits = seasonQueries(input.title, season)
            .flatMap { q -> runCatching { d.search(q) }.getOrDefault(emptyList()) }
            .distinctBy { it.animeId }
        val right = pickSeasonEntry(hits, input.title, season, input.episodeNumber ?: 1)
        if (right != null) {
            corrected.add(itemId)
            d.pickManual(right)
            return true
        }
        // 没有专门的第 N 季：不拿别的季顶替
        blocked[itemId] = "没有第 ${season} 季的弹幕（没用别的季顶替）"
        d.loadItems(emptyList())
        return true
    }

    /** 测试用。 */
    fun reset() {
        inputs.clear()
        checked.clear()
        blocked.clear()
        corrected.clear()
        userChose = null
    }
}

/* ------------------------------------------------------------------ 纯函数（有单测） */

private val CN_DIGITS = mapOf('一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)

/** 「四」「十二」「二十」「4」→ 数字。 */
fun cnNumber(s: String): Int? {
    s.toIntOrNull()?.let { return it }
    if (s.isEmpty()) return null
    if (s == "十") return 10
    val ten = s.indexOf('十')
    if (ten < 0) return if (s.length == 1) CN_DIGITS[s[0]] else null
    val hi = if (ten == 0) 1 else CN_DIGITS[s[0]] ?: return null
    val lo = if (ten == s.length - 1) 0 else CN_DIGITS[s[ten + 1]] ?: return null
    return hi * 10 + lo
}

/** 数字 → 「四」「十二」。 */
fun cnNumeral(n: Int): String {
    val d = "零一二三四五六七八九"
    return when {
        n < 10 -> d[n].toString()
        n == 10 -> "十"
        n < 20 -> "十" + d[n % 10]
        n < 100 -> d[n / 10] + "十" + (if (n % 10 == 0) "" else d[n % 10].toString())
        else -> n.toString()
    }
}

private val SEASON_PATTERNS = listOf(
    Regex("""第\s*([0-9一二两三四五六七八九十]+)\s*[季期部]"""),
    Regex("""(?i)season\s*([0-9]+)"""),
    Regex("""(?i)(?:^|[^a-z0-9])s([0-9]{1,2})(?:[^0-9]|$)"""),
    Regex("""(?i)([0-9]+)(?:st|nd|rd|th)\s*season"""),
)

/** 弹幕条目名里的季号；没有 = null。纯函数。 */
fun danmakuSeasonOf(title: String): Int? {
    for (p in SEASON_PATTERNS) p.find(title)?.groupValues?.get(1)?.let { cnNumber(it) }?.let { return it }
    return null
}

/** 条目名去掉年份、【类型】、from 来源、季标记，剩下的「正名」。 */
fun danmakuCore(title: String, dropSeason: Boolean = true): String {
    var s = title.replace(Regex("""\s*from\s+\S+\s*$"""), "").replace(Regex("""【[^】]*】"""), "").replace(Regex("""\s*[(（]\d{4}[)）]"""), "")
    if (dropSeason) for (p in SEASON_PATTERNS) s = s.replace(p, " ")
    return s.trim()
}

/** 比较用：去掉标点空白、全角转半角、小写。 */
fun normTitle(s: String): String = buildString {
    for (ch in s) {
        val c = if (ch in '！'..'～') (ch - 0xFEE0) else ch
        if (c.isLetterOrDigit()) append(c.lowercaseChar())
    }
}

/**
 * 配上的条目对不对得上第 [season] 季：不带季标记、正名就是剧名本身（= 第一季）→ 不对；带了别的季号 → 不对；
 * 带别的字（篇章名、剧场版副标题…）→ 不归这里管（返回 false）。纯函数，有单测。
 */
fun wrongSeason(matchedTitle: String, seriesTitle: String, season: Int): Boolean {
    val marked = danmakuSeasonOf(matchedTitle)
    if (marked != null) return marked != season
    return normTitle(danmakuCore(matchedTitle)) == normTitle(seriesTitle)
}

/** 去服务器搜的词：「剧名 第四季」「剧名 第4季」。 */
fun seasonQueries(seriesTitle: String, season: Int): List<String> =
    listOf("$seriesTitle 第${cnNumeral(season)}季", "$seriesTitle 第${season}季").distinct()

/**
 * 搜到的条目里挑第 [season] 季的那一个：季号对、去掉季标记后正名就是剧名（不要「前半 / 后半」「剧场版」这种带别的字的，除非只有它们）；
 * 集数够得着这一集的优先，再按集数多的。没有 = null。纯函数，有单测。
 */
fun pickSeasonEntry(hits: List<DanmakuAnime>, seriesTitle: String, season: Int, episode: Int): DanmakuAnime? {
    val series = normTitle(seriesTitle)
    val same = hits.filter { danmakuSeasonOf(it.title) == season && normTitle(danmakuCore(it.title)).startsWith(series) }
    if (same.isEmpty()) return null
    val exact = same.filter { normTitle(danmakuCore(it.title)) == series }
    val pool = exact.ifEmpty { same }
    return pool.sortedWith(compareByDescending<DanmakuAnime> { it.episodeCount >= episode }.thenByDescending { it.episodeCount }).firstOrNull()
}
