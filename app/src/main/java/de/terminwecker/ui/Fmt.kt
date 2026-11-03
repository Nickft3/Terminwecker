package de.terminwecker.ui

import de.terminwecker.data.AlarmRule
import de.terminwecker.data.EventInstance
import de.terminwecker.data.RuleScope
import de.terminwecker.data.TimingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Deutsche Datums- und Textformatierung. */
object Fmt {
    private val locale = Locale.GERMANY
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateShortFmt = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", locale)
    private val dayLongFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale)
    private val dayHeaderFmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", locale)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val monthShortFmt = DateTimeFormatter.ofPattern("MMM", locale)

    fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(timeFmt)

    fun dayLong(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(dayLongFmt)

    fun dateTime(ms: Long): String {
        val z = Instant.ofEpochMilli(ms).atZone(zone)
        return "${z.format(dateShortFmt)}, ${z.format(timeFmt)} Uhr"
    }

    fun date(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(dateShortFmt)

    fun localDateText(inst: EventInstance): String = inst.localStart().format(dateShortFmt)

    /** Termin-Zeitpunkt; ganztägige Termine sind als UTC-Mitternacht gespeichert. */
    fun eventMoment(begin: Long, allDay: Boolean): String =
        if (allDay) Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate().format(dateShortFmt) + " (ganztägig)"
        else dateTime(begin)

    fun dayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val text = date.format(dayHeaderFmt)
        return when (date) {
            today -> "Heute · $text"
            today.plusDays(1) -> "Morgen · $text"
            else -> text
        }
    }

    /** „Mo“, „Di“ … bzw. ausgeschrieben „Montag“. */
    fun weekday(date: LocalDate, long: Boolean = false): String =
        date.dayOfWeek.getDisplayName(if (long) TextStyle.FULL else TextStyle.SHORT, locale).removeSuffix(".")

    /** Monat(e) einer Rasterseite, z. B. „November 2026“ oder „Nov. – Dez. 2026“. */
    fun monthRange(first: LocalDate, last: LocalDate): String = when {
        first.month == last.month && first.year == last.year -> first.format(monthFmt)
        first.year == last.year -> "${first.format(monthShortFmt)} – ${last.format(monthShortFmt)} ${last.year}"
        else -> "${first.format(monthShortFmt)} ${first.year} – ${last.format(monthShortFmt)} ${last.year}"
    }

    fun eventTime(inst: EventInstance): String {
        if (inst.allDay) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(inst.localStart().toLocalDate(), inst.localEnd().toLocalDate())
            return if (days > 1) "Ganztägig ($days Tage)" else "Ganztägig"
        }
        val start = inst.localStart()
        val end = inst.localEnd()
        return if (start.toLocalDate() == end.toLocalDate()) {
            "${start.format(timeFmt)} – ${end.format(timeFmt)}"
        } else {
            "${start.format(timeFmt)} – ${end.format(dateShortFmt)}, ${end.format(timeFmt)}"
        }
    }

    fun minuteOfDay(m: Int): String = "%02d:%02d".format(m / 60, m % 60)

    fun duration(minutes: Int): String = when {
        minutes == 0 -> "0 Min."
        minutes % 1440 == 0 -> if (minutes == 1440) "1 Tag" else "${minutes / 1440} Tage"
        minutes % 60 == 0 -> "${minutes / 60} Std."
        minutes > 60 -> "${minutes / 60} Std. ${minutes % 60} Min."
        else -> "$minutes Min."
    }

    fun timing(rule: AlarmRule): String = when (rule.timing) {
        TimingMode.RELATIVE ->
            if (rule.minutesBefore == 0) "Zum Terminbeginn" else "${duration(rule.minutesBefore)} vor Beginn"
        TimingMode.FIXED_TIME -> {
            val day = when (rule.dayOffset) {
                0 -> "am Termintag"
                -1 -> "am Vortag"
                else -> "${-rule.dayOffset} Tage vorher"
            }
            "Um ${minuteOfDay(rule.fixedMinuteOfDay)} Uhr $day"
        }
    }

    fun scope(rule: AlarmRule): String = when (rule.scope) {
        RuleScope.SINGLE ->
            if (rule.recurring) "Ein Termin der Serie „${rule.eventTitle}“ (${date(rule.instanceBegin)})"
            else "Einzeltermin „${rule.eventTitle}“"
        RuleScope.SERIES -> "Ganze Serie „${rule.eventTitle}“"
        RuleScope.TITLE -> buildString {
            append(if (rule.titleExact) "Alle Termine mit Titel „${rule.titleQuery}“" else "Alle Termine mit „${rule.titleQuery}“ im Titel")
            if (rule.calendarId >= 0 && rule.calendarName.isNotBlank()) append(" im Kalender ${rule.calendarName}")
        }
    }
}
