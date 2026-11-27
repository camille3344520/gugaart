package com.gugaart.ui.screens.favs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.Seal
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.StateView
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPageState
import com.gugaart.ui.screens.PageTab
import com.gugaart.ui.screens.TabsRow
import com.gugaart.ui.screens.TitlePage
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.pageStateOf
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.FAVORITE_SORTS
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.CancellationException

/** 收藏页的页签：片（电影 + 剧集）/ 单集 / 人物 / 合集。 */
enum class FavTab(val id: String, val label: String) {
    Titles("titles", "片"), Episodes("ep", "单集"), People("people", "人物"), Sets("sets", "合集");

    fun accepts(t: Title): Boolean = when (this) {
        Titles -> t.kind == Kind.Movie || t.kind == Kind.Series
        Episodes -> t.kind == Kind.Episode
        People -> t.kind == Kind.Person
        Sets -> t.kind == Kind.BoxSet
    }

    companion object {
        fun of(id: String?): FavTab = entries.firstOrNull { it.id == id } ?: Titles
    }
}

@Immutable
data class Favs(val items: List<Title>) {
    fun of(tab: FavTab): List<Title> = items.filter { tab.accepts(it) }

    /** 一开始落在哪个页签：第一个有东西的；全空 → 片。 */
    val firstTab: FavTab get() = FavTab.entries.firstOrNull { of(it).isNotEmpty() } ?: FavTab.Titles
}

/**
 * 收藏（只读）：上游 :core 的 `getFavorites`（电影 / 剧集 / 合集 / 单集，[sortKey] 是上游 `FAVORITE_SORTS` 的键）；
 * 人物另取一次（收藏的人物也是条目）——服务器不支持这个查询就当没有，不让整页失败。
 */
suspend fun loadFavs(client: EmbyClient, sortKey: String): Favs {
    val opts = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "ProductionYear", "ChildCount", "CommunityRating"))
    val main = client.getFavorites(sortKey, 300, opts).items.map { client.titleOf(it) }
    val people = try {
        client.getItems(LibraryQuery(status = listOf("IsFavorite"), includeItemTypes = listOf("Person"), recursive = true, limit = 100), opts).items.map { client.titleOf(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }
    return Favs(main + people)
}

/** 排序在版面上的说法：收藏 = 盖章，所以「最近添加」写成「按盖章时间」。 */
fun favSortLabel(key: String): String = when (key) {
    "added" -> "按收藏时间 ↓"
    "name" -> "按名字"
    "rating" -> "按评分 ↓"
    "year" -> "按年份 ↓"
    else -> FAVORITE_SORTS.firstOrNull { it.key == key }?.label ?: key
}

/** 换到下一种排序（循环）。 */
fun nextFavSort(key: String): String {
    val keys = FAVORITE_SORTS.map { it.key }
    return keys[(keys.indexOf(key).coerceAtLeast(0) + 1) % keys.size]
}

/** 单集行的副行：「颜心记 · 第 1 季 · 看到 59%」。 */
fun favEpisodeLine(t: Title): String =
    listOfNotNull(t.seriesName, t.seasonNo?.let { "第 $it 季" }, if (t.progress > 0f) "看到 ${t.pct ?: 0}%" else if (t.played) "已看" else null).joinToString(" · ")

/**
 * 收藏（设计稿 `p=favs`，路由 `Route.History(tab = "favorites")`——和上游同一个路由）：盖过章的都在这里。
 * 页签：片（三列海报 + 片名旁一枚小印）/ 单集（行）/ 人物（方形彩色照片的行）/ 合集；右上换排序（上游的四种）。
 */
@Composable
fun FavsScreen() {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    var sort by remember { mutableStateOf(FAVORITE_SORTS.first().key) }
    val load = rememberLoad("favs", sort) { loadFavs(it, sort) }
    val preset = pageStateOf(LocalPageState.current, "tab")
    var picked by remember { mutableStateOf(preset?.let { FavTab.of(it) }) }
    // 在这一页取消收藏的，章褪掉之后从列表里拿走（界面状态在 chrome）
    val favs = load.value?.let { f -> Favs(f.items.filter { chrome.isFavorite(it.id, true) }) }
    val tab = picked ?: favs?.firstTab ?: FavTab.Titles
    TitlePage("收藏 · FAVORITES", "收藏", onBack = { graph.nav.backOrHome() }, barTitle = "收藏", action = GuguIcon.ArrowUpDown, actionLabel = "换排序", onAction = { sort = nextFavSort(sort) }, tag = "favs-page") {
        TabsRow(FavTab.entries.map { t -> PageTab(t.id, t.label, favs?.of(t)?.size) }, tab.id, { picked = FavTab.of(it) }, trailing = favSortLabel(sort), onTrailing = { sort = nextFavSort(sort) })
        Column(Modifier.padding(horizontal = dimens.margin)) {
            when {
                favs == null && load.error != null -> ErrorView(load.error!!, load::retry)
                favs == null -> if (load.showSkeleton) {
                    LoadingView()
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) { repeat(3) { Skeleton(Modifier.weight(1f).aspectRatio(2f / 3f)) } }
                }
                favs.items.isEmpty() -> StateView("state-favs-empty", "捧白纸", "还没有收藏", "在详情页点「收藏」，片子就会放进这一页。", action = "去片库看看", onAction = { graph.nav.reset(Route.Home) })
                else -> {
                    val list = favs.of(tab)
                    when {
                        list.isEmpty() -> BasicText("没有收藏", Modifier.padding(vertical = 28.dp).testTag("favs-tab-empty"), style = Guga.song(type.caption, color = colors.dim))
                        tab == FavTab.Titles || tab == FavTab.Sets -> for (row in list.chunked(3)) {
                            Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(dimens.gutter)) {
                                for (t in row) FavCard(t, { graph.open(t.id) }, Modifier.weight(1f))
                                // 不满一行：不拉伸卡片
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        tab == FavTab.Episodes -> for (t in list) {
                            FavEpisodeRow(t) { graph.open(t.id) }
                            Rule()
                        }
                        else -> for (t in list) {
                            FavPersonRow(t) { graph.open(t.id) }
                            Rule()
                        }
                    }
                }
            }
        }
    }
}

/** 收藏的一部片：海报 + 片名（两行）和一枚小印并排 + 「2024 · 剧集」。 */
@Composable
private fun FavCard(t: Title, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Column(modifier.pressable(press, label = t.name, onClick = onClick).testTag("fav-card")) {
        Box(Modifier.pressGraphics(press).fillMaxWidth().aspectRatio(2f / 3f)) {
            CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), caption = t.name) }
        }
        Row(Modifier.padding(top = 9.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.Top) {
            BasicText(t.name, Modifier.weight(1f).padding(end = 4.dp), style = Guga.song(type.caption * 1.12f, FontWeight.Bold, lineHeight = type.caption * 1.5f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Seal(size = 22.dp)
        }
        val kind = when (t.kind) {
            Kind.Series -> "剧集"
            Kind.Movie -> "电影"
            Kind.BoxSet -> t.count?.let { "$it 部" } ?: "合集"
            else -> null
        }
        BasicText(listOfNotNull(t.year?.toString(), kind).joinToString(" · "), Modifier.padding(top = 3.dp), style = Guga.note(type.fine * 1.1f, colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FavEpisodeRow(t: Title, onClick: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = t.name, onClick = onClick).padding(vertical = 14.dp).testTag("fav-episode"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("E${t.episodeNo ?: ""}", Modifier.width(52.dp), style = Guga.numeral(type.row * 1.1f, colors.spot), maxLines = 1, softWrap = false)
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            BasicText(t.name, style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(favEpisodeLine(t), Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Thumb(t.art.thumb, Rules.episodeNo(t.episodeNo ?: 0), 84.dp, progress = t.progress)
        Seal(Modifier.padding(start = 10.dp), size = 22.dp)
    }
}

@Composable
private fun FavPersonRow(t: Title, onClick: () -> Unit) {
    val type = Guga.type
    val press = rememberPressState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).pressGraphics(press).pressable(press, label = t.name, onClick = onClick).padding(vertical = 12.dp).testTag("fav-person"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 肖像永远是彩色的
        Box(Modifier.size(54.dp)) {
            CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.22f)) { HatchPlate(Rules.monogram(t.name, person = true), Modifier.fillMaxSize(), textSize = type.row) }
        }
        BasicText(t.name, Modifier.weight(1f).padding(start = 16.dp, end = 10.dp), style = Guga.song(type.row, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Seal(size = 22.dp)
    }
}
