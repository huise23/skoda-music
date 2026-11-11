package com.skodamusic.app.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for intercepting steering wheel voice key events
 * and hardware voice intents on AutoChips / Yecon head unit.
 *
 * Provides immediate feedback via CarVoiceFeedbackManager and routes
 * the trigger to the connected phone voice agent.
 */
class CarVoiceButtonReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CarVoiceButtonReceiver"

        // Yecon / AutoChips Voice Actions
        const val ACTION_YECON_VOICE_START = "com.yecon.action.VOICE_START"
        const val ACTION_YECON_VOICE_STOP = "com.yecon.action.VOICE_STOP"
        const val ACTION_YECON_ISSSR_KEY = "com.yecon.action.ISSSR_KEY"
        const val ACTION_YECON_KEY_DOWN = "com.yecon.action.KEY_DOWN"
        const val ACTION_YECON_KEY_UP = "com.yecon.action.KEY_UP"

        // JSBD VR Actions
        const val ACTION_JSBD_VR_START = "com.jsbd.vr.start.action"
        const val ACTION_JSBD_VR_START_ALT = "com.jsbd.vr.start"
        const val ACTION_JSBD_VR_SERVICE = "com.jsbd.vr.service.action"
        const val ACTION_JSBD_VR_STARTSERVICE = "com.jsbd.vr.startservice.action"

        // Android Standard Voice Actions
        const val ACTION_VOICE_COMMAND = Intent.ACTION_VOICE_COMMAND
        const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"
        const val ACTION_WEB_SEARCH = "android.speech.action.WEB_SEARCH"
        const val ACTION_VOICE_SEARCH_HANDS_FREE = "android.speech.action.VOICE_SEARCH_HANDS_FREE"
        const val ACTION_SEARCH_LONG_PRESS = "android.intent.action.SEARCH_LONG_PRESS"

        // App Internal Test Action
        const val ACTION_TEST_VOICE_TRIGGER = "com.skodamusic.app.action.TEST_VOICE_TRIGGER"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "CarVoiceButtonReceiver intercepted action: $action, extras=${intent.extras}")

        when (action) {
            ACTION_YECON_KEY_DOWN -> {
                val keyCode = intent.getIntExtra("keyCode", intent.getIntExtra("key", -1))
                // 231=KEYCODE_VOICE_ASSIST, 84=KEYCODE_SEARCH, 219=KEYCODE_ASSIST, 5=KEYCODE_CALL
                if (keyCode in listOf(231, 84, 219, 5, 110, 114, 115)) {
                    Log.i(TAG, "Intercepted voice keycode: $keyCode via KEY_DOWN")
                    CarVoiceFeedbackManager.onVoiceTriggered(context, "KEY_DOWN($keyCode)")
                    if (isOrderedBroadcast) abortBroadcast()
                } else {
                    Log.d(TAG, "Ignored non-voice KEY_DOWN keyCode: $keyCode")
                }
            }
            ACTION_YECON_VOICE_START,
            ACTION_YECON_ISSSR_KEY,
            ACTION_JSBD_VR_START,
            ACTION_JSBD_VR_START_ALT,
            ACTION_JSBD_VR_SERVICE,
            ACTION_JSBD_VR_STARTSERVICE,
            ACTION_VOICE_COMMAND,
            ACTION_VOICE_ASSIST,
            ACTION_WEB_SEARCH,
            ACTION_VOICE_SEARCH_HANDS_FREE,
            ACTION_SEARCH_LONG_PRESS,
            ACTION_TEST_VOICE_TRIGGER -> {
                Log.i(TAG, "Triggering voice feedback and phone wake for action: $action")
                CarVoiceFeedbackManager.onVoiceTriggered(context, action)
                if (isOrderedBroadcast) {
                    abortBroadcast()
                }
            }
            ACTION_YECON_VOICE_STOP -> {
                Log.d(TAG, "Ignored VOICE_STOP")
            }
            else -> {
                Log.d(TAG, "Unrecognized action in CarVoiceButtonReceiver: $action")
            }
        }
    }
}
