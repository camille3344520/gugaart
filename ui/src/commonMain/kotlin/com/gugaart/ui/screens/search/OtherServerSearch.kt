package com.gugaart.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.OtherServer
import com.gugaart.ui.data.OtherServerHealth
import com.gugaart.ui.data.OtherServerPool
import com.gugaart.ui.data.OtherServers
import com.gugaart.ui.data.RemoteBrowse
import com.gugaart.ui.data.RemoteRef
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.search.SearchFold
import com.gugaart.ui.search.SearchIndexStore
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyException
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/*
 * 多台一起搜（1.6 A1，P1-7）。搜索照旧先出本台结果；本台的片不到 [OtherSearchRules.FEW] 条、或者往下翻到底时，
 * 并发问其它已保存的服务器（[OtherServers] 池里参与的那几台，设置 › 播放 › 多台服务器 的开关和勾选照样管用）：
 * - 每台 [OtherSearchRules.TIMEOUT_MS]（5 秒）超时；那台以前切过去建过本地索引的用索引（拼音首字母也搜得到），没有就问 Emby 的 `SearchTerm`；
 * - 离线 / 超时 / 要重新登录的那台跳过，不报错；
 * - 结果在本台结果下面另起一组「其它服务器」，同一部片（类型 + 片名 + 年份）几台都有只列一行，标「2 台」；本台已经有的不再列；
 * - 点开 = 那台上这部片的详情（不切换当前服务器，data/RemoteBrowse.kt）。
 */

/** 别台上的一处：哪台、那台上的条目 id。 */
@Immutable
data class OtherPlace(val serverId: String, val serverName: String, val itemId: String)

/** 「其它服务器」一组里的一行：同一部片在哪几台（按到达先后；第一台的海报、点开进第一台）。 */
@Immutable
data class OtherHit(val title: Title, val places: List<OtherPlace>, val range: IntRange? = null) {
    /** 行右边的小字：一台 = 服务器名；几台 =「2 台」。 */
    val label: String get() = OtherSearchRules.placeLabel(places)

    /** 点开去哪：第一台上的这一条。 */
    val routeId: String get() = RemoteRef.encode(places.first().serverId, places.first().itemId)
}

/** 一次多台搜索的累计结果。[pending] = 还没回来的台数（0 = 问完了）。 */
@Immutable
data class OtherSearchResult(val query: String, val hits: List<OtherHit> = emptyList(), val pending: Int = 0)

/** 纯规则（有单测）。 */
object OtherSearchRules {
    /** 本台的片不到这么多条就去问别台。 */
    const val FEW = 5

    /** 每台等多久。 */
    const val TIMEOUT_MS = 5_000L

    /** 每台最多拿几条。 */
    const val PER_SERVER = 20

    /** 要不要问别台：本台的片不到 [FEW] 条，或者用户翻到了底。 */
    fun shouldAsk(homeTitles: Int, reachedEnd: Boolean): Boolean = homeTitles < FEW || reachedEnd

    /** 「同一部片」的键：类型 + 折过的片名（繁简、全半角、标点）+ 年份。 */
    fun keyOf(t: Title): String = "${t.kind}|${SearchFold.foldPlain(t.name).text}|${t.year ?: ""}"

    fun placeLabel(places: List<OtherPlace>): String = if (places.size > 1) "${places.size} 台" else places.firstOrNull()?.serverName.orEmpty()

    /** 只列片（电影、剧、合集、其它视频）；单集、人物不跨台搜。 */
    fun isTitle(t: Title): Boolean = t.kind != Kind.Episode && t.kind != Kind.Person && t.kind != Kind.Season

    /**
     * 接上一台的结果：已经有的行（同一部片）补上这一台（不重排），没有的接在最后。同一台同一部只记一次。
     */
    fun merge(acc: List<OtherHit>, server: OtherServer, titles: List<Title>, query: String): List<OtherHit> {
        val out = acc.toMutableList()
        val at = HashMap<String, Int>()
        out.forEachIndexed { i, h -> at[keyOf(h.title)] = i }
        for (t in titles) {
            if (!isTitle(t)) continue
            val k = keyOf(t)
            val place = OtherPlace(server.serverId, server.name, t.id)
            val i = at[k]
            if (i == null) {
                at[k] = out.size
                out += OtherHit(t, listOf(place), com.gugaart.ui.search.SearchMatch.range(t.name, query))
            } else if (out[i].places.none { it.serverId == server.serverId }) {
                out[i] = out[i].copy(places = out[i].places + place)
            }
        }
        return out
    }

    /** 本台已经有的不再列（按同一个键）。 */
    fun withoutHome(hits: List<OtherHit>, home: List<Title>): List<OtherHit> {
        if (home.isEmpty()) return hits
        val keys = home.mapTo(HashSet()) { keyOf(it) }
        return hits.filter { keyOf(it.title) !in keys }
    }
}

/** 在一台别的服务器上搜：返回片；null = 这台跳过（离线、超时、要重新登录）。 */
fun interface OtherTitleSearch {
    suspend fun search(server: OtherServer, query: String): List<Title>?
}

/** 测试 / 截图换成假的；应用里是 null = 真的（[EmbyOtherTitleSearch]）。 */
val LocalOtherTitleSearch = staticCompositionLocalOf<Pair<List<OtherServer>, OtherTitleSearch>?> { null }

private val OTHER_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "Overview", "CommunityRating", "ProductionYear"))

/**
 * 真的：池里那台的客户端。本地有那台的索引（以前切过去建过）就用索引；没有问 `SearchTerm`。
 * [homeEmbyId]：当前服务器的 Emby ServerId——和它一样的那台（同一份 Emby 的另一个地址、几台 Mock）不认索引（索引按 ServerId 分键，会拿到本台的）。
 */
class EmbyOtherTitleSearch(private val pool: OtherServerPool, private val homeEmbyId: () -> String?) : OtherTitleSearch {
    override suspend fun search(server: OtherServer, query: String): List<Title>? {
        val id = server.serverId
        if (pool.health.value[id] == OtherServerHealth.NeedsLogin) return null
        val c = pool.client(id) ?: return null
        return try {
            withTimeout(OtherSearchRules.TIMEOUT_MS) {
                val own = c.session?.serverId
                val index = if (own != null && own == homeEmbyId()) null else SearchIndexStore.cachedOnly(c)
                if (index != null) {
                    searchTitles(SearchIndex(index.titles, index.titles.size, index.engine), query, OtherSearchRules.PER_SERVER).map { it.title }
                } else {
                    c.searchItems(query, LibraryQuery(includeItemTypes = listOf("Movie", "Series"), limit = OtherSearchRules.PER_SERVER), OTHER_FIELDS).items.map { c.titleOf(it) }
                }
            }
        } catch (_: TimeoutCancellationException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmbyException) {
            if (e.kind == EmbyException.Kind.Network) pool.report(id, null)
            null
        } catch (_: Exception) {
            null
        }
    }
}

/** 并发问 [servers]，到一台接一台（累计结果；每台到了发一次，最后 pending = 0）。 */
fun otherSearchFlow(servers: List<OtherServer>, query: String, search: OtherTitleSearch): Flow<OtherSearchResult> = channelFlow {
    if (servers.isEmpty()) {
        send(OtherSearchResult(query))
        return@channelFlow
    }
    val lock = Mutex()
    var acc = OtherSearchResult(query, pending = servers.size)
    send(acc)
    for (s in servers) launch {
        val titles = try {
            search.search(s, query)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val next = lock.withLock {
            acc = acc.copy(hits = if (titles.isNullOrEmpty()) acc.hits else OtherSearchRules.merge(acc.hits, s, titles, query), pending = acc.pending - 1)
            acc
        }
        send(next)
    }
}

/** 近几次多台搜索的结果（返回搜索页时第一帧就在，滚动位置才还原得了）。键 = 当前账号 + 词。主线程读写。 */
internal object OtherSearchMemo {
    private const val MAX = 4
    private val map = LinkedHashMap<String, OtherSearchResult>()

    fun get(key: String): OtherSearchResult? = map[key]

    fun put(key: String, r: OtherSearchResult) {
        map.remove(key)
        map[key] = r
        while (map.size > MAX) map.remove(map.keys.first())
    }

    fun clear() = map.clear()
}

/** 搜索页拿到的：这个词在别台的结果（[hits] 已去掉本台有的）、还在问几台。 */
@Immutable
data class OtherSearchView(val hits: List<OtherHit> = emptyList(), val searching: Boolean = false)

/**
 * 搜索页三端共用：[ask] 为真（[OtherSearchRules.shouldAsk]，本台结果已经出来了）时按 [query] 问一次别台；问过的词结果留着，
 * 换词前不再问。[home] = 本台这一次的片（去重用）。
 */
@Composable
fun rememberOtherServerSearch(query: String, ask: Boolean, home: List<Title>): OtherSearchView {
    val g = LocalGraph.current
    val q = query.trim()
    val override = LocalOtherTitleSearch.current
    val account = g.session.session.value?.let { "${it.serverId}/${it.userId}" }.orEmpty()
    var asked by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf(OtherSearchMemo.get("$account|$asked")) }
    LaunchedEffect(q, ask) { if (ask && q.isNotEmpty()) asked = q }
    LaunchedEffect(asked, account) {
        if (asked.isEmpty()) return@LaunchedEffect
        val memo = OtherSearchMemo.get("$account|$asked")
        if (memo != null && memo.pending == 0) {
            result = memo
            return@LaunchedEffect
        }
        val pool = OtherServers.poolFor(g)
        val servers = override?.first ?: try {
            pool.participants()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        servers.forEach { RemoteBrowse.rememberName(it.serverId, it.name) }
        val search = override?.second ?: EmbyOtherTitleSearch(pool) { g.session.session.value?.client?.session?.serverId }
        otherSearchFlow(servers, asked, search).collect { r ->
            result = r
            OtherSearchMemo.put("$account|${r.query}", r)
        }
    }
    val r = result?.takeIf { it.query == q } ?: return OtherSearchView(searching = ask && q.isNotEmpty() && asked != q)
    return OtherSearchView(OtherSearchRules.withoutHome(r.hits, home), searching = r.pending > 0)
}

/** 翻到底了没有（离底不到 [slackPx]）。派生状态：只在「到 / 没到」翻转时重组。内容不够一屏（不能滚）不算。 */
@Composable
fun rememberReachedEnd(scroll: androidx.compose.foundation.ScrollState, slackPx: Int = 48): Boolean {
    val s = remember(scroll, slackPx) { androidx.compose.runtime.derivedStateOf { scroll.maxValue in 1..Int.MAX_VALUE - 1 && scroll.value >= scroll.maxValue - slackPx } }
    return s.value
}
