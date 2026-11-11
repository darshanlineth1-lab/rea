package com.sleepoff.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sleepoff.app.navigation.Routes
import com.sleepoff.app.theme.SleepOffTheme
import com.sleepoff.app.ui.screens.ConfigureScreen
import com.sleepoff.app.ui.screens.HistoryScreen
import com.sleepoff.app.ui.screens.HomeScreen
import com.sleepoff.app.ui.screens.ScheduleScreen
import com.sleepoff.app.ui.screens.SettingsScreen
import com.sleepoff.app.ui.screens.SetupScreen
import com.sleepoff.app.viewmodel.HomeViewModel

/**
 * Single-activity host. Edge-to-edge Compose UI, NavHost-based navigation
 * between the six screens specified in the spec.
 */
class MainActivity : ComponentActivity() {

    private var vm: HomeViewModel? = null

    private val requestPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm?.refreshPermissionState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            SleepOffTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val vmodel: HomeViewModel = viewModel()
                    vm = vmodel
                    SleepOffNavHost(vmodel, this)
                }
            }
        }

        // Request runtime permissions that the app needs for core functionality.
        requestRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        vm?.refreshPermissionState()
    }

    private fun requestRequiredPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
        }
        if (needed.isNotEmpty()) {
            requestPerms.launch(needed.toTypedArray())
        }
    }
}

@Composable
private fun SleepOffNavHost(vm: HomeViewModel, activity: ComponentActivity) {
    val nav = rememberNavController()
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { } // no bottom nav on v1 — top-bar icons provide navigation
    ) { inner ->
        Box(modifier = Modifier.padding(inner)) {
            NavHost(navController = nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        vm = vm,
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenConfigure = { nav.navigate(Routes.CONFIGURE) },
                        onOpenSetup = { nav.navigate(Routes.SETUP) },
                        onOpenSchedule = { nav.navigate(Routes.SCHEDULE) },
                        onOpenHistory = { nav.navigate(Routes.HISTORY) }
                    )
                }
                composable(Routes.CONFIGURE) {
                    ConfigureScreen(vm = vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.SCHEDULE) {
                    ScheduleScreen(vm = vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(vm = vm, onBack = { nav.popBackStack() })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        vm = vm,
                        onOpenSetup = { nav.navigate(Routes.SETUP) },
                        onBack = { nav.popBackStack() }
                    )
                }
                composable(Routes.SETUP) {
                    SetupScreen(vm = vm, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
