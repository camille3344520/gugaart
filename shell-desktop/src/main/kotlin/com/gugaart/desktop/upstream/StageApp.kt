/*
 * 复制自上游壳（docs/adr/0002-player-wiring-in-repo.md §6，登记在 docs/upstream-sync.md §5.1）：
 *   来源  gugugagaemby/native/desktopApp/src/main/kotlin/com/gugugaga/desktop/StageApp.kt
 *   提交  1e19741a（upstream.lock）
 *   改动  package；界面根 GuguApp → GugaartApp（外面可包一层 wrap）；去掉基准测试用的 BenchFrameClock()；StageOptions 加 uiMode / wrap；desktopDeps 用本壳的（com.gugaart.desktop.desktopDeps）
 * 不要在这里改行为：上游改了来源文件就把同样的改动搬过来（git -C <上游> diff <lock>..HEAD -- <来源文件>）。
 */
package com.gugaart.desktop.upstream

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.isImmersiveRoute
import com.gugugaga.app.nav.isPlayerRoute
import com.gugugaga.app.nav.pageTitleForPath
import com.gugugaga.app.platform.WindowControls
import com.gugugaga.app.platform.installCrashReporting
import com.gugugaga.app.settings.uiSettings
import com.gugaart.desktop.desktopDeps
import com.gugaart.ui.GugaartApp
import com.gugugaga.app.ui.effectiveTheme
import com.gugugaga.desktop.player.VideoStage
import com.gugugaga.ds.theme.ThemeName
import java.awt.Cursor
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.util.Properties

/** Window base colours = `@gugu/ui --bg` (docs/desktop.md §3): what shows where no page / frame is drawn. */
val NEUTRAL_DARK = java.awt.Color(0x0a, 0x0a, 0x12)
val NEUTRAL_LIGHT = java.awt.Color(0xef, 0xee, 0xec)
fun hexOf(c: java.awt.Color) = "#%02x%02x%02x".format(c.red, c.green, c.blue)

/**
 * Minimise / maximise / fullscreen / close of the window stack for the drawn title bar and the player, mirrored into
 * Compose state ([sync] after every stage change). The title strip drags the stack (device px, both windows in one
 * `DeferWindowPos`), double click maximises (the work area), dragging a maximised window restores it under the cursor.
 */
class StageWindowControls(private val stage: () -> VideoStage?, private val onClose: () -> Unit) : WindowControls {
    private var max by mutableStateOf(false)
    private var fs by mutableStateOf(false)
    /** 窗口置顶 (T06): Compose state so the player's pin follows the window; the stage keeps the real flag (fullscreen restores it). */
    private var pinned by mutableStateOf(false)
    override val maximized: Boolean get() = max
    override val fullscreen: Boolean get() = fs
    override val alwaysOnTop: Boolean get() = pinned

    fun sync() {
        val s = stage() ?: return
        max = s.isMaximized
        fs = s.isFullscreen
        pinned = s.alwaysOnTop
    }

    override fun minimize() {
        stage()?.minimize()
    }

    override fun toggleMaximize() {
        stage()?.toggleMaximized()
        sync()
    }

    override fun close() = onClose()

    override fun setFullscreen(on: Boolean) {
        stage()?.setFullscreen(on)
        sync()
    }

    override fun setAlwaysOnTop(on: Boolean) {
        pinned = on
        stage()?.setAlwaysOnTop(on)
    }

    @Composable
    override fun DragArea(modifier: Modifier, content: @Composable () -> Unit) {
        Box(
            modifier.pointerInput(Unit) {
                var lastDownAt = 0L
                awaitEachGesture {
                    // the title buttons consume their own presses: only the empty strip drags
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val s = stage() ?: return@awaitEachGesture
                    val t = down.uptimeMillis
                    if (t - lastDownAt < 450) {
                        lastDownAt = 0L
                        toggleMaximize()
                        return@awaitEachGesture
                    }
                    lastDownAt = t
                    s.dragBegin()
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.changes.none { it.pressed }) break
                        s.dragUpdate()
                    }
                    s.dragEnd()
                    sync()
                }
            },
        ) { content() }
    }
}

/** The app inside the stack's UI window: the shared app + the resize border + the theme-following neutral base. */
@Composable
fun StageContent(stage: VideoStage, graph: AppGraph, controls: StageWindowControls, mpv: MpvHost, wrap: @Composable (@Composable () -> Unit) -> Unit = { it() }) {
    val settings by graph.settings.state.collectAsState()
    val route = graph.nav.route
    val dark = effectiveTheme(settings.uiSettings.theme, isSystemInDarkTheme(), isImmersiveRoute(route) || isPlayerRoute(route)) == ThemeName.Dark
    LaunchedEffect(dark) {
        val c = if (dark) NEUTRAL_DARK else NEUTRAL_LIGHT
        stage.setNeutral(c)
        mpv.setBackground(hexOf(c))
    }
    val title = "${pageTitleForPath(graph.nav.pathname)} · 咕咕嘎嘎"
    LaunchedEffect(title) {
        stage.frame.title = title
        stage.ui.title = title
    }
    Box(Modifier.fillMaxSize()) {
        wrap { GugaartApp(graph) }
        if (!controls.fullscreen && !controls.maximized) ResizeBorder(stage)
    }
}

/** 5dp grab strips on the four edges and 12dp corners: resizing in device px, both windows in one batch. */
@Composable
private fun BoxScope.ResizeBorder(stage: VideoStage) {
    val e = 5.dp
    val c = 12.dp
    val L = VideoStage.Edge.LEFT
    val T = VideoStage.Edge.TOP
    val R = VideoStage.Edge.RIGHT
    val B = VideoStage.Edge.BOTTOM
    Grip(stage, L, Cursor.W_RESIZE_CURSOR, Modifier.align(Alignment.CenterStart).width(e).fillMaxHeight())
    Grip(stage, R, Cursor.E_RESIZE_CURSOR, Modifier.align(Alignment.CenterEnd).width(e).fillMaxHeight())
    Grip(stage, T, Cursor.N_RESIZE_CURSOR, Modifier.align(Alignment.TopCenter).height(e).fillMaxWidth())
    Grip(stage, B, Cursor.S_RESIZE_CURSOR, Modifier.align(Alignment.BottomCenter).height(e).fillMaxWidth())
    Grip(stage, L or T, Cursor.NW_RESIZE_CURSOR, Modifier.align(Alignment.TopStart).size(c))
    Grip(stage, R or T, Cursor.NE_RESIZE_CURSOR, Modifier.align(Alignment.TopEnd).size(c))
    Grip(stage, L or B, Cursor.SW_RESIZE_CURSOR, Modifier.align(Alignment.BottomStart).size(c))
    Grip(stage, R or B, Cursor.SE_RESIZE_CURSOR, Modifier.align(Alignment.BottomEnd).size(c))
}

@Composable
private fun Grip(stage: VideoStage, edges: Int, cursor: Int, modifier: Modifier) {
    Box(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(cursor)))
            .pointerInput(edges) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    stage.resizeBegin(edges)
                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { it.consume() }
                        if (ev.changes.none { it.pressed }) break
                        stage.resizeUpdate()
                    }
                    stage.resizeEnd()
                }
            },
    )
}

/**
 * Window position / size / maximised, persisted like the Electron shell's `window-state.json` (user units = dp at the
 * monitor's scale). A saved rect that is no longer on any screen falls back to the default (centred 1280 x 800).
 */
class WindowStateStore(private val file: File) {
    data class Saved(val bounds: Rectangle, val maximized: Boolean)

    fun load(): Saved? = runCatching {
        if (!file.isFile) return null
        val p = Properties().apply { file.inputStream().use(::load) }
        val r = Rectangle(p.getProperty("x").toInt(), p.getProperty("y").toInt(), p.getProperty("w").toInt(), p.getProperty("h").toInt())
        Saved(r, p.getProperty("maximized") == "true")
    }.getOrNull()

    fun save(s: Saved) {
        runCatching {
            file.parentFile?.mkdirs()
            val p = Properties()
            p["x"] = s.bounds.x.toString()
            p["y"] = s.bounds.y.toString()
            p["w"] = s.bounds.width.toString()
            p["h"] = s.bounds.height.toString()
            p["maximized"] = s.maximized.toString()
            file.outputStream().use { p.store(it, "咕咕嘎嘎播放器 window state") }
        }
    }

    companion object {
        /** [saved] when it is still visible on some screen (>= 120 x 80 of it), else a centred default that fits the primary screen. */
        fun initialBounds(saved: Rectangle?, default: java.awt.Dimension = java.awt.Dimension(1280, 800)): Rectangle {
            val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
            if (saved != null && saved.width >= 320 && saved.height >= 240) {
                val visible = ge.screenDevices.any { d ->
                    val i = d.defaultConfiguration.bounds.intersection(saved)
                    !i.isEmpty && i.width >= 120 && i.height >= 80
                }
                if (visible) return saved
            }
            val b = ge.maximumWindowBounds
            val w = minOf(default.width, (b.width * 0.92).toInt())
            val h = minOf(default.height, (b.height * 0.92).toInt())
            return Rectangle(b.x + (b.width - w) / 2, b.y + (b.height - h) / 2, w, h)
        }
    }
}

/** How the window-stack app starts (the e2e harness passes its own data dir, bounds and debug options). */
data class StageOptions(
    val dataDir: File,
    val route: com.gugugaga.app.nav.Route,
    val backend: com.gugugaga.ds.glass.GlassBackend? = null,
    /** null = the build's own (`DebugBuildConfig`); the e2e harness (test source set only) passes the mock account. */
    val debug: com.gugugaga.app.platform.DebugOptions? = null,
    /** null = the persisted / default bounds. */
    val bounds: Rectangle? = null,
    val persistWindow: Boolean = true,
    val alwaysOnTop: Boolean = false,
    /** What [StageSession.quit] does last (the app exits the JVM; the e2e harness keeps running). */
    val onExit: () -> Unit = { kotlin.system.exitProcess(0) },
    /** gugaart：用哪一端的版式开窗口（--ui=phone|tv 只为在电脑上看版式 / 验证手机播放器）。 */
    val uiMode: com.gugugaga.ds.theme.UiMode = com.gugugaga.ds.theme.UiMode.Desktop,
    /** gugaart：包在界面根外面的一层（调试探针用来提供测试缝），应用里什么都不包。 */
    val wrap: @Composable (@Composable () -> Unit) -> Unit = { it() },
)

/**
 * The Windows app on the N6c window stack (docs/native-player-windows.md §1): an opaque neutral stage (taskbar button,
 * mpv's `--wid` canvas) under a transparent, owned Compose window that hosts the WHOLE app. Pages paint their own opaque
 * backdrop; only the player route leaves a transparent hole over the video. mpv starts on the first play, never before.
 */
class StageSession private constructor(val options: StageOptions) {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    @Volatile var stage: VideoStage? = null
        private set
    val controls = StageWindowControls({ stage }, ::quit)
    val mpv = MpvHost(
        host = { stage?.takeIf { it.canvasHwnd != 0L } },
        extraArgs = listOf(
            "--gpu-shader-cache-dir=" + File(options.dataDir, "cache/mpv-shader").apply { mkdirs() }.absolutePath,
            "--vd-lavc-threads=0",
        ),
    )
    val vlc = VlcHost(
        host = { stage?.takeIf { it.canvasHwnd != 0L } },
    )
    val graph: AppGraph = AppGraph(
        desktopDeps(options.dataDir, options.uiMode, options.backend, controls, DesktopPlayerPlatform(mpv, vlc, keyWindow = { stage?.ui }, hasWindow = { stage != null }), options.debug, onQuit = ::quit),
        scope,
        options.route,
    )

    init {
        // T22: the same lines the phone puts in logcat also reach the console of the Windows build (the player's `timing`
        // line, the danmaku 403 diagnostics). Already masked by AppLog.
        graph.log.sink = { level, message -> println("GuguPlayer [$level] $message") }
    }
    private val windowState = WindowStateStore(File(options.dataDir, "window-state.properties"))
    @Volatile private var quitting = false

    private fun open() {
        val saved = if (options.persistWindow) windowState.load() else null
        val bounds = options.bounds ?: WindowStateStore.initialBounds(saved?.bounds)
        // every key of the UI window goes to the player's key map first (keys.ts listens on `window`): the player keys work
        // wherever the Compose focus is (measured N7: after mpv's first frame re-activated the stack the focus sat outside the page)
        val s = VideoStage(graph.deps.productName, NEUTRAL_DARK, bounds, alwaysOnTop = options.alwaysOnTop, onKey = { e -> com.gugugaga.app.player.PlayerKeyRouter.dispatch(e) }, onCloseRequest = ::quit) { st ->
            StageContent(st, graph, controls, mpv, options.wrap)
        }
        stage = s
        s.onStateChange = { controls.sync() }
        s.show()
        if (options.bounds == null && saved?.maximized == true) s.setMaximized(true)
        s.ui.toFront()
        s.focusUiContent()
        controls.sync()
    }

    /** Save the window, quit OUR mpv (polite quit, then our PID only), close both windows, then [StageOptions.onExit]. */
    fun quit() {
        if (quitting) return
        quitting = true
        val s = stage
        if (s != null && options.persistWindow) {
            val r = s.normalDeviceRect
            val k = s.scale
            if (r != null) windowState.save(WindowStateStore.Saved(Rectangle((r.left / k).toInt(), (r.top / k).toInt(), (r.width / k).toInt(), (r.height / k).toInt()), s.isMaximized))
        }
        s?.ui?.isVisible = false
        s?.frame?.isVisible = false
        Thread({
            runCatching { kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeoutOrNull(4_000) { mpv.shutdown(); vlc.shutdown() } } }
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            javax.swing.SwingUtilities.invokeLater {
                runCatching { s?.dispose() }
                options.onExit()
            }
        }, "gugu-quit").start()
    }

    companion object {
        /** Builds the session and opens the stack on the AWT event thread; returns once it is on screen. */
        fun start(o: StageOptions): StageSession {
            val session = StageSession(o)
            installDesktopCrashGuard(session.graph)
            if (javax.swing.SwingUtilities.isEventDispatchThread()) session.open() else javax.swing.SwingUtilities.invokeAndWait { session.open() }
            return session
        }
    }
}

/**
 * T06: an exception nothing else caught is written to the run log (设置 › 扩展) and toasted instead of ending the app. The log /
 * toast come from [installCrashReporting]; anything before the graph existed (or a second failure while reporting) still goes to
 * stderr so a terminal user sees it. Compose Desktop keeps its last frame after a render failure, which is exactly why swallowing
 * here is better than the default handler's stack trace + dead window.
 */
internal fun installDesktopCrashGuard(graph: AppGraph) {
    installCrashReporting(graph)
    Thread.setDefaultUncaughtExceptionHandler { thread, t ->
        if (!com.gugugaga.app.platform.CrashGuard.report(t)) {
            System.err.println("未捕获的异常（${thread.name}）：$t")
            t.printStackTrace()
        }
    }
}
