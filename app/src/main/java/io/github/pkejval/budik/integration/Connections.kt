package io.github.pkejval.budik.integration

import android.content.Context
import android.util.Log
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.Integration
import io.github.pkejval.budik.data.IntegrationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val TAG = "BudikConnections"
internal const val TIMEOUT_MS = 5_000

/**
 * Connections opened shortly before an alarm rings, so the events go out without waiting
 * for DNS, TCP, TLS or the MQTT handshake.
 *
 * - HTTP: a HEAD request to the server root (never the webhook URL itself) leaves a
 *   keep-alive connection in the platform HttpURLConnection pool, which the webhook reuses.
 * - MQTT: a connected client is parked here and handed to the first publish.
 */
object Connections {
    /** How long a pre-warmed MQTT connection waits for its alarm. */
    const val PREWARM_TTL_MS = 2 * 60_000L
    /** Covers the longest ringing (auto-silence is at most 30 min) until dismissed / missed. */
    const val RINGING_TTL_MS = 35 * 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val parked = HashMap<MqttKey, Parked>()

    private data class Parked(val client: MqttClient, val expiry: Job)

    /** Everything that identifies a broker session; a changed setting must not reuse a connection. */
    private data class MqttKey(
        val id: Long, val host: String, val port: Int, val tls: Boolean, val user: String, val password: String,
    )

    private fun Integration.mqttKey() = MqttKey(id, mqttHost.trim(), mqttPort, mqttTls, mqttUser, mqttPassword)

    suspend fun prewarm(context: Context) = coroutineScope {
        context.app.db.integrations().enabled().forEach { integration ->
            launch(Dispatchers.IO) {
                try {
                    when (integration.type) {
                        IntegrationType.WEBHOOK -> warmHttp(integration.url)
                        IntegrationType.MQTT -> if (!hasMqtt(integration)) {
                            parkMqtt(integration, connectMqtt(integration), PREWARM_TTL_MS)
                        }
                        IntegrationType.INTENT -> Unit
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Prewarm of ${integration.name} failed", e)
                }
            }
        }
    }

    private fun warmHttp(url: String) {
        val target = URL(url.trim())
        if ('{' in target.host) return // host depends on the event
        val root = URL(target.protocol, target.host, target.port, "/")
        val connection = root.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.requestMethod = "HEAD"
        connection.responseCode
        releaseHttp(connection)
    }

    /** Returns an HTTP connection to the pool instead of closing its socket. */
    fun releaseHttp(connection: HttpURLConnection) {
        runCatching { (connection.errorStream ?: connection.inputStream)?.close() }
    }

    fun connectMqtt(integration: Integration): MqttClient {
        val scheme = if (integration.mqttTls) "ssl" else "tcp"
        val client = MqttClient(
            "$scheme://${integration.mqttHost.trim()}:${integration.mqttPort}",
            "budik-" + UUID.randomUUID().toString().take(8),
            MemoryPersistence(),
        )
        try {
            client.timeToWait = TIMEOUT_MS.toLong()
            client.connect(MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = TIMEOUT_MS / 1000
                if (integration.mqttUser.isNotBlank()) {
                    userName = integration.mqttUser
                    password = integration.mqttPassword.toCharArray()
                }
            })
        } catch (e: Exception) {
            closeMqtt(client)
            throw e
        }
        return client
    }

    @Synchronized
    private fun hasMqtt(integration: Integration) = parked[integration.mqttKey()]?.client?.isConnected == true

    /** Takes a parked, still connected client for exclusive use, if there is one. */
    @Synchronized
    fun takeMqtt(integration: Integration): MqttClient? {
        val entry = parked.remove(integration.mqttKey()) ?: return null
        entry.expiry.cancel()
        return entry.client.takeIf { it.isConnected } ?: run { closeMqtt(entry.client); null }
    }

    /** Parks [client] for the next event of the same integration, closing it after [ttlMs]. */
    @Synchronized
    fun parkMqtt(integration: Integration, client: MqttClient, ttlMs: Long) {
        val key = integration.mqttKey()
        val expiry = scope.launch {
            delay(ttlMs)
            val expired = synchronized(this@Connections) { parked[key]?.takeIf { it.client === client }?.also { parked.remove(key) } }
            expired?.let { closeMqtt(it.client) }
        }
        parked.put(key, Parked(client, expiry))?.let { old ->
            old.expiry.cancel()
            if (old.client !== client) scope.launch { closeMqtt(old.client) }
        }
    }

    fun closeMqtt(client: MqttClient) {
        runCatching { if (client.isConnected) client.disconnect(1_000) }
        runCatching { if (client.isConnected) client.disconnectForcibly(500) }
        runCatching { client.close() }
    }
}
