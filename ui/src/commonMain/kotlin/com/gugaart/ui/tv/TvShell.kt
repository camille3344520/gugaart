package com.gugaart.ui.tv

import com.gugaart.ui.components.rememberPageStates
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.StateView
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideNav
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.platform.localUtcOffsetMs
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.ENTRY_SETTLE_MS
import com.gugugaga.ds.focus.EntryFocus
import com.gugugaga.ds.focus.ROUTE_DEFAULT_KEY
import com.gugugaga.ds.focus.RemoteKey
import com.gugugaga.ds.focus.decideEntryFocus
import com.gugugaga.ds.focus.remoteKeyOf
import com.gugugaga.ds.physics.PressState
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 电视端（`UiMode.Tv`，1920 × 1080，电视密度按 1 设计：dp = 设计稿的 px）的版面骨架。设计稿 `fable-b1/tv`，规则 `docs/platforms.md` §3。
 *
 * 安全边：左 136（其中 28–104 是玻璃侧栏）、右 96、上 40、下 48。所有文字和焦点都在这个框里；只有整幅剧照、横排卡片的下一张可以出框。
 * 屏幕上**没有返回按钮**（返回键负责）。
 */
object TvSpec {
    val LEFT = 136.dp
    val RIGHT = 96.dp
    val TOP = 40.dp
    val BOTTOM = 48.dp
    /** 侧栏（收起）：x 28–104。 */
    val RAIL_LEFT = 28.dp
    val RAIL_WIDTH = 76.dp
    /** 侧栏展开成的目录页：宽 404（x 28–432）、高 836（1.5.0 多了「服务器」一行：770 + 66）、圆角 42。 */
    val RAIL_OPEN_WIDTH = 404.dp
    val RAIL_OPEN_HEIGHT = 836.dp
    /** 海报墙卡宽（2 : 3）与间距。 */
    val POSTER = 214.dp
    val POSTER_GAP = 32.dp
    val ROW_GAP = 36.dp
    /** 横排卡：续看 312 × 176、分集 400 × 225、演员 170 × 226。 */
    val RESUME_W = 312.dp
    val EPISODE_W = 400.dp
    val PERSON_W = 170.dp
    /** 侧栏展开时页面上的一层墨色（纸色 62 %）。 */
    const val SCRIM_ALPHA = 0.62f
}

/** 焦点区的名字（focusKey 的前缀）：侧栏、长按菜单不算页面内容，不进「这一页上次的焦点」。 */
const val TV_RAIL_ZONE = "tv-rail"
const val TV_MENU_ZONE = "tv-menu"

/** 进页面多久看一次「这一页要的焦点装好没有」；焦点没了多久算丢了。 */
private const val ENTRY_POLL_MS = 40L
private const val RESCUE_DELAY_MS = 120L

/**
 * 电视外壳自己的状态（焦点兜底、侧栏的来回、菜单键）。挂在 [WideNav.shell] 上：根（按键）、页面层、侧栏三处看到的是同一份。
 * 焦点的移动、登记、返回栈、每页的记忆都是上游的（`ds/focus`）；这里只有「电视画报怎么用它」。
 */
@Stable
class TvShellState {
    /** 根（或它下面的东西）有焦点 / 焦点就在根自己身上（= 页面里没有东西有焦点）。 */
    var rootHasFocus by mutableStateOf(false)
    var rootSelf by mutableStateOf(false)
    val rootFocus = FocusRequester()

    /** 页面内容那一层（一个焦点组）。 */
    var pageHasFocus by mutableStateOf(false)
    val pageFocus = FocusRequester()

    /** 侧栏画着（登录页上没有）。 */
    var railPresent by mutableStateOf(false)
    /** 首页当前是否露着（plain 字段，离开首页时不让 TvHomeDirectory 的 onFocusChanged 误把 inRows 清回 false）。 */
    var homeActive: Boolean = true
    /** 返回恢复焦点时跳过一次 200 ms 放大入场动画（外观落定后完全一致）。 */
    var skipNextLift: Boolean = false

    /** 展开侧栏之前焦点在哪（收起时还给它）。 */
    var railReturn: PressState? = null

    /** 这一页的菜单键处理（[TvMenuKey]）；焦点项的长按菜单（[TvFocusable] 拿到焦点时交上来、失去焦点时收回；焦点项没有菜单 = null）。 */
    var onMenu: (() -> Boolean)? = null
    var focusedLongClick: (() -> Unit)? = null

    /** 正按着的确定键：长按打开了菜单以后，这一次按键剩下的部分不交给菜单（[TvOkHold]）。 */
    val okHold = TvOkHold()

    /** 焦点所在的图片卡，图那一格在屏幕上的位置（px）；焦点不在图片卡上 = null。长按菜单贴在它旁边。 */
    var focusedPicture: androidx.compose.ui.geometry.Rect? = null

    /** 盖在页面上的东西开着几层（[TvModal]）：> 0 时一路按左不进侧栏。 */
    var modals by mutableIntStateOf(0)

    /** 现在这一页在「每页的焦点记忆」里的编号（返回栈条目 × 8 + 导航页）。 */
    var entry: Long = 0L

    /** 测试预置的焦点（[com.gugaart.ui.screens.ScreenPreset.focus]）只在第一页用一次。 */
    var presetUsed = false

    fun menuKey() {
        if (onMenu?.invoke() == true) return
        focusedLongClick?.invoke()
    }
}

/**
 * 一次按住的确定键：从第一个「按下」到「抬起」，中间的「按下」都是自动重复（安卓 `repeatCount > 0`、键盘的自动重复；看的是有没有抬起过，
 * 不看重复计数，遥控器自己发重复也一样）。
 *
 * 长按在这一次按住里触发了（[longPressFired]，菜单 / 页边的管理开了），焦点也已经离开按下去的那一项：剩下的重复和抬起全部吃掉，
 * 菜单停在第一行等新的一次确定。不吃的话，上游 `pressable` 把焦点项收到的第一个重复当成新的一次按下（`keyDownHere`），
 * 抬起时就点中了菜单的第一行——「播放」，用户看到的是长按直接开播（1.5.1）。焦点还在那一项上时照常交给它（上游知道长按过的抬起不算点击）。
 */
class TvOkHold {
    private var key: Key? = null
    private var origin: PressState? = null
    private var longFired = false
    private var swallowAll = false

    /** 根上的一个确定键事件；true = 吃掉。 */
    fun onKey(k: Key, down: Boolean): Boolean {
        if (down) {
            if (key == null) {
                key = k
                origin = null
                longFired = false
                swallowAll = false
                return false
            }
            return k == key && spent()
        }
        if (k != key) return false
        val eat = spent()
        reset()
        return eat
    }

    /** [from] 的长按触发了（[TvFocusable] 的长按先调它）。没按着确定（菜单键、鼠标长按、测试直接调）= 不算，除非明确传 [pressedKey]。 */
    fun longPressFired(from: PressState, pressedKey: Key? = null, swallowRemaining: Boolean = false) {
        if (key == null) {
            if (pressedKey != null) key = pressedKey else return
        }
        longFired = true
        origin = from
        if (swallowRemaining) swallowAll = true
    }

    /** 抬起收不到了（窗口失去焦点 / 触发短按）：这一次按住作废。 */
    fun reset() {
        key = null
        origin = null
        longFired = false
        swallowAll = false
    }

    private fun spent() = longFired && (swallowAll || origin?.focused != true)
}

/** 上游 `pressable` 当成「确定」的键（OK / 方向键中间、回车、空格）。 */
internal fun isOkKey(k: Key) = k == Key.Enter || k == Key.NumPadEnter || k == Key.DirectionCenter || k == Key.Spacebar
/**
 * 测试缝：`WideNav` 是根里 `remember` 的，离屏测试从外面拿不到；页面层在的时候把它放在这里，测试读「侧栏开着没有 / 在哪个导航页 /
 * 交给玻璃场景的整幅图」。应用里没有人读它。
 */
object TvProbe {
    var nav: WideNav? = null
        internal set
}

/** 电视外壳的状态：第一次用到时放进 [WideNav.shell]。 */
val WideNav.tv: TvShellState
    get() = (shell as? TvShellState) ?: TvShellState().also { shell = it }

/** 展开侧栏：记下焦点原来在哪。焦点由侧栏自己接（`TvChrome`）。 */
fun WideNav.openRail(from: PressState?) {
    if (railOpen || !tv.railPresent || tv.modals > 0) return
    tv.railReturn = from
    railOpen = true
}

/** 收起侧栏。[restore] = 把焦点还给展开之前的那一项（按右 / 返回）；选了别的页面时不还（新页面自己放焦点）。 */
fun WideNav.closeRail(restore: Boolean = true) {
    if (!restore) tv.railReturn = null
    railOpen = false
}

/**
 * 根上的遥控器处理（`GugaartFrame` 的根 Box，在它的 `focusable()` 左边）。做这几件事：
 * 1. 数按键（上游 `FocusRegistry.keyPresses`：用户动过之后外壳不再替他放焦点）；
 *    长按确定打开了菜单：这一次按住剩下的重复和抬起在这里吃掉（[TvOkHold]，所有页面的长按菜单一处管）；
 * 2. 菜单键（遥控器 ≡；键盘 M）→ 这一页的选项，没有就 = 焦点项的长按菜单；
 * 3. 方向键没人处理时移动焦点（Compose 桌面版不会自己把方向键变成焦点移动；安卓会，这里移动成功就吃掉，不会动两次）；
 *    **一路按左到头 = 展开侧栏**；侧栏开着时按右 = 收起并回到原来的焦点；
 * 4. 根上放一个焦点请求器：页面里没有东西有焦点时按键也有地方去，方向键把焦点送进页面。
 *
 * 返回键不在这里：根原有的处理（`nav.handleBack()`）先问上游的 `BackDispatcher`（菜单 → 面板 → 下一屏回上一屏，[TvBack] 压进去的），
 * 再退上一页；在首页没人处理时交给壳（安卓壳用上游 `ExitConfirm` 做「再按一次退出」）。
 */
fun Modifier.tvRoot(graph: AppGraph, wide: WideNav): Modifier = composed {
    val tv = wide.tv
    val fm = LocalFocusManager.current
    // 什么都没有焦点时根先拿着（按键有地方去）；页面已经放好焦点就不抢
    LaunchedEffect(tv) { if (!tv.rootHasFocus) runCatching { tv.rootFocus.requestFocus() } }
    // 窗口失去焦点：按着的确定键的抬起不会来了
    val window = LocalWindowInfo.current
    LaunchedEffect(tv, window) { snapshotFlow { window.isWindowFocused }.collect { if (!it) tv.okHold.reset() } }
    this
        .onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown) graph.focus.keyPresses++
            if (isOkKey(e.key) && (e.type == KeyEventType.KeyDown || e.type == KeyEventType.KeyUp) &&
                tv.okHold.onKey(e.key, e.type == KeyEventType.KeyDown)
            ) return@onPreviewKeyEvent true
            val menu = remoteKeyOf(e.key) == RemoteKey.Menu || (e.key == Key.M && !e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed)
            if (!menu) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyUp) tv.menuKey()
            true
        }
        .onKeyEvent { e ->
            // 返回键：页面里没有东西有焦点时（焦点就在根上），根原有的处理够不着（它在 focusable 的右边），这里补上
            if (e.type == KeyEventType.KeyUp && tv.rootSelf && remoteKeyOf(e.key) == RemoteKey.Back && e.key != Key.Backspace) return@onKeyEvent graph.nav.handleBack()
            if (e.type != KeyEventType.KeyDown || e.isCtrlPressed || e.isAltPressed || e.isMetaPressed) return@onKeyEvent false
            val direction = when (remoteKeyOf(e.key)) {
                RemoteKey.Up -> FocusDirection.Up
                RemoteKey.Down -> FocusDirection.Down
                RemoteKey.Left -> FocusDirection.Left
                RemoteKey.Right -> FocusDirection.Right
                else -> return@onKeyEvent false
            }
            when {
                // 侧栏开着：上下在侧栏里走（侧栏是个关着门的焦点区），右 = 收起，左不动
                wide.railOpen -> {
                    if (direction == FocusDirection.Right) wide.closeRail() else if (direction != FocusDirection.Left) fm.moveFocus(direction)
                    true
                }
                // 页面里没有东西有焦点：方向键把焦点送进页面
                tv.rootSelf || !tv.rootHasFocus -> {
                    tvRescueFocus(graph, tv)
                    true
                }
                else -> {
                    val from = graph.focus.lastFocusedPress
                    val moved = fm.moveFocus(direction)
                    // 一路按左到头 = 进导航
                    if (!moved && direction == FocusDirection.Left) wide.openRail(from)
                    true
                }
            }
        }
        .onFocusChanged {
            tv.rootHasFocus = it.hasFocus
            tv.rootSelf = it.isFocused
        }
        .focusRequester(tv.rootFocus)
}

/**
 * 焦点丢了（焦点项随页面走了）：回到**这一页**最后那个带 key 的项 → 这一页的默认项 → 页面里第一个能接焦点的 → 根。
 *
 * 「最后那个」按页记（上游 `RouteFocusMemory`，和进页面放焦点用的是同一份），不用全局的 `lastFocusedKey`：focusKey 只在一页里唯一，
 * 换了页以后上一页的 key 可能正好是这一页另一个东西的 key——片库列表的「lib-<库>」和首页「媒体库」一栏的卡片同名，
 * 片库按返回回首页时焦点会被兜底放到首页目录里的那张库卡上，而不是封面（`Tv1ShellTest.backLevels`）。
 */
internal fun tvRescueFocus(graph: AppGraph, tv: TvShellState) {
    // 换服务器的页边开着：焦点归它（侧栏收起时的兜底不要把焦点拉回页面）
    if (TvServerPanel.open) return
    val last = graph.focusMemory.recall(tv.entry)
    if (last != null && graph.focus.request(last)) return
    if (graph.focus.request(ROUTE_DEFAULT_KEY)) return
    if (runCatching { tv.pageFocus.requestFocus() }.getOrDefault(false)) return
    runCatching { tv.rootFocus.requestFocus() }
}

/**
 * 进了一页，焦点放哪（上游 `decideEntryFocus` 的规则）：回到这一页上次的焦点（从详情返回）→ 这一页的默认项（`default = true`）→
 * 页面里第一个能接焦点的。用户按过键之后不再动。[preset]：测试预置的焦点 key（`tv-rail/…` = 展开侧栏）。
 */
private suspend fun runTvEntryFocus(g: AppGraph, wide: WideNav, entry: Long, preset: String?) {
    val tv = wide.tv
    val presses = g.focus.keyPresses
    var elapsed = 0L
    var fellBack = false
    if (preset != null) {
        if (preset.startsWith("$TV_RAIL_ZONE/")) {
            // 等侧栏和页面都在了再展开（焦点由侧栏接）
            while (elapsed < ENTRY_SETTLE_MS && !tv.railPresent) { delay(ENTRY_POLL_MS); elapsed += ENTRY_POLL_MS }
            if (!g.focus.request(ROUTE_DEFAULT_KEY)) runCatching { tv.pageFocus.requestFocus() }
            wide.openRail(g.focus.lastFocusedPress)
            return
        }
        while (elapsed < ENTRY_SETTLE_MS && g.focus.keyPresses == presses) {
            if (g.focus.isAttached(preset) && g.focus.request(preset)) return
            delay(ENTRY_POLL_MS)
            elapsed += ENTRY_POLL_MS
        }
        elapsed = 0L
    }
    var frameTries = 0
    while (true) {
        val mem = g.focusMemory.recall(entry)
        val decision = if (tv.modals > 0 || wide.railOpen) EntryFocus.Wait else decideEntryFocus(
            moved = g.focus.keyPresses != presses,
            elapsedMs = elapsed,
            memoryKey = mem,
            memoryReady = mem != null && g.focus.isAttached(mem),
            defaultReady = g.focus.isAttached(ROUTE_DEFAULT_KEY),
            focusInPage = tv.pageHasFocus,
            alreadyFellBack = fellBack,
        )
        when (decision) {
            EntryFocus.Done -> return
            EntryFocus.Memory -> if (g.focus.request(mem!!)) return
            EntryFocus.Default -> if (g.focus.request(ROUTE_DEFAULT_KEY)) return
            EntryFocus.FirstInPage -> {
                fellBack = true
                if (!tv.pageHasFocus) runCatching { tv.pageFocus.requestFocus() }
            }
            EntryFocus.Wait -> {}
        }
        if (elapsed > ENTRY_SETTLE_MS) return
        if (frameTries < 3) {
            frameTries++
            withFrameNanos { }
        } else {
            delay(ENTRY_POLL_MS)
            elapsed += ENTRY_POLL_MS
        }
    }
}

/**
 * 电视端的页面层（进 `GlassScene` 的 `backdropContent`）。导航页（首页 / 发现 / 片库 / 合集 / 我的）是 `Route.Home` 之下的本地状态；
 * 搜索、设置等有上游路由的照路由走。
 *
 * 焦点（`docs/platforms.md` §3）在这一层接好：
 * - **默认焦点 / 回到上次的焦点**：每进一页（返回栈条目 × 导航页）跑一次 [runTvEntryFocus]；每页记住最后一个带 key 的焦点（上游 `RouteFocusMemory`）。
 * - **焦点兜底**：焦点项不见了（页面换了、列表重排）而没人接手，120 ms 后放回去——遥控器没有别的办法把焦点找回来。
 * - **返回层级**：导航页（发现 / 片库 / 合集 / 我的）按返回 = 回首页（压在上游 `BackDispatcher` 里，所以壳的系统返回也走它）。
 * - 侧栏展开时页面上盖一层墨色（画在页面这一层：玻璃折射到的就是压暗后的页面）。
 */
@Composable
fun TvPages(route: Route, today: Today) {
    val g = LocalGraph.current
    val nav = LocalWideNav.current
    val tv = nav.tv
    val preset = LocalPreset.current
    // 换导航页：旧的一页先拆掉、空一帧，再排新的一页。同一帧里拆一页带滚动内容的、又排一页新的，Compose 1.12 会撞上
    // 「LayoutNode … not found in RectList」（上游 ContentGlass.kt 记过同一个问题）。
    var shownTab by remember { mutableStateOf<WideTab?>(nav.tab) }
    LaunchedEffect(nav.tab) {
        if (shownTab != nav.tab) {
            shownTab = null
            withFrameNanos { }
            shownTab = nav.tab
        }
    }
    // 画着的已经是 nav.tab 这一页了（上面那一两帧里还不是）。「进页面放焦点」和「每页的焦点记忆」都要等它：
    // 不等的话，默认焦点会被放到还没拆掉的旧页面上（旧页面的默认项还挂着，请求成功，外壳以为放好了），旧页面的 key 也会记到新页面名下——
    // 片库按返回回首页，焦点就丢了 / 落到首页目录里同名的库卡上（`Tv1ShellTest.backLevels`）。
    val settled = route != Route.Home || shownTab == nav.tab
    val entry = g.nav.current.key * 8L + if (route == Route.Home) nav.tab.ordinal.toLong() else 7L
    if (settled) tv.entry = entry
    var prevEntry by remember { mutableStateOf(entry) }
    val entryChanged = settled && prevEntry != entry
    androidx.compose.runtime.SideEffect {
        if (settled && prevEntry != entry) {
            prevEntry = entry
            TvPerfTiming.onBackRouteChanged(if (route == Route.Home) "Home/${nav.tab}" else route.toString())
        }
    }
    DisposableEffect(nav) {
        TvProbe.nav = nav
        onDispose { }
    }
    DisposableEffect(g, tv) {
        g.focus.onKeyFocused = { k -> if (!k.startsWith("$TV_RAIL_ZONE/") && !k.startsWith("$TV_MENU_ZONE/") && !k.startsWith("$TV_SERVERS_ZONE/")) g.focusMemory.remember(tv.entry, k) }
        onDispose { g.focus.onKeyFocused = null }
    }
    // 离开返回栈的页面：忘掉
    val stackKeys = g.nav.entryKeys()
    LaunchedEffect(stackKeys) { g.focusMemory.retain(stackKeys.flatMap { k -> (0L..7L).map { k * 8L + it } }) }
    LaunchedEffect(entry, settled) {
        if (!settled) return@LaunchedEffect
        val want = preset.focus?.takeIf { !tv.presetUsed }
        tv.presetUsed = true
        runTvEntryFocus(g, nav, entry, want)
    }
    // 焦点兜底
    LaunchedEffect(tv) {
        snapshotFlow { tv.rootHasFocus && !tv.rootSelf }.collect { inside ->
            if (inside) return@collect
            delay(RESCUE_DELAY_MS)
            if ((tv.rootHasFocus && !tv.rootSelf) || tv.modals > 0 || nav.railOpen) return@collect
            tvRescueFocus(g, tv)
        }
    }
    // 返回：导航页 → 首页
    TvBack(route == Route.Home && nav.tab != WideTab.Home) { nav.tab = WideTab.Home }
    // 正用着的时候读者证过期（401）：记下来，登录页先说一句
    com.gugaart.ui.wide.WatchSessionExpiry()

    // 返回栈上的每一页记住自己的滚动 / 网格位置：片库进详情再返回，回到进去的那一排，焦点也就能落回那张卡（PageStates）
    val pages = rememberPageStates(g.nav)
    val homeShown = route == Route.Home && shownTab == WideTab.Home
    tv.homeActive = homeShown
    var homeKept by remember { mutableStateOf(false) }
    LaunchedEffect(homeShown) { if (homeShown) homeKept = true }
    var pendingHomeRestore by remember { mutableStateOf<Long?>(null) }
    if (homeShown && entryChanged) pendingHomeRestore = entry
    if (!homeShown) pendingHomeRestore = null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().onFocusChanged { tv.pageHasFocus = it.hasFocus }.focusGroup().testTag("tv-pages")) {
            if (homeShown || homeKept) {
                com.gugaart.ui.KeptPage(homeShown) {
                    Box(
                        Modifier.fillMaxSize()
                            .then(if (homeShown) Modifier.focusRequester(tv.pageFocus) else Modifier)
                            .onGloballyPositioned {
                                if (homeShown && pendingHomeRestore == entry && tv.modals == 0 && !nav.railOpen) {
                                    val mem = g.focusMemory.recall(entry)
                                    if (mem != null) {
                                        tv.skipNextLift = true
                                        if (g.focus.isAttached(mem) && g.focus.request(mem)) {
                                            pendingHomeRestore = null
                                        } else {
                                            tv.skipNextLift = false
                                        }
                                    }
                                }
                            },
                    ) {
                        CompositionLocalProvider(com.gugaart.ui.idle.LocalPageShown provides (homeShown && com.gugaart.ui.idle.LocalPageShown.current)) {
                            TvHomeScreen(today)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize().then(if (!homeShown) Modifier.focusRequester(tv.pageFocus) else Modifier)) {
                pages.Page(g.nav, if (route == Route.Home) shownTab?.ordinal ?: 7 else 7) { when (route) {
                Route.Home -> when (shownTab) {
                    null -> Box(Modifier.fillMaxSize())
                    WideTab.Home -> Unit
                    WideTab.Discover -> TvDiscoverScreen(today)
                    WideTab.Libs -> TvLibsScreen(today)
                    WideTab.Colls -> TvCollsScreen(today)
                    WideTab.Me -> TvMeScreen(today, null)
                }
                Route.Discover -> TvDiscoverScreen(today)
                is Route.Library -> TvLibraryScreen(route.id, today)
                is Route.Search -> TvSearchScreen(route.q, today)
                is Route.Item -> TvItemScreen(route.id, today)
                is Route.History -> TvMeScreen(today, route.tab)
                is Route.Settings -> TvSettingsScreen(route.section, today)
                Route.Servers, is Route.Login -> TvLoginScreen(today)
                else -> TvComing(today, "这一页")
            } }
            }
        }
        // 侧栏展开：页面压暗
        // 侧栏展开 / 长按菜单开着：页面压暗
        // 压暗的深浅只在绘制层读，组合里只看「压没压」（1.5.1 性能：侧栏开合那 300 ms 外壳不再每帧重组）
        val scrim = animateFloatAsState(if (nav.railOpen || TvLongPressMenu.target != null) TvSpec.SCRIM_ALPHA else 0f, tween(300), label = "tv-scrim")
        val scrimOn = androidx.compose.runtime.remember(scrim) { androidx.compose.runtime.derivedStateOf { scrim.value > 0f } }
        if (scrimOn.value) {
            val paper = Guga.colors.paper
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = scrim.value }.background(paper).testTag("tv-scrim"))
        }
    }
}

/**
 * 电视的时钟「21:47」（电视没有状态栏；报头右端、内页页眉右端都有）。应用里（[Today.live]）每分钟跟着走；
 * 测试 / 截图给的是固定时刻，不动。
 */
@OptIn(ExperimentalTime::class)
@Composable
fun TvClock(today: Today, size: androidx.compose.ui.unit.TextUnit = 27.sp) {
    var text by remember(today) { mutableStateOf(today.clock) }
    if (today.live) {
        LaunchedEffect(today) {
            while (true) {
                val now = Clock.System.now().toEpochMilliseconds()
                val local = now + localUtcOffsetMs(now)
                text = tvClockText(local)
                // 睡到下一分钟的开头
                delay(60_000L - local.mod(60_000L) + 50L)
            }
        }
    }
    BasicText(text, Modifier.testTag("tv-clock"), style = Guga.numeral(size, Guga.colors.ink, letterSpacing = 0.04.em).copy(fontWeight = FontWeight.Bold), maxLines = 1)
}

/** 本地时间（自 1970 起的毫秒，已加时区）→「21:47」。纯函数，有单测。 */
fun tvClockText(localMs: Long): String {
    val h = (localMs.mod(86_400_000L) / 3_600_000L).toInt()
    val m = (localMs.mod(3_600_000L) / 60_000L).toInt()
    return "$h:${if (m < 10) "0$m" else m.toString()}"
}

/** 电视的完整报头（首页）：上下线 2 px；日期后面多星期 + 时钟。`VOL.` 可聚焦（付印新刊）由首页自己包。 */
@Composable
fun TvMasthead(today: Today, modifier: Modifier = Modifier) {
    Masthead(volume = "VOL. ${today.volume}", date = today.date, modifier = modifier, issue = today.volume, dateSlot = { com.gugaart.ui.screens.me.RankChip(today.date, it) }, trailing = {
        BasicText(today.weekday, style = Guga.song(Guga.type.kicker, FontWeight.Normal, Guga.colors.dim, letterSpacing = 0.14.em), maxLines = 1)
        TvClock(today)
    })
}

/**
 * 内页的页眉：左 英文小标 + 页名（宋体 56），右 时钟；下面一条 2 px 主线。**实心**（内容不从它下面露半行）。
 */
@Composable
fun TvPageHeader(title: String, english: String, today: Today, modifier: Modifier = Modifier, trailing: String? = null, hints: List<Pair<String, String>> = emptyList()) {
    val colors = Guga.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.paper)
            .padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = TvSpec.TOP)
            .height(84.dp)
            .drawBehind { drawLine(colors.ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
            .testTag("tv-page-header"),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        BasicText(title, Modifier.alignByBaseline(), style = Guga.song(56.sp, FontWeight.Black, letterSpacing = 0.12.em), maxLines = 1)
        BasicText(english, Modifier.alignByBaseline(), style = Guga.numeral(21.sp, colors.spot, letterSpacing = 0.26.em), maxLines = 1)
        // 这一页的按键提示：「菜单键 筛选 / 排序」「长按确定 菜单」
        for ((key, what) in hints) {
            Row(Modifier.alignByBaseline().padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText(key, Modifier.alignByBaseline().border(1.dp, colors.faint).padding(horizontal = 10.dp, vertical = 2.dp), style = Guga.song(20.sp, FontWeight.Bold, colors.dim), maxLines = 1)
                BasicText(what, Modifier.alignByBaseline(), style = Guga.song(22.sp, color = colors.faint), maxLines = 1)
            }
        }
        Box(Modifier.weight(1f))
        if (trailing != null) BasicText(trailing, Modifier.alignByBaseline(), style = Guga.note(24.sp, colors.dim), maxLines = 1)
        Box(Modifier.alignByBaseline()) { TvClock(today) }
    }
}

/** 一个普通的电视内页：实心页眉 + 安全边里的内容。 */
@Composable
fun TvPage(title: String, english: String, today: Today, modifier: Modifier = Modifier, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().testTag("tv-page")) {
        TvPageHeader(title, english, today, trailing = trailing)
        Column(Modifier.fillMaxSize().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, bottom = TvSpec.BOTTOM), content = content)
    }
}

/** 还没排的电视页面。 */
@Composable
fun TvComing(today: Today, name: String) {
    TvPage(name, "COMING", today) { StateView("state-coming", "摆稿纸", "这一场还在筹备", "「$name」敬请期待。") }
}
