package com.gugaart.ui.subtitle

import com.gugaart.ui.danmaku.RelayHost
import com.gugaart.ui.danmaku.RelayResponse
import com.gugugaga.app.player.PlayerClient
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.ExternalSubtitleTrack
import com.gugugaga.core.MediaStream
import com.gugugaga.core.PlaybackCapsPatch
import com.gugugaga.core.PlaybackPrefs
import com.gugugaga.core.ResolvedPlayback
import com.gugugaga.core.SubtitleDelivery

/*
 * 「仅本次播放」的字幕（docs/subtitle-search.md §4.3）：迅雷 / 射手搜到的字幕不能存进这台 Emby（没有上传接口），
 * 下载到本机，作为一条外挂字幕加进正在放的这一集。
 *
 *  - 存在进程里（[LocalSubtitles]），按条目记，最多 [LocalSubtitles.MAX] 条，不落盘。
 *  - 交给内核的是本机回环上的 http 地址（[subtitleLoopbackHost]，和弹幕中转同一种小服务）：Windows 的 mpv 只 sub-add http(s) 地址，
 *    回环地址 mpv / ExoPlayer / VLC 都认。
 *  - 播放器的 Emby 层包一层（[SubtitleAwareClient]，包住上游的 `PlayerClient`，只用公开接口）：协商结果里加一条外挂字幕
 *    （`Index` = 9000 + n）；选它时发给 Emby 的 `SubtitleStreamIndex` 换成 -1（服务器不认识 9000+），回来再把选中项改成它。
 */

/** 本机的一条字幕。[index] = 播放器里的字幕编号（≥ [LocalSubtitles.BASE_INDEX]）。 */
data class LocalSubtitle(val itemId: String, val index: Int, val text: String, val format: SubFormat, val label: String, val lang: String?) {
    override fun toString(): String = "LocalSubtitle(item=$itemId, index=$index, format=$format, ${text.length} chars)"
}

/** 回环服务的平台实现（桌面 / 安卓：`LoopbackRelayHost`；iOS：`IosLoopbackRelayHost`）。起不来 = null。 */
expect fun subtitleLoopbackHost(): RelayHost?

object LocalSubtitles {
    const val BASE_INDEX = 9000
    const val MAX = 20

    @kotlin.concurrent.Volatile
    private var all: List<LocalSubtitle> = emptyList()

    @kotlin.concurrent.Volatile
    private var base: String? = null

    /** 测试缝：换掉回环服务（离屏测试里给一个不开端口的假的）。 */
    var hostFactory: () -> RelayHost? = { subtitleLoopbackHost() }

    fun of(itemId: String): List<LocalSubtitle> = all.filter { it.itemId == itemId }

    fun find(index: Int): LocalSubtitle? = all.firstOrNull { it.index == index }

    /** 加一条，返回它（编号接着上一条往上数；超过 [MAX] 条先丢最早的）。 */
    fun add(itemId: String, text: String, format: SubFormat, label: String, lang: String?): LocalSubtitle {
        val next = (all.maxOfOrNull { it.index } ?: (BASE_INDEX - 1)) + 1
        val sub = LocalSubtitle(itemId, next, text, format, label, lang)
        all = (all + sub).takeLast(MAX)
        return sub
    }

    fun clear() {
        all = emptyList()
    }

    /** 这一条给内核的地址；回环服务起不来 = null（这一条就加不进去）。 */
    fun urlOf(sub: LocalSubtitle): String? {
        val b = base ?: startHost() ?: return null
        return "$b/sub/${sub.index}.${sub.format.ext}"
    }

    private fun startHost(): String? {
        val host = hostFactory() ?: return null
        return try {
            host.start { req ->
                val n = Regex("""^/sub/(\d+)\.\w+$""").find(req.target.substringBefore('?'))?.groupValues?.get(1)?.toIntOrNull()
                val sub = n?.let(::find)
                if (sub == null) RelayResponse(404, "not found", "text/plain; charset=utf-8")
                else RelayResponse(200, sub.text, if (sub.format == SubFormat.Srt) "application/x-subrip; charset=utf-8" else "text/x-ssa; charset=utf-8")
            }.also { base = it }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 协商结果 [r] 加上本机字幕 [locals]（[urlOf] 给不出地址的跳过）；[wanted] = 要选中的那一条的编号（null = 不改选中项）。
 * 纯函数，有单测。
 */
fun withLocalSubtitles(r: ResolvedPlayback, locals: List<LocalSubtitle>, urlOf: (LocalSubtitle) -> String?, wanted: Int?): ResolvedPlayback {
    val usable = locals.mapNotNull { s -> urlOf(s)?.let { s to it } }.filter { (s, _) -> r.mediaSource.mediaStreams.none { it.index == s.index } }
    if (usable.isEmpty()) return r
    val streams = usable.map { (s, url) ->
        MediaStream(
            index = s.index,
            type = "Subtitle",
            codec = if (s.format == SubFormat.Srt) "subrip" else s.format.ext,
            language = s.lang,
            title = s.label,
            displayTitle = s.label,
            isExternal = true,
            isTextSubtitleStream = true,
            supportsExternalStream = true,
            deliveryMethod = "External",
            deliveryUrl = url,
            deliveryFormat = s.format.ext,
        )
    }
    val tracks = usable.map { (s, url) -> ExternalSubtitleTrack(s.index, url, s.format.ext, s.lang, s.label, isForced = false, isDefault = false) }
    val source = r.mediaSource.copy(mediaStreams = r.mediaSource.mediaStreams + streams)
    val pick = wanted?.let { w -> streams.firstOrNull { it.index == w } }
    val track = pick?.let { p -> tracks.first { it.index == p.index } }
    return r.copy(
        mediaSource = source,
        externalSubtitles = r.externalSubtitles + tracks,
        subtitleStreamIndex = if (pick != null) pick.index else r.subtitleStreamIndex,
        subtitleDelivery = if (pick != null && track != null) SubtitleDelivery(index = pick.index, method = "External", stream = pick, url = track.url, format = track.format) else r.subtitleDelivery,
    )
}

/** 播放器的 Emby 层：这一集有本机字幕时把它们加进协商结果（见文件头）；没有就原样转发。 */
class SubtitleAwareClient(private val inner: PlayerClient) : PlayerClient by inner {
    private fun wantedOf(prefs: PlaybackPrefs): Int? = prefs.subtitleStreamIndex?.takeIf { it >= LocalSubtitles.BASE_INDEX }

    override suspend fun preparePlayback(prefs: PlaybackPrefs, caps: PlaybackCapsPatch?): ResolvedPlayback {
        val locals = LocalSubtitles.of(prefs.itemId)
        if (locals.isEmpty()) return inner.preparePlayback(prefs, caps)
        val wanted = wantedOf(prefs)
        val r = inner.preparePlayback(if (wanted != null) prefs.copy(subtitleStreamIndex = -1) else prefs, caps)
        return withLocalSubtitles(r, locals, LocalSubtitles::urlOf, wanted)
    }

    override fun resolveUnprobedStrm(item: BaseItemDto, prefs: PlaybackPrefs, caps: PlaybackCapsPatch?): ResolvedPlayback? {
        val locals = LocalSubtitles.of(prefs.itemId)
        if (locals.isEmpty()) return inner.resolveUnprobedStrm(item, prefs, caps)
        val wanted = wantedOf(prefs)
        val r = inner.resolveUnprobedStrm(item, if (wanted != null) prefs.copy(subtitleStreamIndex = -1) else prefs, caps) ?: return null
        return withLocalSubtitles(r, locals, LocalSubtitles::urlOf, wanted)
    }
}
