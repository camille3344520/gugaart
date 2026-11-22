package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gugaart.ui.data.LocalFontImport
import com.gugaart.ui.screens.settings.rememberGugaStore

/**
 * 把记住的着色器选择（[com.gugaart.ui.data.GugaPrefs.shader]）应用到当前引擎。
 *
 * 订阅 `controller.engineFlow` 与画报设置：引擎或选择一变就把预设的着色器提取成缓存文件（[extractShaders]）、经
 * [com.gugugaga.app.player.PlayerEngine.shaders] 下发。面板点击改的是同一个 store（根提供的 [com.gugaart.ui.screens.settings.LocalGugaSettings]），
 * 所以点完立即应用；下次开播 / 换内核（engineFlow 变）也沿用。引擎没有着色器链（ExoPlayer / VLC）就什么都不做；
 * 平台没有文件缝（[LocalFontImport] 为 null）或选了「关」→ 下发空链清除。
 *
 * 三端的播放器各调一行（手机 [PlayerPhone]、电脑 [com.gugaart.ui.desktop.DesktopPlayer]、电视 [com.gugaart.ui.tv.player.TvPlayerScreen]）。
 */
@Composable
fun RememberShaderApply(play: PlaySession) {
    val store = rememberGugaStore()
    val prefs by store.prefs.collectAsState()
    val engine by play.controller.engineFlow.collectAsState()
    val host = LocalFontImport.current
    val preset = prefs.shader
    LaunchedEffect(engine, preset, host) {
        val control = engine?.shaders ?: return@LaunchedEffect
        control.set(extractShaders(preset, host))
    }
    // 验收缝（照 LocalPageState「pick=engine」的先例）：iOS 进程内运行器造不出真触摸，
    // 用「shader-click=<id>」走面板选项同一个 store.update，让模拟器验收能切档；应用里没人设置它。
    val pageState = com.gugaart.ui.screens.LocalPageState.current
    LaunchedEffect(pageState) {
        val ps = pageState ?: return@LaunchedEffect
        if (ps.startsWith("shader-click=")) store.update { it.copy(shader = ShaderPreset.of(ps.removePrefix("shader-click="))) }
    }
}
