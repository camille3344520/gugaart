package com.gugaart.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Season
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/**
 * 手机 / 电脑播放器的「选集」面板：上面一排**季**（多季才有，火影忍者、航海王这种），一季超过 50 集再一排**集号分页**，下面每一集一行：
 * 剧照 + 集号 + 集名 + 时长 / 看到哪 / 已看；正在放的那一集朱红。点一集 = 换过去（返回栈里只留一个播放器）。
 * 数据是 [LocalPlayerEpisodes]（按季按页取，和详情页同一种）；不是剧集里的一集时退回上游那一串（只有集名）。
 */
@Composable
fun EpisodePanel(snap: PlayerSnapshot) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val t = Guga.player
    val eps = LocalPlayerEpisodes.current
    Column(Modifier.fillMaxSize().testTag("panel-eps")) {
        val kicker = eps?.season?.let { s -> s.number?.let { "第 $it 季 · EPISODES" } } ?: PlayerPanel.Eps.english
        PanelHeader(PlayerPanel.Eps, kicker = kicker)
        if (eps == null) {
            // 不是一集（或者还没登录）：上游那一串
            val current = snap.episodes.indexOfFirst { it.id == snap.itemId }.coerceAtLeast(0)
            val state = rememberLazyListState((current - 2).coerceAtLeast(0))
            LazyColumn(Modifier.fillMaxSize(), state = state) {
                items(snap.episodes.size, key = { snap.episodes[it].id }) { i ->
                    val e = snap.episodes[i]
                    PanelOption(e.name, e.id == snap.itemId, { if (e.id != snap.itemId) g.playEpisode(e.id) }, number = Rules.episodeNo(e.indexNumber ?: (i + 1)))
                }
            }
            return@Column
        }
        if (eps.seasons.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp * t.k).testTag("panel-eps-seasons"),
                horizontalArrangement = Arrangement.spacedBy(6.dp * t.k),
            ) {
                for (s in eps.seasons) PanelChip(seasonWord(s), s.id == eps.seasonId, "eps-season-${s.id}") { eps.pickSeason(s.id) }
            }
        }
        val pages = eps.pages
        if (pages.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp * t.k).testTag("panel-eps-pages"),
                horizontalArrangement = Arrangement.spacedBy(6.dp * t.k),
            ) {
                for (p in pages) PanelChip(p.label, p.index == eps.page, "eps-page-${p.index}") { eps.pickPage(p.index) }
            }
        }
        val list = eps.episodes
        when {
            list.isEmpty() -> BasicText(
                when {
                    eps.loading -> "正在取这一季的分集……"
                    eps.failed -> "这一季的分集没取到，稍后再打开试试。"
                    else -> "这一季还没有分集。"
                },
                Modifier.padding(top = 12.dp * t.k),
                style = Guga.song(t.body, color = colors.dim),
            )
            else -> {
                val current = list.indexOfFirst { it.id == snap.itemId }
                val state = rememberLazyListState((current - 1).coerceAtLeast(0))
                // 换季 / 翻页以后回到顶上（正在看的那一集在这一页就停在它附近）
                LaunchedEffect(eps.seasonId, eps.page) { state.scrollToItem((list.indexOfFirst { it.id == snap.itemId } - 1).coerceAtLeast(0)) }
                LazyColumn(Modifier.fillMaxSize().testTag("panel-eps-list"), state = state) {
                    items(list.size, key = { list[it].id }) { i ->
                        val e = list[i]
                        EpisodeRow(e, e.id == snap.itemId) { if (e.id != snap.itemId) g.playEpisode(e.id) }
                    }
                }
            }
        }
    }
}

/** 季的叫法：有季号「第 N 季」，没有就用季名（乱码也原样，不替用户改名）。 */
fun seasonWord(s: Season): String = if (s.number != null && s.number > 0) "第 ${s.number} 季" else s.name.ifBlank { "特别篇" }

/** 一集一行：剧照（带进度线）+ 集号 + 集名 + 副行。 */
@Composable
private fun EpisodeRow(e: Title, current: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val t = Guga.player
    val chrome = LocalChrome.current
    val played = chrome.isPlayed(e.id, e.played && e.progress <= 0f)
    val press = rememberPressState()
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp * t.k).pressGraphics(press).pressable(press, label = e.name, onClick = onClick)
                .padding(vertical = 6.dp * t.k).testTag(if (current) "panel-ep-current" else "panel-ep"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp * t.k),
        ) {
            Box(Modifier.width(88.dp * t.k).height(49.5.dp * t.k).background(colors.inkWell).then(if (current) Modifier.border(2.dp, colors.spot) else Modifier)) {
                val still = e.art.still ?: e.art.thumb ?: e.art.backdrop
                if (still != null) CoverImage(still, Modifier.fillMaxSize(), Framing(0.5f, 0.3f))
                if (e.progress > 0f) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(colors.paper.copy(alpha = 0.6f)))
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(e.progress.coerceIn(0f, 1f)).height(3.dp).background(colors.spot))
                }
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp * t.k)) {
                    e.episodeNo?.let { BasicText(Rules.episodeNo(it), style = Guga.numeral(t.number, if (current) colors.spot else colors.dim), maxLines = 1) }
                    BasicText(
                        e.name.ifEmpty { e.episodeNo?.let { "第 $it 集" } ?: "" },
                        style = Guga.song(t.option, if (current) FontWeight.Bold else FontWeight.Normal, if (current) colors.spot else colors.ink),
                        // 手机的面板窄：集名折成两行，不剩「江心…」
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val sub = listOfNotNull(
                    "正在看".takeIf { current },
                    e.runtimeSec?.let { Rules.minutesOnly(it) },
                    e.pct?.takeIf { !current }?.let { "看到 $it%" } ?: "已看".takeIf { played && !current },
                ).joinToString(" · ")
                if (sub.isNotEmpty()) BasicText(sub, Modifier.padding(top = 2.dp), style = Guga.note(t.note, if (current) colors.spot else colors.faint), maxLines = 1)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

/** 季 / 分页的一格：细线框；选中 = 米白实底墨字。 */
@Composable
private fun PanelChip(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val t = Guga.player
    val press = rememberPressState()
    Box(
        Modifier.pressGraphics(press).pressable(press, label = text, onClick = onClick)
            .then(if (selected) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.dim))
            .heightIn(min = 30.dp * t.k).padding(horizontal = 10.dp * t.k).testTag(if (selected) "$tag-on" else tag),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Guga.song(t.label, FontWeight.Bold, if (selected) colors.onInk else colors.ink), maxLines = 1)
    }
}

