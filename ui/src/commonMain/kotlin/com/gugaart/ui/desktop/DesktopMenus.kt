package com.gugaart.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.Today
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyException
import com.gugugaga.core.StoredAccount
import com.gugugaga.core.StoredServer
import com.gugugaga.core.apiBaseOf
import com.gugugaga.core.describeEmbyError
import com.gugugaga.ds.components.GlassMorph
import com.gugugaga.ds.components.PopoverAlign
import com.gugugaga.ds.glass.MaterialName
import com.gugugaga.ds.glass.SceneAnchor
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalOnGlass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 菜单 / 对话框的数（设计稿 `p=account` `p=dialogs`，rem）。 */
object DeskMenuSpec {
    /** 账号菜单：宽 28、圆角 1.8、行高 4。 */
    const val ACCOUNT_W = 28f
    const val ACCOUNT_R = 1.8f
    const val ACCOUNT_ROW = 4f

    /** 右键菜单：宽 23.2、行高 3.6；管理面板宽 27。 */
    const val CTX_W = 23.2f
    const val CTX_ROW = 3.6f
    const val MNG_W = 27f

    /** 「换一个片库」对话框的宽。 */
    const val SWITCH_W = 64f
}

/**
 * 账号菜单：从账号圆钮里「长」出来（上游 `GlassMorph`：圆钮的 `Clear` 玻璃一路变成面板的 `Pop`，内容是纸面语言的字）。设计稿 `p=account`（默认 / `switch`）。
 * 状态放在这里，[DesktopChrome] 画圆钮（挂 [anchor]）并调 [Host]。
 *
 * 切换账号 / 换服务器 / 退出登录全部是上游 `SessionManager` 的动作（做法同手机的 `screens/me/MeScreen.kt`）；添加服务器 / 登录别的账号走上游路由。
 * 「换一个片库」和「退出登录」的二次确认是**纸片**对话框，不是玻璃。
 */
object DesktopAccountMenu {
    var open: Boolean by mutableStateOf(false)

    /** 「换一个片库」对话框开着（设计稿 `account-switch`）。 */
    var switching: Boolean by mutableStateOf(false)

    /** 退出登录的二次确认开着。 */
    var leaving: Boolean by mutableStateOf(false)

    /** 账号圆钮在玻璃场景里的位置（菜单从它长出来）。 */
    val anchor = SceneAnchor()

    fun toggle() {
        open = !open
    }

    fun closeAll() {
        open = false
        switching = false
        leaving = false
    }

    @Composable
    fun Host(today: Today) {
        val g = LocalGraph.current
        val session by g.session.session.collectAsState()
        val s = session ?: return
        val onGlass = Color.White
        GlassMorph(
            open = open,
            anchor = anchor,
            onOutsideTap = { open = false },
            modifier = Modifier.testTag("desktop-account-menu"),
            align = PopoverAlign.End,
            radius = Guga.rem(DeskMenuSpec.ACCOUNT_R),
            material = MaterialName.Pop,
            minWidth = Guga.rem(DeskMenuSpec.ACCOUNT_W),
            maxWidth = Guga.rem(DeskMenuSpec.ACCOUNT_W),
        ) {
            Column(Modifier.width(Guga.rem(DeskMenuSpec.ACCOUNT_W)).padding(vertical = Guga.rem(1.2f))) {
                // 当前账号 / 服务器
                Row(Modifier.padding(start = Guga.rem(2f), end = Guga.rem(2f), top = Guga.rem(0.8f), bottom = Guga.rem(1.4f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f))) {
                    Box(Modifier.size(Guga.rem(4f)).background(onGlass.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
                        BasicText(Rules.monogram(s.userName, person = true), style = Guga.song(rsp(1.7f), FontWeight.Black, onGlass), maxLines = 1)
                    }
                    Column(Modifier.weight(1f)) {
                        BasicText(s.userName, style = Guga.song(rsp(1.6f), FontWeight.Bold, onGlass), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText("${s.serverName} · 已连接", style = Guga.song(rsp(1.15f), color = onGlass.copy(alpha = 0.66f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                AccountRow(GuguIcon.History, "观看记录") {
                    open = false
                    g.nav.navigate(Route.History())
                }
                AccountRow(GuguIcon.Heart, "我的收藏") {
                    open = false
                    g.nav.navigate(Route.History("favorites"))
                }
                AccountRow(GuguIcon.UserRound, "切换账号 / 服务器", tag = "account-switch") {
                    open = false
                    switching = true
                }
                AccountRule()
                AccountRow(GuguIcon.SlidersHorizontal, "设置", "Ctrl ,") {
                    open = false
                    g.nav.navigate(Route.Settings())
                }
                AccountRow(GuguIcon.Download, "检查更新", "v${g.deps.appVersion}") {
                    open = false
                    g.update.checkNow()
                    g.nav.navigate(Route.Settings("about"))
                }
                AccountRule()
                AccountRow(GuguIcon.X, "退出登录", tag = "account-leave") {
                    open = false
                    leaving = true
                }
            }
        }
        if (switching) SwitchDialog()
        if (leaving) {
            PaperDialog("desktop-leave", onDismiss = { leaving = false }, width = 44f) {
                BasicText("退出「${s.userName}」", style = Guga.song(rsp(2.6f), FontWeight.Black, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    "这个账号会从这台设备上移除，下次要重新输入密码。服务器上的记录和收藏都留着。",
                    Modifier.padding(top = Guga.rem(1.4f)),
                    style = Guga.song(rsp(1.5f), color = Guga.colors.dim, lineHeight = rsp(2.6f)),
                )
                Row(Modifier.fillMaxWidth().padding(top = Guga.rem(2.8f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f), Alignment.End)) {
                    DeskButton("算了", { leaving = false }, icon = null, small = true, outline = true)
                    DeskButton(
                        "退出登录",
                        {
                            leaving = false
                            // 上游：吊销令牌、忘掉这个账号、回到登录页
                            g.scope.launch { g.session.signOut() }
                        },
                        icon = null,
                        small = true,
                        danger = true,
                    )
                }
            }
        }
    }

    @Composable
    private fun AccountRow(icon: GuguIcon, text: String, hint: String? = null, tag: String = "account-row", onClick: () -> Unit) {
        val press = rememberPressState()
        val hover = rememberHover()
        val ink = Color.White
        Row(
            Modifier
                .fillMaxWidth()
                .height(Guga.rem(DeskMenuSpec.ACCOUNT_ROW))
                .hover(hover)
                .drawBehind { if (hover.over || press.focused) drawRect(ink.copy(alpha = 0.10f)) }
                .deskFocusRing(press).pressable(press, label = text, onClick = onClick)
                .padding(horizontal = Guga.rem(2f))
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f)),
        ) {
            CompositionLocalProvider(LocalOnGlass provides true) { Icon(icon, ink, Guga.rem(1.7f)) }
            BasicText(text, Modifier.weight(1f), style = Guga.song(rsp(1.4f), FontWeight.SemiBold, ink), maxLines = 1)
            if (hint != null) BasicText(hint, style = Guga.numeral(rsp(1.15f), ink.copy(alpha = 0.6f)), maxLines = 1)
        }
    }

    @Composable
    private fun AccountRule() {
        Box(Modifier.padding(horizontal = Guga.rem(2f), vertical = Guga.rem(0.6f)).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.16f)))
    }

    /** 「换一个片库」：已保存的服务器和各自的账号（上游 `SessionManager.saved()`）。地址不上版面。 */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun SwitchDialog() {
        val g = LocalGraph.current
        val colors = Guga.colors
        val session by g.session.session.collectAsState()
        val s = session ?: return
        val revision by g.session.revision.collectAsState()
        val scope = rememberCoroutineScope()
        var servers by remember { mutableStateOf<List<StoredServer>>(emptyList()) }
        LaunchedEffect(revision, s.userId) { servers = g.session.saved().servers }
        val current = servers.firstOrNull { it.id == s.serverId || it.serverId == s.serverId || apiBaseOf(it.address) == s.serverUrl }
        var busy by remember { mutableStateOf<String?>(null) }
        var problem by remember { mutableStateOf<String?>(null) }

        fun switchTo(server: StoredServer, account: StoredAccount) {
            if (busy != null) return
            busy = server.id + "|" + account.userId
            problem = null
            scope.launch {
                try {
                    g.session.switchAccount(server.id, account.userId)
                    switching = false
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (e is EmbyException && e.kind == EmbyException.Kind.Unauthorized) {
                        // 令牌失效：去登录页重新输密码（密码由用户自己输入）
                        switching = false
                        g.nav.navigate(Route.Login(switch = true, server = apiBaseOf(server.address), user = account.userName))
                    } else {
                        problem = describeEmbyError(e)
                    }
                } finally {
                    busy = null
                }
            }
        }

        PaperDialog("desktop-switch", onDismiss = { switching = false }, width = DeskMenuSpec.SWITCH_W, closable = true) {
            Row(horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.4f))) {
                BasicText("换一个片库", Modifier.alignByBaseline(), style = Guga.song(rsp(3f), FontWeight.Black, letterSpacing = 0.06.em), maxLines = 1)
                BasicText("SERVERS & ACCOUNTS", Modifier.alignByBaseline(), style = Guga.numeral(rsp(1.15f), colors.spot, letterSpacing = 0.2.em), maxLines = 1)
            }
            Spacer(Modifier.height(Guga.rem(1.6f)))
            for (server in servers) {
                val isCurrent = server.id == current?.id
                Row(Modifier.fillMaxWidth().padding(top = Guga.rem(1.2f)), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(server.remark?.takeIf { it.isNotBlank() } ?: server.name, style = Guga.song(rsp(2f), FontWeight.Black, if (isCurrent) colors.spot else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(
                            when {
                                busy?.startsWith(server.id + "|") == true -> "正在切换……"
                                server.accounts.isEmpty() -> "还没有登录过"
                                else -> "${server.accounts.size} 个账号保存在这台电脑上"
                            },
                            Modifier.padding(top = Guga.rem(0.4f)),
                            style = Guga.note(rsp(1.25f), colors.faint),
                            maxLines = 1,
                        )
                    }
                    if (isCurrent) {
                        ProofTick(true, size = Guga.rem(1.6f), box = false)
                        BasicText("已连接", Modifier.padding(start = Guga.rem(1.2f)), style = Guga.song(rsp(1.3f), FontWeight.SemiBold), maxLines = 1)
                    }
                }
                FlowRow(Modifier.fillMaxWidth().padding(top = Guga.rem(1.4f), bottom = Guga.rem(1.6f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f)), verticalArrangement = Arrangement.spacedBy(Guga.rem(0.8f))) {
                    for (a in server.accounts) {
                        val on = isCurrent && a.userId == s.userId
                        DeskChip(a.userName, { if (on) switching = false else switchTo(server, a) }, solid = on, tag = "switch-account")
                    }
                    DeskChip(
                        "+ 登录别的账号",
                        {
                            switching = false
                            g.nav.navigate(Route.Login(switch = true, server = apiBaseOf(server.address)))
                        },
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            }
            problem?.let { BasicText(it, Modifier.padding(top = Guga.rem(1.2f)), style = Guga.song(rsp(1.3f), color = colors.seal)) }
            Row(Modifier.fillMaxWidth().padding(top = Guga.rem(2.8f)), verticalAlignment = Alignment.CenterVertically) {
                DeskLink(
                    "+ 添加服务器",
                    {
                        switching = false
                        g.nav.navigate(Route.Servers)
                    },
                )
                Spacer(Modifier.weight(1f))
                DeskLink(
                    "管理服务器",
                    {
                        switching = false
                        g.nav.navigate(Route.Servers)
                    },
                )
                Spacer(Modifier.width(Guga.rem(1.4f)))
                DeskButton("完成", { switching = false }, icon = null, small = true)
            }
        }
    }
}

/**
 * 纸片对话框：页面压暗（纸色 62 %，不是模糊），正中一张 1 dp 米白边的纸。点纸外 / Esc 收起。[closable]：右上角的叉。
 */
@Composable
fun PaperDialog(tag: String, onDismiss: () -> Unit, width: Float, closable: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = Guga.colors
    Box(
        Modifier.fillMaxSize().background(colors.paper.copy(alpha = 0.62f)).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent()
                    if (e.type == PointerEventType.Press && e.changes.none { it.isConsumed }) onDismiss()
                    e.changes.forEach { it.consume() }
                }
            }
        }.testTag("$tag-scrim"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(Guga.rem(width))
                .background(slipColor())
                .border(1.dp, colors.ink)
                // 纸上的点击不算「点纸外」
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
                .testTag(tag),
        ) {
            Column(Modifier.padding(horizontal = Guga.rem(3.6f), vertical = Guga.rem(3.2f)), content = content)
            if (closable) {
                val press = rememberPressState()
                Box(Modifier.align(Alignment.TopEnd).padding(Guga.rem(1f)).size(Guga.rem(3.2f)).pressGraphics(press).deskFocusRing(press).pressable(press, label = "关闭", onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Icon(GuguIcon.X, colors.ink, Guga.rem(1.8f))
                }
            }
        }
    }
}

/**
 * 条目的右键菜单（纸片，不是玻璃；设计稿 `p=dialogs`：`ctx` 右键菜单 / `mng` 管理 / `del` 删除二次确认）。
 * 日常（播放 / 从头播放 / 收藏 / 标记已看 / 查看详情）和「管理」分开；「删除媒体…」在原地变成红边的二次确认，不弹第二个窗口。
 * 任何界面在卡片 / 行的右键处调 [open]；[Host] 由电脑的根（`GugaartFrame`）画一次。
 *
 * **管理 / 删除这一阶段只有界面**：上游没有给我们编辑元数据、删除媒体的入口，而且真实服务器只读（AGENTS.md §2.1）——
 * 管理的几行点了只说明「还没接上」，「删除文件」的执行处是空的（见 [confirmDelete]）。
 * 设计稿里的「加入待看清单」没有对应的上游功能，不画。
 */
object DesktopContextMenu {
    var target: Title? by mutableStateOf(null)
        private set
    var at: Offset by mutableStateOf(Offset.Zero)
        private set

    /** 开到哪一层：`ctx` 只有日常菜单；`mng` 管理面板推开；`del` 管理面板里的删除变成了二次确认。 */
    var stage: String by mutableStateOf("ctx")

    /** 管理面板里的一句说明（点了还没接上的那几行）。 */
    private var notice: String? by mutableStateOf(null)

    /** [windowPos]：鼠标在窗口里的位置（px）。 */
    fun open(title: Title, windowPos: Offset) {
        target = title
        at = windowPos
        stage = "ctx"
        notice = null
    }

    fun close() {
        target = null
        stage = "ctx"
        notice = null
    }

    /**
     * 「删除文件」：**执行处留空**。上游没有删除媒体的入口（`EmbyClient` 没有公开的删除方法），真实服务器只读；
     * 接上之前这里只收起菜单。接的时候：调上游的删除、成功后让当前页重新取数。
     */
    private fun confirmDelete(@Suppress("UNUSED_PARAMETER") title: Title) {
        close()
    }

    @Composable
    fun Host() {
        val t = target ?: return
        val g = LocalGraph.current
        val chrome = LocalChrome.current
        val colors = Guga.colors
        val density = LocalDensity.current
        var mainSize by remember { mutableStateOf(IntSize.Zero) }
        var subSize by remember { mutableStateOf(IntSize.Zero) }
        val favId = if (t.kind == Kind.Episode) t.seriesId ?: t.id else t.id
        val fav = chrome.isFavorite(favId, t.favorite && t.kind != Kind.Episode)
        val seen = chrome.isPlayed(t.id, t.played)
        val playable = t.kind == Kind.Movie || t.kind == Kind.Episode
        BoxWithConstraints(
            Modifier.fillMaxSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        if (e.type == PointerEventType.Press && e.changes.none { it.isConsumed }) close()
                        e.changes.forEach { it.consume() }
                    }
                }
            }.testTag("desktop-context-scrim"),
        ) {
            val edge = with(density) { 8.dp.toPx() }
            val maxW = constraints.maxWidth.toFloat()
            val maxH = constraints.maxHeight.toFloat()
            val x = at.x.coerceAtMost(maxW - mainSize.width - edge).coerceAtLeast(edge)
            val y = at.y.coerceAtMost(maxH - mainSize.height - edge).coerceAtLeast(edge)
            Slip(Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.width(Guga.rem(DeskMenuSpec.CTX_W)).onSizeChanged { mainSize = it }.testTag("desktop-context")) {
                Column(Modifier.padding(horizontal = Guga.rem(1.6f), vertical = Guga.rem(1.4f))) {
                    BasicText(t.displayName, style = Guga.song(rsp(1.5f), FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(headLine(t), Modifier.padding(top = Guga.rem(0.3f)), style = Guga.note(rsp(1.15f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SlipRule()
                Spacer(Modifier.height(Guga.rem(0.6f)))
                if (playable) {
                    CtxRow(GuguIcon.Play, if (t.progress > 0f) "续看" else "播放", "Enter") {
                        close()
                        g.play(t.id, t.positionSec?.times(1000))
                    }
                    if (t.progress > 0f) {
                        CtxRow(GuguIcon.RotateCcw, "从头播放") {
                            close()
                            g.play(t.id, 0L)
                        }
                    }
                }
                CtxRow(GuguIcon.Heart, if (fav) "取消收藏" else "收藏", "V") {
                    g.toggleFavorite(chrome, favId, fav)
                    close()
                }
                CtxRow(GuguIcon.Check, if (seen) "标成没看" else "标记已看", "W") {
                    g.togglePlayed(chrome, t.id, seen)
                    close()
                }
                CtxRow(GuguIcon.Info, "查看详情") {
                    close()
                    g.open(if (t.kind == Kind.Episode) t.seriesId ?: t.id else t.id)
                }
                Spacer(Modifier.height(Guga.rem(0.6f)))
                SlipRule()
                CtxRow(GuguIcon.SlidersHorizontal, "管理", trailingIcon = GuguIcon.ChevronRight, selected = stage != "ctx", tag = "ctx-manage", height = 4.6f) { stage = if (stage == "ctx") "mng" else "ctx" }
            }
            if (stage != "ctx") {
                val gap = with(density) { 2.dp.toPx() }
                val lift = with(density) { Guga.rem(2f).toPx() }
                val right = x + mainSize.width + gap
                val sx = if (right + subSize.width + edge <= maxW) right else (x - subSize.width - gap).coerceAtLeast(edge)
                val sy = (y + lift).coerceAtMost(maxH - subSize.height - edge).coerceAtLeast(edge)
                Slip(Modifier.offset { IntOffset(sx.toInt(), sy.toInt()) }.width(Guga.rem(DeskMenuSpec.MNG_W)).onSizeChanged { subSize = it }.testTag("desktop-context-manage")) {
                    BasicText(
                        notice ?: "管理 · 会改动服务器上的条目",
                        Modifier.padding(horizontal = Guga.rem(1.6f), vertical = Guga.rem(1.3f)),
                        style = Guga.note(rsp(1.15f), if (notice != null) colors.ink else colors.faint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SlipRule()
                    Spacer(Modifier.height(Guga.rem(0.6f)))
                    // 这几行上游还没有入口：点了只在上面那一行说明，不做任何事
                    val pending: () -> Unit = { notice = "这个功能还没接上，条目没有改动" }
                    CtxRow(GuguIcon.RotateCw, "刷新元数据", onClick = pending)
                    CtxRow(GuguIcon.Info, "编辑信息", onClick = pending)
                    CtxRow(GuguIcon.Layers, "识别 / 重新匹配", onClick = pending)
                    CtxRow(GuguIcon.Download, "下载字幕", onClick = pending)
                    Spacer(Modifier.height(Guga.rem(0.6f)))
                    SlipRule()
                    if (stage == "del") {
                        // 原地变成红边的二次确认，不弹第二个窗口
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .drawBehind { drawRect(colors.seal, size = Size(3.dp.toPx(), size.height)) }
                                .padding(start = Guga.rem(1.8f), end = Guga.rem(1.6f), top = Guga.rem(1.6f), bottom = Guga.rem(1.6f))
                                .testTag("ctx-delete-confirm"),
                        ) {
                            BasicText("确定删除「${t.displayName}」？", style = Guga.song(rsp(1.5f), FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            BasicText("会从服务器删除媒体文件，无法撤销。", Modifier.padding(top = Guga.rem(0.6f)), style = Guga.song(rsp(1.25f), color = colors.dim), maxLines = 2)
                            Row(Modifier.fillMaxWidth().padding(top = Guga.rem(1.4f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f), Alignment.End)) {
                                DeskButton("取消", { stage = "mng" }, icon = null, small = true, outline = true)
                                DeskButton("删除文件", { confirmDelete(t) }, icon = null, small = true, danger = true)
                            }
                        }
                    } else {
                        CtxRow(GuguIcon.Trash, "删除媒体…", danger = true, tag = "ctx-delete", height = 4.6f) { stage = "del" }
                    }
                }
            }
        }
    }

    private fun headLine(t: Title): String = listOfNotNull(
        when (t.kind) {
            Kind.Movie -> "电影"
            Kind.Series -> "剧集"
            Kind.Episode -> t.episodeNo?.let { "第 $it 集" } ?: "单集"
            Kind.BoxSet -> "合集"
            Kind.Person -> "人物"
            else -> null
        },
        t.year?.toString(),
    ).joinToString(" · ")

    /** 一张纸片：1 dp 米白边、纸色略提亮的底。纸上的点击不传到背后。 */
    @Composable
    private fun Slip(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier
                .background(slipColor())
                .border(1.dp, Guga.colors.ink.copy(alpha = 0.7f))
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
            content = content,
        )
    }

    @Composable
    private fun SlipRule() {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Guga.colors.line))
    }

    @Composable
    private fun CtxRow(icon: GuguIcon, text: String, hint: String? = null, trailingIcon: GuguIcon? = null, selected: Boolean = false, danger: Boolean = false, tag: String = "ctx-row", height: Float = DeskMenuSpec.CTX_ROW, onClick: () -> Unit) {
        val colors = Guga.colors
        val press = rememberPressState()
        val hover = rememberHover()
        val ink = if (selected) colors.onInk else if (danger) colors.seal else colors.ink
        Row(
            Modifier
                .fillMaxWidth()
                .height(Guga.rem(height))
                .hover(hover)
                .drawBehind { if (selected) drawRect(colors.ink) else if (hover.over || press.focused) drawRect(colors.ink.copy(alpha = 0.08f)) }
                .deskFocusRing(press).pressable(press, label = text, onClick = onClick)
                .padding(horizontal = Guga.rem(1.6f))
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f)),
        ) {
            Icon(icon, ink.copy(alpha = if (selected || danger) 1f else 0.8f), Guga.rem(1.4f), filled = icon == GuguIcon.Play)
            BasicText(text, Modifier.weight(1f), style = Guga.song(rsp(1.4f), FontWeight.SemiBold, ink), maxLines = 1)
            if (hint != null) BasicText(hint, style = Guga.numeral(rsp(1.1f), colors.faint), maxLines = 1)
            if (trailingIcon != null) Icon(trailingIcon, ink, Guga.rem(1.4f))
        }
    }
}
