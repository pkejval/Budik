package io.github.pkejval.budik.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pkejval.budik.R
import io.github.pkejval.budik.alarm.CalendarInfo
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.IntegrationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onIntegration: (Long) -> Unit) {
    val context = LocalContext.current
    val settings = context.app.settings
    val daysOff = context.app.daysOff
    val integrations by remember { context.app.db.integrations().observeAll() }.collectAsStateWithLifecycle(emptyList())

    var czHolidays by remember { mutableStateOf(settings.czechHolidays) }
    var calendarIds by remember { mutableStateOf(settings.calendarIds) }
    var keywords by remember { mutableStateOf(settings.calendarKeywords) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val calendars by produceState(emptyList<CalendarInfo>(), permissionTick) {
        value = withContext(Dispatchers.IO) { daysOff.calendars() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Header(R.string.days_off)
            Text(
                stringResource(R.string.days_off_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SwitchRow(R.string.cz_holidays, R.string.cz_holidays_hint, czHolidays) {
                czHolidays = it
                settings.czechHolidays = it
            }

            if (!daysOff.hasCalendarPermission()) {
                OutlinedButton(
                    onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                    modifier = Modifier.padding(16.dp),
                ) { Text(stringResource(R.string.grant_calendar)) }
            } else {
                Text(
                    stringResource(R.string.calendars),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                )
                if (calendars.isEmpty()) {
                    Text(stringResource(R.string.no_calendars), Modifier.padding(16.dp))
                }
                calendars.forEach { cal ->
                    val checked = cal.id in calendarIds
                    val toggle = {
                        calendarIds = if (checked) calendarIds - cal.id else calendarIds + cal.id
                        settings.calendarIds = calendarIds
                    }
                    ListItem(
                        headlineContent = { Text(cal.name) },
                        supportingContent = { Text(cal.account) },
                        leadingContent = { Box(Modifier.size(16.dp).background(Color(cal.color), CircleShape)) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                        modifier = Modifier.clickable { toggle() },
                    )
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = {
                        keywords = it
                        settings.calendarKeywords = it
                    },
                    label = { Text(stringResource(R.string.keywords)) },
                    supportingText = { Text(stringResource(R.string.keywords_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            Header(R.string.integrations)
            Text(
                stringResource(R.string.integrations_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            integrations.forEach { integration ->
                ListItem(
                    headlineContent = { Text(integration.name.ifBlank { integration.type.name }) },
                    supportingContent = {
                        Text(
                            buildString {
                                append(stringResource(typeLabel(integration.type)))
                                if (!integration.enabled) append(" · ").append(stringResource(R.string.disabled))
                            },
                        )
                    },
                    leadingContent = { Icon(typeIcon(integration.type), null) },
                    modifier = Modifier.clickable { onIntegration(integration.id) },
                )
            }
            Button(onClick = { onIntegration(0) }, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Filled.Add, null)
                Text(stringResource(R.string.add_integration), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun Header(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

fun typeLabel(type: IntegrationType) = when (type) {
    IntegrationType.INTENT -> R.string.type_intent
    IntegrationType.WEBHOOK -> R.string.type_webhook
    IntegrationType.MQTT -> R.string.type_mqtt
}

fun typeIcon(type: IntegrationType) = when (type) {
    IntegrationType.INTENT -> Icons.AutoMirrored.Filled.Send
    IntegrationType.WEBHOOK -> Icons.Filled.Cloud
    IntegrationType.MQTT -> Icons.Filled.Hub
}
