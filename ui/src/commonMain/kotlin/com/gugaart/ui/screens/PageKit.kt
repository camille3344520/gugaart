package com.gugaart.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.chrome.PhoneTabBarSpec
import com.gugaart.ui.chrome.tabBarSunk
import com.gugaart.ui.components.BoxButton
import com.gugaart.ui.data.Title
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaMotion
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.ds.icons.GuguIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 页面和外壳之间的几样共享状态（都是界面状态，不进上游的设置）：
 * - 专色：页面把「这一期 / 这部片」的专色交上来，根界面交给 `GugaTheme(spot)` 去渐变；
 * - 标签栏下沉：标签页把自己的滚动位置报上来；
 * - 收藏 / 已看的本地改动：点了之后先改这里（界面立刻变），再写服务器；别的页面看到的是同一份。
 */
@Stable
class PageChrome {
    /** 当前页面的专色；null = 兜底朱红。 */
    var spot: Color? by mutableStateOf(null)

    /** 标签栏沉没沉。 */
    var tabBarSunk: Boolean by mutableStateOf(false)
        private set
    private var anchor = 0f

    fun reportScroll(scroll: Float, max: Float, returnPx: Float) {
        val (sunk, a) = tabBarSunk(tabBarSunk, anchor, scroll, max, returnPx)
        tabBarSunk = sunk
        anchor = a
    }

    fun resetScroll() {
        tabBarSunk = false
        anchor = 0f
    }

    /** 点状态栏回顶（iOS，D-lead-27）：每点一次加一；正露着的那一页的主滚动区看到它变就滚回 0（[ScrollsToTop]）。 */
    var scrollTopTick: Int by mutableStateOf(0)
        private set

    fun requestScrollTop() {
        scrollTopTick++
    }

    /** 条目 id → 收藏 / 已看（本地改过的才在里面）。 */
    val favorites = mutableStateMapOf<String, Boolean>()
    val played = mutableStateMapOf<String, Boolean>()

    fun isFavorite(t: Title): Boolean = favorites[t.id] ?: t.favorite
    fun isFavorite(id: String, fallback: Boolean): Boolean = favorites[id] ?: fallback
    fun isPlayed(id: String, fallback: Boolean): Boolean = played[id] ?: fallback
}

val LocalChrome = staticCompositionLocalOf { PageChrome() }

/** 把标签页的滚动报给外壳（标签栏下沉）。 */
@Composable
fun ReportScroll(scroll: ScrollState) {
    val chrome = LocalChrome.current
    val returnPx = with(LocalDensity.current) { PhoneTabBarSpec.RETURN_DP.toPx() }
    // 滚动位置在协程里跟（1.5.1 性能）：以前把它当 LaunchedEffect 的键 = 每滚一帧这里重组一次、拆一个协程再起一个
    LaunchedEffect(scroll, chrome, returnPx) {
        androidx.compose.runtime.snapshotFlow { scroll.value to scroll.maxValue }.collect { (v, max) -> chrome.reportScroll(v.toFloat(), max.toFloat(), returnPx) }
    }
    ScrollsToTop { scroll.animateScrollTo(0) }
}

/**
 * 这一页的主滚动区认「点状态栏」（D-lead-27）：壳报上来一次（[PageChrome.requestScrollTop]），正露着的这一页就滚回顶。
 * 只认这一页露面**之后**的点（返回到一页时不会因为以前点过而被滚回顶，`PageStates` 留着的位置照旧）；被盖住的页（`LocalPageShown` = false）不动。
 * 安卓没有这回事：壳不给 [com.gugaart.ui.theme.PhoneHost.statusBarTaps]，计数永远不变。
 */
@Composable
fun ScrollsToTop(toTop: suspend () -> Unit) {
    val chrome = LocalChrome.current
    val shown = com.gugaart.ui.idle.LocalPageShown.current
    val since = androidx.compose.runtime.remember { chrome.scrollTopTick }
    val tick = chrome.scrollTopTick
    LaunchedEffect(tick) { if (tick != since && shown) toTop() }
}

/**
 * 收藏 = 盖章、标记已看 = 校对勾：这两个动作会**写服务器**（上游 `EmbyClient.setFavorite` / `setPlayed`）。是用户操作；
 * 自动化测试只对夹具 / Mock 做（AGENTS.md §2.1）。先改本地（章立刻落下），写失败再改回去。
 */
fun AppGraph.toggleFavorite(chrome: PageChrome, id: String, now: Boolean, onDone: (Boolean) -> Unit = {}) {
    val next = !now
    chrome.favorites[id] = next
    // 1.6 A1：在别台的详情页 / 播放器里 = 写给那一台（条目 id 是那台的）
    val client = com.gugaart.ui.data.RemoteBrowse.contentClient(this) ?: return
    scope.launch {
        try {
            client.setFavorite(id, next)
            onDone(next)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            chrome.favorites[id] = now
        }
    }
}

fun AppGraph.togglePlayed(chrome: PageChrome, id: String, now: Boolean, onDone: (Boolean) -> Unit = {}) {
    val next = !now
    chrome.played[id] = next
    val client = com.gugaart.ui.data.RemoteBrowse.contentClient(this) ?: return
    scope.launch {
        try {
            client.setPlayed(id, next)
            onDone(next)
            // Trakt 连着：同一条也加进 / 移出 Trakt 的观看记录
            com.gugaart.ui.trakt.TraktService.markFromEmby(client, id, next)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            chrome.played[id] = now
        }
    }
}

/** 打开一个条目：单集 / 电影 / 剧集 / 人物都是 `Item(id)`（路由模型是上游的）。 */
fun AppGraph.open(id: String) = nav.navigate(Route.Item(com.gugaart.ui.data.RemoteBrowse.wrap(nav, id)))

/** 播放：交给上游的 `PlaybackLauncher`（默认 = 进播放路由）。在别台的详情页里 = 那台的条目（1.6 A1，data/RemoteBrowse.kt）。 */
fun AppGraph.play(id: String, startMs: Long? = null) = playback.play(Route.Play(com.gugaart.ui.data.RemoteBrowse.wrap(nav, id), startMs = startMs))

/**
 * 这一页滚过 [px] 了没有（顶栏 / 状态栏变实心用）。派生状态（1.5.1 性能）：调用的那一页只在「过了 / 没过」翻转时重组；
 * 以前各页直接写 `scroll.value > x`，滚动的每一帧整页（详情页、发现、我的……）都重组一遍。
 */
@Composable
fun scrolledPast(scroll: androidx.compose.foundation.ScrollState, px: Float): Boolean {
    val past = androidx.compose.runtime.remember(scroll, px) { androidx.compose.runtime.derivedStateOf { scroll.value > px } }
    return past.value
}

/** 手机顶上的状态栏占位。滚动后（[solid]）垫实心纸色，内容从它下面硬切过去。 */
@Composable
fun StatusBand(solid: Boolean, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val a by animateFloatAsState(if (solid) 1f else 0f, tween(GugaMotion.BAND_SOLID_MS), label = "band")
    Box(modifier.fillMaxWidth().height(Guga.dimens.statusBar).graphicsLayer { alpha = a }.background(colors.paper))
}

/**
 * 推入页的顶栏（手机）：状态栏 46 + 返回行 50。**返回永远在左上，选项在右**。压在整幅图上时是透明的（按钮自带 52 % 墨底）；
 * 滚动内容的第一行字到达顶栏下沿之前变成实心纸色并出现页名（[solid]），250 ms。
 */
@Composable
fun BackRow(title: String, solid: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier, favorite: Boolean? = null, onFavorite: (() -> Unit)? = null, onMore: (() -> Unit)? = null) {
    val colors = Guga.colors
    val a by animateFloatAsState(if (solid) 1f else 0f, tween(GugaMotion.BAND_SOLID_MS), label = "backrow")
    Box(
        modifier.fillMaxWidth().drawBehind {
            drawRect(colors.paper.copy(alpha = a))
            if (a > 0f) drawLine(colors.line.copy(alpha = colors.line.alpha * a), Offset(Guga_MARGIN_PX(this), size.height), Offset(size.width - Guga_MARGIN_PX(this), size.height), 1.dp.toPx())
        }.testTag(if (solid) "back-row-solid" else "back-row"),
    ) {
        Row(Modifier.padding(top = Guga.dimens.statusBar).fillMaxWidth().height(50.dp).padding(horizontal = Guga.dimens.margin - 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxButton(GuguIcon.ChevronLeft, "返回", onBack, onImage = !solid)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BasicText(
                    title,
                    Modifier.graphicsLayer { alpha = a },
                    style = Guga.song(Guga.type.row, FontWeight.Black, letterSpacing = 0.2.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onFavorite != null) BoxButton(GuguIcon.Heart, if (favorite == true) "取消收藏" else "收藏", onFavorite, onImage = !solid, selected = favorite == true)
            if (onMore != null) BoxButton(GuguIcon.Ellipsis, "更多", onMore, onImage = !solid)
        }
    }
}

@Suppress("FunctionName")
private fun Guga_MARGIN_PX(scope: androidx.compose.ui.graphics.drawscope.DrawScope): Float = with(scope) { 24.dp.toPx() }

/**
 * 测试 / 截图的预置状态（离屏渲染不能点、不能滚）：让一个界面一开始就处在某个状态。应用里永远是默认值。
 * 只有「界面状态」能预置（滚到哪、哪一块推开着、选了哪一季）；数据一律走真实的代码路径。
 */
data class ScreenPreset(
    /** 首页：海报流一开始在第几期（0 起）。 */
    val homeIssue: Int = 0,
    /** 页面一开始滚到哪（px）。 */
    val scroll: Int = 0,
    /** 片库：推开着哪一块（filter / sort）；选了哪个库；预先选中的题材 / 年代。 */
    val wallPanel: String? = null,
    val wallLibrary: String? = null,
    val wallGenres: Set<String> = emptySet(),
    val wallDecades: Set<String> = emptySet(),
    /** 剧集详情：一开始选哪一季（季号）、第几页分集、卷册是否推开。 */
    val season: Int? = null,
    val episodePage: Int = 0,
    val volumesOpen: Boolean = false,
    /** 片名不加载 logo（看宋体片名的兜底）。 */
    val textTitles: Boolean = false,
    /** 搜索：一开始的查询词。 */
    val query: String? = null,
    /** 播放器：控件是否显示、打开哪个面板（eps / sub / audio / speed / quality / dm）。 */
    val playerControls: Boolean = true,
    /** 播放器：控件钉住不自动隐去（截「控件显示」这一屏时用）。 */
    val playerPin: Boolean = false,
    val playerPanel: String? = null,
    /** 播放器：一开始就锁着 / 一开始就显示缓冲（截这两屏时用）。 */
    val playerLocked: Boolean = false,
    val playerBuffer: Boolean = false,
    /** 电脑 / 电视：这一屏的状态名（= 设计稿 `st=` 的值，如 "toc" "filter" "eps" "menu"）；各界面自己解释。 */
    val state: String? = null,
    /** 电脑 / 电视：一开始落在哪个导航页（home / discover / libs / colls / me）。 */
    val wideTab: String? = null,
    /** 电视 / 电脑键盘：一开始把焦点放到哪个 focusKey 上（null = 这一页的默认焦点）。 */
    val focus: String? = null,
)

val LocalPreset = staticCompositionLocalOf { ScreenPreset() }
