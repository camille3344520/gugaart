package com.gugaart.ui.download

import com.gugugaga.app.player.PlaybackReporting
import com.gugugaga.app.player.PlayerClient
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.PlayMethod
import com.gugugaga.core.PlaybackCapsPatch
import com.gugugaga.core.PlaybackInfoResponse
import com.gugugaga.core.PlaybackMode
import com.gugugaga.core.PlaybackPrefs
import com.gugugaga.core.PlaybackState
import com.gugugaga.core.ResolveContext
import com.gugugaga.core.ResolvedCandidate
import com.gugugaga.core.ResolvedPlayback
import com.gugugaga.core.UrlContext
import com.gugugaga.core.UserItemDataDto
import com.gugugaga.core.pickMediaSource
import com.gugugaga.core.resolvePlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/*
 * 放下好的文件（1.6 批次 E）：上游播放器只认 [PlayerClient] 这一层（测试缝），这里包一层——
 * 下好了的条目：协商 = 本地文件（`file:///…`，三个内核都能放），回报 = 先记本地（[ReportLog]），联网后按顺序报给 Emby；
 * 没下的条目原样交给里面那一层（换集到没下的那一集 = 照常在线放）。
 * 条目本身（片名、媒体流、续看位置）先问服务器，问不到（断网）用下载时存下的那一份，续看位置用离线时记下的。
 */

/** 本地播放的 PlaySessionId 前缀（停转码之类的服务器调用看到它就不发）。 */
const val OFFLINE_SESSION_PREFIX = "gugaart-offline-"

/** 断网时问服务器最多等多久（断网一般马上失败；网很差时别让开播卡住）。 */
private const val ONLINE_PROBE_MS = 4_000L

/** 本地播放每隔多久记一次位置。 */
const val OFFLINE_HEARTBEAT_MS = 10_000L

/**
 * 纯函数：用下载时存下的条目 [item] 和本地文件地址 [fileUrl] 拼出一个「直接播放」的协商结果。
 * 外挂字幕（服务器上的 .srt / .ass）不在文件里，断网拿不到：拿掉；内嵌字幕一律当内嵌（内核自己选轨）。
 */
fun offlineResolve(item: BaseItemDto, prefs: PlaybackPrefs, fileUrl: String, mediaSourceId: String?, sessionId: String): ResolvedPlayback {
    val src0 = pickMediaSource(item.mediaSources, mediaSourceId ?: prefs.mediaSourceId)
        ?: MediaSourceInfo(id = mediaSourceId ?: item.id, runTimeTicks = item.runTimeTicks, container = item.container)
    val source = src0.copy(
        protocol = "File",
        isRemote = false,
        supportsDirectPlay = true,
        supportsDirectStream = false,
        supportsTranscoding = false,
        transcodingUrl = null,
        directStreamUrl = null,
        requiredHttpHeaders = emptyMap(),
        mediaStreams = src0.mediaStreams
            .filter { !(it.type == "Subtitle" && it.isExternal == true) }
            .map { if (it.type == "Subtitle") it.copy(deliveryMethod = "Embed", deliveryUrl = null) else it },
    )
    val p = prefs.copy(mediaSourceId = source.id, forceTranscode = false, directOnly = false, preferStrmTarget = false)
    val r = resolvePlayback(
        PlaybackInfoResponse(mediaSources = listOf(source), playSessionId = sessionId),
        p,
        ResolveContext(UrlContext("http://offline.invalid")),
    )
    return r.copy(
        mode = PlaybackMode.Direct,
        playMethod = PlayMethod.DirectPlay,
        url = fileUrl,
        headers = emptyMap(),
        isRemote = false,
        remoteUrl = null,
        remoteHeaders = null,
        strmTarget = null,
        candidates = listOf(ResolvedCandidate("emby", fileUrl, emptyMap())),
        accelerate = false,
        isHls = false,
        externalSubtitles = emptyList(),
        transcodeReasons = emptyList(),
        synthesizedUrl = false,
    )
}

/** 存下的条目，续看位置换成离线时记下的那个（[DownloadEntry.positionTicks]；没有就用存下时服务器给的）。 */
fun storedItemWithPosition(item: BaseItemDto, e: DownloadEntry): BaseItemDto {
    val pos = e.positionTicks ?: return item
    return item.copy(userData = (item.userData ?: UserItemDataDto()).copy(playbackPositionTicks = pos))
}

/**
 * 本地放的那一条：记位置（心跳覆盖、停下一条），不连服务器。
 * 没等到「停下」的（进程被杀、内核收尾卡住）：下一次本地播放开始或者应用重开时，按最后记下的位置补一条停下（[ReportLog.closeOpen]）。
 */
private class LocalReporter(
    private val scope: CoroutineScope,
    private val resolved: ResolvedPlayback,
    private val getState: () -> PlaybackState,
    private val serverId: String,
    private val userId: String,
) : PlaybackReporting {
    private val session = resolved.playSessionId ?: (OFFLINE_SESSION_PREFIX + resolved.itemId)
    private var heartbeat: Job? = null

    private fun record(stopped: Boolean, positionMs: Long?) {
        val s = runCatching { getState() }.getOrNull()
        val ms = positionMs ?: s?.positionMs ?: 0L
        Downloads.recordPosition(
            PendingReport(serverId, userId, resolved.itemId, resolved.mediaSource.id, ms.coerceAtLeast(0) * 10_000, s?.paused == true, stopped, session, Downloads.now()),
        )
    }

    override suspend fun start() {
        Downloads.log("本地播放开始：" + resolved.itemId)
        record(stopped = false, positionMs = null)
        heartbeat?.cancel()
        // 心跳跑在下载自己的后台作用域上，不跟播放页的主线程走（模拟器上 VLC 放着时页面的协程会停住，心跳一次都没记上）
        heartbeat = Downloads.background.launch {
            while (isActive) {
                delay(OFFLINE_HEARTBEAT_MS)
                record(stopped = false, positionMs = null)
            }
        }
    }

    override suspend fun pause() = record(stopped = false, positionMs = null)

    override suspend fun resume() = record(stopped = false, positionMs = null)

    override suspend fun stop(failed: Boolean, positionMs: Long?) {
        heartbeat?.cancel()
        heartbeat = null
        record(stopped = true, positionMs = positionMs)
    }
}

/**
 * 包在播放器的 Emby 那一层外面（`player/PlaySession.kt`）。[serverId] / [userId] = 正在用的会话：只认这台服务器这个账号下好的文件。
 */
class DownloadedPlayerClient(
    private val inner: PlayerClient,
    private val serverId: String,
    private val userId: String,
) : PlayerClient by inner {
    private fun local(itemId: String): DownloadEntry? = Downloads.playable(serverId, userId, itemId)

    override suspend fun getItem(itemId: String): BaseItemDto {
        val e = local(itemId) ?: return inner.getItem(itemId)
        val stored = Downloads.storedItem(e)
        val online = try {
            withTimeoutOrNull(ONLINE_PROBE_MS) { inner.getItem(itemId) }
        } catch (x: CancellationException) {
            throw x
        } catch (_: Exception) {
            null
        }
        // 联网了但离线时看的进度还没报上去：续看位置以本地的为准
        return when {
            online == null -> stored?.let { storedItemWithPosition(it, e) } ?: inner.getItem(itemId)
            Downloads.hasPending(itemId) -> storedItemWithPosition(online, e)
            else -> online
        }
    }

    override suspend fun preparePlayback(prefs: PlaybackPrefs, caps: PlaybackCapsPatch?): ResolvedPlayback {
        val e = local(prefs.itemId) ?: return inner.preparePlayback(prefs, caps)
        val item = Downloads.storedItem(e) ?: BaseItemDto(id = e.itemId, name = e.name)
        return offlineResolve(item, prefs, Downloads.fileUrlOf(e) ?: return inner.preparePlayback(prefs, caps), e.mediaSourceId, OFFLINE_SESSION_PREFIX + Downloads.now())
    }

    override fun resolveUnprobedStrm(item: BaseItemDto, prefs: PlaybackPrefs, caps: PlaybackCapsPatch?): ResolvedPlayback? =
        if (local(item.id) != null) null else inner.resolveUnprobedStrm(item, prefs, caps)

    override fun createReporter(scope: CoroutineScope, resolved: ResolvedPlayback, getState: () -> PlaybackState): PlaybackReporting =
        if (resolved.url.startsWith("file:")) LocalReporter(scope, resolved, getState, serverId, userId) else inner.createReporter(scope, resolved, getState)

    override suspend fun stopTranscoding(playSessionId: String) {
        if (!playSessionId.startsWith(OFFLINE_SESSION_PREFIX)) inner.stopTranscoding(playSessionId)
    }

    override suspend fun getEpisodes(seriesId: String): List<BaseItemDto> {
        val mine = Downloads.downloadedEpisodes(serverId, userId, seriesId)
        if (mine.isEmpty()) return inner.getEpisodes(seriesId)
        return try {
            withTimeoutOrNull(ONLINE_PROBE_MS) { inner.getEpisodes(seriesId) } ?: mine
        } catch (x: CancellationException) {
            throw x
        } catch (_: Exception) {
            mine
        }
    }
}
