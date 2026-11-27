package com.gugaart.desktop.mac.accept

import com.gugaart.desktop.mac.SystemNowPlayingCenter
import com.gugaart.ui.update.updater
import com.gugaart.ui.data.Pinyin
import com.gugaart.ui.servers.key
import com.gugugaga.app.nav.Route
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.LibraryQuery
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

/**
 * 场景。每个场景写明对应 `docs/feature-parity-macos-ios.md` 的哪几行。规矩：
 *  - 对用户的服务器只读：不点收藏 / 标已看 / 删除 / 管理，不按 `V` `W`（它们写服务器）；要写的检查记 skip。播放允许（会上报进度）。
 *  - 不输入任何账号密码；输入框里打的是 [PROBE_TEXT] 这种明摆着的测试串，自动保存的设置打完就还原（并核对还原了）。
 *  - 密钥类的框（弹幕服务器地址、TMDB 令牌、WebDAV 密码）只记长度变化，不记内容；不点「查看」。
 *  - 外部播放器（SenPlayer / IINA）的入口一律不点。
 */
internal object AcceptScenarios {
    /** 输入框里打的：一个带 `/` `:` `?` `=` `.` 的地址 + 三个字母（j f k 在播放器里是快进 / 全屏 / 暂停——漏出去了一看就知道，也不写服务器）。 */
    const val PROBE_TEXT = "http://a.b/c?d=1jfk"

    val NAMES = listOf("window", "input", "keys", "browse", "play", "nowplaying", "menu", "dock", "keychain", "pip", "error-nolib", "subs-first", "sub-switch", "shader", "subtitle-search", "search141", "sub-style", "specials141", "servers150", "sources150", "switchback150", "serversettings150", "remote150", "season151", "dmjit") // DMJIT-TEMP

    fun all(host: AcceptHost): List<Scenario> = listOf(
        Scenario("dmjit", "danmaku jitter probe (temp)", emptyList(), 900) { DmJitterProbe.run(it) }, // DMJIT-TEMP
        Scenario("window", "窗口外观：红绿灯、报头让位、深色、失焦变淡、全屏、改尺寸", listOf("X01", "X08", "B22", "P20", "G03"), 180) { window(it) },
        Scenario("input", "输入框：聚焦后打字进框、不触发快捷键；未聚焦时 / 跳搜索", listOf("S05", "S01", "C01", "D04", "D07", "T08", "P25", "D03"), 420) { input(it) },
        Scenario("keys", "快捷键：/ ⌘K ⌘, F5 [ ] ← → ⌘F Esc Enter，⌘ 提示与菜单栏", listOf("X04", "B05", "B13", "B29", "S01", "T01", "B10", "B01", "M05"), 300) { keys(it) },
        Scenario("browse", "浏览：五个导航页、片库海报墙、进详情再返回回原位、搜索（拼音首字母、演员）", listOf("B25", "B09", "B10", "B07", "B19", "M01", "S01", "S04", "B16"), 300) { browse(it) },
        Scenario("play", "播放：续看出首帧、画面在窗口里、控件与键位、画面让位、全屏在刘海下、换集、返回详情、进出 10 次内存、开播格式提示", listOf("P01", "P03", "P04", "P05", "P07", "P10", "P12", "P13", "P19", "P20", "P24", "X01", "G01", "G05"), 1_200) { play(it) },
        Scenario("nowplaying", "「正在播放」：信息齐全、命令控制 mpv、离开播放器后清空", listOf("X11"), 240) { nowPlaying(it) },
        Scenario("menu", "菜单栏：七个菜单、播放组不播时整组灰、筛选只在海报墙、菜单命令真的换页/付印", listOf("X11", "X04", "B05", "B10"), 120) { menu(it) },
        Scenario("dock", "Dock 菜单：继续观看 ≤3 + 搜索 + 付印；点一条继续观看进播放器", listOf("X11", "P24"), 120) { dock(it) },
        Scenario("keychain", "令牌存储：= plain-fallback、登录信息在数据目录的文件里、全程没调钥匙串接口、登录态从文件读回来", listOf("C03", "T11"), 90) { keychain(it) },
        Scenario("pip", "画中画：按钮 → 1 秒内进小窗、30 秒帧间隔 / CPU、回到窗口画面回格位置不丢、声音输出没重开", listOf("P18"), 300) { pip(it) },
        Scenario("error-nolib", "出错：libmpv 指到不存在的路径时是出错页（不弹框）", listOf("P02", "P23"), 120) { errorNoLib(it) },
        Scenario("subs-first", "1.3.1 字幕：带内嵌字幕的片第一次开播内核就选中字幕轨（不用关一次再开）", listOf("P06"), 180) { subsFirst(it) },
        Scenario("sub-switch", "切字幕传到内核（dc934d0a）：有内嵌轨时选外挂 SRT 仍 sid≠no；外挂→内嵌→关", listOf("P06"), 180) { subSwitch(it) },
        Scenario("shader", "1.3.1 画质增强：Anime4K / FSR 单选、下发 glsl-shaders、关恢复、重开沿用", listOf("P07"), 300) { shader(it) },
        Scenario("subtitle-search", "1.4.0 字幕搜索：字幕面板「搜索字幕…」→ 三路结果 → 选服务器源进 Emby 并选上；选迅雷 / 射手的仅本次加载（只在 Mock 上跑）", listOf("P06"), 420) { subtitleSearch(it) },
        Scenario("search141", "1.4.1 自写搜索：缩写（复联）/ 人名后两字（云熙）/ 剧名 + 集号（颜心记 5）/ 原名命中（iron man）/ 排序", listOf("S01", "S04"), 300) { search141(it) },
        Scenario("sub-style", "1.4.1 字幕样式：150% + 粗体 + 文楷 + 粗描边 + 抬高；也改 ASS 开 / 关；站酷快乐体第一次下载（只在 Mock 上跑）", listOf("P06", "T05"), 420) { subStyle141(it) },
        Scenario("specials141", "1.4.1 重画的 10 张特别称号章：「我的」页的特别称号一栏（小尺寸不糊）", listOf("M03"), 180) { specials141(it) },
        Scenario("servers150", "1.5.0 换服务器：三台 Mock（一台停掉）——顶栏胶囊下拉来回切、点离线的写原因、⌘⇧S + 数字、菜单栏「片库 › 换服务器」（只在 Mock 上跑）", listOf("A2"), 600) { servers150(it) },
        Scenario("sources150", "1.5.0 详情页「源」：990201 本台 4K（推荐）+1080p（另一份文件）+别台行（服务器名+延迟）；选 1080p 放 990202、选别台放那台（查 Mock sessions；要 --mock-servers 预存三台）", listOf("A2"), 600) { sources150(it) },
        Scenario("switchback150", "1.5.0 切走再切回：首页第一帧就是上次的内容（不空骨架），记切回用时（要 --mock-servers 预存三台）", listOf("A2"), 300) { switchback150(it) },
        Scenario("serversettings150", "1.5.0 设置 › 播放 › 多台服务器：关「从其它服务器找片源」后详情页只剩本台的源（要 --mock-servers 预存三台）", listOf("A2"), 300) { serverSettings150(it) },
        Scenario("remote150", "1.5.0 播放器画质面板「版本」：换到别台从当前位置接着放；停掉那台再换 → 「换到「X」失败」4 秒后消失（要 --mock-servers 预存三台 + GUGAART_ACCEPT_STOP）", listOf("A2"), 600) { remote150(it) },
        Scenario("season151", "1.5.1 多季的剧默认搜这一季：头文字D 第 6 季第 3 集，弹幕来源框里是「头文字D 第六季」、这一季的候选在前、快捷词；字幕「换个名字搜」带季集（要 Mock 里有六季的头文字D + GUGAART_ACCEPT_DANMAKU 假弹幕服务器）", listOf("D04", "P06"), 420) { season151(it) },
    )

    // ================================================================== 1. 窗口外观

    private fun window(c: Ctx) {
        val d = c.d
        d.waitFor(20_000) { d.tree().has("masthead-volume") }
        Thread.sleep(1_500)
        val t = d.tree()
        val lights = d.trafficLights()
        val okLights = lights.size == 3 && lights.all { (exists, hidden, r) -> exists && !hidden && r != null && r.x < 100 && r.y < 40 }
        c.expect(
            "traffic-lights", "左上是系统的红绿灯：关闭 / 最小化 / 缩放三个钮都在、没藏、在左上角", okLights,
            values = mapOf("钮（点）" to lights.joinToString(" ; ") { (e, h, r) -> "在=$e 藏=$h ${r?.short()}" }),
        )
        c.expect("no-drawn-keys", "右上没有自绘的三个窗口键（window-keys 节点不存在）", !t.has("window-keys"))
        val vol = t.tag("masthead-volume")
        val zoomRight = lights.getOrNull(2)?.third?.right ?: 0.0
        c.expect(
            "vol-inset", "报头 VOL. 左边距 ≥ 85 pt，且在缩放钮右边（不被红绿灯压住）",
            vol != null && vol.bounds.x >= 84.5 && vol.bounds.x >= zoomRight + 4,
            values = mapOf("VOL. 左边" to vol?.bounds?.x, "缩放钮右边" to zoomRight, "VOL. 文字" to vol?.let { t.textUnder(it) }),
        )
        // G03：报头正中的徽记仍在窗口正中（文字让位只动左边那一段）
        val w = d.ui { d.window.width.toDouble() }
        val emblem = t.nodes.filter { it.bounds.w > 20 && it.bounds.y < 80 && it.desc.any { s -> s.contains("咕咕") && s.contains("嘎嘎") } }.minByOrNull { kotlin.math.abs(it.bounds.cx - w / 2) }
        if (emblem == null) c.skip("emblem-centered", "报头徽记在窗口正中（G03）", "语义树里找不到徽记节点（没有 testTag / 读屏说明）——看截图")
        else c.expect("emblem-centered", "报头徽记在窗口正中（G03）", kotlin.math.abs(emblem.bounds.cx - w / 2) <= 2.0, values = mapOf("徽记中线" to emblem.bounds.cx, "窗口中线" to w / 2))

        val appearance = runCatching { Objc.onMain { Objc.string(Objc.msg(Objc.msg(d.nsWindowPointer(), "effectiveAppearance")!!, "name")) } }.getOrNull()
        val active = d.shot("window-active", "窗口在前台")
        val img = active?.let(d::image)
        val k = img?.let(d::shotScale) ?: 2.0
        val band = img?.let { Pixels.meanLuma(it, R(0.0, 0.0, w, 28.0).scaled(k)) }
        val bandSd = img?.let { Pixels.lumaStdDev(it, R(0.0, 0.0, w, 28.0).scaled(k)) }
        c.pixelExpect(
            "titlebar-strip", "红绿灯那一条不是一条白边（内容铺到窗口顶上）（X08）",
            band != null && !(band > 200 && (bandSd ?: 0.0) < 8),
            hasPixels = band != null,
            values = mapOf("顶上 28 pt 平均亮度" to band?.let { "%.1f".format(it) }, "标准差" to bandSd?.let { "%.1f".format(it) }),
            shots = listOf(active),
        )
        c.expect(
            "dark-appearance", "窗口声明深色外观 DarkAqua：系统菜单、文件面板按深色画（X08 / M1-6）", appearance?.contains("Dark") == true,
            detail = if (appearance?.contains("Dark") == true) "" else "effectiveAppearance 是 $appearance：Mac 分支没设 apple.awt.application.appearance（任务单 M1-6 的 macSystemProperties() 在这条分支上不存在）",
            values = mapOf("effectiveAppearance" to appearance),
        )

        // 失焦变淡：VOL. 那一块最亮的字的亮度，前台 / 失焦 / 切回来三次读数
        val volRect = vol?.bounds?.let { R(it.x - 2, it.y - 2, it.w + 4, it.h + 4) }
        val b1 = if (img != null && volRect != null) Pixels.brightMean(img, volRect.scaled(k)) else null
        val how = d.deactivate()
        Thread.sleep(900)
        val focusedNow = d.ui { d.window.isFocused }
        val inactive = d.shot("window-inactive", "切到别的应用：报头变淡")
        val b2 = inactive?.let(d::image)?.let { if (volRect != null) Pixels.brightMean(it, volRect.scaled(k)) else null }
        c.pixelExpect(
            "inactive-dim", "失焦时报头的字变淡（像素读数：最亮那 10 % 的亮度降到前台的 85 % 以下）",
            !focusedNow && b1 != null && b2 != null && b2 < b1 * 0.85,
            hasPixels = b1 != null && b2 != null,
            values = mapOf("失焦办法" to how, "前台亮度" to b1?.let { "%.1f".format(it) }, "失焦亮度" to b2?.let { "%.1f".format(it) }, "比值" to if (b1 != null && b2 != null) "%.2f".format(b2 / b1) else null),
            shots = listOf(active, inactive),
        )
        val back = d.activate()
        Thread.sleep(900)
        val again = d.shot("window-reactivated", "切回来：报头恢复")
        val b3 = again?.let(d::image)?.let { if (volRect != null) Pixels.brightMean(it, volRect.scaled(k)) else null }
        c.pixelExpect(
            "refocus-restores", "切回来以后报头恢复原来的亮度（≥ 95 %）",
            d.ui { d.window.isFocused } && b1 != null && b3 != null && b3 >= b1 * 0.95,
            hasPixels = b1 != null && b3 != null,
            values = mapOf("复焦办法" to back, "前台亮度" to b1?.let { "%.1f".format(it) }, "切回来亮度" to b3?.let { "%.1f".format(it) }),
            shots = listOf(again),
        )

        // 系统全屏（JNA toggleFullScreen:）：窗口在刘海那一条下面
        val before = d.ui { d.window.bounds }
        d.toggleFullScreen()
        val entered = d.waitFor(8_000) { d.isFullScreen() }
        Thread.sleep(1_800)
        val (safeTop, screen) = d.screenSafeTop()
        val fb = d.ui { d.window.bounds }
        val fsShot = d.shot("window-fullscreen", "系统全屏（toggleFullScreen:）")
        val tf = d.tree()
        c.expect(
            "fullscreen-below-notch", "系统全屏：窗口铺满屏幕宽、顶边在刘海那一条之下（P20 / M2-6）",
            entered && fb.y >= safeTop - 1 && kotlin.math.abs(fb.width - screen.w) <= 1 && fb.height <= screen.h - safeTop + 1,
            values = mapOf("屏幕（点）" to screen.short(), "安全区顶边" to safeTop, "全屏窗口" to "${fb.x},${fb.y} ${fb.width}x${fb.height}", "VOL. 还在" to tf.has("masthead-volume"), "这块屏有刘海" to (safeTop > 0)),
            shots = listOf(fsShot),
        )
        d.toggleFullScreen()
        val exited = d.waitFor(8_000) { !d.isFullScreen() }
        Thread.sleep(1_500)
        val ab = d.ui { d.window.bounds }
        c.expect(
            "fullscreen-exit", "再切一次退出全屏，窗口回到原来的位置和大小", exited && kotlin.math.abs(ab.width - before.width) <= 2 && kotlin.math.abs(ab.height - before.height) <= 2,
            values = mapOf("全屏前" to "${before.x},${before.y} ${before.width}x${before.height}", "退出后" to "${ab.x},${ab.y} ${ab.width}x${ab.height}"),
        )

        // 应用自己切全屏（播放器的 F / 双击画面走的 WindowControls.setFullscreen；P20）：进出 5 次，退出后没有系统全屏的窗留着
        val wc = c.graph.deps.windowControls
        if (wc == null) c.check("fullscreen-app-5x", "应用自己切全屏进出 5 次（P20）", Verdict.Fail, "没有 WindowControls")
        else {
            val r = c.fullscreenCycles(5) { d.ui { wc.setFullscreen(!wc.fullscreen) } }
            c.expect(
                "fullscreen-app-5x", "应用自己切全屏（WindowControls，播放器的 F 走这一路）进出 5 次：每次都进得去、退得出来、回到原来的大小，结束后可见窗口里没有 NSToolbarFullScreenWindow（P20）",
                r.ok, values = mapOf("每一次" to r.rows.joinToString(" ; "), "可见窗口（结束时）" to r.windows.joinToString(" ")),
            )
        }

        // 改尺寸：最小 1100 × 720（B22）
        val min = runCatching { Objc.onMain { Objc.msgSize(d.nsWindowPointer(), "minSize") } }.getOrNull()
        d.ui { d.window.setSize(1000, 600) }
        d.settle(900)
        val small = d.ui { d.window.size }
        c.expect(
            "min-size", "窗口拖不到比 1100 × 720 更小（AppKit 的 minSize 和 AWT 的最小尺寸都是 1100 × 720）",
            small.width >= 1100 && small.height >= 720 && min != null && min.first >= 1099.5 && min.second >= 719.5,
            values = mapOf("请求 1000×600 得到" to "${small.width}x${small.height}", "NSWindow minSize" to min?.let { "%.0fx%.0f".format(it.first, it.second) }),
        )
        d.ui { d.window.setSize(1100, 720) }
        d.settle(1_500)
        val t11 = d.tree()
        val small11 = d.shot("window-1100x720", "最小窗口 1100 × 720")
        val vol11 = t11.tag("masthead-volume")
        c.expect(
            "min-size-layout", "1100 × 720 时报头仍让位（VOL. ≥ 85 pt）、没有自绘窗口键", vol11 != null && vol11.bounds.x >= 84.5 && !t11.has("window-keys"),
            values = mapOf("VOL. 左边" to vol11?.bounds?.x), shots = listOf(small11),
        )
        d.ui { d.window.bounds = before }
        d.settle(1_200)
    }

    // ================================================================== 2. 输入

    /** 一个输入框的测试：点进去（真的鼠标）、确认焦点在它上面、打 [PROBE_TEXT]、看字进了框、页面没跳。 */
    private fun field(c: Ctx, id: String, title: String, tag: String, secret: Boolean, rows: String, afterType: (() -> String?)? = null): Boolean {
        val d = c.d
        val node0 = scrollIntoView(c, tag)
        if (node0 == null) {
            c.skip(id, title, "这一页上没有这个输入框（$tag）", values = mapOf("对齐清单" to rows))
            return false
        }
        d.click(node0)
        d.settle(250)
        var t = d.tree()
        var node = t.tag(tag)
        // 焦点在这个框上（tag 在框本身，或者在包着它的那一格上）
        fun focusedField(tr: AcceptDriver.Tree): AcceptDriver.Node? {
            val n = tr.tag(tag) ?: return null
            if (n.focused) return n
            return tr.descendants(n).firstOrNull { it.focused }
        }
        var f = focusedField(t)
        if (f == null) {
            // 再点一下文字那一行的左边（有的框的点击区只有字那么宽）
            node?.let { d.click(it.bounds.x + 12, it.bounds.cy) }
            t = d.tree()
            node = t.tag(tag)
            f = focusedField(t)
        }
        val typing = d.ui { c.graph.isTyping }
        if (f == null) {
            // 焦点去哪了：再点一次，0.05 秒一读，读 1.5 秒（拿到了又被抢走 / 根本没拿到）
            val timeline = ArrayList<String>()
            node?.let { n ->
                d.click(n)
                val t0 = System.currentTimeMillis()
                var last = ""
                while (System.currentTimeMillis() - t0 < 1_500) {
                    val tr = d.tree()
                    val now = tr.focused()?.let { fo -> fo.tag ?: if (focusedField(tr) != null) tag else "#${fo.id}" } ?: "无"
                    if (now != last) { timeline += "${System.currentTimeMillis() - t0}ms:$now"; last = now }
                    Thread.sleep(50)
                }
            }
            // 区分：经语义动作要焦点，看它留不留得住（留得住 = 是点击没到；留不住 = 被别的节点抢走了）
            val asked = d.requestFocusBySemantics(tag)
            val held = ArrayList<String>()
            val t1 = System.currentTimeMillis()
            var lastHeld = ""
            while (System.currentTimeMillis() - t1 < 1_000) {
                val tr = d.tree()
                val now = tr.focused()?.let { fo -> fo.tag ?: if (focusedField(tr) != null) tag else "#${fo.id}" } ?: "无"
                if (now != lastHeld) { held += "${System.currentTimeMillis() - t1}ms:$now"; lastHeld = now }
                Thread.sleep(40)
            }
            timeline += "| 语义要焦点=$asked: ${held.joinToString(" → ")}"
            val failShot = d.shot("input-$id-nofocus", "$title：点了以后焦点不在框上")
            val stolen = held.size >= 2 && held.first().endsWith(":$tag")
            val why = if (stolen) "焦点拿到了又被抢走：经语义动作要到焦点后 ${held.last().substringBefore(':')} 内被「${held.last().substringAfter(':')}」拿走（点击本身也一样）" else "点了以后焦点不在这个框上"
            c.check(id, title, Verdict.Fail, why, shots = listOf(failShot), values = mapOf("焦点时间线（再点一次）" to timeline.joinToString(" → "), "对齐清单" to rows, "isTyping" to typing, "焦点在" to t.focused()?.let { it.tag ?: it.desc.firstOrNull() ?: "#${it.id} ${it.bounds.short()}" }, "框（点）" to node?.bounds?.short(), "窗口高" to d.ui { d.window.height }))
            return false
        }
        val before = f.editable.orEmpty()
        val path0 = c.path
        d.type(PROBE_TEXT)
        Thread.sleep(300)
        val t2 = d.tree()
        val f2 = t2.tag(tag)?.let { n -> if (n.editable != null) n else t2.descendants(n).firstOrNull { it.editable != null } }
        val after = f2?.editable.orEmpty()
        val path1 = c.path
        val extra = afterType?.invoke()
        val textOk = if (secret) after.length - before.length == PROBE_TEXT.length else after.contains(PROBE_TEXT)
        val ok = textOk && path1 == path0 && extra == null
        val values = linkedMapOf<String, Any?>(
            "对齐清单" to rows,
            "页面" to path0,
            "打完后的页面" to path1,
            "isTyping（聚焦时）" to typing,
        )
        if (secret) values["长度变化"] = "${before.length} → ${after.length}（打了 ${PROBE_TEXT.length} 个字；密钥框不记内容）"
        else values["框里的字"] = after.take(80)
        extra?.let { values["漏出去的快捷键"] = it }
        val shot = if (secret) null else d.shot("input-$id", title)
        c.check(id, title, if (ok) Verdict.Pass else Verdict.Fail, if (ok) "字进了框，页面没跳" else listOfNotNull("字没进框".takeIf { !textOk }, "页面跳了".takeIf { path1 != path0 }, extra?.let { "快捷键漏出去了：$it" }).joinToString("；"), values, listOf(shot))
        return ok
    }

    /** 把带 [tag] 的节点滚进视口（滚轮落在它那一栏上，按离视口多远给格数；滚不动了就停）；找不到 = null。 */
    private fun scrollIntoView(c: Ctx, tag: String, timeoutMs: Long = 8_000): AcceptDriver.Node? {
        val d = c.d
        if (!d.waitFor(timeoutMs, 150) { d.tree().has(tag) }) return null
        return scrollTo(c) { it.tag(tag) }
    }

    private fun scrollTo(c: Ctx, find: (AcceptDriver.Tree) -> AcceptDriver.Node?): AcceptDriver.Node? {
        val d = c.d
        val (w, h) = d.ui { d.window.width.toDouble() to d.window.height.toDouble() }
        var lastY: Double? = null
        var stuck = 0
        repeat(40) {
            val n = find(d.tree()) ?: return null
            if (n.bounds.cy in 170.0..(h - 110.0) && n.visible) return n
            if (lastY != null && kotlin.math.abs(n.bounds.y - lastY!!) < 0.5) { if (++stuck >= 3) return n } else stuck = 0
            lastY = n.bounds.y
            // 往哪边滚：在视口下面（或者在下半边、被容器裁掉了）就往下读
            val down = n.bounds.cy > h - 110.0 || (n.bounds.cy > h / 2 && !n.visible)
            val far = kotlin.math.abs(if (down) n.bounds.cy - (h - 110.0) else 170.0 - n.bounds.cy).coerceAtLeast(40.0)
            val notches = (far / 40.0).toInt().coerceIn(1, 12)
            d.wheel(n.bounds.cx.coerceIn(40.0, w - 40.0), h / 2, if (down) notches else -notches)
        }
        return find(d.tree())
    }

    private fun input(c: Ctx) {
        val d = c.d
        val g = c.graph
        // ---- 对照：没有焦点时「/」跳搜索
        d.waitFor(15_000) { d.tree().has("desktop-nav-row") }
        Thread.sleep(800)
        val focused0 = d.tree().focused()
        d.key(KeyEvent.VK_SLASH, '/')
        val jumped = d.waitFor(3_000) { c.route is Route.Search }
        c.expect(
            "slash-unfocused", "对照：没有输入框聚焦时按「/」跳到搜索（X04 / S01）", jumped,
            values = mapOf("按之前的焦点" to (focused0?.tag ?: focused0?.desc?.firstOrNull() ?: "无"), "按之后的页面" to c.path),
        )

        // ---- 搜索框（点搜索胶囊进去，框自己拿焦点）
        c.reset()
        // 先收掉称号 / 特别称号纸片：Mock 账号看过很多部，第一次进首页会弹纸片盖在页面上，
        // 纸片开着时 Esc 先被它吃掉，搜索页就退不出（search-esc 误判）。照 iOS runner 的 closeSlips。
        closeSlips(c)
        d.tree().tag("desktop-search")?.let { d.click(it) }
        if (c.waitTag("desktop-search-field", 6_000) != null) {
            field(c, "search-field", "搜索框：打地址和字母都进框（S05 / S01）", "desktop-search-field", secret = false, rows = "S05 S01")
            // 称号自动检查在登录后 ~4s 才异步弹纸片，可能正好落在打字之后；纸片开着会吃掉 Esc。按 Esc 前再收一次。
            closeSlips(c)
            d.key(KeyEvent.VK_ESCAPE)
            val cleared = d.waitFor(2_000) { d.tree().tag("desktop-search-field")?.editable.isNullOrEmpty() }
            closeSlips(c)
            d.key(KeyEvent.VK_ESCAPE)
            val left = d.waitFor(3_000) { c.route !is Route.Search }
            c.expect("search-esc", "搜索框里按 Esc 先清空，再按一次回上一页（B29）", cleared && left, values = mapOf("现在的页面" to c.path))
        } else c.check("search-field", "搜索框：打地址和字母都进框（S05 / S01）", Verdict.Fail, "点搜索胶囊以后没有出搜索框")

        // ---- 设置里的输入行。自动保存的（strm 的 User-Agent / Referer、弹幕屏蔽词）打完就还原，并核对还原了
        fun section(key: String) = g.settings.value[key] as? JsonObject
        val accel0 = section("accelerator")
        val dm0 = section("danmaku")
        fun restore(key: String, orig: JsonObject?) {
            if (orig != null) d.ui { g.settings.patch(key, orig) }
        }
        try {
            c.nav(Route.Settings("strm"), 900)
            field(c, "strm-ua", "设置 › strm › User-Agent 输入行（P25）", "set-strm-ua", secret = false, rows = "P25 S05")
            restore("accelerator", accel0)
            c.nav(Route.Settings("strm"), 900)
            field(c, "strm-referer", "设置 › strm › Referer 输入行（P25）", "set-strm-referer", secret = false, rows = "P25 S05")
            restore("accelerator", accel0)

            c.nav(Route.Settings("danmaku"), 1_200)
            val apiTags = d.tree().tags { it.startsWith("set-dm-apiUrl") && it.endsWith("-input") }.mapNotNull { it.tag }.distinct()
            if (apiTags.isEmpty()) c.skip("dm-api", "设置 › 弹幕 › 弹幕服务器地址（D07）", "这一页上没有弹幕服务器地址的输入框")
            for (tag in apiTags) {
                c.nav(Route.Settings("danmaku"), 1_200)
                field(c, "dm-api-${tag.removePrefix("set-dm-apiUrl").removeSuffix("-input").ifEmpty { "1" }.trim('-')}", "设置 › 弹幕 › 弹幕服务器地址（打码的框；不点保存）（D07）", tag, secret = true, rows = "D07 S05")
            }
            c.nav(Route.Settings("danmaku"), 1_200)
            field(c, "tmdb", "设置 › 弹幕 › TMDB 令牌（打码的框；不点保存）（D07）", "set-tmdb-input", secret = true, rows = "D07 S05")
            c.nav(Route.Settings("danmaku"), 1_200)
            field(c, "dm-block", "设置 › 弹幕 › 屏蔽词（D03）", "set-dm-blockWords", secret = false, rows = "D03 S05")
            restore("danmaku", dm0)

            c.nav(Route.Settings("backup"), 1_500)
            field(c, "dav-url", "设置 › 备份与同步 › WebDAV 地址（不点保存）（T08）", "backup-dav-url", secret = false, rows = "T08 S05")
            c.nav(Route.Settings("backup"), 1_500)
            field(c, "dav-user", "设置 › 备份与同步 › WebDAV 用户名（不点保存）（T08）", "backup-dav-user", secret = false, rows = "T08 S05")
            c.nav(Route.Settings("backup"), 1_500)
            field(c, "dav-pass", "设置 › 备份与同步 › WebDAV 密码（打码的框；不点保存）（T08）", "backup-dav-pass", secret = true, rows = "T08 S05")
        } finally {
            restore("accelerator", accel0)
            restore("danmaku", dm0)
            c.nav(Route.Home, 600)
        }
        val accel1 = section("accelerator")
        val dm1 = section("danmaku")
        c.expect(
            "settings-restored", "打过字的自动保存设置已还原（strm 请求头、弹幕屏蔽词，和测之前逐键相同）", accel1 == accel0 && dm1 == dm0,
            values = mapOf("accelerator 相同" to (accel1 == accel0), "danmaku 相同" to (dm1 == dm0)),
        )

        // ---- 登录页：添加服务器的地址框（只打字，不点「连接」、不碰用户名 / 密码）
        c.nav(Route.Servers, 1_200)
        val add = c.waitTag("desktop-login-add", 6_000)
        if (add != null) {
            d.click(add)
            if (c.waitTag("desktop-login-address", 4_000) != null) {
                field(c, "login-address", "登录 › 添加服务器的地址框（不点连接）（C01）", "desktop-login-address", secret = false, rows = "C01 S05")
            } else c.check("login-address", "登录 › 添加服务器的地址框（C01）", Verdict.Fail, "点「添加服务器」以后没有出地址框")
            d.key(KeyEvent.VK_ESCAPE)
        } else c.skip("login-address", "登录 › 添加服务器的地址框（C01）", "服务器页上没有「添加服务器」")
        c.skip("login-password", "登录 › 用户名 / 密码框", "不往账号密码框里打字（规矩：不输入任何账号密码）；同一个 PaperField 组件，地址框已覆盖")
        c.nav(Route.Home, 600)

        // ---- 播放器里的弹幕源搜索（D04）：开播 → 弹幕面板 → 「换弹幕 / 手动搜索」→ 搜索框。打完收起，不点候选、不点搜索
        val item = resumeItem(c)
        if (item == null) {
            c.skip("dm-search", "播放器 › 弹幕源 › 手动搜索框（D04）", "「继续观看」是空的")
            return
        }
        c.nav(Route.Item(item.id), 2_000)
        val t0 = System.currentTimeMillis()
        // 从头播（不 seek 到续看点）：Mock 的续看点按「假时长」算（如 381s），远超 60s 样片，seek 被夹到片尾→立即 EOF→
        // 自动换集，awaitFirstFrame 在换集瞬间测不到稳定首帧而误判「40s 没首帧」（1.3.2 / 1.4.0 同样复现，非回归）。
        // 从头播样片能正常走完，首帧稳定；弹幕源搜索框只需要「在播」，不依赖续看位置。
        c.nav(Route.Play(item.id, startMs = null), 300)
        val ff = c.awaitFirstFrame(t0)
        if (ff == null) {
            c.check("dm-search", "播放器 › 弹幕源 › 手动搜索框（D04）", Verdict.Fail, "40 秒内没出首帧，进不了弹幕面板")
            return
        }
        Thread.sleep(1_200)
        c.wakeControls()
        val word = d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.startsWith("弹幕") } }
        if (word == null) {
            c.check("dm-search", "播放器 › 弹幕源 › 手动搜索框（D04）", Verdict.Fail, "控制坞里找不到「弹幕」功能词")
        } else {
            d.click(word)
            val panelOpen = d.waitFor(3_000) { d.tree().has("player-side") }
            Thread.sleep(900)
            var link = scrollTo(c) { tr -> tr.nodes.lastOrNull { n -> (n.texts + n.desc).any { it.contains("换弹幕") } } }
            val panelShot = d.shot("input-dm-panel", "弹幕面板（找「换弹幕」）")
            if (link != null && !link.visible) {
                // 滚到底了还看不见：面板最后一行被底下的「收起」压住了。记下来，改点「弹幕源」下面那一行（同一个动作）
                val tr = d.tree()
                val side = tr.tag("player-side")
                c.check(
                    "dm-panel-last-row", "弹幕面板滚到底能看到最后一行「换弹幕 / 手动搜索」（D04 / P03）", Verdict.Fail,
                    "滚到底了这一行仍被裁掉（在「收起」那一条后面）：用户点不到",
                    values = mapOf("这一行" to link.bounds.short(), "面板" to side?.bounds?.short(), "收起" to tr.tag("player-side-close")?.bounds?.short()),
                    shots = listOf(panelShot),
                )
                val inPanel = side?.let { listOf(it) + tr.descendants(it) }.orEmpty()
                val header = inPanel.indexOfFirst { n -> n.texts.any { it.replace(" ", "") == "弹幕源" } }
                link = inPanel.drop(header + 1).firstOrNull { n -> n.visible && (n.texts + n.desc).any { it.isNotBlank() } && (n.texts + n.desc).none { it.contains("换弹幕") } }
            }
            if (link != null) d.click(link)
            if (c.waitTag("dm-picker", 4_000) == null) {
                val after = d.shot("input-dm-after-link", "点了「换弹幕」以后")
                c.check(
                    "dm-search", "播放器 › 弹幕源 › 手动搜索框（D04）", Verdict.Fail, "点「换弹幕 / 手动搜索」以后没有滑出弹幕来源",
                    values = mapOf("面板开了" to panelOpen, "链接" to link?.let { "${(it.texts + it.desc).joinToString("/")} ${it.bounds.short()} 看得见=${it.visible}" }, "页面" to c.path),
                    shots = listOf(panelShot, after),
                )
            } else if (!d.tree().has("dm-search")) {
                c.skip("dm-search", "播放器 › 弹幕源 › 手动搜索框（D04）", "弹幕来源里没有手动搜索（没配弹幕服务器）")
            } else {
                val paused0 = c.controller?.clock?.value?.paused
                val fs0 = d.isFullScreen()
                field(c, "dm-search", "播放器 › 弹幕源 › 手动搜索框：j f k 不触发快进 / 全屏 / 暂停（D04 / P19）", "dm-search", secret = false, rows = "D04 S05 P19") {
                    val leaks = listOfNotNull(
                        "F 全屏了".takeIf { d.isFullScreen() != fs0 },
                        "K 暂停 / 继续了".takeIf { c.controller?.clock?.value?.paused != paused0 },
                    )
                    leaks.joinToString("、").ifEmpty { null }
                }
                d.tree().tag("dm-picker-close")?.let { d.click(it) }
            }
        }
        if (d.isFullScreen()) { d.toggleFullScreen(); d.waitFor(6_000) { !d.isFullScreen() } }
        c.back(1_500)
    }

    // ================================================================== 3. 快捷键

    private fun keys(c: Ctx) {
        val d = c.d
        d.waitFor(15_000) { d.tree().has("masthead-volume") }
        Thread.sleep(800)
        // / 和 Esc
        d.key(KeyEvent.VK_SLASH, '/')
        val toSearch = d.waitFor(3_000) { c.route is Route.Search }
        val fieldFocused = d.waitFor(2_000) { d.tree().tag("desktop-search-field")?.focused == true }
        c.expect("slash", "首页按「/」进搜索，搜索框直接有焦点（X04 / S01）", toSearch && fieldFocused, values = mapOf("页面" to c.path, "框有焦点" to fieldFocused))
        d.key(KeyEvent.VK_ESCAPE)
        c.expect("esc-back-search", "搜索页（空）按 Esc 回上一页（B29）", d.waitFor(3_000) { c.route is Route.Home }, values = mapOf("页面" to c.path))

        d.key(KeyEvent.VK_K, 'k', cmd = true)
        c.expect("cmd-k", "⌘K 进搜索（X04）", d.waitFor(3_000) { c.route is Route.Search }, values = mapOf("页面" to c.path))
        d.key(KeyEvent.VK_ESCAPE)
        d.waitFor(3_000) { c.route is Route.Home }

        d.key(KeyEvent.VK_COMMA, ',', cmd = true)
        val toSettings = d.waitFor(3_000) { c.route is Route.Settings }
        val settingsShot = if (toSettings) { Thread.sleep(800); d.shot("keys-settings", "⌘, 进设置") } else null
        c.expect("cmd-comma", "⌘, 进设置（X04 / T01）", toSettings, values = mapOf("页面" to c.path), shots = listOf(settingsShot))
        d.key(KeyEvent.VK_ESCAPE)
        c.expect("esc-back-settings", "设置页按 Esc 回上一页（B29）", d.waitFor(3_000) { c.route is Route.Home }, values = mapOf("页面" to c.path))

        // F5 / 点 VOL.：付印新刊，期号 +1（B05）
        c.reset()
        d.waitFor(10_000) { d.tree().has("desktop-reprint") }
        fun vol() = d.tree().textUnder("masthead-volume")?.let(::volumeNumber)
        val v0 = vol()
        d.key(KeyEvent.VK_F5)
        val v1ok = d.waitFor(4_000) { (vol() ?: -1) == (v0 ?: -9) + 1 }
        val v1 = vol()
        c.expect("f5-reprint", "F5 付印新刊：期号 +1（B05）", v0 != null && v1ok, values = mapOf("之前" to v0, "之后" to v1))
        d.tree().tag("desktop-reprint")?.let { d.click(it) }
        val v2ok = d.waitFor(4_000) { (vol() ?: -1) == (v1 ?: -9) + 1 }
        c.expect("vol-click", "点报头的 VOL. 付印新刊：期号 +1（B05）", v1 != null && v2ok, values = mapOf("之前" to v1, "之后" to vol()))
        // ⌘R（M2-3 才有）
        val v3 = vol()
        d.key(KeyEvent.VK_R, 'r', cmd = true)
        if (d.waitFor(2_500) { (vol() ?: -1) == (v3 ?: -9) + 1 }) c.check("cmd-r", "⌘R 付印新刊（M2-3）", Verdict.Pass, values = mapOf("之前" to v3, "之后" to vol()))
        else c.skip("cmd-r", "⌘R 付印新刊（M2-3）", "M2-3 还没做：DesktopKeys 只认 ⌘K ⌘, ⌘F，⌘R 不起作用", values = mapOf("期号" to vol()))

        // ← →：首页翻封面（B01）。封面里程表 RollingNumber 用 clearAndSetSemantics 只留 contentDescription（整数），
        // testTag("odometer") 被抹掉（Odometer.kt:50），所以按读屏说明是纯整数来认，不靠 tag；优先封面区 desktop-spread
        // 子树里的那个，排除报头里程表（那是付印期号，不随翻封面变）。先收纸片，免得纸片盖住封面区读不到。
        closeSlips(c)
        fun spread(): String {
            val t = d.tree()
            val mast = t.tag("masthead-volume")
            fun underMast(n: AcceptDriver.Node): Boolean {
                var p: Int? = n.parent
                while (p != null) {
                    val pn = t.nodes.firstOrNull { it.id == p } ?: return false
                    if (mast != null && pn.id == mast.id) return true
                    p = pn.parent
                }
                return false
            }
            val spreadNode = t.tag("desktop-spread")
            val pool = if (spreadNode != null) listOf(spreadNode) + t.descendants(spreadNode) else t.nodes
            val odo = pool.filter { n -> !underMast(n) && n.desc.any { it.matches(Regex("\\d+")) } }
            return odo.flatMap { it.desc }.joinToString("/")
        }
        val s0 = spread()
        d.key(KeyEvent.VK_RIGHT)
        val flipped = d.waitFor(3_000) { spread() != s0 }
        d.key(KeyEvent.VK_LEFT)
        val flippedBack = d.waitFor(3_000) { spread() == s0 }
        c.expect("arrows-flip", "首页 → 翻到下一期、← 翻回来（期号里程表的数，B01）", s0.isNotEmpty() && flipped && flippedBack, values = mapOf("期号" to s0, "翻过去变了" to flipped, "翻回来一样" to flippedBack, "现在" to spread()))

        // ⌘ 提示和菜单栏（M2-3 / M2-4）
        val capsule = d.tree().textUnder("desktop-search").orEmpty()
        when {
            "⌘K" in capsule || "⌘ K" in capsule -> c.check("cmd-hint", "搜索胶囊写 ⌘K（X04 / M2-3）", Verdict.Pass, values = mapOf("胶囊" to capsule))
            "Ctrl" in capsule -> c.skip("cmd-hint", "搜索胶囊写 ⌘K（X04 / M2-3）", "M2-3 还没做：胶囊上写的是「Ctrl K」", values = mapOf("胶囊" to capsule))
            else -> c.check("cmd-hint", "搜索胶囊写 ⌘K（X04 / M2-3）", Verdict.Fail, "胶囊上没有快捷键提示", values = mapOf("胶囊" to capsule))
        }
        val menus = d.mainMenuItems()
        if (menus.size >= 7) c.check("menubar", "菜单栏七个菜单（X11 / M2-4）", Verdict.Pass, values = mapOf("菜单" to menus.joinToString(" ")))
        else c.skip("menubar", "菜单栏七个菜单（X11 / M2-4）", "M2-4 还没做：主菜单只有 ${menus.size} 项", values = mapOf("菜单" to menus.joinToString(" ")))
        // ⌘2：导航第二格（M2-3）
        d.key(KeyEvent.VK_2, '2', cmd = true)
        if (d.waitFor(2_000) { d.tree().nodes.any { it.tag == "desktop-tab-on" && "发现" in it.desc } }) c.check("cmd-digit", "⌘1–5 换导航页（M2-3）", Verdict.Pass)
        else c.skip("cmd-digit", "⌘1–5 换导航页（M2-3）", "M2-3 还没做：⌘2 不换页")
        c.reset()

        // ⌘F：海报墙的筛选条（B10）
        if (c.tab("片库") && d.waitFor(10_000) { d.tree().has("desktop-lib-row") }) {
            Thread.sleep(600)
            d.tree().tag("desktop-lib-row")?.let { d.click(it) }
            if (d.waitFor(10_000) { d.tree().has("desktop-wall") && c.route is Route.Library }) {
                Thread.sleep(1_200)
                d.key(KeyEvent.VK_F, 'f', cmd = true)
                val open = d.waitFor(3_000) { d.tree().has("wall-filter") }
                val sh = if (open) d.shot("keys-wall-filter", "⌘F 开筛选条") else null
                d.key(KeyEvent.VK_ESCAPE)
                val closed = d.waitFor(3_000) { !d.tree().has("wall-filter") }
                c.expect("cmd-f", "海报墙 ⌘F 推开筛选条，Esc 收起（B10）", open && closed, values = mapOf("开了" to open, "Esc 收了" to closed), shots = listOf(sh))
            } else c.check("cmd-f", "海报墙 ⌘F 推开筛选条（B10）", Verdict.Fail, "点第一个片库以后没进海报墙", values = mapOf("页面" to c.path))
        } else c.check("cmd-f", "海报墙 ⌘F 推开筛选条（B10）", Verdict.Fail, "片库页没出来")

        // [ ]：多季剧集详情换季（B13）；Enter 播放（X04）；播放器里 Esc 回详情（B29）；⌘[ / 鼠标侧键（M2-3 / M2-7）
        val series = multiSeasonSeries(c)
        if (series == null) c.skip("brackets", "剧集详情 [ ] 换季（B13）", "找不到两季以上的剧（只读查了「继续观看」和片库里的前 40 部剧）")
        else {
            c.nav(Route.Home, 300)
            c.nav(Route.Item(series.id), 600)
            if (c.waitTag("d-season-dd", 15_000) != null) {
                Thread.sleep(1_200)
                fun season() = d.tree().textUnder("d-season-dd").orEmpty()
                val s1 = season()
                d.key(KeyEvent.VK_CLOSE_BRACKET, ']')
                val changed = d.waitFor(4_000) { season() != s1 }
                val s2 = season()
                val sh = d.shot("keys-season", "剧集详情按 ] 换季")
                d.key(KeyEvent.VK_OPEN_BRACKET, '[')
                val back = d.waitFor(4_000) { season() == s1 }
                c.expect("brackets", "剧集详情 ] 换到下一季、[ 换回来（B13）", changed && back, values = mapOf("原来" to s1, "按 ] 后" to s2, "按 [ 后" to season()), shots = listOf(sh))
            } else c.check("brackets", "剧集详情 [ ] 换季（B13）", Verdict.Fail, "详情页没出选季（d-season-dd）")
            // ⌘[（M2-3）：回上一页
            val before = c.path
            d.key(KeyEvent.VK_OPEN_BRACKET, '[', cmd = true)
            if (d.waitFor(2_000) { c.path != before }) c.check("cmd-bracket", "⌘[ 回上一页（B29 / M2-3）", Verdict.Pass, values = mapOf("页面" to c.path))
            else c.skip("cmd-bracket", "⌘[ 回上一页（B29 / M2-3）", "M2-3 还没做：⌘[ 不起作用")
            if (c.route !is Route.Item) c.nav(Route.Item(series.id), 1_500)
            // 鼠标侧键（M2-7）：后退键 = AWT 的第 4 个键
            val beforeSide = c.path
            val (w, h) = d.ui { d.window.width to d.window.height }
            d.click(w / 2.0, h - 40.0, button = 4)
            if (d.waitFor(2_000) { c.path != beforeSide }) {
                c.check("mouse-back", "鼠标侧键（后退）回上一页（B29 / M2-7）", Verdict.Pass, values = mapOf("页面" to c.path))
                c.nav(Route.Item(series.id), 1_500)
            } else c.skip("mouse-back", "鼠标侧键（后退）回上一页（B29 / M2-7）", "M2-7 还没做：侧键不起作用")
        }
        // Enter 播放 + 播放器里 Esc 回详情：用「继续观看」那一条（不碰别的片的进度）
        val item = resumeItem(c)
        if (item != null) {
            val detail = item.seriesId ?: item.id
            c.nav(Route.Home, 300)
            c.nav(Route.Item(detail), 600)
            if (d.waitFor(15_000) { d.tree().nodes.any { it.tag?.startsWith("d-") == true } }) {
                Thread.sleep(1_500)
                d.key(KeyEvent.VK_ENTER, '\n', typed = false)
                val playing = d.waitFor(5_000) { c.path.startsWith("/play") }
                c.expect("enter-play", "详情页 Enter 播放（X04）", playing, values = mapOf("页面" to c.path))
                if (playing) {
                    Thread.sleep(3_000)
                    d.key(KeyEvent.VK_ESCAPE)
                    val back = d.waitFor(5_000) { c.route is Route.Item }
                    c.expect("esc-player", "播放器里按 Esc 回到详情页（B29 / P10）", back, values = mapOf("页面" to c.path))
                    if (!back) c.back(1_500)
                }
            } else c.check("enter-play", "详情页 Enter 播放（X04）", Verdict.Fail, "详情页没出来")
        } else c.skip("enter-play", "详情页 Enter 播放（X04）", "「继续观看」是空的")
        c.skip("v-w", "V 收藏 / W 标记已看（M05 / X04）", "写服务器：只在 Mock 上验（Mock 服务器没装）；运行器从不按 V / W")
    }

    // ================================================================== 4. 浏览

    private fun browse(c: Ctx) {
        val d = c.d
        d.waitFor(15_000) { d.tree().has("desktop-nav") }
        Thread.sleep(800)
        val tabs = listOf("首页" to "desktop-home", "发现" to "desktop-discover", "片库" to "desktop-libs", "合集" to "desktop-colls", "我的" to "desktop-me")
        for ((label, root) in tabs) {
            val clicked = c.tab(label)
            val shown = d.waitFor(15_000) { d.tree().has(root) }
            Thread.sleep(1_200)
            val sh = d.shot("browse-tab-$root", "导航「$label」")
            val on = d.tree().nodes.firstOrNull { it.tag == "desktop-tab-on" }?.desc?.firstOrNull()
            c.expect("tab-$label", "导航胶囊点「$label」：选中这一格、出这一页（B25）", clicked && shown && on == label, values = mapOf("选中的格" to on, "页面根节点" to root), shots = listOf(sh))
        }
        // 片库海报墙 → 滚下去 → 点一张进详情 → 返回：回到原来那一屏，不先出骨架
        c.tab("片库")
        d.waitFor(10_000) { d.tree().has("desktop-lib-row") }
        Thread.sleep(800)
        // 挑部数最多的那个库（「进详情再返回」要能滚下去好几排）
        val libTree = d.tree()
        val lib = libTree.tags { it == "desktop-lib-row" }.maxByOrNull { n -> Regex("""(\d[\d,]*)\s*部""").findAll(libTree.textUnder(n)).mapNotNull { it.groupValues[1].replace(",", "").toIntOrNull() }.maxOrNull() ?: 0 }
        if (lib == null) { c.check("wall", "片库海报墙（B10）", Verdict.Fail, "片库页没有媒体库"); return }
        d.click(lib)
        val wallOk = d.waitFor(15_000) { d.tree().tags { it.startsWith("desk-card") }.size >= 4 }
        Thread.sleep(1_500)
        val wallShot = d.shot("browse-wall", "片库海报墙")
        val tally = d.tree().textUnder("wall-tally")
        c.expect("wall", "进一个媒体库：海报墙出来（B10）", wallOk && c.route is Route.Library, values = mapOf("卡片数（已组合）" to d.tree().tags { it.startsWith("desk-card") }.size, "计数" to tally), shots = listOf(wallShot))
        if (wallOk) {
            val (w, h) = d.ui { d.window.width.toDouble() to d.window.height.toDouble() }
            val firstY0 = d.tree().tags { it == "desk-card" || it == "desk-card-hover" }.minOfOrNull { it.bounds.y }
            d.wheel(w / 2, h / 2, 8)
            Thread.sleep(1_200)
            // 卡的片名 = 海报那一格的读屏说明（悬停时片名下面那一排「播放 / 印章位 …」不算）
            fun cardsIn(tr: AcceptDriver.Tree) = tr.tags { it == "desk-card" || it == "desk-card-hover" }.mapNotNull { n ->
                val actions = tr.descendants(n).filter { it.tag == "desk-card-actions" }.flatMap { listOf(it) + tr.descendants(it) }.map { it.id }.toSet()
                val title = tr.descendants(n).filter { it.id !in actions }.flatMap { it.desc }.firstOrNull() ?: return@mapNotNull null
                title to n.bounds
            }
            val t1 = d.tree()
            val all = cardsIn(t1)
            val pick = all.filter { (_, b) -> b.y > 130 && b.bottom < h - 10 }.minByOrNull { kotlin.math.abs(it.second.cy - h / 2) }
            val firstY1 = t1.tags { it == "desk-card" || it == "desk-card-hover" }.minOfOrNull { it.bounds.y }
            if (pick == null) c.check("wall-return", "进详情再返回回到原位（B07）", Verdict.Fail, "滚下去以后找不到一张完整的卡", values = mapOf("库" to libTree.textUnder(lib).take(60), "卡（y）" to all.joinToString(" ") { "%.0f".format(it.second.y) }, "第一张卡 y" to "$firstY0 → $firstY1"))
            else {
                val (title, b0) = pick
                val scrolled = d.shot("browse-wall-scrolled", "海报墙滚下去以后")
                d.click(b0.cx, b0.y + b0.h * 0.4)
                val opened = d.waitFor(10_000) { c.route is Route.Item }
                Thread.sleep(2_000)
                val det = d.shot("browse-detail", "海报墙进的详情页")
                d.tree().tag("desktop-back")?.let { d.click(it) } ?: d.key(KeyEvent.VK_ESCAPE)
                // 返回后第一眼：不出骨架、同一张卡在同一个位置
                var skeleton = false
                var b1: R? = null
                d.waitFor(3_000, 60) {
                    val tr = d.tree()
                    if (tr.has("skeleton")) skeleton = true
                    b1 = cardsIn(tr).firstOrNull { it.first == title }?.second
                    c.route is Route.Library && b1 != null
                }
                Thread.sleep(600)
                val returned = d.shot("browse-wall-returned", "返回以后的海报墙")
                val dy = b1?.let { kotlin.math.abs(it.y - b0.y) }
                c.expect(
                    "wall-return", "滚到下面点一张进详情 → 返回：还是那一屏（同一张卡差 ≤ 8 pt），没有先出骨架（B07）",
                    opened && b1 != null && dy!! <= 8.0 && !skeleton,
                    values = mapOf("库" to libTree.textUnder(lib).take(60), "滚动（第一张卡 y）" to "$firstY0 → $firstY1", "卡" to title, "进去前 y" to "%.1f".format(b0.y), "回来后 y" to b1?.let { "%.1f".format(it.y) }, "出过骨架" to skeleton),
                    shots = listOf(scrolled, det, returned),
                )
            }
        }
        // 搜索：拼音首字母、演员（名字用输入法上屏那一步送进去；缩写用键盘打）
        c.reset()
        val dune = findMovie(c, "沙丘")
        if (dune == null) c.skip("search-pinyin", "搜索拼音首字母（S01）", "片库里搜不到《沙丘》")
        else {
            val initials = Pinyin.initials(dune.name)
            openSearch(c)
            d.type(initials)
            val hit = d.waitFor(10_000) { d.tree().textUnder("desktop-search").orEmpty().contains(dune.name) }
            val sh = d.shot("browse-search-pinyin", "搜索「$initials」")
            c.expect("search-pinyin", "搜索框打拼音首字母「$initials」能出《${dune.name}》（S01）", hit, values = mapOf("打的" to initials), shots = listOf(sh))
            d.key(KeyEvent.VK_ESCAPE)
            val actor = runCatching { runBlocking { c.client().getItem(dune.id) } }.getOrNull()?.people?.firstOrNull { it.type == "Actor" && it.name.isNotBlank() }
            if (actor == null) c.skip("search-actor", "按演员搜（S04）", "《${dune.name}》没有演员信息")
            else {
                d.commitText(actor.name)
                fun people(): String { val tr = d.tree(); return tr.tags { it == "search-person" }.joinToString(" / ") { tr.textUnder(it) } }
                val typed = d.waitFor(3_000) { d.tree().tag("desktop-search-field")?.editable == actor.name }
                val found = d.waitFor(10_000) { people().contains(actor.name) }
                val shA = d.shot("browse-search-actor", "搜演员名")
                c.expect("search-actor", "搜演员名字：演员一栏里有 ta（S04；名字经输入法上屏事件送进框）", typed && found, values = mapOf("演员" to actor.name, "上屏进了框" to typed, "演员一栏" to people().take(160)), shots = listOf(shA))
                d.key(KeyEvent.VK_ESCAPE)
                val ai = Pinyin.initials(actor.name)
                if (ai.length >= 2 && actor.name.any { it.code in 0x4E00..0x9FA5 }) {
                    d.type(ai)
                    val foundAbbr = d.waitFor(10_000) { d.tree().textUnder("desktop-search").orEmpty().contains(actor.name) }
                    val shB = d.shot("browse-search-actor-abbr", "搜演员缩写")
                    c.expect("search-actor-abbr", "搜演员的拼音缩写「$ai」也能找到 ta（S04）", foundAbbr, values = mapOf("演员" to actor.name), shots = listOf(shB))
                    d.key(KeyEvent.VK_ESCAPE)
                } else c.skip("search-actor-abbr", "搜演员的拼音缩写（S04）", "演员名不是汉字（${actor.name}），没有拼音缩写")
            }
            d.key(KeyEvent.VK_ESCAPE)
        }
        c.human("trackpad", "触控板双指横滑翻封面 / 横向海报行（B01 / B23）", "运行器只发得了滚轮事件，触控板手势要人试")
    }

    private fun openSearch(c: Ctx) {
        val d = c.d
        if (c.route !is Route.Search) d.tree().tag("desktop-search")?.let { d.click(it) }
        d.waitFor(5_000) { d.tree().tag("desktop-search-field")?.focused == true }
        d.settle(200)
    }

    // ================================================================== 5. 播放

    private fun play(c: Ctx) {
        val d = c.d
        val item = resumeItem(c) ?: run { c.check("resume", "续看", Verdict.Fail, "「继续观看」是空的：没有可以续看的片"); return }
        c.nav(Route.Item(item.seriesId ?: item.id), 2_500)
        val t0 = System.currentTimeMillis()
        c.nav(Route.Play(item.id, startMs = startMsOf(item)), 200)
        val ff = c.awaitFirstFrame(t0)
        val snap = c.controller?.snapshot?.value
        c.expect(
            "first-frame", "续看「继续观看」的第一条：出首帧、位置在走（P01 / P24）", ff != null && snap?.phase == PlayPhase.Ready,
            values = mapOf(
                "首帧（毫秒，从进播放页算到开场 seek 落定后位置在走）" to ff,
                "控制器的分段（毫秒，从开播算）" to startupPhases(c.lastStartupLine(item.id)),
                "阶段" to snap?.phase, "类型" to item.type, "hwdec-current" to c.mpv("hwdec-current"), "vo" to c.mpv("current-vo"), "gpu-context" to c.mpv("current-gpu-context"),
            ),
        )
        if (ff == null) { c.back(1_500); return }
        Thread.sleep(4_500) // 控件自己隐去
        val full = layerVsFrame(c)
        val ns = d.visibleNsWindows()
        val fullShot = d.shot("play-full", "播放：控件隐去，只有画面")
        val img = fullShot?.let(d::image)
        val sd = if (img != null && full.second != null) Pixels.lumaStdDev(img, full.second!!.scaled(d.shotScale(img))) else null
        c.expect(
            "in-window", "画面在咕咕嘎嘎的窗口里：视频层在窗口层树里、和画面那一格差 ≤ 2 pt，没有第二扇窗（P01 / P03）",
            full.first && ns.size == 1,
            values = mapOf("视频层（点）" to full.second?.short(), "画面格（点）" to full.third?.short(), "窗口内容" to full.fourth?.short(), "可见窗口" to ns.joinToString(" "), "画面亮度标准差" to sd?.let { "%.1f".format(it) }),
            shots = listOf(fullShot),
        )
        if (sd != null && sd < 4.0) c.human("picture-flat", "画面像是一片纯色（标准差 < 4）", "可能正好是黑场 / 片头，也可能没出画面：看截图", listOf(fullShot))

        // 控件：返回钮让位红绿灯、网速
        c.wakeControls()
        d.waitFor(3_000) { d.tree().has("player-back") }
        Thread.sleep(700)
        val tc = d.tree()
        val backBtn = tc.tag("player-back")
        val zoomRight = d.trafficLights().getOrNull(2)?.third?.right ?: 0.0
        val ctlShot = d.shot("play-controls", "播放：控件出来")
        c.expect(
            "back-inset", "播放器顶栏的返回钮左边距 ≥ 85 pt，不被红绿灯压住（X01 / M2-2）",
            backBtn != null && backBtn.bounds.x >= 84.5 && backBtn.bounds.x >= zoomRight + 4,
            values = mapOf("返回钮左边" to backBtn?.bounds?.x, "缩放钮右边" to zoomRight), shots = listOf(ctlShot),
        )
        val speed = tc.textUnder("player-speed")
        c.expect("net-speed", "控件出来时底部时间后面有网速（P13）", speed != null && Regex("""\d""").containsMatchIn(speed), values = mapOf("网速" to speed, "mpv cache-speed" to c.mpv("cache-speed")))
        c.expect("no-mascot-playing", "播放时顶栏她那一格是空的（G01 / P04）", tc.has("pause-seat-empty") && !tc.has("pause-seat"))

        // 暂停 / 继续（空格）
        d.key(KeyEvent.VK_SPACE, ' ')
        val paused = d.waitFor(3_000) { c.controller?.clock?.value?.paused == true && c.mpv("pause") == "yes" }
        c.wakeControls()
        val seat = d.waitFor(2_000) { d.tree().has("pause-seat") }
        val pShot = d.shot("play-paused", "空格暂停：她坐进顶栏")
        c.expect("space-pause", "空格暂停：控制器和 mpv 都停了，她坐进顶栏那一格（P19 / P04 / G01）", paused && seat, values = mapOf("mpv pause" to c.mpv("pause"), "她" to seat), shots = listOf(pShot))
        d.key(KeyEvent.VK_SPACE, ' ')
        c.expect("space-resume", "再按空格继续", d.waitFor(3_000) { c.controller?.clock?.value?.paused == false && c.mpv("pause") == "no" }, values = mapOf("mpv pause" to c.mpv("pause")))

        // 快进（→）
        val pos0 = c.mpv("time-pos")?.toDoubleOrNull()
        d.key(KeyEvent.VK_RIGHT)
        var pos1: Double? = null
        val jumped = d.waitFor(5_000) { (c.mpv("time-pos")?.toDoubleOrNull()?.also { pos1 = it } ?: 0.0) > (pos0 ?: 1e9) + 4.0 }
        c.expect("seek", "→ 快进（位置往后跳 ≥ 4 秒）（P19）", jumped, values = mapOf("之前" to pos0?.let { "%.1f".format(it) }, "之后" to pos1?.let { "%.1f".format(it) }))

        // 倍速（] / [）
        d.key(KeyEvent.VK_CLOSE_BRACKET, ']')
        val faster = d.waitFor(3_000) { (c.mpv("speed")?.toDoubleOrNull() ?: 1.0) > 1.01 }
        val sp = c.mpv("speed")
        d.key(KeyEvent.VK_OPEN_BRACKET, '[')
        val normal = d.waitFor(3_000) { kotlin.math.abs((c.mpv("speed")?.toDoubleOrNull() ?: 0.0) - 1.0) < 0.01 }
        c.expect("rate", "] 加速、[ 回到 1×（P07 / P19）", faster && normal, values = mapOf("按 ] 后 mpv speed" to sp, "按 [ 后" to c.mpv("speed")))

        // 字幕面板推开：画面让到左边、按比例缩、播放不断（P03）
        c.wakeControls()
        val subWord = d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.startsWith("字幕") } }
        if (subWord == null) c.check("panel-inset", "开「字幕」面板：画面让位（P03）", Verdict.Fail, "控制坞里找不到「字幕」功能词")
        else {
            d.click(subWord)
            val opened = d.waitFor(3_000) { d.tree().has("player-side") }
            Thread.sleep(1_500)
            val tp0 = c.mpv("time-pos")?.toDoubleOrNull()
            val inset = layerVsFrame(c)
            val side = d.tree().tag("player-side")?.bounds
            val inShot = d.shot("play-panel-sub", "开字幕面板：画面让位")
            Thread.sleep(1_000)
            val tp1 = c.mpv("time-pos")?.toDoubleOrNull()
            val layer = inset.second
            val host = inset.fourth
            val ok = opened && inset.first && layer != null && host != null && side != null && layer.w < host.w - 50 && layer.right <= side.x + 2 && (tp1 ?: 0.0) > (tp0 ?: 1e9)
            c.expect(
                "panel-inset", "开「字幕」面板：画面缩进左边那一格（和画面格差 ≤ 2 pt、不伸到面板下面），播放不断（P03）", ok,
                values = mapOf("视频层" to layer?.short(), "画面格" to inset.third?.short(), "面板" to side?.short(), "time-pos" to "${tp0?.let { "%.2f".format(it) }} → ${tp1?.let { "%.2f".format(it) }}"),
                shots = listOf(inShot),
            )
            d.tree().tag("player-side-close")?.let { d.click(it) } ?: d.key(KeyEvent.VK_ESCAPE)
            d.waitFor(3_000) { !d.tree().has("player-side") }
            Thread.sleep(1_500)
            val after = layerVsFrame(c)
            c.expect("panel-close", "收起面板：画面回到整幅", after.first && after.second != null && after.fourth != null && after.second!!.w >= after.fourth!!.w - 2 * 120, values = mapOf("视频层" to after.second?.short(), "画面格" to after.third?.short()))
        }

        // 全屏（F）：画面在刘海那一条下面
        d.key(KeyEvent.VK_F, 'f')
        val fs = d.waitFor(8_000) { d.isFullScreen() }
        Thread.sleep(2_200)
        val (safeTop, screen) = d.screenSafeTop()
        val fb = d.ui { d.window.bounds }
        val fsl = layerVsFrame(c)
        val fsShot = d.shot("play-fullscreen", "播放：F 进全屏")
        c.expect(
            "fullscreen", "F 进系统全屏：窗口顶边在刘海那一条之下，画面在窗口里、和画面格对得上（P20 / M2-6）",
            fs && fb.y >= safeTop - 1 && fsl.first,
            values = mapOf("安全区顶边" to safeTop, "屏幕" to screen.short(), "全屏窗口" to "${fb.x},${fb.y} ${fb.width}x${fb.height}", "视频层" to fsl.second?.short(), "画面格" to fsl.third?.short()),
            shots = listOf(fsShot),
        )
        // 没进去就不按第二下（再按一次是「又要进去」），这一条记没过
        val fsOut = fs && run {
            d.key(KeyEvent.VK_F, 'f')
            d.waitFor(8_000) { !d.isFullScreen() }.also { Thread.sleep(1_500) }
        }
        c.expect("fullscreen-exit", "再按 F 退出全屏", fsOut, detail = if (!fs) "上一步没进全屏，没按" else "", values = mapOf("可见窗口" to d.visibleNsWindows().joinToString(" ")))
        // P20：F 进出再来 4 次（一共 5 次）；结束后可见窗口里没有系统全屏的窗（NSToolbarFullScreenWindow）
        if (!fs || !fsOut) c.check("fullscreen-5x", "播放器里 F 进出全屏一共 5 次（P20）", Verdict.Fail, "第一次就没${if (!fs) "进去" else "退出来"}")
        else {
            val r = c.fullscreenCycles(4) { d.key(KeyEvent.VK_F, 'f') }
            c.expect(
                "fullscreen-5x", "播放器里 F 进出全屏一共 5 次：每次都进得去、退得出来，结束后可见窗口里没有 NSToolbarFullScreenWindow（P20）",
                r.ok, values = mapOf("第 2 – 5 次" to r.rows.joinToString(" ; "), "可见窗口（结束时）" to r.windows.joinToString(" ")),
            )
        }
        // 后面的检查从窗口状态开始：上面没过也别让它们全在全屏里跑（2026-11-18 那次换集、进出 10 次都是在全屏里跑的）
        if (d.isFullScreen()) {
            d.toggleFullScreen()
            d.waitFor(6_000) { !d.isFullScreen() }
            Thread.sleep(1_000)
        }

        // 换集（Shift+N）
        val next = c.controller?.snapshot?.value?.next
        if (next == null) c.skip("next-episode", "Shift+N 下一集（P05）", "这一条没有下一集（电影或最后一集）")
        else {
            val id0 = c.controller?.snapshot?.value?.itemId
            val tn = System.currentTimeMillis()
            d.key(KeyEvent.VK_N, 'N', shift = true)
            val switched = d.waitFor(10_000) { c.controller?.snapshot?.value?.itemId.let { it != null && it != id0 } }
            val ff2 = if (switched) c.awaitFirstFrame(tn, 40_000) else null
            val nShot = d.shot("play-next", "Shift+N 换到下一集")
            c.expect("next-episode", "Shift+N 换到下一集并出画面（P05）", switched && ff2 != null, values = mapOf("首帧（毫秒）" to ff2), shots = listOf(nShot))
        }

        // 返回：先到详情页（P10）
        c.wakeControls()
        d.waitFor(3_000) { d.tree().has("player-back") }
        d.tree().tag("player-back")?.let { d.click(it) }
        val toDetail = d.waitFor(5_000) { c.route is Route.Item }
        Thread.sleep(1_200)
        val bShot = d.shot("play-back-detail", "退出播放器回到详情页")
        c.expect("back-to-detail", "点返回钮：退出播放器，回到这一部的详情页（P10）", toDetail, values = mapOf("页面" to c.path), shots = listOf(bShot))

        // 进出 10 次：物理占用不涨（D-mac-4：第 10 次之后比第 1 次之后多 ≤ 50 MB）。每次离开后等软释放走完再量：
        // 3 秒后清画面、层退场，空闲 10 秒内核销毁（:player-apple 的 IDLE_QUIT_MS；内核在的时候还攥着上一片的全部解复用包，
        // 量出来的是「上一片下了多少」，不是泄漏）。分阶段记：Java 堆（GC 后已用）、原生 = 物理占用 − Java 堆已提交
        val fp = ArrayList<Long>()
        val heapUsed = ArrayList<Long>()
        val heapCommitted = ArrayList<Long>()
        val freedAfter = ArrayList<String>()
        var rounds = 0
        val firstFrames = ArrayList<Long?>()
        val phases = ArrayList<String?>()
        val mx = java.lang.management.ManagementFactory.getMemoryMXBean()
        for (r in 1..10) {
            val tr = System.currentTimeMillis()
            c.nav(Route.Play(item.id, startMs = startMsOf(item)), 200)
            firstFrames += c.awaitFirstFrame(tr, 40_000)
            phases += startupPhases(c.lastStartupLine(item.id))
            Thread.sleep(1_000)
            c.wakeControls()
            d.tree().tag("player-back")?.let { d.click(it) } ?: c.back(0)
            d.waitFor(5_000) { !c.path.startsWith("/play") }
            val left = System.currentTimeMillis()
            Thread.sleep(3_800) // 3 秒后层退场
            // 空闲的内核销毁了（读不到任何 mpv 属性）；最多等 30 秒
            val freed = d.waitFor(30_000, 250) { c.mpv("idle-active") == null }
            freedAfter += if (freed) "${(System.currentTimeMillis() - left) / 1000}s" else "没销毁"
            System.gc()
            Thread.sleep(500)
            fp += d.physFootprint()
            mx.heapMemoryUsage.let { heapUsed += it.used; heapCommitted += it.committed }
            rounds++
        }
        val grew = if (fp.size >= 10 && fp.first() > 0 && fp.last() > 0) fp.last() - fp.first() else null
        fun mb(v: Long) = (v / 1024 / 1024).toString()
        c.expect(
            "in-out-10", "进出播放器 10 次：每次都出首帧，物理占用第 10 次比第 1 次多 ≤ 50 MB（离开后等空闲内核销毁再量；P01 / D-mac-4）",
            rounds == 10 && firstFrames.all { it != null } && grew != null && footprintGrowthOk(fp.first(), fp.last()),
            values = mapOf(
                "每次首帧（毫秒，开场 seek 落定后位置在走）" to firstFrames.joinToString(" "),
                "控制器的分段（毫秒，从开播算）" to phases.joinToString(" ; "),
                "物理占用（MB）" to fp.joinToString(" ") { mb(it) },
                "其中 Java 堆已用 / 已提交（MB，GC 后）" to heapUsed.indices.joinToString(" ") { "${mb(heapUsed[it])}/${mb(heapCommitted[it])}" },
                "原生（物理占用 − Java 堆已提交，MB）" to fp.indices.joinToString(" ") { mb(fp[it] - heapCommitted[it]) },
                "离开后空闲内核销毁于" to freedAfter.joinToString(" "),
                "增长（MB）" to grew?.let { "%.1f".format(it / 1024.0 / 1024.0) },
                "可见窗口（结束时）" to d.visibleNsWindows().joinToString(" "),
            ),
        )

        // 开播 5 秒的格式徽章：《沙丘》P7 双层 → 「按 HDR10 播放」；《海王2》P8.1 → 没有这句；详情页徽章照常亮（P12 / G05）
        formatHints(c, "沙丘", expectHdr10Hint = true, tag = "dune")
        formatHints(c, "海王2", expectHdr10Hint = false, tag = "aquaman")
        c.human("hdr-look", "HDR / 杜比视界的画面观感（P12 / V6）", "高光比白色控件亮、有层次、暗部不发灰、不偏紫偏绿：截图存不下 EDR，要在屏幕上看")
        c.human("pip", "画中画（P18）", "在 pip 场景里测（按钮、1 秒进小窗、30 秒帧间隔 / CPU、回到窗口）；小窗里的画面要人看（H1）")
    }

    /** 视频层对画面格：(对得上没有, 视频层, 画面格, 窗口内容)。等动画停下来（连续两次读数一样）再比。 */
    private fun layerVsFrame(c: Ctx): Quad<Boolean, R?, R?, R?> {
        val d = c.d
        var last: R? = null
        var res = Quad<Boolean, R?, R?, R?>(false, null, null, null)
        d.waitFor(4_000, 200) {
            val vl = d.videoLayer()
            val fr = d.tree().tag("player-frame")?.bounds
            val layer = vl?.first
            val stable = layer != null && last != null && layer.maxEdgeDiff(last!!) < 0.5
            last = layer
            res = Quad(layer != null && fr != null && layer.maxEdgeDiff(fr) <= 2.0 && layer.inside(vl.second, 1.0), layer, fr, vl?.second)
            stable && res.first
        }
        return res
    }

    private fun formatHints(c: Ctx, name: String, expectHdr10Hint: Boolean, tag: String) {
        val d = c.d
        val movie = findMovie(c, name)
        if (movie == null) {
            c.skip("$tag-badges", "《$name》的格式徽章和开播提示（P12）", "片库里搜不到《$name》")
            return
        }
        c.nav(Route.Home, 300)
        c.nav(Route.Item(movie.id), 600)
        val badges = d.waitFor(15_000) { d.tree().has("format-badges") }
        Thread.sleep(2_000)
        val t = d.tree()
        val dShot = d.shot("$tag-detail", "《$name》详情页的格式徽章")
        val img = dShot?.let(d::image)
        val k = img?.let(d::shotScale) ?: 2.0
        val each = t.tags { it.startsWith("format-") && it != "format-badges" && it != "format-note" }.map { n ->
            val ch = img?.let { Pixels.meanChroma(it, n.bounds.scaled(k)) }
            val lu = img?.let { Pixels.meanLuma(it, n.bounds.scaled(k)) }
            Triple(n.tag!!.removePrefix("format-"), ch, lu)
        }
        val bright = each.isNotEmpty() && each.all { (_, ch, lu) -> (ch ?: 0.0) >= 18.0 && (lu ?: 0.0) >= 55.0 }
        c.pixelExpect(
            "$tag-detail-badges", "《$name》详情页的格式徽章照常亮（不变灰：每枚的平均色度 ≥ 18、亮度 ≥ 55），详情页不写提示（P12 / G05 / D-mac-6）",
            badges && bright && !t.has("format-note"),
            hasPixels = img != null && each.isNotEmpty(),
            values = mapOf("徽章（色度 / 亮度）" to each.joinToString(" ") { (n, ch, lu) -> "$n=${ch?.let { "%.0f".format(it) }}/${lu?.let { "%.0f".format(it) }}" }, "详情页有提示行" to t.has("format-note")),
            shots = listOf(dShot),
        )
        // 开播：Ready 之后 5 秒内徽章下的那一行
        val t0 = System.currentTimeMillis()
        c.nav(Route.Play(movie.id, startMs = startMsOf(movie)), 100)
        var readyAt: Long? = null
        var hintAt: Long? = null
        var hintText: String? = null
        var noteSeen: String? = null
        var startShot: String? = null
        d.waitFor(40_000, 120) {
            val s = c.controller?.snapshot?.value
            if (readyAt == null && s?.phase == PlayPhase.Ready && s.buffering == false) readyAt = System.currentTimeMillis()
            val note = d.tree().textUnder("format-note")
            if (note != null) noteSeen = note
            if (note != null && note.contains("按 HDR10 播放") && hintAt == null) { hintAt = System.currentTimeMillis(); hintText = note }
            val r = readyAt
            // 截图在提示出来 1 秒后（淡入完了、还在 5 秒里）；没有提示的片在 Ready 之后 1.5 秒
            if (startShot == null && ((hintAt != null && System.currentTimeMillis() - hintAt!! > 1_000) || (!expectHdr10Hint && r != null && System.currentTimeMillis() - r > 1_500))) {
                startShot = d.shot("$tag-start-badges", "《$name》开播：徽章和提示行")
            }
            r != null && System.currentTimeMillis() - r > (if (expectHdr10Hint && hintAt != null && startShot != null) 0L else 6_000L)
        }
        if (startShot == null) startShot = d.shot("$tag-start", "《$name》开播")
        d.waitFor(5_000) { c.mpv("video-params/gamma") != null }
        val sinceReady = if (readyAt != null && hintAt != null) hintAt!! - readyAt!! else null
        val values = mapOf(
            "从进播放页到 Ready（毫秒）" to readyAt?.let { it - t0 },
            "提示行" to (hintText ?: noteSeen),
            "提示出现在 Ready 之后（毫秒）" to sinceReady,
            "video-params" to "${c.mpv("video-params/primaries")}/${c.mpv("video-params/gamma")} colormatrix=${c.mpv("video-params/colormatrix")}",
            "hwdec-current" to c.mpv("hwdec-current"),
            "掉帧" to c.mpv("frame-drop-count"),
        )
        if (expectHdr10Hint) c.expect("$tag-hint", "《$name》（杜比视界 P7 双层）开播 5 秒内徽章下面有「按 HDR10 播放」（P12 / D-mac-6）", readyAt != null && hintAt != null && sinceReady!! <= 5_000, values = values, shots = listOf(startShot))
        else c.expect("$tag-hint", "《$name》（杜比视界 P8.1）开播时没有「按 HDR10」这类提示（P12 / D-mac-6）", readyAt != null && (noteSeen?.contains("这台设备") != true), values = values, shots = listOf(startShot))
        c.back(1_500)
    }

    // ================================================================== 6. 正在播放

    private fun nowPlaying(c: Ctx) {
        val d = c.d
        val item = resumeItem(c) ?: run { c.skip("np", "「正在播放」", "「继续观看」是空的"); return }
        c.nav(Route.Item(item.seriesId ?: item.id), 2_000)
        val t0 = System.currentTimeMillis()
        c.nav(Route.Play(item.id, startMs = startMsOf(item)), 200)
        if (c.awaitFirstFrame(t0) == null) { c.check("np-info", "「正在播放」信息齐全（X11）", Verdict.Fail, "40 秒内没出首帧"); c.back(1_500); return }
        Thread.sleep(3_000) // 封面要从服务器取
        val info = SystemNowPlayingCenter.readBackInfo()
        val rb = SystemNowPlayingCenter.readBack()
        val art = SystemNowPlayingCenter.artworkWidth()
        val snap = c.controller?.snapshot?.value
        val titleOk = info?.get("title") == snap?.title && !snap?.title.isNullOrEmpty()
        c.expect(
            "np-info", "播放中系统的「正在播放」里有片名、副标题、时长、位置、速率、类型和封面，状态「播放」（X11 / MP-6）",
            rb != null && rb.first >= 7 && rb.second == 1L && titleOk && (art ?: 0) > 0 && info?.containsKey("playbackDuration") == true,
            values = mapOf("键数" to rb?.first, "状态（1 播放 2 暂停 3 停止）" to rb?.second, "键" to info?.keys?.joinToString(" "), "片名" to info?.get("title"), "副标题" to info?.get("artist"), "时长（秒）" to info?.get("playbackDuration"), "片名和控制器一致" to titleOk, "封面宽（像素）" to art),
        )
        val cmds = SystemNowPlayingCenter.commandsEnabled()
        c.expect("np-commands", "遥控命令都挂上了（播放 / 暂停 / 切换 / 拖动开着）", cmds != null && listOf("play=1", "pause=1", "togglePlayPause=1", "changePlaybackPosition=1").all { it in cmds }, values = mapOf("命令" to cmds))
        SystemNowPlayingCenter.deliver("guguPause:")
        val paused = d.waitFor(3_000) { c.mpv("pause") == "yes" && c.controller?.clock?.value?.paused == true }
        Thread.sleep(500)
        val rbP = SystemNowPlayingCenter.readBack()
        c.expect("np-pause", "像系统那样发「暂停」：mpv 停了、卡片状态「暂停」", paused && rbP?.second == 2L, values = mapOf("mpv pause" to c.mpv("pause"), "状态" to rbP?.second))
        SystemNowPlayingCenter.deliver("guguToggle:")
        val resumed = d.waitFor(3_000) { c.mpv("pause") == "no" && c.controller?.clock?.value?.paused == false }
        Thread.sleep(500)
        val rbT = SystemNowPlayingCenter.readBack()
        c.expect("np-toggle", "发「播放 / 暂停切换」：mpv 接着放、卡片状态「播放」", resumed && rbT?.second == 1L, values = mapOf("mpv pause" to c.mpv("pause"), "状态" to rbT?.second))
        if (snap?.next == null) c.skip("np-next", "发「下一集」（X11）", "这一条没有下一集（电影或最后一集）；命令开关应是关的", values = mapOf("命令" to cmds))
        else {
            val id0 = c.controller?.snapshot?.value?.itemId
            SystemNowPlayingCenter.deliver("guguNext:")
            val moved = d.waitFor(10_000) { c.controller?.snapshot?.value?.itemId.let { it != null && it != id0 } }
            val ff = if (moved) c.awaitFirstFrame(System.currentTimeMillis(), 40_000) else null
            Thread.sleep(2_500)
            val info2 = SystemNowPlayingCenter.readBackInfo()
            val title2 = c.controller?.snapshot?.value?.title
            c.expect("np-next", "发「下一集」：换到下一集、出画面，卡片上的片名跟着换（X11）", moved && ff != null && info2?.get("title") == title2, values = mapOf("换了一集" to moved, "首帧（毫秒）" to ff, "卡片片名" to info2?.get("title"), "控制器片名" to title2))
        }
        c.skip("np-seek", "从控制中心拖动进度", "拖动命令要系统造的事件对象（带位置），进程里造不出来；命令开着（见上），拖动要人试")
        c.wakeControls()
        d.waitFor(3_000) { d.tree().has("player-back") }
        d.tree().tag("player-back")?.let { d.click(it) } ?: c.back(0)
        d.waitFor(5_000) { !c.path.startsWith("/play") }
        Thread.sleep(1_500)
        val rbL = SystemNowPlayingCenter.readBack()
        val cmdsL = SystemNowPlayingCenter.commandsEnabled()
        c.expect("np-cleared", "离开播放器：卡片清空（0 个键、状态「停止」）、命令摘掉（X11）", rbL?.first == 0 && rbL.second == 3L && cmdsL == null, values = mapOf("键数" to rbL?.first, "状态" to rbL?.second, "命令" to (cmdsL ?: "（没挂）")))
        c.human("np-system-ui", "控制中心 / 菜单栏的「正在播放」卡片、键盘 F8、AirPods（X11）", "系统界面不截、硬件键不模拟：要人看卡片上的片名和封面、按 F8 能暂停 / 继续。开发运行时卡片上的应用名是 java")
    }

    // ================================================================== 令牌存储（M2-5 / D-lead-6；2026-11 起存数据目录的文件，不碰钥匙串）

    private fun keychain(c: Ctx) {
        val d = c.d
        val mode = c.graph.deps.secureMode()
        c.nav(Route.Settings("about"), 1_500)
        val shown = d.waitFor(5_000) { "plain-fallback" in d.tree().allText() }
        val sh = d.shot("keychain-about", "设置 › 关于：令牌存储")
        c.expect("kc-about", "设置 › 关于「令牌存储」= plain-fallback（和 Windows 同一种，T11）", mode == "plain-fallback" && shown, values = mapOf("secureMode" to mode, "关于页上看得见" to shown), shots = listOf(sh))
        val dir = c.host.dataDir
        if (dir == null) { c.skip("kc-file", "登录信息在数据目录的文件里（C03）", "这个构建没有交出会话目录"); return }
        // 只看文件在不在、里面有哪些**键名**（plainKeys 不读值）；不打印任何值
        val file = java.io.File(dir, com.gugaart.desktop.mac.MacSecureStore.PLAIN_FILE)
        val keys = com.gugaart.desktop.mac.MacSecureStore.plainKeys(file)
        val hasServers = keys?.contains(com.gugugaga.core.StorageKeys.SERVERS) == true
        c.expect(
            "kc-file", "登录信息存在数据目录的 gugu-secure-store.json 里（键名含服务器清单，C03）",
            file.isFile && hasServers, values = mapOf("文件在" to file.isFile, "键名" to (keys ?: "（读不出）")),
        )
        // 全程没有调钥匙串接口：真的四个 SecItem* 操作都经 SecurityFrameworkKeychain.query，计数应当一直是 0。
        // 不往钥匙串写任何测试条目，也不读旧条目（一读就可能弹授权框）。
        val calls = com.gugaart.desktop.mac.SecurityFrameworkKeychain.callCount
        c.expect("kc-no-keychain", "这次启动全程没有调钥匙串接口（SecItem* 计数 = 0，不会弹授权框）", calls == 0, values = mapOf("SecItem* 调用次数" to calls))
        val signedIn = c.graph.session.session.value != null
        c.expect(
            "kc-session", "这次启动的登录态是从数据目录的文件读回来的（已登录 + 文件里有服务器清单 + 没调钥匙串）",
            signedIn && hasServers && calls == 0, values = mapOf("已登录" to signedIn),
        )
        c.human(
            "kc-two-builds", "两个不同构建（ad-hoc 签名 / cdhash 不同）之间：不弹钥匙串授权框、仍是登录态（A12 / H1）",
            "运行器只核对本次启动没调钥匙串。跨构建那一次要在构建目录里打两个 .app 起（不装进 /Applications）：启动后 15 秒内 pgrep -x SecurityAgent 一直为空、界面仍是登录态",
        )
        c.back(600)
    }

    // ================================================================== 画中画（MP-7，D-lead-20；release-1.3 A10）

    private fun pip(c: Ctx) {
        val d = c.d
        val eng = c.host.engines
        if (eng == null || !c.host.engineAvailable) { c.skip("pip-capability", "画中画的能力（P18）", "这次运行没有内核"); return }
        val pip = eng.pictureInPicture
        c.expect("pip-capability", "有内核时平台报画中画能力（PlayerPlatform.pictureInPicture != null，P18）", pip != null)
        if (pip == null) return
        val item = resumeItem(c) ?: run { c.skip("pip-enter", "画中画", "「继续观看」是空的"); return }
        c.nav(Route.Item(item.seriesId ?: item.id), 2_000)
        val t0 = System.currentTimeMillis()
        c.nav(Route.Play(item.id, startMs = startMsOf(item)), 200)
        if (c.awaitFirstFrame(t0) == null) { c.check("pip-enter", "画中画", Verdict.Fail, "40 秒内没出首帧"); c.back(1_500); return }
        Thread.sleep(1_500)
        c.wakeControls()
        val button = c.waitTag("player-pip", 4_000)
        val shBtn = d.shot("pip-button", "播放器顶栏：画中画钮（收藏右边）")
        c.expect("pip-button", "播放器顶栏右组有画中画钮（设计稿 st=pip）", button != null, shots = listOf(shBtn))
        if (button == null) { c.back(1_500); return }
        val audio0 = runBlocking { eng.audioReconfigs() }
        d.click(button)
        val entered = d.waitFor(3_000, 50) { pip.active }
        Thread.sleep(400)
        val r1 = eng.pipReport()
        val enterMs = (r1?.get("enterMs") as? Double)
        c.expect("pip-enter", "点钮 → 1 秒内画中画处于活动状态、小窗里有帧（P18）", entered && enterMs != null && enterMs <= 1_000.0, values = mapOf("活动" to entered, "请求 → 有画面（毫秒）" to enterMs?.let { "%.0f".format(it) }, "错误" to r1?.get("error")))
        if (!entered) { c.back(1_500); return }
        val note = c.waitTag("player-pip-note", 3_000)
        val shNote = d.shot("pip-note", "画中画期间：画面那一格是纸面说明")
        c.expect("pip-note", "画中画期间主窗口画面那一格是「画面在小窗里 · 回到这里」（D-lead-20）", note != null && "画面在小窗里" in d.tree().allText(), shots = listOf(shNote))
        // 30 秒：帧间隔 > 100 ms 的次数 ≤ 3、应用进程 CPU 平均 ≤ 60 %
        eng.pipResetStats()
        fun cpuSec() = ProcessHandle.current().info().totalCpuDuration().map { it.toNanos() / 1e9 }.orElse(Double.NaN)
        val cpu0 = cpuSec()
        val w0 = System.nanoTime()
        Thread.sleep(30_000)
        val wall = (System.nanoTime() - w0) / 1e9
        val cpu = (cpuSec() - cpu0) / wall * 100
        val r2 = eng.pipReport()
        val frames = r2?.get("frames") as? Int ?: 0
        val hitches = r2?.get("hitches") as? Int ?: -1
        c.expect("pip-30s", "画中画里 30 秒：帧间隔 > 100 ms 的次数 ≤ 3", frames > 0 && hitches in 0..3, values = mapOf("帧数" to frames, "最长帧间隔（毫秒）" to (r2?.get("maxGapMs") as? Double)?.let { "%.0f".format(it) }, "> 100 ms 的次数" to hitches, "出错帧" to r2?.get("renderErrors")))
        c.expect("pip-cpu", "画中画里 30 秒：应用进程 CPU 平均 ≤ 60 %", cpu.isFinite() && cpu <= 60.0, values = mapOf("CPU %" to "%.0f".format(cpu)))
        // 回到窗口：点说明页上的「回到这里」
        val posBefore = c.mpv("time-pos")?.toDoubleOrNull()
        val tBack = System.nanoTime()
        val back = c.waitTag("player-pip-back", 3_000)
        back?.let { d.click(it) } ?: pip.toggle()
        val left = d.waitFor(5_000, 50) { !pip.active }
        d.waitFor(5_000, 100) { c.mpv("current-vo") == "gpu-next" }
        val backMs = (System.nanoTime() - tBack) / 1e6
        Thread.sleep(1_500)
        c.wakeControls()
        val (inPlace, layer, frame, _) = layerVsFrame(c)
        val posAfter = c.mpv("time-pos")?.toDoubleOrNull()
        val shBack = d.shot("pip-back", "回到窗口")
        val r3 = eng.pipReport()
        c.expect(
            "pip-back", "「回到这里」：画中画结束、视频层回到画面格、位置接着走、还在播（不是关小窗的暂停）",
            // 系统的 did-stop 要等它的动画走完：锁屏时永远不来（实测），所以不拿它判；页面是否回来看 active
            left && inPlace && posBefore != null && posAfter != null && posAfter >= posBefore && c.mpv("pause") == "no",
            values = mapOf("用时（毫秒）" to "%.0f".format(backMs), "vo" to c.mpv("current-vo"), "层" to layer, "画面格" to frame, "位置 前 → 后（秒）" to "$posBefore → $posAfter", "pause" to c.mpv("pause"), "系统说怎么结束的（锁屏时为空）" to r3?.get("lastEnd")),
            shots = listOf(shBack),
        )
        val audio1 = runBlocking { eng.audioReconfigs() }
        c.expect("pip-audio", "进出画中画声音输出没有重开（mpv 的 audio-reconfig 次数不变）", audio0 != null && audio0 == audio1, values = mapOf("前" to audio0, "后" to audio1))
        c.human("pip-close-pauses", "关掉画中画小窗 = 暂停（D-lead-20）", "小窗的关闭钮是系统画的、进程里点不到；判断逻辑有单测（上游 PipSessionState）。H1：人点小窗的 ×，看主窗口回来并且是暂停")
        c.human("pip-window", "画中画小窗里的画面 / 声音、拖到角落；HDR 片在小窗里偏灰（已知问题，D-lead-20）", "系统的小窗按窗口截不到（ADR 0004 §4 实测十六）：H1 人看")
        c.wakeControls()
        d.waitFor(3_000) { d.tree().has("player-back") }
        d.tree().tag("player-back")?.let { d.click(it) } ?: c.back(0)
        d.waitFor(5_000) { !c.path.startsWith("/play") }
    }

    // ================================================================== 7. 出错页（找不到 libmpv）

    private fun errorNoLib(c: Ctx) {
        val d = c.d
        if (c.host.engineAvailable) {
            c.skip("nolib-error-page", "libmpv 指到不存在的路径 → 出错页（P02 / P23）", "这次运行找到了 libmpv；这一场要另起一次应用：-Pgugaart.libmpv=/nonexistent/libmpv.dylib（scripts/accept-mac.mjs 自动另起）")
            return
        }
        val facts = c.graph.deps.player?.platformFacts()
        val item = resumeItem(c) ?: run { c.skip("nolib-error-page", "出错页（P02 / P23）", "「继续观看」是空的"); return }
        c.nav(Route.Item(item.seriesId ?: item.id), 2_000)
        c.nav(Route.Play(item.id, startMs = startMsOf(item)), 300)
        val shown = d.waitFor(20_000) { d.tree().has("player-error") }
        Thread.sleep(1_500)
        val awt = d.ui { java.awt.Window.getWindows().filter { it.isShowing }.map { it.javaClass.simpleName } }
        val ns = d.visibleNsWindows()
        val text = d.tree().textUnder("player-error").orEmpty()
        val sh = d.shot("nolib-error", "libmpv 不存在：出错页")
        c.expect(
            "nolib-error-page", "libmpv 指到不存在的路径：进播放页是出错页，不弹框、不崩（P02 / P23）",
            shown && awt.size == 1 && ns.size == 1,
            values = mapOf("内核" to facts?.present?.joinToString { it.name }, "可见的 AWT 窗口" to awt.joinToString(" "), "可见的 NSWindow" to ns.joinToString(" "), "出错页的字" to text.take(160)),
            shots = listOf(sh),
        )
        // M2-14（D-lead-16）：出错页就用上游 PlayController.NO_ENGINE_MESSAGE，不另写壳里的文案
        if ("这台设备上没有可用的播放内核" in text) c.check("nolib-copy", "出错页写明「这台设备上没有可用的播放内核」（M2-14 / P-mac-17）", Verdict.Pass, values = mapOf("字" to text.take(160)))
        else c.check("nolib-copy", "出错页写明「这台设备上没有可用的播放内核」（M2-14 / P-mac-17）", Verdict.Fail, "出错页文案不是上游 NO_ENGINE_MESSAGE", values = mapOf("字" to text.take(160)))
        c.check("nolib-external", "出错页的「外部播放器」（P23）", Verdict.Skip, "不点：本机的 SenPlayer 不运行；IINA 交接要人试")
        c.back(1_500)
    }

    // ================================================================== 菜单栏 / Dock（M2-4 / M2-10）

    private fun menu(c: Ctx) {
        val d = c.d
        c.reset()
        d.waitFor(10_000) { d.tree().has("masthead-volume") }
        val titles = d.mainMenuItems()
        // 第一个是系统的应用菜单（咕咕嘎嘎），后面七个是我们自己的（D-lead-39：不再自己出第二个「咕咕嘎嘎」；1.5.0 多了「片库」）；
        // 系统可能再挂自动项，只核对：应用菜单只有一个且在最前、我们的七个都在、顺序对
        val ours = listOf("文件", "编辑", "显示", "片库", "播放", "窗口", "帮助")
        val orderOk = ours.all { it in titles } && titles.count { it == "咕咕嘎嘎" } <= 1 &&
            ours.map { titles.indexOf(it) } == ours.map { titles.indexOf(it) }.sorted() && titles.indexOf("文件") == 1
        c.expect("menu-seven", "菜单栏 = 系统应用菜单 + 我们的七个、没有重复的「咕咕嘎嘎」、顺序对（X11 / M2-4，D-lead-39）", orderOk, values = mapOf("菜单" to titles.joinToString(" ")))
        // 应用菜单里有系统的「关于」「设置…」（处理器接上了才可用）；「检查更新…」在「帮助」里
        val appItems = titles.firstOrNull()?.let { d.submenuItems(it) }.orEmpty().filter { it.first.isNotBlank() }
        val aboutOn = appItems.any { (it.first.startsWith("关于") || it.first.startsWith("About")) && it.second }
        val prefsOn = appItems.any { (it.first.startsWith("设置") || it.first.startsWith("偏好设置") || it.first.startsWith("Settings") || it.first.startsWith("Preferences")) && it.second }
        val helpItems = d.submenuItems("帮助").map { it.first }
        c.expect(
            "menu-app-handlers", "系统应用菜单的「关于」「设置…」可用、「检查更新…」在「帮助」里（D-lead-39）",
            aboutOn && prefsOn && "检查更新…" in helpItems,
            values = mapOf("应用菜单" to appItems.joinToString { "${it.first}=${it.second}" }, "帮助" to helpItems.joinToString(" ")),
        )
        // 不播放时「播放」整组不可用
        val playItems = d.submenuItems("播放").filter { it.first.isNotBlank() && it.first != "服务" }
        val ourPlay = playItems.filter { it.first in listOf("播放 / 暂停", "快退 10 秒", "快进 10 秒", "下一集", "播放器全屏") }
        c.expect("menu-play-disabled", "不在播放时「播放」组整组不可用（X11 / M2-4）", ourPlay.isNotEmpty() && ourPlay.all { !it.second }, values = mapOf("播放组" to ourPlay.joinToString { "${it.first}=${it.second}" }))
        // 筛选只在海报墙
        val filterHome = d.submenuItems("显示").firstOrNull { it.first == "筛选" }
        c.expect("menu-filter-home", "首页时「显示 › 筛选」不可用（B10 / M2-4）", filterHome != null && !filterHome.second, values = mapOf("筛选" to filterHome?.second))
        // 菜单命令真的生效：显示 › 片库 换页
        val clickedLib = d.clickMenuItem("显示", "片库")
        val libsOk = clickedLib && d.waitFor(3_000) { c.route is Route.Library || d.tree().has("desktop-libs") }
        c.expect("menu-goto-libs", "「显示 › 片库」真的换到片库（X04 / M2-4）", libsOk, values = mapOf("页面" to c.path))
        // 不在首页时「文件 › 付印新刊」灰着（付印只在首页挂着；以前恒可点、点了静默不动）
        val reprintOff = d.waitFor(3_000) { d.submenuItems("文件").firstOrNull { it.first == "付印新刊" }?.second == false }
        c.expect("menu-reprint-grey", "不在首页时「文件 › 付印新刊」不可用（B05 / M2-4）", reprintOff, values = mapOf("页面" to c.path, "付印新刊" to d.submenuItems("文件").firstOrNull { it.first == "付印新刊" }?.second))
        // 编辑 › 搜索片库… 进搜索
        val clickedSearch = d.clickMenuItem("编辑", "搜索片库…")
        val searchOk = clickedSearch && d.waitFor(3_000) { c.route is Route.Search }
        c.expect("menu-search", "「编辑 › 搜索片库…」进搜索（X04 / M2-4）", searchOk, values = mapOf("页面" to c.path))
        c.reset()
        // 文件 › 付印新刊 期号 +1：先确认回到首页、付印已挂上（VOL. 在、菜单项可用）再点（对照 keys 场景的 F5）
        val atHome = d.waitFor(10_000) { c.route is Route.Home && d.tree().has("desktop-reprint") } &&
            d.waitFor(3_000) { d.submenuItems("文件").firstOrNull { it.first == "付印新刊" }?.second == true }
        fun vol() = d.tree().textUnder("masthead-volume")?.let(::volumeNumber)
        val v0 = vol()
        val clickedReprint = atHome && d.clickMenuItem("文件", "付印新刊")
        val reprintOk = clickedReprint && d.waitFor(4_000) { (vol() ?: -1) == (v0 ?: -9) + 1 }
        c.expect("menu-reprint", "「文件 › 付印新刊」期号 +1（B05 / M2-4）", reprintOk, values = mapOf("回到首页且已挂上" to atHome, "之前" to v0, "之后" to vol()))
        c.reset()
    }

    private fun dock(c: Ctx) {
        val d = c.d
        c.reset()
        d.waitFor(10_000) { d.tree().has("masthead-volume") }
        Thread.sleep(1_500) // 等 Dock 菜单异步装好
        val labels = d.dockMenuLabels()
        val resumeCount = labels.count { it.startsWith("继续观看 · ") }
        val shapeOk = resumeCount <= 3 && labels.takeLast(2) == listOf("搜索…", "付印新刊")
        c.expect("dock-shape", "Dock 菜单 = 继续观看 ≤3 + 搜索 + 付印新刊（X11 / M2-10）", shapeOk && labels.isNotEmpty(), values = mapOf("条目" to labels.joinToString(" | ")))
        if (resumeCount > 0) {
            val clicked = d.clickDockItem(0)
            val playing = clicked && d.waitFor(8_000) { c.path.startsWith("/play") }
            c.expect("dock-resume-play", "点一条「继续观看」进播放器（P24 / M2-10）", playing, values = mapOf("页面" to c.path))
            c.back(1_500)
        } else c.skip("dock-resume-play", "点一条「继续观看」进播放器（P24 / M2-10）", "「继续观看」是空的")
        c.reset()
    }

    // ================================================================== 只读的数据

    private fun resumeItem(c: Ctx): BaseItemDto? = runCatching { runBlocking { c.client().getResume(limit = 1).items.firstOrNull() } }.getOrNull()

    private fun startMsOf(item: BaseItemDto): Long? = item.userData?.playbackPositionTicks?.takeIf { it > 0 }?.let { it / 10_000 }

    /** 收起「称号」/「特别称号」纸片（照 iOS runner 的 closeSlips）：纸片走 SheetHost(onClose)，开着时 Esc 被它吃掉，
     *  搜索页退不出（search-esc）、首页封面里程表也被盖住（arrows-flip）。置空 RankBoard 的弹出态 = 点「收下」；记账在弹出之前已做完。 */
    // ================================================================== 1.5.0 换服务器（三台 Mock）

    /**
     * 三台 Mock（外面起好：第一台由 `-Pgugaart.mockAccount` 静默登录，另两台的地址在环境变量 `GUGAART_ACCEPT_SERVERS`，只认 127.0.0.1 / localhost）。
     * ① 真的走「添加服务器」那一屏加上另两台（地址 → 连接 → 夹具账号 demo / demo → 登录）；② 写 `GUGAART_ACCEPT_STOP` 那个文件，外面的脚本看到就停掉最后一台
     * （当成离线），等它真的连不上；③ 顶栏胶囊 → 下拉，来回切、点离线的那台（不切、那一行下面写原因）；④ ⌘⇧S 打开、按 2 切到第二台；
     * ⑤ 菜单栏「片库 › 换服务器」：列出三台、当前打勾，点一台切过去，点离线的那台 = 下拉打开、写原因。每一步截本窗口。
     */
    private fun servers150(c: Ctx) {
        val d = c.d
        val g = c.graph
        val sw = com.gugaart.ui.servers.sharedServerSwitcher(g)
        fun port(): String? = g.session.session.value?.serverUrl?.substringAfterLast(':')?.takeWhile { it.isDigit() }
        fun rows(): List<AcceptDriver.Node> = d.tree().nodes.filter { it.tag == "server-row" || it.tag == "server-row-current" }
        fun rowOf(p: String): AcceptDriver.Node? = d.tree().let { t -> t.nodes.firstOrNull { (it.tag == "server-row" || it.tag == "server-row-current") && ":$p" in t.textUnder(it) } }
        fun menuOpen() = d.ui { com.gugaart.ui.desktop.DesktopServerMenu.open }
        fun openMenu(): Boolean {
            if (menuOpen()) return true
            d.tree().tag("desktop-server-pill")?.let { d.click(it) }
            return d.waitFor(4_000) { d.tree().has("desktop-server-menu") }
        }
        fun pingsSettled() = d.waitFor(10_000, 200) { sw.entries.value.none { it.ping == com.gugaart.ui.servers.PingState.Checking || it.ping == com.gugaart.ui.servers.PingState.Unknown } }

        c.reset()
        closeSlips(c)
        if (!d.waitFor(20_000) { d.tree().has("desktop-server-pill") }) {
            c.check("servers-pill", "顶栏账号圆钮左边有服务器胶囊", Verdict.Fail, "顶栏没有服务器胶囊", shots = listOf(d.shot("srv-00-no-pill")))
            return
        }
        val first = port()
        val extra = System.getenv("GUGAART_ACCEPT_SERVERS").orEmpty().split(',').map { it.trim() }
            .filter { Regex("""^http://(127\.0\.0\.1|localhost):\d+$""").matches(it) }
        if (extra.size < 2 || first == null) {
            c.skip("servers-add", "加另外两台 Mock", "没给 GUGAART_ACCEPT_SERVERS（两台 127.0.0.1 的 Mock 地址）或没登录第一台：跳过")
            return
        }
        // ---- ① 加两台：真的走「添加服务器」那一屏
        val added = extra.map { url -> url to addMockServer(c, url) }
        val viaUi = added.all { it.second.second == "界面" }
        c.check(
            "servers-add", "「添加服务器」那一屏加上另外两台 Mock（地址 → 连接 → demo / demo → 登录），数据层列表里有三台",
            if (added.all { it.second.first } && d.waitFor(5_000) { sw.entries.value.size >= 3 }) (if (viaUi) Verdict.Pass else Verdict.NeedsHuman) else Verdict.Fail,
            if (viaUi) "" else "界面那条路没走通，退回上游登录接口加的（看「方式」）",
            values = mapOf("结果" to added.joinToString { "${it.first.substringAfterLast(':')}=${it.second.first}" }, "方式" to added.joinToString { "${it.first.substringAfterLast(':')}=${it.second.second}" }, "列表" to sw.entries.value.size),
            shots = listOf(d.shot("srv-01-added", "加完第三台")),
        )
        // ---- ② 停掉最后一台（外面的脚本看到这个文件就停它，只停它自己起的那个进程）
        val offPort = extra.last().substringAfterLast(':')
        System.getenv("GUGAART_ACCEPT_STOP")?.let { java.io.File(it).writeText("stop $offPort\n") }
        val down = d.waitFor(60_000, 500) { !reachable(extra.last()) }
        c.expect("servers-offline-ready", "最后一台 Mock（:$offPort）停掉了，当成离线的那台", down)
        // 回到第一台（日常：从常用的那台开始）
        if (port() != first) {
            sw.entries.value.firstOrNull { ":$first" in it.address }?.let { e -> runBlocking { sw.switchTo(e.serverId, e.userId) } }
            d.waitFor(10_000) { port() == first }
        }
        c.reset()
        closeSlips(c)
        d.waitFor(10_000) { d.tree().has("desktop-server-pill") }
        Thread.sleep(1_500)
        c.expect("servers-pill", "顶栏账号圆钮左边是服务器胶囊（当前服务器名 + 在线小点）", d.tree().has("desktop-server-pill"), values = mapOf("胶囊" to d.tree().textUnder("desktop-server-pill")), shots = listOf(d.shot("srv-02-pill", "顶栏胶囊")))

        // ---- ③ 下拉：三台、在线状态；来回切
        val opened = openMenu()
        pingsSettled()
        Thread.sleep(600)
        val texts = rows().map { d.tree().textUnder(it) }
        c.expect(
            "servers-dropdown", "点胶囊垂下纸片：三台都在，当前那台第一个；在线的写毫秒，停掉的那台写「连不上」", opened && texts.size >= 3 && texts.any { "连不上" in it && ":$offPort" in it } && texts.first().contains(":$first"),
            values = mapOf("行" to texts.joinToString(" | ")), shots = listOf(d.shot("srv-03-dropdown", "下拉")),
        )
        val second = extra.first().substringAfterLast(':')
        val t0 = System.currentTimeMillis()
        rowOf(second)?.let { d.click(it) }
        val switched = d.waitFor(10_000) { port() == second && !menuOpen() }
        c.expect("servers-switch", "点第二台：切过去（会话换成 :$second），下拉收起", switched, values = mapOf("用时 ms" to (System.currentTimeMillis() - t0), "现在" to port()), shots = listOf(d.shot("srv-04-switched", "切到第二台")))
        Thread.sleep(1_200)
        openMenu()
        pingsSettled()
        val t1 = System.currentTimeMillis()
        rowOf(first)?.let { d.click(it) }
        val back = d.waitFor(10_000) { port() == first && !menuOpen() }
        c.expect("servers-switch-back", "再切回第一台（:$first）", back, values = mapOf("用时 ms" to (System.currentTimeMillis() - t1), "现在" to port()), shots = listOf(d.shot("srv-05-switched-back", "切回第一台")))
        Thread.sleep(1_200)

        // ---- 点离线的那台：不切，那一行下面写原因
        openMenu()
        pingsSettled()
        rowOf(offPort)?.let { d.click(it) }
        val noted = d.waitFor(8_000) { d.tree().textUnder("server-note")?.contains(com.gugaart.ui.servers.ServerSwitchCopy.OFFLINE) == true }
        c.expect("servers-offline", "点停掉的那台：不切（还在 :$first）、下拉不收，那一行下面写「连不上，未切换」", noted && port() == first && menuOpen(), values = mapOf("现在" to port()), shots = listOf(d.shot("srv-06-offline", "点离线的那台")))
        d.key(KeyEvent.VK_ESCAPE)
        val escClosed = d.waitFor(3_000) { !menuOpen() }
        c.expect("servers-esc", "Esc 收起下拉", escClosed)

        // ---- ④ ⌘⇧S 打开、按 2 切到第二台
        d.key(KeyEvent.VK_S, cmd = true, shift = true)
        val keyOpened = d.waitFor(4_000) { menuOpen() && d.tree().has("desktop-server-menu") }
        pingsSettled()
        Thread.sleep(500)
        val keyShot = d.shot("srv-07-cmd-shift-s", "⌘⇧S 打开的下拉")
        val row2 = rows().getOrNull(1)?.let { d.tree().textUnder(it) }.orEmpty()
        d.key(KeyEvent.VK_2, '2')
        val want2 = Regex(""":(\d+)""").find(row2)?.groupValues?.get(1)
        val digit = d.waitFor(10_000) { want2 != null && port() == want2 && !menuOpen() }
        c.expect("servers-cmd-shift-s", "⌘⇧S 打开下拉", keyOpened, shots = listOf(keyShot))
        c.expect("servers-digit", "下拉开着按 2 = 切到第二行那台", digit, values = mapOf("第二行" to row2, "现在" to port()), shots = listOf(d.shot("srv-08-digit", "按 2 以后")))
        Thread.sleep(1_200)

        // ---- ⑤ 菜单栏「片库 › 换服务器」
        val sub = d.nestedMenuItems("片库", "换服务器")
        val subOk = sub.count { it.first != "添加服务器…" && it.first.isNotBlank() } >= 3 && sub.any { it.third } && sub.any { it.first == "添加服务器…" } && sub.any { "连不上" in it.first }
        c.expect("servers-menubar-list", "菜单栏「片库 › 换服务器」列出三台、当前那台打勾、停掉的写「连不上」、最后是「添加服务器…」", subOk, values = mapOf("子菜单" to sub.joinToString(" | ") { "${it.first}${if (it.third) "✓" else ""}" }))
        val cur = port()
        val target = sw.entries.value.firstOrNull { !it.isCurrent && it.ping is com.gugaart.ui.servers.PingState.Online }
        val targetLabel = target?.let { e -> com.gugaart.desktop.mac.MacMenuModel.serverItems(sw.entries.value).firstOrNull { it.action == com.gugaart.desktop.mac.MacMenuModel.SERVER_ACTION + e.key }?.label }
        val clicked = targetLabel != null && d.clickNestedMenuItem("片库", "换服务器", targetLabel)
        val viaMenu = clicked && d.waitFor(10_000) { port() != cur && port() == target?.address?.substringAfterLast(':')?.takeWhile { it.isDigit() } }
        c.expect("servers-menubar-switch", "点菜单里的另一台：切过去，下拉不打开", viaMenu && !menuOpen(), values = mapOf("点的" to targetLabel, "之前" to cur, "之后" to port()), shots = listOf(d.shot("srv-09-menubar-switch", "菜单栏切换以后")))
        Thread.sleep(1_200)
        val offLabel = sub.firstOrNull { "连不上" in it.first }?.first
        val before = port()
        val clickedOff = offLabel != null && d.clickNestedMenuItem("片库", "换服务器", offLabel)
        val offViaMenu = clickedOff && d.waitFor(8_000) { menuOpen() && d.tree().textUnder("server-note")?.contains(com.gugaart.ui.servers.ServerSwitchCopy.OFFLINE) == true }
        c.expect("servers-menubar-offline", "菜单里点停掉的那台：不切，顶栏的下拉打开、那一行下面写原因", offViaMenu && port() == before, values = mapOf("现在" to port()), shots = listOf(d.shot("srv-10-menubar-offline", "菜单栏点离线的那台")))
        d.key(KeyEvent.VK_ESCAPE)
        c.reset()
    }

    /**
     * 「添加服务器」那一屏：地址（回车 = 连接；找到了再回车 = 下一步）→ 用户名 / 密码（Mock 的夹具账号，密码框里回车 = 登录）。只给 127.0.0.1 的 Mock 用。
     * 界面那条路没走通（找不到框、没连上）就退回上游同一个登录接口 `SessionManager.signIn`（登录页最后调的就是它），记下走的是哪条路和卡在哪一步。
     */
    private fun addMockServer(c: Ctx, url: String): Pair<Boolean, String> {
        val d = c.d
        val g = c.graph
        require(Regex("""^http://(127\.0\.0\.1|localhost):\d+$""").matches(url)) { "只往本机的 Mock 里输夹具账号" }
        val p = url.substringAfterLast(':')
        fun signedIn() = g.session.session.value?.serverUrl?.contains(":$p") == true
        var stuck = ""
        // 界面那条路最多走两遍（刚登录完的几秒里称号纸片可能正好弹出来，吃掉一次点击）
        for (attempt in 1..2) {
            if (signedIn()) break
            if (attempt > 1) { c.nav(Route.Home, 600); Thread.sleep(1_500) }
            uiAdd(c, url) { stuck = "第 $attempt 遍：$it" }
            if (d.waitFor(1_000) { signedIn() }) break
        }
        if (signedIn()) return true to "界面"
        runCatching { runBlocking { g.session.signIn(com.gugugaga.app.session.SignInInput(server = url, username = "demo", password = "demo")) } }
        return d.waitFor(10_000) { signedIn() } to "接口（界面卡在：$stuck）"
    }

    /** [addMockServer] 的界面那一遍；卡住就把卡在哪一步交给 [stuck]。 */
    private fun uiAdd(c: Ctx, url: String, stuck: (String) -> Unit) {
        val d = c.d
        val g = c.graph
        val p = url.substringAfterLast(':')
        fun signedIn() = g.session.session.value?.serverUrl?.contains(":$p") == true
        fun focusAndType(tag: String, text: String): Boolean {
            // 称号纸片（新账号登录后几秒异步弹出）会盖在登录页上、吃掉点击：每一步之前先收掉
            closeSlips(c)
            val n = c.waitTag(tag, 8_000) ?: return false
            d.click(n)
            Thread.sleep(250)
            if (d.tree().focused()?.tag != tag) d.requestFocusBySemantics(tag)
            Thread.sleep(250)
            // 框里原来的字先清掉（⌘A 再删）
            d.key(KeyEvent.VK_A, cmd = true)
            d.key(KeyEvent.VK_BACK_SPACE)
            d.type(text)
            return true
        }
        run ui@{
            closeSlips(c)
            c.nav(Route.Servers, 900)
            // 已登录时这一屏先是已保存的列表：点「添加服务器」进地址那一步
            c.waitTag("desktop-login-add", 5_000)?.let { d.click(it) }
            if (!focusAndType("desktop-login-address", url)) { stuck("找不到地址框"); return@ui }
            d.key(KeyEvent.VK_ENTER)
            if (!d.waitFor(15_000) { d.tree().has("desktop-login-user") || d.tree().has("desktop-login-found") }) {
                stuck("地址那一步没往下走：" + (d.tree().textUnder("desktop-login-add-note") ?: ""))
                return@ui
            }
            if (!d.tree().has("desktop-login-user")) {
                closeSlips(c)
                c.waitTag("desktop-login-connect", 3_000)?.let { d.click(it) }
                if (!d.waitFor(8_000) { d.tree().has("desktop-login-user") }) { stuck("找到了服务器，但没进登录那一步"); return@ui }
            }
            if (!focusAndType("desktop-login-user", "demo")) { stuck("找不到用户名框"); return@ui }
            if (!focusAndType("desktop-login-password", "demo")) { stuck("找不到密码框"); return@ui }
            d.key(KeyEvent.VK_ENTER)
            if (!d.waitFor(15_000) { signedIn() }) stuck("登录没成：" + (d.tree().textUnder("desktop-login-fail") ?: ""))
        }
    }

    /** 这台 Mock 还连得上吗（公开端点，1.5 秒超时）。 */
    private fun reachable(url: String): Boolean = runCatching {
        val conn = java.net.URI("$url/System/Info/Public").toURL().openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 1_500
        conn.readTimeout = 1_500
        try { conn.responseCode in 200..299 } finally { conn.disconnect() }
    }.getOrDefault(false)

    private fun closeSlips(c: Ctx) {
        repeat(3) {
            c.d.ui {
                com.gugaart.ui.screens.me.RankBoard.news = null
                com.gugaart.ui.screens.me.RankBoard.special = null
            }
            Thread.sleep(300)
        }
    }

    private fun findMovie(c: Ctx, name: String): BaseItemDto? = runCatching {
        val hits = runBlocking { c.client().searchItems(name).items }.filter { it.type == "Movie" }
        hits.firstOrNull { it.name == name } ?: hits.firstOrNull { it.name.startsWith(name) } ?: hits.firstOrNull { name in it.name }
    }.getOrNull()

    /** 两季以上的剧：先看「继续观看」那一条的剧，再看片库里的前 40 部剧（只读）。 */
    private fun multiSeasonSeries(c: Ctx): BaseItemDto? = runCatching {
        val client = c.client()
        fun seasons(id: String) = runCatching { runBlocking { client.getSeasons(id).items.count { (it.indexNumber ?: 0) > 0 } } }.getOrDefault(0)
        resumeItem(c)?.seriesId?.let { sid -> if (seasons(sid) >= 2) return@runCatching runBlocking { client.getItem(sid) } }
        val list = runBlocking { client.getItems(LibraryQuery(includeItemTypes = listOf("Series"), recursive = true, limit = 40)).items }
        list.take(12).firstOrNull { seasons(it.id) >= 2 }
    }.getOrNull()

    // ================================================================== 1.3.1 内嵌字幕首开（主理人报：《谷雨》这类）

    /** 只读找片 + 播放（会上报进度，主理人已接受）；不点收藏 / 已看 / 管理。 */
    private fun subsFirst(c: Ctx) {
        val d = c.d
        c.reset()
        val picked = runBlocking {
            val cl = c.client()
            val named = cl.getItems(LibraryQuery(searchTerm = "谷雨", includeItemTypes = listOf("Movie", "Series"), recursive = true, limit = 8)).items
            named.firstOrNull() ?: cl.getItems(LibraryQuery(includeItemTypes = listOf("Movie"), recursive = true, limit = 40)).items.firstOrNull()
        }
        if (picked == null) {
            c.skip("subs-first-sid", "带内嵌字幕的片第一次开播就有字幕（1.3.1）", "库里没找到可播的条目")
            return
        }
        // 剧集要落到第一集：Series 本身没有可播的媒体源
        val playId = if (picked.type == "Series") runBlocking {
            c.client().getItems(LibraryQuery(parentId = picked.id, includeItemTypes = listOf("Episode"), recursive = true, limit = 1)).items.firstOrNull()?.id
        } ?: picked.id else picked.id
        c.nav(Route.Play(playId), 1_500)
        val first = c.awaitFirstFrame(System.currentTimeMillis())
        val streams = c.controller?.snapshot?.value?.resolved?.mediaSource?.mediaStreams.orEmpty()
        val embyEmb = streams.filter { it.type == "Subtitle" && it.isExternal != true }
        val engSubs = c.controller?.engineFlow?.value?.tracks?.value?.subtitle.orEmpty().filter { !it.external }
        val embeddedIdx = if (embyEmb.isNotEmpty()) embyEmb.map { it.index } else engSubs.indices.toList()
        val embeddedDesc = if (embyEmb.isNotEmpty()) embyEmb.joinToString { "${it.index}:${it.language}" } else engSubs.joinToString { "${it.id}:${it.lang}:${it.label}" }
        val audioLang = streams.firstOrNull { it.type == "Audio" }?.language
        if (embeddedIdx.isEmpty()) {
            val engTracks = c.controller?.engineFlow?.value?.tracks?.value
            val sh0 = d.shot("subs-first-nostreams", "Emby 流表为空时的播放器（看字幕面板有没有轨）")
            c.skip(
                "subs-first-sid", "带内嵌字幕的片第一次开播就有字幕（1.3.1）", "这条片源 Emby 流表里没有内嵌字幕轨（${picked.name}）",
                values = mapOf(
                    "片" to picked.name, "类型" to picked.type, "源" to c.controller?.snapshot?.value?.resolved?.mediaSource?.id,
                    "放法" to c.controller?.snapshot?.value?.resolved?.mode,
                    "Emby流" to streams.joinToString { "${it.index}:${it.type}:${it.language}:ext=${it.isExternal}" },
                    "内核字幕轨" to engTracks?.subtitle?.joinToString { "${it.id}:${it.lang}:${it.label}:ext=${it.external}" },
                    "内核sid" to c.mpv("sid"),
                    "面板选项" to c.controller?.snapshot?.value?.subtitles?.joinToString { "${it.index}:${it.label}" },
                ),
                shots = listOf(sh0),
            )
            c.back(1_500)
            return
        }
        Thread.sleep(2_000)
        val sid = c.mpv("sid")
        val idx = c.controller?.snapshot?.value?.subtitleIndex
        val sh = d.shot("subs-first", "第一次开播：内嵌字幕应当在画面上（不用开关一次）")
        // 片头logo卡没有对白：再等一段到对白处拍一张，字幕真的在画面上才看得見
        Thread.sleep(18_000)
        val shLate = d.shot("subs-first-late", "开播约 20 秒：对白处字幕在画面上")
        c.expect(
            "subs-first-sid", "带内嵌字幕的片第一次开播内核就选中字幕轨（sid ≠ no）", sid != null && sid != "no",
            values = mapOf("片" to picked.name, "音轨语言" to audioLang, "sid" to sid, "控制器 subtitleIndex" to idx, "内嵌轨" to embeddedDesc, "首帧ms" to first),
            shots = listOf(sh, shLate),
        )
        c.expect("subs-first-consistent", "控制器的字幕选择与内核一致（不是「界面开着、内核没开」）", idx != null && sid != null && sid != "no", values = mapOf("subtitleIndex" to idx, "sid" to sid))
        // 用户报的 workaround：关一次再开——修好之后它应当仍然正常
        c.controller?.selectSubtitle(null)
        Thread.sleep(800)
        val offSid = c.mpv("sid")
        c.controller?.selectSubtitle(embeddedIdx.first())
        Thread.sleep(2_000)
        val onSid = c.mpv("sid")
        val sh2 = d.shot("subs-toggle", "关一次再开：字幕回来")
        val rNow = c.controller?.snapshot?.value?.resolved
        c.expect(
            "subs-toggle", "关一次再开：sid 先 no 再回到数字（workaround 仍有效）", offSid == "no" && onSid != null && onSid != "no",
            values = mapOf(
                "关" to offSid, "开" to onSid, "开后index" to c.controller?.snapshot?.value?.subtitleIndex,
                "计划" to com.gugugaga.app.player.Tracks.planSubtitleSwitch(rNow?.mediaSource, rNow, c.controller?.engineFlow?.value?.id ?: com.gugugaga.core.EngineId.Mpv, embeddedIdx.first()).toString(),
                "内核轨" to c.controller?.engineFlow?.value?.tracks?.value?.subtitle?.joinToString { "${it.id}:ext=${it.external}" },
            ),
            shots = listOf(sh2),
        )
        // 底部功能词条（1.3.1 修）：开 / 关 / 换轨各一张，词条应与实际选中的轨一致（面板收起、宽窗才写值）
        fun stripSel(): Int? = c.controller?.snapshot?.value?.subtitles?.firstOrNull { it.selected }?.index
        // 桌面版式窄窗（验收窗 1100pt）功能词收成图标、值还截断——拍字幕面板的选中项最清楚（面板与词条同一套选中状态）
        fun subPanelShot(name: String, label: String): String? {
            c.wakeControls()
            if (!d.tree().has("panel-sub")) {
                d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.startsWith("字幕") } }?.let { d.click(it) }
                d.waitFor(3_000) { d.tree().has("panel-sub") }
            }
            Thread.sleep(700)
            return d.shot(name, label)
        }
        val onIdx = c.controller?.snapshot?.value?.subtitleIndex
        val shOn = subPanelShot("strip-on", "字幕面板·开：选中项应是实际选中的轨")
        c.expect("strip-on", "词条/面板·开：选中项与 subtitleIndex 一致", onIdx != null && stripSel() == onIdx, values = mapOf("subtitleIndex" to onIdx, "选中项" to stripSel()), shots = listOf(shOn))
        c.controller?.selectSubtitle(null); Thread.sleep(1_000)
        val shOff = subPanelShot("strip-off", "字幕面板·关：选中项应是「关闭」")
        c.expect("strip-off", "词条/面板·关：subtitleIndex 为空", c.controller?.snapshot?.value?.subtitleIndex == null, values = mapOf("sid" to c.mpv("sid")), shots = listOf(shOff))
        // 换轨要 ≥2 条字幕轨：当前片不够就找一部电影（真服务器只读 + 播放）；放在最后，免得影响上面的开关 workaround
        var swOpts = c.controller?.snapshot?.value?.subtitles.orEmpty().filter { it.index != null }
        if (swOpts.size < 2) {
            val movies = runBlocking { c.client().getItems(LibraryQuery(includeItemTypes = listOf("Movie"), recursive = true, limit = 30)).items }
            for (m in movies.take(6)) {
                c.nav(Route.Play(m.id), 1_500); c.awaitFirstFrame(System.currentTimeMillis())
                val opts = c.controller?.snapshot?.value?.subtitles.orEmpty().filter { it.index != null }
                if (opts.size >= 2) { swOpts = opts; break }
                c.back(1_000)
            }
        }
        val target = swOpts.firstOrNull { it.index != null && it.index != c.controller?.snapshot?.value?.subtitleIndex }?.index
        if (target != null) {
            c.controller?.selectSubtitle(target); Thread.sleep(1_500)
            val shSw = subPanelShot("strip-switch", "字幕面板·换轨：选中项应是新轨")
            c.expect("strip-switch", "词条/面板·换轨：subtitleIndex = 新轨", c.controller?.snapshot?.value?.subtitleIndex == target, values = mapOf("换到" to target, "选中项" to stripSel()), shots = listOf(shSw))
        } else {
            c.skip("strip-switch", "词条·换轨", "前几部电影里没找到 ≥2 条字幕轨的片（iOS 侧已验换轨）")
        }
        c.back(1_500)
    }

    // ================================================================== 切字幕传到内核（dc934d0a）

    /** 有内嵌字幕的片，选外挂 SRT 也要传到内核（旧逻辑过滤侧车，sid 停 no）。外挂 → 内嵌 → 关，各读 sid + 拍字幕面板。Mock 上保证有「内嵌+外挂」的片。 */
    private fun subSwitch(c: Ctx) {
        val d = c.d
        c.reset()
        val movies = runBlocking { c.client().getItems(LibraryQuery(includeItemTypes = listOf("Movie"), recursive = true, limit = 80)).items }
        var picked: BaseItemDto? = null
        var extIdx: Int? = null
        var embIdx: Int? = null
        for (m in movies.take(14)) {
            c.nav(Route.Play(m.id), 1_200)
            c.awaitFirstFrame(System.currentTimeMillis())
            val st = c.controller?.snapshot?.value?.resolved?.mediaSource?.mediaStreams.orEmpty()
            val vc = st.firstOrNull { it.type == "Video" }?.codec
            val emb = st.firstOrNull { it.type == "Subtitle" && it.isExternal != true }
            val ext = st.firstOrNull { it.type == "Subtitle" && it.isExternal == true && it.language == "eng" }
                ?: st.firstOrNull { it.type == "Subtitle" && it.isExternal == true }
            if (vc == "h264" && emb != null && ext != null) { picked = m; embIdx = emb.index; extIdx = ext.index; break }
            c.back(800)
        }
        if (picked == null || extIdx == null || embIdx == null) {
            c.skip("sub-switch-external", "切字幕传到内核（dc934d0a）", "库里没找到「内嵌+外挂」的 h264 片（用 accept-mac.mjs --mock）")
            return
        }
        val movie = picked
        Thread.sleep(1_500)
        fun tracks() = c.controller?.engineFlow?.value?.tracks?.value?.subtitle.orEmpty()
        fun state() = "idx=${c.controller?.snapshot?.value?.subtitleIndex} sid=${c.mpv("sid")} 轨=${tracks().joinToString { "${it.id}:${it.lang}:ext=${it.external}" }}"
        fun panelShot(name: String, label: String): String? {
            c.wakeControls()
            if (!d.tree().has("panel-sub")) {
                d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.startsWith("字幕") } }?.let { d.click(it) }
                d.waitFor(3_000) { d.tree().has("panel-sub") }
            }
            Thread.sleep(600)
            return d.shot(name, label)
        }
        // 1. 选外挂 SRT：内核必须拿到这条轨（sid≠no、track-list 含外挂轨），画面出字
        c.controller?.selectSubtitle(extIdx); Thread.sleep(1_500)
        c.controller?.seekTo(2_000.0); Thread.sleep(1_800)
        val shExt = panelShot("sub-switch-external", "选外挂 SRT：画面出字、面板选中外挂轨")
        val extSid = c.mpv("sid")
        c.expect(
            "sub-switch-external", "有内嵌轨时选外挂 SRT 仍传到内核（dc934d0a）：sid≠no、内核含外挂轨",
            extSid != null && extSid != "no" && c.controller?.snapshot?.value?.subtitleIndex == extIdx && tracks().any { it.external },
            values = mapOf("片" to movie.name, "外挂idx" to extIdx, "内嵌idx" to embIdx, "sid" to extSid, "状态" to state()), shots = listOf(shExt),
        )
        // 2. 换内嵌 ASS：sid≠no，画面出字
        c.controller?.selectSubtitle(embIdx); Thread.sleep(1_500)
        c.controller?.seekTo(2_000.0); Thread.sleep(1_200)
        val shEmb = panelShot("sub-switch-embedded", "换内嵌 ASS：画面出字")
        val embSid = c.mpv("sid")
        c.expect("sub-switch-embedded", "换内嵌 ASS：sid≠no、画面出字", embSid != null && embSid != "no" && c.controller?.snapshot?.value?.subtitleIndex == embIdx, values = mapOf("sid" to embSid, "状态" to state()), shots = listOf(shEmb))
        // 3. 关字幕：sid=no，画面无字
        c.controller?.selectSubtitle(null); Thread.sleep(1_200)
        val shOff = panelShot("sub-switch-off", "关字幕：画面无字、面板选中「关闭」")
        val offSid = c.mpv("sid")
        c.expect("sub-switch-off", "关字幕：sid=no、画面无字", offSid == "no" && c.controller?.snapshot?.value?.subtitleIndex == null, values = mapOf("sid" to offSid, "状态" to state()), shots = listOf(shOff))
        c.back(1_200)
    }

    // ================================================================== 1.3.1 画质增强（Anime4K / FSR）

    /** 只读找片 + 播放（会上报进度，主理人已接受）；不点收藏 / 已看 / 管理。跑完把着色器偏好还原成「关」。 */
    private fun shader(c: Ctx) {
        val d = c.d
        c.reset()
        val picked = runBlocking {
            val cl = c.client()
            val named = cl.getItems(LibraryQuery(searchTerm = "围棋少年", includeItemTypes = listOf("Series", "Movie"), recursive = true, limit = 5)).items
            named.firstOrNull() ?: cl.getItems(LibraryQuery(includeItemTypes = listOf("Series"), recursive = true, limit = 20)).items.firstOrNull()
        }
        if (picked == null) {
            c.skip("shader-a4k", "画质面板的着色器增强（1.3.1）", "库里没找到可播的动画 / 剧集")
            return
        }
        val playId = if (picked.type == "Series") runBlocking {
            c.client().getItems(LibraryQuery(parentId = picked.id, includeItemTypes = listOf("Episode"), recursive = true, limit = 1)).items.firstOrNull()?.id
        } ?: picked.id else picked.id
        fun openQuality(): Boolean {
            d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.contains("画质") } }?.let { d.click(it) }
            return d.waitFor(4_000) { d.tree().has("panel-quality") }
        }
        fun pickOption(label: String): Boolean {
            fun find() = d.tree().nodes.firstOrNull { (it.tag == "panel-option" || it.tag == "panel-option-on") && it.desc.any { s -> s.contains(label) } }
            val panel = d.tree().tag("panel-quality") ?: return false
            var n = find() ?: return false
            // 语义树给的是不裁剪的位置：面板滚viewport 外的行点了会落空——先滚进可见区再点
            if (n.bounds.cy > panel.bounds.y + panel.bounds.h - 24) {
                d.wheel(panel.bounds.cx, panel.bounds.cy, 3)
                n = find() ?: return false
            } else if (n.bounds.cy < panel.bounds.y + 24) {
                d.wheel(panel.bounds.cx, panel.bounds.cy, -3)
                n = find() ?: return false
            }
            d.click(n)
            return true
        }
        fun shaders(): String = c.mpv("glsl-shaders").orEmpty()
        fun snap(name: String, label: String): String? {
            var s = d.shot(name, label)
            if (s == null) { d.activate(); Thread.sleep(900); s = d.shot(name, label) }
            return s
        }
        fun enter() {
            c.nav(Route.Play(playId), 1_500)
            c.awaitFirstFrame(System.currentTimeMillis())
        }
        enter()
        val panelOk = openQuality()
        val hasSection = panelOk && d.tree().nodes.any { it.desc.any { s -> s.contains("Anime4K 模式 A") } }
        c.expect("shader-entry", "mpv 系内核的画质面板有着色器单选（关 / Anime4K A·B·C / FSR）", hasSection, values = mapOf("片" to picked.name, "内核" to c.controller?.engineFlow?.value?.id, "面板开了" to panelOk))
        if (!hasSection) {
            c.back(1_500)
            return
        }
        val drops0 = c.mpv("frame-drop-count")?.toIntOrNull() ?: 0
        fun shaderPaths(): List<String> = shaders().split(":").filter { it.isNotBlank() }
        val clickedA = pickOption("Anime4K 模式 A")
        val aOk = clickedA && d.waitFor(8_000) { shaderPaths().size == 6 }
        Thread.sleep(6_000)
        val dropsA = (c.mpv("frame-drop-count")?.toIntOrNull() ?: 0) - drops0
        val shA = snap("shader-a4k", "Anime4K 模式 A 开着")
        c.expect(
            "shader-a4k", "选 Anime4K 模式 A：glsl-shaders 下发官方 A 链（6 个文件）", aOk,
            values = mapOf("链长" to shaderPaths().size, "6秒掉帧" to dropsA, "点到了" to clickedA), shots = listOf(shA),
        )
        val clickedF = pickOption("FSR")
        val fOk = clickedF && d.waitFor(8_000) { shaderPaths().size == 1 }
        Thread.sleep(6_000)
        val dropsF = (c.mpv("frame-drop-count")?.toIntOrNull() ?: 0) - drops0 - dropsA
        val shF = snap("shader-fsr", "FSR 开着")
        c.expect(
            "shader-fsr", "选 FSR：glsl-shaders 只剩 1 个文件", fOk,
            values = mapOf("链长" to shaderPaths().size, "6秒掉帧" to dropsF, "点到了" to clickedF, "面板还在" to d.tree().has("panel-quality"), "选中" to d.tree().nodes.filter { it.tag == "panel-option-on" }.flatMap { it.desc }), shots = listOf(shF),
        )
        val clickedOff = pickOption("关")
        val offOk = clickedOff && d.waitFor(8_000) { shaders().isEmpty() }
        val shOff = snap("shader-off", "关掉着色器：恢复原画面")
        c.expect("shader-off", "选关：glsl-shaders 清空", offOk, values = mapOf("glsl-shaders" to shaders()), shots = listOf(shOff))
        // 记住选择：再开 FSR，退出播放器重进，应当沿用
        pickOption("FSR")
        d.waitFor(8_000) { shaderPaths().size == 1 }
        c.back(2_000)
        enter()
        openQuality()
        val selFsr = d.tree().nodes.any { it.tag == "panel-option-on" && it.desc.any { s -> s.contains("FSR") } }
        val persist = selFsr && shaderPaths().size == 1
        val shP = snap("shader-persist", "重开播放器：沿用上次选的 FSR")
        c.expect("shader-persist", "重开播放器沿用上次选择（FSR）", persist, values = mapOf("链长" to shaderPaths().size, "面板选中FSR" to selFsr), shots = listOf(shP))
        // 还原成关：别把主理人的偏好留在 FSR
        openQuality()
        pickOption("关")
        d.waitFor(8_000) { shaders().isEmpty() }
        c.human("shader-look", "Anime4K / FSR 的画面观感与掉帧体感（截图存不下 EDR）", "看 shader-a4k / shader-fsr / shader-off 三张：线条锐度、噪点、暗部与掉帧体感", shots = listOf(shA, shF, shOff))
        c.back(1_500)
    }

    // ================================================================== 1.4.0 字幕搜索（docs/subtitle-search.md）

    /**
     * 只在 Mock 上跑：选中服务器源 = 让服务器下载字幕、存进 Emby（写服务器）；主理人的服务器一律 skip。
     * 迅雷 / 射手（在线源，设置里默认开）这里真的会问外网：Mock 样片的文件名 + 指纹，几次请求。
     */
    private fun subtitleSearch(c: Ctx) {
        val d = c.d
        val board = com.gugaart.ui.subtitle.SubtitleSearchBoard
        c.reset()
        val client = runCatching { c.client() }.getOrNull()
        val base = client?.address?.baseUrl.orEmpty()
        if (client == null || !(base.startsWith("http://127.0.0.1:") || base.startsWith("http://localhost:"))) {
            c.skip("subsearch-open", "字幕面板「搜索字幕…」打开搜索栏（1.4.0）", "不是 Mock：这一项会让服务器下载字幕（写服务器），加 --mock 再跑")
            return
        }
        val ep = runCatching { runBlocking { client.getItems(LibraryQuery(includeItemTypes = listOf("Episode"), recursive = true, limit = 8)).items } }.getOrNull()?.firstOrNull()
        if (ep == null) {
            c.skip("subsearch-open", "字幕面板「搜索字幕…」打开搜索栏（1.4.0）", "Mock 里没找到单集")
            return
        }
        c.nav(Route.Play(ep.id), 1_500)
        val first = c.awaitFirstFrame(System.currentTimeMillis())
        // 真点：底部「字幕」词 → 面板最后一行「搜索字幕…」
        c.wakeControls()
        if (!d.tree().has("panel-sub")) {
            d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.startsWith("字幕") } }?.let { d.click(it) }
            d.waitFor(3_000) { d.tree().has("panel-sub") }
        }
        Thread.sleep(500)
        val link = d.tree().nodes.firstOrNull { n -> n.desc.any { it == "搜索字幕…" } }
        link?.let { d.click(it) }
        val opened = d.waitFor(5_000) { d.tree().has("sub-search") }
        // 三路搜完（在线源要问外网，最多等 60 秒）
        d.waitFor(60_000, pollMs = 300) { board.session?.let { !it.searching && it.server != com.gugaart.ui.subtitle.SourceState.Idle } == true }
        Thread.sleep(800)
        val s = board.session
        val shOpen = d.shot("subsearch-open", "搜索栏：状态行 + 结果（服务器 / 迅雷 / 射手），画面在右边照常放")
        c.expect(
            "subsearch-open", "字幕面板「搜索字幕…」打开搜索栏，服务器源回了 Mock 的 3 条（1.4.0）",
            opened && s != null && s.server == com.gugaart.ui.subtitle.SourceState.Done(3),
            values = mapOf(
                "片" to ep.name, "首帧ms" to first, "点到链接" to (link != null),
                "服务器" to s?.server, "迅雷" to s?.thunder, "射手" to s?.shooter, "指纹" to s?.print?.let { com.gugaart.ui.subtitle.printLine(it) },
                "结果" to s?.hits?.joinToString(" | ") { "${it.source.label}:${it.lang.short}:${it.match.label}" },
                "画面让位矩形" to c.controller?.engineFlow?.value?.debugInfo()?.toString()?.take(120),
            ),
            shots = listOf(shOpen),
        )
        c.expect("subsearch-print", "取到了文件指纹（经 Range 读 Mock 的原始流，只读几段）", s?.print is com.gugaart.ui.subtitle.PrintState.Got, values = mapOf("指纹" to s?.print?.let { com.gugaart.ui.subtitle.printLine(it) }))
        if (s == null) {
            c.back(1_500)
            return
        }
        // ---- A：服务器源（指纹精确那条）→ 进 Emby → 播放器选上
        val before = c.controller?.snapshot?.value?.subtitleIndex
        val iEmby = s.hits.indexOfFirst { it.toEmby }
        val row = d.tree().tag("sub-hit-$iEmby")
        row?.let { d.click(it) }
        val closed = d.waitFor(30_000, pollMs = 200) { board.session == null || s.apply is com.gugaart.ui.subtitle.ApplyState.Failed }
        val picked = d.waitFor(15_000) { (c.controller?.snapshot?.value?.subtitleIndex ?: -1).let { it != before && it in 0 until com.gugaart.ui.subtitle.LocalSubtitles.BASE_INDEX } && c.controller?.snapshot?.value?.phase == PlayPhase.Ready }
        val sid = d.waitFor(8_000) { c.mpv("sid").let { it != null && it != "no" } }
        // 字幕文本（Mock 每 4 秒一句「【服务器下载的字幕】第 n 句」）：等它出现在画面上
        val text = d.waitFor(12_000, pollMs = 250) { c.mpv("sub-text").orEmpty().contains("服务器下载的字幕") }
        val shEmby = d.shot("subsearch-emby", "选了服务器源：已存进 Emby、播放器选上，画面上是服务器下载的字幕")
        val newIdx = c.controller?.snapshot?.value?.subtitleIndex
        val onServer = newIdx?.let { idx -> runCatching { runBlocking { client.getItem(ep.id).mediaSources.first().mediaStreams.any { it.index == idx && it.isExternal == true } } }.getOrNull() } == true
        c.expect(
            "subsearch-emby", "选服务器源的一条：服务器下载并存进 Emby（条目多了一条外挂字幕），播放器自动选上它，画面上有它的字",
            closed && picked && sid && text && onServer,
            values = mapOf(
                "点到行" to (row != null), "栏收起" to closed, "选上" to picked, "之前" to before, "之后" to newIdx, "sid" to c.mpv("sid"),
                "sub-text" to c.mpv("sub-text")?.take(60), "服务器上有这条外挂" to onServer, "失败" to (s.apply as? com.gugaart.ui.subtitle.ApplyState.Failed)?.why,
                "字幕顶栏" to c.controller?.snapshot?.value?.subtitles?.firstOrNull { it.selected }?.label,
            ),
            shots = listOf(shEmby),
        )
        // ---- B：迅雷 / 射手（仅本次播放）：再打开一次，选第一条不进 Emby 的
        c.wakeControls()
        if (!d.tree().has("panel-sub")) {
            d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { x -> x.startsWith("字幕") } }?.let { d.click(it) }
            d.waitFor(3_000) { d.tree().has("panel-sub") }
        }
        Thread.sleep(500)
        d.tree().nodes.firstOrNull { n -> n.desc.any { it == "搜索字幕…" } }?.let { d.click(it) }
        d.waitFor(5_000) { d.tree().has("sub-search") }
        d.waitFor(60_000, pollMs = 300) { board.session?.let { !it.searching && it.server != com.gugaart.ui.subtitle.SourceState.Idle } == true }
        val s2 = board.session
        val iLocal = s2?.hits?.indexOfFirst { !it.toEmby } ?: -1
        if (s2 == null || iLocal < 0) {
            c.skip("subsearch-local", "选迅雷 / 射手的一条：下载到本机，作为外挂轨加载（仅本次播放）", "迅雷 / 射手这次对 Mock 样片没有结果（或没连上）", values = mapOf("迅雷" to s2?.thunder, "射手" to s2?.shooter))
            board.close()
            c.back(1_500)
            return
        }
        val beforeLocal = c.controller?.snapshot?.value?.subtitleIndex
        // 行可能在栏的下半截：先滚到它（语义树里点得到就点）
        val rowLocal = d.tree().tag("sub-hit-$iLocal")
        if (rowLocal != null) d.click(rowLocal) else com.gugaart.ui.subtitle.SubtitleSearchBoard.choose(s2.hits[iLocal], c.graph)
        val doneLocal = d.waitFor(40_000, pollMs = 200) { board.session == null || s2.apply is com.gugaart.ui.subtitle.ApplyState.Failed }
        val pickedLocal = d.waitFor(15_000) { (c.controller?.snapshot?.value?.subtitleIndex ?: -1) >= com.gugaart.ui.subtitle.LocalSubtitles.BASE_INDEX && c.controller?.snapshot?.value?.phase == PlayPhase.Ready }
        val sidLocal = d.waitFor(8_000) { c.mpv("sid").let { it != null && it != "no" } }
        Thread.sleep(3_000)
        val shLocal = d.shot("subsearch-local", "选了迅雷 / 射手的一条：仅本次播放，外挂轨已加载并选上")
        c.expect(
            "subsearch-local", "选迅雷 / 射手的一条：下载到本机（回环地址），播放器加成外挂轨并选上（仅本次播放，不写服务器）",
            doneLocal && pickedLocal && sidLocal,
            values = mapOf(
                "来源" to s2.hits[iLocal].source.label, "名" to s2.hits[iLocal].name, "之前" to beforeLocal, "之后" to c.controller?.snapshot?.value?.subtitleIndex,
                "sid" to c.mpv("sid"), "sub-text" to c.mpv("sub-text")?.take(60), "失败" to (s2.apply as? com.gugaart.ui.subtitle.ApplyState.Failed)?.why,
                "点到行" to (rowLocal != null),
            ),
            shots = listOf(shLocal),
        )
        c.back(1_500)
    }
    // ================================================================== 1.4.1（Mac Pi 验收：自写搜索 / 字幕样式）

    /** 1.4.1 自写片库搜索（docs/search.md）：缩写 / 人名后两字 / 剧名 + 集号直达 / 原名命中 / 排序。 */
    private fun search141(c: Ctx) {
        val d = c.d
        c.reset()
        c.graph.updater.dismiss()
        closeSlips(c)
        val client = c.client()
        val snap = runCatching { runBlocking { com.gugaart.ui.search.SearchIndexStore.get(client) } }.getOrNull()
        if (snap == null) { c.skip("search-index", "本地片库索引", "取不到本地索引"); return }
        val titles = snap.titles.filter { it.kind == com.gugaart.ui.data.Kind.Movie || it.kind == com.gugaart.ui.data.Kind.Series }
        val index = com.gugaart.ui.screens.search.SearchIndex(titles, titles.size, snap.engine)
        c.expect("search-index", "本地片库索引建好（片 / 单集 / 人物）", true, values = mapOf("片" to snap.titles.size, "单集" to snap.episodes.size, "人物" to snap.people.size))
        for ((q, tag) in listOf("复联" to "fulian", "云熙" to "yunxi", "颜心记 5" to "yanxinji5", "iron man" to "ironman")) {
            c.nav(Route.Search(q), 2_500)
            closeSlips(c)
            val sh = d.shot("search-$tag", "搜索「$q」：结果 / 排序 / 原名命中 / 直达签")
            val hits = com.gugaart.ui.screens.search.searchTitles(index, q)
            val people = com.gugaart.ui.screens.search.searchPeopleLocal(snap.people, q)
            fun line(h: com.gugaart.ui.screens.search.TitleHit): String = h.title.name +
                (com.gugaart.ui.screens.search.directTag(h)?.let { "[$it]" } ?: "") +
                (h.original?.let { "{原名命中:$it}" } ?: "")
            val top = hits.take(6).joinToString(" / ") { line(it) }
            val ok = when (tag) {
                "fulian" -> hits.firstOrNull()?.title?.name == "复仇者联盟"
                "yunxi" -> people.isNotEmpty()
                "yanxinji5" -> hits.firstOrNull()?.let { com.gugaart.ui.screens.search.directTag(it) } == "第 5 集"
                else -> hits.firstOrNull()?.let { it.original != null && it.range == null } == true
            }
            c.expect("search-$tag", "搜索「$q」", ok, values = mapOf("前几条" to top, "人物" to people.take(3).joinToString { it.person.name }), shots = listOf(sh))
            c.back(700)
        }
    }

    /** 1.4.1 字幕样式（docs/subtitle-style.md）：150% + 粗体 + 文楷 + 粗描边 + 抬高；也改 ASS 开 / 关；站酷快乐体第一次下载。只在 Mock 上跑。 */
    /** 1.4.1 重画的 10 张特别称号章（ui/icons-1.4.1 a29b273）：「我的」页那一栏的小章。 */
    private fun specials141(c: Ctx) {
        val d = c.d
        c.reset()
        c.graph.updater.dismiss()
        closeSlips(c)
        c.nav(Route.History(), 2_500)
        closeSlips(c)
        val sh = d.shot("specials-me", "「我的」页：特别称号一栏（重画的 10 张在小尺寸下）")
        val grid = d.tree().nodes.firstOrNull { it.tag == "me-specials" || it.desc.any { s -> s.contains("特别称号") } }
        c.human("specials-grid", "特别称号章（重画版）", "看 specials-me：压箱底 / 奇幻迷 / 综艺咖 / 合家欢 / 冷门猎人 / 历史迷 / 电影节 / 铁血 / 求知者 / 老古董", shots = listOf(sh), values = mapOf("找到一栏" to (grid != null), "页" to c.path))
        c.back(800)
    }

    private fun subStyle141(c: Ctx) {
        val d = c.d
        c.reset()
        val base = runCatching { c.client().address.baseUrl }.getOrNull().orEmpty()
        if (!(base.startsWith("http://127.0.0.1:") || base.startsWith("http://localhost:"))) {
            c.skip("sub-style-apply", "字幕样式下发到 mpv（1.4.1）", "不是 Mock：加 --mock 再跑")
            return
        }
        c.graph.updater.dismiss()
        val store = c.settingsStoreOf() ?: com.gugaart.ui.data.GugaSettingsStore(c.graph.deps.local)
        val orig = store.prefs.value
        store.update {
            it.copy(subStyle = com.gugaart.ui.data.SubStylePrefs(
                scale = 1.5, bold = true, font = "lxgwWenKai",
                outline = com.gugaart.ui.data.SubOutline.Thick, shadow = false,
                position = com.gugaart.ui.data.SubPosition.Raised, overrideAss = false,
            ))
        }
        c.nav(Route.Settings("subtitles"), 2_000)
        val shSet = d.shot("style-settings", "设置 › 字幕与音轨 › 字幕样式：150% / 粗体 / 霞鹜文楷 / 粗描边 / 抬高")
        c.human("style-settings", "设置里的字幕样式组", "看 style-settings（预览条 / 字号 / 字体 / 描边 / 位置）", shots = listOf(shSet))
        c.back(800)
        val pick = runCatching {
            val cl = c.client()
            val list = runBlocking { cl.getItems(LibraryQuery(includeItemTypes = listOf("Movie"), recursive = true, limit = 80)).items }
            list.take(60).firstOrNull { m ->
                val st = runCatching { runBlocking { cl.getItem(m.id).mediaSources.firstOrNull()?.mediaStreams.orEmpty() } }.getOrNull().orEmpty()
                st.any { it.type == "Subtitle" && it.isExternal == true && (it.codec == "ass" || it.codec == "ssa") } && st.firstOrNull { it.type == "Video" }?.codec == "h264"
            }
        }.getOrNull()
        if (pick == null) {
            c.skip("sub-style-apply", "字幕样式下发到 mpv", "Mock 里没有「h264 + 外挂 ASS」的条目")
            store.update { it.copy(subStyle = orig.subStyle) }
            return
        }
        val assIdx = runBlocking { c.client().getItem(pick.id).mediaSources.first().mediaStreams }
            .first { it.type == "Subtitle" && it.isExternal == true && (it.codec == "ass" || it.codec == "ssa") }.index
        c.nav(Route.Play(pick.id), 1_500)
        c.awaitFirstFrame(System.currentTimeMillis())
        c.controller?.selectSubtitle(assIdx)
        Thread.sleep(1_800)
        fun props(): String = listOf("sub-scale", "sub-bold", "sub-font", "sub-border-size", "sub-shadow-offset", "sub-margin-y", "sub-ass-override", "sub-fonts-dir", "sid")
            .joinToString(" ") { "$it=${c.mpv(it)}" }
        fun still(name: String, label: String): String? {
            c.controller?.seekTo(13_500.0)
            Thread.sleep(1_200)
            c.controller?.pause()
            Thread.sleep(3_600)
            val sh = d.shot(name, label)
            c.controller?.play()
            Thread.sleep(600)
            return sh
        }
        val shKeep = still("style-ass-keep", "「也改 ASS」关：ASS 原样（顶部黄字），只跟着字号缩放")
        val keep = props()
        c.check(
            "sub-style-tracks", "内核这一条字幕轨到底是什么（外挂 ASS 还是 SRT）", Verdict.NeedsHuman,
            detail = "sid=${c.mpv("sid")} sub-text=${c.mpv("sub-text")}",
            values = mapOf("track-list" to c.mpv("track-list"), "控制器选中" to c.controller?.snapshot?.value?.subtitles?.firstOrNull { it.selected }?.label),
        )
        val scaleOk = c.mpv("sub-scale")?.toDoubleOrNull()?.let { kotlin.math.abs(it - 1.5) < 0.02 } == true
        val boldOk = c.mpv("sub-bold") == "yes"
        val fontOk = c.mpv("sub-font")?.contains("LXGW") == true
        val marginOk = c.mpv("sub-margin-y")?.toDoubleOrNull()?.let { kotlin.math.abs(it - 57.6) < 2.0 } == true
        val borderOk = (c.mpv("sub-border-size")?.toDoubleOrNull() ?: 0.0) > 2.0
        c.expect(
            "sub-style-apply", "字号 150% + 粗体 + 霞鹜文楷 + 粗描边 + 抬高都下发到 mpv",
            scaleOk && boldOk && fontOk && marginOk && borderOk && c.mpv("sub-ass-override") != "force",
            values = mapOf("片" to pick.name, "外挂ASS轨" to assIdx, "属性" to keep), shots = listOf(shKeep),
        )
        store.update { it.copy(subStyle = it.subStyle.copy(overrideAss = true)) }
        Thread.sleep(1_500)
        val shForce = still("style-ass-force", "「也改 ASS」开：字体 / 描边 / 位置也改 ASS")
        c.expect("sub-style-ass", "「也改 ASS」开：sub-ass-override=force", c.mpv("sub-ass-override") == "force", values = mapOf("属性" to props()), shots = listOf(shForce))
        store.update { it.copy(subStyle = it.subStyle.copy(font = "zcoolKuaiLe")) }
        val got = d.waitFor(120_000, 500) { c.mpv("sub-font") == "ZCOOL KuaiLe" }
        val shZ = still("style-zcool", "站酷快乐体（第一次用要下载 1.4 MB）")
        c.expect("sub-style-zcool", "站酷快乐体下载好后内核换成 ZCOOL KuaiLe", got, values = mapOf("sub-font" to c.mpv("sub-font"), "sub-fonts-dir" to c.mpv("sub-fonts-dir")), shots = listOf(shZ))
        c.back(1_500)
        store.update { it.copy(subStyle = orig.subStyle) }
    }

    // ================================================================== 1.5.0 AUD：详情「源」/ 切回 / 设置开关 / 播放器换台（要 --mock-servers 预存三台）

    /** 三台预存 Mock 的基地址（scheme://host:port，按服务器列表顺序，当前那台在前）。不足三台 = null。 */
    private fun threeMocks(c: Ctx): List<String>? {
        val es = com.gugaart.ui.servers.sharedServerSwitcher(c.graph).entries.value
        val urls = es.mapNotNull { Regex("^https?://[^/]+").find(it.address)?.value }
            .filter { it.startsWith("http://127.0.0.1:") }.distinct()
        return urls.takeIf { it.size >= 3 }
    }

    private fun portOf(url: String): String = url.substringAfterLast(':').takeWhile { it.isDigit() }

    /** 给一台本机 Mock 用夹具账号 demo / demo 换个令牌（只对本机 Mock；从不发给别的服务器）。 */
    private fun mockToken(url: String): String? = runCatching {
        val conn = java.net.URI("$url/Users/AuthenticateByName").toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 3_000; conn.readTimeout = 3_000
        conn.outputStream.use { it.write("""{"Username":"demo","Pw":"demo"}""".toByteArray()) }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        ((kotlinx.serialization.json.Json.parseToJsonElement(body) as? JsonObject)?.get("AccessToken") as? kotlinx.serialization.json.JsonPrimitive)?.content
    }.getOrNull()

    /** 一台 Mock 此刻在播的条目 id（/Sessions 的 NowPlayingItem.Id）。 */
    private fun mockSessions(url: String, token: String?): List<String> = runCatching {
        val conn = java.net.URI("$url/Sessions").toURL().openConnection() as java.net.HttpURLConnection
        conn.setRequestProperty("X-Emby-Token", token ?: "")
        conn.connectTimeout = 3_000; conn.readTimeout = 3_000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        (kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
            ((el as? JsonObject)?.get("NowPlayingItem") as? JsonObject)?.get("Id")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
        }.orEmpty()
    }.getOrDefault(emptyList())

    /** 详情 / 播放器里「源」选择器的一行（d-slip-row）。 */
    private fun slipRows(d: AcceptDriver): List<AcceptDriver.Node> =
        d.tree().nodes.filter { it.tag == "d-slip-row" || it.tag == "d-slip-row-on" }

    private fun openSourcePick(c: Ctx): Boolean {
        val d = c.d
        closeSlips(c)
        d.waitFor(20_000) { d.tree().has("d-pick-源") || d.tree().has("d-pick-版本") }
        (d.tree().tag("d-pick-源") ?: d.tree().tag("d-pick-版本"))?.let { d.click(it) }
        return d.waitFor(6_000) { d.tree().has("d-pick-list") }
    }

    // ---- 项 5：详情页 990201 的「源」；选 1080p 放 990202、选别台放那台（查 Mock sessions）
    private fun sources150(c: Ctx) {
        val d = c.d
        val g = c.graph
        val mocks = threeMocks(c)
        if (mocks == null) { c.skip("sources-list", "详情页「源」本台 + 别台", "要 --mock-servers 预存三台 Mock"); return }
        fun port(): String? = g.session.session.value?.serverUrl?.substringAfterLast(':')?.takeWhile { it.isDigit() }
        val first = port() ?: return
        val homeUrl = mocks.first { ":$first" in it }
        val tokens = mocks.associateWith { mockToken(it) }
        c.reset(); closeSlips(c)
        c.nav(Route.Item("990201"), 2_500)
        Thread.sleep(1_200); closeSlips(c)
        val listOpen = openSourcePick(c)
        d.waitFor(25_000, 300) { slipRows(d).any { " ms" in d.tree().textUnder(it) } }
        Thread.sleep(600)
        val texts = slipRows(d).map { d.tree().textUnder(it) }
        val sh = d.shot("src-01-list", "详情「源」：本台 4K（推荐）+ 1080p（另一份文件）+ 别台行（名 + 延迟）")
        val rec4k = texts.firstOrNull()?.let { "4K" in it && "推荐" in it } == true
        val dup1080 = texts.any { "1080" in it && "另一份文件" in it }
        val others = texts.filter { " ms" in it }
        c.expect(
            "sources-list", "「源」先列本台 4K（推荐）+ 1080p（另一份文件），再接别台行（服务器名 + 延迟）",
            listOpen && rec4k && dup1080 && others.isNotEmpty(),
            values = mapOf("行" to texts.joinToString(" | "), "别台行数" to others.size), shots = listOf(sh),
        )
        // ---- 选别台的行 → 播放 → 那台 sessions 应有 990201（先测：此时还没有别的播放，控制器干净）
        // 别台行排在纸片下方、可能滚出可见区：先滚进来再点（语义树给不裁剪位置，直接点会落空）
        val list = d.tree().tag("d-pick-list")
        val winH = d.ui { d.window.height.toDouble() }
        var otherRow = slipRows(d).firstOrNull { " ms" in d.tree().textUnder(it) }
        var tries = 0
        while (otherRow != null && list != null && tries < 8 && otherRow.bounds.cy > winH - 30) {
            d.wheel(list.bounds.cx, kotlin.math.min(list.bounds.y + 60.0, winH - 60), 3)
            Thread.sleep(250)
            otherRow = slipRows(d).firstOrNull { " ms" in d.tree().textUnder(it) }
            tries++
        }
        val otherText = otherRow?.let { d.tree().textUnder(it) }.orEmpty()
        otherRow?.let { d.click(it) }
        Thread.sleep(600)
        c.waitTag("d-cta", 5_000)?.let { d.click(it) }
        d.waitFor(8_000) { c.path.startsWith("/play") }
        val ff2 = c.awaitFirstFrame(System.currentTimeMillis())
        val otherUrls = mocks.filter { ":$first" !in it }
        d.waitFor(25_000, 300) { otherUrls.any { mockSessions(it, tokens[it]).contains("990201") } }
        val playingOn = otherUrls.filter { mockSessions(it, tokens[it]).contains("990201") }
        val snapItem2 = c.controller?.snapshot?.value?.itemId
        val sh3 = d.shot("src-03-play-other", "选别台的行后播放（应在那台）")
        c.expect(
            "sources-play-other", "选别台的行：播放落在别台（那台 sessions 有 990201）", ff2 != null && playingOn.isNotEmpty(),
            values = mapOf("首帧ms" to ff2, "点的行" to otherText, "控制器条目" to snapItem2, "在播的别台" to playingOn, "本台sessions" to mockSessions(homeUrl, tokens[homeUrl])), shots = listOf(sh3),
        )
        c.reset(); closeSlips(c)
        // ---- 选 1080p（另一份文件 = 990202）→ 播放 → 本台 sessions 应是 990202
        c.nav(Route.Item("990201"), 2_000)
        Thread.sleep(800); closeSlips(c)
        openSourcePick(c)
        d.waitFor(20_000, 300) { slipRows(d).any { "1080" in d.tree().textUnder(it) } }
        slipRows(d).firstOrNull { "1080" in d.tree().textUnder(it) && "另一份文件" in d.tree().textUnder(it) }?.let { d.click(it) }
        Thread.sleep(600)
        c.waitTag("d-cta", 5_000)?.let { d.click(it) }
        val ff = c.awaitFirstFrame(System.currentTimeMillis())
        d.waitFor(12_000, 300) { mockSessions(homeUrl, tokens[homeUrl]).contains("990202") }
        val snapItem = c.controller?.snapshot?.value?.itemId
        val sess1 = mockSessions(homeUrl, tokens[homeUrl])
        val sh2 = d.shot("src-02-play-1080", "选 1080p 后播放（应为 990202）")
        c.expect(
            "sources-play-1080", "选 1080p（另一份文件）播放的是 990202", ff != null && snapItem == "990202" && sess1.contains("990202"),
            values = mapOf("首帧ms" to ff, "控制器条目" to snapItem, "本台sessions" to sess1), shots = listOf(sh2),
        )
        c.back(1_500)
    }

    // ---- 项 3：切走再切回，首页第一帧就是上次的内容，记切回用时
    private fun switchback150(c: Ctx) {
        val d = c.d
        val g = c.graph
        val sw = com.gugaart.ui.servers.sharedServerSwitcher(g)
        if (threeMocks(c) == null) { c.skip("switchback-content", "切走再切回首页第一帧", "要 --mock-servers 预存三台"); return }
        fun port(): String? = g.session.session.value?.serverUrl?.substringAfterLast(':')?.takeWhile { it.isDigit() }
        val first = port() ?: return
        val e2 = sw.entries.value.firstOrNull { !it.isCurrent } ?: return
        val second = portOf(e2.address)
        c.reset(); closeSlips(c)
        d.waitFor(15_000) { d.tree().has("desktop-home") }
        d.waitFor(15_000) { d.tree().has("fold-title") || d.tree().has("desktop-poster-row") }
        Thread.sleep(1_000)
        fun homeSig(): String = d.tree().let { t ->
            (t.tag("fold-title")?.let { t.textUnder(it) } ?: "") + " | " + (t.tag("desktop-poster-row")?.let { t.textUnder(it) }?.take(80) ?: "")
        }
        val before = homeSig()
        val shBefore = d.shot("sw-01-before", "切走前：首页（第一台）")
        runBlocking { sw.switchTo(e2.serverId, e2.userId) }
        d.waitFor(10_000) { port() == second }
        d.waitFor(15_000) { d.tree().has("fold-title") || d.tree().has("desktop-poster-row") }
        Thread.sleep(800)
        // 切回第一台：计时 + 第一帧有没有内容 / 先出骨架
        val e1 = sw.entries.value.firstOrNull { ":$first" in it.address } ?: return
        val t0 = System.currentTimeMillis()
        runBlocking { sw.switchTo(e1.serverId, e1.userId) }
        d.waitFor(10_000) { port() == first }
        var skeletonSeen = false
        val ready = d.waitFor(8_000, 40) {
            if (d.tree().has("desktop-home-loading")) skeletonSeen = true
            d.tree().has("fold-title") || d.tree().has("desktop-poster-row")
        }
        val elapsed = System.currentTimeMillis() - t0
        val after = homeSig()
        val shAfter = d.shot("sw-02-back", "切回第一台：首页第一帧")
        c.expect(
            "switchback-content", "切回第一台：首页立刻有上次的内容（不先出空骨架）", ready && !skeletonSeen && after.isNotBlank(),
            values = mapOf("切回用时ms" to elapsed, "出骨架" to skeletonSeen, "切走前" to before.take(60), "切回后" to after.take(60)),
            shots = listOf(shBefore, shAfter),
        )
        c.human("switchback-look", "切回第一帧观感", "看 sw-01-before / sw-02-back：切回第一帧就是上次的内容（三台片子一样，看的是不空、不闪骨架）", shots = listOf(shBefore, shAfter))
    }

    // ---- 项 7：设置 › 播放 › 多台服务器，关掉开关后详情只剩本台的源
    private fun serverSettings150(c: Ctx) {
        val d = c.d
        if (threeMocks(c) == null) { c.skip("settings-toggle", "设置多台服务器开关", "要 --mock-servers 预存三台"); return }
        fun otherServersRow(): AcceptDriver.Node? = d.tree().tag("set-otherServers")
        fun onOffCell(want: String): AcceptDriver.Node? = otherServersRow()?.let { r ->
            d.tree().descendants(r).firstOrNull { it.tag == "onoff-on" || it.tag == "onoff-off" }?.let { oo ->
                d.tree().descendants(oo).firstOrNull { it.texts.contains(want) }
            }
        }
        fun scrollToRow() {
            repeat(10) {
                val r = otherServersRow() ?: return
                if (r.visible) return
                d.wheel(d.ui { d.window.width / 2.0 }, d.ui { d.window.height / 2.0 }, 4)
                Thread.sleep(250)
            }
        }
        c.reset(); closeSlips(c)
        c.nav(Route.Settings("playback"), 1_500)
        d.waitFor(8_000) { d.tree().has("set-otherServers") }
        // 滚到「多台服务器」这一组（在播放分区靠下）
        scrollToRow()
        Thread.sleep(600)
        val shOn = d.shot("set-01-on", "设置 › 播放 › 多台服务器（开）")
        onOffCell("关")?.let { d.click(it) }
        val turnedOff = d.waitFor(5_000) { otherServersRow()?.let { r -> d.tree().descendants(r).any { it.tag == "onoff-off" } } == true }
        val shOff = d.shot("set-02-off", "「从其它服务器找片源」关掉")
        c.expect("settings-toggle", "「从其它服务器找片源」开关能关掉", turnedOff, shots = listOf(shOn, shOff))
        // 关掉后详情只剩本台的源
        c.nav(Route.Item("990201"), 2_500)
        openSourcePick(c)
        Thread.sleep(2_500)
        val texts = slipRows(d).map { d.tree().textUnder(it) }
        val noOther = texts.none { " ms" in it }
        val shDetail = d.shot("set-03-detail", "关掉后详情「版本」：只剩本台")
        c.expect(
            "settings-off-detail", "关掉后详情页只剩本台的源（没有别台行）", noOther && texts.isNotEmpty(),
            values = mapOf("行" to texts.joinToString(" | ")), shots = listOf(shDetail),
        )
        // 还原开关（开），不影响后面的场景
        c.nav(Route.Settings("playback"), 1_200)
        d.waitFor(6_000) { d.tree().has("set-otherServers") }
        scrollToRow()
        onOffCell("开")?.let { d.click(it) }
        d.waitFor(5_000) { otherServersRow()?.let { r -> d.tree().descendants(r).any { it.tag == "onoff-on" } } == true }
        c.back(800)
    }

    // ---- 项 6：播放器画质面板「版本」换到别台接着放；停掉那台再换 → 失败提示 4 秒
    private fun remote150(c: Ctx) {
        val d = c.d
        val g = c.graph
        val mocks = threeMocks(c)
        if (mocks == null) { c.skip("remote-switch", "播放器换台", "要 --mock-servers 预存三台"); return }
        fun port(): String? = g.session.session.value?.serverUrl?.substringAfterLast(':')?.takeWhile { it.isDigit() }
        val first = port() ?: return
        val others = com.gugaart.ui.servers.sharedServerSwitcher(g).entries.value.filter { !it.isCurrent }
        if (others.size < 2) { c.skip("remote-switch", "播放器换台", "要三台（两台别台）"); return }
        val url2 = Regex("^https?://[^/]+").find(others[0].address)?.value ?: return
        val url3 = Regex("^https?://[^/]+").find(others[1].address)?.value ?: return
        val tokens = mocks.associateWith { mockToken(it) }
        fun openQuality(): Boolean {
            c.wakeControls()
            if (!d.tree().has("panel-quality")) {
                d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.contains("画质") } }?.let { d.click(it) }
            }
            return d.waitFor(5_000) { d.tree().has("panel-quality") }
        }
        fun sourceOpts() = d.tree().nodes.filter { (it.tag == "panel-option" || it.tag == "panel-option-on") && " ms" in d.tree().textUnder(it) }
        fun clickSourceRow(p: String): Boolean {
            val panel = d.tree().tag("panel-quality") ?: return false
            fun find() = sourceOpts().firstOrNull { p in d.tree().textUnder(it) }
            var n = find() ?: return false
            var tries = 0
            while (tries < 8 && (n.bounds.cy > panel.bounds.y + panel.bounds.h - 24 || n.bounds.cy < panel.bounds.y + 24)) {
                d.wheel(panel.bounds.cx, panel.bounds.cy, if (n.bounds.cy > panel.bounds.y + panel.bounds.h - 24) 3 else -3)
                Thread.sleep(300)
                n = find() ?: return false
                tries++
            }
            d.click(n)
            return true
        }
        c.reset(); closeSlips(c)
        c.nav(Route.Play("990201"), 1_500)
        Thread.sleep(800); closeSlips(c)
        val ff = c.awaitFirstFrame(System.currentTimeMillis())
        if (ff == null) { c.expect("remote-switch", "开播 990201 出首帧", false); c.back(1_500); return }
        val panelOpen = openQuality()
        d.waitFor(25_000, 300) { sourceOpts().size >= 2 }
        val shPanel = d.shot("rem-01-panel", "画质面板「版本 / 源」：本台 + 别台行")
        val posBefore = c.mpv("time-pos")?.toDoubleOrNull() ?: 0.0
        // 点第二台的行（名字里带它的端口）
        val clicked2 = clickSourceRow(portOf(url2))
        val switched = d.waitFor(20_000, 300) { mockSessions(url2, tokens[url2]).contains("990201") }
        // 换台完成后再量：开场 seek 落定、位置接着换前（不倒回 0）
        val resumed = d.waitFor(15_000, 200) {
            c.mpv("seeking") == "no" && (c.mpv("time-pos")?.toDoubleOrNull() ?: -1.0) >= posBefore - 1.5 && c.controller?.snapshot?.value?.phase == PlayPhase.Ready
        }
        val posAfter = c.mpv("time-pos")?.toDoubleOrNull()
        val shSwitch = d.shot("rem-02-switched", "换到第二台：从当前位置接着放")
        c.expect(
            "remote-switch", "画质面板换到别台：从当前位置接着放（那台 sessions 有 990201）",
            panelOpen && clicked2 && switched && resumed && mockSessions(url2, tokens[url2]).contains("990201"),
            values = mapOf("换前pos" to posBefore, "换后pos" to posAfter, "第二台sessions" to mockSessions(url2, tokens[url2])),
            shots = listOf(shPanel, shSwitch),
        )
        // ---- 停掉第三台，再点它的行 → 失败提示
        openQuality()
        d.waitFor(15_000, 300) { sourceOpts().any { portOf(url3) in d.tree().textUnder(it) } }
        System.getenv("GUGAART_ACCEPT_STOP")?.let { java.io.File(it).writeText("stop ${portOf(url3)}\n") }
        val down = d.waitFor(60_000, 500) { !reachable(url3) }
        c.expect("remote-offline-ready", "第三台 Mock 停掉（当成连不上）", down)
        openQuality()
        val clicked3 = clickSourceRow(portOf(url3))
        val flashShown = d.waitFor(8_000, 100) { d.tree().has("player-notice-flash") }
        val flashText = d.tree().textUnder("player-notice-flash").orEmpty()
        val shFlash = d.shot("rem-03-flash", "换到停掉的台：「换到「X」失败」")
        val tFlash = System.currentTimeMillis()
        val gone = d.waitFor(8_000, 200) { !d.tree().has("player-notice-flash") }
        val flashMs = System.currentTimeMillis() - tFlash
        c.expect(
            "remote-fail-toast", "停掉那台再换：出「换到「X」失败」，约 4 秒后消失",
            flashShown && "」失败" in flashText && gone,
            values = mapOf("提示" to flashText, "亮ms" to flashMs, "点到行" to clicked3), shots = listOf(shFlash),
        )
        c.back(1_500)
    }

    // ================================================================== 1.5.1 多季的剧：默认搜这一季

    /**
     * 主理人：「头文字D播放第六季第三集的时候点搜索弹幕、字幕还是默认搜头文字D，这样还得手动再搜一次。」
     * Mock 里六季的《头文字D》（季名「第 N 季」；外面的脚本给 Mock 加的）放第 6 季第 3 集：
     *  ① 弹幕来源：手动搜索框默认「头文字D 第六季」、这一季的候选排前面、框下面是这一季在弹幕服务器上自己的名字；点第一个快捷词真的去搜。
     *     弹幕服务器是外面起的假服务器（环境变量 `GUGAART_ACCEPT_DANMAKU`，只认 127.0.0.1），写进这份 Mock 数据目录的设置。
     *  ② 字幕：字幕面板「搜索字幕…」，「换个名字搜」框默认带季集；迅雷是真的（只读）。只在 Mock 上跑。
     */
    private fun season151(c: Ctx) {
        val d = c.d
        val titleDm = "弹幕来源：多季的剧默认搜这一季（1.5.1）"
        val titleSub = "字幕搜索：「换个名字搜」默认带季集（1.5.1）"
        c.reset()
        val client = runCatching { c.client() }.getOrNull()
        val base = client?.address?.baseUrl.orEmpty()
        if (client == null || !(base.startsWith("http://127.0.0.1:") || base.startsWith("http://localhost:"))) {
            c.skip("season-dm", titleDm, "不是 Mock")
            c.skip("season-sub", titleSub, "不是 Mock")
            return
        }
        val ep = runCatching {
            runBlocking {
                val series = client.getItems(LibraryQuery(includeItemTypes = listOf("Series"), recursive = true, searchTerm = "头文字D", limit = 10)).items.firstOrNull { it.name == "头文字D" }
                series?.let { s -> client.getEpisodes(s.id, season = 6).items.firstOrNull { it.indexNumber == 3 } }
            }
        }.getOrNull()
        if (ep == null) {
            c.skip("season-dm", titleDm, "Mock 里没有六季的头文字D（要外面的脚本加）")
            c.skip("season-sub", titleSub, "Mock 里没有六季的头文字D（要外面的脚本加）")
            return
        }
        val dm = System.getenv("GUGAART_ACCEPT_DANMAKU")?.takeIf { it.startsWith("http://127.0.0.1:") }
        if (dm != null) runBlocking { com.gugaart.ui.danmaku.DanmakuServers.save(listOf(dm)) }
        val t0 = System.currentTimeMillis()
        c.nav(Route.Play(ep.id, startMs = null), 300)
        val ff = c.awaitFirstFrame(t0)
        // 弹幕匹配走完（假服务器几百毫秒；有歧义时自己会出来）
        Thread.sleep(6_000)
        // ---- ① 弹幕来源（「弹幕源」那一行点开的就是这一张）
        if (!d.tree().has("dm-picker")) d.ui { com.gugaart.ui.player.DanmakuPickerBoard.open = true }
        val opened = c.waitTag("dm-picker", 5_000) != null
        d.waitFor(5_000) { d.tree().tag("dm-search")?.editable?.isNotEmpty() == true }
        Thread.sleep(600)
        val tree = d.tree()
        val box = tree.tag("dm-search")?.editable
        val cands = (0 until 6).mapNotNull { i -> tree.textUnder("dm-cand-$i") }
        val quick = (0 until 3).mapNotNull { i -> tree.textUnder("dm-quick-$i") }
        val shPicker = d.shot("season-dm", "弹幕来源：框里「头文字D 第六季」，第六季的候选在前，框下面是快捷词")
        fun sixth(t: String?) = t != null && ("Final Stage" in t || "第六部" in t)
        c.expect(
            "season-dm", titleDm,
            opened && box == "头文字D 第六季" && (cands.isEmpty() || sixth(cands.first())),
            values = mapOf("片" to "${ep.seriesName} S${ep.parentIndexNumber}E${ep.indexNumber}", "首帧ms" to ff, "弹幕服务器" to (dm != null), "框" to box, "候选" to cands.joinToString(" | "), "快捷词" to quick.joinToString(" | ")),
            shots = listOf(shPicker),
        )
        // 框里的词原样搜一次：结果里第六季的排前面
        d.tree().tag("dm-search-go")?.let { d.click(it) }
        val searched = d.waitFor(10_000) { d.tree().has("dm-hit-0") }
        Thread.sleep(500)
        val hits = (0 until 4).mapNotNull { i -> d.tree().textUnder("dm-hit-$i") }
        val shSearch = d.shot("season-dm-search", "点「搜索」：搜「头文字D 第六季」，第六季的条目在前")
        c.expect("season-dm-search", "弹幕来源：搜框里的词，这一季的结果排前面（1.5.1）", searched && sixth(hits.firstOrNull()), values = mapOf("结果" to hits.joinToString(" | ")), shots = listOf(shSearch))
        val chip = d.tree().tag("dm-quick-0")
        if (chip != null) {
            d.click(chip)
            val got = d.waitFor(10_000) { d.tree().has("dm-hit-0") }
            Thread.sleep(500)
            val box2 = d.tree().tag("dm-search")?.editable
            val hit0 = d.tree().textUnder("dm-hit-0")
            val shHits = d.shot("season-dm-quick", "点快捷词：填进框里并搜")
            c.expect("season-dm-quick", "点快捷词 = 填进框里并搜，结果是这一季的条目（1.5.1）", got && box2 == quick.firstOrNull(), values = mapOf("框" to box2, "第一条" to hit0), shots = listOf(shHits))
        } else c.skip("season-dm-quick", "点快捷词（1.5.1）", "没有快捷词（候选里没有这一季自己的名字）")
        d.tree().tag("dm-picker-close")?.let { d.click(it) }
        d.waitFor(3_000) { !d.tree().has("dm-picker") }
        // ---- ② 字幕搜索：底部「字幕」→ 面板最后一行「搜索字幕…」
        val board = com.gugaart.ui.subtitle.SubtitleSearchBoard
        c.wakeControls()
        if (!d.tree().has("panel-sub")) {
            d.tree().nodes.firstOrNull { (it.tag == "word" || it.tag == "word-on") && it.desc.any { s -> s.startsWith("字幕") } }?.let { d.click(it) }
            d.waitFor(3_000) { d.tree().has("panel-sub") }
        }
        Thread.sleep(500)
        d.tree().nodes.firstOrNull { n -> n.desc.any { it == "搜索字幕…" } }?.let { d.click(it) }
        val subOpen = d.waitFor(5_000) { d.tree().has("sub-search") }
        d.waitFor(60_000, pollMs = 300) { board.session?.let { !it.searching } == true }
        Thread.sleep(800)
        val s = board.session
        val subBox = d.tree().tag("sub-query")?.editable
        val shSub = d.shot("season-sub", "字幕搜索：「换个名字搜」默认带季集，迅雷的结果里有这一集")
        c.expect(
            "season-sub", titleSub,
            subOpen && s != null && Regex("""S06E03""", RegexOption.IGNORE_CASE).containsMatchIn(s.query) && subBox == s.query,
            values = mapOf("框" to subBox, "文件名" to s?.target?.fileName, "迅雷" to s?.thunder, "前几条" to s?.hits?.take(5)?.joinToString(" | ") { it.name }),
            shots = listOf(shSub),
        )
        c.back(1_500)
    }
}

internal data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
