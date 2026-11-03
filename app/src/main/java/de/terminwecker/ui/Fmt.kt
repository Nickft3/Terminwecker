package de.terminwecker.ui

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import de.terminwecker.R
import de.terminwecker.data.AlarmRule
import de.terminwecker.data.EventInstance
import de.terminwecker.data.RuleScope
import de.terminwecker.data.TimingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Texte und Datumsformate in der Sprache der App (Englisch oder Deutsch, je nach Handy bzw.
 * App-Spracheinstellung) und im 12-/24-Stunden-Format des Systems.
 */
object Fmt {
    private lateinit var app: Context

    /** Einmal beim App-Start aufrufen; Fmt nutzt den App-Kontext auch in Diensten und Receivern. */
    fun init(context: Context) {
        app = context.applicationContext
    }

    private val res: Resources get() = app.resources
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun str(@StringRes id: Int, vararg args: Any): String = res.getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int): String = res.getQuantityString(id, count, count)

    /** Formatierer passend zu Sprache und 12-/24-Stunden-Einstellung; neu erzeugt, wenn sich eins davon ändert. */
    private class Formats(val locale: Locale, val is24: Boolean) {
        val time = pattern(if (is24) "Hm" else "hma")
        val hour = pattern(if (is24) "Hm" else "ha")
        val dateShort = pattern("EEEddMMyyyy")
        val dayLong = pattern("EEEEdMMMM")
        val dayHeader = pattern("EEEEdMMMMyyyy")
        val month = pattern("MMMMyyyy")
        val monthShort = pattern("MMM")

        // Android liefert ICU-Muster; Standalone-Formen (L, c) auf die gewöhnlichen abbilden.
        private fun pattern(skeleton: String): DateTimeFormatter = DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, skeleton).replace('L', 'M').replace('c', 'E'),
            locale,
        )
    }

    @Volatile
    private var cached: Formats? = null

    private val f: Formats
        get() {
            val locale = res.configuration.locales[0]
            val is24 = DateFormat.is24HourFormat(app)
            return cached?.takeIf { it.locale == locale && it.is24 == is24 } ?: Formats(locale, is24).also { cached = it }
        }

    val is24Hour: Boolean get() = DateFormat.is24HourFormat(app)

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(f.time)

    fun dayLong(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(f.dayLong)

    fun dateTime(ms: Long): String {
        val z = Instant.ofEpochMilli(ms).atZone(zone)
        return str(R.string.fmt_date_time, z.format(f.dateShort), z.format(f.time))
    }

    fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(f.dateShort)

    fun localDateText(inst: EventInstance): String = inst.localStart().format(f.dateShort)

    /** Termin-Zeitpunkt; ganztägige Termine sind als UTC-Mitternacht gespeichert. */
    fun eventMoment(begin: Long, allDay: Boolean): String =
        if (allDay) str(R.string.fmt_all_day_suffix, Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate().format(f.dateShort))
        else dateTime(begin)

    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val text = date.format(f.dayHeader)
        return when (date) {
            today -> str(R.string.fmt_today, text)
            today.plusDays(1) -> str(R.string.fmt_tomorrow, text)
            else -> text
        }
    }

    /** „Mo“, „Di“ … bzw. ausgeschrieben „Montag“. */
    fun weekday(date: LocalDate, long: Boolean = false): String =
        date.dayOfWeek.getDisplayName(if (long) TextStyle.FULL else TextStyle.SHORT, f.locale).removeSuffix(".")

    /** Monat(e) einer Rasterseite, z. B. „November 2026“ oder „Nov. – Dez. 2026“. */
    fun monthRange(first: LocalDate, last: LocalDate): String = when {
        first.month == last.month && first.year == last.year -> first.format(f.month)
        first.year == last.year -> "${first.format(f.monthShort)} – ${last.format(f.monthShort)} ${last.year}"
        else -> "${first.format(f.monthShort)} ${first.year} – ${last.format(f.monthShort)} ${last.year}"
    }

    /** Beschriftung der Stundenachse, z. B. „14:00“ oder „2 PM“. */
    fun hour(hour: Int): String = LocalTime.of(hour, 0).format(f.hour)

    fun eventTime(inst: EventInstance): String {
        if (inst.allDay) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(inst.localStart().toLocalDate(), inst.localEnd().toLocalDate())
            return if (days > 1) plural(R.plurals.fmt_all_day_days, days.toInt()) else str(R.string.fmt_all_day)
        }
        val start = inst.localStart()
        val end = inst.localEnd()
        return if (start.toLocalDate() == end.toLocalDate()) {
            "${start.format(f.time)} – ${end.format(f.time)}"
        } else {
            "${start.format(f.time)} – ${end.format(f.dateShort)}, ${end.format(f.time)}"
        }
    }

    fun minuteOfDay(m: Int): String = LocalTime.of(m / 60, m % 60).format(f.time)

    /** Uhrzeit mit „Uhr“, wo die Sprache das so schreibt. */
    fun clock(m: Int): String = str(R.string.fmt_clock, minuteOfDay(m))

    fun duration(minutes: Int): String = when {
        minutes == 0 -> str(R.string.fmt_minutes, 0)
        minutes % 1440 == 0 -> plural(R.plurals.fmt_days, minutes / 1440)
        minutes % 60 == 0 -> str(R.string.fmt_hours, minutes / 60)
        minutes > 60 -> str(R.string.fmt_hours_minutes, minutes / 60, minutes % 60)
        else -> str(R.string.fmt_minutes, minutes)
    }

    fun timing(rule: AlarmRule): String = when (rule.timing) {
        TimingMode.RELATIVE ->
            if (rule.minutesBefore == 0) str(R.string.timing_at_start)
            else str(R.string.timing_before_start, duration(rule.minutesBefore))
        TimingMode.FIXED_TIME -> {
            val day = when (rule.dayOffset) {
                0 -> str(R.string.timing_on_day)
                -1 -> str(R.string.timing_day_before)
                else -> plural(R.plurals.timing_days_before, -rule.dayOffset)
            }
            str(R.string.timing_fixed, clock(rule.fixedMinuteOfDay), day)
        }
    }

    fun scope(rule: AlarmRule): String = when (rule.scope) {
        RuleScope.SINGLE ->
            if (rule.recurring) str(R.string.scope_single_recurring, rule.eventTitle, date(rule.instanceBegin))
            else str(R.string.scope_single_event, rule.eventTitle)
        RuleScope.SERIES -> str(R.string.scope_whole_series, rule.eventTitle)
        RuleScope.TITLE -> {
            val text = str(if (rule.titleExact) R.string.scope_title_exact else R.string.scope_title_contains, rule.titleQuery)
            if (rule.calendarId >= 0 && rule.calendarName.isNotBlank()) str(R.string.scope_in_calendar, text, rule.calendarName)
            else text
        }
    }
}
