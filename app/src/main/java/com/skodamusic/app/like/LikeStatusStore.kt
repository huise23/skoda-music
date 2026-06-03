package com.skodamusic.app.like

import android.content.Context
import com.skodamusic.app.model.MusicSource
import org.json.JSONArray
import org.json.JSONObject

data class LikeStatusItem(
    val source: MusicSource,
    val sourceTrackId: String,
    val title: String,
    val artist: String,
    val hash: String,
    val actionAtMs: Long,
    val remoteStatus: String,
    val ingestStatus: String,
    val failureReason: String
)

class LikeStatusStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<LikeStatusItem> {
        val raw = prefs.getString(KEY_ITEMS, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val items = ArrayList<LikeStatusItem>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val source = runCatching { MusicSource.valueOf(item.optString("source")) }.getOrNull() ?: continue
            items.add(
                LikeStatusItem(
                    source = source,
                    sourceTrackId = item.optString("sourceTrackId"),
                    title = item.optString("title"),
                    artist = item.optString("artist"),
                    hash = item.optString("hash"),
                    actionAtMs = item.optLong("actionAtMs", 0L),
                    remoteStatus = item.optString("remoteStatus"),
                    ingestStatus = item.optString("ingestStatus"),
                    failureReason = item.optString("failureReason")
                )
            )
        }
        return items.sortedByDescending { it.actionAtMs }
    }

    fun upsert(item: LikeStatusItem) {
        val current = load().filterNot {
            it.source == item.source && it.hash.equals(item.hash, ignoreCase = true) && item.hash.isNotBlank()
        }.toMutableList()
        current.add(0, item)
        persist(current.take(MAX_ITEMS))
    }

    private fun persist(items: List<LikeStatusItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("source", item.source.name)
                    .put("sourceTrackId", item.sourceTrackId)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("hash", item.hash)
                    .put("actionAtMs", item.actionAtMs)
                    .put("remoteStatus", item.remoteStatus)
                    .put("ingestStatus", item.ingestStatus)
                    .put("failureReason", item.failureReason)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    companion object {
        const val REMOTE_PENDING = "pending"
        const val REMOTE_LIKED = "liked"
        const val REMOTE_FAILED = "failed"
        const val INGEST_BLOCKED = "blocked_ingest"
        private const val PREFS_NAME = "source_like_status"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 100
    }
}
