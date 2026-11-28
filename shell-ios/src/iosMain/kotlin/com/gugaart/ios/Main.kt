package com.gugaart.ios

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.window.ComposeUIViewController
import com.gugaart.ui.GugaartApp
import com.gugaart.ui.theme.LocalPhoneHost
import com.gugaart.ui.theme.LocalPhoneInsets
import com.gugaart.ui.theme.PhoneInsets
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import platform.UIKit.UIColor
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController

/*
 * iOS 壳的入口（M4-6）。Xcode 宿主（shell-ios/xcode/Gugaart/AppDelegate.swift）只做三件事：开窗口、把 [MainViewController] 放进去、
 * 把系统的「点状态栏」转给 [onStatusBarTap]。其余都在这里：造上游的 PlatformDeps / AppGraph（整个进程一份）、给 `:ui` 手机宿主和安全区。
 */

/** 系统的「点状态栏」（D-lead-27）：宿主每收到一次就调一次 [onStatusBarTap]，当前页的主滚动区滚回顶。 */
private val statusBarTaps = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

/** Swift 那边调（宿主里那块透明的滚动视图收到了系统的「回顶」）。 */
fun onStatusBarTap() {
    statusBarTaps.tryEmit(Unit)
}

/** Called once by the Swift AppDelegate before launch finishes: registers the background-download system task (iOS 26+, 1.6 batch F3). */
fun registerBackgroundWork() {
    com.gugaart.ui.download.IosDownloadHost.registerBackgroundTask()
}

/** 播放内核（M5-1）：画面放在 [videoHost] 的最底下。Local files: only those in the download folder (offline playback, 1.6 batch F3). */
private val engines by lazy { com.gugugaga.player.apple.AppleMpvEngines(hostView = { videoHost }, background = "#000000", subFontsDir = subFontsDir(), localFiles = listOf(downloadsDir())) }

/** 整个进程一个应用图（会话、设置、导航、缓存）。 */
private val graph: AppGraph by lazy {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val g = AppGraph(iosDeps(playerPlatform), scope, Route.Home)
    // 更新器：上游的验签在 iOS 上恒为 false，换成带本仓库 Ed25519 验签的同款更新器（`:ui` 全经 graph.updater 读它，1.4.0）
    com.gugaart.ui.update.OwnVerifier.install(g)
    // 格式徽章的设备能力（M5-7）：屏幕的 EDR 余量，换屏 / 亮度变化时跟着变
    engines.refreshDisplay()
    scope.launch { engines.displayHdr.collect { hdr -> com.gugaart.ui.player.DeviceAv.caps = iosAvCaps(hdr) } }
    // 声音与离开应用：音频会话、后台只出声、离开时自动进画中画（M5-10 / M5-11）
    IosMedia.start(engines, playerPlatform)
    // 锁屏卡片的封面：这一条的海报（Emby 的 Primary 图）
    IosNowPlaying.artworkUrl = { snap ->
        val item = snap.item
        val client = g.session.session.value?.client
        if (item == null || client == null) null
        else client.itemImage(item, com.gugugaga.core.ImageKind.Poster, com.gugugaga.core.ImageUrlOptions(maxWidth = 600, format = com.gugugaga.core.ImageFormat.Jpg))
    }
    // 模拟器验收（Debug 构建 + 启动参数 -gugaart.accept 才有；Release 里是空的）
    Accept.start(g, engines, playerPlatform)
    g
}

private val playerPlatform by lazy { IosPlayerPlatform(engines) }

private val host by lazy { iosHost(statusBarTaps) }

/**
 * 根视图控制器：一个容器，最底下留给播放器的 Metal 视图（[videoHost]），上面是 Compose 的整页——不透明 = 否，播放器页不铺底，
 * 画面从它的洞里透出来（ADR 0005 §1.4 甲）。别的页面自己铺纸色。
 */
fun MainViewController(): UIViewController {
    val compose = composeController()
    val container = UIViewController()
    val root = container.view
    root.backgroundColor = UIColor.blackColor
    container.addChildViewController(compose)
    compose.view.setFrame(root.bounds)
    compose.view.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
    compose.view.backgroundColor = UIColor.clearColor
    compose.view.opaque = false
    root.addSubview(compose.view)
    compose.didMoveToParentViewController(container)
    videoHost = root
    return container
}

/** Compose 的整页。安全区从系统读（状态栏 / 灵动岛 / home 条 / 横屏的岛那一侧），喂给 `:ui` 的手机尺寸。 */
@OptIn(ExperimentalComposeUiApi::class) // ComposeContainerConfiguration.opaque
private fun composeController(): UIViewController = ComposeUIViewController(configure = { opaque = false }) {
    val app = remember { graph }
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    // 系统栏 + 刘海 / 岛（不含键盘：键盘弹起时标签栏照旧沉下去，结果列表自己让位）
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    val insets = with(density) {
        PhoneInsets(
            top = bars.getTop(this).toDp(),
            bottom = bars.getBottom(this).toDp(),
            left = bars.getLeft(this, dir).toDp(),
            right = bars.getRight(this, dir).toDp(),
        )
    }
    val files = remember { iosBackupFileHost() }
    val fonts = remember { iosFontImportHost() }
    // 离开播放器：锁屏 / 控制中心的「正在播放」卡片消失、命令摘掉（M5-4）
    val onPlayer = app.nav.pathname.startsWith("/play")
    LaunchedEffect(onPlayer) { if (!onPlayer) IosNowPlaying.detach() }
    CompositionLocalProvider(
        LocalPhoneHost provides host,
        LocalPhoneInsets provides insets,
        com.gugaart.ui.backup.LocalBackupFiles provides files,
        com.gugaart.ui.data.LocalFontImport provides fonts,
        // 播放器页建好控制器就交过来（每次重组都会叫，同一个控制器只接一次）
        com.gugaart.ui.player.LocalPlaySessionSink provides {
            IosNowPlaying.attach(it.controller)
            IosMedia.attach(it.controller, engines)
            Accept.onController(it.controller)
            Accept.onPlaySession(it)
        },
    ) {
        Accept.Content { GugaartApp(app) }
    }
}
