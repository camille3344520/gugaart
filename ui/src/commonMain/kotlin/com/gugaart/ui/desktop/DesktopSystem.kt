package com.gugaart.ui.desktop

import com.gugaart.ui.screens.me.Specials
import com.gugaart.ui.screens.me.SpecialNews
import com.gugaart.ui.screens.me.SpecialGrid
import com.gugaart.ui.screens.me.RankBadge
import androidx.compose.foundation.background
import com.gugugaga.ds.physics.pressGraphics
import com.gugaart.ui.screens.me.Ranks
import com.gugaart.ui.screens.me.RankNews
import com.gugaart.ui.screens.me.RankBoard
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.gugaart.ui.components.PosterCard
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.Seal
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.desktop.detail.DGrid
import com.gugaart.ui.desktop.detail.DKbd
import com.gugaart.ui.desktop.detail.DSec
import com.gugaart.ui.desktop.detail.DWidth
import com.gugaart.ui.desktop.detail.autoColumns
import com.gugaart.ui.desktop.detail.contextClick
import com.gugaart.ui.desktop.detail.dWidth
import com.gugaart.ui.desktop.detail.hover
import com.gugaart.ui.desktop.detail.rememberHover
import com.gugaart.ui.desktop.detail.rsp
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.screens.LocalPageState
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.favs.loadFavs
import com.gugaart.ui.screens.history.historyLine
import com.gugaart.ui.screens.history.loadHistory
import com.gugaart.ui.screens.me.loadMe
import com.gugaart.ui.screens.me.subscriberNo
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.pageStateOf
import com.gugaart.ui.screens.settings.AboutSection
import com.gugaart.ui.screens.settings.AccountSection
import com.gugaart.ui.screens.settings.CacheSection
import com.gugaart.ui.screens.settings.DanmakuSection
import com.gugaart.ui.screens.settings.GugaSection
import com.gugaart.ui.screens.settings.LayoutSection
import com.gugaart.ui.screens.settings.LicensesBody
import com.gugaart.ui.screens.settings.OpenSourceLicenses
import com.gugaart.ui.screens.settings.LookSection
import com.gugaart.ui.screens.settings.PlaybackSection
import com.gugaart.ui.screens.settings.StrmSection
import com.gugaart.ui.screens.settings.SubsSection
import com.gugaart.ui.screens.settings.SummaryFacts
import com.gugaart.ui.screens.settings.rememberGugaStore
import com.gugaart.ui.screens.settings.sectionSummary
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.KeyEventLike
import com.gugugaga.app.player.KeyState
import com.gugugaga.app.player.PlayerKeyCommand
import com.gugugaga.app.player.PlayerKeys
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.LocalWindowDp
import com.gugugaga.ds.theme.UiMode

/** 电脑「我的 / 设置」的版式数（rem）。 */
object DesktopSystemSpec {
    /** 设置：左栏（分区）宽、两栏之间。 */
    const val SETTINGS_NAV = 28f
    const val SETTINGS_GAP = 6f
    /** 记录行的缩略图宽。 */
    const val HISTORY_THUMB = 16f
    /** 收藏墙的最小卡宽。 */
    const val FAV_MIN = 15.6f
}

/* ============================== 我的 ============================== */

/**
 * 我的（设计稿 `p=me`）：读者证（她的固定位置之一）+ 记录 / 收藏在同一页。[tab]：`Route.History(tab)` 的 tab（"favorites" = 收藏）；
 * 截图预置 `fav` / `favempty` 也落在收藏。数据复用手机的 `loadMe` / `loadHistory` / `loadFavs`（只读）。
 * 和设计稿的差异：记录不按「今天 / 昨天」分组（设计稿里的分组日期是摆的）；「本月看完」换成真实的「看过」部数（电影一部、剧集一季算一部，
 * 和读者证上的称号同一个数）。
 */
@Composable
fun DesktopMeScreen(today: Today, tab: String?) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val state = LocalPreset.current.state
    val m = Guga.dimens.margin
    val session by g.session.session.collectAsState()
    var fav by remember { mutableStateOf(tab == "favorites" || state == "fav" || state == "favempty") }
    val me = rememberLoad("me", session?.userId) { loadMe(it) }
    val history = rememberLoad("history") { loadHistory(it) }
    val favs = rememberLoad("favs", "SortName") { loadFavs(it, "SortName") }
    DesktopPage(Modifier.testTag("desktop-me")) {
        Column(Modifier.padding(horizontal = m)) {
            // ---- 读者证
            Rule(strong = true)
            Row(Modifier.fillMaxWidth().padding(vertical = Guga.rem(2.4f)).testTag("me-card"), verticalAlignment = Alignment.CenterVertically) {
                if (LocalGugaPrefs.current.showMascot) {
                    Byline(Guga.rem(8.4f), live = false)
                    Spacer(Modifier.width(Guga.rem(2.8f)))
                }
                Column(Modifier.weight(1f)) {
                    BasicText("MEMBER · No. ${subscriberNo(today.volume)}", style = Guga.numeral(rsp(1.05f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                    BasicText("观影卡 · ${session?.userName ?: "影迷"}", Modifier.padding(vertical = Guga.rem(0.4f)), style = Guga.song(rsp(3.4f), FontWeight.Black, letterSpacing = 0.14.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText("陪你看片第 ${today.volume} 天 · ${session?.serverName ?: ""}", style = Guga.note(rsp(1.4f), colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // 称号（看过几部 → 第几级）：点了出称号纸片
                    me.value?.played?.let { played ->
                        val level = Ranks.levelOf(played)
                        val press = rememberPressState()
                        Row(
                            Modifier.padding(top = Guga.rem(1.2f)).pressGraphics(press).pressable(press, label = "称号 ${Ranks.badge(played)}", onClick = { RankBoard.open(RankNews(level, played, browse = true)) }).testTag("me-rank"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RankBadge(level, Guga.rem(3.6f))
                            BasicText(Ranks.of(level)?.title ?: "还没有称号", Modifier.padding(start = Guga.rem(1f)), style = Guga.song(rsp(1.8f), FontWeight.Black), maxLines = 1)
                            BasicText(Ranks.nextLine(played) + " · 全部称号 ›", Modifier.padding(start = Guga.rem(1.2f)), style = Guga.song(rsp(1.3f), color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Guga.rem(3.6f)), verticalAlignment = Alignment.CenterVertically) {
                    val d = me.value
                    MeStat(d?.watching?.toString() ?: "—", "在看")
                    MeStat(d?.favorites?.toString() ?: "—", "收藏")
                    MeStat(d?.played?.toString() ?: "—", "看过")
                    Column(verticalArrangement = Arrangement.spacedBy(Guga.rem(1f))) {
                        MeLink(GuguIcon.UserRound, "切换账号 / 服务器") { g.nav.navigate(Route.Servers) }
                        MeLink(GuguIcon.Settings, "设置") { g.nav.navigate(Route.Settings()) }
                    }
                }
            }
            // ---- 特别称号：一页一排十二枚，左右翻页（拿到的排前面、彩色）；点一枚看拿法
            Rule()
            Row(Modifier.fillMaxWidth().padding(vertical = Guga.rem(1.8f)).testTag("me-specials"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.width(Guga.rem(16f))) {
                    BasicText("特别称号", style = Guga.song(rsp(2.2f), FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
                    BasicText("${RankBoard.owned.size} / ${Specials.COUNT} · 点一枚看拿法", Modifier.padding(top = Guga.rem(0.4f)), style = Guga.note(rsp(1.3f), colors.dim), maxLines = 1)
                }
                SpecialGrid(RankBoard.owned, Modifier.weight(1f), columns = 12, badge = Guga.rem(5.6f), nameSize = rsp(1.15f), rows = 1) { id -> RankBoard.special = SpecialNews(listOf(id), browse = true) }
            }
            Rule(strong = true)
            // ---- 记录 / 收藏
            Row(Modifier.fillMaxWidth().padding(top = Guga.rem(1.6f)), verticalAlignment = Alignment.Bottom) {
                MeSeg("记录", !fav) { fav = false }
                Spacer(Modifier.width(Guga.rem(2.2f)))
                MeSeg("收藏", fav) { fav = true }
                Spacer(Modifier.weight(1f))
                val count = if (fav) favs.value?.items?.size?.let { "$it 部 · 收藏的" } else history.value?.size?.let { "$it 条 · 按最后观看时间" }
                if (count != null) BasicText(count, Modifier.padding(bottom = Guga.rem(0.6f)), style = Guga.note(rsp(1.3f), colors.faint), maxLines = 1)
            }
            Rule(strong = true)
            if (fav) {
                val items = favs.value?.items
                when {
                    items == null && favs.error != null -> ErrorView(favs.error!!, favs::retry)
                    items == null -> if (favs.showSkeleton) LoadingView()
                    items.isEmpty() || state == "favempty" -> Row(Modifier.fillMaxWidth().padding(vertical = Guga.rem(10f)).testTag("me-fav-empty"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        // 她捧白纸（G10）
                        MascotPoseNamed("捧白纸", Guga.rem(16f))
                        Column(Modifier.padding(start = Guga.rem(2.4f)).width(Guga.rem(40f))) {
                            BasicText("收藏夹还是空的", style = Guga.song(rsp(2.4f), FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
                            BasicText("在详情页点「收藏」，或者在任何一张海报上右键 → 收藏，就会收进这里。", Modifier.padding(top = Guga.rem(0.8f)), style = Guga.song(rsp(1.4f), color = colors.dim, lineHeight = rsp(2.5f)), maxLines = 3)
                        }
                    }
                    else -> {
                        val avail = (LocalWindowDp.current.width - m * 2).value
                        val cols = autoColumns(avail, Guga.rem(DesktopSystemSpec.FAV_MIN).value, Guga.dimens.gutter.value)
                        val card = (LocalWindowDp.current.width - m * 2 - Guga.dimens.gutter * (cols - 1)) / cols
                        DGrid(items, cols, Modifier.padding(top = Guga.rem(2.8f)).testTag("me-favs"), gapY = Guga.rem(3.2f)) { t ->
                            Box {
                                PosterCard(t.copy(favorite = false), card, { g.open(t.id) }, Modifier.contextClick(t), subtitle = listOfNotNull(t.year?.toString(), kindLabel(t)).joinToString(" · "))
                                // 每一张都带着「藏」字章
                                Seal(Modifier.align(Alignment.TopEnd).padding(Guga.rem(0.8f)), size = Guga.rem(3f))
                            }
                        }
                    }
                }
            } else {
                val list = history.value
                when {
                    list == null && history.error != null -> ErrorView(history.error!!, history::retry)
                    list == null -> if (history.showSkeleton) LoadingView()
                    list.isEmpty() -> BasicText("还没有观看记录。", Modifier.padding(vertical = Guga.rem(6f)), style = Guga.song(rsp(1.4f), color = colors.faint), maxLines = 1)
                    else -> Column(Modifier.testTag("me-history")) { for (e in list) HistoryLine(e.title) }
                }
            }
        }
    }
}

private fun kindLabel(t: Title): String = when (t.kind) {
    Kind.Movie -> "电影"
    Kind.Series -> "剧集"
    Kind.Episode -> "单集"
    Kind.BoxSet -> "合集"
    Kind.Person -> "人物"
    else -> ""
}

@Composable
private fun MeStat(value: String, label: String) {
    Column {
        BasicText(value, style = Guga.numeral(rsp(4.4f)).copy(lineHeight = rsp(4.4f)), maxLines = 1)
        BasicText(label, Modifier.padding(top = Guga.rem(0.6f)), style = Guga.song(rsp(1.2f), color = Guga.colors.faint), maxLines = 1)
    }
}

@Composable
private fun MeLink(icon: GuguIcon, text: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val hover = rememberHover()
    val ink = if (hover.value) colors.spot else colors.ink
    Row(
        Modifier.hover(hover).deskFocusRing(press).pressable(press, label = text, onClick = onClick).drawBehind { drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(bottom = Guga.rem(0.2f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.6f)),
    ) {
        Icon(icon, ink, Guga.rem(1.4f))
        BasicText(text, style = Guga.song(rsp(1.4f), FontWeight.SemiBold, ink), maxLines = 1)
    }
}

/** 记录 / 收藏的页签：选中 = 米白字 + 专色下划线（不靠颜色单独表达）。 */
@Composable
private fun MeSeg(text: String, on: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    BasicText(
        text,
        Modifier.deskFocusRing(press).pressable(press, label = text, onClick = onClick).drawBehind { if (on) drawLine(colors.spot, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }.padding(vertical = Guga.rem(0.6f)).testTag(if (on) "me-seg-on" else "me-seg"),
        style = Guga.song(rsp(1.8f), FontWeight.Black, if (on) colors.ink else colors.faint, letterSpacing = 0.1.em),
        maxLines = 1,
    )
}

/** 记录的一行：16 rem 的横图（进度线）| 片名 / 副行 | 进度百分比（0 = 未开始）。悬停片名变专色，右键 = 条目菜单。 */
@Composable
private fun HistoryLine(t: Title) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val hover = rememberHover()
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().hover(hover).contextClick(t).deskFocusRing(press).pressable(press, label = t.displayName, onClick = { g.open(t.id) }).padding(vertical = Guga.rem(1.4f)).testTag("me-history-row"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(Guga.rem(DesktopSystemSpec.HISTORY_THUMB)).aspectRatio(16f / 9f).border(1.dp, colors.line)) {
            CoverImage(t.art.thumb ?: t.art.backdrop, Modifier.fillMaxSize(), Framing(0.5f, 0.35f)) { HatchPlate(Rules.monogram(t.displayName, person = false), Modifier.fillMaxSize(), caption = "NO STILL", textSize = rsp(1.5f)) }
            if (t.progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(t.progress.coerceIn(0f, 1f)).padding(top = 0.dp).drawBehind { drawRect(colors.spot, Offset(0f, size.height - 3.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())) }.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = Guga.rem(1.6f))) {
            BasicText(t.displayName, style = Guga.song(rsp(1.65f), FontWeight.Bold, if (hover.value) colors.spot else colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(historyLine(t), Modifier.padding(top = Guga.rem(0.3f)), style = Guga.note(rsp(1.25f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (t.pct != null) BasicText("${t.pct}%", style = Guga.numeral(rsp(1.6f), colors.dim), maxLines = 1)
        else BasicText(if (t.played) "看完" else "未开始", style = Guga.note(rsp(1.4f), colors.dim), maxLines = 1)
    }
    Rule()
}

/* ============================== 设置（两栏） ============================== */

/**
 * 设置（设计稿 `p=settings`）：左栏九个分区（编号 / 分区名 / 英文小标，当前的专色），右栏这一区的条目。
 * 设置树、键、写回**完全是手机那一套**（`screens/settings/SettingsSections.kt`，上游的键，不新增）：右栏直接嵌手机的分区内容，只重排外框；
 * 播放分区多一张快捷键表（键位从上游 `PlayerKeys` 解析出来，不手抄）。没给分区 = 第一个（外观）。
 */
@Composable
fun DesktopSettingsScreen(section: String?, today: Today) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val preset = LocalPreset.current
    val wanted = section ?: when (preset.state) {
        "ui" -> "interface"
        "play", "dd" -> "playback"
        "strm" -> "strm"
        "sub" -> "subtitles"
        "dm" -> "danmaku"
        "cache" -> "cache"
        "acct" -> "account"
        "about" -> "about"
        else -> null
    }
    // 「开源许可」是关于下面的一页：左栏亮着「关于」，右栏换成许可清单
    val licenses = wanted == OpenSourceLicenses.KEY
    val sec = if (licenses) GugaSection.About else GugaSection.of(wanted) ?: GugaSection.Look
    val settings by g.settings.state.collectAsState()
    val prefs by rememberGugaStore().prefs.collectAsState()
    val session by g.session.session.collectAsState()
    val facts = SummaryFacts(settings, prefs, session?.let { it.userName to it.serverName }, g.deps.appVersion, null)
    // 截图预置 `dd`：首选引擎的选择器推开着（纸面语言，推开不盖）
    val pageState = LocalPageState.current ?: "pick=engine".takeIf { preset.state == "dd" }
    val pick = pageStateOf(pageState, "pick")
    DesktopPage(Modifier.testTag(if (licenses) "desktop-settings-licenses" else "desktop-settings-${sec.key}")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Guga.dimens.margin)) {
            Column(Modifier.width(Guga.rem(if (dWidth() == DWidth.Sm) 22f else DesktopSystemSpec.SETTINGS_NAV)).testTag("settings-nav")) {
                Rule(strong = true)
                for (s in GugaSection.entries) {
                    val on = s == sec
                    val hover = rememberHover()
                    val press = rememberPressState()
                    val ink = if (on || hover.value) colors.spot else colors.ink
                    Row(
                        Modifier.fillMaxWidth().hover(hover).deskFocusRing(press).pressable(press, label = s.title, onClick = { if (!on || licenses) g.nav.replace(Route.Settings(s.key)) }).padding(vertical = Guga.rem(1.5f)).testTag(if (on) "settings-nav-on" else "settings-nav-row"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicText(s.number, Modifier.width(Guga.rem(3.6f)), style = Guga.numeral(rsp(1.3f), if (on) colors.spot else colors.faint), maxLines = 1)
                        BasicText(s.title, Modifier.weight(1f), style = Guga.song(rsp(1.7f), FontWeight.Black, ink, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(s.english, style = Guga.numeral(rsp(1.05f), colors.faint, letterSpacing = 0.1.em), maxLines = 1)
                    }
                    Rule()
                }
                BasicText("${DesktopCommand.Settings.hint(LocalDesktopHost.current.modifier) ?: "Ctrl ,"} 打开设置 · Esc 返回", Modifier.padding(top = Guga.rem(1.6f)), style = Guga.note(rsp(1.1f), colors.faint), maxLines = 1)
            }
            Spacer(Modifier.width(Guga.rem(if (dWidth() == DWidth.Sm) 3.2f else DesktopSystemSpec.SETTINGS_GAP)))
            Column(Modifier.weight(1f).testTag("settings-body")) {
                Row(verticalAlignment = Alignment.Bottom) {
                    BasicText(if (licenses) "开源许可" else sec.title, style = Guga.song(rsp(5.2f), FontWeight.Black, lineHeight = rsp(6f), letterSpacing = 0.06.em), maxLines = 1)
                    BasicText(if (licenses) "LICENSES" else sec.english, Modifier.padding(start = Guga.rem(1.6f), bottom = Guga.rem(1.2f)), style = Guga.numeral(rsp(1.15f), colors.spot, letterSpacing = 0.24.em), maxLines = 1)
                }
                BasicText(if (licenses) OpenSourceLicenses.INTRO else sectionSummary(sec, facts), Modifier.padding(top = Guga.rem(0.6f), bottom = Guga.rem(2.4f)), style = Guga.song(rsp(1.4f), color = colors.dim), maxLines = if (licenses) 2 else 1, overflow = TextOverflow.Ellipsis)
                if (licenses) LicensesBody() else CompositionLocalProvider(LocalPageState provides pageState) {
                    when (sec) {
                        GugaSection.Look -> LookSection(pick)
                        GugaSection.Layout -> LayoutSection()
                        GugaSection.Playback -> {
                            PlaybackSection(pick)
                            ShortcutTable(Modifier.padding(top = Guga.rem(4f)))
                        }
                        GugaSection.Strm -> StrmSection()
                        GugaSection.Subs -> SubsSection(pick)
                        GugaSection.Danmaku -> DanmakuSection(pick)
                        GugaSection.Cache -> CacheSection()
                        GugaSection.Account -> AccountSection()
                        GugaSection.About -> AboutSection()
                    }
                }
            }
        }
    }
}

/** 快捷键表的一行：键帽上印什么、做什么。 */
data class Shortcut(val keys: String, val does: String)

private fun commandLabel(c: PlayerKeyCommand): String? = when (c) {
    PlayerKeyCommand.PlayPause -> "播放 / 暂停"
    is PlayerKeyCommand.Seek -> (if (c.dir < 0) "快退" else "快进") + if (c.big == true) "（三倍步长）" else ""
    is PlayerKeyCommand.Volume -> if (c.delta > 0) "音量 +" else "音量 −"
    PlayerKeyCommand.Mute -> "静音"
    PlayerKeyCommand.Fullscreen -> "全屏"
    PlayerKeyCommand.Danmaku -> "弹幕开 / 关"
    is PlayerKeyCommand.Rate -> if (c.dir > 0) "加速一档" else "减速一档"
    PlayerKeyCommand.Next -> "下一集"
    PlayerKeyCommand.Prev -> "上一集"
    PlayerKeyCommand.NextPeak -> "跳到下一个弹幕高能点"
    PlayerKeyCommand.Subtitles -> "字幕"
    PlayerKeyCommand.Favorite -> "收藏 / 取消收藏"
    is PlayerKeyCommand.Fraction -> if (c.f == 0.0) "回到开头" else null
    else -> null
}

/**
 * 播放器的快捷键表：**问上游的 `PlayerKeys`**「这个键在电脑上做什么」，把有动作的排出来（键位改了这张表自己跟着变）。纯函数，有单测。
 * 0–9 合成一行（跳到 0 %–90 %）。
 */
fun playerShortcuts(): List<Shortcut> {
    val st = KeyState(UiMode.Desktop, controlsVisible = true, skipVisible = false)
    val probes = listOf(
        "空格" to KeyEventLike(" "), "K" to KeyEventLike("k"), "←" to KeyEventLike("ArrowLeft"), "→" to KeyEventLike("ArrowRight"),
        "Shift ←" to KeyEventLike("ArrowLeft", shiftKey = true), "Shift →" to KeyEventLike("ArrowRight", shiftKey = true), "J" to KeyEventLike("j"), "L" to KeyEventLike("l"),
        "↑" to KeyEventLike("ArrowUp"), "↓" to KeyEventLike("ArrowDown"), "M" to KeyEventLike("m"), "F" to KeyEventLike("f"), "D" to KeyEventLike("d"), "C" to KeyEventLike("c"),
        "[" to KeyEventLike("["), "]" to KeyEventLike("]"), "N" to KeyEventLike("n"), "Shift N" to KeyEventLike("N", shiftKey = true), "Shift P" to KeyEventLike("P", shiftKey = true),
        "Home" to KeyEventLike("Home"), "V" to KeyEventLike("v"),
    )
    val rows = probes.mapNotNull { (cap, e) -> PlayerKeys.resolve(e, st)?.let(::commandLabel)?.let { cap to it } }
    // 同一个动作的几个键并成一行
    val merged = rows.groupBy({ it.second }, { it.first }).map { (does, caps) -> Shortcut(caps.joinToString(" / "), does) }
    val digits = if (PlayerKeys.resolve(KeyEventLike("5"), st) is PlayerKeyCommand.Fraction) listOf(Shortcut("0 – 9", "跳到 0 % – 90 %")) else emptyList()
    return merged + digits + Shortcut("Esc", "收起面板 / 退出全屏 / 返回")
}

/** 设置 · 播放里的快捷键表（两列）。 */
@Composable
private fun ShortcutTable(modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val rows = remember { playerShortcuts() }
    Column(modifier.testTag("settings-shortcuts")) {
        DSec(null, "快捷键", "SHORTCUTS", trailing = "播放器里 · ${rows.size} 组")
        DGrid(rows, 2, gapX = Guga.dimens.gutter * 2) { r ->
            Column {
                Row(Modifier.fillMaxWidth().padding(vertical = Guga.rem(1.1f)), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(r.does, Modifier.weight(1f), style = Guga.song(rsp(1.4f), FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    DKbd(r.keys)
                }
                Rule()
            }
        }
        val mod = LocalDesktopHost.current.modifier
        BasicText(
            "别处：${DesktopCommand.Search.hint(mod) ?: "Ctrl K"} 搜索 · ${DesktopCommand.Settings.hint(mod) ?: "Ctrl ,"} 设置 · ${DesktopCommand.Filter.hint(mod) ?: "Ctrl F"} 筛选 · 详情页 V 收藏 / W 标记已看 / [ ] 换季 / Enter 播放",
            Modifier.padding(top = Guga.rem(1.4f)), style = Guga.note(rsp(1.15f), colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        // macOS：多一张 ⌘ 键位表（内容 = cmdHint 非空的命令；M2-3）
        if (mod == ModifierGlyph.Cmd) {
            val cmdRows = remember { DesktopCommand.entries.filter { it.cmdHint != null }.map { Shortcut(it.cmdHint!!, it.label) } }
            DSec(null, "⌘ 键位（macOS）", "COMMAND", trailing = "${cmdRows.size} 组")
            DGrid(cmdRows, 2, gapX = Guga.dimens.gutter * 2) { r ->
                Column {
                    Row(Modifier.fillMaxWidth().padding(vertical = Guga.rem(1.1f)), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(r.does, Modifier.weight(1f), style = Guga.song(rsp(1.4f), FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        DKbd(r.keys)
                    }
                    Rule()
                }
            }
        }
    }
}

/* ============================== 登录 / 更新 ============================== */

/**
 * 服务器列表 / 添加 / 登录（设计稿 `p=servers`）：电脑的跨页版式在 `DesktopLogin.kt`，流程是 `screens/login/` 的那一份。
 * [chrome] = 外壳（报头 + 玻璃排）在不在：已登录时从账号菜单进来 = 在。
 */
@Composable
fun DesktopLoginScreen(today: Today, chrome: Boolean = false) {
    DesktopLogin(today, chrome)
}

/** 更新号外：电脑没有独立画板，这一版沿用手机的号外纸片（G17 已有）。 */
@Composable
fun DesktopUpdateExtra() {
    com.gugaart.ui.screens.update.UpdateExtra()
}
