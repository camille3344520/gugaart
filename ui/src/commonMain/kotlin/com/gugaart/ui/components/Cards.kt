package com.gugaart.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.UiMode

/** 集号的三档字号（手机 28 / 21 / 16.5；电脑同；电视放大）。数值属于主题，放在这里是因为只有集号用。 */
@Composable
fun episodeNoSize(n: Int): TextUnit {
    val k = if (Guga.uiMode == UiMode.Tv) 1.6f else 1f
    return when (Rules.episodeNoTier(n)) {
        0 -> 28.sp
        1 -> 21.sp
        else -> 16.5.sp
    } * k
}

/**
 * 海报卡（海报墙 110 × 165、横向海报带 128 × 192）。直角、无投影。片名最多两行（单行省略会让三部 EVA 看起来一样），下一行年份 / 题材。
 * 海报墙（[evenTitle]）：片名占满两行的高度；片名只有一行时，空着的第二行放评分（用户 2026-11-17：「中间有一行是空的，可以用来显示评分」），
 * 两行时评分并进年份那一行的最前面。
 * 没有海报 → 斜纹空版 + 片名。已收藏 → 海报右下压一枚小印；已看 → 右上一笔校对勾。[skeleton]：加载骨架（形状 = 要出现的内容）。
 * 手机上长按 = 这一部的菜单（PhoneItemMenu：播放、收藏、已看；管理员多刷新 / 扫描 / 删除）。
 */
@Composable
fun PosterCard(
    title: Title?,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = title?.let { listOfNotNull(it.year?.toString(), it.genres.firstOrNull()).joinToString(" · ") },
    highlight: IntRange? = null,
    /** 片名永远占两行的高度（海报墙：一行里长短片名的卡片底边对齐）。 */
    evenTitle: Boolean = false,
) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    val longPress: (() -> Unit)? = if (title != null && Guga.uiMode == UiMode.Phone) ({ com.gugaart.ui.screens.PhoneItemMenu.open(title) }) else null
    Column(modifier.width(width).then(if (title != null) Modifier.pressable(press, label = title.name, onLongClick = longPress, onClick = onClick) else Modifier).testTag("poster-card")) {
        Box(Modifier.pressGraphics(press).fillMaxWidth().aspectRatio(2f / 3f)) {
            if (title == null) {
                Skeleton(Modifier.fillMaxSize())
            } else {
                CoverImage(title.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) {
                    HatchPlate(Rules.monogram(title.name, person = title.kind == Kind.Person), Modifier.fillMaxSize(), caption = title.name.takeIf { title.kind != Kind.Person })
                }
                if (title.favorite) Seal(Modifier.align(Alignment.BottomEnd).padding(5.dp), size = width * 0.2f)
                if (title.played) ProofTick(true, Modifier.align(Alignment.TopEnd), size = 13.dp, box = false)
                // 海报墙：评分是海报左上角的一枚小角标（用户 2026-11-24：片名长了会把评分挤到第三行，不优雅）
                if (evenTitle) title.rating?.let { RatingTag(it, Modifier.align(Alignment.TopStart)) }
            }
        }
        if (title == null) {
            Skeleton(Modifier.padding(top = 10.dp).fillMaxWidth(0.8f).height(14.dp))
            Skeleton(Modifier.padding(top = 6.dp).fillMaxWidth(0.5f).height(10.dp))
        } else {
            val titleStyle = Guga.song(type.caption * 1.12f, FontWeight.Bold, lineHeight = type.caption * 1.5f)
            // 海报墙：先量一下片名在这个宽度里几行（同步量，不会先错一帧再跳）
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val widthPx = with(density) { width.roundToPx() }
            val oneLine = evenTitle && remember(title.name, widthPx, titleStyle) {
                measurer.measure(title.name, titleStyle, maxLines = 2, constraints = Constraints(maxWidth = widthPx)).lineCount <= 1
            }
            BasicText(
                highlighted(title.name, highlight, colors.spot),
                Modifier.padding(top = 9.dp),
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 年份 · 题材紧跟片名（片名一行就在第二行，两行就在第三行，中间不留空行）
            val sub = subtitle?.takeIf { it.isNotEmpty() }
            if (sub != null || evenTitle) {
                BasicText(sub ?: "", Modifier.padding(top = 3.dp).testTag("poster-sub"), style = Guga.note(type.fine * 1.1f, colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 海报墙：片名只有一行时，空出来的那一行高度补在卡片最下面，一排卡片底边照样对齐
            if (oneLine) Spacer(Modifier.height(with(density) { (type.caption * 1.5f).toDp() }))
        }
    }
}

/**
 * 海报左上角的评分角标：墨纸 76 % 底、直角，「★」专色 + 斜体数字「8.0」。贴着海报角，和右上校对勾、右下收藏印不挤；
 * 任何海报（亮 / 暗）上都读得清（底够深）。
 */
@Composable
private fun RatingTag(rating: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    BasicText(
        buildAnnotatedString {
            withStyle(SpanStyle(color = colors.spot)) { append("★ ") }
            append(rating)
        },
        modifier.background(colors.paper.copy(alpha = 0.76f)).padding(horizontal = 6.dp, vertical = 2.dp).testTag("poster-rating"),
        style = Guga.numeral(type.fine * 1.15f, colors.ink),
        maxLines = 1,
    )
}

/** 命中的字上专色（搜索）。 */
fun highlighted(text: String, range: IntRange?, color: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    if (range == null || range.first < 0 || range.last >= text.length) {
        append(text)
    } else {
        append(text.substring(0, range.first))
        withStyle(SpanStyle(color = color)) { append(text.substring(range.first, range.last + 1)) }
        append(text.substring(range.last + 1))
    }
}

/**
 * 16:9 的缩略图位（列表行 104 × 58）。没有分集图 → 上游已经退到剧集的横图；连那也没有 → 带剧名的斜纹空版。
 * 进度线在下沿（0 不画）。
 */
@Composable
fun Thumb(url: String?, fallback: String, width: Dp, modifier: Modifier = Modifier, progress: Float = 0f) {
    val colors = Guga.colors
    Box(modifier.width(width).aspectRatio(16f / 9f).progressLine(progress, colors.spot, outside = false)) {
        CoverImage(url, Modifier.fillMaxSize(), Framing(0.5f, 0.35f)) { HatchPlate(fallback, Modifier.fillMaxSize(), textSize = 13.sp) }
    }
}

/**
 * 画报目录行（首页「目录」）：斜体专色编号栏 40 + 标题 / 副行 + 缩略图 104 × 58。行高 ≥ 86，行间 1 dp 细线（由列表画）。整行可点。
 */
@Composable
fun TocRow(number: Int, title: String, subtitle: String, thumbUrl: String?, thumbFallback: String, progress: Float, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Row(
        modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = title, onClick = onClick).padding(vertical = 14.dp).testTag("toc-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(Rules.episodeNo(number), Modifier.width(60.dp), style = Guga.numeral(episodeNoSize(number), colors.spot), maxLines = 1)
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            BasicText(title, style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(subtitle, Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Thumb(thumbUrl, thumbFallback, 122.dp, progress = progress)
    }
}

/**
 * 分集行：集号栏 40（**四位集号**按位数三档字号，不撑破）+ 集名 / 副行 + 缩略图 + 行尾校对格。
 * - 当前（正在看的那一集）：标题专色 + 缩略图下进度线；
 * - 已看：校对勾 + 集号朱红删除线；
 * - 没有分集图的季：缩略图位换成斜纹空版。
 * 点整行 = 播放 / 进这一集；点校对格 = 标记已看（[onTick]）。
 */
@Composable
fun EpisodeRow(
    number: Int,
    name: String,
    subtitle: String?,
    thumbUrl: String?,
    current: Boolean,
    played: Boolean,
    progress: Float,
    onClick: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Row(modifier.fillMaxWidth().heightIn(min = 86.dp).testTag(if (current) "episode-row-current" else "episode-row"), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).pressGraphics(press).pressable(press, label = "第 $number 集 $name", onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 编号栏固定 40 dp：1089 用最小一档字号也放得下
            Box(Modifier.width(46.dp)) {
                BasicText(
                    number.toString(),
                    style = Guga.numeral(episodeNoSize(number), if (current) colors.spot else colors.dim).copy(
                        textDecoration = TextDecoration.None,
                    ),
                    modifier = if (played) Modifier.drawBehind { drawLine(colors.seal, Offset(-2.dp.toPx(), size.height * 0.56f), Offset(size.width + 2.dp.toPx(), size.height * 0.5f), 1.5.dp.toPx()) } else Modifier,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                BasicText(name, style = Guga.song(type.row, FontWeight.Bold, if (current) colors.spot else colors.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrEmpty()) BasicText(subtitle, Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Thumb(thumbUrl, Rules.episodeNo(number), 104.dp, progress = if (current) progress else 0f)
        }
        val tick = rememberPressState()
        Box(Modifier.width(38.dp).height(48.dp).pressable(tick, label = if (played) "改成未看" else "标记已看", onClick = onTick), contentAlignment = Alignment.CenterEnd) {
            ProofTick(played)
        }
    }
}

/**
 * 演员格：方形照片（直角、**彩色**）+ 名字 + 角色，各单行省略（电视两行）。没有照片 → 姓氏的斜纹空版。
 */
@Composable
fun PersonChip(person: Person, width: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    val lines = if (Guga.uiMode == UiMode.Tv) 2 else 1
    Column(modifier.width(width).pressable(press, label = person.name, onClick = onClick).testTag("person-chip")) {
        Box(Modifier.pressGraphics(press).size(width)) {
            CoverImage(person.photo, Modifier.fillMaxSize(), Framing(0.5f, 0.22f)) { HatchPlate(Rules.monogram(person.name, person = true), Modifier.fillMaxSize()) }
        }
        BasicText(person.name, Modifier.padding(top = 8.dp), style = Guga.song(type.caption * 1.08f, FontWeight.Bold), maxLines = lines, overflow = TextOverflow.Ellipsis)
        person.role?.let { BasicText(it, Modifier.padding(top = 2.dp), style = Guga.song(type.fine * 1.1f, color = colors.faint), maxLines = lines, overflow = TextOverflow.Ellipsis) }
    }
}

/** 骨架块：1.2 s 的明暗呼吸（docs/design-language.md §8）。形状由调用方给。 */
@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val reduced = com.gugugaga.ds.physics.LocalMotionPrefs.current.reducedMotion
    val a = if (reduced) 0.5f else {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton")
        t.animateFloat(0.35f, 0.8f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(600), androidx.compose.animation.core.RepeatMode.Reverse), label = "breath").value
    }
    Box(modifier.drawBehind {
        drawRect(colors.line.copy(alpha = colors.line.alpha * a))
        hatch(colors.line.copy(alpha = 0.06f), 7.dp.toPx(), 1.dp.toPx())
    }.testTag("skeleton"))
}

/** 选季「卷册」的一格（手机 6 列）：斜体季号 + 集数。当前季 = 米白实底墨字；正在看的季右上一个朱红点。 */
@Composable
fun VolumeCell(label: String, sub: String?, selected: Boolean, watching: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, latin: Boolean = true) {
    val colors = Guga.colors
    val press = rememberPressState()
    Box(
        modifier
            .pressGraphics(press)
            .heightIn(min = 52.dp)
            .then(if (selected) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.line))
            .pressable(press, label = label, onClick = onClick)
            .testTag(if (selected) "volume-selected" else "volume"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)) {
            val ink = if (selected) colors.onInk else colors.ink
            if (latin) BasicText(label, style = Guga.numeral(19.sp, ink), maxLines = 1)
            else BasicText(label, style = Guga.song(11.sp, FontWeight.Bold, ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) BasicText(sub, style = Guga.song(9.5.sp, color = if (selected) colors.onInk.copy(alpha = 0.7f) else colors.faint), maxLines = 1)
        }
        if (watching) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(5.dp).background(colors.seal, androidx.compose.foundation.shape.CircleShape))
    }
}

/** 一条横向的可选小标签（集号分页「1–50」、筛选项）：选中 = 反白格子。 */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val press = rememberPressState()
    Box(
        modifier
            .pressGraphics(press)
            .heightIn(min = 34.dp)
            .then(if (selected) Modifier.background(colors.ink) else Modifier.border(1.dp, colors.line))
            .pressable(press, label = text, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = Guga.song(12.5.sp, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) colors.onInk else colors.ink), maxLines = 1)
    }
    Spacer(Modifier.width(0.dp))
}
