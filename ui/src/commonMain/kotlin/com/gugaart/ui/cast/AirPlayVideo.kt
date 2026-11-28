package com.gugaart.ui.cast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugugaga.app.player.HandOffSource
import com.gugugaga.app.player.HandOffTarget
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/*
 * AirPlay video on the iPhone (1.6 F2; upstream U-ios-11: PlayController.prepareHandOff / beginHandOff / endHandOff, D-ios-16).
 *  - mpv renders on the phone only, it cannot send video over AirPlay. When the system routes to an AirPlay device (the top bar's
 *    AirPlay button, Control Center), the item is handed to the system's AVPlayer on that route: upstream asks the server again with
 *    the AirPlay caps (Emby direct stream / HLS transcode; a strm target with its own headers), from where the phone was; mpv pauses.
 *  - Play / pause / seek / the progress report stay with the page's controller (upstream forwards them to the AVPlayer); the picture
 *    area shows 「正在投屏」.
 *  - Back: 「停止」, or the system routes back to the phone -> mpv goes on from where the AVPlayer got to.
 *  - A sound-only AirPlay route (a speaker): the AVPlayer never goes external -> back to the picture here; the sound keeps
 *    following the system route.
 *  - No messages: a hand-off that did not happen leaves the picture on the phone ([lastProblem] is for diagnosis / acceptance).
 */

/** The system half (shell-ios: AVAudioSession's route + AVPlayer). */
interface AirPlayHost {
    /** The AirPlay device the system plays to right now (its name); null = this device. Changes on the main thread. */
    val route: StateFlow<String?>

    /** An AVPlayer on that route: [source] from [startMs], paused until [AirPlayPlayer.play]. Main thread. */
    fun open(source: HandOffSource, startMs: Long, title: String): AirPlayPlayer
}

/** An AVPlayer on the AirPlay route (the controller drives it as its [HandOffTarget]). */
interface AirPlayPlayer : HandOffTarget {
    fun status(): AirPlayStatus

    fun close()
}

/** The AVPlayer now: [ready] can play; [failed] cannot open / broke off; [ended] played to the end; [external] the picture really is on the other device. */
data class AirPlayStatus(
    val ready: Boolean = false,
    val failed: Boolean = false,
    val ended: Boolean = false,
    val external: Boolean = false,
    /** Diagnosis: what the AVPlayer says (status, error). */
    val detail: String? = null,
)

/** What the picture area on the phone shows. */
enum class AirPlayPhase { Connecting, Playing }

data class AirPlayCast(val device: String, val title: String, val phase: AirPlayPhase)

/** What one tick of the hand-off does ([airPlayStep]). */
enum class AirPlayAction { None, Start, Stop, Ended, Failed, AudioOnly }

/**
 * One look at the route and the AVPlayer per tick. Pure, unit-tested.
 * - Not casting: the route is an AirPlay device, not the one just given up, and this device is playing (Ready) -> start.
 * - Casting: the route is back on this device -> stop; the AVPlayer broke off / ended; no external playback after
 *   [AIRPLAY_EXTERNAL_WAIT_MS] (a sound-only route) -> back here.
 */
fun airPlayStep(
    route: String?,
    declined: String?,
    active: Boolean,
    ready: Boolean,
    status: AirPlayStatus?,
    playingForMs: Long,
    trustRoute: Boolean,
): AirPlayAction = when {
    !active -> if (route != null && route != declined && ready) AirPlayAction.Start else AirPlayAction.None
    route == null -> AirPlayAction.Stop
    status == null -> AirPlayAction.None
    status.failed -> AirPlayAction.Failed
    status.ended -> AirPlayAction.Ended
    !status.external && !trustRoute && playingForMs >= AIRPLAY_EXTERNAL_WAIT_MS -> AirPlayAction.AudioOnly
    else -> AirPlayAction.None
}

/** The device given up on is forgotten once the route changes (back here / another device). Pure, unit-tested. */
fun airPlayDeclined(route: String?, declined: String?): String? = declined?.takeIf { it == route }

/** The line under the title. Pure, unit-tested. */
fun airPlayWord(cast: AirPlayCast): String = when (cast.phase) {
    AirPlayPhase.Connecting -> "正在连接"
    AirPlayPhase.Playing -> "正在投屏"
}

/**
 * AVPlayer's timeControlStatus (0 paused, 1 waiting to play, 2 playing) as "paused": waiting is not (it plays as soon as it can);
 * an unknown value goes by the rate. Pure, unit-tested.
 */
fun avPlayerPaused(timeControlStatus: Long, rate: Float): Boolean = when (timeControlStatus) {
    0L -> true
    1L, 2L -> false
    else -> rate == 0f
}

/** How long after the start the AVPlayer may take to go external (a sound-only route never does). */
const val AIRPLAY_EXTERNAL_WAIT_MS = 8_000L

/** How long one link may take to become ready to play. */
const val AIRPLAY_OPEN_WAIT_MS = 15_000L

object AirPlayVideo {
    /** The system half the shell installs (iOS); null = no AirPlay hand-off on this platform. */
    var host: AirPlayHost? = null

    /** Acceptance (no AirPlay device on a simulator): take the route as a video route without waiting for external playback. */
    var trustRoute = false

    /** The hand-off in progress (connecting or casting); null = the picture is on the phone. */
    var cast by mutableStateOf<AirPlayCast?>(null)
        private set

    /** Diagnosis / acceptance: why the last hand-off did not happen / came back. */
    var lastProblem: String? = null
        private set

    private var owner: PlayController? = null
    private var player: AirPlayPlayer? = null
    private var opening: AirPlayPlayer? = null
    private var declined: String? = null

    /** The item that played to its end there: not handed off again while the route stays (the next episode is). */
    private var endedItem: String? = null
    private var playingSince = 0L
    private var now: () -> Long = { 0L }

    /** The hand-off of [controller]'s page, if any. */
    fun castFor(controller: PlayController): AirPlayCast? = cast?.takeIf { owner === controller }

    /**
     * The player page's part: follows the system route until the page goes (the coroutine is cancelled). [clock] = a millisecond clock.
     * When the page goes the AVPlayer closes; the controller disposes itself (its report ends where the AVPlayer got to).
     */
    suspend fun watch(controller: PlayController, clock: () -> Long) {
        val h = host ?: return
        now = clock
        try {
            while (true) {
                step(h, controller)
                delay(POLL_MS)
            }
        } finally {
            withContext(NonCancellable) {
                if (owner === controller || owner == null) drop()
            }
        }
    }

    /** The system route picker closed (the user just chose): forget the device given up on, so choosing it again hands off again. */
    fun pickerClosed() {
        declined = null
    }

    /** 「停止」: the picture comes back, mpv goes on from where the AVPlayer got to (paused there = paused here). This route is not handed off again by itself. */
    fun stop(controller: PlayController) {
        if (owner !== controller) return
        declined = host?.route?.value
        end(controller, play = null)
    }

    private suspend fun step(h: AirPlayHost, c: PlayController) {
        val route = h.route.value
        declined = airPlayDeclined(route, declined)
        val active = owner === c && player != null
        val snap = c.value
        val ready = snap.phase == PlayPhase.Ready && snap.switching == null && !c.handingOff
        val status = if (active) runCatching { player?.status() }.getOrNull() else null
        if (endedItem != null && endedItem != c.currentItemId) endedItem = null
        val skip = if (endedItem != null) route else declined
        when (airPlayStep(route, skip, active, ready, status, now() - playingSince, trustRoute)) {
            AirPlayAction.None -> Unit
            AirPlayAction.Start -> start(h, c, route!!)
            AirPlayAction.Stop -> end(c, play = null)
            AirPlayAction.Ended -> {
                // played to the end: mpv goes to the end and plays, so the end of the item works as here (next episode / end page); the next episode is handed off again
                endedItem = c.currentItemId
                end(c, play = true)
            }
            AirPlayAction.Failed -> {
                lastProblem = "the AVPlayer broke off"
                declined = route
                end(c, play = null)
            }
            AirPlayAction.AudioOnly -> {
                lastProblem = "a sound-only AirPlay route (no external playback)"
                declined = route
                end(c, play = null)
            }
        }
    }

    private suspend fun start(h: AirPlayHost, c: PlayController, route: String) {
        lastProblem = null
        owner = c
        cast = AirPlayCast(route, c.value.title, AirPlayPhase.Connecting)
        val stream = try {
            c.prepareHandOff()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastProblem = "no stream for the hand-off: ${e.message}"
            declined = route
            owner = null
            cast = null
            c.endHandOff(play = true)
            return
        }
        // 「停止」 while the server was asked: drop the stream, the picture stays here
        if (owner !== c) {
            c.endHandOff(play = true)
            return
        }
        var picked: AirPlayPlayer? = null
        val tried = ArrayList<String>()
        for (src in stream.sources) {
            val p = h.open(src, stream.startMs, stream.title)
            opening = p
            var seen: AirPlayStatus? = null
            val ok = kotlinx.coroutines.withTimeoutOrNull(AIRPLAY_OPEN_WAIT_MS) {
                while (true) {
                    val s = p.status()
                    seen = s
                    if (s.ready) return@withTimeoutOrNull true
                    if (s.failed) return@withTimeoutOrNull false
                    delay(100)
                }
                @Suppress("UNREACHABLE_CODE")
                false
            } == true
            opening = null
            // 「停止」 while connecting: this hand-off is over already
            if (owner !== c) {
                p.close()
                return
            }
            if (ok) {
                picked = p
                break
            }
            tried += "${src.kind}: ${seen?.detail ?: "no answer"}"
            p.close()
        }
        if (picked == null) {
            lastProblem = "the AVPlayer cannot open the stream (${tried.joinToString("; ")})"
            declined = route
            owner = null
            cast = null
            c.endHandOff(play = true)
            return
        }
        player = picked
        playingSince = now()
        c.beginHandOff(picked)
        picked.play()
        cast = AirPlayCast(route, stream.title, AirPlayPhase.Playing)
    }

    private fun end(c: PlayController, play: Boolean?) {
        val p = player
        player = null
        owner = null
        cast = null
        c.endHandOff(play)
        p?.close()
    }

    /** The page went: close the AVPlayer (the page disposes the controller). */
    private fun drop() {
        opening?.close()
        opening = null
        player?.close()
        player = null
        owner = null
        cast = null
    }

    private const val POLL_MS = 500L
}
