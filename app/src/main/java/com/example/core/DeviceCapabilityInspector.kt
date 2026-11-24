package com.example.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.StringRes
import com.example.R

enum class MotionSensorTier {
    WAKE_UP_LINEAR_ACCELERATION,
    WAKE_UP_ACCELEROMETER,
    STANDARD_LINEAR_ACCELERATION,
    STANDARD_ACCELEROMETER,
    NONE,
}

data class SelectedMotionSensor(
    val sensor: Sensor,
    val tier: MotionSensorTier,
    val isLinearAcceleration: Boolean,
    val isWakeUp: Boolean,
)

data class DeviceCapabilityReport(
    val manufacturerDisplay: String,
    val modelDisplay: String,
    val androidVersionLabel: String,
    val sdkInt: Int,
    val sensorTier: MotionSensorTier,
    val sensorName: String?,
    val sensorPowerMa: Float?,
    val hasProximitySensor: Boolean,
    val isBatteryOptimizationIgnored: Boolean,
    @StringRes val oemGuidanceRes: Int,
) {
    val hasUsableMotionSensor: Boolean
        get() = sensorTier != MotionSensorTier.NONE

    val hasHardwareWakeUpSensor: Boolean
        get() = sensorTier == MotionSensorTier.WAKE_UP_LINEAR_ACCELERATION ||
            sensorTier == MotionSensorTier.WAKE_UP_ACCELEROMETER
}

object DeviceCapabilityInspector {

    fun selectBestMotionSensor(sensorManager: SensorManager): SelectedMotionSensor? {
        // 1. Prefer hardware wake-up linear acceleration (lowest CPU wake + already gravity-free)
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION, true)?.let { sensor ->
            return SelectedMotionSensor(
                sensor = sensor,
                tier = MotionSensorTier.WAKE_UP_LINEAR_ACCELERATION,
                isLinearAcceleration = true,
                isWakeUp = true,
            )
        }

        // 2. Hardware wake-up raw accelerometer
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)?.let { sensor ->
            return SelectedMotionSensor(
                sensor = sensor,
                tier = MotionSensorTier.WAKE_UP_ACCELEROMETER,
                isLinearAcceleration = false,
                isWakeUp = true,
            )
        }

        // 3. Standard linear acceleration
        sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sensor ->
            return SelectedMotionSensor(
                sensor = sensor,
                tier = MotionSensorTier.STANDARD_LINEAR_ACCELERATION,
                isLinearAcceleration = true,
                isWakeUp = false,
            )
        }

        // 4. Standard raw accelerometer
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            return SelectedMotionSensor(
                sensor = sensor,
                tier = MotionSensorTier.STANDARD_ACCELEROMETER,
                isLinearAcceleration = false,
                isWakeUp = false,
            )
        }

        return null
    }

    fun inspect(context: Context): DeviceCapabilityReport {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val selected = sensorManager?.let { selectBestMotionSensor(it) }
        val hasProximity = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isIgnoringBatteryOpt = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false

        val rawManufacturer = Build.MANUFACTURER.orEmpty().trim()
        val manufacturerFormatted = rawManufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }.ifEmpty { "Android" }

        return DeviceCapabilityReport(
            manufacturerDisplay = manufacturerFormatted,
            modelDisplay = Build.MODEL.orEmpty().ifEmpty { "Device" },
            androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            sdkInt = Build.VERSION.SDK_INT,
            sensorTier = selected?.tier ?: MotionSensorTier.NONE,
            sensorName = selected?.sensor?.name,
            sensorPowerMa = selected?.sensor?.power,
            hasProximitySensor = hasProximity,
            isBatteryOptimizationIgnored = isIgnoringBatteryOpt,
            oemGuidanceRes = resolveOemGuidanceRes(rawManufacturer, Build.BRAND.orEmpty()),
        )
    }

    @StringRes
    private fun resolveOemGuidanceRes(manufacturer: String, brand: String): Int {
        val key = "$manufacturer $brand".lowercase()
        return when {
            key.contains("samsung") -> R.string.oem_samsung
            key.contains("google") || key.contains("pixel") -> R.string.oem_google
            key.contains("xiaomi") || key.contains("redmi") || key.contains("poco") -> R.string.oem_xiaomi
            key.contains("oneplus") || key.contains("oppo") || key.contains("realme") -> R.string.oem_oneplus_oppo
            key.contains("motorola") || key.contains("moto") -> R.string.oem_motorola
            key.contains("huawei") || key.contains("honor") -> R.string.oem_huawei_honor
            key.contains("sony") -> R.string.oem_sony
            else -> R.string.oem_generic
        }
    }
}
