package com.example.game

import com.example.data.model.MissionCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanderPhysicsEngineTest {

    private val engine = LanderPhysicsEngine()

    @Test
    fun zeroThrottle_appliesGravityAndIncreasesDownwardSpeed() {
        val moon = MissionCatalog.byId("moon_tranquility")
        var state = engine.createInitialState(moon).copy(vx = 0f, vy = 0f, targetThrottle = 0f, actualThrottle = 0f)
        repeat(60) {
            state = engine.step(state, 1f / 60f, rcsInput = 0f, tiltAngleTargetRad = null)
        }
        assertEquals(FlightPhase.FLYING, state.phase)
        assertTrue("Expected downward velocity near -1.62 m/s, got ${state.vy}", state.vy < -1.4f)
        assertEquals(moon.initialFuelKg, state.fuelKg, 0.01f)
    }

    @Test
    fun fullThrottle_consumesFuelAndProducesUpwardAcceleration() {
        val moon = MissionCatalog.byId("moon_tranquility")
        var state = engine.createInitialState(moon).copy(vx = 0f, vy = -4f, targetThrottle = 1f)
        repeat(90) {
            state = engine.step(state, 1f / 60f, rcsInput = 0f, tiltAngleTargetRad = null)
        }
        assertTrue("Fuel should decrease at full throttle", state.fuelKg < moon.initialFuelKg)
        assertTrue("Full thrust on Moon should arrest descent (vy=${state.vy})", state.vy > -2.5f)
    }

    @Test
    fun softTouchdownOnPad_resultsInSafeLandingWithStarsAndScore() {
        val moon = MissionCatalog.byId("moon_tranquility")
        val initial = engine.createInitialState(moon)
        var state = initial.copy(
            x = initial.padCenterX,
            y = initial.padElevationY + LanderPhysicsEngine.LANDER_CG_HEIGHT + 0.4f,
            vx = 0.2f,
            vy = -1.2f,
            angleRad = 0.02f,
            targetThrottle = 0.2f,
            actualThrottle = 0.2f,
        )
        repeat(60) {
            if (state.phase == FlightPhase.FLYING) {
                state = engine.step(state, 1f / 60f, rcsInput = 0f, tiltAngleTargetRad = null)
            }
        }
        assertEquals(FlightPhase.LANDED_SAFE, state.phase)
        assertTrue(state.touchdownStars in 1..3)
        assertTrue(state.touchdownScore > 0)
    }

    @Test
    fun highVerticalSpeedTouchdown_resultsInCrash() {
        val moon = MissionCatalog.byId("moon_tranquility")
        val initial = engine.createInitialState(moon)
        var state = initial.copy(
            x = initial.padCenterX,
            y = initial.padElevationY + LanderPhysicsEngine.LANDER_CG_HEIGHT + 0.5f,
            vx = 0f,
            vy = -12.0f,
            angleRad = 0f,
        )
        repeat(30) {
            if (state.phase == FlightPhase.FLYING) {
                state = engine.step(state, 1f / 60f, rcsInput = 0f, tiltAngleTargetRad = null)
            }
        }
        assertEquals(FlightPhase.CRASHED, state.phase)
        assertEquals(0, state.touchdownScore)
        assertTrue(state.particles.any { it.kind == ParticleKind.EXPLOSION_SHOCKWAVE })
        assertTrue(state.particles.any { it.kind == ParticleKind.EXPLOSION_FIREBALL })
        assertTrue(state.particles.any { it.kind == ParticleKind.DEBRIS_SPARK })
    }

    @Test
    fun fuelDepletionInAir_endsGameImmediately() {
        val moon = MissionCatalog.byId("moon_tranquility")
        var state = engine.createInitialState(moon).copy(
            fuelKg = 0.25f,
            targetThrottle = 1f,
            actualThrottle = 1f,
        )
        repeat(10) {
            if (state.phase == FlightPhase.FLYING) {
                state = engine.step(state, 1f / 60f, rcsInput = 1f, tiltAngleTargetRad = null)
            }
        }
        assertEquals(FlightPhase.CRASHED, state.phase)
        assertEquals(0f, state.fuelKg, 0.001f)
        assertTrue(state.failureReasonAr?.contains("نفاد الوقود") == true)
    }
}
