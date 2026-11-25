package com.gugaart.ui.data

import androidx.compose.runtime.Immutable
import com.gugaart.ui.player.AvCaps
import com.gugaart.ui.player.AvKind
import com.gugaart.ui.player.DeviceAv
import com.gugaart.ui.player.formatBadges
import com.gugaart.ui.screens.detail.specLine
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.MediaSourceInfo
import com.gugugaga.core.MediaStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/*
 * 统一的「源」模型（1.5.0 C1，docs/plan-multi-server.md §2.3 / §2.4）：
 * 源 = （服务器, 条目 id, 片源, 规格）。详情页三端、播放器「版本」面板、默认版本规则都只认 [PlaySource]。
 * 这一版只有同一台服务器的源（[SourceKind.SameItem] / [SourceKind.Duplicate]）；阶段 B 往里加 [SourceKind.OtherServer]。
 * 界面先对 [FakeSourceRepository] 做（C2-UI），后端好了接 [EmbySourceRepository]。
 */

/** 这个源是怎么来的：同一个条目自带的版本 / 同一台服务器上没被 Emby 合并的重复片 / 别的服务器上的同一部片。 */
enum class SourceKind { SameItem, Duplicate, OtherServer }

/** 按当前设备的播放判定预估：能直连（含 remux）/ 要转码 / 判不了。 */
enum class Playable { Direct, Transcode, Unknown }

/**
 * 一个可播的源。[itemId] 是条目 id（重复片 / 别的服务器上和当前条目不同）；[mediaSource] 是上游接口模型，
 * 播放、格式徽章、规格都从它走。[latencyMs] = 这台服务器最近一次探测的延迟（不知道 = null）。
 */
@Immutable
data class PlaySource(
    val serverId: String,
    val serverName: String,
    val itemId: String,
    val mediaSource: MediaSourceInfo,
    val kind: SourceKind,
    val latencyMs: Int? = null,
    val playable: Playable = Playable.Unknown,
)

private val HDR_KINDS = setOf(AvKind.DolbyVision, AvKind.Hdr10Plus, AvKind.Hdr10, AvKind.Hlg, AvKind.Hdr)
private val AUDIO_KINDS = setOf(AvKind.Atmos, AvKind.DtsX, AvKind.DtsHdMa, AvKind.TrueHd)

/**
 * 把源的规格写成一行字：「4K · HDR10 · DTS-HD MA · 直连」。纯函数。
 * 分辨率复用详情页的 [specLine]，HDR / 声音复用 1.2.6 的 [formatBadges]（不另写一套识别）；
 * [caps] 只影响 badges 的备注，不影响这一行写什么（徽章哪里都照常亮，`FormatBadges` 的规矩）。
 * 没有媒体流信息 → 退到片源名；连名字也没有 → 只写直连 / 转码（都没有 = ""）。
 */
fun playSourceSpecLine(source: PlaySource, caps: AvCaps? = DeviceAv.caps): String {
    val badges = formatBadges(source.mediaSource, audioIndex = null, caps = caps)
    val res = specLine(source.mediaSource)?.substringBefore(" · ")
    val hdr = badges.firstOrNull { it.kind in HDR_KINDS }?.zh ?: "SDR".takeIf { res != null }
    val audio = badges.firstOrNull { it.kind in AUDIO_KINDS }?.zh
    val play = when (source.playable) {
        Playable.Direct -> "直连"
        Playable.Transcode -> "转码"
        Playable.Unknown -> null
    }
    val head = res ?: source.mediaSource.name?.trim()?.takeIf { it.isNotEmpty() }
    return listOfNotNull(head, hdr, audio, play).joinToString(" · ")
}

/** 给一个条目返回它全部的源（本台的版本 + 本台的重复片；阶段 B 再加别台的）。只读。 */
interface SourceRepository {
    suspend fun sourcesFor(item: BaseItemDto): List<PlaySource>

    /**
     * 流式（1.5.0 B2）：先给本台的，别台的到一台补一台；每次发出的都是累计结果（[SourceSearch]）。
     * 默认 = 只有本台的，一次发完（没有别的服务器参与时就是它）。
     */
    fun search(item: BaseItemDto): Flow<SourceSearch> = flow { emit(SourceSearch(sourcesFor(item))) }
}

/** 只要源列表的流（[SourceRepository.search] 的 `sources`，相同的不重复发）。 */
fun SourceRepository.sourcesFlow(item: BaseItemDto): Flow<List<PlaySource>> = search(item).map { it.sources }.distinctUntilChanged()

/* ---- 假实现（C2-UI 先对着它做界面） ---- */

/** 造一条测试用的媒体流 / 片源（假实现和单测共用；字段只填规格识别用得上的）。 */
object FakeMedia {
    fun video(width: Int, height: Int, hdr: String? = null, codec: String = "hevc", profile7: Boolean = false): MediaStream =
        MediaStream(
            index = 0, type = "Video", codec = codec, width = width, height = height,
            videoRange = if (hdr != null) "HDR" else "SDR",
            extendedVideoType = when (hdr) {
                "Dolby Vision" -> "DolbyVision"
                "HDR10+" -> "Hdr10Plus"
                "HDR10" -> "Hdr10"
                "HLG" -> "HyperLogGamma"
                else -> null
            },
            extendedVideoSubType = if (profile7) "dovi profile 7" else null,
            isDefault = true,
        )

    fun audio(codec: String, title: String? = null, channels: Int = 6, profile: String? = null): MediaStream =
        MediaStream(index = 1, type = "Audio", codec = codec, title = title, profile = profile, channels = channels, isDefault = true)

    fun source(
        id: String,
        name: String,
        width: Int,
        height: Int,
        hdr: String? = null,
        audioCodec: String = "aac",
        audioTitle: String? = null,
        bitrate: Long? = null,
        audioProfile: String? = null,
        profile7: Boolean = false,
    ): MediaSourceInfo = MediaSourceInfo(
        id = id, name = name, container = "mkv", bitrate = bitrate,
        supportsDirectPlay = true, supportsDirectStream = true, supportsTranscoding = true,
        mediaStreams = listOf(video(width, height, hdr, profile7 = profile7), audio(audioCodec, audioTitle, profile = audioProfile)),
    )
}

/**
 * 假数据（C2-UI 用）：四种情况各一个条目，[items] 里的 `BaseItemDto` 直接喂 [FakeSourceRepository.sourcesFor]。
 * - [ITEM_SINGLE]：1 个源（1080p SDR，直连）。
 * - [ITEM_MERGED]：Emby 合并好的 3 个版本（4K 杜比视界 / 4K HDR10 / 1080p SDR），全标 SameItem。
 * - [ITEM_DUPLICATES]：同一台服务器上 2 份文件（条目不同、TMDB 相同）：本条目 4K HDR10 + 另一份 1080p SDR（标 Duplicate）。
 * - [ITEM_TRANSCODE]：1 个源，按当前设备要转码。
 */
object FakeSources {
    const val SERVER_ID = "fake-server-home"
    const val SERVER_NAME = "家里 NAS"
    const val ITEM_SINGLE = "fake-single"
    const val ITEM_MERGED = "fake-merged"
    const val ITEM_DUPLICATES = "fake-duplicates"
    const val ITEM_TRANSCODE = "fake-transcode"

    /** 重复片那部电影的另一个条目 id（provider id 相同）。 */
    const val DUP_OTHER_ITEM = "fake-duplicates-other"

    val single: List<PlaySource> = listOf(
        PlaySource(SERVER_ID, SERVER_NAME, ITEM_SINGLE, FakeMedia.source("fs-1", "1080p", 1920, 1080, audioCodec = "ac3", bitrate = 8_000_000), SourceKind.SameItem, latencyMs = 12, playable = Playable.Direct),
    )

    val merged: List<PlaySource> = listOf(
        PlaySource(SERVER_ID, SERVER_NAME, ITEM_MERGED, FakeMedia.source("fm-dv", "4K 杜比视界", 3840, 2160, hdr = "Dolby Vision", audioCodec = "truehd", audioTitle = "TrueHD 7.1 Atmos", bitrate = 45_000_000), SourceKind.SameItem, 12, Playable.Direct),
        PlaySource(SERVER_ID, SERVER_NAME, ITEM_MERGED, FakeMedia.source("fm-hdr10", "4K HDR10", 3840, 2160, hdr = "HDR10", audioCodec = "dts", audioTitle = "DTS-HD MA 5.1", audioProfile = "DTS-HD MA", bitrate = 30_000_000), SourceKind.SameItem, 12, Playable.Direct),
        PlaySource(SERVER_ID, SERVER_NAME, ITEM_MERGED, FakeMedia.source("fm-1080", "1080p", 1920, 1080, audioCodec = "aac", bitrate = 8_000_000), SourceKind.SameItem, 12, Playable.Direct),
    )

    val duplicates: List<PlaySource> = listOf(
        PlaySource(SERVER_ID, SERVER_NAME, ITEM_DUPLICATES, FakeMedia.source("fd-4k", "4K HDR10", 3840, 2160, hdr = "HDR10", audioCodec = "dts", audioTitle = "DTS-HD MA 5.1", audioProfile = "DTS-HD MA", bitrate = 30_000_000), SourceKind.SameItem, 12, Playable.Direct),
        PlaySource(SERVER_ID, SERVER_NAME, DUP_OTHER_ITEM, FakeMedia.source("fd-1080", "1080p", 1920, 1080, audioCodec = "aac", bitrate = 8_000_000), SourceKind.Duplicate, 12, Playable.Direct),
    )

    val transcode: List<PlaySource> = listOf(
        PlaySource(SERVER_ID, SERVER_NAME, ITEM_TRANSCODE, FakeMedia.source("ft-1", "4K HDR10", 3840, 2160, hdr = "HDR10", audioCodec = "truehd", bitrate = 40_000_000), SourceKind.SameItem, 12, Playable.Transcode),
    )

    val table: Map<String, List<PlaySource>> = mapOf(
        ITEM_SINGLE to single,
        ITEM_MERGED to merged,
        ITEM_DUPLICATES to duplicates,
        ITEM_TRANSCODE to transcode,
    )

    private fun item(id: String, name: String, sources: List<PlaySource>, tmdb: String? = null): BaseItemDto = BaseItemDto(
        id = id, name = name, type = "Movie",
        mediaSources = sources.filter { it.itemId == id }.map { it.mediaSource },
        providerIds = tmdb?.let { mapOf("Tmdb" to it) } ?: emptyMap(),
    )

    /** 四个夹具条目（界面拿它当「详情页取到的条目」用）。 */
    val items: List<BaseItemDto> = listOf(
        item(ITEM_SINGLE, "单源电影", single),
        item(ITEM_MERGED, "合并多版本电影", merged, tmdb = "100001"),
        item(ITEM_DUPLICATES, "两份文件的电影", duplicates, tmdb = "100002"),
        item(ITEM_TRANSCODE, "要转码的电影", transcode),
    )
}

/** 假仓库：按条目 id 查表（默认 [FakeSources.table]，可换自己的表）。不发任何请求。 */
class FakeSourceRepository(private val table: Map<String, List<PlaySource>> = FakeSources.table) : SourceRepository {
    override suspend fun sourcesFor(item: BaseItemDto): List<PlaySource> = table[item.id].orEmpty()
}