package com.skodamusic.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private interface PlaybackEngineCallback {
        fun onPrepared()
        fun onCompletion()
        fun onError(code: Int, detail: String)
        fun onBufferingStart()
        fun onBufferingEnd()
    }

    private interface PlaybackEngine {
        fun prepare(source: Uri, callback: PlaybackEngineCallback)
        fun play(): Boolean
        fun pause()
        fun isPlaying(): Boolean
        fun release()
    }

    private class ExoPlaybackEngine(
        private val context: Context
    ) : PlaybackEngine {
        private var exoPlayer: SimpleExoPlayer? = null

        override fun prepare(source: Uri, callback: PlaybackEngineCallback) {
            releaseInternal()
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    LOAD_CONTROL_MIN_BUFFER_MS,
                    LOAD_CONTROL_MAX_BUFFER_MS,
                    LOAD_CONTROL_PLAYBACK_MS,
                    LOAD_CONTROL_REBUFFER_MS
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            val player = SimpleExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .build()
            exoPlayer = player

            var preparedNotified = false
            var buffering = false

            player.setAudioAttributes(
                ExoAudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            if (!buffering) {
                                buffering = true
                                callback.onBufferingStart()
                            }
                        }
                        Player.STATE_READY -> {
                            if (!preparedNotified) {
                                preparedNotified = true
                                callback.onPrepared()
                            }
                            if (buffering) {
                                buffering = false
                                callback.onBufferingEnd()
                            }
                        }
                        Player.STATE_ENDED -> {
                            callback.onCompletion()
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    callback.onError(error.errorCode, error.message ?: error.javaClass.simpleName)
                }
            })
            player.setMediaItem(MediaItem.fromUri(source))
            player.prepare()
        }

        override fun play(): Boolean {
            val player = exoPlayer ?: return false
            return try {
                player.playWhenReady = true
                player.play()
                true
            } catch (_: Exception) {
                false
            }
        }

        override fun pause() {
            try {
                exoPlayer?.pause()
            } catch (_: Exception) {
            }
        }

        override fun isPlaying(): Boolean {
            return try {
                exoPlayer?.isPlaying == true
            } catch (_: Exception) {
                false
            }
        }

        override fun release() {
            releaseInternal()
        }

        private fun releaseInternal() {
            val player = exoPlayer ?: return
            try {
                player.stop()
            } catch (_: Exception) {
            }
            try {
                player.release()
            } catch (_: Exception) {
            }
            exoPlayer = null
        }
    }

    private enum class ListSource {
        QUEUE,
        LIBRARY
    }

    private data class UiState(
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

    private data class EmbyCredentials(
        val baseUrl: String,
        val username: String,
        val password: String
    )

    private data class LrcApiCredentials(
        val baseUrl: String
    )

    private data class EmbyTrack(
        val id: String,
        val title: String
    )

    private data class AuthByNameResult(
        val accessToken: String,
        val userId: String
    )

    private data class HttpResult(
        val code: Int,
        val payload: String
    )

    private data class EmbyLoadResult(
        val success: Boolean,
        val statusText: String,
        val feedbackText: String,
        val tracks: List<EmbyTrack> = emptyList(),
        val embyBase: String? = null,
        val embyUserId: String? = null,
        val accessToken: String? = null
    )

    private data class LrcApiTestResult(
        val success: Boolean,
        val statusText: String,
        val feedbackText: String
    )

    private lateinit var embyBaseUrlInput: EditText
    private lateinit var embyUsernameInput: EditText
    private lateinit var embyPasswordInput: EditText
    private lateinit var lrcApiBaseUrlInput: EditText
    private lateinit var embyStatusValue: TextView
    private lateinit var lrcApiStatusValue: TextView
    private lateinit var trackValue: TextView
    private lateinit var playbackValue: TextView
    private lateinit var actionFeedback: TextView
    private lateinit var runtimeLogPreview: TextView
    private lateinit var queueTracksContainer: LinearLayout
    private lateinit var libraryTracksContainer: LinearLayout
    private lateinit var playPauseButton: Button
    private lateinit var nextButton: Button
    private lateinit var recommendQueueButton: Button
    private lateinit var testEmbyButton: Button
    private lateinit var testLrcApiButton: Button
    private lateinit var navHomeButton: Button
    private lateinit var navQueueButton: Button
    private lateinit var navLibraryButton: Button
    private lateinit var navSettingsButton: Button
    private lateinit var pageHome: View
    private lateinit var pageQueue: View
    private lateinit var pageLibrary: View
    private lateinit var pageSettings: View
    private lateinit var uiState: UiState

    private var loadedTracks: List<EmbyTrack> = emptyList()
    private var embySessionBaseUrl: String? = null
    private var embySessionUserId: String? = null
    private var embyAccessToken: String? = null
    private var currentTrackIndex: Int = 0
    private var playbackEngine: PlaybackEngine? = null
    private var playbackRequestId: Int = 0
    private val runtimeLogLines = mutableListOf<String>()
    private val runtimeLogLock = Any()
    private var runtimeLogDialog: Dialog? = null
    private var runtimeLogDialogText: TextView? = null
    private var selectedPage: Int = PAGE_HOME
    private var queueAutoRefreshInFlight: Boolean = false
    private var lastQueueAutoRefreshMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        embyBaseUrlInput = findViewById(R.id.emby_base_url_input)
        embyUsernameInput = findViewById(R.id.emby_username_input)
        embyPasswordInput = findViewById(R.id.emby_password_input)
        lrcApiBaseUrlInput = findViewById(R.id.lrcapi_base_url_input)
        embyStatusValue = findViewById(R.id.emby_status_value)
        lrcApiStatusValue = findViewById(R.id.lrcapi_status_value)
        trackValue = findViewById(R.id.track_value)
        playbackValue = findViewById(R.id.playback_value)
        actionFeedback = findViewById(R.id.action_feedback)
        runtimeLogPreview = findViewById(R.id.runtime_log_preview)
        queueTracksContainer = findViewById(R.id.queue_tracks_container)
        libraryTracksContainer = findViewById(R.id.library_tracks_container)
        playPauseButton = findViewById(R.id.btn_play_pause)
        nextButton = findViewById(R.id.btn_next)
        recommendQueueButton = findViewById(R.id.btn_recommend_queue)
        testEmbyButton = findViewById(R.id.btn_test_emby)
        testLrcApiButton = findViewById(R.id.btn_test_lrcapi)
        navHomeButton = findViewById(R.id.nav_home)
        navQueueButton = findViewById(R.id.nav_queue)
        navLibraryButton = findViewById(R.id.nav_library)
        navSettingsButton = findViewById(R.id.nav_settings)
        pageHome = findViewById(R.id.page_home)
        pageQueue = findViewById(R.id.page_queue)
        pageLibrary = findViewById(R.id.page_library)
        pageSettings = findViewById(R.id.page_settings)
        bindNavigation()
        switchPage(PAGE_HOME)
        loadSavedCredentials()

        uiState = UiState(
            currentTrack = getString(R.string.track_not_loaded),
            playbackStatusRes = R.string.status_paused,
            playPauseLabelRes = R.string.action_play,
            feedbackText = getString(R.string.feedback_need_emby),
            embyStatusText = getString(R.string.emby_status_not_tested),
            lrcApiStatusText = getString(R.string.lrcapi_status_not_tested),
            isPlaying = false,
            playPauseEnabled = false,
            nextEnabled = false,
            testEmbyEnabled = true,
            testLrcApiEnabled = true
        )
        render(uiState)
        bindActions()
        rebuildTrackLists()
        appendRuntimeLog("app boot completed")
        maybeAutoRefreshQueueRecommendations("app-startup")
    }

    override fun onDestroy() {
        appendRuntimeLog("activity destroyed")
        runtimeLogDialog?.dismiss()
        super.onDestroy()
        playbackRequestId += 1
        releasePlayer()
    }

    private fun bindActions() {
        runtimeLogPreview.setOnClickListener {
            showRuntimeLogsFullscreen()
        }
        findViewById<TextView>(R.id.runtime_log_label).setOnClickListener {
            showRuntimeLogsFullscreen()
        }
        recommendQueueButton.setOnClickListener {
            requestQueueRecommendations()
        }

        playPauseButton.setOnClickListener {
            if (loadedTracks.isEmpty()) {
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
                return@setOnClickListener
            }

            if (uiState.isPlaying) {
                appendRuntimeLog("ui click play/pause -> pause")
                pausePlayback()
                showToast(R.string.toast_paused)
            } else {
                appendRuntimeLog("ui click play/pause -> play")
                startOrResumePlayback()
                showToast(R.string.toast_playing)
            }
        }

        nextButton.setOnClickListener {
            appendRuntimeLog("ui click next")
            if (loadedTracks.isEmpty()) {
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
                return@setOnClickListener
            }
            val wasPlaying = uiState.isPlaying
            val nextTitle = moveToNextTrack()
            if (nextTitle == null) {
                updateState {
                    it.copy(
                        feedbackText = getString(R.string.feedback_end_of_queue),
                        nextEnabled = false
                    )
                }
                showToast(R.string.toast_end_of_queue)
                rebuildTrackLists()
                return@setOnClickListener
            }

            updateState {
                it.copy(
                    currentTrack = nextTitle,
                    feedbackText = getString(R.string.feedback_next_pressed),
                    nextEnabled = hasNextTrack()
                )
            }
            showToast(R.string.toast_next)
            rebuildTrackLists()
            if (wasPlaying) {
                playTrackAtCurrentIndex()
            } else {
                playbackRequestId += 1
                releasePlayer()
            }
        }

        testEmbyButton.setOnClickListener {
            val credentials = EmbyCredentials(
                baseUrl = embyBaseUrlInput.text.toString().trim(),
                username = embyUsernameInput.text.toString().trim(),
                password = embyPasswordInput.text.toString().trim()
            )
            appendRuntimeLog("ui click test emby base=${credentials.baseUrl}")

            if (credentials.baseUrl.isEmpty()) {
                updateState {
                    it.copy(
                        embyStatusText = getString(R.string.emby_status_failed),
                        feedbackText = getString(R.string.feedback_emby_missing)
                    )
                }
                showToast(R.string.toast_emby_failed)
                return@setOnClickListener
            }

            if (credentials.username.isEmpty() || credentials.password.isEmpty()) {
                updateState {
                    it.copy(
                        embyStatusText = getString(R.string.emby_status_failed),
                        feedbackText = getString(R.string.feedback_emby_need_auth)
                    )
                }
                showToast(R.string.toast_emby_failed)
                return@setOnClickListener
            }

            requestTracksFromEmby(credentials)
        }

        testLrcApiButton.setOnClickListener {
            val credentials = LrcApiCredentials(
                baseUrl = lrcApiBaseUrlInput.text.toString().trim()
            )
            appendRuntimeLog("ui click test lrcapi base=${credentials.baseUrl}")
            if (credentials.baseUrl.isEmpty()) {
                updateState {
                    it.copy(
                        lrcApiStatusText = getString(R.string.lrcapi_status_failed),
                        feedbackText = getString(R.string.feedback_lrcapi_missing)
                    )
                }
                showToast(R.string.toast_lrcapi_failed)
                return@setOnClickListener
            }
            requestLrcApiConnectionTest(credentials)
        }
    }

    private fun bindNavigation() {
        navHomeButton.setOnClickListener {
            switchPage(PAGE_HOME)
        }
        navQueueButton.setOnClickListener {
            switchPage(PAGE_QUEUE)
        }
        navLibraryButton.setOnClickListener {
            switchPage(PAGE_LIBRARY)
        }
        navSettingsButton.setOnClickListener {
            switchPage(PAGE_SETTINGS)
        }
    }

    private fun switchPage(targetPage: Int) {
        selectedPage = targetPage
        pageHome.visibility = if (selectedPage == PAGE_HOME) View.VISIBLE else View.GONE
        pageQueue.visibility = if (selectedPage == PAGE_QUEUE) View.VISIBLE else View.GONE
        pageLibrary.visibility = if (selectedPage == PAGE_LIBRARY) View.VISIBLE else View.GONE
        pageSettings.visibility = if (selectedPage == PAGE_SETTINGS) View.VISIBLE else View.GONE
        updateNavigationVisualState()
        if (selectedPage == PAGE_QUEUE) {
            maybeAutoRefreshQueueRecommendations("enter-queue-page")
        }
    }

    private fun updateNavigationVisualState() {
        setNavButtonSelected(navHomeButton, selectedPage == PAGE_HOME)
        setNavButtonSelected(navQueueButton, selectedPage == PAGE_QUEUE)
        setNavButtonSelected(navLibraryButton, selectedPage == PAGE_LIBRARY)
        setNavButtonSelected(navSettingsButton, selectedPage == PAGE_SETTINGS)
    }

    private fun setNavButtonSelected(button: Button, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.button_primary else R.drawable.button_secondary)
    }

    private fun rebuildTrackLists() {
        renderTrackContainer(
            container = queueTracksContainer,
            tracks = loadedTracks,
            highlightCurrent = true,
            emptyRes = R.string.queue_empty
        )
        renderTrackContainer(
            container = libraryTracksContainer,
            tracks = loadedTracks,
            highlightCurrent = false,
            emptyRes = R.string.library_empty
        )
    }

    private fun renderTrackContainer(
        container: LinearLayout,
        tracks: List<EmbyTrack>,
        highlightCurrent: Boolean,
        emptyRes: Int
    ) {
        container.removeAllViews()
        if (tracks.isEmpty()) {
            val empty = TextView(this)
            empty.setBackgroundResource(R.drawable.field_surface)
            empty.setText(emptyRes)
            empty.setTextColor(resources.getColor(R.color.text_secondary))
            empty.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            container.addView(
                empty,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }

        tracks.forEachIndexed { index, track ->
            val row = TextView(this)
            val isCurrent = highlightCurrent && index == currentTrackIndex
            row.text = if (isCurrent) "▶ ${track.title}" else track.title
            row.setTextColor(resources.getColor(R.color.text_primary))
            row.textSize = 14f
            row.setBackgroundResource(if (isCurrent) R.drawable.button_primary else R.drawable.field_surface)
            row.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            row.setOnClickListener {
                val source = if (highlightCurrent) ListSource.QUEUE else ListSource.LIBRARY
                playFromList(index, source)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dpToPx(8)
            }
            container.addView(row, params)
        }
    }

    private fun playFromList(index: Int, source: ListSource) {
        if (loadedTracks.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
            return
        }

        val safeIndex = index.coerceIn(0, loadedTracks.lastIndex)
        currentTrackIndex = safeIndex
        syncNativeQueueToCurrentIndex()
        val first = loadedTracks[currentTrackIndex].title

        val feedbackRes = if (source == ListSource.QUEUE) {
            R.string.feedback_play_now_queue
        } else {
            R.string.feedback_play_now_library
        }
        updateState {
            it.copy(
                currentTrack = first,
                nextEnabled = hasNextTrack(),
                feedbackText = getString(feedbackRes),
                isPlaying = true,
                playbackStatusRes = R.string.status_playing,
                playPauseLabelRes = R.string.action_pause
            )
        }
        rebuildTrackLists()
        playTrackAtCurrentIndex()
    }

    private fun requestQueueRecommendations() {
        if (loadedTracks.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
            showToast(R.string.toast_queue_recommend_failed)
            return
        }
        if (!NativePlaybackBridge.isAvailable()) {
            updateState {
                it.copy(
                    feedbackText = getString(R.string.feedback_native_unavailable),
                    nextEnabled = false
                )
            }
            showToast(R.string.toast_queue_recommend_failed)
            return
        }
        val base = embySessionBaseUrl
        val userId = embySessionUserId
        val token = embyAccessToken
        if (base.isNullOrBlank() || userId.isNullOrBlank() || token.isNullOrBlank()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_queue_recommend_missing_session)) }
            showToast(R.string.toast_queue_recommend_failed)
            return
        }

        recommendQueueButton.isEnabled = false
        updateState { it.copy(feedbackText = getString(R.string.feedback_queue_recommend_loading)) }
        appendRuntimeLog("queue recommend start size=${loadedTracks.size} currentIndex=$currentTrackIndex")

        Thread {
            val response = executeGet(
                endpoint = buildEmbyRecommendedItemsUrl(base, userId, token),
                token = token,
                requestLabel = "GET /Users/{id}/Items (Queue Recommend)",
                log = { message -> appendRuntimeLog("queue recommend $message") }
            )
            if (response.code !in 200..299) {
                runOnUiThread {
                    recommendQueueButton.isEnabled = true
                    updateState {
                        it.copy(
                            feedbackText = getString(
                                R.string.feedback_queue_recommend_failed_http,
                                response.code
                            )
                        )
                    }
                    showToast(R.string.toast_queue_recommend_failed)
                }
                return@Thread
            }

            val recommendedTracks = parseTrackItems(response.payload)
            if (recommendedTracks.isEmpty()) {
                runOnUiThread {
                    recommendQueueButton.isEnabled = true
                    updateState { it.copy(feedbackText = getString(R.string.feedback_queue_recommend_failed_empty)) }
                    showToast(R.string.toast_queue_recommend_failed)
                }
                return@Thread
            }

            runOnUiThread {
                val replacedCount = applyQueueRecommendationReplacement(recommendedTracks)
                recommendQueueButton.isEnabled = true
                if (replacedCount < 0) {
                    updateState { it.copy(feedbackText = getString(R.string.feedback_queue_recommend_failed_apply)) }
                    showToast(R.string.toast_queue_recommend_failed)
                    return@runOnUiThread
                }
                appendRuntimeLog(
                    "queue recommend success replaced=$replacedCount recommendations=${recommendedTracks.size}"
                )
                updateState {
                    it.copy(
                        feedbackText = getString(
                            R.string.feedback_queue_recommend_success,
                            replacedCount,
                            recommendedTracks.size
                        ),
                        nextEnabled = hasNextTrack()
                    )
                }
                showToast(R.string.toast_queue_recommend_success)
            }
        }.start()
    }

    private fun applyQueueRecommendationReplacement(recommendedTracks: List<EmbyTrack>): Int {
        if (loadedTracks.isEmpty()) {
            return -1
        }
        val previousTracks = loadedTracks
        val previousCurrentIndex = currentTrackIndex
        val safeCurrentIndex = currentTrackIndex.coerceIn(0, loadedTracks.lastIndex)
        val playedSegment = previousTracks.take(safeCurrentIndex + 1)
        if (playedSegment.isEmpty()) {
            return -1
        }

        loadedTracks = playedSegment + recommendedTracks
        currentTrackIndex = playedSegment.lastIndex
        if (!syncNativeQueueToCurrentIndex()) {
            loadedTracks = previousTracks
            currentTrackIndex = previousCurrentIndex
            return -1
        }

        rebuildTrackLists()
        return (previousTracks.size - (safeCurrentIndex + 1)).coerceAtLeast(0)
    }

    private fun hasNextTrack(): Boolean {
        if (loadedTracks.isEmpty()) {
            return false
        }
        return currentTrackIndex < loadedTracks.lastIndex
    }

    private fun moveToNextTrack(): String? {
        if (!hasNextTrack()) {
            return null
        }
        currentTrackIndex = (currentTrackIndex + 1).coerceAtMost(loadedTracks.lastIndex)
        syncNativeQueueToCurrentIndex()
        return loadedTracks[currentTrackIndex].title
    }

    private fun syncNativeQueueToCurrentIndex(): Boolean {
        if (!NativePlaybackBridge.isAvailable() || loadedTracks.isEmpty()) {
            return false
        }
        val first = NativePlaybackBridge.initializeQueue(loadedTracks.map { it.title }) ?: return false
        if (first.isEmpty()) {
            return false
        }
        for (i in 0 until currentTrackIndex) {
            val next = NativePlaybackBridge.nextTitle() ?: return false
            if (next.isEmpty()) {
                return false
            }
        }
        return true
    }

    private fun showToast(@StringRes textRes: Int) {
        val toast = Toast.makeText(this, textRes, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dpToPx(56))
        toast.show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun maybeAutoRefreshQueueRecommendations(trigger: String) {
        if (loadedTracks.isNotEmpty()) {
            return
        }
        if (queueAutoRefreshInFlight) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastQueueAutoRefreshMs < AUTO_QUEUE_REFRESH_COOLDOWN_MS) {
            return
        }
        val credentials = EmbyCredentials(
            baseUrl = embyBaseUrlInput.text.toString().trim(),
            username = embyUsernameInput.text.toString().trim(),
            password = embyPasswordInput.text.toString().trim()
        )
        if (credentials.baseUrl.isEmpty() || credentials.username.isEmpty() || credentials.password.isEmpty()) {
            appendRuntimeLog("queue auto refresh skip trigger=$trigger reason=missing-emby-credentials")
            return
        }
        lastQueueAutoRefreshMs = now
        queueAutoRefreshInFlight = true
        appendRuntimeLog("queue auto refresh start trigger=$trigger")
        requestTracksFromEmby(credentials) {
            queueAutoRefreshInFlight = false
            appendRuntimeLog("queue auto refresh finish trigger=$trigger tracks=${loadedTracks.size}")
        }
    }

    private fun requestTracksFromEmby(
        credentials: EmbyCredentials,
        onFinished: (() -> Unit)? = null
    ) {
        appendRuntimeLog("emby load start base=${credentials.baseUrl}")
        updateState {
            it.copy(
                testEmbyEnabled = false,
                embyStatusText = getString(R.string.emby_status_testing),
                feedbackText = getString(R.string.feedback_emby_testing)
            )
        }

        Thread {
            val result = fetchTracksFromEmby(credentials)
            runOnUiThread {
                try {
                    if (result.success) {
                        appendRuntimeLog("emby load success tracks=${result.tracks.size}")
                        loadedTracks = result.tracks
                        embySessionBaseUrl = result.embyBase
                        embySessionUserId = result.embyUserId
                        embyAccessToken = result.accessToken
                        currentTrackIndex = 0
                        playbackRequestId += 1
                        releasePlayer()
                        val firstTrack = loadedTracks.firstOrNull()?.title
                            ?: getString(R.string.track_not_loaded)
                        persistCredentials(credentials)
                        val nativeSynced = syncNativeQueueToCurrentIndex()
                        val feedbackTail = if (nativeSynced) {
                            result.feedbackText
                        } else {
                            result.feedbackText + "\n- native queue unavailable, playback stays in degraded mode"
                        }
                        updateState {
                            it.copy(
                                currentTrack = firstTrack,
                                playbackStatusRes = R.string.status_paused,
                                playPauseLabelRes = R.string.action_play,
                                isPlaying = false,
                                playPauseEnabled = true,
                                nextEnabled = hasNextTrack(),
                                testEmbyEnabled = true,
                                embyStatusText = result.statusText,
                                feedbackText = getString(R.string.feedback_emby_autosaved) + "\n- " + feedbackTail
                            )
                        }
                        rebuildTrackLists()
                        showToast(R.string.toast_emby_success)
                    } else {
                        appendRuntimeLog("emby load failed")
                        loadedTracks = emptyList()
                        embySessionBaseUrl = null
                        embySessionUserId = null
                        embyAccessToken = null
                        currentTrackIndex = 0
                        playbackRequestId += 1
                        releasePlayer()
                        updateState {
                            it.copy(
                                currentTrack = getString(R.string.track_not_loaded),
                                playbackStatusRes = R.string.status_paused,
                                playPauseLabelRes = R.string.action_play,
                                isPlaying = false,
                                playPauseEnabled = false,
                                nextEnabled = false,
                                testEmbyEnabled = true,
                                embyStatusText = result.statusText,
                                feedbackText = result.feedbackText
                            )
                        }
                        rebuildTrackLists()
                        showToast(R.string.toast_emby_failed)
                    }
                } finally {
                    onFinished?.invoke()
                }
            }
        }.start()
    }

    private fun requestLrcApiConnectionTest(credentials: LrcApiCredentials) {
        updateState {
            it.copy(
                testLrcApiEnabled = false,
                lrcApiStatusText = getString(R.string.lrcapi_status_testing),
                feedbackText = getString(R.string.feedback_lrcapi_testing)
            )
        }
        Thread {
            val result = testLrcApiConnection(credentials.baseUrl)
            runOnUiThread {
                if (result.success) {
                    persistLrcApiCredentials(credentials)
                    updateState {
                        it.copy(
                            testLrcApiEnabled = true,
                            lrcApiStatusText = result.statusText,
                            feedbackText = result.feedbackText
                        )
                    }
                    showToast(R.string.toast_lrcapi_success)
                } else {
                    updateState {
                        it.copy(
                            testLrcApiEnabled = true,
                            lrcApiStatusText = result.statusText,
                            feedbackText = result.feedbackText
                        )
                    }
                    showToast(R.string.toast_lrcapi_failed)
                }
            }
        }.start()
    }

    private fun testLrcApiConnection(baseUrl: String): LrcApiTestResult {
        val normalized = baseUrl.trim().trimEnd('/')
        if (!isHttpUrl(normalized)) {
            appendRuntimeLog("lrcapi test invalid-url base=$baseUrl")
            return LrcApiTestResult(
                success = false,
                statusText = getString(R.string.lrcapi_status_failed),
                feedbackText = getString(R.string.feedback_lrcapi_failed)
            )
        }
        var connection: HttpURLConnection? = null
        return try {
            appendRuntimeLog("lrcapi test GET $normalized")
            connection = (URL(normalized).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 6000
                instanceFollowRedirects = true
            }
            val code = connection.responseCode
            appendRuntimeLog("lrcapi test response code=$code")
            if (code in 200..499) {
                LrcApiTestResult(
                    success = true,
                    statusText = getString(R.string.lrcapi_status_connected),
                    feedbackText = getString(R.string.feedback_lrcapi_autosaved)
                )
            } else {
                LrcApiTestResult(
                    success = false,
                    statusText = getString(R.string.lrcapi_status_failed),
                    feedbackText = getString(R.string.feedback_lrcapi_failed)
                )
            }
        } catch (e: Exception) {
            appendRuntimeLog("lrcapi test exception type=${e.javaClass.simpleName} msg=${e.message}")
            LrcApiTestResult(
                success = false,
                statusText = getString(R.string.lrcapi_status_failed),
                feedbackText = getString(R.string.feedback_lrcapi_failed)
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun startOrResumePlayback() {
        val existing = playbackEngine
        if (existing != null) {
            if (existing.play()) {
                updateState {
                    it.copy(
                        isPlaying = true,
                        playbackStatusRes = R.string.status_playing,
                        playPauseLabelRes = R.string.action_pause,
                        feedbackText = getString(R.string.feedback_play_pressed)
                    )
                }
                return
            }
            releasePlayer()
        }
        playTrackAtCurrentIndex()
    }

    private fun pausePlayback() {
        playbackRequestId += 1
        playbackEngine?.pause()
        updateState {
            it.copy(
                isPlaying = false,
                playbackStatusRes = R.string.status_paused,
                playPauseLabelRes = R.string.action_play,
                feedbackText = getString(R.string.feedback_play_pressed)
            )
        }
    }

    private fun playTrackAtCurrentIndex() {
        if (loadedTracks.isEmpty()) {
            return
        }
        val base = embySessionBaseUrl
        val userId = embySessionUserId
        val token = embyAccessToken
        if (base.isNullOrBlank() || userId.isNullOrBlank() || token.isNullOrBlank()) {
            updateState {
                it.copy(
                    isPlaying = false,
                    playbackStatusRes = R.string.status_paused,
                    playPauseLabelRes = R.string.action_play,
                    feedbackText = "Action feedback: missing playback session"
                )
            }
            return
        }

        currentTrackIndex = currentTrackIndex.coerceIn(0, loadedTracks.lastIndex)
        val track = loadedTracks[currentTrackIndex]
        val downloadUrl = buildEmbyDownloadUrl(base, track.id, token)
        val requestId = ++playbackRequestId
        val streamPrepared = AtomicBoolean(false)
        val fallbackTriggered = AtomicBoolean(false)
        appendRuntimeLog("play request track=${track.title} downloadUrl=$downloadUrl requestId=$requestId")
        releasePlayer()

        fun triggerCachedFallback(feedback: String) {
            if (requestId != playbackRequestId) {
                return
            }
            if (!fallbackTriggered.compareAndSet(false, true)) {
                return
            }
            appendRuntimeLog("play fallback requestId=$requestId reason=$feedback")
            runOnUiThread {
                if (requestId != playbackRequestId) {
                    return@runOnUiThread
                }
                releasePlayer()
                updateState {
                    it.copy(
                        isPlaying = false,
                        playbackStatusRes = R.string.status_paused,
                        playPauseLabelRes = R.string.action_play,
                        feedbackText = feedback
                    )
                }
                downloadAndPlayTrack(track, base, token, requestId)
            }
        }

        try {
            val engine = ensurePlaybackEngine()
            appendRuntimeLog("play mode=download-only-url requestId=$requestId")
            engine.prepare(
                source = Uri.parse(downloadUrl),
                callback = object : PlaybackEngineCallback {
                    override fun onPrepared() {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        streamPrepared.set(true)
                        appendRuntimeLog("play prepared requestId=$requestId track=${track.title} source=download")
                        engine.play()
                        runOnUiThread {
                            updateState {
                                it.copy(
                                    isPlaying = true,
                                    playbackStatusRes = R.string.status_playing,
                                    playPauseLabelRes = R.string.action_pause,
                                    feedbackText = "Action feedback: playing ${track.title}"
                                )
                            }
                        }
                    }

                    override fun onCompletion() {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        runOnUiThread {
                            val nextTitle = moveToNextTrack()
                            if (nextTitle != null) {
                                updateState { s ->
                                    s.copy(
                                        currentTrack = nextTitle,
                                        nextEnabled = hasNextTrack()
                                    )
                                }
                                rebuildTrackLists()
                                playTrackAtCurrentIndex()
                            } else {
                                updateState { s ->
                                    s.copy(
                                        isPlaying = false,
                                        playbackStatusRes = R.string.status_paused,
                                        playPauseLabelRes = R.string.action_play,
                                        nextEnabled = false,
                                        feedbackText = getString(R.string.feedback_end_of_queue)
                                    )
                                }
                                rebuildTrackLists()
                            }
                        }
                    }

                    override fun onError(code: Int, detail: String) {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        appendRuntimeLog("play error requestId=$requestId code=$code detail=$detail source=download")
                        triggerCachedFallback("Action feedback: download play error ($code/$detail), trying cached file")
                    }

                    override fun onBufferingStart() {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        appendRuntimeLog("play buffering start requestId=$requestId track=${track.title}")
                        runOnUiThread {
                            updateState {
                                it.copy(
                                    isPlaying = true,
                                    playbackStatusRes = R.string.status_playing,
                                    playPauseLabelRes = R.string.action_pause,
                                    feedbackText = "Action feedback: buffering ${track.title}"
                                )
                            }
                        }
                    }

                    override fun onBufferingEnd() {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        appendRuntimeLog("play buffering end requestId=$requestId track=${track.title}")
                        runOnUiThread {
                            updateState {
                                it.copy(
                                    isPlaying = true,
                                    playbackStatusRes = R.string.status_playing,
                                    playPauseLabelRes = R.string.action_pause,
                                    feedbackText = "Action feedback: playing ${track.title}"
                                )
                            }
                        }
                    }
                }
            )
            appendRuntimeLog("play prepareAsync requestId=$requestId")
            updateState {
                it.copy(
                    isPlaying = false,
                    playbackStatusRes = R.string.status_paused,
                    playPauseLabelRes = R.string.action_play,
                    feedbackText = "Action feedback: preparing ${track.title}"
                )
            }
            Thread {
                try {
                    Thread.sleep(STREAM_PREPARE_TIMEOUT_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (requestId != playbackRequestId || streamPrepared.get()) {
                    return@Thread
                }
                runOnUiThread {
                    if (requestId != playbackRequestId || streamPrepared.get()) {
                        return@runOnUiThread
                    }
                    val active = playbackEngine
                    if (active != null && active.isPlaying()) {
                        streamPrepared.set(true)
                        appendRuntimeLog("play timeout guard ignored requestId=$requestId reason=already-playing")
                        return@runOnUiThread
                    }
                    appendRuntimeLog("play timeout guard confirmed requestId=$requestId")
                    triggerCachedFallback("Action feedback: download play timeout, trying cached file")
                }
            }.start()
        } catch (e: Exception) {
            appendRuntimeLog("play setup exception requestId=$requestId type=${e.javaClass.simpleName} msg=${e.message}")
            triggerCachedFallback(
                "Action feedback: download setup failed (${e.javaClass.simpleName}), trying cached file"
            )
        }
    }

    private fun downloadAndPlayTrack(
        track: EmbyTrack,
        embyBase: String,
        token: String,
        requestId: Int
    ) {
        appendRuntimeLog("cache fallback start requestId=$requestId track=${track.title}")
        runOnUiThread {
            if (requestId != playbackRequestId) {
                return@runOnUiThread
            }
            updateState {
                it.copy(
                    isPlaying = false,
                    playbackStatusRes = R.string.status_paused,
                    playPauseLabelRes = R.string.action_play,
                    feedbackText = "Action feedback: downloading cached ${track.title}"
                )
            }
        }
        Thread {
            val cacheFile = downloadTrackToCache(track, embyBase, token)
            runOnUiThread {
                if (requestId != playbackRequestId) {
                    return@runOnUiThread
                }
                if (cacheFile == null || !cacheFile.exists()) {
                    appendRuntimeLog("cache fallback failed requestId=$requestId track=${track.title}")
                    updateState {
                        it.copy(
                            isPlaying = false,
                            playbackStatusRes = R.string.status_paused,
                            playPauseLabelRes = R.string.action_play,
                            feedbackText = "Action feedback: download fallback failed"
                        )
                    }
                    return@runOnUiThread
                }

                try {
                    appendRuntimeLog("cache fallback file ready requestId=$requestId path=${cacheFile.absolutePath} size=${cacheFile.length()}")
                    releasePlayer()
                    val engine = ensurePlaybackEngine()
                    engine.prepare(
                        source = Uri.fromFile(cacheFile),
                        callback = object : PlaybackEngineCallback {
                            override fun onPrepared() {
                                if (requestId != playbackRequestId) {
                                    return
                                }
                                appendRuntimeLog("cache playback prepared requestId=$requestId")
                                engine.play()
                                runOnUiThread {
                                    updateState {
                                        it.copy(
                                            isPlaying = true,
                                            playbackStatusRes = R.string.status_playing,
                                            playPauseLabelRes = R.string.action_pause,
                                            feedbackText = "Action feedback: playing cached ${track.title}"
                                        )
                                    }
                                }
                            }

                            override fun onCompletion() {
                                if (requestId != playbackRequestId) {
                                    return
                                }
                                runOnUiThread {
                                    val nextTitle = moveToNextTrack()
                                    if (nextTitle != null) {
                                        updateState { s ->
                                            s.copy(
                                                currentTrack = nextTitle,
                                                nextEnabled = hasNextTrack()
                                            )
                                        }
                                        rebuildTrackLists()
                                        playTrackAtCurrentIndex()
                                    } else {
                                        updateState { s ->
                                            s.copy(
                                                isPlaying = false,
                                                playbackStatusRes = R.string.status_paused,
                                                playPauseLabelRes = R.string.action_play,
                                                nextEnabled = false,
                                                feedbackText = getString(R.string.feedback_end_of_queue)
                                            )
                                        }
                                        rebuildTrackLists()
                                    }
                                }
                            }

                            override fun onError(code: Int, detail: String) {
                                if (requestId != playbackRequestId) {
                                    return
                                }
                                appendRuntimeLog("cache playback error requestId=$requestId code=$code detail=$detail")
                                runOnUiThread {
                                    updateState {
                                        it.copy(
                                            isPlaying = false,
                                            playbackStatusRes = R.string.status_paused,
                                            playPauseLabelRes = R.string.action_play,
                                            feedbackText = "Action feedback: cached play failed ($code)"
                                        )
                                    }
                                }
                            }

                            override fun onBufferingStart() {
                                if (requestId != playbackRequestId) {
                                    return
                                }
                                appendRuntimeLog("cache buffering start requestId=$requestId")
                            }

                            override fun onBufferingEnd() {
                                if (requestId != playbackRequestId) {
                                    return
                                }
                                appendRuntimeLog("cache buffering end requestId=$requestId")
                            }
                        }
                    )
                    appendRuntimeLog("cache playback prepareAsync requestId=$requestId")
                    updateState {
                        it.copy(
                            isPlaying = false,
                            playbackStatusRes = R.string.status_paused,
                            playPauseLabelRes = R.string.action_play,
                            feedbackText = "Action feedback: buffering cached ${track.title}"
                        )
                    }
                } catch (e: Exception) {
                    appendRuntimeLog("cache playback exception requestId=$requestId type=${e.javaClass.simpleName} msg=${e.message}")
                    releasePlayer()
                    updateState {
                        it.copy(
                            isPlaying = false,
                            playbackStatusRes = R.string.status_paused,
                            playPauseLabelRes = R.string.action_play,
                            feedbackText = "Action feedback: cached play failed (${e.javaClass.simpleName})"
                        )
                    }
                }
            }
        }.start()
    }

    private fun downloadTrackToCache(
        track: EmbyTrack,
        embyBase: String,
        token: String
    ): File? {
        val candidateUrls = listOf(
            buildEmbyDownloadUrl(embyBase, track.id, token)
        )
        for (candidateUrl in candidateUrls) {
            var connection: HttpURLConnection? = null
            try {
                appendRuntimeLog("emby download try GET $candidateUrl")
                connection = (URL(candidateUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 20000
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "audio/*")
                    setRequestProperty("X-Emby-Token", token)
                }
                val code = connection.responseCode
                if (code !in 200..299) {
                    appendRuntimeLog("emby download non-2xx code=$code url=$candidateUrl")
                    Log.w(LOG_TAG, "downloadTrackToCache HTTP $code url=$candidateUrl")
                    continue
                }
                val file = File(cacheDir, "emby_${track.id}.cache")
                connection.inputStream.use { input ->
                    FileOutputStream(file, false).use { out ->
                        val buffer = ByteArray(8192)
                        var len = input.read(buffer)
                        while (len >= 0) {
                            if (len > 0) {
                                out.write(buffer, 0, len)
                            }
                            len = input.read(buffer)
                        }
                        out.flush()
                    }
                }
                if (file.length() <= 0L) {
                    appendRuntimeLog("emby download empty file url=$candidateUrl")
                    Log.e(LOG_TAG, "downloadTrackToCache empty file url=$candidateUrl")
                    file.delete()
                    continue
                }
                appendRuntimeLog("emby download success bytes=${file.length()} url=$candidateUrl")
                return file
            } catch (e: Exception) {
                appendRuntimeLog("emby download exception type=${e.javaClass.simpleName} msg=${e.message} url=$candidateUrl")
                Log.w(
                    LOG_TAG,
                    "downloadTrackToCache failed url=$candidateUrl: ${e.javaClass.simpleName} ${e.message}"
                )
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }

    private fun ensurePlaybackEngine(): PlaybackEngine {
        val existing = playbackEngine
        if (existing != null) {
            return existing
        }
        val created = ExoPlaybackEngine(this)
        playbackEngine = created
        return created
    }

    private fun releasePlayer() {
        playbackEngine?.release()
        playbackEngine = null
    }

    private fun fetchTracksFromEmby(credentials: EmbyCredentials): EmbyLoadResult {
        val logs = mutableListOf<String>()
        val logger: (String) -> Unit = { message ->
            logs.add(message)
            appendRuntimeLog("emby $message")
        }

        return try {
            val embyBase = normalizeEmbyBase(credentials.baseUrl)
            logger("base=$embyBase")
            logger("auth-mode=username-password")

            val auth = authenticateByName(
                embyBase = embyBase,
                username = credentials.username,
                password = credentials.password,
                log = logger
            ) ?: return failedResult(
                headline = "Action feedback: Emby authentication failed",
                logs = logs
            )

            logger("auth success user=${shortId(auth.userId)}")

            val recommendedEndpoint =
                buildEmbyRecommendedItemsUrl(embyBase, auth.userId, auth.accessToken)
            val recommendedResponse = executeGet(
                endpoint = recommendedEndpoint,
                token = auth.accessToken,
                requestLabel = "GET /Users/{id}/Items (Random/20)",
                log = logger
            )
            if (recommendedResponse.code !in 200..299) {
                return failedResult(
                    headline = "Action feedback: Emby random items request failed (HTTP ${recommendedResponse.code})",
                    logs = logs
                )
            }
            val source = "random-20"
            val tracks = parseTrackItems(recommendedResponse.payload)

            logger("tracks=${tracks.size}")
            if (tracks.isNotEmpty()) {
                logger("tracks-source=$source")
                logger("tracks-sample=${tracks.take(3).joinToString(" | ") { it.title }}")
            }
            if (tracks.isEmpty()) {
                return failedResult(
                    headline = "Action feedback: no audio items returned",
                    logs = logs
                )
            }

            EmbyLoadResult(
                success = true,
                statusText = getString(R.string.emby_status_connected) + " (${tracks.size}, $source)",
                feedbackText = formatFeedback(
                    headline = getString(R.string.feedback_emby_connected),
                    logs = logs
                ),
                tracks = tracks,
                embyBase = embyBase,
                embyUserId = auth.userId,
                accessToken = auth.accessToken
            )
        } catch (e: Exception) {
            logger("exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            failedResult(
                headline = getString(R.string.feedback_emby_failed),
                logs = logs
            )
        }
    }

    private fun authenticateByName(
        embyBase: String,
        username: String,
        password: String,
        log: (String) -> Unit
    ): AuthByNameResult? {
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = buildAuthenticateByNameUrl(embyBase)
            log("POST $endpoint")
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }

            val body = JSONObject()
                .put("Username", username)
                .put("Pw", password)
                .toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(body)
            }

            val code = connection.responseCode
            val payload = readAll(if (code in 200..299) connection.inputStream else connection.errorStream)
            log("POST $endpoint -> HTTP $code")
            log("auth body=${previewPayload(payload)}")

            if (code !in 200..299) {
                return null
            }

            val root = JSONObject(payload)
            val token = root.optString("AccessToken").trim()
            val userId = root.optJSONObject("User")?.optString("Id")?.trim().orEmpty()
            if (token.isEmpty() || userId.isEmpty()) {
                log("auth response missing token/user")
                return null
            }
            AuthByNameResult(accessToken = token, userId = userId)
        } catch (e: Exception) {
            log("auth exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeGet(
        endpoint: String,
        token: String,
        requestLabel: String,
        log: (String) -> Unit
    ): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            log("$requestLabel url=$endpoint")
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Emby-Token", token)
            }
            val code = connection.responseCode
            val payload = readAll(if (code in 200..299) connection.inputStream else connection.errorStream)
            log("$requestLabel -> HTTP $code, body=${previewPayload(payload)}")
            HttpResult(code = code, payload = payload)
        } catch (e: Exception) {
            log("$requestLabel exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            HttpResult(code = -1, payload = "")
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalizeEmbyBase(baseUrl: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        return if (normalized.endsWith("/emby", ignoreCase = true)) {
            normalized
        } else {
            "$normalized/emby"
        }
    }

    private fun buildAuthenticateByNameUrl(embyBase: String): String {
        return "$embyBase/Users/AuthenticateByName?${buildCommonEmbyQuery()}"
    }

    private fun buildEmbyRecommendedItemsUrl(
        embyBase: String,
        userId: String,
        token: String
    ): String {
        val params = mutableListOf(
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "SortBy=Random",
            "Limit=20",
            "api_key=${urlEncode(token)}"
        )
        return "$embyBase/Users/${urlEncode(userId)}/Items?${params.joinToString("&")}"
    }

    private fun buildEmbyDownloadUrl(
        embyBase: String,
        trackId: String,
        token: String
    ): String {
        val params = mutableListOf(
            "api_key=${urlEncode(token)}"
        )
        return "$embyBase/Items/${urlEncode(trackId)}/Download?${params.joinToString("&")}"
    }

    private fun buildCommonEmbyQuery(): String = commonEmbyQueryParams().joinToString("&")

    private fun commonEmbyQueryParams(): List<String> {
        return listOf(
            "X-Emby-Client=${urlEncode(EMBY_QUERY_CLIENT)}",
            "X-Emby-Device-Name=${urlEncode(EMBY_QUERY_DEVICE_NAME)}",
            "X-Emby-Device-Id=${urlEncode(EMBY_QUERY_DEVICE_ID)}",
            "X-Emby-Client-Version=${urlEncode(EMBY_QUERY_CLIENT_VERSION)}",
            "X-Emby-Language=${urlEncode(EMBY_QUERY_LANGUAGE)}"
        )
    }

    private fun parseTrackItems(jsonText: String): List<EmbyTrack> {
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
        if (trackId.isEmpty() || title.isEmpty()) {
            return null
        }
        return EmbyTrack(id = trackId, title = title)
    }

    private fun readAll(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                sb.append(line)
                line = reader.readLine()
            }
            return sb.toString()
        }
    }

    private fun failedResult(headline: String, logs: List<String>): EmbyLoadResult {
        return EmbyLoadResult(
            success = false,
            statusText = getString(R.string.emby_status_failed),
            feedbackText = formatFeedback(headline, logs)
        )
    }

    private fun formatFeedback(headline: String, logs: List<String>): String {
        val tail = logs.takeLast(8)
        val builder = StringBuilder(headline)
        for (line in tail) {
            builder.append('\n').append("- ").append(line)
        }
        return builder.toString()
    }

    private fun previewPayload(payload: String): String {
        if (payload.isBlank()) {
            return "<empty>"
        }
        val singleLine = payload.replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= 160) {
            singleLine
        } else {
            singleLine.substring(0, 160) + "..."
        }
    }

    private fun shortId(value: String): String {
        return if (value.length <= 8) value else value.take(4) + "..." + value.takeLast(4)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun isHttpUrl(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun loadSavedCredentials() {
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        embyBaseUrlInput.setText(prefs.getString(KEY_BASE_URL, "").orEmpty())
        embyUsernameInput.setText(prefs.getString(KEY_USERNAME, "").orEmpty())
        embyPasswordInput.setText(prefs.getString(KEY_PASSWORD, "").orEmpty())
        lrcApiBaseUrlInput.setText(prefs.getString(KEY_LRCAPI_BASE_URL, "").orEmpty())
    }

    private fun persistCredentials(credentials: EmbyCredentials) {
        getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, credentials.baseUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .apply()
    }

    private fun persistLrcApiCredentials(credentials: LrcApiCredentials) {
        getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .edit()
            .putString(KEY_LRCAPI_BASE_URL, credentials.baseUrl)
            .apply()
    }

    private fun appendRuntimeLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp $message"
        val snapshot: String
        synchronized(runtimeLogLock) {
            runtimeLogLines.add(line)
            if (runtimeLogLines.size > MAX_RUNTIME_LOG_LINES) {
                runtimeLogLines.removeAt(0)
            }
            snapshot = runtimeLogLines.joinToString("\n")
        }
        Log.d(LOG_TAG, line)
        if (!this::runtimeLogPreview.isInitialized) {
            return
        }
        runOnUiThread {
            renderRuntimeLogSnapshot(snapshot)
        }
    }

    private fun snapshotRuntimeLogs(): String {
        synchronized(runtimeLogLock) {
            if (runtimeLogLines.isEmpty()) {
                return getString(R.string.runtime_logs_empty)
            }
            return runtimeLogLines.joinToString("\n")
        }
    }

    private fun renderRuntimeLogSnapshot(snapshot: String) {
        val preview = if (snapshot.isBlank()) {
            getString(R.string.runtime_logs_empty)
        } else {
            snapshot
                .split('\n')
                .takeLast(RUNTIME_LOG_PREVIEW_LINES)
                .joinToString("\n")
        }
        runtimeLogPreview.text = preview
        runtimeLogDialogText?.text = if (snapshot.isBlank()) {
            getString(R.string.runtime_logs_empty)
        } else {
            snapshot
        }
    }

    private fun copyRuntimeLogsToClipboard() {
        val snapshot = snapshotRuntimeLogs()
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Skoda Runtime Logs", snapshot))
        appendRuntimeLog("runtime logs copied to clipboard")
        showToast(R.string.toast_runtime_logs_copied)
    }

    private fun clearRuntimeLogs() {
        synchronized(runtimeLogLock) {
            runtimeLogLines.clear()
        }
        renderRuntimeLogSnapshot("")
        showToast(R.string.toast_runtime_logs_cleared)
    }

    private fun showRuntimeLogsFullscreen() {
        if (runtimeLogDialog?.isShowing == true) {
            return
        }
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_runtime_logs)
        val fullText = dialog.findViewById<TextView>(R.id.runtime_log_fullscreen_text)
        dialog.findViewById<Button>(R.id.btn_copy_runtime_logs).setOnClickListener {
            copyRuntimeLogsToClipboard()
        }
        dialog.findViewById<Button>(R.id.btn_clear_runtime_logs).setOnClickListener {
            clearRuntimeLogs()
        }
        dialog.findViewById<Button>(R.id.btn_close_runtime_logs).setOnClickListener {
            dialog.dismiss()
        }
        fullText.text = snapshotRuntimeLogs()
        runtimeLogDialog = dialog
        runtimeLogDialogText = fullText
        dialog.setOnDismissListener {
            runtimeLogDialog = null
            runtimeLogDialogText = null
        }
        dialog.show()
    }

    private fun updateState(reducer: (UiState) -> UiState) {
        uiState = reducer(uiState)
        render(uiState)
    }

    private fun render(state: UiState) {
        embyStatusValue.text = state.embyStatusText
        lrcApiStatusValue.text = state.lrcApiStatusText
        trackValue.text = state.currentTrack
        playbackValue.setText(state.playbackStatusRes)
        actionFeedback.text = state.feedbackText
        playPauseButton.setText(state.playPauseLabelRes)
        playPauseButton.isEnabled = state.playPauseEnabled
        nextButton.isEnabled = state.nextEnabled
        testEmbyButton.isEnabled = state.testEmbyEnabled
        testLrcApiButton.isEnabled = state.testLrcApiEnabled
    }

    private companion object {
        const val LOG_TAG = "SkodaMusicEmby"
        const val PREFS_EMBY = "emby_credentials"
        const val KEY_BASE_URL = "base_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_LRCAPI_BASE_URL = "lrcapi_base_url"
        const val EMBY_QUERY_CLIENT = "Emby Web"
        const val EMBY_QUERY_DEVICE_NAME = "Google Chrome Windows"
        const val EMBY_QUERY_DEVICE_ID = "6ec2a066-66a2-49af-bd97-6302ee307eaf"
        const val EMBY_QUERY_CLIENT_VERSION = "4.9.1.90"
        const val EMBY_QUERY_LANGUAGE = "zh-cn"
        // Start playback once playable duration is >=3s and rebuffer with >=1s.
        const val LOAD_CONTROL_MIN_BUFFER_MS = 8_000
        const val LOAD_CONTROL_MAX_BUFFER_MS = 50_000
        const val LOAD_CONTROL_PLAYBACK_MS = 3_000
        const val LOAD_CONTROL_REBUFFER_MS = 1_000
        const val STREAM_PREPARE_TIMEOUT_MS = 45_000L
        const val MAX_RUNTIME_LOG_LINES = 800
        const val RUNTIME_LOG_PREVIEW_LINES = 2
        const val AUTO_QUEUE_REFRESH_COOLDOWN_MS = 15_000L
        const val PAGE_HOME = 0
        const val PAGE_QUEUE = 1
        const val PAGE_LIBRARY = 2
        const val PAGE_SETTINGS = 3
    }
}
