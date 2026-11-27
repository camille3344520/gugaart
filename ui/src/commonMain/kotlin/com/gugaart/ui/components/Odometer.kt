package com.gugaart.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.LocalMotionPrefs
import kotlin.math.floor
import kotlin.math.roundToInt

/** 里程表的一位：现在的数字、下一个数字、滚到哪了（0..1）。不滚的位 [roll] = 0。 */
data class OdometerDigit(val now: Char, val next: Char, val roll: Float)

/**
 * 里程表怎么滚（纯函数，有单测）：[value] 是连续的数（翻封面时 = 翻页位置 + 1）。整数部分是「现在」，小数部分是滚动进度；
 * **只有会变的那几位在滚**（03 → 04 只滚个位；09 → 10 两位一起滚），像真的里程表。位数不够补 0 到 [minDigits]。
 */
fun odometerDigits(value: Float, minDigits: Int = 2): List<OdometerDigit> {
    val v = value.coerceAtLeast(0f)
    val n = floor(v).toInt()
    val frac = v - n
    val a = n.toString().padStart(minDigits, '0')
    val b = (n + 1).toString().padStart(minDigits, '0')
    val width = maxOf(a.length, b.length)
    val now = a.padStart(width, '0')
    val next = b.padStart(width, '0')
    return List(width) { i -> OdometerDigit(now[i], next[i], if (now[i] != next[i]) frac else 0f) }
}

/**
 * 滚动的数字。每一位是一个裁切的小窗：现在的数字往上走、下一个数字从下面进来，位移 = 滚动进度 × 字高。
 * 减弱动效：不滚，过半就直接换数。读屏只读整数。
 */
@Composable
fun RollingNumber(value: Float, style: TextStyle, modifier: Modifier = Modifier, minDigits: Int = 2) {
    val reduced = LocalMotionPrefs.current.reducedMotion
    val shown = if (reduced) value.roundToInt().toFloat() else value
    val digits = odometerDigits(shown, minDigits)
    Row(modifier.clearAndSetSemantics { contentDescription = value.roundToInt().toString() }.testTag("odometer")) {
        for (d in digits) {
            Layout(
                content = {
                    // 电视上 150 sp 的字在屏幕上 300 px，超过字形图集的上限，每帧按路径重画；放进离屏层后滚动只是挪层（探针 odolayer）
                    val glyph = if (com.gugaart.ui.tv.TvFlipProbe.odoLayer) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier
                    BasicText(d.now.toString(), glyph, style = style, maxLines = 1)
                    BasicText(d.next.toString(), glyph, style = style, maxLines = 1)
                },
                modifier = Modifier.clipToBounds(),
            ) { measurables, constraints ->
                val a = measurables[0].measure(constraints)
                val b = measurables[1].measure(constraints)
                val w = maxOf(a.width, b.width)
                val h = a.height
                layout(w, h) {
                    val dy = (d.roll * h).roundToInt()
                    a.place((w - a.width) / 2, -dy)
                    if (d.roll > 0f) b.place((w - b.width) / 2, h - dy)
                }
            }
        }
    }
}

/**
 * 期号里程表：Georgia 斜体大数字 `03` + 小的 `/ 08`。翻封面时数字跟手滚（[position] = 海报流的连续翻页位置，0 起）。
 * 字号来自 `Guga.type.issue`（手机 66 / 电脑 96 / 电视 150）。
 */
@Composable
fun IssueOdometer(position: () -> Float, total: Int, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        // 位置在这里才读（1.5.1 性能）：翻页动画每帧只重组这只里程表，不连带整个首页
        val at = position().coerceIn(0f, (total - 1).coerceAtLeast(0).toFloat())
        RollingNumber((if (com.gugaart.ui.tv.TvFlipProbe.odometer) at else at.roundToInt().toFloat()) + 1f, Guga.numeral(type.issue))
        Box(Modifier.padding(start = 6.dp, bottom = with(androidx.compose.ui.platform.LocalDensity.current) { (type.issue.toPx() * 0.2f).toDp() })) {
            BasicText("/ ${total.toString().padStart(2, '0')}", style = Guga.numeral(type.issue * 0.23f, colors.dim), maxLines = 1)
        }
    }
}
