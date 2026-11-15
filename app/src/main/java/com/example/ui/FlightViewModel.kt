package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MissionDefinition
import com.example.game.FlightPhase
import com.example.game.LanderPhysicsEngine
import com.example.game.LanderSimState
import com.example.game.SoundSynthesizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FlightViewModel(
    initialMission: MissionDefinition,
    soundEnabledInitially: Boolean,
    private val onFlightFinished: (LanderSimState) -> Unit,
) : ViewModel() {

    private val physics = LanderPhysicsEngine()
    private val soundSynth = SoundSynthesizer()

    private val _simState = MutableStateFlow(physics.createInitialState(initialMission, sasEnabled = true))
    val simState: StateFlow<LanderSimState> = _simState.asStateFlow()

    @Volatile private var rcsInput: Float = 0f
    @Volatile private var tiltTargetRad: Float? = null
    @Volatile private var outcomeDispatched = false

    private var simJob: Job? = null

    init {
        soundSynth.setEnabled(soundEnabledInitially)
        soundSynth.start()
        startLoop()
    }

    private fun startLoop() {
        simJob?.cancel()
        simJob = viewModelScope.launch {
            var lastNanos = System.nanoTime()
            while (isActive) {
                delay(16L)
                val now = System.nanoTime()
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0.008f, 0.032f)
                lastNanos = now

                val current = _simState.value
                if (current.phase == FlightPhase.FLYING) {
                    val next = physics.step(
                        state = current,
                        dtSeconds = dt,
                        rcsInput = rcsInput,
                        tiltAngleTargetRad = tiltTargetRad,
                    )
                    _simState.value = next

                    val lowAltDanger = next.altitudeAgl < 45f && next.vy < -(next.mission.safeVerticalSpeed * 1.15f)
                    soundSynth.updateTelemetry(
                        throttle = next.actualThrottle,
                        rcs = kotlin.math.abs(rcsInput) > 0.1f,
                        lowAltDanger = lowAltDanger,
                    )

                    if ((next.phase == FlightPhase.LANDED_SAFE || next.phase == FlightPhase.CRASHED) && !outcomeDispatched) {
                        outcomeDispatched = true
                        soundSynth.updateTelemetry(0f, rcs = false, lowAltDanger = false)
                        onFlightFinished(next)
                    }
                } else {
                    soundSynth.updateTelemetry(0f, rcs = false, lowAltDanger = false)
                    if (current.phase != FlightPhase.PAUSED && current.particles.isNotEmpty()) {
                        _simState.value = physics.step(current, dt, 0f, null)
                    }
                }
            }
        }
    }

    fun setTargetThrottle(throttle: Float) {
        val clamped = throttle.coerceIn(0f, 1f)
        _simState.update { state ->
            if (state.phase == FlightPhase.FLYING) state.copy(targetThrottle = clamped) else state
        }
    }

    fun setRcsInput(input: Float) {
        rcsInput = input.coerceIn(-1f, 1f)
    }

    fun setTiltSteeringAngle(angleRad: Float?) {
        tiltTargetRad = angleRad
    }

    fun toggleSas() {
        _simState.update { it.copy(sasEnabled = !it.sasEnabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundSynth.setEnabled(enabled)
    }

    fun togglePause() {
        _simState.update { state ->
            when (state.phase) {
                FlightPhase.FLYING -> state.copy(phase = FlightPhase.PAUSED)
                FlightPhase.PAUSED -> state.copy(phase = FlightPhase.FLYING)
                else -> state
            }
        }
    }

    fun restartMission(mission: MissionDefinition = _simState.value.mission) {
        outcomeDispatched = false
        rcsInput = 0f
        _simState.value = physics.createInitialState(mission, sasEnabled = _simState.value.sasEnabled)
    }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
        soundSynth.stop()
    }
}
