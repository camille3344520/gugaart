package com.gugaart.ui.screens.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.BoxButton
import com.gugaart.ui.components.ConfirmSheet
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.PaperField
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.Segments
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.session.SessionStatus
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.StoredServer
import com.gugugaga.core.apiBaseOf
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.delay

/** 测试缝：把这一页的 [LoginModel] 交给离屏测试（离屏渲染不能点、不能打字：测试直接调模型的动作）。应用里从不设置。 */
val LocalLoginModelSink = staticCompositionLocalOf<((LoginModel) -> Unit)?> { null }

/** 登录页的几个数（设计稿 `servers*`，手机 412 宽）。 */
private object LoginSpec {
    /** 大标题（两行）。 */
    val HEADLINE = 44.sp
    val HEADLINE_LINE = 54.sp
    /** 账号方块。 */
    val TILE = 60.dp
    /** 恢复会话时先等这么久再出登录页（很快就恢复好的不闪一下登录页）。 */
    const val RESTORE_GRACE_MS = 350L
}

/**
 * 服务器 / 登录（设计稿 `p=servers`：列表、`add`、`found`、`login`、`busy`、`fail`）。两种进法：
 * - 没有会话（根界面在 `session == null` 时画它）：第一次打开或者退出登录之后。登录成功 → 会话生效，根界面自己换成首页。
 * - 已登录时从「我的」进来（上游路由 `Servers` / `Login(switch, server, user)`）：左上有返回；换好账号 → 回首页。
 *
 * 逻辑全部是上游 `SessionManager` 的（[LoginModel] 只是这一页的状态）。密码由用户自己输入，不回显。
 */
@Composable
fun LoginScreen(today: Today) {
    val graph = LocalGraph.current
    val flow = rememberLoginFlow()
    val model = flow.model
    val signedIn = flow.signedIn
    val restoring = flow.restoring
    var forgetting by remember { mutableStateOf<StoredServer?>(null) }
    val back = flow.back

    Box(Modifier.fillMaxSize().background(Guga.colors.paper).testTag("login-screen")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Guga.dimens.margin)) {
            Spacer(Modifier.height(Guga.dimens.statusBar + 8.dp))
            Masthead(volume = "VOL. ${today.volume}", date = today.date, issue = today.volume)
            if (!restoring) {
                // 方框的线框比触控热区小一圈：往左挪 4 dp，线框的左沿才对齐页边
                if (back != null) BoxButton(GuguIcon.ChevronLeft, "返回", back, Modifier.padding(top = 4.dp).offset(x = (-4).dp).testTag("login-back"))
                else Spacer(Modifier.height(30.dp))
                when (model.step) {
                    LoginStep.List -> ServerList(model, first = !signedIn, onForget = { forgetting = it })
                    LoginStep.Add -> AddServer(model)
                    LoginStep.Login -> SignIn(model)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
        forgetting?.let { s ->
            ConfirmSheet(
                title = "移除「${s.remark?.takeIf { it.isNotBlank() } ?: s.name}」",
                text = "这台服务器和它保存的账号会从这台设备上拿掉。服务器上的东西不动。",
                confirm = "移除",
                onConfirm = {
                    model.forget(s)
                    forgetting = null
                },
                onDismiss = { forgetting = null },
                danger = true,
            )
        }
    }
}

/**
 * 登录页三端共用的那一段：这一页的 [LoginModel]、现在有没有会话、要不要先等一等（恢复会话时不闪登录页）、左上角 / 返回键退到哪一步。
 * 版式由各端自己排（手机 [LoginScreen]、电脑 `DesktopLogin`、电视 `TvLogin`）。
 */
@Stable
class LoginFlow(val model: LoginModel, val signedIn: Boolean, val restoring: Boolean, val back: (() -> Unit)?)

/**
 * 登录页的流程（不含版式）：读已保存的服务器；带着目标进来（上游路由 `Login(server, user)`）直接到那台服务器的登录这一步；
 * **已登录时进来换账号，换好了回首页**——会话换了人 / 换了服务器，或者用保存的令牌重新进了同一个账号（[LoginModel.done]），都算换好了。
 */
@Composable
fun rememberLoginFlow(): LoginFlow {
    val graph = LocalGraph.current
    val scope = graph.scope
    val model = remember(graph) { LoginModel(graph.session, scope) }
    LocalLoginModelSink.current?.invoke(model)
    val status by graph.session.status.collectAsState()
    val session by graph.session.session.collectAsState()
    val revision by graph.session.revision.collectAsState()
    val route = graph.nav.route
    val signedIn = session != null
    LaunchedEffect(revision, status) { model.load() }

    // 深链 / 从「我的」带着目标进来：Login(server = api base, user = 用户名) → 直接到那台服务器的登录这一步
    val wanted = route as? Route.Login
    LaunchedEffect(wanted, model.servers) {
        val list = model.servers ?: return@LaunchedEffect
        val w = wanted ?: return@LaunchedEffect
        val s = w.server?.let { url -> list.firstOrNull { apiBaseOf(it.address) == url } } ?: return@LaunchedEffect
        if (model.step == LoginStep.List) model.openLogin(model.targetOf(s), w.user)
    }
    // 已登录时进来换账号：会话换了（用户 / 服务器变了）→ 回首页；用保存的令牌重新进了同一个账号（会话没变）也回首页，不留在这一页上
    val enteredAs = remember { session?.let { it.serverId + "|" + it.userId } }
    LaunchedEffect(session, model.done) {
        val now = session?.let { it.serverId + "|" + it.userId }
        if (enteredAs != null && now != null && (now != enteredAs || model.done > 0)) graph.nav.reset(Route.Home)
    }

    // 正在恢复上次的会话：先只画报头，不闪登录页
    var waited by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LoginSpec.RESTORE_GRACE_MS)
        waited = true
    }
    val restoring = !signedIn && status == SessionStatus.Restoring && !waited

    val back: (() -> Unit)? = when {
        model.step == LoginStep.Login && model.found != null && model.target == model.found -> model::openAdd
        model.step != LoginStep.List -> model::openList
        signedIn -> ({ graph.nav.backOrHome() })
        else -> null
    }
    return LoginFlow(model, signedIn, restoring, back)
}

@Composable
private fun Headline(kicker: String?, line1: String, line2: String, lead: String? = null) {
    val colors = Guga.colors
    // 没有眉题时也留着这一行（空的）：版面的起点不跳
    BasicText(kicker.orEmpty(), Modifier.padding(top = 14.dp), style = Guga.kicker(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    BasicText("$line1\n$line2", Modifier.padding(top = 6.dp), style = Guga.song(LoginSpec.HEADLINE, FontWeight.Black, lineHeight = LoginSpec.HEADLINE_LINE), maxLines = 2)
    if (lead != null) BasicText(lead, Modifier.padding(top = 14.dp), style = Guga.song(Guga.type.lead, color = colors.dim, lineHeight = 25.sp))
}

/* ------------------------------------------------------------------ 列表 */

@Composable
private fun ServerList(model: LoginModel, first: Boolean, onForget: (StoredServer) -> Unit) {
    val colors = Guga.colors
    val servers = model.servers
    Column(Modifier.testTag("login-list")) {
        Headline(
            // 设计稿的列表页没有眉题；只有第一次打开（一台都没有）才打个招呼
            if (first && servers.isNullOrEmpty()) "第一次见面 · WELCOME" else null,
            "选一台",
            "服务器",
            "咕咕嘎嘎把你的片库排成一本电影画报。先告诉放映员，片子放在哪儿。",
        )
        if (!servers.isNullOrEmpty()) {
            SectionHeader("已保存", "SAVED", Modifier.padding(top = 30.dp), trailing = "${servers.size} 台")
            for ((i, s) in servers.withIndex()) {
                val ms = model.latency[s.id]
                val bad = ms == -1L
                val names = s.accounts.joinToString(" · ") { it.userName }
                ServerRow(
                    number = i + 1,
                    name = s.remark?.takeIf { it.isNotBlank() } ?: s.name,
                    right = when {
                        model.entering == s.id -> "正在进……"
                        ms == null -> "…"
                        bad -> "连不上"
                        else -> "$ms ms"
                    },
                    sub = when {
                        bad -> "连不上：地址也许换了"
                        names.isEmpty() -> "还没有登录过"
                        else -> names
                    },
                    bad = bad,
                    onClick = { model.enter(s) },
                    onLong = { onForget(s) },
                )
                Rule()
            }
        }
        WideButton("添加服务器", model::openAdd, Modifier.padding(top = 26.dp).testTag("login-add"), leading = GuguIcon.Plus)
        Rule(Modifier.padding(top = 30.dp))
        EditorNote(
            if (servers.isNullOrEmpty()) "还没有服务器。添一台，我就开工。" else "点一台就能进；失效的要重输密码。",
            Modifier.padding(vertical = 14.dp),
            live = false,
        )
        Rule()
        if (!servers.isNullOrEmpty()) {
            BasicText("长按一台，可以把它从这台设备上移除。", Modifier.padding(top = 12.dp), style = Guga.note(Guga.type.caption, colors.faint))
        }
    }
}

@Composable
private fun ServerRow(number: Int, name: String, right: String, sub: String, bad: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).pressGraphics(press).pressable(press, label = name, onLongClick = onLong, onClick = onClick).padding(vertical = 16.dp).testTag("server-row"),
        verticalAlignment = Alignment.Top,
    ) {
        BasicText(if (number < 10) "0$number" else "$number", Modifier.width(42.dp).padding(top = 3.dp), style = Guga.numeral(19.sp, colors.spot), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(name, Modifier.weight(1f), style = Guga.song(22.sp, FontWeight.Black, if (bad) colors.dim else colors.ink, letterSpacing = 0.08.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(right, Modifier.padding(start = 10.dp, end = 6.dp), style = Guga.note(13.5.sp, colors.dim), maxLines = 1)
                Icon(GuguIcon.ChevronRight, colors.dim, 12.dp)
            }
            // 「连不上」不靠颜色单独表达：字面就写了
            BasicText(sub, Modifier.padding(top = 10.dp), style = Guga.note(Guga.type.caption, if (bad) colors.seal else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ------------------------------------------------------------------ 添加 */

@Composable
private fun AddServer(model: LoginModel) {
    val colors = Guga.colors
    val found = model.found
    Column(Modifier.testTag(if (found != null) "login-found" else "login-add-step")) {
        Headline("添加服务器 · STEP 1 / 2", "片子", "放在哪儿", "填 Emby 服务器的地址。局域网里的也行，带不带端口都行。")
        PaperField(
            "地址",
            model.address,
            model::editAddress,
            Modifier.padding(top = 30.dp),
            placeholder = "例如 nas.local:8096",
            big = true,
            keyboard = KeyboardType.Uri,
            ime = ImeAction.Go,
            onIme = model::connect,
            error = model.addError != null,
            tag = "login-address",
            trailing = { if (found != null) Icon(GuguIcon.Check, colors.spot, 20.dp) },
        )
        Row(Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("协议", Modifier.weight(1f), style = Guga.song(Guga.type.row, FontWeight.Bold), maxLines = 1)
            Segments(SCHEMES, model.scheme, {
                model.scheme = it
                model.editAddress(model.address)
            })
        }
        Rule()
        when {
            found != null -> Column(Modifier.fillMaxWidth().padding(top = 20.dp).border(1.dp, colors.ink).padding(horizontal = 18.dp, vertical = 20.dp)) {
                BasicText("找到了", style = Guga.song(Guga.type.kicker, color = colors.spot, letterSpacing = 0.3.em), maxLines = 1)
                BasicText(found.name, Modifier.padding(top = 10.dp), style = Guga.song(22.sp, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(listOfNotNull(model.foundInfo, if (found.accounts.isEmpty()) "需要登录" else "保存过 ${found.accounts.size} 个账号").joinToString(" · "), Modifier.padding(top = 10.dp), style = Guga.song(Guga.type.caption, color = colors.dim), maxLines = 2)
            }
            model.addError != null -> Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(GuguIcon.TriangleAlert, colors.seal, 14.dp, Modifier.padding(top = 3.dp))
                BasicText("没连上：${model.addError}", style = Guga.note(Guga.type.caption * 1.06f, colors.seal), maxLines = 3)
            }
            else -> BasicText("不知道地址？在装着 Emby 的那台电脑上打开管理页面，浏览器地址栏里的就是。", Modifier.padding(top = 16.dp), style = Guga.note(Guga.type.caption * 1.04f, colors.faint), maxLines = 3)
        }
        WideButton(
            when {
                found != null -> "下一步：登录"
                model.probing -> "正在找……"
                else -> "连接"
            },
            { if (found != null) model.next() else model.connect() },
            Modifier.padding(top = 26.dp).testTag("login-connect"),
            trailing = GuguIcon.ChevronRight.takeIf { !model.probing },
            enabled = found != null || (model.address.isNotBlank() && !model.probing),
        )
    }
}

/* ------------------------------------------------------------------ 登录 */

@Composable
private fun SignIn(model: LoginModel) {
    val colors = Guga.colors
    val target = model.target ?: return
    val fail = model.error != null
    Column(Modifier.testTag(if (model.busy) "login-busy" else if (fail) "login-fail" else "login-form")) {
        Headline("登录 · STEP 2 / 2 · ${target.name}", "你是", "哪一位")
        if (target.accounts.isNotEmpty()) {
            Row(Modifier.padding(top = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (a in target.accounts) {
                    UserTile(userGlyph(a.userName), a.userName, selected = a.userName == model.username, dashed = false) {
                        target.stored?.let { model.switchTo(it, a) }
                    }
                }
                UserTile("+", "别的账号", selected = false, dashed = true) {
                    model.username = ""
                    model.password = ""
                }
            }
        }
        PaperField("用户名", model.username, { model.username = it }, Modifier.padding(top = 28.dp), enabled = !model.busy, tag = "login-user")
        PaperField(
            "密码",
            model.password,
            { model.password = it },
            Modifier.padding(top = 22.dp),
            placeholder = "由你自己输入",
            password = true,
            error = model.badPassword,
            enabled = !model.busy,
            ime = ImeAction.Done,
            onIme = model::signIn,
            tag = "login-password",
        )
        if (fail) {
            Row(Modifier.padding(top = 14.dp).testTag("login-error"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(GuguIcon.TriangleAlert, colors.seal, 14.dp, Modifier.padding(top = 3.dp))
                BasicText(model.error.orEmpty(), style = Guga.note(Guga.type.caption * 1.06f, colors.seal), maxLines = 3)
            }
        } else {
            BasicText("密码只发给你的服务器；登录后本机只保存令牌，放在系统安全存储里。", Modifier.padding(top = 14.dp), style = Guga.note(Guga.type.caption * 1.04f, colors.faint), maxLines = 3)
        }
        WideButton(
            if (model.busy) "正在登录……" else "登录",
            model::signIn,
            Modifier.padding(top = 24.dp).testTag("login-submit"),
            trailing = GuguIcon.ChevronRight.takeIf { !model.busy },
            enabled = !model.busy && model.username.isNotBlank(),
        )
        Rule(Modifier.padding(top = 28.dp))
        // 她的一格：在表单下面自己的一栏里，不挡输入
        if (LocalGugaPrefs.current.showMascot) {
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                // G15 递钥匙；密码不对换成 G15b（同一姿势、歪头）
                MascotPoseNamed(if (model.badPassword) "拿钥匙歪头" else "递钥匙", 72.dp)
                if (LocalGugaPrefs.current.mascotSpeaks) {
                    BasicText(
                        if (model.badPassword) "再试一次。大小写也算数。" else "密码我不看，也不记。",
                        Modifier.padding(start = 16.dp).widthIn(max = 220.dp),
                        style = Guga.song(Guga.type.caption * 1.05f, color = colors.dim).copy(fontStyle = FontStyle.Italic),
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun UserTile(glyph: String, name: String, selected: Boolean, dashed: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Column(Modifier.pressGraphics(press).pressable(press, label = name, onClick = onClick).testTag("login-user-tile"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(LoginSpec.TILE).then(
                when {
                    selected -> Modifier.background(colors.ink)
                    dashed -> Modifier
                    else -> Modifier.border(1.dp, colors.dim)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            if (dashed) {
                Canvas(Modifier.matchParentSize()) {
                    drawRect(colors.dim, Offset.Zero, size, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
                }
                Icon(GuguIcon.Plus, colors.dim, 18.dp)
            } else {
                BasicText(glyph, style = Guga.song(26.sp, FontWeight.Black, if (selected) colors.onInk else colors.dim), maxLines = 1)
            }
        }
        BasicText(name, Modifier.padding(top = 10.dp).widthIn(max = 84.dp), style = Guga.song(13.sp, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 通栏主按钮（登录页的三个大按钮）：米白实底、墨字**居中**，字前 / 字后可带一个小图标。按下 = 放大（上游按压物理）。 */
@Composable
private fun WideButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leading: GuguIcon? = null, trailing: GuguIcon? = null, enabled: Boolean = true) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        modifier.fillMaxWidth().pressGraphics(press).heightIn(min = 54.dp).background(if (enabled) colors.ink else colors.faint).pressable(press, enabled = enabled, label = text, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (leading != null) Icon(leading, colors.onInk, 14.dp)
        BasicText(text, style = Guga.song(Guga.type.lead, FontWeight.Bold, colors.onInk, letterSpacing = 0.06.em), maxLines = 1)
        if (trailing != null) Icon(trailing, colors.onInk, 14.dp)
    }
}
