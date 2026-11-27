package com.gugaart.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.currentTimeMillis

/**
 * 冷启动计时（1.5.1 perf-win）：壳在 `main()` 第一行设 [startMs]；首页第一帧画出内容时记一条 perf 日志。
 * 只用于测量，不参与任何界面逻辑。没设起点（离屏测试、其它壳没设）时 [elapsedMs] = -1，[BootContentMark] 什么都不记。
 */
object BootPerf {
    @kotlin.concurrent.Volatile
    var startMs: Long = 0L

    /** 从 [startMs] 到现在多少毫秒；没设起点 = -1。 */
    fun elapsedMs(): Long = if (startMs == 0L) -1L else currentTimeMillis() - startMs
}

/**
 * 首页（手机 [com.gugaart.ui.screens.home.HomeScreen] / 电脑 [com.gugaart.ui.desktop.DesktopHomeScreen]）第一帧有内容时
 * 记一条 `boot home-content +N ms`。[content] = 首页数据到了。只记第一次；壳没设 [BootPerf.startMs] 就什么都不记。
 */
@Composable
fun BootContentMark(content: Boolean) {
    val graph = LocalGraph.current
    // 非状态标记：记过就不再记。不用 mutableStateOf 当 LaunchedEffect 的键——那样在里面改它会重启协程把自己取消，日志永远发不出。
    val logged = remember { booleanArrayOf(false) }
    LaunchedEffect(content) {
        if (content && !logged[0]) {
            logged[0] = true
            if (BootPerf.startMs != 0L) {
                withFrameNanos { }
                graph.log.add("perf", "boot home-content +${BootPerf.elapsedMs()} ms")
            }
        }
    }
}
