package com.gugaart.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.servers.ManageStage
import com.gugaart.ui.servers.SEARCH_FROM
import com.gugaart.ui.servers.ServerEntry
import com.gugaart.ui.servers.ServerListLook
import com.gugaart.ui.servers.ServerListProbe
import com.gugaart.ui.servers.ServerListState
import com.gugaart.ui.servers.ServerRowText
import com.gugaart.ui.servers.ServerSwitchCopy
import com.gugaart.ui.servers.currentServerSwitcher
import com.gugaart.ui.servers.key
import com.gugaart.ui.servers.rememberServerListState
import com.gugaart.ui.servers.serverLookSpec
import com.gugaart.ui.servers.serverTitle
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaColors
import com.gugaart.ui.tv.detail.TvDetailSpec
import com.gugaart.ui.tv.detail.TvSecHead
import com.gugaart.ui.tv.system.TvEditText
import com.gugaart.ui.wide.LocalWideNav
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.FocusZoneScope
import com.gugugaga.ds.focus.ZoneRestore
import com.gugugaga.ds.focus.focusZone
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.PressState
import kotlinx.coroutines.delay

/** 换服务器页边的焦点区（focusKey 的前缀）：不是页面内容，不进「这一页上次的焦点」。 */
const val TV_SERVERS_ZONE = "tv-servers"

/** 页边的数（和详情页「版本」那一列同一种纸：左边一道细线、墨底略亮）。 */
object TvServerSpec {
    /** 页边的底（同详情页 / 设置的选择器页边）。 */
    val PANEL = Color(0xFF14120E)
    val TOP = 120.dp
    val PAD = 48.dp
    /** 管理那几行矮一点（是行下面推开的小单子）。 */
    val MANAGE_ROW = 72.dp
}

/**
 * 电视的换服务器（1.5.0 A2-UI）：右边页边出来一列（详情页「版本」页边那一种纸面，不是玻璃），三端共用的列表状态 [ServerListState]。
 * 入口：角落导航的「服务器」一项、首页焦点不在封面 / 卡片上时按遥控器的菜单键。打开时焦点落在**正在用的那一台**；上下换行，确定 = 换过去；
 * 菜单键 / 长按确定 = 在那一行下面推开「改备注 / 用别的账号登录 / 删除这台服务器」；返回 / 左 = 收起（推开着管理时先收管理），焦点还给打开前的那一项。
 * 改备注、搜索（超过 [SEARCH_FROM] 台）按确定弹系统输入法（和登录页一屏表单的输入框同一个 [TvEditText]）。
 */
object TvServerPanel {
    var open: Boolean by mutableStateOf(false)
        private set

    /** 收起时焦点还给谁（从侧栏打开时 = 展开侧栏之前的那一项）。 */
    private var returnTo: PressState? = null

    fun show(returnTo: PressState? = null) {
        if (open) return
        this.returnTo = returnTo
        open = true
    }

    fun close() {
        open = false
    }

    @Composable
    fun Host() {
        val g = LocalGraph.current
        val tv = LocalWideNav.current.tv
        val colors = Guga.colors
        val isOpen = open
        val switcher = currentServerSwitcher()
        val state = rememberServerListState(switcher)
        state.onClose = { close() }
        val entries by switcher.entries.collectAsState()
        val rows = state.visible(entries)
        // 正在打字的那一格（搜索 / 改备注）：左右是挪光标，不收页边
        var editing by remember { mutableStateOf<String?>(null) }
        var from by remember { mutableStateOf<PressState?>(null) }
        var wasOpen by remember { mutableStateOf(false) }

        // 打开：焦点落在正在用的那一台（侧栏收起时还会抢一下焦点：落定、站稳了才算）；收起：还给打开前的那一项
        LaunchedEffect(isOpen) {
            if (isOpen) {
                wasOpen = true
                from = returnTo ?: g.focus.lastFocusedPress?.takeIf { g.focus.lastFocusedKey?.startsWith("$TV_RAIL_ZONE/") != true }
                returnTo = null
                editing = null
                lastManaged = null
                state.query = ""
                state.closeManage()
                val want = "$TV_SERVERS_ZONE/srv-${rows.indexOfFirst { it.isCurrent }.coerceAtLeast(0)}"
                var steady = 0
                repeat(80) {
                    if (g.focus.lastFocusedKey?.startsWith("$TV_SERVERS_ZONE/") == true) {
                        if (++steady >= 4) return@LaunchedEffect
                    } else {
                        steady = 0
                        g.focus.request(want)
                    }
                    delay(16)
                }
            } else if (wasOpen) {
                wasOpen = false
                val back = from
                from = null
                if (back == null || !runCatching { back.focusRequester.requestFocus() }.getOrDefault(false)) {
                    delay(60)
                    tvRescueFocus(g, tv)
                }
            }
        }
        // 推开 / 换一步管理：焦点进那一块的第一项；收起管理：回到那一行
        val managing = state.managing
        val stage = state.stage
        LaunchedEffect(managing, stage) {
            if (!isOpen) return@LaunchedEffect
            val want = when {
                managing == null -> rows.indexOfFirst { it.key == lastManaged }.takeIf { it >= 0 }?.let { "$TV_SERVERS_ZONE/srv-$it" } ?: return@LaunchedEffect
                stage == ManageStage.Menu -> "$TV_SERVERS_ZONE/mng-rename"
                stage == ManageStage.Rename -> "$TV_SERVERS_ZONE/mng-note"
                else -> "$TV_SERVERS_ZONE/mng-cancel"
            }
            repeat(30) {
                if (g.focus.request(want)) return@LaunchedEffect
                delay(16)
            }
        }
        if (managing != null) lastManaged = managing
        if (!isOpen) return
        ServerListProbe.last = state
        TvModal()
        TvBack {
            when {
                editing != null -> editing = null
                state.managing != null -> state.closeManage()
                else -> close()
            }
        }
        val spec = serverLookSpec(ServerListLook.Tv)
        // 打开就 ping 一遍
        LaunchedEffect(Unit) { switcher.refreshPings() }

        Box(Modifier.fillMaxSize().testTag("tv-server-layer")) {
            // 页面压暗（和侧栏展开、长按菜单同一层墨）
            Box(Modifier.fillMaxSize().background(colors.paper.copy(alpha = TvSpec.SCRIM_ALPHA)))
            FocusZoneScope(TV_SERVERS_ZONE) {
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .width(1920.dp - TvDetailSpec.SIDE_LEFT)
                        .fillMaxHeight()
                        .background(TvServerSpec.PANEL)
                        .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                        .focusZone(TV_SERVERS_ZONE, ZoneRestore.Nearest, trap = true, trapActive = { open })
                        // 左 = 收起（和详情页的页边一样）；打字的时候左右是挪光标
                        .onPreviewKeyEvent { e ->
                            if (e.key != Key.DirectionLeft || editing != null) return@onPreviewKeyEvent false
                            if (e.type == KeyEventType.KeyDown) {
                                if (state.managing != null) state.closeManage() else close()
                            }
                            true
                        }
                        .padding(start = TvServerSpec.PAD, end = TvSpec.RIGHT, top = TvServerSpec.TOP, bottom = TvSpec.BOTTOM)
                        .testTag("tv-server-panel"),
                ) {
                    TvSecHead("服务器", "SERVERS", trailing = "${entries.size} 台")
                    Spacer(Modifier.height(18.dp))
                    if (entries.size > SEARCH_FROM) {
                        TvLineField("search", "搜索", state.query, ServerSwitchCopy.SEARCH_HINT, editing == "search", { editing = if (editing == "search") null else "search" }, { state.query = it }) { editing = null }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (rows.isEmpty()) BasicText(ServerSwitchCopy.NO_MATCH, Modifier.padding(vertical = 24.dp), style = Guga.song(23.sp, color = colors.faint), maxLines = 1)
                        val ported = com.gugaart.ui.servers.hostsNeedingPort(entries)
                        rows.forEachIndexed { i, e ->
                            TvServerRow("srv-$i", e, state, spec, com.gugaart.ui.servers.serverHost(e.address) in ported)
                            if (state.managing == e.key) TvManage(e, state, editing == "note", { editing = if (editing == "note") null else "note" }) { editing = null }
                        }
                        TvAddRow { state.add() }
                    }
                }
            }
        }
    }

    /** 上一次推开管理的那一行（收起管理时焦点回到它）。 */
    private var lastManaged: String? = null
}

/** 一台服务器一行（和「版本」页边的选项同一种：左边小方块 = 正在用；焦点 = 整行米白）。 */
@Composable
private fun TvServerRow(key: String, e: ServerEntry, state: ServerListState, spec: com.gugaart.ui.servers.ServerLookSpec, withPort: Boolean) {
    val colors = Guga.colors
    val busy = state.busy == e.key
    Column(Modifier.fillMaxWidth().testTag(if (e.isCurrent) "tv-server-current" else "tv-server-row")) {
        TvFocusable(
            onClick = { state.pick(e) },
            modifier = Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
            key = key,
            label = serverTitle(e),
            onLongClick = { state.manage(e) },
        ) { focused ->
            val ink = if (focused) colors.onInk else colors.ink
            Row(Modifier.fillMaxWidth().heightIn(min = spec.rowMin).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(18.dp).then(
                        if (e.isCurrent) Modifier.background(if (focused) GugaColors.SEAL_ON_INK else colors.spot) else Modifier.border(2.dp, ink.copy(alpha = 0.6f)),
                    ),
                )
                ServerRowText(e, spec, Modifier.padding(start = spec.markGap).weight(1f), onInk = focused, dim = busy, withPort = withPort)
            }
        }
        val note = if (busy) "切换中…" else state.noteOf(e)
        if (note != null) {
            BasicText(note, Modifier.padding(start = 60.dp, top = 8.dp, bottom = 6.dp).testTag(if (busy) "tv-server-busy" else "tv-server-note"), style = Guga.song(22.sp, color = if (busy) colors.dim else colors.seal), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 推开的管理（左边一道专色竖线）：三行，或改备注的输入框，或删除的二次确认。 */
@Composable
private fun TvManage(e: ServerEntry, state: ServerListState, editing: Boolean, onEdit: () -> Unit, onDoneEditing: () -> Unit) {
    val colors = Guga.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 10.dp, bottom = 14.dp)
            .drawBehind { drawLine(colors.spot, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 3.dp.toPx()) }
            .padding(start = 22.dp)
            .testTag("tv-server-manage"),
    ) {
        when (state.stage) {
            ManageStage.Menu -> {
                TvManageRow("mng-rename", GuguIcon.Pencil, "改备注") {
                    state.draft = e.note.orEmpty()
                    state.stage = ManageStage.Rename
                }
                TvManageRow("mng-login", GuguIcon.LogIn, "用别的账号登录") { state.loginOther(e) }
                TvManageRow("mng-delete", GuguIcon.Trash, "删除这台服务器", danger = true, enabled = !e.isCurrent, hint = if (e.isCurrent) ServerSwitchCopy.CURRENT_CANNOT_DELETE else null) {
                    if (!e.isCurrent) state.stage = ManageStage.Delete
                }
            }
            ManageStage.Rename -> {
                TvLineField("mng-note", "备注", state.draft, e.name, editing, onEdit, { state.draft = it }) {
                    onDoneEditing()
                    state.saveNote(e)
                }
                TvManageRow("mng-save", GuguIcon.Check, "保存") { state.saveNote(e) }
                TvManageRow("mng-cancel", GuguIcon.X, "取消") { state.closeManage() }
            }
            ManageStage.Delete -> {
                BasicText(ServerSwitchCopy.deleteTitle(), Modifier.padding(top = 4.dp), style = Guga.song(26.sp, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TvManageRow("mng-cancel", GuguIcon.X, "取消") { state.closeManage() }
                TvManageRow("mng-del-ok", GuguIcon.Trash, "移除", danger = true) { state.delete(e) }
            }
        }
    }
}

@Composable
private fun TvManageRow(key: String, icon: GuguIcon, text: String, danger: Boolean = false, enabled: Boolean = true, hint: String? = null, onClick: () -> Unit) {
    val colors = Guga.colors
    TvFocusable(onClick = onClick, modifier = Modifier.fillMaxWidth(), key = key, label = text) { focused ->
        val ink = when {
            !enabled -> if (focused) colors.onInk.copy(alpha = 0.5f) else colors.faint
            danger -> if (focused) GugaColors.SEAL_ON_INK else colors.seal
            focused -> colors.onInk
            else -> colors.ink
        }
        Row(Modifier.fillMaxWidth().heightIn(min = TvServerSpec.MANAGE_ROW).padding(horizontal = 18.dp, vertical = 6.dp).testTag("tv-$key"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(icon, ink, 26.dp)
            Column {
                BasicText(text, style = Guga.song(25.sp, FontWeight.Bold, ink), maxLines = 1)
                if (hint != null) BasicText(hint, style = Guga.song(19.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** 一格要打字的（搜索、备注）：平时是焦点块，确定 = 换成真的文本框 + 系统输入法（登录页一屏表单的同一个 [TvEditText]）。 */
@Composable
private fun TvLineField(key: String, label: String, value: String, placeholder: String, editing: Boolean, onEdit: () -> Unit, onChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = Guga.colors
    TvFocusable(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().then(if (editing) Modifier.background(colors.paper).border(3.dp, colors.spot) else Modifier).drawBehind { if (!editing) drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) },
        key = key,
        label = label,
    ) { focused ->
        val ink = if (focused && !editing) colors.onInk else colors.ink
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp).testTag(if (editing) "tv-$key-editing" else "tv-$key")) {
            BasicText(label, style = Guga.song(19.sp, color = if (focused && !editing) colors.onInk.copy(alpha = 0.75f) else colors.spot), maxLines = 1)
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(44.dp), contentAlignment = Alignment.CenterStart) {
                if (editing) {
                    TvEditText(value, onChange, false, KeyboardType.Text, ImeAction.Done, onDone) { onDone() }
                } else if (value.isEmpty()) {
                    BasicText(placeholder, style = Guga.song(22.sp, color = if (focused) colors.onInk.copy(alpha = 0.55f) else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    BasicText(value, style = Guga.song(27.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TvAddRow(onClick: () -> Unit) {
    val colors = Guga.colors
    TvFocusable(onClick = onClick, modifier = Modifier.fillMaxWidth(), key = "add", label = ServerSwitchCopy.ADD) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 20.dp).testTag("tv-server-add"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Icon(GuguIcon.Plus, ink, 22.dp)
            BasicText(ServerSwitchCopy.ADD, style = Guga.song(26.sp, FontWeight.Bold, ink), maxLines = 1)
        }
    }
}
