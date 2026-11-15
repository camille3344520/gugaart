package com.gugaart.ui.tv.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.screens.update.ExtraPhase
import com.gugaart.ui.screens.update.ExtraView
import com.gugaart.ui.screens.update.LocalUpdateStateOverride
import com.gugaart.ui.screens.update.extraView
import com.gugaart.ui.screens.update.megabytes
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvGroupsRoot
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.LocalBackDispatcher

/**
 * 号外上有哪几个按钮（按顺序；第一个拿到焦点）。纯函数，有单测。
 * 有新版（用户 2026-11-15：「给用户选择，忽略这个版本，或者下载、下载+安装。多个选项」）：下载并安装 · 忽略这一版 · 稍后
 * （「只下载」用户觉得没用，去掉了）；强制更新只有「下载并安装」。下好了：现在安装 · 稍后（用户确认了才装）。下载中只有「转到后台」。
 */
fun extraButtons(view: ExtraView): List<Pair<String, String>> = when (view.phase) {
    ExtraPhase.Downloading, ExtraPhase.Installing -> listOf("upd-bg" to "转到后台")
    ExtraPhase.Ready -> listOfNotNull("upd-go" to "现在安装", ("upd-later" to "稍后").takeIf { !view.forced })
    ExtraPhase.Permission -> listOfNotNull("upd-go" to "去允许安装", ("upd-later" to "稍后").takeIf { !view.forced })
    ExtraPhase.Failed -> listOfNotNull("upd-go" to "再试一次", ("upd-later" to "稍后").takeIf { !view.forced })
    ExtraPhase.Offer -> listOfNotNull(
        "upd-go" to "下载并安装",
        ("upd-skip" to "忽略这一版").takeIf { !view.forced },
        ("upd-later" to "稍后").takeIf { !view.forced },
    )
}

/**
 * 更新号外（电视，设计稿 `p=update`）：一张米白纸条压在压暗的页面上（纸面语言，不是玻璃）；焦点反过来 = 墨底米白字。
 * 状态和动作全部是上游 `UpdateController` 的（和手机共用 [extraView]）。默认焦点「下载并安装」；左右换按钮；返回 = 稍后
 * （强制更新收不掉）；下载中只有「转到后台」。「忽略这一版」由画报记在本机（UpdateConsent），和「稍后」（这一次运行）分开。
 */
@Composable
fun TvUpdateSlip() {
    val g = LocalGraph.current
    val override = LocalUpdateStateOverride.current
    LaunchedEffect(g) {
        if (override != null) return@LaunchedEffect
        g.update.startAutoCheck()
        g.deps.updateInstallProblem?.invoke()?.let { g.update.reportInstallProblem(it) }
    }
    val live by g.update.state.collectAsState()
    val dismissed by g.update.dismissedVersion.collectAsState()
    var background by remember { mutableStateOf(false) }
    // 设置里点「检查更新」/ 安装卡住了：放到后台的号外重新出来
    val reshowTick = com.gugaart.ui.screens.update.UpdateConsent.reshowTick
    LaunchedEffect(reshowTick) { if (reshowTick > 0) background = false }
    val view = extraView(override ?: live, com.gugaart.ui.screens.update.UpdateConsent.dismissedFor(dismissed), com.gugaart.ui.screens.update.UpdateConsent.ignored)
    if (view == null || (background && (view.phase == ExtraPhase.Downloading || view.phase == ExtraPhase.Installing))) return
    val colors = Guga.colors
    val back = LocalBackDispatcher.current
    val groups = remember { TvGroups() }
    val buttons = extraButtons(view)
    val returnTo = remember { g.focus.lastFocusedKey }
    fun close(action: () -> Unit) {
        action()
        returnTo?.let { g.focus.request(it) }
    }
    groups.edge = { _, _ -> TvGroups.STAY }
    LaunchedEffect(view.phase) { groups.enterWhenReady("update", g.focus) }
    DisposableEffect(view.forced, view.phase) {
        val un = back.push {
            if (!view.forced) close { com.gugaart.ui.screens.update.UpdateConsent.hideAgain(); if (view.phase == ExtraPhase.Downloading) background = true else g.update.dismiss() }
            true
        }
        onDispose { un() }
    }
    val paper = colors.ink
    val ink = colors.onInk
    val red = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK
    TvGroupsRoot(groups, Modifier.background(Color(0xFF0A0907).copy(alpha = 0.8f)).testTag("tv-update-${view.phase.name.lowercase()}")) {
        Column(Modifier.padding(start = 400.dp, top = 150.dp).width(1120.dp).background(paper).drawBehind { drawRect(colors.seal, size = androidx.compose.ui.geometry.Size(size.width, 12.dp.toPx())) }) {
            Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                BasicText("号外", Modifier.background(colors.seal).padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), style = Guga.song(36.sp, FontWeight.Black, Color.White, letterSpacing = 0.3.em), maxLines = 1)
                BasicText(if (view.forced) "EXTRA · 必须更新" else "EXTRA · 新版本", style = Guga.note(22.sp, red, letterSpacing = 0.18.em), maxLines = 1)
                Spacer(Modifier.weight(1f))
                BasicText(view.meta, style = Guga.song(22.sp, color = ink.copy(alpha = 0.6f)), maxLines = 1)
            }
            Row(Modifier.padding(horizontal = 48.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Column(Modifier.width(330.dp)) {
                    BasicText(view.version, style = Guga.numeral(104.sp, ink), maxLines = 1)
                    BasicText(
                        when (view.phase) {
                            ExtraPhase.Ready -> "下好了，现在安装？"
                            ExtraPhase.Failed -> "这一版没下好"
                            ExtraPhase.Downloading -> "正在下载"
                            ExtraPhase.Installing -> "正在装"
                            ExtraPhase.Permission -> "需要先允许安装"
                            ExtraPhase.Offer -> if (view.forced) "这一版要先更新" else "新的一版到了"
                        },
                        style = Guga.song(22.sp, color = ink.copy(alpha = 0.6f)), maxLines = 1,
                    )
                }
                Column(Modifier.weight(1f).drawBehind { drawLine(ink.copy(alpha = 0.25f), Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }.padding(start = 40.dp, top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val lines = if (view.phase == ExtraPhase.Failed) listOfNotNull(view.message) else view.notes.ifEmpty { listOfNotNull(view.message) }
                    lines.forEachIndexed { i, line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BasicText("${i + 1}.", style = Guga.numeral(28.sp, red), maxLines = 1)
                            BasicText(line, style = Guga.song(28.sp, FontWeight.Bold, ink, lineHeight = 42.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (view.phase == ExtraPhase.Downloading) {
                Row(Modifier.padding(start = 48.dp, end = 48.dp, bottom = 26.dp).testTag("tv-update-progress"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    BasicText("${(view.progress * 100).toInt()}%", style = Guga.numeral(34.sp, ink), maxLines = 1)
                    Box(Modifier.weight(1f).height(8.dp).background(ink.copy(alpha = 0.15f))) { Box(Modifier.fillMaxWidth(view.progress).height(8.dp).background(colors.seal)) }
                    if (view.total > 0) BasicText("${megabytes(view.done)} / ${megabytes(view.total)}", style = Guga.song(23.sp, color = ink.copy(alpha = 0.7f)), maxLines = 1)
                }
            }
            Row(
                Modifier.padding(horizontal = 48.dp).fillMaxWidth().drawBehind { drawLine(ink, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) }.padding(top = 24.dp, bottom = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                // 她的一格：署名照（没有编她的话：为什么值得装，由更新说明自己讲）
                if (LocalGugaPrefs.current.showMascot) Byline(68.dp, live = false)
                Spacer(Modifier.weight(1f))
                TvGroup("update", buttons.map { it.first }, default = buttons.first().first)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    for ((key, label) in buttons) {
                        val focusedNow = groups.current == key
                        TvBlockFocus(
                            key,
                            {
                                when (key) {
                                    "upd-go" -> when (view.phase) {
                                        // 下好了：用户按「现在安装」才交给安装器
                                        ExtraPhase.Ready -> g.update.installDownloaded()
                                        ExtraPhase.Permission -> g.update.requestInstallPermission()
                                        // 「下载并安装」：挑了它就是同意装
                                        ExtraPhase.Offer -> com.gugaart.ui.screens.update.UpdateConsent.downloadAndInstall(g)
                                        // 「再试一次」：照上一次挑的那一种
                                        else -> com.gugaart.ui.screens.update.UpdateConsent.downloadAndInstall(g)
                                    }
                                    // 忽略这一版：记在本机，这一版不再弹
                                    "upd-skip" -> ((override ?: live) as? com.gugugaga.app.update.UpdateState.Available)?.release?.let { r -> close { com.gugaart.ui.screens.update.UpdateConsent.ignore(g, r.versionCode) } }
                                    "upd-bg" -> close { com.gugaart.ui.screens.update.UpdateConsent.hideAgain(); background = true }
                                    else -> close { com.gugaart.ui.screens.update.UpdateConsent.hideAgain(); g.update.dismiss() }
                                }
                            },
                            if (focusedNow) Modifier else Modifier.border(2.dp, ink.copy(alpha = 0.5f)),
                            label = label, kind = TvFocusKind.OnPaper,
                        ) { focused ->
                            Box(Modifier.height(70.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                                BasicText(label, style = Guga.song(27.sp, FontWeight.Bold, if (focused) paper else ink, letterSpacing = 0.06.em), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("unused")
private val keepDir = TvDir.Left
