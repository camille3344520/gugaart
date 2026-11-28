package com.gugaart.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.gugaart.ui.download.DownloadHost
import com.gugaart.ui.download.DownloadStorage
import com.gugaart.ui.download.FileDownloadStorage
import com.gugaart.ui.download.NetState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.lang.ref.WeakReference

/**
 * 安卓手机的离线下载能力（1.6 批次 E；电视不设）：
 * - 存在应用内部存储 `filesDir/downloads`（卸载即清）。不用 `getExternalFilesDir`：那里经系统的 FUSE（MediaProvider）读写，
 *   手机模拟器上 VLC 读那里的文件会把 MediaProvider 弄崩（libfuse_jni SIGSEGV），连带我们的进程一起死；现在的安卓上两者是同一块分区，空间一样；
 * - 网络：默认网络的回调（有网 = 有 INTERNET 能力；Wi-Fi = NOT_METERED）；
 * - 后台：有活在干时起前台服务 [DownloadService]（系统要求的通知栏进度），都停了就停掉。
 * 整个进程一份（应用上下文），Activity 重建时只换 [activity]（要通知权限时用）。
 */
class AndroidDownloadHost(context: Context) : DownloadHost {
    private val app = context.applicationContext

    /** 当前的 Activity（要通知权限时用；弱引用，不拖住旧的 Activity）。 */
    var activity: WeakReference<Activity>? = null

    override val storage: DownloadStorage = FileDownloadStorage(File(app.filesDir, "downloads"))

    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val net = MutableStateFlow(read(cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }))
    override val network: StateFlow<NetState> = net

    init {
        runCatching {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    net.value = read(caps)
                }

                override fun onLost(network: Network) {
                    net.value = NetState(online = false, unmetered = false)
                }

                override fun onUnavailable() {
                    net.value = NetState(online = false, unmetered = false)
                }
            })
        }
    }

    private fun read(caps: NetworkCapabilities?): NetState = if (caps == null) {
        NetState(online = false, unmetered = false)
    } else {
        NetState(
            online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        )
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val stop = Runnable { runCatching { app.stopService(Intent(app, DownloadService::class.java)) } }

    override fun keepAlive(on: Boolean) {
        main.removeCallbacks(stop)
        if (on) {
            // 后台里系统可能不让起前台服务（ForegroundServiceStartNotAllowedException）：不要紧，下载照样在进程里走
            runCatching { app.startForegroundService(Intent(app, DownloadService::class.java)) }
        } else {
            // 晚一点再停：一个很小的文件几毫秒就下完，服务还没来得及 startForeground 就被停掉，系统会让应用崩掉
            main.postDelayed(stop, STOP_DELAY_MS)
        }
    }

    override fun onUserStart() {
        if (Build.VERSION.SDK_INT < 33) return
        val a = activity?.get() ?: return
        if (a.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        runCatching { a.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS) }
    }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 0x6d6c
        const val STOP_DELAY_MS = 3_000L
    }
}
