package app.skopry.android.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import app.skopry.android.MainActivity
import app.skopry.android.R

/**
 * Keeps the app's process, and so its connection to the Mac, alive while another app is in front. Without it
 * Samsung's freezer aborted the socket about 5 s after the app left the screen ("Software caused connection
 * abort") and the stream had to reconnect, and start over, each time the person looked at something else.
 * Runs only while a session is open or opening; the notification is how Android makes it visible.
 */
class SessionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Skopry session", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Skopry is connected to your Mac")
            .setContentText("Tap to go back to the stream")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "session"
        private const val ID = 1

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, SessionService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
