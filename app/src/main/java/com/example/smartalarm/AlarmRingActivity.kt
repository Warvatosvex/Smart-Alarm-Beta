package com.example.smartalarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class AlarmRingActivity : AppCompatActivity() {
    private lateinit var tvCounter: TextView
    private lateinit var tvPrompt: TextView
    private lateinit var tvFeedback: TextView
    private lateinit var captchaView: CaptchaView
    private lateinit var etAnswer: EditText
    private lateinit var btnSubmit: Button
    private lateinit var btnFinishTest: Button

    private var tasks: List<Task> = emptyList()
    private var index = 0
    private var test = false

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).requestDismissKeyguard(this, null)

        setContentView(R.layout.activity_ring)
        tvCounter = findViewById(R.id.tvCounter)
        tvPrompt = findViewById(R.id.tvPrompt)
        tvFeedback = findViewById(R.id.tvFeedback)
        captchaView = findViewById(R.id.captchaView)
        etAnswer = findViewById(R.id.etAnswer)
        btnSubmit = findViewById(R.id.btnSubmit)
        btnFinishTest = findViewById(R.id.btnFinishTest)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {}
        })

        val cfg = AlarmConfig.fromJson(intent.getStringExtra(AlarmService.EXTRA_CONFIG))
        test = intent.getBooleanExtra(AlarmService.EXTRA_TEST, false)

        val types = if (cfg.randomEachRing) TaskGenerator.randomTypes(cfg.randomCount) else cfg.tasks
        tasks = types.map { TaskGenerator.generate(it, cfg.difficulty) }

        btnFinishTest.visibility = if (test) View.VISIBLE else View.GONE
        btnFinishTest.setOnClickListener { finishAlarm() }
        btnSubmit.setOnClickListener { submit() }
        etAnswer.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit(); true
            } else false
        }
        showTask()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }

    private fun showTask() {
        tvFeedback.text = ""
        etAnswer.text.clear()
        if (tasks.isEmpty()) {
            tvCounter.text = "Будильник"
            tvPrompt.text = "Заданий нет"
            captchaView.visibility = View.GONE
            etAnswer.visibility = View.GONE
            btnSubmit.text = "Выключить"
            return
        }
        val t = tasks[index]
        tvCounter.text = "Задание ${index + 1} из ${tasks.size}: ${t.type.title}"
        tvPrompt.text = t.prompt
        if (t.captcha != null) {
            captchaView.visibility = View.VISIBLE
            captchaView.setCaptcha(t.captcha, t.difficulty)
        } else {
            captchaView.visibility = View.GONE
        }
        btnSubmit.text = if (index == tasks.size - 1) "Ответить и выключить" else "Ответить"
        etAnswer.requestFocus()
    }

    private fun submit() {
        if (tasks.isEmpty()) {
            finishAlarm(); return
        }
        if (tasks[index].check(etAnswer.text.toString())) {
            index++
            if (index >= tasks.size) finishAlarm() else showTask()
        } else {
            tvFeedback.text = "Неверно, попробуйте ещё раз"
            etAnswer.text.clear()
        }
    }

    private fun finishAlarm() {
        startService(AlarmService.stopIntent(this))
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
            else -> super.dispatchKeyEvent(event)
        }
    }
}