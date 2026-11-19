package com.gugaart.ios

import com.gugugaga.app.player.PlayController
import com.gugugaga.player.apple.AppleMpvEngines
import com.gugugaga.player.apple.PipPlayback
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionModeMoviePlayback
import platform.AVFAudio.setActive
import platform.AVKit.AVRoutePickerView
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import kotlinx.cinterop.useContents
import platform.UIKit.UIControl
import platform.UIKit.UIControlEventPrimaryActionTriggered
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIView

/*
 * iOS 的声音与离开应用（B8：M5-2 AirPlay、M5-11 后台出声、M5-10 画中画的入口；decisions.md D-lead-32 / 35 / 36）。
 *  - 音频会话：类别 playback、模式 moviePlayback（静音键不静音；后台出声和画中画都要它），Info.plist 有 UIBackgroundModes: audio；
 *  - 离开应用：正在放 → 自动进画中画（D-lead-32）；进了后台 → 视频轨关掉只出声（D-lead-36，`AppleMpvEngines.background`），回前台接上；
 *  - AirPlay：系统的路由选择器（AVRoutePickerView），只换声音的输出设备（D-lead-35）。
 */

internal object IosMedia {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    /** 正在播放的那一页的控制器（画中画的播放 / 暂停、离开应用时看在不在放）；离开播放器 = null。 */
    var controller: PlayController? = null
        private set

    /** 离开应用时自动进画中画（D-lead-32）。验收的「后台只出声」那一段临时关掉它。 */
    var autoPip = true

    /** 进了后台（诊断 / 验收：进后台时有没有关掉视频）。 */
    var backgroundedAt: Double? = null
        private set

    fun start(engines: AppleMpvEngines, player: IosPlayerPlatform) {
        if (started) return
        started = true
        // 音频会话：一次设好；真正出声时（keepAwake）再激活
        runCatching {
            val s = AVAudioSession.sharedInstance()
            s.setCategory(AVAudioSessionCategoryPlayback, mode = AVAudioSessionModeMoviePlayback, options = 0u, error = null)
        }
        val center = NSNotificationCenter.defaultCenter
        val main = NSOperationQueue.mainQueue
        // 离开应用（上拉回主屏、切到别的应用、锁屏前一刻）：在放就进画中画
        center.addObserverForName(UIApplicationWillResignActiveNotification, null, main) { _ ->
            if (player.visible && autoPip) engines.pictureInPicture?.enterIfPlaying()
        }
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { _ ->
            backgroundedAt = platform.Foundation.NSDate().timeIntervalSince1970
            scope.launch { runCatching { engines.background(true) } }
        }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, main) { _ ->
            backgroundedAt = null
            scope.launch { runCatching { engines.background(false) } }
        }
    }

    /** 开始出声时激活音频会话（keepAwake(true) 的时候）。 */
    fun activateAudio() {
        runCatching { AVAudioSession.sharedInstance().setActive(true, error = null) }
    }

    /** 播放器页建好控制器就交过来：画中画小窗里的播放 / 暂停接到它。 */
    fun attach(c: PlayController, engines: AppleMpvEngines) {
        if (controller === c) return
        controller = c
        engines.pictureInPicture?.playback = object : PipPlayback {
            override val paused: Boolean get() = c.clock.value.paused
            override fun play() = c.play()
            override fun pause() = c.pause()

            // 小窗的进度条和 ±跳（M5-10 硬指标；上游 PipTimeline：0 … 时长，跳 = 控制器 seek）
            override val positionMs: Double get() = c.clock.value.positionMs
            override val durationMs: Double? get() = c.clock.value.durationMs.takeIf { it > 0 }
            override val rate: Double get() = c.clock.value.rate
            override fun seekTo(ms: Double) = c.seekTo(ms)
        }
    }

    /** 离开播放器。 */
    fun detach(engines: AppleMpvEngines) {
        controller = null
        engines.pictureInPicture?.playback = null
    }
}

/**
 * AirPlay（D-lead-35）：弹系统的路由选择器。AVRoutePickerView 自己是一个钮，这里借它里面那个 UIButton 发一次「点按」——
 * 视图放在窗口里看不见的角上（系统要它在窗口里才弹得出来）。只换声音的输出设备；mpv 的声音跟系统路由走。
 */
@OptIn(ExperimentalForeignApi::class)
internal object AirPlayPicker {
    private var picker: AVRoutePickerView? = null

    /** 诊断：上一次为什么没弹出来。 */
    var lastProblem: String? = null
        private set

    fun show() {
        lastProblem = null
        @Suppress("DEPRECATION")
        val window = UIApplication.sharedApplication.keyWindow ?: run {
            lastProblem = "没有 keyWindow"
            return
        }
        // 选择器从这个钮的位置弹出来：放在窗口里、右上角（和播放器顶栏的 AirPlay 钮差不多的地方），几乎透明；放在窗口外时系统不弹
        val p = picker ?: AVRoutePickerView(frame = CGRectMake(0.0, 0.0, 44.0, 44.0)).also {
            it.alpha = 0.02
            it.userInteractionEnabled = false
            picker = it
        }
        val w = window.bounds.useContents { size.width }
        p.setFrame(CGRectMake(w - 60.0, 16.0, 44.0, 44.0))
        if (p.superview != window) window.addSubview(p)
        p.layoutIfNeeded()
        val button = findControl(p) ?: run {
            lastProblem = "AVRoutePickerView 里找不到 UIControl（子视图 ${p.subviews.size} 个）"
            return
        }
        button.sendActionsForControlEvents(UIControlEventTouchUpInside)
        button.sendActionsForControlEvents(UIControlEventPrimaryActionTriggered)
    }

    private fun findControl(v: UIView): UIControl? {
        if (v is UIControl) return v
        for (s in v.subviews) (s as? UIView)?.let { findControl(it) }?.let { return it }
        return null
    }
}
