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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPose
import com.gugaart.ui.screens.login.LoginModel
import com.gugaart.ui.screens.login.LoginStep
import com.gugaart.ui.screens.login.rememberLoginFlow
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.TvPageHeader
import com.gugaart.ui.tv.TvSpec
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvGroupsRoot
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.StoredServer
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon

/** 测试缝：把这一页的 [LoginModel] 交给测试（断言键盘打出来的字进了上游的登录状态）。应用里是 null。 */
val LocalTvLoginSink = staticCompositionLocalOf<((LoginModel) -> Unit)?> { null }

/** 服务器列表页的版式数（设计稿 `tv/shots/servers.png`）。 */
object TvLoginSpec {
    /** 左：服务器列表，x 136–1036。 */
    val LIST_W = 900.dp
    /** 右：这台服务器的账号与操作，x 1110–1824。 */
    val SIDE_GAP = 74.dp
    val SIDE_ROW = 76.dp
    /** 焦点行上的记号：深朱红。 */
    val MARK_ON_INK = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK
}

/**
 * 服务器列表页的方向键路径：两个组——左边的服务器 `servers`、右边这台服务器的账号与操作 `srv-ops`。
 * 返回去哪个组；[TvGroups.STAY] = 到头了不动；null = 不管（最左再按左交给外壳）。纯函数，有单测。
 */
fun tvServersEdge(from: String, dir: TvDir, has: (String) -> Boolean): String? = when (from) {
    "servers" -> when (dir) {
        TvDir.Right -> if (has("srv-ops")) "srv-ops" else TvGroups.STAY
        TvDir.Left -> null
        else -> TvGroups.STAY
    }
    "srv-ops" -> if (dir == TvDir.Left) "servers" else TvGroups.STAY
    else -> TvGroups.STAY
}

/**
 * 服务器 / 登录（电视，设计稿 `p=servers`）。流程和手机是同一段（[rememberLoginFlow]：上游 `SessionManager` 的探测 / 登录 / 已保存账号，
 * **已登录时换好账号回首页**——电视从「设置 › 切换账号」进来靠它回去）；这里只有电视的版式。密码由用户自己输入。
 * - 列表：左边上下换服务器，确定 = 连接（已保存的账号）/ 进登录；最后一行「添加服务器」。**右边是焦点那台服务器的账号与操作**：
 *   按右进去，上下走——每个保存过的账号一行（确定 = 用它进）、「登录其他账号」、「从这台电视上移除」（再按一次确定才移除）；按左回列表。
 * - 添加 / 登录（用户 2026-11-15 改成**一屏表单**，[TvLoginForm]）：地址、用户名、密码在一屏，按确定弹系统输入法，地址认 URL；
 *   右边是吉祥物和用法，「遥控器键盘」换成原来的全键盘（四个方向走键，确定 = 输入；最下一行 大写 / 空格 / 删除 / 显示密码 / 下一项）。
 * - 返回 = 先收输入法、再退出编辑、再回列表。二维码 / 手机输入不做（decisions Q8）。
 */
@Composable
fun TvLogin(today: Today) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val back = LocalBackDispatcher.current
    val model = rememberLoginFlow().model
    val groups = remember { TvGroups() }
    LocalTvLoginSink.current?.invoke(model)
    val session by g.session.session.collectAsState()
    val form = remember(model.step) { TvFormState().also { it.active = tvFormDefault(model.step, model.username) } }
    val servers = model.servers
    // 右边排的是哪一台（焦点在左边哪一行就是哪一台；焦点进了右边，它不变）
    var side by remember { mutableStateOf<String?>(null) }
    var forgetting by remember { mutableStateOf<String?>(null) }
    // 一台都没有：直接进「添加」
    LaunchedEffect(servers) { if (servers != null && servers.isEmpty() && model.step == LoginStep.List) model.openAdd() }
    // 列表要等已保存的服务器读出来再放焦点：读出来之前这一组里只有「添加服务器」，先把焦点放上去的话它就留在那儿了——
    // 用户按确定进的是「添加」，不是他那台服务器（`Tv2LoginTest.savedServerConnects`）。
    val listReady = servers != null
    LaunchedEffect(model.step, listReady, servers?.size) {
        if (model.step != LoginStep.List) groups.enterWhenReady("form", g.focus) else if (listReady) groups.enterWhenReady("servers", g.focus)
    }
    DisposableEffect(model.step, servers?.size) {
        val un = if (model.step != LoginStep.List && !servers.isNullOrEmpty()) back.push { model.openList(); true } else null
        onDispose { un?.invoke() }
    }
    val cur = groups.current
    LaunchedEffect(cur) {
        if (cur != null && cur.startsWith("server-")) side = cur.removePrefix("server-").takeIf { it != "add" }
        if (cur != "srv-forget") forgetting = null
    }

    /** 遥控器全键盘按下一个键：字进 [TvFormState.active] 那一格；「下一项」= 下一格，最后一格 = 登录。 */
    fun press(k: String) {
        val fields = tvFormFields(model.step)
        when (k) {
            "shift" -> form.upper = !form.upper
            "show" -> form.show = !form.show
            "next" -> {
                val to = fields.getOrNull(fields.indexOf(form.active) + 1)
                if (to != null) form.active = to else model.submitForm(com.gugaart.ui.screens.login.addressPlan(model.address)?.urls.orEmpty())
            }
            else -> when (form.active) {
                TvFormKeys.ADDR -> model.editAddress(TvKeyboard.type(model.address, k, form.upper))
                TvFormKeys.USER -> model.username = TvKeyboard.type(model.username, k, form.upper)
                else -> model.password = TvKeyboard.type(model.password, k, form.upper)
            }
        }
    }
    groups.edge = { from, dir ->
        when {
            model.step == LoginStep.List -> tvServersEdge(from, dir, groups::has)
            TvKeyboard.moveVertical(groups, g.focus, from, dir) -> TvGroups.STAY
            // 键盘最左再按左：回到正在接字的那一格
            from.startsWith("kb-") -> {
                if (dir == TvDir.Left) g.focus.request(form.active)
                TvGroups.STAY
            }
            else -> tvFormEdge(from, dir, groups::has, form.kbd)
        }
    }

    TvGroupsRoot(groups, Modifier.testTag("tv-login-${model.step.name.lowercase()}")) {
        Column(Modifier.fillMaxSize()) {
            TvPageHeader(
                if (model.step == LoginStep.List) "服务器" else if (model.step == LoginStep.Add) "添加服务器" else "登录",
                if (model.step == LoginStep.List) "SERVERS" else "SIGN IN",
                today,
                trailing = servers?.takeIf { model.step == LoginStep.List && it.isNotEmpty() }?.let { "已保存 ${it.size} 台" },
            )
            if (model.step == LoginStep.List && !listReady) {
                // 还没读出来：先不排（读本地存储，一两帧的事）；「添加服务器」不抢默认焦点
                groups.remove("servers")
                groups.remove("srv-ops")
            } else if (model.step == LoginStep.List) {
                val list = servers.orEmpty()
                TvGroup("servers", list.map { "server-${it.id}" } + "server-add", horizontal = false)
                Row(Modifier.fillMaxSize().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 30.dp)) {
                    Column(Modifier.width(TvLoginSpec.LIST_W)) {
                        // 刚才是被服务器请出来的（正用着的时候得到 401）：先说一句，不是没头没脑地回到列表
                        if (com.gugaart.ui.wide.LocalWideNav.current.sessionExpired && session == null) {
                            Row(Modifier.padding(bottom = 14.dp).testTag("tv-login-expired"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                if (LocalGugaPrefs.current.showMascot) MascotPose("G10", 96.dp, label = "观影卡过期")
                                BasicText("观影卡过期了：服务器不再认它（可能在别处改了密码）。选一台重新登录一次，收藏和进度都还在服务器上。", style = Guga.song(26.sp, FontWeight.SemiBold, colors.seal, lineHeight = 40.sp), maxLines = 2)
                            }
                        }
                        for ((i, s) in list.withIndex()) key(s.id) {
                            val ms = model.latency[s.id]
                            val current = session?.let { it.serverId == s.id || it.serverId == s.serverId } == true
                            val note = listOfNotNull(
                                when {
                                    model.entering == s.id -> "连接中…"
                                    current -> "已连接"
                                    ms == null -> "已保存"
                                    ms < 0 -> "连不上"
                                    else -> "$ms ms"
                                },
                                s.accounts.joinToString("、") { it.userName }.takeIf { it.isNotEmpty() } ?: "没有账号",
                            ).joinToString(" · ")
                            // 焦点进了右边那一栏：左边这一行留一道朱红，看得出右边说的是哪一台
                            val marked = groups.currentGroup == "srv-ops" && groups.current?.let { groups.groupOf(it) } == "srv-ops" && (side ?: list.first().id) == s.id
                            TvServerRow("server-${s.id}", s.remark?.takeIf { it.isNotBlank() } ?: s.name, note, number = if (i < 9) "0${i + 1}" else "${i + 1}", default = i == 0, marked = marked) { model.enter(s) }
                        }
                        TvServerRow("server-add", "添加服务器", "地址、账号、密码一屏填完", icon = GuguIcon.Plus, default = list.isEmpty(), small = true) { model.openAdd() }
                        model.error?.let { BasicText(it, Modifier.padding(top = 24.dp), style = Guga.song(26.sp, color = colors.seal), maxLines = 2) }
                    }
                    Spacer(Modifier.width(TvLoginSpec.SIDE_GAP))
                    // ---- 右：焦点那台服务器的账号与操作 ----
                    val s = list.firstOrNull { it.id == side } ?: list.firstOrNull()
                    if (s != null) {
                        TvServerSide(
                            s, model,
                            current = session?.let { it.serverId == s.id || it.serverId == s.serverId } == true,
                            currentUser = session?.userId,
                            forgetting = forgetting == s.id,
                            onForget = {
                                if (forgetting == s.id) {
                                    forgetting = null
                                    side = null
                                    model.forget(s)
                                    groups.forget("servers")
                                    groups.enter("servers", g.focus)
                                } else forgetting = s.id
                            },
                        )
                    } else groups.remove("srv-ops")
                }
            } else {
                groups.remove("servers")
                groups.remove("srv-ops")
                Row(Modifier.fillMaxSize().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 30.dp)) {
                    // ---- 左：一屏表单 ----
                    TvLoginForm(model, form)
                    Spacer(Modifier.width(if (form.kbd) 94.dp else 110.dp))
                    // ---- 右：吉祥物和用法；或者遥控器全键盘 ----
                    if (form.kbd) {
                        TvKeyboardPad(
                            form.upper,
                            bottomLabel = { k ->
                                when (k) {
                                    "shift" -> if (form.upper) "小写" else "大写"
                                    "show" -> if (form.show) "隐藏密码" else "显示密码"
                                    "next" -> if (form.active == TvFormKeys.PASS) "登录" else "下一项"
                                    else -> TvKeyboard.BOTTOM_LABEL[k].orEmpty()
                                }
                            },
                            onKey = ::press,
                            hint = "方向键走、确定输入；最左再按左回到输入框。",
                        )
                    } else {
                        for (r in 0..TvKeyboard.ROWS.size) groups.remove("kb-$r")
                        TvLoginHelp(model)
                    }
                }
            }
        }
    }
}

/** 列表的一行：斜体编号（或图标）+ 大号名字 + 右端状态。 */
@Composable
private fun TvServerRow(key: String, name: String, note: String, number: String? = null, icon: GuguIcon? = null, default: Boolean, small: Boolean = false, marked: Boolean = false, onClick: () -> Unit) {
    val colors = Guga.colors
    TvBlockFocus(
        key, onClick,
        Modifier.fillMaxWidth().drawBehind {
            drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            if (marked) drawRect(colors.seal, Offset(0f, size.height * 0.25f), androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height * 0.5f))
        },
        label = name, default = default,
    ) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(Modifier.fillMaxWidth().height(136.dp).padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(100.dp)) {
                if (number != null) BasicText(number, style = Guga.numeral(38.sp, if (focused) TvLoginSpec.MARK_ON_INK else colors.seal), maxLines = 1)
                else if (icon != null) Icon(icon, ink, 36.dp)
            }
            BasicText(name, Modifier.weight(1f), style = Guga.song(if (small) 36.sp else 54.sp, FontWeight.Black, ink, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(note, Modifier.padding(start = 20.dp), style = Guga.song(25.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.dim), maxLines = 1)
        }
    }
}

/**
 * 列表右边的一栏（设计稿 `servers.png` 的右页）：这台服务器的状态 + **这台服务器上的账号**（每个一行，确定 = 用它进）+
 * 「登录其他账号」+「从这台电视上移除」。地址不显示（截图里不该有地址）。设计稿的「内容：5 个媒体库 · 232 部」要先登进去才知道，不排。
 */
@Composable
private fun TvServerSide(s: StoredServer, model: LoginModel, current: Boolean, currentUser: String?, forgetting: Boolean, onForget: () -> Unit) {
    val colors = Guga.colors
    val ms = model.latency[s.id]
    val keys = s.accounts.map { "acct-${it.userId}" } + "srv-login" + "srv-forget"
    TvGroup("srv-ops", keys, horizontal = false)
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).testTag("tv-server-side")) {
        TvSideHead(s.remark?.takeIf { it.isNotBlank() } ?: s.name, if (current) "CONNECTED" else if (ms != null && ms < 0) "OFFLINE" else "SAVED")
        TvSideFact(
            "状态",
            when {
                model.entering == s.id -> "连接中…"
                current -> "已连接"
                ms == null -> "正在量延迟…"
                ms < 0 -> "连不上：地址也许换了"
                else -> "可连接 · $ms ms"
            },
        )
        TvSideFact("地址", "已保存（这里不显示）")
        TvSideHead("这台服务器上的账号", "ACCOUNTS", Modifier.padding(top = 34.dp))
        for (a in s.accounts) key(a.userId) {
            val on = current && a.userId == currentUser
            TvSideRow("acct-${a.userId}", a.userName, if (on) "当前" else "用它进", mark = if (on) 2 else 1) { model.switchTo(s, a) }
        }
        TvSideRow("srv-login", "登录其他账号", null, icon = GuguIcon.Plus, chevron = true) { model.openLogin(model.targetOf(s), "") }
        TvSideRow("srv-forget", if (forgetting) "再按一次确定：移除" else "从这台电视上移除", if (forgetting) "账号也一起拿掉" else null, icon = GuguIcon.X, danger = forgetting, onClick = onForget)
    }
}

@Composable
private fun TvSideHead(title: String, english: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Row(modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BasicText("¶", style = Guga.numeral(34.sp, colors.seal), maxLines = 1)
        BasicText(title, Modifier.widthIn(max = 440.dp), style = Guga.song(34.sp, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(english, style = Guga.numeral(20.sp, colors.seal, letterSpacing = 0.24.em), maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
    }
}

@Composable
private fun TvSideFact(label: String, value: String) {
    val colors = Guga.colors
    Row(Modifier.fillMaxWidth().height(58.dp).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.width(140.dp), style = Guga.song(24.sp, color = colors.dim), maxLines = 1)
        BasicText(value, style = Guga.song(26.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 右边的一行。[mark]：1 = 空心小方块（保存过的账号）、2 = 实心朱红小方块（现在用着的）、0 = 用 [icon]。 */
@Composable
private fun TvSideRow(key: String, name: String, note: String?, mark: Int = 0, icon: GuguIcon? = null, chevron: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    val colors = Guga.colors
    TvBlockFocus(key, onClick, Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, label = name, kind = TvFocusKind.Row) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(Modifier.fillMaxWidth().height(TvLoginSpec.SIDE_ROW).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(48.dp)) {
                when {
                    mark == 2 -> Box(Modifier.size(18.dp).background(if (focused) TvLoginSpec.MARK_ON_INK else colors.seal))
                    mark == 1 -> Box(Modifier.size(18.dp).border(2.dp, ink))
                    icon != null -> Icon(icon, if (danger && !focused) colors.seal else ink, 26.dp)
                }
            }
            BasicText(name, Modifier.weight(1f), style = Guga.song(30.sp, FontWeight.Bold, if (danger && !focused) colors.seal else ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) BasicText(note, style = Guga.song(24.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.dim), maxLines = 1)
            if (chevron) Icon(GuguIcon.ChevronRight, ink.copy(alpha = 0.7f), 26.dp)
        }
    }
}
