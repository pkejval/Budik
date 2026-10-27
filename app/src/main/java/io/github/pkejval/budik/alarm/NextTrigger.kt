package io.github.pkejval.budik.alarm

import io.github.pkejval.budik.data.Alarm
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

private const val SEARCH_DAYS = 7 * 53L

/**
 * Next ring time strictly after [after] and after [Alarm.skipUntil], or null if there is none.
 * [isDayOff] can veto candidates of repeating alarms (one-time alarms always ring).
 */
fun Alarm.nextTrigger(
    after: ZonedDateTime,
    isDayOff: (ZonedDateTime) -> Boolean = { false },
): ZonedDateTime? {
    val zone = after.zone
    val threshold = maxOf(after.toInstant(), Instant.ofEpochMilli(skipUntil))
    val time = LocalTime.of(hour, minute)
    fun candidate(day: LocalDate) =
        day.atTime(time).atZone(zone).takeIf { it.toInstant().isAfter(threshold) }

    val startDay = threshold.atZone(zone).toLocalDate()
    if (!isRepeating) {
        date?.let { return candidate(it) }
        return candidate(startDay) ?: candidate(startDay.plusDays(1))
    }
    for (i in 0..SEARCH_DAYS) {
        val day = startDay.plusDays(i)
        if (!ringsOn(day.dayOfWeek)) continue
        val at = candidate(day) ?: continue
        if (!isDayOff(at)) return at
    }
    return null
}
