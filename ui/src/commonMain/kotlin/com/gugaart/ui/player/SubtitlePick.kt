package com.gugaart.ui.player

import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.data.SubtitleDefault
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.settings.SUBTITLE_LANGS
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * 开播字幕选择（P0-2）：只认「开播字幕（自动 / 跟随上次 / 关闭）」和「首选字幕语言（有顺序的列表）」两项。
 * 「自动」按首选语言顺序挑第一条有的；都没有就挑默认轨；还是没有就不开。
 */

/** 一条字幕轨里和选择有关的东西（上游 `MediaStream` 的 Index / Language / Title + DisplayTitle / IsDefault）。[external] = 外挂（内嵌 = false）。 */
data class SubTrack(
    val index: Int,
    val language: String? = null,
    val title: String? = null,
    val external: Boolean = false,
    val isDefault: Boolean = false,
)

/** 中文字幕的四类，按用户要的先后：简体 > 繁体 > 中英双语 > 其它中文。 */
enum class ChineseKind(val short: String) { Simplified("简中"), Traditional("繁中"), Bilingual("双语"), Other("中文") }

private val CHINESE_LANGS = setOf("chi", "zho", "cmn", "yue", "zh", "chs", "cht", "zh-cn", "zh-tw", "zh-hk", "zh-hans", "zh-hant", "chinese", "mandarin", "cantonese")
private val CHINESE_PLACES = listOf("china", "hong kong", "taiwan", "macau", "macao", "中国", "香港", "台湾", "臺灣", "澳门", "大陆")

/** 这个语言代码是不是中文（Emby 给的是 ISO 639-2：chi / zho，也见过 cmn、yue、zh-CN）。 */
fun isChineseLanguage(language: String?): Boolean = language?.trim()?.lowercase()?.let { it in CHINESE_LANGS || it.startsWith("zh") } == true

/** 这条字幕是哪一类中文字幕；不是中文 = null。看轨道语言和标题里的「简 / 繁 / chs / cht / sc / tc / 中文 / 双语」。 */
fun chineseKindOf(track: SubTrack): ChineseKind? {
    val title = track.title.orEmpty().lowercase()
    // 英文缩写要整词匹配（「sc」不能命中「description」）
    val words = title.split(Regex("[^a-z0-9\\-]+")).filter { it.isNotEmpty() }.toSet()
    fun word(vararg w: String) = w.any { it in words }
    val simplified = title.contains('简') || title.contains('簡') || word("chs", "sc", "gb", "zh-cn", "zh-hans", "hans", "simplified") || title.contains("chs")
    val traditional = title.contains('繁') || word("cht", "tc", "big5", "zh-tw", "zh-hk", "zh-hant", "hant", "traditional") || title.contains("cht")
    val chineseWord = title.contains("中文") || title.contains("中字") || title.contains("国语") || title.contains("粤语") || word("chinese", "mandarin", "cantonese", "zh", "chi", "zho")
    val english = title.contains('英') || word("eng", "en", "english")
    val bilingual = title.contains("双语") || title.contains("雙語") || word("bilingual") || ((simplified || traditional || chineseWord) && english)
    if (!isChineseLanguage(track.language) && !simplified && !traditional && !chineseWord && !title.contains("双语") && !title.contains("雙語")) return null
    return when {
        bilingual -> ChineseKind.Bilingual
        simplified -> ChineseKind.Simplified
        traditional -> ChineseKind.Traditional
        else -> ChineseKind.Other
    }
}

/** 最合适的中文字幕（简体 > 繁体 > 双语 > 其它中文；同一类取排在前面的）；没有 = null。 */
fun bestChineseSubtitle(subs: List<SubTrack>): SubTrack? =
    subs.mapNotNull { t -> chineseKindOf(t)?.let { it to t } }.minByOrNull { it.first.ordinal }?.second

/** 对白是不是中文（保留给其它调用检查用）。 */
fun dialogueIsChinese(audioLanguage: String?, productionLocations: List<String>): Boolean {
    val lang = audioLanguage?.trim()?.lowercase().orEmpty()
    if (isChineseLanguage(lang)) return true
    if (lang.isNotEmpty() && lang != "und" && lang != "unknown") return false
    return productionLocations.any { p -> CHINESE_PLACES.any { p.lowercase().contains(it) } }
}

/** 记下来的「上次的选择」：关，或者一条轨道的样子（语言 + 标题——同一部剧每一集的轨道序号不一定一样，所以不记序号）。 */
data class SubChoice(val off: Boolean, val language: String? = null, val title: String? = null) {
    fun encode(): String = if (off) "off" else "on|${language.orEmpty()}|${title.orEmpty()}"

    companion object {
        fun of(track: SubTrack?): SubChoice = if (track == null) SubChoice(true) else SubChoice(false, track.language, track.title)

        fun decode(raw: String?): SubChoice? {
            if (raw == null) return null
            if (raw == "off") return SubChoice(true)
            val parts = raw.split('|', limit = 3)
            if (parts.size < 3 || parts[0] != "on") return null
            return SubChoice(false, parts[1].takeIf { it.isNotEmpty() }, parts[2].takeIf { it.isNotEmpty() })
        }
    }
}

/** 选出来的结果：[index] = 要开的字幕轨（null = 关）；[why] = 怎么选来的（顶部信息行写「字幕 简中 · 自动」）。 */
data class SubPick(val index: Int?, val why: String)

private fun isExplicitTraditional(track: SubTrack): Boolean {
    val lang = track.language?.trim()?.lowercase().orEmpty()
    val title = track.title.orEmpty().lowercase()
    return lang in setOf("zh-hant", "zh-tw", "zh-hk", "cht") || title.contains('繁') || title.contains("cht") || title.contains("big5")
}

private fun langMatchesAny(language: String?, codes: List<String>): Boolean {
    val norm = language?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return false
    return codes.any { raw ->
        val c = raw.lowercase()
        norm == c || norm.startsWith("$c-") || norm.startsWith("${c}_")
    }
}

private fun preferredLanguageKeys(preferredLangs: List<String>): List<String> {
    val out = ArrayList<String>()
    for (raw in preferredLangs) {
        val norm = raw.trim().lowercase()
        if (norm.isEmpty()) continue
        val opt = SUBTITLE_LANGS.firstOrNull { o ->
            o.key.equals(norm, ignoreCase = true) || o.codes.any { it.equals(norm, ignoreCase = true) }
        }
        val key = opt?.key?.lowercase() ?: norm
        if (key !in out) out += key
    }
    return out
}

private fun titleMatchesUnknownLang(title: String?, key: String): Boolean {
    val t = title.orEmpty().lowercase()
    if (t.isBlank()) return false
    val words = t.split(Regex("[^a-z0-9\\-]+")).filter { it.isNotEmpty() }.toSet()
    return when (key) {
        "en" -> ("english" in words || "eng" in words || "英文" in t || "英语" in t) && "双语" !in t && "雙語" !in t
        "ja" -> "japanese" in words || "jpn" in words || "日文" in t || "日语" in t || "日本語" in t
        "ko" -> "korean" in words || "kor" in words || "韩文" in t || "韩语" in t || "한국어" in t
        "fr" -> "french" in words || "fra" in words || "fre" in words || "法文" in t || "法语" in t
        "de" -> "german" in words || "deu" in words || "ger" in words || "deutsch" in words || "德文" in t || "德语" in t
        "ru" -> "russian" in words || "rus" in words || "俄文" in t || "俄语" in t
        "es" -> "spanish" in words || "spa" in words || "西班牙" in t
        "th" -> "thai" in words || "tha" in words || "泰文" in t || "泰语" in t
        else -> false
    }
}

private fun matchPreferredTrack(key: String, subs: List<SubTrack>, allowGenericChineseForTraditional: Boolean): SubTrack? = when (key) {
    "zh-hans" -> subs.firstOrNull { chineseKindOf(it) == ChineseKind.Simplified }
        ?: subs.firstOrNull { chineseKindOf(it) == ChineseKind.Bilingual && !isExplicitTraditional(it) }
        ?: subs.firstOrNull { chineseKindOf(it) == ChineseKind.Other }
    "zh-hant" -> subs.firstOrNull { chineseKindOf(it) == ChineseKind.Traditional }
        ?: if (allowGenericChineseForTraditional) {
            subs.firstOrNull { chineseKindOf(it) == ChineseKind.Other }
                ?: subs.firstOrNull { chineseKindOf(it) == ChineseKind.Bilingual }
        } else null
    "yue" -> subs.firstOrNull {
        langMatchesAny(it.language, listOf("yue", "zh-yue")) ||
            it.title.orEmpty().lowercase().let { t -> "粤" in t || "粵" in t || "cantonese" in t }
    }
    else -> {
        val opt = SUBTITLE_LANGS.firstOrNull { it.key.equals(key, ignoreCase = true) }
        val codes = opt?.codes ?: listOf(key)
        subs.firstOrNull { langMatchesAny(it.language, codes) && chineseKindOf(it) == null }
            ?: subs.firstOrNull { langMatchesAny(it.language, codes) }
            ?: subs.firstOrNull {
                val l = it.language?.trim()?.lowercase().orEmpty()
                (l.isEmpty() || l == "und" || l == "unknown") && titleMatchesUnknownLang(it.title, key)
            }
    }
}

/**
 * 「自动」：按 [preferredLangs] 的顺序挑第一条有的；都没有就挑默认轨（[SubTrack.isDefault]）；还是没有就不开（null）。
 */
fun autoSubtitle(preferredLangs: List<String>, subs: List<SubTrack>): Int? {
    if (subs.isEmpty()) return null
    val keys = preferredLanguageKeys(preferredLangs)
    val hasZhHans = "zh-hans" in keys
    for (key in keys) {
        val found = matchPreferredTrack(key, subs, allowGenericChineseForTraditional = !hasZhHans)
        if (found != null) return found.index
    }
    return subs.firstOrNull { it.isDefault }?.index
}

/**
 * 这一条该开哪条字幕。[mode] = 「开播字幕」，[preferredLangs] = 「首选字幕语言」，[remembered] = 这部剧 / 电影上次的选择（没有记录 = null）。
 */
fun pickSubtitle(mode: SubtitleDefault, preferredLangs: List<String>, remembered: SubChoice?, subs: List<SubTrack>): SubPick = when (mode) {
    SubtitleDefault.Off -> SubPick(null, "设置")
    SubtitleDefault.Auto -> SubPick(autoSubtitle(preferredLangs, subs), "自动")
    SubtitleDefault.Follow -> when {
        remembered == null -> SubPick(autoSubtitle(preferredLangs, subs), "自动")
        remembered.off -> SubPick(null, "上次")
        else -> {
            val was = SubTrack(-1, remembered.language, remembered.title)
            val kind = chineseKindOf(was)
            val same = subs.firstOrNull { it.language == remembered.language && it.title == remembered.title }
                ?: kind?.let { k -> subs.firstOrNull { chineseKindOf(it) == k } }
                ?: subs.firstOrNull { kind == null && !remembered.language.isNullOrEmpty() && it.language == remembered.language }
            if (same != null) SubPick(same.index, "上次") else SubPick(autoSubtitle(preferredLangs, subs), "自动")
        }
    }
}
/** 顶部信息行里那一小段：「字幕 简中 · 自动」「字幕 关 · 上次」。[track] = 现在开着的那条（null = 关）；[why] = null 时不写来由。 */
fun subtitleBadge(track: SubTrack?, why: String?): String {
    val what = when {
        track == null -> "关"
        else -> chineseKindOf(track)?.short ?: track.language?.takeIf { it.isNotBlank() && it != "und" } ?: track.title?.take(8) ?: "开"
    }
    return "字幕 $what" + (why?.let { " · $it" } ?: "")
}

/**
 * 每部剧 / 电影上次选的字幕（键 `gugaart.subtitles.v1`，画报自己的；剧按剧记，不按集记）。最多记 [MAX] 部，多了丢掉最早的。
 */
class SubtitleMemory(private val local: LocalStore) {
    private val map = LinkedHashMap<String, String>()

    init {
        GugaPrefs.parseObject(local.get(KEY))?.forEach { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { map[k] = it.content } }
    }

    fun get(key: String): SubChoice? = SubChoice.decode(map[key])

    fun put(key: String, choice: SubChoice) {
        map.remove(key)
        map[key] = choice.encode()
        while (map.size > MAX) map.remove(map.keys.first())
        local.set(KEY, buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString())
    }

    companion object {
        const val KEY = "gugaart.subtitles.v1"
        const val MAX = 300
    }
}
