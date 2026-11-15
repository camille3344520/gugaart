package com.gugaart.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SearchIdleView
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/**
 * 词云（设计稿 `.b-cloud`）：词和词之间一道斜线，三档字号，一个印专色。点一个词 = 搜它。
 * [sizes] = 小 / 中 / 大三档字号。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchCloud(words: List<CloudWord>, onPick: (String) -> Unit, sizes: Triple<TextUnit, TextUnit, TextUnit>, modifier: Modifier = Modifier, gap: Dp = 10.dp) {
    val colors = Guga.colors
    FlowRow(modifier.fillMaxWidth().testTag("search-cloud"), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap * 0.6f), itemVerticalAlignment = Alignment.CenterVertically) {
        for ((i, w) in words.withIndex()) {
            if (i > 0) BasicText("/", style = Guga.song(sizes.first, color = colors.faint), maxLines = 1)
            val press = rememberPressState()
            val size = when (w.size) {
                CloudSize.S -> sizes.first
                CloudSize.M -> sizes.second
                CloudSize.L -> sizes.third
            }
            BasicText(
                w.text,
                Modifier.pressGraphics(press).pressable(press, label = w.text) { onPick(w.text) }.testTag("cloud-$i"),
                style = Guga.song(size, if (w.size == CloudSize.S) FontWeight.SemiBold else FontWeight.Black, if (w.accent) colors.spot else if (w.size == CloudSize.S) colors.dim else colors.ink),
                maxLines = 1,
            )
        }
    }
}

/**
 * 手机搜索页什么都没输入时（设计稿 `p=search` 默认态）：她举笔 →「找过」（编号 · 词 · 什么时候，右上「清空」）→ 词云「随便搜搜」→ 一行小字说能搜什么。
 * 没有搜过就不出「找过」；片库还没读到就不出词云。
 */
@Composable
fun PhoneSearchIdle(recents: List<RecentSearch>, cloud: List<CloudWord>, onPick: (String) -> Unit, onClear: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val dimens = Guga.dimens
    val now = remember { SearchMemory.nowMs() }
    val offset = remember(now) { SearchMemory.offsetMs(now) }
    Column(Modifier.fillMaxWidth().testTag("search-idle")) {
        SearchIdleView(seed = 1)
        if (recents.isNotEmpty()) {
            SectionHeader("找过", "RECENT", Modifier.padding(top = dimens.sectionGap), trailing = "清空", onTrailing = onClear)
            for ((i, r) in recents.take(6).withIndex()) {
                val press = rememberPressState()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).pressGraphics(press).pressable(press, label = r.term) { onPick(r.term) }.padding(vertical = 10.dp).testTag("search-recent-$i"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText("${i + 1}", Modifier.width(34.dp), style = Guga.numeral(type.row, colors.spot), maxLines = 1)
                    BasicText(r.term, Modifier.weight(1f).padding(end = 12.dp), style = Guga.song(type.row, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(SearchMemory.whenLabel(r.at, now, offset), style = Guga.note(type.caption, colors.faint), maxLines = 1)
                }
                Rule()
            }
        }
        if (cloud.isNotEmpty()) {
            SectionHeader("随便搜搜", "TRY", Modifier.padding(top = dimens.sectionGap))
            SearchCloud(cloud, onPick, Triple(type.caption * 1.05f, type.row * 1.15f, type.section * 1.05f), Modifier.padding(top = 16.dp))
        }
        BasicText("按片名、单集名、演员、年份、题材检索；拼音首字母也行（yxj = 颜心记）", Modifier.padding(top = 30.dp), style = Guga.note(type.caption, colors.faint), maxLines = 2)
    }
}
