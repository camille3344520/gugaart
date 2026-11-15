package com.gugaart.ui.tv.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.screens.me.RankBoard
import com.gugaart.ui.screens.me.RankNews
import com.gugaart.ui.screens.me.Ranks
import com.gugaart.ui.screens.me.RankBadge
import com.gugaart.ui.screens.me.rankHead
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvGroupsRoot
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.LocalBackDispatcher

/**
 * 称号纸片（电视）：和号外同一种米白纸条压在压暗的页面上（纸面语言，不是玻璃）。左边这一级的徽章和第几级，右边称号和那句话、
 * 看过几部、离下一级的进度条；下面两排共十格的称号表（一格：徽章 · 称号 · 几部；没拿到的徽章灰），现在这一级朱红实底。
 * 只有一个按钮「收下」，焦点一出来就在它上面；确定 / 返回 = 收起，焦点还给原来那一格。
 */
@Composable
fun TvRankSlip(news: RankNews) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val back = LocalBackDispatcher.current
    val groups = remember { TvGroups() }
    val returnTo = remember { g.focus.lastFocusedKey }
    val rank = Ranks.of(news.level)
    val (badge, head) = rankHead(news)
    fun close() {
        RankBoard.close()
        returnTo?.let { g.focus.request(it) }
    }
    groups.edge = { _, _ -> TvGroups.STAY }
    LaunchedEffect(news) { groups.enterWhenReady("rank", g.focus) }
    DisposableEffect(back) {
        val un = back.push {
            close()
            true
        }
        onDispose { un() }
    }
    val paper = colors.ink
    val ink = colors.onInk
    val red = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK
    TvGroupsRoot(groups, Modifier.background(Color(0xFF0A0907).copy(alpha = 0.8f)).testTag("tv-rank-slip")) {
        Column(Modifier.padding(start = 400.dp, top = 120.dp).width(1120.dp).background(paper).drawBehind { drawRect(colors.seal, size = androidx.compose.ui.geometry.Size(size.width, 12.dp.toPx())) }) {
            Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                BasicText(badge, Modifier.background(colors.seal).padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), style = Guga.song(36.sp, FontWeight.Black, Color.White, letterSpacing = 0.3.em), maxLines = 1)
                BasicText("RANK · 观影称号", style = Guga.note(22.sp, red, letterSpacing = 0.18.em), maxLines = 1)
                Spacer(Modifier.weight(1f))
                BasicText("看过 ${news.count} 部", style = Guga.song(24.sp, FontWeight.Bold, ink.copy(alpha = 0.7f)), maxLines = 1)
            }
            Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                // 这一级的徽章 + 第几级
                Column(Modifier.width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RankBadge(news.level, 196.dp)
                    BasicText("第 ${news.level} 级 / ${Ranks.MAX}", Modifier.padding(top = 4.dp), style = Guga.song(22.sp, FontWeight.Bold, ink.copy(alpha = 0.6f)), maxLines = 1)
                }
                Column(Modifier.weight(1f).drawBehind { drawLine(ink.copy(alpha = 0.25f), Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }.padding(start = 40.dp, top = 4.dp, bottom = 4.dp)) {
                    BasicText(head, style = Guga.song(24.sp, FontWeight.Bold, red), maxLines = 1)
                    BasicText(rank?.title ?: "还没有称号", Modifier.padding(top = 6.dp).testTag("tv-rank-title"), style = Guga.song(64.sp, FontWeight.Black, ink, letterSpacing = 0.06.em), maxLines = 1)
                    BasicText(rank?.line ?: "看完第一部，就是「${Ranks.ALL.first().title}」。", Modifier.padding(top = 8.dp), style = Guga.song(28.sp, FontWeight.SemiBold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // 进度条让字：「再看 137 部升「副主编」」整行写全
                        Box(Modifier.weight(1f).height(8.dp).background(ink.copy(alpha = 0.15f))) { Box(Modifier.fillMaxWidth(Ranks.progress(news.count)).height(8.dp).background(colors.seal)) }
                        BasicText(Ranks.nextLine(news.count), style = Guga.song(23.sp, color = ink.copy(alpha = 0.7f)), maxLines = 1)
                    }
                }
            }
            // 称号表：两排，每排五格
            Column(Modifier.padding(start = 48.dp, end = 48.dp, top = 22.dp).fillMaxWidth().testTag("tv-rank-ladder"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in Ranks.ALL.chunked(5)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (r in row) {
                            val reached = r.level <= news.level
                            val current = r.level == news.level
                            val text = if (current) Color.White else if (reached) ink else ink.copy(alpha = 0.38f)
                            Row(
                                Modifier.weight(1f).height(74.dp)
                                    .then(if (current) Modifier.background(colors.seal) else Modifier.border(2.dp, if (reached) ink.copy(alpha = 0.6f) else ink.copy(alpha = 0.18f)))
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RankBadge(r.level, 56.dp, locked = !reached)
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    BasicText(r.title, style = Guga.song(23.sp, FontWeight.Bold, text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    BasicText("${r.at} 部", style = Guga.song(17.sp, color = text.copy(alpha = if (current) 0.85f else 0.7f)), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 48.dp).padding(top = 24.dp).fillMaxWidth().drawBehind { drawLine(ink, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) }.padding(top = 22.dp, bottom = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(Ranks.RULE, Modifier.weight(1f), style = Guga.song(21.sp, color = ink.copy(alpha = 0.6f)), maxLines = 2)
                TvGroup("rank", listOf("rank-ok"), default = "rank-ok")
                TvBlockFocus("rank-ok", { close() }, Modifier.padding(start = 30.dp), label = "收下", kind = TvFocusKind.OnPaper) { focused ->
                    Box(Modifier.height(70.dp).then(if (focused) Modifier else Modifier.border(2.dp, ink.copy(alpha = 0.5f))).padding(horizontal = 34.dp), contentAlignment = Alignment.Center) {
                        BasicText(if (news.browse) "好" else "收下", style = Guga.song(27.sp, FontWeight.Bold, if (focused) paper else ink, letterSpacing = 0.06.em), maxLines = 1)
                    }
                }
            }
        }
    }
}
