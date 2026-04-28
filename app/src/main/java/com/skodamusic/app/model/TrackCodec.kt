package com.skodamusic.app.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

object TrackCodec {
    fun parseTrackItems(jsonText: String): List<EmbyTrack> {
        val out = mutableListOf<EmbyTrack>()
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) {
            return out
        }
        if (trimmed.startsWith("[")) {
            val items = JSONArray(trimmed)
            for (i in 0 until items.length()) {
                val track = extractTrack(items.optJSONObject(i))
                if (track != null) {
                    out.add(track)
                }
            }
            return out
        }

        val root = JSONObject(trimmed)
        val items = root.optJSONArray("Items") ?: return out
        for (i in 0 until items.length()) {
            val track = extractTrack(items.optJSONObject(i))
            if (track != null) {
                out.add(track)
            }
        }
        return out
    }

    fun parseTotalRecordCount(jsonText: String, fallback: Int): Int {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return fallback
        }
        return try {
            val root = JSONObject(trimmed)
            val total = root.optInt("TotalRecordCount", fallback)
            if (total <= 0) fallback else total
        } catch (_: Exception) {
            fallback
        }
    }

    fun titleComparator(locale: Locale = Locale.CHINA): Comparator<EmbyTrack> {
        val collator = Collator.getInstance(locale).apply {
            strength = Collator.PRIMARY
        }
        return Comparator { left, right ->
            val titleCompare = collator.compare(left.title.trim(), right.title.trim())
            if (titleCompare != 0) {
                titleCompare
            } else {
                left.id.compareTo(right.id, ignoreCase = true)
            }
        }
    }

    fun buildCachedTrackArray(tracks: List<EmbyTrack>): String {
        val arr = JSONArray()
        tracks.forEach { track ->
            arr.put(
                JSONObject()
                    .put("id", track.id)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("runtimeTicks", track.runtimeTicks)
            )
        }
        return arr.toString()
    }

    fun parseCachedTrackArray(payload: String): List<EmbyTrack> {
        return try {
            val arr = JSONArray(payload)
            val out = mutableListOf<EmbyTrack>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isEmpty() || title.isEmpty()) {
                    continue
                }
                out.add(
                    EmbyTrack(
                        id = id,
                        title = title,
                        artist = item.optString("artist").trim(),
                        runtimeTicks = item.optLong("runtimeTicks", -1L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildRecommendCacheOwnerKey(embyBase: String, username: String): String {
        return "${embyBase.lowercase(Locale.US)}|${username.trim().lowercase(Locale.US)}"
    }

    private fun extractTrack(item: JSONObject?): EmbyTrack? {
        if (item == null) {
            return null
        }
        val itemType = item.optString("Type").trim()
        if (itemType.isNotEmpty() && !itemType.equals("Audio", ignoreCase = true)) {
            return null
        }
        val trackId = item.optString("Id").trim()
        val candidates = listOf(
            item.optString("Name").trim(),
            item.optString("SortName").trim(),
            item.optString("Album").trim()
        )
        val title = candidates.firstOrNull { it.isNotEmpty() }.orEmpty()
        val artists = item.optJSONArray("Artists")
        val artist = if (artists != null && artists.length() > 0) {
            artists.optString(0).trim()
        } else {
            item.optString("AlbumArtist").trim()
        }
        if (trackId.isEmpty() || title.isEmpty()) {
            return null
        }
        return EmbyTrack(
            id = trackId,
            title = title,
            artist = artist,
            runtimeTicks = item.optLong("RunTimeTicks", -1L)
        )
    }
}
