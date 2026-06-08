package com.skodamusic.app.playback

import com.skodamusic.app.model.EmbyTrack
import com.skodamusic.app.model.MusicSource
import com.skodamusic.app.model.SourcePlaybackRef
import com.skodamusic.app.model.SourceTrack

data class SourcePlaybackSnapshot(
    val source: MusicSource,
    val trackId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val playbackRef: SourcePlaybackRef? = null
) {
    val hasTrack: Boolean
        get() = trackId.isNotBlank() && title.isNotBlank()
}

class SourcePlaybackSession {
    private var activeSource: MusicSource = MusicSource.EMBY
    private var activeKugouTrack: SourceTrack? = null

    fun markEmbyActive() {
        activeSource = MusicSource.EMBY
        activeKugouTrack = null
    }

    fun markKugouActive(track: SourceTrack) {
        activeSource = MusicSource.KUGOU
        activeKugouTrack = track
    }

    fun clearKugouIfActive() {
        if (activeSource == MusicSource.KUGOU) {
            markEmbyActive()
        }
    }

    fun isKugouActive(): Boolean {
        return activeSource == MusicSource.KUGOU && activeKugouTrack != null
    }

    fun currentKugouTrack(): SourceTrack? {
        return if (isKugouActive()) activeKugouTrack else null
    }

    fun snapshot(
        embyTrack: EmbyTrack?,
        embyDurationMs: Long,
        engineDurationMs: Long
    ): SourcePlaybackSnapshot? {
        val kugou = currentKugouTrack()
        if (kugou != null) {
            val duration = when {
                engineDurationMs > 0L -> engineDurationMs
                kugou.durationMs > 0L -> kugou.durationMs
                else -> 0L
            }
            return SourcePlaybackSnapshot(
                source = MusicSource.KUGOU,
                trackId = kugou.sourceTrackId,
                title = kugou.title,
                artist = kugou.artist,
                durationMs = duration,
                playbackRef = kugou.playbackRef
            )
        }
        if (embyTrack == null) {
            return null
        }
        return SourcePlaybackSnapshot(
            source = MusicSource.EMBY,
            trackId = embyTrack.id,
            title = embyTrack.title,
            artist = embyTrack.artist,
            durationMs = embyDurationMs.coerceAtLeast(0L)
        )
    }
}
