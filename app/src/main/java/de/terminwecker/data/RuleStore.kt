package de.terminwecker.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Speichert Weckregeln, geplante Wecker, Schlummer-Wecker und Einstellungen in den SharedPreferences. */
class RuleStore private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("terminwecker", Context.MODE_PRIVATE)

    private val _rules = MutableStateFlow(readRules())
    val rules: StateFlow<List<AlarmRule>> = _rules.asStateFlow()

    private val _nextAlarms = MutableStateFlow(readNextAlarms())
    val nextAlarms: StateFlow<Map<Long, NextAlarm>> = _nextAlarms.asStateFlow()

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun rule(id: Long): AlarmRule? = _rules.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(rule: AlarmRule): AlarmRule {
        val saved = if (rule.id > 0) rule else rule.copy(id = newId())
        writeRules(_rules.value.filterNot { it.id == saved.id } + saved)
        return saved
    }

    @Synchronized
    fun update(id: Long, transform: (AlarmRule) -> AlarmRule) {
        writeRules(_rules.value.map { if (it.id == id) transform(it) else it })
    }

    @Synchronized
    fun delete(id: Long) {
        writeRules(_rules.value.filterNot { it.id == id })
        removeSnooze(id)
    }

    @Synchronized
    fun setNextAlarms(next: Map<Long, NextAlarm>) {
        val o = JSONObject()
        next.forEach { (id, n) -> o.put(id.toString(), n.toJson()) }
        prefs.edit().putString(KEY_NEXT, o.toString()).commit()
        _nextAlarms.value = next
    }

    @Synchronized
    fun snoozes(): List<Snooze> {
        val a = JSONArray(prefs.getString(KEY_SNOOZES, "[]"))
        return (0 until a.length()).mapNotNull { i ->
            runCatching {
                val o = a.getJSONObject(i)
                Snooze(o.getLong("ruleId"), o.getLong("at"), AlarmPayload.fromJson(o.getJSONObject("payload")))
            }.getOrNull()
        }
    }

    @Synchronized
    fun putSnooze(snooze: Snooze) {
        writeSnoozes(snoozes().filterNot { it.ruleId == snooze.ruleId } + snooze)
    }

    @Synchronized
    fun removeSnooze(ruleId: Long) {
        val current = snoozes()
        if (current.any { it.ruleId == ruleId }) writeSnoozes(current.filterNot { it.ruleId == ruleId })
    }

    @Synchronized
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString(KEY_SOUND, s.soundUri)
            .putBoolean(KEY_VIBRATE, s.vibrate)
            .putInt(KEY_SNOOZE_MIN, s.snoozeMinutes)
            .putInt(KEY_RING_MIN, s.ringMinutes)
            .putStringSet(KEY_HIDDEN_CALENDARS, s.hiddenCalendars.map { it.toString() }.toSet())
            .putString(KEY_CALENDAR_VIEW, s.calendarView.name)
            .commit()
        _settings.value = s
    }

    private fun newId(): Long {
        val id = prefs.getLong(KEY_NEXT_ID, 1)
        prefs.edit().putLong(KEY_NEXT_ID, id + 1).commit()
        return id
    }

    private fun writeRules(list: List<AlarmRule>) {
        // Übersprungene Vorkommen, die längst vorbei sind, aufräumen.
        val cutoff = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
        val cleaned = list.map { r ->
            val skipped = r.skipped.filter { key -> (key.substringAfter('@').toLongOrNull() ?: 0) >= cutoff }.toSet()
            if (skipped.size == r.skipped.size) r else r.copy(skipped = skipped)
        }.sortedBy { it.id }
        val a = JSONArray()
        cleaned.forEach { a.put(it.toJson()) }
        prefs.edit().putString(KEY_RULES, a.toString()).commit()
        _rules.value = cleaned
    }

    private fun writeSnoozes(list: List<Snooze>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("ruleId", it.ruleId).put("at", it.at).put("payload", it.payload.toJson())) }
        prefs.edit().putString(KEY_SNOOZES, a.toString()).commit()
    }

    private fun readRules(): List<AlarmRule> = runCatching {
        val a = JSONArray(prefs.getString(KEY_RULES, "[]"))
        (0 until a.length()).mapNotNull { runCatching { AlarmRule.fromJson(a.getJSONObject(it)) }.getOrNull() }
    }.getOrDefault(emptyList())

    private fun readNextAlarms(): Map<Long, NextAlarm> = runCatching {
        val o = JSONObject(prefs.getString(KEY_NEXT, "{}") ?: "{}")
        o.keys().asSequence().mapNotNull { k ->
            runCatching { NextAlarm.fromJson(o.getJSONObject(k)) }.getOrNull()?.let { it.ruleId to it }
        }.toMap()
    }.getOrDefault(emptyMap())

    private fun readSettings() = AppSettings(
        soundUri = prefs.getString(KEY_SOUND, null),
        vibrate = prefs.getBoolean(KEY_VIBRATE, true),
        snoozeMinutes = prefs.getInt(KEY_SNOOZE_MIN, 5),
        ringMinutes = prefs.getInt(KEY_RING_MIN, 10),
        hiddenCalendars = prefs.getStringSet(KEY_HIDDEN_CALENDARS, emptySet())
            ?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet(),
        calendarView = prefs.getString(KEY_CALENDAR_VIEW, null)
            ?.let { name -> CalendarView.entries.firstOrNull { it.name == name } } ?: CalendarView.WEEK,
    )

    companion object {
        private const val KEY_RULES = "rules"
        private const val KEY_NEXT = "next_alarms"
        private const val KEY_SNOOZES = "snoozes"
        private const val KEY_NEXT_ID = "next_id"
        private const val KEY_SOUND = "sound_uri"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_SNOOZE_MIN = "snooze_minutes"
        private const val KEY_RING_MIN = "ring_minutes"
        private const val KEY_HIDDEN_CALENDARS = "hidden_calendars"
        private const val KEY_CALENDAR_VIEW = "calendar_view"

        @Volatile
        private var instance: RuleStore? = null

        fun get(context: Context): RuleStore =
            instance ?: synchronized(this) {
                instance ?: RuleStore(context.applicationContext).also { instance = it }
            }
    }
}
