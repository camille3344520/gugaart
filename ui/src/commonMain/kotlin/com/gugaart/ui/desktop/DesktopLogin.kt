package com.gugaart.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.PaperField
import com.gugaart.ui.data.ErrorKind
import com.gugaart.ui.components.Segments
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.screens.login.LoginFlow
import com.gugaart.ui.screens.login.LoginModel
import com.gugaart.ui.screens.login.LoginStep
import com.gugaart.ui.screens.login.SCHEMES
import com.gugaart.ui.screens.login.discoverySubtitle
import com.gugaart.ui.screens.login.PublicUser
import com.gugaart.ui.screens.login.userGlyph
import com.gugaart.ui.screens.login.rememberLoginFlow
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.StoredServer
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 电脑登录页的版式数（设计稿 `desktop/css/d4.css` 的 `.first` `.srv` `.field`；单位 rem）。 */
object DeskLoginSpec {
    /** 左页大标题：6.4 rem、行高 1.25、字距 0.1 em。 */
    const val H1 = 6.4f
    const val H1_LINE = 8f
    /** 导语：1.7 rem、行高 1.9、最宽 52 rem。 */
    const val LEAD = 1.7f
    const val LEAD_LINE = 3.2f
    const val LEAD_MAX = 52f
    /** 左页留给她的一格。 */
    const val POSE = 17f
    /** 右页左边的留白（设计稿 = 栏距 × 2.5）。 */
    const val RIGHT_PAD = 6f
    /** 两页的内容离窗口上沿 / 下沿（报头之下）。 */
    const val TOP = 7.2f
    const val BOTTOM = 5f
    /** 服务器的一行：名字 1.8 rem、副行 / 状态 1.25 rem、上下各 1.6 rem。 */
    const val SERVER = 1.8f
    const val SMALL = 1.25f
    const val ROW_PAD = 1.6f
}

/**
 * 服务器 / 登录（电脑，设计稿 `desktop/index.html?p=servers`：列表、`add`、`login`、`pwd`）。跨页：左页是一句欢迎的话和她，右页是这一步的表单；
 * 没有玻璃导航，只有报头（= 窗口标题栏：能拖、有窗口键）。
 *
 * 流程和手机是同一段（[rememberLoginFlow]：上游 `SessionManager` 的探测 / 登录 / 已保存账号切换）；这里只有电脑的版式和键盘：
 * 进「添加」「登录」时输入框自己拿到焦点，`Enter` = 这一步的主按钮，`Esc` = 退回上一步。密码由用户自己输入。
 * 两种进法同手机：没有会话时根界面画它；已登录时从账号菜单进来（上游路由 `Servers` / `Login(switch, server, user)`），换好了回首页。
 */
@Composable
fun DesktopLogin(today: Today, chrome: Boolean = false) {
    val colors = Guga.colors
    // [chrome]：已登录时从账号菜单进来的——外壳已经画了报头和玻璃排，这里不再画报头，内容让到玻璃排下面
    val top = Guga.rem(if (chrome) DesktopSpec.CONTENT_TOP else DeskLoginSpec.TOP)
    val flow = rememberLoginFlow()
    val model = flow.model
    var forgetting by remember { mutableStateOf<StoredServer?>(null) }

    // Esc：先收「移除」的确认，再退回上一步；在列表上（已登录时进来的）= 根上的返回，回原来那一页
    val dispatcher = LocalBackDispatcher.current
    val stepBack by rememberUpdatedState(flow.back)
    val confirming = forgetting != null
    DisposableEffect(dispatcher, model.step, confirming) {
        val off = when {
            confirming -> dispatcher.push { forgetting = null; true }
            model.step != LoginStep.List -> dispatcher.push { stepBack?.invoke(); true }
            else -> null
        }
        onDispose { off?.invoke() }
    }

    // 刚才是被服务器请出来的（正用着的时候得到 401）：先排「读者证过期」那张通知，不是没头没脑地回到列表
    val nav = LocalWideNav.current
    val expired = nav.sessionExpired && !flow.signedIn && model.step == LoginStep.List

    Box(Modifier.fillMaxSize().background(colors.paper).testTag("desktop-login")) {
        if (!flow.restoring && expired) {
            // 最近用过的那台服务器和账号：重新登录就是给它重输一次密码
            val last = model.servers.orEmpty().flatMap { s -> s.accounts.map { s to it } }.maxByOrNull { it.second.lastLoginAt }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = top, bottom = Guga.rem(DeskLoginSpec.BOTTOM)), verticalArrangement = Arrangement.Center) {
                DeskNotice(
                    ErrorKind.Auth,
                    deskErrorCopy(ErrorKind.Auth, last?.first?.let { it.remark?.takeIf { r -> r.isNotBlank() } ?: it.name }),
                    listOf(
                        DeskNoticeAction("重新登录", GuguIcon.User) {
                            nav.sessionExpired = false
                            if (last != null) model.openLogin(model.targetOf(last.first), last.second.userName)
                        },
                        DeskNoticeAction("换一个账号") { nav.sessionExpired = false },
                    ),
                )
            }
        } else if (!flow.restoring) {
            Row(Modifier.fillMaxSize()) {
                // ---- 左页：欢迎的话 + 她 ----
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind { drawLine(colors.line, Offset(size.width - 0.5.dp.toPx(), 0f), Offset(size.width - 0.5.dp.toPx(), size.height), 1.dp.toPx()) }
                        .verticalScroll(rememberScrollState())
                        .padding(start = Guga.dimens.margin, end = Guga.dimens.margin, top = top, bottom = Guga.rem(DeskLoginSpec.BOTTOM))
                        .testTag("desktop-login-left"),
                    verticalArrangement = Arrangement.Center,
                ) { LoginWelcome(model, flow.signedIn) }
                // ---- 右页：这一步的表单 ----
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = Guga.rem(DeskLoginSpec.RIGHT_PAD), end = Guga.dimens.margin, top = top, bottom = Guga.rem(DeskLoginSpec.BOTTOM))
                        .testTag("desktop-login-${model.step.name.lowercase()}"),
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (model.step) {
                        LoginStep.List -> DeskServerList(model, flow) { forgetting = it }
                        LoginStep.Add -> DeskAddServer(model, flow)
                        LoginStep.Login -> DeskSignIn(model)
                    }
                }
            }
        }
        // 报头 = 窗口标题栏（没有会话时外壳不在，报头自己画在这里）
        if (!chrome) DesktopMasthead(today)
        forgetting?.let { s ->
            PaperDialog("desktop-forget", { forgetting = null }, width = 52f) {
                BasicText("移除这台服务器？", style = Guga.song(rsp(2.2f), FontWeight.Black, letterSpacing = 0.06.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(top = Guga.rem(2.8f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.8f))) {
                    DeskButton(
                        "移除",
                        {
                            model.forget(s)
                            forgetting = null
                        },
                        Modifier.testTag("desktop-forget-ok"),
                        icon = null,
                        small = true,
                        danger = true,
                    )
                    DeskLink("留着", { forgetting = null })
                }
            }
        }
    }
}

/** 左页：眉题 + 两行大标题 + 导语 + 她（G15 递钥匙；密码不对换成 G15b 歪头）和她的一句话。 */
@Composable
private fun LoginWelcome(model: LoginModel, signedIn: Boolean) {
    val colors = Guga.colors
    val prefs = LocalGugaPrefs.current
    val first = !signedIn && model.servers.isNullOrEmpty()
    val (line1, line2) = when (model.step) {
        LoginStep.List -> if (signedIn) "换一家" to "片库" else "入场" to "哪一家片库？"
        LoginStep.Add -> "告诉我" to "片库在哪儿"
        LoginStep.Login -> "办一张" to "观影卡"
    }
    DeskKick(if (signedIn) "SERVERS · 服务器与账号" else if (first) "WELCOME · 首映" else "WELCOME · 欢迎回来")
    BasicText(
        "$line1\n$line2",
        Modifier.padding(top = Guga.rem(1.6f)).testTag("desktop-login-h1"),
        style = Guga.song(rsp(DeskLoginSpec.H1), FontWeight.Black, letterSpacing = 0.1.em, lineHeight = rsp(DeskLoginSpec.H1_LINE)),
        maxLines = 2,
    )
    BasicText(
        "咕咕嘎嘎把你的 Emby 片库排成一本每天换片的电影画报。连上之后，封面、片单、主题色都是你自己的片子。",
        Modifier.padding(top = Guga.rem(2.4f)).widthIn(max = Guga.rem(DeskLoginSpec.LEAD_MAX)),
        style = Guga.song(rsp(DeskLoginSpec.LEAD), color = colors.dim, lineHeight = rsp(DeskLoginSpec.LEAD_LINE)),
    )
    if (prefs.showMascot) {
        Row(Modifier.padding(top = Guga.rem(2.8f)).testTag("desktop-login-mascot"), verticalAlignment = Alignment.Bottom) {
            MascotPoseNamed(if (model.badPassword) "拿钥匙歪头" else "递钥匙", Guga.rem(DeskLoginSpec.POSE))
            if (prefs.mascotSpeaks) {
                Column(Modifier.padding(start = Guga.rem(2f), bottom = Guga.rem(2.4f)).widthIn(max = Guga.rem(26f))) {
                    BasicText("放映员 · 咕咕", style = Guga.note(rsp(1.05f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                    BasicText(
                        when {
                            model.badPassword -> "再试一次。大小写也算数。"
                            model.step == LoginStep.Login -> "办一张观影卡，我好帮你记着看到哪。密码我不看，也不记。"
                            model.step == LoginStep.Add -> "不知道地址？装着 Emby 的那台电脑上，管理页面地址栏里的就是。"
                            first -> "还没有片库。添一台，我就开工。"
                            else -> "点一台就能进；观影卡过期的，要重输一次密码。"
                        },
                        Modifier.padding(top = Guga.rem(0.6f)),
                        style = Guga.song(rsp(1.45f), color = colors.dim, lineHeight = rsp(2.3f)).copy(fontStyle = FontStyle.Italic),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

/** 眉题（`.kick`）：1.2 rem、字距 0.24 em、专色。 */
@Composable
internal fun DeskKick(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = Guga.song(rsp(1.2f), FontWeight.SemiBold, Guga.colors.spot, letterSpacing = 0.24.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/* ------------------------------------------------------------------ 列表 */

@Composable
private fun DeskServerList(model: LoginModel, flow: LoginFlow, onForget: (StoredServer) -> Unit) {
    val colors = Guga.colors
    val graph = LocalGraph.current
    val session by graph.session.session.collectAsState()
    val servers = model.servers
    DeskKick(if (servers.isNullOrEmpty()) "SERVERS · 还没有片库" else "SERVERS · 已保存的片库")
    if (servers.isNullOrEmpty()) {
        BasicText(
            "没有保存的服务器",
            Modifier.padding(top = Guga.rem(1.6f)).widthIn(max = Guga.rem(48f)),
            style = Guga.song(rsp(1.5f), color = colors.dim, lineHeight = rsp(2.8f)),
        )
    } else {
        Spacer(Modifier.height(Guga.rem(0.6f)))
        for (s in servers) {
            val ms = model.latency[s.id]
            val bad = ms == -1L
            val names = s.accounts.joinToString(" · ") { it.userName }
            val current = session?.let { it.serverId == s.id || it.serverId == s.serverId } == true
            DeskServerRow(
                name = s.remark?.takeIf { it.isNotBlank() } ?: s.name,
                sub = when {
                    names.isEmpty() -> "未登录"
                    else -> names
                },
                status = when {
                    model.entering == s.id -> "进入中…"
                    current -> if (ms != null && ms >= 0) "已连接 · $ms ms" else "已连接"
                    ms == null -> "…"
                    bad -> "连不上"
                    else -> "可连接 · $ms ms"
                },
                current = current,
                bad = bad,
                onClick = { model.enter(s) },
                onMenu = { onForget(s) },
            )
        }
    }
    model.error?.let { BasicText(it, Modifier.padding(top = Guga.rem(1.4f)).testTag("desktop-login-error"), style = Guga.song(rsp(1.3f), color = colors.seal), maxLines = 3) }
    Row(Modifier.padding(top = Guga.rem(3.2f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.6f))) {
        DeskButton("添加服务器", model::openAdd, Modifier.testTag("desktop-login-add"), icon = GuguIcon.Plus)
        if (flow.signedIn) flow.back?.let { DeskLink("返回", it) }
        if (!servers.isNullOrEmpty()) BasicText("右键一行：从这台电脑上移除", style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1)
    }
}

/** 服务器的一行（`.srv`）：名字 + 斜体副行（保存过的账号），右端 状态 + ›。悬停名字变专色；正连着的那台名字是专色。右键 = 移除。 */
@Composable
private fun DeskServerRow(name: String, sub: String, status: String, current: Boolean, bad: Boolean, onClick: () -> Unit, onMenu: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Row(
        Modifier
            .fillMaxWidth()
            .hover(hover)
            .onRightClick { onMenu() }
            .drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .deskFocusRing(press)
            .pressable(press, label = name, onClick = onClick)
            .padding(vertical = Guga.rem(DeskLoginSpec.ROW_PAD))
            .testTag(if (current) "desktop-server-current" else "desktop-server"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                name,
                style = Guga.song(rsp(DeskLoginSpec.SERVER), FontWeight.Black, if (hover.over || current) colors.spot else if (bad) colors.dim else colors.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 「连不上」不靠颜色单独表达：字面就写了
            BasicText(sub, Modifier.padding(top = Guga.rem(0.4f)), style = Guga.note(rsp(DeskLoginSpec.SMALL), if (bad) colors.seal else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Guga.rem(2f)))
        BasicText(status, style = Guga.song(rsp(DeskLoginSpec.SMALL), color = colors.dim), maxLines = 1)
        Spacer(Modifier.width(Guga.rem(0.8f)))
        Icon(GuguIcon.ChevronRight, colors.dim, Guga.rem(1.4f))
    }
}

/* ------------------------------------------------------------------ 添加 */

@Composable
private fun DeskAddServer(model: LoginModel, flow: LoginFlow) {
    val colors = Guga.colors
    val found = model.found
    val address = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { address.requestFocus() }
    }
    val go = { model.connectThenNext() }
    // 边打边找（P2-19）：停下来就自己去找
    LaunchedEffect(model.address, model.scheme) { model.probeWhenIdle() }
    DeskKick("STEP 1 / 2 · 添加服务器")
    if (model.discovered.isNotEmpty()) {
        DeskKick("ON THIS NETWORK · 这个网络里的服务器", Modifier.padding(top = Guga.rem(1.8f)))
        Spacer(Modifier.height(Guga.rem(0.4f)))
        for (s in model.discovered) {
            val press = rememberPressState()
            val hover = rememberHover()
            Row(
                Modifier
                    .fillMaxWidth()
                    .hover(hover)
                    .drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
                    .deskFocusRing(press)
                    .pressable(press, label = s.name) { model.pickDiscovered(s) }
                    .padding(vertical = Guga.rem(1.2f))
                    .testTag("desktop-discovered-server"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    BasicText(s.name, style = Guga.song(rsp(1.65f), FontWeight.Black, if (hover.over) colors.spot else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(discoverySubtitle(s), Modifier.padding(top = Guga.rem(0.35f)), style = Guga.note(rsp(1.2f), colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(Guga.rem(1.2f)))
                Icon(GuguIcon.ChevronRight, colors.dim, Guga.rem(1.3f))
            }
        }
        Spacer(Modifier.height(Guga.rem(1f)))
    }
    PaperField(
        "服务器地址",
        model.address,
        model::editAddress,
        Modifier.padding(top = Guga.rem(2f)).focusRequester(address),
        placeholder = "服务器:端口，如 nas.local:8096",
        keyboard = KeyboardType.Uri,
        ime = ImeAction.Go,
        onIme = go,
        error = model.addError != null,
        tag = "desktop-login-address",
        trailing = { if (found != null) Icon(GuguIcon.Check, colors.spot, Guga.rem(2f)) },
    )
    BasicText(
        when {
            model.probing -> "连接中…"
            model.addError != null -> "连接失败：${model.addError}"
            found != null -> listOfNotNull("已找到：${found.name}", model.foundInfo, if (found.accounts.isEmpty()) "需要登录" else "保存过 ${found.accounts.size} 个账号").joinToString(" · ")
            else -> ""
        },
        Modifier.padding(top = Guga.rem(0.9f)).testTag(if (found != null) "desktop-login-found" else "desktop-login-add-note"),
        style = Guga.song(rsp(1.25f), color = if (model.addError != null) colors.seal else if (found != null) colors.ink else colors.dim, lineHeight = rsp(2.1f)),
        maxLines = 3,
    )
    DeskOption("协议", "自动 = 先试 https，再试 http") {
        Segments(SCHEMES, model.scheme, {
            model.scheme = it
            model.editAddress(model.address)
        })
    }
    Row(Modifier.padding(top = Guga.rem(3.2f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.6f))) {
        DeskButton(
            when {
                found != null -> "下一步 · 登录"
                model.probing -> "正在找……"
                else -> "连接"
            },
            go,
            Modifier.testTag("desktop-login-connect"),
            icon = null,
            note = "Enter",
        )
        flow.back?.let { DeskLink(if (model.servers.isNullOrEmpty()) "返回" else "返回列表", it) }
    }
}

/** 设置式的一行（`.opt`）：左 名字 + 一句小字，右 控件；下面一条线。 */
@Composable
private fun DeskOption(title: String, note: String?, control: @Composable () -> Unit) {
    val colors = Guga.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = Guga.rem(1.6f))
            .drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .heightIn(min = Guga.rem(6.4f))
            .padding(vertical = Guga.rem(1.2f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Guga.song(rsp(1.6f), FontWeight.Bold), maxLines = 1)
            if (note != null) BasicText(note, Modifier.padding(top = Guga.rem(0.4f)), style = Guga.song(rsp(1.25f), color = colors.faint), maxLines = 2)
        }
        Spacer(Modifier.width(Guga.rem(2.4f)))
        control()
    }
}

/* ------------------------------------------------------------------ 登录 */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeskSignIn(model: LoginModel) {
    val colors = Guga.colors
    val target = model.target ?: return
    val user = remember { FocusRequester() }
    val secret = remember { FocusRequester() }
    // 进这一步：用户名已经填好（保存过的账号）就直接落在密码上
    LaunchedEffect(target) {
        withFrameNanos { }
        runCatching { (if (model.username.isBlank()) user else secret).requestFocus() }
    }
    DeskKick("STEP 2 / 2 · 登录「${target.name}」")
    val pub = model.publicUsers.filter { u -> target.accounts.none { it.userId == u.id || it.userName == u.name } }
    if (target.accounts.isNotEmpty()) {
        FlowRow(Modifier.fillMaxWidth().padding(top = Guga.rem(1.8f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f)), verticalArrangement = Arrangement.spacedBy(Guga.rem(0.8f))) {
            for (a in target.accounts) {
                DeskChip(a.userName, { target.stored?.let { model.switchTo(it, a) } }, solid = a.userName == model.username, tag = "desktop-login-account")
            }
            DeskChip(
                "+ 别的账号",
                {
                    model.username = ""
                    model.password = ""
                    runCatching { user.requestFocus() }
                },
            )
        }
    }
    if (pub.isNotEmpty()) {
        DeskKick("PUBLIC USERS · 公开用户", Modifier.padding(top = Guga.rem(1.8f)))
        FlowRow(Modifier.fillMaxWidth().padding(top = Guga.rem(1f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f)), verticalArrangement = Arrangement.spacedBy(Guga.rem(0.8f))) {
            for (u in pub) {
                DeskPublicUserChip(u, selected = u.name == model.username) {
                    model.pickPublicUser(u)
                    runCatching { secret.requestFocus() }
                }
            }
        }
    }
    // 用户名框里按 Enter = 去密码框（Tab 也行）；密码框里按 Enter = 登录
    PaperField(
        "用户名", model.username, { model.username = it }, Modifier.padding(top = Guga.rem(2f)),
        enabled = !model.busy, ime = ImeAction.Next, onIme = { runCatching { secret.requestFocus() } }, tag = "desktop-login-user", fieldFocus = user,
    )
    PaperField(
        "密码",
        model.password,
        { model.password = it },
        Modifier.padding(top = Guga.rem(2f)),
        placeholder = "没有密码就留空",
        password = true,
        error = model.badPassword,
        enabled = !model.busy,
        ime = ImeAction.Done,
        onIme = model::signIn,
        tag = "desktop-login-password",
        fieldFocus = secret,
    )
    val fail = model.error != null
    BasicText(
        when {
            model.busy -> "登录中…"
            fail -> model.error.orEmpty()
            else -> "密码只发给你的服务器，本机只保存令牌"
        },
        Modifier.padding(top = Guga.rem(1.2f)).testTag(if (model.busy) "desktop-login-busy" else if (fail) "desktop-login-fail" else "desktop-login-note"),
        style = Guga.song(rsp(1.25f), color = if (fail) colors.seal else colors.dim, lineHeight = rsp(2.1f)),
        maxLines = 3,
    )
    Row(Modifier.padding(top = Guga.rem(3.2f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.6f))) {
        DeskButton(if (model.busy) "正在登录……" else "登录", model::signIn, Modifier.testTag("desktop-login-submit"), icon = null, note = "Enter")
        DeskLink("换一个服务器", model::openList)
    }
}

@Composable
private fun DeskPublicUserChip(u: PublicUser, selected: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Row(
        Modifier
            .hover(hover)
            .background(if (selected) colors.ink else colors.paper)
            .border(1.dp, if (selected || hover.over) colors.ink else colors.dim)
            .deskFocusRing(press)
            .pressable(press, label = u.name, onClick = onClick)
            .padding(start = Guga.rem(0.6f), end = Guga.rem(1.2f), top = Guga.rem(0.5f), bottom = Guga.rem(0.5f))
            .testTag("desktop-public-user"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f)),
    ) {
        Box(
            Modifier
                .size(Guga.rem(2.6f))
                .clip(CircleShape)
                .border(1.dp, if (selected) colors.onInk.copy(alpha = 0.3f) else colors.line, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            CoverImage(u.avatarUrl, Modifier.fillMaxSize().clip(CircleShape)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(userGlyph(u.name), style = Guga.song(rsp(1.2f), FontWeight.Bold, if (selected) colors.onInk else colors.dim), maxLines = 1)
                }
            }
        }
        BasicText(u.name, style = Guga.song(rsp(1.35f), if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) colors.onInk else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
