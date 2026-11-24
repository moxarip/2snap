package com.example.ui

import android.app.Application
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.DeviceCapabilityInspector
import com.example.core.DeviceCapabilityReport
import com.example.data.TapSensitivity
import com.example.data.WakePreferences
import com.example.data.WakeSettings
import com.example.service.DoubleTapWakeService
import com.example.service.ServiceRuntimeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MainUiState(
    val settings: WakeSettings = WakeSettings(),
    val serviceState: ServiceRuntimeState = ServiceRuntimeState(),
    val deviceReport: DeviceCapabilityReport? = null,
    val startErrorMessage: String? = null,
) {
    val isEffectivelyEnabled: Boolean
        get() = settings.isServiceEnabled && serviceState.isRunning
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val preferences = WakePreferences(appContext)

    private val _deviceReport = MutableStateFlow(DeviceCapabilityInspector.inspect(appContext))
    private val _startError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<MainUiState> = combine(
        preferences.settingsFlow,
        DoubleTapWakeService.runtimeState,
        _deviceReport,
        _startError,
    ) { settings, runtime, report, error ->
        MainUiState(
            settings = settings,
            serviceState = runtime,
            deviceReport = report,
            startErrorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MainUiState(deviceReport = _deviceReport.value),
    )

    init {
        // Reconcile persisted state with running service on app open
        viewModelScope.launch {
            val saved = preferences.getSnapshot()
            val report = _deviceReport.value
            if (saved.isServiceEnabled && report.hasUsableMotionSensor && !DoubleTapWakeService.runtimeState.value.isRunning) {
                startServiceSafely()
            }
        }
    }

    fun refreshDeviceCapabilities() {
        _deviceReport.value = DeviceCapabilityInspector.inspect(appContext)
    }

    fun toggleFeature(enable: Boolean) {
        viewModelScope.launch {
            _startError.value = null
            if (enable) {
                if (!_deviceReport.value.hasUsableMotionSensor) {
                    preferences.setServiceEnabled(false)
                    return@launch
                }
                preferences.setServiceEnabled(true)
                startServiceSafely()
            } else {
                preferences.setServiceEnabled(false)
                DoubleTapWakeService.stop(appContext)
            }
        }
    }

    fun setEnableOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnableOnBoot(enabled)
        }
    }

    fun setSensitivity(sensitivity: TapSensitivity) {
        viewModelScope.launch {
            preferences.setSensitivity(sensitivity)
        }
    }

    fun setPocketProtection(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setPocketProtectionEnabled(enabled)
        }
    }

    fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(fallback)
        }
    }

    fun openDisplaySettings() {
        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(fallback)
        }
    }

    private suspend fun startServiceSafely() {
        try {
            DoubleTapWakeService.start(appContext)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to start DoubleTapWakeService", e)
            preferences.setServiceEnabled(false)
            _startError.value = e.localizedMessage
        }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
