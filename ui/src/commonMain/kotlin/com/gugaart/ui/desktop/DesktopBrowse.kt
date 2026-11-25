package com.gugaart.ui.desktop

import com.gugaart.ui.data.ItemManage
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.EmptyLibraryView
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.typingTarget
import com.gugaart.ui.components.FilterNoneView
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.SearchIdleView
import com.gugaart.ui.components.SearchNoneView
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.highlighted
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.LibraryEntry
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Pinyin
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.loadLibraries
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.discover.DiscoverData
import com.gugaart.ui.screens.discover.chartLine
import com.gugaart.ui.screens.discover.CHART_SIZE
import com.gugaart.ui.screens.discover.TOP_POOL
import com.gugaart.ui.screens.discover.loadDiscover
import com.gugaart.ui.screens.discover.loadLucky
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.search.RemoteHits
import com.gugaart.ui.screens.search.TitleHit
import com.gugaart.ui.screens.search.loadLibraryIndex
import com.gugaart.ui.screens.search.remoteResultsReady
import com.gugaart.ui.screens.search.searchRemote
import com.gugaart.ui.screens.search.searchTitles
import com.gugaart.ui.screens.sets.loadSets
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.screens.wall.Decade
import com.gugaart.ui.screens.wall.WallFilter
import com.gugaart.ui.screens.wall.WallSort
import com.gugaart.ui.screens.wall.WatchState
import com.gugaart.ui.screens.wall.genreOptions
import com.gugaart.ui.screens.wall.loadWall
import com.gugaart.ui.screens.wall.toFilter
import com.gugaart.ui.screens.wall.toPref
import com.gugaart.ui.screens.wall.toSort
import com.gugaart.ui.screens.settings.LocalGugaSettings
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 浏览类界面的数（设计稿 `d2.css` / `d3.css`，rem）。 */
object DeskBrowseSpec {
    /** 页面大标题（「电影」「片库」「合集」）。 */
    const val HEAD_TITLE = 5.6f

    /** 工具条上的线框键的高。 */
    const val TOOL = 3.6f

    /** 合集卡的最小宽。 */
    const val COLL_MIN = 20f

    /** 媒体库列表的一行：拼贴图 35 × 19.6；右边的小海报宽 9。 */
    const val LIB_MOSAIC_W = 35f
    const val LIB_MOSAIC_H = 19.6f
    const val LIB_POSTER = 9f

    /** 列表视图的行高。 */
    const val LIST_ROW = 5.1f

    /** 搜索：左联想 5 : 右结果 7。 */
    const val SEARCH_LEFT = 5f
    const val SEARCH_RIGHT = 7f

    /** 发现 · 今日专题：剧照铺到 76 % 屏高。 */
    const val DISCOVER_BLEED = 0.76f

    /** 评分筛选的几档。 */
    val RATINGS = listOf(9, 8, 7)
}

/* ------------------------------------------------------------------ 共用：页头 */

/**
 * 浏览页的页头：宋体大字的页名 + 专色斜体英文 + 斜体部数，右端是这一页的工具；下面一条米白实线。
 */
@Composable
fun DeskPageHead(title: String, english: String, modifier: Modifier = Modifier, count: String? = null, unit: String? = null, note: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    val colors = Guga.colors
    Column(modifier.fillMaxWidth().padding(horizontal = Guga.dimens.margin).testTag("desk-page-head")) {
        Row(Modifier.fillMaxWidth()) {
            BasicText(title, Modifier.alignByBaseline(), style = Guga.song(rsp(DeskBrowseSpec.HEAD_TITLE), FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(english, Modifier.alignByBaseline().padding(start = Guga.rem(2.4f)), style = Guga.numeral(rsp(1.1f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
            if (count != null) BasicText(count, Modifier.alignByBaseline().padding(start = Guga.rem(2.4f)), style = Guga.numeral(rsp(3f)), maxLines = 1)
            if (unit != null) BasicText(unit, Modifier.alignByBaseline().padding(start = Guga.rem(if (count != null) 2.4f else 1.4f)), style = Guga.song(rsp(1.2f), color = colors.faint), maxLines = 1)
            if (note != null) BasicText(note, Modifier.alignByBaseline().padding(start = Guga.rem(1.4f)), style = Guga.song(rsp(1.3f), color = colors.faint), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(Modifier.align(Alignment.Bottom).padding(bottom = Guga.rem(0.6f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f)), content = trailing)
        }
        Box(Modifier.padding(top = Guga.rem(1.2f)).fillMaxWidth().height(1.dp).background(colors.ink))
    }
}

/** 一排页签（「全部 / 未看 / 在看…」）里的一格：选中 = 米白 + 专色下划线。 */
@Composable
private fun TabWord(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    BasicText(
        text,
        Modifier
            .hover(hover)
            .deskFocusRing(press)
            .pressable(press, label = text, onClick = onClick)
            .drawBehind { if (on) drawLine(colors.spot, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .padding(bottom = Guga.rem(0.5f))
            .testTag(if (on) "desk-tab-on" else "desk-tab-off"),
        style = Guga.song(rsp(1.4f), FontWeight.SemiBold, if (on || hover.over) colors.ink else colors.faint),
        maxLines = 1,
    )
}

/* ------------------------------------------------------------------ 片库：媒体库列表 */

/** 媒体库列表的一行要的：这个库 + 它最近添加的几部（合集库 = 头几套合集）。 */
@Immutable
data class LibShelf(val library: LibraryEntry, val titles: List<Title>)

private val SHELF_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "CommunityRating"))

/** 只读：上游 :core 的 `getViewList` + 每个库的 `getLatest`（合集库没有「最近添加」，取头几套合集）。 */
suspend fun loadLibShelves(client: EmbyClient): List<LibShelf> = loadLibraries(client).map { lib ->
    val titles = try {
        if (lib.kind == "boxsets") loadSets(client, null).take(16) else client.getLatest(parentId = lib.id, limit = 16, o = SHELF_FIELDS).map { client.titleOf(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    LibShelf(lib, titles)
}

/** 右边排几张小海报：给它的宽 ÷（海报宽 + 间隔），3–10 张。纯函数，有单测。 */
fun libPosterCount(widthDp: Float, remDp: Float): Int = ((widthDp + remDp) / ((DeskBrowseSpec.LIB_POSTER + 1f) * remDp)).toInt().coerceIn(3, 10)

/** 媒体库列表（设计稿 `p=libs`）：每个库一行——编号、四张拼贴、库名和部数、右边一排最近添加。 */
@Composable
fun DesktopLibsScreen(today: Today) {
    val graph = LocalGraph.current
    val nav = LocalWideNav.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val win = LocalWindowDp.current
    LaunchedEffect(Unit) { chrome.spot = null }
    val load = rememberLoad("lib-shelves") { loadLibShelves(it) }
    val shelves = load.value
    DesktopPage(Modifier.testTag("desktop-libs")) {
        val total = shelves?.sumOf { it.library.titles ?: 0 } ?: 0
        DeskPageHead("片库", "SECTIONS") {
            if (shelves != null) BasicText("${shelves.size} 个媒体库" + if (total > 0) " · $total 部" else "", style = Guga.song(rsp(1.3f), color = colors.faint), maxLines = 1)
        }
        when {
            shelves == null && load.error != null -> ErrorView(load.error!!, load::retry)
            shelves == null -> if (load.showSkeleton) LoadingView(seed = today.day)
            shelves.isEmpty() -> EmptyLibraryView()
            else -> Column(Modifier.padding(horizontal = dimens.margin)) {
                val rem = dimens.rem.value
                val contentW = win.width.value - dimens.margin.value * 2
                val leftW = (7f + DeskBrowseSpec.LIB_MOSAIC_W + 2.4f + 30f) * rem
                val n = libPosterCount(contentW - leftW, rem)
                for ((i, shelf) in shelves.withIndex()) {
                    val lib = shelf.library
                    val press = rememberPressState()
                    val hover = rememberHover()
                    val mosaic = if (shelf.titles.size >= n + 4) shelf.titles.subList(n, n + 4) else shelf.titles.take(4)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .hover(hover)
                            .drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
                            .deskFocusRing(press)
                            .pressable(press, label = lib.name) { if (lib.kind == "boxsets") nav.tab = WideTab.Colls else graph.nav.navigate(Route.Library(lib.id)) }
                            .padding(vertical = Guga.rem(2.4f))
                            .testTag("desktop-lib-row"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(Rules.episodeNo(i + 1), Modifier.width(Guga.rem(7f)), style = Guga.numeral(rsp(2.6f), colors.faint), maxLines = 1)
                        // 四张拼贴（细线框里并排四条）
                        Row(Modifier.size(Guga.rem(DeskBrowseSpec.LIB_MOSAIC_W), Guga.rem(DeskBrowseSpec.LIB_MOSAIC_H)).border(1.dp, colors.line).padding(Guga.rem(0.4f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.4f))) {
                            for (t in mosaic) CoverImage(t.art.poster, Modifier.weight(1f).fillMaxHeight(), Framing(0.5f, 0.3f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize()) }
                            repeat(4 - mosaic.size) { HatchPlate("", Modifier.weight(1f).fillMaxHeight()) }
                        }
                        Column(Modifier.padding(start = Guga.rem(2.4f)).weight(1f)) {
                            BasicText(lib.name, style = Guga.song(rsp(3.6f), FontWeight.Black, if (hover.over) colors.spot else colors.ink, letterSpacing = 0.1.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(libraryEnglish(lib.kind), Modifier.padding(top = Guga.rem(0.6f)), style = Guga.numeral(rsp(1.05f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                            // 部数没给就不写（不写 0）
                            lib.titles?.let {
                                Row(Modifier.padding(top = Guga.rem(1.4f)), verticalAlignment = Alignment.Bottom) {
                                    BasicText(it.toString(), style = Guga.numeral(rsp(2.2f)), maxLines = 1)
                                    BasicText(if (lib.kind == "boxsets") "套" else "部", Modifier.padding(start = Guga.rem(0.6f), bottom = Guga.rem(0.3f)), style = Guga.song(rsp(1.2f), color = colors.faint), maxLines = 1)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f))) {
                            for (t in shelf.titles.take(n)) {
                                Box(Modifier.width(Guga.rem(DeskBrowseSpec.LIB_POSTER)).aspectRatio(2f / 3f).border(1.dp, colors.ink.copy(alpha = 0.2f))) {
                                    CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), textSize = rsp(1.5f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 海报墙 */

/** 片名的索引字母：拼音首字母（大写）；数字、符号、认不出的 = '#'。纯函数，有单测。 */
fun indexLetter(name: String): Char {
    val ch = name.firstOrNull { !it.isWhitespace() } ?: return '#'
    val c = Pinyin.initialOf(ch)?.uppercaseChar() ?: return '#'
    return if (c in 'A'..'Z') c else '#'
}

/** 按字母排（字母索引用）：'#' 在最前，其余按拼音首字母串，再按片名。纯函数，有单测。 */
fun sortedByLetter(titles: List<Title>): List<Title> =
    titles.sortedWith(compareBy<Title> { indexLetter(it.name).let { c -> if (c == '#') ' ' else c } }.thenBy { Pinyin.initials(it.name).lowercase() }.thenBy { it.name })

/** 海报墙卡片的副行：「1994 · 7.5 分」。 */
fun wallMeta(t: Title): String = listOfNotNull(t.year?.toString(), t.rating?.let { "$it 分" }).joinToString(" · ")

private val LETTERS: List<Char> = listOf('#') + ('A'..'Z').toList()

/** 一个媒体库 = 海报墙（设计稿 `p=wall`）；合集库 = 合集列表。 */
@Composable
fun DesktopLibraryScreen(id: String, today: Today) {
    val libs = rememberLoad("libraries") { loadLibraries(it) }
    val list = libs.value
    val lib = list?.firstOrNull { it.id == id }
    when {
        list == null && libs.error != null -> DesktopErrorPage(libs.error!!, libs::retry)
        // 库的种类要先问一次服务器；问到之前这一页先空着（很快）
        list == null -> Box(Modifier.fillMaxSize())
        lib?.kind == "boxsets" -> DesktopCollsScreen(today, id)
        else -> DesktopWall(lib ?: LibraryEntry(id, "片库", null, null), today)
    }
}

/**
 * 海报墙：页头（库名、部数、筛选 / 排序 / 视图）→ 状态页签 + 已选的小标签 → 推开的筛选条 → 墙（列数随窗口变）/ 列表视图。
 * 取数和筛选逻辑复用 `screens/wall/WallScreen.kt`（`loadWall`、`WallFilter`、`genreOptions`）。
 * 状态（`LocalPreset.state`）：`filter` `sort` `az` `list` `loading`；筛不到 = 预置一个没有的题材。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopWall(lib: LibraryEntry, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val nav = LocalWideNav.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val win = LocalWindowDp.current
    val preset = LocalPreset.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { chrome.spot = null }
    // 1.3.1：筛选 / 排序 / 评分线按片库记住（进详情返回、重开应用都还在）；测试预置优先于存着的
    val settings = LocalGugaSettings.current
    val saved = settings?.prefs?.value?.wall?.get(lib.id)
    var sort by remember(lib.id) { mutableStateOf(if (preset.state == "az") WallSort.Name else saved?.toSort(WallSort.Added) ?: WallSort.Added) }
    var filter by remember(lib.id) {
        mutableStateOf(
            if (preset.wallGenres.isNotEmpty() || preset.wallDecades.isNotEmpty()) WallFilter(genres = preset.wallGenres, decades = Decade.entries.filter { it.label in preset.wallDecades }.toSet())
            else saved?.toFilter() ?: WallFilter(),
        )
    }
    var minRating by remember(lib.id) { mutableStateOf(saved?.minRating) }
    LaunchedEffect(filter, sort, minRating, lib.id) {
        settings?.update { p -> p.copy(wall = p.wall + (lib.id to filter.toPref(sort, minRating))) }
    }
    var panel by remember { mutableStateOf(preset.state == "filter") }
    var sortOpen by remember { mutableStateOf(preset.state == "sort") }
    var listView by rememberSaveable { mutableStateOf(preset.state == "list") }
    val wall = rememberLoad(lib.id, sort) { loadWall(it, lib, sort) }
    val all = wall.value?.first.takeIf { preset.state != "loading" }
    val total = lib.titles ?: wall.value?.second
    val conditions = filter.count + if (minRating != null) 1 else 0
    // 右键菜单里删掉的先藏起来（ItemManage）
    val deletedCount = ItemManage.deleted.size
    val shown = remember(all, filter, minRating, sort, deletedCount) {
        all?.filter { t -> !ItemManage.isDeleted(t.id) && filter.accepts(t) && (minRating == null || (t.rating?.toFloatOrNull() ?: 0f) >= minRating!!) }?.let { if (sort == WallSort.Name) sortedByLetter(it) else it }
    }
    val list = rememberLazyListState()
    ReportLazyScroll(list)

    // 键盘：Ctrl F = 筛选条；Esc 先收下拉 / 筛选条
    DisposableEffect(Unit) {
        DesktopKeys.onFilter = {
            panel = !panel
            true
        }
        DesktopKeys.onEscape = {
            when {
                sortOpen -> {
                    sortOpen = false
                    true
                }
                panel -> {
                    panel = false
                    true
                }
                else -> false
            }
        }
        onDispose {
            DesktopKeys.onFilter = null
            DesktopKeys.onEscape = null
        }
    }
    LaunchedEffect(list.isScrollInProgress) { if (list.isScrollInProgress) sortOpen = false }

    val rem = dimens.rem.value
    val contentW = win.width.value - dimens.margin.value * 2
    val cols = DeskSpec.wallColumns(contentW, dimens.gutter.value, rem)
    val cardW = ((contentW - dimens.gutter.value * (cols - 1)) / cols).dp
    val az = sort == WallSort.Name && !listView
    val rows = remember(shown, cols) { shown?.chunked(cols).orEmpty() }
    // 墙之前有几个固定的条目（页头、页签、筛选条）：字母索引跳转时要加上
    val headItems = 3

    Box(Modifier.fillMaxSize().testTag("desktop-wall")) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            item("head") {
                Column {
                    Spacer(Modifier.height(Guga.rem(DesktopSpec.CONTENT_TOP)))
                    DeskPageHead(
                        lib.name,
                        libraryEnglish(lib.kind),
                        count = total?.toString(),
                        unit = if (total != null) "部" else null,
                        // 一次最多取 600 部：库更大时写明墙上先排了多少
                        note = all?.size?.takeIf { total != null && it < total }?.let { "先排出 $it 部" },
                    ) {
                        Kbd(DesktopCommand.Filter.hint(LocalDesktopHost.current.modifier) ?: "Ctrl F")
                        DeskChip("筛选", { panel = !panel }, strong = panel || conditions > 0, icon = GuguIcon.SlidersHorizontal, note = conditions.takeIf { it > 0 }?.toString(), height = DeskBrowseSpec.TOOL, tag = "wall-filter-key")
                        DeskChip(sort.label, { sortOpen = !sortOpen }, strong = sortOpen, icon = GuguIcon.ArrowUpDown, note = sortNote(sort), trailingIcon = GuguIcon.ChevronDown, height = DeskBrowseSpec.TOOL, tag = "wall-sort-key")
                        ViewToggle(listView) { listView = it }
                    }
                }
            }
            item("tabs") {
                Row(Modifier.fillMaxWidth().padding(horizontal = dimens.margin).padding(top = Guga.rem(2.4f), bottom = Guga.rem(1.8f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(2.2f))) {
                    TabWord("全部", filter.states.isEmpty()) { filter = filter.copy(states = emptySet()) }
                    for (s in WatchState.entries) TabWord(s.label, filter.states == setOf(s)) { filter = filter.copy(states = setOf(s)) }
                    Spacer(Modifier.weight(1f))
                    // 已选的条件 = 右上角可以点掉的小标签
                    Row(horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f)), verticalAlignment = Alignment.CenterVertically) {
                        for (g in filter.genres) DeskChip(g, { filter = filter.copy(genres = filter.genres - g) }, solid = true, trailingIcon = GuguIcon.X, tag = "wall-tag")
                        for (d in filter.decades) DeskChip(d.label, { filter = filter.copy(decades = filter.decades - d) }, solid = true, trailingIcon = GuguIcon.X, tag = "wall-tag")
                        minRating?.let { DeskChip("$it 分以上", { minRating = null }, solid = true, trailingIcon = GuguIcon.X, tag = "wall-tag") }
                        if (shown != null) BasicText("${shown.size} 部", Modifier.testTag("wall-tally"), style = Guga.note(rsp(1.4f), colors.faint), maxLines = 1)
                    }
                }
            }
            item("filter") {
                // 推开式：筛选条把墙往下推，不盖
                AnimatedVisibility(panel, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(Modifier.padding(horizontal = dimens.margin).padding(bottom = Guga.rem(2.4f)).testTag("wall-filter")) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                        Row(Modifier.fillMaxWidth().padding(top = Guga.rem(2.2f)), horizontalArrangement = Arrangement.spacedBy(dimens.gutter * 2)) {
                            FilterGroup("GENRE · 题材", genreOptions(all.orEmpty(), 16), Modifier.weight(1.2f), { it in filter.genres }) { g -> filter = filter.copy(genres = if (g in filter.genres) filter.genres - g else filter.genres + g) }
                            FilterGroup("DECADE · 年代", Decade.entries.map { it.label }, Modifier.weight(1f), { l -> filter.decades.any { it.label == l } }) { l ->
                                val d = Decade.entries.first { it.label == l }
                                filter = filter.copy(decades = if (d in filter.decades) filter.decades - d else filter.decades + d)
                            }
                            FilterGroup("RATING · 评分", DeskBrowseSpec.RATINGS.map { "$it 分以上" }, Modifier.weight(1f), { it == minRating?.let { r -> "$r 分以上" } }) { l ->
                                val r = l.substringBefore(' ').toInt()
                                minRating = if (minRating == r) null else r
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = Guga.rem(2.2f), bottom = Guga.rem(2f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.6f))) {
                            BasicText("筛到 ${shown?.size ?: 0} 部", style = Guga.song(rsp(1.3f), color = colors.dim), maxLines = 1)
                            DeskLink("清空筛选", {
                                filter = filter.copy(genres = emptySet(), decades = emptySet())
                                minRating = null
                            })
                            Spacer(Modifier.weight(1f))
                            DeskChip("收起", { panel = false }, solid = true, note = "Esc", height = DeskBrowseSpec.TOOL)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                    }
                }
            }
            when {
                wall.error != null && shown == null -> item("error") { ErrorView(wall.error!!, wall::retry) }
                shown == null -> {
                    // 骨架：形状 = 要出现的海报墙
                    if (wall.showSkeleton || preset.state == "loading") item("loading") { LoadingView(seed = today.day) }
                    items(2, key = { "sk$it" }) {
                        Row(Modifier.padding(horizontal = dimens.margin).padding(bottom = Guga.rem(DeskSpec.GRID_ROW_GAP)), horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                            repeat(cols) { DeskPosterCard(null, cardW) }
                        }
                    }
                }
                shown.isEmpty() && conditions > 0 -> item("none") {
                    FilterNoneView(conditions, {
                        filter = WallFilter()
                        minRating = null
                    })
                }
                shown.isEmpty() -> item("empty") { EmptyLibraryView() }
                listView -> {
                    item("list-head") { ListHead() }
                    items(shown.size, key = { shown[it].id }) { i -> ListRow(i + 1, shown[i]) }
                }
                else -> items(rows.size, key = { rows[it].first().id }) { r ->
                    Row(Modifier.padding(horizontal = dimens.margin).padding(bottom = Guga.rem(DeskSpec.GRID_ROW_GAP)), horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                        for (t in rows[r]) {
                            DeskPosterCard(t, cardW, number = if (az) indexLetter(t.name).toString() else null, meta = wallMeta(t), wrap = !az) { graph.open(it.id) }
                        }
                    }
                }
            }
            item("safe") { Spacer(Modifier.height(Guga.rem(8f))) }
        }
        // 排序的下拉：一张纸片，贴在排序键下面
        if (sortOpen) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = Guga.rem(DesktopSpec.CONTENT_TOP + DeskBrowseSpec.HEAD_TITLE + 0.2f), end = dimens.margin + Guga.rem(7.4f))
                    .width(Guga.rem(22f))
                    .background(slipColor())
                    .border(1.dp, colors.ink.copy(alpha = 0.7f))
                    .padding(vertical = Guga.rem(0.6f))
                    .testTag("wall-sort"),
            ) {
                for (s in WallSort.entries) SortRow(s, s == sort) {
                    sort = s
                    sortOpen = false
                }
            }
        }
        // 字母索引：贴右缘的一列；点一个字母跳到那一行
        if (az && shown != null && shown.isNotEmpty()) {
            val present = remember(shown) { shown.map { indexLetter(it.name) }.toSet() }
            val firstRow = (list.firstVisibleItemIndex - headItems).coerceIn(0, (rows.size - 1).coerceAtLeast(0))
            val current = rows.getOrNull(firstRow)?.firstOrNull()?.let { indexLetter(it.name) }
            Column(Modifier.align(Alignment.CenterEnd).padding(top = Guga.rem(DesktopSpec.CONTENT_TOP)).width(dimens.margin).testTag("wall-az"), horizontalAlignment = Alignment.CenterHorizontally) {
                for (c in LETTERS) {
                    val has = c in present
                    val on = c == current
                    val press = rememberPressState()
                    BasicText(
                        c.toString(),
                        Modifier.then(
                            if (has) Modifier.pressable(press, label = "跳到 $c", focusable = false) {
                                val row = rows.indexOfFirst { r -> r.any { indexLetter(it.name) == c } }
                                if (row >= 0) scope.launch { list.animateScrollToItem(row + headItems) }
                            } else Modifier,
                        ).padding(vertical = Guga.rem(0.5f)),
                        style = if (on) Guga.numeral(rsp(1.6f), colors.spot) else Guga.numeral(rsp(1.05f), if (has) colors.dim else colors.faint.copy(alpha = 0.18f)),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun sortNote(s: WallSort): String = when (s) {
    WallSort.Added, WallSort.Year -> "新 → 旧"
    WallSort.Name -> "A → Z"
    WallSort.Rating -> "高 → 低"
}

/** LazyColumn 的页面把滚动报给外壳（报头垫不垫纸色）；这类页面不是整幅铺满。 */
@Composable
private fun ReportLazyScroll(list: LazyListState) {
    val nav = LocalWideNav.current
    LaunchedEffect(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset) { nav.scrollPx = list.firstVisibleItemIndex * 10_000f + list.firstVisibleItemScrollOffset }
    DisposableEffect(Unit) {
        nav.bleed = false
        onDispose { nav.scrollPx = 0f }
    }
}

@Composable
private fun SortRow(s: WallSort, on: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Row(
        Modifier.fillMaxWidth().height(Guga.rem(3.8f)).hover(hover).drawBehind { if (hover.over || press.focused) drawRect(colors.ink.copy(alpha = 0.08f)) }
            .deskFocusRing(press).pressable(press, label = s.label, onClick = onClick).padding(horizontal = Guga.rem(1.6f)).testTag(if (on) "sort-on" else "sort-off"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f)),
    ) {
        Box(Modifier.size(Guga.rem(0.8f)).then(if (on) Modifier.background(colors.spot) else Modifier.border(1.dp, colors.dim)))
        BasicText(s.label, Modifier.weight(1f), style = Guga.song(rsp(1.4f), if (on) FontWeight.Bold else FontWeight.Normal, if (on) colors.ink else colors.dim), maxLines = 1)
        BasicText(sortNote(s), style = Guga.note(rsp(1.15f), colors.faint), maxLines = 1)
    }
}

/** 海报墙 / 列表两种视图的切换（一个线框里两个图标，当前的亮）。 */
@Composable
private fun ViewToggle(list: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Guga.colors
    Row(Modifier.height(Guga.rem(DeskBrowseSpec.TOOL)).border(1.dp, colors.line).padding(horizontal = Guga.rem(0.6f)).testTag(if (list) "wall-view-list" else "wall-view-grid"), verticalAlignment = Alignment.CenterVertically) {
        for ((isList, icon, label) in listOf(Triple(false, GuguIcon.LayoutGrid, "海报墙"), Triple(true, GuguIcon.List, "列表"))) {
            val press = rememberPressState()
            Box(Modifier.size(Guga.rem(2.4f)).deskFocusRing(press).pressGraphics(press).pressable(press, label = label) { onChange(isList) }, contentAlignment = Alignment.Center) {
                Icon(icon, if (list == isList) colors.ink else colors.faint, Guga.rem(1.4f))
            }
        }
    }
}

/** 筛选的一组：专色斜体的组名 + 一片可折行的线框选项；选中 = 米白实底。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(name: String, options: List<String>, modifier: Modifier, selected: (String) -> Boolean, onToggle: (String) -> Unit) {
    Column(modifier) {
        BasicText(name, style = Guga.note(rsp(1.05f), Guga.colors.spot, letterSpacing = 0.24.em), maxLines = 1)
        FlowRow(Modifier.fillMaxWidth().padding(top = Guga.rem(1.2f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.8f)), verticalArrangement = Arrangement.spacedBy(Guga.rem(0.8f))) {
            for (o in options) DeskChip(o, { onToggle(o) }, solid = selected(o), tag = "filter-opt")
        }
    }
}

/** 列表视图的表头和行：编号 / 片名 / 年份 / 题材 / 时长或集数 / 评分 / 分级 / 已看。 */
private val LIST_WEIGHTS = floatArrayOf(0.6f, 2.8f, 0.7f, 2.4f, 1.4f, 0.6f, 0.9f, 0.6f)

@Composable
private fun ListHead() {
    val colors = Guga.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Guga.dimens.margin).height(Guga.rem(3.6f))
            .drawBehind { drawLine(colors.ink, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((i, h) in listOf("NO.", "片名", "年份", "题材", "时长 / 集数", "评分", "分级", "已看").withIndex()) {
            BasicText(h, Modifier.weight(LIST_WEIGHTS[i]), style = Guga.note(rsp(1.05f), colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        }
    }
}

@Composable
private fun ListRow(number: Int, t: Title) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val seen = chrome.isPlayed(t.id, t.played)
    DisposableEffect(hover.over, t.id) {
        if (hover.over) DesktopKeys.hovered = t
        onDispose { if (DesktopKeys.hovered?.id == t.id) DesktopKeys.hovered = null }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Guga.dimens.margin)
            .height(Guga.rem(DeskBrowseSpec.LIST_ROW))
            .hover(hover)
            .onRightClick { DesktopContextMenu.open(t, it) }
            .drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .deskFocusRing(press)
            .pressable(press, label = t.name) { graph.open(t.id) }
            .testTag("wall-list-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(Rules.episodeNo(number), Modifier.weight(LIST_WEIGHTS[0]), style = Guga.numeral(rsp(1.3f), colors.faint), maxLines = 1)
        BasicText(t.name, Modifier.weight(LIST_WEIGHTS[1]).padding(end = Guga.rem(1.2f)), style = Guga.song(rsp(1.5f), FontWeight.Bold, if (hover.over) colors.spot else if (seen) colors.dim else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(t.year?.toString().orEmpty(), Modifier.weight(LIST_WEIGHTS[2]), style = Guga.numeral(rsp(1.3f), colors.faint), maxLines = 1)
        BasicText(t.genres.take(3).joinToString(" / "), Modifier.weight(LIST_WEIGHTS[3]).padding(end = Guga.rem(1.2f)), style = Guga.song(rsp(1.3f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(if (t.kind == Kind.Series) t.count?.let { "$it 集" }.orEmpty() else t.runtimeSec?.let { Rules.minutes(it) }.orEmpty(), Modifier.weight(LIST_WEIGHTS[4]), style = Guga.song(rsp(1.3f)), maxLines = 1)
        BasicText(t.rating.orEmpty(), Modifier.weight(LIST_WEIGHTS[5]), style = Guga.numeral(rsp(1.7f)), maxLines = 1)
        BasicText(t.officialRating ?: "—", Modifier.weight(LIST_WEIGHTS[6]), style = Guga.song(rsp(1.3f), color = if (t.officialRating != null) colors.ink else colors.faint), maxLines = 1)
        Box(Modifier.weight(LIST_WEIGHTS[7])) {
            val tick = rememberPressState()
            Box(Modifier.size(Guga.rem(2.4f)).pressGraphics(tick).deskFocusRing(tick).pressable(tick, label = if (seen) "标成没看" else "标记已看") { graph.togglePlayed(chrome, t.id, seen) }, contentAlignment = Alignment.Center) {
                ProofTick(seen, size = Guga.rem(1.4f))
            }
        }
    }
}

/* ------------------------------------------------------------------ 合集列表 */

/** 合集卡一行排几张：最小卡宽 20 rem。1440 → 6。纯函数，有单测。 */
fun collColumns(contentWidthDp: Float, gutterDp: Float, remDp: Float): Int =
    ((contentWidthDp + gutterDp) / (DeskBrowseSpec.COLL_MIN * remDp + gutterDp)).toInt().coerceAtLeast(1)

/** 合集列表（设计稿 `p=colls`）：全部合集，按名字排。[libraryId]：从合集库进来时是那个库的 id（取数不靠它：服务器上的合集不一定都挂在合集库下）。 */
@Composable
fun DesktopCollsScreen(today: Today, libraryId: String? = null) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val dimens = Guga.dimens
    val win = LocalWindowDp.current
    LaunchedEffect(Unit) { chrome.spot = null }
    val load = rememberLoad("sets") { loadSets(it, null) }
    val sets = load.value
    DesktopPage(Modifier.testTag("desktop-colls")) {
        DeskPageHead("合集", "SETS", count = sets?.size?.toString(), unit = if (sets != null) "套" else null)
        Spacer(Modifier.height(Guga.rem(3.2f)))
        val contentW = win.width.value - dimens.margin.value * 2
        val cols = collColumns(contentW, dimens.gutter.value, dimens.rem.value)
        val cardW = ((contentW - dimens.gutter.value * (cols - 1)) / cols).dp
        when {
            sets == null && load.error != null -> ErrorView(load.error!!, load::retry)
            sets == null -> {
                if (load.showSkeleton) LoadingView(seed = today.day)
                Row(Modifier.padding(horizontal = dimens.margin), horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) { repeat(cols) { DeskPosterCard(null, cardW) } }
            }
            sets.isEmpty() -> EmptyLibraryView()
            else -> for ((r, row) in sets.chunked(cols).withIndex()) {
                Row(Modifier.padding(horizontal = dimens.margin).padding(bottom = Guga.rem(DeskSpec.GRID_ROW_GAP)), horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                    for ((c, t) in row.withIndex()) {
                        DeskPosterCard(t, cardW, number = Rules.episodeNo(r * cols + c + 1), meta = t.count?.let { "共 $it 部" }, wrap = true) { graph.open(it.id) }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 搜索 */

/**
 * 搜索（设计稿 `p=search`：默认、`typing`、`results`、`long`、`none`）。一个大号宋体的输入行；下面左右两栏——
 * 正在打字：左边是联想（↑ ↓ 选、Enter 打开），右边是结果；按了 Enter：左边换成最佳匹配的大图版。命中的字上专色。
 * 匹配逻辑复用 `screens/search`（本地索引 + 拼音首字母；单集和人物问服务器）。「最近搜过」是编的，不排。
 */
@Composable
fun DesktopSearchScreen(q: String?, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val win = LocalWindowDp.current
    val preset = LocalPreset.current
    LaunchedEffect(Unit) { chrome.spot = null }
    // 1.3.1：返回保持位置——搜索词 / 是否按过 Enter / 联想选中项都记住（rememberSaveable，跟着返回栈条目走，PageStates）
    var query by rememberSaveable { mutableStateOf(preset.query ?: q.orEmpty()) }
    // 按过 Enter：左栏从联想换成最佳匹配
    var submitted by rememberSaveable { mutableStateOf(preset.state != "typing" && query.isNotEmpty()) }
    var picked by rememberSaveable { mutableIntStateOf(if (preset.state == "typing") 1 else 0) }
    val index = rememberLoad("search-index") { loadLibraryIndex(it) }
    val hits = remember(index.value, query) { index.value?.let { searchTitles(it, query) } }
    // 服务器那一路的结果走 rememberLoad（按词缓存）：返回时第一帧就是上次这个词的结果，页面够长，滚动位置才还原得了。
    var settled by rememberSaveable { mutableStateOf(query) }
    LaunchedEffect(query) { delay(220); settled = query }
    val remote = rememberLoad("search-remote", settled) { searchRemote(it, settled) }
    val remoteReady = remoteResultsReady(settled, query, remote.valueKeys)
    val remoteHits = remember(index.value, remote.value, remoteReady, query) {
        // 1.4.1：本地索引里的单集（「剧名 + 集号」直达的那一集在最前）+ 服务器兜底的，按 id 去重
        val r = remote.value.takeIf { remoteReady } ?: RemoteHits(emptyList(), emptyList())
        r.copy(episodes = com.gugaart.ui.screens.search.mergeEpisodes(com.gugaart.ui.screens.search.searchEpisodesLocal(index.value, query), r.episodes))
    }
    // 演员：本地人名单（名字 / 拼音缩写）+ 服务器的人物搜索；打名字正好是某位演员 → 演员和 ta 的作品排在前面，缩写照旧片子在前
    val peopleIndex = rememberLoad("search-people") { com.gugaart.ui.screens.search.loadPeopleIndex(it) }
    val localPeople = remember(peopleIndex.value, query) { com.gugaart.ui.screens.search.searchPeopleLocal(peopleIndex.value.orEmpty(), query) }
    val people = remember(localPeople, remoteHits) { com.gugaart.ui.screens.search.mergePeople(localPeople, remoteHits.people, query) }
    val star = people.firstOrNull()?.person?.takeIf { com.gugaart.ui.screens.search.peopleFirst(query, hits.orEmpty(), people) }
    val worksLoad = rememberLoad("search-works", star?.id) { c -> star?.let { com.gugaart.ui.screens.search.loadPersonWorks(c, it.id) }.orEmpty() }
    val works = worksLoad.value.orEmpty().takeIf { star != null }.orEmpty()
    val field = remember { FocusRequester() }
    LaunchedEffect(graph.searchFocusTick) { runCatching { field.requestFocus() } }
    val suggestions = remember(hits, people) { com.gugaart.ui.screens.search.suggestionsOf(hits.orEmpty(), people) }
    // 最近搜过 + 词云（按了 Enter / 打开了哪一条 / 点了词 = 记一笔）
    val recents = com.gugaart.ui.screens.search.rememberSearchRecents()
    val cloud = remember(index.value, recents.items) {
        com.gugaart.ui.screens.search.searchCloud(index.value?.titles.orEmpty(), seed = today.day, skip = recents.items.take(3).map { it.term }.toSet(), max = 14)
    }
    fun openHit(id: String) {
        recents.add(query)
        graph.open(id)
    }
    fun pick(term: String) {
        query = term
        submitted = true
        picked = 0
        recents.add(term)
    }
    DisposableEffect(Unit) {
        DesktopKeys.onEscape = {
            if (query.isNotEmpty()) {
                query = ""
                submitted = false
                true
            } else false
        }
        onDispose {
            DesktopKeys.onEscape = null
            DesktopKeys.typing = false
        }
    }

    DesktopPage(Modifier.testTag("desktop-search")) {
        // 输入行
        Row(
            Modifier
                .padding(horizontal = dimens.margin)
                .fillMaxWidth()
                .height(Guga.rem(6.6f))
                .drawBehind { drawLine(colors.ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.6f)),
        ) {
            Icon(GuguIcon.Search, colors.ink, Guga.rem(2.8f))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) BasicText("片名、拼音首字母、演员", style = Guga.song(rsp(3.6f), FontWeight.Black, colors.faint), maxLines = 1)
                BasicTextField(
                    query,
                    {
                        query = it
                        submitted = false
                        picked = 0
                    },
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(field)
                        .onFocusChanged { DesktopKeys.typing = it.isFocused }
                        .typingTarget()
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> {
                                    if (!submitted && suggestions.isNotEmpty()) picked = (picked + 1).coerceAtMost(suggestions.size - 1)
                                    true
                                }
                                Key.DirectionUp -> {
                                    if (!submitted) picked = (picked - 1).coerceAtLeast(0)
                                    true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    val hit = suggestions.getOrNull(picked)
                                    if (!submitted && hit != null && picked > 0) openHit(hit.id) else submitted = query.isNotBlank()
                                    if (submitted) recents.add(query)
                                    true
                                }
                                else -> false
                            }
                        }
                        .testTag("desktop-search-field"),
                    textStyle = Guga.song(rsp(3.6f), FontWeight.Black),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.spot),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        submitted = query.isNotBlank()
                        if (submitted) recents.add(query)
                    }),
                )
            }
            Kbd("Esc 清空")
            Kbd("Enter 搜索")
        }
        Spacer(Modifier.height(Guga.rem(3.6f)))
        val titles = hits.orEmpty()
        when {
            query.isBlank() -> DesktopSearchIdle(index.value?.titles.orEmpty(), peopleIndex.value.orEmpty(), recents.items, cloud, today.day, ::pick, recents::clear)
            index.value == null && index.error != null -> ErrorView(index.error!!, index::retry)
            index.value == null -> if (index.showSkeleton) LoadingView(seed = today.day)
            titles.isEmpty() && remoteHits.episodes.isEmpty() && people.isEmpty() -> SearchNoneView(query, {
                query = ""
                submitted = false
            })
            else -> {
                Row(Modifier.padding(horizontal = dimens.margin).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.gutter * 2)) {
                    // 左栏：联想 / 最佳匹配
                    Column(Modifier.weight(DeskBrowseSpec.SEARCH_LEFT)) {
                        val best = titles.firstOrNull()
                        if (star != null && (submitted || hits.orEmpty().isEmpty())) {
                            // 打的是演员的名字：最佳匹配是这位演员
                            DeskSection(null, "最佳匹配", "BEST MATCH")
                            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter), verticalAlignment = Alignment.Bottom) {
                                PersonCell(star, Guga.rem(15f), query) { openHit(star.id) }
                                Column(Modifier.padding(bottom = Guga.rem(3f))) {
                                    BasicText("演员", style = Guga.note(rsp(1.3f), colors.spot), maxLines = 1)
                                    BasicText(if (works.isNotEmpty()) "片库里 ${works.size} 部作品" else "片库里的人物", Modifier.padding(top = Guga.rem(0.4f)), style = Guga.song(rsp(1.6f), color = colors.dim), maxLines = 1)
                                }
                            }
                        } else if (submitted && best != null) {
                            DeskSection(null, "最佳匹配", "BEST MATCH")
                            BestMatch(best, { e -> openHit(e.id) }) { openHit(best.title.id) }
                        } else {
                            DeskSection(null, "联想", "SUGGEST") { SectionNote("↑ ↓ 选择 · Enter 打开") }
                            for ((i, h) in suggestions.withIndex()) SuggestRow(h, i == picked && picked > 0 || (preset.state == "typing" && i == picked)) { openHit(h.id) }
                        }
                    }
                    // 右栏：结果
                    Column(Modifier.weight(DeskBrowseSpec.SEARCH_RIGHT)) {
                        val all = titles.size + remoteHits.episodes.size + people.size
                        val rightW = (win.width.value - dimens.margin.value * 2 - dimens.gutter.value * 2) * DeskBrowseSpec.SEARCH_RIGHT / (DeskBrowseSpec.SEARCH_LEFT + DeskBrowseSpec.SEARCH_RIGHT)
                        val cols = if (win.width.value >= DeskSpec.XL_FROM) 8 else if (win.width.value <= 1280f) 4 else 5
                        val cardW = ((rightW - dimens.gutter.value * (cols - 1)) / cols).dp
                        @Composable
                        fun PeopleBlock(top: androidx.compose.ui.unit.Dp) {
                            DeskSection(null, "演员", "PEOPLE", Modifier.padding(top = top).testTag("desktop-search-people")) { SectionNote("${people.size} 位") }
                            Row(horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                                for (p in people.take(cols)) PersonCell(p.person, cardW, query) { openHit(p.person.id) }
                            }
                        }
                        @Composable
                        fun Grid(list: List<Title>, highlight: (Title) -> IntRange?) {
                            for (row in list.chunked(cols)) {
                                Row(Modifier.padding(bottom = Guga.rem(2.4f)), horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                                    for (t in row) DeskPosterCard(t, cardW, wrap = true, highlight = highlight(t)) { openHit(it.id) }
                                }
                            }
                        }
                        val rest = if (star != null) titles.filter { h -> works.none { it.id == h.title.id } } else titles
                        if (star != null) {
                            // 打的是演员的名字：演员在前，接着是 ta 在片库里的作品
                            PeopleBlock(0.dp)
                            if (works.isNotEmpty()) {
                                DeskSection(null, "${star.name}的作品", "WORKS", Modifier.padding(top = Guga.rem(3.2f)).testTag("desktop-search-works")) { SectionNote("${works.size} 部") }
                                Grid(works) { null }
                            }
                        }
                        if (rest.isNotEmpty() || star == null) {
                            DeskSection(null, "影片与剧集", "TITLES", Modifier.padding(top = if (star != null) Guga.rem(3.2f) else 0.dp)) { SectionNote("${rest.size} 部 · 共 $all 条结果") }
                            val ranges = rest.associate { it.title.id to it.range }
                            Grid(rest.map { it.title }) { ranges[it.id] }
                        }
                        if (remoteHits.episodes.isNotEmpty()) {
                            DeskSection(null, "单集", "EPISODES", Modifier.padding(top = Guga.rem(2.4f))) { SectionNote("${remoteHits.episodes.size} 集") }
                            for ((i, e) in remoteHits.episodes.take(8).withIndex()) {
                                DeskTocRow(Rules.episodeNo(i + 1), e.name, listOfNotNull(e.seriesName, e.episodeNo?.let { "第 $it 集" }).joinToString(" · "), e.art.thumb ?: e.art.backdrop, e.progress, { openHit(e.id) }, menu = e, highlight = com.gugaart.ui.search.SearchMatch.range(e.name, query))
                            }
                        }
                        // 缩写 / 片名：演员排在片子和单集后面
                        if (star == null && people.isNotEmpty()) PeopleBlock(Guga.rem(4f))
                    }
                }
            }
        }
    }
}

/** 最佳匹配：16:9 的图版（不是全窗），logo 压在图下沿，右下是一行斜体的栏目。 */
@Composable
private fun BestMatch(hit: TitleHit, onDirect: (Title) -> Unit, onOpen: () -> Unit) {
    val colors = Guga.colors
    val t = hit.title
    val press = rememberPressState()
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).deskFocusRing(press).pressGraphics(press).pressable(press, label = t.name, onClick = onOpen).onRightClick { DesktopContextMenu.open(t, it) }.border(1.dp, colors.line).testTag("search-best")) {
        CoverImage(t.art.backdrop ?: t.art.poster, Modifier.fillMaxSize(), Framing.of(t.id)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize()) }
        // 下沿压暗（画进图里的暗角，logo 和字读得清）
        Box(Modifier.matchParentSize().drawBehind { drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to androidx.compose.ui.graphics.Color.Transparent, 1f to colors.paper.copy(alpha = 0.86f))) })
        TitleLogo(t.name, t.art.logo, width = Guga.rem(22f), height = Guga.rem(8f), modifier = Modifier.align(Alignment.BottomStart).padding(Guga.rem(2f)), baseSize = rsp(3.2f), textOnly = LocalPreset.current.textTitles)
        Column(Modifier.align(Alignment.BottomEnd).padding(Guga.rem(2f)), horizontalAlignment = Alignment.End) {
            BasicText(t.kicker(com.gugaart.ui.screens.search.searchKindLabel(t.kind)).joinToString(" · "), style = Guga.note(rsp(1.3f), colors.dim), maxLines = 1)
            com.gugaart.ui.screens.search.originalHitText(hit, colors.spot)?.let { BasicText(it, Modifier.padding(top = Guga.rem(0.4f)).testTag("search-original"), style = Guga.song(rsp(1.3f), color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            com.gugaart.ui.screens.search.directTag(hit)?.let { tag -> com.gugaart.ui.screens.search.DirectTag(tag, Modifier.padding(top = Guga.rem(0.8f)).background(colors.inkWell), rsp(1.3f)) { onDirect(hit.direct!!) } }
            BasicText(listOfNotNull(if (t.kind == Kind.Series) t.count?.let { "全 $it 集" } else t.runtimeSec?.let { Rules.minutes(it) }, t.rating?.let { "$it 分" }).joinToString(" · "), Modifier.padding(top = Guga.rem(0.4f)), style = Guga.note(rsp(1.3f), colors.dim), maxLines = 1)
        }
    }
}

/** 联想的一行：名字（命中的字专色）+ 小字（类型 · 年份；原名命中时接「原名命中：…」；直达时「第 12 集」）；选中的一行垫一层淡底、行尾出 `Enter`。 */
@Composable
private fun SuggestRow(hit: com.gugaart.ui.screens.search.Suggestion, picked: Boolean, onOpen: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val lit = picked || hover.over
    Row(
        Modifier
            .fillMaxWidth()
            .height(Guga.rem(5.1f))
            .hover(hover)
            .drawBehind {
                if (lit) drawRect(colors.ink.copy(alpha = 0.07f))
                drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
            }
            .pressable(press, label = hit.name, focusable = false, onClick = onOpen)
            .padding(horizontal = Guga.rem(1f))
            .testTag(if (picked) "suggest-on" else "suggest-off"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            BasicText(highlighted(hit.name, hit.range, colors.spot), Modifier.weight(1f, fill = false), style = Guga.song(rsp(1.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(hit.note, Modifier.padding(start = Guga.rem(1.2f)), style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1)
            hit.title?.let { com.gugaart.ui.screens.search.originalHitText(it, colors.spot) }?.let {
                BasicText(it, Modifier.padding(start = Guga.rem(1.2f)).weight(1f, fill = false).testTag("search-original"), style = Guga.song(rsp(1.3f), color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (lit) Kbd("Enter")
    }
}

/** 演员的一格：彩色照片（没有照片 = 姓氏字块），名字里命中的字专色。 */
@Composable
private fun PersonCell(p: Person, width: Dp, query: String, onOpen: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Column(Modifier.width(width).hover(hover).testTag("search-person")) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).deskFocusRing(press).pressGraphics(press).pressable(press, label = p.name, onClick = onOpen).border(1.dp, colors.line)) {
            CoverImage(p.photo, Modifier.fillMaxSize(), Framing(0.5f, 0.2f)) { HatchPlate(Rules.monogram(p.name, person = true), Modifier.fillMaxSize()) }
        }
        BasicText(highlighted(p.name, com.gugaart.ui.search.SearchMatch.range(p.name, query), colors.spot), Modifier.padding(top = Guga.rem(1f)), style = Guga.song(rsp(1.5f), FontWeight.Bold, if (hover.over) colors.spot else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/* ------------------------------------------------------------------ 发现 */

/** 题材索引的一格：题材名 + 这个题材有几部（数自本地索引，真实数据）。纯函数，有单测。 */
fun genreCounts(titles: List<Title>): List<Pair<String, Int>> =
    titles.flatMap { it.genres }.groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key to it.value }

/**
 * 发现（设计稿 `p=discover`：整幅头条 / `idx` 索引 / `top` 榜单）。
 * 新到在前、高分在后（用户 2026-11-15）：头条 = 最新入库的那一部（「换一部」= 服务器随机取一部），下面依次是新到、高分榜（评分前 40 部里
 * 每次随机抽 10 部）、题材、年代。数据复用 `screens/discover` 的 `loadDiscover` / `loadLucky`，题材数自搜索的本地索引。
 * 题材 / 年代是**索引**（看片库的构成），点了不跳转：上游还没有「按题材 / 年代浏览」的路由。
 */
@Composable
fun DesktopDiscoverScreen(today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val win = LocalWindowDp.current
    val preset = LocalPreset.current
    val density = LocalDensity.current
    val load = rememberLoad("discover") { loadDiscover(it) }
    val index = rememberLoad("search-index") { loadLibraryIndex(it) }
    var dip by remember { mutableIntStateOf(0) }
    val lucky = rememberLoad("lucky", dip) { if (dip == 0) null else loadLucky(it) }
    val data = load.value
    val feature = lucky.value?.takeIf { dip > 0 } ?: data?.fresh?.firstOrNull() ?: data?.top?.firstOrNull()
    val featureLabel = if (lucky.value != null && dip > 0) "随手挑 · LUCKY DIP" else if (feature != null && feature.id == data?.fresh?.firstOrNull()?.id) "新到 · NEW ARRIVAL" else "高分 · TOP RATED"
    val poster = rememberImage(feature?.art?.poster, keepPrevious = true)
    LaunchedEffect(poster, feature?.id) { chrome.spot = poster?.spot() }
    val scroll = rememberScrollState()
    val anchors = remember { mutableStateMapOf<String, Float>() }
    val navPx = with(density) { (Guga.rem(DeskHomeSpec.NAV) + DeskHomeSpec.SECTION_NUDGE).toPx() }
    val stateAnchor = when (preset.state) {
        "idx" -> "genres"
        "top" -> "chart"
        else -> null
    }
    if (stateAnchor != null) {
        val target = anchors[stateAnchor]?.let { (it - navPx).toInt() }
        LaunchedEffect(target, scroll.maxValue) { if (target != null) scroll.scrollTo(target.coerceIn(0, scroll.maxValue)) }
    }
    fun Modifier.anchor(key: String): Modifier = onGloballyPositioned { anchors[key] = it.positionInRoot().y + scroll.value }

    if (data == null) {
        DesktopPage(Modifier.testTag("desktop-discover")) {
            DeskPageHead("发现", "DISCOVER")
            if (load.error != null) ErrorView(load.error!!, load::retry) else if (load.showSkeleton) LoadingView(seed = today.day)
        }
        return
    }
    DesktopPage(Modifier.testTag("desktop-discover"), bleed = feature != null, scroll = scroll, bottom = Guga.rem(12f)) {
        if (feature != null) DiscoverFeature(feature, featureLabel, win.height * DeskBrowseSpec.DISCOVER_BLEED, scroll.value.toFloat(), onLucky = { dip++ })
        else Spacer(Modifier.height(Guga.rem(DesktopSpec.CONTENT_TOP)))
        var no = 0
        // 新到
        if (data.fresh.isNotEmpty()) {
            Column(Modifier.padding(top = Guga.rem(4f)).anchor("fresh")) {
                DeskSection(Rules.episodeNo(++no), "新到", "NEW ARRIVALS", Modifier.padding(horizontal = dimens.margin)) { SectionNote("按入库时间") }
                // 头条是最新的那一部时，这一排从第二部起（不重复）
                PosterRow(data.fresh.filter { it.id != feature?.id }.ifEmpty { data.fresh }, key = "fresh")
            }
        }
        // 高分榜：两列（很宽的窗口三列）的编号行
        if (data.top.isNotEmpty()) {
            Column(Modifier.padding(top = dimens.sectionGap).padding(horizontal = dimens.margin).anchor("chart")) {
                DeskSection(Rules.episodeNo(++no), "高分榜", "TOP RATED") { SectionNote("评分前 $TOP_POOL 部里随机 $CHART_SIZE 部 · 每次不一样") }
                val cols = if (win.width.value >= DeskSpec.XL_FROM) 3 else 2
                val perCol = (data.top.size + cols - 1) / cols
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.gutter * 2)) {
                    for (c in 0 until cols) {
                        Column(Modifier.weight(1f)) {
                            for ((i, t) in data.top.drop(c * perCol).take(perCol).withIndex()) ChartRow(c * perCol + i + 1, t)
                        }
                    }
                }
            }
        }
        // Trakt 追更日历（连着 Trakt 才有）
        com.gugaart.ui.components.TraktCalendarSection(Modifier.padding(top = dimens.sectionGap).padding(horizontal = dimens.margin), titleSize = rsp(1.6f), timeWidth = Guga.rem(6f))
        // 题材索引
        val genres = remember(index.value) { genreCounts(index.value?.titles.orEmpty()) }
        if (genres.isNotEmpty()) {
            Column(Modifier.padding(top = dimens.sectionGap).padding(horizontal = dimens.margin).anchor("genres")) {
                DeskSection(Rules.episodeNo(++no), "题材", "GENRES") { SectionNote("${genres.size} 个题材 · ${index.value?.total ?: 0} 部") }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink))
                val cols = if (win.width.value >= DeskSpec.XL_FROM) 8 else 6
                for (row in genres.chunked(cols)) {
                    Row(Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }.padding(vertical = Guga.rem(1.8f))) {
                        for ((name, count) in row) {
                            Row(Modifier.weight(1f).padding(end = Guga.rem(2f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.2f))) {
                                BasicText(name, Modifier.alignByBaseline().weight(1f, fill = false), style = Guga.song(rsp(2.2f), FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(count.toString(), Modifier.alignByBaseline(), style = Guga.numeral(rsp(1.2f), colors.faint), maxLines = 1)
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        // 年代
        if (data.decades.isNotEmpty()) {
            Column(Modifier.padding(top = dimens.sectionGap).padding(horizontal = dimens.margin).anchor("decades")) {
                DeskSection(Rules.episodeNo(++no), "年代", "DECADES") { SectionNote("按上映年份") }
                val max = data.decades.maxOf { it.count }.coerceAtLeast(1)
                Row(Modifier.fillMaxWidth().height(Guga.rem(16f)), horizontalArrangement = Arrangement.spacedBy(dimens.gutter), verticalAlignment = Alignment.Bottom) {
                    for (d in data.decades) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            BasicText(d.count.toString(), style = Guga.numeral(rsp(1.6f)), maxLines = 1)
                            Box(Modifier.padding(top = Guga.rem(0.6f)).fillMaxWidth().height(Guga.rem(10f) * (d.count.toFloat() / max)).background(colors.spot.copy(alpha = 0.85f)))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.ink))
                            BasicText(d.label, Modifier.padding(top = Guga.rem(0.8f)), style = Guga.numeral(rsp(1.2f), colors.dim), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** 发现的头条（[label]：新到 / 随手翻 / 高分）：剧照从窗口顶端铺下来，栏目 / logo / 导语 / 按钮接在渐隐处，右下是大号斜体评分。 */
@Composable
private fun DiscoverFeature(t: Title, label: String, height: Dp, scrollPx: Float, onLucky: () -> Unit) {
    val graph = LocalGraph.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    FullBleed(t.art, height, Modifier.testTag("discover-feature"), framing = Framing.of(t.id), caption = "${label.substringBefore(" · ")} · 剧照 ·《${t.name}》", scrollPx = scrollPx) {
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = dimens.margin).padding(bottom = Guga.rem(1f)), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                BasicText(
                    (listOf(label) + t.kicker(if (t.kind == Kind.Series) "剧集" else "电影")).joinToString("　"),
                    Modifier.background(colors.inkWell).padding(horizontal = Guga.rem(0.8f), vertical = Guga.rem(0.4f)),
                    style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TitleLogo(t.name, t.art.logo, width = Guga.rem(34f), height = Guga.rem(11f), modifier = Modifier.padding(top = Guga.rem(1.4f), bottom = Guga.rem(1.6f)), textOnly = LocalPreset.current.textTitles)
                t.lead?.let { BasicText(it, Modifier.width(Guga.rem(62f)), style = Guga.song(rsp(1.7f), lineHeight = rsp(3f)), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(Modifier.padding(top = Guga.rem(2f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.8f))) {
                    // 剧集从详情页挑一集；电影直接播
                    if (t.kind == Kind.Movie) DeskButton("播放", { graph.play(t.id, t.positionSec?.times(1000)) }, note = t.runtimeSec?.let { Rules.minutes(it) })
                    else DeskButton("去看这一部", { graph.open(t.id) }, icon = null, note = t.count?.let { "全 $it 集" })
                    DeskLink("详情", { graph.open(t.id) })
                    DeskLink("换一部", onLucky, icon = GuguIcon.RotateCcw)
                }
            }
            t.rating?.let {
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(it, style = Guga.numeral(rsp(8.4f)).copy(lineHeight = rsp(8.4f)), maxLines = 1)
                    BasicText(listOfNotNull("评分", t.officialRating).joinToString(" · "), style = Guga.song(rsp(1.3f), color = colors.dim).copy(textAlign = TextAlign.End), maxLines = 1)
                }
            }
        }
    }
}

/** 榜单的一行：大号斜体名次 + 小海报 + 片名 / 副行 + 评分。悬停片名变专色；右键 = 右键菜单。 */
@Composable
private fun ChartRow(rank: Int, t: Title) {
    val graph = LocalGraph.current
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    Row(
        Modifier
            .fillMaxWidth()
            .hover(hover)
            .onRightClick { DesktopContextMenu.open(t, it) }
            .drawBehind { drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx()) }
            .deskFocusRing(press)
            .pressGraphics(press)
            .pressable(press, label = t.name) { graph.open(t.id) }
            .padding(vertical = Guga.rem(1.4f))
            .testTag("discover-chart-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(Rules.episodeNo(rank), Modifier.width(Guga.rem(6f)), style = Guga.numeral(rsp(3.6f), if (rank <= 3) colors.ink else colors.faint), maxLines = 1)
        Box(Modifier.width(Guga.rem(5.6f)).aspectRatio(2f / 3f).border(1.dp, colors.line)) {
            CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), textSize = rsp(1.4f)) }
        }
        Column(Modifier.weight(1f).padding(horizontal = Guga.rem(1.8f))) {
            BasicText(t.name, style = Guga.song(rsp(1.8f), FontWeight.Bold, if (hover.over) colors.spot else colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(chartLine(t), Modifier.padding(top = Guga.rem(0.4f)), style = Guga.note(rsp(1.25f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        t.rating?.let { BasicText(it, style = Guga.numeral(rsp(2.8f)), maxLines = 1) }
    }
}

/**
 * 电脑搜索页什么都没输入时（设计稿 `p=search` 默认态）：左栏「最近搜过」（词 · 现在能搜到几条，右上「清空」）+ 她举笔；
 * 右栏词云「随便搜搜」+「按题材翻」（片库里部数最多的 12 个题材）。点哪个都是搜它。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopSearchIdle(titles: List<Title>, peopleIndex: List<com.gugaart.ui.data.Person>, recents: List<com.gugaart.ui.screens.search.RecentSearch>, cloud: List<com.gugaart.ui.screens.search.CloudWord>, seed: Int, onPick: (String) -> Unit, onClear: () -> Unit) {
    val colors = Guga.colors
    val dimens = Guga.dimens
    Row(Modifier.padding(horizontal = dimens.margin).fillMaxWidth().testTag("desktop-search-idle"), horizontalArrangement = Arrangement.spacedBy(dimens.gutter * 2)) {
        Column(Modifier.weight(DeskBrowseSpec.SEARCH_LEFT)) {
            if (recents.isNotEmpty()) {
                DeskSection(null, "最近搜过", "RECENT") { SectionNote("清空", onClear) }
                for ((i, r) in recents.take(8).withIndex()) {
                    val press = rememberPressState()
                    val hover = rememberHover()
                    val count = remember(titles, peopleIndex, r.term) { searchTitles(titles, r.term).size + com.gugaart.ui.screens.search.searchPeopleLocal(peopleIndex, r.term, 50).size }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(Guga.rem(4.6f))
                            .hover(hover)
                            .drawBehind {
                                if (hover.over) drawRect(colors.ink.copy(alpha = 0.07f))
                                drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
                            }
                            .pressable(press, label = r.term, focusable = false) { onPick(r.term) }
                            .padding(horizontal = Guga.rem(1f))
                            .testTag("desktop-recent-$i"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(r.term, Modifier.weight(1f), style = Guga.song(rsp(1.8f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (titles.isNotEmpty()) BasicText(if (count > 0) "$count 条" else "没有", style = Guga.note(rsp(1.25f), colors.faint), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(Guga.rem(2.4f)))
            }
            SearchIdleView(seed = seed)
        }
        Column(Modifier.weight(DeskBrowseSpec.SEARCH_RIGHT)) {
            if (cloud.isNotEmpty()) {
                DeskSection(null, "随便搜搜", "TRY")
                com.gugaart.ui.screens.search.SearchCloud(cloud, onPick, Triple(rsp(1.5f), rsp(2.2f), rsp(3.0f)), gap = Guga.rem(1.2f))
                Spacer(Modifier.height(Guga.rem(3.2f)))
            }
            val genres = remember(titles) { genreCounts(titles).filter { com.gugaart.ui.screens.search.isRealGenre(it.first) }.take(12) }
            if (genres.isNotEmpty()) {
                DeskSection(null, "按题材翻", "BROWSE")
                for (row in genres.chunked(4)) {
                    Row(Modifier.fillMaxWidth()) {
                        for ((name, n) in row) {
                            val press = rememberPressState()
                            val hover = rememberHover()
                            Row(
                                Modifier
                                    .weight(1f)
                                    .height(Guga.rem(4.4f))
                                    .hover(hover)
                                    .drawBehind {
                                        if (hover.over) drawRect(colors.ink.copy(alpha = 0.07f))
                                        drawLine(colors.line, Offset(0f, size.height - 0.5.dp.toPx()), Offset(size.width, size.height - 0.5.dp.toPx()), 1.dp.toPx())
                                    }
                                    .pressable(press, label = name, focusable = false) { onPick(name) }
                                    .padding(horizontal = Guga.rem(0.8f)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BasicText(name, Modifier.weight(1f), style = Guga.song(rsp(1.7f), FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText("$n", style = Guga.numeral(rsp(1.3f), colors.spot), maxLines = 1)
                            }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
