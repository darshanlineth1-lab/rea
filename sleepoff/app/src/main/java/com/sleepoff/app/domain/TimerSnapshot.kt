package com.sleepoff.app.domain

/**
 * Snapshot of the active timer exposed to the UI (via Flow).
 *
 * @param state             finite state (idle/running/paused)
 * @param remainingMs       time left when RUNNING (computed live) or PAUSED (stored)
 * @param endEpochMs        absolute wall-clock deadline in ms, only meaningful when RUNNING
 * @param durationMs        originally chosen duration (for the progress ring)
 * @param mode              which shutdown mode was selected for this run
 */
data class TimerSnapshot(
    val state: TimerState = TimerState.IDLE,
    val remainingMs: Long = 0L,
    val endEpochMs: Long = 0L,
    val durationMs: Long = 0L,
    val mode: SleepMode = SleepMode.MUSIC_ONLY
) {
    val progress: Float
        get() = if (durationMs <= 0L) 0f
                else (remainingMs.coerceAtLeast(0L) / durationMs.toFloat()).coerceIn(0f, 1f)
}
