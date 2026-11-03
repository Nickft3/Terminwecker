package de.terminwecker.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Liest den lokal synchronisierten Kalender (z. B. Google-Kalender) über den CalendarContract.
 * Kein Login, kein Internet: Android hält die Termine selbst vor.
 */
object CalendarRepository {
    private const val TAG = "CalendarRepository"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun calendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
        )
        val result = mutableListOf<CalendarInfo>()
        try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
                ?.use { c ->
                    while (c.moveToNext()) {
                        result += CalendarInfo(
                            id = c.getLong(0),
                            name = c.getString(1) ?: "",
                            account = c.getString(2) ?: "",
                            color = c.getInt(3),
                            visible = c.getInt(4) == 1,
                        )
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Kalender konnten nicht gelesen werden", e)
        }
        return result.sortedWith(compareBy({ it.account.lowercase() }, { it.name.lowercase() }))
    }

    /**
     * Alle Termin-Vorkommen im Zeitraum, Serien bereits aufgelöst und nach Beginn sortiert.
     * Liefert null, wenn der Kalender gerade nicht lesbar ist, damit Aufrufer ihren Stand behalten.
     */
    fun instances(context: Context, from: Long, to: Long): List<EventInstance>? {
        if (!hasPermission(context)) return null
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.RRULE,
            CalendarContract.Instances.RDATE,
            CalendarContract.Instances.ORIGINAL_ID,
            CalendarContract.Instances.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Instances.STATUS,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    val list = ArrayList<EventInstance>(c.count)
                    while (c.moveToNext()) {
                        if (!c.isNull(13) && c.getInt(13) == CalendarContract.Events.STATUS_CANCELED) continue
                        list += EventInstance(
                            eventId = c.getLong(0),
                            title = c.getString(1) ?: "",
                            begin = c.getLong(2),
                            end = c.getLong(3),
                            allDay = c.getInt(4) == 1,
                            calendarId = c.getLong(5),
                            calendarName = c.getString(6) ?: "",
                            color = c.getInt(7),
                            location = c.getString(8) ?: "",
                            recurring = !c.getString(9).isNullOrBlank() || !c.getString(10).isNullOrBlank(),
                            originalId = c.getString(11)?.toLongOrNull() ?: -1,
                            originalInstanceTime = if (c.isNull(12)) 0 else c.getLong(12),
                        )
                    }
                    list
                }
        } catch (e: Exception) {
            Log.w(TAG, "Termine konnten nicht gelesen werden", e)
            null
        }
    }
}
