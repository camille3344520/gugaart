package com.gugaart.ui.screens.me

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.gugaart.ui.res.Res
import com.gugaart.ui.res.rank_01
import com.gugaart.ui.res.rank_02
import com.gugaart.ui.res.rank_03
import com.gugaart.ui.res.rank_04
import com.gugaart.ui.res.rank_05
import com.gugaart.ui.res.rank_06
import com.gugaart.ui.res.rank_07
import com.gugaart.ui.res.rank_08
import com.gugaart.ui.res.rank_09
import com.gugaart.ui.res.rank_10
import com.gugaart.ui.res.special_anime
import com.gugaart.ui.res.special_morning
import com.gugaart.ui.res.special_daily
import com.gugaart.ui.res.special_seasondone
import com.gugaart.ui.res.special_seasons10
import com.gugaart.ui.res.special_epic
import com.gugaart.ui.res.special_brave
import com.gugaart.ui.res.special_horror
import com.gugaart.ui.res.special_comedy
import com.gugaart.ui.res.special_docu
import com.gugaart.ui.res.special_scifi
import com.gugaart.ui.res.special_detective
import com.gugaart.ui.res.special_collector
import com.gugaart.ui.res.special_binge
import com.gugaart.ui.res.special_classic
import com.gugaart.ui.res.special_fresh
import com.gugaart.ui.res.special_highscore
import com.gugaart.ui.res.special_marathon
import com.gugaart.ui.res.special_newyear
import com.gugaart.ui.res.special_night
import com.gugaart.ui.res.special_omnivore
import com.gugaart.ui.res.special_rewatch
import com.gugaart.ui.res.special_triple
import com.gugaart.ui.res.special_weekends
import com.gugaart.ui.res.special_noon
import com.gugaart.ui.res.special_valentine
import com.gugaart.ui.res.special_christmas
import com.gugaart.ui.res.special_goldenweek
import com.gugaart.ui.res.special_halloween
import com.gugaart.ui.res.special_yearly100
import com.gugaart.ui.res.special_streak30
import com.gugaart.ui.res.special_allnight
import com.gugaart.ui.res.special_festival
import com.gugaart.ui.res.special_homebody
import com.gugaart.ui.res.special_romance
import com.gugaart.ui.res.special_action
import com.gugaart.ui.res.special_war
import com.gugaart.ui.res.special_history
import com.gugaart.ui.res.special_family
import com.gugaart.ui.res.special_music
import com.gugaart.ui.res.special_thriller
import com.gugaart.ui.res.special_fantasy
import com.gugaart.ui.res.special_variety
import com.gugaart.ui.res.special_oldies
import com.gugaart.ui.res.special_masterpiece
import com.gugaart.ui.res.special_obscure
import com.gugaart.ui.res.special_rewatch5
import com.gugaart.ui.res.special_dusty
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/*
 * 十级称号的徽章（用户 2026-11-15：「称号图标，等级都要设计好logo」）。3D 珐琅徽章，四档材质：1–3 铜、4–6 银（加飘带）、
 * 7–9 金（加月桂和飘带）、10 宗师（金 + 月桂 + 小皇冠）；中间是这一级的观影图案（票根、爆米花、沙发、月牙挂胶片、场记板、
 * 胶片盘、∞ 胶片、片架、胶片心、放映机）。图上没有字（字由界面排）。原图在 design/generated/v3/badges/，
 * 提示词在 design/source/prompts-3d.md「称号徽章」。
 */

private val BADGES: List<DrawableResource> = listOf(
    Res.drawable.rank_01, Res.drawable.rank_02, Res.drawable.rank_03, Res.drawable.rank_04, Res.drawable.rank_05,
    Res.drawable.rank_06, Res.drawable.rank_07, Res.drawable.rank_08, Res.drawable.rank_09, Res.drawable.rank_10,
)

/** 去掉颜色（还没拿到的徽章）。 */
private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** 还没拿到的徽章有多淡。 */
const val RANK_LOCKED_ALPHA = 0.32f

/**
 * 第 [level] 级的徽章（1–10；0 = 还没有称号，画第一级的灰章）。[locked] = 还没拿到：去色、变淡（称号表里后面几级）。
 */
@Composable
fun RankBadge(level: Int, size: Dp, modifier: Modifier = Modifier, locked: Boolean = false) {
    val shown = level.coerceIn(1, Ranks.MAX)
    val grey = locked || level <= 0
    Image(
        painterResource(BADGES[shown - 1]),
        contentDescription = null,
        modifier = modifier.size(size).then(if (grey) Modifier.alpha(RANK_LOCKED_ALPHA) else Modifier)
            .testTag("rank-badge-$shown")
            .semantics { contentDescription = Ranks.of(shown)?.let { "第 ${it.level} 级徽章 · ${it.title}" + if (grey) "（还没拿到）" else "" }.orEmpty() },
        contentScale = ContentScale.Fit,
        colorFilter = if (grey) GREY else null,
    )
}

private val SPECIAL_BADGES: Map<String, DrawableResource> = mapOf(
    "night" to Res.drawable.special_night, "binge" to Res.drawable.special_binge, "triple" to Res.drawable.special_triple,
    "rewatch" to Res.drawable.special_rewatch, "fresh" to Res.drawable.special_fresh, "classic" to Res.drawable.special_classic,
    "anime" to Res.drawable.special_anime, "marathon" to Res.drawable.special_marathon, "highscore" to Res.drawable.special_highscore,
    "omnivore" to Res.drawable.special_omnivore, "weekends" to Res.drawable.special_weekends, "newyear" to Res.drawable.special_newyear,
    "morning" to Res.drawable.special_morning,
    "daily" to Res.drawable.special_daily,
    "seasondone" to Res.drawable.special_seasondone,
    "seasons10" to Res.drawable.special_seasons10,
    "epic" to Res.drawable.special_epic,
    "brave" to Res.drawable.special_brave,
    "horror" to Res.drawable.special_horror,
    "comedy" to Res.drawable.special_comedy,
    "docu" to Res.drawable.special_docu,
    "scifi" to Res.drawable.special_scifi,
    "detective" to Res.drawable.special_detective,
    "collector" to Res.drawable.special_collector,
    // 第三批（用户 2026-11-15：「称号太少了，再搞多一些，48个吧」）
    "noon" to Res.drawable.special_noon, "valentine" to Res.drawable.special_valentine, "christmas" to Res.drawable.special_christmas,
    "goldenweek" to Res.drawable.special_goldenweek, "halloween" to Res.drawable.special_halloween, "yearly100" to Res.drawable.special_yearly100,
    "streak30" to Res.drawable.special_streak30, "allnight" to Res.drawable.special_allnight, "festival" to Res.drawable.special_festival,
    "homebody" to Res.drawable.special_homebody, "romance" to Res.drawable.special_romance, "action" to Res.drawable.special_action,
    "war" to Res.drawable.special_war, "history" to Res.drawable.special_history, "family" to Res.drawable.special_family,
    "music" to Res.drawable.special_music, "thriller" to Res.drawable.special_thriller, "fantasy" to Res.drawable.special_fantasy,
    "variety" to Res.drawable.special_variety, "oldies" to Res.drawable.special_oldies, "masterpiece" to Res.drawable.special_masterpiece,
    "obscure" to Res.drawable.special_obscure, "rewatch5" to Res.drawable.special_rewatch5, "dusty" to Res.drawable.special_dusty,
)

/** 特别称号的盾形徽章（和圆形的等级章分开：金边、墨黑底、上沿一颗金星）。[locked] = 还没拿到：去色、变淡。 */
@Composable
fun SpecialBadge(id: String, size: Dp, modifier: Modifier = Modifier, locked: Boolean = false) {
    val res = SPECIAL_BADGES[id] ?: return
    Image(
        painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size).then(if (locked) Modifier.alpha(RANK_LOCKED_ALPHA) else Modifier)
            .testTag("special-badge-$id")
            .semantics { contentDescription = Specials.of(id)?.let { "特别称号 · ${it.title}" + if (locked) "（还没拿到）" else "" }.orEmpty() },
        contentScale = ContentScale.Fit,
        colorFilter = if (locked) GREY else null,
    )
}
