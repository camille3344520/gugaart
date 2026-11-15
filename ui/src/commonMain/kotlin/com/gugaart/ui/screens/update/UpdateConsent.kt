package com.gugaart.ui.screens.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gugugaga.app.AppGraph
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.settings.UPDATE_AUTO_DOWNLOAD_KEY
import com.gugugaga.app.settings.updateAutoDownload
import kotlinx.serialization.json.JsonPrimitive

/*
 * 更新要用户点、安装要用户确认（用户 2026-11-15：「更不更新让用户点，不要自动更新，还有安装也是，用户确认了再安装」）。
 *
 * 上游的更新器会自己动的一处：自动下载（设置 update.autoDownload，默认开）——不计流量的网络上一查到新版就在后台下。
 * 画报每次启动把它关掉，设置里也不再给这个开关（[disableAutoDownload]）——查到新版只出「号外」。
 *
 * 号外上的选项（用户 2026-11-15：「可以弹出版本更新，给用户选择，忽略这个版本……多个选项」，后来「只下载这个选项好像没什么用去掉吧」）：
 *   下载并安装（[downloadAndInstall]：用户挑了它就是同意装）· 忽略这一版（[ignore]：记在本机，这一版不再弹，出了更新的版本照常弹；
 *   设置里手动「检查更新」会解除）· 稍后（上游的 dismiss：这一次运行里不再弹）。强制更新只有「下载并安装」。
 */
object UpdateConsent {
    /** 忽略的那一版（versionCode；0 = 没有）。存本机 [IGNORE_KEY]。 */
    var ignored by mutableIntStateOf(0)
        private set

    const val IGNORE_KEY = "gugaart.update.ignore.v1"

    /** 启动时读一次忽略的版本。 */
    fun load(local: LocalStore) {
        ignored = local.get(IGNORE_KEY)?.trim()?.toIntOrNull() ?: 0
    }

    /** 「忽略这一版」：记下，号外收起。 */
    fun ignore(graph: AppGraph, versionCode: Int) {
        ignored = versionCode
        graph.deps.local.set(IGNORE_KEY, versionCode.toString())
        graph.update.dismiss()
    }

    /** 手动「检查更新」：用户想知道，忽略的那一版也解除。 */
    fun checkByHand(graph: AppGraph) {
        if (ignored != 0) {
            ignored = 0
            graph.deps.local.remove(IGNORE_KEY)
        }
        graph.update.checkNow()
    }

    /** 「下载并安装」/「再试一次」：用户挑了它 = 同意下好就装（交给上游的「下载并安装」）。 */
    fun downloadAndInstall(graph: AppGraph) {
        graph.update.downloadAndInstall()
    }

    /** 启动时：自动下载关掉（设置里不再有这个开关）。 */
    fun disableAutoDownload(graph: AppGraph) {
        if (graph.settings.value.updateAutoDownload) graph.settings.patch("update", UPDATE_AUTO_DOWNLOAD_KEY to JsonPrimitive(false))
    }
}
