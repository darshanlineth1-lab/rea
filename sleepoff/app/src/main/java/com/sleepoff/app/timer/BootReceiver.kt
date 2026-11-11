package com.sleepoff.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sleepoff.app.SleepOffApplication
import com.sleepoff.app.domain.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules alarms after BOOT_COMPLETED or app update. This is the reason
 * the timer survives a reboot: the deadline is in DataStore, and on reboot
 * we re-arm an AlarmManager alarm at the same wall-clock time.
 */
class BootReceiver : BroadcastReceiver() {

    companion object { private const val TAG = "BootRx" }

    override fun onReceive(context: Context, intent: Intent?) {
        val ctx = context.applicationContext
        val action = intent?.action ?: return
        Log.i(TAG, "Received $action")
        val prefs = (ctx as SleepOffApplication).prefs
        val scheduler = TimerAlarmScheduler(ctx)

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Daily schedule (re-arm every boot, regardless of timer state).
                val schedEnabled = prefs.scheduleEnabled.first()
                if (schedEnabled) {
                    scheduler.scheduleDaily(
                        prefs.scheduleHourOfDay.first(),
                        prefs.scheduleMinute.first()
                    )
                } else scheduler.cancelDaily()

                // Active timer.
                val snap = prefs.currentTimerSnapshot()
                when (snap.state) {
                    TimerState.RUNNING -> {
                        val now = System.currentTimeMillis()
                        if (snap.endEpochMs > now) {
                            scheduler.scheduleFire(snap.endEpochMs)
                            // Bring up the foreground service so the
                            // persistent notification is visible again.
                            val i = Intent(ctx, TimerService::class.java).apply {
                                this.action = TimerService.ACTION_RESUME_AFTER_BOOT
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ctx.startForegroundService(i)
                            } else {
                                ctx.startService(i)
                            }
                        } else {
                            // Deadline already passed while the phone was off
                            // — fire completion now.
                            val i = Intent(ctx, TimerService::class.java).apply {
                                this.action = TimerService.ACTION_COMPLETE
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ctx.startForegroundService(i)
                            } else {
                                ctx.startService(i)
                            }
                        }
                    }
                    TimerState.PAUSED -> {
                        // Show a resume notification; don't auto-fire.
                        val i = Intent(ctx, TimerService::class.java).apply {
                            this.action = TimerService.ACTION_RESUME_AFTER_BOOT
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ctx.startForegroundService(i)
                        } else {
                            ctx.startService(i)
                        }
                    }
                    TimerState.IDLE -> {
                        // Ensure notification is gone.
                        ctx.stopService(Intent(ctx, TimerService::class.java))
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
