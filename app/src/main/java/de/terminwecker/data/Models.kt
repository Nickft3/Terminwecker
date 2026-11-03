package de.terminwecker.data

import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Wofür ein Wecker gilt. */
enum class RuleScope {
    /** Genau ein Termin (bzw. ein einzelnes Vorkommen einer Serie). */
    SINGLE,

    /** Alle Vorkommen einer Terminserie, auch verschobene. */
    SERIES,

    /** Alle Termine, deren Titel passt – übersteht auch Serien, die Google beim Bearbeiten aufteilt. */
    TITLE,
}

/** Wann der Wecker relativ zum Termin klingelt. */
enum class TimingMode {
    /** X Minuten vor Terminbeginn. */
    RELATIVE,

    /** Feste Uhrzeit am Termintag (oder ein paar Tage vorher). */
    FIXED_TIME,
}

/** Ein Vorkommen eines Kalendertermins, wie es CalendarContract.Instances liefert. */
data class EventInstance(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val calendarId: Long,
    val calendarName: String,
    val color: Int,
    val location: String,
    /** True für den Haupttermin einer Serie (hat RRULE/RDATE). */
    val recurring: Boolean,
    /** Id des Serien-Haupttermins, wenn dieses Vorkommen einzeln geändert wurde, sonst -1. */
    val originalId: Long,
    val originalInstanceTime: Long,
) {
    val key: String get() = instanceKey(eventId, begin)
    val seriesId: Long get() = if (originalId > 0) originalId else eventId
    val isPartOfSeries: Boolean get() = recurring || originalId > 0

    /** Ganztägige Termine liegen als UTC-Mitternacht vor; sie gelten ab lokaler Mitternacht desselben Datums. */
    fun localStart(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        if (allDay) Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone)
        else Instant.ofEpochMilli(begin).atZone(zone)

    fun localEnd(zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime =
        if (allDay) Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone)
        else Instant.ofEpochMilli(end).atZone(zone)

    fun localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = localStart(zone).toLocalDate()

    companion object {
        fun instanceKey(eventId: Long, begin: Long) = "$eventId@$begin"
    }
}

data class CalendarInfo(
    val id: Long,
    val name: String,
    val account: String,
    val color: Int,
    val visible: Boolean,
)

/** Eine Weckregel: für welche Termine, wann, mit welcher Beschriftung. */
data class AlarmRule(
    val id: Long = 0,
    val scope: RuleScope,
    /** SINGLE: Termin-Id; SERIES: Id des Serien-Haupttermins. */
    val eventId: Long = -1,
    /** SINGLE bei Serien: ursprünglicher Beginn des gewählten Vorkommens. */
    val instanceBegin: Long = 0,
    /** SINGLE: ob der gewählte Termin der Haupttermin einer Serie ist. */
    val recurring: Boolean = false,
    /** Titel des Termins beim Anlegen (nur zur Anzeige). */
    val eventTitle: String = "",
    /** TITLE: Suchtext. */
    val titleQuery: String = "",
    /** TITLE: genauer Titel statt "enthält". */
    val titleExact: Boolean = true,
    /** TITLE: nur dieser Kalender, -1 = alle. */
    val calendarId: Long = -1,
    val calendarName: String = "",
    val timing: TimingMode = TimingMode.RELATIVE,
    val minutesBefore: Int = 15,
    val fixedMinuteOfDay: Int = 8 * 60,
    /** FIXED_TIME: 0 = am Termintag, -1 = Vortag, ... */
    val dayOffset: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    /** Übersprungene Vorkommen als [EventInstance.key]. */
    val skipped: Set<String> = emptySet(),
    /** Zeitpunkt des zuletzt ausgelösten Weckers, damit nichts doppelt klingelt. */
    val lastFiredTrigger: Long = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("scope", scope.name)
        .put("eventId", eventId)
        .put("instanceBegin", instanceBegin)
        .put("recurring", recurring)
        .put("eventTitle", eventTitle)
        .put("titleQuery", titleQuery)
        .put("titleExact", titleExact)
        .put("calendarId", calendarId)
        .put("calendarName", calendarName)
        .put("timing", timing.name)
        .put("minutesBefore", minutesBefore)
        .put("fixedMinuteOfDay", fixedMinuteOfDay)
        .put("dayOffset", dayOffset)
        .put("label", label)
        .put("enabled", enabled)
        .put("skipped", JSONArray(skipped.toList()))
        .put("lastFiredTrigger", lastFiredTrigger)

    companion object {
        fun fromJson(o: JSONObject) = AlarmRule(
            id = o.getLong("id"),
            scope = RuleScope.valueOf(o.getString("scope")),
            eventId = o.optLong("eventId", -1),
            instanceBegin = o.optLong("instanceBegin", 0),
            recurring = o.optBoolean("recurring", false),
            eventTitle = o.optString("eventTitle", ""),
            titleQuery = o.optString("titleQuery", ""),
            titleExact = o.optBoolean("titleExact", true),
            calendarId = o.optLong("calendarId", -1),
            calendarName = o.optString("calendarName", ""),
            timing = TimingMode.valueOf(o.optString("timing", TimingMode.RELATIVE.name)),
            minutesBefore = o.optInt("minutesBefore", 15),
            fixedMinuteOfDay = o.optInt("fixedMinuteOfDay", 8 * 60),
            dayOffset = o.optInt("dayOffset", 0),
            label = o.optString("label", ""),
            enabled = o.optBoolean("enabled", true),
            skipped = o.optJSONArray("skipped")
                ?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                ?: emptySet(),
            lastFiredTrigger = o.optLong("lastFiredTrigger", 0),
        )
    }
}

/** Der nächste geplante Wecker einer Regel (zur Anzeige und zum Überspringen). */
data class NextAlarm(
    val ruleId: Long,
    val trigger: Long,
    val eventId: Long,
    val eventBegin: Long,
    val eventTitle: String,
    val allDay: Boolean,
    val instanceKey: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("ruleId", ruleId)
        .put("trigger", trigger)
        .put("eventId", eventId)
        .put("eventBegin", eventBegin)
        .put("eventTitle", eventTitle)
        .put("allDay", allDay)
        .put("instanceKey", instanceKey)

    companion object {
        fun fromJson(o: JSONObject) = NextAlarm(
            ruleId = o.getLong("ruleId"),
            trigger = o.getLong("trigger"),
            eventId = o.optLong("eventId", -1),
            eventBegin = o.optLong("eventBegin", 0),
            eventTitle = o.optString("eventTitle", ""),
            allDay = o.optBoolean("allDay", false),
            instanceKey = o.optString("instanceKey", ""),
        )
    }
}

/** Was ein klingelnder Wecker anzeigt. Wird als Intent-Extras und als JSON (Schlummern) transportiert. */
data class AlarmPayload(
    val ruleId: Long,
    val label: String,
    val eventTitle: String,
    val eventBegin: Long,
    val allDay: Boolean,
    val trigger: Long,
    val snoozed: Boolean = false,
    val snoozeCount: Int = 0,
) {
    fun writeTo(intent: Intent): Intent = intent
        .putExtra(EXTRA_RULE, ruleId)
        .putExtra(EXTRA_LABEL, label)
        .putExtra(EXTRA_TITLE, eventTitle)
        .putExtra(EXTRA_BEGIN, eventBegin)
        .putExtra(EXTRA_ALL_DAY, allDay)
        .putExtra(EXTRA_TRIGGER, trigger)
        .putExtra(EXTRA_SNOOZED, snoozed)
        .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)

    fun toJson(): JSONObject = JSONObject()
        .put("ruleId", ruleId)
        .put("label", label)
        .put("eventTitle", eventTitle)
        .put("eventBegin", eventBegin)
        .put("allDay", allDay)
        .put("trigger", trigger)
        .put("snoozed", snoozed)
        .put("snoozeCount", snoozeCount)

    companion object {
        const val TEST_RULE_ID = -1L

        private const val EXTRA_RULE = "de.terminwecker.extra.RULE"
        private const val EXTRA_LABEL = "de.terminwecker.extra.LABEL"
        private const val EXTRA_TITLE = "de.terminwecker.extra.TITLE"
        private const val EXTRA_BEGIN = "de.terminwecker.extra.BEGIN"
        private const val EXTRA_ALL_DAY = "de.terminwecker.extra.ALL_DAY"
        private const val EXTRA_TRIGGER = "de.terminwecker.extra.TRIGGER"
        private const val EXTRA_SNOOZED = "de.terminwecker.extra.SNOOZED"
        private const val EXTRA_SNOOZE_COUNT = "de.terminwecker.extra.SNOOZE_COUNT"

        fun from(intent: Intent): AlarmPayload? {
            if (!intent.hasExtra(EXTRA_RULE)) return null
            return AlarmPayload(
                ruleId = intent.getLongExtra(EXTRA_RULE, 0),
                label = intent.getStringExtra(EXTRA_LABEL) ?: "",
                eventTitle = intent.getStringExtra(EXTRA_TITLE) ?: "",
                eventBegin = intent.getLongExtra(EXTRA_BEGIN, 0),
                allDay = intent.getBooleanExtra(EXTRA_ALL_DAY, false),
                trigger = intent.getLongExtra(EXTRA_TRIGGER, 0),
                snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false),
                snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0),
            )
        }

        fun fromJson(o: JSONObject) = AlarmPayload(
            ruleId = o.getLong("ruleId"),
            label = o.optString("label", ""),
            eventTitle = o.optString("eventTitle", ""),
            eventBegin = o.optLong("eventBegin", 0),
            allDay = o.optBoolean("allDay", false),
            trigger = o.optLong("trigger", 0),
            snoozed = o.optBoolean("snoozed", false),
            snoozeCount = o.optInt("snoozeCount", 0),
        )
    }
}

data class Snooze(val ruleId: Long, val at: Long, val payload: AlarmPayload)

/** Ansicht im Kalender-Tab; [days] ist die Zahl der Tage pro Seite im Zeitraster. */
enum class CalendarView(val days: Int) {
    LIST(0),
    DAY(1),
    THREE_DAYS(3),
    WEEK(7),
}

data class AppSettings(
    /** null = Standard-Weckton des Systems, [BUILTIN_SOUND] = eingebauter Piepton. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 5,
    val ringMinutes: Int = 10,
    val hiddenCalendars: Set<Long> = emptySet(),
    val calendarView: CalendarView = CalendarView.WEEK,
) {
    companion object {
        const val BUILTIN_SOUND = "builtin"
    }
}
