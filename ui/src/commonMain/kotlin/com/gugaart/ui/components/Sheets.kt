package com.gugaart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 纸片（菜单、对话框、号外）的几个数（设计稿 `update`、电脑的 `dialogs`）。纸片是纸面语言，**不是玻璃**（docs/glass.md）。 */
object SheetSpec {
    /** 压在版面上的纸片后面那层墨：纸色 72 %（版面还隐约看得见，知道自己在哪一页）。 */
    const val SCRIM = 0.72f
    /** 纸片的底：比纸略亮的一档墨（和版面分得开）。 */
    val SLIP = Color(0xFF1A1712)
    /** 双线框：外线和内线之间的空。 */
    val FRAME_GAP = 5.dp
    val PADDING = 22.dp
    val MAX_WIDTH = 520.dp
}

/**
 * 一张纸片：比纸亮一档的墨底 + 双线框（外细内实）。只是外观，不管它放在哪。
 */
@Composable
fun Slip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = Guga.colors
    Column(
        modifier
            .background(SheetSpec.SLIP)
            .border(1.dp, colors.line)
            .padding(SheetSpec.FRAME_GAP)
            .border(1.dp, colors.ink.copy(alpha = 0.85f))
            .padding(SheetSpec.PADDING),
        content = content,
    )
}

/**
 * 把一张纸片压在版面正中：后面一层墨（[SheetSpec.SCRIM]），纸片居中。画在根上（整个窗口），所以调用方要把它放在根的 Box 里。
 * - [onDismiss] 不为空：点纸片外面 / 系统返回 = 收起；为空（强制更新）：收不掉，返回键被吃掉。
 * - 这是版面上唯一的「盖东西」的做法，只给必须打断的事用（更新、二次确认）；展开选项一律用推开（docs/design-language.md §8）。
 */
@Composable
fun SheetHost(onDismiss: (() -> Unit)?, modifier: Modifier = Modifier, tag: String = "sheet", content: @Composable ColumnScope.() -> Unit) {
    val colors = Guga.colors
    val back = LocalBackDispatcher.current
    val dismiss = rememberUpdatedState(onDismiss)
    DisposableEffect(back) {
        val off = back.push {
            dismiss.value?.invoke()
            true
        }
        onDispose { off() }
    }
    Box(
        modifier.fillMaxSize().background(colors.paper.copy(alpha = SheetSpec.SCRIM)).pointerInput(Unit) { detectTapGestures { dismiss.value?.invoke() } }.testTag("$tag-scrim"),
        contentAlignment = Alignment.Center,
    ) {
        Slip(
            Modifier.padding(horizontal = Guga.dimens.margin - 6.dp).widthIn(max = SheetSpec.MAX_WIDTH).fillMaxWidth()
                // 点在纸片上不算点外面
                .pointerInput(Unit) { detectTapGestures { } }.testTag(tag),
            content = content,
        )
    }
}

/** 纸片的页眉：朱红实底的签（「号外」「确认」）+ 斜体英文小标 + 右端斜体编号，下面一条实线。 */
@Composable
fun SlipHeader(badge: String, english: String, trailing: String? = null) {
    val colors = Guga.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BasicText(badge, Modifier.background(colors.seal).padding(horizontal = 9.dp, vertical = 3.dp), style = Guga.song(15.sp, FontWeight.Black, colors.ink, letterSpacing = 0.2.em), maxLines = 1)
        BasicText(english, Modifier.padding(start = 10.dp).weight(1f), style = Guga.note(Guga.type.kicker, colors.dim, letterSpacing = 0.2.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) BasicText(trailing, style = Guga.numeral(Guga.type.caption, colors.dim), maxLines = 1)
    }
    Rule(Modifier.padding(top = 10.dp), strong = true)
}

/**
 * 二次确认（退出登录、移除服务器…）：一张纸片，标题 + 一句说明 + 主按钮 + 「算了」。[danger]：主按钮的字是朱红（不可撤销的动作）。
 */
@Composable
fun ConfirmSheet(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false, cancel: String = "算了") {
    val colors = Guga.colors
    SheetHost(onDismiss, tag = "confirm-sheet") {
        SlipHeader("确认", "CONFIRM")
        BasicText(title, Modifier.padding(top = 16.dp), style = Guga.song(Guga.type.section, FontWeight.Black), maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicText(text, Modifier.padding(top = 8.dp), style = Guga.song(Guga.type.lead * 0.94f, color = colors.dim, lineHeight = 24.sp))
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            if (danger) TextLink(confirm, onConfirm, color = colors.seal) else PrimaryButton(confirm, onConfirm, icon = null)
            TextLink(cancel, onDismiss, color = if (danger) colors.ink else colors.dim)
        }
    }
}

/**
 * 纸片菜单的一行：图标 + 宋体字 + 可选的右端小字。整行可点；[danger] = 朱红字。菜单本身 = `Slip { MenuRow… }`，由调用方决定放在哪
 * （手机：推开在触发它的那一行下面；电脑：贴着触发点）。
 */
@Composable
fun MenuRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GuguIcon? = null, trailing: String? = null, danger: Boolean = false) {
    val colors = Guga.colors
    val press = rememberPressState()
    val ink = if (danger) colors.seal else colors.ink
    Row(
        modifier.fillMaxWidth().heightIn(min = Guga.dimens.minTarget).pressGraphics(press).pressable(press, label = text, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, ink, 16.dp)
        BasicText(text, Modifier.weight(1f), style = Guga.song(Guga.type.row * 0.94f, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) BasicText(trailing, style = Guga.note(Guga.type.caption, colors.dim), maxLines = 1)
    }
}
