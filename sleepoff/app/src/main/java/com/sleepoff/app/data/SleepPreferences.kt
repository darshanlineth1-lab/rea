package com.sleepoff.app.data

import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.domain.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

/**
 * All persistent state lives here — both user preferences and the
 * live timer state (so a process kill or reboot can resume).
 *
 * We use androidx.datastore.preferences with typed keys; this class
 * wraps it in a clean API for the rest of the app.
 */
interface SleepPreferences {

    // --- user preferences ---
    val lastDurationMinutes: Flow<Int>
    val lastMode: Flow<SleepMode>
    val btDelaySeconds: Flow<Int>        // delay between pause and BT off
    val fadeMinutes: Flow<Int>           // Gentle-mode fade length
    val restoreVolume: Flow<Boolean>
    val batterySaver: Flow<Boolean>

    // --- daily schedule ---
    val scheduleEnabled: Flow<Boolean>
    val scheduleHourOfDay: Flow<Int>
    val scheduleMinute: Flow<Int>
    val scheduleDurationMinutes: Flow<Int>
    val scheduleMode: Flow<SleepMode>

    // --- live timer state (written by TimerController) ---
    val timerState: Flow<TimerState>
    val timerEndEpochMs: Flow<Long>      // for RUNNING
    val timerPausedRemainingMs: Flow<Long> // for PAUSED
    val timerDurationMs: Flow<Long>
    val timerMode: Flow<SleepMode>

    // --- history (JSON-encoded list) ---
    val historyJson: Flow<String>

    suspend fun setLastDurationMinutes(min: Int)
    suspend fun setLastMode(mode: SleepMode)
    suspend fun setBtDelaySeconds(secs: Int)
    suspend fun setFadeMinutes(min: Int)
    suspend fun setRestoreVolume(restore: Boolean)
    suspend fun setBatterySaver(saver: Boolean)

    suspend fun setSchedule(
        enabled: Boolean,
        hour: Int, minute: Int,
        durationMinutes: Int,
        mode: SleepMode
    )

    suspend fun saveTimerSnapshot(
        state: TimerState,
        endEpochMs: Long,
        pausedRemainingMs: Long,
        durationMs: Long,
        mode: SleepMode
    )

    suspend fun clearTimerSnapshot()

    suspend fun appendHistory(entryJson: String)
    suspend fun clearHistory()

    /** Convenience: read the current timer snapshot synchronously-once. */
    suspend fun currentTimerSnapshot(): TimerSnapshotFields
}

data class TimerSnapshotFields(
    val state: TimerState,
    val endEpochMs: Long,
    val pausedRemainingMs: Long,
    val durationMs: Long,
    val mode: SleepMode
)
