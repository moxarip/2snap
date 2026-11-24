package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.wakeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "double_tap_wake_prefs"
)

enum class TapSensitivity(
    val impulseThresholdMs2: Float,
    val settleThresholdMs2: Float,
) {
    LOW(impulseThresholdMs2 = 5.4f, settleThresholdMs2 = 2.8f),
    MEDIUM(impulseThresholdMs2 = 3.6f, settleThresholdMs2 = 2.0f),
    HIGH(impulseThresholdMs2 = 2.3f, settleThresholdMs2 = 1.4f);

    companion object {
        fun fromStored(value: String?): TapSensitivity =
            entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

data class WakeSettings(
    val isServiceEnabled: Boolean = false,
    val enableOnBoot: Boolean = true,
    val sensitivity: TapSensitivity = TapSensitivity.MEDIUM,
    val pocketProtectionEnabled: Boolean = true,
)

class WakePreferences(private val context: Context) {

    private object Keys {
        val SERVICE_ENABLED = booleanPreferencesKey("service_enabled")
        val ENABLE_ON_BOOT = booleanPreferencesKey("enable_on_boot")
        val SENSITIVITY = stringPreferencesKey("tap_sensitivity")
        val POCKET_PROTECTION = booleanPreferencesKey("pocket_protection_enabled")
    }

    val settingsFlow: Flow<WakeSettings> = context.wakeDataStore.data.map { prefs ->
        WakeSettings(
            isServiceEnabled = prefs[Keys.SERVICE_ENABLED] ?: false,
            enableOnBoot = prefs[Keys.ENABLE_ON_BOOT] ?: true,
            sensitivity = TapSensitivity.fromStored(prefs[Keys.SENSITIVITY]),
            pocketProtectionEnabled = prefs[Keys.POCKET_PROTECTION] ?: true,
        )
    }

    suspend fun getSnapshot(): WakeSettings = settingsFlow.first()

    suspend fun setServiceEnabled(enabled: Boolean) {
        context.wakeDataStore.edit { prefs ->
            prefs[Keys.SERVICE_ENABLED] = enabled
        }
    }

    suspend fun setEnableOnBoot(enabled: Boolean) {
        context.wakeDataStore.edit { prefs ->
            prefs[Keys.ENABLE_ON_BOOT] = enabled
        }
    }

    suspend fun setSensitivity(sensitivity: TapSensitivity) {
        context.wakeDataStore.edit { prefs ->
            prefs[Keys.SENSITIVITY] = sensitivity.name
        }
    }

    suspend fun setPocketProtectionEnabled(enabled: Boolean) {
        context.wakeDataStore.edit { prefs ->
            prefs[Keys.POCKET_PROTECTION] = enabled
        }
    }
}
