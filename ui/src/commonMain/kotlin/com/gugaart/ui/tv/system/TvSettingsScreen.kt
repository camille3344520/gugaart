package com.gugaart.ui.tv.system

import com.gugugaga.app.settings.danmakuPrefs
import com.gugaart.ui.update.updater
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.Today
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.mascot.Byline
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.settings.GugaSection
import com.gugaart.ui.screens.settings.LocalGugaSettings
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvPageHeader
import com.gugaart.ui.tv.TvSpec
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDetailSpec
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.TvGroupsRoot
import com.gugaart.ui.tv.detail.TvOption
import com.gugaart.ui.tv.detail.TvSecHead
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon

/** 一列里同时排得下几行（多了就跟着焦点滑）。 */
private const val ROWS_VISIBLE = 8
private const val PICKS_VISIBLE = 8

/** 选择器上面排了字幕预览条时，一屏排得下的项数。 */
private const val PICKS_VISIBLE_WITH_PREVIEW = 5

/**
 * 列表窗口从第几个开始，才能让第 [focus] 个留在 [visible] 行的窗口里。焦点下面留两行；[next] = 下一个能接焦点的行（中间可能隔着小标题、
 * 只读的行）：它必须已经排出来，否则遥控器往下走不到它。纯函数，有单测（`Tv1LogicTest.settingsWindow`）。
 */
fun windowStart(focus: Int, total: Int, visible: Int, next: Int = focus): Int =
    if (total <= visible) 0 else maxOf(focus - visible + 3, next - visible + 1).coerceIn(0, total - visible)

/**
 * 设置（电视，设计稿 `p=settings`）：左页九个分区，右页是这一分区的行，选择器从右边页边出来（纸面，不是玻璃）。
 * 路径：左页上下换分区（**右页跟着翻**），右 / 确定 = 进右页第一行；右页上下换行，确定 = 开关 / 打开选择器 / 执行，左 = 回左页原来的分区；
 * 选择器：上下换项，确定选中并收起，返回 / 左收起（焦点回那一行）。默认焦点 = 当前分区那一行。
 */
@Composable
fun TvSettings(section: String?, today: Today) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val preset = LocalPreset.current
    val back = LocalBackDispatcher.current
    val groups = remember { TvGroups() }
    val store = LocalGugaSettings.current ?: remember(g) { GugaSettingsStore(g.deps.local) }
    val prefs by store.prefs.collectAsState()
    val settings by g.settings.state.collectAsState()
    val status by g.updater.status.collectAsState()

    // `Route.Settings("licenses")`（菜单 / 关于页的那一行）= 「关于」分区 + 开源许可那一列已经推开
    val openLicenses = section == com.gugaart.ui.screens.settings.OpenSourceLicenses.KEY
    val openDiag = section == GugaSection.Diagnostics.key
    val initialSec = when {
        openLicenses || openDiag -> GugaSection.About
        else -> GugaSection.of(section) ?: GugaSection.Account
    }
    var current by remember { mutableStateOf(initialSec) }
    var advOpen by remember { mutableStateOf(initialSec.advanced || openLicenses || openDiag) }
    var diagOpen by remember { mutableStateOf(openDiag || preset.state == "diag") }
    var queriesCleared by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(preset.state?.takeIf { it.startsWith("pick:") }?.removePrefix("pick:") ?: TV_LICENSES.takeIf { openLicenses }) }
    var confirming by remember { mutableStateOf<String?>(null) }
    // 系统安全存储里的两条密钥（弹幕服务器地址、TMDB 令牌）：上游的 SecretsStore，和手机 / 电脑读写的是同一份
    val secrets by g.secrets.state.collectAsState()
    LaunchedEffect(Unit) { g.secrets.load() }
    // 弹幕服务器清单（多个弹幕服务器；danmaku/DanmakuServers.kt）
    val dmServers by com.gugaart.ui.danmaku.DanmakuServers.state.collectAsState()
    LaunchedEffect(Unit) {
        com.gugaart.ui.danmaku.DanmakuServers.bind(g.deps.secure, g.deps.debug.defaultSecrets?.danmakuApiUrl)
        com.gugaart.ui.danmaku.DanmakuServers.load()
    }
    // 备份与还原（账号分区）
    val backupScope = androidx.compose.runtime.rememberCoroutineScope()
    val backupFiles = com.gugaart.ui.backup.LocalBackupFiles.current
    val backup = remember(g, backupFiles) { com.gugaart.ui.backup.BackupController(g, store, backupFiles, backupScope) }
    LaunchedEffect(backup) { backup.load() }
    // Trakt（账号分区）
    LaunchedEffect(Unit) { com.gugaart.ui.trakt.TraktService.bind(g) }
    val traktState by com.gugaart.ui.trakt.TraktService.state.collectAsState()
    val traktPrefs by com.gugaart.ui.trakt.TraktService.prefs.collectAsState()
    val traktSync by com.gugaart.ui.trakt.TraktService.syncMessage.collectAsState()
    val traktCalendar = com.gugaart.ui.components.rememberTraktCalendar()
    var traktJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var traktMessage by remember { mutableStateOf<String?>(null) }
    val trakt = TvTrakt(
        traktState, traktPrefs, traktSync, traktMessage,
        connect = { traktMessage = null; traktJob = backupScope.launch { traktMessage = com.gugaart.ui.trakt.TraktService.connect() } },
        cancel = { traktJob?.cancel(); traktJob = null },
        disconnect = { backupScope.launch { com.gugaart.ui.trakt.TraktService.disconnect() } },
        setPrefs = { com.gugaart.ui.trakt.TraktService.setPrefs(g.deps.local, it) },
        sync = { g.session.session.value?.client?.let { c -> backupScope.launch { com.gugaart.ui.trakt.TraktService.syncToEmby(c) } } },
        calendar = traktCalendar.second,
        openSeries = { id -> g.nav.navigate(com.gugugaga.app.nav.Route.Item(id)) },
    )
    // 字幕 / 弹幕的字体单子（1.4.1：和字幕共用；要下载的那款选了就开始下）
    val styleFonts = com.gugaart.ui.player.rememberStyleFonts()
    val fontStates by styleFonts.states.collectAsState()
    val importedStore = com.gugaart.ui.screens.settings.rememberImportedFonts()
    val imported by importedStore.state.collectAsState()
    val fontScope = androidx.compose.runtime.rememberCoroutineScope()
    val fonts = remember(prefs, settings, fontStates, imported) {
        val prefetch: (com.gugaart.ui.data.StyleFont) -> Unit = { f -> fontScope.launch { styleFonts.ensure(f) } }
        com.gugaart.ui.tv.system.TvFontChoices(
            subtitle = com.gugaart.ui.data.subtitleFontChoices(prefs.subStyle.font, imported, fontStates, { id -> store.update { it.copy(subStyle = it.subStyle.copy(font = id)) } }, prefetch),
            danmaku = com.gugaart.ui.data.danmakuFontChoices(
                settings.danmakuPrefs.render.font, prefs.danmakuFont, imported, fontStates,
                setUpstream = { f ->
                    val cur = ((g.settings.value["danmaku"] as? kotlinx.serialization.json.JsonObject)?.get("render") as? kotlinx.serialization.json.JsonObject) ?: kotlinx.serialization.json.JsonObject(emptyMap())
                    g.settings.patch("danmaku", "render" to kotlinx.serialization.json.JsonObject(LinkedHashMap(cur).also { it["font"] = kotlinx.serialization.json.JsonPrimitive(f.id) }))
                },
                setExtra = { id -> store.update { it.copy(danmakuFont = id) } },
                selectImported = importedStore::select,
                prefetch = prefetch,
            ),
            subtitleStatus = com.gugaart.ui.screens.settings.subtitleFontStatus(prefs.subStyle.styleFont, prefs.subStyle.styleFont?.let { fontStates[it] }, styleFonts.available),
        )
    }
    // 1.5.0：播放分区的「多台服务器」（数据层的服务器列表 + 偏好存储）
    val serverSwitcher = com.gugaart.ui.servers.currentServerSwitcher()
    val otherStore = com.gugaart.ui.servers.currentOtherServersPrefs(serverSwitcher)
    val otherPrefs by otherStore.prefs.collectAsState()
    val savedServers by serverSwitcher.entries.collectAsState()
    val rows = remember(current, settings, prefs, status, secrets, dmServers, backup.webdav, backup.pending, backup.message, backup.busy, traktState, traktPrefs, traktSync, traktMessage, traktCalendar.second, fonts, otherPrefs, savedServers, diagOpen, queriesCleared) {
        tvSettingRows(
            current, settings, prefs, g, store, secrets,
            saveSecret = { tmdb, dm -> g.scope.launch { g.secrets.save(tmdbToken = tmdb, danmakuApiUrl = dm) } },
            danmakuServers = dmServers?.map { it.url },
            backup = backup,
            trakt = trakt,
            fonts = fonts,
            otherServers = TvOtherServers(savedServers, otherPrefs, otherStore::setEnabled, otherStore::setIncluded),
            saveDanmakuServers = { urls ->
                g.scope.launch {
                    com.gugaart.ui.danmaku.DanmakuServers.save(urls)
                    g.secrets.load()
                }
            },
            diagOpen = diagOpen,
            queriesCleared = queriesCleared,
            onToggleDiag = { diagOpen = !diagOpen },
            onClearQueries = { queriesCleared = true },
        )
    }
    val focusRows = rows.filter { it.focusable }
    val picked = rows.firstOrNull { it.id == picker } as? TvChoice
    val panel = rows.firstOrNull { it.id == picker } as? TvPanel

    // ---- 排顺序（[TvOrder]）：页边的一列里「确定拿起 → 上下移动 → 确定放下」
    val ordering = rows.firstOrNull { it.id == picker } as? TvOrder
    var order by remember(ordering?.items) { mutableStateOf(ordering?.items?.map { it.first }.orEmpty()) }
    var held by remember(picker) { mutableStateOf<String?>(null) }
    // 拿起来以后方向键挪的是那一项，不是焦点（左右不动）
    groups.intercept = if (ordering != null && held != null) { dir ->
        if (dir == TvDir.Up || dir == TvDir.Down) held?.let { order = tvMoved(order, it, dir == TvDir.Down) }
        true
    } else null

    // ---- 打字（[TvText]）：换成遥控器全键盘那一屏
    var editing by remember { mutableStateOf(preset.state?.takeIf { it.startsWith("edit:") }?.removePrefix("edit:")) }
    val edit = rows.firstOrNull { it.id == editing } as? TvText
    var draft by remember(editing) { mutableStateOf(edit?.value.orEmpty()) }
    var upper by remember(editing) { mutableStateOf(false) }
    var reveal by remember(editing) { mutableStateOf(false) }
    var edited by remember { mutableStateOf(false) }

    // 焦点到哪个分区，右页就翻到哪个分区
    val navSections = remember(advOpen) {
        if (advOpen) GugaSection.PRIMARY + GugaSection.Advanced + GugaSection.ADVANCED_CHILDREN
        else GugaSection.PRIMARY + GugaSection.Advanced
    }
    val cur = groups.current
    LaunchedEffect(cur, navSections) {
        if (cur?.startsWith("sec-") == true) navSections.firstOrNull { "sec-${it.key}" == cur }?.let { if (it != current) { current = it; groups.forget("rows"); confirming = null } }
        if (cur?.startsWith("row-") == true && cur != "row-$confirming") confirming = null
    }
    fun closePicker() {
        val id = picker
        picker = null
        if (id != null) g.focus.request("row-$id")
    }
    groups.edge = { from, dir ->
        when {
            from == "sections" -> if (dir == TvDir.Right && groups.has("rows")) "rows" else if (dir == TvDir.Left) null else TvGroups.STAY
            from == "rows" -> if (dir == TvDir.Left) "sections" else TvGroups.STAY
            from.startsWith("kb-") -> {
                TvKeyboard.moveVertical(groups, g.focus, from, dir, TvKeyboard.ROWS_WIDE.size)
                TvGroups.STAY
            }
            else -> {
                if (dir == TvDir.Left) closePicker()
                TvGroups.STAY
            }
        }
    }
    DisposableEffect(picker != null, held != null, editing != null) {
        val un = when {
            // 键盘那一屏：返回 = 不改，回到那一行
            editing != null -> back.push { editing = null; true }
            // 拿着一项：返回 = 放回原处
            held != null -> back.push {
                held = null
                order = ordering?.items?.map { it.first }.orEmpty()
                true
            }
            picker != null -> back.push { closePicker(); true }
            else -> null
        }
        onDispose { un?.invoke() }
    }
    LaunchedEffect(picker) { if (picker != null) groups.enterWhenReady("picker", g.focus) }
    LaunchedEffect(editing) {
        if (editing != null) {
            edited = true
            // 每次打开键盘都从同一个键起（上一回停在哪个键不记）
            for (r in 0..TvKeyboard.ROWS_WIDE.size) groups.forget("kb-$r")
            groups.enterWhenReady("kb-1", g.focus)
        } else if (edited) {
            // 从键盘那一屏回来：焦点回到刚才那一行
            groups.enterWhenReady("rows", g.focus)
        }
    }

    TvGroupsRoot(groups, Modifier.testTag("tv-settings-${current.key}")) {
        Column(Modifier.fillMaxSize()) {
            TvPageHeader("设置", "SETTINGS", today)
            if (edit != null) {
                // ---- 打字：左 这一项和输入框，右 遥控器全键盘（和登录页同一个；多一排括号 / 分号 / 引号）----
                groups.remove("sections")
                groups.remove("rows")
                Row(Modifier.fillMaxSize().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 44.dp).testTag("tv-settings-edit")) {
                    Column(Modifier.width(620.dp)) {
                        BasicText("${current.title} · ${edit.label}", Modifier.padding(bottom = 22.dp), style = Guga.song(38.sp, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TvField(edit.english.ifEmpty { edit.label }, if (edit.secret && !reveal) "•".repeat(draft.length) else draft, active = true, hint = edit.hint)
                        for (line in listOfNotNull(edit.note, "${draft.length} 个字符" + if (draft.length > 22) " · 只显示末尾 22 个" else "")) {
                            BasicText(line, style = Guga.song(24.sp, color = colors.dim, lineHeight = 38.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(94.dp))
                    TvKeyboardPad(
                        upper,
                        bottomLabel = { k ->
                            when (k) {
                                "shift" -> if (upper) "小写" else "大写"
                                "show" -> if (!edit.secret) "清空" else if (reveal) "隐藏" else "显示"
                                "next" -> "保存"
                                else -> TvKeyboard.BOTTOM_LABEL[k].orEmpty()
                            }
                        },
                        onKey = { k ->
                            when (k) {
                                "shift" -> upper = !upper
                                "show" -> if (edit.secret) reveal = !reveal else draft = ""
                                "next" -> {
                                    edit.write(draft.trim())
                                    editing = null
                                }
                                else -> draft = TvKeyboard.type(draft, k, upper)
                            }
                        },
                        rows = TvKeyboard.ROWS_WIDE,
                    )
                }
            } else Row(Modifier.fillMaxSize().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 26.dp)) {
                // ---- 左页：六个主要分区 + 折叠「高级」 ----
                TvGroup("sections", navSections.map { "sec-${it.key}" }, horizontal = false, default = "sec-${current.key}")
                Column(Modifier.width(440.dp)) {
                    for (s in navSections) {
                        val on = s == current
                        val isAdvParent = s == GugaSection.Advanced
                        val isAdvChild = s in GugaSection.ADVANCED_CHILDREN
                        TvBlockFocus(
                            "sec-${s.key}",
                            {
                                if (isAdvParent) {
                                    advOpen = !advOpen
                                    if (!advOpen && current in GugaSection.ADVANCED_CHILDREN) current = GugaSection.Advanced
                                } else {
                                    groups.enter("rows", g.focus)
                                }
                            },
                            Modifier.fillMaxWidth().drawBehind {
                                drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                                if (isAdvChild) drawRect(colors.seal, Offset(0f, 0f), androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
                            },
                            label = s.title,
                            default = on && panel == null,
                        ) { focused ->
                            val ink = if (focused) colors.onInk else if (on) colors.spot else colors.ink
                            val rowHeight = if (isAdvChild) 64.dp else 84.dp
                            Row(
                                Modifier.fillMaxWidth().height(rowHeight).padding(start = if (isAdvChild) 52.dp else 26.dp, end = 26.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!isAdvParent && !isAdvChild) {
                                    BasicText(s.number, Modifier.width(64.dp), style = Guga.numeral(25.sp, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot), maxLines = 1)
                                }
                                BasicText(s.title, Modifier.weight(1f), style = Guga.song(if (isAdvChild) 27.sp else 30.sp, FontWeight.Bold, ink), maxLines = 1)
                                val arrow = if (isAdvParent && advOpen) GuguIcon.ChevronDown else GuguIcon.ChevronRight
                                Icon(arrow, ink.copy(alpha = if (on) 1f else 0.5f), 26.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(84.dp))
                // ---- 右页：这一分区的行 ----
                Column(Modifier.weight(1f).padding(end = if (picked != null || ordering != null || panel != null) 604.dp else 0.dp)) {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (current.number.isNotEmpty()) BasicText(current.number, style = Guga.numeral(36.sp, colors.spot), maxLines = 1)
                        BasicText(current.title, style = Guga.song(38.sp, FontWeight.Black), maxLines = 1)
                        BasicText(current.english, Modifier.padding(top = 10.dp), style = Guga.numeral(20.sp, colors.spot, letterSpacing = androidx.compose.ui.unit.TextUnit(0.24f, androidx.compose.ui.unit.TextUnitType.Em)), maxLines = 1)
                        Box(Modifier.weight(1f).height(1.dp).background(colors.line))
                        BasicText("${focusRows.size} 项", style = Guga.note(23.sp, colors.dim), maxLines = 1)
                    }
                    if (current == GugaSection.About && LocalGugaPrefs.current.showMascot) {
                        Row(Modifier.padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            Byline(96.dp, live = false)
                            Column {
                                BasicText("咕咕嘎嘎", style = Guga.song(36.sp, FontWeight.Black, letterSpacing = androidx.compose.ui.unit.TextUnit(0.3f, androidx.compose.ui.unit.TextUnitType.Em)), maxLines = 1)
                                BasicText("咕嘎画报 · EMBY · ${g.deps.appVersion}", style = Guga.song(22.sp, color = colors.dim), maxLines = 1)
                            }
                        }
                    }
                    TvGroup("rows", focusRows.map { "row-${it.id}" }, horizontal = false)
                    // 行多了跟着焦点滑：窗口按「所有行」算（小标题也占一行）
                    val focusAt = rows.indexOfFirst { "row-${it.id}" == (groups.lastOf("rows") ?: "") }.coerceAtLeast(0)
                    val visible = if (current == GugaSection.About) 6 else ROWS_VISIBLE
                    val first = windowStart(focusAt, rows.size, visible, next = rows.indices.firstOrNull { it > focusAt && rows[it].focusable } ?: focusAt)
                    // 每一行带着自己的 key：窗口往下滑一格时行是「挪上去」，不是原地换内容——不带 key 的话，拿着焦点的那个位置会换成另一行
                    // （甚至换成不接焦点的小标题），焦点当场丢掉，往下就走不动了（`Tv2SystemTest.layout` 停在「单集分页数量」就是这个）。
                    for (r in rows.drop(first).take(visible)) key(r.id) {
                        when (r) {
                            is TvHead -> Row(Modifier.fillMaxWidth().height(54.dp).padding(start = 26.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                BasicText(r.label, style = Guga.song(24.sp, FontWeight.Black, colors.dim), maxLines = 1)
                                BasicText(r.english, style = Guga.numeral(18.sp, colors.spot), maxLines = 1)
                            }
                            else -> TvSettingLine(r, confirming == r.id, open = picker == r.id) {
                                when (r) {
                                    is TvToggle -> r.set(!r.on)
                                    is TvChoice -> picker = r.id
                                    is TvOrder -> picker = r.id
                                    is TvPanel -> picker = r.id
                                    is TvText -> editing = r.id
                                    is TvAction -> if (r.confirm != null && confirming != r.id) confirming = r.id else {
                                        confirming = null
                                        r.run()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                    if (first + visible < rows.size && rows.drop(first + visible).any { it.focusable }) BasicText("⌄ 下面还有 ${rows.drop(first + visible).count { it.focusable }} 项", Modifier.padding(start = 26.dp, top = 12.dp), style = Guga.song(22.sp, color = colors.faint), maxLines = 1)
                }
            }
        }
        // ---- 选择器：右边页边（纸面）----
        if (picked != null) {
            Column(
                Modifier.align(Alignment.TopEnd).width(1920.dp - TvDetailSpec.SIDE_LEFT).fillMaxHeight().background(Color(0xFF14120E))
                    .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                    .padding(start = 48.dp, end = TvSpec.RIGHT, top = 150.dp).testTag("tv-settings-picker"),
            ) {
                TvSecHead(picked.label, picked.english)
                val keys = picked.options.indices.map { "pick-$it" }
                // 焦点先落在选中的那一项；锁着的（「本台」）跳过
                val firstPick = picked.selected.filter { !picked.options[it].locked }.minOrNull() ?: picked.options.indices.firstOrNull { !picked.options[it].locked } ?: 0
                TvGroup("picker", keys, horizontal = false, default = "pick-$firstPick")
                val at = keys.indexOf(groups.lastOf("picker") ?: "pick-$firstPick").coerceAtLeast(0)
                // 字幕样式的几行：焦点停在哪一项，预览条就按那一项排（「示例字幕 Sample」）；有预览时少排几项
                val preview = picked.preview?.invoke(at)
                if (preview != null) {
                    com.gugaart.ui.screens.settings.SubtitlePreview(
                        preview, com.gugaart.ui.player.rememberChoiceFamily(preview.font), Modifier.padding(top = 8.dp, bottom = 18.dp),
                        height = com.gugaart.ui.theme.SubtitlePreviewSpec.TV_HEIGHT, text = com.gugaart.ui.theme.SubtitlePreviewSpec.TV_TEXT,
                    )
                }
                val visible = if (preview != null) PICKS_VISIBLE_WITH_PREVIEW else PICKS_VISIBLE
                val first = windowStart(at, keys.size, visible)
                for (i in first until minOf(keys.size, first + visible)) key(keys[i]) {
                    val o = picked.options[i]
                    TvOption(keys[i], o.label, o.note.orEmpty(), i in picked.selected, dim = o.locked, tags = o.tag?.let { t -> { focused -> com.gugaart.ui.components.OutlineTag(t, 18.sp, 2.dp, if (focused) colors.onInk else colors.dim, Modifier.padding(start = 12.dp)) } }) {
                        picked.pick(i)
                        if (!picked.multi) closePicker()
                    }
                }
            }
        } else if (panel != null) {
            // ---- 开源许可：右边页边（纸面）的一列，只读；上下翻看，返回 / 左收起 ----
            TvLicensesPanel(Modifier.align(Alignment.TopEnd), groups)
        } else if (ordering != null) {
            // ---- 排顺序：右边页边（纸面）。确定拿起 → 上下移动（挪的是这一项）→ 确定放下；返回 = 放回原处 / 收起 ----
            Column(
                Modifier.align(Alignment.TopEnd).width(1920.dp - TvDetailSpec.SIDE_LEFT).fillMaxHeight().background(Color(0xFF14120E))
                    .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                    .padding(start = 48.dp, end = TvSpec.RIGHT, top = 150.dp).testTag("tv-settings-order"),
            ) {
                TvSecHead(ordering.label, ordering.english)
                val names = ordering.items.toMap()
                val reset = ordering.reset
                TvGroup("picker", order.map { "ord-$it" } + listOfNotNull("ord-reset".takeIf { reset != null }), horizontal = false)
                for ((i, id) in order.withIndex()) key(id) {
                    TvOrderRow("ord-$id", i + 1, names[id] ?: id, held == id) {
                        if (held == id) {
                            held = null
                            ordering.write(order)
                        } else held = id
                    }
                }
                if (reset != null) TvOption("ord-reset", "恢复默认顺序", "", false) {
                    held = null
                    reset()
                }
                // 电视不显示按键提示（不再写「确定 = 拿起 / 放下」）：拿起的那一行自己会变样（TvOrderRow 的 held）
            }
        } else groups.remove("picker")
    }
}

/** 排顺序的一行：斜体编号 + 名字；拿起来的那一项 = 左边一道朱红 + 「↕ 已拿起」（焦点在它身上，米白实底）。 */
@Composable
private fun TvOrderRow(key: String, number: Int, label: String, held: Boolean, onOk: () -> Unit) {
    val colors = Guga.colors
    TvBlockFocus(
        key, onOk,
        Modifier.fillMaxWidth().drawBehind {
            drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            if (held) drawRect(colors.seal, Offset(-14.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(8.dp.toPx(), size.height))
        }.testTag(if (held) "tv-order-row-held" else "tv-order-row"),
        label = label,
        kind = com.gugaart.ui.tv.TvFocusKind.Row,
    ) { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        Row(Modifier.fillMaxWidth().height(74.dp).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(if (number < 10) "0$number" else "$number", Modifier.width(60.dp), style = Guga.numeral(28.sp, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot), maxLines = 1)
            BasicText(label, Modifier.weight(1f), style = Guga.song(29.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (held) BasicText("↕ 已拿起", style = Guga.song(23.sp, FontWeight.Bold, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.seal), maxLines = 1)
        }
    }
}

/** 设置的一行（设计稿 `.li.set`）：左 名字 + 一句说明，右 现在的值（开关画成小方块滑到哪边）。只读的行不接焦点。 */
@Composable
private fun TvSettingLine(r: TvSetting, confirming: Boolean, open: Boolean, onOk: () -> Unit) {
    val colors = Guga.colors
    val body: @Composable (Boolean) -> Unit = { focused ->
        val ink = if (focused) colors.onInk else colors.ink
        val soft = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.faint
        Row(Modifier.fillMaxWidth().heightIn(min = 98.dp).padding(horizontal = 26.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(r.label, style = Guga.song(29.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val note = if (confirming && r is TvAction) r.confirm else r.note
                if (!note.isNullOrEmpty()) BasicText(note, style = Guga.song(22.sp, color = if (confirming) (if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.seal) else soft), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(30.dp))
            when (r) {
                is TvToggle -> Box(Modifier.width(58.dp).height(30.dp).border(2.dp, ink).testTag(if (r.on) "tv-switch-on" else "tv-switch-off")) {
                    Box(Modifier.align(if (r.on) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 3.dp).size(20.dp).background(ink.copy(alpha = if (r.on) 1f else 0.4f)))
                }
                is TvChoice -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(r.value, style = Guga.song(26.sp, color = if (focused) colors.onInk else colors.dim), maxLines = 1)
                    Icon(if (open) GuguIcon.ChevronRight else GuguIcon.ChevronDown, ink.copy(alpha = 0.6f), 26.dp)
                }
                is TvAction -> BasicText(r.value, style = Guga.song(26.sp, FontWeight.Bold, if (focused) colors.onInk else colors.spot), maxLines = 1)
                is TvPanel -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText(r.value, style = Guga.song(26.sp, color = if (focused) colors.onInk else colors.dim), maxLines = 1)
                    Icon(if (open) GuguIcon.ChevronRight else GuguIcon.ChevronDown, ink.copy(alpha = 0.6f), 26.dp)
                }
                is TvOrder -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("${r.items.size} 项", style = Guga.song(26.sp, color = if (focused) colors.onInk else colors.dim), maxLines = 1)
                    Icon(if (open) GuguIcon.ChevronRight else GuguIcon.ChevronDown, ink.copy(alpha = 0.6f), 26.dp)
                }
                is TvText -> Row(Modifier.widthIn(max = 420.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 密钥在行里不露一个字；别的值照写（太长省略）
                    BasicText(if (r.secret) tvSecretSummary(r.value) else r.value.ifEmpty { "未设置" }, Modifier.weight(1f, fill = false), style = Guga.song(26.sp, color = if (focused) colors.onInk else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(GuguIcon.ChevronRight, ink.copy(alpha = 0.6f), 26.dp)
                }
                is TvInfo -> BasicText(r.value ?: "—", style = Guga.song(26.sp, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                else -> Unit
            }
        }
    }
    val line = Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
    if (r.focusable) TvBlockFocus("row-${r.id}", onOk, line, label = r.label) { body(it) } else Box(line) { body(false) }
}

/** 开源许可那一列里的一项：组标题（不接焦点）、组说明（不接焦点）或一个组件（接焦点：遥控器靠焦点往下翻）。 */
private sealed interface TvLicenseItem {
    val key: String
    data class Head(override val key: String, val title: String, val english: String) : TvLicenseItem
    data class Note(override val key: String, val text: String) : TvLicenseItem
    data class Line(override val key: String, val entry: com.gugaart.ui.screens.settings.LicenseEntry) : TvLicenseItem
}

/** 页边一列里同时排得下几项（组标题、说明也占一项）。 */
private const val LICENSES_VISIBLE = 6

/**
 * 开源许可（电视）：内容和手机 / 电脑同一份 [com.gugaart.ui.screens.settings.OpenSourceLicenses]，排成页边的一列——
 * 每个组件一行（左 名字和出处，右 许可证），组标题和组说明夹在中间；焦点在组件行上走，列表跟着焦点滑（和选择器同一个窗口算法）。
 */
@Composable
private fun TvLicensesPanel(modifier: Modifier, groups: TvGroups) {
    val colors = Guga.colors
    val (platform, list) = com.gugaart.ui.screens.settings.rememberLicenses()
    val items = remember(list) {
        buildList<TvLicenseItem> {
            list.forEachIndexed { gi, group ->
                add(TvLicenseItem.Head("lic-h$gi", group.title, group.english))
                group.note?.let { add(TvLicenseItem.Note("lic-n$gi", it)) }
                group.entries.forEachIndexed { i, e -> add(TvLicenseItem.Line("lic-$gi-$i", e)) }
            }
        }
    }
    val keys = items.filterIsInstance<TvLicenseItem.Line>().map { it.key }
    Column(
        modifier.width(1920.dp - TvDetailSpec.SIDE_LEFT).fillMaxHeight().background(Color(0xFF14120E))
            .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
            .padding(start = 48.dp, end = TvSpec.RIGHT, top = 150.dp).testTag("tv-settings-licenses"),
    ) {
        TvSecHead("开源许可", "LICENSES")
        TvGroup("picker", keys, horizontal = false, default = keys.firstOrNull())
        val focusKey = groups.lastOf("picker") ?: keys.firstOrNull()
        val at = items.indexOfFirst { it.key == focusKey }.coerceAtLeast(0)
        val next = items.indices.firstOrNull { it > at && items[it] is TvLicenseItem.Line } ?: at
        // 第一个组件行之前的组标题也要看得见：焦点在第一行时从头排
        val first = if (focusKey == keys.firstOrNull()) 0 else windowStart(at, items.size, LICENSES_VISIBLE, next)
        for (item in items.drop(first).take(LICENSES_VISIBLE)) key(item.key) {
            when (item) {
                // 组标题下面留出焦点校对角的位置（角伸出行外）
                is TvLicenseItem.Head -> Row(Modifier.fillMaxWidth().height(70.dp).padding(start = 22.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BasicText(item.title, style = Guga.song(26.sp, FontWeight.Black, colors.dim), maxLines = 1)
                    BasicText(item.english, style = Guga.numeral(19.sp, colors.spot), maxLines = 1)
                }
                is TvLicenseItem.Note -> BasicText(item.text, Modifier.padding(start = 22.dp, top = 8.dp, bottom = 6.dp), style = Guga.song(21.sp, color = colors.faint, lineHeight = 32.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                is TvLicenseItem.Line -> TvBlockFocus(
                    item.key, {},
                    Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) },
                    label = item.entry.name,
                    // 从路由直接进来（那一列一开始就推开着）时，进页面的默认焦点 = 第一个组件，不是左页的分区
                    default = item.key == keys.firstOrNull(),
                    kind = com.gugaart.ui.tv.TvFocusKind.Row,
                ) { focused ->
                    val ink = if (focused) colors.onInk else colors.ink
                    // 页边一列不宽：名字、许可证、出处上下三行（许可证不和名字抢一行的宽度，长的也看得全）
                    Column(Modifier.fillMaxWidth().heightIn(min = 84.dp).padding(horizontal = 22.dp, vertical = 10.dp)) {
                        BasicText(item.entry.name, style = Guga.song(27.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(item.entry.license, Modifier.padding(top = 2.dp), style = Guga.note(23.sp, if (focused) colors.onInk else colors.dim), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        item.entry.note?.let { BasicText(it, Modifier.padding(top = 2.dp), style = Guga.song(20.sp, color = if (focused) colors.onInk.copy(alpha = 0.7f) else colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        val below = items.drop(first + LICENSES_VISIBLE).count { it is TvLicenseItem.Line }
        (if (below > 0) "⌄ 下面还有 $below 项" else com.gugaart.ui.screens.settings.OpenSourceLicenses.footer(platform))?.let {
            BasicText(
                it,
                Modifier.padding(top = 24.dp),
                style = Guga.song(21.sp, color = colors.faint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
