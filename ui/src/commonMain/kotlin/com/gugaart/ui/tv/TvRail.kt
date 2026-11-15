package com.gugaart.ui.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.screens.home.ExitHint
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideNav
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.nav.ExitConfirm
import com.gugugaga.app.nav.NavController
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.FocusZoneScope
import com.gugugaga.ds.focus.ZoneRestore
import com.gugugaga.ds.focus.focusZone
import com.gugugaga.ds.glass.MaterialName
import com.gugugaga.ds.glass.glass
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.theme.LocalOnGlass
import kotlinx.coroutines.delay

/** 侧栏的一行：去哪。 */
enum class TvRailItem(val key: String, val label: String, val icon: GuguIcon) {
    Search("search", "搜索", GuguIcon.Search),
    Home("home", "首页", GuguIcon.House),
    Discover("discover", "发现", GuguIcon.Compass),
    Libs("libs", "片库", GuguIcon.LayoutGrid),
    Colls("colls", "合集", GuguIcon.Layers),
    History("history", "记录", GuguIcon.History),
    Favs("favs", "收藏", GuguIcon.Heart),
    Settings("settings", "设置", GuguIcon.SlidersHorizontal),
}

/** 侧栏的版式数（设计稿 `tv/css/tv.css` 的 `.rail` `.navp` `.ni`）。 */
object TvRailSpec {
    /** 收起：一格 56 × 66，图标 30；竖条四周留 10。 */
    val CELL_W = 56.dp
    val CELL_H = 66.dp
    val ICON = 30.dp
    val PAD = 10.dp
    val RADIUS = 38.dp
    /** 展开：面板内边 26 / 22，页眉 84，行高 66（圆角 33），页脚 70。 */
    val OPEN_RADIUS = 42.dp
    val OPEN_PAD_V = 26.dp
    val OPEN_PAD_H = 22.dp
    val HEAD = 84.dp
    val FOOT = 70.dp
    /** 玻璃上的白字（玻璃里不用纸面的米白）：行 86 %、编号 50 %、页脚 66 %、分隔线 22 %、收起时不是当前页的图标 62 %。 */
    const val ROW_ALPHA = 0.86f
    const val NUMBER_ALPHA = 0.5f
    const val FOOT_ALPHA = 0.66f
    const val LINE_ALPHA = 0.22f
    const val IDLE_ALPHA = 0.62f
    /** 焦点纸条上的编号：深朱红。 */
    val NUMBER_ON_SLIP = com.gugaart.ui.theme.GugaColors.SEAL_ON_INK
}

/** 这个路由 / 导航页在侧栏上是哪一行（亮图标 + 朱红小圆点）。纯函数。 */
fun tvRailActive(route: Route, tab: WideTab): TvRailItem? = when (route) {
    Route.Home -> when (tab) {
        WideTab.Home -> TvRailItem.Home
        WideTab.Discover -> TvRailItem.Discover
        WideTab.Libs -> TvRailItem.Libs
        WideTab.Colls -> TvRailItem.Colls
        WideTab.Me -> TvRailItem.History
    }
    Route.Discover -> TvRailItem.Discover
    is Route.Search -> TvRailItem.Search
    is Route.History -> if (route.tab == "favorites") TvRailItem.Favs else TvRailItem.History
    is Route.Settings -> TvRailItem.Settings
    else -> null
}

/**
 * 电视的固定导航（玻璃，`docs/glass.md`）：左侧玻璃竖条（x 28–104，竖向居中）。收起时是一列图标（当前页 = 亮图标 + 朱红小圆点），
 * **不接焦点**；任何页面一路按左到头 → [WideNav.openRail] → 竖条长成一张目录页（编号 + 图标 + 字，`Panel` 材质），焦点落在当前页那一行
 * （玻璃里一张米白纸条、墨字）。上下换行，确定 = 去那一页，右 / 返回 = 收起并把焦点还给原来的那一项。
 *
 * 收起和展开是**同一组**焦点项（只是外形变了），所以展开 / 收起不会丢焦点。展开时是个关着门的焦点区（上游 `focusZone(trap)`）。
 *
 * 画在 `GlassScene` 的前景层。电视 API 28–32 的 ControlledGl 后端只折射 `backdrop` 那张图：根把当前整幅剧照的小图交给场景（decisions Q12）。
 */
@Composable
fun TvChrome(route: Route, modifier: Modifier = Modifier) {
    val g = LocalGraph.current
    val nav = LocalWideNav.current
    val tv = nav.tv
    val colors = Guga.colors
    val active = tvRailActive(route, nav.tab)
    val open = nav.railOpen
    val session by g.session.session.collectAsState()

    DisposableEffect(tv) {
        tv.railPresent = true
        onDispose {
            tv.railPresent = false
            nav.railOpen = false
        }
    }
    // 展开：焦点落在当前页那一行；收起：还给展开之前的那一项（没有就按兜底的规矩找）
    LaunchedEffect(open) {
        if (open) {
            val want = "$TV_RAIL_ZONE/${(active ?: TvRailItem.Home).key}"
            repeat(30) {
                if (g.focus.request(want)) return@LaunchedEffect
                delay(16)
            }
        } else if (g.focus.lastFocusedKey?.startsWith("$TV_RAIL_ZONE/") == true && !tv.pageHasFocus) {
            val back = tv.railReturn
            tv.railReturn = null
            if (back == null || !runCatching { back.focusRequester.requestFocus() }.getOrDefault(false)) {
                // 换了页面：由页面层的「进页面放焦点」接手（TvPages）；同一页就找回这一页的焦点
                delay(60)
                if (!tv.pageHasFocus) tvRescueFocus(g, tv)
            }
        }
    }
    TvBack(open) { nav.closeRail() }

    Box(modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.CenterStart).padding(start = TvSpec.RAIL_LEFT)) {
            FocusZoneScope(TV_RAIL_ZONE) {
                Column(
                    Modifier
                        .then(if (open) Modifier.size(TvSpec.RAIL_OPEN_WIDTH, TvSpec.RAIL_OPEN_HEIGHT) else Modifier.width(TvSpec.RAIL_WIDTH))
                        .glass(if (open) MaterialName.Panel else MaterialName.Bar, if (open) TvRailSpec.OPEN_RADIUS else TvRailSpec.RADIUS)
                        .focusZone(TV_RAIL_ZONE, ZoneRestore.Nearest, trap = true, trapActive = { nav.railOpen })
                        .padding(vertical = if (open) TvRailSpec.OPEN_PAD_V else TvRailSpec.PAD, horizontal = if (open) TvRailSpec.OPEN_PAD_H else TvRailSpec.PAD)
                        .testTag(if (open) "tv-rail-open" else "tv-rail"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CompositionLocalProvider(LocalOnGlass provides true) {
                        val line = Color.White.copy(alpha = TvRailSpec.LINE_ALPHA)
                        if (open) {
                            // 页眉：「咕咕嘎嘎」
                            Box(
                                Modifier.fillMaxWidth().height(TvRailSpec.HEAD).drawBehind { drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }.padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                BasicText("咕咕嘎嘎", style = Guga.song(34.sp, FontWeight.Black, Color.White, letterSpacing = 0.3.em), maxLines = 1)
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        for ((i, item) in TvRailItem.entries.withIndex()) {
                            val on = item == active
                            TvFocusable(
                                onClick = {
                                    val same = item == active
                                    if (!same) tvRailGo(item, g.nav, nav)
                                    nav.closeRail(restore = same)
                                },
                                modifier = Modifier
                                    .then(if (open) Modifier.fillMaxWidth().height(TvRailSpec.CELL_H).clip(RoundedCornerShape(TvRailSpec.CELL_H / 2)) else Modifier.size(TvRailSpec.CELL_W, TvRailSpec.CELL_H))
                                    // 收起时不接焦点：方向键的就近搜索不会从页面里拐进来，只有「一路按左到头」才展开
                                    .focusProperties { canFocus = nav.railOpen },
                                kind = TvFocusKind.OnGlass,
                                key = item.key,
                                label = item.label,
                                contentAlignment = Alignment.Center,
                            ) { focused ->
                                if (open) {
                                    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                        BasicText(
                                            if (i < 9) "0${i + 1}" else "${i + 1}",
                                            Modifier.width(34.dp),
                                            style = Guga.numeral(21.sp, if (focused) TvRailSpec.NUMBER_ON_SLIP else Color.White.copy(alpha = TvRailSpec.NUMBER_ALPHA)),
                                            maxLines = 1,
                                        )
                                        Icon(item.icon, if (focused) colors.onInk else Color.White.copy(alpha = TvRailSpec.ROW_ALPHA), TvRailSpec.ICON)
                                        BasicText(
                                            item.label,
                                            Modifier.weight(1f),
                                            style = Guga.song(29.sp, FontWeight.Bold, if (focused) colors.onInk else Color.White.copy(alpha = TvRailSpec.ROW_ALPHA), letterSpacing = 0.12.em),
                                            maxLines = 1,
                                        )
                                        if (on) Box(Modifier.size(8.dp).background(colors.seal, CircleShape))
                                    }
                                } else {
                                    Icon(item.icon, Color.White.copy(alpha = if (on) 1f else TvRailSpec.IDLE_ALPHA), TvRailSpec.ICON, strokeWidth = if (on) 2.2f else 1.9f)
                                    // 当前页：朱红小圆点（固定朱红，不跟专色——电视的焦点 / 记号都是固定色）
                                    if (on) Box(Modifier.align(Alignment.CenterEnd).offset(x = 4.dp).size(7.dp).background(colors.seal, CircleShape))
                                }
                            }
                        }
                        if (open) {
                            Spacer(Modifier.weight(1f))
                            // 页脚：谁在读、哪台服务器（真实的会话；没有就不写）
                            val s = session
                            Row(
                                Modifier.fillMaxWidth().height(TvRailSpec.FOOT).drawBehind { drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }.padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                val foot = Guga.song(22.sp, FontWeight.Normal, Color.White.copy(alpha = TvRailSpec.FOOT_ALPHA))
                                if (s != null) {
                                    BasicText("影迷 · ${s.userName}", Modifier.weight(1f, fill = false), style = foot, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.weight(0.01f))
                                    BasicText(s.serverName.ifBlank { "Emby 服务器" }, Modifier.weight(1f, fill = false), style = foot, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        TvExitToast(Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }
}

/**
 * 「再按一次返回键退出」（设计稿 `home-exit`）：米白纸条、墨字、左边一道朱红。什么时候算「再按一次」是上游的 `ExitConfirm`（壳里判断，
 * 和手机同一个 [ExitHint]）；这里只有电视的外观。
 */
@Composable
fun TvExitToast(modifier: Modifier = Modifier) {
    val colors = Guga.colors
    LaunchedEffect(ExitHint.shownCount) {
        if (ExitHint.visible) {
            delay(ExitConfirm.EXIT_CONFIRM_WINDOW_MS)
            ExitHint.hide()
        }
    }
    AnimatedVisibility(ExitHint.visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(Modifier.background(colors.ink).drawBehind { drawRect(colors.seal, size = androidx.compose.ui.geometry.Size(8.dp.toPx(), size.height)) }.padding(start = 48.dp, end = 40.dp, top = 18.dp, bottom = 18.dp).testTag("tv-exit-toast")) {
            BasicText("再按一次返回键退出", style = Guga.song(28.sp, FontWeight.Bold, colors.onInk, letterSpacing = 0.08.em), maxLines = 1)
        }
    }
}

/** 侧栏的一行按确定：导航页在 Home 路由上换页；搜索 / 收藏 / 设置走上游的路由。 */
fun tvRailGo(item: TvRailItem, nav: NavController, wide: WideNav) {
    fun tab(t: WideTab) {
        wide.tab = t
        if (nav.route != Route.Home) nav.reset(Route.Home)
    }
    when (item) {
        TvRailItem.Search -> if (nav.route !is Route.Search) nav.navigate(Route.Search())
        TvRailItem.Home -> tab(WideTab.Home)
        TvRailItem.Discover -> tab(WideTab.Discover)
        TvRailItem.Libs -> tab(WideTab.Libs)
        TvRailItem.Colls -> tab(WideTab.Colls)
        TvRailItem.History -> tab(WideTab.Me)
        TvRailItem.Favs -> if ((nav.route as? Route.History)?.tab != "favorites") nav.navigate(Route.History("favorites"))
        TvRailItem.Settings -> if (nav.route !is Route.Settings) nav.navigate(Route.Settings())
    }
}
