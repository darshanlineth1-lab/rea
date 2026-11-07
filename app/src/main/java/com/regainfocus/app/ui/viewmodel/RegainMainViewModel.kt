package com.regainfocus.app.ui.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.local.AppDailyLimitEntity
import com.regainfocus.app.data.local.BlockingGroupEntity
import com.regainfocus.app.data.local.FocusScheduleEntity
import com.regainfocus.app.data.local.FocusSessionEntity
import com.regainfocus.app.data.local.HabitCompletionEntity
import com.regainfocus.app.data.local.HabitEntity
import com.regainfocus.app.data.local.InterceptionEventEntity
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.data.repository.StrictSessionMutationLockedException
import com.regainfocus.app.domain.engine.PomodoroPhaseEngine
import com.regainfocus.app.domain.engine.StreakAndAchievementEngine
import com.regainfocus.app.domain.model.AchievementProgress
import com.regainfocus.app.domain.model.DeviceEnforcementStatus
import com.regainfocus.app.domain.model.InstalledAppInfo
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.ScreenTimeOverview
import com.regainfocus.app.domain.model.SoundscapeType
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.ThemeMode
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.domain.model.TimerPhase
import com.regainfocus.app.domain.model.UserSettings
import com.regainfocus.app.service.FocusTimerService
import com.regainfocus.app.ui.components.SensitivePermissionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class RegainMainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as RegainFocusApplication
    private val container = app.container
    private val repo = container.productivityRepository

    val settings: StateFlow<UserSettings> = repo.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings()
    )

    val activeSession: StateFlow<PersistedTimerSessionState> = container.activeSessionStore.sessionStateFlow

    val liveDisplayMillis: StateFlow<Long> = FocusTimerService.liveDisplayMillis
    val liveStrictRemainingMillis: StateFlow<Long> = FocusTimerService.liveStrictRemainingMillis

    val sessions: StateFlow<List<FocusSessionEntity>> = repo.sessionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val blockingGroups: StateFlow<List<BlockingGroupEntity>> = repo.blockingGroupsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val appLimits: StateFlow<List<AppDailyLimitEntity>> = repo.appLimitsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val schedules: StateFlow<List<FocusScheduleEntity>> = repo.schedulesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val habits: StateFlow<List<HabitEntity>> = repo.habitsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val habitCompletions: StateFlow<List<HabitCompletionEntity>> = repo.habitCompletionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val totalInterceptions: StateFlow<Int> = repo.totalInterceptionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0
    )

    val recentInterceptions: StateFlow<List<InterceptionEventEntity>> = repo.recentInterceptionsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val streakSummary: StateFlow<StreakAndAchievementEngine.StreakSummary> = combine(
        sessions,
        settings
    ) { sessionList, userSettings ->
        StreakAndAchievementEngine.computeStreaks(
            sessions = sessionList,
            todayEpochDay = LocalDate.now().toEpochDay(),
            dailyGoalMinutes = userSettings.dailyFocusGoalMinutes
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StreakAndAchievementEngine.StreakSummary(0, 0, emptySet(), emptySet())
    )

    val achievements: StateFlow<List<AchievementProgress>> = combine(
        sessions,
        streakSummary,
        totalInterceptions
    ) { sessionList, streaks, interceptions ->
        StreakAndAchievementEngine.computeAchievements(sessionList, streaks, interceptions)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppInfo>> = _installedApps.asStateFlow()

    private val _screenTimeOverview = MutableStateFlow(
        ScreenTimeOverview(
            hasUsagePermission = false,
            todayTotalMillis = 0L,
            weeklyTotalMillis = 0L,
            monthlyTotalMillis = 0L,
            dailyAverageWeekMillis = 0L,
            hourlyMinutesToday = List(24) { 0 },
            topAppsToday = emptyList(),
            topAppsWeek = emptyList(),
            topAppsMonth = emptyList(),
            last7DaysScreenMinutes = emptyList(),
            last30DaysScreenMinutes = emptyList()
        )
    )
    val screenTimeOverview: StateFlow<ScreenTimeOverview> = _screenTimeOverview.asStateFlow()

    private val _isLoadingUsage = MutableStateFlow(false)
    val isLoadingUsage: StateFlow<Boolean> = _isLoadingUsage.asStateFlow()

    private val _enforcementStatus = MutableStateFlow(
        container.deviceOwnerManager.getEnforcementStatus(
            container.notificationHelper.hasNotificationPermission()
        )
    )
    val enforcementStatus: StateFlow<DeviceEnforcementStatus> = _enforcementStatus.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    init {
        refreshPermissionsAndData()
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun postMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun recordPermissionDisclosureConsent(type: SensitivePermissionType) {
        viewModelScope.launch {
            repo.updateSettings { current ->
                when (type) {
                    SensitivePermissionType.USAGE_ACCESS ->
                        current.copy(hasAcceptedUsageAccessDisclosure = true)
                    SensitivePermissionType.OVERLAY_SHIELD ->
                        current.copy(hasAcceptedOverlayDisclosure = true)
                    SensitivePermissionType.ACCESSIBILITY_SHIELD ->
                        current.copy(hasAcceptedAccessibilityDisclosure = true)
                }
            }
        }
    }

    fun refreshPermissionsAndData(forceAppsReload: Boolean = false) {
        _enforcementStatus.value = container.deviceOwnerManager.getEnforcementStatus(
            container.notificationHelper.hasNotificationPermission()
        )
        viewModelScope.launch {
            _installedApps.value = container.usageRepository.getLaunchableApps(forceAppsReload)
            refreshScreenTimeOverview()
        }
    }

    fun refreshScreenTimeOverview() {
        viewModelScope.launch {
            _isLoadingUsage.value = true
            try {
                val limits = appLimits.value
                val sess = sessions.value
                val goal = settings.value.dailyScreenTimeGoalMinutes
                _screenTimeOverview.value = container.usageRepository.loadScreenTimeOverview(
                    limits = limits,
                    sessions = sess,
                    dailyScreenTimeGoalMinutes = goal
                )
            } finally {
                _isLoadingUsage.value = false
            }
        }
    }

    fun completeOnboarding(dailyFocusMinutes: Int, dailyScreenGoalMinutes: Int) {
        viewModelScope.launch {
            repo.updateSettings {
                it.copy(
                    hasCompletedOnboarding = true,
                    dailyFocusGoalMinutes = dailyFocusMinutes.coerceIn(15, 720),
                    dailyScreenTimeGoalMinutes = dailyScreenGoalMinutes.coerceIn(30, 960)
                )
            }
        }
    }

    fun startFocusSession(
        mode: TimerModeType,
        workMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        cycles: Int,
        strictLevel: StrictEnforcementLevel,
        selectedGroup: BlockingGroupEntity?,
        extraSelectedPackages: Set<String>,
        sessionTag: String,
        sessionNote: String
    ): Boolean {
        val current = activeSession.value
        if (current.isActive) {
            _snackbarMessage.value = "A focus session is already running."
            return false
        }

        val status = container.deviceOwnerManager.getEnforcementStatus(
            container.notificationHelper.hasNotificationPermission()
        )

        if (strictLevel != StrictEnforcementLevel.NONE && !status.isBlockerReady) {
            _snackbarMessage.value = if (status.supportsAccessibilityBlocker) {
                "Review and enable the Accessibility Focus Shield first to activate Strict Mode."
            } else {
                "Enable Usage Access and Display Over Other Apps first to activate Strict Mode."
            }
            return false
        }

        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val bootId = ActiveStrictSessionStore.currentBootCountId(app)

        val workMillis = workMinutes.coerceIn(1, 360) * 60_000L
        val shortMillis = shortBreakMinutes.coerceIn(0, 60) * 60_000L
        val longMillis = longBreakMinutes.coerceIn(0, 90) * 60_000L
        val safeCycles = cycles.coerceIn(1, 12)

        val totalStrictMillis = PomodoroPhaseEngine.calculateTotalStrictDurationMillis(
            mode = mode,
            workDurationMillis = workMillis,
            shortBreakDurationMillis = shortMillis,
            longBreakDurationMillis = longMillis,
            totalCycles = safeCycles
        )

        val combinedPackages = buildSet {
            selectedGroup?.blockedPackages?.let { addAll(it) }
            addAll(extraSelectedPackages)
        }

        val normalizedStrict = if (strictLevel == StrictEnforcementLevel.NONE) {
            StrictEnforcementLevel.NONE
        } else {
            StrictEnforcementLevel.STANDARD_STRICT
        }

        val newState = PersistedTimerSessionState(
            isActive = true,
            sessionId = nowWall,
            timerMode = mode,
            phase = TimerPhase.WORK,
            isPaused = false,
            sessionStartWallMillis = nowWall,
            phaseStartWallMillis = nowWall,
            phaseDurationMillis = if (mode == TimerModeType.STOPWATCH) 0L else workMillis,
            phaseEndWallMillis = if (mode == TimerModeType.STOPWATCH) 0L else nowWall + workMillis,
            bootAnchorId = bootId,
            phaseEndElapsedRealtimeMillis = if (mode == TimerModeType.STOPWATCH) 0L else nowElapsed + workMillis,
            strictHardEndWallMillis = if (normalizedStrict != StrictEnforcementLevel.NONE) nowWall + totalStrictMillis else 0L,
            strictHardEndElapsedRealtimeMillis = if (normalizedStrict != StrictEnforcementLevel.NONE) nowElapsed + totalStrictMillis else 0L,
            stopwatchBaseElapsedRealtimeMillis = if (mode == TimerModeType.STOPWATCH) nowElapsed else 0L,
            stopwatchAccumulatedMillis = 0L,
            workDurationMillis = workMillis,
            shortBreakDurationMillis = shortMillis,
            longBreakDurationMillis = longMillis,
            currentCycle = 1,
            totalCycles = safeCycles,
            accumulatedWorkMillis = 0L,
            accumulatedBreakMillis = 0L,
            strictLevel = normalizedStrict,
            blockedPackages = combinedPackages,
            blockingGroupId = selectedGroup?.id,
            blockingGroupName = selectedGroup?.name,
            sessionTag = sessionTag.ifBlank { "Study" },
            sessionNote = sessionNote.trim(),
            interceptionCount = 0
        )

        container.activeSessionStore.saveStateBlocking(newState)
        FocusTimerService.startNewSessionService(app)
        return true
    }

    fun togglePauseFlexible() {
        if (activeSession.value.isStrictActive) {
            _snackbarMessage.value = "Strict Mode cannot be paused — stay focused until the timer finishes!"
            return
        }
        FocusTimerService.sendServiceAction(app, FocusTimerService.ACTION_PAUSE_OR_RESUME)
    }

    fun stopSessionFlexible() {
        if (activeSession.value.isStrictActive) {
            _snackbarMessage.value = "Strict Mode cannot be stopped early."
            return
        }
        FocusTimerService.sendServiceAction(app, FocusTimerService.ACTION_STOP_FLEXIBLE)
    }

    fun skipBreakFlexible() {
        if (activeSession.value.isStrictActive) {
            _snackbarMessage.value = "Breaks are locked to your Strict schedule."
            return
        }
        FocusTimerService.sendServiceAction(app, FocusTimerService.ACTION_SKIP_BREAK_FLEXIBLE)
    }

    fun selectSoundscape(soundscape: SoundscapeType) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(selectedSoundscape = soundscape) }
            FocusTimerService.sendServiceAction(app, FocusTimerService.ACTION_REFRESH_AUDIO)
        }
    }

    fun updateSoundVolume(volume: Float) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(soundVolume = volume.coerceIn(0f, 1f)) }
            FocusTimerService.sendServiceAction(app, FocusTimerService.ACTION_REFRESH_AUDIO)
        }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            repo.updateSettings { it.copy(themeMode = themeMode) }
        }
    }

    fun updateUserSettings(
        requiresUnlockedStrict: Boolean = false,
        transform: (UserSettings) -> UserSettings
    ) {
        viewModelScope.launch {
            try {
                repo.updateSettings(requiresUnlockedStrict, transform)
                FocusTimerService.sendServiceAction(app, FocusTimerService.ACTION_REFRESH_AUDIO)
            } catch (e: StrictSessionMutationLockedException) {
                _snackbarMessage.value = e.message
            }
        }
    }

    fun saveBlockingGroup(group: BlockingGroupEntity) {
        viewModelScope.launch {
            try {
                repo.saveBlockingGroup(group)
                _snackbarMessage.value = "Saved blocking group \"${group.name}\"."
            } catch (e: StrictSessionMutationLockedException) {
                _snackbarMessage.value = e.message
            }
        }
    }

    fun deleteBlockingGroup(group: BlockingGroupEntity) {
        viewModelScope.launch {
            try {
                repo.deleteBlockingGroup(group)
                _snackbarMessage.value = "Deleted \"${group.name}\"."
            } catch (e: StrictSessionMutationLockedException) {
                _snackbarMessage.value = e.message
            }
        }
    }

    fun upsertDailyAppLimit(
        packageName: String,
        appLabel: String,
        limitMinutes: Int,
        enabled: Boolean,
        lockStrictHours: Int
    ) {
        viewModelScope.launch {
            try {
                repo.upsertAppLimit(packageName, appLabel, limitMinutes, enabled, lockStrictHours)
                refreshScreenTimeOverview()
                _snackbarMessage.value = "Saved ${limitMinutes}m daily limit for $appLabel."
            } catch (e: StrictSessionMutationLockedException) {
                _snackbarMessage.value = e.message
            }
        }
    }

    fun deleteDailyAppLimit(packageName: String) {
        viewModelScope.launch {
            try {
                repo.removeAppLimit(packageName)
                refreshScreenTimeOverview()
            } catch (e: StrictSessionMutationLockedException) {
                _snackbarMessage.value = e.message
            }
        }
    }

    fun saveSchedule(schedule: FocusScheduleEntity) {
        viewModelScope.launch {
            repo.saveSchedule(schedule)
            _snackbarMessage.value = "Saved schedule \"${schedule.title}\"."
        }
    }

    fun deleteSchedule(schedule: FocusScheduleEntity) {
        viewModelScope.launch {
            try {
                repo.deleteSchedule(schedule)
            } catch (e: StrictSessionMutationLockedException) {
                _snackbarMessage.value = e.message
            }
        }
    }

    fun saveHabit(title: String, category: String, targetMinutes: Int, colorHex: Long) {
        viewModelScope.launch {
            if (title.isBlank()) return@launch
            repo.saveHabit(
                HabitEntity(
                    title = title.trim(),
                    category = category.trim().ifBlank { "Study" },
                    targetMinutesPerDay = targetMinutes.coerceIn(5, 480),
                    colorHex = colorHex,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            _snackbarMessage.value = "Added daily habit \"${title.trim()}\"."
        }
    }

    fun toggleHabitToday(habit: HabitEntity) {
        viewModelScope.launch {
            repo.toggleHabitToday(habit)
        }
    }

    fun archiveHabit(habitId: Long) {
        viewModelScope.launch {
            repo.archiveHabit(habitId)
        }
    }

    suspend fun exportBackupJson(): String = repo.exportBackupJson()

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            try {
                repo.importBackupJson(rawJson)
                _snackbarMessage.value = "Backup restored successfully."
            } catch (e: Exception) {
                _snackbarMessage.value = e.message ?: "Invalid backup JSON file."
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            try {
                repo.clearAllHistory()
                _snackbarMessage.value = "Cleared session history and counters."
            } catch (e: StrictSessionMutationLockedException) {
                _snackbarMessage.value = e.message
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return RegainMainViewModel(application) as T
                }
            }
    }
}
