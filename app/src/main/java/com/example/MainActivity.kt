package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOutUser
import com.example.data.firebase.FlightRepository
import com.example.data.model.MissionDefinition
import com.example.data.model.PilotProfile
import com.example.ui.FlightCockpitScreen
import com.example.ui.FlightViewModel
import com.example.ui.MissionBriefingScreen
import com.example.ui.MissionControlViewModel
import com.example.ui.UiState
import com.example.ui.theme.FalconLanderTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FalconLanderTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppNavigation()
                }
            }
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(
            onAuthSuccess = {},
        )
    } else {
        key(user.uid) {
            FalconLanderAppShell(
                currentUserId = user.uid,
                displayName = user.displayName,
            )
        }
    }
}

@Composable
private fun FalconLanderAppShell(
    currentUserId: String,
    displayName: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val missionControlViewModel: MissionControlViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                MissionControlViewModel(
                    repository = FlightRepository(db, Firebase.auth),
                    currentUserId = currentUserId,
                    defaultDisplayName = displayName,
                )
            }
        },
    )

    val profileState by missionControlViewModel.profileState.collectAsStateWithLifecycle()
    val flightLogsState by missionControlViewModel.flightLogsState.collectAsStateWithLifecycle()
    val selectedMission by missionControlViewModel.selectedMission.collectAsStateWithLifecycle()
    val syncError by missionControlViewModel.syncError.collectAsStateWithLifecycle()

    var activeMission by remember { mutableStateOf<MissionDefinition?>(null) }

    val profile = (profileState as? UiState.Success)?.data ?: PilotProfile()

    val currentFlightMission = activeMission
    if (currentFlightMission == null) {
        MissionBriefingScreen(
            profileState = profileState,
            flightLogsState = flightLogsState,
            selectedMission = selectedMission,
            syncError = syncError,
            onDismissSyncError = { missionControlViewModel.dismissSyncError() },
            onSelectMission = { missionControlViewModel.selectMission(it) },
            onLaunchMission = { activeMission = it },
            onUpdatePreferences = { callsign, tilt, sound ->
                missionControlViewModel.updatePreferences(callsign, tilt, sound)
            },
            onDeleteLog = { missionControlViewModel.deleteFlightLog(it) },
            onSignOut = {
                signOutUser(credentialManager = credentialManager, scope = scope)
            },
        )
    } else {
        key(currentUserId, currentFlightMission.id) {
            val flightViewModel: FlightViewModel = viewModel(
                key = "${currentUserId}_${currentFlightMission.id}",
                factory = viewModelFactory {
                    initializer {
                        FlightViewModel(
                            initialMission = currentFlightMission,
                            soundEnabledInitially = profile.soundEnabled,
                            onFlightFinished = { finalState ->
                                missionControlViewModel.recordFlightResult(finalState)
                            },
                        )
                    }
                },
            )

            FlightCockpitScreen(
                flightViewModel = flightViewModel,
                tiltControlEnabled = profile.tiltControlEnabled,
                unlockedTier = profile.highestUnlockedTier.toInt().coerceIn(1, 4),
                onExitToBriefing = { activeMission = null },
                onSwitchMission = { next ->
                    missionControlViewModel.selectMission(next)
                    activeMission = next
                },
            )
        }
    }
}
