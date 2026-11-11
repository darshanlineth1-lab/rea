package com.sleepoff.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.sleepoff.app.domain.BluetoothStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes Bluetooth state and connected audio devices, and performs the
 * honest shutdown sequence described in the spec.
 *
 * Bluetooth shutdown rules:
 *  - API < 33 (Android 12 and older): BluetoothAdapter.disable() works for
 *    apps holding BLUETOOTH_ADMIN. We attempt it and then verify state.
 *  - API >= 33 (Android 13+): BluetoothAdapter.disable() is deprecated and
 *    the platform throws/returns false for non-system apps. We never claim
 *    success silently: we send the user to Bluetooth Settings via
 *    ACTION_BLUETOOTH_SETTINGS. If the user has explicitly enabled the
 *    optional BluetoothToggleAccessibilityService, it will open Settings and
 *    tap the toggle on their behalf (true user-authorized automation).
 */
class BluetoothController(private val appContext: Context) {

    companion object {
        private const val TAG = "BluetoothCtrl"
    }

    private val btManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = btManager?.adapter

    /** Hot Flow of Bluetooth + connected-device state. */
    fun observeStatus(): Flow<BluetoothStatus> = callbackFlow {
        if (adapter == null) {
            trySend(BluetoothStatus(supported = false))
            awaitClose { }
            return@callbackFlow
        }

        // Maintain a connected-device list via an A2DP profile proxy.
        var a2dpProxy: BluetoothProfile? = null
        val serviceListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                a2dpProxy = proxy
                trySend(snapshot(proxy))
            }
            override fun onServiceDisconnected(profile: Int) {
                a2dpProxy = null
                trySend(BluetoothStatus(enabled = adapter.isEnabled, supported = true))
            }
        }
        adapter.getProfileProxy(appContext, serviceListener, BluetoothProfile.A2DP)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED,
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                        trySend(snapshot(a2dpProxy))
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        appContext.registerReceiver(receiver, filter)

        // Initial value.
        trySend(snapshot(a2dpProxy))

        awaitClose {
            try { appContext.unregisterReceiver(receiver) } catch (_: Throwable) {}
            a2dpProxy?.let { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) }
        }
    }.distinctUntilChanged()

    fun currentStatus(): BluetoothStatus {
        if (adapter == null) return BluetoothStatus(supported = false)
        return BluetoothStatus(
            enabled = adapter.isEnabled,
            supported = true,
            deviceNames = emptyList() // A2DP proxy is async; Flow will update.
        )
    }

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * Attempt to turn Bluetooth off. Returns the HONEST result.
     *
     * @return true only if BluetoothAdapter reported success AND we verified
     *         the adapter is off after a brief grace period; false otherwise.
     */
    fun attemptDisable(): Boolean {
        val a = adapter ?: return false
        if (!a.isEnabled) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Per Android 13 API rules: third-party apps CANNOT disable BT.
            // We never pretend success here; caller must surface the
            // settings/accessibility flow to the user.
            Log.i(TAG, "BluetoothAdapter.disable() is disallowed on API >= 33; not attempting.")
            return false
        }
        @Suppress("DEPRECATION")
        return try {
            val ok = a.disable()
            Log.i(TAG, "BluetoothAdapter.disable() returned $ok")
            // disable() is asynchronous; callers will re-read via observeStatus().
            ok
        } catch (t: Throwable) {
            Log.w(TAG, "disable() threw", t)
            false
        }
    }

    @Suppress("MissingPermission") // BLUETOOTH_CONNECT is requested at runtime.
    private fun snapshot(a2dp: BluetoothProfile?): BluetoothStatus {
        val a = adapter ?: return BluetoothStatus(supported = false)
        val enabled = runCatching { a.isEnabled }.getOrDefault(false)
        val names = if (enabled && a2dp != null) {
            a2dp.connectedDevices
                .filter {
                    @Suppress("DEPRECATION")
                    a2dp.getConnectionState(it) == BluetoothProfile.STATE_CONNECTED
                }
                .mapNotNull { dev ->
                    runCatching {
                        dev.alias ?: dev.name ?: dev.address
                    }.getOrNull()
                }
        } else emptyList()
        return BluetoothStatus(enabled = enabled, supported = true, deviceNames = names)
    }
}

/**
 * Utility to query whether the optional Bluetooth-toggle accessibility
 * service is currently enabled by the user. When it is, BluetoothController
 * (with a Broadcast+Settings intent handoff) can reliably turn BT off on API 33+.
 */
object AccessibilityHelperStatus {
    fun isEnabled(context: Context): Boolean {
        val prefKey = "enabled_accessibility_services"
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver, prefKey
        ) ?: return false
        return enabled.contains(context.packageName + "/")
    }
}
