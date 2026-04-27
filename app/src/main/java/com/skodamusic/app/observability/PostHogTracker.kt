package com.skodamusic.app.observability

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object PostHogTracker {
    enum class Priority {
        LOW,
        NORMAL,
        HIGH
    }

    private const val TAG = "SkodaPostHog"
    private const val CLIENT_TIMEOUT_MS = 3000L
    private const val DEFAULT_COALESCE_MS = 10_000L
    private const val ERROR_COALESCE_MS = 30_000L
    private const val MAX_CACHE_KEYS = 256
    private const val SESSION_BUDGET_NORMAL = 80
    private const val SESSION_BUDGET_TOTAL = 150
    private const val MAX_STRING_LENGTH = 256
    private const val CONFIG_WARN_COOLDOWN_MS = 60_000L
    private const val PREFS_RUNTIME = "posthog_runtime"
    private const val KEY_DEVICE_ID = "device_id"

    private val jsonMediaType: MediaType = MediaType.parse("application/json; charset=utf-8")
        ?: throw IllegalStateException("json media type parse failed")

    private val sensitiveKeyFragments = arrayOf(
        "password", "passwd", "token", "authorization", "header", "response_body", "raw_body"
    )

    private val lock = Any()
    private val lastSentAtMs = LinkedHashMap<String, Long>()
    private val worker = Executors.newSingleThreadExecutor()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    @Volatile private var appContext: Context? = null
    @Volatile private var deviceId: String = ""
    @Volatile private var appVersion: String = "unknown"
    @Volatile private var buildNumber: Long = -1L
    @Volatile private var sessionId: String = ""
    @Volatile private var normalEventCount: Int = 0
    @Volatile private var totalEventCount: Int = 0
    @Volatile private var lastConfigWarnAtMs: Long = 0L

    fun startNewSession(context: Context, launchSource: String): String {
        ensureInitialized(context)
        synchronized(lock) {
            sessionId = buildSessionId(launchSource)
            normalEventCount = 0
            totalEventCount = 0
            return sessionId
        }
    }

    fun capture(
        context: Context,
        eventName: String,
        properties: Map<String, Any?> = emptyMap(),
        priority: Priority = Priority.NORMAL
    ) {
        if (eventName.isBlank()) {
            return
        }
        ensureInitialized(context)
        val safeContext = appContext ?: return
        val config = PostHogConfigStore.read(safeContext)
        if (!PostHogConfigStore.hasEffectiveConfig(config)) {
            maybeWarnMissingConfig()
            return
        }

        val accepted = synchronized(lock) {
            if (!withinBudget(priority)) {
                false
            } else {
                val nowMs = SystemClock.elapsedRealtime()
                val key = buildCoalesceKey(eventName, properties)
                val windowMs = if (isErrorLike(eventName, properties)) ERROR_COALESCE_MS else DEFAULT_COALESCE_MS
                val lastMs = lastSentAtMs[key] ?: 0L
                if (nowMs - lastMs < windowMs) {
                    false
                } else {
                    lastSentAtMs[key] = nowMs
                    trimCoalesceKeys()
                    totalEventCount += 1
                    if (priority != Priority.HIGH) {
                        normalEventCount += 1
                    }
                    true
                }
            }
        }
        if (!accepted) {
            return
        }

        val payload = buildPayload(
            config = config,
            eventName = eventName,
            properties = properties
        ) ?: return
        val url = buildCaptureUrl(config.host) ?: return
        worker.execute {
            send(url, payload, eventName)
        }
    }

    private fun ensureInitialized(context: Context) {
        if (appContext != null) {
            return
        }
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.getSharedPreferences(PREFS_RUNTIME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, "").orEmpty().trim()
        deviceId = if (existing.isNotBlank()) {
            existing
        } else {
            val generated = "android-" + UUID.randomUUID().toString().replace("-", "")
            prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
            generated
        }
        try {
            val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            appVersion = info.versionName ?: "unknown"
            buildNumber = if (Build.VERSION.SDK_INT >= 28) {
                val field = info.javaClass.getField("longVersionCode")
                field.getLong(info)
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            appVersion = "unknown"
            buildNumber = -1L
        }
    }

    private fun buildPayload(
        config: PostHogConfig,
        eventName: String,
        properties: Map<String, Any?>
    ): String? {
        val ctx = appContext ?: return null
        val eventJson = JSONObject()
        eventJson.put("api_key", config.projectApiKey)
        eventJson.put("event", eventName)
        val propsJson = JSONObject()
        val activeSession = synchronized(lock) {
            if (sessionId.isBlank()) {
                sessionId = buildSessionId("auto")
            }
            sessionId
        }
        propsJson.put("distinct_id", deviceId)
        propsJson.put("session_id", activeSession)
        propsJson.put("device_id", deviceId)
        propsJson.put("app_version", appVersion)
        propsJson.put("build_number", buildNumber)
        propsJson.put("project_id", config.projectId)
        propsJson.put("region", config.region)
        propsJson.put("environment", config.environment)
        propsJson.put("os_version", "android-${Build.VERSION.RELEASE ?: "unknown"}")
        propsJson.put("network_type", resolveNetworkType(ctx))
        propsJson.put("event_ts_client_ms", System.currentTimeMillis())
        propsJson.put("\$lib", "skoda-music-android")
        applySanitizedProperties(propsJson, properties)
        eventJson.put("properties", propsJson)
        return eventJson.toString()
    }

    private fun applySanitizedProperties(target: JSONObject, properties: Map<String, Any?>) {
        properties.forEach { (rawKey, rawValue) ->
            val key = rawKey.trim()
            if (key.isEmpty()) {
                return@forEach
            }
            val lower = key.lowercase(Locale.US)
            if (sensitiveKeyFragments.any { lower.contains(it) }) {
                return@forEach
            }
            val safeValue: Any = when (rawValue) {
                null -> "null"
                is Boolean -> rawValue
                is Int -> rawValue
                is Long -> rawValue
                is Float -> rawValue
                is Double -> rawValue
                is Number -> rawValue.toDouble()
                else -> rawValue.toString().trim().take(MAX_STRING_LENGTH)
            }
            target.put(key, safeValue)
        }
    }

    private fun buildCaptureUrl(host: String): String? {
        val normalized = host.trim().trimEnd('/')
        if (normalized.isBlank()) {
            return null
        }
        return "$normalized/capture/"
    }

    private fun send(url: String, payload: String, eventName: String) {
        try {
            val body = RequestBody.create(jsonMediaType, payload)
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code() !in 200..299) {
                    Log.w(TAG, "capture failed event=$eventName code=${response.code()}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "capture exception event=$eventName type=${e.javaClass.simpleName}")
        }
    }

    private fun maybeWarnMissingConfig() {
        val nowMs = SystemClock.elapsedRealtime()
        if (nowMs - lastConfigWarnAtMs < CONFIG_WARN_COOLDOWN_MS) {
            return
        }
        lastConfigWarnAtMs = nowMs
        Log.i(TAG, "capture skipped reason=missing-or-disabled-config")
    }

    private fun buildSessionId(launchSource: String): String {
        val millis = System.currentTimeMillis()
        val suffix = UUID.randomUUID().toString().take(8)
        return "s4-${launchSource.trim().ifEmpty { "session" }}-$millis-$suffix"
    }

    private fun withinBudget(priority: Priority): Boolean {
        if (totalEventCount >= SESSION_BUDGET_TOTAL) {
            return false
        }
        if (priority != Priority.HIGH && normalEventCount >= SESSION_BUDGET_NORMAL) {
            return false
        }
        return true
    }

    private fun buildCoalesceKey(eventName: String, properties: Map<String, Any?>): String {
        val stage = properties["stage"]?.toString().orEmpty()
        val errorCode = properties["error_code"]?.toString().orEmpty()
        val source = properties["source"]?.toString().orEmpty()
        val action = properties["action"]?.toString().orEmpty()
        val trackId = properties["track_id"]?.toString().orEmpty()
        return "$eventName|$stage|$errorCode|$source|$action|$trackId"
    }

    private fun isErrorLike(eventName: String, properties: Map<String, Any?>): Boolean {
        if (properties["error_code"] != null) {
            return true
        }
        val lower = eventName.lowercase(Locale.US)
        return lower.contains("error") || lower.contains("failed")
    }

    private fun trimCoalesceKeys() {
        while (lastSentAtMs.size > MAX_CACHE_KEYS) {
            val first = lastSentAtMs.entries.firstOrNull() ?: break
            lastSentAtMs.remove(first.key)
        }
    }

    private fun resolveNetworkType(context: Context): String {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val info = manager?.activeNetworkInfo
            if (info == null || !info.isConnected) {
                "offline"
            } else {
                (info.typeName ?: "unknown").lowercase(Locale.US)
            }
        } catch (_: Exception) {
            "unknown"
        }
    }
}
