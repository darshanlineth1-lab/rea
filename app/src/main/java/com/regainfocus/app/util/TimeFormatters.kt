package com.regainfocus.app.util

import java.util.Locale

object TimeFormatters {

    fun formatMillisClock(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatMinutesCompact(totalMinutes: Int): String {
        val mins = totalMinutes.coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun formatDurationMillisCompact(millis: Long): String {
        val totalMinutes = (millis.coerceAtLeast(0L) / 60_000L).toInt()
        if (totalMinutes == 0 && millis >= 1_000L) {
            return "${millis / 1_000L}s"
        }
        return formatMinutesCompact(totalMinutes)
    }
}
