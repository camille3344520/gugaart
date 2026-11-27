package com.gugaart.desktop.perf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.gugaart.desktop.upstream.StageSession
import com.gugaart.ui.player.LocalPlaySessionSink
import com.gugaart.ui.player.PlaySession
import com.gugugaga.app.nav.Route
import com.gugugaga.app.nav.parseRoute
import com.gugugaga.core.currentTimeMillis
import kotlinx.coroutines.runBlocking

/**
 * 测量驱动缝（1.5.1 perf-win2，**只在开发运行**：`-Dgugaart.dev=true` + `--perf-drive=<步骤+步骤…>`）。
 *
 * 这台机器上没有 Windows OCR 语言包（`OcrEngine.TryCreateFromUserProfileLanguages()` 返回 null），
 * 上一批 perf-win 的鼠标点击全是 False，所以本批改成**程序化驱动**：导航、切服务器、暂停/继续都直接调应用自己的接口，
 * 每一步打一行 `[perf] drive <步骤> t=<epochMs>`，和 Mock 的请求日志按时间戳对齐。
 *
 * 不参与任何界面逻辑：不给 `--perf-drive` 就什么都不做，界面一行不改。
 *
 * 步骤（`+` 分隔，程序参数本身是逗号分隔的）：
 * - `wait:<毫秒>`            等这么久
 * - `mark:<名字>`            只打一条标记
 * - `nav:<路由>`             导航（`/`、`/item/123`、`/play/123`，和 `--route` 同一套）
 * - `switch:<服务器本地id>`   切到那台已保存服务器的当前账号（上游 `SessionManager.switchAccount`）
 * - `pause` / `resume`       播放器暂停 / 继续（`PlayController.pause()` / `resume()`）
 * - `quit`                   退出应用
 */
internal class PerfDrive(private val steps: List<String>) {
    private val play = mutableStateOf<PlaySession?>(null)

    val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        CompositionLocalProvider(LocalPlaySessionSink provides { play.value = it }) { content() }
    }

    private fun say(s: String) = println("[perf] drive $s t=${currentTimeMillis()}")

    fun run(session: StageSession) {
        Thread({
            try {
                // 等登录（Mock 夹具账号）
                val deadline = System.currentTimeMillis() + 30_000
                while (session.graph.session.session.value == null && System.currentTimeMillis() < deadline) Thread.sleep(100)
                say("ready signedIn=${session.graph.session.session.value != null}")
                for (raw in steps) {
                    val step = raw.trim()
                    if (step.isEmpty()) continue
                    val name = step.substringBefore(':')
                    val arg = step.substringAfter(':', "")
                    when (name) {
                        "wait" -> { say("wait $arg"); Thread.sleep(arg.toLongOrNull() ?: 0L) }
                        "mark" -> say("mark $arg")
                        "nav" -> {
                            val r = parseRoute(arg) ?: Route.Home
                            javax.swing.SwingUtilities.invokeAndWait { session.graph.nav.navigate(r) }
                            say("nav $arg")
                        }
                        "switch" -> {
                            val saved = runBlocking { session.graph.session.saved() }
                            val srv = saved.servers.firstOrNull { it.id == arg }
                            if (srv == null) { say("switch $arg MISSING"); continue }
                            val uid = srv.activeUserId ?: srv.accounts.firstOrNull()?.userId
                            if (uid == null) { say("switch $arg NOACCOUNT"); continue }
                            val ok = runCatching { runBlocking { session.graph.session.switchAccount(arg, uid) } }
                            say("switch $arg ${if (ok.isSuccess) "ok" else "fail:" + ok.exceptionOrNull()?.message}")
                        }
                        "pause" -> { play.value?.controller?.pause(); say("pause") }
                        "resume" -> { play.value?.controller?.play(); say("resume") }
                        "quit" -> { say("quit"); session.quit(); return@Thread }
                        else -> say("unknown $step")
                    }
                }
                say("done")
            } catch (t: Throwable) {
                say("failed $t")
            }
        }, "gugaart-perf-drive").apply { isDaemon = true }.start()
    }

    companion object {
        /** `--perf-drive=a+b+c`；空 = 没有驱动。 */
        fun parse(raw: String?): List<String> = raw.orEmpty().split('+').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
