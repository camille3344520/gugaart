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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.gugaart.ui.screens.me.SpecialBadge
import com.gugaart.ui.screens.me.SpecialNews
import com.gugaart.ui.screens.me.Specials
import com.gugaart.ui.screens.me.specialHead
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvGroupsRoot
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.LocalBackDispatcher

/**
 * 特别称号纸片（电视）：和称号纸片同一种米白纸条。一两个 = 每个一行（大盾 + 名字 + 怎么拿 + 一句话）；
 * 第一次检查一下拿到好几个 = 盾排成一排排格子。只有「收下」一个按钮；确定 / 返回 = 收起，焦点还给原来那一格。
 */
@Composable
fun TvSpecialSlip(news: SpecialNews) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val back = LocalBackDispatcher.current
    val groups = remember { TvGroups() }
    val returnTo = remember { g.focus.lastFocusedKey }
    val owned = RankBoard.owned
    fun close() {
        RankBoard.special = null
        returnTo?.let { g.focus.request(it) }
    }
    // 一下拿到很多：一页三排八枚，左右键翻页（用户 2026-11-15：「可以左右滑 分页，不要占太大界面」）
    val pages = remember(news) { news.ids.mapNotNull(Specials::of).chunked(TV_SPECIAL_PAGE) }
    var page by remember(news) { mutableIntStateOf(0) }
    groups.edge = { _, dir ->
        when (dir) {
            TvDir.Left -> if (page > 0) page--
            TvDir.Right -> if (page < pages.size - 1) page++
            else -> Unit
        }
        TvGroups.STAY
    }
    LaunchedEffect(news) { groups.enterWhenReady("spc", g.focus) }
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
    val list = news.ids.mapNotNull(Specials::of)
    TvGroupsRoot(groups, Modifier.background(Color(0xFF0A0907).copy(alpha = 0.8f)).testTag("tv-special-slip")) {
        Column(Modifier.padding(start = 400.dp, top = 140.dp).width(1120.dp).background(paper).drawBehind { drawRect(colors.seal, size = androidx.compose.ui.geometry.Size(size.width, 12.dp.toPx())) }) {
            Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                BasicText("特别称号", Modifier.background(colors.seal).padding(start = 24.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), style = Guga.song(36.sp, FontWeight.Black, Color.White, letterSpacing = 0.3.em), maxLines = 1)
                BasicText("SPECIAL · 观影称号", style = Guga.note(22.sp, red, letterSpacing = 0.18.em), maxLines = 1)
                Spacer(Modifier.weight(1f))
                BasicText("已拿到 ${owned.size.coerceAtLeast(news.owned)} / ${Specials.COUNT}", style = Guga.song(24.sp, FontWeight.Bold, ink.copy(alpha = 0.7f)), maxLines = 1)
            }
            BasicText(specialHead(news, owned), Modifier.padding(start = 48.dp, top = 22.dp), style = Guga.song(26.sp, FontWeight.Bold, red), maxLines = 1)
            if (list.size <= 2) {
                for (t in list) {
                    Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpecialBadge(t.id, 170.dp)
                        Column(Modifier.padding(start = 30.dp)) {
                            BasicText(t.title, Modifier.testTag("tv-special-title"), style = Guga.song(56.sp, FontWeight.Black, ink, letterSpacing = 0.06.em), maxLines = 1)
                            BasicText(t.how, Modifier.padding(top = 6.dp), style = Guga.song(28.sp, FontWeight.Bold, ink.copy(alpha = 0.75f)), maxLines = 1)
                            BasicText(t.line, Modifier.padding(top = 6.dp), style = Guga.song(26.sp, color = ink.copy(alpha = 0.6f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            } else {
                Column(Modifier.padding(start = 48.dp, end = 48.dp, top = 18.dp).testTag("tv-special-page-$page"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // 一下拿到很多（第一次检查）：一排八枚、小一号，一页最多三排，多了左右键翻页；纸片还在一屏里
                    val perRow = if (list.size > 12) 8 else 6
                    val badge = if (list.size > 12) 84.dp else 110.dp
                    val shown = pages.getOrElse(page) { emptyList() }
                    val rowCount = (minOf(list.size, TV_SPECIAL_PAGE) + perRow - 1) / perRow
                    val chunks = shown.chunked(perRow)
                    for (row in chunks) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (t in row) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    SpecialBadge(t.id, badge)
                                    BasicText(t.title, Modifier.padding(top = 6.dp), style = Guga.song(if (list.size > 12) 21.sp else 24.sp, FontWeight.Bold, ink), maxLines = 1)
                                }
                            }
                            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    // 最后一页补空排，翻页时纸片不变高矮
                    repeat((rowCount - chunks.size).coerceAtLeast(0)) { Spacer(Modifier.height(badge + 6.dp + 30.dp)) }
                    if (pages.size > 1) {
                        BasicText("‹  第 ${page + 1} / ${pages.size} 页  ›", Modifier.align(Alignment.CenterHorizontally).testTag("tv-special-pages"), style = Guga.song(22.sp, FontWeight.Bold, ink.copy(alpha = 0.6f)), maxLines = 1)
                    }
                }
            }
            Row(
                Modifier.padding(horizontal = 48.dp).padding(top = 26.dp).fillMaxWidth().drawBehind { drawLine(ink, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) }.padding(top = 22.dp, bottom = 34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("特别称号看「怎么看的」：凌晨看、连着看、二刷、看恐怖片……「我的」里都列着。", Modifier.weight(1f), style = Guga.song(21.sp, color = ink.copy(alpha = 0.6f)), maxLines = 2)
                TvGroup("spc", listOf("spc-ok"), default = "spc-ok")
                TvBlockFocus("spc-ok", { close() }, Modifier.padding(start = 30.dp), label = "收下", kind = TvFocusKind.OnPaper) { focused ->
                    Box(Modifier.height(70.dp).then(if (focused) Modifier else Modifier.border(2.dp, ink.copy(alpha = 0.5f))).padding(horizontal = 34.dp), contentAlignment = Alignment.Center) {
                        BasicText(if (news.browse) "好" else "收下", style = Guga.song(27.sp, FontWeight.Bold, if (focused) paper else ink, letterSpacing = 0.06.em), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** 电视纸片一页最多几枚（三排 × 八枚）。 */
const val TV_SPECIAL_PAGE = 24
