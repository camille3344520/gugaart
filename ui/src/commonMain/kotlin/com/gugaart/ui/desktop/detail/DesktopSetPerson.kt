package com.gugaart.ui.desktop.detail

import com.gugaart.ui.desktop.deskFocusRing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.Today
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.ProofTick
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SealSlot
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.VerticalCaption
import com.gugaart.ui.components.drawCover
import com.gugaart.ui.components.rememberCrossfade
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.detail.PersonData
import com.gugaart.ui.screens.detail.Work
import com.gugaart.ui.screens.detail.loadPerson
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.sets.SetData
import com.gugaart.ui.screens.sets.loadSet
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp

/* ============================== 合集详情 ============================== */

/**
 * 合集详情（设计稿 `p=coll`）：合集自己的剧照整幅铺满（70 % 屏高；没有 → 借第一部有剧照的成员，图注写明），合集名用宋体大字，
 * 右下角是成员平均分；下面 4 : 8 两栏——左「关于这一套」，右是带大编号的成员目录（按上映顺序）。
 */
@Composable
fun DesktopSet(set: Title, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val hour = LocalHour.current
    val m = Guga.dimens.margin
    val data = rememberLoad("set", set.id) { loadSet(it, set) }
    val d = data.value ?: SetData(set, emptyList())
    val poster = rememberImage(set.art.poster ?: d.members.firstOrNull()?.art?.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }
    val fav = chrome.isFavorite(set)
    val first = d.startWith
    val toggleFav = { graph.toggleFavorite(chrome, set.id, fav) }
    DesktopItemActions.favorite = toggleFav
    DesktopItemActions.played = null
    DesktopItemActions.previousSeason = null
    DesktopItemActions.nextSeason = null
    DesktopItemActions.play = first?.let { f -> { graph.open(f.id) } }

    val borrowed = d.borrowed
    val hero = set.copy(rating = d.average, art = set.art.copy(backdrop = set.art.backdrop ?: borrowed?.art?.backdrop, logo = null))
    DetailScaffold("desktop-set", null) { _ ->
        DHero(
            hero,
            0.70f,
            caption = "合集主图 · " + if (borrowed != null) "取自《${borrowed.name}》" else "合集剧照",
            kicker = listOfNotNull("SET · 合集", "共 ${d.count} 部".takeIf { d.count > 0 }, d.runtimeSec?.let { "连看 ${Rules.minutes(it)}" }).joinToString(" · "),
            ratingNote = if (d.average != null) "成员平均分" else null,
            nameOverride = set.name,
            under = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.8f))) {
                    if (first != null) DButton(if (first.progress > 0f || d.members.firstOrNull()?.id != first.id) "接着看《${first.name}》" else "从第一部看起", { graph.open(first.id) }, Modifier.widthIn(max = Guga.rem(44f)), small = false, tag = "d-cta")
                    DAction("收藏", toggleFav, tag = "d-fav")
                    SealSlot(fav, size = Guga.rem(3.6f))
                }
            },
        )
        Row(Modifier.padding(horizontal = m).padding(top = Guga.rem(3.2f)).fillMaxWidth()) {
            Column(Modifier.weight(4f)) {
                BasicText("ABOUT · 关于这一套", Modifier.padding(bottom = Guga.rem(1.2f)), style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                BasicText(set.overview.ifEmpty { "没有简介" }, style = Guga.song(rsp(1.5f), color = if (set.overview.isEmpty()) colors.faint else colors.dim, lineHeight = rsp(2.85f)), maxLines = 16, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Guga.dimens.gutter * 2))
            Column(Modifier.weight(8f).testTag("d-members")) {
                DSec(null, "成员", "IN THIS SET", trailing = "按上映年份")
                when {
                    d.members.isNotEmpty() -> d.members.forEachIndexed { i, mem -> MemberRow(i + 1, mem) }
                    data.error != null -> ErrorView(data.error!!, data::retry)
                    data.value == null -> if (data.showSkeleton) LoadingView()
                    else -> Row(Modifier.padding(vertical = Guga.rem(4f)), verticalAlignment = Alignment.CenterVertically) {
                        MascotPoseNamed("摊手", Guga.rem(9f))
                        Column(Modifier.padding(start = Guga.rem(1.6f))) {
                            BasicText("没有成员", style = Guga.song(rsp(2f), FontWeight.Black), maxLines = 1)
                            set.count?.let { BasicText("这套合集共 $it 部。", Modifier.padding(top = Guga.rem(0.4f)), style = Guga.song(rsp(1.3f), color = colors.dim), maxLines = 1) }
                        }
                    }
                }
            }
        }
        DEnd(listOfNotNull(set.name, d.years).joinToString(" · "), Modifier.padding(horizontal = m))
    }
}

/** 合集成员的一行：大编号 | 海报 | 片名 / 年份 · 时长 · 评分 / 两行简介 | 播放 + 校对格。右键 = 条目菜单。 */
@Composable
private fun MemberRow(no: Int, t: Title) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val colors = Guga.colors
    val hover = rememberHover()
    val press = rememberPressState()
    val seen = chrome.isPlayed(t.id, t.played)
    Row(
        Modifier.fillMaxWidth().hover(hover).contextClick(t).padding(vertical = Guga.rem(2f)).testTag("d-member"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(Rules.episodeNo(no), Modifier.width(Guga.rem(9.2f)), style = Guga.numeral(rsp(4.4f), colors.ink), maxLines = 1)
        Box(Modifier.width(Guga.rem(13f)).aspectRatio(2f / 3f).border(1.dp, colors.line)) {
            CoverImage(t.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), caption = t.name) }
        }
        Column(Modifier.weight(1f).padding(horizontal = Guga.rem(2.4f))) {
            BasicText(
                t.name,
                Modifier.deskFocusRing(press).pressable(press, label = t.name, onClick = { graph.open(t.id) }),
                style = Guga.song(rsp(2.4f), FontWeight.Black, if (hover.value) colors.spot else colors.ink, lineHeight = rsp(3.2f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(listOfNotNull(t.year?.toString(), t.runtimeSec?.let { Rules.minutes(it) }, t.rating?.let { "$it 分" }, if (t.kind == Kind.Series) t.count?.let { "$it 集" } else null).joinToString(" · "), Modifier.padding(top = Guga.rem(0.6f)), style = Guga.note(rsp(1.3f), colors.spot), maxLines = 1)
            if (t.overview.isNotEmpty()) BasicText(t.overview, Modifier.padding(top = Guga.rem(1f)), style = Guga.song(rsp(1.4f), color = colors.dim, lineHeight = rsp(2.5f)), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Guga.rem(1.6f))) {
            DButton(if (t.kind == Kind.Series) "打开" else if (t.progress > 0f) "续看" else "播放", { if (t.kind == Kind.Series) graph.open(t.id) else graph.play(t.id, t.positionSec?.takeIf { t.progress > 0f }?.times(1000)) }, small = true, tag = "d-member-play")
            TickButton(seen, t.name) { graph.togglePlayed(chrome, t.id, seen) }
        }
    }
    Rule()
}

/* ============================== 人物 ============================== */

/** 人物页的入口：取这个人和片库里的作品（复用手机的 `loadPerson`）。 */
@Composable
fun DesktopPerson(id: String, today: Today) {
    val data = rememberLoad("person", id) { loadPerson(it, id) }
    val d = data.value
    when {
        d != null -> PersonBody(d, today)
        data.error != null -> com.gugaart.ui.desktop.DesktopErrorPage(data.error!!, data::retry)
        else -> DetailMessage { if (data.showSkeleton) LoadingView() }
    }
}

/** 年份跨度：「1988–04」（同一个世纪只写后两位）；只有一年 → 「1988」。纯函数。 */
fun yearSpan(years: Pair<Int, Int>?): String? {
    val (a, b) = years ?: return null
    return if (a == b) a.toString() else "$a–${(b % 100).toString().padStart(2, '0')}"
}

/** 平均评分（一位小数）；没有评分的不算；都没有 → null。纯函数。 */
fun averageRating(works: List<Work>): String? {
    val rs = works.mapNotNull { it.title.rating?.toDoubleOrNull() }
    if (rs.isEmpty()) return null
    return (kotlin.math.round(rs.average() * 10) / 10).toString()
}

/** 作品按年代分组，新 → 旧；没有年份的排在最后一组「年份不详」。纯函数。 */
fun byDecade(works: List<Work>): List<Pair<String, List<Work>>> {
    val groups = works.groupBy { it.title.year?.let { y -> y / 10 * 10 } }
    return groups.keys.sortedWith(compareByDescending<Int?> { it ?: Int.MIN_VALUE }).map { k -> (k?.let { "$it 年代" } ?: "年份不详") to groups.getValue(k) }
}

/**
 * 人物页（设计稿 `p=person`）：大照片占窗口右半边、从顶端铺到第一屏底，向左渐隐进纸面；名字是这一页最大的字（110）。
 * 服务器上没有简介 → 用片库里的作品统计代替。作品多 → 评分最高的四部 + 按年代的作品年表（两列）；只有 1 部 → 大图版 + 同剧演员。
 */
@Composable
private fun PersonBody(d: PersonData, today: Today) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val colors = Guga.colors
    val win = LocalWindowDp.current
    val m = Guga.dimens.margin
    val gut = Guga.dimens.gutter
    val width = dWidth()
    val p = d.person
    val works = d.works
    val one = works.size == 1
    val photo = rememberImage(p.art.poster, keepPrevious = true)
    LaunchedEffect(photo) { chrome.spot = photo?.spot() }
    val cf = rememberCrossfade(photo, clearOnNull = true)
    DesktopItemActions.clear()
    DesktopItemActions.play = works.firstOrNull()?.takeIf { one }?.let { w -> { graph.open(w.title.id) } }

    DetailScaffold("desktop-person", if (preset.state == "works") "works" else null) { mark ->
        val height = maxOf(win.height, Guga.rem(60f))
        Box(Modifier.fillMaxWidth().height(height).clipToBounds().testTag("d-person-hero")) {
            // 照片：右边 52 %，彩色，向左渐隐进纸面
            Canvas(Modifier.align(Alignment.TopEnd).fillMaxWidth(0.52f).fillMaxHeight()) {
                val paper = colors.paper
                drawRect(paper)
                cf.current?.let { drawCover(it, Framing(0.5f, 0.12f), cf.alpha) }
                drawRect(Brush.horizontalGradient(0f to paper, 0.34f to paper.copy(alpha = 0.3f), 0.6f to Color.Transparent, 1f to Color.Transparent))
                val h = size.height
                drawRect(Brush.verticalGradient(0f to paper.copy(alpha = 0.78f), (Guga_REM7 * density / h).coerceIn(0.01f, 0.2f) to paper.copy(alpha = 0.45f), (Guga_REM15 * density / h).coerceIn(0.02f, 0.5f) to Color.Transparent, 0.7f to Color.Transparent, 1f to paper))
            }
            if (p.art.poster != null) VerticalCaption("照片 · ${p.name}", Modifier.align(Alignment.BottomEnd).padding(end = (m - Guga.rem(2.6f)).coerceAtLeast(0.dp), bottom = Guga.rem(6f)))
            Column(Modifier.align(Alignment.CenterStart).padding(start = m, top = Guga.rem(4.4f)).fillMaxWidth(0.56f)) {
                BasicText("PEOPLE · 人物　${d.roles}", style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                val big = if (width == DWidth.Sm) 8f else 11f
                val size = if (p.name.length <= 4) big else if (p.name.length <= 7) big * 0.72f else big * 0.5f
                BasicText(p.name, Modifier.padding(top = Guga.rem(1.6f), bottom = Guga.rem(2.4f)).testTag("d-person-name"), style = Guga.song(rsp(size), FontWeight.Black, lineHeight = rsp(size * 1.1f), letterSpacing = 0.08.em), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val span = d.years
                val bio = p.overview.ifEmpty {
                    "服务器上没有这位的简介和生日。下面是从你的片库里数出来的：" + if (one) "目前只收了一部作品。" else listOfNotNull(span?.let { "${it.first} 到 ${it.second} 年，" }, "共 ${works.size} 部", "，身份有${d.roles.replace(" / ", "、")}".takeIf { d.roles.contains("/") }).joinToString("") + "。"
                }
                BasicText(bio, Modifier.widthIn(max = Guga.rem(60f)).testTag(if (p.overview.isEmpty()) "d-person-stats-bio" else "d-person-bio"), style = Guga.song(rsp(1.6f), color = colors.dim, lineHeight = rsp(3.04f)), maxLines = 5, overflow = TextOverflow.Ellipsis)
                Column(Modifier.padding(vertical = Guga.rem(2.8f)).widthIn(max = Guga.rem(64f))) {
                    Rule(strong = true)
                    Row(Modifier.padding(vertical = Guga.rem(1.8f)), horizontalArrangement = Arrangement.spacedBy(Guga.rem(4f))) {
                        Stat(works.size.toString(), "片库里的作品")
                        if (!one) {
                            yearSpan(span)?.let { Stat(it, "年份跨度") }
                            averageRating(works)?.let { Stat(it, "平均评分") }
                        }
                        Stat("${works.count { it.title.kind == Kind.Movie }} / ${works.count { it.title.kind != Kind.Movie }}", "电影 / 剧集")
                    }
                    Rule()
                }
                if (one) {
                    val w = works.first().title
                    DButton("去看《${w.name}》", { graph.open(w.id) }, tag = "d-cta")
                } else if (works.isNotEmpty()) {
                    BasicText("KNOWN FOR · 评分最高的四部", Modifier.padding(bottom = Guga.rem(1.2f)), style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.6f))) {
                        for (w in works.sortedByDescending { it.title.rating?.toDoubleOrNull() ?: 0.0 }.take(4)) {
                            val press = rememberPressState()
                            Box(Modifier.width(Guga.rem(11f)).aspectRatio(2f / 3f).border(1.dp, colors.ink.copy(alpha = 0.2f)).contextClick(w.title).deskFocusRing(press).pressable(press, label = w.title.name, onClick = { graph.open(w.title.id) }).testTag("d-known")) {
                                CoverImage(w.title.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(w.title.name, person = false), Modifier.fillMaxSize(), caption = w.title.name) }
                            }
                        }
                    }
                }
            }
        }
        if (one) {
            val w = works.first()
            val t = w.title
            Column(mark("works").padding(horizontal = m).testTag("d-works")) {
                DSec("01", "作品", "WORKS", trailing = "片库里只有这一部")
                Row(Modifier.fillMaxWidth()) {
                    val press = rememberPressState()
                    Box(Modifier.weight(if (width == DWidth.Xl) 4f else 5f).aspectRatio(16f / 9f).border(1.dp, colors.line).contextClick(t).deskFocusRing(press).pressable(press, label = t.name, onClick = { graph.open(t.id) }).testTag("d-best")) {
                        CoverImage(t.art.backdrop ?: t.art.poster, Modifier.fillMaxSize(), Framing.of(t.id)) { HatchPlate(Rules.monogram(t.name, person = false), Modifier.fillMaxSize(), caption = t.name) }
                        Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 1f to colors.paper.copy(alpha = 0.92f))) }
                        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = Guga.rem(2.4f), vertical = Guga.rem(2f)), verticalAlignment = Alignment.Bottom) {
                            TitleLogo(t.name, t.art.logo, Guga.rem(26f), Guga.rem(8f), baseSize = rsp(3.6f), textOnly = preset.textTitles)
                            Spacer(Modifier.weight(1f))
                            BasicText(
                                listOfNotNull(listOfNotNull(if (t.kind == Kind.Movie) "电影" else "剧集", t.year?.toString(), t.genres.firstOrNull()).joinToString(" · "), listOfNotNull(if (t.kind == Kind.Series) t.count?.let { "$it 集" } else t.runtimeSec?.let { Rules.minutes(it) }, t.rating?.let { "$it 分" }).joinToString(" · ")).joinToString("\n"),
                                style = Guga.note(rsp(1.3f), colors.dim).copy(lineHeight = rsp(2.2f), textAlign = androidx.compose.ui.text.style.TextAlign.End),
                                maxLines = 2,
                            )
                        }
                    }
                    Spacer(Modifier.width(gut * 2))
                    Column(Modifier.weight(if (width == DWidth.Xl) 8f else 7f)) {
                        if (w.role != null) BasicText(w.role, Modifier.padding(bottom = Guga.rem(1.2f)), style = Guga.song(rsp(1.2f), FontWeight.SemiBold, colors.spot, letterSpacing = 0.24.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (t.overview.isNotEmpty()) BasicText(Rules.firstSentences(t.overview, 150), style = Guga.song(rsp(1.6f), lineHeight = rsp(3.1f)), maxLines = 6, overflow = TextOverflow.Ellipsis)
                        Column(Modifier.padding(top = Guga.rem(2.4f))) {
                            Rule()
                            EditorNote(MascotLines.pick("person.one", hour = LocalHour.current, seed = p.id.hashCode() + today.day) ?: "片库里暂时只有这一部。", Modifier.padding(vertical = Guga.rem(1.4f)), label = "放映手记", english = "SCREENING NOTES", voice = rememberNoteVoice(), photo = Guga.rem(5.2f))
                            Rule()
                        }
                    }
                }
            }
            val co = t.people.filter { it.id != p.id }
            if (co.isNotEmpty()) Column(Modifier.padding(top = Guga.rem(DetailSpec.SECTION))) {
                DSec("02", "同剧演员", "CO-STARS", Modifier.padding(horizontal = m), trailing = "${co.size} 位")
                DCastRow(co, { graph.open(it.id) })
            }
        } else if (works.isNotEmpty()) {
            Column(mark("works").padding(horizontal = m).testTag("d-works")) {
                DSec("01", "作品年表", "FILMOGRAPHY", trailing = "${works.size} 部 · 新 → 旧")
                for ((decade, list) in byDecade(works)) {
                    Row(Modifier.padding(top = Guga.rem(3f), bottom = Guga.rem(0.4f)), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1f))) {
                        BasicText(decade, style = Guga.song(rsp(1.3f), FontWeight.Bold, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                        BasicText("${list.size} 部", style = Guga.note(rsp(1.2f), colors.faint), maxLines = 1)
                    }
                    DGrid(list, if (width == DWidth.Xl) 3 else 2, gapX = gut * 2) { w ->
                        val t = w.title
                        DRow(
                            t.year?.toString() ?: "",
                            t.name,
                            listOfNotNull(w.role ?: "参演", t.genres.take(2).joinToString(" / ").takeIf { it.isNotEmpty() }, if (t.kind == Kind.Series) t.count?.let { "$it 集" } else t.runtimeSec?.let { Rules.minutes(it) }).joinToString(" · "),
                            { graph.open(t.id) },
                            leadWidth = 5.6f,
                            context = t,
                            tag = "d-work",
                            middle = { DMiniPoster(t) },
                            trailing = { t.rating?.let { BasicText(it, style = Guga.numeral(rsp(2.4f)), maxLines = 1) } },
                        )
                    }
                }
            }
        }
        DEnd("${p.name} · 片库里 ${works.size} 部", Modifier.padding(horizontal = m))
    }
}

private const val Guga_REM7 = 70f
private const val Guga_REM15 = 150f

@Composable
private fun Stat(value: String, label: String) {
    Column {
        BasicText(value, style = Guga.numeral(rsp(4.4f)).copy(lineHeight = rsp(4.4f)), maxLines = 1)
        BasicText(label, Modifier.padding(top = Guga.rem(0.6f)), style = Guga.song(rsp(1.2f), color = Guga.colors.faint), maxLines = 1)
    }
}
