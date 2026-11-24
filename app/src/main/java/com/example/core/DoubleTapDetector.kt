package com.example.core

import com.example.data.TapSensitivity
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Result of processing a single accelerometer / linear-acceleration sample.
 */
sealed interface TapDetectionEvent {
    data object None : TapDetectionEvent
    data class FirstTapDetected(val impulseMs2: Float) : TapDetectionEvent
    data class DoubleTapDetected(val impulseMs2: Float) : TapDetectionEvent
    data object NoiseRejected : TapDetectionEvent
}

data class TapSampleResult(
    val event: TapDetectionEvent,
    val impulseMs2: Float,
)

/**
 * Pure, low-allocation signal processor that recognizes two crisp physical taps on the phone
 * while filtering out gravity, single-tap ringdown, slow handling, and continuous pocket shaking.
 *
 * Expects monotonically increasing timestamps in milliseconds.
 */
class DoubleTapDetector(
    private val usesLinearAcceleration: Boolean,
    initialSensitivity: TapSensitivity = TapSensitivity.MEDIUM,
) {
    private enum class State {
        IDLE,
        FIRST_TAP_SETTLING,
        AWAITING_SECOND_TAP,
        COOLDOWN,
    }

    @Volatile
    private var sensitivity: TapSensitivity = initialSensitivity

    private var state: State = State.IDLE
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var gravityInitialized = false
    private var warmupSamplesRemaining = WARMUP_SAMPLE_COUNT

    private var firstTapTimeMs = 0L
    private var firstTapHighEnergyDurationMs = 0L
    private var lastSampleTimeMs = 0L
    private var cooldownUntilMs = 0L
    private var quietBaselineCounter = 0

    fun updateSensitivity(newSensitivity: TapSensitivity) {
        sensitivity = newSensitivity
    }

    fun reset() {
        state = State.IDLE
        gravityInitialized = false
        warmupSamplesRemaining = WARMUP_SAMPLE_COUNT
        firstTapTimeMs = 0L
        firstTapHighEnergyDurationMs = 0L
        lastSampleTimeMs = 0L
        cooldownUntilMs = 0L
        quietBaselineCounter = 0
    }

    fun processSample(
        x: Float,
        y: Float,
        z: Float,
        timestampMs: Long,
    ): TapSampleResult {
        val dtMs = if (lastSampleTimeMs == 0L) 0L else (timestampMs - lastSampleTimeMs).coerceIn(0L, 250L)
        lastSampleTimeMs = timestampMs

        // Extract linear acceleration (remove static gravity when raw accelerometer is used)
        val linX: Float
        val linY: Float
        val linZ: Float
        if (usesLinearAcceleration) {
            linX = x
            linY = y
            linZ = z
        } else {
            if (!gravityInitialized) {
                gravityX = x
                gravityY = y
                gravityZ = z
                gravityInitialized = true
            } else {
                gravityX = GRAVITY_ALPHA * gravityX + (1f - GRAVITY_ALPHA) * x
                gravityY = GRAVITY_ALPHA * gravityY + (1f - GRAVITY_ALPHA) * y
                gravityZ = GRAVITY_ALPHA * gravityZ + (1f - GRAVITY_ALPHA) * z
            }
            linX = x - gravityX
            linY = y - gravityY
            linZ = z - gravityZ
        }

        // Ignore initial filter settling samples so registration transient never false-triggers
        if (warmupSamplesRemaining > 0) {
            warmupSamplesRemaining--
            return TapSampleResult(TapDetectionEvent.None, 0f)
        }

        // Emphasize Z-axis (perpendicular to screen glass) while retaining partial X/Y response
        val planarMag = sqrt(linX * linX + linY * linY)
        val zAbs = abs(linZ)
        val impulse = (zAbs * 0.85f) + (planarMag * 0.25f)

        val currentSensitivity = sensitivity
        val tapThreshold = currentSensitivity.impulseThresholdMs2
        val settleThreshold = currentSensitivity.settleThresholdMs2

        when (state) {
            State.COOLDOWN -> {
                if (timestampMs >= cooldownUntilMs) {
                    state = State.IDLE
                    quietBaselineCounter = 0
                }
                return TapSampleResult(TapDetectionEvent.None, impulse)
            }

            State.IDLE -> {
                if (impulse < settleThreshold) {
                    quietBaselineCounter = (quietBaselineCounter + 1).coerceAtMost(MIN_QUIET_SAMPLES_BEFORE_TAP)
                    return TapSampleResult(TapDetectionEvent.None, impulse)
                }

                // Require a brief calm baseline before tap 1 so walking/shaking is ignored
                if (impulse >= tapThreshold && quietBaselineCounter >= MIN_QUIET_SAMPLES_BEFORE_TAP) {
                    state = State.FIRST_TAP_SETTLING
                    firstTapTimeMs = timestampMs
                    firstTapHighEnergyDurationMs = 0L
                    quietBaselineCounter = 0
                    return TapSampleResult(TapDetectionEvent.FirstTapDetected(impulse), impulse)
                }

                // Elevated non-tap movement resets the calm baseline
                quietBaselineCounter = 0
                return TapSampleResult(TapDetectionEvent.None, impulse)
            }

            State.FIRST_TAP_SETTLING -> {
                val elapsed = timestampMs - firstTapTimeMs
                if (impulse >= settleThreshold) {
                    firstTapHighEnergyDurationMs += dtMs
                    // Continuous sustained energy (>95ms) indicates shaking or dropping, not a crisp tap
                    if (firstTapHighEnergyDurationMs > MAX_SINGLE_TAP_RING_MS) {
                        state = State.IDLE
                        quietBaselineCounter = 0
                        return TapSampleResult(TapDetectionEvent.NoiseRejected, impulse)
                    }
                } else if (elapsed >= MIN_TAP_INTERVAL_MS) {
                    state = State.AWAITING_SECOND_TAP
                }

                if (elapsed > MAX_TAP_INTERVAL_MS) {
                    state = State.IDLE
                    return TapSampleResult(TapDetectionEvent.None, impulse)
                }

                // Allow transitioning to ready-for-second-tap once settled & past refractory period
                if (elapsed >= MIN_TAP_INTERVAL_MS && impulse < settleThreshold) {
                    state = State.AWAITING_SECOND_TAP
                }
                return TapSampleResult(TapDetectionEvent.None, impulse)
            }

            State.AWAITING_SECOND_TAP -> {
                val elapsed = timestampMs - firstTapTimeMs
                if (elapsed > MAX_TAP_INTERVAL_MS) {
                    state = State.IDLE
                    quietBaselineCounter = if (impulse < settleThreshold) 1 else 0
                    return TapSampleResult(TapDetectionEvent.None, impulse)
                }

                if (impulse >= tapThreshold && elapsed >= MIN_TAP_INTERVAL_MS) {
                    state = State.COOLDOWN
                    cooldownUntilMs = timestampMs + POST_WAKE_COOLDOWN_MS
                    quietBaselineCounter = 0
                    return TapSampleResult(TapDetectionEvent.DoubleTapDetected(impulse), impulse)
                }

                return TapSampleResult(TapDetectionEvent.None, impulse)
            }
        }
    }

    companion object {
        private const val GRAVITY_ALPHA = 0.82f
        private const val WARMUP_SAMPLE_COUNT = 5
        private const val MIN_QUIET_SAMPLES_BEFORE_TAP = 2
        private const val MIN_TAP_INTERVAL_MS = 110L
        private const val MAX_TAP_INTERVAL_MS = 560L
        private const val MAX_SINGLE_TAP_RING_MS = 95L
        private const val POST_WAKE_COOLDOWN_MS = 1800L
    }
}
