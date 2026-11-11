package com.sleepoff.app.domain

/**
 * Bluetooth subsystem state, observed from a Flow.
 *
 * @param enabled       whether the adapter is currently ON
 * @param supported     false on devices without any Bluetooth adapter
 * @param deviceNames   list of connected A2DP/LE audio device names
 */
data class BluetoothStatus(
    val enabled: Boolean = false,
    val supported: Boolean = true,
    val deviceNames: List<String> = emptyList()
) {
    val connectedCount: Int get() = deviceNames.size
    val primaryDevice: String? get() = deviceNames.firstOrNull()
}
