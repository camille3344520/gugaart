package com.gugaart.ui.cast

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gugaart.ui.player.PanelHeader
import com.gugaart.ui.player.PanelLink
import com.gugaart.ui.player.PanelOption
import com.gugaart.ui.player.PanelSection
import com.gugaart.ui.player.PlaySession
import com.gugaart.ui.desktop.LocalDesktopHost
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import kotlinx.coroutines.launch

/** 「投屏」状态的一句话。纯函数，有单测。 */
fun castStateWord(st: CastStatus?): String = when {
    st == null -> "正在连接"
    st.playing -> "播放中"
    st.paused -> "已暂停"
    st.state == "TRANSITIONING" -> "正在缓冲"
    st.stopped -> "已停止"
    else -> st.state
}

/**
 * 播放器的「投屏」面板（手机 / 电脑，顶栏的电视按钮打开）：
 *  - 还没投：列出同一个网络里找到的电视（打开时自动找一遍），点一台就投过去；
 *  - 投着：电视上放到哪了，暂停 / 继续、退 10 秒、进 30 秒、结束投屏（本机从电视放到的位置接着放）。
 */
@Composable
fun CastPanelBody(play: PlaySession) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val p = Guga.player
    val scope = rememberCoroutineScope()
    val session by g.session.session.collectAsState()
    LaunchedEffect(Unit) { if (Cast.active == null && Cast.devices.isEmpty()) Cast.search() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("panel-cast")) {
        PanelHeader(com.gugaart.ui.player.PlayerPanel.Cast)
        val active = Cast.active
        if (active != null) {
            val st = Cast.status
            BasicText("正在「${active.name}」上放", Modifier.padding(top = 10.dp), style = Guga.song(p.body, FontWeight.Bold, colors.ink), maxLines = 2)
            BasicText(
                listOfNotNull(castStateWord(st), st?.positionSec?.let { pos -> hms(pos) + (st.durationSec?.let { " / " + hms(it) } ?: "") }).joinToString(" · "),
                Modifier.padding(top = 6.dp).testTag("cast-status"),
                style = Guga.note(p.note, colors.dim),
                maxLines = 1,
            )
            PanelSection("遥控")
            PanelLink(if (st?.playing == true) "暂停" else "继续") { Cast.togglePause(g.scope) }
            PanelLink("退 10 秒") { Cast.seekBy(g.scope, -10) }
            PanelLink("进 30 秒") { Cast.seekBy(g.scope, 30) }
            PanelSection("结束")
            PanelLink("结束投屏，在这里接着放") { Cast.stop(g.scope, play, resume = true) }
            PanelLink("结束投屏") { Cast.stop(g.scope, play, resume = false) }
        } else {
            PanelSection(if (Cast.searching) "正在找电视…" else "同一个网络里的电视")
            for (d in Cast.devices) {
                PanelOption(d.name, false, {
                    val client = session?.client ?: return@PanelOption
                    Cast.start(g.scope, play, client, d)
                }, note = d.model)
            }
            if (!Cast.searching) PanelLink(if (Cast.devices.isEmpty()) "再找一遍" else "重新找一遍") { scope.launch { Cast.search() } }
        }
        Cast.message?.let { BasicText(it, Modifier.padding(top = 12.dp).testTag("cast-message"), style = Guga.note(p.note, colors.dim), maxLines = 5) }
        // M2-13：Mac 上找了半天一台都没有时，多半句「本地网络」权限的提示（只加字，不加按钮；Windows / 手机不变）
        val macHint = if (!Cast.searching && Cast.devices.isEmpty()) castLocalNetworkHint(LocalDesktopHost.current.platformName) else null
        BasicText(
            "用的是 DLNA：电视和这台设备要连同一个网络，电视上打开「投屏 / DLNA / 无线投屏」。投屏时电视自己放原片，弹幕不会投过去；电视放不了的格式会提示。" + (macHint ?: ""),
            Modifier.padding(top = 16.dp).testTag("cast-dlna-note"),
            style = Guga.note(p.note, colors.faint),
            maxLines = 5,
        )
    }
}

/** M2-13（P-mac-15 文案，D-lead-15）：只有 macOS 才有这句「本地网络」权限提示；别的端返回 null。纯函数，有单测。 */
fun castLocalNetworkHint(platformName: String): String? =
    if (platformName == "macOS") "· 也可能是没有本地网络权限，去系统设置 › 隐私与安全性 › 本地网络里打开。" else null
