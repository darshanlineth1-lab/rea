package com.sleepoff.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Receives the alarm broadcast. We do the minimal work here: dispatch to the
 * foreground service, which is the reliable place to do I/O (pausing media,
 * touching Bluetooth, updating DataStore, posting notifications).
 */
class TimerAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FIRE = "com.sleepoff.app.action.FIRE_TIMER"
        const val ACTION_DAILY_START = "com.sleepoff.app.action.DAILY_START"
        private const val TAG = "TimerAlarmRx"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val ctx = context.applicationContext
        when (intent?.action) {
            ACTION_FIRE -> {
                Log.i(TAG, "Fire alarm received")
                val i = Intent(ctx, TimerService::class.java).apply {
                    action = TimerService.ACTION_COMPLETE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            }
            ACTION_DAILY_START -> {
                Log.i(TAG, "Daily-schedule start alarm received")
                val i = Intent(ctx, TimerService::class.java).apply {
                    action = TimerService.ACTION_DAILY_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            }
        }
    }
}
