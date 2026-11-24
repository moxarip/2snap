package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ObsidianDarkColorScheme = darkColorScheme(
    primary = MintPulse,
    onPrimary = OnMintPulse,
    primaryContainer = MintPulseDeep,
    onPrimaryContainer = MintGlow,
    secondary = PrecisionAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = PrecisionAmber,
    tertiary = MistSecondary,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = IceWhite,
    surface = CarbonSurface,
    onSurface = IceWhite,
    surfaceVariant = GraphiteCard,
    onSurfaceVariant = MistSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = CarbonSurface,
    surfaceContainer = GraphiteCard,
    surfaceContainerHigh = SlateElevated,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = CrimsonAlert,
    onError = ObsidianVoid,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert,
)

private val DoubleTapShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DoubleTapWakeTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = ObsidianDarkColorScheme,
        typography = DoubleTapTypography,
        shapes = DoubleTapShapes,
        content = content,
    )
}
