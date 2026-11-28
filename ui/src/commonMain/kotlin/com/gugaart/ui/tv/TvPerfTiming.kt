package com.gugaart.ui.tv

/**
 * 电视交互响应计时（仅 `.perf` / debug 测试包由壳开启，正式 release 包零开销、不打日志）。
 *
 * 时间基准与 Android `KeyEvent.eventTime` 一致（`uptimeMillis`），由宿主在 `Choreographer.postFrameCallback`
 * 收到下一帧呈现时间戳时结算，避免只测到主线程内部调度而漏掉排版/绘制。
 */
object TvPerfTiming {
    var enabled: Boolean = false

    /** 宿主注入：在下一帧渲染回调时传入帧时间（uptimeMillis）。 */
    var postNextFrame: ((onFrameMs: (Long) -> Unit) -> Unit) = {}

    /** 宿主注入：输出 `[tvperf]` 计时日志。 */
    var log: (String) -> Unit = {}

    private var lastKeyMs: Long = 0L
    private var dpadMs: Long = 0L
    private var dpadKey: String = ""

    private var okUpMs: Long = 0L
    private var playerUiReportedId: String? = null

    private var backUpMs: Long = 0L
    private var backFrom: String = ""
    private var backTo: String = ""
    private var backRouteChanged: Boolean = false

    fun onAnyKey(eventTimeMs: Long) {
        if (!enabled) return
        lastKeyMs = eventTimeMs
    }

    fun onDpadDown(eventTimeMs: Long, keyName: String) {
        if (!enabled) return
        dpadMs = eventTimeMs
        dpadKey = keyName
        backUpMs = 0L
    }

    fun onOkUp(eventTimeMs: Long) {
        if (!enabled) return
        okUpMs = eventTimeMs
        playerUiReportedId = null
        backUpMs = 0L
    }

    fun onBackUp(eventTimeMs: Long, fromLabel: String) {
        if (!enabled) return
        okUpMs = 0L
        backUpMs = eventTimeMs
        backFrom = fromLabel
        backTo = ""
        backRouteChanged = false
    }

    fun onFocusGained(focusKey: String) {
        if (!enabled) return
        val dm = dpadMs
        if (dm > 0L) {
            val dk = dpadKey
            dpadMs = 0L
            postNextFrame { frameMs ->
                val delta = (frameMs - dm).coerceAtLeast(0L)
                if (delta < 5_000L) log("[tvperf] dpad key=$dk focus=$focusKey ms=$delta")
            }
        }
        val bm = backUpMs
        if (bm > 0L && backRouteChanged) {
            val from = backFrom
            val to = backTo
            backUpMs = 0L
            backRouteChanged = false
            postNextFrame { frameMs ->
                val delta = (frameMs - bm).coerceAtLeast(0L)
                if (delta < 10_000L) log("[tvperf] back->restore from=$from to=$to focus=$focusKey ms=$delta")
            }
        }
    }

    fun onBackRouteChanged(toLabel: String, immediateFocusKey: String? = null) {
        if (!enabled || backUpMs <= 0L) return
        backTo = toLabel
        backRouteChanged = true
        if (immediateFocusKey != null) {
            val bm = backUpMs
            val from = backFrom
            backUpMs = 0L
            backRouteChanged = false
            postNextFrame { frameMs ->
                val delta = (frameMs - bm).coerceAtLeast(0L)
                if (delta < 10_000L) log("[tvperf] back->restore from=$from to=$toLabel focus=$immediateFocusKey ms=$delta")
            }
        }
    }

    fun onDetailContent(id: String) {
        if (!enabled) return
        val om = okUpMs
        if (om <= 0L) return
        okUpMs = 0L
        postNextFrame { frameMs ->
            val delta = (frameMs - om).coerceAtLeast(0L)
            if (delta < 15_000L) log("[tvperf] ok->detail id=$id ms=$delta")
        }
    }

    fun onPlayerContent(id: String, ready: Boolean) {
        if (!enabled) return
        val om = okUpMs
        if (om <= 0L) return
        if (!ready) {
            if (playerUiReportedId == id) return
            playerUiReportedId = id
            postNextFrame { frameMs ->
                val delta = (frameMs - om).coerceAtLeast(0L)
                if (delta < 15_000L) log("[tvperf] ok->player-ui id=$id ms=$delta")
            }
        } else {
            okUpMs = 0L
            postNextFrame { frameMs ->
                val delta = (frameMs - om).coerceAtLeast(0L)
                if (delta < 15_000L) log("[tvperf] ok->player-ready id=$id ms=$delta")
            }
        }
    }

    private var reactedKeyMs: Long = 0L

    /**
     * 播放器对遥控器按键的反应（批次 mpvanr）：暂停 / 字幕 / 音轨在界面上变了，记「这次按键 → 下一帧」，每次按键只记一条。
     * 内核那边确认（mpv 真的停了 / 换了轨）的时间由上游 `GuguMpv` 的 `[tvperf] mpv-ack` 另记。
     */
    fun onPlayerReaction(kind: String) {
        if (!enabled) return
        val km = lastKeyMs
        if (km <= 0L || km == reactedKeyMs) return
        reactedKeyMs = km
        postNextFrame { frameMs ->
            val delta = (frameMs - km).coerceAtLeast(0L)
            if (delta < 5_000L) log("[tvperf] player-key kind=$kind ms=$delta")
        }
    }

    fun onPanelToggle(opened: Boolean, kind: String) {
        if (!enabled) return
        val km = lastKeyMs
        if (km <= 0L) return
        postNextFrame { frameMs ->
            val delta = (frameMs - km).coerceAtLeast(0L)
            if (delta < 5_000L) log("[tvperf] panel-${if (opened) "open" else "close"} kind=$kind ms=$delta")
        }
    }
}
