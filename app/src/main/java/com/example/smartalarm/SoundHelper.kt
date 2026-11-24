package com.example.smartalarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.widget.Toast
import kotlin.math.roundToInt

data class SoundItem(val title: String, val uri: String)

object SoundHelper {
    private var cache: List<SoundItem>? = null

    fun defaultAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun loadAll(ctx: Context): List<SoundItem> {
        cache?.let { return it }
        val result = LinkedHashMap<String, SoundItem>()
        val types = listOf(
            RingtoneManager.TYPE_ALARM to "Будильник",
            RingtoneManager.TYPE_RINGTONE to "Рингтон",
            RingtoneManager.TYPE_NOTIFICATION to "Уведомление"
        )
        for ((type, label) in types) {
            try {
                val rm = RingtoneManager(ctx)
                rm.setType(type)
                val c = rm.cursor
                while (c.moveToNext()) {
                    val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                    val uri = rm.getRingtoneUri(c.position).toString()
                    if (!result.containsKey(uri)) result[uri] = SoundItem("[$label] $title", uri)
                }
            } catch (e: Exception) {
            }
        }
        val list = result.values.toList()
        cache = list
        return list
    }

    class Previewer(private val ctx: Context) {
        private var player: MediaPlayer? = null
        private var savedVolume = -1
        private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        fun play(uri: String, percent: Int) {
            stop()
            try {
                savedVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                am.setStreamVolume(AudioManager.STREAM_ALARM, (percent / 100.0 * max).roundToInt(), 0)
            } catch (e: SecurityException) {
            }
            val target = if (uri.isBlank()) defaultAlarmUri() else Uri.parse(uri)
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                mp.setDataSource(ctx, target)
                mp.isLooping = true
                mp.setOnErrorListener { _, _, _ -> stop(); true }
                mp.prepare()
                mp.start()
                player = mp
            } catch (e: Exception) {
                Toast.makeText(ctx, "Не удалось воспроизвести звук", Toast.LENGTH_SHORT).show()
                stop()
            }
        }

        fun stop() {
            try {
                player?.stop()
            } catch (e: Exception) {
            }
            player?.release()
            player = null
            if (savedVolume >= 0) {
                try {
                    am.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
                } catch (e: SecurityException) {
                }
                savedVolume = -1
            }
        }
    }
}