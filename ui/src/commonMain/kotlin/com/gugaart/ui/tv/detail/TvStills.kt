package com.gugaart.ui.tv.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.detail.StillsOverlay
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvSpec
import com.gugugaga.app.model.Still
import com.gugugaga.app.ui.LocalGraph

/*
 * 电视详情页的剧照（用户 2026-11-15：「电视详情页的剧照也加上」）。
 *
 * 电视的详情页是「一屏一件事，上下键整屏翻」，剧照单独占最后一屏（演职人员 / 相似之后）：和手机、电脑一样排在信息之后，
 * 是看完信息再「翻画册」；已有的几屏和它们的方向键路径都不动。剧照少于 2 张时这一屏不存在（[com.gugaart.ui.screens.detail.showStills]）。
 *
 * 一排 880 × 495 的 16:9 卡片，焦点卡停在左起第一格（[TvLeadRow]），右边露出下一张的大半；按确定在根上看大图
 * （[StillsOverlay]：← → 翻页、返回关），关掉以后焦点回到刚才那一张。
 */

/** 这一屏的组名。 */
const val TV_STILLS = "stills"

object TvStillsSpec {
    /** 卡片 880 × 495（16:9）：左边距 136 + 一张 + 间距，下一张露出大半，告诉你还能往右翻。 */
    val W = 880.dp
    val H = 495.dp
    /** 卡片间距：焦点卡抬起来会盖掉 32 的缝，大图要宽一点。 */
    val GAP = 64.dp
    /** 卡片下面的「03 / 12」和图注。 */
    val CAPTION_H = 92.dp
}

/** 第几张：「03 / 12」。纯函数，有单测。 */
fun tvStillNumber(index: Int, count: Int): String =
    "${(index + 1).toString().padStart(2, '0')} / ${count.toString().padStart(2, '0')}"

@Composable
fun TvStillsScreen(title: Title, stills: List<Still>, today: Today) {
    val graph = LocalGraph.current
    val groups = LocalTvGroups.current
    val colors = Guga.colors
    // 看完大图回来：焦点回到刚才那一张（大图在根上，焦点离开了这一屏）
    val viewing = StillsOverlay.start != null
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(viewing) {
        if (viewing) {
            opened = true
        } else if (opened) {
            opened = false
            groups.enterWhenReady(TV_STILLS, graph.focus)
        }
    }
    Column(Modifier.fillMaxSize().testTag("tv-stills-screen")) {
        TvSectionTop(title, "剧照 ${stills.size} 张", today)
        TvSecHead("剧照", "STILLS", Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 72.dp), trailing = "${stills.size} 张")
        Spacer(Modifier.height(36.dp))
        val indexed = remember(stills) { stills.withIndex().toList() }
        TvLeadRow(TV_STILLS, indexed, { "still-${it.index}" }, TvStillsSpec.W, TvStillsSpec.H + TvStillsSpec.CAPTION_H, gap = TvStillsSpec.GAP, visible = 3) { (i, s), key, focused ->
            Column(Modifier.width(TvStillsSpec.W)) {
                TvCardFocus(key, { StillsOverlay.open(stills, i) }, label = "剧照 ${i + 1}") {
                    Box(Modifier.width(TvStillsSpec.W).height(TvStillsSpec.H).background(colors.inkWell).border(1.dp, colors.ink.copy(alpha = 0.16f)).testTag("tv-still-$i")) {
                        CoverImage(s.thumb, Modifier.fillMaxSize())
                    }
                }
                BasicText(
                    tvStillNumber(i, stills.size),
                    Modifier.padding(top = 16.dp),
                    style = Guga.numeral(24.sp, if (focused) colors.spot else colors.faint),
                    maxLines = 1,
                )
                s.caption?.takeIf { it.isNotBlank() }?.let {
                    BasicText(it, Modifier.padding(top = 4.dp), style = Guga.song(23.sp, FontWeight.SemiBold, if (focused) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
