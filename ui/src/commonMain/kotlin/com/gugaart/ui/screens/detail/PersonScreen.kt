package com.gugaart.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.HatchPlate
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.Plate
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.TitleLogo
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Art
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.screens.BackRow
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.open
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 人物页的作品筛选（只有一部作品时不出现）。 */
enum class WorkFilter(val label: String) { All("全部"), Acting("出演"), Behind("幕后") }

@Composable
fun PersonScreen(id: String) {
    val graph = LocalGraph.current
    val load = rememberLoad("person", id) { loadPerson(it, id) }
    val data = load.value
    when {
        data != null -> PersonPhone(data)
        load.error != null -> DetailShell("", { graph.nav.backOrHome() }) { ErrorView(load.error!!, load::retry) }
        else -> DetailShell("", { graph.nav.backOrHome() }) { if (load.showSkeleton) LoadingView() }
    }
}

/**
 * 人物页（手机，设计稿 `p=person`）：**彩色**肖像整幅铺满 540，大名压在渐隐处；片库统计一行（片库里 N 部 · 跨度 · 最高）；
 * 服务器上没有小传时**不编生平**，写明「下面只列你片库里有的作品」；作品按年份，带角色名。
 * 只有 1 部作品：不出现筛选条，换成一张带 logo 的 16:9 大卡 + 一句引导。
 */
@Composable
fun PersonPhone(data: PersonData) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val dimens = Guga.dimens
    val type = Guga.type
    val colors = Guga.colors
    val p = data.person
    val scroll = rememberScrollState(LocalPreset.current.scroll)
    com.gugaart.ui.screens.ScrollsToTop { scroll.animateScrollTo(0) }
    val photo = rememberImage(p.art.poster, keepPrevious = true)
    LaunchedEffect(photo) { chrome.spot = photo?.spot() }
    var filter by remember { mutableStateOf(WorkFilter.All) }
    val solidAt = with(LocalDensity.current) { (PERSON_BLEED - dimens.statusBar - 50.dp - 110.dp).toPx() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("person-phone")) {
            // 肖像：脸在上半部，取景靠上
            FullBleed(Art(backdrop = p.art.poster), PERSON_BLEED, framing = Framing(0.5f, 0.12f), caption = if (p.art.poster != null) "肖像 · ${p.name}" else null, scrollPx = scroll.value.toFloat()) {
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = dimens.margin).padding(bottom = 6.dp)) {
                    BasicText("人物 · PROFILE", style = Guga.kicker(), maxLines = 1)
                    // 长的外文名降一号、允许两行，不裁字
                    val long = Rules.titleUnits(p.name) > 5
                    BasicText(
                        p.name,
                        Modifier.padding(top = 4.dp),
                        style = Guga.song(if (long) type.display * 0.62f else type.display, FontWeight.Black, letterSpacing = if (long) 0.em else 0.12.em, lineHeight = type.display * (if (long) 0.8f else 1.2f)),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BasicText(data.roles, Modifier.padding(top = 4.dp), style = Guga.song(type.caption * 1.1f, color = colors.dim), maxLines = 1)
                }
            }
            Column(Modifier.padding(horizontal = dimens.margin)) {
                Rule(Modifier.padding(top = 14.dp), strong = true)
                Row(Modifier.fillMaxWidth().heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stat("片库里", "${data.works.size} 部")
                    Spacer(Modifier.weight(1f))
                    data.years?.let { (a, b) -> Stat("跨度", if (a == b) "$a" else "$a — $b") }
                    Spacer(Modifier.weight(1f))
                    data.best?.let { Stat("最高", it) }
                }
                Rule()
                // 没有小传：不编生平
                if (p.overview.isEmpty()) {
                    BasicText(
                        "服务器上还没有${p.name}的小传。下面只列你片库里有的作品。",
                        Modifier.padding(top = 14.dp),
                        style = Guga.song(type.lead * 0.96f, color = colors.faint, lineHeight = type.leadLine * 0.92f).copy(fontStyle = FontStyle.Italic),
                    )
                } else {
                    Overview(Rules.fold(p.overview, PHONE_FOLD_CHARS), Modifier.padding(top = 16.dp))
                }

                SectionHeader("作品", "IN YOUR LIBRARY", Modifier.padding(top = 30.dp), trailing = "${data.works.size} 部")
                if (data.works.size == 1) {
                    // 只有一部：一张带 logo 的大卡 + 一句引导，不出现筛选条和空栏
                    val w = data.works.first()
                    val press = rememberPressState()
                    Column(Modifier.padding(top = 16.dp).pressGraphics(press).pressable(press, label = w.title.name) { graph.open(w.title.id) }.testTag("person-single")) {
                        Box {
                            Plate(w.title.art.backdrop ?: w.title.art.thumb ?: w.title.art.poster, Modifier.fillMaxWidth(), fallback = Rules.monogram(w.title.name, false), framing = Framing.of(w.title.id))
                            TitleLogo(w.title.name, w.title.art.logo, 180.dp, 60.dp, Modifier.align(Alignment.BottomStart).padding(12.dp))
                        }
                        BasicText(listOfNotNull(w.role, w.title.year?.toString(), w.title.rating?.let { "$it 分" }).joinToString(" · "), Modifier.padding(top = 10.dp), style = Guga.song(type.caption * 1.1f, color = colors.dim))
                        BasicText("片库里只有这一部。点开看看同剧的演员。", Modifier.padding(top = 6.dp), style = Guga.song(type.caption, color = colors.faint).copy(fontStyle = FontStyle.Italic))
                    }
                } else {
                    val acting = data.works.count { !it.behind }
                    val behind = data.works.size - acting
                    if (acting > 0 && behind > 0) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            for (f in WorkFilter.entries) {
                                val n = when (f) {
                                    WorkFilter.All -> data.works.size
                                    WorkFilter.Acting -> acting
                                    WorkFilter.Behind -> behind
                                }
                                val on = f == filter
                                val press = rememberPressState()
                                Row(Modifier.pressGraphics(press).heightIn(min = 44.dp).pressable(press, label = f.label) { filter = f }, verticalAlignment = Alignment.CenterVertically) {
                                    BasicText(f.label, style = Guga.song(type.row, if (on) FontWeight.Black else FontWeight.Normal, if (on) colors.spot else colors.dim))
                                    BasicText(" $n", style = Guga.numeral(type.caption, if (on) colors.spot else colors.dim))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            BasicText("按年份 ↓", style = Guga.note(type.caption, colors.dim))
                        }
                        Rule()
                    }
                    val shown = data.works.filter { filter == WorkFilter.All || (filter == WorkFilter.Behind) == it.behind }
                    for (w in shown) {
                        val press = rememberPressState()
                        Row(
                            Modifier.fillMaxWidth().pressGraphics(press).pressable(press, label = w.title.name) { graph.open(w.title.id) }.padding(vertical = 14.dp).testTag("work-row"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(76.dp).aspectRatio(2f / 3f)) {
                                CoverImage(w.title.art.poster, Modifier.fillMaxSize(), Framing(0.5f, 0.5f)) { HatchPlate(Rules.monogram(w.title.name, false), Modifier.fillMaxSize()) }
                            }
                            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                                // 片名两行截断（长片名单行省略会让几部片看起来一样）
                                BasicText(w.title.name, style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                // 角色名很长 / 多角色：单行省略
                                w.role?.let { BasicText(it, Modifier.padding(top = 5.dp), style = Guga.song(type.caption * 1.05f, FontWeight.Bold, colors.ink.copy(alpha = 0.86f)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                BasicText(
                                    listOfNotNull(w.title.year?.toString(), w.title.rating?.let { "$it 分" }, w.title.genres.firstOrNull()).joinToString(" · "),
                                    Modifier.padding(top = 4.dp),
                                    style = Guga.song(type.caption, color = colors.dim),
                                    maxLines = 1,
                                )
                            }
                        }
                        Rule()
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }
        BackRow(p.name, solid = scroll.value > solidAt, onBack = { graph.nav.backOrHome() })
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText("$label  ", style = Guga.song(Guga.type.caption, color = Guga.colors.dim), maxLines = 1)
        BasicText(value, style = Guga.song(Guga.type.caption * 1.1f, FontWeight.Bold), maxLines = 1)
    }
}
