package com.gugaart.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import com.gugaart.ui.update.updater
import com.gugaart.ui.components.ConfirmSheet
import com.gugaart.ui.screens.LocalPageState
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.icons.GuguIcon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.components.ActionWord
import com.gugaart.ui.components.CheckWord
import com.gugaart.ui.components.InfoRow
import com.gugaart.ui.components.InputLine
import com.gugaart.ui.components.MoveKeys
import com.gugaart.ui.components.OnOff
import com.gugaart.ui.components.PickOption
import com.gugaart.ui.components.PickRow
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.Segmented
import com.gugaart.ui.components.SettingGroup
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.components.Stepper
import com.gugaart.ui.components.ValueChevron
import com.gugaart.ui.components.ValueText
import com.gugaart.ui.components.progressLine
import com.gugaart.ui.data.GUGA_SUBTITLE_LANGS
import com.gugaart.ui.data.SubtitleDefault
import com.gugaart.ui.data.effectiveSubtitleLangs
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.danmaku.DanmakuProbeVisual
import com.gugugaga.app.danmaku.danmakuProbeChipLabel
import com.gugugaga.app.danmaku.danmakuProbeVerdict
import com.gugugaga.danmaku.match.DanmakuCacheProbe
import com.gugugaga.app.nav.Route
import com.gugugaga.app.net.detectSystemProxy
import com.gugugaga.app.player.STRM_PROXY_MODE_KEY
import com.gugugaga.app.player.STRM_SETTINGS_SECTION
import com.gugugaga.app.player.StrmSettings
import com.gugugaga.app.search.SearchHistoryStore
import com.gugugaga.app.settings.AUDIO_LANGS
import com.gugugaga.app.settings.BITRATE_CHOICES_MBPS
import com.gugugaga.app.settings.DANMAKU_SOURCE_NAMES
import com.gugugaga.app.settings.DEFAULT_DANMAKU_RENDER_JSON
import com.gugugaga.app.settings.DEFAULT_DANMAKU_SOURCES
import com.gugugaga.app.settings.GLASS_STRENGTH_MAX
import com.gugugaga.app.settings.GLASS_STRENGTH_MIN
import com.gugugaga.app.settings.LangOption
import com.gugugaga.app.settings.PerfPref
import com.gugugaga.app.settings.RATE_CHOICES
import com.gugugaga.app.settings.SEEK_STEP_CHOICES_SEC
import com.gugugaga.app.settings.bitrateLabel
import com.gugugaga.app.settings.codesFromKeys
import com.gugugaga.app.settings.danmakuPrefs
import com.gugugaga.app.settings.describePriority
import com.gugugaga.app.settings.engineChoices
import com.gugugaga.app.settings.extraCodes
import com.gugugaga.app.settings.formatBytes
import com.gugugaga.app.settings.formatOffset
import com.gugugaga.app.settings.jsonStrings
import com.gugugaga.app.settings.keysFromCodes
import com.gugugaga.app.settings.moved
import com.gugugaga.app.settings.parseBlockWords
import com.gugugaga.app.settings.playbackPrefs
import com.gugugaga.app.settings.uiSettings
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.currentTimeMillis
import com.gugugaga.danmaku.settings.DanmakuWeight
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor

/*
 * 设置分区的行。每一行读写的都是上游已有的设置键，或者画报自己的 `gugaart.settings.v1`。
 */

/** 数字按 JS 的写法存（整数不带小数点），和上游 / 网页版写出来的一样。 */
private fun num(v: Double): JsonPrimitive = if (v == floor(v) && kotlin.math.abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

/* ------------------------------------------------------------------ 外观与界面 */

@Composable
internal fun AppearanceSection(pick: String?) {
    val g = LocalGraph.current
    val store = rememberGugaStore()
    val prefs by store.prefs.collectAsState()
    val settings by g.settings.state.collectAsState()
    val ui = settings.uiSettings

    SettingGroup("纸面", "PAPER")
    SettingRow("主题色跟随海报", tag = "set-spot") {
        OnOff(prefs.spotColorFollowsCover, { on -> store.update { it.copy(spotColorFollowsCover = on) } })
    }

    SettingGroup("玻璃", "GLASS")
    val strength = ui.glassStrength
    SettingRow("玻璃强度", tag = "set-glass") {
        Stepper(
            percentText(strength),
            { g.settings.patch("ui", "glass" to num(stepRange(strength, 0.1, GLASS_STRENGTH_MIN, GLASS_STRENGTH_MAX, -1))) },
            { g.settings.patch("ui", "glass" to num(stepRange(strength, 0.1, GLASS_STRENGTH_MIN, GLASS_STRENGTH_MAX, 1))) },
            canMinus = strength > GLASS_STRENGTH_MIN + 0.001,
            canPlus = strength < GLASS_STRENGTH_MAX - 0.001,
        )
    }
    val perfLabels = listOf(PerfPref.Auto to "自动", PerfPref.High to "高", PerfPref.Mid to "中", PerfPref.Low to "低")
    PickRow(
        "材质",
        perfLabels.first { it.first == ui.perf }.second,
        perfLabels.map { (p, l) -> PickOption(p, l) },
        listOf(ui.perf),
        { g.settings.patch("ui", "perf" to JsonPrimitive(it.id)) },
        tag = "set-perf",
        startOpen = pick == "perf",
    )

    SettingGroup("放映员", "THE PROJECTIONIST")
    SettingRow("咕咕嘎嘎出现在界面上", tag = "set-mascot") {
        OnOff(prefs.showMascot, { on -> store.update { withMascot(it, on) } })
    }
    SettingRow("放映手记", tag = "set-notes") {
        Segmented(NotesChoice.entries.map { it to it.label }, notesChoiceOf(prefs), { c -> store.update { withNotes(it, c) } }, enabled = prefs.showMascot)
    }

    SettingGroup("首页", "HOME")
    SettingRow("封面自动轮播", tag = "set-coverAutoFlip") {
        OnOff(prefs.coverAutoFlip, { on -> store.update { it.copy(coverAutoFlip = on) } })
    }
}

/* ------------------------------------------------------------------ 03 播放 */

/** 首选引擎下面那句回退链说明，按平台写实际的链（上游 EngineSelect）：安卓 ExoPlayer→VLC→mpv→转码；
 *  Mac / iPhone 只有进程内 MPVKit（libmpv），放不了出出错页，不自动换 VLC / 不自动转码；Windows mpv→VLC→转码。纯函数，有单测。 */
fun engineChainNote(android: Boolean, mac: Boolean, ios: Boolean = false): String = when {
    // iPhone 用手机版式（android 那一位是「手机 / 电视版式」），内核却和 Mac 一样（上游 U-ios-9）：先看它
    ios -> "只有 MPVKit，不自动转码"
    android -> "ExoPlayer › VLC › mpv › 服务器转码"
    mac -> "只有 MPVKit，不自动转码"
    else -> "mpv › VLC › 服务器转码"
}

/**
 * 设置 › 播放「首选引擎」的选项：macOS / iOS 用上游按平台的那一份（U-mac-5：自动（MPV）/ mpv / 外部播放器；U-ios-9：自动（MPV）/ mpv，
 * iOS 没有外部播放器），别的端照旧按「是不是安卓设备」。纯函数，有单测。
 */
fun settingsEngineChoices(android: Boolean, mac: Boolean, ios: Boolean = false): List<com.gugugaga.app.settings.EngineChoice> = when {
    // = 上游 engineChoices(PlatformKind.Ios)（U-ios-9，只在 mac/ios-targets 上）；这里由 Mac 那一份去掉「外部播放器」得到，
    // 这样 :ui 挂哪一份上游都编得过
    ios -> engineChoices(com.gugugaga.app.player.PlatformKind.MacOS).filter { it.id != "external" }
    mac -> engineChoices(com.gugugaga.app.player.PlatformKind.MacOS)
    else -> engineChoices(android)
}

@Composable
internal fun PlaybackSection(pick: String?) {
    val g = LocalGraph.current
    val settings by g.settings.state.collectAsState()
    val p = settings.playbackPrefs
    fun put(key: String, v: JsonElement) = g.settings.patch("playback", key to v)
    val android = g.deps.uiMode != UiMode.Desktop
    // macOS 只有一个内嵌内核（上游 U-mac-5 的 engineChoices(MacOS)：自动（MPV）/ mpv / 外部播放器，没有 VLC、没有「双内核」）
    val mac = !android && com.gugaart.ui.desktop.LocalDesktopHost.current.platformName == "macOS"
    // iPhone：手机版式（android = true），平台却是 iOS（壳的 platformLabel「iOS 27.2 · 机型」），内核和 Mac 一样只有 mpv
    val ios = g.deps.platformLabel.startsWith("iOS ")
    val engines = remember(android, mac, ios) { settingsEngineChoices(android, mac, ios) }

    SettingGroup("内核", "ENGINE") // P2-3：不再用「引擎」
    PickRow(
        "播放内核",
        engines.firstOrNull { it.id == p.engine }?.label?.substringBefore('（'),
        engines.map { PickOption(it.id, it.label.substringBefore('（'), it.label.substringAfter('（', "").removeSuffix("）").takeIf { n -> n.isNotEmpty() }) },
        listOf(p.engine),
        { put("engine", JsonPrimitive(it)) },
        note = engineChainNote(android, mac, ios),
        tag = "set-engine",
        startOpen = pick == "engine",
    )
    PickRow(
        "画质上限",
        if (p.maxBitrateMbps == 0) "不限" else "${p.maxBitrateMbps} Mbps",
        BITRATE_CHOICES_MBPS.map { PickOption(it, bitrateLabel(it)) },
        listOf(p.maxBitrateMbps),
        { put("maxBitrateMbps", JsonPrimitive(it)) },
        note = "超过时转码",
        tag = "set-bitrate",
        startOpen = pick == "bitrate",
    )
    val gugaStore = rememberGugaStore()
    val gp by gugaStore.prefs.collectAsState()
    PickRow(
        "默认着色器",
        gp.defaultShader.label,
        com.gugaart.ui.player.ShaderPreset.entries.map { PickOption(it.id, it.label, it.note) },
        listOf(gp.defaultShader.id),
        { id -> gugaStore.update { it.copy(defaultShader = com.gugaart.ui.player.ShaderPreset.of(id)) } },
        tag = "set-defaultShader",
        startOpen = pick == "shader",
    )

    SettingGroup("播放行为", "BEHAVIOUR")
    SettingRow("自动播放下一集", tag = "set-autoPlayNext") { OnOff(p.autoPlayNext, { put("autoPlayNext", JsonPrimitive(it)) }) }
    // P1-3：进了片尾标记倒数 10 秒接下一集（画报自己的键 creditsAutoNext）
    SettingRow("片尾自动播下一集", tag = "set-creditsAutoNext") { OnOff(gp.creditsAutoNext, { on -> gugaStore.update { it.copy(creditsAutoNext = on) } }) }
    SettingRow("跳过片头 / 片尾", tag = "set-skipMarkers") {
        Segmented(listOf("off" to "关闭", "button" to "显示按钮", "auto" to "自动跳过"), p.skipMarkers, { put("skipMarkers", JsonPrimitive(it)) })
    }
    SettingRow("快进 / 快退步长", tag = "set-seekStep") {
        Stepper(
            "${p.seekStepSec} 秒",
            { put("seekStepSec", JsonPrimitive(stepInt(SEEK_STEP_CHOICES_SEC, p.seekStepSec, -1))) },
            { put("seekStepSec", JsonPrimitive(stepInt(SEEK_STEP_CHOICES_SEC, p.seekStepSec, 1))) },
            canMinus = p.seekStepSec > SEEK_STEP_CHOICES_SEC.first(),
            canPlus = p.seekStepSec < SEEK_STEP_CHOICES_SEC.last(),
        )
    }
    SettingRow("默认倍速", tag = "set-defaultRate") {
        Stepper(
            scaleText(p.defaultRate),
            { put("defaultRate", num(stepDouble(RATE_CHOICES, p.defaultRate, -1))) },
            { put("defaultRate", num(stepDouble(RATE_CHOICES, p.defaultRate, 1))) },
            canMinus = p.defaultRate > RATE_CHOICES.first(),
            canPlus = p.defaultRate < RATE_CHOICES.last(),
        )
    }

    // 1.5.0：多台服务器（从其它服务器找片源 + 参与的服务器）
    OtherServersGroup(startOpen = pick == "servers")
}

/* ------------------------------------------------------------------ 04 strm 直链 */

@Composable
internal fun StrmSection() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val settings by g.settings.state.collectAsState()
    val a = StrmSettings.of(settings)
    fun put(key: String, v: JsonElement) = g.settings.patch(STRM_SETTINGS_SECTION, key to v)
    val proxy by produceState<String?>(null) {
        value = withContext(Dispatchers.Default) { detectSystemProxy() }?.let { "系统代理 ${it.display} · ${it.source}" } ?: "未检测到系统代理"
    }

    SettingGroup("直链", "STRM")
    SettingRow("优先直连 strm 目标", note = "跳过 Emby 中转", tag = "set-strm-preferStrm") {
        OnOff(a.preferStrmTarget, { put("preferStrmTarget", JsonPrimitive(it)) })
    }
    SettingRow("线路", tag = "set-strm-proxyMode") {
        Segmented(listOf("auto" to "自动", "direct" to "始终直连", "proxy" to "始终经代理"), a.strmProxyMode, { put(STRM_PROXY_MODE_KEY, JsonPrimitive(it)) })
    }
    proxy?.let { BasicText(it, Modifier.padding(top = 14.dp).testTag("strm-sysproxy"), style = Guga.note(Guga.type.caption, colors.faint), maxLines = 2) }

    SettingGroup("请求头", "HEADERS")
    SettingRow("User-Agent", note = "留空 = 内核默认")
    InputLine(a.cdnUserAgent, { put("cdnUserAgent", JsonPrimitive(it)) }, "未设置", tag = "set-strm-ua")
    SettingRow("Referer", note = "留空 = 不带")
    InputLine(a.cdnReferer, { put("cdnReferer", JsonPrimitive(it)) }, "未设置", tag = "set-strm-referer")
}

/* ------------------------------------------------------------------ 05 字幕与音轨 */

/** 点一种语言：在里面 → 拿掉；不在 → 排到最后。上游不认识的语言代码（手改过的）原样留在末尾。 */
private fun toggledLangs(options: List<LangOption>, codes: List<String>, key: String): List<String> {
    val keys = keysFromCodes(options, codes)
    val next = if (key in keys) keys - key else keys + key
    return codesFromKeys(options, next, extraCodes(options, codes))
}

@Composable
internal fun SubsSection(pick: String?) {
    val g = LocalGraph.current
    val settings by g.settings.state.collectAsState()
    val p = settings.playbackPrefs
    fun put(key: String, v: JsonElement) = g.settings.patch("playback", key to v)

    SettingGroup("音轨", "AUDIO")
    val audioKeys = keysFromCodes(AUDIO_LANGS, p.audioLangs)
    PickRow(
        "首选音轨语言",
        if (audioKeys.isEmpty()) "跟随文件" else "${audioKeys.size} 种",
        AUDIO_LANGS.map { PickOption(it.key, it.label) },
        audioKeys,
        { put("audioLangs", jsonStrings(toggledLangs(AUDIO_LANGS, p.audioLangs, it))) },
        note = "优先级：${describePriority(AUDIO_LANGS, audioKeys)}",
        tag = "set-audioLangs",
        multi = true,
        startOpen = pick == "audio",
    )

    SettingGroup("字幕", "SUBTITLES")
    val store = rememberGugaStore()
    val gp by store.prefs.collectAsState()
    PickRow(
        "开播字幕",
        gp.subtitleDefault.label,
        SubtitleDefault.entries.map { PickOption(it.id, it.label) },
        listOf(gp.subtitleDefault.id),
        { id -> store.update { it.copy(subtitleDefault = SubtitleDefault.of(id)) } },
        tag = "set-subtitleDefault",
        startOpen = pick == "subtitleDefault",
    )
    val effSubLangs = effectiveSubtitleLangs(p.subtitleLangs, gp.subLangsSeeded)
    val subKeys = keysFromCodes(GUGA_SUBTITLE_LANGS, effSubLangs)
    PickRow(
        "首选字幕语言",
        if (subKeys.isEmpty()) "无" else "${subKeys.size} 种",
        GUGA_SUBTITLE_LANGS.map { PickOption(it.key, it.label) },
        subKeys,
        {
            put("subtitleLangs", jsonStrings(toggledLangs(GUGA_SUBTITLE_LANGS, effSubLangs, it)))
            if (!gp.subLangsSeeded) store.update { prev -> prev.copy(subLangsSeeded = true) }
        },
        note = if (subKeys.isEmpty()) "仅按默认轨" else subKeys.mapNotNull { k -> GUGA_SUBTITLE_LANGS.firstOrNull { it.key == k }?.label }.joinToString(" → "),
        tag = "set-subtitleLangs",
        multi = true,
        startOpen = pick == "sub",
    )
    SettingRow(
        "在线字幕（迅雷、射手）",
        tag = "set-subtitleOnline",
    ) { OnOff(gp.subtitleOnline, { on -> store.update { it.copy(subtitleOnline = on) } }) }
    // 字幕智能匹配（1.4.1 U2，docs/subtitle-search.md §2.4）：画报自己的键 subtitleAutoBest，默认关
    SettingRow(
        "自动加载最合适的字幕",
        tag = "set-subtitleAutoBest",
    ) { OnOff(gp.subtitleAutoBest, { on -> store.update { it.copy(subtitleAutoBest = on) } }) }

    // 字幕样式（1.4.1）：字号、粗体、字体、描边、阴影、位置——画报自己的键 subStyle
    SubtitleStyleGroup(pick)
}


/* ------------------------------------------------------------------ 06 弹幕 */

@Composable
internal fun DanmakuSection(pick: String?) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val type = Guga.type
    val scope = rememberCoroutineScope()
    val settings by g.settings.state.collectAsState()
    val dm = settings.danmakuPrefs
    val r = dm.render
    fun put(key: String, v: JsonElement) = g.settings.patch("danmaku", key to v)

    // `render` 是整块替换的：并进存着的那一块里再写（不认识的键原样留着）——和上游的写法一样
    fun render(key: String, v: JsonElement) {
        val cur = ((g.settings.value["danmaku"] as? JsonObject)?.get("render") as? JsonObject) ?: JsonObject(emptyMap())
        put("render", JsonObject(LinkedHashMap(cur).also { it[key] = v }))
    }

    fun mode(key: String, on: Boolean) {
        val cur = (((g.settings.value["danmaku"] as? JsonObject)?.get("render") as? JsonObject)?.get("modes") as? JsonObject) ?: JsonObject(emptyMap())
        render("modes", JsonObject(LinkedHashMap(cur).also { it[key] = JsonPrimitive(on) }))
    }

    // 最上面：弹幕服务器（用户 2026-11-15：「输入弹幕服务器的不要放最下面，放弹幕设置最上面」）。存在系统安全存储里（上游的
    // SecretsStore）。当前值就排在这一行里，默认打码，点「查看」显示明文；可以直接改、保存、清除
    SettingGroup("弹幕服务器", "SERVER")
    val secrets by g.secrets.state.collectAsState()
    LaunchedEffect(Unit) { g.secrets.load() }
    DanmakuServerRows()
    SecretRow("TMDB 令牌", "存在系统安全存储中", secrets?.tmdbToken, "set-tmdb") { v -> scope.launch { g.secrets.save(tmdbToken = v) } }

    // 导入字体（用户 2026-11-14）
    DanmakuFontImportGroup()

    SettingGroup("显示", "DISPLAY")
    SettingRow("显示弹幕", tag = "set-dm-enabled") { OnOff(r.enabled, { render("enabled", JsonPrimitive(it)) }) }
    SettingRow("不透明度", tag = "set-dm-opacity") {
        Stepper(percentText(r.opacity), { render("opacity", num(stepRange(r.opacity, 0.05, 0.1, 1.0, -1))) }, { render("opacity", num(stepRange(r.opacity, 0.05, 0.1, 1.0, 1))) }, canMinus = r.opacity > 0.101, canPlus = r.opacity < 0.999)
    }
    SettingRow("字号", tag = "set-dm-fontScale") {
        Stepper(scaleText(r.fontScale), { render("fontScale", num(stepRange(r.fontScale, 0.05, 0.5, 2.0, -1))) }, { render("fontScale", num(stepRange(r.fontScale, 0.05, 0.5, 2.0, 1))) }, canMinus = r.fontScale > 0.501, canPlus = r.fontScale < 1.999)
    }
    SettingRow("滚动速度", tag = "set-dm-speed") {
        Stepper(scaleText(r.speed), { render("speed", num(stepRange(r.speed, 0.05, 0.5, 2.0, -1))) }, { render("speed", num(stepRange(r.speed, 0.05, 0.5, 2.0, 1))) }, canMinus = r.speed > 0.501, canPlus = r.speed < 1.999)
    }
    SettingRow("显示区域", note = areaChoice(r.area).let { if (it == null) "现在是 ${percentText(r.area)}" else null }, tag = "set-dm-area") {
        Segmented(AREA_CHOICES, areaChoice(r.area) ?: -1.0, { render("area", num(it)) })
    }
    SettingRow("同屏上限", tag = "set-dm-maxOnScreen") {
        Stepper(
            if (r.maxOnScreen == 0) "自动" else r.maxOnScreen.toString(),
            { render("maxOnScreen", JsonPrimitive((r.maxOnScreen - 10).coerceAtLeast(0))) },
            { render("maxOnScreen", JsonPrimitive((r.maxOnScreen + 10).coerceAtMost(300))) },
            canMinus = r.maxOnScreen > 0,
            canPlus = r.maxOnScreen < 300,
        )
    }
    SettingRow("弹幕类型", tag = "set-dm-modes") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CheckWord("滚动", r.scroll, { mode("scroll", !r.scroll) })
            CheckWord("顶部", r.top, { mode("top", !r.top) })
            CheckWord("底部", r.bottom, { mode("bottom", !r.bottom) })
        }
    }

    SettingGroup("样式", "STYLE")
    // 字体：导入的 + 系统 + 五款（和字幕共用）。上游有的写上游的键 danmaku.render.font；思源宋体 / 站酷快乐体记在画报的 danmakuFont，
    // 画面经上游的缝 LocalDanmakuFontOverride 用上（player/StyleApply.kt）
    val gugaStore = rememberGugaStore()
    val gugaPrefs by gugaStore.prefs.collectAsState()
    val importedFonts = rememberImportedFonts()
    val imported by importedFonts.state.collectAsState()
    val styleFonts = com.gugaart.ui.player.rememberStyleFonts()
    val fontStates by styleFonts.states.collectAsState()
    val dmFonts = com.gugaart.ui.data.danmakuFontChoices(
        r.font, gugaPrefs.danmakuFont, imported, fontStates,
        setUpstream = { render("font", JsonPrimitive(it.id)) },
        setExtra = { id -> gugaStore.update { it.copy(danmakuFont = id) } },
        selectImported = importedFonts::select,
        prefetch = { f -> scope.launch { styleFonts.ensure(f) } },
    )
    PickRow(
        "字体", dmFonts.firstOrNull { it.selected }?.label ?: r.font.label, dmFonts.map { PickOption(it.key, it.label, it.note) }, dmFonts.filter { it.selected }.map { it.key },
        { key -> dmFonts.firstOrNull { it.key == key }?.pick?.invoke() }, tag = "set-dm-font", startOpen = pick == "font",
    )
    SettingRow("粗细", tag = "set-dm-weight") { Segmented(DanmakuWeight.entries.map { it to it.label }, r.weight, { render("weight", JsonPrimitive(it.id)) }) }

    SettingGroup("匹配", "MATCHING")
    SettingRow("播放时自动匹配弹幕", tag = "set-autoMatch") { OnOff(dm.autoMatch, { put("autoMatch", JsonPrimitive(it)) }) }
    var sourcesOpen by remember { mutableStateOf(pick == "sources") }
    SettingRow("来源优先级", note = dm.sourcePriority.take(3).joinToString(" › ") { DANMAKU_SOURCE_NAMES[it] ?: it }, tag = "set-sourcePriority", onClick = { sourcesOpen = !sourcesOpen }) {
        ValueChevron("${dm.sourcePriority.size} 个来源", sourcesOpen)
    }
    if (sourcesOpen) {
        dm.sourcePriority.forEachIndexed { i, id ->
            Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 20.dp).testTag("dm-source-$id"), verticalAlignment = Alignment.CenterVertically) {
                BasicText((i + 1).toString(), Modifier.width(30.dp), style = Guga.numeral(type.row, colors.spot), maxLines = 1)
                BasicText(DANMAKU_SOURCE_NAMES[id] ?: id, Modifier.weight(1f), style = Guga.song(type.row * 0.96f), maxLines = 1)
                MoveKeys(
                    i > 0, i < dm.sourcePriority.lastIndex,
                    { put("sourcePriority", jsonStrings(dm.sourcePriority.moved(i, -1))) },
                    { put("sourcePriority", jsonStrings(dm.sourcePriority.moved(i, 1))) },
                )
            }
            Rule()
        }
        SettingRow("恢复默认顺序") { ActionWord("恢复", { put("sourcePriority", jsonStrings(DEFAULT_DANMAKU_SOURCES)) }) }
    }
    SettingRow("时间偏移", tag = "set-dm-offset") {
        Stepper(
            formatOffset(r.offsetMs).replace(" s", " 秒"),
            { render("offsetMs", JsonPrimitive((r.offsetMs - 500).coerceAtLeast(-30_000))) },
            { render("offsetMs", JsonPrimitive((r.offsetMs + 500).coerceAtMost(30_000))) },
            canMinus = r.offsetMs > -30_000,
            canPlus = r.offsetMs < 30_000,
        )
    }
    var blockText by remember { mutableStateOf(r.blockWords.joinToString("，")) }
    SettingRow("屏蔽词", note = "逗号分隔 · ${r.blockWords.size} 个")
    InputLine(blockText, { blockText = it; render("blockWords", jsonStrings(parseBlockWords(it))) }, "剧透，前方高能", tag = "set-dm-blockWords")
    SettingRow("恢复默认的弹幕显示", tag = "set-dm-reset") {
        ActionWord("恢复默认", {
            put("render", DEFAULT_DANMAKU_RENDER_JSON)
            blockText = ""
        })
    }

    SettingGroup("性能", "PERFORMANCE")
    SettingRow("性能档位", tag = "set-dm-perfTier") {
        Segmented(listOf("auto" to "自动", "low" to "低", "mid" to "中", "high" to "高"), r.perfTier, { render("perfTier", JsonPrimitive(it)) })
    }
    PickRow(
        "帧率上限",
        if (r.fpsCap == "auto") "自动" else r.fpsCap,
        listOf("auto", "30", "60", "90", "120").map { PickOption(it, if (it == "auto") "自动" else "$it 帧") },
        listOf(r.fpsCap),
        { render("fpsCap", if (it == "auto") JsonPrimitive("auto") else JsonPrimitive(it.toInt())) },
        tag = "set-dm-fpsCap",
        startOpen = pick == "fps",
    )

}

/**
 * 弹幕服务器清单（用户 2026-11-15：「弹幕支持多源，先匹配出来的优先显示」→ 多个弹幕服务器；danmaku/DanmakuServers.kt）：
 * 每台一行（和原来那一行一样：打码、查看、测试连接、清除 = 去掉这一台）；下面「再加一个」。两台以上时播放时同时去匹配，谁先匹配上用谁。
 * 第一台的 tag 沿用 `set-dm-apiUrl`。
 */
@Composable
private fun DanmakuServerRows() {
    val g = LocalGraph.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        com.gugaart.ui.danmaku.DanmakuServers.bind(g.deps.secure, g.deps.debug.defaultSecrets?.danmakuApiUrl)
        com.gugaart.ui.danmaku.DanmakuServers.load()
    }
    val list by com.gugaart.ui.danmaku.DanmakuServers.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val urls = list?.map { it.url }
    fun save(next: List<String>) = scope.launch {
        com.gugaart.ui.danmaku.DanmakuServers.save(next)
        g.secrets.load()
    }
    if (urls == null || urls.isEmpty()) {
        // 一台都没有：就是原来那一行
        SecretRow("弹幕服务器 API 地址", "地址里含令牌", urls?.let { "" }, "set-dm-apiUrl", probe = { g.danmaku.probe(it) }) { v -> save(listOf(v)) }
        return
    }
    urls.forEachIndexed { i, url ->
        SecretRow(
            if (urls.size > 1) "弹幕服务器 ${i + 1}" else "弹幕服务器 API 地址",
            if (i == 0) "地址里含令牌" else null,
            url,
            if (i == 0) "set-dm-apiUrl" else "set-dm-apiUrl-${i + 1}",
            probe = { g.danmaku.probe(it) },
        ) { v -> save(urls.toMutableList().also { it[i] = v }) }
    }
    if (adding) {
        SecretRow("弹幕服务器 ${urls.size + 1}", null, "", "set-dm-apiUrl-new", probe = { g.danmaku.probe(it) }) { v ->
            adding = false
            if (v.isNotBlank()) save(urls + v)
        }
    } else if (urls.size < com.gugaart.ui.danmaku.MAX_DANMAKU_SERVERS) {
        SettingRow("添加弹幕服务器", tag = "set-dm-add") {
            ActionWord("添加", { adding = true }, modifier = Modifier.testTag("set-dm-add-go"))
        }
    }
}

/**
 * 一条密钥（弹幕服务器地址 / TMDB 令牌）。用户要求（2026-11-14）：**不要藏起来**——
 * - 当前值就在这一行的输入框里，**默认打码**；旁边一枚眼睛「查看」，点一下显示明文，再点隐藏（和上游设置页的眼睛按钮同一个做法）；
 * - 值可以直接改；改过才出现「保存」；有值时有「清除」；
 * - [probe] 不为空（弹幕服务器）：多一个「测试连接」，判词用上游的 `danmakuProbeVerdict`（可用 / 缓存未共享 / 不可用：原因——原因里不带地址和令牌）。
 * [stored]：null = 还没读出来。
 */
@Composable
private fun SecretRow(label: String, note: String?, stored: String?, tag: String, probe: (suspend (String) -> Result<DanmakuCacheProbe>)? = null, onSave: (String) -> Unit) {
    val colors = Guga.colors
    val scope = rememberCoroutineScope()
    // 测试 / 截图的预置：`reveal` = 一开始就是明文（只用假值）
    val revealed = LocalPageState.current == "reveal"
    var shown by remember { mutableStateOf(revealed) }
    var text by remember(stored) { mutableStateOf(stored.orEmpty()) }
    var probeState by remember { mutableStateOf(DanmakuProbeVisual.Idle) }
    var probeError by remember { mutableStateOf<Throwable?>(null) }
    val dirty = stored != null && text.trim() != stored
    SettingRow(label, note = note, tag = tag) {
        ValueText(when { stored == null -> "…"; stored.isEmpty() -> "未配置"; else -> "已配置" })
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        InputLine(
            text,
            {
                text = it
                // 地址改了：上一次的判词不再算数
                if (probeState != DanmakuProbeVisual.Testing) { probeState = DanmakuProbeVisual.Idle; probeError = null }
            },
            if (stored.isNullOrEmpty()) "填在这里" else "",
            Modifier.weight(1f),
            secret = !shown,
            tag = "$tag-input",
        )
        // 眼睛：查看 / 隐藏
        val eye = rememberPressState()
        Box(Modifier.size(44.dp).pressGraphics(eye).pressable(eye, label = if (shown) "隐藏" else "查看") { shown = !shown }.testTag(if (shown) "$tag-hide" else "$tag-show"), contentAlignment = Alignment.Center) {
            Icon(if (shown) GuguIcon.EyeOff else GuguIcon.Eye, if (shown) colors.spot else colors.dim, 18.dp)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 20.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (dirty) ActionWord("保存", { onSave(text.trim()) }, modifier = Modifier.testTag("$tag-save"))
        if (dirty) ActionWord("还原", { text = stored.orEmpty() })
        if (!stored.isNullOrEmpty()) ActionWord("清除", { onSave(""); shown = false }, danger = true, modifier = Modifier.testTag("$tag-clear"))
        if (probe != null) {
            ActionWord(
                danmakuProbeChipLabel(probeState),
                {
                    val url = text
                    probeState = DanmakuProbeVisual.Testing
                    probeError = null
                    scope.launch {
                        val r = probe(url)
                        // 等结果的时候地址又被改了：不给判词（那是另一个地址的）
                        if (text.trim() == url.trim()) {
                            probeError = r.exceptionOrNull()
                            probeState = when {
                                r.isFailure -> DanmakuProbeVisual.Error
                                r.getOrNull() == DanmakuCacheProbe.NotShared -> DanmakuProbeVisual.NoCache
                                else -> DanmakuProbeVisual.Ok
                            }
                        } else {
                            probeError = null
                            probeState = DanmakuProbeVisual.Idle
                        }
                    }
                },
                enabled = text.isNotBlank() && probeState != DanmakuProbeVisual.Testing,
                modifier = Modifier.testTag("$tag-probe"),
            )
        }
    }
    val verdict = danmakuProbeVerdict(probeState, probeError)
    if (verdict != null) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 8.dp).testTag("$tag-verdict"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (verdict.ok && !verdict.warning) GuguIcon.CircleCheck else GuguIcon.TriangleAlert, if (verdict.ok && !verdict.warning) colors.ink else colors.seal, 14.dp)
            BasicText(verdict.text, style = Guga.note(Guga.type.caption * 1.04f, colors.ink), maxLines = 2)
        }
    }
}

/* ------------------------------------------------------------------ 07 缓存 */

@Composable
internal fun CacheSection() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val type = Guga.type
    val scope = rememberCoroutineScope()
    var images by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var comments by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    val history = remember { SearchHistoryStore(g.deps.local) }
    var historyCount by remember { mutableStateOf(history.load().size) }
    var confirmReset by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        images = g.images.diskSize().let { it.entries to it.bytes }
        comments = g.danmaku.commentCacheSize()?.let { it.entries to it.bytes } ?: (0 to 0L)
    }
    val maxBytes = com.gugugaga.app.data.ImageDiskCache.MAX_BYTES
    val used = images?.second ?: 0L

    SettingGroup("占用", "USAGE")
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).progressLine((used.toFloat() / maxBytes).coerceIn(0f, 1f), colors.spot, track = colors.line).padding(bottom = 10.dp).testTag("cache-usage"), verticalAlignment = Alignment.Bottom) {
        BasicText((used / (1024 * 1024)).toString(), style = Guga.numeral(type.display * 0.8f), maxLines = 1)
        BasicText("  MB / ${maxBytes / (1024 * 1024)} MB", Modifier.padding(bottom = 10.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1)
    }
    SettingRow(
        "图片缓存",
        note = images?.let { (n, b) -> if (n > 0) "已缓存 ${formatBytes(b.toDouble())} · $n 张" else "没有缓存" },
        tag = "cache-images",
    ) {
        ActionWord("清除", {
            scope.launch {
                g.images.clearDisk()
                images = g.images.diskSize().let { it.entries to it.bytes }
            }
        }, enabled = (images?.first ?: 0) > 0)
    }
    SettingRow(
        "弹幕缓存",
        note = comments?.let { (n, b) -> if (n > 0) "已缓存 ${formatBytes(b.toDouble())} · $n 集" else "没有缓存" },
        tag = "cache-comments",
    ) {
        ActionWord("清除", {
            scope.launch {
                g.danmaku.clearCommentCache()
                comments = g.danmaku.commentCacheSize()?.let { it.entries to it.bytes } ?: (0 to 0L)
            }
        }, enabled = (comments?.first ?: 0) > 0)
    }
    SettingRow("搜索历史", note = if (historyCount > 0) "$historyCount 个关键词" else "没有记录", tag = "cache-search") {
        ActionWord("清除", { history.clear(); historyCount = 0 }, enabled = historyCount > 0)
    }

    SettingGroup("维护", "MAINTENANCE")
    SettingRow("恢复默认设置", tag = "reset-settings") { ActionWord("恢复默认", { confirmReset = true }) }
    if (confirmReset) {
        ConfirmSheet(
            title = "重置所有设置？",
            confirm = "重置",
            onConfirm = { g.settings.reset(); confirmReset = false },
            onDismiss = { confirmReset = false },
            danger = true,
        )
    }
}

/* ------------------------------------------------------------------ 08 账号与服务器 */

@Composable
internal fun AccountSection() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val scope = rememberCoroutineScope()
    val session by g.session.session.collectAsState()
    val revision by g.session.revision.collectAsState()
    val saved by produceState<com.gugugaga.app.session.SavedServers?>(null, revision) { value = g.session.saved() }
    var confirmOut by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    val s = session

    // 服务器的版本、系统，顺手量一下往返时间（只读的 /System/Info/Public）
    val info by produceState<Pair<com.gugugaga.core.PublicSystemInfo, Long>?>(null, s?.serverId, s?.userId) {
        val cur = s ?: return@produceState
        value = try {
            val t0 = currentTimeMillis()
            val i = cur.client.getPublicInfo()
            i to (currentTimeMillis() - t0)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    if (s != null) {
        SettingGroup("当前账号", "SIGNED IN")
        SettingRow(s.userName, note = "已登录", tag = "account-name") { ActionWord("切换服务器", { g.nav.navigate(Route.Login(switch = true)) }) }
        SettingRow("服务器", note = s.serverName, tag = "account-server") { ValueText(info?.first?.let { "${it.productName?.removeSuffix(" Server") ?: "Emby"} ${it.version}" } ?: "读取中…") }
        InfoRow("操作系统", info?.first?.operatingSystem)
        InfoRow("连接延迟", info?.second?.let { "$it ms" })
        SettingRow("退出登录", tag = "sign-out", onClick = { confirmOut = true }) { ValueChevron(null) }
        if (confirmOut) {
            ConfirmSheet(
                title = "退出登录？",
                confirm = "退出登录",
                onConfirm = {
                    scope.launch {
                        busy = "out"
                        try {
                            g.session.signOut()
                        } finally {
                            busy = null
                            confirmOut = false
                        }
                    }
                },
                onDismiss = { confirmOut = false },
                danger = true,
            )
        }
    }

    SettingGroup("已保存", "SAVED")
    val list = saved
    when {
        list == null -> BasicText("读取中…", Modifier.padding(vertical = 18.dp), style = Guga.song(Guga.type.caption, color = colors.faint))
        list.servers.isEmpty() -> BasicText("没有已保存的账号", Modifier.padding(vertical = 18.dp), style = Guga.song(Guga.type.caption, color = colors.faint))
        else -> for (server in list.servers) {
            for (account in server.accounts) {
                val active = list.active?.serverId == server.id && list.active?.userId == account.userId
                val key = "${server.id}:${account.userId}"
                SettingRow(
                    server.name,
                    note = account.userName + if (failed == key) " · 登录已失效" else "",
                    tag = "saved-$key",
                ) {
                    when {
                        active -> ValueText("当前")
                        failed == key -> ActionWord("重新登录", { g.nav.navigate(Route.Login(switch = true, user = account.userName)) })
                        else -> ActionWord(if (busy == key) "切换中…" else "切换", {
                            scope.launch {
                                busy = key
                                try {
                                    g.session.switchAccount(server.id, account.userId)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    failed = key
                                } finally {
                                    busy = null
                                }
                            }
                        }, enabled = busy == null)
                    }
                }
            }
            // 没有账号（例如从备份还原的服务器）：登录页带上这台的地址
            if (server.accounts.isEmpty()) SettingRow(server.name, note = "没有账号", onClick = { g.nav.navigate(Route.Login(switch = true, server = com.gugugaga.core.apiBaseOf(server.address))) }) { ValueChevron("登录") }
        }
    }
    SettingRow("添加账号", tag = "add-account", onClick = { g.nav.navigate(Route.Login(switch = true)) }) { ValueChevron(null) }

    // Trakt（账号与服务器）
    TraktGroup()
}

/* ------------------------------------------------------------------ 备份与同步 */

@Composable
internal fun BackupSection() {
    BackupGroup()
}

/* ------------------------------------------------------------------ 高级（桌面 / 综合页） */

@Composable
internal fun IdleHomeGroup() {
    if (Guga.uiMode == UiMode.Phone) return
    val g = LocalGraph.current
    val store = rememberGugaStore()
    val prefs by store.prefs.collectAsState()
    SettingGroup("闲置", "IDLE")
    SettingRow("闲置回首页", note = "10 分钟无操作", tag = "set-idleHome") {
        OnOff(prefs.idleHomeFor(g.deps.uiMode), { on -> store.update { it.copy(idleHome = on) } })
    }
}

@Composable
internal fun AdvancedSection(startDiagOpen: Boolean = false) {
    IdleHomeGroup()
    StrmSection()
    CacheSection()
    AboutSection(startDiagOpen = startDiagOpen)
}

/* ------------------------------------------------------------------ 关于与诊断 */

@Composable
internal fun AboutSection(startDiagOpen: Boolean = false) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val type = Guga.type
    val status by g.updater.status.collectAsState()

    Rule(Modifier.padding(top = 22.dp), strong = true)
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("about-head"), verticalAlignment = Alignment.CenterVertically) {
        if (LocalGugaPrefs.current.showMascot) Byline(52.dp, live = false)
        Column(Modifier.padding(start = 14.dp)) {
            BasicText("咕咕嘎嘎", style = Guga.song(type.section, FontWeight.Black, letterSpacing = 0.3.em), maxLines = 1)
            BasicText("${g.deps.appVersion} · ${if (g.updater.enabled) g.updater.channelLabel else "咕嘎画报 · EMBY"}", Modifier.padding(top = 3.dp), style = Guga.note(type.caption, colors.dim), maxLines = 1)
        }
    }
    Rule()

    SettingGroup("更新", "UPDATE")
    if (g.updater.enabled) {
        SettingRow("检查更新", note = com.gugaart.ui.screens.update.aboutCheckNote(status.lastResult), tag = "about-check") { ActionWord("检查", { com.gugaart.ui.screens.update.UpdateConsent.checkByHand(g) }) }
    } else {
        SettingRow("检查更新", note = "这一版不检查更新", tag = "about-check") { ValueText("未开启") }
    }

    // 随包的第三方组件和许可证（D-lead-41）
    SettingGroup("许可", "LICENSES")
    SettingRow("开源许可", note = "GPL-3.0 · 第三方组件许可证", tag = "about-licenses", onClick = { g.nav.navigate(Route.Settings(OpenSourceLicenses.KEY)) }) { ValueChevron(null) }

    SettingGroup("这台设备", "DEVICE")
    InfoRow("平台", g.deps.platformLabel)
    InfoRow("设备名称", g.deps.identity.device)

    // P2-17：诊断项收进 关于 › 诊断，点开才看到
    var diagOpen by remember(startDiagOpen) { mutableStateOf(startDiagOpen) }
    var cleared by remember { mutableStateOf(false) }
    SettingRow("诊断", tag = "about-diagnostics", onClick = { diagOpen = !diagOpen }) { ValueChevron(null, diagOpen) }
    AnimatedVisibility(diagOpen, enter = expandVertically(), exit = shrinkVertically()) {
        Column(
            Modifier.fillMaxWidth()
                .drawBehind { drawLine(colors.seal, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx()) }
                .padding(start = 20.dp)
                .testTag("about-diagnostics-list"),
        ) {
            InfoRow("设备 ID", g.deps.identity.deviceId.take(8) + "…")
            InfoRow("令牌存储", g.deps.secureMode())
            InfoRow("界面模式", when (g.deps.uiMode) { UiMode.Phone -> "手机"; UiMode.Desktop -> "电脑"; UiMode.Tv -> "电视" })
            if (g.updater.enabled) InfoRow("更新通道", g.updater.channelLabel)
            SettingRow("界面数据缓存", note = if (cleared) "已清理" else null, tag = "cache-queries") {
                ActionWord("清理", { g.queries.clear(); cleared = true }, enabled = !cleared)
            }
        }
    }
}
