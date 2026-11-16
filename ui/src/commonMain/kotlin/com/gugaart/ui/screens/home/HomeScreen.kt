package com.gugaart.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.Masthead
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.LibraryEntry
import com.gugaart.ui.data.Load
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.data.loadLibraries
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.theme.Guga
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.ds.theme.UiMode
import kotlin.math.roundToInt

/** 首页封面最多排几期。 */
const val COVER_ISSUES = 8

/** 目录先排几行，其余在「翻开全部 N 条观看记录」里。 */
const val TOC_ROWS = 6

/** 一个媒体库的「新刊」一带。 */
@Immutable
data class NewArrivals(val library: LibraryEntry, val titles: List<Title>)

/** 首页要的全部数据（一次取齐，三端的版式共用）。 */
@Immutable
data class HomeData(
    /** 封面的各期：继续观看在前，不够 [COVER_ISSUES] 用最近添加补。 */
    val covers: List<Title>,
    /** 目录 = 继续观看（真实的顺序）。 */
    val resume: List<Title>,
    val resumeTotal: Int,
    val arrivals: List<NewArrivals>,
    val libraries: List<LibraryEntry>,
    /** 目录里按类排的栏：电视剧 / 电影 / 动漫 / 综艺（空的不在里面；HomeShelves.kt）。 */
    val shelves: List<HomeShelf> = homeShelves(arrivals),
)

private val HOME_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Overview", "Genres", "Taglines", "CommunityRating"))

/** 真实的代码路径：上游 :core 的 `getResume` / `getViewList` / `getLatest`（全部只读）。 */
suspend fun loadHome(client: EmbyClient): HomeData {
    val resume = client.getResume(limit = 12, o = HOME_FIELDS)
    val libraries = loadLibraries(client)
    val arrivals = libraries.filter { it.kind == "movies" || it.kind == "tvshows" || it.kind == null }.map { lib ->
        NewArrivals(lib, client.getLatest(parentId = lib.id, limit = 16, o = HOME_FIELDS).map { client.titleOf(it) })
    }.filter { it.titles.isNotEmpty() }
    val watching = resume.items.map { client.titleOf(it) }
    // 封面换一波时多抽一把：整个片库里随机的 24 部（只读；失败了就只用最近添加的）。离屏测试里牌堆不随机，也不发这一条
    val drawn = if (!CoverDeck.live) emptyList() else try {
        client.getItems(com.gugugaga.core.LibraryQuery(includeItemTypes = listOf("Movie", "Series"), recursive = true, sortBy = listOf("Random"), limit = 24), HOME_FIELDS)
            .items.map { client.titleOf(it) }.filter { it.art.poster != null }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    val dealt = CoverDeck.next(watching, arrivals.flatMap { it.titles } + drawn, from = client)
    return HomeData(
        // 每期的剧照：从这部作品的剧照里随机挑最高清的一档（离屏测试里不随机）
        covers = if (CoverDeck.live) CoverBackdrops.apply(client, dealt, fresh = CoverDeck.lastWasNew) else dealt,
        resume = watching,
        resumeTotal = maxOf(resume.totalRecordCount, watching.size),
        arrivals = arrivals,
        libraries = libraries,
    )
}

/** 封面排哪几期：继续观看在前（同一部剧只上一期），不够用最近添加的补，最多 [limit] 期。纯函数，有单测。 */
fun coverIssues(resume: List<Title>, latest: List<Title>, limit: Int = COVER_ISSUES): List<Title> {
    val out = ArrayList<Title>()
    val seen = HashSet<String>()
    for (t in resume + latest) {
        val key = t.seriesId ?: t.seriesName ?: t.id
        if (!seen.add(key)) continue
        out += t
        if (out.size >= limit) break
    }
    return out
}

/** 封面主按钮上的字：「续看 第 5 集」「播放 第 11 集」「续看」「播放」。 */
fun coverAction(t: Title): String {
    val verb = if (t.progress > 0f) "续看" else "播放"
    return if (t.kind == Kind.Episode && t.episodeNo != null) "$verb 第 ${t.episodeNo} 集" else verb
}

/** 目录行的副行：「第 5 集 · 余 19 分」「第 11 集 · 还没开始」「电影 · 余 78 分」。 */
fun tocSubtitle(t: Title, phone: Boolean): String {
    val head = when {
        t.kind == Kind.Episode && t.episodeNo != null -> "第 ${t.episodeNo} 集"
        t.kind == Kind.Movie -> "电影"
        else -> "剧集"
    }
    val tail = if (t.progress <= 0f) (if (phone) "还没开始" else "未开始") else Rules.remaining(t.progress, t.runtimeSec) ?: "${t.pct}%"
    return "$head · $tail"
}

/** 封面区的栏目行：剧 →「剧集 · 第 1 季 · 第 5 集 · 2026」；电影 → 题材 · 年份。 */
fun coverKicker(t: Title): List<String> = when (t.kind) {
    Kind.Episode -> Rules.kicker(listOf("剧集") + t.genres.take(2) + listOf(t.seasonNo?.let { "第 $it 季" }, t.year?.toString()))
    Kind.Movie -> t.kicker("电影")
    else -> t.kicker(null, t.count?.let { "全 $it 集" })
}

/** 主编手记在这一期说什么（没有合适的 → null，那一行只留署名照）。 */
fun coverNote(t: Title, hour: Int, day: Int): String? {
    val seed = t.id.hashCode() + day
    val facts = LineFacts(
        title = t.displayName,
        ep = t.episodeNo,
        remain = t.runtimeSec?.let { Rules.minutes((it * (1f - t.progress)).toLong()) },
        pct = t.pct,
        time = t.positionSec?.let { Rules.clock(it * 1000) },
    )
    val key = when {
        t.progress > 0f && t.kind == Kind.Episode -> "home.resume"
        t.progress > 0f -> "home.movie"
        t.kind == Kind.Episode -> "home.unstarted"
        else -> "home.fresh"
    }
    return MascotLines.pick(key, facts, hour, seed)
}

/**
 * 首页入口：取数和状态在这里，三端的版式函数只收参数、只画（ADR §4.1）。
 * 电脑 / 电视的版式在下一阶段（docs/impl-status.md）；现在这两端先落到同一个数据 + 最小的占位版式。
 */
@Composable
fun HomeScreen(volume: Int, date: String, day: Int) {
    val load: Load<HomeData> = rememberLoad("home") { loadHome(it) }
    remember { HomeProbe.entered++ }
    // 手机首页离开时不拆（KeptPage）：回来时版面原样立刻在，数据在后台静默刷新（看完一集回来，继续观看的进度要是新的）。
    // 旧内容一直留着，新数据到了再换（不闪）；封面还是这一期（CoverDeck 不发新的一波）。
    RefreshOnReturn(load)
    val data = load.value
    when {
        data != null -> when (Guga.uiMode) {
            UiMode.Phone -> HomePhone(data, volume, date, day, offline = load.error != null, onRefresh = { CoverDeck.requestNew(); load.retry() }, initialIssue = LocalPreset.current.homeIssue, initialScroll = LocalPreset.current.scroll)
            UiMode.Desktop, UiMode.Tv -> HomeWide(data, volume, date)
        }
        load.error != null -> ErrorView(load.error!!, load::retry)
        load.showSkeleton -> HomeLoading(volume, date)
        else -> HomeLoading(volume, date, quiet = true)
    }
}

@Composable
internal fun HomeLoading(volume: Int, date: String, quiet: Boolean = false) {
    Column(Modifier.padding(horizontal = Guga.dimens.margin)) {
        Spacer(Modifier.height(Guga.dimens.statusBar + if (Guga.uiMode == UiMode.Phone) 8.dp else 0.dp))
        Masthead(volume = "VOL. $volume", date = date)
        if (!quiet) LoadingView(seed = volume)
    }
}

/** 测试用：首页进了几次组合（手机首页留在组合里不拆，来回切换应该只进一次）。 */
/** 看完回来后第二次刷新的时间：服务器记下最后进度的请求是退出播放时才发的，第一遍取到的可能还是旧的。 */
const val RETURN_RECHECK_MS = 3_000L

/**
 * 首页被盖住（详情页、播放器）又露出来：静默刷新——马上一次，[RETURN_RECHECK_MS] 后再一次。
 * 用户 2026-11-15：「主界面的继续观看几率现在不会更新」：电视 / 电脑从首页「继续观看」进播放器，首页留在播放器底下，回来时以前不刷新；
 * 手机回来时刷新一次，但那一下常常早于服务器记下最后的进度。三端的首页都用它。
 */
@Composable
fun RefreshOnReturn(load: Load<*>) {
    val shown = com.gugaart.ui.idle.LocalPageShown.current
    var wasHidden by remember { mutableStateOf(false) }
    LaunchedEffect(shown) {
        if (!shown) {
            wasHidden = true
        } else if (wasHidden) {
            wasHidden = false
            load.retry()
            kotlinx.coroutines.delay(RETURN_RECHECK_MS)
            load.retry()
        }
    }
}

object HomeProbe {
    var entered = 0
}
