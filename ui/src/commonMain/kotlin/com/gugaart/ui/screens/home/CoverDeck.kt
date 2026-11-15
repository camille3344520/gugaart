package com.gugaart.ui.screens.home

import com.gugaart.ui.data.Title
import kotlin.random.Random

/*
 * 首页封面换一波（用户 2026-11-15：「首页画报随机更新10-20分钟换一波，不然每次就那几个都看腻了」）。
 *
 * 以前封面那 8 期是定死的：继续观看在前，不够用最近添加补——片库不动，封面就永远是那几张。
 * 现在每一波：先上几部在看的（续看优先，但只占 [RESUME_SLOTS] 个位子，在看的多就轮着上），其余从片库里随机抽
 * （各库的最近添加 + 一次随机查询）；前两波上过的先不上（冷却），实在不够才回头用。
 * 选哪几期是纯函数 [rotateCovers]（有单测）；[CoverDeck] 只记「前两波上过谁」和随机数。
 */

/** 每一波里留给「在看的」的位子。 */
const val RESUME_SLOTS = 3

/** 冷却几波：前 [COOLDOWN_ROUNDS] 波上过的先不上。 */
const val COOLDOWN_ROUNDS = 2

/** 封面上「同一部」的键：单集按剧算。 */
fun coverKey(t: Title): String = t.seriesId ?: t.seriesName ?: t.id

/**
 * 这一波封面排哪几期。[resume] = 在看的（服务器给的顺序），[pool] = 片库里可抽的，[recent] = 冷却中的键（[coverKey]）。
 * - 在看的最多 [resumeSlots] 部，排在最前面，彼此保持原来的先后；冷却外的先上；
 * - 其余从 [pool] 里随机抽，冷却外的先上；
 * - 还不够 [limit]：剩下在看的补上；同一部只上一次。
 */
fun rotateCovers(resume: List<Title>, pool: List<Title>, recent: Set<String>, random: Random, limit: Int = COVER_ISSUES, resumeSlots: Int = RESUME_SLOTS): List<Title> {
    fun distinct(list: List<Title>): List<Title> {
        val seen = HashSet<String>()
        return list.filter { seen.add(coverKey(it)) }
    }
    val watching = distinct(resume)
    // 冷却外的随机排在前，冷却里的随机排在后
    fun ranked(list: List<Title>): List<Title> = list.filter { coverKey(it) !in recent }.shuffled(random) + list.filter { coverKey(it) in recent }.shuffled(random)
    val lead = ranked(watching).take(minOf(resumeSlots, limit)).sortedBy { watching.indexOf(it) }
    val used = lead.mapTo(HashSet()) { coverKey(it) }
    val out = ArrayList(lead)
    for (t in ranked(distinct(pool))) {
        if (out.size >= limit) break
        if (used.add(coverKey(t))) out += t
    }
    for (t in watching) {
        if (out.size >= limit) break
        if (used.add(coverKey(t))) out += t
    }
    return out
}

/**
 * 封面的牌堆（进程里只有一个）：记着前两波上过谁。[live] = false（离屏测试、截图）时不随机，还是原来那个定死的排法（[coverIssues]）。
 * 应用启动时把 [live] 打开（GugaartApp）。
 */
object CoverDeck {
    var live: Boolean = false
    private var random: Random = Random.Default
    private val rounds = ArrayDeque<Set<String>>()

    /** 现在冷却中的键。 */
    val recent: Set<String> get() = rounds.flatMapTo(HashSet()) { it }

    /** 上一次发出去的那一波。 */
    private var dealt: List<Title>? = null

    /** 上一次 [next] 是不是发了新的一波（封面剧照这时重挑：CoverBackdrops）。 */
    var lastWasNew: Boolean = false
        private set

    /** 下一次取数要不要发新的一波：启动、换一波、手动刷新时要；回到首页的静默刷新不要（封面不能一回来就变）。 */
    private var wantNew = true

    /** 上一波是哪个连接（`EmbyClient`）发的：换了服务器 / 账号 = 新的连接，上一台服务器的封面不能留着。 */
    private var owner: Any? = null

    /** 换一波 / 手动刷新（下拉、点 `VOL.`、F5、「再试」）之前叫：下一次取数发新的一波。 */
    fun requestNew() {
        wantNew = true
    }

    fun next(resume: List<Title>, pool: List<Title>, from: Any? = null): List<Title> {
        if (!live) return coverIssues(resume, pool)
        // 换了服务器（用户 2026-11-15：「切换服务器的时候得马上更新主页画报」）：上一台的封面一张都不留，冷却也清掉，发新的一波
        if (from != null && owner != null && from !== owner) {
            rounds.clear()
            dealt = null
            wantNew = true
        }
        if (from != null) owner = from
        val held = dealt
        if (!wantNew && held != null) {
            lastWasNew = false
            return holdCovers(held, resume, pool)
        }
        wantNew = false
        lastWasNew = true
        val covers = rotateCovers(resume, pool, recent, random)
        rounds.addLast(covers.mapTo(HashSet()) { coverKey(it) })
        while (rounds.size > COOLDOWN_ROUNDS) rounds.removeFirst()
        dealt = covers
        return covers
    }

    /** 测试用：回到刚启动的样子。 */
    fun reset(seed: Long? = null) {
        rounds.clear()
        dealt = null
        wantNew = true
        owner = null
        random = if (seed != null) Random(seed) else Random.Default
    }
}

/**
 * 静默刷新时封面保持原样：还是同几期、同样的顺序，只把每一期换成新数据里的同一部（进度、已看是新的）；新数据里没有的照旧用上一次的。
 */
fun holdCovers(held: List<Title>, resume: List<Title>, pool: List<Title>): List<Title> {
    val fresh = HashMap<String, Title>()
    for (t in resume + pool) fresh.getOrPut(coverKey(t)) { t }
    return held.map { fresh[coverKey(it)] ?: it }
}
