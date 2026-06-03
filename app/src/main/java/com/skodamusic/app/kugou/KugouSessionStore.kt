package com.skodamusic.app.kugou

import android.content.Context

data class KugouSessionSnapshot(
    val baseUrl: String,
    val sessionKey: String,
    val savedAtMs: Long,
    val lastUserId: String,
    val displayName: String
)

class KugouSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): KugouSessionSnapshot? {
        val baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty().trim()
        val sessionKey = prefs.getString(KEY_SESSION_KEY, "").orEmpty().trim()
        if (baseUrl.isEmpty() || sessionKey.isEmpty()) {
            return null
        }
        return KugouSessionSnapshot(
            baseUrl = baseUrl,
            sessionKey = sessionKey,
            savedAtMs = prefs.getLong(KEY_SAVED_AT_MS, 0L),
            lastUserId = prefs.getString(KEY_LAST_USER_ID, "").orEmpty().trim(),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty().trim()
        )
    }

    fun loadBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, "").orEmpty().trim()
    }

    fun persistBaseUrl(baseUrl: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .apply()
    }

    fun persistSession(
        baseUrl: String,
        sessionKey: String,
        lastUserId: String,
        displayName: String
    ) {
        prefs.edit()
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_SESSION_KEY, sessionKey.trim())
            .putLong(KEY_SAVED_AT_MS, System.currentTimeMillis())
            .putString(KEY_LAST_USER_ID, lastUserId.trim())
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_KEY)
            .remove(KEY_SAVED_AT_MS)
            .remove(KEY_LAST_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "kugou_webapi_session"
        private const val KEY_BASE_URL = "kugou_webapi_base_url"
        private const val KEY_SESSION_KEY = "kugou_session_key"
        private const val KEY_SAVED_AT_MS = "saved_at_ms"
        private const val KEY_LAST_USER_ID = "last_user_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
