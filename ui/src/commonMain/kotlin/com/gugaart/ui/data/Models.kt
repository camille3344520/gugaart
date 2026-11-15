package com.gugaart.ui.data

import androidx.compose.runtime.Immutable
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.BaseItemPerson
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ImageKind
import com.gugugaga.core.ImageType
import com.gugugaga.core.ImageUrlOptions
import com.gugugaga.core.MediaSourceInfo
import kotlin.math.roundToInt

/** 条目的种类（界面只分这几种）。 */
enum class Kind { Movie, Series, Season, Episode, BoxSet, Person, Other }

fun kindOf(type: String?): Kind = when (type) {
    "Movie" -> Kind.Movie
    "Series" -> Kind.Series
    "Season" -> Kind.Season
    "Episode" -> Kind.Episode
    "BoxSet" -> Kind.BoxSet
    "Person" -> Kind.Person
    else -> Kind.Other
}

/** 一个条目的几张图的地址（null = 服务器上没有这张图：版面走兜底，docs/data-edge-cases.md §5）。 */
@Immutable
data class Art(
    val poster: String? = null,
    val backdrop: String? = null,
    /** 16:9 的横图：分集图 / 横版缩略；没有时依次退到剧集的横图、剧照。 */
    val thumb: String? = null,
    val logo: String? = null,
    /** 单集自己的剧照（分集图）；没有 = null（分集行的缩略图位换成斜纹空版，不拿整部剧的同一张图顶 155 次）。 */
    val still: String? = null,
)

/**
 * 版面上的一个条目（接口模型 `BaseItemDto` → 界面模型，ADR §4.3）。兜底规则在转换时做完：
 * 评分已经是「8.8」/「10」/ null，导语已经过了脏词检查，栏目行最多 4 段……版式函数直接排。
 */
@Immutable
data class Title(
    val id: String,
    val kind: Kind,
    val name: String,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    /** 「9.2」「10」；没有评分 = null（不占位）。 */
    val rating: String? = null,
    val officialRating: String? = null,
    /** 「已完结」「连载中」；不知道 = null。 */
    val status: String? = null,
    val art: Art = Art(),
    /** 去掉了换行和全角空格的简介；没有 = ""（这一栏不出现）。 */
    val overview: String = "",
    /** 导语：tagline（过了检查）→ 简介头一两句 → null。 */
    val lead: String? = null,
    val runtimeSec: Long? = null,
    /** 0..1；0 = 没开始（不画进度线）。 */
    val progress: Float = 0f,
    val positionSec: Long? = null,
    val played: Boolean = false,
    val favorite: Boolean = false,
    /** 剧集：总集数；合集：部数。没给 = null（不写数字）。 */
    val count: Int? = null,
    /** 剧集：没看的集数。 */
    val unplayed: Int? = null,
    /** 单集：所属的剧。 */
    val seriesId: String? = null,
    val seriesName: String? = null,
    val seasonNo: Int? = null,
    /** 单集的集号（绝对集号，服务器给的）。 */
    val episodeNo: Int? = null,
    val studios: List<String> = emptyList(),
    val people: List<Person> = emptyList(),
    val sources: List<MediaSourceInfo> = emptyList(),
    /** 首播日期（ISO）；用来排序。 */
    val premiere: String? = null,
) {
    /** 版面上显示的名字：单集在首页 / 目录里写剧名。 */
    val displayName: String get() = if (kind == Kind.Episode) seriesName ?: name else name

    /** 栏目行：「剧集 · 悬疑 · 喜剧 · 2024」，最多 4 段。 */
    fun kicker(lead: String? = null, tail: String? = null): List<String> =
        Rules.kicker(listOfNotNull(lead) + genres.take(if (lead != null) 2 else 3) + listOfNotNull(year?.toString(), tail))

    /** 进度百分比（整数）；没开始 = null。 */
    val pct: Int? get() = if (progress > 0f) (progress * 100).roundToInt().coerceIn(1, 99) else null
}

/** 演员 / 导演（详情页的一格）。[photo] = null → 姓氏的斜纹空版，不用灰色人形。 */
@Immutable
data class Person(
    val id: String,
    val name: String,
    /** 角色名；长的（「Jing Tian / Fei Peng / …」）由版面单行省略。 */
    val role: String? = null,
    /** Actor / Director / Writer / … */
    val type: String? = null,
    val photo: String? = null,
)

/** 一季（选季控件的一格）。[number] = null：季名乱码、没有季号（「G【古@语】」）——原样显示名字，不替用户改名。 */
@Immutable
data class Season(val id: String, val name: String, val number: Int?, val episodes: Int?, val unplayed: Int? = null)

private const val POSTER_W = 600
private const val BACKDROP_W = 1600
private const val THUMB_W = 960
private const val LOGO_W = 900
private const val PHOTO_W = 360

private fun ticksToSec(t: Long?): Long? = Rules.ticksToSec(t)

private fun statusOf(raw: String?): String? = when (raw) {
    "Ended" -> "已完结"
    "Continuing" -> "连载中"
    else -> null
}

/** 图的地址经上游的 `EmbyClient.itemImage`（它带着「单集没有图 → 退剧集的图」这些规则），尺寸档位写在这里。 */
fun EmbyClient.artOf(item: BaseItemDto): Art {
    val episode = item.type == "Episode"
    val still = if (episode && !item.imageTags["Primary"].isNullOrEmpty()) imageUrl(item.id, ImageType.Primary, ImageUrlOptions(tag = item.imageTags["Primary"], maxWidth = THUMB_W)) else null
    val backdrop = itemImage(item, ImageKind.Backdrop, ImageUrlOptions(maxWidth = BACKDROP_W))
    // 单集的横图：自己的分集图 → 剧集的横图 → 剧集的剧照；**不**退到竖的海报（上游的 Thumb 规则最后会退到海报，16:9 的格子里不能用）
    val thumb = if (episode) {
        still
            ?: item.parentThumbItemId?.let { id -> item.parentThumbImageTag?.takeIf { it.isNotEmpty() }?.let { imageUrl(id, ImageType.Thumb, ImageUrlOptions(tag = it, maxWidth = THUMB_W)) } }
            ?: backdrop
    } else {
        itemImage(item, ImageKind.Thumb, ImageUrlOptions(maxWidth = THUMB_W))?.takeIf { !item.imageTags["Thumb"].isNullOrEmpty() || item.backdropImageTags.isNotEmpty() }
    }
    return Art(
        poster = itemImage(item, ImageKind.Poster, ImageUrlOptions(maxWidth = POSTER_W)),
        backdrop = backdrop,
        thumb = thumb,
        logo = itemImage(item, ImageKind.Logo, ImageUrlOptions(maxWidth = LOGO_W)),
        still = still,
    )
}

fun EmbyClient.personOf(p: BaseItemPerson): Person =
    Person(id = p.id, name = p.name.trim(), role = p.role?.trim()?.takeIf { it.isNotEmpty() }, type = p.type, photo = personImage(p, ImageUrlOptions(maxWidth = PHOTO_W)))

/** 接口模型 → 界面模型。所有兜底在这里做（docs/data-edge-cases.md）。 */
fun EmbyClient.titleOf(item: BaseItemDto): Title {
    val kind = kindOf(item.type)
    val ud = item.userData
    val runtime = ticksToSec(item.runTimeTicks)
    val progress = when {
        ud?.playedPercentage != null && ud.playedPercentage!! > 0 -> (ud.playedPercentage!! / 100.0).toFloat().coerceIn(0f, 1f)
        else -> Rules.progress(ud?.playbackPositionTicks, item.runTimeTicks)
    }
    val art = if (kind == Kind.Person) Art(poster = personImage(item.id, item.imageTags["Primary"], ImageUrlOptions(maxWidth = 2000))) else artOf(item)
    return Title(
        id = item.id,
        kind = kind,
        name = item.name.trim(),
        year = item.productionYear,
        genres = item.genres.map { Rules.genreZh(it) }.filter { it.isNotEmpty() }.distinct(),
        rating = Rules.rating(item.communityRating),
        officialRating = item.officialRating?.trim()?.takeIf { it.isNotEmpty() },
        status = statusOf(item.status),
        art = art,
        overview = Rules.cleanOverview(item.overview),
        lead = Rules.lead(item.taglines, item.overview),
        runtimeSec = runtime,
        progress = if (ud?.played == true && (ud.playbackPositionTicks ?: 0) <= 0) 0f else progress,
        positionSec = ticksToSec(ud?.playbackPositionTicks),
        played = ud?.played == true,
        favorite = ud?.isFavorite == true,
        count = item.recursiveItemCount ?: item.childCount,
        unplayed = ud?.unplayedItemCount,
        seriesId = item.seriesId,
        seriesName = item.seriesName?.trim()?.takeIf { it.isNotEmpty() },
        seasonNo = item.parentIndexNumber,
        episodeNo = item.indexNumber,
        studios = item.studios.map { it.name.orEmpty().trim() }.filter { it.isNotEmpty() },
        people = item.people.map { personOf(it) },
        sources = item.mediaSources,
        premiere = item.premiereDate,
    )
}

fun BaseItemDto.toSeason(): Season = Season(
    id = id,
    name = name.trim(),
    number = indexNumber,
    episodes = childCount ?: recursiveItemCount,
    unplayed = userData?.unplayedItemCount,
)
