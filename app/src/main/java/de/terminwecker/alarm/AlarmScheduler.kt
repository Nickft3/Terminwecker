package de.terminwecker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.WorkerThread
import de.terminwecker.R
import de.terminwecker.data.AlarmPayload
import de.terminwecker.data.CalendarRepository
import de.terminwecker.data.NextAlarm
import de.terminwecker.data.RuleStore
import de.terminwecker.data.Snooze
import de.terminwecker.ui.MainActivity
import java.util.concurrent.Executors

/**
 * Plant für jede aktive Regel genau einen exakten Wecker (den nächsten).
 * Wird nach jedem Klingeln, nach Neustart, bei Kalenderänderungen und beim Öffnen der App neu ausgeführt.
 */
object AlarmScheduler {
    const val ACTION_FIRE = "de.terminwecker.action.FIRE"
    private const val TAG = "AlarmScheduler"
    private const val DAY = 24L * 60 * 60 * 1000
    const val LOOKBACK = 2 * DAY
    const val LOOKAHEAD = 400 * DAY

    private val executor = Executors.newSingleThreadExecutor()

    fun rescheduleAsync(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            try {
                rescheduleNow(app)
            } catch (t: Throwable) {
                Log.e(TAG, "Neuplanung fehlgeschlagen", t)
            } finally {
                onDone?.invoke()
            }
        }
    }

    @WorkerThread
    private fun rescheduleNow(context: Context) {
        val store = RuleStore.get(context)
        val now = System.currentTimeMillis()
        val rules = store.rules.value
        val ruleIds = rules.map { it.id }.toSet()

        // Schlummer-Wecker überleben so auch einen Neustart.
        for (s in store.snoozes()) {
            val stillValid = s.at > now && (s.ruleId in ruleIds || s.ruleId == AlarmPayload.TEST_RULE_ID)
            if (stillValid) set(context, snoozeUri(s.ruleId), s.at, s.payload) else store.removeSnooze(s.ruleId)
        }

        val instances = CalendarRepository.instances(context, now - LOOKBACK, now + LOOKAHEAD)
        if (instances == null && CalendarRepository.hasPermission(context)) {
            // Kalender gerade nicht lesbar (z. B. direkt nach dem Start): bestehende Wecker behalten.
            Log.w(TAG, "Kalender nicht verfügbar, behalte bisherige Wecker")
            return
        }

        val next = HashMap<Long, NextAlarm>()
        for (rule in rules) {
            val occurrence = if (rule.enabled) AlarmMath.upcoming(rule, instances.orEmpty(), now).firstOrNull() else null
            if (occurrence == null) {
                cancel(context, ruleUri(rule.id))
                continue
            }
            val inst = occurrence.instance
            val payload = AlarmPayload(
                ruleId = rule.id,
                label = rule.label.ifBlank { inst.title },
                eventTitle = inst.title,
                eventBegin = inst.begin,
                allDay = inst.allDay,
                trigger = occurrence.trigger,
            )
            set(context, ruleUri(rule.id), occurrence.trigger, payload)
            next[rule.id] = NextAlarm(
                ruleId = rule.id,
                trigger = occurrence.trigger,
                eventId = inst.eventId,
                eventBegin = inst.begin,
                eventTitle = inst.title,
                allDay = inst.allDay,
                instanceKey = inst.key,
            )
        }
        store.nextAlarms.value.keys.filter { it !in ruleIds }.forEach { cancel(context, ruleUri(it)) }
        store.setNextAlarms(next)
    }

    fun snooze(context: Context, payload: AlarmPayload, minutes: Int) {
        val at = System.currentTimeMillis() + minutes * 60_000L
        val p = payload.copy(trigger = at, snoozed = true, snoozeCount = payload.snoozeCount + 1)
        RuleStore.get(context).putSnooze(Snooze(p.ruleId, at, p))
        set(context, snoozeUri(p.ruleId), at, p)
    }

    fun cancelRule(context: Context, ruleId: Long) {
        cancel(context, ruleUri(ruleId))
        cancel(context, snoozeUri(ruleId))
        RuleStore.get(context).removeSnooze(ruleId)
    }

    fun scheduleTest(context: Context, delaySeconds: Int) {
        val at = System.currentTimeMillis() + delaySeconds * 1000L
        val payload = AlarmPayload(
            ruleId = AlarmPayload.TEST_RULE_ID,
            label = context.getString(R.string.test_alarm_label),
            eventTitle = context.getString(R.string.test_alarm_text),
            eventBegin = at,
            allDay = false,
            trigger = at,
        )
        set(context, Uri.parse("terminwecker://test"), at, payload)
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun ruleUri(id: Long) = Uri.parse("terminwecker://rule/$id")
    private fun snoozeUri(id: Long) = Uri.parse("terminwecker://snooze/$id")

    private fun fireIntent(context: Context, uri: Uri) =
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).setData(uri)

    private fun set(context: Context, uri: Uri, at: Long, payload: AlarmPayload) {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context, 0, payload.writeTo(fireIntent(context, uri)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (canScheduleExact(context)) {
            try {
                // setAlarmClock: exakt, auch im Doze-Modus, und mit Wecker-Symbol in der Statusleiste.
                val show = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ALARMS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), operation)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exakter Wecker nicht erlaubt, nutze ungenauen Wecker", e)
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
    }

    private fun cancel(context: Context, uri: Uri) {
        val pi = PendingIntent.getBroadcast(
            context, 0, fireIntent(context, uri),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        context.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }
}
