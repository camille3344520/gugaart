package com.gugaart.desktop.mac

import com.gugaart.desktop.mac.accept.AcceptDriver
import com.gugaart.desktop.mac.accept.AcceptHost
import com.gugaart.desktop.mac.accept.Ctx
import com.gugaart.desktop.mac.accept.Scenario
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlayController
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/*
 * 苹果端性能实测（1.5.1 perf/apple）。只量数，不改界面。
 *
 *   --perf=<输出目录> [--perf-steps=cold,idle,play,switch]
 *
 * 开发运行（-Dgugaart.dev=true）直接认；打好的包要环境变量 GUGAART_PERF=1 才认（和 --accept 一样是诊断缝，
 * 不点它什么都不做）。量完写 <目录>/perf.json，然后正常退出。
 *
 * 起点一律是**进程启动那一刻**（ProcessHandle 的 startInstant），不是 main()：这样「打开应用 → 首页出内容」
 * 把 JVM / 运行时启动也算进去，dmg 版和 :shell-desktop:run 量的是同一个口径。
 */

/** 启动分段的路标（毫秒，相对进程启动）。只记时间，不记任何用户数据。 */
internal object PerfMarks {
    val enabled: Boolean = System.getProperty("gugaart.perf") == "true" || System.getenv("GUGAART_PERF") == "1"
    private val started: Instant? = runCatching { ProcessHandle.current().info().startInstant().orElse(null) }.getOrNull()
    private val marks = ConcurrentHashMap<String, Long>()

    /** 进程启动到现在多少毫秒；拿不到进程启动时间 = -1。 */
    fun sinceStartMs(): Long = started?.let { Duration.between(it, Instant.now()).toMillis() } ?: -1L

    /** 记一个路标（同一个名字只记第一次）。 */
    fun mark(name: String) {
        if (enabled) marks.putIfAbsent(name, sinceStartMs())
    }

    fun all(): Map<String, Long> = marks.toSortedMap()

    /** 进程启动那一刻的墙上时钟（毫秒）；脚本用它把自己 spawn 的时刻对齐到同一口径。 */
    fun startEpochMs(): Long = started?.toEpochMilli() ?: -1L
}

/**
 * 性能探针：在自己的线程上跑，用验收运行器那双手（[AcceptDriver]：语义树 / 投递事件 / phys_footprint）。
 * 空闲采样期间**不碰语义树**（读树本身要占 CPU，会把空闲读数抬上去）。
 */
internal class MacPerfProbe(
    private val out: File,
    private val steps: List<String>,
    private val host: AcceptHost,
    private val controllerOf: () -> PlayController?,
    private val settingsOf: () -> com.gugaart.ui.data.GugaSettingsStore? = { null },
) {
    private val d = AcceptDriver(host.window, out)
    private val scenario = Scenario("perf", "性能实测", emptyList(), 3_600) {}
    private val c = Ctx(host, d, scenario, controllerOf)
    private val pid = ProcessHandle.current().pid()

    fun start() {
        Thread({ run() }, "gugu-perf").apply { isDaemon = false }.start()
    }

    private fun run() {
        out.mkdirs()
        val res = LinkedHashMap<String, Any?>()
        res["pid"] = pid
        res["processStartEpochMs"] = PerfMarks.startEpochMs()
        res["steps"] = steps
        res["mode"] = if (System.getProperty("gugaart.dev") == "true") "run" else "package"
        try {
            // ---- 冷启动：窗口 → 会话 → 首页出内容（20 ms 一轮，只在冷启动这一段读树）
            val windowOk = d.waitFor(120_000, 20) { host.window() != null }
            PerfMarks.mark("window")
            val sessionOk = d.waitFor(120_000, 20) { host.graph.session.session.value != null }
            PerfMarks.mark("session")
            val homeOk = d.waitFor(120_000, 20) { homeReady() }
            PerfMarks.mark("home")
            res["marks"] = PerfMarks.all()
            res["cold"] = mapOf(
                "windowOk" to windowOk, "sessionOk" to sessionOk, "homeOk" to homeOk,
                "toWindowMs" to PerfMarks.all()["window"], "toSessionMs" to PerfMarks.all()["session"],
                "toHomeMs" to PerfMarks.all()["home"], "mainMs" to PerfMarks.all()["main"],
            )
            write(res)
            if (!homeOk) {
                res["error"] = "首页 120 秒没出内容"
                write(res)
                return
            }
            // 首页稳住（图片解码、自动翻页的计时器都起来）再开始量空闲
            Thread.sleep(5_000)

            if ("idle" in steps) {
                res["idleHome"] = idle("idle-home", 5 * 60_000L)
                write(res)
            }
            if ("play" in steps) {
                res["play"] = playRounds(3)
                write(res)
            }
            if ("idle" in steps) {
                // 暂停播放 5 分钟：先进播放器、等首帧、再暂停
                val ff = playOnce()
                val paused = c.controller?.let { it.pause(); d.waitFor(5_000) { it.snapshot.value.paused } } == true
                res["pauseFirstFrameMs"] = ff
                res["paused"] = paused
                Thread.sleep(3_000)
                res["idlePaused"] = idle("idle-paused", 5 * 60_000L)
                write(res)
                c.back(1_500)
            }
            if ("switch" in steps) {
                res["switch"] = switchAwayBack()
                write(res)
            }
            if ("drag" in steps) {
                res["drag"] = dragWindow()
                write(res)
            }
            if ("idleprobe" in steps) {
                res["idleProbe"] = idleProbe()
                write(res)
            }
            if ("idlebg" in steps) {
                res["idleBg"] = idleBackground()
                write(res)
            }
        } catch (t: Throwable) {
            res["error"] = "${t::class.simpleName}: ${t.message}"
            t.printStackTrace()
        } finally {
            runCatching { write(res) }
            println("[perf] done -> ${File(out, "perf.json").path}")
            host.quit()
        }
    }

    private fun homeReady(): Boolean {
        val t = d.tree()
        return t.has("desktop-home") && (t.has("fold-title") || t.has("desktop-poster-row"))
    }

    /**
     * 拖窗口：3 秒里挪 150 次（≈50 次/秒，和真拖一个量级），量这段的 CPU。
     * 窗口每挪一次 MacSession 的 componentMoved 就问一次屏幕 HDR（dispatch_sync 到主线程），
     * 播放中 MacLayerHost 自己的 moved 监听还会再问一次 —— 这段读数就是那两份重复工作的代价。
     */
    private fun dragWindow(): Map<String, Any?> {
        c.reset()
        Thread.sleep(1_000)
        val b = d.ui { java.awt.Rectangle(d.window.bounds) }
        val cpu0 = cpuTimeMs()
        val t0 = System.currentTimeMillis()
        repeat(150) { i ->
            val x = b.x + (if (i % 2 == 0) 30 else 0)
            d.ui { d.window.setLocation(x, b.y) }
            Thread.sleep(20)
        }
        val wall = System.currentTimeMillis() - t0
        val cpu = cpuTimeMs() - cpu0
        d.ui { d.window.setLocation(b.x, b.y) }
        Thread.sleep(500)
        return mapOf(
            "moves" to 150, "wallMs" to wall, "cpuMs" to cpu,
            "cpuPct" to if (wall > 0) "%.2f".format(cpu * 100.0 / wall) else null,
        )
    }

    /**
     * 首页空闲 CPU 的归因：把设置里本来就有的「封面自动翻页」关掉再量 60 秒（不改代码、不改样子，
     * 只是用用户自己的开关做 A/B）。关了就掉下去 = 封面翻页带着玻璃重拍在烧 CPU。量完恢复原值。
     */
    private fun idleProbe(): Map<String, Any?> {
        val store = settingsOf() ?: return mapOf("skip" to "拿不到画报设置 store")
        c.reset()
        Thread.sleep(3_000)
        val before = store.prefs.value.coverAutoFlip
        store.update { it.copy(coverAutoFlip = false) }
        Thread.sleep(3_000)
        val off = idle("probe-noflip", 60_000)
        store.update { it.copy(coverAutoFlip = before) }
        Thread.sleep(1_000)
        return mapOf("coverAutoFlipWas" to before, "noFlip" to off)
    }

    /**
     * 前台 vs 后台的空闲对照（各 2 分钟）：perf/ui 那一批按窗口焦点 gate 动画，
     * 所以「人看着」和「窗口在后面」要分开量。失焦走 AcceptDriver.deactivate()（NSApp deactivate，不行就 open -a Finder）。
     */
    private fun idleBackground(): Map<String, Any?> {
        c.reset()
        Thread.sleep(3_000)
        val fg = idle("bg-foreground", 120_000)
        val how = d.deactivate()
        Thread.sleep(2_000)
        val bg = idle("bg-background", 120_000)
        d.activate()
        return mapOf("deactivate" to how, "foreground" to fg, "background" to bg)
    }

    // ---- 空闲：累计 CPU 时间差 ÷ 墙钟 = 平均 CPU%（比 top 的一次瞬时读数稳）
    private fun idle(label: String, ms: Long): Map<String, Any?> {
        val wall0 = System.currentTimeMillis()
        val cpu0 = cpuTimeMs()
        val fp0 = d.physFootprint()
        val rss0 = rssKb()
        // 中途抓一份线程栈（3 秒），用来看是哪条线程在动；sample 只读自己的进程
        val sampleAt = ms / 2
        Thread.sleep(sampleAt)
        val sampleFile = File(out, "${label}-sample.txt")
        runCatching {
            ProcessBuilder("sample", pid.toString(), "3", "-file", sampleFile.path)
                .redirectErrorStream(true).start().let { it.inputStream.readAllBytes(); it.waitFor() }
        }
        Thread.sleep(ms - sampleAt)
        val wall = System.currentTimeMillis() - wall0
        val cpu = cpuTimeMs() - cpu0
        return mapOf(
            "wallMs" to wall, "cpuMs" to cpu,
            "cpuPct" to if (wall > 0) "%.2f".format(cpu * 100.0 / wall) else null,
            "footprintStartMb" to mb(fp0), "footprintEndMb" to mb(d.physFootprint()),
            "rssStartMb" to mb(rss0 * 1024), "rssEndMb" to mb(rssKb() * 1024),
            "sample" to sampleFile.name,
        )
    }

    private fun mb(bytes: Long): String? = if (bytes <= 0) null else "%.1f".format(bytes / 1024.0 / 1024.0)

    /** 本进程累计 CPU 时间（毫秒）：ps 的 time 字段（[[DD:]HH:]MM:SS.ss）。 */
    private fun cpuTimeMs(): Long = runCatching {
        val s = ProcessBuilder("ps", "-o", "time=", "-p", pid.toString()).redirectErrorStream(true).start()
            .let { it.inputStream.bufferedReader().readText().trim().also { _ -> it.waitFor() } }
        parseClock(s)
    }.getOrDefault(-1L)

    private fun rssKb(): Long = runCatching {
        ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString()).redirectErrorStream(true).start()
            .let { it.inputStream.bufferedReader().readText().trim().also { _ -> it.waitFor() } }.toLong()
    }.getOrDefault(-1L)

    // ---- 开播：点播放 → 首帧（和验收的 play 同一个口径：开场 seek 落定后位置在走）
    private fun playRounds(n: Int): List<Map<String, Any?>> = (1..n).map {
        c.reset()
        Thread.sleep(1_200)
        val r = playOnce()
        c.back(1_500)
        Thread.sleep(1_200)
        mapOf("round" to it, "firstFrameMs" to r)
    }

    private fun playOnce(): Long? {
        val t0 = System.currentTimeMillis()
        d.ui { host.graph.nav.navigate(Route.Play(PLAY_ITEM)) }
        return c.awaitFirstFrame(t0, 40_000)
    }

    // ---- 切服务器：切走 / 切回各计时（切回要看首页第一帧有没有内容、有没有先出骨架）
    private fun switchAwayBack(): Map<String, Any?> {
        val sw = com.gugaart.ui.servers.sharedServerSwitcher(host.graph)
        val es = sw.entries.value
        if (es.size < 2) return mapOf("skip" to "要 --mock-servers 预存 ≥2 台 Mock（现在 ${es.size} 台）")
        fun port(): String? = host.graph.session.session.value?.serverUrl?.substringAfterLast(':')?.takeWhile { it.isDigit() }
        val first = port() ?: return mapOf("skip" to "读不到当前服务器端口")
        val e2 = es.firstOrNull { !it.isCurrent } ?: return mapOf("skip" to "没有别台")
        val second = e2.address.substringAfterLast(':').takeWhile { it.isDigit() }
        c.reset()
        d.waitFor(15_000) { homeReady() }
        Thread.sleep(1_000)
        val tAway = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking { sw.switchTo(e2.serverId, e2.userId) }
        val awayOk = d.waitFor(20_000, 20) { port() == second }
        val awayHome = d.waitFor(20_000, 20) { homeReady() }
        val awayMs = System.currentTimeMillis() - tAway
        Thread.sleep(1_500)
        val e1 = sw.entries.value.firstOrNull { ":$first" in it.address } ?: return mapOf("awayMs" to awayMs, "skip" to "切回找不到第一台")
        val tBack = System.currentTimeMillis()
        kotlinx.coroutines.runBlocking { sw.switchTo(e1.serverId, e1.userId) }
        var skeleton = false
        val backOk = d.waitFor(20_000, 20) {
            if (d.tree().has("desktop-home-loading")) skeleton = true
            port() == first && homeReady()
        }
        val backMs = System.currentTimeMillis() - tBack
        return mapOf(
            "awayMs" to awayMs, "awayOk" to awayOk, "awayHomeOk" to awayHome,
            "backMs" to backMs, "backOk" to backOk, "backSkeleton" to skeleton,
        )
    }

    private fun write(res: Map<String, Any?>) {
        File(out, "perf.json").writeText(json(res) + "\n")
    }

    companion object {
        /** Mock 的 1080p SDR 样片（银翼杀手2049 的另一份文件）：不带 HDR，首帧读数稳定。 */
        const val PLAY_ITEM = "990202"

        /** ps 的累计 CPU 时间 → 毫秒。 */
        fun parseClock(s: String): Long {
            val parts = s.trim().split(":")
            if (parts.isEmpty() || parts.size > 4 || parts.any { it.toDoubleOrNull() == null }) return -1
            // ps 的 time 是 [[DD:]HH:]MM:SS.ss：最后两段是分、秒，再往前是时、天（天是 24 小时，不是 60）
            val mult = longArrayOf(1L, 60L, 3600L, 86400L)
            var sec = 0.0
            for ((i, p) in parts.reversed().withIndex()) sec += p.toDouble() * mult[i]
            return (sec * 1000).toLong()
        }

        /** 只给探针用的小 JSON 写出（不引序列化库；值都是数字 / 布尔 / 字符串 / 嵌套 map / list）。 */
        fun json(v: Any?): String = when (v) {
            null -> "null"
            is Boolean, is Int, is Long, is Double -> v.toString()
            is Map<*, *> -> v.entries.joinToString(",", "{", "}") { (k, x) -> "\"$k\":${json(x)}" }
            is List<*> -> v.joinToString(",", "[", "]") { json(it) }
            else -> "\"" + v.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        }
    }
}