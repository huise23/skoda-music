package com.skodamusic.app.playback

import android.content.Context

class PlaybackStateStore(context: Context) {
    data class Snapshot(
        val trackTitle: String,
        val trackId: String,
        val isPlaying: Boolean,
        val hasActiveTrack: Boolean,
        val positionMs: Long
    )

    private data class PendingCommand(
        val action: String,
        val timestampMs: Long
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSnapshot(snapshot: Snapshot) {
        prefs.edit()
            .putString(KEY_TRACK_TITLE, snapshot.trackTitle)
            .putString(KEY_TRACK_ID, snapshot.trackId)
            .putBoolean(KEY_IS_PLAYING, snapshot.isPlaying)
            .putBoolean(KEY_HAS_ACTIVE_TRACK, snapshot.hasActiveTrack)
            .putLong(KEY_POSITION_MS, snapshot.positionMs.coerceAtLeast(0L))
            .putLong(KEY_UPDATED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun readSnapshot(): Snapshot {
        return Snapshot(
            trackTitle = prefs.getString(KEY_TRACK_TITLE, "").orEmpty(),
            trackId = prefs.getString(KEY_TRACK_ID, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            hasActiveTrack = prefs.getBoolean(KEY_HAS_ACTIVE_TRACK, false),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)
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
        val now = System.currentTimeMillis()
        for (action in actions) {
            if (action.isBlank()) {
                continue
            }
            if (PLAYBACK_TOGGLE_ACTIONS.contains(action)) {
                merged.removeAll { PLAYBACK_TOGGLE_ACTIONS.contains(it.action) }
            }
            if (merged.lastOrNull()?.action == action) {
                continue
            }
            merged.add(PendingCommand(action = action, timestampMs = now))
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
        return pending.map { it.action }
    }

    private fun readPendingCommandsInternal(): MutableList<PendingCommand> {
        val raw = prefs.getString(KEY_PENDING_COMMANDS, "").orEmpty()
        if (raw.isBlank()) {
            return mutableListOf()
        }
        val now = System.currentTimeMillis()
        return raw.split('\n')
            .mapNotNull { line -> parsePendingCommand(line.trim(), now) }
            .toMutableList()
    }

    private fun savePendingCommandsInternal(actions: List<PendingCommand>) {
        if (actions.isEmpty()) {
            prefs.edit().remove(KEY_PENDING_COMMANDS).apply()
            return
        }
        val payload = actions.joinToString("\n") { "${it.timestampMs}|${it.action}" }
        prefs.edit().putString(KEY_PENDING_COMMANDS, payload).apply()
    }

    private fun parsePendingCommand(line: String, now: Long): PendingCommand? {
        if (line.isBlank()) {
            return null
        }
        val separator = line.indexOf('|')
        val (ts, action) = if (separator > 0 && separator < line.lastIndex) {
            val tsPart = line.substring(0, separator)
            val actionPart = line.substring(separator + 1).trim()
            val parsedTs = tsPart.toLongOrNull() ?: now
            parsedTs to actionPart
        } else {
            now to line
        }
        if (action.isBlank()) {
            return null
        }
        if (now - ts > MAX_PENDING_COMMAND_AGE_MS) {
            return null
        }
        return PendingCommand(action = action, timestampMs = ts)
    }

    private companion object {
        const val PREFS_NAME = "playback_runtime_state"
        const val KEY_TRACK_TITLE = "track_title"
        const val KEY_TRACK_ID = "track_id"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_HAS_ACTIVE_TRACK = "has_active_track"
        const val KEY_POSITION_MS = "position_ms"
        const val KEY_UPDATED_AT_MS = "updated_at_ms"
        const val KEY_OVERLAY_DISMISSED_BY_USER = "overlay_dismissed_by_user"
        const val KEY_PENDING_COMMANDS = "pending_commands"
        const val MAX_PENDING_COMMANDS = 16
        const val MAX_PENDING_COMMAND_AGE_MS = 30L * 60L * 1000L
        val PLAYBACK_TOGGLE_ACTIONS = setOf(
            PlaybackActions.ACTION_CMD_PLAY_PAUSE,
            PlaybackActions.ACTION_CMD_PLAY,
            PlaybackActions.ACTION_CMD_PAUSE
        )
    }
}
