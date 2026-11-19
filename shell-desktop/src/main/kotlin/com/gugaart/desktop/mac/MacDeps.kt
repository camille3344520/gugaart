package com.gugaart.desktop.mac

import com.gugaart.desktop.BuildInfo
import com.gugugaga.app.platform.DebugOptions
import com.gugugaga.app.platform.CommentBlobStore
import com.gugugaga.app.platform.FileBlobCache
import com.gugugaga.app.platform.FileLocalStore
import com.gugugaga.app.platform.LocalKeys
import com.gugugaga.app.platform.PlatformDeps
import com.gugugaga.app.platform.WindowControls
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.app.update.UpdateConfig
import com.gugugaga.core.ClientInfo
import com.gugugaga.ds.glass.GlassBackend
import com.gugugaga.ds.theme.UiMode
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.InetAddress
import java.util.UUID

/**
 * macOS 的数据目录（ADR 0004 §2.8）：`~/Library/Application Support/GugaArt`，`--data-dir=` 可盖。
 * 和上游原生版的 `GuguGaga-native` 分开（decisions.md Q1「并存」）。
 */
internal fun macDataDir(override: String?, home: String = System.getProperty("user.home")): File =
    override?.let(::File) ?: File(home, "Library/Application Support/GugaArt")

/**
 * 上游共享逻辑在 Mac 上要的东西。照 `Main.kt` 的 `desktopDeps` 写（同一个壳里的两份接线），不同的只有：
 * 设备名 `Mac · <主机名>`、平台标签 macOS、更新宿主 = [MacUpdateHost]（下 `.dmg`、打开、退出；资产键 `macos-dmg`，M2-8 / D-lead-8；
 * 开发运行 `gugaart.dev` 不检查，同 Windows）、
 * 安全存储 = 钥匙串（[MacSecureStore]，M2-5 / D-lead-6；原来的明文文件第一次用时迁进去再删掉）、
 * 减弱动效 = 系统「减弱动态效果」（启动时读一次，[readReduceMotion]）。
 * 其余（local、弹幕中转、Trakt、DLNA、缓存目录、剪贴板、产品名）和 Windows 相同。
 * [keychain]：单测给假的；[reduceMotion]：单测给定值（不起子进程）。
 */
internal fun macDeps(
    dataDir: File,
    uiMode: UiMode,
    backend: GlassBackend?,
    controls: WindowControls? = null,
    player: PlayerPlatform? = null,
    debug: DebugOptions? = null,
    onQuit: () -> Unit = { kotlin.system.exitProcess(0) },
    keychain: KeychainApi = SecurityFrameworkKeychain,
    reduceMotion: Boolean = readReduceMotion(),
    log: (String) -> Unit = { println("GuguPlayer [secure] $it") },
    dev: Boolean = System.getProperty("gugaart.dev") == "true",
    updateDir: File = MacUpdateHost.defaultDir(),
): PlatformDeps {
    val local = FileLocalStore(File(dataDir, "local.json"))
    val secure = MacSecureStore(MacKeychainStore(keychain, dataDir, log = log), File(dataDir, MacSecureStore.PLAIN_FILE), log)
    // 多个弹幕服务器：清单 + 本机回环上的中转（两台以上时上游读到的主弹幕地址是中转；回环不触发「本地网络」权限）
    val secureForApp = com.gugaart.ui.danmaku.withDanmakuRelay(secure, local, debug?.defaultSecrets?.danmakuApiUrl)
    com.gugaart.ui.trakt.TraktService.install(BuildInfo.TRAKT_CLIENT_ID, BuildInfo.TRAKT_CLIENT_SECRET)
    // DLNA 投屏：代码照用（jvmShared）；打包后的「本地网络」权限说明是 M2-13
    com.gugaart.ui.cast.Cast.host = com.gugaart.ui.cast.DlnaCastHost()
    val deviceId = local.get(LocalKeys.DEVICE_ID) ?: "gugaart-${UUID.randomUUID()}".also { local.set(LocalKeys.DEVICE_ID, it) }
    val host = runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() }
    val updater = if (dev) null else MacUpdateHost(updateDir, onQuit)
    return PlatformDeps(
        uiMode = uiMode,
        identity = ClientInfo(device = "Mac · " + (host ?: "Mac"), deviceId = deviceId, version = BuildInfo.APP_VERSION),
        local = local,
        secure = secureForApp,
        secureMode = { secure.mode },
        appVersion = BuildInfo.APP_VERSION,
        platformLabel = "macOS ${System.getProperty("os.version")} · JVM ${System.getProperty("java.version")}",
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
        // 系统设置 › 辅助功能 › 显示 › 减弱动态效果（启动时读一次；改了要重开应用，和 Windows 读一次的做法一样）
        reducedMotion = { reduceMotion },
        productName = "咕咕嘎嘎",
        // 应用内更新（M2-8、D-lead-8）：下 .dmg → 校验 → 打开 → 退出；开发运行不检查（同 Windows）
        updateHost = updater?.let { com.gugaart.ui.update.GithubUpdate.wrap(it) },
        updateInstallProblem = null,
        updateConfig = if (updater == null) UpdateConfig(versionName = BuildInfo.APP_VERSION, versionCode = BuildInfo.VERSION_CODE)
        else com.gugaart.ui.update.GithubUpdate.config(BuildInfo.APP_VERSION, BuildInfo.VERSION_CODE, MacUpdateHost.ASSET_KEY),
    )
}

/**
 * 系统的「减弱动态效果」开着没有：`defaults read com.apple.universalaccess reduceMotion` 的输出是 `1`（任务单 M2-5）。
 * 没设过这个键时 `defaults` 报错退出 = 关着；子进程起不来 / 超时也当关着。[run] 单测注入。
 */
internal fun readReduceMotion(run: (List<String>) -> String? = ::runQuiet): Boolean =
    run(listOf("defaults", "read", "com.apple.universalaccess", "reduceMotion"))?.trim() == "1"

/** 起一个子进程、2 秒内拿到标准输出（退出码不是 0 = null）。 */
private fun runQuiet(cmd: List<String>): String? = runCatching {
    val p = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    val out = p.inputStream.bufferedReader().readText()
    if (!p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)) {
        p.destroy()
        return@runCatching null
    }
    if (p.exitValue() == 0) out else null
}.getOrNull()

/**
 * M1-6（对齐清单 X06 / X08）：Mac 分支在**任何 AWT / Compose 调用之前**设的两个系统属性。
 * 深色外观：标题栏区域、系统菜单、文件面板按深色画（系统浅色模式下也不出一条白边）；应用名：应用菜单和 Dock 上的名字。
 * 纯函数，单测逐项核对。
 */
internal fun macSystemProperties(): Map<String, String> = mapOf(
    "apple.awt.application.appearance" to "NSAppearanceNameDarkAqua",
    "apple.awt.application.name" to "咕咕嘎嘎",
)