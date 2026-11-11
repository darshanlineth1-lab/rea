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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sleepoff.app.ui.components.SleepCard
import com.sleepoff.app.viewmodel.HomeViewModel

@Composable
fun SetupScreen(vm: HomeViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val perms by vm.permState.collectAsState()

    // Refresh status when returning from a settings screen.
    LaunchedEffect(Unit) { vm.refreshPermissionState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Permissions & Setup", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))

        SleepCard {
            Text(
                "SleepOff needs a few permissions to work reliably overnight. Each one has a clear purpose — nothing is requested without a reason.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PermRow(
            title = "Bluetooth connection",
            desc = "Shows your connected headphones or speaker name. Android 12+ requires this to read connected devices.",
            granted = perms.bluetoothConnectGranted,
            onAllow = { /* runtime permission requested in Activity */ }
        )
        PermRow(
            title = "Notifications",
            desc = "Shows the countdown and completion message. Required on Android 13+.",
            granted = perms.notificationsGranted,
            onAllow = { vm.openNotificationsSettings(); vm.refreshPermissionState() }
        )
        PermRow(
            title = "Exact alarms",
            desc = "Fires the timer at the exact time, even while the phone is in Doze.",
            granted = perms.exactAlarmGranted,
            onAllow = { vm.openExactAlarmSettings(); vm.refreshPermissionState() }
        )
        PermRow(
            title = "Battery optimization exemption",
            desc = "Prevents the system from killing SleepOff overnight. Strongly recommended.",
            granted = perms.batteryOptExempt,
            onAllow = { vm.requestBatteryExemption(); vm.refreshPermissionState() }
        )

        Spacer(Modifier.height(6.dp))

        SleepCard {
            Text("Optional", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            PermRow(
                title = "Media session access",
                desc = "Grants access to the active media session for more reliable pause. The standard media-key pause works without it.",
                granted = perms.notificationListener,
                onAllow = { vm.openNotificationListenerSettings(); vm.refreshPermissionState() }
            )
            Spacer(Modifier.height(8.dp))
            PermRow(
                title = "Bluetooth auto-toggle helper",
                desc = if (perms.directBtDisableSupported)
                    "Direct Bluetooth disable is supported on your Android version; this helper is not needed."
                       else "On Android 13+, only this kind of user-authorized automation can turn Bluetooth off for you.",
                granted = perms.accessibilityHelper || perms.directBtDisableSupported,
                onAllow = {
                    if (!perms.directBtDisableSupported) {
                        vm.openAccessibilitySettings(); vm.refreshPermissionState()
                    }
                }
            )
        }

        SleepCard {
            Text("About Bluetooth on Android 13+", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Since Android 13 (API 33), Google removed the ability for third-party apps to turn Bluetooth off directly (BluetoothAdapter.disable() is deprecated and requires a system-only permission). SleepOff handles this honestly:\n\n" +
                "• On Android 12 and older, Bluetooth is switched off directly when permitted.\n" +
                "• On Android 13+, SleepOff pauses your music, then shows a notification with a one-tap shortcut to Bluetooth settings — or, if you enable the optional helper above, taps the toggle for you.\n" +
                "• SleepOff always reports what actually happened. It never claims Bluetooth is off when it is not.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) { Text("Done") }
    }
}

@Composable
private fun PermRow(title: String, desc: String, granted: Boolean, onAllow: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.padding(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            if (!granted) {
                OutlinedButton(onClick = onAllow) { Text("Allow") }
            } else {
                Text("Granted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
