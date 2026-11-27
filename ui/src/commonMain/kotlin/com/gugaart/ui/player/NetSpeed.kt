package com.gugaart.ui.player

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.gugugaga.app.player.PlayController
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/*
 * 播放器里的网速（用户 2026-11-17：「播放的时候调出菜单，能够自动显示当前的网络速度」）：控件一出来就在，跟着控件一起隐去。
 *  - 安卓：系统按应用记的收包字节数（壳填 [NetTraffic.rxBytes] = TrafficStats.getUidRxBytes），每秒取一次，取最近 3 秒的平均——
 *    不管用的是 ExoPlayer、VLC 还是 mpv（都在本进程里）都准；系统不给这个数的机器退回内核自己的估计。
 *  - Windows：mpv 是另一个进程，本进程的流量里没有它，用 mpv 自己报的 `cache-speed`（上游 PlayerEngine.throughput）。
 * 缓存满了、内核暂时不下载时就是「0 KB/s」——如实写；内核答不上来（null）才不显示。
 */

/** 壳装上的「本应用一共收了多少字节」（安卓：TrafficStats）；null = 这一端没有 / 系统不给。 */
object NetTraffic {
    var rxBytes: (() -> Long?)? = null
}

/** 网速的写法：≥ 1 MB/s 一位小数的 MB/s，以下整数 KB/s（0 也写「0 KB/s」）；null → null。纯函数，有单测。 */
fun netSpeedLabel(bytesPerSecond: Double?): String? {
    val v = bytesPerSecond?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
    return if (v >= 1_000_000) {
        val tenths = (v / 100_000).roundToInt()
        "${tenths / 10}.${tenths % 10} MB/s"
    } else "${(v / 1_000).roundToInt()} KB/s"
}

/** 最近几次采样（字节数、时刻）→ 平均每秒多少字节；不够两次 → null。纯函数，有单测。 */
fun averageRate(samples: List<Pair<Long, Double>>): Double? {
    if (samples.size < 2) return null
    val (b0, t0) = samples.first()
    val (b1, t1) = samples.last()
    val dt = t1 - t0
    if (dt <= 0.0 || b1 < b0) return null
    return (b1 - b0) / dt
}

/** 截图测试用：固定显示这个网速（离屏渲染里没有真的下载，也等不到两次采样）。 */
val LocalNetSpeedPreview = staticCompositionLocalOf<String?> { null }

/**
 * 现在的网速（「3.2 MB/s」），null = 没有可显示的数。给的是 State（1.5.1 性能）：每秒采一次样，以前在调用它的那一层读——
 * 控件收着也每秒把整个播放页重组一遍；现在只有显示网速的那一格（[NetSpeedText]）读它，而且字变了才重组。
 */
@Composable
fun rememberNetSpeed(controller: PlayController): State<String?> {
    LocalNetSpeedPreview.current?.let { preview -> return remember(preview) { mutableStateOf(preview) } }
    val engine by controller.engineFlow.collectAsState()
    val engineFlow = remember(engine) { engine?.throughput }
    val fromEngine = (engineFlow ?: kotlinx.coroutines.flow.MutableStateFlow<Double?>(null)).collectAsState()
    val sampler = NetTraffic.rxBytes
    val fromApp = remember { mutableStateOf<Double?>(null) }
    val appWorks = remember { mutableStateOf(sampler != null) }
    LaunchedEffect(sampler) {
        if (sampler == null) return@LaunchedEffect
        val clock = TimeSource.Monotonic.markNow()
        val window = ArrayDeque<Pair<Long, Double>>()
        while (true) {
            val bytes = runCatching { sampler() }.getOrNull()
            if (bytes == null || bytes < 0) {
                appWorks.value = false
                fromApp.value = null
                return@LaunchedEffect
            }
            window.addLast(bytes to clock.elapsedNow().inWholeMilliseconds / 1000.0)
            while (window.size > 4) window.removeFirst()
            fromApp.value = averageRate(window)
            delay(1_000)
        }
    }
    return remember(fromEngine, fromApp, appWorks) { derivedStateOf { netSpeedLabel(if (appWorks.value) fromApp.value else fromEngine.value) } }
}

/** 「网速 3.2 MB/s」那一格：网速只在这里读（自己一个重组作用域）；没有数 = 不占地方（和以前一样）。 */
@Composable
fun NetSpeedText(speed: State<String?>, modifier: Modifier, style: TextStyle) {
    speed.value?.let { BasicText("网速 $it", modifier, style = style, maxLines = 1) }
}
