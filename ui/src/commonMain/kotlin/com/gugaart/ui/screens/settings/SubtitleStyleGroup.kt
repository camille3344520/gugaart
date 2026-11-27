package com.gugaart.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.ActionWord
import com.gugaart.ui.components.OnOff
import com.gugaart.ui.components.PickOption
import com.gugaart.ui.components.PickRow
import com.gugaart.ui.components.Segmented
import com.gugaart.ui.components.SettingGroup
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.components.Stepper
import com.gugaart.ui.data.FontFileState
import com.gugaart.ui.data.SUB_SCALE_MAX
import com.gugaart.ui.data.SUB_SCALE_MIN
import com.gugaart.ui.data.StyleFont
import com.gugaart.ui.data.SubOutline
import com.gugaart.ui.data.SubPosition
import com.gugaart.ui.data.SubStylePrefs
import com.gugaart.ui.data.currentLabel
import com.gugaart.ui.data.subScaleStep
import com.gugaart.ui.data.subScaleText
import com.gugaart.ui.data.subtitleFontChoices
import com.gugaart.ui.player.rememberChoiceFamily
import com.gugaart.ui.player.rememberStyleFonts
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.SubtitlePreviewSpec
import kotlinx.coroutines.launch

/** 预览条上排的那一行字（设计要求「示例字幕 Sample」：中文 + 拉丁字母都看得到）。 */
const val SUBTITLE_SAMPLE = "示例字幕 Sample"

/** 字幕样式的总说明（设置页那一组下面、电视那一组的说明）。 */
const val SUBTITLE_STYLE_NOTE = "只对文字字幕生效"

/**
 * 字幕样式的预览：一小块暗的画面 + 一行「示例字幕 Sample」，按 [p] 的字号、粗细、描边、阴影、位置排，字体用 [family]（null = 平台黑体）。
 * 和内核画出来的同一套颜色（白字黑边），只是示意大小，不是逐像素的复刻。
 */
@Composable
fun SubtitlePreview(
    p: SubStylePrefs,
    family: FontFamily?,
    modifier: Modifier = Modifier,
    height: Dp = SubtitlePreviewSpec.HEIGHT,
    text: TextUnit = SubtitlePreviewSpec.TEXT,
) {
    val size = text * p.scale.toFloat()
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    val base = TextStyle(
        fontFamily = family ?: FontFamily.SansSerif,
        fontWeight = if (p.bold) FontWeight.Bold else FontWeight.Normal,
        fontSize = size,
        textAlign = TextAlign.Center,
    )
    val outline = sizePx * SubtitlePreviewSpec.OUTLINE_EM * p.outline.factor.toFloat()
    val shadow = with(density) { (sizePx * SubtitlePreviewSpec.SHADOW_EM).toDp() }
    val margin = height * (p.position.marginY?.toFloat() ?: SubtitlePreviewSpec.DEFAULT_MARGIN)
    Box(
        modifier.fillMaxWidth().height(height).clipToBounds()
            .background(Brush.verticalGradient(listOf(SubtitlePreviewSpec.FILM_TOP, SubtitlePreviewSpec.FILM_BOTTOM)))
            .testTag("sub-style-preview"),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.padding(bottom = margin, start = 12.dp, end = 12.dp)) {
            if (p.shadow) BasicText(SUBTITLE_SAMPLE, Modifier.offset(shadow, shadow), style = base.copy(color = SubtitlePreviewSpec.SHADOW_COLOR), maxLines = 1, overflow = TextOverflow.Clip)
            if (outline > 0f) {
                BasicText(
                    SUBTITLE_SAMPLE,
                    style = base.copy(color = SubtitlePreviewSpec.OUTLINE_COLOR, drawStyle = Stroke(width = outline * 2, join = StrokeJoin.Round)),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            BasicText(SUBTITLE_SAMPLE, style = base.copy(color = SubtitlePreviewSpec.TEXT_COLOR), maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

/** 字体那一行下面的一句状态（下载中 / 失败 / 这台设备放不了文件）；没什么可说 → null。 */
fun subtitleFontStatus(font: StyleFont?, state: FontFileState?, canStore: Boolean): String? = when {
    font == null || font == StyleFont.System -> null
    !canStore -> "这台设备只能用系统字体"
    state is FontFileState.Busy -> "正在${if (font.downloadBytes > 0) "下载" else "解包"}「${font.label}」…"
    state is FontFileState.Failed -> "「${font.label}」${state.reason}，已用系统字体"
    else -> null
}

/**
 * 设置 › 字幕与音轨 的「字幕样式」一组（手机 / 电脑；电视在 `tv/system/TvSettingsModel.kt`，写同一批键）：
 * 预览条 + 字号步进 + 粗体 + 字体（选择器，带下载状态）+ 描边 + 阴影 + 位置 + 是否也改 ASS + 恢复默认。
 * 存在画报自己的 `gugaart.settings.v1` → `subStyle`；播放器里的「字号 / 字体」两行快调写的是同一个。
 */
@Composable
internal fun SubtitleStyleGroup(pick: String?) {
    val store = rememberGugaStore()
    val gp by store.prefs.collectAsState()
    val s = gp.subStyle
    fun set(f: (SubStylePrefs) -> SubStylePrefs) = store.update { it.copy(subStyle = f(it.subStyle)) }
    val fonts = rememberStyleFonts()
    val states by fonts.states.collectAsState()
    val imported by rememberImportedFonts().state.collectAsState()
    val scope = rememberCoroutineScope()
    val choices = subtitleFontChoices(s.font, imported, states, { id -> set { it.copy(font = id) } }, { f -> scope.launch { fonts.ensure(f) } })

    SettingGroup("字幕样式", "STYLE")
    SubtitlePreview(s, rememberChoiceFamily(s.font), Modifier.padding(top = 12.dp))
    BasicText(SUBTITLE_STYLE_NOTE, Modifier.padding(top = 8.dp, bottom = 2.dp), style = Guga.note(Guga.type.caption, Guga.colors.faint), maxLines = 3)
    SettingRow("字号", tag = "set-sub-scale") {
        Stepper(
            subScaleText(s.scale),
            { set { it.copy(scale = subScaleStep(it.scale, -1)) } },
            { set { it.copy(scale = subScaleStep(it.scale, 1)) } },
            canMinus = s.scale > SUB_SCALE_MIN + 0.001,
            canPlus = s.scale < SUB_SCALE_MAX - 0.001,
        )
    }
    SettingRow("粗体", tag = "set-sub-bold") { OnOff(s.bold, { on -> set { it.copy(bold = on) } }) }
    PickRow(
        "字体",
        choices.currentLabel(),
        choices.map { PickOption(it.key, it.label, it.note) },
        choices.filter { it.selected }.map { it.key },
        { key -> choices.firstOrNull { it.key == key }?.pick?.invoke() },
        note = subtitleFontStatus(s.styleFont, s.styleFont?.let { states[it] }, fonts.available),
        tag = "set-sub-font",
        startOpen = pick == "subFont",
    )
    SettingRow("描边", tag = "set-sub-outline") { Segmented(SubOutline.entries.map { it to it.label }, s.outline, { o -> set { it.copy(outline = o) } }) }
    SettingRow("阴影", tag = "set-sub-shadow") { OnOff(s.shadow, { on -> set { it.copy(shadow = on) } }) }
    SettingRow("位置", tag = "set-sub-position") { Segmented(SubPosition.entries.map { it to it.label }, s.position, { v -> set { it.copy(position = v) } }) }
    SettingRow("也改 ASS 特效字幕", tag = "set-sub-overrideAss") {
        OnOff(s.overrideAss, { on -> set { it.copy(overrideAss = on) } })
    }
    SettingRow("恢复默认的字幕样式", tag = "set-sub-reset") { ActionWord("恢复默认", { set { SubStylePrefs() } }, enabled = !s.isDefault) }
}
