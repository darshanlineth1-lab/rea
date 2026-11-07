package com.regainfocus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.service.FocusTimerService
import com.regainfocus.app.worker.ReminderWorkScheduler

class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val app = context.applicationContext as? RegainFocusApplication
        val store = app?.container?.activeSessionStore ?: ActiveStrictSessionStore(context)
        val snapshot = store.readSnapshotSync()

        if (snapshot.isActive) {
            FocusTimerService.triggerExpiryCheck(context)
        }

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { ReminderWorkScheduler.syncScheduledWorkers(context) }
        }
    }
}
