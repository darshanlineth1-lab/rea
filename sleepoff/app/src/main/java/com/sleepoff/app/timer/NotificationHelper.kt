package com.sleepoff.app.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sleepoff.app.MainActivity
import com.sleepoff.app.R
import com.sleepoff.app.domain.TimerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the countdown and completion notifications. Two channels:
 *   - "timer"      — ongoing foreground notification (low prominence)
 *   - "completion" — one-shot alert when the timer finishes
 */
class NotificationHelper(private val ctx: Context) {

    companion object {
        const val CHANNEL_TIMER = "sleepoff_timer"
        const val CHANNEL_COMPLETION = "sleepoff_completion"
        const val NOTIF_ID_ONGOING = 1
        const val NOTIF_ID_COMPLETION = 2

        // PendingIntent request codes
        private const val REQ_OPEN = 1
        private const val REQ_PAUSE = 2
        private const val REQ_RESUME = 3
        private const val REQ_CANCEL = 4
        private const val REQ_FINISH_NOW = 5
        private const val REQ_BT_SETTINGS = 6
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val timer = NotificationChannel(
                CHANNEL_TIMER,
                ctx.getString(R.string.notif_channel_timer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Countdown shown while the sleep timer is running."
                setShowBadge(false)
                enableVibration(false)
            }
            val completion = NotificationChannel(
                CHANNEL_COMPLETION,
                ctx.getString(R.string.notif_channel_completion),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alert when the sleep timer finishes."
            }
            nm.createNotificationChannel(timer)
            nm.createNotificationChannel(completion)
        }
    }

    fun buildOngoing(state: TimerState, remainingMs: Long, endEpochMs: Long): Notification {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val endsAt = timeFmt.format(Date(endEpochMs))
        val content = when (state) {
            TimerState.RUNNING ->
                ctx.getString(R.string.notif_countdown_body, formatMs(remainingMs), endsAt)
            TimerState.PAUSED ->
                ctx.getString(R.string.notif_paused_body, formatMs(remainingMs))
            TimerState.IDLE -> ctx.getString(R.string.idle_label)
        }

        val openIntent = PendingIntent.getActivity(
            ctx, REQ_OPEN, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = actionIntent(TimerService.ACTION_PAUSE, REQ_PAUSE)
        val resumeIntent = actionIntent(TimerService.ACTION_RESUME, REQ_RESUME)
        val cancelIntent = actionIntent(TimerService.ACTION_CANCEL, REQ_CANCEL)

        val builder = NotificationCompat.Builder(ctx, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle(ctx.getString(R.string.notif_countdown_title))
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                ctx.getString(if (state == TimerState.PAUSED) R.string.resume else R.string.pause),
                if (state == TimerState.PAUSED) resumeIntent else pauseIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                ctx.getString(R.string.cancel),
                cancelIntent
            )
        return builder.build()
    }

    fun buildCompletion(
        musicPaused: Boolean,
        btTurnedOff: Boolean,
        btAttempted: Boolean,
        musicKept: Boolean
    ): Notification {
        val lines = mutableListOf<String>()
        lines += when {
            musicKept -> ctx.getString(R.string.notif_completion_music_kept)
            musicPaused -> ctx.getString(R.string.notif_completion_music_paused)
            else -> ctx.getString(R.string.notif_completion_music_paused) // generic
        }
        lines += when {
            !btAttempted -> ctx.getString(R.string.notif_completion_bt_kept)
            btTurnedOff -> ctx.getString(R.string.notif_completion_bt_off)
            else -> ctx.getString(R.string.notif_completion_bt_failed)
        }

        val openIntent = PendingIntent.getActivity(
            ctx, REQ_OPEN, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val btSettingsIntent = PendingIntent.getActivity(
            ctx, REQ_BT_SETTINGS,
            Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_COMPLETION)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle(ctx.getString(R.string.notif_completion_title))
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (btAttempted && !btTurnedOff) {
            builder.addAction(
                android.R.drawable.stat_sys_data_bluetooth,
                ctx.getString(R.string.notif_open_bt_settings),
                btSettingsIntent
            )
        }
        return builder.build()
    }

    fun buildResumePending(): Notification {
        val openIntent = PendingIntent.getActivity(
            ctx, REQ_FINISH_NOW, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_COMPLETION)
            .setSmallIcon(R.drawable.ic_notification_moon)
            .setContentTitle(ctx.getString(R.string.notif_completion_title))
            .setContentText(ctx.getString(R.string.notif_finish_now_body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun actionIntent(action: String, req: Int): PendingIntent {
        val i = Intent(ctx, TimerService::class.java).setAction(action)
        return PendingIntent.getService(
            ctx, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun notifyCompletion(notif: Notification) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_COMPLETION, notif)
    }

    fun cancelOngoing() {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID_ONGOING)
        nm.cancel(NOTIF_ID_COMPLETION)
    }

    private fun formatMs(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
               else String.format("%d:%02d", m, s)
    }
}
