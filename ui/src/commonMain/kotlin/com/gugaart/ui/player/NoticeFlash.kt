package com.gugaart.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.data.OtherServerHealth
import com.gugaart.ui.data.OtherServers
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.app.player.Notice
import com.gugugaga.app.player.NoticeTone
import kotlinx.coroutines.delay

/*
 * 播放器里一闪而过的那句话（1.5.0 B2-UI）：换到别台服务器的源没成功时，上游把一切放回原样、原来的继续放，
 * 只发一条 Warning 通知——以前这类通知只排在「缓冲中」下面，不缓冲就看不见。这里把「换台失败」那一条
 * 在画面左上（徽章那一行下面，和字幕「已自动加载」同一个位置、同一种墨底小条）亮 4 秒再淡出。不挡操作、不用点。
 */

/** 小条亮多久。 */
const val NOTICE_FLASH_MS = 4_000L

/**
 * 「换台失败」要在画面上说的话（纯函数，有单测）：上游的标题是「<why>失败」——why = 「换到「香港中转」」或「切回本台」。
 * 别的通知 = null（照旧只排在缓冲态下面）。
 */
fun switchFailedLine(n: Notice?): String? {
    if (n == null || n.tone != NoticeTone.Warning) return null
    Regex("^换到「(.+)」失败$").matchEntire(n.title.trim())?.let { return "换到「${it.groupValues[1]}」没成功，继续播放当前的" }
    if (n.title.trim() == "切回本台失败") return "切回本台没成功，继续播放当前的"
    return null
}

/** 亮 [NOTICE_FLASH_MS] 的小条：每来一条「换台失败」（[PlaySession.noticeSeq] 变了）亮一次。[scale]：手机 1、电脑 1.15、电视 1.9（和字幕提示同一套）。 */
@Composable
fun PlayerNoticeFlash(play: PlaySession, scale: Float, modifier: Modifier = Modifier, maxWidth: Dp = 420.dp) {
    val g = LocalGraph.current
    val seq = play.noticeSeq
    var text by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(-1) }
    LaunchedEffect(seq) {
        val line = switchFailedLine(play.notice) ?: return@LaunchedEffect
        // 换到别台没成功：那台此刻算连不上（池的健康表；要重新登录的不改），「源」单子里它的小圆点变灰
        if (play.notice?.title?.startsWith("换到「") == true) RemotePlay.lastTried?.let { id ->
            val pool = OtherServers.poolFor(g)
            if (pool.health.value[id] != OtherServerHealth.NeedsLogin) pool.report(id, null)
        }
        text = line
        shown = seq
        delay(NOTICE_FLASH_MS)
        if (shown == seq) shown = -1
    }
    AnimatedVisibility(shown == seq && text.isNotEmpty(), modifier, enter = fadeIn(tween(260)), exit = fadeOut(tween(600))) {
        NoticeChip(text, scale, maxWidth)
    }
}

/** 小条本身（墨底 + 细框 + 一句宋体粗字，和字幕「已自动加载」那一条同一种；离屏截图也直接画它）。 */
@Composable
fun NoticeChip(text: String, scale: Float, maxWidth: Dp) {
    val colors = Guga.colors
    Row(
        Modifier.widthIn(max = maxWidth).background(colors.inkWell).border(1.dp, colors.line)
            .padding(horizontal = 12.dp * scale, vertical = 7.dp * scale).testTag("player-notice-flash"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text, style = Guga.song(12.sp * scale, FontWeight.Bold, colors.ink, letterSpacing = 0.04.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
