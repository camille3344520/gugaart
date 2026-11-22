package com.gugaart.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import com.gugaart.ui.GugaartApp
import com.gugaart.ui.player.LocalPlayerPanelDriver
import com.gugaart.ui.screens.home.ExitHint
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.ExitConfirm
import com.gugugaga.app.nav.Route
import com.gugugaga.app.nav.parseRoute
import com.gugugaga.app.platform.AndroidKeystoreStore
import com.gugugaga.app.platform.CommentBlobStore
import com.gugugaga.app.platform.CrashGuard
import com.gugugaga.app.platform.DebugOptions
import com.gugugaga.app.platform.FileBlobCache
import com.gugugaga.app.platform.LocalKeys
import com.gugugaga.app.platform.PlatformDeps
import com.gugugaga.app.platform.PrefsLocalStore
import com.gugugaga.app.platform.installCrashReporting
import com.gugugaga.app.platform.parseMockAccount
import com.gugugaga.app.player.PlayerKeyRouter
import com.gugugaga.app.session.SessionStatus
import com.gugugaga.core.ClientInfo
import com.gugugaga.ds.glass.ControlledGlLayer
import com.gugugaga.ds.glass.GlassBackend
import com.gugugaga.ds.glass.GlassPlatform
import com.gugugaga.ds.theme.UiMode
import com.gugugaga.nativeapp.player.AndroidEngineHost
import com.gugugaga.nativeapp.player.AndroidPlayerPlatform
import com.gugugaga.nativeapp.player.KeyRemap
import com.gugugaga.nativeapp.player.PLAYER_TAG
import com.gugugaga.nativeapp.player.VideoLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.util.UUID
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent

/*
 * 引擎宿主的接法复制自上游壳（docs/adr/0002-player-wiring-in-repo.md §6，登记在 docs/upstream-sync.md §5.1）：
 *   来源  gugugagaemby/native/androidApp/src/main/kotlin/com/gugugaga/nativeapp/MainActivity.kt
 *   提交  1e19741a（upstream.lock）
 *   照搬的  视图树（FrameLayout = VideoLayer 在下 + ComposeView 在上）、AndroidEngineHost / AndroidPlayerPlatform 的构造、返回键与按键分发、
 *           画中画、onStop 暂停、onDestroy 释放、崩溃守卫、会话状态日志、ControlledGl 玻璃层、StrictMode
 *   改了的  包名；界面根 GuguApp → GugaartApp；首页第二次返回的提示从上游 Toast 换成画报的 ExitHint 小条；系统栏图标固定浅色（只有深色主题）；
 *           存储名 gugaart_*；调试参数只留 route / glass，加 panel / engine（「画面让位」实测）
 *   没搬的  玻璃实验室（lab）、A/B 开关（exomemo / exopreload / scrubdwell…）、应用内更新、TMDB 默认密钥、预置测试服务器、selftest
 * 上游改了来源文件：git -C <上游> diff <lock>..HEAD -- <来源文件>，把同样的改动搬过来。
 */

/**
 * Android 入口（手机 `mobile` / 电视 `tv` 同一个 Activity，flavor 决定 UiMode）。
 *
 * 视图树：一个 FrameLayout = [VideoLayer]（ExoPlayer / libmpv / VLC 的 SurfaceView，在最下面）+ 上面的 Compose 视图。
 * 播放页在画面的位置不画底，视频从那里透上来。
 *
 * 按键：播放器显示时每个键先给它（[PlayerKeyRouter]），再给 Compose。返回：先播放页（收面板），再应用的返回栈；
 * 在首页要 2 秒内按第二次才离开（第一次出「再按一次返回，合上本期」）。
 *
 * 调试参数（只在 debug 包里读，不保存）：
 *   -e route /item/123            一开始落在哪个路由；应用已经开着时再发一次 = 导航过去
 *   -e glass live|gl|plates|solid 强制玻璃后端
 *   -e panel sub|audio|dm|…|none  播放中从外面推开 / 收起面板（「画面让位」实测，docs/player.md §9）
 *   -e engine exoplayer|vlc|mpv|auto  把「设置 › 播放 › 内核」写成这个值（实测用；写的是这个应用自己的设置）
 * debug 包用 `-Pgugaart.mockAccount=http://<主机>:<端口>` 构建时，第一次启动静默登录那台 Mock（夹具账号；release 里没有）。
 */
class MainActivity : ComponentActivity() {
    private var scope: CoroutineScope? = null
    private var appGraph: AppGraph? = null
    private var engineHost: AndroidEngineHost? = null
    private var player: AndroidPlayerPlatform? = null
    private var composeView: ComposeView? = null
    private val tv: Boolean get() = BuildConfig.DEVICE_FLAVOR == "tv"
    private val panelDriver = mutableStateOf<String?>(null)

    /** 导入弹幕字体：系统文件选择器（SAF，`OpenDocument`）。结果回到 [fontPick] 里等着的那个协程。 */
    private var fontPick: kotlinx.coroutines.CompletableDeferred<android.net.Uri?>? = null
    private val fontPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        fontPick?.complete(uri)
        fontPick = null
    }

    /** 配置备份：系统文件选择器（SAF）存一份（CreateDocument）/ 选一份（OpenDocument）。电视上多半没有这个界面 → 起不来就当取消。 */
    private var backupSave: kotlinx.coroutines.CompletableDeferred<android.net.Uri?>? = null
    private val backupSaver = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")) { uri ->
        backupSave?.complete(uri)
        backupSave = null
    }
    private var backupOpen: kotlinx.coroutines.CompletableDeferred<android.net.Uri?>? = null
    private val backupOpener = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        backupOpen?.complete(uri)
        backupOpen = null
    }

    private val backupFiles by lazy {
        com.gugaart.ui.backup.BackupFileHost(
            save = { name, text ->
                val wait = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>()
                backupSave?.complete(null)
                backupSave = wait
                val launched = runCatching { backupSaver.launch(name) }.isSuccess
                val uri = if (launched) wait.await() else null
                if (uri == null) false else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null }.getOrDefault(false)
                }
            },
            open = {
                val wait = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>()
                backupOpen?.complete(null)
                backupOpen = wait
                val launched = runCatching { backupOpener.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }.isSuccess
                val uri = if (launched) wait.await() else null
                if (uri == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { contentResolver.openInputStream(uri)?.use { s -> s.readBytes().takeIf { it.size <= 4 * 1024 * 1024 }?.toString(Charsets.UTF_8) ?: "" } }.getOrNull()
                }
            },
        )
    }

    /** 平台那一半：选文件（只读这一次，不要持久的读权限）→ 字节；字体复制进应用自己的 `files/fonts/`。 */
    private val fontImport by lazy {
        val dir = java.io.File(filesDir, "fonts")
        com.gugaart.ui.data.FontImportHost(
            pick = {
                val wait = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>()
                fontPick?.complete(null)
                fontPick = wait
                // 字体文件的 MIME 各家文件管理器给得不一样：都列出来，校验在界面那一半按文件头做
                fontPicker.launch(arrayOf("font/*", "application/x-font-ttf", "application/x-font-otf", "application/vnd.ms-opentype", "application/octet-stream"))
                val uri = wait.await()
                if (uri == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val name = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "font.ttf"
                    val max = com.gugaart.ui.data.ImportedFontStore.MAX_BYTES
                    val bytes = contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            // 超过上限就不再往内存里读（界面那一半会报「太大了」）
                            if (total > max + 1) break
                            out.write(buf, 0, n)
                        }
                        out.toByteArray()
                    } ?: ByteArray(0)
                    if (bytes.size > max) com.gugaart.ui.data.PickedFont(name, ByteArray(0), tooLarge = true) else com.gugaart.ui.data.PickedFont(name, bytes)
                }
            },
            write = { name, bytes ->
                dir.mkdirs()
                java.io.File(dir, name).also { it.writeBytes(bytes) }.absolutePath
            },
            delete = { path -> java.io.File(path).takeIf { it.parentFile == dir }?.delete() },
            exists = { path -> java.io.File(path).isFile },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            // 界面线程不许碰网络：debug 包把每一次这样的调用连同堆栈打进日志
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyLog().build())
        }
        GlassPlatform.init(this)
        enableEdgeToEdge()
        val override = if (!BuildConfig.DEBUG) null else when (intent.getStringExtra("glass")) {
            "live" -> GlassBackend.Live
            "gl" -> GlassBackend.ControlledGl
            "plates" -> GlassBackend.Plates
            "solid" -> GlassBackend.Solid
            else -> null
        }
        val route = intent.getStringExtra("route")?.takeIf { BuildConfig.DEBUG }?.let(::parseRoute) ?: Route.Home

        // 视频层在 Compose 视图下面
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val layer = VideoLayer(this)
        layer.install(root)
        val host = AndroidEngineHost(this, layer).also { engineHost = it }
        val platform = AndroidPlayerPlatform(this, host, tv).also { player = it }

        val deps = androidDeps(override, platform)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        val graph = AppGraph(deps, appScope, route)
        appGraph = graph
        // 电视先不开备用 CDN 连接（上游只在手机上量过）
        if (tv) com.gugugaga.nativeapp.player.ExoNet.spare.enabled = false
        // 运行日志的每一行也进 logcat（标签 GuguPlayer；行里的地址和令牌上游已经打过码）
        graph.log.sink = { level, message -> Log.i(PLAYER_TAG, "[$level] $message") }
        Log.i(TAG, "flavor=${BuildConfig.DEVICE_FLAVOR} api=${Build.VERSION.SDK_INT} mode=${deps.uiMode} secure=${deps.secureMode()} mock=${deps.debug.mockAccount != null} route=${route::class.simpleName}")
        installAndroidCrashGuard(graph)
        // 会话状态一行一条（不带账号和令牌）：设备上的脚本等 `session=SignedIn` 再继续
        appScope.launch {
            var last: SessionStatus? = null
            graph.session.status.collect { st ->
                if (st != last) {
                    last = st
                    Log.i(TAG, "session=${st.name}")
                }
            }
        }
        applyDebugExtras(intent, graph, first = true)

        // 返回（按键 / 手势 / 预测式返回）：先播放页，再应用的返回栈；在首页要 2 秒内第二次才离开
        val exitConfirm = ExitConfirm(clock = { android.os.SystemClock.uptimeMillis() })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (PlayerKeyRouter.dispatch(ComposeKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))) { exitConfirm.reset(); return }
                if (graph.nav.handleBack()) { exitConfirm.reset(); return }
                if (!exitConfirm.onRootBack()) {
                    ExitHint.show()
                    return
                }
                ExitHint.hide()
                if (!moveTaskToBack(true)) finish()
            }
        })

        val compose = ComposeView(this).apply {
            setContent {
                // 只有深色主题：系统栏的图标一直是浅色
                LaunchedEffect(Unit) {
                    val c = WindowCompat.getInsetsController(window, window.decorView)
                    c.isAppearanceLightStatusBars = false
                    c.isAppearanceLightNavigationBars = false
                }
                CompositionLocalProvider(
                    LocalPlayerPanelDriver provides panelDriver.takeIf { BuildConfig.DEBUG },
                    // 导入弹幕字体：手机 = 系统文件选择器（SAF）；电视没有文件选择器 → 不给（界面上写明）
                    com.gugaart.ui.data.LocalFontImport provides fontImport.takeIf { !tv },
                    // 配置备份的本地文件：手机 = 系统文件选择器；电视多半没有 → 不给（电视用 WebDAV）
                    com.gugaart.ui.backup.LocalBackupFiles provides backupFiles.takeIf { !tv },
                ) {
                    GugaartApp(graph)
                }
            }
        }
        composeView = compose
        root.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    /** 没人接住的异常：写进运行日志；主线程上的仍然交还给系统（吞掉会留下一个死掉的窗口）。 */
    private fun installAndroidCrashGuard(graph: AppGraph) {
        installCrashReporting(graph)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val main = Looper.getMainLooper().thread
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            val reported = CrashGuard.report(t)
            if (!reported || thread === main) previous?.uncaughtException(thread, t)
        }
    }

    /** 调试参数（只在 debug 包里）。[first]：冷启动时路由已经交给 AppGraph 了，不再导航一次。 */
    private fun applyDebugExtras(intent: Intent, graph: AppGraph, first: Boolean) {
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra("engine")?.let { graph.settings.patch("playback", "engine" to JsonPrimitive(it)) }
        if (intent.hasExtra("panel")) panelDriver.value = intent.getStringExtra("panel")?.takeIf { it != "none" }
        if (!first) intent.getStringExtra("route")?.takeIf { it.isNotEmpty() }?.let { graph.nav.navigate(parseRoute(it)) }
    }

    /** 播放器显示时每个键先给它；返回是返回回调的事。遥控器 / 耳机上 Compose 不认识的键先换成播放器的媒体键。 */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_DOWN) return true
            if (event.action == KeyEvent.ACTION_UP) {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        if (player?.playerShowing == true) {
            val code = KeyRemap.normalize(event.keyCode)
            val routed = if (code == event.keyCode) event else KeyEvent(event.downTime, event.eventTime, event.action, code, event.repeatCount, event.metaState)
            if (PlayerKeyRouter.dispatch(ComposeKeyEvent(routed))) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        appGraph?.let { applyDebugExtras(intent, it, first = false) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 对话框 / 分屏 / 通知栏之后系统栏会回来：播放器显示着就再藏一次
        val p = player ?: return
        if (hasFocus && p.playerShowing && !tv) p.immersive(true)
    }

    // ---------------------------------------------------------------- 画中画（手机）

    private val pipAvailable: Boolean
        get() = !tv && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    /** 正在播放时回桌面 / 进最近任务：进画中画接着放。 */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val p = player ?: return
        val h = engineHost ?: return
        if (!pipAvailable || !p.playerShowing || !h.isPlayingVideo) return
        if (!p.enterPictureInPicture()) Log.w(TAG, "enterPictureInPictureMode refused")
    }

    /** 画中画里只有画面：Compose 界面先藏起来，窗口回来再显示。 */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        composeView?.visibility = if (isInPictureInPictureMode) View.INVISIBLE else View.VISIBLE
        player?.onPictureInPictureChanged(isInPictureInPictureMode)
    }

    override fun onResume() {
        super.onResume()
        // 应用内更新：从系统的「安装未知应用」设置页回来，接着装等着的那个更新（和上游壳一样）
        appGraph?.update?.onAppResumed()
        // 安装确认被放到后台（或下完时应用在后台、确认没弹出来）：回来时把卡住的「正在安装……」报掉，号外变成「再试一次」
        appGraph?.let { com.gugaart.ui.update.UpdateStall.check(it) }
    }

    override fun onStop() {
        super.onStop()
        // 离开应用（不是进画中画）：暂停，已经加载的都留着
        if (!isInPictureInPictureMode) engineHost?.pauseAllForBackground()
    }

    override fun onDestroy() {
        engineHost?.releaseAll()
        scope?.cancel()
        super.onDestroy()
    }

    /** 系统关了动画（动画时长缩放 = 0）；读不到 = false。 */
    private fun systemReducedMotion(): Boolean = runCatching {
        android.provider.Settings.Global.getFloat(contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
            !android.animation.ValueAnimator.areAnimatorsEnabled()
    }.getOrDefault(false)

    private fun androidDeps(glassOverride: GlassBackend?, player: AndroidPlayerPlatform): PlatformDeps {
        // 存储名和上游原生版不同（"gugaart_*"）：两个应用本来就是两个沙箱，这里只是说明不共用
        val local = PrefsLocalStore(this, "gugaart_local")
        val secure = AndroidKeystoreStore(this, "gugaart_secure")
        // 多个弹幕服务器：清单 + 本机回环上的中转（两台以上时上游读到的主弹幕地址是中转；ui/.../danmaku/）。正式包没有默认弹幕地址
        val secureForApp = com.gugaart.ui.danmaku.withDanmakuRelay(secure, local, null)
        // Trakt：编进包里的应用（.env；没配 = 设置里写「这一版不能连 Trakt」）
        com.gugaart.ui.trakt.TraktService.install(BuildConfig.TRAKT_CLIENT_ID, BuildConfig.TRAKT_CLIENT_SECRET)
        // DLNA 投屏：手机能投到电视（电视自己是被投的一方，不装）
        if (!tv) com.gugaart.ui.cast.Cast.host = com.gugaart.ui.cast.DlnaCastHost {
            val wifi = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val lock = wifi.createMulticastLock("gugaart-dlna").apply {
                setReferenceCounted(false)
                acquire()
            }
            AutoCloseable { runCatching { lock.release() } }
        }
        // 播放器里开播的提示（徽章下面那一行；徽章本身哪里都不变灰，decisions.md D-mac-6）要知道这台设备的屏幕能显示什么：HDR 类型（Display）+ 有没有杜比视界解码器（MediaCodecList）
        com.gugaart.ui.player.DeviceAv.caps = runCatching {
            @Suppress("DEPRECATION")
            val types = windowManager.defaultDisplay.hdrCapabilities?.supportedHdrTypes?.toSet().orEmpty()
            val dvDecoder = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS).codecInfos
                .any { info -> !info.isEncoder && info.supportedTypes.any { it.equals("video/dolby-vision", ignoreCase = true) } }
            com.gugaart.ui.player.AvCaps(
                hdr10 = android.view.Display.HdrCapabilities.HDR_TYPE_HDR10 in types,
                hdr10Plus = android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in types,
                hlg = android.view.Display.HdrCapabilities.HDR_TYPE_HLG in types,
                dolbyVision = android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types && dvDecoder,
            )
        }.getOrNull()
        // 播放器里的网速：本应用一共收了多少字节（ExoPlayer / VLC / mpv 都在本进程里）；系统不给（UNSUPPORTED）就退回内核自己的估计
        com.gugaart.ui.player.NetTraffic.rxBytes = {
            android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()).takeIf { it != android.net.TrafficStats.UNSUPPORTED.toLong() }
        }
        val deviceId = local.get(LocalKeys.DEVICE_ID) ?: "gugaart-${UUID.randomUUID()}".also { local.set(LocalKeys.DEVICE_ID, it) }
        return PlatformDeps(
            uiMode = if (tv) UiMode.Tv else UiMode.Phone,
            identity = ClientInfo(device = Build.MODEL ?: "Android", deviceId = deviceId, version = BuildConfig.VERSION_NAME),
            local = local,
            secure = secureForApp,
            secureMode = { secure.mode },
            appVersion = BuildConfig.VERSION_NAME,
            platformLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${if (tv) "TV" else "手机"}",
            // release 里是空串（构建脚本只给 debug 这个字段写值）
            debug = DebugOptions(mockAccount = parseMockAccount(BuildConfig.MOCK_ACCOUNT)),
            glassBackendOverride = glassOverride,
            glLayer = { host, mod, onFailure -> ControlledGlLayer(host, mod) { msg -> onFailure(msg) } },
            copyText = { text ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("diagnostics", text))
            },
            player = player,
            blobCache = FileBlobCache(File(cacheDir, "tmdb-logos")),
            imageCache = FileBlobCache(
                File(cacheDir, "images"),
                maxEntries = com.gugugaga.app.data.ImageDiskCache.MAX_ENTRIES,
                maxBytes = com.gugugaga.app.data.ImageDiskCache.MAX_BYTES,
            ),
            danmakuComments = CommentBlobStore(
                FileBlobCache(File(cacheDir, "danmaku-comments"), maxEntries = Int.MAX_VALUE, maxBytes = com.gugugaga.danmaku.match.COMMENT_CACHE_MAX_BYTES),
            ),
            reducedMotion = { systemReducedMotion() },
            productName = "咕咕嘎嘎",
            // 应用内更新走 GitHub（com.gugaart.ui.update.GithubUpdate）：只在正式包里开；debug 包的包名不同（.debug），装正式包会变成另一个应用
            updateHost = if (BuildConfig.DEBUG) null else com.gugaart.ui.update.GithubUpdate.wrap(com.gugugaga.app.update.AndroidUpdateHost(this, tv)),
            updateConfig = if (BuildConfig.DEBUG) com.gugugaga.app.update.UpdateConfig(versionName = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE)
            else com.gugaart.ui.update.GithubUpdate.config(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, if (tv) "android-tv" else "android-mobile"),
            // 更新检查的传输层：国内备用镜像 + 超时转普通异常（修「检查中…」卡死；ui/update/UpdateMirrors.kt）
            updateTransport = if (BuildConfig.DEBUG) null else com.gugaart.ui.update.UpdateCheckTransport(com.gugugaga.core.KtorTransport(com.gugugaga.core.platformHttpClient())),
            meteredNetwork = {
                runCatching { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).isActiveNetworkMetered }.getOrDefault(false)
            },
        )
    }

    private companion object {
        const val TAG = "GugaArt"
    }
}
