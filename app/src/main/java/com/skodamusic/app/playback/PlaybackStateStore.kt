package com.skodamusic.app.playback

import android.content.Context

class PlaybackStateStore(context: Context) {
    data class Snapshot(
        val trackTitle: String,
        val isPlaying: Boolean,
        val hasActiveTrack: Boolean
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSnapshot(snapshot: Snapshot) {
        prefs.edit()
            .putString(KEY_TRACK_TITLE, snapshot.trackTitle)
            .putBoolean(KEY_IS_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_HAS_ACTIVE_TRACK, snapshot.hasActiveTrack)
            .putLong(KEY_UPDATED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun readSnapshot(): Snapshot {
        return Snapshot(
            trackTitle = prefs.getString(KEY_TRACK_TITLE, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            hasActiveTrack = prefs.getBoolean(KEY_HAS_ACTIVE_TRACK, false)
        )
    }

    fun setOverlayDismissedByUser(dismissed: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_DISMISSED_BY_USER, dismissed).apply()
    }

    fun isOverlayDismissedByUser(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_DISMISSED_BY_USER, false)
    }

    fun enqueuePendingCommand(action: String) {
        enqueuePendingCommands(listOf(action))
    }

    fun enqueuePendingCommands(actions: List<String>) {
        if (actions.isEmpty()) {
            return
        }
        val merged = readPendingCommandsInternal()
        for (action in actions) {
            if (action.isBlank()) {
                continue
            }
            merged.add(action)
            if (merged.size > MAX_PENDING_COMMANDS) {
                merged.removeAt(0)
            }
        }
        savePendingCommandsInternal(merged)
    }

    fun consumePendingCommands(): List<String> {
        val pending = readPendingCommandsInternal()
        if (pending.isEmpty()) {
            return emptyList()
        }
        prefs.edit().remove(KEY_PENDING_COMMANDS).apply()
        return pending
    }

    private fun readPendingCommandsInternal(): MutableList<String> {
        val raw = prefs.getString(KEY_PENDING_COMMANDS, "").orEmpty()
        if (raw.isBlank()) {
            return mutableListOf()
        }
        return raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
    }

    private fun savePendingCommandsInternal(actions: List<String>) {
        if (actions.isEmpty()) {
            prefs.edit().remove(KEY_PENDING_COMMANDS).apply()
            return
        }
        prefs.edit().putString(KEY_PENDING_COMMANDS, actions.joinToString("\n")).apply()
    }

    private companion object {
        const val PREFS_NAME = "playback_runtime_state"
        const val KEY_TRACK_TITLE = "track_title"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_HAS_ACTIVE_TRACK = "has_active_track"
        const val KEY_UPDATED_AT_MS = "updated_at_ms"
        const val KEY_OVERLAY_DISMISSED_BY_USER = "overlay_dismissed_by_user"
        const val KEY_PENDING_COMMANDS = "pending_commands"
        const val MAX_PENDING_COMMANDS = 16
    }
}
