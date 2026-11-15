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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * `Route.Settings(section)` 的入口（设计稿 `p=settings`）。没有分区 = 目录页（九行）；有 = 那个分区的页。
 * 设置项的键、默认值、取值范围全部是上游的（`app/settings/`，同一份 `gugu.settings.v1`），读写都走上游的 `graph.settings`；
 * 画报自己的四项在 `gugaart.settings.v1`（[GugaSettingsStore]）。这里只重画外观。
 */
@Composable
fun SettingsScreen(section: String?) {
    val sec = GugaSection.of(section)
    if (sec == null) SettingsIndexPage() else SettingsSectionPage(sec)
}

/** 画报设置的那一个 store：根给了就用根的（改了整本画报立刻变），没给（离屏测试）自己造。 */
@Composable
internal fun rememberGugaStore(): GugaSettingsStore {
    val g = LocalGraph.current
    return LocalGugaSettings.current ?: remember(g) { GugaSettingsStore(g.deps.local) }
}

/** 目录页：九行，每行 = 斜体编号 + 分区名 + 当前值 + 右端的英文小标和箭头。 */
@Composable
private fun SettingsIndexPage() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val type = Guga.type
    val settings by g.settings.state.collectAsState()
    val prefs by rememberGugaStore().prefs.collectAsState()
    val session by g.session.session.collectAsState()
    // 缓存用量：图片 + 弹幕（知道了才写，不写 0）
    val cache by produceState<String?>(null) {
        val img = g.images.diskSize().bytes
        val dm = g.danmaku.commentCacheSize()?.bytes ?: 0L
        value = if (img + dm > 0) listOfNotNull(formatBytes(img.toDouble())?.let { "图片 $it" }, dm.takeIf { it > 0 }?.let { formatBytes(it.toDouble()) }?.let { "弹幕 $it" }).joinToString(" · ") else null
    }
    val facts = SummaryFacts(settings, prefs, session?.let { it.userName to it.serverName }, g.deps.appVersion, cache)
    TitlePage("设置 · SETTINGS", "设置", onBack = { g.nav.backOrHome() }, action = GuguIcon.Search, actionLabel = "搜索", onAction = { g.nav.navigate(Route.Search()) }, tag = "settings-index") {
        Column(Modifier.padding(horizontal = Guga.dimens.margin)) {
            Rule(strong = true)
            for (s in GugaSection.entries) {
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
        }
    }
}

/** 一个分区的页：栏目行「设置 · 03 / 09 · PLAYBACK」+ 大标题 + 九个分区的页签（横滑，当前的专色）+ 这一区的行。 */
@Composable
private fun SettingsSectionPage(section: GugaSection) {
    val g = LocalGraph.current
    val pick = pageStateOf(LocalPageState.current, "pick")
    // 页签一开始滚到当前分区附近（当前项不被裁在屏幕外）
    val initial = with(LocalDensity.current) { ((section.ordinal - 1).coerceAtLeast(0) * 74).dp.toPx().toInt() }
    TitlePage(
        "设置 · ${section.number} / 0${GugaSection.entries.size} · ${section.english}",
        section.title,
        onBack = { g.nav.backOrHome() },
        barTitle = "设置",
        action = GuguIcon.Search,
        actionLabel = "搜索",
        onAction = { g.nav.navigate(Route.Search()) },
        tag = "settings-${section.key}",
    ) {
        TabsRow(
            GugaSection.entries.map { PageTab(it.key, it.title) },
            section.key,
            // 换分区 = 换掉当前这一页（返回栈里只留一个分区页，返回直接回目录）
            { key -> if (key != section.key) g.nav.replace(Route.Settings(key)) },
            scrollable = true,
            initialScroll = initial,
        )
        Column(Modifier.padding(horizontal = Guga.dimens.margin), verticalArrangement = Arrangement.Top) {
            when (section) {
                GugaSection.Look -> LookSection(pick)
                GugaSection.Layout -> LayoutSection()
                GugaSection.Playback -> PlaybackSection(pick)
                GugaSection.Strm -> StrmSection()
                GugaSection.Subs -> SubsSection(pick)
                GugaSection.Danmaku -> DanmakuSection(pick)
                GugaSection.Cache -> CacheSection()
                GugaSection.Account -> AccountSection()
                GugaSection.About -> AboutSection()
            }
        }
        PageEnd(section.title)
    }
}
