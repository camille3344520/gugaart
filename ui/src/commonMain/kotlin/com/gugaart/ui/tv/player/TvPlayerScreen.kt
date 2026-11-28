package com.gugaart.ui.tv.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.artOf
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.MascotPose
import com.gugaart.ui.player.BITRATES
import com.gugaart.ui.player.LocalPlayerPanelDriver
import com.gugaart.ui.player.LocalPlayerStill
import com.gugaart.ui.player.NewItemCover
import com.gugaart.ui.player.PlaySession
import com.gugaart.ui.player.PlayerPanel
import com.gugaart.ui.player.PlayerSpec
import com.gugaart.ui.player.RATES
import com.gugaart.ui.player.HeatSpec
import com.gugaart.ui.player.drawHeatColumns
import com.gugaart.ui.player.heatDots
import com.gugaart.ui.player.rateLabel
import com.gugaart.ui.player.ShaderPreset
import com.gugaart.ui.player.shaderEntryVisible
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.specLine
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.TvFocusable
import com.gugaart.ui.tv.detail.TvActionButton
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugaart.ui.tv.detail.LocalTvGroups
import com.gugaart.ui.tv.detail.tvFirstSentence
import com.gugaart.ui.tv.detail.tvMember
import com.gugugaga.app.nav.Route
import com.gugugaga.app.platform.localUtcOffsetMs
import com.gugugaga.app.player.AutoNextState
import com.gugugaga.app.player.ControlsVisibility
import com.gugugaga.app.player.DanmakuHost
import com.gugugaga.app.player.ErrorActionId
import com.gugugaga.app.player.KeyState
import com.gugugaga.app.player.KeyTarget
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.player.PlayerKeyCommand
import com.gugugaga.app.player.PlayerKeys
import com.gugugaga.app.player.PlayerSlots
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.player.VeilLayer
import com.gugugaga.app.settings.playbackPrefs
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.PlaybackMode
import com.gugugaga.ds.components.PageBackdrops
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.glass.GlassBackend
import com.gugugaga.ds.glass.GlassScene
import com.gugugaga.ds.glass.MaterialName
import com.gugugaga.ds.glass.glass
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
/** 电视播放器的版式数（设计稿 `tv/css/player.css`，1920 × 1080）。 */
object TvPlayerSpec {
    /** 页边面板打开时画面的矩形：左 48、上 48、1376 × 774。 */
    val INSET = 48.dp
    val VIDEO_W = 1376.dp
    val VIDEO_H = 774.dp
    /** 页边一栏：x 1476 起，右边留 48。 */
    val PANEL_LEFT = 1476.dp
    /** 控件自动隐去的时间（和上游电视一样 5 秒）。 */
    const val HIDE_MS = 5_000L
    /** 一步 10 秒；长按逐级加速：按住 0.8 秒 ×2，2 秒 ×3。每 200 ms 走一步。 */
    const val STEP_MS = 10_000.0
    const val HOLD_TICK_MS = 200L
    const val HOLD_X2_MS = 800L
    const val HOLD_X3_MS = 2_000L
    /** 片尾页里还在播的小窗、下一集的剧照：480 × 270。 */
    val WINDOW_W = 480.dp
    val WINDOW_H = 270.dp
    val DARK = Color(0xFF050504)
    /**
     * 「跳过片头 / 片尾」胶囊离底边多高。平时 96（右下角）；快进时下面那一片是进度条（底 120、高 64）、预览框（底 214、连时间约高 226）、
     * 跳转量（底 250、约高 160），最高到离底边约 440 → 胶囊上移到它们上面（int16b：快进到片尾附近，胶囊压在时间轴右端）。
     */
    val SKIP_BOTTOM = 96.dp
    val SKIP_BOTTOM_SEEK = 472.dp
}

/** 长按快进的档位：按住多久 → ×1 / ×2 / ×3。纯函数，有单测。 */
fun holdLevel(heldMs: Long): Int = when {
    heldMs >= TvPlayerSpec.HOLD_X3_MS -> 3
    heldMs >= TvPlayerSpec.HOLD_X2_MS -> 2
    else -> 1
}

/** 「+ 4:40」「− 0:30」。纯函数，有单测。 */
fun seekDeltaLabel(deltaMs: Double): String = (if (deltaMs < 0) "− " else "+ ") + Rules.clock(kotlin.math.abs(deltaMs).toLong())

/**
 * 快进时「跳转量」那一列要不要换到左下（int16a）：预览框（左边在 [previewX]，宽 326）的右边加 24 的间隔碰到右下那一列（右边距 96、宽 [deltaWidth]）
 * 就换。[deltaWidth] 还没量出来（第一帧）时按 240 估（「+ 00:10」72sp 的宽），免得先在右边闪一帧再挪。纯函数，有单测。
 */
fun seekDeltaOnLeft(previewX: Dp, deltaWidth: Dp): Boolean =
    previewX + 326.dp + 24.dp > 1920.dp - 96.dp - maxOf(deltaWidth, 240.dp)

/** 页边面板的一行里，值往左 / 往右一格（到头不绕回）。纯函数，有单测。 */
fun stepIndex(i: Int, n: Int, dir: Int): Int = if (n <= 0) 0 else (i + dir).coerceIn(0, n - 1)

/** 「预计 22:06 播完」：现在 [hour]:[minute] + 剩下的时间 / 倍速。纯函数，有单测。 */
fun endClock(hour: Int, minute: Int, remainMs: Double, rate: Double): String {
    val total = hour * 60 + minute + (remainMs / 60_000.0 / rate.coerceAtLeast(0.1)).let { kotlin.math.ceil(it).toInt() }
    val h = (total / 60) % 24
    val m = total % 60
    return "$h:${if (m < 10) "0$m" else m.toString()}"
}

/** 测试缝：播放器顶栏的时钟（时、分）。应用里是 null = 系统时间。 */
val LocalTvPlayerClock = staticCompositionLocalOf<Pair<Int, Int>?> { null }

/** 测试缝：一开始把焦点放在控件的哪个键上（截「焦点在进度条 / 功能键」这两屏）。应用里是 null = 播放键。 */
val LocalTvPlayerFocus = staticCompositionLocalOf<String?> { null }

/** 页边面板的六行。 */
private val ROWS = listOf(PlayerPanel.Eps, PlayerPanel.Sub, PlayerPanel.Audio, PlayerPanel.Speed, PlayerPanel.Quality, PlayerPanel.Core, PlayerPanel.Dm)

/**
 * 页边一行功能下面的子行（着色器增强、字幕的字号 / 字体、弹幕的字体 / 粗细）：上下走到它、左右直接改值（不用按确定），改完引擎 / 弹幕立即跟着变。
 */
private class ExtraRow(val key: String, val word: String, val english: String, val values: List<String>, val index: Int, val detail: String?, val step: ((dir: Int) -> Unit)? = null, val ok: (() -> Unit)? = null, val button: Boolean = false, val pick: ((Int) -> Unit)? = null, val current: Int = index, val apply: (Int) -> Unit)

/** 一行的值：有哪些、现在是第几个、选第 i 个时做什么。[deferred] = 左右只是挑，按确定才换（选集、画质：一换就要重新起播）。 */
private class RowModel(val values: List<String>, val index: Int, val detail: (Int) -> String?, val deferred: Boolean, val apply: (Int) -> Unit)

/**
 * 电视播放器的画报外观（`docs/player.md` §电视，设计稿 `tv/index.html?p=player`）。逻辑（`PlayController`、引擎回退链、弹幕）一行不动，
 * 键位照上游 `PlayerKeys`（电视那一段）：
 * - **看片时**（控件隐去，画面上什么都没有）：左右 = 退 / 进 10 秒（按住逐级加速到 ×3，松开才跳，带预览）；确定 = 暂停并出控件；
 *   上 = 出控件（焦点在进度条）；下 = 打开页边面板。
 * - **控件**：第一行进度条（左右拖）；第二行 上一集 / 退 10 / 播放（默认焦点）/ 进 10 / 下一集 ｜ 选集 字幕 音轨 倍速 画质 弹幕 收藏——每个都有焦点。
 * - **页边面板 = 画面让位**：画面缩到左边，页边一栏上下换功能、左右直接改值；返回收起。
 * - 「跳过片头」一出现就拿到焦点（玻璃胶囊）；片尾进行中 / 放完 = 整幅「下一集」页，「立即播放」拿到焦点。
 * - 没有手势、没有锁、没有亮度 / 音量条；屏幕上没有返回按钮（返回键）；收藏在控件第二行最右。她只在暂停时坐进顶栏那一格。
 */
@OptIn(ExperimentalTime::class)
@Composable
fun TvPlayerScreen(play: PlaySession, danmaku: PlayerDanmaku, slots: PlayerSlots) {
    val g = LocalGraph.current
    val controller = play.controller
    val colors = Guga.colors
    val preset = LocalPreset.current
    val scope = rememberCoroutineScope()
    val snap by controller.snapshot.collectAsState()
    // 时钟（每 250 ms 走一下）不在这一层读（1.5.1 性能）：只在顶栏、进度条、片尾那几格各自订阅，整个播放页不跟着每一下重组
    com.gugaart.ui.player.RememberShaderApply(play) // 画质面板记住的着色器选择：引擎 / 选择一变就应用（电视现在也有选择入口）
    com.gugaart.ui.player.RememberSecondSubtitleApply(play) // 双语字幕（1.6 C1）：第二字幕按片记住、交给 mpv
    val clockDurationMs by remember(controller) { controller.clock.map { it.durationMs }.distinctUntilChanged() }.collectAsState(controller.clock.value.durationMs)
    val engine by controller.engineFlow.collectAsState()
    // 着色器增强（画质面板第二行）：按片记住（GugaPrefs.shaderFor），只在 mpv 系内核 + 能写文件的平台画
    val shaderStore = com.gugaart.ui.screens.settings.rememberGugaStore()
    val shaderPrefs by shaderStore.prefs.collectAsState()
    val shaderHost = com.gugaart.ui.data.LocalFontImport.current
    val shaderVisible = shaderEntryVisible(engine?.shaders != null, shaderHost != null)
    val shaderKey = com.gugaart.ui.data.shaderItemKey(snap.item?.seriesId, snap.itemId)
    val activeShader = shaderPrefs.shaderFor(shaderKey)
    val shaderIndex = ShaderPreset.entries.indexOf(activeShader).coerceAtLeast(0)
    val settings by danmaku.settingsState.collectAsState()
    val seekStepSec = remember(settings) { settings.playbackPrefs.seekStepSec.coerceAtLeast(1) }
    val stepNow by rememberUpdatedState(seekStepSec * 1_000.0)
    val danmakuConfigured = com.gugaart.ui.danmaku.rememberDanmakuConfigured()
    val danmakuUnconfigured = (danmaku.state.phase == com.gugugaga.danmaku.match.DanmakuPhase.Unconfigured || !danmakuConfigured) &&
        com.gugaart.ui.player.LocalDanmakuSlotOverride.current == null &&
        com.gugaart.ui.player.DanmakuPickerBoard.stateOverride == null
    val render = remember(settings) { danmaku.prefsNow().render }
    // 字幕样式的两行快调（字号 / 字体）和弹幕的字体 / 粗细（1.4.1）：和手机 / 电脑、设置页同一批键
    val subStyle = shaderPrefs.subStyle
    val subFonts = com.gugaart.ui.player.rememberSubtitleFontChoices()
    val dmFonts = com.gugaart.ui.player.rememberDanmakuFontChoices(danmaku, render.font)
    val controls = remember { ControlsVisibility(scope, TvPlayerSpec.HIDE_MS) }
    val groups = remember { TvGroups() }
    val back = LocalBackDispatcher.current
    val rootFocus = remember { FocusRequester() }

    var panel by remember { mutableStateOf(PlayerPanel.of(preset.playerPanel)?.takeIf { it in ROWS }) }
    var prevPanel by remember { mutableStateOf(panel) }
    LaunchedEffect(panel) { if (prevPanel != panel) { com.gugaart.ui.tv.TvPerfTiming.onPanelToggle(panel != null, (panel ?: prevPanel)?.id ?: "panel"); prevPanel = panel } }
    LaunchedEffect(play) { com.gugaart.ui.tv.TvPerfTiming.onPlayerContent(snap.itemId, ready = false) }
    LaunchedEffect(snap.phase, snap.itemId) { if (snap.phase == PlayPhase.Ready) com.gugaart.ui.tv.TvPerfTiming.onPlayerContent(snap.itemId, ready = true) }
    LocalPlayerPanelDriver.current?.let { driver -> LaunchedEffect(driver.value) { panel = PlayerPanel.of(driver.value)?.takeIf { it in ROWS } } }
    var osd by remember { mutableStateOf(preset.playerControls) }
    var dismissedSkip by remember(snap.itemId) { mutableStateOf<String?>(null) }
    var dismissedNext by remember(snap.itemId) { mutableStateOf(false) }
    // 选集带（用户 2026-11-15：「选集的部分没地方选集」「选季不是集」）：页边「选集」一行按确定 / 遥控器的选集键打开
    val eps = com.gugaart.ui.player.LocalPlayerEpisodes.current
    var strip by remember { mutableStateOf(preset.playerPanel == "eps-strip") }
    // 长按快进：方向（±1）、从哪开始、现在挑到哪、档位
    var holdDir by remember { mutableIntStateOf(0) }
    var holdFrom by remember { mutableStateOf(0.0) }
    var holdTo by remember { mutableStateOf(0.0) }
    var holdLevel by remember { mutableIntStateOf(1) }
    var seekShown by remember { mutableStateOf(false) }
    // 字幕时间：左右键按下的时刻（按住满 2 秒换大步）
    var timingDown by remember { mutableStateOf<kotlin.time.TimeSource.Monotonic.ValueTimeMark?>(null) }
    fun timingHeldMs(): Long = timingDown?.elapsedNow()?.inWholeMilliseconds ?: 0L

    val playing = snap.phase == PlayPhase.Ready && !snap.paused
    val duration = clockDurationMs.takeIf { it > 0 } ?: snap.resolved?.durationMs?.toDouble() ?: 0.0
    // 网速：一直在采样，控件一出来就有数（NetSpeed.kt）
    val speed = com.gugaart.ui.player.rememberNetSpeed(play.controller)
    val snapNow by rememberUpdatedState(snap)
    val durationNow by rememberUpdatedState(duration)
    // 宿主环境（和手机版、上游 PlayPage 一样）：播放时屏幕常亮
    val awake = playing || snap.phase == PlayPhase.Preparing || snap.phase == PlayPhase.Loading
    LaunchedEffect(awake) { play.platform.keepAwake(awake) }
    DisposableEffect(play) {
        play.platform.playerVisible(true)
        onDispose {
            play.platform.playerVisible(false)
            play.platform.keepAwake(false)
        }
    }
    // 控件 5 秒不碰自动隐去（暂停、面板开着、钉住时不隐）
    LaunchedEffect(playing, panel, osd) { controls.canHide = playing && panel == null && osd && !preset.playerPin }
    LaunchedEffect(controls.visible) { if (!controls.visible) osd = false }
    // 暂停一定出控件
    LaunchedEffect(snap.paused, snap.phase) { if (snap.paused && snap.phase == PlayPhase.Ready) osd = true }
    // [tvperf]（只在测试包里开）：按键 → 暂停 / 字幕 / 音轨在界面上变了的那一帧（批次 mpvanr）
    val reacted = remember(snap.itemId) { ArrayList<Triple<Boolean, Int?, Int?>>(1) } // 不是状态：记上一次，不引起重组
    LaunchedEffect(snap.phase, snap.paused, snap.subtitleIndex, snap.audioIndex) {
        if (snap.phase != PlayPhase.Ready) return@LaunchedEffect
        val now = Triple(snap.paused, snap.subtitleIndex, snap.audioIndex)
        val was = reacted.firstOrNull()
        reacted.clear()
        reacted += now
        if (was == null) return@LaunchedEffect
        when {
            was.first != now.first -> com.gugaart.ui.tv.TvPerfTiming.onPlayerReaction(if (now.first) "pause" else "play")
            was.second != now.second -> com.gugaart.ui.tv.TvPerfTiming.onPlayerReaction("subtitle")
            was.third != now.third -> com.gugaart.ui.tv.TvPerfTiming.onPlayerReaction("audio")
        }
    }
    // 缓冲超过 150 ms 才出现
    var showBuffer by remember { mutableStateOf(false) }
    LaunchedEffect(snap.buffering, snap.phase) {
        showBuffer = preset.playerBuffer
        if (snap.buffering || snap.phase == PlayPhase.Loading || snap.phase == PlayPhase.Preparing) {
            delay(150)
            showBuffer = true
        }
    }

    val client = g.session.session.collectAsState().value?.client
    val art = remember(snap.item, client) { snap.item?.let { client?.artOf(it) } ?: Art() }
    val heat = com.gugaart.ui.player.LocalHeatPreview.current ?: remember(danmaku.heat) { heatDots(danmaku.heat?.model?.values) }
    val skip = snap.skip?.takeIf { snap.phase == PlayPhase.Ready && panel == null && dismissedSkip != it.kind.name }
    val credits = snap.autoNext != AutoNextState.Idle && snap.next != null && !dismissedNext && snap.phase == PlayPhase.Ready
    val stripOpen = strip && eps != null
    // 弹幕来源（左边一栏）：有歧义时自己出来一次；页边面板「弹幕」一行按确定也打开（tv/player/TvDanmakuPicker.kt）
    LaunchedEffect(danmaku.asksChoice, snap.itemId) { com.gugaart.ui.player.DanmakuPickerBoard.settle(danmaku, snap.itemId) }
    LaunchedEffect(danmaku.state.phase, danmaku.state.title, snap.itemId) { com.gugaart.ui.player.DanmakuSeasonGuard.check(danmaku, snap.itemId) }
    DisposableEffect(Unit) { onDispose { com.gugaart.ui.player.DanmakuPickerBoard.open = false } }
    val pickOpen = com.gugaart.ui.player.DanmakuPickerBoard.open && snap.phase != PlayPhase.Error
    var pickEditing by remember { mutableStateOf(false) }
    val dmSearch = remember { com.gugaart.ui.player.DanmakuSearch() }
    // 字幕搜索（左边一栏，tv/player/TvSubtitleSearch.kt）：页边面板「字幕」一行按确定打开；长按菜单的「搜索字幕…」开播后自己打开
    val subSearch = com.gugaart.ui.subtitle.SubtitleSearchBoard.session?.takeIf { com.gugaart.ui.subtitle.SubtitleSearchBoard.play === play }
    val subOpen = subSearch != null && snap.phase != PlayPhase.Error
    var subEditing by remember { mutableStateOf(false) }
    // 迅雷 / 射手那一条按确定后原地展开的「保存到服务器」「仅本次播放」挂在哪一条（null = 没展开）；返回先收它
    var subExpanded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(subSearch) { subExpanded = null }
    LaunchedEffect(snap.itemId, snap.phase) {
        if (com.gugaart.ui.subtitle.SubtitleSearchBoard.applyPending(play, snap.itemId, snap.phase)) return@LaunchedEffect
        if (com.gugaart.ui.subtitle.SubtitleSearchBoard.openOnPlay == snap.itemId && snap.phase == PlayPhase.Ready) {
            com.gugaart.ui.subtitle.SubtitleSearchBoard.openOnPlay = null
            com.gugaart.ui.subtitle.SubtitleSearchBoard.openInPlayer(play, g)
        }
    }
    DisposableEffect(play) { onDispose { if (com.gugaart.ui.subtitle.SubtitleSearchBoard.play === play) com.gugaart.ui.subtitle.SubtitleSearchBoard.close() } }
    val showOsd = osd && panel == null && !credits && skip == null && !stripOpen && !pickOpen && !subOpen

    // ---- 页边面板每一行的值（都是上游已有的命令）----
    var pendingEp by remember(snap.itemId, snap.episodes.size) { mutableIntStateOf(snap.episodes.indexOfFirst { it.id == snap.itemId }.coerceAtLeast(0)) }
    var pendingQuality by remember(snap.maxBitrateMbps) { mutableIntStateOf(BITRATES.indexOf(snap.maxBitrateMbps).coerceAtLeast(0)) }
    val coreOpts = remember(play) { com.gugaart.ui.player.coreOptionsFor(play, g) }
    // P1-1：这一部的内核（按片记住；没记 = 全局首选）；和默认不一样时下面多一行「设为默认」
    val settingsNow by g.settings.state.collectAsState()
    val coreGlobal = remember(settingsNow) { com.gugaart.ui.player.currentEnginePref(g) }
    val corePref = remember(settingsNow, snap.itemId, play) { com.gugaart.ui.player.filmEnginePref(play, g) }
    // 1.6 mpvcopy2：实际在用的内核和首选不一样（4K 自动改用 EXO）时，从正在用的那个开始
    val coreShown = com.gugaart.ui.player.shownEnginePref(corePref, snap.engineId)
    var pendingCore by remember(coreShown) { mutableIntStateOf(coreOpts.indexOfFirst { it.pref == coreShown }.coerceAtLeast(0)) }
    // 字幕智能匹配（1.4.1 U2）：字幕一行的小字跟着提示走
    val bestHint = com.gugaart.ui.subtitle.AutoBestBoard.hintFor(snap.itemId)
    // 1.6 A2：页边「画质」下面一行「版本 / 源」：本台的版本在前，别台的源接在后面（和手机 / 电脑的面板同一份数据，player/PlayerPanels.kt）。
    // 左右先挑、确定才换（换源要重新起播）；别台的源从当前位置接着放（上游 switchServer，进度只报正在播的那台）。页边开着才去问。
    val panelSources = if (panel != null) com.gugaart.ui.player.rememberPlayerSources(play, snap) else null
    val sourceList = panelSources?.sources.orEmpty()
    val playingSource = sourceList.indexOfFirst { com.gugaart.ui.player.isPlayingSource(play, snap, it) }
    var pendingSource by remember(playingSource, sourceList.size) { mutableIntStateOf(playingSource.coerceAtLeast(0)) }
    fun rowOf(p: PlayerPanel): RowModel = when (p) {
        PlayerPanel.Eps -> if (eps != null) RowModel(
            listOf(listOfNotNull(eps.season?.let { com.gugaart.ui.player.seasonWord(it) }, snap.item?.indexNumber?.let { "第 $it 集" }).joinToString(" · ").ifEmpty { "选集" }),
            0, { null }, deferred = false,
        ) { strip = true } else RowModel(snap.episodes.mapIndexed { i, e -> "第 ${e.indexNumber ?: (i + 1)} 集" }, pendingEp, { snap.episodes.getOrNull(it)?.name }, deferred = true) { i ->
            snap.episodes.getOrNull(i)?.let { e ->
                if (e.id != snap.itemId) {
                    g.queue.nowPlaying(e.id)
                    g.nav.replace(Route.Play(com.gugaart.ui.data.RemoteBrowse.wrap(g.nav, e.id)))
                }
            }
        }
        PlayerPanel.Sub -> {
            val subs = com.gugaart.ui.player.orderedSubtitles(snap)
            RowModel(listOf("关") + subs.map { com.gugaart.ui.player.trackLabel(snap, it, "Subtitle").line }, if (snap.subtitleIndex == null) 0 else subs.indexOfFirst { it.index == snap.subtitleIndex } + 1, { com.gugaart.ui.subtitle.tvSubRowDetail(bestHint) }, deferred = false) { i ->
                play.chooseSubtitle(if (i == 0) null else subs.getOrNull(i - 1)?.index)
            }
        }
        PlayerPanel.Audio -> RowModel(snap.audio.map { com.gugaart.ui.player.trackLabel(snap, it, "Audio").line }, snap.audio.indexOfFirst { it.selected }.coerceAtLeast(0), { null }, deferred = false) { i ->
            snap.audio.getOrNull(i)?.index?.let(controller::selectAudio)
        }
        PlayerPanel.Speed -> RowModel(RATES.map(::rateLabel), RATES.indexOfFirst { kotlin.math.abs(it - snap.rate) < 0.01 }.coerceAtLeast(0), { null }, deferred = false) { i -> controller.setRate(RATES[i]) }
        PlayerPanel.Quality -> RowModel(
            BITRATES.map { b -> if (b == 0.0) listOfNotNull("原画", specLine(snap.resolved?.mediaSource)?.substringBefore(" · ")).joinToString(" · ") else "${b.toInt()} Mbps · 转码" },
            pendingQuality, { null }, deferred = true,
        ) { i -> controller.setMaxBitrate(BITRATES[i]) }
        // 内核：自动 · EXO · VLC · MPV（先挑、确定才换：换内核要重开一次）
        PlayerPanel.Core -> RowModel(
            coreOpts.map { it.label },
            pendingCore, { i -> coreOpts.getOrNull(i)?.let { o -> if (o.pref == com.gugugaga.app.player.EnginePref.Auto) "现在 ${com.gugaart.ui.player.engineShort(snap.engineId)} · ${o.note}" else o.note } }, deferred = true,
        ) { i -> coreOpts.getOrNull(i)?.let { com.gugaart.ui.player.PlayerRestart.switchEngine(play, g, it.pref) } }
        else -> if (danmakuUnconfigured) {
            RowModel(listOf("未配置"), 0, { null }, deferred = false) { g.nav.navigate(Route.Settings("danmaku")) }
        } else {
            RowModel(listOf("关", "开"), if (render.enabled) 1 else 0, { com.gugaart.ui.player.DanmakuSeasonGuard.status(snap.itemId) ?: slots.danmakuStatus?.invoke() }, deferred = false) { i -> danmaku.updateRender("enabled" to JsonPrimitive(i == 1)) }
        }
    }
    // 双语字幕（1.6 C1）：只有 mpv 内核才有「第二字幕」这一行
    val secondRow = com.gugaart.ui.player.rememberSecondSubtitleRow(play, snap)
    // P2-9：能选的内核不到两个时不显示「内核」
    val rows = ROWS.filter { (it != PlayerPanel.Eps || eps != null || snap.episodes.isNotEmpty()) && (it != PlayerPanel.Core || com.gugaart.ui.player.coreWordVisible(coreOpts)) }
    /** 每行功能下面的子行。 */
    fun extrasOf(p: PlayerPanel): List<ExtraRow> = when (p) {
        PlayerPanel.Quality -> listOfNotNull(
            panelSources?.takeIf { sourceList.size > 1 }?.let { st ->
                val at = pendingSource.coerceIn(0, sourceList.size - 1)
                val r = st.rowText(sourceList[at])
                val detail = listOfNotNull(
                    "正在放".takeIf { at == playingSource },
                    r.spec.takeIf { it.isNotEmpty() },
                    "推荐".takeIf { r.recommended && at != playingSource },
                    st.progressText.searching.firstOrNull() ?: st.progressText.summary,
                ).joinToString(" · ").ifEmpty { null }
                ExtraRow(
                    "pr-source", st.sectionTitle, st.sectionEnglish, sourceList.map { st.rowText(it).title }, at, detail,
                    pick = { i -> pendingSource = i }, current = playingSource,
                ) { i ->
                    sourceList.getOrNull(i)?.let { s ->
                        if (i != playingSource) {
                            st.pick(s)
                            com.gugaart.ui.player.choosePlayerSource(play, g, snap, s)
                        }
                    }
                }
            },
            if (shaderVisible) ExtraRow("pr-shader", "着色器增强", "SHADER", ShaderPreset.entries.map { it.label }, shaderIndex, activeShader.note) { i -> shaderStore.update { it.withItemShader(shaderKey, ShaderPreset.entries[i]) } } else null,
        )
        PlayerPanel.Sub -> listOfNotNull(
            secondRow?.let { r -> ExtraRow("pr-sub-second", "第二字幕", "SECOND", r.labels, r.at, null) { i -> r.pick(i) } },
            ExtraRow("pr-sub-scale", "字幕字号", "SIZE", com.gugaart.ui.data.SUB_SCALES.map { com.gugaart.ui.data.subScaleText(it) }, nearestScale(subStyle.scale), null) { i ->
                shaderStore.update { it.copy(subStyle = it.subStyle.copy(scale = com.gugaart.ui.data.SUB_SCALES[i])) }
            },
            ExtraRow("pr-sub-font", "字幕字体", "FONT", subFonts.map { it.label }, subFonts.indexOfFirst { it.selected }.coerceAtLeast(0), subFonts.firstOrNull { it.selected }?.note) { i -> subFonts.getOrNull(i)?.pick?.invoke() },
        ) + (if (snap.subtitleIndex == null) emptyList() else listOf(
            // 字幕时间（审计批次 P）：左右 ±0.1 秒（按住满 2 秒每次 0.5 秒），确定 = 复位；按「这一集 + 这条字幕」记住
            ExtraRow(
                "pr-sub-timing", "字幕时间", "TIMING", listOf(com.gugaart.ui.player.subtitleTimingText(snap.subtitleDelayMs)), 0, null,
                step = { d -> play.stepSubtitleTiming(d * com.gugaart.ui.player.subtitleTimingStep(timingHeldMs())) }, ok = { play.setSubtitleTiming(0) },
            ) { },
        ))
        PlayerPanel.Core -> if (corePref == coreGlobal) emptyList() else listOf(
            ExtraRow("pr-core-default", "设为默认", "DEFAULT", listOf(coreOpts.firstOrNull { it.pref == corePref }?.label ?: ""), 0, null, ok = {
                com.gugaart.ui.player.setEngineDefault(play, g, corePref)
                // 这一行随即收起：焦点交回「内核」那一行
                scope.launchFocus(g.focus, "pr-core")
            }, button = true) { },
        )
        PlayerPanel.Dm -> listOf(
            ExtraRow("pr-dm-font", "弹幕字体", "FONT", dmFonts.map { it.label }, dmFonts.indexOfFirst { it.selected }.coerceAtLeast(0), dmFonts.firstOrNull { it.selected }?.note) { i -> dmFonts.getOrNull(i)?.pick?.invoke() },
            ExtraRow("pr-dm-weight", "弹幕粗细", "WEIGHT", com.gugugaga.danmaku.settings.DanmakuWeight.entries.map { it.label }, com.gugugaga.danmaku.settings.DanmakuWeight.entries.indexOf(render.weight).coerceAtLeast(0), null) { i ->
                danmaku.updateRender("weight" to JsonPrimitive(com.gugugaga.danmaku.settings.DanmakuWeight.entries[i].id))
            },
        )
        else -> emptyList()
    }

    // ---- 这一页的方向键路径 ----
    groups.edge = { from, dir ->
        if (pickOpen || subOpen) TvGroups.STAY else if (stripOpen) tvStripEdge(from, dir, groups::has) else when (from) {
            "bar" -> if (dir == TvDir.Down) "brow" else TvGroups.STAY
            "brow" -> when (dir) {
                TvDir.Up -> "bar"
                TvDir.Down -> {
                    // 第二行再按下 = 打开页边面板（焦点在功能键上就打开那一栏）
                    panel = groups.current?.removePrefix("fn-")?.let(PlayerPanel::of)?.takeIf { it in rows } ?: rows.firstOrNull { it == PlayerPanel.Sub } ?: rows.first()
                    TvGroups.STAY
                }
                else -> TvGroups.STAY
            }
            else -> TvGroups.STAY
        }
    }
    val bump = { controls.bump() }

    fun showControls(focusKey: String) {
        osd = true
        controls.bump()
        scope.launchFocus(g.focus, focusKey)
    }
    fun closePanel() {
        panel = null
        if (osd) scope.launchFocus(g.focus, "osd-play") else runCatching { rootFocus.requestFocus() }
    }

    // 返回 = 收起最上面一层：面板 → 跳过提示 → 控件；都没有 → 外壳（退出播放）
    DisposableEffect(panel, showOsd, skip != null, credits, stripOpen, pickOpen, pickEditing, subOpen, subEditing, subExpanded) {
        val un = when {
            subOpen -> back.push {
                when {
                    subEditing -> subEditing = false
                    subExpanded != null -> subExpanded = null
                    else -> com.gugaart.ui.subtitle.SubtitleSearchBoard.close()
                }
                true
            }
            pickOpen -> back.push {
                if (pickEditing) pickEditing = false else com.gugaart.ui.player.DanmakuPickerBoard.open = false
                true
            }
            stripOpen -> back.push {
                strip = false
                true
            }
            panel != null -> back.push {
                closePanel()
                true
            }
            skip != null -> back.push {
                dismissedSkip = skip.kind.name
                runCatching { rootFocus.requestFocus() }
                true
            }
            showOsd && !(snap.paused && snap.phase == PlayPhase.Ready) -> back.push {
                osd = false
                runCatching { rootFocus.requestFocus() }
                true
            }
            else -> null
        }
        onDispose { un?.invoke() }
    }
    // 焦点跟着状态走：没有控件 → 这一页自己拿着（收方向键）；出了什么 → 给它
    val startKey = LocalTvPlayerFocus.current
    LaunchedEffect(panel, showOsd, skip != null, credits, snap.phase, stripOpen, pickOpen, subOpen) {
        when {
            snap.phase == PlayPhase.Error || snap.phase == PlayPhase.Ended || credits -> Unit
            subOpen -> {
                panel = null
                groups.enterWhenReady("sbs", g.focus)
            }
            pickOpen -> {
                panel = null
                groups.enterWhenReady("dmp", g.focus)
            }
            stripOpen -> {
                panel = null
                groups.enterWhenReady("ps-eps", g.focus) || groups.enterWhenReady("ps-seasons", g.focus)
            }
            panel != null -> g.focus.awaitRequest(startKey?.takeIf { it.startsWith("pr-") } ?: "pr-${panel!!.id}")
            skip != null -> g.focus.awaitRequest("skip")
            showOsd -> if (groups.current == null || groups.currentGroup == "panel") g.focus.awaitRequest(startKey ?: "osd-play")
            else -> runCatching { rootFocus.requestFocus() }
        }
    }
    // 选集带里焦点到哪季分集就换成哪季、到哪页就翻到哪页（和详情页一样，不用按确定）
    val stripCur = groups.current
    LaunchedEffect(stripCur, stripOpen) {
        if (!stripOpen || stripCur == null) return@LaunchedEffect
        when {
            stripCur.startsWith("ps-season-") -> {
                eps?.pickSeason(stripCur.removePrefix("ps-season-"))
                groups.forget("ps-eps")
                groups.forget("ps-pages")
            }
            stripCur.startsWith("ps-page-") -> stripCur.removePrefix("ps-page-").toIntOrNull()?.let {
                eps?.pickPage(it)
                groups.forget("ps-eps")
            }
        }
    }
    // 长按快进：按住期间每 200 ms 走一步，越按越快；松开才跳
    LaunchedEffect(holdDir) {
        if (holdDir == 0) return@LaunchedEffect
        var held = 0L
        while (true) {
            delay(TvPlayerSpec.HOLD_TICK_MS)
            held += TvPlayerSpec.HOLD_TICK_MS
            holdLevel = holdLevel(held)
            holdTo = (holdTo + holdDir * stepNow * holdLevel).coerceIn(0.0, durationNow.takeIf { it > 0 } ?: Double.MAX_VALUE)
            controller.scrubTo(holdTo)
        }
    }
    LaunchedEffect(seekShown, holdDir) {
        if (seekShown && holdDir == 0) {
            delay(900)
            seekShown = false
        }
    }

    val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = onKey@{ e ->
        val down = e.type == KeyEventType.KeyDown
        val dir = when (e.key) {
            Key.DirectionLeft -> TvDir.Left
            Key.DirectionRight -> TvDir.Right
            Key.DirectionUp -> TvDir.Up
            Key.DirectionDown -> TvDir.Down
            else -> null
        }
        val phase = snapNow.phase
        // 出错页 / 片尾页：自己的按钮行管方向键
        if (phase == PlayPhase.Error || phase == PlayPhase.Ended || credits) {
            return@onKey if (dir != null && down) groups.move(dir, g.focus) || true else dir != null
        }
        // 字幕搜索：方向键交给它那一栏（正在输入名字时交给输入框）
        if (subOpen) {
            return@onKey if (subEditing) false else if (dir != null && down) groups.move(dir, g.focus) || true else dir != null
        }
        // 弹幕来源：方向键交给它那一栏（正在输入片名时交给输入框）
        if (pickOpen) {
            return@onKey if (pickEditing) false else if (dir != null && down) groups.move(dir, g.focus) || true else dir != null
        }
        // 松开左右：长按快进落地；字幕时间的按住计时归零
        if (!down) {
            if (e.key == Key.DirectionLeft || e.key == Key.DirectionRight) timingDown = null
            if (holdDir != 0 && (e.key == Key.DirectionLeft || e.key == Key.DirectionRight)) {
                holdDir = 0
                controller.seekTo(holdTo)
                controller.setScrubbing(false)
                return@onKey true
            }
            return@onKey false
        }
        when {
            // 「跳过片头」拿着焦点：确定执行（按钮自己的），任何方向键放弃
            skip != null -> {
                if (dir != null) {
                    dismissedSkip = skip.kind.name
                    runCatching { rootFocus.requestFocus() }
                    true
                } else false
            }
            // 选集带：四个方向都交给它的组（确定由焦点那一张卡自己处理）
            stripOpen -> if (dir != null) groups.move(dir, g.focus) || true else false
            // 页边面板：上下换功能，左右改值（电视不显示按键提示：‹ › 和能选中的按钮已经说明了）
            panel != null -> {
                val p = groups.current?.removePrefix("pr-")?.let(PlayerPanel::of)
                when (dir) {
                    TvDir.Up, TvDir.Down -> groups.move(dir, g.focus) || true
                    TvDir.Left, TvDir.Right -> {
                        val cur = groups.current
                        val extra = rows.flatMap(::extrasOf).firstOrNull { it.key == cur }
                        if (extra?.step != null) {
                            if (timingDown == null) timingDown = kotlin.time.TimeSource.Monotonic.markNow()
                            extra.step.invoke(if (dir == TvDir.Right) 1 else -1)
                        } else if (extra != null) {
                            // 子行（着色器、字幕字号 / 字体、弹幕字体 / 粗细）：左右直接改值，改完立即生效；「源」只是挑（确定才换）
                            val next = stepIndex(extra.index, extra.values.size, if (dir == TvDir.Right) 1 else -1)
                            if (next != extra.index) extra.pick?.invoke(next) ?: extra.apply(next)
                        } else if (p != null) {
                            val row = rowOf(p)
                            val next = stepIndex(row.index, row.values.size, if (dir == TvDir.Right) 1 else -1)
                            when {
                                p == PlayerPanel.Eps -> pendingEp = next
                                p == PlayerPanel.Quality -> pendingQuality = next
                                p == PlayerPanel.Core -> pendingCore = next
                                next != row.index -> row.apply(next)
                            }
                        }
                        true
                    }
                    null -> false
                }
            }
            // 控件：每个键都有焦点；进度条上左右 = 拖
            showOsd -> {
                bump()
                if (dir == null) false
                else if (groups.current == "osd-bar" && (dir == TvDir.Left || dir == TvDir.Right)) {
                    controller.seekBy(if (dir == TvDir.Right) stepNow else -stepNow)
                    true
                } else groups.move(dir, g.focus) || true
            }
            // 看片时：键位照上游 PlayerKeys（电视）
            else -> {
                val cmd = PlayerKeys.eventLike(e)?.let { PlayerKeys.resolve(it, KeyState(UiMode.Tv, controlsVisible = false, skipVisible = false, target = KeyTarget.Other)) }
                when (cmd) {
                    is PlayerKeyCommand.Seek -> {
                        if (holdDir == 0) {
                            holdDir = cmd.dir
                            holdLevel = 1
                            holdFrom = controller.positionMs()
                            holdTo = (holdFrom + cmd.dir * stepNow * (if (cmd.big == true) 3 else 1)).coerceAtLeast(0.0)
                            seekShown = true
                            controller.setScrubbing(true)
                            controller.scrubTo(holdTo)
                        }
                        true
                    }
                    PlayerKeyCommand.PlayPause -> {
                        controller.userGesture()
                        controller.togglePlay()
                        showControls("osd-play")
                        true
                    }
                    is PlayerKeyCommand.ShowControls -> {
                        showControls("osd-bar")
                        true
                    }
                    PlayerKeyCommand.Episodes -> {
                        panel = rows.firstOrNull { it == PlayerPanel.Sub } ?: rows.first()
                        true
                    }
                    PlayerKeyCommand.Next -> { controller.playNext(); true }
                    PlayerKeyCommand.Prev -> { controller.playPrev(); true }
                    PlayerKeyCommand.Stop -> { play.exit(); true }
                    else -> false
                }
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTvGroups provides groups) {
        Box(
            Modifier.fillMaxSize().focusRequester(rootFocus).onPreviewKeyEvent(onKey).focusable().testTag(
                when {
                    snap.phase == PlayPhase.Error -> "tv-player-error"
                    subOpen -> "tv-player-subsearch"
                    pickOpen -> "tv-player-dmpick"
                    stripOpen -> "tv-player-strip"
                    panel != null -> "tv-player-panel-${panel!!.id}"
                    showOsd -> "tv-player-osd"
                    else -> "tv-player"
                },
            ),
        ) {
            when {
                snap.phase == PlayPhase.Error -> TvPlayerError(play, snap)
                snap.phase == PlayPhase.Ended || credits -> TvPlayerNext(play, snap, art, live = credits, duration = duration, engine = { m -> if (credits) Picture(play, snap, danmaku, slots, m, dim = 0f, danmakuTop = 0.dp, duration = duration) }, onStay = {
                    dismissedNext = true
                    controller.cancelAutoNext()
                })
                subOpen -> {
                    // ---- 字幕搜索：画面照常全幅在后面放，左边一栏墨色的纸 ----
                    Picture(play, snap, danmaku, slots, Modifier.fillMaxSize(), dim = if (snap.paused) PlayerSpec.PAUSE_DIM else 0f, danmakuTop = 0.dp, duration = duration)
                    TvSubtitleSearch(subSearch!!, subEditing, { subEditing = it }, subExpanded, { subExpanded = it }) {
                        subEditing = false
                        subExpanded = null
                        com.gugaart.ui.subtitle.SubtitleSearchBoard.close()
                    }
                }
                pickOpen -> {
                    // ---- 弹幕来源：画面照常全幅在后面放，左边一栏墨色的纸 ----
                    Picture(play, snap, danmaku, slots, Modifier.fillMaxSize(), dim = if (snap.paused) PlayerSpec.PAUSE_DIM else 0f, danmakuTop = 0.dp, duration = duration)
                    TvDanmakuPicker(danmaku, dmSearch, snap.itemId, pickEditing, { pickEditing = it }) {
                        pickEditing = false
                        com.gugaart.ui.player.DanmakuPickerBoard.open = false
                    }
                }
                stripOpen -> {
                    // ---- 选集带：画面照常全幅在后面放，下半一块墨色渐变上排季 / 分页 / 分集卡 ----
                    Picture(play, snap, danmaku, slots, Modifier.fillMaxSize(), dim = if (snap.paused) PlayerSpec.PAUSE_DIM else 0f, danmakuTop = 0.dp, duration = duration)
                    TvEpisodeStrip(eps!!, snap) { id ->
                        strip = false
                        g.queue.nowPlaying(id)
                        g.nav.replace(Route.Play(com.gugaart.ui.data.RemoteBrowse.wrap(g.nav, id)))
                    }
                }
                panel != null -> {
                    // ---- 画面让位：画面缩到左边；页边是墨黑的纸，画面那一块不画底（下面是原生视频层）----
                    val dark = TvPlayerSpec.DARK
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(TvPlayerSpec.INSET).background(dark))
                        Row(Modifier.height(TvPlayerSpec.VIDEO_H)) {
                            Box(Modifier.width(TvPlayerSpec.INSET).height(TvPlayerSpec.VIDEO_H).background(dark))
                            Box(Modifier.width(TvPlayerSpec.VIDEO_W).height(TvPlayerSpec.VIDEO_H).clipToBounds().border(1.dp, colors.line).testTag("tv-player-inset")) {
                                Picture(play, snap, danmaku, slots, Modifier.matchParentSize(), dim = if (snap.paused) PlayerSpec.PAUSE_DIM else 0f, danmakuTop = 0.dp, duration = duration)
                            }
                            Box(Modifier.weight(1f).height(TvPlayerSpec.VIDEO_H).background(dark))
                        }
                        Box(Modifier.fillMaxSize().background(dark))
                    }
                    TvLiveTimeline(controller.clock, duration, heat, snap, focused = false, modifier = Modifier.padding(start = TvPlayerSpec.INSET, top = 842.dp + 56.dp).width(TvPlayerSpec.VIDEO_W))
                    Column(Modifier.padding(start = TvPlayerSpec.PANEL_LEFT, end = TvPlayerSpec.INSET, top = TvPlayerSpec.INSET, bottom = TvPlayerSpec.INSET).fillMaxSize().testTag("tv-player-margin")) {
                        Box(Modifier.fillMaxWidth().height(4.dp).background(colors.ink))
                        BasicText("播放设置 · SETTINGS", Modifier.padding(top = 16.dp, bottom = 18.dp), style = Guga.note(20.sp, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                        val panelKeys = remember(rows, shaderVisible, snap.subtitleIndex == null, corePref == coreGlobal, sourceList.size > 1) { // 只取键：哪几行在，跟着这几样变
                            rows.flatMap { p -> listOf("pr-${p.id}") + extrasOf(p).map { it.key } }
                        }
                        TvGroup("panel", panelKeys, horizontal = false, default = LocalTvPlayerFocus.current?.takeIf { it.startsWith("pr-") && it in panelKeys } ?: "pr-${panel!!.id}")
                        // 行多了（子行）一屏排不下：这一栏可以滚，焦点行自己滚进来（TvPanelRow）
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        for (p in rows) {
                            val row = rowOf(p)
                            TvPanelRow(p, row.values, row.index, row.detail(row.index), deferred = row.deferred && row.index != (if (p == PlayerPanel.Core) coreOpts.indexOfFirst { it.pref == coreShown } else currentIndexOf(p, snap)), onOk = {
                                when {
                                    p == PlayerPanel.Eps && eps != null -> strip = true
                                    // 弹幕一行按确定 = 未配置去设置，否则左边出弹幕来源
                                    p == PlayerPanel.Dm -> if (danmakuUnconfigured) g.nav.navigate(Route.Settings("danmaku")) else com.gugaart.ui.player.DanmakuPickerBoard.open = true
                                    // 字幕一行按确定 = 左边出字幕搜索（docs/subtitle-search.md §5.3）
                                    p == PlayerPanel.Sub && snap.item != null -> com.gugaart.ui.subtitle.SubtitleSearchBoard.openInPlayer(play, g)
                                    row.deferred -> row.apply(row.index)
                                }
                            })
                            // 子行：着色器增强（画质下面）、字幕字号 / 字体（字幕下面）、弹幕字体 / 粗细（弹幕下面）；上下走到它、左右改值
                            for (x in extrasOf(p)) {
                                TvPanelRow(p, x.values, x.index, x.detail, deferred = x.pick != null && x.index != x.current, onOk = { x.ok?.invoke() ?: x.apply(x.index) }, rowKey = x.key, word = x.word, english = x.english, sub = true, free = x.step != null, button = x.button)
                            }
                        }
                        }
                    }
                }
                else -> {
                    // ---- 全幅 ----
                    val dim = when {
                        showBuffer -> PlayerSpec.BUFFER_DIM
                        snap.paused && snap.phase == PlayPhase.Ready -> PlayerSpec.PAUSE_DIM
                        else -> 0f
                    }
                    Picture(play, snap, danmaku, slots, Modifier.fillMaxSize(), dim, danmakuTop = if (showOsd) 170.dp else 0.dp, duration = duration)
                    if (showBuffer && !seekShown) TvBuffer(play, snap)
                    // 字幕智能匹配的那句提示（左上、「片源效果」徽章那一行下面；遥控器点不到它，写怎么走）
                    // 下面一格：「换到别台的源没成功」那一句（亮 4 秒，player/NoticeFlash.kt）；两样同时在时上下排开，不叠
                    if (!seekShown) Column(Modifier.align(Alignment.TopStart).padding(start = 96.dp, top = 272.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.gugaart.ui.subtitle.AutoBestFlash(play, controls = showOsd, scale = 1.9f, tv = true, maxWidth = 1100.dp)
                        com.gugaart.ui.player.PlayerNoticeFlash(play, 1.9f, maxWidth = 1100.dp)
                    }
                    // 1.6 C3：长按快进的预览框——服务器有缩略图（Emby 的 BIF）就放这一刻的那张，没有照旧是剧照
                    val tvThumbs = com.gugaart.ui.player.rememberSeekThumbs()
                    androidx.compose.runtime.LaunchedEffect(seekShown) { if (seekShown) tvThumbs.prepare(play, g) }
                    if (showOsd) {
                        TvLivePlayerTop(controller.clock, snap, art, duration, com.gugaart.ui.player.subtitleBadgeOf(play, snap), speed)
                        // 底部暗角 + 进度条 + 两排键
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(300.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to TvPlayerSpec.DARK.copy(alpha = 0.75f), 1f to TvPlayerSpec.DARK.copy(alpha = 0.95f))))
                        TvGroup("bar", listOf("osd-bar"))
                        TvFocusable(
                            { controller.userGesture(); controller.togglePlay(); bump() },
                            Modifier.align(Alignment.BottomStart).padding(start = 96.dp, end = 96.dp, bottom = 176.dp).fillMaxWidth().tvMember(groups, "osd-bar"),
                            TvFocusKind.OnGlass, "osd-bar", label = "进度条",
                        ) { focused -> TvLiveTimeline(controller.clock, duration, heat, snap, focused) }
                        TvOsdButtons(snap, controller, rows, render.enabled, danmakuUnconfigured, seekStepSec, stepNow, pendingEp, fav = { controller.toggleFavorite(); bump() }, open = { p -> if (p == PlayerPanel.Dm && danmakuUnconfigured) g.nav.navigate(Route.Settings("danmaku")) else panel = p }, bump = bump)
                    } else if (seekShown) {
                        // ---- 快进 / 快退：进度条 + 走了多少 + 预览 ----
                        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(300.dp).background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to TvPlayerSpec.DARK.copy(alpha = 0.75f), 1f to TvPlayerSpec.DARK.copy(alpha = 0.95f))))
                        TvTimeline(holdTo, duration, heat, snap, focused = false, ghostMs = holdFrom, modifier = Modifier.align(Alignment.BottomStart).padding(start = 96.dp, end = 96.dp, bottom = 120.dp).fillMaxWidth().testTag("tv-player-seek"))
                        // 预览：拿不到真实截帧（上游的截帧只给玻璃取样），排「时间 + 这一集的剧照小图」
                        val frac = if (duration > 0) (holdTo / duration).toFloat().coerceIn(0f, 1f) else 0f
                        val previewX = 232.dp + (1920.dp - 464.dp) * frac - 163.dp
                        // 跳转量（「+00:10」那一列）照设计在右下；预览框走到它那儿会盖住它（快进到片尾附近，int16a 协调人定）→ 这一列换到左下，
                        // 和预览框各在一边。按这一列量出来的实际宽度判断（「-1:23:45」比「+00:10」宽）。
                        var deltaW by remember { mutableIntStateOf(0) }
                        val deltaLeft = seekDeltaOnLeft(previewX, with(androidx.compose.ui.platform.LocalDensity.current) { deltaW.toDp() })
                        Column(
                            Modifier.align(if (deltaLeft) Alignment.BottomStart else Alignment.BottomEnd)
                                .padding(start = if (deltaLeft) 96.dp else 0.dp, end = if (deltaLeft) 0.dp else 96.dp, bottom = 250.dp)
                                .onSizeChanged { deltaW = it.width }.testTag("tv-player-seek-delta"),
                            horizontalAlignment = if (deltaLeft) Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            BasicText(seekDeltaLabel(holdTo - holdFrom), style = Guga.numeral(72.sp), maxLines = 1)
                            if (holdDir != 0) BasicText("长按中 · ×$holdLevel", Modifier.background(colors.ink).padding(horizontal = 12.dp, vertical = 2.dp), style = Guga.song(24.sp, FontWeight.Bold, colors.onInk), maxLines = 1)
                            BasicText(if (holdDir != 0) "松开播放" else "从这里播放", style = Guga.song(24.sp, color = colors.dim), maxLines = 1)
                        }
                        Column(Modifier.align(Alignment.BottomStart).offset(x = previewX).padding(bottom = 214.dp).width(326.dp).testTag("tv-player-preview"), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.width(320.dp).height(180.dp).border(3.dp, colors.ink).background(TvPlayerSpec.DARK)) {
                                val frameImg = tvThumbs.image(holdTo)
                                if (frameImg != null) androidx.compose.foundation.Image(frameImg, "预览", Modifier.fillMaxSize().testTag("tv-player-seek-thumb"), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                else CoverImage(art.still ?: art.thumb, Modifier.fillMaxSize(), Framing(0.5f, 0.3f), alpha = 0.55f, knownSize = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.unit.IntSize(320.dp.roundToPx(), 180.dp.roundToPx()) })
                            }
                            BasicText(Rules.clock(holdTo.toLong()), Modifier.padding(top = 6.dp), style = Guga.numeral(32.sp), maxLines = 1)
                        }
                    }
                    // ---- 跳过片头：玻璃胶囊，一出现就拿到焦点；快进时让到进度条 / 预览框 / 跳转量上面 ----
                    if (skip != null) TvSkipPill(skip.label, raised = seekShown) { controller.skip() }
                }
            }
        }
    }
}

/** 现在真的生效的是第几个（选集 / 画质这两行是「先挑、确定才换」）。 */
private fun currentIndexOf(p: PlayerPanel, snap: PlayerSnapshot): Int = when (p) {
    PlayerPanel.Eps -> snap.episodes.indexOfFirst { it.id == snap.itemId }.coerceAtLeast(0)
    PlayerPanel.Quality -> BITRATES.indexOf(snap.maxBitrateMbps).coerceAtLeast(0)
    else -> -1
}

private fun kotlinx.coroutines.CoroutineScope.launchFocus(registry: com.gugugaga.ds.focus.FocusRegistry, key: String) {
    launch { registry.awaitRequest(key) }
}

/** 等 [key] 挂上（控件刚出现的那一帧它还不在），再把焦点交给它；最多等 1 秒。 */
private suspend fun com.gugugaga.ds.focus.FocusRegistry.awaitRequest(key: String) {
    repeat(40) {
        if (isAttached(key) && request(key)) return
        delay(25)
    }
}

/**
 * 第 0 层：画面（原生层；这里只占位并把矩形报给引擎）+ 弹幕（上游的图层）+ 压暗。测试里用一张剧照顶着（`LocalPlayerStill`）。
 * **画面上没有她，也没有任何常驻的东西。**
 */
@Composable
private fun Picture(play: PlaySession, snap: PlayerSnapshot, danmaku: PlayerDanmaku, slots: PlayerSlots, modifier: Modifier, dim: Float, danmakuTop: Dp, duration: Double) {
    val controller = play.controller
    val engine by controller.engineFlow.collectAsState()
    val settings by danmaku.settingsState.collectAsState()
    val render = remember(settings) { danmaku.prefsNow().render }
    val paper = TvPlayerSpec.DARK
    Box(modifier.testTag(if (snap.paused) "tv-player-picture-paused" else "tv-player-picture")) {
        LocalPlayerStill.current?.let { still -> Canvas(Modifier.matchParentSize()) { drawCover(still, Framing(0.5f, 0.12f)) } }
        com.gugaart.ui.player.GugaVideoSurface(engine, Modifier.matchParentSize()) { VeilLayer(controller.veil) }
        // 换片时新片子第一帧到之前，盖住引擎留着的上一部的最后一帧（和手机同一个做法）
        val tvClient = LocalGraph.current.session.session.collectAsState().value?.client
        val coverArt = remember(snap.item, tvClient) { snap.item?.let { tvClient?.artOf(it) } ?: Art() }
        NewItemCover(snap.itemId, snap.phase, coverArt)
        (com.gugaart.ui.player.LocalDanmakuSlotOverride.current ?: slots.danmaku)?.let { dm ->
            val holding = rememberUpdatedState(snap.holding)
            val dur = rememberUpdatedState(duration)
            dm(DanmakuHost(snap.itemId, snap.item, controller.presentationClock, holding, render.enabled, dur, UiMode.Tv), Modifier.matchParentSize().padding(top = danmakuTop))
        }
        if (dim > 0f) Box(Modifier.matchParentSize().drawBehind { drawRect(paper.copy(alpha = dim)) })
        // 开播那几秒：片源的效果（杜比视界 / HDR10 / 杜比全景声…），在顶栏下面
        // P2-5：「换台失败」那一条亮着时，徽章下面的说明先收起（两行不叠）
        com.gugaart.ui.player.FormatFlash(snap, 1.6f, Modifier.align(Alignment.TopStart).padding(start = 96.dp, top = 200.dp), notes = !play.noticeShowing)
    }
}

/** 顶栏（只在控件显示时）：左 片名 logo + 集名 + 规格；右 她的一格（**只有暂停时**坐进来）+ 时钟 + 预计几点播完。没有返回按钮。 */
@OptIn(ExperimentalTime::class)
@Composable
private fun BoxScope.TvLivePlayerTop(clockFlow: kotlinx.coroutines.flow.StateFlow<com.gugugaga.app.player.ClockSnapshot>, snap: PlayerSnapshot, art: Art, duration: Double, subtitle: String? = null, speed: androidx.compose.runtime.State<String?>? = null) {
    val clock by clockFlow.collectAsState()
    TvPlayerTop(snap, art, clock.positionMs, duration, clock.rate, subtitle, speed)
}

@OptIn(ExperimentalTime::class)
@Composable
private fun BoxScope.TvPlayerTop(snap: PlayerSnapshot, art: Art, position: Double, duration: Double, rate: Double, subtitle: String? = null, speed: androidx.compose.runtime.State<String?>? = null) {
    val colors = Guga.colors
    val preset = LocalPreset.current
    val now = LocalTvPlayerClock.current ?: remember(position.toLong() / 30_000) {
        val ms = Clock.System.now().toEpochMilliseconds()
        val local = ms + localUtcOffsetMs(ms)
        (local.mod(86_400_000L) / 3_600_000L).toInt() to (local.mod(3_600_000L) / 60_000L).toInt()
    }
    Box(Modifier.align(Alignment.TopStart).fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(TvPlayerSpec.DARK.copy(alpha = 0.88f), Color.Transparent))))
    Row(Modifier.align(Alignment.TopStart).padding(start = 96.dp, top = 44.dp).widthIn(max = 1200.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        val showLogo = snap.item?.seriesName != null || (art.logo != null && !preset.textTitles)
        if (showLogo) TitleLogo(snap.item?.seriesName ?: snap.title, art.logo, 230.dp, 72.dp, textOnly = preset.textTitles, baseSize = 40.sp)
        Column {
            val ep = snap.item?.indexNumber?.let { "第 $it 集　" }.orEmpty()
            BasicText(ep + (snap.item?.name ?: snap.title), style = Guga.song(32.sp, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val spec = listOfNotNull(
                snap.item?.parentIndexNumber?.let { "第 $it 季" },
                snap.episodes.size.takeIf { it > 0 }?.let { "共 $it 集" },
                listOfNotNull(if (snap.mode == PlaybackMode.Direct) "原画" else if (snap.mode != null) "转码" else null, specLine(snap.resolved?.mediaSource)?.substringBefore(" · ")).joinToString(" ").takeIf { it.isNotEmpty() },
            ).joinToString(" · ")
            if (spec.isNotEmpty()) BasicText(spec, Modifier.padding(top = 6.dp), style = Guga.song(22.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Row(Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        // 她的一格：播放时是空的
        val paused = snap.paused && snap.phase == PlayPhase.Ready
        if (paused) {
            Row(Modifier.height(76.dp).testTag("tv-pause-seat"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (LocalGugaPrefs.current.showMascot) Byline(56.dp, live = false)
                Column {
                    BasicText("暂停", style = Guga.song(28.sp, FontWeight.Black), maxLines = 1)
                    BasicText("停在 ${Rules.clock(position.toLong())}", style = Guga.note(22.sp, colors.dim), maxLines = 1)
                }
                Box(Modifier.padding(start = 14.dp).width(1.dp).height(76.dp).background(colors.line))
            }
        } else Spacer(Modifier.size(1.dp).testTag("tv-pause-seat-empty"))
        Column(horizontalAlignment = Alignment.End) {
            BasicText("${now.first}:${if (now.second < 10) "0" else ""}${now.second}", style = Guga.numeral(36.sp), maxLines = 1)
            if (duration > 0) BasicText("预计 ${endClock(now.first, now.second, duration - position, rate)} 播完", style = Guga.song(20.sp, color = colors.dim), maxLines = 1)
            // 网速（用户 2026-11-17：「调出菜单，能够自动显示当前的网络速度」）
            speed?.let { com.gugaart.ui.player.NetSpeedText(it, Modifier.testTag("tv-player-speed"), Guga.song(22.sp, color = colors.ink)) }
            // 字幕是怎么选来的（「字幕 简中 · 自动」）
            subtitle?.let { BasicText(it, style = Guga.song(20.sp, color = colors.dim), maxLines = 1) }
        }
    }
}

/**
 * 进度条（设计稿 `.tl`）：左 现在的时间，右 总时长；线上方一排热度条（弹幕热力图，上游给的；播过的专色）；线下面标出片头 / 片尾的范围；
 * 焦点在进度条上：播放头变大、朱红描边，时间变米白实底。[ghostMs]：快进时原来的位置（虚线）。
 */
@Composable
private fun TvLiveTimeline(clockFlow: kotlinx.coroutines.flow.StateFlow<com.gugugaga.app.player.ClockSnapshot>, duration: Double, heat: FloatArray, snap: PlayerSnapshot, focused: Boolean, modifier: Modifier = Modifier, ghostMs: Double? = null) {
    val clock by clockFlow.collectAsState()
    TvTimeline(clock.positionMs, duration, heat, snap, focused, modifier, ghostMs)
}

@Composable
private fun TvTimeline(position: Double, duration: Double, heat: FloatArray, snap: PlayerSnapshot, focused: Boolean, modifier: Modifier = Modifier, ghostMs: Double? = null) {
    val colors = Guga.colors
    val p = if (duration > 0) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    Row(modifier.height(64.dp).testTag(if (focused) "tv-timeline-focused" else "tv-timeline"), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BasicText(
            Rules.clock(position.toLong()),
            Modifier.width(120.dp).then(if (focused) Modifier.background(colors.ink).padding(horizontal = 8.dp, vertical = 4.dp) else Modifier.padding(horizontal = 8.dp, vertical = 4.dp)),
            style = Guga.numeral(30.sp, if (focused) colors.onInk else colors.ink), maxLines = 1,
        )
        Canvas(Modifier.weight(1f).height(64.dp)) {
            val w = size.width
            val lineH = (if (focused) 10.dp else 6.dp).toPx()
            val lineY = size.height - 4.dp.toPx() - lineH
            // 弹幕热度：三端同一个画法（player/PlayerParts.kt 的 drawHeatColumns）。电视的柱最高 44 dp——3 米外也看得出起伏；能放多高看这一条的高度
            drawHeatColumns(heat, p, size.height - 16.dp.toPx(), minOf(HeatSpec.TV_MAX.toPx(), size.height - 20.dp.toPx()), colors.spot, colors.ink)
            drawRect(colors.ink.copy(alpha = 0.25f), Offset(0f, lineY), Size(w, lineH))
            drawRect(colors.spot, Offset(0f, lineY), Size(w * p, lineH))
            // 片头 / 片尾的范围（上游的章节标记）
            if (duration > 0) {
                snap.markers.intro?.let { m -> drawRect(colors.ink.copy(alpha = 0.55f), Offset((m.startMs / duration).toFloat() * w, lineY + 12.dp.toPx()), Size(((m.endMs - m.startMs) / duration).toFloat() * w, 3.dp.toPx())) }
                snap.markers.credits?.let { m -> drawRect(colors.ink.copy(alpha = 0.55f), Offset((m.startMs / duration).toFloat() * w, lineY + 12.dp.toPx()), Size(((duration - m.startMs) / duration).toFloat().coerceAtLeast(0f) * w, 3.dp.toPx())) }
                ghostMs?.let { gms ->
                    val gx = (gms / duration).toFloat().coerceIn(0f, 1f) * w
                    drawRect(colors.faint, Offset(gx - 3.dp.toPx(), lineY - 14.dp.toPx()), Size(6.dp.toPx(), 32.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
                }
            }
            // 播放头
            val hw = (if (focused) 16.dp else 6.dp).toPx()
            val hh = (if (focused) 44.dp else 32.dp).toPx()
            val hx = w * p - hw / 2
            val hy = lineY + lineH / 2 - hh / 2
            if (focused) drawRect(colors.seal, Offset(hx - 4.dp.toPx(), hy - 4.dp.toPx()), Size(hw + 8.dp.toPx(), hh + 8.dp.toPx()))
            drawRect(colors.ink, Offset(hx, hy), Size(hw, hh))
        }
        BasicText(Rules.clock(duration.toLong()), Modifier.width(112.dp), style = Guga.numeral(30.sp, colors.dim).copy(textAlign = TextAlign.End), maxLines = 1)
    }
}

/** 控件第二行：上一集 / 退 10 / 播放 / 进 10 / 下一集 ｜ 选集 字幕 音轨 倍速 画质 弹幕 收藏。每个键都有焦点；默认焦点「播放」。 */
@Composable
private fun BoxScope.TvOsdButtons(
    snap: PlayerSnapshot,
    controller: com.gugugaga.app.player.PlayController,
    rows: List<PlayerPanel>,
    danmakuOn: Boolean,
    danmakuUnconfigured: Boolean,
    seekStepSec: Int,
    seekStepMs: Double,
    pendingEp: Int,
    fav: () -> Unit,
    open: (PlayerPanel) -> Unit,
    bump: () -> Unit,
) {
    val colors = Guga.colors
    val transport = listOfNotNull("osd-prev".takeIf { snap.prev != null }, "osd-rew", "osd-play", "osd-fwd", "osd-next".takeIf { snap.next != null })
    val keys = transport + rows.map { "fn-${it.id}" } + "osd-fav"
    TvGroup("brow", keys, default = "osd-play")
    Row(Modifier.align(Alignment.BottomStart).padding(start = 96.dp, end = 96.dp, bottom = 60.dp).fillMaxWidth().height(84.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (snap.prev != null) TvOsdKey("osd-prev", GuguIcon.SkipBack, "上一集") { controller.playPrev() }
            TvOsdKeyContent("osd-rew", "退 $seekStepSec 秒", onClick = { controller.seekBy(-seekStepMs); bump() }) { ink ->
                com.gugaart.ui.player.SeekStepIcon(false, seekStepSec, ink, 36.dp)
            }
            TvOsdKey("osd-play", if (snap.paused) GuguIcon.Play else GuguIcon.Pause, if (snap.paused) "继续" else "暂停", big = true) { controller.userGesture(); controller.togglePlay(); bump() }
            TvOsdKeyContent("osd-fwd", "进 $seekStepSec 秒", onClick = { controller.seekBy(seekStepMs); bump() }) { ink ->
                com.gugaart.ui.player.SeekStepIcon(true, seekStepSec, ink, 36.dp)
            }
            if (snap.next != null) TvOsdKey("osd-next", GuguIcon.SkipForward, "下一集") { controller.playNext() }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            for (p in rows) {
                val value = when (p) {
                    PlayerPanel.Eps -> snap.episodes.getOrNull(pendingEp)?.indexNumber?.let { "第 $it 集" } ?: snap.item?.indexNumber?.let { "第 $it 集" }
                    PlayerPanel.Sub -> com.gugaart.ui.player.subtitleWordValue(snap)
                    PlayerPanel.Audio -> com.gugaart.ui.player.audioWordValue(snap)
                    PlayerPanel.Speed -> rateLabel(snap.rate)
                    PlayerPanel.Quality -> if (snap.maxBitrateMbps > 0) "${snap.maxBitrateMbps.toInt()} Mbps" else "原画"
                    PlayerPanel.Core -> com.gugaart.ui.player.engineShort(snap.engineId)
                    else -> if (danmakuUnconfigured) "未配置" else if (danmakuOn) "开" else "关"
                }
                TvBlockFocus("fn-${p.id}", { open(p) }, Modifier.drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }, label = p.word) { focused ->
                    Column(Modifier.height(84.dp).widthIn(min = 120.dp, max = 190.dp).padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
                        BasicText(p.word, style = Guga.song(29.sp, FontWeight.Black, if (focused) colors.onInk else colors.ink, letterSpacing = 0.1.em), maxLines = 1)
                        if (value != null) BasicText(value, style = Guga.note(20.sp, if (focused) colors.onInk.copy(alpha = 0.65f) else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // 收藏必须在：第二行最右
            TvOsdKey("osd-fav", GuguIcon.Heart, if (snap.favorite == true) "取消收藏" else "收藏", tint = if (snap.favorite == true) colors.seal else null, filled = snap.favorite == true, onClick = fav)
        }
    }
}

/** 一个方的图标键（84 × 84，播放键 120 宽）：线框；焦点 = 米白实底 + 校对角，下面出一行小字说明。 */
@Composable
private fun TvOsdKey(key: String, icon: GuguIcon, label: String, big: Boolean = false, tint: Color? = null, filled: Boolean = false, onClick: () -> Unit) {
    TvOsdKeyContent(key, label, big, tint, onClick) { ink ->
        Icon(icon, ink, if (big) 42.dp else 36.dp, filled = filled || icon == GuguIcon.Play || icon == GuguIcon.Pause)
    }
}

@Composable
private fun TvOsdKeyContent(key: String, label: String, big: Boolean = false, tint: Color? = null, onClick: () -> Unit, iconContent: @Composable (Color) -> Unit) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val focusedNow = groups.current == key
    Box {
        TvBlockFocus(key, onClick, if (focusedNow) Modifier else Modifier.background(TvPlayerSpec.DARK.copy(alpha = 0.4f)).border(2.dp, (tint ?: colors.ink).copy(alpha = if (tint != null) 1f else 0.5f)), label = label, default = key == "osd-play") { focused ->
            val ink = if (focused) colors.onInk else tint ?: colors.ink
            Box(Modifier.width(if (big) 120.dp else 84.dp).height(84.dp), contentAlignment = Alignment.Center) {
                iconContent(ink)
            }
        }
        if (focusedNow) {
            BasicText(label, Modifier.align(Alignment.BottomCenter).offset(y = 44.dp).widthIn(min = 160.dp), style = Guga.song(20.sp).copy(textAlign = TextAlign.Center), maxLines = 1, softWrap = false)
        }
    }
}

/**
 * 页边面板的一行（设计稿 `.pr`）：功能名 + 英文小标 + 现在的值；焦点行 = 米白实底，两侧出 ‹ ›（到头的那一侧变淡），下面一排小方格（第几个）和「2 / 4」。
 * [deferred]：这一行挑的值还没生效（选集 / 画质），提示「确定 = 换过去」。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TvPanelRow(panel: PlayerPanel, values: List<String>, index: Int, detail: String?, deferred: Boolean, onOk: () -> Unit, rowKey: String = "pr-${panel.id}", word: String = panel.word, english: String = panel.english, sub: Boolean = false, free: Boolean = false, button: Boolean = false) {
    val colors = Guga.colors
    val key = rowKey
    // 页边能滚（子行多了一屏排不下）：焦点到了这一行，就把它滚进来
    val into = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val focusedNow = LocalTvGroups.current.current == key
    LaunchedEffect(focusedNow) { if (focusedNow) into.bringIntoView() }
    TvBlockFocus(key, onOk, Modifier.fillMaxWidth().bringIntoViewRequester(into).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, label = word) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Column(Modifier.fillMaxWidth().padding(start = if (sub) 40.dp else 18.dp, end = 18.dp, top = if (sub) 10.dp else 15.dp, bottom = if (sub) 11.dp else 16.dp).testTag(if (focused) "tv-panel-row-focused" else "tv-panel-row")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                BasicText(word, style = Guga.song(if (sub) 26.sp else 31.sp, FontWeight.Black, ink, letterSpacing = 0.1.em), maxLines = 1)
                Spacer(Modifier.weight(1f))
                BasicText(english, style = Guga.numeral(16.sp, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot, letterSpacing = 0.2.em), maxLines = 1)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BasicText("‹", style = Guga.numeral(38.sp, ink.copy(alpha = if (!focused || button) 0f else if (free || index > 0) 1f else 0.22f)).copy(fontStyle = FontStyle.Normal), maxLines = 1)
                BasicText(
                    values.getOrNull(index) ?: "—",
                    Modifier.weight(1f),
                    style = Guga.song(25.sp, FontWeight.SemiBold, if (focused) colors.onInk else colors.dim).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BasicText("›", style = Guga.numeral(38.sp, ink.copy(alpha = if (!focused || button) 0f else if (free || index < values.size - 1) 1f else 0.22f)).copy(fontStyle = FontStyle.Normal), maxLines = 1)
            }
            if (focused) {
                val line = detail
                if (line != null) BasicText(line, Modifier.fillMaxWidth().padding(top = 8.dp), style = Guga.song(22.sp, color = colors.onInk).copy(textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 连续的值（字幕时间）没有「第几个」
                if (!free && !button) Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 一排小方格：值不多时一格一个；多了（1215 集）只写「第几个 / 共几个」
                    if (values.size <= 14) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in values.indices) Box(Modifier.size(9.dp).then(if (i == index) Modifier.background(colors.onInk) else Modifier.border(1.dp, colors.onInk.copy(alpha = 0.6f))))
                    } else Spacer(Modifier.weight(1f))
                    BasicText("${index + 1} / ${values.size}", style = Guga.numeral(20.sp, com.gugaart.ui.theme.GugaColors.SEAL_ON_INK), maxLines = 1)
                }
            }
        }
    }
}

/** 缓冲：画面压暗 40 %；正中「缓冲中」+ 一行小字（引擎回退 / 换线路的通知排在这里，不弹窗）+ 一条细线。没有编的百分比和网速。 */
@Composable
private fun BoxScope.TvBuffer(play: PlaySession, snap: PlayerSnapshot) {
    val colors = Guga.colors
    Column(Modifier.align(Alignment.Center).testTag("tv-player-buffer"), horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText("缓冲中", style = Guga.song(56.sp, FontWeight.Black, letterSpacing = 0.3.em), maxLines = 1)
        val line = play.notice?.title ?: snap.switching?.why ?: snap.stepLabel.takeIf { it.isNotEmpty() }
        if (line != null) BasicText(line, Modifier.padding(top = 8.dp), style = Guga.song(23.sp, color = colors.dim), maxLines = 1)
        Box(Modifier.padding(top = 26.dp).width(420.dp).height(5.dp).background(colors.line)) { Box(Modifier.width(140.dp).height(5.dp).background(colors.spot)) }
    }
}

/**
 * 「跳过片头」：播放器里**唯一的玻璃**（固定悬浮钮）。播放页不在根的 GlassScene 里（画面是原生层），上游给视频上的玻璃供底图的
 * `PlayerGlassScene` 是 internal 的，这里先用上游的 **Plates 退路**（色调 + 边光，不折射）；焦点 = 玻璃里一张米白纸条、墨字。
 * 一出现就拿到焦点：确定 = 跳过；返回或任何方向键 = 放弃。
 * [raised]：快进的进度条 / 预览框 / 跳转量在下面那一片，胶囊滑到它们上面（[TvPlayerSpec.SKIP_BOTTOM_SEEK]），快进收起再滑回来；样子不变。
 */
@Composable
private fun BoxScope.TvSkipPill(label: String, raised: Boolean, onSkip: () -> Unit) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    val clear = remember { PageBackdrops.solid(TvPlayerSpec.DARK) }
    val bottom by androidx.compose.animation.core.animateDpAsState(
        if (raised) TvPlayerSpec.SKIP_BOTTOM_SEEK else TvPlayerSpec.SKIP_BOTTOM,
        androidx.compose.animation.core.tween(220), label = "skip-bottom",
    )
    TvGroup("skip", listOf("skip"))
    Box(Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = bottom).width(368.dp).height(100.dp).testTag("tv-player-skip")) {
        GlassScene(backend = GlassBackend.Plates, backdrop = clear, modifier = Modifier.matchParentSize(), backdropVisible = false) {
            Box(Modifier.matchParentSize().glass(MaterialName.Bar, 50.dp).padding(8.dp)) {
                TvFocusable(onSkip, Modifier.matchParentSize().tvMember(groups, "skip"), TvFocusKind.OnGlass, "skip", label = label) { focused ->
                    Row(
                        Modifier.matchParentSize().then(if (focused) Modifier.background(colors.ink, androidx.compose.foundation.shape.RoundedCornerShape(42.dp)) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    ) {
                        val ink = if (focused) colors.onInk else Color.White
                        Icon(GuguIcon.SkipForward, ink, 32.dp)
                        BasicText(label, style = Guga.song(31.sp, FontWeight.Black, ink, letterSpacing = 0.08.em), maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * 片尾 · 下一集（设计稿 `st=next`）：这部剧的剧照**整幅铺满**、压暗。左：下一集的集名（宋体 88）+ 简介第一句 + 按钮（「立即播放」拿到焦点）；
 * 右上：片尾还在播的小窗（[live]；放完了就没有）；右下：下一集的剧照。她鼓掌（G13）+ 一句台词——这是「正片已经放完」的页，可以有她。
 * 没有下一集：只有「回到详情」。
 */
@Composable
private fun TvPlayerNext(play: PlaySession, snap: PlayerSnapshot, art: Art, live: Boolean, duration: Double, engine: @Composable (Modifier) -> Unit, onStay: () -> Unit) {
    val clock by play.controller.clock.collectAsState()
    val position = clock.positionMs
    val g = LocalGraph.current
    val colors = Guga.colors
    val hour = LocalHour.current
    val client = g.session.session.collectAsState().value?.client
    val next = snap.next
    val nextArt = remember(next, client) { next?.let { client?.artOf(it) } }
    val groups = LocalTvGroups.current
    groups.edge = { _, _ -> TvGroups.STAY }
    LaunchedEffect(next?.id, live) { g.focus.awaitRequest("next-play") }
    Box(Modifier.fillMaxSize().background(colors.paper).testTag(if (live) "tv-player-next-live" else "tv-player-ended")) {
        val bd = com.gugugaga.app.data.rememberImage(art.backdrop ?: art.poster, keepPrevious = true)
        Canvas(Modifier.fillMaxSize()) {
            bd?.let { drawCover(it, Framing(0.5f, 0.12f), alpha = 0.55f) }
            val p = colors.paper
            drawRect(Brush.horizontalGradient(0f to p.copy(alpha = 0.94f), 0.36f to p.copy(alpha = 0.8f), 0.70f to p.copy(alpha = 0.35f), 1f to p.copy(alpha = 0.5f)))
            drawRect(Brush.verticalGradient(0f to p.copy(alpha = 0.5f), 0.30f to Color.Transparent, 1f to p.copy(alpha = 0.85f)))
        }
        // 右上：片尾还在播的小窗（画面让位到这里）
        if (live) Column(Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 60.dp).width(TvPlayerSpec.WINDOW_W)) {
            Box(Modifier.width(TvPlayerSpec.WINDOW_W).height(TvPlayerSpec.WINDOW_H).border(1.dp, colors.line).clipToBounds()) { engine(Modifier.matchParentSize()) }
            BasicText("片尾 · 还在播　${Rules.clock(position.toLong())} / ${Rules.clock(duration.toLong())}", Modifier.padding(top = 10.dp), style = Guga.song(21.sp, color = colors.dim), maxLines = 1)
        }
        if (next != null) Column(Modifier.align(Alignment.TopEnd).padding(end = 96.dp, top = 440.dp).width(TvPlayerSpec.WINDOW_W)) {
            Box(Modifier.width(TvPlayerSpec.WINDOW_W).height(TvPlayerSpec.WINDOW_H).border(3.dp, colors.ink)) { CoverImage(nextArt?.thumb, Modifier.fillMaxSize(), Framing(0.5f, 0.3f)) }
            BasicText("第 ${next.indexNumber ?: ""} 集 剧照", Modifier.padding(top = 10.dp), style = Guga.song(21.sp, color = colors.dim), maxLines = 1)
        }
        Column(Modifier.padding(start = 136.dp, top = 150.dp).width(1060.dp)) {
            if (next != null) {
                BasicText(
                    listOfNotNull("下一集 · NEXT", listOfNotNull(next.indexNumber?.let { "第 $it 集" }, Rules.ticksToSec(next.runTimeTicks)?.let { "${(it + 30) / 60} 分钟" }).joinToString(" · ")).joinToString("　"),
                    style = Guga.song(24.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.16.em), maxLines = 1,
                )
                BasicText(next.name, Modifier.padding(top = 14.dp, bottom = 20.dp), style = Guga.song(88.sp, FontWeight.Black, lineHeight = 101.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val lead = tvFirstSentence(Rules.cleanOverview(next.overview), 96)
                if (lead.isNotEmpty()) BasicText(lead, Modifier.width(980.dp).padding(bottom = 26.dp), style = Guga.song(28.sp, FontWeight.SemiBold, lineHeight = 43.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                // P1-3：进了片尾标记倒数 10 秒，数字写在按钮上（「下一集 · 8」）；「取消」= 不再自动接
                val left = if (live) com.gugaart.ui.player.rememberNextCountdown(play, snap, { play.controller.clock.value.positionMs }, duration) else null
                val keys = listOfNotNull("next-play", "next-cancel".takeIf { live }, "next-exit")
                TvGroup("next", keys, default = "next-play")
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    TvActionButton("next-play", com.gugaart.ui.player.nextButtonLabel(left), { play.controller.playNext() }, icon = GuguIcon.Play, primary = true, default = true)
                    if (live) TvActionButton("next-cancel", "取消", onStay)
                    TvActionButton("next-exit", "返回详情", play.exit, icon = GuguIcon.ChevronLeft)
                }
            } else {
                BasicText("本集完 · THE END", style = Guga.song(24.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.16.em), maxLines = 1)
                BasicText(snap.item?.name ?: snap.title, Modifier.padding(top = 14.dp, bottom = 30.dp), style = Guga.song(88.sp, FontWeight.Black, lineHeight = 101.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TvGroup("next", listOf("next-play"), default = "next-play")
                TvActionButton("next-play", "回到详情", play.exit, icon = GuguIcon.ChevronLeft, default = true)
            }
            if (LocalGugaPrefs.current.showMascot) {
                Row(Modifier.padding(top = 54.dp).fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    MascotPose("G13", 110.dp, label = "鼓掌")
                    val line = MascotLines.pick(if (next == null) "player.ended.last" else "player.ended", LineFacts(ep = snap.item?.indexNumber, next = next?.indexNumber), hour, snap.itemId.hashCode())
                    if (line != null && LocalGugaPrefs.current.mascotSpeaks) BasicText(line, style = Guga.song(28.sp, color = colors.dim).copy(fontStyle = FontStyle.Italic), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * 出错（设计稿 `st=error`）：**画面拿掉**，一页说明——朱红「号外」+ 宋体标题 + 一句人话 + 出路（来自上游 `ErrorView.actions`，第一个拿到焦点）。
 * 错误原文不上版面（只进「复制错误信息」）。她：平静地看印花稿纸（G21）。
 */
@Composable
private fun TvPlayerError(play: PlaySession, snap: PlayerSnapshot) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val view = snap.errorView
    val controller = play.controller
    val groups = LocalTvGroups.current
    groups.edge = { _, _ -> TvGroups.STAY }
    LaunchedEffect(Unit) { g.focus.awaitRequest("err-0") }
    Box(Modifier.fillMaxSize().background(TvPlayerSpec.DARK).testTag("tv-player-error-page")) {
        Column(Modifier.padding(start = 136.dp, top = 230.dp).width(1180.dp)) {
            // P2-1：三端统一——标题「播放失败」，下面一行原因，按钮「重试」「返回」；电视没有「复制错误信息」
            BasicText(com.gugaart.ui.player.PLAY_ERROR_TITLE, style = Guga.song(64.sp, FontWeight.Black), maxLines = 1)
            BasicText(com.gugaart.ui.player.playErrorReason(snap), Modifier.padding(top = 30.dp, bottom = 20.dp), style = Guga.song(30.sp, FontWeight.SemiBold, colors.dim, lineHeight = 48.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val facts = listOfNotNull(
                "片子" to listOfNotNull(snap.item?.seriesName, snap.item?.indexNumber?.let { "第 $it 集" } ?: snap.title.takeIf { snap.item?.seriesName == null }).joinToString(" · "),
                snap.mode?.let { "线路" to if (it == PlaybackMode.Direct) "直连" else "转码" },
                snap.engineId?.let { "内核" to it.id },
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            for ((k, v) in facts) {
                Row(Modifier.fillMaxWidth().height(56.dp).drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, verticalAlignment = Alignment.CenterVertically) {
                    BasicText(k, Modifier.width(160.dp), style = Guga.song(25.sp, color = colors.dim), maxLines = 1)
                    BasicText(v, style = Guga.song(25.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val actions = com.gugaart.ui.player.playErrorActions(snap)
            TvGroup("error", actions.indices.map { "err-$it" }, default = "err-0")
            Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                for ((i, a) in actions.withIndex()) {
                    TvActionButton(
                        "err-$i", a.label,
                        {
                            when (a) {
                                ErrorActionId.Retry -> controller.retry()
                                ErrorActionId.SwitchVersion -> snap.versions.firstOrNull { it.id != snap.sourceId }?.id?.let { controller.setSource(it) }
                                ErrorActionId.SwitchCore -> controller.switchCore()
                                ErrorActionId.UseEmbedded -> controller.useEmbedded()
                                ErrorActionId.External -> controller.openExternalNow()
                                ErrorActionId.Exit -> play.exit()
                                ErrorActionId.UseWeb -> {}
                            }
                        },
                        icon = if (i == 0) GuguIcon.RotateCcw else null, primary = i == 0, default = i == 0,
                    )
                }
            }
        }
        if (LocalGugaPrefs.current.showMascot) Column(Modifier.align(Alignment.CenterEnd).padding(end = 136.dp).width(360.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            MascotPose("G21", 220.dp, label = "看印花稿纸")
            if (LocalGugaPrefs.current.mascotSpeaks) MascotLines.pick("player.error", hour = LocalHour.current, seed = snap.itemId.hashCode())?.let {
                BasicText(it, Modifier.padding(top = 16.dp), style = Guga.song(24.sp, color = colors.dim).copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Center), maxLines = 2)
            }
        }
    }
}

/** 字号的那一列里，[scale] 落在第几档。 */
private fun nearestScale(scale: Double): Int = com.gugaart.ui.data.SUB_SCALES.indices.minBy { kotlin.math.abs(com.gugaart.ui.data.SUB_SCALES[it] - scale) }
