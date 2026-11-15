package com.gugaart.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.res.Res
import com.gugaart.ui.res.pose_g03
import com.gugaart.ui.theme.Guga
import org.jetbrains.compose.resources.painterResource

/** 报头徽记直径 ÷ 字高（design/source/shared-masthead.md）。 */
const val EMBLEM_PER_CAP = 1.15f

/** 报头四个字的字距。 */
const val MASTHEAD_TRACKING_EM = 0.34f

/**
 * 三端统一的报头（docs/design-language.md §4，定稿 = design/source/shared-masthead.md）：
 *
 *     ──────────────────────────────────────────  上线：1 dp 米白实线（电视 2 dp）
 *     VOL. 318        咕 咕 ◉ 嘎 嘎        十一月十四日
 *     ──────────────────────────────────────────  下线：细线（米白 18 %）
 *
 * - 左 [volume]：Georgia 斜体；中：宋体 900、字距 0.34 em，正中嵌圆形徽记（G03：戴企鹅帽的她的笑脸；直径 = 字高 × 1.15，外圈 1 dp 米白线）；右 [date]：宋体。
 * - 标签页（发现 / 片库 / 我的）沿用同一条报头的排法，中间换成页名：传 [title]。
 * - [trailing]：右端日期之后的东西 —— 电视的时钟（电视没有状态栏）、电脑的窗口键。报头自己不知道平台。
 * - 报头不是玻璃：它压在整幅剧照上时是透明的（靠剧照顶部的暗角读得清），滚动后由页面给它垫实心纸色。
 */
@Composable
fun Masthead(
    volume: String,
    date: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** 期号是数字时给这个：「付印新刊」（刷新）后期号 +1，数字像里程表一样滚上去，不是直接换。给了它就不用 [volume] 的字。 */
    issue: Int? = null,
    /** 页名版左端的英文小标（「LIBRARY」「INDEX」）；完整报头不用。 */
    leading: String? = null,
    /** 右端日期那一格换成别的（首页 = 称号，见 `screens/me/RankChip`）；参数是日期本来的字样式。 */
    dateSlot: (@Composable (androidx.compose.ui.text.TextStyle) -> Unit)? = null,
) {
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    val strong = with(LocalDensity.current) { dimens.ruleStrong.toPx() }
    val hair = with(LocalDensity.current) { dimens.ruleHair.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(dimens.mastheadHeight)
            .drawBehind {
                drawLine(colors.ink, Offset(0f, strong / 2), Offset(size.width, strong / 2), strong)
                drawLine(colors.line, Offset(0f, size.height - hair / 2), Offset(size.width, size.height - hair / 2), hair)
            },
    ) {
        val side = Guga.numeral(type.kicker, colors.dim, letterSpacing = 0.14.em)
        if (issue != null && leading == null) {
            val rolled by animateFloatAsState(issue.toFloat(), tween(900), label = "volume")
            Row(Modifier.align(Alignment.CenterStart).testTag("masthead-volume"), verticalAlignment = Alignment.CenterVertically) {
                BasicText("VOL. ", style = side, maxLines = 1)
                RollingNumber(rolled, side, minDigits = 1)
            }
        } else {
            BasicText(leading ?: volume, Modifier.align(Alignment.CenterStart), style = side, maxLines = 1)
        }
        Row(
            Modifier.align(Alignment.Center).semantics(mergeDescendants = true) { heading(); contentDescription = title ?: "咕咕嘎嘎" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val word = Guga.song(type.masthead, FontWeight.Black, colors.ink, letterSpacing = MASTHEAD_TRACKING_EM.em)
            if (title != null) {
                BasicText(title, style = word, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                // 字高 ≈ 字号；徽记直径 = 字高 × 1.15
                val emblem = with(LocalDensity.current) { (type.masthead.toPx() * EMBLEM_PER_CAP).toDp() }
                BasicText("咕咕", style = word, maxLines = 1)
                Image(
                    painterResource(Res.drawable.pose_g03),
                    contentDescription = null,
                    modifier = Modifier.size(emblem).clip(CircleShape).border(1.dp, colors.ink, CircleShape),
                )
                // 字距加在每个字的右边：徽记右侧补同样的一格，让四个字以徽记为中心对称
                Spacer(Modifier.width(with(LocalDensity.current) { (type.masthead.toPx() * MASTHEAD_TRACKING_EM).toDp() }))
                BasicText("嘎嘎", style = word, maxLines = 1)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimens.gutter / 2)) {
            val dateStyle = Guga.song(type.kicker, FontWeight.Normal, colors.dim, letterSpacing = 0.14.em)
            if (dateSlot != null) dateSlot(dateStyle) else BasicText(date, style = dateStyle, maxLines = 1)
            trailing?.invoke()
        }
    }
}

private val CN_DIGITS = charArrayOf('〇', '一', '二', '三', '四', '五', '六', '七', '八', '九')

/** 1..99 → 一、十、十一、二十、三十一（报头日期用的写法）。 */
fun cnNumber(n: Int): String {
    require(n in 1..99) { "cnNumber 只管 1..99，得到 $n" }
    val tens = n / 10
    val ones = n % 10
    return buildString {
        if (tens > 1) append(CN_DIGITS[tens])
        if (tens >= 1) append('十')
        if (ones > 0) append(CN_DIGITS[ones])
    }
}

/** 报头右端的日期：「十一月十四日」。 */
fun cnMonthDay(month: Int, day: Int): String = "${cnNumber(month)}月${cnNumber(day)}日"

/** 公历：自 1970-01-01 起的天数 → (年, 月, 日)。Howard Hinnant 的 civil_from_days，纯整数。 */
fun civilFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
    return Triple(y, m, d)
}

/** 本地时间的「几月几日」。[utcOffsetMs] 用上游的 `localUtcOffsetMs(epochMs)`。 */
fun cnMonthDayOf(epochMs: Long, utcOffsetMs: Long): String {
    val (_, m, d) = civilFromEpochDay((epochMs + utcOffsetMs).floorDiv(86_400_000L))
    return cnMonthDay(m, d)
}
