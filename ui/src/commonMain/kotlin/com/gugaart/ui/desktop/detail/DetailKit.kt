package com.gugaart.ui.desktop.detail

import com.gugaart.ui.desktop.deskFocusRing
import com.gugaart.ui.components.ScrollArrows
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.gugaart.ui.desktop.isContextPress
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.BleedSource
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.VerticalCaption
import com.gugaart.ui.components.bleedSourceOf
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.components.hatch
import com.gugaart.ui.components.rememberCrossfade
import com.gugaart.ui.components.rememberSoft
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.desktop.DesktopContextMenu
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp

/**
 * 电脑详情页的版式数（设计稿 `desktop/css/d2.css` `d3.css` `d4.css`；单位 rem = `Guga.rem`）。只是版式，颜色 / 字体仍从 `Guga.*` 取。
 */
object DetailSpec {
    /** 整幅剧照占屏高：剧集 72 %、电影 66 %；不低于 44 rem。 */
    const val SERIES_HERO = 0.72f
    const val MOVIE_HERO = 0.66f
    const val HERO_MIN = 44f
    /** 片名盒（logo / 宋体片名）：44 × 13 rem，最宽 42 % 窗宽。 */
    const val LOGO_W = 44f
    const val LOGO_H = 13f
    const val LOGO_MAX = 0.42f
    /** 评分的大斜体数字。 */
    const val RATE = 8.4f
    /** 窗口宽度的两个断点（dp）：≥ 2200 多一栏，≤ 1280 资料栏掉到下一行。 */
    const val WIDE = 2200f
    const val NARROW = 1280f
    /** 分集卡 / 目录行的最小宽（自动填充算列数）。 */
    const val EP_MIN = 27f
    const val EP_ROW_MIN = 38f
    /** 简介：超过 330 字折 6 行；不足 60 字放大一号。 */
    const val FOLD = 330
    const val SHORT = 60
    /** 主按钮高。 */
    const val CTA = 5.6f
    /** 区块之间。 */
    const val SECTION = 5.6f
    /** 电脑详情的取景覆盖（条目 id → 取景）：网球王子的剧照顶端就是人脸，图整体下移一行。别的条目用共用表 [Framing.OVERRIDES]。 */
    val FRAMING: Map<String, Framing> = mapOf("44983" to Framing(0.5f, 0.18f, shiftDown = 0.06f))
    /** 滚到某个区块时，区块（含它上面 5.6 rem 的间隔）顶离窗口顶多远：标题落在报头 + 玻璃排之下 10.6 rem 处。 */
    const val ANCHOR = 5f
    /** 电影海报往上提多少（压在剧照的渐隐处）。 */
    const val POSTER_LIFT = 26f
}

enum class DWidth { Sm, Md, Xl }

@Composable
@ReadOnlyComposable
fun dWidth(): DWidth {
    val w = LocalWindowDp.current.width.value
    return if (w >= DetailSpec.WIDE) DWidth.Xl else if (w <= DetailSpec.NARROW) DWidth.Sm else DWidth.Md
}

/** rem → 字号（电脑的字跟着窗口宽缩放）。 */
@Composable
@ReadOnlyComposable
fun rsp(x: Float): TextUnit = (Guga.dimens.rem.value * x).sp

/** 自动填充的列数（CSS `repeat(auto-fill, minmax(min, 1fr))`）。 */
fun autoColumns(available: Float, min: Float, gap: Float): Int = ((available + gap) / (min + gap)).toInt().coerceAtLeast(1)

/* ---- 鼠标：悬停、右键 ---- */

@Composable
fun rememberHover(): MutableState<Boolean> = remember { mutableStateOf(false) }

/** 悬停状态（鼠标进 / 出）。 */
fun Modifier.hover(state: MutableState<Boolean>): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.type == PointerEventType.Enter) state.value = true
            if (e.type == PointerEventType.Exit) state.value = false
        }
    }
}

/** 右键 = 条目的纸片菜单（Host 由外壳画）。[title] = null 时不接。 */
fun Modifier.contextClick(title: Title?): Modifier = if (title == null) this else composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val ctrlCtx by rememberUpdatedState(com.gugaart.ui.desktop.ctrlClickIsContext())
    onGloballyPositioned { origin = it.positionInWindow() }.pointerInput(title.id) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.isContextPress(ctrlCtx)) {
                    DesktopContextMenu.open(title, origin + e.changes.first().position)
                    e.changes.forEach { it.consume() }
                }
            }
        }
    }
}

/**
 * 当前详情页的键盘动作（`V` 收藏、`W` 标记已看、`[` `]` 换季、`Enter` 播放）。页面在的时候把自己的动作挂上来、离开时摘掉；
 * 外壳的全局按键分发可以直接调 [handle]。页面自己也用 `onKeyEvent` 兜底（页面拿着焦点时）。
 */
object DesktopItemActions {
    var favorite: (() -> Unit)? = null
    var played: (() -> Unit)? = null
    var previousSeason: (() -> Unit)? = null
    var nextSeason: (() -> Unit)? = null
    var play: (() -> Unit)? = null

    fun clear() {
        favorite = null
        played = null
        previousSeason = null
        nextSeason = null
        play = null
    }

    /** 认得这个键并且当前页有对应的动作 → 做了，返回 true。 */
    fun handle(key: Key): Boolean {
        val action = when (key) {
            Key.V -> favorite
            Key.W -> played
            Key.LeftBracket -> previousSeason
            Key.RightBracket -> nextSeason
            Key.Enter, Key.NumPadEnter -> play
            else -> null
        } ?: return false
        action()
        return true
    }
}

/* ---- 区块标题、资料栏、按钮 ---- */

/** 区块标题：斜体编号 + 宋体 26 + 斜体英文 + 通栏细线 + 右端斜体小计。 */
@Composable
fun DSec(number: String?, title: String, english: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val colors = Guga.colors
    Row(modifier.fillMaxWidth().padding(bottom = Guga.rem(1.6f)), verticalAlignment = Alignment.Bottom) {
        if (!number.isNullOrEmpty()) BasicText(number, Modifier.padding(end = Guga.rem(1.2f), bottom = Guga.rem(0.5f)), style = Guga.numeral(rsp(1.3f), colors.spot, letterSpacing = 0.06.em), maxLines = 1)
        BasicText(title, style = Guga.song(rsp(2.6f), FontWeight.Black, letterSpacing = 0.12.em), maxLines = 1)
        if (english.isNotEmpty()) BasicText(english, Modifier.padding(start = Guga.rem(1.2f), bottom = Guga.rem(0.5f)), style = Guga.numeral(rsp(1.15f), colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        Box(Modifier.weight(1f).padding(horizontal = Guga.rem(1.2f)).padding(bottom = Guga.rem(1.3f)).height(1.dp).background(colors.line))
        if (trailing != null) BasicText(trailing, Modifier.padding(bottom = Guga.rem(0.5f)), style = Guga.note(rsp(1.25f), colors.faint), maxLines = 1)
    }
}

/** 资料栏：主线起头，下面一行一条「标签 | 内容」。 */
@Composable
fun DFacts(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.testTag("d-facts")) {
        Rule(strong = true)
        content()
    }
}

@Composable
fun DFact(label: String, content: @Composable () -> Unit) {
    val colors = Guga.colors
    Row(Modifier.fillMaxWidth().padding(vertical = Guga.rem(1.15f))) {
        BasicText(label, Modifier.width(Guga.rem(8f)), style = Guga.song(rsp(1.4f), color = colors.faint), maxLines = 1)
        Box(Modifier.weight(1f)) { content() }
    }
    Rule()
}

@Composable
fun DFact(label: String, value: String?) {
    if (value.isNullOrEmpty()) return
    DFact(label) { BasicText(value, style = Guga.song(rsp(1.4f), FontWeight.Bold, lineHeight = rsp(2.1f)), maxLines = 3, overflow = TextOverflow.Ellipsis) }
}

/** 资料里的人名：悬停变专色，点了去人物页。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DPeople(label: String, people: List<Person>, onOpen: (Person) -> Unit) {
    if (people.isEmpty()) return
    DFact(label) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.4f))) {
            for (p in people) {
                val hover = rememberHover()
                val press = rememberPressState()
                BasicText(
                    p.name,
                    Modifier.hover(hover).deskFocusRing(press).pressable(press, label = p.name, onClick = { onOpen(p) }).testTag("d-person-link"),
                    style = Guga.song(rsp(1.4f), FontWeight.Bold, if (hover.value) Guga.colors.spot else Guga.colors.ink, lineHeight = rsp(2.1f)),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 主按钮（电脑）：米白实底墨字、直角；右边一行斜体小字（时间 / 时长）；下沿外侧一条进度线（有轨道）。悬停放大 3 %，按下再放大（上游按压物理）。
 * [line] = 线框版（「从头看」）；[small] = 3.8 rem 高。
 */
@Composable
fun DButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = null, progress: Float? = null, icon: GuguIcon? = GuguIcon.Play, small: Boolean = false, line: Boolean = false, tag: String = "d-button") {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val scale by animateFloatAsState(if (hover.value) 1.03f else 1f, label = "btn-hover")
    val ink = if (line) colors.ink else colors.onInk
    val bar = Guga.rem(0.3f)
    val gap = Guga.rem(0.2f)
    Row(
        modifier
            .hover(hover)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pressGraphics(press)
            .height(Guga.rem(if (small) 3.8f else DetailSpec.CTA))
            .drawWithContent {
                drawContent()
                if (progress != null) {
                    val y = size.height + gap.toPx() + bar.toPx() / 2
                    drawLine(colors.line, Offset(0f, y), Offset(size.width, y), bar.toPx())
                    if (progress > 0f) drawLine(colors.spot, Offset(0f, y), Offset(size.width * progress.coerceIn(0f, 1f), y), bar.toPx())
                }
            }
            .then(if (line) Modifier.border(1.dp, colors.faint) else Modifier.background(colors.ink))
            .deskFocusRing(press).pressable(press, label = text, onClick = onClick)
            .padding(start = Guga.rem(if (small) 1.5f else 2.4f), end = Guga.rem(if (small) 1.8f else 2.8f))
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, ink, Guga.rem(if (small) 1.5f else 1.7f), filled = icon == GuguIcon.Play)
            Spacer(Modifier.width(Guga.rem(1f)))
        }
        BasicText(text, Modifier.weight(1f, fill = false), style = Guga.song(rsp(if (small) 1.4f else 1.7f), FontWeight.Bold, ink, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) BasicText(trailing, Modifier.padding(start = Guga.rem(1.4f)), style = Guga.note(rsp(1.25f), ink.copy(alpha = 0.7f)), maxLines = 1)
    }
}

/** 主按钮旁的文字动作（收藏 / 整部已看 / 更多）：前面可带一个小记号。悬停变专色。 */
@Composable
fun DAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String = "d-action", leading: (@Composable () -> Unit)? = null) {
    val press = rememberPressState()
    val hover = rememberHover()
    Row(modifier.hover(hover).pressGraphics(press).deskFocusRing(press).pressable(press, label = label, onClick = onClick).testTag(tag), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f))) {
        leading?.invoke()
        BasicText(label, style = Guga.song(rsp(1.4f), FontWeight.SemiBold, if (hover.value) Guga.colors.spot else Guga.colors.ink), maxLines = 1)
    }
}

/** 键位小标（`[ ] 换季`）。 */
@Composable
fun DKbd(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.border(1.dp, Guga.colors.line).padding(horizontal = Guga.rem(0.6f), vertical = Guga.rem(0.1f)), style = Guga.note(rsp(1.1f), Guga.colors.faint, letterSpacing = 0.04.em), maxLines = 1)
}

/** 线框小盒（下拉的收起样子、「本季全部标为已看」）：[open] 时线变实。 */
@Composable
fun DBox(onClick: (() -> Unit)?, modifier: Modifier = Modifier, open: Boolean = false, label: String = "", tag: String = "d-box", content: @Composable RowScope.() -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        modifier
            .height(Guga.rem(3.6f))
            .border(1.dp, if (open) colors.ink else colors.faint)
            .then(if (onClick != null) Modifier.deskFocusRing(press).pressable(press, label = label, onClick = onClick) else Modifier)
            .padding(start = Guga.rem(1.4f), end = Guga.rem(1.2f))
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f)),
        content = content,
    )
}

/** 纸片（下拉、选季列表）：实心深纸色 + 米白线框。不是玻璃，没有投影。 */
@Composable
fun DSlip(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.background(SLIP).border(1.dp, Guga.colors.ink).padding(vertical = Guga.rem(0.6f)), content = content)
}

private val SLIP = Color(0xFF1B1814)

/**
 * 纸片里的一行：悬停 / 选中 = 反白。[tags]：标题后面的小签（版本的「推荐」「另一份文件」；参数 = 这一行此刻是不是反白）；
 * [lead]：标题前面的一样东西（「源」的服务器小圆点）。
 */
@Composable
fun DSlipRow(text: String, sub: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, note: String? = null, tags: (@Composable (onInk: Boolean) -> Unit)? = null, lead: (@Composable (onInk: Boolean) -> Unit)? = null) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val on = selected || hover.value
    val ink = if (on) colors.onInk else colors.ink
    Row(
        modifier.fillMaxWidth().hover(hover).then(if (on) Modifier.background(colors.ink) else Modifier).deskFocusRing(press).pressable(press, label = text, onClick = onClick).padding(horizontal = Guga.rem(1.6f), vertical = Guga.rem(0.8f)).testTag(if (selected) "d-slip-row-on" else "d-slip-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (tags == null && lead == null) BasicText(text, style = Guga.song(rsp(1.4f), FontWeight.SemiBold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Row(verticalAlignment = Alignment.CenterVertically) {
                lead?.invoke(on)
                BasicText(text, Modifier.weight(1f, fill = false), style = Guga.song(rsp(1.4f), FontWeight.SemiBold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                tags?.invoke(on)
            }
            if (!note.isNullOrEmpty()) BasicText(note, style = Guga.song(rsp(1.2f), color = ink.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (sub != null) BasicText(sub, Modifier.padding(start = Guga.rem(2f)), style = Guga.song(rsp(1.2f), color = ink.copy(alpha = 0.6f)), maxLines = 1)
        if (selected) BasicText("✓", Modifier.padding(start = Guga.rem(1.2f)), style = Guga.numeral(rsp(1.4f), ink), maxLines = 1)
    }
}

/** 目录行（「接下来」、作品年表、合集成员）：左栏斜体编号 / 年份，中间标题 + 斜体副行，右端可带东西。悬停标题变专色。 */
@Composable
fun DRow(lead: String, title: String, sub: String?, onClick: () -> Unit, modifier: Modifier = Modifier, leadWidth: Float = 4.6f, context: Title? = null, current: Boolean = false, tag: String = "d-row", middle: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().hover(hover).contextClick(context).deskFocusRing(press).pressable(press, label = title, onClick = onClick).padding(vertical = Guga.rem(1.4f)).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
            BasicText(lead, Modifier.width(Guga.rem(leadWidth)), style = Guga.numeral(rsp(if (lead.length > 3) 2f else 2.6f), colors.faint), maxLines = 1, softWrap = false)
            Spacer(Modifier.width(Guga.rem(1.6f)))
            if (middle != null) {
                middle()
                Spacer(Modifier.width(Guga.rem(1.6f)))
            }
            Column(Modifier.weight(1f)) {
                BasicText(title, style = Guga.song(rsp(1.65f), FontWeight.Bold, if (hover.value || current) colors.spot else colors.ink, lineHeight = rsp(2.3f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!sub.isNullOrEmpty()) BasicText(sub, Modifier.padding(top = Guga.rem(0.3f)), style = Guga.note(rsp(1.25f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (trailing != null) {
                Spacer(Modifier.width(Guga.rem(1.6f)))
                trailing()
            }
        }
        Rule()
    }
}

/** 小海报（目录行里）：5.4 rem 宽、2:3、细边。 */
@Composable
fun DMiniPoster(t: Title, width: Float = 5.4f) {
    Box(Modifier.width(Guga.rem(width)).aspectRatio(2f / 3f).border(1.dp, Guga.colors.line)) {
        CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), textSize = rsp(1.6f)) }
    }
}

/** 页尾：「— 本篇完 —」+ 右端一行斜体。 */
@Composable
fun DEnd(text: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Column(modifier.padding(top = Guga.rem(7f))) {
        Rule(strong = true)
        Row(Modifier.fillMaxWidth().padding(top = Guga.rem(2.4f)), verticalAlignment = Alignment.CenterVertically) {
            BasicText("— 散场 —", style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            Spacer(Modifier.weight(1f))
            BasicText(text, style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/* ---- 演职人员：彩色照片，没有照片 = 姓氏字块 ---- */

/** 演职人员横排：3 : 4 的彩色照片 + 名字（悬停变专色）+ 「饰 角色」/ 职务。横向可滚（Shift + 滚轮 / 触控板）。出血到窗口两边。 */
@Composable
fun DCastRow(people: List<Person>, onOpen: (Person) -> Unit, modifier: Modifier = Modifier) {
    if (people.isEmpty()) return
    val colors = Guga.colors
    val w = LocalWindowDp.current.width
    val m = Guga.dimens.margin
    val gut = Guga.dimens.gutter
    val n = when (dWidth()) {
        DWidth.Sm -> 6
        DWidth.Md -> 7
        DWidth.Xl -> 11
    }
    val card = (w - m * 2 - gut * (n + 1)) / (n + 2)
    // 演员多了右边看不到（用户 2026-11-16：「演员超过数量的时候没办法查看右边的演员了」）：两头各一个翻页箭头，
    // 右边还有人才出「→」，翻过去左边才出「←」，点一下翻一屏；Shift + 滚轮 / 触控板照旧能横着滑
    val scroll = rememberScrollState()
    val page = with(androidx.compose.ui.platform.LocalDensity.current) { (w - m * 2 - card).toPx() }
    Box(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = m).testTag("d-cast"), horizontalArrangement = Arrangement.spacedBy(gut)) {
            for (p in people) {
                val hover = rememberHover()
                val press = rememberPressState()
                Column(Modifier.width(card).hover(hover).deskFocusRing(press).pressable(press, label = p.name, onClick = { onOpen(p) }).testTag("d-cast-card")) {
                    Box(Modifier.pressGraphics(press).fillMaxWidth().aspectRatio(3f / 4f).border(1.dp, colors.line)) {
                        // 彩色，没有任何去色滤镜
                        CoverImage(p.photo, Modifier.fillMaxSize(), Framing(0.5f, 0.2f)) { HatchPlate(Rules.monogram(p.name, person = true), Modifier.fillMaxSize(), textSize = rsp(4f)) }
                    }
                    BasicText(p.name, Modifier.padding(top = Guga.rem(1f)), style = Guga.song(rsp(1.45f), FontWeight.Bold, if (hover.value) colors.spot else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(castRole(p), Modifier.padding(top = Guga.rem(0.2f)), style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // 箭头立在照片的竖直中间
        ScrollArrows(scroll, page, top = card * (4f / 3f) / 2 - Guga.rem(2.4f), inset = m / 2, size = Guga.rem(4.8f), tag = "d-cast")
    }
}

/** 演职员卡片的第二行：「饰 江心白」/「演员」/「导演」。纯函数。 */
fun castRole(p: Person): String = when (p.type) {
    null, "Actor", "GuestStar" -> p.role?.let { "饰 $it" } ?: if (p.type == "GuestStar") "客串" else "演员"
    "Director" -> "导演"
    "Writer" -> "编剧"
    "Producer" -> "制片"
    else -> p.type
}

/* ---- 整幅铺满的头图（电脑） ---- */

/**
 * 电脑详情页的整幅剧照：从窗口最顶端铺到 [fraction] 屏高（报头、玻璃排直接压在上面）。暗角和渐隐画进图里：
 * 顶部 0–13 rem 由深到浅（报头 / 玻璃排读得清），40 % 起向下渐隐，底边就是纸色；左侧再压一条横向的暗（片名在左下）。
 * 没有剧照 → 海报放大虚化铺满，图注写明。片名（logo / 黑笔画 logo 垫衬纸 / 宋体）和评分压在渐隐处。
 *
 * [startPad]：片名区左边让出多少（电影：让出海报那一栏）。[under]：片名下面的一行（电影的宣传语）。
 */
@Composable
fun DHero(
    title: Title,
    fraction: Float,
    caption: String?,
    kicker: String,
    ratingNote: String?,
    modifier: Modifier = Modifier,
    startPad: Dp = 0.dp,
    nameOverride: String? = null,
    under: (@Composable () -> Unit)? = null,
) {
    val colors = Guga.colors
    val win = LocalWindowDp.current
    val m = Guga.dimens.margin
    val height = maxOf(win.height * fraction, Guga.rem(DetailSpec.HERO_MIN))
    val source = bleedSourceOf(title.art)
    val bmp = rememberImage(title.art.backdrop ?: title.art.poster, keepPrevious = true)
    val cf = rememberCrossfade(bmp, clearOnNull = true)
    val soft = rememberSoft(cf.current.takeIf { source == BleedSource.PosterBlur })
    val over = DetailSpec.FRAMING[title.id] ?: Framing.of(title.id)
    // 电脑的默认取景比手机靠上（18 %）；手动覆盖表里有的按表
    val framing = if (over === Framing.DEFAULT) Framing(0.5f, 0.18f) else over.copy(shiftDown = 0f)
    // 剧照顶端就是人脸：图整体下移一行，让玻璃排落在纸面上
    val shift = if (over.shiftDown > 0f) Guga.rem(11.6f) else 0.dp
    val top1 = Guga.rem(6f)
    val top2 = Guga.rem(13f)
    Box(modifier.fillMaxWidth().height(height).clipToBounds().testTag("d-hero-${source.name.lowercase()}")) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(colors.paper)
            val sh = shift.toPx()
            val img = if (source == BleedSource.PosterBlur) soft else cf.current
            if (img == null) hatch(colors.line.copy(alpha = 0.08f), 9.dp.toPx(), 1.dp.toPx()) else {
                inset(0f, sh, 0f, 0f) { drawCover(img, framing, cf.alpha, quality = if (source == BleedSource.PosterBlur) FilterQuality.High else FilterQuality.Medium) }
                if (source == BleedSource.PosterBlur) drawRect(colors.paper.copy(alpha = 0.34f))
            }
            val p = colors.paper
            val h = size.height
            drawRect(
                Brush.verticalGradient(
                    0f to p.copy(alpha = 0.78f),
                    (top1.toPx() / h).coerceIn(0.01f, 0.2f) to p.copy(alpha = 0.5f),
                    (top2.toPx() / h).coerceIn(0.02f, 0.39f) to p.copy(alpha = 0.16f),
                    0.4f to Color.Transparent,
                    0.72f to p.copy(alpha = 0.55f),
                    1f to p,
                ),
            )
            drawRect(Brush.horizontalGradient(0f to p.copy(alpha = 0.5f), 0.55f to Color.Transparent, 1f to Color.Transparent))
        }
        if (caption != null) {
            val text = if (source == BleedSource.PosterBlur) "海报" else caption
            if (over.captionDown > 0f) VerticalCaption(text, Modifier.align(Alignment.BottomEnd).padding(end = m - Guga.rem(2.6f).coerceAtMost(m), bottom = height * 0.22f))
            else VerticalCaption(text, Modifier.align(Alignment.TopEnd).padding(end = m - Guga.rem(2.6f).coerceAtMost(m), top = Guga.rem(13.2f)))
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = m + startPad, end = m, bottom = Guga.rem(1f)), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    kicker,
                    // 朱红栏目行压在剧照上：垫足够深的纸底才读得清（亮剧照如《海王2》——0.5 的纸底压不住亮图，专色小字对比不够）。
                    // 照 design-language §1.3「靠墨底、不描边不发光」：把纸底加深到近实底，任何亮图上都 ≥ 4.5。
                    Modifier.offset(x = -Guga.rem(0.8f)).background(colors.paper.copy(alpha = 0.9f)).padding(horizontal = Guga.rem(0.8f), vertical = Guga.rem(0.3f)).testTag("d-kicker"),
                    style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val name = nameOverride ?: title.name
                // 超过 9 个字降一号（并允许折两行）
                TitleLogo(
                    name,
                    title.art.logo.takeIf { nameOverride == null },
                    width = minOf(Guga.rem(DetailSpec.LOGO_W), win.width * DetailSpec.LOGO_MAX) * (if (title.art.logo == null || nameOverride != null) 1.7f else 1f),
                    height = Guga.rem(DetailSpec.LOGO_H),
                    modifier = Modifier.padding(top = Guga.rem(1.4f), bottom = Guga.rem(1.6f)),
                    alignX = 0f,
                    baseSize = rsp(if (name.length > 9) 4.6f else 5.6f),
                    textOnly = LocalPreset.current.textTitles,
                )
                under?.invoke()
            }
            Spacer(Modifier.width(Guga.rem(4f)))
            Column(horizontalAlignment = Alignment.End) {
                if (title.rating != null) BasicText(title.rating, Modifier.testTag("d-rating"), style = Guga.numeral(rsp(DetailSpec.RATE)).copy(lineHeight = rsp(DetailSpec.RATE * 0.9f)), maxLines = 1)
                if (ratingNote != null) BasicText(ratingNote, Modifier.padding(top = Guga.rem(0.4f)), style = Guga.song(rsp(1.4f), color = colors.dim, letterSpacing = 0.06.em), maxLines = 1)
            }
        }
    }
}

/** 评分下面那行小字：「评分 · KR-15」/「暂无评分 · PG-13」。 */
fun ratingNote(t: Title): String = listOfNotNull(if (t.rating != null) "评分" else "暂无评分", t.officialRating).joinToString(" · ")

/** 「2024 年 6 月 21 日」（ISO 日期 → 中文）；读不懂 → null。纯函数。 */
fun cnDate(iso: String?): String? {
    val d = iso?.take(10) ?: return null
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").matchEntire(d) ?: return null
    val (y, mo, da) = m.destructured
    return "$y 年 ${mo.toInt()} 月 ${da.toInt()} 日"
}

/** 一格一格排的网格（列等宽）。[items] 不满一行的补空位。 */
@Composable
fun <T> DGrid(items: List<T>, columns: Int, modifier: Modifier = Modifier, gapX: Dp = Guga.dimens.gutter, gapY: Dp = 0.dp, cell: @Composable BoxScope.(T) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gapY)) {
        for (row in items.chunked(columns.coerceAtLeast(1))) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gapX)) {
                for (it in row) Box(Modifier.weight(1f)) { cell(it) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 一格宽度封顶的版心（正文不超过 86 rem）。 */
fun Modifier.readable(max: Dp): Modifier = widthIn(max = max)

/** 剧照一排（电脑）：16:9，一屏露四张半，横滑；点开在根上看大图（[com.gugaart.ui.screens.detail.StillsOverlay]）。 */
@Composable
fun DStills(stills: List<com.gugugaga.app.model.Still>) {
    val w = LocalWindowDp.current.width
    val m = Guga.dimens.margin
    val card = (w - m * 2) / 4.5f
    com.gugaart.ui.screens.detail.StillsBand(
        stills,
        { com.gugaart.ui.screens.detail.StillsOverlay.open(stills, it) },
        Modifier.padding(top = Guga.rem(1.6f)),
        cardWidth = card,
        margin = m,
        header = false,
        arrows = true,
    )
}
