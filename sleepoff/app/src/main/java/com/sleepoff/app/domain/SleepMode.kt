package com.sleepoff.app.domain

/**
 * The four shutdown modes described in the spec.
 */
enum class SleepMode(val label: String, val description: String) {
    MUSIC_ONLY("Music Only", "Pause music, keep Bluetooth on"),
    MUSIC_BT("Music + Bluetooth", "Pause music, then turn Bluetooth off"),
    BT_ONLY("Bluetooth Only", "Turn Bluetooth off, keep music playing"),
    GENTLE("Gentle Shutdown", "Fade volume, pause music, then turn Bluetooth off");

    val pausesMusic: Boolean get() = this != BT_ONLY
    val touchesBluetooth: Boolean get() = this == MUSIC_BT || this == GENTLE || this == BT_ONLY
    val fadesVolume: Boolean get() = this == GENTLE
}
