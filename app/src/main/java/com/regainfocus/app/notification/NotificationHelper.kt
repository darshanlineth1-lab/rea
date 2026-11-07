package com.regainfocus.app.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.regainfocus.app.MainActivity
import com.regainfocus.app.R
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.service.FocusTimerService

class NotificationHelper(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannelsCreated()
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannelsCreated() {
        val timerChannel = NotificationChannel(
            CHANNEL_TIMER_ONGOING,
            "Active Focus Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Displays real-time focus countdown and strict enforcement status"
            setShowBadge(false)
        }

        val alertsChannel = NotificationChannel(
            CHANNEL_SESSION_ALERTS,
            "Session & Break Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when focus phases, breaks, or strict sessions complete"
            enableVibration(true)
        }

        val remindersChannel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Study Goals & Scheduled Blocks",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily study goal nudges and scheduled focus session reminders"
        }

        manager.createNotificationChannels(listOf(timerChannel, alertsChannel, remindersChannel))
    }

    fun buildOngoingTimerNotification(
        state: PersistedTimerSessionState,
        formattedTime: String
    ): Notification {
        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            appContext,
            100,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isStrict = state.strictLevel != StrictEnforcementLevel.NONE
        val titlePrefix = if (isStrict) "🔒 ${state.strictLevel.badge}" else "⏱ ${state.timerMode.displayName}"
        val phaseLabel = if (state.timerMode == TimerModeType.STOPWATCH) {
            "Elapsed · ${state.sessionTag}"
        } else {
            "${state.phase.label} · ${state.sessionTag}"
        }

        val subtitle = if (isStrict) {
            "$formattedTime remaining · ${state.blockedPackages.size} apps locked"
        } else if (state.isPaused) {
            "Paused at $formattedTime"
        } else {
            "$formattedTime · Tap to view session"
        }

        val builder = NotificationCompat.Builder(appContext, CHANNEL_TIMER_ONGOING)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle("$titlePrefix — $phaseLabel")
            .setContentText(subtitle)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        // CRITICAL STRICT REQUIREMENT:
        // Never include Pause, Skip, or Stop actions in the notification during an active Strict Session!
        if (!isStrict) {
            val pauseResumeIntent = PendingIntent.getService(
                appContext,
                101,
                Intent(appContext, FocusTimerService::class.java).apply {
                    action = FocusTimerService.ACTION_PAUSE_OR_RESUME
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val stopIntent = PendingIntent.getService(
                appContext,
                102,
                Intent(appContext, FocusTimerService::class.java).apply {
                    action = FocusTimerService.ACTION_STOP_FLEXIBLE
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder.addAction(
                0,
                if (state.isPaused) "Resume" else "Pause",
                pauseResumeIntent
            )
            builder.addAction(0, "Finish", stopIntent)
        }

        return builder.build()
    }

    fun notifyPhaseOrSessionAlert(title: String, message: String, notificationId: Int = 2001) {
        if (!hasNotificationPermission()) return
        val openPending = PendingIntent.getActivity(
            appContext,
            notificationId,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_SESSION_ALERTS)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(notificationId, notification)
    }

    fun notifyStudyReminder(title: String, message: String, notificationId: Int = 3001) {
        if (!hasNotificationPermission()) return
        val openPending = PendingIntent.getActivity(
            appContext,
            notificationId,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_focus_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openPending)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_TIMER_ONGOING = "focus_timer_ongoing"
        const val CHANNEL_SESSION_ALERTS = "focus_session_alerts"
        const val CHANNEL_REMINDERS = "focus_study_reminders"
        const val ONGOING_NOTIFICATION_ID = 1001
    }
}
