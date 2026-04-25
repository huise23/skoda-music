package com.skodamusic.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.net.Uri
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import okhttp3.Dns
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLEncoder
import java.net.URL
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private data class LyricLine(
        val timeMs: Long,
        val text: String
    )

    private enum class DownloadControlPhase {
        MAINTAIN_CURRENT_WINDOW,
        FINISH_CURRENT_TRACK,
        PREFETCH_NEXT_WINDOW,
        IDLE
    }

    private enum class PlaybackFailureCategory {
        DECODER_FAILURE,
        SOURCE_FAILURE,
        NETWORK_FAILURE,
        UNKNOWN
    }

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
        fun seekTo(positionMs: Long): Boolean
        fun isPlaying(): Boolean
        fun currentPositionMs(): Long
        fun durationMs(): Long
        fun release()
    }

    private class ExoPlaybackEngine(
        private val context: Context,
        private val dataSourceFactory: DataSource.Factory
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
                .build()
            val player = SimpleExoPlayer.Builder(context)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
            exoPlayer = player

            var preparedNotified = false
            var buffering = false

            player.setAudioAttributes(
                ExoAudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
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

        override fun seekTo(positionMs: Long): Boolean {
            val player = exoPlayer ?: return false
            return try {
                player.seekTo(positionMs.coerceAtLeast(0L))
                true
            } catch (_: Exception) {
                false
            }
        }

        override fun isPlaying(): Boolean {
            return try {
                exoPlayer?.isPlaying == true
            } catch (_: Exception) {
                false
            }
        }

        override fun currentPositionMs(): Long {
            return try {
                exoPlayer?.currentPosition ?: -1L
            } catch (_: Exception) {
                -1L
            }
        }

        override fun durationMs(): Long {
            return try {
                val value = exoPlayer?.duration ?: -1L
                if (value <= 0L) -1L else value
            } catch (_: Exception) {
                -1L
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
        val password: String,
        val cfReferenceDomain: String
    )

    private data class LrcApiCredentials(
        val baseUrl: String
    )

    private data class EmbyTrack(
        val id: String,
        val title: String,
        val artist: String = "",
        val runtimeTicks: Long = -1L
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

    private data class TrackDownloadState(
        val trackId: String,
        var totalBytes: Long = -1L,
        var downloadedBytes: Long = 0L,
        var completed: Boolean = false,
        var bitrateBps: Long = DEFAULT_ESTIMATED_BITRATE_BPS
    )

    private lateinit var embyBaseUrlInput: EditText
    private lateinit var cfRefDomainInput: EditText
    private lateinit var embyUsernameInput: EditText
    private lateinit var embyPasswordInput: EditText
    private lateinit var lrcApiBaseUrlInput: EditText
    private lateinit var embyStatusValue: TextView
    private lateinit var lrcApiStatusValue: TextView
    private lateinit var downloadCacheSizeValue: TextView
    private lateinit var clearDownloadCacheButton: Button
    private lateinit var buildIdBadge: TextView
    private lateinit var trackValue: TextView
    private lateinit var trackArtistValue: TextView
    private lateinit var playbackValue: TextView
    private lateinit var playbackProgressValue: TextView
    private lateinit var playbackDurationValue: TextView
    private lateinit var playbackSeekBar: SeekBar
    private lateinit var downloadProgressValue: TextView
    private lateinit var runtimeLogPreview: TextView
    private lateinit var queueTracksContainer: LinearLayout
    private lateinit var libraryTracksContainer: LinearLayout
    private lateinit var homeRecommendRefresh: SwipeRefreshLayout
    private lateinit var prevButton: ImageButton
    private lateinit var playPauseButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var homeTabRecommendButton: Button
    private lateinit var homeTabLyricsButton: Button
    private lateinit var homeRecommendPanel: View
    private lateinit var homeLyricsPanel: View
    private lateinit var homeRecommendList: LinearLayout
    private lateinit var homeLyricsScroll: ScrollView
    private lateinit var homeLyricsText: TextView
    private lateinit var testEmbyButton: Button
    private lateinit var testLrcApiButton: Button
    private lateinit var navHomeButton: ImageButton
    private lateinit var navQueueButton: ImageButton
    private lateinit var navLibraryButton: ImageButton
    private lateinit var navSettingsButton: ImageButton
    private lateinit var pageHome: View
    private lateinit var pageQueue: View
    private lateinit var pageLibrary: ScrollView
    private lateinit var pageSettings: View
    private lateinit var uiState: UiState

    private var loadedTracks: List<EmbyTrack> = emptyList()
    private val libraryTracks = mutableListOf<EmbyTrack>()
    private var libraryLoadInFlight: Boolean = false
    private var libraryNextStartIndex: Int = 0
    private var libraryTotalRecordCount: Int = Int.MAX_VALUE
    private var libraryPagingBound: Boolean = false
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
    private var queueTailRefillInFlight: Boolean = false
    private var showingHomeRecommendTab: Boolean = true
    private var previewArtistOverride: String? = null
    private var homeLyricsLines: List<LyricLine> = emptyList()
    private var homeLyricsTrackKey: String = ""
    private var homeLyricsRequestTrackKey: String? = null
    private val homeLyricsCache = LinkedHashMap<String, List<LyricLine>>()
    private var isUserSeeking: Boolean = false
    private var pendingSeekPositionMs: Long = -1L
    private val playbackErrorHandleLock = Any()
    private var playbackErrorHandledRequestId: Int = -1
    private val dnsCacheLock = Any()
    private val cfPreferredIpv4Cache = LinkedHashMap<String, Pair<Long, List<InetAddress>>>()
    private val downloadStateLock = Any()
    private val trackDownloadStates = LinkedHashMap<String, TrackDownloadState>()
    private val uiProgressHandler = Handler(Looper.getMainLooper())
    private val uiProgressTicker = object : Runnable {
        override fun run() {
            refreshProgressMetrics()
            uiProgressHandler.postDelayed(this, UI_PROGRESS_REFRESH_MS)
        }
    }
    @Volatile private var lastKnownBitrateBps: Long = DEFAULT_ESTIMATED_BITRATE_BPS
    @Volatile private var downloadControllerRequestId: Int = -1
    @Volatile private var downloadControllerStop = false
    private var downloadControllerThread: Thread? = null
    private val jsonMediaType: MediaType = MediaType.parse("application/json; charset=utf-8")
        ?: throw IllegalStateException("json media type parse failed")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        embyBaseUrlInput = findViewById(R.id.emby_base_url_input)
        cfRefDomainInput = findViewById(R.id.cf_ref_domain_input)
        embyUsernameInput = findViewById(R.id.emby_username_input)
        embyPasswordInput = findViewById(R.id.emby_password_input)
        lrcApiBaseUrlInput = findViewById(R.id.lrcapi_base_url_input)
        embyStatusValue = findViewById(R.id.emby_status_value)
        lrcApiStatusValue = findViewById(R.id.lrcapi_status_value)
        downloadCacheSizeValue = findViewById(R.id.download_cache_size_value)
        clearDownloadCacheButton = findViewById(R.id.btn_clear_download_cache)
        buildIdBadge = findViewById(R.id.build_id_badge)
        trackValue = findViewById(R.id.track_value)
        trackArtistValue = findViewById(R.id.track_artist_value)
        playbackValue = findViewById(R.id.playback_value)
        playbackProgressValue = findViewById(R.id.playback_progress_value)
        playbackDurationValue = findViewById(R.id.playback_duration_value)
        playbackSeekBar = findViewById(R.id.playback_seek_bar)
        downloadProgressValue = findViewById(R.id.download_progress_value)
        runtimeLogPreview = findViewById(R.id.runtime_log_preview)
        homeRecommendRefresh = findViewById(R.id.home_recommend_refresh)
        queueTracksContainer = findViewById(R.id.queue_tracks_container)
        libraryTracksContainer = findViewById(R.id.library_tracks_container)
        prevButton = findViewById(R.id.btn_prev)
        playPauseButton = findViewById(R.id.btn_play_pause)
        nextButton = findViewById(R.id.btn_next)
        homeTabRecommendButton = findViewById(R.id.btn_home_tab_recommend)
        homeTabLyricsButton = findViewById(R.id.btn_home_tab_lyrics)
        homeRecommendPanel = findViewById(R.id.home_recommend_panel)
        homeLyricsPanel = findViewById(R.id.home_lyrics_panel)
        homeRecommendList = findViewById(R.id.home_recommend_list)
        homeLyricsScroll = findViewById(R.id.home_lyrics_scroll)
        homeLyricsText = findViewById(R.id.home_lyrics_text)
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
        buildIdBadge.text = "构建: ${BuildConfig.GIT_SHORT_SHA}"
        configureModernHomeShell()
        switchHomeTab(showRecommend = false)
        bindLibraryPaging()
        bindNavigation()
        switchPage(PAGE_HOME)
        loadSavedCredentials()
        maybeClearDownloadCacheOnColdStart()

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
        refreshDownloadCacheInfoUi()
        startUiProgressTicker()
        appendRuntimeLog("app boot completed")
        uiProgressHandler.postDelayed(
            { maybeAutoRefreshQueueRecommendations("app-startup") },
            APP_STARTUP_QUEUE_REFRESH_DELAY_MS
        )
    }

    override fun onDestroy() {
        appendRuntimeLog("activity destroyed")
        stopUiProgressTicker()
        runtimeLogDialog?.dismiss()
        super.onDestroy()
        playbackRequestId += 1
        stopDownloadController()
        releasePlayer()
    }

    private fun configureModernHomeShell() {
        trackValue.isSelected = true
        trackValue.textSize = 36f
        trackValue.setHorizontallyScrolling(true)
        trackValue.setSingleLine(true)
        trackValue.ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
        trackValue.marqueeRepeatLimit = -1

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            playbackSeekBar.splitTrack = false
        }

        navHomeButton.setImageResource(android.R.drawable.ic_menu_view)
        navQueueButton.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        navLibraryButton.setImageResource(android.R.drawable.ic_menu_agenda)
        navSettingsButton.setImageResource(android.R.drawable.ic_menu_manage)
        navHomeButton.contentDescription = getString(R.string.nav_home)
        navQueueButton.contentDescription = getString(R.string.nav_queue)
        navLibraryButton.contentDescription = getString(R.string.nav_library)
        navSettingsButton.contentDescription = getString(R.string.nav_settings)

        prevButton.setImageResource(android.R.drawable.ic_media_previous)
        prevButton.contentDescription = getString(R.string.action_prev)
        playPauseButton.setImageResource(android.R.drawable.ic_media_play)
        playPauseButton.contentDescription = getString(R.string.action_play)
        nextButton.setImageResource(android.R.drawable.ic_media_next)
        nextButton.contentDescription = getString(R.string.action_next)
        prevButton.setColorFilter(resources.getColor(R.color.white))
        playPauseButton.setColorFilter(resources.getColor(R.color.white))
        nextButton.setColorFilter(resources.getColor(R.color.white))

        playbackProgressValue.text = formatDurationClock(-1L)
        playbackDurationValue.text = formatDurationClock(-1L)
    }

    private fun bindActions() {
        runtimeLogPreview.setOnClickListener {
            showRuntimeLogsFullscreen()
        }
        findViewById<TextView>(R.id.runtime_log_label).setOnClickListener {
            showRuntimeLogsFullscreen()
        }
        homeRecommendRefresh.setOnRefreshListener {
            val credentials = EmbyCredentials(
                baseUrl = embyBaseUrlInput.text.toString().trim(),
                username = embyUsernameInput.text.toString().trim(),
                password = embyPasswordInput.text.toString().trim(),
                cfReferenceDomain = resolveCfReferenceDomain()
            )
            if (credentials.baseUrl.isEmpty() || credentials.username.isEmpty() || credentials.password.isEmpty()) {
                homeRecommendRefresh.isRefreshing = false
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
                showToast(R.string.toast_emby_failed)
                return@setOnRefreshListener
            }
            requestTracksFromEmby(
                credentials = credentials,
                onFinished = {
                    runOnUiThread {
                        homeRecommendRefresh.isRefreshing = false
                    }
                },
                forceRefreshRecommendations = true
            )
        }
        homeTabRecommendButton.setOnClickListener {
            switchHomeTab(showRecommend = true)
        }
        homeTabLyricsButton.setOnClickListener {
            switchHomeTab(showRecommend = false)
        }
        playbackSeekBar.max = SEEK_BAR_MAX
        playbackSeekBar.progress = 0
        playbackSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) {
                    return
                }
                val track = loadedTracks.getOrNull(currentTrackIndex) ?: return
                val durationMs = resolveTrackDurationMs(track, playbackEngine?.durationMs() ?: -1L)
                if (durationMs <= 0L) {
                    return
                }
                pendingSeekPositionMs = ((progress.toLong() * durationMs) / SEEK_BAR_MAX.toLong()).coerceIn(0L, durationMs)
                playbackProgressValue.text = formatDurationClock(pendingSeekPositionMs.coerceAtLeast(0L))
                playbackDurationValue.text = formatDurationClock(durationMs)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val targetMs = pendingSeekPositionMs
                pendingSeekPositionMs = -1L
                isUserSeeking = false
                applySeekTarget(targetMs)
            }
        })

        prevButton.setOnClickListener {
            appendRuntimeLog("ui click prev")
            if (loadedTracks.isEmpty()) {
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
                return@setOnClickListener
            }
            if (currentTrackIndex <= 0) {
                currentTrackIndex = 0
                syncNativeQueueToCurrentIndex()
                updateState {
                    it.copy(
                        currentTrack = loadedTracks.first().title,
                        feedbackText = getString(R.string.feedback_prev_to_start),
                        nextEnabled = hasNextTrack()
                    )
                }
                rebuildTrackLists()
                playTrackAtCurrentIndex()
                return@setOnClickListener
            }
            currentTrackIndex = (currentTrackIndex - 1).coerceAtLeast(0)
            syncNativeQueueToCurrentIndex()
            updateState {
                it.copy(
                    currentTrack = loadedTracks[currentTrackIndex].title,
                    feedbackText = getString(R.string.feedback_prev_pressed),
                    nextEnabled = hasNextTrack(),
                    isPlaying = true,
                    playbackStatusRes = R.string.status_playing,
                    playPauseLabelRes = R.string.action_pause
                )
            }
            rebuildTrackLists()
            playTrackAtCurrentIndex()
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
                    nextEnabled = hasNextTrack(),
                    isPlaying = true,
                    playbackStatusRes = R.string.status_playing,
                    playPauseLabelRes = R.string.action_pause
                )
            }
            showToast(R.string.toast_next)
            rebuildTrackLists()
            appendRuntimeLog("ui click next -> play immediately index=$currentTrackIndex")
            playTrackAtCurrentIndex()
        }

        testEmbyButton.setOnClickListener {
            val credentials = EmbyCredentials(
                baseUrl = embyBaseUrlInput.text.toString().trim(),
                username = embyUsernameInput.text.toString().trim(),
                password = embyPasswordInput.text.toString().trim(),
                cfReferenceDomain = resolveCfReferenceDomain()
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

            requestTracksFromEmby(credentials, forceRefreshRecommendations = true)
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

        clearDownloadCacheButton.setOnClickListener {
            val cleared = clearDownloadCacheFiles("manual-clear")
            refreshDownloadCacheInfoUi()
            showToast(
                if (cleared) R.string.toast_download_cache_cleared
                else R.string.toast_download_cache_clear_failed
            )
        }
    }

    private fun bindNavigation() {
        navHomeButton.setOnClickListener {
            switchPage(PAGE_HOME)
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
        pageQueue.visibility = View.GONE
        pageLibrary.visibility = if (selectedPage == PAGE_LIBRARY) View.VISIBLE else View.GONE
        pageSettings.visibility = if (selectedPage == PAGE_SETTINGS) View.VISIBLE else View.GONE
        updateNavigationVisualState()
        if (selectedPage == PAGE_LIBRARY) {
            ensureLibraryTracksLoaded("enter-library-page")
        } else if (selectedPage == PAGE_SETTINGS) {
            refreshDownloadCacheInfoUi()
        }
    }

    private fun updateNavigationVisualState() {
        setNavButtonSelected(navHomeButton, selectedPage == PAGE_HOME, android.R.drawable.ic_menu_view)
        setNavButtonSelected(navLibraryButton, selectedPage == PAGE_LIBRARY, android.R.drawable.ic_menu_agenda)
        setNavButtonSelected(navSettingsButton, selectedPage == PAGE_SETTINGS, android.R.drawable.ic_menu_manage)
    }

    private fun setNavButtonSelected(button: ImageButton, selected: Boolean, iconRes: Int) {
        button.setImageResource(iconRes)
        button.setBackgroundResource(
            if (selected) R.drawable.button_nav_icon_active else R.drawable.button_nav_icon_inactive
        )
        button.setColorFilter(resources.getColor(if (selected) R.color.white else R.color.text_secondary))
    }

    private fun rebuildTrackLists() {
        renderTrackContainer(
            container = queueTracksContainer,
            tracks = loadedTracks,
            highlightCurrent = true,
            emptyRes = R.string.queue_empty,
            source = ListSource.QUEUE
        )
        renderTrackContainer(
            container = libraryTracksContainer,
            tracks = libraryTracks,
            highlightCurrent = false,
            emptyRes = R.string.library_empty,
            source = ListSource.LIBRARY
        )
        renderHomeRecommendationPreview()
    }

    private fun switchHomeTab(showRecommend: Boolean) {
        showingHomeRecommendTab = showRecommend
        homeRecommendPanel.visibility = if (showRecommend) View.VISIBLE else View.GONE
        homeLyricsPanel.visibility = if (showRecommend) View.GONE else View.VISIBLE
        homeTabRecommendButton.setBackgroundResource(
            if (showRecommend) R.drawable.tab_home_selected else R.drawable.tab_home_unselected
        )
        homeTabLyricsButton.setBackgroundResource(
            if (showRecommend) R.drawable.tab_home_unselected else R.drawable.tab_home_selected
        )
        homeTabRecommendButton.setTextColor(
            resources.getColor(if (showRecommend) R.color.white else R.color.text_secondary)
        )
        homeTabLyricsButton.setTextColor(
            resources.getColor(if (showRecommend) R.color.text_secondary else R.color.white)
        )
        homeTabRecommendButton.setTypeface(
            null,
            if (showRecommend) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
        homeTabLyricsButton.setTypeface(
            null,
            if (showRecommend) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD
        )
        if (!showRecommend) {
            val positionMs = playbackEngine?.currentPositionMs()?.coerceAtLeast(0L) ?: 0L
            renderHomeLyricsByPosition(positionMs)
        }
    }

    private fun bindLibraryPaging() {
        if (libraryPagingBound) {
            return
        }
        libraryPagingBound = true
        pageLibrary.viewTreeObserver.addOnScrollChangedListener {
            if (selectedPage != PAGE_LIBRARY) {
                return@addOnScrollChangedListener
            }
            if (isLibraryNearBottom()) {
                loadMoreLibraryTracks("library-scroll")
            }
        }
    }

    private fun isLibraryNearBottom(): Boolean {
        val child = pageLibrary.getChildAt(0) ?: return false
        val distanceToBottom = child.bottom - (pageLibrary.height + pageLibrary.scrollY)
        return distanceToBottom <= dpToPx(220)
    }

    private fun ensureLibraryTracksLoaded(trigger: String) {
        if (libraryTracks.isEmpty()) {
            resetAndLoadLibraryTracks(trigger)
            return
        }
        if (isLibraryNearBottom()) {
            loadMoreLibraryTracks("$trigger-near-bottom")
        }
    }

    private fun resetAndLoadLibraryTracks(trigger: String) {
        libraryTracks.clear()
        libraryNextStartIndex = 0
        libraryTotalRecordCount = Int.MAX_VALUE
        rebuildTrackLists()
        loadMoreLibraryTracks("$trigger-reset")
    }

    private fun loadMoreLibraryTracks(trigger: String) {
        if (libraryLoadInFlight) {
            return
        }
        if (libraryNextStartIndex >= libraryTotalRecordCount) {
            return
        }
        val base = embySessionBaseUrl
        val userId = embySessionUserId
        val token = embyAccessToken
        if (base.isNullOrBlank() || userId.isNullOrBlank() || token.isNullOrBlank()) {
            val credentials = EmbyCredentials(
                baseUrl = embyBaseUrlInput.text.toString().trim(),
                username = embyUsernameInput.text.toString().trim(),
                password = embyPasswordInput.text.toString().trim(),
                cfReferenceDomain = resolveCfReferenceDomain()
            )
            if (credentials.baseUrl.isEmpty() || credentials.username.isEmpty() || credentials.password.isEmpty()) {
                return
            }
            requestTracksFromEmby(
                credentials = credentials,
                onFinished = {
                    runOnUiThread {
                        if (!embySessionBaseUrl.isNullOrBlank() && !embySessionUserId.isNullOrBlank() && !embyAccessToken.isNullOrBlank()) {
                            loadMoreLibraryTracks("$trigger-auth-ready")
                        }
                    }
                }
            )
            return
        }
        val start = libraryNextStartIndex
        val limit = LIBRARY_PAGE_SIZE
        libraryLoadInFlight = true
        appendRuntimeLog("library load start trigger=$trigger start=$start limit=$limit")
        val embyClient = buildEmbyHttpClient(base, resolveCfReferenceDomain())
        Thread {
            val endpoint = buildEmbyLibraryItemsUrl(base, userId, token, start, limit)
            val response = executeGet(
                endpoint = endpoint,
                token = token,
                requestLabel = "GET /Users/{id}/Items (Library Paging)",
                log = { message -> appendRuntimeLog("library $message") },
                httpClient = embyClient
            )
            val pageTracks = if (response.code in 200..299) parseTrackItems(response.payload) else emptyList()
            val totalCount = parseTotalRecordCount(response.payload, start + pageTracks.size)
            runOnUiThread {
                libraryLoadInFlight = false
                if (response.code !in 200..299) {
                    appendRuntimeLog("library load failed code=${response.code} trigger=$trigger")
                    return@runOnUiThread
                }
                val existingIds = HashSet<String>(libraryTracks.size * 2 + pageTracks.size)
                libraryTracks.forEach { existingIds.add(it.id) }
                pageTracks.forEach { track ->
                    if (!existingIds.contains(track.id)) {
                        libraryTracks.add(track)
                        existingIds.add(track.id)
                    }
                }
                libraryTracks.sortWith(trackTitleComparator())
                libraryNextStartIndex = (start + pageTracks.size).coerceAtLeast(libraryTracks.size)
                libraryTotalRecordCount = totalCount.coerceAtLeast(libraryTracks.size)
                appendRuntimeLog(
                    "library load success trigger=$trigger fetched=${pageTracks.size} list=${libraryTracks.size} next=$libraryNextStartIndex total=$libraryTotalRecordCount"
                )
                rebuildTrackLists()
                if (selectedPage == PAGE_LIBRARY && isLibraryNearBottom()) {
                    loadMoreLibraryTracks("$trigger-fill-viewport")
                }
            }
        }.start()
    }

    private fun renderHomeRecommendationPreview() {
        homeRecommendList.removeAllViews()
        if (loadedTracks.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.home_recommend_placeholder)
            empty.setTextColor(resources.getColor(R.color.text_secondary))
            empty.textSize = 16f
            empty.gravity = Gravity.CENTER
            empty.setPadding(dpToPx(12), dpToPx(22), dpToPx(12), dpToPx(22))
            empty.setBackgroundResource(R.drawable.row_recommend_idle)
            homeRecommendList.addView(
                empty,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }
        val fallbackServerArtist = getString(R.string.track_artist_server)
        val recommendations: List<Pair<String, String>> =
            loadedTracks.take(DEFAULT_HOME_QUEUE_SIZE).map { it.title to it.artist.ifBlank { fallbackServerArtist } }

        recommendations.forEachIndexed { index, item ->
            val isCurrent = index == currentTrackIndex
            val row = LinearLayout(this)
            row.orientation = LinearLayout.VERTICAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = dpToPx(70)
            row.setBackgroundResource(
                if (isCurrent) R.drawable.row_recommend_active else R.drawable.row_recommend_idle
            )
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))

            val title = TextView(this)
            title.text = item.first
            title.setTextColor(resources.getColor(R.color.text_primary))
            title.textSize = 17f
            title.setTypeface(null, if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            val artist = TextView(this)
            artist.text = item.second
            artist.setTextColor(resources.getColor(R.color.text_secondary))
            artist.textSize = 14f

            row.addView(title)
            row.addView(artist)
            row.setOnClickListener {
                playFromList(index, ListSource.QUEUE)
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dpToPx(8)
            }
            homeRecommendList.addView(row, params)
        }
    }

    private fun renderHomeLyricsPreview(currentTrack: String) {
        val displayTrack = if (currentTrack.isBlank()) getString(R.string.track_not_loaded) else currentTrack
        val displayArtist = loadedTracks.getOrNull(currentTrackIndex)?.artist?.takeIf { it.isNotBlank() }
            ?: previewArtistOverride
            ?: ""
        val trackKey = displayTrack.trim() + "\u0001" + displayArtist.trim()
        if (trackKey != homeLyricsTrackKey) {
            homeLyricsTrackKey = trackKey
            val cached = homeLyricsCache[trackKey]
            if (cached != null && cached.isNotEmpty()) {
                homeLyricsLines = cached
            } else {
                homeLyricsLines = buildHomeLyricsLines(displayTrack)
                requestLyricsFromLrcApi(trackName = displayTrack, artistName = displayArtist, trackKey = trackKey)
            }
        } else if (homeLyricsLines.isEmpty()) {
            homeLyricsLines = homeLyricsCache[trackKey] ?: buildHomeLyricsLines(displayTrack)
        }
        val positionMs = playbackEngine?.currentPositionMs()?.coerceAtLeast(0L) ?: 0L
        renderHomeLyricsByPosition(positionMs)
    }

    private fun buildHomeLyricsLines(trackName: String): List<LyricLine> {
        val fallbackText = getString(R.string.home_lyrics_placeholder)
        return listOf(LyricLine(0L, fallbackText))
    }

    private fun requestLyricsFromLrcApi(trackName: String, artistName: String, trackKey: String) {
        val cleanTrack = trackName.trim()
        if (cleanTrack.isEmpty() || cleanTrack == getString(R.string.track_not_loaded)) {
            return
        }
        val baseUrl = resolveLrcApiBaseUrl()
        if (baseUrl.isEmpty() || !isHttpUrl(baseUrl)) {
            appendRuntimeLog("lyrics fetch skip reason=invalid-lrcapi-base track=$cleanTrack")
            return
        }
        if (homeLyricsRequestTrackKey == trackKey) {
            return
        }
        homeLyricsRequestTrackKey = trackKey
        Thread {
            val fetchedLines = fetchLyricsLinesFromLrcApi(baseUrl, cleanTrack, artistName.trim())
            runOnUiThread {
                if (homeLyricsRequestTrackKey == trackKey) {
                    homeLyricsRequestTrackKey = null
                }
                if (homeLyricsTrackKey != trackKey) {
                    return@runOnUiThread
                }
                if (fetchedLines.isEmpty()) {
                    appendRuntimeLog("lyrics fetch empty track=$cleanTrack artist=$artistName")
                    return@runOnUiThread
                }
                homeLyricsCache[trackKey] = fetchedLines
                if (homeLyricsCache.size > LYRICS_CACHE_MAX_TRACKS) {
                    val iterator = homeLyricsCache.entries.iterator()
                    if (iterator.hasNext()) {
                        iterator.next()
                        iterator.remove()
                    }
                }
                homeLyricsLines = fetchedLines
                val positionMs = playbackEngine?.currentPositionMs()?.coerceAtLeast(0L) ?: 0L
                renderHomeLyricsByPosition(positionMs)
                appendRuntimeLog("lyrics fetch success track=$cleanTrack lines=${fetchedLines.size}")
            }
        }.start()
    }

    private fun resolveLrcApiBaseUrl(): String {
        val input = lrcApiBaseUrlInput.text?.toString()?.trim().orEmpty()
        if (input.isNotEmpty()) {
            return input
        }
        return getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .getString(KEY_LRCAPI_BASE_URL, "")
            .orEmpty()
            .trim()
    }

    private fun fetchLyricsLinesFromLrcApi(baseUrl: String, trackName: String, artistName: String): List<LyricLine> {
        val endpoint = buildLrcApiLyricsUrl(baseUrl, trackName, artistName)
        val lrcApiClient = buildEmbyHttpClient(baseUrl, resolveCfReferenceDomain())
        return try {
            appendRuntimeLog("lyrics fetch GET $endpoint")
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "text/plain, application/json")
                .build()
            lrcApiClient.newCall(request).execute().use { response ->
                val code = response.code()
                val payload = response.body()?.string().orEmpty()
                appendRuntimeLog("lyrics fetch response code=$code body=${previewPayload(payload)}")
                if (code !in 200..299 || payload.isBlank()) {
                    emptyList()
                } else {
                    val lyricPayload = extractLyricPayload(payload)
                    parseLyricLines(lyricPayload)
                }
            }
        } catch (e: Exception) {
            appendRuntimeLog("lyrics fetch exception type=${e.javaClass.simpleName} msg=${e.message}")
            emptyList()
        }
    }

    private fun buildLrcApiLyricsUrl(baseUrl: String, trackName: String, artistName: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val endpoint = if (normalized.endsWith("/lyrics", ignoreCase = true)) {
            normalized
        } else {
            "$normalized/lyrics"
        }
        return "$endpoint?title=${urlEncode(trackName)}&artist=${urlEncode(artistName)}"
    }

    private fun extractLyricPayload(payload: String): String {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        if (!trimmed.startsWith("{")) {
            return trimmed
        }
        return try {
            val root = JSONObject(trimmed)
            val directKeys = arrayOf("lyrics", "lyric", "lrc", "content")
            for (key in directKeys) {
                val text = root.optString(key).trim()
                if (text.isNotEmpty()) {
                    return text
                }
            }
            val dataText = root.opt("data")
            if (dataText is String && dataText.trim().isNotEmpty()) {
                return dataText.trim()
            }
            val dataObj = root.optJSONObject("data")
            if (dataObj != null) {
                for (key in directKeys) {
                    val text = dataObj.optString(key).trim()
                    if (text.isNotEmpty()) {
                        return text
                    }
                }
            }
            trimmed
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun parseLyricLines(rawLyrics: String): List<LyricLine> {
        val normalized = rawLyrics
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }

        // Some providers return all LRC tags in one line. Inject newlines before each time tag.
        val expanded = normalized.replace(
            Regex("(?<!\\n)\\[(\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?)]"),
            "\n[$1]"
        )
        val timedPattern = Regex("\\[(\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?)]\\s*([^\\[]*)")
        val parsed = mutableListOf<LyricLine>()
        timedPattern.findAll(expanded).forEach { match ->
            val timeTag = match.groupValues[1]
            val text = match.groupValues[2].replace('\n', ' ').trim()
            val timeMs = parseLrcTimeTagToMs(timeTag)
            if (timeMs >= 0L && text.isNotEmpty()) {
                parsed.add(LyricLine(timeMs = timeMs, text = text))
            }
        }
        if (parsed.isNotEmpty()) {
            return parsed.sortedBy { it.timeMs }
        }
        return emptyList()
    }

    private fun parseLrcTimeTagToMs(timeTag: String): Long {
        val parts = timeTag.split(":")
        if (parts.size != 2) {
            return -1L
        }
        val minute = parts[0].toLongOrNull() ?: return -1L
        val secPart = parts[1]
        return if (secPart.contains(".")) {
            val secSplit = secPart.split(".", limit = 2)
            if (secSplit.size != 2) {
                return -1L
            }
            val second = secSplit[0].toLongOrNull() ?: return -1L
            val ms = secSplit[1].padEnd(3, '0').take(3).toLongOrNull() ?: return -1L
            minute * 60_000L + second * 1_000L + ms
        } else {
            val second = secPart.toLongOrNull() ?: return -1L
            minute * 60_000L + second * 1_000L
        }
    }

    private fun renderHomeLyricsByPosition(positionMs: Long) {
        if (homeLyricsLines.isEmpty()) {
            homeLyricsText.text = getString(R.string.home_lyrics_placeholder)
            return
        }
        val activeIndex = findActiveLyricIndex(positionMs)
        val builder = SpannableStringBuilder()
        homeLyricsLines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line.text)
            val end = builder.length
            val isActive = index == activeIndex
            builder.setSpan(
                ForegroundColorSpan(resources.getColor(if (isActive) R.color.white else R.color.text_secondary)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(
                AbsoluteSizeSpan(if (isActive) 18 else 17, true),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (isActive) {
                builder.setSpan(
                    StyleSpan(android.graphics.Typeface.BOLD),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            if (index < homeLyricsLines.lastIndex) {
                builder.append('\n')
            }
        }
        homeLyricsText.text = builder
        centerHomeLyricsLine(activeIndex)
    }

    private fun centerHomeLyricsLine(activeIndex: Int) {
        homeLyricsText.post {
            val layout = homeLyricsText.layout ?: return@post
            val viewportHeight = homeLyricsScroll.height
            if (viewportHeight <= 0) {
                return@post
            }
            if (activeIndex < 0 || activeIndex >= layout.lineCount) {
                return@post
            }
            val lineHeight = (layout.getLineBottom(activeIndex) - layout.getLineTop(activeIndex))
                .coerceAtLeast(dpToPx(20))
            val dynamicVerticalPadding = (viewportHeight / 2 - lineHeight / 2).coerceAtLeast(0)
            if (homeLyricsText.paddingTop != dynamicVerticalPadding || homeLyricsText.paddingBottom != dynamicVerticalPadding) {
                homeLyricsText.setPadding(
                    homeLyricsText.paddingLeft,
                    dynamicVerticalPadding,
                    homeLyricsText.paddingRight,
                    dynamicVerticalPadding
                )
                homeLyricsText.post { centerHomeLyricsLine(activeIndex) }
                return@post
            }

            val lineCenter = homeLyricsText.paddingTop +
                (layout.getLineTop(activeIndex) + layout.getLineBottom(activeIndex)) / 2
            val viewportCenter = viewportHeight / 2
            val maxScroll = (homeLyricsText.height - homeLyricsScroll.height).coerceAtLeast(0)
            val targetScroll = (lineCenter - viewportCenter).coerceIn(0, maxScroll)
            homeLyricsScroll.scrollTo(0, targetScroll)
        }
    }

    private fun findActiveLyricIndex(positionMs: Long): Int {
        var active = 0
        homeLyricsLines.forEachIndexed { index, line ->
            if (positionMs >= line.timeMs) {
                active = index
            }
        }
        return active
    }

    private fun renderTrackContainer(
        container: LinearLayout,
        tracks: List<EmbyTrack>,
        highlightCurrent: Boolean,
        emptyRes: Int,
        source: ListSource
    ) {
        container.removeAllViews()
        if (tracks.isEmpty()) {
            val empty = TextView(this)
            empty.setBackgroundResource(R.drawable.row_recommend_idle)
            empty.setText(emptyRes)
            empty.setTextColor(resources.getColor(R.color.text_secondary))
            empty.textSize = 14f
            empty.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
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
            val row = LinearLayout(this)
            val isCurrent = highlightCurrent && index == currentTrackIndex
            val title = TextView(this)
            val artist = TextView(this)
            row.orientation = LinearLayout.VERTICAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = dpToPx(70)
            row.setBackgroundResource(
                if (isCurrent) R.drawable.row_recommend_active else R.drawable.row_recommend_idle
            )
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))

            title.text = if (isCurrent) "\u25B6 ${track.title}" else track.title
            title.setTextColor(resources.getColor(R.color.text_primary))
            title.textSize = if (source == ListSource.LIBRARY) 17f else 16f
            title.setTypeface(
                null,
                if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )

            val artistLabel = track.artist.ifBlank { getString(R.string.track_artist_server) }
            artist.text = artistLabel
            artist.setTextColor(resources.getColor(R.color.text_secondary))
            artist.textSize = if (source == ListSource.LIBRARY) 14f else 13f

            row.addView(title)
            row.addView(artist)
            row.setOnClickListener {
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
        val targetTracks = if (source == ListSource.LIBRARY) libraryTracks else loadedTracks
        if (targetTracks.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
            return
        }

        if (source == ListSource.LIBRARY) {
            val safeLibraryIndex = index.coerceIn(0, targetTracks.lastIndex)
            val endExclusive = (safeLibraryIndex + DEFAULT_HOME_QUEUE_SIZE).coerceAtMost(targetTracks.size)
            loadedTracks = targetTracks.subList(safeLibraryIndex, endExclusive)
            currentTrackIndex = 0
        } else {
            val safeIndex = index.coerceIn(0, loadedTracks.lastIndex)
            currentTrackIndex = safeIndex
        }
        previewArtistOverride = loadedTracks[currentTrackIndex].artist.ifBlank { null }
        syncNativeQueueToCurrentIndex()
        val first = loadedTracks[currentTrackIndex].title

        val feedbackRes = if (source == ListSource.QUEUE) R.string.feedback_play_now_queue else R.string.feedback_play_now_library
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
        val currentTitle = loadedTracks[currentTrackIndex].title
        syncNativeQueueToCurrentIndex()
        maybeRefillQueueAtTail("next-track")
        return currentTitle
    }

    private fun maybeRefillQueueAtTail(trigger: String) {
        if (queueTailRefillInFlight) {
            return
        }
        if (loadedTracks.size < 4) {
            return
        }
        val remaining = loadedTracks.lastIndex - currentTrackIndex
        if (remaining > 2) {
            return
        }
        val base = embySessionBaseUrl ?: return
        var userId = embySessionUserId ?: return
        var token = embyAccessToken ?: return
        val credentials = EmbyCredentials(
            baseUrl = embyBaseUrlInput.text.toString().trim(),
            username = embyUsernameInput.text.toString().trim(),
            password = embyPasswordInput.text.toString().trim(),
            cfReferenceDomain = resolveCfReferenceDomain()
        )
        queueTailRefillInFlight = true

        val removedPlayed = currentTrackIndex.coerceAtLeast(0)
        if (removedPlayed > 0) {
            loadedTracks = loadedTracks.drop(currentTrackIndex)
            currentTrackIndex = 0
            syncNativeQueueToCurrentIndex()
            rebuildTrackLists()
        }

        appendRuntimeLog("queue tail refill start trigger=$trigger removed=$removedPlayed keep=${loadedTracks.size}")
        val embyClient = buildEmbyHttpClient(base, credentials.cfReferenceDomain)
        Thread {
            var response = executeGet(
                endpoint = buildEmbyRecommendedItemsUrl(base, userId, token, DEFAULT_HOME_QUEUE_SIZE),
                token = token,
                requestLabel = "GET /Users/{id}/Items (Tail Refill/${DEFAULT_HOME_QUEUE_SIZE})",
                log = { message -> appendRuntimeLog("queue-refill $message") },
                httpClient = embyClient
            )
            if (response.code == 401 && credentials.username.isNotEmpty() && credentials.password.isNotEmpty()) {
                val refreshed = authenticateByName(
                    embyBase = base,
                    username = credentials.username,
                    password = credentials.password,
                    log = { message -> appendRuntimeLog("queue-refill $message") },
                    httpClient = embyClient
                )
                if (refreshed != null) {
                    userId = refreshed.userId
                    token = refreshed.accessToken
                    embySessionUserId = refreshed.userId
                    embyAccessToken = refreshed.accessToken
                    persistCachedSessionAuth(base, credentials.username, refreshed)
                    response = executeGet(
                        endpoint = buildEmbyRecommendedItemsUrl(base, userId, token, DEFAULT_HOME_QUEUE_SIZE),
                        token = token,
                        requestLabel = "GET /Users/{id}/Items (Tail Refill Retry/${DEFAULT_HOME_QUEUE_SIZE})",
                        log = { message -> appendRuntimeLog("queue-refill $message") },
                        httpClient = embyClient
                    )
                }
            }
            val fetched = if (response.code in 200..299) parseTrackItems(response.payload) else emptyList()
            runOnUiThread {
                queueTailRefillInFlight = false
                if (response.code !in 200..299 || fetched.isEmpty()) {
                    appendRuntimeLog("queue tail refill skip code=${response.code} fetched=${fetched.size}")
                    return@runOnUiThread
                }
                val existingIds = HashSet<String>(loadedTracks.size * 2 + fetched.size)
                loadedTracks.forEach { existingIds.add(it.id) }
                val append = fetched.filter { existingIds.add(it.id) }.take(DEFAULT_HOME_QUEUE_SIZE)
                if (append.isEmpty()) {
                    appendRuntimeLog("queue tail refill no-append reason=dedupe")
                    return@runOnUiThread
                }
                loadedTracks = loadedTracks + append
                syncNativeQueueToCurrentIndex()
                rebuildTrackLists()
                updateState {
                    it.copy(
                        nextEnabled = hasNextTrack(),
                        feedbackText = getString(
                            R.string.feedback_queue_tail_refill,
                            append.size,
                            removedPlayed
                        )
                    )
                }
                appendRuntimeLog("queue tail refill done appended=${append.size} total=${loadedTracks.size}")
            }
        }.start()
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

    private fun startUiProgressTicker() {
        uiProgressHandler.removeCallbacks(uiProgressTicker)
        uiProgressHandler.post(uiProgressTicker)
    }

    private fun stopUiProgressTicker() {
        uiProgressHandler.removeCallbacks(uiProgressTicker)
    }

    private fun refreshProgressMetrics() {
        if (!this::playbackProgressValue.isInitialized || !this::playbackDurationValue.isInitialized || !this::downloadProgressValue.isInitialized || !this::playbackSeekBar.isInitialized) {
            return
        }
        val track = loadedTracks.getOrNull(currentTrackIndex)
        if (track == null) {
            playbackProgressValue.text = formatDurationClock(-1L)
            playbackDurationValue.text = formatDurationClock(-1L)
            downloadProgressValue.text = getString(R.string.download_progress_placeholder)
            if (!isUserSeeking) {
                playbackSeekBar.progress = 0
            }
            renderHomeLyricsByPosition(0L)
            return
        }

        val engineDurationMs = playbackEngine?.durationMs() ?: -1L
        val durationMs = resolveTrackDurationMs(track, engineDurationMs)
        val positionMsRaw = playbackEngine?.currentPositionMs() ?: -1L
        val positionMs = positionMsRaw.coerceAtLeast(0L)
        val positionText = if (positionMsRaw >= 0L) {
            formatDurationClock(positionMs)
        } else {
            "--:--"
        }
        val totalText = if (durationMs > 0L) {
            formatDurationClock(durationMs)
        } else {
            "--:--"
        }
        playbackProgressValue.text = positionText
        playbackDurationValue.text = totalText
        if (!isUserSeeking) {
            val seekProgress = if (durationMs > 0L) {
                ((positionMs * SEEK_BAR_MAX.toLong()) / durationMs).toInt().coerceIn(0, SEEK_BAR_MAX)
            } else {
                0
            }
            playbackSeekBar.progress = seekProgress
        }
        renderHomeLyricsByPosition(positionMs)

        val state = getOrCreateTrackDownloadState(track)
        val downloadedBytes = state.downloadedBytes.coerceAtLeast(0L)
        val totalBytes = state.totalBytes
        val downloadedText = Formatter.formatShortFileSize(this, downloadedBytes)
        val totalBytesText = if (totalBytes > 0L) {
            Formatter.formatShortFileSize(this, totalBytes)
        } else {
            "?"
        }
        val playableSec = estimatePlayableSeconds(track, durationMs)
        val playableText = formatDurationClock(playableSec * 1000L)
        val durationText = if (durationMs > 0L) {
            formatDurationClock(durationMs)
        } else {
            "--:--"
        }
        downloadProgressValue.text = getString(
            R.string.download_progress_format,
            downloadedText,
            totalBytesText,
            playableText,
            durationText
        )

    }

    private fun applySeekTarget(targetMs: Long) {
        if (targetMs < 0L) {
            refreshProgressMetrics()
            return
        }
        val engine = playbackEngine
        if (engine == null) {
            refreshProgressMetrics()
            return
        }
        val ok = engine.seekTo(targetMs)
        appendRuntimeLog("play seek targetMs=$targetMs ok=$ok")
        if (!ok) {
            updateState {
                it.copy(feedbackText = getString(R.string.feedback_seek_failed))
            }
        }
        refreshProgressMetrics()
    }

    private fun categorizePlaybackError(code: Int, detail: String): PlaybackFailureCategory {
        val lower = detail.lowercase(Locale.US)
        if (code == 4003 ||
            code in 4000..4099 ||
            lower.contains("mediacodec") ||
            lower.contains("renderer") ||
            lower.contains("decode")
        ) {
            return PlaybackFailureCategory.DECODER_FAILURE
        }
        if (code in 2000..2099 || lower.contains("timeout") || lower.contains("network") || lower.contains("connection")) {
            return PlaybackFailureCategory.NETWORK_FAILURE
        }
        if (lower.contains("http") || lower.contains("container") || lower.contains("unsupported") || lower.contains("parser")) {
            return PlaybackFailureCategory.SOURCE_FAILURE
        }
        return PlaybackFailureCategory.UNKNOWN
    }

    private fun handlePlaybackErrorAutoSkip(
        requestId: Int,
        code: Int,
        detail: String,
        source: String
    ) {
        if (requestId != playbackRequestId) {
            return
        }
        val category = categorizePlaybackError(code, detail)
        synchronized(playbackErrorHandleLock) {
            if (playbackErrorHandledRequestId == requestId) {
                return
            }
            playbackErrorHandledRequestId = requestId
        }
        val track = loadedTracks.getOrNull(currentTrackIndex)
        appendRuntimeLog(
            "playback error source=$source requestId=$requestId category=$category code=$code detail=$detail track=${track?.title ?: "<unknown>"}"
        )
        runOnUiThread {
            if (requestId != playbackRequestId) {
                return@runOnUiThread
            }
            val nextTitle = moveToNextTrack()
            if (nextTitle != null) {
                updateState {
                    it.copy(
                        currentTrack = nextTitle,
                        isPlaying = false,
                        playbackStatusRes = R.string.status_paused,
                        playPauseLabelRes = R.string.action_play,
                        nextEnabled = hasNextTrack(),
                        feedbackText = getString(R.string.feedback_auto_skipped_failed_track)
                    )
                }
                rebuildTrackLists()
                playTrackAtCurrentIndex()
            } else {
                updateState {
                    it.copy(
                        isPlaying = false,
                        playbackStatusRes = R.string.status_paused,
                        playPauseLabelRes = R.string.action_play,
                        nextEnabled = false,
                        feedbackText = getString(R.string.feedback_no_next_after_failed_track)
                    )
                }
                rebuildTrackLists()
            }
        }
    }

    private fun resolveTrackDurationMs(track: EmbyTrack, durationMsHint: Long): Long {
        if (durationMsHint > 0L) {
            return durationMsHint
        }
        return runTimeTicksToDurationMs(track.runtimeTicks)
    }

    private fun runTimeTicksToDurationMs(runTimeTicks: Long): Long {
        if (runTimeTicks <= 0L) {
            return -1L
        }
        // Emby RunTimeTicks follows .NET ticks (100ns per tick).
        return (runTimeTicks / 10_000L).coerceAtLeast(0L)
    }

    private fun formatDurationClock(durationMs: Long): String {
        if (durationMs < 0L) {
            return "00:00"
        }
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
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
            password = embyPasswordInput.text.toString().trim(),
            cfReferenceDomain = resolveCfReferenceDomain()
        )
        if (credentials.baseUrl.isEmpty() || credentials.username.isEmpty() || credentials.password.isEmpty()) {
            appendRuntimeLog("queue auto refresh skip trigger=$trigger reason=missing-emby-credentials")
            return
        }
        lastQueueAutoRefreshMs = now
        queueAutoRefreshInFlight = true
        appendRuntimeLog("queue auto refresh start trigger=$trigger")
        requestTracksFromEmby(
            credentials = credentials,
            onFinished = {
                queueAutoRefreshInFlight = false
                appendRuntimeLog("queue auto refresh finish trigger=$trigger tracks=${loadedTracks.size}")
            }
        )
    }

    private fun requestTracksFromEmby(
        credentials: EmbyCredentials,
        onFinished: (() -> Unit)? = null,
        forceRefreshRecommendations: Boolean = false
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
            val result = fetchTracksFromEmby(
                credentials = credentials,
                forceRefreshRecommendations = forceRefreshRecommendations
            )
            runOnUiThread {
                try {
                    if (result.success) {
                        appendRuntimeLog("emby load success tracks=${result.tracks.size}")
                        loadedTracks = result.tracks.take(DEFAULT_HOME_QUEUE_SIZE)
                        embySessionBaseUrl = result.embyBase
                        embySessionUserId = result.embyUserId
                        embyAccessToken = result.accessToken
                        libraryTracks.clear()
                        libraryNextStartIndex = 0
                        libraryTotalRecordCount = Int.MAX_VALUE
                        currentTrackIndex = 0
                        previewArtistOverride = loadedTracks.firstOrNull()?.artist?.takeIf { it.isNotBlank() }
                        playbackRequestId += 1
                        stopDownloadController()
                        releasePlayer()
                        val firstTrack = loadedTracks.firstOrNull()?.title
                            ?: getString(R.string.track_not_loaded)
                        persistCredentials(credentials)
                        val nativeSynced = syncNativeQueueToCurrentIndex()
                        val feedbackTail = if (nativeSynced) {
                            result.feedbackText
                        } else {
                            result.feedbackText + "\n- 原生队列不可用，播放保持降级模式"
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
                        if (selectedPage == PAGE_LIBRARY) {
                            ensureLibraryTracksLoaded("emby-load-success")
                        }
                        showToast(R.string.toast_emby_success)
                        if (loadedTracks.isNotEmpty()) {
                            appendRuntimeLog("emby load success autoplay-first-track")
                            playTrackAtCurrentIndex()
                        }
                    } else {
                        appendRuntimeLog("emby load failed")
                        loadedTracks = emptyList()
                        libraryTracks.clear()
                        libraryNextStartIndex = 0
                        libraryTotalRecordCount = Int.MAX_VALUE
                        embySessionBaseUrl = null
                        embySessionUserId = null
                        embyAccessToken = null
                        currentTrackIndex = 0
                        previewArtistOverride = null
                        playbackRequestId += 1
                        stopDownloadController()
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
        val lrcApiClient = buildEmbyHttpClient(normalized, resolveCfReferenceDomain())
        return try {
            appendRuntimeLog("lrcapi test GET $normalized")
            val request = Request.Builder()
                .url(normalized)
                .get()
                .build()
            lrcApiClient.newCall(request).execute().use { response ->
                val code = response.code()
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
            }
        } catch (e: Exception) {
            appendRuntimeLog("lrcapi test exception type=${e.javaClass.simpleName} msg=${e.message}")
            LrcApiTestResult(
                success = false,
                statusText = getString(R.string.lrcapi_status_failed),
                feedbackText = getString(R.string.feedback_lrcapi_failed)
            )
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
        stopDownloadController()
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
        synchronized(playbackErrorHandleLock) {
            playbackErrorHandledRequestId = -1
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
                    feedbackText = "动作反馈：播放会话缺失"
                )
            }
            return
        }

        currentTrackIndex = currentTrackIndex.coerceIn(0, loadedTracks.lastIndex)
        val track = loadedTracks[currentTrackIndex]
        val nextTrack = loadedTracks.getOrNull(currentTrackIndex + 1)
        val downloadUrl = buildEmbyDownloadUrl(base, track.id, token)
        val embyClient = buildEmbyHttpClient(base, resolveCfReferenceDomain())
        val requestId = ++playbackRequestId
        val streamPrepared = AtomicBoolean(false)
        val fallbackTriggered = AtomicBoolean(false)
        appendRuntimeLog("play request track=${track.title} downloadUrl=$downloadUrl requestId=$requestId")
        releasePlayer()
        startDownloadController(
            requestId = requestId,
            embyBase = base,
            token = token,
            currentTrack = track,
            nextTrack = nextTrack,
            httpClient = embyClient
        )

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
                stopDownloadController()
                updateState {
                    it.copy(
                        isPlaying = false,
                        playbackStatusRes = R.string.status_paused,
                        playPauseLabelRes = R.string.action_play,
                        feedbackText = feedback
                    )
                }
                downloadAndPlayTrack(track, base, token, requestId, embyClient)
            }
        }

        try {
            val engine = ensurePlaybackEngine(embyClient)
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
                                    feedbackText = "动作反馈：正在播放 ${track.title}"
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
                        handlePlaybackErrorAutoSkip(
                            requestId = requestId,
                            code = code,
                            detail = detail,
                            source = "download"
                        )
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
                                    feedbackText = "动作反馈：缓冲中 ${track.title}"
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
                                    feedbackText = "动作反馈：正在播放 ${track.title}"
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
                    feedbackText = "动作反馈：准备播放 ${track.title}"
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
                    triggerCachedFallback("动作反馈：下载播放超时，切换本地缓存播放")
                }
            }.start()
        } catch (e: Exception) {
            appendRuntimeLog("play setup exception requestId=$requestId type=${e.javaClass.simpleName} msg=${e.message}")
            triggerCachedFallback(
                "动作反馈：下载链路初始化失败（${e.javaClass.simpleName}），切换本地缓存播放"
            )
        }
    }

    private fun startDownloadController(
        requestId: Int,
        embyBase: String,
        token: String,
        currentTrack: EmbyTrack,
        nextTrack: EmbyTrack?,
        httpClient: OkHttpClient
    ) {
        stopDownloadController()
        downloadControllerStop = false
        downloadControllerRequestId = requestId
        val worker = Thread {
            runDownloadController(
                requestId = requestId,
                embyBase = embyBase,
                token = token,
                currentTrack = currentTrack,
                nextTrack = nextTrack,
                httpClient = httpClient
            )
        }
        worker.name = "download-controller-$requestId"
        worker.isDaemon = true
        downloadControllerThread = worker
        worker.start()
    }

    private fun stopDownloadController() {
        downloadControllerStop = true
        downloadControllerRequestId = -1
        downloadControllerThread?.interrupt()
        downloadControllerThread = null
    }

    private fun runDownloadController(
        requestId: Int,
        embyBase: String,
        token: String,
        currentTrack: EmbyTrack,
        nextTrack: EmbyTrack?,
        httpClient: OkHttpClient
    ) {
        appendRuntimeLog("dl-ctl start requestId=$requestId current=${currentTrack.title}")
        var lastPhase: DownloadControlPhase? = null
        var lastHeartbeatMs = 0L
        while (!downloadControllerStop && requestId == playbackRequestId) {
            val durationMs = playbackEngine?.durationMs() ?: -1L
            val positionMs = playbackEngine?.currentPositionMs() ?: -1L
            val playedSec = if (positionMs >= 0L) positionMs / 1000L else -1L
            val remainingSec = if (durationMs > 0L && positionMs >= 0L) {
                ((durationMs - positionMs).coerceAtLeast(0L) / 1000L)
            } else {
                Long.MAX_VALUE
            }
            val currentPlayableSec = estimatePlayableSeconds(currentTrack, durationMs)
            val currentAheadSec = if (playedSec >= 0L) {
                (currentPlayableSec - playedSec).coerceAtLeast(0L)
            } else {
                currentPlayableSec
            }
            val currentComplete = isTrackDownloadComplete(currentTrack, durationMs)
            val nextPlayableSec = if (nextTrack != null) {
                estimatePlayableSeconds(nextTrack, -1L)
            } else {
                -1L
            }
            val phase = chooseDownloadControlPhase(
                remainingSec = remainingSec,
                currentAheadSec = currentAheadSec,
                playbackPositionKnown = positionMs >= 0L,
                currentTrackComplete = currentComplete,
                hasNextTrack = nextTrack != null,
                nextPlayableSec = nextPlayableSec
            )
            if (phase != lastPhase) {
                appendRuntimeLog(
                    "dl-ctl phase=$phase remainSec=$remainingSec currentPlayableSec=$currentPlayableSec currentAheadSec=$currentAheadSec nextPlayableSec=$nextPlayableSec"
                )
                if (phase == DownloadControlPhase.IDLE) {
                    appendRuntimeLog(
                        "dl-ctl pause reason=${describeIdleReason(remainingSec, currentAheadSec, currentComplete, nextTrack != null, nextPlayableSec)}"
                    )
                }
                lastPhase = phase
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastHeartbeatMs >= DOWNLOAD_HEARTBEAT_LOG_INTERVAL_MS) {
                val state = getOrCreateTrackDownloadState(currentTrack)
                val remainText = if (remainingSec == Long.MAX_VALUE) "unknown" else "${remainingSec}s"
                appendRuntimeLog(
                    "dl-ctl heartbeat phase=$phase remain=$remainText track=${currentTrack.title} downloaded=${state.downloadedBytes}/${state.totalBytes} playableSec=$currentPlayableSec aheadSec=$currentAheadSec posMs=$positionMs durationMs=$durationMs"
                )
                lastHeartbeatMs = now
            }

            val didWork = when (phase) {
                DownloadControlPhase.MAINTAIN_CURRENT_WINDOW,
                DownloadControlPhase.FINISH_CURRENT_TRACK -> {
                    downloadChunkForTrack(
                        track = currentTrack,
                        embyBase = embyBase,
                        token = token,
                        httpClient = httpClient,
                        durationMsHint = durationMs,
                        maxChunkBytes = DOWNLOAD_CHUNK_BYTES
                    )
                }
                DownloadControlPhase.PREFETCH_NEXT_WINDOW -> {
                    if (nextTrack == null) {
                        false
                    } else {
                        downloadChunkForTrack(
                            track = nextTrack,
                            embyBase = embyBase,
                            token = token,
                            httpClient = httpClient,
                            durationMsHint = -1L,
                            maxChunkBytes = DOWNLOAD_CHUNK_BYTES
                        )
                    }
                }
                DownloadControlPhase.IDLE -> false
            }

            if (!didWork) {
                try {
                    Thread.sleep(DOWNLOAD_CONTROLLER_IDLE_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        appendRuntimeLog("dl-ctl stop requestId=$requestId")
    }

    private fun chooseDownloadControlPhase(
        remainingSec: Long,
        currentAheadSec: Long,
        playbackPositionKnown: Boolean,
        currentTrackComplete: Boolean,
        hasNextTrack: Boolean,
        nextPlayableSec: Long
    ): DownloadControlPhase {
        if (remainingSec < DOWNLOAD_WINDOW_SEC) {
            if (!currentTrackComplete) {
                return DownloadControlPhase.FINISH_CURRENT_TRACK
            }
            if (hasNextTrack && nextPlayableSec in 0 until DOWNLOAD_WINDOW_SEC) {
                return DownloadControlPhase.PREFETCH_NEXT_WINDOW
            }
            return DownloadControlPhase.IDLE
        }
        if (!playbackPositionKnown && !currentTrackComplete) {
            return DownloadControlPhase.MAINTAIN_CURRENT_WINDOW
        }
        if (currentAheadSec < DOWNLOAD_WINDOW_SEC) {
            return DownloadControlPhase.MAINTAIN_CURRENT_WINDOW
        }
        if (!currentTrackComplete) {
            return DownloadControlPhase.FINISH_CURRENT_TRACK
        }
        if (hasNextTrack && nextPlayableSec in 0 until DOWNLOAD_WINDOW_SEC) {
            return DownloadControlPhase.PREFETCH_NEXT_WINDOW
        }
        return DownloadControlPhase.IDLE
    }

    private fun describeIdleReason(
        remainingSec: Long,
        currentAheadSec: Long,
        currentTrackComplete: Boolean,
        hasNextTrack: Boolean,
        nextPlayableSec: Long
    ): String {
        if (remainingSec < DOWNLOAD_WINDOW_SEC) {
            if (!hasNextTrack) {
                return "near-end-no-next-track"
            }
            if (!currentTrackComplete) {
                return "near-end-current-not-complete"
            }
            if (nextPlayableSec >= DOWNLOAD_WINDOW_SEC) {
                return "near-end-next-window-ready"
            }
            return "near-end-waiting-next-window"
        }
        if (hasNextTrack && nextPlayableSec >= DOWNLOAD_WINDOW_SEC && currentAheadSec >= DOWNLOAD_WINDOW_SEC) {
            return "current-window-ready-next-window-ready"
        }
        if (currentAheadSec >= DOWNLOAD_WINDOW_SEC) {
            return "current-window-ready"
        }
        return "no-op"
    }

    private fun isTrackDownloadComplete(track: EmbyTrack, durationMsHint: Long): Boolean {
        val state = getOrCreateTrackDownloadState(track)
        if (state.completed) {
            return true
        }
        val durationMs = resolveTrackDurationMs(track, durationMsHint)
        if (durationMs > 0L) {
            val durationSec = durationMs / 1000L
            if (durationSec > 0L) {
                val playableSec = estimatePlayableSeconds(track, durationMs)
                if (playableSec + 1 >= durationSec) {
                    synchronized(downloadStateLock) {
                        state.completed = true
                    }
                    return true
                }
            }
        }
        return false
    }

    private fun estimatePlayableSeconds(track: EmbyTrack, durationMsHint: Long): Long {
        val state = getOrCreateTrackDownloadState(track)
        val durationMs = resolveTrackDurationMs(track, durationMsHint)
        if (durationMs > 0L && state.totalBytes > 0L) {
            val durationSec = durationMs / 1000L
            if (durationSec > 0L) {
                return ((state.downloadedBytes.toDouble() / state.totalBytes.toDouble()) * durationSec.toDouble()).toLong()
            }
        }
        val bitrate = state.bitrateBps.coerceAtLeast(64_000L)
        return ((state.downloadedBytes * 8L) / bitrate).coerceAtLeast(0L)
    }

    private fun getOrCreateTrackDownloadState(track: EmbyTrack): TrackDownloadState {
        val cacheFile = File(cacheDir, "emby_${track.id}.cache")
        synchronized(downloadStateLock) {
            val existing = trackDownloadStates[track.id]
            if (existing != null) {
                val fileLen = cacheFile.length().coerceAtLeast(0L)
                if (fileLen > existing.downloadedBytes) {
                    existing.downloadedBytes = fileLen
                }
                if (existing.totalBytes > 0L && existing.downloadedBytes >= existing.totalBytes) {
                    existing.completed = true
                }
                return existing
            }
            val created = TrackDownloadState(
                trackId = track.id,
                downloadedBytes = cacheFile.length().coerceAtLeast(0L),
                bitrateBps = lastKnownBitrateBps
            )
            trackDownloadStates[track.id] = created
            if (trackDownloadStates.size > TRACK_DOWNLOAD_STATE_MAX_SIZE) {
                val iterator = trackDownloadStates.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            return created
        }
    }

    private fun downloadChunkForTrack(
        track: EmbyTrack,
        embyBase: String,
        token: String,
        httpClient: OkHttpClient,
        durationMsHint: Long,
        maxChunkBytes: Long
    ): Boolean {
        val state = getOrCreateTrackDownloadState(track)
        if (state.completed) {
            appendRuntimeLog("dl-ctl chunk skip-completed track=${track.title}")
            return false
        }
        val file = File(cacheDir, "emby_${track.id}.cache")
        val start = file.length().coerceAtLeast(0L)
        if (state.totalBytes > 0L && start >= state.totalBytes) {
            synchronized(downloadStateLock) {
                state.downloadedBytes = start
                state.completed = true
            }
            appendRuntimeLog("dl-ctl chunk skip-eof track=${track.title} bytes=$start")
            return false
        }
        val end = if (state.totalBytes > 0L) {
            (start + maxChunkBytes - 1L).coerceAtMost(state.totalBytes - 1L)
        } else {
            start + maxChunkBytes - 1L
        }
        appendRuntimeLog("dl-ctl chunk start track=${track.title} range=$start-$end")
        val request = Request.Builder()
            .url(buildEmbyDownloadUrl(embyBase, track.id, token))
            .get()
            .header("Accept", "audio/*")
            .header("X-Emby-Token", token)
            .header("Range", "bytes=$start-$end")
            .build()
        val callClient = httpClient.newBuilder()
            .connectTimeout(10000L, TimeUnit.MILLISECONDS)
            .readTimeout(20000L, TimeUnit.MILLISECONDS)
            .build()
        try {
            callClient.newCall(request).execute().use { response ->
                val code = response.code()
                if (code !in 200..299) {
                    appendRuntimeLog("dl-ctl chunk fail code=$code track=${track.title}")
                    return false
                }
                if (code == 200 && start > 0L) {
                    appendRuntimeLog("dl-ctl range-unsupported track=${track.title} start=$start")
                    return false
                }
                val responseBody = response.body() ?: return false
                var written = 0L
                responseBody.byteStream().use { input ->
                    java.io.RandomAccessFile(file, "rw").use { raf ->
                        if (code == 206) {
                            raf.seek(start)
                        } else {
                            raf.setLength(0L)
                            raf.seek(0L)
                        }
                        val buffer = ByteArray(8192)
                        var len = input.read(buffer)
                        while (len >= 0) {
                            if (len > 0) {
                                raf.write(buffer, 0, len)
                                written += len.toLong()
                            }
                            len = input.read(buffer)
                        }
                    }
                }
                if (written <= 0L) {
                    return false
                }

                val totalBytesFromHeader = parseTotalBytesFromResponse(response, start)
                synchronized(downloadStateLock) {
                    state.downloadedBytes = file.length().coerceAtLeast(0L)
                    if (totalBytesFromHeader > 0L) {
                        state.totalBytes = totalBytesFromHeader
                    }
                    if (state.totalBytes > 0L && state.downloadedBytes >= state.totalBytes) {
                        state.completed = true
                    }
                    val durationMs = resolveTrackDurationMs(track, durationMsHint)
                    if (durationMs > 0L && state.totalBytes > 0L) {
                        val bitrate = ((state.totalBytes * 8L * 1000L) / durationMs).coerceAtLeast(64_000L)
                        state.bitrateBps = bitrate
                        lastKnownBitrateBps = bitrate
                    }
                }
                val playableSecAfter = estimatePlayableSeconds(track, durationMsHint)
                appendRuntimeLog(
                    "dl-ctl chunk ok track=${track.title} written=$written downloaded=${state.downloadedBytes} total=${state.totalBytes} playableSec=$playableSecAfter complete=${state.completed}"
                )
                return true
            }
        } catch (e: Exception) {
            appendRuntimeLog("dl-ctl chunk exception track=${track.title} type=${e.javaClass.simpleName}")
            return false
        }
    }

    private fun parseTotalBytesFromResponse(response: okhttp3.Response, start: Long): Long {
        val contentRange = response.header("Content-Range").orEmpty()
        if (contentRange.isNotEmpty()) {
            val slashIndex = contentRange.lastIndexOf('/')
            if (slashIndex >= 0 && slashIndex + 1 < contentRange.length) {
                val tail = contentRange.substring(slashIndex + 1).trim()
                val parsed = tail.toLongOrNull() ?: -1L
                if (parsed > 0L) {
                    return parsed
                }
            }
        }
        val bodyLength = response.body()?.contentLength() ?: -1L
        if (bodyLength > 0L) {
            return if (response.code() == 206) start + bodyLength else bodyLength
        }
        return -1L
    }

    private fun downloadAndPlayTrack(
        track: EmbyTrack,
        embyBase: String,
        token: String,
        requestId: Int,
        httpClient: OkHttpClient
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
                    feedbackText = "动作反馈：正在下载缓存 ${track.title}"
                )
            }
        }
        Thread {
            val cacheFile = downloadTrackToCache(track, embyBase, token, httpClient)
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
                            feedbackText = "动作反馈：缓存回退下载失败"
                        )
                    }
                    return@runOnUiThread
                }

                try {
                    appendRuntimeLog("cache fallback file ready requestId=$requestId path=${cacheFile.absolutePath} size=${cacheFile.length()}")
                    releasePlayer()
                    val engine = ensurePlaybackEngine(httpClient)
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
                                            feedbackText = "动作反馈：正在播放缓存 ${track.title}"
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
                                handlePlaybackErrorAutoSkip(
                                    requestId = requestId,
                                    code = code,
                                    detail = detail,
                                    source = "cache"
                                )
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
                            feedbackText = "动作反馈：缓存缓冲中 ${track.title}"
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
                            feedbackText = "动作反馈：缓存播放失败（${e.javaClass.simpleName}）"
                        )
                    }
                }
            }
        }.start()
    }

    private fun downloadTrackToCache(
        track: EmbyTrack,
        embyBase: String,
        token: String,
        httpClient: OkHttpClient
    ): File? {
        val candidateUrls = listOf(
            buildEmbyDownloadUrl(embyBase, track.id, token)
        )
        for (candidateUrl in candidateUrls) {
            try {
                appendRuntimeLog("emby download try GET $candidateUrl")
                val request = Request.Builder()
                    .url(candidateUrl)
                    .get()
                    .header("Accept", "audio/*")
                    .header("X-Emby-Token", token)
                    .build()
                val callClient = httpClient.newBuilder()
                    .connectTimeout(10000L, TimeUnit.MILLISECONDS)
                    .readTimeout(20000L, TimeUnit.MILLISECONDS)
                    .build()
                callClient.newCall(request).execute().use { response ->
                    val code = response.code()
                    if (code !in 200..299) {
                        appendRuntimeLog("emby download non-2xx code=$code url=$candidateUrl")
                        Log.w(LOG_TAG, "downloadTrackToCache HTTP $code url=$candidateUrl")
                        return@use
                    }
                    val responseBody = response.body()
                    if (responseBody == null) {
                        appendRuntimeLog("emby download empty body url=$candidateUrl")
                        return@use
                    }
                    val file = File(cacheDir, "emby_${track.id}.cache")
                    responseBody.byteStream().use { input ->
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
                        return@use
                    }
                    appendRuntimeLog("emby download success bytes=${file.length()} url=$candidateUrl")
                    return file
                }
            } catch (e: Exception) {
                appendRuntimeLog("emby download exception type=${e.javaClass.simpleName} msg=${e.message} url=$candidateUrl")
                Log.w(
                    LOG_TAG,
                    "downloadTrackToCache failed url=$candidateUrl: ${e.javaClass.simpleName} ${e.message}"
                )
            }
        }
        return null
    }

    private fun ensurePlaybackEngine(httpClient: OkHttpClient): PlaybackEngine {
        val existing = playbackEngine
        if (existing != null) {
            return existing
        }
        // Keep API17 compatibility: avoid exoplayer extension-okhttp (minSdk 21 in 2.17.1).
        // Streaming path uses Exo default HTTP stack; Emby API/download control path still uses OkHttp client.
        val defaultDataSourceFactory = DefaultDataSource.Factory(
            this,
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
        )
        val created = ExoPlaybackEngine(this, defaultDataSourceFactory)
        playbackEngine = created
        return created
    }

    private fun releasePlayer() {
        playbackEngine?.release()
        playbackEngine = null
    }

    private fun fetchTracksFromEmby(
        credentials: EmbyCredentials,
        forceRefreshRecommendations: Boolean
    ): EmbyLoadResult {
        val logs = mutableListOf<String>()
        val logger: (String) -> Unit = { message ->
            logs.add(message)
            appendRuntimeLog("emby $message")
        }

        try {
            val embyBase = normalizeEmbyBase(credentials.baseUrl)
            val embyClient = buildEmbyHttpClient(embyBase, credentials.cfReferenceDomain)
            val ownerKey = buildRecommendCacheOwnerKey(embyBase, credentials.username)
            logger("base=$embyBase")
            logger("force-refresh=$forceRefreshRecommendations")

            var auth: AuthByNameResult? = loadCachedSessionAuth(
                embyBase = embyBase,
                username = credentials.username
            )
            if (auth != null) {
                logger("auth-mode=cached-token user=${shortId(auth.userId)}")
            } else {
                logger("auth-mode=username-password")
            }

            if (!forceRefreshRecommendations && auth != null) {
                val cachedTracks = loadTodayRecommendCache(ownerKey)
                if (cachedTracks.isNotEmpty()) {
                    logger("tracks=${cachedTracks.size}")
                    logger("tracks-source=缓存-当日")
                    logger("tracks-sample=${cachedTracks.take(3).joinToString(" | ") { it.title }}")
                    val cachedAuth = auth
                    if (cachedAuth != null) {
                        return EmbyLoadResult(
                            success = true,
                            statusText = getString(R.string.emby_status_connected) + " (${cachedTracks.size}, 缓存-当日)",
                            feedbackText = formatFeedback(
                                headline = getString(R.string.feedback_emby_connected),
                                logs = logs
                            ),
                            tracks = cachedTracks,
                            embyBase = embyBase,
                            embyUserId = cachedAuth.userId,
                            accessToken = cachedAuth.accessToken
                        )
                    }
                }
            }

            if (auth == null) {
                auth = authenticateByName(
                    embyBase = embyBase,
                    username = credentials.username,
                    password = credentials.password,
                    log = logger,
                    httpClient = embyClient
                ) ?: return failedResult(
                    headline = "动作反馈：Emby 鉴权失败",
                    logs = logs
                )
                logger("auth success user=${shortId(auth.userId)}")
            }

            var retriedAfterUnauthorized = false
            while (true) {
                val activeAuth = auth ?: return failedResult(
                    headline = "动作反馈：Emby 鉴权失败",
                    logs = logs
                )
                val recommendedEndpoint = buildEmbyRecommendedItemsUrl(
                    embyBase = embyBase,
                    userId = activeAuth.userId,
                    token = activeAuth.accessToken,
                    limit = DEFAULT_HOME_QUEUE_SIZE
                )
                val recommendedResponse = executeGet(
                    endpoint = recommendedEndpoint,
                    token = activeAuth.accessToken,
                    requestLabel = "GET /Users/{id}/Items (Random/${DEFAULT_HOME_QUEUE_SIZE})",
                    log = logger,
                    httpClient = embyClient
                )

                if (recommendedResponse.code == 401 && !retriedAfterUnauthorized) {
                    logger("recommend unauthorized -> re-auth")
                    clearCachedSessionAuth(embyBase, credentials.username)
                    auth = authenticateByName(
                        embyBase = embyBase,
                        username = credentials.username,
                        password = credentials.password,
                        log = logger,
                        httpClient = embyClient
                    ) ?: return failedResult(
                        headline = "动作反馈：Emby 鉴权失败",
                        logs = logs
                    )
                    retriedAfterUnauthorized = true
                    continue
                }

                if (recommendedResponse.code !in 200..299) {
                    return failedResult(
                        headline = "动作反馈：Emby 随机拉取失败（HTTP ${recommendedResponse.code}）",
                        logs = logs
                    )
                }

                val source = "随机-${DEFAULT_HOME_QUEUE_SIZE}"
                val tracks = parseTrackItems(recommendedResponse.payload)

                logger("tracks=${tracks.size}")
                if (tracks.isNotEmpty()) {
                    logger("tracks-source=$source")
                    logger("tracks-sample=${tracks.take(3).joinToString(" | ") { it.title }}")
                }
                if (tracks.isEmpty()) {
                    return failedResult(
                        headline = "动作反馈：未返回可播放音频",
                        logs = logs
                    )
                }

                persistCachedSessionAuth(
                    embyBase = embyBase,
                    username = credentials.username,
                    auth = activeAuth
                )
                persistTodayRecommendCache(ownerKey, tracks)

                return EmbyLoadResult(
                    success = true,
                    statusText = getString(R.string.emby_status_connected) + " (${tracks.size}, $source)",
                    feedbackText = formatFeedback(
                        headline = getString(R.string.feedback_emby_connected),
                        logs = logs
                    ),
                    tracks = tracks,
                    embyBase = embyBase,
                    embyUserId = activeAuth.userId,
                    accessToken = activeAuth.accessToken
                )
            }
        } catch (e: Exception) {
            logger("exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            return failedResult(
                headline = getString(R.string.feedback_emby_failed),
                logs = logs
            )
        }
    }

    private fun authenticateByName(
        embyBase: String,
        username: String,
        password: String,
        log: (String) -> Unit,
        httpClient: OkHttpClient
    ): AuthByNameResult? {
        return try {
            val endpoint = buildAuthenticateByNameUrl(embyBase)
            log("POST $endpoint")
            val body = JSONObject()
                .put("Username", username)
                .put("Pw", password)
                .toString()
            val requestBody = RequestBody.create(jsonMediaType, body)
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .build()
            val callClient = httpClient.newBuilder()
                .connectTimeout(6000L, TimeUnit.MILLISECONDS)
                .readTimeout(10000L, TimeUnit.MILLISECONDS)
                .build()
            callClient.newCall(request).execute().use { response ->
                val code = response.code()
                val payload = response.body()?.string().orEmpty()
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
                return AuthByNameResult(accessToken = token, userId = userId)
            }
        } catch (e: Exception) {
            log("auth exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            null
        }
    }

    private fun executeGet(
        endpoint: String,
        token: String,
        requestLabel: String,
        log: (String) -> Unit,
        httpClient: OkHttpClient
    ): HttpResult {
        return try {
            log("$requestLabel url=$endpoint")
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("Accept", "application/json")
                .header("X-Emby-Token", token)
                .build()
            val callClient = httpClient.newBuilder()
                .connectTimeout(6000L, TimeUnit.MILLISECONDS)
                .readTimeout(10000L, TimeUnit.MILLISECONDS)
                .build()
            callClient.newCall(request).execute().use { response ->
                val code = response.code()
                val payload = response.body()?.string().orEmpty()
                log("$requestLabel -> HTTP $code, body=${previewPayload(payload)}")
                HttpResult(code = code, payload = payload)
            }
        } catch (e: Exception) {
            log("$requestLabel exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            HttpResult(code = -1, payload = "")
        }
    }

    private fun buildEmbyHttpClient(embyBase: String, cfReferenceRaw: String): OkHttpClient {
        val embyHost = try {
            URL(embyBase).host.trim()
        } catch (_: Exception) {
            ""
        }
        if (embyHost.isEmpty()) {
            return OkHttpClient()
        }
        val refHost = parseCfReferenceHost(cfReferenceRaw)
        if (refHost.isNullOrEmpty()) {
            appendRuntimeLog("cf-opt disabled reason=empty-reference-domain host=$embyHost")
            return OkHttpClient()
        }
        appendRuntimeLog("cf-opt enabled embyHost=$embyHost referenceHost=$refHost ipv6=disabled")
        return OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return resolveDnsForHost(hostname, embyHost, refHost)
                }
            })
            .connectTimeout(10000L, TimeUnit.MILLISECONDS)
            .readTimeout(20000L, TimeUnit.MILLISECONDS)
            .build()
    }

    private fun parseCfReferenceHost(raw: String): String? {
        val trimmed = raw.trim().trim('/')
        if (trimmed.isEmpty()) {
            return null
        }
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return try {
            URL(withScheme).host.trim().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCfReferenceDomain(): String {
        val input = cfRefDomainInput.text?.toString()?.trim().orEmpty()
        if (input.isNotEmpty()) {
            return input
        }
        return getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .getString(KEY_CF_REF_DOMAIN, "")
            .orEmpty()
            .trim()
    }

    private fun resolveDnsForHost(
        hostname: String,
        embyHost: String,
        referenceHost: String
    ): List<InetAddress> {
        val systemResolved = safeLookupIpv4(hostname)
        if (!hostname.equals(embyHost, ignoreCase = true)) {
            if (systemResolved.isNotEmpty()) {
                appendRuntimeLog("cf-opt bypass host=$hostname reason=non-emby ipv4Count=${systemResolved.size}")
                return systemResolved
            }
            appendRuntimeLog("cf-opt bypass-fail host=$hostname reason=non-emby-no-ipv4")
            throw UnknownHostException("No IPv4 address for host: $hostname")
        }

        val preferredFromReference = resolvePreferredCfIpv4Candidates(referenceHost)
        if (preferredFromReference.isEmpty()) {
            appendRuntimeLog("cf-opt fallback reason=reference-resolve-empty host=$hostname")
            if (systemResolved.isNotEmpty()) {
                return systemResolved
            }
            throw UnknownHostException("No IPv4 address for emby host: $hostname")
        }

        val merged = ArrayList<InetAddress>(preferredFromReference.size + systemResolved.size)
        val dedupe = HashSet<String>()
        for (addr in preferredFromReference) {
            if (dedupe.add(addr.hostAddress ?: "")) {
                merged.add(addr)
            }
        }
        for (addr in systemResolved) {
            if (dedupe.add(addr.hostAddress ?: "")) {
                merged.add(addr)
            }
        }
        val preview = merged.take(MAX_CF_IP_PREVIEW).joinToString(",") { it.hostAddress ?: "?" }
        val selected = merged.firstOrNull()?.hostAddress ?: "?"
        appendRuntimeLog(
            "cf-opt dns host=$hostname selected=$selected preferredCount=${preferredFromReference.size} systemCount=${systemResolved.size} merged=${merged.size} sample=$preview"
        )
        if (merged.isNotEmpty()) {
            return merged
        }
        if (systemResolved.isNotEmpty()) {
            appendRuntimeLog("cf-opt fallback reason=merge-empty-use-system host=$hostname systemCount=${systemResolved.size}")
            return systemResolved
        }
        throw UnknownHostException("No IPv4 address after cf-opt merge for host: $hostname")
    }

    private fun resolvePreferredCfIpv4Candidates(referenceHost: String): List<InetAddress> {
        val now = SystemClock.elapsedRealtime()
        synchronized(dnsCacheLock) {
            val cached = cfPreferredIpv4Cache[referenceHost]
            if (cached != null && now - cached.first < CF_IP_CACHE_TTL_MS) {
                val preview = cached.second.take(MAX_CF_IP_PREVIEW).joinToString(",") { it.hostAddress ?: "?" }
                appendRuntimeLog(
                    "cf-opt cache-hit referenceHost=$referenceHost ageMs=${now - cached.first} ipv4Count=${cached.second.size} sample=$preview"
                )
                return cached.second
            }
        }
        val resolved = safeLookupIpv4(referenceHost)
        val deduped = LinkedHashMap<String, InetAddress>()
        for (addr in resolved) {
            val key = addr.hostAddress ?: continue
            if (!deduped.containsKey(key)) {
                deduped[key] = addr
            }
            if (deduped.size >= MAX_CF_IP_CANDIDATES) {
                break
            }
        }
        val result = deduped.values.toList()
        val resolvedPreview = result.take(MAX_CF_IP_PREVIEW).joinToString(",") { it.hostAddress ?: "?" }
        appendRuntimeLog("cf-opt cache-refresh referenceHost=$referenceHost ipv4Count=${result.size} sample=$resolvedPreview")
        synchronized(dnsCacheLock) {
            cfPreferredIpv4Cache[referenceHost] = now to result
            if (cfPreferredIpv4Cache.size > CF_IP_CACHE_MAX_HOSTS) {
                val iterator = cfPreferredIpv4Cache.entries.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun safeLookupIpv4(host: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(host)
                .filterIsInstance<Inet4Address>()
        } catch (_: SocketTimeoutException) {
            appendRuntimeLog("cf-opt system-dns-timeout host=$host")
            emptyList()
        } catch (e: Exception) {
            appendRuntimeLog("cf-opt system-dns-fail host=$host type=${e.javaClass.simpleName}")
            emptyList()
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
        token: String,
        limit: Int = DEFAULT_HOME_QUEUE_SIZE
    ): String {
        val params = mutableListOf(
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "SortBy=Random",
            "Limit=${limit.coerceAtLeast(1)}",
            "api_key=${urlEncode(token)}"
        )
        return "$embyBase/Users/${urlEncode(userId)}/Items?${params.joinToString("&")}"
    }

    private fun buildEmbyLibraryItemsUrl(
        embyBase: String,
        userId: String,
        token: String,
        startIndex: Int,
        limit: Int
    ): String {
        val params = mutableListOf(
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "SortBy=SortName",
            "SortOrder=Ascending",
            "StartIndex=${startIndex.coerceAtLeast(0)}",
            "Limit=${limit.coerceAtLeast(1)}",
            "EnableTotalRecordCount=true",
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

    private fun parseTotalRecordCount(jsonText: String, fallback: Int): Int {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) {
            return fallback
        }
        return try {
            val root = JSONObject(trimmed)
            val total = root.optInt("TotalRecordCount", fallback)
            if (total <= 0) fallback else total
        } catch (_: Exception) {
            fallback
        }
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
        val artists = item.optJSONArray("Artists")
        val artist = if (artists != null && artists.length() > 0) {
            artists.optString(0).trim()
        } else {
            item.optString("AlbumArtist").trim()
        }
        if (trackId.isEmpty() || title.isEmpty()) {
            return null
        }
        return EmbyTrack(
            id = trackId,
            title = title,
            artist = artist,
            runtimeTicks = item.optLong("RunTimeTicks", -1L)
        )
    }

    private fun trackTitleComparator(): Comparator<EmbyTrack> {
        val collator = Collator.getInstance(Locale.CHINA).apply {
            strength = Collator.PRIMARY
        }
        return Comparator { left, right ->
            val titleCompare = collator.compare(left.title.trim(), right.title.trim())
            if (titleCompare != 0) {
                titleCompare
            } else {
                left.id.compareTo(right.id, ignoreCase = true)
            }
        }
    }

    private fun readTextWithLineBreaks(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            val sb = StringBuilder()
            var line = reader.readLine()
            while (line != null) {
                if (sb.isNotEmpty()) {
                    sb.append('\n')
                }
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

    private fun buildRecommendCacheOwnerKey(embyBase: String, username: String): String {
        return "${embyBase.lowercase(Locale.US)}|${username.trim().lowercase(Locale.US)}"
    }

    private fun currentDayKey(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private fun loadCachedSessionAuth(
        embyBase: String,
        username: String
    ): AuthByNameResult? {
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        val savedBase = prefs.getString(KEY_AUTH_BASE_URL, "").orEmpty().trim()
        val savedUser = prefs.getString(KEY_AUTH_USERNAME, "").orEmpty().trim()
        if (!savedBase.equals(embyBase, ignoreCase = true) || !savedUser.equals(username, ignoreCase = true)) {
            return null
        }
        val token = prefs.getString(KEY_AUTH_ACCESS_TOKEN, "").orEmpty().trim()
        val userId = prefs.getString(KEY_AUTH_USER_ID, "").orEmpty().trim()
        if (token.isEmpty() || userId.isEmpty()) {
            return null
        }
        return AuthByNameResult(accessToken = token, userId = userId)
    }

    private fun persistCachedSessionAuth(
        embyBase: String,
        username: String,
        auth: AuthByNameResult
    ) {
        getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTH_BASE_URL, embyBase)
            .putString(KEY_AUTH_USERNAME, username)
            .putString(KEY_AUTH_ACCESS_TOKEN, auth.accessToken)
            .putString(KEY_AUTH_USER_ID, auth.userId)
            .putLong(KEY_AUTH_SAVED_AT_MS, System.currentTimeMillis())
            .apply()
    }

    private fun clearCachedSessionAuth(embyBase: String, username: String) {
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        val savedBase = prefs.getString(KEY_AUTH_BASE_URL, "").orEmpty().trim()
        val savedUser = prefs.getString(KEY_AUTH_USERNAME, "").orEmpty().trim()
        if (!savedBase.equals(embyBase, ignoreCase = true) || !savedUser.equals(username, ignoreCase = true)) {
            return
        }
        prefs.edit()
            .remove(KEY_AUTH_BASE_URL)
            .remove(KEY_AUTH_USERNAME)
            .remove(KEY_AUTH_ACCESS_TOKEN)
            .remove(KEY_AUTH_USER_ID)
            .remove(KEY_AUTH_SAVED_AT_MS)
            .apply()
    }

    private fun loadTodayRecommendCache(ownerKey: String): List<EmbyTrack> {
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        val day = prefs.getString(KEY_RECOMMEND_CACHE_DAY, "").orEmpty()
        val owner = prefs.getString(KEY_RECOMMEND_CACHE_OWNER, "").orEmpty()
        if (day != currentDayKey() || owner != ownerKey) {
            return emptyList()
        }
        val payload = prefs.getString(KEY_RECOMMEND_CACHE_JSON, "").orEmpty()
        if (payload.isBlank()) {
            return emptyList()
        }
        return parseCachedTrackArray(payload)
    }

    private fun persistTodayRecommendCache(ownerKey: String, tracks: List<EmbyTrack>) {
        getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .edit()
            .putString(KEY_RECOMMEND_CACHE_DAY, currentDayKey())
            .putString(KEY_RECOMMEND_CACHE_OWNER, ownerKey)
            .putString(KEY_RECOMMEND_CACHE_JSON, buildCachedTrackArray(tracks))
            .apply()
    }

    private fun buildCachedTrackArray(tracks: List<EmbyTrack>): String {
        val arr = JSONArray()
        tracks.forEach { track ->
            arr.put(
                JSONObject()
                    .put("id", track.id)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("runtimeTicks", track.runtimeTicks)
            )
        }
        return arr.toString()
    }

    private fun parseCachedTrackArray(payload: String): List<EmbyTrack> {
        return try {
            val arr = JSONArray(payload)
            val out = mutableListOf<EmbyTrack>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val id = item.optString("id").trim()
                val title = item.optString("title").trim()
                if (id.isEmpty() || title.isEmpty()) {
                    continue
                }
                out.add(
                    EmbyTrack(
                        id = id,
                        title = title,
                        artist = item.optString("artist").trim(),
                        runtimeTicks = item.optLong("runtimeTicks", -1L)
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun maybeClearDownloadCacheOnColdStart() {
        if (downloadCacheClearedAtColdStart) {
            return
        }
        downloadCacheClearedAtColdStart = true
        Thread {
            clearDownloadCacheFiles("cold-start")
            runOnUiThread {
                refreshDownloadCacheInfoUi()
            }
        }.start()
    }

    private fun clearDownloadCacheFiles(reason: String): Boolean {
        val files = cacheDir?.listFiles()?.filter {
            it.isFile && it.name.startsWith("emby_") && it.name.endsWith(".cache")
        }.orEmpty()
        var allDeleted = true
        files.forEach { file ->
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            if (!deleted) {
                allDeleted = false
            }
        }
        synchronized(downloadStateLock) {
            trackDownloadStates.clear()
        }
        appendRuntimeLog("download-cache clear reason=$reason files=${files.size} success=$allDeleted")
        return allDeleted
    }

    private fun calculateDownloadCacheBytes(): Long {
        val files = cacheDir?.listFiles()?.filter {
            it.isFile && it.name.startsWith("emby_") && it.name.endsWith(".cache")
        }.orEmpty()
        var total = 0L
        files.forEach { file ->
            total += file.length().coerceAtLeast(0L)
        }
        return total
    }

    private fun refreshDownloadCacheInfoUi() {
        if (!this::downloadCacheSizeValue.isInitialized) {
            return
        }
        Thread {
            val bytes = calculateDownloadCacheBytes()
            runOnUiThread {
                if (!this::downloadCacheSizeValue.isInitialized) {
                    return@runOnUiThread
                }
                val text = Formatter.formatShortFileSize(this, bytes)
                downloadCacheSizeValue.text = getString(R.string.download_cache_size_format, text)
            }
        }.start()
    }

    private fun loadSavedCredentials() {
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        embyBaseUrlInput.setText(prefs.getString(KEY_BASE_URL, "").orEmpty())
        cfRefDomainInput.setText(prefs.getString(KEY_CF_REF_DOMAIN, "").orEmpty())
        embyUsernameInput.setText(prefs.getString(KEY_USERNAME, "").orEmpty())
        embyPasswordInput.setText(prefs.getString(KEY_PASSWORD, "").orEmpty())
        lrcApiBaseUrlInput.setText(prefs.getString(KEY_LRCAPI_BASE_URL, "").orEmpty())
    }

    private fun persistCredentials(credentials: EmbyCredentials) {
        getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, credentials.baseUrl)
            .putString(KEY_CF_REF_DOMAIN, credentials.cfReferenceDomain)
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
        val currentArtist = loadedTracks.getOrNull(currentTrackIndex)?.artist?.takeIf { it.isNotBlank() }
            ?: previewArtistOverride
            ?: getString(R.string.track_artist_placeholder)
        trackArtistValue.text = currentArtist
        playbackValue.setText(state.playbackStatusRes)
        renderHomeLyricsPreview(state.currentTrack)
        prevButton.setImageResource(android.R.drawable.ic_media_previous)
        nextButton.setImageResource(android.R.drawable.ic_media_next)
        playPauseButton.setImageResource(
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        playPauseButton.contentDescription = getString(state.playPauseLabelRes)
        playPauseButton.isEnabled = state.playPauseEnabled
        prevButton.setColorFilter(resources.getColor(R.color.white))
        nextButton.setColorFilter(resources.getColor(R.color.white))
        playPauseButton.setColorFilter(resources.getColor(R.color.white))
        prevButton.isEnabled = loadedTracks.isNotEmpty()
        nextButton.isEnabled = state.nextEnabled
        testEmbyButton.isEnabled = state.testEmbyEnabled
        testLrcApiButton.isEnabled = state.testLrcApiEnabled
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    prevButton.performClick()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    nextButton.performClick()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    playPauseButton.performClick()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    if (!uiState.isPlaying) {
                        playPauseButton.performClick()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (uiState.isPlaying) {
                        playPauseButton.performClick()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        const val LOG_TAG = "SkodaMusicEmby"
        const val PREFS_EMBY = "emby_credentials"
        const val KEY_BASE_URL = "base_url"
        const val KEY_CF_REF_DOMAIN = "cf_ref_domain"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_LRCAPI_BASE_URL = "lrcapi_base_url"
        const val KEY_AUTH_BASE_URL = "auth_base_url"
        const val KEY_AUTH_USERNAME = "auth_username"
        const val KEY_AUTH_ACCESS_TOKEN = "auth_access_token"
        const val KEY_AUTH_USER_ID = "auth_user_id"
        const val KEY_AUTH_SAVED_AT_MS = "auth_saved_at_ms"
        const val KEY_RECOMMEND_CACHE_DAY = "recommend_cache_day"
        const val KEY_RECOMMEND_CACHE_OWNER = "recommend_cache_owner"
        const val KEY_RECOMMEND_CACHE_JSON = "recommend_cache_json"
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
        const val CF_IP_CACHE_TTL_MS = 60_000L
        const val CF_IP_CACHE_MAX_HOSTS = 8
        const val MAX_CF_IP_CANDIDATES = 6
        const val MAX_CF_IP_PREVIEW = 3
        const val DOWNLOAD_WINDOW_SEC = 30L
        const val DOWNLOAD_CHUNK_BYTES = 512L * 1024L
        const val DOWNLOAD_CONTROLLER_IDLE_MS = 300L
        const val TRACK_DOWNLOAD_STATE_MAX_SIZE = 48
        const val DEFAULT_ESTIMATED_BITRATE_BPS = 192_000L
        const val STREAM_PREPARE_TIMEOUT_MS = 45_000L
        const val UI_PROGRESS_REFRESH_MS = 1_000L
        const val SEEK_BAR_MAX = 1000
        const val DOWNLOAD_HEARTBEAT_LOG_INTERVAL_MS = 5_000L
        const val MAX_RUNTIME_LOG_LINES = 800
        const val RUNTIME_LOG_PREVIEW_LINES = 2
        const val AUTO_QUEUE_REFRESH_COOLDOWN_MS = 15_000L
        const val APP_STARTUP_QUEUE_REFRESH_DELAY_MS = 400L
        const val PAGE_HOME = 0
        const val PAGE_LIBRARY = 1
        const val PAGE_SETTINGS = 2
        const val DEFAULT_HOME_QUEUE_SIZE = 20
        const val LIBRARY_PAGE_SIZE = 40
        const val LYRICS_CACHE_MAX_TRACKS = 32
        @Volatile var downloadCacheClearedAtColdStart = false
    }
}
