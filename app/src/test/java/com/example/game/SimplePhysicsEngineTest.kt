package com.example.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimplePhysicsEngineTest {

    private val engine = SimplePhysicsEngine()

    private fun centeredPadTerrain() = RandomTerrainProfile(
        heightsMeters = List(21) { 0f },
        padStartNorm = 0.35f,
        padEndNorm = 0.65f,
        padAltitudeMeters = 0f,
    )

    @Test
    fun gravityAcceleratesSpacecraftDownwardEachFrame() {
        var state = SimpleSpacecraftState(
            altitudeMeters = 100f,
            velocityY = 0f,
            gravity = 9.81f,
            terrain = centeredPadTerrain(),
        )
        repeat(60) {
            state = engine.stepFrame(state, 1f / 60f)
        }
        assertEquals(SimpleLandingStatus.DESCENDING, state.status)
        assertTrue("Expected velocityY near 9.81 m/s, got ${state.velocityY}", state.velocityY in 9.5f..10.1f)
        assertTrue("Expected altitude to drop from 100m, got ${state.altitudeMeters}", state.altitudeMeters < 96f)
    }

    @Test
    fun activeThrusterDeceleratesDescentAndConsumesFuel() {
        var state = SimpleSpacecraftState(
            altitudeMeters = 100f,
            velocityY = 6f,
            gravity = 1.62f,
            thrusterPower = 4.8f,
            isThrusterActive = true,
            terrain = centeredPadTerrain(),
        )
        repeat(60) {
            state = engine.stepFrame(state, 1f / 60f)
        }
        assertTrue("Thruster should reduce downward velocity", state.velocityY < 3.5f)
        assertTrue("Fuel should decrease when thruster burns", state.fuelPercent < 100f)
    }

    @Test
    fun touchdownWithinSafeSpeedOnPadMarksSafeLanding() {
        var state = SimpleSpacecraftState(
            altitudeMeters = 0.5f,
            positionXNorm = 0.5f,
            velocityY = 2.0f,
            gravity = 1.62f,
            safeLandingSpeed = 3.5f,
            terrain = centeredPadTerrain(),
        )
        repeat(30) {
            state = engine.stepFrame(state, 1f / 60f)
        }
        assertEquals(SimpleLandingStatus.SAFE_LANDING, state.status)
        assertEquals(0f, state.altitudeMeters, 0.001f)
    }

    @Test
    fun highSpeedTouchdownMarksCrashed() {
        var state = SimpleSpacecraftState(
            altitudeMeters = 0.5f,
            positionXNorm = 0.5f,
            velocityY = 8.5f,
            gravity = 3.72f,
            safeLandingSpeed = 3.5f,
            terrain = centeredPadTerrain(),
        )
        repeat(20) {
            state = engine.stepFrame(state, 1f / 60f)
        }
        assertEquals(SimpleLandingStatus.CRASHED, state.status)
    }

    @Test
    fun fuelReachingZeroInAirEndsGameAsOutOfFuel() {
        var state = SimpleSpacecraftState(
            altitudeMeters = 80f,
            velocityY = 1f,
            fuelPercent = 0.4f,
            isThrusterActive = true,
            terrain = centeredPadTerrain(),
        )
        repeat(10) {
            state = engine.stepFrame(state, 1f / 60f)
        }
        assertEquals(SimpleLandingStatus.OUT_OF_FUEL, state.status)
        assertEquals(0f, state.fuelPercent, 0.001f)
        assertEquals(false, state.isThrusterActive)
    }

    @Test
    fun generateRandomTerrain_createsFlatPadAndVariedHeights() {
        val t1 = SimplePhysicsEngine.generateRandomTerrain(random = Random(11))
        val t2 = SimplePhysicsEngine.generateRandomTerrain(random = Random(99))
        assertTrue(t1.heightsMeters.size >= 12)
        assertTrue(t1.padEndNorm > t1.padStartNorm)
        assertEquals(t1.padAltitudeMeters, t1.heightAt((t1.padStartNorm + t1.padEndNorm) * 0.5f), 0.1f)
        assertNotEquals(t1.heightsMeters, t2.heightsMeters)
    }

    @Test
    fun activeThruster_emitsFlameExhaustParticlesThatFadeAfterShutdown() {
        var state = SimpleSpacecraftState(
            altitudeMeters = 100f,
            velocityY = 2f,
            isThrusterActive = true,
            fuelPercent = 80f,
            terrain = centeredPadTerrain(),
        )
        repeat(15) {
            state = engine.stepFrame(state, 1f / 60f, random = Random(7))
        }
        val exhaustCount = state.particles.count { it.kind == SimpleParticleKind.MAIN_EXHAUST }
        assertTrue("Active thruster should emit MAIN_EXHAUST particles", exhaustCount > 0)
        assertTrue("Particles should carry fuel ratio feedback", state.particles.any { it.fuelRatioAtSpawn in 0.7f..0.85f })

        // Shut off thruster and advance enough time for all particles to expire
        state = state.copy(isThrusterActive = false)
        repeat(90) {
            state = engine.stepFrame(state, 1f / 60f, random = Random(7))
        }
        assertTrue("All exhaust particles should fade away after thruster cutoff", state.particles.isEmpty())
    }

    @Test
    fun terrainCollision_spawnsExplosionShockwaveFireballsAndSparks() {
        var state = SimpleSpacecraftState(
            altitudeMeters = 0.5f,
            positionXNorm = 0.5f,
            velocityY = 12f,
            fuelPercent = 65f,
            safeLandingSpeed = 3.5f,
            terrain = centeredPadTerrain(),
        )
        repeat(10) {
            if (state.status == SimpleLandingStatus.DESCENDING) {
                state = engine.stepFrame(state, 1f / 60f, random = Random(42))
            }
        }
        assertEquals(SimpleLandingStatus.CRASHED, state.status)
        assertTrue(state.particles.any { it.kind == SimpleParticleKind.EXPLOSION_SHOCKWAVE })
        assertTrue(state.particles.any { it.kind == SimpleParticleKind.EXPLOSION_FIREBALL })
        assertTrue(state.particles.any { it.kind == SimpleParticleKind.EXPLOSION_SPARK })

        // Explosion particles continue animating after crash until they fade away
        repeat(120) {
            state = engine.stepFrame(state, 1f / 60f, random = Random(42))
        }
        assertTrue("Explosion particles should eventually dissipate", state.particles.isEmpty())
    }

    @Test
    fun pausedStatus_freezesPhysicsFuelAndParticles() {
        val pausedState = SimpleSpacecraftState(
            altitudeMeters = 64.5f,
            positionXNorm = 0.42f,
            velocityX = 0.08f,
            velocityY = 3.2f,
            fuelPercent = 55f,
            isThrusterActive = true,
            elapsedTimeSec = 4.2f,
            status = SimpleLandingStatus.PAUSED,
            terrain = centeredPadTerrain(),
        )
        var afterFrames = pausedState
        repeat(30) {
            afterFrames = engine.stepFrame(afterFrames, 1f / 60f)
        }
        assertEquals(pausedState, afterFrames)
    }
}
