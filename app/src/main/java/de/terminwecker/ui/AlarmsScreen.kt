package de.terminwecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.terminwecker.data.AlarmRule
import de.terminwecker.data.NextAlarm
import de.terminwecker.data.RuleScope

/** Liste aller Weckregeln mit nächstem Termin, Ein/Aus, Überspringen und Löschen. */
@Composable
fun AlarmsScreen(vm: MainViewModel, onEdit: (AlarmRule) -> Unit, onNewKeyword: () -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val next by vm.nextAlarms.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<AlarmRule?>(null) }
    val sorted = remember(rules, next) {
        rules.sortedWith(compareBy({ !it.enabled }, { next[it.id]?.trigger ?: Long.MAX_VALUE }))
    }

    Box(Modifier.fillMaxSize()) {
        if (rules.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Noch keine Wecker", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                Text(
                    "Tippe im Kalender auf einen Termin, um dafür einen Wecker zu stellen – für den einzelnen Termin, " +
                        "die ganze Serie oder alle Termine mit gleichem Titel.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(sorted, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        next = next[rule.id],
                        onToggle = { vm.setEnabled(rule, it) },
                        onEdit = { onEdit(rule) },
                        onSkip = { vm.skipNext(rule) },
                        onClearSkips = { vm.clearSkips(rule) },
                        onDelete = { confirmDelete = rule },
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewKeyword,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Stichwort-Wecker") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    confirmDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Wecker löschen?") },
            text = { Text("„${ruleTitle(rule)}“ – ${Fmt.scope(rule)}") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteRule(rule)
                    confirmDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun RuleCard(
    rule: AlarmRule,
    next: NextAlarm?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onSkip: () -> Unit,
    onClearSkips: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        ruleTitle(rule),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(Fmt.scope(rule), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fmt.timing(rule), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = rule.enabled, onCheckedChange = onToggle, modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when {
                        !rule.enabled -> Text(
                            "Ausgeschaltet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        next == null && rule.scope == RuleScope.SINGLE -> Text(
                            "Termin ist vorbei oder wurde gelöscht",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        next == null -> Text(
                            "Kein kommender Termin gefunden",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> {
                            Text(
                                "Nächster Wecker: ${Fmt.dateTime(next.trigger)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "für ${next.eventTitle} · ${Fmt.eventMoment(next.eventBegin, next.allDay)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (rule.skipped.isNotEmpty()) {
                        Text(
                            if (rule.skipped.size == 1) "1 Termin wird übersprungen" else "${rule.skipped.size} Termine werden übersprungen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mehr") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Bearbeiten") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menu = false
                                onEdit()
                            },
                        )
                        if (rule.enabled && next != null) {
                            DropdownMenuItem(
                                text = { Text("Nächsten Termin überspringen") },
                                leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onSkip()
                                },
                            )
                        }
                        if (rule.skipped.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Überspringen aufheben") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null) },
                                onClick = {
                                    menu = false
                                    onClearSkips()
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Löschen") },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = {
                                menu = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}
