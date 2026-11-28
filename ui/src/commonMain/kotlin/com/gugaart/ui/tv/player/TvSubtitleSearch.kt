package com.gugaart.ui.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.gugaart.ui.subtitle.ApplyState
import com.gugaart.ui.subtitle.ExactTag
import com.gugaart.ui.subtitle.HitAction
import com.gugaart.ui.subtitle.hitActionsOf
import com.gugaart.ui.subtitle.ScoredHit
import com.gugaart.ui.subtitle.applyLineOf
import com.gugaart.ui.subtitle.bestCardText
import com.gugaart.ui.tv.detail.TvActionButton
import com.gugaart.ui.subtitle.SearchLang
import com.gugaart.ui.subtitle.SourceState
import com.gugaart.ui.subtitle.SubtitleHit
import com.gugaart.ui.subtitle.SubtitleSearchBoard
import com.gugaart.ui.subtitle.SubtitleSearchSession
import com.gugaart.ui.subtitle.destinationOf
import com.gugaart.ui.subtitle.printLine
import com.gugaart.ui.subtitle.sourceLine
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.detail.LocalTvGroups
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.system.TvEditText
import com.gugugaga.app.ui.LocalGraph
import kotlinx.coroutines.launch

/** 电视字幕搜索的几个数。 */
object TvSubtitleSearchSpec {
    val WIDTH = 860.dp

    /** 结果最多列几条（遥控器一条条往下走）。 */
    const val HITS = 16
}

/**
 * 电视字幕搜索这一栏的焦点键（从上往下：最合适的「用上」、结果、换个名字搜、语言）。纯函数，有单测。
 * [expanded]：哪一条（`sbs-best` / `sbs-h-<i>`）按确定后在下面原地展开了 [actions] 个去向按钮（[tvSubActKey]），紧跟在它后面。
 */
fun tvSubtitleKeys(hits: Int, online: Boolean, best: Boolean = false, expanded: String? = null, actions: Int = 0): List<String> = buildList {
    fun add2(key: String) {
        add(key)
        if (key == expanded) repeat(actions) { add(tvSubActKey(it)) }
    }
    if (best) add2("sbs-best")
    repeat(hits) { add2("sbs-h-$it") }
    if (online) add("sbs-query")
    add("sbs-lang")
}

/** 原地展开的第 [i] 个去向按钮的焦点键（一次只展开一条）。 */
fun tvSubActKey(i: Int) = "sbs-act-$i"

/**
 * 电视上一条结果按确定（纯函数，有单测）：只有一个去向（服务器源「保存到服务器」；迅雷 / 射手而服务器没装 Subtitle Manager 或不是管理员 = 「仅本次播放」）
 * = 直接做，返回它；两个（「保存到服务器」/「重试」+「仅本次播放」，[com.gugaart.ui.subtitle.hitActionsOf]）= null，在这一行下面原地展开让人挑。
 */
fun tvHitPick(actions: List<HitAction>): HitAction? = actions.singleOrNull()

/**
 * 一条结果右端的去向小签（纯函数，有单测）：只有一个去向 = 写它（「保存到服务器」/「仅本次播放」）；两个（装了 Subtitle Manager、是管理员）=
 * 不写——按确定展开的两个按钮已经说明了（int151e 协调人定）。
 */
fun tvHitTag(hit: SubtitleHit, actions: List<HitAction>): String? =
    // 没有去向（下载下来是弹幕，§4.5）/ 两个去向：都不写
    if (actions.size != 1) null else destinationOf(hit, inPlayer = true)

/**
 * 字幕搜索（电视，docs/subtitle-search.md §5.3）：画面照常全幅在后面放，左边一栏墨色的纸——状态行、结果（确定 = 用这一条，进度写在那一行）、
 * 换个名字搜（确定 = 弹输入法，输入法里按「搜索」= 搜）、语言（确定 = 中文 / 英文换一下）。上下走，返回 = 收起（正在输入时先收输入法）。
 * 方向键、返回、焦点由 `TvPlayerScreen` 接（和弹幕来源同一种做法）；这里只排版。
 *
 * 迅雷 / 射手的一条、服务器装了 Subtitle Manager 而且是管理员（docs/subtitle-search.md §4.4）：按确定 = 在这一条下面原地展开两个竖排按钮
 * 「保存到服务器」（焦点落在这）和「仅本次播放」，返回收起、焦点回到这一条（[expanded] 由 `TvPlayerScreen` 持有，返回键在那边接）。
 * 做法同 P0-3 电视的原地确认；上传、出错文案和手机同一套（[SubtitleSearchBoard.chooseIn] 带 [HitAction]）。
 */
@Composable
fun BoxScope.TvSubtitleSearch(
    s: SubtitleSearchSession,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    expanded: String? = null,
    onExpanded: (String?) -> Unit = {},
    onClose: () -> Unit,
) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val scope = rememberCoroutineScope()
    val hits = s.hits.take(TvSubtitleSearchSpec.HITS)
    val best = s.best
    fun hitOf(key: String?): SubtitleHit? = when {
        key == null -> null
        key == "sbs-best" -> best?.hit
        key.startsWith("sbs-h-") -> key.removePrefix("sbs-h-").toIntOrNull()?.let { hits.getOrNull(it) }
        else -> null
    }
    // 展开的那一条还在、而且确实有两个去向才算展开（重新搜了、上传失败成「已有」只剩一个去向 → 不展开）
    val openHit = hitOf(expanded)
    val openActions = openHit?.let { hitActionsOf(it, inPlayer = true, canUpload = s.canUpload, apply = s.apply) }?.takeIf { it.size > 1 }.orEmpty()
    val open = expanded?.takeIf { openActions.isNotEmpty() }
    val keys = tvSubtitleKeys(hits.size, s.online, best = best != null, expanded = open, actions = openActions.size)
    fun pick(key: String, hit: SubtitleHit) {
        val actions = hitActionsOf(hit, inPlayer = true, canUpload = s.canUpload, apply = s.apply)
        if (actions.isEmpty()) return // 下载下来是弹幕：这一行只剩标签
        val one = tvHitPick(actions)
        if (one != null)SubtitleSearchBoard.chooseIn(s, SubtitleSearchBoard.play, hit, g, scope, one) else onExpanded(key)
    }
    fun act(a: HitAction) {
        val hit = openHit ?: return
        onExpanded(null)
        SubtitleSearchBoard.chooseIn(s, SubtitleSearchBoard.play, hit, g, scope, a)
    }
    // 展开 → 焦点落在第一个按钮（「保存到服务器」/「重试」）；收起（返回 / 挑了一个）→ 回到那一条
    var lastOpen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(open) {
        val back = lastOpen
        lastOpen = open
        val target = if (open != null) tvSubActKey(0) else back ?: return@LaunchedEffect
        repeat(40) {
            if (g.focus.request(target)) return@LaunchedEffect
            kotlinx.coroutines.delay(25)
        }
    }
    @Composable
    fun Actions(key: String) {
        if (key != open) return
        Column(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 12.dp).fillMaxWidth().testTag("tv-sub-actions"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            openActions.forEachIndexed { i, a ->
                TvActionButton(tvSubActKey(i), a.label, { act(a) }, Modifier.fillMaxWidth().testTag("tv-sub-act-$i"), primary = i == 0)
            }
        }
    }
    val dark = TvPlayerSpec.DARK
    val scroll = rememberScrollState()
    // 结果到了、焦点还停在下面的输入行 / 语言行（刚打开时没有结果）：挪到第一条
    LaunchedEffect(hits.isNotEmpty()) {
        if (hits.isNotEmpty() && !editing && (groups.current == "sbs-query" || groups.current == "sbs-lang")) g.focus.request(if (best != null) "sbs-best" else "sbs-h-0")
    }
    // 最合适的出来了（1.4.1 U2）：焦点默认落在卡上的「用上」——还没动过（停在第一条 / 输入行 / 语言行）才挪，正在输入不挪；
    // 手动搜完也一样（焦点从输入行回到这里，栏滚回顶上）
    LaunchedEffect(best?.hit?.key) {
        if (best != null && !editing && groups.current in TV_BEST_STEAL_FROM) {
            scroll.animateScrollTo(0)
            g.focus.request("sbs-best")
        }
    }
    Box(Modifier.align(Alignment.CenterStart).width(TvSubtitleSearchSpec.WIDTH).fillMaxHeight().background(dark.copy(alpha = 0.96f)).border(1.dp, colors.line).testTag("tv-sub-search")) {
        Column(Modifier.fillMaxHeight().verticalScroll(scroll).padding(start = 64.dp, end = 48.dp, top = 56.dp, bottom = 56.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                BasicText("搜索字幕", style = Guga.song(44.sp, FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
                BasicText("SUBTITLES", Modifier.padding(start = 16.dp, bottom = 8.dp), style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            }
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(3.dp).background(colors.ink))
            BasicText(s.target.title, Modifier.padding(top = 16.dp), style = Guga.song(26.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(s.target.fileName, Modifier.padding(top = 4.dp), style = Guga.song(20.sp, color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Column(Modifier.padding(top = 10.dp, bottom = 12.dp).testTag("tv-sub-status")) {
                for (line in listOfNotNull(sourceLine("服务器", s.server), sourceLine("迅雷", s.thunder), sourceLine("射手", s.shooter), printLine(s.print).takeIf { s.online })) {
                    BasicText(line, style = Guga.song(20.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            TvGroup("sbs", keys, horizontal = false, default = keys.first())
            if (best != null) {
                TvBestCard(best, s.apply, twoWays = hitActionsOf(best.hit, inPlayer = true, canUpload = s.canUpload, apply = s.apply).size > 1) { pick("sbs-best", best.hit) }
                Actions("sbs-best")
            }
            if (hits.isEmpty() && !s.searching) BasicText("没有结果", Modifier.padding(vertical = 10.dp), style = Guga.song(24.sp, color = colors.dim), maxLines = 1)
            hits.forEachIndexed { i, h ->
                TvHitRow(h, i, s.apply, tvHitTag(h, hitActionsOf(h, inPlayer = true, canUpload = s.canUpload, apply = s.apply)), best = best?.takeIf { it.hit.key == h.key }) { pick("sbs-h-$i", h) }
                Actions("sbs-h-$i")
            }
            if (s.online) {
                BasicText("手动搜索", Modifier.padding(top = 22.dp, bottom = 8.dp), style = Guga.song(22.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                TvBlockFocus("sbs-query", { onEditing(true) }, Modifier.fillMaxWidth().then(if (editing) Modifier.border(3.dp, colors.spot) else Modifier.border(2.dp, colors.line)), label = "手动搜索字幕", kind = TvFocusKind.Row) { focused ->
                    Box(Modifier.fillMaxWidth().height(84.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                        if (editing) {
                            TvEditText(s.query, { s.query = it }, false, KeyboardType.Text, ImeAction.Search, onIme = {
                                onEditing(false)
                                scope.launch {
                                    s.runName()
                                    groups.enter("sbs", g.focus)
                                }
                            }, onLeave = { dir ->
                                onEditing(false)
                                if (dir == TvDir.Down || dir == TvDir.Up) groups.move(dir, g.focus)
                            })
                        } else {
                            val text = s.query.ifEmpty { "片名或文件名" }
                            BasicText(if (s.thunder == SourceState.Busy) "搜索中…" else text, style = Guga.song(28.sp, color = if (focused) colors.onInk else if (s.query.isEmpty()) colors.faint else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            TvBlockFocus("sbs-lang", {
                if (!s.searching) {
                    s.lang = if (s.lang == SearchLang.Chinese) SearchLang.English else SearchLang.Chinese
                    scope.launch { s.run() }
                }
            }, Modifier.fillMaxWidth().padding(top = 10.dp).testTag("tv-sbs-lang"), label = "字幕语言", kind = TvFocusKind.Row) { focused ->
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("语言：${s.lang.label}", Modifier.weight(1f), style = Guga.song(26.sp, FontWeight.Bold, if (focused) colors.onInk else colors.dim), maxLines = 1)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 一条结果（电视）：名称 + 小字 + 去向小签（[tag]，null = 不写，见 [tvHitTag]）；焦点 = 米白实底。选中以后小字换成进度 / 结果。 */
@Composable
private fun TvHitRow(h: SubtitleHit, i: Int, apply: ApplyState?, tag: String?, best: ScoredHit? = null, onPick: () -> Unit) {
    val colors = Guga.colors
    val mine = when (apply) {
        is ApplyState.Working -> apply.key == h.key
        is ApplyState.Done -> apply.key == h.key
        is ApplyState.Failed -> apply.key == h.key
        null -> false
    }
    TvBlockFocus("sbs-h-$i", onPick, Modifier.fillMaxWidth().testTag("tv-sub-hit-$i"), label = "字幕 ${h.name}", kind = TvFocusKind.Row) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (best != null) BasicText(
                    if (best.score.isExactHash) "最合适 · 精确" else "最合适",
                    Modifier.padding(bottom = 2.dp).testTag("tv-sub-hit-best"),
                    style = Guga.song(20.sp, FontWeight.Black, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot, letterSpacing = 0.2.em),
                    maxLines = 1,
                )
                BasicText(h.name, style = Guga.song(27.sp, FontWeight.Black, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = if (mine) when (apply) {
                    is ApplyState.Working -> apply.step
                    is ApplyState.Done -> apply.note
                    is ApplyState.Failed -> apply.why
                    null -> h.meta()
                } else h.meta()
                val tint = when {
                    mine && apply is ApplyState.Failed -> if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.seal
                    mine -> if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot
                    focused -> colors.onInk.copy(alpha = 0.7f)
                    else -> colors.dim
                }
                BasicText(line, Modifier.padding(top = 4.dp), style = Guga.song(21.sp, color = tint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (tag != null) BasicText(
                tag,
                Modifier.padding(start = 12.dp).testTag("tv-sub-hit-tag-$i").border(2.dp, if (h.toEmby) (if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot) else (if (focused) colors.onInk.copy(alpha = 0.5f) else colors.line)).padding(horizontal = 10.dp, vertical = 3.dp),
                style = Guga.song(20.sp, FontWeight.Bold, if (h.toEmby) (if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot) else (if (focused) colors.onInk else colors.dim)),
                maxLines = 1,
            )
        }
    }
}

/** 焦点在这几处（还没动过）时，最合适的一出来就把焦点挪到它的「用上」上。 */
private val TV_BEST_STEAL_FROM = setOf(null, "sbs-h-0", "sbs-query", "sbs-lang")

/**
 * 「最合适」卡（电视，1.4.1 U2）：小标、字幕名、理由、语言 · 格式 · 来源 · 去向；卡本身不拿焦点，焦点在下面的「用上」上（打开栏时默认在这里）。
 * 下键从「用上」走到完整的单子。
 */
@Composable
private fun TvBestCard(best: ScoredHit, apply: ApplyState?, twoWays: Boolean, onUse: () -> Unit) {
    val colors = Guga.colors
    // 两个去向（按确定展开「保存到服务器」「仅本次播放」）时小字里不写去向，同手机 / 电脑和 [tvHitTag]
    val c = bestCardText(best, inPlayer = true, withDestination = !twoWays)
    val mine = applyLineOf(best.hit, apply)
    Column(Modifier.padding(bottom = 18.dp).fillMaxWidth().border(2.dp, colors.ink).padding(horizontal = 24.dp, vertical = 20.dp).testTag("tv-sub-best")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(c.kicker, style = Guga.song(24.sp, FontWeight.Black, colors.spot, letterSpacing = 0.3.em), maxLines = 1)
            BasicText("BEST MATCH", Modifier.padding(start = 14.dp), style = Guga.numeral(20.sp, colors.faint, letterSpacing = 0.2.em), maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (c.exact) ExactTag(20.sp, 2.dp)
        }
        BasicText(c.name, Modifier.padding(top = 10.dp), style = Guga.song(28.sp, FontWeight.Black, lineHeight = 38.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (c.reasons.isNotEmpty()) BasicText(c.reasons, Modifier.padding(top = 6.dp), style = Guga.song(22.sp, color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(c.meta, Modifier.padding(top = 4.dp), style = Guga.song(21.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (mine != null) BasicText(mine.first, Modifier.padding(top = 8.dp).testTag("tv-sub-best-apply"), style = Guga.song(21.sp, color = if (mine.second) colors.seal else colors.spot), maxLines = 1, overflow = TextOverflow.Ellipsis)
        TvActionButton("sbs-best", if (best.hit.toEmby) "保存到服务器" else "用上", onUse, Modifier.padding(top = 16.dp).fillMaxWidth().testTag("tv-sub-best-use"), primary = true)
    }
}
