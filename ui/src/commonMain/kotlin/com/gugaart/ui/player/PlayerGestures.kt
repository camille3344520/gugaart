package com.gugaart.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.data.Rules
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.player.ControlsVisibility
import com.gugugaga.app.player.GestureStart
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayerPlatform
import com.gugugaga.app.player.PlayerPrefsStore
import com.gugugaga.app.player.SurfaceGestures
import com.gugugaga.app.player.TapZone
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 手机播放器的手势（docs/player.md §5）。
 *
 * 手势本身——左半亮度、右半音量、横滑快进（按片长取跨度、甩得快就加速）、双击、长按 2 倍速、阈值——全部是上游的
 * `Modifier.playerGestures` + `GestureMath`，这里一行都没有重写。这个文件只有两样：
 *   1. [GugaGestures]：上游手势层要的那组回调（`SurfaceGestures`），和上游 `PlayPage` 里的接法逐项对应，只是把「中央 HUD」换成贴边提示的状态；
 *   2. [EdgeHints]：贴边提示的外观——亮度条在左缘、音量条在右缘、快进量和长按倍速在左上角（这时顶栏是隐藏的），都带一小块墨底，不压画面中央和字幕。
 */

/** 这一刻的贴边提示。 */
sealed interface EdgeHint {
    data class Brightness(val value01: Double) : EdgeHint
    data class Volume(val value01: Double) : EdgeHint

    /** 横滑快进：松手会跳到 [targetMs]；[fromMs] = 手指按下时的位置。 */
    data class Seek(val targetMs: Double, val fromMs: Double, val durationMs: Double) : EdgeHint
    data class Rate(val rate: Double) : EdgeHint
}

/** 快进量：「+ 0:42」/「− 1:05」。纯函数，有单测。 */
fun seekDeltaLabel(targetMs: Double, fromMs: Double): String {
    val d = targetMs - fromMs
    // 增量不补前导 0：「+ 0:42」，不是「+ 00:42」
    val clock = Rules.clock(abs(d).toLong()).let { if (it.length == 5 && it[0] == '0') it.drop(1) else it }
    return (if (d < 0) "− " else "+ ") + clock
}

/** 百分数：0.616 → "62%"。 */
fun percentLabel(value01: Double): String = "${(value01.coerceIn(0.0, 1.0) * 100).roundToInt()}%"

/** 倍速：2.0 → "2.0×"。 */
fun rateHintLabel(rate: Double): String {
    val tenths = (rate * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}×"
}

/**
 * 上游手势层的回调。整页只造一个（回调执行时才读当前的控制器 / 时钟——手势检测器不能被重组打断，上游踩过这个坑）。
 * 和上游 `PlayPage` 的 `PhoneGestures` 逐项对应；锁屏时除了「点一下」全部不响应。
 */
@Stable
class GugaGestures(
    private val controller: PlayController,
    private val platform: PlayerPlatform,
    private val prefs: PlayerPrefsStore,
    private val controls: ControlsVisibility,
    private val locked: () -> Boolean,
    private val duration: () -> Double,
    /** 点一下画面：有面板就收面板，否则显隐控件。 */
    private val onSingleTap: () -> Unit,
) : SurfaceGestures {
    var hint: EdgeHint? by mutableStateOf(null)
        private set

    /** 每出一次提示加一：提示的自动收起以它为准（新的提示把旧的计时顶掉）。 */
    var hintSeq by mutableIntStateOf(0)
        private set

    /** 这条提示手指离开后还留多久（0 = 一直留到被清掉：长按倍速）。 */
    var hintHoldMs: Long = HOLD_MS
        private set
    private var rateBefore = 1.0
    private var boosting = false
    private var seekFrom: Double? = null

    private fun show(h: EdgeHint, holdMs: Long = HOLD_MS) {
        hint = h
        hintHoldMs = holdMs
        hintSeq++
    }

    fun clear() {
        hint = null
    }

    override fun onTap(zone: TapZone) = onSingleTap()

    override fun onDoubleTap(zone: TapZone) {
        if (locked()) return
        when (zone) {
            TapZone.Left -> controller.seekBy(-STEP_MS)
            TapZone.Right -> controller.seekBy(STEP_MS)
            TapZone.Center -> controller.togglePlay()
        }
    }

    override fun onClick() {
        if (locked()) return
        controller.togglePlay()
    }

    override fun onDoubleClick() {}

    override fun onSeekPreview(target: Double) {
        if (locked()) return
        // 横滑也是拖进度：进拖动模式，停住才真的跳（上游 PlayController.scrubTo）
        val from = seekFrom ?: controller.positionMs().also { seekFrom = it }
        controller.setScrubbing(true)
        controller.scrubTo(target)
        show(EdgeHint.Seek(target, from, duration()), holdMs = 0)
    }

    override fun onSeekCommit(target: Double) {
        if (locked()) return
        controller.setScrubbing(false)
        controller.seekTo(target)
        show(EdgeHint.Seek(target, seekFrom ?: target, duration()), holdMs = 600)
        seekFrom = null
    }

    override fun onSeekCancel() {
        controller.setScrubbing(false)
        seekFrom = null
        clear()
    }

    override fun onAdjust(brightness: Boolean, value01: Double) {
        if (locked()) return
        if (brightness) {
            platform.brightness?.set(value01)
            show(EdgeHint.Brightness(value01))
        } else {
            val sys = platform.systemVolume
            if (sys != null) sys.set(value01) else controller.setVolume(value01)
            show(EdgeHint.Volume(value01))
        }
    }

    override fun onLongPressStart() {
        if (locked() || boosting) return
        boosting = true
        rateBefore = controller.value.rate
        controller.setRate(LONG_PRESS_RATE)
        show(EdgeHint.Rate(LONG_PRESS_RATE), holdMs = 0)
    }

    override fun onLongPressEnd() {
        if (!boosting) return
        boosting = false
        controller.setRate(rateBefore)
        clear()
    }

    override fun read(): GestureStart {
        val p = prefs.state.value
        return GestureStart(controller.positionMs(), duration(), platform.systemVolume?.get() ?: if (p.muted) 0.0 else p.volume, platform.brightness?.get())
    }

    override fun onAnyTouch() {
        if (locked()) return
        controller.userGesture()
    }

    companion object {
        /** 双击左右：退 / 进 10 秒（和中间的两个键一样）。 */
        const val STEP_MS = 10_000.0

        /** 长按：2 倍速（上游的值）。 */
        const val LONG_PRESS_RATE = 2.0
        const val HOLD_MS = 700L
    }
}

/** 贴边提示。放在画面那个 Box 里；[hint] 为 null 时什么都不画。 */
@Composable
fun BoxScope.EdgeHints(gestures: GugaGestures) {
    val hint = gestures.hint
    // 手指离开后留一小会儿再收
    LaunchedEffect(gestures.hintSeq) {
        val hold = gestures.hintHoldMs
        if (hold > 0 && gestures.hint != null) {
            delay(hold)
            gestures.clear()
        }
    }
    when (hint) {
        is EdgeHint.Brightness -> LevelBar(GuguIcon.Sun, "亮度", hint.value01, Modifier.align(Alignment.CenterStart).padding(start = 12.dp).testTag("hint-bright"))
        is EdgeHint.Volume -> LevelBar(GuguIcon.Volume2, "音量", hint.value01, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).testTag("hint-vol"))
        is EdgeHint.Seek -> SeekHint(hint, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp).testTag("hint-seek"))
        is EdgeHint.Rate -> RateHint(hint.rate, Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 12.dp).testTag("hint-long"))
        null -> {}
    }
}

/** 亮度 / 音量：贴边的一竖条，墨底；图标、细轨、斜体百分数、两个字。 */
@Composable
private fun LevelBar(icon: GuguIcon, label: String, value01: Double, modifier: Modifier) {
    val colors = Guga.colors
    Column(modifier.width(40.dp).background(colors.inkWell).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, colors.ink, 15.dp)
        Box(Modifier.padding(top = 10.dp).width(2.dp).height(84.dp).background(colors.ink.copy(alpha = 0.22f))) {
            Box(Modifier.align(Alignment.BottomCenter).width(2.dp).fillMaxHeight(value01.coerceIn(0.0, 1.0).toFloat()).background(colors.ink))
        }
        BasicText(percentLabel(value01), Modifier.padding(top = 8.dp), style = Guga.numeral(14.sp), maxLines = 1)
        BasicText(label, Modifier.padding(top = 2.dp), style = Guga.song(9.sp, color = colors.dim, letterSpacing = 0.2.em), maxLines = 1)
    }
}

/** 横滑快进：左上角一条——「快进」专色小字、「+ 0:42」斜体大数字、「27:12 / 45:01」。 */
@Composable
private fun SeekHint(h: EdgeHint.Seek, modifier: Modifier) {
    val colors = Guga.colors
    Row(modifier.background(colors.inkWell).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(if (h.targetMs < h.fromMs) "快退" else "快进", style = Guga.song(10.sp, FontWeight.Medium, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        BasicText(seekDeltaLabel(h.targetMs, h.fromMs), style = Guga.numeral(22.sp), maxLines = 1)
        val total = if (h.durationMs > 0) " / ${Rules.clock(h.durationMs.toLong())}" else ""
        BasicText(Rules.clock(h.targetMs.toLong()) + total, style = Guga.numeral(11.sp, colors.dim), maxLines = 1)
    }
}

/** 长按倍速：左上角一条——「2.0×」斜体大数字 +「松手恢复」。 */
@Composable
private fun RateHint(rate: Double, modifier: Modifier) {
    val colors = Guga.colors
    Row(modifier.background(colors.inkWell).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(GuguIcon.FastForward, colors.ink, 13.dp, filled = true)
        BasicText(rateHintLabel(rate), style = Guga.numeral(22.sp), maxLines = 1)
        BasicText("松手恢复", style = Guga.note(11.sp, colors.dim), maxLines = 1)
    }
}
