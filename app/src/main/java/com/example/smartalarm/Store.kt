package com.example.smartalarm

import android.content.Context

object Store {
    private fun prefs(c: Context) = c.getSharedPreferences("smartalarm", Context.MODE_PRIVATE)

    fun loadConfig(c: Context, key: String): AlarmConfig =
        AlarmConfig.fromJson(prefs(c).getString("cfg_$key", null))

    fun saveConfig(c: Context, key: String, cfg: AlarmConfig) {
        prefs(c).edit().putString("cfg_$key", cfg.toJson()).apply()
    }

    fun getInt(c: Context, key: String, def: Int): Int = prefs(c).getInt(key, def)

    fun setInt(c: Context, key: String, value: Int) {
        prefs(c).edit().putInt(key, value).apply()
    }

    fun getString(c: Context, key: String): String? = prefs(c).getString(key, null)

    fun setString(c: Context, key: String, value: String?) {
        prefs(c).edit().putString(key, value).apply()
    }
}