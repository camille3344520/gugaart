package com.gugaart.ui.download

import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.data.Title
import com.gugugaga.app.AppGraph
import com.gugugaga.app.net.STRM_DEFAULT_UA
import com.gugugaga.app.player.StrmSettings
import com.gugugaga.app.player.strmCdnHeaders
import com.gugugaga.app.session.AppSession
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyJson
import com.gugugaga.core.isRemoteSource
import com.gugugaga.core.pickMediaSource
import com.gugugaga.core.platformHttpClient
import com.gugugaga.core.setQueryParams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** 离线进度回报时一条最多等多久。 */
const val REPORT_SEND_TIMEOUT_MS = 15_000L

/** 一条下载此刻在等什么（不存盘，跟着网络和设置走）。 */
enum class Waiting { None, Wifi, Network }

/** 界面看到的一行：存下的记录 + 是不是正在下 + 在等什么。 */
data class DownloadRow(val entry: DownloadEntry, val running: Boolean, val waiting: Waiting) {
    val key: String get() = entry.key
}

/** 纯函数：现在的网 + 「只用 Wi-Fi 下载」→ 能不能下。 */
fun gateOf(net: NetState, wifiOnly: Boolean): Waiting = when {
    !net.online -> Waiting.Network
    wifiOnly && !net.unmetered -> Waiting.Wifi
    else -> Waiting.None
}

/** 纯函数：界面上的行（排队的、没在下的那些才「等」）。 */
fun rowsOf(entries: List<DownloadEntry>, running: String?, gate: Waiting): List<DownloadRow> = entries.map { e ->
    val isRunning = e.key == running
    DownloadRow(e, isRunning, if (e.status == DlStatus.Queued && !isRunning) gate else Waiting.None)
}

/** 纯函数：下一条该下的（这台服务器这个账号、排着队的、先来的先下）。 */
fun pickNext(entries: List<DownloadEntry>, serverId: String, userId: String): DownloadEntry? =
    entries.firstOrNull { it.status == DlStatus.Queued && it.serverId == serverId && it.userId == userId }

/** 通知栏的一行（安卓前台服务用）：(标题, 正文)；没有在下的 = null。 */
fun notificationText(rows: List<DownloadRow>): Pair<String, String>? {
    val r = rows.firstOrNull { it.running } ?: return null
    val queued = rows.count { !it.running && it.entry.status == DlStatus.Queued }
    val name = r.entry.episodeLine?.let { "${r.entry.displayName} · $it" } ?: r.entry.displayName
    val state = downloadLabel(r)
    return name to (if (queued > 0) "$state · 还有 $queued 项" else state)
}

/**
 * 离线下载的队列和状态（1.6 批次 E；三端共用，平台只给 [host]）。
 *
 * - 一次只下一条（先来的先下），只下当前服务器当前账号的；换了账号，别的账号的排着等。
 * - 断网 / 「只用 Wi-Fi 下载」开着又不在 Wi-Fi：停下来等（[Waiting]），网回来接着下（断点续传）。
 * - 下载在自己的协程作用域里跑（不跟着界面走）：安卓的 Activity 没了也接着下（前台服务保着进程）。
 * - 记录（[INDEX_FILE]）和待回报的进度（[REPORTS_FILE]）都在下载目录里。
 */
@OptIn(ExperimentalTime::class)
object Downloads {
    /** 壳设：安卓手机、iPhone = 有；电视、电脑 = null = 没有下载入口。 */
    var host: DownloadHost? = null

    /** 测试缝：换掉网络那一层（默认 = ktor）。 */
    var transportOverride: DownloadTransport? = null

    /** 测试缝：现在几点（毫秒）。 */
    var clock: () -> Long = { Clock.System.now().toEpochMilliseconds() }

    fun now(): Long = clock()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 下载自己的后台作用域（本地播放的心跳也在这里跑）。 */
    internal val background: CoroutineScope get() = scope
    private val entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    private val runningKey = MutableStateFlow<String?>(null)
    private val net = MutableStateFlow(NetState.UNKNOWN_ONLINE)
    private val wifiOnly = MutableStateFlow(true)
    private val session = MutableStateFlow<AppSession?>(null)
    private val reports = MutableStateFlow<List<PendingReport>>(emptyList())

    /** 界面看的行（按加进来的先后）。 */
    val rows: StateFlow<List<DownloadRow>> = combine(entries, runningKey, net, wifiOnly) { e, r, n, w -> rowsOf(e, r, gateOf(n, w)) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 现在的网（界面：断网时首页出「下载」一栏）。 */
    val network: StateFlow<NetState> = net

    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val saves = Channel<Unit>(Channel.CONFLATED)
    private val reportCmds = Channel<ReportCmd>(Channel.UNLIMITED)
    private var started = false
    private var current: Job? = null
    private var graph: AppGraph? = null
    private var alive = false
    private val transport: DownloadTransport by lazy { KtorDownloadTransport(platformHttpClient()) }

    private sealed interface ReportCmd {
        data class Add(val r: PendingReport) : ReportCmd
        data object Flush : ReportCmd
    }

    /** 有没有下载这回事（壳设了 host）。界面只在手机版式上出入口（[com.gugaart.ui.download.rememberDownloads]）。 */
    val available: Boolean get() = host != null

    /** 根界面每造一个 [AppGraph] 调一次：读记录、跟上会话 / 网络 / 设置，起下载和回报的两个循环（只起一次）。 */
    fun bind(g: AppGraph) {
        val h = host ?: return
        graph = g
        wifiOnly.value = GugaPrefs.parse(g.deps.local.get(GugaSettingsStore.KEY)).downloadWifiOnly
        if (!started) {
            started = true
            // 先同步拿一次现在的网（别让会话一恢复就以「在线」去回报）
            net.value = h.network.value
            entries.value = loadIndex(h.storage)
            // 上一次没等到「停下」就结束了的播放：按最后的位置补一条停下
            reports.value = ReportLog.closeOpen(ReportLog.parse(h.storage.readText(REPORTS_FILE)), except = null)
            scope.launch {
                h.network.collect { n ->
                    val back = n.online && !net.value.online
                    net.value = n
                    wake.trySend(Unit)
                    if (back) reportCmds.trySend(ReportCmd.Flush)
                }
            }
            scope.launch { workLoop(h) }
            scope.launch { reportLoop(h) }
            scope.launch {
                for (u in saves) {
                    runCatching { h.storage.writeText(INDEX_FILE, DownloadIndex.encode(entries.value)) }
                    delay(1_000)
                }
            }
        }
        g.scope.launch {
            g.session.session.collect { s ->
                session.value = s
                wake.trySend(Unit)
                if (s != null) reportCmds.trySend(ReportCmd.Flush)
            }
        }
    }

    /** 「只用 Wi-Fi 下载」改了（设置页调）。 */
    fun setWifiOnly(on: Boolean) {
        wifiOnly.value = on
        wake.trySend(Unit)
    }

    /* ---- 查询 ---- */

    fun rowFor(serverId: String, userId: String, itemId: String): DownloadRow? =
        rows.value.firstOrNull { it.entry.serverId == serverId && it.entry.userId == userId && it.entry.itemId == itemId }

    /** 下好了、文件还在的那一条（播放器用）。 */
    fun playable(serverId: String, userId: String, itemId: String): DownloadEntry? {
        val h = host ?: return null
        val e = entries.value.firstOrNull { it.serverId == serverId && it.userId == userId && it.itemId == itemId && it.status == DlStatus.Done } ?: return null
        return e.takeIf { h.storage.length(it.fileName) > 0 }
    }

    fun fileUrlOf(e: DownloadEntry): String? = host?.storage?.fileUrl(e.fileName)

    /** 下载时存下的条目原文（读不懂 = null）。 */
    fun storedItem(e: DownloadEntry): BaseItemDto? {
        if (e.itemJson.isEmpty()) return null
        return try {
            EmbyJson.decodeFromString(BaseItemDto.serializer(), e.itemJson)
        } catch (_: Exception) {
            null
        }
    }

    /** 这部剧下好了的几集（断网时播放器的上一集 / 下一集只在这些里走）。 */
    fun downloadedEpisodes(serverId: String, userId: String, seriesId: String): List<BaseItemDto> =
        entries.value.filter { it.serverId == serverId && it.userId == userId && it.seriesId == seriesId && it.status == DlStatus.Done }
            .sortedWith(compareBy({ it.seasonNo ?: Int.MAX_VALUE }, { it.episodeNo ?: Int.MAX_VALUE }))
            .mapNotNull { e -> storedItem(e)?.let { storedItemWithPosition(it, e) } }

    /** 这一条还有没报上去的离线进度。 */
    fun hasPending(itemId: String): Boolean = reports.value.any { it.itemId == itemId }

    /* ---- 操作 ---- */

    /**
     * 加进队列（详情页的「下载」、整季下载）。已经在的不重复加；失败了的重新排队。
     * [mediaSourceId]：详情页选中的版本（多版本时）；null = 第一个。
     */
    fun enqueue(s: AppSession, titles: List<Title>, mediaSourceId: String? = null) {
        val h = host ?: return
        if (titles.isEmpty()) return
        h.onUserStart()
        val at = now()
        entries.update { list ->
            val out = list.toMutableList()
            for ((i, t) in titles.withIndex()) {
                val key = DownloadEntry.keyOf(s.serverId, s.userId, t.id)
                val at0 = out.indexOfFirst { it.key == key }
                if (at0 >= 0) {
                    if (out[at0].status == DlStatus.Failed) out[at0] = out[at0].copy(status = DlStatus.Queued, error = null)
                    continue
                }
                val src = t.sources.firstOrNull { it.id == mediaSourceId } ?: t.sources.firstOrNull()
                out += DownloadEntry(
                    key = key,
                    serverId = s.serverId,
                    userId = s.userId,
                    itemId = t.id,
                    kind = if (t.kind == com.gugaart.ui.data.Kind.Episode) "Episode" else "Movie",
                    name = t.name,
                    seriesId = t.seriesId,
                    seriesName = t.seriesName,
                    seasonNo = t.seasonNo,
                    episodeNo = t.episodeNo,
                    mediaSourceId = mediaSourceId ?: src?.id,
                    fileName = fileNameFor(key, src?.container),
                    total = src?.size?.coerceAtLeast(0) ?: 0,
                    addedAt = at + i,
                )
            }
            out
        }
        save()
        wake.trySend(Unit)
    }

    /** 失败了的那一条重新排队。 */
    fun retry(key: String) {
        entries.update { list -> list.map { if (it.key == key && it.status == DlStatus.Failed) it.copy(status = DlStatus.Queued, error = null) else it } }
        save()
        wake.trySend(Unit)
    }

    /** 删除下载（正在下的先停）：记录和文件一起删。 */
    fun delete(key: String) {
        val h = host ?: return
        val e = entries.value.firstOrNull { it.key == key } ?: return
        entries.update { list -> list.filterNot { it.key == key } }
        save()
        scope.launch {
            if (runningKey.value == key) current?.cancelAndJoin()
            h.storage.delete(e.fileName)
            h.storage.delete(partNameOf(e.fileName))
            wake.trySend(Unit)
        }
    }

    /** 本地播放的进度（[LocalReporter]）：记下来（顺序不乱：一个协程按到达的先后处理）。 */
    internal fun recordPosition(r: PendingReport) {
        val ok = reportCmds.trySend(ReportCmd.Add(r)).isSuccess
        if (r.stopped || !ok) log("离线进度：" + (if (r.stopped) "停下" else "进度") + " " + r.positionTicks / 10_000 + " ms" + (if (ok) "" else "（没记上）"))
    }

    /** 运行日志（设置 › 扩展 › 运行日志；安卓 debug 包也进 logcat）。不写地址和令牌。 */
    internal fun log(message: String) {
        runCatching { graph?.log?.add("perf", "[download] " + message) }
    }

    /* ---- 两个循环 ---- */

    private suspend fun workLoop(h: DownloadHost) {
        while (true) {
            val s = session.value
            val next = s?.let { pickNext(entries.value, it.serverId, it.userId) }
            if (s == null || next == null || gateOf(net.value, wifiOnly.value) != Waiting.None) {
                setAlive(h, false)
                wake.receive()
                continue
            }
            setAlive(h, true)
            runningKey.value = next.key
            val job = scope.launch { runOne(h, s, next) }
            current = job
            // 断网 / 离开 Wi-Fi / 换了账号：停下（已经下的留着，回来接着下）
            val watcher = scope.launch {
                combine(net, wifiOnly, session) { n, w, ss -> gateOf(n, w) != Waiting.None || ss == null || ss.serverId != s.serverId || ss.userId != s.userId }.first { it }
                job.cancel()
            }
            job.join()
            watcher.cancel()
            current = null
            runningKey.value = null
        }
    }

    private fun setAlive(h: DownloadHost, on: Boolean) {
        if (alive == on) return
        alive = on
        runCatching { h.keepAlive(on) }
    }

    private suspend fun runOne(h: DownloadHost, s: AppSession, first: DownloadEntry) {
        var e = first
        // 第一次下：先把完整的条目（媒体流、strm 地址、大小）存下来，断网时详情 / 播放器要用
        if (e.itemJson.isEmpty()) {
            val item = try {
                s.client.getItem(e.itemId)
            } catch (x: CancellationException) {
                throw x
            } catch (_: Exception) {
                fail(e.key, "网络断了")
                return
            }
            val src = pickMediaSource(item.mediaSources, e.mediaSourceId)
            val fresh = h.storage.length(partNameOf(e.fileName)) <= 0
            e = e.copy(
                itemJson = EmbyJson.encodeToString(BaseItemDto.serializer(), item),
                mediaSourceId = e.mediaSourceId ?: src?.id,
                fileName = if (fresh) fileNameFor(e.key, src?.container ?: item.container) else e.fileName,
                total = if (e.total > 0) e.total else src?.size?.coerceAtLeast(0) ?: 0,
                seriesName = e.seriesName ?: item.seriesName,
                seasonNo = e.seasonNo ?: item.parentIndexNumber,
                episodeNo = e.episodeNo ?: item.indexNumber,
            )
            replace(e)
        }
        val entry = e
        var lastEmit = 0L
        val result = runTransfer(
            transportOverride ?: transport,
            h.storage,
            partNameOf(entry.fileName),
            entry.fileName,
            entry.total,
            request = { requestFor(entry, s) },
            onProgress = { done, total ->
                val t = now()
                if (t - lastEmit >= 300) {
                    lastEmit = t
                    progress(entry.key, done, total)
                }
            },
        )
        when (result) {
            is TransferResult.Done -> {
                entries.update { list -> list.map { if (it.key == entry.key) it.copy(status = DlStatus.Done, total = result.total, done = result.total, error = null) else it } }
                save()
            }
            is TransferResult.Failed -> fail(entry.key, result.reason)
        }
    }

    private fun replace(e: DownloadEntry) {
        entries.update { list -> list.map { if (it.key == e.key) e else it } }
        save()
    }

    private fun progress(key: String, done: Long, total: Long) {
        entries.update { list -> list.map { if (it.key == key) it.copy(done = done, total = if (total > 0) total else it.total) else it } }
        save()
    }

    private fun fail(key: String, reason: String) {
        entries.update { list -> list.map { if (it.key == key) it.copy(status = DlStatus.Failed, error = reason) else it } }
        save()
    }

    private fun save() {
        saves.trySend(Unit)
    }

    /**
     * 这一条的下载地址（每次尝试现算）：strm（片源是 http(s) 地址的远程源）= strm 的目标地址，头和播放时一样
     * （片源要求的头 + 设置 › strm 直链里的 UA / Referer，没有 UA 用播放时的默认 UA），**不带 Emby 令牌**；
     * 其它 = Emby `/Items/{id}/Download`（原画；多版本时带 MediaSourceId）。
     */
    private fun requestFor(e: DownloadEntry, s: AppSession): DownloadRequest {
        val item = storedItem(e)
        val src = item?.let { pickMediaSource(it.mediaSources, e.mediaSourceId) }
        val path = src?.path
        if (src != null && isRemoteSource(src) && path != null && Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(path)) {
            val strm = StrmSettings.of(graph?.settings?.value ?: JsonObject(emptyMap()))
            return DownloadRequest(path, strmDownloadHeaders(src.requiredHttpHeaders, strm))
        }
        val base = s.client.getDownloadUrl(e.itemId)
        return DownloadRequest(e.mediaSourceId?.let { setQueryParams(base, mapOf("MediaSourceId" to it)) } ?: base, emptyMap())
    }

    private suspend fun reportLoop(h: DownloadHost) {
        for (cmd in reportCmds) {
            when (cmd) {
                is ReportCmd.Add -> {
                    val r = cmd.r
                    // 新的一次本地播放开始了：之前没停下的那些补一条停下
                    val base = if (!r.stopped && reports.value.none { it.session == r.session }) ReportLog.closeOpen(reports.value, except = r.session) else reports.value
                    reports.value = if (r.stopped) ReportLog.stop(base, r) else ReportLog.progress(base, r)
                    entries.update { list ->
                        list.map { if (it.serverId == r.serverId && it.userId == r.userId && it.itemId == r.itemId) it.copy(positionTicks = r.positionTicks) else it }
                    }
                    save()
                    runCatching { h.storage.writeText(REPORTS_FILE, ReportLog.encode(reports.value)) }
                    flush(h)
                }
                ReportCmd.Flush -> flush(h)
            }
        }
    }

    /**
     * 按顺序回报（[ReportLog.flush]）。断网不试；每一条最多等 [REPORT_SEND_TIMEOUT_MS]：这个循环也在记新的位置，
     * 不能被一个连不上的请求卡住（模拟器验收时出过：开机就断网，回报卡住，后面的心跳和停下都没记上）。
     */
    private suspend fun flush(h: DownloadHost) {
        if (!net.value.online) return
        val s = session.value ?: return
        if (reports.value.none { it.serverId == s.serverId && it.userId == s.userId }) return
        val left = ReportLog.flush(reports.value, s.serverId, s.userId) { r ->
            withTimeoutOrNull(REPORT_SEND_TIMEOUT_MS) { s.client.sendPending(r) } ?: error("report timed out")
        }
        val sent = reports.value.size - left.size
        if (sent > 0 || left.isNotEmpty()) log("离线进度回报：送出 " + sent + " 条，还剩 " + left.size + " 条")
        if (left != reports.value) {
            reports.value = left
            runCatching { h.storage.writeText(REPORTS_FILE, ReportLog.encode(left)) }
        }
    }

    /** 读记录：没下完的按盘上的 .part 校正进度；标着下完了、文件却不在了 → 失败（让用户看得见）。 */
    private fun loadIndex(storage: DownloadStorage): List<DownloadEntry> = DownloadIndex.parse(storage.readText(INDEX_FILE)).map { e ->
        when (e.status) {
            DlStatus.Done -> if (storage.length(e.fileName) > 0) e else e.copy(status = DlStatus.Failed, error = "文件不见了", done = 0)
            else -> e.copy(done = storage.length(partNameOf(e.fileName)).coerceAtLeast(0))
        }
    }

    /** 测试用：回到刚启动的样子（不碰盘）。 */
    internal fun resetForTest() {
        current?.cancel()
        entries.value = emptyList()
        reports.value = emptyList()
        runningKey.value = null
        session.value = null
        net.value = NetState.UNKNOWN_ONLINE
    }
}

/** strm 下载的头 = 播放时引擎发的那一套（上游 `strmCdnHeaders` + 默认 UA），没有 Emby 令牌。纯函数。 */
fun strmDownloadHeaders(required: Map<String, String>?, strm: StrmSettings): Map<String, String> {
    val h = strmCdnHeaders(required, strm)
    return if (h.keys.any { it.equals("User-Agent", ignoreCase = true) }) h else h + ("User-Agent" to STRM_DEFAULT_UA)
}
