package io.github.pkejval.budik.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.Alarm
import io.github.pkejval.budik.ui.MainActivity
import java.time.ZonedDateTime

/** Owns the AlarmManager registrations and every change to alarms that affects them. */
object AlarmScheduler {
    const val EXTRA_ALARM_ID = "alarm_id"

    fun canScheduleExact(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** Next time AlarmManager should fire; calendar days off are checked later, at ring time. */
    fun scheduledTrigger(context: Context, alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()) =
        if (!alarm.enabled) null
        else alarm.nextTrigger(after) { alarm.skipOnDaysOff && context.app.daysOff.isHoliday(it) }

    /** Next time the alarm will actually ring, including calendar days off. Queries the calendar. */
    fun expectedTrigger(context: Context, alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()) =
        if (!alarm.enabled) null
        else alarm.nextTrigger(after) { alarm.skipOnDaysOff && context.app.daysOff.isDayOff(it) }

    fun schedule(context: Context, alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = operation(context, alarm.id)
        val at = scheduledTrigger(context, alarm, after)
        if (at == null) {
            am.cancel(operation)
            return null
        }
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at.toInstant().toEpochMilli(), show), operation)
        } catch (e: SecurityException) {
            Log.e("Budik", "Exact alarm permission missing", e)
            return null
        }
        return at
    }

    fun cancel(context: Context, alarmId: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(operation(context, alarmId))
    }

    suspend fun rescheduleAll(context: Context) {
        context.app.db.alarms().all().forEach { schedule(context, it) }
    }

    // --- mutations ---

    suspend fun save(context: Context, alarm: Alarm): Pair<Alarm, ZonedDateTime?> {
        val id = context.app.db.alarms().upsert(alarm)
        val saved = alarm.copy(id = id)
        return saved to schedule(context, saved)
    }

    suspend fun delete(context: Context, alarm: Alarm) {
        cancel(context, alarm.id)
        context.app.db.alarms().delete(alarm)
    }

    suspend fun duplicate(context: Context, alarm: Alarm) = save(context, alarm.copy(id = 0))

    /** Skips the next real ring (as shown to the user), or undoes a previous skip. */
    suspend fun toggleSkipNext(context: Context, alarm: Alarm): Pair<Alarm, ZonedDateTime?> {
        val skipped = alarm.skipUntil > System.currentTimeMillis()
        val skipUntil = if (skipped) 0 else expectedTrigger(context, alarm)?.toInstant()?.toEpochMilli() ?: 0
        return save(context, alarm.copy(skipUntil = skipUntil))
    }

    /** Called when an alarm fires: plans its next occurrence, or disables a one-time alarm. */
    suspend fun onFired(context: Context, alarm: Alarm) {
        if (alarm.isRepeating) {
            // A minute of margin so an alarm delivered marginally early is not re-armed for the same time.
            schedule(context, alarm, ZonedDateTime.now().plusMinutes(1))
        } else {
            context.app.db.alarms().upsert(alarm.copy(enabled = false))
        }
    }

    private fun operation(context: Context, alarmId: Long) = PendingIntent.getBroadcast(
        context,
        alarmId.toInt(),
        Intent(context, AlarmReceiver::class.java).putExtra(EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
