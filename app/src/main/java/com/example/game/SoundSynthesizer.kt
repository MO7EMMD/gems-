package com.example.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural real-time spacecraft audio synthesizer using Android AudioTrack.
 * Generates thruster combustion rumble, RCS cold-gas hiss, and altitude warning chimes
 * without requiring external sound files or microphone/storage permissions.
 */
class SoundSynthesizer {

    @Volatile private var enabled: Boolean = true
    @Volatile private var throttleLevel: Float = 0f
    @Volatile private var rcsActive: Boolean = false
    @Volatile private var warningBeep: Boolean = false

    private val running = AtomicBoolean(false)
    private var audioThread: Thread? = null

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        if (!isEnabled) {
            throttleLevel = 0f
            rcsActive = false
            warningBeep = false
        }
    }

    fun updateTelemetry(throttle: Float, rcs: Boolean, lowAltDanger: Boolean) {
        throttleLevel = throttle.coerceIn(0f, 1f)
        rcsActive = rcs
        warningBeep = lowAltDanger
    }

    fun start() {
        if (running.getAndSet(true)) return
        audioThread = thread(start = true, isDaemon = true, name = "LanderAudioSynth") {
            val sampleRate = 22050
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(1024)

            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Throwable) {
                running.set(false)
                return@thread
            }

            try {
                track.play()
                val chunkSamples = 512
                val buffer = ShortArray(chunkSamples)
                val rng = Random(42)
                var lpNoise = 0f
                var rumblePhase = 0f
                var warnPhase = 0f
                var warnTimer = 0f

                while (running.get()) {
                    val isOn = enabled
                    val thr = if (isOn) throttleLevel else 0f
                    val rcs = isOn && rcsActive
                    val warn = isOn && warningBeep

                    for (i in 0 until chunkSamples) {
                        var sample = 0f

                        if (thr > 0.02f) {
                            val white = rng.nextFloat() * 2f - 1f
                            lpNoise += 0.09f * (white - lpNoise)
                            rumblePhase += (2f * PI.toFloat() * (46f + thr * 34f)) / sampleRate
                            if (rumblePhase > 2f * PI.toFloat()) rumblePhase -= 2f * PI.toFloat()
                            val subHarmonic = sin(rumblePhase) * 0.35f
                            sample += (lpNoise * 0.78f + subHarmonic) * (0.25f + thr * 0.55f)
                        }

                        if (rcs) {
                            val hiss = (rng.nextFloat() * 2f - 1f) * 0.16f
                            sample += hiss
                        }

                        if (warn) {
                            warnTimer += 1f / sampleRate
                            if ((warnTimer % 0.45f) < 0.14f) {
                                warnPhase += (2f * PI.toFloat() * 880f) / sampleRate
                                if (warnPhase > 2f * PI.toFloat()) warnPhase -= 2f * PI.toFloat()
                                sample += sin(warnPhase) * 0.14f
                            }
                        } else {
                            warnTimer = 0f
                        }

                        val clamped = (sample * 18000f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        buffer[i] = clamped.toShort()
                    }
                    track.write(buffer, 0, chunkSamples)
                }
            } catch (_: Throwable) {
                // Ignore audio shutdown race
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        audioThread?.interrupt()
        audioThread = null
    }
}
