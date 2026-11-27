package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.gugugaga.app.player.PlayerClient
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.player.PlayerDeps
import com.gugugaga.app.player.PlayerEvent
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.app.player.PlayerPrefsStore
import com.gugugaga.app.player.StrmSettings
import com.gugugaga.app.session.AppSession
import com.gugugaga.app.settings.playbackPrefs
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
    /** 本台（当前会话）的播放客户端：从别台切回来用（1.5.0 B3）。 */
    val homeClient: PlayerClient = controller.currentClient,
    /** 路由里的条目（本台的）：放着别台的源时，详情 / 返回 / 「版本」面板认的还是它。 */
    val homeItemId: String = controller.currentItemId,
) {
    /** 各个别台客户端是哪台（[RemotePlay.choose] / 开播交接登记）。 */
    private val remotes = HashMap<PlayerClient, RemoteSource>()

    internal fun register(client: PlayerClient, source: RemoteSource) {
        remotes[client] = source
    }

    /**
     * 现在正放着别台的哪个源（放的是本台的 = null）。跟着控制器实际用的客户端走：换台失败上游会把客户端放回去，这里也就跟着回去。
     * 组合里读它要配合读一次 `controller.snapshot`（换台一定会改 snapshot 的 itemId / sourceId）。
     */
    val remote: RemoteSource? get() = remotes[controller.currentClient]

    /** 上游的通知（引擎回退、换线路…）：画报播放器把最近的一条排成缓冲态下面的一行小字，不弹窗（docs/player.md §4）。 */
    var notice: Notice? by mutableStateOf(null)
        internal set

    /** 第几条通知（同样的通知连来两次也算两次：播放器的提示条按它重新亮，[PlayerNoticeFlash]）。 */
    var noticeSeq: Int by mutableIntStateOf(0)
        private set

    /** 「换台失败」那一条小条正亮着（[PlayerNoticeFlash] 写）：电视上这时先收起格式徽章下面那行说明，两行不叠（审计 P2-5）。 */
    var noticeShowing: Boolean by mutableStateOf(false)
        internal set

    /** 来了一条通知（上游的 notify、画报自己的「换台失败」都走这里）。 */
    fun post(n: Notice) {
        notice = n
        noticeSeq++
    }

    /** 现在这条字幕是怎么选来的：「自动」「上次」「设置」「手动」；还没选过 / 路由自己带着字幕 = null。顶部信息行用（[subtitleBadge]）。 */
    var subtitleWhy: String? by mutableStateOf(null)
        internal set

    /** 用户在播放器里选过字幕的源（itemId|sourceId）：开播第一帧之后不再改选。 */
    private val userChosenSources = HashSet<String>()

    internal fun markSubtitleChosen(itemId: String, sourceId: String?) {
        if (sourceId != null) userChosenSources += "$itemId|$sourceId"
    }

    internal fun hasUserChosenSubtitle(itemId: String, sourceId: String?): Boolean =
        sourceId != null && "$itemId|$sourceId" in userChosenSources

    /** 用户在播放器里手动换了字幕之后要做的事（记进「上次的选择」）。 */
    internal var onSubtitleChosen: ((Int?) -> Unit)? = null

    /** 播放器里换字幕（面板、快捷键都走这里，不直接调控制器）：换，并且记下来。 */
    fun chooseSubtitle(index: Int?) {
        val snap = controller.value
        markSubtitleChosen(snap.itemId, snap.resolved?.mediaSource?.id)
        controller.selectSubtitle(index)
        subtitleWhy = "手动"
        onSubtitleChosen?.invoke(index)
    }

    /** 记「这一集 + 这条字幕」的字幕时间（[rememberPlaySession] 接上本地存储；测试里没有 = 只改不记）。 */
    internal var timing: SubtitleTimingSync? = null

    /** 字幕时间改成 [ms]（面板、电视页边、键盘都走这里）：改，并且记下来。 */
    fun setSubtitleTiming(ms: Long) {
        val v = ms.coerceIn(-SUBTITLE_TIMING_LIMIT_MS, SUBTITLE_TIMING_LIMIT_MS)
        controller.setSubtitleDelay(v)
        val s = controller.value
        timing?.remember(s.itemId, s.sourceId, s.subtitleIndex, v)
    }

    /** 字幕时间走一步（± [deltaMs]）。 */
    fun stepSubtitleTiming(deltaMs: Long) = setSubtitleTiming(subtitleTimingNext(controller.value.subtitleDelayMs, deltaMs))
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

/**
 * 把这一页的 [PlaySession] 交出去（每次重组都会叫一次）。测试用它直接调控制器的公开命令（离屏渲染不能点）；
 * Mac 壳用它把控制器接到系统的「正在播放」和媒体键（任务单 MP-6）。别的壳不设置。
 */
val LocalPlaySessionSink = staticCompositionLocalOf<((PlaySession) -> Unit)?> { null }

/** 验收运行器的测试缝（iOS `dmjit151`）：把这一页的 PlayerDanmaku 交给外面（手动搜 / 选弹幕）；应用里永远是 null = 不交。 */
val LocalPlayerDanmakuSink = staticCompositionLocalOf<((com.gugugaga.app.player.PlayerDanmaku) -> Unit)?> { null }

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
        // 字幕搜索的「仅本次播放」：这一集有本机字幕时加进协商结果（subtitle/LocalSubtitles.kt；没有就原样转发）
        val home = com.gugaart.ui.subtitle.SubtitleAwareClient(clientOverride?.invoke(s) ?: EmbyPlayerClient(s.client))
        // 1.5.0 B3：详情页选了别台的源（或在别台的源上换了内核）→ 一开始就用那台的客户端（池里找片源时建好的）
        val handoff = RemotePlay.take(route.id)
        val remoteClient = handoff?.let { com.gugaart.ui.data.OtherServers.poolFor(g).clientNow(it.serverId) }
        val remote = if (handoff != null && remoteClient != null) RemoteSource(handoff.serverId, handoff.serverName, handoff.itemId, route.id, remoteClient) else null
        val remotePlayer = remote?.let { RemotePlay.playerClientOf(it.client) }
        // P1-1：这一部记住的内核盖在全局首选上（剧按剧记；放着别台的源时认本台路由里那一条）
        val filmItem = if (remote != null) route.id else restart?.itemId ?: route.id
        val filmEngine = EngineMemory(g.deps.local).get(filmItem, g.queries.cachedItem(filmItem)?.seriesId)
        val controller = PlayController(
            PlayerDeps(
                client = remotePlayer ?: home,
                platform = platform,
                playback = { playbackFor(g.settings.value, filmEngine) },
                strm = { StrmSettings.of(g.settings.value) },
                prefs = prefs,
                scope = scope,
                appScope = g.scope,
                notify = { n -> holder?.post(n) },
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
            restart ?: (if (remote != null && handoff != null) PlayRequest(remote.itemId, handoff.mediaSourceId, handoff.startMs) else null)
                ?: PlayRequest(route.id, route.mediaSourceId, route.startMs, route.audio, route.subtitle),
        )
        var session: PlaySession? = null
        PlaySession(controller, platform, prefs, exit = {
            // 屏幕上的返回键 / 停止 / 出错页的「退出」：和返回键同一个去处——下面不是详情页就先到这部剧的详情页。
            // 放着别台的源时，条目 / 条目 id 是那台的，本台认不得：按路由里本台的条目走
            val onRemote = session?.remote != null
            val t = playerBackTarget(g.nav.routes(), if (onRemote) null else controller.value.item, if (onRemote) route.id else controller.currentItemId)
            if (t != null) g.nav.replace(t) else if (!g.nav.pop()) g.nav.replace(Route.Item(if (onRemote) route.id else controller.currentItemId))
        }, homeClient = home, homeItemId = route.id).also {
            session = it
            if (remote != null && remotePlayer != null) it.register(remotePlayer, remote)
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
    // Trakt：连着就记（开始 / 暂停 / 换集或退出 = 停，带进度）；离屏测试的假客户端不记。
    // 编号从正在播的那台取：放着别台的源 = 别台的客户端（条目 id 只在那一台有意义，1.5.0）
    if (clientOverride == null) LaunchedEffect(play) {
        com.gugaart.ui.trakt.TraktService.bind(g)
        com.gugaart.ui.trakt.TraktService.follow(play.controller) { play.remote?.client ?: s.client }
    }
    // 字幕自动选择（设置 › 字幕与音轨 ›「字幕默认」）：每换一条片源选一次；路由自己带着字幕（详情页里挑好的）就不插手
    val gugaPrefs = rememberUpdatedState(com.gugaart.ui.mascot.LocalGugaPrefs.current)
    val memory = remember { SubtitleMemory(g.deps.local) }
    val explicit = rememberUpdatedState(route.subtitle != null)
    LaunchedEffect(play) {
        fun tracksOf(snap: com.gugugaga.app.player.PlayerSnapshot): List<SubTrack> {
            val emby = snap.resolved?.mediaSource?.mediaStreams.orEmpty().filter { it.type == "Subtitle" }
            if (emby.isNotEmpty()) {
                return emby.map { SubTrack(it.index, it.language, it.title ?: it.displayTitle, it.isExternal == true, it.isDefault == true) }
            }
            // 1.3.1：Emby 流表空（《谷雨》这类源）→ 用内核 track-list 的内嵌轨，编号 = ordinal（和上游空表回落同一套）
            return play.controller.engineFlow.value?.tracks?.value?.subtitle.orEmpty().filter { !it.external }
                .mapIndexed { i, t -> SubTrack(i, t.lang, t.label, false, t.isDefault) }
        }
        fun keyOf(snap: com.gugugaga.app.player.PlayerSnapshot): String = snap.item?.seriesId ?: snap.itemId
        play.onSubtitleChosen = { index ->
            val snap = play.controller.value
            memory.put(keyOf(snap), SubChoice.of(tracksOf(snap).firstOrNull { it.index == index }))
        }
        val pickedNonEmpty = HashSet<String>()
        val pickedAny = HashSet<String>()
        play.controller.snapshot.collect { snap ->
            val resolved = snap.resolved ?: return@collect
            snap.item ?: return@collect
            if (snap.phase != com.gugugaga.app.player.PlayPhase.Ready) return@collect
            val sourceKey = snap.itemId + "|" + resolved.mediaSource.id
            if (play.hasUserChosenSubtitle(snap.itemId, resolved.mediaSource.id)) return@collect
            if (com.gugaart.ui.subtitle.SubtitleSearchBoard.pinned == snap.itemId) return@collect
            if (sourceKey in pickedNonEmpty) return@collect
            if (snap.framedItemId == snap.itemId && sourceKey in pickedAny) return@collect
            val tracks = tracksOf(snap)
            val embyHasSubs = resolved.mediaSource.mediaStreams.any { it.type == "Subtitle" }
            val engineTracksReported = play.controller.engineFlow.value?.tracks?.value != null
            if (!embyHasSubs && !engineTracksReported && snap.framedItemId != snap.itemId) return@collect
            pickedAny += sourceKey
            if (embyHasSubs || tracks.isNotEmpty() || engineTracksReported) pickedNonEmpty += sourceKey
            if (explicit.value) {
                play.subtitleWhy = null
                return@collect
            }
            val langs = com.gugaart.ui.data.effectiveSubtitleLangs(
                g.settings.value.playbackPrefs.subtitleLangs,
                gugaPrefs.value.subLangsSeeded,
            )
            val pick = pickSubtitle(
                gugaPrefs.value.subtitleDefault,
                langs,
                memory.get(keyOf(snap)),
                tracks,
            )
            play.subtitleWhy = pick.why
            if (snap.subtitleIndex != pick.index) play.controller.selectSubtitle(pick.index)
        }
    }
    // 字幕时间：按「这一集 + 这条字幕」记；开播 / 换集 / 换字幕时放回记下的值（没记过 = 0；上游换集会清零）
    val timingMemory = remember { SubtitleTimingMemory(g.deps.local) }
    LaunchedEffect(play) {
        val sync = SubtitleTimingSync(timingMemory)
        play.timing = sync
        play.controller.snapshot.collect { snap ->
            if (snap.phase != com.gugugaga.app.player.PlayPhase.Ready) return@collect
            sync.restore(snap.itemId, snap.sourceId, snap.subtitleIndex, snap.subtitleDelayMs)?.let(play.controller::setSubtitleDelay)
        }
    }
    // 字幕智能匹配（1.4.1 U2，subtitle/BestMatchUi.kt）：开播后这一集没有中文字幕 → 搜一次，指纹精确的自动加载（开关开着）或者提示一句；
    // 离屏测试的假客户端不搜（截图里的提示由测试直接摆状态）
    if (clientOverride == null) com.gugaart.ui.subtitle.RememberAutoBest(play, explicit = route.subtitle != null)
    val sink = LocalPlaySessionSink.current
    SideEffect { sink?.invoke(play) }
    LaunchedEffect(play, route) {
        // 放着别台的源、路由还是本台那一条：不是「换了条目」，别把本台的条目 id 交给别台的客户端
        if (play.remote?.homeItemId == route.id) return@LaunchedEffect
        play.controller.changeItem(PlayRequest(route.id, route.mediaSourceId, route.startMs, route.audio, route.subtitle))
    }
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
            matchInput = { id ->
                // 放着别台的源：弹幕匹配要问那一台（条目 id 是那台的）
                val client = play.remote?.client ?: g.session.session.value?.client ?: error("没有会话")
                val input = client.getDanmakuMatchInput(id)
                // 这一季在 Emby 的首播年 / 集数 / 最大季号（只读，一部剧取一次）：季守卫认定「第 N 季」用
                DanmakuSeasonGuard.record(id, input, runCatching { DanmakuSeasonFacts.of(client, input) }.getOrNull())
                input
            },
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
    LocalPlayerDanmakuSink.current?.let { sink -> androidx.compose.runtime.LaunchedEffect(d) { sink(d) } }
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
