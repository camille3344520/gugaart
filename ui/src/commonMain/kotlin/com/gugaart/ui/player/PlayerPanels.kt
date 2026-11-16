package com.gugaart.ui.player

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.artOf
import com.gugaart.ui.screens.detail.specLine
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.AutoNextState
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.player.PlayerSlots
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.danmaku.settings.DanmakuFont
import com.gugugaga.danmaku.settings.DanmakuWeight
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/** 倍速的档位。 */
val RATES = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0)

/** 画质（码率上限）的档位：0 = 原画（不限）。超过上限时由服务器转码——这是上游 `setMaxBitrate` 的行为。 */
val BITRATES = listOf(0.0, 20.0, 10.0, 6.0, 3.0)

/** 步进一格并夹在范围里（保留两位小数，免得 0.1 加十次不是 1）。 */
fun stepped(v: Double, delta: Double, min: Double, max: Double): Double = (((v + delta) * 100).roundToInt() / 100.0).coerceIn(min, max)

/**
 * 页边面板的内容。每个面板的条目都取自上游已有的功能（`PlayController` 的命令、上游弹幕面板的设置键），不增不减；这里只管排版。
 */
@Composable
fun PanelBody(panel: PlayerPanel, play: PlaySession, snap: PlayerSnapshot, danmaku: PlayerDanmaku, slots: PlayerSlots, art: Art, positionMs: Double, close: () -> Unit) {
    val g = LocalGraph.current
    val controller = play.controller
    val colors = Guga.colors
    when (panel) {
        PlayerPanel.Eps -> EpisodePanel(snap)
        PlayerPanel.Sub -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("panel-sub")) {
            PanelHeader(panel)
            PanelOption("关", snap.subtitleIndex == null, { play.chooseSubtitle(null) })
            // 中文字幕排前面（简体 → 中文 → 繁体 → 其他）
            for (t in orderedSubtitles(snap)) {
                // 没名字的字幕上游已经给了「未命名」这类标签；同名的靠徽标区分
                PanelOption(t.label, t.index == snap.subtitleIndex, { play.chooseSubtitle(t.index) }, note = t.badge)
            }
            PanelStepper("延迟", "${snap.subtitleDelayMs / 1000}.${(kotlin.math.abs(snap.subtitleDelayMs) % 1000) / 100} 秒", { controller.setSubtitleDelay(snap.subtitleDelayMs - 100) }, { controller.setSubtitleDelay(snap.subtitleDelayMs + 100) })
        }
        PlayerPanel.Audio -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("panel-audio")) {
            PanelHeader(panel)
            if (snap.audio.isEmpty()) BasicText("这个版本只有一条音轨。", Modifier.padding(top = 10.dp), style = Guga.song(Guga.player.body, color = colors.dim))
            for (t in snap.audio) PanelOption(t.label, t.selected, { t.index?.let(controller::selectAudio) }, note = t.badge)
        }
        PlayerPanel.Speed -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("panel-speed")) {
            PanelHeader(panel)
            for (r in RATES) PanelOption(rateLabel(r), kotlin.math.abs(snap.rate - r) < 0.01, { controller.setRate(r) }, note = "正常".takeIf { r == 1.0 })
        }
        PlayerPanel.Quality -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("panel-quality")) {
            PanelHeader(panel)
            for (b in BITRATES) PanelOption(if (b == 0.0) "原画" else "${b.toInt()} Mbps", snap.maxBitrateMbps == b, { controller.setMaxBitrate(b) }, note = if (b == 0.0) specLine(snap.resolved?.mediaSource)?.substringBefore(" · ") else "转码")
            // 版本：只有一个时不出现
            if (snap.versions.size > 1) {
                PanelSection("版本")
                for (v in snap.versions) PanelOption(v.name?.takeIf { it.isNotBlank() } ?: "未命名", v.id == snap.sourceId, { v.id?.let(controller::setSource) }, note = specLine(v)?.substringBefore(" · "))
            }
        }
        PlayerPanel.Core -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("panel-core")) {
            PanelHeader(panel)
            // 自动 · EXO · VLC · MPV（只列这台设备上真有的）；选了 = 存成首选引擎，在当前位置用它接着放
            val pref = currentEnginePref(g)
            val running = engineShort(snap.engineId)
            for (o in remember(play) { coreOptionsFor(play, g) }) {
                PanelOption(o.label, o.pref == pref, { PlayerRestart.switchEngine(play, g, o.pref) }, note = if (o.pref == com.gugugaga.app.player.EnginePref.Auto) "现在 $running" else o.note)
            }
        }
        PlayerPanel.Dm -> {
            val settings by danmaku.settingsState.collectAsState()
            val s = remember(settings) { danmaku.prefsNow().render }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("panel-dm")) {
                PanelHeader(panel)
                PanelOption("开", s.enabled, { danmaku.updateRender("enabled" to JsonPrimitive(true)) })
                PanelOption("关", !s.enabled, { danmaku.updateRender("enabled" to JsonPrimitive(false)) })
                PanelStepper("不透明", pct(s.opacity), { danmaku.updateRender("opacity" to JsonPrimitive(stepped(s.opacity, -0.05, 0.1, 1.0))) }, { danmaku.updateRender("opacity" to JsonPrimitive(stepped(s.opacity, 0.05, 0.1, 1.0))) })
                PanelStepper("字号", pct(s.fontScale), { danmaku.updateRender("fontScale" to JsonPrimitive(stepped(s.fontScale, -0.1, 0.6, 2.5))) }, { danmaku.updateRender("fontScale" to JsonPrimitive(stepped(s.fontScale, 0.1, 0.6, 2.5))) })
                PanelStepper("区域", pct(s.area), { danmaku.updateRender("area" to JsonPrimitive(stepped(s.area, -0.1, 0.05, 1.0))) }, { danmaku.updateRender("area" to JsonPrimitive(stepped(s.area, 0.1, 0.05, 1.0))) })
                PanelStepper("速度", rateLabel(s.speed), { danmaku.updateRender("speed" to JsonPrimitive(stepped(s.speed, -0.25, 0.5, 2.0))) }, { danmaku.updateRender("speed" to JsonPrimitive(stepped(s.speed, 0.25, 0.5, 2.0))) })
                // 弹幕字体：上游 :danmaku 内置的四款（系统 / 思源黑体 / 霞鹜文楷 / 得意黑），写的是上游同一个键 danmaku.render.font，
                // 渲染照旧走 :danmaku。画报的宋体只用于界面文字，不进这张单子（decisions Q3）。
                PanelSection("字体")
                // 导入的字体排在最上面（设置 › 弹幕 里导入 / 删除；这里能选、能直接导入）。选中导入的那款时内置的四款都不亮
                val fonts = com.gugaart.ui.screens.settings.rememberImportedFonts()
                val imported by fonts.state.collectAsState()
                val importHost = com.gugaart.ui.data.LocalFontImport.current
                val importScope = androidx.compose.runtime.rememberCoroutineScope()
                if (importHost != null) PanelLink("导入字体…") { importScope.launch { importHost.pick()?.let { fonts.import(it) } } }
                for (f in imported.fonts) PanelOption(f.name, imported.selected == f.id, { fonts.select(f.id) }, note = "导入")
                for (f in DanmakuFont.entries) PanelOption(f.label, imported.selected == null && s.font == f, {
                    fonts.select(null)
                    danmaku.updateRender("font" to JsonPrimitive(f.id))
                })
                PanelSection("字重")
                for (w in DanmakuWeight.entries) PanelOption(w.label, s.weight == w, { danmaku.updateRender("weight" to JsonPrimitive(w.id)) })
                PanelSection("弹幕源")
                // 匹配状态（上游的徽标文字：「赘婿 (2021) · 第5集 · 1,234 条」/ 没配置 / 没匹配到）；点它 = 左边滑出弹幕来源（候选 + 手动搜索）
                PanelLink(DanmakuSeasonGuard.status(snap.itemId) ?: slots.danmakuStatus?.invoke() ?: "还没有匹配") {
                    close()
                    DanmakuPickerBoard.open = true
                }
                PanelLink("不对？换一个 / 手动搜索") {
                    close()
                    DanmakuPickerBoard.open = true
                }
                if (danmaku.query.hasPeaks) PanelLink("跳到下一个高能点") { danmaku.nextPeak(positionMs)?.let { controller.seekTo(it) } }
            }
        }
        PlayerPanel.Cast -> com.gugaart.ui.cast.CastPanelBody(play)
        PlayerPanel.Next -> Column(Modifier.fillMaxSize().testTag("panel-next")) {
            val next = snap.next
            PanelHeader(panel, kicker = "NEXT · 片尾")
            if (next != null) {
                val client = g.session.session.collectAsState().value?.client
                val t = Guga.player
                Thumb(client?.artOf(next)?.thumb, Rules.episodeNo(next.indexNumber ?: 0), 160.dp * t.k)
                BasicText("第 ${next.indexNumber ?: ""} 集", Modifier.padding(top = 8.dp), style = Guga.note(t.option, colors.spot), maxLines = 1)
                BasicText(next.name, style = Guga.song(t.option * 1.08f, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Rules.ticksToSec(next.runTimeTicks)?.let { BasicText(Rules.minutes(it), Modifier.padding(top = 2.dp), style = Guga.song(t.note, color = colors.dim), maxLines = 1) }
                if (snap.autoNext == AutoNextState.Counting) BasicText("片尾放完自动播放", Modifier.padding(top = 8.dp), style = Guga.song(t.note, color = colors.dim), maxLines = 1)
                PrimaryButton("立即播放", { controller.playNext() }, Modifier.padding(top = 10.dp).fillMaxWidth())
            }
        }
    }
}
