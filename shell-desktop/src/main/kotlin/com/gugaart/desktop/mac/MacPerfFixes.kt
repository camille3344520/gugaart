package com.gugaart.desktop.mac

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 「挪了地方再问一次」的合并器（1.5.1 perf/apple）。
 *
 * 窗口每挪一下，AWT 的 `componentMoved` 就来一次（拖着走时每秒几十次）。原来每一次都起一个协程去问屏幕的 HDR 余量，
 * 而问一次 = `dispatch_sync` 到 AppKit 主线程等它答（播放中主线程正忙着出帧，这一等可能就是一帧）。
 * 屏幕会不会 HDR 只在窗口**停下来**之后才有意义：这里把连着的请求合并成「停 [waitMs] 毫秒再问一次」，
 * 拖动过程中一次都不问，停下才问。[refresh] 抛了也不往外传。
 */
internal class TrailingRefresh(
    private val scope: CoroutineScope,
    private val waitMs: Long = 120L,
    private val refresh: () -> Unit,
) {
    private var job: Job? = null

    fun request() {
        job?.cancel()
        job = scope.launch(Dispatchers.IO) {
            delay(waitMs)
            runCatching { refresh() }
        }
    }
}

/**
 * 同一个对象只接一次（按引用相等；1.5.1 perf/apple）。
 *
 * 播放器页每次重组都会把控制器交过来（`LocalPlaySessionSink`，播放中时钟每 250 ms 一跳就是好几次）；
 * 原来每次都新建一个 `PipPlayback` 对象写进内核。换了控制器才重接。
 */
internal class BindOnce<T : Any>(private val act: (T) -> Unit) {
    private var last: T? = null

    fun call(value: T) {
        if (last === value) return
        last = value
        act(value)
    }
}