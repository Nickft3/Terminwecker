package de.terminwecker.alarm

import de.terminwecker.data.AlarmRule
import de.terminwecker.data.EventInstance
import de.terminwecker.data.RuleScope
import de.terminwecker.data.TimingMode
import java.time.ZoneId

/** Reine Rechenlogik: welche Termine zu einer Regel passen und wann der Wecker dafür klingelt. */
object AlarmMath {

    data class Occurrence(val instance: EventInstance, val trigger: Long)

    fun matches(rule: AlarmRule, inst: EventInstance): Boolean = when (rule.scope) {
        RuleScope.SINGLE ->
            if (rule.recurring) {
                // Ein Vorkommen einer Serie; wurde es verschoben, ist es eine Ausnahme mit Verweis auf die Serie.
                (inst.eventId == rule.eventId && inst.begin == rule.instanceBegin) ||
                    (inst.originalId == rule.eventId && inst.originalInstanceTime == rule.instanceBegin)
            } else {
                // Einzeltermin: über die Id, so folgt der Wecker auch einer Verschiebung.
                inst.eventId == rule.eventId
            }

        RuleScope.SERIES -> inst.eventId == rule.eventId || inst.originalId == rule.eventId

        RuleScope.TITLE ->
            (rule.calendarId < 0 || inst.calendarId == rule.calendarId) && titleMatches(rule, inst.title)
    }

    fun titleMatches(rule: AlarmRule, title: String): Boolean {
        val query = rule.titleQuery.trim()
        if (query.isEmpty()) return false
        val t = title.trim()
        return if (rule.titleExact) t.equals(query, ignoreCase = true) else t.contains(query, ignoreCase = true)
    }

    fun triggerFor(rule: AlarmRule, inst: EventInstance, zone: ZoneId = ZoneId.systemDefault()): Long {
        val start = inst.localStart(zone)
        return when (rule.timing) {
            TimingMode.RELATIVE -> start.toInstant().toEpochMilli() - rule.minutesBefore * 60_000L
            TimingMode.FIXED_TIME -> start.toLocalDate()
                .plusDays(rule.dayOffset.toLong())
                .atTime(rule.fixedMinuteOfDay / 60, rule.fixedMinuteOfDay % 60)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }
    }

    /** Die nächsten Wecker einer Regel nach [now], ohne bereits ausgelöste und übersprungene. */
    fun upcoming(
        rule: AlarmRule,
        instances: List<EventInstance>,
        now: Long,
        limit: Int = 1,
        includeSkipped: Boolean = false,
    ): List<Occurrence> {
        val after = maxOf(now, rule.lastFiredTrigger)
        return instances.asSequence()
            .filter { matches(rule, it) }
            .filter { includeSkipped || it.key !in rule.skipped }
            .map { Occurrence(it, triggerFor(rule, it)) }
            .filter { it.trigger > after }
            .sortedBy { it.trigger }
            .distinctBy { it.trigger }
            .take(limit)
            .toList()
    }
}
