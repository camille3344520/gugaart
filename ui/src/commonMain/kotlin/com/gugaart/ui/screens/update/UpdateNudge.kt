package com.gugaart.ui.screens.update

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 比上游更勤的更新检查（用户 2026-11-15：「推个1.0的正式版，三端都可以直接更新的。我发现现在还是需要手动覆盖安装」）。
 *
 * 上游的更新器自己一天最多查一次（`UPDATE_CHECK_INTERVAL_MS` = 24 小时）：早上查过，下午发的新版要等到第二天才出号外，
 * 用户只好手动覆盖安装。画报这边再补两道：启动后（上游那次之后）距上次检查满 [AFTER_START_MS] 就查；开着不关的（电视常年开着）
 * 每 [TICK_MS] 看一眼，满 [EVERY_MS] 再查。走的是上游的「检查更新」（和 设置 › 关于 里那个按钮同一个），验签、比版本都在上游。
 */
object UpdateNudge {
    /** 第一次看在启动后多久（上游自己那次在 10 秒）。 */
    const val FIRST_MS = 20_000L
    /** 启动时：距上次检查满 1 小时就再查。 */
    const val AFTER_START_MS = 60L * 60_000L
    /** 开着的时候：满 3 小时再查。 */
    const val EVERY_MS = 3L * 60L * 60_000L
    /** 多久看一眼。 */
    const val TICK_MS = 30L * 60_000L

    /** 该不该再查：从没查过、钟往回拨了、或者离上次满 [interval]。纯函数，有单测。 */
    fun due(lastCheckAt: Long?, now: Long, interval: Long): Boolean {
        if (lastCheckAt == null || lastCheckAt <= 0L) return true
        val since = now - lastCheckAt
        return since < 0L || since >= interval
    }

    @OptIn(ExperimentalTime::class)
    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()
}
