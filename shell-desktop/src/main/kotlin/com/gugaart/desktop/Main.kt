package com.gugaart.desktop

import com.gugaart.desktop.upstream.StageOptions
import com.gugaart.desktop.upstream.StageSession
import com.gugaart.desktop.upstream.windowsReducedMotion
import com.gugugaga.app.nav.Route
import com.gugugaga.app.nav.parseRoute
import com.gugugaga.app.platform.CommentBlobStore
import com.gugugaga.app.platform.DebugOptions
import com.gugugaga.app.platform.DpapiKeyValueStore
import com.gugugaga.app.platform.FileBlobCache
import com.gugugaga.app.platform.FileLocalStore
import com.gugugaga.app.platform.LocalKeys
import com.gugugaga.app.platform.PlatformDeps
import com.gugugaga.app.platform.WindowControls
import com.gugugaga.app.platform.parseMockAccount
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.core.ClientInfo
import com.gugugaga.ds.glass.GlassBackend
import com.gugugaga.ds.theme.UiMode
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.util.UUID

/**
 * Windows 入口。窗口 = 上游的窗口栈（不透明底板 + mpv `--wid` 子窗口 + 透明 Compose 窗口，`upstream/StageApp.kt`，
 * 上游 docs/native-player-windows.md）：整个应用画在透明窗口里，只有播放页在画面的位置留一个透明的洞。
 *
 *   --data-dir=<目录>            设置和安全存储放哪（默认 %APPDATA%\GugaArt；和上游原生版的 GuguGaga-native 分开，互不影响）
 *   --backend=live|plates|solid  强制玻璃后端（调试）
 *   --ui=phone|tv                用手机 / 电视的版式开窗口，只为在电脑上看版式（手机 = 横屏 905 × 412，播放器就是这个方向）
 *   --route=/item/123            一开始落在哪个路由（和上游同一套路径）
 *   --bounds=x,y,w,h             窗口位置和大小（dp；不记住）
 *
 * 只在开发运行时有效（Gradle `:shell-desktop:run` 传 `-Dgugaart.dev=true`；打出来的包里没有）：
 *   -Dgugaart.mockAccount=<地址>  Mock Emby 的夹具账号，第一次启动时静默登录（`-Pgugaart.mockAccount=http://127.0.0.1:<端口>`；从不是真实服务器）
 *   --probe=<目录>                「画面让位」实测：见 [ViewportProbe]
 *   --accept=<场景+场景|all>      macOS 真窗口验收（`--accept-out=<目录>` 放报告和截图）：见 `mac/accept/`、docs/acceptance-mac.md
 *   --engine=mpv|vlc              探针用哪个内核（写进探针自己的数据目录的设置里）
 */
/**
 * 导入弹幕字体的平台那一半（Windows）：系统的文件对话框（只列 ttf / otf）；字体复制到 `<数据目录>/fonts/`。
 * 对话框在 AWT 事件线程上开（模态），读文件在 IO 线程。
 */
/** 配置备份的文件那一半（Windows）：系统的保存 / 打开对话框（模态，在 AWT 事件线程上开），读写在 IO 线程。 */
internal fun backupFileHost(): com.gugaart.ui.backup.BackupFileHost {
    fun dialog(title: String, mode: Int, name: String?): File? {
        var chosen: File? = null
        java.awt.EventQueue.invokeAndWait {
            val d = java.awt.FileDialog(null as java.awt.Frame?, title, mode)
            if (name != null) d.file = name else d.file = "*.json"
            d.setFilenameFilter { _, n -> n.endsWith(".json", true) }
            d.isVisible = true
            chosen = d.files.firstOrNull()
        }
        return chosen
    }
    return com.gugaart.ui.backup.BackupFileHost(
        save = { name, text ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val f = dialog("导出配置备份", java.awt.FileDialog.SAVE, name) ?: return@withContext false
                val target = if (f.name.endsWith(".json", true)) f else File(f.parentFile, f.name + ".json")
                target.writeText(text, Charsets.UTF_8)
                true
            }
        },
        open = {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val f = dialog("从备份还原", java.awt.FileDialog.LOAD, null) ?: return@withContext null
                // 备份文件很小；太大的不是备份
                if (f.length() > 4L * 1024 * 1024) "" else f.readText(Charsets.UTF_8)
            }
        },
    )
}

internal fun fontImportHost(dataDir: File): com.gugaart.ui.data.FontImportHost {
    val dir = File(dataDir, "fonts")
    return com.gugaart.ui.data.FontImportHost(
        pick = {
            val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                var chosen: File? = null
                java.awt.EventQueue.invokeAndWait {
                    val d = java.awt.FileDialog(null as java.awt.Frame?, "导入弹幕字体", java.awt.FileDialog.LOAD)
                    d.file = "*.ttf;*.otf"
                    d.setFilenameFilter { _, name -> name.endsWith(".ttf", true) || name.endsWith(".otf", true) }
                    d.isVisible = true
                    chosen = d.files.firstOrNull()
                }
                chosen
            }
            if (file == null) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // 太大的不读进内存（上限在界面那一半再判一次）
                if (file.length() > com.gugaart.ui.data.ImportedFontStore.MAX_BYTES) com.gugaart.ui.data.PickedFont(file.name, ByteArray(0), tooLarge = true)
                else com.gugaart.ui.data.PickedFont(file.name, file.readBytes())
            }
        },
        write = { name, bytes ->
            dir.mkdirs()
            File(dir, name).also { it.writeBytes(bytes) }.absolutePath
        },
        delete = { path -> File(path).takeIf { it.parentFile == dir }?.delete() },
        exists = { path -> File(path).isFile },
    )
}

fun main(args: Array<String>) {
    // 冷启动计时的起点（1.5.1 perf-win）：在任何重活之前
    com.gugaart.ui.data.BootPerf.startMs = com.gugugaga.core.currentTimeMillis()
    // 性能实测的路标（GUGAART_PERF=1 才记；只记毫秒，见 mac/MacPerf.kt）
    com.gugaart.desktop.mac.PerfMarks.mark("main")
    // 和上游一样：系统代理要在任何联网类加载之前装好
    com.gugugaga.core.SystemProxySelector.install()
    val opt = args.mapNotNull { a -> a.removePrefix("--").split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
    val dev = System.getProperty("gugaart.dev") == "true"
    if (dev) println("GuguPlayer [perf] args ${args.joinToString(" ")}")
    val dataDir = opt["data-dir"]?.let(::File) ?: defaultDataDir()
    // 覆盖安装后桌面图标还是旧的：换了版本第一次启动刷一次 Windows 的图标缓存（IconRefresh.kt）
    IconRefresh.runOnce(dataDir, BuildInfo.APP_VERSION, dev)
    val backend = opt["backend"]?.let { b -> GlassBackend.entries.firstOrNull { it.name.equals(b, ignoreCase = true) } }
    val uiMode = when (opt["ui"]?.lowercase()) {
        "phone" -> UiMode.Phone
        "tv" -> UiMode.Tv
        else -> UiMode.Desktop
    }
    val route = opt["route"]?.let(::parseRoute) ?: Route.Home
    val bounds = opt["bounds"]?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 4 }?.let { Rectangle(it[0], it[1], it[2], it[3]) }
        ?: if (uiMode == UiMode.Phone) Rectangle(60, 60, 905, 412) else null
    // Mock Emby（tools/mock-emby）的夹具账号 demo / demo：只在开发运行时、只对 -Pgugaart.mockAccount 给的那台 Mock 用，从不是真实凭据（Windows / macOS 都认）。
    // 两个壳共用：Mac 的真窗口验收也要用 Mock 登录（不输入任何真实账号密码）。
    val mock = System.getProperty("gugaart.mockAccount")?.takeIf { dev && Regex("""^https?://[^\s"\\]+$""").matches(it) }
    val debug = if (mock != null) DebugOptions(mockAccount = parseMockAccount("""{"server":"$mock","username":"demo","password":"demo"}"""), defaultSecrets = System.getenv("GUGAART_DMJIT_DANMAKU")?.let { com.gugugaga.app.platform.DefaultSecrets(danmakuApiUrl = it) }) else DebugOptions.None // DMJIT-TEMP
    // 1.5.0 跨服务器验收：--mock-servers=<地址>+<地址>+… 把几台本地 Mock 预先存成几台服务器（同样只在开发运行时，夹具账号 demo / demo）
    if (dev) com.gugaart.ui.data.DevMockServers.urls = com.gugaart.ui.data.DevMockServers.parseArg(opt["mock-servers"])
    // macOS：一扇 Compose Window（系统标题栏 + 红绿灯），不用 Win32 窗口栈（ADR 0004 §2.2）。Windows 路径一行不动。
    if (System.getProperty("os.name").startsWith("Mac")) {
        // M1-6：深色外观 + 应用名，要在任何 AWT / Compose 调用之前设（X06 / X08）
        com.gugaart.desktop.mac.macSystemProperties().forEach { (k, v) -> System.setProperty(k, v) }
        com.gugaart.desktop.mac.MacSession.start(
            com.gugaart.desktop.mac.MacOptions(
                dataDir = opt["data-dir"]?.let(::File) ?: com.gugaart.desktop.mac.macDataDir(null),
                route = route,
                backend = backend,
                uiMode = uiMode,
                bounds = bounds,
                debug = debug,
                probeDir = opt["probe"]?.takeIf { dev }?.let(::File),
                probeRounds = opt["probe-rounds"]?.toIntOrNull()?.coerceIn(1, 20) ?: 1,
                probeExtras = opt["probe-extras"] == "true",
                probeSearch = opt["probe-search"],
                probeSecs = opt["probe-secs"]?.toIntOrNull()?.coerceIn(5, 600) ?: 40,
                probeStrm = opt["probe-strm"]?.takeIf { it in setOf("auto", "direct", "proxy") },
                // 真窗口验收（开发运行才有；scripts/accept-mac.mjs）：--accept=<场景+场景|all> --accept-out=<目录> [--accept-merge=true]
                acceptScenarios = opt["accept"]?.takeIf { dev }?.let { com.gugaart.desktop.mac.accept.parseScenarioList(it, com.gugaart.desktop.mac.accept.AcceptScenarios.NAMES) },
                acceptOut = opt["accept-out"]?.takeIf { dev && opt["accept"] != null }?.let(::File),
                acceptMerge = opt["accept-merge"] == "true",
                // 性能实测（GUGAART_PERF=1 才认）：--perf=<目录> [--perf-steps=cold,idle,play,switch]
                perfOut = opt["perf"]?.takeIf { com.gugaart.desktop.mac.PerfMarks.enabled }?.let(::File),
                perfSteps = opt["perf-steps"]?.split(",", "+")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: listOf("cold", "idle", "play", "switch"),
            ),
        )
        return
    }
    val probe = opt["probe"]?.takeIf { dev }?.let { ViewportProbe(File(it), opt["engine"], route) }
    // 测量驱动缝（1.5.1 perf-win2，只在开发运行 + 给了 --perf-drive 时）：程序化导航 / 切服务器 / 暂停，不改界面
    val drive = opt["perf-drive"]?.takeIf { dev }?.let { com.gugaart.desktop.perf.PerfDrive(com.gugaart.desktop.perf.PerfDrive.parse(it)) }
    val session = StageSession.start(
        StageOptions(
            dataDir = dataDir,
            // 探针自己导航（先登录、写好内核设置）
            route = if (probe != null) Route.Home else route,
            backend = backend,
            debug = debug,
            bounds = bounds,
            persistWindow = bounds == null,
            uiMode = uiMode,
            // 导入弹幕字体：Windows 的文件对话框 + 应用数据目录下的 fonts/
            // 配置备份：Windows 的保存 / 打开对话框
            wrap = { content ->
                androidx.compose.runtime.CompositionLocalProvider(
                    com.gugaart.ui.data.LocalFontImport provides fontImportHost(dataDir),
                    com.gugaart.ui.backup.LocalBackupFiles provides backupFileHost(),
                ) {
                    (drive?.wrap ?: { it() }) { (probe?.wrap ?: { it() })(content) }
                }
            },
        ),
    )
    // 窗口栈已经上屏（StageSession.start 返回 = 开完窗）：冷启动的一个里程碑
    com.gugaart.ui.data.BootPerf.elapsedMs().takeIf { it >= 0 }?.let { println("GuguPlayer [perf] boot window-shown +$it ms") }
    if (dev) com.gugaart.desktop.perf.UiStallWatch.start()
    probe?.run(session)
    drive?.run(session)
}

private fun defaultDataDir(): File {
    val base = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File) ?: File(System.getProperty("user.home"), ".config")
    return File(base, "GugaArt")
}

/**
 * 上游的共享逻辑要从 Windows 拿的东西（存储、身份、缓存目录、窗口键、播放器）。和上游壳 `desktopApp/…/Main.kt` 的 `desktopDeps` 同一个接法；
 * 应用内更新走 GitHub（com.gugaart.ui.update.GithubUpdate；开发运行 gugaart.dev 时不检查）。没有接的：TMDB 默认密钥、预置测试服务器。
 */
internal fun desktopDeps(
    dataDir: File,
    uiMode: UiMode,
    backend: GlassBackend?,
    controls: WindowControls? = null,
    player: PlayerPlatform? = null,
    debug: DebugOptions? = null,
    onQuit: () -> Unit = { kotlin.system.exitProcess(0) },
): PlatformDeps {
    val local = FileLocalStore(File(dataDir, "local.json"))
    val secure = DpapiKeyValueStore(File(dataDir, "gugu-secure-store.json"))
    // 多个弹幕服务器：清单 + 本机回环上的中转（两台以上时上游读到的主弹幕地址是中转；ui/.../danmaku/）
    val secureForApp = com.gugaart.ui.danmaku.withDanmakuRelay(secure, local, debug?.defaultSecrets?.danmakuApiUrl)
    // Trakt：编进包里的应用（.env；没配 = 设置里写「这一版不能连 Trakt」）
    com.gugaart.ui.trakt.TraktService.install(BuildInfo.TRAKT_CLIENT_ID, BuildInfo.TRAKT_CLIENT_SECRET)
    // DLNA 投屏：电脑也能投到电视
    com.gugaart.ui.cast.Cast.host = com.gugaart.ui.cast.DlnaCastHost()
    // 每次安装一个稳定的 id（不从机器信息推导：它会显示在 Emby 的「设备」里）
    val deviceId = local.get(LocalKeys.DEVICE_ID) ?: "gugaart-${UUID.randomUUID()}".also { local.set(LocalKeys.DEVICE_ID, it) }
    val computer = System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
    // 应用内更新（上游的 Windows 宿主：下好 MSI，退出后由一个 .cmd 等本进程结束再装、再启动）；开发运行（-Dgugaart.dev）不检查
    val dev = System.getProperty("gugaart.dev") == "true"
    val updater = if (dev) null else com.gugugaga.app.update.DesktopUpdateHost(
        updateDir = File(dataDir, "cache/update"),
        appExe = { com.gugugaga.app.update.DesktopUpdateHost.launcherExe() },
        onQuit = onQuit,
    )
    // 片库搜索的本地索引（1.4.1，docs/search.md §1）：按「服务器 + 用户」一份，文件名是键的 SHA-256，里面没有令牌
    com.gugaart.ui.search.SearchIndexStore.disk = FileBlobCache(File(dataDir, "cache/search-index"), maxEntries = com.gugaart.ui.search.SearchIndexStore.DISK_MAX_ENTRIES, maxBytes = com.gugaart.ui.search.SearchIndexStore.DISK_MAX_BYTES)
    // 索引各阶段的耗时进控制台（perf-win 测量缝；不设就没有日志）
    com.gugaart.ui.search.SearchIndexStore.perf = { println("GuguPlayer [perf] $it") }
    return PlatformDeps(
        uiMode = uiMode,
        identity = ClientInfo(device = if (computer != null) "Windows · $computer" else "Windows PC", deviceId = deviceId, version = BuildInfo.APP_VERSION),
        local = local,
        secure = secureForApp,
        secureMode = { secure.mode },
        appVersion = BuildInfo.APP_VERSION,
        platformLabel = "${System.getProperty("os.name")} ${System.getProperty("os.version")} · JVM ${System.getProperty("java.version")}",
        debug = debug ?: DebugOptions.None,
        windowControls = controls,
        glassBackendOverride = backend,
        copyText = { text -> Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) },
        player = player,
        blobCache = FileBlobCache(File(dataDir, "cache/tmdb-logos")),
        imageCache = FileBlobCache(
            File(dataDir, "cache/images"),
            maxEntries = com.gugugaga.app.data.ImageDiskCache.MAX_ENTRIES,
            maxBytes = com.gugugaga.app.data.ImageDiskCache.MAX_BYTES,
        ),
        danmakuComments = CommentBlobStore(
            FileBlobCache(File(dataDir, "cache/danmaku-comments"), maxEntries = Int.MAX_VALUE, maxBytes = com.gugugaga.danmaku.match.COMMENT_CACHE_MAX_BYTES),
        ),
        reducedMotion = { windowsReducedMotion() },
        productName = "咕咕嘎嘎",
        updateHost = updater?.let { com.gugaart.ui.update.GithubUpdate.wrap(it) },
        updateInstallProblem = updater?.let { u -> { u.readLastResultAndClear() } },
        updateConfig = if (updater == null) com.gugugaga.app.update.UpdateConfig(versionName = BuildInfo.APP_VERSION, versionCode = BuildInfo.VERSION_CODE)
        else com.gugaart.ui.update.GithubUpdate.config(BuildInfo.APP_VERSION, BuildInfo.VERSION_CODE, "windows-msi"),
        // 更新检查的传输层：国内备用镜像 + 超时转普通异常（修「检查中…」卡死；ui/update/UpdateMirrors.kt）
        updateTransport = updater?.let { com.gugaart.ui.update.UpdateCheckTransport(com.gugugaga.core.KtorTransport(com.gugugaga.core.platformHttpClient())) },
    )
}
