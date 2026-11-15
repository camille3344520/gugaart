package com.gugaart.ui.screens.sets

import androidx.compose.runtime.Immutable
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery

/** 合集列表：全部合集（`BoxSet`），按名字排。只读。 */
suspend fun loadSets(client: EmbyClient, libraryId: String?): List<Title> =
    client.getItems(
        LibraryQuery(parentId = libraryId, includeItemTypes = listOf("BoxSet"), recursive = true, sortBy = listOf("SortName"), sortOrder = "Ascending", limit = 300),
        ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "ChildCount", "Overview")),
    ).items.map { client.titleOf(it) }

/** 合集详情要的：这套合集 + 它的成员（按上映顺序）。 */
@Immutable
data class SetData(val set: Title, val members: List<Title>) {
    /** 「2007 — 2021」；只有一个年份 → 「2007」；没有年份 → null。 */
    val years: String?
        get() {
            val ys = members.mapNotNull { it.year }
            if (ys.isEmpty()) return null
            return if (ys.min() == ys.max()) ys.min().toString() else "${ys.min()} — ${ys.max()}"
        }

    /** 成员的真实部数（服务器给的 ChildCount 可能没有）。 */
    val count: Int get() = if (members.isNotEmpty()) members.size else set.count ?: 0

    /** 总时长（秒）；有成员没有时长就不算进去，全都没有 → null。 */
    val runtimeSec: Long? get() = members.mapNotNull { it.runtimeSec }.takeIf { it.isNotEmpty() }?.sum()

    /** 均分「7.7」：只算有评分的成员。 */
    val average: String?
        get() {
            val rs = members.mapNotNull { it.rating?.toDoubleOrNull() }
            if (rs.isEmpty()) return null
            val a = kotlin.math.round(rs.average() * 10) / 10
            return if (a == kotlin.math.floor(a) && a >= 10) "10" else a.toString()
        }

    val latestYear: Int? get() = members.mapNotNull { it.year }.maxOrNull()

    /** 从哪一部看起：第一部没看完的；都看完了 → 第一部。 */
    val startWith: Title? get() = members.firstOrNull { it.progress > 0f } ?: members.firstOrNull { !it.played } ?: members.firstOrNull()

    /**
     * 合集自己没有题图时用哪一部的剧照（docs/data-edge-cases.md §5：取第一部有剧照的成员，图注写明取自哪部）。
     * 合集自己有 → null。
     */
    val borrowed: Title? get() = if (set.art.backdrop != null) null else members.firstOrNull { it.art.backdrop != null }
}

/** 按上映顺序：有首播日期按日期，其次年份；都没有的排在最后，保持服务器给的顺序。纯函数。 */
fun byRelease(members: List<Title>): List<Title> =
    members.withIndex().sortedWith(compareBy({ it.value.premiere ?: it.value.year?.toString()?.plus("-99") ?: "9999" }, { it.index })).map { it.value }

suspend fun loadSet(client: EmbyClient, set: Title): SetData {
    val r = client.getItems(
        LibraryQuery(parentId = set.id, limit = 300),
        ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Overview", "Genres", "ProductionYear", "PremiereDate")),
    )
    return SetData(set, byRelease(r.items.map { client.titleOf(it) }.filter { it.kind == Kind.Movie || it.kind == Kind.Series }))
}

/** 成员行的副行：「2007 · 7.6 分 · 1 小时 38 分」，没有的那一段不写。 */
fun memberLine(t: Title, runtime: (Long) -> String): String =
    listOfNotNull(t.year?.toString(), t.rating?.let { "$it 分" }, t.runtimeSec?.let(runtime), if (t.kind == Kind.Series) t.count?.let { "$it 集" } else null).joinToString(" · ")
