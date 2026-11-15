package com.gugaart.ui.screens.wall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.chrome.PhoneTabBarSpec
import com.gugaart.ui.components.EmptyLibraryView
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.FilterNoneView
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.PosterCard
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.RadioMark
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.data.LibraryEntry
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.data.loadLibraries
import com.gugaart.ui.data.libraryItemTypes
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.StatusBand
import com.gugaart.ui.screens.open
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 排序的四种（服务器排，走上游 `LibraryQuery.sortBy`）。 */
enum class WallSort(val label: String, val sortBy: String, val descending: Boolean) {
    Added("最近添加", "DateCreated", true),
    Name("片名", "SortName", false),
    Year("年份", "ProductionYear", true),
    Rating("评分", "CommunityRating", true),
}

/** 状态筛选。 */
enum class WatchState(val label: String) { Unseen("未看"), Watching("在看"), Seen("看完"), Favorite("收藏") }

/** 年代的桶：按实际年份分，不假设都是近年（「更早」= 2000 年以前）。 */
enum class Decade(val label: String) {
    D2020("2020s"), D2010("2010s"), D2000("2000s"), Earlier("更早");

    companion object {
        fun of(year: Int?): Decade? = when {
            year == null -> null
            year >= 2020 -> D2020
            year >= 2010 -> D2010
            year >= 2000 -> D2000
            else -> Earlier
        }
    }
}

/** 海报墙的筛选条件。同一组里是「或」，组之间是「与」。 */
@Immutable
data class WallFilter(val genres: Set<String> = emptySet(), val decades: Set<Decade> = emptySet(), val states: Set<WatchState> = emptySet()) {
    /** 条件的个数（「筛选 2」「清掉 2 个筛选条件」）。 */
    val count: Int get() = genres.size + decades.size + states.size

    fun accepts(t: Title): Boolean {
        if (genres.isNotEmpty() && t.genres.none { it in genres }) return false
        if (decades.isNotEmpty() && Decade.of(t.year) !in decades) return false
        if (states.isNotEmpty()) {
            val watching = t.progress > 0f || (t.unplayed != null && t.count != null && t.unplayed in 1 until t.count)
            val ok = states.any {
                when (it) {
                    WatchState.Unseen -> !t.played && !watching
                    WatchState.Watching -> watching
                    WatchState.Seen -> t.played
                    WatchState.Favorite -> t.favorite
                }
            }
            if (!ok) return false
        }
        return true
    }
}

/** 这个库里出现过的题材，按出现次数从多到少，最多 [limit] 个（筛选面板的选项来自真实数据，不是写死的）。 */
fun genreOptions(titles: List<Title>, limit: Int = 8): List<String> =
    titles.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(limit).map { it.key }

private val WALL_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "DateCreated", "SortName", "ProductionYear"))

/** 一个媒体库里的片子（只读）。合集库取 BoxSet，其余取电影和剧集。一次最多 [limit] 部——更大的库的分页加载在下一阶段。 */
suspend fun loadWall(client: EmbyClient, library: LibraryEntry, sort: WallSort, limit: Int = 600): Pair<List<Title>, Int> {
    val types = libraryItemTypes(library.kind)
    val r = client.getItems(
        LibraryQuery(parentId = library.id, includeItemTypes = types, recursive = true, sortBy = listOf(sort.sortBy), sortOrder = if (sort.descending) "Descending" else "Ascending", limit = limit),
        WALL_FIELDS,
    )
    return r.items.map { client.titleOf(it) } to maxOf(r.totalRecordCount, r.items.size)
}

/**
 * 片库（手机第三个标签）/ 一个媒体库的海报墙（`Route.Library(id)`）。设计稿 `p=wall`。
 * 报头的页名版「片库」→ 库名一排（当前的大、带专色下划线和斜体部数）→ 筛选 / 排序一行（**推开式**：在这一行下面展开，把墙推下去，不盖）→ 3 列海报墙。
 * 这一页不铺图；专色 = 兜底朱红。[libraryId] = null：第一个库。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WallScreen(libraryId: String?) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    LaunchedEffect(Unit) { chrome.spot = null }
    val preset = LocalPreset.current
    val initialFilter = remember { WallFilter(genres = preset.wallGenres, decades = Decade.entries.filter { it.label in preset.wallDecades }.toSet()) }
    val initialPanel = preset.wallPanel
    val libs = rememberLoad("libraries") { c -> loadLibraries(c) }
    var picked by rememberSaveable { mutableStateOf(libraryId ?: preset.wallLibrary) }
    var sort by remember { mutableStateOf(WallSort.Added) }
    var filter by remember { mutableStateOf(initialFilter) }
    // 哪一块推开着：filter / sort / null
    var panel by remember { mutableStateOf(initialPanel) }
    val library = libs.value?.let { list -> list.firstOrNull { it.id == picked } ?: list.firstOrNull() }
    val wall = rememberLoad(library?.id, sort) { c -> library?.let { loadWall(c, it, sort) } }
    val all = wall.value?.first
    // 总数 = 这次查询的 TotalRecordCount（一次最多取 600 部；库更大时墙上先是前 600 部）；还没取到时先用库名上的那个数
    val total = wall.value?.second ?: library?.titles
    val shown = remember(all, filter) { all?.filter { filter.accepts(it) } }
    val list = rememberLazyListState()
    val returnPx = with(LocalDensity.current) { PhoneTabBarSpec.RETURN_DP.toPx() }
    LaunchedEffect(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) {
        chrome.reportScroll(list.firstVisibleItemIndex * 2000f + list.firstVisibleItemScrollOffset, 0f, returnPx)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("wall"), state = list) {
            item("head") {
                Column(Modifier.padding(horizontal = dimens.margin)) {
                    Spacer(Modifier.height(dimens.statusBar + 8.dp))
                    Masthead(volume = "", date = "媒体库", title = "片库", leading = "LIBRARY")
                    // 库名一排：不假设库名是中文宽度（emby302）；放不下就横滑
                    // 右缘还有没露出来的库名时渐隐进纸色（看得出还能滑）；选中的那个滑进视野
                    val tabs = rememberScrollState()
                    val tabX = remember { mutableStateMapOf<String, Pair<Int, Int>>() }
                    var tabsWidth by remember { mutableStateOf(0) }
                    LaunchedEffect(library?.id, tabX[library?.id], tabsWidth) {
                        val (x, w) = tabX[library?.id] ?: return@LaunchedEffect
                        if (tabsWidth <= 0) return@LaunchedEffect
                        val target = when {
                            x < tabs.value -> x
                            x + w > tabs.value + tabsWidth -> x + w - tabsWidth
                            else -> return@LaunchedEffect
                        }
                        tabs.animateScrollTo(target.coerceIn(0, tabs.maxValue))
                    }
                    Row(
                        Modifier
                            .padding(top = 26.dp)
                            .fillMaxWidth()
                            .onSizeChanged { tabsWidth = it.width }
                            .drawWithContent {
                                drawContent()
                                val fade = 36.dp.toPx()
                                if (tabs.canScrollForward) drawRect(Brush.horizontalGradient(listOf(colors.paper.copy(alpha = 0f), colors.paper), size.width - fade, size.width), Offset(size.width - fade, 0f), Size(fade, size.height))
                                if (tabs.canScrollBackward) drawRect(Brush.horizontalGradient(listOf(colors.paper, colors.paper.copy(alpha = 0f)), 0f, fade), Offset.Zero, Size(fade, size.height))
                            }
                            .horizontalScroll(tabs)
                            .testTag("lib-tabs"),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        for (lib in libs.value.orEmpty()) {
                            val on = lib.id == library?.id
                            val press = rememberPressState()
                            Row(
                                Modifier
                                    .onGloballyPositioned { tabX[lib.id] = it.positionInParent().x.toInt() to it.size.width }
                                    .pressGraphics(press)
                                    .pressable(press, label = lib.name) { picked = lib.id; filter = WallFilter(); panel = null }
                                    .drawBehind { if (on) drawLine(colors.spot, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }
                                    .padding(bottom = 12.dp)
                                    .testTag(if (on) "lib-on" else "lib-off"),
                                verticalAlignment = Alignment.Top,
                            ) {
                                BasicText(lib.name, style = Guga.song(if (on) 26.sp else 20.sp, if (on) FontWeight.Black else FontWeight.Normal, if (on) colors.ink else colors.dim), maxLines = 1)
                                // 部数没给就不写（不写 0）
                                lib.titles?.let { BasicText(" $it", Modifier.padding(top = 2.dp), style = Guga.numeral(10.sp, colors.dim), maxLines = 1) }
                            }
                        }
                    }
                    Rule(strong = true)
                    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        ToggleWord(GuguIcon.SlidersHorizontal, "筛选", filter.count.takeIf { it > 0 }?.toString(), filter.count > 0 || panel == "filter") { panel = if (panel == "filter") null else "filter" }
                        ToggleWord(GuguIcon.ArrowUpDown, sort.label, if (sort.descending) "↓" else "↑", panel == "sort") { panel = if (panel == "sort") null else "sort" }
                        Spacer(Modifier.weight(1f))
                        val tally = when {
                            shown == null -> total?.let { "$it 部" }
                            filter.count > 0 -> "筛出 ${shown.size} / ${total ?: all?.size ?: 0} 部"
                            else -> "${total ?: shown.size} 部"
                        }
                        if (tally != null) BasicText(tally, style = Guga.note(type.caption, colors.dim), maxLines = 1, modifier = Modifier.testTag("wall-tally"))
                    }
                    Rule()
                    // 推开式：筛选
                    AnimatedVisibility(panel == "filter", enter = expandVertically(), exit = shrinkVertically()) {
                        Column(Modifier.testTag("wall-filter")) {
                            FilterGroup("题材", genreOptions(all.orEmpty()), { it in filter.genres }) { g -> filter = filter.copy(genres = filter.genres.toggle(g)) }
                            FilterGroup("年代", Decade.entries.map { it.label }, { l -> filter.decades.any { it.label == l } }) { l ->
                                filter = filter.copy(decades = filter.decades.toggle(Decade.entries.first { it.label == l }))
                            }
                            FilterGroup("状态", WatchState.entries.map { it.label }, { l -> filter.states.any { it.label == l } }) { l ->
                                filter = filter.copy(states = filter.states.toggle(WatchState.entries.first { it.label == l }))
                            }
                            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextLink("全部重置", { filter = WallFilter() })
                                Spacer(Modifier.weight(1f))
                                PrimaryButton("看这 ${shown?.size ?: 0} 部", { panel = null }, icon = null)
                            }
                            Rule(strong = true)
                        }
                    }
                    // 推开式：排序
                    AnimatedVisibility(panel == "sort", enter = expandVertically(), exit = shrinkVertically()) {
                        Column(Modifier.testTag("wall-sort")) {
                            for (s in WallSort.entries) {
                                val press = rememberPressState()
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).pressGraphics(press).pressable(press, label = s.label) { sort = s; panel = null },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RadioMark(s == sort)
                                    BasicText(s.label, style = Guga.song(type.row, if (s == sort) FontWeight.Bold else FontWeight.Normal, if (s == sort) colors.ink else colors.dim))
                                    Spacer(Modifier.weight(1f))
                                    BasicText(if (s.descending) "从新到旧 / 从高到低" else "A → Z", style = Guga.note(type.fine * 1.1f, colors.faint))
                                }
                                Rule()
                            }
                        }
                    }
                }
            }
            when {
                wall.error != null && shown == null -> item("error") { ErrorView(wall.error!!, wall::retry) }
                libs.value?.isEmpty() == true -> item("nolib") { EmptyLibraryView() }
                shown == null -> {
                    if (wall.showSkeleton || libs.showSkeleton) item("loading") { LoadingView(seed = 1) }
                    // 骨架：形状 = 要出现的海报墙
                    items(3, key = { "sk$it" }) { WallRow(listOf(null, null, null)) }
                }
                shown.isEmpty() && filter.count > 0 -> item("none") { FilterNoneView(filter.count, { filter = WallFilter() }) }
                shown.isEmpty() -> item("empty") { EmptyLibraryView() }
                else -> {
                    val rows = shown.chunked(3)
                    items(rows.size, key = { rows[it].first().id }) { i -> WallRow(rows[i]) { graph.open(it.id) } }
                    item("end") {
                        BasicText("— 到底了 —", Modifier.fillMaxWidth().padding(top = 26.dp), style = Guga.note(type.caption, colors.faint).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center))
                    }
                }
            }
            item("safe") { Spacer(Modifier.height(150.dp)) }
        }
        StatusBand(solid = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 4, modifier = Modifier.background(colors.paper.copy(alpha = 0f)))
    }
}

private fun <T> Set<T>.toggle(v: T): Set<T> = if (v in this) this - v else this + v

/** 海报墙：3 列，列距 17，行距 22。 */
const val WALL_COLUMNS = 3
val WALL_GAP = 17.dp

/** 一列的宽：三列**等宽**，由可用宽度算（412 dp 宽的设计稿上 = 110；更窄 / 字更大的手机上跟着变，不是写死的 110）。纯函数，有单测。 */
fun wallColumnWidth(available: Dp, columns: Int = WALL_COLUMNS, gap: Dp = WALL_GAP): Dp = ((available - gap * (columns - 1)) / columns).coerceAtLeast(0.dp)

/**
 * 海报墙的一行：三列等宽（[wallColumnWidth]），海报 2 : 3。不满 3 张不拉伸、不凑满（空位留着）。
 * 以前每张卡写死 110 dp：屏幕窄于 412 dp（或显示大小调大）时前两列占满、第三列被挤窄变形。
 */
@Composable
private fun WallRow(titles: List<Title?>, onOpen: (Title) -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Guga.dimens.margin).padding(top = 22.dp)) {
        val w = wallColumnWidth(maxWidth)
        Row(horizontalArrangement = Arrangement.spacedBy(WALL_GAP)) {
            for (t in titles) PosterCard(t, w, { t?.let(onOpen) }, Modifier.testTag("wall-cell"), evenTitle = true)
        }
    }
}

/** 「筛选 2」「最近添加 ↓」：一个图标 + 宋体 700 的词 + 斜体小字。打开 / 有条件时词变专色（同时图标也变，不靠颜色单独表达：数字在旁边）。 */
@Composable
private fun ToggleWord(icon: GuguIcon, text: String, note: String?, active: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val ink = if (active) colors.spot else colors.ink
    Row(Modifier.pressGraphics(press).heightIn(min = 48.dp).pressable(press, label = text, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, ink, 15.dp)
        BasicText(text, style = Guga.song(14.sp, FontWeight.Bold, ink), maxLines = 1)
        if (note != null) BasicText(note, style = Guga.note(13.sp, ink), maxLines = 1)
    }
}

/** 筛选的一组：左边专色的组名，右边一排可折行的选项；选中 = 实心小方块 + 加粗。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(name: String, options: List<String>, selected: (String) -> Boolean, onToggle: (String) -> Unit) {
    if (options.isEmpty()) return
    val colors = Guga.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        BasicText(name, Modifier.width(54.dp).padding(top = 9.dp), style = Guga.song(12.sp, color = colors.spot, letterSpacing = 0.3.em), maxLines = 1)
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            for (o in options) {
                val on = selected(o)
                val press = rememberPressState()
                Row(Modifier.pressGraphics(press).heightIn(min = 36.dp).pressable(press, label = o) { onToggle(o) }.testTag(if (on) "filter-on" else "filter-off"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    RadioMark(on, size = 7.dp)
                    BasicText(o, style = Guga.song(15.sp, if (on) FontWeight.Bold else FontWeight.Normal, if (on) colors.ink else colors.dim), maxLines = 1)
                }
            }
        }
    }
    Rule()
}
