package com.gugaart.ui.data

import com.gugugaga.app.platform.LocalStore
import com.gugaart.ui.player.ShaderPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** 「开播字幕」的三档（设置 › 字幕与音轨；键 `subtitleDefault`）。 */
enum class SubtitleDefault(val id: String, val label: String) {
    Auto("auto", "自动"),
    Follow("follow", "跟随上次"),
    Off("off", "关闭");

    companion object {
        fun of(id: String?): SubtitleDefault = when (id) {
            "auto", "smart", "chinese" -> Auto
            "follow" -> Follow
            "off" -> Off
            else -> Follow
        }
    }
}

val UPSTREAM_DEFAULT_SUBTITLE_LANGS = listOf("zh-Hans", "zh", "chi", "zho", "zh-Hant")
val DEFAULT_GUGA_SUBTITLE_LANGS = listOf("zh-Hans", "zh", "chi", "zho", "zh-Hant", "en", "eng")

val GUGA_SUBTITLE_LANGS: List<com.gugugaga.app.settings.LangOption> by lazy {
    // 语言名和轨道名同一个写法（TrackNames.languageName：「中文（简体）」「英语」）；它不认识的照用上游的名字
    com.gugugaga.app.settings.SUBTITLE_LANGS.map { o -> o.copy(label = languageName(o.key) ?: o.label) }
}

fun effectiveSubtitleLangs(langs: List<String>, seeded: Boolean): List<String> =
    if (!seeded && langs == UPSTREAM_DEFAULT_SUBTITLE_LANGS) DEFAULT_GUGA_SUBTITLE_LANGS else langs

/** 「咕咕嘎嘎出现在版面上」的三档。 */
enum class MascotPref(val id: String) {
    On("on"), Subtle("subtle"), Off("off");

    companion object {
        fun of(id: String?): MascotPref = entries.firstOrNull { it.id == id } ?: On
    }
}

/**
 * 一个片库上次用的筛选 / 排序（1.3.1：进详情返回、重开应用都还在；「全部重置」回到默认）。
 * 存字符串（题材名 / 年代标签 / 状态枚举名 / 排序枚举名），读不懂的项丢回默认；不碰上游的键。
 */
data class WallPref(
    val genres: Set<String> = emptySet(),
    val decades: Set<String> = emptySet(),
    val states: Set<String> = emptySet(),
    val sort: String? = null,
    val minRating: Int? = null,
) {
    companion object {
        fun parse(o: JsonObject?): WallPref {
            if (o == null) return WallPref()
            fun strs(k: String): Set<String> = (o[k] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }?.toSet().orEmpty()
            return WallPref(
                genres = strs("genres"),
                decades = strs("decades"),
                states = strs("states"),
                sort = (o["sort"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                minRating = (o["minRating"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull,
            )
        }
    }

    fun encode(): JsonObject = buildJsonObject {
        put("genres", buildJsonArray { genres.forEach { add(JsonPrimitive(it)) } })
        put("decades", buildJsonArray { decades.forEach { add(JsonPrimitive(it)) } })
        put("states", buildJsonArray { states.forEach { add(JsonPrimitive(it)) } })
        sort?.let { put("sort", it) }
        minRating?.let { put("minRating", it) }
    }
}

/**
 * 画报自己的设置（docs/decisions.md Q4）：存在**独立的键** `gugaart.settings.v1` 里，不碰上游 `gugu.settings.v1` 的任何键。
 * 读不懂的值按默认；未知的键原样保留（以后的版本加了键，旧版本写回时不丢）。
 */
data class GugaPrefs(
    /** 专色跟随封面；关 = 一直用兜底朱红。 */
    val spotColorFollowsCover: Boolean = true,
    /** 她：on = 全量；subtle = 只留署名照、不说话；off = 所有她的格子收起。 */
    val mascot: MascotPref = MascotPref.On,
    /** 主编手记这一格。 */
    val editorNotes: Boolean = true,
    /** 「开播字幕」：自动 / 跟随上次 / 关闭（[SubtitleDefault]）。 */
    val subtitleDefault: SubtitleDefault = SubtitleDefault.Follow,
    /** 设置 › 播放「默认着色器」：关（默认）/ Anime4K 模式 A/B/C / FSR（[ShaderPreset]）。 */
    val defaultShader: ShaderPreset = ShaderPreset.Off,
    /** 按部记住的着色器（键 = seriesId ?: itemId，[shaderItemKey]）：不全局沿用到别的片子。 */
    val itemShaders: Map<String, ShaderPreset> = emptyMap(),
    /** 闲置回首页（电脑、电视）：null = 按端默认（电脑 / Mac 关，电视开）；非 null = 用户存下的选择。 */
    val idleHome: Boolean? = null,
    val coverAutoFlip: Boolean = true,
    /** 弹幕的画报默认值（区域 10 %、字号 150 %）已经写过一次了（之后用户怎么改都不再动）。 */
    val danmakuSeeded: Boolean = false,
    /** 首选字幕语言默认（中文简体 → 中文繁体 → 英语）已经写过一次了。 */
    val subLangsSeeded: Boolean = false,
    /** 第一次进首页时「目录在下面」的提示已经看过了。 */
    val tocHintSeen: Boolean = false,
    /** 每个片库上次的筛选 / 排序（键 = 片库 id；1.3.1）。 */
    val wall: Map<String, WallPref> = emptyMap(),
    /**
     * 字幕搜索也问迅雷 / 射手（1.4.0，docs/subtitle-search.md §6）：会把文件名和文件指纹发给这两家；关 = 只用服务器的字幕源。
     * 只有用户点「搜索字幕…」才会发请求。
     */
    val subtitleOnline: Boolean = true,
    /**
     * 字幕智能匹配的「自动加载」（1.4.1 P2，docs/subtitle-search.md §2.4）：开播时这一集没有中文字幕、
     * 而且「最合适」那条是**指纹精确**，就自动加载它（服务器源 = 进 Emby，迅雷 / 射手 = 仅本次）。
     * **默认关**：开了才会在开播时自己搜一次（只发文件名和指纹，和手动搜一样）。
     */
    val subtitleAutoBest: Boolean = false,
    /** 字幕样式（1.4.1：字号、粗体、字体、描边、阴影、位置、是否改 ASS 自带样式；[SubStylePrefs]）。 */
    val subStyle: SubStylePrefs = SubStylePrefs(),
    /**
     * 弹幕用上游 `:danmaku` 没有的那几款（[StyleFont] 里 `danmaku == null` 的：思源宋体、站酷快乐体）时记在这里（它的 id）；
     * null = 用上游的键 `danmaku.render.font`。导入的字体另有自己的键（[ImportedFontStore]），选中导入的时这里清空。
     */
    val danmakuFont: String? = null,
    /** 片尾自动播下一集（审计 P1-3）：进了片尾标记倒数 10 秒接下一集。没有片尾标记的片子不受它管（播完照旧接，跟「自动播放下一集」）。 */
    val creditsAutoNext: Boolean = true,
) {
    /** 她的格子出不出现。 */
    val showMascot: Boolean get() = mascot != MascotPref.Off
    /** 她说不说话（手记 / 按语那一行字）。 */
    val mascotSpeaks: Boolean get() = mascot == MascotPref.On && editorNotes

    /** 这一端实际的「闲置回首页」开关：存过按存下的，没存过电脑 / Mac 默认关、电视保持开（P2-16）。 */
    fun idleHomeFor(mode: com.gugugaga.ds.theme.UiMode): Boolean = idleHome ?: (mode == com.gugugaga.ds.theme.UiMode.Tv)

    /** 这部片实际的着色器预设：记过按这部片自己的，没记过用设置里的默认着色器（P2-20）。 */
    fun shaderFor(itemKey: String?): ShaderPreset = itemKey?.let { itemShaders[it] } ?: defaultShader

    /** 更新这一部片的着色器（不动默认着色器也不影响别的片子）。 */
    fun withItemShader(itemKey: String, preset: ShaderPreset): GugaPrefs = copy(
        itemShaders = LinkedHashMap(itemShaders).apply {
            remove(itemKey)
            put(itemKey, preset)
        },
    )

    companion object {
        fun parse(raw: String?): GugaPrefs {
            val o = parseObject(raw) ?: return GugaPrefs()
            fun bool(k: String, d: Boolean) = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: d
            return GugaPrefs(
                spotColorFollowsCover = bool("spotColorFollowsCover", true),
                mascot = MascotPref.of((o["mascot"] as? JsonPrimitive)?.takeIf { it.isString }?.content),
                editorNotes = bool("editorNotes", true),
                subtitleDefault = SubtitleDefault.of((o["subtitleDefault"] as? JsonPrimitive)?.takeIf { it.isString }?.content),
                defaultShader = ShaderPreset.of((o["defaultShader"] as? JsonPrimitive)?.takeIf { it.isString }?.content),
                itemShaders = (o["itemShaders"] as? JsonObject)?.mapNotNull { (k, v) ->
                    val id = (v as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
                    val preset = ShaderPreset.entries.firstOrNull { it.id == id } ?: return@mapNotNull null
                    k to preset
                }?.toMap().orEmpty(),
                idleHome = (o["idleHome"] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull,
                coverAutoFlip = bool("coverAutoFlip", true),
                danmakuSeeded = bool("danmakuSeeded", false),
                subLangsSeeded = bool("subLangsSeeded", false),
                tocHintSeen = bool("tocHintSeen", false),
                wall = (o["wall"] as? JsonObject)?.mapValues { (_, v) -> WallPref.parse(v as? JsonObject) }.orEmpty(),
                subtitleOnline = bool("subtitleOnline", true),
                subtitleAutoBest = bool("subtitleAutoBest", false),
                subStyle = SubStylePrefs.parse(o["subStyle"] as? JsonObject),
                creditsAutoNext = bool("creditsAutoNext", true),
                danmakuFont = (o["danmakuFont"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { id -> StyleFont.of(id)?.let { it.danmaku == null } == true },
            )
        }

        internal fun parseObject(raw: String?): JsonObject? = try {
            raw?.takeIf { it.isNotBlank() }?.let { Json.parseToJsonElement(it).jsonObject }
        } catch (_: Exception) {
            null
        }
    }

    /** 写回：在 [previous]（存着的原文）上只改自己认识的键。 */
    fun encode(previous: String? = null): String = buildJsonObject {
        // 认识的键下面都会重写；nullable / 空映射拿掉，不能留着旧值
        parseObject(previous)?.forEach { (k, v) -> if (k != "danmakuFont" && k != "itemShaders" && k != "idleHome") put(k, v) }
        put("spotColorFollowsCover", spotColorFollowsCover)
        put("mascot", mascot.id)
        put("editorNotes", editorNotes)
        put("subtitleDefault", subtitleDefault.id)
        put("defaultShader", defaultShader.id)
        if (itemShaders.isNotEmpty()) put("itemShaders", buildJsonObject { itemShaders.forEach { (k, v) -> put(k, v.id) } })
        idleHome?.let { put("idleHome", it) }
        put("coverAutoFlip", coverAutoFlip)
        put("danmakuSeeded", danmakuSeeded)
        put("subLangsSeeded", subLangsSeeded)
        put("tocHintSeen", tocHintSeen)
        put("wall", buildJsonObject { wall.forEach { (k, v) -> put(k, v.encode()) } })
        put("subtitleOnline", subtitleOnline)
        put("subtitleAutoBest", subtitleAutoBest)
        put("subStyle", subStyle.encode())
        put("creditsAutoNext", creditsAutoNext)
        danmakuFont?.let { put("danmakuFont", it) }
    }.toString()
}


/** 按「这一部」记住着色器时的键：剧集按整部（seriesId），电影按条目（itemId）。 */
fun shaderItemKey(seriesId: String?, itemId: String): String =
    seriesId?.takeIf { it.isNotBlank() } ?: itemId
/** 期号（报头的 `VOL.`）：本地计数，每天第一次打开 +1（docs/fabricated-content.md；decisions Q4「期号存本地」）。 */
data class Issue(val volume: Int, val day: String) {
    companion object {
        const val FIRST = 1

        fun parse(raw: String?): Issue? {
            val o = GugaPrefs.parseObject(raw) ?: return null
            val v = (o["volume"] as? JsonPrimitive)?.intOrNull ?: return null
            val d = (o["day"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            return Issue(v.coerceAtLeast(FIRST), d)
        }

        /** 今天（[today] = `YYYY-MM-DD`）的期号：没存过 → 第 1 期；换了一天 → +1；同一天 → 不变。 */
        fun next(stored: Issue?, today: String): Issue = when {
            stored == null -> Issue(FIRST, today)
            stored.day == today -> stored
            else -> Issue(stored.volume + 1, today)
        }
    }

    fun encode(): String = buildJsonObject {
        put("volume", volume)
        put("day", day)
    }.toString()

    val label: String get() = "VOL. $volume"
}

/** 两个键的读写。壳给的 [LocalStore] 就是上游存设置的那一个（同一个文件 / SharedPreferences，不同的键）。 */
class GugaSettingsStore(private val local: LocalStore) {
    private val _prefs = MutableStateFlow(GugaPrefs.parse(local.get(KEY)))
    val prefs: StateFlow<GugaPrefs> = _prefs

    fun update(change: (GugaPrefs) -> GugaPrefs) {
        val next = change(_prefs.value)
        local.set(KEY, next.encode(local.get(KEY)))
        _prefs.value = next
    }

    /** 今天的期号（会把「今天开过了」记下来）。 */
    fun issue(today: String): Issue {
        val stored = Issue.parse(local.get(ISSUE_KEY))
        val now = Issue.next(stored, today)
        if (now != stored) local.set(ISSUE_KEY, now.encode())
        return now
    }

    companion object {
        const val KEY = "gugaart.settings.v1"
        const val ISSUE_KEY = "gugaart.issue.v1"
    }
}
