package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.DeviceCapabilityInspector
import com.example.core.DoubleTapDetector
import com.example.core.ScreenWaker
import com.example.core.SelectedMotionSensor
import com.example.core.TapDetectionEvent
import com.example.data.WakePreferences
import com.example.data.WakeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class ServiceRuntimeState(
    val isRunning: Boolean = false,
    val isListeningNow: Boolean = false,
    val activeSensorName: String? = null,
    val isWakeUpSensor: Boolean = false,
    val lastWakeTimestampMs: Long? = null,
)

/**
 * Minimal-footprint Foreground Service that monitors physical double taps ONLY while the
 * screen is OFF. All sensors are immediately unregistered while the screen is ON to keep
 * CPU, memory, and battery impact near zero during normal phone usage.
 */
class DoubleTapWakeService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var preferences: WakePreferences
    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private lateinit var notificationManager: NotificationManager

    private var selectedMotionSensor: SelectedMotionSensor? = null
    private var proximitySensor: Sensor? = null
    private var detector: DoubleTapDetector? = null

    private var currentSettings: WakeSettings = WakeSettings()
    private var sensorsRegistered = false
    private var isPocketCovered = false
    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> registerSensorsForScreenOff()
                Intent.ACTION_SCREEN_ON -> unregisterSensorsForScreenOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = WakePreferences(applicationContext)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        selectedMotionSensor = DeviceCapabilityInspector.selectBestMotionSensor(sensorManager)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        selectedMotionSensor?.let { chosen ->
            detector = DoubleTapDetector(
                usesLinearAcceleration = chosen.isLinearAcceleration,
                initialSensitivity = currentSettings.sensitivity,
            )
        }

        ensureNotificationChannel(this)
        registerScreenReceiver()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            serviceScope.launch {
                preferences.setServiceEnabled(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (selectedMotionSensor == null) {
            Log.w(TAG, "No accelerometer available on this device; stopping service.")
            serviceScope.launch { preferences.setServiceEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification(isListening = !powerManager.isInteractive)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _runtimeState.value = ServiceRuntimeState(
            isRunning = true,
            isListeningNow = sensorsRegistered,
            activeSensorName = selectedMotionSensor?.sensor?.name,
            isWakeUpSensor = selectedMotionSensor?.isWakeUp ?: false,
            lastWakeTimestampMs = _runtimeState.value.lastWakeTimestampMs,
        )

        // Only attach sensors immediately if the screen is already off (e.g., started after boot)
        if (!powerManager.isInteractive) {
            registerSensorsForScreenOff()
        } else {
            unregisterSensorsForScreenOn()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterSensorsForScreenOn()
        unregisterScreenReceiver()
        serviceScope.cancel()
        _runtimeState.value = ServiceRuntimeState(
            isRunning = false,
            isListeningNow = false,
            activeSensorName = null,
            isWakeUpSensor = false,
            lastWakeTimestampMs = _runtimeState.value.lastWakeTimestampMs,
        )
        super.onDestroy()
    }

    private fun observeSettings() {
        serviceScope.launch {
            preferences.settingsFlow.collectLatest { settings ->
                val pocketChanged = settings.pocketProtectionEnabled != currentSettings.pocketProtectionEnabled
                currentSettings = settings
                detector?.updateSensitivity(settings.sensitivity)

                if (!settings.isServiceEnabled) {
                    stopSelf()
                    return@collectLatest
                }

                if (pocketChanged && sensorsRegistered) {
                    // Refresh proximity sensor registration cleanly
                    unregisterSensorsForScreenOn()
                    if (!powerManager.isInteractive) {
                        registerSensorsForScreenOff()
                    }
                }
            }
        }
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(
            this,
            screenStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (_: IllegalArgumentException) {
            // Receiver was already unregistered
        }
        receiverRegistered = false
    }

    private fun registerSensorsForScreenOff() {
        if (sensorsRegistered) return
        val chosen = selectedMotionSensor ?: return

        detector?.reset()
        isPocketCovered = false

        // SENSOR_DELAY_GAME (~20ms / 50Hz) captures crisp physical tap impulses reliably
        // while keeping average sensor hub power around ~0.1–0.5 mA.
        val registered = sensorManager.registerListener(
            this,
            chosen.sensor,
            SensorManager.SENSOR_DELAY_GAME,
        )

        if (registered) {
            if (currentSettings.pocketProtectionEnabled && proximitySensor != null) {
                sensorManager.registerListener(
                    this,
                    proximitySensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                )
            }
            sensorsRegistered = true
            _runtimeState.value = _runtimeState.value.copy(isListeningNow = true)
            updateNotification(isListening = true)
        }
    }

    private fun unregisterSensorsForScreenOn() {
        if (sensorsRegistered) {
            sensorManager.unregisterListener(this)
            sensorsRegistered = false
            isPocketCovered = false
            detector?.reset()
        }
        _runtimeState.value = _runtimeState.value.copy(isListeningNow = false)
        if (_runtimeState.value.isRunning) {
            updateNotification(isListening = false)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
                val distance = event.values.firstOrNull() ?: maxRange
                isPocketCovered = distance < maxRange && distance < 5f
            }

            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                if (currentSettings.pocketProtectionEnabled && isPocketCovered) {
                    return
                }
                val activeDetector = detector ?: return
                val sampleTimeMs = event.timestamp / 1_000_000L
                val result = activeDetector.processSample(
                    x = event.values[0],
                    y = event.values[1],
                    z = event.values[2],
                    timestampMs = if (sampleTimeMs > 0L) sampleTimeMs else SystemClock.elapsedRealtime(),
                )

                if (result.event is TapDetectionEvent.DoubleTapDetected) {
                    ScreenWaker.wakeScreen(applicationContext)
                    _runtimeState.value = _runtimeState.value.copy(
                        lastWakeTimestampMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun updateNotification(isListening: Boolean) {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(isListening))
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+; service still remains valid
        }
    }

    private fun buildNotification(isListening: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DoubleTapWakeService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sensorTag = if (selectedMotionSensor?.isWakeUp == true) {
            getString(R.string.sensor_wakeup_badge)
        } else {
            getString(R.string.sensor_standard_badge)
        }

        val bodyText = if (isListening) {
            getString(R.string.notif_body_listening, sensorTag)
        } else {
            getString(R.string.notif_body_waiting)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_tap)
            .setContentTitle(getString(R.string.notif_title_active))
            .setContentText(bodyText)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notif_action_stop), stopIntent)
            .build()
    }

    companion object {
        private const val TAG = "DoubleTapWakeService"
        const val CHANNEL_ID = "double_tap_wake_service_channel"
        const val NOTIFICATION_ID = 1001
        const val BOOT_BLOCKED_NOTIFICATION_ID = 1002
        const val ACTION_START = "com.example.action.START_WAKE_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_WAKE_SERVICE"

        private val _runtimeState = MutableStateFlow(ServiceRuntimeState())
        val runtimeState: StateFlow<ServiceRuntimeState> = _runtimeState.asStateFlow()

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notif_channel_desc)
                    setShowBadge(false)
                    enableVibration(false)
                    enableLights(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, DoubleTapWakeService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DoubleTapWakeService::class.java))
        }
    }
}
