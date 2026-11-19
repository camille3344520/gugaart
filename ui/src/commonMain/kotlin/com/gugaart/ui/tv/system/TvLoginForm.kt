package com.gugaart.ui.tv.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPose
import com.gugaart.ui.screens.login.LoginModel
import com.gugaart.ui.screens.login.LoginStep
import com.gugaart.ui.screens.login.addressPlan
import com.gugaart.ui.screens.login.addressPreview
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.components.typingTarget
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.detail.LocalTvGroups
import com.gugaart.ui.tv.detail.TvActionButton
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvChip
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvSecHead
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon

/*
 * 电视的登录表单（用户 2026-11-15：「要有能让输入法弹出来的，最好是一个界面可以输入域名端口、账号密码的，域名端口支持url识别填入」）。
 *
 * 一屏填完：左边 服务器地址（已保存的服务器只写名字，不显示地址）→ 用户名 → 密码，下面「登录」「显示密码」「遥控器键盘」；
 * 右边 吉祥物递钥匙（G15，密码不对换 G15b）和三句用法；点了「遥控器键盘」右边换成原来的全键盘（给没有输入法、或输入法难用的电视）。
 *
 * 输入法：三个框平时是普通的焦点块（米白实底 + 朱红校对角），按确定 = 进入编辑：里面换成真的文本框、拿到输入焦点、弹出系统输入法。
 * 地址框是 URL 键盘，密码框打码。输入法的「下一项」跳到下一个框，密码框上「完成」= 登录。返回键：先收输入法（系统自己处理），
 * 再退出编辑（[TvFormState.editing] → null），再回服务器列表。编辑时 上 / 下 = 退出编辑并去上 / 下一格，左 / 右 = 在字里移光标。
 */

/** 表单里的焦点键。 */
object TvFormKeys {
    const val ADDR = "f-addr"
    const val USER = "f-user"
    const val PASS = "f-pass"
    const val GO = "f-go"
    const val SHOW = "f-show"
    const val KBD = "f-kbd"
}

/** 表单这一屏的状态（外面的 [TvLogin] 要用它排方向键路径，所以放在外面 remember）。 */
@Stable
class TvFormState {
    /** 正在编辑哪个框（输入法打字）；null = 没在编辑。 */
    var editing: String? by mutableStateOf(null)
    /** 右边开着遥控器全键盘。 */
    var kbd: Boolean by mutableStateOf(false)
    var upper: Boolean by mutableStateOf(false)
    /** 密码明文显示。 */
    var show: Boolean by mutableStateOf(false)
    /** 遥控器键盘打的字进哪个框（最后停过的那个框）。 */
    var active: String by mutableStateOf(TvFormKeys.ADDR)
}

/** 测试缝：编辑状态和「弹输入法」的次数（离屏测试里没有真的输入法）。应用里只写不读。 */
object TvLoginProbe {
    var editing: String? = null
    var imeShows: Int = 0
}

/**
 * 表单的方向键路径（组 `form` 竖排三个框、`form-act` 横排三个按钮、`accounts` 已保存账号；键盘的组由 [TvKeyboard.moveVertical] 和外面管）。
 * 返回去哪个组；[TvGroups.STAY] = 不动。纯函数，有单测。
 */
fun tvFormEdge(from: String, dir: TvDir, has: (String) -> Boolean, kbd: Boolean): String = when (from) {
    "form" -> when (dir) {
        TvDir.Down -> "form-act"
        TvDir.Right -> if (kbd) "kb-1" else TvGroups.STAY
        else -> TvGroups.STAY
    }
    "form-act" -> when (dir) {
        TvDir.Up -> "form"
        TvDir.Down -> if (has("accounts")) "accounts" else TvGroups.STAY
        TvDir.Right -> if (kbd) "kb-1" else TvGroups.STAY
        else -> TvGroups.STAY
    }
    "accounts" -> when (dir) {
        TvDir.Up -> "form-act"
        TvDir.Right -> if (kbd) "kb-1" else TvGroups.STAY
        else -> TvGroups.STAY
    }
    else -> TvGroups.STAY
}

/** 这一屏有哪几个框（已保存的服务器没有地址框）。 */
fun tvFormFields(step: LoginStep): List<String> = listOfNotNull(TvFormKeys.ADDR.takeIf { step == LoginStep.Add }, TvFormKeys.USER, TvFormKeys.PASS)

/** 进表单时焦点落在哪：新服务器 = 地址框；已保存的服务器 = 用户名（已经填好了就是密码）。纯函数，有单测。 */
fun tvFormDefault(step: LoginStep, username: String): String = when {
    step == LoginStep.Add -> TvFormKeys.ADDR
    username.isBlank() -> TvFormKeys.USER
    else -> TvFormKeys.PASS
}

object TvFormSpec {
    /** 左栏宽：键盘收着 860；键盘开着让给键盘（961），620。 */
    val LEFT_W = 860.dp
    val LEFT_W_KBD = 620.dp
    val FIELD_H = 132.dp
}

@Composable
fun TvLoginForm(model: LoginModel, form: TvFormState) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val back = LocalBackDispatcher.current
    val keyboard = LocalSoftwareKeyboardController.current
    val adding = model.step == LoginStep.Add
    val fields = tvFormFields(model.step)
    val plan = remember(model.address) { addressPlan(model.address) }
    SideEffect { TvLoginProbe.editing = form.editing }

    // 地址里带了用户名（reader@host）：用户名还空着就填上
    LaunchedEffect(plan?.username) {
        val u = plan?.username
        if (u != null && model.username.isBlank()) model.username = u
    }

    fun submit() {
        form.editing = null
        keyboard?.hide()
        model.submitForm(plan?.urls.orEmpty())
    }

    /** 退出编辑；[focusBack] = 焦点回到这个框（返回键）；不回 = 调用方自己放焦点。 */
    fun stopEditing(focusBack: Boolean) {
        val k = form.editing ?: return
        form.editing = null
        keyboard?.hide()
        // 地址里的 user:pass@ 不留在框里
        if (k == TvFormKeys.ADDR && plan?.strippedCredentials == true) model.editAddress(plan.clean)
        if (focusBack) g.focus.request(k)
    }

    /** 编辑时按上 / 下：退出编辑，去上 / 下一格（最上不动；最下去按钮行）。 */
    fun leave(from: String, dir: TvDir) {
        stopEditing(focusBack = false)
        val i = fields.indexOf(from)
        val to = when (dir) {
            TvDir.Up -> fields.getOrNull(i - 1) ?: from
            TvDir.Down -> fields.getOrNull(i + 1) ?: groups.entry("form-act") ?: from
            else -> from
        }
        g.focus.request(to)
    }

    /** 输入法的「下一项」/「完成」。 */
    fun imeNext(from: String) {
        val to = fields.getOrNull(fields.indexOf(from) + 1)
        if (to == null) {
            stopEditing(focusBack = true)
            submit()
        } else {
            if (from == TvFormKeys.ADDR && plan?.strippedCredentials == true) model.editAddress(plan.clean)
            form.active = to
            form.editing = to
        }
    }

    // 返回 = 先退出编辑（输入法开着时系统先把它收起来，那一下到不了这里）
    val editingNow = form.editing != null
    DisposableEffect(editingNow) {
        val un = if (editingNow) back.push {
            stopEditing(focusBack = true)
            true
        } else null
        onDispose { un?.invoke() }
    }

    TvGroup("form", fields, horizontal = false, default = tvFormDefault(model.step, model.username))
    // 能滚：壳是 adjustResize，输入法弹出来窗口变矮，正在编辑的框（文本框拿到焦点会自己 bringIntoView）滚进还看得见的那一截
    Column(Modifier.width(if (form.kbd) TvFormSpec.LEFT_W_KBD else TvFormSpec.LEFT_W).verticalScroll(rememberScrollState()).testTag("tv-login-form")) {
        if (adding) {
            TvInputField(
                TvFormKeys.ADDR, "服务器地址", model.address, { model.editAddress(it) }, form,
                placeholder = "域名或 IP，可带端口",
                keyboardType = KeyboardType.Uri, ime = ImeAction.Next,
                error = model.addError != null || plan?.error != null,
                onIme = { imeNext(TvFormKeys.ADDR) }, onLeave = { leave(TvFormKeys.ADDR, it) },
            )
            // 框下面一行：认出来的地址 / 探测结果 / 出错原因
            val (line, tone) = when {
                model.probing -> "正在找这台服务器…" to 1
                model.addError != null -> model.addError.orEmpty() to 3
                model.found != null -> "找到了：${model.found?.name}　${model.foundInfo.orEmpty()}" to 2
                plan == null -> "可以直接粘贴完整地址，例如 https://emby.example.com:8920，会自动拆出协议、主机和端口" to 0
                plan.error != null -> addressPreview(plan) to 3
                else -> addressPreview(plan) to 1
            }
            BasicText(
                line,
                Modifier.padding(start = 24.dp, top = 4.dp, bottom = 16.dp).heightIn(min = 36.dp).testTag("tv-login-plan"),
                style = Guga.song(23.sp, if (tone >= 2) FontWeight.SemiBold else FontWeight.Normal, when (tone) { 3 -> colors.seal; 2 -> colors.ink; 1 -> colors.dim; else -> colors.faint }, lineHeight = 34.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            // 已保存的服务器：只写名字，地址不显示（截图里不该有地址）；换一台 = 返回
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp).testTag("tv-login-server")) {
                BasicText("服务器", style = Guga.song(22.sp, color = colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(model.target?.name.orEmpty(), Modifier.weight(1f, fill = false), style = Guga.song(44.sp, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText("已保存 · 换一台按返回", Modifier.padding(start = 20.dp), style = Guga.song(22.sp, color = colors.faint), maxLines = 1)
                }
            }
        }
        TvInputField(
            TvFormKeys.USER, "用户名", model.username, { model.username = it }, form,
            placeholder = "",
            keyboardType = KeyboardType.Text, ime = ImeAction.Next,
            onIme = { imeNext(TvFormKeys.USER) }, onLeave = { leave(TvFormKeys.USER, it) },
        )
        Spacer(Modifier.height(12.dp))
        TvInputField(
            TvFormKeys.PASS, "密码", model.password, { model.password = it }, form,
            placeholder = "没有密码就空着",
            keyboardType = KeyboardType.Password, ime = ImeAction.Done, mask = !form.show,
            error = model.badPassword,
            onIme = { imeNext(TvFormKeys.PASS) }, onLeave = { leave(TvFormKeys.PASS, it) },
        )
        // 登录中 / 没登进去
        val status = when {
            model.busy -> "登录中…"
            model.error != null -> model.error
            else -> null
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 24.dp, top = 12.dp)) {
            if (status != null) {
                BasicText(
                    status,
                    Modifier.testTag(if (model.error != null) "tv-login-fail" else "tv-login-busy"),
                    style = Guga.song(25.sp, FontWeight.SemiBold, if (model.error != null) colors.seal else colors.dim, lineHeight = 38.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TvGroup("form-act", listOf(TvFormKeys.GO, TvFormKeys.SHOW, TvFormKeys.KBD))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvActionButton(TvFormKeys.GO, if (model.busy || model.probing) "登录中" else "登录", ::submit, icon = GuguIcon.LogIn, primary = true)
            TvActionButton(TvFormKeys.SHOW, if (form.show) "隐藏密码" else "显示密码", { form.show = !form.show })
            TvActionButton(TvFormKeys.KBD, if (form.kbd) "收起键盘" else "遥控器键盘", {
                form.editing = null
                if (!form.kbd) {
                    // 打开键盘：字先进第一个还空着的框（新服务器 = 地址）；之后停在哪个框就进哪个框
                    form.active = fields.firstOrNull { k ->
                        when (k) {
                            TvFormKeys.ADDR -> model.address.isEmpty()
                            TvFormKeys.USER -> model.username.isEmpty()
                            else -> model.password.isEmpty()
                        }
                    } ?: fields.last()
                }
                form.kbd = !form.kbd
            })
        }
        val accounts = model.target?.accounts.orEmpty().takeIf { !adding }.orEmpty()
        if (accounts.isNotEmpty()) {
            TvSecHead("这台服务器上的账号", "SAVED", Modifier.padding(top = 34.dp), small = true)
            TvGroup("accounts", accounts.map { "account-${it.userId}" })
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (a in accounts) TvChip("account-${a.userId}", a.userName, a.userName == model.username, { model.target?.stored?.let { s -> model.switchTo(s, a) } })
            }
        } else groups.remove("accounts")
    }
    // 打开遥控器键盘：焦点进键盘
    LaunchedEffect(form.kbd) { if (form.kbd) groups.enterWhenReady("kb-1", g.focus) }
}

/** 右栏：键盘收着时是吉祥物递钥匙 + 三句用法；密码不对换成歪头那张。 */
@Composable
fun TvLoginHelp(model: LoginModel) {
    val colors = Guga.colors
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).testTag("tv-login-help")) {
        if (LocalGugaPrefs.current.showMascot) MascotPose(if (model.badPassword) "G15b" else "G15", 240.dp, label = if (model.badPassword) "密码不对" else "递钥匙")
        TvSecHead("怎么填", "HOW TO", Modifier.padding(top = 18.dp), small = true)
        val tips = listOf(
            "在框上按确定开始输入，电视会弹出输入法。",
            "输入法里的「下一项」跳到下一格；密码那一格按「完成」就登录。",
            "地址可以整段粘贴（浏览器里 Emby 网页的地址也行），协议、主机、端口会自己拆出来。",
            "电视没有输入法、或者不好用：按「遥控器键盘」。",
        )
        for (t in tips) {
            Row(Modifier.padding(top = 14.dp)) {
                BasicText("·", Modifier.width(26.dp), style = Guga.song(25.sp, FontWeight.Black, colors.seal), maxLines = 1)
                BasicText(t, style = Guga.song(24.sp, color = colors.dim, lineHeight = 36.sp), maxLines = 3)
            }
        }
    }
}

/**
 * 一个输入框：平时是焦点块（标签 + 大号的字），焦点 = 米白实底 + 校对角；按确定 = 编辑（里面换成真的文本框 + 系统输入法）。
 * 遥控器键盘开着时，正在接字的那一格有一道朱红光标。
 */
@Composable
private fun TvInputField(
    key: String,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    form: TvFormState,
    placeholder: String,
    keyboardType: KeyboardType,
    ime: ImeAction,
    onIme: () -> Unit,
    onLeave: (TvDir) -> Unit,
    mask: Boolean = false,
    error: Boolean = false,
) {
    val colors = Guga.colors
    val editing = form.editing == key
    val caret = form.kbd && !editing && form.active == key
    val shownText = if (mask) "•".repeat(value.length) else value
    TvBlockFocus(
        key,
        { form.active = key; form.editing = key },
        Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) form.active = key }
            .then(if (editing) Modifier.background(colors.paper).border(3.dp, colors.spot) else Modifier)
            .drawBehind { if (!editing) drawLine(if (error) colors.seal else colors.line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) },
        label = label,
        kind = TvFocusKind.Row,
    ) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Column(Modifier.fillMaxWidth().height(TvFormSpec.FIELD_H).padding(horizontal = 24.dp, vertical = 14.dp).testTag(if (editing) "tv-field-editing" else if (caret) "tv-field-active" else "tv-field")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(label, style = Guga.song(22.sp, color = if (focused) colors.onInk.copy(alpha = 0.75f) else colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                Spacer(Modifier.weight(1f))
                val hint = when {
                    editing -> "输入法里按「下一项」· 返回 = 收起"
                    focused -> "确定 = 输入"
                    else -> null
                }
                if (hint != null) BasicText(hint, style = Guga.song(20.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.faint), maxLines = 1)
            }
            Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(62.dp), contentAlignment = Alignment.CenterStart) {
                if (editing) {
                    TvEditText(value, onChange, mask, keyboardType, ime, onIme, onLeave)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (value.isEmpty()) {
                            if (placeholder.isNotEmpty()) BasicText(placeholder, style = Guga.song(28.sp, color = if (focused) colors.onInk.copy(alpha = 0.55f) else colors.faint), maxLines = 1)
                        } else if (caret) {
                            // 遥控器键盘正往这一格打字：看末尾（光标在末尾）
                            BasicText(shownText.takeLast(20), style = Guga.numeral(46.sp, ink), maxLines = 1)
                        } else {
                            // 平时看开头（地址的主机名在前面），太长在末尾省略
                            BasicText(shownText, Modifier.weight(1f, fill = false), style = Guga.numeral(46.sp, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (caret) Box(Modifier.padding(start = 6.dp).width(5.dp).height(50.dp).background(colors.seal))
                    }
                }
            }
        }
    }
}

/** 编辑中的真文本框：拿焦点 + 弹输入法；上下 = 离开，左右 = 移光标（电视的方向键不交给系统去挪焦点）。 */
@Composable
internal fun TvEditText(value: String, onChange: (String) -> Unit, mask: Boolean, keyboardType: KeyboardType, ime: ImeAction, onIme: () -> Unit, onLeave: (TvDir) -> Unit) {
    val colors = Guga.colors
    val keyboard = LocalSoftwareKeyboardController.current
    val requester = remember { FocusRequester() }
    var tfv by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // 外面改了字（遥控器键盘、去掉账号密码）：光标放到最后
    val shown = if (tfv.text == value) tfv else TextFieldValue(value, TextRange(value.length))
    LaunchedEffect(Unit) {
        runCatching { requester.requestFocus() }
        keyboard?.show()
        TvLoginProbe.imeShows++
    }
    BasicTextField(
        shown,
        {
            tfv = it
            if (it.text != value) onChange(it.text)
        },
        Modifier
            .fillMaxWidth()
            .typingTarget()
            .focusRequester(requester)
            .onPreviewKeyEvent { e ->
                val down = e.type == KeyEventType.KeyDown
                when (e.key) {
                    Key.DirectionUp -> { if (down) onLeave(TvDir.Up); true }
                    Key.DirectionDown -> { if (down) onLeave(TvDir.Down); true }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (down) {
                            val to = (shown.selection.end + if (e.key == Key.DirectionLeft) -1 else 1).coerceIn(0, shown.text.length)
                            tfv = shown.copy(selection = TextRange(to))
                        }
                        true
                    }
                    else -> false
                }
            }
            .testTag("tv-edit-text"),
        textStyle = Guga.numeral(46.sp, colors.ink),
        singleLine = true,
        cursorBrush = SolidColor(colors.seal),
        visualTransformation = if (mask) PasswordVisualTransformation('•') else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ime, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
    )
}
