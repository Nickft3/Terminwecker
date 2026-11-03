package de.terminwecker

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import de.terminwecker.alarm.AlarmScheduler
import de.terminwecker.alarm.CalendarJobService

class TerminweckerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        CalendarJobService.ensureScheduled(this)
        AlarmScheduler.rescheduleAsync(this)
    }
}

object Notifications {
    const val CHANNEL_ALARM = "alarm"
    const val CHANNEL_INFO = "info"
    const val ID_RINGING = 1
    const val ID_MISSED = 2

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarm = NotificationChannel(CHANNEL_ALARM, "Klingelnder Wecker", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Wird angezeigt, solange ein Wecker klingelt"
            // Ton und Vibration kommen vom Weckerdienst selbst.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val info = NotificationChannel(CHANNEL_INFO, "Hinweise", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Verpasste Wecker"
        }
        nm.createNotificationChannels(listOf(alarm, info))
    }
}
