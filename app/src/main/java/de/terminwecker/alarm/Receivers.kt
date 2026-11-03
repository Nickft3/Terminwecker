package de.terminwecker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.terminwecker.data.AlarmPayload
import de.terminwecker.data.RuleStore

/** Wird vom AlarmManager zur Weckzeit aufgerufen. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_FIRE) return
        val payload = AlarmPayload.from(intent) ?: return
        val store = RuleStore.get(context)

        val shouldRing = when {
            payload.ruleId == AlarmPayload.TEST_RULE_ID -> true
            payload.snoozed -> {
                store.removeSnooze(payload.ruleId)
                store.rule(payload.ruleId) != null
            }
            else -> {
                val rule = store.rule(payload.ruleId)
                if (rule != null) {
                    store.update(rule.id) { it.copy(lastFiredTrigger = maxOf(it.lastFiredTrigger, payload.trigger)) }
                }
                rule != null && rule.enabled
            }
        }
        if (shouldRing) AlarmService.start(context, payload)

        // Gleich den nächsten Termin der Serie / des Titels planen.
        val pending = goAsync()
        AlarmScheduler.rescheduleAsync(context) { pending.finish() }
    }
}

/** Neustart, Zeit-/Zeitzonenwechsel, App-Update, geänderte Wecker-Berechtigung: alles neu planen. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CalendarJobService.ensureScheduled(context)
        val pending = goAsync()
        AlarmScheduler.rescheduleAsync(context) { pending.finish() }
    }
}
