package com.gugaart.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.MenuRow
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SheetHost
import com.gugaart.ui.components.SlipHeader
import com.gugaart.ui.data.ItemManage
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Title
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.theme.feel
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon
import kotlinx.coroutines.launch

/**
 * 手机的长按菜单（用户 2026-11-17：「管理…安卓手机端也要做一下呢」）：长按海报卡 → 正中一张纸片（和号外、二次确认同一种纸）。
 * 日常：播放 / 从头播放 / 查看详情 / 收藏 / 标记已看；管理员多「刷新元数据 / 扫描文件 / 删除…」（[ItemManage]，和电视长按、电脑右键同一套）。
 * 刷新 / 扫描做完结果写在纸片底下、不收；「删除…」把纸片换成二次确认（先列「取消」，「删除文件」朱红），返回键回到菜单；删掉以后片库里马上消失。
 * 任何界面在卡片的长按里调 [open]；[Host] 由手机的根画一次。
 */
object PhoneItemMenu {
    var target: Title? by mutableStateOf(null)
        private set

    fun open(title: Title) {
        if (title.kind == Kind.Person) return
        target = title
    }

    fun close() {
        target = null
    }

    @Composable
    fun Host() {
        val t = target ?: return
        // 触感（M4-3）：长按出纸片 = 中
        val host = com.gugaart.ui.theme.LocalPhoneHost.current
        androidx.compose.runtime.LaunchedEffect(t) { host.feel(com.gugaart.ui.theme.HapticStyle.Medium) }
        val g = LocalGraph.current
        val chrome = LocalChrome.current
        val colors = Guga.colors
        var stage by remember(t) { mutableStateOf("menu") }
        var status by remember(t) { mutableStateOf<String?>(null) }
        var busy by remember(t) { mutableStateOf(false) }
        val manage = ItemManage.canManage()
        val favId = if (t.kind == Kind.Episode) t.seriesId ?: t.id else t.id
        val fav = chrome.isFavorite(favId, t.favorite && t.kind != Kind.Episode)
        val seen = chrome.isPlayed(t.id, t.played)
        val playable = t.kind == Kind.Movie || t.kind == Kind.Episode

        SheetHost({ if (busy) Unit else if (stage == "del") stage = "menu" else close() }, tag = "phone-item-menu") {
            SlipHeader("选项", "OPTIONS")
            BasicText(
                if (t.kind == Kind.Episode && t.seriesName != null) "${t.seriesName} · ${t.episodeNo?.let { "第 $it 集 " }.orEmpty()}${t.name}" else t.name,
                Modifier.padding(top = 14.dp).testTag("phone-item-menu-title"),
                style = Guga.song(Guga.type.section, FontWeight.Black),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Rule(Modifier.padding(top = 12.dp, bottom = 4.dp))
            if (stage == "menu") {
                if (playable) {
                    MenuRow(if (t.progress > 0f) "续看" else "播放", { close(); g.play(t.id, t.positionSec?.times(1000)) }, Modifier.testTag("phone-menu-play"), icon = GuguIcon.Play)
                    if (t.progress > 0f) MenuRow("从头播放", { close(); g.play(t.id, 0L) }, icon = GuguIcon.RotateCcw)
                }
                MenuRow(if (t.kind == Kind.Episode) "打开这部剧" else "查看详情", { close(); g.open(if (t.kind == Kind.Episode) t.seriesId ?: t.id else t.id) }, icon = GuguIcon.Info)
                MenuRow(if (fav) "取消收藏" else "收藏", { g.toggleFavorite(chrome, favId, fav); close() }, icon = GuguIcon.Heart)
                if (t.kind != Kind.BoxSet) MenuRow(if (seen) "标成没看" else "标记已看", { g.togglePlayed(chrome, t.id, seen); close() }, icon = GuguIcon.Check)
                if (manage) {
                    Rule(Modifier.padding(vertical = 4.dp))
                    BasicText("管理 · 会改动服务器上的条目", Modifier.padding(vertical = 4.dp), style = Guga.note(Guga.type.caption, colors.faint), maxLines = 1)
                    MenuRow("刷新元数据", {
                        status = "正在请求…"
                        g.scope.launch { status = ItemManage.refresh(g, t.id) }
                    }, Modifier.testTag("phone-menu-refresh"), icon = GuguIcon.RefreshCw)
                    MenuRow("扫描文件", {
                        status = "正在请求…"
                        g.scope.launch { status = ItemManage.scan(g, t.id) }
                    }, Modifier.testTag("phone-menu-scan"), icon = GuguIcon.Radio)
                    MenuRow("删除…", { status = null; stage = "del" }, Modifier.testTag("phone-menu-delete"), icon = GuguIcon.Trash, danger = true)
                }
            } else {
                Column(Modifier.testTag("phone-menu-del-confirm")) {
                    BasicText("确定删除？", Modifier.padding(top = 8.dp), style = Guga.song(Guga.type.row, FontWeight.Black, colors.seal), maxLines = 1)
                    BasicText("会从服务器删除媒体文件，不能撤销。", Modifier.padding(top = 6.dp, bottom = 8.dp), style = Guga.song(Guga.type.lead * 0.94f, color = colors.dim), maxLines = 2)
                    MenuRow("取消", { if (!busy) stage = "menu" }, Modifier.testTag("phone-menu-del-cancel"), icon = GuguIcon.X)
                    MenuRow(if (busy) "正在删除…" else "删除文件", {
                        if (!busy) {
                            busy = true
                            status = null
                            g.scope.launch {
                                val err = ItemManage.delete(g, t)
                                busy = false
                                if (err == null) close() else status = err
                            }
                        }
                    }, Modifier.testTag("phone-menu-del-confirm-row"), icon = GuguIcon.Trash, danger = true)
                }
            }
            status?.let { BasicText(it, Modifier.padding(top = 10.dp).testTag("phone-menu-status"), style = Guga.note(Guga.type.caption, colors.ink), maxLines = 3) }
        }
    }
}
