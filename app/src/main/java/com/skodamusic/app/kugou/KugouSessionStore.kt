package com.skodamusic.app.kugou

import android.content.Context

class KugouSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun clearLegacyWebApiState() {
        prefs.edit()
            .remove(KEY_BASE_URL)
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
