package com.gugaart.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.components.SheetHost
import com.gugaart.ui.components.SlipHeader
import com.gugaart.ui.components.ValueChevron
import com.gugaart.ui.components.ValueText
import com.gugaart.ui.desktop.detail.PickOption
import com.gugaart.ui.desktop.detail.audioOption
import com.gugaart.ui.desktop.detail.subtitleOption
import com.gugaart.ui.player.chineseSubtitlesFirst
import com.gugugaga.core.MediaSourceInfo

/*
 * 手机详情页（电影 / 单集，iPhone 同一份代码）的「版本 / 音轨 / 字幕」（1.5.1）：一组收起来的行，每样一行写当前选的，
 * 点开是贴底的纸片（和「版本」同一种），点一项 = 选中 + 收起。电脑详情页（`DesktopMovie`）用同一份状态：
 * 音轨默认 = 片源标的默认那条（没标就第一条）；字幕第一项「关闭」、默认 = 中文优先排序后的第一条；
 * 开播只带用户在这一页**点过**的（没动过 = 不带，播放器按自己的「字幕默认」/ 音轨语言设置来）；
 * 音轨 / 字幕跟着选中的版本走（换版本 = 回到默认、算没动过）。
 */

/** 一个片源上的音轨 / 字幕选择（纯状态，单测直接 new；组合里按片源 [rememberTrackPicks]）。 */
@Stable
class TrackPicks(source: MediaSourceInfo?) {
    private val audioStreams = source?.mediaStreams.orEmpty().filter { it.type == "Audio" }

    val audios: List<PickOption> = audioStreams.map(::audioOption)

    /** 第一项是「关闭」（index = -1）。 */
    val subs: List<PickOption> = listOf(PickOption("关闭", "")) + chineseSubtitlesFirst(source?.mediaStreams.orEmpty().filter { it.type == "Subtitle" }).map(::subtitleOption)

    /** 片源里有没有字幕流（只有「关闭」= 没有）。 */
    val hasSubtitles: Boolean get() = subs.size > 1

    private var audioAt by mutableIntStateOf(audioStreams.indexOfFirst { it.isDefault == true }.coerceAtLeast(0))
    private var subAt by mutableIntStateOf(if (subs.size > 1) 1 else 0)

    /** 用户在这一页点过音轨 / 字幕（点过才带进开播路由）。 */
    var audioPicked: Boolean by mutableStateOf(false)
        private set
    var subPicked: Boolean by mutableStateOf(false)
        private set

    /** 现在选着的音轨（[audios] 的位置）。设 = 用户点了。 */
    var audio: Int
        get() = audioAt
        set(v) {
            audioAt = v
            audioPicked = true
        }

    /** 现在选着的字幕（[subs] 的位置，0 = 关闭）。设 = 用户点了。 */
    var sub: Int
        get() = subAt
        set(v) {
            subAt = v
            subPicked = true
        }

    /** 开播时带的音轨（Emby 流编号）：用户没点过 = 不带。 */
    val audioIndex: Int? get() = if (audioPicked) audios.getOrNull(audio)?.index else null

    /**
     * 开播时带的字幕：-1 = 关；用户没点过 = 不带。片源里一条字幕流都没有 = 不带
     * （流表空的源，内封字幕交给播放器自己认，不能被这里关掉）。
     */
    val subtitleIndex: Int? get() = if (subPicked && hasSubtitles) subs.getOrNull(sub)?.index else null
}

/** 这个片源的选择：换了片源（换版本）就是新的一份，回到默认。 */
@Composable
fun rememberTrackPicks(source: MediaSourceInfo?): TrackPicks = remember(source) { TrackPicks(source) }

/** 验收运行器的测试缝（iOS `pickers151`）：这一页的选择和纸片开关交给外面；应用里永远是 null = 不交。 */
class DetailPickerDriver(val picks: TrackPicks, val sheet: (String?) -> Unit)

val LocalDetailPickerSink = androidx.compose.runtime.staticCompositionLocalOf<((DetailPickerDriver) -> Unit)?> { null }

/** 音轨 / 字幕那一行：左 = 名字，右 = 现在选的；不止一项才能点开（带 ›）。没有选项 = 不出现。 */
@Composable
fun TrackRow(label: String, options: List<PickOption>, selected: Int, tag: String, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    if (options.isEmpty()) return
    val cur = options[selected.coerceIn(0, options.lastIndex)]
    val many = options.size > 1
    SettingRow(label, modifier, tag = tag, onClick = if (many) onOpen else null) { if (many) ValueChevron(cur.name) else ValueText(cur.name) }
}

/** 版本 / 音轨 / 字幕一组：上面一条线，每样一行。哪一样都没有 = 整组不出现。[onOpen] 参数 = "ver" / "audio" / "sub"。 */
@Composable
fun TrackPickerGroup(sources: SourcesState, picks: TrackPicks, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val ver = sources.sources.size > 1
    if (!ver && picks.audios.isEmpty() && !picks.hasSubtitles) return
    Column(modifier.testTag("phone-pickers")) {
        Rule()
        if (ver) VersionRow(sources, { onOpen("ver") })
        TrackRow("音轨", picks.audios, picks.audio, "phone-audio-row") { onOpen("audio") }
        if (picks.hasSubtitles) TrackRow("字幕", picks.subs, picks.sub, "phone-sub-row") { onOpen("sub") }
    }
}

/** 开着的那一张纸片（[open] = "ver" / "audio" / "sub"；null = 都收着）。 */
@Composable
fun TrackPickerSheets(open: String?, sources: SourcesState, picks: TrackPicks, onDismiss: () -> Unit) {
    when (open) {
        "ver" -> if (sources.sources.size > 1) VersionSheet(sources, onDismiss)
        "audio" -> if (picks.audios.size > 1) TrackSheet("音轨", "AUDIO", picks.audios, picks.audio, "${picks.audios.size} 条", "audio", { picks.audio = it }, onDismiss)
        "sub" -> if (picks.subs.size > 1) TrackSheet("字幕", "SUBTITLES", picks.subs, picks.sub, "${picks.subs.size - 1} 条", "sub", { picks.sub = it }, onDismiss)
    }
}

/** 贴底的纸片：一项一行（和「版本」纸片同一种），点一项 = 选中 + 收起。 */
@Composable
fun TrackSheet(title: String, english: String, options: List<PickOption>, selected: Int, trailing: String, tag: String, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    SheetHost(onDismiss, tag = "$tag-sheet", bottom = true) {
        SlipHeader(title, english, trailing = trailing)
        Column(Modifier.padding(top = 4.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            options.forEachIndexed { i, o ->
                SheetOptionRow(o.name, o.note, i == selected, "$tag-option", onClick = {
                    onPick(i)
                    onDismiss()
                })
            }
        }
    }
}
