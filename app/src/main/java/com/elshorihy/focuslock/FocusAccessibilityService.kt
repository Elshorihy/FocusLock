package com.elshorihy.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    private var lastPackage = ""
    private var lastLaunchAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val prefs = getSharedPreferences("focuslock", MODE_PRIVATE)
        val endAt = prefs.getLong("session_end", 0L)
        val active = endAt > System.currentTimeMillis()
        if (!active || !prefs.getBoolean("blocking_enabled", false)) return
        if (prefs.getLong("pause_until", 0L) > System.currentTimeMillis()) return
        val blocked = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        if (pkg !in blocked) return
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastPackage && now - lastLaunchAt < 1200L) return
        lastPackage = pkg
        lastLaunchAt = now
        startActivity(Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("blocked_package", pkg))
    }

    override fun onInterrupt() = Unit
}