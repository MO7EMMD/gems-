package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.FalconLanderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SimplePhysicsPauseTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pauseButton_stopsSimulationAndShowsResumeAndRestartMenu() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            FalconLanderTheme {
                SimplePhysicsLabScreen(onBack = {})
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        // Pause button is visible and pause menu is hidden initially
        composeTestRule.onNodeWithTag("simple_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("simple_pause_overlay").assertDoesNotExist()

        // Tap Pause button
        composeTestRule.onNodeWithTag("simple_pause_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        // Pause menu with Resume and Restart buttons appears
        composeTestRule.onNodeWithTag("simple_pause_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("simple_resume_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("simple_restart_button").assertIsDisplayed()

        // Tap Resume -> pause menu closes
        composeTestRule.onNodeWithTag("simple_resume_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("simple_pause_overlay").assertDoesNotExist()

        // Pause again and tap Restart -> resets simulation and closes menu
        composeTestRule.onNodeWithTag("simple_pause_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("simple_pause_overlay").assertIsDisplayed()

        composeTestRule.onNodeWithTag("simple_restart_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("simple_pause_overlay").assertDoesNotExist()
    }
}
