package dev.waddle.android.terminal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import dev.waddle.android.MainActivity
import dev.waddle.android.R

class TerminalService : Service() {
    inner class LocalBinder : Binder() { val service get() = this@TerminalService }
    lateinit var workspace: TerminalWorkspace
        private set
    private var foreground = false
    private var initialized = false

    fun openInitialSession() {
        if (initialized) return
        initialized = true
        workspace.create()
    }

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Terminal sessions", NotificationManager.IMPORTANCE_LOW)
        )
        workspace = TerminalWorkspace(this) { if (foreground) updateNotification() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            workspace.closeAll()
            foreground = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        foreground = true
        startForeground(NOTIFICATION_ID, notification())
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder = LocalBinder()
    override fun onDestroy() {
        foreground = false
        workspace.closeAll()
        super.onDestroy()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, TerminalService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val running = workspace.sessions.count { it.terminal.isRunning }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_terminal_notification)
            .setContentTitle("Waddle · $running running ${if (running == 1) "session" else "sessions"}")
            .setContentText("Tap to return to your terminal")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Close all sessions", stop).build())
            .build()
    }

    companion object {
        private const val CHANNEL = "terminal_sessions"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "dev.waddle.android.STOP_SESSIONS"
    }
}
