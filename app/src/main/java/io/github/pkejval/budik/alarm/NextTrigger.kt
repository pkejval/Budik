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

data class SkippedRange(val from: LocalDate, val to: LocalDate, val reason: DayOffReason)

/**
 * Occurrences vetoed by [reasonOf] before the next real ring. Consecutive occurrences with
 * the same reason are merged, so a week of vacation is one range.
 */
fun Alarm.skippedBeforeNext(after: ZonedDateTime, reasonOf: (ZonedDateTime) -> DayOffReason?): List<SkippedRange> {
    val ranges = mutableListOf<SkippedRange>()
    if (!isRepeating) return ranges
    var cursor = after
    repeat(SEARCH_DAYS.toInt()) {
        val at = nextTrigger(cursor) ?: return ranges
        val reason = reasonOf(at) ?: return ranges
        val day = at.toLocalDate()
        val last = ranges.lastOrNull()
        if (last?.reason == reason) ranges[ranges.lastIndex] = last.copy(to = day)
        else ranges += SkippedRange(day, day, reason)
        cursor = at
    }
    return ranges
}
