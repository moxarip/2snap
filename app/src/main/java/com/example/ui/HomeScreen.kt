package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.DeviceCapabilityReport
import com.example.data.TapSensitivity

@Composable
fun HomeScreen(
    uiState: MainUiState,
    needsNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleFeature: (Boolean) -> Unit,
    onSensitivitySelected: (TapSensitivity) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDisplaySettings: () -> Unit,
) {
    var showTestSheet by remember { mutableStateOf(false) }

    val isEnabled = uiState.isEffectivelyEnabled
    val report = uiState.deviceReport
    val canEnable = report?.hasUsableMotionSensor ?: true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Minimalist Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        if (report != null) {
                            Text(
                                text = "${report.manufacturerDisplay} ${report.modelDisplay} · ${report.androidVersionLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_open_settings),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // Android 13+ Notification permission prompt (only if not yet granted)
                AnimatedVisibility(visible = needsNotificationPermission) {
                    NotificationPermissionCard(onGrantClick = onRequestNotificationPermission)
                }

                // No accelerometer hardware warning
                if (report != null && !report.hasUsableMotionSensor) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(R.string.no_accelerometer_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                // Hero Power Switch Centerpiece
                Spacer(modifier = Modifier.height(4.dp))
                HeroPowerToggle(
                    isEnabled = isEnabled,
                    enabledForClick = canEnable,
                    onClick = { onToggleFeature(!isEnabled) },
                )

                // Clear Status Indicator Pill + Subtitle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusIndicatorPill(isEnabled = isEnabled)

                    Text(
                        text = when {
                            !isEnabled -> stringResource(R.string.subtitle_inactive)
                            uiState.serviceState.isListeningNow -> stringResource(R.string.subtitle_active_listening)
                            else -> stringResource(R.string.subtitle_active_waiting)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                // Primary Action Row: Test & Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = { showTestSheet = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("test_button"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.TouchApp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_test),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }

                    FilledTonalButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("settings_action_button"),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_settings),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                // Short How-It-Works Explanation Card
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.how_it_works_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.how_it_works_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Honest Device & OEM Compatibility Card
                if (report != null) {
                    DeviceSupportCard(
                        report = report,
                        onOpenDisplaySettings = onOpenDisplaySettings,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showTestSheet) {
        TestTapSheet(
            sensitivity = uiState.settings.sensitivity,
            onSensitivitySelected = onSensitivitySelected,
            onDismiss = { showTestSheet = false },
        )
    }
}

@Composable
private fun HeroPowerToggle(
    isEnabled: Boolean,
    enabledForClick: Boolean,
    onClick: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val inactiveBorder = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface
    val activeContainer = MaterialTheme.colorScheme.primaryContainer

    val ringColor by animateColorAsState(
        targetValue = if (isEnabled) primaryColor else inactiveBorder,
        animationSpec = tween(280),
        label = "toggle_ring_color",
    )
    val coreFill by animateColorAsState(
        targetValue = if (isEnabled) activeContainer else surfaceColor,
        animationSpec = tween(280),
        label = "toggle_core_fill",
    )
    val contentTint by animateColorAsState(
        targetValue = if (isEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(280),
        label = "toggle_content_tint",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "tap_pulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse_progress",
    )

    val toggleDescription = stringResource(R.string.cd_power_toggle)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(240.dp)
            .drawBehind {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.38f

                // Ambient radial glow behind the switch
                if (isEnabled) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.24f),
                                primaryColor.copy(alpha = 0.06f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.minDimension * 0.52f,
                        ),
                        radius = size.minDimension * 0.52f,
                        center = center,
                    )

                    // Two subtle concentric ripple waves evoking physical double tap
                    val wave1Radius = baseRadius + (size.minDimension * 0.14f) * pulseProgress
                    val wave1Alpha = (1f - pulseProgress) * 0.45f
                    drawCircle(
                        color = primaryColor.copy(alpha = wave1Alpha),
                        radius = wave1Radius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )

                    val phase2 = (pulseProgress + 0.35f) % 1f
                    val wave2Radius = baseRadius + (size.minDimension * 0.14f) * phase2
                    val wave2Alpha = (1f - phase2) * 0.30f
                    drawCircle(
                        color = primaryColor.copy(alpha = wave2Alpha),
                        radius = wave2Radius,
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                } else {
                    // Subtle static precision ring when OFF
                    drawCircle(
                        color = inactiveBorder.copy(alpha = 0.45f),
                        radius = baseRadius + 14.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .semantics { contentDescription = toggleDescription }
                .clickable(
                    enabled = enabledForClick,
                    role = Role.Switch,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = primaryColor),
                    onClick = onClick,
                )
                .testTag("master_toggle_button"),
            shape = CircleShape,
            color = coreFill,
            border = BorderStroke(width = if (isEnabled) 2.5.dp else 1.5.dp, color = ringColor),
            tonalElevation = if (isEnabled) 8.dp else 2.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = contentTint,
                    modifier = Modifier.size(54.dp),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isEnabled) stringResource(R.string.toggle_on) else stringResource(R.string.toggle_off),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = contentTint,
                )
            }
        }
    }
}

@Composable
private fun StatusIndicatorPill(isEnabled: Boolean) {
    val dotColor by animateColorAsState(
        targetValue = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "status_dot",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isEnabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        label = "status_bg",
    )

    Surface(
        modifier = Modifier.testTag("status_indicator"),
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = if (isEnabled) stringResource(R.string.status_enabled) else stringResource(R.string.status_disabled),
                style = MaterialTheme.typography.labelLarge,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationPermissionCard(onGrantClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.notif_permission_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.notif_permission_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onGrantClick,
                modifier = Modifier.testTag("grant_notification_permission_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text(text = stringResource(R.string.notif_permission_grant))
            }
        }
    }
}

@Composable
private fun DeviceSupportCard(
    report: DeviceCapabilityReport,
    onOpenDisplaySettings: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.device_support_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Sensor capability chips row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CapabilityBadge(
                    icon = Icons.Rounded.Sensors,
                    label = when {
                        report.hasHardwareWakeUpSensor -> stringResource(R.string.sensor_wakeup_badge)
                        report.hasUsableMotionSensor -> stringResource(R.string.sensor_standard_badge)
                        else -> stringResource(R.string.sensor_missing_badge)
                    },
                    accent = when {
                        report.hasHardwareWakeUpSensor -> MaterialTheme.colorScheme.primary
                        report.hasUsableMotionSensor -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.weight(1f),
                )

                CapabilityBadge(
                    icon = Icons.Rounded.Shield,
                    label = if (report.hasProximitySensor) {
                        stringResource(R.string.proximity_available)
                    } else {
                        stringResource(R.string.proximity_unavailable)
                    },
                    accent = if (report.hasProximitySensor) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            if (report.hasUsableMotionSensor && !report.hasHardwareWakeUpSensor) {
                Text(
                    text = stringResource(R.string.oem_deep_doze_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }

            Text(
                text = stringResource(report.oemGuidanceRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = onOpenDisplaySettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("display_settings_button"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    text = stringResource(R.string.btn_display_settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CapabilityBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
            )
        }
    }
}
