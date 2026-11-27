package com.gugaart.desktop.mac.accept

// TEMPORARY measurement for fix/danmaku-jitter (not committed): pause / buffering stall on the real Mac window with a fixture
// danmaku server, screenshot bursts + a shadow DanmakuController fed by the REAL presentation clock of the player page.

import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlayerClock
import com.gugugaga.app.player.PlayerTime
import com.gugugaga.danmaku.DanmakuItem
import com.gugugaga.danmaku.DanmakuMode
import com.gugugaga.danmaku.render.ActiveComment
import com.gugugaga.danmaku.render.BackendKind
import com.gugugaga.danmaku.render.DanmakuController
import com.gugugaga.danmaku.render.Geometry
import com.gugugaga.danmaku.render.PlayerClockSample
import com.gugugaga.danmaku.render.RenderBackend
import com.gugugaga.danmaku.render.RendererOptions
import com.gugugaga.danmaku.render.VisualParams
import com.gugugaga.danmaku.settings.DanmakuSettings
import java.awt.event.KeyEvent
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.math.abs

internal object DmJitterProbe {
    private class Rec : RenderBackend {
        override val kind = BackendKind.Direct
        @Volatile var xs: Map<String, Double> = emptyMap()
        var clears = 0
        override fun spawn(c: ActiveComment, tD: Double) {}
        override fun release(c: ActiveComment) {}
        override fun clear() { clears++ }
        override fun resize(geo: Geometry, dpr: Double) {}
        override fun setVisual(visual: VisualParams) {}
        override fun setPlayback(running: Boolean, rate: Double, tD: Double) {}
        override fun frame(tD: Double, now: Double, running: Boolean, actives: List<ActiveComment>) {
            xs = HashMap<String, Double>().also { m -> for (c in actives) if (!c.queued) m[c.item.id] = c.xAt(tD) }
        }
        override fun trim() {}
        override fun destroy() {}
    }

    class Frame(val t: Double, val paused: Boolean, val buffering: Boolean, val pos: Double, val xs: Map<String, Double>, val clears: Int)

    /** The shadow renderer + the clock trace, on its own thread at ~60 Hz. */
    private class Shadow(val clock: () -> PlayerClock?) : Thread("dm-shadow") {
        @Volatile var stop = false
        val frames = java.util.concurrent.CopyOnWriteArrayList<Frame>()
        val trace = java.util.concurrent.CopyOnWriteArrayList<String>()
        override fun run() {
            val rec = ArrayList<Rec>()
            val ctl = DanmakuController(RendererOptions(settings = DanmakuSettings(area = 1.0)), now = { PlayerTime.now() }, backendFactory = { Rec().also(rec::add) })
            ctl.load(List(160) { i -> DanmakuItem("c$i", (400 + i * 370L), DanmakuMode.Scroll, 0xffffff, "弹幕测试 ${i.toString().padStart(3, '0')}") })
            var frameMs = PlayerTime.now()
            ctl.setClockSource {
                val c = clock() ?: PlayerClock.empty(frameMs)
                PlayerClockSample(c.positionMs, c.rate, c.paused, c.buffering, sampledAt = frameMs - (PlayerTime.now() - c.sampledAt), durationMs = c.durationMs)
            }
            ctl.mount(1440.0, 810.0)
            var last: PlayerClock? = null
            var next = PlayerTime.now()
            while (!stop) {
                val now = PlayerTime.now()
                val c = clock()
                if (c != null && c != last) {
                    last = c
                    trace += "%.1f\t%.1f\t%.3f\t%s\t%s\t%.1f".format(java.util.Locale.ROOT, now, c.positionMs, c.rate, c.paused, c.buffering, c.sampledAt)
                }
                if (now >= next) {
                    next += 1000.0 / 60
                    if (next < now) next = now + 1000.0 / 60
                    frameMs = now
                    ctl.tick(now)
                    frames += Frame(now, c?.paused == true, c?.buffering == true, c?.positionMs ?: 0.0, rec.last().xs, rec.last().clears)
                }
                Thread.sleep(1)
            }
            ctl.destroy()
        }
    }

    private fun amplitude(fs: List<Frame>): Double {
        val lo = HashMap<String, Double>()
        val hi = HashMap<String, Double>()
        for (f in fs) for ((id, x) in f.xs) { lo[id] = minOf(lo[id] ?: x, x); hi[id] = maxOf(hi[id] ?: x, x) }
        return lo.keys.maxOfOrNull { hi[it]!! - lo[it]!! } ?: 0.0
    }

    private fun backSteps(fs: List<Frame>): Pair<Int, Double> {
        var n = 0
        var worst = 0.0
        for ((a, b) in fs.zipWithNext()) {
            if (b.clears != a.clears) continue
            val d = b.xs.maxOfOrNull { (id, x) -> a.xs[id]?.let { x - it } ?: 0.0 } ?: 0.0
            if (d > 1e-6) { n++; worst = maxOf(worst, d) }
        }
        return n to worst
    }

    private fun mockPatch(base: String, json: String) {
        val c = URI("$base/__mock/state").toURL().openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        c.outputStream.use { it.write(json.toByteArray()) }
        c.inputStream.use { it.readAllBytes() }
    }

    /** Mean |luma difference| of two shots inside the danmaku band (y 12..45 % of the window, the whole width). */
    private fun bandDiff(c: Ctx, a: String?, b: String?): Double? {
        val ia = a?.let(c.d::image) ?: return null
        val ib = b?.let(c.d::image) ?: return null
        if (ia.width != ib.width || ia.height != ib.height) return null
        val y0 = (ia.height * 0.12).toInt()
        val y1 = (ia.height * 0.45).toInt()
        var sum = 0.0
        var n = 0
        for (y in y0 until y1 step 2) for (x in 0 until ia.width step 2) {
            sum += abs(Pixels.luma(ia.getRGB(x, y)) - Pixels.luma(ib.getRGB(x, y)))
            n++
        }
        return sum / n
    }

    fun run(c: Ctx) {
        val d = c.d
        val mock = System.getProperty("gugaart.mockAccount") ?: error("needs --mock")
        val item = runCatching { kotlinx.coroutines.runBlocking { c.client().getResume(limit = 1).items.firstOrNull() } }.getOrNull()
            ?: run { c.check("dm-item", "an item", Verdict.Fail, "no resume item"); return }
        val shadow = Shadow { c.controller?.presentationClock?.value }
        shadow.isDaemon = true
        shadow.start()
        val marks = LinkedHashMap<String, Double>()
        fun mark(k: String) { marks[k] = PlayerTime.now() }
        val bursts = ArrayList<Triple<String, Double, String?>>()
        fun burst(tag: String, n: Int) = repeat(n) { val t = PlayerTime.now(); bursts += Triple(tag, t, d.shot("$tag-${it.toString().padStart(2, '0')}")) }
        try {
            // ---- 1: unthrottled - playing, then a 6 s pause
            c.nav(Route.Item(item.seriesId ?: item.id), 2_000)
            val t0 = System.currentTimeMillis()
            c.nav(Route.Play(item.id, startMs = 1_000), 200)
            val ff = c.awaitFirstFrame(t0)
            c.check("dm-first-frame", "first frame", if (ff != null) Verdict.Pass else Verdict.Fail, values = mapOf("ms" to ff))
            if (ff == null) return
            Thread.sleep(7_000) // the comments load + the controls hide
            mark("play-burst")
            burst("playing", 6)
            d.key(KeyEvent.VK_SPACE, ' ')
            mark("pause")
            d.waitFor(3_000) { c.controller?.clock?.value?.paused == true }
            Thread.sleep(1_200)
            mark("pause-burst")
            burst("paused", 14)
            mark("pause-burst-end")
            Thread.sleep(800)
            d.key(KeyEvent.VK_SPACE, ' ')
            mark("resume")
            Thread.sleep(4_000)
            mark("resumed-4s")
            c.back(1_500)
            Thread.sleep(1_000)

            // ---- 2: throttled below the bitrate (1.9 Mbit/s sample, 150 KiB/s) - mpv stops for its cache again and again
            mockPatch(mock, """{"cdn":{"limitKBps":150},"server":{"localLimitKBps":150}}""")
            mark("throttle")
            val t1 = System.currentTimeMillis()
            c.nav(Route.Play(item.id, startMs = 1_000), 200)
            c.awaitFirstFrame(t1, 60_000)
            mark("throttled-play")
            val until = PlayerTime.now() + 22_000
            var stalls = 0
            while (PlayerTime.now() < until) {
                // a burst whenever the player says it is buffering
                if (c.controller?.presentationClock?.value?.buffering == true) {
                    stalls++
                    mark("stall-$stalls")
                    Thread.sleep(250)
                    burst("stall$stalls", 5)
                    d.waitFor(8_000, 50) { c.controller?.presentationClock?.value?.buffering != true }
                    mark("stall-$stalls-end")
                } else Thread.sleep(20)
            }
            mark("throttled-end")
            mockPatch(mock, """{"cdn":{"limitKBps":4096},"server":{"localLimitKBps":0}}""")
            c.back(1_500)
        } finally {
            shadow.stop = true
            shadow.join(2_000)
        }

        // ---- analysis
        val fs = shadow.frames.toList()
        fun window(a: Double, b: Double) = fs.filter { it.t >= a && it.t <= b }
        val pausedFrames = window((marks["pause"] ?: 0.0) + 300, (marks["resume"] ?: 0.0) - 20)
        val bufFrames = ArrayList<List<Frame>>()
        run {
            var cur = ArrayList<Frame>()
            var since = Double.NaN
            for (f in fs) {
                if (f.buffering) {
                    if (since.isNaN()) since = f.t
                    if (f.t - since >= 100) cur.add(f)
                } else {
                    if (cur.size > 3) bufFrames.add(cur)
                    cur = ArrayList()
                    since = Double.NaN
                }
            }
            if (cur.size > 3) bufFrames.add(cur)
        }
        val (backN, backWorst) = backSteps(fs)
        val shotDiff = { tag: String -> bursts.filter { it.first == tag }.zipWithNext().mapNotNull { (a, b) -> bandDiff(c, a.third, b.third) } }
        val pausedDiffs = shotDiff("paused")
        val playingDiffs = shotDiff("playing")
        val stallDiffs = (1..20).flatMap { shotDiff("stall$it") }
        val values = linkedMapOf<String, Any?>(
            "shadow frames" to fs.size,
            "pause: shadow amplitude px" to "%.2f".format(amplitude(pausedFrames)),
            "pause: shadow frames" to pausedFrames.size,
            "buffering windows" to bufFrames.size,
            "buffering: shadow amplitude px (each)" to bufFrames.joinToString(" ") { "%.2f".format(amplitude(it)) },
            "shadow back steps (count / worst px)" to "$backN / ${"%.2f".format(backWorst)}",
            "shadow rebuilds" to ((fs.lastOrNull()?.clears ?: 1) - 1),
            "shots playing band diff" to playingDiffs.joinToString(" ") { "%.2f".format(it) },
            "shots paused band diff" to pausedDiffs.joinToString(" ") { "%.2f".format(it) },
            "shots stall band diff" to stallDiffs.joinToString(" ") { "%.2f".format(it) },
            "stalls seen" to bufFrames.size,
        )
        File(d.out, "dmjit-trace.tsv").writeText("now\tpos\trate\tpaused\tbuffering\tsampledAt\n" + shadow.trace.joinToString("\n"))
        File(d.out, "dmjit-frames.tsv").writeText(
            "t\tpaused\tbuffering\tpos\tclears\tx(c-first)\n" + fs.joinToString("\n") { f ->
                val k = f.xs.keys.minByOrNull { it.removePrefix("c").toInt() }
                "%.1f\t%s\t%s\t%.1f\t%d\t%s".format(java.util.Locale.ROOT, f.t, f.paused, f.buffering, f.pos, f.clears, k?.let { "$it=%.2f".format(java.util.Locale.ROOT, f.xs[it]) } ?: "")
            },
        )
        File(d.out, "dmjit-marks.tsv").writeText(marks.entries.joinToString("\n") { "${it.key}\t${"%.1f".format(java.util.Locale.ROOT, it.value)}" } + "\n" +
            bursts.joinToString("\n") { "${it.first}\t${"%.1f".format(java.util.Locale.ROOT, it.second)}\t${it.third}" })
        c.check("dm-measure", "danmaku jitter measurement", Verdict.Pass, values = values, shots = bursts.map { it.third }.take(4))
    }
}
