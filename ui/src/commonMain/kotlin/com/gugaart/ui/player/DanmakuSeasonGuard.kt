package com.gugaart.ui.player

import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.core.DanmakuMatchInput
import com.gugugaga.danmaku.match.DanmakuAnime
import com.gugugaga.danmaku.match.DanmakuPhase
import kotlin.math.abs

/*
 * 分季的剧不要配到别的季（用户 2026-11-16：「从零开始的异世界，这个有好多部，我库里面的是第四季，为啥匹配不到。如果没有的话不要误匹配。
 * 就这种按季的要看下怎么处理」）。
 *
 * 实测（上游匹配器 + 用户的弹幕服务器）：TMDB 把《Re：从零开始的异世界生活》记成「共 1 季」，没有第 4 季的年份，上游就拿整部剧的
 * 首播年（2016）比年份——第一季的条目（2016，无季标记，69 集）+20、第四季的条目（2026）-25，于是自动采用了第一季，
 * 再把第 4 季第 1 集「按集号直接映射」成第一季第 1 集：弹幕全是错的。上游只读，这里在画报这一层补一道检查：
 *
 * 匹配好以后（Ready），这一集是第 N 季，配上的却是**不带季标记、名字就是剧名本身的条目**（= 第一季）或者**别的季**：
 *  1. 去服务器搜「剧名 第N季」和剧名本身，有专门的第 N 季条目 → 换成它（[PlayerDanmaku.pickManual]，上游会记住这一季，后面几集直接用）；
 *  2. 没有：不放错的弹幕（清空这一集的弹幕，面板里写「没有第 N 季的弹幕」）。
 * 篇章名的季（「鬼灭之刃 锻刀村篇」这种名字里带别的字的条目）不管——上游用 TMDB 的季年份和集数判断那种。用户在弹幕来源里自己挑过的这一集也不管。
 *
 * 2026-11-24（主理人：「弹幕搜索，如果是多季的就没办法精准搜索的了，比如头文字D」）：季不写成「第N季」的剧也要认。
 * 《头文字D》六季在弹幕服务器上叫 First / Second / Third / Fourth / Fifth / Final Stage、第三部 / 第五季 / 第六部，
 * 季序号对不上名字，但**年份一一对得上**（S1 1998、S2 1999、S3 2001、S4 2004、S5 2012、S6 2014）。所以认定一季的证据按强弱：
 *  1. 名字里的季序词（第N季/部/期、Season N、SN、Nth Season、First…Sixth Stage、Final Stage / 最终季 = 最后一季）；
 *  2. 年份：条目的 startDate 年份 = 这一季在 Emby 的首播年份；±1 年只在没有更好的候选时用；
 *  3. 类型：电影 / 剧场版 / OVA / Extra / Battle / Legend / 续作・别名 不算某一季（Emby 那季本身是特别篇、季号 0 时除外）；
 *  4. 集数：差太多降分，不硬卡（各家集数不一）。
 * 季序词和年份**冲突**的（名字写 Second Stage、年份却是 2004）这条不要。同一季多个来源按集数 / 来源偏好挑一个；
 * 拿不准（不是同一部、集数又接近）就不替用户选，弹出来让人挑。
 */
object DanmakuSeasonGuard {
    /** 这一集交给匹配器的条目信息（[record] 在 PlaySession 的 matchInput 里记下）。 */
    private val inputs = HashMap<String, DanmakuMatchInput>()

    /** 这一季在 Emby 的事实（首播年 / 集数 / 最大的季号），由 PlaySession 取一次交进来。 */
    private val facts = HashMap<String, SeasonFacts>()
    private val checked = HashSet<String>()
    private val blocked = HashMap<String, String>()

    /** 已经替用户换过一次的那一集：不再换（防止来回换）。 */
    private val corrected = HashSet<String>()

    /** 用户自己在弹幕来源里挑过的那一集。 */
    var userChose: String? = null

    fun record(itemId: String, input: DanmakuMatchInput, seasonFacts: SeasonFacts? = null) {
        inputs[itemId] = input
        if (seasonFacts != null) facts[itemId] = seasonFacts
    }

    /** 这一集的季号（第 2 季起才有意义）。 */
    fun targetSeason(itemId: String?): Int? = itemId?.let { inputs[it] }?.takeIf { it.kind == "episode" && !it.isSpecial }?.seasonNumber

    /** 这一季在 Emby 的事实；没取到 = null（退回只看名字的老路子）。 */
    fun seasonFacts(itemId: String?): SeasonFacts? = itemId?.let { facts[it] }

    /** 这一集被拦下时，面板里那一行（null = 没拦）。 */
    fun status(itemId: String?): String? = itemId?.let { blocked[it] }

    /** 匹配好以后看一眼；换了 / 拦了返回 true。 */
    suspend fun check(d: PlayerDanmaku, itemId: String?): Boolean {
        val st = d.state
        if (itemId == null || st.phase != DanmakuPhase.Ready || userChose == itemId || itemId in corrected) return false
        val input = inputs[itemId] ?: return false
        val season = targetSeason(itemId) ?: return false
        val f = facts[itemId]
        // 单季的剧照旧不管；多季的剧从第 1 季起就核对（《头文字D》第一季会被 2005 的真人电影顶掉）
        if (season < 1 || (season < 2 && (f?.lastSeason ?: 1) < 2)) return false
        val matched = st.title ?: return false
        if (!checked.add("$itemId|$matched")) return false
        if (!wrongSeason(matched, input.title, season, f?.year, f?.lastSeason)) return false
        // 搜「剧名 第N季」之外也搜剧名本身 / 原名：季不写成「第N季」的剧（头文字D）只有搜剧名才搜得到那一季的条目
        val queries = (seasonQueries(input.title, season) + listOfNotNull(input.title, input.originalTitle)).distinct()
        val hits = queries
            .flatMap { q -> runCatching { d.search(q) }.getOrDefault(emptyList()) }
            .distinctBy { it.animeId }
        val right = pickSeasonEntry(hits, input.title, season, input.episodeNumber ?: 1, f?.year, f?.episodes, f?.lastSeason)
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
        facts.clear()
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

private val EN_ORDINALS = listOf(
    "first", "second", "third", "fourth", "fifth", "sixth",
    "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth",
)

/** 英文序数 + Stage / Season（头文字D 的 First Stage…Fifth Stage；中间有没有空格都认）。 */
private val ORDINAL_STAGE = Regex(
    """(?i)(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth)\s*(?:stage|season)\b""",
)

/** Final Stage / Final Season / 最终季・部・期 = 这部剧的最后一季。 */
private val FINAL_MARK = Regex("""(?i)\bfinal\s*(?:stage|season)\b|最终[季部期]""")

/** 条目名里的季序词。[Final] 要配上 Emby 最大的季号才知道是第几季。 */
sealed class SeasonWord {
    data class Num(val n: Int) : SeasonWord()
    object Final : SeasonWord()
}

/** 条目名里的季序词；没有 = null。纯函数。 */
fun seasonWordOf(title: String): SeasonWord? {
    for (p in SEASON_PATTERNS) p.find(title)?.groupValues?.get(1)?.let { cnNumber(it) }?.let { return SeasonWord.Num(it) }
    ORDINAL_STAGE.find(title)?.groupValues?.get(1)?.let { w ->
        EN_ORDINALS.indexOf(w.lowercase()).takeIf { it >= 0 }?.let { return SeasonWord.Num(it + 1) }
    }
    if (FINAL_MARK.containsMatchIn(title)) return SeasonWord.Final
    return null
}

/** 弹幕条目名里的季号；没有 = null。[lastSeason] = 这部剧在 Emby 里最大的季号（Final 用它解析）。纯函数。 */
fun danmakuSeasonOf(title: String, lastSeason: Int? = null): Int? = when (val w = seasonWordOf(title)) {
    is SeasonWord.Num -> w.n
    SeasonWord.Final -> lastSeason
    null -> null
}

/** 条目名去掉年份、【类型】、from 来源、季标记，剩下的「正名」。 */
fun danmakuCore(title: String, dropSeason: Boolean = true): String {
    var s = title.replace(Regex("""\s*from\s+\S+\s*$"""), "").replace(Regex("""【[^】]*】"""), "").replace(Regex("""\s*[(（]\d{4}[)）]"""), "")
    if (dropSeason) {
        for (p in SEASON_PATTERNS) s = s.replace(p, " ")
        s = s.replace(ORDINAL_STAGE, " ").replace(FINAL_MARK, " ")
    }
    return s.trim()
}

/** 比较用：去掉标点空白、全角转半角、小写。 */
fun normTitle(s: String): String = buildString {
    for (ch in s) {
        val c = if (ch in '！'..'～') (ch - 0xFEE0) else ch
        if (c.isLetterOrDigit()) append(c.lowercaseChar())
    }
}

private val TITLE_YEAR = Regex("""[(（]\s*(\d{4})\s*[)）]""")

/** 条目名里括号中的年份（(1998) / （2004））。纯函数。 */
fun danmakuTitleYear(title: String): Int? =
    TITLE_YEAR.find(title)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 1800 }

/** 条目的年份：服务器的 startDate 优先，没有再看名字里的括号年。纯函数。 */
fun danmakuYearOf(a: DanmakuAnime): Int? =
    a.startDate.take(4).toIntOrNull()?.takeIf { it > 1800 } ?: danmakuTitleYear(a.title)

/** 电影 / 剧场版 / OVA 这类「不是某一季」的类型。 */
private val NON_TV_TYPE = Regex("""(?i)电影|剧场版|剧场|ova|oad|movie|film""")

/** 名字里就写明不是正篇某一季的：Extra / Battle / Legend、新剧场版、续作・别名、真人版。 */
private val NON_SEASON_TITLE = Regex("""(?i)extra\s*stage|battle\s*stage|legend|新剧场版|续作|别名|真人版""")

/** 名字里【电影】【剧场版】这种括号标记。 */
private val BRACKET_NON_TV = Regex("""(?i)【[^】]*(电影|剧场版|ova|oad|movie)[^】]*】""")

/**
 * 找「第 [season] 季」时不算数的条目：电影 / 剧场版 / OVA / Extra / Battle / Legend / 续作・别名。
 * Emby 那一季本身就是特别篇（季号 0）时不排除——特别篇的弹幕就在这些条目里。纯函数。
 */
fun seasonExcluded(title: String, type: String, typeDescription: String, season: Int): Boolean {
    if (season == 0) return false
    return NON_TV_TYPE.containsMatchIn(type) || NON_TV_TYPE.containsMatchIn(typeDescription) ||
        BRACKET_NON_TV.containsMatchIn(title) || NON_SEASON_TITLE.containsMatchIn(title)
}

/**
 * 一条条目算不算「第 [season] 季」的证据档：
 *  2 = 名字里的季序词对上；1 = 年份和这一季在 Emby 的首播年精确对上；0 = 年份差 ±1（只在没有更好的候选时用）；
 *  -1 = 看不出年份、正名就是剧名本身（当作第一季，老路子）。
 * null = 不算这一季：不是这部剧、季序词是别的季、年份差 ≥ 2（含「Second Stage 配 2004」这种词和年冲突）。
 * 类型不对的由 [seasonExcluded] 先筛掉。纯函数，有单测。
 */
fun seasonTier(
    title: String,
    year: Int?,
    seriesTitle: String,
    season: Int,
    seasonYear: Int?,
    lastSeason: Int? = null,
): Int? {
    val series = normTitle(seriesTitle)
    val core = normTitle(danmakuCore(title))
    if (!core.startsWith(series)) return null
    val word = danmakuSeasonOf(title, lastSeason)
    if (word != null) return if (word != season) null else if (year != null && seasonYear != null && abs(year - seasonYear) >= 2) null else 2
    if (year != null && seasonYear != null) {
        val d = year - seasonYear
        return when {
            d == 0 -> 1
            abs(d) == 1 -> 0
            else -> null
        }
    }
    // 年份看不出来：正名就是剧名本身的那条当作第一季（1.2.2 的老路子），别的季不认
    return if (core == series && season == 1) -1 else null
}

/**
 * 配上的条目对不对得上第 [season] 季：带了别的季号 → 不对；类型是电影 / 剧场版 / OVA… → 不对；
 * 年份和这一季差 ≥ 2 → 不对；不带季标记、正名就是剧名本身（= 第一季）配给第 2 季起 → 不对；
 * 带别的字（篇章名、剧场版副标题…）→ 不归这里管（返回 false）。纯函数，有单测。
 */
fun wrongSeason(matchedTitle: String, seriesTitle: String, season: Int, seasonYear: Int? = null, lastSeason: Int? = null): Boolean {
    val marked = danmakuSeasonOf(matchedTitle, lastSeason)
    if (marked != null) return marked != season
    if (seasonExcluded(matchedTitle, "", "", season)) return true
    if (seasonTier(matchedTitle, danmakuTitleYear(matchedTitle), seriesTitle, season, seasonYear, lastSeason) != null) return false
    return normTitle(danmakuCore(matchedTitle)) == normTitle(seriesTitle)
}

/** 去服务器搜的词：「剧名 第四季」「剧名 第4季」。 */
fun seasonQueries(seriesTitle: String, season: Int): List<String> =
    listOf("$seriesTitle 第${cnNumeral(season)}季", "$seriesTitle 第${season}季").distinct()

/** 上游的来源偏好（`MatchConfig.sourcePriority` 的默认值）；越靠前越优先。 */
val DANMAKU_SOURCE_PRIORITY = listOf("bilibili", "tencent", "iqiyi", "youku", "mgtv", "dandan", "migu", "360")

private fun sourceRank(source: String, priority: List<String>): Int =
    priority.indexOf(source.lowercase()).takeIf { it >= 0 } ?: priority.size

/**
 * 搜到的条目里挑第 [season] 季的那一个。证据按 [seasonTier] 的档：季序词 > 精确年 > ±1 年；类型不对、词和年冲突的先筛掉。
 * 同一档里：正名就是剧名的（整季）优先于带别的字的（前半 / 后半）；再按集数够得着这一集、集数多的、来源偏好。
 * 拿不准（同一档、不是同一部——正名或年份不同——集数又接近）= null，弹出来让人挑，不替用户选。
 * 没有 = null。纯函数，有单测。
 */
fun pickSeasonEntry(
    hits: List<DanmakuAnime>,
    seriesTitle: String,
    season: Int,
    episode: Int,
    seasonYear: Int? = null,
    seasonEpisodes: Int? = null,
    lastSeason: Int? = null,
    sourcePriority: List<String> = DANMAKU_SOURCE_PRIORITY,
): DanmakuAnime? {
    val series = normTitle(seriesTitle)
    val scored = hits.mapNotNull { a ->
        if (seasonExcluded(a.title, a.type, a.typeDescription, season)) return@mapNotNull null
        val tier = seasonTier(a.title, danmakuYearOf(a), seriesTitle, season, seasonYear, lastSeason) ?: return@mapNotNull null
        // 集数差太多降一档（不硬卡：各家集数不一）。条目 1 集、这一季 26 集这种明显不是整季。
        val penalty = if (seasonEpisodes != null && seasonEpisodes > 0 && a.episodeCount > 0 && a.episodeCount * 2 < seasonEpisodes) 1 else 0
        a to (tier - penalty)
    }
    if (scored.isEmpty()) return null
    val best = scored.maxOf { it.second }
    val pool = scored.filter { it.second == best }.map { it.first }
    val exact = pool.filter { normTitle(danmakuCore(it.title)) == series }
    val candidates = exact.ifEmpty { pool }
    val ranked = candidates.sortedWith(
        compareByDescending<DanmakuAnime> { it.episodeCount >= episode }
            .thenByDescending { it.episodeCount }
            .thenBy { sourceRank(it.source, sourcePriority) }
            .thenBy { it.animeId },
    )
    val top = ranked.first()
    val second = ranked.getOrNull(1) ?: return top
    val sameWork = normTitle(danmakuCore(top.title)) == normTitle(danmakuCore(second.title)) && danmakuYearOf(top) == danmakuYearOf(second)
    if (!sameWork && abs(top.episodeCount - second.episodeCount) <= 3) return null
    return top
}