package com.sleepoff.app

import android.app.Application
import android.app.NotificationManager
import com.sleepoff.app.bluetooth.BluetoothController
import com.sleepoff.app.data.DataStoreSleepPreferences
import com.sleepoff.app.data.SleepPreferences
import com.sleepoff.app.media.MediaControllerHelper
import com.sleepoff.app.timer.NotificationHelper
import com.sleepoff.app.timer.TimerAlarmScheduler

/**
 * Application-scoped singletons. Nothing heavy — all lazy.
 */
class SleepOffApplication : Application() {

    lateinit var prefs: SleepPreferences
        private set
    lateinit var alarmScheduler: TimerAlarmScheduler
        private set
    lateinit var bluetooth: BluetoothController
        private set
    lateinit var media: MediaControllerHelper
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = DataStoreSleepPreferences(this)
        alarmScheduler = TimerAlarmScheduler(this)
        bluetooth = BluetoothController(this)
        media = MediaControllerHelper(this)
        // Ensure channels exist before anything posts a notification.
        NotificationHelper(this).ensureChannels()
    }
}
