package de.terminwecker.ui

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.terminwecker.alarm.AlarmMath
import de.terminwecker.alarm.AlarmScheduler
import de.terminwecker.data.AlarmRule
import de.terminwecker.data.AppSettings
import de.terminwecker.data.CalendarInfo
import de.terminwecker.data.CalendarRepository
import de.terminwecker.data.EventInstance
import de.terminwecker.data.RuleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = RuleStore.get(app)

    val rules = store.rules
    val nextAlarms = store.nextAlarms
    val settings: StateFlow<AppSettings> = store.settings

    private val _instances = MutableStateFlow<List<EventInstance>>(emptyList())
    val instances: StateFlow<List<EventInstance>> = _instances.asStateFlow()

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _status = MutableStateFlow(PermissionStatus.read(app))
    val status: StateFlow<PermissionStatus> = _status.asStateFlow()

    private var observerRegistered = false
    private var reloadJob: Job? = null
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload(debounce = true)
    }

    init {
        refresh()
    }

    /** Berechtigungen neu prüfen, Kalender neu laden, Wecker neu planen (z. B. beim Zurückkehren in die App). */
    fun refresh() {
        val app = getApplication<Application>()
        _status.value = PermissionStatus.read(app)
        if (_status.value.calendar && !observerRegistered) {
            app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observerRegistered = true
        }
        reload()
        AlarmScheduler.rescheduleAsync(app)
    }

    fun reload(debounce: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (debounce) delay(1_500)
            _loading.value = true
            val app = getApplication<Application>()
            val (cals, inst) = withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                // Für das Zeitraster auch die vergangenen Wochen laden, die man zurückblättern kann.
                CalendarRepository.calendars(app) to
                    CalendarRepository.instances(app, now - DISPLAY_LOOKBACK, now + AlarmScheduler.LOOKAHEAD)
            }
            _calendars.value = cals
            if (inst != null) _instances.value = inst
            _loading.value = false
        }
    }

    override fun onCleared() {
        if (observerRegistered) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    fun preview(rule: AlarmRule, limit: Int = 3): List<AlarmMath.Occurrence> =
        AlarmMath.upcoming(rule, _instances.value, System.currentTimeMillis(), limit)

    fun saveRule(rule: AlarmRule) {
        store.upsert(rule)
        AlarmScheduler.rescheduleAsync(getApplication())
    }

    fun deleteRule(rule: AlarmRule) {
        store.delete(rule.id)
        AlarmScheduler.cancelRule(getApplication(), rule.id)
        AlarmScheduler.rescheduleAsync(getApplication())
    }

    fun setEnabled(rule: AlarmRule, enabled: Boolean) {
        store.update(rule.id) { it.copy(enabled = enabled) }
        if (!enabled) AlarmScheduler.cancelRule(getApplication(), rule.id)
        AlarmScheduler.rescheduleAsync(getApplication())
    }

    fun skipNext(rule: AlarmRule) {
        val next = nextAlarms.value[rule.id] ?: return
        store.update(rule.id) { it.copy(skipped = it.skipped + next.instanceKey) }
        AlarmScheduler.rescheduleAsync(getApplication())
    }

    fun clearSkips(rule: AlarmRule) {
        store.update(rule.id) { it.copy(skipped = emptySet()) }
        AlarmScheduler.rescheduleAsync(getApplication())
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = store.updateSettings(transform)

    fun testAlarm(delaySeconds: Int) = AlarmScheduler.scheduleTest(getApplication(), delaySeconds)

    private companion object {
        val DISPLAY_LOOKBACK = TimeUnit.DAYS.toMillis(GRID_LOOKBACK_DAYS + 7)
    }
}
