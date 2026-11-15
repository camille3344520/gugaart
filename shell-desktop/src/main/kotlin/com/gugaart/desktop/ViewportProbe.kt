package com.gugaart.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import com.gugaart.desktop.upstream.StageSession
import com.gugaart.ui.player.LocalPlayerPanelDriver
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO

/**
 * 「画面让位」实测探针（docs/player.md §9，只在开发运行时：`--probe=<目录>`）。
 *
 * 真窗口 + 真内核：等播放起来 → 截全幅（`1-full`）→ 推开字幕面板，画面应该缩到左边（`2-inset`）→ 换成弹幕面板（`3-inset-dm`）→
 * 收起，画面回到全幅（`4-back`）→ 退出。截的是屏幕上窗口那一块（视频是原生子窗口，不在 Compose 图层里，只有截屏看得到）。
 * 每一步在控制台打一行 `[probe]`；图由人打开看——画面在不在左边那一格里、有没有被拉伸、右侧页边是不是纸色。
 * 探针用自己的数据目录（`--data-dir`），所以 `--engine=vlc` 写的设置不会碰用户的设置。
 */
internal class ViewportProbe(private val dir: File, private val engine: String?, private val route: com.gugugaga.app.nav.Route) {
    private val panel = mutableStateOf<String?>(null)

    val wrap: @Composable (@Composable () -> Unit) -> Unit = { content ->
        CompositionLocalProvider(LocalPlayerPanelDriver provides panel) { content() }
    }

    fun run(session: StageSession) {
        dir.mkdirs()
        Thread({
            val tag = engine ?: "auto"
            fun shot(name: String) {
                val ui = session.stage?.ui ?: return
                val r = Rectangle(ui.locationOnScreen, ui.size)
                val file = File(dir, "$tag-$name.png")
                ImageIO.write(Robot().createScreenCapture(r), "png", file)
                println("[probe] $name -> ${file.name} (${r.width} x ${r.height})")
            }
            // 无论哪一步出错都要退出：探针开的窗口不能留在桌面上
            try {
                // 先等登录（Mock 的夹具账号），写好内核设置，再进播放页：这样第一次起播用的就是要测的内核
                val deadline = System.currentTimeMillis() + 20_000
                while (session.graph.session.session.value == null && System.currentTimeMillis() < deadline) Thread.sleep(200)
                check(session.graph.session.session.value != null) { "没有登录上 Mock" }
                engine?.let { session.graph.settings.patch("playback", "engine" to JsonPrimitive(it)) }
                javax.swing.SwingUtilities.invokeAndWait { session.graph.nav.navigate(route) }
                val facts = session.graph.deps.player?.platformFacts()
                println("[probe] signed in, engine=$tag, engines on this machine=${facts?.present?.map { it.id }}, setting=${com.gugugaga.app.player.PlaybackSettings.of(session.graph.settings.value).engine}, navigating")
                val waitMs = System.getProperty("gugaart.probe.waitMs")?.toLongOrNull() ?: 14_000L
                Thread.sleep(waitMs)
                shot("1-full")
                panel.value = "sub"
                Thread.sleep(2_500)
                shot("2-inset")
                panel.value = "dm"
                Thread.sleep(2_000)
                shot("3-inset-dm")
                panel.value = null
                Thread.sleep(2_500)
                shot("4-back")
            } catch (t: Throwable) {
                println("[probe] failed: $t")
            } finally {
                session.quit()
            }
        }, "gugaart-probe").apply { isDaemon = true }.start()
    }
}
