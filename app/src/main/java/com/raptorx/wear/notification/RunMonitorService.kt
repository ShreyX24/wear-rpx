package com.raptorx.wear.notification

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
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.raptorx.wear.MainActivity
import com.raptorx.wear.data.RpxRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the shared Socket.IO connection alive and turns
 * lifecycle events into wrist notifications — so failures/completions reach the
 * user even when the app UI is closed.
 *
 * It reuses [RpxRepository]'s single socket (the UI shares it), listens to the
 * global `automation_event` stream (the master auto-joins every client to
 * `general_updates` on connect — no room join needed), and delegates the
 * "should this buzz?" question entirely to [NotificationPolicy].
 *
 * Trade-off: a persistent foreground socket costs some battery vs. a periodic
 * poll, but it's the only way to deliver low-latency failure alerts, which is
 * the whole point of the app.
 */
class RunMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // "connectedDevice" type: we keep a continuous network connection to an
        // external device (the RPX master). No daily time cap, unlike dataSync.
        ServiceCompat.startForeground(
            this,
            FOREGROUND_ID,
            buildForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            },
        )

        val repo = RpxRepository.get(this)
        // Ensure the socket is up (no-op if the UI already connected it).
        repo.masterUrl.value?.let { repo.socket.connect(it) }

        scope.launch {
            repo.socket.automationEvents.collect { event ->
                NotificationPolicy.decide(event)?.let { postAlert(it) }
            }
        }
        // If the OS kills us, restart so monitoring resumes.
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---- Notifications -------------------------------------------------------

    private fun postAlert(decision: NotificationPolicy.Decision) {
        // POST_NOTIFICATIONS may be denied; NotificationManagerCompat.notify is a
        // no-op without it, so we don't crash — we just stay silent.
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val priority = when (decision.priority) {
            NotificationPolicy.Priority.HIGH -> NotificationCompat.PRIORITY_HIGH
            NotificationPolicy.Priority.NORMAL -> NotificationCompat.PRIORITY_DEFAULT
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(decision.title)
            .setContentText(decision.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(decision.text))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        NotificationManagerCompat.from(this).notify(decision.notificationId, notification)
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Watching RPX runs")
            .setContentText("Alerts you on failures and completions")
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS, "Monitor status", NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Ongoing 'watching runs' status" },
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS, "Run alerts", NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Run failed / completed / iteration skipped" },
        )
    }

    companion object {
        private const val FOREGROUND_ID = 1001
        private const val CHANNEL_STATUS = "rpx_monitor_status"
        private const val CHANNEL_ALERTS = "rpx_run_alerts"

        fun start(context: Context) {
            val intent = Intent(context, RunMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RunMonitorService::class.java))
        }
    }
}
