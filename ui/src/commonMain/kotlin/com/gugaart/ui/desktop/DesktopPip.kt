package com.gugaart.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.desktop.detail.DButton
import com.gugaart.ui.desktop.detail.rsp
import com.gugaart.ui.theme.Guga

/*
 * 画中画（MP-7、decisions.md D-lead-20；设计稿 design/macos/ 的 `st=pip`、notes §6）在电脑播放器里的两样东西：
 *  - 顶栏右组的钮（收藏的右边）用的图形：一个画框、右下角一块实心的小画面（设计稿的 `i-pip`，上游图标集里没有这一个）；
 *  - 画中画期间画面那一格的纸面说明「画面在小窗里 · 回到这里」——画面只是搬走了，没有停，所以没有她（notes §10）。
 * 钮和说明都只在 `PlayerPlatform.pictureInPicture != null` 时出现（有能力才画，上游规则）。
 */

/** 设计稿 `i-pip` 的几何（24 格）：外框 3,5 18×14 圆角 2；小画面 12,11.5 6.5×5 圆角 1；线宽同上游线条图标（2 × 0.8）。 */
private object PipGlyphSpec {
    const val GRID = 24f
    const val STROKE = 1.6f
    const val FRAME_X = 3f
    const val FRAME_Y = 5f
    const val FRAME_W = 18f
    const val FRAME_H = 14f
    const val FRAME_R = 2f
    const val INSET_X = 12f
    const val INSET_Y = 11.5f
    const val INSET_W = 6.5f
    const val INSET_H = 5f
    const val INSET_R = 1f
}

/** 画中画的图形（[size] 见方，[color] 着色）。 */
@Composable
internal fun PipGlyph(color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / PipGlyphSpec.GRID
        drawRoundRect(
            color,
            topLeft = Offset(PipGlyphSpec.FRAME_X * u, PipGlyphSpec.FRAME_Y * u),
            size = Size(PipGlyphSpec.FRAME_W * u, PipGlyphSpec.FRAME_H * u),
            cornerRadius = CornerRadius(PipGlyphSpec.FRAME_R * u),
            style = Stroke(PipGlyphSpec.STROKE * u),
        )
        drawRoundRect(
            color,
            topLeft = Offset(PipGlyphSpec.INSET_X * u, PipGlyphSpec.INSET_Y * u),
            size = Size(PipGlyphSpec.INSET_W * u, PipGlyphSpec.INSET_H * u),
            cornerRadius = CornerRadius(PipGlyphSpec.INSET_R * u),
        )
    }
}

/**
 * 画中画期间画面那一格：纸色底上一页说明（设计稿 `macos.js` 的 `pip-note`：专色斜体小标 / 宋体大标题 / 一句人话 / 「回到这里」）。
 * 字幕跟着画面去了小窗（它们画在画面里）；弹幕是界面画的，留在这一页、回来接着飘——说明照实写。
 */
@Composable
internal fun DesktopPipNote(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Box(modifier.fillMaxSize().background(DesktopPlayerSpec.PIP_PAPER).testTag("player-pip-note"), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = Guga.rem(46f)).padding(horizontal = Guga.rem(3f)), horizontalAlignment = Alignment.CenterHorizontally) {
            BasicText("PICTURE IN PICTURE · 画中画", style = Guga.song(rsp(1.05f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            BasicText("画面在小窗里", Modifier.padding(top = Guga.rem(1f), bottom = Guga.rem(1.2f)), style = Guga.song(rsp(3.2f), FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
            BasicText(
                "这一集正在画中画窗口里继续放，字幕跟着过去了；弹幕留在这一页，回来接着飘。小窗是系统的：拖到哪个角都行，别的应用盖不住它。",
                style = Guga.song(rsp(1.4f), color = colors.dim, lineHeight = rsp(2.5f)).copy(textAlign = TextAlign.Center),
                maxLines = 4,
            )
            Box(Modifier.padding(top = Guga.rem(2.4f))) {
                DButton("回到这里", onBack, icon = null, small = true, tag = "player-pip-back")
            }
        }
    }
}
