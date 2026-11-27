package com.gugaart.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.player.DanmakuSearch
import com.gugaart.ui.player.DanmakuSeasonGuard
import com.gugaart.ui.player.candidatesSeasonFirst
import com.gugaart.ui.player.hitsSeasonFirst
import com.gugaart.ui.player.seasonAltQueries
import com.gugaart.ui.player.candidateMeta
import com.gugaart.ui.player.pickerHead
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.detail.LocalTvGroups
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.system.TvEditText
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.danmaku.match.DanmakuPhase
import kotlinx.coroutines.launch

/** 电视弹幕来源的几个数。 */
object TvDanmakuPickerSpec {
    val WIDTH = 860.dp
    /** 手动搜索的结果最多列几条（遥控器一条条往下走）。 */
    const val HITS = 12
}

/** 电视弹幕来源这一栏的焦点键（从上往下：重试 / 去设置、候选、搜索框、快捷词、重新自动匹配、搜索结果）。纯函数，有单测。 */
fun tvPickerKeys(phase: DanmakuPhase, candidates: Int, hits: Int, quick: Int = 0): List<String> = buildList {
    if (phase == DanmakuPhase.Unconfigured) {
        add("dmp-settings")
        return@buildList
    }
    if (phase == DanmakuPhase.Failed || phase == DanmakuPhase.Unrecognized) add("dmp-retry")
    repeat(candidates) { add("dmp-c-$it") }
    add("dmp-search")
    repeat(quick) { add("dmp-q-$it") }
    add("dmp-repick")
    repeat(hits) { add("dmp-h-$it") }
}

/**
 * 弹幕来源（电视）：画面照常全幅在后面放，左边一栏墨色的纸——分数最高的几个候选（第一个「推荐」）、手动搜索框（确定 = 弹输入法，
 * 输入法里按「搜索」= 搜）、重新自动匹配、搜索结果。上下走，确定 = 用这一个，返回 = 收起（正在输入时先收输入法）。
 * 方向键、返回、焦点由 `TvPlayerScreen` 接（和选集带同一种做法）；这里只排版。[search] 在外面记着（收起再打开还在）。
 */
@Composable
fun BoxScope.TvDanmakuPicker(d: PlayerDanmaku, search: DanmakuSearch, itemId: String?, editing: Boolean, onEditing: (Boolean) -> Unit, onClose: () -> Unit) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val scope = rememberCoroutineScope()
    val st = com.gugaart.ui.player.DanmakuPickerBoard.stateOverride ?: d.state
    // 多季的剧：框里默认是这一季的词，这一季的候选 / 结果排前面，候选里有这一季自己的名字就多给一两个快捷词（和手机同一套）
    val season = remember(itemId, st) { DanmakuSeasonGuard.seasonKey(itemId) }
    val main = remember(season) { DanmakuSeasonGuard.manualQuery(itemId) }
    LaunchedEffect(main) { search.prefill(main) }
    val shown = remember(st.candidates, season) { candidatesSeasonFirst(st.candidates, season) }
    val inUse = st.candidates.firstOrNull()?.animeId?.takeIf { st.phase == DanmakuPhase.Ready }
    val quick = remember(st.candidates, season, main) { seasonAltQueries(st.candidates, season, main) }
    val hits = hitsSeasonFirst(search.hits.orEmpty(), season).take(TvDanmakuPickerSpec.HITS)
    val keys = tvPickerKeys(st.phase, st.candidates.size, hits.size, quick.size)
    val dark = TvPlayerSpec.DARK
    Box(Modifier.align(Alignment.CenterStart).width(TvDanmakuPickerSpec.WIDTH).fillMaxHeight().background(dark.copy(alpha = 0.96f)).border(1.dp, colors.line).testTag("tv-dm-picker")) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 64.dp, end = 48.dp, top = 56.dp, bottom = 56.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("弹幕来源", style = Guga.song(44.sp, FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
                BasicText("DANMAKU", Modifier.padding(start = 16.dp, bottom = 8.dp), style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            }
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(3.dp).background(colors.ink))
            BasicText(pickerHead(st.phase), Modifier.padding(top = 16.dp, bottom = 14.dp), style = Guga.song(26.sp, FontWeight.Bold, if (st.phase == DanmakuPhase.Ambiguous) colors.spot else colors.dim), maxLines = 2)
            TvGroup("dmp", keys, horizontal = false, default = keys.firstOrNull { it.startsWith("dmp-c-") } ?: keys.first())
            if (st.phase == DanmakuPhase.Unconfigured) {
                TvAction("dmp-settings", "设置弹幕服务器") {
                    onClose()
                    g.nav.navigate(com.gugugaga.app.nav.Route.Settings("danmaku"))
                }
            }
            if (st.phase == DanmakuPhase.Failed || st.phase == DanmakuPhase.Unrecognized) {
                st.error?.let { BasicText(it, Modifier.padding(bottom = 8.dp), style = Guga.song(22.sp, color = colors.seal), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                TvAction("dmp-retry", "重试") { d.run() }
            }
            shown.forEachIndexed { i, c ->
                val current = c.animeId == inUse
                TvBlockFocus("dmp-c-$i", {
                    com.gugaart.ui.player.DanmakuSeasonGuard.userChose = com.gugaart.ui.player.DanmakuPickerBoard.currentItem
                    d.pick(c)
                    onClose()
                }, Modifier.fillMaxWidth().testTag("tv-dm-cand-$i"), label = "弹幕 ${c.base}", kind = TvFocusKind.Row) { focused ->
                    val ink = if (focused) colors.onInk else colors.ink
                    Row(Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("${i + 1}", Modifier.width(48.dp), style = Guga.numeral(36.sp, if (focused) TvDiscoverSpecRed else colors.spot), maxLines = 1)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(c.base.ifBlank { c.title }, Modifier.weight(1f, fill = false), style = Guga.song(30.sp, FontWeight.Black, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val tag = if (current) "正在用" else if (i == 0) "推荐" else null
                                if (tag != null) BasicText(tag, Modifier.padding(start = 12.dp).background(colors.seal).padding(horizontal = 8.dp, vertical = 2.dp), style = Guga.song(18.sp, FontWeight.Bold, androidx.compose.ui.graphics.Color.White), maxLines = 1)
                            }
                            BasicText(candidateMeta(c, season = i == 0), Modifier.padding(top = 4.dp), style = Guga.song(21.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            if (st.phase != DanmakuPhase.Unconfigured) {
                BasicText("手动搜索", Modifier.padding(top = 24.dp, bottom = 8.dp), style = Guga.song(22.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                TvBlockFocus("dmp-search", { onEditing(true) }, Modifier.fillMaxWidth().then(if (editing) Modifier.border(3.dp, colors.spot) else Modifier.border(2.dp, colors.line)), label = "手动搜索片名", kind = TvFocusKind.Row) { focused ->
                    Box(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                        if (editing) {
                            TvEditText(search.query, { search.query = it }, false, KeyboardType.Text, ImeAction.Search, onIme = {
                                onEditing(false)
                                scope.launch {
                                    search.run(d)
                                    groups.enter("dmp", g.focus)
                                }
                            }, onLeave = { dir ->
                                onEditing(false)
                                if (dir == TvDir.Down || dir == TvDir.Up) groups.move(dir, g.focus)
                            })
                        } else {
                            val text = search.query.ifEmpty { "输入片名" }
                            BasicText(if (search.searching) "搜索中…" else text, style = Guga.song(28.sp, color = if (focused) colors.onInk else if (search.query.isEmpty()) colors.faint else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                search.error?.let { BasicText(it, Modifier.padding(top = 6.dp), style = Guga.song(22.sp, color = colors.seal), maxLines = 2) }
                quick.forEachIndexed { i, q ->
                    TvAction("dmp-q-$i", q, ink = true) {
                        search.query = q
                        scope.launch {
                            search.run(d)
                            groups.enter("dmp", g.focus)
                        }
                    }
                }
                TvAction("dmp-repick", "重新自动匹配") { com.gugaart.ui.player.DanmakuPickerBoard.repickByHand(d) }
                if (search.hits?.isEmpty() == true) BasicText("没有结果", Modifier.padding(top = 10.dp), style = Guga.song(22.sp, color = colors.dim), maxLines = 1)
                hits.forEachIndexed { i, a ->
                    TvBlockFocus("dmp-h-$i", {
                        com.gugaart.ui.player.DanmakuSeasonGuard.userChose = com.gugaart.ui.player.DanmakuPickerBoard.currentItem
                        d.pickManual(a)
                        onClose()
                    }, Modifier.fillMaxWidth().testTag("tv-dm-hit-$i"), label = "弹幕 ${a.title}", kind = TvFocusKind.Row) { focused ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicText(a.title, Modifier.weight(1f), style = Guga.song(26.sp, FontWeight.Bold, if (focused) colors.onInk else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (a.episodeCount > 0) BasicText("${a.episodeCount} 集", style = Guga.song(21.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.dim), maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 焦点是一整行的一个动作（「重试」「去设置」「重新自动匹配」、快捷词 [ink] = 墨色字）。 */
@Composable
private fun TvAction(key: String, label: String, ink: Boolean = false, onClick: () -> Unit) {
    val colors = Guga.colors
    TvBlockFocus(key, onClick, Modifier.fillMaxWidth().padding(top = 10.dp).testTag("tv-$key"), label = label, kind = TvFocusKind.Row) { focused ->
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            BasicText(label, style = Guga.song(26.sp, FontWeight.Bold, if (focused) colors.onInk else if (ink) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 焦点行上的名次：深朱红（米白底上看得清）。 */
private val TvDiscoverSpecRed = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK
