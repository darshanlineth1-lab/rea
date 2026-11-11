package com.sleepoff.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Schedules the single exact alarm that fires at deadline epoch. The alarm
 * goes to {@link TimerAlarmReceiver}, which either starts the foreground
 * service's completion flow directly or asks the user to tap to apply it
 * (if the device was restarted after an exact-alarm revocation).
 *
 * Why AlarmManager (not WorkManager)? The user expects *exact* shutdown at
 * a chosen wall-clock time, even in Doze. WorkManager permits inexact
 * batching; setExactAndAllowWhileIdle is the correct primitive for this use
 * case. On Android 12+ the SCHEDULE_EXACT_ALARM permission must be granted
 * by the user (the Setup screen walks them through it).
 */
class TimerAlarmScheduler(private val appContext: Context) {

    companion object {
        private const val TAG = "AlarmScheduler"
        private const val REQ_FIRE = 10001
        private const val REQ_SCHEDULE = 10002
    }

    fun canScheduleExactAlarms(): Boolean {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true
    }

    fun scheduleFire(epochMs: Long) {
        cancelFire()
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = firePendingIntent()
        try {
            if (canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
                Log.i(TAG, "Scheduled exact alarm at $epochMs")
            } else {
                // Fallback: inexact alarm. Will fire around the deadline, which
                // is acceptable if the user has denied exact-alarm access. The
                // service still shows a notification explaining this.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
                Log.w(TAG, "Exact alarms not granted; scheduling inexact.")
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "setExact denied, using inexact", se)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMs, pi)
        }
    }

    fun cancelFire() {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent())
    }

    /**
     * Schedule a daily alarm at (hour, minute) that starts the timer with the
     * user's saved schedule configuration. The daily alarm itself is inexact
     * (it's a convenience, not a precision timer), but once started the timer
     * uses an exact fire alarm as usual.
     */
    fun scheduleDaily(hour: Int, minute: Int) {
        cancelDaily()
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = schedulePendingIntent()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pi
        )
        Log.i(TAG, "Scheduled daily timer at $hour:$minute")
    }

    fun cancelDaily() {
        val am = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(schedulePendingIntent())
    }

    private fun firePendingIntent(): PendingIntent {
        val i = Intent(appContext, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            appContext, REQ_FIRE, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun schedulePendingIntent(): PendingIntent {
        val i = Intent(appContext, TimerAlarmReceiver::class.java).apply {
            action = TimerAlarmReceiver.ACTION_DAILY_START
        }
        return PendingIntent.getBroadcast(
            appContext, REQ_SCHEDULE, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
