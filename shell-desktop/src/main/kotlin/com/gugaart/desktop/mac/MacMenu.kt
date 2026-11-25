package com.gugaart.desktop.mac

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import com.gugaart.ui.desktop.DesktopCommand
import com.gugaart.ui.desktop.DesktopCommandSink
import com.gugaart.ui.desktop.DesktopCommands
import com.gugaart.ui.desktop.PlayerCommandSink
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlayerKeyCommand

/**
 * M2-4（ADR 0004 §2.4）：macOS 应用菜单栏的**模型**（纯函数，单测）+ 渲染。
 * 我们只出六个菜单（文件 / 编辑 / 显示 / 播放 / 窗口 / 帮助），命令与 ⌘ 键照 `design/macos/notes.md` §2 和命令表 [DesktopCommand]；
 * 最前面那个应用菜单（咕咕嘎嘎）是**系统的**：「关于 / 设置… ⌘, / 退出 ⌘Q」由 `MacSession.installAppHandlers` 经
 * `java.awt.Desktop` 的处理器接上，这里不再自己出一个同名菜单（以前出了，菜单栏上就是两个「咕咕嘎嘎」，D-lead-39），
 * 「检查更新…」放进「帮助」；
 * 系统自动加的项（服务 / 隐藏 / 撤销 / 全选 / 听写 / 表情 / 最小化缩放前置 / 帮助搜索框）不写。
 * 「播放」组不在播放时整组不可用；「筛选」只在海报墙；「窗口置顶」任何时候可用（D-lead-12）；
 * 「画中画」这一条**按能力出现**（[pip] = 平台有 `PlayerPlatform.pictureInPicture`；没有就没有这一项，不是灰的：对齐清单 P18、D-lead-20），
 * 勾选状态 = [pipActive]，点了调同一个 `PictureInPicture.toggle()`（和播放器顶栏的钮是同一个对象）。
 */
data class MacMenuItem(
    val label: String,
    val cmd: DesktopCommand? = null,
    val playerCmd: PlayerKeyCommand? = null,
    /** 非命令表的动作（关于 / 退出 / 置顶 …），见 [MacMenuBar] 的分发。 */
    val action: String? = null,
    val enabled: Boolean = true,
    /** 前面打勾（菜单里的勾选项：画中画正开着）。 */
    val checked: Boolean = false,
    /** 不在命令表里、但要有快捷键的那一项（「关闭窗口」⌘W，D-lead-14）。 */
    val keys: String? = null,
) {
    /** 菜单上印的 ⌘ 键（和命令表一致）。 */
    val shortcut: String? get() = cmd?.cmdHint ?: keys
}

data class MacMenu(val title: String, val items: List<MacMenuItem>)

object MacMenuModel {
    /** [reprint]：「付印新刊」有没有页面接着（只在首页挂上，[DesktopCommandSink.reprintReady]）；没有就灰，照「筛选」的待遇。 */
    fun items(route: Route, tab: WideTab?, playing: Boolean, pip: Boolean = false, pipActive: Boolean = false, reprint: Boolean = true): List<MacMenu> {
        val play = playing
        return listOf(
            MacMenu(
                "文件",
                listOf(
                    MacMenuItem("付印新刊", cmd = DesktopCommand.Reprint, enabled = reprint),
                    MacMenuItem("切换账号 / 服务器…", action = "switch-server"),
                    MacMenuItem("退出登录", action = "logout"),
                    // 关窗 = 退出（D-lead-14）：⌘W 走同一条正常退出
                    MacMenuItem("关闭窗口", action = "close-window", keys = "⌘W"),
                ),
            ),
            MacMenu(
                "编辑",
                listOf(MacMenuItem("搜索片库…", cmd = DesktopCommand.Search)),
            ),
            MacMenu(
                "显示",
                listOf(
                    MacMenuItem("首页", cmd = DesktopCommand.GoHome),
                    MacMenuItem("发现", cmd = DesktopCommand.GoDiscover),
                    MacMenuItem("片库", cmd = DesktopCommand.GoLibs),
                    MacMenuItem("合集", cmd = DesktopCommand.GoColls),
                    MacMenuItem("我的", cmd = DesktopCommand.GoMe),
                    MacMenuItem("返回", cmd = DesktopCommand.Back),
                    MacMenuItem("前进", cmd = DesktopCommand.Forward, enabled = DesktopCommands.enabled(DesktopCommand.Forward, route, tab)),
                    MacMenuItem("筛选", cmd = DesktopCommand.Filter, enabled = DesktopCommands.enabled(DesktopCommand.Filter, route, tab)),
                ),
            ),
            MacMenu(
                "播放",
                listOf(
                    MacMenuItem("播放 / 暂停", playerCmd = PlayerKeyCommand.PlayPause, enabled = play),
                    MacMenuItem("快退 10 秒", playerCmd = PlayerKeyCommand.Seek(-1), enabled = play),
                    MacMenuItem("快进 10 秒", playerCmd = PlayerKeyCommand.Seek(1), enabled = play),
                    MacMenuItem("上一集", playerCmd = PlayerKeyCommand.Prev, enabled = play),
                    MacMenuItem("下一集", playerCmd = PlayerKeyCommand.Next, enabled = play),
                    MacMenuItem("音量 +", playerCmd = PlayerKeyCommand.Volume(0.05), enabled = play),
                    MacMenuItem("音量 −", playerCmd = PlayerKeyCommand.Volume(-0.05), enabled = play),
                    MacMenuItem("字幕", playerCmd = PlayerKeyCommand.Subtitles, enabled = play),
                    MacMenuItem("倍速 +", playerCmd = PlayerKeyCommand.Rate(1), enabled = play),
                    MacMenuItem("倍速 −", playerCmd = PlayerKeyCommand.Rate(-1), enabled = play),
                    MacMenuItem("弹幕开 / 关", playerCmd = PlayerKeyCommand.Danmaku, enabled = play),
                    MacMenuItem("收藏 · 盖章", cmd = DesktopCommand.Favorite, enabled = play),
                    MacMenuItem("标记已看", cmd = DesktopCommand.Played, enabled = play),
                ) + listOfNotNull(
                    // A10：有能力才有这一项
                    MacMenuItem("画中画", action = "pip", enabled = play, checked = pipActive).takeIf { pip },
                ) + listOf(
                    MacMenuItem("投屏（DLNA）…", action = "cast", enabled = play),
                    MacMenuItem("播放器全屏", playerCmd = PlayerKeyCommand.Fullscreen, enabled = play),
                ),
            ),
            MacMenu(
                "窗口",
                listOf(
                    MacMenuItem("最小化", action = "minimize"),
                    MacMenuItem("窗口置顶", action = "always-on-top"),
                ),
            ),
            MacMenu(
                "帮助",
                listOf(
                    MacMenuItem("咕咕嘎嘎帮助", action = "help"),
                    MacMenuItem("快捷键一览", action = "shortcuts"),
                    MacMenuItem("检查更新…", action = "check-update"),
                    MacMenuItem("开源许可", action = "licenses"),
                ),
            ),
        )
    }
}

/**
 * 渲染进 Compose Desktop 的 [MenuBar]（macOS 上就是系统菜单栏）。在 `Window` 里调。
 * [onAction] 分发非命令表动作；命令走 [DesktopCommandSink] / [PlayerCommandSink]。
 */
@Composable
fun FrameWindowScope.MacMenuBar(route: Route, tab: WideTab?, playing: Boolean, onAction: (String) -> Unit, pip: Boolean = false, pipActive: Boolean = false) {
    // 在组合里读：首页挂上 / 撤下付印的回调时菜单跟着变灰 / 变亮
    val reprint = DesktopCommandSink.reprintReady?.invoke() == true
    val menus = MacMenuModel.items(route, tab, playing, pip, pipActive, reprint)
    MenuBar {
        for (m in menus) {
            Menu(m.title) {
                for (item in m.items) {
                    // 画中画是勾选项（开着打勾）；点了和别的动作一样交给 onAction
                    if (item.action == "pip") {
                        CheckboxItem(item.label, checked = item.checked, enabled = item.enabled, onCheckedChange = { onAction("pip") })
                        continue
                    }
                    Item(
                        item.label,
                        enabled = item.enabled,
                        // 命令表里有 ⌘ 键的项挂上 KeyShortcut：菜单右边印出 ⌘K / ⌘R …，AppKit 也认这些快捷键。
                        // ⌘W 不在命令表里，单独挂。同一次按键菜单和界面可能都接住——经 [DesktopCommandSink.claimOnce] 只执行一次。
                        shortcut = macCmdShortcut(item.cmd) ?: if (item.keys == "⌘W") KeyShortcut(Key.W, meta = true) else null,
                        onClick = {
                            when {
                                item.cmd != null -> if (DesktopCommandSink.claimOnce(item.cmd)) DesktopCommandSink.sink?.invoke(item.cmd)
                                item.playerCmd != null -> PlayerCommandSink.sink?.invoke(item.playerCmd)
                                item.action != null -> onAction(item.action)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 命令表的 ⌘ 提示 → 菜单快捷键（[KeyShortcut]）：菜单右边印出来、AppKit 的 `performKeyEquivalent` 也认。
 * 没有 ⌘ 键的命令（收藏 V / 已看 W）= null，不挂快捷键。纯函数，单测逐项核对。
 */
internal fun macCmdShortcut(cmd: DesktopCommand?): KeyShortcut? {
    val hint = cmd?.cmdHint ?: return null
    if (!hint.startsWith("⌘")) return null
    val key = when (hint.removePrefix("⌘")) {
        "K" -> Key.K
        "," -> Key.Comma
        "R" -> Key.R
        "[" -> Key.LeftBracket
        "]" -> Key.RightBracket
        "F" -> Key.F
        "1" -> Key.One
        "2" -> Key.Two
        "3" -> Key.Three
        "4" -> Key.Four
        "5" -> Key.Five
        else -> null
    } ?: return null
    return KeyShortcut(key, meta = true)
}