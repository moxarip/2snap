package com.example.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.WakePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restores DoubleTapWakeService automatically after device reboot (or app update)
 * when both "Enabled" and "Enable on boot" are turned on.
 *
 * Safely catches Android 12–15 ForegroundServiceStartNotAllowedException on strict OEM ROMs
 * and shows a gentle tap-to-resume notification instead of crashing.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = WakePreferences(appContext).getSnapshot()
                if (settings.isServiceEnabled && settings.enableOnBoot) {
                    try {
                        DoubleTapWakeService.start(appContext)
                    } catch (e: RuntimeException) {
                        // Handles ForegroundServiceStartNotAllowedException on API 31+ / strict OEMs
                        Log.w(TAG, "OS blocked auto-start from boot: ${e.message}")
                        postTapToResumeNotification(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postTapToResumeNotification(context: Context) {
        try {
            DoubleTapWakeService.ensureNotificationChannel(context)
            val openIntent = PendingIntent.getActivity(
                context,
                2,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, DoubleTapWakeService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_tap)
                .setContentTitle(context.getString(R.string.notif_boot_blocked_title))
                .setContentText(context.getString(R.string.notif_boot_blocked_body))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openIntent)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(DoubleTapWakeService.BOOT_BLOCKED_NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
