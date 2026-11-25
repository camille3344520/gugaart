package com.gugaart.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.CoverFlow
import com.gugaart.ui.components.CoverFlowSpec
import com.gugaart.ui.components.EndOfPage
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.IssueOdometer
import com.gugaart.ui.components.Kicker
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.OfflineBar
import com.gugaart.ui.components.PageDots
import com.gugaart.ui.components.PosterCard
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.TocRow
import com.gugaart.ui.components.rememberCoverFlowState
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.ReportScroll
import com.gugaart.ui.screens.StatusBand
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.servers.MastheadServerLine
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.coverScale
import com.gugaart.ui.theme.feel
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph

/**
 * 手机首页（设计稿 `design/phone/index.html?p=home`）：封面区 + 目录区。
 *
 * 封面区：这一期的剧照**虚化、压暗**从状态栏铺到期号；报头压在图上；三维海报流（跟手、惯性吸附、倾斜、视差）；片名 logo 搭在海报下沿；
 * 期号里程表跟手滚；翻过半张，栏目行 / 导语 / 续看按钮 / 主编手记一起换，专色换（700 ms，主题里做）。
 * 目录区：继续观看的画报目录行、「新刊」海报带、末尾她打盹。底部留 150 给标签栏。
 */
@Composable
fun HomePhone(data: HomeData, volume: Int, date: String, day: Int, offline: Boolean, onRefresh: () -> Unit, initialIssue: Int = 0, initialScroll: Int = 0) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    val scroll = rememberScrollState(initialScroll)
    ReportScroll(scroll)
    // 矮屏收紧封面区（D-lead-23）：412 × 905 的参考安卓机 = 1（一个像素不变）；402 × 874 → 0.96；375 × 812 → 0.86。字号不变
    val k = coverScale(com.gugugaga.ds.theme.LocalWindowDp.current.height.value - dimens.statusBar.value)
    val covers = data.covers
    val flow = rememberCoverFlowState(covers.size, initialIssue)
    // 触感（M4-3）：翻期号 = 选择（翻到新的一期才响，进首页时不响）
    val phoneHost = com.gugaart.ui.theme.LocalPhoneHost.current
    val lastIssue = remember { intArrayOf(flow.settled) }
    LaunchedEffect(flow.settled) {
        if (flow.settled != lastIssue[0]) {
            lastIssue[0] = flow.settled
            phoneHost.feel(com.gugaart.ui.theme.HapticStyle.Selection)
        }
    }
    val issue = covers.getOrNull(flow.settled)
    val voice = rememberNoteVoice()
    val hour = LocalHour.current
    // 没人动时封面自己翻（还在封面那一屏时）；每 10–20 分钟换一波
    // 目录的入口（期号指示右边的「目录 ↓」）和第一次的提示；翻到目录就算看过了
    val tocHint = rememberTocHint(UiMode.Phone, voice)
    val tocScope = rememberCoroutineScope()
    var tocY by remember { mutableIntStateOf(0) }
    LaunchedEffect(tocHint.show) {
        if (tocHint.show) {
            snapshotFlow { scroll.value > 300 }.first { it }
            tocHint.seen()
        }
    }
    com.gugaart.ui.idle.CoverAutoFlip(flow, visible = { scroll.value < 300 })
    com.gugaart.ui.idle.CoverRotation {
        onRefresh()
        flow.animateTo(0)
    }

    // 专色跟正中那一张的海报走
    val poster = rememberImage(issue?.art?.poster, keepPrevious = true)
    LaunchedEffect(poster, issue?.id) { chrome.spot = poster?.spot() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("home-phone")) {
            if (issue != null) {
                // ---- 封面区 ----
                Box(Modifier.fillMaxWidth()) {
                    // 背后的整幅图：这一期的剧照（没有就海报），虚化压暗；换期交叉淡入
                    FullBleed(issue.art, 560.dp * k, Modifier.testTag("home-backdrop"), framing = Framing.of(issue.seriesId ?: issue.id), soften = true, dim = 0.52f)
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(dimens.statusBar + 8.dp))
                        if (offline) OfflineBar(onRetry = onRefresh)
                        PhoneMasthead(volume, date)
                        CoverFlow(
                            flow,
                            covers.map { it.art.poster },
                            Modifier.padding(top = 18.dp * k),
                            cardWidth = 220.dp * k,
                            names = covers.map { it.displayName },
                            onClick = { i -> covers.getOrNull(i)?.let { graph.open(it.seriesId ?: it.id) } },
                        )
                        // 片名搭在海报下沿十几 dp；手指在海报上时 logo 反向走（视差）
                        val shift = with(LocalDensity.current) { CoverFlowSpec.PARALLAX_LOGO_DP.dp.toPx() }
                        TitleLogo(
                            issue.displayName,
                            issue.art.logo,
                            width = 260.dp * k,
                            height = 76.dp * k,
                            modifier = Modifier.align(Alignment.CenterHorizontally).offset(y = (-26).dp).graphicsLayer {
                                translationX = -flow.pointer.x * shift
                                translationY = -flow.pointer.y * shift
                            },
                            alignX = 0.5f,
                        )
                        // 期号指示；右边是目录的入口（不占高度）
                        Box(Modifier.fillMaxWidth().height(3.dp).offset(y = (-10).dp)) {
                            PageDots(flow.settled, covers.size, Modifier.align(Alignment.Center))
                            if (data.resume.isNotEmpty() || data.shelves.isNotEmpty()) {
                                TocJump(
                                    tocHint.show,
                                    {
                                        tocHint.seen()
                                        tocScope.launch { scroll.animateScrollTo(tocY) }
                                    },
                                    Modifier.align(Alignment.CenterEnd).wrapContentHeight(unbounded = true).padding(end = dimens.margin - 10.dp),
                                )
                            }
                        }
                        Column(Modifier.padding(horizontal = dimens.margin)) {
                            Rule(Modifier.padding(top = 4.dp))
                            Row(Modifier.padding(top = 14.dp * k).height(118.dp), verticalAlignment = Alignment.Top) {
                                IssueOdometer(flow.position, covers.size, Modifier.width(118.dp))
                                Box(Modifier.padding(top = 6.dp).width(1.dp).height(100.dp).background(colors.line))
                                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                    Kicker(coverKicker(issue))
                                    issue.lead?.let {
                                        BasicText(it, Modifier.padding(top = 6.dp), style = Guga.song(type.lead, lineHeight = type.leadLine), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            // 续看按钮 + 收藏 + 印章位 + 「余 19 分 · 59%」
                            val favId = issue.seriesId ?: issue.id
                            val fav = chrome.isFavorite(favId, issue.favorite && issue.kind != Kind.Episode)
                            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                PrimaryButton(coverAction(issue), { graph.play(issue.id, issue.positionSec?.times(1000)) }, progress = issue.progress)
                                TextLink("收藏", {
                                    graph.toggleFavorite(chrome, favId, fav)
                                    voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = issue.id.hashCode() + day))
                                })
                                SealSlot(fav)
                                Spacer(Modifier.weight(1f))
                                BasicText(Rules.progressLabel(issue.progress, issue.runtimeSec, phone = true), style = Guga.note(type.caption, colors.dim), maxLines = 1)
                            }
                            Rule(Modifier.padding(top = 24.dp))
                            // 主编手记：她的小格（页面流里的一栏，不遮挡）
                            EditorNote(coverNote(issue, hour, day), Modifier.padding(vertical = 16.dp), voice = voice)
                            Rule()
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(dimens.statusBar + 8.dp))
                PhoneMasthead(volume, date)
            }

            // ---- 目录区 ----
            Column(Modifier.padding(horizontal = dimens.margin).onGloballyPositioned { tocY = (it.positionInRoot().y + scroll.value).toInt().coerceAtLeast(0) }) {
                if (data.resume.isNotEmpty()) {
                    SectionHeader("片单", "LINEUP", Modifier.padding(top = 26.dp), trailing = "继续观看 · ${data.resumeTotal}")
                    for ((i, t) in data.resume.take(TOC_ROWS).withIndex()) {
                        TocRow(
                            number = i + 1,
                            title = t.displayName,
                            subtitle = tocSubtitle(t, phone = true),
                            thumbUrl = t.art.thumb ?: t.art.backdrop,
                            thumbFallback = Rules.monogram(t.displayName, person = false),
                            progress = t.progress,
                            onClick = { graph.open(t.seriesId ?: t.id) },
                        )
                        Rule()
                    }
                    if (data.resumeTotal > TOC_ROWS) {
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
                            TextLink("查看全部 ${data.resumeTotal} 条观看记录  →", { graph.nav.navigate(Route.History()) })
                        }
                    }
                }
            }
            // 按类排的栏：电视剧 / 电影 / 动漫 / 综艺（空的不出现）
            for (band in data.shelves) {
                SectionHeader("${band.name}", band.kind.english, Modifier.padding(top = dimens.sectionGap).padding(horizontal = dimens.margin).testTag("home-shelf-${band.key}"), trailing = "最近添加")
                // 横向海报带：第一张对齐页边，最后一张露半张提示可滑
                Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = dimens.margin), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    for (t in band.titles) PosterCard(t, 128.dp, { graph.open(t.id) })
                }
            }
            EndOfPage(seed = day)
            // 底部安全区：末尾内容能滚到标签栏上方
            Spacer(Modifier.height(150.dp + dimens.tabLift))
        }
        // 状态栏：滚动 2 dp 就变实心，内容从它下面硬切
        StatusBand(solid = scroll.value > with(LocalDensity.current) { 2.dp.toPx() })
    }
}

/**
 * 手机首页的报头 + 下面一行小字「当前服务器 ⌄」（1.5.0：点它换服务器，[ServerSheet]）。
 * 那一行压在报头下线和海报流之间的空里，不占版面：报头（刊名正中的徽记）、海报流、片名 logo 一个像素都不挪。
 */
@Composable
private fun PhoneMasthead(volume: Int, date: String) {
    val dimens = Guga.dimens
    Box(Modifier.padding(horizontal = dimens.margin)) {
        Masthead(volume = "VOL. $volume", date = date, issue = volume, dateSlot = { com.gugaart.ui.screens.me.RankChip(date, it) })
        // 报头高 44，下线之下 0–22 dp：海报流离报头 18 dp（矮屏 ×0.86），这一行的字落在这一截里
        MastheadServerLine(Modifier.align(Alignment.BottomStart).offset(y = 22.dp))
    }
}

/** 电脑 / 电视的首页版式在下一阶段；现在只立报头和一行媒体库（共用同一份 [HomeData]，证明状态三端可复用）。 */
@Composable
fun HomeWide(data: HomeData, volume: Int, date: String) {
    val dimens = Guga.dimens
    Box(Modifier.fillMaxSize().padding(horizontal = dimens.margin).padding(top = if (dimens.statusBar.value == 0f && dimens.mastheadHeight > 44.dp) 40.dp else 0.dp)) {
        Masthead(volume = "VOL. $volume", date = date, issue = volume, dateSlot = { com.gugaart.ui.screens.me.RankChip(date, it) })
        BasicText(
            "媒体库 ${data.libraries.size} · " + data.libraries.joinToString(" · ") { l -> if (l.titles != null) "${l.name} ${l.titles}" else l.name },
            Modifier.align(Alignment.Center),
            style = Guga.song(Guga.type.lead, color = Guga.colors.dim),
        )
    }
}
