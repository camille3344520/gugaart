package com.gugaart.ui.wide

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.gugugaga.app.nav.Route
import com.gugugaga.app.session.SessionEvent
import com.gugugaga.app.ui.LocalGraph

/**
 * 电脑 / 电视的导航页（电脑 = 报头下的玻璃导航胶囊，电视 = 左侧玻璃侧栏）。
 *
 * 「片库」（媒体库列表）、「合集」、「我的」在上游没有路由（`docs/screens.md` 的 ✚）：和手机的标签页一样，是 `Route.Home` 之下的本地状态，
 * 不进返回栈。「发现」有上游路由（`Route.Discover`），但作为导航页时也走这里，保证「返回 = 回首页」的层级一致。
 */
enum class WideTab(val id: String, val label: String) {
    Home("home", "首页"),
    Discover("discover", "发现"),
    Libs("libs", "片库"),
    Me("me", "我的"),
    ;

    companion object {
        fun of(id: String?): WideTab = entries.firstOrNull { it.id == id } ?: Home
    }
}

/**
 * 电脑 / 电视外壳的界面状态：现在在哪个导航页、页面滚了多少（报头据此变实心）、这一页是不是整幅铺满（报头透明压图）。
 * 只是界面状态，不进上游的设置，也不进返回栈。
 */
@Stable
class WideNav(start: WideTab = WideTab.Home) {
    var tab: WideTab by mutableStateOf(start)

    /** 当前页面的滚动位置（px）。页面用 [com.gugaart.ui.desktop.DesktopPage] 时自动上报。 */
    var scrollPx: Float by mutableFloatStateOf(0f)

    /** 当前页面顶部是整幅图（报头、玻璃排直接压在图上，滚动前不垫纸色）。 */
    var bleed: Boolean by mutableStateOf(false)

    /** 「付印新刊」（刷新）：电脑点报头的 `VOL.` / F5，电视在 `VOL.` 上按确定。首页在的时候把自己的刷新挂在这里，离开时摘掉。 */
    var onReprint: (() -> Unit)? by mutableStateOf(null)

    /** 电视：侧栏是否展开（焦点进了侧栏）。 */
    var railOpen: Boolean by mutableStateOf(false)

    /**
     * 刚才是被服务器请出来的：正用着的时候某个请求得到 401，上游把会话收掉了（`SessionEvent.Expired`），根界面换成登录页。
     * 登录页据此先说清楚「读者证过期了」（设计稿 `errors` 的 `auth`），不是没头没脑地回到服务器列表。重新登录 / 换了账号之后清掉。
     * 由页面层在有会话的时候盯着（[WatchSessionExpiry]）。
     */
    var sessionExpired: Boolean by mutableStateOf(false)

    /**
     * 电视：当前整幅图的「整屏小图」（已经压暗、铺在它在屏幕上的位置，其余是纸色）。ControlledGl 后端（电视 API 28–32）只折射
     * `GlassScene` 的 `backdrop` 那张图，看不见页面内容：根把这张图交给场景，侧栏折射的就是它（docs/glass.md §4、decisions Q12）。
     * 整幅铺满的电视页面往里放（`tv/TvKit.kt` 的 `TvBleedBackdrop`），离开时清掉；null = 纸色。只在 `UiMode.Tv` 用。
     */
    var bleedBackdrop: androidx.compose.ui.graphics.ImageBitmap? by mutableStateOf(null)

    /** 各端外壳自己的状态对象（电视：焦点 / 返回 / 菜单键，`tv/TvShell.kt` 的 `TvShellState`）；外壳第一次用到时放进来。 */
    var shell: Any? = null

    /** 导航项对应到哪：在 Home 路由上换导航页；不在 Home 时先回 Home。 */
    fun activeTab(route: Route): WideTab? = when (route) {
        Route.Home -> tab
        Route.Discover -> WideTab.Discover
        is Route.History -> WideTab.Me
        else -> null
    }
}

val LocalWideNav = staticCompositionLocalOf { WideNav() }

/**
 * 有会话的时候盯着上游的会话通知：读者证过期（401）→ 记在 [WideNav.sessionExpired] 上；会话在，就不是过期。
 * 电脑 / 电视的页面层各放一个（登录页在会话没了之后才出现，来不及自己听这一声）。
 */
@Composable
fun WatchSessionExpiry() {
    val graph = LocalGraph.current
    val nav = LocalWideNav.current
    LaunchedEffect(graph, nav) {
        nav.sessionExpired = false
        graph.session.events.collect { if (it is SessionEvent.Expired) nav.sessionExpired = true }
    }
}
