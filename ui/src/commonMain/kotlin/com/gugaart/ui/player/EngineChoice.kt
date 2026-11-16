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
 * 上游的控制器只在开始时读一次「首选引擎」（设置 › 播放 › 首选引擎，键 playback.engine），所以选了一个 = 把它存成首选引擎
 * （下次播放也用它，设置里那一行同步变）+ 在当前位置、同一个版本 / 音轨 / 字幕重开一个控制器（[PlayerRestart]）。
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

/** 这台设备能选的：自动 + 真有的内核（安卓 EXO · VLC · MPV，Windows MPV · VLC）。纯函数，有单测。 */
fun coreOptions(available: List<EngineId>, android: Boolean): List<CoreOption> = buildList {
    add(CoreOption(EnginePref.Auto, "自动", "放不了自动换"))
    val order = if (android) listOf(EngineId.Exoplayer, EngineId.Vlc, EngineId.Mpv) else listOf(EngineId.Mpv, EngineId.Vlc)
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
    return coreOptions(ctx.native, android = graph.deps.uiMode != com.gugugaga.ds.theme.UiMode.Desktop)
}

/** 设置里现在的首选引擎。 */
fun currentEnginePref(graph: AppGraph): EnginePref = PlaybackSettings.of(graph.settings.value).engine

/** 在当前位置换内核：存成首选引擎，重开控制器。选的和正在用的一样就只存设置。 */
object PlayerRestart {
    /** 每重开一次加一（[rememberPlaySession] 看到它变就换一个控制器）。 */
    var tick by mutableIntStateOf(0)
        private set

    private var pending: PlayRequest? = null

    /** 取走这一次重开要用的请求（只用一次）。 */
    internal fun take(): PlayRequest? = pending.also { pending = null }

    fun switchEngine(play: PlaySession, graph: AppGraph, pref: EnginePref) {
        val before = currentEnginePref(graph)
        graph.settings.patch("playback", "engine" to JsonPrimitive(pref.id))
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
        tick++
    }
}
