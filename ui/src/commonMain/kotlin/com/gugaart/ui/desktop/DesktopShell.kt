package com.gugaart.ui.desktop

import com.gugaart.ui.components.rememberPageStates
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gugaart.ui.Today
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/**
 * 电脑端（`UiMode.Desktop`）的版面骨架（设计稿 `fable-b1/desktop`，规则 `docs/platforms.md` §2）。
 *
 * 竖向节奏（rem = 窗口宽 / 144，夹在 9.4–13；1440 宽时 1 rem = 10 dp）：报头（= 窗口标题栏）高 4.4 rem；玻璃排在报头下 1.2 rem、高 4.4 rem；
 * 普通页面的内容从 13.2 rem 起。整幅铺满的页面从窗口顶端铺起，报头和玻璃排直接压在图上。
 */
object DesktopSpec {
    /** 报头上边留白（上实线离窗口顶）。 */
    const val MASTHEAD_TOP = 0.6f

    /** 报头两条线之间的高（连上边留白一共 4.4 rem）。 */
    const val MASTHEAD = 3.8f
    const val NAV_GAP = 1.2f
    const val NAV = 4.4f
    /** 普通页面内容的起点。 */
    const val CONTENT_TOP = 13.2f
    /** 一个窗口键的宽、高（窗口键贴着窗口右上角）。 */
    const val WINDOW_KEY = 4.6f
    const val WINDOW_KEY_H = 3.4f

    /** 报头的右端离窗口右缘（三个窗口键 13.8 + 1.6）。 */
    const val MASTHEAD_END = 15.4f

    /** 点 `VOL.` 的热区宽。 */
    const val VOL_HIT = 9f
}

/** 电脑外壳自己的界面状态：「付印新刊」了几次（报头的期号跟着滚上去）。 */
@Stable
class DesktopShellState {
    var reprints: Int by mutableIntStateOf(0)
}

val LocalDesktopShell = staticCompositionLocalOf { DesktopShellState() }

/**
 * 电脑端的页面层（进 `GlassScene` 的 `backdropContent`：玻璃排折射的就是它）。按上游路由分到各界面，再压上报头。
 * 导航页（首页 / 发现 / 片库 / 合集 / 我的）是 `Route.Home` 之下的本地状态（[com.gugaart.ui.wide.WideNav]）。
 */
@Composable
fun DesktopPages(route: Route, today: Today) {
    val nav = LocalWideNav.current
    val shell = remember { DesktopShellState() }
    DisposableEffect(Unit) { onDispose { DesktopFocus.pages = false } }
    // 正用着的时候读者证过期（401）：记下来，登录页先说清楚（设计稿 `errors` 的 `auth`）
    com.gugaart.ui.wide.WatchSessionExpiry()
    // 返回栈上的每一页记住自己的滚动位置：片库进详情再返回，回到进去的那一屏（PageStates）
    val navController = com.gugugaga.app.ui.LocalGraph.current.nav
    val pages = rememberPageStates(navController)
    CompositionLocalProvider(LocalDesktopShell provides shell) {
        Box(Modifier.fillMaxSize().onFocusChanged { DesktopFocus.pages = it.hasFocus }.desktopKeys()) {
            pages.Page(navController, if (route == Route.Home) nav.tab.ordinal else 7) { when (route) {
                Route.Home -> when (nav.tab) {
                    WideTab.Home -> DesktopHomeScreen(today)
                    WideTab.Discover -> DesktopDiscoverScreen(today)
                    WideTab.Libs -> DesktopLibsScreen(today)
                    WideTab.Colls -> DesktopCollsScreen(today)
                    WideTab.Me -> DesktopMeScreen(today, null)
                }
                Route.Discover -> DesktopDiscoverScreen(today)
                is Route.Library -> DesktopLibraryScreen(route.id, today)
                is Route.Search -> DesktopSearchScreen(route.q, today)
                is Route.Item -> DesktopItemScreen(route.id, today)
                is Route.History -> DesktopMeScreen(today, route.tab)
                is Route.Settings -> DesktopSettingsScreen(route.section, today)
                Route.Servers, is Route.Login -> DesktopLoginScreen(today, chrome = true)
                else -> DesktopComing(today, "这一页")
            } }
            DesktopMasthead(today.copy(volume = today.volume + shell.reprints))
        }
    }
}

/**
 * 报头 = 窗口标题栏：可拖动、双击最大化（上游 `WindowControls.DragArea`），右端三个窗口键。不是玻璃。
 * 普通页面上它垫实心纸色（滚动内容从它下面硬切过去，不叠字）；整幅铺满的页面在滚过 [com.gugaart.ui.wide.WideNav.bleed] 之前是透明的，直接压在剧照顶部的暗角上。
 * `VOL.` 可点（F5 同效）：付印新刊 —— 回调由首页挂在 [com.gugaart.ui.wide.WideNav.onReprint] 上；没有页面挂着时点了不动。
 */
@Composable
fun DesktopMasthead(today: Today, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val nav = LocalWideNav.current
    val controls = LocalGraph.current.deps.windowControls
    val solid = !nav.bleed || nav.scrollPx > 2f
    val band: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Guga.rem(DesktopSpec.MASTHEAD_TOP + DesktopSpec.MASTHEAD))
                .drawBehind { if (solid) drawRect(colors.paper) }
                .testTag(if (solid) "desktop-masthead-solid" else "desktop-masthead"),
        ) {
            Box(Modifier.padding(start = Guga.dimens.margin, end = Guga.rem(DesktopSpec.MASTHEAD_END), top = Guga.rem(DesktopSpec.MASTHEAD_TOP)).fillMaxWidth().height(Guga.rem(DesktopSpec.MASTHEAD))) {
                Masthead(volume = "VOL. ${today.volume}", date = today.date, issue = today.volume, dateSlot = { com.gugaart.ui.screens.me.RankChip(today.date, it) })
                // VOL. 可点：付印新刊（热区盖在期号上，不改报头的排法）
                val press = rememberPressState()
                val reprint = nav.onReprint
                if (reprint != null) {
                    Box(Modifier.width(Guga.rem(DesktopSpec.VOL_HIT)).fillMaxHeight().deskFocusRing(press).pressable(press, label = "换一批片单", onClick = reprint).testTag("desktop-reprint"))
                }
            }
            WindowKeys(Modifier.align(Alignment.TopEnd))
        }
    }
    Box(modifier.fillMaxWidth()) {
        if (controls != null) controls.DragArea(Modifier.fillMaxWidth()) { band() } else band()
    }
}

/** 窗口键：最小化 / 最大化 / 关闭。悬停：浅底；关闭键红底（docs/platforms.md §2）。测试里没有窗口（`windowControls == null`）也照画，点了不动。 */
@Composable
fun WindowKeys(modifier: Modifier = Modifier) {
    val controls = LocalGraph.current.deps.windowControls
    Row(modifier.testTag("window-keys")) {
        WindowKey(0, "最小化") { controls?.minimize() }
        WindowKey(1, if (controls?.maximized == true) "还原" else "最大化") { controls?.toggleMaximize() }
        WindowKey(2, "关闭") { controls?.close() }
    }
}

/** 关闭键悬停的红底（Windows 的惯例色，不是专色也不是印章朱红）。 */
private val CLOSE_HOVER = Color(0xFFC42B1C)

@Composable
private fun WindowKey(kind: Int, label: String, onClick: () -> Unit) {
    val close = kind == 2
    val colors = Guga.colors
    val press = rememberPressState()
    var hover by remember { mutableStateOf(false) }
    Box(
        Modifier
            .width(Guga.rem(DesktopSpec.WINDOW_KEY))
            .height(Guga.rem(DesktopSpec.WINDOW_KEY_H))
            .background(if (!hover) Color.Transparent else if (close) CLOSE_HOVER else colors.ink.copy(alpha = 0.14f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Enter) hover = true
                        if (e.type == PointerEventType.Exit) hover = false
                    }
                }
            }
            .pressable(press, label = label, focusable = false, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 三个记号自己画（细线，和报头的细线同一个粗细）：一横 / 方框 / 叉
        Box(
            Modifier.width(Guga.rem(1f)).height(Guga.rem(1f)).drawBehind {
                val w = 1.dp.toPx()
                val ink = colors.ink
                when (kind) {
                    0 -> drawLine(ink, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), w)
                    1 -> drawRect(ink, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                    else -> {
                        drawLine(ink, Offset(0f, 0f), Offset(size.width, size.height), w)
                        drawLine(ink, Offset(size.width, 0f), Offset(0f, size.height), w)
                    }
                }
            },
        )
    }
}

/**
 * 一个电脑页面：整页一起滚（跨页是版式，不是两个独立滚动的区域）。
 * - [bleed] = false：内容从 13.2 rem 起（报头 + 玻璃排之下）。
 * - [bleed] = true：内容从窗口最顶端起（第一块是整幅剧照），报头在滚动前透明。
 * 滚动位置自动报给外壳（报头变实心）。页边由内容自己加（整幅图要出血到窗口边）。
 */
@Composable
fun DesktopPage(
    modifier: Modifier = Modifier,
    bleed: Boolean = false,
    scroll: ScrollState = rememberScrollState(LocalPreset.current.scroll),
    bottom: Dp = 64.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val nav = LocalWideNav.current
    DisposableEffect(bleed) {
        nav.bleed = bleed
        onDispose { nav.bleed = false }
    }
    LaunchedEffect(scroll.value) { nav.scrollPx = scroll.value.toFloat() }
    DisposableEffect(Unit) { onDispose { nav.scrollPx = 0f } }
    Column(modifier.fillMaxSize().verticalScroll(scroll).testTag("desktop-page")) {
        if (!bleed) Spacer(Modifier.height(Guga.rem(DesktopSpec.CONTENT_TOP)))
        content()
        Spacer(Modifier.height(bottom))
    }
}

/** 还没排的电脑页面：一句说明（不是空白墙）。 */
@Composable
fun DesktopComing(today: Today, name: String) {
    DesktopPage {
        com.gugaart.ui.components.StateView("state-coming", "摆稿纸", "这一场还在筹备", "「$name」敬请期待。")
    }
}
