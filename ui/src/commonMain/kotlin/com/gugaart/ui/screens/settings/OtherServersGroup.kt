package com.gugaart.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.OnOff
import com.gugaart.ui.components.OutlineTag
import com.gugaart.ui.components.RadioMark
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SettingGroup
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.components.ValueChevron
import com.gugaart.ui.data.OtherServersPrefs
import com.gugaart.ui.servers.ServerEntry
import com.gugaart.ui.servers.currentOtherServersPrefs
import com.gugaart.ui.servers.currentServerSwitcher
import com.gugaart.ui.servers.serverHost
import com.gugaart.ui.servers.serverTitle
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/*
 * 设置 › 播放 ›「多台服务器」（1.5.0 B4-UI，规划 §2.2 最后一条）：开关「从其它服务器找片源」（默认开）+ 已保存两台以上时一张勾选单
 * 「参与的服务器」（每台已保存的服务器一行；正在用的那台也列着，灰的、写「本台」：它自己的片源总是在）。存储是数据层的
 * [com.gugaart.ui.data.OtherServersPrefsStore]（键 `gugaart.otherServers.v1`）。电脑的设置页直接嵌这一段；电视的在 `tv/system/TvSettingsModel.kt`。
 */

/** 设置里的文案（三端同一份）。 */
object OtherServersCopy {
    const val GROUP = "多台服务器"
    const val TOGGLE = "从其它服务器找片源"
    const val TOGGLE_NOTE = "详情页和播放器里，把其它服务器上的同一部片也列进来"
    const val LIST = "参与的服务器"
    const val LIST_NOTE = "勾掉的服务器不去找片源"
    const val LIST_OFF = "上面的开关关着：哪台都不去找"
    const val CURRENT = "本台"

    /** 「全部 2 台」/「1 / 2 台」：[others] = 除了正在用的那台。纯函数，有单测。 */
    fun summary(others: List<ServerEntry>, prefs: OtherServersPrefs): String {
        val n = others.count { included(it, prefs) }
        return if (n == others.size) "全部 ${others.size} 台" else "$n / ${others.size} 台"
    }

    /** 这台参不参与（[OtherServersPrefs.servers] = null = 全部）。 */
    fun included(e: ServerEntry, prefs: OtherServersPrefs): Boolean = prefs.servers?.contains(e.serverId) ?: true
}

/** 手机 / 电脑设置 › 播放 里的这一组。[startOpen]：截图预置（勾选单推开着）。 */
@Composable
internal fun OtherServersGroup(startOpen: Boolean = false) {
    val switcher = currentServerSwitcher()
    val store = currentOtherServersPrefs(switcher)
    val prefs by store.prefs.collectAsState()
    val entries by switcher.entries.collectAsState()
    // 一台服务器一行（同一台的几个账号只算一台：找片源是按服务器的）
    val servers = remember(entries) { entries.distinctBy { it.serverId } }

    SettingGroup(OtherServersCopy.GROUP, "SERVERS")
    SettingRow(OtherServersCopy.TOGGLE, note = OtherServersCopy.TOGGLE_NOTE, tag = "set-otherServers") { OnOff(prefs.enabled, { store.setEnabled(it) }) }
    if (servers.size < 2) return
    val others = servers.filterNot { it.isCurrent }
    // 台数不多时推开着（一眼看全）；多了收着，点开再看
    var open by remember { mutableStateOf(startOpen || servers.size <= 6) }
    Column {
        SettingRow(
            OtherServersCopy.LIST,
            note = if (prefs.enabled) OtherServersCopy.LIST_NOTE else OtherServersCopy.LIST_OFF,
            tag = "set-otherServers-list",
            onClick = { open = !open },
        ) { ValueChevron(OtherServersCopy.summary(others, prefs), open) }
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            ServerChecklist(servers, prefs, enabled = prefs.enabled) { e, on -> store.setIncluded(e.serverId, on) }
        }
    }
}

/** 勾选单：推开在那一行下面（左边一道朱红竖线，和设置里别的推开式选择器同一种）；勾 = 实心小方块。 */
@Composable
private fun ServerChecklist(servers: List<ServerEntry>, prefs: OtherServersPrefs, enabled: Boolean, onToggle: (ServerEntry, Boolean) -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val ported = com.gugaart.ui.servers.hostsNeedingPort(servers)
    Column(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.5f)
            .drawBehind { drawLine(colors.seal, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx()) }
            .padding(start = 20.dp)
            .testTag("other-servers-checklist"),
    ) {
        for (e in servers) {
            val current = e.isCurrent
            val on = current || OtherServersCopy.included(e, prefs)
            val press = rememberPressState()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .then(if (!current && enabled) Modifier.pressGraphics(press).pressable(press, label = serverTitle(e), onClick = { onToggle(e, !on) }) else Modifier)
                    .padding(vertical = 8.dp)
                    .testTag(if (current) "other-server-current" else if (on) "other-server-on" else "other-server-off"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioMark(on, Modifier.alpha(if (current) 0.45f else 1f))
                Column(Modifier.weight(1f).padding(start = 18.dp, end = 10.dp).alpha(if (current) 0.45f else 1f)) {
                    BasicText(serverTitle(e), style = Guga.song(type.row, if (on) FontWeight.Bold else FontWeight.Normal, if (on) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(if (serverHost(e.address) in ported) com.gugaart.ui.servers.serverHostPort(e.address) else serverHost(e.address), Modifier.padding(top = 2.dp), style = Guga.song(type.caption, color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (current) OutlineTag(OtherServersCopy.CURRENT, type.kicker, 1.dp, colors.dim)
            }
            Rule()
        }
    }
}
