package com.skodamusic.app.bridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * Hardware voice service to intercept startService() calls from AutoChips / Yecon / JSBD firmware.
 *
 * Firmware invokes context.startService(Intent("com.jsbd.vr.start.action")) when the steering wheel
 * voice button is pressed. Android does not deliver startService calls to BroadcastReceivers,
 * making this Service essential for physical steering wheel button events.
 *
 * Fully compatible with Android API 17 (Jelly Bean 4.2.2).
 */
class CarVoiceService : Service() {
    companion object {
        private const val TAG = "CarVoiceService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "SERVICE_START"
        Log.i(TAG, "Intercepted hardware voice service intent: $action, extras=${intent?.extras}")
        CarVoiceFeedbackManager.onVoiceTriggered(applicationContext, "SERVICE($action)")
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
