package com.gugaart.ui.subtitle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.player.PanelLink
import com.gugaart.ui.player.PlaySession
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.settings.playbackPrefs
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * 字幕「最合适」的界面（1.4.1 U2，docs/subtitle-search.md §5.4）。后端（打分、最合适、自动加载）在 SmartMatch.kt / SubtitleAutoBest.kt；
 * 这里只有：搜索栏顶上那张「最合适」卡（手机 / 电脑；电视的在 tv/player/TvSubtitleSearch.kt）、播放器里的那句提示（贴边小条 + 字幕面板顶上一段）、
 * 开播后调一次自动加载的接线。
 */

/**
 * 自动加载**关着**时也在开播后搜一次（只在这一集没有中文字幕时），最合适那条是指纹精确就提示「找到精确匹配的字幕 · 用上」。
 * 主理人 2026-11-24 的决定（plan-1.4.1 §3 第 2 条）。改成 false = 关着就一次请求都不发（那句提示也就只在开着时出现）。
 * 这一次搜索尊重「在线字幕源」（`subtitleOnline`）：关着只问服务器，不向迅雷 / 射手发请求（协调者 2026-11-25 定）。
 */
const val AUTO_BEST_HINT_WHEN_OFF = true

/** 开播后等多久再搜：让开播那几秒的带宽和「片源效果」那排徽章先过去（徽章亮 5 秒）。 */
const val AUTO_BEST_DELAY_MS = 6_000L

/** 「最合适」卡上的几行字。纯函数 [bestCardText] 的结果，有单测。 */
data class BestCardText(
    /** 小标：「最合适」。 */
    val kicker: String,
    /** 指纹精确：卡上多一个「精确匹配」。 */
    val exact: Boolean,
    val name: String,
    /** 一两条理由（`SmartScore.reasons` 的前两条），「 · 」隔开。 */
    val reasons: String,
    /** 语言 · 格式 · 来源 · 去向：「简 · SRT · 迅雷 · 仅本次」。 */
    val meta: String,
)

/**
 * 「最合适」卡的字（纯函数，有单测）。[inPlayer]：播放器外的去向写「播放并加载」。
 * [withDestination] = false：去向已经写在两个按钮上（「保存到服务器」「仅本次播放」），小字里不写。
 */
fun bestCardText(best: ScoredHit, inPlayer: Boolean, withDestination: Boolean = true): BestCardText {
    val h = best.hit
    val source = if (h.source == SubSource.Server) h.provider?.let { "服务器 · $it" } ?: h.source.label else h.source.label
    return BestCardText(
        kicker = "最合适",
        exact = best.score.isExactHash,
        name = h.name,
        reasons = best.score.reasons.take(2).joinToString(" · "),
        meta = listOfNotNull(h.lang.short, h.format.uppercase().takeIf { it.isNotBlank() }, source, destinationOf(h, inPlayer).takeIf { withDestination }).joinToString(" · "),
    )
}

/** 提示上那个动作。 */
enum class HintAction { Use, Undo, None }

/**
 * 播放器里那句提示。[key] 换了 = 换了一句（贴边小条重新亮几秒）；[text] = 贴边小条上的一句（手机 / 电脑）；[tvText] = 电视上的一句（遥控器没法点小条，写怎么走）；
 * [detail] = 字幕面板顶上那一段（带字幕名）；[persistent] = 控件出来时一直显示（「找到…」）；不是的亮几秒就淡出（「已自动加载」）。
 */
data class AutoBestHint(
    val key: String,
    val text: String,
    val tvText: String,
    val detail: String,
    val action: HintAction,
    val actionLabel: String?,
    val exact: Boolean,
    val persistent: Boolean,
)

/**
 * 这一刻该提示什么（纯函数，有单测）。[stateItem] = 状态是给哪一集的；[playing] = 正在放的那一集；[apply] = 自动加载那一次搜索的「选中以后」
 * （用户点了「用上」之后的进度）；[dismissed] = 用户撤销 / 收掉的那一句的 key。
 */
fun autoBestHintOf(state: AutoBestState, stateItem: String?, playing: String, apply: ApplyState?, dismissed: String?): AutoBestHint? {
    if (stateItem == null || stateItem != playing) return null
    val hint = when (state) {
        is AutoBestState.Found -> {
            val exact = state.score.isExactHash
            val head = if (exact) "找到精确匹配的字幕" else "找到匹配的字幕"
            val mine = when (apply) {
                is ApplyState.Working -> apply.key == state.hit.key
                is ApplyState.Done -> apply.key == state.hit.key
                is ApplyState.Failed -> apply.key == state.hit.key
                null -> false
            }
            when {
                mine && apply is ApplyState.Working -> AutoBestHint("using:${state.hit.key}", "${apply.step}　${state.hit.name}", "${apply.step}　${state.hit.name}", "${apply.step}　${state.hit.name}", HintAction.None, null, exact, persistent = true)
                // 用上了：字幕已经换上，顶栏的字幕那一段写「搜索」，这句不用再说
                mine && apply is ApplyState.Done -> null
                mine && apply is ApplyState.Failed -> AutoBestHint("failed:${state.hit.key}", apply.why, apply.why, apply.why, HintAction.None, null, exact, persistent = false)
                else -> AutoBestHint(
                    "found:${state.hit.key}", head, "$head · 下键 › 字幕 › 确定",
                    // 去向写在动作按钮上：服务器源 = 保存到服务器，本机源 = 用上（1.5.1 m151：不再另写说明）
                    "$head：${state.hit.name}", HintAction.Use, if (state.hit.toEmby) "保存到服务器" else "用上", exact, persistent = true,
                )
            }
        }
        is AutoBestState.Loaded -> if (state.index != null) AutoBestHint(
            "loaded:${state.hit.key}", "已自动加载：${state.hit.name}", "已自动加载：${state.hit.name}",
            state.note, HintAction.Undo, "撤销", state.score.isExactHash, persistent = false,
        ) else AutoBestHint(
            "loaded:${state.hit.key}", "已存进 Emby，处理中", "已存进 Emby，处理中", state.note, HintAction.None, null, state.score.isExactHash, persistent = false,
        )
        else -> null
    } ?: return null
    return hint.takeIf { it.key != dismissed }
}

/**
 * 播放器那边的自动加载：开播后调一次 [SubtitleAutoBest.consider]、记着「撤销」要换回哪一条、用户收掉了哪一句。
 * 状态本身在 [SubtitleAutoBest]（后端）；这里只有界面要的那一点。
 */
object AutoBestBoard {
    /** 用户撤销 / 收掉的那一句（[AutoBestHint.key]）。 */
    var dismissed: String? by mutableStateOf(null)
        private set

    /** 撤销要换回的：条目 id → 自动加载之前选着的字幕（null = 关）。 */
    private var undoTo: Pair<String, Int?>? = null

    fun reset() {
        dismissed = null
        undoTo = null
        SubtitleAutoBest.reset()
    }

    /** 测试缝：摆一个提示（离屏截图）。 */
    internal fun preview(item: String, s: AutoBestState, search: SubtitleSearchSession? = null, undo: Int? = null) {
        dismissed = null
        undoTo = item to undo
        SubtitleAutoBest.preview(item, s, search)
    }

    /** 这一刻的提示（读 Compose 状态：放在组合里调）。 */
    fun hintFor(playing: String): AutoBestHint? =
        autoBestHintOf(SubtitleAutoBest.state, SubtitleAutoBest.itemId, playing, SubtitleAutoBest.session?.apply, dismissed)

    /** 「用上」：用自动加载那一次的搜索选上最合适那条（走和搜索栏一样的路：进 Emby / 仅本次），不开栏。 */
    fun use(play: PlaySession, g: com.gugugaga.app.AppGraph, scope: kotlinx.coroutines.CoroutineScope) {
        val st = SubtitleAutoBest.state as? AutoBestState.Found ?: return
        val s = SubtitleAutoBest.session ?: return
        if (SubtitleAutoBest.itemId != play.controller.value.itemId) return
        SubtitleSearchBoard.chooseIn(s, play, st.hit, g, scope)
    }

    /** 「撤销」：换回自动加载之前那一条（存进 Emby 的那条留在服务器上，字幕面板里还能选）。 */
    fun undo(play: PlaySession) {
        val st = SubtitleAutoBest.state as? AutoBestState.Loaded ?: return
        val (item, prev) = undoTo ?: return
        if (item != play.controller.value.itemId) return
        play.chooseSubtitle(prev)
        dismissed = "loaded:${st.hit.key}"
    }

    /** 自动加载选上第 [index] 条之前记下现在那一条（撤销用），然后选上。 */
    internal fun select(play: PlaySession, itemId: String, index: Int) {
        undoTo = itemId to play.controller.value.subtitleIndex
        SubtitleSearchBoard.select(play, itemId, index, "自动加载对得上的字幕")
        play.subtitleWhy = "自动加载"
    }
}

/**
 * 开播后调一次自动加载（三端共用，`rememberPlaySession` 里挂一次）：每一集 Ready 之后等 [AUTO_BEST_DELAY_MS]，
 * 这一集没有中文字幕 → [SubtitleAutoBest.consider]（开关开着 = 指纹精确就加载；关着 = 指纹精确只提示）。
 * [explicit]：路由自己带着字幕（详情页里挑好的）= 不插手。
 */
@Composable
fun RememberAutoBest(play: PlaySession, explicit: Boolean) {
    val g = LocalGraph.current
    LaunchedEffect(play) {
        val outer = this
        var seen: String? = null
        var job: Job? = null
        try {
            play.controller.snapshot.collect { snap ->
                if (snap.itemId != seen && SubtitleAutoBest.itemId != null && SubtitleAutoBest.itemId != snap.itemId) {
                    // 换集了：上一集的提示和还没搜完的那一轮都不要了
                    job?.cancel()
                    AutoBestBoard.reset()
                }
                if (snap.phase != PlayPhase.Ready || seen == snap.itemId) return@collect
                val item = snap.item ?: return@collect
                val resolved = snap.resolved ?: return@collect
                seen = snap.itemId
                job?.cancel()
                AutoBestBoard.reset()
                if (explicit) return@collect
                job = outer.launch {
                    delay(AUTO_BEST_DELAY_MS)
                    val prefs = GugaPrefs.parse(g.deps.local.get(GugaSettingsStore.KEY))
                    if (!prefs.subtitleAutoBest && !AUTO_BEST_HINT_WHEN_OFF) return@launch
                    // 用户已经自己搜过、用上了（或者播放器外选了「播放并加载」）：不再插手
                    if (SubtitleSearchBoard.pinned == item.id || SubtitleSearchBoard.pendingLocal?.first == item.id) return@launch
                    val client = g.session.session.value?.client
                    val target = SubtitleTarget.of(item, resolved.mediaSource, client, resolved.strmTarget, resolved.remoteHeaders)
                    SubtitleAutoBest.consider(
                        target, client, SubtitleSearchBoard.net(),
                        online = prefs.subtitleOnline,
                        enabled = prefs.subtitleAutoBest,
                        langs = com.gugaart.ui.data.effectiveSubtitleLangs(
                            g.settings.state.value.playbackPrefs.subtitleLangs,
                            prefs.subLangsSeeded,
                        ).ifEmpty { SmartMatch.DEFAULT_LANGS },
                        hasChinese = hasChineseSubtitle(resolved.mediaSource.mediaStreams, LocalSubtitles.of(item.id)),
                        select = { idx -> if (!play.hasUserChosenSubtitle(item.id, resolved.mediaSource.id)) AutoBestBoard.select(play, item.id, idx) },
                        offerWhenOff = AUTO_BEST_HINT_WHEN_OFF,
                    )
                }
            }
        } finally {
            job?.cancel()
            AutoBestBoard.reset()
        }
    }
}

/**
 * 播放器里那句提示的贴边小条（docs/player.md §1 第 2 种：角上、一小块墨底，不进画面中央和字幕带）。放在左上「片源效果」徽章那一行**下面**
 * （徽章 5 秒就淡出、搜索开播 6 秒后才开始，平常不会同时在；开播缓冲久了徽章会晚亮，所以不共用一格）。
 * - 「找到精确匹配的字幕 · 用上」：出现时亮 [FLASH_MS]，之后**控件出来时**才在（[controls]）；字幕面板顶上一直有。
 * - 「已自动加载：… · 撤销」：亮几秒就淡出；之后只在字幕面板顶上。
 * 电视（[tv]）：遥控器点不到小条，写怎么走（「下键打开设置，字幕一行按确定」）。
 */
@Composable
fun AutoBestFlash(play: PlaySession, controls: Boolean, scale: Float, modifier: Modifier = Modifier, tv: Boolean = false, maxWidth: Dp = 420.dp) {
    val g = LocalGraph.current
    val snap by play.controller.snapshot.collectAsState()
    val hint = AutoBestBoard.hintFor(snap.itemId)
    var flashing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(hint?.key) {
        val k = hint?.key ?: return@LaunchedEffect
        flashing = k
        delay(if (hint.persistent) AutoBestSpec.FLASH_MS else AutoBestSpec.LOADED_MS)
        if (flashing == k) flashing = null
    }
    val visible = hint != null && (flashing == hint.key || (controls && hint.persistent))
    val scope = rememberCoroutineScope()
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(260)), exit = fadeOut(tween(600))) {
        val h = hint ?: return@AnimatedVisibility
        AutoBestChip(h, scale, tv, maxWidth) {
            when (h.action) {
                HintAction.Use -> AutoBestBoard.use(play, g, scope)
                HintAction.Undo -> AutoBestBoard.undo(play)
                HintAction.None -> {}
            }
        }
    }
}

/** 贴边小条本身（离屏截图也直接画它）。 */
@Composable
fun AutoBestChip(h: AutoBestHint, scale: Float, tv: Boolean, maxWidth: Dp, onAction: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        Modifier.widthIn(max = maxWidth).background(colors.inkWell).border(1.dp, colors.line)
            .padding(horizontal = 12.dp * scale, vertical = 7.dp * scale).testTag("sub-best-hint"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp * scale),
    ) {
        BasicText(
            if (tv) h.tvText else h.text,
            Modifier.weight(1f, fill = false),
            style = Guga.song(12.sp * scale, FontWeight.Bold, colors.ink, letterSpacing = 0.04.em),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!tv && h.actionLabel != null) {
            BasicText(
                h.actionLabel,
                Modifier.pressGraphics(press).pressable(press, label = h.actionLabel, onClick = onAction).border(1.dp, colors.ink).padding(horizontal = 9.dp * scale, vertical = 2.dp * scale)
                    .testTag("sub-best-hint-action"),
                style = Guga.song(12.sp * scale, FontWeight.Black, colors.ink, letterSpacing = 0.1.em),
                maxLines = 1,
            )
        }
    }
}

/** 字幕面板顶上那一段（手机 / 电脑共用 `PanelBody`）：一句话 + 「用上」/「撤销」。没有要说的 = 什么都不画。 */
@Composable
fun AutoBestPanelLine(play: PlaySession) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val t = Guga.player
    val scope = rememberCoroutineScope()
    val snap by play.controller.snapshot.collectAsState()
    val h = AutoBestBoard.hintFor(snap.itemId) ?: return
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp * t.k).testTag("sub-best-panel")) {
        BasicText(if (h.exact) "智能匹配 · 精确" else "智能匹配", style = Guga.song(t.section, color = colors.spot, letterSpacing = 0.3.em), maxLines = 1)
        BasicText(h.detail, Modifier.padding(top = 2.dp * t.k, bottom = 2.dp * t.k), style = Guga.song(t.note, color = colors.ink, lineHeight = t.note * 1.5f), maxLines = 3, overflow = TextOverflow.Ellipsis)
        when (h.action) {
            HintAction.Use -> PanelLink(h.actionLabel ?: "用上") { AutoBestBoard.use(play, g, scope) }
            HintAction.Undo -> PanelLink("撤销") { AutoBestBoard.undo(play) }
            HintAction.None -> Spacer(Modifier.padding(top = 2.dp))
        }
    }
}

/** 搜索栏里「最合适」卡（手机 / 电脑）：小标、字幕名、一两条理由、语言 · 格式 · 来源 · 去向，一个主按钮「用上」。 */
@Composable
fun BestCard(best: ScoredHit, apply: ApplyState?, inPlayer: Boolean, actions: List<HitAction>, onUse: (HitAction) -> Unit) {
    val colors = Guga.colors
    // 两个去向（「保存到服务器」「仅本次播放」，§4.4）时去向写在两个按钮上，小字里不再写
    val c = bestCardText(best, inPlayer, withDestination = actions.size < 2)
    val mine = applyLineOf(best.hit, apply)
    Column(Modifier.padding(top = 12.dp).fillMaxWidth().border(1.dp, colors.ink).padding(horizontal = 14.dp, vertical = 12.dp).testTag("sub-best")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(c.kicker, style = Guga.song(13.sp, FontWeight.Black, colors.spot, letterSpacing = 0.3.em), maxLines = 1)
            BasicText("BEST MATCH", Modifier.padding(start = 8.dp), style = Guga.note(11.sp, colors.faint, letterSpacing = 0.2.em), maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (c.exact) ExactTag(11.sp, 1.dp)
        }
        BasicText(c.name, Modifier.padding(top = 6.dp), style = Guga.song(15.sp, FontWeight.Bold, lineHeight = 21.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (c.reasons.isNotEmpty()) BasicText(c.reasons, Modifier.padding(top = 4.dp), style = Guga.song(12.5.sp, color = colors.ink, lineHeight = 18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        BasicText(c.meta, Modifier.padding(top = 2.dp), style = Guga.song(12.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (mine != null) BasicText(mine.first, Modifier.padding(top = 6.dp).testTag("sub-best-apply"), style = Guga.song(12.sp, color = if (mine.second) colors.seal else colors.spot), maxLines = 2)
        val single = actions.singleOrNull()
        if (single != null) PrimaryButton(
            // 去向写在按钮上（1.5.1 m151）：服务器源 = 保存到服务器，本机源 = 用上
            if (best.hit.toEmby) "保存到服务器" else "用上",
            { onUse(single) },
            Modifier.padding(top = 10.dp).fillMaxWidth().testTag("sub-best-use"),
            icon = null,
            enabled = apply !is ApplyState.Working,
        ) else Row(Modifier.padding(top = 10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 「保存到服务器」（或失败后的「重试」）在前、实底；「仅本次播放」在后、细框
            actions.forEachIndexed { i, a ->
                if (i == 0) PrimaryButton(a.label, { onUse(a) }, Modifier.weight(1f).testTag("sub-best-${a.name.lowercase()}"), icon = null, enabled = apply !is ApplyState.Working)
                else SecondaryAction(a.label, apply !is ApplyState.Working, Modifier.weight(1f).testTag("sub-best-${a.name.lowercase()}")) { onUse(a) }
            }
        }
    }
}

/** 卡上的第二个按钮：米白细框、米白字（和主按钮一样高）。按下放大。 */
@Composable
private fun SecondaryAction(text: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    androidx.compose.foundation.layout.Box(
        modifier.pressGraphics(press).heightIn(min = 46.dp).border(1.dp, colors.line)
            .pressable(press, enabled = enabled, label = text, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Guga.song(Guga.type.lead, FontWeight.Bold, if (enabled) colors.ink else colors.faint, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 「精确匹配」小签：专色细框 + 专色字（不当底色）。 */
@Composable
fun ExactTag(size: androidx.compose.ui.unit.TextUnit, line: Dp, onInk: Boolean = false) {
    val colors = Guga.colors
    val ink = if (onInk) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot
    com.gugaart.ui.components.OutlineTag("精确匹配", size, line, ink, Modifier.testTag("sub-best-exact"))
}

/** 选中这一条以后那一行写什么（进度 / 结果 / 失败），不是这一条 = null。second = 是不是失败（朱红）。 */
fun applyLineOf(hit: SubtitleHit, apply: ApplyState?): Pair<String, Boolean>? = when (apply) {
    is ApplyState.Working -> if (apply.key == hit.key) apply.step to false else null
    is ApplyState.Done -> if (apply.key == hit.key) apply.note to false else null
    is ApplyState.Failed -> if (apply.key == hit.key) apply.why to true else null
    null -> null
}

/** 提示的几个时长。 */
object AutoBestSpec {
    /** 「找到…」第一次出现时亮多久（之后控件出来时才在）。 */
    const val FLASH_MS = 8_000L

    /** 「已自动加载…」亮多久再淡出。 */
    const val LOADED_MS = 6_000L
}

/** 电视页边面板「字幕」一行的小字：有提示就说提示，没有就不写（电视不显示按键提示；纯函数，有单测）。 */
fun tvSubRowDetail(hint: AutoBestHint?): String? = when (hint?.action) {
    HintAction.Use -> hint.text.removePrefix("找到")
    HintAction.Undo -> "已自动加载"
    else -> null
}
