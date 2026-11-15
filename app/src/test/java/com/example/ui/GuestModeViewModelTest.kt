package com.example.ui

import com.example.data.model.MissionCatalog
import com.example.game.FlightPhase
import com.example.game.LanderSimState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestModeViewModelTest {

    @Test
    fun guestMode_recordsFlightsUnlocksTiersAndManagesLogsLocally() {
        val vm = MissionControlViewModel(
            repository = null,
            currentUserId = null,
            defaultDisplayName = null,
            guestPrefs = null,
        )
        assertTrue(vm.isGuestMode)

        val initialProfile = (vm.profileState.value as UiState.Success).data
        assertEquals("طيار محلي", initialProfile.callsign)
        assertEquals(1L, initialProfile.highestUnlockedTier)

        // Update preferences locally
        vm.updatePreferences(callsign = "صقر الصحراء", tiltControlEnabled = true, soundEnabled = false)
        val afterPrefs = (vm.profileState.value as UiState.Success).data
        assertEquals("صقر الصحراء", afterPrefs.callsign)
        assertTrue(afterPrefs.tiltControlEnabled)
        assertEquals(false, afterPrefs.soundEnabled)

        // Record successful Tier 1 Moon landing -> unlocks Tier 2 Mars & adds flight log
        val moon = MissionCatalog.byId("moon_tranquility")
        vm.recordFlightResult(
            LanderSimState(
                mission = moon,
                phase = FlightPhase.LANDED_SAFE,
                touchdownScore = 2450,
                touchdownStars = 3,
                vy = -1.2f,
                vx = 0.3f,
                fuelKg = 340f,
                elapsedTimeSec = 28.5f,
            )
        )

        val afterLanding = (vm.profileState.value as UiState.Success).data
        assertEquals(1L, afterLanding.totalFlights)
        assertEquals(1L, afterLanding.successfulLandings)
        assertEquals(2450L, afterLanding.highestScore)
        assertEquals(2L, afterLanding.highestUnlockedTier)

        val logs = (vm.flightLogsState.value as UiState.Success).data
        assertEquals(1, logs.size)
        assertEquals("moon_tranquility", logs.first().missionId)

        // Delete log locally
        vm.deleteFlightLog(logs.first().id)
        val afterDelete = (vm.flightLogsState.value as UiState.Success).data
        assertTrue(afterDelete.isEmpty())
    }
}
