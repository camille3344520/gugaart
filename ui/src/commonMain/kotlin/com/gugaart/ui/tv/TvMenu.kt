package com.gugaart.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.CoverImage
import com.gugaart.ui.data.ItemManage
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Title
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.FocusZoneScope
import com.gugugaga.ds.focus.ZoneRestore
import com.gugugaga.ds.focus.focusZone
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.PressState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 长按菜单的版式数（设计稿 `tv/css/pages.css` 的 `.cmenu`）。 */
object TvMenuSpec {
    /** 纸条宽 540，左边一道 10 px 朱红；内边 24 / 26 / 14；离那张卡 54。 */
    val WIDTH = 540.dp
    val EDGE = 10.dp
    val GAP = 54.dp
    val ROW = 72.dp
    /** 纸条上的小标（深朱红）、说明（墨 55 %）、分隔线（墨 18 %）。 */
    val LABEL = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK
    const val HINT_ALPHA = 0.55f
    const val LINE_ALPHA = 0.18f
}

/**
 * 菜单里的一行。[stay] = 做完留在这一页（收藏、已看）：收起后焦点还给那张卡；否则是去别的页面（播放、详情）。
 * [closes] = false：做完菜单不收（管理那几行：结果写在纸条底下；「删除…」换成二次确认）。[danger]：朱红字。
 */
class TvMenuAction(val key: String, val label: String, val icon: GuguIcon, val stay: Boolean = false, val closes: Boolean = true, val danger: Boolean = false, val run: () -> Unit)

/**
 * 长按确定菜单（设计稿 `p=menu`：默认 / `ep`）：米白纸条，焦点项墨底米白字；标题显示片名全名。
 * 焦点落在第一行；上下换行，确定执行，返回收起并把焦点**还给那张卡**；左右不动（防误触）。
 * 任何界面在卡片的 `onLongClick` 里调 [open]；[Host] 由电视的根（`GugaartFrame`）画一次。
 *
 * 条目：播放 / 打开详情 / 收藏（盖章）/ 标记已看（画校对勾）。收藏和已看会写服务器，只走 `AppGraph.toggleFavorite / togglePlayed`。
 * 设计稿里的「所属合集」「下载」没有数据来源 / 功能，不排。[open] 的 `more`：调用的界面自己加的几行（记录里的「从记录里移除」）。
 *
 * 管理员多三行「刷新元数据 / 扫描文件 / 删除…」（[ItemManage]；用户 2026-11-16：「之前可以管理emby的」）：前两行做完结果写在纸条底下、菜单不收；
 * 「删除…」把纸条换成二次确认（「取消」/「删除文件」，焦点默认在「取消」，返回键回到菜单），删掉以后这张卡从墙上消失。
 */
object TvLongPressMenu {
    var target: Title? by mutableStateOf(null)
        private set
    internal var more: List<TvMenuAction> = emptyList()
        private set

    fun open(title: Title) = open(title, emptyList())

    fun open(title: Title, more: List<TvMenuAction>) {
        this.more = more
        target = title
    }

    fun close() {
        target = null
    }

    @Composable
    fun Host() {
        val t = target
        val g = LocalGraph.current
        val nav = LocalWideNav.current
        val tv = nav.tv
        val chrome = LocalChrome.current
        val colors = Guga.colors
        // 打开的那一刻焦点在哪张卡上：收起时还给它；那张卡的图在屏幕上的位置：纸条贴在它旁边，卡片留一个亮着的框
        var from by remember { mutableStateOf<PressState?>(null) }
        var held by remember { mutableStateOf<Rect?>(null) }
        var restore by remember { mutableStateOf(true) }
        LaunchedEffect(t) {
            if (t != null) {
                from = g.focus.lastFocusedPress
                held = tv.focusedPicture
                restore = true
                repeat(30) {
                    val first = g.focus.attachedKeys().firstOrNull { it == "$TV_MENU_ZONE/play" } ?: "$TV_MENU_ZONE/detail"
                    if (g.focus.request(first)) return@LaunchedEffect
                    delay(16)
                }
            } else {
                val back = from
                from = null
                if (back != null && restore && !runCatching { back.focusRequester.requestFocus() }.getOrDefault(false)) tvRescueFocus(g, tv)
            }
        }
        // 纸条的两种样子：menu = 菜单；del = 删除的二次确认。status = 纸条底下的一句（管理操作的结果）
        var stage by remember(t) { mutableStateOf("menu") }
        var status by remember(t) { mutableStateOf<String?>(null) }
        var busy by remember(t) { mutableStateOf(false) }
        var wasDel by remember(t) { mutableStateOf(false) }
        // 换成二次确认：焦点落在「取消」；从确认回到菜单：落回「删除…」（刚打开时的焦点由上面那一段给）
        LaunchedEffect(t, stage) {
            if (t == null) return@LaunchedEffect
            if (stage == "del") wasDel = true else if (!wasDel) return@LaunchedEffect
            val key = if (stage == "del") "$TV_MENU_ZONE/del-cancel" else "$TV_MENU_ZONE/delete"
            repeat(30) {
                if (g.focus.request(key)) return@LaunchedEffect
                delay(16)
            }
        }
        if (t == null) return
        val manage = ItemManage.canManage() && t.kind != Kind.Person
        TvModal()
        TvBack { if (stage == "del" && !busy) stage = "menu" else if (!busy) close() }

        val favId = if (t.kind == Kind.Episode) t.seriesId ?: t.id else t.id
        val fav = chrome.isFavorite(favId, t.favorite && t.kind != Kind.Episode)
        val played = chrome.isPlayed(t.id, t.played)
        val playable = t.kind == Kind.Movie || t.kind == Kind.Episode
        val actions = buildList {
            if (playable) add(TvMenuAction("play", if (t.kind == Kind.Episode) "从这一集播放" else "播放", GuguIcon.Play) { g.play(t.id, t.positionSec?.times(1000)) })
            add(TvMenuAction("detail", if (t.kind == Kind.Episode) "打开这部剧" else "查看详情", GuguIcon.Info) { g.open(if (t.kind == Kind.Episode) t.seriesId ?: t.id else t.id) })
            if (t.kind != Kind.Person) {
                add(TvMenuAction("fav", if (fav) "取消收藏" else "收藏", GuguIcon.Heart, stay = true) { g.toggleFavorite(chrome, favId, fav) })
                if (t.kind != Kind.BoxSet) add(TvMenuAction("played", if (played) "标记未看" else "标记已看", GuguIcon.Check, stay = true) { g.togglePlayed(chrome, t.id, played) })
            }
            addAll(more)
            if (manage) {
                add(TvMenuAction("refresh", "刷新元数据", GuguIcon.RefreshCw, closes = false) {
                    status = "正在请求…"
                    g.scope.launch { status = ItemManage.refresh(g, t.id) }
                })
                add(TvMenuAction("scan", "扫描文件", GuguIcon.Radio, closes = false) {
                    status = "正在请求…"
                    g.scope.launch { status = ItemManage.scan(g, t.id) }
                })
                add(TvMenuAction("delete", "删除…", GuguIcon.Trash, closes = false, danger = true) { status = null; stage = "del" })
            }
        }.let { menu ->
            if (stage != "del") menu else listOf(
                TvMenuAction("del-cancel", "取消", GuguIcon.X, closes = false) { if (!busy) stage = "menu" },
                TvMenuAction("del-confirm", if (busy) "正在删除…" else "删除文件", GuguIcon.Trash, closes = false, danger = true) {
                    if (!busy) {
                        busy = true
                        status = null
                        g.scope.launch {
                            val err = ItemManage.delete(g, t)
                            busy = false
                            if (err == null) {
                                // 那张卡已经从墙上拿掉了：焦点不还给它，交给页面上最近的一张
                                restore = false
                                close()
                                delay(120)
                                tvRescueFocus(g, tv)
                            } else {
                                status = err
                            }
                        }
                    }
                },
            )
        }

        val density = LocalDensity.current
        var menuH by remember { mutableStateOf(0) }
        Box(Modifier.fillMaxSize().testTag("tv-menu-layer")) {
            // 被按住的那张卡：亮着的图 + 米白框（页面的其余部分被压暗了）
            val h = held
            if (h != null) {
                Box(Modifier.offset { IntOffset(h.left.roundToInt(), h.top.roundToInt()) }.size(with(density) { h.width.toDp() }, with(density) { h.height.toDp() }).focusFrame(true, colors.ink)) {
                    CoverImage(if (h.width > h.height) t.art.thumb ?: t.art.backdrop ?: t.art.poster else t.art.poster, Modifier.fillMaxSize())
                }
            }
            // 纸条贴在卡片右边；放不下就放左边；竖向不出安全边
            val place: (IntOffset) = with(density) {
                val w = TvMenuSpec.WIDTH.toPx()
                val gap = TvMenuSpec.GAP.toPx()
                val screenW = 1920.dp.toPx()
                val screenH = 1080.dp.toPx()
                val x = when {
                    h == null -> (screenW - w) / 2
                    h.right + gap + w <= screenW - TvSpec.RIGHT.toPx() -> h.right + gap
                    else -> (h.left - gap - w).coerceAtLeast(TvSpec.LEFT.toPx())
                }
                val y = ((h?.top ?: (screenH / 2 - menuH / 2)) + 35.dp.toPx()).coerceIn(TvSpec.TOP.toPx(), (screenH - TvSpec.BOTTOM.toPx() - menuH).coerceAtLeast(TvSpec.TOP.toPx()))
                IntOffset(x.roundToInt(), y.roundToInt())
            }
            FocusZoneScope(TV_MENU_ZONE) {
                Column(
                    Modifier
                        .offset { place }
                        .width(TvMenuSpec.WIDTH)
                        .onSizeChanged { menuH = it.height }
                        .drawBehind {
                            drawRect(colors.ink)
                            drawRect(colors.seal, size = Size(TvMenuSpec.EDGE.toPx(), size.height))
                            // 朝那张卡的小三角
                            val a = 12.dp.toPx()
                            drawPath(Path().apply { moveTo(0f, 60.dp.toPx()); lineTo(-a, 60.dp.toPx() + a); lineTo(0f, 60.dp.toPx() + 2 * a); close() }, colors.seal)
                        }
                        .focusZone(TV_MENU_ZONE, ZoneRestore.Nearest, trap = true, trapActive = { target != null })
                        .padding(start = 26.dp + TvMenuSpec.EDGE, end = 26.dp, top = 24.dp, bottom = 14.dp)
                        .testTag("tv-menu"),
                ) {
                    BasicText("选项 · OPTIONS", style = Guga.numeral(18.sp, TvMenuSpec.LABEL, letterSpacing = 0.22.em), maxLines = 1)
                    // 标题：片名全名（最长的片名在这里才看得全），两行
                    BasicText(
                        if (t.kind == Kind.Episode && t.seriesName != null) "${t.seriesName} · ${t.episodeNo?.let { "第 $it 集 " }.orEmpty()}${t.name}" else t.name,
                        Modifier.padding(top = 6.dp).testTag("tv-menu-title"),
                        style = Guga.song(30.sp, FontWeight.Black, colors.onInk, lineHeight = 39.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(Modifier.padding(top = 14.dp, bottom = 4.dp).fillMaxWidth().height(2.dp).background(colors.onInk))
                    if (stage == "del") {
                        BasicText("确定删除？", Modifier.padding(top = 12.dp), style = Guga.song(27.sp, FontWeight.Black, TvMenuSpec.LABEL), maxLines = 1)
                        BasicText("会从服务器删除媒体文件，不能撤销。", Modifier.padding(top = 6.dp, bottom = 10.dp).testTag("tv-menu-del-note"), style = Guga.song(22.sp, color = colors.onInk.copy(alpha = 0.8f)), maxLines = 2)
                    }
                    for (a in actions) {
                        TvFocusable(
                            onClick = {
                                restore = a.stay
                                a.run()
                                if (a.closes) close()
                            },
                            modifier = Modifier.fillMaxWidth().height(TvMenuSpec.ROW),
                            kind = TvFocusKind.OnPaper,
                            key = a.key,
                            label = a.label,
                        ) { focused ->
                            // 删除那几行用朱红：纸上用深一档的（和小标同色），焦点的墨底上用亮的
                            val ink = if (a.danger) (if (focused) colors.seal else TvMenuSpec.LABEL) else if (focused) colors.ink else colors.onInk
                            Row(
                                Modifier.fillMaxSize().drawBehind {
                                    if (!focused) drawLine(colors.onInk.copy(alpha = TvMenuSpec.LINE_ALPHA), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                                }.padding(horizontal = 18.dp).testTag("tv-menu-${a.key}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                Icon(a.icon, ink, 28.dp, filled = a.icon == GuguIcon.Play)
                                BasicText(a.label, style = Guga.song(27.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    val note = status
                    if (note != null) BasicText(note, Modifier.padding(top = 12.dp).testTag("tv-menu-status"), style = Guga.song(21.sp, color = colors.onInk), maxLines = 3)
                    BasicText(if (stage == "del") "返回键回到菜单" else "返回键收起", Modifier.align(Alignment.End).padding(top = 12.dp), style = Guga.song(20.sp, color = colors.onInk.copy(alpha = TvMenuSpec.HINT_ALPHA)), maxLines = 1)
                }
            }
        }
    }
}

/**
 * 图片卡把「图那一格在屏幕上的位置」交给外壳（长按菜单贴在它旁边、给它留一个亮着的框）。只有焦点在这张卡上时才交；
 * 位置变了（横排滑动、抬起）跟着更新。
 */
@Composable
fun Modifier.tvHeldPicture(focused: Boolean): Modifier {
    val tv = LocalWideNav.current.tv
    val last = remember { arrayOfNulls<Rect>(1) }
    val now by rememberUpdatedState(focused)
    DisposableEffect(focused) {
        if (focused) tv.focusedPicture = last[0]
        onDispose { }
    }
    return onGloballyPositioned { c ->
        val r = c.boundsInRoot()
        last[0] = r
        if (now) tv.focusedPicture = r
    }
}
