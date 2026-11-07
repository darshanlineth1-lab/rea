package com.regainfocus.app

import android.app.Application
import com.regainfocus.app.blocker.DeviceOwnerEnforcementManager
import com.regainfocus.app.data.local.RegainFocusDatabase
import com.regainfocus.app.data.preferences.ActiveStrictSessionStore
import com.regainfocus.app.data.preferences.AppPreferencesStore
import com.regainfocus.app.data.repository.FocusProductivityRepository
import com.regainfocus.app.data.repository.InstalledAppsAndUsageRepository
import com.regainfocus.app.notification.NotificationHelper
import com.regainfocus.app.service.FocusTimerService
import com.regainfocus.app.worker.ReminderWorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database: RegainFocusDatabase = RegainFocusDatabase.getInstance(application)
    val preferencesStore = AppPreferencesStore(application)
    val activeSessionStore = ActiveStrictSessionStore(application)
    val usageRepository = InstalledAppsAndUsageRepository(application)
    val productivityRepository = FocusProductivityRepository(
        database = database,
        preferencesStore = preferencesStore,
        activeSessionStore = activeSessionStore
    )
    val deviceOwnerManager = DeviceOwnerEnforcementManager(
        context = application,
        usageRepository = usageRepository
    )
    val notificationHelper = NotificationHelper(application)
}

class RegainFocusApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        appScope.launch {
            container.productivityRepository.ensureStarterBlockingGroupsSeeded()
        }

        val activeSnapshot = container.activeSessionStore.readSnapshotSync()
        if (activeSnapshot.isActive) {
            FocusTimerService.triggerExpiryCheck(this)
        }

        runCatching { ReminderWorkScheduler.syncScheduledWorkers(this) }
    }
}
