package com.gugaart.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「字幕样式」的预览条（设置 › 字幕与音轨；电视设置页右栏）的数值。预览是「一小块画面 + 一行字幕」：画面那块是暗的电影灰
 * （不是墨黑纸——字幕压在画面上，不在纸上），字是字幕的米白 + 黑描边 / 阴影，和内核画出来的同一套颜色。
 */
object SubtitlePreviewSpec {
    /** 画面那块的高（手机 / 电脑）；电视用 [TV_HEIGHT]。 */
    val HEIGHT = 132.dp
    val TV_HEIGHT = 220.dp

    /** 100 % 时的字号（手机 / 电脑）；电视 [TV_TEXT]。按画面那块的高缩放后大致等于内核在同样大的画面上的字号。 */
    val TEXT = 22.sp
    val TV_TEXT = 36.sp

    /** 「默认」位置时最下面一行离画面底边的距离 = 画面高 × 这个比例（mpv 的默认 34 / 720）。 */
    const val DEFAULT_MARGIN = 0.047f

    /** 「标准」描边的宽（占字号的比例；内核的默认大致是字号的 4 %）。 */
    const val OUTLINE_EM = 0.08f

    /** 阴影：往右下错开（占字号的比例）。 */
    const val SHADOW_EM = 0.08f

    /** 画面那块：上浅下深的电影灰（像一帧夜景），字幕颜色固定。 */
    val FILM_TOP = Color(0xFF3B4048)
    val FILM_BOTTOM = Color(0xFF16181C)
    val TEXT_COLOR = Color(0xFFFFFFFF)
    val OUTLINE_COLOR = Color(0xFF000000)
    val SHADOW_COLOR = Color(0xAF000000)
}
