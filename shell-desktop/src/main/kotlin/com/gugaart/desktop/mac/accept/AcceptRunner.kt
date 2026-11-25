package com.gugaart.desktop.mac.accept

import androidx.compose.ui.awt.ComposeWindow
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlayController
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/** 运行器要从壳里拿的东西（MacSession 交进来）。 */
internal class AcceptHost(
    val graph: AppGraph,
    val window: () -> ComposeWindow?,
    /** 内核的一个 mpv 属性（文本）；没有内核 / 没有在跑 = null。 */
    val mpv: (String) -> String?,
    /** 找到了 libmpv 没有。 */
    val engineAvailable: Boolean,
    val quit: () -> Unit,
    /** 内核本身（画中画的数、声音输出重开了几次）；没挂 :player-apple 的构建 = null。 */
    val engines: com.gugaart.desktop.mac.MacEngines? = null,
    /** 会话目录（钥匙串的账户名按它分开；只看明文文件在不在，不读内容）。 */
    val dataDir: java.io.File? = null,
)

/** 一个场景：名字、标题、对应对齐清单的行、要跑多久以内。 */
internal class Scenario(val id: String, val title: String, val rows: List<String>, val timeoutSec: Long, val body: (Ctx) -> Unit)

/**
 * 一个场景跑的时候手边的东西：驱动（看 / 动 / 截图）、上游的 AppGraph（跳路由、取只读数据）、播放器页的控制器、记结论。
 * 记结论只经 [check] / [pass] / [fail] / [skip] / [human]；场景里抛出来的异常由运行器记成场景的 error。
 */
internal class Ctx(val host: AcceptHost, val d: AcceptDriver, private val scenario: Scenario, val controllerOf: () -> PlayController?, val settingsStoreOf: () -> com.gugaart.ui.data.GugaSettingsStore? = { null }) {
    val checks = ArrayList<Check>()
    val graph: AppGraph get() = host.graph

    fun check(id: String, title: String, verdict: Verdict, detail: String = "", values: Map<String, Any?> = emptyMap(), shots: List<String?> = emptyList()) {
        val c = Check(id, title, verdict, detail, values.filterValues { it != null }.mapValues { it.value.toString() }, shots.filterNotNull())
        checks += c
        println("[accept] ${scenario.id}/${c.id}: ${c.verdict.word}${if (c.detail.isNotEmpty()) " - " + c.detail.take(200) else ""}")
    }

    fun expect(id: String, title: String, ok: Boolean, detail: String = "", values: Map<String, Any?> = emptyMap(), shots: List<String?> = emptyList()) =
        check(id, title, if (ok) Verdict.Pass else Verdict.Fail, detail, values, shots)

    fun skip(id: String, title: String, why: String, values: Map<String, Any?> = emptyMap(), shots: List<String?> = emptyList()) = check(id, title, Verdict.Skip, why, values, shots)
    fun human(id: String, title: String, what: String, shots: List<String?> = emptyList(), values: Map<String, Any?> = emptyMap()) = check(id, title, Verdict.NeedsHuman, what, values, shots)
    /** 像素读数类检查：拿不到截图且确认缺屏幕录制权限 = env-skip（不算没过）；否则照 [expect]。 */
    fun pixelExpect(id: String, title: String, ok: Boolean, hasPixels: Boolean, detail: String = "", values: Map<String, Any?> = emptyMap(), shots: List<String?> = emptyList()) {
        if (!hasPixels && d.screenRecordingOk == false) check(id, title, Verdict.EnvSkip, "缺屏幕录制权限，截图拿不到；需主理人给 PI-Desktop 授权后复跑", values, shots)
        else expect(id, title, ok, detail, values, shots)
    }

    // ---- 常用动作

    fun client() = graph.session.session.value?.client ?: error("没有已登录的会话")

    fun nav(route: Route, waitMs: Long = 600) {
        d.ui { graph.nav.navigate(route) }
        d.settle(waitMs)
    }

    fun back(waitMs: Long = 600) {
        d.ui { graph.nav.backOrHome() }
        d.settle(waitMs)
    }

    val route: Route get() = d.ui { graph.nav.route }
    val path: String get() = d.ui { graph.nav.pathname }

    fun mpv(name: String): String? = host.mpv(name)

    fun waitTag(tag: String, timeoutMs: Long = 15_000): AcceptDriver.Node? {
        var n: AcceptDriver.Node? = null
        d.waitFor(timeoutMs, 150) { d.tree().tag(tag).also { n = it } != null }
        return n
    }

    /** 回到起点：退出全屏、回到前台、首页的「首页」那一格。 */
    fun reset() {
        if (runCatching { d.isFullScreen() }.getOrDefault(false)) {
            d.toggleFullScreen()
            d.waitFor(6_000) { !d.isFullScreen() }
            Thread.sleep(800)
        }
        d.activate()
        d.ui { graph.nav.navigate(Route.Home) }
        d.settle(400)
        tab("首页")
    }

    /** 点导航胶囊里的一格（真的鼠标点）。返回点到了没有。 */
    fun tab(label: String): Boolean {
        val n = d.tree().nodes.firstOrNull { (it.tag == "desktop-tab-on" || it.tag == "desktop-tab-off") && label in it.desc } ?: return false
        if (n.tag == "desktop-tab-on") return true
        d.click(n)
        return d.waitFor(4_000) { d.tree().nodes.any { it.tag == "desktop-tab-on" && label in it.desc } }
    }

    /** 让播放器的控件出来：在窗口中间动一下鼠标。 */
    fun wakeControls() {
        val (w, h) = d.ui { d.window.width to d.window.height }
        d.move(w / 2.0, h / 2.0)
        d.move(w / 2.0 + 9, h / 2.0 + 3)
    }

    val controller: PlayController? get() = controllerOf()

    /**
     * 开播：等到控制器 Ready、内核的开场 seek 落定（`seeking` = no）之后位置在走（[timeoutMs] 以内）。返回从 [t0] 到这一刻的毫秒数，没等到 = null。
     *
     * 起点要在 seek 落定之后取：续看时开场 seek 走关键帧（`hr-seek=no`），seek 还没落定时 `time-pos` 报的是**要去的**位置（续看点），
     * 落定后跳回它前面那个关键帧（实测《继续观看》那条在续看点前 13 秒）。原来在 seek 落定前就取了起点，等的就成了「从关键帧放回续看点」，
     * 慢线路上要 18 – 23 秒（2026-11-18 的报告），而画面其实 0.6 – 1.2 秒就出来了（控制器自己的「播放启动耗时」）。
     */
    fun awaitFirstFrame(t0: Long, timeoutMs: Long = 40_000): Long? {
        var firstPos: Double? = null
        var at: Long? = null
        d.waitFor(timeoutMs, 100) {
            val c = controller ?: return@waitFor false
            val s = c.snapshot.value
            if (s.phase == com.gugugaga.app.player.PlayPhase.Error) return@waitFor true
            if (s.phase != com.gugugaga.app.player.PlayPhase.Ready || mpv("seeking") != "no") {
                firstPos = null
                return@waitFor false
            }
            val pos = mpv("time-pos")?.toDoubleOrNull() ?: return@waitFor false
            if (firstPos == null) firstPos = pos
            else if (pos > firstPos!! + 0.05) { at = System.currentTimeMillis(); return@waitFor true }
            false
        }
        return at?.let { it - t0 }
    }

    /** 这一次开播控制器自己记的分段（「播放启动耗时」那一行：打开媒体 / 首个字节 / seek 完成 / 首帧；不带地址和令牌）。没有 = null。 */
    fun lastStartupLine(itemId: String?): String? =
        graph.startupLog.reports.value.lastOrNull { itemId == null || it.itemId == itemId }?.line()

    /**
     * 系统全屏进出 [cycles] 次（P20）：[press] 是这一路的「按一下」（播放器里的 F、`WindowControls`、或者系统的 `toggleFullScreen:`）。
     * 每次等进去（`styleMask` 的全屏位）、等出来（位清掉、窗口回到原来的大小）。返回每一次的记录、都进出了没有、最后的可见窗口。
     */
    fun fullscreenCycles(cycles: Int, press: () -> Unit): FullscreenCycles {
        val before = d.ui { d.window.bounds }
        val rows = ArrayList<String>()
        var ok = true
        for (i in 1..cycles) {
            val wc = graph.deps.windowControls
            val pre = "前：key=${runCatching { Objc.onMain { Objc.msgBool(d.nsWindowPointer(), "isKeyWindow") } }.getOrNull()} 焦点=${d.ui { d.window.isFocused }} 界面记的全屏=${d.ui { wc?.fullscreen }}"
            press()
            val inn = d.waitFor(8_000) { d.isFullScreen() }
            if (!inn) {
                // 没进去就别再按（再按一次会变成「又要进去」）
                rows += "#$i 没进去（$pre）"
                ok = false
                break
            }
            Thread.sleep(1_500)
            press()
            val out = d.waitFor(8_000) { !d.isFullScreen() }
            Thread.sleep(1_500)
            val b = d.ui { d.window.bounds }
            val back = kotlin.math.abs(b.width - before.width) <= 2 && kotlin.math.abs(b.height - before.height) <= 2
            if (!inn || !out || !back) ok = false
            rows += "#$i 进=$inn 出=$out 回原大小=$back（$pre）"
            if (!ok) break
        }
        val leftover = fullscreenLeftovers(d.visibleNsWindows())
        return FullscreenCycles(rows, ok && leftover.isEmpty() && !d.isFullScreen(), d.visibleNsWindows())
    }

    class FullscreenCycles(val rows: List<String>, val ok: Boolean, val windows: List<String>)
}

/**
 * 进程内的验收运行器（开发运行：`--accept=<场景|all> --accept-out=<目录>`；`scripts/accept-mac.mjs` 一行起）。
 * 在自己的线程上一个场景一个场景地跑，每个场景有超时；跑完写 `report.json`、`report.md` 和截图联系表，然后退出应用。
 * 退出码由脚本从 `report.json` 读（`exitCode`）。对用户的服务器只读：不收藏、不标已看、不删、不改服务器设置；要写的检查记 skip。
 */
internal class AcceptRunner(
    private val names: List<String>,
    private val out: File,
    /** 把结果并进输出目录里已有的 `report.json`（第二次起应用跑「找不到 libmpv」那一场）。 */
    private val merge: Boolean,
) {
    @Volatile private var controller: PlayController? = null

    /** 播放器页建好控制器就交过来（MacSession 的 `LocalPlaySessionSink`）。 */
    fun onController(c: PlayController) {
        controller = c
    }

    /** 根界面的那一个画报设置 store（:ui 的验收缝 LocalGugaSettingsSink）：自己 new 一个只写盘，界面那份 StateFlow 收不到。 */
    @Volatile private var settingsStore: com.gugaart.ui.data.GugaSettingsStore? = null

    fun onSettingsStore(s: com.gugaart.ui.data.GugaSettingsStore) {
        settingsStore = s
    }

    fun start(host: AcceptHost) {
        Thread({ runAll(host) }, "gugu-accept").apply { isDaemon = false }.start()
    }

    private fun stamp(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).substringBefore('.')

    private fun runAll(host: AcceptHost) {
        out.mkdirs()
        val started = stamp()
        val d = AcceptDriver(host.window, out)
        val results = ArrayList<ScenarioResult>()
        val all = AcceptScenarios.all(host)
        try {
            // 窗口出来、会话恢复（运行器不登录、不输入任何账号密码；没有会话就停）
            d.waitFor(20_000) { host.window() != null }
            Thread.sleep(1_500)
            val signedIn = d.waitFor(30_000, 200) { host.graph.session.session.value != null }
            if (!signedIn) {
                results += ScenarioResult("session", "已登录的会话", listOf("C03"), emptyList(), error = "没有已登录的会话（--data-dir 指的目录里没有会话，或读不了）；运行器不登录，停在这里")
            } else {
                for (name in names) {
                    val sc = all.firstOrNull { it.id == name }
                    if (sc == null) {
                        results += ScenarioResult(name, "（不认识的场景）", emptyList(), emptyList(), error = "没有这个场景：$name；有 ${all.joinToString(" ") { it.id }}")
                        continue
                    }
                    results += runOne(sc, Ctx(host, d, sc, { controller }, { settingsStore }), d)
                    writeReport(started, results, d) // 每个场景跑完都落一次盘：中途崩了也有报告
                }
            }
        } catch (t: Throwable) {
            results += ScenarioResult("runner", "运行器", emptyList(), emptyList(), error = "${t::class.simpleName}: ${t.message}")
        } finally {
            runCatching { writeReport(started, results, d) }.onFailure { println("[accept] report failed: ${it.message}") }
            println("[accept] done -> ${File(out, "report.md").path}")
            host.quit()
        }
    }

    private fun runOne(sc: Scenario, ctx: Ctx, d: AcceptDriver): ScenarioResult {
        println("[accept] ==== ${sc.id} ${sc.title}")
        val t0 = System.currentTimeMillis()
        var error: String? = null
        val th = Thread({
            try {
                ctx.reset()
                sc.body(ctx)
            } catch (t: Throwable) {
                error = "${t::class.simpleName}: ${t.message}"
                t.printStackTrace()
            }
        }, "gugu-accept-${sc.id}").apply { isDaemon = true }
        th.start()
        th.join(sc.timeoutSec * 1000)
        if (th.isAlive) {
            error = "超时（${sc.timeoutSec} s）：停在「${ctx.checks.lastOrNull()?.title ?: "开头"}」之后"
            th.interrupt()
        }
        // 下一个场景从干净的地方开始；离开播放器
        runCatching { if (ctx.path.startsWith("/play")) ctx.back(1_500) }
        return ScenarioResult(sc.id, sc.title, sc.rows, ArrayList(ctx.checks), error, System.currentTimeMillis() - t0)
    }

    private fun machine(): Map<String, String> = mapOf(
        "系统" to "macOS ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
        "JVM" to System.getProperty("java.version").orEmpty(),
        "libmpv" to (System.getProperty("gugu.apple.libmpv") ?: "默认查找（MPVKit 开发库 → Homebrew）"),
        "场景" to names.joinToString(" "),
    )

    private fun writeReport(started: String, results: List<ScenarioResult>, d: AcceptDriver) {
        var report = AcceptReport(started, stamp(), machine(), results)
        val json = File(out, "report.json")
        if (merge && json.isFile) {
            runCatching { AcceptReport.fromJson(json.readText()) }.getOrNull()?.let { old ->
                // 第一次运行的截图也要进联系表：它们的文件名在旧报告里
                report = old.merge(report).copy(startedAt = old.startedAt, machine = old.machine + mapOf("场景" to (old.machine["场景"].orEmpty() + " " + names.joinToString(" ")).trim()))
            }
        }
        val shots = report.scenarios.flatMap { s -> s.checks.flatMap { c -> c.shots.map { it to "${s.id} · ${c.title}" } } }.distinctBy { it.first }
        val sheets = contactSheets(shots)
        json.writeText(report.toJsonText())
        File(out, "report.md").writeText(report.toMarkdown(sheets, HUMAN_ONLY))
    }

    /** 截图联系表：每张缩略图下面写场景和检查名；一页 24 张。 */
    private fun contactSheets(shots: List<Pair<String, String>>): List<String> {
        val layout = SheetLayout()
        val pages = sheetPages(shots.size, layout)
        return pages.mapIndexedNotNull { i, range ->
            val items = shots.subList(range.first, range.last + 1)
            val thumbs = items.map { (rel, label) -> runCatching { ImageIO.read(File(out, rel)) }.getOrNull() to "${rel.substringAfterLast('/')} · $label" }
            val thumbH = (layout.thumbW * 0.66).toInt()
            val rows = (items.size + layout.perRow - 1) / layout.perRow
            val w = layout.perRow * (layout.thumbW + layout.gap) + layout.gap
            val h = rows * (thumbH + layout.labelH + layout.gap) + layout.gap
            val sheet = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = sheet.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = Color(0x1a, 0x1a, 0x1f)
            g.fillRect(0, 0, w, h)
            g.font = Font("PingFang SC", Font.PLAIN, 12)
            thumbs.forEachIndexed { k, (img, label) ->
                val x = layout.gap + (k % layout.perRow) * (layout.thumbW + layout.gap)
                val y = layout.gap + (k / layout.perRow) * (thumbH + layout.labelH + layout.gap)
                if (img != null) {
                    val s = minOf(layout.thumbW.toDouble() / img.width, thumbH.toDouble() / img.height)
                    val tw = (img.width * s).toInt()
                    val th = (img.height * s).toInt()
                    g.drawImage(img, x + (layout.thumbW - tw) / 2, y + (thumbH - th) / 2, tw, th, null)
                } else {
                    g.color = Color(0x55, 0x22, 0x22); g.fillRect(x, y, layout.thumbW, thumbH)
                }
                g.color = Color(0xdd, 0xd8, 0xcc)
                val lines = label.chunked(46).take(2)
                lines.forEachIndexed { j, line -> g.drawString(line, x, y + thumbH + 14 + j * 15) }
            }
            g.dispose()
            val name = "contact-${i + 1}.png"
            if (ImageIO.write(sheet, "png", File(out, name))) name else null
        }
    }

    companion object {
        /** 自动化看不了的（人眼 / 人耳 / 系统界面 / 要动手的硬件），每次都列在报告里。 */
        val HUMAN_ONLY = listOf(
            "画面观感：清晰度、色彩、字幕排版（P01 / P06）——看 play 场景的截图，再在屏幕上看一眼",
            "HDR 高光比旁边的白色控件亮、高光有层次、暗部不发灰、不偏色（P12 / ADR 0004 §2.7.5 V6）——截图存不下 EDR，只能看屏幕",
            "杜比视界 P8.1（《海王2》）颜色正常、不偏紫偏绿；P5 没有片源（V6）",
            "画中画小窗（P18）：小窗里有没有画面、声音断不断、关小窗是不是暂停、HDR 片偏灰能不能接受（pip 场景量了按钮 / 进出 / 帧间隔 / CPU）",
            "「正在播放」的系统卡片长什么样（控制中心 / 菜单栏）、键盘的播放键 F8、AirPods 捏一下（X11）——系统界面不截、硬件键不模拟",
            "中文输入法：候选窗跟着光标、回车上屏后才搜（S05 / P19 的输入法那一半）——运行器只模拟了上屏那一步",
            "触控板：双指横滑翻封面、横向海报行双指滚（B01 / B23）",
            "「本地网络」权限弹窗和投屏（P14）",
        )
    }
}
