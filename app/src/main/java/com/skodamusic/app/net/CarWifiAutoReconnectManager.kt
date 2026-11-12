package com.skodamusic.app.net

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.DataOutputStream

/**
 * Auto-reconnect manager for Car Wi-Fi & Bluetooth coexistence.
 *
 * Root Cause on AutoChips AC8317 / Yecon (Android 4.2.2):
 * The head unit combo chip (RTL8723 / MT6620 / MT6622) shares a single 2.4GHz antenna.
 * When the driver gets in the car, Bluetooth connects immediately. If the phone's hotspot
 * starts moments later, the Wi-Fi baseband often enters a scan-starved / dormant state,
 * failing to send probe requests ("连不上且搜不到").
 *
 * This manager detects Bluetooth connect events, allows a short grace period (8s) for
 * the phone hotspot to become active, and if still disconnected, performs an automated
 * radio soft-reset (`setWifiEnabled(false) -> true` + `startScan()` + `reconnect()`),
 * breaking the coexistence deadlock and restoring immediate connectivity.
 */
object CarWifiAutoReconnectManager {
    private const val TAG = "CarWifiAutoReconnect"
    private const val PREFS_NAME = "car_wifi_prefs"
    const val KEY_AUTO_RECONNECT_ENABLED = "key_wifi_auto_reconnect_enabled"

    private const val BT_CONNECT_GRACE_PERIOD_MS = 8000L
    private const val RESET_COOLDOWN_MS = 25000L
    private const val WAKEUP_CHECK_DELAY_MS = 9000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isRegistered = false
    private var lastResetTimestampMs = 0L

    private val delayedCheckRunnable = Runnable {
        appContext?.let { ctx ->
            checkAndHealIfDisconnected(ctx, "bluetooth_post_delay")
        }
    }

    private var appContext: Context? = null

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            val action = intent?.action ?: return
            Log.d(TAG, "Radio broadcast received: $action")

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    Log.i(TAG, "Bluetooth ACL connected. Scheduling 8s Wi-Fi verification check.")
                    scheduleDelayedCheck()
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.STATE_DISCONNECTED)
                    if (state == BluetoothAdapter.STATE_CONNECTED) {
                        Log.i(TAG, "Bluetooth adapter state connected. Scheduling 8s Wi-Fi verification check.")
                        scheduleDelayedCheck()
                    }
                }
                ConnectivityManager.CONNECTIVITY_ACTION,
                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                    if (isWifiConnected(context)) {
                        Log.d(TAG, "Wi-Fi is connected, canceling any pending healing checks.")
                        cancelDelayedCheck()
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (isRegistered) {
            return
        }
        val app = context.applicationContext
        appContext = app

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        try {
            app.registerReceiver(radioReceiver, filter)
            isRegistered = true
            Log.i(TAG, "CarWifiAutoReconnectManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register radio receiver: ${e.message}", e)
        }
    }

    fun release() {
        if (!isRegistered) {
            return
        }
        cancelDelayedCheck()
        try {
            appContext?.unregisterReceiver(radioReceiver)
        } catch (_: Exception) {}
        isRegistered = false
        appContext = null
        Log.i(TAG, "CarWifiAutoReconnectManager released")
    }

    fun onWakeup(context: Context) {
        val app = context.applicationContext
        appContext = app
        mainHandler.postDelayed({
            checkAndHealIfDisconnected(app, "car_hot_boot_wakeup")
        }, WAKEUP_CHECK_DELAY_MS)
    }

    fun isAutoReconnectEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_RECONNECT_ENABLED, true)
    }

    fun setAutoReconnectEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT_ENABLED, enabled).apply()
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeInfo: NetworkInfo? = cm.activeNetworkInfo
        return activeInfo != null && activeInfo.isConnected && activeInfo.type == ConnectivityManager.TYPE_WIFI
    }

    fun isWifiEnabled(context: Context): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return false
        return wm.isWifiEnabled
    }

    private fun scheduleDelayedCheck() {
        cancelDelayedCheck()
        mainHandler.postDelayed(delayedCheckRunnable, BT_CONNECT_GRACE_PERIOD_MS)
    }

    private fun cancelDelayedCheck() {
        mainHandler.removeCallbacks(delayedCheckRunnable)
    }

    private fun checkAndHealIfDisconnected(context: Context, triggerSource: String) {
        if (!isAutoReconnectEnabled(context)) {
            Log.i(TAG, "Auto reconnect is disabled in settings. Skipping check ($triggerSource).")
            return
        }

        if (isWifiConnected(context)) {
            Log.i(TAG, "Wi-Fi is already connected. No action required ($triggerSource).")
            return
        }

        if (!isWifiEnabled(context)) {
            Log.i(TAG, "Wi-Fi is explicitly powered off. Enabling Wi-Fi...")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.setWifiEnabled(true)
            wm?.startScan()
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastResetTimestampMs < RESET_COOLDOWN_MS) {
            Log.w(TAG, "Wi-Fi reset cooldown active (${(now - lastResetTimestampMs) / 1000}s ago). Triggering scan only.")
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.startScan()
            wm?.reconnect()
            return
        }

        Log.i(TAG, "Wi-Fi disconnected and scan-deadlocked ($triggerSource). Triggering radio soft reset...")
        performSoftReset(context, triggerSource, null)
    }

    /**
     * Manual one-touch Wi-Fi reset triggered by user in Settings.
     */
    fun manualResetWifi(context: Context, callback: ((Boolean) -> Unit)? = null) {
        val app = context.applicationContext
        performSoftReset(app, "user_manual_request", callback)
    }

    private fun performSoftReset(context: Context, reason: String, callback: ((Boolean) -> Unit)?) {
        lastResetTimestampMs = System.currentTimeMillis()
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        Thread {
            var success = false
            try {
                Log.i(TAG, "Executing Wi-Fi reset (reason: $reason)...")
                wm?.setWifiEnabled(false)
                Thread.sleep(800)

                wm?.setWifiEnabled(true)
                Thread.sleep(1200)

                wm?.startScan()
                wm?.reconnect()

                // Optional booster via root svc command if available on head unit
                executeRootSvcWifiKick()

                // Second scan pulse after 4s
                Thread.sleep(4000)
                wm?.startScan()
                success = true
                Log.i(TAG, "Wi-Fi reset completed successfully for reason: $reason")
            } catch (e: Exception) {
                Log.e(TAG, "Error performing Wi-Fi soft reset: ${e.message}", e)
            } finally {
                mainHandler.post {
                    callback?.invoke(success)
                }
            }
        }.start()
    }

    private fun executeRootSvcWifiKick() {
        try {
            val process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes("svc wifi enable\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor()
        } catch (_: Exception) {
            // Root svc is an optional booster; failure is completely benign
        }
    }
}
