package com.gugaart.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.gugaart.ui.data.Rules
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.GugaFonts
import com.gugugaga.app.data.rememberImage
import com.gugugaga.ds.theme.UiMode

/**
 * 片名（docs/components.md · TitleLogo，docs/data-edge-cases.md §1）。四种情况：
 * 1. 有 clearlogo → logo 原图，只缩放；
 * 2. 黑笔画的 logo（[isDarkLogo]）→ 垫一张米白衬纸（电视略微倾斜，像剪报）；
 * 3. 没有 logo → 宋体 900 片名，按字数四档缩小，最长的一档允许折两行；英文长名用斜体衬线、缩一号；
 * 4. logo 还没到 → 先出宋体片名，logo 解码完交叉淡入（不跳版：两者占同一个盒子）。
 *
 * [width] × [height] 是留给片名的盒子；[alignX]：0 = 靠左（详情页），0.5 = 居中（封面）。
 */
@Composable
fun TitleLogo(
    name: String,
    logoUrl: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    alignX: Float = 0f,
    baseSize: TextUnit = Guga.type.title,
    /** 测试 / 截图用：不加载 logo，直接看宋体片名。 */
    textOnly: Boolean = false,
) {
    val colors = Guga.colors
    val logo = if (textOnly) null else rememberImage(logoUrl, keepPrevious = false)
    val dark = remember(logo) { logo?.isDarkLogo() == true }
    val logoAlpha by animateFloatAsState(if (logo != null) 1f else 0f, tween(260), label = "logo")
    Box(
        modifier.width(width).height(height).semantics { heading(); contentDescription = name }.testTag(if (logo == null) "title-text" else if (dark) "title-logo-backed" else "title-logo"),
        contentAlignment = if (alignX < 0.25f) Alignment.CenterStart else Alignment.Center,
    ) {
        if (logoAlpha < 1f) {
            val tier = Rules.titleTier(name)
            val latin = Rules.isLatinTitle(name)
            val size = baseSize * Rules.TITLE_SCALE[tier] * (if (latin) 0.86f else 1f)
            BasicText(
                name,
                Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - logoAlpha },
                style = Guga.song(size, FontWeight.Black, colors.ink, lineHeight = size * 1.18f).copy(
                    textAlign = if (alignX < 0.25f) TextAlign.Start else TextAlign.Center,
                    fontStyle = if (latin) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = if (latin) GugaFonts.numeral else GugaFonts.song,
                ),
                // 标题不被裁掉字形：最长的一档折两行；再长才省略
                maxLines = if (tier >= 2) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (logo != null) {
            val tv = Guga.uiMode == UiMode.Tv
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = logoAlpha
                        if (dark && tv) rotationZ = -1.6f
                    },
                contentAlignment = if (alignX < 0.25f) Alignment.CenterStart else Alignment.Center,
            ) {
                if (dark) {
                    // 衬纸：按 logo 的比例收紧，四周留一圈纸边
                    val ratio = logo.width.toFloat() / logo.height
                    val pad = 8.dp
                    val boxH = height
                    val paperW = minOf(width, (boxH - pad * 2) * ratio + pad * 2)
                    Box(Modifier.width(paperW).height(boxH).background(colors.ink).padding(pad)) {
                        Canvas(Modifier.matchParentSize()) { drawFit(logo, 0.5f, 0.5f) }
                    }
                } else {
                    Canvas(Modifier.matchParentSize()) { drawFit(logo, alignX, 0.5f) }
                }
            }
        }
    }
}
