package com.skodamusic.app.data

import android.content.Context
import com.skodamusic.app.model.AuthByNameResult
import com.skodamusic.app.model.EmbyTrack
import com.skodamusic.app.model.TrackCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EmbySessionCache(
    context: Context,
    prefsName: String,
    private val keyAuthBaseUrl: String,
    private val keyAuthUsername: String,
    private val keyAuthAccessToken: String,
    private val keyAuthUserId: String,
    private val keyAuthSavedAtMs: String,
    private val keyRecommendCacheDay: String,
    private val keyRecommendCacheOwner: String,
    private val keyRecommendCacheJson: String
) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun loadCachedSessionAuth(embyBase: String, username: String): AuthByNameResult? {
        val savedBase = prefs.getString(keyAuthBaseUrl, "").orEmpty().trim()
        val savedUser = prefs.getString(keyAuthUsername, "").orEmpty().trim()
        if (!savedBase.equals(embyBase, ignoreCase = true) || !savedUser.equals(username, ignoreCase = true)) {
            return null
        }
        val token = prefs.getString(keyAuthAccessToken, "").orEmpty().trim()
        val userId = prefs.getString(keyAuthUserId, "").orEmpty().trim()
        if (token.isEmpty() || userId.isEmpty()) {
            return null
        }
        return AuthByNameResult(accessToken = token, userId = userId)
    }

    fun persistCachedSessionAuth(embyBase: String, username: String, auth: AuthByNameResult) {
        prefs.edit()
            .putString(keyAuthBaseUrl, embyBase)
            .putString(keyAuthUsername, username)
            .putString(keyAuthAccessToken, auth.accessToken)
            .putString(keyAuthUserId, auth.userId)
            .putLong(keyAuthSavedAtMs, System.currentTimeMillis())
            .apply()
    }

    fun clearCachedSessionAuth(embyBase: String, username: String) {
        val savedBase = prefs.getString(keyAuthBaseUrl, "").orEmpty().trim()
        val savedUser = prefs.getString(keyAuthUsername, "").orEmpty().trim()
        if (!savedBase.equals(embyBase, ignoreCase = true) || !savedUser.equals(username, ignoreCase = true)) {
            return
        }
        prefs.edit()
            .remove(keyAuthBaseUrl)
            .remove(keyAuthUsername)
            .remove(keyAuthAccessToken)
            .remove(keyAuthUserId)
            .remove(keyAuthSavedAtMs)
            .apply()
    }

    fun loadTodayRecommendCache(ownerKey: String): List<EmbyTrack> {
        val day = prefs.getString(keyRecommendCacheDay, "").orEmpty()
        val owner = prefs.getString(keyRecommendCacheOwner, "").orEmpty()
        if (day != currentDayKey() || owner != ownerKey) {
            return emptyList()
        }
        val payload = prefs.getString(keyRecommendCacheJson, "").orEmpty()
        if (payload.isBlank()) {
            return emptyList()
        }
        return TrackCodec.parseCachedTrackArray(payload)
    }

    fun persistTodayRecommendCache(ownerKey: String, tracks: List<EmbyTrack>) {
        prefs.edit()
            .putString(keyRecommendCacheDay, currentDayKey())
            .putString(keyRecommendCacheOwner, ownerKey)
            .putString(keyRecommendCacheJson, TrackCodec.buildCachedTrackArray(tracks))
            .apply()
    }

    private fun currentDayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
