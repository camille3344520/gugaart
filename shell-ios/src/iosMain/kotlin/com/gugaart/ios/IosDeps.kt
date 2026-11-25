package com.gugaart.ios

import com.gugaart.ui.theme.Distribution
import com.gugaart.ui.theme.HapticStyle
import com.gugaart.ui.theme.PhoneHost
import com.gugugaga.app.platform.CommentBlobStore
import com.gugugaga.app.platform.FileBlobCache
import com.gugugaga.app.platform.LocalKeys
import com.gugugaga.app.platform.PlatformDeps
import com.gugugaga.app.update.DownloadTarget
import com.gugugaga.app.update.InstallOutcome
import com.gugugaga.app.update.RangeResult
import com.gugugaga.app.update.RangeSource
import com.gugugaga.app.update.ReleaseAsset
import com.gugugaga.app.update.UpdateHost
import com.gugugaga.core.ClientInfo
import com.gugugaga.ds.theme.UiMode
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.coroutines.flow.Flow
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UIPasteboard
import platform.UIKit.UISelectionFeedbackGenerator
import platform.posix.uname
import platform.posix.utsname

/*
 * 上游的共享逻辑要从 iOS 拿的东西（M4-6，ADR 0005 §2.5）：存储、身份、缓存目录、复制、减弱动效、更新宿主。和桌面壳的 `desktopDeps`
 * 同一个接法；没有接的：播放器（M5）、DLNA 投屏和多台弹幕服务器的中转（B9）、TMDB 默认密钥、预置测试服务器。
 */

/** 机型标识（`iPhone17,1`；模拟器上是它模拟的那台）。 */
@OptIn(ExperimentalForeignApi::class)
internal fun machineId(): String {
    NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"]?.let { return it.toString() }
    return memScoped {
        val u = alloc<utsname>()
        uname(u.ptr)
        u.machine.toKString()
    }
}

internal fun iosDeps(player: com.gugugaga.app.player.PlayerPlatform?): PlatformDeps {
    val support = appSupportDir()
    val caches = cachesDir()
    val local = FileLocalStore("$support/local.json")
    val secure = KeychainStore("com.gugugaga.art", "$support/secure-fallback.json")
    // 多台弹幕服务器（D-lead-31）：清单 + 本机回环上的中转（iOS 版，和桌面 / 安卓同一个行为）；上游读主弹幕地址读到的是中转
    val danmakuSecure = com.gugaart.ui.danmaku.withDanmakuRelay(secure, local, null)
    // DLNA 投屏（D-lead-30）：SSDP + SOAP 的 iOS 版；播放器顶栏出「投屏」钮
    com.gugaart.ui.cast.Cast.host = com.gugaart.ui.cast.IosDlnaCastHost()
    // Trakt：编进包里的应用（.env；没配 = 设置里写「这一版不能连 Trakt」）
    com.gugaart.ui.trakt.TraktService.install(BuildInfo.TRAKT_CLIENT_ID, BuildInfo.TRAKT_CLIENT_SECRET)
    // 每次安装一个稳定的 id（不从机器信息推导：它会显示在 Emby 的「设备」里）
    val deviceId = local.get(LocalKeys.DEVICE_ID) ?: "gugaart-${NSUUID().UUIDString.lowercase()}".also { local.set(LocalKeys.DEVICE_ID, it) }
    val machine = machineId()
    val system = UIDevice.currentDevice.systemVersion
    return PlatformDeps(
        uiMode = UiMode.Phone,
        identity = ClientInfo(device = "iPhone · $machine", deviceId = deviceId, version = BuildInfo.APP_VERSION),
        local = local,
        secure = danmakuSecure,
        secureMode = { secure.mode },
        appVersion = BuildInfo.APP_VERSION,
        platformLabel = "iOS $system · $machine",
        copyText = { text -> UIPasteboard.generalPasteboard.string = text },
        player = player,
        // Mock Emby 的夹具账号（只在 Debug 的模拟器验收、启动参数给了 Mock 的地址时；Release 里是 None）
        debug = Accept.debugOptions(),
        blobCache = FileBlobCache("$caches/tmdb-logos"),
        imageCache = FileBlobCache(
            "$caches/images",
            maxEntries = com.gugugaga.app.data.ImageDiskCache.MAX_ENTRIES,
            maxBytes = com.gugugaga.app.data.ImageDiskCache.MAX_BYTES,
        ),
        danmakuComments = CommentBlobStore(
            FileBlobCache("$caches/danmaku-comments", maxEntries = Int.MAX_VALUE, maxBytes = com.gugugaga.danmaku.match.COMMENT_CACHE_MAX_BYTES),
        ),
        reducedMotion = { UIAccessibilityIsReduceMotionEnabled() },
        productName = "咕咕嘎嘎",
        updateHost = IosUpdateHost(),
        updateConfig = iosUpdateConfig(),
        // 清单请求：主地址 + 国内备用镜像赛跑、超时转普通失败（和桌面 / 安卓同一个传输层，UpdateMirrors.kt）
        updateTransport = com.gugaart.ui.update.UpdateCheckTransport(com.gugugaga.core.KtorTransport(com.gugugaga.core.platformHttpClient())),
    )
}

/**
 * 更新比的是哪一版：发版的版本号（gradle.properties 的 gugaart.version → BuildInfo，和 Xcode 写进 Info.plist 的 CFBundleShortVersionString /
 * CFBundleVersion 同一个来源、同一个算法 x × 10000 + y × 100 + z，发版脚本给清单算 versionCode 也是它），资产键 `ios-ipa`。
 * Debug 的模拟器验收可以经运行器的启动参数假装成更低的一版（`Accept.updateVersion()`，验「有新版出号外」）；Release / IPA 里没有这个缝。
 */
internal fun iosUpdateConfig(): com.gugugaga.app.update.UpdateConfig {
    val (name, code) = Accept.updateVersion() ?: (BuildInfo.APP_VERSION to BuildInfo.VERSION_CODE)
    return com.gugaart.ui.update.GithubUpdate.config(name, code, IosUpdateHost.ASSET_KEY)
}

/**
 * 更新：只查、不下载（D-lead-26）。iPhone 上的应用不能自己装包；号外走 `:ui` 的侧载分支（「打开发布页」）。查清单、比版本是上游的更新器，
 * 验签是本仓库的纯 Kotlin Ed25519（上游在 iOS 上验签恒为 false；`Main.kt` 里 `OwnVerifier.install`，1.4.0）；画报每次启动关掉了自动下载
 * （`UpdateConsent`），所以这里的下载 / 安装永远不会被调用——万一被调到也只回一句话、不写任何文件。
 */
internal class IosUpdateHost : UpdateHost {
    override val assetKey: String = ASSET_KEY
    override val rangeSource: RangeSource = RangeSource { _, _, _ -> RangeResult(0, null, null, problem = NO_INSTALL) }
    override suspend fun createTarget(asset: ReleaseAsset): DownloadTarget? = null
    override fun canInstall(): Boolean = false
    override fun requestInstallPermission(): Boolean = false
    override suspend fun install(asset: ReleaseAsset, target: DownloadTarget): InstallOutcome = InstallOutcome.Failed(NO_INSTALL)

    companion object {
        /** 清单里 iOS 安装包的资产键（D-lead-22：未签名的 IPA）。 */
        const val ASSET_KEY = "ios-ipa"
        const val NO_INSTALL = "iPhone 上不在应用里安装：请到发布页下载 .ipa，用自签的工具装上。"
    }
}

/** 手机宿主（ADR 0005 §2.1）：没有返回键、左缘右滑返回、Taptic 触感、侧载的号外、点状态栏回顶。AirPlay 钮在 M5 / B8。 */
internal fun iosHost(statusBarTaps: Flow<Unit>): PhoneHost = PhoneHost(
    systemBack = false,
    edgeBack = true,
    haptic = ::feel,
    // AirPlay 只换声音的输出设备（D-lead-35）；画中画用页内的钮和纸面说明（D-lead-32 / 24）
    airPlay = AirPlayPicker::show,
    pipInPage = true,
    distribution = Distribution.Sideload,
    platformName = "iPhone",
    statusBarTaps = statusBarTaps,
)

/** 触感：组合里调（主线程）；生成器每次新建，系统自己节流。跟系统「系统触感」开关走。 */
private fun feel(style: HapticStyle) {
    when (style) {
        HapticStyle.Selection -> UISelectionFeedbackGenerator().selectionChanged()
        HapticStyle.Light -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight).impactOccurred()
        HapticStyle.Medium -> UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
        HapticStyle.Success -> UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
    }
}
