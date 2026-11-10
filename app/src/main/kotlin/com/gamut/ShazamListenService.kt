package com.gamut

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
import com.gamut.core.designsystem.R as DesignR
import com.gamut.feature.search.ShazamSession
import com.gamut.feature.search.ShazamState
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ShazamListenService : Service() {
    @Inject lateinit var session: ShazamSession

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels(this)
        val listening = NotificationCompat.Builder(this, CHANNEL_LISTENING)
            .setSmallIcon(DesignR.drawable.ic_shazam)
            .setContentTitle("Listening…")
            .setContentText("Gamut is identifying the music around you")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent(this))
            .addAction(0, "Cancel", cancelIntent(this))
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_LISTENING, listening, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(ID_LISTENING, listening)
        }

        if (intent?.action == ACTION_CANCEL) {
            session.dismiss()
            stopSelf()
            return START_NOT_STICKY
        }

        cancelResultNotification(this)
        session.start()
        scope.launch {
            val end = session.state.first { it !is ShazamState.Listening }
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (end !is ShazamState.Idle && !session.appVisible) postResult(end)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun postResult(end: ShazamState) {
        val (title, text) = when (end) {
            is ShazamState.Found -> end.track.title to end.track.artist
            ShazamState.PermissionDenied -> "Microphone access is off" to "Tap to open Gamut"
            ShazamState.NotFound -> "Couldn’t recognize this one" to "Tap to try again"
            is ShazamState.Failed -> "Something went wrong" to end.message
            else -> return
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_RESULT)
            .setSmallIcon(DesignR.drawable.ic_shazam)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(this))
            .build()
        getSystemService(NotificationManager::class.java).notify(ID_RESULT, notification)
    }

    companion object {
        private const val CHANNEL_LISTENING = "shazam_listening"
        private const val CHANNEL_RESULT = "shazam_result"
        private const val ID_LISTENING = 4101
        private const val ID_RESULT = 4102
        private const val ACTION_CANCEL = "com.gamut.action.SHAZAM_CANCEL"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, ShazamListenService::class.java))
        }

        private fun cancelResultNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(ID_RESULT)
        }

        private fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LISTENING, "Listening", NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, "Song found", NotificationManager.IMPORTANCE_HIGH),
            )
        }

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun cancelIntent(context: Context): PendingIntent {
            val intent = Intent(context, ShazamListenService::class.java).setAction(ACTION_CANCEL)
            return PendingIntent.getForegroundService(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
