package com.gugaart.ui.screens.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.gugugaga.app.AppGraph
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.settings.UPDATE_AUTO_DOWNLOAD_KEY
import com.gugugaga.app.settings.updateAutoDownload
import com.gugugaga.app.update.ReleaseInfo
import com.gugugaga.app.update.UpdateState
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
        hideAgain()
        graph.update.dismiss()
    }

    /**
     * 「放到后台」/「稍后」之后又叫回来的那一版（versionCode；0 = 没有）：号外照常显示（[dismissedFor]）。
     * 用户 2026-11-16：「安卓更新的时候点后台，再次点击没反应」——上游在下载 / 安装中途不理「检查更新」，号外又被收着，看起来就是点了没反应。
     */
    var reshown by mutableIntStateOf(0)
        private set

    /** 每叫回来一次加一（电视的「放到后台」是号外自己的开关，看到它变就重新打开）。 */
    var reshowTick by mutableIntStateOf(0)
        private set

    fun reshow(versionCode: Int) {
        reshown = versionCode
        reshowTick++
    }

    /** 又点了「稍后」/「放到后台」：不再硬叫出来。 */
    fun hideAgain() {
        reshown = 0
    }

    /** 号外判断收不收用的「按过稍后的那一版」：叫回来的那一版不算。纯函数，有单测。 */
    fun dismissedFor(dismissed: Int, reshown: Int = this.reshown): Int = if (dismissed != 0 && dismissed == reshown) 0 else dismissed

    /** 正在进行中的那一版（下载中 / 下好了 / 安装中 / 等安装权限）；这时上游的「检查更新」什么都不做。纯函数，有单测。 */
    fun busyRelease(state: UpdateState): ReleaseInfo? = when (state) {
        is UpdateState.Downloading -> state.release
        is UpdateState.Downloaded -> state.release
        is UpdateState.Installing -> state.release
        is UpdateState.NeedsPermission -> state.release
        else -> null
    }

    /** 手动「检查更新」：用户想知道，忽略的那一版也解除。正在下 / 装的那一版：把号外叫回来（安装卡住了就报掉，变成「再试一次」）。 */
    fun checkByHand(graph: AppGraph) {
        if (ignored != 0) {
            ignored = 0
            graph.deps.local.remove(IGNORE_KEY)
        }
        val busy = busyRelease(graph.update.state.value)
        if (busy != null) {
            reshow(busy.versionCode)
            com.gugaart.ui.update.UpdateStall.check(graph)
            return
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
