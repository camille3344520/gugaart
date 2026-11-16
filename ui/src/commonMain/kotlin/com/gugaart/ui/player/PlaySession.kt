package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.net.detectSystemProxy
import com.gugugaga.app.player.EmbyPlayerClient
import com.gugugaga.app.player.Notice
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayRequest
import com.gugugaga.app.player.PlaybackSettings
import com.gugugaga.app.player.PlayerClient
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.player.PlayerDeps
import com.gugugaga.app.player.PlayerEvent
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.app.player.PlayerPrefsStore
import com.gugugaga.app.player.StrmSettings
import com.gugugaga.app.session.AppSession
import com.gugugaga.app.settings.PerfPref
import com.gugugaga.app.settings.uiSettings
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.danmaku.heat.HeatFilter
import com.gugugaga.danmaku.match.embyLocalKey
import com.gugugaga.danmaku.render.Tier
import com.gugugaga.danmaku.settings.parseDanmakuPrefsFromBlob
import com.gugugaga.danmaku.ui.rememberDanmakuHeat
import com.gugugaga.ds.theme.GuguTheme
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/*
 * 播放接线：只用上游的公开 API，在本仓库里重新接（docs/adr/0002-player-wiring-in-repo.md）。没有复制上游文件。
 *
 * 这两段是照着上游这两处写的等价物（提交 1e19741a）——上游改了它们，要看一眼这里要不要跟（docs/upstream-sync.md §5.2）：
 *   rememberPlaySession  ←  app/…/player/PlayPage.kt        PlayPage() 里造 PlayerDeps / PlayController 的那一段（115–171 行）
 *   rememberGugaDanmaku  ←  app/…/player/PlayerDanmaku.kt   rememberPlayerDanmaku()（440–508 行）、danmakuTier()（427–432 行）
 *
 * 行为逻辑（PlayController、引擎回退链、弹幕匹配与渲染、Emby 接口）全部是上游的类，这里一行都没有重写。
 */

/** 一次播放：上游的控制器 + 平台 + 播放器偏好，加上画报播放器要的两样（退出、引擎回退的通知）。 */
@Stable
class PlaySession internal constructor(
    val controller: PlayController,
    val platform: PlayerPlatform,
    val prefs: PlayerPrefsStore,
    /** 退出播放器：回上一页；没有上一页（深链进来的）→ 这一条的详情页。 */
    val exit: () -> Unit,
) {
    /** 上游的通知（引擎回退、换线路…）：画报播放器把最近的一条排成缓冲态下面的一行小字，不弹窗（docs/player.md §4）。 */
    var notice: Notice? by mutableStateOf(null)
        internal set

    /** 现在这条字幕是怎么选来的：「自动」「上次」「设置」「手动」；还没选过 / 路由自己带着字幕 = null。顶部信息行用（[subtitleBadge]）。 */
    var subtitleWhy: String? by mutableStateOf(null)
        internal set

    /** 用户在播放器里手动换了字幕之后要做的事（记进「上次的选择」）。 */
    internal var onSubtitleChosen: ((Int?) -> Unit)? = null

    /** 播放器里换字幕（面板、快捷键都走这里，不直接调控制器）：换，并且记下来。 */
    fun chooseSubtitle(index: Int?) {
        controller.selectSubtitle(index)
        subtitleWhy = "手动"
        onSubtitleChosen?.invoke(index)
    }
}

/**
 * 测试缝：换掉播放器对 Emby 的那一层（默认 = 上游的 `EmbyPlayerClient`）。离屏测试给一个假的，配合假引擎，不连任何服务器。
 * 应用里从不设置。
 */
val LocalPlayerClientOverride = staticCompositionLocalOf<((AppSession) -> PlayerClient)?> { null }

/**
 * 测试缝：换掉弹幕那一层（默认 = 上游 `PlayerSlots.danmaku`）。离屏测试没有弹幕服务器，给一个把「弹幕那一格 + 各条弹幕道」画出来的假层，
 * 用来证明弹幕排在哪。应用里从不设置。
 */
val LocalDanmakuSlotOverride = staticCompositionLocalOf<(@Composable (com.gugugaga.app.player.DanmakuHost, androidx.compose.ui.Modifier) -> Unit)?> { null }

/** 测试缝：把这一页的 [PlaySession] 交给测试（离屏渲染不能点：测试直接调控制器的公开命令，比如暂停）。应用里从不设置。 */
val LocalPlaySessionSink = staticCompositionLocalOf<((PlaySession) -> Unit)?> { null }

/**
 * 把 [AppGraph] 接成一个可播放的 [PlayController]。和上游 `PlayPage()` 的接法逐项对应；有意不同的三处见 ADR 0002 §4.1：
 * 通知不走旧皮肤的 Toast、不接下一集的媒体预加载（上游的计划器是 internal）、Emby 那一层可以被测试换掉。
 * 返回 null：这个平台还没有播放器（壳里 `player = null`），或者还没登录。
 */
@Composable
fun rememberPlaySession(route: Route.Play): PlaySession? {
    val g = LocalGraph.current
    val platform = g.deps.player ?: return null
    val session by g.session.session.collectAsState()
    val s = session ?: return null
    val scope = rememberCoroutineScope()
    val prefs = remember { PlayerPrefsStore(g.deps.local) }
    val clientOverride = LocalPlayerClientOverride.current
    // 播放器里换内核：存好首选引擎后 tick 加一，这里换一个控制器（在原来的位置 / 版本 / 音轨 / 字幕接着放）
    val restartTick = PlayerRestart.tick
    val play = remember(s.client, restartTick) {
        var holder: PlaySession? = null
        val restart = if (restartTick > 0) PlayerRestart.take() else null
        val controller = PlayController(
            PlayerDeps(
                client = clientOverride?.invoke(s) ?: EmbyPlayerClient(s.client),
                platform = platform,
                playback = { PlaybackSettings.of(g.settings.value) },
                strm = { StrmSettings.of(g.settings.value) },
                prefs = prefs,
                scope = scope,
                appScope = g.scope,
                notify = { n -> holder?.notice = n },
                // strm / CDN 链接由引擎自己打开，线路跟「设置 › 播放 › strm 直链 › 线路」走
                systemProxy = { withContext(Dispatchers.Default) { detectSystemProxy() } },
                resolveRedirect = { u, h, p -> g.strmRedirects.resolve(u, h, p) },
                // 上一集 / 下一集 / 自动下一集：换路由（返回栈里只留一个播放器），队列跟着走
                onNavigate = { item ->
                    g.queue.nowPlaying(item.id)
                    g.nav.replace(Route.Play(item.id))
                },
                onStartup = { r ->
                    g.startupLog.add(r)
                    g.log.add("perf", r.line())
                    g.log.add("perf", r.timingLine())
                },
                cachedItem = { id -> g.queries.cachedItem(id) },
                parallelStart = g.playerParallelStart,
                prefetchedPlayback = { o, caps -> g.playbackPrefetch.consume("${s.serverId}|${s.userId}", o, caps) },
                log = { level, message -> g.log.add(level, message) },
                scrubDwellMs = { g.scrubDwellMs },
            ),
            restart ?: PlayRequest(route.id, route.mediaSourceId, route.startMs, route.audio, route.subtitle),
        )
        PlaySession(controller, platform, prefs, exit = {
            // 屏幕上的返回键 / 停止 / 出错页的「退出」：和返回键同一个去处——下面不是详情页就先到这部剧的详情页
            val t = playerBackTarget(g.nav.routes(), controller.value.item, controller.currentItemId)
            if (t != null) g.nav.replace(t) else if (!g.nav.pop()) g.nav.replace(Route.Item(controller.currentItemId))
        }).also {
            holder = it
            controller.start()
        }
    }
    DisposableEffect(play) {
        onDispose {
            // 离开播放器 = 结束投屏（电视也停；本机停在电视放到的位置，进度照常报）
            com.gugaart.ui.cast.Cast.stop(g.scope, play, resume = false)
            play.controller.dispose()
        }
    }
    // Trakt：连着就记（开始 / 暂停 / 换集或退出 = 停，带进度）；离屏测试的假客户端不记
    if (clientOverride == null) LaunchedEffect(play) {
        com.gugaart.ui.trakt.TraktService.bind(g)
        com.gugaart.ui.trakt.TraktService.follow(play.controller, s.client)
    }
    // 字幕自动选择（设置 › 字幕与音轨 ›「字幕默认」）：每换一条片源选一次；路由自己带着字幕（详情页里挑好的）就不插手
    val gugaPrefs = rememberUpdatedState(com.gugaart.ui.mascot.LocalGugaPrefs.current)
    val memory = remember { SubtitleMemory(g.deps.local) }
    val explicit = rememberUpdatedState(route.subtitle != null)
    LaunchedEffect(play) {
        fun tracksOf(snap: com.gugugaga.app.player.PlayerSnapshot): List<SubTrack> =
            snap.resolved?.mediaSource?.mediaStreams.orEmpty().filter { it.type == "Subtitle" }.map { SubTrack(it.index, it.language, it.title ?: it.displayTitle) }
        fun keyOf(snap: com.gugugaga.app.player.PlayerSnapshot): String = snap.item?.seriesId ?: snap.itemId
        play.onSubtitleChosen = { index ->
            val snap = play.controller.value
            memory.put(keyOf(snap), SubChoice.of(tracksOf(snap).firstOrNull { it.index == index }))
        }
        var done: String? = null
        play.controller.snapshot.collect { snap ->
            val resolved = snap.resolved ?: return@collect
            val item = snap.item ?: return@collect
            // 等内核真的起来了（Ready：轨道表已经报上来）再选（用户 2026-11-15：「字幕有时候开关不灵敏，显示有，但是没有字幕，要开关一次才有」）。
            // 以前一拿到播放信息就选：那时内核还没加载 / 正在加载，选择先挂起，内核的 load() 一开始就把挂起的选择清掉——
            // 控制器记着「开」，内核里其实没开。换内核、重新协商也会再 load 一次，同样会丢。
            if (snap.phase != com.gugugaga.app.player.PlayPhase.Ready) return@collect
            val id = snap.itemId + "|" + resolved.mediaSource.id
            if (done == id) return@collect
            done = id
            if (explicit.value) {
                play.subtitleWhy = null
                return@collect
            }
            val streams = resolved.mediaSource.mediaStreams
            val audio = streams.firstOrNull { it.type == "Audio" && it.index == resolved.audioStreamIndex } ?: streams.firstOrNull { it.type == "Audio" && it.isDefault == true } ?: streams.firstOrNull { it.type == "Audio" }
            val pick = pickSubtitle(gugaPrefs.value.subtitleDefault, memory.get(keyOf(snap)), audio?.language, item.productionLocations, tracksOf(snap))
            play.subtitleWhy = pick.why
            if (snap.subtitleIndex != pick.index) play.controller.selectSubtitle(pick.index)
        }
    }
    val sink = LocalPlaySessionSink.current
    SideEffect { sink?.invoke(play) }
    LaunchedEffect(play, route) { play.controller.changeItem(PlayRequest(route.id, route.mediaSourceId, route.startMs, route.audio, route.subtitle)) }
    // 不是队列交过来的条目（从详情页、记录里点开的）：队列到此为止
    LaunchedEffect(route.id) { if (g.queue.current != null && g.queue.playingId != route.id) g.queue.clear() }
    return play
}

/** 弹幕渲染器的起始档位：用户选的「材质」档，否则按设备（电脑 High，其余 Mid）。和上游 `danmakuTier` 同一张表。 */
fun gugaDanmakuTier(perf: PerfPref, mode: UiMode): Tier = when (perf) {
    PerfPref.Low -> Tier.Low
    PerfPref.Mid -> Tier.Mid
    PerfPref.High -> Tier.High
    PerfPref.Auto -> if (mode == UiMode.Desktop) Tier.High else Tier.Mid
}

/**
 * 这一页的弹幕（上游的 [PlayerDanmaku]：匹配、图层、热力图、面板数据），以及让它跟着控制器走的那几步：
 * 换集 → 换弹幕；自动匹配；用户拖进度 → 重排可见窗口里的弹幕；热力图在弹幕 / 过滤 / 时长变了的时候重算（不在每个时钟节拍上）。
 * 和上游 `rememberPlayerDanmaku()` 的步骤逐项对应；不同的两处见 ADR 0002 §4.2。
 */
@Composable
fun rememberGugaDanmaku(play: PlaySession): PlayerDanmaku {
    val g = LocalGraph.current
    val controller = play.controller
    val scope = rememberCoroutineScope()
    val mode = GuguTheme.dimens.mode
    val d = remember(controller) {
        PlayerDanmaku(
            scope, g.settings, play.prefs, g.danmaku,
            matchInput = { id -> (g.session.session.value?.client?.getDanmakuMatchInput(id) ?: error("没有会话")).also { DanmakuSeasonGuard.record(id, it) } },
            mode = mode,
            tier = gugaDanmakuTier(g.settings.value.uiSettings.perf, mode),
            dprCap = if (mode == UiMode.Tv) 1.0 else 3.0,
            openSettings = { g.nav.navigate(Route.Settings("danmaku")) },
            localKey = { id -> embyLocalKey(g.session.session.value?.serverId.orEmpty(), id).takeIf { it.isNotEmpty() } },
            log = { level, message -> g.log.add(level, message) },
            perf = { message -> g.log.add("perf", message) },
        )
    }
    DisposableEffect(d) { onDispose { d.dispose() } }
    val settings by g.settings.state.collectAsState()
    val prefs = remember(settings) { parseDanmakuPrefsFromBlob(settings) }
    val pp by play.prefs.state.collectAsState()
    SideEffect { d.playerPrefs = pp }

    val itemId by remember(controller) { controller.snapshot.map { it.itemId }.distinctUntilChanged() }.collectAsState(controller.value.itemId)
    LaunchedEffect(d, itemId) { d.onItem(itemId) }
    LaunchedEffect(d, itemId, prefs.render.enabled, prefs.autoMatch) { d.autoStart(prefs.render.enabled, prefs.autoMatch) }

    val engine by controller.engineFlow.collectAsState()
    LaunchedEffect(engine) {
        engine?.events?.collect { e -> if (e is PlayerEvent.Seeked && !controller.isHolding) d.layer.seeked(e.positionMs) }
    }

    val clockDuration by remember(controller) { controller.clock.map { it.durationMs }.distinctUntilChanged() }.collectAsState(controller.clock.value.durationMs)
    val duration = clockDuration.takeIf { it > 0 } ?: controller.value.resolved?.durationMs?.toDouble() ?: 0.0
    val render = prefs.render
    val heat = rememberDanmakuHeat(
        items = d.items,
        active = render.enabled && pp.heatmap,
        filter = HeatFilter(render.modes, render.blockWords),
        offsetMs = render.offsetMs,
        durationMs = duration,
        buckets = d.heatBuckets,
    )
    SideEffect { d.heat = heat }
    return d
}
