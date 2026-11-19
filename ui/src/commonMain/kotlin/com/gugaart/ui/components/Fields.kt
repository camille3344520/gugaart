package com.gugaart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.icons.GuguIcon
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.DisposableEffect
import com.gugugaga.app.ui.LocalGraph
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 纸面输入框的几个数（设计稿 `servers-add` / `servers-login`）。 */
object FieldSpec {
    /** 普通字段的字号（用户名、密码）。 */
    val TEXT = 22.sp
    /** 大字段的字号（服务器地址）。 */
    val TEXT_BIG = 30.sp
    /** 下划线：平时米白 2 dp，出错朱红。 */
    val UNDERLINE = 2.dp
}

/**
 * 输入框焦点报给上游（`AppGraph.typingFocusChanged` → `isTyping`）：聚焦期间单键快捷键（`/`、字母）stand down，
 * 只有 ⌘/Ctrl 组合键还生效（主理人实测：设置里填弹幕服务器地址打「/」会跳搜索）。离开组合时若还聚焦要减回去，计数不泄漏。
 */
fun Modifier.typingTarget(): Modifier = composed {
    val graph = LocalGraph.current
    var focused by remember { mutableStateOf(false) }
    DisposableEffect(graph) { onDispose { if (focused) graph.typingFocusChanged(false) } }
    onFocusChanged {
        focused = it.isFocused
        graph.typingFocusChanged(it.isFocused)
    }
}

/**
 * 纸面输入框：专色小标签 + 宋体大字 + 一条通栏下划线。没有框、没有底色（纸面语言，不是玻璃）。
 *
 * - [password]：默认不回显（圆点）；行尾有一只「眼睛」，用户自己点了才显示（设计稿 `servers-login`），离开这一页就复位。值不进读屏文本、不进任何日志（这里只把它交回 [onChange]）。
 * - [error]：下划线换成朱红（印章色）。出错的原因由调用方写在下面，不靠颜色单独表达。
 * - [trailing]：行尾的小东西（「找到了」的勾）。
 */
@Composable
fun PaperField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    error: Boolean = false,
    big: Boolean = false,
    enabled: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Next,
    onIme: (() -> Unit)? = null,
    tag: String = "paper-field",
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Guga.colors
    val size: TextUnit = if (big) FieldSpec.TEXT_BIG else FieldSpec.TEXT
    var reveal by remember { mutableStateOf(false) }
    val masked = password && !reveal
    Column(modifier.fillMaxWidth()) {
        BasicText(label, style = Guga.song(Guga.type.caption, color = colors.spot, letterSpacing = 0.3.em), maxLines = 1)
        Row(Modifier.fillMaxWidth().heightIn(min = if (big) 64.dp else 54.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) BasicText(placeholder, style = Guga.song(size * 0.72f, color = colors.faint), maxLines = 1)
                BasicTextField(
                    value,
                    onChange,
                    Modifier.fillMaxWidth().testTag(tag).typingTarget().semantics { contentDescription = label },
                    enabled = enabled,
                    textStyle = Guga.song(size, FontWeight.Black, if (enabled) colors.ink else colors.dim, letterSpacing = if (masked) 0.4.em else TextUnit.Unspecified),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.spot),
                    visualTransformation = if (masked) PasswordVisualTransformation('•') else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = ime, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onAny = { onIme?.invoke() }),
                )
            }
            trailing?.invoke()
            if (password) {
                val press = rememberPressState()
                Box(Modifier.size(44.dp).pressGraphics(press).pressable(press, enabled = enabled, label = if (reveal) "隐藏密码" else "显示密码") { reveal = !reveal }.testTag("$tag-reveal"), contentAlignment = Alignment.Center) {
                    Icon(if (reveal) GuguIcon.EyeOff else GuguIcon.Eye, colors.dim, 20.dp)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(FieldSpec.UNDERLINE).background(if (error) colors.seal else colors.ink))
    }
}

/**
 * 一排分段选项（「自动 | http | https」）：1 dp 米白线框，选中 = 米白实底墨字。纸面的，不是玻璃。
 */
@Composable
fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Row(modifier.border(1.dp, colors.dim)) {
        for ((i, o) in options.withIndex()) {
            val press = rememberPressState()
            val on = i == selected
            Box(
                Modifier.pressGraphics(press).heightIn(min = 34.dp).then(if (on) Modifier.background(colors.ink) else Modifier.border(0.5.dp, colors.line))
                    .pressable(press, label = o) { onSelect(i) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(o, style = Guga.song(13.sp, FontWeight.Bold, if (on) colors.onInk else colors.dim), maxLines = 1)
            }
        }
    }
}
