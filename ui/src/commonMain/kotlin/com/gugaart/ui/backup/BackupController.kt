package com.gugaart.ui.backup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugaart.ui.components.civilFromEpochDay
import com.gugaart.ui.data.GugaSettingsStore
import com.gugugaga.app.AppGraph
import com.gugugaga.app.platform.localUtcOffsetMs
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.currentTimeMillis
import com.gugugaga.core.platformHttpClient
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** 本地时间「2026-11-15 22:10」。纯函数，有单测。 */
fun backupTimeText(epochMs: Long, utcOffsetMs: Long): String {
    val local = epochMs + utcOffsetMs
    val day = local.floorDiv(86_400_000L)
    val (y, m, d) = civilFromEpochDay(day)
    val minutes = (local.mod(86_400_000L) / 60_000L).toInt()
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "$y-${two(m)}-${two(d)} ${two(minutes / 60)}:${two(minutes % 60)}"
}

/** 备份里写的「哪一端做的」。电脑端写桌面宿主报的平台名（`LocalDesktopHost.platformName`：Windows / macOS）。 */
fun backupPlatformLabel(mode: UiMode, desktopPlatform: String = "Windows"): String = when (mode) {
    UiMode.Phone -> "安卓手机"
    UiMode.Tv -> "安卓电视"
    UiMode.Desktop -> desktopPlatform
}

/**
 * 备份与还原的状态和动作（手机 / 电脑的设置页和电视的设置页共用；ConfigBackup.kt 说了备份里有什么）。
 * 还原分两步：先读出来给人看是哪一份（[pending]），确认了才 [apply]。
 */
class BackupController(
    private val g: AppGraph,
    private val prefs: GugaSettingsStore,
    val files: BackupFileHost?,
    private val scope: CoroutineScope,
    /** 电脑端的平台名（`LocalDesktopHost.platformName`）；手机 / 电视不用。 */
    private val desktopPlatform: String = "Windows",
) {
    var webdav by mutableStateOf(WebDavConfig("", "", ""))
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var pending by mutableStateOf<ParsedBackup?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    private val dav by lazy { WebDavClient(g.deps.transport ?: KtorTransport(platformHttpClient())) }

    private fun now(): String = currentTimeMillis().let { backupTimeText(it, localUtcOffsetMs(it)) }

    private suspend fun make(): String = makeBackup(g, backupPlatformLabel(g.deps.uiMode, desktopPlatform), now())

    fun load() {
        scope.launch { webdav = WebDavConfig.load(g.deps.secure) }
    }

    fun saveWebdav(c: WebDavConfig) = act("dav-save") {
        WebDavConfig.save(g.deps.secure, c)
        webdav = c
        message = "WebDAV 设置已保存"
    }

    fun testWebdav() = act("dav-test") { message = dav.test(webdav) ?: "WebDAV 连接正常" }

    fun upload() = act("dav-up") {
        val err = dav.upload(webdav, make())
        message = err ?: "已备份到 WebDAV · ${now()}"
    }

    fun download() = act("dav-down") { dav.download(webdav).fold({ stage(it) }, { message = it.message }) }

    fun exportFile() = act("file-out") {
        val host = files ?: return@act
        message = if (host.save(BACKUP_FILE_NAME, make())) "已导出 · ${now()}" else null
    }

    fun importFile() = act("file-in") {
        val host = files ?: return@act
        val text = host.open() ?: return@act
        stage(text)
    }

    fun apply() = act("apply") {
        val p = pending ?: return@act
        val sum = restoreBackup(g, prefs, p)
        pending = null
        message = sum.text
    }

    fun cancel() {
        pending = null
        message = null
    }

    private fun stage(text: String) {
        parseBackup(text).fold(
            { pending = it; message = null },
            { message = (it as? BackupException)?.error?.text ?: BackupError.Unreadable.text },
        )
    }

    private fun act(name: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = name
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = "失败"
            } finally {
                busy = null
            }
        }
    }
}

/** 一份待还原的备份给人看的一句：「来自 Windows · 2026-11-15 22:10 · 3 台服务器」。 */
fun pendingText(p: ParsedBackup): String = listOfNotNull(
    p.platform?.let { "来自$it" },
    p.createdAt,
    "${p.servers.size} 台服务器".takeIf { p.servers.isNotEmpty() },
    "${p.danmakuServers.size} 台弹幕服务器".takeIf { p.danmakuServers.isNotEmpty() },
).joinToString(" · ")
