package com.gugaart.ui.player

import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.data.SubtitleDefault
import com.gugugaga.app.platform.LocalStore
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * 字幕自动选择（用户 2026-11-15：「有字幕的片不加载字幕，没字幕的片自动加载中文。也可以根据用户自己上次的选择（默认跟随用户上次的习惯）」）。
 *
 * 这一层全是纯函数 + 一个小的本地记录；播放的事还是上游 `PlayController.selectSubtitle` 在做。四档见 [SubtitleDefault]。
 * 「有字幕的片」= 对白是中文的片子：国产剧 / 电影大多把字幕烧在画面里，再开一条外挂字幕就叠了两层。
 */

/** 一条字幕轨里和选择有关的东西（上游 `MediaStream` 的 Index / Language / Title + DisplayTitle）。[external] = 外挂（内嵌 = false）。 */
data class SubTrack(val index: Int, val language: String? = null, val title: String? = null, val external: Boolean = false)

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

/**
 * 对白是不是中文：音轨语言是中文；或者音轨没标语言（und / 空）而片子的出品地是华语地区（Emby 的 `ProductionLocations`）。
 */
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

/** 「智能」：对白是中文且只有外挂字幕 = 不开（画面多半烧了字幕，用户 2026-11-15）；带内嵌字幕 = 内嵌不是烧录，照偏好开（1.3.1）；否则最合适的中文字幕。 */
fun smartSubtitle(audioLanguage: String?, productionLocations: List<String>, subs: List<SubTrack>): Int? = when {
    !dialogueIsChinese(audioLanguage, productionLocations) -> bestChineseSubtitle(subs)?.index
    subs.any { !it.external } -> bestChineseSubtitle(subs.filter { !it.external })?.index
    else -> null
}

/**
 * 这一条该开哪条字幕。[remembered] = 这部剧 / 电影上次的选择（没有记录 = null）。
 * 跟随上次：上次关 = 关；上次开的那条在这一集里还找得到（语言和标题都一样，或者同一类中文字幕、同一种语言）= 开它；找不到 / 没有记录 = 按「智能」。
 */
fun pickSubtitle(mode: SubtitleDefault, remembered: SubChoice?, audioLanguage: String?, productionLocations: List<String>, subs: List<SubTrack>): SubPick = when (mode) {
    SubtitleDefault.Off -> SubPick(null, "设置")
    SubtitleDefault.Chinese -> SubPick(bestChineseSubtitle(subs)?.index, "设置")
    SubtitleDefault.Smart -> SubPick(smartSubtitle(audioLanguage, productionLocations, subs), "自动")
    SubtitleDefault.Follow -> when {
        remembered == null -> SubPick(smartSubtitle(audioLanguage, productionLocations, subs), "自动")
        remembered.off -> SubPick(null, "上次")
        else -> {
            val was = SubTrack(-1, remembered.language, remembered.title)
            val kind = chineseKindOf(was)
            val same = subs.firstOrNull { it.language == remembered.language && it.title == remembered.title }
                ?: kind?.let { k -> subs.firstOrNull { chineseKindOf(it) == k } }
                ?: subs.firstOrNull { kind == null && !remembered.language.isNullOrEmpty() && it.language == remembered.language }
            if (same != null) SubPick(same.index, "上次") else SubPick(smartSubtitle(audioLanguage, productionLocations, subs), "自动")
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
