package com.skodamusic.app.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Dedicated single-threaded background keep-alive daemon for Car-Phone bridge.
 * Sends periodic HTTP heartbeats to Yiyu on the phone (http://$phoneIp:8999/api/agent/ping)
 * every 6 seconds to prevent the phone's 15-second timeout from dropping the connection.
 *
 * Thread-safe and compatible with Android API 17 (Jelly Bean 4.2.2).
 */
object CarBridgeHeartbeatManager {
    private const val TAG = "CarBridgeHeartbeat"
    private const val PING_INTERVAL_SECONDS = 6L
    private const val PING_TIMEOUT_MS = 2500

    data class HeartbeatState(
        val isConnected: Boolean = false,
        val phoneIp: String = "",
        val latencyMs: Long = -1L,
        val lastPingTime: Long = 0L,
        val pingCount: Long = 0L,
        val consecutiveFailures: Int = 0,
        val lastError: String? = null
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(HeartbeatState) -> Unit>()
    private val isRunning = AtomicBoolean(false)

    private var scheduler: ScheduledExecutorService? = null
    private var scheduledTask: ScheduledFuture<*>? = null
    private var appContext: Context? = null

    @Volatile
    private var currentState = HeartbeatState()

    fun getState(): HeartbeatState = currentState

    fun isConnected(): Boolean = currentState.isConnected

    fun addListener(listener: (HeartbeatState) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            mainHandler.post { listener(currentState) }
        }
    }

    fun removeListener(listener: (HeartbeatState) -> Unit) {
        listeners.remove(listener)
    }

    @Synchronized
    fun start(context: Context) {
        appContext = context.applicationContext
        if (isRunning.getAndSet(true)) {
            return
        }

        Log.i(TAG, "Starting CarBridgeHeartbeatManager keep-alive thread (interval=${PING_INTERVAL_SECONDS}s)")
        val exec = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "CarBridge-HeartbeatDaemon").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }
        scheduler = exec

        scheduledTask = exec.scheduleWithFixedDelay(
            { performPing() },
            1L,
            PING_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        )
    }

    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping CarBridgeHeartbeatManager")
        scheduledTask?.cancel(true)
        scheduledTask = null
        scheduler?.shutdownNow()
        scheduler = null
    }

    /**
     * Forces an immediate ping on background thread (e.g. after Wi-Fi reconnect or voice key press).
     */
    fun pingNow(context: Context) {
        appContext = context.applicationContext
        scheduler?.execute {
            performPing()
        }
    }

    private fun performPing() {
        val ctx = appContext ?: return
        val phoneIp = CarBridgeManager.getPhoneIp(ctx)
        val phonePort = CarBridgeManager.DEFAULT_PHONE_PORT

        val startTime = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        try {
            val targetUrl = "http://$phoneIp:$phonePort/api/agent/ping"
            val url = URL(targetUrl)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = PING_TIMEOUT_MS
                readTimeout = PING_TIMEOUT_MS
                useCaches = false
            }

            val code = conn.responseCode
            val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)

            if (code in 200..299) {
                val newState = HeartbeatState(
                    isConnected = true,
                    phoneIp = phoneIp,
                    latencyMs = latency,
                    lastPingTime = System.currentTimeMillis(),
                    pingCount = currentState.pingCount + 1,
                    consecutiveFailures = 0,
                    lastError = null
                )
                currentState = newState
                notifyListeners(newState)
            } else {
                handlePingFailure(phoneIp, "HTTP $code")
            }
        } catch (e: Exception) {
            handlePingFailure(phoneIp, "${e.javaClass.simpleName}: ${e.message ?: "timeout"}")
        } finally {
            conn?.disconnect()
        }
    }

    private fun handlePingFailure(phoneIp: String, reason: String) {
        val failures = currentState.consecutiveFailures + 1
        val connected = if (failures >= 2) false else currentState.isConnected
        val newState = currentState.copy(
            isConnected = connected,
            phoneIp = phoneIp,
            consecutiveFailures = failures,
            lastError = reason
        )
        currentState = newState
        notifyListeners(newState)
    }

    private fun notifyListeners(state: HeartbeatState) {
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener(state)
                } catch (e: Exception) {
                    Log.w(TAG, "Listener error: ${e.message}")
                }
            }
        }
    }
}
