package com.gugaart.ui.idle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.gugaart.ui.components.CoverFlowState
import com.gugaart.ui.mascot.LocalGugaPrefs
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.TimeSource

/*
 * 「没人动」这件事（用户 2026-11-15）：
 * - 电脑 / 电视：不在首页也不在播放时，10 分钟没有任何输入 → 翻回首页（GugaartApp 里接）；
 * - 三端：首页封面没人动时自己翻页，一碰就停，过一会儿再接着翻；
 * - 三端：封面那几期每 10–20 分钟换一波。
 * 时间和判断都在 [IdleRules]（纯函数，有单测）；[IdleClock] 只记「上一次输入是什么时候」。
 */

/**
 * 上一次输入（按键、鼠标、触摸）到现在过了多久。根界面在事件的第一站记一笔，不吃事件。
 * [sleeper] = 「等这只钟走过多少毫秒」：应用里就是真的 `delay`；测试给一只假钟时也给一个盯着假钟的等法（拨钟就能叫醒等的人）。
 * [now] 放在最后：`IdleClock { now }` 这种尾随 lambda 写法给的是「现在几点」。
 */
class IdleClock(
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = monotonic(),
) {
    private var last = now()

    fun touch() {
        last = now()
    }

    fun idleMs(): Long = now() - last

    /** 等这只钟走过 [ms] 毫秒。 */
    suspend fun sleep(ms: Long) = sleeper(ms)

    companion object {
        fun monotonic(): () -> Long {
            val start = TimeSource.Monotonic.markNow()
            return { start.elapsedNow().inWholeMilliseconds }
        }
    }
}

/** 根界面给的那只钟；没给（离屏测试、截图）= null：不自动翻页、不换一波、不回首页，画面是定死的。 */
val LocalIdleClock = staticCompositionLocalOf<IdleClock?> { null }

/**
 * 这一页现在露着吗。手机的首页离开后不拆（[com.gugaart.ui.KeptPage]，回来第一帧就是完整的首页），
 * 被别的页盖住时这里是 false：自动翻页、换一波都停，不在看不见的地方干活。
 */
val LocalPageShown = compositionLocalOf { true }

object IdleRules {
    /** 闲置多久回首页。 */
    const val HOME_AFTER_MS = 10 * 60_000L

    /** 封面自动翻页：每隔多久翻一张。 */
    const val FLIP_EVERY_MS = 5_000L

    /** 碰过之后要安静多久才接着自动翻。 */
    const val FLIP_RESUME_MS = 5_000L

    /** 封面换一波的间隔：这两个数之间随机。 */
    const val ROTATE_MIN_MS = 10 * 60_000L
    const val ROTATE_MAX_MS = 20 * 60_000L

    /** 换一波也要等人停手这么久（正在翻的时候不换）。 */
    const val ROTATE_QUIET_MS = 15_000L

    /**
     * 现在该不该翻回首页。[enabled] = 设置里开着；[signedIn] = 有会话（登录页上不动）；[atHome] = 已经在首页；[playing] = 在播放器里。
     */
    fun shouldGoHome(idleMs: Long, enabled: Boolean, signedIn: Boolean, atHome: Boolean, playing: Boolean): Boolean =
        enabled && signedIn && !atHome && !playing && idleMs >= HOME_AFTER_MS

    /** 离「该回首页了」还差多久（下一次什么时候再看；至少 1 秒）。 */
    fun homeCheckIn(idleMs: Long): Long = (HOME_AFTER_MS - idleMs).coerceAtLeast(1_000L)

    /**
     * 现在该不该自动翻一张。[idleMs] = 多久没有输入，[sinceFlipMs] = 上一次自动翻到现在多久；[visible] = 封面那一屏正露着
     * （没滚到目录、没被菜单盖住；电视 = 焦点在封面流上）；[count] = 一共几期；[busy] = 手正拖着 / 还在翻。
     */
    fun shouldFlip(idleMs: Long, sinceFlipMs: Long, enabled: Boolean, visible: Boolean, count: Int, busy: Boolean): Boolean =
        enabled && visible && count > 1 && !busy && idleMs >= FLIP_RESUME_MS && sinceFlipMs >= FLIP_EVERY_MS

    /** 自动翻到哪一张：下一张，到头回第一张。 */
    fun nextPage(current: Int, count: Int): Int = if (count <= 0) 0 else (current + 1) % count

    /** 下一波封面等多久（10–20 分钟之间）。 */
    fun rotateDelayMs(random: Random): Long = ROTATE_MIN_MS + random.nextLong(ROTATE_MAX_MS - ROTATE_MIN_MS + 1)
}

/**
 * 封面自动翻页（三端的首页各放一个）。[visible] = 封面那一屏正露着；[flip] = 翻到第几张（电视还要同步自己的 `want`）。
 * 设置 › 首页 ›「封面自动翻页」关了、或者没有 [LocalIdleClock]（测试）= 什么都不做。播放器后面没有首页（那时首页不在组合里），所以不会偷偷翻。
 */
@Composable
fun CoverAutoFlip(flow: CoverFlowState, visible: () -> Boolean, flip: (Int) -> Unit = { flow.animateTo(it) }) {
    val clock = LocalIdleClock.current ?: return
    if (!LocalGugaPrefs.current.coverAutoFlip) return
    if (!LocalPageShown.current) return
    val seen = rememberUpdatedState(visible)
    val go = rememberUpdatedState(flip)
    LaunchedEffect(flow, clock) {
        var lastFlip = 0L
        val since = IdleClock.monotonic()
        while (true) {
            delay(500)
            val busy = flow.dragging || flow.velocity != 0f
            if (IdleRules.shouldFlip(clock.idleMs(), since() - lastFlip, true, seen.value(), flow.count, busy)) {
                go.value(IdleRules.nextPage(flow.settled, flow.count))
                lastFlip = since()
            }
        }
    }
}

/**
 * 封面换一波：每 10–20 分钟（随机）叫一次 [onRotate]（首页重新取数 → 牌堆发新的一波 → 回到第一期）。等人停手才换。
 * 没有 [LocalIdleClock]（测试）= 什么都不做。
 */
@Composable
fun CoverRotation(onRotate: () -> Unit) {
    val clock = LocalIdleClock.current ?: return
    if (!LocalPageShown.current) return
    val go = rememberUpdatedState(onRotate)
    LaunchedEffect(clock) {
        while (true) {
            delay(IdleRules.rotateDelayMs(Random.Default))
            while (clock.idleMs() < IdleRules.ROTATE_QUIET_MS) delay(2_000)
            go.value()
        }
    }
}
