package com.sleepoff.app.domain

/**
 * Finite-state machine for the countdown.
 *
 * Design note: the timer is deadline-based, not tick-based.
 *   endEpochMs = System.currentTimeMillis() + remainingMs (when started)
 * This lets the service / AlarmManager / reboot receiver rebuild the
 * remaining time purely from the stored deadline, which survives process
 * death and Doze. The foreground service ticker just re-reads now vs end.
 */
enum class TimerState {
    IDLE,       // nothing scheduled
    RUNNING,    // counting down to endEpochMs
    PAUSED;     // paused; remainingMs is stored

    val isActive: Boolean get() = this != IDLE
}
