package com.skodamusic.app.bridge

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Transparent fallback activity to intercept startActivity() calls from firmware
 * when steering wheel voice button is pressed.
 *
 * Fully compatible with Android API 17 (Jelly Bean 4.2.2).
 */
class CarVoiceActivity : Activity() {
    companion object {
        private const val TAG = "CarVoiceActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent?.action ?: "ACTIVITY_START"
        Log.i(TAG, "Intercepted hardware voice activity intent: $action, extras=${intent?.extras}")
        CarVoiceFeedbackManager.onVoiceTriggered(applicationContext, "ACTIVITY($action)")
        finish()
    }
}
