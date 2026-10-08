package com.skodamusic.app.bridge

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Manages CarBridge lifecycle and outgoing wake requests to the phone (Yiyu App).
 * Thread-safe and compatible with Android API 17.
 */
object CarBridgeManager {
    private const val TAG = "CarBridgeManager"
    private const val PREFS_NAME = "car_bridge_prefs"
    private const val KEY_PHONE_IP = "phone_ip"
    private const val KEY_PHONE_PORT = "phone_port"
    private const val KEY_BRIDGE_ENABLED = "bridge_enabled"

    // Default Android Wi-Fi hotspot gateway IP
    const val DEFAULT_PHONE_IP = "192.168.43.1"
    const val DEFAULT_PHONE_PORT = 8999
    const val DEFAULT_SERVER_PORT = 8088

    private var server: CarBridgeServer? = null
    private val executor = Executors.newSingleThreadExecutor()

    fun start(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_BRIDGE_ENABLED, true)
        if (!enabled) {
            Log.i(TAG, "CarBridge is disabled in preferences")
            return
        }

        if (server == null) {
            val s = CarBridgeServer(context.applicationContext, DEFAULT_SERVER_PORT)
            s.start()
            server = s
            Log.i(TAG, "CarBridgeManager initialized and server started")
        }
    }

    fun stop() {
        server?.stop()
        server = null
        Log.i(TAG, "CarBridgeManager stopped")
    }

    /**
     * Dynamically resolves the phone hotspot gateway IP from Wi-Fi DHCP info.
     * Compatible with Android API 17+.
     */
    fun resolveHotspotGatewayIp(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val dhcp = wifiManager?.dhcpInfo
            if (dhcp != null && dhcp.gateway != 0) {
                val g = dhcp.gateway
                String.format(
                    java.util.Locale.US,
                    "%d.%d.%d.%d",
                    g and 0xFF,
                    (g shr 8) and 0xFF,
                    (g shr 16) and 0xFF,
                    (g shr 24) and 0xFF
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve Wi-Fi DHCP gateway: ${e.message}")
            null
        }
    }

    /**
     * Notify phone (Yiyu App) to wake up and start listening to user's voice command.
     * Called when steering-wheel voice key or UI voice button is pressed.
     */
    fun notifyVoiceTriggered(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dynamicGateway = resolveHotspotGatewayIp(context)
        val phoneIp = dynamicGateway
            ?: prefs.getString(KEY_PHONE_IP, DEFAULT_PHONE_IP)
            ?: DEFAULT_PHONE_IP
        val phonePort = prefs.getInt(KEY_PHONE_PORT, DEFAULT_PHONE_PORT)

        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val targetUrl = "http://$phoneIp:$phonePort/api/agent/wake"
                val url = URL(targetUrl)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 3000
                    readTimeout = 3000
                    useCaches = false
                }
                val code = conn.responseCode
                Log.i(TAG, "Notified phone agent at $targetUrl (dynamic=$dynamicGateway), response code: $code")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to wake phone agent at $phoneIp:$phonePort: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
    }

    fun setPhoneIp(context: Context, ip: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PHONE_IP, ip.trim())
            .apply()
    }

    fun getPhoneIp(context: Context): String {
        return resolveHotspotGatewayIp(context)
            ?: context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_PHONE_IP, DEFAULT_PHONE_IP)
            ?: DEFAULT_PHONE_IP
    }
}
