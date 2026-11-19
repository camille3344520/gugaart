package com.gugaart.desktop.mac

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/**
 * macOS 系统全屏（全屏 Space）的两件原生事：读 NSWindow 现在是不是全屏、请它切换一次。
 *
 * 为什么不走 Compose 的 `WindowState.placement`（P20：2026-11-18 的验收里播放器按 F 进了全屏，再按 F 8 秒内没退出来，之后一直停在全屏，
 * 可见窗口里一直有全屏才有的 `NSToolbarFullScreenWindow`）：Compose 1.12.2 的 `ComposeWindow.setPlacement(Floating)` 是两步——
 * Skiko 的 `makeFullscreen:` 异步排一个 `toggleFullScreen:`，紧接着 `setExtendedState(state & ~MAXIMIZED_BOTH)`（字节码）。
 * JDK 21 的 `CPlatformWindow` 把「窗口铺满屏幕」（`NSWindow.isZoomed`）记成 `MAXIMIZED_BOTH`（`checkZoom`），这时第二步走
 * `setWindowState(NORMAL)` → `unmaximize()` → 再给主线程排一个 `zoom:`，和退出全屏的过渡撞车（源码）。进全屏只有一步，所以进得去、退不出来。
 * 系统绿灯同款的 `toggleFullScreen:` 只有一步，验收 `window` 场景里进出都过。所以这里全屏只发这一步，Compose 的 placement 由它自己的
 * 窗口监听（`componentResized` / `windowStateChanged` 读 `styleMask`）跟上，Compose 不再替我们调 `setPlacement`。
 * 「第二步是 zoom: 撞车」是读代码得出的；真窗口上的复测要屏幕解锁（这次排查时屏幕锁着，AppKit 拒绝任何全屏过渡）。
 */
internal interface MacFullscreen {
    /** 窗口 [handle] 现在在不在系统全屏里（`styleMask` 的全屏位，过渡一开始就变）；读不到 = null。 */
    fun isFullScreen(handle: Long): Boolean?

    /** 请 AppKit 切换一次（异步排到主线程，不等；不在 AppKit 主线程和 AWT 事件线程上等任何东西）。 */
    fun toggle(handle: Long)

    /** 真的 AppKit（JNA → objc_msgSend）。 */
    object System : MacFullscreen {
        private val objc by lazy { NativeLibrary.getInstance("objc") }
        private val msgSend by lazy { objc.getFunction("objc_msgSend") }
        private val sels = java.util.concurrent.ConcurrentHashMap<String, Pointer>()
        private fun sel(name: String): Pointer = sels.getOrPut(name) { objc.getFunction("sel_registerName").invokePointer(arrayOf<Any>(name)) }

        /** NSWindowStyleMaskFullScreen = 1 << 14。 */
        private const val FULL_SCREEN_MASK = 1L shl 14

        override fun isFullScreen(handle: Long): Boolean? = runCatching {
            if (handle == 0L) return null
            (msgSend.invokeLong(arrayOf<Any>(Pointer(handle), sel("styleMask"))) and FULL_SCREEN_MASK) != 0L
        }.getOrNull()

        override fun toggle(handle: Long) {
            if (handle == 0L) return
            // 和 Skiko 自己切全屏同一种发法（performSelectorOnMainThread … waitUntilDone:NO），只是只有这一步
            val f: Function = msgSend
            f.invokeVoid(arrayOf<Any?>(Pointer(handle), sel("performSelectorOnMainThread:withObject:waitUntilDone:"), sel("toggleFullScreen:"), null, 0))
        }
    }
}

/**
 * 一次只切一次（纯逻辑，单测）：发了切换以后 [settleMs] 之内（系统的全屏动画约 0.5 – 0.7 秒）不再发第二次——AppKit 在过渡里收到第二个
 * `toggleFullScreen:` 会把刚开始的过渡反过来，用户手快连按两下 F 也只算一下。过了这段时间就按窗口真实的状态比：要的和现在的一样 = 不发。
 * 过渡失败（屏幕锁着、应用不在前台时 AppKit 会把窗口退回原样）不会卡住：[settleMs] 一过就重新按真实状态判断。
 */
internal class FullscreenRequests(
    private val settleMs: Long = SETTLE_MS,
    private val now: () -> Long = java.lang.System::currentTimeMillis,
) {
    private var sentAt = Long.MIN_VALUE / 2

    /** 要 [on]，窗口现在是 [actual]：要不要发一次切换。 */
    fun request(on: Boolean, actual: Boolean): Boolean {
        val t = now()
        if (t - sentAt < settleMs) return false
        if (on == actual) return false
        sentAt = t
        return true
    }

    companion object {
        const val SETTLE_MS = 1_200L
    }
}
