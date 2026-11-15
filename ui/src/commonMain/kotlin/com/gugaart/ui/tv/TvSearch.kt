package com.gugaart.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.highlighted
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Pinyin
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.search.TitleHit
import com.gugaart.ui.screens.search.loadSearchIndex
import com.gugaart.ui.screens.search.searchTitles
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.RemoteKey
import com.gugugaga.ds.focus.remoteKeyOf
import com.gugugaga.ds.icons.GuguIcon

/** 搜索的版式数（设计稿 `tv/css/pages.css` 的 `.sr-l` `.kb` `.key` `.sr-r`）。 */
object TvSearchSpec {
    /** 左页：x 136、宽 656；输入行高 116；字母盘 6 列，键 100 × 80，间距 11。 */
    val LEFT_W = 656.dp
    val INPUT_H = 116.dp
    const val COLUMNS = 6
    val KEY_W = 100.dp
    val KEY_H = 80.dp
    val KEY_GAP = 11.dp
    /** 右页：x 880 起。结果行高 128，海报 66 × 99。 */
    val RIGHT_X = 880.dp
    val ROW_H = 128.dp
    /** 查询词最长 12 个字符。 */
    const val MAX_QUERY = 12
    /** 右页最多排几行（一屏放得下，不滚）。 */
    const val MAX_ROWS = 6
}

/** 字母盘上的键：字母 / 数字，加最后一行的 删除、清空、空格、换盘。 */
fun tvSearchKeys(digits: Boolean): List<String> =
    (if (digits) "1234567890".map { it.toString() } else ('A'..'Z').map { it.toString() }) + listOf("del", "clr", "space", "mode")

/** 按了一个键之后的查询词。纯函数，有单测。 */
fun tvSearchType(query: String, key: String): String = when (key) {
    "del" -> query.dropLast(1)
    "clr" -> ""
    "space" -> if (query.isEmpty() || query.endsWith(" ") || query.length >= TvSearchSpec.MAX_QUERY) query else "$query "
    "mode" -> query
    else -> if (query.length >= TvSearchSpec.MAX_QUERY) query else query + key
}

/**
 * 搜索（`p=search`）：左页是**拼音首字母字母盘**（6 列；四个方向走键，确定 = 输入；删除 / 清空 / 空格 / 123 在最后一行；「123」换成数字盘），
 * 右页每按一个键重排一次，命中的字上专色。最右一列再按右 = 进结果；结果里按左 = 回字母盘原来的键。**不弹系统输入法。**
 * 匹配复用手机的 `searchTitles`（本地索引 + `data/Pinyin.kt`）。「用手机输入 / 扫码」这一阶段不做（decisions Q8）。
 */
@Composable
fun TvSearchScreen(q: String?, today: Today) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val preset = LocalPreset.current
    val chrome = LocalChrome.current
    val hour = LocalHour.current
    LaunchedEffect(Unit) { chrome.spot = null }
    val index = rememberLoad("tv-search-index") { loadSearchIndex(it) }
    var query by remember { mutableStateOf((preset.query ?: q).orEmpty().uppercase().take(TvSearchSpec.MAX_QUERY)) }
    var digits by remember { mutableStateOf(false) }
    var lastKey by remember { mutableStateOf("key-A") }
    val titles = index.value?.titles
    val hits = remember(titles, query) { titles?.let { searchTitles(it, query.trim(), TvSearchSpec.MAX_ROWS) }.orEmpty() }
    // 结果里按左：回字母盘原来的键
    val backToKeys = Modifier.onKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && remoteKeyOf(e.key) == RemoteKey.Left) g.focus.request(lastKey) else false
    }

    Column(Modifier.fillMaxSize().testTag("tv-search")) {
        TvPageHeader("搜索", "SEARCH", today, trailing = index.value?.let { "在 ${it.total} 部里找" })
        Box(Modifier.fillMaxSize()) {
            // ---- 左页：输入行 + 字母盘 ----
            Column(Modifier.padding(start = TvSpec.LEFT, top = 26.dp).width(TvSearchSpec.LEFT_W)) {
                Row(
                    Modifier.fillMaxWidth().height(TvSearchSpec.INPUT_H).drawBehind { drawLine(colors.ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }.testTag("tv-search-input"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText(query, style = Guga.numeral(76.sp, colors.ink, letterSpacing = 0.08.em), maxLines = 1)
                    Box(Modifier.size(5.dp, 70.dp).background(colors.seal))
                    if (query.isEmpty()) BasicText("拼音首字母 · 片名里的字", Modifier.padding(start = 14.dp), style = Guga.song(25.sp, color = colors.faint), maxLines = 1)
                }
                Column(Modifier.padding(top = 34.dp).testTag("tv-search-keys"), verticalArrangement = Arrangement.spacedBy(TvSearchSpec.KEY_GAP)) {
                    for (row in tvSearchKeys(digits).chunked(TvSearchSpec.COLUMNS)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(TvSearchSpec.KEY_GAP)) {
                            for (k in row) {
                                val fk = "key-$k"
                                TvFocusable(
                                    onClick = { if (k == "mode") digits = !digits else query = tvSearchType(query, k) },
                                    modifier = Modifier.size(TvSearchSpec.KEY_W, TvSearchSpec.KEY_H),
                                    kind = TvFocusKind.Chip,
                                    key = fk,
                                    default = k == "A",
                                    label = k,
                                    contentAlignment = Alignment.Center,
                                    onFocus = { lastKey = fk },
                                ) { focused ->
                                    val ink = if (focused) colors.onInk else colors.ink
                                    Box(Modifier.fillMaxSize().then(if (focused) Modifier else Modifier.border(1.5.dp, colors.line)), contentAlignment = Alignment.Center) {
                                        when (k) {
                                            "del" -> BasicText("‹ 删除", style = Guga.song(23.sp, FontWeight.Bold, ink), maxLines = 1)
                                            "clr" -> BasicText("清空", style = Guga.song(23.sp, FontWeight.Bold, ink), maxLines = 1)
                                            "space" -> BasicText("空格", style = Guga.song(23.sp, FontWeight.Bold, ink), maxLines = 1)
                                            "mode" -> BasicText(if (digits) "ABC" else "123", style = Guga.song(23.sp, FontWeight.Bold, ink), maxLines = 1)
                                            else -> BasicText(k, style = Guga.numeral(38.sp, ink).copy(fontStyle = FontStyle.Normal), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // ---- 右页：结果 ----
            Column(Modifier.padding(start = TvSearchSpec.RIGHT_X, end = TvSpec.RIGHT, top = 26.dp).fillMaxSize().testTag("tv-search-results")) {
                val best = hits.firstOrNull()?.takeIf { Pinyin.initials(it.title.name).equals(query.trim(), ignoreCase = true) || it.title.name.equals(query.trim(), ignoreCase = true) }
                when {
                    query.isBlank() -> TvStateView("tv-search-idle", "举笔", "想找哪一部？", MascotLines.pick("search.idle", hour = hour, seed = today.day), Modifier.padding(top = 40.dp), inset = false)
                    titles == null -> Unit
                    hits.isEmpty() -> TvStateView("tv-search-none", "摊手", "片库里没有「${query.trim()}」", MascotLines.pick("search.none", hour = hour, seed = today.day), Modifier.padding(top = 60.dp), inset = false)
                    best != null -> {
                        TvSearchHeader("最佳匹配", "BEST MATCH", "共 ${hits.size} 条")
                        val t = best.title
                        Row(Modifier.padding(top = 26.dp, bottom = 30.dp).testTag("tv-search-best"), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            Box(Modifier.size(200.dp, 300.dp).border(1.dp, colors.line)) { CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) }
                            Column(Modifier.weight(1f)) {
                                TitleLogo(t.name, t.art.logo, 440.dp, 96.dp, baseSize = 60.sp, textOnly = preset.textTitles)
                                BasicText(
                                    (t.kicker(if (t.kind == Kind.Movie) "电影" else "剧集") + listOfNotNull(t.rating?.let { "★ $it" })).joinToString(" · "),
                                    Modifier.padding(top = 16.dp, bottom = 8.dp), style = Guga.song(24.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.16.em), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                t.lead?.let { BasicText(it, Modifier.padding(bottom = 22.dp), style = Guga.song(25.sp, FontWeight.SemiBold, colors.dim, lineHeight = 38.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (t.kind == Kind.Movie) {
                                        TvButton(if (t.progress > 0f) "续看" else "播放", { g.play(t.id, t.positionSec?.times(1000)) }, backToKeys, icon = GuguIcon.Play, key = "best-play", progress = t.progress)
                                        TvButton("详情", { g.open(t.id) }, icon = GuguIcon.Info, key = "best-detail")
                                    } else {
                                        TvButton("打开", { g.open(t.id) }, backToKeys, icon = GuguIcon.Info, key = "best-detail")
                                    }
                                }
                            }
                        }
                        val rest = hits.drop(1).take(2)
                        if (rest.isNotEmpty()) {
                            TvSearchHeader("也含「${query.trim()}」", "MATCHES", null)
                            for (h in rest) TvSearchRow(h, backToKeys) { g.open(h.title.id) }
                        }
                    }
                    else -> {
                        TvSearchHeader("含「${query.trim()}」", "MATCHES", "${hits.size} 条")
                        for (h in hits.take(5)) TvSearchRow(h, backToKeys) { g.open(h.title.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchHeader(title: String, english: String, trailing: String?) {
    val colors = Guga.colors
    Row(Modifier.fillMaxWidth().height(TvKitSpec.SECTION_H), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        BasicText("¶", style = Guga.numeral(36.sp, colors.spot), maxLines = 1)
        BasicText(title, style = Guga.song(Guga.type.section, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
        BasicText(english, style = Guga.numeral(20.sp, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
        if (trailing != null) BasicText(trailing, style = Guga.note(23.sp, colors.dim), maxLines = 1)
    }
}

/** 结果的一行：小海报 + 片名（命中的字专色）+「剧集 · 2024 · ★ 9.2」+ 右端的拼音首字母。 */
@Composable
private fun TvSearchRow(hit: TitleHit, modifier: Modifier, onOpen: () -> Unit) {
    val colors = Guga.colors
    val t = hit.title
    TvFocusable(onOpen, modifier.fillMaxWidth().height(TvSearchSpec.ROW_H), TvFocusKind.Row, key = "hit-${t.id}", label = t.name, onLongClick = { TvLongPressMenu.open(t) }) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(
            Modifier.fillMaxSize().drawBehind { if (!focused) drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(66.dp, 99.dp).border(1.dp, colors.line)) { CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) }
            Column(Modifier.weight(1f)) {
                BasicText(highlighted(t.name, hit.range, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot), style = Guga.song(34.sp, FontWeight.Black, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    listOfNotNull(if (t.kind == Kind.Movie) "电影" else "剧集", t.year?.toString(), t.rating?.let { "★ $it" }).joinToString(" · "),
                    Modifier.padding(top = 4.dp), style = Guga.song(22.sp, color = ink.copy(alpha = if (focused) 0.7f else 0.44f)), maxLines = 1,
                )
            }
            BasicText(Pinyin.initials(t.name).uppercase().take(10), style = Guga.numeral(26.sp, ink.copy(alpha = 0.7f), letterSpacing = 0.14.em), maxLines = 1)
        }
    }
}
