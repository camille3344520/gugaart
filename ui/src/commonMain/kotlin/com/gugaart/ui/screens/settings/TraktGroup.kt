package com.gugaart.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.ActionWord
import com.gugaart.ui.components.OnOff
import com.gugaart.ui.components.SettingGroup
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.trakt.TraktService
import com.gugaart.ui.trakt.TraktState
import com.gugugaga.app.ui.LocalGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 设置 › 账号与服务器 › Trakt（用户 2026-11-15：「添加 连接trakt功能」→「看了就记到 Trakt」+「Trakt 已看同步回 Emby」）。
 * 连接 = Trakt 的设备码：这里显示一串码，用户在手机 / 电脑浏览器打开 trakt.tv/activate 输入；连上后两个开关和「同步」。
 */
@Composable
internal fun TraktGroup() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { TraktService.bind(g) }
    val state by TraktService.state.collectAsState()
    val prefs by TraktService.prefs.collectAsState()
    val syncMsg by TraktService.syncMessage.collectAsState()
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // 点「连接」时在浏览器里打开授权页（参考 CapyPlayer 走网页授权；码照样写在这里）。打不开（没有浏览器）就算了
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    fun openUrl(url: String) {
        try {
            uriHandler.openUri(url)
        } catch (_: Exception) {
        }
    }
    // 码先放进剪贴板再开网页（用户 2026-11-16：「trakt的码出来的时候给一下复制的按钮，不然用户还要手动输入很麻烦」）：
    // 网页一开就离开了应用，来不及点按钮——所以自动复制一次，码旁边再放一个「复制」
    @Suppress("DEPRECATION")
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { mutableStateOf<String?>(null) }
    fun copy(code: String) {
        runCatching { clipboard.setText(androidx.compose.ui.text.AnnotatedString(code)) }.onSuccess { copied = code }
    }

    SettingGroup("Trakt", "TRAKT")
    when (val s = state) {
        TraktState.Unavailable -> SettingRow("连接 Trakt", note = "这一版不支持", tag = "trakt-unavailable")
        TraktState.Disconnected, is TraktState.Expired -> {
            val expired = state as? TraktState.Expired
            SettingRow(
                if (expired != null) "Trakt 的授权过期了" else "连接 Trakt",
                note = message ?: expired?.user?.let { "@$it" },
                tag = if (expired != null) "trakt-expired" else "trakt-connect",
            ) {
                ActionWord(if (expired != null) "重新连接" else "连接", {
                    message = null
                    // 手机 / 电脑：顺手在浏览器里打开授权页（电视上照旧只显示短码）
                    job = scope.launch {
                        message = TraktService.connect { url ->
                            (TraktService.state.value as? TraktState.Waiting)?.let { copy(it.code.userCode) }
                            openUrl(url)
                        }
                    }
                })
            }
        }
        is TraktState.Waiting -> {
            val isCopied = copied == s.code.userCode
            SettingRow(
                "在浏览器打开 ${s.code.verificationUrl.removePrefix("https://")}",
                note = if (isCopied) "码已复制，在 Trakt 网页登录后粘贴" else "在 Trakt 网页登录后输入下面的码",
                tag = "trakt-waiting",
            ) {
                ActionWord("取消", { job?.cancel(); job = null })
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column {
                    BasicText(s.code.userCode, Modifier.testTag("trakt-code"), style = Guga.numeral(34.sp, colors.ink).copy(fontWeight = FontWeight.Bold, letterSpacing = 0.12.em), maxLines = 1)
                    BasicText("${s.code.expiresInSec / 60} 分钟内有效", style = Guga.note(Guga.type.caption, colors.faint), maxLines = 1)
                }
                ActionWord(if (isCopied) "已复制" else "复制", { copy(s.code.userCode) })
                ActionWord("打开网页", { openUrl(s.code.verificationUrl) })
            }
        }
        is TraktState.Connected -> {
            SettingRow("已连接 Trakt", note = s.user?.let { "@$it" } ?: "已连接", tag = "trakt-connected") {
                ActionWord("断开", { scope.launch { TraktService.disconnect() } }, danger = true)
            }
            SettingRow("看的时候记到 Trakt", tag = "trakt-scrobble") {
                OnOff(prefs.scrobble, { TraktService.setPrefs(g.deps.local, prefs.copy(scrobble = it)) })
            }
            SettingRow("Trakt 看过的同步到 Emby", note = syncMsg ?: "只把未看的标成已看", tag = "trakt-sync") {
                ActionWord(if (syncMsg == "同步中…") "同步中…" else "同步", {
                    val client = g.session.session.value?.client ?: return@ActionWord
                    scope.launch { TraktService.syncToEmby(client) }
                }, enabled = syncMsg != "同步中…")
            }
            SettingRow("打开应用时自动同步", tag = "trakt-autosync") {
                OnOff(prefs.autoSync, { TraktService.setPrefs(g.deps.local, prefs.copy(autoSync = it)) })
            }
        }
    }
    if (state is TraktState.Connected && message != null) {
        Row(Modifier.padding(start = 20.dp, top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicText(message!!, style = Guga.note(Guga.type.caption, colors.dim), maxLines = 1)
        }
    }
}
