package io.github.pkejval.budik.alarm

import io.github.pkejval.budik.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NextTriggerTest {
    private val zone = ZoneId.of("Europe/Prague")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    // 2026-10-27 is a Tuesday.
    private val tuesdayMorning = at(2026, 10, 27, 6, 0)

    @Test
    fun oneTimeRingsTodayIfStillAhead() {
        assertEquals(at(2026, 10, 27, 7, 0), Alarm(hour = 7).nextTrigger(tuesdayMorning))
    }

    @Test
    fun oneTimeRollsToTomorrowWhenTimePassed() {
        assertEquals(at(2026, 10, 28, 5, 0), Alarm(hour = 5).nextTrigger(tuesdayMorning))
    }

    @Test
    fun oneTimeWithDateInPastIsNotScheduled() {
        val alarm = Alarm(hour = 7, dateEpochDay = LocalDate.of(2026, 10, 1).toEpochDay())
        assertNull(alarm.nextTrigger(tuesdayMorning))
    }

    @Test
    fun oneTimeIgnoresDaysOff() {
        assertEquals(at(2026, 10, 27, 7, 0), Alarm(hour = 7).nextTrigger(tuesdayMorning) { true })
    }

    @Test
    fun workdaysSkipWeekend() {
        val alarm = Alarm(hour = 7, daysMask = Alarm.WORKDAYS)
        // Friday after the alarm time -> Monday.
        assertEquals(at(2026, 11, 2, 7, 0), alarm.nextTrigger(at(2026, 10, 30, 8, 0)))
    }

    @Test
    fun skipUntilSkipsNextOccurrence() {
        val first = at(2026, 10, 27, 7, 0)
        val alarm = Alarm(hour = 7, daysMask = Alarm.EVERY_DAY, skipUntil = first.toInstant().toEpochMilli())
        assertEquals(at(2026, 10, 28, 7, 0), alarm.nextTrigger(tuesdayMorning))
    }

    @Test
    fun daysOffAreSkipped() {
        val alarm = Alarm(hour = 7, daysMask = Alarm.WORKDAYS)
        // Wednesday 28 October is a Czech public holiday.
        val next = alarm.nextTrigger(at(2026, 10, 27, 8, 0)) { CzechHolidays.isHoliday(it.toLocalDate()) }
        assertEquals(at(2026, 10, 29, 7, 0), next)
    }

    @Test
    fun dstGapMovesAlarmForward() {
        // 2026-03-29 02:30 does not exist in Prague.
        val alarm = Alarm(hour = 2, minute = 30, daysMask = Alarm.EVERY_DAY)
        val next = alarm.nextTrigger(at(2026, 3, 29, 0, 0))!!
        assertEquals(LocalDate.of(2026, 3, 29), next.toLocalDate())
        assertEquals(3, next.hour)
    }

    @Test
    fun czechHolidays() {
        assertEquals(LocalDate.of(2026, 4, 5), CzechHolidays.easterSunday(2026))
        assertEquals(LocalDate.of(2025, 4, 20), CzechHolidays.easterSunday(2025))
        assertEquals(LocalDate.of(2024, 3, 31), CzechHolidays.easterSunday(2024))
        assertTrue(CzechHolidays.isHoliday(LocalDate.of(2026, 4, 3))) // Good Friday
        assertTrue(CzechHolidays.isHoliday(LocalDate.of(2026, 4, 6))) // Easter Monday
        assertTrue(CzechHolidays.isHoliday(LocalDate.of(2026, 11, 17)))
        assertFalse(CzechHolidays.isHoliday(LocalDate.of(2026, 4, 5))) // Sunday itself is not listed
        assertFalse(CzechHolidays.isHoliday(LocalDate.of(2026, 10, 27)))
    }

    @Test
    fun upcomingHolidaysCrossYearBoundary() {
        val upcoming = CzechHolidays.upcoming(LocalDate.of(2026, 12, 26), 3)
        assertEquals(
            listOf(LocalDate.of(2026, 12, 26), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 26)),
            upcoming.map { it.first },
        )
        assertEquals(CzechHoliday.GOOD_FRIDAY, upcoming[2].second)
    }
}
