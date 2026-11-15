package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val FalconDarkColorScheme = darkColorScheme(
    primary = TelemetryCyan,
    onPrimary = SpaceVoid,
    primaryContainer = TelemetryCyanDeep,
    onPrimaryContainer = TextPrimary,
    secondary = ThrusterAmber,
    onSecondary = SpaceVoid,
    secondaryContainer = Color(0xFF452406),
    onSecondaryContainer = ThrusterAmber,
    tertiary = SafeEmerald,
    onTertiary = SpaceVoid,
    error = CriticalCrimson,
    onError = TextPrimary,
    background = SpaceVoid,
    onBackground = TextPrimary,
    surface = SpaceObsidian,
    onSurface = TextPrimary,
    surfaceVariant = SpaceCarbon,
    onSurfaceVariant = TextSecondary,
    outline = CockpitBorder,
)

private val FalconShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun FalconLanderTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = FalconDarkColorScheme,
        typography = FalconTypography,
        shapes = FalconShapes,
        content = content,
    )
}
