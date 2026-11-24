package com.example.smartalarm

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "SmartAlarm готов к разработке!"
            textSize = 22f
            setPadding(48, 96, 48, 48)
        }
        setContentView(textView)
    }
}
