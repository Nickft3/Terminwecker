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
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.terminwecker.R
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
        SectionLabel(stringResource(R.string.settings_permissions), Modifier.padding(horizontal = 16.dp))
        StatusItem(R.string.perm_calendar, R.string.perm_calendar_desc, status.calendar, requestCalendar)
        StatusItem(R.string.perm_notifications, R.string.perm_notifications_desc, status.notifications, requestNotifications)
        StatusItem(R.string.perm_exact, R.string.perm_exact_desc, status.exactAlarms) {
            SystemSettings.open(context, SystemSettings.exactAlarms(context))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            StatusItem(R.string.perm_fullscreen, R.string.perm_fullscreen_desc, status.fullScreen) {
                SystemSettings.open(context, SystemSettings.fullScreen(context))
            }
        }
        StatusItem(R.string.perm_battery, R.string.perm_battery_desc, status.batteryUnrestricted) {
            SystemSettings.open(context, SystemSettings.battery(context))
        }
        Text(
            stringResource(R.string.battery_tip),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        TextButton(
            onClick = { SystemSettings.open(context, SystemSettings.appDetails(context)) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.open_app_settings)) }

        SectionLabel(stringResource(R.string.section_alarm), Modifier.padding(horizontal = 16.dp))
        ListItem(
            headlineContent = { Text(stringResource(R.string.alarm_sound)) },
            supportingContent = { Text(soundTitle) },
            leadingContent = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
            modifier = Modifier.clickable { ringtonePicker.launch(ringtonePickerIntent(context, settings.soundUri)) },
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
                label = { Text(stringResource(R.string.sound_system)) },
            )
            FilterChip(
                selected = settings.soundUri == AppSettings.BUILTIN_SOUND,
                onClick = { vm.updateSettings { it.copy(soundUri = AppSettings.BUILTIN_SOUND) } },
                label = { Text(stringResource(R.string.sound_builtin)) },
            )
            FilterChip(
                selected = settings.soundUri != null && settings.soundUri != AppSettings.BUILTIN_SOUND,
                onClick = { ringtonePicker.launch(ringtonePickerIntent(context, settings.soundUri)) },
                label = { Text(stringResource(R.string.sound_other)) },
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.vibration)) },
            leadingContent = { Icon(Icons.Filled.Vibration, contentDescription = null) },
            trailingContent = {
                Switch(checked = settings.vibrate, onCheckedChange = { v -> vm.updateSettings { it.copy(vibrate = v) } })
            },
        )
        ChoiceRow(stringResource(R.string.snooze_duration), listOf(5, 10, 15, 30), settings.snoozeMinutes) { m ->
            vm.updateSettings { it.copy(snoozeMinutes = m) }
        }
        ChoiceRow(stringResource(R.string.auto_off_after), listOf(1, 3, 5, 10, 30), settings.ringMinutes) { m ->
            vm.updateSettings { it.copy(ringMinutes = m) }
        }

        SectionLabel(stringResource(R.string.section_test), Modifier.padding(horizontal = 16.dp))
        Text(
            stringResource(R.string.test_text),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Button(
            onClick = {
                vm.testAlarm(10)
                Toast.makeText(context, context.getString(R.string.test_toast), Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.test_button))
        }

        SectionLabel(stringResource(R.string.section_info), Modifier.padding(horizontal = 16.dp))
        Text(
            stringResource(R.string.info_text, versionName(context)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun StatusItem(@StringRes title: Int, @StringRes description: Int, ok: Boolean, onFix: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(description)) },
        leadingContent = {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = stringResource(if (ok) R.string.status_ok else R.string.status_missing),
                tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            if (!ok) TextButton(onClick = onFix) { Text(stringResource(R.string.action_fix)) }
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
                FilterChip(
                    selected = m == selected,
                    onClick = { onSelect(m) },
                    label = { Text(stringResource(R.string.minutes_short, m)) },
                )
            }
        }
    }
}

private fun ringtonePickerIntent(context: Context, current: String?): Intent {
    val existing = current?.takeIf { it != AppSettings.BUILTIN_SOUND }?.let(Uri::parse)
        ?: Settings.System.DEFAULT_ALARM_ALERT_URI
    return Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, context.getString(R.string.alarm_sound))
        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
}

private fun soundTitle(context: Context, uri: String?): String = when (uri) {
    null -> context.getString(R.string.sound_system_default)
    AppSettings.BUILTIN_SOUND -> context.getString(R.string.sound_builtin)
    else -> runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
        ?: context.getString(R.string.sound_selected)
}

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
