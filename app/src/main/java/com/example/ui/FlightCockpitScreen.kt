package com.example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.MissionCatalog
import com.example.data.model.MissionDefinition
import com.example.game.FlightPhase
import com.example.game.LanderSimState
import com.example.game.ParticleKind
import com.example.ui.components.BrandCopyrightBadge
import com.example.ui.components.TelemetryHudData
import com.example.ui.components.TelemetryHudOverlay
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SpaceCarbon
import com.example.ui.theme.SpaceObsidian
import com.example.ui.theme.SpaceVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TelemetryMonoFontFamily
import com.example.ui.theme.ThrusterAmber
import com.example.ui.theme.ThrusterOrange
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FlightCockpitScreen(
    flightViewModel: FlightViewModel,
    tiltControlEnabled: Boolean,
    unlockedTier: Int,
    onExitToBriefing: () -> Unit,
    onSwitchMission: (MissionDefinition) -> Unit,
) {
    val simState by flightViewModel.simState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    BackHandler {
        if (simState.phase == FlightPhase.FLYING) {
            flightViewModel.togglePause()
        } else {
            onExitToBriefing()
        }
    }

    // Haptic feedback on touchdown or crash
    LaunchedEffect(simState.phase) {
        when (simState.phase) {
            FlightPhase.LANDED_SAFE -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            FlightPhase.CRASHED -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            else -> {}
        }
    }

    // Optional real accelerometer tilt steering
    DisposableEffect(tiltControlEnabled, simState.phase) {
        if (!tiltControlEnabled || simState.phase != FlightPhase.FLYING) {
            flightViewModel.setTiltSteeringAngle(null)
            return@DisposableEffect onDispose {}
        }
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensorManager == null || sensor == null) {
            flightViewModel.setTiltSteeringAngle(null)
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Tilt phone left/right -> event.values[0] in [-9.8, +9.8]
                val rollNorm = (-event.values[0] / 6.5f).coerceIn(-1f, 1f)
                flightViewModel.setTiltSteeringAngle(rollNorm * 0.55f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sensorManager.unregisterListener(listener)
            flightViewModel.setTiltSteeringAngle(null)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceVoid)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.type == KeyEventType.KeyDown
                val isUp = keyEvent.type == KeyEventType.KeyUp
                when (keyEvent.key) {
                    Key.W, Key.DirectionUp, Key.Spacebar -> {
                        if (isDown) flightViewModel.setTargetThrottle(1f)
                        else if (isUp) flightViewModel.setTargetThrottle(0f)
                        true
                    }
                    Key.S, Key.DirectionDown -> {
                        if (isDown) flightViewModel.setTargetThrottle(0f)
                        true
                    }
                    Key.E -> {
                        if (isDown) flightViewModel.setTargetThrottle((simState.targetThrottle + 0.15f).coerceIn(0f, 1f))
                        true
                    }
                    Key.Q -> {
                        if (isDown) flightViewModel.setTargetThrottle((simState.targetThrottle - 0.15f).coerceIn(0f, 1f))
                        true
                    }
                    Key.A, Key.DirectionLeft -> {
                        if (isDown) flightViewModel.setRcsInput(-1f)
                        else if (isUp) flightViewModel.setRcsInput(0f)
                        true
                    }
                    Key.D, Key.DirectionRight -> {
                        if (isDown) flightViewModel.setRcsInput(1f)
                        else if (isUp) flightViewModel.setRcsInput(0f)
                        true
                    }
                    Key.T -> {
                        if (isDown) flightViewModel.toggleSas()
                        true
                    }
                    Key.P, Key.Escape -> {
                        if (isDown) flightViewModel.togglePause()
                        true
                    }
                    Key.R -> {
                        if (isDown && simState.phase != FlightPhase.FLYING) {
                            flightViewModel.restartMission()
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        val isWideScreen = maxWidth >= 660.dp
        // 1. Full-Bleed Physics Simulation Canvas
        LanderSimulationCanvas(
            state = simState,
            modifier = Modifier.fillMaxSize(),
        )

        // 2. Cockpit HUD & Touch Controls inside safe insets
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Telemetry Strip + Copyright Watermark
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CockpitTelemetryHud(
                    state = simState,
                    onToggleSas = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        flightViewModel.toggleSas()
                    },
                    onTogglePause = { flightViewModel.togglePause() },
                    onExit = onExitToBriefing,
                )
                BrandCopyrightBadge(
                    compact = true,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            // Bottom Ergonomic Touch Controls (visible while flying)
            if (simState.phase == FlightPhase.FLYING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Left: RCS Rotation Thrusters
                    RcsControlCluster(
                        rcsCommand = simState.rcsCommand,
                        tiltEnabled = tiltControlEnabled,
                        onRcsChange = { input ->
                            if (abs(input) > 0.1f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            flightViewModel.setRcsInput(input)
                        },
                    )

                    if (isWideScreen) {
                        Surface(
                            color = SpaceObsidian.copy(alpha = 0.82f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.border(1.dp, CockpitBorder, RoundedCornerShape(12.dp)),
                        ) {
                            Text(
                                text = "لوحة المفاتيح: W/Space دفع • A/D تدوير • Q/E تدريجي • T تثبيت • P إيقاف",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }

                    // Right: Analog Throttle Lever + Quick Cut/Max
                    ThrottleControlColumn(
                        targetThrottle = simState.targetThrottle,
                        actualThrottle = simState.actualThrottle,
                        hasFuel = simState.fuelKg > 0.05f,
                        onThrottleChange = { flightViewModel.setTargetThrottle(it) },
                    )
                }
            }
        }

        // 3. Pause Overlay
        if (simState.phase == FlightPhase.PAUSED) {
            PauseOverlay(
                missionName = simState.mission.nameAr,
                onResume = { flightViewModel.togglePause() },
                onRestart = { flightViewModel.restartMission() },
                onExit = onExitToBriefing,
            )
        }

        // 4. Touchdown / Crash Debrief Modal
        AnimatedVisibility(
            visible = simState.phase == FlightPhase.LANDED_SAFE || simState.phase == FlightPhase.CRASHED,
            enter = fadeIn() + scaleIn(
                initialScale = 0.9f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            ),
            modifier = Modifier.align(Alignment.Center),
        ) {
            val nextMission = MissionCatalog.missions.firstOrNull { it.tier == simState.mission.tier + 1 }
            val nextUnlocked = nextMission != null &&
                (simState.phase == FlightPhase.LANDED_SAFE || nextMission.tier <= unlockedTier)

            FlightDebriefOverlay(
                state = simState,
                nextMission = if (nextUnlocked) nextMission else null,
                onRetry = { flightViewModel.restartMission() },
                onNextMission = { if (nextMission != null) onSwitchMission(nextMission) },
                onExit = onExitToBriefing,
            )
        }
    }
}

@Composable
private fun LanderSimulationCanvas(
    state: LanderSimState,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mission = state.mission

        // Sky gradient
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(mission.skyTopColor, mission.skyBottomColor)
            ),
            size = size,
        )

        // Stars
        val starVisibility = if (mission.tier == 4) 0.35f else 1f
        for (star in state.stars) {
            drawCircle(
                color = Color.White.copy(alpha = star.alpha * starVisibility),
                radius = star.radius,
                center = Offset(star.normX * w, star.normY * h),
            )
        }

        // Distant celestial body in background sky
        drawCelestialBackdrop(mission, w, h)

        // Dynamic camera: zoom in smoothly as lander approaches ground (<115m AGL)
        val proximityT = (1f - (state.altitudeAgl / 130f)).coerceIn(0f, 1f)
        val basePxPerMeter = (w / 185f).coerceIn(2.6f, 6.2f)
        val pxPerMeter = basePxPerMeter * (1f + proximityT * 1.15f)

        val camX = (state.x * 0.85f + state.padCenterX * 0.15f)
            .coerceIn(-160f, 160f)
        val desiredLanderScreenY = h * (0.42f + proximityT * 0.08f)
        val groundBaseScreenY = h * 0.78f

        // Smooth vertical camera follow so pad and lander stay framed cleanly
        val camWorldY = (state.y - (groundBaseScreenY - desiredLanderScreenY) / pxPerMeter)
            .coerceAtLeast(state.padElevationY - 12f)

        fun toScreenX(wx: Float): Float = w * 0.5f + (wx - camX) * pxPerMeter
        fun toScreenY(wy: Float): Float = groundBaseScreenY - (wy - camWorldY) * pxPerMeter

        // Atmospheric wind streaks (Mars & Titan)
        if (abs(state.currentWindSpeed) > 0.25f) {
            val windDir = if (state.currentWindSpeed > 0f) 1f else -1f
            val streakColor = mission.accentColor.copy(alpha = 0.22f)
            for (i in 0 until 14) {
                val t = ((state.elapsedTimeSec * abs(state.currentWindSpeed) * 0.18f + i * 0.17f) % 1.2f)
                val sx = if (windDir > 0f) t * w else (1.2f - t) * w
                val sy = (0.14f + (i % 7) * 0.1f) * h
                drawLine(
                    color = streakColor,
                    start = Offset(sx, sy),
                    end = Offset(sx + windDir * 44f, sy),
                    strokeWidth = 2f,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Terrain polygon
        if (state.terrain.size >= 2) {
            val terrainPath = Path()
            val surfacePath = Path()
            val first = state.terrain.first()
            terrainPath.moveTo(toScreenX(first.x), h + 40f)
            terrainPath.lineTo(toScreenX(first.x), toScreenY(first.y))
            surfacePath.moveTo(toScreenX(first.x), toScreenY(first.y))

            for (i in 1 until state.terrain.size) {
                val pt = state.terrain[i]
                val sx = toScreenX(pt.x)
                val sy = toScreenY(pt.y)
                terrainPath.lineTo(sx, sy)
                surfacePath.lineTo(sx, sy)
            }
            terrainPath.lineTo(toScreenX(state.terrain.last().x), h + 40f)
            terrainPath.close()

            drawPath(
                path = terrainPath,
                brush = Brush.verticalGradient(
                    colors = listOf(mission.terrainTopColor, mission.terrainDeepColor),
                    startY = toScreenY(65f),
                    endY = h,
                ),
            )
            drawPath(
                path = surfacePath,
                color = mission.terrainTopColor.copy(alpha = 0.95f),
                style = Stroke(width = 3.5f),
            )
        }

        // Reinforced Landing Pad + Pulsing Approach Beacons
        val padLeftX = toScreenX(state.padCenterX - mission.padWidthMeters * 0.5f)
        val padRightX = toScreenX(state.padCenterX + mission.padWidthMeters * 0.5f)
        val padY = toScreenY(state.padElevationY)
        val padScreenW = padRightX - padLeftX

        drawRect(
            color = Color(0xFF1E293B),
            topLeft = Offset(padLeftX, padY - 4f),
            size = Size(padScreenW, 10f),
        )
        drawLine(
            color = SafeEmerald,
            start = Offset(padLeftX, padY - 4f),
            end = Offset(padRightX, padY - 4f),
            strokeWidth = 4f,
        )
        val beaconPulse = (0.45f + 0.55f * abs(sin(state.elapsedTimeSec * 4.5f)))
        drawCircle(
            color = SafeEmerald.copy(alpha = beaconPulse),
            radius = 7f,
            center = Offset(padLeftX + 4f, padY - 8f),
        )
        drawCircle(
            color = SafeEmerald.copy(alpha = beaconPulse),
            radius = 7f,
            center = Offset(padRightX - 4f, padY - 8f),
        )
        // Center target chevron
        val padCenterScreenX = toScreenX(state.padCenterX)
        drawLine(
            color = TelemetryCyan.copy(alpha = 0.7f),
            start = Offset(padCenterScreenX, padY - 12f),
            end = Offset(padCenterScreenX, padY + 6f),
            strokeWidth = 2.5f,
        )

        // Subtle ballistic trajectory prediction curve while flying
        if (state.phase == FlightPhase.FLYING) {
            val predPath = Path()
            var px = state.x
            var py = state.y
            var pvx = state.vx
            var pvy = state.vy
            predPath.moveTo(toScreenX(px), toScreenY(py))
            val simStep = 0.14f
            for (step in 1..14) {
                pvy -= mission.gravity * simStep
                px += pvx * simStep
                py += pvy * simStep
                predPath.lineTo(toScreenX(px), toScreenY(py))
            }
            drawPath(
                path = predPath,
                color = TelemetryCyan.copy(alpha = 0.30f),
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                ),
            )
        }

        // Particles (dust, exhaust, RCS, sparks, explosion)
        for (p in state.particles) {
            val alpha = (1f - p.life / p.maxLife).coerceIn(0f, 1f)
            val age = 1f - alpha
            val center = Offset(toScreenX(p.x), toScreenY(p.y))
            val baseRadius = p.size * (pxPerMeter * 0.28f)
            when (p.kind) {
                ParticleKind.EXHAUST_FLAME -> {
                    val lowFuel = state.fuelPercent < 20f
                    val outerColor = if (lowFuel) CriticalCrimson else ThrusterOrange
                    val coreColor = if (lowFuel) ThrusterAmber else Color(0xFFFFF3C4)
                    drawCircle(
                        color = outerColor.copy(alpha = alpha * 0.42f),
                        radius = baseRadius * (1.55f + age * 0.7f),
                        center = center,
                    )
                    drawCircle(
                        color = coreColor.copy(alpha = alpha * 0.90f),
                        radius = baseRadius * (0.45f + alpha * 0.55f),
                        center = center,
                    )
                }

                ParticleKind.EXPLOSION_FIREBALL -> {
                    val outerBlast = if (age < 0.55f) ThrusterOrange else CriticalCrimson
                    val coreBlast = if (age < 0.35f) Color(0xFFFEF9C3) else ThrusterAmber
                    drawCircle(
                        color = outerBlast.copy(alpha = alpha * 0.50f),
                        radius = baseRadius * (1.2f + age * 1.3f) * 1.6f,
                        center = center,
                    )
                    drawCircle(
                        color = coreBlast.copy(alpha = alpha * 0.92f),
                        radius = baseRadius * (1.05f - age * 0.4f).coerceAtLeast(0.3f),
                        center = center,
                    )
                }

                ParticleKind.EXPLOSION_SHOCKWAVE -> {
                    drawCircle(
                        color = ThrusterAmber.copy(alpha = alpha * 0.78f),
                        radius = baseRadius * (0.6f + age * 4.5f),
                        center = center,
                        style = Stroke(width = (5f * alpha).coerceAtLeast(1.5f)),
                    )
                }

                ParticleKind.DEBRIS_SPARK -> {
                    drawCircle(
                        color = ThrusterOrange.copy(alpha = alpha * 0.48f),
                        radius = baseRadius * 2.0f,
                        center = center,
                    )
                    drawCircle(
                        color = Color(0xFFFEF08A).copy(alpha = alpha * 0.95f),
                        radius = baseRadius,
                        center = center,
                    )
                }

                ParticleKind.RCS_PUFF -> {
                    drawCircle(
                        color = Color(0xFFE2E8F0).copy(alpha = alpha * 0.85f),
                        radius = baseRadius,
                        center = center,
                    )
                }

                ParticleKind.REGOLITH_DUST -> {
                    drawCircle(
                        color = mission.terrainTopColor.copy(alpha = alpha * 0.80f),
                        radius = baseRadius * (1f + age * 0.8f),
                        center = center,
                    )
                }
            }
        }

        // Radar laser drop line to surface directly below lander
        val landerSX = toScreenX(state.x)
        val landerSY = toScreenY(state.y)
        val groundBelowSY = toScreenY(state.y - state.altitudeAgl - 2.0f)
        if (state.altitudeAgl > 1f && state.altitudeAgl < 110f) {
            drawLine(
                color = TelemetryCyan.copy(alpha = 0.28f),
                start = Offset(landerSX, landerSY + 2f * pxPerMeter),
                end = Offset(landerSX, groundBelowSY),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            )
        }

        // Spacecraft Falcon Lander
        drawFalconLander(
            center = Offset(landerSX, landerSY),
            scale = pxPerMeter,
            angleDeg = state.angleDeg,
            throttle = state.actualThrottle,
            rcsCommand = state.rcsCommand,
            gearCompression = state.gearCompression,
            crashed = state.phase == FlightPhase.CRASHED,
        )

        // Off-screen or high-altitude Pad Direction Indicator
        if (padCenterScreenX < 24f || padCenterScreenX > w - 24f || padY > h - 20f) {
            val clampedX = padCenterScreenX.coerceIn(36f, w - 36f)
            val indicatorY = (h * 0.72f)
            drawCircle(
                color = SafeEmerald.copy(alpha = 0.22f),
                radius = 16f,
                center = Offset(clampedX, indicatorY),
            )
            drawCircle(
                color = SafeEmerald,
                radius = 6f,
                center = Offset(clampedX, indicatorY),
            )
        }
    }
}

private fun DrawScope.drawCelestialBackdrop(
    mission: MissionDefinition,
    w: Float,
    h: Float,
) {
    when (mission.tier) {
        1 -> {
            // Distant blue Earth rising
            val earthPos = Offset(w * 0.82f, h * 0.18f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF60A5FA), Color(0xFF1D4ED8), Color(0xFF0B1120)),
                    center = Offset(earthPos.x - 10f, earthPos.y - 10f),
                    radius = 44f,
                ),
                radius = 32f,
                center = earthPos,
            )
        }
        2 -> {
            // Distant pale Sun halo in dusty Martian sky
            val sunPos = Offset(w * 0.22f, h * 0.2f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFEDD5), Color(0x44FB923C), Color.Transparent),
                    center = sunPos,
                    radius = 70f,
                ),
                radius = 70f,
                center = sunPos,
            )
        }
        3 -> {
            // Giant banded Jupiter looming on Europa's horizon
            val jupPos = Offset(w * 0.78f, h * 0.22f)
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD6B48C), Color(0xFF9E6B47), Color(0xFFE5C9A6), Color(0xFF6E472D)),
                    startY = jupPos.y - 68f,
                    endY = jupPos.y + 68f,
                ),
                radius = 68f,
                center = jupPos,
                alpha = 0.72f,
            )
        }
        else -> {
            // Ringed Saturn in hazy golden Titan sky
            val satPos = Offset(w * 0.75f, h * 0.22f)
            drawCircle(
                color = Color(0xFFEAB308).copy(alpha = 0.55f),
                radius = 42f,
                center = satPos,
            )
            rotate(-18f, pivot = satPos) {
                drawOval(
                    color = Color(0xFFFDE68A).copy(alpha = 0.45f),
                    topLeft = Offset(satPos.x - 86f, satPos.y - 16f),
                    size = Size(172f, 32f),
                    style = Stroke(width = 6f),
                )
            }
        }
    }
}

private fun DrawScope.drawFalconLander(
    center: Offset,
    scale: Float,
    angleDeg: Float,
    throttle: Float,
    rcsCommand: Float,
    gearCompression: Float,
    crashed: Boolean,
) {
    val s = scale.coerceAtLeast(2.2f)
    rotate(degrees = angleDeg, pivot = center) {
        val legCompressOffset = gearCompression * 0.45f * s

        // Main engine flame cone
        if (throttle > 0.03f && !crashed) {
            val flameLen = (2.2f + throttle * 5.2f) * s
            val outerFlame = Path().apply {
                moveTo(center.x - 0.75f * s, center.y + 1.3f * s)
                lineTo(center.x + 0.75f * s, center.y + 1.3f * s)
                lineTo(center.x, center.y + 1.3f * s + flameLen)
                close()
            }
            drawPath(outerFlame, color = ThrusterOrange.copy(alpha = 0.85f))
            val innerFlame = Path().apply {
                moveTo(center.x - 0.4f * s, center.y + 1.3f * s)
                lineTo(center.x + 0.4f * s, center.y + 1.3f * s)
                lineTo(center.x, center.y + 1.3f * s + flameLen * 0.62f)
                close()
            }
            drawPath(innerFlame, color = Color(0xFFFEF08A))
        }

        // RCS side thruster flare
        if (abs(rcsCommand) > 0.1f && !crashed) {
            val side = if (rcsCommand > 0f) -1f else 1f
            drawCircle(
                color = TelemetryCyan.copy(alpha = 0.8f),
                radius = 0.55f * s,
                center = Offset(center.x + side * 1.9f * s, center.y - 1.1f * s),
            )
        }

        // Landing Gear Struts (Left & Right)
        val strutColor = Color(0xFFCBD5E1)
        val footY = center.y + 2.15f * s - legCompressOffset
        // Left strut
        drawLine(
            color = strutColor,
            start = Offset(center.x - 1.25f * s, center.y + 0.7f * s),
            end = Offset(center.x - 2.35f * s, footY),
            strokeWidth = (0.28f * s).coerceAtLeast(2f),
            cap = StrokeCap.Round,
        )
        // Right strut
        drawLine(
            color = strutColor,
            start = Offset(center.x + 1.25f * s, center.y + 0.7f * s),
            end = Offset(center.x + 2.35f * s, footY),
            strokeWidth = (0.28f * s).coerceAtLeast(2f),
            cap = StrokeCap.Round,
        )
        // Footpads
        drawLine(
            color = Color(0xFFF59E0B),
            start = Offset(center.x - 2.85f * s, footY),
            end = Offset(center.x - 1.85f * s, footY),
            strokeWidth = (0.35f * s).coerceAtLeast(3f),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFFF59E0B),
            start = Offset(center.x + 1.85f * s, footY),
            end = Offset(center.x + 2.85f * s, footY),
            strokeWidth = (0.35f * s).coerceAtLeast(3f),
            cap = StrokeCap.Round,
        )

        // Main Engine Nozzle Bell
        val nozzle = Path().apply {
            moveTo(center.x - 0.55f * s, center.y + 0.85f * s)
            lineTo(center.x + 0.55f * s, center.y + 0.85f * s)
            lineTo(center.x + 0.85f * s, center.y + 1.45f * s)
            lineTo(center.x - 0.85f * s, center.y + 1.45f * s)
            close()
        }
        drawPath(nozzle, color = Color(0xFF334155))

        // Descent Stage (Gold Foil Thermal Shielding)
        drawRect(
            color = if (crashed) Color(0xFF78350F) else Color(0xFFD97706),
            topLeft = Offset(center.x - 1.55f * s, center.y - 0.1f * s),
            size = Size(3.1f * s, 1.05f * s),
        )

        // Ascent Crew Cabin (Metallic Titanium Hull)
        val cabin = Path().apply {
            moveTo(center.x - 1.15f * s, center.y - 0.1f * s)
            lineTo(center.x - 0.85f * s, center.y - 1.55f * s)
            lineTo(center.x + 0.85f * s, center.y - 1.55f * s)
            lineTo(center.x + 1.15f * s, center.y - 0.1f * s)
            close()
        }
        drawPath(cabin, color = Color(0xFFE2E8F0))

        // Cockpit Viewport
        drawRect(
            color = if (crashed) CriticalCrimson else TelemetryCyan,
            topLeft = Offset(center.x - 0.55f * s, center.y - 1.15f * s),
            size = Size(1.1f * s, 0.45f * s),
        )
    }
}

@Composable
private fun CockpitTelemetryHud(
    state: LanderSimState,
    onToggleSas: () -> Unit,
    onTogglePause: () -> Unit,
    onExit: () -> Unit,
) {
    val mission = state.mission
    val tiltColor = when {
        abs(state.angleDeg) <= mission.safeTiltDeg * 0.7f -> SafeEmerald
        abs(state.angleDeg) <= mission.safeTiltDeg -> ThrusterAmber
        else -> CriticalCrimson
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Top Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceObsidian.copy(alpha = 0.85f))
                        .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                        .testTag("cockpit_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "العودة",
                        tint = Color.White,
                    )
                }

                Surface(
                    color = SpaceObsidian.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, CockpitBorder, RoundedCornerShape(12.dp)),
                ) {
                    Text(
                        text = mission.nameAr,
                        style = MaterialTheme.typography.titleMedium,
                        color = mission.accentColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = onToggleSas,
                    color = if (state.sasEnabled) TelemetryCyan.copy(alpha = 0.22f) else SpaceObsidian.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .border(
                            1.dp,
                            if (state.sasEnabled) TelemetryCyan else CockpitBorder,
                            RoundedCornerShape(12.dp),
                        )
                        .testTag("sas_toggle_button"),
                ) {
                    Text(
                        text = if (state.sasEnabled) "SAS: نشط" else "SAS: يدوي",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.sasEnabled) TelemetryCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }

                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceObsidian.copy(alpha = 0.85f))
                        .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                        .testTag("pause_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Pause,
                        contentDescription = "إيقاف مؤقت",
                        tint = Color.White,
                    )
                }
            }
        }

        // Real-time Heads-Up Display (HUD) Overlay
        TelemetryHudOverlay(
            data = TelemetryHudData(
                altitudeMeters = state.altitudeAgl,
                verticalVelocityMps = state.vy,
                horizontalVelocityMps = state.vx,
                fuelPercent = state.fuelPercent,
                safeVerticalSpeedMps = mission.safeVerticalSpeed,
                safeHorizontalSpeedMps = mission.safeHorizontalSpeed,
                isBurningFuel = state.actualThrottle > 0.03f || abs(state.rcsCommand) > 0.1f,
                fuelMassLabel = "${String.format("%.0f", state.fuelKg)}kg",
                extraMetricLabel = "الميلان",
                extraMetricValue = "${String.format("%.0f", state.angleDeg)}°",
                extraMetricColor = tiltColor,
                secondaryExtraLabel = if (mission.atmosphericDensity > 0f) "الرياح" else "الجاذبية",
                secondaryExtraValue = if (mission.atmosphericDensity > 0f) {
                    "${String.format("%.1f", state.currentWindSpeed)}"
                } else {
                    "${String.format("%.2f", state.gForce)}G"
                },
                secondaryExtraColor = if (mission.atmosphericDensity > 0f) ThrusterAmber else Color.White,
            ),
        )
    }
}

@Composable
private fun RcsControlCluster(
    rcsCommand: Float,
    tiltEnabled: Boolean,
    onRcsChange: (Float) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        if (tiltEnabled) {
            Surface(
                color = TelemetryCyan.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(1.dp, TelemetryCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
            ) {
                Text(
                    text = "توجيه بالإمالة مفعل",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RcsHoldButton(
                icon = Icons.AutoMirrored.Filled.RotateLeft,
                contentDescription = "تدوير لليسار",
                active = rcsCommand < -0.1f,
                tag = "rcs_left_button",
                onPressedChanged = { pressed -> onRcsChange(if (pressed) -1f else 0f) },
            )
            RcsHoldButton(
                icon = Icons.AutoMirrored.Filled.RotateRight,
                contentDescription = "تدوير لليمين",
                active = rcsCommand > 0.1f,
                tag = "rcs_right_button",
                onPressedChanged = { pressed -> onRcsChange(if (pressed) 1f else 0f) },
            )
        }
    }
}

@Composable
private fun RcsHoldButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    tag: String,
    onPressedChanged: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(if (active) TelemetryCyan.copy(alpha = 0.35f) else SpaceObsidian.copy(alpha = 0.85f))
            .border(2.dp, if (active) TelemetryCyan else CockpitBorder, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressedChanged(true)
                        try {
                            awaitRelease()
                        } finally {
                            onPressedChanged(false)
                        }
                    }
                )
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) TelemetryCyan else Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun ThrottleControlColumn(
    targetThrottle: Float,
    actualThrottle: Float,
    hasFuel: Boolean,
    onThrottleChange: (Float) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Quick MAX boost button
        Surface(
            onClick = { onThrottleChange(1f) },
            color = ThrusterAmber.copy(alpha = 0.22f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .border(1.dp, ThrusterAmber.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                .testTag("throttle_max_button"),
        ) {
            Text(
                text = "MAX",
                style = MaterialTheme.typography.labelMedium,
                color = ThrusterAmber,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        // Vertical Analog Throttle Track
        Box(
            modifier = Modifier
                .width(66.dp)
                .height(175.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(SpaceObsidian.copy(alpha = 0.88f))
                .border(1.5.dp, CockpitBorder, RoundedCornerShape(20.dp))
                .pointerInput(hasFuel) {
                    detectTapGestures { offset ->
                        val norm = (1f - (offset.y / size.height.toFloat())).coerceIn(0f, 1f)
                        onThrottleChange(if (norm < 0.06f) 0f else norm)
                    }
                }
                .pointerInput(hasFuel) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val norm = (1f - (change.position.y / size.height.toFloat())).coerceIn(0f, 1f)
                        onThrottleChange(if (norm < 0.06f) 0f else norm)
                    }
                }
                .testTag("throttle_lever"),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // Actual spooled engine fill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = actualThrottle.coerceIn(0f, 1f))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ThrusterAmber, ThrusterOrange)
                        )
                    )
            )
            // Target throttle notch bar
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${(targetThrottle * 100f).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = "الدفع",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        // Quick CUT button
        Surface(
            onClick = { onThrottleChange(0f) },
            color = SpaceObsidian.copy(alpha = 0.88f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .border(1.dp, CockpitBorder, RoundedCornerShape(10.dp))
                .testTag("throttle_cut_button"),
        ) {
            Text(
                text = "CUT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PauseOverlay(
    missionName: String,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = SpaceObsidian,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 400.dp)
                .fillMaxWidth()
                .border(1.dp, CockpitBorder, RoundedCornerShape(24.dp)),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "إيقاف مؤقت",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                Text(
                    text = missionName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TelemetryCyan,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("resume_button"),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resume • استئناف")
                }
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("restart_pause_button"),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restart • إعادة البدء")
                }
                OutlinedButton(
                    onClick = onExit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text("العودة لمركز القيادة")
                }
                BrandCopyrightBadge(compact = true)
            }
        }
    }
}

@Composable
private fun FlightDebriefOverlay(
    state: LanderSimState,
    nextMission: MissionDefinition?,
    onRetry: () -> Unit,
    onNextMission: () -> Unit,
    onExit: () -> Unit,
) {
    val isWin = state.phase == FlightPhase.LANDED_SAFE
    val statusColor = if (isWin) SafeEmerald else CriticalCrimson

    Surface(
        color = SpaceObsidian.copy(alpha = 0.96f),
        shape = RoundedCornerShape(26.dp),
        modifier = Modifier
            .padding(22.dp)
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .border(1.5.dp, statusColor.copy(alpha = 0.65f), RoundedCornerShape(26.dp)),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = if (isWin) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(48.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isWin) "تم الهبوط بنجاح!" else "تحطمت المركبة!",
                style = MaterialTheme.typography.headlineMedium,
                color = statusColor,
                textAlign = TextAlign.Center,
            )

            if (isWin) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(3) { idx ->
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = if (idx < state.touchdownStars) ThrusterAmber else CockpitBorder,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "النتيجة: ${state.touchdownScore}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = TelemetryMonoFontFamily),
                    color = TelemetryCyan,
                )
            } else if (!state.failureReasonAr.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.failureReasonAr,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Touchdown telemetry summary
            Surface(
                color = SpaceCarbon,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CockpitBorder, RoundedCornerShape(14.dp)),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DebriefRow("السرعة العمودية", "${String.format("%.2f", state.verticalSpeedAbs)} م/ث")
                    DebriefRow("السرعة الأفقية", "${String.format("%.2f", state.horizontalSpeedAbs)} م/ث")
                    DebriefRow("زاوية الميلان", "${String.format("%.1f", abs(state.angleDeg))}°")
                    DebriefRow("الوقود المتبقي", "${String.format("%.0f", state.fuelPercent)}%")
                    DebriefRow("دقة المنصة", "${String.format("%.0f", state.padAccuracyPercent)}%")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (nextMission != null && isWin) {
                Button(
                    onClick = onNextMission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafeEmerald,
                        contentColor = SpaceVoid,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("next_mission_button"),
                ) {
                    Icon(Icons.Filled.RocketLaunch, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("المهمة التالية: ${nextMission.nameAr}")
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("retry_mission_button"),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة المحاولة")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("return_base_button"),
            ) {
                Text("العودة لمركز القيادة")
            }
            Spacer(modifier = Modifier.height(10.dp))
            BrandCopyrightBadge(compact = true)
        }
    }
}

@Composable
private fun DebriefRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
