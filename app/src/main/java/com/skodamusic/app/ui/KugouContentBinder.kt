package com.skodamusic.app.ui

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.skodamusic.app.R
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.kugou.KugouWebApiClient
import com.skodamusic.app.model.MusicSource
import com.skodamusic.app.model.SourceCapability
import com.skodamusic.app.model.SourcePlaybackRef
import com.skodamusic.app.model.SourcePlaylist
import com.skodamusic.app.model.SourceRadio
import com.skodamusic.app.model.SourceTrack
import com.skodamusic.app.observability.PostHogTracker

class KugouContentBinder(
    private val activity: AppCompatActivity,
    private val backgroundExecutor: AppBackgroundExecutor,
    private val webApiClient: () -> KugouWebApiClient,
    private val renderer: KugouContentRenderer,
    private val resolveBaseUrl: () -> String,
    private val getSessionKey: () -> String,
    private val updateSessionKey: (String) -> Unit,
    private val hasSession: () -> Boolean,
    private val ensureWifiConnectedForNetworkRequest: (String, Boolean) -> Boolean,
    private val setFeedbackText: (String) -> Unit,
    private val clearSessionState: (Boolean) -> Unit,
    private val requestQrLogin: () -> Unit,
    private val appendRuntimeLog: (String) -> Unit,
    private val onPlayQueuedTrack: (SourceTrack, List<SourceTrack>, String) -> Unit,
    private val onStartRadioTrack: (SourceRadio?, List<SourceTrack>, SourceTrack) -> Unit,
    private val onLikeTrack: (SourceTrack) -> Unit
) {
    private lateinit var radioStatusValue: TextView
    private lateinit var radioList: LinearLayout
    private lateinit var discoverStatusValue: TextView
    private lateinit var discoverTagList: LinearLayout
    private lateinit var discoverPlaylistList: LinearLayout

    private var recommendedTracks: List<SourceTrack> = emptyList()
    private var recommendedLoading: Boolean = false
    private var recommendedRadios: List<SourceRadio> = emptyList()
    private var radioSongs: List<SourceTrack> = emptyList()
    private var radioLoading: Boolean = false
    private var selectedRadioId: String = ""
    private var discoverTags: List<Pair<Int, String>> = emptyList()
    private var discoverPlaylists: List<SourcePlaylist> = emptyList()
    private var discoverSongs: List<SourceTrack> = emptyList()
    private var discoverLoading: Boolean = false
    private var selectedDiscoverTagId: Int = -1
    private var selectedPlaylistId: String = ""

    fun bindViews(
        radioStatusValue: TextView,
        radioList: LinearLayout,
        discoverStatusValue: TextView,
        discoverTagList: LinearLayout,
        discoverPlaylistList: LinearLayout
    ) {
        this.radioStatusValue = radioStatusValue
        this.radioList = radioList
        this.discoverStatusValue = discoverStatusValue
        this.discoverTagList = discoverTagList
        this.discoverPlaylistList = discoverPlaylistList
    }

    fun isRecommendedLoading(): Boolean {
        return recommendedLoading
    }

    fun clearContentState() {
        recommendedTracks = emptyList()
        recommendedLoading = false
        recommendedRadios = emptyList()
        radioSongs = emptyList()
        radioLoading = false
        selectedRadioId = ""
        discoverTags = emptyList()
        discoverPlaylists = emptyList()
        discoverSongs = emptyList()
        discoverLoading = false
        selectedDiscoverTagId = -1
        selectedPlaylistId = ""
    }

    fun requestRecommendedSongs(onFinished: (() -> Unit)? = null, renderHome: () -> Unit) {
        val baseUrl = resolveBaseUrl()
        val session = getSessionKey().trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasSession()) {
            onFinished?.invoke()
            renderHome()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            return
        }
        if (recommendedLoading) {
            onFinished?.invoke()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_recommend_songs", true)) {
            onFinished?.invoke()
            return
        }
        recommendedLoading = true
        renderHome()
        setFeedbackText(activity.getString(R.string.feedback_kugou_recommend_loading))
        backgroundExecutor.execute {
            val result = webApiClient().getRecommendedSongs(baseUrl, session)
            val mapped = result?.first.orEmpty().map { song ->
                SourceTrack(
                    source = MusicSource.KUGOU,
                    sourceTrackId = song.audioId.ifBlank { song.mixSongId.ifBlank { song.hash } },
                    title = song.name,
                    artist = song.singerName,
                    album = song.albumName,
                    coverUrl = song.coverUrl,
                    durationMs = if (song.durationSeconds > 0) song.durationSeconds * 1000L else -1L,
                    playbackRef = SourcePlaybackRef(
                        source = MusicSource.KUGOU,
                        primaryId = song.audioId.ifBlank { song.hash },
                        hash = song.hash,
                        albumId = song.albumId,
                        albumAudioId = song.mixSongId,
                        quality = "128",
                        requiresLogin = true
                    ),
                    capabilities = KUGOU_TRACK_CAPABILITIES
                )
            }
            activity.runOnUiThread {
                recommendedLoading = false
                onFinished?.invoke()
                if (result == null) {
                    appendRuntimeLog("kugou recommend songs failed")
                    captureContentEvent("kugou_content_load_failed", "recommend_songs", errorCode = "KUGOU_CONTENT_FAILED")
                    clearSessionState(true)
                    setFeedbackText(activity.getString(R.string.feedback_kugou_recommend_failed))
                    requestQrLogin()
                    return@runOnUiThread
                }
                updateSessionKeyIfPresent(result.second)
                recommendedTracks = mapped
                captureContentEvent("kugou_content_load_success", "recommend_songs", itemCount = mapped.size)
                renderHome()
                setFeedbackText(
                    if (mapped.isEmpty()) {
                        activity.getString(R.string.feedback_kugou_recommend_empty)
                    } else {
                        activity.getString(R.string.feedback_kugou_recommend_success, mapped.size)
                    }
                )
            }
        }
    }

    fun requestRecommendedRadios() {
        val baseUrl = resolveBaseUrl()
        val session = getSessionKey().trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasSession()) {
            renderRadioPage()
            return
        }
        if (radioLoading || recommendedRadios.isNotEmpty()) {
            renderRadioPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_fm_recommend", true)) {
            return
        }
        radioLoading = true
        renderRadioPage()
        backgroundExecutor.execute {
            val result = webApiClient().getRecommendedRadios(baseUrl, session)
            val mapped = result?.first.orEmpty().map { radio ->
                SourceRadio(
                    source = MusicSource.KUGOU,
                    sourceRadioId = radio.fmId,
                    title = radio.fmName,
                    coverUrl = radio.imageUrl.ifBlank { radio.banner },
                    subtitle = listOf(radio.className, radio.description, radio.previewSong)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    type = 2,
                    capabilities = KUGOU_RADIO_CAPABILITIES
                )
            }
            activity.runOnUiThread {
                radioLoading = false
                if (result == null) {
                    captureContentEvent("kugou_content_load_failed", "recommended_radios", errorCode = "KUGOU_CONTENT_FAILED")
                    clearSessionState(true)
                    setFeedbackText(activity.getString(R.string.feedback_kugou_radio_failed))
                    requestQrLogin()
                    return@runOnUiThread
                }
                updateSessionKeyIfPresent(result.second)
                recommendedRadios = mapped
                captureContentEvent("kugou_content_load_success", "recommended_radios", itemCount = mapped.size)
                renderRadioPage()
                setFeedbackText(
                    if (mapped.isEmpty()) {
                        activity.getString(R.string.feedback_kugou_radio_empty)
                    } else {
                        activity.getString(R.string.feedback_kugou_radio_success, mapped.size)
                    }
                )
            }
        }
    }

    fun requestDiscoverTags() {
        val baseUrl = resolveBaseUrl()
        val session = getSessionKey().trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasSession()) {
            renderDiscoverPage()
            return
        }
        if (discoverLoading || discoverTags.isNotEmpty()) {
            renderDiscoverPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_playlist_tags", true)) {
            return
        }
        discoverLoading = true
        renderDiscoverPage()
        backgroundExecutor.execute {
            val result = webApiClient().getPlaylistTags(baseUrl, session)
            val tags = result?.first.orEmpty()
                .take(DISCOVER_TAG_PREVIEW_LIMIT)
                .map { it.tagId to "${it.categoryName} · ${it.tagName}" }
            activity.runOnUiThread {
                discoverLoading = false
                if (result == null) {
                    captureContentEvent("kugou_content_load_failed", "discover_tags", errorCode = "KUGOU_CONTENT_FAILED")
                    clearSessionState(true)
                    setFeedbackText(activity.getString(R.string.feedback_kugou_discover_failed))
                    requestQrLogin()
                    return@runOnUiThread
                }
                updateSessionKeyIfPresent(result.second)
                discoverTags = tags
                captureContentEvent("kugou_content_load_success", "discover_tags", itemCount = tags.size)
                renderDiscoverPage()
                if (tags.isNotEmpty()) {
                    requestPlaylistsByTag(tags[0].first)
                } else {
                    setFeedbackText(activity.getString(R.string.feedback_kugou_discover_empty))
                }
            }
        }
    }

    fun renderRecommendedSongs(container: LinearLayout, limit: Int) {
        renderer.renderRecommendedSongs(
            container = container,
            tracks = recommendedTracks,
            limit = limit,
            onTrackClick = { index, track ->
                appendRuntimeLog("kugou recommend click index=$index hash=${track.playbackRef.hash}")
                captureContentEvent("kugou_queue_start", "home_recommend", itemCount = recommendedTracks.size)
                onPlayQueuedTrack(track, recommendedTracks, "home_recommend")
            },
            onLike = { track -> onLikeTrack(track) }
        )
    }

    fun renderRadioPage() {
        if (!this::radioList.isInitialized) {
            return
        }
        renderer.renderRadioPage(
            statusView = radioStatusValue,
            list = radioList,
            hasSession = hasSession(),
            loading = radioLoading,
            radios = recommendedRadios,
            selectedRadioId = selectedRadioId,
            radioSongs = radioSongs,
            onRadioClick = { radio -> requestRadioSongs(radio) },
            onTrackClick = { index, track ->
                appendRuntimeLog("kugou radio song click index=$index hash=${track.playbackRef.hash}")
                captureContentEvent("kugou_radio_session_start", "radio_songs", itemCount = radioSongs.size)
                onStartRadioTrack(currentSelectedRadio(), radioSongs, track)
            },
            onLike = { track -> onLikeTrack(track) }
        )
    }

    fun renderDiscoverPage() {
        if (!this::discoverTagList.isInitialized) {
            return
        }
        renderer.renderDiscoverPage(
            statusView = discoverStatusValue,
            tagList = discoverTagList,
            playlistList = discoverPlaylistList,
            hasSession = hasSession(),
            loading = discoverLoading,
            tags = discoverTags,
            selectedTagId = selectedDiscoverTagId,
            playlists = discoverPlaylists,
            selectedPlaylistId = selectedPlaylistId,
            songs = discoverSongs,
            onTagClick = { tagId -> requestPlaylistsByTag(tagId) },
            onPlaylistClick = { playlist -> requestPlaylistSongs(playlist) },
            onTrackClick = { index, track ->
                appendRuntimeLog("kugou playlist song click index=$index hash=${track.playbackRef.hash}")
                captureContentEvent("kugou_queue_start", "discover_playlist", itemCount = discoverSongs.size)
                onPlayQueuedTrack(track, discoverSongs, "discover_playlist")
            },
            onLike = { track -> onLikeTrack(track) }
        )
    }

    private fun requestRadioSongs(radio: SourceRadio) {
        val baseUrl = resolveBaseUrl()
        val session = getSessionKey().trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasSession()) {
            renderRadioPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_fm_songs", true)) {
            return
        }
        selectedRadioId = radio.sourceRadioId
        radioSongs = emptyList()
        radioLoading = true
        renderRadioPage()
        backgroundExecutor.execute {
            val result = webApiClient().getRadioSongs(baseUrl, session, radio.sourceRadioId, radio.type)
            val mapped = result?.first.orEmpty().map { song ->
                SourceTrack(
                    source = MusicSource.KUGOU,
                    sourceTrackId = song.audioId.ifBlank { song.albumAudioId.ifBlank { song.hash } },
                    title = song.name,
                    artist = song.singerName,
                    album = "",
                    coverUrl = song.coverUrl,
                    durationMs = song.durationMs,
                    playbackRef = SourcePlaybackRef(
                        source = MusicSource.KUGOU,
                        primaryId = song.audioId.ifBlank { song.hash },
                        hash = song.hash,
                        albumId = song.albumId,
                        albumAudioId = song.albumAudioId,
                        quality = "128",
                        requiresLogin = true
                    ),
                    capabilities = KUGOU_TRACK_CAPABILITIES
                )
            }
            activity.runOnUiThread {
                radioLoading = false
                if (result == null) {
                    captureContentEvent("kugou_content_load_failed", "radio_songs", errorCode = "KUGOU_CONTENT_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_radio_songs_failed))
                    renderRadioPage()
                    return@runOnUiThread
                }
                updateSessionKeyIfPresent(result.second)
                radioSongs = mapped
                captureContentEvent("kugou_content_load_success", "radio_songs", itemCount = mapped.size)
                renderRadioPage()
                setFeedbackText(activity.getString(R.string.feedback_kugou_radio_songs_success, mapped.size))
                mapped.firstOrNull()?.let { firstTrack ->
                    onStartRadioTrack(radio, mapped, firstTrack)
                }
            }
        }
    }

    private fun currentSelectedRadio(): SourceRadio? {
        return recommendedRadios.firstOrNull { it.sourceRadioId == selectedRadioId }
    }

    private fun requestPlaylistsByTag(tagId: Int) {
        val baseUrl = resolveBaseUrl()
        val session = getSessionKey().trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasSession()) {
            renderDiscoverPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_top_playlist", true)) {
            return
        }
        selectedDiscoverTagId = tagId
        selectedPlaylistId = ""
        discoverPlaylists = emptyList()
        discoverSongs = emptyList()
        discoverLoading = true
        renderDiscoverPage()
        backgroundExecutor.execute {
            val result = webApiClient().getPlaylistsByTag(baseUrl, session, tagId)
            val mapped = result?.first.orEmpty().map { playlist ->
                SourcePlaylist(
                    source = MusicSource.KUGOU,
                    sourcePlaylistId = playlist.listId.ifBlank { playlist.globalId },
                    title = playlist.name,
                    globalId = playlist.globalId,
                    coverUrl = playlist.coverUrl,
                    subtitle = listOf(playlist.creatorName, playlist.intro).filter { it.isNotBlank() }.joinToString(" · "),
                    tagId = tagId,
                    capabilities = KUGOU_PLAYLIST_CAPABILITIES
                )
            }
            activity.runOnUiThread {
                discoverLoading = false
                if (result == null) {
                    captureContentEvent("kugou_content_load_failed", "playlists_by_tag", errorCode = "KUGOU_CONTENT_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_playlist_failed))
                    renderDiscoverPage()
                    return@runOnUiThread
                }
                updateSessionKeyIfPresent(result.second)
                discoverPlaylists = mapped
                captureContentEvent("kugou_content_load_success", "playlists_by_tag", itemCount = mapped.size)
                renderDiscoverPage()
                setFeedbackText(
                    if (mapped.isEmpty()) {
                        activity.getString(R.string.feedback_kugou_playlist_empty)
                    } else {
                        activity.getString(R.string.feedback_kugou_playlist_success, mapped.size)
                    }
                )
            }
        }
    }

    private fun requestPlaylistSongs(playlist: SourcePlaylist) {
        val baseUrl = resolveBaseUrl()
        val session = getSessionKey().trim()
        val playlistId = playlist.globalId.ifBlank { playlist.sourcePlaylistId }
        if (baseUrl.isEmpty() || session.isEmpty() || playlistId.isEmpty() || !hasSession()) {
            renderDiscoverPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_playlist_songs", true)) {
            return
        }
        selectedPlaylistId = playlist.sourcePlaylistId
        discoverSongs = emptyList()
        discoverLoading = true
        renderDiscoverPage()
        backgroundExecutor.execute {
            val result = webApiClient().getPlaylistSongs(baseUrl, session, playlistId, pageSize = 30)
            val mapped = result?.first.orEmpty().map { song ->
                SourceTrack(
                    source = MusicSource.KUGOU,
                    sourceTrackId = song.fileId.ifBlank { song.mixSongId.ifBlank { song.hash } },
                    title = song.name,
                    artist = song.singers,
                    album = song.albumName,
                    coverUrl = song.coverUrl,
                    durationMs = song.durationMs,
                    playbackRef = SourcePlaybackRef(
                        source = MusicSource.KUGOU,
                        primaryId = song.fileId.ifBlank { song.hash },
                        hash = song.hash,
                        albumId = song.albumId,
                        albumAudioId = song.mixSongId,
                        quality = "128",
                        requiresLogin = true
                    ),
                    capabilities = KUGOU_TRACK_CAPABILITIES
                )
            }
            activity.runOnUiThread {
                discoverLoading = false
                if (result == null) {
                    captureContentEvent("kugou_content_load_failed", "playlist_songs", errorCode = "KUGOU_CONTENT_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_playlist_songs_failed))
                    renderDiscoverPage()
                    return@runOnUiThread
                }
                updateSessionKeyIfPresent(result.second)
                discoverSongs = mapped
                captureContentEvent("kugou_content_load_success", "playlist_songs", itemCount = mapped.size)
                renderDiscoverPage()
                setFeedbackText(activity.getString(R.string.feedback_kugou_playlist_songs_success, mapped.size))
            }
        }
    }

    private fun updateSessionKeyIfPresent(sessionKey: String) {
        if (sessionKey.isNotBlank()) {
            updateSessionKey(sessionKey)
        }
    }

    private fun captureContentEvent(
        eventName: String,
        stage: String,
        itemCount: Int? = null,
        errorCode: String? = null
    ) {
        val properties = linkedMapOf<String, Any?>(
            "source" to "kugou",
            "feature" to "kugou_content",
            "stage" to stage
        )
        if (itemCount != null) {
            properties["item_count"] = itemCount
        }
        if (!errorCode.isNullOrBlank()) {
            properties["error_code"] = errorCode
        }
        PostHogTracker.capture(
            context = activity.applicationContext,
            eventName = eventName,
            properties = properties,
            priority = if (errorCode.isNullOrBlank()) PostHogTracker.Priority.NORMAL else PostHogTracker.Priority.HIGH
        )
    }

    companion object {
        private const val DISCOVER_TAG_PREVIEW_LIMIT = 12
        private val KUGOU_TRACK_CAPABILITIES = setOf(
            SourceCapability.PLAY,
            SourceCapability.LIKE,
            SourceCapability.REQUIRES_LOGIN
        )
        private val KUGOU_RADIO_CAPABILITIES = setOf(
            SourceCapability.RADIO,
            SourceCapability.PLAY,
            SourceCapability.REQUIRES_LOGIN
        )
        private val KUGOU_PLAYLIST_CAPABILITIES = setOf(
            SourceCapability.PLAYLIST,
            SourceCapability.PLAY,
            SourceCapability.REQUIRES_LOGIN
        )
    }
}
