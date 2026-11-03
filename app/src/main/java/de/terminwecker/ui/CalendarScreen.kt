package de.terminwecker.ui

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.terminwecker.R
import de.terminwecker.alarm.AlarmMath
import de.terminwecker.data.AppSettings
import de.terminwecker.data.CalendarInfo
import de.terminwecker.data.CalendarView
import de.terminwecker.data.EventInstance
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private sealed interface CalRow {
    data class Header(val date: LocalDate) : CalRow
    data class Event(val inst: EventInstance) : CalRow
}

/**
 * Gespiegelter Kalender, wahlweise als Zeitraster (Tag/3 Tage/Woche) oder als Terminliste mit Suche.
 * Tippen auf einen Termin öffnet den Wecker-Editor.
 */
@Composable
fun CalendarScreen(vm: MainViewModel, onEventClick: (EventInstance) -> Unit) {
    val status by vm.status.collectAsStateWithLifecycle()
    val requestCalendar = rememberPermissionRequest(Manifest.permission.READ_CALENDAR, vm)

    if (!status.calendar) {
        PermissionHint(
            text = stringResource(R.string.calendar_permission_text),
            button = stringResource(R.string.calendar_permission_button),
            onClick = requestCalendar,
        )
        return
    }

    val instances by vm.instances.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsState()
    val now by rememberMinuteClock()
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

    var showFilter by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var focusDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }

    val visible = remember(instances, settings.hiddenCalendars) {
        instances.filter { it.calendarId !in settings.hiddenCalendars }.distinctBy { it.key }
    }
    val alarmKeys: Set<String> = remember(rules, instances) {
        val active = rules.filter { it.enabled }
        if (active.isEmpty()) emptySet()
        else instances.filter { inst -> active.any { AlarmMath.matches(it, inst) } }.map { it.key }.toSet()
    }

    if (settings.calendarView == CalendarView.LIST) {
        CalendarList(
            events = visible,
            alarmKeys = alarmKeys,
            loading = loading,
            today = today,
            jumpTo = LocalDate.ofEpochDay(focusDay),
            onPickDate = { showDatePicker = true },
            onFilter = { showFilter = true },
            onEventClick = onEventClick,
        )
    } else {
        CalendarGrid(
            view = settings.calendarView,
            events = visible,
            alarmKeys = alarmKeys,
            now = now,
            loading = loading,
            focus = LocalDate.ofEpochDay(focusDay),
            onFocusChange = { focusDay = it.toEpochDay() },
            onDayClick = { day ->
                focusDay = day.toEpochDay()
                vm.updateSettings { it.copy(calendarView = CalendarView.DAY) }
            },
            onEventClick = onEventClick,
            onPickDate = { showDatePicker = true },
            onFilter = { showFilter = true },
        )
    }

    if (showDatePicker) {
        JumpToDateDialog(
            initial = LocalDate.ofEpochDay(focusDay),
            onDismiss = { showDatePicker = false },
            onPick = { focusDay = it.toEpochDay() },
        )
    }
    if (showFilter) {
        CalendarFilterDialog(calendars, settings, vm, onDismiss = { showFilter = false })
    }
}

/** Aktuelle Zeit, die sich zu jeder vollen Minute aktualisiert (Jetzt-Linie, „heute“). */
@Composable
private fun rememberMinuteClock(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(60_000 - System.currentTimeMillis() % 60_000)
        value = System.currentTimeMillis()
    }
}

/** Umschalter für die Ansicht, sitzt in der oberen App-Leiste. */
@Composable
internal fun CalendarViewMenu(current: CalendarView, onSelect: (CalendarView) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(viewIcon(current), contentDescription = stringResource(R.string.view_current, stringResource(viewName(current))))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CalendarView.entries.forEach { view ->
                DropdownMenuItem(
                    text = { Text(stringResource(viewName(view))) },
                    leadingIcon = { Icon(viewIcon(view), contentDescription = null) },
                    trailingIcon = { if (view == current) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onSelect(view)
                    },
                )
            }
        }
    }
}

@StringRes
private fun viewName(view: CalendarView): Int = when (view) {
    CalendarView.LIST -> R.string.view_list
    CalendarView.DAY -> R.string.view_day
    CalendarView.THREE_DAYS -> R.string.view_three_days
    CalendarView.WEEK -> R.string.view_week
}

private fun viewIcon(view: CalendarView): ImageVector = when (view) {
    CalendarView.LIST -> Icons.Filled.ViewAgenda
    CalendarView.DAY -> Icons.Filled.ViewDay
    CalendarView.THREE_DAYS -> Icons.Filled.ViewColumn
    CalendarView.WEEK -> Icons.Filled.ViewWeek
}

/** Kommende Termine als Liste mit Tagesüberschriften und Suche nach Titel oder Ort. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CalendarList(
    events: List<EventInstance>,
    alarmKeys: Set<String>,
    loading: Boolean,
    today: LocalDate,
    jumpTo: LocalDate,
    onPickDate: () -> Unit,
    onFilter: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val rows: List<CalRow> = remember(events, query, today) {
        val q = query.trim()
        events.asSequence()
            .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.location.contains(q, ignoreCase = true) }
            .filter { !it.localDate().isBefore(today) }
            .groupBy { it.localDate() }
            .entries
            .sortedBy { it.key.toEpochDay() }
            .flatMap { (date, list) ->
                listOf<CalRow>(CalRow.Header(date)) +
                    list.sortedWith(compareBy({ !it.allDay }, { it.begin })).map { CalRow.Event(it) }
            }
    }

    // Nach „Zu Datum springen“ zum ersten Tag ab dem gewählten Datum scrollen.
    var lastJump by rememberSaveable { mutableLongStateOf(jumpTo.toEpochDay()) }
    LaunchedEffect(jumpTo) {
        if (jumpTo.toEpochDay() == lastJump) return@LaunchedEffect
        lastJump = jumpTo.toEpochDay()
        val index = rows.indexOfFirst { it is CalRow.Header && !it.date.isBefore(jumpTo) }
        listState.scrollToItem(if (index >= 0) index else (rows.size - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
            )
            IconButton(onClick = onPickDate) {
                Icon(Icons.Filled.Today, contentDescription = stringResource(R.string.jump_to_date))
            }
            IconButton(onClick = onFilter) {
                Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.choose_calendars))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (rows.isEmpty() && !loading) {
            Text(
                if (query.isBlank()) stringResource(R.string.no_upcoming_events)
                else stringResource(R.string.no_events_for_query, query.trim()),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            rows.forEach { row ->
                when (row) {
                    is CalRow.Header -> stickyHeader(key = "h${row.date}") { DayHeader(row.date, today) }
                    is CalRow.Event -> item(key = row.inst.key) {
                        EventRow(row.inst, row.inst.key in alarmKeys) { onEventClick(row.inst) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JumpToDateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text(stringResource(R.string.action_jump)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun CalendarFilterDialog(
    calendars: List<CalendarInfo>,
    settings: AppSettings,
    vm: MainViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
        title = { Text(stringResource(R.string.shown_calendars)) },
        text = {
            LazyColumn {
                items(calendars, key = { it.id }) { cal ->
                    val shown = cal.id !in settings.hiddenCalendars
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.updateSettings { s ->
                                    s.copy(hiddenCalendars = if (shown) s.hiddenCalendars + cal.id else s.hiddenCalendars - cal.id)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = shown, onCheckedChange = null)
                        ColorDot(cal.color, Modifier.padding(start = 12.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(cal.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                cal.account,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun DayHeader(date: LocalDate, today: LocalDate) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Text(
            Fmt.dayHeader(date, today),
            style = MaterialTheme.typography.titleSmall,
            color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EventRow(inst: EventInstance, hasAlarm: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                Modifier
                    .width(5.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(calendarColor(inst.color)),
            )
        },
        headlineContent = {
            Text(inst.title.ifBlank { stringResource(R.string.untitled) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                buildString {
                    append(Fmt.eventTime(inst))
                    if (inst.location.isNotBlank()) append(" · ").append(inst.location)
                    append(" · ").append(inst.calendarName)
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (inst.isPartOfSeries) {
                    Icon(
                        Icons.Filled.Repeat,
                        contentDescription = stringResource(R.string.series),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasAlarm) {
                    Icon(
                        Icons.Filled.Alarm,
                        contentDescription = stringResource(R.string.alarm_set),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Outlined.AlarmAdd,
                        contentDescription = stringResource(R.string.set_alarm),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        },
    )
}
