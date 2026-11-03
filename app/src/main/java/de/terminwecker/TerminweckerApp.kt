package de.terminwecker

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import de.terminwecker.alarm.AlarmScheduler
import de.terminwecker.alarm.CalendarJobService
import de.terminwecker.ui.Fmt

class TerminweckerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Fmt.init(this)
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

    /** Legt die Kanäle an bzw. aktualisiert ihre Namen, falls sich die Sprache geändert hat. */
    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val alarmName = context.getString(R.string.channel_alarm)
        val alarm = NotificationChannel(CHANNEL_ALARM, alarmName, NotificationManager.IMPORTANCE_HIGH).apply {
            description = context.getString(R.string.channel_alarm_desc)
            // Ton und Vibration kommen vom Weckerdienst selbst.
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val info = NotificationChannel(CHANNEL_INFO, context.getString(R.string.channel_info), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_info_desc)
        }
        nm.createNotificationChannels(listOf(alarm, info))
    }
}
