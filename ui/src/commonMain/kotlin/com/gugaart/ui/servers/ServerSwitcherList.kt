package com.gugaart.ui.servers

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.InputLine
import com.gugaart.ui.components.OutlineTag
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.RadioMark
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.data.FakeOtherServersSettings
import com.gugaart.ui.data.OtherServersPrefsStore
import com.gugaart.ui.data.rememberOtherServersPrefs
import com.gugaart.ui.desktop.DeskButton
import com.gugaart.ui.desktop.deskFocusRing
import com.gugaart.ui.desktop.hover
import com.gugaart.ui.desktop.onRightClick
import com.gugaart.ui.desktop.rememberHover
import com.gugaart.ui.desktop.rsp
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.app.ui.LocalOptionalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.LocalMotionPrefs
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/*
 * 换服务器的列表（1.5.0 A1-UI，规划 docs/plan-multi-server.md §2.1）：三端共用这一个组件——手机的贴底纸片（[ServerSheet]）、
 * 电脑 / Mac 顶栏胶囊下面的下拉（`desktop/DesktopServerMenu.kt`）、电视的页边（`tv/TvServerPanel.kt`）都画它，只换外观档（[ServerListLook]）。
 *
 * 一行：第一行 = 备注（没有就服务器名），粗；第二行 = `账号 · 主机`（只留主机，不要协议 / 端口 / 路径）。右端 = 在线状态：
 * 在线 = 小绿点 +「23 ms」；正在检查 = 淡点轻轻地呼吸 +「…」；连不上 = 灰点 +「连不上」；要重新登录 = 小签「要重新登录」。
 * 正在用的那台总在第一个（数据层排好的：当前 → 最近 → 没用过），打勾 / 选中记号按那一端的样子。
 *
 * 点 / 确定一行 = [ServerSwitcher.switchTo]：成功 = 收起（第一帧就是那台的缓存，数据层管）；连不上 = 不动，那一行下面写一句；
 * 要重新登录 = 去那台的登录页（上游 `Route.Login(switch = true)`，和「我的」页原来的做法一样）；别的失败 = 那一行下面写原因。
 * 长按（手机）/ 右键（电脑）/ 菜单键或长按确定（电视）= 在那一行下面推开管理：改备注 / 用别的账号登录 / 删除这台服务器（正在用的那台不能删）。
 * 最后一行「添加服务器」= 上游 `Route.Servers`。超过 [SEARCH_FROM] 台时顶上有搜索框（数据层的 [ServerSwitcher.search]，认拼音首字母）。
 */

/** 超过几台出搜索框。 */
const val SEARCH_FROM = 6

/** 三端的外观档：字号、行高、选中记号、手势（长按 / 右键 / 菜单键）不同，内容和动作同一份。 */
enum class ServerListLook { Phone, Desktop, Tv }

/** 一行的 key（同一台服务器的不同账号各是一行）。 */
val ServerEntry.key: String get() = "$serverId/$userId"

/**
 * 只留主机：`http://192.168.1.10:8096` → `192.168.1.10`、`https://emby1.example.com/emby` → `emby1.example.com`、
 * `http://[::1]:8096` → `::1`。带账号密码的地址（`user:pw@host`）只留 `host`。纯函数，有单测。
 */
fun serverHost(address: String): String {
    val trimmed = address.trim()
    val noScheme = if ("://" in trimmed) trimmed.substringAfter("://") else trimmed
    val authority = noScheme.takeWhile { it != '/' && it != '?' && it != '#' }
    val hostPort = authority.substringAfterLast('@')
    val host = if (hostPort.startsWith("[")) hostPort.removePrefix("[").substringBefore(']') else hostPort.substringBefore(':')
    return host.ifEmpty { trimmed }
}

/** 主机带端口（`127.0.0.1:18397`）：没写端口的地址 = 只有主机。 */
fun serverHostPort(address: String): String {
    val host = serverHost(address)
    val trimmed = address.trim()
    val noScheme = if ("://" in trimmed) trimmed.substringAfter("://") else trimmed
    val authority = noScheme.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@')
    val port = authority.substringAfterLast(']').substringAfter(':', "").takeIf { p -> p.isNotEmpty() && p.all { it.isDigit() } }
    return if (port != null) "$host:$port" else host
}

/**
 * 哪些主机要带上端口才分得清：两台（不同的服务器）在同一台机器上（同一个 IP、不同端口）时，只写主机就一模一样了。纯函数，有单测。
 */
fun hostsNeedingPort(entries: List<ServerEntry>): Set<String> =
    entries.groupBy { serverHost(it.address) }.filterValues { list -> list.map { it.serverId }.distinct().size > 1 }.keys

/** 第一行：备注，没有就服务器名（服务器名也空 = 主机）。 */
fun serverTitle(e: ServerEntry): String = e.note?.trim()?.takeIf { it.isNotEmpty() } ?: e.name.trim().ifEmpty { serverHost(e.address) }

/** 第二行：`账号 · 主机`；[withPort]：同一台机器上不止一台服务器时带端口（[hostsNeedingPort]）。 */
fun serverSubtitle(e: ServerEntry, withPort: Boolean = false): String =
    listOf(e.userName.trim(), if (withPort) serverHostPort(e.address) else serverHost(e.address)).filter { it.isNotEmpty() }.joinToString(" · ")

/** 右端的字：「23 ms」/「1.8 s」/「…」/「连不上」；还没 ping 过 = null（什么都不画）。纯函数，有单测。 */
fun pingText(p: PingState): String? = when (p) {
    is PingState.Online -> if (p.ms < 1000) "${p.ms} ms" else "${(p.ms / 100) / 10.0} s"
    PingState.Checking -> "…"
    PingState.Offline -> "连不上"
    PingState.Unknown -> null
}

/** 界面上的几句话（三端同一份）。 */
object ServerSwitchCopy {
    const val OFFLINE = "连不上这台服务器，没有切换"
    fun failed(reason: String) = "没有切换：$reason"
    const val NEEDS_LOGIN = "要重新登录"
    const val CURRENT_CANNOT_DELETE = "正在用的这台不能删：先换到别的服务器"
    const val SEARCH_HINT = "搜名字、备注、地址，拼音首字母也行"
    const val NO_MATCH = "没有对得上的服务器"
    const val ADD = "添加服务器"
    fun deleteTitle(name: String) = "删除「$name」？"
    const val DELETE_TEXT = "这台服务器和保存在这台设备上的账号会被移除；服务器上的片子和记录都不受影响。"
}

/**
 * 「要重新登录」的记号：哪一行上一次切换得到 [SwitchResult.NeedsLogin]（令牌失效）。进程里记着，下次打开列表那一行带小签；
 * 那一行切换成功就擦掉。（数据层以后能直接报 401 时换成它的字段，界面不变。）
 */
object ServerLoginMarks {
    private val marks = mutableStateMapOf<String, Boolean>()
    fun has(key: String): Boolean = marks[key] == true
    fun mark(key: String) { marks[key] = true }
    fun clear(key: String) { marks.remove(key) }
    fun clearAll() = marks.clear()
}

/** 测试 / 截图：换掉数据层（`FakeServerSwitcher(3)`）。应用里是 null = 真实现 [ServerDirectory]（每个 `AppGraph` 一份）。 */
val LocalServerSwitcher = staticCompositionLocalOf<ServerSwitcher?> { null }

/** 测试 / 截图：换掉「从其它服务器找片源」的存储（[FakeOtherServersSettings]）。应用里是 null = 真实现。 */
val LocalOtherServersPrefs = staticCompositionLocalOf<OtherServersPrefsStore?> { null }

private var sharedFor: AppGraph? = null
private var sharedSwitcher: ServerSwitcher? = null

/**
 * 一个 `AppGraph` 只建一份真实现（[ServerDirectory] 在 app 生命周期的 scope 上挂收集器：每个调用点各建一份会越攒越多，
 * 各自的 ping 结果也对不上）。顶栏胶囊、下拉、手机报头、电视页边、macOS 菜单栏看到的是同一份列表。
 */
fun sharedServerSwitcher(graph: AppGraph): ServerSwitcher {
    val cached = sharedSwitcher
    if (sharedFor === graph && cached != null) return cached
    return ServerDirectory(graph.session, graph.deps.local, graph.scope, transport = graph.deps.transport).also {
        sharedFor = graph
        sharedSwitcher = it
    }
}

/** 界面拿数据层：测试给了 [LocalServerSwitcher] 就用它，否则这个 `AppGraph` 的那一份。 */
@Composable
fun currentServerSwitcher(): ServerSwitcher {
    LocalServerSwitcher.current?.let { return it }
    val graph = LocalGraph.current
    return remember(graph) { sharedServerSwitcher(graph) }
}

/** 设置页拿「从其它服务器找片源」的存储：测试给了 [LocalOtherServersPrefs] 就用它。 */
@Composable
fun currentOtherServersPrefs(switcher: ServerSwitcher = currentServerSwitcher()): OtherServersPrefsStore =
    LocalOtherServersPrefs.current ?: rememberOtherServersPrefs(switcher)

/** 推开的管理那一块现在是哪一步。 */
enum class ManageStage { Menu, Rename, Delete }

/**
 * 列表的状态（三端同一份）：搜索词、正在切的那一行、每一行下面的那句话、推开着管理的那一行。
 * 切换 / 改备注 / 删除放在 app 生命周期的 [scope] 上跑（列表收起了也不会把切到一半的会话掐断）；
 * 切完以后的界面动作（收起、写那一句、去登录页）回到界面的 [ui] 上做。
 */
@Stable
class ServerListState(val switcher: ServerSwitcher, private val graph: AppGraph?, private val scope: CoroutineScope, private val ui: CoroutineScope = scope) {
    var query by mutableStateOf("")
    var busy by mutableStateOf<String?>(null)
        private set
    private val notes = mutableStateMapOf<String, String>()
    var managing by mutableStateOf<String?>(null)
        private set
    var stage by mutableStateOf(ManageStage.Menu)
    var draft by mutableStateOf("")

    /** 切过去了 / 去登录页了 / 去添加了：收起列表（由画它的那一端给）。 */
    var onClose: () -> Unit = {}

    /** 切换没成（连不上 / 别的失败）：列表收着的时候（macOS 菜单栏、数字键）要把它打开，让那一行下面的话看得见。 */
    var onProblem: () -> Unit = {}

    fun noteOf(e: ServerEntry): String? = notes[e.key]

    /** 现在显示哪些行：台数超过 [SEARCH_FROM] 且有搜索词时按数据层的搜索筛（保持数据层的顺序）。 */
    fun visible(entries: List<ServerEntry>): List<ServerEntry> =
        if (entries.size > SEARCH_FROM && query.isNotBlank()) switcher.search(query) else entries

    /** 点 / 确定一行。 */
    fun pick(e: ServerEntry) {
        if (busy != null) return
        if (e.isCurrent) {
            onClose()
            return
        }
        val k = e.key
        busy = k
        notes.remove(k)
        managing = null
        val work = scope.async {
            try {
                switcher.switchTo(e.serverId, e.userId)
            } catch (c: CancellationException) {
                throw c
            } catch (x: Exception) {
                SwitchResult.Failed(x.message ?: "切换失败")
            }
        }
        ui.launch {
            val r = try {
                work.await()
            } catch (c: CancellationException) {
                busy = null
                throw c
            }
            busy = null
            when (r) {
                SwitchResult.Ok -> {
                    ServerLoginMarks.clear(k)
                    onClose()
                }
                SwitchResult.Offline -> {
                    notes[k] = ServerSwitchCopy.OFFLINE
                    onProblem()
                }
                SwitchResult.NeedsLogin -> {
                    // 令牌失效：去这台的登录页重新输密码（密码由用户自己输入；上游登录页认 switch = true）
                    ServerLoginMarks.mark(k)
                    onClose()
                    graph?.nav?.navigate(Route.Login(switch = true, server = e.address, user = e.userName))
                }
                is SwitchResult.Failed -> {
                    notes[k] = ServerSwitchCopy.failed(r.reason)
                    onProblem()
                }
            }
        }
    }

    /** 长按 / 右键 / 菜单键：在这一行下面推开管理（再来一次 = 收起）。 */
    fun manage(e: ServerEntry) {
        if (managing == e.key) {
            closeManage()
            return
        }
        managing = e.key
        stage = ManageStage.Menu
        draft = e.note.orEmpty()
    }

    fun closeManage() {
        managing = null
        stage = ManageStage.Menu
    }

    fun saveNote(e: ServerEntry) {
        val note = draft.trim().ifEmpty { null }
        closeManage()
        scope.launch { runCatching { switcher.rename(e.serverId, note) } }
    }

    fun delete(e: ServerEntry) {
        if (e.isCurrent) return
        closeManage()
        notes.remove(e.key)
        ServerLoginMarks.clear(e.key)
        scope.launch { runCatching { switcher.remove(e.serverId) } }
    }

    /** 用别的账号登录这一台（原来「换一个片库」对话框里每台下面的「+ 登录别的账号」）。 */
    fun loginOther(e: ServerEntry) {
        closeManage()
        onClose()
        graph?.nav?.navigate(Route.Login(switch = true, server = e.address))
    }

    fun add() {
        closeManage()
        onClose()
        graph?.nav?.navigate(Route.Servers)
    }
}

/** 在界面里拿一份列表状态（跟着 [switcher] 记）。 */
@Composable
fun rememberServerListState(switcher: ServerSwitcher = currentServerSwitcher()): ServerListState {
    val graph = LocalOptionalGraph.current
    val ui = androidx.compose.runtime.rememberCoroutineScope()
    return remember(switcher, graph) { ServerListState(switcher, graph, graph?.scope ?: ui, ui) }
}

/** 测试缝：最近画出来的那份列表状态（测试从外面「点一行」）。应用里没有人读它。 */
object ServerListProbe {
    var last: ServerListState? = null
        internal set
}

/** 一端的字号 / 尺寸。电脑的数跟着 rem 走。 */
internal class ServerLookSpec(
    val title: TextUnit,
    val sub: TextUnit,
    val ping: TextUnit,
    val tag: TextUnit,
    val tagLine: Dp,
    val dot: Dp,
    val rowMin: Dp,
    val rowPad: Dp,
    val markGap: Dp,
    val icon: Dp,
)

@Composable
internal fun serverLookSpec(look: ServerListLook): ServerLookSpec = when (look) {
    ServerListLook.Phone -> ServerLookSpec(Guga.type.row, Guga.type.caption, Guga.type.caption, 11.sp, 1.dp, 7.dp, 62.dp, 10.dp, 16.dp, 14.dp)
    ServerListLook.Desktop -> ServerLookSpec(rsp(1.55f), rsp(1.2f), rsp(1.2f), rsp(1.05f), 1.dp, Guga.rem(0.7f), Guga.rem(5.6f), Guga.rem(0.9f), Guga.rem(1.2f), Guga.rem(1.4f))
    ServerListLook.Tv -> ServerLookSpec(27.sp, 21.sp, 21.sp, 18.sp, 2.dp, 12.dp, 96.dp, 10.dp, 22.dp, 26.dp)
}

/**
 * 在线状态：一个小圆点 + 字。在线 = 绿点 + 毫秒；正在检查 = 淡点轻轻呼吸（减弱动效时不动）+「…」；连不上 = 灰点 +「连不上」。
 * [onInk]：画在米白焦点块上（电视的焦点行）——字换墨色，绿点照旧。
 */
@Composable
fun ServerPingMark(ping: PingState, dot: Dp, text: TextUnit, modifier: Modifier = Modifier, onInk: Boolean = false) {
    val label = pingText(ping) ?: return
    val colors = Guga.colors
    val ink = if (onInk) colors.onInk else colors.ink
    val dotColor = when (ping) {
        is PingState.Online -> colors.online
        PingState.Offline -> ink.copy(alpha = 0.32f)
        else -> ink.copy(alpha = 0.5f)
    }
    val breathing = ping == PingState.Checking && !LocalMotionPrefs.current.reducedMotion
    val pulse = if (breathing) {
        val t = rememberInfiniteTransition(label = "ping-pulse")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ping-alpha").value
    } else 1f
    Row(modifier.testTag("server-ping"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dot * 0.9f)) {
        Box(Modifier.size(dot).alpha(pulse).background(dotColor, CircleShape))
        BasicText(
            label,
            style = if (ping is PingState.Online || ping == PingState.Checking) Guga.numeral(text, ink.copy(alpha = if (onInk) 0.75f else 0.64f)) else Guga.song(text, color = ink.copy(alpha = if (onInk) 0.75f else 0.64f)),
            maxLines = 1,
        )
    }
}

/** 一行的内容（三端共用）：两行字 + 小签 + 右端的在线状态。[onInk]：电视的焦点行（米白实底）。 */
@Composable
internal fun ServerRowText(e: ServerEntry, spec: ServerLookSpec, modifier: Modifier = Modifier, onInk: Boolean = false, dim: Boolean = false, withPort: Boolean = false) {
    val colors = Guga.colors
    val ink = if (onInk) colors.onInk else colors.ink
    val soft = if (onInk) colors.onInk.copy(alpha = 0.7f) else colors.dim
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).alpha(if (dim) 0.5f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(serverTitle(e), Modifier.weight(1f, fill = false).testTag("server-title"), style = Guga.song(spec.title, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ServerLoginMarks.has(e.key)) {
                    OutlineTag(ServerSwitchCopy.NEEDS_LOGIN, spec.tag, spec.tagLine, ink, Modifier.padding(start = spec.markGap * 0.6f).testTag("server-needs-login"))
                }
            }
            BasicText(serverSubtitle(e, withPort), Modifier.padding(top = spec.rowPad * 0.3f), style = Guga.song(spec.sub, color = soft), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        ServerPingMark(e.ping, spec.dot, spec.ping, Modifier.padding(start = spec.markGap), onInk = onInk)
    }
}

/**
 * 手机 / 电脑的列表（电视的焦点行在 `tv/TvServerPanel.kt`，用同一份 [ServerListState] 和 [ServerRowText]）。
 * [maxHeight]：列表这一段最多多高（再多就在里面滚）；搜索框和「添加服务器」不跟着滚。
 */
@Composable
fun ServerSwitcherList(state: ServerListState, look: ServerListLook, modifier: Modifier = Modifier, maxHeight: Dp = Dp.Unspecified) {
    val colors = Guga.colors
    val spec = serverLookSpec(look)
    val entries by state.switcher.entries.collectAsState()
    // 打开列表 = 并行 ping 一遍（每台 2 秒超时，结果陆续写回来）
    LaunchedEffect(state) { state.switcher.refreshPings() }
    ServerListProbe.last = state
    Column(modifier.testTag("server-list")) {
        val side = if (look == ServerListLook.Desktop) Guga.rem(1.6f) else 0.dp
        if (entries.size > SEARCH_FROM) {
            Row(Modifier.fillMaxWidth().padding(start = side, end = side, bottom = spec.rowPad), verticalAlignment = Alignment.CenterVertically) {
                Icon(GuguIcon.Search, colors.dim, spec.icon)
                Spacer(Modifier.width(spec.markGap * 0.75f))
                InputLine(state.query, { state.query = it }, ServerSwitchCopy.SEARCH_HINT, Modifier.weight(1f), tag = "server-search")
            }
        }
        val rows = state.visible(entries)
        val ported = hostsNeedingPort(entries)
        Column(Modifier.fillMaxWidth().then(if (maxHeight != Dp.Unspecified) Modifier.heightIn(max = maxHeight) else Modifier).verticalScroll(rememberScrollState())) {
            if (rows.isEmpty()) {
                BasicText(ServerSwitchCopy.NO_MATCH, Modifier.padding(horizontal = side, vertical = spec.rowPad * 2).testTag("server-no-match"), style = Guga.song(spec.sub, color = colors.faint), maxLines = 1)
            }
            rows.forEachIndexed { i, e ->
                ServerRow(state, e, i, look, spec, serverHost(e.address) in ported)
                if (state.managing == e.key) ManageStrip(state, e, look, spec)
                Rule()
            }
        }
        AddRow(state, look, spec)
    }
}

@Composable
private fun ServerRow(state: ServerListState, e: ServerEntry, index: Int, look: ServerListLook, spec: ServerLookSpec, withPort: Boolean) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val desktop = look == ServerListLook.Desktop
    val busy = state.busy == e.key
    Column(Modifier.fillMaxWidth().testTag(if (e.isCurrent) "server-row-current" else "server-row")) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = spec.rowMin)
                .then(
                    if (desktop) {
                        Modifier.hover(hover)
                            .drawBehind { if (hover.over || press.focused || state.managing == e.key) drawRect(colors.ink.copy(alpha = 0.08f)) }
                            .deskFocusRing(press)
                            .onRightClick { state.manage(e) }
                    } else Modifier.pressGraphics(press),
                )
                .pressable(press, label = serverTitle(e), onLongClick = if (desktop) null else ({ state.manage(e) }), onClick = { state.pick(e) })
                .padding(horizontal = if (desktop) Guga.rem(1.6f) else 0.dp, vertical = spec.rowPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (look) {
                // 电脑：左边一格 = 正在用的那台打校对勾，别的写数字（下拉开着时按 1–9 直接切到第几台）
                ServerListLook.Desktop -> Box(Modifier.width(Guga.rem(2.4f)), contentAlignment = Alignment.CenterStart) {
                    if (e.isCurrent) ProofTick(true, size = Guga.rem(1.1f), box = false)
                    else if (index < 9) BasicText("${index + 1}", style = Guga.numeral(rsp(1.25f), colors.faint), maxLines = 1)
                }
                // 手机：和「版本」纸片同一种选中记号（实心小方块 = 正在用；空心 = 别的）
                else -> RadioMark(e.isCurrent, Modifier.padding(end = spec.markGap))
            }
            ServerRowText(e, spec, Modifier.weight(1f), dim = busy, withPort = withPort)
        }
        val note = if (busy) "正在切换……" else state.noteOf(e)
        if (note != null) {
            BasicText(
                note,
                Modifier.padding(start = if (desktop) Guga.rem(4f) else 9.dp + spec.markGap, bottom = spec.rowPad, end = if (desktop) Guga.rem(1.6f) else 0.dp).testTag(if (busy) "server-busy" else "server-note"),
                style = Guga.song(spec.sub, color = if (busy) colors.dim else colors.seal),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 推开的管理：左边一道专色竖线（和「我的」页的切换面板同一种），在这一行下面把后面的行推开，不盖东西。 */
@Composable
private fun ManageStrip(state: ServerListState, e: ServerEntry, look: ServerListLook, spec: ServerLookSpec) {
    val colors = Guga.colors
    val desktop = look == ServerListLook.Desktop
    val inset = if (desktop) Guga.rem(4f) else 9.dp + spec.markGap
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = inset - (if (desktop) Guga.rem(1.2f) else 10.dp), end = if (desktop) Guga.rem(1.6f) else 0.dp, bottom = spec.rowPad)
            .drawBehind { drawLine(colors.spot, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx()) }
            .padding(start = if (desktop) Guga.rem(1.2f) else 10.dp)
            .testTag("server-manage"),
    ) {
        when (state.stage) {
            ManageStage.Menu -> {
                ManageRow(GuguIcon.Pencil, "改备注", spec, look, tag = "server-manage-rename") {
                    state.draft = e.note.orEmpty()
                    state.stage = ManageStage.Rename
                }
                ManageRow(GuguIcon.LogIn, "用别的账号登录", spec, look, tag = "server-manage-login") { state.loginOther(e) }
                ManageRow(GuguIcon.Trash, "删除这台服务器", spec, look, enabled = !e.isCurrent, danger = true, hint = if (e.isCurrent) ServerSwitchCopy.CURRENT_CANNOT_DELETE else null, tag = "server-manage-delete") {
                    state.stage = ManageStage.Delete
                }
            }
            ManageStage.Rename -> {
                BasicText("备注（留空 = 用服务器名「${e.name}」）", Modifier.padding(top = spec.rowPad * 0.6f), style = Guga.song(spec.sub, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                InputLine(state.draft, { state.draft = it }, e.name, Modifier.padding(top = spec.rowPad * 0.4f), tag = "server-note-input")
                ManageButtons(look, "保存", { state.saveNote(e) }, danger = false) { state.closeManage() }
            }
            ManageStage.Delete -> {
                BasicText(ServerSwitchCopy.deleteTitle(serverTitle(e)), Modifier.padding(top = spec.rowPad * 0.6f), style = Guga.song(spec.title * 0.94f, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(ServerSwitchCopy.DELETE_TEXT, Modifier.padding(top = spec.rowPad * 0.4f).testTag("server-delete-text"), style = Guga.song(spec.sub, color = colors.dim, lineHeight = spec.sub * 1.55f), maxLines = 3)
                ManageButtons(look, "删除", { state.delete(e) }, danger = true) { state.closeManage() }
            }
        }
    }
}

@Composable
private fun ManageRow(icon: GuguIcon, text: String, spec: ServerLookSpec, look: ServerListLook, enabled: Boolean = true, danger: Boolean = false, hint: String? = null, tag: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val desktop = look == ServerListLook.Desktop
    val ink = when {
        !enabled -> colors.faint
        danger -> colors.seal
        else -> colors.ink
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (desktop) Guga.rem(3.8f) else Guga.dimens.minTarget)
            .then(if (desktop) Modifier.hover(hover).drawBehind { if (enabled && (hover.over || press.focused)) drawRect(colors.ink.copy(alpha = 0.08f)) }.deskFocusRing(press) else Modifier.pressGraphics(press))
            .pressable(press, enabled = enabled, label = text, onClick = onClick)
            .padding(horizontal = if (desktop) Guga.rem(0.8f) else 0.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spec.markGap * 0.75f),
    ) {
        Icon(icon, ink, spec.icon)
        Column(Modifier.weight(1f)) {
            BasicText(text, style = Guga.song(spec.title * 0.9f, FontWeight.Bold, ink), maxLines = 1)
            if (hint != null) BasicText(hint, style = Guga.song(spec.sub * 0.92f, color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 改备注 / 删除的两个钮：电脑 = 直角小按钮（主动作在右）；手机 = 文字链接（和二次确认纸片一样：主动作在前）。 */
@Composable
private fun ManageButtons(look: ServerListLook, confirm: String, onConfirm: () -> Unit, danger: Boolean, onCancel: () -> Unit) {
    val colors = Guga.colors
    if (look == ServerListLook.Desktop) {
        Row(Modifier.fillMaxWidth().padding(top = Guga.rem(1.2f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f), Alignment.End)) {
            DeskButton("算了", onCancel, icon = null, small = true, outline = true)
            DeskButton(confirm, onConfirm, Modifier.testTag("server-manage-confirm"), icon = null, small = true, danger = danger)
        }
    } else {
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            TextLink(confirm, onConfirm, Modifier.testTag("server-manage-confirm"), color = if (danger) colors.seal else colors.ink)
            TextLink("算了", onCancel, color = colors.dim)
        }
    }
}

@Composable
private fun AddRow(state: ServerListState, look: ServerListLook, spec: ServerLookSpec) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val desktop = look == ServerListLook.Desktop
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (desktop) Guga.rem(4.4f) else 52.dp)
            .then(if (desktop) Modifier.hover(hover).drawBehind { if (hover.over || press.focused) drawRect(colors.ink.copy(alpha = 0.08f)) }.deskFocusRing(press) else Modifier.pressGraphics(press))
            .pressable(press, label = ServerSwitchCopy.ADD, onClick = { state.add() })
            .padding(horizontal = if (desktop) Guga.rem(1.6f) else 0.dp)
            .testTag("server-add"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(if (desktop) Guga.rem(2.4f) else 9.dp + spec.markGap), contentAlignment = Alignment.CenterStart) { Icon(GuguIcon.Plus, colors.ink, spec.icon * 0.9f) }
        BasicText(ServerSwitchCopy.ADD, style = Guga.song(spec.title * 0.94f, FontWeight.Bold), maxLines = 1)
    }
}

/** 当前那台的名字（报头 / 胶囊上写的）：列表里打勾的那一行；列表还没出来 = 会话里的服务器名。 */
@Composable
fun currentServerTitle(switcher: ServerSwitcher = currentServerSwitcher()): String? {
    val entries by switcher.entries.collectAsState()
    entries.firstOrNull { it.isCurrent }?.let { return serverTitle(it) }
    val graph = LocalOptionalGraph.current ?: return null
    val session by graph.session.session.collectAsState()
    return session?.serverName?.takeIf { it.isNotBlank() }
}

/** 当前那台（数据层列表里打勾的那一行）。 */
@Composable
fun currentServerEntry(switcher: ServerSwitcher = currentServerSwitcher()): ServerEntry? {
    val entries by switcher.entries.collectAsState()
    return entries.firstOrNull { it.isCurrent }
}
