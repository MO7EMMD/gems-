package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FlightRepository
import com.example.data.model.FlightLog
import com.example.data.model.MissionCatalog
import com.example.data.model.MissionDefinition
import com.example.data.model.PilotProfile
import com.example.game.FlightPhase
import com.example.game.LanderSimState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MissionControlViewModel(
    private val repository: FlightRepository,
    private val currentUserId: String,
    defaultDisplayName: String?,
) : ViewModel() {

    private val defaultCallsign = defaultDisplayName?.trim()?.takeIf { it.isNotEmpty() }?.take(40) ?: "صقر-1"

    val profileState: StateFlow<UiState<PilotProfile>> = repository.observePilotProfile(currentUserId)
        .map<PilotProfile?, UiState<PilotProfile>> { profile ->
            UiState.Success(
                profile ?: PilotProfile(
                    id = currentUserId,
                    userId = currentUserId,
                    callsign = defaultCallsign,
                )
            )
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing pilot profile", rootError)
            emit(UiState.Error(rootError.message ?: "تعذر تحميل ملف الطيار"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    val flightLogsState: StateFlow<UiState<List<FlightLog>>> = repository.observeFlightLogs(currentUserId)
        .map<List<FlightLog>, UiState<List<FlightLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing flight logs", rootError)
            emit(UiState.Error(rootError.message ?: "تعذر تحميل سجل الرحلات"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _selectedMission = MutableStateFlow(MissionCatalog.missions.first())
    val selectedMission: StateFlow<MissionDefinition> = _selectedMission.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    init {
        viewModelScope.launch {
            val res = repository.ensurePilotProfile(defaultCallsign)
            if (res.isFailure) {
                _syncError.value = "تعذر تهيئة سجل الطيار السحابي"
            }
        }
    }

    fun selectMission(mission: MissionDefinition) {
        _selectedMission.value = mission
    }

    fun dismissSyncError() {
        _syncError.value = null
    }

    fun updatePreferences(
        callsign: String? = null,
        tiltControlEnabled: Boolean? = null,
        soundEnabled: Boolean? = null,
    ) {
        viewModelScope.launch {
            val result = repository.updatePreferences(
                callsign = callsign,
                tiltControlEnabled = tiltControlEnabled,
                soundEnabled = soundEnabled,
            )
            if (result.isFailure) {
                _syncError.value = "فشل حفظ الإعدادات سحابياً"
            }
        }
    }

    fun recordFlightResult(simState: LanderSimState) {
        if (simState.phase != FlightPhase.LANDED_SAFE && simState.phase != FlightPhase.CRASHED) return
        val mission = simState.mission
        val isWin = simState.phase == FlightPhase.LANDED_SAFE
        val nextTier = if (isWin) (mission.tier + 1).coerceAtMost(4) else mission.tier

        val log = FlightLog(
            userId = currentUserId,
            missionId = mission.id,
            missionName = mission.nameAr,
            success = isWin,
            score = simState.touchdownScore.toLong(),
            ratingStars = simState.touchdownStars.toLong(),
            touchdownVerticalSpeed = simState.verticalSpeedAbs.toDouble(),
            touchdownHorizontalSpeed = simState.horizontalSpeedAbs.toDouble(),
            touchdownTiltDeg = kotlin.math.abs(simState.angleDeg).toDouble(),
            fuelRemainingPercent = simState.fuelPercent.toDouble(),
            flightDurationSec = simState.elapsedTimeSec.toDouble(),
            failureReason = simState.failureReasonAr,
        )

        viewModelScope.launch {
            val result = repository.recordFlightOutcome(
                log = log,
                unlockedTierCandidate = nextTier,
                defaultCallsign = defaultCallsign,
            )
            if (result.isFailure) {
                _syncError.value = "تعذر مزامنة نتيجة الرحلة مع السحابة"
            }
        }
    }

    fun deleteFlightLog(logId: String) {
        viewModelScope.launch {
            val result = repository.deleteFlightLog(logId)
            if (result.isFailure) {
                _syncError.value = "تعذر حذف السجل"
            }
        }
    }

    private companion object {
        const val TAG = "MissionControlVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
