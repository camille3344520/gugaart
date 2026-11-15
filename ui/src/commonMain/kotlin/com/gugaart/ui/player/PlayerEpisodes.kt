package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.gugaart.ui.data.Season
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.toSeason
import com.gugaart.ui.screens.detail.EPISODE_PAGE
import com.gugaart.ui.screens.detail.pageOf
import com.gugaart.ui.data.EpisodePage
import com.gugaart.ui.screens.detail.EpisodeSlice
import com.gugaart.ui.screens.detail.loadEpisodes
import com.gugaart.ui.screens.detail.pagesOf
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.BaseItemDto
import kotlinx.coroutines.CancellationException

/*
 * 播放器里的选集 / 选季（用户 2026-11-15：「选集的部分没地方选集，这部分设计实现下」「选季不是集，可以拿火影忍者测试」）。
 *
 * 上游的播放器只在起播成功后取一次**整部剧**的分集（`getEpisodes(seriesId, limit = 2000)`），失败了就一直是空的，三端的「选集」入口跟着不见；
 * 也没有季：多季的剧（火影忍者、航海王）只能在一长串里找。这里改成和详情页同一种取法——**按季、按页**（`loadEpisodes`，详情页在用户的服务器上一直是好的），
 * 带剧照、进度、已看；季可以换，一季超过 50 集按集号分页。入口只看「这是不是一集」，不看上游那一串有没有取到。
 */

/** 播放器的分集来源：这部剧的季、选中的季、这一季的一页分集。 */
@Stable
class PlayerEpisodes(val seriesId: String, initialSeason: String?) {
    var seasons: List<Season> by mutableStateOf(emptyList())
    var seasonId: String? by mutableStateOf(initialSeason)
        private set
    var page: Int by mutableIntStateOf(0)
    var slice: EpisodeSlice? by mutableStateOf(null)
    var loading: Boolean by mutableStateOf(true)
    var failed: Boolean by mutableStateOf(false)

    /** 第一次取到这一季时翻到正在看的那一集所在的页（一季超过 50 集）；之后用户翻页不再跳回去。 */
    internal var jumped = false

    val episodes: List<Title> get() = slice?.episodes.orEmpty()
    val pages: List<EpisodePage> get() = pagesOf(slice)
    val season: Season? get() = seasons.firstOrNull { it.id == seasonId }

    /** 换一季：从第一页起。 */
    fun pickSeason(id: String) {
        if (id == seasonId) return
        seasonId = id
        page = 0
        slice = null
        jumped = true
    }

    fun pickPage(p: Int) {
        if (p == page) return
        page = p
        jumped = true
    }

    /** 播放器换到了另一集（自动下一集跨季、在面板里点了别的季的一集）：季和页跟过去。 */
    internal fun follow(item: BaseItemDto) {
        val s = item.seasonId
        if (s != null && s != seasonId) {
            seasonId = s
            page = 0
            slice = null
        }
        jumped = false
    }
}

/** 这一集（[item] 是剧集里的一集）的分集面板：季 / 页 / 分集。不是一集（电影、单个视频）= null。 */
val LocalPlayerEpisodes = staticCompositionLocalOf<PlayerEpisodes?> { null }

/** 这一集属于哪部剧：[item] 是一集且带剧 id → 剧 id；否则 null。纯函数，有单测。 */
fun seriesIdOf(item: BaseItemDto?): String? =
    item?.seriesId?.takeIf { item.type == "Episode" && it.isNotEmpty() }

/** 播放器里选中一集：交给队列，换路由（返回栈里只留一个播放器）。 */
fun AppGraph.playEpisode(id: String) {
    queue.nowPlaying(id)
    nav.replace(Route.Play(id))
}

@Composable
fun rememberPlayerEpisodes(snap: PlayerSnapshot): PlayerEpisodes? {
    val item = snap.item
    val seriesId = seriesIdOf(item) ?: return null
    val g = LocalGraph.current
    val client = g.session.session.collectAsState().value?.client ?: return null
    val eps = remember(seriesId) { PlayerEpisodes(seriesId, item?.seasonId) }
    LaunchedEffect(eps, client) {
        eps.seasons = try {
            client.getSeasons(seriesId).items.map { it.toSeason() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (eps.seasonId == null || eps.seasons.none { it.id == eps.seasonId }) eps.seasons.firstOrNull()?.let { eps.pickSeason(it.id) }
    }
    LaunchedEffect(snap.itemId) { item?.let { eps.follow(it) } }
    LaunchedEffect(eps, client, eps.seasonId, eps.page) {
        eps.loading = true
        try {
            val s = loadEpisodes(client, seriesId, eps.seasonId, eps.page)
            eps.slice = s
            eps.failed = false
            val no = item?.indexNumber
            val first = s.firstNumber
            if (!eps.jumped && s.episodes.none { it.id == snap.itemId } && s.total > EPISODE_PAGE && (pageOf(s, snap.itemId) != null || no != null && first != null)) {
                eps.jumped = true
                // 按集号表找正在看的那一集在第几页；没有表才按集号推算
                val p = pageOf(s, snap.itemId) ?: ((no!! - first!!) / EPISODE_PAGE).coerceAtLeast(0)
                if (p != eps.page) eps.page = p
            } else {
                eps.jumped = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            eps.failed = true
        } finally {
            eps.loading = false
        }
    }
    return eps
}

/**
 * 播放器里按返回该去哪（用户 2026-11-15：「最好是在观看界面连按两下返回再返回主页」）：
 * 下面一页是详情页（剧、电影、人物、合集）→ null，照常退回去；否则（从首页「继续观看」、记录页直接起播）→ 先到这部剧（电影）的详情页，
 * 再按一次返回才回到首页——从播放器回首页永远是两下。纯函数，有单测。
 */
fun playerBackTarget(routes: List<Route>, item: BaseItemDto?, routeItemId: String): Route.Item? {
    val below = routes.getOrNull(routes.size - 2)
    if (below is Route.Item) return null
    val id = seriesIdOf(item) ?: item?.id ?: routeItemId
    return Route.Item(id)
}
