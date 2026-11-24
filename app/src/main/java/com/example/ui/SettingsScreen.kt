package com.example.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.TapSensitivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: MainUiState,
    onNavigateBack: () -> Unit,
    onToggleBoot: (Boolean) -> Unit,
    onTogglePocketProtection: (Boolean) -> Unit,
    onSelectSensitivity: (TapSensitivity) -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val report = uiState.deviceReport
    val hasProximity = report?.hasProximitySensor ?: false
    val isUnrestricted = report?.isBatteryOptimizationIgnored ?: false

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: Behavior (Enable on boot + Pocket guard)
                SettingsSectionHeader(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = stringResource(R.string.settings_section_behavior),
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_boot_title),
                            subtitle = stringResource(R.string.settings_boot_desc),
                            checked = uiState.settings.enableOnBoot,
                            enabled = true,
                            testTag = "boot_switch",
                            onCheckedChange = onToggleBoot,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                        SettingsToggleRow(
                            title = stringResource(R.string.settings_pocket_title),
                            subtitle = if (hasProximity) {
                                stringResource(R.string.settings_pocket_desc)
                            } else {
                                stringResource(R.string.settings_pocket_unavailable)
                            },
                            checked = hasProximity && uiState.settings.pocketProtectionEnabled,
                            enabled = hasProximity,
                            testTag = "pocket_guard_switch",
                            onCheckedChange = onTogglePocketProtection,
                        )
                    }
                }

                // Section 2: Sensitivity
                SettingsSectionHeader(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.settings_section_sensitivity),
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TapSensitivity.entries.forEach { level ->
                                val selected = level == uiState.settings.sensitivity
                                Surface(
                                    onClick = { onSelectSensitivity(level) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("sensitivity_${level.name.lowercase()}"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                    ),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = when (level) {
                                                TapSensitivity.LOW -> stringResource(R.string.sensitivity_low)
                                                TapSensitivity.MEDIUM -> stringResource(R.string.sensitivity_medium)
                                                TapSensitivity.HIGH -> stringResource(R.string.sensitivity_high)
                                            },
                                            style = MaterialTheme.typography.labelLarge,
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

                        Text(
                            text = when (uiState.settings.sensitivity) {
                                TapSensitivity.LOW -> stringResource(R.string.sensitivity_low_desc)
                                TapSensitivity.MEDIUM -> stringResource(R.string.sensitivity_medium_desc)
                                TapSensitivity.HIGH -> stringResource(R.string.sensitivity_high_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Section 3: Battery Optimization Info
                SettingsSectionHeader(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = stringResource(R.string.settings_section_battery),
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isUnrestricted) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isUnrestricted) {
                                            Icons.Rounded.CheckCircle
                                        } else {
                                            Icons.Rounded.BatteryStd
                                        },
                                        contentDescription = null,
                                        tint = if (isUnrestricted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = if (isUnrestricted) {
                                            stringResource(R.string.battery_status_unrestricted)
                                        } else {
                                            stringResource(R.string.battery_status_optimized)
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isUnrestricted) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        },
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.battery_info_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        OutlinedButton(
                            onClick = onOpenBatterySettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("battery_optimization_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        ) {
                            Text(
                                text = stringResource(R.string.battery_open_settings),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // Section 4: Privacy & Permissions Transparency
                SettingsSectionHeader(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.settings_section_privacy),
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = stringResource(R.string.privacy_badges),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        PermissionExplanationRow(
                            title = stringResource(R.string.perm_wake_lock_title),
                            description = stringResource(R.string.perm_wake_lock_desc),
                        )
                        PermissionExplanationRow(
                            title = stringResource(R.string.perm_fgs_title),
                            description = stringResource(R.string.perm_fgs_desc),
                        )
                        PermissionExplanationRow(
                            title = stringResource(R.string.perm_boot_title),
                            description = stringResource(R.string.perm_boot_desc),
                        )
                        PermissionExplanationRow(
                            title = stringResource(R.string.perm_notif_title),
                            description = stringResource(R.string.perm_notif_desc),
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.why_no_accessibility),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun PermissionExplanationRow(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
