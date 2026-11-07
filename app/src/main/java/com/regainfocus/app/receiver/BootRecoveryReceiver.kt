package com.regainfocus.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.regainfocus.app.RegainFocusApplication
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.domain.engine.StrictRemainingTimeCalculator
import com.regainfocus.app.service.FocusTimerService
import com.regainfocus.app.worker.ReminderWorkScheduler

/**
 * Restores persisted Strict Focus Session state immediately upon device reboot (`LOCKED_BOOT_COMPLETED`
 * and `BOOT_COMPLETED`) or app update (`MY_PACKAGE_REPLACED`), and cleanly releases Device Owner
 * suspensions if the session finished while the phone was powered off.
 */
class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val app = context.applicationContext as? RegainFocusApplication
        val store = app?.container?.activeSessionStore ?: ActiveStrictSessionStore(context)
        val snapshot = store.readSnapshotSync()

        if (snapshot.isActive) {
            val nowWall = System.currentTimeMillis()
            val bootId = ActiveStrictSessionStore.currentBootCountId(context)
            val expired = snapshot.isStrictActive && StrictRemainingTimeCalculator.isStrictSessionExpired(
                state = snapshot,
                currentWallMillis = nowWall,
                currentElapsedRealtimeMillis = 0L,
                currentBootId = bootId
            )
            if (expired) {
                app?.container?.deviceOwnerManager?.releaseAllDeviceOwnerRestrictions(snapshot.blockedPackages)
            }
            FocusTimerService.triggerExpiryCheck(context)
        } else {
            app?.container?.deviceOwnerManager?.releaseAllDeviceOwnerRestrictions()
        }

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { ReminderWorkScheduler.syncScheduledWorkers(context) }
        }
    }
}
