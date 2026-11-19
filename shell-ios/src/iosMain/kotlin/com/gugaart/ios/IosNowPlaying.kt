package com.gugaart.ios

import com.gugugaga.app.player.ClockSnapshot
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlayerSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoMediaTypeVideo
import platform.MediaPlayer.MPNowPlayingInfoPropertyDefaultPlaybackRate
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyMediaType
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import kotlin.math.abs
import kotlin.math.floor

/*
 * iOS 的「正在播放」（M5-4）：播放器的状态报给 MPNowPlayingInfoCenter（锁屏 / 控制中心的卡片），MPRemoteCommandCenter 的命令
 * （播放 / 暂停 / 上下一集 / 拖动）接回 PlayController。规则和 macOS 的 MacNowPlaying（MP-6）一样：位置只在和系统自己推的差出
 * 1.5 秒时再报；暂停 / 缓冲时速率 0；离开播放器卡片消失、命令摘掉。封面：这一条的海报（[artworkUrl]），取到了补报一次（同 macOS）。
 */

/** 要报给系统的那几样（同 macOS 的 NowPlayingState）。 */
internal data class IosNowPlayingState(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val durationMs: Double,
    val positionMs: Double,
    val paused: Boolean,
    val buffering: Boolean,
    val rate: Double,
    val canNext: Boolean,
    val canPrev: Boolean,
) {
    /** 系统眼里现在走多快：暂停 / 缓冲 = 0（时间不走）。 */
    val effectiveRate: Double get() = if (paused || buffering) 0.0 else rate

    companion object {
        const val DRIFT_MS = 1_500.0

        /** 控制器的两条状态流 → 要报的状态；还没有条目 / 出错 = null（不报）。纯函数，有单测。 */
        fun of(snap: PlayerSnapshot, clock: ClockSnapshot): IosNowPlayingState? {
            if (snap.itemId.isEmpty() || snap.title.isEmpty() || snap.phase == PlayPhase.Error) return null
            return IosNowPlayingState(snap.itemId, snap.title, snap.subtitle, clock.durationMs, clock.positionMs, clock.paused, clock.buffering, clock.rate, snap.next != null, snap.prev != null)
        }

        /** 上次报的是 [last]（在 [lastAtMs]）；位置之外有变化、或者位置和系统自己推的对不上 → 再报。纯函数，有单测。 */
        fun needsPublish(last: IosNowPlayingState?, lastAtMs: Long, now: IosNowPlayingState, nowMs: Long): Boolean {
            if (last == null) return true
            if (last.copy(positionMs = 0.0) != now.copy(positionMs = 0.0)) return true
            val expected = last.positionMs + (nowMs - lastAtMs) * last.effectiveRate
            return abs(now.positionMs - expected) > DRIFT_MS
        }
    }
}

internal object IosNowPlaying {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var attached: PlayController? = null
    private var job: Job? = null

    /** 这一条的海报地址（壳给：Emby 的 Primary 图，600 宽 JPG）；null = 不报封面。 */
    var artworkUrl: ((com.gugugaga.app.player.PlayerSnapshot) -> String?)? = null

    /** 现在报着的封面（诊断 / 验收：锁屏卡片有没有图）。 */
    var artwork: MPMediaItemArtwork? = null
        private set

    private fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

    /** 播放器页建好控制器就交过来（每次重组都会叫，同一个控制器只接一次）。 */
    fun attach(controller: PlayController) {
        if (attached === controller) return
        detach()
        attached = controller
        val cc = MPRemoteCommandCenter.sharedCommandCenter()
        fun mine(block: () -> Unit): Long {
            if (attached === controller) block()
            return MPRemoteCommandHandlerStatusSuccess
        }
        cc.playCommand.addTargetWithHandler { mine { controller.play() } }
        cc.pauseCommand.addTargetWithHandler { mine { controller.pause() } }
        cc.togglePlayPauseCommand.addTargetWithHandler { mine { controller.togglePlay() } }
        cc.nextTrackCommand.addTargetWithHandler { mine { controller.playNext() } }
        cc.previousTrackCommand.addTargetWithHandler { mine { controller.playPrev() } }
        cc.changePlaybackPositionCommand.addTargetWithHandler { ev ->
            mine { (ev as? MPChangePlaybackPositionCommandEvent)?.let { controller.seekTo(it.positionTime * 1000.0) } }
        }
        job = scope.launch {
            var last: IosNowPlayingState? = null
            var lastAt = 0L
            var artFor: String? = null
            var artJob: Job? = null
            combine(controller.snapshot, controller.clock) { s, c -> s to c }.conflate().collect { (snap, clock) ->
                val state = IosNowPlayingState.of(snap, clock) ?: return@collect
                if (artFor != state.itemId) {
                    // 换了一条：旧封面不留；新的取到了再补报一次
                    artFor = state.itemId
                    artwork = null
                    artJob?.cancel()
                    val url = runCatching { artworkUrl?.invoke(snap) }.getOrNull()
                    if (url != null) artJob = launch {
                        val image = withContext(Dispatchers.Default) {
                            NSURL.URLWithString(url)?.let { NSData.dataWithContentsOfURL(it) }?.let { UIImage.imageWithData(it) }
                        } ?: return@launch
                        if (artFor != state.itemId) return@launch
                        artwork = MPMediaItemArtwork(boundsSize = image.size, requestHandler = { _ -> image })
                        last?.let {
                            val again = it.copy(positionMs = controller.clock.value.positionMs)
                            last = again
                            lastAt = nowMs()
                            publish(again)
                        }
                    }
                }
                val t = nowMs()
                if (IosNowPlayingState.needsPublish(last, lastAt, state, t)) {
                    last = state
                    lastAt = t
                    publish(state)
                }
            }
        }
    }

    private fun publish(s: IosNowPlayingState) {
        val cc = MPRemoteCommandCenter.sharedCommandCenter()
        cc.nextTrackCommand.enabled = s.canNext
        cc.previousTrackCommand.enabled = s.canPrev
        val info = HashMap<Any?, Any?>()
        info[MPMediaItemPropertyTitle] = s.title
        if (s.subtitle.isNotEmpty()) info[MPMediaItemPropertyArtist] = s.subtitle
        if (s.durationMs > 0) info[MPMediaItemPropertyPlaybackDuration] = NSNumber(s.durationMs / 1000.0)
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = NSNumber(floor(s.positionMs.coerceAtLeast(0.0) / 1000.0))
        info[MPNowPlayingInfoPropertyPlaybackRate] = NSNumber(s.effectiveRate)
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = NSNumber(s.rate)
        info[MPNowPlayingInfoPropertyMediaType] = NSNumber(unsignedInteger = MPNowPlayingInfoMediaTypeVideo)
        artwork?.let { info[MPMediaItemPropertyArtwork] = it }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
    }

    /** 离开播放器：卡片消失、命令摘掉。 */
    fun detach() {
        if (attached == null) return
        attached = null
        job?.cancel()
        job = null
        artwork = null
        val cc = MPRemoteCommandCenter.sharedCommandCenter()
        for (c in listOf(cc.playCommand, cc.pauseCommand, cc.togglePlayPauseCommand, cc.nextTrackCommand, cc.previousTrackCommand, cc.changePlaybackPositionCommand)) c.removeTarget(null)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }
}
