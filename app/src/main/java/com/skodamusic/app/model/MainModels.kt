package com.skodamusic.app.model

import androidx.annotation.StringRes

data class LyricLine(
    val timeMs: Long,
    val text: String
)

enum class DownloadControlPhase {
    MAINTAIN_CURRENT_WINDOW,
    FINISH_CURRENT_TRACK,
    PREFETCH_NEXT_WINDOW,
    IDLE
}

enum class PlaybackFailureCategory {
    DECODER_FAILURE,
    SOURCE_FAILURE,
    NETWORK_FAILURE,
    UNKNOWN
}

enum class ListSource {
    QUEUE,
    LIBRARY
}

data class UiState(
    val currentTrack: String,
    @StringRes val playbackStatusRes: Int,
    @StringRes val playPauseLabelRes: Int,
    val feedbackText: String,
    val embyStatusText: String,
    val lrcApiStatusText: String,
    val isPlaying: Boolean,
    val playPauseEnabled: Boolean,
    val nextEnabled: Boolean,
    val testEmbyEnabled: Boolean,
    val testLrcApiEnabled: Boolean
)

data class EmbyCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
    val cfReferenceDomain: String
)

data class LrcApiCredentials(
    val baseUrl: String
)

data class EmbyTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val runtimeTicks: Long = -1L
)

data class AuthByNameResult(
    val accessToken: String,
    val userId: String
)

data class HttpResult(
    val code: Int,
    val payload: String
)

data class EmbyLoadResult(
    val success: Boolean,
    val statusText: String,
    val feedbackText: String,
    val tracks: List<EmbyTrack> = emptyList(),
    val embyBase: String? = null,
    val embyUserId: String? = null,
    val accessToken: String? = null
)

data class LrcApiTestResult(
    val success: Boolean,
    val statusText: String,
    val feedbackText: String
)

data class TrackDownloadState(
    val trackId: String,
    var totalBytes: Long = -1L,
    var downloadedBytes: Long = 0L,
    var completed: Boolean = false,
    var bitrateBps: Long = 192_000L
)

data class DeleteTrackOutcome(
    val removedFromQueue: Boolean,
    val removedCurrentTrack: Boolean,
    val queueEmptyAfterDelete: Boolean,
    val removedAnything: Boolean
)
