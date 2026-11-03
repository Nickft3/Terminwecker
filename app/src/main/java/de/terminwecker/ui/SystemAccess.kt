package de.terminwecker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.terminwecker.alarm.AlarmScheduler
import de.terminwecker.data.CalendarRepository

/** Stand aller Berechtigungen, die für einen zuverlässigen Wecker nötig sind. */
data class PermissionStatus(
    val calendar: Boolean,
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val critical: Boolean get() = calendar && notifications && exactAlarms && fullScreen
    val allGood: Boolean get() = critical && batteryUnrestricted

    companion object {
        fun read(context: Context): PermissionStatus {
            val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            val fullScreen = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            return PermissionStatus(
                calendar = CalendarRepository.hasPermission(context),
                notifications = notificationsGranted && NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarms = AlarmScheduler.canScheduleExact(context),
                fullScreen = fullScreen,
                batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                    .isIgnoringBatteryOptimizations(context.packageName),
            )
        }
    }
}

/** Sprünge in die passenden Systemeinstellungen. */
object SystemSettings {
    private fun pkg(context: Context) = Uri.fromParts("package", context.packageName, null)

    fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg(context))

    fun notifications(context: Context) = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun exactAlarms(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg(context))
        else appDetails(context)

    fun fullScreen(context: Context) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg(context))
        } else {
            appDetails(context)
        }

    @SuppressLint("BatteryLife")
    fun battery(context: Context) = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg(context))

    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(appDetails(context))
        }
    }
}
