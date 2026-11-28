package com.gugaart.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.ItemManage
import com.gugaart.ui.data.LibraryEntry
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.loadLibraries
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.sets.loadSets
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
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.ImageType
import com.gugugaga.core.ImageUrlOptions
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.ds.focus.FocusZoneScope
import com.gugugaga.ds.focus.ZoneRestore
import com.gugugaga.ds.focus.focusZone
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.physics.PressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 片库 / 海报墙 / 合集列表的版式数（设计稿 `tv/css/pages.css`）。 */
object TvBrowseSpec {
    /** 海报墙：7 列（筛选栏打开时 5 列），卡宽 214，列距 32，行距 36；墙顶离页眉 34；焦点所在的一排吸到这里。 */
    const val COLUMNS = 7
    const val COLUMNS_FILTER = 5
    val WALL_TOP = 34.dp

    /** 墙顶「筛选 · 排序」按钮离页眉的距离（P1-8）。 */
    val FILTER_BUTTON_TOP = 26.dp
    val WALL_TAIL = 700.dp
    /** 筛选栏：x 1408 起、宽 416。 */
    val FILTER_W = 416.dp
    /** 媒体库列表：左页宽 880、一行高 158；右页 x 1100、宽 724，题图 724 × 407，四张海报 160 × 240。 */
    val LIBS_W = 880.dp
    val LIB_ROW = 158.dp
    val LIBS_RIGHT_X = 1100.dp
    val FRAME_W = 724.dp
    val FRAME_H = 407.dp
    /** 米白焦点块上的编号：深朱红。 */
    val NUMBER_ON_INK = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK

    /** 墙宽：n 列。 */
    fun wallWidth(columns: Int) = TvSpec.POSTER * columns + TvSpec.POSTER_GAP * (columns - 1)
}

/* ------------------------------------------------------------------ 媒体库列表 */

@Immutable
private data class TvLibs(val libraries: List<LibraryEntry>, val latest: Map<String, List<Title>>)

private val LATEST_FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "CommunityRating"))

/**
 * 媒体库列表（`p=libs`）：媒体库排成一页带编号的目录；上下换库，右页跟着换（那一库的题图和最新四张海报；**右页只是预览，不接焦点**）；
 * 确定 = 进海报墙。数据：上游 `getViewList` + 每库 `getLatest`（合集库 = 前四套合集），只读。
 */
@Composable
fun TvLibsScreen(today: Today) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val session by g.session.session.collectAsState()
    val chrome = LocalChrome.current
    LaunchedEffect(Unit) { chrome.spot = null }
    val load = rememberLoad("tv-libs") { c ->
        val libs = loadLibraries(c)
        TvLibs(libs, libs.associate { lib ->
            lib.id to try {
                if (lib.kind == "boxsets") loadSets(c, lib.id).take(4) else c.getLatest(parentId = lib.id, limit = 4, o = LATEST_FIELDS).map { c.titleOf(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
        })
    }
    val data = load.value
    var at by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().testTag("tv-libs")) {
        TvPageHeader("片库", "LIBRARY", today, trailing = data?.let { d -> "${d.libraries.size} 个媒体库" + d.libraries.mapNotNull { it.titles }.takeIf { it.isNotEmpty() }?.let { " · ${it.sum()} 部" }.orEmpty() })
        when {
            data == null && load.error != null -> TvStateView("tv-libs-error", "摊手", load.error!!, null, action = "再试一次", onAction = load::retry)
            data == null -> if (load.showSkeleton) TvStateView("tv-libs-loading", "摆稿纸", "正在排片", MascotLines.pick("wall.loading", hour = LocalHour.current, seed = today.day))
            data.libraries.isEmpty() -> TvStateView("tv-libs-empty", "捧白纸", "这台服务器上还没有媒体库", null)
            else -> Box(Modifier.fillMaxSize()) {
                Column(Modifier.padding(start = TvSpec.LEFT, top = 26.dp, bottom = TvSpec.BOTTOM).width(TvBrowseSpec.LIBS_W).verticalScroll(rememberScrollState())) {
                    for ((i, lib) in data.libraries.withIndex()) {
                        TvFocusable(
                            onClick = { g.nav.navigate(Route.Library(lib.id)) },
                            modifier = Modifier.fillMaxWidth().height(TvBrowseSpec.LIB_ROW),
                            kind = TvFocusKind.Row,
                            key = "lib-${lib.id}",
                            default = i == 0,
                            label = lib.name,
                            onFocus = { at = lib.id },
                        ) { focused ->
                            val ink = if (focused) colors.onInk else colors.ink
                            Row(
                                Modifier.fillMaxSize().drawBehind { if (!focused) drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(horizontal = 30.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(30.dp),
                            ) {
                                BasicText(if (i < 9) "0${i + 1}" else "${i + 1}", Modifier.width(70.dp), style = Guga.numeral(44.sp, if (focused) TvBrowseSpec.NUMBER_ON_INK else colors.spot), maxLines = 1)
                                BasicText(lib.name, Modifier.weight(1f), style = Guga.song(68.sp, FontWeight.Black, ink, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (lib.titles != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        BasicText(lib.titles.toString(), style = Guga.numeral(44.sp, ink), maxLines = 1)
                                        BasicText(if (lib.kind == "boxsets") "套" else "部", style = Guga.song(27.sp, color = ink.copy(alpha = 0.7f)), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                // 右页：焦点那一库的题图 + 最新入库的四张（预览，不接焦点）
                val lib = data.libraries.firstOrNull { it.id == at } ?: data.libraries.first()
                Column(Modifier.padding(start = TvBrowseSpec.LIBS_RIGHT_X, top = 36.dp).width(TvBrowseSpec.FRAME_W).testTag("tv-libs-preview")) {
                    Box(Modifier.size(TvBrowseSpec.FRAME_W, TvBrowseSpec.FRAME_H).background(Color(0xFF1C1914)).border(1.dp, colors.line)) {
                        CoverImage(session?.client?.imageUrl(lib.id, ImageType.Primary, ImageUrlOptions(maxWidth = 1200)), Modifier.fillMaxSize(), Framing(0.5f, 0.3f))
                    }
                    val four = data.latest[lib.id].orEmpty()
                    if (four.isNotEmpty()) {
                        Row(
                            Modifier.padding(top = 34.dp, bottom = 18.dp).fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            BasicText("最新入库", Modifier.alignByBaseline(), style = Guga.song(30.sp, FontWeight.Black), maxLines = 1)
                            BasicText(if (lib.kind == "boxsets") "这一库的合集" else "最近添加的${four.size.let { if (it == 4) "四" else it.toString() }}部", Modifier.alignByBaseline(), style = Guga.note(22.sp, colors.dim), maxLines = 1)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            for (t in four) Column(Modifier.width(160.dp)) {
                                Box(Modifier.size(160.dp, 240.dp).background(Color(0xFF1C1914)).border(1.dp, colors.line)) { CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) }
                                BasicText(t.name, Modifier.padding(top = 10.dp), style = Guga.song(22.sp, FontWeight.SemiBold, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 海报墙 */

/** 一面海报墙：[columns] 列，四个方向走，焦点所在的一排吸到顶；长按确定 = 这张海报的菜单。最左再按左 = 进导航（外壳做）。 */
@Composable
private fun TvPosterWall(titles: List<Title>, columns: Int, keyPrefix: String, sub: ((Title) -> String?)? = null) {
    val g = LocalGraph.current
    val sess by g.session.session.collectAsState()
    val acct = sess?.let { "${it.serverId}/${it.userId}" }
    remember(acct, titles) { TvTitlePeekCache.putAll(acct, titles) }
    val visible = remember(titles, ItemManage.deleted.size) { titles.filterNot { ItemManage.isDeleted(it.id) } }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val boxSize = remember(density) { with(density) { androidx.compose.ui.unit.IntSize(TvKitSpec.POSTER_W.roundToPx(), (TvKitSpec.POSTER_W * 1.5f).roundToPx()) } }
    val gridState = rememberLazyGridState()
    // 1.6 B2：按住方向键翻墙时，下一行（再是上一行）的海报先解码、先上传——一个协程按顺序做，可见的行变了就取消重来（不是每张卡各起一个）
    val images = g.images
    LaunchedEffect(gridState, visible, columns, boxSize) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.let { v -> if (v.isEmpty()) null else (v.first().index / columns) to (v.last().index / columns) } }
            .distinctUntilChanged()
            .collectLatest { rows ->
                if (rows == null) return@collectLatest
                for (i in wallPrefetchOrder(rows.first, rows.second, columns, visible.size)) {
                    val u = visible[i].art.poster ?: continue
                    images.load(com.gugaart.ui.data.ImageSizing.sized(u, boxSize.width, boxSize.height), com.gugugaga.app.data.ImagePriority.Prefetch)?.prepareToDraw()
                }
            }
    }
    // 1.6 B2：墙在滚的时候（按住方向键）不报给无障碍服务，停下来再报。开着无障碍服务的电视（海信开了 4 个）上，Compose 每滚一帧都要把
    // 整棵语义树重算一遍再按几何排序（真机 simpleperf：主线程 27 %）；滚动中不报，按住下键 P90 34 → 27 ms（探针 `wallsem off` 量的）。停下来焦点卡照常报。
    val scrolling by remember(gridState) { androidx.compose.runtime.derivedStateOf { gridState.isScrollInProgress } }
    TvSnap(TvBrowseSpec.WALL_TOP) {
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            Modifier.fillMaxSize().testTag("tv-wall").then(if (TvFlipProbe.wallSemantics && !scrolling) Modifier else Modifier.clearAndSetSemantics { }),
            state = gridState,
            contentPadding = PaddingValues(start = TvSpec.LEFT, end = 1920.dp - TvSpec.LEFT - TvBrowseSpec.wallWidth(columns), top = TvBrowseSpec.WALL_TOP, bottom = TvBrowseSpec.WALL_TAIL),
            horizontalArrangement = Arrangement.spacedBy(TvSpec.POSTER_GAP),
            verticalArrangement = Arrangement.spacedBy(TvSpec.ROW_GAP),
        ) {
            // 长按菜单里删掉的先藏起来（ItemManage）
            itemsIndexed(visible, key = { _, t -> t.id }, contentType = { _, _ -> "poster" }) { i, t ->
                val openClick = remember(t.id, g) { { g.open(t.id) } }
                val longMenu = remember(t, t.id) { { TvLongPressMenu.open(t) } }
                if (sub != null) TvPosterCard(t, openClick, key = "$keyPrefix${t.id}", default = i == 0, onLongClick = longMenu, sub = sub(t), knownSize = boxSize)
                else TvPosterCard(t, openClick, key = "$keyPrefix${t.id}", default = i == 0, onLongClick = longMenu, knownSize = boxSize)
            }
    }
    }
}

/** 加载中的墙：14 个虚线空格（斜体编号）+ 下面她摆稿纸的一栏。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvWallLoading(seed: Int) {
    val colors = Guga.colors
    Box(Modifier.fillMaxSize().testTag("tv-wall-loading")) {
        FlowRow(Modifier.padding(start = TvSpec.LEFT, top = 40.dp).width(TvBrowseSpec.wallWidth(TvBrowseSpec.COLUMNS) + 2.dp), horizontalArrangement = Arrangement.spacedBy(TvSpec.POSTER_GAP), verticalArrangement = Arrangement.spacedBy(TvSpec.ROW_GAP)) {
            repeat(14) { i ->
                Box(
                    Modifier.size(TvSpec.POSTER, TvSpec.POSTER * 1.5f).drawBehind { drawRect(colors.line, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))) },
                    contentAlignment = Alignment.Center,
                ) { BasicText(if (i < 9) "0${i + 1}" else "${i + 1}", style = Guga.numeral(44.sp, colors.line), maxLines = 1) }
            }
        }
        Row(
            Modifier.padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 766.dp).fillMaxWidth().background(colors.paper).drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 20.dp).height(200.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            MascotPoseNamed("摆稿纸", 150.dp)
            BasicText(MascotLines.pick("wall.loading", hour = LocalHour.current, seed = seed) ?: "正在排片……", Modifier.padding(top = 50.dp), style = Guga.song(28.sp, color = colors.dim), maxLines = 1)
        }
    }
}

/** 一个媒体库 = 海报墙（`p=lib`：7 列，筛选栏打开时 5 列）；合集库 = 合集列表。 */
@Composable
fun TvLibraryScreen(id: String, today: Today) {
    val libs = rememberLoad("tv-library-kind") { loadLibraries(it) }
    val list = libs.value
    val lib = list?.firstOrNull { it.id == id }
    when {
        list == null && libs.error == null -> Column(Modifier.fillMaxSize()) { TvPageHeader("片库", "LIBRARY", today) }
        lib?.kind == "boxsets" -> TvCollsScreen(today, id)
        else -> TvWallScreen(lib ?: LibraryEntry(id, "片库", null, null), today)
    }
}

/**
 * 海报墙。墙顶一个能选中的「筛选 · 排序」按钮（P1-8：不少遥控器没有菜单键；第一排按上键就到），菜单键保留作快捷方式：
 * 右栏打开，墙挤成 5 列（不盖海报），焦点进右栏（关着门的焦点区：上下左右换格子、确定选中、
 * 返回或再按菜单键收起，焦点还给打开它的那一格——墙上那张卡或这个按钮）。筛选 / 排序的规则复用手机的 `screens/wall`（`WallFilter` `WallSort` `genreOptions` `loadWall`）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TvWallScreen(lib: LibraryEntry, today: Today) {
    val g = LocalGraph.current
    val tv = LocalWideNav.current.tv
    val colors = Guga.colors
    val preset = LocalPreset.current
    val chrome = LocalChrome.current
    LaunchedEffect(Unit) { chrome.spot = null }
    // 1.3.1：筛选 / 排序按片库记住（返回、重开都还在）；测试预置优先于存着的
    val settings = LocalGugaSettings.current
    val saved = settings?.prefs?.value?.wall?.get(lib.id)
    var sort by remember(lib.id) { mutableStateOf(saved?.toSort(WallSort.Added) ?: WallSort.Added) }
    var filter by remember(lib.id) {
        mutableStateOf(
            if (preset.wallGenres.isNotEmpty() || preset.wallDecades.isNotEmpty()) WallFilter(genres = preset.wallGenres, decades = Decade.entries.filter { it.label in preset.wallDecades }.toSet())
            else saved?.toFilter() ?: WallFilter(),
        )
    }
    LaunchedEffect(filter, sort, lib.id) {
        settings?.update { p -> p.copy(wall = p.wall + (lib.id to filter.toPref(sort))) }
    }
    var panel by remember { mutableStateOf(preset.state == "filter" || preset.wallPanel != null) }
    var from by remember { mutableStateOf<PressState?>(null) }
    val load = rememberLoad("tv-wall", lib.id, sort) { loadWall(it, lib, sort) }
    val all = load.value?.first
    val total = load.value?.second
    val shown = remember(all, filter) { all?.filter(filter::accepts) }
    val genres = remember(all) { genreOptions(all.orEmpty(), 6) }
    val loading = preset.state == "loading" || (all == null && load.error == null)

    TvMenuKey {
        if (!panel) from = g.focus.lastFocusedPress
        panel = !panel
        true
    }
    TvBack(panel) { panel = false }
    // 打开：焦点进右栏（现在的排序那一格）；收起：还给墙上那张卡（它被筛掉了就按兜底的规矩找）
    LaunchedEffect(panel) {
        if (panel) {
            repeat(30) {
                if (g.focus.request("tv-filter/sort-${sort.name}")) return@LaunchedEffect
                delay(16)
            }
        } else if (g.focus.lastFocusedKey?.startsWith("tv-filter/") == true) {
            val back = from
            from = null
            delay(40)
            if (back == null || !runCatching { back.focusRequester.requestFocus() }.getOrDefault(false)) tvRescueFocus(g, tv)
        }
    }

    Column(Modifier.fillMaxSize().testTag("tv-library")) {
        TvPageHeader(
            lib.name,
            when (lib.kind) { "movies" -> "MOVIES"; "tvshows" -> "SERIES"; else -> "LIBRARY" },
            today,
            trailing = when {
                total == null -> null
                filter.count > 0 -> "筛出 ${shown?.size ?: 0} 部 · 共 $total 部"
                else -> "$total 部 · 按${sort.label} · 全部题材"
            },
        )
        // 墙顶的「筛选 · 排序」（P1-8）：能选中，第一排按上键就到；确定 = 打开右栏（和菜单键同一件事）
        if (all != null) {
            TvButton(
                if (filter.count > 0) "筛选 · 排序 · ${filter.count}" else "筛选 · 排序",
                {
                    from = g.focus.lastFocusedPress
                    panel = true
                },
                Modifier.padding(start = TvSpec.LEFT, top = TvBrowseSpec.FILTER_BUTTON_TOP).testTag("tv-wall-filter-button"),
                icon = GuguIcon.SlidersHorizontal,
                key = "tv-wall-filter",
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                loading -> if (preset.state == "loading" || load.showSkeleton) TvWallLoading(today.day)
                all == null -> TvStateView("tv-wall-error", "摊手", load.error!!, null, action = "再试一次", onAction = load::retry)
                all.isEmpty() -> TvStateView("tv-wall-empty", "捧白纸", "这个库还是空的", null)
                shown.isNullOrEmpty() -> TvStateView(
                    "tv-wall-none", "摊手",
                    "「${(listOf(sort.label) + filter.genres + filter.decades.map { it.label } + filter.states.map { it.label }).joinToString(" · ")}」一部都没有",
                    MascotLines.pick("wall.none", hour = LocalHour.current, seed = today.day),
                    Modifier.padding(top = 200.dp).padding(end = if (panel) TvBrowseSpec.FILTER_W + 80.dp else 0.dp),
                    action = if (panel) null else "清除筛选", onAction = { filter = WallFilter() },
                )
                else -> TvPosterWall(shown, if (panel) TvBrowseSpec.COLUMNS_FILTER else TvBrowseSpec.COLUMNS, "w-")
            }
            if (panel) {
                TvModal()
                FocusZoneScope("tv-filter") {
                    Column(
                        Modifier.align(Alignment.TopEnd).padding(end = TvSpec.RIGHT, top = 26.dp).width(TvBrowseSpec.FILTER_W)
                            .focusZone("tv-filter", ZoneRestore.Nearest, trap = true, trapActive = { panel }).testTag("tv-filter"),
                    ) {
                        Row(Modifier.height(TvKitSpec.SECTION_H), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            BasicText("¶", style = Guga.numeral(36.sp, colors.spot), maxLines = 1)
                            BasicText("筛选", style = Guga.song(Guga.type.section, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
                            BasicText("FILTER & SORT", style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                        }
                        @Composable
                        fun group(name: String, chips: @Composable () -> Unit) {
                            BasicText(name, Modifier.padding(top = 16.dp, bottom = 10.dp), style = Guga.song(22.sp, FontWeight.SemiBold, colors.dim, letterSpacing = 0.2.em), maxLines = 1)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { chips() }
                        }
                        group("排序") {
                            for (s in WallSort.entries) TvChip(s.label, s == sort, { sort = s }, key = "sort-${s.name}", trailing = if (s == sort) (if (s.descending) "↓" else "↑") else null)
                        }
                        if (genres.isNotEmpty()) group("题材") {
                            TvChip("全部", filter.genres.isEmpty(), { filter = filter.copy(genres = emptySet()) }, key = "genre-all")
                            for (x in genres) TvChip(x, x in filter.genres, { filter = filter.copy(genres = if (x in filter.genres) filter.genres - x else filter.genres + x) }, key = "genre-$x")
                        }
                        group("年代") {
                            TvChip("全部", filter.decades.isEmpty(), { filter = filter.copy(decades = emptySet()) }, key = "decade-all")
                            for (d in Decade.entries) TvChip(d.label, d in filter.decades, { filter = filter.copy(decades = if (d in filter.decades) filter.decades - d else filter.decades + d) }, key = "decade-${d.name}")
                        }
                        group("状态") {
                            TvChip("全部", filter.states.isEmpty(), { filter = filter.copy(states = emptySet()) }, key = "state-all")
                            for (w in WatchState.entries) TvChip(w.label, w in filter.states, { filter = filter.copy(states = if (w in filter.states) filter.states - w else filter.states + w) }, key = "state-${w.name}")
                        }
                        Box(Modifier.padding(top = 24.dp).fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(top = 22.dp)) {
                            TvButton("清除筛选", { filter = WallFilter(); sort = WallSort.Added }, icon = GuguIcon.X, key = "clear")
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ 合集列表 */

/** 合集列表（`p=colls`）：同海报墙（四个方向走格子，确定 = 打开合集）；卡片副行 = 部数。数据复用 `screens/sets` 的 `loadSets`。 */
@Composable
fun TvCollsScreen(today: Today, libraryId: String? = null) {
    val chrome = LocalChrome.current
    LaunchedEffect(Unit) { chrome.spot = null }
    val load = rememberLoad("tv-colls", libraryId) { loadSets(it, libraryId) }
    val sets = load.value
    Column(Modifier.fillMaxSize().testTag("tv-colls")) {
        TvPageHeader("合集", "COLLECTIONS", today, trailing = sets?.let { s -> "${s.size} 套" + s.mapNotNull { it.count }.takeIf { it.isNotEmpty() }?.let { " · 共 ${it.sum()} 部" }.orEmpty() })
        when {
            sets == null && load.error != null -> TvStateView("tv-colls-error", "摊手", load.error!!, null, action = "再试一次", onAction = load::retry)
            sets == null -> if (load.showSkeleton) TvWallLoading(today.day)
            sets.isEmpty() -> TvStateView("tv-colls-empty", "捧白纸", "还没有合集", null)
            else -> TvPosterWall(sets, TvBrowseSpec.COLUMNS, "set-", sub = { it.count?.let { n -> "$n 部" } })
        }
    }
}

/**
 * 海报墙预取的顺序（1.6 B2）：可见的是第 [firstRow]–[lastRow] 行；先下一行（按住下键要进来的），再上一行（按住上键），每行从左到右。
 * 返回条目下标；越界的不给。纯函数，有单测。
 */
fun wallPrefetchOrder(firstRow: Int, lastRow: Int, columns: Int, count: Int): List<Int> {
    if (columns <= 0 || count <= 0) return emptyList()
    val rows = listOf(lastRow + 1, firstRow - 1).filter { it >= 0 && it * columns < count }.distinct()
    return rows.flatMap { r -> (r * columns until minOf((r + 1) * columns, count)).toList() }
}
