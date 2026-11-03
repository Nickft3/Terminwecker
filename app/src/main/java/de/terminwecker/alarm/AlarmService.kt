package de.terminwecker.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.terminwecker.Notifications
import de.terminwecker.R
import de.terminwecker.data.AlarmPayload
import de.terminwecker.data.AppSettings
import de.terminwecker.data.RuleStore
import de.terminwecker.ui.Fmt
import de.terminwecker.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Vordergrunddienst, der klingelt, vibriert und den Weckerbildschirm über dem Sperrbildschirm zeigt. */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { onTimeout() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val payload = AlarmPayload.from(intent)
                if (payload != null) startRinging(payload) else stopIfIdle()
            }
            ACTION_SNOOZE -> {
                _current.value?.let { AlarmScheduler.snooze(this, it, settings().snoozeMinutes) }
                stopRinging()
            }
            ACTION_DISMISS -> stopRinging()
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun settings(): AppSettings = RuleStore.get(this).settings.value

    private fun startRinging(payload: AlarmPayload) {
        stopPlayback()
        _current.value = payload
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, Notifications.ID_RINGING, buildNotification(payload), type)

        val ringMinutes = settings().ringMinutes
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Terminwecker:ringing")
            .apply { acquire((ringMinutes + 1) * 60_000L) }

        playSound()
        vibrate()
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, ringMinutes * 60_000L)
    }

    private fun buildNotification(payload: AlarmPayload): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0, AlarmActivity.intent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getService(
            this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_SNOOZE), PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getService(
            this, 2, Intent(this, AlarmService::class.java).setAction(ACTION_DISMISS), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(payload.label.ifBlank { getString(R.string.alarm_default_label) })
            .setContentText(describe(this, payload))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, getString(R.string.snooze_button, settings().snoozeMinutes), snooze)
            .addAction(0, getString(R.string.action_dismiss), dismiss)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun playSound() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val am = getSystemService(AudioManager::class.java)
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .build()
            .also { am.requestAudioFocus(it) }

        val builtin = Uri.parse(
            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://$packageName/${R.raw.fallback_alarm}",
        )
        val chosen = settings().soundUri
        val candidates = buildList {
            when (chosen) {
                AppSettings.BUILTIN_SOUND -> add(builtin)
                null -> {}
                else -> add(Uri.parse(chosen))
            }
            RingtoneManager.getActualDefaultRingtoneUri(this@AlarmService, RingtoneManager.TYPE_ALARM)?.let(::add)
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
            add(builtin)
        }
        for (uri in candidates) {
            player = try {
                MediaPlayer().apply {
                    setAudioAttributes(attrs)
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ton $uri nicht abspielbar", e)
                null
            }
            if (player != null) break
        }
    }

    private fun vibrate() {
        if (!settings().vibrate) return
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (!v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
        vibrator = v
    }

    private fun onTimeout() {
        val payload = _current.value
        if (payload != null) {
            val open = PendingIntent.getActivity(
                this, 3,
                Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, MainActivity.TAB_ALARMS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = NotificationCompat.Builder(this, Notifications.CHANNEL_INFO)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(getString(R.string.missed_alarm, payload.label))
                .setContentText(describe(this, payload))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try {
                getSystemService(NotificationManager::class.java).notify(Notifications.ID_MISSED, n)
            } catch (e: SecurityException) {
                Log.w(TAG, "Benachrichtigung nicht erlaubt", e)
            }
        }
        stopRinging()
    }

    private fun stopPlayback() {
        handler.removeCallbacks(timeout)
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        focusRequest?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun stopRinging() {
        stopPlayback()
        _current.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopIfIdle() {
        if (_current.value == null) stopSelf()
    }

    override fun onDestroy() {
        stopPlayback()
        _current.value = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AlarmService"
        private const val ACTION_START = "de.terminwecker.action.START"
        const val ACTION_SNOOZE = "de.terminwecker.action.SNOOZE"
        const val ACTION_DISMISS = "de.terminwecker.action.DISMISS"

        private val _current = MutableStateFlow<AlarmPayload?>(null)

        /** Der gerade klingelnde Wecker, null wenn still. */
        val current: StateFlow<AlarmPayload?> = _current.asStateFlow()

        fun start(context: Context, payload: AlarmPayload) {
            val intent = payload.writeTo(Intent(context, AlarmService::class.java).setAction(ACTION_START))
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }

        fun describe(context: Context, payload: AlarmPayload): String =
            if (payload.ruleId == AlarmPayload.TEST_RULE_ID) payload.eventTitle
            else context.getString(R.string.alarm_event, payload.eventTitle, Fmt.eventMoment(payload.eventBegin, payload.allDay))
    }
}
