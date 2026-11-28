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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.withStyle
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
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.search.SearchIndex
import com.gugaart.ui.screens.search.TitleHit
import com.gugaart.ui.screens.search.loadLibraryIndex
import com.gugaart.ui.screens.search.rememberAsyncSearch
import com.gugaart.ui.screens.search.rememberRecentTops
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.RemoteKey
import com.gugugaga.ds.focus.remoteKeyOf
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon

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
    val index = rememberLoad("tv-search-index") { loadLibraryIndex(it) }
    // 1.3.1：返回保持位置——打过的词 / 数字盘 / 字母盘上最后的键都记住（rememberSaveable，跟着返回栈条目走）；
    // 返回时词还在，本地索引（缓存）第一帧就排得出结果，焦点才落回原来那条（PageStates + RouteFocusMemory）。
    var query by rememberSaveable { mutableStateOf((preset.query ?: q).orEmpty().uppercase().take(TvSearchSpec.MAX_QUERY)) }
    var digits by rememberSaveable { mutableStateOf(false) }
    var lastKey by rememberSaveable { mutableStateOf("key-A") }
    val titles = index.value?.titles
    val peopleIndex = rememberLoad("tv-search-people") { com.gugaart.ui.screens.search.loadPeopleIndex(it) }
    val search = rememberAsyncSearch(query, index.value, peopleIndex.value, remote = null, titleLimit = TvSearchSpec.MAX_ROWS, peopleLimit = 6, episodeLimit = 0)
    val snap = search.snapshot
    val hits = snap.hits
    val people = snap.people
    val peopleFirst = snap.peopleFirst
    val shownQuery = snap.query
    val recents = com.gugaart.ui.screens.search.rememberSearchRecents()
    fun openHit(id: String) {
        recents.add(query.trim())
        g.open(id)
    }
    // 1.6 B5：语音输入（TvVoice.host；没有语音服务 = null，不显示入口）
    val voice = TvVoice.host
    val voiceScope = rememberCoroutineScope()
    var listening by remember { mutableStateOf(false) }
    fun listen() {
        val h = voice ?: return
        if (listening) return
        listening = true
        voiceScope.launch {
            val heard = try { h() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null } finally { listening = false }
            tvVoiceQuery(heard)?.let { query = it }
        }
    }
    // 结果里按左：回字母盘原来的键
    val backToKeys = Modifier.onKeyEvent { e ->
        if (e.type == KeyEventType.KeyDown && remoteKeyOf(e.key) == RemoteKey.Left) g.focus.request(lastKey) else false
    }
    // 1.6 A1 多台一起搜：本台的片出来了、不到 5 条（或者焦点走到了本台最后一行）→ 并发问其它已保存的服务器，结果接在下面「其它服务器」
    var reachedEnd by remember(shownQuery) { mutableStateOf(false) }
    val homeSettled = query.isNotBlank() && index.value != null && !search.isSearching(query, index.value != null || peopleIndex.value != null)
    val other = com.gugaart.ui.screens.search.rememberOtherServerSearch(query, homeSettled && com.gugaart.ui.screens.search.OtherSearchRules.shouldAsk(hits.size, reachedEnd), hits.map { it.title })
    @Composable
    fun OtherGroup(room: Int) {
        val shown = other.hits.take(room.coerceAtLeast(1))
        if (shown.isEmpty()) return
        TvSearchHeader("其它服务器", "OTHER SERVERS", "${other.hits.size} 部")
        for (h in shown) TvSearchRow(TitleHit(h.title, h.range), backToKeys, trailing = h.label, key = "other-${h.routeId}") { openHit(h.routeId) }
    }

    Column(
        Modifier.fillMaxSize().testTag("tv-search").onPreviewKeyEvent { e ->
            // 遥控器的麦克风键（系统没先拿走的话）= 语音
            if (voice == null || (e.key != Key.Search && e.key != Key.VoiceAssist)) return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyUp) listen()
            true
        },
    ) {
        TvPageHeader("搜索", "SEARCH", today, trailing = index.value?.let { "共 ${it.total} 部" })
        Box(Modifier.fillMaxSize()) {
            // ---- 左页：输入行 + 字母盘 ----
            Column(Modifier.padding(start = TvSpec.LEFT, top = 26.dp).width(TvSearchSpec.LEFT_W)) {
                Row(
                    Modifier.fillMaxWidth().height(TvSearchSpec.INPUT_H).drawBehind { drawLine(colors.ink, Offset(0f, size.height - 1.dp.toPx()), Offset(size.width, size.height - 1.dp.toPx()), 2.dp.toPx()) }.testTag("tv-search-input"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicText(query, Modifier.weight(1f, fill = false), style = Guga.numeral(76.sp, colors.ink, letterSpacing = 0.08.em), maxLines = 1, overflow = TextOverflow.Clip)
                        Box(Modifier.size(5.dp, 70.dp).background(colors.seal))
                        if (query.isEmpty()) BasicText("拼音首字母 · 片名里的字", Modifier.padding(start = 14.dp), style = Guga.song(25.sp, color = colors.faint), maxLines = 1)
                    }
                    // 1.6 B5：语音（系统有语音识别才有这一格）；识别出来的字填进来直接搜
                    if (voice != null) {
                        TvFocusable(
                            onClick = ::listen,
                            modifier = Modifier.size(TvSearchSpec.KEY_W, TvSearchSpec.KEY_H).testTag("tv-search-voice"),
                            kind = TvFocusKind.Chip,
                            key = "voice",
                            label = "语音",
                            contentAlignment = Alignment.Center,
                        ) { focused ->
                            val ink = if (focused) colors.onInk else colors.ink
                            Box(Modifier.fillMaxSize().then(if (focused) Modifier else Modifier.border(1.5.dp, colors.line)), contentAlignment = Alignment.Center) {
                                BasicText(if (listening) "在听" else "语音", style = Guga.song(23.sp, FontWeight.Bold, ink), maxLines = 1)
                            }
                        }
                    }
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
                val best = hits.firstOrNull()?.takeIf { Pinyin.initials(it.title.name).equals(shownQuery, ignoreCase = true) || it.title.name.equals(shownQuery, ignoreCase = true) }
                when {
                    query.isBlank() -> TvSearchIdle(index.value, recents.items, today.day, backToKeys) { term ->
                        val next = term.take(TvSearchSpec.MAX_QUERY)
                        query = next
                        search.flush(next)
                    }
                    titles == null -> Unit
                    hits.isEmpty() && people.isEmpty() ->
                        if (other.hits.isNotEmpty()) Column(Modifier.testTag("tv-search-other")) { OtherGroup(TvSearchSpec.MAX_ROWS - 1) }
                        else if (search.isSearching(query, index.value != null || peopleIndex.value != null) || other.searching) Unit
                        else TvStateView("tv-search-none", "摊手", "片库里没有「${query.trim()}」", MascotLines.pick("search.none", hour = hour, seed = today.day), Modifier.padding(top = 60.dp), inset = false)
                    best != null -> {
                        TvSearchHeader("最佳匹配", "BEST MATCH", "共 ${hits.size} 条")
                        val t = best.title
                        Row(Modifier.padding(top = 26.dp, bottom = 30.dp).testTag("tv-search-best"), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                            Box(Modifier.size(200.dp, 300.dp).border(1.dp, colors.line)) { CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) }
                            Column(Modifier.weight(1f)) {
                                TitleLogo(t.name, t.art.logo, 440.dp, 96.dp, baseSize = 60.sp, textOnly = preset.textTitles)
                                BasicText(
                                    (t.kicker(com.gugaart.ui.screens.search.searchKindLabel(t.kind)) + listOfNotNull(t.rating?.let { "★ $it" })).joinToString(" · "),
                                    Modifier.padding(top = 16.dp, bottom = 8.dp), style = Guga.song(24.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.16.em), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                com.gugaart.ui.screens.search.originalHitText(best, colors.spot)?.let {
                                    BasicText(it, Modifier.padding(bottom = 10.dp).testTag("search-original"), style = Guga.song(24.sp, FontWeight.SemiBold, colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                t.lead?.let { BasicText(it, Modifier.padding(bottom = 22.dp), style = Guga.song(25.sp, FontWeight.SemiBold, colors.dim, lineHeight = 38.sp), maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (t.kind == Kind.Movie) {
                                        TvButton(if (t.progress > 0f) "续看" else "播放", {
                                            recents.add(query.trim())
                                            g.play(t.id, t.positionSec?.times(1000))
                                        }, backToKeys, icon = GuguIcon.Play, key = "best-play", progress = t.progress)
                                        TvButton("详情", { openHit(t.id) }, icon = GuguIcon.Info, key = "best-detail")
                                    } else {
                                        TvButton("打开", { openHit(t.id) }, backToKeys, icon = GuguIcon.Info, key = "best-detail")
                                    }
                                    val direct = best.direct
                                    com.gugaart.ui.screens.search.directTag(best)?.let { tag -> if (direct != null) TvButton(tag, { openHit(direct.id) }, icon = GuguIcon.Play, key = "best-direct") }
                                }
                            }
                        }
                        val rest = hits.drop(1).take(if (people.isEmpty()) 2 else 1)
                        if (rest.isNotEmpty()) {
                            TvSearchHeader("也含「$shownQuery」", "MATCHES", null)
                            for ((i, h) in rest.withIndex()) TvSearchRow(h, backToKeys, onFocus = { if (i == rest.size - 1) reachedEnd = true }) { openHit(h.direct?.id ?: h.title.id) }
                        }
                        if (people.isNotEmpty() && (other.hits.isEmpty() || rest.isEmpty())) {
                            TvSearchHeader("演员", "PEOPLE", "${people.size} 位")
                            TvPersonRow(people.first(), backToKeys) { openHit(people.first().person.id) }
                        }
                        OtherGroup(2 - rest.size.coerceAtMost(1))
                    }
                    else -> {
                        val shownPeople = people.take(if (hits.isEmpty()) 5 else 2)
                        val shownTitles = hits.take(if (people.isEmpty()) 5 else 3)
                        if (peopleFirst || hits.isEmpty()) {
                            TvSearchHeader("演员", "PEOPLE", "${people.size} 位")
                            for (p in shownPeople) TvPersonRow(p, backToKeys) { openHit(p.person.id) }
                        }
                        // 有别台的结果时本台的片少排一行，给「其它服务器」留地方（一屏放得下，不滚）
                        val titleRows = if (other.hits.isNotEmpty()) shownTitles.take((TvSearchSpec.MAX_ROWS - 3).coerceAtLeast(1)) else shownTitles
                        if (titleRows.isNotEmpty()) {
                            TvSearchHeader("含「$shownQuery」", "MATCHES", "${hits.size} 条")
                            // 打了集号（「仙逆 12」「XIANNIS01E12」）= 要的是那一集：电视一行只有一个「确定」，有直达的行确定就开那一集
                            for ((i, h) in titleRows.withIndex()) TvSearchRow(h, backToKeys, onFocus = { if (i == titleRows.size - 1) reachedEnd = true }) { openHit(h.direct?.id ?: h.title.id) }
                        }
                        if (!peopleFirst && hits.isNotEmpty() && shownPeople.isNotEmpty() && other.hits.isEmpty()) {
                            TvSearchHeader("演员", "PEOPLE", "${people.size} 位")
                            for (p in shownPeople) TvPersonRow(p, backToKeys) { openHit(p.person.id) }
                        }
                        OtherGroup(TvSearchSpec.MAX_ROWS - 2 - titleRows.size - (if (peopleFirst || hits.isEmpty()) shownPeople.size else 0))
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
private fun TvSearchRow(hit: TitleHit, modifier: Modifier, trailing: String? = null, key: String? = null, onFocus: (() -> Unit)? = null, onOpen: () -> Unit) {
    val colors = Guga.colors
    val t = hit.title
    // 别台的行（[trailing] = 服务器名）没有长按菜单：收藏 / 已看要写那一台，进了详情再做
    TvFocusable(onOpen, modifier.fillMaxWidth().height(TvSearchSpec.ROW_H), TvFocusKind.Row, key = key ?: "hit-${t.id}", label = t.name, onLongClick = if (trailing == null) ({ TvLongPressMenu.open(t) }) else null, onFocus = onFocus) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(
            Modifier.fillMaxSize().drawBehind { if (!focused) drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(66.dp, 99.dp).border(1.dp, colors.line)) { CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) }
            Column(Modifier.weight(1f)) {
                BasicText(highlighted(t.name, hit.range, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot), style = Guga.song(34.sp, FontWeight.Black, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = ink.copy(alpha = if (focused) 0.7f else 0.44f)
                val mark = if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot
                BasicText(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append(listOfNotNull(com.gugaart.ui.screens.search.searchKindLabel(t.kind), t.year?.toString(), t.rating?.let { "★ $it" }, com.gugaart.ui.screens.search.directTag(hit)?.let { "$it →" }).joinToString(" · "))
                        com.gugaart.ui.screens.search.originalHitText(hit, mark)?.let { o ->
                            append("　")
                            withStyle(androidx.compose.ui.text.SpanStyle(color = ink)) { append(o) }
                        }
                    },
                    Modifier.padding(top = 4.dp), style = Guga.song(22.sp, color = sub), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (trailing != null) BasicText(trailing, Modifier.widthIn(max = 260.dp).testTag("search-other-server"), style = Guga.song(26.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            else BasicText(Pinyin.initials(t.name).uppercase().take(10), style = Guga.numeral(26.sp, ink.copy(alpha = 0.7f), letterSpacing = 0.14.em), maxLines = 1)
        }
    }
}

/**
 * 电视搜索页什么都没输入时（设计稿 `p=search` 默认态）：「最近搜过」一行一条（打的词 · 现在能搜到的第一部 · 什么时候），确定 = 再搜一次；
 * 下面她举笔。没搜过就只有她。结果区按左回字母盘（[modifier]）。
 */
@Composable
private fun TvSearchIdle(index: SearchIndex?, recents: List<com.gugaart.ui.screens.search.RecentSearch>, seed: Int, modifier: Modifier, onPick: (String) -> Unit) {
    val colors = Guga.colors
    val hour = LocalHour.current
    val now = remember { com.gugaart.ui.screens.search.SearchMemory.nowMs() }
    val offset = remember(now) { com.gugaart.ui.screens.search.SearchMemory.offsetMs(now) }
    val shown = recents.take(4)
    val tops = rememberRecentTops(index, shown.map { it.term })
    Column(Modifier.fillMaxWidth().testTag("tv-search-idle")) {
        if (recents.isNotEmpty()) {
            TvSearchHeader("最近搜过", "RECENT", null)
            for ((i, r) in shown.withIndex()) {
                val top = tops[r.term]
                TvFocusable({ onPick(r.term) }, modifier.fillMaxWidth().height(TvSearchSpec.ROW_H * 0.62f), TvFocusKind.Row, key = "recent-$i", label = r.term) { focused ->
                    val ink = if (focused) colors.onInk else colors.ink
                    Row(
                        Modifier.fillMaxSize().drawBehind { if (!focused) drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(horizontal = 26.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                    ) {
                        Icon(GuguIcon.History, ink.copy(alpha = 0.7f), 30.dp)
                        BasicText(r.term, style = Guga.numeral(34.sp, ink, letterSpacing = 0.1.em), maxLines = 1)
                        BasicText(top ?: "", Modifier.weight(1f), style = Guga.song(24.sp, color = ink.copy(alpha = if (focused) 0.75f else 0.5f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(com.gugaart.ui.screens.search.SearchMemory.whenLabel(r.at, now, offset), style = Guga.note(22.sp, ink.copy(alpha = 0.5f)), maxLines = 1)
                    }
                }
            }
        }
        TvStateView("tv-search-ask", "举笔", "找哪一部", MascotLines.pick("search.idle", hour = hour, seed = seed), Modifier.padding(top = if (recents.isEmpty()) 40.dp else 26.dp), inset = false)
    }
}

/** 一位演员一行（圆头像 · 名字（命中的字上专色）·「演员」· 拼音缩写）；确定 = 打开人物页。 */
@Composable
private fun TvPersonRow(hit: com.gugaart.ui.screens.search.PersonHit, modifier: Modifier, onOpen: () -> Unit) {
    val colors = Guga.colors
    val p = hit.person
    TvFocusable(onOpen, modifier.fillMaxWidth().height(TvSearchSpec.ROW_H * 0.8f), TvFocusKind.Row, key = "person-${p.id}", label = p.name) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(
            Modifier.fillMaxSize().drawBehind { if (!focused) drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(72.dp).clip(androidx.compose.foundation.shape.CircleShape).background(colors.line)) { CoverImage(p.photo, Modifier.fillMaxSize(), Framing(0.5f, 0.2f)) }
            Column(Modifier.weight(1f)) {
                BasicText(highlighted(p.name, hit.range, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot), style = Guga.song(34.sp, FontWeight.Black, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText("演员", Modifier.padding(top = 4.dp), style = Guga.song(22.sp, color = ink.copy(alpha = if (focused) 0.7f else 0.44f)), maxLines = 1)
            }
            BasicText(Pinyin.initials(p.name).uppercase().take(10), style = Guga.numeral(26.sp, ink.copy(alpha = 0.7f), letterSpacing = 0.14.em), maxLines = 1)
        }
    }
}
