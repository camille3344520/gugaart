package com.gugaart.ui.search

import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugugaga.app.platform.BlobCache
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ImageUrlOptions
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import com.gugugaga.core.NamedListOptions
import com.gugugaga.core.QueryResult
import com.gugugaga.core.currentTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/*
 * 片库搜索的本地索引（docs/search.md §1）：按「服务器 + 用户」一份，内存 → 磁盘 → 服务器。
 *
 * - 取什么：电影 / 剧集 / 合集 / 其它视频（片名、原名、排序名、年份、题材、评分）、单集（集名、剧名、季号、集号）、人物（本用户可见的 `/Persons`）。
 *   分页取全（每页 [PAGE]），不截断。
 * - 存哪：内存里最近两个账号；磁盘上是壳给的 [disk]（`FileBlobCache`，文件名是键的 SHA-256，里面只有服务器回的条目 JSON，**没有令牌、没有图片地址**——
 *   图片地址每次用当前会话的 client 现算）。没给 [disk]（离屏测试）就只在内存里。
 * - 什么时候刷新：拿索引时它已经超过 [STALE_MS]（10 分钟）→ 后台增量刷新（每组先问总数 + 最新加入的 50 条：没变 → 不动；只多了几条 → 把这几条补进来；
 *   别的变化 → 这一组重取）；超过 [FULL_MS]（24 小时）整份重取（改名、删了又加这类增量看不出来的变化）。刷新好了下次拿就是新的。
 * - 索引还没好（第一次、磁盘上也没有）：界面照旧问 Emby 的 `SearchTerm` 兜底（[searchRemote][com.gugaart.ui.screens.search.searchRemote]），两路按 id 去重合并。
 */
object SearchIndexStore {
    /** 壳给的磁盘缓存（Windows / macOS：数据目录下 `cache/search-index`；安卓：cacheDir 下 `search-index`）。 */
    var disk: BlobCache? = null

    /** 测量缝（1.5.1 perf-win）：壳设了它，索引各阶段的耗时就进日志；不设（默认）什么都不记。 */
    var perf: ((String) -> Unit)? = null

    const val PAGE = 2000
    /** 磁盘上最多几个账号的索引、一共多大（一份 2 万条约 10 MB）。 */
    const val DISK_MAX_ENTRIES = 6
    const val DISK_MAX_BYTES = 96L shl 20
    const val STALE_MS = 10 * 60 * 1000L
    const val FULL_MS = 24 * 60 * 60 * 1000L
    private const val PROBE = 50
    private const val FORMAT = 1

    /** 时钟（单测可换）。 */
    var now: () -> Long = { currentTimeMillis() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private val locks = HashMap<String, Mutex>()
    /** 内存里的索引（整份替换，读的时候不用加锁）；最近用的在后面。 */
    @kotlin.concurrent.Volatile
    private var mem: Map<String, Snapshot> = emptyMap()
    private val refreshing = HashSet<String>()

    /** 一份索引：界面直接用的模型 + 预算好的引擎；[raw] 是落盘用的原始条目。 */
    class Snapshot internal constructor(
        val account: String,
        val titles: List<Title>,
        val episodes: List<Title>,
        val people: List<Person>,
        val engine: LibraryEngine,
        internal val raw: Raw,
    ) {
        val savedAt: Long get() = raw.savedAt
    }

    /** 落盘的形状：三组服务器条目 + 每组最近一次整组重取的时间。 */
    internal class Raw(val savedAt: Long, val fullAt: Long, val titles: List<BaseItemDto>, val episodes: List<BaseItemDto>, val people: List<BaseItemDto>)

    /** 账号键：服务器 id（没有就地址）+ 用户 id。 */
    fun accountOf(client: EmbyClient): String = "${client.session?.serverId ?: client.address.baseUrl}/${client.userId.orEmpty()}"

    /** 这个账号的索引已经在内存里了（界面就不用再问 Emby 兜底）。 */
    fun ready(client: EmbyClient): Boolean = mem[accountOf(client)] != null

    /** 现在内存里的那份（没有 → null，不取）。 */
    fun peek(client: EmbyClient): Snapshot? = mem[accountOf(client)]

    /** 别台的索引（1.6 A1 多台一起搜）：只读内存 / 磁盘里已经有的，不向服务器取、不刷新、不挤掉本台在内存里的那份。 */
    private var others: Map<String, Snapshot> = emptyMap()

    /**
     * 这台（别的服务器）本地已经有的索引：内存 → 磁盘；都没有 = null（调用方改问 Emby 的 SearchTerm）。
     * 以前切到过那台、建过索引才会有。读过的留在一张小表里（最多 [DISK_MAX_ENTRIES] 份）。
     */
    suspend fun cachedOnly(client: EmbyClient): Snapshot? {
        val account = accountOf(client)
        mem[account]?.let { return it }
        others[account]?.let { return it }
        val gate = lock.withLock { locks.getOrPut(account) { Mutex() } }
        val snap = gate.withLock { readDisk(client, account) } ?: return null
        val m = LinkedHashMap(others)
        m[account] = snap
        while (m.size > DISK_MAX_ENTRIES) m.remove(m.keys.first())
        others = m
        return snap
    }

    /** 拿索引：内存 → 磁盘 → 服务器；旧了就在后台刷新（这次先给旧的）。 */
    suspend fun get(client: EmbyClient): Snapshot {
        val account = accountOf(client)
        val gate = lock.withLock { locks.getOrPut(account) { Mutex() } }
        val snap = gate.withLock {
            mem[account] ?: (readDisk(client, account) ?: fetchAll(client).let { raw -> build(client, account, raw).also { writeDisk(account, raw) } }).also { remember(it) }
        }
        if (now() - snap.savedAt > STALE_MS) refreshLater(client, account, gate)
        return snap
    }

    private suspend fun remember(s: Snapshot) = lock.withLock {
        val m = LinkedHashMap(mem)
        m.remove(s.account)
        m[s.account] = s
        while (m.size > 2) m.remove(m.keys.first())
        mem = m
    }

    /** 测试：丢掉内存里的。 */
    fun clearMemory() {
        mem = emptyMap()
        others = emptyMap()
    }

    /** 测试基准用：直接往内存索引放入一份造好的数据（和真实构建同结构）。 */
    internal fun seed(client: EmbyClient, titles: List<Title>, episodes: List<Title>, people: List<Person>) {
        val account = accountOf(client)
        val engine = LibraryEngine(titles, episodes, people)
        DocMemo.seedPeople(people, engine.personDocs)
        val snap = Snapshot(account, titles, episodes, people, engine, Raw(now(), now(), emptyList(), emptyList(), emptyList()))
        mem = mem + (account to snap)
    }

    private fun refreshLater(client: EmbyClient, account: String, gate: Mutex) {
        scope.launch {
            if (!lock.withLock { refreshing.add(account) }) return@launch
            try {
                val old = mem[account] ?: return@launch
                val raw = refresh(client, old.raw)
                if (raw !== old.raw) {
                    val s = build(client, account, raw)
                    gate.withLock { remember(s) }
                } else {
                    gate.withLock { mem[account]?.let { remember(Snapshot(it.account, it.titles, it.episodes, it.people, it.engine, Raw(now(), it.raw.fullAt, it.raw.titles, it.raw.episodes, it.raw.people))) } }
                }
                mem[account]?.let { writeDisk(account, it.raw) }
            } catch (_: Exception) {
                // 刷新失败：留着旧的，下次再试
            } finally {
                lock.withLock { refreshing.remove(account) }
            }
        }
    }

    /* ---------------- 取数 ---------------- */

    /** 片（电影 / 剧集 / 合集 / 其它视频）要的字段：界面要的（海报比例、题材、简介、评分）+ 搜索要的（原名、排序名、加入时间）。 */
    private val TITLE_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "Overview", "CommunityRating", "OriginalTitle", "SortName", "DateCreated"))
    private val EPISODE_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "DateCreated"))
    internal val TITLE_TYPES = listOf("Movie", "Series", "BoxSet", "Video")
    private val EPISODE_TYPES = listOf("Episode")

    private enum class Group { Titles, Episodes, People }

    private suspend fun page(client: EmbyClient, g: Group, start: Int, limit: Int, newest: Boolean): QueryResult<BaseItemDto> = when (g) {
        Group.People -> client.getPersons(
            NamedListOptions(startIndex = start.takeIf { it > 0 }, limit = limit, sortBy = if (newest) "DateCreated" else "SortName", sortOrder = if (newest) "Descending" else "Ascending"),
        )
        else -> client.getItems(
            LibraryQuery(
                includeItemTypes = if (g == Group.Titles) TITLE_TYPES else EPISODE_TYPES,
                recursive = true,
                sortBy = listOf(if (newest) "DateCreated" else "SortName"),
                sortOrder = if (newest) "Descending" else "Ascending",
                startIndex = start.takeIf { it > 0 },
                limit = limit,
            ),
            if (g == Group.Titles) TITLE_FIELDS else EPISODE_FIELDS,
        )
    }

    /** 一组分页取全。 */
    private suspend fun fetchGroup(client: EmbyClient, g: Group): List<BaseItemDto> {
        val out = ArrayList<BaseItemDto>()
        var total = 0
        while (true) {
            val r = page(client, g, out.size, PAGE, newest = false)
            total = maxOf(total, r.totalRecordCount)
            if (r.items.isEmpty()) break
            out += r.items
            if (r.items.size < PAGE || out.size >= total) break
        }
        return out.filter { it.id.isNotEmpty() && it.name.isNotBlank() }.distinctBy { it.id }
    }

    private suspend fun fetchAll(client: EmbyClient): Raw = kotlinx.coroutines.coroutineScope {
        val t0 = now()
        // 三组（片 / 单集 / 人物）独立，并发取（以前串行；1.5.1 perf-win）
        val titlesD = async { fetchGroup(client, Group.Titles) }
        val episodesD = async { fetchGroup(client, Group.Episodes) }
        // 人物取不到（有的服务器给普通用户关了 /Persons）不拖垮整份索引：没有人物，界面照旧问 Emby 的人物搜索
        val peopleD = async {
            try {
                fetchGroup(client, Group.People)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        }
        val titles = titlesD.await()
        val episodes = episodesD.await()
        val people = peopleD.await()
        val t = now()
        perf?.invoke("index fetch titles=${titles.size} episodes=${episodes.size} people=${people.size} in ${t - t0} ms")
        Raw(t, t, titles, episodes, people)
    }

    /** 增量刷新；什么都没变 → 返回同一个 [old]。 */
    internal suspend fun refresh(client: EmbyClient, old: Raw): Raw {
        val t0 = now()
        if (now() - old.fullAt > FULL_MS) return fetchAll(client).also { perf?.invoke("index refresh full in ${now() - t0} ms") }
        var changed = false
        suspend fun group(g: Group, have: List<BaseItemDto>): List<BaseItemDto> {
            val probe = page(client, g, 0, PROBE, newest = true)
            val ids = have.mapTo(HashSet(have.size)) { it.id }
            val fresh = probe.items.filter { it.id.isNotEmpty() && it.id !in ids }
            return when {
                probe.totalRecordCount == have.size && fresh.isEmpty() -> have
                fresh.isNotEmpty() && fresh.size < PROBE && probe.totalRecordCount == have.size + fresh.size -> {
                    changed = true
                    have + fresh.filter { it.name.isNotBlank() }
                }
                else -> {
                    changed = true
                    fetchGroup(client, g)
                }
            }
        }
        val titles = group(Group.Titles, old.titles)
        val episodes = group(Group.Episodes, old.episodes)
        val people = try {
            group(Group.People, old.people)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            old.people
        }
        val out = if (!changed) old else Raw(now(), old.fullAt, titles, episodes, people)
        perf?.invoke("index refresh changed=$changed in ${now() - t0} ms")
        return out
    }

    /** 原始条目 → 界面模型 + 引擎（几万条：放在后台线程）。 */
    private suspend fun build(client: EmbyClient, account: String, raw: Raw): Snapshot = withContext(Dispatchers.Default) {
        val t0 = now()
        val titles = raw.titles.map { client.titleOf(it) }
        val episodes = raw.episodes.map { client.titleOf(it) }
        val people = raw.people.map { Person(it.id, it.name.trim(), photo = client.personImage(it.id, it.imageTags["Primary"], ImageUrlOptions(maxWidth = 360))) }
        val engine = LibraryEngine(titles, episodes, people)
        DocMemo.seedPeople(people, engine.personDocs)
        perf?.invoke("index build titles=${titles.size} episodes=${episodes.size} people=${people.size} in ${now() - t0} ms")
        Snapshot(account, titles, episodes, people, engine, raw)
    }

    /* ---------------- 磁盘 ---------------- */

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        isLenient = true
        coerceInputValues = true
    }
    private val listSer = ListSerializer(BaseItemDto.serializer())

    private fun key(account: String) = "search-index/v$FORMAT/$account"

    internal fun encode(raw: Raw): ByteArray = buildJsonObject {
        put("v", FORMAT)
        put("savedAt", raw.savedAt)
        put("fullAt", raw.fullAt)
        put("titles", json.encodeToJsonElement(listSer, raw.titles))
        put("episodes", json.encodeToJsonElement(listSer, raw.episodes))
        put("people", json.encodeToJsonElement(listSer, raw.people))
    }.toString().encodeToByteArray()

    internal fun decode(bytes: ByteArray): Raw? = try {
        val o = json.parseToJsonElement(bytes.decodeToString()).jsonObject
        if ((o["v"] as? JsonPrimitive)?.content != FORMAT.toString()) null
        else Raw(
            o["savedAt"]!!.jsonPrimitive.long,
            o["fullAt"]!!.jsonPrimitive.long,
            json.decodeFromJsonElement(listSer, o["titles"]!!.jsonArray),
            json.decodeFromJsonElement(listSer, o["episodes"]!!.jsonArray),
            json.decodeFromJsonElement(listSer, o["people"]!!.jsonArray),
        )
    } catch (_: Exception) {
        null
    }

    private suspend fun readDisk(client: EmbyClient, account: String): Snapshot? {
        val d = disk ?: return null
        val t0 = now()
        val bytes = try {
            d.get(key(account))
        } catch (_: Exception) {
            null
        } ?: return null
        val raw = withContext(Dispatchers.Default) { decode(bytes) } ?: return null
        perf?.invoke("index disk-read ${bytes.size} bytes in ${now() - t0} ms")
        return build(client, account, raw)
    }

    private suspend fun writeDisk(account: String, raw: Raw) {
        val d = disk ?: return
        try {
            d.put(key(account), withContext(Dispatchers.Default) { encode(raw) })
        } catch (_: Exception) {
            // 写不进去：下次启动再从服务器取
        }
    }
}
