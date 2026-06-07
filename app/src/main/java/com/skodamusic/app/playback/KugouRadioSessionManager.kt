package com.skodamusic.app.playback

import com.skodamusic.app.model.SourceRadio
import com.skodamusic.app.model.SourceTrack

class KugouRadioSessionManager {
    private var activeRadio: SourceRadio? = null
    private var currentTrack: SourceTrack? = null
    private val upcomingTracks = mutableListOf<SourceTrack>()
    private val historyTracks = mutableListOf<SourceTrack>()

    fun isActive(): Boolean {
        return currentTrack != null
    }

    fun current(): SourceTrack? {
        return if (isActive()) currentTrack else null
    }

    fun hasNext(): Boolean {
        return isActive() && upcomingTracks.isNotEmpty()
    }

    fun start(radio: SourceRadio?, songs: List<SourceTrack>, startTrack: SourceTrack): SourceTrack? {
        val prepared = prepareSongs(songs, startTrack)
        if (prepared.isEmpty()) {
            clear()
            return null
        }
        val target = prepared.firstOrNull { it.sameTrack(startTrack) } ?: prepared[0]
        activeRadio = radio
        currentTrack = target
        historyTracks.clear()
        upcomingTracks.clear()
        prepared.forEach { song ->
            if (!song.sameTrack(target)) {
                upcomingTracks.add(song)
            }
        }
        return target
    }

    fun select(track: SourceTrack): SourceTrack? {
        val current = currentTrack
        if (!isActive() || current == null) {
            return null
        }
        if (current.sameTrack(track)) {
            return current
        }
        val upcomingIndex = upcomingTracks.indexOfFirst { it.sameTrack(track) }
        if (upcomingIndex < 0) {
            return null
        }
        historyTracks.add(current)
        val selected = upcomingTracks.removeAt(upcomingIndex)
        currentTrack = selected
        return selected
    }

    fun next(): SourceTrack? {
        val current = currentTrack
        if (!isActive() || current == null || upcomingTracks.isEmpty()) {
            return null
        }
        historyTracks.add(current)
        val nextTrack = upcomingTracks.removeAt(0)
        currentTrack = nextTrack
        return nextTrack
    }

    fun previous(): SourceTrack? {
        val current = currentTrack
        if (!isActive() || current == null || historyTracks.isEmpty()) {
            return null
        }
        val previousTrack = historyTracks.removeAt(historyTracks.size - 1)
        upcomingTracks.add(0, current)
        currentTrack = previousTrack
        return previousTrack
    }

    fun queueSnapshot(): List<SourceTrack> {
        val current = currentTrack ?: return emptyList()
        return listOf(current) + upcomingTracks
    }

    fun historySnapshot(): List<SourceTrack> {
        return historyTracks.toList()
    }

    fun upcomingSnapshot(): List<SourceTrack> {
        return upcomingTracks.toList()
    }

    fun clear() {
        activeRadio = null
        currentTrack = null
        upcomingTracks.clear()
        historyTracks.clear()
    }

    private fun prepareSongs(songs: List<SourceTrack>, startTrack: SourceTrack): List<SourceTrack> {
        val source = if (songs.isEmpty()) listOf(startTrack) else songs
        val prepared = mutableListOf<SourceTrack>()
        source.forEach { song ->
            if (prepared.none { it.sameTrack(song) }) {
                prepared.add(song)
            }
        }
        if (prepared.none { it.sameTrack(startTrack) }) {
            prepared.add(0, startTrack)
        }
        return prepared
    }

    private fun SourceTrack.sameTrack(other: SourceTrack): Boolean {
        return source == other.source &&
            sourceTrackId == other.sourceTrackId &&
            playbackRef.hash == other.playbackRef.hash
    }
}
