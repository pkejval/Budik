package io.github.pkejval.budik.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.pkejval.budik.app
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        val pending = goAsync()
        context.app.scope.launch {
            try {
                val alarm = context.app.db.alarms().get(id) ?: return@launch
                if (!alarm.enabled) return@launch
                AlarmScheduler.onFired(context, alarm)
                if (alarm.isRepeating && alarm.skipOnDaysOff && context.app.daysOff.isDayOff(ZonedDateTime.now())) {
                    return@launch
                }
                ContextCompat.startForegroundService(context, AlarmService.startIntent(context, id))
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
