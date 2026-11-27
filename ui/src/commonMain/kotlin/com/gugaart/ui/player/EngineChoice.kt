package com.gugaart.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gugugaga.app.AppGraph
import com.gugugaga.core.EngineId
import com.gugugaga.app.player.EnginePref
import com.gugugaga.app.player.EngineSelect
import com.gugugaga.app.player.PlayRequest
import com.gugugaga.app.player.PlaybackSettings
import kotlinx.serialization.json.JsonPrimitive

/*
 * 播放器里直接选内核（用户 2026-11-16：「播放器里面最好还可以选下播放器类型 mpv exo这种缩写3个字就行」）。
 *
 * 功能词「内核」下面写正在用的那个（EXO / VLC / MPV），面板里：自动 · EXO · VLC · MPV（只列这台设备上真有的）。
 * 审计 P1-1（1.5.1）：面板里换内核**只对这一部生效**，按片记住（剧按剧记，像字幕那样，[EngineMemory]）；面板底下一行「设为默认」，
 * 点了才改全局首选（设置 › 播放 › 播放内核，键 playback.engine）。上游的控制器只在开始时读一次首选，所以这里在交给控制器的
 * 播放设置里把这一部记住的内核盖上去（[playbackFor]），换了就在当前位置、同一个版本 / 音轨 / 字幕重开一个控制器（[PlayerRestart]）。
 * 审计 P2-9：真有的内核不到两个（macOS、iOS 只有 MPV）时不显示「内核」（[coreWordVisible]）。
 */

/** 内核的三个字母。 */
fun engineShort(id: EngineId?): String = when (id) {
    EngineId.Exoplayer -> "EXO"
    EngineId.Vlc -> "VLC"
    EngineId.Mpv -> "MPV"
    EngineId.Web -> "WEB"
    null -> "—"
}

/** 面板里的一个选项：[pref] 是存进设置的值（auto / exoplayer / vlc / mpv）。 */
@Immutable
data class CoreOption(val pref: EnginePref, val label: String, val note: String)

/** 安卓（手机 / 电视）列内核的顺序。 */
val ANDROID_CORE_ORDER = listOf(EngineId.Exoplayer, EngineId.Vlc, EngineId.Mpv)

/** 电脑（Windows / macOS）列内核的顺序；iOS 只有 MPV（M5-6，`available` 里只有它）。 */
val DESKTOP_CORE_ORDER = listOf(EngineId.Mpv, EngineId.Vlc)

/** 这台设备能选的：自动 + 真有的内核（安卓 EXO · VLC · MPV，Windows MPV · VLC）。纯函数，有单测。 */
fun coreOptions(available: List<EngineId>, android: Boolean): List<CoreOption> = coreOptions(available, if (android) ANDROID_CORE_ORDER else DESKTOP_CORE_ORDER)

/**
 * M5-6：按壳报上来的内核排（[order] = 这一端列内核的顺序，只列 [available] 里有的）。macOS / iOS 只报 MPV → 「自动 · MPV」；
 * 一个都没有 → 只有「自动」。安卓 / Windows 的列表和标签和原来逐项相同。纯函数，有单测。
 */
fun coreOptions(available: List<EngineId>, order: List<EngineId>): List<CoreOption> = buildList {
    add(CoreOption(EnginePref.Auto, "自动", "放不了自动换"))
    for (e in order) if (e in available) add(
        when (e) {
            EngineId.Exoplayer -> CoreOption(EnginePref.Exoplayer, "EXO", "硬解 · 省电")
            EngineId.Vlc -> CoreOption(EnginePref.Vlc, "VLC", "网络容错")
            else -> CoreOption(EnginePref.Mpv, "MPV", "格式最全")
        },
    )
}

/** 这台设备上的内核选项（读壳报上来的可用内核）。 */
fun coreOptionsFor(play: PlaySession, graph: AppGraph): List<CoreOption> {
    val ctx = EngineSelect.selectContext(play.platform.platformFacts())
    // 手机（安卓 / iOS）和电视按安卓的顺序，电脑按电脑的；iOS / macOS 只报 MPV，所以都是「自动 · MPV」
    return coreOptions(ctx.native, if (graph.deps.uiMode == com.gugugaga.ds.theme.UiMode.Desktop) DESKTOP_CORE_ORDER else ANDROID_CORE_ORDER)
}

/** 设置里现在的首选引擎（全局的「默认」）。 */
fun currentEnginePref(graph: AppGraph): EnginePref = PlaybackSettings.of(graph.settings.value).engine

/** P2-9：能选的内核（不算「自动」）不到两个时，播放器里不显示「内核」。纯函数，有单测。 */
fun coreWordVisible(options: List<CoreOption>): Boolean = options.count { it.pref != EnginePref.Auto } >= 2

/**
 * 每部片记住的内核（键 `gugaart.engines.v1`，画报自己的）：剧按剧记（seriesId），也按集记（条目 id）——开播时只知道路由里的条目，
 * 剧的编号要从查询缓存里找，找不到时还认得这一集。没记过 = 跟全局首选。最多记 [MAX] 条。
 */
class EngineMemory(private val local: com.gugugaga.app.platform.LocalStore) {
    private val map = LinkedHashMap<String, String>()

    init {
        com.gugaart.ui.data.GugaPrefs.parseObject(local.get(KEY))?.forEach { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { map[k] = it.content } }
    }

    /** 这一部记住的内核：先按剧，再按条目；没有 = null。 */
    fun get(itemId: String, seriesId: String?): EnginePref? = (seriesId?.let { map[it] } ?: map[itemId])?.let(EnginePref::of)

    /** 记下 / 清掉（[pref] = null：这一部跟全局首选）。 */
    fun put(itemId: String, seriesId: String?, pref: EnginePref?) {
        for (k in listOfNotNull(seriesId, itemId)) {
            map.remove(k)
            if (pref != null) map[k] = pref.id
        }
        while (map.size > MAX) map.remove(map.keys.first())
        local.set(KEY, kotlinx.serialization.json.buildJsonObject { map.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }.toString())
    }

    companion object {
        const val KEY = "gugaart.engines.v1"
        const val MAX = 300
    }
}

/** 交给控制器的播放设置：全局的设置，内核换成这一部记住的那个（没记 = 原样）。 */
fun playbackFor(settings: kotlinx.serialization.json.JsonObject, film: EnginePref?): PlaybackSettings {
    if (film == null) return PlaybackSettings.of(settings)
    val p = (settings["playback"] as? kotlinx.serialization.json.JsonObject) ?: kotlinx.serialization.json.JsonObject(emptyMap())
    return PlaybackSettings(kotlinx.serialization.json.JsonObject(p + ("engine" to JsonPrimitive(film.id))))
}

/** 这一部的编号：剧 = seriesId（条目没在缓存里 = null，只按条目记）。 */
private fun seriesOf(graph: AppGraph, play: PlaySession): String? =
    if (play.remote == null) play.controller.value.item?.seriesId ?: graph.queries.cachedItem(play.homeItemId)?.seriesId
    else graph.queries.cachedItem(play.homeItemId)?.seriesId

/** 这一部条目的编号（放着别台的源时认本台那一条）。 */
private fun itemOf(play: PlaySession): String = if (play.remote == null) play.controller.currentItemId else play.homeItemId

/** 这一部现在用的首选：记住的，没有就是全局的。 */
fun filmEnginePref(play: PlaySession, graph: AppGraph): EnginePref =
    EngineMemory(graph.deps.local).get(itemOf(play), seriesOf(graph, play)) ?: currentEnginePref(graph)

/** 「设为默认」：把这一部的内核存成全局首选，这一部的记录清掉（以后跟着默认走）。 */
fun setEngineDefault(play: PlaySession, graph: AppGraph, pref: EnginePref) {
    graph.settings.patch("playback", "engine" to JsonPrimitive(pref.id))
    EngineMemory(graph.deps.local).put(itemOf(play), seriesOf(graph, play), null)
}

/** 在当前位置换内核：只记在这一部上（P1-1），重开控制器。选的和正在用的一样就只记。 */
object PlayerRestart {
    /** 每重开一次加一（[rememberPlaySession] 看到它变就换一个控制器）。 */
    var tick by mutableIntStateOf(0)
        private set

    private var pending: PlayRequest? = null

    /** 取走这一次重开要用的请求（只用一次）。 */
    internal fun take(): PlayRequest? = pending.also { pending = null }

    fun switchEngine(play: PlaySession, graph: AppGraph, pref: EnginePref) {
        val before = filmEnginePref(play, graph)
        // 和全局首选一样 = 这一部不另记（以后跟着默认走）
        EngineMemory(graph.deps.local).put(itemOf(play), seriesOf(graph, play), pref.takeIf { it != currentEnginePref(graph) })
        val c = play.controller
        val snap = c.value
        val running = snap.engineId
        val same = (pref == EnginePref.Auto && before == EnginePref.Auto) || (running != null && pref.id == running.id)
        if (same) return
        pending = PlayRequest(
            itemId = c.currentItemId,
            mediaSourceId = snap.sourceId,
            startMs = c.clock.value.positionMs.toLong().coerceAtLeast(0L),
            audioIndex = snap.audioIndex,
            // 字幕关着的（null）就保持关：-1
            subtitleIndex = snap.subtitleIndex ?: -1,
        )
        // 1.5.0 B3：放着别台的源 → 新控制器也要用那台的客户端（条目 id 是那台的）
        play.remote?.let { r -> RemotePlay.handOff(RemoteHandoff(play.homeItemId, r.serverId, r.serverName, r.itemId, snap.sourceId)) }
        tick++
    }

    /**
     * 换到另一个条目的源（1.5.0「源」：同一台上没被 Emby 合并的重复片 = 另一个条目 id）：上游 `setSource` 只换得了同一条目里的片源，
     * 所以在当前位置重开那一个条目（音轨 / 字幕交给设置重新选），路由跟着换成它（返回、「正在播放」认的是路由里的条目）。
     */
    fun switchItem(play: PlaySession, graph: AppGraph, itemId: String, mediaSourceId: String?) {
        val at = play.controller.clock.value.positionMs.toLong().coerceAtLeast(0L)
        pending = PlayRequest(itemId = itemId, mediaSourceId = mediaSourceId?.takeIf { it.isNotEmpty() }, startMs = at)
        tick++
        graph.nav.replace(com.gugugaga.app.nav.Route.Play(itemId, mediaSourceId = mediaSourceId?.takeIf { it.isNotEmpty() }, startMs = at))
    }
}
