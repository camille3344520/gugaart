package com.gugaart.desktop.mac

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.gugaart.desktop.AppIcon
import com.gugaart.desktop.backupFileHost
import com.gugaart.desktop.fontImportHost
import com.gugaart.desktop.upstream.WindowStateStore
import com.gugaart.desktop.upstream.installDesktopCrashGuard
import com.gugaart.ui.GugaartApp
import com.gugaart.ui.backup.LocalBackupFiles
import com.gugaart.ui.data.LocalFontImport
import com.gugaart.ui.desktop.DesktopHost
import com.gugaart.ui.desktop.LocalDesktopHost
import com.gugaart.ui.desktop.ModifierGlyph
import com.gugaart.ui.desktop.SelfUpdate
import com.gugaart.ui.desktop.WindowKeysStyle
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.nav.pageTitleForPath
import com.gugugaga.app.player.PlayerKeyRouter
import com.gugugaga.ds.glass.GlassBackend
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

/** Mac 壳的启动参数（`Main.kt` 按 `os.name` 分流后传进来；和 Windows 的 `StageOptions` 同一批开关）。 */
internal data class MacOptions(
    val dataDir: File,
    val route: Route = Route.Home,
    val backend: GlassBackend? = null,
    val uiMode: UiMode = UiMode.Desktop,
    /** null = 记住的位置 / 大小；给了就不记（和 Windows 一样）。 */
    val bounds: Rectangle? = null,
    /** 开发运行的调试开关（Mock Emby 的夹具账号，`-Pgugaart.mockAccount=`）；打出来的包里没有。 */
    val debug: com.gugugaga.app.platform.DebugOptions = com.gugugaga.app.platform.DebugOptions.None,
    /** 开发运行的实测探针（`--probe=<目录>`）：截图放这里；null = 不跑。 */
    val probeDir: File? = null,
    val probeRounds: Int = 1,
    /** 探针多做一遍：弹幕、字幕、音轨面板、倍速、快进、暂停、全屏、下一集。 */
    val probeExtras: Boolean = false,
    val probeSearch: String? = null,
    val probeSecs: Int = 40,
    val probeStrm: String? = null,
    /** 真窗口验收（`--accept=<场景+场景|all>`，只在开发运行）：要跑的场景；null = 不跑。 */
    val acceptScenarios: List<String>? = null,
    /** 验收的输出目录（`--accept-out=`）：report.json / report.md / 截图 / 联系表。null = 不跑验收。 */
    val acceptOut: File? = null,
    /** 把结果并进输出目录里已有的报告（`--accept-merge=true`，脚本第二次起应用时给）。 */
    val acceptMerge: Boolean = false,
)

/**
 * macOS 的窗口（ADR 0004 §2.2）：一扇 Compose `Window`，系统标题栏（透明、内容到顶、红绿灯由系统画），
 * 不用上游的 Win32 窗口栈 `VideoStage`。播放器 = libmpv 进程内（`MacPlayerPlatform`），画面是这扇窗层树里 Compose 之下的一个层。
 *
 * 不做（M2）：菜单栏、红绿灯让位（界面层右上还会画三个自绘窗口键，M2-1 去掉）、失焦变淡的界面效果（M2-1）、
 * mpv 之外的内核。关窗 = 退出（P-mac-14 待定之前照 Windows）。
 */
internal class MacSession private constructor(val options: MacOptions) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    val controls = MacWindowControls(::quit)
    @Volatile private var composeWindow: androidx.compose.ui.awt.ComposeWindow? = null
    private val engines: MacEngines? = macEngines(
        window = { composeWindow },
        userAgent = com.gugaart.desktop.upstream.DEFAULT_MPV_USER_AGENT,
        log = { println("GuguPlayer [mpv] $it") },
    )
    private val playerPlatform = MacPlayerPlatform(engines)

    /**
     * 「播放 › 画中画」的钩子（M2-4 的菜单栏接它；D-lead-20、对齐清单 P18）：null = 这台 Mac 做不了画中画，菜单里**没有**这一项（不是灰的）；
     * 有 = 菜单项点了调 `toggle()`，勾选状态读 `active`（和播放器顶栏的钮是同一个对象）。
     */
    val pictureInPicture: com.gugugaga.app.player.PictureInPicture? get() = playerPlatform.pictureInPicture
    val graph: AppGraph = AppGraph(
        macDeps(options.dataDir, options.uiMode, options.backend, controls, player = playerPlatform, debug = options.debug, onQuit = ::quit),
        scope,
        // 探针 / 验收自己导航（先等会话恢复）
        if (options.probeDir != null || options.acceptOut != null) Route.Home else options.route,
    )
    private val probe = options.probeDir?.let { MacViewportProbe(it, options.route, options.probeRounds, options.probeExtras, options.probeSearch, options.probeSecs, options.probeStrm, engines) }

    /** 真窗口验收（开发开关；`docs/acceptance-mac.md`）。 */
    private val accept = options.acceptOut?.let { com.gugaart.desktop.mac.accept.AcceptRunner(options.acceptScenarios.orEmpty(), it, options.acceptMerge) }

    /** 「正在播放」和媒体键（MP-6）：播放器页把它的控制器交过来，离开播放器清掉。 */
    private val nowPlaying = MacNowPlaying(
        scope = scope,
        center = SystemNowPlayingCenter,
        artworkUrl = { snap ->
            val item = snap.item
            val client = graph.session.session.value?.client
            if (item == null || client == null) null
            else client.itemImage(item, com.gugugaga.core.ImageKind.Poster, com.gugugaga.core.ImageUrlOptions(maxWidth = 600, format = com.gugugaga.core.ImageFormat.Jpg))
        },
        fetch = com.gugugaga.app.data.HttpFetcher()::fetch,
    )

    private val windowState = WindowStateStore(File(options.dataDir, "window-state.properties"))
    /** 给了 --bounds、或者在跑验收（固定 1280 × 800 居中，不碰会话目录里记着的窗口状态）时不记窗口位置。 */
    private val persistWindow = options.bounds == null && options.acceptOut == null
    @Volatile private var quitting = false
    @Volatile private var awtWindow: java.awt.Window? = null
    private var exitApp: (() -> Unit)? = null

    init {
        graph.log.sink = { level, message -> println("GuguPlayer [$level] $message") }
        // 装好的应用核对随包的内核用（accept-mac --package）：用的是哪一份 libmpv
        println("GuguPlayer [mpv] libmpv: ${engines?.libraryPath ?: "none"}")
    }

    /** 阻塞到应用退出（`application` 的事件循环）。 */
    fun run() {
        enableVideoUnderCompose()
        installAppHandlers()
        installDesktopCrashGuard(graph)
        val saved = if (persistWindow) windowState.load() else null
        application {
            exitApp = { exitApplication() }
            val state = rememberWindowState(
                placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = saved?.bounds?.let { WindowPosition(it.x.dp, it.y.dp) } ?: WindowPosition(Alignment.Center),
                size = saved?.bounds?.let { DpSize(it.width.dp, it.height.dp) } ?: DpSize(1280.dp, 800.dp),
            )
            var active by remember { mutableStateOf(true) }
            Window(
                onCloseRequest = ::quit,
                state = state,
                title = "${pageTitleForPath(graph.nav.pathname)} · 咕咕嘎嘎",
                undecorated = false,
                onPreviewKeyEvent = { PlayerKeyRouter.dispatch(it) },
            ) {
                // 1.5.0：「片库 › 换服务器」列出全部已保存的服务器（和顶栏胶囊同一份数据层列表）
                val savedServers by androidx.compose.runtime.remember(graph) { com.gugaart.ui.servers.sharedServerSwitcher(graph) }.entries.collectAsState()
                // M2-4：系统菜单栏（七个菜单；命令走命令表 / 播放接收者，不碰引擎）
                MacMenuBar(
                    route = graph.nav.route,
                    tab = null,
                    playing = graph.nav.route is com.gugugaga.app.nav.Route.Play,
                    onAction = ::macMenuAction,
                    // A10：「播放 › 画中画」按能力出现，勾选跟着系统的画中画
                    pip = pictureInPicture != null,
                    pipActive = pictureInPicture?.active == true,
                    servers = savedServers,
                )
                // M2-10：Dock 续看随会话就绪 / 切换刷新（启动时会话还没恢复，先印搜索+付印，恢复后补续看）
                LaunchedEffect(Unit) {
                    graph.session.session.collect { refreshDockMenu() }
                }
                // 离开播放器（续看列表可能变了）也刷新一次
                LaunchedEffect(graph.nav.route) {
                    if (graph.nav.route !is com.gugugaga.app.nav.Route.Play) refreshDockMenu()
                }
                // 付印新刊挂上 / 撤下（首页组合里挂 WideNav.onReprint）时，Dock 那条跟着灰 / 亮（和应用菜单同步）
                LaunchedEffect(Unit) {
                    snapshotFlow { com.gugaart.ui.desktop.DesktopCommandSink.reprintReady?.invoke() == true }
                        .collect { refreshDockMenu() }
                }
                // 号外出现且应用不在前台时发一条通知
                LaunchedEffect(Unit) {
                    var lastNotified: String? = null
                    graph.update.state.collect { st ->
                        if (st is com.gugugaga.app.update.UpdateState.Available && awtWindow?.isActive == false && st.release.version != lastNotified) {
                            lastNotified = st.release.version
                            runCatching { ProcessBuilder(notificationCommand("咕咕嘎嘎", "号外：有新版本 ${st.release.version}")).start() }
                        }
                    }
                }
                LaunchedEffect(Unit) {
                    val w = window
                    awtWindow = w
                    composeWindow = w
                    probe?.run(graph, { awtWindow }, ::quit)
                    accept?.start(
                        com.gugaart.desktop.mac.accept.AcceptHost(
                            graph = graph,
                            window = { composeWindow },
                            mpv = { name -> kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(2_000) { engines?.property(name) } } },
                            engineAvailable = engines?.available == true,
                            quit = ::quit,
                            engines = engines,
                            dataDir = options.dataDir,
                        ),
                    )
                    // 画面在 Compose 之下：透过去看到的最底下是窗口自己的底色，用墨黑（系统全屏里换纯黑，见下）
                    w.background = WINDOW_INK
                    // 内容延伸到窗口最顶，系统只画红绿灯（JDK 标准属性，ADR 0004 §2.2）
                    w.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    w.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    w.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                    w.minimumSize = Dimension(1100, 720)
                    AppIcon.apply(w)
                    // M1-6（X06）：开发运行时 Dock / ⌘Tab 用应用自己的图标，不是 Java 的咖啡杯；不支持就跳过，不抛
                    runCatching {
                        val tb = java.awt.Taskbar.getTaskbar()
                        if (java.awt.Taskbar.isTaskbarSupported() && tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                            MacSession::class.java.getResourceAsStream("/gugaart/icon-512.png")?.use { tb.iconImage = javax.imageio.ImageIO.read(it) }
                        }
                    }
                    controls.attach(w, state, this@Window)
                    // 播放器里开播的提示要知道这块屏幕出不出得了 HDR（MP-8、D-mac-6）：窗口出来问一次，挪了地方（可能换了屏幕）再问；不在事件线程上问
                    engines?.takeIf { it.available }?.let { e ->
                        fun ask() = scope.launch(kotlinx.coroutines.Dispatchers.IO) { e.refreshDisplay() }
                        e.displayHdr?.let { flow -> scope.launch { flow.collect { hdr -> com.gugaart.ui.player.DeviceAv.caps = macAvCaps(hdr) } } }
                        ask()
                        w.addComponentListener(object : java.awt.event.ComponentAdapter() {
                            override fun componentMoved(ev: java.awt.event.ComponentEvent?) { ask() }
                        })
                    }
                    w.addWindowFocusListener(object : WindowAdapter() {
                        override fun windowGainedFocus(e: WindowEvent?) { active = true }
                        override fun windowLostFocus(e: WindowEvent?) { active = false }
                    })
                }
                // 页面换了打一行路径（不带查询串：登录页的 ?server= 里可能有服务器地址）；accept-mac --package 靠它认出登录页
                val pathOnly = graph.nav.pathname.substringBefore('?')
                LaunchedEffect(pathOnly) { println("GuguPlayer [nav] $pathOnly") }
                // 会话恢复的结果（SignedOut = 电脑版式出登录页；不打用户名、服务器地址）
                LaunchedEffect(graph) { graph.session.status.collect { println("GuguPlayer [session] $it") } }
                // 菜单栏装好以后打一行顶层菜单的标题和应用菜单里的项（D-lead-39：只该有一个「咕咕嘎嘎」）；
                // 读的是进程自己的 NSApp.mainMenu，不要辅助功能授权——锁屏下 accept-mac --package 靠它核对
                // 菜单栏装好以后：先按系统语言把系统应用菜单中文化（1.4.0），再打一行顶层菜单 + 应用菜单项核对
                // （D-lead-39：只该有一个「咕咕嘎嘎」）；读进程自己的 NSApp.mainMenu，不要辅助功能授权——锁屏下 accept-mac --package 靠它
                LaunchedEffect(Unit) { scope.launch(kotlinx.coroutines.Dispatchers.IO) { kotlinx.coroutines.delay(4_000); localizeSystemAppMenu(); logMenuBar() } }
                // 换页时菜单栏会重装：再中文化一次，保证应用菜单不回到 JDK 的英文（幂等，已是中文就不动）
                LaunchedEffect(graph.nav.route) { scope.launch(kotlinx.coroutines.Dispatchers.IO) { kotlinx.coroutines.delay(300); localizeSystemAppMenu() } }
                // 离开播放器：「正在播放」的卡片消失、媒体键摘掉
                val onPlayer = graph.nav.pathname.startsWith("/play")
                LaunchedEffect(onPlayer) { if (!onPlayer) nowPlaying.detach() }
                // 系统改了窗口状态（绿灯进全屏、⌥ 点绿灯缩放）→ 镜像给界面
                LaunchedEffect(state) {
                    snapshotFlow { state.placement }.collect { controls.sync() }
                }
                // M2-6：系统全屏里窗口底色纯黑（和系统涂黑的刘海那一条、播放器的全屏页边 DesktopSpec.FULLSCREEN_BLACK 接成一片；
                // 过渡动画里、Compose 还没重画的那一帧透出来的也是黑）
                LaunchedEffect(Unit) {
                    snapshotFlow { controls.fullscreen }.collect { fs -> awtWindow?.background = if (fs) java.awt.Color.BLACK else WINDOW_INK }
                }
                CompositionLocalProvider(
                    LocalDesktopHost provides DesktopHost(
                        windowKeys = WindowKeysStyle.SystemLeft,
                        leadingInsetPx = 85f, // TODO M2-1: DesktopSpec.MAC_LEADING_PX
                        active = active,
                        modifier = ModifierGlyph.Cmd,
                        platformName = "macOS",
                        selfUpdate = SelfUpdate.OpenDmg,
                    ),
                    LocalFontImport provides fontImportHost(options.dataDir),
                    LocalBackupFiles provides backupFileHost(),
                    // 播放器页建好控制器就交过来（每次重组都会叫，同一个控制器只接一次）
                    com.gugaart.ui.player.LocalPlaySessionSink provides {
                        nowPlaying.attach(it.controller)
                        accept?.onController(it.controller)
                        // 画中画小窗自己的播放 / 暂停、关小窗 = 暂停、进度条和 ±跳：走这一页的控制器（和键盘、「正在播放」同一条路）
                        engines?.bindPipPlayback(it.controller)
                    },
                    // 验收缝：根界面的那一个画报设置 store 交给运行器（改设置要改界面正在用的那一份）
                    com.gugaart.ui.screens.settings.LocalGugaSettingsSink provides { s -> accept?.onSettingsStore(s) },
                ) {
                    // 视频层在 Compose 层之下（ADR 0004 §4「实测（MP-0）」）：Compose 会先用窗口色把画布铺满，根上清一次，
                    // 上面没画东西的地方（播放器页的洞）才透得出画面；别的页面自己铺不透明的底，不受影响。
                    Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }) {
                        val p = probe
                        if (p != null) p.wrap { GugaartApp(graph) } else GugaartApp(graph)
                    }
                }
            }
        }
        kotlin.system.exitProcess(0)
    }

    /**
     * 系统的应用菜单 / Dock 的「退出」、`⌘Q`、「关于」「设置… ⌘,」（任务单 M2-4，D-lead-14 / D-lead-39）：不设处理器时 JDK 的「退出」直接结束进程，
     * 不写回窗口状态、不按正常流程停内核。退出走 [quit]（它自己结束进程，所以这里取消系统的那一次）。
     * 应用菜单只有系统这一个（菜单栏上不再有我们自己的「咕咕嘎嘎」菜单），关于 / 设置都挂在它上面。
     */
    private fun installAppHandlers() {
        runCatching {
            if (!java.awt.Desktop.isDesktopSupported()) return
            val d = java.awt.Desktop.getDesktop()
            if (d.isSupported(java.awt.Desktop.Action.APP_QUIT_HANDLER)) d.setQuitHandler { _, response ->
                quit()
                response.cancelQuit()
            }
            if (d.isSupported(java.awt.Desktop.Action.APP_ABOUT)) d.setAboutHandler { graph.nav.navigate(com.gugugaga.app.nav.Route.Settings("about")) }
            // ⌘, 只走系统应用菜单这一条（我们自己不再出「设置…」那一项，D-lead-39）；经命令表进设置——已在设置里就不再压一层，
            // 万一窗口也收到了同一个 ⌘, 也只换一次页
            if (d.isSupported(java.awt.Desktop.Action.APP_PREFERENCES)) d.setPreferencesHandler {
                val sink = com.gugaart.ui.desktop.DesktopCommandSink.sink
                if (sink != null) sink(com.gugaart.ui.desktop.DesktopCommand.Settings)
                else if (graph.nav.route !is com.gugugaga.app.nav.Route.Settings) graph.nav.navigate(com.gugugaga.app.nav.Route.Settings())
            }
        }
    }

    /** 一行：`GuguPlayer [menu] 顶层1 | 顶层2 … ‖ 应用菜单项=可用…`（只打标题，没有用户数据）。 */
    private fun logMenuBar() {
        runCatching {
            val line = com.gugaart.desktop.mac.accept.Objc.onMain {
                val o = com.gugaart.desktop.mac.accept.Objc
                val app = o.msg(o.cls("NSApplication"), "sharedApplication") ?: return@onMain null
                val main = o.msg(app, "mainMenu") ?: return@onMain null
                val items = o.array(o.msg(main, "itemArray"))
                val titles = items.map { o.string(o.msg(it, "title")) ?: "?" }
                val appMenu = items.firstOrNull()?.let { o.msg(it, "submenu") }?.let { o.array(o.msg(it, "itemArray")) }.orEmpty()
                    .map { (o.string(o.msg(it, "title")) ?: "") to o.msgBool(it, "isEnabled") }
                    .filter { it.first.isNotBlank() }
                titles.joinToString(" | ") + " ‖ " + appMenu.joinToString(" | ") { "${it.first}=${it.second}" }
            }
            if (line != null) println("GuguPlayer [menu] $line")
        }.onFailure { println("GuguPlayer [menu] 读不到：${it.message}") }
    }

    /** 关窗 / 退出：写回窗口状态，停播放器（M1-3 接），取消协程，退出进程（P-mac-14 待定之前 = 退出）。 */
    fun quit() {
        if (quitting) return
        quitting = true
        val w = awtWindow
        if (w != null && persistWindow) {
            val b = w.bounds
            windowState.save(WindowStateStore.Saved(Rectangle(b.x, b.y, b.width, b.height), controls.maximized))
        }
        w?.isVisible = false
        nowPlaying.detach()
        Thread({
            // 释放进程内的 mpv 内核；caffeinate 随本进程退出自动结束
            runCatching { kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(4_000) { engines?.shutdown() } } }
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            javax.swing.SwingUtilities.invokeLater {
                val exit = exitApp
                if (exit != null) exit() else kotlin.system.exitProcess(0)
            }
        }, "gugu-quit").start()
    }

    /** M2-4 菜单栏里非命令表的动作。关窗 / 退出走同一条正常退出（D-lead-14）。 */
    private fun macMenuAction(action: String) {
        when (action) {
            "about", "help" -> graph.nav.navigate(com.gugugaga.app.nav.Route.Settings("about"))
            // 「帮助 › 开源许可」= 关于下面那一页（D-lead-41）
            "licenses" -> graph.nav.navigate(com.gugugaga.app.nav.Route.Settings(com.gugaart.ui.screens.settings.OpenSourceLicenses.KEY))
            "shortcuts" -> graph.nav.navigate(com.gugugaga.app.nav.Route.Settings("playback"))
            // 1.5.0：「文件 › 切换账号 / 服务器…」= 顶栏服务器胶囊的下拉（和账号菜单那一项一样）
            "switch-server" -> com.gugaart.ui.desktop.DesktopServerMenu.open = true
            "add-server" -> graph.nav.navigate(com.gugugaga.app.nav.Route.Servers)
            "check-update" -> graph.update.checkNow()
            "quit", "close-window" -> quit()
            // 和账号菜单同一个二次确认（纸片对话框）
            "logout" -> com.gugaart.ui.desktop.DesktopAccountMenu.leaving = true
            "cast" -> com.gugaart.ui.desktop.PlayerCommandSink.castPanelRequested = true
            "minimize" -> controls.minimize()
            "always-on-top" -> controls.setAlwaysOnTop(!controls.alwaysOnTop)
            // A10：和播放器顶栏的钮同一个对象
            "pip" -> pictureInPicture?.toggle()
            // 「片库 › 换服务器」里的一台：直接切；切不过去时顶栏的下拉会打开，原因写在那一行下面
            else -> if (action.startsWith(MacMenuModel.SERVER_ACTION)) com.gugaart.ui.desktop.DesktopServerMenu.switchToKey(action.removePrefix(MacMenuModel.SERVER_ACTION))
        }
    }

    /** M2-10：Dock 菜单 = 继续观看 ≤3 + 搜索 + 付印新刊（条目生成是纯函数 [dockMenuEntries]）。 */
    private fun refreshDockMenu() {
        scope.launch {
            val resume = runCatching {
                graph.session.session.value?.client?.getResume(limit = 3)?.items.orEmpty()
            }.getOrNull().orEmpty()
            val reprintReady = com.gugaart.ui.desktop.DesktopCommandSink.reprintReady?.invoke() == true
            val entries = dockMenuItems(resumeTitlesOf(resume), reprintReady)
            javax.swing.SwingUtilities.invokeLater {
                runCatching {
                    val tb = java.awt.Taskbar.getTaskbar()
                    if (!java.awt.Taskbar.isTaskbarSupported() || !tb.isSupported(java.awt.Taskbar.Feature.MENU)) return@runCatching
                    val menu = java.awt.PopupMenu()
                    entries.forEachIndexed { i, entry ->
                        val item = java.awt.MenuItem(entry.label)
                        // 「付印新刊」不在首页时灰着（和应用菜单那条一样）；灰的项 AWT 不派发动作
                        item.isEnabled = entry.enabled
                        item.addActionListener {
                            when {
                                i < resume.size -> resume.getOrNull(i)?.let { graph.nav.navigate(com.gugugaga.app.nav.Route.Play(it.id)) }
                                entry.label == "搜索…" -> com.gugaart.ui.desktop.DesktopCommandSink.sink?.invoke(com.gugaart.ui.desktop.DesktopCommand.Search)
                                else -> com.gugaart.ui.desktop.DesktopCommandSink.sink?.invoke(com.gugaart.ui.desktop.DesktopCommand.Reprint)
                            }
                        }
                        menu.add(item)
                    }
                    tb.menu = menu
                }
            }
        }
    }

    companion object {
        /** 窗口自己的底色（画面在 Compose 之下，没画东西的地方透出它）：墨黑，和 mpv 的留边色 `#0a0a12` 同一个。 */
        private val WINDOW_INK = java.awt.Color(0x0A, 0x0A, 0x12)

        /** 造会话并开窗；阻塞到应用退出。 */
        fun start(o: MacOptions) {
            MacSession(o).run()
        }
    }
}