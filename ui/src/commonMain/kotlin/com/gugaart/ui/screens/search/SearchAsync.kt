package com.gugaart.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Title
import com.gugaart.ui.search.LibraryEngine
import com.gugaart.ui.search.SearchMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 1.5.1 批次 F：搜索打字防抖，停顿这么多毫秒再在后台线程重算结果。 */
const val SEARCH_DEBOUNCE_MS = 150L

/** 远程兜底搜索的防抖。 */
const val REMOTE_DEBOUNCE_MS = 250L

/** 后台线程一次性算好的搜索结果快照（主线程只拿它画界面，不做任何全库匹配/高亮计算）。 */
@Immutable
data class SearchSnapshot(
    val query: String = "",
    val hits: List<TitleHit> = emptyList(),
    val episodes: List<Title> = emptyList(),
    val episodeRanges: Map<String, IntRange?> = emptyMap(),
    val people: List<PersonHit> = emptyList(),
    val peopleFirst: Boolean = false,
    val suggestions: List<Suggestion> = emptyList(),
)

/** 在后台线程执行的纯计算：片 / 单集 / 人物匹配 + 高亮范围 + 演员优先 + 联想列表。 */
internal fun computeSearchSnapshot(
    query: String,
    index: SearchIndex?,
    peopleIndex: List<Person>?,
    remote: RemoteHits?,
    titleLimit: Int = 30,
    peopleLimit: Int = 12,
    episodeLimit: Int = 30,
): SearchSnapshot {
    val q = query.trim()
    if (q.isEmpty()) return SearchSnapshot()
    val localTitles = if (index != null) searchTitles(index, q, titleLimit) else emptyList()
    val remoteTitles = remote?.titles.orEmpty()
    val hits = if (remoteTitles.isEmpty()) localTitles else {
        val seen = localTitles.mapTo(HashSet(localTitles.size)) { it.title.id }
        localTitles + remoteTitles.filter { it.id !in seen }.map { TitleHit(it, SearchMatch.range(it.name, q)) }
    }
    val localEps = if (episodeLimit > 0 && index != null) searchEpisodesLocal(index, q, episodeLimit) else emptyList()
    val episodes = if (episodeLimit > 0) mergeEpisodes(localEps, remote?.episodes.orEmpty()) else emptyList()
    val episodeRanges = if (episodes.isEmpty()) emptyMap() else episodes.associate { e -> e.id to SearchMatch.range(e.name, q) }
    val localPeople = if (peopleIndex != null) searchPeopleLocal(peopleIndex, q, peopleLimit) else emptyList()
    val people = mergePeople(localPeople, remote?.people.orEmpty(), q)
    val first = peopleFirst(q, hits, people)
    val suggestions = suggestionsOf(hits, people)
    return SearchSnapshot(
        query = q,
        hits = hits,
        episodes = episodes,
        episodeRanges = episodeRanges,
        people = people,
        peopleFirst = first,
        suggestions = suggestions,
    )
}

/** 测试可替换后台调度器 / 防抖时长；正式运行默认 150 ms + `Dispatchers.Default`。 */
@Immutable
data class SearchRuntime(
    val debounceMs: Long = SEARCH_DEBOUNCE_MS,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
)

val LocalSearchRuntime = staticCompositionLocalOf { SearchRuntime() }

/**
 * 近期算好的搜索快照备忘（最多留 4 份，只在主线程读写）：
 * 进详情再返回搜索页时，`rememberSaveable` 恢复搜索词，`rememberLoad` 从 `LoadCache` 同步取出索引，
 * 这里同步拿回上次的结果，第一帧页面高度即完整，滚动位置才不会被压回 0。
 */
internal object SearchResultMemo {
    private class Entry(
        val query: String,
        val engine: LibraryEngine?,
        val titlesSize: Int,
        val people: List<Person>?,
        val remote: RemoteHits?,
        val titleLimit: Int,
        val peopleLimit: Int,
        val episodeLimit: Int,
        val snapshot: SearchSnapshot,
    ) {
        fun matches(
            q: String,
            idx: SearchIndex?,
            ppl: List<Person>?,
            rem: RemoteHits?,
            tl: Int,
            pl: Int,
            el: Int,
        ): Boolean = query == q &&
            engine === idx?.engine &&
            titlesSize == (idx?.titles?.size ?: -1) &&
            people === ppl &&
            remote == rem &&
            titleLimit == tl &&
            peopleLimit == pl &&
            episodeLimit == el
    }

    private const val MAX = 4
    private val entries = ArrayList<Entry>(MAX)

    fun get(
        query: String,
        index: SearchIndex?,
        peopleIndex: List<Person>?,
        remote: RemoteHits?,
        titleLimit: Int,
        peopleLimit: Int,
        episodeLimit: Int,
    ): SearchSnapshot? {
        val hit = entries.firstOrNull { it.matches(query, index, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit) } ?: return null
        entries.remove(hit)
        entries.add(hit)
        return hit.snapshot
    }

    fun put(
        query: String,
        index: SearchIndex?,
        peopleIndex: List<Person>?,
        remote: RemoteHits?,
        titleLimit: Int,
        peopleLimit: Int,
        episodeLimit: Int,
        snapshot: SearchSnapshot,
    ) {
        entries.removeAll { it.matches(query, index, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit) }
        entries.add(Entry(query, index?.engine, index?.titles?.size ?: -1, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit, snapshot))
        while (entries.size > MAX) entries.removeAt(0)
    }

    fun clear() = entries.clear()
}

@Stable
class AsyncSearchState internal constructor(initialSettled: String, initialSnapshot: SearchSnapshot) {
    /** 当前正在画面上展示的结果快照（防抖期间和后台计算期间保持上一份结果，不闪）。 */
    var snapshot by mutableStateOf(initialSnapshot)
        internal set

    /** 已经停稳（过完 150 ms 或显式 flush）的搜索词。 */
    var settled by mutableStateOf(initialSettled)
        internal set

    /** 正在后台线程算当前 `settled` 的结果。 */
    var computing by mutableStateOf(false)
        internal set

    internal var flushSeq by mutableIntStateOf(0)

    /** 是否仍在等待防抖或后台计算当前词（用于结果为空时先不闪「搜不到」）。 */
    fun isSearching(query: String, hasSource: Boolean): Boolean {
        val q = query.trim()
        return q.isNotEmpty() && (settled != query || computing || (hasSource && snapshot.query != q))
    }

    /** 立即按 [target] 触发后台计算，不等 150 ms（点最近搜过 / 词云 / 按 Enter 时用）。 */
    fun flush(target: String) {
        settled = target
        flushSeq++
    }

    /** 立即清空（点「清除」或清空按钮时用）。 */
    fun clear() {
        settled = ""
        snapshot = SearchSnapshot()
        computing = false
        flushSeq++
    }
}

/**
 * 异步防抖搜索持有者：
 * - 打字改 [query] 时先等 [SearchRuntime.debounceMs]（默认 150 ms）；停下之后才上 [SearchRuntime.dispatcher]（`Dispatchers.Default`）算；
 * - 停顿与计算期间 [AsyncSearchState.snapshot] 保持上一次结果不变，不闪；
 * - [query] 变空时立刻切回空态；初始词 / 返回恢复命中备忘时第一帧就有结果。
 */
@Composable
fun rememberAsyncSearch(
    query: String,
    index: SearchIndex?,
    peopleIndex: List<Person>?,
    remote: RemoteHits? = null,
    titleLimit: Int = 30,
    peopleLimit: Int = 12,
    episodeLimit: Int = 30,
): AsyncSearchState {
    val runtime = LocalSearchRuntime.current
    var savedSettled by rememberSaveable { mutableStateOf(query) }
    val qInit = savedSettled.trim()
    val state = remember {
        val seed = if (qInit.isNotEmpty() && (index != null || peopleIndex != null || remote != null)) {
            SearchResultMemo.get(qInit, index, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit) ?: SearchSnapshot()
        } else {
            SearchSnapshot()
        }
        AsyncSearchState(savedSettled, seed)
    }

    // 1. 150 ms 打字防抖（变空时立刻归零，不停等）
    LaunchedEffect(query, state.flushSeq, runtime.debounceMs) {
        if (query.isBlank()) {
            state.settled = ""
            savedSettled = ""
            state.snapshot = SearchSnapshot()
            state.computing = false
            return@LaunchedEffect
        }
        if (state.settled != query) {
            if (runtime.debounceMs > 0L) delay(runtime.debounceMs)
            state.settled = query
        }
        savedSettled = state.settled
    }

    // 2. 停稳后在后台线程算结果；算完前旧 snapshot 留着不动
    val settledQ = state.settled.trim()
    LaunchedEffect(settledQ, index, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit, runtime.dispatcher) {
        if (settledQ.isEmpty()) {
            state.snapshot = SearchSnapshot()
            state.computing = false
            return@LaunchedEffect
        }
        if (index == null && peopleIndex == null && remote == null) {
            state.computing = false
            return@LaunchedEffect
        }
        val cached = SearchResultMemo.get(settledQ, index, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit)
        if (cached != null) {
            state.snapshot = cached
            state.computing = false
            return@LaunchedEffect
        }
        state.computing = true
        try {
            val computed = withContext(runtime.dispatcher) {
                computeSearchSnapshot(settledQ, index, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit)
            }
            SearchResultMemo.put(settledQ, index, peopleIndex, remote, titleLimit, peopleLimit, episodeLimit, computed)
            state.snapshot = computed
        } finally {
            state.computing = false
        }
    }

    return state
}

/** 电脑空闲页「最近搜过」每条的命中数（后台算，不在主线程遍历库）。 */
@Composable
internal fun rememberRecentCounts(
    index: SearchIndex?,
    peopleIndex: List<Person>,
    terms: List<String>,
): Map<String, Int> {
    val runtime = LocalSearchRuntime.current
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(index, peopleIndex, terms, runtime.dispatcher) {
        if (index == null || terms.isEmpty()) {
            counts = emptyMap()
            return@LaunchedEffect
        }
        counts = withContext(runtime.dispatcher) {
            terms.associateWith { term ->
                searchTitles(index, term).size + searchPeopleLocal(peopleIndex, term, 50).size
            }
        }
    }
    return counts
}

/** 电视空闲页「最近搜过」每条对应的第一部片名（后台算）。 */
@Composable
internal fun rememberRecentTops(
    index: SearchIndex?,
    terms: List<String>,
): Map<String, String?> {
    val runtime = LocalSearchRuntime.current
    var tops by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    LaunchedEffect(index, terms, runtime.dispatcher) {
        if (index == null || terms.isEmpty()) {
            tops = emptyMap()
            return@LaunchedEffect
        }
        tops = withContext(runtime.dispatcher) {
            terms.associateWith { term ->
                searchTitles(index, term, 1).firstOrNull()?.title?.name
            }
        }
    }
    return tops
}
