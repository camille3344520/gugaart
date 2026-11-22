/*
 * 复制自上游壳（docs/adr/0002-player-wiring-in-repo.md §6，登记在 docs/upstream-sync.md §5.1）：
 *   来源  gugugagaemby/native/desktopApp/src/main/kotlin/com/gugugaga/desktop/DesktopPlayer.kt
 *   提交  1e19741a（upstream.lock）
 *   改动  package；macOS（ADR 0004 §2.7）：shortcutKeys / keepAwake 的 Win32 调用包 Platform.isWindows() 守卫（M1-3 加的 MpvHost.sessionDeps 参数已在 MP-5 还原）
 * 不要在这里改行为：上游改了来源文件就把同样的改动搬过来（git -C <上游> diff <lock>..HEAD -- <来源文件>）。
 */
package com.gugaart.desktop.upstream

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.gugugaga.app.player.ExternalPlayerInfo
import com.gugugaga.app.player.ExternalPlayers
import com.gugugaga.app.player.FrameSnapshot
import com.gugugaga.app.player.NativeCommand
import com.gugugaga.app.player.NativeCommandException
import com.gugugaga.app.player.NativeEngine
import com.gugugaga.app.player.NativePlayerBackend
import com.gugugaga.app.player.PlatformFacts
import com.gugugaga.app.player.PlatformKind
import com.gugugaga.app.player.PlayerClock
import com.gugugaga.app.player.PlayerEngine
import com.gugugaga.app.player.PlayerEvent
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.app.player.PlayerTime
import com.gugugaga.app.player.PlayerTrack
import com.gugugaga.app.player.PlayerTracks
import com.gugugaga.app.player.VideoSize
import com.gugugaga.core.EngineId
import com.gugugaga.desktop.mpv.DefaultMpvLocateEnv
import com.gugugaga.desktop.mpv.ExternalSubtitle
import com.gugugaga.desktop.mpv.MpvEngineConfig
import com.gugugaga.desktop.mpv.MpvEvent
import com.gugugaga.desktop.mpv.MpvException
import com.gugugaga.desktop.mpv.MpvLoadOptions
import com.gugugaga.desktop.mpv.MpvTrack
import com.gugugaga.desktop.mpv.MpvTransportCommand
import com.gugugaga.desktop.mpv.MpvWindowsEngine
import com.gugugaga.desktop.mpv.VideoHostLike
import com.gugugaga.desktop.mpv.Viewport
import com.gugugaga.desktop.mpv.locateMpv
import com.gugugaga.desktop.vlc.VlcEngineConfig
import com.gugugaga.desktop.vlc.VlcLocation
import com.gugugaga.desktop.vlc.VlcWindowsEngine
import com.gugugaga.desktop.vlc.locateVlc
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.HWND
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/**
 * Chrome-like User-Agent for mpv when the request carries none (docs/desktop.md §5.2: network-disk CDNs reject `libmpv`).
 * The Electron shell sends its Chromium UA; the native app sends this fixed one.
 */
const val DEFAULT_MPV_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

/**
 * The ONE mpv engine of the app (one mpv.exe at most, started on the first `load`, never without `--wid`), rendering into
 * the [VideoHostLike] of the window stack, plus the fan-out of its events to whichever [MpvBackend] is attached (the
 * player page's engine). Clock samples are stamped with [PlayerTime] (monotonic, same process: no wall-clock maths).
 */
class MpvHost(host: () -> VideoHostLike?, mpvPath: String? = null, extraArgs: List<String> = emptyList()) {
    private val listeners = CopyOnWriteArrayList<(MpvEvent) -> Unit>()
    @Volatile var background: String = "#0a0a12"
        private set

    /** Every event, for diagnostics / the e2e (`System.nanoTime()` stamped). */
    val tap = CopyOnWriteArrayList<(MpvEvent) -> Unit>()

    val engine = MpvWindowsEngine(
        MpvEngineConfig(
            host = host,
            locate = { locateMpv(mpvPath, DefaultMpvLocateEnv) },
            background = { background },
            emit = { e ->
                for (t in tap) t(e)
                for (l in listeners) l(e)
            },
            extraArgs = extraArgs,
            defaultUserAgent = DEFAULT_MPV_USER_AGENT,
            wallClock = { PlayerTime.now().toLong() },
            // diagnostics: -Dgugu.mpv.log=<file> makes mpv write its verbose log there (startup analysis, docs/native-log-perf.md)
            logFile = System.getProperty("gugu.mpv.log")?.takeIf { it.isNotBlank() },
        ),
    )

    /** Is there an mpv.exe to embed? (cheap: file checks only) */
    fun available(): Boolean = locateMpv(null, DefaultMpvLocateEnv) != null

    fun subscribe(l: (MpvEvent) -> Unit): () -> Unit {
        listeners += l
        return { listeners -= l }
    }

    /** The neutral colour mpv paints where no frame is shown: follows the app theme like the stage (docs/desktop.md §3). */
    suspend fun setBackground(hex: String) {
        if (hex == background) return
        background = hex
        engine.setBackground(hex)
    }

    /** App exit: quit OUR mpv process (kill only our own PID when it does not quit). */
    suspend fun shutdown() = engine.shutdown()
}

/** The embedded VLC engine host for Windows. */
class VlcHost(
    host: () -> VideoHostLike?,
    vlcPath: String? = null,
) {
    private val listeners = CopyOnWriteArrayList<(MpvEvent) -> Unit>()
    val tap = CopyOnWriteArrayList<(MpvEvent) -> Unit>()

    val engine = VlcWindowsEngine(
        VlcEngineConfig(
            host = host,
            locate = { locateVlc(vlcPath) },
            emit = { e ->
                for (t in tap) t(e)
                for (l in listeners) l(e)
            },
            wallClock = { PlayerTime.now().toLong() },
        )
    )

    fun available(): Boolean = engine.isAvailable

    fun subscribe(l: (MpvEvent) -> Unit): () -> Unit {
        listeners += l
        return { listeners -= l }
    }

    suspend fun shutdown() = engine.shutdown()
}

/** NativePlayerBackend for embedded VLC. */
class VlcBackend(private val host: VlcHost) : NativePlayerBackend {
    override val engineId: EngineId get() = EngineId.Vlc
    override val sampleIntervalMs: Long get() = 500L

    override fun onEvent(listener: (PlayerEvent) -> Unit): () -> Unit =
        host.subscribe { e -> MpvBackend.toPlayerEvent(e)?.let(listener) }

    override suspend fun sampleThroughput(): Double? = null

    override suspend fun invoke(cmd: NativeCommand): Any? = try {
        val e = host.engine
        when (cmd) {
            is NativeCommand.Load -> {
                val o = cmd.opts
                e.load(
                    MpvLoadOptions(
                        url = o.url,
                        headers = o.headers ?: emptyMap(),
                        startMs = o.startMs,
                        title = o.title,
                    )
                )
            }
            NativeCommand.Play -> e.play()
            NativeCommand.Pause -> e.pause()
            is NativeCommand.Seek -> e.seek(cmd.ms.toLong())
            is NativeCommand.Rate -> e.setRate(cmd.rate)
            is NativeCommand.Volume -> e.setVolume(cmd.volume)
            is NativeCommand.Mute -> e.setMuted(cmd.muted)
            is NativeCommand.Audio -> e.selectAudio(cmd.id)
            is NativeCommand.Subtitle -> e.selectSubtitle(cmd.id)
            is NativeCommand.SubDelay -> e.setSubtitleDelay(cmd.ms.toLong())
            is NativeCommand.Shaders -> Unit // VLC has no GLSL shader chain (NativeEngine never offers one for it)
            is NativeCommand.Aspect -> e.setAspect(cmd.mode.id)
            is NativeCommand.Viewport -> e.setViewport(cmd.rect?.let { Viewport.Rect(it.x, it.y, it.w, it.h) } ?: Viewport.Fullscreen)
            NativeCommand.Snapshot, is NativeCommand.FrameSample -> null
            NativeCommand.Destroy -> e.destroy()
        }
    } catch (x: MpvException) {
        throw NativeCommandException(x.code.wire, x.reason)
    }
}


/** `NativePlayerBackend` of one player engine over the app-wide [MpvHost] (`players.mpv` of the Electron bridge). */
class MpvBackend(private val host: MpvHost) : NativePlayerBackend {
    override val engineId: EngineId get() = EngineId.Mpv

    /** mpv writes a JPEG and it has to be read back and decoded (~100 ms): the glass over the video samples twice a second. */
    override val sampleIntervalMs: Long get() = 500L

    override fun onEvent(listener: (PlayerEvent) -> Unit): () -> Unit = host.subscribe { e -> toPlayerEvent(e)?.let(listener) }

    /** 网速 (T06): mpv's own `cache-speed`, polled once a second by the engine; null while mpv cannot answer. */
    override suspend fun sampleThroughput(): Double? = host.engine.cacheSpeed()

    override suspend fun invoke(cmd: NativeCommand): Any? = try {
        val e = host.engine
        when (cmd) {
            is NativeCommand.Load -> {
                val o = cmd.opts
                e.load(
                    MpvLoadOptions(
                        url = o.url,
                        headers = o.headers ?: emptyMap(),
                        startMs = o.startMs,
                        title = o.title,
                        externalSubtitles = o.externalSubtitles.orEmpty().map { ExternalSubtitle(it.url, it.label, it.lang) },
                        httpProxy = o.httpProxy,
                    ),
                )
            }
            NativeCommand.Play -> e.command(MpvTransportCommand.Play)
            NativeCommand.Pause -> e.command(MpvTransportCommand.Pause)
            is NativeCommand.Seek -> e.command(MpvTransportCommand.Seek(cmd.ms))
            is NativeCommand.Rate -> e.command(MpvTransportCommand.Rate(cmd.rate))
            is NativeCommand.Volume -> e.command(MpvTransportCommand.Volume(cmd.volume))
            is NativeCommand.Mute -> e.command(MpvTransportCommand.Mute(cmd.muted))
            is NativeCommand.Audio -> e.command(MpvTransportCommand.Audio(cmd.id))
            is NativeCommand.Subtitle -> e.command(MpvTransportCommand.Subtitle(cmd.id))
            is NativeCommand.SubDelay -> e.command(MpvTransportCommand.SubDelay(cmd.ms))
            is NativeCommand.Shaders -> e.command(MpvTransportCommand.Shaders(cmd.paths))
            is NativeCommand.Aspect -> e.command(MpvTransportCommand.Aspect(cmd.mode.id))
            is NativeCommand.Viewport -> e.setViewport(cmd.rect?.let { Viewport.Rect(it.x, it.y, it.w, it.h) } ?: Viewport.Fullscreen)
            NativeCommand.Snapshot -> {
                val s = e.snapshot()
                val img = org.jetbrains.skia.Image.makeFromEncoded(s.jpeg).toComposeImageBitmap()
                FrameSnapshot(img, s.width, s.height)
            }
            // the glass over the video (T11): the same mpv screenshot, downscaled to ~128 px wide off the command thread
            is NativeCommand.FrameSample -> {
                val s = e.snapshot()
                val img = downscaled(s.jpeg, cmd.maxWidth)
                FrameSnapshot(img, img.width, img.height)
            }
            NativeCommand.Destroy -> e.destroy()
        }
    } catch (x: MpvException) {
        throw NativeCommandException(x.code.wire, x.reason)
    }

    companion object {
        private fun track(t: MpvTrack) = PlayerTrack(t.id, t.type, t.label, t.lang, t.codec, t.channels, t.isDefault, t.selected, t.external)

        /**
         * A JPEG still scaled to at most [maxWidth] px wide (aspect kept). Off the engine's command thread: decoding a 4K
         * still costs tens of milliseconds and must not hold up seeks (T11: ~100 ms per sample, 2 per second at most).
         */
        private suspend fun downscaled(jpeg: ByteArray, maxWidth: Int): androidx.compose.ui.graphics.ImageBitmap =
            withContext(Dispatchers.IO) {
                val src = org.jetbrains.skia.Image.makeFromEncoded(jpeg)
                val w = src.width
                val h = src.height
                val tw = maxWidth.coerceIn(1, maxOf(1, w))
                val th = maxOf(1, (h.toFloat() * tw / maxOf(1, w)).roundToInt())
                if (tw == w && th == h) return@withContext src.toComposeImageBitmap()
                val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(tw, th)
                try {
                    surface.canvas.drawImageRect(src, org.jetbrains.skia.Rect.makeWH(tw.toFloat(), th.toFloat()))
                    surface.makeImageSnapshot().toComposeImageBitmap()
                } finally {
                    surface.close()
                }
            }

        /** mpv engine event -> player event (`sampledAt` is already [PlayerTime]: the host stamps with it). */
        fun toPlayerEvent(e: MpvEvent): PlayerEvent? = when (e) {
            is MpvEvent.Ready -> PlayerEvent.Ready(e.durationMs.toDouble())
            MpvEvent.Play -> PlayerEvent.Play
            MpvEvent.Pause -> PlayerEvent.Pause
            is MpvEvent.Buffering -> PlayerEvent.Buffering(e.buffering)
            MpvEvent.Ended -> PlayerEvent.Ended
            is MpvEvent.Error -> PlayerEvent.Error(e.code.wire, e.message, e.fatal)
            is MpvEvent.Clock -> PlayerEvent.Clock(PlayerClock(e.positionMs.toDouble(), e.durationMs.toDouble(), e.rate, e.paused, e.buffering, e.wallClockMs.toDouble()))
            is MpvEvent.Tracks -> PlayerEvent.Tracks(PlayerTracks(e.audio.map(::track), e.subtitle.map(::track), e.video.map(::track)))
            is MpvEvent.Seeked -> PlayerEvent.Seeked(e.positionMs.toDouble())
            is MpvEvent.VideoSize -> PlayerEvent.VideoSizeChanged(VideoSize(e.width, e.height, e.fps))
            is MpvEvent.SourceSwitching -> PlayerEvent.SourceSwitching(e.generation, e.positionMs.toDouble())
            is MpvEvent.FirstFrame -> PlayerEvent.FirstFrame(e.generation, e.positionMs.toDouble(), e.width, e.height, e.switched)
        }
    }
}

/** Windows playback for the shared app: embedded mpv only (no web core, external players only by the user's hand - N7). */
class DesktopPlayerPlatform(
    private val mpv: MpvHost,
    private val vlc: VlcHost? = null,
    /** The window the player's keys arrive in (the stage's UI window). */
    private val keyWindow: () -> java.awt.Window? = { null },
    private val hasWindow: () -> Boolean,
    /** 外部播放器 (T06): the probe of this machine - a test hands in its own instead of reading the registry. */
    private val externalPlayersImpl: DesktopExternalPlayers = DesktopExternalPlayers(),
) : PlayerPlatform {
    private val mpvFound: Boolean by lazy { mpv.available() }
    private val vlcFound: Boolean by lazy { vlc?.available() == true }
    private var compositionBefore: Boolean? = null

    /** 截图 (T06): mpv writes the picture into 图片\咕咕嘎嘎播放器; the toast says where. */
    override val screenshots: com.gugugaga.app.player.Screenshots = MpvScreenshots(mpv)

    override fun platformFacts(): PlatformFacts {
        val engines = buildList {
            if (mpvFound) add(EngineId.Mpv)
            if (vlcFound) add(EngineId.Vlc)
        }
        // 外部播放器 (T06): PotPlayer / VLC / mpv / MPC-HC, handed a stream only by the user's explicit choice
        val external = externalPlayersImpl.installed()
        return PlatformFacts(
            kind = PlatformKind.Windows,
            listed = engines,
            present = engines,
            mpvMode = if (hasWindow()) "overlay" else "unavailable",
            externalBridge = external.isNotEmpty(),
            externalCapability = external.isNotEmpty(),
            web = false,
        )
    }

    /** 外部播放器 (T06): the ones installed on this machine; the probe is cached (a player installed later shows after a restart). */
    override val externalPlayers: ExternalPlayers = externalPlayersImpl

    override fun createEngine(id: EngineId): PlayerEngine? = when (id) {
        EngineId.Mpv -> NativeEngine(MpvBackend(mpv))
        EngineId.Vlc -> vlc?.let { NativeEngine(VlcBackend(it)) }
        else -> null
    }

    /** The controller negotiates the stream: start the (one, `--wid`-embedded) mpv process meanwhile (docs/native-log-perf.md). */
    override fun prewarm(id: EngineId) {
        if (id == EngineId.Mpv && mpvFound && hasWindow()) mpv.engine.prewarm()
        else if (id == EngineId.Vlc && vlcFound && hasWindow()) vlc?.engine?.prewarm()
    }

    /**
     * The player's letter shortcuts vs. an active Chinese IME (N7 e2e: V / Shift+N never reached the JVM). While [on]: the
     * focused component's input context stops composing (the IME of THIS window goes to direct input, other apps keep
     * theirs) and every component of the window has its input methods disabled, so a later focus gain (Alt+Tab back) gets no
     * IME either (AWT disables the native IME for a non-client on focus gain). `false` restores both. A Compose text field
     * re-enables its own input on focus, so the player's panels can still take Chinese text.
     *
     * win-ime: `enableInputMethods` alone is NOT enough. AWT (dis)associates the native IME context with a component's
     * window handle only when that component GAINS AWT focus; Compose moves the focus inside its one canvas, so after the
     * shortcut cycle the canvas had no context at all - the taskbar said 中 but letters landed raw in the search field
     * (win-accept-029; a JNA probe watched the context land on the frame instead of the focused canvas). The association is
     * therefore managed here explicitly with [Imm32.ImmAssociateContextEx]: flags `0` takes the IME off the focused handle,
     * [Imm32.IACE_DEFAULT] puts the default context back. The probe also showed `enableInputMethods(false)` does not detach a
     * context attached this way, so the [on] side detaches explicitly too - both directions are deterministic.
     */
    override fun shortcutKeys(on: Boolean) {
        if (!com.sun.jna.Platform.isWindows()) return
        SwingUtilities.invokeLater {
            val w = keyWindow() ?: return@invokeLater
            val focused = w.focusOwner ?: w
            val ic = w.focusOwner?.inputContext
            if (on) {
                runCatching {
                    if (ic != null) {
                        if (compositionBefore == null) compositionBefore = ic.isCompositionEnabled
                        ic.setCompositionEnabled(false)
                    }
                }
                forEachComponent(w) { it.enableInputMethods(false) }
                imeAttached(focused, false)
            } else {
                forEachComponent(w) { it.enableInputMethods(true) }
                imeAttached(focused, true)
                val before = compositionBefore
                compositionBefore = null
                if (before != null && ic != null) runCatching { ic.setCompositionEnabled(before) }
            }
        }
    }

    /** (Dis)associate the IME context on the focused component's own HWND - see [shortcutKeys] (win-ime). */
    private fun imeAttached(target: java.awt.Component, attach: Boolean) {
        runCatching {
            val hwnd = HWND(Native.getComponentPointer(target))
            Imm32.INSTANCE.ImmAssociateContextEx(hwnd, null, if (attach) Imm32.IACE_DEFAULT else 0)
        }
    }

    private fun forEachComponent(c: java.awt.Component, f: (java.awt.Component) -> Unit) {
        f(c)
        if (c is java.awt.Container) for (child in c.components) forEachComponent(child, f)
    }

    /** `SetThreadExecutionState` on the (long-lived) event thread: the display stays on while something plays. */
    override fun keepAwake(on: Boolean) {
        if (!com.sun.jna.Platform.isWindows()) return
        SwingUtilities.invokeLater {
            runCatching {
                val flags = if (on) WinBase.ES_CONTINUOUS or WinBase.ES_DISPLAY_REQUIRED or WinBase.ES_SYSTEM_REQUIRED else WinBase.ES_CONTINUOUS
                Kernel32.INSTANCE.SetThreadExecutionState(flags)
            }
        }
    }
}

/**
 * 截图 on Windows (T06 second batch): mpv's `screenshot-to-file … video` straight into `图片\咕咕嘎嘎播放器\`.
 *
 * The folder is the signed-in user's Pictures library, which Windows may have redirected into OneDrive: an existing
 * `%USERPROFILE%\Pictures` or `%USERPROFILE%\OneDrive\Pictures` wins, else `%USERPROFILE%\Pictures` is created. The file name is
 * the local time (`2026-11-05 21-03-44.jpg`), so a second press within the same second gets a ` (2)` suffix instead of overwriting.
 */
class MpvScreenshots(
    private val mpv: MpvHost,
    private val now: () -> Long = System::currentTimeMillis,
    private val home: String = System.getProperty("user.home") ?: ".",
) : com.gugugaga.app.player.Screenshots {
    override suspend fun save(): String {
        val (file, shown) = targetFile()
        file.parentFile?.mkdirs()
        mpv.engine.saveScreenshot(file.path)
        return shown
    }

    /** `咕咕嘎嘎播放器/<local time>.jpg` - never an absolute path with the user's name in it. */
    internal fun targetFile(nowMs: Long = now()): Pair<java.io.File, String> = target(home, nowMs)

    companion object {
        /** `图片` (the Pictures library; an existing OneDrive-redirected folder wins), created on first use. */
        internal fun picturesDir(home: String): java.io.File {
            val homeDir = java.io.File(home)
            val existing = listOf("Pictures", "OneDrive/Pictures", "OneDrive/图片").map { java.io.File(homeDir, it) }.firstOrNull { it.isDirectory }
            return existing ?: java.io.File(homeDir, "Pictures")
        }

        /** The file a screenshot taken at [nowMs] goes to, plus the relative place the toast shows. Pure (tested). */
        internal fun target(home: String, nowMs: Long, exists: (java.io.File) -> Boolean = { it.exists() }): Pair<java.io.File, String> {
            val dir = java.io.File(picturesDir(home), "咕咕嘎嘎播放器")
            val name = stamp(nowMs)
            var file = java.io.File(dir, "$name.jpg")
            var n = 2
            while (exists(file)) file = java.io.File(dir, "$name ($n).jpg").also { n++ }
            return file to "图片\\咕咕嘎嘎播放器\\${file.name}"
        }

        /** `2026-11-05 21-03-44` in LOCAL time (logcat-style, no colons: a file name must be valid on every OS). */
        fun stamp(epochMs: Long): String {
            val d = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault())
            fun p(n: Int) = n.toString().padStart(2, '0')
            return "${p(d.year)}-${p(d.monthValue)}-${p(d.dayOfMonth)} ${p(d.hour)}-${p(d.minute)}-${p(d.second)}"
        }
    }
}
