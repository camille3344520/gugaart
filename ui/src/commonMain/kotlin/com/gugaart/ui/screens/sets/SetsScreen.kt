package com.gugaart.ui.screens.sets

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.BleedSource
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.EmptyLibraryView
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.Kicker
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.bleedSourceOf
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.BackRow
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.rememberPresetScroll
import com.gugaart.ui.screens.PageEnd
import com.gugaart.ui.screens.TitlePage
import com.gugaart.ui.screens.detail.ActionsRow
import com.gugaart.ui.screens.detail.Overview
import com.gugaart.ui.screens.detail.PHONE_FOLD_CHARS
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 合集详情整幅图的高度（手机）。 */
internal val SET_BLEED = 430.dp

/**
 * 合集列表（设计稿 `p=sets`）：两列大海报，每张后面露出两道纸边（「一摞」）；海报下 = 斜体编号 + 名字（两行）+ 部数。
 * [libraryId]：合集库的 id（从片库进来）；null = 整台服务器上的合集。
 * 设计稿的「电影系列 / 动画」两个页签是编的分类（合集没有这个字段），这里不做，只写总数。
 */
@Composable
fun SetsScreen(libraryId: String? = null) {
    val graph = LocalGraph.current
    val colors = Guga.colors
    val type = Guga.type
    val dimens = Guga.dimens
    val load = rememberLoad("sets", libraryId) { loadSets(it, libraryId) }
    val sets = load.value
    TitlePage("合集 · COLLECTIONS", "合集", onBack = { graph.nav.backOrHome() }, barTitle = "合集", action = GuguIcon.Search, actionLabel = "搜索", onAction = { graph.nav.navigate(Route.Search()) }, tag = "sets-page") {
        Column(Modifier.padding(horizontal = dimens.margin)) {
            Rule(strong = true)
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("全部", style = Guga.song(type.row, FontWeight.Black, colors.spot), maxLines = 1)
                sets?.let { BasicText(" ${it.size}", Modifier.padding(top = 5.dp), style = Guga.numeral(type.caption, colors.spot), maxLines = 1) }
                Spacer(Modifier.weight(1f))
                sets?.mapNotNull { it.count }?.takeIf { it.isNotEmpty() }?.sum()?.let { BasicText("共 $it 部影片", style = Guga.note(type.caption * 1.1f, colors.dim), maxLines = 1) }
            }
            Rule()
            when {
                sets == null && load.error != null -> ErrorView(load.error!!, load::retry)
                sets == null -> if (load.showSkeleton) {
                    LoadingView()
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(dimens.gutter + 8.dp)) { repeat(2) { Skeleton(Modifier.weight(1f).aspectRatio(2f / 3f)) } }
                }
                sets.isEmpty() -> EmptyLibraryView()
                else -> {
                    for ((r, row) in sets.chunked(2).withIndex()) {
                        Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(dimens.gutter + 8.dp)) {
                            for ((c, s) in row.withIndex()) SetCard(r * 2 + c + 1, s, { graph.open(s.id) }, Modifier.weight(1f))
                            // 只剩一套：不拉伸，另一半留白
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    PageEnd("合集")
                }
            }
        }
    }
}

/** 一套合集的卡：海报后面露出两道纸边（一摞），下面 = 斜体编号 + 名字 + 部数。 */
@Composable
private fun SetCard(number: Int, set: Title, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Column(modifier.pressable(press, label = set.name, onClick = onClick).testTag("set-card")) {
        Box(
            Modifier.pressGraphics(press).fillMaxWidth().aspectRatio(2f / 3f).padding(end = 8.dp, bottom = 8.dp).drawBehind {
                // 两道纸边：往右下各错开 4 dp
                for (i in 2 downTo 1) {
                    val o = (4 * i).dp.toPx()
                    drawRect(colors.paper, Offset(o, o), Size(size.width, size.height))
                    drawRect(colors.line.copy(alpha = 0.36f), Offset(o, o), Size(size.width, size.height), style = Stroke(1.dp.toPx()))
                }
            },
        ) {
            CoverImage(set.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(set.name, person = false), Modifier.fillMaxSize(), caption = set.name) }
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
            BasicText(Rules.episodeNo(number), Modifier.width(40.dp), style = Guga.numeral(type.section * 1.05f, colors.spot), maxLines = 1)
            Column(Modifier.weight(1f)) {
                BasicText(set.name, style = Guga.song(type.row * 0.86f, FontWeight.Bold, lineHeight = type.row * 1.28f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                set.count?.let { BasicText("$it 部", Modifier.padding(top = 3.dp), style = Guga.note(type.caption, colors.faint), maxLines = 1) }
            }
        }
    }
}

/**
 * 合集详情（设计稿 `p=set`）：题图整幅铺满（合集自己没有 → 第一部有剧照的成员的剧照，图注写明取自哪部）→ 栏目行「合集 · 4 部 · 2007 — 2021」
 * → 名字 + 右边的大号部数和总时长 → 均分 / 已看 / 最新 → 从第一部看起 → 收藏 / 整套已看 → 简介 → 本辑（按上映顺序，带校对格）。
 */
@Composable
fun SetScreen(set: Title, day: Int) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val dimens = Guga.dimens
    val type = Guga.type
    val colors = Guga.colors
    val hour = LocalHour.current
    val scroll = rememberPresetScroll()
    val voice = rememberNoteVoice()
    val poster = rememberImage(set.art.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }
    val load = rememberLoad("set", set.id) { loadSet(it, set) }
    val data = load.value ?: SetData(set, emptyList())
    val members = data.members
    val fav = chrome.isFavorite(set)
    val seen = members.count { chrome.isPlayed(it.id, it.played) }
    val allSeen = members.isNotEmpty() && seen == members.size
    val solidAt = with(LocalDensity.current) { (SET_BLEED - dimens.statusBar - 50.dp - 70.dp).toPx() }
    val borrowed = data.borrowed
    val art = if (borrowed != null) set.art.copy(backdrop = borrowed.art.backdrop) else set.art

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("set-phone")) {
            FullBleed(
                art,
                SET_BLEED,
                framing = Framing.of(borrowed?.id ?: set.id),
                caption = when {
                    borrowed != null -> "剧照 · 取自《${borrowed.name}》"
                    bleedSourceOf(art) == BleedSource.Backdrop -> "剧照 · 合集背景图"
                    bleedSourceOf(art) == BleedSource.PosterBlur -> "海报"
                    else -> null
                },
                scrollPx = { scroll.value.toFloat() },
            ) {
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = dimens.margin).padding(bottom = 4.dp).fillMaxWidth()) {
                    Kicker(listOfNotNull("合集", data.count.takeIf { it > 0 }?.let { "$it 部" }, data.years))
                    Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        // 合集的名字普遍长（「…（系列）」「… Collection」）而且多半没有 logo：折两行，不跟着字数一路缩到看不清（设计稿 set / set-school）
                        val tierScale = Rules.TITLE_SCALE[Rules.titleTier(set.name)]
                        val titleBase = type.title * ((if (Rules.isLatinTitle(set.name)) 0.95f else maxOf(tierScale, 0.66f)) / tierScale)
                        Box(Modifier.weight(1f)) { TitleLogo(set.name, set.art.logo, 250.dp, 96.dp, textOnly = preset.textTitles, baseSize = titleBase) }
                        if (data.count > 0) {
                            Column(Modifier.padding(start = 10.dp), horizontalAlignment = Alignment.End) {
                                BasicText(data.count.toString(), style = Guga.numeral(type.issue * 0.9f), maxLines = 1, modifier = Modifier.testTag("set-count"))
                                BasicText(listOfNotNull("部", data.runtimeSec?.let { "共 ${Rules.minutes(it)}" }).joinToString(" · "), style = Guga.song(type.fine * 1.15f, color = colors.dim), maxLines = 1)
                            }
                        }
                    }
                }
            }
            Column(Modifier.padding(horizontal = dimens.margin)) {
                Rule(Modifier.padding(top = 12.dp), strong = true)
                Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("set-facts"), verticalAlignment = Alignment.CenterVertically) {
                    val facts = listOfNotNull(
                        data.average?.let { "均分" to it },
                        members.takeIf { it.isNotEmpty() }?.let { "已看" to "$seen / ${it.size}" },
                        data.latestYear?.let { "最新" to it.toString() },
                    )
                    facts.forEachIndexed { i, (label, value) ->
                        if (i > 0) Spacer(Modifier.weight(1f))
                        BasicText("$label  ", style = Guga.song(type.caption, color = colors.dim), maxLines = 1)
                        BasicText(value, style = Guga.song(type.caption, FontWeight.Bold), maxLines = 1)
                    }
                }
                Rule()
                val start = data.startWith
                PrimaryButton(
                    when {
                        start == null -> "从第一部看起"
                        start.progress > 0f -> "续看 · ${start.name}"
                        start.id == members.firstOrNull()?.id -> "从第一部看起 · ${start.name}"
                        else -> "接着看 · ${start.name}"
                    },
                    { start?.let { graph.play(it.id, it.positionSec?.times(1000)) } },
                    Modifier.padding(top = 18.dp).fillMaxWidth(),
                    progress = start?.progress ?: 0f,
                    trailing = start?.runtimeSec?.let { Rules.minutes(it) },
                    enabled = start != null,
                )
                ActionsRow(
                    fav,
                    {
                        graph.toggleFavorite(chrome, set.id, fav)
                        voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = set.id.hashCode() + day))
                    },
                    "整套已看",
                    allSeen,
                    // 整套已看 = 逐部标记（合集本身没有「已看」）；再点一次 = 逐部改回未看
                    { for (m in members) if (chrome.isPlayed(m.id, m.played) == allSeen) graph.togglePlayed(chrome, m.id, allSeen) },
                    Modifier.padding(top = 14.dp),
                )
                // 合集的简介可能很长：比详情页折得更早
                Overview(Rules.fold(set.overview, PHONE_FOLD_CHARS), Modifier.padding(top = 18.dp))

                SectionHeader("本合集", "IN THIS SET", Modifier.padding(top = 30.dp), trailing = "按上映顺序")
                when {
                    load.value == null && load.error != null -> ErrorView(load.error!!, load::retry)
                    load.value == null -> repeat(set.count?.coerceAtMost(4) ?: 2) {
                        Skeleton(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(62.dp))
                        Rule()
                    }
                    members.isEmpty() -> BasicText("没有影片", Modifier.padding(vertical = 24.dp), style = Guga.song(type.caption, color = colors.dim))
                    else -> members.forEachIndexed { i, m ->
                        val played = chrome.isPlayed(m.id, m.played)
                        MemberRow(i + 1, m, played, { graph.open(m.id) }, { graph.togglePlayed(chrome, m.id, played) })
                        Rule()
                    }
                }
                if (members.isNotEmpty()) PageEnd("本合集")
            }
            Spacer(Modifier.height(72.dp))
        }
        BackRow(set.name, solid = com.gugaart.ui.screens.scrolledPast(scroll, solidAt), onBack = { graph.nav.backOrHome() }, favorite = fav, onFavorite = { graph.toggleFavorite(chrome, set.id, fav) })
    }
}

/** 成员行：斜体编号 + 片名 / 「2007 · 7.6 分 · 1 小时 38 分」+ 小海报 + 行尾校对格。点整行 = 打开；点校对格 = 标记已看。 */
@Composable
private fun MemberRow(number: Int, m: Title, played: Boolean, onClick: () -> Unit, onTick: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().heightIn(min = 92.dp).testTag("set-member"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).pressGraphics(press).pressable(press, label = m.name, onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(Rules.episodeNo(number), Modifier.width(52.dp), style = Guga.numeral(type.section * 1.05f, colors.spot), maxLines = 1)
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                BasicText(m.name, style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                memberLine(m) { Rules.minutes(it) }.takeIf { it.isNotEmpty() }?.let {
                    BasicText(it, Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.width(46.dp).aspectRatio(2f / 3f)) {
                CoverImage(m.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(m.name, person = false), Modifier.fillMaxSize(), textSize = type.caption) }
            }
        }
        val tick = rememberPressState()
        Box(Modifier.width(38.dp).height(48.dp).pressable(tick, label = if (played) "改成未看" else "标记已看", onClick = onTick), contentAlignment = Alignment.CenterEnd) { ProofTick(played) }
    }
}

/**
 * `Route.Library(id)` 的入口：合集库（CollectionType = boxsets）排成合集列表，其它库是海报墙。
 * 库的种类要先问一次服务器（`getViewList`）；问到之前这一页先空着（很快；出错就按海报墙走，它有自己的错误页）。
 */
@Composable
fun LibraryScreen(id: String) {
    val libs = rememberLoad("library-kind") { com.gugaart.ui.data.loadLibraries(it) }
    val list = libs.value
    when {
        list == null && libs.error == null -> Box(Modifier.fillMaxSize())
        list?.firstOrNull { it.id == id }?.kind == "boxsets" -> SetsScreen(id)
        else -> com.gugaart.ui.screens.wall.WallScreen(id)
    }
}
