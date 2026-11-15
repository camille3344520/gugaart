package com.gugaart.ui.tv.system

import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.data.SubtitleDefault
import com.gugaart.ui.screens.settings.AREA_CHOICES
import com.gugaart.ui.screens.settings.GugaSection
import com.gugaart.ui.screens.settings.NotesChoice
import com.gugaart.ui.screens.settings.areaChoice
import com.gugaart.ui.screens.settings.notesChoiceOf
import com.gugaart.ui.screens.settings.percentText
import com.gugaart.ui.screens.settings.scaleText
import com.gugaart.ui.screens.settings.withMascot
import com.gugaart.ui.screens.settings.withNotes
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.STRM_PROXY_MODE_KEY
import com.gugugaga.app.player.STRM_SETTINGS_SECTION
import com.gugugaga.app.player.StrmSettings
import com.gugugaga.app.settings.AUDIO_LANGS
import com.gugugaga.app.settings.BITRATE_CHOICES_MBPS
import com.gugugaga.app.settings.DANMAKU_SOURCE_NAMES
import com.gugugaga.app.settings.DEFAULT_DANMAKU_RENDER_JSON
import com.gugugaga.app.settings.DEFAULT_DANMAKU_SOURCES
import com.gugugaga.app.settings.EPISODE_PAGE_SIZES
import com.gugugaga.app.settings.GLASS_STRENGTH_MAX
import com.gugugaga.app.settings.GLASS_STRENGTH_MIN
import com.gugugaga.app.settings.HOME_SECTION_LABELS
import com.gugugaga.app.settings.LangOption
import com.gugugaga.app.settings.PerfPref
import com.gugugaga.app.settings.RATE_CHOICES
import com.gugugaga.app.settings.SEEK_STEP_CHOICES_SEC
import com.gugugaga.app.settings.SUBTITLE_LANGS
import com.gugugaga.app.settings.SUBTITLE_MODE_CHOICES
import com.gugugaga.app.settings.Secrets
import com.gugugaga.app.settings.bitrateLabel
import com.gugugaga.app.settings.codesFromKeys
import com.gugugaga.app.settings.danmakuPrefs
import com.gugugaga.app.settings.engineChoices
import com.gugugaga.app.settings.extraCodes
import com.gugugaga.app.settings.homeHidden
import com.gugugaga.app.settings.homeSections
import com.gugugaga.app.settings.jsonStrings
import com.gugugaga.app.settings.keysFromCodes
import com.gugugaga.app.settings.playbackPrefs
import com.gugugaga.app.settings.uiSettings
import com.gugugaga.danmaku.settings.DanmakuFont
import com.gugugaga.danmaku.settings.DanmakuWeight
import com.gugugaga.ds.theme.UiMode
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * 电视设置页的行（设计稿 `tv/index.html?p=settings`）。**和手机是同一棵设置树、写回同一批键**（上游 `app/settings/` 的键，
 * 和画报自己的 `gugaart.settings.v1`），这里没有新增任何键。手机的行组件（开关格、分段、步进的 − / ＋）是给手指点的，
 * 遥控器够不着；电视上每一行是**一个焦点**：确定 = 开关 / 打开选择器 / 执行。分段和步进在电视上都变成选择器里的一列值。
 *
 * 这一层是纯数据（不是 Composable）：给一份设置，排出这一分区的行——所以每一行写回哪个键、选了第几个写什么值，都能单测。
 *
 * 要打字的项（[TvText]：strm 的 User-Agent / Referer、弹幕服务器地址、TMDB 令牌）= 确定后换成遥控器全键盘那一屏（和登录页同一个键盘）；
 * 要排顺序的项（[TvOrder]：首页板块、弹幕来源）= 确定后页边出来一列，「确定拿起 → 上下移动 → 确定放下」。
 * 电视上还没排的：弹幕屏蔽词（要输中文，遥控器键盘打不了；只读显示个数）。
 */

sealed interface TvSetting {
    val id: String
    val label: String
    val note: String?
}

/** 分组的小标题（不接焦点）。 */
class TvHead(override val label: String, val english: String) : TvSetting {
    override val id: String get() = "head-$english"
    override val note: String? get() = null
}

/** 开关：确定 = 开 / 关。 */
class TvToggle(override val id: String, override val label: String, override val note: String?, val on: Boolean, val set: (Boolean) -> Unit) : TvSetting

/** 选择器里的一项。 */
class TvPick(val label: String, val note: String? = null)

/** 一组值里选一个（或几个，[multi]）：确定 = 打开选择器；选择器里确定 = 选中并收起（多选 = 勾 / 去勾，不收起）。 */
class TvChoice(
    override val id: String,
    override val label: String,
    override val note: String?,
    val value: String,
    val options: List<TvPick>,
    val selected: Set<Int>,
    val english: String = "",
    val multi: Boolean = false,
    val pick: (Int) -> Unit,
) : TvSetting

/** 只读的一行（版本号、设备名）：不接焦点。 */
class TvInfo(override val id: String, override val label: String, val value: String?, override val note: String? = null) : TvSetting

/** 一个动作（检查更新、切换账号、恢复默认）：确定 = 执行。[confirm]：要再按一次确定才执行（恢复默认、退出登录）。 */
class TvAction(override val id: String, override val label: String, override val note: String?, val value: String, val confirm: String? = null, val run: () -> Unit) : TvSetting

/**
 * 要打字的一项：确定 = 换成遥控器全键盘那一屏，「保存」写回、返回不改。[secret]：密钥（弹幕服务器地址里带令牌、TMDB 令牌）——
 * 行里只写「已配置 / 未设置」，键盘那一屏默认打码，按「显示」才是明文。[value] = 现在存着的字。
 */
class TvText(
    override val id: String,
    override val label: String,
    override val note: String?,
    val value: String,
    val english: String = "",
    val secret: Boolean = false,
    val hint: String = "",
    val write: (String) -> Unit,
) : TvSetting

/**
 * 要排顺序的一项：确定 = 页边出来一列（[items] = id → 名字，按现在的顺序）；列里**确定拿起 → 上下移动 → 确定放下**，放下时把新顺序交给 [write]。
 * [reset]：「恢复默认顺序」（没有就不排这一行）。
 */
class TvOrder(
    override val id: String,
    override val label: String,
    override val note: String?,
    val items: List<Pair<String, String>>,
    val english: String = "",
    val reset: (() -> Unit)? = null,
    val write: (List<String>) -> Unit,
) : TvSetting

/** 这一行接不接焦点。 */
val TvSetting.focusable: Boolean get() = this is TvToggle || this is TvChoice || this is TvAction || this is TvText || this is TvOrder

/** 拿起的那一项往上 / 往下挪一格之后的顺序（到头不动）。纯函数，有单测。 */
fun tvMoved(order: List<String>, held: String, down: Boolean): List<String> {
    val i = order.indexOf(held)
    val j = i + if (down) 1 else -1
    if (i < 0 || j !in order.indices) return order
    return order.toMutableList().also {
        it[i] = order[j]
        it[j] = held
    }
}

/** 密钥在行里怎么写：不露一个字。 */
fun tvSecretSummary(value: String): String = if (value.isEmpty()) "未设置" else "已配置 · ${value.length} 个字符"

/** 数字按 JS 的写法存（整数不带小数点），和上游 / 手机写出来的一样。 */
private fun num(v: Double): JsonPrimitive = if (v == floor(v) && abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

/** 步进量在电视上的那一列值：[min]..[max] 每 [step] 一格（消掉浮点尾巴）。纯函数，有单测。 */
fun stepValues(min: Double, max: Double, step: Double): List<Double> {
    val n = ((max - min) / step).roundToInt()
    return (0..n).map { ((min + it * step) * 1000).roundToInt() / 1000.0 }
}

/** 现在的值在这一列里是第几个（不在档位上 → 最近的一档）。 */
fun nearestIndex(values: List<Double>, current: Double): Int = if (values.isEmpty()) 0 else values.indices.minBy { abs(values[it] - current) }

private fun steps(id: String, label: String, note: String?, values: List<Double>, current: Double, text: (Double) -> String, english: String, write: (Double) -> Unit): TvChoice {
    val at = nearestIndex(values, current)
    return TvChoice(id, label, note, text(current), values.map { TvPick(text(it)) }, setOf(at), english) { write(values[it]) }
}

private fun <T> one(id: String, label: String, note: String?, options: List<Pair<T, String>>, current: T, english: String, notes: (T) -> String? = { null }, write: (T) -> Unit): TvChoice {
    val at = options.indexOfFirst { it.first == current }
    return TvChoice(id, label, note, options.getOrNull(at)?.second ?: "—", options.map { TvPick(it.second, notes(it.first)) }, if (at >= 0) setOf(at) else emptySet(), english) { write(options[it].first) }
}

private fun toggledLangs(options: List<LangOption>, codes: List<String>, key: String): List<String> {
    val keys = keysFromCodes(options, codes)
    val next = if (key in keys) keys - key else keys + key
    return codesFromKeys(options, next, extraCodes(options, codes))
}

private fun langs(id: String, label: String, options: List<LangOption>, codes: List<String>, english: String, write: (List<String>) -> Unit): TvChoice {
    val keys = keysFromCodes(options, codes)
    return TvChoice(
        id, label, if (keys.isEmpty()) "跟随文件" else "优先级：" + keys.mapNotNull { k -> options.firstOrNull { it.key == k }?.label }.joinToString(" › "),
        if (keys.isEmpty()) "跟随文件" else "${keys.size} 种",
        options.map { o -> TvPick(o.label, keys.indexOf(o.key).takeIf { it >= 0 }?.let { "第 ${it + 1} 优先" }) },
        options.indices.filter { options[it].key in keys }.toSet(), english, multi = true,
    ) { write(toggledLangs(options, codes, options[it].key)) }
}

/**
 * 这一分区在电视上的行。[g]：上游的 `AppGraph`（设置存取、更新、导航都在里面）；[store]：画报自己的设置；
 * [secrets]：系统安全存储里的那两条密钥（弹幕服务器地址、TMDB 令牌；还没读出来 = null）；[saveSecret]：把改过的一条写回去（上游 `SecretsStore.save`）。
 */
fun tvSettingRows(
    section: GugaSection,
    settings: JsonObject,
    prefs: GugaPrefs,
    g: AppGraph,
    store: GugaSettingsStore,
    secrets: Secrets? = null,
    saveSecret: (tmdbToken: String?, danmakuApiUrl: String?) -> Unit = { _, _ -> },
    /** 弹幕服务器清单（danmaku/DanmakuServers.kt；null = 还没读出来）和存清单（按先后的地址，空的去掉）。 */
    danmakuServers: List<String>? = null,
    saveDanmakuServers: (List<String>) -> Unit = {},
    /** 备份与还原（账号分区里；backup/BackupController.kt）。null = 不出这一块。 */
    backup: com.gugaart.ui.backup.BackupController? = null,
    /** Trakt（账号分区里）。null = 不出这一块。 */
    trakt: TvTrakt? = null,
): List<TvSetting> = when (section) {
    GugaSection.Look -> {
        val ui = settings.uiSettings
        val perf = listOf(PerfPref.Auto to "自动", PerfPref.High to "高", PerfPref.Mid to "中", PerfPref.Low to "低")
        listOf(
            TvHead("纸面", "PAPER"),
            TvInfo("theme", "主题", "深色", "只有深色：墨黑纸、米白字"),
            TvToggle("spot", "主题色跟随海报", "封面翻到哪一部，片单名和进度线换成那张海报的颜色", prefs.spotColorFollowsCover) { on -> store.update { it.copy(spotColorFollowsCover = on) } },
            TvToggle("paper", "纸纹", null, prefs.paperTexture) { on -> store.update { it.copy(paperTexture = on) } },
            TvHead("玻璃", "GLASS"),
            steps("glass", "玻璃强度", "左侧导航栏的折射强度", stepValues(GLASS_STRENGTH_MIN, GLASS_STRENGTH_MAX, 0.1), ui.glassStrength, ::percentText, "GLASS") { g.settings.patch("ui", "glass" to num(it)) },
            one("perf", "材质", "玻璃的实时折射吃显卡；卡了再降", perf, ui.perf, "MATERIAL", { if (it == PerfPref.Auto) "按设备自动选择" else null }) { g.settings.patch("ui", "perf" to JsonPrimitive(it.id)) },
            TvHead("放映员", "THE PROJECTIONIST"),
            TvToggle("mascot", "咕咕嘎嘎出现在界面上", "放映手记、观影卡、空状态；播放时从不出现", prefs.showMascot) { on -> store.update { withMascot(it, on) } },
            one("notes", "放映手记", null, NotesChoice.entries.map { it to it.label }, notesChoiceOf(prefs), "NOTES") { c -> store.update { withNotes(it, c) } },
        )
    }
    GugaSection.Layout -> {
        val p = settings.uiSettings.interfacePrefs
        fun ui(key: String, v: JsonElement) = g.settings.patch("ui", key to v)
        val hidden = settings.homeHidden
        listOf(
            TvHead("首页", "HOME"),
            TvToggle("homeCarousel", "首页轮播图", "首页封面轮播的海报", p.homeCarousel) { ui("homeCarousel", JsonPrimitive(it)) },
            TvToggle("mediaCount", "媒体计数", "片单里显示部数 / 集数", p.mediaCount) { ui("mediaCount", JsonPrimitive(it)) },
            TvToggle("posterCenter", "海报卡片文字居中", null, p.posterCenter) { ui("posterCenter", JsonPrimitive(it)) },
            TvToggle("coverAutoFlip", "封面自动轮播", "不动的时候每隔几秒换到下一部；一按键就停", prefs.coverAutoFlip) { on -> store.update { it.copy(coverAutoFlip = on) } },
            TvToggle("idleHome", "闲置回首页", "不在首页也不在播放时，10 分钟没有按键就回到封面", prefs.idleHome) { on -> store.update { it.copy(idleHome = on) } },
            TvHead("详情页", "DETAIL"),
            one("detailImage", "详情页图片", null, listOf("poster" to "海报", "backdrop" to "横版剧照"), p.detailImage, "IMAGE") { ui("detailImage", JsonPrimitive(it)) },
            one("episodePageSize", "单集分页数量", "一季超过这么多集就分页", EPISODE_PAGE_SIZES.map { it to it.toString() }, p.episodePageSize, "PAGE SIZE") { ui("episodePageSize", JsonPrimitive(it)) },
            TvHead("首页板块", "SECTIONS"),
            TvOrder(
                "homeOrder", "板块顺序", settings.homeSections.take(4).joinToString(" › ") { HOME_SECTION_LABELS[it] ?: it } + " …",
                settings.homeSections.map { it to (HOME_SECTION_LABELS[it] ?: it) }, "ORDER",
            ) { g.settings.patch("home", "sections" to jsonStrings(it)) },
        ) + settings.homeSections.map { id ->
            TvToggle("home-$id", HOME_SECTION_LABELS[id] ?: id, null, id !in hidden) { on -> g.settings.patch("home", "hidden" to jsonStrings(if (on) hidden - id else (hidden + id).distinct())) }
        }
    }
    GugaSection.Playback -> {
        val p = settings.playbackPrefs
        fun put(key: String, v: JsonElement) = g.settings.patch("playback", key to v)
        val android = g.deps.uiMode != UiMode.Desktop
        val engines = engineChoices(android)
        listOf(
            TvHead("播放引擎", "ENGINE"),
            TvChoice(
                "engine", "首选引擎", if (android) "ExoPlayer 放不了时换 VLC，再换 mpv，最后请服务器转码" else "mpv 放不了时换 VLC，最后请服务器转码",
                engines.firstOrNull { it.id == p.engine }?.label?.substringBefore('（') ?: "自动",
                engines.map { TvPick(it.label.substringBefore('（'), it.label.substringAfter('（', "").removeSuffix("）").takeIf { n -> n.isNotEmpty() }) },
                setOf(engines.indexOfFirst { it.id == p.engine }).filter { it >= 0 }.toSet(), "ENGINE",
            ) { put("engine", JsonPrimitive(engines[it].id)) },
            one("bitrate", "码率上限", "超过时请服务器转码", BITRATE_CHOICES_MBPS.map { it to bitrateLabel(it).substringBefore('（') }, p.maxBitrateMbps, "BITRATE") { put("maxBitrateMbps", JsonPrimitive(it)) },
            TvHead("播放行为", "BEHAVIOUR"),
            TvToggle("autoPlayNext", "自动播放下一集", "片尾出现时倒数", p.autoPlayNext) { put("autoPlayNext", JsonPrimitive(it)) },
            one("skipMarkers", "跳过片头 / 片尾", null, listOf("off" to "关闭", "button" to "显示按钮", "auto" to "自动跳过"), p.skipMarkers, "SKIP") { put("skipMarkers", JsonPrimitive(it)) },
            one("seekStep", "快进 / 快退步长", "长按时逐级加速到 ×3", SEEK_STEP_CHOICES_SEC.map { it to "$it 秒" }, p.seekStepSec, "STEP") { put("seekStepSec", JsonPrimitive(it)) },
            steps("defaultRate", "默认倍速", null, RATE_CHOICES, p.defaultRate, ::scaleText, "SPEED") { put("defaultRate", num(it)) },
        )
    }
    GugaSection.Strm -> {
        val a = StrmSettings.of(settings)
        fun put(key: String, v: JsonElement) = g.settings.patch(STRM_SETTINGS_SECTION, key to v)
        listOf(
            TvHead("直链", "STRM"),
            TvToggle("preferStrm", "优先直连 strm 目标", "跳过 Emby 中转，由播放内核直接打开网盘 / CDN 地址", a.preferStrmTarget) { put("preferStrmTarget", JsonPrimitive(it)) },
            one("proxyMode", "线路", null, listOf("auto" to "自动", "direct" to "始终直连", "proxy" to "始终经代理"), a.strmProxyMode, "ROUTE") { put(STRM_PROXY_MODE_KEY, JsonPrimitive(it)) },
            TvHead("请求头", "HEADERS"),
            TvText("ua", "User-Agent", "网盘 / CDN 认浏览器的时候填；留空 = 播放内核自己的", a.cdnUserAgent, "USER-AGENT", hint = "留空 = 不带") { put("cdnUserAgent", JsonPrimitive(it)) },
            TvText("referer", "Referer", "有的直链要带来源页才放行", a.cdnReferer, "REFERER", hint = "留空 = 不带") { put("cdnReferer", JsonPrimitive(it)) },
        )
    }
    GugaSection.Subs -> {
        val p = settings.playbackPrefs
        fun put(key: String, v: JsonElement) = g.settings.patch("playback", key to v)
        listOf(
            TvHead("音轨", "AUDIO"),
            langs("audioLangs", "首选音轨语言", AUDIO_LANGS, p.audioLangs, "AUDIO") { put("audioLangs", jsonStrings(it)) },
            TvHead("字幕", "SUBTITLES"),
            one("subtitleDefault", "字幕默认", "手动换过字幕，就记成这部片子「上次的选择」", SubtitleDefault.entries.map { it to it.label }, prefs.subtitleDefault, "DEFAULT", { it.note }) { v -> store.update { it.copy(subtitleDefault = v) } },
            one("subtitleMode", "字幕模式", null, SUBTITLE_MODE_CHOICES.map { it.id to it.label.substringBefore('（') }, p.subtitleMode, "MODE", { id ->
                SUBTITLE_MODE_CHOICES.firstOrNull { it.id == id }?.label?.substringAfter('（', "")?.removeSuffix("）")?.takeIf { it.isNotEmpty() }
            }) { put("subtitleMode", JsonPrimitive(it)) },
            langs("subtitleLangs", "首选字幕语言", SUBTITLE_LANGS, p.subtitleLangs, "SUBTITLES") { put("subtitleLangs", jsonStrings(it)) },
        )
    }
    GugaSection.Danmaku -> {
        val dm = settings.danmakuPrefs
        val r = dm.render
        fun put(key: String, v: JsonElement) = g.settings.patch("danmaku", key to v)
        // `render` 是整块替换的：并进存着的那一块里再写（不认识的键原样留着）——和上游、手机的写法一样
        fun render(key: String, v: JsonElement) {
            val cur = ((g.settings.value["danmaku"] as? JsonObject)?.get("render") as? JsonObject) ?: JsonObject(emptyMap())
            put("render", JsonObject(LinkedHashMap(cur).also { it[key] = v }))
        }
        fun mode(key: String, on: Boolean) {
            val cur = (((g.settings.value["danmaku"] as? JsonObject)?.get("render") as? JsonObject)?.get("modes") as? JsonObject) ?: JsonObject(emptyMap())
            render("modes", JsonObject(LinkedHashMap(cur).also { it[key] = JsonPrimitive(on) }))
        }
        listOf(
            // 最上面：弹幕服务器（用户 2026-11-15：「输入弹幕服务器的不要放最下面，放弹幕设置最上面」）
            TvHead("弹幕服务器", "SERVER"),
            *tvDanmakuServerRows(danmakuServers, saveDanmakuServers).toTypedArray(),
            TvText("tmdb", "TMDB 令牌", "匹配更准；保存在系统安全存储中", secrets?.tmdbToken.orEmpty(), "TMDB TOKEN", secret = true) { saveSecret(it, null) },
            TvHead("显示", "DISPLAY"),
            TvToggle("dm-enabled", "显示弹幕", null, r.enabled) { render("enabled", JsonPrimitive(it)) },
            steps("dm-opacity", "不透明度", null, stepValues(0.1, 1.0, 0.05), r.opacity, ::percentText, "OPACITY") { render("opacity", num(it)) },
            steps("dm-fontScale", "字号", null, stepValues(0.5, 2.0, 0.05), r.fontScale, ::scaleText, "SIZE") { render("fontScale", num(it)) },
            steps("dm-speed", "滚动速度", null, stepValues(0.5, 2.0, 0.05), r.speed, ::scaleText, "SPEED") { render("speed", num(it)) },
            TvChoice(
                "dm-area", "显示区域", if (areaChoice(r.area) == null) "现在是 ${percentText(r.area)}" else null, AREA_CHOICES.firstOrNull { it.first == areaChoice(r.area) }?.second ?: percentText(r.area),
                AREA_CHOICES.map { TvPick(it.second) }, setOf(AREA_CHOICES.indexOfFirst { it.first == areaChoice(r.area) }).filter { it >= 0 }.toSet(), "AREA",
            ) { render("area", num(AREA_CHOICES[it].first)) },
            steps("dm-max", "同屏上限", null, stepValues(0.0, 300.0, 10.0), r.maxOnScreen.toDouble(), { if (it == 0.0) "自动" else it.toInt().toString() }, "LIMIT") { render("maxOnScreen", JsonPrimitive(it.toInt())) },
            TvToggle("dm-scroll", "滚动弹幕", null, r.scroll) { mode("scroll", it) },
            TvToggle("dm-top", "顶部弹幕", null, r.top) { mode("top", it) },
            TvToggle("dm-bottom", "底部弹幕", null, r.bottom) { mode("bottom", it) },
            TvHead("样式", "STYLE"),
            one("dm-font", "字体", null, DanmakuFont.entries.map { it to it.label }, r.font, "FONT") { render("font", JsonPrimitive(it.id)) },
            one("dm-weight", "粗细", null, DanmakuWeight.entries.map { it to it.label }, r.weight, "WEIGHT") { render("weight", JsonPrimitive(it.id)) },
            TvHead("匹配", "MATCHING"),
            TvToggle("autoMatch", "播放时自动匹配弹幕", "按 TMDB 身份 + 集号匹配", dm.autoMatch) { put("autoMatch", JsonPrimitive(it)) },
            TvOrder(
                "dm-sources", "来源优先级", dm.sourcePriority.take(3).joinToString(" › ") { DANMAKU_SOURCE_NAMES[it] ?: it },
                dm.sourcePriority.map { it to (DANMAKU_SOURCE_NAMES[it] ?: it) }, "SOURCES",
                reset = { put("sourcePriority", jsonStrings(DEFAULT_DANMAKU_SOURCES)) },
            ) { put("sourcePriority", jsonStrings(it)) },
            steps("dm-offset", "时间偏移", null, stepValues(-30.0, 30.0, 0.5), r.offsetMs / 1000.0, { "${if (it > 0) "+" else ""}$it 秒" }, "OFFSET") { render("offsetMs", JsonPrimitive((it * 1000).toInt())) },
            TvHead("性能", "PERFORMANCE"),
            one("dm-perfTier", "性能档位", null, listOf("auto" to "自动", "low" to "低", "mid" to "中", "high" to "高"), r.perfTier, "TIER") { render("perfTier", JsonPrimitive(it)) },
            one("dm-fpsCap", "帧率上限", null, listOf("auto", "30", "60", "90", "120").map { it to if (it == "auto") "自动" else "$it 帧" }, r.fpsCap, "FPS") {
                render("fpsCap", if (it == "auto") JsonPrimitive("auto") else JsonPrimitive(it.toInt()))
            },
            TvAction("dm-reset", "恢复默认的弹幕显示", null, "恢复默认", confirm = "再按一次确定：恢复默认") { put("render", DEFAULT_DANMAKU_RENDER_JSON) },
            TvInfo("dm-blockWords", "屏蔽词", if (r.blockWords.isEmpty()) "没有" else "${r.blockWords.size} 个", "要输中文：请在手机或电脑上改"),
        )
    }
    GugaSection.Cache -> listOf(
        TvHead("占用", "USAGE"),
        TvAction("cache-queries", "界面数据缓存", "首页、片库上次的内容", "清理") { g.queries.clear() },
        TvHead("维护", "MAINTENANCE"),
        TvAction("reset-settings", "恢复默认设置", "播放、弹幕、外观等偏好将恢复为默认值；账号和缓存不受影响", "恢复默认", confirm = "再按一次确定：恢复默认设置") { g.settings.reset() },
    )
    GugaSection.Account -> {
        val s = g.session.session.value
        listOf(
            TvHead("当前账号", "SIGNED IN"),
            TvInfo("account-name", s?.userName ?: "未登录", s?.serverName, "已登录".takeIf { s != null }),
            TvAction("account-switch", "切换账号 / 服务器", "已保存的账号在登录页里选", "切换") { g.nav.navigate(Route.Login(switch = true)) },
        ) + (trakt?.let(::tvTraktRows) ?: emptyList()) + (backup?.let(::tvBackupRows) ?: emptyList())
    }
    GugaSection.About -> listOfNotNull(
        TvHead("更新", "UPDATE"),
        if (g.update.enabled) TvAction("about-check", "检查更新", g.update.status.value.lastResult.ifEmpty { "还没有检查过" }, "检查") { com.gugaart.ui.screens.update.UpdateConsent.checkByHand(g) } else TvInfo("about-check", "检查更新", "未开启", "这一版不检查更新"),
        // 不自动下载、不自动安装（用户 2026-11-15）：没有「自动下载更新」开关了
        if (g.update.enabled) TvInfo("update-how", "怎么更新", "点了才下，确认了才装", "有新版时出号外：按「立即更新」才下载，下好了按「现在安装」才装") else null,
        TvInfo("channel", "更新通道", g.update.channelLabel),
        TvHead("这台设备", "DEVICE"),
        TvInfo("version", "版本", g.deps.appVersion),
        TvInfo("platform", "平台", g.deps.platformLabel),
        TvInfo("device", "设备名称", g.deps.identity.device),
        TvInfo("mode", "界面模式", "电视", "由设备决定"),
        TvInfo("secure", "令牌存储", g.deps.secureMode()),
    )
}

/**
 * 电视上的弹幕服务器清单（多个弹幕服务器：播放时同时去匹配，谁先匹配上用谁）：每台一行（键盘那一屏改；存成空 = 去掉这一台），
 * 下面「再加一个弹幕服务器」。第一台的 id 沿用 `dm-apiUrl`。
 */
private fun tvDanmakuServerRows(urls: List<String>?, save: (List<String>) -> Unit): List<TvSetting> {
    val list = urls.orEmpty()
    val rows = ArrayList<TvSetting>()
    if (list.isEmpty()) {
        rows += TvText("dm-apiUrl", "弹幕服务器 API 地址", "令牌在路径里；键盘那一屏默认打码", "", "DANMAKU API", secret = true, hint = "https://…") { save(listOf(it)) }
        return rows
    }
    list.forEachIndexed { i, url ->
        rows += TvText(
            if (i == 0) "dm-apiUrl" else "dm-apiUrl-${i + 1}",
            if (list.size > 1) "弹幕服务器 ${i + 1}" else "弹幕服务器 API 地址",
            if (i == 0) "令牌在路径里；键盘那一屏默认打码" else "存成空 = 去掉这一台",
            url, "DANMAKU API", secret = true, hint = "https://…",
        ) { v -> save(list.toMutableList().also { it[i] = v }) }
    }
    if (list.size < com.gugaart.ui.danmaku.MAX_DANMAKU_SERVERS) {
        rows += TvText("dm-apiUrl-new", "再加一个弹幕服务器", "可以填好几台：播放时同时去匹配，哪台先匹配上就先用哪台", "", "DANMAKU API", secret = true, hint = "https://…") { v ->
            if (v.isNotBlank()) save(list + v)
        }
    }
    return rows
}

/**
 * 电视上的备份与还原（用户 2026-11-15：「配置备份还原功能，一份配置支持3端」「支持本地和webdav」）：电视上一般没有文件选择器，
 * 主要用 WebDAV（地址 / 用户名 / 密码在键盘那一屏填）；壳给了文件对话框才多出「导出 / 从文件还原」。还原先读出来给人看是哪一份，确认了才覆盖。
 */
private fun tvBackupRows(c: com.gugaart.ui.backup.BackupController): List<TvSetting> {
    val dav = c.webdav
    val idle = c.busy == null
    val rows = ArrayList<TvSetting>()
    rows += TvHead("备份与还原", "BACKUP")
    rows += TvInfo("bk-what", "备份里有什么", "设置 · 弹幕服务器 · Emby 地址", "不带登录令牌，还原后要重新登录；手机、电视、电脑通用")
    val p = c.pending
    if (p != null) {
        rows += TvAction("bk-apply", "用这份备份覆盖这台设备的设置", com.gugaart.ui.backup.pendingText(p), "还原", confirm = "再按一次确定：覆盖这台设备的设置") { c.apply() }
        rows += TvAction("bk-cancel", "不还原", null, "取消") { c.cancel() }
    }
    rows += TvText("bk-dav-url", "WebDAV 地址", "文件夹地址；备份存成里面的 gugaart-backup.json", dav.url, "WEBDAV", hint = "https://…") { c.saveWebdav(dav.copy(url = it)) }
    rows += TvText("bk-dav-user", "WebDAV 用户名", null, dav.user, "USER") { c.saveWebdav(dav.copy(user = it)) }
    rows += TvText("bk-dav-pass", "WebDAV 密码", "存在系统安全存储里", dav.password, "PASSWORD", secret = true) { c.saveWebdav(dav.copy(password = it)) }
    if (dav.complete) {
        rows += TvAction("bk-dav-up", "备份到 WebDAV", c.message, if (c.busy == "dav-up") "备份中…" else "备份") { if (idle) c.upload() }
        if (p == null) rows += TvAction("bk-dav-down", "从 WebDAV 还原", "先读出来给你看是哪一份", if (c.busy == "dav-down") "读取中…" else "读取") { if (idle) c.download() }
        rows += TvAction("bk-dav-test", "测试 WebDAV 连接", null, if (c.busy == "dav-test") "测试中…" else "测试") { if (idle) c.testWebdav() }
    } else if (c.message != null) {
        rows += TvInfo("bk-message", "备份", c.message)
    }
    if (c.files != null) {
        rows += TvAction("bk-file-out", "导出到文件", null, "导出") { if (idle) c.exportFile() }
        rows += TvAction("bk-file-in", "从文件还原", null, "选文件") { if (idle) c.importFile() }
    }
    return rows
}

/** 电视设置页上 Trakt 那一块要的状态和动作（TvSettingsScreen 收集好交进来）。 */
class TvTrakt(
    val state: com.gugaart.ui.trakt.TraktState,
    val prefs: com.gugaart.ui.trakt.TraktPrefs,
    val syncMessage: String?,
    val message: String?,
    val connect: () -> Unit,
    val cancel: () -> Unit,
    val disconnect: () -> Unit,
    val setPrefs: (com.gugaart.ui.trakt.TraktPrefs) -> Unit,
    val sync: () -> Unit,
    /** 追更日历（null = 没连 / 还在取）；[openSeries] = 打开库里的那部剧。 */
    val calendar: List<com.gugaart.ui.trakt.CalendarDay>? = null,
    val openSeries: (String) -> Unit = {},
)

/**
 * 电视上的 Trakt（用户 2026-11-15：「添加 连接trakt功能」）：连接 = 设备码，码就写在这一行里，用手机打开 trakt.tv/activate 输入；
 * 连上后：看的时候记、同步到 Emby、打开应用时自动同步。
 */
private fun tvTraktRows(t: TvTrakt): List<TvSetting> {
    val rows = ArrayList<TvSetting>()
    rows += TvHead("Trakt", "TRAKT")
    when (val s = t.state) {
        com.gugaart.ui.trakt.TraktState.Unavailable -> rows += TvInfo("trakt-unavailable", "连接 Trakt", "连不了", "这一版没有带 Trakt 应用")
        com.gugaart.ui.trakt.TraktState.Disconnected -> rows += TvAction("trakt-connect", "连接 Trakt", t.message ?: "看的时候自动记到 Trakt；Trakt 看过的可以同步回 Emby", "连接") { t.connect() }
        is com.gugaart.ui.trakt.TraktState.Expired -> rows += TvAction("trakt-expired", "Trakt 的授权过期了", t.message ?: ((s.user?.let { "@$it · " } ?: "") + "重新连接一次就好"), "重新连接") { t.connect() }
        is com.gugaart.ui.trakt.TraktState.Waiting -> rows += TvAction(
            "trakt-waiting",
            "在手机上打开 ${s.code.verificationUrl.removePrefix("https://")}，输入：${s.code.userCode}",
            "输好了这里会自己连上；${s.code.expiresInSec / 60} 分钟内有效",
            "取消",
        ) { t.cancel() }
        is com.gugaart.ui.trakt.TraktState.Connected -> {
            rows += TvAction("trakt-connected", "已连接 Trakt", s.user?.let { "@$it" }, "断开", confirm = "再按一次确定：断开 Trakt") { t.disconnect() }
            rows += TvToggle("trakt-scrobble", "看的时候记到 Trakt", "看到 80% 以上 Trakt 记为看过", t.prefs.scrobble) { t.setPrefs(t.prefs.copy(scrobble = it)) }
            rows += TvAction("trakt-sync", "Trakt 看过的同步到 Emby", t.syncMessage ?: "只把 Emby 里还没看的标成已看", if (t.syncMessage == "同步中…") "同步中…" else "同步") { if (t.syncMessage != "同步中…") t.sync() }
            rows += TvToggle("trakt-autosync", "打开应用时自动同步", null, t.prefs.autoSync) { t.setPrefs(t.prefs.copy(autoSync = it)) }
            // 追更日历：一集一行，确定 = 打开库里的那部剧
            rows += TvHead("追更日历", "TRAKT CALENDAR")
            val days = t.calendar
            when {
                days == null -> rows += TvInfo("trakt-cal-loading", "追更日历", "正在读 Trakt…")
                days.isEmpty() -> rows += TvInfo("trakt-cal-empty", "追更日历", "接下来两周没有", "在剧集详情页按「追更」，新的一集就会排在这里")
                else -> for (day in days) for ((i, e) in day.entries.withIndex()) {
                    val line = listOfNotNull(e.season?.let { "第 $it 季" }, e.number?.let { "第 $it 集" }, e.title).joinToString(" · ")
                    val sid = e.seriesId
                    rows += TvAction("trakt-cal-${day.epochDay}-$i", "${day.label} ${e.time} · ${e.show}", line.ifEmpty { null }, if (sid != null) "打开" else "库里没有") { if (sid != null) t.openSeries(sid) }
                }
            }
        }
    }
    return rows
}
