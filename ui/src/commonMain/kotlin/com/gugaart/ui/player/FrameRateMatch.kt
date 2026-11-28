package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.gugugaga.app.player.PlayerSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/** 显示器的一个模式（安卓 `Display.Mode`：物理宽高 + 刷新率）。 */
data class DisplayModeInfo(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

/**
 * 匹配片源帧率（1.6 B1，安卓电视）。界面这一半只说「现在要合哪个帧率」（null = 回到原来的模式）；切显示模式是壳的事：
 * 安卓电视壳把 [host] 设成「挑模式 → `window.attributes.preferredDisplayModeId`」，退出播放把原来的值写回去。别的平台 [host] = null，什么都不做。
 */
object FrameRateMatch {
    var host: ((fps: Double?) -> Unit)? = null

    private var last: Double? = null
    private var asked = false

    /** 同一个值不重复下发（快照每次变都会走到这里）。 */
    fun request(fps: Double?) {
        val h = host ?: return
        if (asked && last == fps) return
        asked = true
        last = fps
        h(fps)
    }

    /** 测试用：忘掉上一次下发的值。 */
    fun reset() {
        asked = false
        last = null
    }
}

/** 片源的帧率：选中版本的视频流（Emby 的 RealFrameRate，没有再 AverageFrameRate），都没有用内核报的。不认识 = null。 */
fun sourceFrameRate(snap: PlayerSnapshot): Double? {
    val v = snap.resolved?.mediaSource?.mediaStreams?.firstOrNull { it.type == "Video" }
    return (v?.realFrameRate ?: v?.averageFrameRate ?: snap.videoSize?.fps)?.takeIf { it.isFinite() && it in 5.0..300.0 }
}

/**
 * 给片源帧率 [fps] 挑一个显示模式（只在和 [current] 同分辨率的模式里挑）：
 * 1. 刷新率是帧率的整数倍（误差 < 0.05 %：23.976 → 23.976 / 47.952 / 119.88 Hz；29.97 → 59.94 Hz）的里面挑刷新率最高的；
 * 2. 没有的话，差一点点的整数倍（< 0.2 %：只有 24 Hz 的电视放 23.976）也行；
 * 3. 还没有 = null（不切；已经切着的要切回原来的）。挑出来的可能就是现在这个模式（壳判断要不要再写）。纯函数，有单测。
 */
fun pickDisplayMode(modes: List<DisplayModeInfo>, current: DisplayModeInfo, fps: Double): Int? {
    if (fps <= 0.0) return null
    val same = modes.filter { it.width == current.width && it.height == current.height }
    fun err(m: DisplayModeInfo): Double {
        val k = (m.refreshRate / fps).roundToInt()
        if (k < 1) return Double.MAX_VALUE
        return abs(m.refreshRate - k * fps) / (k * fps)
    }
    val best = same.filter { err(it) < 0.0005 }.maxByOrNull { it.refreshRate }
        ?: same.filter { err(it) < 0.002 }.maxByOrNull { it.refreshRate }
        ?: return null
    return best.id
}

/**
 * 电视播放器里调一次：开关开着、知道片源帧率 → 下发；关了 / 不知道 → 回到原来的模式；离开播放器 → 回到原来的模式。
 */
@Composable
fun RememberFrameRateMatch(play: PlaySession) {
    val on = com.gugaart.ui.mascot.LocalGugaPrefs.current.matchFrameRate
    val snap by play.controller.snapshot.collectAsState()
    val fps = sourceFrameRate(snap)
    LaunchedEffect(on, fps) { FrameRateMatch.request(if (on) fps else null) }
    DisposableEffect(Unit) { onDispose { FrameRateMatch.request(null) } }
}
