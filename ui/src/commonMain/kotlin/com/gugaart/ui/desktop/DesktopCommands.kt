package com.gugaart.ui.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import com.gugaart.ui.idle.IdleClock
import com.gugaart.ui.screens.PageChrome
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.wide.WideNav
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlayerKeyCommand
import kotlin.time.TimeSource

/**
 * M2-3（ADR 0004 §2.4）：菜单和快捷键共用的一张命令表。每条带标签和两种修饰键写法（Ctrl / ⌘）；
 * `run` 的内容就是原来 `DesktopKeys.handle` 各分支里的动作；菜单栏（M2-4）调同一个 `run`。
 * 「前进」永远不可用：上游 `NavController` 没有前进栈（不另造）。
 */
enum class DesktopCommand(val label: String, val ctrlHint: String?, val cmdHint: String?) {
    Search("搜索片库…", "Ctrl K", "⌘K"),
    Settings("设置…", "Ctrl ,", "⌘,"),
    Reprint("付印新刊", "F5", "⌘R"),
    Back("返回", null, "⌘["),
    Forward("前进", null, "⌘]"),
    GoHome("首页", null, "⌘1"),
    GoDiscover("发现", null, "⌘2"),
    GoLibs("片库", null, "⌘3"),
    GoColls("合集", null, "⌘4"),
    GoMe("我的", null, "⌘5"),
    Filter("筛选", "Ctrl F", "⌘F"),
    Favorite("收藏 · 盖章", "V", "V"),
    Played("标记已看", "W", "W"),
}

/** 提示文字写 Ctrl 还是 ⌘（由壳提供的 [LocalDesktopHost] 决定）。 */
fun DesktopCommand.hint(m: ModifierGlyph): String? = when (m) {
    ModifierGlyph.Ctrl -> ctrlHint
    ModifierGlyph.Cmd -> cmdHint
}

/**
 * 播放器那一组命令的接收者：`DesktopPlayer` 组合时登记、离开时置空；菜单栏的「播放」组经它把
 * [PlayerKeyCommand] 交给当前播放器（不碰引擎）。
 */
object PlayerCommandSink {
    var sink: ((PlayerKeyCommand) -> Unit)? = null

    /**
     * 菜单「播放 › 投屏（DLNA）…」：请播放器页打开投屏面板（面板是播放器页自己的状态，不是 [PlayerKeyCommand]）。
     * 播放器页看到就打开并清掉；没有投屏宿主时什么也不做。
     */
    var castPanelRequested: Boolean by androidx.compose.runtime.mutableStateOf(false)
}

/**
 * 应用级命令的接收者：`GugaartApp` 桌面分支登记（拿着它自己的 nav / chrome / 闲置钟），
 * 菜单栏（M2-4，AWT 事件、不经过 Compose 输入）经它调 [DesktopCommands.run]。
 */
object DesktopCommandSink {
    var sink: ((DesktopCommand) -> Unit)? = null

    /**
     * 「付印新刊」这一刻有没有页面接着（[com.gugaart.ui.wide.WideNav.onReprint] 只在首页挂上）：菜单栏照「筛选」的待遇，接不住就灰。
     * 是快照状态——登记的人（`GugaartApp` 桌面分支）给一个读 `WideNav.onReprint` 的函数，菜单在组合里调它，首页挂上 / 撤下时菜单跟着变。
     */
    var reprintReady: (() -> Boolean)? by androidx.compose.runtime.mutableStateOf(null)

    /**
     * 一次 ⌘ 按键可能既被菜单栏挂的快捷键（AppKit `performKeyEquivalent`）接住、又落到界面的按键处理
     * （[DesktopKeys]）：同一条命令在这个窗口内只执行一次。菜单和界面两边在执行前都来 [claimOnce] 问一次——
     * 谁先到谁执行，后到的同一条命令被跳过。窗口取得很小（一次按键的两条路几乎同时到），不影响隔了一会儿的
     * 正常重复按键。纯状态 + 可注入时钟，单测锁住「一次按键只执行一次」。
     */
    private var guardCmd: DesktopCommand? = null
    private var guardAtNs: Long = 0
    internal const val GUARD_WINDOW_NS: Long = 150_000_000L // 150 ms

    /** 单调时钟原点（公共写法，iOS 也编得过）：now 取「距原点多少纳秒」，不用 JVM 的 System.nanoTime()。 */
    private val guardClockOrigin = TimeSource.Monotonic.markNow()
    private fun monotonicNowNs(): Long = guardClockOrigin.elapsedNow().inWholeNanoseconds

    /** true = 这次该执行（已记下）；false = 同一条命令刚执行过、这次跳过。[nowNs] 单测注入。 */
    fun claimOnce(cmd: DesktopCommand, nowNs: Long = monotonicNowNs()): Boolean {
        if (cmd == guardCmd && nowNs - guardAtNs < GUARD_WINDOW_NS) return false
        guardCmd = cmd
        guardAtNs = nowNs
        return true
    }

    /** 单测用：清掉上一条命令的记录，避免用例之间互相影响。 */
    internal fun resetOnceGuard() {
        guardCmd = null
        guardAtNs = 0
    }
}

object DesktopCommands {
    /** 这一刻这条命令能不能用（菜单灰不灰）。 */
    fun enabled(cmd: DesktopCommand, route: Route, tab: WideTab?): Boolean = when (cmd) {
        DesktopCommand.Forward -> false
        DesktopCommand.Filter -> route is Route.Library
        else -> true
    }

    /** 做这条命令；返回是否消费。菜单命令是 AWT 事件、不经过 Compose 输入，顺手刷新闲置钟（X05）。 */
    fun run(cmd: DesktopCommand, graph: AppGraph, nav: WideNav, chrome: PageChrome, idle: IdleClock? = null): Boolean {
        idle?.touch()
        return when (cmd) {
            DesktopCommand.Search -> {
                DesktopKeys.openSearch(graph)
                true
            }
            DesktopCommand.Settings -> {
                if (graph.nav.route !is Route.Settings) graph.nav.navigate(Route.Settings())
                true
            }
            DesktopCommand.Reprint -> {
                nav.onReprint?.invoke()
                nav.onReprint != null
            }
            DesktopCommand.Back -> {
                graph.nav.handleBack()
                true
            }
            DesktopCommand.Forward -> false
            DesktopCommand.GoHome -> go(nav, graph, WideTab.Home)
            DesktopCommand.GoDiscover -> go(nav, graph, WideTab.Discover)
            DesktopCommand.GoLibs -> go(nav, graph, WideTab.Libs)
            DesktopCommand.GoColls -> go(nav, graph, WideTab.Colls)
            DesktopCommand.GoMe -> go(nav, graph, WideTab.Me)
            DesktopCommand.Filter -> DesktopKeys.onFilter?.invoke() ?: false
            DesktopCommand.Favorite -> DesktopKeys.target?.let { t ->
                val id = if (t.kind == com.gugaart.ui.data.Kind.Episode) t.seriesId ?: t.id else t.id
                graph.toggleFavorite(chrome, id, chrome.isFavorite(id, t.favorite && t.kind != com.gugaart.ui.data.Kind.Episode))
                true
            } ?: false
            DesktopCommand.Played -> DesktopKeys.target?.let { t ->
                graph.togglePlayed(chrome, t.id, chrome.isPlayed(t.id, t.played))
                true
            } ?: false
        }
    }

    private fun go(nav: WideNav, graph: AppGraph, tab: WideTab): Boolean {
        nav.tab = tab
        if (graph.nav.route !is Route.Home) graph.nav.reset(Route.Home)
        return true
    }
}