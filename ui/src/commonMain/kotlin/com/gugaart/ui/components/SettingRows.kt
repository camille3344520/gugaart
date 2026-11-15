package com.gugaart.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/*
 * 设置页的行和控件（设计稿 `p=settings`）。纸面语言：没有卡片、没有玻璃、没有圆角开关——
 * 一行 = 标题 + 一句说明 + 右端的控件，行间一条细线；控件是直角的格子（选中 = 米白实底墨字）。
 * 数值（行高、格子尺寸）在 [SettingSpec]。
 */

object SettingSpec {
    /** 一行的最小高度（带说明的行自己撑高）。 */
    val ROW = 68.dp
    /** 开关 / 分段的一格。 */
    val CELL_H = 34.dp
    val CELL_W = 40.dp
    /** 步进器的方键。 */
    val STEP = 34.dp
    /** 步进器中间的数值栏。 */
    val STEP_VALUE = 74.dp
}

/** 分组标题：宋体 + 专色斜体英文小标（「播放引擎 ENGINE」）。英文是装饰。 */
@Composable
fun SettingGroup(title: String, english: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    Row(modifier.fillMaxWidth().padding(top = 34.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        BasicText(title, style = Guga.song(type.section * 0.96f, FontWeight.Black, letterSpacing = 0.06.em), maxLines = 1)
        Spacer(Modifier.width(12.dp))
        BasicText(english, Modifier.padding(bottom = 3.dp), style = Guga.numeral(type.kicker * 1.05f, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
    }
}

/**
 * 一行设置：左边标题 + 说明（说明最多两行），右边控件；下面一条细线。整行可点时给 [onClick]（跳转行、推开选择器的行）。
 */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    tag: String? = null,
    onClick: (() -> Unit)? = null,
    control: (@Composable () -> Unit)? = null,
) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Column(modifier.fillMaxWidth().then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = SettingSpec.ROW)
                .then(if (onClick != null) Modifier.pressGraphics(press).pressable(press, label = label, onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 14.dp)) {
                BasicText(label, style = Guga.song(type.row, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!note.isNullOrEmpty()) BasicText(note, Modifier.padding(top = 4.dp), style = Guga.song(type.caption * 0.96f, color = colors.faint, lineHeight = type.caption * 1.5f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            control?.invoke()
        }
        Rule()
    }
}

/** 只读的一行（「平台 ……… Android 14」）。没有值 → 这一行不出现。 */
@Composable
fun InfoRow(label: String, value: String?, modifier: Modifier = Modifier, note: String? = null) {
    if (value.isNullOrEmpty()) return
    SettingRow(label, modifier, note = note) { ValueText(value) }
}

/** 右端的斜体值。 */
@Composable
fun ValueText(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.widthIn(max = 190.dp), style = Guga.note(Guga.type.row * 0.94f, Guga.colors.dim).copy(textAlign = TextAlign.End), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 右端的斜体值 + 一个小箭头（跳转 / 推开）。[open]：推开着 → 箭头朝下。 */
@Composable
fun ValueChevron(text: String?, open: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!text.isNullOrEmpty()) ValueText(text)
        Spacer(Modifier.width(8.dp))
        Icon(if (open) GuguIcon.ChevronDown else GuguIcon.ChevronRight, Guga.colors.dim, 13.dp)
    }
}

/** 右端的斜体小动作（「清除」「切换」「检查」）。[danger]：朱红（退出、恢复默认的确认）。 */
@Composable
fun ActionWord(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, danger: Boolean = false) {
    val colors = Guga.colors
    val press = rememberPressState()
    Box(modifier.pressGraphics(press).heightIn(min = 44.dp).pressable(press, enabled = enabled, label = text, onClick = onClick).padding(start = 10.dp), contentAlignment = Alignment.CenterEnd) {
        BasicText(text, style = Guga.note(Guga.type.row * 0.94f, if (!enabled) colors.faint else if (danger) colors.seal else colors.ink), maxLines = 1)
    }
}

@Composable
private fun Cell(text: String, selected: Boolean, onClick: () -> Unit, minWidth: Dp, enabled: Boolean = true) {
    val colors = Guga.colors
    val press = rememberPressState()
    Box(
        Modifier.pressGraphics(press).height(SettingSpec.CELL_H).widthIn(min = minWidth)
            .then(if (selected) Modifier.background(if (enabled) colors.ink else colors.faint) else Modifier)
            .pressable(press, enabled = enabled, label = text, onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag(if (selected) "cell-selected" else "cell"),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Guga.song(13.sp, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) colors.onInk else colors.dim), maxLines = 1)
    }
}

/** 开 / 关：两格，选中的那格米白实底。**不是**滑块开关；选中不靠颜色单独表达（实底 + 加粗）。 */
@Composable
fun OnOff(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.border(1.dp, Guga.colors.line).testTag(if (on) "onoff-on" else "onoff-off")) {
        Cell("开", on, { if (!on) onChange(true) }, SettingSpec.CELL_W, enabled)
        Cell("关", !on, { if (on) onChange(false) }, SettingSpec.CELL_W, enabled)
    }
}

/** 分段：几格里选一格（「关闭 / 显示按钮 / 自动跳过」）。 */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = Guga.colors
    Row(modifier.border(1.dp, colors.line).testTag("segmented")) {
        options.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).height(SettingSpec.CELL_H).background(colors.line))
            Cell(label, value == selected, { onSelect(value) }, 30.dp, enabled)
        }
    }
}

/** 步进器：－ 值 ＋。到头的那一边变淡、不能点。 */
@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier, canMinus: Boolean = true, canPlus: Boolean = true) {
    val colors = Guga.colors
    @Composable
    fun key(icon: GuguIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
        val press = rememberPressState()
        Box(Modifier.pressGraphics(press).size(SettingSpec.STEP).border(1.dp, if (enabled) colors.dim else colors.line).pressable(press, enabled = enabled, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, if (enabled) colors.ink else colors.faint, 13.dp)
        }
    }
    Row(modifier.testTag("stepper"), verticalAlignment = Alignment.CenterVertically) {
        key(GuguIcon.Minus, "减小", canMinus, onMinus)
        BasicText(value, Modifier.width(SettingSpec.STEP_VALUE), style = Guga.song(Guga.type.row * 0.96f, FontWeight.Bold).copy(textAlign = TextAlign.Center), maxLines = 1)
        key(GuguIcon.Plus, "增大", canPlus, onPlus)
    }
}

/** 上移 / 下移两个方键（首页板块、弹幕来源的顺序）。 */
@Composable
fun MoveKeys(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    @Composable
    fun key(icon: GuguIcon, label: String, enabled: Boolean, onClick: () -> Unit) {
        val press = rememberPressState()
        Box(Modifier.pressGraphics(press).size(SettingSpec.STEP).border(1.dp, if (enabled) colors.dim else colors.line).pressable(press, enabled = enabled, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, if (enabled) colors.ink else colors.faint, 13.dp)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        key(GuguIcon.ChevronUp, "上移", canUp, onUp)
        key(GuguIcon.ChevronDown, "下移", canDown, onDown)
    }
}

/** 勾选的一个词（「■ 滚动　■ 顶部　□ 底部」）：选中 = 实心小方块 + 米白字。 */
@Composable
fun CheckWord(text: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(modifier.pressGraphics(press).heightIn(min = 44.dp).pressable(press, label = text, onClick = onToggle).testTag(if (checked) "check-on" else "check-off"), verticalAlignment = Alignment.CenterVertically) {
        RadioMark(checked)
        BasicText(text, Modifier.padding(start = 7.dp), style = Guga.song(13.5.sp, if (checked) FontWeight.Bold else FontWeight.Normal, if (checked) colors.ink else colors.dim), maxLines = 1)
    }
}

/** 选择器里的一项。[note]：右端的斜体说明（「推荐」「系统硬解，最省电」）。 */
data class PickOption<T>(val value: T, val label: String, val note: String? = null)

/**
 * 推开式的选择器（设计稿 `settings-pick`）：在这一行下面**推开**一列选项，把下面的内容往下推，不盖住；左边一条朱红竖线。
 * 选中 = 实心小方块 + 加粗。[multi]：多选（语言优先级），那时 [selected] 是一组值，点一下切换。
 */
@Composable
fun <T> PickList(options: List<PickOption<T>>, selected: Collection<T>, onPick: (T) -> Unit, modifier: Modifier = Modifier, order: Boolean = false) {
    val colors = Guga.colors
    val type = Guga.type
    Column(modifier.fillMaxWidth().drawBehind { drawLine(colors.seal, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx()) }.padding(start = 20.dp).testTag("pick-list")) {
        for (o in options) {
            val on = o.value in selected
            val press = rememberPressState()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).pressGraphics(press).pressable(press, label = o.label, onClick = { onPick(o.value) }).testTag(if (on) "pick-selected" else "pick-option"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioMark(on)
                BasicText(o.label, Modifier.weight(1f).padding(start = 18.dp, end = 10.dp), style = Guga.song(type.row, if (on) FontWeight.Bold else FontWeight.Normal, if (on) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 多选带次序时，右端写第几位
                val place = if (order && on) "第 ${selected.indexOf(o.value) + 1} 位" else o.note
                if (!place.isNullOrEmpty()) BasicText(place, style = Guga.note(type.caption, colors.faint), maxLines = 1)
            }
            Rule()
        }
    }
}

/**
 * 一行 + 它下面推开的选择器。点这一行推开 / 收起；单选选完自动收起。
 */
@Composable
fun <T> PickRow(
    label: String,
    value: String?,
    options: List<PickOption<T>>,
    selected: Collection<T>,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    tag: String? = null,
    multi: Boolean = false,
    startOpen: Boolean = false,
) {
    var open by remember { mutableStateOf(startOpen) }
    Column(modifier) {
        SettingRow(label, note = note, tag = tag, onClick = { open = !open }) { ValueChevron(value, open) }
        AnimatedVisibility(open, enter = expandVertically(), exit = shrinkVertically()) {
            PickList(options, selected, {
                onPick(it)
                if (!multi) open = false
            }, order = multi)
        }
    }
}

/**
 * 最小的文字输入行（设置里的 User-Agent / Referer / 屏蔽词 / 密钥）：一条下划线，没有框。[secret]：按密码显示（密钥不上版面）。
 * 完整的输入框组件（`Fields.kt`）在登录页那边；这里只够设置用。
 */
@Composable
fun InputLine(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, secret: Boolean = false, tag: String? = null) {
    val colors = Guga.colors
    val style = Guga.song(Guga.type.row * 0.92f)
    Box(
        modifier.fillMaxWidth().heightIn(min = 44.dp).drawBehind { drawLine(colors.dim, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 1.dp.toPx()) }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) BasicText(placeholder, style = style.copy(color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicTextField(
            value,
            onChange,
            Modifier.fillMaxWidth(),
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(colors.ink),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        )
    }
}
