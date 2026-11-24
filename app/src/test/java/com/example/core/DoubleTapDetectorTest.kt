package com.example.core

import com.example.data.TapSensitivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DoubleTapDetectorTest {

    @Test
    fun linearAcceleration_detectsCleanDoubleTap() {
        val detector = DoubleTapDetector(
            usesLinearAcceleration = true,
            initialSensitivity = TapSensitivity.MEDIUM,
        )

        var timeMs = 1000L
        // Warmup + calm baseline samples
        repeat(8) {
            val res = detector.processSample(0f, 0f, 0.1f, timeMs)
            assertEquals(TapDetectionEvent.None, res.event)
            timeMs += 20L
        }

        // First crisp tap (Z impulse = 5.5 m/s^2 -> weighted ~4.67 >= 3.6)
        val tap1 = detector.processSample(0.1f, 0.1f, 5.5f, timeMs)
        assertTrue(tap1.event is TapDetectionEvent.FirstTapDetected)
        timeMs += 20L

        // Quiet settling gap of 220ms
        repeat(11) {
            val res = detector.processSample(0f, 0f, 0.1f, timeMs)
            assertEquals(TapDetectionEvent.None, res.event)
            timeMs += 20L
        }

        // Second crisp tap
        val tap2 = detector.processSample(0.1f, 0.1f, 5.2f, timeMs)
        assertTrue(tap2.event is TapDetectionEvent.DoubleTapDetected)
    }

    @Test
    fun rawAccelerometer_filtersStaticGravityAndDetectsDoubleTap() {
        val detector = DoubleTapDetector(
            usesLinearAcceleration = false,
            initialSensitivity = TapSensitivity.MEDIUM,
        )

        var timeMs = 2000L
        // Static 9.81 m/s^2 gravity on Z axis must NOT false-trigger
        repeat(12) {
            val res = detector.processSample(0f, 0f, 9.81f, timeMs)
            assertEquals(TapDetectionEvent.None, res.event)
            timeMs += 20L
        }

        // First tap adds +6.5 m/s^2 spike above gravity
        val tap1 = detector.processSample(0f, 0f, 9.81f + 6.5f, timeMs)
        assertTrue(tap1.event is TapDetectionEvent.FirstTapDetected)
        timeMs += 20L

        // Return to resting gravity for 240ms
        repeat(12) {
            val res = detector.processSample(0f, 0f, 9.81f, timeMs)
            assertEquals(TapDetectionEvent.None, res.event)
            timeMs += 20L
        }

        // Second tap spike
        val tap2 = detector.processSample(0f, 0f, 9.81f + 6.5f, timeMs)
        assertTrue(tap2.event is TapDetectionEvent.DoubleTapDetected)
    }

    @Test
    fun continuousShaking_isRejectedAndDoesNotTriggerDoubleTap() {
        val detector = DoubleTapDetector(
            usesLinearAcceleration = true,
            initialSensitivity = TapSensitivity.MEDIUM,
        )

        var timeMs = 3000L
        repeat(8) {
            detector.processSample(0f, 0f, 0f, timeMs)
            timeMs += 20L
        }

        var doubleTapTriggered = false
        var noiseRejected = false

        // Simulate sustained shaking (>120ms of uninterrupted high acceleration)
        repeat(10) {
            val res = detector.processSample(3f, 3f, 6.0f, timeMs)
            if (res.event is TapDetectionEvent.DoubleTapDetected) doubleTapTriggered = true
            if (res.event is TapDetectionEvent.NoiseRejected) noiseRejected = true
            timeMs += 20L
        }

        assertTrue(noiseRejected)
        assertTrue(!doubleTapTriggered)
    }

    @Test
    fun lowSensitivity_ignoresLightTaps() {
        val detector = DoubleTapDetector(
            usesLinearAcceleration = true,
            initialSensitivity = TapSensitivity.LOW,
        )

        var timeMs = 4000L
        repeat(8) {
            detector.processSample(0f, 0f, 0f, timeMs)
            timeMs += 20L
        }

        // Light tap (3.5 m/s^2 -> weighted ~2.97 < 5.4 LOW threshold)
        val lightTap = detector.processSample(0f, 0f, 3.5f, timeMs)
        assertEquals(TapDetectionEvent.None, lightTap.event)
    }
}
