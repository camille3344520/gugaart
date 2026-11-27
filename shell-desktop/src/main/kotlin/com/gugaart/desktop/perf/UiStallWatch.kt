package com.gugaart.desktop.perf

import javax.swing.SwingUtilities

/**
 * UI 线程卡顿看门狗（1.5.1 perf-win，只在开发运行 `-Dgugaart.dev=true` 时由 Main 启动）：
 * 每 100 ms 往 AWT 事件线程（Compose Desktop 的帧钟 / 输入都在这条线程上）投一个空任务，
 * 投进去到被执行超过 [STALL_MS] 就记一条 `ui-stall` —— 界面卡不卡有了数字，不用靠眼睛。
 * 空任务本身几乎不花时间；发布物里不启动。
 */
object UiStallWatch {
    private const val STALL_MS = 250L
    @Volatile private var started = false

    fun start() {
        if (started) return
        started = true
        val t = Thread({
            while (true) {
                val t0 = System.nanoTime()
                try {
                    SwingUtilities.invokeAndWait { }
                } catch (_: Throwable) {
                    return@Thread
                }
                val gapMs = (System.nanoTime() - t0) / 1_000_000
                if (gapMs >= STALL_MS) println("GuguPlayer [perf] ui-stall $gapMs ms")
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "gugu-ui-stall-watch")
        t.isDaemon = true
        t.start()
    }
}
