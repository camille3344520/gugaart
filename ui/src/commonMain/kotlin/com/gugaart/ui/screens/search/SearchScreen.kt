package com.gugaart.ui.screens.search

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.typingTarget
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.PersonChip
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SearchNoneView
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.highlighted
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Pinyin
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.ReportScroll
import com.gugaart.ui.search.DocMemo
import com.gugaart.ui.search.FieldKind
import com.gugaart.ui.search.SearchHit
import com.gugaart.ui.search.LibraryEngine
import com.gugaart.ui.search.SearchIndexStore
import com.gugaart.ui.search.SearchMatch
import com.gugaart.ui.search.SearchQuery
import com.gugaart.ui.screens.StatusBand
import com.gugaart.ui.screens.open
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ImageUrlOptions
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.delay

/**
 * 搜索要用的本地索引。[titles] = 全库的电影 + 剧集（词云、「共 N 部」、题材 / 年份那一路用）；
 * [engine] = 1.4.1 自己写的搜索（`ui/…/search/`，docs/search.md）：片（含合集 / 其它视频、原名、排序名）、单集、人物都在里面，
 * 按「服务器 + 用户」缓存、10 分钟后台增量刷新（[SearchIndexStore]）。null = 旧的只有片名的索引（[loadSearchIndex]）。
 */
@Immutable
data class SearchIndex(val titles: List<Title>, val total: Int, val engine: LibraryEngine? = null)

/** 服务器那一半：片 / 剧（1.3.1 起也问）、单集和人物（它们数量大，不建本地索引）。 */
@Immutable
data class RemoteHits(val episodes: List<Title>, val people: List<Person>, val titles: List<Title> = emptyList())

/**
 * 命中的一部片 + 命中的字的范围（上专色）。
 * [original] / [originalRange]：是原名（`OriginalTitle`）命中的——片名不高亮，界面在片名下面加一行「原名命中：Iron Man」，命中的原名上专色。
 * [direct]：「剧名 + 集号」直达时，这一行是那部剧，[direct] 是那一集（界面给一个「第 12 集」签）。
 */
@Immutable
data class TitleHit(
    val title: Title,
    val range: IntRange?,
    val original: String? = null,
    val originalRange: IntRange? = null,
    val direct: Title? = null,
)

/** 引擎的命中 → [TitleHit]（原名命中时带上原名和其中命中的字）。 */
fun SearchHit<Title>.toTitleHit(): TitleHit =
    if (field == FieldKind.Original) TitleHit(item, null, fieldText, fieldRange) else TitleHit(item, range)

/** 原名命中那一行的前缀（纯函数用的常量；界面拼 [originalHitText]）。 */
const val ORIGINAL_HIT_PREFIX = "原名命中："

/** 「原名命中：Iron Man」（命中的原名上 [color]）；不是原名命中 = null。 */
fun originalHitText(hit: TitleHit, color: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString? {
    val o = hit.original ?: return null
    return androidx.compose.ui.text.buildAnnotatedString {
        append(ORIGINAL_HIT_PREFIX)
        append(highlighted(o, hit.originalRange, color))
    }
}

/** 直达那一集的签：「第 12 集」；不是第 1 季的写「第 2 季 第 12 集」。不是直达 = null。纯函数，有单测。 */
fun directTag(hit: TitleHit): String? {
    val e = hit.direct ?: return null
    val ep = e.episodeNo ?: return null
    return listOfNotNull(e.seasonNo?.takeIf { it != 1 }?.let { "第 $it 季" }, "第 $ep 集").joinToString(" ")
}

/** 搜索结果行上的类型名（以前只分剧集 / 电影，合集、其它视频也写成了「电影」）。纯函数，有单测。 */
fun searchKindLabel(kind: Kind): String = when (kind) {
    Kind.Movie -> "电影"
    Kind.Series -> "剧集"
    Kind.BoxSet -> "合集"
    Kind.Episode -> "单集"
    Kind.Season -> "季"
    Kind.Person -> "人物"
    Kind.Other -> "视频"
}

private val INDEX_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "Overview", "CommunityRating"))

/** 1.4.1 的索引（三端的搜索页用这个）：[SearchIndexStore] 那份（内存 → 磁盘 → 服务器；旧了后台刷新）。 */
suspend fun loadLibraryIndex(client: EmbyClient): SearchIndex {
    val s = SearchIndexStore.get(client)
    val titles = s.titles.filter { it.kind == Kind.Movie || it.kind == Kind.Series }
    return SearchIndex(titles, titles.size, s.engine)
}

/** 只有电影 + 剧集片名的旧索引（发现页的词云、单测用；搜索页用 [loadLibraryIndex]）。 */
suspend fun loadSearchIndex(client: EmbyClient, pageSize: Int = 5000): SearchIndex {
    // 1.3.1：分页取全（不再截断在 5000 部）——截断之外的片子以前本地永远搜不到。[pageSize] 只为单测能强制走多页。
    val out = ArrayList<Title>()
    var total = 0
    while (true) {
        val r = client.getItems(
            LibraryQuery(includeItemTypes = listOf("Movie", "Series"), recursive = true, sortBy = listOf("SortName"), sortOrder = "Ascending", limit = pageSize, startIndex = out.size.takeIf { it > 0 }),
            INDEX_FIELDS,
        )
        total = maxOf(total, r.totalRecordCount)
        if (r.items.isEmpty()) break
        out += r.items.map { client.titleOf(it) }
        if (r.items.size < pageSize || out.size >= total) break
    }
    return SearchIndex(out, maxOf(total, out.size))
}

/**
 * 本地匹配（1.4.1 起走 `ui/…/search/` 的分档匹配，docs/search.md）：整词 > 前缀 > 子串 > 全拼 > 首字母 > 中文缩写 > 英文前缀 / 错一个字母；
 * 繁简、全半角、标点、中文数字 / 罗马数字都先折成一样。
 * 查询词正好是一个题材（「喜剧」）或年份（「2024」）时，片名命中之后再接上这个题材 / 这一年的片子（词云里点的多半是这些），高分在前。纯函数，有单测。
 */
fun searchTitles(index: List<Title>, query: String, limit: Int = 30): List<TitleHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val byName = SearchMatch.search(DocMemo.titles(index), SearchQuery(q), limit).map { it.toTitleHit() }
    return withTags(index, q, byName, limit)
}

/**
 * 搜索页用的：[SearchIndex.engine] 在就用它（片名 / 原名 / 排序名、合集、其它视频；「剧名 + 集号」时那部剧排第一），
 * 不在（旧索引）就退回 [searchTitles] 的列表版。
 */
fun searchTitles(index: SearchIndex?, query: String, limit: Int = 30): List<TitleHit> {
    val engine = index?.engine ?: return searchTitles(index?.titles.orEmpty(), query, limit)
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val direct = engine.directWithSeries(q)?.let { (series, eps) -> series?.let { TitleHit(it.item, null, direct = eps.firstOrNull()?.item) } }
    val byName = (listOfNotNull(direct) + engine.titles(q, limit).map { it.toTitleHit() }).distinctBy { it.title.id }
    return withTags(index.titles, q, byName, limit)
}

/** 单集（本地索引）：「剧名 + 集号」直达的那一集在最前，接着是集名命中的。索引没好 → 空（界面用服务器那一路）。 */
fun searchEpisodesLocal(index: SearchIndex?, query: String, limit: Int = 30): List<Title> =
    index?.engine?.episodes(query.trim(), limit)?.map { it.item }.orEmpty()

/** 本地的单集在前，服务器另外找到的接在后面（同一集只留一次）。 */
fun mergeEpisodes(local: List<Title>, remote: List<Title>): List<Title> {
    val seen = local.mapTo(HashSet()) { it.id }
    return local + remote.filter { seen.add(it.id) }
}

private fun withTags(index: List<Title>, q: String, byName: List<TitleHit>, limit: Int): List<TitleHit> {
    val year = q.toIntOrNull()?.takeIf { q.length == 4 && it in 1880..2100 }
    val byTag = if (year == null && index.none { t -> t.genres.any { it.equals(q, ignoreCase = true) } }) emptyList() else {
        val named = byName.map { it.title.id }.toSet()
        index.filter { t -> t.id !in named && (if (year != null) t.year == year else t.genres.any { it.equals(q, ignoreCase = true) }) }
            .sortedWith(compareByDescending<Title> { it.rating?.toFloatOrNull() ?: 0f }.thenBy { it.name })
            .map { TitleHit(it, null) }
    }
    return (byName + byTag).take(limit)
}

suspend fun searchRemote(client: EmbyClient, query: String): RemoteHits {
    val q = query.trim()
    if (q.isEmpty()) return RemoteHits(emptyList(), emptyList())
    // 1.4.1：本地索引好了就不再问 Emby（它的 SearchTerm 只认词的开头，本地那一路是它的超集）；人物没取到（服务器关了 /Persons）的话人物照旧问
    SearchIndexStore.peek(client)?.let { snap ->
        val people = if (snap.people.isNotEmpty() || Pinyin.isInitialsQuery(q)) emptyList()
        else client.searchPeople(q, limit = 12).map { Person(it.id, it.name, photo = client.personImage(it.id, it.imageTag, ImageUrlOptions(maxWidth = 360))) }
        return RemoteHits(emptyList(), people)
    }
    // 1.3.1：片 / 剧**任何词都问服务器**。以前「≤6 个纯字母」整段跳过（本意是挡拼音首字母 yxj），
    // 把 dune / up / her 这类英文片名也一起挡了：本地索引只有中文片名时就「搜不到」。
    // 片 + 剧这一路条目少，拼音首字母问过去也只是 0 条，不亏。
    val titles = client.searchItems(q, LibraryQuery(includeItemTypes = listOf("Movie", "Series"), limit = 30), INDEX_FIELDS).items.map { client.titleOf(it) }
    // 单集和人物数量大：只打了拼音首字母（纯字母短词）时不查
    if (Pinyin.isInitialsQuery(q) && q.length <= 6 && q.all { it.isLetter() }) return RemoteHits(emptyList(), emptyList(), titles)
    val episodes = client.searchItems(q, LibraryQuery(includeItemTypes = listOf("Episode"), limit = 30), ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio"))).items.map { client.titleOf(it) }
    val people = client.searchPeople(q, limit = 12).map { Person(it.id, it.name, photo = client.personImage(it.id, it.imageTag, ImageUrlOptions(maxWidth = 360))) }
    return RemoteHits(episodes, people, titles)
}

/** 结果的一行小计：「30 条结果：片 2 · 单集 27 · 演员 1」。 */
fun searchTally(titles: Int, episodes: Int, people: Int): String {
    val parts = listOfNotNull("片 $titles".takeIf { titles > 0 }, "单集 $episodes".takeIf { episodes > 0 }, "演员 $people".takeIf { people > 0 })
    return "${titles + episodes + people} 条结果：" + parts.joinToString(" · ")
}

/** 服务器那一路的结果能不能算「当前这个词的」：词已经停稳（settled == query），而且留着的这份结果就是按这个词取来的
 *  （[com.gugaart.ui.data.Load.valueKeys] 的最后一个参数 == settled）。返回搜索页时缓存种子和当前词一致，第一帧就能用，
 *  滚动位置才还原得了；换了词、新请求还在路上时旧结果不算数（1.3.1）。纯函数，有单测。 */
fun remoteResultsReady(settled: String, query: String, valueKeys: List<Any?>?): Boolean =
    settled == query && valueKeys?.lastOrNull() == settled

/**
 * 搜索（`Route.Search(q)`，设计稿 `p=search`）：报头的页名版「检索」；一个大号宋体的输入行（这一页不铺图）；
 * 空状态（她举笔）/ 结果（片：最佳匹配是大卡 + 其余编号行；单集；演员）/ 搜不到（她摊手 +「换个词」）。命中的字上专色。
 */
@Composable
fun SearchScreen(initial: String?) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    LaunchedEffect(Unit) { chrome.spot = null }
    // 返回需要记住搜索词（进详情再返回，词和结果还在，滚动位置才还原得了）：rememberSaveable，跟着返回栈条目走（PageStates）
    var query by rememberSaveable { mutableStateOf(preset.query ?: initial.orEmpty()) }
    var remoteSettled by rememberSaveable { mutableStateOf(query) }
    val index = rememberLoad("search-index") { loadLibraryIndex(it) }
    val peopleIndex = rememberLoad("search-people") { loadPeopleIndex(it) }
    val remote = rememberLoad("search-remote", remoteSettled) { searchRemote(it, remoteSettled) }
    // 本地搜索先在后台算（150 ms 防抖）；服务器兜底继续 250 ms 防抖
    var searchRef: AsyncSearchState? = null
    val remoteReady = remoteResultsReady(remoteSettled, searchRef?.settled ?: query, remote.valueKeys)
    val effectiveRemote = remote.value?.takeIf {
        remoteReady && (it.titles.isNotEmpty() || it.episodes.isNotEmpty() || it.people.isNotEmpty())
    }
    val search = rememberAsyncSearch(query, index.value, peopleIndex.value, effectiveRemote)
    searchRef = search
    LaunchedEffect(query, search.flushSeq) {
        if (query.isBlank()) {
            remoteSettled = ""
        } else if (remoteSettled != query) {
            delay(REMOTE_DEBOUNCE_MS)
            remoteSettled = query
        }
    }
    val snap = search.snapshot
    val hits = snap.hits
    val people = snap.people
    val first = snap.peopleFirst
    val star = people.firstOrNull()?.person?.takeIf { first }
    val worksLoad = rememberLoad("search-works", star?.id) { c -> star?.let { loadPersonWorks(c, it.id) }.orEmpty() }
    val works = worksLoad.value.orEmpty().takeIf { star != null }.orEmpty()
    val scroll = rememberScrollState(preset.scroll)
    ReportScroll(scroll)
    val q = query.trim()
    // 1.6 A1 多台一起搜：本台的片出来了、不到 5 条（或者翻到了底）→ 并发问其它已保存的服务器，结果另起一组「其它服务器」
    val homeSettled = q.isNotEmpty() && !search.isSearching(query, index.value != null || peopleIndex.value != null || effectiveRemote != null) && !index.pending
    val reachedEnd = rememberReachedEnd(scroll)
    val other = rememberOtherServerSearch(query, homeSettled && OtherSearchRules.shouldAsk(hits.size, reachedEnd), hits.map { it.title })
    // 找过 + 词云（打开了哪一条 / 按了键盘上的「搜索」/ 点了词云里的词 = 记一笔）
    val recents = rememberSearchRecents()
    val cloud = remember(index.value, recents.items) {
        val day = ((SearchMemory.nowMs() + SearchMemory.offsetMs()) / SearchMemory.DAY_MS).toInt()
        searchCloud(index.value?.titles.orEmpty(), seed = day, skip = recents.items.take(3).map { it.term }.toSet())
    }
    fun openHit(id: String) {
        recents.add(q)
        graph.open(id)
    }
    fun pick(term: String) {
        query = term
        search.flush(term)
        remoteSettled = term
        recents.add(term)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = dimens.margin).testTag("search")) {
            Spacer(Modifier.height(dimens.statusBar + 8.dp))
            Masthead(volume = "", date = index.value?.let { "共 ${it.total} 部" }.orEmpty(), title = "搜索", leading = "SEARCH")
            Row(Modifier.padding(top = 26.dp).fillMaxWidth().heightIn(min = 76.dp), verticalAlignment = Alignment.Bottom) {
                Box(Modifier.weight(1f).padding(bottom = 6.dp)) {
                    if (query.isEmpty()) BasicText("片名、演员、拼音首字母", style = Guga.song(type.section, color = colors.faint), maxLines = 1, modifier = Modifier.padding(bottom = 10.dp))
                    BasicTextField(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth().testTag("search-field").typingTarget(),
                        textStyle = Guga.song(if (Rules.titleUnits(query) > 6) type.title else type.display * 0.86f, FontWeight.Black),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.spot),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            search.flush(query)
                            remoteSettled = query
                            recents.add(query)
                        }),
                    )
                }
                if (query.isNotEmpty()) TextLink("清除", { query = ""; search.clear(); remoteSettled = "" }, Modifier.padding(start = 12.dp), color = colors.dim)
            }
            Rule(strong = true)
            val episodes = snap.episodes
            val waitingRemote = (index.value?.engine == null || peopleIndex.value.isNullOrEmpty()) && (remoteSettled != query || remote.refreshing)
            val searching = search.isSearching(query, index.value != null || peopleIndex.value != null || effectiveRemote != null)
            when {
                q.isEmpty() -> PhoneSearchIdle(recents.items, cloud, ::pick, recents::clear)
                hits.isEmpty() && episodes.isEmpty() && people.isEmpty() ->
                    when {
                        other.hits.isNotEmpty() -> PhoneOtherServers(other, 1, Modifier.padding(top = 26.dp)) { openHit(it) }
                        index.pending || waitingRemote || searching || other.searching ->
                            BasicText("搜索中…", Modifier.padding(top = 16.dp), style = Guga.note(type.caption, colors.faint))
                        index.error != null || remote.error != null -> Column(Modifier.padding(top = 16.dp)) {
                            BasicText(index.error ?: remote.error!!, style = Guga.note(type.caption, colors.dim), maxLines = 2)
                            TextLink("重试", { index.retry(); remote.retry() }, Modifier.padding(top = 8.dp))
                        }
                        else -> SearchNoneView(q, { query = ""; search.clear(); remoteSettled = "" })
                    }
                else -> {
                    BasicText(searchTally((hits.map { it.title.id } + works.map { it.id }).toSet().size, episodes.size, people.size), Modifier.padding(top = 12.dp).testTag("search-tally"), style = Guga.note(type.caption, colors.faint), maxLines = 1)
                    @Composable
                    fun PeopleSection(top: androidx.compose.ui.unit.Dp) {
                        SectionHeader("演员", "PEOPLE", Modifier.padding(top = top), trailing = "${people.size}")
                        Row(Modifier.padding(top = 14.dp).testTag("search-people"), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            for (p in people.take(3)) PersonChip(p.person, 92.dp, { openHit(p.person.id) })
                        }
                    }
                    if (star != null) {
                        // 打的是演员的名字：演员在前，接着是 ta 在片库里的作品
                        PeopleSection(26.dp)
                        if (works.isNotEmpty()) {
                            SectionHeader("${star.name}的作品", "WORKS", Modifier.padding(top = dimens.sectionGap).testTag("search-works"), trailing = "${works.size}")
                            for ((i, w) in works.withIndex()) {
                                TitleRow(i + 1, TitleHit(w, null)) { openHit(w.id) }
                                Rule()
                            }
                        }
                        val rest = hits.filter { h -> works.none { it.id == h.title.id } }
                        if (rest.isNotEmpty()) {
                            SectionHeader("片", "TITLES", Modifier.padding(top = dimens.sectionGap), trailing = "${rest.size}")
                            for ((i, h) in rest.withIndex()) {
                                TitleRow(i + 1, h, { e -> openHit(e.id) }) { openHit(h.title.id) }
                                Rule()
                            }
                        }
                    } else if (hits.isNotEmpty()) {
                        SectionHeader("片", "TITLES", Modifier.padding(top = 26.dp), trailing = "${hits.size}")
                        BestMatch(hits.first(), { openHit(hits.first().title.id) }, { e -> openHit(e.id) })
                        Rule()
                        for ((i, h) in hits.drop(1).withIndex()) {
                            TitleRow(i + 2, h, { e -> openHit(e.id) }) { openHit(h.title.id) }
                            Rule()
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        SectionHeader("单集", "EPISODES", Modifier.padding(top = dimens.sectionGap), trailing = "${episodes.size}")
                        for (e in episodes) {
                            val press = rememberPressState()
                            Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = e.name) { openHit(e.seriesId ?: e.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText("E${e.episodeNo ?: ""}", Modifier.width(66.dp), style = Guga.numeral(if ((e.episodeNo ?: 0) >= 1000) 15.sp else type.row * 1.1f, colors.spot), maxLines = 1)
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    BasicText(highlighted(e.name, snap.episodeRanges[e.id], colors.spot), style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    BasicText(
                                        listOfNotNull(e.seriesName, e.seasonNo?.let { "第 $it 季" }, "已看".takeIf { e.played }).joinToString(" · "),
                                        Modifier.padding(top = 4.dp),
                                        style = Guga.song(type.caption, color = colors.dim),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Thumb(e.art.thumb, Rules.episodeNo(e.episodeNo ?: 0), 92.dp)
                            }
                            Rule()
                        }
                    }
                    // 缩写 / 片名：演员排在片子和单集后面
                    if (star == null && people.isNotEmpty()) PeopleSection(dimens.sectionGap)
                    if (other.hits.isNotEmpty()) PhoneOtherServers(other, hits.size + 1, Modifier.padding(top = dimens.sectionGap)) { openHit(it) }
                }
            }
            Spacer(Modifier.height(150.dp + dimens.tabLift))
        }
        StatusBand(solid = scroll.value > with(LocalDensity.current) { 2.dp.toPx() })
    }
}

/**
 * 「其它服务器」一组（1.6 A1）：接着本台的编号；每行 斜体编号 + 片名（命中的字专色）+ 类型 · 年份 + 右边服务器名（几台都有写「2 台」）+ 小海报。
 * 点开 = 那台上这部片的详情（不切换当前服务器）。
 */
@Composable
private fun PhoneOtherServers(other: OtherSearchView, startNo: Int, modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    Column(modifier.testTag("search-other")) {
        SectionHeader("其它服务器", "OTHER SERVERS", trailing = "${other.hits.size}")
        for ((i, h) in other.hits.withIndex()) {
            val t = h.title
            val press = rememberPressState()
            Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = t.name) { onOpen(h.routeId) }.padding(vertical = 12.dp).testTag("search-other-row"), verticalAlignment = Alignment.CenterVertically) {
                BasicText(Rules.episodeNo(startNo + i), Modifier.width(54.dp), style = Guga.numeral(26.sp, colors.spot), maxLines = 1)
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    BasicText(highlighted(t.name, h.range, colors.spot), style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicText(listOfNotNull(searchKindLabel(t.kind), t.year?.toString()).joinToString(" · "), Modifier.padding(top = 4.dp), style = Guga.song(type.caption, color = colors.dim).copy(fontStyle = FontStyle.Normal), maxLines = 1)
                }
                BasicText(h.label, Modifier.padding(end = 12.dp).widthIn(max = 110.dp).testTag("search-other-server"), style = Guga.song(type.caption, FontWeight.Bold, colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.width(46.dp).aspectRatio(2f / 3f)) {
                    CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, false), Modifier.fillMaxSize()) }
                }
            }
            Rule()
        }
    }
}

/** 最佳匹配：海报 + 片名（logo / 宋体，**允许折行**）+ 年份 · 题材 · 集数 + 简介头几行 +「翻开 →」。是 16:9 以内的图版，不是全窗。 */
@Composable
private fun BestMatch(hit: TitleHit, onOpen: () -> Unit, onDirect: (Title) -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val t = hit.title
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).pressGraphics(press).pressable(press, label = t.name, onClick = onOpen).testTag("search-best")) {
        Box(Modifier.width(112.dp).aspectRatio(2f / 3f)) {
            CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, false), Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            if (t.art.logo != null) {
                TitleLogo(t.name, t.art.logo, 200.dp, 62.dp, baseSize = type.title * 0.8f)
            } else {
                // 超长片名：允许折行，命中的字上专色
                BasicText(highlighted(t.name, hit.range, colors.spot), style = Guga.song(if (Rules.titleUnits(t.name) > 9) type.row * 1.15f else type.title * 0.8f, FontWeight.Black, lineHeight = type.title * 0.95f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            originalHitText(hit, colors.spot)?.let { BasicText(it, Modifier.padding(top = 6.dp).testTag("search-original"), style = Guga.song(type.caption, color = colors.dim), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            directTag(hit)?.let { tag -> DirectTag(tag, Modifier.padding(top = 8.dp)) { onDirect(hit.direct!!) } }
            BasicText(
                listOfNotNull(searchKindLabel(t.kind).takeIf { t.kind != Kind.Movie && t.kind != Kind.Series }, t.year?.toString(), t.genres.take(2).joinToString(" · ").takeIf { it.isNotEmpty() }, t.count?.takeIf { t.kind == Kind.Series }?.let { "全 $it 集" }).joinToString(" · "),
                Modifier.padding(top = 8.dp),
                style = Guga.song(type.caption * 1.08f, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (t.overview.isNotEmpty()) BasicText(t.overview, Modifier.padding(top = 4.dp), style = Guga.song(type.caption * 1.05f, color = colors.dim, lineHeight = type.caption * 1.75f), maxLines = 3, overflow = TextOverflow.Ellipsis)
            BasicText("打开  →", Modifier.padding(top = 8.dp), style = Guga.song(type.caption * 1.08f, FontWeight.Bold, colors.spot))
        }
    }
}

/** 其余命中的片：斜体专色编号 + 片名（两行截断，命中的字上专色）+ 副行 + 小海报 46 × 69。 */
@Composable
private fun TitleRow(number: Int, hit: TitleHit, onDirect: (Title) -> Unit = {}, onOpen: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val t = hit.title
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = t.name, onClick = onOpen).padding(vertical = 12.dp).testTag("search-row"), verticalAlignment = Alignment.CenterVertically) {
        BasicText(Rules.episodeNo(number), Modifier.width(54.dp), style = Guga.numeral(26.sp, colors.spot), maxLines = 1)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(highlighted(t.name, hit.range, colors.spot), style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            originalHitText(hit, colors.spot)?.let { BasicText(it, Modifier.padding(top = 4.dp).testTag("search-original"), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            directTag(hit)?.let { tag -> DirectTag(tag, Modifier.padding(top = 6.dp)) { onDirect(hit.direct!!) } }
            BasicText(
                listOfNotNull(searchKindLabel(t.kind), t.year?.toString(), t.rating?.let { "$it 分" }).joinToString(" · "),
                Modifier.padding(top = 4.dp),
                style = Guga.song(type.caption, color = colors.dim).copy(fontStyle = FontStyle.Normal),
                maxLines = 1,
            )
        }
        Box(Modifier.width(46.dp).aspectRatio(2f / 3f)) {
            CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, false), Modifier.fillMaxSize()) }
        }
    }
}

/**
 * 「剧名 + 集号」直达的签（docs/search.md §4）：米白细框 + 米白粗字「第 12 集　→」，点了直接打开那一集（不是那部剧）。
 * 纸面语言的小方框，不用专色当底、不靠颜色表达。[size] 不给 = 手机的说明字号。
 */
@Composable
fun DirectTag(tag: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified, onOpen: () -> Unit) {
    val colors = Guga.colors
    val s = if (size == androidx.compose.ui.unit.TextUnit.Unspecified) Guga.type.caption else size
    val press = rememberPressState()
    BasicText(
        "$tag　→",
        modifier.pressGraphics(press).pressable(press, label = tag, onClick = onOpen).border(1.dp, colors.ink).padding(horizontal = 8.dp, vertical = 3.dp).testTag("search-direct"),
        style = Guga.song(s, FontWeight.Bold, colors.ink, letterSpacing = 0.06.em),
        maxLines = 1,
    )
}

/** 电脑左栏「联想」的一行：一部片（[title] 在）或一位人物。[note] = 名字后面的小字（类型 · 年份 · 直达的那一集 / 「人物」）。 */
@Immutable
data class Suggestion(val id: String, val name: String, val range: IntRange?, val note: String, val title: TitleHit? = null)

/**
 * 联想：片最多 7 条在前，接着最多 2 位人物（设计稿 `design/desktop/js/screens-a2.js` 的 `sug`：片 7 + 人物 2 + 合集 2；合集已经在片里）。
 * 以前只有片：只有演员命中时左栏是一个空的「联想」。纯函数，有单测。
 */
fun suggestionsOf(titles: List<TitleHit>, people: List<PersonHit>): List<Suggestion> =
    titles.take(7).map { h ->
        Suggestion(h.title.id, h.title.name, h.range, listOfNotNull(searchKindLabel(h.title.kind), h.title.year?.toString(), directTag(h)).joinToString(" · "), h)
    } + people.take(2).map { Suggestion(it.person.id, it.person.name, it.range, "人物") }
