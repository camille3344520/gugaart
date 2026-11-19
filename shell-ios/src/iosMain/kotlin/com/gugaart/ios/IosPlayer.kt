package com.gugaart.ios

import com.gugaart.ui.player.AvCaps
import com.gugugaga.app.player.PlatformFacts
import com.gugugaga.app.player.PlatformKind
import com.gugugaga.app.player.PlayerEngine
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.app.player.SystemLevel
import com.gugugaga.core.EngineId
import com.gugugaga.player.apple.AppleMpvEngines
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.MediaPlayer.MPVolumeView
import platform.UIKit.UIApplication
import platform.UIKit.UIInterfaceOrientationMaskLandscape
import platform.UIKit.UIInterfaceOrientationMaskPortrait
import platform.UIKit.UIScreen
import platform.UIKit.UISlider
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.UIKit.setNeedsUpdateOfHomeIndicatorAutoHidden
import platform.UIKit.setNeedsUpdateOfSupportedInterfaceOrientations
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 * iOS 的播放（M5-1 / M5-7 / M5-8，ADR 0005 §1.4 / §2.4）：上游 `:player-apple` 的 libmpv 进程内内核（MPVKit 静态链进应用），
 * 画面是容器视图最底下的一个 Metal 视图，Compose 的视图在它上面、不透明 = 否（甲）。只有 mpv 一个内核，回退链 `mpv`（U-ios-9）。
 */

/** 横屏 / 系统栏的状态：Swift 的根视图控制器读它（[supportedOrientationsMask] 等），Kotlin 在进出播放器时改。 */
object IosChrome {
    /** 播放器在前台：锁横屏、藏状态栏和 home 条（D-lead：`PlayerPlatform.playerVisible`）。 */
    var player = false
        private set

    fun setPlayer(on: Boolean) {
        if (player == on) return
        player = on
        dispatch_async(dispatch_get_main_queue()) { apply() }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun apply() {
        @Suppress("DEPRECATION")
        val root: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController
        root?.setNeedsStatusBarAppearanceUpdate()
        root?.setNeedsUpdateOfHomeIndicatorAutoHidden()
        root?.setNeedsUpdateOfSupportedInterfaceOrientations()
        val scene = UIApplication.sharedApplication.connectedScenes.firstOrNull { it is UIWindowScene } as? UIWindowScene ?: return
        val mask = if (player) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait
        scene.requestGeometryUpdateWithPreferences(UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = mask), errorHandler = null)
    }
}

/** Swift 的根视图控制器：现在允许哪些方向（UIInterfaceOrientationMask 的位）。 */
fun supportedOrientationsMask(): ULong = if (IosChrome.player) UIInterfaceOrientationMaskLandscape else UIInterfaceOrientationMaskPortrait

/** Swift 的根视图控制器：状态栏 / home 条藏不藏（播放器里藏）。 */
fun hideSystemBars(): Boolean = IosChrome.player

/**
 * iOS 的播放平台：只有 mpv；画面在界面之下（[videoUnderUi] = true）。没链上 libmpv 的构建（上游找不到 MPVKit）不列任何内核，
 * 播放器页出「这台设备上没有可用的播放内核」。
 */
internal class IosPlayerPlatform(private val engines: AppleMpvEngines) : PlayerPlatform {
    private val found: Boolean get() = engines.available()
    private var brightnessBefore: Double? = null

    /** 播放器页在前台（离开应用时看它决定要不要进画中画）。 */
    var visible = false
        private set

    override fun platformFacts(): PlatformFacts {
        val listed = if (found) listOf(EngineId.Mpv) else emptyList()
        return PlatformFacts(kind = PlatformKind.Ios, listed = listed, present = listed, mpvMode = null, web = false)
    }

    override fun createEngine(id: EngineId): PlayerEngine? = if (id == EngineId.Mpv && found) engines.create() else null

    override fun prewarm(id: EngineId) {
        if (id == EngineId.Mpv && found) engines.prewarm()
    }

    override val videoUnderUi: Boolean get() = true

    /** 播放时屏幕常亮。 */
    override fun keepAwake(on: Boolean) {
        dispatch_async(dispatch_get_main_queue()) {
            UIApplication.sharedApplication.idleTimerDisabled = on
            // 开始出声：激活音频会话（类别 playback：后台出声、画中画都要它，D-lead-36）
            if (on) IosMedia.activateAudio()
        }
    }

    /** 画中画（M5-10，D-lead-32）：上游 :player-apple 的 iOS 一半；这台设备不能画中画 = null，钮不画。 */
    override val pictureInPicture: com.gugugaga.app.player.PictureInPicture? get() = engines.pictureInPicture

    /** 播放器在前台：锁横屏、藏状态栏和 home 条；离开时还原，亮度也还原成进来时的值（M5-8）。 */
    override fun playerVisible(visible: Boolean) {
        this.visible = visible
        IosChrome.setPlayer(visible)
        if (!visible) IosMedia.detach(engines)
        if (!visible) brightnessBefore?.let { b ->
            brightnessBefore = null
            dispatch_async(dispatch_get_main_queue()) { UIScreen.mainScreen.brightness = b }
        }
    }

    /** 右半边上下滑 = 系统音量（D-lead-33）：借系统的音量滑块（MPVolumeView 里那个 UISlider），不碰私有接口。 */
    override val systemVolume: SystemLevel = SystemVolume

    /** 左半边上下滑 = 屏幕亮度（UIScreen.brightness）；第一次调之前记下原来的值，离开播放器还原。 */
    override val brightness: SystemLevel = object : SystemLevel {
        override fun get(): Double = UIScreen.mainScreen.brightness

        override fun set(value01: Double) {
            if (brightnessBefore == null) brightnessBefore = UIScreen.mainScreen.brightness
            UIScreen.mainScreen.brightness = value01.coerceIn(0.0, 1.0)
        }
    }
}

/**
 * 系统音量：MPVolumeView 放在屏幕外（不可见，但要在窗口里系统才认），读写它里面的 UISlider。模拟器上只有滑块的值会变，
 * 真机才真的调音量（D-lead-33：不上架，没有审核风险）。
 */
@OptIn(ExperimentalForeignApi::class)
internal object SystemVolume : SystemLevel {
    private var view: MPVolumeView? = null

    private fun slider(): UISlider? {
        val v = view ?: MPVolumeView(frame = CGRectMake(-200.0, -200.0, 10.0, 10.0)).also { mv ->
            mv.alpha = 0.01
            @Suppress("DEPRECATION")
            UIApplication.sharedApplication.keyWindow?.addSubview(mv)
            view = mv
        }
        return v.subviews.firstOrNull { it is UISlider } as? UISlider
    }

    override fun get(): Double = slider()?.value?.toDouble() ?: 0.5

    override fun set(value01: Double) {
        val s = slider() ?: return
        s.setValue(value01.coerceIn(0.0, 1.0).toFloat(), animated = false)
        s.sendActionsForControlEvents(platform.UIKit.UIControlEventValueChanged)
    }
}

/** 格式徽章的设备能力（M5-7）：屏幕有 EDR 余量 = 能出 HDR；杜比视界靠 libplacebo 重塑（双层的增强层不读，同 Mac）。 */
internal fun iosAvCaps(displayHdr: Boolean): AvCaps =
    AvCaps(hdr10 = displayHdr, hdr10Plus = displayHdr, hlg = displayHdr, dolbyVision = displayHdr, dolbyVisionDualLayer = false)

/** 放视频的那块容器视图（Kotlin 自己的根视图控制器的 view）：Metal 视图在最底下，Compose 的视图在上面。 */
internal var videoHost: UIView? = null
