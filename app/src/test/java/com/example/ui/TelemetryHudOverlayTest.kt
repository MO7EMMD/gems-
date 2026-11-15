package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.components.TelemetryHudData
import com.example.ui.components.TelemetryHudOverlay
import com.example.ui.theme.FalconLanderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TelemetryHudOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun displaysAndUpdatesRealTimeTelemetryValues() {
        var hudData by mutableStateOf(
            TelemetryHudData(
                altitudeMeters = 84.5f,
                verticalVelocityMps = 4.2f,
                horizontalVelocityMps = -1.8f,
                fuelPercent = 76f,
            )
        )

        composeTestRule.setContent {
            FalconLanderTheme {
                TelemetryHudOverlay(data = hudData)
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("hud_overlay").assertExists()
        composeTestRule.onNodeWithTag("hud_altitude_value").assertTextEquals("84.5m")
        composeTestRule.onNodeWithTag("hud_vertical_velocity_value").assertTextEquals("4.2m/s")
        composeTestRule.onNodeWithTag("hud_horizontal_velocity_value").assertTextEquals("-1.8m/s")
        composeTestRule.onNodeWithTag("hud_fuel_value").assertTextEquals("76%")

        // Simulate real-time frame update
        hudData = hudData.copy(
            altitudeMeters = 12.3f,
            verticalVelocityMps = 1.4f,
            horizontalVelocityMps = 0.5f,
            fuelPercent = 18f,
            isBurningFuel = true,
        )
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("hud_altitude_value").assertTextEquals("12.3m")
        composeTestRule.onNodeWithTag("hud_vertical_velocity_value").assertTextEquals("1.4m/s")
        composeTestRule.onNodeWithTag("hud_horizontal_velocity_value").assertTextEquals("0.5m/s")
        composeTestRule.onNodeWithTag("hud_fuel_value").assertTextEquals("18%")
    }
}
