/*
 * 复制自上游壳（docs/adr/0002-player-wiring-in-repo.md §6，登记在 docs/upstream-sync.md §5.1）：
 *   来源  gugugagaemby/native/desktopApp/src/main/kotlin/com/gugugaga/desktop/ReducedMotion.kt
 *   提交  1e19741a（upstream.lock）
 *   改动  只改了 package
 * 不要在这里改行为：上游改了来源文件就把同样的改动搬过来（git -C <上游> diff <lock>..HEAD -- <来源文件>）。
 */
package com.gugaart.desktop.upstream

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** The one call of user32 this needs (jna-platform's User32 does not declare it with a BOOL out-parameter). */
@Suppress("FunctionName")
private interface SpiUser32 : StdCallLibrary {
    fun SystemParametersInfo(uiAction: Int, uiParam: Int, pvParam: IntByReference, fWinIni: Int): Boolean
}

/** `SPI_GETCLIENTAREAANIMATION` (WinUser.h): the 「在 Windows 中显示动画」 switch, FALSE = animations off. */
private const val SPI_GETCLIENTAREAANIMATION = 0x1042

/**
 * Windows asks for less motion (设置 › 辅助功能 › 视觉效果 › 动画效果 off): `SystemParametersInfo(SPI_GETCLIENTAREAANIMATION)` reports
 * FALSE. Anything unreadable (not Windows, the call fails, no user32) counts as false - the app animates as designed.
 */
internal fun windowsReducedMotion(): Boolean = runCatching {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return@runCatching false
    val user32 = Native.load("user32", SpiUser32::class.java, W32APIOptions.DEFAULT_OPTIONS)
    val on = IntByReference(1)
    user32.SystemParametersInfo(SPI_GETCLIENTAREAANIMATION, 0, on, 0) && on.value == 0
}.getOrDefault(false)
