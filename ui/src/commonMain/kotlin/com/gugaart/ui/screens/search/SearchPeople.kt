package com.gugaart.ui.screens.search

import androidx.compose.runtime.Immutable
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Pinyin
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ImageUrlOptions
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import com.gugugaga.core.NamedListOptions

/*
 * 按演员名字搜（用户 2026-11-16：「搜索也支持按演员名字搜索，不过缩写的话还是影片排在前面」）。
 *
 * 服务器的人物搜索只认名字里的字，拼音缩写（zxc = 周星驰）它不懂——这里像片名一样读一份本地的人名单（[loadPeopleIndex]，一次最多 [PEOPLE_MAX] 人），
 * 名字和首字母都能命中（[searchPeopleLocal]）。排序（[peopleFirst]）：打的是缩写（只有字母数字）→ 片子在前、演员在后；
 * 打的是名字、正好是某位演员（或以它开头），又没有哪部片名正好是它 → 演员在前，下面接着列这位演员在片库里的作品（[loadPersonWorks]）。
 */

const val PEOPLE_MAX = 6000

/** 命中的一位演员 + 命中的字（上专色）。 */
@Immutable
data class PersonHit(val person: Person, val range: IntRange?)

/** 本地人名单：服务器 `/Persons`，按名字排，带头像地址。 */
suspend fun loadPeopleIndex(client: EmbyClient): List<Person> =
    client.getPersons(NamedListOptions(limit = PEOPLE_MAX, sortBy = "SortName", sortOrder = "Ascending")).items
        .filter { !it.name.isNullOrBlank() }
        .map { Person(it.id, it.name.orEmpty(), photo = client.personImage(it.id, it.imageTags["Primary"], ImageUrlOptions(maxWidth = 360))) }

/** 人名匹配：和片名同一套（原文命中优先、越靠前越好；字母数字再按拼音首字母）。纯函数，有单测。 */
fun searchPeopleLocal(people: List<Person>, query: String, limit: Int = 12): List<PersonHit> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return people.distinctBy { it.id }
        .mapNotNull { p -> Pinyin.score(p.name, q)?.let { Triple(p, it, Pinyin.match(p.name, q)) } }
        .sortedWith(compareBy<Triple<Person, Int, IntRange?>> { it.second }.thenBy { it.first.name.length }.thenBy { it.first.name })
        .take(limit)
        .map { PersonHit(it.first, it.third) }
}

/** 本地命中的在前，服务器另外找到的接在后面（同一个人只留一次）。 */
fun mergePeople(local: List<PersonHit>, remote: List<Person>, query: String): List<PersonHit> {
    val seen = local.map { it.person.id }.toHashSet()
    return local + remote.filter { seen.add(it.id) }.map { PersonHit(it, Pinyin.match(it.name, query.trim())) }
}

/** 演员排在片子前面吗。缩写（只有字母数字）一律片子在前；名字正好是 / 以它开头（至少两个字）、又没有片名正好是它 → 演员在前。纯函数，有单测。 */
fun peopleFirst(query: String, titles: List<TitleHit>, people: List<PersonHit>): Boolean {
    val q = query.trim()
    if (q.isEmpty() || Pinyin.isInitialsQuery(q)) return false
    if (titles.any { it.title.name.equals(q, ignoreCase = true) }) return false
    return people.any { it.person.name.equals(q, ignoreCase = true) || (q.length >= 2 && it.person.name.startsWith(q, ignoreCase = true)) }
}

/** 这位演员在片库里的电影和剧集（新的在前）。 */
suspend fun loadPersonWorks(client: EmbyClient, personId: String, limit: Int = 40): List<Title> =
    client.getItems(
        LibraryQuery(personIds = listOf(personId), includeItemTypes = listOf("Movie", "Series"), recursive = true, sortBy = listOf("ProductionYear", "SortName"), sortOrder = "Descending", limit = limit),
        ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Genres", "Overview", "CommunityRating")),
    ).items.map { client.titleOf(it) }
