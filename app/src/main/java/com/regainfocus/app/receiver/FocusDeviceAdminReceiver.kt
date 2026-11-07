package com.regainfocus.app.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.ui.blocker.StrictBlockShieldActivity

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val active = ActiveStrictSessionStore(context).readSnapshotSync()
        if (active.isStrictActive) {
            StrictBlockShieldActivity.launch(
                context = context,
                blockedPackage = "com.android.settings",
                reasonLabel = "Strict Mode is active — Device Admin protection cannot be disabled until the session ends."
            )
            return "Active Strict Focus Session in progress. Please wait until your focus timer finishes."
        }
        return "Disabling RegainFocus Admin removes Strict Mode tamper protection."
    }
}
