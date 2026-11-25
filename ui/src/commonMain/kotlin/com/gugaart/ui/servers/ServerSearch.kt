package com.gugaart.ui.servers

import com.gugaart.ui.data.Pinyin
import com.gugaart.ui.search.SearchFold
import com.gugaart.ui.search.SearchPinyin

/**
 * 服务器列表的搜索（纯函数，有单测）。名字 / 备注 / 地址做子串匹配，加拼音首字母。
 *
 * 复用 1.4.1 搜索引擎的现成工具，不另写一套：
 * - [SearchFold.foldPlain]：小写、全角→半角、去调号、繁→简、去空白标点 —— 查询词和名字 / 备注 / 地址折成同一形状再比，
 *   所以「速度與激情」找得到「速度与激情」、「127.0.0.1」找得到地址里的「127001」；
 * - [SearchPinyin.key] 的首字母串（沿用 [Pinyin] 的表）：查询词全是字母数字时，再按名字 / 备注的拼音首字母子串匹配，
 *   「jl」找得到「家里 NAS」、「yxj」找得到「颜心记」。
 */
object ServerSearch {
    /** 在 [entries] 里筛出命中 [query] 的，保持原顺序。空 / 全标点查询返回全部。 */
    fun match(entries: List<ServerEntry>, query: String): List<ServerEntry> {
        val q = SearchFold.foldPlain(query.trim()).text
        if (q.isEmpty()) return entries
        val byInitials = Pinyin.isInitialsQuery(q)
        return entries.filter { e ->
            fields(e).any { it.contains(q) } || (byInitials && initials(e).any { it.contains(q) })
        }
    }

    /** 折过的名字 / 备注 / 地址（子串匹配用）。 */
    private fun fields(e: ServerEntry): List<String> =
        listOf(e.name, e.note.orEmpty(), e.address).map { SearchFold.foldPlain(it).text }

    /** 名字 / 备注的拼音首字母串（地址多是字母数字，子串匹配已覆盖，不再折拼音）。 */
    private fun initials(e: ServerEntry): List<String> =
        listOf(e.name, e.note.orEmpty()).map { SearchPinyin.key(it).initials }
}
