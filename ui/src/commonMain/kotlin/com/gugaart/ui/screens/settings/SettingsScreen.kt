package com.gugaart.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.components.Rule
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.screens.LocalPageState
import com.gugaart.ui.screens.PageEnd
import com.gugaart.ui.screens.PageTab
import com.gugaart.ui.screens.TabsRow
import com.gugaart.ui.screens.TitlePage
import com.gugaart.ui.screens.pageStateOf
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.settings.formatBytes
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/**
 * `Route.Settings(section)` 的入口。没有分区 = 目录页（六个主要分区 + 折叠「高级」）；有 = 那个分区的页；`licenses` = 关于下面的「开源许可」。
 * 设置项的键、默认值、取值范围全部是上游的（`app/settings/`，同一份 `gugu.settings.v1`），读写都走上游的 `graph.settings`；
 * 画报自己的项在 `gugaart.settings.v1`（[GugaSettingsStore]）。
 */
@Composable
fun SettingsScreen(section: String?) {
    // 「关于」下面的「开源许可」不是九个分区之一，是单独一页
    if (section == OpenSourceLicenses.KEY) return LicensesPage()
    val sec = GugaSection.of(section)
    if (sec == null) SettingsIndexPage() else SettingsSectionPage(sec)
}

/** 画报设置的那一个 store：根给了就用根的（改了整本画报立刻变），没给（离屏测试）自己造。 */
@Composable
internal fun rememberGugaStore(): GugaSettingsStore {
    val g = LocalGraph.current
    return LocalGugaSettings.current ?: remember(g) { GugaSettingsStore(g.deps.local) }
}

/** 目录页：六个主要分区（斜体编号 + 分区名 + 当前值 + 右端英文小标和箭头）+ 默认折叠的「高级」。 */
@Composable
private fun SettingsIndexPage() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val type = Guga.type
    val settings by g.settings.state.collectAsState()
    val prefs by rememberGugaStore().prefs.collectAsState()
    val session by g.session.session.collectAsState()
    var advancedOpen by remember { mutableStateOf(false) }
    // 缓存用量：图片 + 弹幕（知道了才写，不写 0）
    val cache by produceState<String?>(null) {
        val img = g.images.diskSize().bytes
        val dm = g.danmaku.commentCacheSize()?.bytes ?: 0L
        value = if (img + dm > 0) listOfNotNull(formatBytes(img.toDouble())?.let { "图片 $it" }, dm.takeIf { it > 0 }?.let { formatBytes(it.toDouble()) }?.let { "弹幕 $it" }).joinToString(" · ") else null
    }
    val webdavConfigured by produceState(false) {
        value = com.gugaart.ui.backup.WebDavConfig.load(g.deps.secure).complete
    }
    val danmakuConfigured = com.gugaart.ui.danmaku.rememberDanmakuConfigured()
    val facts = SummaryFacts(
        settings,
        prefs,
        session?.let { it.userName to it.serverName },
        g.deps.appVersion,
        cache,
        danmakuConfigured = danmakuConfigured,
        webdavConfigured = webdavConfigured,
        hasIdleHome = Guga.uiMode != com.gugugaga.ds.theme.UiMode.Phone,
    )
    TitlePage("设置 · SETTINGS", "设置", onBack = { g.nav.backOrHome() }, action = GuguIcon.Search, actionLabel = "搜索", onAction = { g.nav.navigate(Route.Search()) }, tag = "settings-index") {
        Column(Modifier.padding(horizontal = Guga.dimens.margin)) {
            Rule(strong = true)
            for (s in GugaSection.PRIMARY) {
                val press = rememberPressState()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 78.dp).pressGraphics(press).pressable(press, label = s.title, onClick = { g.nav.navigate(Route.Settings(s.key)) }).padding(vertical = 10.dp).testTag("settings-row-${s.key}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicText(s.number, Modifier.width(48.dp), style = Guga.numeral(type.section * 1.05f, colors.spot), maxLines = 1)
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        BasicText(s.title, style = Guga.song(type.section * 0.92f, FontWeight.Black, letterSpacing = 0.06.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(sectionSummary(s, facts), Modifier.padding(top = 4.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    BasicText(s.english, style = Guga.numeral(type.kicker, colors.faint, letterSpacing = 0.2.em), maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    Icon(GuguIcon.ChevronRight, colors.dim, 13.dp)
                }
                Rule()
            }
            val advPress = rememberPressState()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 72.dp).pressGraphics(advPress).pressable(advPress, label = "高级", onClick = { advancedOpen = !advancedOpen }).padding(vertical = 10.dp).testTag("settings-row-advanced"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 10.dp)) {
                    BasicText("高级", style = Guga.song(type.section * 0.88f, FontWeight.Black, letterSpacing = 0.06.em), maxLines = 1)
                    BasicText(sectionSummary(GugaSection.Advanced, facts), Modifier.padding(top = 4.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicText(GugaSection.Advanced.english, style = Guga.numeral(type.kicker, colors.faint, letterSpacing = 0.2.em), maxLines = 1)
                Spacer(Modifier.width(12.dp))
                Icon(if (advancedOpen) GuguIcon.ChevronDown else GuguIcon.ChevronRight, colors.dim, 13.dp)
            }
            Rule()
            AnimatedVisibility(advancedOpen, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    Modifier.fillMaxWidth()
                        .drawBehind { drawLine(colors.seal, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx()) }
                        .padding(start = 20.dp)
                        .testTag("settings-advanced-list"),
                ) {
                    for (s in GugaSection.ADVANCED_CHILDREN) {
                        val press = rememberPressState()
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 66.dp).pressGraphics(press).pressable(press, label = s.title, onClick = { g.nav.navigate(Route.Settings(s.key)) }).padding(vertical = 8.dp).testTag("settings-row-${s.key}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                                BasicText(s.title, style = Guga.song(type.row, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                BasicText(sectionSummary(s, facts), Modifier.padding(top = 3.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(GuguIcon.ChevronRight, colors.dim, 13.dp)
                        }
                        Rule()
                    }
                }
            }
        }
    }
}

/** 一个分区的页：栏目行 + 大标题 + 页签 + 这一区的行。 */
@Composable
private fun SettingsSectionPage(section: GugaSection) {
    val g = LocalGraph.current
    val pick = pageStateOf(LocalPageState.current, "pick")
    val displaySection = if (section == GugaSection.Diagnostics) GugaSection.About else section
    val tabs = if (displaySection.advanced) GugaSection.ADVANCED_CHILDREN else GugaSection.PRIMARY
    val activeTab = if (displaySection == GugaSection.Advanced) GugaSection.Strm else displaySection
    val initial = with(LocalDensity.current) { ((tabs.indexOf(activeTab).coerceAtLeast(0) - 1).coerceAtLeast(0) * 74).dp.toPx().toInt() }
    val kicker = if (displaySection.advanced) "设置 · 高级 · ${displaySection.english}" else "设置 · ${displaySection.number} / 0${GugaSection.PRIMARY.size} · ${displaySection.english}"
    TitlePage(
        kicker,
        displaySection.title,
        onBack = { g.nav.backOrHome() },
        barTitle = "设置",
        action = GuguIcon.Search,
        actionLabel = "搜索",
        onAction = { g.nav.navigate(Route.Search()) },
        tag = "settings-${displaySection.key}",
    ) {
        if (displaySection != GugaSection.Advanced) {
            TabsRow(
                tabs.map { PageTab(it.key, it.title) },
                activeTab.key,
                { key -> if (key != displaySection.key) g.nav.replace(Route.Settings(key)) },
                scrollable = true,
                initialScroll = initial,
            )
        }
        Column(Modifier.padding(horizontal = Guga.dimens.margin), verticalArrangement = Arrangement.Top) {
            when (section) {
                GugaSection.Account -> AccountSection()
                GugaSection.Playback -> PlaybackSection(pick)
                GugaSection.Subs -> SubsSection(pick)
                GugaSection.Danmaku -> DanmakuSection(pick)
                GugaSection.Appearance -> AppearanceSection(pick)
                GugaSection.Backup -> BackupSection()
                GugaSection.Advanced -> AdvancedSection(pick == "diag")
                GugaSection.Strm -> StrmSection()
                GugaSection.Cache -> CacheSection()
                GugaSection.About -> AboutSection(startDiagOpen = pick == "diag")
                GugaSection.Diagnostics -> AboutSection(startDiagOpen = true)
            }
        }
        PageEnd(displaySection.title)
    }
}
