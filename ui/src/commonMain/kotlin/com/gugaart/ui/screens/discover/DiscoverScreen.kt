package com.gugaart.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.EmptyLibraryView
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.Kicker
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.screens.StatusBand
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPageState
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.rememberPresetScroll
import com.gugaart.ui.screens.ReportScroll
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.pageStateOf
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 发现页第 1 名的整幅图高度（手机）。 */
internal val DISCOVER_BLEED = 500.dp

/**
 * 发现（手机底栏第二格，设计稿 `p=discover`）：这台服务器自己的榜单——第 1 名整幅铺满 + 大号名次，下面是 02 起的榜单行；
 * 再往下：年代柱状图、随手翻。全部是真实数据（见 [DiscoverData]：热播 / 冷门 / 主编片单没有数据来源，不做）。
 */
@Composable
fun DiscoverScreen(today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    val preset = LocalPreset.current
    val scroll = rememberPresetScroll()
    ReportScroll(scroll)
    val load = rememberLoad("discover") { loadDiscover(it) }
    val startChart = pageStateOf(LocalPageState.current, "chart")
    var chart by remember { mutableStateOf(Chart.of(startChart)) }
    var dip by remember { mutableIntStateOf(0) }
    val lucky = rememberLoad("lucky", dip) { loadLucky(it) }
    val data = load.value
    val list = data?.chart(chart).orEmpty()
    val first = list.firstOrNull()
    // 专色跟第 1 名的海报走
    val poster = rememberImage(first?.art?.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }
    val week = remember(today.day) { isoWeek(today.day.toLong()) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("discover-phone")) {
            when {
                data == null -> {
                    Column(Modifier.padding(horizontal = dimens.margin)) {
                        Spacer(Modifier.height(dimens.statusBar + 8.dp))
                        Masthead(volume = "", date = "第 $week 周", title = "发现", leading = "DISCOVER")
                    }
                    if (load.error != null) ErrorView(load.error!!, load::retry)
                    else if (load.showSkeleton) {
                        LoadingView()
                        Column(Modifier.padding(horizontal = dimens.margin)) { repeat(3) { Skeleton(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(62.dp)) } }
                    }
                }
                data.top.isEmpty() && data.fresh.isEmpty() -> {
                    Column(Modifier.padding(horizontal = dimens.margin)) {
                        Spacer(Modifier.height(dimens.statusBar + 8.dp))
                        Masthead(volume = "", date = "第 $week 周", title = "发现", leading = "DISCOVER")
                    }
                    EmptyLibraryView()
                }
                else -> {
                    // ---- 第 1 名：整幅铺满，报头压在图上
                    FullBleed(first?.art ?: com.gugaart.ui.data.Art(), DISCOVER_BLEED, framing = Framing.of(first?.id.orEmpty()), scrollPx = { scroll.value.toFloat() }) {
                        Column(Modifier.padding(horizontal = dimens.margin)) {
                            Spacer(Modifier.height(dimens.statusBar + 8.dp))
                            Masthead(volume = "", date = "第 $week 周", title = "发现", leading = "DISCOVER")
                        }
                        if (first != null) {
                            val press = rememberPressState()
                            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = dimens.margin).padding(bottom = 6.dp).fillMaxWidth().pressable(press, label = first.name, onClick = { graph.open(first.id) }).testTag("discover-first")) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    BasicText("1", Modifier.padding(end = 14.dp), style = Guga.numeral(type.issue * 1.5f), maxLines = 1)
                                    Column(Modifier.padding(bottom = 14.dp)) {
                                        Kicker(listOf(chart.title, "第 1 名", if (first.kind == Kind.Series) "剧集" else "电影"))
                                        TitleLogo(first.name, first.art.logo, 270.dp, 84.dp, Modifier.padding(top = 6.dp), textOnly = preset.textTitles)
                                    }
                                }
                                heroLine(first).takeIf { it.isNotEmpty() }?.let { BasicText(it, Modifier.padding(top = 2.dp), style = Guga.note(type.caption * 1.1f, colors.dim), maxLines = 1) }
                            }
                        }
                    }
                    Column(Modifier.padding(horizontal = dimens.margin)) {
                        // ---- 两张榜：选中的大一号 + 专色下划线
                        Row(Modifier.fillMaxWidth().padding(top = 26.dp).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.testTag("discover-charts"), verticalAlignment = Alignment.Bottom) {
                            for (c in Chart.entries) {
                                val on = c == chart
                                val press = rememberPressState()
                                BasicText(
                                    c.label,
                                    Modifier.padding(end = 22.dp).pressGraphics(press).heightIn(min = 44.dp).pressable(press, label = c.title, onClick = { chart = c })
                                        .drawBehind { if (on) drawLine(colors.spot, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
                                        .padding(bottom = 10.dp).testTag(if (on) "chart-on" else "chart-off"),
                                    style = Guga.song(if (on) type.section * 1.18f else type.row * 1.1f, if (on) FontWeight.Black else FontWeight.Normal, if (on) colors.ink else colors.dim),
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            BasicText(chart.note, Modifier.padding(bottom = 12.dp), style = Guga.note(type.caption, colors.faint), maxLines = 1)
                        }
                        list.drop(1).forEachIndexed { i, t ->
                            ChartRow(i + 2, t) { graph.open(t.id) }
                            Rule()
                        }

                        // ---- Trakt 追更日历（连着 Trakt 才有）
                        com.gugaart.ui.components.TraktCalendarSection(Modifier.padding(top = dimens.sectionGap))

                        // ---- 年代
                        if (data.decades.size > 1) {
                            SectionHeader("年代", "BY DECADE", Modifier.padding(top = dimens.sectionGap), trailing = "${data.decades.sumOf { it.count }} 部")
                            DecadeBars(data.decades.takeLast(6), Modifier.padding(top = 18.dp))
                        }

                        // ---- 随手翻
                        SectionHeader("随手挑", "LUCKY DIP", Modifier.padding(top = dimens.sectionGap))
                        val pick = lucky.value
                        Row(Modifier.fillMaxWidth().padding(top = 18.dp).testTag("discover-lucky"), verticalAlignment = Alignment.Top) {
                            val press = rememberPressState()
                            Box(Modifier.width(128.dp).aspectRatio(2f / 3f).pressGraphics(press).then(if (pick != null) Modifier.pressable(press, label = pick.name, onClick = { graph.open(pick.id) }) else Modifier)) {
                                if (pick == null) Skeleton(Modifier.fillMaxSize())
                                else CoverImage(pick.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(pick.name, person = false), Modifier.fillMaxSize(), caption = pick.name) }
                            }
                            Column(Modifier.weight(1f).padding(start = 20.dp, top = 6.dp)) {
                                BasicText("随机挑一部", style = Guga.kicker(), maxLines = 1)
                                // 超长片名允许折行，不裁字
                                BasicText(pick?.name ?: "正在挑…", Modifier.padding(top = 8.dp), style = Guga.song(type.section, FontWeight.Black, lineHeight = type.section * 1.36f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                                pick?.let { p -> chartLine(p).takeIf { it.isNotEmpty() }?.let { BasicText(it, Modifier.padding(top = 8.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1) } }
                                PrimaryButton("换一部", { dip++ }, Modifier.padding(top = 16.dp), icon = null)
                            }
                        }
                    }
                }
            }
            // 给底部的玻璃标签栏让出位置
            Spacer(Modifier.height(128.dp + dimens.tabLift))
        }
        // 滚动 2 dp 之后状态栏垫实心纸色（docs/platforms.md）
        StatusBand(solid = com.gugaart.ui.screens.scrolledPast(scroll, 6f))
    }
}

/** 榜单的一行：斜体专色名次 + 片名 / 「剧集 · 2026 · 9.5 分」+ 小海报。 */
@Composable
private fun ChartRow(rank: Int, t: Title, onClick: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 96.dp).pressGraphics(press).pressable(press, label = "第 $rank 名 ${t.name}", onClick = onClick).padding(vertical = 13.dp).testTag("chart-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(Rules.episodeNo(rank), Modifier.width(52.dp), style = Guga.numeral(type.section * 1.18f, colors.spot), maxLines = 1)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(t.name, style = Guga.song(type.row * 1.06f, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            chartLine(t).takeIf { it.isNotEmpty() }?.let { BasicText(it, Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1) }
        }
        Box(Modifier.width(46.dp).aspectRatio(2f / 3f)) {
            CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), textSize = type.caption) }
        }
    }
}

/** 年代柱状图：每个年代一根柱（高度按部数），柱顶一道专色线；下面「4 部」和斜体的「1980s」。柱子不是按钮。 */
@Composable
private fun DecadeBars(decades: List<Decade>, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    val max = decades.maxOf { it.count }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth().testTag("decade-bars"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        for (d in decades) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val h = (4 + 52f * d.count / max).dp
                Box(Modifier.fillMaxWidth().height(h).background(colors.line.copy(alpha = 0.12f)).drawBehind { drawLine(colors.spot, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) })
                BasicText("${d.count} 部", Modifier.padding(top = 8.dp), style = Guga.song(type.fine, color = colors.faint), maxLines = 1)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(1.dp).background(colors.ink))
                BasicText(d.label, Modifier.padding(top = 6.dp), style = Guga.numeral(type.caption * 1.1f), maxLines = 1)
            }
        }
    }
}
