package com.gugaart.android

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.net.Uri
import android.util.Log
import com.gugaart.ui.tv.WatchNextEntry

/**
 * 1.6 B4：安卓电视系统首页的「继续观看」（Watch Next）。
 *
 * 直接用系统的 `TvContract.WatchNextPrograms`（API 26+，电视 minSdk 28）：计划里写的 `androidx.tvprovider` 只是它的一层包装，
 * 本机离线的 Gradle 缓存里没有那个库（不联网下依赖），系统接口能做同样的事。
 * - 每条的 `INTERNAL_PROVIDER_ID` = `服务器 id|用户 id|条目 id`；点开的 intent 带同一个值（[EXTRA]），MainActivity 认出来直接开播；
 * - 只动自己写的行（系统只给本应用看自己的行）；不在新列表里的删掉（看完的、换了账号的）；
 * - 用户在系统首页里移除过的（`BROWSABLE = 0`）不再写回去；
 * - 没有 TvProvider 的电视（海信 VIDAA 实测没有 `android.media.tv`）：第一次失败就记下来，之后什么都不做。
 * 都在后台线程里做（ContentResolver 是跨进程调用）。
 */
class WatchNextHost(private val context: Context) {
    @Volatile
    private var unavailable = false
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "gugaart-watchnext").apply { isDaemon = true } }

    fun sync(entries: List<WatchNextEntry>) {
        if (unavailable) return
        worker.execute {
            try {
                write(entries)
            } catch (e: Exception) {
                // 没有 TvProvider / 不让写：这台电视上就不做了
                unavailable = true
                Log.i(TAG, "[watchnext] unavailable: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun write(entries: List<WatchNextEntry>) {
        val cr = context.contentResolver
        val uri = TvContract.WatchNextPrograms.CONTENT_URI
        if (cr.acquireContentProviderClient(uri)?.also { it.close() } == null) {
            unavailable = true
            Log.i(TAG, "[watchnext] no TvProvider on this device")
            return
        }
        // 自己已经写过的：内部 id → (行 id, 用户移除过没有)
        val existing = HashMap<String, Pair<Long, Boolean>>()
        cr.query(uri, arrayOf(TvContract.WatchNextPrograms._ID, TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, TvContract.WatchNextPrograms.COLUMN_BROWSABLE), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(1) ?: continue
                existing[key] = c.getLong(0) to (c.getInt(2) != 0)
            }
        }
        val wanted = entries.associateBy { it.key }
        var removed = 0
        for ((key, row) in existing) {
            if (key !in wanted) {
                cr.delete(TvContract.buildWatchNextProgramUri(row.first), null, null)
                removed++
            }
        }
        var inserted = 0
        var updated = 0
        var skipped = 0
        for (e in entries) {
            val row = existing[e.key]
            if (row != null && !row.second) {
                skipped++ // 用户在系统首页里移除过
                continue
            }
            val v = values(e)
            if (row == null) {
                if (cr.insert(uri, v) != null) inserted++
            } else {
                cr.update(TvContract.buildWatchNextProgramUri(row.first), v, null, null)
                updated++
            }
        }
        Log.i(TAG, "[watchnext] sync n=${entries.size} +$inserted ~$updated -$removed skip=$skipped")
    }

    private fun values(e: WatchNextEntry) = ContentValues().apply {
        put(TvContract.WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE, TvContract.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
        put(TvContract.WatchNextPrograms.COLUMN_TYPE, if (e.episode) TvContract.PreviewPrograms.TYPE_TV_EPISODE else TvContract.PreviewPrograms.TYPE_MOVIE)
        put(TvContract.WatchNextPrograms.COLUMN_TITLE, e.title)
        if (e.episodeTitle != null) put(TvContract.WatchNextPrograms.COLUMN_EPISODE_TITLE, e.episodeTitle)
        e.season?.let { put(TvContract.WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER, it.toString()) }
        e.episodeNo?.let { put(TvContract.WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER, it.toString()) }
        if (e.image != null) {
            put(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI, e.image)
            put(
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO,
                if (e.wide) TvContract.PreviewPrograms.ASPECT_RATIO_16_9 else TvContract.PreviewPrograms.ASPECT_RATIO_2_3,
            )
        }
        put(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS, e.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        e.durationMs?.let { put(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS, it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        put(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, e.engagedAtMs)
        put(TvContract.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, e.key)
        put(TvContract.WatchNextPrograms.COLUMN_INTENT_URI, playIntent(context, e.key).toUri(Intent.URI_INTENT_SCHEME))
    }

    companion object {
        const val TAG = "GugaArt"

        /** 系统首页点开一条时带回来的值：`服务器 id|用户 id|条目 id`。 */
        const val EXTRA = "com.gugaart.watchNext"

        fun playIntent(context: Context, key: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("gugaart-watchnext:${Uri.encode(key)}"))
                .setComponent(ComponentName(context, MainActivity::class.java))
                .putExtra(EXTRA, key)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
