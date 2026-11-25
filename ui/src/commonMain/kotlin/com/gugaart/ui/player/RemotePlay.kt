package com.gugaart.ui.player

import com.gugaart.ui.data.OtherServers
import com.gugaart.ui.data.PlaySource
import com.gugaart.ui.data.SourceKind
import com.gugaart.ui.subtitle.SubtitleAwareClient
import com.gugugaga.app.AppGraph
import com.gugugaga.app.player.EmbyPlayerClient
import com.gugugaga.app.player.Notice
import com.gugugaga.app.player.NoticeTone
import com.gugugaga.app.player.PlayRequest
import com.gugugaga.app.player.PlayerClient
import com.gugugaga.core.EmbyClient

/*
 * 播放器换到别台服务器的源（1.5.0 B3，docs/plan-multi-server.md §2.2）。
 *
 * - 路由始终是**本台**的条目（返回、详情页、「正在播放」都认它）；正在放别台的哪一个，记在 [PlaySession] 里（[RemoteSource]）。
 * - 详情页选了别台的源再点播放：[SourcesState.playRoute] 交一张 [RemoteHandoff] 给这里，播放器开控制器时取走，
 *   一开始就用那台的客户端（[OtherServers] 池里找片源时建好的那个）。
 * - 播放中在「版本」面板里换：上游 `PlayController.switchServer`（B3 开的缝）——同一个内核、从当前位置接着放，
 *   进度（PlaybackStart / Progress / Stopped）只报给正在播的那台；对方连不上 → 上游把一切放回原样，原来的继续放。
 */

/** 正在放的是别台的源：哪台、那台上的条目、本台对应的条目（路由里那一个）、那台的客户端。 */
data class RemoteSource(
    val serverId: String,
    val serverName: String,
    val itemId: String,
    val homeItemId: String,
    val client: EmbyClient,
)

/** 详情页交给播放器的「用别台的源开播」。[startMs] null = 那台自己的续看位置。 */
data class RemoteHandoff(
    val homeItemId: String,
    val serverId: String,
    val serverName: String,
    val itemId: String,
    val mediaSourceId: String?,
    val startMs: Long? = null,
)

object RemotePlay {
    private var pending: RemoteHandoff? = null

    /** 最近一次要换过去的别台（换台失败时把它在池里标成连不上，「源」单子里那台的小圆点变灰）。 */
    internal var lastTried: String? = null

    /** 详情页：下一次为 [h.homeItemId] 开的播放器用别台的源。 */
    fun handOff(h: RemoteHandoff) {
        pending = h
    }

    /** 播放器开控制器时取走（只认这一条路由的；取一次就没了）。 */
    internal fun take(homeItemId: String): RemoteHandoff? = pending?.takeIf { it.homeItemId == homeItemId }.also { if (it != null) pending = null }

    /** 播放器那一层给这台的客户端（和本台的一样包一层本机字幕）。 */
    internal fun playerClientOf(c: EmbyClient): PlayerClient = SubtitleAwareClient(EmbyPlayerClient(c))

    /**
     * 面板里点了一行源：别台的 → 换到那台；本台的而现在正放着别台的 → 切回本台；其余交给原来的做法（同条目换版本 / 重复片重开）。
     * 返回 true = 这里处理了。
     */
    fun choose(play: PlaySession, graph: AppGraph, s: PlaySource): Boolean {
        val at = play.controller.clock.value.positionMs.toLong().coerceAtLeast(0L)
        if (s.kind == SourceKind.OtherServer) {
            lastTried = s.serverId
            val c = OtherServers.poolFor(graph).clientNow(s.serverId)
            if (c == null) {
                play.post(Notice(NoticeTone.Warning, "换到「${s.serverName}」失败", "这台服务器要重新登录，或者现在连不上"))
                return true
            }
            val pc = playerClientOf(c)
            play.register(pc, RemoteSource(s.serverId, s.serverName, s.itemId, play.homeItemId, c))
            play.controller.switchServer(pc, PlayRequest(s.itemId, s.mediaSource.id.takeIf { it.isNotEmpty() }, startMs = at), why = "换到「${s.serverName}」")
            return true
        }
        if (play.remote != null) {
            play.controller.switchServer(play.homeClient, PlayRequest(s.itemId, s.mediaSource.id.takeIf { it.isNotEmpty() }, startMs = at), why = "切回本台")
            return true
        }
        return false
    }
}
