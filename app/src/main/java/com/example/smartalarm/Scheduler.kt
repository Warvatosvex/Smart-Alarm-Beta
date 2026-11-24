package com.example.smartalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

data class ScheduledAlarm(val code: Int, val triggerAt: Long, val configJson: String)

object Scheduler {
    private val kinds = listOf("main", "series")

    private fun alarmManager(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent(c: Context, code: Int, json: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java)
            .putExtra(AlarmService.EXTRA_CONFIG, json)
            .putExtra(AlarmService.EXTRA_TEST, false)
        return PendingIntent.getBroadcast(
            c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setOne(c: Context, a: ScheduledAlarm) {
        alarmManager(c).setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, a.triggerAt, pendingIntent(c, a.code, a.configJson)
        )
    }

    private fun load(c: Context, kind: String): List<ScheduledAlarm> {
        val s = Store.getString(c, "sched_$kind") ?: return emptyList()
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ScheduledAlarm(o.getInt("code"), o.getLong("t"), o.getString("cfg"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(c: Context, kind: String, list: List<ScheduledAlarm>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("code", it.code).put("t", it.triggerAt).put("cfg", it.configJson))
        }
        Store.setString(c, "sched_$kind", arr.toString())
    }

    fun scheduleAll(c: Context, kind: String, list: List<ScheduledAlarm>) {
        cancelKind(c, kind)
        list.forEach { setOne(c, it) }
        save(c, kind, list)
    }

    fun cancelKind(c: Context, kind: String) {
        val am = alarmManager(c)
        load(c, kind).forEach { am.cancel(pendingIntent(c, it.code, it.configJson)) }
        save(c, kind, emptyList())
    }

    fun pending(c: Context, kind: String): List<ScheduledAlarm> {
        val now = System.currentTimeMillis()
        return load(c, kind).filter { it.triggerAt > now }
    }

    fun restore(c: Context) {
        kinds.forEach { kind ->
            val future = pending(c, kind)
            try {
                future.forEach { setOne(c, it) }
            } catch (e: SecurityException) {
                return
            }
            save(c, kind, future)
        }
    }
}