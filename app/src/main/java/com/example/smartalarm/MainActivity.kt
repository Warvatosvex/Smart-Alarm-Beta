package com.example.smartalarm

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var cfg: AlarmConfig
    private lateinit var settings: AlarmSettingsView
    private lateinit var btnTime: Button
    private lateinit var tvStatus: TextView
    private lateinit var btnPermOverlay: Button
    private lateinit var btnPermExact: Button
    private lateinit var btnPermBattery: Button
    private lateinit var btnPermFullScreen: Button
    private lateinit var btnPermNotif: Button

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshPerms() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = findViewById(R.id.settings)
        btnTime = findViewById(R.id.btnTime)
        tvStatus = findViewById(R.id.tvStatus)
        btnPermOverlay = findViewById(R.id.btnPermOverlay)
        btnPermExact = findViewById(R.id.btnPermExact)
        btnPermBattery = findViewById(R.id.btnPermBattery)
        btnPermFullScreen = findViewById(R.id.btnPermFullScreen)
        btnPermNotif = findViewById(R.id.btnPermNotif)

        cfg = Store.loadConfig(this, "main")
        settings.bind(cfg)
        settings.onChange = { Store.saveConfig(this, "main", cfg) }
        Store.saveConfig(this, "main", cfg)

        updateTimeButton()
        btnTime.setOnClickListener {
            val m = Store.getInt(this, "main_time", 7 * 60)
            TimePickerDialog(this, { _, h, min ->
                Store.setInt(this, "main_time", h * 60 + min)
                updateTimeButton()
            }, m / 60, m % 60, true).show()
        }

        btnPermOverlay.setOnClickListener { startActivity(Perms.overlayIntent(this)) }
        btnPermExact.setOnClickListener { startActivity(Perms.exactIntent(this)) }
        btnPermBattery.setOnClickListener { startActivity(Perms.batteryIntent(this)) }
        btnPermFullScreen.setOnClickListener { startActivity(Perms.fullScreenIntent(this)) }
        btnPermNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        findViewById<Button>(R.id.btnSet).setOnClickListener { setAlarm() }
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            Scheduler.cancelKind(this, "main")
            refreshStatus()
            Toast.makeText(this, "Будильник отменён", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnSeries).setOnClickListener {
            startActivity(Intent(this, SeriesActivity::class.java))
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            ContextCompat.startForegroundService(this, AlarmService.startIntent(this, cfg.toJson(), true))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPerms()
        refreshStatus()
    }

    private fun updateTimeButton() {
        val m = Store.getInt(this, "main_time", 7 * 60)
        btnTime.text = "Время: %02d:%02d".format(m / 60, m % 60)
    }

    private fun mark(ok: Boolean) = if (ok) "✓" else "✗"

    private fun refreshPerms() {
        btnPermOverlay.text = "${mark(Perms.overlay(this))} Поверх других окон"
        btnPermExact.text = "${mark(Perms.exact(this))} Точные будильники"
        btnPermBattery.text = "${mark(Perms.battery(this))} Не оптимизировать батарею"
        btnPermFullScreen.text = "${mark(Perms.fullScreen(this))} Полноэкранные уведомления"
        btnPermNotif.text = "${mark(Perms.notifications(this))} Уведомления"
    }

    private fun refreshStatus() {
        val list = Scheduler.pending(this, "main")
        tvStatus.text = if (list.isEmpty()) "Будильник не установлен" else {
            val f = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            "Установлен на ${f.format(Date(list.first().triggerAt))}"
        }
    }

    private fun setAlarm() {
        if (!Perms.exact(this)) {
            Toast.makeText(this, "Выдайте разрешение «Точные будильники»", Toast.LENGTH_LONG).show()
            startActivity(Perms.exactIntent(this))
            return
        }
        val m = Store.getInt(this, "main_time", 7 * 60)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, m / 60)
            set(Calendar.MINUTE, m % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        Scheduler.scheduleAll(this, "main", listOf(ScheduledAlarm(1, cal.timeInMillis, cfg.toJson())))
        refreshStatus()
        Toast.makeText(this, "Будильник установлен", Toast.LENGTH_SHORT).show()
    }
}