package com.gugaart.ui.player

import com.gugaart.ui.subtitle.SubtitleSearchHost
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.BoxButton
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.StateView
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.components.holdTouches
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.artOf
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.screens.BackRow
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.specLine
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.AutoNextState
import com.gugugaga.app.player.ControlsVisibility
import com.gugugaga.app.player.DanmakuHost
import com.gugugaga.app.player.ErrorActionId
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayPage
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.player.PlayerSlots
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.player.VeilLayer
import com.gugugaga.app.player.playerGestures
import com.gugugaga.app.settings.playbackPrefs
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.PlaybackMode
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.delay

/**
 * 测试缝：站在「画面」位置上的一张图。真的画面是原生层（不在 Compose 图层里），离屏测试里没有它，就用一张剧照顶着，
 * 才看得出控件压在画面上读不读得清、画面让位之后剩多大。应用里永远是 null。
 */
val LocalPlayerStill = staticCompositionLocalOf<ImageBitmap?> { null }

/** 画面让位的做法（ADR 0002 §5）。默认把画面缩到一边；某个引擎不行时由壳在这里换成 [ViewportMode.Overlay]。 */
val LocalViewportMode = staticCompositionLocalOf { ViewportMode.Inset }

/**
 * 测试缝：从外面推开 / 收起面板（值 = 面板的 id，null = 收起）。真窗口 / 设备上验证「画面让位」时，壳的调试探针用它在播放中换面板
 * （docs/player.md §9 的引擎实测）。应用里永远是 null。
 */
/** 测试缝：把这一页的手势回调交给测试（离屏渲染没有手指：测试直接调上游手势层会调的那几个回调）。应用里从不设置。 */
val LocalPlayerGesturesSink = staticCompositionLocalOf<((GugaGestures) -> Unit)?> { null }

/** 测试缝：直接给时间轴一份热度（离屏测试没有弹幕服务器；真实数据对照时把取到的热力图放进来）。应用里永远是 null。 */
val LocalHeatPreview = staticCompositionLocalOf<FloatArray?> { null }

val LocalPlayerPanelDriver = staticCompositionLocalOf<androidx.compose.runtime.State<String?>?> { null }

/**
 * `Route.Play` 的入口。逻辑一行不动（上游的 `PlayController` / 引擎回退链 / 弹幕），只换外观（docs/player.md）。
 * - 手机 / 电脑 / 电视各一套画报外观（`PlayerPhone`、`desktop/DesktopPlayer.kt`、`tv/TvPlayer.kt`），共用同一个 [PlaySession]。
 * - 这个平台还没有播放器（壳里 `player = null`，ADR 0002 §3）：一页说明，能退出去。
 */
@Composable
fun GugaPlayerScreen(route: Route.Play) {
    // 1.6 A1：路由里是别台的条目 → 播放器里的取数（剧的分集、「源」）也用那台的客户端（data/RemoteBrowse.kt）
    val ref = com.gugaart.ui.data.RemoteRef.parse(route.id)
    if (ref != null && com.gugaart.ui.data.LocalBrowseServer.current == null) {
        val pool = com.gugaart.ui.data.OtherServers.poolFor(LocalGraph.current)
        val server = remember(ref.serverId) { com.gugaart.ui.data.BrowseServer(ref.serverId, com.gugaart.ui.data.RemoteBrowse.nameOf(ref.serverId), pool.clientNow(ref.serverId)) }
        androidx.compose.runtime.CompositionLocalProvider(com.gugaart.ui.data.LocalBrowseServer provides server) { GugaPlayerScreen(route) }
        return
    }
    val g = LocalGraph.current
    val play = rememberPlaySession(route)
    if (play == null) {
        Box(Modifier.fillMaxSize().background(Guga.colors.paper)) {
            Column(Modifier.fillMaxSize().padding(top = Guga.dimens.statusBar + 50.dp)) {
                StateView("state-no-player", "摆稿纸", "这台设备上还放不了", "播放内核还没接进这一版。先回去看看别的。", action = "回上一页", onAction = { g.nav.backOrHome() })
            }
            BackRow("", solid = true, onBack = { g.nav.backOrHome() })
        }
        return
    }
    val danmaku = rememberGugaDanmaku(play)
    val slots = remember(danmaku) { danmaku.slots() }
    // 返回：下面不是详情页（从首页「继续观看」直接起播）→ 先到这部剧的详情页，再按一次才回首页。
    // 在三端的播放器之前登记 = 优先级最低：面板、跳过片头、电视的控件都先收返回。
    val back = com.gugugaga.ds.focus.LocalBackDispatcher.current
    androidx.compose.runtime.DisposableEffect(back, play) {
        val un = back.push {
            // 放着别台的源、路由是本台那一条：条目是那台的，按路由走；路由本身是别台的（1.6 A1）：回那台的详情
            val homeRoute = play.remote != null && ref == null
            val t = playerBackTarget(g.nav.routes(), if (homeRoute) null else play.controller.value.item, route.id)
                ?.let { Route.Item(com.gugaart.ui.data.RemoteBrowse.wrap(g.nav, it.id)) }
            if (t != null) g.nav.replace(t)
            t != null
        }
        onDispose { un() }
    }
    val snapForEps by play.controller.snapshot.collectAsState()
    val episodes = rememberPlayerEpisodes(snapForEps)
    // 字幕样式 → 引擎（三端一处）；弹幕用上游没有的字体（导入的 / 思源宋体 / 站酷快乐体）→ 上游的缝 LocalDanmakuFontOverride
    RememberSubtitleStyleApply(play)
    val danmakuFont = rememberDanmakuFontOverride()
    CompositionLocalProvider(LocalPlayerEpisodes provides episodes, com.gugugaga.danmaku.ui.LocalDanmakuFontOverride provides danmakuFont) {
        when (Guga.uiMode) {
            UiMode.Phone -> PlayerPhone(play, danmaku, slots)
            UiMode.Desktop -> com.gugaart.ui.desktop.DesktopPlayer(play, danmaku, slots)
            UiMode.Tv -> {
                // 1.6 B1：匹配片源帧率（只在电视上；切模式是壳的事，FrameRateMatch.host）
                RememberFrameRateMatch(play)
                com.gugaart.ui.tv.TvPlayer(play, danmaku, slots)
            }
        }
    }
}

/** 这一刻该显示哪个面板：用户打开的；没有的话，片尾进行中自动出「下一集」这一栏（用户收起过就不再出）。纯函数，有单测。 */
fun effectivePanel(opened: PlayerPanel?, autoNext: AutoNextState, hasNext: Boolean, dismissedNext: Boolean): PlayerPanel? = when {
    opened != null -> opened
    autoNext != AutoNextState.Idle && hasNext && !dismissedNext -> PlayerPanel.Next
    else -> null
}

/** 规格行：「4K · HDR10 · 国语 AAC 2.0 · 直连」——每一段都来自真实的媒体流 / 播放方式，没有的不写。 */
/** 顶部信息行里字幕那一小段（「字幕 简中 · 自动」）：现在开着哪条、怎么选来的。没开字幕也没有来由 = 不写。 */
fun subtitleBadgeOf(play: PlaySession, snap: PlayerSnapshot): String? {
    val why = play.subtitleWhy
    val track = snap.resolved?.mediaSource?.mediaStreams?.firstOrNull { it.type == "Subtitle" && it.index == snap.subtitleIndex }?.let { SubTrack(it.index, it.language, it.title ?: it.displayTitle) }
    if (why == null && track == null) return null
    return subtitleBadge(track, why)
}

/** 规格那一行 + 字幕是怎么选来的。 */
fun specOf(snap: PlayerSnapshot, play: PlaySession): String = listOfNotNull(specOf(snap).takeIf { it.isNotEmpty() }, subtitleBadgeOf(play, snap)).joinToString(" · ")

fun specOf(snap: PlayerSnapshot): String {
    val audio = audioWordValue(snap)
    val mode = when (snap.mode) {
        PlaybackMode.Direct -> "直连"
        null -> null
        else -> "转码"
    }
    return listOfNotNull(specLine(snap.resolved?.mediaSource), audio, mode, snap.engineId?.id).joinToString(" · ")
}

/**
 * 手机播放器（横屏 905 × 412，设计稿 `p=player`）。
 *
 * 第一原则：**画面和字幕一个像素都不挡**。成块的内容（六个面板、片尾的下一集）排在页边里——画面缩到左边约 75 %，
 * 时间轴和功能词在画面下方；浮在画面上的只有贴边的小提示（跳过片头）和点一下就全部隐去的控件。
 * 暂停（decisions D1）：画面保持可见，只压暗 18 %，加上控件和她的小格；**播放中她的那一格是空的**。
 */
@Composable
fun PlayerPhone(play: PlaySession, danmaku: PlayerDanmaku, slots: PlayerSlots) {
    val g = LocalGraph.current
    // P2-9：能选的内核不到两个（iOS 只有 MPV）时不显示「内核」
    val coreVisible = remember(play) { coreWordVisible(coreOptionsFor(play, g)) }
    val controller = play.controller
    val colors = Guga.colors
    val preset = LocalPreset.current
    val scope = rememberCoroutineScope()
    val snap by controller.snapshot.collectAsState()
    // 时钟（每 250 ms 走一下）不在这一层读（1.5.1 性能）：以前这里一读，在播的每一下整个播放页（画面层、弹幕宿主、控件）重组一遍；
    // 现在只有时间轴那一格、暂停那一格、真用到位置的点击里才读
    val clock = controller.clock.collectAsState()
    val clockDuration = remember(clock) { derivedStateOf { clock.value.durationMs } }
    val engine by controller.engineFlow.collectAsState()
    RememberShaderApply(play) // 画质面板记住的着色器选择：引擎 / 选择一变就应用（ShaderApply.kt）
    RememberSecondSubtitleApply(play) // 双语字幕（1.6 C1）：第二字幕按片记住、交给 mpv
    val settings by danmaku.settingsState.collectAsState()
    val appSettings by g.settings.state.collectAsState()
    val seekStepSec = remember(appSettings) { appSettings.playbackPrefs.seekStepSec.coerceAtLeast(1) }
    val stepNow = rememberUpdatedState(seekStepSec * 1_000.0)
    val danmakuConfigured = com.gugaart.ui.danmaku.rememberDanmakuConfigured()
    val danmakuUnconfigured = (danmaku.state.phase == com.gugugaga.danmaku.match.DanmakuPhase.Unconfigured || !danmakuConfigured) &&
        LocalDanmakuSlotOverride.current == null &&
        DanmakuPickerBoard.stateOverride == null
    val render = remember(settings) { danmaku.prefsNow().render }
    val controls = remember { ControlsVisibility(scope, 3_000) }
    var opened by remember { mutableStateOf(PlayerPanel.of(preset.playerPanel)) }
    LocalPlayerPanelDriver.current?.let { driver -> LaunchedEffect(driver.value) { opened = PlayerPanel.of(driver.value) } }
    var dismissedNext by remember(snap.itemId) { mutableStateOf(false) }
    var locked by remember { mutableStateOf(preset.playerLocked) }
    val playing = snap.phase == PlayPhase.Ready && !snap.paused
    val panel = effectivePanel(opened, snap.autoNext, snap.next != null, dismissedNext)
    // 宿主环境（和上游 PlayPage 同样的三步）：播放时屏幕常亮；这一页显示时手机藏系统栏并锁横屏，离开时还原
    val awake = playing || snap.phase == PlayPhase.Preparing || snap.phase == PlayPhase.Loading
    LaunchedEffect(awake) { play.platform.keepAwake(awake) }
    androidx.compose.runtime.DisposableEffect(play) {
        play.platform.playerVisible(true)
        onDispose {
            play.platform.playerVisible(false)
            play.platform.keepAwake(false)
        }
    }
    // AirPlay hand-off: the picture is on the TV, the top bar (back, AirPlay) stays
    val castingNow = com.gugaart.ui.cast.AirPlayVideo.host != null && com.gugaart.ui.cast.AirPlayVideo.castFor(controller) != null
    LaunchedEffect(playing, panel, castingNow) {
        controls.canHide = playing && panel == null && !preset.playerPin && !castingNow
        if (castingNow) controls.bump()
    }
    LaunchedEffect(preset.playerControls, snap.phase) { if (!preset.playerControls && snap.phase == PlayPhase.Ready) controls.hide() }
    // 缓冲超过 150 ms 才出现（不闪）
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
    val heat = LocalHeatPreview.current ?: remember(danmaku.heat) { heatDots(danmaku.heat?.model?.values) }
    val marks = remember(snap.markers) { listOfNotNull(snap.markers.intro?.endMs?.toDouble(), snap.markers.credits?.startMs?.toDouble()) }
    val position: () -> Double = { clock.value.positionMs }
    val duration = clockDuration.value.takeIf { it > 0 } ?: snap.resolved?.durationMs?.toDouble() ?: 0.0
    // 网速：一直在采样，控件一出来就有数（NetSpeed.kt）
    val speed = rememberNetSpeed(play.controller)
    val showControls = controls.visible && !locked
    // 横屏的安全区（M4-1）：控件、字幕区、弹幕、贴边提示让出左右 [side]（iOS 的岛那一侧 62），时间轴让出底下的 home 条；
    // 画面本身不让（铺满）。安卓：side = 0、stripBottom = 10，和原来一样。
    val side = Guga.dimens.sideInset
    val stripBottom = maxOf(10.dp, com.gugaart.ui.theme.LocalPhoneInsets.current.bottom - 8.dp)
    // 手势：上游的手势层 + 这一页的回调（整页一个实例；回调执行时才读当前状态）
    val lockedNow = rememberUpdatedState(locked)
    val durationNow = rememberUpdatedState(duration)
    val gestures = remember(controller) {
        GugaGestures(controller, play.platform, play.prefs, controls, locked = { lockedNow.value }, duration = { durationNow.value }, onSingleTap = {
            if (opened != null) opened = null else controls.toggle()
        }, stepMs = { stepNow.value })
    }
    val gesturesSink = LocalPlayerGesturesSink.current
    androidx.compose.runtime.SideEffect { gesturesSink?.invoke(gestures) }
    // AirPlay hand-off (iPhone, 1.6 F2): the page follows the system route; while handed off the picture area is the cast state with its own controls, the centre keys and the strip go
    if (com.gugaart.ui.cast.AirPlayVideo.host != null) LaunchedEffect(controller) {
        val t0 = kotlin.time.TimeSource.Monotonic.markNow()
        com.gugaart.ui.cast.AirPlayVideo.watch(controller) { t0.elapsedNow().inWholeMilliseconds }
    }
    val airCast = com.gugaart.ui.cast.AirPlayVideo.castFor(controller)
    LaunchedEffect(airCast != null) { if (airCast != null) opened = null }

    when (snap.phase) {
        PlayPhase.Error -> {
            ErrorPage(play, snap)
            return
        }
        PlayPhase.Ended -> if (snap.autoNext == AutoNextState.Idle) {
            EndedPage(play, snap, art)
            return
        }
        else -> {}
    }

    // 1.6 C3：拖进度条时的缩略图（Emby 的 BIF；第一次拖的时候才问服务器，没有就照旧只显示时间）
    val thumbs = rememberSeekThumbs()
    val strip: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Timeline(
                position, duration, heat, marks,
                onScrubStart = { thumbs.prepare(play, g); controller.setScrubbing(true); controls.bump() },
                onScrub = { controller.scrubTo(it) },
                onScrubEnd = { controller.seekTo(it); controller.setScrubbing(false); controls.bump() },
                thumbAt = thumbs::image,
            )
            // 窄的时候（iOS 横屏开着面板：画面一栏只有 530 dp 上下）七个功能词只写词、不写值，免得挤在一起；安卓、全幅照旧
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val showValues = maxWidth >= 600.dp
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for (p in PlayerPanel.WORDS) {
                    if (p == PlayerPanel.Eps && LocalPlayerEpisodes.current == null && snap.episodes.isEmpty()) continue
                    if (p == PlayerPanel.Core && !coreVisible) continue
                    val value = when (p) {
                        PlayerPanel.Eps -> snap.item?.indexNumber?.let { "第 $it 集" }
                        PlayerPanel.Sub -> subtitleWordValue(snap)
                        PlayerPanel.Audio -> audioWordValue(snap)
                        PlayerPanel.Speed -> rateLabel(snap.rate)
                        PlayerPanel.Quality -> if (snap.maxBitrateMbps > 0) "${snap.maxBitrateMbps.toInt()} Mbps" else specLine(snap.resolved?.mediaSource)?.substringBefore(" · ") ?: "原画"
                        PlayerPanel.Core -> engineShort(snap.engineId)
                        PlayerPanel.Dm -> if (danmakuUnconfigured) "未配置" else if (render.enabled) "开" else "关"
                        PlayerPanel.Next, PlayerPanel.Cast -> null
                    }
                    FunctionWord(
                        p,
                        value.takeIf { showValues || (p == PlayerPanel.Dm && danmakuUnconfigured) },
                        panel == p,
                        warn = p == PlayerPanel.Dm && (danmakuUnconfigured || slots.danmakuWarns?.invoke() == true),
                        onClick = {
                            if (p == PlayerPanel.Dm && danmakuUnconfigured) {
                                g.nav.navigate(Route.Settings("danmaku"))
                            } else {
                                opened = if (opened == p) null else p
                                controls.bump()
                            }
                        },
                    )
                }
            }
            }
        }
    }

    val picture: @Composable BoxScope.(Boolean) -> Unit = { inset ->
        // 第 0 层：画面（原生层；这里只占位并把矩形报给引擎）。测试里用一张剧照顶着
        LocalPlayerStill.current?.let { still -> Canvas(Modifier.matchParentSize()) { drawCover(still, Framing(0.5f, 0.3f)) } }
        com.gugaart.ui.player.GugaVideoSurface(engine, Modifier.matchParentSize()) { VeilLayer(controller.veil) }
        // 换片的那一段（用户 2026-11-15：「切换到其他影片的时候在加载的时候会卡成其他影片的那一画面」）：引擎和画面层是复用的，
        // 新片子第一帧到之前，屏幕上留着的是上一部的最后一帧（换集时上游还特意用截图挡着）。这一部还没出过画面时，用它自己的剧照盖住。
        NewItemCover(snap.itemId, snap.phase, art)
        // 第 2 层：弹幕（上游的图层）。用户 2026-11-15：「全屏的时候需要适配的最顶部」「菜单打开的时候弹幕可以不挡上面」——
        // 全屏（控件隐去）时从画面最顶上开始走；顶栏出现时让到顶栏下沿以下（滑过去，不跳）；画面让位时跟着画面矩形，从它顶上开始。
        (LocalDanmakuSlotOverride.current ?: slots.danmaku)?.let { dm ->
            val holding = rememberUpdatedState(snap.holding)
            val dur = rememberUpdatedState(duration)
            val top by androidx.compose.animation.core.animateDpAsState(phoneDanmakuTop(inset, showControls), androidx.compose.animation.core.tween(220), label = "dm-top")
            dm(DanmakuHost(snap.itemId, snap.item, controller.presentationClock, holding, render.enabled, dur, UiMode.Phone), Modifier.matchParentSize().padding(top = top).padding(horizontal = side))
        }
        // 暂停只轻微压暗；缓冲压暗 40 % + 一条细线和速度
        val dim = when {
            showBuffer -> PlayerSpec.BUFFER_DIM
            snap.paused && snap.phase == PlayPhase.Ready -> PlayerSpec.PAUSE_DIM
            else -> 0f
        }
        Box(
            Modifier.matchParentSize().drawBehind { if (dim > 0f) drawRect(colors.paper.copy(alpha = dim)) }.playerGestures(gestures, touchGestures = true).testTag(if (snap.paused) "player-picture-paused" else "player-picture"),
        )
        // 画中画期间（iOS M5-10，D-lead-24）：画面那一格是纸面说明，时间轴和功能词留着；点「回到这里」= 收回小窗
        val pip = play.platform.pictureInPicture.takeIf { com.gugaart.ui.theme.LocalPhoneHost.current.pipInPage }
        if (pip != null && pip.active) PhonePipNote(onBack = { pip.toggle() }, Modifier.matchParentSize())
        // AirPlay hand-off: the picture area shows the cast state - play / pause, seek, stop (its own controls, they do not hide)
        airCast?.let { cast ->
            PhoneAirPlayNote(cast, play, clock, duration, stepMs = stepNow.value, onStop = { com.gugaart.ui.cast.AirPlayVideo.stop(controller) }, Modifier.matchParentSize())
        }
        if (showBuffer && airCast == null) {
            // 缓冲：正中一小块墨底——细线、「缓冲中」、一行小字（引擎回退 / 换线路的通知排在这里，不弹窗）
            Column(Modifier.align(Alignment.Center).background(colors.inkWell).padding(horizontal = 22.dp, vertical = 12.dp).testTag("player-buffer"), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(96.dp).height(1.dp).background(colors.ink))
                BasicText("缓冲中", Modifier.padding(top = 9.dp), style = Guga.song(14.sp, FontWeight.Bold, letterSpacing = 0.3.em), maxLines = 1)
                val line = play.notice?.title ?: snap.switching?.why ?: snap.stepLabel.takeIf { it.isNotEmpty() }
                if (line != null) BasicText(line, Modifier.padding(top = 5.dp), style = Guga.note(10.5.sp, colors.dim), maxLines = 1)
            }
        }
        // 贴边提示：亮度 / 音量 / 快进量 / 长按倍速
        Box(Modifier.matchParentSize().padding(horizontal = side)) { EdgeHints(gestures) }
        // 开播那几秒：片源的效果（杜比视界 / HDR10 / 杜比全景声…）
        FormatFlash(snap, 1f, Modifier.align(Alignment.TopStart).padding(start = 16.dp + side, top = 64.dp))
        // 字幕智能匹配的那句提示（「找到精确匹配的字幕 · 用上」/「已自动加载：… · 撤销」）：排在徽章那一行下面（开播缓冲久了两样可能同时在，不叠）（subtitle/BestMatchUi.kt）
        // 下面一格：「换到别台的源没成功」那一句（亮 4 秒，player/NoticeFlash.kt）；两样同时在时上下排开，不叠
        Column(Modifier.align(Alignment.TopStart).padding(start = 16.dp + if (inset) 0.dp else side, top = 64.dp + 40.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!locked) com.gugaart.ui.subtitle.AutoBestFlash(play, controls = showControls || inset, scale = 1f, maxWidth = 380.dp)
            PlayerNoticeFlash(play, 1f, maxWidth = 380.dp)
        }
        if (locked) {
            // 锁屏：只留左缘一个锁（点它解开）；别的手势都不响应
            Row(Modifier.align(Alignment.BottomStart).padding(start = 12.dp + side, bottom = 96.dp).background(colors.inkWell).testTag("player-lock"), verticalAlignment = Alignment.CenterVertically) {
                BoxButton(GuguIcon.Lock, "解锁", { locked = false; controls.bump() }, onImage = true, selected = true)
                BasicText("已锁定", Modifier.padding(start = 4.dp, end = 14.dp), style = Guga.song(11.sp, color = colors.ink, letterSpacing = 0.1.em), maxLines = 1)
            }
        } else if (showControls || inset) {
            // 两个上角的方框按钮：返回永远在左上；收藏必须在
            BoxButton(GuguIcon.ChevronLeft, "返回", play.exit, Modifier.align(Alignment.TopStart).padding(start = 8.dp + if (inset) 0.dp else side, top = 8.dp, end = 8.dp, bottom = 8.dp), onImage = true)
            Row(Modifier.align(Alignment.TopEnd).padding(start = 8.dp, top = 8.dp, end = 8.dp + if (inset) 0.dp else side, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                // AirPlay（iOS，D-lead-35：只换声音的输出设备，系统的路由选择器）：宿主给了才画；设计稿 ios notes §1 第 11 条的顺序
                // [AirPlay][她的一格][收藏][画中画][锁]
                com.gugaart.ui.theme.LocalPhoneHost.current.airPlay?.let { air -> BoxButton(GuguIcon.Airplay, "AirPlay", air, Modifier.testTag("player-airplay"), onImage = true) }
                // not while handed off: no mascot over a picture that is on the TV (design/ios/notes.md §1 item 11)
                if (!inset && airCast == null) PauseSeat(paused = snap.paused && snap.phase == PlayPhase.Ready, positionMs = position)
                // 投屏（DLNA）：手机能投才有
                if (com.gugaart.ui.cast.Cast.host != null) BoxButton(GuguIcon.Tv, "投屏", { opened = if (opened == PlayerPanel.Cast) null else PlayerPanel.Cast }, onImage = true, selected = com.gugaart.ui.cast.Cast.active != null)
                BoxButton(GuguIcon.Heart, if (snap.favorite == true) "取消收藏" else "收藏", { controller.toggleFavorite() }, onImage = true, selected = snap.favorite == true)
                // 画中画（iOS M5-10）：宿主要页内的钮（PhoneHost.pipInPage）、平台也有这个能力才画（上游规则：不能用的钮不如没有）
                // not while handed off (the picture is on the TV, mpv is paused here)
                play.platform.pictureInPicture?.takeIf { com.gugaart.ui.theme.LocalPhoneHost.current.pipInPage && airCast == null }?.let { pip -> PipBoxButton(pip.active, { pip.toggle(); controls.bump() }) }
                BoxButton(GuguIcon.Lock, "锁屏", { locked = true; opened = null }, onImage = true)
            }
        }
        // 贴边提示：跳过片头 / 片尾，右下、时间轴上方，带一小块墨底
        snap.skip?.let { skip ->
            if (!locked) {
                val press = rememberPressState()
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp + if (inset) 0.dp else side, bottom = if (inset) 12.dp else PlayerSpec.STRIP + 10.dp).pressGraphics(press).background(colors.inkWell)
                        .pressable(press, label = skip.label) { controller.skip() }.padding(horizontal = 14.dp, vertical = 9.dp).testTag("player-skip"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(skip.label, style = Guga.song(13.sp, FontWeight.Bold), maxLines = 1)
                    Icon(GuguIcon.ChevronRight, colors.ink, 12.dp)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().testTag("player-phone")) {
        if (panel != null && LocalViewportMode.current == ViewportMode.Inset) {
            // ---- 画面让位：画面缩到左边，面板排在右侧页边，时间轴和功能词在画面下方。页边是纸色；画面那一块不画底（下面是原生视频层）
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(PlayerSpec.INSET_TOP).background(colors.paper))
                Row(Modifier.weight(1f)) {
                    Box(Modifier.width(PlayerSpec.INSET + side).fillMaxHeight().background(colors.paper))
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) { picture(true) }
                        strip(Modifier.fillMaxWidth().height(PlayerSpec.STRIP + stripBottom - 10.dp).background(colors.paper).padding(top = 4.dp, bottom = stripBottom - 10.dp))
                    }
                    Column(Modifier.width(PlayerSpec.MARGIN + side).fillMaxHeight().background(colors.paper).padding(start = 18.dp, end = 20.dp + side, bottom = stripBottom).testTag("player-margin")) {
                        Box(Modifier.weight(1f)) {
                            PanelBody(panel, play, snap, danmaku, slots, art, position, close = {
                                if (panel == PlayerPanel.Next) dismissedNext = true
                                opened = null
                            })
                        }
                        // 片尾的「下一集」面板自己有「取消」（P1-3），底下不再重复
                        if (panel != PlayerPanel.Next) TextLink("收起  →", { opened = null }, color = colors.dim)
                    }
                }
            }
        } else {
            // ---- 全幅：画面铺满；控件压在画面外沿的暗角上，点一下画面全部隐去
            Box(Modifier.fillMaxSize()) { picture(false) }
            if (showControls) {
                // 顶部和底部的暗角（只在控件显示时）
                Canvas(Modifier.fillMaxSize()) {
                    val top = 96.dp.toPx()
                    drawRect(Brush.verticalGradient(listOf(colors.paper.copy(alpha = 0.72f), Color.Transparent), 0f, top), size = Size(size.width, top))
                    val bottom = 150.dp.toPx()
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.paper.copy(alpha = 0.86f)), size.height - bottom, size.height), Offset(0f, size.height - bottom), Size(size.width, bottom))
                }
                // 顶栏：返回（在 picture 里）｜片名 logo + 集名 + 规格
                Row(Modifier.align(Alignment.TopStart).padding(start = 66.dp + side, top = 8.dp, end = 200.dp + side).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 片名 logo：剧集一定有（没有 logo 图就是宋体剧名）；电影只有真有 logo 图时才放——否则和右边那行片名是同一行字写两遍
                    val showLogo = snap.item?.seriesName != null || (art.logo != null && !preset.textTitles)
                    if (showLogo) TitleLogo(snap.item?.seriesName ?: snap.title, art.logo, 78.dp, 40.dp, textOnly = preset.textTitles, baseSize = 20.sp)
                    Column(Modifier.padding(start = if (showLogo) 14.dp else 0.dp)) {
                        val ep = snap.item?.indexNumber?.let { "第 $it 集　" }.orEmpty()
                        BasicText(ep + (snap.item?.name ?: snap.title), style = Guga.song(15.sp, FontWeight.Bold, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // 规格行 + 网速（用户 2026-11-17：「调出菜单，能够自动显示当前的网络速度」）：规格太长时被省略号截，网速固定在后面不截
                        // not while handed off (that is this device's stream; the TV plays another one)
                        if (airCast == null) Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            specOf(snap, play).takeIf { it.isNotEmpty() }?.let { BasicText(it, Modifier.weight(1f, fill = false), style = Guga.note(10.5.sp, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            NetSpeedText(speed, Modifier.padding(start = 10.dp).testTag("player-speed"), Guga.note(10.5.sp, colors.ink))
                        }
                    }
                }
                // 中间三个键：退 10 秒 / 播放暂停 / 进 10 秒；剧集两侧再加上一集 / 下一集（P2-10，没有的那一边是灰的）
                // 缓冲时不排（正中是「缓冲中」那一小块）
                val episodic = snap.item?.seriesName != null || snap.prev != null || snap.next != null
                if (!showBuffer && airCast == null) Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (episodic) 40.dp else 54.dp)) {
                    if (episodic) CenterKey(GuguIcon.SkipBack, "上一集", 22.dp, enabled = snap.prev != null) { controller.playPrev(); controls.bump() }
                    CenterKeyContent("退 $seekStepSec 秒", { controller.seekBy(-stepNow.value); controls.bump() }) {
                        SeekStepIcon(false, seekStepSec, colors.ink, 26.dp)
                    }
                    CenterKey(if (snap.paused) GuguIcon.Play else GuguIcon.Pause, if (snap.paused) "播放" else "暂停", 34.dp) { controller.userGesture(); controller.togglePlay(); controls.bump() }
                    CenterKeyContent("进 $seekStepSec 秒", { controller.seekBy(stepNow.value); controls.bump() }) {
                        SeekStepIcon(true, seekStepSec, colors.ink, 26.dp)
                    }
                    if (episodic) CenterKey(GuguIcon.SkipForward, "下一集", 22.dp, enabled = snap.next != null) { controller.playNext(); controls.bump() }
                }
                if (airCast == null) strip(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 28.dp + side).padding(bottom = stripBottom))
            }
            // 引擎不支持非全窗视口时的退路：贴右边的小面板（墨底），不挡字幕区（画面下方三分之一不放）
            if (panel != null) {
                Column(Modifier.align(Alignment.TopEnd).width(PlayerSpec.MARGIN + side).fillMaxHeight(0.66f).background(colors.paper.copy(alpha = 0.92f)).padding(start = 16.dp, end = 16.dp + side, top = 8.dp, bottom = 8.dp)
                    // 面板空白处的点不漏到下面的画面（1.5.1 subfix：不吃事件，只挡住下面的兄弟；点画面 = 收面板，在手势层的单击里）
                    .holdTouches().testTag("player-overlay-panel")) {
                    PanelBody(panel, play, snap, danmaku, slots, art, position, close = { opened = null })
                }
            }
        }
        // 弹幕来源：从左边滑出来（有歧义时自己出来一次）
        DanmakuPickerHost(danmaku, snap.itemId, 360.dp)
        // 字幕搜索：同一种做法，从左边出来（字幕面板「搜索字幕…」打开）
        SubtitleSearchHost(play, 360.dp)
    }
}

@Composable
private fun CenterKey(icon: GuguIcon, label: String, size: androidx.compose.ui.unit.Dp, enabled: Boolean = true, onClick: () -> Unit) {
    CenterKeyContent(label, onClick, enabled) {
        Icon(icon, if (enabled) Guga.colors.ink else Guga.colors.faint, size, filled = icon == GuguIcon.Play || icon == GuguIcon.Pause)
    }
}

@Composable
private fun CenterKeyContent(label: String, onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    val press = rememberPressState()
    Box(Modifier.size(56.dp).pressGraphics(press).pressable(press, enabled = enabled, label = label, onClick = onClick).testTag("center-$label"), contentAlignment = Alignment.Center) {
        content()
    }
}

/**
 * 顶栏里她的一格（收藏键左边）。**播放时是空的**；暂停才坐进来，旁边写「暂停 · 停在 26:30」。
 * 她关掉（设置）时只剩那行字。
 */
@Composable
fun PauseSeat(paused: Boolean, positionMs: () -> Double) {
    if (!paused) {
        Spacer(Modifier.size(1.dp).testTag("pause-seat-empty"))
        return
    }
    val colors = Guga.colors
    Row(Modifier.padding(end = 10.dp).background(colors.inkWell).padding(horizontal = 8.dp, vertical = 4.dp).testTag("pause-seat"), verticalAlignment = Alignment.CenterVertically) {
        if (LocalGugaPrefs.current.showMascot) Byline(34.dp, live = false)
        BasicText("暂停 · 停在 ${Rules.clock(positionMs().toLong())}", Modifier.padding(start = 8.dp), style = Guga.song(12.sp, color = colors.ink), maxLines = 1)
    }
}

/**
 * 看完一集（没有下一集，或者用户留在了片尾）：这部剧的剧照**整幅铺满**横屏、压暗；左：「本集完」+ 片名 + 她的一句话；
 * 右：有下一集 → 下一集的剧照和播放键，没有 → 回详情。这是「画面已经停了」的状态，可以有她。
 */
@Composable
internal fun EndedPage(play: PlaySession, snap: PlayerSnapshot, art: Art) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val hour = LocalHour.current
    val client = g.session.session.collectAsState().value?.client
    val next = snap.next
    Box(Modifier.fillMaxSize().background(colors.paper).testTag("player-ended")) {
        FullBleed(Art(backdrop = art.backdrop, poster = art.poster), 412.dp, Modifier.fillMaxSize(), framing = Framing.of(snap.item?.seriesId ?: snap.itemId), dim = 0.46f)
        BoxButton(GuguIcon.ChevronLeft, "返回", play.exit, Modifier.align(Alignment.TopStart).padding(8.dp), onImage = true)
        Row(Modifier.fillMaxSize().padding(start = 72.dp, end = 40.dp, top = 56.dp, bottom = 36.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                BasicText("本集完 · THE END", style = Guga.kicker(), maxLines = 1)
                TitleLogo(snap.item?.seriesName ?: snap.title, art.logo, 220.dp, 76.dp, Modifier.padding(top = 8.dp))
                BasicText(listOfNotNull(snap.item?.indexNumber?.let { "第 $it 集" }, snap.item?.name).joinToString("　"), Modifier.padding(top = 8.dp), style = Guga.song(15.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (LocalGugaPrefs.current.showMascot) {
                    Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.gugaart.ui.mascot.MascotPose("G13", 64.dp, label = "鼓掌")
                        val line = MascotLines.pick(if (next == null) "player.ended.last" else "player.ended", LineFacts(ep = snap.item?.indexNumber, next = next?.indexNumber), hour, snap.itemId.hashCode())
                        if (line != null && LocalGugaPrefs.current.mascotSpeaks) {
                            BasicText(line, Modifier.padding(start = 12.dp), style = Guga.song(13.sp, color = colors.ink).copy(fontStyle = FontStyle.Italic), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Column(Modifier.width(230.dp)) {
                if (next != null) {
                    Thumb(client?.artOf(next)?.thumb, Rules.episodeNo(next.indexNumber ?: 0), 230.dp)
                    BasicText("第 ${next.indexNumber ?: ""} 集", Modifier.padding(top = 10.dp), style = Guga.note(14.sp, colors.spot), maxLines = 1)
                    BasicText(next.name, Modifier.padding(top = 2.dp, bottom = 12.dp), style = Guga.song(15.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PrimaryButton("播放下一集", { play.controller.playNext() }, Modifier.fillMaxWidth())
                } else {
                    PrimaryButton("回到详情", play.exit, Modifier.fillMaxWidth(), icon = null)
                }
            }
        }
    }
}

/**
 * 出错：**画面拿掉**，换成一页说明（宋体标题 + 一句人话）+ 出路。错误原文只进「复制错误信息」，不写在页面上，更不写进她的话。
 * 她：平静地看印花稿纸（decisions Q5；生图到之前是轮廓占位）。出路来自上游的 `ErrorView.actions`（它知道这次失败有哪些路可走）。
 */
@Composable
private fun ErrorPage(play: PlaySession, snap: PlayerSnapshot) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val view = snap.errorView
    // M2-14（D-lead-16）：整条回退链为空（找不到 libmpv）时，出错页就用上游那句 NO_ENGINE_MESSAGE。
    val noEngine = snap.error == PlayController.NO_ENGINE_MESSAGE
    val controller = play.controller
    Box(Modifier.fillMaxSize().background(colors.paper).testTag("player-error")) {
        Row(Modifier.fillMaxSize().padding(start = 72.dp, end = 48.dp, top = 40.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // P2-1：三端统一——标题「播放失败」，下面一行原因（找不到内核 = 上游那句），按钮「重试」「返回」
                BasicText(PLAY_ERROR_TITLE, style = Guga.song(30.sp, FontWeight.Black), maxLines = 1)
                BasicText(playErrorReason(snap), Modifier.padding(top = 10.dp).testTag("player-error-reason"), style = Guga.song(14.sp, color = colors.dim, lineHeight = 24.sp), maxLines = 2)
                Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    for ((i, a) in playErrorActions(snap).withIndex()) {
                        val run: () -> Unit = {
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
                        if (i == 0) PrimaryButton(a.label, run, icon = null) else TextLink(a.label, run)
                    }
                    g.deps.copyText?.let { copy -> snap.error?.let { raw -> TextLink("复制错误信息", { copy(raw) }, color = colors.dim) } }
                }
            }
            Column(Modifier.width(210.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                com.gugaart.ui.mascot.MascotPose("G21", 110.dp, label = "看印花稿纸")
                if (LocalGugaPrefs.current.mascotSpeaks) {
                    MascotLines.pick("player.error", hour = LocalHour.current, seed = snap.itemId.hashCode())?.let {
                        BasicText(it, Modifier.padding(top = 10.dp), style = Guga.song(12.5.sp, color = colors.dim).copy(fontStyle = FontStyle.Italic, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2)
                    }
                }
            }
        }
        BoxButton(GuguIcon.ChevronLeft, "返回", play.exit, Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

/**
 * 手机播放器里弹幕区的上沿（纯函数，有单测）：
 * - 画面让位（[inset]，面板打开）：画面矩形本身已经在页边以下，从它顶上开始 = 0；
 * - 全幅、控件显示：顶栏压在画面上，让到顶栏下沿（[PlayerSpec.TOP]）；
 * - 全幅、控件隐去（全屏）：贴着画面最顶上 = 0。
 */
fun phoneDanmakuTop(inset: Boolean, controlsShown: Boolean): androidx.compose.ui.unit.Dp =
    if (!inset && controlsShown) PlayerSpec.TOP else androidx.compose.ui.unit.Dp(0f)

/**
 * 这一部片还没出过第一帧时盖在画面上的那张图：它自己的剧照（没有就海报虚化 / 纸色），压暗一点，缓冲提示照常画在上面。
 * 第一帧到了（[PlayPhase.Ready]）就撤，以后同一部片换线路、拖进度都不再盖（那时上游的截图挡着，不闪）。
 * 手机和电视的播放器都用它。
 */
@Composable
fun androidx.compose.foundation.layout.BoxScope.NewItemCover(itemId: String?, phase: PlayPhase, art: com.gugaart.ui.data.Art) {
    var shownFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(itemId, phase) { if (phase == PlayPhase.Ready) shownFor = itemId }
    if (!coverNewItem(itemId, shownFor, phase)) return
    Box(Modifier.matchParentSize().background(Guga.colors.paper).testTag("player-new-item-cover")) {
        com.gugaart.ui.components.CoverImage(art.backdrop ?: art.thumb ?: art.poster, Modifier.matchParentSize(), alpha = 0.55f)
    }
}

/** 纯函数：要不要盖。[shownFor] = 最近一次出过画面的那一部；出错 / 播完 / 外部播放器时不盖（那些状态有自己的画面）。 */
fun coverNewItem(itemId: String?, shownFor: String?, phase: PlayPhase): Boolean =
    itemId != null && itemId != shownFor && (phase == PlayPhase.Preparing || phase == PlayPhase.Loading)

/** 顶栏的画中画钮：和 [BoxButton] 同一个方框（40 dp、墨底细边），图形是电脑版的画框 + 小画面（[com.gugaart.ui.desktop.PipGlyph]）。 */
@Composable
private fun PipBoxButton(active: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Box(Modifier.size(48.dp).pressable(press, label = if (active) "收回画中画" else "画中画", onClick = onClick).testTag("player-pip"), contentAlignment = Alignment.Center) {
        Box(Modifier.pressGraphics(press).size(40.dp).background(colors.inkWell).border(1.dp, colors.ink.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            com.gugaart.ui.desktop.PipGlyph(if (active) colors.seal else colors.ink, 20.dp)
        }
    }
}

/**
 * The picture area during an AirPlay hand-off (iPhone, 1.6 F2): title, 「正在投屏 · <device>」, back / play-pause / forward, the
 * timeline, 「停止」. Paper, no mascot (the picture is on the TV). Every control goes through the controller: upstream forwards
 * play / pause / seek to the AVPlayer and the timeline reads the AVPlayer's clock.
 */
@Composable
private fun PhoneAirPlayNote(
    cast: com.gugaart.ui.cast.AirPlayCast,
    play: PlaySession,
    clock: androidx.compose.runtime.State<com.gugugaga.app.player.ClockSnapshot>,
    duration: Double,
    stepMs: Double,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Guga.colors
    val controller = play.controller
    val connecting = cast.phase == com.gugaart.ui.cast.AirPlayPhase.Connecting
    Box(modifier.background(colors.paper).testTag("player-airplay-note"), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("AIRPLAY · 投屏", style = Guga.song(10.sp, FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            BasicText(cast.title, Modifier.padding(top = 8.dp), style = Guga.song(24.sp, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(
                com.gugaart.ui.cast.airPlayWord(cast) + " · " + cast.device,
                Modifier.padding(top = 6.dp).testTag("airplay-state"),
                style = Guga.song(13.sp, color = colors.dim, letterSpacing = 0.1.em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!connecting) {
                val paused = clock.value.paused
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                    CenterKeyContent("退 ${(stepMs / 1000).toInt()} 秒", { controller.seekBy(-stepMs) }) { SeekStepIcon(false, (stepMs / 1000).toInt(), colors.ink, 24.dp) }
                    CenterKey(if (paused) GuguIcon.Play else GuguIcon.Pause, if (paused) "播放" else "暂停", 30.dp) { controller.togglePlay() }
                    CenterKeyContent("进 ${(stepMs / 1000).toInt()} 秒", { controller.seekBy(stepMs) }) { SeekStepIcon(true, (stepMs / 1000).toInt(), colors.ink, 24.dp) }
                }
                Timeline(
                    { clock.value.positionMs }, duration, FloatArray(0), emptyList(),
                    onScrubStart = {}, onScrub = {}, onScrubEnd = { controller.seekTo(it) },
                    modifier = Modifier.widthIn(max = 460.dp),
                )
            }
            val press = rememberPressState()
            Box(
                Modifier.padding(top = 14.dp).pressGraphics(press).border(1.dp, colors.ink.copy(alpha = 0.6f)).pressable(press, label = "停止投屏", onClick = onStop)
                    .padding(horizontal = 18.dp, vertical = 9.dp).testTag("player-airplay-stop"),
            ) {
                BasicText("停止", style = Guga.song(13.sp, FontWeight.Bold, letterSpacing = 0.2.em), maxLines = 1)
            }
        }
    }
}

/**
 * 画中画期间画面那一格（D-lead-24：画面搬走时不留黑洞，纸面说明；时间轴和功能词留着，没有她）。字和电脑版同一份
 * （[com.gugaart.ui.desktop.DesktopPipNote]），按手机的字号排。
 */
@Composable
private fun PhonePipNote(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Box(modifier.background(colors.paper).testTag("player-pip-note"), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("PICTURE IN PICTURE · 画中画", style = Guga.song(10.sp, FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            BasicText("画面在小窗里", Modifier.padding(top = 8.dp), style = Guga.song(26.sp, FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
            val press = rememberPressState()
            Box(
                Modifier.padding(top = 16.dp).pressGraphics(press).border(1.dp, colors.ink.copy(alpha = 0.6f)).pressable(press, label = "回到这里", onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 9.dp).testTag("player-pip-back"),
            ) {
                BasicText("回到这里", style = Guga.song(13.sp, FontWeight.Bold, letterSpacing = 0.2.em), maxLines = 1)
            }
        }
    }
}
