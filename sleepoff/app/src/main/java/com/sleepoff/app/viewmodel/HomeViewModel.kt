package com.sleepoff.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sleepoff.app.SleepOffApplication
import com.sleepoff.app.bluetooth.AccessibilityHelperStatus
import com.sleepoff.app.domain.BluetoothStatus
import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.domain.TimerSnapshot
import com.sleepoff.app.domain.TimerState
import com.sleepoff.app.media.NotificationListenerAccess
import com.sleepoff.app.timer.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single ViewModel used by Home, Configure, Schedule, History, Settings and
 * Setup screens. The timer state is owned by TimerService; this VM exposes it
 * as StateFlows and dispatches intents to the service.
 */
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val application: SleepOffApplication = app as SleepOffApplication
    private val ctx: Context = app.applicationContext

    // ---- bluetooth status ----
    val bluetoothStatus: StateFlow<BluetoothStatus> =
        application.bluetooth.observeStatus()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
                application.bluetooth.currentStatus())

    // ---- preferences ----
    val lastDurationMinutes = application.prefs.lastDurationMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)
    val lastMode = application.prefs.lastMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepMode.MUSIC_BT)
    val btDelaySeconds = application.prefs.btDelaySeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)
    val fadeMinutes = application.prefs.fadeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)
    val restoreVolume = application.prefs.restoreVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val batterySaver = application.prefs.batterySaver
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val scheduleEnabled = application.prefs.scheduleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val scheduleHour = application.prefs.scheduleHourOfDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 23)
    val scheduleMinute = application.prefs.scheduleMinute
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val scheduleDuration = application.prefs.scheduleDurationMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 45)
    val scheduleMode = application.prefs.scheduleMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepMode.MUSIC_BT)

    // ---- timer snapshot, updated every second while running ----
    val timer: StateFlow<TimerSnapshot> = run {
        val ticker: Flow<Unit> = flow {
            while (viewModelScope.isActive) {
                emit(Unit)
                delay(1_000L)
            }
        }
        combine(
            application.prefs.timerState,
            application.prefs.timerEndEpochMs,
            application.prefs.timerPausedRemainingMs,
            application.prefs.timerDurationMs,
            application.prefs.timerMode,
            ticker
        ) { state, end, pausedRem, dur, mode, _ ->
            val now = System.currentTimeMillis()
            val remaining = when (state) {
                TimerState.RUNNING -> (end - now).coerceAtLeast(0L)
                TimerState.PAUSED -> pausedRem
                TimerState.IDLE -> 0L
            }
            TimerSnapshot(
                state = state,
                remainingMs = remaining,
                endEpochMs = end,
                durationMs = dur,
                mode = mode
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TimerSnapshot())
    }

    // ---- permissions / setup status ----
    private val _permState = MutableStateFlow(PermissionsState())
    val permState: StateFlow<PermissionsState> = _permState.asStateFlow()

    data class PermissionsState(
        val notificationsGranted: Boolean = true,
        val bluetoothConnectGranted: Boolean = true,
        val exactAlarmGranted: Boolean = true,
        val batteryOptExempt: Boolean = false,
        val notificationListener: Boolean = false,
        val accessibilityHelper: Boolean = false,
        val directBtDisableSupported: Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    )

    init {
        refreshPermissionState()
        // On cold start, if DataStore says we're running but the service
        // isn't foreground (process death), kick the service so it recovers.
        viewModelScope.launch(Dispatchers.Default) {
            val snap = application.prefs.currentTimerSnapshot()
            if (snap.state != TimerState.IDLE) {
                val i = Intent(ctx, TimerService::class.java).apply {
                    action = TimerService.ACTION_RESUME_AFTER_BOOT
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            }
        }
    }

    fun refreshPermissionState() {
        val pm = ctx.packageManager
        val notificationsGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                pm.checkPermission(android.Manifest.permission.POST_NOTIFICATIONS, ctx.packageName) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
        val btGranted =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                pm.checkPermission(android.Manifest.permission.BLUETOOTH_CONNECT, ctx.packageName) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
        val exactGranted = application.alarmScheduler.canScheduleExactAlarms()

        val pm2 = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryExempt = pm2.isIgnoringBatteryOptimizations(ctx.packageName)

        _permState.value = PermissionsState(
            notificationsGranted = notificationsGranted,
            bluetoothConnectGranted = btGranted,
            exactAlarmGranted = exactGranted,
            batteryOptExempt = batteryExempt,
            notificationListener = NotificationListenerAccess.isGranted(ctx),
            accessibilityHelper = AccessibilityHelperStatus.isEnabled(ctx),
            directBtDisableSupported = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        )
    }

    // ---- timer controls ----

    fun startTimer(durationMinutes: Int, mode: SleepMode) {
        viewModelScope.launch {
            application.prefs.setLastDurationMinutes(durationMinutes)
            application.prefs.setLastMode(mode)
            val i = TimerService.startIntent(ctx, durationMinutes.toLong() * 60_000L, mode)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
    }

    fun pauseTimer() = ctx.startService(TimerService.pauseIntent(ctx))
    fun resumeTimer() = ctx.startService(TimerService.resumeIntent(ctx))
    fun cancelTimer() = ctx.startService(TimerService.cancelIntent(ctx))
    fun extendBy(minutes: Int) = ctx.startService(TimerService.extendIntent(ctx, minutes.toLong() * 60_000L))

    // ---- settings setters ----

    fun setBtDelay(secs: Int) = viewModelScope.launch { application.prefs.setBtDelaySeconds(secs) }
    fun setFadeMinutes(min: Int) = viewModelScope.launch { application.prefs.setFadeMinutes(min) }
    fun setRestoreVolume(restore: Boolean) = viewModelScope.launch { application.prefs.setRestoreVolume(restore) }
    fun setBatterySaver(saver: Boolean) = viewModelScope.launch { application.prefs.setBatterySaver(saver) }

    fun setSchedule(enabled: Boolean, hour: Int, min: Int, duration: Int, mode: SleepMode) {
        viewModelScope.launch {
            application.prefs.setSchedule(enabled, hour, min, duration, mode)
            if (enabled) application.alarmScheduler.scheduleDaily(hour, min)
            else application.alarmScheduler.cancelDaily()
        }
    }

    fun clearHistory() = viewModelScope.launch { application.prefs.clearHistory() }

    /** History (JSON-encoded list) — exposed for the History screen. */
    val historyJson: kotlinx.coroutines.flow.Flow<String> = application.prefs.historyJson

    // ---- permission-intent helpers (consumed by the Setup/Settings screens) ----

    fun openNotificationsSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun openBluetoothSettings() {
        ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .setData(Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun requestBatteryExemption() {
        @Suppress("BatteryLife")
        val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun openNotificationListenerSettings() {
        ctx.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAccessibilitySettings() {
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
