package io.github.pkejval.budik.integration

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What happened, serialisable into WorkManager input so a retry sends the original event. */
data class EventMessage(
    val event: String,
    val alarmId: Long,
    val label: String,
    val alarmTime: String,
    val timestamp: Long,
) {
    val isoTime: String get() = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))

    fun toJson() = buildString {
        append('{')
        append("\"event\":").append(jsonString(event)).append(',')
        append("\"alarm_id\":").append(alarmId).append(',')
        append("\"label\":").append(jsonString(label)).append(',')
        append("\"alarm_time\":").append(jsonString(alarmTime)).append(',')
        append("\"timestamp\":").append(timestamp).append(',')
        append("\"time\":").append(jsonString(isoTime))
        append('}')
    }

    /**
     * Replaces {event}, {alarm_id}, {label}, {alarm_time}, {timestamp}, {time} and {json}.
     * With [urlEncode] values are encoded for use inside a URL.
     */
    fun render(template: String, urlEncode: Boolean = false): String {
        fun v(s: String) = if (urlEncode) URLEncoder.encode(s, "UTF-8") else s
        return template
            .replace("{event}", v(event))
            .replace("{alarm_id}", v(alarmId.toString()))
            .replace("{label}", v(label))
            .replace("{alarm_time}", v(alarmTime))
            .replace("{timestamp}", v(timestamp.toString()))
            .replace("{time}", v(isoTime))
            .replace("{json}", v(toJson()))
    }

    companion object {
        fun jsonString(s: String) = buildString {
            append('"')
            for (ch in s) when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch == '\n' -> append("\\n")
                ch == '\r' -> append("\\r")
                ch == '\t' -> append("\\t")
                ch < ' ' -> append(String.format("\\u%04x", ch.code))
                else -> append(ch)
            }
            append('"')
        }
    }
}
