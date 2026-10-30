package com.elshorihy.focuslock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class BlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("focuslock", MODE_PRIVATE)
        val pkg = intent.getStringExtra("blocked_package") ?: ""
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.rgb(12, 16, 29))
        }
        root.addView(TextView(this).apply {
            text = "وقت التركيز 🌙\n\nالتطبيق ده ضمن قائمة الحجب.\n\n$pkg"
            textSize = 22f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        })
        root.addView(Button(this).apply {
            text = "إيقاف الحجب مؤقتًا والعودة"
            setOnClickListener {
                prefs.edit().putLong("pause_until", System.currentTimeMillis() + 5 * 60 * 1000L).apply()
                finish()
            }
        })
        setContentView(root)
    }
}
