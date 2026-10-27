package io.github.pkejval.budik.integration

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.Alarm
import io.github.pkejval.budik.data.AlarmEvent
import io.github.pkejval.budik.data.Integration
import io.github.pkejval.budik.data.IntegrationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.MqttClient
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private const val TAG = "BudikEvents"

object EventDispatcher {

    /**
     * Sends [event] to every matching integration in parallel, right away. Failed network
     * deliveries are handed to [RetryWorker]. The returned job completes once every first
     * attempt has finished, so callers can keep their process alive until then.
     */
    fun dispatch(context: Context, event: AlarmEvent, alarm: Alarm): Job {
        val message = EventMessage(
            event = event.key,
            alarmId = alarm.id,
            label = alarm.label,
            alarmTime = "%02d:%02d".format(alarm.hour, alarm.minute),
            timestamp = System.currentTimeMillis(),
        )
        val appContext = context.applicationContext
        // More events follow a ringing one, so its MQTT connection stays open for them.
        val keepOpen = event == AlarmEvent.RINGING
        return appContext.app.scope.launch(Dispatchers.IO) {
            appContext.app.db.integrations().enabled().filter { it.handles(event) }.forEach { integration ->
                launch {
                    try {
                        send(appContext, integration, message, keepOpen)
                    } catch (e: Exception) {
                        Log.w(TAG, "Delivery to ${integration.name} failed, queueing retry", e)
                        if (integration.type != IntegrationType.INTENT) {
                            RetryWorker.enqueue(appContext, integration.id, message)
                        }
                    }
                }
            }
        }
    }

    /** Throws on failure. With [keepOpen] an MQTT connection is parked for the next event. */
    suspend fun send(context: Context, integration: Integration, message: EventMessage, keepOpen: Boolean = false) =
        withContext(Dispatchers.IO) {
            when (integration.type) {
                IntegrationType.INTENT -> sendIntent(context, integration, message)
                IntegrationType.WEBHOOK -> sendWebhook(integration, message)
                IntegrationType.MQTT -> sendMqtt(integration, message, keepOpen)
            }
        }

    private fun sendIntent(context: Context, integration: Integration, message: EventMessage) {
        val action = message.render(integration.intentAction.ifBlank { Integration.DEFAULT_INTENT_ACTION })
        val intent = Intent(action).apply {
            if (integration.intentPackage.isNotBlank()) setPackage(integration.intentPackage.trim())
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra("event", message.event)
            putExtra("alarm_id", message.alarmId)
            putExtra("label", message.label)
            putExtra("alarm_time", message.alarmTime)
            putExtra("timestamp", message.timestamp)
            putExtra("time", message.isoTime)
        }
        context.sendBroadcast(intent)
    }

    private fun sendWebhook(integration: Integration, message: EventMessage) {
        val connection = URL(message.render(integration.url.trim(), urlEncode = true)).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = integration.method
            var hasContentType = false
            integration.headers.lineSequence().forEach { line ->
                val name = line.substringBefore(':', "").trim()
                if (name.isNotEmpty()) {
                    connection.setRequestProperty(name, message.render(line.substringAfter(':').trim()))
                    if (name.equals("Content-Type", ignoreCase = true)) hasContentType = true
                }
            }
            if (integration.method != "GET" && integration.body.isNotEmpty()) {
                if (!hasContentType) connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.use { it.write(message.render(integration.body).toByteArray()) }
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
        } finally {
            // Keep the socket in the pool so the next event skips DNS, TCP and TLS.
            Connections.releaseHttp(connection)
        }
    }

    private fun sendMqtt(integration: Integration, message: EventMessage, keepOpen: Boolean) {
        fun publish(client: MqttClient) {
            client.publish(
                message.render(integration.mqttTopic),
                message.render(integration.mqttPayload).toByteArray(),
                integration.mqttQos.coerceIn(0, 2),
                integration.mqttRetain,
            )
            if (keepOpen) Connections.parkMqtt(integration, client, Connections.RINGING_TTL_MS)
            else Connections.closeMqtt(client)
        }
        Connections.takeMqtt(integration)?.let { warm ->
            try {
                return publish(warm)
            } catch (e: Exception) {
                // The broker may have dropped the parked session; fall through to a fresh one.
                Log.w(TAG, "Parked MQTT connection failed, reconnecting", e)
                Connections.closeMqtt(warm)
            }
        }
        val fresh = Connections.connectMqtt(integration)
        try {
            publish(fresh)
        } catch (e: Exception) {
            Connections.closeMqtt(fresh)
            throw e
        }
    }
}

/** Re-sends a failed event once the network is available, with exponential backoff. */
class RetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val integration = applicationContext.app.db.integrations().get(inputData.getLong(KEY_INTEGRATION, -1))
            ?.takeIf { it.enabled } ?: return Result.success()
        val message = EventMessage(
            event = inputData.getString(KEY_EVENT) ?: return Result.failure(),
            alarmId = inputData.getLong(KEY_ALARM_ID, 0),
            label = inputData.getString(KEY_LABEL) ?: "",
            alarmTime = inputData.getString(KEY_ALARM_TIME) ?: "",
            timestamp = inputData.getLong(KEY_TIMESTAMP, 0),
        )
        return try {
            EventDispatcher.send(applicationContext, integration, message)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Retry ${runAttemptCount + 1} to ${integration.name} failed", e)
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 20
        private const val KEY_INTEGRATION = "integration"
        private const val KEY_EVENT = "event"
        private const val KEY_ALARM_ID = "alarm_id"
        private const val KEY_LABEL = "label"
        private const val KEY_ALARM_TIME = "alarm_time"
        private const val KEY_TIMESTAMP = "timestamp"

        fun enqueue(context: Context, integrationId: Long, message: EventMessage) {
            val request = OneTimeWorkRequestBuilder<RetryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_INTEGRATION to integrationId,
                        KEY_EVENT to message.event,
                        KEY_ALARM_ID to message.alarmId,
                        KEY_LABEL to message.label,
                        KEY_ALARM_TIME to message.alarmTime,
                        KEY_TIMESTAMP to message.timestamp,
                    ),
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
