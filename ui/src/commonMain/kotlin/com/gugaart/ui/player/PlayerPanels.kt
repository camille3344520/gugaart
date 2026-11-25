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
            // 字幕智能匹配（1.4.1 U2）：「找到完全对得上的字幕：… · 用上这条」/「已自动加载：… · 撤销」，没有就不占地方
            com.gugaart.ui.subtitle.AutoBestPanelLine(play)
            PanelOption("关", snap.subtitleIndex == null, { play.chooseSubtitle(null) })
            // 中文字幕排前面（简体 → 中文 → 繁体 → 其他）
            for (t in orderedSubtitles(snap)) {
                // 没名字的字幕上游已经给了「未命名」这类标签；同名的靠徽标区分
                PanelOption(t.label, t.index == snap.subtitleIndex, { play.chooseSubtitle(t.index) }, note = t.badge)
            }
            PanelStepper("延迟", "${snap.subtitleDelayMs / 1000}.${(kotlin.math.abs(snap.subtitleDelayMs) % 1000) / 100} 秒", { controller.setSubtitleDelay(snap.subtitleDelayMs - 100) }, { controller.setSubtitleDelay(snap.subtitleDelayMs + 100) })
            // 字幕样式的两行快调（1.4.1）：字号、字体；和 设置 › 字幕与音轨 › 字幕样式 同一个键，引擎即时生效（RememberSubtitleStyleApply）
            SubtitleQuickRows()
            // 字幕搜索（1.4.0，docs/subtitle-search.md）：面板收起，左边出搜索栏（画面在右边照常放）
            if (snap.item != null) PanelLink("搜索字幕…") {
                close()
                com.gugaart.ui.subtitle.SubtitleSearchBoard.openInPlayer(play, g)
            }
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
            // 版本 = 「源」（1.5.0：本台的版本 + 没被合并的重复片），只有一个时不出现。选中 = 正在放的那一个。
            // 同一条目的另一个版本：上游 setSource（原来的做法）；重复片（另一个条目）：在当前位置重开那一个条目。点了都记住（下次默认它）
            // 1.5.0 B3：别台的源（OtherServer）也列在这里；放着别台的源时，这一栏认的仍是本台的条目（路由里那一个），
            // 「正在放」按服务器 + 条目 + 片源认（几台服务器上的条目 id 可能一样）
            val onRemote = play.remote
            val sources = com.gugaart.ui.screens.detail.rememberSourcesState(
                onRemote?.homeItemId ?: snap.itemId, snap.item?.type, snap.item?.providerIds.orEmpty(),
                if (onRemote != null) emptyList() else snap.versions, snap.item?.name.orEmpty(),
            )
            if (sources.sources.size > 1) {
                // 有别的服务器参与时这一栏叫「源」，每行前面是服务器名和小圆点；最后是别台的进展（B2-UI）
                PanelSection(sources.sectionTitle)
                val t = Guga.player
                // 手机横屏的页边只有 170：小签挪到名字下面，「服务器名 · 版本名」先读得全
                val narrow = Guga.dimens.uiMode == com.gugugaga.ds.theme.UiMode.Phone
                for (s in sources.sources) {
                    val r = sources.rowText(s)
                    val playing = s.mediaSource.id == snap.sourceId && if (s.kind == com.gugaart.ui.data.SourceKind.OtherServer) {
                        onRemote?.serverId == s.serverId && onRemote.itemId == s.itemId
                    } else {
                        onRemote == null && s.itemId == snap.itemId
                    }
                    PanelOption(r.title, playing, {
                        sources.pick(s)
                        // 别台的源 / 从别台切回本台：上游 switchServer（同一个内核、当前位置接着放、进度只报正在播的那台）
                        if (RemotePlay.choose(play, g, s)) Unit
                        else if (s.itemId == snap.itemId) controller.setSource(s.mediaSource.id)
                        else PlayerRestart.switchItem(play, g, s.itemId, s.mediaSource.id)
                    }, sub = r.spec, tags = { com.gugaart.ui.screens.detail.SourceTags(r, t.note * 0.92f, 1.dp, gap = 6.dp * t.k, leadingGap = !narrow) },
                        lead = if (r.server == null) null else ({ com.gugaart.ui.screens.detail.SourceDot(r, 5.dp * t.k, gap = 6.dp * t.k) }),
                        tagsBelow = narrow)
                }
                com.gugaart.ui.screens.detail.SourceProgress(sources, t.note, 5.dp * t.k, Modifier.padding(top = 7.dp * t.k, start = 15.dp * t.k), lineGap = 3.dp * t.k)
            }
            // 着色器增强（Anime4K / FSR）：只有 mpv 系内核 + 能写文件的平台才画；选择记在画报自己的键里，点完立即应用（RememberShaderApply）
            val shaderEngine by controller.engineFlow.collectAsState()
            val shaderStore = com.gugaart.ui.screens.settings.rememberGugaStore()
            val shaderPrefs by shaderStore.prefs.collectAsState()
            val shaderHost = com.gugaart.ui.data.LocalFontImport.current
            if (shaderEntryVisible(shaderEngine?.shaders != null, shaderHost != null)) {
                PanelSection("着色器增强")
                for (s in ShaderPreset.entries) PanelOption(s.label, shaderPrefs.shader == s, { shaderStore.update { it.copy(shader = s) } }, note = s.note)
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
                // 弹幕字体（1.4.1：和字幕共用同一套）：导入的排最上面，再系统 + 思源黑体 / 思源宋体 / 霞鹜文楷 / 得意黑 / 站酷快乐体。
                // 上游有的写上游的键 danmaku.render.font；上游没有的（思源宋体、站酷快乐体、导入的）经上游的缝 LocalDanmakuFontOverride 画（StyleApply.kt）
                PanelSection("字体")
                val fonts = com.gugaart.ui.screens.settings.rememberImportedFonts()
                val importHost = com.gugaart.ui.data.LocalFontImport.current
                val importScope = androidx.compose.runtime.rememberCoroutineScope()
                if (importHost?.canPick == true) PanelLink("导入字体…") { importScope.launch { importHost.pick()?.let { fonts.import(it) } } }
                for (c in rememberDanmakuFontChoices(danmaku, s.font)) PanelOption(c.label, c.selected, c.pick, note = c.note)
                PanelSection("粗细")
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

/** 字幕面板里的两行快调：字号（50 % – 200 %）、字体（‹ › 轮着换；要下载的那款换到它就开始下）。 */
@Composable
fun SubtitleQuickRows() {
    val store = com.gugaart.ui.screens.settings.rememberGugaStore()
    val prefs by store.prefs.collectAsState()
    val s = prefs.subStyle
    val choices = rememberSubtitleFontChoices()
    PanelStepper(
        "字号", com.gugaart.ui.data.subScaleText(s.scale),
        { store.update { it.copy(subStyle = it.subStyle.copy(scale = com.gugaart.ui.data.subScaleStep(it.subStyle.scale, -1))) } },
        { store.update { it.copy(subStyle = it.subStyle.copy(scale = com.gugaart.ui.data.subScaleStep(it.subStyle.scale, 1))) } },
    )
    val at = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
    PanelStepper(
        "字体", choices.getOrNull(at)?.let { c -> listOfNotNull(c.label, c.note?.takeIf { n -> n != "导入" }).joinToString(" · ") }.orEmpty(),
        { choices.getOrNull((at - 1 + choices.size) % choices.size)?.pick?.invoke() },
        { choices.getOrNull((at + 1) % choices.size)?.pick?.invoke() },
        cycle = true,
    )
}

/** 字幕的字体单子（[com.gugaart.ui.data.subtitleFontChoices]），接好 store / 字体文件仓。 */
@Composable
fun rememberSubtitleFontChoices(): List<com.gugaart.ui.data.FontChoice> {
    val store = com.gugaart.ui.screens.settings.rememberGugaStore()
    val prefs by store.prefs.collectAsState()
    val fonts = rememberStyleFonts()
    val states by fonts.states.collectAsState()
    val imported by com.gugaart.ui.screens.settings.rememberImportedFonts().state.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return com.gugaart.ui.data.subtitleFontChoices(
        prefs.subStyle.font, imported, states,
        { id -> store.update { it.copy(subStyle = it.subStyle.copy(font = id)) } },
        { f -> scope.launch { fonts.ensure(f) } },
    )
}

/** 弹幕的字体单子（[com.gugaart.ui.data.danmakuFontChoices]），接好上游的弹幕设置、画报的 store、导入字体仓、字体文件仓。 */
@Composable
fun rememberDanmakuFontChoices(danmaku: PlayerDanmaku, upstream: com.gugugaga.danmaku.settings.DanmakuFont): List<com.gugaart.ui.data.FontChoice> {
    val store = com.gugaart.ui.screens.settings.rememberGugaStore()
    val prefs by store.prefs.collectAsState()
    val importedStore = com.gugaart.ui.screens.settings.rememberImportedFonts()
    val imported by importedStore.state.collectAsState()
    val fonts = rememberStyleFonts()
    val states by fonts.states.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return com.gugaart.ui.data.danmakuFontChoices(
        upstream, prefs.danmakuFont, imported, states,
        setUpstream = { danmaku.updateRender("font" to JsonPrimitive(it.id)) },
        setExtra = { id -> store.update { it.copy(danmakuFont = id) } },
        selectImported = importedStore::select,
        prefetch = { f -> scope.launch { fonts.ensure(f) } },
    )
}
