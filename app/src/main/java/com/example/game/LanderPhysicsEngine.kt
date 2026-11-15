package com.example.game

import com.example.data.model.MissionDefinition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class FlightPhase {
    FLYING,
    PAUSED,
    LANDED_SAFE,
    CRASHED,
}

enum class ParticleKind {
    EXHAUST_FLAME,
    RCS_PUFF,
    REGOLITH_DUST,
    DEBRIS_SPARK,
}

data class SimParticle(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val maxLife: Float,
    val size: Float,
    val kind: ParticleKind,
)

data class StarPoint(
    val normX: Float,
    val normY: Float,
    val radius: Float,
    val alpha: Float,
)

data class TerrainPoint(
    val x: Float,
    val y: Float,
)

data class LanderSimState(
    val mission: MissionDefinition,
    val phase: FlightPhase = FlightPhase.FLYING,
    val x: Float = 0f,
    val y: Float = 240f,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val angleRad: Float = 0f,
    val angularVelRad: Float = 0f,
    val targetThrottle: Float = 0f,
    val actualThrottle: Float = 0f,
    val rcsCommand: Float = 0f,
    val sasEnabled: Boolean = true,
    val fuelKg: Float = 680f,
    val currentWindSpeed: Float = 0f,
    val altitudeAgl: Float = 220f,
    val gForce: Float = 0f,
    val elapsedTimeSec: Float = 0f,
    val padCenterX: Float = 0f,
    val padElevationY: Float = 18f,
    val gearCompression: Float = 0f,
    val terrain: List<TerrainPoint> = emptyList(),
    val particles: List<SimParticle> = emptyList(),
    val stars: List<StarPoint> = emptyList(),
    val touchdownScore: Int = 0,
    val touchdownStars: Int = 0,
    val failureReasonAr: String? = null,
    val padAccuracyPercent: Float = 0f,
) {
    val angleDeg: Float get() = (angleRad * 180f / PI.toFloat())
    val fuelPercent: Float get() = ((fuelKg / mission.initialFuelKg) * 100f).coerceIn(0f, 100f)
    val verticalSpeedAbs: Float get() = abs(vy)
    val horizontalSpeedAbs: Float get() = abs(vx)
    val totalMassKg: Float get() = mission.dryMassKg + fuelKg
}

class LanderPhysicsEngine {

    companion object {
        const val WORLD_HALF_WIDTH = 220f
        const val LANDER_CG_HEIGHT = 2.1f
        const val LANDER_LEG_HALF_SPAN = 2.4f
        private const val RAD_TO_DEG = 57.29578f
    }

    fun createInitialState(mission: MissionDefinition, sasEnabled: Boolean = true): LanderSimState {
        val rng = Random(mission.seed)
        val padCenter = when (mission.tier) {
            1 -> 0f
            2 -> 18f
            3 -> -16f
            else -> -12f
        }
        val padElev = 18f
        val terrain = generateTerrain(mission, padCenter, padElev, rng)
        val stars = List(95) {
            StarPoint(
                normX = rng.nextFloat(),
                normY = rng.nextFloat() * 0.78f,
                radius = 0.8f + rng.nextFloat() * 1.8f,
                alpha = 0.25f + rng.nextFloat() * 0.7f,
            )
        }
        val startY = padElev + mission.startAltitudeMeters
        val startX = padCenter + mission.startHorizontalOffset
        val initialAgl = computeAltitudeAgl(startX, startY, 0f, terrain)

        return LanderSimState(
            mission = mission,
            phase = FlightPhase.FLYING,
            x = startX,
            y = startY,
            vx = mission.startVelocityX,
            vy = mission.startVelocityY,
            angleRad = 0f,
            angularVelRad = 0f,
            targetThrottle = 0f,
            actualThrottle = 0f,
            rcsCommand = 0f,
            sasEnabled = sasEnabled,
            fuelKg = mission.initialFuelKg,
            currentWindSpeed = mission.baseWindSpeed,
            altitudeAgl = initialAgl,
            gForce = 0f,
            elapsedTimeSec = 0f,
            padCenterX = padCenter,
            padElevationY = padElev,
            gearCompression = 0f,
            terrain = terrain,
            particles = emptyList(),
            stars = stars,
        )
    }

    private fun generateTerrain(
        mission: MissionDefinition,
        padCenter: Float,
        padElevation: Float,
        rng: Random,
    ): List<TerrainPoint> {
        val count = 161
        val step = (WORLD_HALF_WIDTH * 2f) / (count - 1)
        val halfPad = mission.padWidthMeters * 0.5f
        val roughness = when (mission.tier) {
            1 -> 1.0f
            2 -> 1.2f
            3 -> 1.65f
            else -> 1.15f
        }
        val phase1 = rng.nextFloat() * 6.28f
        val phase2 = rng.nextFloat() * 6.28f

        return List(count) { i ->
            val wx = -WORLD_HALF_WIDTH + i * step
            val distFromPad = abs(wx - padCenter)
            val wy = if (distFromPad <= halfPad) {
                padElevation
            } else {
                val edgeDist = distFromPad - halfPad
                val blend = min(1f, edgeDist / 22f)
                val wave = sin(wx * 0.045f + phase1) * 14f +
                    cos(wx * 0.11f + phase2) * 8f +
                    sin(wx * 0.27f) * 3.5f
                val ridgeBoost = if (mission.tier == 3 && edgeDist in 6f..42f) 18f else 0f
                val boundaryWall = if (abs(wx) > WORLD_HALF_WIDTH - 24f) {
                    (abs(wx) - (WORLD_HALF_WIDTH - 24f)) * 1.4f
                } else 0f
                val rawH = (padElevation + (wave * roughness + ridgeBoost + boundaryWall) * blend)
                max(4f, rawH)
            }
            TerrainPoint(wx, wy)
        }
    }

    fun step(
        state: LanderSimState,
        dtSeconds: Float,
        rcsInput: Float,
        tiltAngleTargetRad: Float?,
    ): LanderSimState {
        if (state.phase != FlightPhase.FLYING) {
            return if (state.particles.isNotEmpty()) {
                state.copy(particles = updateParticles(state.particles, dtSeconds, state.mission.gravity, state.terrain))
            } else {
                state
            }
        }

        val dt = dtSeconds.coerceIn(0.004f, 0.033f)
        val mission = state.mission
        val nextTime = state.elapsedTimeSec + dt

        // 1. Engine throttle spooling & propellant check
        val requestedThrottle = if (state.fuelKg > 0.05f) state.targetThrottle.coerceIn(0f, 1f) else 0f
        val actualThrottle = if (state.fuelKg > 0.05f) {
            val delta = requestedThrottle - state.actualThrottle
            (state.actualThrottle + delta * min(1f, dt * 7.0f)).coerceIn(0f, 1f)
        } else {
            0f
        }

        val fuelBurn = actualThrottle * mission.specificImpulseBurnRate * dt
        val nextFuel = max(0f, state.fuelKg - fuelBurn)
        val totalMass = mission.dryMassKg + nextFuel

        // 2. Wind field & aerodynamic drag
        val windX = if (mission.atmosphericDensity > 0f) {
            mission.baseWindSpeed +
                mission.windGustAmplitude * sin(nextTime * 0.85f + state.y * 0.025f) +
                (mission.windGustAmplitude * 0.35f) * cos(nextTime * 1.9f)
        } else {
            0f
        }

        // 3. Attitude dynamics (RCS + Gyroscopic SAS + optional Tilt Steering)
        var angularAccel = rcsInput.coerceIn(-1f, 1f) * 2.65f
        if (tiltAngleTargetRad != null && abs(rcsInput) < 0.05f) {
            val angleErr = (tiltAngleTargetRad.coerceIn(-0.7f, 0.7f) - state.angleRad)
            angularAccel += angleErr * 5.2f - state.angularVelRad * 3.8f
        } else if (state.sasEnabled && abs(rcsInput) < 0.05f) {
            // Gyroscopic reaction wheel rate damping + gentle attitude hold near vertical
            angularAccel += -state.angularVelRad * 4.8f
            if (abs(state.angleRad) < 0.35f) {
                angularAccel += -state.angleRad * 1.15f
            }
        } else {
            // Subtle natural rotational damping
            angularAccel += -state.angularVelRad * 0.18f
        }

        var nextAngularVel = (state.angularVelRad + angularAccel * dt).coerceIn(-3.5f, 3.5f)
        var nextAngle = state.angleRad + nextAngularVel * dt
        while (nextAngle > PI.toFloat()) nextAngle -= 2f * PI.toFloat()
        while (nextAngle < -PI.toFloat()) nextAngle += 2f * PI.toFloat()

        // 4. Translational forces (Gravity + Main Engine Thrust + Aero Drag)
        val thrustForce = actualThrottle * mission.maxThrustNewtons
        val fxThrust = thrustForce * sin(nextAngle)
        val fyThrust = thrustForce * cos(nextAngle)

        val relVx = state.vx - windX
        val relVy = state.vy
        val relSpeed = hypot(relVx, relVy)
        val fxDrag = -0.5f * mission.atmosphericDensity * 20f * relSpeed * relVx
        val fyDrag = -0.5f * mission.atmosphericDensity * 24f * relSpeed * relVy

        val fxTotal = fxThrust + fxDrag
        val fyTotal = fyThrust + fyDrag - (totalMass * mission.gravity)

        val ax = fxTotal / totalMass
        val ay = fyTotal / totalMass

        // Perceived G-force on structure (excluding gravity freefall, i.e., proper acceleration)
        val properAccel = hypot(fxThrust + fxDrag, fyThrust + fyDrag) / totalMass
        val gForce = properAccel / 9.81f

        var nextVx = state.vx + ax * dt
        var nextVy = state.vy + ay * dt
        var nextX = state.x + nextVx * dt
        var nextY = state.y + nextVy * dt

        // Soft horizontal arena bounds bounce/clamp
        if (nextX < -WORLD_HALF_WIDTH + 6f) {
            nextX = -WORLD_HALF_WIDTH + 6f
            nextVx = abs(nextVx) * 0.25f
        } else if (nextX > WORLD_HALF_WIDTH - 6f) {
            nextX = WORLD_HALF_WIDTH - 6f
            nextVx = -abs(nextVx) * 0.25f
        }

        // 5. Particles generation (exhaust, RCS puffs, surface dust kickup)
        val updatedParticles = updateParticles(state.particles, dt, mission.gravity, state.terrain).toMutableList()
        val rng = Random((nextTime * 10000f).toInt())

        if (actualThrottle > 0.04f && updatedParticles.size < 160) {
            val nozzleX = nextX - sin(nextAngle) * 1.4f
            val nozzleY = nextY - cos(nextAngle) * 1.4f
            val jetSpeed = 16f + actualThrottle * 24f
            val count = if (actualThrottle > 0.55f) 3 else 1
            repeat(count) {
                val spread = (rng.nextFloat() - 0.5f) * 0.24f
                val ejectAngle = nextAngle + spread
                updatedParticles.add(
                    SimParticle(
                        x = nozzleX + (rng.nextFloat() - 0.5f) * 0.4f,
                        y = nozzleY + (rng.nextFloat() - 0.5f) * 0.4f,
                        vx = nextVx - sin(ejectAngle) * jetSpeed,
                        vy = nextVy - cos(ejectAngle) * jetSpeed,
                        life = 0f,
                        maxLife = 0.20f + actualThrottle * 0.24f,
                        size = 1.4f + actualThrottle * 2.2f,
                        kind = ParticleKind.EXHAUST_FLAME,
                    )
                )
            }
        }

        if (abs(rcsInput) > 0.1f && updatedParticles.size < 160) {
            val sideSign = if (rcsInput > 0f) -1f else 1f
            val rcsX = nextX + cos(nextAngle) * 1.6f * sideSign
            val rcsY = nextY - sin(nextAngle) * 1.6f * sideSign + cos(nextAngle) * 1.1f
            updatedParticles.add(
                SimParticle(
                    x = rcsX,
                    y = rcsY,
                    vx = nextVx + cos(nextAngle) * sideSign * 9f + (rng.nextFloat() - 0.5f) * 2f,
                    vy = nextVy - sin(nextAngle) * sideSign * 9f,
                    life = 0f,
                    maxLife = 0.26f,
                    size = 1.2f,
                    kind = ParticleKind.RCS_PUFF,
                )
            )
        }

        // Ground proximity dust plume
        val agl = computeAltitudeAgl(nextX, nextY, nextAngle, state.terrain)
        if (actualThrottle > 0.12f && agl < 28f && agl > 0.1f && updatedParticles.size < 180) {
            val intensity = ((28f - agl) / 28f) * actualThrottle
            if (rng.nextFloat() < intensity * 0.9f) {
                val dir = if (rng.nextBoolean()) 1f else -1f
                val groundY = terrainHeightAt(nextX, state.terrain)
                updatedParticles.add(
                    SimParticle(
                        x = nextX + (rng.nextFloat() - 0.5f) * 2.5f,
                        y = groundY + 0.4f,
                        vx = dir * (6f + intensity * 14f) + windX * 0.4f,
                        vy = 1.5f + rng.nextFloat() * 4.5f * intensity,
                        life = 0f,
                        maxLife = 0.45f + rng.nextFloat() * 0.4f,
                        size = 1.6f + intensity * 2.6f,
                        kind = ParticleKind.REGOLITH_DUST,
                    )
                )
            }
        }

        // 6. Touchdown & Collision detection on both footpads + body hull
        val cosA = cos(nextAngle)
        val sinA = sin(nextAngle)
        val leftFootX = nextX - LANDER_LEG_HALF_SPAN * cosA + LANDER_CG_HEIGHT * sinA
        val leftFootY = nextY + LANDER_LEG_HALF_SPAN * sinA - LANDER_CG_HEIGHT * cosA
        val rightFootX = nextX + LANDER_LEG_HALF_SPAN * cosA + LANDER_CG_HEIGHT * sinA
        val rightFootY = nextY - LANDER_LEG_HALF_SPAN * sinA - LANDER_CG_HEIGHT * cosA
        val centerBottomY = nextY - (LANDER_CG_HEIGHT - 0.3f) * cosA

        val leftGround = terrainHeightAt(leftFootX, state.terrain)
        val rightGround = terrainHeightAt(rightFootX, state.terrain)
        val centerGround = terrainHeightAt(nextX, state.terrain)

        val hitLeft = leftFootY <= leftGround
        val hitRight = rightFootY <= rightGround
        val hitCenter = centerBottomY <= centerGround

        if (hitLeft || hitRight || hitCenter) {
            val angleDegAbs = abs(nextAngle * RAD_TO_DEG)
            val vSpeed = abs(nextVy)
            val hSpeed = abs(nextVx)
            val padHalf = mission.padWidthMeters * 0.5f
            val onPad = abs(leftFootX - state.padCenterX) <= padHalf + 0.6f &&
                abs(rightFootX - state.padCenterX) <= padHalf + 0.6f

            val failureReason: String? = when {
                !onPad -> "هبطت المركبة خارج منصة الهبوط المخصصة فوق تضاريس وعرة"
                angleDegAbs > mission.safeTiltDeg -> "زاوية ميلان المركبة (${angleDegAbs.toInt()}°) تجاوزت حد الأمان (${mission.safeTiltDeg.toInt()}°)"
                vSpeed > mission.safeVerticalSpeed -> "سرعة الهبوط العمودية (${String.format("%.1f", vSpeed)} م/ث) مرتفعة جداً"
                hSpeed > mission.safeHorizontalSpeed -> "الانجراف الأفقي (${String.format("%.1f", hSpeed)} م/ث) كسر أرجل الهبوط"
                else -> null
            }

            val snappedY = max(leftGround, rightGround) + LANDER_CG_HEIGHT - 0.15f
            val padAccuracy = (1f - (abs(nextX - state.padCenterX) / padHalf)).coerceIn(0f, 1f) * 100f

            if (failureReason == null) {
                val fuelBonus = ((nextFuel / mission.initialFuelKg) * 1200f).toInt()
                val softnessBonus = ((1f - (vSpeed / mission.safeVerticalSpeed)).coerceIn(0f, 1f) * 700f).toInt()
                val centerBonus = ((padAccuracy / 100f) * 600f).toInt()
                val totalScore = (1000 * mission.tier + fuelBonus + softnessBonus + centerBonus).coerceIn(100, 100000)

                val stars = when {
                    vSpeed <= mission.safeVerticalSpeed * 0.5f &&
                        padAccuracy >= 60f &&
                        (nextFuel / mission.initialFuelKg) >= 0.25f -> 3
                    vSpeed <= mission.safeVerticalSpeed * 0.8f && padAccuracy >= 30f -> 2
                    else -> 1
                }

                return state.copy(
                    phase = FlightPhase.LANDED_SAFE,
                    x = nextX,
                    y = snappedY,
                    vx = 0f,
                    vy = 0f,
                    angleRad = 0f,
                    angularVelRad = 0f,
                    actualThrottle = 0f,
                    targetThrottle = 0f,
                    rcsCommand = 0f,
                    fuelKg = nextFuel,
                    currentWindSpeed = windX,
                    altitudeAgl = 0f,
                    gForce = mission.gravity / 9.81f,
                    elapsedTimeSec = nextTime,
                    gearCompression = 1f,
                    particles = updatedParticles,
                    touchdownScore = totalScore,
                    touchdownStars = stars,
                    failureReasonAr = null,
                    padAccuracyPercent = padAccuracy,
                )
            } else {
                // Spawn crash sparks/debris
                repeat(28) {
                    val sparkAngle = rng.nextFloat() * PI.toFloat()
                    val speed = 6f + rng.nextFloat() * 22f
                    updatedParticles.add(
                        SimParticle(
                            x = nextX,
                            y = snappedY - 0.5f,
                            vx = cos(sparkAngle) * speed,
                            vy = sin(sparkAngle) * speed,
                            life = 0f,
                            maxLife = 0.7f + rng.nextFloat() * 0.6f,
                            size = 1.5f + rng.nextFloat() * 2.2f,
                            kind = ParticleKind.DEBRIS_SPARK,
                        )
                    )
                }

                return state.copy(
                    phase = FlightPhase.CRASHED,
                    x = nextX,
                    y = snappedY - 0.6f,
                    vx = nextVx,
                    vy = nextVy,
                    angleRad = nextAngle,
                    angularVelRad = 0f,
                    actualThrottle = 0f,
                    targetThrottle = 0f,
                    rcsCommand = 0f,
                    fuelKg = nextFuel,
                    currentWindSpeed = windX,
                    altitudeAgl = 0f,
                    gForce = gForce,
                    elapsedTimeSec = nextTime,
                    gearCompression = 1f,
                    particles = updatedParticles,
                    touchdownScore = 0,
                    touchdownStars = 0,
                    failureReasonAr = failureReason,
                    padAccuracyPercent = padAccuracy,
                )
            }
        }

        return state.copy(
            x = nextX,
            y = nextY,
            vx = nextVx,
            vy = nextVy,
            angleRad = nextAngle,
            angularVelRad = nextAngularVel,
            actualThrottle = actualThrottle,
            rcsCommand = rcsInput,
            fuelKg = nextFuel,
            currentWindSpeed = windX,
            altitudeAgl = agl,
            gForce = gForce,
            elapsedTimeSec = nextTime,
            gearCompression = max(0f, state.gearCompression - dt * 3f),
            particles = updatedParticles,
        )
    }

    private fun updateParticles(
        particles: List<SimParticle>,
        dt: Float,
        gravity: Float,
        terrain: List<TerrainPoint>,
    ): List<SimParticle> {
        val result = ArrayList<SimParticle>(particles.size)
        for (p in particles) {
            val nextLife = p.life + dt
            if (nextLife < p.maxLife) {
                val gy = when (p.kind) {
                    ParticleKind.DEBRIS_SPARK -> -gravity * 1.4f
                    ParticleKind.REGOLITH_DUST -> -gravity * 0.35f
                    else -> 0f
                }
                val nextVx = p.vx * (1f - dt * 1.1f)
                val nextVy = p.vy + gy * dt
                val nx = p.x + nextVx * dt
                val ny = p.y + nextVy * dt
                val gH = terrainHeightAt(nx, terrain)
                val clampedY = if (ny < gH && p.kind == ParticleKind.EXHAUST_FLAME) gH + 0.1f else ny
                val deflectedVx = if (ny < gH && p.kind == ParticleKind.EXHAUST_FLAME) {
                    nextVx + (if (nextVx >= 0f) 6f else -6f)
                } else {
                    nextVx
                }
                result.add(
                    p.copy(
                        x = nx,
                        y = clampedY,
                        vx = deflectedVx,
                        vy = if (ny < gH && p.kind == ParticleKind.EXHAUST_FLAME) 1.8f else nextVy,
                        life = nextLife,
                    )
                )
            }
        }
        return result
    }

    fun terrainHeightAt(worldX: Float, terrain: List<TerrainPoint>): Float {
        if (terrain.isEmpty()) return 0f
        if (worldX <= terrain.first().x) return terrain.first().y
        if (worldX >= terrain.last().x) return terrain.last().y

        val totalWidth = terrain.last().x - terrain.first().x
        val step = totalWidth / (terrain.size - 1)
        val idx = ((worldX - terrain.first().x) / step).toInt().coerceIn(0, terrain.size - 2)
        val p0 = terrain[idx]
        val p1 = terrain[idx + 1]
        val t = ((worldX - p0.x) / (p1.x - p0.x)).coerceIn(0f, 1f)
        return p0.y + (p1.y - p0.y) * t
    }

    private fun computeAltitudeAgl(
        x: Float,
        y: Float,
        angleRad: Float,
        terrain: List<TerrainPoint>,
    ): Float {
        val ground = terrainHeightAt(x, terrain)
        val lowestY = y - LANDER_CG_HEIGHT * cos(angleRad)
        return max(0f, lowestY - ground)
    }
}
