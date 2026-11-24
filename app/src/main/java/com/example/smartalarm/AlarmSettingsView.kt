package com.example.smartalarm

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

class AlarmSettingsView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : LinearLayout(ctx, attrs) {

    var onChange: (() -> Unit)? = null

    private var cfg = AlarmConfig()
    private var binding = false

    private val items: List<SoundItem>
    private val previewer = SoundHelper.Previewer(ctx)

    private val spSound: Spinner
    private val tvVolume: TextView
    private val sbVolume: SeekBar
    private val tvDifficulty: TextView
    private val sbDifficulty: SeekBar
    private val llTasks: LinearLayout
    private val cbRandomEach: CheckBox

    init {
        orientation = VERTICAL
        LayoutInflater.from(ctx).inflate(R.layout.view_alarm_settings, this, true)

        spSound = findViewById(R.id.spSound)
        tvVolume = findViewById(R.id.tvVolume)
        sbVolume = findViewById(R.id.sbVolume)
        tvDifficulty = findViewById(R.id.tvDifficulty)
        sbDifficulty = findViewById(R.id.sbDifficulty)
        llTasks = findViewById(R.id.llTasks)
        cbRandomEach = findViewById(R.id.cbRandomEach)

        val sounds = SoundHelper.loadAll(ctx).ifEmpty {
            listOf(SoundItem("Системный по умолчанию", SoundHelper.defaultAlarmUri().toString()))
        }
        items = listOf(SoundItem("Рандомный звук", AlarmConfig.RANDOM)) + sounds

        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, items.map { it.title })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spSound.adapter = adapter
        spSound.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (binding) return
                cfg.soundUri = items[pos].uri
                onChange?.invoke()
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btnPreview).setOnClickListener {
            val item = items[spSound.selectedItemPosition]
            if (item.uri == AlarmConfig.RANDOM) {
                val pick = sounds.random()
                Toast.makeText(ctx, "Случайный: ${pick.title}", Toast.LENGTH_SHORT).show()
                previewer.play(pick.uri, cfg.volumePercent)
            } else {
                previewer.play(item.uri, cfg.volumePercent)
            }
        }
        findViewById<Button>(R.id.btnStopPreview).setOnClickListener { previewer.stop() }

        sbVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) setVolume(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        findViewById<Button>(R.id.btnVolMinus).setOnClickListener { setVolume((cfg.volumePercent - 5).coerceAtLeast(0)) }
        findViewById<Button>(R.id.btnVolPlus).setOnClickListener { setVolume((cfg.volumePercent + 5).coerceAtMost(100)) }

        sbDifficulty.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) setDifficulty(p)
            }

            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        findViewById<Button>(R.id.btnDiffMinus).setOnClickListener { setDifficulty((cfg.difficulty - 1).coerceAtLeast(0)) }
        findViewById<Button>(R.id.btnDiffPlus).setOnClickListener { setDifficulty((cfg.difficulty + 1).coerceAtMost(10)) }

        findViewById<Button>(R.id.btnAddTask).setOnClickListener {
            val types = TaskType.values()
            AlertDialog.Builder(ctx)
                .setTitle("Добавить задание")
                .setItems(types.map { it.title }.toTypedArray()) { _, which ->
                    cfg.tasks.add(types[which].name)
                    renderTasks()
                    onChange?.invoke()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        findViewById<Button>(R.id.btnRandomTasks).setOnClickListener {
            val picker = NumberPicker(ctx).apply {
                minValue = 1
                maxValue = 10
                value = cfg.randomCount
            }
            AlertDialog.Builder(ctx)
                .setTitle("Сколько заданий сгенерировать?")
                .setView(picker)
                .setPositiveButton("Сгенерировать") { _, _ ->
                    cfg.randomCount = picker.value
                    cfg.tasks = TaskGenerator.randomTypes(picker.value)
                    renderTasks()
                    updateRandomLabel()
                    onChange?.invoke()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }

        cbRandomEach.setOnCheckedChangeListener { _, checked ->
            if (binding) return@setOnCheckedChangeListener
            cfg.randomEachRing = checked
            onChange?.invoke()
        }
    }

    fun bind(c: AlarmConfig) {
        cfg = c
        binding = true
        val idx = items.indexOfFirst { it.uri == c.soundUri }
        spSound.setSelection(if (idx >= 0) idx else 1.coerceAtMost(items.size - 1))
        if (idx < 0) c.soundUri = items[spSound.selectedItemPosition.coerceAtLeast(0)].uri
        sbVolume.progress = c.volumePercent
        tvVolume.text = "Громкость: ${c.volumePercent}%"
        sbDifficulty.progress = c.difficulty
        tvDifficulty.text = difficultyText(c.difficulty)
        cbRandomEach.isChecked = c.randomEachRing
        updateRandomLabel()
        renderTasks()
        binding = false
    }

    private fun updateRandomLabel() {
        cbRandomEach.text = "Случайные задания при каждом звонке (количество: ${cfg.randomCount})"
    }

    private fun setVolume(v: Int) {
        cfg.volumePercent = v
        sbVolume.progress = v
        tvVolume.text = "Громкость: $v%"
        onChange?.invoke()
    }

    private fun setDifficulty(v: Int) {
        cfg.difficulty = v
        sbDifficulty.progress = v
        tvDifficulty.text = difficultyText(v)
        onChange?.invoke()
    }

    private fun difficultyText(v: Int) = "Сложность: $v / 10 " + when {
        v <= 2 -> "(очень легко)"
        v <= 5 -> "(средне)"
        v <= 8 -> "(сложно)"
        else -> "(очень сложно)"
    }

    private fun smallButton(text: String, action: () -> Unit): Button {
        val b = Button(context)
        b.text = text
        b.minWidth = 0
        b.minimumWidth = 0
        b.setOnClickListener { action() }
        return b
    }

    private fun renderTasks() {
        llTasks.removeAllViews()
        if (cfg.tasks.isEmpty()) {
            llTasks.addView(TextView(context).apply { text = "Список заданий пуст" })
            return
        }
        cfg.tasks.forEachIndexed { i, name ->
            val row = LinearLayout(context)
            row.orientation = HORIZONTAL
            row.gravity = android.view.Gravity.CENTER_VERTICAL
            val tv = TextView(context)
            tv.text = "${i + 1}. ${TaskType.fromName(name)?.title ?: name}"
            row.addView(tv, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            row.addView(smallButton("▲") {
                if (i > 0) {
                    cfg.tasks.add(i - 1, cfg.tasks.removeAt(i)); renderTasks(); onChange?.invoke()
                }
            })
            row.addView(smallButton("▼") {
                if (i < cfg.tasks.size - 1) {
                    cfg.tasks.add(i + 1, cfg.tasks.removeAt(i)); renderTasks(); onChange?.invoke()
                }
            })
            row.addView(smallButton("✕") {
                cfg.tasks.removeAt(i); renderTasks(); onChange?.invoke()
            })
            llTasks.addView(row)
        }
    }

    override fun onDetachedFromWindow() {
        previewer.stop()
        super.onDetachedFromWindow()
    }
}