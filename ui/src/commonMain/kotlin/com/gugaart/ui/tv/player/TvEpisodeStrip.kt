package com.gugaart.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.player.PlayerEpisodes
import com.gugaart.ui.player.seasonWord
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvSpec
import com.gugaart.ui.tv.detail.LocalTvGroups
import com.gugaart.ui.tv.detail.TvChip
import com.gugaart.ui.tv.detail.TvChipRow
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvEpisodeCard
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvLeadRow
import com.gugaart.ui.tv.detail.tvEpisodeSub
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.ui.LocalGraph

/*
 * 电视播放器的选集（用户 2026-11-15：「选集的部分没地方选集，这部分设计实现下」「选季不是集」）。
 *
 * 原来的「选集」是页边面板里的一行，左右一格一格挑「第 N 集」，没有季、也没有剧照。现在在那一行按确定（或遥控器的选集键）
 * = 画面下半出一条选集带：最上一排季（多季才有），一季超过 50 集再一排集号分页，下面是和详情页同一种分集卡（剧照、进度、已看），
 * 焦点停在正在放的那一集。左右走、上 = 分页 / 季（焦点到哪季分集就换成哪季）、确定 = 换到这一集、返回 = 收起。画面照常在后面放。
 */

/** 选集带的方向键路径（组：`ps-seasons` 季、`ps-pages` 分页、`ps-eps` 分集）。纯函数，有单测。 */
fun tvStripEdge(from: String, dir: TvDir, has: (String) -> Boolean): String = when (from) {
    "ps-seasons" -> if (dir == TvDir.Down) listOf("ps-pages", "ps-eps").firstOrNull(has) ?: TvGroups.STAY else TvGroups.STAY
    "ps-pages" -> when (dir) {
        TvDir.Up -> if (has("ps-seasons")) "ps-seasons" else TvGroups.STAY
        TvDir.Down -> if (has("ps-eps")) "ps-eps" else TvGroups.STAY
        else -> TvGroups.STAY
    }
    "ps-eps" -> if (dir == TvDir.Up) listOf("ps-pages", "ps-seasons").firstOrNull(has) ?: TvGroups.STAY else TvGroups.STAY
    else -> TvGroups.STAY
}

/** 选集带：画面下半一块墨色渐变，上面季 / 分页 / 分集卡。[onPlay] = 换到这一集。 */
@Composable
fun BoxScope.TvEpisodeStrip(eps: PlayerEpisodes, snap: PlayerSnapshot, onPlay: (String) -> Unit) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val chrome = LocalChrome.current
    val seriesStills = com.gugaart.ui.screens.detail.rememberItemStills(eps.seriesId)
    Box(
        Modifier.align(Alignment.BottomStart).fillMaxWidth().height(760.dp)
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.22f to TvPlayerSpec.DARK.copy(alpha = 0.82f), 0.5f to TvPlayerSpec.DARK.copy(alpha = 0.95f), 1f to TvPlayerSpec.DARK)),
    )
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 54.dp).testTag("tv-episode-strip")) {
        Row(Modifier.fillMaxWidth().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BasicText("选集", style = Guga.song(44.sp, FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
            BasicText("EPISODES", Modifier.padding(bottom = 8.dp), style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            val count = eps.slice?.total?.let { "共 $it 集" }
            listOfNotNull(eps.season?.let(::seasonWord), count).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                BasicText(it, Modifier.padding(bottom = 6.dp), style = Guga.song(26.sp, FontWeight.SemiBold, colors.dim), maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(22.dp))
        if (eps.seasons.size > 1) {
            val keys = eps.seasons.map { "ps-season-${it.id}" }
            TvChipRow("ps-seasons", keys, default = eps.seasonId?.let { "ps-season-$it" }) { i, key ->
                val s = eps.seasons[i]
                TvChip(key, seasonWord(s), s.id == eps.seasonId, { groups.enter("ps-eps", g.focus) }, count = s.episodes?.toString())
            }
            Spacer(Modifier.height(18.dp))
        } else {
            groups.remove("ps-seasons")
        }
        val pages = eps.pages
        if (pages.isNotEmpty()) {
            TvChipRow("ps-pages", pages.map { "ps-page-${it.index}" }, default = "ps-page-${eps.page}") { i, key ->
                TvChip(key, pages[i].label.replace("–", " – "), pages[i].index == eps.page, { groups.enter("ps-eps", g.focus) }, numeral = true)
            }
            Spacer(Modifier.height(18.dp))
        } else {
            groups.remove("ps-pages")
        }
        Spacer(Modifier.height(12.dp))
        val list = eps.episodes
        if (list.isEmpty()) {
            groups.remove("ps-eps")
            BasicText(
                when {
                    eps.loading -> "加载中…"
                    eps.failed -> "加载失败"
                    else -> "没有分集"
                },
                Modifier.padding(start = TvSpec.LEFT).height(320.dp),
                style = Guga.song(28.sp, color = colors.dim),
            )
        } else {
            val currentKey = "pe-${snap.itemId}".takeIf { k -> list.any { "pe-${it.id}" == k } }
            TvLeadRow("ps-eps", list, { "pe-${it.id}" }, TvSpec.EPISODE_W, 330.dp, default = currentKey, visible = 5) { e, key, _ ->
                val current = e.id == snap.itemId
                val played = chrome.isPlayed(e.id, e.played && e.progress <= 0f)
                TvEpisodeCard(e, key, played, current, subtitle = if (current) "播放中" else tvEpisodeSub(e, played, false), onClick = { if (!current) onPlay(e.id) }, onLongClick = {}, fallback = com.gugaart.ui.screens.detail.episodeStillFallback(e.episodeNo, seriesStills, e.art.thumb))
            }
        }
    }
}
