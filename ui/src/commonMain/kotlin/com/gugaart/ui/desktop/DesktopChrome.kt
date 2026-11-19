package com.gugaart.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.Today
import com.gugaart.ui.data.Rules
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.wide.LocalWideNav
import com.gugaart.ui.wide.WideTab
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.glass.GlassModel
import com.gugugaga.ds.glass.MaterialName
import com.gugugaga.ds.glass.glass
import com.gugugaga.ds.glass.glassThemeHere
import com.gugugaga.ds.glass.sceneAnchor
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.IndicatorMath
import com.gugugaga.ds.physics.PressConfig
import com.gugugaga.ds.physics.PressKind
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import com.gugugaga.ds.physics.rememberSlidingIndicator
import com.gugugaga.ds.theme.LocalOnGlass
import kotlin.math.abs
import kotlin.math.roundToInt

/** 玻璃排的数（设计稿 `d.css` 的 `.g.tabs` / `.tab` / `#g-search` / `.g.rb`，rem）。 */
object DeskNavSpec {
    /** 一格导航的宽；胶囊的内边。 */
    const val TAB = 7.6f
    const val PAD = 0.4f

    /** 圆钮之间、圆钮和胶囊之间（≥ 12 dp）。 */
    const val GAP = 1.2f
}

/**
 * 电脑的固定导航（玻璃，`docs/glass.md`）：报头下 1.2 rem 的一排，高 4.4 rem——
 * 左：返回圆钮（有上一页才出现）+ 导航胶囊（首页 / 发现 / 片库 / 合集 / 我的）；右：搜索胶囊（带 `Ctrl K`）+ 账号圆钮。**正中留空**（剧照的主体在中间）。
 * 胶囊 = 上游 `Bar` 材质，圆钮 = `Clear`；每个可点的圆钮自己一块玻璃，之间 ≥ 12 dp。
 * 选中 = 加粗 + 专色小圆点（decisions Q11）；选中的那一格是一块透镜（上游 `GlassModel.restingLens` + `SlidingIndicator`，做法同手机的 `chrome/PhoneTabBar.kt`）：
 * 按住左右拖，透镜跟手，松手落到哪格就去哪格。
 *
 * 画在 `GlassScene` 的前景层（[DesktopPages] 是它折射的页面内容）。播放器、登录页上不出现。
 * 账号菜单从账号圆钮里长出来（[DesktopAccountMenu]）。这里还放着键盘的兜底接收格（没有别的东西有焦点时，快捷键从它进来）。
 */
@Composable
fun DesktopChrome(route: Route, today: Today, modifier: Modifier = Modifier) {
    val g = LocalGraph.current
    val nav = LocalWideNav.current
    val colors = Guga.colors
    val density = LocalDensity.current
    val session by g.session.session.collectAsState()
    // 海报墙（片库 / 合集库里的一页）也算在「片库 / 合集」这一格下面
    val active = nav.activeTab(route) ?: if (route is Route.Library) (if (nav.tab == WideTab.Colls) WideTab.Colls else WideTab.Libs) else null
    val tabs = WideTab.entries
    val index = active?.let { tabs.indexOf(it) } ?: -1
    val ind = rememberSlidingIndicator(index.coerceAtLeast(0).toFloat(), tabs.size)
    LaunchedEffect(index) { if (index >= 0 && ind.targetValue != index.toFloat()) ind.animateTo(index.toFloat()) }
    val select: (WideTab) -> Unit = { tab ->
        nav.tab = tab
        if (route != Route.Home) g.nav.reset(Route.Home)
    }
    val selectNow = rememberUpdatedState(select)
    val indexNow = rememberUpdatedState(index)

    // 键盘：玻璃排里的东西有焦点时键从这里冒泡；什么都没有焦点时，焦点放到兜底接收格上。
    // 但任何一层（含播放器里的弹幕源搜索）的输入框有焦点时不抢：抢了字就打不进（主理人真窗口实测，Windows 同路径）。
    var chromeFocus by remember { mutableStateOf(false) }
    val sink = remember { FocusRequester() }
    val typing = g.isTyping
    LaunchedEffect(route, nav.tab, chromeFocus, DesktopFocus.pages, typing) {
        if (!chromeFocus && !DesktopFocus.pages && !typing) {
            withFrameNanos { }
            if (!chromeFocus && !DesktopFocus.pages && !g.isTyping) runCatching { sink.requestFocus() }
        }
    }
    Box(Modifier.fillMaxSize().onFocusChanged { chromeFocus = it.hasFocus }.desktopKeys()) {
        Box(Modifier.size(1.dp).focusRequester(sink).focusable().testTag("desktop-key-sink"))
        Row(
            modifier
                .fillMaxWidth()
                .padding(top = Guga.rem(DesktopSpec.MASTHEAD_TOP + DesktopSpec.MASTHEAD + DesktopSpec.NAV_GAP))
                .height(Guga.rem(DesktopSpec.NAV))
                .padding(horizontal = Guga.dimens.margin)
                .testTag("desktop-nav-row"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalOnGlass provides true) {
                // 返回永远在左上
                if (g.nav.canGoBack) {
                    GlassCircle("返回", "desktop-back", onClick = { g.nav.backOrHome() }) { Icon(GuguIcon.ChevronLeft, colors.ink, Guga.rem(1.8f)) }
                    Spacer(Modifier.width(Guga.rem(DeskNavSpec.GAP)))
                }
                val barPress = rememberPressState(PressKind.TabBar)
                // 搜索页上不排导航胶囊（设计稿 `p=search`：只有返回、搜索、账号），那一排留给大号的输入行
                if (route !is Route.Search) Box(Modifier.fillMaxHeight().glass(MaterialName.Bar, Guga.rem(2.2f), barPress).padding(Guga.rem(DeskNavSpec.PAD)).testTag("desktop-nav")) {
                    val tabW = Guga.rem(DeskNavSpec.TAB)
                    Box(
                        Modifier.fillMaxHeight().width(tabW * tabs.size).pointerInput(tabs.size, ind) {
                            val slop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val tabPx = size.width.toFloat() / tabs.size
                                val last = (tabs.size - 1).toFloat()
                                ind.press()
                                var dragging = false
                                var value = ind.targetValue
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!ch.pressed) break
                                    if (!dragging && abs(ch.position.x - down.position.x) > slop) dragging = true
                                    if (dragging) {
                                        value = (ch.position.x / tabPx - 0.5f).coerceIn(0f, last)
                                        ind.animateTo(value)
                                        ch.consume()
                                    }
                                }
                                ind.release()
                                if (dragging) {
                                    val target = value.roundToInt().coerceIn(0, tabs.size - 1)
                                    ind.animateTo(target.toFloat())
                                    if (target != indexNow.value) selectNow.value(tabs[target])
                                }
                            }
                        },
                    ) {
                        val expandX = with(density) { PressConfig.INDICATOR_EXPAND_X_DP.dp.toPx() }
                        val expandY = with(density) { PressConfig.INDICATOR_EXPAND_Y_DP.dp.toPx() }
                        // 透镜：选中的那一格。位置由布局给，图层只带抬起 / 果冻的缩放
                        Box(
                            Modifier
                                .offset { IntOffset((tabW * ind.value).roundToPx(), 0) }
                                .graphicsLayer {
                                    ind.applyTo(this, expandX, expandY)
                                    alpha = if (index >= 0) 1f else 0f
                                }
                                .width(tabW)
                                .fillMaxHeight()
                                .glass(GlassModel.restingLens(glassThemeHere()), Guga.rem(1.8f))
                                .testTag("desktop-tab-lens"),
                        )
                        Row(Modifier.fillMaxSize()) {
                            for ((i, tab) in tabs.withIndex()) {
                                val on = i == index
                                val press = rememberPressState()
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .width(tabW)
                                        .graphicsLayer {
                                            val s = IndicatorMath.magnification(ind.lift, IndicatorMath.proximity(ind.value - i))
                                            scaleX = s
                                            scaleY = s
                                        }
                                        .semantics { selected = on }
                                        .deskFocusRing(press, inside = true)
                                        .pressable(press, role = Role.Tab, label = tab.label) { if (!on || route != Route.Home) select(tab) }
                                        .testTag(if (on) "desktop-tab-on" else "desktop-tab-off"),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        BasicText(tab.label, style = Guga.song(rsp(1.5f), if (on) FontWeight.Black else FontWeight.Bold, colors.ink.copy(alpha = if (on) 1f else 0.74f), letterSpacing = 0.16.em), maxLines = 1)
                                        // 选中记号：专色小圆点（没选的格子留同样的高度，字不跳）
                                        Box(Modifier.padding(top = Guga.rem(0.3f)).size(Guga.rem(0.4f)).then(if (on) Modifier.background(colors.spot, CircleShape) else Modifier))
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // 搜索胶囊：Ctrl K
                val searchPress = rememberPressState()
                Row(
                    Modifier
                        .fillMaxHeight()
                        .deskFocusRing(searchPress, inside = true)
                        .glass(MaterialName.Bar, Guga.rem(2.2f), searchPress)
                        .pressable(searchPress, label = "搜索") { DesktopKeys.openSearch(g) }
                        .padding(start = Guga.rem(1.4f), end = Guga.rem(1.6f))
                        .testTag("desktop-search"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Guga.rem(0.9f)),
                ) {
                    Icon(GuguIcon.Search, colors.ink, Guga.rem(1.7f), strokeWidth = if (route is Route.Search) 2.6f else 2f)
                    BasicText("搜索", style = Guga.song(rsp(1.4f), FontWeight.SemiBold, letterSpacing = 0.1.em), maxLines = 1)
                    BasicText(DesktopCommand.Search.hint(LocalDesktopHost.current.modifier) ?: "Ctrl K", style = Guga.numeral(rsp(1.15f), colors.ink.copy(alpha = 0.7f), letterSpacing = 0.04.em), maxLines = 1)
                }
                Spacer(Modifier.width(Guga.rem(DeskNavSpec.GAP)))
                // 账号圆钮：用户名的头一个字。菜单从圆钮里长出来（上游 GlassMorph）
                GlassCircle("账号", "desktop-account", onClick = { DesktopAccountMenu.toggle() }, modifier = Modifier.sceneAnchor(DesktopAccountMenu.anchor)) {
                    BasicText(Rules.monogram(session?.userName ?: "客", person = true), style = Guga.song(rsp(1.7f), FontWeight.Black), maxLines = 1)
                }
            }
        }
        DesktopAccountMenu.Host(today)
    }
}

/** 一枚玻璃圆钮（`Clear` 材质，4.4 rem）。 */
@Composable
fun GlassCircle(label: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val press = rememberPressState()
    Box(
        modifier.size(Guga.rem(DesktopSpec.NAV)).deskFocusRing(press, inside = true).glass(MaterialName.Clear, Guga.rem(2.2f), press).pressable(press, label = label, onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 页面层里有没有东西拿着键盘焦点（[DesktopPages] 报上来）。 */
object DesktopFocus {
    var pages: Boolean by mutableStateOf(false)
}
