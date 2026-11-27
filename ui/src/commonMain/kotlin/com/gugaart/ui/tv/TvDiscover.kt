package com.gugaart.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.rememberSpotColor
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.discover.Decade
import com.gugaart.ui.screens.discover.TOP_POOL
import com.gugaart.ui.screens.discover.loadDiscover
import com.gugaart.ui.screens.discover.loadLucky
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.detail.TvActionButton
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvGroupsRoot
import com.gugaart.ui.tv.detail.TvLeadRow
import com.gugaart.ui.tv.detail.TvTitle
import com.gugaart.ui.tv.detail.tvFirstSentence
import com.gugaart.ui.tv.detail.tvKickOf
import com.gugaart.ui.tv.detail.tvKindName
import com.gugaart.ui.tv.detail.tvMember
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 电视发现页的版式数（设计稿 `tv/shots/discover*.png`，1920 × 1080）。 */
object TvDiscoverSpec {
    /** 右栏（新到）排几部（一屏排得下的）。 */
    const val CHART = 6
    /** 上半屏（头一篇 + 新到）到哪儿为止；整幅图铺到这里。 */
    val TOP_H = 706.dp
    /** 右栏：x 1270–1824，行高 86（六行 + 下面高分一栏要一起排进 1080 的安全边里）。 */
    val CHART_X = 1270.dp
    val CHART_W = 554.dp
    val CHART_ROW = 86.dp
    /** 下面高分一栏的卡：280 × 158（16 : 9）。 */
    val CARD_W = 280.dp
    val CARD_H = 158.dp
    /** 焦点行上的名次：深朱红。 */
    val NUMBER_ON_INK = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK
}

/**
 * 发现页的方向键路径（`docs/platforms.md` §3）：三个组——按钮行 `disc-cta`、右栏新到 `disc-new`、下面一栏高分 `disc-top`。
 * 返回去哪个组；[TvGroups.STAY] = 这个方向到头了不动；null = 不管（最左再按左：交给外壳进导航）。纯函数，有单测。
 */
fun tvDiscoverEdge(from: String, dir: TvDir, has: (String) -> Boolean): String? = when (from) {
    "disc-cta" -> when (dir) {
        TvDir.Right -> if (has("disc-new")) "disc-new" else TvGroups.STAY
        TvDir.Down -> if (has("disc-top")) "disc-top" else TvGroups.STAY
        TvDir.Left -> null
        TvDir.Up -> TvGroups.STAY
    }
    "disc-new" -> when (dir) {
        TvDir.Left -> "disc-cta"
        TvDir.Down -> if (has("disc-top")) "disc-top" else TvGroups.STAY
        else -> TvGroups.STAY
    }
    "disc-top" -> when (dir) {
        TvDir.Up -> "disc-cta"
        TvDir.Left -> null
        else -> TvGroups.STAY
    }
    else -> null
}

/** 年代的范围：「1989 — 2026」（真实的年份聚合；只有一个年代就只写它）。 */
fun tvDecadeRange(decades: List<Decade>): String? = when {
    decades.isEmpty() -> null
    decades.size == 1 -> decades.single().label
    else -> "${decades.first().label} — ${decades.last().label}"
}

/**
 * 发现（电视，设计稿 `p=discover`）：一屏。新到在前、高分在后（用户 2026-11-15：「新到排在前面，高分排后面，高分要随机一点」）：
 * 上半屏左边是头一篇 = 最新入库的那一部（整幅剧照 + 片名 + 一句话 + 播放 / 换一篇 / 详情），右边是新到榜；
 * **焦点走到右栏的哪一行，左边就换成那一部**（焦点预览）。下面一栏是高分榜（评分前 40 部里每次随机抽 10 部）。
 *
 * 数据和手机 / 电脑的发现页是同一份（`loadDiscover`；「换一篇」= 服务器随机取一部）。
 * 设计稿下半屏的「题材」「年代」两排格子没有排：上游没有按题材 / 年代浏览的路由，遥控器走到一排按了没反应的格子上是死路；
 * 那一块排高分榜（真实数据、每张都能进），年代的范围写在栏目标题右端。
 *
 * 路径：默认焦点「播放」；按钮行左右走，最右再按右 = 进新到，下 = 高分；新到上下走、左回按钮行、到底再按下 = 高分；
 * 高分左右走、上回按钮行；任何一行最左再按左 = 进导航（外壳）。返回 = 回首页（外壳）。
 */
@Composable
fun TvDiscoverScreen(today: Today) {
    val g = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val scope = rememberCoroutineScope()
    val session by g.session.session.collectAsState()
    val groups = remember { TvGroups() }
    val load = rememberLoad("tv-discover") { loadDiscover(it) }
    val data = load.value
    // 「换一篇」：服务器随机取的一部（取到之前左边不动——不闪）
    var swapped by remember { mutableStateOf<Title?>(null) }
    var swapping by remember { mutableStateOf(false) }

    if (data == null || (data.top.isEmpty() && data.fresh.isEmpty())) {
        LaunchedEffect(Unit) { chrome.spot = null }
        Column(Modifier.fillMaxSize().testTag("tv-discover-state")) {
            TvPageHeader("发现", "DISCOVER", today)
            when {
                data != null -> TvStateView("tv-discover-empty", "捧白纸", "这台服务器上还没有内容", null)
                load.error != null -> TvStateView("tv-discover-error", if (load.error!!.contains("网络")) "拔掉的电源插头" else "摊手", load.error!!, null, action = "再试一次", onAction = load::retry)
                load.showSkeleton -> TvStateView("tv-discover-loading", "摆稿纸", "正在排片", MascotLines.pick("wall.loading", hour = LocalHour.current, seed = today.day))
            }
        }
        return
    }

    val chart = data.fresh.take(TvDiscoverSpec.CHART)
    val pick = swapped ?: data.fresh.firstOrNull() ?: data.top.first()
    val focusedRank = groups.current?.takeIf { it.startsWith("new-") }?.removePrefix("new-")?.toIntOrNull()
    val shown = focusedRank?.let { chart.getOrNull(it) } ?: pick
    val poster = rememberImage(shown.art.poster, keepPrevious = true)
    val tvSpot = rememberSpotColor(poster, tv = true)
    LaunchedEffect(tvSpot) { chrome.spot = tvSpot }
    groups.edge = { from, dir -> tvDiscoverEdge(from, dir, groups::has) }

    TvGroupsRoot(groups, Modifier.testTag("tv-discover")) {
        TvBleed(shown.art.backdrop, shown.art.poster ?: shown.art.thumb, TvDiscoverSpec.TOP_H)
        Column(Modifier.fillMaxSize()) {
            TvMasthead(today, Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = TvSpec.TOP))
            Box(Modifier.fillMaxWidth().height(TvDiscoverSpec.TOP_H - TvSpec.TOP - Guga.dimens.mastheadHeight)) {
                // ---- 左：头一篇（最新入库）/ 焦点预览 ----
                Column(Modifier.padding(start = TvSpec.LEFT, top = 30.dp).width(1040.dp).testTag("tv-discover-pick")) {
                    val head = when {
                        focusedRank != null -> "新到 · 第 ${focusedRank + 1} 部" + (shown.rating?.let { " · ★ $it" } ?: "")
                        swapped != null -> "随手挑一部 · LUCKY DIP"
                        shown.id == data.fresh.firstOrNull()?.id -> "新到 · 刚入库 · NEW ARRIVAL"
                        else -> "高分 · TOP RATED"
                    }
                    BasicText(head, style = Guga.song(24.sp, FontWeight.Bold, letterSpacing = 0.16.em), maxLines = 1)
                    Spacer(Modifier.weight(1f))
                    TvTitle(shown)
                    val facts = listOfNotNull(
                        tvKickOf(shown, tvKindName(shown.kind)).takeIf { it.isNotEmpty() },
                        if (shown.kind == Kind.Series) shown.count?.let { "全 $it 集" } else shown.runtimeSec?.let { Rules.minutes(it) },
                        shown.rating?.let { "★ $it" },
                    )
                    BasicText(facts.joinToString(" · "), Modifier.padding(top = 16.dp), style = Guga.song(24.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.16.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val lead = tvFirstSentence(shown.overview)
                    // 没有简介也留着这两行的高度：焦点在榜上走的时候按钮行不跟着上下跳
                    BasicText(lead, Modifier.padding(top = 14.dp).width(820.dp).height(86.dp), style = Guga.song(28.sp, FontWeight.SemiBold, lineHeight = 43.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TvGroup("disc-cta", listOf("disc-play", "disc-swap", "disc-info"), default = "disc-play")
                    Row(Modifier.padding(top = 22.dp, bottom = 40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // 按钮行说的永远是左边画着的那一部：焦点在按钮上时 = 这一篇；焦点在榜上时左边是预览，按钮上的字跟着它（确定走的是那一行自己的动作）
                        TvActionButton(
                            "disc-play",
                            if (shown.kind == Kind.Series) "去看这一部" else "播放",
                            { if (shown.kind == Kind.Series) g.open(shown.id) else g.play(shown.id, shown.positionSec?.times(1000)) },
                            icon = GuguIcon.Play,
                            primary = true,
                            default = true,
                        )
                        TvActionButton(
                            "disc-swap",
                            if (swapping) "正在挑……" else "换一部",
                            {
                                val client = session?.client
                                if (client != null && !swapping) {
                                    swapping = true
                                    scope.launch {
                                        try {
                                            loadLucky(client)?.let { swapped = it }
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            // 没翻到：这一篇留着不动
                                        } finally {
                                            swapping = false
                                        }
                                    }
                                }
                            },
                            icon = GuguIcon.RotateCcw,
                        )
                        TvActionButton("disc-info", "详情", { g.open(shown.id) }, icon = GuguIcon.Info)
                    }
                }
                // ---- 右：新到榜 ----
                if (chart.isNotEmpty()) {
                    Column(Modifier.padding(start = TvDiscoverSpec.CHART_X, top = 20.dp).width(TvDiscoverSpec.CHART_W).testTag("tv-discover-chart")) {
                        Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BasicText("壹", Modifier.alignByBaseline(), style = Guga.numeral(36.sp, colors.spot), maxLines = 1)
                            BasicText("新到", Modifier.alignByBaseline(), style = Guga.song(Guga.type.section, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
                            BasicText("NEW ARRIVALS", Modifier.alignByBaseline(), style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                            Box(Modifier.weight(1f).height(1.dp).background(colors.line))
                        }
                        TvGroup("disc-new", chart.indices.map { "new-$it" }, horizontal = false)
                        for ((i, t) in chart.withIndex()) TvChartRow(i, t) { g.open(t.id) }
                    }
                }
            }
            // ---- 下：高分榜（评分前 40 部里每次随机抽 10 部）----
            if (data.top.isNotEmpty()) {
                TvSectionHeader("高分", "TOP RATED", number = "贰", trailing = listOfNotNull("评分前 $TOP_POOL · 随机", tvDecadeRange(data.decades), data.total.takeIf { it > 0 }?.let { "片库共 $it 部" }).joinToString(" · "))
                TvLeadRow("disc-top", data.top, { "top-${it.id}" }, TvDiscoverSpec.CARD_W, 250.dp, Modifier.padding(top = 14.dp), gap = 28.dp) { t, key, _ ->
                    TvLandscapeCard(
                        image = t.art.thumb ?: t.art.backdrop ?: t.art.poster,
                        title = t.name,
                        sub = listOfNotNull(tvKindName(t.kind), t.year?.toString(), t.rating?.let { "★ $it" }).joinToString(" · "),
                        onClick = { g.open(t.id) },
                        modifier = Modifier.tvMember(groups, key),
                        key = key,
                        onLongClick = { TvLongPressMenu.open(t) },
                        width = TvDiscoverSpec.CARD_W,
                        height = TvDiscoverSpec.CARD_H,
                        framing = Framing(0.5f, 0.3f),
                        origin = TransformOrigin(0f, 0.4f),
                    )
                }
            } else groups.remove("disc-top")
        }
    }
}

/** 右栏新到的一行：斜体序号（专色）+ 小海报 + 片名 +「电影 · 2026」+ 右端大号评分（没有评分 = 一道短横）。焦点 = 整行米白实底、墨字 + 校对角。 */
@Composable
private fun TvChartRow(index: Int, t: Title, onClick: () -> Unit) {
    val colors = Guga.colors
    TvBlockFocus(
        "new-$index",
        onClick,
        Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
        label = "新到第 ${index + 1} 部 ${t.name}",
        kind = TvFocusKind.Row,
        onLongClick = { TvLongPressMenu.open(t) },
    ) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(Modifier.fillMaxWidth().height(TvDiscoverSpec.CHART_ROW).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("${index + 1}", Modifier.width(62.dp), style = Guga.numeral(40.sp, if (focused) TvDiscoverSpec.NUMBER_ON_INK else colors.spot), maxLines = 1)
            Box(Modifier.size(50.dp, 74.dp).background(Color(0xFF1C1914)).border(1.dp, colors.ink.copy(alpha = 0.16f))) {
                CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), textSize = 18.sp) }
            }
            // 片名 +「电影 · 2026」占中间那一段：片名先要自己的宽度，太长才省略；评分贴右端
            Row(Modifier.weight(1f).padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(t.name, Modifier.weight(1f, fill = false), style = Guga.song(30.sp, FontWeight.Black, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    listOfNotNull(tvKindName(t.kind), t.year?.toString()).joinToString(" · "),
                    Modifier.padding(start = 18.dp),
                    style = Guga.song(22.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.dim),
                    maxLines = 1,
                )
            }
            BasicText(t.rating ?: "—", Modifier.padding(start = 14.dp), style = Guga.numeral(40.sp, ink), maxLines = 1)
        }
    }
}
