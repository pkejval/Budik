package io.github.pkejval.budik.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import io.github.pkejval.budik.R
import io.github.pkejval.budik.alarm.AlarmScheduler
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.Alarm
import io.github.pkejval.budik.data.DismissMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var alarm by remember { mutableStateOf<Alarm?>(null) }
    LaunchedEffect(id) {
        alarm = if (id == 0L) Alarm() else context.app.db.alarms().get(id) ?: Alarm()
    }
    val draft = alarm ?: return
    fun update(change: Alarm.() -> Alarm) { alarm = draft.change() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (id == 0L) R.string.new_alarm else R.string.edit_alarm)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (id != 0L) {
                        IconButton(onClick = { scope.launch { AlarmScheduler.delete(context, draft); onBack() } }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    IconButton(onClick = {
                        scope.launch {
                            // Editing resets a pending "skip next".
                            val (saved) = AlarmScheduler.save(context, draft.copy(enabled = true, skipUntil = 0))
                            announceNext(context, saved)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimeField(draft.hour, draft.minute) { h, m -> update { copy(hour = h, minute = m) } }

            OutlinedTextField(
                value = draft.label,
                onValueChange = { v -> update { copy(label = v) } },
                label = { Text(stringResource(R.string.label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(R.string.repeat)
            DaysSelector(draft.daysMask) { mask -> update { copy(daysMask = mask) } }
            if (!draft.isRepeating) {
                DateField(draft.date) { date -> update { copy(dateEpochDay = date?.toEpochDay()) } }
            } else {
                SwitchRow(
                    R.string.skip_days_off, R.string.skip_days_off_hint, draft.skipOnDaysOff,
                ) { v -> update { copy(skipOnDaysOff = v) } }
            }

            SectionTitle(R.string.sound)
            RingtoneField(draft.ringtoneUri) { uri -> update { copy(ringtoneUri = uri) } }
            SwitchRow(R.string.vibrate, null, draft.vibrate) { v -> update { copy(vibrate = v) } }
            SliderRow(
                title = stringResource(R.string.ramp),
                value = if (draft.rampSeconds == 0) stringResource(R.string.off)
                else stringResource(R.string.seconds, draft.rampSeconds),
                current = draft.rampSeconds.toFloat(),
                range = 0f..180f,
                steps = 180 / 10 - 1,
            ) { v -> update { copy(rampSeconds = v.toInt()) } }
            SliderRow(
                title = stringResource(R.string.auto_silence),
                value = stringResource(R.string.minutes, draft.autoSilenceMinutes),
                current = draft.autoSilenceMinutes.toFloat(),
                range = 1f..30f,
                steps = 28,
            ) { v -> update { copy(autoSilenceMinutes = v.toInt()) } }

            SectionTitle(R.string.dismiss_method)
            val methods = listOf(
                DismissMethod.SLIDE_HORIZONTAL to R.string.method_horizontal,
                DismissMethod.SLIDE_VERTICAL to R.string.method_vertical,
                DismissMethod.BUTTON to R.string.method_button,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                methods.forEachIndexed { i, (method, text) ->
                    SegmentedButton(
                        selected = draft.dismissMethod == method,
                        onClick = { update { copy(dismissMethod = method) } },
                        shape = SegmentedButtonDefaults.itemShape(i, methods.size),
                    ) { Text(stringResource(text)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Text(
        formatTime(context, hour, minute),
        fontSize = 72.sp,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
    if (open) {
        val state = rememberTimePickerState(hour, minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onChange(state.hour, state.minute); open = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun DaysSelector(mask: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val bit = 1 shl (day.value - 1)
            FilledIconToggleButton(
                checked = mask and bit != 0,
                onCheckedChange = { onChange(mask xor bit) },
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
            ) { Text(dayShort(day).take(2), style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(stringResource(R.string.date)) },
        supportingContent = { Text(date?.let(::formatDate) ?: stringResource(R.string.date_next)) },
        leadingContent = { Icon(Icons.Filled.DateRange, null) },
        trailingContent = {
            if (date != null) IconButton(onClick = { onChange(null) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear))
            }
        },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        val today = LocalDate.now()
        // DatePicker works with UTC midnights.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis / 86_400_000 >= today.toEpochDay()
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / 86_400_000)) }
                    open = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun RingtoneField(uri: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    var chooser by remember { mutableStateOf(false) }
    val title by produceState("", uri) { value = withContext(Dispatchers.IO) { ringtoneTitle(context, uri) } }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val picked = IntentCompat.getParcelableExtra(data, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        onChange(picked?.takeUnless { it == Settings.System.DEFAULT_ALARM_ALERT_URI }?.toString())
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
        if (picked != null) {
            context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            onChange(picked.toString())
        }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.ringtone)) },
        supportingContent = { Text(title) },
        leadingContent = { Icon(Icons.Filled.MusicNote, null) },
        modifier = Modifier.clickable { chooser = true },
    )
    if (chooser) {
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text(stringResource(R.string.ringtone)) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.ringtone_system)) },
                        modifier = Modifier.clickable {
                            chooser = false
                            systemPicker.launch(
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        uri?.let(Uri::parse) ?: Settings.System.DEFAULT_ALARM_ALERT_URI,
                                    ),
                            )
                        },
                    )
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.ringtone_file)) },
                        modifier = Modifier.clickable {
                            chooser = false
                            filePicker.launch(arrayOf("audio/*"))
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooser = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun SwitchRow(title: Int, subtitle: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = subtitle?.let { { Text(stringResource(it)) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
