package io.github.pkejval.budik.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.AlarmEvent
import io.github.pkejval.budik.integration.Connections
import io.github.pkejval.budik.integration.EventDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZonedDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val prewarm = intent.action == AlarmScheduler.ACTION_PREWARM
        val pending = goAsync()
        context.app.scope.launch {
            try {
                val alarm = context.app.db.alarms().get(id) ?: return@launch
                if (!alarm.enabled) return@launch
                val ringsAt = if (prewarm) ZonedDateTime.now().plusSeconds(AlarmScheduler.PREWARM_LEAD_MS / 1000) else ZonedDateTime.now()
                val dayOff = alarm.isRepeating && alarm.skipOnDaysOff && context.app.daysOff.isDayOff(ringsAt)
                if (prewarm) {
                    // goAsync() allows ~10 s; connections that are not ready by then are opened at ring time.
                    if (!dayOff) withTimeoutOrNull(8_000) { Connections.prewarm(context) }
                    return@launch
                }
                if (!dayOff) {
                    // Sent before the service even starts, to report the event as early as possible.
                    EventDispatcher.dispatch(context, AlarmEvent.RINGING, alarm)
                    try {
                        ContextCompat.startForegroundService(context, AlarmService.startIntent(context, id))
                    } catch (e: IllegalStateException) {
                        // Still plan the next occurrence below.
                        Log.e("Budik", "Cannot start ringing service", e)
                    }
                }
                AlarmScheduler.onFired(context, alarm)
            } finally {
                pending.finish()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.app.scope.launch {
            try {
                AlarmScheduler.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
