package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.game.SimpleLandingStatus
import com.example.game.SimpleParticleKind
import com.example.game.SimplePhysicsEngine
import com.example.game.SimpleSpacecraftState
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
import kotlin.math.abs

private data class GravityPreset(
    val label: String,
    val gravity: Float,
    val thrusterPower: Float,
)

private val gravityPresets = listOf(
    GravityPreset("القمر 1.62", gravity = 1.62f, thrusterPower = 4.8f),
    GravityPreset("المريخ 3.72", gravity = 3.72f, thrusterPower = 9.2f),
    GravityPreset("الأرض 9.81", gravity = 9.81f, thrusterPower = 18.5f),
)

@Composable
fun SimplePhysicsLabScreen(
    onBack: () -> Unit,
) {
    val engine = remember { SimplePhysicsEngine() }
    var selectedPreset by remember { mutableStateOf(gravityPresets.first()) }
    var simState by remember {
        mutableStateOf(
            SimpleSpacecraftState(
                gravity = selectedPreset.gravity,
                thrusterPower = selectedPreset.thrusterPower,
                terrain = SimplePhysicsEngine.generateRandomTerrain(),
            )
        )
    }

    fun togglePause() {
        simState = when (simState.status) {
            SimpleLandingStatus.DESCENDING -> simState.copy(
                status = SimpleLandingStatus.PAUSED,
                isThrusterActive = false,
                horizontalInput = 0f,
            )
            SimpleLandingStatus.PAUSED -> simState.copy(status = SimpleLandingStatus.DESCENDING)
            else -> simState
        }
    }

    fun resetWithNewTerrain(preset: GravityPreset = selectedPreset) {
        simState = SimpleSpacecraftState(
            gravity = preset.gravity,
            thrusterPower = preset.thrusterPower,
            terrain = SimplePhysicsEngine.generateRandomTerrain(),
        )
    }

    BackHandler {
        if (simState.status == SimpleLandingStatus.DESCENDING || simState.status == SimpleLandingStatus.PAUSED) {
            togglePause()
        } else {
            onBack()
        }
    }

    // Per-frame physics & particle loop synchronized to Compose's Choreographer frame clock
    LaunchedEffect(simState.status, selectedPreset) {
        if (simState.status == SimpleLandingStatus.PAUSED) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (simState.status == SimpleLandingStatus.DESCENDING || simState.particles.isNotEmpty()) {
            withFrameNanos { frameNanos ->
                val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNanos = frameNanos
                simState = engine.stepFrame(simState, dt)
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceVoid)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val isDown = keyEvent.type == KeyEventType.KeyDown
                val isUp = keyEvent.type == KeyEventType.KeyUp
                when (keyEvent.key) {
                    Key.Spacebar, Key.W, Key.DirectionUp -> {
                        if (simState.status == SimpleLandingStatus.DESCENDING) {
                            if (isDown) simState = simState.copy(isThrusterActive = true)
                            else if (isUp) simState = simState.copy(isThrusterActive = false)
                        }
                        true
                    }
                    Key.A, Key.DirectionLeft -> {
                        if (simState.status == SimpleLandingStatus.DESCENDING) {
                            if (isDown) simState = simState.copy(horizontalInput = -1f)
                            else if (isUp) simState = simState.copy(horizontalInput = 0f)
                        }
                        true
                    }
                    Key.D, Key.DirectionRight -> {
                        if (simState.status == SimpleLandingStatus.DESCENDING) {
                            if (isDown) simState = simState.copy(horizontalInput = 1f)
                            else if (isUp) simState = simState.copy(horizontalInput = 0f)
                        }
                        true
                    }
                    Key.P, Key.Escape -> {
                        if (isDown) togglePause()
                        true
                    }
                    Key.R -> {
                        if (isDown) resetWithNewTerrain()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Compose Canvas Physics Renderer
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("simple_physics_canvas"),
        ) {
            val w = size.width
            val h = size.height
            val baseGroundY = h * 0.85f
            val topSpawnY = h * 0.22f
            val maxAltitude = 120f

            fun altToScreenY(altMeters: Float): Float {
                val norm = (altMeters / maxAltitude).coerceIn(-0.05f, 1.15f)
                return baseGroundY - norm * (baseGroundY - topSpawnY)
            }

            // Sky gradient
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF030712), Color(0xFF0F172A))
                ),
                size = size,
            )

            // Altitude ruler grid lines every 30m
            for (alt in 30..120 step 30) {
                val lineY = altToScreenY(alt.toFloat())
                drawLine(
                    color = Color.White.copy(alpha = 0.08f),
                    start = Offset(24f, lineY),
                    end = Offset(w - 24f, lineY),
                    strokeWidth = 1.5f,
                )
            }

            // Random jagged terrain polygon at the bottom of the screen
            val terrain = simState.terrain
            if (terrain.heightsMeters.size >= 2) {
                val terrainPath = Path()
                val ridgePath = Path()
                val count = terrain.heightsMeters.size

                terrainPath.moveTo(0f, h)
                for (i in 0 until count) {
                    val x = (i.toFloat() / (count - 1).toFloat()) * w
                    val y = altToScreenY(terrain.heightsMeters[i])
                    if (i == 0) {
                        terrainPath.lineTo(x, y)
                        ridgePath.moveTo(x, y)
                    } else {
                        terrainPath.lineTo(x, y)
                        ridgePath.lineTo(x, y)
                    }
                }
                terrainPath.lineTo(w, h)
                terrainPath.close()

                drawPath(
                    path = terrainPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF475569), Color(0xFF0F172A)),
                        startY = altToScreenY(28f),
                        endY = h,
                    ),
                )
                drawPath(
                    path = ridgePath,
                    color = Color(0xFF94A3B8),
                    style = Stroke(width = 3f),
                )
            }

            // Highlighted flat landing pad carved into the random terrain
            val padLeft = terrain.padStartNorm * w
            val padRight = terrain.padEndNorm * w
            val padY = altToScreenY(terrain.padAltitudeMeters)
            drawRect(
                color = SafeEmerald,
                topLeft = Offset(padLeft, padY - 5f),
                size = Size(padRight - padLeft, 8f),
            )
            drawCircle(
                color = SafeEmerald,
                radius = 5f,
                center = Offset(padLeft + 4f, padY - 9f),
            )
            drawCircle(
                color = SafeEmerald,
                radius = 5f,
                center = Offset(padRight - 4f, padY - 9f),
            )

            // Spacecraft Screen Coordinates
            val craftFootY = altToScreenY(simState.altitudeMeters)
            val craftCenterX = simState.positionXNorm * w

            // Flame exhaust, ember, dust, and RCS particle system
            for (p in simState.particles) {
                val px = p.normX * w
                val py = altToScreenY(p.altitudeMeters)
                val age = p.ageFraction
                val alpha = (1f - age).coerceIn(0f, 1f)
                val radiusPx = p.radiusDp.dp.toPx()

                when (p.kind) {
                    SimpleParticleKind.MAIN_EXHAUST -> {
                        val isLowFuel = p.fuelRatioAtSpawn < 0.20f
                        val outerColor = when {
                            isLowFuel -> Color(0xFFEF4444)
                            age < 0.45f -> ThrusterOrange
                            else -> Color(0xFFDC2626)
                        }
                        val coreColor = when {
                            isLowFuel -> ThrusterAmber
                            age < 0.35f -> Color(0xFFFEF9C3)
                            else -> ThrusterAmber
                        }
                        val spreadScale = 1f + age * 0.75f
                        // Glowing outer flame plume
                        drawCircle(
                            color = outerColor.copy(alpha = alpha * 0.42f),
                            radius = radiusPx * spreadScale * 1.65f,
                            center = Offset(px, py),
                        )
                        // Hot inner flame core
                        drawCircle(
                            color = coreColor.copy(alpha = alpha * 0.92f),
                            radius = radiusPx * (1f - age * 0.45f).coerceAtLeast(0.25f),
                            center = Offset(px, py),
                        )
                    }

                    SimpleParticleKind.SMOKE_EMBER -> {
                        val emberColor = if (age < 0.35f) ThrusterOrange else Color(0xFF64748B)
                        drawCircle(
                            color = emberColor.copy(alpha = alpha * 0.50f),
                            radius = radiusPx * (1f + age * 0.9f),
                            center = Offset(px, py),
                        )
                    }

                    SimpleParticleKind.RCS_JET -> {
                        drawCircle(
                            color = TelemetryCyan.copy(alpha = alpha * 0.78f),
                            radius = radiusPx * (0.85f + age * 0.5f),
                            center = Offset(px, py),
                        )
                    }

                    SimpleParticleKind.GROUND_DUST -> {
                        drawCircle(
                            color = Color(0xFF94A3B8).copy(alpha = alpha * 0.48f),
                            radius = radiusPx * (1f + age * 1.1f),
                            center = Offset(px, py),
                        )
                    }

                    SimpleParticleKind.EXPLOSION_FIREBALL -> {
                        val blastOuter = when {
                            age < 0.30f -> ThrusterAmber
                            age < 0.65f -> ThrusterOrange
                            else -> Color(0xFF7F1D1D)
                        }
                        val blastCore = if (age < 0.40f) Color(0xFFFEF9C3) else CriticalCrimson
                        val expand = 1f + age * 1.35f
                        drawCircle(
                            color = blastOuter.copy(alpha = alpha * 0.52f),
                            radius = radiusPx * expand * 1.75f,
                            center = Offset(px, py),
                        )
                        drawCircle(
                            color = blastCore.copy(alpha = alpha * 0.90f),
                            radius = radiusPx * (1.1f - age * 0.45f).coerceAtLeast(0.3f),
                            center = Offset(px, py),
                        )
                    }

                    SimpleParticleKind.EXPLOSION_SPARK -> {
                        drawCircle(
                            color = ThrusterOrange.copy(alpha = alpha * 0.55f),
                            radius = radiusPx * 2.1f,
                            center = Offset(px, py),
                        )
                        drawCircle(
                            color = Color(0xFFFEF08A).copy(alpha = alpha),
                            radius = radiusPx,
                            center = Offset(px, py),
                        )
                    }

                    SimpleParticleKind.EXPLOSION_SHOCKWAVE -> {
                        val ringRadius = radiusPx * (0.4f + age * 5.2f)
                        drawCircle(
                            color = ThrusterAmber.copy(alpha = alpha * 0.80f),
                            radius = ringRadius,
                            center = Offset(px, py),
                            style = Stroke(width = (5.dp.toPx() * alpha).coerceAtLeast(1.5f)),
                        )
                    }
                }
            }

            // Nozzle combustion glow bloom while fuel is burning
            if (simState.isThrusterActive && simState.fuelPercent > 0f && simState.status == SimpleLandingStatus.DESCENDING) {
                val nozzleColor = if (simState.fuelPercent < 20f) CriticalCrimson else ThrusterAmber
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFEF08A), nozzleColor.copy(alpha = 0.65f), Color.Transparent),
                        center = Offset(craftCenterX, craftFootY - 6f),
                        radius = 36f,
                    ),
                    radius = 36f,
                    center = Offset(craftCenterX, craftFootY - 6f),
                )
            }

            // Spacecraft landing legs
            val legColor = Color(0xFFCBD5E1)
            drawLine(
                color = legColor,
                start = Offset(craftCenterX - 18f, craftFootY - 24f),
                end = Offset(craftCenterX - 32f, craftFootY),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = legColor,
                start = Offset(craftCenterX + 18f, craftFootY - 24f),
                end = Offset(craftCenterX + 32f, craftFootY),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )

            // Spacecraft body
            val hullColor = when (simState.status) {
                SimpleLandingStatus.CRASHED,
                SimpleLandingStatus.OUT_OF_FUEL -> CriticalCrimson
                SimpleLandingStatus.SAFE_LANDING -> SafeEmerald
                SimpleLandingStatus.DESCENDING,
                SimpleLandingStatus.PAUSED -> Color(0xFFE2E8F0)
            }
            drawRoundRect(
                color = hullColor,
                topLeft = Offset(craftCenterX - 24f, craftFootY - 62f),
                size = Size(48f, 44f),
                cornerRadius = CornerRadius(12f, 12f),
            )
            // Nose cone
            val nose = Path().apply {
                moveTo(craftCenterX - 18f, craftFootY - 62f)
                lineTo(craftCenterX + 18f, craftFootY - 62f)
                lineTo(craftCenterX, craftFootY - 82f)
                close()
            }
            drawPath(nose, color = ThrusterAmber)

            // Cockpit viewport
            drawCircle(
                color = TelemetryCyan,
                radius = 9f,
                center = Offset(craftCenterX, craftFootY - 44f),
            )
            drawCircle(
                color = SpaceVoid,
                radius = 9f,
                center = Offset(craftCenterX, craftFootY - 44f),
                style = Stroke(width = 2f),
            )
        }

        // Overlay HUD & Controls
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpaceObsidian.copy(alpha = 0.85f))
                                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                                .testTag("simple_physics_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "محرك الفيزياء البسيط (Canvas)",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { togglePause() },
                            enabled = simState.status == SimpleLandingStatus.DESCENDING ||
                                simState.status == SimpleLandingStatus.PAUSED,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpaceObsidian.copy(alpha = 0.85f))
                                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                                .testTag("simple_pause_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "إيقاف مؤقت",
                                tint = Color.White,
                            )
                        }

                        IconButton(
                            onClick = { resetWithNewTerrain() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpaceObsidian.copy(alpha = 0.85f))
                                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                                .testTag("simple_physics_reset_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "توليد تضاريس جديدة وإعادة",
                                tint = TelemetryCyan,
                            )
                        }
                    }
                }

                // Gravity Selector Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    gravityPresets.forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                resetWithNewTerrain(preset)
                            },
                            label = { Text(preset.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }

                // Real-time Heads-Up Display (HUD) Overlay
                val burningFuel = (simState.isThrusterActive || abs(simState.horizontalInput) > 0.1f) &&
                    simState.fuelPercent > 0f &&
                    simState.status == SimpleLandingStatus.DESCENDING
                TelemetryHudOverlay(
                    data = TelemetryHudData(
                        altitudeMeters = simState.altitudeAglMeters,
                        verticalVelocityMps = simState.velocityY,
                        horizontalVelocityMps = simState.horizontalSpeedMps,
                        fuelPercent = simState.fuelPercent,
                        safeVerticalSpeedMps = simState.safeLandingSpeed,
                        safeHorizontalSpeedMps = 6.0f,
                        isBurningFuel = burningFuel,
                    ),
                )

                BrandCopyrightBadge(
                    compact = true,
                    modifier = Modifier.align(Alignment.End),
                )
            }

            // Bottom Action Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (simState.status) {
                    SimpleLandingStatus.DESCENDING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HorizontalHoldButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "تحريك يمين",
                                active = simState.horizontalInput > 0.1f,
                                tag = "simple_move_right_button",
                                onPressedChanged = { pressed ->
                                    simState = simState.copy(horizontalInput = if (pressed) 1f else 0f)
                                },
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        if (simState.isThrusterActive) ThrusterAmber else SpaceCarbon
                                    )
                                    .border(
                                        1.5.dp,
                                        if (simState.isThrusterActive) ThrusterOrange else TelemetryCyan,
                                        RoundedCornerShape(18.dp),
                                    )
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onPress = {
                                                simState = simState.copy(isThrusterActive = true)
                                                try {
                                                    awaitRelease()
                                                } finally {
                                                    simState = simState.copy(isThrusterActive = false)
                                                }
                                            }
                                        )
                                    }
                                    .testTag("simple_thruster_hold_button"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = if (simState.isThrusterActive) SpaceVoid else ThrusterAmber,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "دفع (Space)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (simState.isThrusterActive) SpaceVoid else Color.White,
                                    )
                                }
                            }

                            HorizontalHoldButton(
                                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "تحريك يسار",
                                active = simState.horizontalInput < -0.1f,
                                tag = "simple_move_left_button",
                                onPressedChanged = { pressed ->
                                    simState = simState.copy(horizontalInput = if (pressed) -1f else 0f)
                                },
                            )
                        }
                    }

                    SimpleLandingStatus.PAUSED -> Unit

                    SimpleLandingStatus.SAFE_LANDING,
                    SimpleLandingStatus.CRASHED,
                    SimpleLandingStatus.OUT_OF_FUEL -> {
                        val isSafe = simState.status == SimpleLandingStatus.SAFE_LANDING
                        val isOutOfFuel = simState.status == SimpleLandingStatus.OUT_OF_FUEL
                        Surface(
                            color = SpaceObsidian.copy(alpha = 0.95f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.5.dp,
                                    if (isSafe) SafeEmerald else CriticalCrimson,
                                    RoundedCornerShape(18.dp),
                                ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when {
                                            isSafe -> "هبوط آمن!"
                                            isOutOfFuel -> "نفاد الوقود — انتهت اللعبة!"
                                            else -> "تحطم المركبة!"
                                        },
                                        style = MaterialTheme.typography.titleLarge,
                                        color = if (isSafe) SafeEmerald else CriticalCrimson,
                                    )
                                    Text(
                                        text = simState.failureReasonAr
                                            ?: "سرعة الملامسة: ${String.format("%.2f", simState.velocityY)} م/ث",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Button(
                                    onClick = { resetWithNewTerrain() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TelemetryCyan,
                                        contentColor = SpaceVoid,
                                    ),
                                ) {
                                    Text("تضاريس جديدة")
                                }
                            }
                        }
                    }
                }
            }
        }

        if (simState.status == SimpleLandingStatus.PAUSED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .testTag("simple_pause_overlay"),
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
                            text = selectedPreset.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TelemetryCyan,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { togglePause() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("simple_resume_button"),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resume • استئناف")
                        }
                        OutlinedButton(
                            onClick = { resetWithNewTerrain() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("simple_restart_button"),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restart • إعادة البدء")
                        }
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("simple_pause_exit_button"),
                        ) {
                            Text("العودة لمركز القيادة")
                        }
                        BrandCopyrightBadge(compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalHoldButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    tag: String,
    onPressedChanged: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) TelemetryCyan.copy(alpha = 0.35f) else SpaceObsidian.copy(alpha = 0.9f))
            .border(1.5.dp, if (active) TelemetryCyan else CockpitBorder, RoundedCornerShape(18.dp))
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
