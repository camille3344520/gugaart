package com.gugaart.ui.screens.settings

import androidx.compose.runtime.staticCompositionLocalOf
import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.data.MascotPref
import com.gugugaga.app.player.StrmSettings
import com.gugugaga.app.settings.AUDIO_LANGS
import com.gugugaga.app.settings.SettingsSection
import com.gugugaga.app.settings.SubtitleContext
import com.gugugaga.app.settings.keysFromCodes
import com.gugugaga.app.settings.playbackPrefs
import com.gugugaga.app.settings.settingsSubtitle
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 设置页的纯逻辑：六个主要分区 + 折叠「高级」的目录、目录页每行下面那句「现在是什么」、步进器在一组档位里走一步。
 * 设置项本身（键名、默认值、取值范围）全部是上游的（`app/settings/`），这里只决定画报怎么分区、怎么排。
 */

/**
 * 画报的六个主要分区 + 折叠「高级」（P2-18）。
 * [key] = 路由 `Route.Settings(section)` 的那一段：兼容旧路由 `interface` → [Appearance]、`extensions` → [Cache]、
 * `strm` / `cache` / `about` / `diagnostics` → 归在「高级」下。
 */
enum class GugaSection(
    val key: String,
    val title: String,
    val english: String,
    val advanced: Boolean = false,
) {
    Account("account", "账号与服务器", "ACCOUNT"),
    Playback("playback", "播放", "PLAYBACK"),
    Subs("subtitles", "字幕与音轨", "SUBS & AUDIO"),
    Danmaku("danmaku", "弹幕", "DANMAKU"),
    Appearance("appearance", "外观与界面", "APPEARANCE"),
    Backup("backup", "备份与同步", "BACKUP"),
    Advanced("advanced", "高级", "ADVANCED", advanced = true),
    Strm("strm", "strm 直链", "DIRECT LINK", advanced = true),
    Cache("cache", "缓存", "CACHE", advanced = true),
    About("about", "关于", "ABOUT", advanced = true),
    Diagnostics("diagnostics", "诊断", "DIAGNOSTICS", advanced = true);

    /** 「01」..「06」；高级子项不编号。 */
    val number: String get() = if (advanced) "" else (PRIMARY.indexOf(this) + 1).toString().padStart(2, '0')

    companion object {
        /** 目录页 / 侧栏常驻的六个分区。 */
        val PRIMARY: List<GugaSection> = listOf(Account, Playback, Subs, Danmaku, Appearance, Backup)

        /** 「高级」折叠区展开后列出的子页（手机目录 / 桌面左栏 / 电视左页）。 */
        val ADVANCED_CHILDREN: List<GugaSection> = listOf(Strm, Cache, About)

        fun of(key: String?): GugaSection? = when (key) {
            null, "" -> null
            "interface" -> Appearance
            "extensions" -> Cache
            else -> entries.firstOrNull { it.key == key }
        }
    }
}

/** 目录页每个分区要的「现在」：设置原文、画报设置、当前账号、版本、缓存用量（知道才写）。 */
class SummaryFacts(
    val settings: JsonObject,
    val prefs: GugaPrefs,
    val account: Pair<String, String>? = null,
    val appVersion: String = "",
    val cache: String? = null,
    val danmakuConfigured: Boolean = true,
    val webdavConfigured: Boolean = false,
    val hasIdleHome: Boolean = false,
)

private val PROXY_MODE = mapOf("auto" to "自动", "direct" to "始终直连", "proxy" to "始终经代理")

fun proxyModeLabel(mode: String): String = PROXY_MODE[mode] ?: "自动"

/** 目录页「播放」一行里的内核：自动 / EXO / VLC / MPV / 外部播放器（审计 P2-3：不叫「引擎」）。纯函数。 */
fun engineSummaryLabel(id: String?): String = when (id) {
    "exoplayer" -> "EXO"
    "vlc" -> "VLC"
    "mpv" -> "MPV"
    "external" -> "外部播放器"
    "web" -> "WEB"
    else -> "自动"
}

/** 目录页一行下面那句话：写**当前值**。 */
fun sectionSummary(section: GugaSection, f: SummaryFacts): String {
    val ctx = SubtitleContext(f.settings, com.gugugaga.app.settings.ThemePref.Dark, com.gugugaga.app.settings.PerfPref.Auto, f.account, f.appVersion, f.cache)
    return when (section) {
        GugaSection.Account -> settingsSubtitle(SettingsSection.Account, ctx)
        // 审计 P2-3：叫「内核」，不叫「引擎」
        GugaSection.Playback -> "内核 " + engineSummaryLabel(f.settings.playbackPrefs.engine) + " · " + if (f.settings.playbackPrefs.autoPlayNext) "自动下一集" else "不自动下一集"
        // 「开播字幕」的值（自动 / 跟随上次 / 关闭）；前面带首选音轨语言
        GugaSection.Subs -> {
            val p = f.settings.playbackPrefs
            val audioFirst = keysFromCodes(AUDIO_LANGS, p.audioLangs).firstOrNull()?.let { k -> AUDIO_LANGS.firstOrNull { it.key == k }?.label }.orEmpty()
            listOfNotNull(audioFirst.takeIf { it.isNotEmpty() }?.let { "音轨$it" }, "开播字幕 " + f.prefs.subtitleDefault.label).joinToString(" · ")
        }
        GugaSection.Danmaku -> if (!f.danmakuConfigured) "未配置" else settingsSubtitle(SettingsSection.Danmaku, ctx)
        GugaSection.Appearance -> if (f.prefs.spotColorFollowsCover) "主题色跟随海报" else "主题色固定朱红"
        GugaSection.Backup -> if (f.webdavConfigured) "WebDAV 已配置" else "本地 · WebDAV"
        GugaSection.Advanced -> if (f.hasIdleHome) "strm 直链 · 闲置 · 缓存 · 诊断" else "strm 直链 · 缓存 · 诊断"
        GugaSection.Strm -> {
            val s = StrmSettings.of(f.settings)
            proxyModeLabel(s.strmProxyMode) + " · " + if (s.preferStrmTarget) "优先直连" else "经 Emby 中转"
        }
        GugaSection.Cache -> settingsSubtitle(SettingsSection.Cache, ctx)
        GugaSection.About -> f.appVersion.ifEmpty { "版本 · 更新 · 许可" }
        GugaSection.Diagnostics -> "设备 ID · 令牌存储 · 界面数据缓存"
    }
}

/* ---- 步进器 ---- */

/** 在一组档位里走一步：[current] 不在档位上时先落到最近的一档。到头不动。 */
fun <T : Comparable<T>> stepChoice(choices: List<T>, current: T, delta: Int, distance: (T, T) -> Double): T {
    if (choices.isEmpty()) return current
    val at = choices.indices.minBy { distance(choices[it], current) }
    return choices[(at + delta).coerceIn(0, choices.lastIndex)]
}

fun stepInt(choices: List<Int>, current: Int, delta: Int): Int = stepChoice(choices, current, delta) { a, b -> abs(a - b).toDouble() }

fun stepDouble(choices: List<Double>, current: Double, delta: Int): Double = stepChoice(choices, current, delta) { a, b -> abs(a - b) }

/** 连续量走一步（不透明度 5 %、字号 0.05 ×）：夹在范围里，并消掉浮点尾巴（0.8500000001 → 0.85）。 */
fun stepRange(value: Double, step: Double, min: Double, max: Double, delta: Int): Double {
    val n = ((value + step * delta) / step).roundToInt() * step
    return ((n.coerceIn(min, max)) * 1000).roundToInt() / 1000.0
}

/** 「80%」。 */
fun percentText(v: Double): String = "${(v * 100).roundToInt()}%"

/** 「1.0×」「1.25×」。 */
fun scaleText(v: Double): String {
    val h = (v * 100).roundToInt()
    return if (h % 10 == 0) "${h / 100}.${h % 100 / 10}×" else "${h / 100}.${(h % 100).toString().padStart(2, '0')}×"
}

/** 显示区域的四格：¼ 屏 / 半屏 / ¾ 屏 / 全屏。存的是上游的 `area`（0.05 – 1）；不在这四档上（别处调过）→ 哪一格都不亮。 */
val AREA_CHOICES: List<Pair<Double, String>> = listOf(0.1 to "10%", 0.25 to "¼ 屏", 0.5 to "半屏", 0.75 to "¾ 屏", 1.0 to "全屏")

fun areaChoice(area: Double): Double? = AREA_CHOICES.firstOrNull { abs(it.first - area) < 0.011 }?.first

/* ---- 画报自己的四项（gugaart.settings.v1） ---- */

/** 「手记」三档 ↔ 存着的两项（mascot、editorNotes）。常写 = 她在、说话；少写 = 只留署名照；不写 = 手记那一格收起。 */
enum class NotesChoice(val label: String) { Often("常写"), Seldom("少写"), Never("不写") }

fun notesChoiceOf(p: GugaPrefs): NotesChoice = when {
    !p.editorNotes -> NotesChoice.Never
    p.mascot == MascotPref.Subtle -> NotesChoice.Seldom
    else -> NotesChoice.Often
}

/** 改「手记」：只动 editorNotes 和 mascot 的 on / subtle；她已经关掉（off）时不替用户打开。 */
fun withNotes(p: GugaPrefs, c: NotesChoice): GugaPrefs {
    val off = p.mascot == MascotPref.Off
    return when (c) {
        NotesChoice.Often -> p.copy(editorNotes = true, mascot = if (off) MascotPref.Off else MascotPref.On)
        NotesChoice.Seldom -> p.copy(editorNotes = true, mascot = if (off) MascotPref.Off else MascotPref.Subtle)
        NotesChoice.Never -> p.copy(editorNotes = false)
    }
}

/** 「咕咕嘎嘎出现在版面上」开 / 关：关 = off；开 = 回到 on（少写的那一档由「手记」管）。 */
fun withMascot(p: GugaPrefs, on: Boolean): GugaPrefs = p.copy(mascot = if (!on) MascotPref.Off else if (p.mascot == MascotPref.Off) MascotPref.On else p.mascot)

/**
 * 根界面的那一个 [GugaSettingsStore]（设置页改了，整本画报立刻跟着变）。根没给（离屏测试直接画某一页）时设置页自己造一个，
 * 读写的是同一个键。
 */
val LocalGugaSettings = staticCompositionLocalOf<GugaSettingsStore?> { null }

/**
 * 验收缝（照 `LocalPlaySessionSink` 的先例）：进程内运行器（iOS / macOS 的 `--accept`）造不出真触摸，
 * 要改设置就得拿根界面的**这一个** store（自己 new 一个只写盘，界面那份 StateFlow 收不到）。界面不提供给它的 sinks 时什么都不做。
 */
val LocalGugaSettingsSink = staticCompositionLocalOf<((GugaSettingsStore) -> Unit)?> { null }
