package com.gugaart.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/*
 * 盖在画面上的纸（字幕搜索栏、弹幕来源、播放器面板）怎么接触屏（1.5.1 subfix，主理人：「安卓选字幕，上下滑动很困难，经常点不到」
 * 「点击画面中间，选择字幕的地方也不会消失」）。
 *
 * 以前这些纸在 Main 阶段把**所有**事件吃掉（不让点击漏到后面的画面）。可 Compose 的滚动在过触摸阈值之前、点击在等抬起的时候，
 * 都要在 Final 阶段看「事件有没有被别人消费」：父层一吃，慢慢拖就滚不动、手指一抖点击就作废——真手指总会抖几个像素，模拟器的 `input tap` 不抖。
 *
 * 不漏到画面其实不用吃事件：同一个 Box 里的兄弟节点，命中测试停在最上面那个被点中、带指针输入的节点上，下面的兄弟收不到
 * （画面的手势层是兄弟，不是祖先）。所以纸上只要「在场」（[holdTouches]），里面的滚动、点击照常；纸外面再垫一层 [TapOutside]。
 */

/**
 * 这一块挡住后面的兄弟节点（点击、拖动都到不了下面的画面），自己不吃按下和移动：里面的滚动、点击照常过触摸阈值。
 * 用在盖在画面上的纸上（字幕搜索栏、弹幕来源、播放器面板）。
 *
 * 另外**拖过的不算点**：手指在这一块里走出了按压阈值（上游 `PressConfig.TOUCH_SLOP_DP`），抬起那一下在 Initial 阶段吃掉。
 * 为什么：上游的 `pressable` 只在 Main 阶段看「被滚动拿走了没有」，可滚动是它的祖先、Main 阶段在它后面才消费；
 * 列表一滚，按钮跟着手指走，按钮里看手指几乎没动——从按钮上开始拖一段列表，松手就成了点那个按钮
 * （模拟器上从「最合适」卡的「保存到服务器」上慢慢拖，松手就存进了服务器）。按压的位置用这一块自己的坐标算（它不跟着滚）。
 */
fun Modifier.holdTouches(): Modifier = pointerInput(Unit) {
    val slop = com.gugugaga.ds.physics.PressConfig.TOUCH_SLOP_DP.dp.toPx()
    val downs = HashMap<PointerId, Offset>()
    val dragged = HashSet<PointerId>()
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            for (ch in ev.changes) {
                when {
                    ch.changedToDownIgnoreConsumed() -> {
                        downs[ch.id] = ch.position
                        dragged -= ch.id
                    }
                    ch.changedToUpIgnoreConsumed() -> {
                        if (ch.id in dragged) ch.consume()
                        downs -= ch.id
                        dragged -= ch.id
                    }
                    ch.pressed -> downs[ch.id]?.let { d -> if ((ch.position - d).getDistance() > slop) dragged += ch.id }
                }
            }
        }
    }
}

/**
 * 纸外面那一层（透明，整个父 Box 那么大）：点一下（手指抖几个像素也算）= [onTap]（收起，和返回键一样）；拖动、长按不算点。
 * 下面的画面一个事件都收不到：不开关控件、不暂停、不拖进度、不调音量亮度。画在纸**下面**（先画它，再画纸）。
 */
@Composable
fun BoxScope.TapOutside(onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(Modifier.matchParentSize().closeOnTap(onTap).then(modifier))
}

/** [TapOutside] 的手势：按下到抬起没走出触摸阈值 = 点；走出了 = 拖，不算。全部吃掉（这一层下面不该有人收到）。 */
fun Modifier.closeOnTap(onTap: () -> Unit): Modifier = composed {
    val tap = rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            var still = true
            while (true) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (ch.changedToUpIgnoreConsumed()) {
                    ch.consume()
                    if (still) tap.value()
                    break
                }
                if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) still = false
                ch.consume()
            }
        }
    }
}
