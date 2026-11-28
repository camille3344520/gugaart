package com.gugaart.ui.screens.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.components.ConfirmSheet
import com.gugaart.ui.components.Masthead
import com.gugaart.ui.components.RadioMark
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.Thumb
import com.gugaart.ui.data.Load
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.ReportScroll
import com.gugaart.ui.screens.StatusBand
import com.gugaart.ui.screens.open
import com.gugaart.ui.servers.ServerSheet
import com.gugaart.ui.servers.currentServerTitle
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.session.AppSession
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyException
import com.gugugaga.core.ItemsRequestOptions
import com.gugugaga.core.LibraryQuery
import com.gugugaga.core.StoredAccount
import com.gugugaga.core.StoredServer
import com.gugugaga.core.apiBaseOf
import com.gugugaga.core.describeEmbyError
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** 测试 / 截图：「我的」一开始推开着哪一块（"server" / "account"）。应用里是 null。 */
val LocalMePanel = staticCompositionLocalOf<String?> { null }

/** 「我的」页的几个数（设计稿 `me*`，手机 412 宽）。 */
private object MeSpec {
    val CARD_PHOTO = 76.dp
    val STAT = 34.sp
    val STRIP_THUMB = 124.dp
}

/**
 * 读者证下面三格数字 + 「记录」那一带。全部是服务器给的真实数（只读查询）；取不到的格子不写数字。
 * [played] = 看过几部（电影一部、剧集一季算一部，和读者证上的称号是同一个数，见 [countWatched]）。
 */
data class MeData(val played: Int?, val watching: Int, val favorites: Int?, val resume: List<Title>)

/** 只读：已看（[countWatched]）、继续观看的条数（「记录」那一行）、收藏（盖过章的）。收藏只取 `TotalRecordCount`，不拉条目。 */
suspend fun loadMe(client: EmbyClient): MeData {
    suspend fun count(types: List<String>, status: String): Int? = try {
        client.getItems(LibraryQuery(includeItemTypes = types, recursive = true, status = listOf(status), limit = 1)).totalRecordCount
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
    val resume = client.getResume(limit = 12, o = ItemsRequestOptions(fields = listOf("PrimaryImageAspectRatio")))
    return MeData(
        played = try {
            countWatched(client).total
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        },
        watching = maxOf(resume.totalRecordCount, resume.items.size),
        favorites = count(listOf("Movie", "Series", "Episode", "BoxSet"), "IsFavorite"),
        resume = resume.items.map { client.titleOf(it) },
    )
}

/** 订阅号：期号补到 6 位（「000318」）。 */
fun subscriberNo(volume: Int): String = volume.coerceAtLeast(0).toString().padStart(6, '0')

/** 「另有 2 台 · 切换」/「只有这一台」。 */
fun othersLabel(total: Int, unit: String): String = if (total > 1) "另有 ${total - 1} $unit · 切换" else "只有这一$unit"

/**
 * 「我的」（手机底栏第四格；设计稿 `p=me`：读者证、`server`、`account`、`end`）。
 *
 * 读者证（她的署名照 + 订阅号 + 用户名）、三格数字、服务器 / 账号两行——点一行，切换面板在这一行**下面推开**，不盖东西——
 * 再往下是「翻阅」：记录、收藏、合集、设置、关于。
 * 切换账号 / 换服务器 / 退出登录全部是上游 `SessionManager` 的动作；添加服务器 / 添加账号走上游路由 `Servers` / `Login`。
 * [onOpenFavs] / [onOpenSets]：上游没有「收藏」「合集」的独立路由时由根界面接（默认：收藏 = `Route.History("favorites")` 的收藏页，三端同一个路由；合集没有入口就不画这一行）。
 */
@Composable
fun MeScreen(today: Today, onOpenFavs: (() -> Unit)? = null, onOpenSets: (() -> Unit)? = null) {
    val graph = LocalGraph.current
    val colors = Guga.colors
    val dimens = Guga.dimens
    val session by graph.session.session.collectAsState()
    val s = session ?: return
    val revision by graph.session.revision.collectAsState()
    val scope = rememberCoroutineScope()
    val load: Load<MeData> = rememberLoad("me", s.userId) { loadMe(it) }
    val data = load.value
    var servers by remember { mutableStateOf<List<StoredServer>>(emptyList()) }
    LaunchedEffect(revision, s.userId) { servers = graph.session.saved().servers }
    val current = servers.firstOrNull { it.id == s.serverId || it.serverId == s.serverId || apiBaseOf(it.address) == s.serverUrl }
    val accounts = current?.accounts.orEmpty()
    val startPanel = LocalMePanel.current
    // 截图预置 "server"：服务器那一行现在打开的是纸片（不在页里推开）
    var open by remember { mutableStateOf(startPanel?.takeIf { it != "server" }) }
    LaunchedEffect(startPanel) { if (startPanel == "server") ServerSheet.show() }
    var problem by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val scroll = rememberScrollState(LocalPreset.current.scroll)
    ReportScroll(scroll)
    // P2-13：进了「我的」= 看到了读者证上的称号，导航上的小点清掉
    androidx.compose.runtime.LaunchedEffect(Unit) { RankBoard.seen() }

    fun switchTo(server: StoredServer, account: StoredAccount) {
        if (busy != null) return
        busy = server.id + "|" + account.userId
        problem = null
        scope.launch {
            try {
                graph.session.switchAccount(server.id, account.userId)
                open = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is EmbyException && e.kind == EmbyException.Kind.Unauthorized) {
                    // 令牌失效：去登录页重新输密码（密码由用户自己输入）
                    graph.nav.navigate(Route.Login(switch = true, server = apiBaseOf(server.address), user = account.userName))
                } else {
                    problem = describeEmbyError(e)
                }
            } finally {
                busy = null
            }
        }
    }

    Box(Modifier.fillMaxSize().testTag("me-screen")) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = dimens.margin)) {
            Spacer(Modifier.height(dimens.statusBar + 8.dp))
            Masthead(
                volume = "",
                date = "",
                title = "我的",
                leading = "MEMBER",
                trailing = {
                    val press = rememberPressState()
                    Box(Modifier.size(40.dp).pressGraphics(press).pressable(press, label = "设置") { graph.nav.navigate(Route.Settings()) }, contentAlignment = Alignment.CenterEnd) {
                        Icon(GuguIcon.SlidersHorizontal, colors.ink, 17.dp)
                    }
                },
            )
            ReaderCard(s, today, data?.played, Modifier.padding(top = 26.dp))
            Stats(data)
            Rule()
            // ---- 翻阅：记录 / 收藏紧跟在观影卡下面（P2-12：常用的不用往下翻一屏半），称号徽章墙往下挪
            SectionHeader("常去", "SHORTCUTS", Modifier.padding(top = 26.dp))
            var n = 0
            PageRow(++n, "记录", data?.let { if (it.watching > 0) "${it.watching} 条" else "还没有" }) { graph.nav.navigate(Route.History()) }
            val resume = data?.resume.orEmpty()
            if (resume.isNotEmpty()) {
                // 一带缩略图：往右滑出页边（右边不留页边，露出半张 = 还能滑）
                Row(Modifier.padding(bottom = 18.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (t in resume.take(8)) {
                        val press = rememberPressState()
                        Column(Modifier.width(MeSpec.STRIP_THUMB).pressGraphics(press).pressable(press, label = t.displayName) { graph.open(t.id) }) {
                            Thumb(t.art.thumb, t.displayName, MeSpec.STRIP_THUMB, progress = t.progress)
                            BasicText(t.displayName, Modifier.padding(top = 8.dp), style = Guga.song(13.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Rule()
            PageRow(++n, "收藏", data?.favorites?.let { if (it > 0) "$it 部收藏" else "还没有收藏" }) { (onOpenFavs ?: { graph.nav.navigate(Route.History("favorites")) })() }
            Rule()
            // 离线下载（1.6 批次 E，只在手机上）：账号能下载、或者已经下过东西才有这一行
            com.gugaart.ui.download.rememberDownloads()?.takeIf { it.allowed || it.mine.isNotEmpty() }?.let { dl ->
                PageRow(++n, "下载", com.gugaart.ui.download.downloadsHint(dl.mine.map { it.entry })) { graph.nav.navigate(Route.History(com.gugaart.ui.download.DOWNLOADS_TAB)) }
                Rule()
            }
            // 特别称号：一页两排六枚，左右滑翻页（拿到的排前面、彩色）；点一枚看拿法
            SectionHeader("特别称号", "SPECIAL", Modifier.padding(top = 26.dp), trailing = "${RankBoard.owned.size} / ${Specials.COUNT}")
            SpecialGrid(RankBoard.owned, Modifier.padding(top = 16.dp).testTag("me-specials"), columns = 6, badge = 46.dp, nameSize = 11.sp, rows = 2) { id -> RankBoard.special = SpecialNews(listOf(id), browse = true) }
            Rule(Modifier.padding(top = 18.dp))

            // ---- 服务器：点这一行 = 和首页报头下那一行同一张「换一台服务器」纸片（1.5.0，[ServerSheet]）
            SwitchRow("服务器", currentServerTitle() ?: s.serverName.ifBlank { "Emby 服务器" }, othersLabel(servers.size.coerceAtLeast(1), "台"), false, tag = "me-server-row") { ServerSheet.show() }
            Rule()

            // ---- 账号
            SwitchRow("账　号", s.userName, othersLabel(accounts.size.coerceAtLeast(1), "个"), open == "account", tag = "me-account-row") { open = if (open == "account") null else "account" }
            AnimatedVisibility(open == "account", enter = expandVertically(), exit = shrinkVertically()) {
                SwitchPanel("me-account-panel") {
                    for (a in accounts) {
                        val isCurrent = a.userId == s.userId
                        PanelOption(
                            title = a.userName,
                            sub = when {
                                isCurrent -> "正在使用"
                                busy == current?.id + "|" + a.userId -> "正在切换……"
                                else -> "已保存在这台设备上"
                            },
                            selected = isCurrent,
                        ) {
                            val server = current
                            if (isCurrent || server == null) open = null else switchTo(server, a)
                        }
                        Rule()
                    }
                    PanelAction("添加账号") { graph.nav.navigate(Route.Login(switch = true, server = current?.let { apiBaseOf(it.address) } ?: s.serverUrl)) }
                    Rule()
                    val press = rememberPressState()
                    Column(Modifier.fillMaxWidth().pressGraphics(press).pressable(press, label = "退出登录") { leaving = true }.padding(vertical = 14.dp).testTag("me-sign-out")) {
                        BasicText("退出登录", style = Guga.song(Guga.type.row, FontWeight.Black, colors.seal), maxLines = 1)
                        BasicText("从这台设备上移除「${s.userName}」", Modifier.padding(top = 4.dp), style = Guga.note(Guga.type.caption, colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Rule()
            problem?.let { BasicText("没换成：$it", Modifier.padding(top = 10.dp), style = Guga.note(Guga.type.caption, colors.seal), maxLines = 2) }
            Spacer(Modifier.height(26.dp))
            if (onOpenSets != null) {
                PageRow(++n, "合集", null, onOpenSets)
                Rule()
            }
            PageRow(++n, "设置", "播放、字幕、弹幕、外观") { graph.nav.navigate(Route.Settings()) }
            Rule()
            PageRow(++n, "关于", graph.deps.appVersion) { graph.nav.navigate(Route.Settings("about")) }
            Rule()
            BasicText("咕咕嘎嘎 · 用你的片库自动排片", Modifier.fillMaxWidth().padding(top = 30.dp), style = Guga.song(Guga.type.caption, color = colors.faint, letterSpacing = 0.2.em).copy(textAlign = TextAlign.Center), maxLines = 1)
            // 底部安全区：末尾内容能滚到标签栏上方
            Spacer(Modifier.height(150.dp + dimens.tabLift))
        }
        StatusBand(solid = com.gugaart.ui.screens.scrolledPast(scroll, 2f))
        if (leaving) {
            ConfirmSheet(
                title = "退出登录？",
                confirm = "退出登录",
                onConfirm = {
                    leaving = false
                    // 上游：吊销令牌、忘掉这个账号、回到登录页
                    graph.scope.launch { graph.session.signOut() }
                },
                onDismiss = { leaving = false },
                danger = true,
            )
        }
    }
}

/**
 * 读者证：1 dp 米白线框的一张卡——她的署名照（设置里关掉她 → 只剩字）+ 订阅号 + 「读者证」+ 用户名 + 一句；
 * 下面是称号（[played] = 看过几部；还没数出来就不写）：点它 = 出称号纸片（十级都在上面）。
 */
@Composable
private fun ReaderCard(s: AppSession, today: Today, played: Int?, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Row(modifier.fillMaxWidth().border(1.dp, colors.ink).padding(horizontal = 16.dp, vertical = 20.dp).testTag("reader-card"), verticalAlignment = Alignment.CenterVertically) {
        if (LocalGugaPrefs.current.showMascot) {
            Byline(MeSpec.CARD_PHOTO, live = false)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            BasicText("MEMBER · No. ${subscriberNo(today.volume)}", style = Guga.note(Guga.type.kicker, colors.spot, letterSpacing = 0.2.em), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("观影卡", Modifier.padding(top = 4.dp), style = Guga.song(Guga.type.section, FontWeight.Black, letterSpacing = 0.14.em), maxLines = 1)
            BasicText(s.userName, Modifier.padding(top = 8.dp), style = Guga.song(Guga.type.row, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText("陪你看片第 ${today.volume} 天 · 咕咕嘎嘎为你排片", Modifier.padding(top = 5.dp), style = Guga.song(Guga.type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (played != null) RankLine(played, Modifier.padding(top = 10.dp))
        }
    }
}

/** 读者证上的称号一行：这一级的徽章 + 称号 + 「再看 60 部升「阅片无数」」。点了出称号纸片。 */
@Composable
private fun RankLine(played: Int, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val level = Ranks.levelOf(played)
    val press = rememberPressState()
    Row(
        modifier.fillMaxWidth().heightIn(min = 36.dp).pressGraphics(press).pressable(press, label = "称号 ${Ranks.badge(played)}", onClick = { RankBoard.open(RankNews(level, played, browse = true)) }).testTag("me-rank"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankBadge(level, 34.dp)
        BasicText(Ranks.of(level)?.title ?: "还没有称号", Modifier.padding(start = 8.dp), style = Guga.song(Guga.type.row, FontWeight.Black), maxLines = 1)
        BasicText(Ranks.nextLine(played), Modifier.padding(start = 10.dp).weight(1f), style = Guga.song(Guga.type.fine, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 两格数字：已看 / 收藏（观看状态只有未看 / 已看，D-watch：不再有「在看」那一格）。还没取到 = 一道短横，不编数。 */
@Composable
private fun Stats(data: MeData?) {
    val colors = Guga.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("me-stats")) {
        val cells = listOf("已看" to data?.played, "收藏" to data?.favorites)
        for ((i, c) in cells.withIndex()) {
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(colors.line))
            Column(Modifier.weight(1f).padding(start = if (i == 0) 0.dp else 16.dp, top = 14.dp, bottom = 12.dp)) {
                BasicText(c.second?.toString() ?: "—", style = Guga.numeral(MeSpec.STAT), maxLines = 1)
                BasicText(c.first, Modifier.padding(top = 2.dp), style = Guga.song(Guga.type.caption, color = colors.dim), maxLines = 1)
            }
        }
    }
}

/** 「服务器　家里的片库　　另有 2 台 · 切换 ⌄」：专色小标 + 当前值 + 右端斜体小字和折角。 */
@Composable
private fun SwitchRow(label: String, value: String, hint: String, expanded: Boolean, tag: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().heightIn(min = 50.dp).pressGraphics(press).pressable(press, label = "$label $value", onClick = onClick).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.width(64.dp), style = Guga.song(Guga.type.caption, color = colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        BasicText(value, Modifier.weight(1f), style = Guga.song(Guga.type.row, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(hint, Modifier.padding(start = 8.dp, end = 6.dp), style = Guga.note(Guga.type.caption, colors.dim), maxLines = 1)
        Icon(if (expanded) GuguIcon.ChevronUp else GuguIcon.ChevronDown, colors.dim, 12.dp)
    }
}

/** 推开的切换面板：左边一道专色竖线，里面是一行行选项。在页面流里，把下面的内容推开。 */
@Composable
private fun SwitchPanel(tag: String, content: @Composable () -> Unit) {
    val colors = Guga.colors
    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(colors.spot, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), size.height), 2.dp.toPx())
            drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.padding(start = 18.dp).testTag(tag),
    ) { content() }
}

/** 面板里的一个选项：单选记号（选中 = 实心方块，不靠颜色单独表达：副行也写着「正在使用」）+ 名字 + 副行。 */
@Composable
private fun PanelOption(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).pressGraphics(press).pressable(press, label = title, onClick = onClick).padding(vertical = 10.dp).testTag("me-option"), verticalAlignment = Alignment.CenterVertically) {
        RadioMark(selected, Modifier.padding(end = 14.dp), size = 8.dp)
        Column(Modifier.weight(1f)) {
            BasicText(title, style = Guga.song(Guga.type.row * 0.94f, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicText(sub, Modifier.padding(top = 4.dp), style = Guga.note(Guga.type.caption, colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PanelAction(text: String, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).pressGraphics(press).pressable(press, label = text, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(GuguIcon.Plus, colors.ink, 13.dp)
        BasicText(text, style = Guga.song(Guga.type.row * 0.94f, FontWeight.Bold), maxLines = 1)
    }
}

/** 「翻阅」里的一行：斜体专色编号 + 宋体大字 + 右端斜体小字 + 折角。 */
@Composable
private fun PageRow(number: Int, title: String, hint: String?, onClick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    Row(Modifier.fillMaxWidth().heightIn(min = 62.dp).pressGraphics(press).pressable(press, label = title, onClick = onClick).testTag("me-page-row"), verticalAlignment = Alignment.CenterVertically) {
        BasicText("0$number", Modifier.width(42.dp), style = Guga.numeral(19.sp, colors.spot), maxLines = 1)
        BasicText(title, Modifier.weight(1f), style = Guga.song(22.sp, FontWeight.Black, letterSpacing = 0.08.em), maxLines = 1)
        if (hint != null) BasicText(hint, Modifier.padding(start = 8.dp, end = 6.dp), style = Guga.note(Guga.type.caption, colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(GuguIcon.ChevronRight, colors.dim, 12.dp)
    }
}
