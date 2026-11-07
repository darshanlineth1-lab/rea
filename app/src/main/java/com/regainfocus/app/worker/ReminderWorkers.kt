package com.regainfocus.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.regainfocus.app.RegainFocusApplication
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Periodic WorkManager worker checking:
 * 1) Daily Study Goal progress reminder at the user's configured evening reminder window.
 * 2) Scheduled Focus Planner blocks (`FocusScheduleEntity`) to alert or auto-start scheduled sessions.
 */
class FocusReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? RegainFocusApplication ?: return Result.success()
        val container = app.container
        val settings = container.productivityRepository.settingsFlow.first()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val nowTime = LocalTime.now(zone)

        // 1. Check Daily Focus Goal Reminder
        if (settings.enableDailyGoalReminder) {
            val sessions = container.database.sessionDao().getAllSessionsOnce()
            val todayMinutes = sessions
                .filter {
                    Instant.ofEpochMilli(it.startTimeEpochMillis).atZone(zone).toLocalDate() == today
                }
                .sumOf { it.effectiveWorkSeconds } / 60L

            val remaining = settings.dailyFocusGoalMinutes - todayMinutes.toInt()
            if (remaining > 0 && nowTime.hour == settings.dailyGoalReminderHour) {
                container.notificationHelper.notifyStudyReminder(
                    title = "Daily Study Goal: ${remaining}m Remaining 🎯",
                    message = "You've logged ${todayMinutes}m of your ${settings.dailyFocusGoalMinutes}m target today. Start a focus block to protect your streak!",
                    notificationId = 3100 + today.dayOfYear
                )
            }
        }

        // 2. Check Scheduled Focus Blocks
        if (settings.enableScheduleReminders) {
            val schedules = container.database.scheduleDao().getEnabledSchedulesOnce()
            val dayBit = 1 shl (today.dayOfWeek.value - 1) // Monday = bit 0 .. Sunday = bit 6
            val epochDay = today.toEpochDay()
            val nowMinuteOfDay = nowTime.hour * 60 + nowTime.minute

            for (sched in schedules) {
                if ((sched.daysOfWeekMask and dayBit) == 0) continue
                if (sched.lastTriggeredEpochDay == epochDay) continue

                val schedStartMin = sched.startHour * 60 + sched.startMinute
                if (nowMinuteOfDay in (schedStartMin - 15)..(schedStartMin + 20)) {
                    container.database.scheduleDao().markTriggered(sched.id, epochDay)
                    container.notificationHelper.notifyStudyReminder(
                        title = "Scheduled Focus: ${sched.title} ⏰",
                        message = String.format(
                            "Your planned block (%02d:%02d–%02d:%02d)%s is ready to begin.",
                            sched.startHour,
                            sched.startMinute,
                            sched.endHour,
                            sched.endMinute,
                            if (sched.strictMode) " [Strict Mode]" else ""
                        ),
                        notificationId = (3500 + sched.id).toInt()
                    )
                }
            }
        }

        return Result.success()
    }
}

object ReminderWorkScheduler {
    private const val WORK_NAME = "regain_periodic_focus_reminders"

    fun syncScheduledWorkers(context: Context) {
        val request = PeriodicWorkRequestBuilder<FocusReminderWorker>(30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
