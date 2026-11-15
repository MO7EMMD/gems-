package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FlightLog
import com.example.data.model.MissionCatalog
import com.example.data.model.MissionDefinition
import com.example.data.model.PilotProfile
import com.example.ui.components.BrandCopyrightBadge
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CriticalCrimson
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SpaceCarbon
import com.example.ui.theme.SpaceCarbonElevated
import com.example.ui.theme.SpaceObsidian
import com.example.ui.theme.SpaceVoid
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.ThrusterAmber

@Composable
fun MissionBriefingScreen(
    profileState: UiState<PilotProfile>,
    flightLogsState: UiState<List<FlightLog>>,
    selectedMission: MissionDefinition,
    syncError: String?,
    isGuestMode: Boolean = false,
    onDismissSyncError: () -> Unit,
    onSelectMission: (MissionDefinition) -> Unit,
    onLaunchMission: (MissionDefinition) -> Unit,
    onOpenSimplePhysicsLab: () -> Unit,
    onUpdatePreferences: (callsign: String?, tiltEnabled: Boolean?, soundEnabled: Boolean?) -> Unit,
    onDeleteLog: (String) -> Unit,
    onRequestGoogleSignIn: () -> Unit = {},
    onSignOut: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val profile = (profileState as? UiState.Success)?.data ?: PilotProfile()
    val unlockedTier = profile.highestUnlockedTier.toInt().coerceIn(1, 4)

    Scaffold(
        containerColor = SpaceVoid,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (selectedTab == 0) {
                val isMissionUnlocked = selectedMission.tier <= unlockedTier
                Surface(
                    color = SpaceObsidian.copy(alpha = 0.96f),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CockpitBorder),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Button(
                            onClick = { onLaunchMission(selectedMission) },
                            enabled = isMissionUnlocked,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = selectedMission.accentColor,
                                contentColor = SpaceVoid,
                                disabledContainerColor = SpaceCarbonElevated,
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            modifier = Modifier
                                .widthIn(max = 560.dp)
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("launch_mission_button"),
                        ) {
                            Icon(
                                imageVector = if (isMissionUnlocked) Icons.Filled.RocketLaunch else Icons.Filled.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isMissionUnlocked) {
                                    "إطلاق مهمة: ${selectedMission.nameAr}"
                                } else {
                                    "أتمم المهمة السابقة لفتح ${selectedMission.nameAr}"
                                },
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
            ) {
                // Top Command Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "مركز قيادة الصقر",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(SafeEmerald)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "القائد: ${profile.callsign}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TelemetryCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenSimplePhysicsLab,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("open_simple_physics_button"),
                        ) {
                            Text(
                                text = "مختبر Canvas",
                                style = MaterialTheme.typography.labelLarge,
                                color = TelemetryCyan,
                            )
                        }

                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpaceCarbon)
                                .border(1.dp, CockpitBorder, RoundedCornerShape(12.dp))
                                .testTag("settings_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "إعدادات الطيران والحساب",
                                tint = TelemetryCyan,
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = syncError != null) {
                    Surface(
                        color = CriticalCrimson.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .border(1.dp, CriticalCrimson.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    tint = CriticalCrimson,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = syncError ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CriticalCrimson,
                                )
                            }
                            TextButton(onClick = onDismissSyncError) {
                                Text("إغلاق", color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = isGuestMode) {
                    Surface(
                        color = TelemetryCyan.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .border(1.dp, TelemetryCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "وضع محلي نشط • تسجيل دخول Google اختياري",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = onRequestGoogleSignIn,
                                modifier = Modifier.testTag("optional_google_sign_in_button"),
                            ) {
                                Text(
                                    text = "ربط حساب",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = TelemetryCyan,
                                )
                            }
                        }
                    }
                }

                // Career Telemetry Card
                PilotTelemetryBanner(
                    profile = profile,
                    isLoading = profileState is UiState.Loading,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = SpaceObsidian,
                    contentColor = TelemetryCyan,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, CockpitBorder, RoundedCornerShape(14.dp)),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("tab_missions"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("المهام الكوكبية")
                            }
                        },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.testTag("tab_flight_logs"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("الصندوق الأسود")
                            }
                        },
                    )
                }

                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(MissionCatalog.missions, key = { it.id }) { mission ->
                                val unlocked = mission.tier <= unlockedTier
                                val isSelected = mission.id == selectedMission.id
                                MissionCard(
                                    mission = mission,
                                    unlocked = unlocked,
                                    selected = isSelected,
                                    onClick = { onSelectMission(mission) },
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                BrandCopyrightBadge(
                                    modifier = Modifier.fillMaxWidth(),
                                    compact = false,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    1 -> {
                        FlightLogbookTab(
                            flightLogsState = flightLogsState,
                            onDeleteLog = onDeleteLog,
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        PilotSettingsDialog(
            profile = profile,
            isGuestMode = isGuestMode,
            onDismiss = { showSettingsDialog = false },
            onSave = { callsign, tilt, sound ->
                onUpdatePreferences(callsign, tilt, sound)
                showSettingsDialog = false
            },
            onRequestGoogleSignIn = {
                showSettingsDialog = false
                onRequestGoogleSignIn()
            },
            onSignOut = {
                showSettingsDialog = false
                onSignOut()
            },
        )
    }
}

@Composable
private fun PilotTelemetryBanner(
    profile: PilotProfile,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, CockpitBorder, RoundedCornerShape(20.dp)),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_mission_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xE6070B14), Color(0xAA0A1324))
                    )
                )
        )
        if (isLoading) {
            CircularProgressIndicator(
                color = TelemetryCyan,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatChip(label = "أعلى نتيجة", value = "${profile.highestScore}", accent = ThrusterAmber)
                StatChip(label = "هبوط ناجح", value = "${profile.successfulLandings}", accent = SafeEmerald)
                StatChip(label = "الرحلات", value = "${profile.totalFlights}", accent = TelemetryCyan)
                StatChip(label = "العوالم", value = "${profile.highestUnlockedTier}/4", accent = Color.White)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.example.ui.theme.TelemetryMonoFontFamily),
            color = accent,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MissionCard(
    mission: MissionDefinition,
    unlocked: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = when {
        selected -> mission.accentColor
        else -> CockpitBorder
    }
    val bgColor = if (selected) SpaceCarbonElevated else SpaceCarbon

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("mission_card_${mission.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Procedural Planet Visual Emblem
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(mission.skyTopColor),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(mission.terrainTopColor, mission.terrainDeepColor),
                        center = Offset(size.width * 0.35f, size.height * 0.35f),
                        radius = size.minDimension * 0.65f,
                    ),
                    radius = size.minDimension * 0.36f,
                )
                if (mission.tier == 4) {
                    drawOval(
                        color = mission.accentColor.copy(alpha = 0.5f),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.4f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.22f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
                    )
                }
            }
            if (!unlocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "مغلق",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = mission.nameAr,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = mission.accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "T${mission.tier}",
                        style = MaterialTheme.typography.labelSmall,
                        color = mission.accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = mission.subtitleAr,
                style = MaterialTheme.typography.bodyMedium,
                color = mission.accentColor,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = mission.briefingAr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "أقصى هبوط: ${mission.safeVerticalSpeed} م/ث",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (mission.atmosphericDensity > 0f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Air,
                            contentDescription = null,
                            tint = ThrusterAmber,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "رياح نشطة",
                            style = MaterialTheme.typography.labelSmall,
                            color = ThrusterAmber,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightLogbookTab(
    flightLogsState: UiState<List<FlightLog>>,
    onDeleteLog: (String) -> Unit,
) {
    when (flightLogsState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TelemetryCyan)
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = CriticalCrimson,
                        modifier = Modifier.size(44.dp),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = flightLogsState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CriticalCrimson,
                    )
                }
            }
        }
        is UiState.Success -> {
            val logs = flightLogsState.data
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = TelemetryCyan.copy(alpha = 0.65f),
                            modifier = Modifier.size(54.dp),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "لا توجد رحلات مسجلة بعد",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "أطلق أول مهمة هبوط ليتم توثيق بيانات الرادار والسرعة والوقود هنا.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        BrandCopyrightBadge(compact = false)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(logs, key = { it.id.ifBlank { "${it.missionId}_${it.flightDurationSec}" } }) { log ->
                        FlightLogRow(log = log, onDelete = { if (log.id.isNotBlank()) onDeleteLog(log.id) })
                    }
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        BrandCopyrightBadge(
                            modifier = Modifier.fillMaxWidth(),
                            compact = false,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightLogRow(
    log: FlightLog,
    onDelete: () -> Unit,
) {
    val statusColor = if (log.success) SafeEmerald else CriticalCrimson
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SpaceCarbon)
            .border(1.dp, CockpitBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (log.success) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
            contentDescription = if (log.success) "هبوط ناجح" else "تحطم",
            tint = statusColor,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = log.missionName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (log.success) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(3) { idx ->
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (idx < log.ratingStars) ThrusterAmber else CockpitBorder,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "عمودي: ${String.format("%.1f", log.touchdownVerticalSpeed)} م/ث • ميلان: ${String.format("%.1f", log.touchdownTiltDeg)}° • وقود: ${String.format("%.0f", log.fuelRemainingPercent)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!log.success && !log.failureReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = log.failureReason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CriticalCrimson,
                )
            } else if (log.success) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "النقاط: ${log.score}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TelemetryCyan,
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("delete_log_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = "حذف السجل",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PilotSettingsDialog(
    profile: PilotProfile,
    isGuestMode: Boolean,
    onDismiss: () -> Unit,
    onSave: (callsign: String, tiltEnabled: Boolean, soundEnabled: Boolean) -> Unit,
    onRequestGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    var callsign by remember(profile.callsign) { mutableStateOf(profile.callsign) }
    var tiltEnabled by remember(profile.tiltControlEnabled) { mutableStateOf(profile.tiltControlEnabled) }
    var soundEnabled by remember(profile.soundEnabled) { mutableStateOf(profile.soundEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SpaceObsidian,
        title = {
            Text(
                text = "إعدادات الطيار والمحاكاة",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = callsign,
                    onValueChange = { if (it.length <= 40) callsign = it },
                    label = { Text("نداء الطيار (Callsign)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("callsign_input"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("التوجيه بإمالة الهاتف", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "استخدام مستشعر الجاذبية للتحكم بميلان المركبة",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = tiltEnabled,
                        onCheckedChange = { tiltEnabled = it },
                        modifier = Modifier.testTag("tilt_switch"),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("المؤثرات الصوتية للمحرك", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "توليد صوت الدفع الصاروخي وصافرة الإنذار",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = soundEnabled,
                        onCheckedChange = { soundEnabled = it },
                        modifier = Modifier.testTag("sound_switch"),
                    )
                }
                if (isGuestMode) {
                    OutlinedButton(
                        onClick = onRequestGoogleSignIn,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TelemetryCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_google_sign_in_button"),
                    ) {
                        Icon(Icons.Filled.RocketLaunch, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تسجيل الدخول عبر Google (اختياري)")
                    }
                } else {
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CriticalCrimson),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("تسجيل الخروج من الحساب")
                    }
                }
                BrandCopyrightBadge(
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(callsign, tiltEnabled, soundEnabled) },
                modifier = Modifier.testTag("save_settings_button"),
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
    )
}
