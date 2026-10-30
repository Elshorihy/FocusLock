package com.elshorihy.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class FocusAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        val prefs = getSharedPreferences("focuslock", MODE_PRIVATE)
        if (!prefs.getBoolean("blocking_enabled", false)) return
        if (System.currentTimeMillis() < prefs.getLong("pause_until", 0L)) return
        val blocked = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        if (pkg in blocked) {
            startActivity(Intent(this, BlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("blocked_package", pkg))
        }
    }
    override fun onInterrupt() = Unit
}
