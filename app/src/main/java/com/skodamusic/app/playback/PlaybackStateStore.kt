package com.skodamusic.app.playback

import android.content.Context

class PlaybackStateStore(context: Context) {
    data class Snapshot(
        val trackTitle: String,
        val trackId: String,
        val isPlaying: Boolean,
        val hasActiveTrack: Boolean,
        val positionMs: Long,
        val durationMs: Long
    )

    data class CommandTrace(
        val action: String,
        val source: String,
        val handled: Boolean,
        val detail: String,
        val updatedAtMs: Long
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSnapshot(snapshot: Snapshot) {
        prefs.edit()
            .putString(KEY_TRACK_TITLE, snapshot.trackTitle)
            .putString(KEY_TRACK_ID, snapshot.trackId)
            .putBoolean(KEY_IS_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_HAS_ACTIVE_TRACK, snapshot.hasActiveTrack)
            .putLong(KEY_POSITION_MS, snapshot.positionMs.coerceAtLeast(0L))
            .putLong(KEY_DURATION_MS, snapshot.durationMs.coerceAtLeast(0L))
            .putLong(KEY_UPDATED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun readSnapshot(): Snapshot {
        return Snapshot(
            trackTitle = prefs.getString(KEY_TRACK_TITLE, "").orEmpty(),
            trackId = prefs.getString(KEY_TRACK_ID, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            hasActiveTrack = prefs.getBoolean(KEY_HAS_ACTIVE_TRACK, false),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L),
            durationMs = prefs.getLong(KEY_DURATION_MS, 0L).coerceAtLeast(0L)
        )
    }

    fun setOverlayDismissedByUser(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_DISMISSED_BY_USER, dismissed).apply()
    }

    fun isOverlayDismissedByUser(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_DISMISSED_BY_USER, false)
    }

    fun saveCommandTrace(action: String, source: String, handled: Boolean, detail: String) {
        prefs.edit()
            .putString(KEY_LAST_CMD_ACTION, action)
            .putString(KEY_LAST_CMD_SOURCE, source)
            .putBoolean(KEY_LAST_CMD_HANDLED, handled)
            .putString(KEY_LAST_CMD_DETAIL, detail)
            .putLong(KEY_LAST_CMD_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun readCommandTrace(): CommandTrace {
        return CommandTrace(
            action = prefs.getString(KEY_LAST_CMD_ACTION, "").orEmpty(),
            source = prefs.getString(KEY_LAST_CMD_SOURCE, "").orEmpty(),
            handled = prefs.getBoolean(KEY_LAST_CMD_HANDLED, false),
            detail = prefs.getString(KEY_LAST_CMD_DETAIL, "").orEmpty(),
            updatedAtMs = prefs.getLong(KEY_LAST_CMD_AT_MS, 0L)
        )
    }

    private companion object {
        const val PREFS_NAME = "playback_runtime_state"
        const val KEY_TRACK_TITLE = "track_title"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_HAS_ACTIVE_TRACK = "has_active_track"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_UPDATED_AT_MS = "updated_at_ms"
        const val KEY_OVERLAY_DISMISSED_BY_USER = "overlay_dismissed_by_user"
        const val KEY_LAST_CMD_ACTION = "last_cmd_action"
        const val KEY_LAST_CMD_SOURCE = "last_cmd_source"
        const val KEY_LAST_CMD_HANDLED = "last_cmd_handled"
        const val KEY_LAST_CMD_DETAIL = "last_cmd_detail"
        const val KEY_LAST_CMD_AT_MS = "last_cmd_at_ms"
    }
}
