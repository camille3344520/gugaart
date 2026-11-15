package com.gugaart.ui.screens.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.Kicker
import com.gugaart.ui.components.PersonChip
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.data.Fold
import com.gugaart.ui.data.Person
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon

/** 手机详情页折叠简介的门槛（字数）和折成几行（docs/data-edge-cases.md §3）。 */
const val PHONE_FOLD_CHARS = 170
const val PHONE_FOLD_LINES = 6

/** 首字下沉占几行。 */
private const val DROP_LINES = 2

/**
 * 压在整幅图渐隐处的片名区：栏目行 → 片名（logo / 衬纸 / 宋体）+ 右边的大号斜体评分和一行小字。
 * 没有评分 → 右边不占位，片名用满整行。
 */
@Composable
fun TitleBlock(title: Title, kicker: List<String>, ratingNote: String?, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    val textOnly = LocalPreset.current.textTitles
    Column(modifier.fillMaxWidth()) {
        Kicker(kicker)
        Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            TitleLogo(title.name, title.art.logo, width = if (title.rating != null) 230.dp else 364.dp, height = 84.dp, alignX = 0f, textOnly = textOnly)
            if (title.rating != null) {
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    // 满分写「10」，比别的评分宽一位：右对齐，不挤片名
                    BasicText(title.rating, style = Guga.numeral(type.issue * 0.9f), maxLines = 1, modifier = Modifier.testTag("rating"))
                    if (ratingNote != null) BasicText(ratingNote, style = Guga.song(type.fine * 1.15f, color = colors.dim), maxLines = 1)
                }
            } else if (ratingNote != null) {
                Spacer(Modifier.weight(1f))
                BasicText(ratingNote, style = Guga.song(type.fine * 1.15f, color = colors.dim), maxLines = 1)
            }
        }
    }
}

/** 主线下面的一行资料：「主演 罗云熙 宋轶 ……… 全 40 集」。左边放不下单行省略。 */
@Composable
fun FactsRow(leftLabel: String?, left: String?, rightLabel: String?, right: String?, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val type = Guga.type
    Column(modifier) {
        Rule(strong = true)
        // 两边都没有内容（没有演职员、没有集数）：只留一条主线，不排一行空的
        if (left == null && right == null) return@Column
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (left != null) {
                if (leftLabel != null) BasicText("$leftLabel  ", style = Guga.song(type.caption, color = colors.dim), maxLines = 1)
                BasicText(left, Modifier.weight(1f), style = Guga.song(type.caption, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (right != null) {
                if (rightLabel != null) BasicText("$rightLabel  ", Modifier.padding(start = 12.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1)
                BasicText(right, style = Guga.song(type.caption, FontWeight.Bold), maxLines = 1)
            }
        }
        Rule()
    }
}

/** 收藏（+ 印章位）和「已看」两个文字链接。印章落在收藏旁边预留的虚线格里。 */
@Composable
fun ActionsRow(favorite: Boolean, onFavorite: () -> Unit, playedLabel: String, played: Boolean, onPlayed: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextLink("收藏", onFavorite, icon = GuguIcon.Heart)
        SealSlot(favorite)
        Spacer(Modifier.weight(1f))
        TextLink(if (played) "改成未看" else playedLabel, onPlayed, icon = GuguIcon.Check)
    }
}

/** 首字下沉占正文开头的几个字符：开头的前引号 / 前括号 / 书名号（最多 2 个）连同后面的第一个字。纯函数，有单测。 */
fun dropCapLength(text: String): Int {
    if (text.isEmpty()) return 0
    var i = 0
    while (i < 2 && i < text.length - 1 && text[i] in "《「『“‘（〈【(\"'") i++
    return i + 1
}

/**
 * 简介：首字下沉（专色的宋体大字，占两行）+ 正文。超过 [foldChars] 字折成整 [foldLines] 行 +「展开全文（N 字）」；
 * 不超过就不出现「展开」；没有简介这一栏不出现（不写「暂无简介」）。渐隐带里不露半行字：折的是整行数。
 */
@Composable
fun Overview(fold: Fold, modifier: Modifier = Modifier, foldLines: Int = PHONE_FOLD_LINES) {
    if (fold.text.isEmpty()) return
    val colors = Guga.colors
    val type = Guga.type
    var open by remember(fold.text) { mutableStateOf(false) }
    // 首字旁边那几行排到哪个字为止（第一次排完才知道）
    var split by remember(fold.text) { mutableIntStateOf(-1) }
    // 首字 = 第一个「字」；开头是前引号 / 书名号（「《复仇者联盟》系列…」）时把它们缩小带在首字前面，不让一个括号占两行
    val capLen = dropCapLength(fold.text)
    val body = fold.text.substring(capLen)
    val style = Guga.song(type.lead, lineHeight = type.leadLine)
    Column(modifier.fillMaxWidth().testTag(if (fold.folded && !open) "overview-folded" else "overview")) {
        Row(verticalAlignment = Alignment.Top) {
            // 首字：字高 ≈ 两行正文
            val capSize = type.leadLine * (DROP_LINES * 0.86f)
            BasicText(
                androidx.compose.ui.text.buildAnnotatedString {
                    if (capLen > 1) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = capSize * 0.5f))
                        append(fold.text.substring(0, capLen - 1))
                        pop()
                    }
                    append(fold.text.substring(capLen - 1, capLen))
                },
                Modifier.padding(end = 10.dp).height(with(LocalDensity.current) { (type.leadLine * DROP_LINES.toFloat()).toDp() }).wrapContentHeight(Alignment.CenterVertically, unbounded = true),
                style = Guga.song(capSize, FontWeight.Black, colors.spot),
                maxLines = 1,
            )
            BasicText(
                if (split < 0) body else body.substring(0, split),
                Modifier.weight(1f),
                style = style,
                maxLines = DROP_LINES,
                onTextLayout = { r ->
                    if (split < 0) {
                        val last = minOf(DROP_LINES, r.lineCount) - 1
                        split = r.getLineEnd(last, visibleEnd = false).coerceIn(0, body.length)
                    }
                },
            )
        }
        val rest = if (split in 0 until body.length) body.substring(split) else ""
        if (rest.isNotEmpty()) {
            BasicText(rest, style = style, maxLines = if (fold.folded && !open) foldLines - DROP_LINES else Int.MAX_VALUE, overflow = TextOverflow.Clip)
        }
        if (fold.folded) {
            TextLink(if (open) "收起" else "展开全文（${fold.chars} 字）", { open = !open }, Modifier.padding(top = 2.dp), color = colors.dim)
        }
    }
}

/** 演职员一带：方形照片 + 名字 + 角色，横滑；最后一张露半张。没有演职员 → 这一栏不出现。 */
@Composable
fun CastBand(people: List<Person>, onOpen: (Person) -> Unit, modifier: Modifier = Modifier) {
    if (people.isEmpty()) return
    val dimens = Guga.dimens
    Column(modifier) {
        SectionHeader("演职员", "CAST", Modifier.padding(horizontal = dimens.margin), trailing = "${people.size} 人")
        Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = dimens.margin), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            for (p in people) PersonChip(p, 92.dp, { onOpen(p) })
        }
    }
}

/** 一行「标签 + 内容」的小表（音轨 / 字幕 / 版本）。 */
@Composable
fun StreamList(title: String, english: String, rows: List<StreamRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val colors = Guga.colors
    val type = Guga.type
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title, english, trailing = "${rows.size}")
        for (r in rows) {
            Column(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(vertical = 9.dp)) {
                BasicText(r.name, style = Guga.song(type.row * 0.92f, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (r.note.isNotEmpty()) BasicText(r.note, Modifier.padding(top = 3.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Rule()
        }
    }
    Box(Modifier.width(0.dp))
}
