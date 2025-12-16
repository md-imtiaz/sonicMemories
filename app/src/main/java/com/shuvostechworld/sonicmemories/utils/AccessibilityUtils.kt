package com.shuvostechworld.sonicmemories.utils

import android.view.View
import android.view.accessibility.AccessibilityEvent

object AccessibilityUtils {
    fun announceToScreenReader(view: View, text: String) {
        val accessibilityManager = view.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        if (accessibilityManager?.isEnabled == true) {
            val event = AccessibilityEvent()
            @Suppress("DEPRECATION")
            event.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
            event.text.add(text)
            event.className = AccessibilityUtils::class.java.name
            event.packageName = view.context.packageName
            accessibilityManager.sendAccessibilityEvent(event)
        }
    }

    fun vibrate(context: android.content.Context, duration: Long = 100) {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(android.content.Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(
                    duration,
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
