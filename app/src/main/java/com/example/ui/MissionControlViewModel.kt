package com.example.ui

import android.content.SharedPreferences
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
import com.google.firebase.Timestamp
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
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MissionControlViewModel(
    private val repository: FlightRepository?,
    private val currentUserId: String?,
    defaultDisplayName: String?,
    private val guestPrefs: SharedPreferences? = null,
) : ViewModel() {

    val isGuestMode: Boolean = repository == null || currentUserId.isNullOrBlank()

    private val defaultCallsign = defaultDisplayName?.trim()?.takeIf { it.isNotEmpty() }?.take(40)
        ?: if (isGuestMode) "طيار محلي" else "صقر-1"

    private val _localProfileState = MutableStateFlow<UiState<PilotProfile>>(
        UiState.Success(loadLocalProfile())
    )
    private val _localFlightLogsState = MutableStateFlow<UiState<List<FlightLog>>>(
        UiState.Success(emptyList())
    )

    val profileState: StateFlow<UiState<PilotProfile>> = if (!isGuestMode && repository != null && currentUserId != null) {
        repository.observePilotProfile(currentUserId)
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
    } else {
        _localProfileState.asStateFlow()
    }

    val flightLogsState: StateFlow<UiState<List<FlightLog>>> = if (!isGuestMode && repository != null && currentUserId != null) {
        repository.observeFlightLogs(currentUserId)
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
    } else {
        _localFlightLogsState.asStateFlow()
    }

    private val _selectedMission = MutableStateFlow(MissionCatalog.missions.first())
    val selectedMission: StateFlow<MissionDefinition> = _selectedMission.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    init {
        if (!isGuestMode && repository != null) {
            viewModelScope.launch {
                val res = repository.ensurePilotProfile(defaultCallsign)
                if (res.isFailure) {
                    _syncError.value = "تعذر تهيئة سجل الطيار السحابي"
                }
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
        if (isGuestMode || repository == null) {
            val current = (_localProfileState.value as? UiState.Success)?.data ?: loadLocalProfile()
            val updated = current.copy(
                callsign = callsign?.trim()?.ifBlank { defaultCallsign }?.take(40) ?: current.callsign,
                tiltControlEnabled = tiltControlEnabled ?: current.tiltControlEnabled,
                soundEnabled = soundEnabled ?: current.soundEnabled,
            )
            saveLocalProfile(updated)
            _localProfileState.value = UiState.Success(updated)
            return
        }

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

        if (isGuestMode || repository == null || currentUserId == null) {
            val log = FlightLog(
                id = UUID.randomUUID().toString(),
                userId = "guest_local",
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
                createdAt = Timestamp.now(),
            )
            val currentLogs = (_localFlightLogsState.value as? UiState.Success)?.data.orEmpty()
            _localFlightLogsState.value = UiState.Success((listOf(log) + currentLogs).take(40))

            val currentProfile = (_localProfileState.value as? UiState.Success)?.data ?: loadLocalProfile()
            val updatedProfile = currentProfile.copy(
                totalFlights = currentProfile.totalFlights + 1L,
                successfulLandings = if (isWin) currentProfile.successfulLandings + 1L else currentProfile.successfulLandings,
                highestScore = maxOf(currentProfile.highestScore, simState.touchdownScore.toLong()),
                highestUnlockedTier = if (isWin) {
                    maxOf(currentProfile.highestUnlockedTier, nextTier.toLong()).coerceIn(1L, 4L)
                } else {
                    currentProfile.highestUnlockedTier.coerceIn(1L, 4L)
                },
            )
            saveLocalProfile(updatedProfile)
            _localProfileState.value = UiState.Success(updatedProfile)
            return
        }

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
        if (isGuestMode || repository == null) {
            val currentLogs = (_localFlightLogsState.value as? UiState.Success)?.data.orEmpty()
            _localFlightLogsState.value = UiState.Success(currentLogs.filterNot { it.id == logId })
            return
        }

        viewModelScope.launch {
            val result = repository.deleteFlightLog(logId)
            if (result.isFailure) {
                _syncError.value = "تعذر حذف السجل"
            }
        }
    }

    private fun loadLocalProfile(): PilotProfile {
        if (guestPrefs == null) {
            return PilotProfile(
                id = "guest_local",
                userId = "guest_local",
                callsign = defaultCallsign,
            )
        }
        return PilotProfile(
            id = "guest_local",
            userId = "guest_local",
            callsign = guestPrefs.getString(KEY_CALLSIGN, defaultCallsign) ?: defaultCallsign,
            totalFlights = guestPrefs.getLong(KEY_TOTAL_FLIGHTS, 0L),
            successfulLandings = guestPrefs.getLong(KEY_WINS, 0L),
            highestScore = guestPrefs.getLong(KEY_BEST_SCORE, 0L),
            highestUnlockedTier = guestPrefs.getLong(KEY_UNLOCKED_TIER, 1L).coerceIn(1L, 4L),
            tiltControlEnabled = guestPrefs.getBoolean(KEY_TILT, false),
            soundEnabled = guestPrefs.getBoolean(KEY_SOUND, true),
        )
    }

    private fun saveLocalProfile(profile: PilotProfile) {
        guestPrefs?.edit()
            ?.putString(KEY_CALLSIGN, profile.callsign)
            ?.putLong(KEY_TOTAL_FLIGHTS, profile.totalFlights)
            ?.putLong(KEY_WINS, profile.successfulLandings)
            ?.putLong(KEY_BEST_SCORE, profile.highestScore)
            ?.putLong(KEY_UNLOCKED_TIER, profile.highestUnlockedTier)
            ?.putBoolean(KEY_TILT, profile.tiltControlEnabled)
            ?.putBoolean(KEY_SOUND, profile.soundEnabled)
            ?.apply()
    }

    private companion object {
        const val TAG = "MissionControlVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val KEY_CALLSIGN = "guest_callsign"
        const val KEY_TOTAL_FLIGHTS = "guest_total_flights"
        const val KEY_WINS = "guest_wins"
        const val KEY_BEST_SCORE = "guest_best_score"
        const val KEY_UNLOCKED_TIER = "guest_unlocked_tier"
        const val KEY_TILT = "guest_tilt"
        const val KEY_SOUND = "guest_sound"
    }
}
