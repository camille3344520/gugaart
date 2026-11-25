package com.gugaart.ui.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.SheetSpec
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.typingTarget
import com.gugaart.ui.data.GugaPrefs
import com.gugaart.ui.player.PlaySession
import com.gugaart.ui.screens.play
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.AppGraph
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyException
import com.gugugaga.core.describeEmbyError
import com.gugugaga.ds.focus.LocalBackDispatcher
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * 字幕搜索的那一栏（docs/subtitle-search.md §5）：和「弹幕来源」同一种做法——从左边滑出来的一张纸，画面在右边照常放；
 * 播放器外（详情页 / 条目菜单的入口）是同一张纸，盖在页面上（后面压一层墨）。电视的那一栏在 tv/player/TvSubtitleSearch.kt。
 *
 * [SubtitleSearchBoard]：开没开、给哪一集、从哪打开的；选中一条之后怎么落到播放器里（reload 选上、自动选字幕让路）。
 */
object SubtitleSearchBoard {
    /** 现在开着的那一次搜索（null = 没开）。 */
    var session: SubtitleSearchSession? by mutableStateOf(null)
        private set

    /** 从播放器打开的：那一次播放（选上字幕用它）；null = 播放器外。 */
    var play: PlaySession? by mutableStateOf(null)
        private set

    /** 播放器外打开、还在取这一集的信息：要显示的片名（null = 没在取）。 */
    var preparing: String? by mutableStateOf(null)
        private set

    /** 取信息失败的原因（栏里写一句）。 */
    var problem: String? by mutableStateOf(null)
        private set

    /** 这一集正在用搜来的字幕：自动选字幕（`PlaySession`）不再改它。 */
    var pinned: String? = null

    /** 播放器外选了「播放并加载」：开播这一集后自动加载的本机字幕（条目 id → 编号）。 */
    var pendingLocal: Pair<String, Int>? = null

    /** 电视的入口：开播这一集后打开搜索栏。 */
    var openOnPlay: String? = null

    /** 测试缝：换掉网络（离屏测试 / 单测不联网）。应用里 = Ktor。 */
    var netFactory: () -> SubtitleNet = { KtorSubtitleNet() }
        set(value) {
            field = value
            netCache = null
        }

    private var netCache: SubtitleNet? = null
    private fun net(): SubtitleNet = netCache ?: netFactory().also { netCache = it }

    /** 测试缝：直接给一个搜索（离屏截图）。 */
    fun show(s: SubtitleSearchSession, fromPlayer: PlaySession?) {
        session = s
        play = fromPlayer
        preparing = null
        problem = null
    }

    fun close() {
        session = null
        play = null
        preparing = null
        problem = null
    }

    private fun online(g: AppGraph): Boolean = GugaPrefs.parse(g.deps.local.get(com.gugaart.ui.data.GugaSettingsStore.KEY)).subtitleOnline

    /** 播放器里打开：这一集的信息控制器都有了（播放信息、strm 目标），马上开搜。 */
    fun openInPlayer(play: PlaySession, g: AppGraph) {
        val snap = play.controller.value
        val item = snap.item ?: return
        val client = g.session.session.value?.client
        val r = snap.resolved
        val target = SubtitleTarget.of(item, r?.mediaSource ?: item.mediaSources.firstOrNull(), client, r?.strmTarget, r?.remoteHeaders)
        val s = SubtitleSearchSession(target, client, net(), online(g))
        show(s, play)
        g.scope.launch { s.run() }
    }

    /** 播放器外打开（条目菜单）：先取这一集的片源，再开搜。 */
    fun openForItem(itemId: String, name: String, g: AppGraph) {
        close()
        preparing = name
        g.scope.launch {
            val client = g.session.session.value?.client
            if (client == null) {
                problem = "没有登录"
                return@launch
            }
            try {
                val item = client.getItem(itemId)
                val s = SubtitleSearchSession(SubtitleTarget.of(item, item.mediaSources.firstOrNull(), client), client, net(), online(g))
                if (preparing != name) return@launch
                show(s, null)
                s.run()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = "没取到这一集的信息：" + describeEmbyError(e)
            }
        }
    }

    /**
     * 选中 [hit]。服务器源：让 Emby 下载、存进 Emby，播放器里就重新协商并选上它；迅雷 / 射手：下载到本机，播放器里加成外挂轨并选上
     * （播放器外 = 开播这一集再加载）。结果写在 [SubtitleSearchSession.apply] 里。
     * [scope]：界面上点的传那一栏自己的（界面线程：选上字幕要调控制器）；栏收起 = 不再往下做。
     */
    fun choose(hit: SubtitleHit, g: AppGraph, scope: kotlinx.coroutines.CoroutineScope = g.scope) {
        val s = session ?: return
        if (s.apply is ApplyState.Working) return
        val player = play
        scope.launch {
            try {
                if (hit.toEmby) {
                    val idx = s.downloadToEmby(hit)
                    s.apply = when {
                        idx != null && player != null -> {
                            select(player, s.target.itemId, idx, "选上搜到的字幕")
                            ApplyState.Done(hit.key, "已存进 Emby，已选上")
                        }
                        idx != null -> ApplyState.Done(hit.key, "已存进 Emby，下次播放可以直接选")
                        else -> ApplyState.Done(hit.key, "服务器收下了，还在处理；稍后在字幕里就能看到")
                    }
                    if (idx != null && player != null) close()
                } else {
                    val (text, format) = s.fetchText(hit)
                    val sub = LocalSubtitles.add(s.target.itemId, text, format, localLabel(hit), if (hit.lang == SubLang.English) "eng" else "chi")
                    if (LocalSubtitles.urlOf(sub) == null) {
                        s.apply = ApplyState.Failed(hit.key, "本机放不了这条字幕（回环服务起不来）")
                        return@launch
                    }
                    if (player != null) {
                        select(player, s.target.itemId, sub.index, "加载搜到的字幕")
                        s.apply = ApplyState.Done(hit.key, "已加载（仅本次播放）")
                        close()
                    } else {
                        pendingLocal = s.target.itemId to sub.index
                        s.apply = ApplyState.Done(hit.key, "开播后加载（仅本次播放）")
                        close()
                        g.play(s.target.itemId)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: EmbyException) {
                s.apply = ApplyState.Failed(hit.key, if (e.status == 401 || e.status == 403) "服务器不让这个账号下载字幕" else "没存进去：" + describeEmbyError(e))
            } catch (e: SubtitleFetchException) {
                s.apply = ApplyState.Failed(hit.key, e.message ?: "下载失败")
            } catch (e: Exception) {
                s.apply = ApplyState.Failed(hit.key, "出错了：" + (e.message ?: e::class.simpleName.orEmpty()))
            }
        }
    }

    /** 播放器里选上第 [index] 条：重新协商（服务器的新流表 / 本机字幕都在里面），这一集的自动选字幕让路。 */
    internal fun select(play: PlaySession, itemId: String, index: Int, why: String) {
        pinned = itemId
        play.subtitleWhy = "搜索"
        play.controller.reload(PlayController.ReloadPatch(subtitle = PlayController.Box(index)), why)
    }

    /** 播放器那边：开播了、这一集有「开播后加载」的本机字幕 → 选上它。返回做了没有。 */
    fun applyPending(play: PlaySession, itemId: String, phase: PlayPhase): Boolean {
        val p = pendingLocal ?: return false
        if (p.first != itemId || phase != PlayPhase.Ready) return false
        pendingLocal = null
        if (LocalSubtitles.find(p.second) == null) return false
        select(play, itemId, p.second, "加载搜到的字幕")
        return true
    }

    /** 本机字幕在字幕面板里叫什么。 */
    fun localLabel(hit: SubtitleHit): String = "搜到的字幕 · ${hit.lang.short} · ${hit.source.label} · 仅本次"
}

/** 一路源的状态行。纯函数，有单测。 */
fun sourceLine(label: String, s: SourceState): String = when (s) {
    SourceState.Idle -> "$label · —"
    SourceState.Busy -> "$label · 正在搜…"
    is SourceState.Done -> if (s.count == 0) "$label · 没有" else "$label · ${s.count} 条"
    is SourceState.Off -> "$label：${s.why}"
    is SourceState.Failed -> "$label：${s.why}"
}

/** 手机 / 电脑的状态：三路一段，「；」隔开；指纹取到了只写「已取指纹」。纯函数，有单测。 */
fun statusLine(s: SubtitleSearchSession): String = listOfNotNull(
    sourceLine("服务器", s.server),
    sourceLine("迅雷", s.thunder),
    sourceLine("射手", s.shooter),
    (if (s.print is PrintState.Got) "已取文件指纹" else printLine(s.print)).takeIf { s.online },
).joinToString("；")

/** 指纹那一行。 */
fun printLine(p: PrintState): String? = when (p) {
    PrintState.Idle -> null
    PrintState.Busy -> "正在取文件指纹（只读几段，约 64 KB）…"
    is PrintState.Got -> "文件指纹已取（${listOfNotNull("迅雷".takeIf { p.prints.thunderCid != null }, "射手".takeIf { p.prints.shooterHash != null }).joinToString(" / ")}）"
    is PrintState.Missing -> p.why
}

/** 在线源的隐私说明（栏底的小字）。 */
const val SUBTITLE_PRIVACY_NOTE = "迅雷 / 射手：会发送文件名和文件指纹（几段内容的哈希），不发服务器地址和登录信息。可在 设置 › 字幕与音轨 关闭。"

/** 这一行右端的去向印记。 */
fun destinationOf(hit: SubtitleHit, inPlayer: Boolean): String = when {
    hit.toEmby -> "进 Emby"
    inPlayer -> "仅本次"
    else -> "播放并加载"
}

/** 手机 / 电脑：播放器里那一栏（[width] 宽、整高，从左边出来；画面在右边照常放）。 */
@Composable
fun BoxScope.SubtitleSearchHost(play: PlaySession, width: Dp) {
    val snap by play.controller.snapshot.collectAsState()
    // 播放器外选了「播放并加载」：开播后加载
    androidx.compose.runtime.LaunchedEffect(play, snap.itemId, snap.phase) { SubtitleSearchBoard.applyPending(play, snap.itemId, snap.phase) }
    DisposableEffect(play) { onDispose { if (SubtitleSearchBoard.play === play) SubtitleSearchBoard.close() } }
    val s = SubtitleSearchBoard.session ?: return
    if (SubtitleSearchBoard.play !== play) return
    SubtitleSearchDrawer(s, width, inPlayer = true, onClose = { SubtitleSearchBoard.close() })
}

/** 手机 / 电脑：播放器外的那一栏（条目菜单打开的），盖在页面上，后面压一层墨。根上画一次。 */
@Composable
fun BoxScope.SubtitleSearchPageHost() {
    val s = SubtitleSearchBoard.session
    val preparing = SubtitleSearchBoard.preparing
    val problem = SubtitleSearchBoard.problem
    if (SubtitleSearchBoard.play != null) return
    if (s == null && preparing == null) return
    val width = if (Guga.uiMode == com.gugugaga.ds.theme.UiMode.Desktop) Guga.rem(48f) else 360.dp
    Box(
        Modifier.matchParentSize().background(Guga.colors.paper.copy(alpha = SheetSpec.SCRIM))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .testTag("sub-search-scrim"),
    )
    if (s != null) SubtitleSearchDrawer(s, width, inPlayer = false, onClose = { SubtitleSearchBoard.close() })
    else SubtitleSearchPreparing(preparing.orEmpty(), problem, width) { SubtitleSearchBoard.close() }
}

@Composable
private fun BoxScope.SubtitleSearchPreparing(name: String, problem: String?, width: Dp, onClose: () -> Unit) {
    val colors = Guga.colors
    BackHandlerOf(onClose)
    Box(Modifier.align(Alignment.CenterStart).width(width).fillMaxHeight().background(SheetSpec.SLIP.copy(alpha = 0.97f)).border(1.dp, colors.line).testTag("sub-search")) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            DrawerHead(onClose)
            BasicText(name, Modifier.padding(top = 12.dp), style = Guga.song(14.sp, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(problem ?: "正在取这一集的信息…", Modifier.padding(top = 8.dp), style = Guga.song(13.sp, color = if (problem != null) colors.seal else colors.dim), maxLines = 3)
        }
    }
}

@Composable
private fun BackHandlerOf(onClose: () -> Unit) {
    val back = LocalBackDispatcher.current
    val close = rememberUpdatedState(onClose)
    DisposableEffect(back) {
        val un = back.push {
            close.value()
            true
        }
        onDispose { un() }
    }
}

@Composable
private fun DrawerHead(onClose: () -> Unit) {
    val colors = Guga.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicText("搜索字幕", style = Guga.song(22.sp, FontWeight.Black, letterSpacing = 0.1.em), maxLines = 1)
        BasicText("SUBTITLES", Modifier.padding(start = 10.dp), style = Guga.note(13.sp, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        Spacer(Modifier.weight(1f))
        TextLink("收起", onClose, Modifier.testTag("sub-search-close"), color = colors.dim)
    }
    Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(2.dp).background(colors.ink))
}

@Composable
fun BoxScope.SubtitleSearchDrawer(s: SubtitleSearchSession, width: Dp, inPlayer: Boolean, onClose: () -> Unit) {
    val g = LocalGraph.current
    val colors = Guga.colors
    val scope = rememberCoroutineScope()
    BackHandlerOf(onClose)
    Box(
        Modifier.align(Alignment.CenterStart).width(width).fillMaxHeight()
            .background(SheetSpec.SLIP.copy(alpha = 0.97f))
            .border(1.dp, colors.line)
            // 纸上空白处的点击不漏到后面的画面（行、输入框、滚动先拿到事件，这里只收它们不要的）
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
            .testTag("sub-search"),
    ) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp)) {
            DrawerHead(onClose)
            BasicText(s.target.title, Modifier.padding(top = 12.dp), style = Guga.song(14.sp, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            BasicText(s.target.fileName, Modifier.padding(top = 2.dp), style = Guga.song(12.sp, color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 语言：中文 / 英文
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("语言", style = Guga.song(13.sp, color = colors.dim), maxLines = 1)
                for (l in SearchLang.entries) {
                    val on = s.lang == l
                    val press = rememberPressState()
                    BasicText(
                        l.label,
                        Modifier.pressGraphics(press).pressable(press, label = "字幕语言 ${l.label}", onClick = {
                            if (!on && !s.searching) {
                                s.lang = l
                                scope.launch { s.run() }
                            }
                        }).border(1.dp, if (on) colors.spot else colors.line).padding(horizontal = 10.dp, vertical = 3.dp).testTag("sub-lang-${l.emby}"),
                        style = Guga.song(13.sp, if (on) FontWeight.Bold else FontWeight.Normal, if (on) colors.spot else colors.dim),
                        maxLines = 1,
                    )
                }
            }
            // 状态：每路一行 + 指纹
            // 横屏手机只有 412 高：三路并成一段（结果尽量多露几条）；指纹只在没取到 / 正在取时写
            BasicText(
                statusLine(s),
                Modifier.padding(top = 10.dp).testTag("sub-status"),
                style = Guga.song(12.sp, color = colors.dim, lineHeight = 18.sp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            // 结果
            Column(Modifier.padding(top = 10.dp).testTag("sub-results")) {
                if (s.hits.isEmpty() && !s.searching) BasicText("没有搜到。可以换个名字搜，或者换成英文。", Modifier.padding(vertical = 10.dp), style = Guga.song(13.sp, color = colors.dim), maxLines = 2)
                s.hits.take(30).forEachIndexed { i, h -> HitRow(h, i, s.apply, inPlayer) { SubtitleSearchBoard.choose(h, g, scope) } }
            }
            if (s.online) {
                // 手动：换个名字搜（只重搜迅雷；服务器和射手不吃自由文本）
                BasicText("换个名字搜", Modifier.padding(top = 18.dp), style = Guga.song(13.sp, FontWeight.Bold, colors.spot, letterSpacing = 0.2.em), maxLines = 1)
                Row(Modifier.padding(top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).heightIn(min = 44.dp).border(1.dp, colors.line).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (s.query.isEmpty()) BasicText("片名或文件名", style = Guga.song(15.sp, color = colors.faint), maxLines = 1)
                        BasicTextField(
                            s.query,
                            { s.query = it },
                            Modifier.fillMaxWidth().testTag("sub-query").typingTarget(),
                            textStyle = Guga.song(15.sp, color = colors.ink),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.spot),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { scope.launch { s.runName() } }),
                        )
                    }
                    PrimaryButton("搜索", { scope.launch { s.runName() } }, Modifier.padding(start = 10.dp).testTag("sub-query-go"), icon = null, enabled = s.query.isNotBlank() && s.thunder != SourceState.Busy)
                }
                BasicText(SUBTITLE_PRIVACY_NOTE, Modifier.padding(top = 14.dp).testTag("sub-privacy"), style = Guga.song(11.sp, color = colors.faint, lineHeight = 17.sp), maxLines = 4)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 一条结果：名称 + 小字（简 · SRT · 迅雷 · 文件名）+ 右端去向印记；选中以后小字换成进度 / 结果。 */
@Composable
private fun HitRow(h: SubtitleHit, index: Int, apply: ApplyState?, inPlayer: Boolean, onPick: () -> Unit) {
    val colors = Guga.colors
    val press = rememberPressState()
    val mine = when (apply) {
        is ApplyState.Working -> apply.key == h.key
        is ApplyState.Done -> apply.key == h.key
        is ApplyState.Failed -> apply.key == h.key
        null -> false
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).pressGraphics(press).pressable(press, label = "字幕 ${h.name}", onClick = onPick)
            .then(if (mine) Modifier.background(colors.spot.copy(alpha = 0.14f)) else Modifier)
            .padding(vertical = 8.dp, horizontal = 6.dp).testTag("sub-hit-$index"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(h.name, style = Guga.song(14.sp, FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            val line = if (mine) when (apply) {
                is ApplyState.Working -> apply.step
                is ApplyState.Done -> apply.note
                is ApplyState.Failed -> apply.why
                null -> h.meta()
            } else h.meta()
            val tint = if (mine && apply is ApplyState.Failed) colors.seal else if (mine) colors.spot else colors.dim
            BasicText(line, Modifier.padding(top = 2.dp), style = Guga.song(12.sp, color = tint), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val dest = destinationOf(h, inPlayer)
        BasicText(
            dest,
            Modifier.padding(start = 8.dp).border(1.dp, if (h.toEmby) colors.spot else colors.line).padding(horizontal = 6.dp, vertical = 2.dp),
            style = Guga.song(11.sp, FontWeight.Bold, if (h.toEmby) colors.spot else colors.dim),
            maxLines = 1,
        )
    }
}
