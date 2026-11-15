package com.gugaart.ui.tv.system

import com.gugaart.ui.screens.me.Specials
import com.gugaart.ui.screens.me.RankBoard
import com.gugaart.ui.screens.me.RankBadge
import com.gugaart.ui.screens.me.countWatched
import com.gugaart.ui.screens.me.Ranks
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPose
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.favs.loadFavs
import com.gugaart.ui.screens.history.historyLine
import com.gugaart.ui.screens.history.loadHistory
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvLongPressMenu
import com.gugaart.ui.tv.TvPageHeader
import com.gugaart.ui.tv.TvSpec
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvGroupsRoot
import com.gugaart.ui.tv.detail.TvLeadRow
import com.gugaart.ui.tv.detail.TvPosterCard
import com.gugaart.ui.tv.detail.TvSecHead
import com.gugugaga.core.FAVORITE_SORTS
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon

/** 记录 / 收藏右边分哪几栏：（栏名, 英文, 条目）。空的栏不出现。纯函数，有单测。 */
fun meShelves(favorites: Boolean, items: List<Title>): List<Triple<String, String, List<Title>>> = (
    if (favorites) listOf(
        Triple("片子", "TITLES", items.filter { it.kind == Kind.Movie || it.kind == Kind.Series || it.kind == Kind.BoxSet }),
        Triple("单集", "EPISODES", items.filter { it.kind == Kind.Episode }),
        Triple("人物", "PEOPLE", items.filter { it.kind == Kind.Person }),
    ) else listOf(
        Triple("看到一半", "IN PROGRESS", items.filter { it.progress > 0f }),
        Triple("看完的", "FINISHED", items.filter { it.progress <= 0f }),
    )
    ).filter { it.third.isNotEmpty() }

/** 记录 / 收藏的方向键路径：右边几栏上下换；最左再按左 = 读者证下面的「记录 / 收藏」两行；再按左交给外壳（进导航）。纯函数，有单测。 */
fun meEdge(from: String, dir: TvDir, shelves: Int, has: (String) -> Boolean): String? = when {
    from == "tabs" -> when (dir) {
        TvDir.Right -> (0 until shelves).map { "shelf-$it" }.firstOrNull(has) ?: TvGroups.STAY
        TvDir.Left -> null
        else -> TvGroups.STAY
    }
    else -> {
        val i = from.removePrefix("shelf-").toIntOrNull() ?: 0
        when (dir) {
            TvDir.Left -> "tabs"
            TvDir.Right -> TvGroups.STAY
            TvDir.Up -> (i - 1 downTo 0).map { "shelf-$it" }.firstOrNull(has) ?: TvGroups.STAY
            TvDir.Down -> (i + 1 until shelves).map { "shelf-$it" }.firstOrNull(has) ?: TvGroups.STAY
        }
    }
}

/**
 * 记录 / 收藏 + 读者证（电视，设计稿 `p=me`）：左栏读者证（署名照 120 + 账号 + 服务器）和「记录 / 收藏」两行，右边按栏排的横排。
 * 默认焦点在右边第一张卡。数据和手机同一批（`loadHistory` / `loadFavs`，只读）。
 * 设计稿把记录按「今天 / 昨天 / 更早」分栏——那是编的（服务器给的继续观看没有日期），这里按「看到一半 / 看完的」分；收藏按 片子 / 单集 / 人物 分。
 * 收藏为空：她捧白纸（G10）。长按确定 = 这一张的菜单（外壳画）。
 */
@Composable
fun TvMe(today: Today, tab: String?) {
    val g = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val groups = remember { TvGroups() }
    var favorites by remember { mutableStateOf(tab == "favorites") }
    val session by g.session.session.collectAsState()
    val history = rememberLoad("tv-history") { c -> loadHistory(c).map { it.title } }
    val favs = rememberLoad("tv-favs") { c -> loadFavs(c, FAVORITE_SORTS.first().key).items }
    // 看过几部（电影一部、剧集一季算一部）→ 读者证上的称号
    val watched = rememberLoad("tv-watched", session?.userId) { c -> countWatched(c).total }
    LaunchedEffect(Unit) { chrome.spot = null }
    val load = if (favorites) favs else history
    val items = load.value.orEmpty().let { list -> if (favorites) list.filter { chrome.isFavorite(it) } else list }
    val shelves = meShelves(favorites, items)
    groups.edge = { from, dir -> meEdge(from, dir, shelves.size, groups::has) }
    // 焦点到「记录 / 收藏」哪一行，右边就换成哪一份
    val cur = groups.current
    LaunchedEffect(cur) {
        if (cur == "tab-history" && favorites) favorites = false
        if (cur == "tab-favorites" && !favorites) favorites = true
    }

    TvGroupsRoot(groups, Modifier.testTag(if (favorites) "tv-me-fav" else "tv-me")) {
        Column(Modifier.fillMaxSize()) {
            TvPageHeader(if (favorites) "收藏" else "记录", if (favorites) "FAVORITES" else "HISTORY", today, trailing = "${items.size} 条".takeIf { items.isNotEmpty() })
            Row(Modifier.fillMaxSize().padding(top = 24.dp)) {
                // ---- 读者证 ----
                Column(Modifier.padding(start = TvSpec.LEFT).width(400.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 22.dp).drawBehind { drawLine(colors.ink, Offset(0f, size.height + 22.dp.toPx()), Offset(size.width, size.height + 22.dp.toPx()), 2.dp.toPx()) }, verticalAlignment = Alignment.CenterVertically) {
                        if (LocalGugaPrefs.current.showMascot) Byline(120.dp, live = false)
                        Column(Modifier.padding(start = 22.dp)) {
                            BasicText("MEMBER CARD", style = Guga.numeral(16.sp, colors.spot, letterSpacing = 0.14.em), maxLines = 1)
                            BasicText("观影卡", style = Guga.song(46.sp, FontWeight.Black, letterSpacing = 0.14.em), maxLines = 1)
                            BasicText(session?.userName.orEmpty(), style = Guga.song(25.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(30.dp))
                    val rank = watched.value?.let { n -> listOf("称号" to (Ranks.of(Ranks.levelOf(n))?.let { "第 ${it.level} 级 · ${it.title}" } ?: "还没有"), "看过" to "$n 部", "特别称号" to "${RankBoard.owned.size} / ${Specials.COUNT}") }.orEmpty()
                    for ((k, v) in listOfNotNull(session?.serverName?.let { "服务器" to it }) + rank + listOfNotNull(history.value?.let { "记录" to "${it.size} 条" }, favs.value?.let { "收藏" to "${it.size} 条" })) {
                        Row(Modifier.fillMaxWidth().height(52.dp).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, verticalAlignment = Alignment.CenterVertically) {
                            BasicText(k, Modifier.weight(1f), style = Guga.song(25.sp, color = colors.dim), maxLines = 1)
                            // 称号那一行带这一级的徽章
                            if (k == "称号") watched.value?.let { RankBadge(Ranks.levelOf(it), 44.dp, Modifier.padding(end = 10.dp)) }
                            BasicText(v, style = Guga.song(25.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    TvGroup("tabs", listOf("tab-history", "tab-favorites"), horizontal = false, default = if (favorites) "tab-favorites" else "tab-history")
                    for ((key, label, icon) in listOf(Triple("tab-history", "记录", GuguIcon.History), Triple("tab-favorites", "收藏", GuguIcon.Heart))) {
                        val on = (key == "tab-favorites") == favorites
                        TvBlockFocus(key, { groups.enter("shelf-0", g.focus) }, Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, label = label) { focused ->
                            val ink = if (focused) colors.onInk else if (on) colors.spot else colors.ink
                            Row(Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, ink, 30.dp)
                                BasicText(label, Modifier.padding(start = 20.dp), style = Guga.song(28.sp, FontWeight.Bold, ink), maxLines = 1)
                            }
                        }
                    }
                }
                // ---- 右边：按栏排的横排 ----
                Column(Modifier.padding(start = 64.dp).fillMaxSize()) {
                    when {
                        load.value == null && load.error != null -> ErrorView(load.error!!, load::retry)
                        load.value == null -> Unit
                        shelves.isEmpty() -> Row(Modifier.padding(top = 180.dp, end = TvSpec.RIGHT).testTag("tv-me-empty"), verticalAlignment = Alignment.CenterVertically) {
                            if (LocalGugaPrefs.current.showMascot) MascotPose("G10", 150.dp, label = "捧白纸")
                            Column(Modifier.padding(start = 26.dp)) {
                                BasicText(if (favorites) "还没有收藏" else "还没有看过的", style = Guga.song(38.sp, FontWeight.Black), maxLines = 1)
                                BasicText(if (favorites) "在详情页的「收藏」上按确定，片子就放进这里。" else "看过的片子都在这里。", Modifier.padding(top = 8.dp), style = Guga.song(28.sp, color = colors.dim), maxLines = 1)
                            }
                        }
                        else -> shelves.forEachIndexed { i, (name, english, list) ->
                            TvSecHead(name, english, Modifier.padding(end = TvSpec.RIGHT), trailing = "${list.size}")
                            Spacer(Modifier.height(20.dp))
                            TvLeadRow("shelf-$i", list, { "me-$i-${it.id}" }, 190.dp, if (shelves.size > 1) 400.dp else 430.dp, start = 0.dp, gap = 28.dp, visible = 6, default = list.firstOrNull()?.let { "me-$i-${it.id}" }) { t, key, _ ->
                                TvPosterCard(
                                    t.copy(name = t.displayName), key,
                                    { if (t.kind == Kind.Episode || (t.kind == Kind.Movie && t.progress > 0f)) g.play(t.id, t.positionSec?.times(1000)) else g.open(t.id) },
                                    subtitle = if (favorites) listOfNotNull(t.year?.toString(), t.episodeNo?.let { "第 $it 集" }).joinToString(" · ") else historyLine(t),
                                    onLongClick = { TvLongPressMenu.open(t) },
                                    favorite = favorites,
                                    default = i == 0 && t == list.first(),
                                    width = 190.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
