package de.terminwecker.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.terminwecker.alarm.AlarmActivity
import de.terminwecker.alarm.AlarmService
import de.terminwecker.data.AlarmRule
import de.terminwecker.data.CalendarView
import de.terminwecker.data.EventInstance
import de.terminwecker.ui.theme.TerminweckerTheme

class MainActivity : ComponentActivity() {
    private val tabRequest = mutableIntStateOf(-1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        tabRequest.intValue = intent.getIntExtra(EXTRA_TAB, -1)
        setContent {
            TerminweckerTheme {
                MainScreen(tabRequest)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabRequest.intValue = intent.getIntExtra(EXTRA_TAB, -1)
    }

    companion object {
        const val EXTRA_TAB = "de.terminwecker.extra.TAB"
        const val TAB_CALENDAR = 0
        const val TAB_ALARMS = 1
        const val TAB_SETTINGS = 2
    }
}

/** Was der Wecker-Editor bearbeiten soll. */
sealed interface EditorRequest {
    data class ForInstance(val instance: EventInstance) : EditorRequest
    data class ForRule(val rule: AlarmRule) : EditorRequest
    data object NewKeyword : EditorRequest
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(tabRequest: MutableIntState, vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(MainActivity.TAB_CALENDAR) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    val status by vm.status.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ringing by AlarmService.current.collectAsStateWithLifecycle()

    LaunchedEffect(tabRequest.intValue) {
        if (tabRequest.intValue >= 0) {
            tab = tabRequest.intValue
            tabRequest.intValue = -1
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    // Beim ersten Start direkt nach Kalender und Benachrichtigungen fragen.
    val firstRunPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { vm.refresh() }
    var askedOnStart by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (askedOnStart) return@LaunchedEffect
        askedOnStart = true
        val needed = buildList {
            if (!status.calendar) add(Manifest.permission.READ_CALENDAR)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.notifications) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) firstRunPermissions.launch(needed.toTypedArray())
    }

    val titles = listOf("Kalender", "Wecker", "Einstellungen")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[tab]) },
                actions = {
                    if (tab == MainActivity.TAB_CALENDAR && status.calendar) {
                        if (settings.calendarView != CalendarView.LIST) {
                            IconButton(onClick = { vm.updateSettings { it.copy(calendarView = CalendarView.LIST) } }) {
                                Icon(Icons.Filled.Search, contentDescription = "Termin suchen")
                            }
                        }
                        CalendarViewMenu(settings.calendarView) { view -> vm.updateSettings { it.copy(calendarView = view) } }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_CALENDAR,
                    onClick = { tab = MainActivity.TAB_CALENDAR },
                    icon = {
                        Icon(
                            if (tab == MainActivity.TAB_CALENDAR) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                        )
                    },
                    label = { Text(titles[0]) },
                )
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_ALARMS,
                    onClick = { tab = MainActivity.TAB_ALARMS },
                    icon = {
                        Icon(
                            if (tab == MainActivity.TAB_ALARMS) Icons.Filled.Alarm else Icons.Outlined.Alarm,
                            contentDescription = null,
                        )
                    },
                    label = { Text(titles[1]) },
                )
                NavigationBarItem(
                    selected = tab == MainActivity.TAB_SETTINGS,
                    onClick = { tab = MainActivity.TAB_SETTINGS },
                    icon = {
                        BadgedBox(badge = { if (!status.allGood) Badge() }) {
                            Icon(
                                if (tab == MainActivity.TAB_SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = null,
                            )
                        }
                    },
                    label = { Text(titles[2]) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            ringing?.let { payload ->
                Banner(
                    text = "Wecker klingelt: ${payload.label}",
                    action = "Öffnen",
                    onAction = { context.startActivity(AlarmActivity.intent(context)) },
                )
            }
            if (!status.critical && tab != MainActivity.TAB_SETTINGS) {
                Banner(
                    text = "Es fehlen Berechtigungen – Wecker klingeln sonst evtl. nicht.",
                    action = "Beheben",
                    onAction = { tab = MainActivity.TAB_SETTINGS },
                )
            }
            Box(Modifier.weight(1f)) {
                when (tab) {
                    MainActivity.TAB_CALENDAR -> CalendarScreen(vm, onEventClick = { editor = EditorRequest.ForInstance(it) })
                    MainActivity.TAB_ALARMS -> AlarmsScreen(
                        vm,
                        onEdit = { editor = EditorRequest.ForRule(it) },
                        onNewKeyword = { editor = EditorRequest.NewKeyword },
                    )
                    else -> SettingsScreen(vm)
                }
            }
        }
    }

    editor?.let { request ->
        RuleEditorDialog(
            vm = vm,
            request = request,
            onEditRule = { editor = EditorRequest.ForRule(it) },
            onDismiss = { editor = null },
        )
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(text, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun PermissionHint(text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.padding(24.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(button) }
    }
}
