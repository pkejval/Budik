package io.github.pkejval.budik.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.pkejval.budik.R
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.AlarmEvent
import io.github.pkejval.budik.data.Integration
import io.github.pkejval.budik.data.IntegrationType
import io.github.pkejval.budik.integration.EventDispatcher
import io.github.pkejval.budik.integration.EventMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationEditScreen(id: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val dao = context.app.db.integrations()
    var integration by remember { mutableStateOf<Integration?>(null) }
    LaunchedEffect(id) { integration = if (id == 0L) Integration() else dao.get(id) ?: Integration() }
    val draft = integration ?: return
    fun update(change: Integration.() -> Integration) { integration = draft.change() }

    val testOk = stringResource(R.string.test_ok)
    val testFailed = stringResource(R.string.test_failed)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.integration)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (id != 0L) {
                        IconButton(onClick = { scope.launch { dao.delete(draft); onBack() } }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    IconButton(onClick = { scope.launch { dao.upsert(draft); onBack() } }) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val types = IntegrationType.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, type ->
                    SegmentedButton(
                        selected = draft.type == type,
                        onClick = { update { copy(type = type) } },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                        icon = { Icon(typeIcon(type), null) },
                    ) { Text(stringResource(typeLabel(type))) }
                }
            }
            Field(R.string.name, draft.name) { update { copy(name = it) } }
            SwitchRow(R.string.enabled, null, draft.enabled) { v -> update { copy(enabled = v) } }

            Text(stringResource(R.string.events), style = MaterialTheme.typography.titleSmall)
            listOf(
                AlarmEvent.RINGING to R.string.event_ringing,
                AlarmEvent.DISMISSED to R.string.event_dismissed,
                AlarmEvent.MISSED to R.string.event_missed,
            ).forEach { (event, text) ->
                val checked = draft.handles(event)
                Row(
                    Modifier.fillMaxWidth().clickable { update { copy(events = events xor event.bit) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { update { copy(events = events xor event.bit) } })
                    Text(stringResource(text))
                }
            }

            when (draft.type) {
                IntegrationType.INTENT -> {
                    Field(R.string.intent_action, draft.intentAction) { update { copy(intentAction = it) } }
                    Field(R.string.intent_package, draft.intentPackage) { update { copy(intentPackage = it) } }
                    Hint(R.string.intent_hint)
                }
                IntegrationType.WEBHOOK -> {
                    Field(R.string.url, draft.url, keyboard = KeyboardType.Uri) { update { copy(url = it) } }
                    val methods = listOf("POST", "PUT", "GET")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        methods.forEachIndexed { i, m ->
                            SegmentedButton(
                                selected = draft.method == m,
                                onClick = { update { copy(method = m) } },
                                shape = SegmentedButtonDefaults.itemShape(i, methods.size),
                            ) { Text(m) }
                        }
                    }
                    Field(R.string.headers, draft.headers, singleLine = false) { update { copy(headers = it) } }
                    if (draft.method != "GET") {
                        Field(R.string.body, draft.body, singleLine = false) { update { copy(body = it) } }
                    }
                }
                IntegrationType.MQTT -> {
                    Field(R.string.mqtt_host, draft.mqttHost, keyboard = KeyboardType.Uri) { update { copy(mqttHost = it) } }
                    Field(R.string.mqtt_port, draft.mqttPort.toString(), keyboard = KeyboardType.Number) {
                        update { copy(mqttPort = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) }
                    }
                    SwitchRow(R.string.mqtt_tls, null, draft.mqttTls) { v ->
                        update { copy(mqttTls = v, mqttPort = if (v && mqttPort == 1883) 8883 else if (!v && mqttPort == 8883) 1883 else mqttPort) }
                    }
                    Field(R.string.mqtt_user, draft.mqttUser) { update { copy(mqttUser = it) } }
                    Field(R.string.mqtt_password, draft.mqttPassword, password = true) { update { copy(mqttPassword = it) } }
                    Field(R.string.mqtt_topic, draft.mqttTopic) { update { copy(mqttTopic = it) } }
                    Field(R.string.mqtt_payload, draft.mqttPayload, singleLine = false) { update { copy(mqttPayload = it) } }
                    Text(stringResource(R.string.mqtt_qos), style = MaterialTheme.typography.titleSmall)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        (0..2).forEach { q ->
                            SegmentedButton(
                                selected = draft.mqttQos == q,
                                onClick = { update { copy(mqttQos = q) } },
                                shape = SegmentedButtonDefaults.itemShape(q, 3),
                            ) { Text("QoS $q") }
                        }
                    }
                    SwitchRow(R.string.mqtt_retain, null, draft.mqttRetain) { v -> update { copy(mqttRetain = v) } }
                }
            }
            Hint(R.string.placeholders_hint)

            FilledTonalButton(onClick = {
                scope.launch {
                    val message = EventMessage("test", 0, "Test", "07:00", System.currentTimeMillis())
                    val result = runCatching { EventDispatcher.send(context, draft, message) }
                    snackbar.showSnackbar(
                        result.exceptionOrNull()?.let { "$testFailed: ${it.message ?: it.javaClass.simpleName}" } ?: testOk,
                    )
                }
            }) { Text(stringResource(R.string.test_send)) }
        }
    }
}

@Composable
private fun Field(
    label: Int,
    value: String,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
