package io.github.pkejval.budik.ui

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.format.DateFormat
import android.widget.Toast
import io.github.pkejval.budik.R
import io.github.pkejval.budik.alarm.AlarmScheduler
import io.github.pkejval.budik.alarm.DayOffReason
import io.github.pkejval.budik.alarm.SkippedRange
import io.github.pkejval.budik.data.Alarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

fun formatTime(context: Context, hour: Int, minute: Int): String =
    if (DateFormat.is24HourFormat(context)) "%d:%02d".format(hour, minute)
    else "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")

fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun dayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun repeatSummary(context: Context, alarm: Alarm): String = when (alarm.daysMask) {
    0 -> alarm.date?.let(::formatDate) ?: context.getString(R.string.once)
    Alarm.EVERY_DAY -> context.getString(R.string.every_day)
    Alarm.WORKDAYS -> context.getString(R.string.workdays)
    0b1100000 -> context.getString(R.string.weekend)
    else -> DayOfWeek.entries.filter(alarm::ringsOn).joinToString(", ", transform = ::dayShort)
}

private fun formatDay(day: LocalDate) = "${dayShort(day.dayOfWeek)} ${day.format(DateTimeFormatter.ofPattern("d. M."))}"

fun formatNext(context: Context, at: ZonedDateTime): String {
    val day = at.toLocalDate()
    val today = LocalDate.now()
    val dayText = when (day) {
        today -> context.getString(R.string.today)
        today.plusDays(1) -> context.getString(R.string.tomorrow)
        else -> formatDay(day)
    }
    return "$dayText ${formatTime(context, at.hour, at.minute)}"
}

/** "St 28. 10. (svátek), Po 2. 11. – Pá 6. 11. (dovolená)" */
fun formatSkipped(context: Context, ranges: List<SkippedRange>) = ranges.joinToString(", ") { r ->
    val dates = if (r.from == r.to) formatDay(r.from) else "${formatDay(r.from)} – ${formatDay(r.to)}"
    val reason = context.getString(
        when (r.reason) {
            DayOffReason.HOLIDAY -> R.string.reason_holiday
            DayOffReason.VACATION -> R.string.reason_vacation
        },
    )
    "$dates ($reason)"
}

fun formatRingsIn(context: Context, at: ZonedDateTime): String {
    val minutes = Duration.between(ZonedDateTime.now(), at).toMinutes().coerceAtLeast(0) + 1
    val d = minutes / (24 * 60)
    val h = minutes / 60 % 24
    val m = minutes % 60
    val text = when {
        d > 0 -> context.getString(R.string.duration_dh, d, h)
        h > 0 -> context.getString(R.string.duration_hm, h, m)
        else -> context.getString(R.string.duration_m, m)
    }
    return context.getString(R.string.rings_in, text)
}

/** Toast with the time until the alarm really rings. */
suspend fun announceNext(context: Context, alarm: Alarm) {
    val at = withContext(Dispatchers.IO) { AlarmScheduler.expectedTrigger(context, alarm) } ?: return
    Toast.makeText(context, formatRingsIn(context, at), Toast.LENGTH_SHORT).show()
}

fun ringtoneTitle(context: Context, uri: String?): String {
    if (uri == null) return context.getString(R.string.default_sound)
    val parsed = Uri.parse(uri)
    return runCatching {
        if (DocumentsContract.isDocumentUri(context, parsed)) {
            context.contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } else {
            RingtoneManager.getRingtone(context, parsed)?.getTitle(context)
        }
    }.getOrNull() ?: uri.substringAfterLast('/')
}
