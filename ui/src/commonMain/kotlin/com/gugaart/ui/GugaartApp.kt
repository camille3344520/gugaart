package com.gugaart.ui

import com.gugaart.ui.components.rememberPageStates
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.gugaart.ui.chrome.PhoneTab
import com.gugaart.ui.chrome.PhoneTabBar
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.StateView
import com.gugaart.ui.components.civilFromEpochDay
import com.gugaart.ui.components.cnMonthDayOf
import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.desktop.DesktopChrome
import com.gugaart.ui.desktop.DesktopContextMenu
import com.gugaart.ui.desktop.DesktopLoginScreen
import com.gugaart.ui.desktop.DesktopPages
import com.gugaart.ui.desktop.DesktopUpdateExtra
import com.gugaart.ui.tv.TvChrome
import com.gugaart.ui.tv.TvLoginScreen
import com.gugaart.ui.tv.TvLongPressMenu
import com.gugaart.ui.tv.TvPages
import com.gugaart.ui.tv.tvRoot
import com.gugaart.ui.tv.TvUpdateExtra
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideNav
import com.gugaart.ui.wide.WideTab
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.player.GugaPlayerScreen
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.PageChrome
import com.gugaart.ui.screens.ScreenPreset
import com.gugaart.ui.screens.detail.ItemScreen
import com.gugaart.ui.screens.discover.DiscoverScreen
import com.gugaart.ui.screens.favs.FavsScreen
import com.gugaart.ui.screens.history.HistoryScreen
import com.gugaart.ui.screens.settings.SettingsScreen
import com.gugaart.ui.screens.sets.LibraryScreen
import com.gugaart.ui.screens.home.ExitBar
import com.gugaart.ui.screens.home.HomeScreen
import com.gugaart.ui.screens.login.LoginScreen
import com.gugaart.ui.screens.me.MeScreen
import com.gugaart.ui.screens.search.SearchScreen
import com.gugaart.ui.screens.update.UpdateExtra
import com.gugaart.ui.screens.wall.WallScreen
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaColors
import com.gugaart.ui.theme.GugaFonts
import com.gugaart.ui.theme.GugaTheme
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.platform.localUtcOffsetMs
import com.gugugaga.app.settings.uiSettings
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.app.ui.LocalOptionalGraph
import com.gugugaga.app.ui.glassBackendFor
import com.gugugaga.ds.components.PageBackdrops
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.focus.LocalFocusRegistry
import com.gugugaga.ds.glass.GlassBackend
import com.gugugaga.ds.glass.GlassScene
import com.gugugaga.ds.glass.LocalGlassStrength
import com.gugugaga.ds.glass.platformGlassCaps
import com.gugugaga.ds.physics.MotionPrefs
import com.gugugaga.ds.theme.Accent
import com.gugugaga.ds.theme.GuguTheme
import com.gugugaga.ds.theme.ThemeName
import com.gugugaga.ds.theme.UiMode
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 报头和台词要的「现在」：日期、期号、几点、第几天（台词的种子）。根算一次；测试里给固定值，截图才稳定。 */
data class Today(
    val date: String,
    val volume: Int,
    val hour: Int,
    val day: Int,
    /** 电视的报头 / 页眉上的时钟和星期（电视没有状态栏）。 */
    val minute: Int = 47,
    val weekday: String = "星期六",
    /** true = 这是「现在」（应用里）：电视的时钟每分钟跟着走。测试 / 截图给的固定时刻是 false，时钟不动。 */
    val live: Boolean = false,
) {
    /** 「21:47」。 */
    val clock: String get() = "$hour:${if (minute < 10) "0$minute" else minute.toString()}"

    companion object {
        /** 截图 / 测试用的固定时刻（和设计稿一样：十一月十四日，晚上九点，第 318 期）。 */
        val SAMPLE = Today("十一月十四日", 318, 21, 20771)
    }
}

/**
 * 整个应用的根（docs/adr/0001-architecture.md §4）。壳（:shell-desktop / :shell-android）造好上游的 [AppGraph] 交进来，这里不写平台分支。
 *
 * 层次：上游 `GuguTheme`（UiMode、窗口尺寸、按压物理、动效偏好）→ [GugaTheme]（画报 token + 专色）→ 上游 `GlassScene`（页面内容 = 玻璃要折射的东西；
 * 固定导航 = 玻璃）→ 当前界面（按上游的路由）。
 */
@Composable
fun GugaartApp(graph: AppGraph, windowSize: DpSize? = null) {
    // 电视版按 1920 × 1080 dp 设计（设计稿、离屏测试都是这个尺寸）。真机常是 1920 × 1080 px、密度 2.0（= 960 × 540 dp），
    // 不换算的话所有东西大一倍、挤出屏幕（用户在海信电视上看到：登录键盘挤成一窄条）。所以电视上把密度换成「窗口宽 / 1920」，
    // 字号跟着走（不吃系统字体缩放：电视的 10 英尺字号是设计定的）。手机 / 电脑、给了 [windowSize] 的调用不变。
    val px = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    if (graph.deps.uiMode == UiMode.Tv && windowSize == null && px.width > 0) {
        val density = androidx.compose.ui.unit.Density(tvDesignDensity(px.width), fontScale = 1f)
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) { GugaartRoot(graph, null) }
    } else {
        GugaartRoot(graph, windowSize)
    }
}

/** 电视的设计宽度（dp）。 */
const val TV_DESIGN_WIDTH = 1920f

/** 电视上用的密度：让窗口正好是 [TV_DESIGN_WIDTH] dp 宽（1920 px → 1.0，3840 px → 2.0，1280 px → 0.667）。 */
fun tvDesignDensity(windowWidthPx: Int): Float = windowWidthPx / TV_DESIGN_WIDTH

@OptIn(ExperimentalTime::class)
@Composable
private fun GugaartRoot(graph: AppGraph, windowSize: DpSize?) {
    val deps = graph.deps
    val settings by graph.settings.state.collectAsState()
    val ui = settings.uiSettings
    val caps = remember { platformGlassCaps() }
    val backend = glassBackendFor(ui.perf, caps, deps.glassBackendOverride)
    // 会话：恢复上次的账号（登录 / 已保存账号 / 备用线路的逻辑都在上游的 SessionManager）
    LaunchedEffect(graph) { graph.session.restore() }
    // 更新不自动下载（用户 2026-11-15：「更不更新让用户点，不要自动更新」）：上游的「自动下载更新」默认开，这里每次启动关掉
    LaunchedEffect(graph) {
        com.gugaart.ui.screens.update.UpdateConsent.disableAutoDownload(graph)
        com.gugaart.ui.screens.update.UpdateConsent.load(graph.deps.local)
    }
    // Trakt：开着「打开应用时自动同步」就在登录好、首页取完数之后同步一次（每次启动、每个账号一次）
    val traktSession by graph.session.session.collectAsState()
    LaunchedEffect(traktSession?.serverId, traktSession?.userId) {
        val s = traktSession ?: return@LaunchedEffect
        kotlinx.coroutines.delay(20_000)
        com.gugaart.ui.trakt.TraktService.bind(graph)
        if (com.gugaart.ui.trakt.TraktService.prefs.value.autoSync && com.gugaart.ui.trakt.TraktService.state.value is com.gugaart.ui.trakt.TraktState.Connected) {
            com.gugaart.ui.trakt.TraktService.syncToEmby(s.client)
        }
    }
    // 宋体：资源里有字体文件就换上（没有就一直是平台衬线字体）
    LaunchedEffect(Unit) { GugaFonts.ensureLoaded() }
    // 应用里封面的牌堆是活的（每一波随机抽）；离屏测试不走这个函数，牌堆是定死的
    remember { com.gugaart.ui.screens.home.CoverDeck.live = true }
    // 发现页的高分榜同理：应用里每次进来都从评分前 40 部里重新抽 10 部
    remember { com.gugaart.ui.screens.discover.DiscoverDice.live = true }

    // 画报自己的设置（独立的键 gugaart.settings.v1）和期号（本地计数）
    val store = remember(graph) { GugaSettingsStore(deps.local) }
    val prefs by store.prefs.collectAsState()
    // 弹幕的画报默认（区域 10 %、字号 150 %）：只在第一次启动、这两项还是上游默认时写一次（data/DanmakuDefaults.kt）
    LaunchedEffect(store) {
        if (!store.prefs.value.danmakuSeeded) {
            val stored = (graph.settings.value["danmaku"] as? kotlinx.serialization.json.JsonObject)?.get("render") as? kotlinx.serialization.json.JsonObject
            com.gugaart.ui.data.DanmakuDefaults.seed(stored)?.let { graph.settings.patch("danmaku", "render" to it) }
            store.update { it.copy(danmakuSeeded = true) }
        }
    }
    val today = remember {
        val now = Clock.System.now().toEpochMilliseconds()
        val local = now + localUtcOffsetMs(now)
        val epochDay = local.floorDiv(86_400_000L)
        val (y, m, d) = civilFromEpochDay(epochDay)
        fun two(v: Int) = if (v < 10) "0$v" else v.toString()
        Today(
            date = cnMonthDayOf(now, localUtcOffsetMs(now)),
            volume = store.issue("$y-${two(m)}-${two(d)}").volume,
            hour = (local.mod(86_400_000L) / 3_600_000L).toInt(),
            day = epochDay.toInt(),
            minute = (local.mod(3_600_000L) / 60_000L).toInt(),
            // 1970-01-01 是星期四
            weekday = "星期" + "四五六日一二三"[epochDay.mod(7L).toInt()],
            live = true,
        )
    }

    GuguTheme(
        theme = ThemeName.Dark,
        uiMode = deps.uiMode,
        palette = Accent.NEUTRAL,
        motion = MotionPrefs(tv = deps.uiMode == UiMode.Tv, reducedMotion = deps.reducedMotion()),
        windowSize = windowSize,
    ) {
        CompositionLocalProvider(
            LocalGraph provides graph,
            LocalOptionalGraph provides graph,
            LocalGlassStrength provides ui.glassStrength.toFloat(),
            LocalFocusRegistry provides graph.focus,
            LocalBackDispatcher provides graph.back,
            // 画报设置的那一个 store：设置页、首页的一次性提示都用它（改了整本画报立刻变）
            com.gugaart.ui.screens.settings.LocalGugaSettings provides store,
        ) {
            GugaartFrame(backend = backend, today = today, prefs = prefs)
        }
    }
}

/**
 * 根以下的那一层：画报主题 + 玻璃场景 + 当前界面。单独成一个函数是为了离屏渲染测试能直接画它（给固定的 [today]）。
 * 必须在上游的 `GuguTheme` 里面，并且有 `LocalGraph`。[startTab]：测试 / 截图用，直接落在某个标签页。
 */
@Composable
fun GugaartFrame(backend: GlassBackend, today: Today, prefs: GugaPrefs = GugaPrefs(), startTab: PhoneTab = PhoneTab.Home, preset: ScreenPreset = ScreenPreset()) {
    val graph = LocalGraph.current
    val chrome = remember { PageChrome() }
    // 页面底 = 墨黑纸。整幅剧照是页面内容（backdropContent），不是这张底图。
    val paper = remember { PageBackdrops.solid(GugaColors().paper) }
    var tab by remember { mutableStateOf(startTab) }
    // 电脑 / 电视的导航页（Route.Home 之下的本地状态）和外壳状态
    val wide = remember { WideNav(WideTab.of(preset.wideTab)) }
    val route = graph.nav.route
    val session by graph.session.session.collectAsState()
    LaunchedEffect(route, tab) { chrome.resetScroll() }
    // 「没人动」的那只钟：应用里才有（测试可以从外面给一只）。电脑 / 电视：不在首页也不在播放，10 分钟没有输入 → 翻回首页
    val outerClock = com.gugaart.ui.idle.LocalIdleClock.current
    val idle = remember(outerClock, today.live) { outerClock ?: if (today.live) com.gugaart.ui.idle.IdleClock() else null }
    val idleHome = androidx.compose.runtime.rememberUpdatedState(prefs.idleHome)
    // 换页、在播放器里都算「有人在」（用户 2026-11-15：「播放器里面 返回 有几率直接返回到主页」）：看片时遥控器的键直接交给播放器、
    // 安卓的返回键在 Compose 之前就被接走，这只钟一直以为没人动；看了 10 分钟以上，按返回一离开播放器就被它翻回了首页。
    if (idle != null) LaunchedEffect(route) { idle.touch() }
    if (idle != null && graph.deps.uiMode != UiMode.Phone) LaunchedEffect(idle) {
        while (true) {
            idle.sleep(com.gugaart.ui.idle.IdleRules.homeCheckIn(idle.idleMs()))
            val r = graph.nav.route
            if (r is Route.Play) {
                idle.touch()
                continue
            }
            val atHome = r == Route.Home && wide.tab == WideTab.Home
            if (com.gugaart.ui.idle.IdleRules.shouldGoHome(idle.idleMs(), idleHome.value, graph.session.session.value != null, atHome, r is Route.Play)) {
                // 浮着的菜单收掉，导航栈清空，回到首页那一页；电视的焦点由首页自己落到默认位置（新的一页）
                DesktopContextMenu.close()
                TvLongPressMenu.close()
                com.gugaart.ui.screens.detail.StillsOverlay.close()
                wide.railOpen = false
                wide.tab = WideTab.Home
                graph.nav.reset(Route.Home)
                idle.touch()
            }
        }
    }
    // 没有海报的页面（片库、搜索、设置…）= 兜底朱红
    GugaTheme(spot = chrome.spot.takeIf { prefs.spotColorFollowsCover }) {
        CompositionLocalProvider(LocalChrome provides chrome, LocalGugaPrefs provides prefs, LocalHour provides today.hour, LocalPreset provides preset, LocalWideNav provides wide, com.gugaart.ui.idle.LocalIdleClock provides idle) {
            val mode = Guga.uiMode
            val phone = mode == UiMode.Phone
            val playing = route is Route.Play
            Box(
                // 电视：遥控器的方向键 / 菜单键 / 焦点兜底在 tv/TvShell.kt（tvRoot）；别的端不变
                Modifier.fillMaxSize()
                    // 任何输入（按键、鼠标、触摸）先在这里记一笔「有人在」，不吃事件
                    .then(
                        if (idle == null) Modifier else Modifier
                            .onPreviewKeyEvent {
                                idle.touch()
                                false
                            }
                            .pointerInput(idle) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                        idle.touch()
                                    }
                                }
                            },
                    )
                    .then(if (mode == UiMode.Tv) Modifier.tvRoot(graph, wide) else Modifier).focusable().onKeyEvent { e ->
                    // 返回键 / Esc：交给上游的导航（先收浮层，再回上一页）
                    if (e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape)) graph.nav.handleBack() else false
                },
            ) {
                // 播放器不在玻璃场景里：画面是原生层（在 Compose 下面），所以这一页不能垫纸色的底；纸色只画在让出来的页边上。
                // 播放器的控件也不是玻璃（docs/player.md §9）。
                // 播放时页面层不拆（用户 2026-11-15：「安卓从影片直接返回首页的时候还是排版比较久」）：以前进播放器时整个页面层（首页、详情）
                // 换成播放器，返回时首页从头取数、重排。现在页面层留在组合里（播放之前那一页 = pageRoute），播放时不放下（不画、不收输入、
                // 自动翻页停）；播放器盖在上面。返回的第一帧就是原来那一页。一开始就直接进播放器（测试 / 外部链接）时不额外垫页面。
                val inPlayer = playing && session != null
                val lastPage = remember { arrayOf<Route>(Route.Home) }
                if (route !is Route.Play) lastPage[0] = route
                val pageRoute = if (playing) lastPage[0] else route
                var pagesKept by remember { mutableStateOf(!inPlayer) }
                LaunchedEffect(inPlayer) { if (!inPlayer) pagesKept = true }
                if (inPlayer) GugaPlayerScreen(route as Route.Play)
                if (!inPlayer || pagesKept) KeptPage(!inPlayer) { CompositionLocalProvider(com.gugaart.ui.idle.LocalPageShown provides !inPlayer) { GlassScene(
                    backend = backend,
                    // 电视的 ControlledGl 后端只折射这张图：整幅铺满的页面把剧照的整屏小图放在 wide.bleedBackdrop 里（docs/glass.md §4）
                    backdrop = if (mode == UiMode.Tv) wide.bleedBackdrop ?: paper else paper,
                    modifier = Modifier.fillMaxSize(),
                    // 玻璃要「看见」的页面内容画在这里：Live 后端把它录进取屏层，固定导航才有东西可折射
                    backdropContent = {
                        Box(Modifier.fillMaxSize().background(Guga.colors.paper)) {
                            when {
                                // 电脑 / 电视：各自一套版式（desktop/、tv/），和手机共用数据入口
                                mode == UiMode.Desktop -> if (session == null) DesktopLoginScreen(today) else DesktopPages(pageRoute, today)
                                mode == UiMode.Tv -> if (session == null) TvLoginScreen(today) else TvPages(pageRoute, today)
                                session == null -> LoginScreen(today)
                                else -> {
                                    // 手机的首页离开后不拆（用户 2026-11-15：「安卓从其他界面返回首页排版很久，这个不能卡」）：
                                    // 以前进详情 / 换标签页时首页整个拆掉，回来要重新取数、解码、排立体海报流，一块一块排出来。
                                    // 现在首页第一次露面后就一直留在组合里：被盖住时不排、不画、不收输入，自动翻页和换一波也停（LocalPageShown）；
                                    // 回来的第一帧就是离开时的样子（滚动位置、翻到的那一期都在）。
                                    val homeShown = pageRoute == Route.Home && tab == PhoneTab.Home
                                    var homeKept by remember { mutableStateOf(false) }
                                    LaunchedEffect(homeShown) { if (homeShown) homeKept = true }
                                    if (homeShown || homeKept) {
                                        KeptPage(homeShown) {
                                            CompositionLocalProvider(com.gugaart.ui.idle.LocalPageShown provides (homeShown && !inPlayer)) {
                                                HomeScreen(today.volume, today.date, today.day)
                                            }
                                        }
                                    }
                                    // 返回栈上的每一页记住自己的滚动位置：片库进详情再返回，回到进去的那一屏（PageStates）
                                    val pages = rememberPageStates(graph.nav)
                                    if (!homeShown) pages.Page(graph.nav, if (pageRoute == Route.Home) tab.ordinal else 7) { when (pageRoute) {
                                        Route.Home -> when (tab) {
                                            PhoneTab.Home -> Unit
                                            PhoneTab.Library -> WallScreen(null)
                                            PhoneTab.Discover -> DiscoverScreen(today)
                                            PhoneTab.Me -> MeScreen(today)
                                        }
                                        is Route.Library -> LibraryScreen(pageRoute.id)
                                        is Route.Search -> SearchScreen(pageRoute.q)
                                        is Route.Item -> ItemScreen(pageRoute.id, today.day)
                                        is Route.History -> if (pageRoute.tab == "favorites") FavsScreen() else HistoryScreen()
                                        // 上游有的地方用 Browse(Favorites) 进收藏：同一页
                                        is Route.Browse -> if (pageRoute.kind == com.gugugaga.app.nav.BrowseKind.Favorites) FavsScreen() else ComingPage(today, "这一页")
                                        is Route.Settings -> SettingsScreen(pageRoute.section)
                                        Route.Discover -> DiscoverScreen(today)
                                        Route.Servers, is Route.Login -> LoginScreen(today)
                                        else -> ComingPage(today, "这一页")
                                    } }
                                }
                            }
                        }
                    },
                ) {
                    // 玻璃只用在固定导航和固定悬浮钮（docs/glass.md）：手机 = 底部标签栏 + 搜索圆钮，只在标签页和搜索页
                    val tabPage = pageRoute == Route.Home || pageRoute is Route.Search || pageRoute is Route.Library
                    if (phone && session != null && tabPage) {
                        PhoneTabBar(
                            active = if (pageRoute == Route.Home) tab else if (pageRoute is Route.Library) PhoneTab.Library else null,
                            onSelect = {
                                tab = it
                                if (route != Route.Home) graph.nav.reset(Route.Home)
                            },
                            onSearch = { if (route !is Route.Search) graph.nav.navigate(Route.Search()) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                            sunk = chrome.tabBarSunk,
                            searchSelected = route is Route.Search,
                        )
                    }
                    // 电脑 = 报头下的一排玻璃；电视 = 左侧玻璃侧栏。登录页上不出现
                    if (mode == UiMode.Desktop && session != null) DesktopChrome(pageRoute, today)
                    if (mode == UiMode.Tv && session != null) TvChrome(pageRoute)
                } } }
                // 根上的两样：「再按一次返回」小条（贴在标签栏上方）和更新号外（纸片，不在播放画面上出现）
                if (!playing && phone) ExitBar(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
                if (!playing) when (mode) {
                    UiMode.Phone -> UpdateExtra()
                    UiMode.Desktop -> DesktopUpdateExtra()
                    UiMode.Tv -> TvUpdateExtra()
                }
                // 读者证上的称号：看过的部数到了下一级就出一张纸片（应用里才数；号外在的时候先让号外）
                if (!playing) com.gugaart.ui.screens.me.RankHost(live = today.live, tv = mode == UiMode.Tv)
                // 纸片菜单（不是玻璃）：电脑的右键菜单、电视的长按确定菜单，各画一次
                if (mode == UiMode.Desktop) DesktopContextMenu.Host()
                if (mode == UiMode.Tv) TvLongPressMenu.Host()
                if (mode == UiMode.Phone) com.gugaart.ui.screens.PhoneItemMenu.Host()
                // 看剧照的大图（电脑 / 电视的详情页打开；手机在页里自己画）
                if (!playing && !phone) com.gugaart.ui.screens.detail.StillsOverlay.Host()
            }
        }
    }
}

/** 还没排的页面：报头的页名版 + 一句说明（不是空白墙）。 */
@Composable
private fun ComingPage(today: Today, name: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = Guga.dimens.margin)) {
        Spacer(Modifier.height(Guga.dimens.statusBar + 8.dp))
        Masthead(volume = "VOL. ${today.volume}", date = today.date, title = name.takeIf { it.length <= 4 })
        StateView("state-coming", "摆稿纸", "这一场还在筹备", "「$name」敬请期待。")
    }
}

/**
 * 一页留在组合里，但可以暂时「不在」：[shown] = false 时照样量（尺寸不变就用缓存，几乎不花时间），但不放下——
 * 不画、不收触摸 / 点击，状态（取到的数据、解码好的图、滚动位置、翻到的那一期）都留着。手机首页用它，回来第一帧就是完整的首页。
 */
@Composable
fun KeptPage(shown: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) { if (shown) placeable.place(0, 0) }
        },
    ) { content() }
}
