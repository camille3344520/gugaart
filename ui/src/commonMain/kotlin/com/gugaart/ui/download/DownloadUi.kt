package com.gugaart.ui.download

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.ConfirmSheet
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Season
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.session.AppSession
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * 离线下载的界面零件（1.6 批次 E，只在手机版式上出现）：电影详情的「下载」、分集行尾的那一格、整季的「下载这一季」、
 * 删除 / 整季的确认框（画在根上：[DownloadSheet.Host]）。
 * 三态：下载 → 进度（37% / 排队 / 等 Wi-Fi）→ 已下载；失败 = 重试。已下载 / 正在下的长按 = 删除（确认框）。
 * 账号没有「允许下载」权限（Emby `Policy.EnableContentDownloading`）就不出入口；已经下好的照样显示「已下载」。
 */

/** 这一屏的下载状态：会话 + 能不能下 + 所有行。 */
class DownloadUi(val session: AppSession, val allowed: Boolean, val rows: List<DownloadRow>) {
    /** 这个账号的那一条。 */
    fun rowOf(itemId: String): DownloadRow? =
        rows.firstOrNull { it.entry.itemId == itemId && it.entry.serverId == session.serverId && it.entry.userId == session.userId }

    /** 这个账号的全部（按加进来的先后）。 */
    val mine: List<DownloadRow> get() = rows.filter { it.entry.serverId == session.serverId && it.entry.userId == session.userId }
}

/** 账号的「允许下载」权限：按「服务器 / 账号」问一次服务器；问不到（断网）下次再问，先当没有。 */
object DownloadPermission {
    private val byKey = mutableStateMapOf<String, Boolean>()

    /** 测试缝：直接定（离屏截图不连服务器）。 */
    fun set(serverId: String, userId: String, allowed: Boolean) {
        byKey["$serverId/$userId"] = allowed
    }

    @Composable
    fun allowed(s: AppSession): Boolean {
        val key = "${s.serverId}/${s.userId}"
        LaunchedEffect(key) {
            if (byKey.containsKey(key)) return@LaunchedEffect
            try {
                byKey[key] = s.client.getCurrentUser().policy?.enableContentDownloading == true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 断网 / 服务器不答：这次不出「下载」，下次进来再问
            }
        }
        return byKey[key] == true
    }
}

/** 手机版式 + 壳有下载能力 + 登录着 → 有；否则 null（不出任何下载入口）。 */
@Composable
fun rememberDownloads(): DownloadUi? {
    if (!Downloads.available || Guga.uiMode != UiMode.Phone) return null
    val g = LocalGraph.current
    val s by g.session.session.collectAsState()
    val session = s ?: return null
    val rows by Downloads.rows.collectAsState()
    return DownloadUi(session, DownloadPermission.allowed(session), rows)
}

/** 根上的确认框（详情页 / 分集行 / 「我的 › 下载」/ 长按菜单都在这里问）。 */
object DownloadSheet {
    sealed interface Ask {
        /** 删除下载（下好了的）/ 取消下载（还在下的）。 */
        data class Remove(val row: DownloadRow) : Ask

        /** 整季下载：「下载第 1 季，共 12 集？」。 */
        data class Season(val session: AppSession, val seasonNo: Int?, val titles: List<Title>) : Ask
    }

    var ask: Ask? by mutableStateOf(null)

    fun remove(row: DownloadRow) {
        ask = Ask.Remove(row)
    }

    @Composable
    fun Host() {
        when (val a = ask) {
            null -> Unit
            is Ask.Remove -> {
                val done = a.row.entry.status == DlStatus.Done
                ConfirmSheet(
                    title = (if (done) "删除「" else "取消「") + a.row.entry.fullName + "」的下载？",
                    confirm = if (done) "删除下载" else "取消下载",
                    onConfirm = {
                        Downloads.delete(a.row.key)
                        ask = null
                    },
                    onDismiss = { ask = null },
                    danger = true,
                    cancel = if (done) "取消" else "不取消",
                )
            }
            is Ask.Season -> ConfirmSheet(
                title = seasonDownloadTitle(a.seasonNo, a.titles.size),
                confirm = "下载",
                onConfirm = {
                    Downloads.enqueue(a.session, a.titles)
                    ask = null
                },
                onDismiss = { ask = null },
            )
        }
    }
}

/** 点下载按钮：没下 = 加进队列；失败 = 重新排队；其它（在下、已下载）= 不动（删除是长按）。 */
private fun onTap(ui: DownloadUi, row: DownloadRow?, title: Title, mediaSourceId: String?) {
    when {
        row == null -> Downloads.enqueue(ui.session, listOf(title), mediaSourceId)
        row.entry.status == DlStatus.Failed -> Downloads.retry(row.key)
    }
}

/**
 * 电影详情（单集详情同）的「下载」文字链接：下载 / 37% / 已下载（失败 = 重试）。长按（有这一条时）= 删除 / 取消下载。
 * [mediaSourceId]：详情页选中的版本。
 */
@Composable
fun DownloadLink(title: Title, mediaSourceId: String?, modifier: Modifier = Modifier) {
    val ui = rememberDownloads() ?: return
    val row = ui.rowOf(title.id)
    if (row == null && !ui.allowed) return
    if (title.kind != Kind.Movie && title.kind != Kind.Episode) return
    TextLink(
        downloadLabel(row),
        { onTap(ui, row, title, mediaSourceId) },
        modifier.testTag("download-link"),
        icon = GuguIcon.Download,
        onLongClick = row?.let { r -> { DownloadSheet.remove(r) } },
    )
}

/** 分集行尾的那一格（校对格左边）：下载图标 / 37% / 排队 / 已下载。没有权限也没下过 = 不占位。 */
@Composable
fun EpisodeDownloadCell(episode: Title) {
    val ui = rememberDownloads() ?: return
    val row = ui.rowOf(episode.id)
    if (row == null && !ui.allowed) return
    if (episode.unaired) return
    val colors = Guga.colors
    val press = rememberPressState()
    val label = downloadLabel(row)
    Box(
        Modifier.widthIn(min = 40.dp).height(48.dp)
            .pressable(press, label = "第 ${episode.episodeNo ?: ""} 集 $label", onLongClick = row?.let { r -> { DownloadSheet.remove(r) } }) { onTap(ui, row, episode, null) }
            .testTag(if (row == null) "episode-download" else "episode-download-${row.entry.status.id}"),
        contentAlignment = Alignment.Center,
    ) {
        if (row == null) {
            Icon(GuguIcon.Download, colors.dim, 15.dp)
        } else {
            val done = row.entry.status == DlStatus.Done
            BasicText(label, style = Guga.song(Guga.type.fine, if (done) FontWeight.Bold else FontWeight.Normal, if (done) colors.ink else colors.dim), maxLines = 1)
        }
    }
}

/**
 * 整季下载的链接（剧集详情「分集」下）：点了先取这一季的全部分集（不只当前这一页），再问「下载第 1 季，共 12 集？」。
 * 这一季都下了 / 都在队列里 = 「这一季已下载」，点了不动。
 */
@Composable
fun SeasonDownloadLink(seriesId: String, season: Season?, modifier: Modifier = Modifier) {
    val ui = rememberDownloads() ?: return
    if (!ui.allowed) return
    val scope = rememberCoroutineScope()
    var busy by androidx.compose.runtime.remember(seriesId, season?.id) { mutableStateOf(false) }
    val have = ui.mine.count { it.entry.seriesId == seriesId && it.entry.seasonNo == season?.number }
    val total = season?.episodes ?: 0
    val missing = (total - have).coerceAtLeast(0)
    TextLink(
        if (busy) "正在取分集…" else seasonDownloadLink(missing, total),
        {
            if (busy || (total > 0 && missing == 0)) return@TextLink
            busy = true
            scope.launch {
                try {
                    val items = ui.session.client.getEpisodes(seriesId, seasonId = season?.id, isMissing = false, limit = 2000).items
                    val titles = items.map { ui.session.client.titleOf(it) }.filter { !it.unaired && ui.rowOf(it.id)?.entry?.status != DlStatus.Done }
                    if (titles.isNotEmpty()) DownloadSheet.ask = DownloadSheet.Ask.Season(ui.session, season?.number, titles)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 取不到分集（断网）：什么都不弹
                } finally {
                    busy = false
                }
            }
        },
        modifier.testTag("season-download"),
        icon = GuguIcon.Download,
    )
}
