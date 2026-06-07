package com.skodamusic.app.ui

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.skodamusic.app.R
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.kugou.KugouDirectContentClient
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
    private val directContentClient: () -> KugouDirectContentClient,
    private val renderer: KugouContentRenderer,
    private val getSessionKey: () -> String,
    private val hasSession: () -> Boolean,
    private val ensureWifiConnectedForNetworkRequest: (String, Boolean) -> Boolean,
    private val setFeedbackText: (String) -> Unit,
    private val requestLogin: (String, KugouLoginRecoveryCoordinator.PendingAction) -> Unit,
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
    private var radioLoadFailed: Boolean = false
    private var selectedRadioId: String = ""
    private var discoverTags: List<Pair<Int, String>> = emptyList()
    private var discoverPlaylists: List<SourcePlaylist> = emptyList()
    private var discoverSongs: List<SourceTrack> = emptyList()
    private var discoverLoading: Boolean = false
    private var discoverLoadFailed: Boolean = false
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
        radioLoadFailed = false
        selectedRadioId = ""
        discoverTags = emptyList()
        discoverPlaylists = emptyList()
        discoverSongs = emptyList()
        discoverLoading = false
        discoverLoadFailed = false
        selectedDiscoverTagId = -1
        selectedPlaylistId = ""
    }

    fun requestRecommendedSongs(
        onFinished: (() -> Unit)? = null,
        renderHome: () -> Unit,
        onLoaded: ((List<SourceTrack>) -> Unit)? = null
    ) {
        val session = getSessionKey().trim()
        if (session.isEmpty() || !hasSession()) {
            onFinished?.invoke()
            renderHome()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            requestLogin("recommend_missing_session", KugouLoginRecoveryCoordinator.PendingAction.HOME_RECOMMEND)
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
        captureContentEvent("kugou_direct_content_request", "recommend_songs")
        backgroundExecutor.execute {
            val songs = directContentClient().getRecommendedSongs()
            val mapped = songs.orEmpty().map { song ->
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
                if (songs == null) {
                    appendRuntimeLog("kugou direct recommend songs failed")
                    captureContentEvent("kugou_content_load_failed", "recommend_songs", errorCode = "KUGOU_DIRECT_CONTENT_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_recommend_failed))
                    return@runOnUiThread
                }
                recommendedTracks = mapped
                captureContentEvent("kugou_content_load_success", "recommend_songs", itemCount = mapped.size)
                renderHome()
                onLoaded?.invoke(mapped)
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
        val session = getSessionKey().trim()
        if (session.isEmpty() || !hasSession()) {
            renderRadioPage()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            requestLogin("radio_missing_session", KugouLoginRecoveryCoordinator.PendingAction.RADIO_PAGE)
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
        radioLoadFailed = false
        renderRadioPage()
        captureContentEvent("kugou_direct_content_request", "recommended_radios")
        backgroundExecutor.execute {
            val result = directContentClient().getRecommendedRadios()
            val mapped = result.orEmpty().map { radio ->
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
                    appendRuntimeLog("kugou direct recommended radios failed")
                    captureContentEvent("kugou_content_load_failed", "recommended_radios", errorCode = "KUGOU_DIRECT_CONTENT_FAILED")
                    radioLoadFailed = true
                    setFeedbackText(activity.getString(R.string.feedback_kugou_radio_failed))
                    renderRadioPage()
                    return@runOnUiThread
                }
                recommendedRadios = mapped
                radioLoadFailed = false
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
        val session = getSessionKey().trim()
        if (session.isEmpty() || !hasSession()) {
            renderDiscoverPage()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            requestLogin("discover_missing_session", KugouLoginRecoveryCoordinator.PendingAction.DISCOVER_PAGE)
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
        discoverLoadFailed = false
        renderDiscoverPage()
        captureContentEvent("kugou_direct_content_request", "discover_tags")
        backgroundExecutor.execute {
            val result = directContentClient().getPlaylistTags()
            val tags = result.orEmpty()
                .take(DISCOVER_TAG_PREVIEW_LIMIT)
                .map { it.tagId to "${it.categoryName} · ${it.tagName}" }
            activity.runOnUiThread {
                discoverLoading = false
                if (result == null) {
                    appendRuntimeLog("kugou direct discover tags failed")
                    captureContentEvent("kugou_content_load_failed", "discover_tags", errorCode = "KUGOU_DIRECT_CONTENT_FAILED")
                    discoverLoadFailed = true
                    setFeedbackText(activity.getString(R.string.feedback_kugou_discover_failed))
                    renderDiscoverPage()
                    return@runOnUiThread
                }
                discoverTags = tags
                discoverLoadFailed = false
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
                appendRuntimeLog("kugou recommend click index=$index hash=${shortId(track.playbackRef.hash)}")
                captureContentEvent("kugou_queue_start", "home_recommend", itemCount = recommendedTracks.size)
                onPlayQueuedTrack(track, recommendedTracks, "home_recommend")
            },
            onLike = { track -> onLikeTrack(track) }
        )
    }

    fun recommendedTracksSnapshot(): List<SourceTrack> {
        return recommendedTracks
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
            loadFailed = radioLoadFailed,
            selectedRadioId = selectedRadioId,
            radioSongs = radioSongs,
            onRetry = { requestRecommendedRadios() },
            onRadioClick = { radio -> requestRadioSongs(radio) },
            onTrackClick = { index, track ->
                appendRuntimeLog("kugou radio song click index=$index hash=${shortId(track.playbackRef.hash)}")
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
            loadFailed = discoverLoadFailed,
            selectedTagId = selectedDiscoverTagId,
            playlists = discoverPlaylists,
            selectedPlaylistId = selectedPlaylistId,
            songs = discoverSongs,
            onRetry = { requestDiscoverTags() },
            onTagClick = { tagId -> requestPlaylistsByTag(tagId) },
            onPlaylistClick = { playlist -> requestPlaylistSongs(playlist) },
            onTrackClick = { index, track ->
                appendRuntimeLog("kugou playlist song click index=$index hash=${shortId(track.playbackRef.hash)}")
                captureContentEvent("kugou_queue_start", "discover_playlist", itemCount = discoverSongs.size)
                onPlayQueuedTrack(track, discoverSongs, "discover_playlist")
            },
            onLike = { track -> onLikeTrack(track) }
        )
    }

    private fun requestRadioSongs(radio: SourceRadio) {
        val session = getSessionKey().trim()
        if (session.isEmpty() || !hasSession()) {
            renderRadioPage()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            requestLogin("radio_songs_missing_session", KugouLoginRecoveryCoordinator.PendingAction.RADIO_PAGE)
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_fm_songs", true)) {
            return
        }
        selectedRadioId = radio.sourceRadioId
        radioSongs = emptyList()
        radioLoading = true
        renderRadioPage()
        captureContentEvent("kugou_direct_content_request", "radio_songs")
        backgroundExecutor.execute {
            val result = directContentClient().getRadioSongs(radio.sourceRadioId, radio.type)
            val mapped = result.orEmpty().map { song ->
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
                    appendRuntimeLog("kugou direct radio songs failed")
                    captureContentEvent("kugou_content_load_failed", "radio_songs", errorCode = "KUGOU_DIRECT_CONTENT_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_radio_songs_failed))
                    renderRadioPage()
                    return@runOnUiThread
                }
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
        val session = getSessionKey().trim()
        if (session.isEmpty() || !hasSession()) {
            renderDiscoverPage()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            requestLogin("playlists_missing_session", KugouLoginRecoveryCoordinator.PendingAction.DISCOVER_PAGE)
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
        captureContentEvent("kugou_direct_content_request", "playlists_by_tag")
        backgroundExecutor.execute {
            val result = directContentClient().getRecommendedPlaylists(categoryId = tagId)
            val mapped = result.orEmpty().map { playlist ->
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
                    appendRuntimeLog("kugou direct playlists failed")
                    captureContentEvent("kugou_content_load_failed", "playlists_by_tag", errorCode = "KUGOU_DIRECT_CONTENT_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_playlist_failed))
                    renderDiscoverPage()
                    return@runOnUiThread
                }
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
        val session = getSessionKey().trim()
        val playlistId = playlist.globalId.ifBlank { playlist.sourcePlaylistId }
        if (session.isEmpty() || playlistId.isEmpty() || !hasSession()) {
            renderDiscoverPage()
            if (session.isEmpty() || !hasSession()) {
                setFeedbackText(activity.getString(R.string.feedback_need_kugou))
                requestLogin("playlist_songs_missing_session", KugouLoginRecoveryCoordinator.PendingAction.DISCOVER_PAGE)
            }
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_playlist_songs", true)) {
            return
        }
        selectedPlaylistId = playlist.sourcePlaylistId
        discoverSongs = emptyList()
        discoverLoading = true
        renderDiscoverPage()
        captureContentEvent("kugou_direct_content_request", "playlist_songs")
        backgroundExecutor.execute {
            val result = directContentClient().getPlaylistSongs(playlistId, pageSize = 30)
            val mapped = result.orEmpty().map { song ->
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
                    appendRuntimeLog("kugou direct playlist songs failed")
                    captureContentEvent("kugou_content_load_failed", "playlist_songs", errorCode = "KUGOU_DIRECT_CONTENT_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_playlist_songs_failed))
                    renderDiscoverPage()
                    return@runOnUiThread
                }
                discoverSongs = mapped
                captureContentEvent("kugou_content_load_success", "playlist_songs", itemCount = mapped.size)
                renderDiscoverPage()
                setFeedbackText(activity.getString(R.string.feedback_kugou_playlist_songs_success, mapped.size))
            }
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

    private fun shortId(value: String): String {
        val clean = value.trim()
        return if (clean.length <= 8) clean else clean.take(4) + "..." + clean.takeLast(4)
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
