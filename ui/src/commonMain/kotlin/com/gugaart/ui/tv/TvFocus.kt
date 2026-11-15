package com.gugaart.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.gugaart.ui.components.progressLine
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.focus.focusKey
import com.gugugaga.ds.focus.routeDefault
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.LocalMotionPrefs
import com.gugugaga.ds.physics.PressState
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/**
 * 电视的焦点样式（`docs/platforms.md` §3，设计稿 `tv/css/tv.css` 的 `.fc`）：一屏里只有焦点是米白实底的，3 米外一眼找得到——
 * **米白实底（或米白 4 px 框）+ 四角朱红校对角 + 略微抬起**（卡片 ×1.08，按钮 ×1.04）。焦点色是固定的朱红 + 米白，**不跟专色**；焦点环常驻。
 *
 * 焦点的移动、记忆、返回栈是上游的（`com.gugugaga.ds.focus`、`pressable` 的 `PressState.focused`）；这里只画样子。
 */
object TvFocusSpec {
    const val CARD_LIFT = 1.08f
    const val BUTTON_LIFT = 1.04f

    /** 米白框的粗细（画在卡片外面，不吃图）。 */
    val FRAME = 4.dp

    /** 校对角（卡片、按钮、列表行）：臂长 26、粗 4、离焦点块 15。 */
    val CORNER_ARM = 26.dp
    val CORNER_STROKE = 4.dp
    val CORNER_GAP = 15.dp

    /** 小号的校对角（字母键、筛选格、选项）：臂长 18、粗 3、离焦点块 10。 */
    val CORNER_ARM_SMALL = 18.dp
    val CORNER_STROKE_SMALL = 3.dp
    val CORNER_GAP_SMALL = 10.dp
    const val LIFT_MS = 200

    /** 按钮：高 76、左右 32、线框 2 px 米白 50 %、底 = 纸色 50 %（压在整幅图上也读得清）。 */
    val BUTTON_HEIGHT = 76.dp
    val BUTTON_PAD = 32.dp
    const val BUTTON_BORDER_ALPHA = 0.5f
    const val BUTTON_FILL_ALPHA = 0.5f
}

/** 焦点块怎么画。 */
enum class TvFocusKind {
    /** 图片卡：米白 4 px 框（画在外面）+ 校对角 + 抬起 ×1.08（片名由灰变白由调用方按 focused 做）。 */
    Card,

    /** 按钮：整块米白实底、墨字 + 校对角 + 抬起 ×1.04。 */
    Block,

    /** 米白纸条上的菜单（长按菜单、更新号外）：反过来，墨底米白字 + 校对角（设计稿截图 `menu.png`），不抬起。 */
    OnPaper,

    /** 玻璃上的焦点（侧栏展开、跳过片头）：玻璃里一张米白纸条、墨字，不抬起、不画校对角。 */
    OnGlass,

    /** 列表行（设置、媒体库列表、搜索结果）：整行米白实底、墨字 + 校对角，不抬起。 */
    Row,

    /** 字母键、筛选格、选项：米白实底、墨字 + 小号校对角，不抬起。 */
    Chip,
}

/** 四角的朱红校对角（画在内容外面 [gap] 处，不占布局）。 */
fun Modifier.proofCorners(on: Boolean, color: Color, arm: Dp = TvFocusSpec.CORNER_ARM, stroke: Dp = TvFocusSpec.CORNER_STROKE, gap: Dp = TvFocusSpec.CORNER_GAP): Modifier =
    if (!on) this else drawWithContent {
        drawContent()
        val g = gap.toPx()
        val a = arm.toPx()
        val w = stroke.toPx()
        val l = -g + w / 2
        val t = -g + w / 2
        val r = size.width + g - w / 2
        val b = size.height + g - w / 2
        // 左上、右上、左下、右下：每个角两笔
        drawLine(color, Offset(l - w / 2, t), Offset(l - w / 2 + a, t), w); drawLine(color, Offset(l, t - w / 2), Offset(l, t - w / 2 + a), w)
        drawLine(color, Offset(r + w / 2, t), Offset(r + w / 2 - a, t), w); drawLine(color, Offset(r, t - w / 2), Offset(r, t - w / 2 + a), w)
        drawLine(color, Offset(l - w / 2, b), Offset(l - w / 2 + a, b), w); drawLine(color, Offset(l, b + w / 2), Offset(l, b + w / 2 - a), w)
        drawLine(color, Offset(r + w / 2, b), Offset(r + w / 2 - a, b), w); drawLine(color, Offset(r, b + w / 2), Offset(r, b + w / 2 - a), w)
    }

/** 图片卡的焦点框：4 px 米白，画在卡片**外面**（不盖图）。 */
fun Modifier.focusFrame(on: Boolean, color: Color, width: Dp = TvFocusSpec.FRAME): Modifier =
    if (!on) this else drawWithContent {
        drawContent()
        val w = width.toPx()
        drawRect(color, Offset(-w / 2, -w / 2), Size(size.width + w, size.height + w), style = Stroke(w))
    }

/** 图片卡的焦点：4 px 米白框 + 四角朱红校对角，画在图的外面。给 `TvFocusable(frame = false)` 的卡片用在图那一格上。 */
@Composable
fun Modifier.tvCardFrame(focused: Boolean): Modifier = focusFrame(focused, Guga.colors.ink).proofCorners(focused, Guga.colors.seal)

/**
 * 一个能接焦点的电视元素。内容拿到 `focused`，自己决定字的颜色（米白实底上用 `colors.onInk`）。
 * - [key]：上游 `focusKey`（区内唯一；路由记忆、测试用它找元素）。[default] = 这一页的默认焦点（上游 `routeDefault`）。
 * - 确定 = [onClick]；长按确定 = [onLongClick]（上游 `pressable` 已处理按住确定键）；菜单键在没有页面级处理时也走 [onLongClick]。
 * - [origin]：抬起的缩放原点（横排里靠左的卡用 `TransformOrigin(0f, 0.4f)`，抬起后不出安全边）。
 * - [onFocus]：拿到焦点时调（焦点预览、右页跟着换）。
 *
 * 抬起的缩放画在里面一层：接焦点的节点自己不缩放，「把焦点卡滚到左起第一格」的计算不受抬起动画影响。
 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: TvFocusKind = TvFocusKind.Block,
    key: String? = null,
    default: Boolean = false,
    label: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    press: PressState = rememberPressState(),
    contentAlignment: Alignment = Alignment.TopStart,
    origin: TransformOrigin = TransformOrigin.Center,
    onFocus: (() -> Unit)? = null,
    /** false = 不在整块外面画焦点框 / 校对角：卡片自己把它们画在图上（[tvCardFrame]），片名在框外面。 */
    frame: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val colors = Guga.colors
    val focused = press.focused
    val reduced = LocalMotionPrefs.current.reducedMotion
    val tv = LocalWideNav.current.tv
    val lift = when (kind) {
        TvFocusKind.Card -> TvFocusSpec.CARD_LIFT
        TvFocusKind.Block -> TvFocusSpec.BUTTON_LIFT
        else -> 1f
    }
    val scale by animateFloatAsState(if (focused) lift else 1f, tween(if (reduced) 0 else TvFocusSpec.LIFT_MS), label = "tv-lift")
    val small = kind == TvFocusKind.Chip
    val corners = kind != TvFocusKind.OnGlass
    // 菜单键（没有页面级的处理时）= 焦点项的长按菜单；拿到焦点时把自己的交给外壳
    val longClick by rememberUpdatedState(onLongClick)
    val focusCb by rememberUpdatedState(onFocus)
    DisposableEffect(focused) {
        if (focused) {
            tv.focusedLongClick = { longClick?.invoke() }
            tv.focusedPicture = null
            focusCb?.invoke()
        }
        onDispose { }
    }
    Box(
        modifier
            .zIndex(if (focused) 3f else 0f)
            .focusKey(key)
            .routeDefault(default)
            .pressable(press, enabled = enabled, label = label, onLongClick = onLongClick, onClick = onClick)
            .testTag(if (focused) "tv-focused" else "tv-focusable"),
        propagateMinConstraints = true,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = origin
                }
                .proofCorners(
                    focused && corners && frame,
                    colors.seal,
                    if (small) TvFocusSpec.CORNER_ARM_SMALL else TvFocusSpec.CORNER_ARM,
                    if (small) TvFocusSpec.CORNER_STROKE_SMALL else TvFocusSpec.CORNER_STROKE,
                    if (small) TvFocusSpec.CORNER_GAP_SMALL else TvFocusSpec.CORNER_GAP,
                )
                .then(
                    when {
                        !focused || !frame -> Modifier
                        kind == TvFocusKind.Card -> Modifier.focusFrame(true, colors.ink)
                        kind == TvFocusKind.OnPaper -> Modifier.background(colors.onInk)
                        else -> Modifier.background(colors.ink)
                    },
                ),
            contentAlignment = contentAlignment,
        ) { content(focused) }
    }
}

/**
 * 电视按钮：默认是**米白线框**（2 px、50 %，垫纸色 50 %），焦点才变米白实底 + 校对角 ×1.04（「▶ 续看 第 5 集　余 19 分」的小字在按钮里）。
 * [slot]：按钮里靠右的一格（收藏按钮里的印章位）。[progress] > 0：按钮下沿外面一条进度线（专色）。
 */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: GuguIcon? = null,
    trailing: String? = null,
    key: String? = null,
    default: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    slot: (@Composable (focused: Boolean) -> Unit)? = null,
    progress: Float = 0f,
    padding: PaddingValues = PaddingValues(horizontal = TvFocusSpec.BUTTON_PAD),
    onFocus: (() -> Unit)? = null,
) {
    val colors = Guga.colors
    val press = rememberPressState()
    TvFocusable(
        onClick,
        modifier.then(if (progress > 0f) Modifier.progressLine(progress, colors.spot, colors.line, 5.dp, outside = true) else Modifier),
        TvFocusKind.Block, key, default, text, onLongClick = onLongClick, press = press, onFocus = onFocus,
    ) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(
            Modifier
                .then(if (focused) Modifier else Modifier.background(colors.paper.copy(alpha = TvFocusSpec.BUTTON_FILL_ALPHA)).border(Guga.dimens.ruleStrong, colors.ink.copy(alpha = TvFocusSpec.BUTTON_BORDER_ALPHA)))
                .height(TvFocusSpec.BUTTON_HEIGHT)
                .padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (icon != null) Icon(icon, if (focused) colors.onInk else if (icon == GuguIcon.Play) colors.spot else colors.ink, 28.dp, filled = icon == GuguIcon.Play)
            BasicText(text, style = Guga.song(Guga.type.lead, FontWeight.Bold, ink, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) BasicText(trailing, style = Guga.note(23.sp, ink.copy(alpha = if (focused) 0.66f else 0.7f)), maxLines = 1)
            slot?.invoke(focused)
        }
    }
}

/**
 * 返回键的一层（上游 `BackDispatcher`）：[enabled] 时把 [onBack] 压进返回栈，最后压进去的先收（菜单 → 面板 → 下一屏回上一屏 → 上一页）。
 * 面板 / 菜单 / 「第二屏」开着的时候各放一个。
 */
@Composable
fun TvBack(enabled: Boolean = true, onBack: () -> Unit) {
    val back = LocalBackDispatcher.current
    val cb by rememberUpdatedState(onBack)
    DisposableEffect(back, enabled) {
        val off = if (enabled) back.push { cb(); true } else null
        onDispose { off?.invoke() }
    }
}

/**
 * 菜单键 = 这一页的选项（片库是筛选 / 排序）。页面在的时候挂上；没有页面挂的时候，菜单键 = 焦点项的长按菜单。
 * [onMenu] 返回 false = 这次不处理（交给焦点项）。
 */
@Composable
fun TvMenuKey(enabled: Boolean = true, onMenu: () -> Boolean) {
    val tv = LocalWideNav.current.tv
    val cb by rememberUpdatedState(onMenu)
    DisposableEffect(tv, enabled) {
        val h: () -> Boolean = { cb() }
        if (enabled) tv.onMenu = h
        onDispose { if (tv.onMenu === h) tv.onMenu = null }
    }
}

/** 一层盖在页面上的东西开着（长按菜单、号外）：这期间一路按左不进侧栏。 */
@Composable
fun TvModal(active: Boolean = true) {
    val tv = LocalWideNav.current.tv
    DisposableEffect(tv, active) {
        if (active) tv.modals++
        onDispose { if (active) tv.modals-- }
    }
}
