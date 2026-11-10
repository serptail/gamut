package com.gamut.feature.resolver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Access {
        fun downloads(): TrackDownloadManager
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = EntryPointAccessors.fromApplication(applicationContext, Access::class.java).downloads()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        val first = notification(manager.batch.value)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(ID, first)
        }
        if (watcher == null) {
            watcher = scope.launch {
                manager.batch.collect { batch ->
                    if (batch.total == 0) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        notifications.notify(ID, notification(batch))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(batch: DownloadBatch): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val text = when {
            batch.total > 0 && batch.waitingForWifi -> "Waiting for Wi\u2011Fi \u00b7 ${batch.total - batch.done} left"
            batch.total > 0 -> "${minOf(batch.done + 1, batch.total)} of ${batch.total}" + (batch.current?.let { " \u00b7 $it" } ?: "")
            else -> "Starting\u2026"
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading songs")
            .setContentText(text)
            .setProgress(batch.total.coerceAtLeast(1), batch.done, batch.total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    private companion object {
        const val CHANNEL = "downloads"
        const val ID = 4107
    }
}
