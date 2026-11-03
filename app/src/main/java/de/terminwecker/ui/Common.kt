package de.terminwecker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import de.terminwecker.data.AlarmRule
import de.terminwecker.data.RuleScope

/** Kalenderfarben kommen teils ohne Alpha-Kanal aus dem Provider. */
fun calendarColor(color: Int): Color = Color(color or 0xFF000000.toInt())

fun ruleTitle(rule: AlarmRule): String =
    rule.label.ifBlank { if (rule.scope == RuleScope.TITLE) rule.titleQuery else rule.eventTitle }.ifBlank { "Wecker" }

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Fragt eine Laufzeit-Berechtigung an. Wurde sie dauerhaft abgelehnt, geht es stattdessen
 * in die App-Einstellungen, weil Android dann keinen Dialog mehr zeigt.
 */
@Composable
fun rememberPermissionRequest(permission: String, vm: MainViewModel, onGranted: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = context.findActivity()
        if (granted) {
            onGranted()
        } else if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            SystemSettings.open(context, SystemSettings.appDetails(context))
        }
        vm.refresh()
    }
    return { launcher.launch(permission) }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun ColorDot(color: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(calendarColor(color)),
    )
}
