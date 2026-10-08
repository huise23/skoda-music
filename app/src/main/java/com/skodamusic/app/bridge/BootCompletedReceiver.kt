package com.skodamusic.app.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackService

/**
 * BroadcastReceiver for system BOOT_COMPLETED.
 * Starts PlaybackService and CarBridge on head-unit startup.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootCompletedReceiver"
        const val PREFS_BOOT = "boot_prefs"
        const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences(PREFS_BOOT, Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, true)
            if (!autoStart) {
                Log.i(TAG, "Auto-start on boot is disabled by user setting")
                return
            }

            try {
                val serviceIntent = Intent(context, PlaybackService::class.java).apply {
                    this.action = PlaybackActions.ACTION_SERVICE_INIT
                }
                context.startService(serviceIntent)
                Log.i(TAG, "Successfully started PlaybackService on boot")

                // Start CarBridgeServer
                CarBridgeManager.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service on boot: ${e.message}", e)
            }
        }
    }
}
