package com.gugaart.ui.screens.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.OutlineTag
import com.gugaart.ui.data.EmbySourceRepository
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.OtherServerHealth
import com.gugaart.ui.data.OtherServerProgress
import com.gugaart.ui.data.ServerSearchState
import com.gugaart.ui.data.PlaySource
import com.gugaart.ui.data.SourceKind
import com.gugaart.ui.data.SourceRepository
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.VersionPick
import com.gugaart.ui.data.VersionPickStore
import com.gugaart.ui.data.defaultSource
import com.gugaart.ui.data.pickMatchLevel
import com.gugaart.ui.data.playSourceSpecLine
import com.gugaart.ui.player.AvCaps
import com.gugaart.ui.player.DeviceAv
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaColors
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlaybackSettings
import com.gugugaga.app.player.StrmSettings
import com.gugugaga.app.player.detailPrefetchPrefs
import com.gugugaga.app.player.primaryEngineStep
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.PlaybackPrefs
import com.gugugaga.ds.physics.LocalMotionPrefs
import kotlinx.coroutines.CancellationException

/*
 * 详情页三端 + 播放器「版本」面板共用的一份「源」状态（1.5.0 C2-UI，docs/plan-multi-server.md §2.3 / §2.4）。
 * - 第一帧就有行：条目自带的 `MediaSources` 直接当本台的 SameItem 源列出来（不空一下）；
 * - 再问 [SourceRepository]（[LocalSourceRepository]；没给 = 当前服务器的 [EmbySourceRepository]），新来的行**接在下面**，
 *   已经在的行原地更新（直连 / 转码、延迟），**不重排**；
 * - 选中 = 用户这次点的 → 记在 [VersionPickStore] 里的（[pickMatchLevel] 认）→ 默认版本规则 [defaultSource]；
 *   用户没选过时，规则挑的那一行标「推荐」。
 * 只有一台服务器：各端这一栏仍叫「版本」。
 */

/**
 * 「源」从哪来：null（默认）= 当前服务器的真实现 [EmbySourceRepository]；离屏测试 / 截图给 [com.gugaart.ui.data.FakeSourceRepository]。
 */
val LocalSourceRepository = staticCompositionLocalOf<SourceRepository?> { null }

/** 两行是不是同一个源：同条目、同片源；同一台服务器（本台的 SameItem / Duplicate 互认，服务器 id 写法不同也算）。 */
internal fun sameSource(a: PlaySource, b: PlaySource): Boolean {
    if (a.itemId != b.itemId) return false
    val ids = a.mediaSource.id.isNotEmpty() || b.mediaSource.id.isNotEmpty()
    if (if (ids) a.mediaSource.id != b.mediaSource.id else a.mediaSource != b.mediaSource) return false
    return a.serverId == b.serverId || (a.kind != SourceKind.OtherServer && b.kind != SourceKind.OtherServer)
}

/** 一行源的标题：Emby 给这个版本起的名字（去空白）；没有 → 规格那一行的头一段（「4K」）；再没有 →「默认版本」。纯函数。 */
fun sourceTitle(s: PlaySource, caps: AvCaps? = DeviceAv.caps): String =
    s.mediaSource.name?.trim()?.takeIf { it.isNotEmpty() }
        ?: playSourceSpecLine(s, caps).substringBefore(" · ").takeIf { it.isNotEmpty() }
        ?: "默认版本"

/**
 * 各端一行源要写的字：标题、规格（第二行）、两个小签（「另一份文件」「推荐」）。
 * [server]：列表里有别台的源时，每行前面写的服务器名（标题已经是「服务器名 · 版本名」，这里给小圆点用）；只有本台 = null。
 * [offline]：那台此刻连不上 / 要重新登录（小圆点灰着）。
 */
data class SourceRowText(
    val title: String,
    val spec: String,
    val duplicate: Boolean,
    val recommended: Boolean,
    val server: String? = null,
    val offline: Boolean = false,
)

/** 规格那一行：别台的源后面再写延迟（「4K · HDR10 · 直连 · 23 ms」）。纯函数。 */
fun sourceSpecLine(s: PlaySource, caps: AvCaps? = DeviceAv.caps): String {
    val spec = playSourceSpecLine(s, caps)
    val ms = s.latencyMs?.takeIf { s.kind == SourceKind.OtherServer } ?: return spec
    return if (spec.isEmpty()) "$ms ms" else "$spec · $ms ms"
}

/** 别台没找到时那一台写什么：没有这部片 / 连不上 / 没回应 / 要重新登录（还在找 / 找到了 = null）。纯函数。 */
fun serverMissWord(p: OtherServerProgress): String? = when (p.state) {
    ServerSearchState.Searching -> null
    ServerSearchState.Found -> "没有这部片".takeIf { p.found == 0 }
    ServerSearchState.NotFound -> "没有这部片"
    ServerSearchState.Offline -> "连不上"
    ServerSearchState.TimedOut -> "没回应"
    ServerSearchState.NeedsLogin -> "要重新登录"
}

/**
 * 列表底下的进展（纯函数，有单测）：[searching] = 还在找的每一台一行「正在问 香港中转…」；
 * [summary] = 有结论但没贡献源的几台并成一行「办公室 NAS 连不上 · 书房 没有这部片」（都找到了 = null）。
 */
data class SourceProgressText(val searching: List<String>, val summary: String?)

fun sourceProgressText(servers: List<OtherServerProgress>): SourceProgressText = SourceProgressText(
    searching = servers.filter { it.state == ServerSearchState.Searching }.map { "正在问 ${it.server.name}…" },
    summary = servers.mapNotNull { p -> serverMissWord(p)?.let { "${p.server.name} $it" } }.joinToString(" · ").takeIf { it.isNotEmpty() },
)

/**
 * 一部片的「源」列表 + 选中哪一个。快照状态（组合里读到就会跟着变）；不碰组合也能用（单测直接 new）。
 * [itemId]：这一页的条目；[providerIds]：记住选择的键（没有 TMDB / IMDB = 不记，只在这一页有效）；[store] null = 不记；
 * [currentServerId]：默认规则同分时取当前服务器的；[caps] / [maxBitrateBps]：默认规则用的设备能力、码率上限（null = 不限）。
 */
@Stable
class SourcesState(
    val itemId: String,
    private val providerIds: Map<String, String?>,
    private val store: VersionPickStore?,
    private val currentServerId: String?,
    initial: List<PlaySource> = emptyList(),
    private val caps: () -> AvCaps? = { DeviceAv.caps },
    private val maxBitrateBps: () -> Long? = { null },
) {
    /** 全部的源，按出现的先后（只往后接，不重排）。 */
    var sources: List<PlaySource> by mutableStateOf(initial)
        private set

    /**
     * 别的服务器这一次找片源的进展（1.5.0 B2；只有本台时是空的）。界面画「加载中」「离线」「要重新登录」用；
     * 顺序固定（参与顺序），只更新状态，不增删。
     */
    var servers: List<OtherServerProgress> by mutableStateOf(emptyList())
        private set

    /** 换上新的进展（[com.gugaart.ui.data.SourceSearch.servers]）。 */
    fun progress(list: List<OtherServerProgress>) {
        if (list != servers) servers = list
    }

    /** 各台此刻的情况（池的 [OtherServerHealth]）：连不上 / 要重新登录的那台，行前的小圆点灰着。 */
    var health: Map<String, OtherServerHealth> by mutableStateOf(emptyMap())

    /** 列表里有别台的源：每一行都写服务器名（本台的也写，读起来是一张「在哪几处」的单子）。 */
    val hasOtherServers: Boolean get() = sources.any { it.kind == SourceKind.OtherServer }

    /** 有别台参与（有别台的行，或者正在 / 问过别台）：这一栏叫「源」；只有本台 = 「版本」（docs/plan-multi-server.md §2.2）。 */
    val crossServer: Boolean get() = servers.isNotEmpty() || hasOtherServers

    val sectionTitle: String get() = if (crossServer) "源" else "版本"

    val sectionEnglish: String get() = if (crossServer) "SOURCES" else "VERSIONS"

    /** 列表底下那几行进展。 */
    val progressText: SourceProgressText get() = sourceProgressText(servers)

    /** 这一页里用户点过的那一个（没点过 = null）。 */
    private var chosen: PlaySource? by mutableStateOf(null)

    /** 记在本地的选择（这一页打开时读一次；这一页里再点的以 [chosen] 为准）。 */
    private val stored: VersionPick? = store?.pickFor(providerIds)

    /** 用户选过（这一页点过，或者记着的选择认得出列表里的某一行）。 */
    val userPicked: Boolean by derivedStateOf {
        chosen?.let { c -> sources.any { sameSource(it, c) } } == true ||
            (stored != null && sources.any { pickMatchLevel(stored, it) > 0 })
    }

    /** 选中的源：用户点的 → 记着的 → 默认规则。没有源 = null。 */
    val selected: PlaySource? by derivedStateOf {
        chosen?.let { c -> sources.firstOrNull { sameSource(it, c) } }
            ?: defaultSource(sources, pick = stored, caps = caps(), maxBitrateBps = maxBitrateBps(), currentServerId = currentServerId)
    }

    fun isSelected(s: PlaySource): Boolean = selected?.let { sameSource(it, s) } == true

    /** 「推荐」：用户还没选过时，默认规则挑的那一行（只有一个源时不标，没得比）。 */
    fun isRecommended(s: PlaySource): Boolean = sources.size > 1 && !userPicked && isSelected(s)

    /** 这一行写什么（各端同一套字）。有别台的源时标题前面加服务器名（本台的服务器名空着就写「本台」）。 */
    fun rowText(s: PlaySource): SourceRowText {
        val title = sourceTitle(s, caps())
        val server = if (hasOtherServers) s.serverName.trim().ifEmpty { if (s.kind == SourceKind.OtherServer) "别的服务器" else "本台" } else null
        val h = health[s.serverId]
        return SourceRowText(
            if (server != null) "$server · $title" else title,
            sourceSpecLine(s, caps()),
            s.kind == SourceKind.Duplicate,
            isRecommended(s),
            server,
            offline = server != null && (h == OtherServerHealth.Offline || h == OtherServerHealth.NeedsLogin),
        )
    }

    /** 选这一个，并记住（按 provider id，下次打开这部片还是它）。 */
    fun pick(s: PlaySource) {
        chosen = s
        store?.remember(providerIds, s)
    }

    /**
     * 接上新取到的源：已经在的行原地换成新的（直连 / 转码、延迟），没有的接在最后。不重排、不删。
     * 本台的源永远排在别台的前面：晚到的本台行（播放器里条目自带的片源后到）插在第一条别台的行前面；别台的按到达先后接在最后。
     * [replace] = false：只接没有的，已经在的行不动（条目自带的片源晚到时用：仓库已经补好的直连 / 转码不能被它盖回「不知道」）。
     */
    fun merge(fetched: List<PlaySource>, replace: Boolean = true) {
        if (fetched.isEmpty()) return
        val out = sources.toMutableList()
        for (f in fetched) {
            val i = out.indexOfFirst { sameSource(it, f) }
            when {
                i >= 0 -> if (replace) out[i] = f
                f.kind == SourceKind.OtherServer -> out += f
                else -> out.indexOfFirst { it.kind == SourceKind.OtherServer }.let { at -> if (at < 0) out += f else out.add(at, f) }
            }
        }
        if (out != sources) sources = out
    }

    /**
     * 开播的路由：放选中的那一个。重复片是另一个条目（条目 id 不同）——[resumeMs]（这一页条目的续看位置）只对同一个条目有效，
     * 换了条目就不带（让服务器按那一个条目自己的进度来）。只有一个源时不指定片源（和以前一样交给服务器默认）。
     */
    fun playRoute(resumeMs: Long?, audio: Int? = null, subtitle: Int? = null): Route.Play {
        val s = selected ?: return Route.Play(itemId, startMs = resumeMs, audio = audio, subtitle = subtitle)
        if (s.kind == SourceKind.OtherServer) {
            // 1.5.0 B3：别台的源。路由仍是本台这一条（返回 / 详情认它）；播放器开控制器时取走这张交接单，用那台的客户端开播。
            // 续看位置、音轨 / 字幕的编号都是本台的，对那台不作数：交给那台自己的进度和设置
            com.gugaart.ui.player.RemotePlay.handOff(
                com.gugaart.ui.player.RemoteHandoff(itemId, s.serverId, s.serverName, s.itemId, s.mediaSource.id.takeIf { it.isNotEmpty() }),
            )
            return Route.Play(itemId)
        }
        val same = s.itemId == itemId
        val choose = sources.size > 1 || !same
        return Route.Play(
            s.itemId,
            mediaSourceId = s.mediaSource.id.takeIf { it.isNotEmpty() && choose },
            startMs = if (same) resumeMs else null,
            audio = audio,
            subtitle = subtitle,
        )
    }
}

/** 条目自带的片源 → 本台的 SameItem 源（第一帧就列出来的那几行）。 */
fun ownSources(itemId: String, own: List<MediaSourceInfo>, serverId: String, serverName: String): List<PlaySource> =
    own.map { PlaySource(serverId, serverName, itemId, it, SourceKind.SameItem) }

private fun typeOf(kind: Kind): String? = when (kind) {
    Kind.Movie -> "Movie"
    Kind.Episode -> "Episode"
    else -> null
}

/** 直连 / 转码预估用的播放偏好：和详情页预取（上游 `DetailPrefetch`）同一套——首选引擎的第一步 + 播放设置。没有播放器的平台 = null（判不了）。 */
private fun AppGraph.sourcePrefs(itemId: String, mediaSourceId: String): PlaybackPrefs? {
    val platform = deps.player ?: return null
    val json = settings.value
    val playback = PlaybackSettings.of(json)
    val step = primaryEngineStep(playback.engine, platform, null, mediaSourceId, null, playback.audioLangs) ?: return null
    return detailPrefetchPrefs(itemId, mediaSourceId, step.engine, playback, StrmSettings.of(json))
}

/** 这一页用哪个「源」仓库：[LocalSourceRepository] 给了就用它，否则当前服务器的真实现（没登录 = null）。 */
@Composable
fun rememberSourceRepository(): SourceRepository? {
    LocalSourceRepository.current?.let { return it }
    val g = LocalGraph.current
    val session = g.session.session.collectAsState().value ?: return null
    return remember(session) {
        val prefs = { id: String, ms: String -> g.sourcePrefs(id, ms) }
        val local = EmbySourceRepository(session.client, session.serverId, session.serverName, prefs = prefs)
        // 1.5.0 B2：再去别的服务器找同一部片（开关关了 / 只存了一台 = 和 local 一样）
        com.gugaart.ui.data.CrossServerSourceRepository(
            local, session.client,
            com.gugaart.ui.data.OtherServerSourceFinder(com.gugaart.ui.data.OtherServers.poolFor(g), prefs),
        )
    }
}

/** 电影 / 单集详情页的「源」状态。 */
@Composable
fun rememberSourcesState(movie: Title): SourcesState =
    rememberSourcesState(movie.id, typeOf(movie.kind), movie.providerIds, movie.sources, movie.name)

/**
 * 一个条目的「源」状态（详情页、播放器面板）。[own]：条目自带的片源（播放器里开播之后才到，变了就并进来）；
 * [providerIds] 变了（播放器里条目详情到了）再问一次仓库。
 */
@Composable
fun rememberSourcesState(itemId: String, type: String?, providerIds: Map<String, String?>, own: List<MediaSourceInfo>, name: String = ""): SourcesState {
    val g = LocalGraph.current
    val session = g.session.session.collectAsState().value
    val serverId = session?.serverId.orEmpty()
    val serverName = session?.serverName.orEmpty()
    val repo = rememberSourceRepository()
    // provider id 是记住选择的键：播放器里条目详情晚到，到了换一份状态（自带的行照样第一帧就在）
    val state = remember(itemId, serverId, com.gugaart.ui.data.providerKeyOf(providerIds)) {
        SourcesState(
            itemId, providerIds, VersionPickStore(g.deps.local), serverId,
            initial = ownSources(itemId, own, serverId, serverName),
            maxBitrateBps = { PlaybackSettings.of(g.settings.value).maxBitrateMbps.takeIf { it > 0 }?.let { (it * 1_000_000).toLong() } },
        )
    }
    LaunchedEffect(state, own) { state.merge(ownSources(itemId, own, serverId, serverName), replace = false) }
    // 各台此刻连不连得上（池的健康表）：行前的小圆点跟着变灰
    LaunchedEffect(state) { com.gugaart.ui.data.OtherServers.poolFor(g).health.collect { state.health = it } }
    LaunchedEffect(state, repo, providerIds) {
        if (repo == null) return@LaunchedEffect
        val item = BaseItemDto(id = itemId, name = name, type = type, mediaSources = own, providerIds = providerIds)
        // 1.5.0 B2：流式——先到本台的，别台的到一台补一台（累计的列表；merge 只往后接、不重排）
        try {
            repo.search(item).collect { r ->
                state.merge(r.sources)
                state.progress(r.servers)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 取不到就只有条目自带的那几行
        }
    }
    return state
}

/**
 * 一行源后面的小签：「推荐」（专色细框）、「另一份文件」（米白 64 % 细框）。都没有 = 什么都不画。
 * [onInk]：这一行此刻是米白实底（电脑的选中 / 悬停、电视的焦点）——推荐换深一号的朱红，另一份文件换墨色。
 */
@Composable
fun SourceTags(row: SourceRowText, size: TextUnit, line: Dp, modifier: Modifier = Modifier, onInk: Boolean = false, gap: Dp = 6.dp, leadingGap: Boolean = true) {
    if (!row.recommended && !row.duplicate) return
    val colors = Guga.colors
    Row(modifier.padding(start = if (leadingGap) gap else 0.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        if (row.recommended) OutlineTag("推荐", size, line, if (onInk) GugaColors.SEAL_ON_INK else colors.spot, Modifier.testTag("source-tag-recommended"))
        if (row.duplicate) OutlineTag("另一份文件", size, line, if (onInk) colors.onInk.copy(alpha = 0.72f) else colors.dim, Modifier.testTag("source-tag-duplicate"))
    }
}

/**
 * 行前的小圆点（列表里有别台的源时每行都有）：在线 = 铜绿（主题 `online`，和换服务器列表同一个点）；连不上 / 要重新登录 = 灰。
 * [onInk]：这一行此刻是米白实底——灰点换墨色的灰，绿点照旧。没有服务器名的行（只有本台）什么都不画。
 */
@Composable
fun SourceDot(row: SourceRowText, size: Dp, modifier: Modifier = Modifier, onInk: Boolean = false, gap: Dp = size) {
    if (row.server == null) return
    val colors = Guga.colors
    val ink = if (onInk) colors.onInk else colors.ink
    Box(modifier.padding(end = gap).size(size).background(if (row.offline) ink.copy(alpha = 0.32f) else colors.online, CircleShape).testTag(if (row.offline) "source-dot-off" else "source-dot"))
}

/**
 * 列表底下的进展（四处共用：手机纸片、电脑下拉、电视页边、播放器面板）：还在找的每台一行「正在问 香港中转…」，前面一个淡点轻轻呼吸
 * （和换服务器列表「正在检查」同一种；减弱动效时不动）；有结论没贡献源的几台并成一行。没有要说的 = 什么都不画（不占地方）。
 * 只往列表最后加行、不用转圈，已有的行不会被挤动。
 */
@Composable
fun SourceProgress(state: SourcesState, text: TextUnit, dot: Dp, modifier: Modifier = Modifier, lineGap: Dp = 4.dp) {
    val p = state.progressText
    if (p.searching.isEmpty() && p.summary == null) return
    val colors = Guga.colors
    val breathing = p.searching.isNotEmpty() && !LocalMotionPrefs.current.reducedMotion
    val pulse = if (breathing) {
        val t = rememberInfiniteTransition(label = "source-search-pulse")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "source-search-alpha").value
    } else 1f
    Column(modifier.testTag("source-progress"), verticalArrangement = Arrangement.spacedBy(lineGap)) {
        for (line in p.searching) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.padding(end = dot).size(dot).alpha(pulse).background(colors.ink.copy(alpha = 0.5f), CircleShape))
                BasicText(line, Modifier.testTag("source-searching"), style = Guga.song(text, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        p.summary?.let { BasicText(it, Modifier.testTag("source-missed"), style = Guga.song(text, color = colors.faint), maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}
