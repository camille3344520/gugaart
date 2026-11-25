package com.gugaart.desktop.mac.accept

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.sun.jna.Callback
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.Component
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * 验收运行器的手：只在本进程里动，不需要系统的「辅助功能」权限。
 *
 *  - **看**：Compose 的语义树（`testTag`、文字、焦点、边界），从窗口的 `ComposeWindowPanel.getSemanticsOwners()` 读（Compose 没有给真窗口
 *    的公开入口，取那个私有字段是唯一的反射，只在开发运行里用）；边界换算成点（= dp，左上为原点）。
 *  - **动**：往本进程的 AWT 事件队列**投递**（`EventQueue.postEvent`）真实的键盘 / 鼠标事件——和系统送来的走同一条路：键盘事件由焦点管理器
 *    转给焦点所在的组件（KEY_PRESSED / KEY_TYPED / KEY_RELEASED，带字符），鼠标事件落在 Compose 画布上节点边界的中点。
 *  - **截图**：只截本应用自己的窗口（`screencapture -l <窗口号>`，窗口号用 JNA 从我们的 NSWindow 取），绝不截全屏。
 *  - **窗口**：红绿灯的位置、失焦 / 复焦、系统全屏（`toggleFullScreen:`）、改尺寸、视频层在窗口层树里的位置，都经 JNA 调 AppKit。
 *
 * 线程：语义树和 AWT 在事件线程上读；AppKit 的调用 `dispatch_sync` 到主线程。调用者（运行器）在自己的线程上，**不能**是事件线程。
 */
internal class AcceptDriver(private val windowOf: () -> ComposeWindow?, val out: File) {
    val window: ComposeWindow get() = windowOf() ?: error("窗口还没出来")

    // ------------------------------------------------------------------ 线程

    fun <T> ui(block: () -> T): T {
        check(!EventQueue.isDispatchThread()) { "运行器不能在事件线程上" }
        var r: Result<T>? = null
        SwingUtilities.invokeAndWait { r = runCatching(block) }
        return r!!.getOrThrow()
    }

    /** 等事件队列把之前投递的都处理完，再给 Compose 几帧。 */
    fun settle(ms: Long = 120) {
        SwingUtilities.invokeAndWait { }
        if (ms > 0) Thread.sleep(ms)
        SwingUtilities.invokeAndWait { }
    }

    /** 轮询到 [cond] 成立或超时；返回成立了没有。 */
    fun waitFor(timeoutMs: Long, pollMs: Long = 100, cond: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (runCatching(cond).getOrDefault(false)) return true
            if (System.currentTimeMillis() > end) return false
            Thread.sleep(pollMs)
        }
    }

    // ------------------------------------------------------------------ 语义树

    /** 语义树里的一个节点（不合并的树）。[bounds] 是点；[texts] 是它自己的文字（不含子节点）。 */
    data class Node(
        val id: Int,
        val tag: String?,
        val texts: List<String>,
        val desc: List<String>,
        val editable: String?,
        val focused: Boolean,
        val selected: Boolean?,
        val password: Boolean,
        /** 不裁剪的位置和大小（滚出视口的节点也有真实位置）。 */
        val bounds: R,
        /** 裁到窗口里以后还有面积（看得见）。 */
        val visible: Boolean,
        val parent: Int?,
        val depth: Int,
    )

    /** 一张语义树的快照（所有 owner：窗口本身 + 弹出层）。 */
    class Tree(val nodes: List<Node>, val density: Double) {
        private val byParent = nodes.groupBy { it.parent }
        fun tag(t: String): Node? = nodes.firstOrNull { it.tag == t }
        fun tags(pred: (String) -> Boolean): List<Node> = nodes.filter { it.tag != null && pred(it.tag) }
        fun has(t: String): Boolean = tag(t) != null
        fun children(n: Node): List<Node> = byParent[n.id].orEmpty()
        fun descendants(n: Node): List<Node> = children(n).flatMap { listOf(it) + descendants(it) }

        /** 节点和它下面所有的文字（含按钮的读屏说明）。 */
        fun textUnder(n: Node): String = (listOf(n) + descendants(n)).flatMap { it.texts + it.desc + listOfNotNull(it.editable) }.joinToString(" ")
        fun textUnder(t: String): String? = tag(t)?.let(::textUnder)
        fun allText(): String = nodes.flatMap { it.texts + it.desc }.joinToString(" ")
        fun focused(): Node? = nodes.lastOrNull { it.focused }
        fun byDesc(pred: (String) -> Boolean): List<Node> = nodes.filter { n -> n.desc.any(pred) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun owners(w: ComposeWindow): Collection<SemanticsOwner> {
        val f = ComposeWindow::class.java.getDeclaredField("composePanel").apply { isAccessible = true }
        val panel = f.get(w)
        return panel.javaClass.getMethod("getSemanticsOwners").invoke(panel) as Collection<SemanticsOwner>
    }

    private fun composePanel(w: ComposeWindow): java.awt.Container =
        ComposeWindow::class.java.getDeclaredField("composePanel").apply { isAccessible = true }.get(w) as java.awt.Container

    fun tree(): Tree = ui {
        val w = window
        val out = ArrayList<Node>()
        var density = 2.0
        var seq = 0
        fun walk(n: SemanticsNode, parent: Int?, depth: Int, d: Double) {
            val c = n.config
            val clipped = n.boundsInWindow
            val pos = n.positionInWindow
            val id = seq++
            out += Node(
                id = id,
                tag = c.getOrNull(SemanticsProperties.TestTag),
                texts = c.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text },
                desc = c.getOrNull(SemanticsProperties.ContentDescription).orEmpty(),
                editable = c.getOrNull(SemanticsProperties.EditableText)?.text,
                focused = c.getOrNull(SemanticsProperties.Focused) == true,
                selected = c.getOrNull(SemanticsProperties.Selected),
                password = c.contains(SemanticsProperties.Password),
                bounds = R(pos.x / d, pos.y / d, n.size.width / d, n.size.height / d),
                visible = clipped.width > 0f && clipped.height > 0f,
                parent = parent,
                depth = depth,
            )
            for (ch in n.children) walk(ch, id, depth + 1, d)
        }
        // 像素 → 点：语义树的边界是画布像素；倍数取「根节点的像素宽 ÷ 承载它的面板的点宽」（layoutInfo.density 在真窗口里不是这个数）
        val panelW = composePanel(w).width.toDouble()
        val owners = owners(w).toList()
        owners.firstOrNull()?.unmergedRootSemanticsNode?.boundsInWindow?.width?.toDouble()?.takeIf { it > 0 && panelW > 0 }?.let { density = it / panelW }
        for (o in owners) walk(o.unmergedRootSemanticsNode, null, 0, density)
        Tree(out, density)
    }

    /**
     * 诊断用（不是输入路径）：经语义动作 RequestFocus 让带 [tag] 的节点（或它下面第一个能聚焦的）拿焦点。
     * 用来区分「点击没到」和「焦点拿到了又被别的抢走」。
     */
    fun requestFocusBySemantics(tag: String): Boolean = ui {
        val w = window
        var done = false
        fun find(n: SemanticsNode, inside: Boolean): Boolean {
            val here = inside || n.config.getOrNull(SemanticsProperties.TestTag) == tag
            val act = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
            if (here && act != null) { done = act.action?.invoke() == true; return true }
            return n.children.any { find(it, here) }
        }
        owners(w).any { find(it.unmergedRootSemanticsNode, false) }
        done
    }

    // ------------------------------------------------------------------ 输入：投递进事件队列

    private val queue get() = Toolkit.getDefaultToolkit().systemEventQueue

    /** 窗口内容里的一个点（点）→ 收这个点的组件和它自己的坐标。 */
    private fun target(xPt: Double, yPt: Double): Triple<Component, java.awt.Point, java.awt.Point> = ui {
        val panel = composePanel(window)
        val p = java.awt.Point(xPt.toInt(), yPt.toInt())
        val c = SwingUtilities.getDeepestComponentAt(panel, p.x, p.y) ?: panel
        val local = SwingUtilities.convertPoint(panel, p, c)
        val screen = java.awt.Point(p).also { SwingUtilities.convertPointToScreen(it, panel) }
        Triple(c, local, screen)
    }

    @Volatile private var lastMouse: java.awt.Point? = null

    fun move(xPt: Double, yPt: Double) {
        val (c, p, s) = target(xPt, yPt)
        queue.postEvent(MouseEvent(c, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, p.x, p.y, s.x, s.y, 0, false, MouseEvent.NOBUTTON))
        lastMouse = java.awt.Point(xPt.toInt(), yPt.toInt())
        settle(40)
    }

    /** 鼠标在 ([xPt], [yPt]) 处按下抬起（先移过去，让悬停状态先到）。 */
    fun click(xPt: Double, yPt: Double, button: Int = MouseEvent.BUTTON1, count: Int = 1) {
        move(xPt, yPt)
        val (c, p, s) = target(xPt, yPt)
        val down = InputEvent.getMaskForButton(button)
        val t = System.currentTimeMillis()
        queue.postEvent(MouseEvent(c, MouseEvent.MOUSE_PRESSED, t, down, p.x, p.y, s.x, s.y, count, false, button))
        settle(30)
        queue.postEvent(MouseEvent(c, MouseEvent.MOUSE_RELEASED, t + 60, 0, p.x, p.y, s.x, s.y, count, false, button))
        queue.postEvent(MouseEvent(c, MouseEvent.MOUSE_CLICKED, t + 60, 0, p.x, p.y, s.x, s.y, count, false, button))
        settle(150)
    }

    fun click(n: Node) = click(n.bounds.cx, n.bounds.cy)

    /** 滚轮（[notches] > 0 = 往下读）。 */
    fun wheel(xPt: Double, yPt: Double, notches: Int) {
        move(xPt, yPt)
        val (c, p, s) = target(xPt, yPt)
        val step = if (notches > 0) 1 else -1
        repeat(kotlin.math.abs(notches)) {
            queue.postEvent(MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, p.x, p.y, s.x, s.y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, step, step.toDouble()))
            settle(25)
        }
        settle(200)
    }

    private fun focusTarget(): Component = ui { window.mostRecentFocusOwner ?: window }

    private fun postKey(id: Int, code: Int, ch: Char, mods: Int, location: Int) {
        queue.postEvent(KeyEvent(focusTarget(), id, System.currentTimeMillis(), mods, code, ch, location))
    }

    /** 一个键：按下（+ 打出的字）+ 抬起。[cmd] / [shift] / [ctrl] / [alt] 照真的那样先按修饰键。 */
    fun key(code: Int, ch: Char = KeyEvent.CHAR_UNDEFINED, shift: Boolean = false, cmd: Boolean = false, ctrl: Boolean = false, alt: Boolean = false, typed: Boolean = ch != KeyEvent.CHAR_UNDEFINED && !cmd && !ctrl) {
        var mods = 0
        val pressedMods = ArrayList<Pair<Int, Int>>()
        fun mod(on: Boolean, vk: Int, mask: Int) {
            if (!on) return
            mods = mods or mask
            postKey(KeyEvent.KEY_PRESSED, vk, KeyEvent.CHAR_UNDEFINED, mods, KeyEvent.KEY_LOCATION_LEFT)
            pressedMods += vk to mask
        }
        mod(cmd, KeyEvent.VK_META, InputEvent.META_DOWN_MASK)
        mod(ctrl, KeyEvent.VK_CONTROL, InputEvent.CTRL_DOWN_MASK)
        mod(alt, KeyEvent.VK_ALT, InputEvent.ALT_DOWN_MASK)
        mod(shift, KeyEvent.VK_SHIFT, InputEvent.SHIFT_DOWN_MASK)
        postKey(KeyEvent.KEY_PRESSED, code, ch, mods, KeyEvent.KEY_LOCATION_STANDARD)
        if (typed) postKey(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, ch, mods, KeyEvent.KEY_LOCATION_UNKNOWN)
        postKey(KeyEvent.KEY_RELEASED, code, ch, mods, KeyEvent.KEY_LOCATION_STANDARD)
        for ((vk, mask) in pressedMods.asReversed()) {
            mods = mods and mask.inv()
            postKey(KeyEvent.KEY_RELEASED, vk, KeyEvent.CHAR_UNDEFINED, mods, KeyEvent.KEY_LOCATION_LEFT)
        }
        settle(60)
    }

    /** 一串字（美式键盘打得出来的）一个键一个键地打。 */
    fun type(text: String, perKeyMs: Long = 25) {
        for (c in text) {
            val s = strokeOf(c) ?: error("打不出这个字符：$c")
            key(s.keyCode, s.char, shift = s.shift)
            if (perKeyMs > 0) Thread.sleep(perKeyMs)
        }
        settle(150)
    }

    /**
     * 输入法「上屏」：中文（或键盘打不出来的字）由输入法交给焦点所在的组件——AWT 里就是一个带已确认文字的 `InputMethodEvent`，
     * 和拼音输入法回车上屏走同一条路（候选窗本身不在这条路上，要人看）。
     */
    fun commitText(text: String) {
        val it = java.text.AttributedString(text).iterator
        queue.postEvent(java.awt.event.InputMethodEvent(focusTarget(), java.awt.event.InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, it, text.length, null, null))
        settle(250)
    }

    // ------------------------------------------------------------------ 截图（只截自己的窗口）

    val shotDir: File get() = File(out, "shots").apply { mkdirs() }
    private val shotSeq = java.util.concurrent.atomic.AtomicInteger()
    val shotsTaken = java.util.Collections.synchronizedList(ArrayList<Pair<String, String>>())
    /** 截得到图 = 有屏幕录制权限；一次都截不到 = 缺权限（像素读数类检查记 env-skip，不算没过）。null = 还没截过。 */
    var screenRecordingOk: Boolean? = null

    /** 截本窗口，返回相对于输出目录的路径（失败 = null）。 */
    fun shot(name: String, label: String = name): String? {
        val number = windowNumber() ?: return null
        val file = File(shotDir, "%02d-%s.png".format(shotSeq.incrementAndGet(), name))
        val ok = runCatching {
            ProcessBuilder("screencapture", "-x", "-o", "-l$number", file.path).redirectErrorStream(true).start().let { p ->
                p.inputStream.readAllBytes()
                p.waitFor()
            }
        }.isSuccess && file.isFile && file.length() > 0
        screenRecordingOk = ok
        if (!ok) return null
        val rel = "shots/${file.name}"
        shotsTaken += rel to label
        return rel
    }

    fun image(rel: String): java.awt.image.BufferedImage? = runCatching { ImageIO.read(File(out, rel)) }.getOrNull()

    /** 截图的像素 / 点（Retina = 2）：图宽 ÷ 窗口宽。 */
    fun shotScale(img: java.awt.image.BufferedImage): Double = img.width.toDouble() / ui { window.width }.coerceAtLeast(1)

    // ------------------------------------------------------------------ AppKit（JNA）

    fun windowNumber(): Long? = runCatching { Objc.onMain { Objc.msgLong(nsWindow(), "windowNumber") } }.getOrNull()?.takeIf { it > 0 }

    private fun nsWindow(): Pointer = Pointer(window.windowHandle.takeIf { it != 0L } ?: error("没有 NSWindow"))

    /** 我们的 NSWindow（只在 [Objc.onMain] 里用）。 */
    fun nsWindowPointer(): Pointer = nsWindow()

    /** 红绿灯三个钮：(在不在、藏没藏、窗口里的矩形)；矩形是点、左上为原点。 */
    fun trafficLights(): List<Triple<Boolean, Boolean, R?>> = Objc.onMain {
        val win = nsWindow()
        val frame = Objc.msgRect(win, "frame")
        (0L..2L).map { i ->
            val b = Objc.msg(win, "standardWindowButton:", i)
            if (b == null) Triple(false, true, null) else {
                val hidden = Objc.msgBool(b, "isHidden")
                val r = Objc.msgRect(b, "convertRect:toView:", Objc.msgRect(b, "bounds"), null)
                Triple(true, hidden, R(r.x, frame.h - (r.y + r.h), r.w, r.h))
            }
        }
    }

    fun isFullScreen(): Boolean = Objc.onMain { (Objc.msgLong(nsWindow(), "styleMask") and (1L shl 14)) != 0L }

    fun toggleFullScreen() = Objc.onMain { Objc.send(nsWindow(), "toggleFullScreen:", null) }

    /** 窗口所在屏幕的安全区顶边（刘海那一条；没有刘海 = 0）和屏幕的点数尺寸。 */
    fun screenSafeTop(): Pair<Double, R> = Objc.onMain {
        val screen = Objc.msg(nsWindow(), "screen") ?: Objc.msg(Objc.cls("NSScreen"), "mainScreen")!!
        val f = Objc.msgRect(screen, "frame")
        val insets = runCatching { Objc.msgInsets(screen, "safeAreaInsets") }.getOrNull()
        (insets?.top ?: 0.0) to R(f.x, f.y, f.w, f.h)
    }

    /** 应用失焦：让 AppKit 把本应用切到后台（窗口留在屏幕上，只是不是前台）。返回用了哪一种办法。 */
    fun deactivate(): String {
        Objc.onMain { Objc.send(Objc.msg(Objc.cls("NSApplication"), "sharedApplication")!!, "deactivate") }
        if (waitFor(2_000) { !ui { window.isFocused } }) return "NSApp deactivate"
        // 退路：激活访达（LaunchServices，不要任何权限），本应用自然退到后台
        runCatching { ProcessBuilder("open", "-a", "Finder").start().waitFor() }
        return if (waitFor(3_000) { !ui { window.isFocused } }) "open -a Finder" else "没能失焦"
    }

    /** 复焦：把本应用叫回前台、窗口成为主窗口。返回用了哪一种办法（叫不回来 = 「没能复焦」）。 */
    fun activate(): String {
        if (ui { window.isFocused }) return "本来就在前台"
        val app = Objc.onMain { Objc.msg(Objc.cls("NSApplication"), "sharedApplication")!! }
        val tries = listOf<Pair<String, () -> Unit>>(
            "activateIgnoringOtherApps" to { Objc.onMain { Objc.send(app, "activateIgnoringOtherApps:", 1L); Objc.send(nsWindow(), "makeKeyAndOrderFront:", null) } },
            "NSApp hide + unhide" to { Objc.onMain { Objc.send(app, "hide:", null) }; Thread.sleep(400); Objc.onMain { Objc.send(app, "unhide:", null); Objc.send(nsWindow(), "makeKeyAndOrderFront:", null) } },
            "NSRunningApplication activate" to { Objc.onMain { Objc.msgLong(Objc.msg(Objc.cls("NSRunningApplication"), "currentApplication")!!, "activateWithOptions:", 3L) } },
        )
        for ((name, f) in tries) {
            runCatching(f)
            if (waitFor(2_500) { ui { window.isFocused } }) {
                ui { window.toFront(); window.requestFocus() }
                return name
            }
        }
        // 从 PI-Desktop 里跑时 macOS「协作式激活」不让应用自己抢前台，上面三种都会失败：
        // 写约定请求文件（PID + 标题栏上一个点），请 scripts/accept-mac.mjs 经 agent-desktop 代劳复焦
        // （focus-window 要辅助功能元素；Compose 窗口没有时就物理点一下标题栏）。
        val req = File(out, "need-focus")
        runCatching {
            val b = ui { window.bounds }
            req.writeText("${ProcessHandle.current().pid()} ${b.x + b.width / 2} ${b.y + 12}")
        }
        if (waitFor(8_000, 200) { ui { window.isFocused } }) {
            ui { window.toFront(); window.requestFocus() }
            runCatching { req.delete() }
            return "agent-desktop focus-window"
        }
        runCatching { req.delete() }
        return "没能复焦"
    }

    /** 应用自己的可见 NSWindow（类名）。全屏时 AppKit 自己多一扇 `NSToolbarFullScreenWindow`。 */
    fun visibleNsWindows(): List<String> = Objc.onMain {
        val app = Objc.msg(Objc.cls("NSApplication"), "sharedApplication")!!
        Objc.array(Objc.msg(app, "windows")).filter { Objc.msgBool(it, "isVisible") }.map { Objc.className(it) }
    }

    /** 主菜单的顶层项数（Compose 没装菜单栏时只有应用菜单那 1 项）。 */
    fun mainMenuItems(): List<String> = Objc.onMain {
        val app = Objc.msg(Objc.cls("NSApplication"), "sharedApplication")!!
        val menu = Objc.msg(app, "mainMenu") ?: return@onMain emptyList()
        Objc.array(Objc.msg(menu, "itemArray")).map { Objc.string(Objc.msg(it, "title")) ?: "?" }
    }

    /** 某个顶层菜单下的条目（标题 + 是否可用）；系统自动加的项也在里面（读的是真的系统菜单）。 */
    fun submenuItems(title: String): List<Pair<String, Boolean>> = Objc.onMain {
        val app = Objc.msg(Objc.cls("NSApplication"), "sharedApplication")!!
        val menu = Objc.msg(app, "mainMenu") ?: return@onMain emptyList()
        val item = Objc.array(Objc.msg(menu, "itemArray")).firstOrNull { Objc.string(Objc.msg(it, "title")) == title } ?: return@onMain emptyList()
        val sub = Objc.msg(item, "submenu") ?: return@onMain emptyList()
        Objc.array(Objc.msg(sub, "itemArray")).map { (Objc.string(Objc.msg(it, "title")) ?: "?") to Objc.msgBool(it, "isEnabled") }
    }

    /** 真的点系统菜单里的一条（走 NSMenu 的 action，和人手点同一条路）。 */
    fun clickMenuItem(title: String, itemTitle: String): Boolean = Objc.onMain {
        val app = Objc.msg(Objc.cls("NSApplication"), "sharedApplication")!!
        val menu = Objc.msg(app, "mainMenu") ?: return@onMain false
        val item = Objc.array(Objc.msg(menu, "itemArray")).firstOrNull { Objc.string(Objc.msg(it, "title")) == title } ?: return@onMain false
        val sub = Objc.msg(item, "submenu") ?: return@onMain false
        val idx = Objc.array(Objc.msg(sub, "itemArray")).indexOfFirst { Objc.string(Objc.msg(it, "title")) == itemTitle }
        if (idx < 0) return@onMain false
        Objc.send(sub, "performActionForItemAtIndex:", idx)
        true
    }

    /** 顶层菜单 [title] 下子菜单 [subTitle] 里的条目：标题、可用、打勾（NSMenuItem.state == on）。1.5.0「片库 › 换服务器」用。 */
    fun nestedMenuItems(title: String, subTitle: String): List<Triple<String, Boolean, Boolean>> = Objc.onMain {
        val sub = nestedMenu(title, subTitle) ?: return@onMain emptyList()
        Objc.array(Objc.msg(sub, "itemArray")).map { Triple(Objc.string(Objc.msg(it, "title")) ?: "?", Objc.msgBool(it, "isEnabled"), Objc.msgLong(it, "state") == 1L) }
    }

    /** 真的点子菜单里的一条（同 [clickMenuItem]，多一层）。 */
    fun clickNestedMenuItem(title: String, subTitle: String, itemTitle: String): Boolean = Objc.onMain {
        val sub = nestedMenu(title, subTitle) ?: return@onMain false
        val idx = Objc.array(Objc.msg(sub, "itemArray")).indexOfFirst { Objc.string(Objc.msg(it, "title")) == itemTitle }
        if (idx < 0) return@onMain false
        Objc.send(sub, "performActionForItemAtIndex:", idx)
        true
    }

    private fun nestedMenu(title: String, subTitle: String): Pointer? {
        val app = Objc.msg(Objc.cls("NSApplication"), "sharedApplication")!!
        val menu = Objc.msg(app, "mainMenu") ?: return null
        val top = Objc.array(Objc.msg(menu, "itemArray")).firstOrNull { Objc.string(Objc.msg(it, "title")) == title } ?: return null
        val sub = Objc.msg(top, "submenu") ?: return null
        val item = Objc.array(Objc.msg(sub, "itemArray")).firstOrNull { Objc.string(Objc.msg(it, "title")) == subTitle } ?: return null
        return Objc.msg(item, "submenu")
    }

    /** Dock 图标菜单的条目（M2-10；Taskbar 的 PopupMenu）。 */
    fun dockMenuLabels(): List<String> = runCatching {
        val tb = java.awt.Taskbar.getTaskbar()
        if (!java.awt.Taskbar.isTaskbarSupported() || !tb.isSupported(java.awt.Taskbar.Feature.MENU)) return@runCatching emptyList()
        val menu = tb.menu ?: return@runCatching emptyList()
        (0 until menu.itemCount).map { menu.getItem(it).label }
    }.getOrDefault(emptyList())

    /** 点 Dock 菜单的第 [i] 条（调它自己的 ActionListener）。 */
    fun clickDockItem(i: Int): Boolean = runCatching {
        val menu = java.awt.Taskbar.getTaskbar().menu ?: return@runCatching false
        val item = menu.getItem(i)
        val l = item.actionListeners.firstOrNull() ?: return@runCatching false
        l.actionPerformed(java.awt.event.ActionEvent(item, java.awt.event.ActionEvent.ACTION_PERFORMED, item.label))
        true
    }.getOrDefault(false)

    /**
     * 视频层：窗口内容层下面那个装着 Metal 层的容器（`:player-apple` 放的），换算成点、左上为原点；还有内容层本身的尺寸。
     * 没有（不在播放 / 已退场）= null。
     */
    fun videoLayer(): Pair<R, R>? = Objc.onMain {
        val content = Objc.msg(nsWindow(), "contentView") ?: return@onMain null
        val host = Objc.msg(content, "layer") ?: return@onMain null
        val hb = Objc.msgRect(host, "bounds")
        val flipped = Objc.msgBool(host, "isGeometryFlipped")
        val box = Objc.array(Objc.msg(host, "sublayers")).firstOrNull { l ->
            Objc.array(Objc.msg(l, "sublayers")).any { Objc.className(it).contains("MetalLayer") }
        } ?: return@onMain null
        if (Objc.msgBool(box, "isHidden")) return@onMain null
        val f = Objc.msgRect(box, "frame")
        val y = if (flipped) f.y else hb.h - f.y - f.h
        R(f.x, y, f.w, f.h) to R(0.0, 0.0, hb.w, hb.h)
    }

    // ------------------------------------------------------------------ 进程

    /** 本进程的物理占用（`phys_footprint`，和 `vmmap` 的 Physical footprint 同一个数；字节）。D-mac-4 用这个，不用 RSS。 */
    fun physFootprint(): Long = runCatching {
        val mem = Memory(512)
        val rc = NativeLibrary.getInstance("System").getFunction("proc_pid_rusage")
            .invokeInt(arrayOf<Any>(ProcessHandle.current().pid().toInt(), 2 /* RUSAGE_INFO_V2 */, mem))
        if (rc != 0) -1L else mem.getLong(72) // ri_phys_footprint：uuid[16] + 7 个 uint64 之后
    }.getOrDefault(-1L)
}

/** 运行器自己的一点 ObjC 运行时调用（纯 JNA，不带原生库）。AppKit 的调用一律 `onMain`。 */
internal object Objc {
    private val objc by lazy { NativeLibrary.getInstance("objc") }
    private val system by lazy { NativeLibrary.getInstance("System") }
    private val msgSend: Function by lazy { objc.getFunction("objc_msgSend") }
    private val sels = ConcurrentHashMap<String, Pointer>()

    @Structure.FieldOrder("x", "y", "w", "h")
    class Rect : Structure(), Structure.ByValue {
        @JvmField var x = 0.0
        @JvmField var y = 0.0
        @JvmField var w = 0.0
        @JvmField var h = 0.0
    }

    @Structure.FieldOrder("w", "h")
    class Size : Structure(), Structure.ByValue {
        @JvmField var w = 0.0
        @JvmField var h = 0.0
    }

    @Structure.FieldOrder("top", "left", "bottom", "right")
    class Insets : Structure(), Structure.ByValue {
        @JvmField var top = 0.0
        @JvmField var left = 0.0
        @JvmField var bottom = 0.0
        @JvmField var right = 0.0
    }

    fun cls(name: String): Pointer = objc.getFunction("objc_getClass").invokePointer(arrayOf<Any>(name)) ?: error("没有 ObjC 类 $name")
    fun sel(name: String): Pointer = sels.getOrPut(name) { objc.getFunction("sel_registerName").invokePointer(arrayOf<Any>(name)) }
    private fun pack(t: Pointer, s: String, a: Array<out Any?>): Array<Any?> = arrayOfNulls<Any?>(a.size + 2).also { it[0] = t; it[1] = sel(s); a.copyInto(it, 2) }

    fun msg(t: Pointer, s: String, vararg a: Any?): Pointer? = msgSend.invokePointer(pack(t, s, a))
    fun send(t: Pointer, s: String, vararg a: Any?) = msgSend.invokeVoid(pack(t, s, a))
    fun msgLong(t: Pointer, s: String, vararg a: Any?): Long = msgSend.invokeLong(pack(t, s, a))
    fun msgBool(t: Pointer, s: String, vararg a: Any?): Boolean = (msgSend.invokeInt(pack(t, s, a)) and 0xff) != 0
    fun msgRect(t: Pointer, s: String, vararg a: Any?): Rect = msgSend.invoke(Rect::class.java, pack(t, s, a)) as Rect
    fun msgSize(t: Pointer, s: String): Pair<Double, Double> = (msgSend.invoke(Size::class.java, pack(t, s, emptyArray())) as Size).let { it.w to it.h }
    fun msgInsets(t: Pointer, s: String): Insets = msgSend.invoke(Insets::class.java, pack(t, s, emptyArray())) as Insets
    fun className(o: Pointer): String = objc.getFunction("object_getClassName").invokePointer(arrayOf<Any>(o))?.getString(0) ?: "?"
    fun string(ns: Pointer?): String? = ns?.let { msg(it, "UTF8String")?.getString(0, "UTF-8") }
    fun array(ns: Pointer?): List<Pointer> = if (ns == null) emptyList() else (0 until msgLong(ns, "count")).mapNotNull { msg(ns, "objectAtIndex:", it) }

    fun interface Work : Callback {
        fun invoke(ctx: Pointer?)
    }

    /** 在 AppKit 主线程上跑并等它（调用者不能是主线程在等的那条线：不在 AWT 事件线程上调）。 */
    fun <T> onMain(block: () -> T): T {
        if (system.getFunction("pthread_main_np").invokeInt(emptyArray()) != 0) return block()
        var r: Result<T>? = null
        val work = Work { r = runCatching(block) }
        system.getFunction("dispatch_sync_f").invokeVoid(arrayOf<Any?>(system.getGlobalVariableAddress("_dispatch_main_q"), null, work))
        return r!!.getOrThrow()
    }
}
