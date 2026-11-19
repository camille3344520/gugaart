package com.gugaart.desktop.mac

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.gugaart.ui.player.LocalPlayerPanelDriver
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import java.io.File

/**
 * Mac 的「续看 → 画面在窗口里 → 画面让位 → 返回」实测探针（任务单 MP-5 的验收 ①②⑦，只在开发运行时：`--probe=<目录>`）。
 * 照 Windows 的 `ViewportProbe` 写：真窗口 + 真内核，用**已经登录好的会话**（`--data-dir`；探针不登录、不输入任何东西），
 * 对服务器只读（取「继续观看」的第一条、播放）。每一步 `screencapture` 窗口那一块，控制台打一行 `[probe]`；图由人打开看。
 * 控制台不打印条目名、地址、令牌。
 */
internal class MacViewportProbe(
    private val dir: File,
    private val route: Route,
    private val rounds: Int,
    private val extras: Boolean = false,
    /** `--probe-search=<片名>`：搜这个名字的第一部电影，从头播 [statSecs] 秒，每 2 秒打一行内核的数（网速、缓存、掉帧）。 */
    private val search: String? = null,
    private val statSecs: Int = 40,
    /** `--probe-strm=direct|auto|proxy`：这一次用的 strm 线路（跑完改回原值）。 */
    private val strmMode: String? = null,
    private val engines: MacEngines? = null,
) {
    private val panel = mutableStateOf<String?>(null)

    val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        CompositionLocalProvider(LocalPlayerPanelDriver provides panel) { content() }
    }

    /** 播放诊断：只读。不打印片名之外的东西——地址只报主机名。 */
    @Volatile private var keepOpen = false

    /** Returns true when the window is to be left open (paused) afterwards (`-Dgugaart.probe.keep=true`). */
    private fun stats(graph: AppGraph, client: com.gugugaga.core.EmbyClient, onUi: (() -> Unit) -> Unit, pause: () -> Unit, shot: (String) -> Unit = {}, wake: () -> Unit = {}): Boolean {
        val section = com.gugugaga.app.player.STRM_SETTINGS_SECTION
        val key = com.gugugaga.app.player.STRM_PROXY_MODE_KEY
        val before = com.gugugaga.app.player.StrmSettings.of(graph.settings.value).strmProxyMode
        if (strmMode != null) graph.settings.patch(section, key to kotlinx.serialization.json.JsonPrimitive(strmMode))
        try {
            val hits = kotlinx.coroutines.runBlocking { client.searchItems(search!!).items }
            // Emby 那边怎么标的（详情页的格式徽章读的就是这几项：ExtendedVideoType / ExtendedVideoSubType，认不出再看 VideoRange）
            val movies = hits.filter { it.type == "Movie" }
            movies.forEachIndexed { i, m ->
                val full = runCatching { kotlinx.coroutines.runBlocking { client.getItem(m.id) } }.getOrNull()
                val v = full?.mediaSources.orEmpty().flatMap { src -> src.mediaStreams.filter { it.type == "Video" } }
                println("[stat] movie #$i year=${m.productionYear} sources=${full?.mediaSources?.size} emby video: " + v.joinToString(" | ") { "${it.codec} ${it.width}x${it.height} VideoRange=${it.videoRange} ExtendedVideoType=${it.extendedVideoType} SubType=${it.extendedVideoSubType} (${it.extendedVideoSubTypeDescription})" })
            }
            val pick = System.getProperty("gugaart.probe.pick")?.toIntOrNull() ?: 0
            val item = movies.getOrNull(pick) ?: hits.firstOrNull { it.type == "Episode" }
            if (item == null) {
                println("[stat] no Movie / Episode named like the search term (${hits.size} hits)")
                return false
            }
            println("[stat] found ${item.type}; strmProxyMode=${com.gugugaga.app.player.StrmSettings.of(graph.settings.value).strmProxyMode} (was $before)")
            val startSec = System.getProperty("gugaart.probe.start")?.toLongOrNull() ?: 0L
            // MP-8：`-Dgugaart.probe.shots=true` 先到详情页截一张（格式徽章），开播几秒截一张（开播的徽章），控件出来再截一张（网速）
            val shots = System.getProperty("gugaart.probe.shots") == "true"
            if (shots) {
                println("[stat] device caps for the badges: ${com.gugaart.ui.player.DeviceAv.caps}")
                onUi { graph.nav.navigate(Route.Item(item.id)) }
                Thread.sleep(4_000)
                shot("s1-detail")
            }
            onUi { graph.nav.navigate(Route.Play(item.id, startMs = startSec * 1000)) }
            // 内核眼里的输出尺寸跟不跟得上层的尺寸（开播头几秒「画面比窗口小一圈」的排查）：每 0.1 秒一行，一共 9 秒
            if (shots) Thread({
                fun q(name: String): String = kotlinx.coroutines.runBlocking { engines?.property(name) } ?: "-"
                repeat(90) {
                    Thread.sleep(100)
                    println("[size] t=${(it + 1) / 10.0}s osd=${q("osd-dimensions/w")}x${q("osd-dimensions/h")} idle=${q("core-idle")} cache-pause=${q("paused-for-cache")} pause=${q("pause")} aspect=${q("video-aspect-override")} pos=${q("time-pos").take(8)}")
                }
            }, "gugu-probe-size").start()
            if (shots) Thread({
                Thread.sleep(4_500); shot("s2-start-badges")
                Thread.sleep(6_000); wake(); Thread.sleep(1_200); shot("s3-controls-net-speed")
            }, "gugu-probe-shots").start()
            fun p(name: String): String = kotlinx.coroutines.runBlocking { engines?.property(name) } ?: "-"
            val t0 = System.currentTimeMillis()
            var described = false
            while (System.currentTimeMillis() - t0 < statSecs * 1000L) {
                Thread.sleep(2_000)
                val open = p("stream-open-filename")
                if (!described && open != "-") {
                    described = true
                    // 对照用（本机命令行 mpv / curl 放同一个地址）：地址只写进调用者给的本地文件，不进日志
                    System.getProperty("gugaart.probe.urlFile")?.takeIf { it.isNotBlank() }?.let { f ->
                        runCatching {
                            val file = File(f)
                            file.writeText(open + "\n" + p("user-agent") + "\n" + p("http-header-fields") + "\n")
                            file.setReadable(false, false); file.setReadable(true, true)
                        }
                    }
                    val host = runCatching { java.net.URI(open).let { "${it.scheme}://${it.host}${if (it.port > 0) ":" + it.port else ""}" } }.getOrDefault("?")
                    println("[stat] stream host=$host proxy=${p("http-proxy").ifEmpty { "(none)" }} size=${p("file-size")} format=${p("file-format")} video=${p("video-codec").take(40)} ${p("video-params/w")}x${p("video-params/h")} ${p("video-params/hw-pixelformat")} ${p("video-params/primaries")}/${p("video-params/gamma")} fps=${p("container-fps")} bitrate(v)=${p("video-bitrate")}")
                }
                println(
                    "[stat] t=${(System.currentTimeMillis() - t0) / 1000}s pos=${p("time-pos").take(7)} speed=${p("cache-speed")} B/s cache=${p("demuxer-cache-duration").take(6)}s " +
                        "buffering=${p("paused-for-cache")} idle=${p("core-idle")} hwdec=${p("hwdec-current")} drop=${p("frame-drop-count")} decdrop=${p("decoder-frame-drop-count")} delayed=${p("vo-delayed-frame-count")} vfps=${p("estimated-vf-fps").take(6)} " +
                        // 这一帧有多亮（libplacebo 的峰值检测，PQ 0–1）：给「停在高光多的画面」找位置用
                        "maxpq=${p("video-out-params/max-pq-y").take(5)} avgpq=${p("video-out-params/avg-pq-y").take(5)} in-maxpq=${p("video-params/max-pq-y").take(5)} in-avgpq=${p("video-params/avg-pq-y").take(5)}",
                )
            }
            // 色彩 / 杜比视界：片源、解码后的参数、输出到层的参数
            fun group(prefix: String) = listOf("pixelformat", "hw-pixelformat", "colormatrix", "primaries", "gamma", "sig-peak", "light", "max-luma", "max-cll").joinToString(" ") { k -> "$k=${p("$prefix/$k")}" }
            println("[stat] video-dec-params: ${group("video-dec-params")}")
            println("[stat] video-params: ${group("video-params")}")
            println("[stat] video-target-params: ${group("video-target-params")}")
            val n = p("track-list/count").toIntOrNull() ?: 0
            for (i in 0 until n) if (p("track-list/$i/type") == "video") {
                println("[stat] video track: codec=${p("track-list/$i/codec")} ${p("track-list/$i/demux-w")}x${p("track-list/$i/demux-h")} dolby-vision-profile=${p("track-list/$i/dolby-vision-profile")} level=${p("track-list/$i/dolby-vision-level")} hdr=${p("track-list/$i/hdr")} format-name=${p("track-list/$i/format-name")}")
            }
            println("[stat] hwdec=${p("hwdec")} hwdec-current=${p("hwdec-current")} target-colorspace-hint=${p("target-colorspace-hint")} tone-mapping=${p("tone-mapping")} hdr-compute-peak=${p("hdr-compute-peak")} vo=${p("current-vo")} ctx=${p("current-gpu-context")} pos=${p("time-pos").take(8)} drop=${p("frame-drop-count")} decdrop=${p("decoder-frame-drop-count")} delayed=${p("vo-delayed-frame-count")}")
            println("[stat] page: path is play=${graph.nav.pathname.startsWith("/play")} phase-ok=${p("time-pos") != "-"}")
            if (System.getProperty("gugaart.probe.keep") == "true" && p("time-pos") != "-") {
                // `-Dgugaart.probe.pauseAt=<秒>`：放到这个位置再停（最多等 60 秒）
                System.getProperty("gugaart.probe.pauseAt")?.toDoubleOrNull()?.let { at ->
                    val until = System.currentTimeMillis() + 60_000
                    while (System.currentTimeMillis() < until && (p("time-pos").toDoubleOrNull() ?: at) < at) Thread.sleep(40)
                }
                pause()
                Thread.sleep(1_500)
                println("[stat] paused=${p("pause")} at pos=${p("time-pos").take(8)} s")
                return true
            }
            // MP-6：系统那头的「正在播放」读回来；像系统那样发暂停 / 播放 / 拖动，看内核跟不跟；离开播放器后应当清空
            val np = System.getProperty("gugaart.probe.nowPlaying") == "true"
            if (np) {
                fun back() = SystemNowPlayingCenter.readBack()?.let { "keys=${it.first} playbackState=${it.second}" } ?: "(no answer)"
                println("[np] playing: ${back()} artworkWidth=${SystemNowPlayingCenter.artworkWidth()}")
                println("[np] commands: ${SystemNowPlayingCenter.commandsEnabled()}")
                println("[np] deliver pause -> status=${SystemNowPlayingCenter.deliver("guguPause:")}")
                Thread.sleep(1_200)
                println("[np] after pause: mpv pause=${p("pause")} ${back()}")
                println("[np] deliver toggle -> status=${SystemNowPlayingCenter.deliver("guguToggle:")}")
                Thread.sleep(1_200)
                println("[np] after toggle: mpv pause=${p("pause")} ${back()}")
                println("[np] deliver play (already playing) -> status=${SystemNowPlayingCenter.deliver("guguPlay:")} mpv pause=${p("pause")}")
            }
            onUi { graph.nav.backOrHome() }
            Thread.sleep(1_000)
            if (np) println("[np] commands after leaving: ${SystemNowPlayingCenter.commandsEnabled()}")
            if (np) println("[np] left the player: ${SystemNowPlayingCenter.readBack()?.let { "keys=${it.first} playbackState=${it.second}" }} artworkWidth=${SystemNowPlayingCenter.artworkWidth()}")
            return false
        } finally {
            if (strmMode != null) graph.settings.patch(section, key to kotlinx.serialization.json.JsonPrimitive(before))
        }
    }

    fun run(graph: AppGraph, window: () -> java.awt.Window?, quit: () -> Unit) {
        dir.mkdirs()
        Thread({
            fun shot(name: String) {
                val w = window() ?: return
                val b = w.bounds
                val file = File(dir, "$name.png")
                runCatching {
                    // 只截我们自己的这扇窗（按窗口号），不按屏幕上的矩形截——那样会把叠在上面的别的应用也截进来
                    val number = (w as? androidx.compose.ui.awt.ComposeWindow)?.windowHandle?.takeIf { it != 0L }?.let { handle ->
                        val objc = com.sun.jna.NativeLibrary.getInstance("objc")
                        val sel = objc.getFunction("sel_registerName").invokePointer(arrayOf<Any>("windowNumber"))
                        objc.getFunction("objc_msgSend").invokeLong(arrayOf<Any?>(com.sun.jna.Pointer(handle), sel))
                    } ?: error("no window number")
                    ProcessBuilder("screencapture", "-x", "-o", "-l$number", file.path).inheritIO().start().waitFor()
                }
                println("[probe] $name -> ${file.name} (${b.width} x ${b.height}, written=${file.isFile})")
            }
            fun onUi(block: () -> Unit) = javax.swing.SwingUtilities.invokeAndWait(block)
            // 播放器的快捷键（进程内发给窗口的焦点组件，不经系统的辅助功能）。**不发 V**：那是收藏，会写服务器。
            fun key(code: Int, ch: Char, shift: Boolean = false) = onUi {
                val w = window() ?: return@onUi
                val target = w.focusOwner ?: w
                val mod = if (shift) java.awt.event.InputEvent.SHIFT_DOWN_MASK else 0
                val t = System.currentTimeMillis()
                target.dispatchEvent(java.awt.event.KeyEvent(target, java.awt.event.KeyEvent.KEY_PRESSED, t, mod, code, ch))
                target.dispatchEvent(java.awt.event.KeyEvent(target, java.awt.event.KeyEvent.KEY_RELEASED, t, mod, code, ch))
            }
            // 物理占用（vmmap 的 Physical footprint；RSS 不反映 IOSurface）
            fun footprint(): String = runCatching {
                val p = ProcessBuilder("vmmap", "--summary", ProcessHandle.current().pid().toString()).redirectErrorStream(true).start()
                val line = p.inputStream.bufferedReader().readLines().firstOrNull { it.startsWith("Physical footprint:") }
                p.waitFor()
                line?.substringAfter(':')?.trim() ?: "?"
            }.getOrDefault("?")
            try {
                val deadline = System.currentTimeMillis() + 20_000
                while (graph.session.session.value == null && System.currentTimeMillis() < deadline) Thread.sleep(200)
                val session = checkNotNull(graph.session.session.value) { "没有已登录的会话（探针不登录）" }
                val facts = graph.deps.player?.platformFacts()
                println("[probe] signed in; engines=${facts?.present?.map { it.id }} kind=${facts?.kind?.id} videoUnderUi=${graph.deps.player?.videoUnderUi}")
                if (search != null) {
                    // 让控件出来：进程内给内容区发一个鼠标移动
                    fun wake() = onUi {
                        val w = window() as? javax.swing.JFrame ?: return@onUi
                        val x = w.contentPane.width / 2
                        val y = w.contentPane.height / 2
                        val c = javax.swing.SwingUtilities.getDeepestComponentAt(w.contentPane, x, y) ?: w.contentPane
                        for (d in 0..2) {
                            val pt = javax.swing.SwingUtilities.convertPoint(w.contentPane, x + d * 6, y, c)
                            c.dispatchEvent(java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, pt.x, pt.y, 0, false, java.awt.event.MouseEvent.NOBUTTON))
                        }
                    }
                    val keep = stats(graph, session.client, ::onUi, { key(java.awt.event.KeyEvent.VK_SPACE, ' ') }, ::shot, ::wake)
                    if (keep) {
                        keepOpen = true
                        println("[stat] left open and paused for a human to look at: pid=${ProcessHandle.current().pid()}")
                    }
                    return@Thread
                }
                // 「续看」：继续观看的第一条，从它记着的位置接着放（给了 --route 就用给的）
                val target: Route = if (route != Route.Home) route else {
                    val item = kotlinx.coroutines.runBlocking { session.client.getResume(limit = 1).items.firstOrNull() }
                    checkNotNull(item) { "「继续观看」是空的：用 --route=/play/<条目 id> 指一条" }
                    println("[probe] resume item found")
                    onUi { graph.nav.navigate(Route.Item(item.id)) }
                    Thread.sleep(3_000)
                    shot("0-detail")
                    Route.Play(item.id, startMs = item.userData?.playbackPositionTicks?.let { it / 10_000 })
                }
                val waitMs = System.getProperty("gugaart.probe.waitMs")?.toLongOrNull() ?: 12_000L
                repeat(rounds) { round ->
                    val tag = if (rounds == 1) "" else "r${round + 1}-"
                    onUi { graph.nav.navigate(target) }
                    Thread.sleep(if (round == 0) waitMs else 6_000)
                    shot("${tag}1-full")
                    if (round == 0) {
                        Thread.sleep(4_000) // 控件自动隐藏之后再来一张：只有画面
                        shot("1b-full-controls-hidden")
                        panel.value = "sub"
                        Thread.sleep(2_500)
                        shot("2-inset-sub")
                        panel.value = "dm"
                        Thread.sleep(2_000)
                        shot("3-inset-dm")
                        panel.value = null
                        Thread.sleep(2_500)
                        shot("4-full-again")
                        // `-Dgugaart.probe.click=x,y`（窗口内容里的点）：进程内点一下（例如第一次播一部剧时「弹幕来源」里挑第一条——
                        // 只记在本机，不写服务器），等弹幕出来再截一张
                        System.getProperty("gugaart.probe.click")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }?.let { (x, y) ->
                            onUi {
                                val w = window() as? javax.swing.JFrame ?: return@onUi
                                val c = javax.swing.SwingUtilities.getDeepestComponentAt(w.contentPane, x, y) ?: w.contentPane
                                val pt = javax.swing.SwingUtilities.convertPoint(w.contentPane, x, y, c)
                                val t = System.currentTimeMillis()
                                for (id in intArrayOf(java.awt.event.MouseEvent.MOUSE_MOVED, java.awt.event.MouseEvent.MOUSE_PRESSED, java.awt.event.MouseEvent.MOUSE_RELEASED, java.awt.event.MouseEvent.MOUSE_CLICKED)) {
                                    val button = if (id == java.awt.event.MouseEvent.MOUSE_MOVED) java.awt.event.MouseEvent.NOBUTTON else java.awt.event.MouseEvent.BUTTON1
                                    val mods = if (id == java.awt.event.MouseEvent.MOUSE_PRESSED) java.awt.event.InputEvent.BUTTON1_DOWN_MASK else 0
                                    c.dispatchEvent(java.awt.event.MouseEvent(c, id, t, mods, pt.x, pt.y, 1, false, button))
                                }
                            }
                            Thread.sleep(3_000)
                            shot("4b-after-click")
                            Thread.sleep(9_000)
                            shot("4c-danmaku")
                            Thread.sleep(1_500)
                            shot("4d-danmaku")
                        }
                        if (extras) {
                            // 弹幕开 / 关各一张（D 是开关，哪张有弹幕看图）
                            key(java.awt.event.KeyEvent.VK_D, 'd'); Thread.sleep(6_000); shot("6-danmaku-toggled")
                            key(java.awt.event.KeyEvent.VK_D, 'd'); Thread.sleep(3_000); shot("6b-danmaku-toggled-back")
                            // 字幕开关、音轨面板、倍速 +2 档、快进、暂停 / 继续
                            key(java.awt.event.KeyEvent.VK_C, 'c'); Thread.sleep(2_500); shot("7-subtitles-toggled")
                            key(java.awt.event.KeyEvent.VK_C, 'c'); Thread.sleep(1_000)
                            panel.value = "audio"; Thread.sleep(2_000); shot("7b-audio-panel"); panel.value = null; Thread.sleep(1_500)
                            key(java.awt.event.KeyEvent.VK_CLOSE_BRACKET, ']'); key(java.awt.event.KeyEvent.VK_CLOSE_BRACKET, ']')
                            panel.value = "speed"; Thread.sleep(2_500); shot("8-rate-up-twice"); panel.value = null
                            key(java.awt.event.KeyEvent.VK_OPEN_BRACKET, '['); key(java.awt.event.KeyEvent.VK_OPEN_BRACKET, '[')
                            key(java.awt.event.KeyEvent.VK_RIGHT, java.awt.event.KeyEvent.CHAR_UNDEFINED); Thread.sleep(2_500); shot("8b-after-seek")
                            key(java.awt.event.KeyEvent.VK_SPACE, ' '); Thread.sleep(1_500); shot("8c-paused")
                            key(java.awt.event.KeyEvent.VK_SPACE, ' '); Thread.sleep(1_000)
                            // 全屏进 / 出
                            key(java.awt.event.KeyEvent.VK_F, 'f'); Thread.sleep(3_500); shot("9-fullscreen")
                            println("[probe] fullscreen window=${window()?.bounds?.let { "${it.x},${it.y} ${it.width}x${it.height}" }}")
                            key(java.awt.event.KeyEvent.VK_F, 'f'); Thread.sleep(3_000); shot("9b-windowed-again")
                            // 下一集（Shift+N）：从头开始，有片头标记的话这里会出「跳过片头」胶囊
                            key(java.awt.event.KeyEvent.VK_N, 'N', shift = true); Thread.sleep(7_000); shot("10-next-episode")
                            Thread.sleep(4_000); shot("10b-next-episode-controls-hidden")
                        }
                    }
                    onUi { graph.nav.backOrHome() }
                    Thread.sleep(2_000)
                    shot("${tag}5-after-back")
                    Thread.sleep(2_500) // 软释放：3 秒后层退场
                    if (rounds > 1) println("[probe] round ${round + 1}: footprint ${footprint()} threads ${Thread.getAllStackTraces().size}")
                }
                println("[probe] done; on the player route=${graph.nav.pathname.startsWith("/play")} threads=${Thread.getAllStackTraces().size}")
            } catch (t: Throwable) {
                println("[probe] failed: ${t::class.simpleName}: ${t.message}")
            } finally {
                if (!keepOpen) quit()
            }
        }, "gugu-mac-probe").start()
    }
}
