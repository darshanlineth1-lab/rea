package com.regainfocus.app.data.preferences

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.regainfocus.app.domain.model.PersistedTimerSessionState
import com.regainfocus.app.domain.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object UserSettingsSerializer : Serializer<UserSettings> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: UserSettings = UserSettings()

    override suspend fun readFrom(input: InputStream): UserSettings {
        return try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) defaultValue else json.decodeFromString(UserSettings.serializer(), text)
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: UserSettings, output: OutputStream) {
        output.write(json.encodeToString(UserSettings.serializer(), t).encodeToByteArray())
    }
}

object TimerSessionStateSerializer : Serializer<PersistedTimerSessionState> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val defaultValue: PersistedTimerSessionState = PersistedTimerSessionState()

    override suspend fun readFrom(input: InputStream): PersistedTimerSessionState {
        return try {
            val text = input.readBytes().decodeToString()
            if (text.isBlank()) defaultValue else json.decodeFromString(PersistedTimerSessionState.serializer(), text)
        } catch (_: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: PersistedTimerSessionState, output: OutputStream) {
        output.write(json.encodeToString(PersistedTimerSessionState.serializer(), t).encodeToByteArray())
    }
}

class AppPreferencesStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsDataStore: DataStore<UserSettings> = DataStoreFactory.create(
        serializer = UserSettingsSerializer,
        scope = scope,
        produceFile = { appContext.dataStoreFile("regain_user_settings.json") }
    )

    val settingsFlow: Flow<UserSettings> = settingsDataStore.data

    suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        settingsDataStore.updateData(transform)
    }
}

/**
 * Dual-persisted active session state store:
 * 1) Device-Protected Storage (`createDeviceProtectedStorageContext()`) so `LOCKED_BOOT_COMPLETED`
 *    and `AccessibilityService` can synchronously read active strict sessions immediately after reboot
 *    even before user credential unlock.
 * 2) Typed Jetpack DataStore mirror for reactive Flow consumers across the app.
 */
class ActiveStrictSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val deviceProtectedContext: Context =
        appContext.createDeviceProtectedStorageContext() ?: appContext

    private val directBootPrefs = deviceProtectedContext.getSharedPreferences(
        "regain_strict_session_direct_boot",
        Context.MODE_PRIVATE
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val sessionDataStore: DataStore<PersistedTimerSessionState> = DataStoreFactory.create(
        serializer = TimerSessionStateSerializer,
        scope = scope,
        produceFile = { deviceProtectedContext.dataStoreFile("regain_active_session.json") }
    )

    private val _liveState = MutableStateFlow(readDirectBootSnapshot())
    val sessionStateFlow: StateFlow<PersistedTimerSessionState> = _liveState.asStateFlow()

    init {
        scope.launch {
            sessionDataStore.data.collect { dsState ->
                // Prefer directBootPrefs if dsState is empty on first boot before migration
                if (dsState.isActive || !_liveState.value.isActive) {
                    _liveState.value = dsState
                }
            }
        }
    }

    fun readSnapshotSync(): PersistedTimerSessionState {
        return readDirectBootSnapshot()
    }

    private fun readDirectBootSnapshot(): PersistedTimerSessionState {
        val raw = directBootPrefs.getString(KEY_SESSION_JSON, null) ?: return PersistedTimerSessionState()
        return runCatching {
            json.decodeFromString(PersistedTimerSessionState.serializer(), raw)
        }.getOrDefault(PersistedTimerSessionState())
    }

    suspend fun saveState(newState: PersistedTimerSessionState) {
        val encoded = json.encodeToString(PersistedTimerSessionState.serializer(), newState)
        directBootPrefs.edit().putString(KEY_SESSION_JSON, encoded).commit()
        _liveState.value = newState
        runCatching {
            sessionDataStore.updateData { newState }
        }
    }

    fun saveStateBlocking(newState: PersistedTimerSessionState) {
        val encoded = json.encodeToString(PersistedTimerSessionState.serializer(), newState)
        directBootPrefs.edit().putString(KEY_SESSION_JSON, encoded).commit()
        _liveState.value = newState
        scope.launch {
            runCatching { sessionDataStore.updateData { newState } }
        }
    }

    suspend fun clearSession() {
        saveState(PersistedTimerSessionState())
    }

    companion object {
        private const val KEY_SESSION_JSON = "active_session_json"

        fun currentBootCountId(context: Context): String {
            return runCatching {
                val bootCount = Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.BOOT_COUNT,
                    -1
                )
                "boot_${bootCount}_${Build.TIME}"
            }.getOrDefault("boot_fallback")
        }
    }
}
