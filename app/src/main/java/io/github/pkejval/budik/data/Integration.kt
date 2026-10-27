package io.github.pkejval.budik.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class IntegrationType { INTENT, WEBHOOK, MQTT }

enum class AlarmEvent(val key: String, val bit: Int) {
    RINGING("ringing", 1),
    DISMISSED("dismissed", 2),
    MISSED("missed", 4),
}

/** A target notified about alarm events. Fields not relevant to [type] are ignored. */
@Entity(tableName = "integrations")
data class Integration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: IntegrationType = IntegrationType.WEBHOOK,
    val name: String = "",
    val enabled: Boolean = true,
    /** Bitmask of [AlarmEvent.bit]. */
    val events: Int = AlarmEvent.entries.sumOf { it.bit },

    // Intent
    val intentAction: String = DEFAULT_INTENT_ACTION,
    val intentPackage: String = "",

    // Webhook
    val url: String = "",
    val method: String = "POST",
    /** One "Name: value" per line. */
    val headers: String = "",
    val body: String = "{json}",

    // MQTT
    val mqttHost: String = "",
    val mqttPort: Int = 1883,
    val mqttTls: Boolean = false,
    val mqttUser: String = "",
    val mqttPassword: String = "",
    val mqttTopic: String = "budik/{event}",
    val mqttPayload: String = "{json}",
    val mqttQos: Int = 1,
    val mqttRetain: Boolean = false,
) {
    fun handles(event: AlarmEvent) = events and event.bit != 0

    companion object {
        const val DEFAULT_INTENT_ACTION = "io.github.pkejval.budik.ALARM_EVENT"
    }
}
