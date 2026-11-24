package com.example.smartalarm

import org.json.JSONArray
import org.json.JSONObject

data class AlarmConfig(
    var soundUri: String = "",
    var volumePercent: Int = 70,
    var difficulty: Int = 3,
    var tasks: MutableList<String> = mutableListOf(TaskType.MATH.name, TaskType.CAESAR.name),
    var randomEachRing: Boolean = false,
    var randomCount: Int = 3
) {
    fun toJson(): String {
        val o = JSONObject()
        o.put("soundUri", soundUri)
        o.put("volumePercent", volumePercent)
        o.put("difficulty", difficulty)
        o.put("tasks", JSONArray(tasks))
        o.put("randomEachRing", randomEachRing)
        o.put("randomCount", randomCount)
        return o.toString()
    }

    companion object {
        const val RANDOM = "random"

        fun fromJson(s: String?): AlarmConfig {
            if (s.isNullOrBlank()) return AlarmConfig()
            return try {
                val o = JSONObject(s)
                val arr = o.optJSONArray("tasks") ?: JSONArray()
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) list.add(arr.getString(i))
                AlarmConfig(
                    soundUri = o.optString("soundUri", ""),
                    volumePercent = o.optInt("volumePercent", 70).coerceIn(0, 100),
                    difficulty = o.optInt("difficulty", 3).coerceIn(0, 10),
                    tasks = list,
                    randomEachRing = o.optBoolean("randomEachRing", false),
                    randomCount = o.optInt("randomCount", 3).coerceIn(1, 10)
                )
            } catch (e: Exception) {
                AlarmConfig()
            }
        }
    }
}