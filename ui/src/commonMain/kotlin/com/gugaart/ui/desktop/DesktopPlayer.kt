package com.gugaart.ui.desktop

import com.gugaart.ui.subtitle.SubtitleSearchHost
import com.gugaart.ui.player.DanmakuPickerHost
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import com.gugaart.ui.components.holdTouches
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.components.verticalCells
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.artOf
import com.gugaart.ui.desktop.detail.DButton
import com.gugaart.ui.desktop.detail.DKbd
import com.gugaart.ui.desktop.detail.hover
import com.gugaart.ui.desktop.detail.rememberHover
import com.gugaart.ui.desktop.detail.rsp
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.MascotPose
import com.gugaart.ui.player.EndedPage
import com.gugaart.ui.player.LocalPlayerPanelDriver
import com.gugaart.ui.player.LocalPlayerStill
import com.gugaart.ui.player.LocalViewportMode
import com.gugaart.ui.player.PanelBody
import com.gugaart.ui.player.PlaySession
import com.gugaart.ui.player.PlayerPanel
import com.gugaart.ui.player.PlayerSpec
import com.gugaart.ui.player.ViewportMode
import com.gugaart.ui.player.effectivePanel
import com.gugaart.ui.player.HeatSpec
import com.gugaart.ui.player.drawHeatColumns
import com.gugaart.ui.player.heatDots
import com.gugaart.ui.player.rateLabel
import com.gugaart.ui.player.SeekStepIcon
import com.gugaart.ui.player.specOf
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.specLine
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.AutoNextState
import com.gugugaga.app.player.ControlsVisibility
import com.gugugaga.app.player.DanmakuHost
import com.gugugaga.app.player.ErrorActionId
import com.gugugaga.app.player.KeyState
import com.gugugaga.app.player.KeyTarget
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlaybackSettings
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.player.PlayerEngine
import com.gugugaga.app.player.PlayerFormat
import com.gugugaga.app.player.PlayerKeyCommand
import com.gugugaga.app.player.PlayerKeyRouter
import com.gugugaga.app.player.PlayerKeys
import com.gugugaga.app.player.PlayerSlots
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.player.VeilLayer
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.glass.GlassBackend
import com.gugugaga.ds.glass.GlassScene
import com.gugugaga.ds.glass.MaterialName
import com.gugugaga.ds.glass.glass
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

/** 电脑播放器的版式数（设计稿 `desktop/css/d4.css` 的 `.pl`；rem）。 */
object DesktopPlayerSpec {
    /** 1.6 C3：进度条缩略图的宽（rem；16:9 → 高 9 rem），在悬停小横条正上方。 */
    const val SEEK_THUMB_REM = 16f

    /** 控件出现时画面四边让出的页边：顶栏 / 底部控制坞 / 左右。 */
    const val TOP = 6.4f
    const val BOTTOM = 11.8f
    const val EDGE = 5.6f
    /** 面板打开时右侧页边的宽：40 rem（1100 → 376，1440 → 400），3168 宽 46 rem（598）。 */
    const val SIDE = 40f
    const val SIDE_XL = 46f
    /** 很窄的窗口（≤ 900 dp 宽）：页边收紧、功能词收成图标、面板占右半窗。 */
    const val NARROW = 900f
    const val NARROW_TOP = 5.2f
    const val NARROW_BOTTOM = 9.6f
    const val NARROW_EDGE = 1.6f
    /** 画面让位的时长。 */
    const val GIVE_WAY_MS = 450
    /** 页边的底色（比纸色再深一点，画面的黑边融进去）。 */
    val MARGIN = Color(0xFF0B0A08)
    /** 画中画期间画面那一格的纸色（设计稿 `macos.css` 的 `.pl.pip .pv .fr`，和出错页同一张纸）。 */
    val PIP_PAPER = Color(0xFF14110D)
    /** 进度条悬停预览在测试 / 截图里固定的位置。 */
    const val HOVER_SAMPLE = 0.72f
}

private val WORD_ICONS = mapOf(
    PlayerPanel.Eps to GuguIcon.ListVideo,
    PlayerPanel.Sub to GuguIcon.Captions,
    PlayerPanel.Audio to GuguIcon.AudioLines,
    PlayerPanel.Speed to GuguIcon.Gauge,
    PlayerPanel.Quality to GuguIcon.Monitor,
    PlayerPanel.Core to GuguIcon.Clapperboard,
    PlayerPanel.Dm to GuguIcon.MessageSquareText,
)

/** 时间轴上的章节：悬停预览里写这一刻在哪一段（片头 / 正片 / 片尾）。没有标记 → null。纯函数。 */
fun chapterAt(ms: Double, introEndMs: Double?, creditsStartMs: Double?): String? = when {
    introEndMs == null && creditsStartMs == null -> null
    introEndMs != null && ms < introEndMs -> "片头"
    creditsStartMs != null && ms >= creditsStartMs -> "片尾"
    else -> "正片"
}

/**
 * 电脑播放器的画报外观（`docs/player.md` §电脑，设计稿 `desktop/board.html?p=player`）。逻辑一行不动：上游的 `PlayController` / 引擎 / 弹幕，
 * 状态的读法和手机的 [com.gugaart.ui.player.PlayerPhone] 一样。
 *
 * - 播放中画面铺满整个窗口；控件出现 / 暂停时画面「退一步」，四边让出页边——顶栏、控制坞、左缘图注、右缘音量都排在页边里，**画面和字幕一个像素都不挡**。
 * - 打开面板（或片尾的「下一集」）：画面再往左让，面板排在右侧页边（376 / 400 / 598）。
 * - 底部控制坞三段对称：左 时间；中 上一集 / 退 10 / 播放 / 进 10 / 下一集；右 功能词。下面是时间轴（热度圆点、片头 / 片尾刻度），
 *   悬停预览是一张小横条，落在控制坞里，不压画面。
 * - 「跳过片头」：浮在画面右下角的玻璃胶囊——播放器里唯一的玻璃。
 * - 键位沿用上游 `PlayerKeys`（只把它解析出的命令接到控制器的公开方法上）。
 */
@Composable
fun DesktopPlayer(play: PlaySession, danmaku: PlayerDanmaku, slots: PlayerSlots) {
    val g = LocalGraph.current
    // P2-9：能选的内核不到两个（macOS 只有 MPV）时不显示「内核」
    val coreVisible = remember(play) { com.gugaart.ui.player.coreWordVisible(com.gugaart.ui.player.coreOptionsFor(play, g)) }
    val controller = play.controller
    val colors = Guga.colors
    val preset = LocalPreset.current
    val scope = rememberCoroutineScope()
    val snap by controller.snapshot.collectAsState()
    // 时钟（每 250 ms 走一下）不在这一层读（1.5.1 性能）：只在时间、时间轴、暂停那一格里读，整个播放页不跟着每一下重组
    val clock = controller.clock.collectAsState()
    val clockDuration = remember(clock) { androidx.compose.runtime.derivedStateOf { clock.value.durationMs } }
    val engine by controller.engineFlow.collectAsState()
    com.gugaart.ui.player.RememberShaderApply(play) // 画质面板记住的着色器选择：引擎 / 选择一变就应用（ShaderApply.kt）
    com.gugaart.ui.player.RememberSecondSubtitleApply(play) // 双语字幕（1.6 C1）：第二字幕按片记住、交给 mpv
    val settings by danmaku.settingsState.collectAsState()
    val appSettings by g.settings.state.collectAsState()
    val prefs by play.prefs.state.collectAsState()
    val danmakuConfigured = com.gugaart.ui.danmaku.rememberDanmakuConfigured()
    val danmakuUnconfigured = (danmaku.state.phase == com.gugugaga.danmaku.match.DanmakuPhase.Unconfigured || !danmakuConfigured) &&
        com.gugaart.ui.player.LocalDanmakuSlotOverride.current == null &&
        com.gugaart.ui.player.DanmakuPickerBoard.stateOverride == null
    val render = remember(settings) { danmaku.prefsNow().render }
    val controls = remember { ControlsVisibility(scope, 3_000) }
    val window = g.deps.windowControls
    // 画中画（MP-7、D-lead-20）：平台有能力才有钮；进了画中画，画面那一格换成纸面说明、弹幕不画
    val pip = play.platform.pictureInPicture
    val inPip = pip?.active == true
    var opened by remember { mutableStateOf(PlayerPanel.of(preset.playerPanel)) }
    // 菜单栏「播放 › 投屏（DLNA）…」（macOS）：打开投屏面板
    val castAsked = PlayerCommandSink.castPanelRequested
    LaunchedEffect(castAsked) {
        if (castAsked) {
            PlayerCommandSink.castPanelRequested = false
            if (com.gugaart.ui.cast.Cast.host != null) opened = PlayerPanel.Cast
        }
    }
    LocalPlayerPanelDriver.current?.let { driver -> LaunchedEffect(driver.value) { opened = PlayerPanel.of(driver.value) } }
    var dismissedNext by remember(snap.itemId) { mutableStateOf(false) }
    val playing = snap.phase == PlayPhase.Ready && !snap.paused
    val panel = effectivePanel(opened, snap.autoNext, snap.next != null, dismissedNext)
    val awake = playing || snap.phase == PlayPhase.Preparing || snap.phase == PlayPhase.Loading
    LaunchedEffect(awake) { play.platform.keepAwake(awake) }
    DisposableEffect(play) {
        play.platform.playerVisible(true)
        onDispose {
            play.platform.playerVisible(false)
            play.platform.keepAwake(false)
        }
    }
    LaunchedEffect(playing, panel) { controls.canHide = playing && panel == null && !preset.playerPin }
    LaunchedEffect(preset.playerControls, snap.phase) { if (!preset.playerControls && snap.phase == PlayPhase.Ready) controls.hide() }
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
    val heat = com.gugaart.ui.player.LocalHeatPreview.current ?: remember(danmaku.heat) { heatDots(danmaku.heat?.model?.values, 140) }
    val introEnd = snap.markers.intro?.endMs?.toDouble()
    val creditsStart = snap.markers.credits?.startMs?.toDouble()
    val position: () -> Double = { clock.value.positionMs }
    val duration = clockDuration.value.takeIf { it > 0 } ?: snap.resolved?.durationMs?.toDouble() ?: 0.0
    // 网速：mpv 自己报的下载速度，一直在取，控件一出来就有数（NetSpeed.kt）
    val speed = com.gugaart.ui.player.rememberNetSpeed(play.controller)
    val show = controls.visible || panel != null

    // ---- 键位：上游 PlayerKeys 解析，命令接到控制器的公开方法
    val snapNow by rememberUpdatedState(snap)
    val episodesNow by rememberUpdatedState(com.gugaart.ui.player.LocalPlayerEpisodes.current)
    val prefsNow by rememberUpdatedState(prefs)
    val durationNow by rememberUpdatedState(duration)
    val stepSec = maxOf(1, PlaybackSettings.of(appSettings).seekStepSec.toInt())
    val stepNow by rememberUpdatedState(stepSec * 1_000.0)
    val openedNow by rememberUpdatedState(opened)
    val run: (PlayerKeyCommand) -> Unit = remember(controller) {
        { cmd ->
            controls.bump()
            controller.userGesture()
            when (cmd) {
                PlayerKeyCommand.PlayPause -> controller.togglePlay()
                is PlayerKeyCommand.Seek -> controller.seekBy(cmd.dir * (if (cmd.big == true) stepNow * 3 else stepNow))
                is PlayerKeyCommand.Volume -> controller.setVolume(((if (prefsNow.muted) 0.0 else prefsNow.volume) + cmd.delta).coerceIn(0.0, 1.0))
                PlayerKeyCommand.Mute -> controller.toggleMute()
                PlayerKeyCommand.Fullscreen -> window?.let { it.setFullscreen(!it.fullscreen) }
                PlayerKeyCommand.Danmaku -> danmaku.updateRender("enabled" to JsonPrimitive(!danmaku.prefsNow().render.enabled))
                is PlayerKeyCommand.Rate -> controller.setRate(PlayerFormat.stepRate(snapNow.rate, cmd.dir))
                PlayerKeyCommand.Next -> if (snapNow.next != null) controller.playNext()
                PlayerKeyCommand.Prev -> if (snapNow.prev != null) controller.playPrev()
                PlayerKeyCommand.Episodes -> if (episodesNow != null || snapNow.episodes.isNotEmpty()) opened = PlayerPanel.Eps
                PlayerKeyCommand.Skip -> controller.skip()
                PlayerKeyCommand.Stop -> play.exit()
                is PlayerKeyCommand.Fraction -> if (durationNow > 0) controller.seekTo(durationNow * cmd.f)
                PlayerKeyCommand.NextPeak -> slots.nextPeakAfter?.invoke(controller.positionMs())?.let { controller.seekTo(it) }
                PlayerKeyCommand.Subtitles -> opened = PlayerPanel.Sub
                PlayerKeyCommand.Favorite -> controller.toggleFavorite()
                else -> {}
            }
        }
    }
    // 底栏的快捷键行：不常驻，按 ? 才出来、再按一次收起（审计 P2-4）
    var keysHint by remember { mutableStateOf(false) }
    // 字幕时间：[ 减、] 加（按住连续调，按满 2 秒每次 0.5 秒）；记着这一次按下的时刻
    val timingDown = remember { arrayOfNulls<kotlin.time.TimeSource.Monotonic.ValueTimeMark>(1) }
    val handleKey: (KeyEvent) -> Boolean = remember(run) {
        keys@{ e ->
            val st = KeyState(UiMode.Desktop, controls.visible, snapNow.skip != null && snapNow.phase == PlayPhase.Ready, KeyTarget.Other)
            if (e.type == KeyEventType.KeyUp && (e.key == Key.LeftBracket || e.key == Key.RightBracket)) timingDown[0] = null
            if (e.type == KeyEventType.KeyDown && e.key == Key.Slash && openedNow == null && !e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed) {
                keysHint = !keysHint
                controls.bump()
                return@keys true
            }
            val like = if (e.type == KeyEventType.KeyDown && openedNow == null && !e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed) PlayerKeys.eventLike(e) else null
            val timing = like?.let { com.gugaart.ui.player.subtitleTimingKey(it.key) }
            if (timing != null) {
                val at = timingDown[0] ?: kotlin.time.TimeSource.Monotonic.markNow().also { timingDown[0] = it }
                if (snapNow.subtitleIndex != null) {
                    play.stepSubtitleTiming(timing * com.gugaart.ui.player.subtitleTimingStep(at.elapsedNow().inWholeMilliseconds))
                    controls.bump()
                }
                return@keys true
            }
            val cmd = like?.let { PlayerKeys.resolve(it, st) }
            if (cmd != null) run(cmd)
            cmd != null && cmd != PlayerKeyCommand.Bump
        }
    }
    // Esc：先收面板，再退出全屏；都没有 → 交给外壳的返回栈（离开播放器）
    val handleBack: (KeyEvent) -> Boolean = remember(window) {
        { e ->
            if (e.key != Key.Escape && e.key != Key.Back) false
            else when {
                openedNow != null -> {
                    if (e.type == KeyEventType.KeyUp) opened = null
                    true
                }
                window?.fullscreen == true -> {
                    if (e.type == KeyEventType.KeyUp) window.setFullscreen(false)
                    true
                }
                else -> false
            }
        }
    }
    DisposableEffect(handleKey, handleBack) {
        val detach = PlayerKeyRouter.attach { e -> handleKey(e) || handleBack(e) }
        // 菜单栏的「播放」组经这个接收者把命令交给当前播放器（M2-3 / M2-4）
        PlayerCommandSink.sink = { cmd -> run(cmd) }
        onDispose {
            detach()
            PlayerCommandSink.sink = null
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    when (snap.phase) {
        PlayPhase.Error -> {
            DesktopPlayerError(play, snap)
            return
        }
        PlayPhase.Ended -> if (snap.autoNext == AutoNextState.Idle) {
            EndedPage(play, snap, art)
            return
        }
        else -> {}
    }

    // ---- 版式：四边的页边（rem），窄窗收紧；面板 = 右侧再让一栏
    val win = LocalWindowDp.current
    val narrow = win.width.value <= DesktopPlayerSpec.NARROW
    val insetMode = LocalViewportMode.current == ViewportMode.Inset
    val topH = Guga.rem(if (narrow) DesktopPlayerSpec.NARROW_TOP else DesktopPlayerSpec.TOP)
    val bottomH = Guga.rem(if (narrow) DesktopPlayerSpec.NARROW_BOTTOM else DesktopPlayerSpec.BOTTOM)
    val edgeW = Guga.rem(if (narrow) DesktopPlayerSpec.NARROW_EDGE else DesktopPlayerSpec.EDGE)
    val sideW = if (narrow) win.width / 2 else Guga.rem(if (win.width.value >= 2200f) DesktopPlayerSpec.SIDE_XL else DesktopPlayerSpec.SIDE)
    val spec = tween<Dp>(DesktopPlayerSpec.GIVE_WAY_MS)
    // P1-4（1.5.1）：鼠标唤出控件时画面不缩——顶栏和控制坞压在画面上、边缘压暗（和手机一样）；只有打开页边面板时画面才让位（带过渡）
    val giveWay = panel != null && insetMode
    val pt by animateDpAsState(if (giveWay) topH else 0.dp, spec, label = "pt")
    val pb by animateDpAsState(if (giveWay) bottomH else 0.dp, spec, label = "pb")
    val px by animateDpAsState(if (giveWay) edgeW else 0.dp, spec, label = "px")
    val side by animateDpAsState(if (panel != null && insetMode) sideW else 0.dp, spec, label = "side")
    // M2-6：macOS 系统全屏时底色纯黑（和系统涂黑的刘海那一条接上）；全屏又有不可用的顶条（safeTop > 0）时，画面上下各让出这一条、居中，
    // 顶栏排在这一条之下。壳现在给的 safeTop 是 0（全屏窗口本来就在刘海之下，探针 S3），这段逻辑由离屏测试 player-full-notch 拿假宿主证明
    val deskHost = LocalDesktopHost.current
    val macFull = window?.fullscreen == true && deskHost.windowKeys == WindowKeysStyle.SystemLeft
    val safe = if (macFull) deskHost.safeTop else 0.dp
    val bg = if (macFull) DesktopSpec.FULLSCREEN_BLACK else DesktopPlayerSpec.MARGIN
    val chromeBg = if (insetMode) bg else bg.copy(alpha = 0.9f)
    // 不让位时顶栏 / 控制坞的底：画面边缘压暗（上深下透 / 下深上透），让位时是页边的实底
    val topBg: Brush = if (giveWay) SolidColor(chromeBg) else Brush.verticalGradient(listOf(bg.copy(alpha = 0.78f), Color.Transparent))
    val dockBg: Brush = if (giveWay) SolidColor(chromeBg) else Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = 0.6f), bg.copy(alpha = 0.9f)))

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { handleKey(it) }
            .onKeyEvent { handleBack(it) }
            .focusRequester(focus)
            .focusable()
            // 不可用的顶条和对称的底条：涂底色（画面不进去）
            .drawBehind {
                val sp = safe.toPx()
                if (sp > 0.5f) {
                    drawRect(bg, Offset.Zero, Size(size.width, sp))
                    drawRect(bg, Offset(0f, size.height - sp), Size(size.width, sp))
                }
            }
            // 鼠标动一下 = 控件出来（只看，不吃事件）
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var last = Offset.Unspecified
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        val p = e.changes.firstOrNull()?.position ?: continue
                        if (e.type == PointerEventType.Move && p != last) controls.bump()
                        last = p
                    }
                }
            }
            .testTag("player-desktop"),
    ) {
        // ---- 画面：在让出来的矩形里按片子的比例放（黑边由页边色补上；画面那一块不画底——下面是原生视频层）
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = px, top = pt + safe, end = px + side, bottom = pb + safe)) {
            val v = snap.resolved?.mediaSource?.mediaStreams?.firstOrNull { it.type == "Video" }
            val vw = v?.width ?: 0
            val vh = v?.height ?: 0
            val ratio = if (vw > 0 && vh > 0) vw.toFloat() / vh else 16f / 9f
            val fw = minOf(maxWidth, maxHeight * ratio)
            val fh = fw / ratio
            // 画面上面留多少空：平常上下居中；开着弹幕时至少留够弹幕区那么高（空不够就全留在上面），弹幕先排在黑边里
            val gapTop = pictureTopGap(maxHeight.value, fh.value, if (render.enabled) render.area.toFloat() else 0f).dp
            Canvas(Modifier.fillMaxSize()) {
                val w = fw.toPx()
                val h = fh.toPx()
                val l = (size.width - w) / 2
                val t = gapTop.toPx()
                if (l > 0.5f) {
                    drawRect(bg, Offset.Zero, Size(l, size.height))
                    drawRect(bg, Offset(l + w, 0f), Size(size.width - l - w, size.height))
                }
                if (t > 0.5f) {
                    drawRect(bg, Offset(l, 0f), Size(w, t))
                    drawRect(bg, Offset(l, t + h), Size(w, size.height - t - h))
                }
            }
            Box(Modifier.align(Alignment.TopCenter).padding(top = gapTop).size(fw, fh).clipToBounds().then(if (giveWay) Modifier.border(1.dp, colors.ink.copy(alpha = 0.14f)) else Modifier).testTag("player-frame")) {
                Picture(play, snap, engine, slots, render.enabled, duration, showBuffer, controls = show, onClick = {
                    // 开着面板（字幕 / 音轨 / 弹幕…）时点画面 = 只收面板，和返回键一样；不暂停（1.5.1 subfix 问题 2）
                    if (opened != null) {
                        opened = null
                    } else {
                        controller.userGesture()
                        controller.togglePlay()
                    }
                    controls.bump()
                }, onDoubleClick = { window?.let { it.setFullscreen(!it.fullscreen) } })
                if (inPip && pip != null) DesktopPipNote(onBack = { pip.toggle() })
            }
            // ---- 弹幕：从画面上方那条黑边的顶上开始排（宽银幕的黑边、开面板时画面让出来的空），黑边不够再接着压到画面顶上；
            //      所以它不在画面那一格里，而是一块「黑边 + 画面」的格子（上游的弹幕层从格子顶往下排 area 那么高）。不接鼠标。
            (com.gugaart.ui.player.LocalDanmakuSlotOverride.current ?: slots.danmaku)?.takeIf { !inPip }?.let { dm ->
                val holding = rememberUpdatedState(snap.holding)
                val dur = rememberUpdatedState(duration)
                Box(Modifier.align(Alignment.TopCenter).size(fw, gapTop + fh).clipToBounds().testTag("player-danmaku")) {
                    dm(DanmakuHost(snap.itemId, snap.item, controller.presentationClock, holding, render.enabled, dur, UiMode.Desktop), Modifier.matchParentSize())
                }
            }
        }

        if (show) {
            val host = LocalDesktopHost.current
            // macOS 非全屏：返回钮让位红绿灯（M2-2）；全屏时红绿灯被系统收走，让位归零
            val macInset = host.windowKeys == WindowKeysStyle.SystemLeft && window?.fullscreen != true
            // ---- 顶栏（页边里）：返回｜logo + 集名 + 规格｜她的一格（暂停才有）｜印章位 + 收藏｜置顶｜窗口键
            Row(Modifier.align(Alignment.TopStart).padding(top = safe).fillMaxWidth().height(topH).background(topBg).padding(start = if (macInset) maxOf(Guga.rem(2f), host.leadingInsetPx.dp) else Guga.rem(2f)).testTag("player-top"), verticalAlignment = Alignment.CenterVertically) {
                EdgeButton(GuguIcon.ChevronLeft, "返回", play.exit, tag = "player-back")
                Spacer(Modifier.width(Guga.rem(1.6f)))
                val title: @Composable () -> Unit = {
                    Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        val showLogo = snap.item?.seriesName != null || (art.logo != null && !preset.textTitles)
                        if (showLogo) TitleLogo(snap.item?.seriesName ?: snap.title, art.logo, Guga.rem(11f), Guga.rem(3.8f), Modifier.padding(end = Guga.rem(1.4f)), textOnly = preset.textTitles, baseSize = rsp(2.2f))
                        val ep = snap.item?.indexNumber?.takeIf { snap.item?.seriesName != null }?.let { "第 $it 集 · " }.orEmpty()
                        BasicText(ep + (snap.item?.name ?: snap.title), Modifier.weight(1f, fill = false), style = Guga.song(Guga.player.headline, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!narrow) specOf(snap, play).takeIf { it.isNotEmpty() }?.let { BasicText(it, Modifier.padding(start = Guga.rem(1.4f)), style = Guga.note(Guga.player.spec, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
                // 顶栏中段可以拖动窗口（上游 WindowControls.DragArea）
                Box(Modifier.weight(1f).fillMaxHeight()) { if (window != null) window.DragArea(Modifier.fillMaxSize()) { title() } else title() }
                if (!narrow) DesktopPauseSeat(snap.paused && snap.phase == PlayPhase.Ready, position)
                SealSlot(snap.favorite == true, Modifier.padding(start = Guga.rem(1f)), size = Guga.rem(3.6f))
                Spacer(Modifier.width(Guga.rem(1f)))
                // 投屏（DLNA）
                if (com.gugaart.ui.cast.Cast.host != null) {
                    EdgeButton(GuguIcon.Tv, "投屏", { opened = if (opened == PlayerPanel.Cast) null else PlayerPanel.Cast }, selected = com.gugaart.ui.cast.Cast.active != null, tag = "player-cast")
                    Spacer(Modifier.width(Guga.rem(1f)))
                }
                EdgeButton(GuguIcon.Heart, if (snap.favorite == true) "取消收藏" else "收藏", { controller.toggleFavorite() }, selected = snap.favorite == true, tag = "player-fav")
                // 画中画（设计稿 design/macos/ 的 st=pip：收藏的右边）：有能力才画
                if (pip != null) {
                    Spacer(Modifier.width(Guga.rem(1.6f)))
                    EdgeFrame(if (inPip) "回到窗口" else "画中画", { pip.toggle() }, tag = "player-pip") { hover ->
                        PipGlyph(if (inPip) colors.seal else if (hover) colors.onInk else colors.ink, Guga.rem(2f))
                    }
                }
                // 置顶：有窗口才画（不能用的控件不画）
                if (window != null) {
                    Spacer(Modifier.width(Guga.rem(1.6f)))
                    EdgeButton(if (window.alwaysOnTop) GuguIcon.PinOff else GuguIcon.Pin, if (window.alwaysOnTop) "取消置顶" else "窗口置顶", { window.setAlwaysOnTop(!window.alwaysOnTop) }, tag = "player-pin")
                }
                Spacer(Modifier.width(Guga.rem(1.6f)))
                if (host.windowKeys != WindowKeysStyle.SystemLeft && window?.fullscreen != true) Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.TopEnd) { WindowKeys() }
            }
            // ---- 左缘图注、右缘音量（页边里）
            if (px > 0.dp && !narrow) {
                Box(Modifier.align(Alignment.TopStart).padding(top = pt + safe, bottom = pb + safe).width(px).fillMaxHeight().background(chromeBg), contentAlignment = Alignment.Center) {
                    val caption = listOfNotNull(snap.item?.parentIndexNumber?.let { "第 $it 季" }, snap.item?.indexNumber?.takeIf { snap.item?.seriesName != null }?.let { "第 $it 集" }, snap.item?.name?.let { "《$it》" } ?: snap.title).joinToString(" · ")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        for (cell in verticalCells(caption).take(26)) BasicText(cell, style = Guga.song(rsp(1.15f), color = colors.faint, lineHeight = rsp(1.75f)), maxLines = 1)
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = pt + safe, bottom = pb + safe, end = side).width(px).fillMaxHeight().background(chromeBg), contentAlignment = Alignment.Center) {
                    val vol = if (prefs.muted) 0.0 else prefs.volume
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.testTag("player-volume")) {
                        BasicText("${kotlin.math.round(vol * 100).toInt()}", style = Guga.numeral(rsp(1.2f), colors.dim), maxLines = 1)
                        Box(Modifier.padding(vertical = Guga.rem(1f)).width(Guga.rem(0.3f)).height(Guga.rem(16f)).background(colors.line), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(vol.toFloat().coerceIn(0f, 1f)).background(colors.ink))
                        }
                        Icon(GuguIcon.Volume2, colors.dim, Guga.rem(2f))
                    }
                }
            }
            // ---- 底部控制坞（页边里）：三段对称 + 时间轴
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(bottomH).background(dockBg).padding(horizontal = edgeW).padding(bottom = Guga.rem(1f)).testTag("player-dock"), verticalArrangement = Arrangement.Bottom) {
                Layout(
                    modifier = Modifier.fillMaxWidth().height(Guga.rem(5.2f)),
                    content = {
                        // 左：时间
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerTimeText(position, Guga.numeral(Guga.player.clock))
                            BasicText(" / ${Rules.clock(duration.toLong())}", style = Guga.numeral(Guga.player.clock, colors.dim), maxLines = 1)
                            // 网速（用户 2026-11-17：「调出菜单，能够自动显示当前的网络速度」）：和时间一样是一直在变的数，排在时间后面
                            com.gugaart.ui.player.NetSpeedText(speed, Modifier.padding(start = Guga.rem(1.6f)).testTag("player-speed"), Guga.note(Guga.player.hint, colors.ink, letterSpacing = 0.04.em))
                            if (keysHint && !narrow && win.width.value >= 1280f) {
                                BasicText(
                                    "空格 暂停 · ← → ${(stepNow / 1000).toInt()} 秒 · ↑ ↓ 音量 · [ ] 字幕时间",
                                    Modifier.padding(start = Guga.rem(1.6f)).testTag("player-keys-hint").border(1.dp, colors.line).padding(horizontal = Guga.rem(0.8f), vertical = Guga.rem(0.2f)),
                                    style = Guga.note(Guga.player.hint, colors.dim, letterSpacing = 0.04.em),
                                    maxLines = 1,
                                )
                            }
                        }
                        // 中：上一集 / 退步长 / 播放 / 进步长 / 下一集
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.6f))) {
                            DockKey(GuguIcon.SkipBack, "上一集", enabled = snap.prev != null) { controller.playPrev(); controls.bump() }
                            DockKey("退 $stepSec 秒", onClick = { controller.seekBy(-stepNow); controls.bump() }) { ink -> SeekStepIcon(false, stepSec, ink, Guga.rem(2f)) }
                            DockKey(if (snap.paused) GuguIcon.Play else GuguIcon.Pause, if (snap.paused) "播放" else "暂停", big = true, tag = "player-play") { controller.userGesture(); controller.togglePlay(); controls.bump() }
                            DockKey("进 $stepSec 秒", onClick = { controller.seekBy(stepNow); controls.bump() }) { ink -> SeekStepIcon(true, stepSec, ink, Guga.rem(2f)) }
                            DockKey(GuguIcon.SkipForward, "下一集", enabled = snap.next != null) { controller.playNext(); controls.bump() }
                        }
                        // 右：功能词（窄窗收成图标）+ 全屏
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                            for (p in PlayerPanel.WORDS) {
                                if (p == PlayerPanel.Eps && com.gugaart.ui.player.LocalPlayerEpisodes.current == null && snap.episodes.isEmpty()) continue
                                if (p == PlayerPanel.Core && !coreVisible) continue
                                val value = when (p) {
                                    PlayerPanel.Eps -> snap.item?.indexNumber?.let { n -> com.gugaart.ui.player.LocalPlayerEpisodes.current?.season?.number?.let { s -> "第 $s 季 · 第 $n 集" } ?: "第 $n 集" }
                                    PlayerPanel.Sub -> com.gugaart.ui.player.subtitleWordValue(snap)
                                    PlayerPanel.Audio -> com.gugaart.ui.player.audioWordValue(snap)
                                    PlayerPanel.Speed -> rateLabel(snap.rate)
                                    PlayerPanel.Quality -> if (snap.maxBitrateMbps > 0) "${snap.maxBitrateMbps.toInt()} Mbps" else specLine(snap.resolved?.mediaSource)?.substringBefore(" · ") ?: "原画"
                                    PlayerPanel.Core -> com.gugaart.ui.player.engineShort(snap.engineId)
                                    PlayerPanel.Dm -> if (danmakuUnconfigured) "未配置" else if (render.enabled) "开" else "关"
                                    PlayerPanel.Next, PlayerPanel.Cast -> null
                                }
                                DockWord(
                                    p,
                                    value?.take(8),
                                    panel == p,
                                    compact = win.width.value < 1800f,
                                    iconOnly = win.width.value < 1280f,
                                    warn = p == PlayerPanel.Dm && (danmakuUnconfigured || slots.danmakuWarns?.invoke() == true),
                                    forceValue = p == PlayerPanel.Dm && danmakuUnconfigured,
                                ) {
                                    if (p == PlayerPanel.Dm && danmakuUnconfigured) {
                                        g.nav.navigate(Route.Settings("danmaku"))
                                    } else {
                                        opened = if (opened == p) null else p
                                        controls.bump()
                                    }
                                }
                            }
                            if (window != null) DockKey(if (window.fullscreen) GuguIcon.Minimize else GuguIcon.Maximize, if (window.fullscreen) "退出全屏" else "全屏", tag = "player-full") { window.setFullscreen(!window.fullscreen) }
                        }
                    },
                ) { measurables, constraints ->
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val loose = constraints.copy(minWidth = 0, minHeight = 0)
                    val mid = measurables[1].measure(loose)
                    val right = measurables[2].measure(loose.copy(maxWidth = (w - mid.width).coerceAtLeast(0)))
                    val left = measurables[0].measure(loose.copy(maxWidth = (w - mid.width - right.width).coerceAtLeast(0)))
                    val idealMidX = (w - mid.width) / 2
                    val minMidX = left.width
                    val maxMidX = (w - right.width - mid.width).coerceAtLeast(minMidX)
                    val midX = idealMidX.coerceIn(minMidX, maxMidX)
                    layout(w, h) {
                        left.placeRelative(0, (h - left.height) / 2)
                        mid.placeRelative(midX, (h - mid.height) / 2)
                        right.placeRelative(w - right.width, (h - right.height) / 2)
                    }
                }
                // 1.6 C3：悬停 / 拖进度条时小横条上方出缩略图（Emby 的 BIF；第一次悬停才问服务器，没有就照旧只有时间）
                val seekThumbs = com.gugaart.ui.player.rememberSeekThumbs()
                DesktopTimeline(
                    position, duration, heat, introEnd, creditsStart,
                    sampleHover = if (preset.state == "hover") DesktopPlayerSpec.HOVER_SAMPLE else null,
                    onScrubStart = { controller.setScrubbing(true); controls.bump() },
                    onScrub = { controller.scrubTo(it) },
                    onScrubEnd = { controller.seekTo(it); controller.setScrubbing(false); controls.bump() },
                    onSeek = { controller.seekTo(it); controls.bump() },
                    thumbAt = seekThumbs::image,
                    onPreview = { seekThumbs.prepare(play, g) },
                )
            }
        }
        // ---- 右侧页边：面板（内容复用手机的 PanelBody）；片尾的「下一集」也排在这里
        if (panel != null) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = if (insetMode) pt else topH, bottom = if (insetMode) pb else bottomH).width(sideW).fillMaxHeight().background(chromeBg)
                    .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                    .padding(horizontal = Guga.rem(if (narrow) 1.4f else 2.4f))
                    // 面板空白处的点不漏到下面的画面（1.5.1 subfix：不吃事件，只挡住下面的兄弟，components/TouchGuard.kt）
                    .holdTouches().testTag("player-side"),
            ) {
                Box(Modifier.weight(1f)) {
                    Column {
                        PanelBody(panel, play, snap, danmaku, slots, art, position, close = {
                            if (panel == PlayerPanel.Next) dismissedNext = true
                            opened = null
                        })
                    }
                }
                // 片尾的「下一集」面板自己有「取消」（P1-3），底下不再重复
                val closePress = rememberPressState()
                if (panel != PlayerPanel.Next) BasicText(
                    "收起  →",
                    Modifier.padding(vertical = Guga.rem(1f)).deskFocusRing(closePress).pressable(closePress, label = "收起面板") {
                        opened = null
                    }.testTag("player-side-close"),
                    style = Guga.song(Guga.player.body, FontWeight.Bold, colors.dim),
                    maxLines = 1,
                )
            }
        }
        // 弹幕来源：从左边滑出来（有歧义时自己出来一次）
        DanmakuPickerHost(danmaku, snap.itemId, Guga.rem(48f))
        // 字幕搜索：同一种做法，从左边出来（字幕面板「搜索字幕…」打开）
        SubtitleSearchHost(play, Guga.rem(48f))
    }
}

/**
 * 画面上面留多少空（dp）。[available] = 让出来的矩形的高，[picture] = 画面的高，[area] = 弹幕的显示区域（0 = 没开弹幕）。
 * 平常上下居中（空的一半）。开着弹幕时：弹幕那一格 = 上面的空 + 画面，弹幕区 = 格子高 × area，从格子顶往下排——
 * 所以上面的空至少要 `area × picture / (1 − area)` 才装得下弹幕区；居中时不够就把画面往下挪一点，空全用上还不够就全留在上面
 * （弹幕接着压到画面顶上）。没有空（画面占满）= 0。
 */
fun pictureTopGap(available: Float, picture: Float, area: Float): Float {
    val spare = (available - picture).coerceAtLeast(0f)
    val half = spare / 2f
    if (area <= 0f || area >= 1f) return half
    val need = area * picture / (1f - area)
    return minOf(spare, maxOf(half, need))
}

/** 画面这一块：原生视频层的占位（把矩形报给引擎）+ 暂停 / 缓冲的压暗 + 「跳过片头」玻璃胶囊。点一下 = 播放 / 暂停，双击 = 全屏。 */
@Composable
private fun BoxScope.Picture(play: PlaySession, snap: PlayerSnapshot, engine: PlayerEngine?, slots: PlayerSlots, danmakuOn: Boolean, duration: Double, showBuffer: Boolean, controls: Boolean, onClick: () -> Unit, onDoubleClick: () -> Unit) {
    val controller = play.controller
    val colors = Guga.colors
    LocalPlayerStill.current?.let { still -> Canvas(Modifier.matchParentSize()) { drawCover(still, Framing(0.5f, 0.3f)) } }
    com.gugaart.ui.player.GugaVideoSurface(engine, Modifier.matchParentSize()) { VeilLayer(controller.veil) }
    val dim = when {
        showBuffer -> PlayerSpec.BUFFER_DIM
        snap.paused && snap.phase == PlayPhase.Ready -> PlayerSpec.PAUSE_DIM
        else -> 0f
    }
    Box(
        Modifier.matchParentSize().drawBehind { if (dim > 0f) drawRect(colors.paper.copy(alpha = dim)) }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onDoubleClick() }, onTap = { onClick() }) }
            .testTag(if (snap.paused) "player-picture-paused" else "player-picture"),
    )
    if (showBuffer) {
        Column(Modifier.align(Alignment.Center).background(colors.inkWell).padding(horizontal = Guga.rem(2.8f), vertical = Guga.rem(1.6f)).testTag("player-buffer"), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(Guga.rem(12f)).height(1.dp).background(colors.ink))
            BasicText("缓冲中", Modifier.padding(top = Guga.rem(1.1f)), style = Guga.song(rsp(1.6f), FontWeight.Bold, letterSpacing = 0.2.em), maxLines = 1)
            val line = play.notice?.title ?: snap.switching?.why ?: snap.stepLabel.takeIf { it.isNotEmpty() }
            if (line != null) BasicText(line, Modifier.padding(top = Guga.rem(0.6f)), style = Guga.note(rsp(1.3f), colors.dim), maxLines = 1)
        }
    }
    // 开播那几秒：片源的效果（杜比视界 / HDR10 / 杜比全景声…）
    com.gugaart.ui.player.FormatFlash(snap, 1.15f, Modifier.align(Alignment.TopStart).padding(start = Guga.rem(2f), top = Guga.rem(2f)))
    // 字幕智能匹配的那句提示：排在徽章那一行下面（不叠）；「找到…」控件出来时在，「已自动加载…」亮几秒就淡出（subtitle/BestMatchUi.kt）
    // 下面一格：「换到别台的源没成功」那一句（亮 4 秒，player/NoticeFlash.kt）；两样同时在时上下排开，不叠
    Column(Modifier.align(Alignment.TopStart).padding(start = Guga.rem(2f), top = Guga.rem(2f) + 56.dp), verticalArrangement = Arrangement.spacedBy(Guga.rem(0.6f))) {
        com.gugaart.ui.subtitle.AutoBestFlash(play, controls, scale = 1.15f, maxWidth = Guga.rem(56f))
        com.gugaart.ui.player.PlayerNoticeFlash(play, 1.15f, maxWidth = Guga.rem(56f))
    }
    // 「跳过片头 / 片尾」：玻璃胶囊（播放器里唯一的玻璃）。播放页不在根的 GlassScene 里：这里自己起一个「视频底图」场景（底图不画，画面是原生层），
    // 后端先用上游的 Plates 退路——视频取帧供底图的那一套（上游 PlayerGlassScene）是 internal，接不上。
    snap.skip?.let { skip ->
        val none = remember { ImageBitmap(1, 1) }
        GlassScene(backend = GlassBackend.Plates, backdrop = none, modifier = Modifier.matchParentSize(), backdropVisible = false) {
            val press = rememberPressState()
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = Guga.rem(3.2f), bottom = Guga.rem(3.2f)).height(Guga.rem(4.4f)).glass(MaterialName.Bar, Guga.rem(2.2f), press)
                    .deskFocusRing(press).pressable(press, label = skip.label) { controller.skip() }.padding(horizontal = Guga.rem(2f)).testTag("player-skip"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f)),
            ) {
                Icon(GuguIcon.FastForward, colors.ink, Guga.rem(1.8f))
                BasicText(skip.label, style = Guga.song(rsp(1.45f), FontWeight.Bold, letterSpacing = 0.08.em), maxLines = 1)
                BasicText("Enter", style = Guga.numeral(rsp(1.2f), colors.ink.copy(alpha = 0.7f)), maxLines = 1)
            }
        }
    }
}

/** 顶栏的方框按钮（4 rem，1 dp 线；悬停 = 反白）。收藏了 = 实心朱红爱心。 */
@Composable
private fun EdgeButton(icon: GuguIcon, label: String, onClick: () -> Unit, selected: Boolean = false, tag: String = "player-edge-button") {
    val colors = Guga.colors
    EdgeFrame(label, onClick, tag) { hover -> Icon(icon, if (selected) colors.seal else if (hover) colors.onInk else colors.ink, Guga.rem(2f), filled = selected) }
}

/** [EdgeButton] 的方框本身，里面画什么由 [content] 定（参数 = 悬停中）。 */
@Composable
private fun EdgeFrame(label: String, onClick: () -> Unit, tag: String, content: @Composable (Boolean) -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Box(
        Modifier.size(Guga.rem(4f)).hover(hover).pressGraphics(press).then(if (hover.value) Modifier.background(colors.ink) else Modifier).border(1.dp, colors.faint).deskFocusRing(press).pressable(press, label = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { content(hover.value) }
}

/** 控制坞中段的键（图标；悬停 = 反白）。[enabled] = false：没有上一集 / 下一集时变淡、点不动（位置留着，三段才对称）。 */
@Composable
private fun DockKey(icon: GuguIcon, label: String, big: Boolean = false, enabled: Boolean = true, tag: String = "player-key", onClick: () -> Unit) {
    DockKey(label, big, enabled, tag, onClick) { ink ->
        Icon(icon, ink, Guga.rem(if (big) 2.8f else 2f), filled = icon == GuguIcon.Play || icon == GuguIcon.Pause)
    }
}

@Composable
private fun DockKey(label: String, big: Boolean = false, enabled: Boolean = true, tag: String = "player-key", onClick: () -> Unit, iconContent: @Composable (Color) -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val on = hover.value && enabled
    val ink = if (on) colors.onInk else if (enabled) colors.ink else colors.faint
    Box(
        Modifier.height(Guga.rem(4f)).width(Guga.rem(if (big) 5.2f else 4.4f)).hover(hover).pressGraphics(press).then(if (on) Modifier.background(colors.ink) else Modifier).deskFocusRing(press).pressable(press, enabled = enabled, label = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { iconContent(ink) }
}

/** 一个功能词：图标 + 词 + 斜体的当前值；打开着的 / 悬停 = 反白。[compact] 不写当前值；[iconOnly]（很窄）只剩图标。 */
@Composable
private fun DockWord(panel: PlayerPanel, value: String?, active: Boolean, compact: Boolean, iconOnly: Boolean, warn: Boolean, forceValue: Boolean = false, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val on = active || hover.value
    val ink = if (on) colors.onInk else colors.ink
    Row(
        Modifier.height(Guga.rem(4f)).hover(hover).pressGraphics(press).then(if (on) Modifier.background(colors.ink) else Modifier)
            .deskFocusRing(press).pressable(press, label = panel.word + (value?.let { " $it" } ?: ""), onClick = onClick).padding(horizontal = Guga.rem(if (iconOnly) 0.55f else if (compact) 0.55f else 1.2f)).testTag(if (active) "word-on" else "word"),
        verticalAlignment = Alignment.CenterVertically,
        // 七个功能词（多了「内核」）：窄一点的窗口里字和图标挨近些，免得最后一个被挤掉
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(if (compact) 0.35f else 0.7f)),
    ) {
        Icon(WORD_ICONS.getValue(panel), ink, Guga.rem(2f))
        if (!iconOnly) BasicText(panel.word, style = Guga.song(Guga.player.dock, FontWeight.SemiBold, ink), maxLines = 1)
        if (!iconOnly && (!compact || forceValue) && value != null) BasicText(value, style = Guga.note(Guga.player.dockValue, if (on) colors.onInk else colors.dim), maxLines = 1)
        if (warn) Box(Modifier.size(Guga.rem(0.5f)).background(colors.seal, CircleShape))
    }
}

/** 控制坞左端的时间（单独一格：时钟每走一下只重组这一个字串，1.5.1 性能）。 */
@Composable
private fun PlayerTimeText(position: () -> Double, style: androidx.compose.ui.text.TextStyle) {
    BasicText(Rules.clock(position().toLong()), style = style, maxLines = 1, modifier = Modifier.testTag("player-time"))
}

/** 顶栏里她的一格：**播放时是空的**（格子留着，版面不跳）；暂停才坐进来，旁边写停在哪。她关掉时只剩那行字。 */
@Composable
private fun DesktopPauseSeat(paused: Boolean, positionMs: () -> Double) {
    val colors = Guga.colors
    Row(Modifier.height(Guga.rem(4.4f)).widthIn(min = Guga.rem(17f)).testTag(if (paused) "pause-seat" else "pause-seat-empty"), verticalAlignment = Alignment.CenterVertically) {
        if (!paused) return@Row
        if (LocalGugaPrefs.current.showMascot) Byline(Guga.rem(4.4f), live = false)
        Column(Modifier.padding(start = Guga.rem(1f))) {
            BasicText("PAUSED · 暂停", style = Guga.note(rsp(1.05f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            BasicText("停在 ${Rules.clock(positionMs().toLong())}", style = Guga.note(rsp(1.25f), colors.dim), maxLines = 1)
        }
    }
}

/**
 * 电脑的时间轴（4.4 rem 高）：一排弹幕热度圆点（播过的专色）、轨道、当前位置的小竖条、片头 / 片尾的刻度和小字。
 * 鼠标悬停：一条专色细线 + **预览小横条**（时间 + 这一刻在哪一段），落在控制坞里，不压画面。
 * 预览图（1.6 C3）：服务器生成过缩略图（Emby 的 BIF）就在小横条正上方放这一刻的那张；没有就照旧只排时间和章节，不放假图。
 * 点 = 跳过去；按住拖 = 拖动（松手才 seek）。
 */
@Composable
private fun DesktopTimeline(
    positionMs: () -> Double,
    durationMs: Double,
    heat: FloatArray,
    introEndMs: Double?,
    creditsStartMs: Double?,
    sampleHover: Float?,
    onScrubStart: () -> Unit,
    onScrub: (Double) -> Unit,
    onScrubEnd: (Double) -> Unit,
    onSeek: (Double) -> Unit,
    /** 1.6 C3：这一刻的缩略图（没有 = null，小横条照旧只有时间和章节）。 */
    thumbAt: ((Double) -> androidx.compose.ui.graphics.ImageBitmap?)? = null,
    /** 第一次悬停 / 拖：让缩略图去问服务器。 */
    onPreview: () -> Unit = {},
) {
    val colors = Guga.colors
    var drag by remember { mutableStateOf<Double?>(null) }
    var hoverX by remember { mutableStateOf(sampleHover) }
    val previewing = hoverX != null || drag != null
    androidx.compose.runtime.LaunchedEffect(previewing) { if (previewing) onPreview() }
    val shown = drag ?: positionMs()
    val dur = rememberUpdatedState(durationMs)
    BoxWithConstraints(Modifier.fillMaxWidth().height(Guga.rem(4.4f)).testTag("player-timeline")) {
        val width = maxWidth
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            when (e.type) {
                                PointerEventType.Move, PointerEventType.Enter -> hoverX = (e.changes.first().position.x / size.width).coerceIn(0f, 1f)
                                PointerEventType.Exit -> hoverX = sampleHover
                            }
                        }
                    }
                }
                .pointerInput(Unit) { detectTapGestures { p -> if (dur.value > 0) onSeek((p.x / size.width).coerceIn(0f, 1f) * dur.value) } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            if (dur.value > 0) {
                                drag = (p.x / size.width).coerceIn(0f, 1f) * dur.value
                                onScrubStart()
                            }
                        },
                        onDragEnd = { drag?.let(onScrubEnd); drag = null },
                        onDragCancel = { drag = null },
                    ) { change, _ ->
                        if (dur.value > 0) {
                            val ms = (change.position.x / size.width).coerceIn(0f, 1f) * dur.value
                            drag = ms
                            onScrub(ms)
                        }
                    }
                },
        ) {
            val rem = size.height / 4.4f
            val frac = if (durationMs > 0) (shown / durationMs).toFloat().coerceIn(0f, 1f) else 0f
            val railY = size.height - 1.75f * rem
            // 弹幕热度：和手机同一种画法——从轨道往上长的一排细柱（播过的专色、没播的米白压暗；没有热力图就不画）
            drawHeatColumns(heat, frac, railY - 0.3f * rem, minOf(HeatSpec.DESKTOP_MAX.toPx(), 2.2f * rem), colors.spot, colors.ink)
            drawLine(colors.ink.copy(alpha = 0.24f), Offset(0f, railY), Offset(size.width, railY), 0.3f * rem)
            drawLine(colors.ink, Offset(0f, railY), Offset(size.width * frac, railY), 0.3f * rem)
            drawRect(colors.ink, Offset(size.width * frac - 0.2f * rem, railY - 0.75f * rem), Size(0.4f * rem, 1.5f * rem))
            if (durationMs > 0) for (m in listOfNotNull(introEndMs, creditsStartMs)) {
                val x = size.width * (m / durationMs).toFloat().coerceIn(0f, 1f)
                drawLine(colors.faint, Offset(x, railY + 0.3f * rem), Offset(x, size.height), 1.dp.toPx())
            }
            hoverX?.let { h -> drawLine(colors.spot, Offset(size.width * h, railY - 1.6f * rem), Offset(size.width * h, railY + rem), 1.dp.toPx()) }
        }
        // 章节小字：片头 / 片尾（有标记才写）
        if (durationMs > 0) {
            if (introEndMs != null) {
                BasicText("片头", Modifier.align(Alignment.BottomStart).padding(start = Guga.rem(0.5f)), style = Guga.note(rsp(1.05f), colors.faint), maxLines = 1)
                BasicText("正片", Modifier.align(Alignment.BottomStart).offset(x = width * (introEndMs / durationMs).toFloat().coerceIn(0f, 0.9f) + Guga.rem(0.5f)), style = Guga.note(rsp(1.05f), colors.faint), maxLines = 1)
            }
            if (creditsStartMs != null) BasicText("片尾", Modifier.align(Alignment.BottomStart).offset(x = width * (creditsStartMs / durationMs).toFloat().coerceIn(0f, 0.96f) + Guga.rem(0.5f)), style = Guga.note(rsp(1.05f), colors.faint), maxLines = 1)
        }
        // 悬停预览：一张小横条（纸片），在时间轴上方、控制坞里
        val h = hoverX
        if (h != null && durationMs > 0) {
            val ms = h * durationMs
            val slip = Guga.rem(15f)
            Row(
                Modifier.align(Alignment.TopStart).offset(x = (width * h - slip / 2).coerceIn(0.dp, (width - slip).coerceAtLeast(0.dp)), y = -Guga.rem(3.4f)).width(slip).height(Guga.rem(3.6f))
                    .background(Color(0xFF171410)).border(1.dp, colors.ink).padding(horizontal = Guga.rem(1.2f)).testTag("player-hover-preview"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f)),
            ) {
                BasicText(Rules.clock(ms.toLong()), style = Guga.numeral(rsp(1.7f)), maxLines = 1)
                chapterAt(ms, introEndMs, creditsStartMs)?.let { BasicText(it, style = Guga.note(rsp(1.1f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            // 缩略图：小横条正上方，同一根中线（不出两边）
            thumbAt?.invoke(drag ?: ms)?.let { img ->
                val tw = Guga.rem(DesktopPlayerSpec.SEEK_THUMB_REM)
                val th = tw * img.height.toFloat() / img.width.coerceAtLeast(1)
                androidx.compose.foundation.Image(
                    img, "预览",
                    Modifier.align(Alignment.TopStart).offset(x = (width * h - tw / 2).coerceIn(0.dp, (width - tw).coerceAtLeast(0.dp)), y = -Guga.rem(3.4f) - th - Guga.rem(0.4f))
                        .wrapContentSize(Alignment.TopStart, unbounded = true).size(tw, th).border(1.dp, colors.ink).testTag("player-seek-thumb"),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
        }
    }
}

/**
 * 出错（电脑版式）：画面拿掉，换成一页说明——居中一栏（专色小标 / 宋体大标题 / 一句人话 / 出路），右边是她（看印花稿纸，G21）。
 * 出路来自上游的 `errorView.actions`；错误原文只进「复制错误信息」，不上版面。和手机的出错页同一套逻辑。
 */
@Composable
private fun DesktopPlayerError(play: PlaySession, snap: PlayerSnapshot) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val view = snap.errorView
    // M2-14（D-lead-16）：整条回退链为空（找不到 libmpv）= 上游那句 NO_ENGINE_MESSAGE（电脑版式同手机）。
    val noEngine = snap.error == PlayController.NO_ENGINE_MESSAGE
    val controller = play.controller
    Box(Modifier.fillMaxSize().background(Color(0xFF14110D)).testTag("player-error")) {
        Row(Modifier.align(Alignment.Center).widthIn(max = Guga.rem(96f)).padding(horizontal = Guga.rem(6f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // P2-1：三端统一——标题「播放失败」，下面一行原因，按钮「重试」「返回」
                BasicText(com.gugaart.ui.player.PLAY_ERROR_TITLE, Modifier.padding(bottom = Guga.rem(1.6f)), style = Guga.song(rsp(4f), FontWeight.Black, lineHeight = rsp(5.2f), letterSpacing = 0.1.em), maxLines = 1)
                BasicText(com.gugaart.ui.player.playErrorReason(snap), Modifier.testTag("player-error-reason"), style = Guga.song(rsp(1.5f), color = colors.dim, lineHeight = rsp(2.8f)), maxLines = 2)
                Row(Modifier.padding(top = Guga.rem(2.8f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.4f)), verticalAlignment = Alignment.CenterVertically) {
                    for ((i, a) in com.gugaart.ui.player.playErrorActions(snap).withIndex()) {
                        val act: () -> Unit = {
                            when (a) {
                                ErrorActionId.Retry -> controller.retry()
                                ErrorActionId.SwitchVersion -> snap.versions.firstOrNull { it.id != snap.sourceId }?.id?.let { controller.setSource(it) }
                                ErrorActionId.SwitchCore -> controller.switchCore()
                                ErrorActionId.UseEmbedded -> controller.useEmbedded()
                                ErrorActionId.External -> controller.openExternalNow()
                                ErrorActionId.Exit -> play.exit()
                                ErrorActionId.UseWeb -> {}
                            }
                        }
                        DButton(a.label, act, icon = if (i == 0) GuguIcon.RotateCcw else null, small = true, line = i > 0, tag = "player-error-action")
                    }
                    g.deps.copyText?.let { copy -> snap.error?.let { raw -> TextLink("复制错误信息", { copy(raw) }, color = colors.dim) } }
                }
            }
            Column(Modifier.width(Guga.rem(26f)), horizontalAlignment = Alignment.CenterHorizontally) {
                MascotPose("G21", Guga.rem(16f), label = "看印花稿纸")
                if (LocalGugaPrefs.current.mascotSpeaks) {
                    MascotLines.pick("player.error", hour = LocalHour.current, seed = snap.itemId.hashCode())?.let {
                        BasicText(it, Modifier.padding(top = Guga.rem(1.2f)), style = Guga.song(rsp(1.4f), color = colors.dim).copy(fontStyle = FontStyle.Italic, textAlign = TextAlign.Center), maxLines = 2)
                    }
                }
            }
        }
        val errHost = LocalDesktopHost.current
        val errMacInset = errHost.windowKeys == WindowKeysStyle.SystemLeft
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().height(Guga.rem(DesktopPlayerSpec.TOP)).padding(start = if (errMacInset) maxOf(Guga.rem(2f), errHost.leadingInsetPx.dp) else Guga.rem(2f)), verticalAlignment = Alignment.CenterVertically) {
            EdgeButton(GuguIcon.ChevronLeft, "返回", play.exit, tag = "player-back")
            Spacer(Modifier.weight(1f))
            if (!errMacInset) Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.TopEnd) { WindowKeys() }
        }
    }
}
