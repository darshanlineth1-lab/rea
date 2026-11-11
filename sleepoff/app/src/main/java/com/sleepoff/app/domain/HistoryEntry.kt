package com.sleepoff.app.domain

/**
 * Result of a single timer run, stored in the history list.
 */
data class HistoryEntry(
    val startedEpochMs: Long,
    val endedEpochMs: Long,
    val durationMs: Long,
    val mode: SleepMode,
    val musicPaused: Boolean,
    val bluetoothTurnedOff: Boolean,
    val bluetoothAttempted: Boolean,
    val note: String? = null
)
