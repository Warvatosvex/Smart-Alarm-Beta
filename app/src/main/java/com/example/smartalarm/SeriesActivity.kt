package com.example.smartalarm

import android.app.TimePickerDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class SeriesActivity : AppCompatActivity() {
    private lateinit var cfg: AlarmConfig
    private lateinit var btnStart: Button
    private lateinit var btnEnd: Button
    private lateinit var etInterval: EditText
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_series)

        btnStart = findViewById(R.id.btnStart)
        btnEnd = findViewById(R.id.btnEnd)
        etInterval = findViewById(R.id.etInterval)
        tvStatus = findViewById(R.id.tvSeriesStatus)
        etInterval.inputType = InputType.TYPE_CLASS_NUMBER
        etInterval.setText(Store.getInt(this, "series_interval", 10).toString())

        cfg = Store.loadConfig(this, "series")
        val settings = findViewById<AlarmSettingsView>(R.id.seriesSettings)
        settings.bind(cfg)
        settings.onChange = { Store.saveConfig(this, "series", cfg) }
        Store.saveConfig(this, "series", cfg)

        updateButtons()
        btnStart.setOnClickListener { pickTime("series_start", 6 * 60) }
        btnEnd.setOnClickListener { pickTime("series_end", 7 * 60 + 25) }

        findViewById<Button>(R.id.btnStartSeries).setOnClickListener { startSeries() }
        findViewById<Button>(R.id.btnCancelSeries).setOnClickListener {
            Scheduler.cancelKind(this, "series")
            refreshStatus()
            Toast.makeText(this, "Серия отменена", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnTestSeries).setOnClickListener {
            ContextCompat.startForegroundService(this, AlarmService.startIntent(this, cfg.toJson(), true))
        }
        refreshStatus()
    }

    override fun onPause() {
        super.onPause()
        etInterval.text.toString().toIntOrNull()?.let { Store.setInt(this, "series_interval", it) }
    }

    private fun pickTime(key: String, def: Int) {
        val m = Store.getInt(this, key, def)
        TimePickerDialog(this, { _, h, min ->
            Store.setInt(this, key, h * 60 + min)
            updateButtons()
        }, m / 60, m % 60, true).show()
    }

    private fun updateButtons() {
        val s = Store.getInt(this, "series_start", 6 * 60)
        val e = Store.getInt(this, "series_end", 7 * 60 + 25)
        btnStart.text = "Начало: %02d:%02d".format(s / 60, s % 60)
        btnEnd.text = "Конец: %02d:%02d".format(e / 60, e % 60)
    }

    private fun refreshStatus() {
        val list = Scheduler.pending(this, "series")
        tvStatus.text = if (list.isEmpty()) "Серия не запущена" else {
            val f = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())
            "Запланировано ${list.size}: ${f.format(Date(list.first().triggerAt))} — ${f.format(Date(list.last().triggerAt))}"
        }
    }

    private fun startSeries() {
        if (!Perms.exact(this)) {
            Toast.makeText(this, "Выдайте разрешение «Точные будильники»", Toast.LENGTH_LONG).show()
            startActivity(Perms.exactIntent(this))
            return
        }
        val interval = etInterval.text.toString().toIntOrNull()
        if (interval == null || interval < 1) {
            Toast.makeText(this, "Введите интервал в минутах (≥ 1)", Toast.LENGTH_SHORT).show()
            return
        }
        Store.setInt(this, "series_interval", interval)
        val startMin = Store.getInt(this, "series_start", 6 * 60)
        var endMin = Store.getInt(this, "series_end", 7 * 60 + 25)
        if (endMin < startMin) endMin += 1440
        val minutes = generateSequence(startMin) { it + interval }.takeWhile { it <= endMin }.toList()
        if (minutes.size > 300) {
            Toast.makeText(this, "Слишком много будильников (макс. 300)", Toast.LENGTH_SHORT).show()
            return
        }
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val dayMs = 86_400_000L
        val all = minutes.map { midnight + it * 60_000L }
        val now = System.currentTimeMillis()
        var times = all.filter { it > now }
        if (times.isEmpty()) times = all.map { it + dayMs }
        val json = cfg.toJson()
        Scheduler.scheduleAll(this, "series", times.mapIndexed { i, t -> ScheduledAlarm(1000 + i, t, json) })
        refreshStatus()
        Toast.makeText(this, "Серия запущена: ${times.size} будильников", Toast.LENGTH_SHORT).show()
    }
}