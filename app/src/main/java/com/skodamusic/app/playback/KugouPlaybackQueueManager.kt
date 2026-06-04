package com.skodamusic.app.playback

import com.skodamusic.app.model.SourceTrack

class KugouPlaybackQueueManager {
    private val playbackQueue = mutableListOf<SourceTrack>()
    private val originalQueue = mutableListOf<SourceTrack>()

    fun setupQueue(track: SourceTrack, contextList: List<SourceTrack>?) {
        if (!contextList.isNullOrEmpty()) {
            val targetList = boundedContext(track, contextList)
            originalQueue.clear()
            originalQueue.addAll(targetList)
            playbackQueue.clear()
            playbackQueue.addAll(targetList)
            return
        }
        if (playbackQueue.isEmpty()) {
            originalQueue.add(track)
            playbackQueue.add(track)
        }
    }

    fun getNext(current: SourceTrack?): SourceTrack? {
        if (playbackQueue.isEmpty()) {
            return null
        }
        val index = indexOf(current)
        val nextIndex = (index + 1).floorMod(playbackQueue.size)
        return playbackQueue[nextIndex]
    }

    fun getPrevious(current: SourceTrack?): SourceTrack? {
        if (playbackQueue.isEmpty()) {
            return null
        }
        val index = indexOf(current)
        val previousIndex = (index - 1).floorMod(playbackQueue.size)
        return playbackQueue[previousIndex]
    }

    fun snapshot(): List<SourceTrack> {
        return playbackQueue.toList()
    }

    fun clear() {
        playbackQueue.clear()
        originalQueue.clear()
    }

    private fun boundedContext(track: SourceTrack, contextList: List<SourceTrack>): List<SourceTrack> {
        if (contextList.size <= MAX_QUEUE_SIZE) {
            return contextList.toList()
        }
        val currentIndex = contextList.indexOfFirst { it.sameTrack(track) }
        if (currentIndex < 0) {
            return contextList.take(MAX_QUEUE_SIZE)
        }
        val start = (currentIndex - MAX_QUEUE_SIZE / 2).coerceAtLeast(0)
        val count = (contextList.size - start).coerceAtMost(MAX_QUEUE_SIZE)
        return contextList.subList(start, start + count).toList()
    }

    private fun indexOf(track: SourceTrack?): Int {
        if (track == null) {
            return -1
        }
        return playbackQueue.indexOfFirst { it.sameTrack(track) }
    }

    private fun SourceTrack.sameTrack(other: SourceTrack): Boolean {
        return source == other.source &&
            sourceTrackId == other.sourceTrackId &&
            playbackRef.hash == other.playbackRef.hash
    }

    private fun Int.floorMod(size: Int): Int {
        return ((this % size) + size) % size
    }

    companion object {
        private const val MAX_QUEUE_SIZE = 300
    }
}
