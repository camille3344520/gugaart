package com.gugaart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.UiMode

/** 一条通栏线：细线（米白 18 %）或主线（米白实线，电视 2 dp）。 */
@Composable
fun Rule(modifier: Modifier = Modifier, strong: Boolean = false) {
    val colors = Guga.colors
    val dimens = Guga.dimens
    Box(modifier.fillMaxWidth().height(if (strong) dimens.ruleStrong else dimens.ruleHair).background(if (strong) colors.ink else colors.line))
}

/** 栏目行：专色小字、字距 0.3 em，「悬疑 · 喜剧 · 2024 · 全 40 集」。最多 4 段（多的在数据层已经截掉）。 */
@Composable
fun Kicker(parts: List<String>, modifier: Modifier = Modifier) {
    if (parts.isEmpty()) return
    BasicText(parts.take(4).joinToString(" · "), modifier, style = Guga.kicker(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * 区块标题：〔斜体编号〕宋体标题 + 专色斜体英文小标 + 通栏细线 + 右端斜体小计。
 * 英文小标是装饰，不承担信息（读屏只读标题）。[number] 手机不用，电脑 / 电视的区块带编号。
 */
@Composable
fun SectionHeader(title: String, english: String, modifier: Modifier = Modifier, number: String? = null, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val colors = Guga.colors
    val type = Guga.type
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        if (number != null) {
            BasicText(number, Modifier.padding(end = 10.dp), style = Guga.numeral(type.section, colors.spot))
        }
        BasicText(title, style = Guga.song(type.section, FontWeight.Black), maxLines = 1)
        Spacer(Modifier.width(12.dp))
        BasicText(english, Modifier.padding(bottom = 3.dp), style = Guga.numeral(type.kicker * 1.05f, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
        Box(Modifier.weight(1f).padding(horizontal = 12.dp).padding(bottom = 8.dp).height(Guga.dimens.ruleHair).background(colors.line))
        if (trailing != null) {
            val press = rememberPressState()
            BasicText(
                trailing,
                Modifier.padding(bottom = 3.dp).then(if (onTrailing != null) Modifier.pressGraphics(press).pressable(press, label = trailing, onClick = onTrailing) else Modifier),
                style = Guga.note(type.caption, colors.dim),
                maxLines = 1,
            )
        }
    }
}

/** 进度线：2 dp 专色，画在宿主的下沿外侧。进度 0 **不画**（由调用方写「未看」）。 */
fun Modifier.progressLine(progress: Float, color: Color, track: Color? = null, thickness: Dp = 2.dp, outside: Boolean = true): Modifier = drawWithContent {
    drawContent()
    val t = thickness.toPx()
    val y = if (outside) size.height + t / 2 else size.height - t / 2
    if (track != null) drawLine(track, Offset(0f, y), Offset(size.width, y), t)
    if (progress > 0f) drawLine(color, Offset(0f, y), Offset(size.width * progress.coerceIn(0f, 1f), y), t)
}

/**
 * 主按钮：米白实底、墨字、直角；下沿可带专色进度线。按下 = 放大（上游按压物理）。
 * 电视：默认是米白线框，焦点才变实底（[focused] 由调用方给；手机 / 电脑不用）。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    icon: GuguIcon? = GuguIcon.Play,
    trailing: String? = null,
    enabled: Boolean = true,
) {
    val colors = Guga.colors
    val press = rememberPressState()
    val tv = Guga.uiMode == UiMode.Tv
    val solid = !tv || press.focused
    Row(
        modifier
            .pressGraphics(press)
            .heightIn(min = if (tv) 64.dp else 46.dp)
            .then(if (solid) Modifier.background(if (enabled) colors.ink else colors.faint) else Modifier.border(Guga.dimens.ruleStrong, colors.ink))
            .progressLine(progress, colors.spot)
            .pressable(press, enabled = enabled, label = text, onClick = onClick)
            .padding(horizontal = if (tv) 28.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (solid) colors.onInk else colors.ink
        if (icon != null) {
            Icon(icon, ink, if (tv) 22.dp else 14.dp, filled = icon == GuguIcon.Play)
            Spacer(Modifier.width(if (tv) 14.dp else 12.dp))
        }
        BasicText(text, Modifier.weight(1f, fill = trailing != null), style = Guga.song(Guga.type.lead, FontWeight.Bold, ink, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            BasicText(trailing, style = Guga.note(Guga.type.caption, ink.copy(alpha = 0.72f)), maxLines = 1)
        }
    }
}

/** 文字链接：宋体 700 + 下划细线。可带一个小图标（「♡ 收藏」「✓ 整季已看」）。 */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: GuguIcon? = null, color: Color = Guga.colors.ink) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        modifier
            .pressGraphics(press)
            .heightIn(min = 40.dp)
            .pressable(press, label = text, onClick = onClick)
            .drawBehind { drawLine(colors.line, Offset(0f, size.height - 6.dp.toPx()), Offset(size.width, size.height - 6.dp.toPx()), 1.dp.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, color, 14.dp)
        BasicText(text, style = Guga.song(14.sp, FontWeight.Bold, color), maxLines = 1)
    }
}

/**
 * 方框按钮：40 × 40、1 dp 米白线；压在图上时加 52 % 墨底（[onImage]）。触控热区 48（外圈留白算在布局里）。
 * 返回、收藏、更多。[selected]：收藏了 = 实心爱心（朱红），不靠边框颜色单独表达。
 */
@Composable
fun BoxButton(icon: GuguIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, onImage: Boolean = false, selected: Boolean = false) {
    val colors = Guga.colors
    val press = rememberPressState()
    Box(modifier.size(48.dp).pressable(press, label = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .pressGraphics(press)
                .size(40.dp)
                .then(if (onImage) Modifier.background(colors.inkWell) else Modifier)
                .border(1.dp, if (onImage) colors.ink.copy(alpha = 0.5f) else colors.line),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, if (selected) colors.seal else colors.ink, 18.dp, filled = selected)
        }
    }
}

/**
 * 细斜纹：斜纹空版的底。45° 的线（x + y = k × [gap]），每 [gap] 一条。
 *
 * 画法（1.5.1 电视性能，批次 tvflip）：先把一格 [gap] × [gap] 的「砖」（一条对角线）画成小图，再用重复的图像着色器铺满——一次贴图。
 * 以前是一根一根画线：一张 736 × 1104 的海报空版要画 130 多根抗锯齿斜线；海报流里的空版还带着 3D 倾斜，Mali-G52 上这些线全走路径，
 * 一帧 GPU 95 ms（真电视实测）。砖的边长取整到像素，线距和以前差不到半个像素。
 */
fun DrawScope.hatch(color: Color, gap: Float, stroke: Float) {
    drawRect(HatchTiles.brush(color, gap, stroke))
}

/** 斜纹砖的缓存（界面线程上画的时候才用）：颜色、边长、线宽一样就复用同一张小图和着色器。 */
private object HatchTiles {
    private val cache = HashMap<Triple<ULong, Int, Int>, androidx.compose.ui.graphics.ShaderBrush>()

    fun brush(color: Color, gap: Float, stroke: Float): androidx.compose.ui.graphics.ShaderBrush {
        val t = kotlin.math.max(2, kotlin.math.round(gap).toInt())
        val key = Triple(color.value, t, kotlin.math.round(stroke * 100).toInt())
        return cache.getOrPut(key) {
            val tile = tile(color, t, stroke)
            androidx.compose.ui.graphics.ShaderBrush(androidx.compose.ui.graphics.ImageShader(tile, androidx.compose.ui.graphics.TileMode.Repeated, androidx.compose.ui.graphics.TileMode.Repeated))
        }
    }

    /** 一格砖：x + y = t 那条对角线，加上两个角上属于 x + y = 0 / 2t 的那一小截（铺开以后线是连着的）。 */
    fun tile(color: Color, t: Int, stroke: Float): androidx.compose.ui.graphics.ImageBitmap {
        val img = androidx.compose.ui.graphics.ImageBitmap(t, t)
        val f = t.toFloat()
        val e = stroke * 2
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(img), Size(f, f),
        ) {
            drawLine(color, Offset(-e, f + e), Offset(f + e, -e), stroke)
            drawLine(color, Offset(-e, e), Offset(e, -e), stroke)
            drawLine(color, Offset(f - e, f + e), Offset(f + e, f - e), stroke)
        }
        return img
    }
}

/**
 * 斜纹空版：没有照片的演员、没有任何图的条目（docs/design-language.md §5）。细斜纹 + 细边 + 宋体的姓氏 / 剧名。不用灰色人形图标。
 * [text] 已经是要印的字（`Rules.monogram`）；[caption] 是下面一行小字（剧名全称），可空。
 */
@Composable
fun HatchPlate(text: String, modifier: Modifier = Modifier, caption: String? = null, textSize: androidx.compose.ui.unit.TextUnit = Guga.type.section) {
    val colors = Guga.colors
    Box(
        modifier
            .clipToBounds()
            .drawBehind {
                hatch(colors.line.copy(alpha = 0.10f), 7.dp.toPx(), 1.dp.toPx())
                drawRect(colors.line, size = Size(size.width, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            }
            .testTag("hatch-plate"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
            BasicText(text, style = Guga.song(textSize, FontWeight.Black, colors.dim), maxLines = 1)
            if (caption != null) BasicText(caption, Modifier.padding(top = 4.dp), style = Guga.song(Guga.type.fine, color = colors.faint), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 选中记号：实心小方块（单选）。选中不靠颜色单独表达：没选 = 空心方框。 */
@Composable
fun RadioMark(selected: Boolean, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    val colors = Guga.colors
    Box(modifier.size(size).then(if (selected) Modifier.background(colors.spot) else Modifier.border(1.dp, colors.dim)))
}

/**
 * 细框小签（「精确匹配」「推荐」「另一份文件」）：[color] 的细框 + 同色字，不垫底色（专色不当底色）。
 * [size]：字号（手机 11、电视 20……，跟着所在那一行）；[line]：框线粗细（电视 2）。
 */
@Composable
fun OutlineTag(text: String, size: androidx.compose.ui.unit.TextUnit, line: Dp, color: Color, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier.border(line, color).padding(horizontal = 6.dp * (size.value / 11f), vertical = 1.dp * (size.value / 11f)),
        style = Guga.song(size, FontWeight.Bold, color, letterSpacing = 0.08.em),
        maxLines = 1,
        softWrap = false,
    )
}

/** 压在整幅图上的一层：顶部暗角 + 自定内容。 */
@Composable
fun Stack(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) = Box(modifier.fillMaxSize(), content = content)

/** 一行里靠右的斜体小字（计数、「118 部」）。 */
@Composable
fun RowScope.Tally(text: String) {
    Spacer(Modifier.weight(1f))
    BasicText(text, style = Guga.note(Guga.type.caption, Guga.colors.dim), maxLines = 1)
}
