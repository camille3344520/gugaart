package com.gugaart.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.gugaart.ui.download.DlStatus
import com.gugaart.ui.download.Downloads
import com.gugaart.ui.download.notificationText
import com.gugaart.ui.download.percentOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 离线下载的前台服务（1.6 批次 E，只在手机上，清单在 `src/mobile/AndroidManifest.xml`）：系统要求后台下载有一条常驻通知。
 * 它不下载——下载在 [Downloads] 自己的协程里；它只让进程在后台活着，并把正在下的那一条的进度放在通知栏。
 * [AndroidDownloadHost.keepAlive] 起 / 停它。
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(NotificationChannel(CHANNEL, "下载", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val first = build("下载", "", -1)
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, first)
        if (watch == null) {
            watch = scope.launch {
                Downloads.rows
                    .map { rows ->
                        val running = rows.firstOrNull { it.running }
                        val pct = running?.entry?.takeIf { it.total > 0 && it.status == DlStatus.Queued }?.let { percentOf(it) } ?: -1
                        notificationText(rows)?.let { Triple(it.first, it.second, pct) }
                    }
                    .distinctUntilChanged()
                    .collect { t -> if (t != null) nm?.notify(ID, build(t.first, t.second, t.third)) }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, text: String, percent: Int): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "gugaart-downloads"
        const val ID = 0x6467
    }
}
