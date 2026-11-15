package com.gugaart.ui.tv.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.components.rememberCrossfade
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.detail.PersonData
import com.gugaart.ui.screens.detail.loadPerson
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.sets.SetData
import com.gugaart.ui.screens.sets.loadSet
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvLongPressMenu
import com.gugaart.ui.tv.TvPage
import com.gugaart.ui.tv.TvSpec
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon

/** 「共 9 小时 12 分」：合集总时长。 */
private fun totalRuntime(sec: Long?): String? = sec?.takeIf { it > 0 }?.let { "共 ${Rules.minutes(it)}" }

/** 合集 / 人物：按钮行 ↔ 下面一排。纯函数，有单测。 */
fun twoRowEdge(from: String, dir: TvDir, top: String, bottom: String, has: (String) -> Boolean): String? = when {
    dir == TvDir.Left -> null
    dir == TvDir.Right -> TvGroups.STAY
    from == top && dir == TvDir.Down && has(bottom) -> bottom
    from == bottom && dir == TvDir.Up && has(top) -> top
    else -> TvGroups.STAY
}

/**
 * 合集详情（电视，设计稿 `p=coll`）：横版剧照整幅铺满；片名（合集没有 logo → 宋体）、简介（三行截断）压在左边的暗角里；
 * 默认焦点「从第一部看起」；下 = 成员一排（按上映顺序编号，只有 2 部时不拉伸）；上回按钮。
 */
@Composable
fun TvCollection(set: Title, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val groups = remember { TvGroups() }
    val load = rememberLoad("tv-set", set.id) { loadSet(it, set) }
    val data = load.value ?: SetData(set, emptyList())
    val members = data.members
    val firstPoster = rememberImage(members.firstOrNull()?.art?.poster ?: set.art.poster, keepPrevious = true)
    LaunchedEffect(firstPoster) { chrome.spot = firstPoster?.spot(tv = true) }
    val fav = chrome.isFavorite(set)
    val borrowed = data.borrowed
    val art = if (borrowed != null) set.art.copy(backdrop = borrowed.art.backdrop) else set.art
    groups.edge = { from, dir -> twoRowEdge(from, dir, "cta", "members", groups::has) }

    TvGroupsRoot(groups, Modifier.testTag("tv-collection")) {
        TvBackdrop(art, deep = false, heavy = true)
        TvRunHead("COLLECTION", when {
            borrowed != null -> "剧照｜《${borrowed.name}》"
            set.art.backdrop != null -> "剧照｜合集题图"
            else -> null
        }, today)
        Column(Modifier.padding(start = TvSpec.LEFT, top = 128.dp).width(980.dp)) {
            TvKick(listOfNotNull("合集", "${data.count} 部".takeIf { data.count > 0 }, data.years, totalRuntime(data.runtimeSec)).joinToString(" · "))
            val tier = tvTitleTier(set.name)
            val size = TvDetailSpec.TITLE_TIERS[tier]
            Box(Modifier.padding(top = 14.dp, bottom = 18.dp).height(if (tier == 3) 110.dp else 124.dp), contentAlignment = Alignment.BottomStart) {
                BasicText(set.name, Modifier.testTag("tv-title-text"), style = Guga.song(size, FontWeight.Black, lineHeight = size * 1.12f, letterSpacing = 0.04.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (set.overview.isNotEmpty()) {
                BasicText(set.overview, Modifier.height(132.dp), style = Guga.song(28.sp, FontWeight.SemiBold, lineHeight = 43.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else Spacer(Modifier.height(20.dp))
            TvGroup("cta", listOf("cta-play", "cta-fav", "cta-played"), default = "cta-play")
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                val start = data.startWith
                TvActionButton(
                    "cta-play", if (start != null && start.id != members.firstOrNull()?.id) "接着看《${start.name.take(8)}》" else "从第一部看起",
                    { start?.let { if (it.kind == Kind.Movie) graph.play(it.id, it.positionSec?.times(1000)) else graph.open(it.id) } },
                    icon = GuguIcon.Play, primary = true, default = true,
                )
                TvActionButton("cta-fav", "收藏", { graph.toggleFavorite(chrome, set.id, fav) }, icon = GuguIcon.Heart) { TvSealSlot(fav, it) }
                val all = members.isNotEmpty() && members.all { chrome.isPlayed(it.id, it.played) }
                TvActionButton("cta-played", if (all) "全部已看" else "全部标记已看", { for (m in members) if (chrome.isPlayed(m.id, m.played) == all) graph.togglePlayed(chrome, m.id, all) }, icon = GuguIcon.Check)
            }
        }
        Column(Modifier.padding(top = 566.dp).fillMaxWidth()) {
            TvSecHead("本合集", "IN THIS SET", Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT), trailing = if (data.count > 0) "${data.count} 部" else null)
            Spacer(Modifier.height(24.dp))
            when {
                load.value == null && load.error != null -> ErrorView(load.error!!, load::retry, Modifier.padding(start = TvSpec.LEFT))
                members.isEmpty() -> groups.remove("members")
                else -> TvLeadRow("members", members, { "member-${it.id}" }, TvSpec.POSTER, 430.dp, visible = 8) { m, key, _ ->
                    TvPosterCard(
                        m, key, { graph.open(m.id) },
                        subtitle = listOfNotNull(m.year?.toString(), m.runtimeSec?.let { "${((it + 30) / 60)} 分钟" }).joinToString(" · "),
                        number = (members.indexOf(m) + 1).toString(),
                        onLongClick = { TvLongPressMenu.open(m) },
                        favorite = chrome.isFavorite(m),
                    )
                }
            }
        }
    }
}

@Composable
fun TvPersonScreen(id: String, today: Today) {
    val load = rememberLoad("tv-person", id) { loadPerson(it, id) }
    val data = load.value
    when {
        data != null -> TvPerson(data, today)
        load.error != null -> TvPage("", "PROFILE", today) { ErrorView(load.error!!, load::retry) }
        else -> TvPage("", "PROFILE", today) {}
    }
}

/**
 * 人物页（电视，设计稿 `p=person`）：**彩色**大照片从右边整幅铺到屏幕边（x 1180 起），往左渐隐进纸面；名字（宋体 124）、统计压在左边；
 * 服务器上没有生平 → 名字下面那一格由她补一句（不编生平）。默认焦点在第一部作品（按年份，新的在前）。
 * 作品 ≤ 3 部（`few`）：换成一张大卡（续看 / 详情）+ 同剧的人。
 */
@Composable
fun TvPerson(data: PersonData, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val p = data.person
    val groups = remember { TvGroups() }
    LaunchedEffect(Unit) { chrome.spot = null }
    val works = data.works
    val few = works.size in 1..3
    groups.edge = { from, dir -> if (few) twoRowEdge(from, dir, "cta", "costars", groups::has) else if (dir == TvDir.Left) null else TvGroups.STAY }

    TvGroupsRoot(groups, Modifier.testTag(if (few) "tv-person-few" else "tv-person")) {
        // 肖像：右边 740 宽，左缘渐隐
        val photo = rememberImage(p.art.poster, keepPrevious = true)
        val cf = rememberCrossfade(photo)
        Canvas(Modifier.align(Alignment.TopEnd).width(740.dp).fillMaxHeight().testTag("tv-person-photo")) {
            cf.current?.let { drawCover(it, Framing(0.5f, 0.12f), cf.alpha) }
            val pc = colors.paper
            drawRect(Brush.horizontalGradient(0f to pc, 0.16f to pc.copy(alpha = 0.5f), 0.40f to Color.Transparent, 1f to Color.Transparent))
            drawRect(Brush.verticalGradient(0f to pc.copy(alpha = 0.8f), 0.15f to Color.Transparent, 0.74f to Color.Transparent, 1f to pc.copy(alpha = 0.7f)))
        }
        TvRunHead("PROFILE", null, today)
        Column(Modifier.padding(start = TvSpec.LEFT, top = 128.dp).width(900.dp)) {
            TvKick("人物 · ${data.roles}")
            // 长的外文名降一号、两行，不裁字
            val long = Rules.titleUnits(p.name) > 6
            BasicText(
                p.name,
                Modifier.padding(top = 6.dp, bottom = 14.dp).testTag("tv-person-name"),
                style = Guga.song(if (long) 72.sp else 124.sp, FontWeight.Black, lineHeight = if (long) 84.sp else 136.sp, letterSpacing = if (long) 0.em else 0.06.em),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(works.size.toString(), style = Guga.numeral(TvDetailSpec.RATING), maxLines = 1)
                BasicText("部\n在库", Modifier.padding(start = 14.dp), style = Guga.song(18.sp, color = colors.dim, lineHeight = 24.sp), maxLines = 2)
                val span = data.years?.let { (a, b) -> if (a == b) "$a" else "$a — $b" }
                val best = works.mapNotNull { w -> w.title.rating?.toDoubleOrNull()?.let { r -> r to w.title } }.maxByOrNull { it.first }?.second
                BasicText(
                    listOfNotNull(span, best?.let { "最高分《${it.name}》★ ${it.rating}" }).joinToString("　·　"),
                    Modifier.padding(start = 32.dp),
                    style = Guga.song(25.sp, FontWeight.SemiBold, colors.dim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 她的格子：服务器没有生平时补一句（不编生平）；有生平就排生平的第一句
            Box(
                Modifier.padding(top = 24.dp).width(860.dp).height(92.dp).drawBehind {
                    drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (p.overview.isEmpty()) {
                    EditorNote(if (few) "资料卡是空的，片库里也只有${if (works.size == 1) "这一部" else "这几部"}。" else "服务器上没有${p.name}的生平，下面只列你片库里有的作品。", label = "放映手记", english = "SCREENING NOTES", photo = 56.dp)
                } else {
                    BasicText(tvFirstSentence(p.overview, 60), style = Guga.song(26.sp, color = colors.dim), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (!few) {
            groups.remove("cta"); groups.remove("costars")
            Column(Modifier.padding(top = 584.dp).width(1160.dp).fillMaxHeight()) {
                TvSecHead("作品", "IN YOUR LIBRARY", Modifier.padding(start = TvSpec.LEFT, end = 20.dp), trailing = "按年份 · ${works.size} 部")
                Spacer(Modifier.height(24.dp))
                if (works.isNotEmpty()) {
                    TvLeadRow("works", works, { "work-${it.title.id}" }, TvSpec.POSTER, 430.dp, default = "work-${works.first().title.id}", visible = 4) { w, key, _ ->
                        TvPosterCard(
                            w.title, key, { graph.open(w.title.id) },
                            subtitle = listOfNotNull(w.title.year?.toString(), w.role).joinToString(" · "),
                            onLongClick = { TvLongPressMenu.open(w.title) },
                            default = key == "work-${works.first().title.id}",
                        )
                    }
                }
            }
        } else {
            groups.remove("works")
            val w = works.first()
            // 同剧的人要这部作品完整的演职员表（作品列表里每部只带这个人自己）
            val full = rememberLoad("tv-person-work", w.title.id) { c -> c.titleOf(c.getItem(w.title.id)) }
            val d = full.value ?: w.title
            Column(Modifier.padding(start = TvSpec.LEFT, top = 520.dp).width(1000.dp)) {
                TvSecHead("作品", "IN YOUR LIBRARY", trailing = if (works.size == 1) "只有 1 部" else "${works.size} 部")
                Row(Modifier.padding(top = 22.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Box(Modifier.width(160.dp).height(240.dp).border(1.dp, colors.line)) { CoverImage(d.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) }
                    Column(Modifier.weight(1f)) {
                        TvTitle(d, small = true, width = 360.dp, height = 76.dp)
                        TvKick(listOfNotNull(w.role, tvKindName(d.kind), d.year?.toString()).joinToString(" · "), Modifier.padding(top = 16.dp, bottom = 8.dp))
                        val lead = tvFirstSentence(d.overview, 60)
                        if (lead.isNotEmpty()) BasicText(lead, Modifier.padding(bottom = 16.dp), style = Guga.song(25.sp, FontWeight.SemiBold, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TvGroup("cta", listOf("cta-play", "cta-info"), default = "cta-play")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            TvActionButton(
                                "cta-play", if (d.kind == Kind.Movie) (if (d.progress > 0f) "续看" else "播放") else "去看",
                                { if (d.kind == Kind.Movie) graph.play(d.id, d.positionSec?.times(1000)) else graph.open(d.id) },
                                icon = GuguIcon.Play, primary = true, default = true,
                                sub = if (d.kind == Kind.Movie && d.progress > 0f) Rules.remaining(d.progress, d.runtimeSec) else null, progress = if (d.kind == Kind.Movie) d.progress else 0f,
                            )
                            TvActionButton("cta-info", "详情", { graph.open(d.id) }, icon = GuguIcon.Info)
                        }
                    }
                }
                // 同剧的人：这部作品的其他演职员（真实数据）
                val others = d.people.filter { it.id != p.id }.distinctBy { it.id }.take(5)
                if (others.isNotEmpty()) {
                    TvSecHead("同剧的人", "CO-STARS", small = true)
                    TvGroup("costars", others.map { "costar-${it.id}" })
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        for (o in others) {
                            TvChip("costar-${o.id}", o.name, false, { graph.open(o.id) }, height = 76.dp, leading = {
                                Box(Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF2A251C))) { CoverImage(o.photo, Modifier.fillMaxSize(), Framing(0.5f, 0.2f)) }
                            })
                        }
                    }
                } else groups.remove("costars")
            }
        }
    }
}
