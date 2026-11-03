package de.terminwecker.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.terminwecker.data.AppSettings

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val status by vm.status.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    val requestCalendar = rememberPermissionRequest(Manifest.permission.READ_CALENDAR, vm)
    val requestNotifications: () -> Unit =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionRequest(Manifest.permission.POST_NOTIFICATIONS, vm) {
                // Erlaubt, aber in den Systemeinstellungen abgeschaltet: dorthin weiterleiten.
                if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    SystemSettings.open(context, SystemSettings.notifications(context))
                }
            }
        } else {
            { SystemSettings.open(context, SystemSettings.notifications(context)) }
        }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val uri = res.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            vm.updateSettings { it.copy(soundUri = uri?.toString()) }
        }
    }
    val soundTitle = remember(settings.soundUri) { soundTitle(context, settings.soundUri) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SectionLabel("Berechtigungen & Zuverlässigkeit", Modifier.padding(horizontal = 16.dp))
        StatusItem("Kalender lesen", "Um deine Termine anzuzeigen und Serien zu verfolgen", status.calendar, requestCalendar)
        StatusItem("Benachrichtigungen", "Damit der klingelnde Wecker angezeigt wird", status.notifications, requestNotifications)
        StatusItem("Exakte Wecker", "Damit der Wecker auf die Minute genau klingelt", status.exactAlarms) {
            SystemSettings.open(context, SystemSettings.exactAlarms(context))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            StatusItem("Vollbild-Wecker", "Wecker über dem Sperrbildschirm anzeigen", status.fullScreen) {
                SystemSettings.open(context, SystemSettings.fullScreen(context))
            }
        }
        StatusItem(
            "Akku-Optimierung aus",
            "Verhindert, dass Android die App im Hintergrund beendet",
            status.batteryUnrestricted,
        ) { SystemSettings.open(context, SystemSettings.battery(context)) }
        Text(
            "Tipp: Bei Samsung, Xiaomi, Huawei, OnePlus & Co. zusätzlich in den App-Einstellungen unter Akku " +
                "„Nicht eingeschränkt“ bzw. „Autostart“ erlauben. Anleitungen je Hersteller: dontkillmyapp.com",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        TextButton(
            onClick = { SystemSettings.open(context, SystemSettings.appDetails(context)) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text("App-Einstellungen öffnen") }

        SectionLabel("Wecker", Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text("Weckton") },
            supportingContent = { Text(soundTitle) },
            leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
            modifier = Modifier.clickable { ringtonePicker.launch(ringtonePickerIntent(settings.soundUri)) },
        )
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = settings.soundUri == null,
                onClick = { vm.updateSettings { it.copy(soundUri = null) } },
                label = { Text("System-Weckton") },
            )
            FilterChip(
                selected = settings.soundUri == AppSettings.BUILTIN_SOUND,
                onClick = { vm.updateSettings { it.copy(soundUri = AppSettings.BUILTIN_SOUND) } },
                label = { Text("Eingebauter Piepton") },
            )
            FilterChip(
                selected = settings.soundUri != null && settings.soundUri != AppSettings.BUILTIN_SOUND,
                onClick = { ringtonePicker.launch(ringtonePickerIntent(settings.soundUri)) },
                label = { Text("Anderer Ton …") },
            )
        }
        ListItem(
            headlineContent = { Text("Vibration") },
            leadingContent = { Icon(Icons.Filled.Vibration, contentDescription = null) },
            trailingContent = {
                Switch(checked = settings.vibrate, onCheckedChange = { v -> vm.updateSettings { it.copy(vibrate = v) } })
            },
        )
        ChoiceRow("Schlummerdauer", listOf(5, 10, 15, 30), settings.snoozeMinutes) { m ->
            vm.updateSettings { it.copy(snoozeMinutes = m) }
        }
        ChoiceRow("Automatisch aus nach", listOf(1, 3, 5, 10, 30), settings.ringMinutes) { m ->
            vm.updateSettings { it.copy(ringMinutes = m) }
        }

        SectionLabel("Testen", Modifier.padding(horizontal = 16.dp))
        Text(
            "Stellt einen Testwecker in 10 Sekunden. Sperr ruhig den Bildschirm, um den Vollbild-Wecker zu prüfen.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(
            onClick = {
                vm.testAlarm(10)
                Toast.makeText(context, "Testwecker klingelt in 10 Sekunden", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Testwecker in 10 Sekunden")
        }

        SectionLabel("Info", Modifier.padding(horizontal = 16.dp))
        Text(
            "Terminwecker ${versionName(context)} · liest deinen Kalender nur lokal auf dem Gerät – " +
                "ohne Internet und ohne Google-Login. Wecker werden nach Neustart, Zeitumstellung und " +
                "Kalenderänderungen automatisch neu geplant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun StatusItem(title: String, description: String, ok: Boolean, onFix: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = if (ok) "OK" else "Fehlt",
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            if (!ok) TextButton(onClick = onFix) { Text("Beheben") }
        },
    )
}

@Composable
private fun ChoiceRow(title: String, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { m ->
                FilterChip(selected = m == selected, onClick = { onSelect(m) }, label = { Text("$m Min.") })
            }
        }
    }
}

private fun ringtonePickerIntent(current: String?): Intent {
    val existing = current?.takeIf { it != AppSettings.BUILTIN_SOUND }?.let(Uri::parse)
        ?: Settings.System.DEFAULT_ALARM_ALERT_URI
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Weckton")
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
}

private fun soundTitle(context: Context, uri: String?): String = when (uri) {
    null -> "System-Weckton (Standard)"
    AppSettings.BUILTIN_SOUND -> "Eingebauter Piepton"
    else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
        ?: "Ausgewählter Ton"
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
