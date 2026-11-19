package com.gugaart.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.SheetSpec
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.typingTarget
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.danmaku.match.DanmakuAnime
import com.gugugaga.danmaku.match.DanmakuPhase
import com.gugugaga.danmaku.match.MatchCandidate
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * 弹幕来源（用户 2026-11-15：「如果弹幕不对，你推荐分数较高的从左边出来让用户选择，现在有分歧点了选择选择不了。
 * 有时候用户可能也需要自己加载对应的，需要手动，要给这个入口」）。
 *
 * 匹配、打分、记住这一季、手动搜索全是上游 :danmaku / `PlayerDanmaku` 的（[PlayerDanmaku.pick]、[PlayerDanmaku.search]、
 * [PlayerDanmaku.pickManual]、[PlayerDanmaku.repick]）；以前画报的播放器只在面板里放了一行「弹幕有歧义 · 点击选择」，点了只是重新匹配，
 * 候选从来没有画出来——看起来像点不动。现在：一张从左边滑出来的纸（画面照常在右边放），
 * 上面是分数最高的几个候选（第一个标「推荐」，正在用的标「正在用」），下面是手动搜索和「重新自动匹配」。
 * 有歧义时（上游等用户挑）自己出来一次（每一集一次）；平时从「弹幕」面板的「弹幕源」那一行点开。电视在 `tv/player/TvDanmakuPicker.kt`。
 */

/** 弹幕来源那张纸开没开（三端共用）。[autoFor] = 已经为哪一集自动打开过（每一集只自动出来一次）。 */
object DanmakuPickerBoard {
    var open: Boolean by mutableStateOf(false)
    var autoFor: String? = null

    /** 测试缝：直接给一个匹配状态（离屏测试没有弹幕服务器）。应用里永远是 null = 读上游的。 */
    var stateOverride: com.gugugaga.danmaku.match.DanmakuState? by mutableStateOf(null)

    /** 有歧义了：这一集还没自动出来过就打开。返回打开了没有。纯逻辑，有单测。 */
    fun autoOpen(asks: Boolean, itemId: String?): Boolean {
        if (!asks || itemId == null || autoFor == itemId) return false
        autoFor = itemId
        open = true
        return true
    }

    /** 用户自己点了「重新自动匹配」的那一集：候选照样摆出来让 ta 挑，不替 ta 选。 */
    var manualFor: String? = null

    /** 现在放的是哪一集（[settle] 每换一集记一下；「重新自动匹配」用它）。 */
    var currentItem: String? = null

    /** 用户点了「重新自动匹配」。 */
    fun repickByHand(d: com.gugugaga.app.player.PlayerDanmaku) {
        manualFor = currentItem
        d.repick()
    }

    /**
     * 上游等用户挑的时候先看一眼（用户 2026-11-16：「弹幕能直接匹配好的就不要弹出来，不确定的再弹出来」）：
     * 其实很确定（[confidentCandidate]）就直接用它、不弹；不确定才像以前一样从左边出来。返回自动用了哪一个（null = 弹了或者不用管）。
     */
    fun settle(d: com.gugugaga.app.player.PlayerDanmaku, itemId: String?): MatchCandidate? {
        currentItem = itemId
        if (!d.asksChoice || itemId == null) return null
        if (manualFor != itemId) confidentCandidate(d.state, DanmakuSeasonGuard.targetSeason(itemId))?.let { c ->
            autoFor = itemId
            d.pick(c)
            return c
        }
        autoOpen(true, itemId)
        return null
    }
}

/**
 * 上游标成「有歧义」、其实很确定的那一个；拿不准 = null（弹出来让用户挑）。纯函数，有单测。
 * - 弹幕服务器按文件名直接配上了（上游 /match 回退：本引擎打分不够，标成有歧义「需用户确认」）→ 用服务器配上的那一部；
 * - 只剩一个像样的候选（没被淘汰、匹配 ≥ 50）→ 用它；
 * - 第一名明显领先：匹配 ≥ 65 且领先第二名 ≥ 12，或者匹配 ≥ 75 且领先 ≥ 6（上游自己的门槛是 70 分、领先 10）。
 */
fun confidentCandidate(st: com.gugugaga.danmaku.match.DanmakuState, season: Int? = null): MatchCandidate? {
    if (st.phase != DanmakuPhase.Ambiguous || st.candidates.isEmpty()) return null
    // 第 2 季起：有对得上季号的就用它；第一名不是这一季的 → 拿不准，弹出来（不拿别的季顶替）
    if (season != null && season >= 2) {
        st.candidates.firstOrNull { !it.eliminated && danmakuSeasonOf(it.title) == season }?.let { return it }
        if (st.candidates.firstOrNull { !it.eliminated }?.let { danmakuSeasonOf(it.title) != season } == true) return null
    }
    st.explain.firstOrNull { it.startsWith("回退：服务器 /match") }?.let { line ->
        val title = Regex("返回 “([^”]+)”").find(line)?.groupValues?.get(1)
        st.candidates.firstOrNull { it.title == title }?.let { return it }
    }
    val viable = st.candidates.filter { !it.eliminated }
    val top = viable.firstOrNull() ?: return null
    val second = viable.getOrNull(1) ?: return top.takeIf { it.score >= 50.0 }
    val lead = top.score - second.score
    return top.takeIf { (it.score >= 65.0 && lead >= 12.0) || (it.score >= 75.0 && lead >= 6.0) }
}

/** 弹幕来源的名字（服务器给的是英文标记）。 */
fun danmakuSourceName(s: String): String? = when (s.lowercase()) {
    "" -> null
    "tencent", "qq" -> "腾讯"
    "iqiyi" -> "爱奇艺"
    "youku" -> "优酷"
    "bilibili", "bilibili1" -> "B 站"
    "mgtv" -> "芒果"
    "renren" -> "人人"
    "sohu" -> "搜狐"
    "letv", "le" -> "乐视"
    "migu" -> "咪咕"
    else -> s
}

/** 候选那一行的小字：「2021 · 电视剧 · 36 集 · 腾讯」。纯函数，有单测。 */
fun candidateMeta(c: MatchCandidate): String = listOfNotNull(
    c.year?.toString(),
    c.kind.ifBlank { c.typeLabel }.takeIf { it.isNotBlank() },
    c.episodeCount.takeIf { it > 0 }?.let { "$it 集" },
    danmakuSourceName(c.source),
).joinToString(" · ")

/** 「匹配 87」：上游的总分 0–100。 */
fun candidateScore(c: MatchCandidate): String = "匹配 ${c.score.roundToInt()}"

/** 纸片顶上那句（随上游的状态）。纯函数，有单测。 */
fun pickerHead(phase: DanmakuPhase): String = when (phase) {
    DanmakuPhase.Ambiguous -> "这一集有几个可能的弹幕，挑一个（会记住这一季）"
    DanmakuPhase.Ready -> "现在用的是标「正在用」的那个；不对就换一个"
    DanmakuPhase.None, DanmakuPhase.Empty -> "没有自动匹配到，可以手动搜片名"
    DanmakuPhase.Unconfigured -> "还没配置弹幕服务器"
    DanmakuPhase.Failed -> "弹幕没取到"
    DanmakuPhase.Unrecognized -> "弹幕服务器认不出这一集"
    DanmakuPhase.Matching, DanmakuPhase.Loading, DanmakuPhase.Busy -> "正在匹配……"
    else -> "挑一个弹幕，或者手动搜片名"
}

/** 手动搜索：输入、结果、在搜、出错。三端共用（电视的输入框换成遥控器那一种）。 */
class DanmakuSearch {
    var query by mutableStateOf("")
    var hits by mutableStateOf<List<DanmakuAnime>?>(null)
    var searching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    suspend fun run(d: PlayerDanmaku) {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        error = null
        try {
            hits = d.search(q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "搜索失败"
            hits = null
        } finally {
            searching = false
        }
    }
}

/**
 * 手机 / 电脑：弹幕来源那张纸（从左边滑出来，[width] 宽、整高；画面在右边照常放）。有歧义时自己出来一次。
 * 放在播放器那一页的 Box 里（盖在画面和控件上面）。
 */
@Composable
fun BoxScope.DanmakuPickerHost(d: PlayerDanmaku, itemId: String?, width: Dp) {
    LaunchedEffect(d.asksChoice, itemId) { DanmakuPickerBoard.settle(d, itemId) }
    // 分季的剧：配到了别的季就换成这一季的条目，没有就不放错的弹幕
    LaunchedEffect(d.state.phase, d.state.title, itemId) { DanmakuSeasonGuard.check(d, itemId) }
    DisposableEffect(Unit) { onDispose { DanmakuPickerBoard.open = false } }
    if (!DanmakuPickerBoard.open) return
    DanmakuPickerDrawer(d, width, onClose = { DanmakuPickerBoard.open = false })
}

@Composable
private fun BoxScope.DanmakuPickerDrawer(d: PlayerDanmaku, width: Dp, onClose: () -> Unit) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val back = LocalBackDispatcher.current
    val close = rememberUpdatedState(onClose)
    DisposableEffect(back) {
        val un = back.push {
            close.value()
            true
        }
        onDispose { un() }
    }
    val scope = rememberCoroutineScope()
    val search = remember { DanmakuSearch() }
    val st = DanmakuPickerBoard.stateOverride ?: d.state
    Box(
        Modifier.align(Alignment.CenterStart).width(width).fillMaxHeight()
            .background(SheetSpec.SLIP.copy(alpha = 0.97f))
            .border(1.dp, colors.line)
            .testTag("dm-picker"),
    ) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("弹幕来源", style = Guga.song(22.sp, FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
                BasicText("DANMAKU", Modifier.padding(start = 10.dp), style = Guga.note(13.sp, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                Spacer(Modifier.weight(1f))
                TextLink("收起", onClose, Modifier.testTag("dm-picker-close"), color = colors.dim)
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(2.dp).background(colors.ink))
            BasicText(pickerHead(st.phase), Modifier.padding(top = 12.dp), style = Guga.song(14.sp, FontWeight.Bold, if (st.phase == DanmakuPhase.Ambiguous) colors.spot else colors.dim, lineHeight = 21.sp), maxLines = 2)
            when (st.phase) {
                DanmakuPhase.Unconfigured -> PrimaryButton("去设置弹幕服务器", {
                    onClose()
                    g.nav.navigate(com.gugugaga.app.nav.Route.Settings("danmaku"))
                }, Modifier.padding(top = 12.dp), icon = GuguIcon.Settings)
                DanmakuPhase.Failed, DanmakuPhase.Unrecognized -> {
                    st.error?.let { BasicText(it, Modifier.padding(top = 6.dp), style = Guga.song(13.sp, color = colors.seal), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    PrimaryButton("重试", { d.run() }, Modifier.padding(top = 10.dp), icon = GuguIcon.RotateCcw)
                }
                else -> {}
            }
            // ---- 候选：分数从高到低 ----
            if (st.candidates.isNotEmpty()) {
                Column(Modifier.padding(top = 12.dp).testTag("dm-candidates")) {
                    st.candidates.forEachIndexed { i, c ->
                        CandidateRow(c, i, current = st.phase == DanmakuPhase.Ready && i == 0) {
                            DanmakuSeasonGuard.userChose = DanmakuPickerBoard.currentItem
                            d.pick(c)
                            onClose()
                        }
                    }
                }
            }
            if (st.phase != DanmakuPhase.Unconfigured) {
                // ---- 手动：自己搜片名 ----
                BasicText("手动搜索", Modifier.padding(top = 20.dp), style = Guga.song(13.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, colors.line).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (search.query.isEmpty()) BasicText("输入片名，例如：赘婿", style = Guga.song(15.sp, color = colors.faint), maxLines = 1)
                        BasicTextField(
                            search.query,
                            { search.query = it },
                            Modifier.fillMaxWidth().testTag("dm-search").typingTarget(),
                            textStyle = Guga.song(15.sp, color = colors.ink),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.spot),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { scope.launch { search.run(d) } }),
                        )
                    }
                    PrimaryButton(if (search.searching) "搜索中" else "搜索", { scope.launch { search.run(d) } }, Modifier.padding(start = 10.dp).testTag("dm-search-go"), icon = null, enabled = search.query.isNotBlank() && !search.searching)
                }
                search.error?.let { BasicText(it, Modifier.padding(top = 6.dp), style = Guga.song(13.sp, color = colors.seal), maxLines = 2) }
                search.hits?.let { hits ->
                    if (hits.isEmpty()) BasicText("没有搜到，换个叫法试试。", Modifier.padding(top = 8.dp), style = Guga.song(13.sp, color = colors.dim), maxLines = 1)
                    for ((i, a) in hits.take(30).withIndex()) {
                        HitRow(a, i) {
                            DanmakuSeasonGuard.userChose = DanmakuPickerBoard.currentItem
                            d.pickManual(a)
                            onClose()
                        }
                    }
                }
                TextLink("重新自动匹配", { DanmakuPickerBoard.repickByHand(d) }, Modifier.padding(top = 16.dp).testTag("dm-repick"), icon = GuguIcon.RotateCcw, color = colors.dim)
            }
        }
    }
}

/** 候选的一行：名次 + 片名 +「2021 · 电视剧 · 36 集 · 腾讯」+ 右端「匹配 87」；第一个标「推荐」，正在用的标「正在用」。 */
@Composable
private fun CandidateRow(c: MatchCandidate, index: Int, current: Boolean, onPick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).pressGraphics(press).pressable(press, label = "弹幕 ${c.base}", onClick = onPick)
            .then(if (current) Modifier.background(colors.spot.copy(alpha = 0.14f)) else Modifier)
            .padding(vertical = 8.dp, horizontal = 6.dp).testTag("dm-cand-$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("${index + 1}", Modifier.width(26.dp), style = Guga.numeral(20.sp, colors.spot), maxLines = 1)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(c.base.ifBlank { c.title }, Modifier.weight(1f, fill = false), style = Guga.song(16.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val tag = when {
                    current -> "正在用"
                    index == 0 -> "推荐"
                    else -> null
                }
                if (tag != null) BasicText(tag, Modifier.padding(start = 8.dp).background(colors.seal).padding(horizontal = 5.dp, vertical = 1.dp), style = Guga.song(11.sp, FontWeight.Bold, colors.ink), maxLines = 1)
            }
            BasicText(candidateMeta(c), Modifier.padding(top = 2.dp), style = Guga.song(12.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicText(candidateScore(c), Modifier.padding(start = 8.dp), style = Guga.song(13.sp, FontWeight.Bold, if (index == 0) colors.spot else colors.dim), maxLines = 1)
    }
}

/** 手动搜索的一条结果：片名 + 集数。 */
@Composable
private fun HitRow(a: DanmakuAnime, index: Int, onPick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).pressGraphics(press).pressable(press, label = "弹幕 ${a.title}", onClick = onPick).padding(vertical = 6.dp, horizontal = 6.dp).testTag("dm-hit-$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(a.title, Modifier.weight(1f), style = Guga.song(14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (a.episodeCount > 0) BasicText("${a.episodeCount} 集", style = Guga.song(12.sp, color = colors.dim), maxLines = 1)
    }
}
