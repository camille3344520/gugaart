package com.gugaart.ui.screens.home

import androidx.compose.runtime.Immutable
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.LibraryEntry
import com.gugaart.ui.data.Title

/*
 * 首页目录里按「类」排的栏：电视剧 / 电影 / 动漫 / 综艺（用户 2026-11-15：「除了电影、动漫 加一下电视剧、综艺」）。
 *
 * 以前是「每个媒体库一栏」（电脑只排第 2、3 个库），库怎么起名、排第几就决定了有没有那一栏。现在按内容分：
 * - 综艺 = 库名里带「综艺」的那个库，加上别的库里题材是真人秀 / 脱口秀 / 综艺的剧；题材没刮全的（用户 2026-11-15：「极限挑战不是综艺吗？
 *   为啥是出现在剧集」——Emby 里综艺和电视剧都是 Series，只能看库名、题材），再看简介里有没有「真人秀 / 综艺 / 脱口秀 / 选秀 / 访谈节目…」；
 * - 动漫 = 库名里带「动漫 / 动画 / 番」的库，加上剧集库里题材是动画的剧；
 * - 电影 = 电影库（动画电影还在电影里）；
 * - 电视剧 = 剧集库里剩下的（不含动漫、不含综艺）。
 * 分不进这四类的库（混合库里既不是电影也不是剧的东西）不上首页的栏——「媒体库」那一栏里还在。空的栏不出现。
 * 全是纯函数（有单测）；数据还是每个库的「最近添加」，不多发请求。
 */

/** 四个栏，[label] = 栏名，顺序 = 目录里的先后。 */
enum class ShelfKind(val label: String, val english: String) {
    Tv("电视剧", "SERIES"),
    Movie("电影", "FILMS"),
    Anime("动漫", "ANIME"),
    Variety("综艺", "VARIETY"),
}

/** 首页的一栏：[library] = 这一栏只来自一个库时就是它（「全部 →」翻到那个库）；来自好几个库 = null。 */
@Immutable
data class HomeShelf(val kind: ShelfKind, val library: LibraryEntry?, val titles: List<Title>) {
    val name: String get() = kind.label
    val key: String get() = kind.name.lowercase()
}

private val VARIETY_GENRES = setOf("reality", "reality-tv", "reality tv", "talk show", "talk-show", "talk", "game show", "game-show", "variety", "variety show", "真人秀", "综艺", "脱口秀", "访谈", "游戏节目", "选秀")
private val ANIME_GENRES = setOf("animation", "anime", "动画", "动漫", "番剧")
private val ANIME_LIBRARY = listOf("动漫", "动画", "番", "anime")
private val VARIETY_LIBRARY = listOf("综艺", "variety")

fun isVarietyGenre(genre: String): Boolean = genre.trim().lowercase() in VARIETY_GENRES

/** 简介里说自己是节目的词（电视剧的简介几乎不会这样写）。 */
private val VARIETY_WORDS = listOf("真人秀", "综艺", "脱口秀", "选秀", "访谈节目", "竞技节目", "户外节目", "棚内节目")

/** 是不是综艺：题材说是，或者简介里说自己是真人秀 / 综艺节目。纯函数，有单测。 */
fun looksLikeVariety(title: Title): Boolean = title.genres.any(::isVarietyGenre) || VARIETY_WORDS.any { title.overview.contains(it) }

fun isAnimeGenre(genre: String): Boolean = genre.trim().lowercase() in ANIME_GENRES

/** 库名说的是哪一类；看不出来 = null（再看库的种类和条目的题材）。 */
fun libraryShelfKind(library: LibraryEntry): ShelfKind? {
    val name = library.name.lowercase()
    return when {
        VARIETY_LIBRARY.any { name.contains(it) } -> ShelfKind.Variety
        ANIME_LIBRARY.any { name.contains(it) } -> ShelfKind.Anime
        else -> null
    }
}

/** 这一条排进哪一栏；哪一栏都不是 = null。 */
fun shelfKindOf(title: Title, library: LibraryEntry): ShelfKind? {
    val named = libraryShelfKind(library)
    val movie = title.kind == Kind.Movie
    val series = title.kind == Kind.Series || title.kind == Kind.Episode
    return when {
        named != null -> named
        // 综艺先于别的：真人秀就算放在剧集库里也是综艺
        !movie && looksLikeVariety(title) -> ShelfKind.Variety
        library.kind == "movies" || (library.kind == null && movie) -> ShelfKind.Movie
        library.kind == "tvshows" || (library.kind == null && series) -> if (title.genres.any(::isAnimeGenre)) ShelfKind.Anime else ShelfKind.Tv
        else -> null
    }
}

/**
 * 把每个库的「最近添加」分成四栏（顺序 = [ShelfKind] 的顺序；空的不出现；每栏最多 [limit] 部，同一部只上一次）。
 * 一栏来自好几个库时轮流取（每个库一部一部地轮），不让第一个库占满。
 */
fun homeShelves(arrivals: List<NewArrivals>, limit: Int = 16): List<HomeShelf> {
    val out = ArrayList<HomeShelf>()
    for (kind in ShelfKind.entries) {
        val parts = arrivals.map { band -> band.library to band.titles.filter { shelfKindOf(it, band.library) == kind } }.filter { it.second.isNotEmpty() }
        if (parts.isEmpty()) continue
        val titles = ArrayList<Title>()
        val seen = HashSet<String>()
        var i = 0
        while (titles.size < limit && parts.any { i < it.second.size }) {
            for ((_, list) in parts) {
                val t = list.getOrNull(i) ?: continue
                if (seen.add(t.seriesId ?: t.id) && titles.size < limit) titles += t
            }
            i++
        }
        out += HomeShelf(kind, parts.singleOrNull()?.first, titles)
    }
    return out
}
