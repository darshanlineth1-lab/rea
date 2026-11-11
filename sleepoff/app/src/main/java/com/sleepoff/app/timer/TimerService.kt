package com.sleepoff.app.timer

import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.sleepoff.app.SleepOffApplication
import com.sleepoff.app.bluetooth.AccessibilityHelperStatus
import com.sleepoff.app.bluetooth.BluetoothController
import com.sleepoff.app.bluetooth.BluetoothToggleAccessibilityService
import com.sleepoff.app.domain.HistoryEntry
import com.sleepoff.app.domain.SleepMode
import com.sleepoff.app.domain.TimerState
import com.sleepoff.app.media.MediaControllerHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that drives the countdown, updates the persistent
 * notification, and executes the completion sequence (pause → BT off →
 * history). The service is the single source of truth for running state;
 * the UI (MainActivity / ViewModel) reads from DataStore Flows which the
 * service writes to.
 */
class TimerService : Service() {

    companion object {
        private const val TAG = "TimerService"

        // Actions that external code sends to the service.
        const val ACTION_START = "com.sleepoff.app.action.START"
        const val ACTION_PAUSE = "com.sleepoff.app.action.PAUSE"
        const val ACTION_RESUME = "com.sleepoff.app.action.RESUME"
        const val ACTION_CANCEL = "com.sleepoff.app.action.CANCEL"
        const val ACTION_EXTEND = "com.sleepoff.app.action.EXTEND"
        const val ACTION_COMPLETE = "com.sleepoff.app.action.COMPLETE"
        const val ACTION_DAILY_START = "com.sleepoff.app.action.DAILY_START"
        const val ACTION_RESUME_AFTER_BOOT = "com.sleepoff.app.action.RESUME_AFTER_BOOT"

        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_MODE = "mode"
        const val EXTRA_EXTEND_MS = "extend_ms"

        // Tick interval while running. One second is plenty for a countdown
        // we update in the notification; the UI collects with ~1s too.
        private const val TICK_MS = 1_000L

        fun startIntent(context: Context, durationMs: Long, mode: SleepMode): Intent =
            Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DURATION_MS, durationMs)
                putExtra(EXTRA_MODE, mode.name)
            }

        fun pauseIntent(context: Context): Intent =
            Intent(context, TimerService::class.java).setAction(ACTION_PAUSE)
        fun resumeIntent(context: Context): Intent =
            Intent(context, TimerService::class.java).setAction(ACTION_RESUME)
        fun cancelIntent(context: Context): Intent =
            Intent(context, TimerService::class.java).setAction(ACTION_CANCEL)
        fun extendIntent(context: Context, addMs: Long): Intent =
            Intent(context, TimerService::class.java).apply {
                action = ACTION_EXTEND
                putExtra(EXTRA_EXTEND_MS, addMs)
            }
    }

    private lateinit var prefs: com.sleepoff.app.data.SleepPreferences
    private lateinit var scheduler: TimerAlarmScheduler
    private lateinit var notif: NotificationHelper
    private lateinit var bt: BluetoothController
    private lateinit var media: MediaControllerHelper
    private lateinit var wl: PowerManager.WakeLock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var state: TimerState = TimerState.IDLE
    @Volatile private var endEpochMs: Long = 0L
    @Volatile private var pausedRemainingMs: Long = 0L
    @Volatile private var durationMs: Long = 0L
    @Volatile private var mode: SleepMode = SleepMode.MUSIC_BT
    @Volatile private var startedEpochMs: Long = 0L
    @Volatile private var preFadeVolume: Int = -1

    private var tickJob: Job? = null

    // ---- lifecycle ----

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as SleepOffApplication
        prefs = app.prefs
        scheduler = app.alarmScheduler
        bt = app.bluetooth
        media = app.media
        notif = NotificationHelper(this)
        notif.ensureChannels()

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SleepOff::TimerWakeLock"
        ).apply { setReferenceCounted(false) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> cmdStart(
                intent.getLongExtra(EXTRA_DURATION_MS, 0L),
                SleepMode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: SleepMode.MUSIC_BT.name)
            )
            ACTION_PAUSE -> cmdPause()
            ACTION_RESUME -> cmdResume()
            ACTION_CANCEL -> cmdCancel()
            ACTION_EXTEND -> {
                val add = intent.getLongExtra(EXTRA_EXTEND_MS, 0L)
                cmdExtend(add)
            }
            ACTION_COMPLETE -> cmdComplete()
            ACTION_DAILY_START -> cmdDailyStart()
            ACTION_RESUME_AFTER_BOOT -> cmdResumeAfterBoot()
            null -> {
                // System restarted the service; attempt to recover from DataStore.
                scope.launch { recoverFromStore() }
            }
        }
        // Sticky restart is NOT used because AlarmManager is the authoritative
        // trigger; if the service is killed mid-countdown the alarm will still
        // fire and restart us with ACTION_COMPLETE. We do, however, re-hydrate
        // state from DataStore immediately above.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        tickJob?.cancel()
        scope.cancel()
        if (wl.isHeld) runCatching { wl.release() }
    }

    // ---- commands ----

    private fun cmdStart(durMs: Long, m: SleepMode) {
        if (durMs <= 0) {
            Log.w(TAG, "Refusing to start with duration $durMs")
            return
        }
        val now = System.currentTimeMillis()
        state = TimerState.RUNNING
        endEpochMs = now + durMs
        durationMs = durMs
        mode = m
        startedEpochMs = now
        pausedRemainingMs = 0L
        persist()
        scheduler.scheduleFire(endEpochMs)
        startForegroundOrPromote()
        startTicker()
        Log.i(TAG, "Started $durMs ms ($m), ends at $endEpochMs")
    }

    private fun cmdPause() {
        if (state != TimerState.RUNNING) return
        pausedRemainingMs = (endEpochMs - System.currentTimeMillis()).coerceAtLeast(0)
        state = TimerState.PAUSED
        scheduler.cancelFire()
        persist()
        updateOngoingNotification()
        Log.i(TAG, "Paused with $pausedRemainingMs remaining")
    }

    private fun cmdResume() {
        if (state != TimerState.PAUSED) return
        val rem = if (pausedRemainingMs > 0) pausedRemainingMs
                  else durationMs
        endEpochMs = System.currentTimeMillis() + rem
        state = TimerState.RUNNING
        pausedRemainingMs = 0L
        scheduler.scheduleFire(endEpochMs)
        persist()
        startForegroundOrPromote()
        startTicker()
        Log.i(TAG, "Resumed, ends at $endEpochMs")
    }

    private fun cmdCancel() {
        state = TimerState.IDLE
        endEpochMs = 0L
        pausedRemainingMs = 0L
        durationMs = 0L
        scheduler.cancelFire()
        persist()
        stopTicker()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notif.cancelOngoing()
        stopSelf()
        Log.i(TAG, "Cancelled")
    }

    private fun cmdExtend(addMs: Long) {
        when (state) {
            TimerState.RUNNING -> {
                endEpochMs += addMs
                durationMs += addMs
                scheduler.scheduleFire(endEpochMs)
                persist()
                updateOngoingNotification()
                Log.i(TAG, "Extended by $addMs ms; new end $endEpochMs")
            }
            TimerState.PAUSED -> {
                pausedRemainingMs += addMs
                durationMs += addMs
                persist()
                updateOngoingNotification()
            }
            TimerState.IDLE -> { /* nothing */ }
        }
    }

    private fun cmdDailyStart() {
        scope.launch {
            val enabled = prefs.scheduleEnabled.first()
            if (!enabled) return@launch
            // Only auto-start if there is no timer already running.
            val snap = prefs.currentTimerSnapshot()
            if (snap.state != TimerState.IDLE) return@launch
            val dur = prefs.scheduleDurationMinutes.first().coerceAtLeast(1).toLong() * 60_000L
            val m = prefs.scheduleMode.first()
            cmdStart(dur, m)
        }
    }

    private fun cmdResumeAfterBoot() {
        scope.launch { recoverFromStore() }
    }

    private suspend fun recoverFromStore() {
        val snap = prefs.currentTimerSnapshot()
        state = snap.state
        endEpochMs = snap.endEpochMs
        pausedRemainingMs = snap.pausedRemainingMs
        durationMs = snap.durationMs
        mode = snap.mode
        if (state == TimerState.RUNNING) {
            val now = System.currentTimeMillis()
            if (endEpochMs <= now) {
                cmdComplete()
                return
            }
            scheduler.scheduleFire(endEpochMs)
            startForegroundOrPromote()
            startTicker()
        } else if (state == TimerState.PAUSED) {
            startForegroundOrPromote()
            updateOngoingNotification()
        } else {
            stopSelf()
        }
    }

    /**
     * Completion sequence:
     *  1. Grab a short wake lock so we don't die mid-sequence.
     *  2. If mode is GENTLE and we're at deadline, pause with volume faded.
     *     (We don't start the fade early here; the foreground ticker
     *      already performed the fade over the final minutes.)
     *  3. Pause media (unless BT_ONLY).
     *  4. Wait for configured delay.
     *  5. Try Bluetooth off (honest result).
     *  6. Post completion notification, write history, reset state.
     */
    private fun cmdComplete() {
        val seqMode = mode
        val seqDuration = durationMs
        val seqStarted = startedEpochMs
        val seqFade = preFadeVolume
        scope.launch {
            if (wl.isHeld.not()) runCatching { wl.acquire(60_000L) }

            // Step 1: ensure volume is 0 if we were fading (the ticker will
            // already have reduced it to 0 during the fade window; this is a
            // safety in case completion was triggered manually).
            if (seqMode.fadesVolume) {
                val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                runCatching {
                    am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, 0, 0)
                }
            }

            // Step 2: pause media.
            var musicPaused = false
            val musicKept = (seqMode == SleepMode.BT_ONLY)
            if (!musicKept) {
                musicPaused = media.pauseActiveMedia()
                // Optionally restore volume (after a short delay so the
                // pause has taken effect).
                if (seqFade > 0) {
                    val restore = prefs.restoreVolume.first()
                    if (restore) {
                        delay(1_500)
                        media.restoreVolume(seqFade)
                    }
                }
            }

            // Step 3: configured delay before BT action.
            val delaySec = prefs.btDelaySeconds.first().coerceIn(0, 120)
            delay(delaySec * 1000L)

            // Step 4: Bluetooth.
            var btTurnedOff = false
            var btAttempted = false
            if (seqMode.touchesBluetooth && bt.currentStatus().enabled) {
                btAttempted = true
                btTurnedOff = bt.attemptDisable()
                if (!btTurnedOff) {
                    // API 33+ path: show Settings (and optionally trigger
                    // the accessibility auto-toggle if the user enabled it).
                    if (AccessibilityHelperStatus.isEnabled(this@TimerService)) {
                        // Broadcast the tap request, then open Settings.
                        sendBroadcast(Intent(BluetoothToggleAccessibilityService.ACTION_TAP_BT_TOGGLE))
                        val i = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                        // Wait briefly and re-check.
                        delay(3_000)
                        btTurnedOff = !bt.currentStatus().enabled
                    } else {
                        // Open Settings for the user; completion notification
                        // will also have a shortcut.
                        val i = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(i)
                    }
                }
            }

            // Step 5: completion notification.
            val n = notif.buildCompletion(
                musicPaused = musicPaused,
                btTurnedOff = btTurnedOff,
                btAttempted = btAttempted,
                musicKept = musicKept
            )
            notif.notifyCompletion(n)

            // Step 6: history + reset.
            val entry = HistoryEntry(
                startedEpochMs = if (seqStarted != 0L) seqStarted else System.currentTimeMillis() - seqDuration,
                endedEpochMs = System.currentTimeMillis(),
                durationMs = seqDuration,
                mode = seqMode,
                musicPaused = musicPaused,
                bluetoothTurnedOff = btTurnedOff,
                bluetoothAttempted = btAttempted,
                note = if (btAttempted && !btTurnedOff) "BT off via Settings required" else null
            )
            appendHistory(entry)

            // Clear running state but remember last-used config.
            state = TimerState.IDLE
            endEpochMs = 0L
            pausedRemainingMs = 0L
            durationMs = 0L
            preFadeVolume = -1
            scheduler.cancelFire()
            prefs.clearTimerSnapshot()

            stopTicker()
            stopForeground(STOP_FOREGROUND_REMOVE)
            notif.cancelOngoing()
            stopSelf()
            if (wl.isHeld) runCatching { wl.release() }
        }
    }

    // ---- ticker (updates the ongoing notification + fades volume) ----

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var lastRemainingMinBucket: Int = -1
            while (state == TimerState.RUNNING) {
                val now = System.currentTimeMillis()
                val remaining = (endEpochMs - now).coerceAtLeast(0L)

                // Gentle fade over final 'fadeMinutes' minutes.
                if (mode.fadesVolume) {
                    val fadeMin = prefs.fadeMinutes.first().coerceIn(1, 30)
                    val fadeMs = fadeMin.toLong() * 60_000L
                    if (remaining in 1..fadeMs) {
                        val bucket = (remaining / 1000).toInt()
                        if (bucket != lastRemainingMinBucket) {
                            lastRemainingMinBucket = bucket
                            // We only want to set the starting volume once.
                            if (preFadeVolume < 0) {
                                val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                preFadeVolume = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            }
                            val am = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                            val fraction = (remaining.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                            val target = (preFadeVolume.coerceAtLeast(max) * fraction).toInt()
                                .coerceIn(0, max)
                            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
                        }
                    }
                }

                // Update notification once per second.
                updateOngoingNotification()

                if (remaining <= 0L) {
                    cmdComplete()
                    break
                }

                // Battery saver: tick slower.
                val saver = prefs.batterySaver.first()
                delay(if (saver) 5_000L else TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun startForegroundOrPromote() {
        try {
            startForeground(
                NotificationHelper.NOTIF_ID_ONGOING,
                notif.buildOngoing(state, currentRemainingMs(), endEpochMs)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "startForeground failed", t)
        }
    }

    private fun updateOngoingNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        // When PAUSED we don't have a real endEpochMs; pass the stored value
        // so the notification says "Timer paused".
        val displayEnd = when (state) {
            TimerState.RUNNING -> endEpochMs
            TimerState.PAUSED -> System.currentTimeMillis() + pausedRemainingMs
            TimerState.IDLE -> System.currentTimeMillis()
        }
        try {
            nm.notify(
                NotificationHelper.NOTIF_ID_ONGOING,
                notif.buildOngoing(state, currentRemainingMs(), displayEnd)
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Notification update failed", t)
        }
    }

    private fun currentRemainingMs(): Long = when (state) {
        TimerState.RUNNING -> (endEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        TimerState.PAUSED -> pausedRemainingMs
        TimerState.IDLE -> 0L
    }

    private fun persist() {
        scope.launch {
            prefs.saveTimerSnapshot(state, endEpochMs, pausedRemainingMs, durationMs, mode)
            if (state == TimerState.RUNNING || state == TimerState.PAUSED) {
                prefs.setLastDurationMinutes((durationMs / 60_000L).toInt().coerceAtLeast(1))
                prefs.setLastMode(mode)
            }
        }
    }

    private suspend fun appendHistory(entry: HistoryEntry) {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val json = JSONObject().apply {
            put("startedEpochMs", entry.startedEpochMs)
            put("endedEpochMs", entry.endedEpochMs)
            put("durationMs", entry.durationMs)
            put("mode", entry.mode.name)
            put("musicPaused", entry.musicPaused)
            put("bluetoothTurnedOff", entry.bluetoothTurnedOff)
            put("bluetoothAttempted", entry.bluetoothAttempted)
            put("note", entry.note ?: JSONObject.NULL)
            put("startedIso", df.format(Date(entry.startedEpochMs)))
            put("endedIso", df.format(Date(entry.endedEpochMs)))
        }.toString()
        prefs.appendHistory(json)
    }
}
