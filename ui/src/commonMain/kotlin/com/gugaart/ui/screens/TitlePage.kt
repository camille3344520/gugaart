package com.gugaart.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import com.gugaart.ui.components.BoxButton
import com.gugaart.ui.components.Rule
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/**
 * 测试 / 截图的预置状态（和 [ScreenPreset] 同一个用途，给这一批「标题页」用）：一开始在哪个页签、哪一个选择器推开着。
 * 形如 `tab=movies`、`pick=engine`，多个用 `;` 隔开。应用里永远是 null。
 */
val LocalPageState = staticCompositionLocalOf<String?> { null }

/** 从 [LocalPageState] 的字串里取一个键的值（`tab=un;pick=engine` → `un`）。纯函数。 */
fun pageStateOf(raw: String?, key: String): String? =
    raw?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("$key=") }?.substringAfter('=')?.takeIf { it.isNotEmpty() }

/** 标题页顶栏高度（状态栏之下）。 */
val TITLE_BAR = 50.dp

/**
 * 「标题页」的共同版式（设置、记录、收藏、合集列表；设计稿 `p=settings` / `hist` / `favs` / `sets`）：
 * 顶栏（**返回在左上**、页名居中、右上一个选项按钮）→ 栏目行（专色）→ 宋体大标题 → 主线 → 页签 / 内容。
 * 顶栏固定、实心纸色；下面整页滚动。[barTitle]：顶栏正中的小页名（进了下一级才写，例如设置的分区页写「设置」）。
 */
@Composable
fun TitlePage(
    kicker: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    barTitle: String = "",
    action: GuguIcon? = null,
    actionLabel: String = "",
    onAction: (() -> Unit)? = null,
    scroll: ScrollState = rememberPresetScroll(),
    tag: String = "title-page",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    Box(modifier.fillMaxSize().testTag(tag)) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(dimens.statusBar + TITLE_BAR + 6.dp))
            Column(Modifier.padding(horizontal = dimens.margin)) {
                Rule()
                BasicText(kicker, Modifier.padding(top = 20.dp), style = Guga.kicker(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 大标题：字多了降一号，不裁字（「关于与检查更新」7 个字）
                val size = when {
                    title.length <= 4 -> type.display * 0.86f
                    title.length <= 6 -> type.display * 0.68f
                    else -> type.display * 0.56f
                }
                BasicText(title, Modifier.padding(top = 12.dp, bottom = 18.dp), style = Guga.song(size, FontWeight.Black, letterSpacing = 0.08.em, lineHeight = size * 1.2f), maxLines = 2)
            }
            content()
            Spacer(Modifier.height(72.dp))
        }
        // 顶栏：实心纸色，内容从它下面硬切过去
        Box(Modifier.fillMaxWidth().background(colors.paper)) {
            Row(Modifier.padding(top = dimens.statusBar).fillMaxWidth().height(TITLE_BAR).padding(horizontal = dimens.margin - 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BoxButton(GuguIcon.ChevronLeft, "返回", onBack)
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    BasicText(barTitle, style = Guga.song(type.row, FontWeight.Black, letterSpacing = 0.2.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (action != null && onAction != null) BoxButton(action, actionLabel, onAction) else Spacer(Modifier.size(48.dp))
            }
        }
    }
}

/** 一个页签：名字 + 斜体小计数。 */
data class PageTab(val id: String, val label: String, val count: Int? = null)

/**
 * 页签一行（「全部 12　剧集 9　电影 3 ……… 清空」）：选中 = 专色加粗；右端一个斜体的小动作 / 小计。上面是主线，下面是细线。
 * [scrollable]：页签多（设置的 9 个分区）时横向滚动，选中项不裁。
 */
@Composable
fun TabsRow(
    tabs: List<PageTab>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailing: (() -> Unit)? = null,
    scrollable: Boolean = false,
    initialScroll: Int = 0,
) {
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    Column(modifier.fillMaxWidth()) {
        Rule(Modifier.padding(horizontal = dimens.margin), strong = true)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).then(if (scrollable) Modifier.horizontalScroll(rememberScrollState(initialScroll)) else Modifier).padding(horizontal = dimens.margin).testTag("page-tabs"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (scrollable) 22.dp else 18.dp),
        ) {
            for (t in tabs) {
                val on = t.id == selected
                val press = rememberPressState()
                Row(
                    Modifier.pressGraphics(press).heightIn(min = 44.dp).pressable(press, label = t.label, onClick = { onSelect(t.id) }).testTag(if (on) "page-tab-selected" else "page-tab"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(t.label, style = Guga.song(type.row * 0.98f, if (on) FontWeight.Black else FontWeight.Normal, if (on) colors.spot else colors.dim), maxLines = 1)
                    if (t.count != null) BasicText(" ${t.count}", Modifier.padding(top = 5.dp), style = Guga.numeral(type.caption, if (on) colors.spot else colors.faint), maxLines = 1)
                }
            }
            if (trailing != null && !scrollable) {
                Spacer(Modifier.weight(1f))
                val press = rememberPressState()
                BasicText(
                    trailing,
                    Modifier.then(if (onTrailing != null) Modifier.pressGraphics(press).heightIn(min = 44.dp).pressable(press, label = trailing, onClick = onTrailing).padding(top = 12.dp) else Modifier),
                    style = Guga.note(type.caption * 1.1f, colors.dim),
                    maxLines = 1,
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = if (scrollable) 0.dp else dimens.margin).height(dimens.ruleHair).background(colors.line))
    }
}

/** 页尾：「— 播放 完 —」。一页到底了，不是还在加载。 */
@Composable
fun PageEnd(name: String, modifier: Modifier = Modifier) {
    BasicText("—  $name 完  —", modifier.fillMaxWidth().padding(top = 40.dp), style = Guga.note(Guga.type.caption, Guga.colors.faint, letterSpacing = 0.2.em).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1)
}

/** 分组的斜体小标（记录页的「今天」「昨天」）。 */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(top = 26.dp, bottom = 6.dp), style = Guga.note(Guga.type.caption * 1.05f, Guga.colors.faint, letterSpacing = 0.2.em), maxLines = 1)
}

/** 一条左侧的竖线（推开的选择器、号外的引文）。 */
fun Modifier.leftRule(color: androidx.compose.ui.graphics.Color, width: androidx.compose.ui.unit.Dp = 2.dp): Modifier = drawBehind {
    drawLine(color, Offset(width.toPx() / 2, 0f), Offset(width.toPx() / 2, size.height), width.toPx())
}

/**
 * 这一页的纵向滚动。`ScreenPreset.scroll`（只有测试 / 截图会给）要等内容排出来才滚得过去：数据和图片是异步到的，
 * 一开始页面还没有那么长，初始值会被夹回 0——所以页面每长一次就再滚一次，直到滚到预置的位置。应用里预置是 0，这里什么都不做。
 */
@Composable
fun rememberPresetScroll(): ScrollState {
    val target = LocalPreset.current.scroll
    val scroll = rememberScrollState(target)
    if (target > 0) {
        androidx.compose.runtime.LaunchedEffect(scroll.maxValue) {
            val to = minOf(target, scroll.maxValue)
            if (scroll.value < to) scroll.scrollTo(to)
        }
    }
    return scroll
}
