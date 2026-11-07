package com.regainfocus.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.domain.engine.PomodoroPhaseEngine
import com.regainfocus.app.domain.engine.StrictBlockerPolicyEngine
import com.regainfocus.app.domain.engine.StrictRemainingTimeCalculator
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.SessionOutcome
import com.regainfocus.app.domain.model.SoundscapeType
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.domain.model.TimerPhase
import com.regainfocus.app.notification.NotificationHelper
import com.regainfocus.app.receiver.FocusAlarmReceiver
import com.regainfocus.app.ui.blocker.StrictBlockShieldActivity
import com.regainfocus.app.util.TimeFormatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Wall-clock & monotonic foreground Focus Timer + Strict Watchdog + Ambient Audio Service.
 *
 * Key upgrades over open-source foundation (`PomodoroTimerService`):
 * 1) Uses `SystemClock.elapsedRealtime()` + persisted wall-clock deadlines (`AlarmManager` fallback)
 *    so minimizing the app, Doze mode, or process restarts never cause timer drift.
 * 2) Never cancels/resets running sessions in `onUnbind()` or `onDestroy()`.
 * 3) Rejects any pause/stop/skip intent if an active Strict Session (`STANDARD_STRICT` or `DEVICE_OWNER_STRICT`) is running!
 * 4) Runs a secondary `UsageStatsManager` foreground watchdog every 750ms during active sessions.
 * 5) Automatically releases Mode A & Mode B restrictions and saves session history when timer reaches 00:00.
 */
class FocusTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private val soundSynthesizer = AmbientSoundSynthesizer()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SESSION -> handleStartNewSession()
            ACTION_RECOVER_OR_SYNC -> handleRecoverOrSync()
            ACTION_PAUSE_OR_RESUME -> handlePauseOrResume()
            ACTION_STOP_FLEXIBLE -> handleStopFlexible()
            ACTION_SKIP_BREAK_FLEXIBLE -> handleSkipBreakFlexible()
            ACTION_REFRESH_AUDIO -> serviceScope.launch { syncAudioWithSettings() }
            null -> handleRecoverOrSync() // System restart via START_STICKY
        }
        return START_STICKY
    }

    private fun handleStartNewSession() {
        val app = application as RegainFocusApplication
        val state = app.container.activeSessionStore.readSnapshotSync()
        if (!state.isActive) return

        promoteToForeground(state)
        if (state.strictLevel == StrictEnforcementLevel.DEVICE_OWNER_STRICT) {
            app.container.deviceOwnerManager.applyDeviceOwnerStrictSession(
                blockedPackages = state.blockedPackages,
                enableLockTask = state.useLockTaskInDeviceOwner
            )
        }
        scheduleExactPhaseAlarm(state)
        startTickerLoop()
    }

    private fun handleRecoverOrSync() {
        val app = application as RegainFocusApplication
        val state = app.container.activeSessionStore.readSnapshotSync()
        if (!state.isActive) {
            app.container.deviceOwnerManager.releaseAllDeviceOwnerRestrictions()
            stopForegroundSafely()
            stopSelf()
            return
        }

        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = ActiveStrictSessionStore.currentBootCountId(this)

        // Check if strict session already expired while device was powered off / process was dead
        if (state.isStrictActive &&
            StrictRemainingTimeCalculator.isStrictSessionExpired(state, nowWall, nowElapsed, bootId)
        ) {
            serviceScope.launch {
                completeAndReleaseSession(state, SessionOutcome.RECOVERED_COMPLETED)
            }
            return
        }

        // Re-anchor monotonic elapsedRealtime if recovered after a device reboot
        val syncedState = if (state.bootAnchorId != bootId && state.timerMode != TimerModeType.STOPWATCH) {
            val remPhase = max(0L, state.phaseEndWallMillis - nowWall)
            val remStrict = if (state.isStrictActive) max(0L, state.strictHardEndWallMillis - nowWall) else 0L
            state.copy(
                bootAnchorId = bootId,
                phaseEndElapsedRealtimeMillis = nowElapsed + remPhase,
                strictHardEndElapsedRealtimeMillis = if (state.isStrictActive) nowElapsed + remStrict else 0L
            ).also { app.container.activeSessionStore.saveStateBlocking(it) }
        } else {
            state
        }

        if (syncedState.strictLevel == StrictEnforcementLevel.DEVICE_OWNER_STRICT) {
            app.container.deviceOwnerManager.applyDeviceOwnerStrictSession(
                blockedPackages = syncedState.blockedPackages,
                enableLockTask = syncedState.useLockTaskInDeviceOwner
            )
        }

        promoteToForeground(syncedState)
        scheduleExactPhaseAlarm(syncedState)
        startTickerLoop()
    }

    private fun handlePauseOrResume() {
        val app = application as RegainFocusApplication
        val state = app.container.activeSessionStore.readSnapshotSync()
        // STRICT INVARIANT: Never allow pausing an active Strict Session!
        if (!state.isActive || state.isStrictActive) return

        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = ActiveStrictSessionStore.currentBootCountId(this)

        serviceScope.launch {
            if (!state.isPaused) {
                val rem = StrictRemainingTimeCalculator.computePhaseRemainingMillis(
                    state, nowWall, nowElapsed, bootId
                )
                val updated = if (state.timerMode == TimerModeType.STOPWATCH) {
                    state.copy(
                        isPaused = true,
                        stopwatchAccumulatedMillis = rem,
                        accumulatedWorkMillis = rem
                    )
                } else {
                    val elapsedInPhase = (state.phaseDurationMillis - rem).coerceAtLeast(0L)
                    state.copy(
                        isPaused = true,
                        pausedRemainingMillis = rem,
                        accumulatedWorkMillis = if (state.phase == TimerPhase.WORK) {
                            state.accumulatedWorkMillis + elapsedInPhase
                        } else state.accumulatedWorkMillis,
                        accumulatedBreakMillis = if (state.phase != TimerPhase.WORK) {
                            state.accumulatedBreakMillis + elapsedInPhase
                        } else state.accumulatedBreakMillis
                    )
                }
                cancelExactPhaseAlarm()
                app.container.activeSessionStore.saveState(updated)
                syncAudioWithSettings()
                updateOngoingNotification(updated, rem)
            } else {
                val rem = state.pausedRemainingMillis
                val updated = if (state.timerMode == TimerModeType.STOPWATCH) {
                    state.copy(
                        isPaused = false,
                        bootAnchorId = bootId,
                        phaseStartWallMillis = nowWall,
                        stopwatchBaseElapsedRealtimeMillis = nowElapsed
                    )
                } else {
                    state.copy(
                        isPaused = false,
                        bootAnchorId = bootId,
                        phaseStartWallMillis = nowWall,
                        phaseDurationMillis = rem,
                        phaseEndWallMillis = nowWall + rem,
                        phaseEndElapsedRealtimeMillis = nowElapsed + rem
                    )
                }
                app.container.activeSessionStore.saveState(updated)
                scheduleExactPhaseAlarm(updated)
                startTickerLoop()
            }
        }
    }

    private fun handleStopFlexible() {
        val app = application as RegainFocusApplication
        val state = app.container.activeSessionStore.readSnapshotSync()
        // STRICT INVARIANT: Cannot stop early during Strict Mode!
        if (!state.isActive || state.isStrictActive) return

        serviceScope.launch {
            val nowWall = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()
            val bootId = ActiveStrictSessionStore.currentBootCountId(this@FocusTimerService)
            val currentVal = StrictRemainingTimeCalculator.computePhaseRemainingMillis(
                state, nowWall, nowElapsed, bootId
            )

            val finalState = if (state.timerMode == TimerModeType.STOPWATCH) {
                state.copy(accumulatedWorkMillis = currentVal)
            } else if (!state.isPaused) {
                val elapsed = (state.phaseDurationMillis - currentVal).coerceAtLeast(0L)
                state.copy(
                    accumulatedWorkMillis = if (state.phase == TimerPhase.WORK) {
                        state.accumulatedWorkMillis + elapsed
                    } else state.accumulatedWorkMillis,
                    accumulatedBreakMillis = if (state.phase != TimerPhase.WORK) {
                        state.accumulatedBreakMillis + elapsed
                    } else state.accumulatedBreakMillis
                )
            } else state

            val outcome = if (state.timerMode == TimerModeType.STOPWATCH && finalState.accumulatedWorkMillis >= 60_000L) {
                SessionOutcome.COMPLETED
            } else {
                SessionOutcome.STOPPED_EARLY
            }
            completeAndReleaseSession(finalState, outcome)
        }
    }

    private fun handleSkipBreakFlexible() {
        val app = application as RegainFocusApplication
        val state = app.container.activeSessionStore.readSnapshotSync()
        if (!state.isActive || state.isStrictActive) return
        if (state.phase == TimerPhase.SHORT_BREAK || state.phase == TimerPhase.LONG_BREAK) {
            serviceScope.launch { advancePhase(state) }
        }
    }

    private fun startTickerLoop() {
        if (tickerJob?.isActive == true) return
        val app = application as RegainFocusApplication
        val bootId = ActiveStrictSessionStore.currentBootCountId(this)

        tickerJob = serviceScope.launch {
            syncAudioWithSettings()
            var lastNotificationSec = -1L

            while (isActive) {
                val state = app.container.activeSessionStore.readSnapshotSync()
                if (!state.isActive) break

                val nowWall = System.currentTimeMillis()
                val nowElapsed = SystemClock.elapsedRealtime()

                val displayMillis = StrictRemainingTimeCalculator.computePhaseRemainingMillis(
                    state = state,
                    currentWallMillis = nowWall,
                    currentElapsedRealtimeMillis = nowElapsed,
                    currentBootId = bootId
                )
                val strictTotalRem = StrictRemainingTimeCalculator.computeStrictTotalRemainingMillis(
                    state = state,
                    currentWallMillis = nowWall,
                    currentElapsedRealtimeMillis = nowElapsed,
                    currentBootId = bootId
                )

                _liveDisplayMillis.value = displayMillis
                _liveStrictRemainingMillis.value = strictTotalRem

                // Secondary foreground package watchdog via UsageStatsManager
                if (state.isActive && (!state.isPaused || state.isStrictActive)) {
                    runSecondaryForegroundWatchdog(state, nowWall, nowElapsed, bootId)
                }

                // Check phase/session completion
                if (state.timerMode == TimerModeType.STOPWATCH) {
                    if (state.isStrictActive && strictTotalRem <= 0L) {
                        val completedStopwatch = state.copy(accumulatedWorkMillis = displayMillis)
                        completeAndReleaseSession(completedStopwatch, SessionOutcome.COMPLETED)
                        break
                    }
                } else if (!state.isPaused && displayMillis <= 0L) {
                    val finishedAll = advancePhase(state)
                    if (finishedAll) break
                    continue
                }

                val sec = displayMillis / 1000L
                if (sec != lastNotificationSec) {
                    lastNotificationSec = sec
                    val notifMillis = if (state.isStrictActive) strictTotalRem else displayMillis
                    updateOngoingNotification(state, notifMillis)
                }

                delay(500L)
            }
        }
    }

    private suspend fun runSecondaryForegroundWatchdog(
        state: PersistedTimerSessionState,
        nowWall: Long,
        nowElapsed: Long,
        bootId: String
    ) {
        val app = application as RegainFocusApplication
        val fg = app.container.usageRepository.getRecentForegroundPackage(lookbackMillis = 2_500L) ?: return
        val (fgPkg, fgCls) = fg
        if (fgPkg == packageName) return

        val enabledLimits = app.container.database.appLimitDao().getEnabledLimitsOnce()
        val exceeded = app.container.usageRepository.getExceededDailyLimitPackages(enabledLimits)

        val decision = StrictBlockerPolicyEngine.evaluateForegroundEvent(
            selfPackageName = packageName,
            foregroundPackage = fgPkg,
            foregroundClassName = fgCls,
            launcherPackages = app.container.usageRepository.getLauncherPackages(),
            imePackages = app.container.usageRepository.getImePackages(),
            sessionState = state,
            currentWallMillis = nowWall,
            currentElapsedRealtimeMillis = nowElapsed,
            currentBootId = bootId,
            exceededDailyLimitPackages = exceeded
        )

        if (decision is StrictBlockerPolicyEngine.InterceptionDecision.BlockApp) {
            StrictBlockShieldActivity.launch(
                context = this@FocusTimerService,
                blockedPackage = decision.packageName,
                reasonLabel = if (state.isStrictActive) {
                    "Locked by active Strict Focus Session (${state.strictLevel.badge})."
                } else {
                    "Restricted during active Focus Session."
                }
            )
        }
    }

    private suspend fun advancePhase(state: PersistedTimerSessionState): Boolean {
        val app = application as RegainFocusApplication
        val settings = app.container.productivityRepository.settingsFlow.first()
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = ActiveStrictSessionStore.currentBootCountId(this)

        val updatedWork = if (state.phase == TimerPhase.WORK) {
            state.accumulatedWorkMillis + state.phaseDurationMillis
        } else state.accumulatedWorkMillis
        val updatedBreak = if (state.phase != TimerPhase.WORK) {
            state.accumulatedBreakMillis + state.phaseDurationMillis
        } else state.accumulatedBreakMillis

        if (state.timerMode == TimerModeType.COUNTDOWN) {
            completeAndReleaseSession(
                state.copy(accumulatedWorkMillis = updatedWork),
                SessionOutcome.COMPLETED
            )
            return true
        }

        // Pomodoro transition
        val transition = PomodoroPhaseEngine.nextPomodoroPhase(
            currentPhase = state.phase,
            currentCycle = state.currentCycle,
            totalCycles = state.totalCycles,
            workDurationMillis = state.workDurationMillis,
            shortBreakDurationMillis = state.shortBreakDurationMillis,
            longBreakDurationMillis = state.longBreakDurationMillis
        )

        if (transition.isSessionCompleted) {
            completeAndReleaseSession(
                state.copy(
                    accumulatedWorkMillis = updatedWork,
                    accumulatedBreakMillis = updatedBreak
                ),
                SessionOutcome.COMPLETED
            )
            return true
        }

        val nextState = state.copy(
            phase = transition.nextPhase,
            currentCycle = transition.nextCycle,
            isPaused = false,
            bootAnchorId = bootId,
            phaseStartWallMillis = nowWall,
            phaseDurationMillis = transition.nextDurationMillis,
            phaseEndWallMillis = nowWall + transition.nextDurationMillis,
            phaseEndElapsedRealtimeMillis = nowElapsed + transition.nextDurationMillis,
            accumulatedWorkMillis = updatedWork,
            accumulatedBreakMillis = updatedBreak
        )
        app.container.activeSessionStore.saveState(nextState)
        scheduleExactPhaseAlarm(nextState)
        syncAudioWithSettings()

        if (settings.enableBreakReminders || settings.enableSessionNotifications) {
            val alertTitle = when (transition.nextPhase) {
                TimerPhase.WORK -> "Round ${transition.nextCycle}/${state.totalCycles} — Focus Time"
                TimerPhase.SHORT_BREAK -> "Round Complete — Short Break (${transition.nextDurationMillis / 60_000L}m)"
                TimerPhase.LONG_BREAK -> "All Rounds Done — Long Break (${transition.nextDurationMillis / 60_000L}m)"
                else -> "Phase Updated"
            }
            app.container.notificationHelper.notifyPhaseOrSessionAlert(
                title = alertTitle,
                message = if (state.isStrictActive) {
                    "Strict Mode remains locked until your full session finishes."
                } else {
                    "Stay mindful and keep your momentum going!"
                }
            )
        }
        return false
    }

    private suspend fun completeAndReleaseSession(
        finalState: PersistedTimerSessionState,
        outcome: SessionOutcome
    ) {
        val app = application as RegainFocusApplication
        soundSynthesizer.stop()
        cancelExactPhaseAlarm()

        // 1. Release all Mode B Device Owner restrictions & package suspensions immediately
        app.container.deviceOwnerManager.releaseAllDeviceOwnerRestrictions(finalState.blockedPackages)

        // 2. Persist verified FocusSessionEntity to Room Database
        val nowWall = System.currentTimeMillis()
        val plannedWorkSec = when (finalState.timerMode) {
            TimerModeType.POMODORO -> (finalState.workDurationMillis * finalState.totalCycles) / 1000L
            TimerModeType.COUNTDOWN -> finalState.workDurationMillis / 1000L
            TimerModeType.STOPWATCH -> finalState.accumulatedWorkMillis / 1000L
        }
        val plannedBreakSec = if (finalState.timerMode == TimerModeType.POMODORO) {
            ((finalState.shortBreakDurationMillis * (finalState.totalCycles - 1).coerceAtLeast(0)) +
                finalState.longBreakDurationMillis) / 1000L
        } else 0L

        val effectiveWorkSec = (finalState.accumulatedWorkMillis / 1000L).coerceAtLeast(
            if (outcome != SessionOutcome.STOPPED_EARLY) plannedWorkSec else 0L
        )

        if (effectiveWorkSec > 0L || outcome != SessionOutcome.STOPPED_EARLY) {
            app.container.productivityRepository.recordSession(
                FocusSessionEntity(
                    modeType = finalState.timerMode,
                    tag = finalState.sessionTag,
                    note = finalState.sessionNote,
                    blockingGroupId = finalState.blockingGroupId,
                    blockingGroupName = finalState.blockingGroupName,
                    strictEnforcementMode = finalState.strictLevel,
                    startTimeEpochMillis = finalState.sessionStartWallMillis.takeIf { it > 0L } ?: nowWall,
                    endTimeEpochMillis = nowWall,
                    plannedWorkSeconds = plannedWorkSec,
                    plannedBreakSeconds = plannedBreakSec,
                    effectiveWorkSeconds = effectiveWorkSec,
                    effectiveBreakSeconds = finalState.accumulatedBreakMillis / 1000L,
                    completedCycles = finalState.currentCycle,
                    interceptionCount = finalState.interceptionCount,
                    outcome = outcome
                )
            )
        }

        // 3. Clear active session store so apps unlock immediately
        app.container.activeSessionStore.clearSession()
        _liveDisplayMillis.value = 0L
        _liveStrictRemainingMillis.value = 0L

        // 4. Notify user of session completion
        val settings = app.container.productivityRepository.settingsFlow.first()
        if (settings.enableSessionNotifications && outcome != SessionOutcome.STOPPED_EARLY) {
            val mins = (effectiveWorkSec / 60L).coerceAtLeast(1L)
            app.container.notificationHelper.notifyPhaseOrSessionAlert(
                title = "Focus Session Complete! 🎉",
                message = "You completed ${mins}m of ${finalState.sessionTag} focus" +
                    (if (finalState.blockedPackages.isNotEmpty()) " and unlocked ${finalState.blockedPackages.size} apps." else ".")
            )
        }

        stopForegroundSafely()
        stopSelf()
    }

    private suspend fun syncAudioWithSettings() {
        val app = application as RegainFocusApplication
        val state = app.container.activeSessionStore.readSnapshotSync()
        val settings = app.container.productivityRepository.settingsFlow.first()
        val shouldPlay = state.isActive &&
            !state.isPaused &&
            settings.selectedSoundscape != SoundscapeType.NONE &&
            (state.phase == TimerPhase.WORK || settings.playSoundDuringBreaks)

        soundSynthesizer.updatePlayback(
            type = settings.selectedSoundscape,
            volume = settings.soundVolume,
            shouldPlay = shouldPlay
        )
    }

    private fun promoteToForeground(state: PersistedTimerSessionState) {
        val app = application as RegainFocusApplication
        val notification = app.container.notificationHelper.buildOngoingTimerNotification(
            state = state,
            formattedTime = TimeFormatters.formatMillisClock(state.phaseDurationMillis)
        )
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationHelper.ONGOING_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NotificationHelper.ONGOING_NOTIFICATION_ID, notification)
            }
        }
    }

    private fun updateOngoingNotification(state: PersistedTimerSessionState, millis: Long) {
        val app = application as RegainFocusApplication
        val notification = app.container.notificationHelper.buildOngoingTimerNotification(
            state = state,
            formattedTime = TimeFormatters.formatMillisClock(millis)
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationHelper.ONGOING_NOTIFICATION_ID, notification)
    }

    private fun scheduleExactPhaseAlarm(state: PersistedTimerSessionState) {
        if (!state.isActive || state.isPaused || state.timerMode == TimerModeType.STOPWATCH) return
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerWall = if (state.isStrictActive) {
            minOf(state.phaseEndWallMillis, state.strictHardEndWallMillis).takeIf { it > 0L } ?: state.phaseEndWallMillis
        } else {
            state.phaseEndWallMillis
        }
        if (triggerWall <= System.currentTimeMillis()) return

        val pi = PendingIntent.getBroadcast(
            this,
            901,
            Intent(this, FocusAlarmReceiver::class.java).apply {
                action = FocusAlarmReceiver.ACTION_TIMER_DEADLINE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerWall, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerWall, pi)
            }
        }
    }

    private fun cancelExactPhaseAlarm() {
        val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pi = PendingIntent.getBroadcast(
            this,
            901,
            Intent(this, FocusAlarmReceiver::class.java).apply {
                action = FocusAlarmReceiver.ACTION_TIMER_DEADLINE
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        ) ?: return
        am.cancel(pi)
    }

    private fun stopForegroundSafely() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep session alive if user swipes RegainFocus from Recents!
        val app = application as RegainFocusApplication
        val state = app.container.activeSessionStore.readSnapshotSync()
        if (state.isActive) {
            scheduleExactPhaseAlarm(state)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        soundSynthesizer.stop()
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_SESSION = "com.regainfocus.app.ACTION_START_SESSION"
        const val ACTION_RECOVER_OR_SYNC = "com.regainfocus.app.ACTION_RECOVER_OR_SYNC"
        const val ACTION_PAUSE_OR_RESUME = "com.regainfocus.app.ACTION_PAUSE_OR_RESUME"
        const val ACTION_STOP_FLEXIBLE = "com.regainfocus.app.ACTION_STOP_FLEXIBLE"
        const val ACTION_SKIP_BREAK_FLEXIBLE = "com.regainfocus.app.ACTION_SKIP_BREAK_FLEXIBLE"
        const val ACTION_REFRESH_AUDIO = "com.regainfocus.app.ACTION_REFRESH_AUDIO"

        private val _liveDisplayMillis = MutableStateFlow(0L)
        val liveDisplayMillis: StateFlow<Long> = _liveDisplayMillis.asStateFlow()

        private val _liveStrictRemainingMillis = MutableStateFlow(0L)
        val liveStrictRemainingMillis: StateFlow<Long> = _liveStrictRemainingMillis.asStateFlow()

        fun startNewSessionService(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_START_SESSION
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun triggerExpiryCheck(context: Context) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                action = ACTION_RECOVER_OR_SYNC
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun sendServiceAction(context: Context, action: String) {
            val intent = Intent(context, FocusTimerService::class.java).apply {
                this.action = action
            }
            runCatching { context.startService(intent) }
        }
    }
}
