package com.skodamusic.app.ui

import com.skodamusic.app.model.SourceTrack

class DailyRecommendCoordinator(
    private val appendRuntimeLog: (String) -> Unit
) {
    private var pendingAutoPlay = false
    private var pendingForce = false
    private var firstEntryPlayed = false

    fun reset() {
        pendingAutoPlay = false
        pendingForce = false
        firstEntryPlayed = false
    }

    fun requestPlay(
        reason: String,
        force: Boolean,
        hasSession: Boolean,
        tracks: List<SourceTrack>,
        requestLogin: () -> Unit,
        requestLoad: () -> Unit,
        playFirst: (SourceTrack, List<SourceTrack>, String) -> Unit
    ) {
        if (!force && firstEntryPlayed) {
            return
        }
        pendingAutoPlay = true
        pendingForce = force
        if (!hasSession) {
            appendRuntimeLog("daily recommend pending reason=$reason state=missing-session")
            requestLogin()
            return
        }
        if (tracks.isEmpty()) {
            appendRuntimeLog("daily recommend pending reason=$reason state=load")
            requestLoad()
            return
        }
        playLoaded(reason, force, tracks, playFirst)
    }

    fun onLoaded(
        reason: String,
        tracks: List<SourceTrack>,
        playFirst: (SourceTrack, List<SourceTrack>, String) -> Unit
    ) {
        if (!pendingAutoPlay || tracks.isEmpty()) {
            return
        }
        playLoaded(reason, force = pendingForce, tracks = tracks, playFirst = playFirst)
    }

    private fun playLoaded(
        reason: String,
        force: Boolean,
        tracks: List<SourceTrack>,
        playFirst: (SourceTrack, List<SourceTrack>, String) -> Unit
    ) {
        if (!force && firstEntryPlayed) {
            pendingAutoPlay = false
            pendingForce = false
            return
        }
        val firstTrack = tracks.firstOrNull() ?: return
        pendingAutoPlay = false
        pendingForce = false
        firstEntryPlayed = true
        appendRuntimeLog("daily recommend play-first reason=$reason size=${tracks.size}")
        playFirst(firstTrack, tracks, "daily_recommend")
    }
}
