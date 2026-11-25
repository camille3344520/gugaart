package com.gugaart.ui.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.highlighted
import com.gugaart.ui.components.progressLine
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.PageChrome
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideNav
import com.gugugaga.app.AppGraph
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.LocalInputMode
import com.gugugaga.ds.physics.LocalMotionPrefs
import com.gugugaga.ds.physics.PressState
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlin.math.floor

/**
 * 电脑共用部件的数（设计稿 `desktop/css/d.css`，单位 rem：1440 宽时 1 rem = 10 dp）。
 * 悬停 / 焦点的规则见 `docs/platforms.md` §2。
 */
object DeskSpec {
    /** 海报墙：最小卡宽（自动填充列数）。 */
    const val CARD_MIN = 15.6f

    /** 海报墙的行距。 */
    const val GRID_ROW_GAP = 3.2f

    /** 悬停的海报朝鼠标倾的最大角度（左右 / 上下）。 */
    const val TILT_Y = 7f
    const val TILT_X = 4f

    /** 按钮悬停放大 3 %（按下的放大是上游的按压物理）。 */
    const val HOVER_SCALE = 1.03f

    /** 键盘焦点环：2 dp 米白线，外扩 4 dp。 */
    val FOCUS_WIDTH = 2.dp
    val FOCUS_GROW = 4.dp

    /** 区块标题到内容。 */
    const val HEAD_GAP = 1.6f

    /** 目录行缩略图的宽。 */
    const val TOC_THUMB = 12.8f

    /** 横向海报行里一屏排几张：≤ 1280 → 6，1440 → 7，≥ 1900 → 9，≥ 2700 → 11。 */
    fun rowCount(windowWidthDp: Float): Int = when {
        windowWidthDp <= 1280f -> 6
        windowWidthDp >= 2700f -> 11
        windowWidthDp >= 1900f -> 9
        else -> 7
    }

    /** 海报墙的列数：`repeat(auto-fill, minmax(15.6rem, 1fr))`。1100 → 6、1440 → 7、3168 → 12。纯函数，有单测。 */
    fun wallColumns(contentWidthDp: Float, gutterDp: Float, remDp: Float): Int =
        floor((contentWidthDp + gutterDp) / (CARD_MIN * remDp + gutterDp)).toInt().coerceAtLeast(1)

    /** 三栏跨页 / 多一栏的断点（设计稿 `@media (min-width: 2200px)`）。 */
    const val XL_FROM = 2200f
}

/** 电脑的字号：x rem → sp（字跟着窗口宽在 0.94–1.3 之间缩放）。 */
@Composable
@ReadOnlyComposable
fun rsp(x: Float): TextUnit = (Guga.dimens.rem.value * x).sp

/** 纸片（菜单、下拉、对话框）的底：纸色上压一层很淡的米白。不是玻璃。 */
@Composable
@ReadOnlyComposable
fun slipColor(): Color = Guga.colors.ink.copy(alpha = 0.055f).compositeOver(Guga.colors.paper)

/* ------------------------------------------------------------------ 悬停 / 右键 / 键盘焦点 */

/** 鼠标在不在这一格上、在哪（[at]：−1..1，格子正中 = 0）。 */
@Stable
class HoverState {
    var over by mutableStateOf(false)
        internal set
    var at by mutableStateOf(Offset.Zero)
        internal set
}

@Composable
fun rememberHover(): HoverState = remember { HoverState() }

/** 跟踪悬停（不消费事件，按压 / 点击照常）。 */
fun Modifier.hover(state: HoverState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            when (e.type) {
                PointerEventType.Enter, PointerEventType.Move -> {
                    val p = e.changes.firstOrNull()?.position ?: continue
                    state.over = true
                    if (size.width > 0 && size.height > 0) {
                        state.at = Offset((p.x / size.width * 2f - 1f).coerceIn(-1f, 1f), (p.y / size.height * 2f - 1f).coerceIn(-1f, 1f))
                    }
                }
                PointerEventType.Exit -> {
                    state.over = false
                    state.at = Offset.Zero
                }
            }
        }
    }
}

/**
 * 右键按下：真的右键（含触控板的双指点按）；Mac 宿主（⌘ 键位）上 ⌃ 点按也算右键——系统惯例（`design/macos/notes.md` §1 第 14 条、
 * 对齐清单 A01）。AWT 把 ⌃ 点按报成「主键 + Ctrl」，不是副键，所以要单独认；Windows 的 Ctrl + 点击不变。
 */
internal fun PointerEvent.isContextPress(ctrlClickIsContext: Boolean): Boolean =
    type == PointerEventType.Press &&
        (buttons.isSecondaryPressed || (ctrlClickIsContext && buttons.isPrimaryPressed && keyboardModifiers.isCtrlPressed))

/** Mac 宿主：⌃ 点按 = 右键（[isContextPress]）。 */
@Composable
internal fun ctrlClickIsContext(): Boolean = LocalDesktopHost.current.modifier == ModifierGlyph.Cmd

/** 右键：回调拿到鼠标在窗口里的位置（px），直接交给 [DesktopContextMenu.open]。 */
fun Modifier.onRightClick(onClick: (Offset) -> Unit): Modifier = composed {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cb by rememberUpdatedState(onClick)
    val ctrlCtx by rememberUpdatedState(ctrlClickIsContext())
    onGloballyPositioned { coords = it }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent()
                if (e.isContextPress(ctrlCtx)) {
                    val ch = e.changes.firstOrNull() ?: continue
                    ch.consume()
                    cb(coords?.takeIf { it.isAttached }?.localToRoot(ch.position) ?: ch.position)
                }
            }
        }
    }
}

/** 悬停放大 3 %（按钮）。按下的放大由上游的 `pressGraphics` 另算，两者相乘。 */
fun Modifier.hoverLift(state: HoverState, scale: Float = DeskSpec.HOVER_SCALE): Modifier = composed {
    val reduced = LocalMotionPrefs.current.reducedMotion
    val s by animateFloatAsState(if (state.over && !reduced) scale else 1f, spring(0.6f, 420f), label = "hoverLift")
    graphicsLayer {
        scaleX = s
        scaleY = s
    }
}

/**
 * 键盘焦点环：2 dp 米白线、外扩 4 dp；只在按过键之后出现，鼠标一动就消失（上游 `LocalInputMode`，`GuguTheme` 里跟踪）。
 * 放在 `pressable` 所在的那个节点上（画在自己的边界外，不占版面）。
 *
 * [inside]：环画在边界**里面**，两头是圆的——给玻璃导航胶囊里的格子用：胶囊会把出了界的东西裁掉，画在外面的方框只剩左右两道竖线
 * （上下两边正好落在胶囊外面）。
 */
fun Modifier.deskFocusRing(press: PressState, force: Boolean = false, inside: Boolean = false): Modifier = composed {
    val show = force || (press.focused && LocalInputMode.current.keyboard)
    val ink = Guga.colors.ink
    drawWithContent {
        drawContent()
        if (show) {
            val g = DeskSpec.FOCUS_GROW.toPx()
            val w = DeskSpec.FOCUS_WIDTH.toPx()
            if (inside) {
                val h = size.height - 2 * g
                drawRoundRect(ink, Offset(g, g), Size(size.width - 2 * g, h), androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2), style = Stroke(w))
            } else {
                drawRect(ink, Offset(-g - w / 2, -g - w / 2), Size(size.width + 2 * g + w, size.height + 2 * g + w), style = Stroke(w))
            }
        }
    }
}

/* ------------------------------------------------------------------ 快捷键（播放器外） */

/**
 * 播放器外的快捷键（`docs/decisions.md` Q6、`docs/platforms.md` §2）：
 * 搜索 `Ctrl K` / `/`、设置 `Ctrl ,`、刷新 `F5`、收藏 `V`、标记已看 `W`、片库筛选 `Ctrl F`、首页 `← →` 翻封面、`Enter` 播放 / 打开、`Esc` 关菜单。
 * 输入框里字母键不触发（上游 `AppGraph.isTyping` + 这里的 [typing]）；`Esc` 没有菜单可关时不消费，交回根上的「返回」。
 *
 * 页面把自己的动作挂在这里（进页面挂、离开摘）；键从三处进来：页面层、玻璃排、和一个兜底的按键接收格（[Modifier.desktopKeys]）。
 */
object DesktopKeys {
    /** 首页：← → 翻封面（参数 −1 / +1）。返回 false = 这一刻不归它管（已经滚到目录区）。 */
    var onArrow: ((Int) -> Boolean)? = null

    /** Enter：首页 = 播放这一期；海报墙 = 打开悬停的那一张。 */
    var onEnter: (() -> Boolean)? = null

    /** `Ctrl F`：海报墙的筛选条。 */
    var onFilter: (() -> Boolean)? = null

    /** Esc：页面自己还有要先收的东西（筛选条、下拉、搜索框里的字）。返回 false = 没有，交回「返回」。 */
    var onEscape: (() -> Boolean)? = null

    /** `V` / `W` 作用在谁身上：鼠标悬停的那张卡；没有就是页面给的（首页 = 这一期）。 */
    var hovered: Title? by mutableStateOf(null)
    var pageTarget: Title? by mutableStateOf(null)

    /** 我们自己的输入框（搜索）有焦点。 */
    var typing: Boolean by mutableStateOf(false)

    val target: Title? get() = hovered ?: pageTarget

    fun handle(e: KeyEvent, graph: AppGraph, nav: WideNav, chrome: PageChrome, modifier: ModifierGlyph = ModifierGlyph.Ctrl, idle: com.gugaart.ui.idle.IdleClock? = null): Boolean {
        // Esc（和根上的「返回」一样在抬起时处理）：先关我们的菜单，再收页面自己的面板（[onEscape]）；都没有就不消费，交回根上的「返回」
        if (e.key == Key.Escape) {
            if (e.type != KeyEventType.KeyUp) return false
            val open = DesktopContextMenu.target != null || DesktopAccountMenu.open || DesktopAccountMenu.switching || DesktopAccountMenu.leaving
            if (open) {
                DesktopContextMenu.close()
                DesktopAccountMenu.closeAll()
                return true
            }
            return onEscape?.invoke() ?: false
        }
        if (e.type != KeyEventType.KeyDown) return false
        val ctrl = e.isCtrlPressed || e.isMetaPressed
        if (e.isAltPressed) return false
        if (ctrl) {
            val c = when (e.key) {
                Key.K -> DesktopCommand.Search
                Key.Comma -> DesktopCommand.Settings
                Key.F -> DesktopCommand.Filter
                else -> null
            }
            if (c != null) {
                // 菜单快捷键可能已经接住这一次 ⌘ 按键：刚执行过就消费掉、不重复执行（一次按键只执行一次）
                if (!DesktopCommandSink.claimOnce(c)) return true
                return DesktopCommands.run(c, graph, nav, chrome, idle)
            }
            // macOS 惯例键只在 modifier == Cmd 时认（P-mac-1 已定）；Windows 不变
            if (modifier == ModifierGlyph.Cmd && e.isMetaPressed) {
                val c2 = when (e.key) {
                    Key.R -> DesktopCommand.Reprint
                    Key.LeftBracket -> DesktopCommand.Back
                    Key.One -> DesktopCommand.GoHome
                    Key.Two -> DesktopCommand.GoDiscover
                    Key.Three -> DesktopCommand.GoLibs
                    Key.Four -> DesktopCommand.GoColls
                    Key.Five -> DesktopCommand.GoMe
                    else -> null
                }
                if (c2 != null) {
                    if (!DesktopCommandSink.claimOnce(c2)) return true
                    return DesktopCommands.run(c2, graph, nav, chrome, idle)
                }
            }
            return false
        }
        if (e.key == Key.F5) return DesktopCommands.run(DesktopCommand.Reprint, graph, nav, chrome, idle)
        if (typing || graph.isTyping) return false
        return when (e.key) {
            Key.Slash -> DesktopCommands.run(DesktopCommand.Search, graph, nav, chrome, idle)
            Key.V -> DesktopCommands.run(DesktopCommand.Favorite, graph, nav, chrome, idle)
            Key.W -> DesktopCommands.run(DesktopCommand.Played, graph, nav, chrome, idle)
            Key.DirectionLeft -> onArrow?.invoke(-1) ?: false
            Key.DirectionRight -> onArrow?.invoke(1) ?: false
            Key.Enter, Key.NumPadEnter -> onEnter?.invoke() ?: false
            else -> false
        }
    }

    fun openSearch(graph: AppGraph) {
        if (graph.nav.route !is Route.Search) graph.nav.navigate(Route.Search())
        graph.requestSearchFocus()
    }
}

/** 把 [DesktopKeys] 接到这个节点上（焦点在它里面时，键冒泡到这里）。 */
fun Modifier.desktopKeys(): Modifier = composed {
    val graph = LocalGraph.current
    val nav = LocalWideNav.current
    val chrome = LocalChrome.current
    val host = LocalDesktopHost.current
    val idle = com.gugaart.ui.idle.LocalIdleClock.current
    onKeyEvent { DesktopKeys.handle(it, graph, nav, chrome, host.modifier, idle) }
}

/* ------------------------------------------------------------------ 纸面小件 */

/** 键帽提示：`Ctrl K`、`← →`、`Enter`（Georgia 斜体小字 + 细线框）。 */
@Composable
fun Kbd(text: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    BasicText(
        text,
        modifier.border(1.dp, colors.line).padding(horizontal = Guga.rem(0.6f), vertical = Guga.rem(0.1f)),
        style = Guga.numeral(rsp(1.1f), colors.faint, letterSpacing = 0.04.em),
        maxLines = 1,
    )
}

/**
 * 区块标题（`.hd`）：斜体编号 + 宋体 26 + 专色斜体英文 + 通栏细线 + 右端的东西（小计 / 页签 / 「全部 N 部 →」）。到内容 1.6 rem。
 */
@Composable
fun DeskSection(number: String?, title: String, english: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = Guga.colors
    Row(modifier.fillMaxWidth().padding(bottom = Guga.rem(DeskSpec.HEAD_GAP)).testTag("desk-section"), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f))) {
        if (number != null) BasicText(number, Modifier.alignByBaseline(), style = Guga.numeral(rsp(1.3f), colors.spot, letterSpacing = 0.06.em), maxLines = 1)
        BasicText(title, Modifier.alignByBaseline(), style = Guga.song(rsp(2.6f), FontWeight.Black, letterSpacing = 0.12.em), maxLines = 1)
        BasicText(english, Modifier.alignByBaseline(), style = Guga.numeral(rsp(1.15f), colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        Box(Modifier.weight(1f).align(Alignment.CenterVertically).height(1.dp).background(colors.line))
        trailing()
    }
}

/** 区块标题右端的斜体小计（「12 部在看」）；[onClick] 给了就是链接（悬停变亮）。 */
@Composable
fun RowScope.SectionNote(text: String, onClick: (() -> Unit)? = null) {
    val colors = Guga.colors
    val hover = rememberHover()
    val press = rememberPressState()
    BasicText(
        text,
        Modifier.alignByBaseline().then(
            if (onClick != null) Modifier.hover(hover).deskFocusRing(press).pressGraphics(press).pressable(press, label = text, onClick = onClick) else Modifier,
        ),
        style = Guga.note(rsp(1.25f), if (hover.over) colors.ink else colors.faint),
        maxLines = 1,
    )
}

/** 主按钮（`.btn`）：米白实底、墨字、直角。悬停 ×1.03，按下再放大（上游）。[small] = `.btn.sm`。 */
@Composable
fun DeskButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GuguIcon? = GuguIcon.Play, small: Boolean = false, outline: Boolean = false, danger: Boolean = false, note: String? = null) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val ink = if (outline) colors.ink else if (danger) Color.White else colors.onInk
    Row(
        modifier
            .hover(hover)
            .hoverLift(hover)
            .deskFocusRing(press)
            .pressGraphics(press)
            .height(Guga.rem(if (small) 3.8f else 4.8f))
            .then(if (outline) Modifier.border(1.dp, colors.faint) else Modifier.background(if (danger) colors.seal else colors.ink))
            .pressable(press, label = text, onClick = onClick)
            .padding(start = Guga.rem(if (icon == null) (if (small) 1.8f else 2.4f) else if (small) 1.5f else 2f), end = Guga.rem(if (small) 1.8f else 2.4f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f)),
    ) {
        if (icon != null) Icon(icon, ink, Guga.rem(if (small) 1.3f else 1.5f), filled = icon == GuguIcon.Play)
        BasicText(text, style = Guga.song(rsp(if (small) 1.4f else 1.55f), FontWeight.Bold, ink, letterSpacing = 0.06.em), maxLines = 1)
        if (note != null) BasicText(note, style = Guga.numeral(rsp(1.25f), ink.copy(alpha = 0.7f)), maxLines = 1)
    }
}

/** 文字链接（`.lnk`）：宋体 600 + 一条贴字的下划线；悬停 / [active] 变专色。 */
@Composable
fun DeskLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, icon: GuguIcon? = null) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val ink = if (hover.over || active) colors.spot else colors.ink
    Row(
        modifier
            .hover(hover)
            .deskFocusRing(press)
            .pressGraphics(press)
            .pressable(press, label = text, onClick = onClick)
            .drawBehind { drawLine(ink, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(bottom = Guga.rem(0.3f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.6f)),
    ) {
        if (icon != null) Icon(icon, ink, Guga.rem(1.4f))
        BasicText(text, style = Guga.song(rsp(1.4f), FontWeight.SemiBold, ink), maxLines = 1)
    }
}

/** 线框小按钮（工具条的「筛选」「排序」、筛选的选项、已选的小标签）：[solid] = 米白实底墨字。 */
@Composable
fun DeskChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, solid: Boolean = false, strong: Boolean = false, icon: GuguIcon? = null, note: String? = null, trailingIcon: GuguIcon? = null, height: Float = 3f, tag: String = "desk-chip") {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val ink = if (solid) colors.onInk else colors.ink
    Row(
        modifier
            .hover(hover)
            .hoverLift(hover)
            .deskFocusRing(press)
            .pressGraphics(press)
            .height(Guga.rem(height))
            .then(if (solid) Modifier.background(colors.ink) else Modifier.border(1.dp, if (strong || hover.over) colors.ink else colors.line))
            .pressable(press, label = text, onClick = onClick)
            .padding(horizontal = Guga.rem(1.2f))
            .testTag(if (solid) "$tag-on" else tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f)),
    ) {
        if (icon != null) Icon(icon, ink, Guga.rem(1.3f))
        BasicText(text, style = Guga.song(rsp(1.3f), FontWeight.SemiBold, ink), maxLines = 1)
        if (note != null) BasicText(note, style = Guga.note(rsp(1.25f), ink.copy(alpha = 0.55f)), maxLines = 1)
        if (trailingIcon != null) Icon(trailingIcon, ink, Guga.rem(1.2f))
    }
}

/* ------------------------------------------------------------------ 悬停海报卡 */

/**
 * 电脑的海报卡（`.pc`）。悬停：海报朝鼠标倾斜 + 一道高光 + **这张海报自己颜色**的投影（下沿一片渐隐的色晕，不是模糊）；
 * 片名下面那一行换成快捷操作（播放 / 印章位 / 校对格 / 更多）——**操作不压在海报上**。右键 = 右键菜单。
 *
 * [number]：行里的编号（「01」），海报墙不带。[meta]：片名下的斜体小字。[wrap]：片名可折两行（海报墙）。[title] = null：加载骨架。
 * [forceHover]：测试 / 截图用（离屏渲染里也可以真的发鼠标事件，这个只为省事）。
 */
@Composable
fun DeskPosterCard(
    title: Title?,
    width: Dp,
    modifier: Modifier = Modifier,
    number: String? = null,
    meta: String? = title?.let { defaultMeta(it) },
    wrap: Boolean = false,
    highlight: IntRange? = null,
    forceHover: Boolean = false,
    /** 测试 / 截图用：直接画出键盘焦点环（设计稿 `home-focus`）。 */
    forceFocus: Boolean = false,
    onOpen: (Title) -> Unit = {},
) {
    val colors = Guga.colors
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val press = rememberPressState()
    val hover = rememberHover()
    val hot = title != null && (hover.over || forceHover)
    val reduced = LocalMotionPrefs.current.reducedMotion
    val at = if (forceHover && !hover.over) Offset(0.9f, -0.8f) else hover.at
    val tiltY by animateFloatAsState(if (hot && !reduced) at.x * DeskSpec.TILT_Y else 0f, spring(0.8f, 300f), label = "tiltY")
    val tiltX by animateFloatAsState(if (hot && !reduced) -at.y * DeskSpec.TILT_X else 0f, spring(0.8f, 300f), label = "tiltX")
    val glow by animateFloatAsState(if (hot) 1f else 0f, label = "glow")
    // 这张海报自己的颜色（只在悬停时取；取不到 = 当前专色）
    val bmp = if (title != null && hot) rememberImage(title.art.poster, keepPrevious = true) else null
    val own = remember(bmp) { bmp?.spot() } ?: colors.spot
    if (title != null) {
        DisposableEffect(hot, title.id) {
            if (hot && !forceHover) DesktopKeys.hovered = title
            onDispose { if (DesktopKeys.hovered?.id == title.id) DesktopKeys.hovered = null }
        }
    }
    val density = LocalDensity.current
    Column(
        modifier
            .width(width)
            .then(
                if (title == null) Modifier else Modifier
                    .hover(hover)
                    .onRightClick { DesktopContextMenu.open(title, it) },
            )
            .testTag(if (hot) "desk-card-hover" else "desk-card"),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .drawBehind {
                    // 专色投影：贴着海报下沿的一片色晕（`0 2.6rem 4rem -1.8rem var(--spot)`）
                    if (glow > 0f) {
                        val h = size.height * 0.16f
                        drawRect(
                            Brush.verticalGradient(listOf(own.copy(alpha = 0.55f * glow), Color.Transparent), size.height - h * 0.2f, size.height + h),
                            Offset(size.width * 0.06f, size.height - h * 0.2f),
                            Size(size.width * 0.88f, h * 1.2f),
                        )
                    }
                }
                .deskFocusRing(press, forceFocus)
                .then(if (title != null) Modifier.pressable(press, label = title.name) { onOpen(title) } else Modifier)
                .pressGraphics(press)
                .graphicsLayer {
                    cameraDistance = 18f * density.density
                    rotationY = tiltY
                    rotationX = tiltX
                }
                .border(1.dp, colors.ink.copy(alpha = 0.2f + 0.3f * glow))
                .drawWithContent {
                    drawContent()
                    // 高光：一道斜的亮带，鼠标在哪边哪边亮
                    if (glow > 0f) {
                        val x = size.width * (0.5f + at.x * 0.5f)
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.22f * glow), Color.Transparent, Color.Transparent, Color.White.copy(alpha = 0.07f * glow)),
                                Offset(x - size.width * 0.7f, 0f),
                                Offset(x + size.width * 0.5f, size.height * 0.7f),
                            ),
                        )
                    }
                },
        ) {
            if (title == null) {
                Skeleton(Modifier.fillMaxSize())
            } else {
                CoverImage(title.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) {
                    HatchPlate(Rules.monogram(title.name, person = title.kind == Kind.Person), Modifier.fillMaxSize(), caption = title.name.takeIf { title.kind != Kind.Person })
                }
                if (title.progress > 0f) Box(Modifier.matchParentSize().progressLine(title.progress, colors.spot, thickness = Guga.rem(0.3f), outside = false))
            }
        }
        if (title == null) {
            Skeleton(Modifier.padding(top = Guga.rem(1.2f)).fillMaxWidth(0.8f).height(Guga.rem(1.5f)))
            Skeleton(Modifier.padding(top = Guga.rem(0.7f)).fillMaxWidth(0.5f).height(Guga.rem(1.1f)))
            return@Column
        }
        val favId = title.id
        val fav = chrome.isFavorite(favId, title.favorite)
        val seen = chrome.isPlayed(title.id, title.played)
        Row(Modifier.padding(top = Guga.rem(1f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f))) {
            if (number != null) BasicText(number, style = Guga.numeral(rsp(2.2f), colors.faint), maxLines = 1)
            Column(Modifier.weight(1f)) {
                val namePress = rememberPressState()
                BasicText(
                    highlighted(title.name, highlight, colors.spot),
                    Modifier.pressable(namePress, label = null, focusable = false) { onOpen(title) },
                    style = Guga.song(rsp(1.5f), FontWeight.Bold, if (seen) colors.dim else colors.ink, lineHeight = rsp(2.1f)),
                    maxLines = if (wrap) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 这一行高度固定：悬停时换成快捷操作，版面不跳
                Box(Modifier.height(Guga.rem(2.2f)).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    if (hot) {
                        Row(Modifier.testTag("desk-card-actions"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f))) {
                            QuickAction("播放", GuguIcon.Play) { if (title.kind == Kind.Movie || title.kind == Kind.Episode) graph.play(title.id, title.positionSec?.times(1000)) else graph.open(title.id) }
                            val sealPress = rememberPressState()
                            SealSlot(fav, Modifier.pressGraphics(sealPress).deskFocusRing(sealPress).pressable(sealPress, label = if (fav) "取消收藏" else "收藏") { graph.toggleFavorite(chrome, favId, fav) }, size = Guga.rem(2f))
                            val tickPress = rememberPressState()
                            Box(Modifier.size(Guga.rem(2.2f)).pressGraphics(tickPress).pressable(tickPress, label = if (seen) "标成没看" else "标记已看") { graph.togglePlayed(chrome, title.id, seen) }, contentAlignment = Alignment.Center) {
                                ProofTick(seen, size = Guga.rem(1.3f))
                            }
                            val more = rememberPressState()
                            var pos by remember { mutableStateOf<LayoutCoordinates?>(null) }
                            Box(
                                Modifier.size(Guga.rem(2.2f)).onGloballyPositioned { pos = it }.pressGraphics(more).pressable(more, label = "更多") {
                                    DesktopContextMenu.open(title, pos?.takeIf { it.isAttached }?.localToRoot(Offset.Zero) ?: Offset.Zero)
                                },
                                contentAlignment = Alignment.Center,
                            ) { Icon(GuguIcon.Ellipsis, colors.ink, Guga.rem(1.5f)) }
                        }
                    } else if (!meta.isNullOrEmpty()) {
                        BasicText(meta, style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** 卡片副行的默认写法：「2025 · 剧情 · 科幻奇幻」。 */
fun defaultMeta(t: Title): String = (listOfNotNull(t.year?.toString()) + t.genres.take(2)).joinToString(" · ")

@Composable
private fun QuickAction(text: String, icon: GuguIcon, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val ink = if (hover.over) colors.spot else colors.ink
    Row(Modifier.hover(hover).pressGraphics(press).deskFocusRing(press).pressable(press, label = text, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.4f))) {
        Icon(icon, ink, Guga.rem(1.1f), filled = true)
        BasicText(text, style = Guga.song(rsp(1.2f), FontWeight.SemiBold, ink), maxLines = 1)
    }
}

/* ------------------------------------------------------------------ 目录行 */

/**
 * 电脑的目录行（`.li`）：斜体编号 + 片名 / 副行 + 右端 16:9 缩略图（下沿进度线）。悬停：片名变专色。右键 = 右键菜单（[menu]）。
 */
@Composable
fun DeskTocRow(number: String, name: String, sub: String, thumbUrl: String?, progress: Float, onClick: () -> Unit, modifier: Modifier = Modifier, menu: Title? = null, current: Boolean = false, highlight: IntRange? = null) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Row(
        modifier
            .fillMaxWidth()
            .hover(hover)
            .then(if (menu != null) Modifier.onRightClick { DesktopContextMenu.open(menu, it) } else Modifier)
            .drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .deskFocusRing(press)
            .pressGraphics(press)
            .pressable(press, label = name, onClick = onClick)
            .padding(vertical = Guga.rem(1.4f))
            .testTag(if (hover.over) "desk-toc-hover" else "desk-toc"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(number, Modifier.width(Guga.rem(4.6f)), style = Guga.numeral(rsp(2.6f), colors.faint), maxLines = 1)
        Spacer(Modifier.width(Guga.rem(1.6f)))
        Column(Modifier.weight(1f)) {
            BasicText(highlighted(name, highlight, colors.spot), style = Guga.song(rsp(1.65f), FontWeight.Bold, if (hover.over || current) colors.spot else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(sub, Modifier.padding(top = Guga.rem(0.3f)), style = Guga.note(rsp(1.25f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Guga.rem(1.6f)))
        Box(Modifier.width(Guga.rem(DeskSpec.TOC_THUMB)).aspectRatio(16f / 9f).border(1.dp, colors.line).progressLine(progress, colors.spot, thickness = Guga.rem(0.3f), outside = false)) {
            CoverImage(thumbUrl, Modifier.fillMaxSize(), Framing(0.5f, 0.35f)) { HatchPlate(Rules.monogram(name, person = false), Modifier.fillMaxSize(), textSize = rsp(1.5f)) }
        }
    }
}
