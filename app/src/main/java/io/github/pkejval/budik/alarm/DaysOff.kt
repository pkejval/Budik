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

object CzechHolidays {
    private val fixed = setOf(
        Month.JANUARY to 1, Month.MAY to 1, Month.MAY to 8, Month.JULY to 5, Month.JULY to 6,
        Month.SEPTEMBER to 28, Month.OCTOBER to 28, Month.NOVEMBER to 17,
        Month.DECEMBER to 24, Month.DECEMBER to 25, Month.DECEMBER to 26,
    )

    fun isHoliday(day: LocalDate): Boolean {
        if (day.month to day.dayOfMonth in fixed) return true
        val easter = easterSunday(day.year)
        return day == easter.minusDays(2) || day == easter.plusDays(1) // Good Friday, Easter Monday
    }

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

data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

/** Decides whether a moment falls on a day off (public holiday or a matching calendar event). */
class DaysOff(private val context: Context, private val settings: Settings) {

    /** Holidays are deterministic, so they are safe to apply when scheduling. */
    fun isHoliday(at: ZonedDateTime) = settings.czechHolidays && CzechHolidays.isHoliday(at.toLocalDate())

    /**
     * Calendar events can change after scheduling, so they are only consulted for display
     * and at ring time; otherwise a deleted vacation event would leave the alarm silent.
     */
    fun isDayOff(at: ZonedDateTime) = isHoliday(at) || isCalendarDayOff(at)

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
