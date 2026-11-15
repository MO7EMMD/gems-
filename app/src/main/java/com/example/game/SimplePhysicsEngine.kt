package com.example.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class SimpleLandingStatus {
    DESCENDING,
    PAUSED,
    SAFE_LANDING,
    CRASHED,
    OUT_OF_FUEL,
}

enum class SimpleParticleKind {
    MAIN_EXHAUST,
    SMOKE_EMBER,
    RCS_JET,
    GROUND_DUST,
    EXPLOSION_FIREBALL,
    EXPLOSION_SPARK,
    EXPLOSION_SHOCKWAVE,
}

data class SimpleExhaustParticle(
    val normX: Float,
    val altitudeMeters: Float,
    val vxNorm: Float,
    val vyMetersPerSec: Float, // Positive = upward, Negative = downward
    val lifeSec: Float,
    val maxLifeSec: Float,
    val radiusDp: Float,
    val fuelRatioAtSpawn: Float,
    val kind: SimpleParticleKind = SimpleParticleKind.MAIN_EXHAUST,
) {
    val ageFraction: Float get() = (lifeSec / maxLifeSec).coerceIn(0f, 1f)
}

data class RandomTerrainProfile(
    val heightsMeters: List<Float>,
    val padStartNorm: Float,
    val padEndNorm: Float,
    val padAltitudeMeters: Float,
) {
    fun heightAt(normX: Float): Float {
        if (heightsMeters.isEmpty()) return 0f
        if (heightsMeters.size == 1) return heightsMeters.first()
        val clamped = normX.coerceIn(0f, 1f)
        val scaled = clamped * (heightsMeters.size - 1)
        val idx = scaled.toInt().coerceIn(0, heightsMeters.size - 2)
        val frac = scaled - idx
        return heightsMeters[idx] + (heightsMeters[idx + 1] - heightsMeters[idx]) * frac
    }

    fun isInsidePad(normX: Float): Boolean = normX in padStartNorm..padEndNorm
}

data class SimpleSpacecraftState(
    val altitudeMeters: Float = 120f,
    val positionXNorm: Float = 0.5f,
    val velocityX: Float = 0f, // Normalized units per second
    val velocityY: Float = 0f, // Positive = downward descent speed (m/s)
    val gravity: Float = 1.62f, // m/s^2 (Moon default)
    val thrusterPower: Float = 4.8f, // m/s^2 upward acceleration at full burn
    val isThrusterActive: Boolean = false,
    val horizontalInput: Float = 0f, // -1 left, +1 right
    val fuelPercent: Float = 100f,
    val safeLandingSpeed: Float = 3.5f, // m/s max safe touchdown speed
    val elapsedTimeSec: Float = 0f,
    val status: SimpleLandingStatus = SimpleLandingStatus.DESCENDING,
    val failureReasonAr: String? = null,
    val terrain: RandomTerrainProfile = SimplePhysicsEngine.generateRandomTerrain(random = Random(101)),
    val particles: List<SimpleExhaustParticle> = emptyList(),
) {
    val altitudeAglMeters: Float
        get() = (altitudeMeters - terrain.heightAt(positionXNorm)).coerceAtLeast(0f)

    val horizontalSpeedMps: Float
        get() = velocityX * 140f
}

class SimplePhysicsEngine {

    companion object {
        private const val MAX_PARTICLES = 180

        /**
         * Generates random craggy terrain along the bottom of the screen with a flat
         * landing pad carved at a random horizontal position to challenge the player.
         */
        fun generateRandomTerrain(
            segmentCount: Int = 38,
            padWidthNorm: Float = 0.22f,
            maxHeightMeters: Float = 26f,
            random: Random = Random.Default,
        ): RandomTerrainProfile {
            val count = segmentCount.coerceAtLeast(12)
            val padHalf = (padWidthNorm.coerceIn(0.12f, 0.36f)) * 0.5f
            val padCenter = (0.24f + random.nextFloat() * 0.52f).coerceIn(padHalf + 0.08f, 1f - padHalf - 0.08f)
            val padStart = padCenter - padHalf
            val padEnd = padCenter + padHalf
            val padAltitude = (3f + random.nextFloat() * (maxHeightMeters * 0.35f))

            val heights = MutableList(count) { 0f }
            var currentHeight = padAltitude + 6f + random.nextFloat() * (maxHeightMeters * 0.5f)
            for (i in 0 until count) {
                val xNorm = i.toFloat() / (count - 1).toFloat()
                if (xNorm in padStart..padEnd) {
                    heights[i] = padAltitude
                } else {
                    val distToPad = min(abs(xNorm - padStart), abs(xNorm - padEnd))
                    val blend = min(1f, distToPad / 0.09f)
                    val delta = (random.nextFloat() - 0.48f) * 14f
                    currentHeight = (currentHeight + delta).coerceIn(2f, maxHeightMeters)
                    heights[i] = padAltitude + (currentHeight - padAltitude) * blend
                }
            }
            return RandomTerrainProfile(
                heightsMeters = heights,
                padStartNorm = padStart,
                padEndNorm = padEnd,
                padAltitudeMeters = padAltitude,
            )
        }
    }

    fun stepFrame(
        state: SimpleSpacecraftState,
        dtSeconds: Float,
        random: Random = Random.Default,
    ): SimpleSpacecraftState {
        if (state.status == SimpleLandingStatus.PAUSED) {
            return state
        }

        val dt = dtSeconds.coerceIn(0.001f, 0.05f)
        val agedParticles = updateParticles(state.particles, dt, state.terrain)

        if (state.status != SimpleLandingStatus.DESCENDING) {
            return if (agedParticles.size != state.particles.size || agedParticles.isNotEmpty()) {
                state.copy(particles = agedParticles)
            } else {
                state
            }
        }

        val hasFuel = state.fuelPercent > 0f
        val canBurnMain = state.isThrusterActive && hasFuel
        val activeHoriz = if (hasFuel) state.horizontalInput.coerceIn(-1f, 1f) else 0f

        val upwardAccel = if (canBurnMain) state.thrusterPower else 0f
        val netAccelY = state.gravity - upwardAccel

        // Velocity-Verlet vertical integration
        val nextVelocityY = state.velocityY + netAccelY * dt
        val avgVelocityY = (state.velocityY + nextVelocityY) * 0.5f
        val nextAltitude = state.altitudeMeters - avgVelocityY * dt

        // Horizontal RCS steering with gentle damping
        val nextVx = (state.velocityX + activeHoriz * 0.32f * dt) * (1f - dt * 0.65f)
        val nextX = (state.positionXNorm + nextVx * dt).coerceIn(0.06f, 0.94f)

        val mainBurn = if (canBurnMain) dt * 11f else 0f
        val rcsBurn = abs(activeHoriz) * dt * 2.5f
        val nextFuel = max(0f, state.fuelPercent - mainBurn - rcsBurn)

        val nextParticles = agedParticles.toMutableList()
        val fuelRatio = (nextFuel / 100f).coerceIn(0f, 1f)

        // Emit flame exhaust & ember particles when main thruster is burning fuel
        if (canBurnMain && nextParticles.size < MAX_PARTICLES) {
            val isSputtering = fuelRatio < 0.20f
            val flameCount = if (isSputtering) {
                if (random.nextFloat() < 0.65f) 2 else 1
            } else {
                4
            }
            repeat(flameCount) {
                if (nextParticles.size < MAX_PARTICLES) {
                    val spreadVx = (random.nextFloat() - 0.5f) * (if (isSputtering) 0.18f else 0.11f)
                    val exhaustSpeed = (22f + random.nextFloat() * 18f) * (0.72f + 0.28f * fuelRatio)
                    nextParticles.add(
                        SimpleExhaustParticle(
                            normX = (nextX + (random.nextFloat() - 0.5f) * 0.018f).coerceIn(0f, 1f),
                            altitudeMeters = nextAltitude + 1.4f,
                            vxNorm = nextVx * 0.45f + spreadVx,
                            vyMetersPerSec = -nextVelocityY - exhaustSpeed,
                            lifeSec = 0f,
                            maxLifeSec = (0.20f + random.nextFloat() * 0.22f) * (0.7f + 0.3f * fuelRatio),
                            radiusDp = (6.5f + random.nextFloat() * 6.5f) * (0.78f + 0.22f * fuelRatio),
                            fuelRatioAtSpawn = fuelRatio,
                            kind = SimpleParticleKind.MAIN_EXHAUST,
                        )
                    )
                }
            }

            // Trailing hot embers / smoke wisps
            if (nextParticles.size < MAX_PARTICLES && random.nextFloat() < 0.75f) {
                nextParticles.add(
                    SimpleExhaustParticle(
                        normX = (nextX + (random.nextFloat() - 0.5f) * 0.025f).coerceIn(0f, 1f),
                        altitudeMeters = nextAltitude + 0.6f,
                        vxNorm = nextVx * 0.3f + (random.nextFloat() - 0.5f) * 0.22f,
                        vyMetersPerSec = -nextVelocityY - (10f + random.nextFloat() * 10f),
                        lifeSec = 0f,
                        maxLifeSec = 0.42f + random.nextFloat() * 0.32f,
                        radiusDp = 4.5f + random.nextFloat() * 5.5f,
                        fuelRatioAtSpawn = fuelRatio,
                        kind = SimpleParticleKind.SMOKE_EMBER,
                    )
                )
            }

            // Surface regolith dust kick-up when exhaust approaches terrain
            val groundBelow = state.terrain.heightAt(nextX)
            val agl = nextAltitude - groundBelow
            if (agl in 0.2f..22f && nextParticles.size < MAX_PARTICLES) {
                val intensity = ((22f - agl) / 22f)
                if (random.nextFloat() < intensity * 0.85f) {
                    val dir = if (random.nextBoolean()) 1f else -1f
                    nextParticles.add(
                        SimpleExhaustParticle(
                            normX = nextX,
                            altitudeMeters = groundBelow + 0.5f,
                            vxNorm = dir * (0.14f + intensity * 0.26f),
                            vyMetersPerSec = 2.5f + random.nextFloat() * 5.5f * intensity,
                            lifeSec = 0f,
                            maxLifeSec = 0.40f + random.nextFloat() * 0.35f,
                            radiusDp = 4f + intensity * 5.5f,
                            fuelRatioAtSpawn = fuelRatio,
                            kind = SimpleParticleKind.GROUND_DUST,
                        )
                    )
                }
            }
        }

        // Emit horizontal RCS steering jet puffs
        if (abs(activeHoriz) > 0.1f && nextParticles.size < MAX_PARTICLES) {
            val jetSide = if (activeHoriz > 0f) -1f else 1f
            nextParticles.add(
                SimpleExhaustParticle(
                    normX = (nextX + jetSide * 0.032f).coerceIn(0f, 1f),
                    altitudeMeters = nextAltitude + 6.5f,
                    vxNorm = nextVx * 0.3f + jetSide * (0.20f + random.nextFloat() * 0.12f),
                    vyMetersPerSec = -nextVelocityY + (random.nextFloat() - 0.5f) * 3f,
                    lifeSec = 0f,
                    maxLifeSec = 0.22f + random.nextFloat() * 0.12f,
                    radiusDp = 3.8f + random.nextFloat() * 2.5f,
                    fuelRatioAtSpawn = fuelRatio,
                    kind = SimpleParticleKind.RCS_JET,
                )
            )
        }

        val groundAltitude = state.terrain.heightAt(nextX)

        if (nextAltitude <= groundAltitude) {
            val impactSpeed = abs(nextVelocityY)
            val onPad = state.terrain.isInsidePad(nextX)
            val outcome = when {
                !onPad -> SimpleLandingStatus.CRASHED
                impactSpeed > state.safeLandingSpeed -> SimpleLandingStatus.CRASHED
                else -> SimpleLandingStatus.SAFE_LANDING
            }
            val reason = when {
                outcome == SimpleLandingStatus.SAFE_LANDING -> null
                !onPad -> "ارتطمت المركبة بتضاريس وعرة خارج منصة الهبوط!"
                else -> "سرعة الملامسة (${String.format("%.2f", impactSpeed)} م/ث) تجاوزت الحد الآمن"
            }

            if (outcome == SimpleLandingStatus.CRASHED) {
                spawnExplosionParticles(
                    target = nextParticles,
                    impactNormX = nextX,
                    groundAltitude = groundAltitude,
                    impactSpeed = impactSpeed,
                    remainingFuelPercent = nextFuel,
                    random = random,
                )
            }

            return state.copy(
                altitudeMeters = groundAltitude,
                positionXNorm = nextX,
                velocityX = 0f,
                velocityY = impactSpeed,
                fuelPercent = nextFuel,
                elapsedTimeSec = state.elapsedTimeSec + dt,
                isThrusterActive = false,
                horizontalInput = 0f,
                status = outcome,
                failureReasonAr = reason,
                particles = nextParticles,
            )
        }

        if (nextFuel <= 0f) {
            // Final sputter puff on engine flameout
            repeat(5) {
                if (nextParticles.size < MAX_PARTICLES) {
                    nextParticles.add(
                        SimpleExhaustParticle(
                            normX = nextX,
                            altitudeMeters = nextAltitude + 1f,
                            vxNorm = nextVx * 0.3f + (random.nextFloat() - 0.5f) * 0.16f,
                            vyMetersPerSec = -nextVelocityY - (4f + random.nextFloat() * 6f),
                            lifeSec = 0f,
                            maxLifeSec = 0.45f + random.nextFloat() * 0.25f,
                            radiusDp = 5f + random.nextFloat() * 4f,
                            fuelRatioAtSpawn = 0f,
                            kind = SimpleParticleKind.SMOKE_EMBER,
                        )
                    )
                }
            }

            return state.copy(
                altitudeMeters = nextAltitude,
                positionXNorm = nextX,
                velocityX = nextVx,
                velocityY = nextVelocityY,
                fuelPercent = 0f,
                elapsedTimeSec = state.elapsedTimeSec + dt,
                isThrusterActive = false,
                horizontalInput = 0f,
                status = SimpleLandingStatus.OUT_OF_FUEL,
                failureReasonAr = "نفاد الوقود — انتهت اللعبة!",
                particles = nextParticles,
            )
        }

        return state.copy(
            altitudeMeters = nextAltitude,
            positionXNorm = nextX,
            velocityX = nextVx,
            velocityY = nextVelocityY,
            fuelPercent = nextFuel,
            elapsedTimeSec = state.elapsedTimeSec + dt,
            particles = nextParticles,
        )
    }

    fun spawnExplosionParticles(
        target: MutableList<SimpleExhaustParticle>,
        impactNormX: Float,
        groundAltitude: Float,
        impactSpeed: Float,
        remainingFuelPercent: Float,
        random: Random = Random.Default,
    ) {
        val intensity = (impactSpeed / 10f).coerceIn(0.75f, 1.85f)
        val fuelBoost = 1f + (remainingFuelPercent / 100f) * 0.45f
        val energy = intensity * fuelBoost

        // 1. Expanding radial shockwave ring
        target.add(
            SimpleExhaustParticle(
                normX = impactNormX,
                altitudeMeters = groundAltitude + 1.5f,
                vxNorm = 0f,
                vyMetersPerSec = 0f,
                lifeSec = 0f,
                maxLifeSec = 0.55f,
                radiusDp = 18f * energy,
                fuelRatioAtSpawn = 1f,
                kind = SimpleParticleKind.EXPLOSION_SHOCKWAVE,
            )
        )

        // 2. Core explosion fireball clouds bursting outward
        repeat(18) {
            if (target.size < MAX_PARTICLES) {
                val angle = random.nextFloat() * kotlin.math.PI.toFloat()
                val speed = (0.10f + random.nextFloat() * 0.34f) * energy
                target.add(
                    SimpleExhaustParticle(
                        normX = (impactNormX + (random.nextFloat() - 0.5f) * 0.025f).coerceIn(0f, 1f),
                        altitudeMeters = groundAltitude + 1.2f,
                        vxNorm = kotlin.math.cos(angle) * speed * (if (random.nextBoolean()) 1f else -1f),
                        vyMetersPerSec = kotlin.math.sin(angle) * (10f + random.nextFloat() * 18f) * energy,
                        lifeSec = 0f,
                        maxLifeSec = 0.48f + random.nextFloat() * 0.42f,
                        radiusDp = (9f + random.nextFloat() * 11f) * energy.coerceAtMost(1.5f),
                        fuelRatioAtSpawn = 1f,
                        kind = SimpleParticleKind.EXPLOSION_FIREBALL,
                    )
                )
            }
        }

        // 3. High-velocity ballistic debris sparks obeying gravity & terrain bounce
        repeat(28) {
            if (target.size < MAX_PARTICLES) {
                val angle = 0.15f + random.nextFloat() * (kotlin.math.PI.toFloat() - 0.3f)
                val horizDir = kotlin.math.cos(angle)
                val vertDir = kotlin.math.sin(angle)
                val launchSpeed = (16f + random.nextFloat() * 28f) * energy
                target.add(
                    SimpleExhaustParticle(
                        normX = impactNormX,
                        altitudeMeters = groundAltitude + 1.5f,
                        vxNorm = horizDir * (0.28f + random.nextFloat() * 0.55f) * energy,
                        vyMetersPerSec = vertDir * launchSpeed,
                        lifeSec = 0f,
                        maxLifeSec = 0.75f + random.nextFloat() * 0.65f,
                        radiusDp = 2.6f + random.nextFloat() * 3.0f,
                        fuelRatioAtSpawn = 1f,
                        kind = SimpleParticleKind.EXPLOSION_SPARK,
                    )
                )
            }
        }

        // 4. Rising crater smoke & embers
        repeat(14) {
            if (target.size < MAX_PARTICLES) {
                target.add(
                    SimpleExhaustParticle(
                        normX = (impactNormX + (random.nextFloat() - 0.5f) * 0.05f).coerceIn(0f, 1f),
                        altitudeMeters = groundAltitude + 1f,
                        vxNorm = (random.nextFloat() - 0.5f) * 0.24f,
                        vyMetersPerSec = 5f + random.nextFloat() * 11f,
                        lifeSec = 0f,
                        maxLifeSec = 0.85f + random.nextFloat() * 0.65f,
                        radiusDp = 7f + random.nextFloat() * 8f,
                        fuelRatioAtSpawn = 0f,
                        kind = SimpleParticleKind.SMOKE_EMBER,
                    )
                )
            }
        }
    }

    private fun updateParticles(
        particles: List<SimpleExhaustParticle>,
        dt: Float,
        terrain: RandomTerrainProfile,
    ): List<SimpleExhaustParticle> {
        if (particles.isEmpty()) return emptyList()
        val updated = ArrayList<SimpleExhaustParticle>(particles.size)
        for (p in particles) {
            val nextLife = p.lifeSec + dt
            if (nextLife < p.maxLifeSec) {
                val drag = when (p.kind) {
                    SimpleParticleKind.SMOKE_EMBER -> 1.8f
                    SimpleParticleKind.EXPLOSION_FIREBALL -> 2.8f
                    SimpleParticleKind.EXPLOSION_SPARK -> 0.55f
                    else -> 0.9f
                }
                var vx = p.vxNorm * (1f - drag * dt)
                var vy = when (p.kind) {
                    SimpleParticleKind.GROUND_DUST -> p.vyMetersPerSec - 4.5f * dt
                    SimpleParticleKind.SMOKE_EMBER -> p.vyMetersPerSec * (1f - 1.5f * dt)
                    SimpleParticleKind.EXPLOSION_FIREBALL -> p.vyMetersPerSec * (1f - 2.6f * dt)
                    SimpleParticleKind.EXPLOSION_SPARK -> p.vyMetersPerSec - 16.5f * dt
                    SimpleParticleKind.EXPLOSION_SHOCKWAVE -> 0f
                    else -> p.vyMetersPerSec
                }
                val nx = (p.normX + vx * dt).coerceIn(0f, 1f)
                var nAlt = p.altitudeMeters + vy * dt
                val ground = terrain.heightAt(nx)
                if (nAlt < ground && p.kind == SimpleParticleKind.MAIN_EXHAUST) {
                    // Deflect flame plume sideways along terrain on impact
                    nAlt = ground + 0.3f
                    vx += if (vx >= 0f) 0.22f else -0.22f
                    vy = 3.5f
                } else if (nAlt <= ground && p.kind == SimpleParticleKind.EXPLOSION_SPARK) {
                    // Ballistic spark bounce off terrain surface
                    nAlt = ground + 0.25f
                    vy = abs(vy) * 0.42f
                    vx *= 0.72f
                }
                updated.add(
                    p.copy(
                        normX = nx,
                        altitudeMeters = nAlt,
                        vxNorm = vx,
                        vyMetersPerSec = vy,
                        lifeSec = nextLife,
                    )
                )
            }
        }
        return updated
    }
}
