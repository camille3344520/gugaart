package com.gugaart.ui.screens.search

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.PersonChip
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SearchIdleView
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

/** 搜索要用的本地索引：全库的片名（电影 + 剧集），拼音首字母在本地匹配（decisions Q8），不问服务器。 */
@Immutable
data class SearchIndex(val titles: List<Title>, val total: Int)

/** 服务器那一半：单集和人物（它们数量大，不建本地索引）。 */
@Immutable
data class RemoteHits(val episodes: List<Title>, val people: List<Person>)

/** 命中的一部片 + 命中的字的范围（上专色）。 */
@Immutable
data class TitleHit(val title: Title, val range: IntRange?)

private val INDEX_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "Overview"))

suspend fun loadSearchIndex(client: EmbyClient): SearchIndex {
    val r = client.getItems(LibraryQuery(includeItemTypes = listOf("Movie", "Series"), recursive = true, sortBy = listOf("SortName"), sortOrder = "Ascending", limit = 5000), INDEX_FIELDS)
    return SearchIndex(r.items.map { client.titleOf(it) }, maxOf(r.totalRecordCount, r.items.size))
}

/** 本地匹配：原文命中优先、越靠前越好；全是字母数字的查询词再按拼音首字母匹配（`yxj` → 颜心记）。纯函数，有单测。 */
fun searchTitles(index: List<Title>, query: String, limit: Int = 30): List<TitleHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return index.mapNotNull { t -> Pinyin.score(t.name, q)?.let { Triple(t, it, Pinyin.match(t.name, q)) } }
        .sortedWith(compareBy<Triple<Title, Int, IntRange?>> { it.second }.thenBy { it.first.name.length }.thenBy { it.first.name })
        .take(limit)
        .map { TitleHit(it.first, it.third) }
}

suspend fun searchRemote(client: EmbyClient, query: String): RemoteHits {
    val q = query.trim()
    // 只打了拼音首字母（纯字母）时服务器帮不上忙：单集和人物不查
    if (q.isEmpty() || Pinyin.isInitialsQuery(q) && q.length <= 6 && q.all { it.isLetter() }) return RemoteHits(emptyList(), emptyList())
    val episodes = client.searchItems(q, LibraryQuery(includeItemTypes = listOf("Episode"), limit = 30), ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio"))).items.map { client.titleOf(it) }
    val people = client.searchPeople(q, limit = 12).map { Person(it.id, it.name, photo = client.personImage(it.id, it.imageTag, ImageUrlOptions(maxWidth = 360))) }
    return RemoteHits(episodes, people)
}

/** 结果的一行小计：「30 条结果：片 2 · 单集 27 · 演员 1」。 */
fun searchTally(titles: Int, episodes: Int, people: Int): String {
    val parts = listOfNotNull("片 $titles".takeIf { titles > 0 }, "单集 $episodes".takeIf { episodes > 0 }, "演员 $people".takeIf { people > 0 })
    return "${titles + episodes + people} 条结果：" + parts.joinToString(" · ")
}

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
    var query by remember { mutableStateOf(preset.query ?: initial.orEmpty()) }
    // 打字时不每个字都去问服务器：停 250 ms 才查
    var settled by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        delay(250)
        settled = query
    }
    val index = rememberLoad("search-index") { loadSearchIndex(it) }
    val remote = rememberLoad("search-remote", settled) { searchRemote(it, settled) }
    val hits = remember(index.value, query) { searchTitles(index.value?.titles.orEmpty(), query) }
    val scroll = rememberScrollState(preset.scroll)
    ReportScroll(scroll)
    val q = query.trim()

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
                        Modifier.fillMaxWidth().testTag("search-field"),
                        textStyle = Guga.song(if (Rules.titleUnits(query) > 6) type.title else type.display * 0.86f, FontWeight.Black),
                        singleLine = true,
                        cursorBrush = SolidColor(colors.spot),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                }
                if (query.isNotEmpty()) TextLink("清除", { query = "" }, Modifier.padding(start = 12.dp), color = colors.dim)
            }
            Rule(strong = true)
            val episodes = remote.value?.episodes.orEmpty().takeIf { settled == query }.orEmpty()
            val people = remote.value?.people.orEmpty().takeIf { settled == query }.orEmpty()
            when {
                q.isEmpty() -> SearchIdleView(seed = 1)
                hits.isEmpty() && episodes.isEmpty() && people.isEmpty() ->
                    if (index.pending || remote.refreshing || settled != query) {
                        BasicText("正在找……", Modifier.padding(top = 16.dp), style = Guga.note(type.caption, colors.faint))
                    } else {
                        SearchNoneView(q, { query = "" })
                    }
                else -> {
                    BasicText(searchTally(hits.size, episodes.size, people.size), Modifier.padding(top = 12.dp).testTag("search-tally"), style = Guga.note(type.caption, colors.faint), maxLines = 1)
                    if (hits.isNotEmpty()) {
                        SectionHeader("片", "TITLES", Modifier.padding(top = 26.dp), trailing = "${hits.size}")
                        BestMatch(hits.first()) { graph.open(hits.first().title.id) }
                        Rule()
                        for ((i, h) in hits.drop(1).withIndex()) {
                            TitleRow(i + 2, h) { graph.open(h.title.id) }
                            Rule()
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        SectionHeader("单集", "EPISODES", Modifier.padding(top = dimens.sectionGap), trailing = "${episodes.size}")
                        for (e in episodes) {
                            val press = rememberPressState()
                            Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = e.name) { graph.open(e.seriesId ?: e.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText("E${e.episodeNo ?: ""}", Modifier.width(66.dp), style = Guga.numeral(if ((e.episodeNo ?: 0) >= 1000) 15.sp else type.row * 1.1f, colors.spot), maxLines = 1)
                                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                    BasicText(highlighted(e.name, Pinyin.match(e.name, q), colors.spot), style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                    if (people.isNotEmpty()) {
                        SectionHeader("演员", "PEOPLE", Modifier.padding(top = dimens.sectionGap), trailing = "${people.size}")
                        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            for (p in people.take(3)) PersonChip(p, 92.dp, { graph.open(p.id) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(150.dp))
        }
        StatusBand(solid = scroll.value > with(LocalDensity.current) { 2.dp.toPx() })
    }
}

/** 最佳匹配：海报 + 片名（logo / 宋体，**允许折行**）+ 年份 · 题材 · 集数 + 简介头几行 +「翻开 →」。是 16:9 以内的图版，不是全窗。 */
@Composable
private fun BestMatch(hit: TitleHit, onOpen: () -> Unit) {
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
            BasicText(
                listOfNotNull(t.year?.toString(), t.genres.take(2).joinToString(" · ").takeIf { it.isNotEmpty() }, t.count?.takeIf { t.kind == Kind.Series }?.let { "全 $it 集" }).joinToString(" · "),
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
private fun TitleRow(number: Int, hit: TitleHit, onOpen: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val t = hit.title
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = t.name, onClick = onOpen).padding(vertical = 12.dp).testTag("search-row"), verticalAlignment = Alignment.CenterVertically) {
        BasicText(Rules.episodeNo(number), Modifier.width(54.dp), style = Guga.numeral(26.sp, colors.spot), maxLines = 1)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(highlighted(t.name, hit.range, colors.spot), style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(
                listOfNotNull(if (t.kind == Kind.Series) "剧集" else "电影", t.year?.toString(), t.rating?.let { "$it 分" }).joinToString(" · "),
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
