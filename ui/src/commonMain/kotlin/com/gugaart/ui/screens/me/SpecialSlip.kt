package com.gugaart.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.SheetHost
import com.gugaart.ui.components.SlipHeader
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.launch

/** 特别称号纸片的页眉小字：「新拿到一个特别称号」/「你已经拿到 5 个特别称号」/「还没拿到」。纯函数，有单测。 */
fun specialHead(news: SpecialNews, owned: Set<String>): String = when {
    news.browse -> if (news.ids.all { it in owned }) "已经拿到" else "还没拿到"
    news.first && news.ids.size > 1 -> "你已经拿到 ${news.ids.size} 个特别称号"
    news.ids.size > 1 -> "新拿到 ${news.ids.size} 个特别称号"
    else -> "新拿到一个特别称号"
}

/**
 * 特别称号纸片（手机 / 电脑）：页眉「特别称号」+ 一共拿到几个；一两个 = 每个一行（盾 + 名字 + 怎么拿 + 一句话）；
 * 第一次检查一下拿到好几个 = 拿到的盾排成格子。自己点开看的（[SpecialNews.browse]）：这一个的盾（没拿到 = 灰）和拿法。
 */
@Composable
fun SpecialSlip(news: SpecialNews, onClose: () -> Unit = { RankBoard.special = null }) {
    val colors = Guga.colors
    val type = Guga.type
    val owned = RankBoard.owned
    SheetHost(onClose, tag = "special-slip") {
        SlipHeader("特别称号", "SPECIAL · 观影称号", "${owned.size.coerceAtLeast(news.owned)} / ${Specials.COUNT}")
        BasicText(specialHead(news, owned), Modifier.padding(top = 14.dp), style = Guga.song(type.caption, FontWeight.Bold, colors.spot), maxLines = 1)
        val list = news.ids.mapNotNull(Specials::of)
        if (list.size <= 2 || news.browse) {
            for (t in list) {
                val locked = news.browse && t.id !in owned
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    SpecialBadge(t.id, 92.dp, locked = locked)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        BasicText(t.title, Modifier.testTag("special-title"), style = Guga.song(30.sp, FontWeight.Black, lineHeight = 38.sp), maxLines = 1)
                        BasicText(t.how, Modifier.padding(top = 4.dp), style = Guga.song(type.caption, FontWeight.Bold, colors.dim), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (!locked) BasicText(t.line, Modifier.padding(top = 4.dp), style = Guga.song(type.caption, color = colors.faint), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        } else {
            // 一下拿到很多（第一次检查）：格子排密一点，最多三排，再多左右滑翻页，整张纸片还在一屏里
            val dense = list.size > 8
            SpecialGrid(list.map { it.id }.toSet(), Modifier.padding(top = 14.dp), columns = if (dense) 6 else 4, badge = if (dense) 44.dp else 56.dp, only = list.map { it.id }, nameSize = if (dense) 11.sp else 12.sp, rows = 3)
        }
        PrimaryButton(if (news.browse) "好" else "收下", onClose, Modifier.padding(top = 18.dp).testTag("special-ok"), icon = null)
    }
}

/**
 * 特别称号分成几页（用户 2026-11-15：「这个徽章界面上放不下，可以左右滑 分页，不要占太大界面」）：
 * 拿到的排前面（第一页先看到自己拿到的），其余按原来的顺序；每页 [perPage] 枚。纯函数，有单测。
 */
fun specialPages(owned: Set<String>, perPage: Int, ids: List<String> = Specials.ALL.map { it.id }): List<List<String>> =
    (ids.filter { it in owned } + ids.filter { it !in owned }).chunked(perPage.coerceAtLeast(1)).ifEmpty { listOf(emptyList()) }

/**
 * 特别称号的格子：每格一枚盾 + 名字；拿到的彩色、没拿到的灰。[onPick] 不为空 = 点一格看这一个的拿法。
 * [only] = 只排这几个（纸片上报拿到的），默认全部。
 * [rows] > 0 = 分页：每页 [rows] 排，左右滑翻页，下面一排页点（两头的小箭头给鼠标用）；每页高度一样（最后一页补空排），翻页不跳。
 */
@Composable
fun SpecialGrid(
    owned: Set<String>,
    modifier: Modifier = Modifier,
    columns: Int = 4,
    badge: Dp = 52.dp,
    only: List<String>? = null,
    nameSize: TextUnit = 12.sp,
    rows: Int = 0,
    onPick: ((String) -> Unit)? = null,
) {
    val ids = only ?: Specials.ALL.map { it.id }
    if (rows <= 0) {
        SpecialRows(ids, owned, columns, badge, nameSize, 0, onPick, modifier.fillMaxWidth().testTag("special-grid"))
        return
    }
    val pages = remember(owned, ids, rows, columns) { specialPages(owned, rows * columns, ids) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxWidth().testTag("special-grid")) {
        HorizontalPager(pager, Modifier.fillMaxWidth().testTag("special-pager"), pageSpacing = 16.dp, verticalAlignment = Alignment.Top) { p ->
            SpecialRows(pages.getOrElse(p) { emptyList() }, owned, columns, badge, nameSize, if (pages.size > 1) rows else 0, onPick, Modifier.fillMaxWidth())
        }
        if (pages.size > 1) {
            PageDots(pager.currentPage, pages.size, Modifier.padding(top = 10.dp).align(Alignment.CenterHorizontally)) { to ->
                scope.launch { pager.animateScrollToPage(to.coerceIn(0, pages.size - 1)) }
            }
        }
    }
}

/** 一页的格子：[ids] 排成 [columns] 列；[padRows] > 0 = 补到这么多排（空排透明占位，各页一样高）。 */
@Composable
private fun SpecialRows(
    ids: List<String>,
    owned: Set<String>,
    columns: Int,
    badge: Dp,
    nameSize: TextUnit,
    padRows: Int,
    onPick: ((String) -> Unit)?,
    modifier: Modifier,
) {
    val colors = Guga.colors
    val chunks = ids.chunked(columns)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in chunks) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (id in row) {
                    val t = Specials.of(id) ?: continue
                    val got = id in owned
                    val press = rememberPressState()
                    Column(
                        Modifier.weight(1f).then(if (onPick != null) Modifier.pressGraphics(press).pressable(press, label = "特别称号 ${t.title}" + if (got) "" else "（还没拿到）", onClick = { onPick(id) }) else Modifier),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SpecialBadge(id, badge, locked = !got)
                        BasicText(t.title, Modifier.padding(top = 4.dp), style = Guga.song(nameSize, FontWeight.Bold, if (got) colors.ink else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // 最后一页补空排：一枚看不见的盾 + 一行字的高度，和别的页一样高
        repeat((padRows - chunks.size).coerceAtLeast(0)) {
            Column(Modifier.alpha(0f).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                SpecialBadge(Specials.ALL.first().id, badge)
                BasicText(" ", Modifier.padding(top = 4.dp), style = Guga.song(nameSize, FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

/** 页点：当前页实心墨点，其余淡点；两头一对小箭头（鼠标点它翻页；手机上也能点）。 */
@Composable
private fun PageDots(current: Int, count: Int, modifier: Modifier = Modifier, onGo: (Int) -> Unit) {
    val colors = Guga.colors
    Row(modifier.testTag("special-dots"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PageArrow("‹", "上一页", current > 0) { onGo(current - 1) }
        repeat(count) { i ->
            Box(Modifier.size(if (i == current) 7.dp else 5.dp).background(if (i == current) colors.ink else colors.faint, CircleShape))
        }
        PageArrow("›", "下一页", current < count - 1) { onGo(current + 1) }
    }
}

@Composable
private fun PageArrow(glyph: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Box(
        Modifier.size(28.dp).alpha(if (enabled) 1f else 0.3f).pressGraphics(press).pressable(press, label = label, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(glyph, style = Guga.song(18.sp, FontWeight.Bold, colors.dim), maxLines = 1)
    }
}
