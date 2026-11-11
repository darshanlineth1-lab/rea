package com.sleepoff.app.bluetooth

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * OPTIONAL, USER-AUTHORIZED Bluetooth auto-toggle helper.
 *
 * On Android 13+, third-party apps cannot turn Bluetooth off programmatically.
 * If the user enables this service, SleepOff opens the Bluetooth Settings panel
 * at timer end and this service clicks the primary "Use Bluetooth" switch for
 * them. This is the officially accepted user-authorized automation path
 * (used by apps like Tasker and Bixby Routines via Accessibility).
 *
 * The service ONLY reacts when SleepOff explicitly fires the local broadcast
 * ACTION_TAP_BT_TOGGLE after opening the Bluetooth Settings screen.
 */
class BluetoothToggleAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "BTAccessibility"
        /** Local broadcast action that tells us to click the toggle. */
        const val ACTION_TAP_BT_TOGGLE = "com.sleepoff.app.action.TAP_BT_TOGGLE"
    }

    private val receiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ACTION_TAP_BT_TOGGLE) {
                // The activity we just launched is Settings.ACTION_BLUETOOTH_SETTINGS;
                // give the window a moment to render then find and click the switch.
                android.os.Handler(applicationContext.mainLooper).postDelayed({
                    tapBluetoothSwitch()
                }, 600)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = android.content.IntentFilter(ACTION_TAP_BT_TOGGLE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(receiver) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't react to continuous accessibility events; we only act when
        // our own broadcast fires. If the Settings window isn't ready yet,
        // the retry below will find the switch on the next window update.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (pendingTap) tapBluetoothSwitch()
        }
    }

    override fun onInterrupt() { /* no-op */ }

    @Volatile private var pendingTap: Boolean = false

    private fun tapBluetoothSwitch() {
        pendingTap = true
        val root = rootInActiveWindow ?: run {
            Log.w(TAG, "No active window yet for BT toggle")
            return
        }

        // Try common switch labels on AOSP / Pixel / Samsung / MIUI.
        val candidates = listOf(
            "Use Bluetooth", "Bluetooth", "Off", "On",
            "블루투스", "蓝牙"
        )
        for (label in candidates) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    pendingTap = false
                    Log.i(TAG, "Clicked BT toggle node labeled '$label'")
                    // After toggling, leave Settings (go back to home) after a beat.
                    android.os.Handler(applicationContext.mainLooper).postDelayed({
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }, 500)
                    return
                }
                // Walk up to a clickable parent (common on AOSP).
                var p: AccessibilityNodeInfo? = node.parent
                var hops = 0
                while (p != null && hops < 6) {
                    if (p.isClickable && p.className?.contains("Switch") == true) {
                        p.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        pendingTap = false
                        Log.i(TAG, "Clicked BT toggle switch for label '$label'")
                        android.os.Handler(applicationContext.mainLooper).postDelayed({
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }, 500)
                        return
                    }
                    p = p.parent
                    hops++
                }
            }
        }
        Log.w(TAG, "Could not find Bluetooth toggle in Settings; user must tap manually.")
        // Don't clear pendingTap — let subsequent events retry once more.
        android.os.Handler(applicationContext.mainLooper).postDelayed({ pendingTap = false }, 4000)
    }
}
