package com.skodamusic.app.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for intercepting steering wheel voice key events
 * and hardware voice intents on AutoChips / Yecon head unit.
 */
class CarVoiceButtonReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CarVoiceButtonReceiver"
        const val ACTION_YECON_VOICE_START = "com.yecon.action.VOICE_START"
        const val ACTION_JSBD_VR_START = "com.jsbd.vr.start.action"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "CarVoiceButtonReceiver intercepted action: $action")

        when (action) {
            ACTION_YECON_VOICE_START,
            ACTION_JSBD_VR_START,
            Intent.ACTION_VOICE_COMMAND -> {
                // Ensure CarBridgeManager is started
                CarBridgeManager.start(context)

                // Notify phone (Yiyu App) to wake up and start listening
                CarBridgeManager.notifyVoiceTriggered(context)
            }
        }
    }
}
