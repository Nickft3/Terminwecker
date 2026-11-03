package de.terminwecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.terminwecker.R
import de.terminwecker.alarm.AlarmMath
import de.terminwecker.data.AlarmRule
import de.terminwecker.data.CalendarInfo
import de.terminwecker.data.EventInstance
import de.terminwecker.data.RuleScope
import de.terminwecker.data.TimingMode

private val MINUTE_PRESETS = listOf(0, 5, 10, 15, 30, 60, 120, 1440)
private val DAY_OFFSETS = listOf(0 to R.string.offset_same_day, -1 to R.string.offset_day_before, -2 to R.string.offset_two_days)

/** Vollbild-Dialog zum Anlegen oder Bearbeiten eines Weckers. */
@Composable
fun RuleEditorDialog(
    vm: MainViewModel,
    request: EditorRequest,
    onEditRule: (AlarmRule) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        key(request) {
            EditorContent(vm, request, onEditRule, onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorContent(
    vm: MainViewModel,
    request: EditorRequest,
    onEditRule: (AlarmRule) -> Unit,
    onDismiss: () -> Unit,
) {
    val existing = (request as? EditorRequest.ForRule)?.rule
    val inst = (request as? EditorRequest.ForInstance)?.instance
    val rules by vm.rules.collectAsStateWithLifecycle()
    val instances by vm.instances.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()

    var scope by remember {
        mutableStateOf(
            existing?.scope ?: when {
                inst == null -> RuleScope.TITLE
                inst.isPartOfSeries -> RuleScope.SERIES
                else -> RuleScope.SINGLE
            },
        )
    }
    var titleQuery by remember { mutableStateOf(existing?.titleQuery?.takeIf { it.isNotBlank() } ?: inst?.title ?: "") }
    var titleExact by remember { mutableStateOf(existing?.titleExact ?: (inst != null)) }
    var titleCalendarId by remember { mutableLongStateOf(existing?.calendarId ?: inst?.calendarId ?: -1L) }
    var timing by remember {
        mutableStateOf(existing?.timing ?: if (inst?.allDay == true) TimingMode.FIXED_TIME else TimingMode.RELATIVE)
    }
    var minutesText by remember { mutableStateOf((existing?.minutesBefore ?: 15).toString()) }
    var fixedMinute by remember { mutableIntStateOf(existing?.fixedMinuteOfDay ?: defaultFixedMinute(inst)) }
    var dayOffset by remember { mutableIntStateOf(existing?.dayOffset ?: 0) }
    var labelText by remember { mutableStateOf(existing?.label ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }

    val minutes = minutesText.trim().toIntOrNull()?.takeIf { it in 0..(60 * 24 * 60) }
    val calendarName = calendars.firstOrNull { it.id == titleCalendarId }?.name ?: existing?.calendarName.orEmpty()

    val draft: AlarmRule? = run {
        if (timing == TimingMode.RELATIVE && minutes == null) return@run null
        if (scope == RuleScope.TITLE && titleQuery.isBlank()) return@run null
        if (existing == null && inst == null && scope != RuleScope.TITLE) return@run null
        val base = existing ?: AlarmRule(scope = scope)
        base.copy(
            scope = scope,
            eventId = existing?.eventId ?: when (scope) {
                RuleScope.SERIES -> inst?.seriesId ?: -1
                else -> inst?.eventId ?: -1
            },
            instanceBegin = existing?.instanceBegin ?: inst?.begin ?: 0,
            recurring = existing?.recurring ?: (inst?.recurring == true),
            eventTitle = existing?.eventTitle ?: inst?.title ?: titleQuery.trim(),
            titleQuery = if (scope == RuleScope.TITLE) titleQuery.trim() else "",
            titleExact = titleExact,
            calendarId = if (scope == RuleScope.TITLE) titleCalendarId else -1,
            calendarName = if (scope == RuleScope.TITLE && titleCalendarId >= 0) calendarName else "",
            timing = timing,
            minutesBefore = minutes ?: base.minutesBefore,
            fixedMinuteOfDay = fixedMinute,
            dayOffset = dayOffset,
            label = labelText.trim(),
        )
    }
    val preview = remember(draft, instances) { draft?.let { vm.preview(it) } ?: emptyList() }
    val existingForEvent = remember(rules, inst) {
        inst?.let { i -> rules.filter { AlarmMath.matches(it, i) } } ?: emptyList()
    }
    val fallbackLabel = stringResource(R.string.alarm_default_label)
    val defaultLabel = when {
        scope == RuleScope.TITLE -> titleQuery.trim().ifBlank { fallbackLabel }
        else -> existing?.eventTitle ?: inst?.title ?: fallbackLabel
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(if (existing != null) R.string.edit_alarm else R.string.set_alarm)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    TextButton(
                        enabled = draft != null,
                        onClick = {
                            draft?.let(vm::saveRule)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                },
                windowInsets = WindowInsets(0),
            )
            Column(
                Modifier
                    .weight(1f)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (inst != null) EventInfoCard(inst)
                if (existing != null) {
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Text(
                            Fmt.scope(existing),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (existingForEvent.isNotEmpty()) {
                    SectionLabel(stringResource(R.string.already_set))
                    existingForEvent.forEach { r ->
                        ExistingRuleRow(r, onEdit = { onEditRule(r) }, onDelete = { vm.deleteRule(r) })
                    }
                    SectionLabel(stringResource(R.string.add_another))
                }

                if (existing == null) {
                    SectionLabel(stringResource(R.string.applies_to))
                    if (inst != null) {
                        RadioRow(
                            selected = scope == RuleScope.SINGLE,
                            title = stringResource(R.string.scope_single),
                            subtitle = Fmt.eventMoment(inst.begin, inst.allDay),
                        ) { scope = RuleScope.SINGLE }
                        if (inst.isPartOfSeries) {
                            RadioRow(
                                selected = scope == RuleScope.SERIES,
                                title = stringResource(R.string.scope_series),
                                subtitle = stringResource(R.string.scope_series_hint),
                            ) { scope = RuleScope.SERIES }
                        }
                    }
                    RadioRow(
                        selected = scope == RuleScope.TITLE,
                        title = stringResource(R.string.scope_title),
                        subtitle = stringResource(R.string.scope_title_hint),
                    ) { scope = RuleScope.TITLE }
                }

                if (scope == RuleScope.TITLE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = titleQuery,
                        onValueChange = { titleQuery = it },
                        label = { Text(stringResource(R.string.title_keyword)) },
                        singleLine = true,
                        isError = titleQuery.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CheckRow(
                        checked = titleExact,
                        title = stringResource(R.string.title_exact),
                        subtitle = stringResource(R.string.title_exact_hint),
                    ) { titleExact = it }
                    CalendarDropdown(calendars, titleCalendarId) { titleCalendarId = it }
                }

                SectionLabel(stringResource(R.string.when_ring))
                RadioRow(selected = timing == TimingMode.RELATIVE, title = stringResource(R.string.timing_before), subtitle = null) {
                    timing = TimingMode.RELATIVE
                }
                if (timing == TimingMode.RELATIVE) {
                    Row(
                        Modifier
                            .padding(start = 40.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MINUTE_PRESETS.forEach { m ->
                            FilterChip(
                                selected = minutes == m,
                                onClick = { minutesText = m.toString() },
                                label = { Text(if (m == 0) stringResource(R.string.at_start) else Fmt.duration(m)) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.minutes_before)) },
                        singleLine = true,
                        isError = minutes == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .padding(start = 40.dp, top = 4.dp)
                            .width(200.dp),
                    )
                }
                RadioRow(selected = timing == TimingMode.FIXED_TIME, title = stringResource(R.string.timing_fixed_label), subtitle = null) {
                    timing = TimingMode.FIXED_TIME
                }
                if (timing == TimingMode.FIXED_TIME) {
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.padding(start = 40.dp)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(Fmt.clock(fixedMinute))
                    }
                    Row(
                        Modifier
                            .padding(start = 40.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DAY_OFFSETS.forEach { (d, text) ->
                            FilterChip(selected = dayOffset == d, onClick = { dayOffset = d }, label = { Text(stringResource(text)) })
                        }
                    }
                }

                SectionLabel(stringResource(R.string.label_section))
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text(stringResource(R.string.label_hint)) },
                    placeholder = { Text(defaultLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionLabel(stringResource(R.string.preview))
                when {
                    draft == null -> Text(
                        stringResource(R.string.incomplete),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    preview.isEmpty() -> Text(
                        stringResource(R.string.preview_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> preview.forEach { occ ->
                        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(Fmt.dateTime(occ.trigger), fontWeight = FontWeight.Medium)
                                Text(
                                    "${occ.instance.title} · ${Fmt.eventMoment(occ.instance.begin, occ.instance.allDay)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (scope == RuleScope.SERIES) {
                    Text(
                        stringResource(R.string.series_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        if (showTimePicker) {
            val state = rememberTimePickerState(
                initialHour = fixedMinute / 60,
                initialMinute = fixedMinute % 60,
                is24Hour = Fmt.is24Hour,
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        fixedMinute = state.hour * 60 + state.minute
                        showTimePicker = false
                    }) { Text(stringResource(R.string.action_ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.action_cancel)) }
                },
                text = { TimePicker(state = state) },
            )
        }
    }
}

private fun defaultFixedMinute(inst: EventInstance?): Int {
    if (inst == null || inst.allDay) return 8 * 60
    val start = inst.localStart()
    return (start.hour * 60 + start.minute - 15).coerceAtLeast(0)
}

@Composable
private fun EventInfoCard(inst: EventInstance) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(5.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(calendarColor(inst.color)),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(inst.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleMedium)
                Text(
                    Fmt.localDateText(inst) + " · " + Fmt.eventTime(inst),
                    style = MaterialTheme.typography.bodyMedium,
                )
                val seriesNote = if (inst.isPartOfSeries) " · " + stringResource(R.string.part_of_series) else ""
                Text(
                    inst.calendarName + seriesNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExistingRuleRow(rule: AlarmRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (rule.enabled) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
            contentDescription = null,
            tint = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(ruleTitle(rule), style = MaterialTheme.typography.bodyLarge)
            Text(
                "${Fmt.scope(rule)} · ${Fmt.timing(rule)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete)) }
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, title: String, subtitle: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarDropdown(calendars: List<CalendarInfo>, selected: Long, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val allCalendars = stringResource(R.string.all_calendars)
    val name = if (selected < 0) allCalendars
    else calendars.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.calendar)
    Box(Modifier.padding(top = 4.dp)) {
        OutlinedButton(onClick = { open = true }) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.calendar_selected, name))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(allCalendars) },
                onClick = {
                    onSelect(-1)
                    open = false
                },
            )
            calendars.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c.name) },
                    leadingIcon = { ColorDot(c.color) },
                    onClick = {
                        onSelect(c.id)
                        open = false
                    },
                )
            }
        }
    }
}
