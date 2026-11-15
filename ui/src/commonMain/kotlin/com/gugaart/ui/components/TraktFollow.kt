package com.gugaart.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.gugaart.ui.screens.open
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.trakt.CalendarDay
import com.gugaart.ui.trakt.TraktService
import com.gugaart.ui.trakt.TraktState
import com.gugugaga.app.platform.localUtcOffsetMs
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.currentTimeMillis
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Trakt 追更（参考 CapyPlayer：「详情页面 - Trakt增加追更」「Trakt 追更日历」；用户 2026-11-15 选了「Trakt 追更 + 追更日历」）。
 * 追更 = 把这部剧加进 Trakt 的想看清单；追更日历 = Trakt 的 /calendars/my/shows（追的、看过的剧接下来要播的集）。
 * 没连 Trakt：这两样都不出现（按钮和日历一起藏起来，界面和以前一样）。
 */

/** 详情页「追更」按钮要的：现在追没追、正在改、点一下。 */
class TraktFollowUi(val following: Boolean, val busy: Boolean, val toggle: () -> Unit)

/** 这部剧的「追更」；没连 Trakt / 这部剧没有刮削编号 = null（按钮不出现）。 */
@Composable
fun rememberTraktFollow(seriesId: String): TraktFollowUi? {
    val g = LocalGraph.current
    LaunchedEffect(Unit) {
        TraktService.bind(g)
        if (TraktService.follows.value == null) TraktService.loadFollows()
    }
    val state by TraktService.state.collectAsState()
    val follows by TraktService.follows.collectAsState()
    val client = g.session.session.collectAsState().value?.client
    val ids by produceState<Map<String, String?>?>(null, seriesId, client, state is TraktState.Connected) {
        value = if (client == null || state !is TraktState.Connected) null else try {
            client.getItem(seriesId, ItemsRequestOptions(fields = listOf("ProviderIds"), enableImages = false, enableUserData = false)).providerIds
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pid = ids
    if (state !is TraktState.Connected || client == null || pid.isNullOrEmpty()) return null
    val following = TraktService.isFollowing(follows, pid)
    return TraktFollowUi(following, busy) {
        if (!busy) {
            busy = true
            scope.launch {
                try {
                    TraktService.setFollow(client, seriesId, !following)
                } finally {
                    busy = false
                }
            }
        }
    }
}

/** 追更日历的数据（连着 Trakt 才取）：null = 没连 / 还在取；空 = 两周里没有要播的。 */
@Composable
fun rememberTraktCalendar(): Pair<Boolean, List<CalendarDay>?> {
    val g = LocalGraph.current
    LaunchedEffect(Unit) { TraktService.bind(g) }
    val state by TraktService.state.collectAsState()
    val client = g.session.session.collectAsState().value?.client
    val connected = state is TraktState.Connected && client != null
    val days by produceState<List<CalendarDay>?>(null, connected, client) {
        if (connected && client != null) {
            val now = currentTimeMillis()
            value = TraktService.calendar(client, now, { localUtcOffsetMs(it) }) ?: emptyList()
        }
    }
    return connected to days
}

/**
 * 手机 / 电脑的追更日历一节：一天一组，每集一行（播出时间 · 剧名 · 第几季第几集 · 集名）；库里有这部剧 = 点了打开它。
 * 没连 Trakt = 什么都不画。
 */
@Composable
fun TraktCalendarSection(modifier: Modifier = Modifier, titleSize: TextUnit = Guga.type.row, timeWidth: Dp = 56.dp) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val (connected, days) = rememberTraktCalendar()
    if (!connected) return
    Column(modifier.fillMaxWidth().testTag("trakt-calendar")) {
        SectionHeader("追更日历", "TRAKT CALENDAR", trailing = "接下来两周")
        when {
            days == null -> BasicText("正在读 Trakt…", Modifier.padding(vertical = 18.dp), style = Guga.note(Guga.type.caption, colors.faint), maxLines = 1)
            days.isEmpty() -> BasicText("接下来两周没有你追的剧要播。在剧集详情页点「追更」，新的一集就会排在这里。", Modifier.padding(vertical = 18.dp), style = Guga.note(Guga.type.caption, colors.dim), maxLines = 3)
            else -> for (day in days) {
                BasicText(day.label, Modifier.padding(top = 18.dp, bottom = 6.dp), style = Guga.song(Guga.type.caption * 1.1f, FontWeight.Bold, if (day.label == "今天") colors.spot else colors.ink), maxLines = 1)
                for (e in day.entries) {
                    val press = rememberPressState()
                    val sid = e.seriesId
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (sid != null) Modifier.pressGraphics(press).pressable(press, label = e.show, onClick = { g.open(sid) }) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BasicText(e.time, Modifier.width(timeWidth), style = Guga.numeral(Guga.type.caption * 1.1f, colors.dim), maxLines = 1)
                        Column(Modifier.weight(1f)) {
                            BasicText(e.show, style = Guga.song(titleSize, FontWeight.Bold, if (sid != null) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val line = listOfNotNull(e.season?.let { "第 $it 季" }, e.number?.let { "第 $it 集" }, e.title).joinToString(" · ")
                            if (line.isNotEmpty()) BasicText(line, Modifier.padding(top = 2.dp), style = Guga.song(Guga.type.caption, color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        BasicText(if (sid != null) "打开" else "库里没有", style = Guga.note(Guga.type.caption, if (sid != null) colors.spot else colors.faint), maxLines = 1)
                    }
                    Rule()
                }
            }
        }
    }
}
