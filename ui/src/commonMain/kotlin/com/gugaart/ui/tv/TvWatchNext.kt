package com.gugaart.ui.tv

import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Title

/**
 * 安卓电视系统首页的「继续观看」（Watch Next，1.6 B4）里的一条：只放公开的东西（片名、集号、图的地址去掉令牌、进度）。
 * [key] = `服务器 id|用户 id|条目 id`（写进系统的 `INTERNAL_PROVIDER_ID`，点开时靠它认出是哪台服务器的哪一条）。
 */
data class WatchNextEntry(
    val key: String,
    val itemId: String,
    val episode: Boolean,
    /** 电影 = 片名；单集 = 剧名。 */
    val title: String,
    /** 单集的标题（「第 5 集」这类没意义的也照写，系统首页自己决定显示不显示）；电影 = null。 */
    val episodeTitle: String?,
    val season: Int?,
    val episodeNo: Int?,
    val image: String?,
    /** true = 16:9 横图（剧照 / 分集图）；false = 2:3 海报。 */
    val wide: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    /** 最近看的排前面：系统按这个时间排序。 */
    val engagedAtMs: Long,
)

/**
 * 壳给的同步（只有安卓电视壳设）：把「这台服务器这个用户」的继续观看写成系统首页的那几条——不在里面的（看完的、别的账号的）删掉，
 * 用户在系统首页里自己移除过的不再加回去。没有 TvProvider 的电视（比如海信 VIDAA）壳自己什么都不做。
 */
object TvWatchNext {
    const val MAX = 10
    var host: ((entries: List<WatchNextEntry>) -> Unit)? = null

    private var lastSent: List<WatchNextEntry>? = null

    /** 首页拿到「继续观看」就调一次；和上次一样（除了时间）不重复写。 */
    fun sync(account: String, resume: List<Title>, nowMs: Long) {
        val h = host ?: return
        val entries = watchNextEntries(account, resume, nowMs)
        val prev = lastSent
        if (prev != null && prev.map { it.copy(engagedAtMs = 0) } == entries.map { it.copy(engagedAtMs = 0) }) return
        lastSent = entries
        h(entries)
    }

    /** 测试用。 */
    fun reset() {
        lastSent = null
    }

    /** 系统首页点开一条：intent 里带的 [WatchNextEntry.key] → (账号 `服务器 id|用户 id`, 条目 id)；认不出 = null。 */
    fun parseKey(key: String?): Pair<String, String>? {
        val parts = key?.split('|') ?: return null
        if (parts.size != 3 || parts.any { it.isBlank() }) return null
        return "${parts[0]}|${parts[1]}" to parts[2]
    }
}

/** 图的地址里不带令牌（系统首页是别的应用，地址会存在系统的数据库里）。 */
fun withoutToken(url: String?): String? {
    if (url == null) return null
    val q = url.indexOf('?')
    if (q < 0) return url
    val kept = url.substring(q + 1).split('&').filter { p ->
        val k = p.substringBefore('=').lowercase()
        k.isNotEmpty() && k != "api_key" && k != "x-emby-token"
    }
    return url.substring(0, q) + if (kept.isEmpty()) "" else "?" + kept.joinToString("&")
}

/**
 * 首页的「继续观看」→ 系统首页的条目：前 [TvWatchNext.MAX] 条、只要电影和单集、看完的不要；排在前面的「最近看过」时间最近。
 * [account] = `服务器 id|用户 id`。纯函数，有单测。
 */
fun watchNextEntries(account: String, resume: List<Title>, nowMs: Long): List<WatchNextEntry> =
    resume.asSequence()
        .filter { (it.kind == Kind.Movie || it.kind == Kind.Episode) && !it.played }
        .distinctBy { it.id }
        .take(TvWatchNext.MAX)
        .mapIndexed { i, t ->
            val ep = t.kind == Kind.Episode
            val wide = t.art.thumb ?: t.art.backdrop
            WatchNextEntry(
                key = "$account|${t.id}",
                itemId = t.id,
                episode = ep,
                title = if (ep) t.seriesName?.takeIf { it.isNotBlank() } ?: t.name else t.name,
                episodeTitle = if (ep) t.name else null,
                season = if (ep) t.seasonNo else null,
                episodeNo = if (ep) t.episodeNo else null,
                image = withoutToken(wide ?: t.art.poster),
                wide = wide != null,
                positionMs = (t.positionSec ?: 0L) * 1000L,
                durationMs = t.runtimeSec?.let { it * 1000L },
                engagedAtMs = nowMs - i * 1000L,
            )
        }
        .toList()
