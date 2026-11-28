package com.gugaart.ui.subtitle

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugaart.ui.player.DanmakuSeasonFacts
import com.gugugaga.app.model.isStrmSource
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyException
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.RequestOptions
import com.gugugaga.core.RetryMode
import com.gugugaga.core.encodeURIComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/*
 * 一次字幕搜索（docs/subtitle-search.md §2–§4）：这一集是什么（[SubtitleTarget]）→ 取指纹 → 三路同时搜 → 合成一张单子；
 * 选中一条 → 服务器源：让 Emby 下载、存进 Emby、等新字幕出现；迅雷 / 射手：下载到本机、只在这次播放里用（[LocalSubtitles]）。
 * 怎么用在播放器里（reload、自动选字幕让路）在 SubtitleSearchUi.kt 的 [SubtitleSearchBoard]。
 */

/** 去哪读视频文件的几段（取指纹）：地址 + 要带的头。本地片源 = Emby 原始流（令牌在头里）；strm = 目标地址（**不带** Emby 的任何东西）。 */
data class ReadPlan(val url: String, val headers: Map<String, String>, val viaEmby: Boolean) {
    override fun toString(): String = "ReadPlan(viaEmby=$viaEmby)"
}

/** 要给哪一集搜字幕。 */
data class SubtitleTarget(
    val itemId: String,
    val mediaSourceId: String?,
    /** 栏里第二行：「孤岛传说 · 第 1 集 · 风起」/「肖申克的救赎 (1994)」。 */
    val title: String,
    /** 发给字幕站的文件名（只有最后一段，不带目录）。 */
    val fileName: String,
    /** 迅雷按名字搜的默认词（文件名像个视频文件就用它，否则「剧名 S01E05」/ 片名）。 */
    val nameQuery: String,
    val runtimeMs: Long?,
    /** MediaSource.Size（服务器不说总长时才用）。 */
    val size: Long?,
    val read: ReadPlan?,
    /** 选之前这个片源已有的外挂字幕（index → 路径），用来认出服务器新存的那一条。 */
    val externalBefore: Map<Int, String?>,
    /** 片源是 strm / 远程直链：服务器插件的 IsHashMatch 在这一集上不算数（docs/subtitle-search.md §1.4）。 */
    val isStrm: Boolean = false,
    /** 元数据里的季 / 集 / 年份（文件名里没写时靠它；季集对不上直接扣到底）。 */
    val season: Int? = null,
    val episode: Int? = null,
    val year: Int? = null,
    /** 元数据里的片名 / 剧名（strm 的文件名常常是网盘的乱码，片名 token 靠它）。 */
    val name: String? = null,
    /** 备选查询（中文剧集「剧名 第N集」/ 电影纯片名）：主查询没搜到时再试一次。 */
    val altQuery: String? = null,
    /** 单集所属的剧（取季列表：最大的季号、这一季的集数，智能匹配用）。 */
    val seriesId: String? = null,
) {
    override fun toString(): String = "SubtitleTarget(item=$itemId, source=$mediaSourceId, read=$read)"

    /**
     * 射手的 `pathinfo`：射手要的是文件名（和哈希一起配），所以默认词只是文件名里的编号时照旧传文件名；
     * 别的情况和以前一样传 [nameQuery]（无意义的 CDN 名换成元数据查询，§3）。
     */
    val shooterName: String get() = if (nameQuery == codeOf(fileName)) fileName else nameQuery

    companion object {
        private val VIDEO_EXT = Regex("""\.(mkv|mp4|m4v|avi|ts|m2ts|mov|wmv|flv|webm|rmvb|iso|mpg|mpeg|vob|strm)$""", RegexOption.IGNORE_CASE)

        /** 路径 / 地址的最后一段（去掉查询串；URL 解码一次）。纯函数，有单测。 */
        fun baseName(path: String?): String? {
            val p = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val noQuery = p.substringBefore('?').substringBefore('#')
            val last = noQuery.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
            return percentDecode(last).takeIf { it.isNotBlank() }
        }

        private fun percentDecode(s: String): String {
            if ('%' !in s) return s
            val out = ArrayList<Byte>(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) {
                    val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                    if (v != null) {
                        out += v.toByte()
                        i += 3
                        continue
                    }
                }
                for (b in c.toString().encodeToByteArray()) out += b
                i++
            }
            return out.toByteArray().decodeToString()
        }

        /** 片名那一行和按名字搜的默认词。 */
        fun titleOf(item: BaseItemDto): String = if (item.type == "Episode") listOfNotNull(
            item.seriesName,
            item.indexNumber?.let { "第 $it 集" },
            item.name.takeIf { it.isNotBlank() },
        ).joinToString(" · ") else listOfNotNull(item.name, item.productionYear?.let { "($it)" }).joinToString(" ")

        /** 文件名看起来有没有意义（能拿去按名字搜）：纯数字 / 哈希样 / 太短 / 全是通用词 / 单个短 token 又和条目名毫无交集 = 无意义
         * （strm 的 CDN 名常常是 `0.mp4`、`a1b2c3d4.mkv` 这种）。有分隔符的正常发布名照旧有意义。纯函数，有单测。 */
        fun fileNameLooksMeaningful(fileName: String?, item: BaseItemDto): Boolean {
            val trimmed = fileName?.trim().orEmpty()
            val base = trimmed.substringBeforeLast('.', trimmed).trim()
            // 「太短」只针对拉丁/数字名；中文片名常常只有两三个字
            if (base.length < (if (base.any(::isCjkChar)) 2 else 4)) return false
            if (base.all { it in '0'..'9' }) return false
            if (HASH_NAME.matches(base)) return false
            val tokens = base.split(NAME_SPLIT).filter { it.isNotEmpty() }
            if (tokens.isEmpty() || tokens.all { it.lowercase() in GENERIC_FILE_WORDS }) return false
            val hasSeparator = base.any { it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9' && !isCjkChar(it) }
            if (!hasSeparator && base.length <= 12) {
                val names = listOfNotNull(item.name, item.seriesName).flatMap { it.split(NAME_SPLIT).filter { t -> t.isNotEmpty() } }
                val lower = base.lowercase()
                if (names.none { it.lowercase() == lower || it.contains(base) || base.contains(it) }) return false
            }
            return true
        }

        /** 文件名无意义时按元数据构造的查询：电影「片名 年份」，单集「剧名 S01E05」。纯函数，有单测。 */
        fun metadataNameQuery(item: BaseItemDto): String = if (item.type == "Episode" && item.seriesName != null) {
            "${item.seriesName} S${(item.parentIndexNumber ?: 1).toString().padStart(2, '0')}E${(item.indexNumber ?: 1).toString().padStart(2, '0')}"
        } else listOfNotNull(item.name.takeIf { it.isNotBlank() }, item.productionYear?.takeIf { it > 0 }?.toString()).joinToString(" ")

        /**
         * 备选查询：中文剧集再试「剧名 第N集」；电影带年份时再试纯片名。null = 没有备选。纯函数，有单测。
         * 剧集的备选故意不带季：迅雷认「S06E03」和「第六季 第3集」是同一个意思（2026-11-26 实测），这一季它没收录时两种写法都只回整部剧的字幕；
         * 「剧名 第N集」回的是各季的第 N 集（头文字D 第3集 里有 Final Stage - 03），季对不上的由智能匹配扣掉。
         */
        fun altNameQueryOf(item: BaseItemDto): String? {
            val series = item.seriesName
            return when {
                item.type == "Episode" && series != null && series.any(::isCjkChar) -> "$series 第${item.indexNumber ?: 1}集"
                item.type != "Episode" -> item.productionYear?.takeIf { it > 0 }?.let { item.name.takeIf { n -> n.isNotBlank() } }
                else -> null
            }
        }

        /**
         * 按名字搜的默认词：文件名像个视频文件、有意义、（单集）还写明了是哪一集就用文件名，否则 [metadataNameQuery]（「剧名 S06E03」/「片名 年份」）。
         * 单集的文件名没写这一集（只有剧名、只有集号没有季号的第 2 季起）= 拿去搜只会搜到整部剧（2026-11-26 主理人：头文字D 第六季第三集默认搜「头文字D」）。
         * 纯函数，有单测。
         */
        fun nameQueryOf(item: BaseItemDto, fileName: String?): String {
            // 文件名里有编号（abp-811-ltx.mp4）：只搜编号（ABP-811），不带发布组、扩展名、画质、编码（1.5.1 subfix）
            fileName?.let(::codeOf)?.let { return it }
            if (fileName != null && VIDEO_EXT.containsMatchIn(fileName) && fileNameLooksMeaningful(fileName, item) && fileNameNamesEpisode(fileName, item)) return fileName
            return metadataNameQuery(item)
        }

        /**
         * 单集：文件名里写的季集是不是这一集（集号对上；季号对上，或者没写季号而这是第 1 季）。不是单集 = true。纯函数，有单测。
         */
        fun fileNameNamesEpisode(fileName: String, item: BaseItemDto): Boolean {
            if (item.type != "Episode") return true
            val parts = parseName(fileName)
            val episode = item.indexNumber ?: return true
            val season = item.parentIndexNumber ?: 1
            return parts.episode == episode && (parts.season == season || (parts.season == null && season <= 1))
        }

        /**
         * [item] 的 [source] → 目标。[strmTarget] / [remoteHeaders]：播放器已经解析好的 strm 目标（有就用它，和内核开的是同一个地址）。
         * 纯函数（只拼地址），有单测。
         */
        fun of(item: BaseItemDto, source: MediaSourceInfo?, client: EmbyClient?, strmTarget: String? = null, remoteHeaders: Map<String, String>? = null): SubtitleTarget {
            val remote = strmTarget ?: source?.path?.takeIf { source.isRemote == true || source.protocol.equals("Http", ignoreCase = true) }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val fileName = baseName(remote ?: source?.path) ?: nameQueryOf(item, null)
            val read = when {
                source == null -> null
                remote != null -> ReadPlan(remote, remoteHeaders ?: source.requiredHttpHeaders, viaEmby = false)
                // 令牌走头（和内核取流同一组：X-Emby-Token + 反代要的自定义头）；跳到别的主机时这些头一个都不带（[SubtitleSearchSession.readSpan]）
                client != null && !client.accessToken.isNullOrEmpty() -> ReadPlan(
                    client.http.url("/Videos/${encodeURIComponent(item.id)}/stream", mapOf("Static" to "true", "MediaSourceId" to source.id)),
                    client.streamHeaders(),
                    viaEmby = true,
                )
                else -> null
            }
            return SubtitleTarget(
                itemId = item.id,
                mediaSourceId = source?.id,
                title = titleOf(item),
                fileName = fileName,
                nameQuery = nameQueryOf(item, fileName),
                runtimeMs = ((source?.runTimeTicks ?: item.runTimeTicks)?.div(10_000))?.takeIf { it > 0 },
                size = source?.size,
                read = read,
                externalBefore = externalSubtitles(source),
                isStrm = strmTarget != null || (source != null && isStrmSource(source)),
                season = item.parentIndexNumber.takeIf { item.type == "Episode" },
                episode = item.indexNumber.takeIf { item.type == "Episode" },
                year = item.productionYear?.takeIf { it > 0 },
                name = (if (item.type == "Episode") item.seriesName ?: item.name else item.name).takeIf { it.isNotBlank() },
                altQuery = altNameQueryOf(item),
                seriesId = item.seriesId?.takeIf { item.type == "Episode" && it.isNotEmpty() },
            )
        }

        fun externalSubtitles(source: MediaSourceInfo?): Map<Int, String?> =
            source?.mediaStreams.orEmpty().filter { it.type == "Subtitle" && it.isExternal == true }.associate { it.index to it.path }
    }
}

/** 一路源现在怎么样（栏上的状态行）。 */
sealed interface SourceState {
    data object Idle : SourceState
    data object Busy : SourceState
    data class Done(val count: Int) : SourceState

    /** 没去搜：[why] = 为什么（「设置里关了」「这个账号不能下载字幕」…）。 */
    data class Off(val why: String) : SourceState
    data class Failed(val why: String) : SourceState
}

/** 指纹取得怎么样。 */
sealed interface PrintState {
    data object Idle : PrintState
    data object Busy : PrintState
    data class Got(val prints: FilePrints) : PrintState
    data class Missing(val why: String) : PrintState
}

/** 选中一条之后。 */
sealed interface ApplyState {
    /** 哪一条（[SubtitleHit.key]）。 */
    val key: String

    /** [step]：「下载中…」「存进 Emby…」「等服务器刷新…」。 */
    data class Working(override val key: String, val step: String) : ApplyState
    data class Done(override val key: String, val note: String) : ApplyState

    /**
     * [upload]：「保存到服务器」（迅雷 / 射手的结果传进 Subtitle Manager 插件）失败的是哪一种——决定那一行还剩哪几个按钮（[hitActionsOf]）；
     * null = 不是上传失败（下载失败、服务器源失败…）。
     * [danmaku]：下载下来一看是弹幕（§4.5）——行上写 [DANMAKU_LABEL]，一个按钮都不留。
     */
    data class Failed(override val key: String, val why: String, val upload: UploadFail? = null, val danmaku: Boolean = false) : ApplyState
}

/** 「保存到服务器」失败的三种（docs/subtitle-search.md §4.4）。 */
enum class UploadFail {
    /** 同名字幕已经在服务器上（插件不覆盖）：只剩「仅本次播放」。 */
    Exists,

    /** 401 / 403：只剩「仅本次播放」。 */
    Denied,

    /** 别的：「重试」+「仅本次播放」。 */
    Other,
}

/** 一条结果上的按钮（顺序 = 显示顺序）。 */
enum class HitAction(val label: String) {
    /** 服务器源：让 Emby 下载、存进去（§4.2）。 */
    ServerSave("保存到服务器"),

    /** 迅雷 / 射手：下载后传给 Subtitle Manager 插件（§4.4）。 */
    Upload("保存到服务器"),
    Retry("重试"),

    /** 播放器里：仅本次播放（§4.3）。 */
    Local("仅本次播放"),

    /** 播放器外：开播这一集再加载（§4.3 第 6 条）。 */
    PlayLoad("播放并加载"),
}

/**
 * 一条结果有哪些按钮（纯函数，有单测）。服务器源只有「保存到服务器」；迅雷 / 射手：服务器装了 Subtitle Manager、账号是管理员（[canUpload]）
 * 时「保存到服务器」在前、「仅本次播放」在后；上传失败了按 [ApplyState.Failed.upload] 换（已有 / 没权限 → 只剩本次；别的 → 「重试」在前）。
 */
fun hitActionsOf(hit: SubtitleHit, inPlayer: Boolean, canUpload: Boolean, apply: ApplyState?): List<HitAction> {
    if (hit.toEmby) return listOf(HitAction.ServerSave)
    // 下载下来是弹幕：不加载、不上传，什么都不留（§4.5）
    if ((apply as? ApplyState.Failed)?.takeIf { it.key == hit.key }?.danmaku == true) return emptyList()
    val local = if (inPlayer) HitAction.Local else HitAction.PlayLoad
    if (!canUpload) return listOf(local)
    val failed = (apply as? ApplyState.Failed)?.takeIf { it.key == hit.key }?.upload
    return when (failed) {
        UploadFail.Exists, UploadFail.Denied -> listOf(local)
        UploadFail.Other -> listOf(HitAction.Retry, local)
        null -> listOf(HitAction.Upload, local)
    }
}

/**
 * 社区插件 Subtitle Manager（Kectai/Emby.SubtitleManager 1.3.6，MIT）：`POST /SubtitleManager/Upload?ItemId=&Language=&Format=&IsForced=`，
 * 请求体就是字幕文件；只有管理员能用（不是 = 403 / 401）；别的失败都是 200 + `{Success:false, Message}`；同名字幕不覆盖；
 * 存在 Emby 自己的元数据目录里（strm 也行），存完刷新这一条的元数据。认插件看 `GET /Plugins` 的 Id（名字会本地化）。
 */
object SubtitleManagerPlugin {
    /** Plugin.cs 的 Id（`944151CB-7308-487B-8023-3E5C352BC545`），比的时候去掉横杠、小写。 */
    const val ID = "944151cb7308487b80233e5c352bc545"
    const val UPLOAD = "/SubtitleManager/Upload"

    /** 插件的名字（默认 + 本地化的几种）：Id 对不上时的退路。 */
    val NAMES = setOf("subtitle manager", "字幕管理器", "字幕マネージャー", "자막 관리자")
}

/** 服务器装了 Subtitle Manager 插件、而且这个账号是管理员：迅雷 / 射手的结果能「保存到服务器」。纯函数，有单测。 */
fun subtitleUploadAvailable(plugins: kotlinx.serialization.json.JsonElement?, policy: JsonObject?): Boolean {
    val admin = (policy?.get("IsAdministrator") as? JsonPrimitive)?.booleanOrNull == true
    if (!admin) return false
    val list = plugins as? kotlinx.serialization.json.JsonArray ?: return false
    return list.any { p ->
        val o = p as? JsonObject ?: return@any false
        val id = (o["Id"] as? JsonPrimitive)?.content?.replace("-", "")?.lowercase()
        val name = (o["Name"] as? JsonPrimitive)?.content?.trim()?.lowercase()
        id == SubtitleManagerPlugin.ID || name in SubtitleManagerPlugin.NAMES
    }
}

/** 插件回的那一句：[UploadAnswer.Ok] / [UploadAnswer.Exists] / [UploadAnswer.Failed]。纯函数，有单测。 */
sealed interface UploadAnswer {
    data object Ok : UploadAnswer
    data object Exists : UploadAnswer
    data class Failed(val message: String) : UploadAnswer
}

fun uploadAnswerOf(body: String): UploadAnswer {
    if (body.isBlank()) return UploadAnswer.Ok
    val o = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return UploadAnswer.Failed(body.take(120))
    if ((o["Success"] as? JsonPrimitive)?.booleanOrNull == true) return UploadAnswer.Ok
    val msg = (o["Message"] as? JsonPrimitive)?.content.orEmpty()
    return if ("already exists" in msg.lowercase()) UploadAnswer.Exists else UploadAnswer.Failed(msg)
}

/** 传给插件的语言（Emby 认的三字码）。 */
fun uploadLanguageOf(lang: SubLang): String = when (lang) {
    SubLang.English -> "eng"
    SubLang.Japanese -> "jpn"
    SubLang.Korean -> "kor"
    else -> "chi"
}

/** 上传失败（[UploadFail] + 给人看的一句）。 */
class SubtitleUploadException(val fail: UploadFail, message: String) : Exception(message)

/** 一路服务器源能不能用（看用户策略）。纯函数，有单测。null = 问不到（照样试，服务器自己会拒）。 */
fun serverDownloadAllowed(policy: JsonObject?): Boolean? {
    if (policy == null) return null
    val admin = (policy["IsAdministrator"] as? JsonPrimitive)?.booleanOrNull
    val dl = (policy["EnableSubtitleDownloading"] as? JsonPrimitive)?.booleanOrNull
    // 明说「允许下载字幕」或者是管理员（Emby 没写清管理员受不受这一项约束：管理员一律让试，拒了再说）
    return dl == true || admin == true || dl == null
}

/** 认出服务器新存的那条外挂字幕：路径不在 [before] 里的（没路径就看编号）。纯函数，有单测。 */
fun newExternalIndex(before: Map<Int, String?>, after: Map<Int, String?>): Int? {
    val oldPaths = before.values.filterNotNull().toSet()
    val byPath = after.filter { (_, p) -> p != null && p !in oldPaths }.keys
    if (byPath.isNotEmpty()) return byPath.max()
    return after.keys.filter { it !in before.keys }.maxOrNull()
}

/**
 * 一次搜索的全部状态（Compose 读）。[client] = 当前会话的 Emby（服务器源、取指纹的令牌）；[online] = 设置里的「在线字幕源」。
 */
@Stable
class SubtitleSearchSession(
    val target: SubtitleTarget,
    private val client: EmbyClient?,
    private val net: SubtitleNet,
    val online: Boolean,
    /** 设置里的首选字幕语言（上游 `subtitleLangs` 的代码数组，顺序 = 优先级）。 */
    val subtitleLangs: List<String> = SmartMatch.DEFAULT_LANGS,
    /** 剧的别名（[SubtitleAliases]，弹幕服务器给的「Initial D」这种）；null = 不问。 */
    private val aliasSource: (suspend () -> List<String>)? = null,
) {
    var lang by mutableStateOf(SearchLang.Chinese)
    var query by mutableStateOf(target.nameQuery)
    var server: SourceState by mutableStateOf(SourceState.Idle)
        private set
    var thunder: SourceState by mutableStateOf(SourceState.Idle)
        private set
    var shooter: SourceState by mutableStateOf(SourceState.Idle)
        private set
    var print: PrintState by mutableStateOf(PrintState.Idle)
        private set
    var hits: List<SubtitleHit> by mutableStateOf(emptyList())
        private set
    /** 智能匹配的单子：按分数从高到低（[SmartMatch.rank]）。老的 [hits] 顺序不变，界面要用哪一套自己选。 */
    var smartHits: List<ScoredHit> by mutableStateOf(emptyList())
        private set

    /** 「最合适」：分数最高且过门槛的那条；一条都没过 = null。 */
    var best: ScoredHit? by mutableStateOf(null)
        private set
    private var applyState: ApplyState? by mutableStateOf(null)
    var apply: ApplyState?
        get() = applyState
        internal set(value) {
            applyState = value
            // 行上写着「这是弹幕，不是字幕」的那一条：一换别的事就从单子里拿掉（§4.5）
            if (labelled != null && value?.key != labelled) {
                labelled = null
                publish()
            }
        }

    /** 判成弹幕的下载地址（§4.5）：从单子、「最合适」、自动加载里去掉。 */
    private var danmakuUrls: Set<String> = emptySet()

    /** 判过不是弹幕的下载地址（名字像弹幕的，查完是干净的就不再压后）。 */
    private var cleanUrls: Set<String> = emptySet()

    /** 正在后台预查的下载地址：还没出结果的不当「最合适」。 */
    private var screening: Set<String> = emptySet()

    /** 点的时候刚判成弹幕、行上正写着 [DANMAKU_LABEL] 的那一条（留在单子里让人看到这一句；别的地方都不算它）。 */
    private var labelled: String? = null

    /** 迅雷 / 射手的结果能「保存到服务器」（服务器装了 Subtitle Manager 插件、账号是管理员；[subtitleUploadAvailable]）。 */
    var canUpload: Boolean by mutableStateOf(false)
        internal set // 测试（离屏）直接定
    private var uploadChecked = false

    private var serverHits: List<SubtitleHit> = emptyList()
    private var thunderHits: List<SubtitleHit> = emptyList()
    private var shooterHits: List<SubtitleHit> = emptyList()
    private var serverAllowed: Boolean? = null

    /** 还在搜（还没开始的也算：栏刚出来那一下不写「没有搜到」）。 */
    val searching: Boolean get() = listOf(server, thunder, shooter).any { it == SourceState.Busy || it == SourceState.Idle } || print == PrintState.Busy

    /** 判成弹幕了（§4.5）。 */
    private fun isDanmaku(h: SubtitleHit): Boolean = h.url != null && h.url in danmakuUrls

    /**
     * 名字就写着是 B 站弹幕导出的（「(Av14121610,P13)」「BV1…」「弹幕」）、还没查出是干净的：**不出现在单子里**
     * （主理人 2026-11-28：「而且还是会搜索到bilbili的那种弹幕的」——1.6 只是压到最后，照样看得见）。
     * 迅雷 / 射手的这种一律先下载查内容（[screenTargets]），查出是正经字幕的才放出来；服务器源查不了内容（Emby 自己下载），
     * 跟着同名的迅雷那一条的结论走（MeiamSub 的结果就是迅雷的同一份），没有同名的就不出现。
     */
    private fun suspected(h: SubtitleHit): Boolean {
        if (!DanmakuSniff.nameLooksLikeDanmaku(h.name)) return false
        if (h.url != null && h.url in cleanUrls) return false
        if (h.source == SubSource.Server) {
            val key = nameKey(h.name)
            if ((thunderHits + shooterHits).any { it.url != null && it.url in cleanUrls && nameKey(it.name) == key }) return false
        }
        return true
    }

    /** 服务器源和迅雷的同一份字幕：名字去掉空白、大小写以后一样。 */
    private fun nameKey(name: String): String = name.filterNot { it.isWhitespace() }.lowercase()

    private fun publish() {
        val all = serverHits + shooterHits + thunderHits
        val kept = all.filterNot { isDanmaku(it) || suspected(it) }
        // 刚点出来是弹幕的那一行留着写标签（不计数、不打分、不当最合适）
        val shown = all.filter { (!isDanmaku(it) && !suspected(it)) || it.key == labelled }
        hits = rankHits(shown, target.runtimeMs)
        // 智能匹配（1.4.1 P2）：同一批结果再按「和这一集对不对得上」打一次分，界面拿 smartHits / best 显示「最合适」
        smartHits = SmartMatch.rank(kept, smartFile())
        best = smartHits.firstOrNull { it.score.passes && it.hit.url !in screening }
        // 状态行的条数 = 单子里看得见的（去掉弹幕和名字像弹幕、没查出干净的；不加说明文字）
        if (thunder is SourceState.Done) thunder = SourceState.Done(thunderHits.count { !isDanmaku(it) && !suspected(it) })
        if (shooter is SourceState.Done) shooter = SourceState.Done(shooterHits.count { !isDanmaku(it) && !suspected(it) })
        if (server is SourceState.Done) server = SourceState.Done(serverHits.count { !isDanmaku(it) && !suspected(it) })
    }

    /** 这部剧最大的季号、这一季的集数（[loadSeasons]；取不到 = null）。 */
    private var lastSeason: Int? = null
    private var seasonEpisodes: Int? = null

    /** 剧的别名（[loadAliases]）。 */
    private var aliases: List<String> = emptyList()

    /** 打分用的「这一集」：手动改过查询词就拿查询词当片名 token（同一套打分）。 */
    fun smartFile(): SmartFile = SmartFile.of(target, subtitleLangs, query, lastSeason, seasonEpisodes, aliases)

    /** 问一次别名（和字幕搜索同时进行；拿到了重新排一次）。 */
    private suspend fun loadAliases() {
        val src = aliasSource ?: return
        if (aliases.isNotEmpty()) return
        val got = try {
            src()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        if (got.isEmpty()) return
        aliases = got
        publish()
    }

    /** 单集：取一次这部剧的季列表（和弹幕的季守卫同一份缓存，只读）。 */
    private suspend fun loadSeasons() {
        val c = client ?: return
        val sid = target.seriesId ?: return
        if (target.season == null || lastSeason != null) return
        val list = DanmakuSeasonFacts.seasons(c, sid) ?: return
        lastSeason = list.mapNotNull { it.indexNumber }.filter { it > 0 }.maxOrNull()
        seasonEpisodes = list.firstOrNull { it.indexNumber == target.season }?.childCount
    }

    /** 这一条的分数（界面每一行要写理由时用）。 */
    fun scoreOf(hit: SubtitleHit): SmartScore =
        smartHits.firstOrNull { it.hit.key == hit.key }?.score ?: SmartMatch.score(hit, smartFile())

    /** 全部重搜（打开时、换语言时）。 */
    suspend fun run() = coroutineScope {
        serverHits = emptyList()
        thunderHits = emptyList()
        shooterHits = emptyList()
        screening = emptySet()
        loadSeasons()
        publish()
        server = SourceState.Busy
        if (online) {
            thunder = SourceState.Busy
            shooter = SourceState.Busy
        }
        val al = async { loadAliases() }
        val s = async { searchServer() }
        val up = async { checkUpload() }
        if (online) {
            val prints = if (print is PrintState.Got) (print as PrintState.Got).prints else fingerprint()
            val t = async { searchThunder(prints?.thunderCid) }
            val sh = async { searchShooter(prints?.shooterHash) }
            t.await()
            sh.await()
        } else {
            thunder = SourceState.Off("在线字幕已关")
            shooter = SourceState.Off("在线字幕已关")
        }
        s.await()
        up.await()
        al.await()
    }

    /** 问一次服务器：装没装 Subtitle Manager 插件、这个账号是不是管理员（只读 GET）。问不到 = 不出「保存到服务器」。 */
    private suspend fun checkUpload() {
        if (uploadChecked || !online) return
        val c = client ?: return
        val uid = c.userId ?: return
        uploadChecked = true
        canUpload = try {
            val policy = (c.getJson("/Users/${encodeURIComponent(uid)}") as? JsonObject)?.get("Policy") as? JsonObject
            if ((policy?.get("IsAdministrator") as? JsonPrimitive)?.booleanOrNull != true) false
            else subtitleUploadAvailable(c.getJson("/Plugins"), policy)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 迅雷 / 射手的一条「保存到服务器」（docs/subtitle-search.md §4.4）：下载、校验、转 UTF-8（和仅本次同一套 [fetchText]）→
     * 传给 Subtitle Manager 插件 → 等新的外挂字幕出现（同 [downloadToEmby]）。返回它的 index（等不到 = null，但服务器已经收下了）。
     * 失败抛 [SubtitleUploadException]（已有 / 没权限 / 别的）或 [SubtitleFetchException]（下载失败）。
     */
    suspend fun uploadToServer(hit: SubtitleHit, pollMs: Long = 1_500, attempts: Int = 10): Int? {
        val c = client ?: throw IllegalStateException("没有登录")
        val (text, format) = fetchText(hit)
        apply = ApplyState.Working(hit.key, "保存到服务器…")
        val answer = try {
            val res = c.http.request(
                "POST",
                SubtitleManagerPlugin.UPLOAD,
                RequestOptions(
                    query = mapOf("ItemId" to target.itemId, "Language" to uploadLanguageOf(hit.lang), "Format" to format.ext, "IsForced" to "false"),
                    textBody = text,
                    headers = mapOf("Content-Type" to "text/plain; charset=utf-8"),
                    timeoutMs = 60_000,
                    retry = RetryMode.Never,
                ),
            )
            uploadAnswerOf(res.text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbyException) {
            throw if (e.status == 401 || e.status == 403) SubtitleUploadException(UploadFail.Denied, "没有权限") else SubtitleUploadException(UploadFail.Other, "保存失败")
        }
        when (answer) {
            UploadAnswer.Ok -> {}
            UploadAnswer.Exists -> throw SubtitleUploadException(UploadFail.Exists, "服务器上已有这条字幕")
            is UploadAnswer.Failed -> throw SubtitleUploadException(UploadFail.Other, "保存失败")
        }
        apply = ApplyState.Working(hit.key, "等服务器刷新…")
        return awaitNewExternal(c, pollMs, attempts)
    }

    /** 每 [pollMs] 问一次这一条，最多 [attempts] 次：这个片源多出来的外挂字幕的 index。 */
    private suspend fun awaitNewExternal(c: EmbyClient, pollMs: Long, attempts: Int): Int? {
        val ms = target.mediaSourceId
        repeat(attempts) { n ->
            if (n > 0) delay(pollMs)
            val after = try {
                val item = c.getItem(target.itemId)
                SubtitleTarget.externalSubtitles(item.mediaSources.firstOrNull { it.id == ms } ?: item.mediaSources.firstOrNull())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (after != null) newExternalIndex(target.externalBefore, after)?.let { return it }
        }
        return null
    }

    /** 只按名字重搜迅雷（「换个名字搜」）。 */
    suspend fun runName() {
        if (!online) return
        val prints = (print as? PrintState.Got)?.prints
        searchThunder(prints?.thunderCid)
    }

    private suspend fun fingerprint(): FilePrints? {
        val plan = target.read
        if (plan == null) {
            print = PrintState.Missing("没有文件指纹，只按文件名")
            return null
        }
        print = PrintState.Busy
        val got = withTimeoutOrNull(20_000) { Fingerprint.compute(target.size) { span -> readSpan(plan, span) } }
        print = if (got != null) PrintState.Got(got) else PrintState.Missing("没有文件指纹，只按文件名")
        return got
    }

    /** 读一段：Emby 回 302 时手动跟——跳到别的主机就不带原来的任何头（Emby 的令牌在头里，不跟着走）。 */
    private suspend fun readSpan(plan: ReadPlan, span: ByteSpan): RangeBytes? {
        var url = plan.url
        var headers = plan.headers
        repeat(5) {
            val a = net.range(url, headers, span) ?: return null
            if (a.status == 206 && a.bytes != null) return RangeBytes(a.bytes, a.total)
            val loc = a.location
            if (a.status !in 300..399 || loc.isNullOrBlank()) return null
            val next = resolveLocation(url, loc)
            if (hostOf(next) != hostOf(url)) headers = emptyMap()
            url = next
        }
        return null
    }

    private suspend fun searchServer() {
        val c = client
        val ms = target.mediaSourceId
        if (c == null || ms == null) {
            server = SourceState.Off("没有登录")
            return
        }
        server = SourceState.Busy
        if (serverAllowed == null) serverAllowed = try {
            val uid = c.userId
            if (uid == null) null else serverDownloadAllowed((c.getJson("/Users/${encodeURIComponent(uid)}") as? JsonObject)?.get("Policy") as? JsonObject)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (serverAllowed == false) {
            server = SourceState.Off("账号没有「允许下载字幕」权限")
            return
        }
        server = try {
            val res = c.http.request(
                "GET",
                "/Items/${encodeURIComponent(target.itemId)}/RemoteSearch/Subtitles/${lang.emby}",
                RequestOptions(query = mapOf("MediaSourceId" to ms), timeoutMs = 45_000),
            )
            serverHits = SubtitleProviders.parseEmbyRemote(res.text, lang)
            publish()
            SourceState.Done(serverHits.count { !isDanmaku(it) && !suspected(it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbyException) {
            when (e.status) {
                401, 403 -> SourceState.Off("账号没有搜索字幕的权限")
                404 -> SourceState.Off("这台服务器不支持字幕搜索")
                else -> SourceState.Failed("服务器无应答（${e.status ?: "网络"}）")
            }
        } catch (_: Exception) {
            SourceState.Failed("服务器无应答")
        }
    }

    private suspend fun searchThunder(cid: String?) {
        if (lang != SearchLang.Chinese) {
            thunder = SourceState.Off("迅雷只有中文字幕")
            thunderHits = emptyList()
            publish()
            return
        }
        val q = query.trim().ifEmpty { target.nameQuery }
        thunder = SourceState.Busy
        var r = net.getText(SubtitleProviders.thunderUrl(q))
        var hits = if (r.ok) SubtitleProviders.parseThunder(r.text, cid) else emptyList()
        // 默认词没搜到 → 再试备选（中文剧集「剧名 第N集」/ 电影纯片名）；手动改过查询词就不擅自换。
        // 单集：默认词搜到的没几条是这一集的（迅雷没收录这一季时「剧名 S06E03」只回整部剧的字幕）→ 也问备选，接在后面
        val alt = target.altQuery
        if (r.ok && alt != null && alt != q && q == target.nameQuery && (hits.isEmpty() || !namesThisEpisode(hits))) {
            val r2 = net.getText(SubtitleProviders.thunderUrl(alt))
            if (r2.ok) {
                // 备选不带季：写明是别的季 / 别的集的不要（智能匹配反正也扣到底）
                val more = SubtitleProviders.parseThunder(r2.text, cid).filterNot { conflictsWithThisEpisode(it) }
                if (hits.isEmpty()) r = r2
                hits = (hits + more).distinctBy { it.key }
            }
        }
        if (!r.ok) {
            thunder = SourceState.Failed(r.failed ?: "迅雷回了 ${r.status}")
            return
        }
        thunderHits = hits
        // 先上单子（名字像弹幕的压在后面、正在查的不当最合适），状态行等查完再写条数
        screening = screenTargets(hits).mapNotNull { it.url }.toSet()
        publish()
        screen(hits)
        thunder = SourceState.Done(thunderHits.count { !isDanmaku(it) && !suspected(it) })
    }

    /**
     * 后台预查哪几条：按智能匹配的顺序取前 [SCREEN_TAKE] 条（最可能当「最合适」的先查），再加上名字像弹幕的（最多 [SCREEN_SUSPECTS] 条：
     * 它们不查就不出现在单子里，查出干净才放出来）。
     */
    private fun screenTargets(hits: List<SubtitleHit>): List<SubtitleHit> {
        val ranked = SmartMatch.rank(hits, smartFile()).map { it.hit }.filter { it.url != null && it.subFormat != null }
        val top = ranked.take(SCREEN_TAKE)
        val named = ranked.filter { it !in top && DanmakuSniff.nameLooksLikeDanmaku(it.name) }.take(SCREEN_SUSPECTS)
        return top + named
    }

    /**
     * 迅雷的结果在后台先下载、判一次是不是弹幕（§4.5）：最多 [SCREEN_TAKE] 条、同时 [SCREEN_PARALLEL] 个、每个 [SCREEN_TIMEOUT_MS]、
     * 单个最大 [SCREEN_MAX_BYTES]；判过的按 url 记在 [DanmakuCache]（同一次播放里不重复下载）。下载 / 解码失败 = 不知道（照常留着，点的时候再查）。
     */
    private suspend fun screen(hits: List<SubtitleHit>) {
        val targets = screenTargets(hits)
        // 前 12 条以外、以前判过的（点过的、上一次搜索查过的）也直接用记下的结果
        for (h in hits) {
            val url = h.url ?: continue
            when (DanmakuCache.get(url)?.isDanmaku) {
                true -> danmakuUrls = danmakuUrls + url
                false -> cleanUrls = cleanUrls + url
                null -> {}
            }
        }
        val gate = Semaphore(SCREEN_PARALLEL)
        val verdicts = coroutineScope {
            targets.map { h ->
                async {
                    val url = h.url!!
                    url to (DanmakuCache.get(url) ?: gate.withPermit { screenOne(url, h.subFormat!!) }?.also { DanmakuCache.put(url, it) })
                }
            }.awaitAll()
        }
        for ((url, v) in verdicts) when (v?.isDanmaku) {
            true -> danmakuUrls = danmakuUrls + url
            false -> cleanUrls = cleanUrls + url
            null -> {}
        }
        screening = emptySet()
        publish()
    }

    private suspend fun screenOne(url: String, format: SubFormat): DanmakuVerdict? {
        val r = withTimeoutOrNull(SCREEN_TIMEOUT_MS) { net.getBytesCapped(url, SCREEN_MAX_BYTES, SCREEN_TIMEOUT_MS) } ?: return null
        if (!r.ok) return null
        val d = SubtitleText.decode(r.bytes, format, r.contentType) as? DecodedSubtitle.Ok ?: return null
        return DanmakuSniff.judge(d.text, format)
    }

    /**
     * 这批结果里写明是这一集的（和智能匹配的 [SmartScore.pinned] 同一套：集号对上；第 2 季起季也要写明、对上）够不够 [EPISODE_HITS] 条；
     * 不是单集 = 够。2026-11-28：以前没写季的「第 2 集」也算，《头文字D》第 6 季第 2 集的默认词搜回来一堆别的季的就算够了，
     * 备选「头文字D 第2集」（里面才有 Final Stage - 02）没去搜。
     */
    private fun namesThisEpisode(hits: List<SubtitleHit>): Boolean {
        if (target.episode == null) return true
        val file = smartFile()
        return hits.count { SmartMatch.score(it, file).pinned } >= EPISODE_HITS
    }

    /** 名字里写明的季 / 集和这一集对不上（两边都写了才算）。 */
    private fun conflictsWithThisEpisode(h: SubtitleHit): Boolean {
        val p = parseName(h.name)
        return (p.season != null && target.season != null && p.season != target.season) ||
            (p.episode != null && target.episode != null && p.episode != target.episode)
    }

    private suspend fun searchShooter(hash: String?) {
        shooter = SourceState.Busy
        // 按名字配的那一路也用 nameQuery：strm 的 CDN 文件名无意义时它是元数据查询（§3）
        val r = net.postForm(SubtitleProviders.SHOOTER_API, SubtitleProviders.shooterForm(hash, target.shooterName, lang))
        shooter = if (r.ok) {
            shooterHits = SubtitleProviders.parseShooter(r.text, target.shooterName, lang, hashSent = !hash.isNullOrEmpty())
            publish()
            SourceState.Done(shooterHits.count { !isDanmaku(it) && !suspected(it) })
        } else SourceState.Failed(r.failed ?: "射手回了 ${r.status}")
    }

    /**
     * 服务器源：让 Emby 下载并存进去，等新字幕出现。返回新字幕的 index（等不到 = null，但服务器已经收下了）。抛 [EmbyException]（401 / 403 / 网络）。
     */
    suspend fun downloadToEmby(hit: SubtitleHit, pollMs: Long = 1_500, attempts: Int = 10): Int? {
        val c = client ?: throw IllegalStateException("没有登录")
        val ms = target.mediaSourceId ?: throw IllegalStateException("没有片源")
        val id = hit.serverId ?: throw IllegalStateException("不是服务器的结果")
        apply = ApplyState.Working(hit.key, "存进 Emby…")
        c.http.send(
            "POST",
            "/Items/${encodeURIComponent(target.itemId)}/RemoteSearch/Subtitles/${encodeURIComponent(id)}",
            RequestOptions(query = mapOf("MediaSourceId" to ms), timeoutMs = 60_000, retry = RetryMode.Never),
        )
        apply = ApplyState.Working(hit.key, "等服务器刷新…")
        repeat(attempts) { n ->
            if (n > 0) delay(pollMs)
            val after = try {
                val item = c.getItem(target.itemId)
                SubtitleTarget.externalSubtitles(item.mediaSources.firstOrNull { it.id == ms } ?: item.mediaSources.firstOrNull())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (after != null) newExternalIndex(target.externalBefore, after)?.let { return it }
        }
        return null
    }

    /** 迅雷 / 射手：下载并校验，返回 UTF-8 文本（失败 = 抛 [SubtitleFetchException]，带中文原因）。 */
    suspend fun fetchText(hit: SubtitleHit): Pair<String, SubFormat> {
        val url = hit.url ?: throw SubtitleFetchException("没有下载地址")
        val format = hit.subFormat ?: throw SubtitleFetchException("不认识的字幕格式")
        apply = ApplyState.Working(hit.key, "下载中…")
        val r = net.getBytes(url)
        if (!r.ok) throw SubtitleFetchException(r.failed?.let { "下载失败：$it" } ?: "下载失败（${r.status}）")
        val text = when (val d = SubtitleText.decode(r.bytes, format, r.contentType)) {
            is DecodedSubtitle.Ok -> d.text
            is DecodedSubtitle.Bad -> throw SubtitleFetchException(d.why)
        }
        // 点的时候再查一次（§4.5）：是弹幕 → 不加载、不上传（调用方都在拿到文本之前停下），从单子里去掉
        val v = DanmakuSniff.judge(text, format)
        DanmakuCache.put(url, v)
        if (v.isDanmaku) {
            markDanmaku(hit)
            throw DanmakuFileException(v)
        }
        return text to format
    }

    /** 点的时候判成弹幕：记下、这一行留着写标签（[labelled]），别的地方都不算它。 */
    private fun markDanmaku(hit: SubtitleHit) {
        val url = hit.url ?: return
        danmakuUrls = danmakuUrls + url
        cleanUrls = cleanUrls - url
        labelled = hit.key
        publish()
    }
}

open class SubtitleFetchException(message: String) : Exception(message)

/** 下载下来是弹幕（§4.5）：不加载、不上传；行上写 [DANMAKU_LABEL]。 */
class DanmakuFileException(val verdict: DanmakuVerdict) : SubtitleFetchException(DANMAKU_LABEL)

/** 默认词的迅雷结果里至少这么多条写明是这一集，才不再问备选。 */
private const val EPISODE_HITS = 3

/** 弹幕预查（§4.5）：最多查前几条迅雷结果。 */
const val SCREEN_TAKE = 12

/** 名字像弹幕、排在 [SCREEN_TAKE] 以外的，再多查这么多条（查出干净才出现在单子里）。 */
const val SCREEN_SUSPECTS = 8

/** 弹幕预查：同时下几个。 */
const val SCREEN_PARALLEL = 3

/** 弹幕预查：每个的超时。 */
const val SCREEN_TIMEOUT_MS = 10_000L

/** 弹幕预查：单个最大（超过不下、不判）。 */
const val SCREEN_MAX_BYTES = 2L shl 20

/** `scheme://host:port`（比较是不是同一台）。 */
internal fun hostOf(url: String): String {
    val i = url.indexOf("://")
    if (i < 0) return ""
    val rest = url.substring(i + 3)
    return url.substring(0, i).lowercase() + "://" + rest.substringBefore('/').substringBefore('?').lowercase()
}

/** 3xx 的 `Location` → 绝对地址（相对路径接在原主机上）。 */
internal fun resolveLocation(base: String, loc: String): String = when {
    loc.startsWith("http://") || loc.startsWith("https://") -> loc
    loc.startsWith("//") -> base.substringBefore("://") + ":" + loc
    loc.startsWith("/") -> hostOf(base) + loc
    else -> base.substringBeforeLast('/') + "/" + loc
}

/** 哈希样的文件名（`a1b2c3d4`、32/40 位十六进制）：拿去按名字搜没有意义。 */
private val HASH_NAME = Regex("^[0-9a-fA-F]{8,}$")

/** 分词：拉丁 / 数字 / 中日韩文字以外都当分隔。 */
private val NAME_SPLIT = Regex("[^A-Za-z0-9" + '\u4e00' + "-" + '\u9fff' + "]+")

/** 网盘 / 中转常见的通用文件名：本身不含片名信息。 */
private val GENERIC_FILE_WORDS = setOf("stream", "video", "play", "playlist", "index", "master", "download", "media", "file", "movie", "film", "tmp", "temp", "out", "output", "default", "main")

private fun isCjkChar(c: Char): Boolean = c >= '\u4e00' && c <= '\u9fff'
