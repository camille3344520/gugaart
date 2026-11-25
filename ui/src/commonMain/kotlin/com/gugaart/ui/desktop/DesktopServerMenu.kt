package com.gugaart.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.components.Rule
import com.gugaart.ui.servers.PingState
import com.gugaart.ui.servers.ServerListLook
import com.gugaart.ui.servers.ServerListState
import com.gugaart.ui.servers.ServerSwitcherList
import com.gugaart.ui.servers.currentServerEntry
import com.gugaart.ui.servers.currentServerSwitcher
import com.gugaart.ui.servers.currentServerTitle
import com.gugaart.ui.servers.key
import com.gugaart.ui.servers.rememberServerListState
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.glass.MaterialName
import com.gugugaga.ds.glass.glass
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlin.math.roundToInt

/** 顶栏服务器胶囊和下拉的数（rem）。 */
object DeskServerSpec {
    /** 胶囊里的名字最宽多少（再长省略）。 */
    const val PILL_NAME_MAX = 16f

    /** 下拉的宽；离胶囊下沿。 */
    const val MENU_W = 40f
    const val MENU_GAP = 0.8f
}

/**
 * 电脑 / Mac 的换服务器（1.5.0 A2-UI）：顶栏账号圆钮左边一枚玻璃胶囊（当前服务器名 + 在线小点），点一下 = 胶囊下面垂下一张纸片，
 * 里面是三端共用的 [ServerSwitcherList]（取代原来账号菜单里的「换一个片库」对话框；账号菜单那一项保留，打开的就是这张下拉）。
 *
 * 快捷键 `Ctrl Shift S`（Mac `⌘⇧S`，命令表 [DesktopCommand.Servers]）开 / 关；开着的时候按 1–9 = 切到第几台（行首印着数字）。
 * macOS 菜单栏「片库 › 换服务器」点一台 = [switchTo]：切不过去（连不上 / 别的失败）就把下拉打开，原因写在那一行下面。
 * 纸片不是玻璃（docs/glass.md：只有固定导航是玻璃）；Esc / 点纸外收起。
 */
object DesktopServerMenu {
    var open: Boolean by mutableStateOf(false)

    /** 胶囊在窗口里的位置（px）：下拉挂在它下面。 */
    internal var anchor: Rect? by mutableStateOf(null)

    /** 下拉开着时按的数字（1–9），由 [Host] 接走。 */
    internal var pendingIndex: Int? by mutableStateOf(null)

    /** 菜单栏点的那一台（`serverId/userId`），由 [Host] 接走。 */
    internal var pendingKey: String? by mutableStateOf(null)

    /** 画着的那份列表状态（Esc 先收「管理」那一块）。 */
    internal var live: ServerListState? = null

    fun toggle() {
        open = !open
        if (!open) live?.closeManage()
    }

    fun close() {
        open = false
        live?.closeManage()
    }

    /** Esc：先收推开的管理，再收下拉。返回是否接住。 */
    fun escape(): Boolean {
        if (!open) return false
        val s = live
        if (s?.managing != null) s.closeManage() else open = false
        return true
    }

    /** 下拉开着时的数字键：切到第 [n] 台（1 起）。没开着 = 不接。 */
    fun pickNth(n: Int): Boolean {
        if (!open || n !in 1..9) return false
        pendingIndex = n
        return true
    }

    /** macOS 菜单栏「片库 › 换服务器」里点的那一台。 */
    fun switchTo(serverId: String, userId: String) {
        pendingKey = "$serverId/$userId"
    }

    /** 同上，给的是那一行的 key（`serverId/userId`，[com.gugaart.ui.servers.key]）。 */
    fun switchToKey(key: String) {
        pendingKey = key
    }

    /** 单测 / 截图：收干净。 */
    fun reset() {
        open = false
        pendingIndex = null
        pendingKey = null
        live = null
    }

    /** 顶栏的胶囊（`Bar` 材质，和搜索胶囊同一种玻璃）：在线小点 + 名字 + 小折角。 */
    @Composable
    fun Pill(modifier: Modifier = Modifier) {
        val colors = Guga.colors
        val g = LocalGraph.current
        val switcher = currentServerSwitcher()
        val session by g.session.session.collectAsState()
        // 胶囊上的小点要有数：每换一次会话 ping 一遍（每台 2 秒超时，并行）
        LaunchedEffect(switcher, session?.serverId, session?.userId) { switcher.refreshPings() }
        val name = currentServerTitle(switcher) ?: "服务器"
        val ping = currentServerEntry(switcher)?.ping ?: PingState.Unknown
        val press = rememberPressState()
        Row(
            modifier
                .fillMaxHeight()
                .onGloballyPositioned { anchor = it.boundsInRoot() }
                .deskFocusRing(press, inside = true)
                .glass(MaterialName.Bar, Guga.rem(2.2f), press)
                .pressable(press, label = "换服务器：现在是 $name") { toggle() }
                .padding(start = Guga.rem(1.4f), end = Guga.rem(1.3f))
                .testTag("desktop-server-pill"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f)),
        ) {
            val dot = when (ping) {
                is PingState.Online -> colors.online
                PingState.Offline -> colors.ink.copy(alpha = 0.32f)
                else -> null
            }
            if (dot != null) androidx.compose.foundation.layout.Box(Modifier.size(Guga.rem(0.7f)).background(dot, CircleShape).testTag("desktop-server-pill-dot"))
            BasicText(name, Modifier.widthIn(max = Guga.rem(DeskServerSpec.PILL_NAME_MAX)), style = Guga.song(rsp(1.4f), FontWeight.SemiBold, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(if (open) GuguIcon.ChevronUp else GuguIcon.ChevronDown, colors.ink.copy(alpha = 0.7f), Guga.rem(1.2f))
        }
    }

    /** 下拉本身（画在电脑的玻璃排那一层，跟着胶囊）。关着也在组合里：菜单栏点的、数字键按的都从这里接。 */
    @Composable
    fun Host() {
        val colors = Guga.colors
        val density = LocalDensity.current
        val switcher = currentServerSwitcher()
        val state = rememberServerListState(switcher)
        live = state
        state.onClose = { close() }
        state.onProblem = { open = true }
        val entries by switcher.entries.collectAsState()
        // 菜单栏点的那一台：直接切（成功不打开下拉；切不过去才打开，看那一行下面的原因）
        LaunchedEffect(pendingKey) {
            val k = pendingKey ?: return@LaunchedEffect
            pendingKey = null
            switcher.entries.value.firstOrNull { it.key == k }?.let(state::pick)
        }
        // 数字键：第 N 行（按现在显示的那几行数）
        LaunchedEffect(pendingIndex) {
            val n = pendingIndex ?: return@LaunchedEffect
            pendingIndex = null
            state.visible(switcher.entries.value).getOrNull(n - 1)?.let(state::pick)
        }
        if (!open) return
        var origin by androidx.compose.runtime.remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { origin = it.positionInRoot() }
                // 点纸外 = 收起（这一下不再传给后面的页面）
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (e.type == PointerEventType.Press && e.changes.none { it.isConsumed }) close()
                            e.changes.forEach { it.consume() }
                        }
                    }
                }
                .testTag("desktop-server-scrim"),
        ) {
            val a = anchor
            val w = with(density) { Guga.rem(DeskServerSpec.MENU_W).toPx() }
            val edge = with(density) { Guga.dimens.margin.toPx() }
            val maxW = constraints.maxWidth.toFloat()
            // 右边和胶囊的右边对齐；左边不出页边
            val x = ((a?.right ?: (maxW - edge)) - origin.x - w).coerceIn(edge.coerceAtMost(maxW - w), (maxW - edge - w).coerceAtLeast(0f))
            val y = (a?.bottom ?: 0f) - origin.y + with(density) { Guga.rem(DeskServerSpec.MENU_GAP).toPx() }
            val room = with(density) { (constraints.maxHeight - y).toDp() } - Guga.rem(2.4f)
            Column(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .width(Guga.rem(DeskServerSpec.MENU_W))
                    .background(slipColor())
                    .border(1.dp, colors.ink.copy(alpha = 0.7f))
                    // 纸上的点击不算「点纸外」
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                    .testTag("desktop-server-menu"),
            ) {
                Row(Modifier.fillMaxWidth().padding(start = Guga.rem(1.6f), end = Guga.rem(1.6f), top = Guga.rem(1.6f), bottom = Guga.rem(1.2f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f))) {
                    BasicText("换一台服务器", Modifier.alignByBaseline(), style = Guga.song(rsp(2f), FontWeight.Black, letterSpacing = 0.06.em), maxLines = 1)
                    BasicText("SERVERS", Modifier.alignByBaseline(), style = Guga.numeral(rsp(1.1f), colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    BasicText("${entries.size} 台", Modifier.alignByBaseline(), style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1)
                }
                Rule()
                val searchH = if (entries.size > com.gugaart.ui.servers.SEARCH_FROM) Guga.rem(5f) else 0.dp
                ServerSwitcherList(
                    state,
                    ServerListLook.Desktop,
                    Modifier.padding(top = Guga.rem(0.6f)),
                    maxHeight = (room - Guga.rem(14f) - searchH).coerceAtLeast(Guga.rem(16f)),
                )
                Rule()
                val mod = LocalDesktopHost.current.modifier
                BasicText(
                    "${DesktopCommand.Servers.hint(mod) ?: "Ctrl Shift S"} 开 / 关 · 1–9 直接切到第几台 · 右键改备注 / 删除",
                    Modifier.padding(horizontal = Guga.rem(1.6f), vertical = Guga.rem(1.1f)).testTag("desktop-server-hint"),
                    style = Guga.note(rsp(1.1f), colors.faint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
