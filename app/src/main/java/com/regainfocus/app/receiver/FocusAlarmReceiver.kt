package com.regainfocus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.regainfocus.app.service.FocusTimerService

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TIMER_DEADLINE) {
            FocusTimerService.triggerExpiryCheck(context)
        }
    }

    companion object {
        const val ACTION_TIMER_DEADLINE = "com.regainfocus.app.ACTION_TIMER_DEADLINE"
    }
}
