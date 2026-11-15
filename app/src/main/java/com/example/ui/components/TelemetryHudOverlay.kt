package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SpaceCarbon
import com.example.ui.theme.SpaceObsidian
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFontFamily
import com.example.ui.theme.ThrusterAmber
import com.example.ui.theme.ThrusterOrange
import java.util.Locale
import kotlin.math.abs

data class TelemetryHudData(
    val altitudeMeters: Float,
    val verticalVelocityMps: Float,
    val horizontalVelocityMps: Float,
    val fuelPercent: Float,
    val safeVerticalSpeedMps: Float = 3.5f,
    val safeHorizontalSpeedMps: Float = 2.5f,
    val isBurningFuel: Boolean = false,
    val fuelMassLabel: String? = null,
    val extraMetricLabel: String? = null,
    val extraMetricValue: String? = null,
    val extraMetricColor: Color = TelemetryCyan,
    val secondaryExtraLabel: String? = null,
    val secondaryExtraValue: String? = null,
    val secondaryExtraColor: Color = Color.White,
)

@Composable
fun TelemetryHudOverlay(
    data: TelemetryHudData,
    modifier: Modifier = Modifier,
) {
    val vAbs = abs(data.verticalVelocityMps)
    val hAbs = abs(data.horizontalVelocityMps)

    val vSpeedColor = when {
        vAbs <= data.safeVerticalSpeedMps * 0.7f -> SafeEmerald
        vAbs <= data.safeVerticalSpeedMps -> ThrusterAmber
        else -> CriticalCrimson
    }
    val hSpeedColor = when {
        hAbs <= data.safeHorizontalSpeedMps * 0.7f -> SafeEmerald
        hAbs <= data.safeHorizontalSpeedMps -> ThrusterAmber
        else -> CriticalCrimson
    }
    val fuelClamped = data.fuelPercent.coerceIn(0f, 100f)
    val fuelColor = when {
        fuelClamped <= 15f -> CriticalCrimson
        fuelClamped <= 30f -> ThrusterAmber
        data.isBurningFuel -> ThrusterOrange
        else -> TelemetryCyan
    }

    Surface(
        color = SpaceObsidian.copy(alpha = 0.86f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp))
            .testTag("hud_overlay"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudTelemetryCell(
                    icon = Icons.Filled.Height,
                    label = "الارتفاع",
                    value = "${String.format(Locale.US, "%.1f", data.altitudeMeters.coerceAtLeast(0f))}m",
                    accent = TelemetryCyan,
                    valueTag = "hud_altitude_value",
                    modifier = Modifier.weight(1f),
                )
                HudTelemetryCell(
                    icon = Icons.Filled.SwapVert,
                    label = "السرعة عمودي",
                    value = "${String.format(Locale.US, "%.1f", data.verticalVelocityMps)}m/s",
                    accent = vSpeedColor,
                    valueTag = "hud_vertical_velocity_value",
                    modifier = Modifier.weight(1f),
                )
                HudTelemetryCell(
                    icon = Icons.Filled.SwapHoriz,
                    label = "السرعة أفقي",
                    value = "${String.format(Locale.US, "%.1f", data.horizontalVelocityMps)}m/s",
                    accent = hSpeedColor,
                    valueTag = "hud_horizontal_velocity_value",
                    modifier = Modifier.weight(1f),
                )
                if (data.extraMetricLabel != null && data.extraMetricValue != null) {
                    HudTelemetryCell(
                        icon = null,
                        label = data.extraMetricLabel,
                        value = data.extraMetricValue,
                        accent = data.extraMetricColor,
                        valueTag = "hud_extra_metric_value",
                        modifier = Modifier.weight(0.85f),
                    )
                }
                if (data.secondaryExtraLabel != null && data.secondaryExtraValue != null) {
                    HudTelemetryCell(
                        icon = null,
                        label = data.secondaryExtraLabel,
                        value = data.secondaryExtraValue,
                        accent = data.secondaryExtraColor,
                        valueTag = "hud_secondary_metric_value",
                        modifier = Modifier.weight(0.85f),
                    )
                }
            }

            // Real-time Fuel Percentage & Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.LocalGasStation,
                        contentDescription = "نسبة الوقود المتبقية",
                        tint = fuelColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (data.fuelMassLabel != null) {
                            "الوقود ${data.fuelMassLabel}"
                        } else if (data.isBurningFuel && fuelClamped > 0f) {
                            "الوقود (احتراق)"
                        } else {
                            "الوقود"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = fuelColor,
                    )
                }

                if (data.isBurningFuel && fuelClamped > 0f) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(ThrusterOrange)
                    )
                }

                LinearProgressIndicator(
                    progress = { fuelClamped / 100f },
                    color = fuelColor,
                    trackColor = SpaceCarbon,
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .testTag("hud_fuel_bar"),
                )

                Text(
                    text = "${String.format(Locale.US, "%.0f", fuelClamped)}%",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = TelemetryMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = fuelColor,
                    modifier = Modifier.testTag("hud_fuel_value"),
                )
            }
        }
    }
}

@Composable
private fun HudTelemetryCell(
    icon: ImageVector?,
    label: String,
    value: String,
    accent: Color,
    valueTag: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = TelemetryMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            color = accent,
            maxLines = 1,
            modifier = Modifier.testTag(valueTag),
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
