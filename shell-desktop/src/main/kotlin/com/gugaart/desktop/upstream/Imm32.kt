/*
 * 复制自上游壳（docs/adr/0002-player-wiring-in-repo.md §6，登记在 docs/upstream-sync.md §5.1）：
 *   来源  gugugagaemby/native/desktopApp/src/main/kotlin/com/gugugaga/desktop/Imm32.kt
 *   提交  1e19741a（upstream.lock）
 *   改动  只改了 package
 * 不要在这里改行为：上游改了来源文件就把同样的改动搬过来（git -C <上游> diff <lock>..HEAD -- <来源文件>）。
 */
package com.gugaart.desktop.upstream

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary

/**
 * The one IMM32 call the host needs and jna-platform does not map (win-ime): associating an IME context with a
 * window handle. AWT's `enableInputMethods` only flips a Java-side flag for the component that already has the
 * focus; the native (dis)association is what actually routes keys through the IME, and AWT performs it only on
 * an AWT focus gain - which Compose's internal focus moves never produce (see [DesktopPlayerPlatform.shortcutKeys]).
 */
internal interface Imm32 : StdCallLibrary {
    /** `hIMC = null` + [IACE_DEFAULT]: associate the default context; `hIMC = null` + flags `0`: disassociate. Returns the previous context. */
    fun ImmAssociateContextEx(hWnd: HWND?, hIMC: Pointer?, dwFlags: Int): Pointer?

    companion object {
        val INSTANCE: Imm32 = Native.load("imm32", Imm32::class.java)

        /** Re-associate the window's default input context. */
        const val IACE_DEFAULT = 0x0010
    }
}
