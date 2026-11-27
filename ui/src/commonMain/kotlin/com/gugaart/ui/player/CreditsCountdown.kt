package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.gugugaga.app.player.AutoNextState
import com.gugugaga.app.player.PlayerSnapshot
import kotlinx.coroutines.delay

/*
 * 片尾倒数自动下一集（审计 P1-3，1.5.1）：进了片尾标记，倒数 10 秒接下一集；按钮写「下一集 · 8」，旁边「取消」。
 * 设置 › 播放「片尾自动播下一集」（GugaPrefs.creditsAutoNext，默认开）关掉 = 不倒数。没有片尾标记的片子照旧在播完时接下一集（上游）。
 * 暂停时不数。「取消」= 上游 cancelAutoNext（播完也不再自动接）。
 */

/** 倒数几秒。 */
const val CREDITS_COUNTDOWN_S = 10

/** 该不该倒数：开着、有下一集、上游已经进了「下一集」状态、片子有片尾标记而且已经放到了。纯函数，有单测。 */
fun creditsCountdownArmed(enabled: Boolean, hasNext: Boolean, autoNext: AutoNextState, creditsStartMs: Long?, posMs: Double): Boolean =
    enabled && hasNext && autoNext != AutoNextState.Idle && creditsStartMs != null && posMs >= creditsStartMs

/** 「下一集 · 8」；不在倒数 = 「下一集」。 */
fun nextButtonLabel(seconds: Int?): String = if (seconds == null) "下一集" else "下一集 · $seconds"

/**
 * 片尾的倒数：返回按钮上的数（倒数中 = 剩几秒；上游自己在播完前数的最后 10 秒 = 片子还剩几秒；都不是 = null）。
 * 数到 0 就接下一集。三端的「下一集」面板 / 页都用它。
 */
@Composable
fun rememberNextCountdown(play: PlaySession, snap: PlayerSnapshot, position: () -> Double, duration: Double): Int? {
    val enabled = com.gugaart.ui.mascot.LocalGugaPrefs.current.creditsAutoNext
    val credits = snap.markers.credits?.startMs
    val positionNow by rememberUpdatedState(position)
    var left by remember(snap.itemId) { mutableStateOf<Int?>(null) }
    val waiting = enabled && snap.next != null && credits != null && snap.autoNext != AutoNextState.Idle
    LaunchedEffect(snap.itemId, waiting) {
        left = null
        if (!waiting || credits == null) return@LaunchedEffect
        while (!creditsCountdownArmed(true, true, play.controller.value.autoNext, credits, positionNow())) {
            if (play.controller.value.autoNext == AutoNextState.Idle) return@LaunchedEffect
            delay(250)
        }
        var n = CREDITS_COUNTDOWN_S
        left = n
        while (n > 0) {
            delay(1000)
            if (!play.controller.value.paused) {
                n--
                left = n
            }
        }
        play.controller.playNext()
    }
    return left ?: if (snap.autoNext == AutoNextState.Counting && duration > 0) ((duration - position()) / 1000).toInt().coerceAtLeast(0) else null
}
