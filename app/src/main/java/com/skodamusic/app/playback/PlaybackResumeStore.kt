package com.skodamusic.app.playback

import android.content.Context

class PlaybackResumeStore(context: Context) {
    data class Snapshot(
        val queueJson: String,
        val index: Int,
        val positionMs: Long,
        val wasPlaying: Boolean,
        val savedAtMs: Long,
        val baseUrl: String,
        val username: String
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): Snapshot? {
        val currentQueue = prefs.getString(KEY_QUEUE_JSON, "").orEmpty()
        if (currentQueue.isNotBlank()) {
            return Snapshot(
                queueJson = currentQueue,
                index = prefs.getInt(KEY_INDEX, 0),
                positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
                wasPlaying = prefs.getBoolean(KEY_WAS_PLAYING, false),
                savedAtMs = prefs.getLong(KEY_SAVED_AT_MS, 0L),
                baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
                username = prefs.getString(KEY_USERNAME, "").orEmpty()
            )
        }

        // Backward compatibility: migrate from legacy keys inside emby_credentials once.
        val legacyQueue = legacyPrefs.getString(KEY_QUEUE_JSON, "").orEmpty()
        if (legacyQueue.isBlank()) {
            return null
        }
        val snapshot = Snapshot(
            queueJson = legacyQueue,
            index = legacyPrefs.getInt(KEY_INDEX, 0),
            positionMs = legacyPrefs.getLong(KEY_POSITION_MS, 0L),
            wasPlaying = legacyPrefs.getBoolean(KEY_WAS_PLAYING, false),
            savedAtMs = legacyPrefs.getLong(KEY_SAVED_AT_MS, 0L),
            baseUrl = legacyPrefs.getString(KEY_BASE_URL, "").orEmpty(),
            username = legacyPrefs.getString(KEY_USERNAME, "").orEmpty()
        )
        save(snapshot)
        clearLegacyKeys()
        return snapshot
    }

    fun save(snapshot: Snapshot) {
        if (snapshot.queueJson.isBlank()) {
            clear()
            return
        }
        prefs.edit()
            .putString(KEY_QUEUE_JSON, snapshot.queueJson)
            .putInt(KEY_INDEX, snapshot.index)
            .putLong(KEY_POSITION_MS, snapshot.positionMs.coerceAtLeast(0L))
            .putBoolean(KEY_WAS_PLAYING, snapshot.wasPlaying)
            .putLong(KEY_SAVED_AT_MS, snapshot.savedAtMs)
            .putString(KEY_BASE_URL, snapshot.baseUrl)
            .putString(KEY_USERNAME, snapshot.username)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_QUEUE_JSON)
            .remove(KEY_INDEX)
            .remove(KEY_POSITION_MS)
            .remove(KEY_WAS_PLAYING)
            .remove(KEY_SAVED_AT_MS)
            .remove(KEY_BASE_URL)
            .remove(KEY_USERNAME)
            .apply()
        clearLegacyKeys()
    }

    private fun clearLegacyKeys() {
        legacyPrefs.edit()
            .remove(KEY_QUEUE_JSON)
            .remove(KEY_INDEX)
            .remove(KEY_POSITION_MS)
            .remove(KEY_WAS_PLAYING)
            .remove(KEY_SAVED_AT_MS)
            .remove(KEY_BASE_URL)
            .remove(KEY_USERNAME)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "playback_resume_state"
        const val LEGACY_PREFS_NAME = "emby_credentials"

        const val KEY_QUEUE_JSON = "resume_queue_json"
        const val KEY_INDEX = "resume_index"
        const val KEY_POSITION_MS = "resume_position_ms"
        const val KEY_WAS_PLAYING = "resume_was_playing"
        const val KEY_SAVED_AT_MS = "resume_saved_at_ms"
        const val KEY_BASE_URL = "resume_base_url"
        const val KEY_USERNAME = "resume_username"
    }
}
