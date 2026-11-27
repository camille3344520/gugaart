package com.gugaart.ui.player

import com.gugaart.ui.data.TrackName
import com.gugaart.ui.data.trackName
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.player.TrackOption

/*
 * 播放器里的音轨 / 字幕名字（审计 P1-2）：和详情页同一个 [trackName]。选项上只有上游的显示名，语言 / 编码 / 声道从这条片源的流里查；
 * 流表里查不到（流表空、内核自己报的轨）就用上游的名字。上游的小签（「烧录」）接在后面。
 */

/** 一个播放器选项的名字 + 小签。 */
fun trackLabel(snap: PlayerSnapshot, option: TrackOption, type: String): TrackName {
    val stream = option.index?.let { i -> snap.resolved?.mediaSource?.mediaStreams?.firstOrNull { it.type == type && it.index == i } }
    val base = stream?.let(::trackName) ?: TrackName(option.label, emptyList())
    return option.badge?.let { b -> base.copy(tags = base.tags + b) } ?: base
}

/** 功能词下面那一小段：正在用的字幕（关着 = 「关」）。 */
fun subtitleWordValue(snap: PlayerSnapshot): String =
    snap.subtitles.firstOrNull { it.index != null && (it.selected || it.index == snap.subtitleIndex) }?.takeIf { snap.subtitleIndex != null }
        ?.let { trackLabel(snap, it, "Subtitle").text } ?: "关"

/** 功能词下面那一小段：正在用的音轨。 */
fun audioWordValue(snap: PlayerSnapshot): String? = snap.audio.firstOrNull { it.selected }?.let { trackLabel(snap, it, "Audio").text }
