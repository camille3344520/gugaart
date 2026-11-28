package com.gugaart.ios

import com.gugaart.ui.cast.AirPlayHost
import com.gugaart.ui.cast.AirPlayPlayer
import com.gugaart.ui.cast.AirPlayStatus
import com.gugaart.ui.cast.avPlayerPaused
import com.gugugaga.app.player.HandOffClock
import com.gugugaga.app.player.HandOffSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortAirPlay
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.currentRoute
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.allowsExternalPlayback
import platform.AVFoundation.usesExternalPlaybackWhileExternalScreenIsActive
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.externalPlaybackActive
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

/*
 * The system half of the AirPlay hand-off (iPhone, 1.6 F2; the flow and the screen are :ui's cast/AirPlayVideo.kt):
 *  - route: an AirPlay port among AVAudioSession's current outputs = the system plays to that device (port name = device name);
 *    read again on every route change (the AirPlay button, Control Center);
 *  - playback: an AVPlayer without a layer (external playback allowed) on that route. The link is the one upstream asked for the
 *    AirPlay caps (Emby direct stream / HLS transcode, carrying api_key); a strm target goes with its own headers (AVURLAsset's
 *    HTTP header option). With real AirPlay video externalPlaybackActive turns true.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosAirPlay : AirPlayHost {
    private val routeNow = MutableStateFlow<String?>(null)
    override val route: StateFlow<String?> = routeNow
    private var started = false

    /** Acceptance (a simulator has no AirPlay device): act as if the system routed to this device; null = the real route. */
    var fakeRoute: String? = null
        set(v) {
            field = v
            refresh()
        }

    fun start() {
        if (started) return
        started = true
        NSNotificationCenter.defaultCenter.addObserverForName(AVAudioSessionRouteChangeNotification, null, NSOperationQueue.mainQueue) { _ -> refresh() }
        refresh()
    }

    private fun refresh() {
        routeNow.value = fakeRoute ?: airPlayOutput()
    }

    /** The AirPlay port's name in the current route (none = null). */
    private fun airPlayOutput(): String? = runCatching {
        AVAudioSession.sharedInstance().currentRoute.outputs
            .mapNotNull { it as? AVAudioSessionPortDescription }
            .firstOrNull { it.portType == AVAudioSessionPortAirPlay }
            ?.portName?.takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun open(source: HandOffSource, startMs: Long, title: String): AirPlayPlayer = IosAirPlayPlayer(source, startMs)
}

@OptIn(ExperimentalForeignApi::class)
private class IosAirPlayPlayer(source: HandOffSource, private val startMs: Long) : AirPlayPlayer {
    private val item: AVPlayerItem
    private val player: AVPlayer
    private val observers = ArrayList<NSObjectProtocol>()
    private var ended = false
    private var failedToEnd = false
    private var seekAsked = false
    private var seekDone = startMs <= 0
    private var closed = false
    private val where = source.url.substringBefore('?')

    /** Asked after close (the controller's last report when the page goes): the clock at close, never 0. */
    private var last: HandOffClock? = null

    init {
        val url = NSURL.URLWithString(source.url) ?: NSURL.URLWithString("about:blank")!!
        // a strm target carries only its own headers (UA / Referer..., never the Emby token); Emby's link carries api_key itself
        val options: Map<Any?, Any?>? = source.headers.takeIf { it.isNotEmpty() }?.let { mapOf("AVURLAssetHTTPHeaderFieldsKey" to it) }
        item = AVPlayerItem(asset = AVURLAsset(uRL = url, options = options))
        player = AVPlayer(playerItem = item)
        player.allowsExternalPlayback = true
        player.usesExternalPlaybackWhileExternalScreenIsActive = true
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        observers += center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, item, main) { _ -> ended = true }
        observers += center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, item, main) { _ -> failedToEnd = true }
    }

    override fun status(): AirPlayStatus {
        val itemReady = item.status == AVPlayerItemStatusReadyToPlay
        // from where the phone was: seeking before the item is ready to play throws
        if (itemReady && !seekAsked && !seekDone) {
            seekAsked = true
            player.seekToTime(CMTimeMakeWithSeconds(startMs / 1000.0, 600), toleranceBefore = CMTimeMakeWithSeconds(0.5, 600), toleranceAfter = CMTimeMakeWithSeconds(0.5, 600)) { _ -> seekDone = true }
        }
        // the seek's completion did not come but the position is there: ready too
        if (itemReady && seekAsked && !seekDone && CMTimeGetSeconds(player.currentTime()).let { it.isFinite() && it >= startMs / 1000.0 - 1 && it > 0 }) seekDone = true
        val err = item.error ?: player.error
        return AirPlayStatus(
            ready = itemReady && seekDone,
            failed = failedToEnd || item.status == AVPlayerItemStatusFailed || player.status == AVPlayerStatusFailed,
            ended = ended,
            external = player.externalPlaybackActive,
            detail = "item=${item.status} player=${player.status} seek=$seekAsked/$seekDone ${where.substringAfterLast('/')}" + (err?.let { " · ${it.domain} ${it.code} ${it.localizedDescription}" } ?: ""),
        )
    }

    override fun clock(): HandOffClock {
        if (closed) return last ?: HandOffClock(startMs.toDouble(), paused = true)
        val pos = CMTimeGetSeconds(player.currentTime()).takeIf { it.isFinite() && seekDone }?.let { it * 1000 } ?: startMs.toDouble()
        val dur = CMTimeGetSeconds(item.duration).takeIf { it.isFinite() && it > 0 }?.let { it * 1000 } ?: 0.0
        val status = player.timeControlStatus
        return HandOffClock(pos, dur, paused = avPlayerPaused(status, player.rate), buffering = status == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate).also { last = it }
    }

    override fun play() {
        if (closed) return
        ended = false
        player.play()
    }

    override fun pause() {
        if (!closed) player.pause()
    }

    override fun seekTo(ms: Double) {
        if (closed) return
        ended = false
        player.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, 600))
    }

    override fun close() {
        if (closed) return
        last = clock().copy(paused = true)
        closed = true
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        for (o in observers) NSNotificationCenter.defaultCenter.removeObserver(o)
        observers.clear()
    }
}
