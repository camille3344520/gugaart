package com.gugaart.ui.subtitle

import com.gugaart.ui.player.cnNumber
import com.gugugaga.danmaku.match.t2s
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * 字幕智能匹配（1.4.1 P2，docs/subtitle-search.md §2.3）：hash 匹配 + 搜索匹配合成一套分数，
 * 分数最高且过门槛的那条 = 「最合适」。这里全是纯函数（commonTest 有单测），只给数据，不画界面：
 * 界面拿 [SmartScore.points] / [SmartScore.reasons] / [SmartScore.isExactHash] 和 [bestOf] 自己排、自己写。
 *
 * 权重表（满分不封顶，门槛 [THRESHOLD] = 40）：
 *
 * | 项 | 分 | 说明 |
 * |---|---|---|
 * | 指纹精确 | +60 | 射手真发了 filehash / 迅雷 CID 命中 / 服务器 IsHashMatch **且片源不是 strm**（见下） |
 * | 编号一致 | +40 | 两边都有番号式编号（ABP-811 = abp00811）且一样；**不一样直接扣到底（0 分）**；编号的字母不算片名 token |
 * | 片名 token | 0…+20 | 按覆盖率给分：交集 / 两边里较短的那一边的 token 数 |
 * | 季集一致 | +12 | 两边都写了季集且一样；**不一样直接扣到底（0 分，不进「最合适」）** |
 * | 年份 | +5 / −8 | 两边都写了年份：一样加，不一样减 |
 * | 分辨率 | +7 / −3 | 2160p / 1080p / 720p / 480p… |
 * | 来源 | +5 / +2 | WEB-DL / BluRay / REMUX / HDTV / DVD…；同一族（web 系）+2 |
 * | 发布组 | +9 | 名字尾巴的 `-GROUP` 或开头的 `[GROUP]` |
 * | 语言 | 0…+12 | 按设置 `subtitleLangs` 的顺序：第一偏好 12，往后每档 −2；简 > 简英 > 繁 > 繁英 > 中 |
 * | 时长 | +8 / +3 / −6 | 迅雷给的 duration 和片长差 ≤ 3 % / ≤ 15 % / 更远（多半只有歌词或片段） |
 * | 格式 | +4 / +3 | SRT +4；ASS / SSA +3 |
 * | 下载数 | +4 / +2 / +1 | 服务器源的 DownloadCount：≥ 1000 / ≥ 100 / > 0 |
 *
 * **strm 上服务器的 IsHashMatch 不能信**（2026-11-25 读 MeiamSubtitles 提交 b0427939 的源码确认）：
 * 两个插件都是 `new FileStream(request.MediaPath, …)` 读**服务器磁盘上的那个文件**。strm 条目的 MediaPath 是 `.strm` 文本本身：
 *  - 迅雷：`.strm` 一般几百字节 < 0xF000 → 算的是**整段文本（那条网址）**的 SHA-1，不是视频的；
 *  - 射手：`.strm` < 8 KB → `ComputeFileHashAsync` 直接返回空串，但回包里**每一条都写死 `IsHashMatch = true`**（ShooterProvider.cs:184）。
 *  - MediaPath 要是 http(s) / smb（Emby 直接给远程地址时）：迅雷跳过算哈希（cid 空 → IsHashMatch 恒 false），射手整个返回空。
 * 所以 strm 上只认**我们自己**经 Range 读 strm 目标算出来的指纹（[Fingerprint]，读的是真视频，不是 .strm 文本）。
 */

/** 一条结果的分数。[points] 过没过门槛看 [passes]；[reasons] 是给界面显示的中文短句；[isExactHash] = 有没有指纹证据。 */
data class SmartScore(
    val points: Int,
    val reasons: List<String>,
    val isExactHash: Boolean,
    /**
     * 这一条写明了是不是这一集（单集才看）：指纹 / 编号对上，或者集号对上、季也对得上（第 2 季起名字里要写季，没写季的多半是第一季的）。
     * 没写明的照样打分、排序，但不当「最合适」（2026-11-28 主理人：头文字D、围棋少年「智能匹配要把每一次都对上」）。电影恒为 true。
     */
    val pinned: Boolean = true,
) {
    val passes: Boolean get() = points >= SmartMatch.THRESHOLD && pinned

    /** 一行小字（界面要用就用，不用就自己拼 [reasons]）。 */
    fun summary(): String = reasons.joinToString(" · ")
}

/** 打过分的一条结果。 */
data class ScoredHit(val hit: SubtitleHit, val score: SmartScore)

/** 从名字里读出来的东西（片名 token / 年份 / 季集 / 分辨率 / 来源 / 发布组）。纯函数 [parseName] 的结果。 */
data class NameParts(
    val titleTokens: Set<String>,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    /** 归一化的分辨率：`2160p` / `1080p` / `720p` / `480p` / `576p` / `4320p`。 */
    val resolution: String?,
    /** 归一化的来源：`remux` / `bluray` / `webdl` / `webrip` / `web` / `hdtv` / `dvd` / `hdtv`… */
    val source: String?,
    /** 发布组（小写）。 */
    val group: String?,
    /** 番号式的编号，规范成「大写字母-去掉前导零的数字」：`abp00811` → `ABP-811`、`FC2-PPV-1234567`（1.5.1 subfix，[codeOf]）。 */
    val code: String? = null,
    /** 写的是「Final Stage / Final Season / 最终季」：这部剧的最后一季（是第几季要看 [SmartFile.lastSeason]）。 */
    val finalSeason: Boolean = false,
    /**
     * 松一点的写法读出来的季集（「围棋少年01」「围棋少年2 05」「头文字D 5 11」「[03]」）：只在给单集打分时用
     * （电影名里的「流浪地球2」是续集号，不是集号）。
     */
    val looseSeason: Int? = null,
    val looseEpisode: Int? = null,
)

/**
 * 打分要知道的「这一集是什么样」。[fileName] = 片源文件名（strm = 目标地址的最后一段）；[name] = 元数据里的片名 / 剧名
 * （文件名不像片名时靠它）；[manualQuery] = 用户在「换个名字搜」里输入的词（给了就**只**拿它当片名 token）。
 * [subtitleLangs] = 设置里的首选字幕语言（上游 `gugu.settings.v1` 的键，代码原样传进来）。
 */
data class SmartFile(
    val fileName: String,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    val runtimeMs: Long? = null,
    /** 片源是 strm / 远程直链：服务器插件的 IsHashMatch 在这一集上不算数（见文件头）。 */
    val isStrm: Boolean = false,
    val subtitleLangs: List<String> = SmartMatch.DEFAULT_LANGS,
    val manualQuery: String? = null,
    /** 这部剧在 Emby 里最大的季号（「Final Stage」的字幕算这一季）；不知道 = null（Final 的字幕就不算任何一季）。 */
    val lastSeason: Int? = null,
    /** 这一季在 Emby 里有几集（只有 1 集的季——头文字D 第 3 季是一部电影——字幕名不写集号也算这一集）。 */
    val seasonEpisodes: Int? = null,
    /** 剧的别名（[SubtitleAliases]：「Initial D」）各自的片名 token；片名分取中文名和各别名里对得最好的那个。手动搜索时不用。 */
    val altTitles: List<Set<String>> = emptyList(),
) {
    /** 手动搜索时片名 token 只从查询词来；否则文件名 + 元数据片名两边都算。 */
    val parts: NameParts = parseName(manualQuery ?: fileName)

    val titleTokens: Set<String> =
        if (manualQuery != null) parts.titleTokens
        else parts.titleTokens + (name?.let { parseName(it).titleTokens } ?: emptySet())

    /** 名字里写了季集就用名字里的（手动搜别的季集时跟着查询词走），否则用元数据的。 */
    val effSeason: Int? = parts.season ?: season
    val effEpisode: Int? = parts.episode ?: episode
    val effYear: Int? = parts.year ?: year

    /** 编号：名字（或手动查询词）里的；没写时用元数据片名里的（手动搜索时只认查询词）。 */
    val effCode: String? = parts.code ?: if (manualQuery == null) name?.let { parseName(it).code } else null

    companion object {
        /** 从一次搜索的目标 + 条目信息拼出来（[langs] = 设置里的 `subtitleLangs`；[query] = 手动输入的词，null = 没改过）。 */
        fun of(
            target: SubtitleTarget,
            langs: List<String> = SmartMatch.DEFAULT_LANGS,
            query: String? = null,
            lastSeason: Int? = null,
            seasonEpisodes: Int? = null,
            aliases: List<String> = emptyList(),
        ): SmartFile = SmartFile(
            fileName = target.fileName,
            name = target.name,
            season = target.season,
            episode = target.episode,
            year = target.year,
            runtimeMs = target.runtimeMs,
            isStrm = target.isStrm,
            subtitleLangs = langs,
            manualQuery = query?.takeIf { it.isNotBlank() && it.trim() != target.nameQuery.trim() },
            lastSeason = lastSeason,
            seasonEpisodes = seasonEpisodes,
            altTitles = aliases.map { parseName(it).titleTokens }.filter { it.isNotEmpty() }.distinct(),
        )
    }
}

object SmartMatch {
    /** 「最合适」的门槛：低于这个分数的不算对得上（宁可不自动加载）。 */
    const val THRESHOLD = 40

    /** 没设首选语言时按上游的默认（简体优先，其次繁体）。 */
    val DEFAULT_LANGS = listOf("zh-Hans", "zh", "chi", "zho", "zh-Hant")

    // ---- 权重（改这里就是改规则；docs/subtitle-search.md §2.3 的表要和这里一致）----
    const val W_HASH = 60

    /** 编号一样（`ABP-811` = `abp00811`）：单凭它就过门槛（1.5.1 subfix）；编号不一样和季集对不上一样，直接 0 分。 */
    const val W_CODE = 40
    const val W_TITLE = 20
    const val W_EPISODE = 12
    const val W_YEAR = 5
    const val W_YEAR_OFF = -8
    const val W_RESOLUTION = 7
    const val W_RESOLUTION_OFF = -3
    const val W_SOURCE = 5
    const val W_SOURCE_FAMILY = 2
    const val W_GROUP = 9
    const val W_LANG_FIRST = 12
    const val W_LANG_STEP = 2
    const val W_LANG_CHINESE_FALLBACK = 3
    const val W_DURATION_CLOSE = 8
    const val W_DURATION_NEAR = 3
    const val W_DURATION_FAR = -6
    const val W_FORMAT_SRT = 4
    const val W_FORMAT_ASS = 3
    const val W_DOWNLOADS_HIGH = 4
    const val W_DOWNLOADS_MID = 2
    const val W_DOWNLOADS_LOW = 1

    /**
     * 给一条结果打分。[file] = 这一集的样子。季集对不上 = 0 分（扣到底，[SmartScore.passes] 一定 false）。
     * 纯函数，有单测。
     */
    fun score(hit: SubtitleHit, file: SmartFile): SmartScore {
        val sub = parseName(hit.name)
        val reasons = ArrayList<String>(6)

        // 1) 指纹：服务器源在 strm 上不算数（MeiamSub 读的是 .strm 文本，射手还无条件写 true）
        val exact = hit.match == SubMatch.Fingerprint && !(hit.source == SubSource.Server && file.isStrm)
        if (hit.match == SubMatch.Fingerprint && !exact) reasons += "服务器标指纹一致，strm 不算"

        // 2) 季集：两边都写了又对不上 = 扣到底。单集才用松写法（「围棋少年01」）；Final Stage = 最后一季（不知道最后是第几季 = 哪一季都不算）
        val isEpisode = file.episode != null
        val subSeason: Int? = sub.season
            ?: (if (sub.finalSeason) file.lastSeason ?: FINAL_UNKNOWN else null)
            ?: (if (isEpisode) sub.looseSeason else null)
        val subEpisode: Int? = sub.episode ?: (if (isEpisode) sub.looseEpisode else null)
        val seasonOff = file.effSeason != null && subSeason != null && file.effSeason != subSeason
        val episodeOff = file.effEpisode != null && subEpisode != null && file.effEpisode != subEpisode
        if (seasonOff || episodeOff) {
            val want = epLabel(file.effSeason, file.effEpisode)
            val got = if (subSeason == FINAL_UNKNOWN) "Final" else epLabel(subSeason, subEpisode)
            return SmartScore(0, listOf("季集对不上（$want ≠ $got）"), exact, pinned = false)
        }
        // 2¼) 正片的一集不要特别篇 / OVA / 外传的字幕（「Battle Stage 2」「Extra Stage」「Legend1」「赛车专辑」）；Emby 那一季本身是特别篇（季 0）时不管
        if (isEpisode && (file.effSeason ?: 1) >= 1 && SIDE_STORY.containsMatchIn(foldName(hit.name)) && !SIDE_STORY.containsMatchIn(foldName(file.fileName))) {
            return SmartScore(0, listOf("不是正片"), exact, pinned = false)
        }
        // 2½) 编号：两边都有又不一样 = 扣到底（abp-811 的文件不要 abp-652 的字幕）
        val fileCode = file.effCode
        if (fileCode != null && sub.code != null && fileCode != sub.code) {
            return SmartScore(0, listOf("编号对不上（$fileCode ≠ ${sub.code}）"), exact, pinned = false)
        }

        var pts = 0
        if (exact) {
            pts += W_HASH
            reasons += if (hit.source == SubSource.Server) "指纹一致（服务器）" else "指纹一致"
        }

        // 3) 片名 token：交集 / 较短那边的大小（中文名和罗马字名混着写时也能对上）
        val theirs = sub.titleTokens
        fun coverageOf(mine: Set<String>): Double {
            val shared = mine intersect theirs
            return if (mine.isEmpty() || theirs.isEmpty() || shared.isEmpty()) 0.0 else shared.size.toDouble() / min(mine.size, theirs.size)
        }
        // 中文名和各个别名（「Initial D」）里对得最好的那个
        val coverage = (listOf(file.titleTokens) + (if (file.manualQuery == null) file.altTitles else emptyList())).maxOf { coverageOf(it) }
        val titlePts = (W_TITLE * coverage).roundToInt()
        pts += titlePts

        val matched = ArrayList<String>(6)
        if (fileCode != null && sub.code == fileCode) { pts += W_CODE; matched += "编号" }
        if (titlePts > 0) matched += "片名"

        // 4) 年份
        val fy = file.effYear
        if (fy != null && sub.year != null) {
            if (fy == sub.year) { pts += W_YEAR; matched += "年份" } else { pts += W_YEAR_OFF; reasons += "年份对不上（$fy ≠ ${sub.year}）" }
        }
        // 5) 季集一致
        if (file.effEpisode != null && subEpisode != null && file.effEpisode == subEpisode &&
            (file.effSeason == null || subSeason == null || file.effSeason == subSeason)
        ) {
            pts += W_EPISODE; matched += "集数"
        }
        // 6) 分辨率
        if (file.parts.resolution != null && sub.resolution != null) {
            if (file.parts.resolution == sub.resolution) { pts += W_RESOLUTION; matched += "分辨率" } else pts += W_RESOLUTION_OFF
        }
        // 7) 来源
        val srcA = file.parts.source
        val srcB = sub.source
        if (srcA != null && srcB != null) {
            when {
                srcA == srcB -> { pts += W_SOURCE; matched += "来源" }
                sameFamily(srcA, srcB) -> pts += W_SOURCE_FAMILY
            }
        }
        // 8) 发布组
        if (file.parts.group != null && sub.group != null && file.parts.group == sub.group) { pts += W_GROUP; matched += "发布组" }
        if (matched.isNotEmpty()) reasons += if (matched.size >= 2) matched.joinToString("、") + "都对上" else matched.single() + "对上"

        // 9) 语言（按设置里的顺序）
        val lang = langPoints(hit.lang, file.subtitleLangs)
        pts += lang.first
        reasons += lang.second

        // 10) 时长（只有迅雷给）
        val d = hit.durationMs
        val rt = file.runtimeMs
        if (d != null && d > 0 && rt != null && rt > 0) {
            val diff = abs(rt - d).toDouble() / rt
            when {
                diff <= 0.03 -> { pts += W_DURATION_CLOSE; reasons += "时长接近" }
                diff <= 0.15 -> { pts += W_DURATION_NEAR; reasons += "时长略差" }
                else -> { pts += W_DURATION_FAR; reasons += "时长差得远" }
            }
        }
        // 11) 格式
        when (hit.format.lowercase()) {
            "srt" -> pts += W_FORMAT_SRT
            "ass", "ssa" -> pts += W_FORMAT_ASS
        }
        // 12) 下载数（服务器源才有）
        val dl = hit.downloadCount ?: 0
        if (dl > 0) {
            pts += when {
                dl >= 1000 -> W_DOWNLOADS_HIGH
                dl >= 100 -> W_DOWNLOADS_MID
                else -> W_DOWNLOADS_LOW
            }
            reasons += "下载 $dl 次"
        }
        // 13) 单集：写明是这一集了吗（没写明的不当「最合适」）
        val pinned = when {
            exact -> true
            fileCode != null && sub.code == fileCode -> true
            file.effEpisode == null -> true
            subEpisode == null -> subSeason != null && subSeason == file.effSeason && file.seasonEpisodes == 1 && file.effEpisode == 1
            subSeason != null -> true
            else -> (file.effSeason ?: 1) <= 1
        }
        if (!pinned) reasons += if (subEpisode == null) "没写第几集" else "没写第几季"
        return SmartScore(pts, reasons, exact, pinned)
    }

    /** 「Final Stage」又不知道这部剧最后是第几季：哪一季都对不上。 */
    private const val FINAL_UNKNOWN = -1

    /**
     * 智能匹配模式的单子：按分数从高到低（同分再按 指纹 > 语言 > 进 Emby > 来源 > key，稳定可复现）。
     * 同一份（同 key）只留一条。纯函数，有单测。
     */
    fun rank(hits: List<SubtitleHit>, file: SmartFile): List<ScoredHit> =
        hits.distinctBy { it.key }.map { ScoredHit(it, score(it, file)) }.sortedWith(
            compareByDescending<ScoredHit> { it.score.points }
                .thenByDescending { it.score.isExactHash }
                .thenBy { it.hit.lang.rank }
                .thenBy { if (it.hit.toEmby) 0 else 1 }
                .thenBy { it.hit.source.order }
                .thenBy { it.hit.key },
        )

    /** 「最合适」：分数最高且过门槛的那条；一条都没过门槛 = null。手动搜索的结果也走这里（查询词当片名 token）。 */
    fun bestOf(hits: List<SubtitleHit>, file: SmartFile): ScoredHit? = rank(hits, file).firstOrNull { it.score.passes }

    /** 语言那一项的分 + 一句中文。[langs] = 设置里的代码数组（顺序 = 优先级）。 */
    fun langPoints(lang: SubLang, langs: List<String>): Pair<Int, String> {
        val families = langFamilies(langs)
        val mine = familyOf(lang)
        val at = families.indexOfFirst { it == mine || (mine == "zh" && (it == "hans" || it == "hant")) }
        val label = langLabel(lang)
        if (at < 0) {
            // 不在首选里：中文还给一点分（这一路搜的本来就是中文字幕），别的语言不给
            return if (mine == "hans" || mine == "hant" || mine == "zh") W_LANG_CHINESE_FALLBACK to label else 0 to label
        }
        val base = maxOf(0, W_LANG_FIRST - W_LANG_STEP * at)
        val off = when (lang) {
            SubLang.SimplifiedBilingual, SubLang.TraditionalBilingual -> 1
            SubLang.Chinese -> 3
            SubLang.Unknown -> 4
            else -> 0
        }
        return maxOf(0, base - off) to (if (at == 0) "$label（首选）" else label)
    }

    private fun epLabel(season: Int?, episode: Int?): String = when {
        season != null && episode != null -> "S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}"
        episode != null -> "第 $episode 集"
        season != null -> "第 $season 季"
        else -> "—"
    }

    private fun sameFamily(a: String, b: String): Boolean =
        (a.startsWith("web") && b.startsWith("web")) || (a in BLURAY_FAMILY && b in BLURAY_FAMILY)

    private val BLURAY_FAMILY = setOf("bluray", "remux")

    /** 设置里的语言代码 → 去重后的偏好顺序（`zh-Hans` / `zh-Hant` / `en` / `ja` / `ko` / `yue`…）。 */
    private fun langFamilies(langs: List<String>): List<String> {
        val src = langs.ifEmpty { DEFAULT_LANGS }
        val out = ArrayList<String>(4)
        for (code in src) {
            val f = familyOfCode(code.trim().lowercase())
            if (f != null && f !in out) out += f
        }
        return out.ifEmpty { listOf("hans", "hant") }
    }

    private fun familyOfCode(code: String): String? = when {
        code.isEmpty() -> null
        code == "zh-hant" || code == "cht" || code == "tc" || code == "big5" || code == "zh-tw" || code == "zh-hk" -> "hant"
        code == "zh-hans" || code == "zh" || code == "chi" || code == "zho" || code == "chs" || code == "sc" || code == "zh-cn" || code == "cmn" -> "hans"
        code == "yue" -> "zh"
        code == "en" || code == "eng" -> "en"
        code == "ja" || code == "jpn" -> "ja"
        code == "ko" || code == "kor" -> "ko"
        else -> code
    }

    private fun familyOf(lang: SubLang): String = when (lang) {
        SubLang.Simplified, SubLang.SimplifiedBilingual -> "hans"
        SubLang.Traditional, SubLang.TraditionalBilingual -> "hant"
        SubLang.Chinese -> "zh"
        SubLang.English -> "en"
        SubLang.Japanese -> "ja"
        SubLang.Korean -> "ko"
        SubLang.Unknown -> "unknown"
    }

    private fun langLabel(lang: SubLang): String = when (lang) {
        SubLang.Simplified -> "简体中文"
        SubLang.SimplifiedBilingual -> "简体双语"
        SubLang.Traditional -> "繁体中文"
        SubLang.TraditionalBilingual -> "繁体双语"
        SubLang.Chinese -> "中文"
        SubLang.English -> "英文"
        SubLang.Japanese -> "日文"
        SubLang.Korean -> "韩文"
        SubLang.Unknown -> "语言未知"
    }
}

// ---------------------------------------------------------------- 名字解析（纯函数）

private val EXT_TAIL = Regex("""\.(mkv|mp4|m4v|avi|ts|m2ts|mov|wmv|flv|webm|rmvb|iso|mpg|mpeg|vob|strm|srt|ass|ssa|smi|sub|idx|vtt|zip|rar|7z)$""", RegexOption.IGNORE_CASE)
private val LANG_TAIL = Regex("""(\.(chs|cht|chi|zho|eng|en|sc|tc|gb|big5|hans|hant|zh|cn|tw|hk|jpn|kor|forced|sdh)){1,4}$""", RegexOption.IGNORE_CASE)
private val BRACKETS = Regex("""\[[^\]]*\]|【[^】]*】|\{[^}]*\}|\([^)]*\)""")
private val RE_SE = Regex("""s(\d{1,2})[ ._-]*e(\d{1,3})""", RegexOption.IGNORE_CASE)
private val RE_X = Regex("""(^|[^0-9])(\d{1,2})x(\d{1,3})([^0-9]|$)""", RegexOption.IGNORE_CASE)
private val RE_SEASON_WORD = Regex("""season[ ._-]*(\d{1,2})""", RegexOption.IGNORE_CASE)
/** 「Episode 05」「ACT.4」（头文字D Fifth / Final Stage 的字幕按 ACT 编集）。 */
private val RE_EPISODE_WORD = Regex("""(?<![a-z])(?:episode|act)[ ._-]*(\d{1,3})(?![0-9])""", RegexOption.IGNORE_CASE)
private val RE_CN_EP = Regex("""第\s*([0-9]{1,4}|[一二两三四五六七八九十]{1,3})\s*[集话話期回]""")
/** 「第2季」「第二季」（中文数字也认：迅雷上「头文字D第二季03」这种名字不少）。 */
private val RE_CN_SEASON = Regex("""第\s*([0-9一二两三四五六七八九十]{1,3})\s*季""")

/*
 * 季不写成「第N季 / SxxEyy」的写法（《头文字D》的字幕名：「Second Stage 03」「4th.Stage_03」「[头文字D_第四部][Initial_D_Stage4]」）。
 * 「Final Stage」不知道是第几季（要这部剧一共几季），不认。「Stage 03」这种空开的不认（「Final Stage - 03」的 03 是集号）。
 */
private val RE_ORDINAL_STAGE = Regex("""(?i)(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth)[ ._-]*(?:stage|season)""")
private val RE_NUM_STAGE = Regex("""(?i)(?:^|[^a-z0-9])(\d{1,2})(?:st|nd|rd|th)[ ._-]*(?:stage|season)""")
private val RE_CN_PART = Regex("""第\s*([0-9一二两三四五六七八九十]{1,3})\s*部""")
private val RE_STAGE_GLUED = Regex("""(?i)(?:^|[^a-z])stage(\d{1,2})(?![0-9])""")
private val ORDINALS = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth")

/** 特别篇 / OVA / 外传（正片的一集不要这些字幕）。 */
private val SIDE_STORY = Regex("""(?i)battle[ ._-]*stage|extra[ ._-]*stage|legend[ ._-]*[0-9]|赛车专辑|(?:^|[^a-z])(?:ova|oad)(?:[^a-z]|$)|特别篇|番外|总集篇|新剧场版""")

/** 「Final Stage」「Final Season」「最终季」：最后一季。 */
private val RE_FINAL = Regex("""(?i)final[ ._-]*(?:stage|season)|最终[季部]""")

/** 单独的「1st」「4th」（「頭文字D 1st 第01話」「[頭文字D_4th]」）：第几季。后面得是分隔符或结尾。 */
private val RE_ORD_ALONE = Regex("""(?i)(?:^|[^a-z0-9])(\d{1,2})(?:st|nd|rd|th)(?=$|[ ._\-\]】」)])""")

/** 季标记后面紧跟的数字 = 集号（「第二季02」「第4部 02」「Final Stage - 03」「Stage4][03]」「第一部 (26)」）；后面跟分辨率、年份、单位的不算。 */
private val RE_EP_AFTER_SEASON = Regex("""^[ ._\-\])】」]*[(\[【「]?[ ]*(\d{1,3})(?![0-9])(?![ ]*(?:[pik](?![a-z])|x[0-9]|×|bit|fps|帧|年|季|部|期))""", RegexOption.IGNORE_CASE)

/** 汉字后面紧跟的数字（「围棋少年01」「围棋少年2 05」）：剧名后面的集号；后面是「集 / 年 / 季」这些的不算。 */
private val RE_GLUED_SE = Regex("""[\u4e00-\u9fff](\d)[ _.\-]+(\d{1,3})(?![0-9])(?![ ]*[集话季部期年回篇章岁])""")
private val RE_GLUED_EP = Regex("""[\u4e00-\u9fff](\d{1,3})(?![0-9])(?![ ]*[集话季部期年回篇章岁])""")

/** 名字最后是「季 集」两个数（「头文字D 5 11」）。 */
private val RE_TRAIL_SE = Regex("""(?:^|[ _.])(\d{1,2})[ _.]+(\d{1,3})$""")

/** 方括号里只有一个数（「[Initial D First_Stage][03][BDRIP]」）：集号；常见分辨率不算。 */
private val RE_BRACKET_EP = Regex("""[\[【(](\d{1,3})[\]】)]""")
private val NOT_EPISODE_NUMBERS = setOf(480, 576, 720)

private val SEASON_MARKS = listOf(RE_CN_SEASON, RE_CN_PART, RE_ORDINAL_STAGE, RE_NUM_STAGE, RE_STAGE_GLUED, RE_FINAL, RE_SEASON_WORD, RE_ORD_ALONE)

/** 季标记后面紧跟的集号（[RE_EP_AFTER_SEASON]）；每个季标记都试一次。 */
private fun episodeAfterSeason(s: String): Int? {
    for (re in SEASON_MARKS) for (m in re.findAll(s)) {
        val rest = s.substring(m.range.last + 1)
        RE_EP_AFTER_SEASON.find(rest)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
    }
    return null
}

/**
 * 繁体 → 简体、全角 → 半角、各种空格 → 普通空格（「頭文字D」→「头文字D」、「第０１話」→「第01话」；
 * 2026-11-28 真机：迅雷有的名字用不换行空格 U+00A0 隔词「頭文字D Fifth Stage ACT.7」，季就没认出来）。
 */
internal fun foldName(raw: String): String = buildString(raw.length) {
    for (ch in t2s(raw)) append(
        when {
            ch in '\uFF01'..'\uFF5E' -> ch - 0xFEE0
            ch == '\u3000' || ch == '\u00A0' || ch in '\u2000'..'\u200B' || ch == '\u202F' || ch == '\u205F' || ch == '\uFEFF' -> ' '
            else -> ch
        },
    )
}

private fun stageSeason(s: String): Int? =
    RE_ORDINAL_STAGE.find(s)?.groupValues?.get(1)?.let { ORDINALS.indexOf(it.lowercase()) + 1 }?.takeIf { it > 0 }
        ?: RE_NUM_STAGE.find(s)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..99 }
        ?: RE_CN_PART.find(s)?.groupValues?.get(1)?.let(::cnNumber)?.takeIf { it in 1..99 }
        ?: RE_STAGE_GLUED.find(s)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..99 }
        ?: RE_ORD_ALONE.find(s)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..99 }
private val RE_EP = Regex("""(^|[^a-z0-9])(?:ep[ ._-]*|e)(\d{1,3})([^0-9]|$)""", RegexOption.IGNORE_CASE)
private val RE_S_ONLY = Regex("""(^|[^a-z0-9])s(\d{1,2})([^0-9]|$)""", RegexOption.IGNORE_CASE)
private val RE_YEAR = Regex("""(^|[^0-9])((?:19|20)\d{2})([^0-9]|$)""")
private val RE_RES = Regex("""(^|[^0-9])(2160|1080|720|480|576|4320)[pi]([^0-9]|$)""", RegexOption.IGNORE_CASE)
private val RE_UHD = Regex("""(^|[^a-z0-9])(4k|8k|uhd)([^a-z0-9]|$)""", RegexOption.IGNORE_CASE)
private val RE_GROUP_TAIL = Regex("""-([a-z0-9]{2,})$""", RegexOption.IGNORE_CASE)
private val RE_GROUP_HEAD = Regex("""^\[([^\]]{2,20})\]|^【([^】]{2,20})】""")

/** 技术 / 语言 / 版本标签：不当片名 token。 */
private val NOISE = setOf(
    "x264", "h264", "x265", "h265", "hevc", "avc", "av1", "xvid", "divx", "vp9", "mpeg2", "h264hi10p", "hi10p",
    "aac", "aac2", "ac3", "eac3", "ddp", "dd", "dts", "dtshd", "truehd", "atmos", "flac", "mp3", "opus", "pcm", "ma", "hdma",
    "10bit", "8bit", "10bits", "hdr", "hdr10", "hdr10plus", "dv", "dovi", "dolby", "vision", "sdr", "hfr", "60fps", "fps",
    "multi", "multisub", "multisubs", "subs", "sub", "subbed", "dubbed", "dual", "audio", "internal", "proper", "repack", "real",
    "extended", "unrated", "remastered", "criterion", "limited", "retail", "complete", "remux", "bdremux", "uhdbluray",
    "chs", "cht", "chi", "zho", "eng", "en", "jpn", "jp", "kor", "kr", "sc", "tc", "gb", "big5", "hans", "hant",
    "simplified", "traditional", "chinese", "english", "japanese", "korean", "bilingual", "forced", "sdh", "cc",
    "简体", "繁体", "简中", "繁中", "中英", "双语", "雙語", "中字", "字幕", "中文", "英文", "日语", "韩语", "内嵌", "外挂", "高清", "超清", "蓝光", "原盘", "特效", "翻译", "校对", "时轴",
    "web", "webdl", "webrip", "bluray", "blu", "ray", "bdrip", "brrip", "bd", "dvd", "dvdrip", "dvdscr", "hdtv", "hdrip", "hdcam", "cam", "telesync", "tc", "ts", "hd", "sd", "uhd", "dl", "rip",
    "4k", "8k", "2160p", "1080p", "720p", "480p", "576p", "4320p", "1080i", "720i", "2160", "1080", "720", "480",
    "srt", "ass", "ssa", "smi", "vtt", "idx", "mkv", "mp4", "avi", "strm", "zip", "rar", "7z",
    "season", "episode", "ep", "part", "cd", "disc", "vol", "volume", "movie", "film", "series", "show", "tv", "all", "final", "end",
    "the", "a", "an", "of", "and", "to", "in", "on", "at", "for", "with", "by", "from",
    "stream", "video", "play", "playlist", "index", "master", "download", "media", "file",
    "第", "集", "季", "话", "話", "期", "回", "版", "之", "和", "与",
)

/** 单个汉字里不算片名的（「简」「繁」这类标签）。 */
private val NOISE_SINGLE_CJK = setOf("简", "繁", "中", "英", "日", "韩", "版", "集", "季", "第", "字", "幕", "之", "和", "与")

/**
 * 名字 → [NameParts]。片名 / 字幕名都走这一个（点、空格、下划线、横杠都当分隔；中英文混写各自成 token）。
 * 纯函数，有单测。
 */
fun parseName(raw: String): NameParts {
    var s = foldName(raw.trim())
    // 尾巴的扩展名 / 语言后缀一层层剥（`…chi.srt`、`…chs.ass`）
    while (true) {
        val next = EXT_TAIL.replace(s, "")
        val next2 = LANG_TAIL.replace(next, "")
        if (next2 == s) break
        s = next2
    }
    val headGroup = RE_GROUP_HEAD.find(s)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase() }
    val noBrackets = BRACKETS.replace(s, " ")
    // 尾巴的 `-GROUP`：至少有一个字母、不是技术标签（`WEB-DL` 的 dl、`x264-GROUP` 的 x264 都不算发布组）
    val tailGroup = RE_GROUP_TAIL.find(noBrackets.trim())?.groupValues?.get(1)?.lowercase()
        ?.takeIf { g -> g.any { it in 'a'..'z' } && g !in NOISE }
    val group = tailGroup ?: headGroup?.takeIf { it !in NOISE && it.any { c -> c in 'a'..'z' } }

    val season: Int?
    val episode: Int?
    val se = RE_SE.find(s)
    val x = RE_X.find(s)
    when {
        se != null -> { season = se.groupValues[1].toIntOrNull(); episode = se.groupValues[2].toIntOrNull() }
        x != null -> { season = x.groupValues[2].toIntOrNull(); episode = x.groupValues[3].toIntOrNull() }
        else -> {
            season = RE_CN_SEASON.find(s)?.groupValues?.get(1)?.let(::cnNumber)?.takeIf { it in 1..99 }
                ?: stageSeason(s)
                ?: RE_SEASON_WORD.find(s)?.groupValues?.get(1)?.toIntOrNull()
                ?: RE_S_ONLY.find(s)?.groupValues?.get(2)?.toIntOrNull()
            episode = RE_CN_EP.find(s)?.groupValues?.get(1)?.let(::cnNumber)
                ?: RE_EPISODE_WORD.find(s)?.groupValues?.get(1)?.toIntOrNull()
                ?: RE_EP.find(s)?.groupValues?.get(2)?.toIntOrNull()
                ?: episodeAfterSeason(s)
        }
    }
    val finalSeason = season == null && RE_FINAL.containsMatchIn(s)
    // 松写法（只给单集打分用）：季集都没读出来时才看
    var looseSeason: Int? = null
    var looseEpisode: Int? = null
    if (episode == null) {
        val glued = RE_GLUED_SE.find(noBrackets)
        val trail = RE_TRAIL_SE.find(s)
        // 名字里另有字母和数字粘在一起的词（「頭文字D4-04」的 D4 = 第四部）：单独的数是不是集号说不准，不认
        val mixed = words(noBrackets).any { w -> w.any { it in '0'..'9' } && w.any { it in 'a'..'z' } && w !in NOISE && codeIn(w) == null }
        when {
            glued != null -> { looseSeason = glued.groupValues[1].toIntOrNull(); looseEpisode = glued.groupValues[2].toIntOrNull() }
            trail != null && season == null -> { looseSeason = trail.groupValues[1].toIntOrNull(); looseEpisode = trail.groupValues[2].toIntOrNull() }
            mixed -> {}
            else -> looseEpisode = RE_GLUED_EP.find(noBrackets)?.groupValues?.get(1)?.toIntOrNull()
                // 剧名后面单独一个数（「围棋少年 13.srt」「围棋少年.13.chs.srt」）：只有一个才算
                ?: words(noBrackets).filter { w -> w.length <= 3 && w.all { it in '0'..'9' } && w.toInt() !in NOT_EPISODE_NUMBERS }.singleOrNull()?.toInt()
                ?: RE_BRACKET_EP.findAll(s).mapNotNull { it.groupValues[1].toIntOrNull() }.firstOrNull { it > 0 && it !in NOT_EPISODE_NUMBERS }
        }
        if (looseSeason != null && season != null) looseSeason = null
        if (looseEpisode == 0) looseEpisode = null
    }
    val year = RE_YEAR.find(s)?.groupValues?.get(2)?.toIntOrNull()
    val resolution = RE_RES.find(s)?.let { it.groupValues[2] + "p" }
        ?: RE_UHD.find(s)?.let { when (it.groupValues[2].lowercase()) { "8k" -> "4320p"; else -> "2160p" } }
    val source = sourceOf(s)
    val code = codeIn(noBrackets)

    // 片名 token：分词以后丢掉数字、技术标签、年份、季集写法、发布组、编号的字母部分（不然 ABP 开头的字幕都拿满片名分）
    // 整个名字都在方括号里（「[AI-Raws][頭文字D_4th][Initial_D_Fourth_Stage][2004][03]…」）：片名 token 从括号里取（第一组是发布组，不算）
    val tokenSource = if (words(noBrackets).any { it.any { c -> c.isLetter() } }) noBrackets
    else BRACKETS.findAll(s).drop(1).joinToString(" ") { it.value.drop(1).dropLast(1) }
    val tokens = words(tokenSource).filterTo(LinkedHashSet<String>()) { w ->
        when {
            w.isEmpty() -> false
            w.all { it in '0'..'9' } -> false
            code != null && w in code.words -> false
            w in NOISE -> false
            w.length == 1 && w in NOISE_SINGLE_CJK -> false
            w == group -> false
            w == year?.toString() -> false
            RE_SE.matches(w) || RE_X.matches(w) -> false
            Regex("""^s\d{1,2}$""").matches(w) || Regex("""^ep?\d{1,3}$""").matches(w) || Regex("""^\d{1,2}x\d{1,3}$""").matches(w) -> false
            w.length < 2 && !isCjk(w) -> false
            else -> true
        }
    }
    return NameParts(tokens, year, season, episode, resolution, source, group, code?.code, finalSeason, looseSeason, looseEpisode)
}

/**
 * 名字里番号式的编号（`ABP-811`、`abp_811`、`ABP811`、`abp00811`、`300MIUM-123`、`FC2-PPV-1234567`），规范成「大写字母-去掉前导零的数字」；没有 = null。
 * 括号里的不算（`[中字]ABP-811-C.srt` 认的是 ABP-811）。纯函数，有单测。
 */
fun codeOf(raw: String): String? {
    var s = raw.trim()
    while (true) {
        val next = LANG_TAIL.replace(EXT_TAIL.replace(s, ""), "")
        if (next == s) break
        s = next
    }
    return codeIn(BRACKETS.replace(s, " "))?.code
}

/** 认出来的编号：[code] = 规范写法；[words] = 它在分词里占的小写 token（字母部分、带数字前缀的那一段），不再当片名。 */
private class CodeHit(val code: String, val words: Set<String>)

/*
 * 「2–6 个字母（前面可以带最多 3 位数字）+ 可有可无的 - / _ / 空格 + 2–7 位数字」；FC2 的 `FC2-PPV-` 前缀单独认。
 * 不算编号的：字母部分是技术 / 版本标签（ep、cd、part、vol、hdr、web、bd…，[NOISE] + [CODE_NOISE]）；数字是年份（1900–2099，年份优先）；
 * 数字后面紧跟 p / i / k / bit / fps（分辨率、码率）；字母大小写混写（`Apollo-13`、`Hi10P` 是词，不是编号）；空格隔开的只认全大写（`ABP 811`，
 * 不认 `Blade Runner 2049`）。点隔开的不认（`Apollo.13.1995`）。
 */
private val RE_CODE = Regex("""(?:^|[^A-Za-z0-9])((?:[Ff][Cc]2[-_ ]?)?)([0-9]{0,3})([A-Za-z]{2,6})([-_ ]?)([0-9]{2,7})(?![0-9])([A-Za-z]*)""")
private val CODE_NOISE = setOf("hi", "ma", "ch", "bit", "bits", "fps", "hz", "mb", "gb", "kbps", "mbps", "mp", "ps", "av", "vc", "op", "ed", "sp", "ova", "oad", "ncop", "nced", "pv", "cm", "no", "nr", "s", "e", "x")
private val CODE_UNIT_TAIL = setOf("p", "i", "k", "bit", "bits", "fps", "hz", "ch", "kbps", "mbps")

private fun codeIn(s: String): CodeHit? {
    for (m in RE_CODE.findAll(s)) {
        val fc2 = m.groupValues[1]
        val lead = m.groupValues[2]
        val letters = m.groupValues[3]
        val sep = m.groupValues[4]
        val digits = m.groupValues[5]
        val tail = m.groupValues[6]
        val low = letters.lowercase()
        if (low in NOISE || low in CODE_NOISE) continue
        if (tail.lowercase() in CODE_UNIT_TAIL) continue
        val upper = letters.all { it in 'A'..'Z' }
        val lower = letters.all { it in 'a'..'z' }
        if (!upper && !lower) continue
        if (sep == " " && !upper) continue
        if (digits.length == 4 && digits.toInt() in 1900..2099) continue
        val number = digits.trimStart('0').ifEmpty { "0" }
        val head = if (fc2.isNotEmpty()) "FC2-" + lead.uppercase() + letters.uppercase() else lead.uppercase() + letters.uppercase()
        val words = setOfNotNull(low, (lead + letters).lowercase(), "fc2".takeIf { fc2.isNotEmpty() })
        return CodeHit("$head-$number", words)
    }
    return null
}

private fun sourceOf(s: String): String? {
    val n = s.lowercase()
    fun has(vararg w: String) = w.any { Regex("(^|[^a-z0-9])${Regex.escape(it)}([^a-z0-9]|$)").containsMatchIn(n) }
    return when {
        has("remux", "bdremux") -> "remux"
        has("bluray", "blu-ray", "bdrip", "brrip", "bd", "uhdbluray") -> "bluray"
        has("web-dl", "webdl", "web dl") -> "webdl"
        has("webrip", "web-rip") -> "webrip"
        has("web") -> "web"
        has("hdtv") -> "hdtv"
        has("dvdrip", "dvd", "dvdscr") -> "dvd"
        has("hdrip") -> "hdrip"
        else -> null
    }
}

private fun isCjk(w: String): Boolean = w.any { it >= '\u4e00' && it <= '\u9fff' }

/** 分词：拉丁 / 数字一段，中日韩文字一段；别的字符都是分隔。小写。 */
private fun words(s: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var kind = 0
    fun flush() {
        if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
    }
    for (c in s.lowercase()) {
        val k = when {
            c in 'a'..'z' || c in '0'..'9' -> 1
            c >= '\u4e00' && c <= '\u9fff' -> 2
            c >= '\u3040' && c <= '\u30ff' -> 2
            c >= '\uac00' && c <= '\ud7af' -> 2
            else -> 0
        }
        if (k == 0) { flush(); kind = 0 } else {
            if (kind != k) flush()
            kind = k
            sb.append(c)
        }
    }
    flush()
    return out
}
