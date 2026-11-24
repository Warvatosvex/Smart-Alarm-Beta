package com.example.smartalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import kotlin.math.roundToInt

class AlarmService : Service() {
    companion object {
        const val ACTION_STOP = "com.example.smartalarm.STOP"
        const val EXTRA_CONFIG = "config"
        const val EXTRA_TEST = "test"
        private const val CHANNEL_ID = "alarm_ring"
        private const val NOTIF_ID = 42

        fun startIntent(ctx: Context, json: String, test: Boolean) =
            Intent(ctx, AlarmService::class.java)
                .putExtra(EXTRA_CONFIG, json)
                .putExtra(EXTRA_TEST, test)

        fun stopIntent(ctx: Context) = Intent(ctx, AlarmService::class.java).setAction(ACTION_STOP)
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var am: AudioManager
    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringing = false
    private var targetIndex = -1
    private var originalVolume = -1
    private var observerRegistered = false

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            applyVolume()
        }
    }

    private val enforcer = object : Runnable {
        override fun run() {
            applyVolume()
            handler.postDelayed(this, 250)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        val json = intent?.getStringExtra(EXTRA_CONFIG)
        if (json == null) {
            shutdown()
            return START_NOT_STICKY
        }
        val test = intent.getBooleanExtra(EXTRA_TEST, false)

        val activityIntent = Intent(this, AlarmRingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_CONFIG, json)
            .putExtra(EXTRA_TEST, test)
        val pi = PendingIntent.getActivity(
            this, 0, activityIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = buildNotification(pi, test)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        if (ringing) return START_NOT_STICKY
        ringing = true

        val cfg = AlarmConfig.fromJson(json)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smartalarm:ring").apply {
            acquire(30 * 60 * 1000L)
        }
        lockVolume(cfg.volumePercent)
        playSound(cfg)
        try {
            startActivity(activityIntent)
        } catch (e: Exception) {
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(pi: PendingIntent, test: Boolean): android.app.Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL_ID, "Будильник", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (test) "Тест будильника" else "Будильник")
            .setContentText("Решите задания, чтобы отключить")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
    }

    private fun lockVolume(percent: Int) {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val min = if (Build.VERSION.SDK_INT >= 28) am.getStreamMinVolume(AudioManager.STREAM_ALARM) else 0
        originalVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        targetIndex = (percent / 100.0 * max).roundToInt().coerceIn(min, max)
        applyVolume()
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        observerRegistered = true
        handler.postDelayed(enforcer, 250)
    }

    private fun applyVolume() {
        if (targetIndex < 0) return
        try {
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) != targetIndex) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, targetIndex, 0)
            }
        } catch (e: SecurityException) {
        }
    }

    private fun playSound(cfg: AlarmConfig) {
        val uri: Uri = when (cfg.soundUri) {
            AlarmConfig.RANDOM -> SoundHelper.loadAll(this).randomOrNull()?.let { Uri.parse(it.uri) }
                ?: SoundHelper.defaultAlarmUri()
            "" -> SoundHelper.defaultAlarmUri()
            else -> Uri.parse(cfg.soundUri)
        }
        if (!tryPlay(uri)) tryPlay(SoundHelper.defaultAlarmUri())
    }

    private fun tryPlay(uri: Uri): Boolean {
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun shutdown() {
        ringing = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(enforcer)
        if (observerRegistered) {
            contentResolver.unregisterContentObserver(observer)
            observerRegistered = false
        }
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        if (originalVolume >= 0) {
            try {
                am.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            } catch (e: SecurityException) {
            }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}