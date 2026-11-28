package com.gugaart.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.gugugaga.app.AppGraph
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * 「片库」一栏在版面上的一格：一个媒体库的名字和规模（首页目录的「媒体库 5」、片库标签页的列表）。
 * 界面只认这个模型，不直接拿 `BaseItemDto` 排版（docs/adr/0001-architecture.md §4.3：界面模型在 :ui，接口模型在上游 :core）。
 */
data class LibraryEntry(
    val id: String,
    val name: String,
    /** Emby 的 CollectionType：movies / tvshows / boxsets / …；null = 混合库。 */
    val kind: String?,
    /** 部数（服务器没给就是 null：版面上不写数字，不写 0）。 */
    val titles: Int?,
)

/** 接口模型 → 界面模型。库名可能是拉丁字母（emby302）：排版时不要假设是中文宽度。 */
fun BaseItemDto.toLibraryEntry(): LibraryEntry =
    LibraryEntry(id = id, name = name.trim(), kind = collectionType?.lowercase(), titles = childCount ?: recursiveItemCount)

/**
 * 一个媒体库的海报墙里排哪几种条目。部数也按这个口径数。纯函数，有单测。
 * 1.5.1 审计 P1-12：家庭视频、MV 按 `Video` 取（MV 库里 Emby 给的类型是 `MusicVideo`，一起取）；混合库（没有 CollectionType）电影、剧集之外也取 `Video`。
 */
fun libraryItemTypes(kind: String?): List<String> = when (kind) {
    "movies" -> listOf("Movie")
    "tvshows" -> listOf("Series")
    "boxsets" -> listOf("BoxSet")
    "homevideos" -> listOf("Video")
    "musicvideos" -> listOf("MusicVideo", "Video")
    null, "mixed" -> listOf("Movie", "Series", "Video")
    else -> listOf("Movie", "Series")
}

/** 不在片库里列出来的库（P1-12：影视客户端，不做音乐、直播、照片、播放列表）。 */
private val UNLISTED_LIBRARY_KINDS = setOf("music", "livetv", "photos", "playlists")

/** 这个库在片库里列不列出来。纯函数，有单测。 */
fun isListedLibrary(kind: String?): Boolean = kind !in UNLISTED_LIBRARY_KINDS

/**
 * 一个媒体库的部数：`/Items?ParentId=…&Recursive=true&IncludeItemTypes=…&Limit=0` 的 `TotalRecordCount`（只读；和上游筛选面板数数的口径一样）。
 * 不能用 `/Views` 给的 `ChildCount`：那是库的直接子项数——真实服务器上一个库常常只有 1 个文件夹，于是每个库都写成「1 部」。
 */
suspend fun libraryCount(client: EmbyClient, library: LibraryEntry): Int? = try {
    client.getItems(
        LibraryQuery(parentId = library.id, includeItemTypes = libraryItemTypes(library.kind), recursive = true, limit = 0),
        ItemsRequestOptions(fields = emptyList(), enableImages = false, enableUserData = false),
    ).totalRecordCount
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

/**
 * 真实数据的代码路径：上游 :core 的 `EmbyClient.getViewList()`（`/Users/{id}/Views`，只读）+ 每个库各数一次部数（并行）。
 * 数不出来的库不写数字（null），不退回 `ChildCount`。
 */
suspend fun loadLibraries(client: EmbyClient, onViews: ((List<LibraryEntry>) -> Unit)? = null): List<LibraryEntry> = coroutineScope {
    val views = loadLibraryViews(client)
    // 先把库名交出去（大库数部数要好几秒：几千、上万部的库，Emby 递归数一次不快）
    onViews?.invoke(views)
    views.map { lib -> async { lib.copy(titles = libraryCount(client, lib)) } }.awaitAll()
}

/** 只要库名和类型、不数部数（一次 `/Views`）：进一个库之前认它是什么库用这个，不等每个库都数完。 */
suspend fun loadLibraryViews(client: EmbyClient): List<LibraryEntry> =
    client.getViewList().map { it.toLibraryEntry().copy(titles = null) }.filter { isListedLibrary(it.kind) }

/** 一次加载的三种结果；界面按它画「加载中（她在摆稿纸）/ 目录 / 连不上」。 */
sealed interface Shelf {
    data object Loading : Shelf
    data class Ready(val libraries: List<LibraryEntry>) : Shelf
    data class Failed(val message: String) : Shelf

    /** 还没登录：没有会话，就没有可读的库。 */
    data object SignedOut : Shelf
}

/**
 * 当前会话的媒体库。会话来自上游的 `AppGraph.session`（登录、已保存账号、备用线路都在那边）；换账号时自动重读。
 * 骨架阶段直接读；页面多起来以后改走上游的 `QueryClient`（旧内容保持 = 不闪），见 ADR §4.4。
 */
@Composable
fun rememberLibraryShelf(graph: AppGraph): State<Shelf> {
    val session by graph.session.session.collectAsState()
    val client = session?.client
    return produceState<Shelf>(if (client == null) Shelf.SignedOut else Shelf.Loading, client) {
        if (client == null) {
            value = Shelf.SignedOut
            return@produceState
        }
        value = Shelf.Loading
        value = try {
            Shelf.Ready(loadLibraries(client))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 不把地址 / token 带进界面：只给异常自己的说明
            Shelf.Failed(e.message ?: "服务器没有应答")
        }
    }
}

/** 「媒体库 5 · 电视剧 22 · 电影 118 · …」；各种没有数据的情况都有自己的一句话，不留空行。 */
fun shelfLine(shelf: Shelf): String = when (shelf) {
    Shelf.SignedOut -> "未登录"
    Shelf.Loading -> "正在排片…"
    is Shelf.Failed -> "片库没连上 · ${shelf.message}"
    is Shelf.Ready ->
        if (shelf.libraries.isEmpty()) "没有媒体库"
        else "媒体库 ${shelf.libraries.size} · " + shelf.libraries.joinToString(" · ") { l -> if (l.titles != null) "${l.name} ${l.titles}" else l.name }
}
