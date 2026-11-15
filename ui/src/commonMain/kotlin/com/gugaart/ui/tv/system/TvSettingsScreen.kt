package com.gugaart.ui.tv.system

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
    val status by g.update.status.collectAsState()

    var current by remember { mutableStateOf(GugaSection.of(section) ?: GugaSection.Look) }
    var picker by remember { mutableStateOf(preset.state?.takeIf { it.startsWith("pick:") }?.removePrefix("pick:")) }
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
    val rows = remember(current, settings, prefs, status, secrets, dmServers, backup.webdav, backup.pending, backup.message, backup.busy, traktState, traktPrefs, traktSync, traktMessage, traktCalendar.second) {
        tvSettingRows(
            current, settings, prefs, g, store, secrets,
            saveSecret = { tmdb, dm -> g.scope.launch { g.secrets.save(tmdbToken = tmdb, danmakuApiUrl = dm) } },
            danmakuServers = dmServers?.map { it.url },
            backup = backup,
            trakt = trakt,
            saveDanmakuServers = { urls ->
                g.scope.launch {
                    com.gugaart.ui.danmaku.DanmakuServers.save(urls)
                    g.secrets.load()
                }
            },
        )
    }
    val focusRows = rows.filter { it.focusable }
    val picked = rows.firstOrNull { it.id == picker } as? TvChoice

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
    val cur = groups.current
    LaunchedEffect(cur) {
        if (cur?.startsWith("sec-") == true) GugaSection.entries.firstOrNull { "sec-${it.key}" == cur }?.let { if (it != current) { current = it; groups.forget("rows"); confirming = null } }
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
                        for (line in listOfNotNull(edit.note, "${draft.length} 个字符" + if (draft.length > 22) "（只显示末尾 22 个）" else "", "按「保存」写回；按返回键不改。")) {
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
                // ---- 左页：九个分区 ----
                TvGroup("sections", GugaSection.entries.map { "sec-${it.key}" }, horizontal = false, default = "sec-${current.key}")
                Column(Modifier.width(440.dp)) {
                    for (s in GugaSection.entries) {
                        val on = s == current
                        TvBlockFocus("sec-${s.key}", { groups.enter("rows", g.focus) }, Modifier.fillMaxWidth().drawBehind { drawLine(colors.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }, label = s.title, default = on) { focused ->
                            val ink = if (focused) colors.onInk else if (on) colors.spot else colors.ink
                            Row(Modifier.fillMaxWidth().height(88.dp).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                                BasicText(s.number, Modifier.width(64.dp), style = Guga.numeral(25.sp, if (focused) com.gugaart.ui.theme.GugaColors.SEAL_ON_INK else colors.spot), maxLines = 1)
                                BasicText(s.title, Modifier.weight(1f), style = Guga.song(30.sp, FontWeight.Bold, ink), maxLines = 1)
                                Icon(GuguIcon.ChevronRight, ink.copy(alpha = if (on) 1f else 0.5f), 26.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(84.dp))
                // ---- 右页：这一分区的行 ----
                Column(Modifier.weight(1f).padding(end = if (picked != null || ordering != null) 604.dp else 0.dp)) {
                    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        BasicText(current.number, style = Guga.numeral(36.sp, colors.spot), maxLines = 1)
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
                    if (first + visible < rows.size) BasicText("⌄ 下面还有 ${rows.drop(first + visible).count { it.focusable }} 项", Modifier.padding(start = 26.dp, top = 12.dp), style = Guga.song(22.sp, color = colors.faint), maxLines = 1)
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
                TvGroup("picker", keys, horizontal = false, default = "pick-${picked.selected.minOrNull() ?: 0}")
                val at = keys.indexOf(groups.lastOf("picker") ?: "pick-${picked.selected.minOrNull() ?: 0}").coerceAtLeast(0)
                val first = windowStart(at, keys.size, PICKS_VISIBLE)
                for (i in first until minOf(keys.size, first + PICKS_VISIBLE)) key(keys[i]) {
                    val o = picked.options[i]
                    TvOption(keys[i], o.label, o.note.orEmpty(), i in picked.selected) {
                        picked.pick(i)
                        if (!picked.multi) closePicker()
                    }
                }
                BasicText(if (picked.multi) "确定 = 勾选 / 去掉；返回收起。" else "确定选中并收起；返回不改。", Modifier.padding(top = 30.dp), style = Guga.song(21.sp, color = colors.faint), maxLines = 1)
            }
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
                BasicText(
                    if (held != null) "上下移动；确定放下，返回 = 放回原处。" else "确定拿起一项，上下移动，再按确定放下；返回收起。",
                    Modifier.padding(top = 30.dp).testTag(if (held != null) "tv-order-held" else "tv-order-idle"),
                    style = Guga.song(21.sp, color = if (held != null) colors.seal else colors.faint),
                    maxLines = 1,
                )
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
