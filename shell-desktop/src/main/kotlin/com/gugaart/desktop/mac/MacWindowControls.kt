package com.gugaart.desktop.mac

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.gugugaga.app.platform.WindowControls

/**
 * macOS 的窗口控制（ADR 0004 §2.2）：系统标题栏 + 红绿灯。最小化 / 缩放走 Compose 的 [WindowState]；**全屏**（系统的全屏 Space）
 * 直接对 NSWindow 发 `toggleFullScreen:`（[MacFullscreen]：Compose 的 `placement = Floating` 在 macOS 上退不出全屏，P20），
 * 而且只在窗口真实的状态和要的不一样、上一次切换已经过去 [FullscreenRequests.SETTLE_MS] 时才发。红绿灯本身由系统画、系统管，这里不画也不拦。
 *
 * [attach] 在 `Window` 组合里把 AWT 窗口、Compose 窗口状态和 `WindowScope` 交进来（`DragArea` 要 `WindowScope`
 * 才能用 [WindowDraggableArea]）。[sync] 在系统改了窗口状态（绿灯、缩放、全屏过渡）后把状态镜像回来：全屏读窗口自己的 `styleMask`。
 */
internal class MacWindowControls(
    private val onClose: () -> Unit,
    private val native: MacFullscreen = MacFullscreen.System,
    private val requests: FullscreenRequests = FullscreenRequests(),
    /** NSWindow 的指针；默认取 [attach] 交进来的 AWT 窗口的（测试换掉）。 */
    handleOf: (() -> Long?)? = null,
) : WindowControls {
    private var win: java.awt.Window? = null
    private var st: WindowState? = null
    private var scope: WindowScope? = null
    private val handle: () -> Long? = handleOf ?: { (win as? androidx.compose.ui.awt.ComposeWindow)?.windowHandle?.takeIf { it != 0L } }

    private var max by mutableStateOf(false)
    private var fs by mutableStateOf(false)
    private var top by mutableStateOf(false)

    override val maximized: Boolean get() = max
    override val fullscreen: Boolean get() = fs
    override val alwaysOnTop: Boolean get() = top

    fun attach(window: java.awt.Window, state: WindowState, windowScope: WindowScope) {
        win = window
        st = state
        scope = windowScope
        // 全屏过渡改的是窗口的大小和 JDK 的窗口状态：每次都重读一遍（Compose 的 placement 也在这时跟上）
        window.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) = sync()
        })
        window.addWindowStateListener { sync() }
        sync()
    }

    fun sync() {
        val s = st
        val native = handle()?.let(native::isFullScreen)
        fs = native ?: (s?.placement == WindowPlacement.Fullscreen)
        max = !fs && s?.placement == WindowPlacement.Maximized
        top = win?.isAlwaysOnTop == true
    }

    override fun minimize() {
        st?.isMinimized = true
    }

    override fun toggleMaximize() {
        // 全屏里没有「缩放」（双击顶栏不该把全屏的窗口拿去 zoom:）
        if (fs) return
        val s = st ?: return
        s.placement = if (s.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
        sync()
    }

    override fun close() = onClose()

    override fun setFullscreen(on: Boolean) {
        val h = handle()
        val actual = h?.let(native::isFullScreen)
        if (h == null || actual == null) {
            // 还没有原生窗口（不会发生在真窗口里）：退回 Compose 的状态
            st?.placement = if (on) WindowPlacement.Fullscreen else WindowPlacement.Floating
            sync()
            return
        }
        if (requests.request(on, actual)) {
            native.toggle(h)
            // 过渡中途的 resize 事件已经会触发 sync；过渡结束后再各读一次，万一最后一个事件来得比 styleMask 早也不会留着旧值
            if (win != null) for (ms in longArrayOf(FullscreenRequests.SETTLE_MS + 300, 3_000)) {
                javax.swing.Timer(ms.toInt()) { sync() }.apply { isRepeats = false }.start()
            }
        }
    }

    override fun setAlwaysOnTop(on: Boolean) {
        win?.isAlwaysOnTop = on
        top = on
    }

    /** 报头整条可拖（系统的可拖区）；双击 = 缩放（和 Windows 的标题栏一致）。 */
    @Composable
    override fun DragArea(modifier: Modifier, content: @Composable () -> Unit) {
        val sc = scope
        if (sc == null) {
            Box(modifier) { content() }
            return
        }
        Box(
            modifier.pointerInput(Unit) {
                var lastDownAt = 0L
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t = down.uptimeMillis
                    if (t - lastDownAt < 450) {
                        lastDownAt = 0L
                        toggleMaximize()
                    } else {
                        lastDownAt = t
                    }
                }
            },
        ) {
            with(sc) { WindowDraggableArea { content() } }
        }
    }
}
