package com.gugaart.ui.desktop

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 桌面宿主样式（ADR 0004 §2.1）：`:ui` 不知道自己在哪个操作系统上，只知道「这扇窗口长什么样」。
 * 由壳提供：Windows 壳经 `StageOptions.wrap`，Mac 壳在 `MacSession` 的根上；默认值 = Windows 的现状，
 * 所以 Windows 的像素一个不变。离屏测试直接 `CompositionLocalProvider(LocalDesktopHost provides mac样式)`。
 *
 * 规则：`:ui` 里不出现 `os.name` / `Platform.isMac` 这类判断，只读 [LocalDesktopHost]。
 */
enum class WindowKeysStyle { DrawnRight, SystemLeft }

/** 提示文字写 Ctrl 还是 ⌘。 */
enum class ModifierGlyph { Ctrl, Cmd }

/** 这一端能不能自己下载并安装更新（Windows 能；macOS 是「下载 .dmg 打开」；iOS 不能）。 */
enum class SelfUpdate { Install, OpenDmg, None }

@Immutable
data class DesktopHost(
    /** Windows：右端自绘三键；macOS：系统在左上画红绿灯，报头文字让位。 */
    val windowKeys: WindowKeysStyle = WindowKeysStyle.DrawnRight,
    /** [WindowKeysStyle.SystemLeft] 时报头 / 播放器顶栏的文字从哪起（设计稿 85 px；固定 px，不随 rem）。 */
    val leadingInsetPx: Float = 0f,
    /** 窗口是否活跃：失焦时报头文字变淡（设计稿 55 %）。 */
    val active: Boolean = true,
    val modifier: ModifierGlyph = ModifierGlyph.Ctrl,
    /** 全屏时顶部不可用的一条（刘海屏 MacBook 的菜单栏位置，设计稿 37 pt）；0 = 没有。 */
    val safeTop: Dp = 0.dp,
    /** 设置 › 关于、备份文件里的平台名。 */
    val platformName: String = "Windows",
    val selfUpdate: SelfUpdate = SelfUpdate.Install,
)

val LocalDesktopHost = staticCompositionLocalOf { DesktopHost() }