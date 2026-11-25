package com.gugaart.ui.subtitle

import com.gugugaga.core.encodeURIComponent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/*
 * 三路字幕源：请求怎么发、回来的怎么读、合起来怎么排（docs/subtitle-search.md §1、§2）。解析和排序都是纯函数，
 * 用录下来的真实返回做单测（ui/src/commonTest/resources/subtitles/）。
 *
 * 迅雷 / 射手两家的接口地址、参数、过滤和排序规则移植自 MeiamSubtitles（https://github.com/91270/MeiamSubtitles ，提交 b0427939，
 * Apache License 2.0，Copyright Meiam）的 ThunderProvider.cs / ShooterProvider.cs。改动：只发文件名（不发服务器上的完整路径）；
 * 迅雷多用回包里的 `duration` 排序；结果并成一张单子统一排（[rankHits]）。
 */

/** 结果从哪来。[order]：同等条件下的先后。 */
enum class SubSource(val label: String, val order: Int) {
    Server("服务器", 0),
    Shooter("射手", 1),
    Thunder("迅雷", 2),
}

/** 怎么配上的。 */
enum class SubMatch(val label: String) {
    Fingerprint("指纹精确"),
    FileName("文件名"),
    Server("服务器"),
}

/** 字幕的语言（行上写 [short]；[rank] 小的排前面）。 */
enum class SubLang(val short: String, val rank: Int) {
    Simplified("简", 0),
    SimplifiedBilingual("简英", 1),
    Traditional("繁", 2),
    TraditionalBilingual("繁英", 3),
    Chinese("中", 4),
    English("英", 5),
    Japanese("日", 6),
    Korean("韩", 6),
    Unknown("未知", 7),
}

/** 搜哪种语言的字幕。[emby] = 发给 Emby 的语言（ISO 639-2）；迅雷只有中文。 */
enum class SearchLang(val label: String, val emby: String, val shooter: String) {
    Chinese("中文", "chi", "chn"),
    English("英文", "eng", "eng"),
}

/** 一条结果。[key] 在一次搜索里唯一（行的 testTag、去重、记「正在用」都用它）。 */
data class SubtitleHit(
    val key: String,
    val source: SubSource,
    val name: String,
    val lang: SubLang,
    /** 显示的格式（SRT / ASS / SSA…，服务器的原样大写）。 */
    val format: String,
    val match: SubMatch,
    /** 迅雷：这份字幕覆盖的时长（毫秒）。 */
    val durationMs: Long? = null,
    /** 服务器源：插件名（MeiamSub.Thunder、Open Subtitles…）。 */
    val provider: String? = null,
    /** 迅雷 / 射手：下载地址。 */
    val url: String? = null,
    /** 服务器源：`RemoteSubtitleInfo.Id`。 */
    val serverId: String? = null,
    /** 服务器源：`RemoteSubtitleInfo.DownloadCount`（别家没有 = null）。智能匹配按它加一点分。 */
    val downloadCount: Int? = null,
) {
    /** 选了能不能存进 Emby（只有服务器源能：这台 Emby 没有上传字幕的接口，§4.1）。 */
    val toEmby: Boolean get() = source == SubSource.Server

    /** 下载回来按什么格式校验（迅雷 / 射手）。 */
    val subFormat: SubFormat? get() = SubFormat.of(format)

    /** 行上的小字：「简 · SRT · 迅雷 · 文件名 · 41:59」。 */
    fun meta(): String = listOfNotNull(
        lang.short,
        format.uppercase().takeIf { it.isNotBlank() },
        if (source == SubSource.Server) provider?.let { "服务器 · $it" } ?: source.label else source.label,
        match.label,
        durationMs?.let(::clockOf),
    ).joinToString(" · ")
}

private fun clockOf(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    fun two(n: Long) = if (n < 10) "0$n" else "$n"
    return if (h > 0) "$h:${two(m)}:${two(sec)}" else "$m:${two(sec)}"
}

object SubtitleProviders {
    const val THUNDER_API = "https://api-shoulei-ssl.xunlei.com/oracle/subtitle"
    const val SHOOTER_API = "https://www.shooter.cn/api/subapi.php"

    /** 迅雷最多留几条（和 MeiamSub 一样 20）。 */
    const val THUNDER_TAKE = 20

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun thunderUrl(name: String): String = "$THUNDER_API?name=${encodeURIComponent(name)}"

    /** 射手的表单：没有指纹就不发 `filehash`（只按文件名配）。 */
    fun shooterForm(hash: String?, fileName: String, lang: SearchLang): List<Pair<String, String>> = buildList {
        if (!hash.isNullOrEmpty()) add("filehash" to hash)
        add("pathinfo" to fileName)
        add("format" to "json")
        add("lang" to lang.shooter)
    }

    /**
     * 迅雷的回包 → 结果。`code != 0` / 不是 JSON → 空。过滤：name、url 非空，格式是 srt / ass / ssa；按 url 去重（不分大小写）；
     * [cid] 等于结果的 `cid` = 指纹精确（实测这个 `cid` 是字幕文件自己的，几乎配不上，§1.3）；按 MeiamSub 的顺序取前 [THUNDER_TAKE] 条。
     */
    fun parseThunder(body: String, cid: String?): List<SubtitleHit> {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        if ((root["code"] as? JsonPrimitive)?.intOrNull != 0) return emptyList()
        val data = root["data"] as? JsonArray ?: return emptyList()
        val seen = HashSet<String>()
        val rows = data.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            val name = s("name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = s("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val format = SubFormat.of(s("ext")) ?: return@mapNotNull null
            if (!seen.add(url.lowercase())) return@mapNotNull null
            val langs = (o["languages"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            val exact = !cid.isNullOrEmpty() && cid.equals(s("cid"), ignoreCase = true)
            Triple(
                SubtitleHit(
                    key = "thunder:$url",
                    source = SubSource.Thunder,
                    name = name,
                    lang = subtitleLangOf(langs, name, SubLang.Unknown),
                    format = format.ext,
                    match = if (exact) SubMatch.Fingerprint else SubMatch.FileName,
                    durationMs = (o["duration"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
                    url = url,
                ),
                exact,
                langs.any { "简体" in it },
            )
        }
        return rows.sortedWith(compareByDescending<Triple<SubtitleHit, Boolean, Boolean>> { it.second }.thenByDescending { it.third })
            .take(THUNDER_TAKE).map { it.first }
    }

    /**
     * 射手的回包 → 结果。不是 `[` 开头 = 没有（射手没结果时回一个字节或空）。每个 `Files` 一条；[hashSent] = 真的发了指纹（才标「指纹精确」）。
     * 名称：文件名去掉扩展名 + 格式（射手不给字幕名），同一次回了几份就编号。
     */
    fun parseShooter(body: String, fileName: String, lang: SearchLang, hashSent: Boolean): List<SubtitleHit> {
        val t = body.trim()
        if (!t.startsWith("[")) return emptyList()
        val arr = runCatching { json.parseToJsonElement(t) as? JsonArray }.getOrNull() ?: return emptyList()
        val base = fileName.substringBeforeLast('.', fileName)
        val out = ArrayList<SubtitleHit>()
        for (group in arr) {
            val g = group as? JsonObject ?: continue
            val desc = (g["Desc"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            for (f in (g["Files"] as? JsonArray).orEmpty()) {
                val fo = f as? JsonObject ?: continue
                val link = (fo["Link"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                val format = SubFormat.of((fo["Ext"] as? JsonPrimitive)?.contentOrNull) ?: continue
                val n = out.size + 1
                val name = "$base.${format.ext}" + (if (n > 1) " · $n" else "") + (desc?.let { " · $it" } ?: "")
                out += SubtitleHit(
                    key = "shooter:$link",
                    source = SubSource.Shooter,
                    name = name,
                    lang = subtitleLangOf(emptyList(), desc.orEmpty(), if (lang == SearchLang.English) SubLang.English else SubLang.Chinese),
                    format = format.ext,
                    match = if (hashSent) SubMatch.Fingerprint else SubMatch.FileName,
                    url = link,
                )
            }
        }
        return out
    }

    /**
     * Emby 的 `RemoteSubtitleInfo[]` → 结果（服务器源）。MeiamSub 插件的名字带「[MEIAMSUB] … | 语言 | 迅雷」，语言从名字里推；
     * `IsHashMatch` = 指纹精确，否则写「服务器」（服务器按条目信息配的）。
     */
    fun parseEmbyRemote(body: String, lang: SearchLang): List<SubtitleHit> {
        val arr = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
            val id = s("Id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = s("Name")?.takeIf { it.isNotBlank() } ?: id
            val provider = s("ProviderName")?.takeIf { it.isNotBlank() }
            val language = (s("Language") ?: s("ThreeLetterISOLanguageName")).orEmpty().lowercase()
            val fallback = when {
                language.startsWith("en") -> SubLang.English
                language.startsWith("zh") || language == "chi" || language == "zho" -> SubLang.Chinese
                language.startsWith("ja") || language == "jpn" -> SubLang.Japanese
                language.startsWith("ko") || language == "kor" -> SubLang.Korean
                lang == SearchLang.English -> SubLang.English
                else -> SubLang.Chinese
            }
            SubtitleHit(
                key = "server:$id",
                source = SubSource.Server,
                name = cleanServerName(name),
                lang = subtitleLangOf(emptyList(), name, fallback),
                format = s("Format").orEmpty(),
                match = if ((o["IsHashMatch"] as? JsonPrimitive)?.booleanOrNull == true) SubMatch.Fingerprint else SubMatch.Server,
                provider = provider,
                serverId = id,
                downloadCount = (o["DownloadCount"] as? JsonPrimitive)?.intOrNull?.takeIf { it >= 0 },
            )
        }
    }
}

/**
 * 服务器源的名字：MeiamSub 插件写成「[MEIAMSUB] 文件名 | 语言 | 迅雷」，语言和来源小字里都有，名字只留文件名。纯函数，有单测。
 */
fun cleanServerName(name: String): String {
    val n = name.removePrefix("[MEIAMSUB]").trim()
    val m = Regex("""^(.*?) \| [^|]* \| ([^|]{1,16})$""").find(n)
    return m?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: n
}

/**
 * 推语言：迅雷的 `languages`（简体 / 繁体 / 英语 / 简体&英语…）优先，否则看名字（简体 / chs / sc / 简、繁体 / cht / tc / 繁、
 * 双语 / 中英 / 英语）。都看不出 = [fallback]。纯函数，有单测。
 */
fun subtitleLangOf(languages: List<String>, name: String, fallback: SubLang): SubLang {
    val joined = languages.joinToString("&")
    if (joined.isNotBlank()) {
        val simp = "简" in joined
        val trad = "繁" in joined
        val eng = "英" in joined
        return when {
            simp && eng -> SubLang.SimplifiedBilingual
            trad && eng -> SubLang.TraditionalBilingual
            simp -> SubLang.Simplified
            trad -> SubLang.Traditional
            eng -> SubLang.English
            "日" in joined -> SubLang.Japanese
            "韩" in joined -> SubLang.Korean
            else -> fallback
        }
    }
    val n = name.lowercase()
    fun token(vararg t: String) = t.any { Regex("(^|[^a-z])${Regex.escape(it)}([^a-z]|$)").containsMatchIn(n) }
    val simp = "简体" in n || "简中" in n || token("chs", "sc", "gb", "zh-hans", "zh-cn")
    val trad = "繁体" in n || "繁中" in n || "繁體" in n || token("cht", "tc", "big5", "zh-hant", "zh-tw")
    val bi = "双语" in n || "雙語" in n || "中英" in n || "英语" in n || "英文" in n || token("eng", "en", "english") || "&英" in n
    return when {
        simp && bi -> SubLang.SimplifiedBilingual
        trad && bi -> SubLang.TraditionalBilingual
        simp -> SubLang.Simplified
        trad -> SubLang.Traditional
        "简" in n -> SubLang.Simplified
        "繁" in n -> SubLang.Traditional
        bi && fallback == SubLang.Chinese -> SubLang.Chinese
        else -> fallback
    }
}

/**
 * 并成一张单子排序（§2.1）：指纹精确 → 语言 → 时长和片长接近（[runtimeMs]，只有迅雷有；差 ≤ 3 % 算一档）→ 进 Emby 的在前 → 来源。
 * 同一份（同 key）只留一条。纯函数，有单测。
 */
fun rankHits(hits: List<SubtitleHit>, runtimeMs: Long?): List<SubtitleHit> {
    fun durationBand(h: SubtitleHit): Int {
        val d = h.durationMs ?: return 1
        val rt = runtimeMs?.takeIf { it > 0 } ?: return 1
        val diff = kotlin.math.abs(rt - d).toDouble() / rt
        return when {
            diff <= 0.03 -> 0
            diff <= 0.15 -> 1
            else -> 2
        }
    }
    return hits.distinctBy { it.key }.sortedWith(
        compareBy<SubtitleHit> { if (it.match == SubMatch.Fingerprint) 0 else 1 }
            .thenBy { it.lang.rank }
            .thenBy { durationBand(it) }
            .thenBy { if (it.toEmby) 0 else 1 }
            .thenBy { it.source.order },
    )
}
