package com.gugaart.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.gugaart.ui.idle.LocalIdleClock
import com.gugugaga.core.currentTimeMillis
import kotlinx.coroutines.delay

/**
 * 电视帧率自测与自适应降级器（仅在帧率掉下来时降级，静止时零帧开销）。
 *
 * 原理：在根绘制阶段观测**连续动画/滚动帧**的帧间隔（跳过 > [IDLE_GAP_MS] 的静止后第一帧）：
 * 连续出现 [SLOW_STREAK] 帧间隔 ≥ [SLOW_FRAME_MS]（< ~35 fps），才打开 [HOLD_MS] 降级窗口；
 * 帧率恢复或停手后自动退出降级。满帧机器永远不触发；离屏测试不挂钩子。
 */
class TvFrameTracker(
    private val slowFrameMs: Long = SLOW_FRAME_MS,
    private val idleGapMs: Long = IDLE_GAP_MS,
    private val slowStreak: Int = SLOW_STREAK,
    private val holdMs: Long = HOLD_MS,
) {
    private var lastFrameMs: Long = 0L
    private var streak: Int = 0
    var degradedUntilMs: Long = 0L
        private set

    fun onFrame(nowMs: Long): Boolean {
        val prev = lastFrameMs
        lastFrameMs = nowMs
        if (prev == 0L) return nowMs < degradedUntilMs
        val dt = nowMs - prev
        if (dt > idleGapMs) {
            streak = 0
            return nowMs < degradedUntilMs
        }
        if (dt >= slowFrameMs) {
            streak++
            if (streak >= slowStreak) {
                degradedUntilMs = nowMs + holdMs
            }
        } else {
            streak = 0
        }
        return nowMs < degradedUntilMs
    }

    fun isDegraded(nowMs: Long): Boolean = nowMs < degradedUntilMs

    fun reset() {
        lastFrameMs = 0L
        streak = 0
        degradedUntilMs = 0L
    }

    companion object {
        const val SLOW_FRAME_MS = 28L
        const val IDLE_GAP_MS = 250L
        const val SLOW_STREAK = 3
        const val HOLD_MS = 1_500L
    }
}

object TvPerfGovernor {
    var degraded by mutableStateOf(false)
        internal set
}

@Composable
fun rememberTvPerfModifier(enabled: Boolean): Modifier {
    if (!enabled || !TvFlipProbe.governor || LocalIdleClock.current == null) return Modifier
    val tracker = remember { TvFrameTracker() }
    var expireSeq by remember { mutableLongStateOf(0L) }
    LaunchedEffect(expireSeq) {
        if (expireSeq == 0L) return@LaunchedEffect
        val wait = (tracker.degradedUntilMs - currentTimeMillis()).coerceAtLeast(0L)
        if (wait > 0L) delay(wait + 16L)
        if (!tracker.isDegraded(currentTimeMillis())) {
            TvPerfGovernor.degraded = false
        }
    }
    return Modifier.drawWithContent {
        val now = currentTimeMillis()
        val down = tracker.onFrame(now)
        if (down != TvPerfGovernor.degraded) {
            TvPerfGovernor.degraded = down
        }
        if (down && tracker.degradedUntilMs > expireSeq) {
            expireSeq = tracker.degradedUntilMs
        }
        drawContent()
    }
}
