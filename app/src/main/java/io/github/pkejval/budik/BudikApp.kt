package io.github.pkejval.budik

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import io.github.pkejval.budik.alarm.DaysOff
import io.github.pkejval.budik.data.AppDatabase
import io.github.pkejval.budik.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BudikApp : Application() {
    val db by lazy { AppDatabase.create(this) }
    val settings by lazy { Settings(this) }
    val daysOff by lazy { DaysOff(this, settings) }

    /** Outlives screens and services; used for work that must finish (event delivery, DB writes). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RINGING, getString(R.string.channel_ringing), NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null); enableVibration(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MISSED, getString(R.string.channel_missed), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    companion object {
        const val CHANNEL_RINGING = "ringing"
        const val CHANNEL_MISSED = "missed"
    }
}

val Context.app get() = applicationContext as BudikApp
