package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.FlightLog
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensurePilotProfile_authenticatedUser_createsAndObservesProfile() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = FlightRepository(firestore, auth)

        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensurePilotProfile("صقر-أليس")
        }
        assertTrue(result.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePilotProfile(uid).first { it != null }
        }
        assertNotNull(observed)
        assertEquals(uid, observed?.userId)
    }

    @Test
    fun recordFlightOutcome_authenticatedOwner_persistsLogAndUpdatesStats() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = FlightRepository(firestore, auth)

        val log = FlightLog(
            missionId = "moon_tranquility",
            missionName = "بحر السكون — القمر",
            success = true,
            score = 2640L,
            ratingStars = 3L,
            touchdownVerticalSpeed = 1.25,
            touchdownHorizontalSpeed = 0.4,
            touchdownTiltDeg = 2.1,
            fuelRemainingPercent = 52.5,
            flightDurationSec = 31.8,
        )

        val recordResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordFlightOutcome(log, unlockedTierCandidate = 2, defaultCallsign = "صقر-أليس")
        }
        assertTrue(recordResult.isSuccess)
        val logId = recordResult.getOrThrow()

        val logs = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeFlightLogs(uid).first { list -> list.any { it.id == logId } }
        }
        assertTrue(logs.any { it.id == logId && it.score == 2640L })

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observePilotProfile(uid).first { it != null && it.totalFlights >= 1L }
        }
        assertTrue((profile?.successfulLandings ?: 0L) >= 1L)
        assertTrue((profile?.highestUnlockedTier ?: 1L) >= 2L)
    }

    @Test
    fun observeFlightLogs_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FlightRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensurePilotProfile("صقر-أليس").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FlightRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeFlightLogs(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException for cross-user access, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun observeFlightLogs_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = FlightRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeFlightLogs("some_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }
        val firestoreException = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected FirebaseFirestoreException when unauthenticated, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_lander@test.com"
        const val BOB_EMAIL = "bob_lander@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
