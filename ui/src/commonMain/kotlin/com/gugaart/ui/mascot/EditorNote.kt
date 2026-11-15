package com.gugaart.ui.mascot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.res.Res
import com.gugaart.ui.res.pose_g01
import com.gugaart.ui.res.pose_g01b
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaMotion
import com.gugugaga.ds.physics.LocalMotionPrefs
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.delay

/** 画报自己的设置（`gugaart.settings.v1`）：她出不出现、说不说话，组件从这里读。根界面提供；测试里默认全开。 */
val LocalGugaPrefs = staticCompositionLocalOf { GugaPrefs() }

/** 现在几点（0–23）：台词按时间段变。根界面提供；测试里固定，截图才稳定。 */
val LocalHour = staticCompositionLocalOf { 21 }

/** 署名照多久眨一次眼（随机落在这个区间里）、闭眼多久。 */
object BylineSpec {
    const val BLINK_MIN_MS = 3_200L
    const val BLINK_MAX_MS = 6_400L
    const val CLOSED_MS = 140L
}

/**
 * 圆形署名照：她的 3D 头像（G01，2026-11-15 第三版；原来是上游分层的平涂立绘 `MascotAvatar`，画风对不上新图，换掉），外圈 1 dp 米白线。
 * 活的时候每 3–6 秒眨一下眼（换成闭眼的 G01b 140 ms）；减弱动效、[live] = false（离屏截图、列表里的小号）= 不眨。
 */
@Composable
fun Byline(size: Dp, modifier: Modifier = Modifier, live: Boolean = true) {
    val colors = Guga.colors
    val reduced = LocalMotionPrefs.current.reducedMotion
    var closed by remember { mutableStateOf(false) }
    if (live && !reduced) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(kotlin.random.Random.nextLong(BylineSpec.BLINK_MIN_MS, BylineSpec.BLINK_MAX_MS))
                closed = true
                kotlinx.coroutines.delay(BylineSpec.CLOSED_MS)
                closed = false
            }
        }
    }
    Box(modifier.size(size).clip(CircleShape).border(1.dp, colors.ink.copy(alpha = 0.7f), CircleShape).testTag("mascot-byline")) {
        androidx.compose.foundation.Image(
            org.jetbrains.compose.resources.painterResource(if (closed) Res.drawable.pose_g01b else Res.drawable.pose_g01),
            contentDescription = "咕咕嘎嘎",
            modifier = Modifier.size(size),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
    }
}

/**
 * 她说的话：默认句 [base]；[say] 一句之后约 3 秒恢复默认句（docs/mascot.md §2）。同一格里同一时刻只有一句。
 */
@Stable
class NoteVoice {
    var spoken: String? by mutableStateOf(null)
        private set
    internal var tick by mutableIntStateOf(0)
        private set

    fun say(line: String?) {
        if (line.isNullOrEmpty()) return
        spoken = line
        tick++
    }

    internal fun rest() {
        spoken = null
    }
}

@Composable
fun rememberNoteVoice(): NoteVoice {
    val voice = remember { NoteVoice() }
    LaunchedEffect(voice.tick) {
        if (voice.spoken != null) {
            delay(GugaMotion.MASCOT_LINE_HOLD_MS.toLong())
            voice.rest()
        }
    }
    return voice
}

/**
 * 主编手记 / 主编按：她的小格。版面上本来就有的一栏（跟着页面滚），不是浮层，**不遮挡任何内容**：
 * 圆形署名照 + 专色小标「放映手记 · SCREENING NOTES」+ 一行宋体斜体的话（一行以内，放不下省略）。
 *
 * 设置（`LocalGugaPrefs`）：她关掉 → 整行不出现（版面不留空洞）；只关手记 / subtle → 只留署名照和小标，不说话。
 * [line] = null（这一刻没有合适的话）→ 同样只留署名照。
 */
@Composable
fun EditorNote(line: String?, modifier: Modifier = Modifier, label: String = "放映手记", english: String = "SCREENING NOTES", voice: NoteVoice? = null, photo: Dp = if (Guga.uiMode == UiMode.Tv) 78.dp else 50.dp, live: Boolean = true) {
    val prefs = LocalGugaPrefs.current
    if (!prefs.showMascot) return
    val colors = Guga.colors
    val type = Guga.type
    val text = if (prefs.mascotSpeaks) voice?.spoken ?: line else null
    Row(modifier.fillMaxWidth().testTag("editor-note"), verticalAlignment = Alignment.CenterVertically) {
        Byline(photo, live = live)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            BasicText("$label · $english", style = Guga.note(type.kicker, colors.spot, letterSpacing = 0.2.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (text != null) {
                BasicText(text, Modifier.padding(top = 5.dp), style = Guga.song(type.lead * 0.96f, color = colors.dim).copy(fontStyle = FontStyle.Italic), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * 轮廓占位（生图到位之前）：一圈虚线 + 她的署名照 + 「轮廓占位 · 动作」小字。生图到了换真图，格子不变。
 * [pose]：动作名（「摆稿纸」「摊手」「举笔」「打盹」「鼓掌」「看印花稿纸」「拔掉的电源插头」…，见 docs/mascot.md §5、decisions Q5）。
 */
@Composable
fun PosePlaceholder(pose: String, modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val prefs = LocalGugaPrefs.current
    if (!prefs.showMascot) return
    val colors = Guga.colors
    Column(modifier.testTag("pose-placeholder"), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(colors.line, radius = this.size.minDimension / 2 - 1.dp.toPx(), center = Offset(this.size.width / 2, this.size.height / 2), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))))
            }
            Byline(size * 0.7f, live = false)
        }
        BasicText("轮廓占位 · $pose", Modifier.padding(top = 6.dp), style = Guga.song(Guga.type.fine, color = colors.faint), maxLines = 1)
    }
}
