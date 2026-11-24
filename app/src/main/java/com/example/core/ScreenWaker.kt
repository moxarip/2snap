package com.example.core

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

sealed interface WakeResult {
    data object WokeLockedScreen : WakeResult
    data object WokeUnlockedScreen : WakeResult
    data object AlreadyInteractive : WakeResult
    data class Failed(val message: String) : WakeResult
}

/**
 * Official Android screen-wake helper using PowerManager and KeyguardManager.
 *
 * Acquires a brief ACQUIRE_CAUSES_WAKEUP WakeLock with a strict hard timeout and releases it
 * immediately after triggering the OS display wake pipeline, handing normal screen-off timing
 * back to the system via ON_AFTER_RELEASE. Never keeps the screen awake permanently and never
 * bypasses lock screen security.
 */
object ScreenWaker {

    private const val TAG = "DoubleTapScreenWaker"
    private const val WAKELOCK_TAG = "DoubleTapWake::ScreenWake"
    private const val HARD_TIMEOUT_MS = 1200L
    private const val IMMEDIATE_RELEASE_DELAY_MS = 350L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun wakeScreen(context: Context): WakeResult {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return WakeResult.Failed("PowerManager unavailable")

        if (powerManager.isInteractive) {
            return WakeResult.AlreadyInteractive
        }

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked ?: false

        return try {
            // SCREEN_BRIGHT_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP is the only official non-Activity API
            // to wake an off display from a background Service without launching a hidden Activity.
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                WAKELOCK_TAG
            ).apply {
                setReferenceCounted(false)
            }

            wakeLock.acquire(HARD_TIMEOUT_MS)

            // Release promptly once the OS display wake signal has been committed.
            mainHandler.postDelayed(
                {
                    try {
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Safe wakeLock release notice: ${e.message}")
                    }
                },
                IMMEDIATE_RELEASE_DELAY_MS
            )

            if (isLocked) WakeResult.WokeLockedScreen else WakeResult.WokeUnlockedScreen
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing WAKE_LOCK permission", e)
            WakeResult.Failed(e.message ?: "SecurityException")
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to wake screen", e)
            WakeResult.Failed(e.message ?: "RuntimeException")
        }
    }
}
