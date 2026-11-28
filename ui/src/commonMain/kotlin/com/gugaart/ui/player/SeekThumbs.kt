package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.gugugaga.app.data.decodeImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.appendQuery
import com.gugugaga.core.joinUrl
import com.gugugaga.core.seg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * 进度条缩略图（1.6 C3）。数据 = Emby 的「缩略图」（服务器的「缩略图图像」计划任务生成的 Roku BIF：`/Videos/{id}/index.bif`）。
 * 服务器没生成 = 404 → 照旧只显示时间，不加说明。一部片只问一次（拖 / 悬停第一次时问，不是每次开播都下），结果按片记着。
 * 手机、电脑拖进度条时在上方出图；电视长按快进的预览框里有图就用它，没有照旧是剧照。
 */

/** 一份 BIF 的目录：第 i 张图在 [offsets]`[i]`..[offsets]`[i+1]`，时间 = i × [intervalMs]（Emby 的时间戳都是整格）。 */
class BifIndex(private val bytes: ByteArray, val intervalMs: Long, private val stamps: LongArray, private val offsets: IntArray) {
    val count: Int get() = stamps.size

    /** 拖到 [ms] 时该显示第几张：时间戳不大于 [ms] 的最后一张。 */
    fun frameAt(ms: Double): Int {
        if (count == 0) return -1
        val t = (ms / intervalMs).toLong()
        var lo = 0
        var hi = count - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (stamps[mid] <= t) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** 第 [i] 张图的字节（JPEG；Mock 给 PNG）。 */
    fun frame(i: Int): ByteArray? = if (i !in 0 until count) null else bytes.copyOfRange(offsets[i], offsets[i + 1])
}

private val BIF_MAGIC = byteArrayOf(0x89.toByte(), 0x42, 0x49, 0x46, 0x0D, 0x0A, 0x1A, 0x0A)

private fun ByteArray.u32(at: Int): Long =
    (this[at].toLong() and 0xFF) or ((this[at + 1].toLong() and 0xFF) shl 8) or ((this[at + 2].toLong() and 0xFF) shl 16) or ((this[at + 3].toLong() and 0xFF) shl 24)

/**
 * 读 BIF（Roku 格式，小端）：8 字节魔数、版本、张数、时间单位（毫秒，0 = 1000）、到 64 字节的保留；然后每张 (时间戳, 偏移)，
 * 最后一项时间戳 0xFFFFFFFF、偏移 = 文件末尾。不是 BIF、目录越界 = null。纯函数，有单测。
 */
fun parseBif(bytes: ByteArray): BifIndex? {
    if (bytes.size < 64 || (0 until 8).any { bytes[it] != BIF_MAGIC[it] }) return null
    val declared = bytes.u32(12)
    val unit = bytes.u32(16).let { if (it == 0L) 1000L else it }
    val stamps = ArrayList<Long>()
    val offsets = ArrayList<Int>()
    var p = 64
    while (p + 8 <= bytes.size) {
        val ts = bytes.u32(p)
        val off = bytes.u32(p + 4)
        if (off > bytes.size) return null
        if (ts == 0xFFFFFFFFL) {
            offsets += off.toInt()
            break
        }
        if (offsets.isNotEmpty() && off < offsets.last()) return null
        stamps += ts
        offsets += off.toInt()
        p += 8
        if (stamps.size > 100_000) return null
    }
    if (offsets.size != stamps.size + 1 || stamps.isEmpty()) return null
    if (declared != 0L && declared != stamps.size.toLong()) return null
    return BifIndex(bytes, unit, stamps.toLongArray(), offsets.toIntArray())
}

/** 这台 Emby 上这一部片源的 BIF 地址（宽 [width] px；带令牌：和播放地址一样，图片加载器发不了请求头）。 */
fun bifUrl(apiBase: String, token: String?, itemId: String, mediaSourceId: String?, width: Int = BIF_WIDTH): String =
    appendQuery(joinUrl(apiBase, "/Videos/${seg(itemId)}/index.bif"), linkedMapOf("Width" to width, "MediaSourceId" to mediaSourceId, "api_key" to token))

const val BIF_WIDTH = 320

/**
 * 一次播放的缩略图：[request] 在第一次拖 / 悬停时叫，按「服务器 + 条目 + 片源」问一次服务器；[image] 给拖到的位置的图
 * （没有 BIF / 还没下完 = null）。解出来的图只留最近几张。
 */
@Stable
class SeekThumbs internal constructor(private val scope: CoroutineScope, private val fetch: suspend (String) -> ByteArray?, private val decode: (ByteArray) -> ImageBitmap?) {
    private var key: String? = null
    private var index by mutableStateOf<BifIndex?>(null)
    private val decoded = LinkedHashMap<Int, ImageBitmap?>()

    /** 有缩略图（BIF 已经下好、读得懂）。 */
    val available: Boolean get() = index != null

    /** 准备 [itemId] / [mediaSourceId] 的缩略图（[url] 是它的 BIF 地址）；同一份不会再问。 */
    fun request(itemId: String, mediaSourceId: String?, url: String) {
        val k = "$itemId|$mediaSourceId|${url.substringBefore('?')}"
        if (k == key) return
        key = k
        index = null
        decoded.clear()
        val known = cache[k]
        if (known != null) {
            index = known.index
            return
        }
        scope.launch {
            val bif = withContext(Dispatchers.Default) { runCatching { fetch(url) }.getOrNull()?.let(::parseBif) }
            cache[k] = Cached(bif)
            while (cache.size > CACHE_MAX) cache.remove(cache.keys.first())
            if (key == k) index = bif
        }
    }

    /** 拖 / 悬停开始：准备正在放的这一份（放着别台的源时问那一台）。 */
    fun prepare(play: PlaySession, g: com.gugugaga.app.AppGraph) {
        val snap = play.controller.value
        val client = play.remote?.client ?: g.session.session.value?.client ?: return
        val itemId = snap.itemId.takeIf { it.isNotEmpty() } ?: return
        request(itemId, snap.sourceId, bifUrl(client.apiBase, client.accessToken, itemId, snap.sourceId))
    }

    /** 拖到 [ms] 时的图；没有 = null（照旧只显示时间）。 */
    fun image(ms: Double): ImageBitmap? {
        val bif = index ?: return null
        val i = bif.frameAt(ms)
        if (i < 0) return null
        if (decoded.containsKey(i)) return decoded[i]
        val img = bif.frame(i)?.let { runCatching { decode(it) }.getOrNull() }
        decoded[i] = img
        while (decoded.size > DECODED_MAX) decoded.remove(decoded.keys.first())
        return img
    }

    private class Cached(val index: BifIndex?)

    companion object {
        /** 按片记住的结果（含「这部没有」）：进程里最多几部，BIF 一部几 MB。 */
        private val cache = LinkedHashMap<String, Cached>()
        private const val CACHE_MAX = 3
        private const val DECODED_MAX = 12

        /** 测试缝：换掉下载（离屏测试里直接给 BIF 字节）。应用里是 null = 用上游的图片字节源。 */
        var fetchOverride: (suspend (String) -> ByteArray?)? = null

        internal fun clearCache() = cache.clear()
    }
}

/** 这一页的缩略图（接好上游的字节源和解码）。 */
@Composable
fun rememberSeekThumbs(): SeekThumbs {
    val g = LocalGraph.current
    val scope = rememberCoroutineScope()
    return remember(g) {
        val fetcher = SeekThumbs.fetchOverride ?: g.deps.fetchImageBytes ?: com.gugugaga.app.data.HttpFetcher()::fetch
        SeekThumbs(scope, fetcher) { bytes -> decodeImage(bytes) }
    }
}
