package io.github.pkejval.budik.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.pkejval.budik.BudikApp
import io.github.pkejval.budik.R
import io.github.pkejval.budik.app
import io.github.pkejval.budik.data.Alarm
import io.github.pkejval.budik.data.AlarmEvent
import io.github.pkejval.budik.integration.EventDispatcher
import io.github.pkejval.budik.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** Plays a ringing alarm and reports ringing / dismissed / missed events. */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var current: Alarm? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringJobs = mutableListOf<Job>()
    private val deliveries = mutableListOf<Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Must be foreground within a few seconds of startForegroundService().
                goForeground(null)
                val id = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
                scope.launch {
                    val alarm = withContext(Dispatchers.IO) { app.db.alarms().get(id) }
                    if (alarm == null) stopIfIdle() else ring(alarm)
                }
            }
            ACTION_DISMISS -> stop(AlarmEvent.DISMISSED)
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun ring(alarm: Alarm) {
        // A new alarm while one is ringing replaces it; the old one was not answered.
        if (current != null) stop(AlarmEvent.MISSED)
        current = alarm
        _ringing.value = alarm
        // The RINGING event was already sent by AlarmReceiver.
        goForeground(alarm)

        val timeoutMs = TimeUnit.MINUTES.toMillis(alarm.autoSilenceMinutes.toLong())
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Budik:ringing")
            .apply { acquire(timeoutMs + 60_000) }

        startSound(alarm)
        if (alarm.vibrate) startVibration()
        ringJobs += scope.launch {
            delay(timeoutMs)
            stop(AlarmEvent.MISSED)
        }
    }

    private fun startSound(alarm: Alarm) {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val audio = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()
            .also { audio.requestAudioFocus(it) }

        val candidates = listOfNotNull(
            alarm.ringtoneUri?.let(Uri::parse),
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        val mp = candidates.firstNotNullOfOrNull { uri ->
            try {
                MediaPlayer().apply {
                    setAudioAttributes(attributes)
                    setWakeMode(this@AlarmService, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                }
            } catch (e: Exception) {
                Log.w("Budik", "Cannot play $uri", e)
                null
            }
        } ?: return
        player = mp
        if (alarm.rampSeconds <= 0) {
            mp.setVolume(1f, 1f)
            mp.start()
            return
        }
        mp.setVolume(0f, 0f)
        mp.start()
        ringJobs += scope.launch {
            val steps = alarm.rampSeconds * 4
            for (step in 1..steps) {
                delay(250)
                // Quadratic curve: perceived loudness grows roughly evenly.
                val v = (step.toFloat() / steps).let { it * it }
                player?.setVolume(v, v)
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 700), 0), attributes)
    }

    private fun stop(event: AlarmEvent) {
        val alarm = current ?: return stopIfIdle()
        ringJobs.forEach { it.cancel() }
        ringJobs.clear()
        player?.run { runCatching { stop() }; release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        current = null
        _ringing.value = null

        deliveries += EventDispatcher.dispatch(this, event, alarm)
        if (event == AlarmEvent.MISSED) notifyMissed(alarm)
        stopIfIdle()
    }

    /** Stays foreground (keeping the process alive) until pending deliveries finish. */
    private fun stopIfIdle() {
        scope.launch {
            withTimeoutOrNull(DELIVERY_GRACE_MS) { deliveries.toList().forEach { it.join() } }
            deliveries.removeAll { it.isCompleted }
            if (current == null) {
                ServiceCompat.stopForeground(this@AlarmService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun goForeground(alarm: Alarm?) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, ringingNotification(alarm), type)
    }

    private fun ringingNotification(alarm: Alarm?): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0, Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getService(
            this, 1, dismissIntent(this), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, BudikApp.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alarm?.let { formatTime(it) } ?: getString(R.string.app_name))
            .setContentText(alarm?.label?.ifBlank { null } ?: getString(R.string.alarm_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, getString(R.string.dismiss), dismiss)
            .build()
    }

    private fun notifyMissed(alarm: Alarm) {
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, BudikApp.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.missed_alarm, formatTime(alarm)))
            .setContentText(alarm.label.ifBlank { null })
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(alarm.id.toInt() + 1000, notification) }
    }

    private fun formatTime(alarm: Alarm) = "%d:%02d".format(alarm.hour, alarm.minute)

    override fun onDestroy() {
        if (current != null) stop(AlarmEvent.MISSED)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_DISMISS = "dismiss"
        private const val NOTIFICATION_ID = 1
        private const val DELIVERY_GRACE_MS = 15_000L

        private val _ringing = MutableStateFlow<Alarm?>(null)
        /** The alarm currently ringing, observed by [AlarmActivity]. */
        val ringing: StateFlow<Alarm?> = _ringing.asStateFlow()

        fun startIntent(context: Context, alarmId: Long) = Intent(context, AlarmService::class.java)
            .setAction(ACTION_START)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)

        fun dismissIntent(context: Context) = Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS)
    }
}
