package com.skodamusic.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.format.Formatter
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.core.network.WifiNetworkGate
import com.skodamusic.app.data.EmbySessionCache
import com.skodamusic.app.emby.EmbyApi
import com.skodamusic.app.model.AuthByNameResult
import com.skodamusic.app.model.DeleteTrackOutcome
import com.skodamusic.app.model.DownloadControlPhase
import com.skodamusic.app.model.EmbyCredentials
import com.skodamusic.app.model.EmbyLoadResult
import com.skodamusic.app.model.EmbyTrack
import com.skodamusic.app.model.HttpResult
import com.skodamusic.app.model.ListSource
import com.skodamusic.app.model.LrcApiCredentials
import com.skodamusic.app.model.LrcApiTestResult
import com.skodamusic.app.model.LyricLine
import com.skodamusic.app.model.PlaybackFailureCategory
import com.skodamusic.app.model.TrackCodec
import com.skodamusic.app.model.TrackDownloadState
import com.skodamusic.app.model.UiState
import com.skodamusic.app.observability.PostHogTracker
import com.skodamusic.app.player.ExoPlaybackEngine
import com.skodamusic.app.player.PlaybackEngine
import com.skodamusic.app.player.PlaybackEngineCallback
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackControlBus
import com.skodamusic.app.playback.PlaybackResumeStore
import com.skodamusic.app.playback.PlaybackService
import com.skodamusic.app.playback.PlaybackStateStore
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.skodamusic.app.update.AppUpdateCoordinator
import com.skodamusic.app.update.AppUpdateManager
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class MainActivity : AppCompatActivity(), PlaybackControlBus.Controller {
    private lateinit var embyBaseUrlInput: EditText
    private lateinit var cfRefDomainInput: EditText
    private lateinit var embyUsernameInput: EditText
    private lateinit var embyPasswordInput: EditText
    private lateinit var lrcApiBaseUrlInput: EditText
    private lateinit var embyStatusValue: TextView
    private lateinit var lrcApiStatusValue: TextView
    private lateinit var downloadCacheSizeValue: TextView
    private lateinit var clearDownloadCacheButton: Button
    private lateinit var checkUpdateButton: Button
    private lateinit var updateStatusValue: TextView
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
    private lateinit var deleteCurrentTrackButton: ImageButton
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
    private var pauseRequestedRequestId: Int = -1
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
    private val downloadStateLock = Any()
    private val trackDownloadStates = LinkedHashMap<String, TrackDownloadState>()
    private val uiProgressHandler = Handler(Looper.getMainLooper())
    private var networkRecoveryRetryScheduled: Boolean = false
    private var networkRecoveryRetryTrackId: String = ""
    private val networkRecoveryRetryRunnable = object : Runnable {
        override fun run() {
            if (!networkRecoveryRetryScheduled) {
                return
            }
            val expectedTrackId = networkRecoveryRetryTrackId
            if (expectedTrackId.isEmpty()) {
                cancelNetworkRecoveryRetry()
                return
            }
            val current = loadedTracks.getOrNull(currentTrackIndex)
            if (current == null || current.id != expectedTrackId) {
                cancelNetworkRecoveryRetry()
                return
            }
            if (!hasPlaybackSession()) {
                uiProgressHandler.postDelayed(this, NETWORK_RECOVERY_RETRY_INTERVAL_MS)
                return
            }
            if (!ensureWifiConnectedForNetworkRequest(requestTag = "playback_network_retry", promptUser = false)) {
                uiProgressHandler.postDelayed(this, NETWORK_RECOVERY_RETRY_INTERVAL_MS)
                return
            }
            networkRecoveryRetryScheduled = false
            appendRuntimeLog("network retry start track=${current.title}")
            updateState {
                it.copy(
                    isPlaying = true,
                    playbackStatusRes = R.string.status_playing,
                    playPauseLabelRes = R.string.action_pause,
                    feedbackText = "动作反馈：网络恢复，重试当前歌曲"
                )
            }
            playTrackAtCurrentIndex("network_retry")
        }
    }
    private val uiProgressTicker = object : Runnable {
        override fun run() {
            refreshProgressMetrics()
            uiProgressHandler.postDelayed(this, UI_PROGRESS_REFRESH_MS)
        }
    }
    @Volatile private var lastKnownBitrateBps: Long = DEFAULT_ESTIMATED_BITRATE_BPS
    @Volatile private var downloadControllerRequestId: Int = -1
    @Volatile private var downloadControllerStop = false
    private var lastReportedServiceTrackId: String = ""
    private var lastReportedServiceTrackTitle: String = ""
    private var lastReportedServiceIsPlaying: Boolean = false
    private var lastReportedServiceHasTrack: Boolean = false
    private var lastReportedServicePositionMs: Long = -1L
    private var lastReportedServiceDurationMs: Long = -1L
    private var lastReportedServiceAtMs: Long = 0L
    private var resumeRestoreAttempted: Boolean = false
    private var lastResumePersistTrackId: String = ""
    private var lastResumePersistIndex: Int = -1
    private var lastResumePersistQueueSize: Int = -1
    private var lastResumePersistAtMs: Long = 0L
    private var downloadControllerThread: Thread? = null
    private lateinit var playbackResumeStore: PlaybackResumeStore
    private lateinit var playbackStateStore: PlaybackStateStore
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var appUpdateCoordinator: AppUpdateCoordinator
    private lateinit var backgroundExecutor: AppBackgroundExecutor
    private lateinit var wifiNetworkGate: WifiNetworkGate
    private lateinit var embySessionCache: EmbySessionCache
    private lateinit var embyApi: EmbyApi
    private var postHogSessionId: String = ""
    private var lastObservedCommandTraceAtMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        val bootStartMs = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PlaybackControlBus.attach(this)
        playbackResumeStore = PlaybackResumeStore(applicationContext)
        playbackStateStore = PlaybackStateStore(applicationContext)
        backgroundExecutor = AppBackgroundExecutor(ioThreads = 3)
        wifiNetworkGate = WifiNetworkGate(this) { message -> appendRuntimeLog(message) }
        embySessionCache = EmbySessionCache(
            context = applicationContext,
            prefsName = PREFS_EMBY,
            keyAuthBaseUrl = KEY_AUTH_BASE_URL,
            keyAuthUsername = KEY_AUTH_USERNAME,
            keyAuthAccessToken = KEY_AUTH_ACCESS_TOKEN,
            keyAuthUserId = KEY_AUTH_USER_ID,
            keyAuthSavedAtMs = KEY_AUTH_SAVED_AT_MS,
            keyRecommendCacheDay = KEY_RECOMMEND_CACHE_DAY,
            keyRecommendCacheOwner = KEY_RECOMMEND_CACHE_OWNER,
            keyRecommendCacheJson = KEY_RECOMMEND_CACHE_JSON
        )
        embyApi = EmbyApi { message -> appendRuntimeLog(message) }
        postHogSessionId = PostHogTracker.startNewSession(
            context = applicationContext,
            launchSource = "cold_start"
        )
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "boot_stage",
            properties = mapOf(
                "stage" to "on_create_start",
                "elapsed_ms" to 0L
            )
        )
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "app_start",
            properties = mapOf(
                "cold_start" to true,
                "launch_source" to "launcher",
                "session_id_local" to postHogSessionId
            )
        )

        embyBaseUrlInput = findViewById(R.id.emby_base_url_input)
        cfRefDomainInput = findViewById(R.id.cf_ref_domain_input)
        embyUsernameInput = findViewById(R.id.emby_username_input)
        embyPasswordInput = findViewById(R.id.emby_password_input)
        lrcApiBaseUrlInput = findViewById(R.id.lrcapi_base_url_input)
        embyStatusValue = findViewById(R.id.emby_status_value)
        lrcApiStatusValue = findViewById(R.id.lrcapi_status_value)
        downloadCacheSizeValue = findViewById(R.id.download_cache_size_value)
        clearDownloadCacheButton = findViewById(R.id.btn_clear_download_cache)
        checkUpdateButton = findViewById(R.id.btn_check_update)
        updateStatusValue = findViewById(R.id.update_status_value)
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
        deleteCurrentTrackButton = findViewById(R.id.btn_delete_current_track)
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
        appUpdateManager = AppUpdateManager(
            context = applicationContext,
            owner = "huise23",
            repo = "skoda-music",
            log = { msg -> appendRuntimeLog(msg) }
        )
        appUpdateCoordinator = AppUpdateCoordinator(
            activity = this,
            appUpdateManager = appUpdateManager,
            checkUpdateButton = checkUpdateButton,
            updateStatusValue = updateStatusValue,
            ensureWifiConnectedForNetworkRequest = { requestTag, promptUser ->
                ensureWifiConnectedForNetworkRequest(requestTag, promptUser)
            },
            setFeedbackText = { feedback ->
                updateState { it.copy(feedbackText = feedback) }
            },
            showToast = { resId ->
                showToast(resId)
            },
            appendRuntimeLog = { message ->
                appendRuntimeLog(message)
            },
            pauseTrackDownloadController = {
                appendRuntimeLog("update flow pause track download controller")
                stopDownloadController()
            },
            resumeTrackDownloadControllerIfNeeded = {
                resumeDownloadControllerIfNeeded()
            }
        )
        buildIdBadge.text = resolveBuildVersionTag()
        buildIdBadge.visibility = View.VISIBLE
        appendRuntimeLog("boot stage=views_bound +${SystemClock.elapsedRealtime() - bootStartMs}ms")
        captureBootStage(stage = "views_bound", bootStartMs = bootStartMs)
        appUpdateCoordinator.restoreLastUpdateState()
        configureModernHomeShell()
        switchHomeTab(showRecommend = false)
        bindLibraryPaging()
        bindNavigation()
        switchPage(PAGE_HOME)
        loadSavedCredentials()
        appendRuntimeLog("boot stage=credentials_loaded +${SystemClock.elapsedRealtime() - bootStartMs}ms")
        captureBootStage(stage = "credentials_loaded", bootStartMs = bootStartMs)

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
        window.decorView.post {
            appendRuntimeLog("boot first-frame +${SystemClock.elapsedRealtime() - bootStartMs}ms")
            captureBootStage(stage = "first_frame", bootStartMs = bootStartMs)
            restorePlaybackResumeStateIfNeeded()
            appendRuntimeLog("boot stage=resume_restore_done +${SystemClock.elapsedRealtime() - bootStartMs}ms")
            captureBootStage(stage = "resume_restore_done", bootStartMs = bootStartMs)
            rebuildTrackLists()
            refreshDownloadCacheInfoUi()
            startUiProgressTicker()
            reportPlaybackStateToService(force = true)
            appendRuntimeLog("app boot completed +${SystemClock.elapsedRealtime() - bootStartMs}ms")
            PostHogTracker.capture(
                context = applicationContext,
                eventName = "app_ready",
                properties = mapOf(
                    "startup_ms" to (SystemClock.elapsedRealtime() - bootStartMs).coerceAtLeast(0L)
                )
            )
            appUpdateCoordinator.scheduleAutoUpdateCheck(uiProgressHandler, AUTO_UPDATE_CHECK_DELAY_MS)
            uiProgressHandler.postDelayed(
                { maybeAutoRefreshQueueRecommendations("app-startup") },
                APP_STARTUP_QUEUE_REFRESH_DELAY_MS
            )
        }
    }

    override fun onStart() {
        super.onStart()
        sendPlaybackServiceIntent(PlaybackActions.ACTION_SERVICE_INIT)
        maybeRequestOverlayPermission()
        sendPlaybackServiceIntent(PlaybackActions.ACTION_APP_FOREGROUND)
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "app_foreground"
        )
        reportPlaybackStateToService(force = true)
    }

    override fun onStop() {
        maybePersistPlaybackResumeState(force = true)
        reportPlaybackStateToService(force = true)
        sendPlaybackServiceIntent(PlaybackActions.ACTION_APP_BACKGROUND)
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "app_background"
        )
        super.onStop()
    }

    override fun onDestroy() {
        appendRuntimeLog("activity destroyed")
        stopUiProgressTicker()
        cancelNetworkRecoveryRetry()
        runtimeLogDialog?.dismiss()
        PlaybackControlBus.detach(this)
        maybePersistPlaybackResumeState(force = true)
        super.onDestroy()
        playbackRequestId += 1
        stopDownloadController()
        releasePlayer()
        backgroundExecutor.shutdown()
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
            performPrevAction(source = PlaybackActions.CMD_SOURCE_UI, allowToast = false)
        }

        playPauseButton.setOnClickListener {
            performPlayPauseAction(
                forcePlay = null,
                source = PlaybackActions.CMD_SOURCE_UI,
                allowToast = true
            )
        }

        nextButton.setOnClickListener {
            performNextAction(source = PlaybackActions.CMD_SOURCE_UI, allowToast = true)
        }
        deleteCurrentTrackButton.setOnClickListener {
            val current = loadedTracks.getOrNull(currentTrackIndex)
            if (current == null) {
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
                showToast(R.string.toast_delete_source_failed)
                return@setOnClickListener
            }
            promptDeleteSourceTrack(current)
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

        checkUpdateButton.setOnClickListener {
            appUpdateCoordinator.onCheckButtonClicked()
        }
    }

    private fun performPrevAction(source: String, allowToast: Boolean): Boolean {
        appendRuntimeLog("$source prev")
        if (loadedTracks.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
            return false
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
            playTrackAtCurrentIndex(source)
            return true
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
        playTrackAtCurrentIndex(source)
        if (allowToast) {
            showToast(R.string.toast_playing)
        }
        return true
    }

    private fun performPlayPauseAction(forcePlay: Boolean?, source: String, allowToast: Boolean): Boolean {
        if (loadedTracks.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
            return false
        }
        val shouldPlay = forcePlay ?: !uiState.isPlaying
        if (shouldPlay == uiState.isPlaying) {
            return true
        }
        if (shouldPlay) {
            appendRuntimeLog("$source play/pause -> play")
            startOrResumePlayback(source)
            if (allowToast) {
                showToast(R.string.toast_playing)
            }
        } else {
            appendRuntimeLog("$source play/pause -> pause")
            pausePlayback(source)
            if (allowToast) {
                showToast(R.string.toast_paused)
            }
        }
        return true
    }

    private fun performNextAction(source: String, allowToast: Boolean): Boolean {
        appendRuntimeLog("$source next")
        if (loadedTracks.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
            return false
        }
        val nextTitle = moveToNextTrack()
        if (nextTitle == null) {
            updateState {
                it.copy(
                    feedbackText = getString(R.string.feedback_end_of_queue),
                    nextEnabled = false
                )
            }
            if (allowToast) {
                showToast(R.string.toast_end_of_queue)
            }
            rebuildTrackLists()
            return true
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
        if (allowToast) {
            showToast(R.string.toast_next)
        }
        rebuildTrackLists()
        appendRuntimeLog("$source next -> play immediately index=$currentTrackIndex")
        playTrackAtCurrentIndex(source)
        return true
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
        deleteCurrentTrackButton.isEnabled = loadedTracks.isNotEmpty()
        deleteCurrentTrackButton.alpha = if (loadedTracks.isNotEmpty()) 1f else 0.45f
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
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "library_load", promptUser = true)) {
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
        val embyClient = embyApi.buildHttpClient(base, resolveCfReferenceDomain())
        backgroundExecutor.execute {
            val endpoint = embyApi.buildLibraryItemsUrl(base, userId, token, start, limit)
            val response = embyApi.executeGet(
                endpoint = endpoint,
                token = token,
                requestLabel = "GET /Users/{id}/Items (Library Paging)",
                log = { message -> appendRuntimeLog("library $message") },
                httpClient = embyClient
            )
            val pageTracks = if (response.code in 200..299) TrackCodec.parseTrackItems(response.payload) else emptyList()
            val totalCount = TrackCodec.parseTotalRecordCount(response.payload, start + pageTracks.size)
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
                libraryTracks.sortWith(TrackCodec.titleComparator())
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
        }
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
        val recommendations: List<EmbyTrack> = loadedTracks.take(DEFAULT_HOME_QUEUE_SIZE)
        recommendations.forEachIndexed { index, track ->
            val isCurrent = index == currentTrackIndex
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = dpToPx(70)
            row.setBackgroundResource(
                if (isCurrent) R.drawable.row_recommend_active else R.drawable.row_recommend_idle
            )
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))

            val textBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val title = TextView(this)
            title.text = if (isCurrent) "\u25B6 ${track.title}" else track.title
            title.setTextColor(resources.getColor(R.color.text_primary))
            title.textSize = 17f
            title.setTypeface(null, if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)

            val artist = TextView(this)
            artist.text = track.artist.ifBlank { getString(R.string.track_artist_server) }
            artist.setTextColor(resources.getColor(R.color.text_secondary))
            artist.textSize = 14f

            textBlock.addView(title)
            textBlock.addView(artist)
            row.addView(textBlock)
            row.addView(buildDeleteButton(track))
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
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "lyrics_fetch", promptUser = false)) {
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
        backgroundExecutor.execute {
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
        }
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

    private fun fetchLyricsLinesFromLrcApi(baseUrl: String, trackName: String, artistName: String): List<LyricLine> {
        val endpoint = buildLrcApiLyricsUrl(baseUrl, trackName, artistName)
        val lrcApiClient = embyApi.buildHttpClient(baseUrl, resolveCfReferenceDomain())
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
                appendRuntimeLog("lyrics fetch response code=$code body=${embyApi.previewPayload(payload)}")
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
                AbsoluteSizeSpan(if (isActive) 20 else 19, true),
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
            val textBlock = LinearLayout(this)
            val isCurrent = highlightCurrent && index == currentTrackIndex
            val title = TextView(this)
            val artist = TextView(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = dpToPx(70)
            row.setBackgroundResource(
                if (isCurrent) R.drawable.row_recommend_active else R.drawable.row_recommend_idle
            )
            row.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
            textBlock.orientation = LinearLayout.VERTICAL
            textBlock.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

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

            textBlock.addView(title)
            textBlock.addView(artist)
            row.addView(textBlock)
            if (source == ListSource.LIBRARY) {
                row.addView(buildDeleteButton(track))
            }
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

    private fun buildDeleteButton(track: EmbyTrack): ImageButton {
        return ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_delete)
            setBackgroundResource(R.drawable.button_nav_icon_inactive)
            setColorFilter(resources.getColor(R.color.text_secondary))
            contentDescription = getString(R.string.action_delete_source)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                promptDeleteSourceTrack(track)
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                leftMargin = dpToPx(10)
            }
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
        playTrackAtCurrentIndex(if (source == ListSource.QUEUE) "queue_tap" else "library_tap")
    }

    private fun promptDeleteSourceTrack(track: EmbyTrack) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_source_title)
            .setMessage(getString(R.string.dialog_delete_source_message_final, track.title))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                deleteSourceTrack(track)
            }
            .show()
    }

    private fun deleteSourceTrack(track: EmbyTrack) {
        val base = embySessionBaseUrl
        val token = embyAccessToken
        if (base.isNullOrBlank() || token.isNullOrBlank()) {
            updateState {
                it.copy(feedbackText = getString(R.string.feedback_delete_source_missing_session))
            }
            showToast(R.string.toast_delete_source_failed)
            return
        }
        val wasPlaying = uiState.isPlaying
        appendRuntimeLog("delete source start track=${track.title} id=${shortId(track.id)}")
        val embyClient = embyApi.buildHttpClient(base, resolveCfReferenceDomain())
        backgroundExecutor.execute {
            val code = requestDeleteTrackFromEmby(base, token, track.id, embyClient)
            runOnUiThread {
                if (code !in 200..299 && code != 404) {
                    appendRuntimeLog("delete source failed code=$code track=${track.title} id=${shortId(track.id)}")
                    updateState {
                        it.copy(feedbackText = getString(R.string.feedback_delete_source_failed, code))
                    }
                    showToast(R.string.toast_delete_source_failed)
                    return@runOnUiThread
                }
                val outcome = applyDeletedTrackLocally(track.id)
                if (!outcome.removedAnything) {
                    appendRuntimeLog("delete source local-miss track=${track.title} id=${shortId(track.id)}")
                    updateState {
                        it.copy(
                            feedbackText = getString(
                                R.string.feedback_delete_source_success,
                                track.title
                            ),
                            nextEnabled = hasNextTrack()
                        )
                    }
                    showToast(R.string.toast_delete_source_success)
                    return@runOnUiThread
                }
                if (outcome.removedCurrentTrack && !outcome.queueEmptyAfterDelete && wasPlaying) {
                    appendRuntimeLog("delete source removed-current autoplay-next index=$currentTrackIndex")
                    updateState {
                        it.copy(
                            feedbackText = getString(
                                R.string.feedback_delete_source_skip_next,
                                loadedTracks[currentTrackIndex].title
                            ),
                            nextEnabled = hasNextTrack()
                        )
                    }
                    playTrackAtCurrentIndex()
                } else if (outcome.queueEmptyAfterDelete) {
                    updateState {
                        it.copy(
                            feedbackText = getString(R.string.feedback_delete_source_queue_empty),
                            nextEnabled = false
                        )
                    }
                } else if (outcome.removedCurrentTrack) {
                    updateState {
                        it.copy(
                            feedbackText = getString(
                                R.string.feedback_delete_source_current_selected,
                                loadedTracks[currentTrackIndex].title
                            ),
                            nextEnabled = hasNextTrack()
                        )
                    }
                } else {
                    updateState {
                        it.copy(
                            feedbackText = getString(
                                R.string.feedback_delete_source_success,
                                track.title
                            ),
                            nextEnabled = hasNextTrack()
                        )
                    }
                }
                appendRuntimeLog("delete source success code=$code track=${track.title} id=${shortId(track.id)}")
                showToast(R.string.toast_delete_source_success)
            }
        }
    }

    private fun requestDeleteTrackFromEmby(
        embyBase: String,
        token: String,
        trackId: String,
        httpClient: OkHttpClient
    ): Int {
        val endpoint = "$embyBase/Items/${urlEncode(trackId)}?api_key=${urlEncode(token)}"
        return try {
            val request = Request.Builder()
                .url(endpoint)
                .delete()
                .header("Accept", "application/json")
                .header("X-Emby-Token", token)
                .build()
            val callClient = httpClient.newBuilder()
                .connectTimeout(6000L, TimeUnit.MILLISECONDS)
                .readTimeout(10000L, TimeUnit.MILLISECONDS)
                .build()
            callClient.newCall(request).execute().use { response ->
                response.code()
            }
        } catch (e: Exception) {
            appendRuntimeLog("delete source exception type=${e.javaClass.simpleName} msg=${e.message}")
            -1
        }
    }

    private fun applyDeletedTrackLocally(trackId: String): DeleteTrackOutcome {
        val queueBefore = loadedTracks
        val removedIndices = mutableListOf<Int>()
        queueBefore.forEachIndexed { index, item ->
            if (item.id == trackId) {
                removedIndices.add(index)
            }
        }
        val removedFromQueue = removedIndices.isNotEmpty()
        val removedCurrentTrack = removedIndices.contains(currentTrackIndex)
        if (removedFromQueue) {
            val removedBeforeCurrent = removedIndices.count { it < currentTrackIndex }
            loadedTracks = queueBefore.filterNot { it.id == trackId }
            currentTrackIndex = when {
                loadedTracks.isEmpty() -> 0
                removedCurrentTrack -> currentTrackIndex.coerceAtMost(loadedTracks.lastIndex)
                else -> (currentTrackIndex - removedBeforeCurrent).coerceIn(0, loadedTracks.lastIndex)
            }
        }
        val removedFromLibrary = libraryTracks.removeAll { it.id == trackId }
        removeTrackFromTodayRecommendCache(trackId)
        removeTrackDownloadArtifacts(trackId)

        val removedAnything = removedFromQueue || removedFromLibrary
        if (!removedAnything) {
            return DeleteTrackOutcome(
                removedFromQueue = false,
                removedCurrentTrack = false,
                queueEmptyAfterDelete = loadedTracks.isEmpty(),
                removedAnything = false
            )
        }
        if (loadedTracks.isEmpty()) {
            playbackRequestId += 1
            stopDownloadController()
            releasePlayer()
            previewArtistOverride = null
            updateState {
                it.copy(
                    currentTrack = getString(R.string.track_not_loaded),
                    isPlaying = false,
                    playbackStatusRes = R.string.status_paused,
                    playPauseLabelRes = R.string.action_play,
                    playPauseEnabled = false,
                    nextEnabled = false
                )
            }
            rebuildTrackLists()
            return DeleteTrackOutcome(
                removedFromQueue = removedFromQueue,
                removedCurrentTrack = removedCurrentTrack,
                queueEmptyAfterDelete = true,
                removedAnything = true
            )
        }
        currentTrackIndex = currentTrackIndex.coerceIn(0, loadedTracks.lastIndex)
        previewArtistOverride = loadedTracks[currentTrackIndex].artist.ifBlank { null }
        syncNativeQueueToCurrentIndex()
        updateState {
            it.copy(
                currentTrack = loadedTracks[currentTrackIndex].title,
                playPauseEnabled = true,
                nextEnabled = hasNextTrack()
            )
        }
        rebuildTrackLists()
        return DeleteTrackOutcome(
            removedFromQueue = removedFromQueue,
            removedCurrentTrack = removedCurrentTrack,
            queueEmptyAfterDelete = false,
            removedAnything = true
        )
    }

    private fun hasNextTrack(): Boolean {
        if (loadedTracks.isEmpty()) {
            return false
        }
        return currentTrackIndex < loadedTracks.lastIndex
    }

    private fun autoCleanupDownloadCacheAfterTrackCompletion(completedTrackId: String) {
        if (completedTrackId.isBlank()) {
            return
        }
        val keepTrackId = loadedTracks.getOrNull(currentTrackIndex + 1)?.id
            ?.takeIf { it.isNotBlank() && it != completedTrackId }
        val cleared = clearDownloadCacheFiles(
            reason = "track-complete",
            keepTrackId = keepTrackId
        )
        appendRuntimeLog(
            "download-cache auto-cleanup completedTrackId=$completedTrackId keepTrackId=${keepTrackId.orEmpty()} success=$cleared"
        )
        refreshDownloadCacheInfoUi()
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
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "queue_tail_refill", promptUser = false)) {
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
        val embyClient = embyApi.buildHttpClient(base, credentials.cfReferenceDomain)
        backgroundExecutor.execute {
            var response = embyApi.executeGet(
                endpoint = embyApi.buildRecommendedItemsUrl(base, userId, token, DEFAULT_HOME_QUEUE_SIZE),
                token = token,
                requestLabel = "GET /Users/{id}/Items (Tail Refill/${DEFAULT_HOME_QUEUE_SIZE})",
                log = { message -> appendRuntimeLog("queue-refill $message") },
                httpClient = embyClient
            )
            if (response.code == 401 && credentials.username.isNotEmpty() && credentials.password.isNotEmpty()) {
                val refreshed = embyApi.authenticateByName(
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
                    embySessionCache.persistCachedSessionAuth(base, credentials.username, refreshed)
                    response = embyApi.executeGet(
                        endpoint = embyApi.buildRecommendedItemsUrl(base, userId, token, DEFAULT_HOME_QUEUE_SIZE),
                        token = token,
                        requestLabel = "GET /Users/{id}/Items (Tail Refill Retry/${DEFAULT_HOME_QUEUE_SIZE})",
                        log = { message -> appendRuntimeLog("queue-refill $message") },
                        httpClient = embyClient
                    )
                }
            }
            val fetched = if (response.code in 200..299) TrackCodec.parseTrackItems(response.payload) else emptyList()
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
        }
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
            maybeSyncServiceCommandTrace()
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
        maybePersistPlaybackResumeState(positionMs = positionMs)
        maybeSyncServiceCommandTrace()

    }

    private fun maybeSyncServiceCommandTrace() {
        if (!this::playbackStateStore.isInitialized) {
            return
        }
        val trace = playbackStateStore.readCommandTrace()
        if (trace.updatedAtMs <= 0L || trace.updatedAtMs == lastObservedCommandTraceAtMs) {
            return
        }
        lastObservedCommandTraceAtMs = trace.updatedAtMs
        appendRuntimeLog(
            "service cmd result action=${trace.action} source=${trace.source} handled=${trace.handled} detail=${trace.detail}"
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

    private fun mapPlaybackErrorCode(code: Int, category: PlaybackFailureCategory): String {
        return when (category) {
            PlaybackFailureCategory.DECODER_FAILURE -> {
                if (code == 4003) "CODEC_INIT_TIMEOUT" else "DECODER_FAILURE"
            }
            PlaybackFailureCategory.NETWORK_FAILURE -> {
                if (code > 0) "NETWORK_$code" else "NETWORK_FAILURE"
            }
            PlaybackFailureCategory.SOURCE_FAILURE -> {
                if (code > 0) "SOURCE_$code" else "SOURCE_FAILURE"
            }
            PlaybackFailureCategory.UNKNOWN -> {
                if (code > 0) "UNKNOWN_$code" else "UNKNOWN_FAILURE"
            }
        }
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
        if (category == PlaybackFailureCategory.NETWORK_FAILURE) {
            runOnUiThread {
                if (requestId != playbackRequestId) {
                    return@runOnUiThread
                }
                val activeTrack = loadedTracks.getOrNull(currentTrackIndex)
                if (activeTrack == null) {
                    return@runOnUiThread
                }
                cancelNetworkRecoveryRetry()
                scheduleNetworkRecoveryRetry(activeTrack.id, source)
            }
            return
        }
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

    private fun scheduleNetworkRecoveryRetry(trackId: String, source: String) {
        if (trackId.isBlank()) {
            return
        }
        val track = loadedTracks.getOrNull(currentTrackIndex)
        val trackTitle = track?.title ?: getString(R.string.track_not_loaded)
        if (!networkRecoveryRetryScheduled || networkRecoveryRetryTrackId != trackId) {
            appendRuntimeLog("network retry schedule source=$source track=$trackTitle")
        }
        networkRecoveryRetryScheduled = true
        networkRecoveryRetryTrackId = trackId
        updateState {
            it.copy(
                isPlaying = false,
                playbackStatusRes = R.string.status_paused,
                playPauseLabelRes = R.string.action_play,
                feedbackText = "动作反馈：网络中断，等待恢复后重试当前歌曲"
            )
        }
        uiProgressHandler.removeCallbacks(networkRecoveryRetryRunnable)
        uiProgressHandler.postDelayed(networkRecoveryRetryRunnable, NETWORK_RECOVERY_RETRY_INTERVAL_MS)
    }

    private fun cancelNetworkRecoveryRetry() {
        networkRecoveryRetryScheduled = false
        networkRecoveryRetryTrackId = ""
        uiProgressHandler.removeCallbacks(networkRecoveryRetryRunnable)
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
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "queue_auto_refresh", promptUser = true)) {
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
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "emby_load", promptUser = true)) {
            onFinished?.invoke()
            updateState {
                it.copy(
                    testEmbyEnabled = true,
                    embyStatusText = getString(R.string.emby_status_failed),
                    feedbackText = getString(R.string.feedback_network_wifi_required)
                )
            }
            showToast(R.string.toast_network_wifi_required)
            return
        }
        appendRuntimeLog("emby load start base=${credentials.baseUrl}")
        updateState {
            it.copy(
                testEmbyEnabled = false,
                embyStatusText = getString(R.string.emby_status_testing),
                feedbackText = getString(R.string.feedback_emby_testing)
            )
        }

        backgroundExecutor.execute {
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
                        if (AUTO_PLAY_FIRST_TRACK_ON_EMBY_LOAD && loadedTracks.isNotEmpty()) {
                            appendRuntimeLog("emby load success autoplay-first-track")
                            playTrackAtCurrentIndex("autoplay_first")
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
        }
    }

    private fun requestLrcApiConnectionTest(credentials: LrcApiCredentials) {
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "lrcapi_test", promptUser = true)) {
            updateState {
                it.copy(
                    testLrcApiEnabled = true,
                    lrcApiStatusText = getString(R.string.lrcapi_status_failed),
                    feedbackText = getString(R.string.feedback_network_wifi_required)
                )
            }
            showToast(R.string.toast_network_wifi_required)
            return
        }
        updateState {
            it.copy(
                testLrcApiEnabled = false,
                lrcApiStatusText = getString(R.string.lrcapi_status_testing),
                feedbackText = getString(R.string.feedback_lrcapi_testing)
            )
        }
        backgroundExecutor.execute {
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
        }
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
        val lrcApiClient = embyApi.buildHttpClient(normalized, resolveCfReferenceDomain())
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

    private fun startOrResumePlayback(source: String) {
        cancelNetworkRecoveryRetry()
        val existing = playbackEngine
        if (existing != null) {
            if (existing.play()) {
                pauseRequestedRequestId = -1
                val trackId = loadedTracks.getOrNull(currentTrackIndex)?.id.orEmpty()
                PostHogTracker.capture(
                    context = applicationContext,
                    eventName = "resume",
                    properties = mapOf(
                        "track_id" to trackId,
                        "source" to source
                    )
                )
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
        playTrackAtCurrentIndex(source)
    }

    private fun pausePlayback(source: String) {
        cancelNetworkRecoveryRetry()
        pauseRequestedRequestId = playbackRequestId
        stopDownloadController()
        playbackEngine?.pause()
        val trackId = loadedTracks.getOrNull(currentTrackIndex)?.id.orEmpty()
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "pause",
            properties = mapOf(
                "track_id" to trackId,
                "source" to source
            )
        )
        updateState {
            it.copy(
                isPlaying = false,
                playbackStatusRes = R.string.status_paused,
                playPauseLabelRes = R.string.action_play,
                feedbackText = getString(R.string.feedback_play_pressed)
            )
        }
    }

    private fun playTrackAtCurrentIndex(source: String = "system") {
        cancelNetworkRecoveryRetry()
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
            PostHogTracker.capture(
                context = applicationContext,
                eventName = "playback_failed",
                properties = mapOf(
                    "track_id" to loadedTracks.getOrNull(currentTrackIndex)?.id.orEmpty(),
                    "stage" to "session_check",
                    "error_code" to "SESSION_UNAVAILABLE",
                    "source" to source
                ),
                priority = PostHogTracker.Priority.HIGH
            )
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
        val downloadUrl = embyApi.buildDownloadUrl(base, track.id, token)
        val embyClient = embyApi.buildHttpClient(base, resolveCfReferenceDomain())
        val requestId = ++playbackRequestId
        pauseRequestedRequestId = -1
        val streamPrepared = AtomicBoolean(false)
        val fallbackTriggered = AtomicBoolean(false)
        val prepareStartMs = SystemClock.elapsedRealtime()
        val wasPlayingAtStart = uiState.isPlaying
        var bufferingStartMs = 0L
        var prepareTimeoutTriggered = false
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "play_start",
            properties = mapOf(
                "track_id" to track.id,
                "position_ms" to 0L,
                "is_playing_before_start" to wasPlayingAtStart,
                "from_source" to source
            )
        )
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
                        if (pauseRequestedRequestId == requestId) {
                            appendRuntimeLog("play prepared requestId=$requestId skipped reason=pause-requested")
                            return
                        }
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "play_success",
                            properties = mapOf(
                                "track_id" to track.id,
                                "prepare_ms" to (SystemClock.elapsedRealtime() - prepareStartMs).coerceAtLeast(0L),
                                "decoder" to "exo_download"
                            )
                        )
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
                        val completedTrackId = track.id
                        runOnUiThread {
                            autoCleanupDownloadCacheAfterTrackCompletion(completedTrackId)
                            val nextTitle = moveToNextTrack()
                            if (nextTitle != null) {
                                updateState { s ->
                                    s.copy(
                                        currentTrack = nextTitle,
                                        nextEnabled = hasNextTrack()
                                    )
                                }
                                rebuildTrackLists()
                                playTrackAtCurrentIndex("auto_next")
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
                        if (pauseRequestedRequestId == requestId) {
                            appendRuntimeLog("play error ignored requestId=$requestId reason=pause-requested code=$code")
                            return
                        }
                        val category = categorizePlaybackError(code, detail)
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "playback_failed",
                            properties = mapOf(
                                "track_id" to track.id,
                                "stage" to "download_prepare",
                                "error_code" to mapPlaybackErrorCode(code, category),
                                "error_summary" to detail.take(160)
                            ),
                            priority = PostHogTracker.Priority.HIGH
                        )
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
                        bufferingStartMs = SystemClock.elapsedRealtime()
                        appendRuntimeLog("play buffering start requestId=$requestId track=${track.title}")
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "playback_buffering_start",
                            properties = mapOf(
                                "track_id" to track.id,
                                "source" to source
                            )
                        )
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
                        val bufferingMs = if (bufferingStartMs > 0L) {
                            (SystemClock.elapsedRealtime() - bufferingStartMs).coerceAtLeast(0L)
                        } else {
                            -1L
                        }
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "playback_buffering_end",
                            properties = mapOf(
                                "track_id" to track.id,
                                "source" to source,
                                "buffering_ms" to bufferingMs
                            )
                        )
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
                if (pauseRequestedRequestId == requestId) {
                    return@Thread
                }
                runOnUiThread {
                    if (requestId != playbackRequestId || streamPrepared.get()) {
                        return@runOnUiThread
                    }
                    if (pauseRequestedRequestId == requestId) {
                        return@runOnUiThread
                    }
                    val active = playbackEngine
                    if (active != null && active.isPlaying()) {
                        streamPrepared.set(true)
                        appendRuntimeLog("play timeout guard ignored requestId=$requestId reason=already-playing")
                        return@runOnUiThread
                    }
                    appendRuntimeLog("play timeout guard confirmed requestId=$requestId")
                    prepareTimeoutTriggered = true
                    PostHogTracker.capture(
                        context = applicationContext,
                        eventName = "playback_stall_detected",
                        properties = mapOf(
                            "track_id" to track.id,
                            "stage" to "download_prepare",
                            "error_code" to "PREPARE_TIMEOUT",
                            "source" to source,
                            "timeout_ms" to STREAM_PREPARE_TIMEOUT_MS
                        ),
                        priority = PostHogTracker.Priority.HIGH
                    )
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

    private fun resumeDownloadControllerIfNeeded() {
        if (!uiState.isPlaying) {
            return
        }
        if (downloadControllerThread != null) {
            return
        }
        val base = embySessionBaseUrl ?: return
        val token = embyAccessToken ?: return
        val current = loadedTracks.getOrNull(currentTrackIndex) ?: return
        val next = loadedTracks.getOrNull(currentTrackIndex + 1)
        val requestId = playbackRequestId
        if (requestId <= 0) {
            return
        }
        appendRuntimeLog("resume track download controller requestId=$requestId track=${current.title}")
        startDownloadController(
            requestId = requestId,
            embyBase = base,
            token = token,
            currentTrack = current,
            nextTrack = next,
            httpClient = embyApi.buildHttpClient(base, resolveCfReferenceDomain())
        )
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
            .url(embyApi.buildDownloadUrl(embyBase, track.id, token))
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
        backgroundExecutor.execute {
            val cacheFile = downloadTrackToCache(track, embyBase, token, httpClient)
            runOnUiThread {
                if (requestId != playbackRequestId) {
                    return@runOnUiThread
                }
                if (cacheFile == null || !cacheFile.exists()) {
                    appendRuntimeLog("cache fallback failed requestId=$requestId track=${track.title}")
                    PostHogTracker.capture(
                        context = applicationContext,
                        eventName = "playback_failed",
                        properties = mapOf(
                            "track_id" to track.id,
                            "stage" to "cache_download",
                            "error_code" to "CACHE_DOWNLOAD_FAILED",
                            "error_summary" to "cache file missing after fallback download"
                        ),
                        priority = PostHogTracker.Priority.HIGH
                    )
                    handlePlaybackErrorAutoSkip(
                        requestId = requestId,
                        code = -1,
                        detail = "cache fallback download failed",
                        source = "cache_download"
                    )
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
                        if (pauseRequestedRequestId == requestId) {
                            appendRuntimeLog("cache playback prepared requestId=$requestId skipped reason=pause-requested")
                            return
                        }
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "play_success",
                            properties = mapOf(
                                "track_id" to track.id,
                                "prepare_ms" to -1L,
                                "decoder" to "exo_cache"
                            )
                        )
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
                        val completedTrackId = track.id
                        runOnUiThread {
                            autoCleanupDownloadCacheAfterTrackCompletion(completedTrackId)
                            val nextTitle = moveToNextTrack()
                            if (nextTitle != null) {
                                updateState { s ->
                                    s.copy(
                                        currentTrack = nextTitle,
                                        nextEnabled = hasNextTrack()
                                    )
                                }
                                rebuildTrackLists()
                                playTrackAtCurrentIndex("auto_next")
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
                                if (pauseRequestedRequestId == requestId) {
                                    appendRuntimeLog("cache playback error ignored requestId=$requestId reason=pause-requested code=$code")
                                    return
                                }
                                val category = categorizePlaybackError(code, detail)
                                PostHogTracker.capture(
                                    context = applicationContext,
                                    eventName = "playback_failed",
                                    properties = mapOf(
                                        "track_id" to track.id,
                                        "stage" to "cache_prepare",
                                        "error_code" to mapPlaybackErrorCode(code, category),
                                        "error_summary" to detail.take(160)
                                    ),
                                    priority = PostHogTracker.Priority.HIGH
                                )
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
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "playback_buffering_start",
                            properties = mapOf(
                                "track_id" to track.id,
                                "source" to "cache_fallback"
                            )
                        )
                    }

                    override fun onBufferingEnd() {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        appendRuntimeLog("cache buffering end requestId=$requestId")
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "playback_buffering_end",
                            properties = mapOf(
                                "track_id" to track.id,
                                "source" to "cache_fallback"
                            )
                        )
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
                    PostHogTracker.capture(
                        context = applicationContext,
                        eventName = "playback_failed",
                        properties = mapOf(
                            "track_id" to track.id,
                            "stage" to "cache_prepare",
                            "error_code" to "CACHE_PLAYBACK_SETUP_EXCEPTION",
                            "error_summary" to e.javaClass.simpleName
                        ),
                        priority = PostHogTracker.Priority.HIGH
                    )
                    handlePlaybackErrorAutoSkip(
                        requestId = requestId,
                        code = -1,
                        detail = "cache playback exception ${e.javaClass.simpleName}",
                        source = "cache"
                    )
                }
            }
        }
    }

    private fun downloadTrackToCache(
        track: EmbyTrack,
        embyBase: String,
        token: String,
        httpClient: OkHttpClient
    ): File? {
        val candidateUrls = listOf(
            embyApi.buildDownloadUrl(embyBase, track.id, token)
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

    private fun ensurePlaybackEngine(@Suppress("UNUSED_PARAMETER") httpClient: OkHttpClient): PlaybackEngine {
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
        val created = ExoPlaybackEngine(
            context = this,
            dataSourceFactory = defaultDataSourceFactory,
            minBufferMs = LOAD_CONTROL_MIN_BUFFER_MS,
            maxBufferMs = LOAD_CONTROL_MAX_BUFFER_MS,
            playbackBufferMs = LOAD_CONTROL_PLAYBACK_MS,
            rebufferMs = LOAD_CONTROL_REBUFFER_MS
        )
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
            val embyBase = embyApi.normalizeEmbyBase(credentials.baseUrl)
            val embyClient = embyApi.buildHttpClient(embyBase, credentials.cfReferenceDomain)
            val ownerKey = TrackCodec.buildRecommendCacheOwnerKey(embyBase, credentials.username)
            logger("base=$embyBase")
            logger("force-refresh=$forceRefreshRecommendations")

            var auth: AuthByNameResult? = embySessionCache.loadCachedSessionAuth(
                embyBase = embyBase,
                username = credentials.username
            )
            if (auth != null) {
                logger("auth-mode=cached-token user=${shortId(auth.userId)}")
            } else {
                logger("auth-mode=username-password")
            }

            if (!forceRefreshRecommendations && auth != null) {
                val cachedTracks = embySessionCache.loadTodayRecommendCache(ownerKey)
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
                auth = embyApi.authenticateByName(
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
                val recommendedEndpoint = embyApi.buildRecommendedItemsUrl(
                    embyBase = embyBase,
                    userId = activeAuth.userId,
                    token = activeAuth.accessToken,
                    limit = DEFAULT_HOME_QUEUE_SIZE
                )
                val recommendedResponse = embyApi.executeGet(
                    endpoint = recommendedEndpoint,
                    token = activeAuth.accessToken,
                    requestLabel = "GET /Users/{id}/Items (Random/${DEFAULT_HOME_QUEUE_SIZE})",
                    log = logger,
                    httpClient = embyClient
                )

                if (recommendedResponse.code == 401 && !retriedAfterUnauthorized) {
                    logger("recommend unauthorized -> re-auth")
                    embySessionCache.clearCachedSessionAuth(embyBase, credentials.username)
                    auth = embyApi.authenticateByName(
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
                val tracks = TrackCodec.parseTrackItems(recommendedResponse.payload)

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

                embySessionCache.persistCachedSessionAuth(
                    embyBase = embyBase,
                    username = credentials.username,
                    auth = activeAuth
                )
                embySessionCache.persistTodayRecommendCache(ownerKey, tracks)

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

    private fun shortId(value: String): String {
        return if (value.length <= 8) value else value.take(4) + "..." + value.takeLast(4)
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun isHttpUrl(value: String): Boolean {
        val lower = value.lowercase(Locale.US)
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun restorePlaybackResumeStateIfNeeded() {
        if (!ENABLE_RESUME_STATE_RESTORE) {
            if (playbackResumeStore.read() != null) {
                appendRuntimeLog("resume restore disabled: clear snapshot")
                clearPersistedPlaybackResumeState()
            }
            resumeRestoreAttempted = true
            return
        }
        if (resumeRestoreAttempted) {
            return
        }
        resumeRestoreAttempted = true
        val snapshot = playbackResumeStore.read() ?: return
        val ageMs = (System.currentTimeMillis() - snapshot.savedAtMs).coerceAtLeast(0L)
        appendRuntimeLog(
            "resume restore snapshot-hit ageMs=$ageMs queueLen=${snapshot.queueJson.length} index=${snapshot.index}"
        )
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "resume_restore_attempt",
            properties = mapOf(
                "has_snapshot" to true,
                "snapshot_age_ms" to ageMs
            )
        )
        val payload = snapshot.queueJson
        if (payload.isBlank()) {
            appendRuntimeLog("resume restore snapshot-empty")
            return
        }
        val restoredTracks = TrackCodec.parseCachedTrackArray(payload)
        if (restoredTracks.isEmpty()) {
            appendRuntimeLog("resume restore snapshot-parse-empty")
            PostHogTracker.capture(
                context = applicationContext,
                eventName = "resume_restore_failed",
                properties = mapOf(
                    "stage" to "parse_snapshot",
                    "error_code" to "RESUME_SNAPSHOT_INVALID"
                ),
                priority = PostHogTracker.Priority.HIGH
            )
            clearPersistedPlaybackResumeState()
            return
        }
        loadedTracks = restoredTracks
        currentTrackIndex = snapshot.index.coerceIn(0, loadedTracks.lastIndex)
        previewArtistOverride = loadedTracks[currentTrackIndex].artist.ifBlank { null }
        syncNativeQueueToCurrentIndex()
        rebuildTrackLists()

        val sessionReady = restorePlaybackSessionForResume(snapshot)

        val feedback = if (sessionReady) {
            "动作反馈：已恢复上次播放，自动续播当前索引"
        } else {
            "动作反馈：已恢复上次播放列表，正在尝试自动鉴权续播"
        }
        updateState {
            it.copy(
                currentTrack = loadedTracks[currentTrackIndex].title,
                isPlaying = false,
                playbackStatusRes = R.string.status_paused,
                playPauseLabelRes = R.string.action_play,
                playPauseEnabled = true,
                nextEnabled = hasNextTrack(),
                feedbackText = feedback
            )
        }
        maybePersistPlaybackResumeState(force = true)
        maybeStartResumePlayback("resume-restore")
    }

    private fun restorePlaybackSessionForResume(snapshot: PlaybackResumeStore.Snapshot): Boolean {
        val persistedBase = snapshot.baseUrl.trim()
        val persistedUser = snapshot.username.trim()
        val inputBase = embyBaseUrlInput.text?.toString()?.trim().orEmpty()
        val inputUser = embyUsernameInput.text?.toString()?.trim().orEmpty()
        val embyBase = when {
            persistedBase.isNotEmpty() -> persistedBase
            isHttpUrl(inputBase) -> embyApi.normalizeEmbyBase(inputBase)
            else -> ""
        }
        val username = if (persistedUser.isNotEmpty()) persistedUser else inputUser
        if (embyBase.isEmpty() || username.isEmpty()) {
            return false
        }
        val auth = embySessionCache.loadCachedSessionAuth(embyBase = embyBase, username = username)
        if (auth != null) {
            embySessionBaseUrl = embyBase
            embySessionUserId = auth.userId
            embyAccessToken = auth.accessToken
            appendRuntimeLog("resume restore session-hit user=${shortId(auth.userId)}")
            return true
        } else {
            appendRuntimeLog("resume restore session-miss base=$embyBase")
            return false
        }
    }

    private fun maybeStartResumePlayback(trigger: String) {
        if (loadedTracks.isEmpty()) {
            return
        }
        if (uiState.isPlaying) {
            return
        }
        if (hasPlaybackSession()) {
            appendRuntimeLog("resume autoplay start trigger=$trigger track=${loadedTracks[currentTrackIndex].title}")
            PostHogTracker.capture(
                context = applicationContext,
                eventName = "resume_restore_success",
                properties = mapOf(
                    "track_id" to loadedTracks[currentTrackIndex].id,
                    "trigger" to trigger
                )
            )
            updateState {
                it.copy(
                    isPlaying = true,
                    playbackStatusRes = R.string.status_playing,
                    playPauseLabelRes = R.string.action_pause,
                    feedbackText = "动作反馈：恢复上次播放中"
                )
            }
            playTrackAtCurrentIndex("resume_autoplay")
            return
        }
        val credentials = EmbyCredentials(
            baseUrl = embyBaseUrlInput.text.toString().trim(),
            username = embyUsernameInput.text.toString().trim(),
            password = embyPasswordInput.text.toString().trim(),
            cfReferenceDomain = resolveCfReferenceDomain()
        )
        if (credentials.baseUrl.isEmpty() || credentials.username.isEmpty() || credentials.password.isEmpty()) {
            appendRuntimeLog("resume autoplay skip trigger=$trigger reason=missing-credentials")
            PostHogTracker.capture(
                context = applicationContext,
                eventName = "resume_restore_failed",
                properties = mapOf(
                    "stage" to "session_restore",
                    "error_code" to "RESUME_SESSION_MISSING"
                ),
                priority = PostHogTracker.Priority.HIGH
            )
            return
        }
        appendRuntimeLog("resume autoplay auth-retry trigger=$trigger")
        requestTracksFromEmby(
            credentials = credentials,
            onFinished = { maybeStartResumePlayback("$trigger-auth-retry") },
            forceRefreshRecommendations = false
        )
    }

    private fun maybePersistPlaybackResumeState(positionMs: Long = -1L, force: Boolean = false) {
        if (!ENABLE_RESUME_STATE_RESTORE) {
            return
        }
        if (!resumeRestoreAttempted) {
            return
        }
        if (loadedTracks.isEmpty()) {
            appendRuntimeLog("resume persist skip reason=empty-queue")
            clearPersistedPlaybackResumeState()
            return
        }
        val safeIndex = currentTrackIndex.coerceIn(0, loadedTracks.lastIndex)
        val activeTrack = loadedTracks[safeIndex]
        val queueSize = loadedTracks.size
        val now = System.currentTimeMillis()
        val stateChanged = force ||
            activeTrack.id != lastResumePersistTrackId ||
            safeIndex != lastResumePersistIndex ||
            queueSize != lastResumePersistQueueSize
        if (!stateChanged) {
            if (positionMs >= 0L && now - lastResumePersistAtMs < RESUME_INDEX_PERSIST_INTERVAL_MS) {
                return
            }
        }
        val baseForPersist = resolveResumeBaseForPersist().orEmpty()
        val usernameForPersist = embyUsernameInput.text?.toString()?.trim().orEmpty()
        playbackResumeStore.save(
            PlaybackResumeStore.Snapshot(
                queueJson = TrackCodec.buildCachedTrackArray(loadedTracks),
                index = safeIndex,
                savedAtMs = now,
                baseUrl = baseForPersist,
                username = usernameForPersist
            )
        )
        appendRuntimeLog(
            "resume persist saved index=$safeIndex queueSize=$queueSize"
        )
        lastResumePersistTrackId = activeTrack.id
        lastResumePersistIndex = safeIndex
        lastResumePersistQueueSize = queueSize
        lastResumePersistAtMs = now
    }

    private fun clearPersistedPlaybackResumeState() {
        playbackResumeStore.clear()
        appendRuntimeLog("resume persist cleared")
        lastResumePersistTrackId = ""
        lastResumePersistIndex = -1
        lastResumePersistQueueSize = -1
        lastResumePersistAtMs = 0L
    }

    private fun resolveResumeBaseForPersist(): String? {
        val sessionBase = embySessionBaseUrl?.trim().orEmpty()
        if (sessionBase.isNotEmpty()) {
            return sessionBase
        }
        val inputBase = embyBaseUrlInput.text?.toString()?.trim().orEmpty()
        if (!isHttpUrl(inputBase)) {
            return null
        }
        return runCatching { embyApi.normalizeEmbyBase(inputBase) }.getOrNull()
    }

    private fun hasPlaybackSession(): Boolean {
        return !embySessionBaseUrl.isNullOrBlank() &&
            !embySessionUserId.isNullOrBlank() &&
            !embyAccessToken.isNullOrBlank()
    }

    private fun removeTrackFromTodayRecommendCache(trackId: String) {
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        val payload = prefs.getString(KEY_RECOMMEND_CACHE_JSON, "").orEmpty()
        if (payload.isBlank()) {
            return
        }
        val tracks = TrackCodec.parseCachedTrackArray(payload)
        val filtered = tracks.filterNot { it.id == trackId }
        if (filtered.size == tracks.size) {
            return
        }
        prefs.edit()
            .putString(KEY_RECOMMEND_CACHE_JSON, TrackCodec.buildCachedTrackArray(filtered))
            .apply()
    }

    private fun removeTrackDownloadArtifacts(trackId: String) {
        val cacheFile = File(cacheDir, "emby_${trackId}.cache")
        runCatching {
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
        synchronized(downloadStateLock) {
            trackDownloadStates.remove(trackId)
        }
    }

    private fun maybeClearDownloadCacheOnColdStart() {
        if (downloadCacheClearedAtColdStart) {
            return
        }
        downloadCacheClearedAtColdStart = true
        backgroundExecutor.execute {
            val keepTrackId = resolveResumeTrackIdForColdStartKeep()
            val cleared = clearDownloadCacheFiles(
                reason = "cold-start",
                keepTrackId = keepTrackId
            )
            appendRuntimeLog("download-cache cold-start keepTrackId=$keepTrackId success=$cleared")
            runOnUiThread {
                refreshDownloadCacheInfoUi()
            }
        }
    }

    private fun resolveResumeTrackIdForColdStartKeep(): String? {
        val snapshot = playbackResumeStore.read() ?: return null
        val tracks = TrackCodec.parseCachedTrackArray(snapshot.queueJson)
        if (tracks.isEmpty()) {
            return null
        }
        val safeIndex = snapshot.index.coerceIn(0, tracks.lastIndex)
        return tracks.getOrNull(safeIndex)?.id?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun clearDownloadCacheFiles(reason: String, keepTrackId: String? = null): Boolean {
        val files = cacheDir?.listFiles()?.filter {
            it.isFile && it.name.startsWith("emby_") && it.name.endsWith(".cache")
        }.orEmpty()
        val keepFileName = keepTrackId?.let { "emby_${it}.cache" }
        var kept = 0
        var allDeleted = true
        files.forEach { file ->
            if (!keepFileName.isNullOrEmpty() && file.name == keepFileName) {
                kept += 1
                return@forEach
            }
            val deleted = runCatching { file.delete() }.getOrDefault(false)
            if (!deleted) {
                allDeleted = false
            }
        }
        synchronized(downloadStateLock) {
            if (keepTrackId.isNullOrEmpty()) {
                trackDownloadStates.clear()
            } else {
                val keepState = trackDownloadStates[keepTrackId]
                trackDownloadStates.clear()
                if (keepState != null) {
                    trackDownloadStates[keepTrackId] = keepState
                }
            }
        }
        appendRuntimeLog(
            "download-cache clear reason=$reason files=${files.size} kept=$kept keepTrackId=${keepTrackId.orEmpty()} success=$allDeleted"
        )
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "cache_cleanup",
            properties = mapOf(
                "reason" to reason,
                "files_total" to files.size,
                "files_kept" to kept,
                "keep_track_id" to keepTrackId.orEmpty(),
                "success" to allDeleted
            ),
            priority = if (allDeleted) PostHogTracker.Priority.NORMAL else PostHogTracker.Priority.HIGH
        )
        return allDeleted
    }

    private fun ensureWifiConnectedForNetworkRequest(requestTag: String, promptUser: Boolean): Boolean {
        return wifiNetworkGate.ensureWifiConnectedForNetworkRequest(
            requestTag = requestTag,
            promptUser = promptUser
        )
    }

    private fun captureBootStage(stage: String, bootStartMs: Long) {
        PostHogTracker.capture(
            context = applicationContext,
            eventName = "boot_stage",
            properties = mapOf(
                "stage" to stage,
                "elapsed_ms" to (SystemClock.elapsedRealtime() - bootStartMs).coerceAtLeast(0L)
            )
        )
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
        backgroundExecutor.execute {
            val bytes = calculateDownloadCacheBytes()
            runOnUiThread {
                if (!this::downloadCacheSizeValue.isInitialized) {
                    return@runOnUiThread
                }
                val text = Formatter.formatShortFileSize(this, bytes)
                downloadCacheSizeValue.text = getString(R.string.download_cache_size_format, text)
            }
        }
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
        reportPlaybackStateToService()
        maybePersistPlaybackResumeState()
    }

    override fun onPlaybackCommand(action: String, source: String, allowToast: Boolean): Boolean {
        if (!this::uiState.isInitialized) {
            return false
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return performPlaybackCommand(action, source = source, allowToast = allowToast)
        }
        val latch = CountDownLatch(1)
        val handledRef = booleanArrayOf(false)
        runOnUiThread {
            handledRef[0] = performPlaybackCommand(action, source = source, allowToast = allowToast)
            latch.countDown()
        }
        return try {
            if (!latch.await(EXTERNAL_COMMAND_WAIT_MS, TimeUnit.MILLISECONDS)) {
                appendRuntimeLog("service cmd timeout action=$action source=$source")
                false
            } else {
                handledRef[0]
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    private fun reportPlaybackStateToService(force: Boolean = false) {
        val trackTitle = uiState.currentTrack
        val hasTrack = loadedTracks.isNotEmpty()
        val trackId = loadedTracks.getOrNull(currentTrackIndex)?.id.orEmpty()
        val isPlaying = uiState.isPlaying && hasTrack
        val positionMs = playbackEngine?.currentPositionMs()?.coerceAtLeast(0L) ?: 0L
        val engineDurationMs = playbackEngine?.durationMs() ?: -1L
        val durationMs = loadedTracks.getOrNull(currentTrackIndex)?.let {
            resolveTrackDurationMs(it, engineDurationMs)
        }?.coerceAtLeast(0L) ?: engineDurationMs.coerceAtLeast(0L)
        val nowMs = SystemClock.elapsedRealtime()
        val baseUnchanged = trackId == lastReportedServiceTrackId &&
            trackTitle == lastReportedServiceTrackTitle &&
            hasTrack == lastReportedServiceHasTrack &&
            isPlaying == lastReportedServiceIsPlaying &&
            durationMs == lastReportedServiceDurationMs
        val positionDeltaMs = abs(positionMs - lastReportedServicePositionMs)
        val elapsedMs = nowMs - lastReportedServiceAtMs
        val shouldSend = force ||
            !baseUnchanged ||
            positionDeltaMs >= SERVICE_REPORT_POSITION_DELTA_MS ||
            (isPlaying && elapsedMs >= SERVICE_REPORT_HEARTBEAT_MS)
        if (!shouldSend) {
            return
        }
        lastReportedServiceTrackId = trackId
        lastReportedServiceTrackTitle = trackTitle
        lastReportedServiceHasTrack = hasTrack
        lastReportedServiceIsPlaying = isPlaying
        lastReportedServicePositionMs = positionMs
        lastReportedServiceDurationMs = durationMs
        lastReportedServiceAtMs = nowMs
        sendPlaybackServiceIntent(PlaybackActions.ACTION_STATE_UPDATE) {
            putExtra(PlaybackActions.EXTRA_TRACK_TITLE, trackTitle)
            putExtra(PlaybackActions.EXTRA_TRACK_ID, trackId)
            putExtra(PlaybackActions.EXTRA_HAS_ACTIVE_TRACK, hasTrack)
            putExtra(PlaybackActions.EXTRA_IS_PLAYING, isPlaying)
            putExtra(PlaybackActions.EXTRA_POSITION_MS, positionMs)
            putExtra(PlaybackActions.EXTRA_DURATION_MS, durationMs)
        }
    }

    private fun sendPlaybackServiceIntent(action: String, extras: (Intent.() -> Unit)? = null): Boolean {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        extras?.invoke(intent)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            return true
        } catch (e: Exception) {
            appendRuntimeLog("playback-service send failed action=$action type=${e.javaClass.simpleName}")
            return false
        }
    }

    private fun maybeRequestOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23) {
            return
        }
        if (Settings.canDrawOverlays(this)) {
            return
        }
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        val prompted = prefs.getBoolean(KEY_OVERLAY_PERMISSION_PROMPTED, false)
        if (prompted) {
            return
        }
        prefs.edit().putBoolean(KEY_OVERLAY_PERMISSION_PROMPTED, true).apply()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        val canOpenSettings = intent.resolveActivity(packageManager) != null
        if (canOpenSettings) {
            runCatching {
                startActivity(intent)
            }
        } else {
            appendRuntimeLog("overlay permission settings activity unavailable")
        }
        showToast(R.string.toast_overlay_permission_needed)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    performPlaybackCommand(
                        PlaybackActions.ACTION_CMD_PREV,
                        source = PlaybackActions.CMD_SOURCE_HARDWARE_KEY,
                        allowToast = false
                    )
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    performPlaybackCommand(
                        PlaybackActions.ACTION_CMD_NEXT,
                        source = PlaybackActions.CMD_SOURCE_HARDWARE_KEY,
                        allowToast = false
                    )
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    performPlaybackCommand(
                        PlaybackActions.ACTION_CMD_PLAY_PAUSE,
                        source = PlaybackActions.CMD_SOURCE_HARDWARE_KEY,
                        allowToast = false
                    )
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    performPlaybackCommand(
                        PlaybackActions.ACTION_CMD_PLAY,
                        source = PlaybackActions.CMD_SOURCE_HARDWARE_KEY,
                        allowToast = false
                    )
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    performPlaybackCommand(
                        PlaybackActions.ACTION_CMD_PAUSE,
                        source = PlaybackActions.CMD_SOURCE_HARDWARE_KEY,
                        allowToast = false
                    )
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        if (selectedPage != PAGE_HOME) {
            switchPage(PAGE_HOME)
            updateState { it.copy(feedbackText = "动作反馈：已返回首页") }
            return
        }
        appendRuntimeLog("back pressed -> move task to background")
        moveTaskToBack(true)
    }

    private fun resolveBuildVersionTag(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= 28) {
                val field = info.javaClass.getField("longVersionCode")
                field.getLong(info)
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            "#$versionCode"
        } catch (_: Exception) {
            "#?"
        }
    }

    private fun performPlaybackCommand(action: String, source: String, allowToast: Boolean): Boolean {
        return when (action) {
            PlaybackActions.ACTION_CMD_PLAY_PAUSE -> performPlayPauseAction(
                forcePlay = null,
                source = source,
                allowToast = allowToast
            )
            PlaybackActions.ACTION_CMD_PLAY -> performPlayPauseAction(
                forcePlay = true,
                source = source,
                allowToast = allowToast
            )
            PlaybackActions.ACTION_CMD_PAUSE -> performPlayPauseAction(
                forcePlay = false,
                source = source,
                allowToast = allowToast
            )
            PlaybackActions.ACTION_CMD_NEXT -> performNextAction(source = source, allowToast = allowToast)
            PlaybackActions.ACTION_CMD_PREV -> performPrevAction(source = source, allowToast = allowToast)
            else -> false
        }
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
        const val KEY_OVERLAY_PERMISSION_PROMPTED = "overlay_permission_prompted"
        const val KEY_RECOMMEND_CACHE_DAY = "recommend_cache_day"
        const val KEY_RECOMMEND_CACHE_OWNER = "recommend_cache_owner"
        const val KEY_RECOMMEND_CACHE_JSON = "recommend_cache_json"
        // Start playback once playable duration is >=3s and rebuffer with >=1s.
        const val LOAD_CONTROL_MIN_BUFFER_MS = 8_000
        const val LOAD_CONTROL_MAX_BUFFER_MS = 50_000
        const val LOAD_CONTROL_PLAYBACK_MS = 3_000
        const val LOAD_CONTROL_REBUFFER_MS = 1_000
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
        const val EXTERNAL_COMMAND_WAIT_MS = 800L
        const val SERVICE_REPORT_POSITION_DELTA_MS = 2_000L
        const val SERVICE_REPORT_HEARTBEAT_MS = 10_000L
        const val ENABLE_RESUME_STATE_RESTORE = true
        const val RESUME_INDEX_PERSIST_INTERVAL_MS = 4_000L
        const val AUTO_QUEUE_REFRESH_COOLDOWN_MS = 15_000L
        const val APP_STARTUP_QUEUE_REFRESH_DELAY_MS = 400L
        const val AUTO_UPDATE_CHECK_DELAY_MS = 1_500L
        const val NETWORK_RECOVERY_RETRY_INTERVAL_MS = 4_000L
        const val PAGE_HOME = 0
        const val PAGE_LIBRARY = 1
        const val PAGE_SETTINGS = 2
        const val DEFAULT_HOME_QUEUE_SIZE = 20
        const val LIBRARY_PAGE_SIZE = 40
        const val LYRICS_CACHE_MAX_TRACKS = 32
        const val AUTO_PLAY_FIRST_TRACK_ON_EMBY_LOAD = true
        @Volatile var downloadCacheClearedAtColdStart = false
    }
}
