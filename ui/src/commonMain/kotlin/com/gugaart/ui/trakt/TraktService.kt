package com.gugaart.ui.trakt

import com.gugaart.ui.data.GugaPrefs
import com.gugugaga.app.AppGraph
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.player.PlayController
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.LibraryQuery
import com.gugugaga.core.Transport
import com.gugugaga.core.currentTimeMillis
import com.gugugaga.core.platformHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/*
 * Trakt 的两件事（TraktClient.kt 说了怎么连）：
 *  1. 看的时候记到 Trakt（scrobble）：播放器开始播 = start，暂停 = pause，换集 / 退出 = stop（带进度；Trakt 在 80% 以上记为看过）。
 *     片子用 Emby 刮削到的 TMDB / IMDb / TVDB 编号对上 Trakt；单集没有自己的编号就用整部剧的编号 + 季号集号。
 *  2. Trakt 上看过的同步回 Emby（用户点「同步」，或开着「打开应用时自动同步」）：只把 Emby 里还没看过、Trakt 上看过的标成已看，
 *     不会把 Emby 里看过的改回没看。**这会改 Emby 上的已看状态**（用户选的）。
 */

/** 本机的 Trakt 开关（不是秘密）：看的时候记、打开应用时自动同步。 */
data class TraktPrefs(val scrobble: Boolean = true, val autoSync: Boolean = false) {
    fun encode(): String = buildJsonObject { put("scrobble", scrobble); put("autoSync", autoSync) }.toString()

    companion object {
        const val KEY = "gugaart.trakt.prefs.v1"

        fun parse(raw: String?): TraktPrefs {
            val o = GugaPrefs.parseObject(raw) ?: return TraktPrefs()
            fun b(k: String, d: Boolean) = (o[k] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: d
            return TraktPrefs(b("scrobble", true), b("autoSync", false))
        }
    }
}

/** Emby 的编号（ProviderIds 的键大小写不一）→ Trakt 的 ids 对象（tmdb / tvdb 是数字，imdb 是字符串）。纯函数，有单测。 */
fun traktIdsOf(providerIds: Map<String, String?>): JsonObject? {
    fun pid(k: String) = providerIds.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value?.trim()?.takeIf { it.isNotEmpty() }
    val out = LinkedHashMap<String, JsonElement>()
    pid("Tmdb")?.toLongOrNull()?.let { out["tmdb"] = JsonPrimitive(it) }
    pid("Tvdb")?.toLongOrNull()?.let { out["tvdb"] = JsonPrimitive(it) }
    pid("Imdb")?.takeIf { it.startsWith("tt") }?.let { out["imdb"] = JsonPrimitive(it) }
    return if (out.isEmpty()) null else JsonObject(out)
}

/**
 * 一个 Emby 条目 → scrobble 的主体（不含进度）；对不上 = null。[series] = 单集所属的剧。纯函数，有单测。
 */
fun scrobbleTarget(item: BaseItemDto, series: BaseItemDto?): JsonObject? = when (item.type) {
    "Movie" -> traktIdsOf(item.providerIds)?.let { buildJsonObject { put("movie", buildJsonObject { put("ids", it) }) } }
    "Episode" -> {
        val own = traktIdsOf(item.providerIds)?.takeIf { it.containsKey("tvdb") || it.containsKey("tmdb") }
        val show = series?.let { traktIdsOf(it.providerIds) }
        val season = item.parentIndexNumber
        val number = item.indexNumber
        when {
            show != null && season != null && number != null -> buildJsonObject {
                put("show", buildJsonObject { put("ids", show) })
                put("episode", buildJsonObject { put("season", season); put("number", number) })
            }
            own != null -> buildJsonObject { put("episode", buildJsonObject { put("ids", own) }) }
            else -> null
        }
    }
    else -> null
}

/**
 * 手动标已看 / 未看时给 Trakt 的观看记录主体（/sync/history 和 /sync/history/remove 同一种）：电影 / 一整部剧 / 一季 / 一集。
 * 对不上 = null。纯函数，有单测。
 */
fun traktHistoryBody(item: BaseItemDto, series: BaseItemDto?): JsonObject? {
    fun list(key: String, e: JsonObject) = buildJsonObject { put(key, JsonArray(listOf(e))) }
    return when (item.type) {
        "Movie" -> traktIdsOf(item.providerIds)?.let { list("movies", buildJsonObject { put("ids", it) }) }
        "Series" -> traktIdsOf(item.providerIds)?.let { list("shows", buildJsonObject { put("ids", it) }) }
        "Season" -> {
            val show = series?.let { traktIdsOf(it.providerIds) } ?: return null
            val no = item.indexNumber ?: return null
            list("shows", buildJsonObject { put("ids", show); put("seasons", JsonArray(listOf(buildJsonObject { put("number", no) }))) })
        }
        "Episode" -> {
            val show = series?.let { traktIdsOf(it.providerIds) }
            val s = item.parentIndexNumber
            val n = item.indexNumber
            if (show != null && s != null && n != null) {
                list("shows", buildJsonObject {
                    put("ids", show)
                    put("seasons", JsonArray(listOf(buildJsonObject { put("number", s); put("episodes", JsonArray(listOf(buildJsonObject { put("number", n) }))) })))
                })
            } else {
                traktIdsOf(item.providerIds)?.takeIf { it.containsKey("tvdb") || it.containsKey("tmdb") }?.let { list("episodes", buildJsonObject { put("ids", it) }) }
            }
        }
        else -> null
    }
}

/**
 * 正在播的条目 → scrobble 的主体（不含进度）；对不上 / 查不到 = null。[client] = 条目 [itemId] 所在的那台（正在播的那台）。
 * [dto] 只有确实是这个条目（id 一样）且带着编号时才直接用；单集的剧也从同一台取。换台的一瞬间控制器的条目 id 已经是别台的，
 * 条目对象可能还是上一台的——那时不用它（它的剧 id 是上一台的）。
 */
suspend fun resolveScrobbleTarget(client: EmbyClient, itemId: String, dto: BaseItemDto?): JsonObject? = try {
    val o = ItemsRequestOptions(fields = listOf("ProviderIds"), enableImages = false, enableUserData = false)
    val full = dto?.takeIf { it.id == itemId && it.providerIds.isNotEmpty() } ?: client.getItem(itemId, o)
    val series = if (full.type == "Episode") full.seriesId?.let { client.getItem(it, o) } else null
    scrobbleTarget(full, series)
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/** 两个 scrobble 主体是不是同一部 / 同一集（不看 `_id` 和进度）：换台前后是同一部就接着算。纯函数，有单测。 */
fun sameScrobbleTitle(a: JsonObject, b: JsonObject): Boolean {
    fun core(o: JsonObject) = JsonObject(o.filterKeys { it != "_id" && it != "progress" })
    return core(a) == core(b)
}

/** 主体 + 进度（0–100）。 */
fun withProgress(target: JsonObject, progress: Double): JsonObject = JsonObject(LinkedHashMap(target).also { it["progress"] = JsonPrimitive((progress.coerceIn(0.0, 100.0) * 100).roundToInt() / 100.0) })

/** 连接的状态（设置页显示）。 */
sealed interface TraktState {
    /** 这一版没带 Trakt 应用（开发者没配 Key）。 */
    data object Unavailable : TraktState
    data object Disconnected : TraktState
    /** 正在等用户在网页上输入 [code]。 */
    data class Waiting(val code: TraktDeviceCode) : TraktState
    data class Connected(val user: String?) : TraktState
    /** 授权过期 / 被吊销，续不上：要重新连接（参考 CapyPlayer「Trakt 授权过期状态优化」）。 */
    data class Expired(val user: String?) : TraktState
}

/** Trakt（进程里一个）。壳 [install] 编进包里的 Trakt 应用；界面 [bind] 上游的 AppGraph。 */
object TraktService {
    private var app = TraktApp("", "")
    private var transport: Transport? = null
    private var graph: AppGraph? = null
    private val lock = Mutex()
    private var tokens: TraktTokens? = null
    private val _state = MutableStateFlow<TraktState>(TraktState.Unavailable)
    val state: StateFlow<TraktState> = _state.asStateFlow()
    private val _prefs = MutableStateFlow(TraktPrefs())
    val prefs: StateFlow<TraktPrefs> = _prefs.asStateFlow()
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    /** 壳：编进包里的 Trakt 应用（.env 的 TRAKT_CLIENT_ID / TRAKT_CLIENT_SECRET；没配就是空）。 */
    fun install(clientId: String, clientSecret: String, transport: Transport? = null) {
        app = TraktApp(clientId.trim(), clientSecret.trim())
        this.transport = transport
    }

    private fun client(): TraktClient = TraktClient(transport ?: graph?.deps?.transport ?: KtorTransport(platformHttpClient()).also { transport = it }, app)

    /** 界面：读一次令牌和开关。 */
    suspend fun bind(g: AppGraph) {
        graph = g
        _prefs.value = TraktPrefs.parse(g.deps.local.get(TraktPrefs.KEY))
        if (!app.configured) {
            _state.value = TraktState.Unavailable
            return
        }
        lock.withLock {
            if (_state.value is TraktState.Waiting) return
            tokens = TraktTokens.load(g.deps.secure)
            _state.value = tokens?.let { TraktState.Connected(it.user) } ?: (_state.value as? TraktState.Expired ?: TraktState.Disconnected)
        }
    }

    fun setPrefs(local: LocalStore, p: TraktPrefs) {
        local.set(TraktPrefs.KEY, p.encode())
        _prefs.value = p
    }

    /** 能用的令牌（快过期了先续；续不上 = 断开）。 */
    private suspend fun token(): String? = lock.withLock {
        val g = graph ?: return null
        val t = tokens ?: TraktTokens.load(g.deps.secure)?.also { tokens = it } ?: return null
        val now = currentTimeMillis()
        if (!t.needsRefresh(now)) return t.access
        val fresh = try {
            client().refresh(t, now)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return t.access
        }
        if (fresh == null) {
            // 续不上（被吊销 / 过期太久）：设置页显示「授权过期，重新连接」
            expire(t.user)
            return null
        }
        tokens = fresh
        TraktTokens.save(g.deps.secure, fresh)
        fresh.access
    }

    /** 连接：拿设备码，一直问到用户授权、码过期或用户取消（取消 = 这个协程被取消）。返回给人看的结果。 */
    /** [openPage]：拿到码后在浏览器里打开授权页（手机 / 电脑；电视不给）。 */
    suspend fun connect(openPage: (String) -> Unit = {}): String {
        val g = graph ?: return "未就绪"
        if (!app.configured) return "这一版不支持 Trakt"
        val code = try {
            client().deviceCode()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return "连不上 Trakt"
        _state.value = TraktState.Waiting(code)
        openPage(code.verificationUrl)
        try {
            var interval = code.intervalSec.coerceAtLeast(1) * 1000L
            val deadline = currentTimeMillis() + code.expiresInSec * 1000L
            while (currentTimeMillis() < deadline) {
                delay(interval)
                val r = try {
                    client().poll(code, currentTimeMillis())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    TraktPoll.Pending
                }
                when (r) {
                    is TraktPoll.Done -> {
                        lock.withLock {
                            tokens = r.tokens
                            TraktTokens.save(g.deps.secure, r.tokens)
                        }
                        _state.value = TraktState.Connected(r.tokens.user)
                        return "已连接 Trakt" + (r.tokens.user?.let { "：@$it" } ?: "")
                    }
                    TraktPoll.Pending -> Unit
                    TraktPoll.SlowDown -> interval += 1000
                    is TraktPoll.Failed -> {
                        _state.value = TraktState.Disconnected
                        return r.reason
                    }
                }
            }
            _state.value = TraktState.Disconnected
            return "码已过期"
        } finally {
            if (_state.value is TraktState.Waiting) _state.value = tokens?.let { TraktState.Connected(it.user) } ?: TraktState.Disconnected
        }
    }

    /** 令牌不能用了（续不上、Trakt 回 401）：删掉，状态变成「授权过期」。 */
    private suspend fun expire(user: String?) {
        val g = graph ?: return
        tokens = null
        TraktTokens.save(g.deps.secure, null)
        _state.value = TraktState.Expired(user)
    }

    /** 一个请求回了 401：令牌被吊销了。 */
    private suspend fun onUnauthorized() {
        val user = (state.value as? TraktState.Connected)?.user
        lock.withLock { expire(user) }
    }

    suspend fun disconnect() {
        val g = graph ?: return
        val t = lock.withLock { tokens.also { tokens = null } }
        TraktTokens.save(g.deps.secure, null)
        _state.value = if (app.configured) TraktState.Disconnected else TraktState.Unavailable
        if (t != null) client().revoke(t)
    }

    /* ------------------------------------------------------------ scrobble */

    /** 只有一台服务器的旧写法：条目永远按 [client] 去认。 */
    suspend fun follow(controller: PlayController, client: EmbyClient) = follow(controller) { client }

    /**
     * 跟着一个播放器记到 Trakt（播放页的组合里起一次，退出播放页时这个协程被取消 = 记 stop）。没连接 / 关了开关 = 什么都不做。
     *
     * 认片靠 TMDB / IMDb / TVDB 编号（[scrobbleTarget]），编号从**正在播的那台**取：[clientOf] 给出那台的客户端（1.5.0：放着别台的源时
     * 是别台的；从详情页用别台的源开播、播放中换台、切回本台都跟着变）。条目 id 只在一台服务器里有意义——拿别台的 id 去本台查，
     * 查不到就不记，碰巧撞上本台另一个条目就记错片。换台前后是同一部（编号一样）：不记 stop / start，接着算这一次。
     */
    suspend fun follow(controller: PlayController, clientOf: () -> EmbyClient) {
        if (!_prefs.value.scrobble || tokens == null && graph?.deps?.secure?.let { TraktTokens.load(it) } == null) return
        var target: JsonObject? = null
        /** [target] 是按哪一个（条目 id，条目对象）认出来的。 */
        var resolvedFor: Pair<String, BaseItemDto>? = null
        var started = false
        var lastProgress = 0.0
        suspend fun send(action: String, progress: Double) {
            val t = target ?: return
            val tk = token() ?: return
            try {
                if (client().scrobble(tk, action, withProgress(t, progress)) == TraktClient.UNAUTHORIZED) onUnauthorized()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        try {
            // 条目对象换了也重新认（换台时控制器先换 id、后换成那台的条目对象）
            val items = controller.snapshot.map { it.itemId to it.item }.distinctUntilChanged { a, b -> a.first == b.first && a.second === b.second }
            combine(items, controller.clock) { item, clock -> item to clock }.collectLatest { (item, clock) ->
                val (id, dto) = item
                if (dto == null || id.isEmpty()) return@collectLatest
                val known = resolvedFor
                if (target == null || known == null || known.first != id || known.second !== dto) {
                    val next = resolveScrobbleTarget(clientOf(), id, dto)
                    val prev = target
                    if (prev == null || next == null || !sameScrobbleTitle(prev, next)) {
                        // 换了一集：上一集记 stop（换台前后是同一部 = 不算换，接着记）
                        if (started) send("stop", lastProgress)
                        started = false
                    }
                    target = next
                    resolvedFor = if (next != null) id to dto else null
                }
                if (clock.durationMs <= 0) return@collectLatest
                lastProgress = clock.positionMs / clock.durationMs * 100
                if (!clock.paused && !clock.buffering && !started) {
                    started = true
                    send("start", lastProgress)
                } else if (clock.paused && started) {
                    started = false
                    send("pause", lastProgress)
                }
            }
        } finally {
            if (target != null) withContext(NonCancellable) { send("stop", lastProgress) }
        }
    }

    /* ------------------------------------------------------------ 手动标已看 → Trakt */

    /**
     * Emby 里手动标了已看 / 未看（详情页、长按菜单、W 键）：同一条也加进 / 移出 Trakt 的观看记录。电影、单集、一季、一整部剧都行。
     * 没连接 = 什么都不做；失败不影响 Emby 那边。
     */
    fun markFromEmby(client: EmbyClient, itemId: String, played: Boolean) {
        val g = graph ?: return
        if (state.value !is TraktState.Connected) return
        g.scope.launch {
            try {
                val tk = token() ?: return@launch
                val body = historyBody(client, itemId) ?: return@launch
                if (client().history(tk, body, remove = !played) == TraktClient.UNAUTHORIZED) onUnauthorized()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun historyBody(client: EmbyClient, itemId: String): JsonObject? {
        val o = ItemsRequestOptions(fields = listOf("ProviderIds"), enableImages = false, enableUserData = false)
        val item = client.getItem(itemId, o)
        val series = when (item.type) {
            "Episode", "Season" -> item.seriesId?.let { client.getItem(it, o) }
            else -> null
        }
        return traktHistoryBody(item, series)
    }

    /* ------------------------------------------------------------ 同步回 Emby */

    /** Trakt 上看过的 → Emby 里标成已看（只标没看过的）。返回给人看的结果。 */
    suspend fun syncToEmby(client: EmbyClient): String {
        val tk = token() ?: return "未连接 Trakt"
        _syncMessage.value = "同步中…"
        val msg = try {
            val c = client()
            val movies = c.watched(tk, "movies")
            val shows = c.watched(tk, "shows")
            if (movies == null || shows == null) {
                onUnauthorized()
                "Trakt 授权已过期"
            } else {
                val n = markWatched(client, movies, shows)
                if (n == 0) "已是最新" else "已同步：在 Emby 标记 $n 个已看"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            "同步失败"
        }
        _syncMessage.value = msg
        return msg
    }

    private suspend fun markWatched(client: EmbyClient, movies: JsonArray, shows: JsonArray): Int {
        var marked = 0
        val opts = ItemsRequestOptions(fields = listOf("ProviderIds"), enableImages = false, enableUserData = true)
        // 电影：整个片库的电影（带编号和已看），对上 Trakt 看过的就标
        val watchedMovies = movies.mapNotNull { (it as? JsonObject)?.get("movie")?.let { m -> traktIds((m as? JsonObject)?.get("ids")) } }
        if (watchedMovies.isNotEmpty()) {
            for (m in allItems(client, "Movie", opts)) {
                if (m.userData?.played == true) continue
                val mine = idKeys(m.providerIds)
                if (watchedMovies.any { ids -> ids.any { (k, v) -> "$k:$v" in mine } }) {
                    client.setPlayed(m.id, true)
                    marked++
                }
            }
        }
        // 剧：对上的剧，取它的分集，Trakt 上看过的季号 / 集号标成已看
        val watchedShows = shows.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val ids = traktIds((o["show"] as? JsonObject)?.get("ids"))
            val eps = HashSet<Pair<Int, Int>>()
            for (s in (o["seasons"] as? JsonArray).orEmpty()) {
                val so = s as? JsonObject ?: continue
                val season = (so["number"] as? JsonPrimitive)?.intOrNull ?: continue
                for (ep in (so["episodes"] as? JsonArray).orEmpty()) (((ep as? JsonObject)?.get("number") as? JsonPrimitive)?.intOrNull)?.let { eps += season to it }
            }
            if (ids.isEmpty() || eps.isEmpty()) null else ids to eps
        }
        if (watchedShows.isNotEmpty()) {
            for (series in allItems(client, "Series", opts)) {
                val mine = idKeys(series.providerIds)
                val eps = watchedShows.firstOrNull { (ids, _) -> ids.any { (k, v) -> "$k:$v" in mine } }?.second ?: continue
                val episodes = client.getItems(LibraryQuery(parentId = series.id, includeItemTypes = listOf("Episode"), recursive = true), opts).items
                for (ep in episodes) {
                    if (ep.userData?.played == true) continue
                    val key = (ep.parentIndexNumber ?: continue) to (ep.indexNumber ?: continue)
                    if (key in eps) {
                        client.setPlayed(ep.id, true)
                        marked++
                    }
                }
            }
        }
        return marked
    }

    private fun idKeys(p: Map<String, String?>): Set<String> = p.mapNotNull { (k, v) -> v?.trim()?.takeIf { it.isNotEmpty() }?.let { "${k.lowercase()}:$it" } }.toSet()

    private suspend fun allItems(client: EmbyClient, type: String, opts: ItemsRequestOptions): List<BaseItemDto> {
        val out = ArrayList<BaseItemDto>()
        var start = 0
        while (true) {
            val page = client.getItems(LibraryQuery(includeItemTypes = listOf(type), recursive = true, startIndex = start, limit = 500), opts)
            out += page.items
            start += page.items.size
            if (page.items.isEmpty() || start >= page.totalRecordCount) break
        }
        return out
    }

    /* ------------------------------------------------------------ 追更 + 追更日历（参考 CapyPlayer「Trakt 追更日历」） */

    private val _follows = MutableStateFlow<Set<String>?>(null)

    /** 正在追的剧（Trakt 的想看剧集清单）的编号键（「tmdb:123」…）；null = 还没读 / 没连接。 */
    val follows: StateFlow<Set<String>?> = _follows.asStateFlow()

    /** 读一次追更清单（连着才读）。 */
    suspend fun loadFollows() {
        if (state.value !is TraktState.Connected) return
        val tk = token() ?: return
        val list = try {
            client().watchlistShows(tk)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        } ?: return onUnauthorized()
        _follows.value = list.flatMap { e -> traktIds(((e as? JsonObject)?.get("show") as? JsonObject)?.get("ids")).map { (k, v) -> "$k:$v" } }.toSet()
    }

    /** 这部剧（Emby 的编号）在不在追更清单里。 */
    fun isFollowing(follows: Set<String>?, providerIds: Map<String, String?>): Boolean =
        follows != null && idKeys(providerIds).any { it in follows }

    /** 加入 / 取消追更。成功 = true。 */
    suspend fun setFollow(client: EmbyClient, seriesId: String, follow: Boolean): Boolean {
        val tk = token() ?: return false
        return try {
            val item = client.getItem(seriesId, ItemsRequestOptions(fields = listOf("ProviderIds"), enableImages = false, enableUserData = false))
            val ids = traktIdsOf(item.providerIds) ?: return false
            val status = client().watchlist(tk, buildJsonObject { put("shows", JsonArray(listOf(buildJsonObject { put("ids", ids) }))) }, remove = !follow)
            if (status == TraktClient.UNAUTHORIZED) {
                onUnauthorized()
                return false
            }
            if (status !in 200..299) return false
            val keys = idKeys(item.providerIds)
            _follows.value = (_follows.value ?: emptySet()).let { if (follow) it + keys else it - keys }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Emby 片库里的剧：编号键 → 剧的 id（日历上点一集打开它）；半小时内用缓存。 */
    private var libraryIndex: Pair<Long, Map<String, String>>? = null

    private suspend fun seriesIndex(client: EmbyClient): Map<String, String> {
        val now = currentTimeMillis()
        libraryIndex?.takeIf { now - it.first < 30 * 60_000L }?.let { return it.second }
        val map = HashMap<String, String>()
        for (s in allItems(client, "Series", ItemsRequestOptions(fields = listOf("ProviderIds"), enableImages = false, enableUserData = false))) {
            for (k in idKeys(s.providerIds)) map.getOrPut(k) { s.id } // 先到的那部留着（putIfAbsent 是 JVM 专有的）
        }
        libraryIndex = now to map
        return map
    }

    /**
     * 追更日历：从昨天起两周里，追的剧要播的集，按本地日期排好。没连接 / 失败 = null。
     */
    suspend fun calendar(client: EmbyClient, nowMs: Long, utcOffsetMs: (Long) -> Long, back: Int = 1, days: Int = 14): List<CalendarDay>? {
        val tk = token() ?: return null
        val startUtcDay = (nowMs - back * 86_400_000L).floorDiv(86_400_000L)
        val arr = try {
            client().calendar(tk, isoDate(startUtcDay), back + days)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        } ?: return null.also { onUnauthorized() }
        val library = try {
            seriesIndex(client)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
        return calendarDays(arr, library, nowMs, utcOffsetMs)
    }
}

/** 日历上的一集。[seriesId] = Emby 里对应的剧（库里没有 = null）。 */
data class CalendarEntry(val epochDay: Long, val time: String, val show: String, val season: Int?, val number: Int?, val title: String?, val seriesId: String?)

/** 日历上的一天：[label] = 今天 / 明天 / 昨天 / 「11 月 18 日 · 周三」。 */
data class CalendarDay(val epochDay: Long, val label: String, val entries: List<CalendarEntry>)

/** 1970-01-01 起的第几天 → 「2026-11-15」。 */
fun isoDate(epochDay: Long): String {
    val (y, m, d) = com.gugaart.ui.components.civilFromEpochDay(epochDay)
    return "$y-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"
}

/** 一天的叫法。纯函数，有单测。 */
fun calendarDayLabel(day: Long, today: Long): String = when (day - today) {
    0L -> "今天"
    1L -> "明天"
    -1L -> "昨天"
    else -> {
        val (_, m, d) = com.gugaart.ui.components.civilFromEpochDay(day)
        val week = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[(day + 3).mod(7L).toInt()]
        "$m 月 $d 日 · $week"
    }
}

/** Trakt 日历的回应 → 按本地日期排好的几天（对上 Emby 里的剧）。纯函数，有单测。 */
fun calendarDays(arr: JsonArray, library: Map<String, String>, nowMs: Long, utcOffsetMs: (Long) -> Long): List<CalendarDay> {
    val today = (nowMs + utcOffsetMs(nowMs)).floorDiv(86_400_000L)
    val entries = arr.mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val aired = com.gugaart.ui.screens.me.embyTimeMs((o["first_aired"] as? JsonPrimitive)?.contentOrNull) ?: return@mapNotNull null
        val local = aired + utcOffsetMs(aired)
        val show = o["show"] as? JsonObject
        val ep = o["episode"] as? JsonObject
        val keys = traktIds(show?.get("ids")).map { (k, v) -> "$k:$v" }
        val minutes = (local.mod(86_400_000L) / 60_000L).toInt()
        CalendarEntry(
            epochDay = local.floorDiv(86_400_000L),
            time = "${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}",
            show = (show?.get("title") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            season = (ep?.get("season") as? JsonPrimitive)?.intOrNull,
            number = (ep?.get("number") as? JsonPrimitive)?.intOrNull,
            title = (ep?.get("title") as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() },
            seriesId = keys.firstNotNullOfOrNull { library[it] },
        )
    }.sortedWith(compareBy({ it.epochDay }, { it.time }, { it.show }))
    return entries.groupBy { it.epochDay }.map { (day, list) -> CalendarDay(day, calendarDayLabel(day, today), list) }
}
