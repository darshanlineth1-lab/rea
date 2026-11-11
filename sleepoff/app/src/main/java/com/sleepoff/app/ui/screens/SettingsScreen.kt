package com.sleepoff.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sleepoff.app.ui.components.SleepCard
import com.sleepoff.app.viewmodel.HomeViewModel

@Composable
fun SettingsScreen(vm: HomeViewModel, onOpenSetup: () -> Unit, onBack: () -> Unit = {}) {
    BackHandler(onBack = onBack)

    val perms by vm.permState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))

        SettingRow(
            title = "Permissions & Setup",
            subtitle = "Bluetooth, notifications, alarms, battery optimization",
            onClick = onOpenSetup
        )
        SettingRow(
            title = "Media session control",
            subtitle = if (perms.notificationListener) "Granted" else "Not granted (optional)",
            onClick = { vm.openNotificationListenerSettings() }
        )
        SettingRow(
            title = "Bluetooth auto-toggle helper",
            subtitle = if (perms.accessibilityHelper) "Enabled" else "Not enabled (optional, Android 13+)",
            onClick = { vm.openAccessibilitySettings() }
        )
        SettingRow(
            title = "Battery optimization",
            subtitle = if (perms.batteryOptExempt) "Exempted" else "Not exempted (recommended)",
            onClick = { vm.requestBatteryExemption(); vm.refreshPermissionState() }
        )
        SettingRow(
            title = "Exact alarms",
            subtitle = if (perms.exactAlarmGranted) "Allowed" else "Not allowed (required for reliable timing)",
            onClick = { vm.openExactAlarmSettings(); vm.refreshPermissionState() }
        )

        SleepCard {
            Text("About SleepOff", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "SleepOff 1.0.0\n\n" +
                "No ads. No tracking. No account. No internet access.\n\n" +
                "On Android 13+, apps cannot directly turn Bluetooth off. " +
                "SleepOff pauses your music, then either sends you to Bluetooth settings " +
                "or (if you enable the optional helper) taps the toggle for you. " +
                "SleepOff always tells you what actually happened.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    SleepCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Open settings") }
    }
}
