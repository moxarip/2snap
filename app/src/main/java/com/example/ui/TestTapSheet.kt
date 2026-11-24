package com.example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DeviceCapabilityInspector
import com.example.core.DoubleTapDetector
import com.example.core.ScreenWaker
import com.example.core.TapDetectionEvent
import com.example.core.WakeResult
import com.example.data.TapSensitivity
import kotlinx.coroutines.delay

private enum class LiveTapUiState {
    READY,
    FIRST_TAP,
    DOUBLE_TAP,
    NOISE_REJECTED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestTapSheet(
    sensitivity: TapSensitivity,
    onSensitivitySelected: (TapSensitivity) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var liveState by remember { mutableStateOf(LiveTapUiState.READY) }
    var peakImpulse by remember { mutableFloatStateOf(0f) }
    var doubleTapCount by remember { mutableIntStateOf(0) }
    var hasSensor by remember { mutableStateOf(true) }

    var wakeCountdown by remember { mutableIntStateOf(0) }
    var wakeFeedback by remember { mutableStateOf<String?>(null) }

    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    val selectedSensor = remember(sensorManager) {
        sensorManager?.let { DeviceCapabilityInspector.selectBestMotionSensor(it) }
    }
    val detector = remember(selectedSensor) {
        selectedSensor?.let {
            DoubleTapDetector(
                usesLinearAcceleration = it.isLinearAcceleration,
                initialSensitivity = sensitivity,
            )
        }
    }

    LaunchedEffect(sensitivity, detector) {
        detector?.updateSensitivity(sensitivity)
    }

    // Reset live indicator back to READY after feedback settles
    LaunchedEffect(liveState, doubleTapCount) {
        if (liveState != LiveTapUiState.READY) {
            delay(1600L)
            liveState = LiveTapUiState.READY
        }
    }

    // Smoothly decay visual peak bar so user sees crisp impulse spikes
    LaunchedEffect(peakImpulse) {
        if (peakImpulse > 0.05f) {
            delay(90L)
            peakImpulse = (peakImpulse * 0.72f).let { if (it < 0.05f) 0f else it }
        }
    }

    // Register real hardware motion sensor while the test sheet is open
    DisposableEffect(sensorManager, selectedSensor, detector) {
        if (sensorManager == null || selectedSensor == null || detector == null) {
            hasSensor = false
            onDispose {}
        } else {
            hasSensor = true
            detector.reset()
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val tsMs = event.timestamp / 1_000_000L
                    val result = detector.processSample(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2],
                        timestampMs = if (tsMs > 0L) tsMs else SystemClock.elapsedRealtime(),
                    )
                    if (result.impulseMs2 > peakImpulse) {
                        peakImpulse = result.impulseMs2
                    }
                    when (result.event) {
                        is TapDetectionEvent.FirstTapDetected -> {
                            liveState = LiveTapUiState.FIRST_TAP
                        }
                        is TapDetectionEvent.DoubleTapDetected -> {
                            liveState = LiveTapUiState.DOUBLE_TAP
                            doubleTapCount += 1
                        }
                        TapDetectionEvent.NoiseRejected -> {
                            liveState = LiveTapUiState.NOISE_REJECTED
                        }
                        TapDetectionEvent.None -> Unit
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            sensorManager.registerListener(
                listener,
                selectedSensor.sensor,
                SensorManager.SENSOR_DELAY_GAME,
            )

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    // 5-second delayed screen-off wake verification timer
    LaunchedEffect(wakeCountdown) {
        if (wakeCountdown > 0) {
            delay(1000L)
            val next = wakeCountdown - 1
            wakeCountdown = next
            if (next == 0) {
                wakeFeedback = when (ScreenWaker.wakeScreen(context)) {
                    WakeResult.WokeLockedScreen,
                    WakeResult.WokeUnlockedScreen -> context.getString(R.string.test_wake_done)
                    WakeResult.AlreadyInteractive -> context.getString(R.string.test_wake_already_on)
                    is WakeResult.Failed -> context.getString(R.string.test_wake_already_on)
                }
            }
        }
    }

    val statusColor by animateColorAsState(
        targetValue = when (liveState) {
            LiveTapUiState.READY -> MaterialTheme.colorScheme.onSurfaceVariant
            LiveTapUiState.FIRST_TAP -> MaterialTheme.colorScheme.secondary
            LiveTapUiState.DOUBLE_TAP -> MaterialTheme.colorScheme.primary
            LiveTapUiState.NOISE_REJECTED -> MaterialTheme.colorScheme.error
        },
        label = "live_tap_status_color",
    )

    val maxMeterScale = (sensitivity.impulseThresholdMs2 * 2f).coerceAtLeast(8f)
    val animatedMeterFraction by animateFloatAsState(
        targetValue = (peakImpulse / maxMeterScale).coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "impulse_meter",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sensors,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.test_sheet_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_test_sheet_button"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cd_close_dialog),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Section 1: Live Tap Sensor Test
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.test_live_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (hasSensor) {
                            stringResource(R.string.test_live_hint)
                        } else {
                            stringResource(R.string.no_accelerometer_warning)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (hasSensor) {
                        // Status pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = when (liveState) {
                                        LiveTapUiState.DOUBLE_TAP -> Icons.Rounded.CheckCircle
                                        else -> Icons.Rounded.TouchApp
                                    },
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = when (liveState) {
                                        LiveTapUiState.READY -> stringResource(R.string.test_state_ready)
                                        LiveTapUiState.FIRST_TAP -> stringResource(R.string.test_state_first_tap)
                                        LiveTapUiState.DOUBLE_TAP -> stringResource(R.string.test_state_double_tap)
                                        LiveTapUiState.NOISE_REJECTED -> stringResource(R.string.test_state_noise)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = statusColor,
                                )
                            }
                        }

                        // Impulse visualizer bar with threshold tick
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animatedMeterFraction)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(
                                            if (peakImpulse >= sensitivity.impulseThresholdMs2) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                            }
                                        ),
                                )
                                // Threshold marker at 50% (since maxMeterScale = 2x threshold)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .width(2.dp)
                                        .fillMaxHeight()
                                        .background(MaterialTheme.colorScheme.secondary),
                                )
                            }
                            Text(
                                text = stringResource(
                                    R.string.test_impulse_label,
                                    peakImpulse,
                                    sensitivity.impulseThresholdMs2,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        // Quick sensitivity selector inside Test panel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TapSensitivity.entries.forEach { level ->
                                val selected = level == sensitivity
                                Surface(
                                    onClick = { onSensitivitySelected(level) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("test_sensitivity_${level.name.lowercase()}"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    ),
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = when (level) {
                                                TapSensitivity.LOW -> stringResource(R.string.sensitivity_low)
                                                TapSensitivity.MEDIUM -> stringResource(R.string.sensitivity_medium)
                                                TapSensitivity.HIGH -> stringResource(R.string.sensitivity_high)
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            // Section 2: Screen-Off Wake Test (PowerManager verification)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.test_wake_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.test_wake_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (wakeCountdown > 0) {
                        OutlinedButton(
                            onClick = { wakeCountdown = 0 },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("wake_test_cancel_button"),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.test_wake_cancel, wakeCountdown),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                wakeFeedback = null
                                wakeCountdown = 5
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("wake_test_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.test_wake_start))
                        }
                    }

                    wakeFeedback?.let { feedback ->
                        Text(
                            text = feedback,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
