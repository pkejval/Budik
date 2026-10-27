package io.github.pkejval.budik.alarm

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import io.github.pkejval.budik.data.Settings
import java.time.LocalDate
import java.time.Month
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

enum class CzechHoliday {
    NEW_YEAR, GOOD_FRIDAY, EASTER_MONDAY, LABOUR_DAY, LIBERATION_DAY, CYRIL_METHODIUS, JAN_HUS,
    STATEHOOD_DAY, INDEPENDENCE_DAY, FREEDOM_DAY, CHRISTMAS_EVE, CHRISTMAS_DAY, ST_STEPHEN,
}

object CzechHolidays {
    fun of(year: Int): List<Pair<LocalDate, CzechHoliday>> {
        val easter = easterSunday(year)
        fun d(month: Month, day: Int) = LocalDate.of(year, month, day)
        return listOf(
            d(Month.JANUARY, 1) to CzechHoliday.NEW_YEAR,
            easter.minusDays(2) to CzechHoliday.GOOD_FRIDAY,
            easter.plusDays(1) to CzechHoliday.EASTER_MONDAY,
            d(Month.MAY, 1) to CzechHoliday.LABOUR_DAY,
            d(Month.MAY, 8) to CzechHoliday.LIBERATION_DAY,
            d(Month.JULY, 5) to CzechHoliday.CYRIL_METHODIUS,
            d(Month.JULY, 6) to CzechHoliday.JAN_HUS,
            d(Month.SEPTEMBER, 28) to CzechHoliday.STATEHOOD_DAY,
            d(Month.OCTOBER, 28) to CzechHoliday.INDEPENDENCE_DAY,
            d(Month.NOVEMBER, 17) to CzechHoliday.FREEDOM_DAY,
            d(Month.DECEMBER, 24) to CzechHoliday.CHRISTMAS_EVE,
            d(Month.DECEMBER, 25) to CzechHoliday.CHRISTMAS_DAY,
            d(Month.DECEMBER, 26) to CzechHoliday.ST_STEPHEN,
        ).sortedBy { it.first }
    }

    fun isHoliday(day: LocalDate) = of(day.year).any { it.first == day }

    fun upcoming(from: LocalDate, count: Int) =
        (of(from.year) + of(from.year + 1)).filter { !it.first.isBefore(from) }.take(count)

    /** Anonymous Gregorian algorithm. */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val dayOfMonth = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, dayOfMonth)
    }
}

enum class DayOffReason { HOLIDAY, VACATION }

data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

/** Decides whether a moment falls on a day off (public holiday or a matching calendar event). */
class DaysOff(private val context: Context, private val settings: Settings) {

    /** Holidays are deterministic, so they are safe to apply when scheduling. */
    fun isHoliday(at: ZonedDateTime) = settings.czechHolidays && CzechHolidays.isHoliday(at.toLocalDate())

    /**
     * Calendar events can change after scheduling, so they are only consulted for display
     * and at ring time; otherwise a deleted vacation event would leave the alarm silent.
     */
    fun reason(at: ZonedDateTime) = when {
        isHoliday(at) -> DayOffReason.HOLIDAY
        isCalendarDayOff(at) -> DayOffReason.VACATION
        else -> null
    }

    fun isDayOff(at: ZonedDateTime) = reason(at) != null

    fun hasCalendarPermission() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

    fun calendars(): List<CalendarInfo> {
        if (!hasCalendarPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        return context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(CalendarInfo(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "", c.getInt(3)))
                }
            }
        } ?: emptyList()
    }

    private fun isCalendarDayOff(at: ZonedDateTime): Boolean {
        val ids = settings.calendarIds
        if (ids.isEmpty() || !hasCalendarPermission()) return false
        val keywords = settings.calendarKeywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val t = at.toInstant().toEpochMilli()
        val margin = TimeUnit.DAYS.toMillis(2)
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, t - margin)
            ContentUris.appendId(it, t + margin)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.TITLE,
        )
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",")})"
        // All-day instances are stored as UTC midnights, so compare them by date, not by instant.
        val epochDay = at.toLocalDate().toEpochDay()
        return try {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { c ->
                generateSequence { if (c.moveToNext()) c else null }.any {
                    val begin = it.getLong(0)
                    val end = it.getLong(1)
                    val title = it.getString(3) ?: ""
                    val covers = if (it.getInt(2) == 1) {
                        epochDay in TimeUnit.MILLISECONDS.toDays(begin) until TimeUnit.MILLISECONDS.toDays(end)
                    } else {
                        t in begin until end
                    }
                    covers && (keywords.isEmpty() || keywords.any { k -> title.contains(k, ignoreCase = true) })
                }
            } ?: false
        } catch (e: SecurityException) {
            false
        }
    }
}
