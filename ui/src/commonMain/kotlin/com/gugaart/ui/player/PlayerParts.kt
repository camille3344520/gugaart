package com.gugaart.ui.player

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.RadioMark
import com.gugaart.ui.components.Rule
import com.gugaart.ui.data.Rules
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlin.math.roundToInt

/** 播放器的六个功能词 + 片尾的「下一集」：都是页边里的一栏，同一种做法（docs/player.md §2）。 */
enum class PlayerPanel(val id: String, val word: String, val english: String) {
    Eps("eps", "选集", "EPISODES"),
    Sub("sub", "字幕", "SUBTITLES"),
    Audio("audio", "音轨", "AUDIO"),
    Speed("speed", "倍速", "SPEED"),
    Quality("quality", "画质", "QUALITY"),
    Core("core", "内核", "ENGINE"),
    Dm("dm", "弹幕", "DANMAKU"),
    Next("next", "下一集", "NEXT");

    companion object {
        /** 排在底下的七个功能词（「下一集」不是功能词，是片尾自己出现的）。「内核」= 播放器里直接选 EXO / VLC / MPV（用户 2026-11-16）。 */
        val WORDS = listOf(Eps, Sub, Audio, Speed, Quality, Core, Dm)
        fun of(id: String?): PlayerPanel? = entries.firstOrNull { it.id == id }
    }
}

/** 播放器版式的数（手机横屏 905 × 412）。 */
object PlayerSpec {
    /** 面板打开时右侧页边的宽度（内容 170 + 两侧留白）。 */
    val MARGIN = 198.dp

    /** 面板打开时画面离左、上的边。 */
    val INSET = 20.dp
    val INSET_TOP = 14.dp

    /** 时间轴 + 功能词这一条的高度。 */
    val STRIP = 96.dp

    /** 顶栏的高度：弹幕从它下沿以下开始走。 */
    val TOP = 58.dp

    /** 暂停时画面压暗多少、缓冲时压暗多少（decisions D1：暂停只轻微压暗，画面保持可见）。 */
    const val PAUSE_DIM = 0.18f
    const val BUFFER_DIM = 0.40f

    /** 时间轴上方热度柱的根数。 */
    const val HEAT_DOTS = 96
}

/** 画面让位的两种做法（ADR 0002 §5）：默认把画面缩到一边；某个引擎的非全窗视口不可行时退回「贴边小面板」。 */
enum class ViewportMode { Inset, Overlay }

/**
 * 热力图的 N 个桶 → [dots] 根柱的热度（0..1）。没有热力图 → 全 0（不画柱）。纯函数，有单测。
 *
 * 两步：① 每根柱取它那一段的**平均**；② **按这一集自己的起伏拉开**（[stretchHeat]）。
 * 为什么要第二步（用户 2026-11-14 拿《子夜归》第 1 集排查的结论）：上游的热力图以 98 分位为 1、以 0 为底。弹幕很密的一集（那一集 3.5 万条 / 49 分钟）
 * 几乎每个桶都在 0.8–1.0 之间——数据本身没错，但直接按 0..1 画，不管是圆点还是柱都是一条平线。画报只在**显示**时拉开，不改上游的数。
 */
fun heatDots(values: DoubleArray?, dots: Int = PlayerSpec.HEAT_DOTS): FloatArray {
    val out = FloatArray(dots)
    if (values == null || values.isEmpty()) return out
    for (i in 0 until dots) {
        val a = i * values.size / dots
        val b = minOf(maxOf(a + 1, (i + 1) * values.size / dots), values.size)
        var sum = 0.0
        for (j in a until b) sum += values[j]
        out[i] = (sum / (b - a).coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    }
    return stretchHeat(out)
}

/** 拉开之后最冷的那一段还剩多高（不让它贴地：看得出「这里有弹幕、只是相对冷」）。 */
const val HEAT_FLOOR = 0.12f

/** 拉开之后，「中位热度」的那一段落在多高（0..1）：密集的剧集里大多数桶都挤在顶上，把中位压到这里，高能点才冒得出来。 */
const val HEAT_MEDIAN_AT = 0.45f

/**
 * 按这一集自己的起伏拉开：
 * 1. 把「10 分位 .. 最大值」线性映到 0..1（低于 10 分位的落在 0..[HEAT_FLOOR] 那个墩里）；
 * 2. 如果拉开之后中位数还高于 [HEAT_MEDIAN_AT]（= 大多数桶仍挤在顶上，《子夜归》第 1 集就是这样），再取一次幂（1..3 次方），
 *    让中位数落到 [HEAT_MEDIAN_AT]；起伏本来就大的剧集（中位数不高）不取幂，原样。
 * 全 0（没有数据）原样返回；几乎完全平的（最大值和 10 分位差不到 0.02）也原样返回——那是真的平，不把噪声放大成山。纯函数，有单测。
 */
fun stretchHeat(v: FloatArray): FloatArray {
    val positive = v.filter { it > 0f }.sorted()
    if (positive.isEmpty()) return v
    val lo = positive[((positive.size - 1) * 0.10f).toInt()]
    val hi = positive.last()
    if (hi - lo < 0.02f) return v
    val median = ((positive[positive.size / 2] - lo) / (hi - lo)).coerceIn(0f, 1f)
    val k = if (median > HEAT_MEDIAN_AT && median < 1f) (kotlin.math.ln(HEAT_MEDIAN_AT) / kotlin.math.ln(median)).coerceIn(1f, 3f) else 1f
    return FloatArray(v.size) { i ->
        val x = v[i]
        when {
            x <= 0f -> 0f
            x <= lo -> HEAT_FLOOR * (x / lo)
            else -> HEAT_FLOOR + (1f - HEAT_FLOOR) * Math.pow(((x - lo) / (hi - lo)).toDouble(), k.toDouble()).toFloat()
        }
    }
}

/** 热度柱的数（用户 2026-11-14：「一排小点几乎就是一条线，看不清」→ 改成有高度的柱）。 */
object HeatSpec {
    /** 最高的柱（热度 = 1）有多高：手机 / 电脑 / 电视（3 米外也要看得出起伏）。 */
    val PHONE_MAX = 20.dp
    val DESKTOP_MAX = 26.dp
    val TV_MAX = 44.dp
    /** 最矮的柱（热度 = 0 的那一段也留一个墩，看得出「这里有数据、只是冷」）。 */
    val MIN = 2.dp
    /** 柱宽占一格的多少（其余是缝）。 */
    const val FILL = 0.62f
    /** 没播的部分：米白的不透明度（压暗但仍可读）。 */
    const val REST_ALPHA = 0.5f
    /** 低热度的提亮：高度 = 热度 ^ 这个数（< 1 → 中低段的起伏更明显）。 */
    const val GAMMA = 0.8f
}

/** 一根热度柱的高（px）。纯函数，有单测：0 → 最矮的墩；1 → 最高；中间按 [HeatSpec.GAMMA] 提亮。 */
fun heatColumnHeight(v: Float, maxPx: Float, minPx: Float): Float = minPx + Math.pow(v.coerceIn(0f, 1f).toDouble(), HeatSpec.GAMMA.toDouble()).toFloat() * (maxPx - minPx)

/**
 * 画弹幕热度：一排从 [baseY] 往上长的细柱，每柱的高 = 这一段的热度。播过的（[frac] 以左）= [played]（专色），没播的 = [rest] 压暗。
 * 没有热力图（[heat] 全是 0：弹幕太少 / 没匹配上 / 关了）→ **什么都不画**，不拿一排等高的点冒充数据。
 * 三端的时间轴共用（手机 / 电脑 / 电视各给自己的 [maxPx]）。
 */
fun DrawScope.drawHeatColumns(heat: FloatArray, frac: Float, baseY: Float, maxPx: Float, played: Color, rest: Color) {
    val n = heat.size
    if (n == 0 || heat.none { it > 0f }) return
    val slot = size.width / n
    val w = (slot * HeatSpec.FILL).coerceAtLeast(1f)
    val minPx = HeatSpec.MIN.toPx()
    val restInk = rest.copy(alpha = rest.alpha * HeatSpec.REST_ALPHA)
    for (i in 0 until n) {
        val h = heatColumnHeight(heat[i], maxPx, minPx)
        val x = slot * i + (slot - w) / 2
        drawRect(if ((i + 0.5f) / n <= frac) played else restInk, Offset(x, baseY - h), Size(w, h))
    }
}

/**
 * 时间轴：两端是斜体时间；轨道上方一排**弹幕热度柱**（播过的专色、没播的米白压暗；数据是上游的热力图，0..1，以 98 分位为 1）；片头结束 / 片尾开始两根刻度；
 * 当前位置一个向下的小三角。按住拖 = 拖动（拖的时候只预览，松手才 seek；引擎支持时停一下就先 seek，上游的 `scrubTo`）。
 */
@Composable
fun Timeline(
    positionMs: Double,
    durationMs: Double,
    heat: FloatArray,
    marks: List<Double>,
    onScrubStart: () -> Unit,
    onScrub: (Double) -> Unit,
    onScrubEnd: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Guga.colors
    var drag by remember { mutableStateOf<Double?>(null) }
    val shown = drag ?: positionMs
    val dur = rememberUpdatedState(durationMs)
    Row(modifier.fillMaxWidth().height(48.dp).testTag("player-timeline"), verticalAlignment = Alignment.Bottom) {
        BasicText(Rules.clock(shown.toLong()), Modifier.width(58.dp).padding(bottom = 2.dp), style = Guga.numeral(13.sp), maxLines = 1)
        Canvas(
            Modifier
                .weight(1f)
                .height(48.dp)
                .semantics { contentDescription = "进度 ${Rules.clock(shown.toLong())} / ${Rules.clock(durationMs.toLong())}" }
                .pointerInput(Unit) {
                    detectTapGestures { p -> if (dur.value > 0) onScrubEnd((p.x / size.width).coerceIn(0f, 1f) * dur.value) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { p ->
                            if (dur.value > 0) {
                                drag = (p.x / size.width).coerceIn(0f, 1f) * dur.value
                                onScrubStart()
                            }
                        },
                        onDragEnd = { drag?.let(onScrubEnd); drag = null },
                        onDragCancel = { drag = null },
                    ) { change, _ ->
                        if (dur.value > 0) {
                            val ms = (change.position.x / size.width).coerceIn(0f, 1f) * dur.value
                            drag = ms
                            onScrub(ms)
                        }
                    }
                },
        ) {
            val frac = if (durationMs > 0) (shown / durationMs).toFloat().coerceIn(0f, 1f) else 0f
            val railY = size.height - 6.dp.toPx()
            // 当前位置的小三角悬在最高的柱上面
            val dotY = railY - HeatSpec.PHONE_MAX.toPx() - 2.dp.toPx()
            // 弹幕热度：从轨道往上长的一排细柱（播过的专色、没播的米白压暗）
            drawHeatColumns(heat, frac, railY - 2.dp.toPx(), HeatSpec.PHONE_MAX.toPx(), colors.spot, colors.ink)
            // 轨道：没播的细线，播过的专色
            drawLine(colors.faint, Offset(0f, railY), Offset(size.width, railY), 1.dp.toPx())
            drawLine(colors.spot, Offset(0f, railY), Offset(size.width * frac, railY), 2.dp.toPx())
            // 片头 / 片尾的刻度
            if (durationMs > 0) for (m in marks) {
                val x = size.width * (m / durationMs).toFloat().coerceIn(0f, 1f)
                drawLine(colors.ink, Offset(x, railY - 14.dp.toPx()), Offset(x, railY + 3.dp.toPx()), 1.dp.toPx())
            }
            // 当前位置：竖线 + 向下的小三角
            val x = size.width * frac
            drawLine(colors.ink, Offset(x, dotY - 9.dp.toPx()), Offset(x, railY + 4.dp.toPx()), 1.dp.toPx())
            drawPath(
                Path().apply {
                    moveTo(x - 5.dp.toPx(), dotY - 15.dp.toPx())
                    lineTo(x + 5.dp.toPx(), dotY - 15.dp.toPx())
                    lineTo(x, dotY - 7.dp.toPx())
                    close()
                },
                colors.ink,
            )
        }
        BasicText(Rules.clock(durationMs.toLong()), Modifier.width(58.dp).padding(start = 12.dp, bottom = 2.dp), style = Guga.numeral(13.sp), maxLines = 1)
    }
}

/** 一个功能词：宋体 700 的词 + 斜体的当前值。打开着的那个：专色 + 下划线（不靠颜色单独表达）。 */
@Composable
fun FunctionWord(panel: PlayerPanel, value: String?, active: Boolean, warn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val press = rememberPressState()
    val ink = if (active) colors.spot else colors.ink
    Row(
        modifier
            .pressGraphics(press)
            .heightIn(min = 40.dp)
            .pressable(press, label = panel.word + (value?.let { " $it" } ?: ""), onClick = onClick)
            .drawBehind { if (active) drawLine(colors.spot, Offset(0f, size.height - 2.dp.toPx()), Offset(size.width, size.height - 2.dp.toPx()), 1.5.dp.toPx()) }
            .testTag(if (active) "word-on" else "word"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(panel.word, style = Guga.song(15.sp, FontWeight.Bold, ink, letterSpacing = 0.12.em), maxLines = 1)
        if (value != null) BasicText(value, style = Guga.note(12.sp, if (active) colors.spot else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        // 弹幕匹配失败 / 没有弹幕：一个朱红小点（上游的 danmakuWarns）
        if (warn) Box(Modifier.size(5.dp).background(colors.seal, androidx.compose.foundation.shape.CircleShape))
    }
}

/** 页边面板的标题：一条主线、专色斜体英文小标、宋体大标题。 */
@Composable
fun PanelHeader(panel: PlayerPanel, title: String = panel.word, kicker: String = panel.english) {
    val colors = Guga.colors
    val t = Guga.player
    Rule(strong = true)
    BasicText(kicker, Modifier.padding(top = 8.dp * t.k), style = Guga.numeral(t.panelKicker, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
    BasicText(title, Modifier.padding(top = 2.dp * t.k, bottom = 6.dp * t.k), style = Guga.song(t.panelTitle, FontWeight.Black), maxLines = 1)
}

/** 面板里的一行单选：实心小方块 + 字（选中加粗）+ 右边可有一行小字。 */
@Composable
fun PanelOption(text: String, selected: Boolean, onClick: () -> Unit, note: String? = null, number: String? = null) {
    val colors = Guga.colors
    val press = rememberPressState()
    val t = Guga.player
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 34.dp * t.k).pressGraphics(press).pressable(press, label = text, onClick = onClick).testTag(if (selected) "panel-option-on" else "panel-option"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp * t.k),
        ) {
            if (number != null) BasicText(number, Modifier.width(30.dp * t.k), style = Guga.numeral(if (number.length >= 4) t.numberLong else t.number, if (selected) colors.spot else colors.dim), maxLines = 1)
            else RadioMark(selected, size = 6.dp * t.k)
            BasicText(text, Modifier.weight(1f), style = Guga.song(t.option, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) (if (number != null) colors.spot else colors.ink) else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (note != null) BasicText(note, style = Guga.note(t.note, colors.faint), maxLines = 1)
        }
        Rule()
    }
}

/** 面板里的一行步进：标签 + 〔−〕值〔+〕。直接改值，画面实时变。 */
@Composable
fun PanelStepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = Guga.colors
    val t = Guga.player
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp * t.k).testTag("panel-stepper"), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.width(48.dp * t.k), style = Guga.song(t.label, color = colors.dim), maxLines = 1)
        Spacer(Modifier.weight(1f))
        StepKey(GuguIcon.Minus, "$label 减", onMinus)
        BasicText(value, Modifier.width(52.dp * t.k), style = Guga.song(t.option, FontWeight.Bold).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1)
        StepKey(GuguIcon.Plus, "$label 加", onPlus)
    }
}

@Composable
private fun StepKey(icon: GuguIcon, label: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val k = Guga.player.k
    Box(Modifier.size(36.dp * k).pressable(press, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.pressGraphics(press).size(26.dp * k).border(1.dp, colors.dim), contentAlignment = Alignment.Center) { Icon(icon, colors.ink, 12.dp * k) }
    }
}

/** 面板里的小节名（「字体」「字重」）。 */
@Composable
fun PanelSection(text: String) {
    val t = Guga.player
    BasicText(text, Modifier.padding(top = 12.dp * t.k, bottom = 2.dp * t.k), style = Guga.song(t.section, color = Guga.colors.spot, letterSpacing = 0.3.em), maxLines = 1)
}

/** 面板里的一行文字链接（「换一个匹配 →」）。 */
@Composable
fun PanelLink(text: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val t = Guga.player
    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp * t.k).pressGraphics(press).pressable(press, label = text, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text, Modifier.weight(1f), style = Guga.song(t.body, color = colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Icon(GuguIcon.ChevronRight, colors.dim, 12.dp * t.k)
    }
    Rule()
}

/** 百分比的写法。 */
fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

/** 倍速的写法：「1.0×」「1.25×」。 */
fun rateLabel(r: Double): String {
    val hundredths = (r * 100).roundToInt()
    return if (hundredths % 10 == 0) "${hundredths / 100}.${hundredths % 100 / 10}×" else "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}×"
}
