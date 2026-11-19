package com.gugaart.desktop.mac

import com.gugugaga.app.player.ExternalPlayerInfo
import com.gugugaga.app.player.ExternalPlayers
import com.gugugaga.app.player.PlatformFacts
import com.gugugaga.app.player.PlatformKind
import com.gugugaga.app.player.PlayerEngine
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.app.player.Screenshots
import com.gugugaga.core.EngineId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * Mac 的播放内核在壳里的样子（ADR 0004 §2.7、ADR 0006）：上游 `:player-apple` 的 libmpv 进程内内核，画面是窗口层树里
 * Compose 之下的一个 Metal 层。实现在按构建切换的源码目录里（`shell-desktop/build.gradle.kts`）：挂了 `:player-apple`
 * （macOS 主机）时是 `src/macLibmpv/`，没挂（Windows 上编这个壳）时是 `src/macNoLibmpv/` 的空实现。
 */
internal interface MacEngines {
    /** 找到了 libmpv。 */
    val available: Boolean

    /** 会加载的那一份 libmpv（装好的应用里是包里的那一份）；没有 = null。启动时打进日志，`accept-mac --package` 靠它核对。 */
    val libraryPath: String? get() = null

    /** 回退链用的平台种类（上游有 U-mac-5 时是 macOS）。 */
    val kind: PlatformKind

    /** 给一个播放器页的引擎（共用一个内核和一个层）；没有 libmpv 时 null。 */
    fun create(): PlayerEngine?
    fun prewarm()

    /** 把屏幕上的画面（不带字幕）写进 [file]；没有画面或写不出来就抛。 */
    suspend fun saveScreenshot(file: File)

    /** 诊断：正在跑的内核的一个 mpv 属性（文本）；没有内核 = null。 */
    suspend fun property(name: String): String? = null

    /** 窗口所在的屏幕现在出不出得了 HDR（EDR 余量 > 1）；null = 这个构建不知道。[refreshDisplay] 之后才准。 */
    val displayHdr: kotlinx.coroutines.flow.StateFlow<Boolean>? get() = null

    /** 再问一次屏幕（窗口出来了、窗口挪了地方）。 */
    fun refreshDisplay() {}

    /**
     * 画中画（MP-7、D-lead-20；上游 U-mac-3）：这台 Mac / 这份 libmpv 做得了才有；null = 播放器顶栏不画钮、菜单里没有这一项（P18）。
     */
    val pictureInPicture: com.gugugaga.app.player.PictureInPicture? get() = null

    /**
     * 画中画小窗自己的播放 / 暂停、进度条和 ±跳接到播放器页的控制器上（关小窗 = 暂停也走这里；±跳 = 控制器 seek，M5-10 的硬指标，
     * 上游 PipTimeline）。没有画中画就什么也不做。
     */
    fun bindPipPlayback(controller: com.gugugaga.app.player.PlayController) {}

    /** 诊断（验收运行器）：上一次画中画的数（进入用时、帧数、最长帧间隔、> 100 ms 的次数…）；没有画中画 = null。 */
    fun pipReport(): Map<String, Any?>? = null

    /** 诊断：从现在起重新数帧间隔（运行器量 30 秒的那一段）。 */
    fun pipResetStats() {}

    /** 诊断：当前内核的声音输出（重新）配置过几次（画中画进出不该让它变）；没有内核 = null。 */
    suspend fun audioReconfigs(): Int? = null

    /** 应用退出：释放内核。 */
    suspend fun shutdown()
}

/**
 * Mac 的播放平台：只有 mpv（libmpv 进程内）；画面在界面之下（[videoUnderUi] = true，播放器页不铺底，根上清画布，
 * 见 `MacSession`）。找不到 libmpv 时不列任何内核，播放器页出说明。
 * 防休眠用 `caffeinate -d -w <本进程 pid>` 子进程（只结束自己起的那个；本进程退出它自动结束）。输入法 M2-12 再接。
 */
internal class MacPlayerPlatform(
    private val engines: MacEngines?,
    private val home: String = System.getProperty("user.home") ?: ".",
) : PlayerPlatform {
    private var caffeinate: Process? = null
    private val found: Boolean get() = engines?.available == true

    override fun platformFacts(): PlatformFacts {
        val listed = if (found) listOf(EngineId.Mpv) else emptyList()
        val external = runCatching { kotlinx.coroutines.runBlocking { MacExternalPlayers.list() } }.getOrDefault(emptyList())
        return PlatformFacts(
            kind = engines?.kind ?: PlatformKind.Windows,
            listed = listed,
            present = listed,
            // 嵌在窗口里的内核：不是「外置 / 不可用」的降级形态
            mpvMode = null,
            externalBridge = external.isNotEmpty(),
            externalCapability = external.isNotEmpty(),
            web = false,
        )
    }

    override fun createEngine(id: EngineId): PlayerEngine? = if (id == EngineId.Mpv && found) engines?.create() else null

    override fun prewarm(id: EngineId) {
        if (id == EngineId.Mpv && found) engines?.prewarm()
    }

    override val videoUnderUi: Boolean get() = true

    override fun keepAwake(on: Boolean) {
        if (on) {
            if (caffeinate == null) {
                caffeinate = runCatching {
                    ProcessBuilder("caffeinate", "-d", "-w", ProcessHandle.current().pid().toString())
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start()
                }.getOrNull()
            }
        } else {
            caffeinate?.destroy()
            caffeinate = null
        }
    }

    override fun shortcutKeys(on: Boolean) {
        // M2-12：播放器里临时关窗口的输入法；现在空操作
    }

    override val externalPlayers: ExternalPlayers = MacExternalPlayers

    /** 画中画：有内核、并且这台 Mac 做得了才有（没有 = null，钮和菜单项都不出）。 */
    override val pictureInPicture: com.gugugaga.app.player.PictureInPicture? = engines?.takeIf { it.available }?.pictureInPicture

    /** 截图：有内核才有这个钮（没有 = null，钮不画）。 */
    override val screenshots: Screenshots? = engines?.takeIf { it.available }?.let { MacScreenshots(it, home) }
}

/**
 * Mac 的截图：libmpv 的 `screenshot-to-file … video`（不带字幕和 OSD）写进 `~/Pictures/咕咕嘎嘎播放器/`，文件名是本地时间
 * （`2026-11-05 21-03-44.jpg`），同一秒再按一次加 ` (2)`。返回给界面的是不带用户名的短路径。
 */
internal class MacScreenshots(
    private val engines: MacEngines,
    private val home: String,
    private val now: () -> Long = System::currentTimeMillis,
) : Screenshots {
    override suspend fun save(): String {
        val (file, shown) = target(home, now())
        withContext(Dispatchers.IO) { file.parentFile?.mkdirs() }
        engines.saveScreenshot(file)
        return shown
    }

    companion object {
        /** [nowMs] 时拍的截图放哪、界面上怎么说。纯函数（单测）。 */
        internal fun target(home: String, nowMs: Long, exists: (File) -> Boolean = { it.exists() }): Pair<File, String> {
            val dir = File(File(home, "Pictures"), "咕咕嘎嘎播放器")
            val name = stamp(nowMs)
            var file = File(dir, "$name.jpg")
            var n = 2
            while (exists(file)) file = File(dir, "$name ($n).jpg").also { n++ }
            return file to "图片/咕咕嘎嘎播放器/${file.name}"
        }

        /** 本地时间 `2026-11-05 21-03-44`（没有冒号）。 */
        internal fun stamp(epochMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
            val d = java.time.Instant.ofEpochMilli(epochMs).atZone(zone)
            fun p(n: Int) = n.toString().padStart(2, '0')
            return "${d.year}-${p(d.monthValue)}-${p(d.dayOfMonth)} ${p(d.hour)}-${p(d.minute)}-${p(d.second)}"
        }
    }
}

/** Mac 的外部播放器：按存在与否报 SenPlayer / IINA / VLC / mpv，用 `open` 把流交出去（从当前位置开始）。 */
internal object MacExternalPlayers : ExternalPlayers {
    private val candidates = listOf(
        Triple("senplayer", "SenPlayer", "/Applications/SenPlayer.app"),
        Triple("iina", "IINA", "/Applications/IINA.app"),
        Triple("vlc", "VLC", "/Applications/VLC.app"),
        Triple("mpv", "mpv", "/opt/homebrew/bin/mpv"),
    )

    override suspend fun list(): List<ExternalPlayerInfo> = withContext(Dispatchers.IO) {
        candidates.filter { File(it.third).exists() }.map { ExternalPlayerInfo(it.first, it.second) }
    }

    override suspend fun open(playerId: String, url: String, title: String?, startMs: Long?, headers: Map<String, String>?) {
        val cmd = buildExternalCommand(playerId, url, startMs) ?: throw IOException("unknown external player: $playerId")
        withContext(Dispatchers.IO) {
            ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        }
    }

    /** 命令行拼法（纯函数，单测）：秒数向下取整；SenPlayer 的 url 要 URL 编码。 */
    internal fun buildExternalCommand(playerId: String, url: String, startMs: Long?): List<String>? {
        val sec = (startMs ?: 0L) / 1000
        return when (playerId) {
            "senplayer" -> listOf("open", "senplayer://x-callback-url/play?url=" + URLEncoder.encode(url, Charsets.UTF_8) + "&position=$sec")
            "iina" -> listOf("open", "-a", "IINA", "--args", "--mpv-start=$sec", url)
            "vlc" -> listOf("open", "-a", "VLC", "--args", "--start-time=$sec", url)
            "mpv" -> listOf("/opt/homebrew/bin/mpv", "--start=$sec", url)
            else -> null
        }
    }
}
/**
 * Mac 上「这台设备显示得了什么」（任务单 MP-8）：只给播放器里开播那一行提示用，徽章本身不变灰（decisions.md D-mac-6）。纯函数（单测）。
 * 内核是 libmpv + libplacebo：屏幕有 EDR 余量时 HDR10 / HDR10+ / HLG 都按 HDR 出，单层的杜比视界（profile 5 / 8）由 libplacebo 按 RPU 重塑
 * （实测《海王2》，ADR 0004 §4「实测」十四）；双层的（profile 7）mpv 不读增强层，只出 HDR10 基础层（实测《沙丘》，十三）。
 * 屏幕没有 EDR 余量（外接的普通显示器）：都是色调映射后的普通画面。
 */
internal fun macAvCaps(displayHdr: Boolean): com.gugaart.ui.player.AvCaps =
    com.gugaart.ui.player.AvCaps(hdr10 = displayHdr, hdr10Plus = displayHdr, hlg = displayHdr, dolbyVision = displayHdr, dolbyVisionDualLayer = false)
