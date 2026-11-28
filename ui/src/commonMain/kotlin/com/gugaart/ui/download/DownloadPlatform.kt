package com.gugaart.ui.download

import kotlinx.coroutines.flow.StateFlow

/*
 * 离线下载的平台缝（1.6 批次 E）：共用的部分（队列、状态、断点续传、离线播放、进度暂存回报）都在 commonMain，
 * 平台只给这里的三样——存在哪（[DownloadStorage]）、现在的网（[NetState]）、后台怎么保活（[DownloadHost.keepAlive]）。
 * 壳在启动时设 [Downloads.host]：安卓手机设（前台服务 + 通知栏进度），iPhone 设（`IosDownloadHost`：NWPathMonitor + 后台借一段时间，批次 F3），
 * 电视 / 电脑不设（=没有下载入口）。
 */

/** 下载目录（应用私有，卸载即清）。文件名只会是 [fileNameFor] / [partNameOf] / [INDEX_FILE] / [REPORTS_FILE] 这几种。 */
interface DownloadStorage {
    /** 文件的字节数；没有 = -1。 */
    fun length(name: String): Long

    /** 打开来写：[append] = 接在末尾（断点续传），否则从头写（清空）。 */
    fun open(name: String, append: Boolean): DownloadSink

    /** 改名（下完：`x.mkv.part` → `x.mkv`）；目标已有就覆盖。 */
    fun rename(from: String, to: String): Boolean

    fun delete(name: String)

    fun readText(name: String): String?

    /** 整份写（先写临时文件再换，写一半断电不坏原来的）。 */
    fun writeText(name: String, text: String)

    /** 给播放内核的地址（`file:///…`，三个内核都认）。 */
    fun fileUrl(name: String): String

    /** 这块盘还剩多少；不知道 = null。 */
    fun freeBytes(): Long?
}

/** 一个打开着的写入。 */
interface DownloadSink {
    fun write(bytes: ByteArray, offset: Int, length: Int)
    fun close()
}

/** 现在的网：[online] = 有能用的网；[unmetered] = 不按流量计费（Wi-Fi / 有线）。 */
data class NetState(val online: Boolean, val unmetered: Boolean) {
    companion object {
        val UNKNOWN_ONLINE = NetState(online = true, unmetered = true)
    }
}

/** 平台给的下载能力。 */
interface DownloadHost {
    val storage: DownloadStorage

    /** 网络变化（安卓 ConnectivityManager 的回调；iPhone NWPathMonitor）。 */
    val network: StateFlow<NetState>

    /**
     * 有活在干（[on] = true）/ 都停了：安卓起 / 停前台服务（通知栏进度由服务自己看 [Downloads.rows]）；iPhone 退到后台时借一段后台时间。
     * 系统不让起（后台里）也不要紧：下载照样在应用进程里走，只是没有通知。
     */
    fun keepAlive(on: Boolean)

    /** 用户第一次点「下载」的那一刻（在前台）：安卓 13+ 在这里要通知权限（拒了也照样下，只是通知栏看不到进度）。 */
    fun onUserStart() {}
}
