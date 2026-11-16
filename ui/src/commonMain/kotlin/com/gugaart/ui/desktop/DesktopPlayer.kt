package com.gugaart.ui.desktop

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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.platform.testTag
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
import com.gugaart.ui.player.specOf
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.specLine
import com.gugaart.ui.theme.Guga
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
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.player.VeilLayer
import com.gugugaga.app.player.VideoSurface
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
    val controller = play.controller
    val colors = Guga.colors
    val preset = LocalPreset.current
    val scope = rememberCoroutineScope()
    val snap by controller.snapshot.collectAsState()
    val clock by controller.clock.collectAsState()
    val engine by controller.engineFlow.collectAsState()
    val settings by danmaku.settingsState.collectAsState()
    val appSettings by g.settings.state.collectAsState()
    val prefs by play.prefs.state.collectAsState()
    val render = remember(settings) { danmaku.prefsNow().render }
    val controls = remember { ControlsVisibility(scope, 3_000) }
    val window = g.deps.windowControls
    var opened by remember { mutableStateOf(PlayerPanel.of(preset.playerPanel)) }
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
    val position = clock.positionMs
    val duration = clock.durationMs.takeIf { it > 0 } ?: snap.resolved?.durationMs?.toDouble() ?: 0.0
    // 网速：mpv 自己报的下载速度，一直在取，控件一出来就有数（NetSpeed.kt）
    val speed = com.gugaart.ui.player.rememberNetSpeed(play.controller)
    val show = controls.visible || panel != null

    // ---- 键位：上游 PlayerKeys 解析，命令接到控制器的公开方法
    val snapNow by rememberUpdatedState(snap)
    val episodesNow by rememberUpdatedState(com.gugaart.ui.player.LocalPlayerEpisodes.current)
    val prefsNow by rememberUpdatedState(prefs)
    val durationNow by rememberUpdatedState(duration)
    val stepNow by rememberUpdatedState(maxOf(1.0, PlaybackSettings.of(appSettings).seekStepSec) * 1000)
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
    val handleKey: (KeyEvent) -> Boolean = remember(run) {
        { e ->
            val st = KeyState(UiMode.Desktop, controls.visible, snapNow.skip != null && snapNow.phase == PlayPhase.Ready, KeyTarget.Other)
            val like = if (e.type == KeyEventType.KeyDown && openedNow == null && !e.isCtrlPressed && !e.isAltPressed && !e.isMetaPressed) PlayerKeys.eventLike(e) else null
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
        onDispose { detach() }
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
    val pt by animateDpAsState(if (show && insetMode) topH else 0.dp, spec, label = "pt")
    val pb by animateDpAsState(if (show && insetMode) bottomH else 0.dp, spec, label = "pb")
    val px by animateDpAsState(if (show && insetMode) edgeW else 0.dp, spec, label = "px")
    val side by animateDpAsState(if (panel != null && insetMode) sideW else 0.dp, spec, label = "side")
    val bg = DesktopPlayerSpec.MARGIN
    val chromeBg = if (insetMode) bg else bg.copy(alpha = 0.9f)

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { handleKey(it) }
            .onKeyEvent { handleBack(it) }
            .focusRequester(focus)
            .focusable()
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
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = px, top = pt, end = px + side, bottom = pb)) {
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
            Box(Modifier.align(Alignment.TopCenter).padding(top = gapTop).size(fw, fh).clipToBounds().then(if (show) Modifier.border(1.dp, colors.ink.copy(alpha = 0.14f)) else Modifier).testTag("player-frame")) {
                Picture(play, snap, engine, slots, render.enabled, duration, showBuffer, onClick = {
                    controller.userGesture()
                    controller.togglePlay()
                    controls.bump()
                }, onDoubleClick = { window?.let { it.setFullscreen(!it.fullscreen) } })
            }
            // ---- 弹幕：从画面上方那条黑边的顶上开始排（宽银幕的黑边、开面板时画面让出来的空），黑边不够再接着压到画面顶上；
            //      所以它不在画面那一格里，而是一块「黑边 + 画面」的格子（上游的弹幕层从格子顶往下排 area 那么高）。不接鼠标。
            (com.gugaart.ui.player.LocalDanmakuSlotOverride.current ?: slots.danmaku)?.let { dm ->
                val holding = rememberUpdatedState(snap.holding)
                val dur = rememberUpdatedState(duration)
                Box(Modifier.align(Alignment.TopCenter).size(fw, gapTop + fh).clipToBounds().testTag("player-danmaku")) {
                    dm(DanmakuHost(snap.itemId, snap.item, controller.presentationClock, holding, render.enabled, dur, UiMode.Desktop), Modifier.matchParentSize())
                }
            }
        }

        if (show) {
            // ---- 顶栏（页边里）：返回｜logo + 集名 + 规格｜她的一格（暂停才有）｜印章位 + 收藏｜置顶｜窗口键
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth().height(topH).background(chromeBg).padding(start = Guga.rem(2f)).testTag("player-top"), verticalAlignment = Alignment.CenterVertically) {
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
                // 置顶：有窗口才画（不能用的控件不画）
                if (window != null) {
                    Spacer(Modifier.width(Guga.rem(1.6f)))
                    EdgeButton(if (window.alwaysOnTop) GuguIcon.PinOff else GuguIcon.Pin, if (window.alwaysOnTop) "取消置顶" else "窗口置顶", { window.setAlwaysOnTop(!window.alwaysOnTop) }, tag = "player-pin")
                }
                Spacer(Modifier.width(Guga.rem(1.6f)))
                if (window?.fullscreen != true) Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.TopEnd) { WindowKeys() }
            }
            // ---- 左缘图注、右缘音量（页边里）
            if (px > 0.dp && !narrow) {
                Box(Modifier.align(Alignment.TopStart).padding(top = pt, bottom = pb).width(px).fillMaxHeight().background(chromeBg), contentAlignment = Alignment.Center) {
                    val caption = listOfNotNull(snap.item?.parentIndexNumber?.let { "第 $it 季" }, snap.item?.indexNumber?.takeIf { snap.item?.seriesName != null }?.let { "第 $it 集" }, snap.item?.name?.let { "《$it》" } ?: snap.title).joinToString(" · ")
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        for (cell in verticalCells(caption).take(26)) BasicText(cell, style = Guga.song(rsp(1.15f), color = colors.faint, lineHeight = rsp(1.75f)), maxLines = 1)
                    }
                }
                Box(Modifier.align(Alignment.TopEnd).padding(top = pt, bottom = pb, end = side).width(px).fillMaxHeight().background(chromeBg), contentAlignment = Alignment.Center) {
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
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(bottomH).background(chromeBg).padding(horizontal = edgeW).padding(bottom = Guga.rem(1f)).testTag("player-dock"), verticalArrangement = Arrangement.Bottom) {
                Row(Modifier.fillMaxWidth().height(Guga.rem(5.2f)), verticalAlignment = Alignment.CenterVertically) {
                    // 左：时间
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(Rules.clock(position.toLong()), style = Guga.numeral(Guga.player.clock), maxLines = 1, modifier = Modifier.testTag("player-time"))
                        BasicText(" / ${Rules.clock(duration.toLong())}", style = Guga.numeral(Guga.player.clock, colors.dim), maxLines = 1)
                        // 网速（用户 2026-11-17：「调出菜单，能够自动显示当前的网络速度」）：和时间一样是一直在变的数，排在时间后面
                        speed?.let { BasicText("网速 $it", Modifier.padding(start = Guga.rem(1.6f)).testTag("player-speed"), style = Guga.note(Guga.player.hint, colors.ink, letterSpacing = 0.04.em), maxLines = 1) }
                        if (!narrow && win.width.value >= 1280f) {
                            BasicText(
                                "空格 暂停 · ← → ${(stepNow / 1000).toInt()} 秒 · ↑ ↓ 音量",
                                Modifier.padding(start = Guga.rem(1.6f)).border(1.dp, colors.line).padding(horizontal = Guga.rem(0.8f), vertical = Guga.rem(0.2f)),
                                style = Guga.note(Guga.player.hint, colors.dim, letterSpacing = 0.04.em),
                                maxLines = 1,
                            )
                        }
                    }
                    // 中：上一集 / 退 10 / 播放 / 进 10 / 下一集
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.6f))) {
                        DockKey(GuguIcon.SkipBack, "上一集", enabled = snap.prev != null) { controller.playPrev(); controls.bump() }
                        DockKey(GuguIcon.Replay10, "退 10 秒") { controller.seekBy(-10_000.0); controls.bump() }
                        DockKey(if (snap.paused) GuguIcon.Play else GuguIcon.Pause, if (snap.paused) "播放" else "暂停", big = true, tag = "player-play") { controller.userGesture(); controller.togglePlay(); controls.bump() }
                        DockKey(GuguIcon.Forward10, "进 10 秒") { controller.seekBy(10_000.0); controls.bump() }
                        DockKey(GuguIcon.SkipForward, "下一集", enabled = snap.next != null) { controller.playNext(); controls.bump() }
                    }
                    // 右：功能词（窄窗收成图标）+ 全屏
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                        for (p in PlayerPanel.WORDS) {
                            if (p == PlayerPanel.Eps && com.gugaart.ui.player.LocalPlayerEpisodes.current == null && snap.episodes.isEmpty()) continue
                            val value = when (p) {
                                PlayerPanel.Eps -> snap.item?.indexNumber?.let { n -> com.gugaart.ui.player.LocalPlayerEpisodes.current?.season?.number?.let { s -> "第 $s 季 · 第 $n 集" } ?: "第 $n 集" }
                                PlayerPanel.Sub -> snap.subtitles.firstOrNull { it.selected }?.label?.takeIf { snap.subtitleIndex != null } ?: "关"
                                PlayerPanel.Audio -> snap.audio.firstOrNull { it.selected }?.label
                                PlayerPanel.Speed -> rateLabel(snap.rate)
                                PlayerPanel.Quality -> if (snap.maxBitrateMbps > 0) "${snap.maxBitrateMbps.toInt()} Mbps" else specLine(snap.resolved?.mediaSource)?.substringBefore(" · ") ?: "原画"
                                PlayerPanel.Core -> com.gugaart.ui.player.engineShort(snap.engineId)
                                PlayerPanel.Dm -> if (render.enabled) "开" else "关"
                                PlayerPanel.Next, PlayerPanel.Cast -> null
                            }
                            DockWord(p, value?.take(8), panel == p, compact = win.width.value < 1800f, iconOnly = win.width.value < 1280f, warn = p == PlayerPanel.Dm && slots.danmakuWarns?.invoke() == true) {
                                opened = if (opened == p) null else p
                                controls.bump()
                            }
                        }
                        if (window != null) DockKey(if (window.fullscreen) GuguIcon.Minimize else GuguIcon.Maximize, if (window.fullscreen) "退出全屏" else "全屏", tag = "player-full") { window.setFullscreen(!window.fullscreen) }
                    }
                }
                DesktopTimeline(
                    position, duration, heat, introEnd, creditsStart,
                    sampleHover = if (preset.state == "hover") DesktopPlayerSpec.HOVER_SAMPLE else null,
                    onScrubStart = { controller.setScrubbing(true); controls.bump() },
                    onScrub = { controller.scrubTo(it) },
                    onScrubEnd = { controller.seekTo(it); controller.setScrubbing(false); controls.bump() },
                    onSeek = { controller.seekTo(it); controls.bump() },
                )
            }
        }
        // ---- 右侧页边：面板（内容复用手机的 PanelBody）；片尾的「下一集」也排在这里
        if (panel != null) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = if (insetMode) pt else topH, bottom = if (insetMode) pb else bottomH).width(sideW).fillMaxHeight().background(chromeBg)
                    .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                    .padding(horizontal = Guga.rem(if (narrow) 1.4f else 2.4f)).testTag("player-side"),
            ) {
                Box(Modifier.weight(1f)) {
                    Column {
                        PanelBody(panel, play, snap, danmaku, slots, art, position, close = {
                            if (panel == PlayerPanel.Next) dismissedNext = true
                            opened = null
                        })
                    }
                }
                val closePress = rememberPressState()
                BasicText(
                    if (panel == PlayerPanel.Next) "留在片尾  →" else "收起，回到全幅  →",
                    Modifier.padding(vertical = Guga.rem(1f)).deskFocusRing(closePress).pressable(closePress, label = "收起面板") {
                        if (panel == PlayerPanel.Next) {
                            dismissedNext = true
                            controller.cancelAutoNext()
                        }
                        opened = null
                    }.testTag("player-side-close"),
                    style = Guga.song(Guga.player.body, FontWeight.Bold, colors.dim),
                    maxLines = 1,
                )
            }
        }
        // 弹幕来源：从左边滑出来（有歧义时自己出来一次）
        DanmakuPickerHost(danmaku, snap.itemId, Guga.rem(48f))
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
private fun BoxScope.Picture(play: PlaySession, snap: PlayerSnapshot, engine: PlayerEngine?, slots: PlayerSlots, danmakuOn: Boolean, duration: Double, showBuffer: Boolean, onClick: () -> Unit, onDoubleClick: () -> Unit) {
    val controller = play.controller
    val colors = Guga.colors
    LocalPlayerStill.current?.let { still -> Canvas(Modifier.matchParentSize()) { drawCover(still, Framing(0.5f, 0.3f)) } }
    VideoSurface(engine, Modifier.matchParentSize()) { VeilLayer(controller.veil) }
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
    val press = rememberPressState()
    val hover = rememberHover()
    Box(
        Modifier.size(Guga.rem(4f)).hover(hover).pressGraphics(press).then(if (hover.value) Modifier.background(colors.ink) else Modifier).border(1.dp, colors.faint).deskFocusRing(press).pressable(press, label = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, if (selected) colors.seal else if (hover.value) colors.onInk else colors.ink, Guga.rem(2f), filled = selected) }
}

/** 控制坞中段的键（图标；悬停 = 反白）。[enabled] = false：没有上一集 / 下一集时变淡、点不动（位置留着，三段才对称）。 */
@Composable
private fun DockKey(icon: GuguIcon, label: String, big: Boolean = false, enabled: Boolean = true, tag: String = "player-key", onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val on = hover.value && enabled
    Box(
        Modifier.height(Guga.rem(4f)).width(Guga.rem(if (big) 5.2f else 4.4f)).hover(hover).pressGraphics(press).then(if (on) Modifier.background(colors.ink) else Modifier).deskFocusRing(press).pressable(press, enabled = enabled, label = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, if (on) colors.onInk else if (enabled) colors.ink else colors.faint, Guga.rem(if (big) 2.8f else 2f), filled = icon == GuguIcon.Play || icon == GuguIcon.Pause) }
}

/** 一个功能词：图标 + 词 + 斜体的当前值；打开着的 / 悬停 = 反白。[compact] 不写当前值；[iconOnly]（很窄）只剩图标。 */
@Composable
private fun DockWord(panel: PlayerPanel, value: String?, active: Boolean, compact: Boolean, iconOnly: Boolean, warn: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val on = active || hover.value
    val ink = if (on) colors.onInk else colors.ink
    Row(
        Modifier.height(Guga.rem(4f)).hover(hover).pressGraphics(press).then(if (on) Modifier.background(colors.ink) else Modifier)
            .deskFocusRing(press).pressable(press, label = panel.word + (value?.let { " $it" } ?: ""), onClick = onClick).padding(horizontal = Guga.rem(if (iconOnly) 0.8f else if (compact) 0.7f else 1.2f)).testTag(if (active) "word-on" else "word"),
        verticalAlignment = Alignment.CenterVertically,
        // 七个功能词（多了「内核」）：窄一点的窗口里字和图标挨近些，免得最后一个被挤掉
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(if (compact) 0.45f else 0.7f)),
    ) {
        Icon(WORD_ICONS.getValue(panel), ink, Guga.rem(2f))
        if (!iconOnly) BasicText(panel.word, style = Guga.song(Guga.player.dock, FontWeight.SemiBold, ink), maxLines = 1)
        if (!compact && value != null) BasicText(value, style = Guga.note(Guga.player.dockValue, if (on) colors.onInk else colors.dim), maxLines = 1)
        if (warn) Box(Modifier.size(Guga.rem(0.5f)).background(colors.seal, CircleShape))
    }
}

/** 顶栏里她的一格：**播放时是空的**（格子留着，版面不跳）；暂停才坐进来，旁边写停在哪。她关掉时只剩那行字。 */
@Composable
private fun DesktopPauseSeat(paused: Boolean, positionMs: Double) {
    val colors = Guga.colors
    Row(Modifier.height(Guga.rem(4.4f)).widthIn(min = Guga.rem(17f)).testTag(if (paused) "pause-seat" else "pause-seat-empty"), verticalAlignment = Alignment.CenterVertically) {
        if (!paused) return@Row
        if (LocalGugaPrefs.current.showMascot) Byline(Guga.rem(4.4f), live = false)
        Column(Modifier.padding(start = Guga.rem(1f))) {
            BasicText("PAUSED · 暂停", style = Guga.note(rsp(1.05f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            BasicText("停在 ${Rules.clock(positionMs.toLong())}", style = Guga.note(rsp(1.25f), colors.dim), maxLines = 1)
        }
    }
}

/**
 * 电脑的时间轴（4.4 rem 高）：一排弹幕热度圆点（播过的专色）、轨道、当前位置的小竖条、片头 / 片尾的刻度和小字。
 * 鼠标悬停：一条专色细线 + **预览小横条**（时间 + 这一刻在哪一段），落在控制坞里，不压画面。
 * 预览图：上游没有给界面层取截帧的公开入口，所以小横条里只排时间和章节，不放假图。点 = 跳过去；按住拖 = 拖动（松手才 seek）。
 */
@Composable
private fun DesktopTimeline(
    positionMs: Double,
    durationMs: Double,
    heat: FloatArray,
    introEndMs: Double?,
    creditsStartMs: Double?,
    sampleHover: Float?,
    onScrubStart: () -> Unit,
    onScrub: (Double) -> Unit,
    onScrubEnd: (Double) -> Unit,
    onSeek: (Double) -> Unit,
) {
    val colors = Guga.colors
    var drag by remember { mutableStateOf<Double?>(null) }
    var hoverX by remember { mutableStateOf(sampleHover) }
    val shown = drag ?: positionMs
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
                BasicText(chapterAt(ms, introEndMs, creditsStartMs) ?: "点一下跳到这里", style = Guga.note(rsp(1.1f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val controller = play.controller
    Box(Modifier.fillMaxSize().background(Color(0xFF14110D)).testTag("player-error")) {
        Row(Modifier.align(Alignment.Center).widthIn(max = Guga.rem(96f)).padding(horizontal = Guga.rem(6f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("PLAYBACK ERROR · 放映中断", style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                BasicText(view?.title ?: "这一集放不出来", Modifier.padding(top = Guga.rem(1f), bottom = Guga.rem(1.6f)), style = Guga.song(rsp(4f), FontWeight.Black, lineHeight = rsp(5.2f), letterSpacing = 0.1.em), maxLines = 2)
                BasicText(view?.hint ?: "换个版本，或者让服务器转一下，通常就好了。", style = Guga.song(rsp(1.5f), color = colors.dim, lineHeight = rsp(2.8f)), maxLines = 4)
                Row(Modifier.padding(top = Guga.rem(2.8f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.4f)), verticalAlignment = Alignment.CenterVertically) {
                    val actions = view?.actions ?: listOf(ErrorActionId.Retry, ErrorActionId.Exit)
                    for ((i, a) in actions.filter { it != ErrorActionId.UseWeb }.take(4).withIndex()) {
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
                        val label = if (a == ErrorActionId.Retry) "再试一次" else a.label
                        DButton(label, act, icon = if (i == 0) GuguIcon.RotateCcw else null, small = true, line = i > 0, tag = "player-error-action")
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
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().height(Guga.rem(DesktopPlayerSpec.TOP)).padding(start = Guga.rem(2f)), verticalAlignment = Alignment.CenterVertically) {
            EdgeButton(GuguIcon.ChevronLeft, "返回", play.exit, tag = "player-back")
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.TopEnd) { WindowKeys() }
        }
    }
}
