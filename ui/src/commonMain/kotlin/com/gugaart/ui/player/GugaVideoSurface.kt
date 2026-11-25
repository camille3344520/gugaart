package com.gugaart.ui.player

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.gugugaga.app.player.PlayerEngine
import com.gugugaga.app.player.VideoSurface

/**
 * 宿主（窗口）真正的密度。电视上 [com.gugaart.ui.GugaartApp] 把 `LocalDensity` 换成了「窗口宽 / 1920」的设计密度，
 * 这里留着换之前的那个；没换过 = null。
 */
val LocalHostDensity = staticCompositionLocalOf<Density?> { null }

/**
 * 上游的 [VideoSurface]，但量洞的时候用宿主的真密度。
 *
 * 上游把洞的窗口像素按当前 `LocalDensity` 折成 dp 报给引擎，安卓的视频层再按**显示器的**密度乘回像素（`VideoLayer.setViewport`）。
 * 电视上我们换了密度（1920 px 宽、显示器密度 2.0 → 设计密度 1.0），两边差一倍：页边面板开着时视频层排成 2752 × 1548 px，
 * 小窗里只看得到画面左上的四分之一，字幕在屏幕外（1.4.1 fix141 F3）。全幅时洞 = 整个窗口（报 null），所以以前只坏在小窗。
 * 洞里的内容（压暗层等）照旧用界面的密度。
 */
@Composable
fun GugaVideoSurface(engine: PlayerEngine?, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val host = LocalHostDensity.current
    val ui = LocalDensity.current
    if (host == null || host == ui) {
        VideoSurface(engine, modifier, content)
        return
    }
    CompositionLocalProvider(LocalDensity provides host) {
        VideoSurface(engine, modifier) {
            val scope = this
            CompositionLocalProvider(LocalDensity provides ui) { scope.content() }
        }
    }
}
