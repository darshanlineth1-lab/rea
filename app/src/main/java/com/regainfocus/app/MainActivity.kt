package com.regainfocus.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.regainfocus.app.domain.model.StrictEnforcementLevel
import com.regainfocus.app.domain.model.TimerModeType
import com.regainfocus.app.ui.screens.AppsScreen
import com.regainfocus.app.ui.screens.FocusScreen
import com.regainfocus.app.ui.screens.HomeScreen
import com.regainfocus.app.ui.screens.OnboardingScreen
import com.regainfocus.app.ui.screens.SettingsScreen
import com.regainfocus.app.ui.screens.StatisticsScreen
import com.regainfocus.app.ui.theme.AuroraEmerald
import com.regainfocus.app.ui.theme.RegainFocusTheme
import com.regainfocus.app.ui.viewmodel.RegainMainViewModel

private enum class MainTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Default.Home),
    FOCUS("focus", "Focus", Icons.Default.Timer),
    APPS("apps", "Apps", Icons.Default.Apps),
    STATS("stats", "Statistics", Icons.Default.BarChart),
    SETTINGS("settings", "Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: RegainMainViewModel by viewModels {
        RegainMainViewModel.factory(application)
    }

    private var requestedTabRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTabRoute = intent?.getStringExtra(EXTRA_OPEN_TAB)

        setContent {
            val settings by viewModel.settings.collectAsState()
            val activeSession by viewModel.activeSession.collectAsState()

            // Automatically exit Lock Task kiosk pinning if a Mode B session just completed
            LaunchedEffect(activeSession.isActive) {
                if (!activeSession.isActive) {
                    (application as RegainFocusApplication).container.deviceOwnerManager
                        .stopLockTaskIfActive(this@MainActivity)
                }
            }

            RegainFocusTheme(themeMode = settings.themeMode) {
                RegainAppRoot(
                    viewModel = viewModel,
                    externalTabRoute = requestedTabRoute,
                    onConsumedExternalTab = { requestedTabRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedTabRoute = intent.getStringExtra(EXTRA_OPEN_TAB)
    }

    companion object {
        const val EXTRA_OPEN_TAB = "extra_open_tab"
    }
}

@Composable
private fun RegainAppRoot(
    viewModel: RegainMainViewModel,
    externalTabRoute: String?,
    onConsumedExternalTab: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val liveDisplayMillis by viewModel.liveDisplayMillis.collectAsState()
    val liveStrictRemainingMillis by viewModel.liveStrictRemainingMillis.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val blockingGroups by viewModel.blockingGroups.collectAsState()
    val appLimits by viewModel.appLimits.collectAsState()
    val schedules by viewModel.schedules.collectAsState()
    val habits by viewModel.habits.collectAsState()
    val habitCompletions by viewModel.habitCompletions.collectAsState()
    val totalInterceptions by viewModel.totalInterceptions.collectAsState()
    val streakSummary by viewModel.streakSummary.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val installedApps by viewModel.installedApps.collectAsState()
    val screenTimeOverview by viewModel.screenTimeOverview.collectAsState()
    val isLoadingUsage by viewModel.isLoadingUsage.collectAsState()
    val enforcementStatus by viewModel.enforcementStatus.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionsAndData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    if (!settings.hasCompletedOnboarding) {
        OnboardingScreen(
            enforcementStatus = enforcementStatus,
            onRefreshPermissions = { viewModel.refreshPermissionsAndData() },
            onComplete = { focusMins, screenMins ->
                viewModel.completeOnboarding(focusMins, screenMins)
            }
        )
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    LaunchedEffect(externalTabRoute) {
        if (externalTabRoute != null) {
            MainTab.entries.firstOrNull { it.route == externalTabRoute }?.let {
                currentTab = it
            }
            onConsumedExternalTab()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AuroraEmerald,
                            selectedTextColor = AuroraEmerald,
                            indicatorColor = AuroraEmerald.copy(alpha = 0.16f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainTab.HOME -> HomeScreen(
                    settings = settings,
                    activeSession = activeSession,
                    liveDisplayMillis = liveDisplayMillis,
                    sessions = sessions,
                    blockingGroups = blockingGroups,
                    screenTimeOverview = screenTimeOverview,
                    streakSummary = streakSummary,
                    habits = habits,
                    habitCompletions = habitCompletions,
                    onNavigateToFocus = { currentTab = MainTab.FOCUS },
                    onNavigateToApps = { currentTab = MainTab.APPS },
                    onQuickStartFocus = { mins, group ->
                        val started = viewModel.startFocusSession(
                            mode = TimerModeType.COUNTDOWN,
                            workMinutes = mins,
                            shortBreakMinutes = 0,
                            longBreakMinutes = 0,
                            cycles = 1,
                            strictLevel = StrictEnforcementLevel.NONE,
                            selectedGroup = group,
                            extraSelectedPackages = emptySet(),
                            blockShortsAndReels = true,
                            sessionTag = "Quick Focus",
                            sessionNote = ""
                        )
                        if (started) currentTab = MainTab.FOCUS
                    },
                    onAddHabit = { title, cat, target ->
                        viewModel.saveHabit(title, cat, target, 0xFF2EE59D)
                    },
                    onToggleHabitToday = { viewModel.toggleHabitToday(it) },
                    onArchiveHabit = { viewModel.archiveHabit(it) }
                )

                MainTab.FOCUS -> FocusScreen(
                    settings = settings,
                    activeSession = activeSession,
                    liveDisplayMillis = liveDisplayMillis,
                    liveStrictRemainingMillis = liveStrictRemainingMillis,
                    blockingGroups = blockingGroups,
                    installedApps = installedApps,
                    enforcementStatus = enforcementStatus,
                    recentSessions = sessions,
                    onStartSession = { mode, work, shortB, longB, cyc, strict, grp, extra, shorts, tag, note ->
                        viewModel.startFocusSession(
                            mode = mode,
                            workMinutes = work,
                            shortBreakMinutes = shortB,
                            longBreakMinutes = longB,
                            cycles = cyc,
                            strictLevel = strict,
                            selectedGroup = grp,
                            extraSelectedPackages = extra,
                            blockShortsAndReels = shorts,
                            sessionTag = tag,
                            sessionNote = note
                        )
                    },
                    onTogglePauseFlexible = { viewModel.togglePauseFlexible() },
                    onSkipBreakFlexible = { viewModel.skipBreakFlexible() },
                    onStopFlexible = { viewModel.stopSessionFlexible() },
                    onSelectSoundscape = { viewModel.selectSoundscape(it) },
                    onUpdateVolume = { viewModel.updateSoundVolume(it) }
                )

                MainTab.APPS -> AppsScreen(
                    activeSession = activeSession,
                    blockingGroups = blockingGroups,
                    installedApps = installedApps,
                    appLimits = appLimits,
                    schedules = schedules,
                    screenTimeOverview = screenTimeOverview,
                    isLoadingUsage = isLoadingUsage,
                    onRefreshUsage = { viewModel.refreshPermissionsAndData(forceAppsReload = true) },
                    onSaveGroup = { viewModel.saveBlockingGroup(it) },
                    onDeleteGroup = { viewModel.deleteBlockingGroup(it) },
                    onUpsertLimit = { pkg, lbl, mins, en, lockH ->
                        viewModel.upsertDailyAppLimit(pkg, lbl, mins, en, lockH)
                    },
                    onDeleteLimit = { viewModel.deleteDailyAppLimit(it) },
                    onSaveSchedule = { viewModel.saveSchedule(it) },
                    onDeleteSchedule = { viewModel.deleteSchedule(it) }
                )

                MainTab.STATS -> StatisticsScreen(
                    settings = settings,
                    sessions = sessions,
                    screenTimeOverview = screenTimeOverview,
                    streakSummary = streakSummary,
                    achievements = achievements,
                    totalInterceptions = totalInterceptions
                )

                MainTab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    activeSession = activeSession,
                    enforcementStatus = enforcementStatus,
                    onUpdateTheme = { viewModel.updateThemeMode(it) },
                    onUpdateSettings = { reqUnlock, transform ->
                        viewModel.updateUserSettings(reqUnlock, transform)
                    },
                    onExportJson = { viewModel.exportBackupJson() },
                    onImportJson = { viewModel.importBackupJson(it) },
                    onClearHistory = { viewModel.clearAllHistory() },
                    onPostMessage = { viewModel.postMessage(it) }
                )
            }
        }
    }
}
