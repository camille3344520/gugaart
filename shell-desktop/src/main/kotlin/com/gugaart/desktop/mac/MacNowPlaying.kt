package com.gugaart.desktop.mac

import com.gugugaga.app.player.ClockSnapshot
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlayerSnapshot
import com.sun.jna.Callback
import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.floor

/*
 * macOS 的「正在播放」和媒体键（任务单 MP-6；ADR 0004 §2.7.6）：播放器的状态报给 MPNowPlayingInfoCenter，
 * 控制中心 / 锁屏 / 键盘媒体键 / 耳机键（MPRemoteCommandCenter）接回 PlayController。纯 JNA 的 ObjC 桥，没有原生库、没有新依赖。
 * 内核自己的那一套是关着的（上游 `input-media-keys=no`），只有这里在报。
 */

/** 要报给系统的那几样（从控制器的两条状态流里取出来的）。 */
internal data class NowPlayingState(
    val itemId: String,
    val title: String,
    val subtitle: String,
    val durationMs: Double,
    val positionMs: Double,
    val paused: Boolean,
    val buffering: Boolean,
    val rate: Double,
    val canNext: Boolean,
    val canPrev: Boolean,
)

/** 字典的键：值是 MediaPlayer.framework 里那个 NSString 常量的符号名。 */
internal enum class NowPlayingKey(val symbol: String) {
    Title("MPMediaItemPropertyTitle"),
    Artist("MPMediaItemPropertyArtist"),
    Duration("MPMediaItemPropertyPlaybackDuration"),
    Artwork("MPMediaItemPropertyArtwork"),
    Elapsed("MPNowPlayingInfoPropertyElapsedPlaybackTime"),
    Rate("MPNowPlayingInfoPropertyPlaybackRate"),
    DefaultRate("MPNowPlayingInfoPropertyDefaultPlaybackRate"),
    MediaType("MPNowPlayingInfoPropertyMediaType"),
}

/** 「状态 → 要报的字典」和「要不要再报一次」：纯函数（单测）。 */
internal object NowPlayingInfo {
    /** MPNowPlayingInfoMediaTypeVideo */
    const val MEDIA_TYPE_VIDEO = 2L

    /** 系统按「位置 + 速率 × 过去的时间」自己往前推；和它推的差出这么多才再报（拖动、卡顿）。 */
    const val DRIFT_MS = 1_500.0

    /** 系统眼里现在走多快：暂停 / 缓冲 = 0（时间不走）。 */
    fun effectiveRate(s: NowPlayingState): Double = if (s.paused || s.buffering) 0.0 else s.rate

    /**
     * 要报的字典。值只有 String / Double / Long 和 [artwork]（原样放进去）。
     * 暂停时速率 0；位置是整秒（向下取）；没有封面就没有封面键；没有副标题就没有那一键；时长还不知道就不报时长。
     */
    fun info(s: NowPlayingState, artwork: Any? = null): Map<NowPlayingKey, Any> = buildMap {
        put(NowPlayingKey.Title, s.title)
        if (s.subtitle.isNotEmpty()) put(NowPlayingKey.Artist, s.subtitle)
        if (s.durationMs > 0) put(NowPlayingKey.Duration, s.durationMs / 1000.0)
        put(NowPlayingKey.Elapsed, floor(s.positionMs.coerceAtLeast(0.0) / 1000.0))
        put(NowPlayingKey.Rate, effectiveRate(s))
        put(NowPlayingKey.DefaultRate, s.rate)
        put(NowPlayingKey.MediaType, MEDIA_TYPE_VIDEO)
        if (artwork != null) put(NowPlayingKey.Artwork, artwork)
    }

    /** 上次报的是 [last]（在 [lastAtMs]）；现在是 [now]（在 [nowMs]）。位置之外有变化、或者位置和系统自己推的对不上 → 再报。 */
    fun needsPublish(last: NowPlayingState?, lastAtMs: Long, now: NowPlayingState, nowMs: Long): Boolean {
        if (last == null) return true
        if (last.copy(positionMs = 0.0) != now.copy(positionMs = 0.0)) return true
        val expected = last.positionMs + (nowMs - lastAtMs) * effectiveRate(last)
        return abs(now.positionMs - expected) > DRIFT_MS
    }

    /** 控制器的两条状态流 → 要报的状态；还没有条目（刚进页面）= null，不报。 */
    fun stateOf(snap: PlayerSnapshot, clock: ClockSnapshot): NowPlayingState? {
        if (snap.itemId.isEmpty() || snap.title.isEmpty() || snap.phase == PlayPhase.Error) return null
        return NowPlayingState(
            itemId = snap.itemId,
            title = snap.title,
            subtitle = snap.subtitle,
            durationMs = clock.durationMs,
            positionMs = clock.positionMs,
            paused = clock.paused,
            buffering = clock.buffering,
            rate = clock.rate,
            canNext = snap.next != null,
            canPrev = snap.prev != null,
        )
    }
}

/** 遥控命令（控制中心的钮、媒体键、耳机键）。 */
internal sealed interface RemoteCommand {
    data object Play : RemoteCommand
    data object Pause : RemoteCommand
    data object Toggle : RemoteCommand
    data object Next : RemoteCommand
    data object Previous : RemoteCommand
    data class Seek(val positionMs: Double) : RemoteCommand
}

/** 系统那一头（测试给假的）。 */
internal interface NowPlayingCenter {
    /** 报一次；[artwork] = 封面的字节（null = 不带封面键）。 */
    fun publish(state: NowPlayingState, artwork: ByteArray?)

    /** 命令从哪来回哪去；重复调用只换 [handler]。 */
    fun commands(handler: (RemoteCommand) -> Unit)

    /** 离开播放器：卡片消失、命令摘掉。 */
    fun clear()
}

/**
 * 把一个播放器页的 [PlayController] 接到系统的「正在播放」。[attach] 可以重复调用（同一个控制器只接一次）；[detach] 清掉。
 * 命令在 AWT 事件线程上交给控制器（和界面上的钮同一个线程）。
 */
internal class MacNowPlaying(
    private val scope: CoroutineScope,
    private val center: NowPlayingCenter,
    /** 这一条的封面地址（null = 没有封面）。 */
    private val artworkUrl: (PlayerSnapshot) -> String?,
    private val fetch: suspend (String) -> ByteArray?,
    private val onEventThread: (() -> Unit) -> Unit = { javax.swing.SwingUtilities.invokeLater(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var attached: PlayController? = null
    private var job: Job? = null

    @Synchronized
    fun attach(controller: PlayController) {
        if (attached === controller) return
        detach()
        attached = controller
        center.commands { c ->
            onEventThread {
                if (attached === controller) when (c) {
                    RemoteCommand.Play -> controller.play()
                    RemoteCommand.Pause -> controller.pause()
                    RemoteCommand.Toggle -> controller.togglePlay()
                    RemoteCommand.Next -> controller.playNext()
                    RemoteCommand.Previous -> controller.playPrev()
                    is RemoteCommand.Seek -> controller.seekTo(c.positionMs)
                }
            }
        }
        // 一条线上跑：下面几个变量不加锁
        job = scope.launch(kotlinx.coroutines.Dispatchers.Default.limitedParallelism(1)) {
            var last: NowPlayingState? = null
            var lastAt = 0L
            var artFor: String? = null
            var art: ByteArray? = null
            var artJob: Job? = null
            combine(controller.snapshot, controller.clock) { s, c -> s to c }.conflate().collect { (snap, clock) ->
                val state = NowPlayingInfo.stateOf(snap, clock) ?: return@collect
                if (artFor != state.itemId) {
                    // 换了一条：旧封面不留；新的取到了再补报一次
                    artFor = state.itemId
                    art = null
                    artJob?.cancel()
                    val url = runCatching { artworkUrl(snap) }.getOrNull()
                    if (url != null) artJob = launch {
                        val bytes = runCatching { fetch(url) }.getOrNull() ?: return@launch
                        if (artFor == state.itemId) {
                            art = bytes
                            last?.let {
                                val again = it.copy(positionMs = controller.clock.value.positionMs)
                                last = again
                                lastAt = now()
                                center.publish(again, art)
                            }
                        }
                    }
                }
                val t = now()
                if (NowPlayingInfo.needsPublish(last, lastAt, state, t)) {
                    last = state
                    lastAt = t
                    center.publish(state, art)
                }
            }
        }
    }

    @Synchronized
    fun detach() {
        if (attached == null) return
        attached = null
        job?.cancel()
        job = null
        center.clear()
    }
}

/**
 * 真的那一头：MPNowPlayingInfoCenter / MPRemoteCommandCenter，经 JNA 调 ObjC 运行时。所有 ObjC 调用都排到 AppKit 主线程
 * （不等它：发起的线程可能是 AWT 事件线程）。命令的回调也在主线程上来。
 *
 * 两处要「造」东西，都只用运行时接口：
 *  - 命令的目标：`objc_allocateClassPair` 造一个 NSObject 子类，`class_addMethod` 把一个 JNA 回调挂成它的方法（`addTarget:action:`）；
 *  - 封面：`MPMediaItemArtwork` 只收块（block）——按块的内存布局手写一个全局块（`_NSConcreteGlobalBlock`，不捕获变量），
 *    调用指针是一个 JNA 回调。
 */
internal object SystemNowPlayingCenter : NowPlayingCenter {
    private const val PLAYING = 1L
    private const val PAUSED = 2L
    private const val STOPPED = 3L

    /** MPRemoteCommandHandlerStatusSuccess / …CommandFailed */
    private const val SUCCESS = 0L
    private const val FAILED = 200L

    private val objc by lazy { NativeLibrary.getInstance("objc") }
    private val system by lazy { NativeLibrary.getInstance("System") }
    private val mediaPlayer by lazy { NativeLibrary.getInstance("/System/Library/Frameworks/MediaPlayer.framework/MediaPlayer") }
    private val msgSend: Function by lazy { objc.getFunction("objc_msgSend") }
    private val selectors = ConcurrentHashMap<String, Pointer>()

    private fun cls(name: String): Pointer = objc.getFunction("objc_getClass").invokePointer(arrayOf<Any>(name)) ?: error("no Objective-C class $name")
    private fun sel(name: String): Pointer = selectors.getOrPut(name) { objc.getFunction("sel_registerName").invokePointer(arrayOf<Any>(name)) }
    private fun pack(target: Pointer, selector: String, args: Array<out Any?>): Array<Any?> =
        arrayOfNulls<Any?>(args.size + 2).also { it[0] = target; it[1] = sel(selector); args.copyInto(it, 2) }

    private fun msg(target: Pointer, selector: String, vararg args: Any?): Pointer? = msgSend.invokePointer(pack(target, selector, args))
    private fun send(target: Pointer, selector: String, vararg args: Any?) { msgSend.invokeVoid(pack(target, selector, args)) }
    private fun msgDouble(target: Pointer, selector: String): Double = msgSend.invokeDouble(pack(target, selector, emptyArray()))
    private fun msgLong(target: Pointer, selector: String): Long = msgSend.invokeLong(pack(target, selector, emptyArray()))
    private fun nsString(s: String): Pointer = msg(cls("NSString"), "stringWithUTF8String:", s)!!
    private fun constant(symbol: String): Pointer = mediaPlayer.getGlobalVariableAddress(symbol).getPointer(0)

    fun interface Work : Callback {
        fun invoke(ctx: Pointer?)
    }

    private val pending = ConcurrentHashMap<Long, Work>()
    private val seq = AtomicLong()

    /** 排到主线程，不等。回调对象在跑完之前要有人拿着。 */
    private fun onMain(block: () -> Unit) {
        val id = seq.incrementAndGet()
        val work = Work {
            pending.remove(id)
            runCatching {
                mediaPlayer // 框架先装进来，它的类才查得到
                block()
            }.onFailure { System.err.println("GuguPlayer [nowplaying] ${it::class.simpleName}: ${it.message}") }
        }
        pending[id] = work
        system.getFunction("dispatch_async_f").invokeVoid(arrayOf<Any?>(system.getGlobalVariableAddress("_dispatch_main_q"), null, work))
    }

    // ---- 命令 ----

    fun interface Action : Callback {
        fun invoke(self: Pointer?, cmd: Pointer?, event: Pointer?): Long
    }

    private val commandSelectors = listOf(
        "playCommand" to "guguPlay:",
        "pauseCommand" to "guguPause:",
        "togglePlayPauseCommand" to "guguToggle:",
        "nextTrackCommand" to "guguNext:",
        "previousTrackCommand" to "guguPrevious:",
        "changePlaybackPositionCommand" to "guguSeek:",
    )

    @Volatile private var handler: ((RemoteCommand) -> Unit)? = null
    private var target: Pointer? = null
    private var registered = false

    /** 回调对象要一直有人拿着（方法表里只有它的函数指针）。 */
    private val action = Action { _, cmd, event ->
        val h = handler ?: return@Action FAILED
        val command = when (cmd) {
            sel("guguPlay:") -> RemoteCommand.Play
            sel("guguPause:") -> RemoteCommand.Pause
            sel("guguToggle:") -> RemoteCommand.Toggle
            sel("guguNext:") -> RemoteCommand.Next
            sel("guguPrevious:") -> RemoteCommand.Previous
            sel("guguSeek:") -> event?.let { RemoteCommand.Seek(msgDouble(it, "positionTime") * 1000.0) }
            else -> null
        } ?: return@Action FAILED
        runCatching { h(command) }
        SUCCESS
    }

    /** 主线程上。造一次目标类和它的一个实例。 */
    private fun target(): Pointer {
        target?.let { return it }
        val name = "GuguNowPlayingTarget"
        var c = objc.getFunction("objc_getClass").invokePointer(arrayOf<Any>(name))
        if (c == null) {
            c = objc.getFunction("objc_allocateClassPair").invokePointer(arrayOf<Any?>(cls("NSObject"), name, 0L)) ?: error("objc_allocateClassPair")
            // 返回 NSInteger（q），参数 self（@）、_cmd（:）、事件（@）
            for ((_, s) in commandSelectors) objc.getFunction("class_addMethod").invokeInt(arrayOf<Any?>(c, sel(s), action, "q@:@"))
            objc.getFunction("objc_registerClassPair").invokeVoid(arrayOf<Any?>(c))
        }
        return msg(c, "new")!!.also { target = it }
    }

    override fun commands(handler: (RemoteCommand) -> Unit) {
        this.handler = handler
        onMain {
            if (registered) return@onMain
            val center = msg(cls("MPRemoteCommandCenter"), "sharedCommandCenter")!!
            val t = target()
            for ((command, s) in commandSelectors) {
                val c = msg(center, command)!!
                send(c, "addTarget:action:", t, sel(s))
                send(c, "setEnabled:", 1)
            }
            registered = true
        }
    }

    // ---- 封面 ----

    fun interface ImageForSize : Callback {
        fun invoke(block: Pointer?, width: Double, height: Double): Pointer?
    }

    /** 现在这张封面（NSImage，我们持有一次引用）；块被问到时交出它。 */
    @Volatile private var image: Pointer? = null
    private var imageOf: ByteArray? = null
    private var artwork: Pointer? = null
    private val imageForSize = ImageForSize { _, _, _ -> image }

    /** 手写的全局块：isa / flags / reserved / invoke / descriptor（32 字节），descriptor = reserved / size（16 字节）。 */
    private val block: Memory by lazy {
        val descriptor = Memory(16).apply { setLong(0, 0); setLong(8, 32) }
        Memory(32 + 16).apply {
            setPointer(0, system.getGlobalVariableAddress("_NSConcreteGlobalBlock"))
            setInt(8, 1 shl 28) // BLOCK_IS_GLOBAL
            setInt(12, 0)
            setPointer(16, CallbackReference.getFunctionPointer(imageForSize))
            // descriptor 跟在块后面，同一块内存，一起活着
            write(32, descriptor.getByteArray(0, 16), 0, 16)
            setPointer(24, share(32))
        }
    }

    /** 主线程上。[bytes] 换了才重建；返回 MPMediaItemArtwork 或 null。 */
    private fun artworkFor(bytes: ByteArray?): Pointer? {
        if (bytes === imageOf) return artwork
        imageOf = bytes
        val old = image
        val oldArtwork = artwork
        image = null
        artwork = null
        if (bytes != null) {
            val mem = Memory(bytes.size.toLong()).apply { write(0, bytes, 0, bytes.size) }
            val data = msg(cls("NSData"), "dataWithBytes:length:", mem, bytes.size.toLong())
            val img = data?.let { msg(msg(cls("NSImage"), "alloc")!!, "initWithData:", it) }
            if (img != null) {
                val w = msgSizeWidth(img)
                image = img
                artwork = msg(msg(cls("MPMediaItemArtwork"), "alloc")!!, "initWithBoundsSize:requestHandler:", w.first, w.second, block)
            }
        }
        oldArtwork?.let { send(it, "release") }
        old?.let { send(it, "release") }
        return artwork
    }

    /** `-[NSImage size]`：两个 double 的结构体，在 arm64 / x86-64 上都是从两个浮点寄存器回来；取不到就给个常见的海报尺寸。 */
    private fun msgSizeWidth(img: Pointer): Pair<Double, Double> {
        val reps = msg(img, "representations")
        val rep = reps?.takeIf { msgLong(it, "count") > 0 }?.let { msg(it, "objectAtIndex:", 0L) }
        val w = rep?.let { msgLong(it, "pixelsWide") } ?: 0L
        val h = rep?.let { msgLong(it, "pixelsHigh") } ?: 0L
        return if (w > 0 && h > 0) w.toDouble() to h.toDouble() else 600.0 to 900.0
    }

    // ---- 报 / 清 ----

    private fun toObject(v: Any): Pointer = when (v) {
        is String -> nsString(v)
        is Double -> msg(cls("NSNumber"), "numberWithDouble:", v)!!
        is Long -> msg(cls("NSNumber"), "numberWithLongLong:", v)!!
        is Pointer -> v
        else -> error("unsupported now-playing value ${v::class.simpleName}")
    }

    override fun publish(state: NowPlayingState, artwork: ByteArray?) {
        onMain {
            val info = NowPlayingInfo.info(state, artworkFor(artwork))
            val dict = msg(cls("NSMutableDictionary"), "dictionary")!!
            for ((k, v) in info) send(dict, "setObject:forKey:", toObject(v), constant(k.symbol))
            val center = msg(cls("MPNowPlayingInfoCenter"), "defaultCenter")!!
            send(center, "setNowPlayingInfo:", dict)
            send(center, "setPlaybackState:", if (state.paused) PAUSED else PLAYING)
            val commands = msg(cls("MPRemoteCommandCenter"), "sharedCommandCenter")!!
            send(msg(commands, "nextTrackCommand")!!, "setEnabled:", if (state.canNext) 1 else 0)
            send(msg(commands, "previousTrackCommand")!!, "setEnabled:", if (state.canPrev) 1 else 0)
        }
    }

    override fun clear() {
        handler = null
        onMain {
            val center = msg(cls("MPNowPlayingInfoCenter"), "defaultCenter")!!
            send(center, "setNowPlayingInfo:", null as Pointer?)
            send(center, "setPlaybackState:", STOPPED)
            if (registered) {
                registered = false
                val commands = msg(cls("MPRemoteCommandCenter"), "sharedCommandCenter")!!
                val t = target()
                for ((command, _) in commandSelectors) {
                    val c = msg(commands, command)!!
                    send(c, "removeTarget:", t)
                    send(c, "setEnabled:", 0)
                }
            }
            artworkFor(null)
        }
    }

    /** 诊断 / 集成测试：系统那边现在有几个键（没报 = 0）、播放状态；在主线程上读，等结果。 */
    internal fun readBack(timeoutMs: Long = 3_000): Pair<Int, Long>? {
        val done = java.util.concurrent.CompletableFuture<Pair<Int, Long>>()
        onMain {
            val center = msg(cls("MPNowPlayingInfoCenter"), "defaultCenter")!!
            val info = msg(center, "nowPlayingInfo")
            done.complete((info?.let { msgLong(it, "count").toInt() } ?: 0) to msgLong(center, "playbackState"))
        }
        return runCatching { done.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()
    }

    /**
     * 诊断 / 验收：系统那边现在的字典，键名（`MPMediaItemPropertyTitle` …）→ 值（字符串、数字原样；封面写它的类名）；没报 = 空表。
     * 在主线程上读，等结果。
     */
    internal fun readBackInfo(timeoutMs: Long = 3_000): Map<String, String>? {
        val done = java.util.concurrent.CompletableFuture<Map<String, String>>()
        onMain {
            val center = msg(cls("MPNowPlayingInfoCenter"), "defaultCenter")!!
            val info = msg(center, "nowPlayingInfo")
            val out = LinkedHashMap<String, String>()
            fun utf8(p: Pointer?): String = p?.let { msg(it, "UTF8String") }?.getString(0, "UTF-8").orEmpty()
            if (info != null) {
                val keys = msg(info, "allKeys")!!
                for (i in 0 until msgLong(keys, "count")) {
                    val k = msg(keys, "objectAtIndex:", i) ?: continue
                    val v = msg(info, "objectForKey:", k)
                    out[utf8(k)] = when {
                        v == null -> ""
                        (msgSend.invokeLong(pack(v, "isKindOfClass:", arrayOf(cls("NSString")))) and 0xff) != 0L -> utf8(v)
                        (msgSend.invokeLong(pack(v, "isKindOfClass:", arrayOf(cls("NSNumber")))) and 0xff) != 0L -> utf8(msg(v, "stringValue"))
                        else -> objc.getFunction("object_getClassName").invokePointer(arrayOf<Any>(v))?.getString(0).orEmpty()
                    }
                }
            }
            done.complete(out)
        }
        return runCatching { done.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()
    }

    /** 诊断 / 集成测试：六条命令是不是都挂上了、各自开没开（`play=1 pause=1 …`）；没挂 = null。 */
    internal fun commandsEnabled(timeoutMs: Long = 3_000): String? {
        val done = java.util.concurrent.CompletableFuture<String>()
        onMain {
            if (!registered) { done.complete(""); return@onMain }
            val center = msg(cls("MPRemoteCommandCenter"), "sharedCommandCenter")!!
            done.complete(commandSelectors.joinToString(" ") { (command, _) -> "${command.removeSuffix("Command")}=${msgLong(msg(center, command)!!, "isEnabled") and 0xff}" })
        }
        return runCatching { done.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** 诊断 / 集成测试：像系统那样把一条命令发给我们的目标（走真的 ObjC 方法分发）；返回处理状态。 */
    internal fun deliver(selector: String, timeoutMs: Long = 3_000): Long? {
        val done = java.util.concurrent.CompletableFuture<Long>()
        onMain { done.complete(msgSend.invokeLong(arrayOf<Any?>(target(), sel(selector), null))) }
        return runCatching { done.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()
    }

    /** 诊断 / 集成测试：像系统那样向现在的封面要一张图（走手写的块）；返回图的像素宽，没有封面 = null。 */
    internal fun artworkWidth(timeoutMs: Long = 3_000): Long? {
        val done = java.util.concurrent.CompletableFuture<Long>()
        onMain {
            val a = artwork
            val img = a?.let { msg(it, "imageWithSize:", 300.0, 300.0) }
            done.complete(img?.let { msgSizeWidth(it).first.toLong() } ?: -1L)
        }
        return runCatching { done.get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrNull()?.takeIf { it >= 0 }
    }
}
