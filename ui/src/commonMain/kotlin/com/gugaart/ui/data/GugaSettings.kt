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

/** 「字幕默认」的四档（设置 › 字幕与音轨；键 `subtitleDefault`）。 */
enum class SubtitleDefault(val id: String, val label: String, val note: String) {
    Follow("follow", "跟随上次", "同一部剧 / 电影记住你上次的选择（包括关）；新片子按「智能」"),
    Smart("smart", "智能", "对白是中文的不开字幕；别的自动选中文字幕（简体优先），没有就关"),
    Off("off", "总是关闭", "不加载字幕"),
    Chinese("chinese", "总是中文", "有中文字幕就开（简体优先）");

    companion object {
        fun of(id: String?): SubtitleDefault = entries.firstOrNull { it.id == id } ?: Follow
    }
}

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
    /** 纸纹（素材没出之前只存不画）。 */
    val paperTexture: Boolean = true,
    /** 她：on = 全量；subtle = 只留署名照、不说话；off = 所有她的格子收起。 */
    val mascot: MascotPref = MascotPref.On,
    /** 主编手记这一格。 */
    val editorNotes: Boolean = true,
    /** 「字幕默认」：跟随上次（默认）/ 智能 / 总是关闭 / 总是中文（[SubtitleDefault]）。 */
    val subtitleDefault: SubtitleDefault = SubtitleDefault.Follow,
    /** 画质面板的「着色器增强」：关（默认）/ Anime4K 模式 A/B/C / FSR（[ShaderPreset]）。只在 mpv 系内核出现，下次开播沿用。 */
    val shader: ShaderPreset = ShaderPreset.Off,
    /** 闲置回首页（电脑、电视）：不在首页也不在播放时 10 分钟没有输入，翻回封面。 */
    val idleHome: Boolean = true,
    /** 首页封面自动翻页。 */
    val coverAutoFlip: Boolean = true,
    /** 弹幕的画报默认值（区域 10 %、字号 150 %）已经写过一次了（之后用户怎么改都不再动）。 */
    val danmakuSeeded: Boolean = false,
    /** 第一次进首页时「目录在下面」的提示已经看过了。 */
    val tocHintSeen: Boolean = false,
    /** 每个片库上次的筛选 / 排序（键 = 片库 id；1.3.1）。 */
    val wall: Map<String, WallPref> = emptyMap(),
) {
    /** 她的格子出不出现。 */
    val showMascot: Boolean get() = mascot != MascotPref.Off

    /** 她说不说话（手记 / 按语那一行字）。 */
    val mascotSpeaks: Boolean get() = mascot == MascotPref.On && editorNotes

    companion object {
        fun parse(raw: String?): GugaPrefs {
            val o = parseObject(raw) ?: return GugaPrefs()
            fun bool(k: String, d: Boolean) = (o[k] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull ?: d
            return GugaPrefs(
                spotColorFollowsCover = bool("spotColorFollowsCover", true),
                paperTexture = bool("paperTexture", true),
                mascot = MascotPref.of((o["mascot"] as? JsonPrimitive)?.takeIf { it.isString }?.content),
                editorNotes = bool("editorNotes", true),
                subtitleDefault = SubtitleDefault.of((o["subtitleDefault"] as? JsonPrimitive)?.takeIf { it.isString }?.content),
                shader = ShaderPreset.of((o["shader"] as? JsonPrimitive)?.takeIf { it.isString }?.content),
                idleHome = bool("idleHome", true),
                coverAutoFlip = bool("coverAutoFlip", true),
                danmakuSeeded = bool("danmakuSeeded", false),
                tocHintSeen = bool("tocHintSeen", false),
                wall = (o["wall"] as? JsonObject)?.mapValues { (_, v) -> WallPref.parse(v as? JsonObject) }.orEmpty(),
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
        parseObject(previous)?.forEach { (k, v) -> put(k, v) }
        put("spotColorFollowsCover", spotColorFollowsCover)
        put("paperTexture", paperTexture)
        put("mascot", mascot.id)
        put("editorNotes", editorNotes)
        put("subtitleDefault", subtitleDefault.id)
        put("shader", shader.id)
        put("idleHome", idleHome)
        put("coverAutoFlip", coverAutoFlip)
        put("danmakuSeeded", danmakuSeeded)
        put("tocHintSeen", tocHintSeen)
        put("wall", buildJsonObject { wall.forEach { (k, v) -> put(k, v.encode()) } })
    }.toString()
}

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
