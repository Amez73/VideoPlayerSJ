package com.shareef.videoplayersj.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.shareef.videoplayersj.MainActivity
import com.shareef.videoplayersj.VideoPlayerApp
import com.shareef.videoplayersj.torrent.DownloadFormat
import com.shareef.videoplayersj.torrent.DownloadItem
import com.shareef.videoplayersj.torrent.FinishedDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "downloads"
private const val ONGOING_NOTIFICATION_ID = 2001
private const val FINISHED_NOTIFICATION_BASE_ID = 3000

/**
 * Keeps the app alive while torrents download, with a notification showing their progress.
 * Stops itself once nothing is left downloading.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val engine = (application as VideoPlayerApp).appContainer.torrentEngine
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                ONGOING_NOTIFICATION_ID,
                ongoingNotification(this, emptyList()),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return
        }
        // Also covers the system restarting this service after killing the app mid-download.
        scope.launch(Dispatchers.IO) { engine.start() }
        scope.launch {
            engine.downloads.collect { downloads ->
                if (downloads.isEmpty()) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java)
                        .notify(ONGOING_NOTIFICATION_ID, ongoingNotification(this@DownloadService, downloads))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // Android 15 caps how long a data-sync service may run; downloads carry on while the app is open.
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }

        fun notifyFinished(context: Context, done: FinishedDownload) {
            ensureChannel(context)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download finished")
                .setContentText(done.name)
                .setContentIntent(openDownloadsIntent(context))
                .setAutoCancel(true)
                .build()
            runCatching {
                context.getSystemService(NotificationManager::class.java)
                    .notify(FINISHED_NOTIFICATION_BASE_ID + (done.id.hashCode() and 0xFFF), notification)
            }
        }

        private fun ongoingNotification(context: Context, downloads: List<DownloadItem>): Notification {
            ensureChannel(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openDownloadsIntent(context))
            val single = downloads.singleOrNull()
            if (single != null) {
                builder.setContentTitle(single.name).setContentText(DownloadFormat.summary(single))
                if (single.totalBytes > 0) {
                    builder.setProgress(1000, (single.progress * 1000).toInt(), false)
                } else {
                    builder.setProgress(0, 0, true)
                }
            } else {
                val rate = downloads.sumOf { it.downloadRate }
                builder.setContentTitle(if (downloads.isEmpty()) "Starting downloads" else "Downloading ${downloads.size} torrents")
                    .setContentText(DownloadFormat.rate(rate))
            }
            return builder.build()
        }

        private fun openDownloadsIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_OPEN_DOWNLOADS)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
    }
}
