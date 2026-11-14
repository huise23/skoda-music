package com.skodamusic.app.playback

import android.content.Context
import com.skodamusic.app.model.MusicSource
import com.skodamusic.app.model.SourcePlaybackRef
import com.skodamusic.app.model.SourceTrack
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Persists and restores Kugou playback state across head-unit reboots,
 * preventing queue restart from index 0 on each car drive within the same day.
 *
 * Rules:
 * 1. The playlist is reset daily (valid only for the current calendar day).
 * 2. Intra-song progress is not saved (songs resume from 00:00).
 */
class KugouResumeStore(context: Context) {
    data class Snapshot(
        val tracks: List<SourceTrack>,
        val currentIndex: Int,
        val currentTrack: SourceTrack,
        val sourceContext: String,
        val savedAtMs: Long
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(
        tracks: List<SourceTrack>,
        currentTrack: SourceTrack?,
        sourceContext: String = ""
    ) {
        if (tracks.isEmpty() || currentTrack == null) {
            return
        }
        val safeIndex = tracks.indexOfFirst {
            it.sourceTrackId == currentTrack.sourceTrackId ||
                (it.playbackRef.hash.isNotBlank() && it.playbackRef.hash.equals(currentTrack.playbackRef.hash, ignoreCase = true))
        }.coerceAtLeast(0)

        val queueJson = encodeQueue(tracks)
        val trackJson = encodeTrack(currentTrack)

        prefs.edit()
            .putString(KEY_QUEUE_JSON, queueJson)
            .putString(KEY_CURRENT_TRACK_JSON, trackJson)
            .putInt(KEY_INDEX, safeIndex)
            .putString(KEY_SOURCE_CONTEXT, sourceContext)
            .putLong(KEY_SAVED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    fun read(): Snapshot? {
        val savedAtMs = prefs.getLong(KEY_SAVED_AT_MS, 0L)
        val now = System.currentTimeMillis()
        if (!isSameDay(savedAtMs, now)) {
            if (savedAtMs > 0L) {
                clear()
            }
            return null
        }

        val queueJson = prefs.getString(KEY_QUEUE_JSON, "").orEmpty()
        val trackJson = prefs.getString(KEY_CURRENT_TRACK_JSON, "").orEmpty()
        if (queueJson.isBlank() && trackJson.isBlank()) {
            return null
        }
        val tracks = decodeQueue(queueJson).toMutableList()
        val currentTrack = decodeTrack(trackJson)
        val fallbackTrack = currentTrack ?: tracks.firstOrNull() ?: return null
        if (tracks.isEmpty()) {
            tracks.add(fallbackTrack)
        }
        val rawIndex = prefs.getInt(KEY_INDEX, 0)
        val safeIndex = rawIndex.coerceIn(0, tracks.lastIndex)
        val activeTrack = if (currentTrack != null) {
            val foundIndex = tracks.indexOfFirst {
                it.sourceTrackId == currentTrack.sourceTrackId ||
                    (it.playbackRef.hash.isNotBlank() && it.playbackRef.hash.equals(currentTrack.playbackRef.hash, ignoreCase = true))
            }
            if (foundIndex >= 0) tracks[foundIndex] else currentTrack
        } else {
            tracks[safeIndex]
        }
        val sourceContext = prefs.getString(KEY_SOURCE_CONTEXT, "").orEmpty()

        return Snapshot(
            tracks = tracks,
            currentIndex = safeIndex,
            currentTrack = activeTrack,
            sourceContext = sourceContext,
            savedAtMs = savedAtMs
        )
    }

    fun isToday(timestampMs: Long = prefs.getLong(KEY_SAVED_AT_MS, 0L)): Boolean {
        return isSameDay(timestampMs, System.currentTimeMillis())
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        if (t1 <= 0L || t2 <= 0L) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun encodeTrack(track: SourceTrack): String {
        val obj = JSONObject()
        obj.put("source", track.source.name)
        obj.put("trackId", track.sourceTrackId)
        obj.put("title", track.title)
        obj.put("artist", track.artist)
        obj.put("album", track.album)
        obj.put("coverUrl", track.coverUrl)
        obj.put("durationMs", track.durationMs)
        val refObj = JSONObject()
        refObj.put("primaryId", track.playbackRef.primaryId)
        refObj.put("hash", track.playbackRef.hash)
        refObj.put("albumId", track.playbackRef.albumId)
        refObj.put("albumAudioId", track.playbackRef.albumAudioId)
        refObj.put("quality", track.playbackRef.quality)
        refObj.put("requiresLogin", track.playbackRef.requiresLogin)
        obj.put("ref", refObj)
        return obj.toString()
    }

    private fun decodeTrack(jsonText: String): SourceTrack? {
        if (jsonText.isBlank()) return null
        return try {
            val obj = JSONObject(jsonText)
            val trackId = obj.optString("trackId")
            val title = obj.optString("title")
            if (trackId.isBlank() || title.isBlank()) return null
            val refObj = obj.optJSONObject("ref")
            val playbackRef = SourcePlaybackRef(
                source = MusicSource.KUGOU,
                primaryId = refObj?.optString("primaryId") ?: trackId,
                hash = refObj?.optString("hash") ?: "",
                albumId = refObj?.optString("albumId") ?: "",
                albumAudioId = refObj?.optString("albumAudioId") ?: "",
                quality = refObj?.optString("quality") ?: "",
                requiresLogin = refObj?.optBoolean("requiresLogin") ?: false
            )
            SourceTrack(
                source = MusicSource.KUGOU,
                sourceTrackId = trackId,
                title = title,
                artist = obj.optString("artist"),
                album = obj.optString("album"),
                coverUrl = obj.optString("coverUrl"),
                durationMs = obj.optLong("durationMs", -1L),
                playbackRef = playbackRef
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeQueue(tracks: List<SourceTrack>): String {
        val array = JSONArray()
        for (track in tracks) {
            array.put(JSONObject(encodeTrack(track)))
        }
        return array.toString()
    }

    private fun decodeQueue(jsonText: String): List<SourceTrack> {
        val out = mutableListOf<SourceTrack>()
        if (jsonText.isBlank()) return out
        return try {
            val array = JSONArray(jsonText)
            for (i in 0 until array.length()) {
                val itemObj = array.optJSONObject(i) ?: continue
                val track = decodeTrack(itemObj.toString())
                if (track != null) {
                    out.add(track)
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS_NAME = "kugou_playback_resume_state"
        private const val KEY_QUEUE_JSON = "kugou_queue_json"
        private const val KEY_CURRENT_TRACK_JSON = "kugou_current_track_json"
        private const val KEY_INDEX = "kugou_index"
        private const val KEY_SOURCE_CONTEXT = "kugou_source_context"
        private const val KEY_SAVED_AT_MS = "kugou_saved_at_ms"
    }
}
