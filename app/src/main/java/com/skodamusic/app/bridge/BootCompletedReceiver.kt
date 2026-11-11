package com.skodamusic.app.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.skodamusic.app.MainActivity
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackService
import com.skodamusic.app.playback.PlaybackStateStore

/**
 * BroadcastReceiver for system BOOT_COMPLETED and AutoChips / Yecon platform wakeups.
 * Starts PlaybackService, CarBridge, and launches MainActivity in background dock mode.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
        const val PREFS_BOOT = "boot_prefs"
        const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
        const val KEY_BOOT_START_MODE = "boot_start_mode"
        const val BOOT_MODE_DOCK = "dock"
        const val BOOT_MODE_FOREGROUND = "foreground"

        val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "autochips.intent.action.RESUME_APPS",
            "autochips.intent.action.QB_SERVICE",
            "com.autochips.quickboot.poweron",
            "com.autochips.action.RESUME_APPS",
            "com.yecon.action.ACC_ON",
            "com.yecon.action.RESUME_APPS"
        )
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast action: $action")

        if (BOOT_ACTIONS.contains(action)) {
            val prefs = context.getSharedPreferences(PREFS_BOOT, Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, true)
            if (!autoStart) {
                Log.i(TAG, "Auto-start on boot is disabled by user setting")
                return
            }

            try {
                // Reset dismissed-by-user flag so overlay dock is available on fresh car startup
                val stateStore = PlaybackStateStore(context)
                stateStore.setOverlayDismissedByUser(false)

                // 1. Start PlaybackService
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    this.action = PlaybackActions.ACTION_SERVICE_INIT
                }
                context.startService(serviceIntent)
                Log.i(TAG, "Successfully started PlaybackService on boot")

                // 2. Start CarBridgeServer
                CarBridgeManager.start(context)

                // 3. Launch MainActivity with EXTRA_BOOT_START
                val activityIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(MainActivity.EXTRA_BOOT_START, true)
                }
                context.startActivity(activityIntent)
                Log.i(TAG, "Successfully triggered MainActivity boot startup")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to execute boot startup: ${e.message}", e)
            }
        }
    }
}
