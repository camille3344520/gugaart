package com.gugaart.ui.screens.me

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugaart.ui.data.GugaPrefs
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/*
 * 读者证上的称号（用户 2026-11-15：「配置一些称号，观看到达一定数量的影片时弹框。10个等级吧，看下怎么分布」）。
 *
 * 数什么：**看过多少部**——电影看完一部算一部；剧集按季算，一季里看完过一集就算这一季一部（和豆瓣一样一季一个条目；
 * 不按单集数，否则追一部一千集的动画就满级了，只看电影的人却要看一千部）。数全部来自服务器的「已看」（只读查询）。
 *
 * 怎么分：前几级来得快（1、5、15 部——刚用就能拿到两三个），往后每级大约是上一级的 1.6–2 倍。
 * 最高一级 1000 部（用户 2026-11-15：「10级别 要1000部比较合适」）。按一周看三部（一年一百五十部左右）算：120 部「资深影迷」大约十个月，
 * 350 部两年多，1000 部「观影宗师」六七年；一周看六部的三年出头。
 * 称号按观影来（用户 2026-11-15：「称号按观影的来，不要画版风格」）：从入场观众、爆米花党，到资深影迷、阅片无数，最高一级是观影宗师。
 */

/** 一级称号：第几级、叫什么、看过几部升到这一级、纸片上的那句话。 */
@Immutable
data class Rank(val level: Int, val title: String, val at: Int, val line: String)

object Ranks {
    val ALL: List<Rank> = listOf(
        Rank(1, "入场观众", 1, "检过票、坐下了，第一部看完。"),
        Rank(2, "爆米花党", 5, "爆米花还没见底，又看完一部。"),
        Rank(3, "周末影迷", 15, "周末不出门，片单排得满满的。"),
        Rank(4, "夜场常客", 30, "最后一场，总有你的位置。"),
        Rank(5, "追片达人", 60, "新片一到，你总是第一个看。"),
        Rank(6, "资深影迷", 120, "片单已经比谁都长了。"),
        Rank(7, "阅片无数", 200, "开头三分钟，就猜得到结局。"),
        Rank(8, "片库行家", 350, "哪个导演拍过什么，张口就来。"),
        Rank(9, "骨灰影迷", 600, "冷门片也翻得出来，还看得津津有味。"),
        Rank(10, "观影宗师", 1000, "一千部，放映机都认得你了。"),
    )

    val MAX: Int get() = ALL.size

    /** 怎么数，印在纸片底下。 */
    const val RULE = "电影一部算一部；剧集一季算一部（看完过其中一集就算）。"

    /** 看过 [count] 部 → 第几级（0 = 还没有称号）。 */
    fun levelOf(count: Int): Int = ALL.count { count >= it.at }

    fun of(level: Int): Rank? = ALL.getOrNull(level - 1)

    /** 下一级（满级 = null）。 */
    fun next(count: Int): Rank? = ALL.firstOrNull { count < it.at }

    /** 从这一级到下一级走了多少（0–1）；满级 = 1。 */
    fun progress(count: Int): Float {
        val next = next(count) ?: return 1f
        val from = of(levelOf(count))?.at ?: 0
        return ((count - from).toFloat() / (next.at - from)).coerceIn(0f, 1f)
    }

    /** 「再看 60 部升「阅片无数」」/「称号到顶了」。 */
    fun nextLine(count: Int): String = next(count)?.let { "再看 ${it.at - count} 部升「${it.title}」" } ?: "称号到顶了"

    /** 读者证上那一行：「第 6 级 · 资深影迷」/「还没有称号」。 */
    fun badge(count: Int): String = of(levelOf(count))?.let { "第 ${it.level} 级 · ${it.title}" } ?: "还没有称号"
}

/** 看过的：电影几部 + 剧集几季。[total] = 称号按它算。 */
data class Watched(val movies: Int, val seasons: Int) {
    val total: Int get() = movies + seasons
}

/** 单集最多数这么多条（按季去重；再多的老用户只看最近这么多集，季数只会少算一点）。 */
const val EPISODE_SCAN = 20_000

/** 看过的单集 → 几季：按季 id 去重（没有季 id 的按剧 + 季号，再没有的一集算一部）。纯函数，有单测。 */
fun seasonsOf(episodes: List<BaseItemDto>): Int =
    episodes.mapTo(HashSet()) { e ->
        e.seasonId ?: e.parentId ?: e.seriesId?.let { "$it#${e.parentIndexNumber ?: 1}" } ?: e.id
    }.size

/** 只读：看完的电影（只要总数）+ 看完过的单集（只要季 id，不要图、不要用户数据）。 */
suspend fun countWatched(client: EmbyClient): Watched {
    val light = ItemsRequestOptions(fields = listOf("ParentId"), enableImages = false, enableUserData = false)
    val movies = client.getItems(LibraryQuery(includeItemTypes = listOf("Movie"), recursive = true, status = listOf("IsPlayed"), limit = 1), light).totalRecordCount
    val episodes = client.getItems(LibraryQuery(includeItemTypes = listOf("Episode"), recursive = true, status = listOf("IsPlayed"), limit = EPISODE_SCAN), light).items
    return Watched(movies, seasonsOf(episodes))
}

/**
 * 这一次要不要出称号的纸片：[announced] = 这个账号上次报到第几级（null = 这个功能装上以后还没报过）；[level] = 现在第几级。
 * 返回要报的那一级；null = 不出。第一次：已经有称号就报一次现在的（让人知道读者证上有了这个）；之后只在升级时出，一下跨好几级只报最高的。
 * 退回去（在服务器上把看过的改回没看）不出，也不往回记。纯函数，有单测。
 */
fun rankNews(announced: Int?, level: Int): Int? = when {
    level <= 0 -> null
    announced == null -> level
    level > announced -> level
    else -> null
}

/** 要出的那张纸片：第几级、看过几部、[first] = 第一次报（不是升级）、[browse] = 用户自己点开看的（读者证上那一行）。 */
@Immutable
data class RankNews(val level: Int, val count: Int, val first: Boolean = false, val browse: Boolean = false)

/**
 * 每个账号报到第几级，存在画报自己的键 `gugaart.ranks.v1`（`{"<服务器 id>/<用户 id>": 6}`；只是本机的记号，换设备会再报一次）。
 * 不碰上游的任何键。
 */
class RankStore(private val local: LocalStore) {
    fun announced(account: String): Int? = (GugaPrefs.parseObject(local.get(KEY))?.get(account) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    /** 记下报到了第几级（只往上记）。 */
    fun mark(account: String, level: Int) {
        val raw = local.get(KEY)
        val old = GugaPrefs.parseObject(raw)
        val keep = maxOf(level, announced(account) ?: 0)
        local.set(KEY, buildJsonObject {
            old?.forEach { (k, v) -> if (k != account) put(k, v) }
            put(account, keep)
        }.toString())
    }

    /** 上次数出来的部数（报头一打开就有，不等这一次数完；也用来算「+N 部」）。存在 `gugaart.rankcount.v1`。 */
    fun lastCount(account: String): Int? = (GugaPrefs.parseObject(local.get(COUNT_KEY))?.get(account) as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    fun setLastCount(account: String, count: Int) {
        val old = GugaPrefs.parseObject(local.get(COUNT_KEY))
        local.set(COUNT_KEY, buildJsonObject {
            old?.forEach { (k, v) -> if (k != account) put(k, v) }
            put(account, count)
        }.toString())
    }

    companion object {
        const val KEY = "gugaart.ranks.v1"
        const val COUNT_KEY = "gugaart.rankcount.v1"

        fun accountOf(serverId: String, userId: String): String = "$serverId/$userId"
    }
}

/**
 * 根上那张称号纸片现在出不出、出哪一张（自动检查和读者证上点开都放在这里）；还有报头上那枚称号用的部数和「+N 部」。
 */
object RankBoard {
    var news: RankNews? by mutableStateOf(null)

    /** 特别称号的纸片（等级纸片收起后再出）。 */
    var special: SpecialNews? by mutableStateOf(null)

    /** 当前账号拿到了哪些特别称号（「我的」里的盾亮哪几枚）。 */
    var owned: Set<String> by mutableStateOf(emptySet())

    /** 当前账号看过几部（null = 还没数出来；报头那一格这时画日期）。 */
    var count: Int? by mutableStateOf(null)

    /**
     * 报头展示的那个称号（[rankKey] / [specialKey]；null = 当前等级）：每次打开应用从最近拿到的三个里随机挑一个
     * （RecentTitles.kt），这一次运行里又拿到新的就换成它。[featuredFor] = 挑的时候是哪个账号（从播放器回来不重挑）。
     */
    var featured: String? by mutableStateOf(null)
    var featuredFor: String? = null

    /** 上一次涨了几部（报头浮出「+N 部」）；[gainToken] 每涨一次 +1。 */
    var gained: Int by mutableStateOf(0)
    var gainToken: Int by mutableStateOf(0)

    /** 数出了新的部数：比 [previous]（上次记下的）多就记一次「涨了」。 */
    fun counted(total: Int, previous: Int?) {
        if (previous != null && total > previous) {
            gained = total - previous
            gainToken++
        }
        count = total
    }

    fun open(n: RankNews) {
        news = n
    }

    fun close() {
        news = null
    }
}
