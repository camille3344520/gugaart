package com.gugaart.desktop.mac

import com.gugugaga.app.update.DesktopUpdateHost
import com.gugugaga.app.update.DownloadTarget
import com.gugugaga.app.update.InstallOutcome
import com.gugugaga.app.update.RangeSource
import com.gugugaga.app.update.ReleaseAsset
import com.gugugaga.app.update.UpdateHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * macOS 的更新宿主（任务单 M2-8；decisions.md D-lead-8；release-1.3.md A11）：号外的「下载并安装」在 Mac 上 =
 * 下载 `.dmg`（断点续传、按清单校验大小和 SHA-256——上游更新器做）→ `open` 它（系统挂载、访达里出现应用和「应用程序」）→ 应用退出，
 * 用户自己把「咕咕嘎嘎」拖进「应用程序」替换旧的。不接 Sparkle。
 *
 * 下载那一半（HTTP Range、文件落在哪）用上游现成的：[DesktopUpdateHost] 的 `rangeSource` / `createTarget`（公开接口，只借它的
 * 下载目录和字节源，不用它的 MSI 安装）；装这一步是这里的 [install]。资产键 `macos-dmg`（D-lead-17：同一份清单）。
 * [launch] 单测注入（不真的起 `open`）。
 */
internal class MacUpdateHost(
    private val updateDir: File,
    private val onQuit: () -> Unit,
    private val launch: (List<String>) -> Unit = { cmd ->
        ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    },
) : UpdateHost {
    private val files = DesktopUpdateHost(updateDir, appExe = { null }, onQuit = {})

    override val assetKey: String = ASSET_KEY
    override val rangeSource: RangeSource get() = files.rangeSource

    override suspend fun createTarget(asset: ReleaseAsset): DownloadTarget? = files.createTarget(asset)

    /** Mac 上打开 `.dmg` 不要额外的权限。 */
    override fun canInstall(): Boolean = true

    override fun requestInstallPermission(): Boolean = true

    override suspend fun install(asset: ReleaseAsset, target: DownloadTarget): InstallOutcome {
        val dmg = File(target.path)
        val exists = withContext(Dispatchers.IO) { dmg.isFile && dmg.length() > 0 }
        if (!exists) return InstallOutcome.Failed("下载的文件不见了，请重试")
        if (!dmg.extension.equals("dmg", ignoreCase = true)) return InstallOutcome.Failed("更新包不是 .dmg，无法打开")
        try {
            withContext(Dispatchers.IO) { launch(openCommand(dmg)) }
        } catch (e: Exception) {
            return InstallOutcome.Failed("无法打开安装包：" + (e.message ?: "系统拒绝"))
        }
        // 镜像已经交给系统挂载；应用退出，用户拖进「应用程序」替换旧的（正在运行的应用不该被替换）
        onQuit()
        return InstallOutcome.Started
    }

    /** 已经是那一版了 / 号外撤了：下好的 `.dmg` 只占地方。 */
    override suspend fun discardDownloaded() {
        withContext(Dispatchers.IO) { updateDir.listFiles()?.forEach { it.delete() } }
    }

    companion object {
        const val ASSET_KEY = "macos-dmg"

        /** `open <dmg>`：系统挂载镜像、在访达里打开。纯函数（单测）。 */
        fun openCommand(dmg: File): List<String> = listOf("open", dmg.absolutePath)

        /** 下载放哪：`~/Library/Caches/GugaArt/update/`（ADR 0004 §2.9；系统清缓存可以删它）。 */
        fun defaultDir(home: String = System.getProperty("user.home")): File = File(home, "Library/Caches/GugaArt/update")
    }
}
