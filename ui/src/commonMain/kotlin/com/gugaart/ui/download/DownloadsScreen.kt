package com.gugaart.ui.download

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.StateView
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.screens.TitlePage
import com.gugaart.ui.screens.play
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/*
 * 「我的 › 下载」（1.6 批次 E，路由 `Route.History(tab = "downloads")`，只在手机上有入口）：片名、大小、状态，顶上是占用空间合计；
 * 点下好了的 = 播放（本地文件，断网也行）；失败的点 = 重试；长按 = 删除（确认框）。
 * 首页断网时的「下载」一栏（[DownloadsShelf]）也在这里。
 */

/** 「我的 › 下载」的路由：`Route.History(DOWNLOADS_TAB)`（和收藏一样借上游的记录路由，不新加路由）。 */
const val DOWNLOADS_TAB = "downloads"

/** 列表上一行的副行：单集写「第 1 季 第 3 集 · 集名」，电影写「电影」；再接大小（下着的写「已下 / 总共」）和失败原因。纯函数。 */
fun downloadRowLine(r: DownloadRow): String {
    val e = r.entry
    val kind = e.episodeLine ?: "电影"
    val size = when {
        e.status == DlStatus.Done -> sizeLabel(maxOf(e.total, e.done))
        e.total > 0 && e.done > 0 -> "${sizeLabel(e.done)} / ${sizeLabel(e.total)}"
        else -> sizeLabel(e.total)
    }
    val why = if (e.status == DlStatus.Failed) e.error?.let { "下载失败：$it" } ?: "下载失败" else null
    return listOfNotNull(kind, size, why).joinToString(" · ")
}

/** 一行的缩略图地址（用当前账号的客户端拼；断网时靠图片的磁盘缓存）。 */
@Composable
private fun thumbOf(ui: DownloadUi, r: DownloadRow): String? = remember(r.entry.key, r.entry.itemJson.length) {
    Downloads.storedItem(r.entry)?.let { item ->
        val t = ui.session.client.titleOf(item)
        t.art.still ?: t.art.thumb ?: t.art.backdrop ?: t.art.poster
    }
}

private fun open(g: com.gugugaga.app.AppGraph, r: DownloadRow) {
    when (r.entry.status) {
        DlStatus.Done -> g.play(r.entry.itemId, r.entry.positionTicks?.let { it / 10_000 })
        DlStatus.Failed -> Downloads.retry(r.key)
        DlStatus.Queued -> Unit
    }
}

@Composable
fun DownloadsScreen() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val type = Guga.type
    val ui = rememberDownloads()
    val rows = ui?.mine.orEmpty()
    TitlePage("我的 · DOWNLOADS", "下载", onBack = { g.nav.backOrHome() }, barTitle = "我的", tag = "downloads-page") {
        Column(Modifier.padding(horizontal = dimens.margin)) {
            if (ui == null || rows.isEmpty()) {
                StateView("state-downloads-empty", "捧白纸", "还没有下载", null)
            } else {
                SectionHeader("占用", "STORAGE", Modifier.padding(top = 18.dp).testTag("downloads-total"), trailing = downloadsHint(rows.map { it.entry }))
                Rule(Modifier.padding(top = 10.dp), strong = true)
                for (r in rows) {
                    val press = rememberPressState()
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 86.dp).pressGraphics(press)
                            .pressable(press, label = r.entry.displayName, onLongClick = { DownloadSheet.remove(r) }) { open(g, r) }
                            .padding(vertical = 12.dp).testTag("download-row"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Thumb(thumbOf(ui, r), Rules.monogram(r.entry.displayName, person = false), 104.dp, progress = if (r.entry.status == DlStatus.Done) 0f else r.entry.fraction)
                        Column(Modifier.weight(1f).padding(start = 14.dp, end = 10.dp)) {
                            BasicText(r.entry.displayName, style = Guga.song(type.row, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            BasicText(downloadRowLine(r), Modifier.padding(top = 5.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        val done = r.entry.status == DlStatus.Done
                        BasicText(downloadLabel(r), style = Guga.song(type.caption, if (done) FontWeight.Bold else FontWeight.Normal, if (done) colors.ink else colors.dim), maxLines = 1)
                    }
                    Rule()
                }
            }
        }
        Spacer(Modifier.height(150.dp + dimens.tabLift))
    }
}

/** 首页断网时的「下载」一栏：下好了的横排，点了直接放本地文件。没有下好的 = 不出现。 */
@Composable
fun DownloadsShelf(modifier: Modifier = Modifier) {
    val ui = rememberDownloads() ?: return
    val done = ui.mine.filter { it.entry.status == DlStatus.Done }
    if (done.isEmpty()) return
    val g = LocalGraph.current
    val dimens = Guga.dimens
    val type = Guga.type
    val colors = Guga.colors
    Column(modifier.testTag("home-downloads")) {
        SectionHeader("下载", "DOWNLOADS", Modifier.padding(horizontal = dimens.margin), trailing = "${done.size} 项")
        Row(Modifier.padding(top = 16.dp).horizontalScroll(rememberScrollState()).padding(horizontal = dimens.margin), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (r in done) {
                val press = rememberPressState()
                Column(
                    Modifier.width(SHELF_THUMB).pressGraphics(press)
                        .pressable(press, label = r.entry.displayName, onLongClick = { DownloadSheet.remove(r) }) { open(g, r) }
                        .testTag("home-download-card"),
                ) {
                    Thumb(thumbOf(ui, r), Rules.monogram(r.entry.displayName, person = false), SHELF_THUMB)
                    BasicText(r.entry.displayName, Modifier.padding(top = 8.dp), style = Guga.song(type.caption * 1.08f, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BasicText(r.entry.episodeLine ?: sizeLabel(maxOf(r.entry.total, r.entry.done)).orEmpty(), Modifier.padding(top = 2.dp), style = Guga.song(type.fine, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 首页「下载」一栏的缩略图宽（16:9）。 */
private val SHELF_THUMB = 168.dp
