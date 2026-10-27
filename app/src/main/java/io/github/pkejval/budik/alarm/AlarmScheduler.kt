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
    const val ACTION_PREWARM = "io.github.pkejval.budik.PREWARM"
    const val PREWARM_LEAD_MS = 30_000L
    private const val PREWARM_REQUEST_FLAG = 0x40000000

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

    /** Days off that silence the alarm before its next real ring. Queries the calendar. */
    fun skippedBeforeNext(context: Context, alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()) =
        if (!alarm.enabled || !alarm.skipOnDaysOff) emptyList()
        else alarm.skippedBeforeNext(after) { context.app.daysOff.reason(it) }

    fun schedule(context: Context, alarm: Alarm, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
        val am = context.getSystemService(AlarmManager::class.java)
        val operation = operation(context, alarm.id)
        val prewarm = prewarmOperation(context, alarm.id)
        val at = scheduledTrigger(context, alarm, after)
        if (at == null) {
            am.cancel(operation)
            am.cancel(prewarm)
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
        // Allow-while-idle so it also fires in Doze; the app is briefly allowed network access then.
        val prewarmAt = at.toInstant().toEpochMilli() - PREWARM_LEAD_MS
        if (prewarmAt > System.currentTimeMillis()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prewarmAt, prewarm)
        } else {
            am.cancel(prewarm)
        }
        return at
    }

    fun cancel(context: Context, alarmId: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(operation(context, alarmId))
        am.cancel(prewarmOperation(context, alarmId))
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

    /** Fires [PREWARM_LEAD_MS] before the alarm to open integration connections in advance. */
    private fun prewarmOperation(context: Context, alarmId: Long) = PendingIntent.getBroadcast(
        context,
        alarmId.toInt() xor PREWARM_REQUEST_FLAG,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_PREWARM).putExtra(EXTRA_ALARM_ID, alarmId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
