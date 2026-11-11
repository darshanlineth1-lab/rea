package com.sleepoff.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.domain.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed preferences. One preferences file named "sleepoff_prefs".
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sleepoff_prefs")

class DataStoreSleepPreferences(private val context: Context) : SleepPreferences {

    // --- keys ---
    private val kLastDuration = intPreferencesKey("last_duration_min")
    private val kLastMode = stringPreferencesKey("last_mode")
    private val kBtDelay = intPreferencesKey("bt_delay_seconds")
    private val kFadeMin = intPreferencesKey("fade_minutes")
    private val kRestoreVol = booleanPreferencesKey("restore_volume")
    private val kBatterySaver = booleanPreferencesKey("battery_saver")

    private val kSchedEnabled = booleanPreferencesKey("sched_enabled")
    private val kSchedHour = intPreferencesKey("sched_hour")
    private val kSchedMin = intPreferencesKey("sched_minute")
    private val kSchedDur = intPreferencesKey("sched_duration")
    private val kSchedMode = stringPreferencesKey("sched_mode")

    private val kTimerState = stringPreferencesKey("timer_state")
    private val kTimerEnd = longPreferencesKey("timer_end_ms")
    private val kTimerPausedRem = longPreferencesKey("timer_paused_remaining_ms")
    private val kTimerDur = longPreferencesKey("timer_duration_ms")
    private val kTimerMode = stringPreferencesKey("timer_mode")

    private val kHistoryJson = stringPreferencesKey("history_json")

    // --- user pref flows ---
    override val lastDurationMinutes: Flow<Int> = context.dataStore.data.map {
        it[kLastDuration] ?: 30
    }
    override val lastMode: Flow<SleepMode> = context.dataStore.data.map {
        SleepMode.valueOf(it[kLastMode] ?: SleepMode.MUSIC_BT.name)
    }
    override val btDelaySeconds: Flow<Int> = context.dataStore.data.map {
        (it[kBtDelay] ?: 5).coerceIn(0, 120)
    }
    override val fadeMinutes: Flow<Int> = context.dataStore.data.map {
        (it[kFadeMin] ?: 5).coerceIn(1, 30)
    }
    override val restoreVolume: Flow<Boolean> = context.dataStore.data.map {
        it[kRestoreVol] ?: false
    }
    override val batterySaver: Flow<Boolean> = context.dataStore.data.map {
        it[kBatterySaver] ?: false
    }

    // --- schedule flows ---
    override val scheduleEnabled: Flow<Boolean> = context.dataStore.data.map { it[kSchedEnabled] ?: false }
    override val scheduleHourOfDay: Flow<Int> = context.dataStore.data.map { (it[kSchedHour] ?: 23).coerceIn(0, 23) }
    override val scheduleMinute: Flow<Int> = context.dataStore.data.map { (it[kSchedMin] ?: 0).coerceIn(0, 59) }
    override val scheduleDurationMinutes: Flow<Int> = context.dataStore.data.map { (it[kSchedDur] ?: 45).coerceIn(1, 720) }
    override val scheduleMode: Flow<SleepMode> = context.dataStore.data.map {
        SleepMode.valueOf(it[kSchedMode] ?: SleepMode.MUSIC_BT.name)
    }

    // --- live timer flows ---
    override val timerState: Flow<TimerState> = context.dataStore.data.map {
        TimerState.valueOf(it[kTimerState] ?: TimerState.IDLE.name)
    }
    override val timerEndEpochMs: Flow<Long> = context.dataStore.data.map { it[kTimerEnd] ?: 0L }
    override val timerPausedRemainingMs: Flow<Long> = context.dataStore.data.map { it[kTimerPausedRem] ?: 0L }
    override val timerDurationMs: Flow<Long> = context.dataStore.data.map { it[kTimerDur] ?: 0L }
    override val timerMode: Flow<SleepMode> = context.dataStore.data.map {
        SleepMode.valueOf(it[kTimerMode] ?: SleepMode.MUSIC_BT.name)
    }

    override val historyJson: Flow<String> = context.dataStore.data.map { it[kHistoryJson] ?: "[]" }

    // --- setters ---
    override suspend fun setLastDurationMinutes(min: Int) = context.dataStore.edit { it[kLastDuration] = min }
    override suspend fun setLastMode(mode: SleepMode) = context.dataStore.edit { it[kLastMode] = mode.name }
    override suspend fun setBtDelaySeconds(secs: Int) = context.dataStore.edit { it[kBtDelay] = secs.coerceIn(0, 120) }
    override suspend fun setFadeMinutes(min: Int) = context.dataStore.edit { it[kFadeMin] = min.coerceIn(1, 30) }
    override suspend fun setRestoreVolume(restore: Boolean) = context.dataStore.edit { it[kRestoreVol] = restore }
    override suspend fun setBatterySaver(saver: Boolean) = context.dataStore.edit { it[kBatterySaver] = saver }

    override suspend fun setSchedule(
        enabled: Boolean, hour: Int, minute: Int, durationMinutes: Int, mode: SleepMode
    ) = context.dataStore.edit {
        it[kSchedEnabled] = enabled
        it[kSchedHour] = hour
        it[kSchedMin] = minute
        it[kSchedDur] = durationMinutes
        it[kSchedMode] = mode.name
    }

    override suspend fun saveTimerSnapshot(
        state: TimerState,
        endEpochMs: Long,
        pausedRemainingMs: Long,
        durationMs: Long,
        mode: SleepMode
    ) = context.dataStore.edit {
        it[kTimerState] = state.name
        it[kTimerEnd] = endEpochMs
        it[kTimerPausedRem] = pausedRemainingMs
        it[kTimerDur] = durationMs
        it[kTimerMode] = mode.name
    }

    override suspend fun clearTimerSnapshot() = context.dataStore.edit {
        it[kTimerState] = TimerState.IDLE.name
        it[kTimerEnd] = 0L
        it[kTimerPausedRem] = 0L
        it[kTimerDur] = 0L
        // mode left as last-selected
    }

    override suspend fun appendHistory(entryJson: String) {
        // Cap history at 100 entries to keep the single preferences value small.
        // Parse as a JSONArray to keep the output well-formed regardless of
        // existing contents.
        val current = context.dataStore.data.first()[kHistoryJson] ?: "[]"
        val arr = try { org.json.JSONArray(current) } catch (_: Throwable) { org.json.JSONArray() }
        arr.put(org.json.JSONObject(entryJson))
        // Drop oldest entries if over cap.
        while (arr.length() > 100) arr.remove(0)
        context.dataStore.edit { it[kHistoryJson] = arr.toString() }
    }

    override suspend fun clearHistory() = context.dataStore.edit { it[kHistoryJson] = "[]" }

    override suspend fun currentTimerSnapshot(): TimerSnapshotFields {
        val p = context.dataStore.data.first()
        return TimerSnapshotFields(
            state = TimerState.valueOf(p[kTimerState] ?: TimerState.IDLE.name),
            endEpochMs = p[kTimerEnd] ?: 0L,
            pausedRemainingMs = p[kTimerPausedRem] ?: 0L,
            durationMs = p[kTimerDur] ?: 0L,
            mode = SleepMode.valueOf(p[kTimerMode] ?: SleepMode.MUSIC_BT.name)
        )
    }
}
