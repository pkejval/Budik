package io.github.pkejval.budik.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pkejval.budik.R
import io.github.pkejval.budik.alarm.CalendarInfo
import io.github.pkejval.budik.alarm.CzechHoliday
import io.github.pkejval.budik.alarm.CzechHolidays
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.AlarmEvent
import io.github.pkejval.budik.data.Integration
import io.github.pkejval.budik.data.IntegrationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class SettingsTab(val title: Int, val icon: ImageVector) {
    DAYS_OFF(R.string.days_off, Icons.Filled.EventBusy),
    CALENDARS(R.string.calendars, Icons.Filled.CalendarMonth),
    INTEGRATIONS(R.string.integrations, Icons.Filled.Hub),
    APPEARANCE(R.string.appearance, Icons.Filled.Palette),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onIntegration: (id: Long, type: IntegrationType?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = context.app.settings
    val daysOff = context.app.daysOff
    val dao = context.app.db.integrations()
    val integrations by remember { dao.observeAll() }.collectAsStateWithLifecycle(emptyList())

    var czHolidays by remember { mutableStateOf(settings.czechHolidays) }
    var calendarIds by remember { mutableStateOf(settings.calendarIds) }
    var keywords by remember { mutableStateOf(settings.calendarKeywords) }
    var appearance by remember { mutableStateOf(settings.ringingAppearance) }

    var permissionTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val hasCalendarPermission = remember(permissionTick) { daysOff.hasCalendarPermission() }
    val calendars by produceState(emptyList<CalendarInfo>(), hasCalendarPermission) {
        value = withContext(Dispatchers.IO) { daysOff.calendars() }
    }
    fun setCalendars(ids: Set<Long>) {
        calendarIds = ids
        settings.calendarIds = ids
    }

    var calendarSheet by remember { mutableStateOf(false) }
    var typeSheet by remember { mutableStateOf(false) }

    val tabs = SettingsTab.entries
    val pager = rememberPagerState { tabs.size }
    val currentTab = tabs[pager.currentPage]

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.settings)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                    tabs.forEachIndexed { i, tab ->
                        Tab(
                            selected = pager.currentPage == i,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            text = { Text(stringResource(tab.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = currentTab == SettingsTab.CALENDARS || currentTab == SettingsTab.INTEGRATIONS,
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                FloatingActionButton(onClick = {
                    when (currentTab) {
                        SettingsTab.CALENDARS ->
                            if (hasCalendarPermission) calendarSheet = true
                            else permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        else -> typeSheet = true
                    }
                }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(
                            if (currentTab == SettingsTab.CALENDARS) R.string.add_calendar else R.string.add_integration,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(pager, Modifier.padding(padding).fillMaxSize()) { page ->
            when (tabs[page]) {
                SettingsTab.DAYS_OFF -> DaysOffTab(czHolidays) {
                    czHolidays = it
                    settings.czechHolidays = it
                }
                SettingsTab.CALENDARS -> CalendarsTab(
                    hasPermission = hasCalendarPermission,
                    selected = calendars.filter { it.id in calendarIds },
                    keywords = keywords,
                    onKeywords = {
                        keywords = it
                        settings.calendarKeywords = it
                    },
                    onRemove = { setCalendars(calendarIds - it.id) },
                    onGrant = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) },
                )
                SettingsTab.INTEGRATIONS -> IntegrationsTab(
                    integrations = integrations,
                    onOpen = { onIntegration(it.id, null) },
                    onToggle = { integration, enabled -> scope.launch { dao.upsert(integration.copy(enabled = enabled)) } },
                )
                SettingsTab.APPEARANCE -> AppearanceTab(appearance) {
                    appearance = it
                    settings.ringingAppearance = it
                }
            }
        }
    }

    if (calendarSheet) {
        CalendarPickerSheet(
            available = calendars.filter { it.id !in calendarIds },
            hasAny = calendars.isNotEmpty(),
            onPick = {
                setCalendars(calendarIds + it.id)
                calendarSheet = false
            },
            onDismiss = { calendarSheet = false },
        )
    }
    if (typeSheet) {
        IntegrationTypeSheet(
            onPick = {
                typeSheet = false
                onIntegration(0, it)
            },
            onDismiss = { typeSheet = false },
        )
    }
}

// --- shared building blocks ---

private val TabPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)

@Composable
private fun TabColumn(content: LazyListScope.() -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = TabPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun InfoCard(text: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun EmptyState(icon: ImageVector, text: Int, action: Pair<Int, () -> Unit>? = null) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(icon, null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClick) { Text(stringResource(label)) }
        }
    }
}

private val TransparentItem @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

// --- tabs ---

@Composable
private fun DaysOffTab(czHolidays: Boolean, onCzHolidays: (Boolean) -> Unit) {
    val upcoming = remember { CzechHolidays.upcoming(LocalDate.now(), 6) }
    TabColumn {
        item { InfoCard(R.string.days_off_hint) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.cz_holidays)) },
                    supportingContent = { Text(stringResource(R.string.cz_holidays_hint)) },
                    trailingContent = { Switch(checked = czHolidays, onCheckedChange = onCzHolidays) },
                    colors = TransparentItem,
                    modifier = Modifier.clickable { onCzHolidays(!czHolidays) },
                )
            }
        }
        if (czHolidays) {
            item { SectionLabel(R.string.upcoming_holidays) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    upcoming.forEachIndexed { i, (date, holiday) ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 80.dp))
                        ListItem(
                            headlineContent = { Text(stringResource(holidayName(holiday))) },
                            supportingContent = {
                                Text(date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                            },
                            leadingContent = { DateBadge(date) },
                            colors = TransparentItem,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DateBadge(date: LocalDate) {
    Column(
        Modifier
            .size(48.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CalendarsTab(
    hasPermission: Boolean,
    selected: List<CalendarInfo>,
    keywords: String,
    onKeywords: (String) -> Unit,
    onRemove: (CalendarInfo) -> Unit,
    onGrant: () -> Unit,
) {
    TabColumn {
        if (!hasPermission) {
            item { EmptyState(Icons.Filled.CalendarMonth, R.string.calendar_permission_hint, R.string.grant_calendar to onGrant) }
            return@TabColumn
        }
        if (selected.isEmpty()) {
            item { EmptyState(Icons.Filled.CalendarMonth, R.string.calendars_empty) }
        } else {
            item { SectionLabel(R.string.selected_calendars) }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    selected.forEachIndexed { i, cal ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp))
                        ListItem(
                            headlineContent = { Text(cal.name) },
                            supportingContent = { Text(cal.account) },
                            leadingContent = { CalendarDot(cal) },
                            trailingContent = {
                                IconButton(onClick = { onRemove(cal) }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
                                }
                            },
                            colors = TransparentItem,
                        )
                    }
                }
            }
        }
        item { SectionLabel(R.string.event_filter) }
        item {
            OutlinedTextField(
                value = keywords,
                onValueChange = onKeywords,
                label = { Text(stringResource(R.string.keywords)) },
                supportingText = { Text(stringResource(R.string.keywords_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CalendarDot(cal: CalendarInfo) {
    Box(Modifier.size(20.dp).background(Color(cal.color), CircleShape))
}

@Composable
private fun IntegrationsTab(
    integrations: List<Integration>,
    onOpen: (Integration) -> Unit,
    onToggle: (Integration, Boolean) -> Unit,
) {
    TabColumn {
        item { InfoCard(R.string.integrations_hint) }
        if (integrations.isEmpty()) {
            item { EmptyState(Icons.Filled.Hub, R.string.integrations_empty) }
        }
        items(integrations, key = { it.id }) { integration ->
            ElevatedCard(onClick = { onOpen(integration) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TypeIcon(integration.type)
                    Spacer(Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            integration.name.ifBlank { stringResource(typeLabel(integration.type)) },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            integrationTarget(integration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AlarmEvent.entries.filter(integration::handles).forEach { EventChip(it) }
                        }
                    }
                    Switch(checked = integration.enabled, onCheckedChange = { onToggle(integration, it) })
                }
            }
        }
    }
}

@Composable
private fun EventChip(event: AlarmEvent) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            stringResource(eventLabel(event)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TypeIcon(type: IntegrationType) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Icon(typeIcon(type), null, Modifier.padding(10.dp).size(24.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private fun integrationTarget(integration: Integration) = when (integration.type) {
    IntegrationType.INTENT -> integration.intentAction
    IntegrationType.WEBHOOK -> "${integration.method} ${Uri.parse(integration.url).host ?: integration.url}"
    IntegrationType.MQTT -> "${integration.mqttHost} · ${integration.mqttTopic}"
}

// --- sheets ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarPickerSheet(
    available: List<CalendarInfo>,
    hasAny: Boolean,
    onPick: (CalendarInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.add_calendar),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (available.isEmpty()) {
            Text(
                stringResource(if (hasAny) R.string.all_calendars_added else R.string.no_calendars),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.navigationBarsPadding()) {
            available.groupBy { it.account }.forEach { (account, cals) ->
                item(key = "header-$account") {
                    Text(
                        account,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(cals, key = { it.id }) { cal ->
                    ListItem(
                        headlineContent = { Text(cal.name) },
                        leadingContent = { CalendarDot(cal) },
                        colors = TransparentItem,
                        modifier = Modifier.clickable { onPick(cal) }.padding(horizontal = 8.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntegrationTypeSheet(onPick: (IntegrationType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.add_integration),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            IntegrationType.entries.forEach { type ->
                ListItem(
                    headlineContent = { Text(stringResource(typeLabel(type))) },
                    supportingContent = { Text(stringResource(typeDescription(type))) },
                    leadingContent = { TypeIcon(type) },
                    colors = TransparentItem,
                    modifier = Modifier.clickable { onPick(type) }.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

// --- labels ---

fun typeLabel(type: IntegrationType) = when (type) {
    IntegrationType.INTENT -> R.string.type_intent
    IntegrationType.WEBHOOK -> R.string.type_webhook
    IntegrationType.MQTT -> R.string.type_mqtt
}

private fun typeDescription(type: IntegrationType) = when (type) {
    IntegrationType.INTENT -> R.string.type_intent_desc
    IntegrationType.WEBHOOK -> R.string.type_webhook_desc
    IntegrationType.MQTT -> R.string.type_mqtt_desc
}

fun typeIcon(type: IntegrationType) = when (type) {
    IntegrationType.INTENT -> Icons.AutoMirrored.Filled.Send
    IntegrationType.WEBHOOK -> Icons.Filled.Cloud
    IntegrationType.MQTT -> Icons.Filled.Hub
}

fun eventLabel(event: AlarmEvent) = when (event) {
    AlarmEvent.RINGING -> R.string.event_ringing
    AlarmEvent.DISMISSED -> R.string.event_dismissed
    AlarmEvent.MISSED -> R.string.event_missed
}

private fun holidayName(holiday: CzechHoliday) = when (holiday) {
    CzechHoliday.NEW_YEAR -> R.string.holiday_new_year
    CzechHoliday.GOOD_FRIDAY -> R.string.holiday_good_friday
    CzechHoliday.EASTER_MONDAY -> R.string.holiday_easter_monday
    CzechHoliday.LABOUR_DAY -> R.string.holiday_labour_day
    CzechHoliday.LIBERATION_DAY -> R.string.holiday_liberation
    CzechHoliday.CYRIL_METHODIUS -> R.string.holiday_cyril_methodius
    CzechHoliday.JAN_HUS -> R.string.holiday_jan_hus
    CzechHoliday.STATEHOOD_DAY -> R.string.holiday_statehood
    CzechHoliday.INDEPENDENCE_DAY -> R.string.holiday_independence
    CzechHoliday.FREEDOM_DAY -> R.string.holiday_freedom
    CzechHoliday.CHRISTMAS_EVE -> R.string.holiday_christmas_eve
    CzechHoliday.CHRISTMAS_DAY -> R.string.holiday_christmas_day
    CzechHoliday.ST_STEPHEN -> R.string.holiday_st_stephen
}
