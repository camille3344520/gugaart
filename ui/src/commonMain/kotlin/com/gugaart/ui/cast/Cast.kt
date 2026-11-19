package com.gugaart.ui.cast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugaart.ui.player.PlaySession
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.buildStaticStreamUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 * 投屏的状态（三端共用一份；电视自己是被投的一方，不用）：
 *  - 投出去：本机暂停，把这一版片源的原始地址（Emby 的 Static 流，带登录令牌）交给电视，电视从本机的位置接着放；
 *  - 每秒问一次电视放到哪了（状态 + 进度）；
 *  - 结束投屏：电视停下，本机跳到电视放到的位置——本机的进度上报（上游 PlayController）照常走，Emby 的续看位置就对了；
 *  - 离开播放器 = 结束投屏（电视也停）。弹幕不投（电视自己放片子，画面上没有我们的图层）。
 */
object Cast {
    /** 壳装上的投屏层（手机、电脑：jvmShared 的 DlnaCastHost）；null = 这一端不能投。 */
    var host: CastHost? = null

    var devices by mutableStateOf<List<CastDevice>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var active by mutableStateOf<CastDevice?>(null)
        private set
    var status by mutableStateOf<CastStatus?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var poller: Job? = null
    private var lastPosSec = 0L
    private val inst = listOf("InstanceID" to "0")

    /** 找同一个网络里能投的电视。 */
    suspend fun search() {
        val h = host ?: return
        if (searching) return
        searching = true
        message = null
        devices = try {
            h.discover(3_500)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        } finally {
            searching = false
        }
        if (devices.isEmpty()) message = "没找到能投的电视：电视和这台设备要连同一个网络，电视上要打开「投屏 / DLNA」" + (runCatching { h.emptyHint() }.getOrNull() ?: "")
    }

    /** 投到 [device]：本机暂停，电视从本机现在的位置接着放。 */
    fun start(scope: CoroutineScope, play: PlaySession, client: EmbyClient, device: CastDevice) {
        val h = host ?: return
        val c = play.controller
        val snap = c.value
        val source = snap.resolved?.mediaSource ?: run {
            message = "片源还没准备好，稍等一下再投"
            return
        }
        val url = buildStaticStreamUrl(client.urlContext, c.currentItemId, source, useExtension = true)
        val title = snap.item?.name ?: "咕咕嘎嘎"
        val startSec = (c.clock.value.positionMs / 1000).toLong().coerceAtLeast(0)
        c.pause()
        poller?.cancel()
        active = device
        status = null
        lastPosSec = startSec
        message = "正在投到「${device.name}」…"
        poller = scope.launch {
            val loaded = h.action(device, "SetAVTransportURI", inst + listOf("CurrentURI" to url, "CurrentURIMetaData" to didl(title, url, mimeOf(source.container)))) != null
            val playing = loaded && h.action(device, "Play", inst + ("Speed" to "1")) != null
            if (!playing) {
                message = "「${device.name}」没接受这个片子（可能放不了这种格式）"
                active = null
                return@launch
            }
            // 电视真的开始放了再跳到原来的位置（很多电视在准备时不理 Seek）
            var sought = startSec < 5
            var sawPlaying = false
            var polls = 0
            while (isActive) {
                polls++
                // 实测（海信）：第一次投到一台电视，电视上会弹「是否允许投屏」，没人点就一直停着——几秒还没放起来就提醒一句
                if (!sawPlaying && polls == 5) message = "正在投到「${device.name}」…电视上要是弹出确认，点一下「允许」"
                val st = statusOf(h, device)
                if (st != null) {
                    status = st
                    st.positionSec?.let { if (sought) lastPosSec = it }
                    if (st.playing) sawPlaying = true
                    if (!sought && st.playing) {
                        h.action(device, "Seek", inst + listOf("Unit" to "REL_TIME", "Target" to hms(startSec)))
                        sought = true
                    }
                    if (sawPlaying && message?.startsWith("正在投到") == true) message = null
                    // 电视那边放完了 / 被人停了：投屏结束，本机停在电视放到的位置
                    if (sawPlaying && sought && st.stopped) {
                        message = "电视那边停了"
                        active = null
                        status = null
                        c.seekTo(lastPosSec * 1000.0)
                        return@launch
                    }
                }
                delay(1_000)
            }
        }
    }

    private suspend fun statusOf(h: CastHost, d: CastDevice): CastStatus? =
        parseStatus(h.action(d, "GetTransportInfo", inst), h.action(d, "GetPositionInfo", inst))

    fun togglePause(scope: CoroutineScope) {
        val d = active ?: return
        val h = host ?: return
        val pausing = status?.playing == true
        scope.launch { if (pausing) h.action(d, "Pause", inst) else h.action(d, "Play", inst + ("Speed" to "1")) }
    }

    fun seekBy(scope: CoroutineScope, deltaSec: Long) {
        val d = active ?: return
        val h = host ?: return
        val target = (lastPosSec + deltaSec).coerceAtLeast(0)
        lastPosSec = target
        scope.launch { h.action(d, "Seek", inst + listOf("Unit" to "REL_TIME", "Target" to hms(target))) }
    }

    /** 结束投屏：电视停下；本机跳到电视放到的位置，[resume] = 本机接着放。 */
    fun stop(scope: CoroutineScope, play: PlaySession?, resume: Boolean) {
        val d = active ?: return
        poller?.cancel()
        poller = null
        active = null
        status = null
        message = null
        val pos = lastPosSec
        scope.launch { host?.action(d, "Stop", inst) }
        play?.controller?.let { c ->
            c.seekTo(pos * 1000.0)
            if (resume) c.play()
        }
    }
}
