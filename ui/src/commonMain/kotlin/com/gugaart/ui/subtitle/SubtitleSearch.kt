package com.gugaart.ui.subtitle

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyException
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.RequestOptions
import com.gugugaga.core.RetryMode
import com.gugugaga.core.encodeURIComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
) {
    override fun toString(): String = "SubtitleTarget(item=$itemId, source=$mediaSourceId, read=$read)"

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

        fun nameQueryOf(item: BaseItemDto, fileName: String?): String {
            if (fileName != null && VIDEO_EXT.containsMatchIn(fileName)) return fileName
            return if (item.type == "Episode" && item.seriesName != null) {
                "${item.seriesName} S${(item.parentIndexNumber ?: 1).toString().padStart(2, '0')}E${(item.indexNumber ?: 1).toString().padStart(2, '0')}"
            } else item.name
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
    /** [step]：「下载中…」「存进 Emby…」「等服务器刷新…」。 */
    data class Working(val key: String, val step: String) : ApplyState
    data class Done(val key: String, val note: String) : ApplyState

    /** [canLocal]：服务器那一路失败了，可以改成「仅本次播放」再试（只有能拿到字幕文件的才行——服务器源拿不到，所以总是 false）。 */
    data class Failed(val key: String, val why: String) : ApplyState
}

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
    var apply: ApplyState? by mutableStateOf(null)
        internal set

    private var serverHits: List<SubtitleHit> = emptyList()
    private var thunderHits: List<SubtitleHit> = emptyList()
    private var shooterHits: List<SubtitleHit> = emptyList()
    private var serverAllowed: Boolean? = null

    /** 还在搜（还没开始的也算：栏刚出来那一下不写「没有搜到」）。 */
    val searching: Boolean get() = listOf(server, thunder, shooter).any { it == SourceState.Busy || it == SourceState.Idle } || print == PrintState.Busy

    private fun publish() {
        hits = rankHits(serverHits + shooterHits + thunderHits, target.runtimeMs)
    }

    /** 全部重搜（打开时、换语言时）。 */
    suspend fun run() = coroutineScope {
        serverHits = emptyList()
        thunderHits = emptyList()
        shooterHits = emptyList()
        publish()
        server = SourceState.Busy
        if (online) {
            thunder = SourceState.Busy
            shooter = SourceState.Busy
        }
        val s = async { searchServer() }
        if (online) {
            val prints = if (print is PrintState.Got) (print as PrintState.Got).prints else fingerprint()
            val t = async { searchThunder(prints?.thunderCid) }
            val sh = async { searchShooter(prints?.shooterHash) }
            t.await()
            sh.await()
        } else {
            thunder = SourceState.Off("设置里关了在线字幕源")
            shooter = SourceState.Off("设置里关了在线字幕源")
        }
        s.await()
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
            print = PrintState.Missing("没取到文件指纹，只按文件名")
            return null
        }
        print = PrintState.Busy
        val got = withTimeoutOrNull(20_000) { Fingerprint.compute(target.size) { span -> readSpan(plan, span) } }
        print = if (got != null) PrintState.Got(got) else PrintState.Missing("没取到文件指纹，只按文件名")
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
            server = SourceState.Off("这个账号不能下载字幕（管理员可在 Emby 的 用户 › 权限 里打开「允许下载字幕」）")
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
            SourceState.Done(serverHits.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbyException) {
            when (e.status) {
                401, 403 -> SourceState.Off("服务器不让这个账号搜字幕")
                404 -> SourceState.Off("这台服务器不支持字幕搜索")
                else -> SourceState.Failed("服务器没回（${e.status ?: "网络"}）")
            }
        } catch (_: Exception) {
            SourceState.Failed("服务器没回")
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
        val r = net.getText(SubtitleProviders.thunderUrl(q))
        thunder = if (r.ok) {
            thunderHits = SubtitleProviders.parseThunder(r.text, cid)
            publish()
            SourceState.Done(thunderHits.size)
        } else SourceState.Failed(r.failed ?: "迅雷回了 ${r.status}")
    }

    private suspend fun searchShooter(hash: String?) {
        shooter = SourceState.Busy
        val r = net.postForm(SubtitleProviders.SHOOTER_API, SubtitleProviders.shooterForm(hash, target.fileName, lang))
        shooter = if (r.ok) {
            shooterHits = SubtitleProviders.parseShooter(r.text, target.fileName, lang, hashSent = !hash.isNullOrEmpty())
            publish()
            SourceState.Done(shooterHits.size)
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
        return when (val d = SubtitleText.decode(r.bytes, format, r.contentType)) {
            is DecodedSubtitle.Ok -> d.text to format
            is DecodedSubtitle.Bad -> throw SubtitleFetchException(d.why)
        }
    }
}

class SubtitleFetchException(message: String) : Exception(message)

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
