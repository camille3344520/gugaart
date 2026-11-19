package com.gugaart.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.gugugaga.ds.theme.GuguTheme
import com.gugugaga.ds.theme.LocalWindowDp
import com.gugugaga.ds.theme.UiMode

@Immutable
class GugaThemeValues(
    val uiMode: UiMode,
    val colors: GugaColors,
    val dimens: GugaDimens,
    val type: GugaType,
    val player: GugaPlayerType,
)

private val LocalGuga = staticCompositionLocalOf<GugaThemeValues> { error("没有 GugaTheme：界面要包在 GugaartApp / GugaTheme 里") }

/** 界面代码的唯一入口：`Guga.colors.spot`、`Guga.dimens.margin`、`Guga.type.lead`、`Guga.uiMode`。 */
object Guga {
    val colors: GugaColors @Composable @ReadOnlyComposable get() = LocalGuga.current.colors
    val dimens: GugaDimens @Composable @ReadOnlyComposable get() = LocalGuga.current.dimens
    val type: GugaType @Composable @ReadOnlyComposable get() = LocalGuga.current.type

    /** 播放器里的字号（页边面板、电脑的顶栏和控制坞）。 */
    val player: GugaPlayerType @Composable @ReadOnlyComposable get() = LocalGuga.current.player
    val uiMode: UiMode @Composable @ReadOnlyComposable get() = LocalGuga.current.uiMode

    /** 电脑的弹性单位：`Guga.rem(4.4f)` = 4.4 rem（1440 宽时 44 dp）。手机 / 电视上 1 rem 恒为 10 dp。 */
    @Composable
    @ReadOnlyComposable
    fun rem(x: Float): androidx.compose.ui.unit.Dp = dimens.rem * x

    /** 宋体文字样式。 */
    @Composable
    @ReadOnlyComposable
    fun song(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = colors.ink, lineHeight: TextUnit = TextUnit.Unspecified, letterSpacing: TextUnit = TextUnit.Unspecified): TextStyle =
        TextStyle(color = color, fontSize = size, fontFamily = GugaFonts.song, fontWeight = GugaFonts.songWeight(weight), lineHeight = lineHeight, letterSpacing = letterSpacing)

    /** Georgia 斜体：数字和英文小标。 */
    @Composable
    @ReadOnlyComposable
    fun numeral(size: TextUnit, color: Color = colors.ink, letterSpacing: TextUnit = TextUnit.Unspecified): TextStyle =
        TextStyle(color = color, fontSize = size, fontFamily = GugaFonts.numeral, fontStyle = FontStyle.Italic, letterSpacing = letterSpacing)

    /**
     * 带汉字的斜体小字（「余 19 分 · 59%」「继续观看 · 12」「118 部」）：宋体倾斜。[numeral] 的衬线斜体没有汉字，
     * 汉字会掉到平台字体上、字距是乱的，所以混排的小字用这个；纯数字 / 纯英文仍用 [numeral]。
     */
    @Composable
    @ReadOnlyComposable
    fun note(size: TextUnit, color: Color = colors.ink, letterSpacing: TextUnit = TextUnit.Unspecified): TextStyle =
        TextStyle(color = color, fontSize = size, fontFamily = GugaFonts.song, fontWeight = GugaFonts.songWeight(FontWeight.Normal), fontStyle = FontStyle.Italic, letterSpacing = letterSpacing)

    /** 栏目行：专色、字距 0.3 em。 */
    @Composable
    @ReadOnlyComposable
    fun kicker(): TextStyle = song(type.kicker, FontWeight.Medium, colors.spot, letterSpacing = 0.3.em)
}

/**
 * 「咕嘎画报」主题。必须在上游的 `GuguTheme` 里面（它给出 UiMode、窗口尺寸、按压物理和玻璃要的东西）；
 * 这一层只加画报自己的 token，并把专色的变化做成 700 ms 的渐变（翻封面、进详情页时整本画报一起换色）。
 *
 * [spot]：当前这一期 / 这部片的专色（[SpotColor.of]）；null = 兜底朱红。
 */
@Composable
fun GugaTheme(spot: Color? = null, content: @Composable () -> Unit) {
    val uiMode = GuguTheme.current.uiMode
    val win = LocalWindowDp.current
    val animatedSpot by animateColorAsState(spot ?: GugaColors.SPOT_FALLBACK, tween(GugaMotion.SPOT_MS), label = "spot")
    val values = GugaThemeValues(
        uiMode = uiMode,
        colors = GugaColors(spot = animatedSpot),
        dimens = GugaDimens.of(uiMode, win.width.value, LocalPhoneInsets.current),
        type = GugaType.of(uiMode, if (uiMode == UiMode.Desktop) desktopRem(win.width.value) else 10f),
        player = GugaPlayerType.of(uiMode, if (uiMode == UiMode.Desktop) desktopRem(win.width.value) else 10f),
    )
    CompositionLocalProvider(LocalGuga provides values, content = content)
}
