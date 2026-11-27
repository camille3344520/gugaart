package com.gugaart.ui.screens.history

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.StateView
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.screens.GroupLabel
import com.gugaart.ui.screens.LocalPageState
import com.gugaart.ui.screens.PageTab
import com.gugaart.ui.screens.TabsRow
import com.gugaart.ui.screens.TitlePage
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.pageStateOf
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.history.HistoryGroupId
import com.gugugaga.app.history.historyGroupFor
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.currentTimeMillis
import com.gugugaga.core.lastPlayedMs
import com.gugugaga.core.mergeHistory
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/** 记录页的一条：界面模型 + 上次播放的时刻（分组用；服务器没给 = 0 → 归到「更早」）。 */
@Immutable
data class HistoryEntry(val title: Title, val playedMs: Long)

/** 记录页的页签。 */
enum class HistoryFilter(val id: String, val label: String) {
    All("all", "全部"), Series("series", "剧集"), Movies("movies", "电影");

    fun accepts(t: Title): Boolean = when (this) {
        All -> true
        Series -> t.kind == Kind.Episode
        Movies -> t.kind == Kind.Movie
    }

    companion object {
        fun of(id: String?): HistoryFilter = entries.firstOrNull { it.id == id } ?: All
    }
}

private val FIELDS = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio", "Overview"))

/**
 * 观看记录（只读）：和上游的记录页同一个取法——「继续观看」（带进度）+「看完的」（按上次播放时间），用上游 :core 的 `mergeHistory` 并成一列。
 */
suspend fun loadHistory(client: EmbyClient): List<HistoryEntry> {
    val resume = client.getResume(limit = 40, o = FIELDS).items
    val played = client.getPlayedHistory(0, 60, FIELDS).items
    return mergeHistory(resume, played).map { HistoryEntry(client.titleOf(it), lastPlayedMs(it)) }
}

/** 按「今天 / 昨天 / 本周 / 更早」分组（分组规则是上游的 `historyGroupFor`：按本地日历日）。空的组不出现，组内顺序不变。纯函数。 */
fun groupEntries(entries: List<HistoryEntry>, now: Long, zone: (Long) -> Long): List<Pair<HistoryGroupId, List<HistoryEntry>>> {
    val buckets = LinkedHashMap<HistoryGroupId, MutableList<HistoryEntry>>()
    for (e in entries) buckets.getOrPut(historyGroupFor(e.playedMs, now, zone)) { ArrayList() }.add(e)
    return HistoryGroupId.entries.filter { it in buckets }.map { it to buckets.getValue(it) }
}

/** 一行的副行：「第 5 集 · 看到 59% · 余 19 分」「看到 42% · 余 78 分」「第 2 集 · 未看」「第 3 集 · 已看」。纯函数。 */
fun historyLine(t: Title): String {
    val ep = if (t.kind == Kind.Episode) t.episodeNo?.let { "第 $it 集" } else null
    val state = when {
        t.progress > 0f -> listOfNotNull("看到 ${t.pct ?: 0}%", t.runtimeSec?.let { "余 ${Rules.minutesOnly((it * (1f - t.progress)).toLong())}" }).joinToString(" · ")
        t.played -> "已看"
        else -> "未看"
    }
    return listOfNotNull(ep, state).joinToString(" · ")
}

/** 行首的方框字：剧 / 影。 */
fun kindMark(t: Title): String = if (t.kind == Kind.Movie) "影" else "剧"

/**
 * 记录（设计稿 `p=hist`，路由 `Route.History`）：页签（全部 / 剧集 / 电影；观看状态只有未看 / 已看，不按进度另分一组）+ 按天分组的行（方框字 + 片名 + 进度 + 缩略图）。
 * 记录在服务器上、跟着账号走；这一页**只读**——设计稿右上的「清空」要逐条改服务器上的已看状态，这一版不做（见 impl-status）。
 */
@Composable
fun HistoryScreen() {
    val graph = LocalGraph.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val load = rememberLoad("history") { loadHistory(it) }
    val preset = pageStateOf(LocalPageState.current, "tab")
    var filter by remember { mutableStateOf(HistoryFilter.of(preset)) }
    val all = load.value
    TitlePage("记录 · HISTORY", "记录", onBack = { graph.nav.backOrHome() }, barTitle = "记录", tag = "history-page") {
        TabsRow(HistoryFilter.entries.map { f -> PageTab(f.id, f.label, all?.count { f.accepts(it.title) }) }, filter.id, { filter = HistoryFilter.of(it) })
        Column(Modifier.padding(horizontal = dimens.margin)) {
            when {
                all == null && load.error != null -> ErrorView(load.error!!, load::retry)
                all == null -> if (load.showSkeleton) {
                    LoadingView()
                    repeat(3) {
                        Skeleton(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(58.dp))
                        Rule()
                    }
                }
                all.isEmpty() -> StateView("state-history-empty", "捧白纸", "还没有观看记录", "看过的每一集都会记在这里，换台设备也能接着看。", action = "去片库看看", onAction = { graph.nav.reset(Route.Home) })
                else -> {
                    val shown = all.filter { filter.accepts(it.title) }
                    if (shown.isEmpty()) {
                        BasicText("没有记录", Modifier.padding(vertical = 28.dp), style = Guga.song(Guga.type.caption, color = colors.dim))
                    }
                    val now = remember { currentTimeMillis() }
                    for ((group, list) in groupEntries(shown, now) { com.gugugaga.app.platform.localUtcOffsetMs(it) }) {
                        GroupLabel(group.label)
                        for (e in list) {
                            HistoryRow(e.title) { graph.open(e.title.id) }
                            Rule()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(t: Title, onClick: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    val press = rememberPressState()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press).pressable(press, label = t.displayName, onClick = onClick).padding(vertical = 14.dp).testTag("history-row"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 方框字：专色线框，不是实底（专色不当底色）
        Box(Modifier.size(30.dp).border(1.dp, colors.spot), contentAlignment = Alignment.Center) {
            BasicText(kindMark(t), style = Guga.song(type.caption * 1.1f, FontWeight.Black, colors.spot), maxLines = 1)
        }
        Column(Modifier.weight(1f).padding(start = 34.dp, end = 12.dp)) {
            BasicText(t.displayName, style = Guga.song(type.row * 1.06f, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(historyLine(t), Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Thumb(t.art.thumb ?: t.art.backdrop, Rules.monogram(t.displayName, person = false), 104.dp, progress = t.progress)
    }
}
