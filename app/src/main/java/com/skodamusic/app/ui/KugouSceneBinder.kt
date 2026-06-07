package com.skodamusic.app.ui

import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.skodamusic.app.R
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.kugou.KugouSceneContentClient
import com.skodamusic.app.kugou.KugouSceneItem
import com.skodamusic.app.model.MusicSource
import com.skodamusic.app.model.SourceCapability
import com.skodamusic.app.model.SourcePlaybackRef
import com.skodamusic.app.model.SourceTrack
import com.skodamusic.app.observability.PostHogTracker

class KugouSceneBinder(
    private val activity: AppCompatActivity,
    private val backgroundExecutor: AppBackgroundExecutor,
    private val sceneClient: () -> KugouSceneContentClient,
    private val renderer: KugouContentRenderer,
    private val getSessionKey: () -> String,
    private val hasSession: () -> Boolean,
    private val ensureWifiConnectedForNetworkRequest: (String, Boolean) -> Boolean,
    private val setFeedbackText: (String) -> Unit,
    private val requestLogin: (String, KugouLoginRecoveryCoordinator.PendingAction) -> Unit,
    private val appendRuntimeLog: (String) -> Unit,
    private val onPlayQueuedTrack: (SourceTrack, List<SourceTrack>, String) -> Unit,
    private val onLikeTrack: (SourceTrack) -> Unit
) {
    private lateinit var statusView: TextView
    private lateinit var tabList: LinearLayout
    private lateinit var songList: LinearLayout

    private var scenes: List<KugouSceneItem> = emptyList()
    private var songs: List<SourceTrack> = emptyList()
    private var selectedSceneId: String = ""
    private var loading: Boolean = false
    private var loadFailed: Boolean = false
    private var tabsExpanded: Boolean = false

    fun bindViews(statusView: TextView, tabList: LinearLayout, songList: LinearLayout) {
        this.statusView = statusView
        this.tabList = tabList
        this.songList = songList
    }

    fun clearContentState() {
        scenes = emptyList()
        songs = emptyList()
        selectedSceneId = ""
        loading = false
        loadFailed = false
        tabsExpanded = false
    }

    fun requestSceneList() {
        if (getSessionKey().trim().isEmpty() || !hasSession()) {
            renderScenePage()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            requestLogin("scene_missing_session", KugouLoginRecoveryCoordinator.PendingAction.SCENE_PAGE)
            return
        }
        if (loading || scenes.isNotEmpty()) {
            renderScenePage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_scene_list", true)) {
            return
        }
        loading = true
        loadFailed = false
        renderScenePage()
        captureSceneEvent("kugou_direct_content_request", "scene_list")
        backgroundExecutor.execute {
            val result = sceneClient().getSceneLists()
            activity.runOnUiThread {
                loading = false
                if (result == null) {
                    appendRuntimeLog("kugou direct scene list failed")
                    loadFailed = true
                    captureSceneEvent("kugou_content_load_failed", "scene_list", errorCode = "KUGOU_DIRECT_SCENE_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_scene_failed))
                    renderScenePage()
                    return@runOnUiThread
                }
                scenes = result
                loadFailed = false
                captureSceneEvent("kugou_content_load_success", "scene_list", itemCount = result.size)
                renderScenePage()
                if (result.isNotEmpty()) {
                    requestSceneSongs(result[0])
                } else {
                    setFeedbackText(activity.getString(R.string.feedback_kugou_scene_empty))
                }
            }
        }
    }

    fun renderScenePage() {
        if (!this::tabList.isInitialized) {
            return
        }
        renderer.renderScenePage(
            statusView = statusView,
            tabList = tabList,
            songList = songList,
            hasSession = hasSession(),
            loading = loading,
            loadFailed = loadFailed,
            scenes = scenes,
            selectedSceneId = selectedSceneId,
            expanded = tabsExpanded,
            songs = songs,
            onRetry = { requestSceneList() },
            onToggleExpanded = {
                tabsExpanded = !tabsExpanded
                captureSceneEvent(
                    eventName = "kugou_scene_tab_toggle",
                    stage = if (tabsExpanded) "expand" else "collapse",
                    itemCount = scenes.size
                )
                renderScenePage()
            },
            onSceneClick = { scene ->
                tabsExpanded = false
                requestSceneSongs(scene)
            },
            onTrackClick = { index, track ->
                appendRuntimeLog("kugou scene song click index=$index hash=${shortId(track.playbackRef.hash)}")
                captureSceneEvent("kugou_queue_start", "scene_songs", itemCount = songs.size)
                onPlayQueuedTrack(track, songs, "scene_songs")
            },
            onLike = { track -> onLikeTrack(track) }
        )
    }

    private fun requestSceneSongs(scene: KugouSceneItem) {
        if (getSessionKey().trim().isEmpty() || !hasSession()) {
            renderScenePage()
            setFeedbackText(activity.getString(R.string.feedback_need_kugou))
            requestLogin("scene_songs_missing_session", KugouLoginRecoveryCoordinator.PendingAction.SCENE_PAGE)
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("kugou_scene_songs", true)) {
            return
        }
        selectedSceneId = scene.sceneId
        songs = emptyList()
        loading = true
        loadFailed = false
        renderScenePage()
        captureSceneEvent("kugou_direct_content_request", "scene_songs")
        backgroundExecutor.execute {
            val result = sceneClient().getSceneMusic(scene.sceneId) ?: sceneClient().getSceneAudios(scene.sceneId)
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
                loading = false
                if (result == null) {
                    appendRuntimeLog("kugou direct scene songs failed scene=${shortId(scene.sceneId)}")
                    captureSceneEvent("kugou_content_load_failed", "scene_songs", errorCode = "KUGOU_DIRECT_SCENE_FAILED")
                    setFeedbackText(activity.getString(R.string.feedback_kugou_scene_songs_failed))
                    renderScenePage()
                    return@runOnUiThread
                }
                songs = mapped
                captureSceneEvent("kugou_content_load_success", "scene_songs", itemCount = mapped.size)
                renderScenePage()
                setFeedbackText(activity.getString(R.string.feedback_kugou_scene_songs_success, mapped.size))
            }
        }
    }

    private fun captureSceneEvent(
        eventName: String,
        stage: String,
        itemCount: Int? = null,
        errorCode: String? = null
    ) {
        val properties = linkedMapOf<String, Any?>(
            "source" to "kugou",
            "feature" to "kugou_scene",
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
        private val KUGOU_TRACK_CAPABILITIES = setOf(
            SourceCapability.PLAY,
            SourceCapability.LIKE,
            SourceCapability.REQUIRES_LOGIN
        )
    }
}
