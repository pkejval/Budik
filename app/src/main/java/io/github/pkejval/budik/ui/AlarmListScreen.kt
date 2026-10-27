package io.github.pkejval.budik.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.pkejval.budik.R
import io.github.pkejval.budik.alarm.AlarmScheduler
import io.github.pkejval.budik.alarm.SkippedRange
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.Alarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(onEdit: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val alarms by remember { context.app.db.alarms().observeAll() }.collectAsStateWithLifecycle(emptyList())

    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val upcoming by produceState(emptyMap<Long, Upcoming>(), alarms, tick) {
        value = withContext(Dispatchers.IO) {
            alarms.associate {
                it.id to Upcoming(
                    AlarmScheduler.expectedTrigger(context, it),
                    AlarmScheduler.skippedBeforeNext(context, it),
                )
            }
        }
    }
    val next = upcoming.values.mapNotNull { it.next }.minOrNull()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(0) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm))
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PermissionBanners() }
            item { NextAlarmHeader(next) }
            if (alarms.isEmpty()) item { EmptyState() }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    upcoming = upcoming[alarm.id] ?: Upcoming(null, emptyList()),
                    onClick = { onEdit(alarm.id) },
                    onToggle = { enabled ->
                        scope.launch {
                            val (saved) = AlarmScheduler.save(context, alarm.copy(enabled = enabled, skipUntil = 0))
                            if (enabled) announceNext(context, saved)
                        }
                    },
                    onDuplicate = { scope.launch { AlarmScheduler.duplicate(context, alarm) } },
                    onSkip = { scope.launch { AlarmScheduler.toggleSkipNext(context, alarm) } },
                    onDelete = { scope.launch { AlarmScheduler.delete(context, alarm) } },
                )
            }
        }
    }
}

@Composable
private fun NextAlarmHeader(next: ZonedDateTime?) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) {
        Text(
            if (next == null) stringResource(R.string.no_active_alarm) else formatRingsIn(context, next),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (next != null) {
            Text(formatNext(context, next), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.AlarmOff, contentDescription = null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.no_alarms), color = MaterialTheme.colorScheme.outline)
    }
}

private data class Upcoming(val next: ZonedDateTime?, val skipped: List<SkippedRange>)

@Composable
private fun AlarmCard(
    alarm: Alarm,
    upcoming: Upcoming,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val skipped = alarm.enabled && alarm.skipUntil > System.currentTimeMillis()
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).alpha(if (alarm.enabled) 1f else 0.5f)) {
                Text(
                    formatTime(context, alarm.hour, alarm.minute),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Light,
                )
                val subtitle = listOf(alarm.label, repeatSummary(context, alarm)).filter { it.isNotBlank() }
                Text(subtitle.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                if (alarm.enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (skipped) {
                            Icon(Icons.Filled.SkipNext, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(4.dp))
                        } else if (alarm.skipOnDaysOff) {
                            Icon(Icons.Filled.EventBusy, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            upcoming.next?.let { stringResource(if (skipped) R.string.skipped_next else R.string.next_ring, formatNext(context, it)) }
                                ?: stringResource(R.string.not_scheduled),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (upcoming.skipped.isNotEmpty()) {
                        Text(
                            stringResource(R.string.skips, formatSkipped(context, upcoming.skipped)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (alarm.enabled && alarm.isRepeating) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (skipped) R.string.unskip else R.string.skip_next)) },
                            leadingIcon = { Icon(Icons.Filled.SkipNext, null) },
                            onClick = { menu = false; onSkip() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.duplicate)) },
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                        onClick = { menu = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** Warns about missing permissions without which alarms would not ring reliably. */
@Composable
private fun PermissionBanners() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    val needsNotifications = remember(refresh) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    val needsExact = remember(refresh) { !AlarmScheduler.canScheduleExact(context) }
    val needsFullScreen = remember(refresh) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    LaunchedEffect(Unit) {
        if (needsNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (needsNotifications) Banner(R.string.perm_notifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needsExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Banner(R.string.perm_exact) {
            context.openSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        }
        if (needsFullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Banner(R.string.perm_full_screen) {
            context.openSettings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        }
    }
}

private fun Context.openSettings(action: String) {
    startActivity(Intent(action, Uri.parse("package:$packageName")))
}

@Composable
private fun Banner(text: Int, onFix: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(text), Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onFix) { Text(stringResource(R.string.fix)) }
        }
    }
}
