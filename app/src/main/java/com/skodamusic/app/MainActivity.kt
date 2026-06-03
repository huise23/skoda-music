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
import android.media.audiofx.AudioEffect
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.skodamusic.app.audio.EqualizerManager
import com.skodamusic.app.audio.dsp.HiFiDspController
import com.skodamusic.app.audio.dsp.HiFiDspMode
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.core.network.WifiNetworkGate
import com.skodamusic.app.data.EmbySessionCache
import com.skodamusic.app.emby.EmbyApi
import com.skodamusic.app.kugou.KugouLoginResult
import com.skodamusic.app.kugou.KugouSessionStore
import com.skodamusic.app.kugou.KugouWebApiClient
import com.skodamusic.app.like.LikeStatusItem
import com.skodamusic.app.like.LikeStatusStore
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
import com.skodamusic.app.model.MusicSource
import com.skodamusic.app.model.PlaybackFailureCategory
import com.skodamusic.app.model.SourceCapability
import com.skodamusic.app.model.SourcePlaybackRef
import com.skodamusic.app.model.SourcePlaylist
import com.skodamusic.app.model.SourceRadio
import com.skodamusic.app.model.SourceTrack
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
    private lateinit var kugouWebApiBaseUrlInput: EditText
    private lateinit var kugouStatusValue: TextView
    private lateinit var kugouHomeStatusValue: TextView
    private lateinit var kugouQrStatusValue: TextView
    private lateinit var kugouQrUrlValue: TextView
    private lateinit var kugouQrImage: ImageView
    private lateinit var kugouRefreshQrButton: Button
    private lateinit var kugouMobileInput: EditText
    private lateinit var kugouCodeInput: EditText
    private lateinit var kugouSendSmsButton: Button
    private lateinit var kugouSmsLoginButton: Button
    private lateinit var kugouLogoutButton: Button
    private lateinit var kugouHomeLoginPanel: View
    private lateinit var kugouRadioStatusValue: TextView
    private lateinit var kugouRadioList: LinearLayout
    private lateinit var kugouDiscoverStatusValue: TextView
    private lateinit var kugouDiscoverTagList: LinearLayout
    private lateinit var kugouDiscoverPlaylistList: LinearLayout
    private lateinit var likeStatusList: LinearLayout
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
    private var lastPlayButtonDspStatus: HiFiDspController.RuntimeStatus? = null
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
    private lateinit var eqEnableSwitch: SwitchCompat
    private lateinit var eqEntryRow: View
    private lateinit var eqEntryValue: TextView
    private lateinit var eqNoteValue: TextView
    private lateinit var eqPage: View
    private lateinit var eqPageStateValue: TextView
    private lateinit var eqBandsContainer: LinearLayout
    private lateinit var eqPresetsContainer: LinearLayout
    private lateinit var eqBackButton: ImageButton
    private lateinit var navHomeButton: ImageButton
    private lateinit var navKugouRadioButton: ImageButton
    private lateinit var navKugouDiscoverButton: ImageButton
    private lateinit var navQueueButton: ImageButton
    private lateinit var navLikeStatusButton: ImageButton
    private lateinit var navLibraryButton: ImageButton
    private lateinit var navSettingsButton: ImageButton
    private lateinit var pageHome: View
    private lateinit var pageKugouRadio: View
    private lateinit var pageKugouDiscover: View
    private lateinit var pageQueue: View
    private lateinit var pageLikeStatus: View
    private lateinit var pageLibrary: ScrollView
    private lateinit var pageSettings: View
    private lateinit var uiState: UiState

    private var loadedTracks: List<EmbyTrack> = emptyList()
    private var kugouRecommendedTracks: List<SourceTrack> = emptyList()
    private var kugouRecommendedLoading: Boolean = false
    private var kugouRecommendedRadios: List<SourceRadio> = emptyList()
    private var kugouRadioSongs: List<SourceTrack> = emptyList()
    private var kugouRadioLoading: Boolean = false
    private var kugouSelectedRadioId: String = ""
    private var kugouDiscoverTags: List<Pair<Int, String>> = emptyList()
    private var kugouDiscoverPlaylists: List<SourcePlaylist> = emptyList()
    private var kugouDiscoverSongs: List<SourceTrack> = emptyList()
    private var kugouDiscoverLoading: Boolean = false
    private var kugouSelectedDiscoverTagId: Int = -1
    private var kugouSelectedPlaylistId: String = ""
    private val libraryTracks = mutableListOf<EmbyTrack>()
    private var libraryLoadInFlight: Boolean = false
    private var libraryNextStartIndex: Int = 0
    private var libraryTotalRecordCount: Int = Int.MAX_VALUE
    private var libraryPagingBound: Boolean = false
    private var embySessionBaseUrl: String? = null
    private var embySessionUserId: String? = null
    private var embyAccessToken: String? = null
    private var kugouWebApiBaseUrl: String = ""
    private var kugouSessionKey: String = ""
    private var kugouLastUserId: String = ""
    private var kugouDisplayName: String = ""
    private var kugouQrKey: String = ""
    private var kugouQrPollingActive: Boolean = false
    private var kugouQrRequestGeneration: Int = 0
    private var kugouQrPollGeneration: Int = 0
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
    private lateinit var kugouSessionStore: KugouSessionStore
    private lateinit var kugouWebApiClient: KugouWebApiClient
    private lateinit var likeStatusStore: LikeStatusStore
    private lateinit var equalizerManager: EqualizerManager
    private lateinit var hiFiDspController: HiFiDspController
    private var postHogSessionId: String = ""
    private var lastObservedCommandTraceAtMs: Long = 0L
    private var lastObservedAudioSessionId: Int = -1
    private var eqEnabled: Boolean = false
    private var eqPresetIndex: Int = 0
    private var eqMode: EqualizerManager.EqMode = EqualizerManager.EqMode.PRESET
    private val eqCustomBandLevels = mutableListOf<Int>()
    private var suppressEqSwitchListener: Boolean = false
    private var systemEqSessionId: Int = -1
    private var lastSystemEqUnavailableToastAtMs: Long = 0L
    private var systemEqFallbackHintShown: Boolean = false
    private var soundEffectEnabled: Boolean = false
    private var soundEffectMode: HiFiDspMode = HiFiDspMode.FIDELITY

    private data class FixedEqBand(
        val index: Int,
        val label: String,
        val frequencyHz: Int
    )

    private data class FixedEqPreset(
        val name: String,
        val levels: IntArray
    )

    private data class SoundModeOption(
        val mode: HiFiDspMode,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        val bootStartMs = SystemClock.elapsedRealtime()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        PlaybackControlBus.attach(this)
        playbackResumeStore = PlaybackResumeStore(applicationContext)
        playbackStateStore = PlaybackStateStore(applicationContext)
        backgroundExecutor = AppBackgroundExecutor(ioThreads = 3)
        wifiNetworkGate = WifiNetworkGate(this) { message -> appendRuntimeLog(message) }
        kugouSessionStore = KugouSessionStore(applicationContext)
        kugouWebApiClient = KugouWebApiClient { message -> appendRuntimeLog(message) }
        likeStatusStore = LikeStatusStore(applicationContext)
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
        hiFiDspController = HiFiDspController { message -> appendRuntimeLog(message) }
        equalizerManager = EqualizerManager { message -> appendRuntimeLog(message) }
        equalizerManager.updateConfig(
            enabled = false,
            presetIndex = 0,
            mode = EqualizerManager.EqMode.PRESET,
            customBandLevels = FIXED_EQ_FLAT_LEVELS.toList(),
            source = "init"
        )
        if (USE_SYSTEM_EQ_INHERIT_MODE) {
            appendRuntimeLog("eq mode=system-priority manual-app-eq-fallback-enabled")
        }
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
        kugouWebApiBaseUrlInput = findViewById(R.id.kugou_webapi_base_url_input)
        kugouStatusValue = findViewById(R.id.kugou_status_value)
        kugouHomeStatusValue = findViewById(R.id.kugou_home_status_value)
        kugouQrStatusValue = findViewById(R.id.kugou_qr_status_value)
        kugouQrUrlValue = findViewById(R.id.kugou_qr_url_value)
        kugouQrImage = findViewById(R.id.kugou_qr_image)
        kugouRefreshQrButton = findViewById(R.id.btn_kugou_refresh_qr)
        kugouMobileInput = findViewById(R.id.kugou_mobile_input)
        kugouCodeInput = findViewById(R.id.kugou_code_input)
        kugouSendSmsButton = findViewById(R.id.btn_kugou_send_sms)
        kugouSmsLoginButton = findViewById(R.id.btn_kugou_sms_login)
        kugouLogoutButton = findViewById(R.id.btn_kugou_logout)
        kugouHomeLoginPanel = findViewById(R.id.kugou_home_login_panel)
        kugouRadioStatusValue = findViewById(R.id.kugou_radio_status_value)
        kugouRadioList = findViewById(R.id.kugou_radio_list)
        kugouDiscoverStatusValue = findViewById(R.id.kugou_discover_status_value)
        kugouDiscoverTagList = findViewById(R.id.kugou_discover_tag_list)
        kugouDiscoverPlaylistList = findViewById(R.id.kugou_discover_playlist_list)
        likeStatusList = findViewById(R.id.like_status_list)
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
        eqEnableSwitch = findViewById(R.id.eq_enable_switch)
        eqEntryRow = findViewById(R.id.eq_entry_row)
        eqEntryValue = findViewById(R.id.eq_entry_value)
        eqNoteValue = findViewById(R.id.eq_note_value)
        eqPage = findViewById(R.id.page_eq)
        eqPageStateValue = findViewById(R.id.eq_page_state_value)
        eqBandsContainer = findViewById(R.id.eq_bands_container)
        eqPresetsContainer = findViewById(R.id.eq_presets_container)
        eqBackButton = findViewById(R.id.btn_eq_back)
        navHomeButton = findViewById(R.id.nav_home)
        navKugouRadioButton = findViewById(R.id.nav_kugou_radio)
        navKugouDiscoverButton = findViewById(R.id.nav_kugou_discover)
        navQueueButton = findViewById(R.id.nav_queue)
        navLikeStatusButton = findViewById(R.id.nav_like_status)
        navLibraryButton = findViewById(R.id.nav_library)
        navSettingsButton = findViewById(R.id.nav_settings)
        pageHome = findViewById(R.id.page_home)
        pageKugouRadio = findViewById(R.id.page_kugou_radio)
        pageKugouDiscover = findViewById(R.id.page_kugou_discover)
        pageQueue = findViewById(R.id.page_queue)
        pageLikeStatus = findViewById(R.id.page_like_status)
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
        switchHomeTab(showRecommend = true)
        bindLibraryPaging()
        bindNavigation()
        switchPage(PAGE_HOME)
        loadSavedCredentials()
        restoreKugouSessionFromCache()
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
        stopKugouQrPolling()
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
        navKugouRadioButton.setImageResource(android.R.drawable.ic_menu_upload)
        navKugouDiscoverButton.setImageResource(android.R.drawable.ic_menu_search)
        navQueueButton.setImageResource(android.R.drawable.ic_menu_sort_by_size)
        navLikeStatusButton.setImageResource(android.R.drawable.ic_menu_save)
        navLibraryButton.setImageResource(android.R.drawable.ic_menu_agenda)
        navSettingsButton.setImageResource(android.R.drawable.ic_menu_manage)
        navHomeButton.contentDescription = getString(R.string.nav_kugou_recommended_songs)
        navKugouRadioButton.contentDescription = getString(R.string.nav_kugou_recommended_radio)
        navKugouDiscoverButton.contentDescription = getString(R.string.nav_kugou_discover_playlists)
        navQueueButton.contentDescription = getString(R.string.nav_playback_queue)
        navLikeStatusButton.contentDescription = getString(R.string.nav_like_status)
        navLibraryButton.contentDescription = getString(R.string.nav_library)
        navSettingsButton.contentDescription = getString(R.string.nav_settings)

        prevButton.setImageResource(android.R.drawable.ic_media_previous)
        prevButton.contentDescription = getString(R.string.action_prev)
        playPauseButton.setImageResource(android.R.drawable.ic_media_play)
        playPauseButton.contentDescription = getString(R.string.action_play)
        refreshDspPlayButtonIndicator()
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
            if (!hasKugouSession()) {
                homeRecommendRefresh.isRefreshing = false
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_kugou)) }
                requestKugouQrLogin()
                return@setOnRefreshListener
            }
            requestKugouRecommendedSongs(onFinished = {
                runOnUiThread {
                    homeRecommendRefresh.isRefreshing = false
                }
            })
        }

        kugouRefreshQrButton.setOnClickListener {
            requestKugouQrLogin()
        }
        kugouSendSmsButton.setOnClickListener {
            requestKugouSmsCode()
        }
        kugouSmsLoginButton.setOnClickListener {
            requestKugouSmsLogin()
        }
        kugouLogoutButton.setOnClickListener {
            requestKugouLogout()
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

        eqEnableSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (suppressEqSwitchListener) {
                return@setOnCheckedChangeListener
            }
            if (soundEffectEnabled == isChecked) {
                return@setOnCheckedChangeListener
            }
            if (isChecked) {
                soundEffectEnabled = true
                if (soundEffectMode == HiFiDspMode.ORIGINAL) {
                    soundEffectMode = HiFiDspMode.FIDELITY
                }
            } else {
                soundEffectEnabled = false
            }
            applySoundEffectConfig("ui_toggle")
            persistSoundEffectConfig()
            refreshEqualizerSettingsUi()
            updateState {
                it.copy(
                    feedbackText = getString(
                        if (isChecked) R.string.feedback_sound_enabled else R.string.feedback_sound_disabled
                    )
                )
            }
            showToast(if (isChecked) R.string.toast_sound_enabled else R.string.toast_sound_disabled)
        }
        eqEntryRow.setOnClickListener {
            switchPage(PAGE_EQ)
        }
        eqBackButton.setOnClickListener {
            switchPage(PAGE_SETTINGS)
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
        navKugouRadioButton.setOnClickListener {
            switchPage(PAGE_KUGOU_RADIO)
        }
        navKugouDiscoverButton.setOnClickListener {
            switchPage(PAGE_KUGOU_DISCOVER)
        }
        navQueueButton.setOnClickListener {
            switchPage(PAGE_QUEUE)
        }
        navLikeStatusButton.setOnClickListener {
            switchPage(PAGE_LIKE_STATUS)
        }
        navSettingsButton.setOnClickListener {
            switchPage(PAGE_SETTINGS)
        }
    }

    private fun switchPage(targetPage: Int) {
        selectedPage = targetPage
        pageHome.visibility = if (selectedPage == PAGE_HOME) View.VISIBLE else View.GONE
        pageKugouRadio.visibility = if (selectedPage == PAGE_KUGOU_RADIO) View.VISIBLE else View.GONE
        pageKugouDiscover.visibility = if (selectedPage == PAGE_KUGOU_DISCOVER) View.VISIBLE else View.GONE
        pageQueue.visibility = if (selectedPage == PAGE_QUEUE) View.VISIBLE else View.GONE
        pageLikeStatus.visibility = if (selectedPage == PAGE_LIKE_STATUS) View.VISIBLE else View.GONE
        pageLibrary.visibility = if (selectedPage == PAGE_LIBRARY) View.VISIBLE else View.GONE
        pageSettings.visibility = if (selectedPage == PAGE_SETTINGS) View.VISIBLE else View.GONE
        eqPage.visibility = if (selectedPage == PAGE_EQ) View.VISIBLE else View.GONE
        updateNavigationVisualState()
        if (selectedPage == PAGE_QUEUE) {
            rebuildTrackLists()
        } else if (selectedPage == PAGE_LIKE_STATUS) {
            renderLikeStatusPage()
        } else if (selectedPage == PAGE_KUGOU_RADIO) {
            if (hasKugouSession()) {
                requestKugouRecommendedRadios()
            } else {
                renderKugouRadioPage()
            }
        } else if (selectedPage == PAGE_KUGOU_DISCOVER) {
            if (hasKugouSession()) {
                requestKugouDiscoverTags()
            } else {
                renderKugouDiscoverPage()
            }
        } else if (selectedPage == PAGE_LIBRARY) {
            ensureLibraryTracksLoaded("enter-library-page")
        } else if (selectedPage == PAGE_SETTINGS) {
            refreshEqualizerSettingsUi()
            refreshDownloadCacheInfoUi()
            refreshKugouLoginUi()
        } else if (selectedPage == PAGE_EQ) {
            refreshEqualizerSettingsUi()
            renderEqualizerFullscreenPage()
        }
        if (selectedPage == PAGE_HOME || selectedPage == PAGE_KUGOU_RADIO || selectedPage == PAGE_KUGOU_DISCOVER) {
            refreshKugouLoginUi()
        }
    }

    private fun restoreKugouSessionFromCache() {
        val snapshot = kugouSessionStore.load()
        val savedBase = snapshot?.baseUrl ?: kugouSessionStore.loadBaseUrl()
        kugouWebApiBaseUrl = savedBase
        kugouWebApiBaseUrlInput.setText(savedBase)
        if (snapshot == null) {
            clearKugouSessionState(clearStored = false)
            refreshKugouLoginUi()
            if (savedBase.isNotEmpty()) {
                requestKugouQrLogin()
            }
            return
        }
        kugouSessionKey = snapshot.sessionKey
        kugouLastUserId = snapshot.lastUserId
        kugouDisplayName = snapshot.displayName
        setKugouStatusText(getString(R.string.kugou_status_checking))
        refreshKugouSession("startup-cache")
    }

    private fun refreshKugouSession(source: String) {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        if (baseUrl.isEmpty() || session.isEmpty()) {
            clearKugouSessionState(clearStored = session.isEmpty())
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_refresh", promptUser = false)) {
            appendRuntimeLog("kugou refresh skip source=$source reason=wifi")
            refreshKugouLoginUi()
            return
        }
        appendRuntimeLog("kugou refresh start source=$source")
        backgroundExecutor.execute {
            val result = kugouWebApiClient.refreshSession(baseUrl, session)
            runOnUiThread {
                if (result == null) {
                    appendRuntimeLog("kugou refresh failed source=$source")
                    clearKugouSessionState(clearStored = true)
                    requestKugouQrLogin()
                    return@runOnUiThread
                }
                applyKugouLoginResult(baseUrl, result, "refresh")
                requestKugouRecommendedSongs()
            }
        }
    }

    private fun requestKugouQrLogin() {
        val baseUrl = resolveAndPersistKugouBaseUrl()
        if (baseUrl.isEmpty()) {
            setKugouStatusText(getString(R.string.kugou_status_failed))
            setKugouQrText(getString(R.string.kugou_qr_waiting))
            updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_base_missing)) }
            showToast(R.string.toast_kugou_failed)
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_qr", promptUser = true)) {
            return
        }
        val generation = ++kugouQrRequestGeneration
        stopKugouQrPolling()
        kugouQrImage.setImageDrawable(null)
        setKugouStatusText(getString(R.string.kugou_status_not_logged_in))
        setKugouQrText(getString(R.string.kugou_qr_loading))
        updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_qr_loading)) }
        backgroundExecutor.execute {
            val qr = kugouWebApiClient.getQrCode(baseUrl, kugouSessionKey.takeIf { it.isNotBlank() })
            val bitmap = qr?.let { kugouWebApiClient.downloadBitmap(it.imageUrl) }
            runOnUiThread {
                if (generation != kugouQrRequestGeneration) {
                    return@runOnUiThread
                }
                if (qr == null) {
                    setKugouStatusText(getString(R.string.kugou_status_failed))
                    setKugouQrText(getString(R.string.kugou_qr_expired))
                    showToast(R.string.toast_kugou_failed)
                    return@runOnUiThread
                }
                kugouSessionKey = qr.sessionKey
                kugouQrKey = qr.key
                kugouQrUrlValue.text = qr.imageUrl
                if (bitmap != null) {
                    kugouQrImage.setImageBitmap(bitmap)
                } else {
                    kugouQrImage.setImageDrawable(null)
                }
                setKugouQrText(getString(R.string.kugou_qr_scan))
                startKugouQrPolling(baseUrl, qr.sessionKey, qr.key)
            }
        }
    }

    private fun startKugouQrPolling(baseUrl: String, sessionKey: String, qrKey: String) {
        val generation = ++kugouQrPollGeneration
        kugouQrPollingActive = true
        uiProgressHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!kugouQrPollingActive || generation != kugouQrPollGeneration) {
                    return
                }
                backgroundExecutor.execute {
                    val status = kugouWebApiClient.checkQrStatus(baseUrl, sessionKey, qrKey)
                    runOnUiThread {
                        if (!kugouQrPollingActive || generation != kugouQrPollGeneration) {
                            return@runOnUiThread
                        }
                        if (status == null) {
                            uiProgressHandler.postDelayed(this, KUGOU_QR_POLL_INTERVAL_MS)
                            return@runOnUiThread
                        }
                        kugouSessionKey = status.sessionKey
                        when (status.status) {
                            KugouWebApiClient.QR_STATUS_WAITING_FOR_SCAN -> {
                                setKugouQrText(getString(R.string.kugou_qr_scan))
                                uiProgressHandler.postDelayed(this, KUGOU_QR_POLL_INTERVAL_MS)
                            }
                            KugouWebApiClient.QR_STATUS_WAITING_FOR_CONFIRM -> {
                                setKugouQrText(getString(R.string.kugou_qr_confirm))
                                uiProgressHandler.postDelayed(this, KUGOU_QR_POLL_INTERVAL_MS)
                            }
                            KugouWebApiClient.QR_STATUS_SUCCESS -> {
                                stopKugouQrPolling()
                                setKugouQrText(getString(R.string.kugou_qr_success))
                                refreshKugouSession("qr-success")
                            }
                            KugouWebApiClient.QR_STATUS_EXPIRED -> {
                                stopKugouQrPolling()
                                setKugouQrText(getString(R.string.kugou_qr_expired))
                            }
                            else -> {
                                uiProgressHandler.postDelayed(this, KUGOU_QR_POLL_INTERVAL_MS)
                            }
                        }
                    }
                }
            }
        }, KUGOU_QR_POLL_INTERVAL_MS)
    }

    private fun stopKugouQrPolling() {
        kugouQrPollingActive = false
        kugouQrPollGeneration += 1
    }

    private fun requestKugouSmsCode() {
        val baseUrl = resolveAndPersistKugouBaseUrl()
        val mobile = kugouMobileInput.text?.toString()?.trim().orEmpty()
        if (baseUrl.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_base_missing)) }
            showToast(R.string.toast_kugou_failed)
            return
        }
        if (mobile.length != 11) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_sms_invalid)) }
            showToast(R.string.toast_kugou_failed)
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_sms_sent", promptUser = true)) {
            return
        }
        kugouSendSmsButton.isEnabled = false
        backgroundExecutor.execute {
            val result = kugouWebApiClient.sendSmsCode(baseUrl, kugouSessionKey.takeIf { it.isNotBlank() }, mobile)
            runOnUiThread {
                kugouSendSmsButton.isEnabled = true
                if (result.httpCode in 200..299 && result.kgStatus == 1) {
                    if (result.sessionKey.isNotBlank()) {
                        kugouSessionKey = result.sessionKey
                    }
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_sms_sent)) }
                    showToast(R.string.toast_kugou_sms_sent)
                } else {
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_login_failed)) }
                    showToast(R.string.toast_kugou_failed)
                }
            }
        }
    }

    private fun requestKugouSmsLogin() {
        val baseUrl = resolveAndPersistKugouBaseUrl()
        val mobile = kugouMobileInput.text?.toString()?.trim().orEmpty()
        val code = kugouCodeInput.text?.toString()?.trim().orEmpty()
        if (baseUrl.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_base_missing)) }
            showToast(R.string.toast_kugou_failed)
            return
        }
        if (mobile.length != 11 || code.isEmpty()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_sms_invalid)) }
            showToast(R.string.toast_kugou_failed)
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_sms_login", promptUser = true)) {
            return
        }
        kugouSmsLoginButton.isEnabled = false
        setKugouStatusText(getString(R.string.kugou_status_checking))
        backgroundExecutor.execute {
            val login = kugouWebApiClient.loginByMobile(baseUrl, kugouSessionKey.takeIf { it.isNotBlank() }, mobile, code)
            runOnUiThread {
                kugouSmsLoginButton.isEnabled = true
                if (login == null) {
                    clearKugouSessionState(clearStored = true)
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_login_failed)) }
                    showToast(R.string.toast_kugou_failed)
                    return@runOnUiThread
                }
                applyKugouLoginResult(baseUrl, login, "sms")
            }
        }
    }

    private fun requestKugouLogout() {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.takeIf { it.isNotBlank() }
        stopKugouQrPolling()
        if (baseUrl.isNotEmpty() && session != null && ensureWifiConnectedForNetworkRequest("kugou_logout", promptUser = false)) {
            backgroundExecutor.execute {
                kugouWebApiClient.logout(baseUrl, session)
            }
        }
        clearKugouSessionState(clearStored = true)
        updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_logged_out)) }
        showToast(R.string.toast_kugou_logged_out)
    }

    private fun applyKugouLoginResult(baseUrl: String, login: KugouLoginResult, source: String) {
        kugouWebApiBaseUrl = baseUrl
        kugouSessionKey = login.sessionKey
        kugouLastUserId = login.userId
        if (login.displayName.isNotBlank()) {
            kugouDisplayName = login.displayName
        }
        kugouSessionStore.persistSession(
            baseUrl = baseUrl,
            sessionKey = login.sessionKey,
            lastUserId = login.userId,
            displayName = kugouDisplayName
        )
        stopKugouQrPolling()
        refreshKugouLoginUi()
        updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_login_success)) }
        appendRuntimeLog("kugou login applied source=$source user=${login.userId}")
        showToast(R.string.toast_kugou_success)
        requestKugouRecommendedSongs()
    }

    private fun clearKugouSessionState(clearStored: Boolean) {
        stopKugouQrPolling()
        kugouSessionKey = ""
        kugouLastUserId = ""
        kugouDisplayName = ""
        kugouQrKey = ""
        kugouRecommendedTracks = emptyList()
        kugouRecommendedLoading = false
        kugouRecommendedRadios = emptyList()
        kugouRadioSongs = emptyList()
        kugouRadioLoading = false
        kugouSelectedRadioId = ""
        kugouDiscoverTags = emptyList()
        kugouDiscoverPlaylists = emptyList()
        kugouDiscoverSongs = emptyList()
        kugouDiscoverLoading = false
        kugouSelectedDiscoverTagId = -1
        kugouSelectedPlaylistId = ""
        if (this::kugouQrImage.isInitialized) {
            kugouQrImage.setImageDrawable(null)
        }
        if (this::kugouQrUrlValue.isInitialized) {
            kugouQrUrlValue.text = getString(R.string.kugou_qr_waiting)
        }
        if (clearStored) {
            kugouSessionStore.clearSession()
        }
        refreshKugouLoginUi()
        renderHomeRecommendationPreview()
        renderKugouRadioPage()
        renderKugouDiscoverPage()
    }

    private fun requestKugouRecommendedSongs(onFinished: (() -> Unit)? = null) {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasKugouSession()) {
            onFinished?.invoke()
            renderHomeRecommendationPreview()
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_kugou)) }
            return
        }
        if (kugouRecommendedLoading) {
            onFinished?.invoke()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_recommend_songs", promptUser = true)) {
            onFinished?.invoke()
            return
        }
        kugouRecommendedLoading = true
        renderHomeRecommendationPreview()
        updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_recommend_loading)) }
        backgroundExecutor.execute {
            val result = kugouWebApiClient.getRecommendedSongs(baseUrl, session)
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
                    capabilities = setOf(
                        SourceCapability.PLAY,
                        SourceCapability.LIKE,
                        SourceCapability.REQUIRES_LOGIN
                    )
                )
            }
            runOnUiThread {
                kugouRecommendedLoading = false
                onFinished?.invoke()
                if (result == null) {
                    appendRuntimeLog("kugou recommend songs failed")
                    clearKugouSessionState(clearStored = true)
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_recommend_failed)) }
                    requestKugouQrLogin()
                    return@runOnUiThread
                }
                if (result.second.isNotBlank()) {
                    kugouSessionKey = result.second
                    kugouSessionStore.persistSession(
                        baseUrl = baseUrl,
                        sessionKey = kugouSessionKey,
                        lastUserId = kugouLastUserId,
                        displayName = kugouDisplayName
                    )
                }
                kugouRecommendedTracks = mapped
                renderHomeRecommendationPreview()
                updateState {
                    it.copy(
                        feedbackText = if (mapped.isEmpty()) {
                            getString(R.string.feedback_kugou_recommend_empty)
                        } else {
                            getString(R.string.feedback_kugou_recommend_success, mapped.size)
                        }
                    )
                }
            }
        }
    }

    private fun requestKugouRecommendedRadios() {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasKugouSession()) {
            renderKugouRadioPage()
            return
        }
        if (kugouRadioLoading || kugouRecommendedRadios.isNotEmpty()) {
            renderKugouRadioPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_fm_recommend", promptUser = true)) {
            return
        }
        kugouRadioLoading = true
        renderKugouRadioPage()
        backgroundExecutor.execute {
            val result = kugouWebApiClient.getRecommendedRadios(baseUrl, session)
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
                    capabilities = setOf(
                        SourceCapability.RADIO,
                        SourceCapability.PLAY,
                        SourceCapability.REQUIRES_LOGIN
                    )
                )
            }
            runOnUiThread {
                kugouRadioLoading = false
                if (result == null) {
                    clearKugouSessionState(clearStored = true)
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_radio_failed)) }
                    requestKugouQrLogin()
                    return@runOnUiThread
                }
                if (result.second.isNotBlank()) {
                    kugouSessionKey = result.second
                    kugouSessionStore.persistSession(
                        baseUrl = baseUrl,
                        sessionKey = kugouSessionKey,
                        lastUserId = kugouLastUserId,
                        displayName = kugouDisplayName
                    )
                }
                kugouRecommendedRadios = mapped
                renderKugouRadioPage()
                updateState {
                    it.copy(
                        feedbackText = if (mapped.isEmpty()) {
                            getString(R.string.feedback_kugou_radio_empty)
                        } else {
                            getString(R.string.feedback_kugou_radio_success, mapped.size)
                        }
                    )
                }
            }
        }
    }

    private fun requestKugouRadioSongs(radio: SourceRadio) {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasKugouSession()) {
            renderKugouRadioPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_fm_songs", promptUser = true)) {
            return
        }
        kugouSelectedRadioId = radio.sourceRadioId
        kugouRadioSongs = emptyList()
        kugouRadioLoading = true
        renderKugouRadioPage()
        backgroundExecutor.execute {
            val result = kugouWebApiClient.getRadioSongs(baseUrl, session, radio.sourceRadioId, radio.type)
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
                    capabilities = setOf(
                        SourceCapability.PLAY,
                        SourceCapability.LIKE,
                        SourceCapability.REQUIRES_LOGIN
                    )
                )
            }
            runOnUiThread {
                kugouRadioLoading = false
                if (result == null) {
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_radio_songs_failed)) }
                    renderKugouRadioPage()
                    return@runOnUiThread
                }
                if (result.second.isNotBlank()) {
                    kugouSessionKey = result.second
                }
                kugouRadioSongs = mapped
                renderKugouRadioPage()
                updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_radio_songs_success, mapped.size)) }
            }
        }
    }

    private fun renderKugouRadioPage() {
        if (!this::kugouRadioList.isInitialized) {
            return
        }
        kugouRadioList.removeAllViews()
        when {
            !hasKugouSession() -> {
                kugouRadioStatusValue.text = getString(R.string.kugou_login_required_state)
                return
            }
            kugouRadioLoading -> {
                kugouRadioStatusValue.text = getString(R.string.feedback_kugou_radio_loading)
            }
            kugouRecommendedRadios.isEmpty() -> {
                kugouRadioStatusValue.text = getString(R.string.feedback_kugou_radio_empty)
            }
            else -> {
                kugouRadioStatusValue.text = getString(R.string.feedback_kugou_radio_success, kugouRecommendedRadios.size)
            }
        }
        kugouRecommendedRadios.forEachIndexed { index, radio ->
            val row = buildSourceRow(
                titleText = radio.title,
                subtitleText = radio.subtitle.ifBlank { getString(R.string.kugou_radio_title) },
                active = radio.sourceRadioId == kugouSelectedRadioId
            )
            row.setOnClickListener {
                requestKugouRadioSongs(radio)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dpToPx(8)
            }
            kugouRadioList.addView(row, params)
        }
        if (kugouRadioSongs.isNotEmpty()) {
            val spacer = TextView(this)
            spacer.text = getString(R.string.kugou_radio_songs_title)
            spacer.setTextColor(resources.getColor(R.color.text_secondary))
            spacer.textSize = 16f
            spacer.setPadding(dpToPx(4), dpToPx(16), dpToPx(4), dpToPx(8))
            kugouRadioList.addView(spacer)
            kugouRadioSongs.forEachIndexed { index, track ->
                val row = buildSourceRow(
                    titleText = track.title,
                    subtitleText = track.artist,
                    active = false
                )
                row.setOnClickListener {
                    appendRuntimeLog("kugou radio song click index=$index hash=${track.playbackRef.hash}")
                    playKugouTrack(track)
                }
                row.addView(buildLikeButton(track))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) {
                    params.topMargin = dpToPx(8)
                }
                kugouRadioList.addView(row, params)
            }
        }
    }

    private fun buildSourceRow(titleText: String, subtitleText: String, active: Boolean): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.minimumHeight = dpToPx(70)
        row.setBackgroundResource(if (active) R.drawable.row_recommend_active else R.drawable.row_recommend_idle)
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
        title.text = titleText
        title.setTextColor(resources.getColor(R.color.text_primary))
        title.textSize = 17f
        val subtitle = TextView(this)
        subtitle.text = subtitleText
        subtitle.setTextColor(resources.getColor(R.color.text_secondary))
        subtitle.textSize = 14f
        textBlock.addView(title)
        textBlock.addView(subtitle)
        row.addView(textBlock)
        return row
    }

    private fun requestKugouDiscoverTags() {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasKugouSession()) {
            renderKugouDiscoverPage()
            return
        }
        if (kugouDiscoverLoading || kugouDiscoverTags.isNotEmpty()) {
            renderKugouDiscoverPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_playlist_tags", promptUser = true)) {
            return
        }
        kugouDiscoverLoading = true
        renderKugouDiscoverPage()
        backgroundExecutor.execute {
            val result = kugouWebApiClient.getPlaylistTags(baseUrl, session)
            val tags = result?.first.orEmpty()
                .take(DISCOVER_TAG_PREVIEW_LIMIT)
                .map { it.tagId to "${it.categoryName} · ${it.tagName}" }
            runOnUiThread {
                kugouDiscoverLoading = false
                if (result == null) {
                    clearKugouSessionState(clearStored = true)
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_discover_failed)) }
                    requestKugouQrLogin()
                    return@runOnUiThread
                }
                if (result.second.isNotBlank()) {
                    kugouSessionKey = result.second
                }
                kugouDiscoverTags = tags
                renderKugouDiscoverPage()
                if (tags.isNotEmpty()) {
                    requestKugouPlaylistsByTag(tags[0].first)
                } else {
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_discover_empty)) }
                }
            }
        }
    }

    private fun requestKugouPlaylistsByTag(tagId: Int) {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasKugouSession()) {
            renderKugouDiscoverPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_top_playlist", promptUser = true)) {
            return
        }
        kugouSelectedDiscoverTagId = tagId
        kugouSelectedPlaylistId = ""
        kugouDiscoverPlaylists = emptyList()
        kugouDiscoverSongs = emptyList()
        kugouDiscoverLoading = true
        renderKugouDiscoverPage()
        backgroundExecutor.execute {
            val result = kugouWebApiClient.getPlaylistsByTag(baseUrl, session, tagId)
            val mapped = result?.first.orEmpty().map { playlist ->
                SourcePlaylist(
                    source = MusicSource.KUGOU,
                    sourcePlaylistId = playlist.listId.ifBlank { playlist.globalId },
                    title = playlist.name,
                    globalId = playlist.globalId,
                    coverUrl = playlist.coverUrl,
                    subtitle = listOf(playlist.creatorName, playlist.intro).filter { it.isNotBlank() }.joinToString(" · "),
                    tagId = tagId,
                    capabilities = setOf(
                        SourceCapability.PLAYLIST,
                        SourceCapability.PLAY,
                        SourceCapability.REQUIRES_LOGIN
                    )
                )
            }
            runOnUiThread {
                kugouDiscoverLoading = false
                if (result == null) {
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_playlist_failed)) }
                    renderKugouDiscoverPage()
                    return@runOnUiThread
                }
                if (result.second.isNotBlank()) {
                    kugouSessionKey = result.second
                }
                kugouDiscoverPlaylists = mapped
                renderKugouDiscoverPage()
                updateState {
                    it.copy(
                        feedbackText = if (mapped.isEmpty()) {
                            getString(R.string.feedback_kugou_playlist_empty)
                        } else {
                            getString(R.string.feedback_kugou_playlist_success, mapped.size)
                        }
                    )
                }
            }
        }
    }

    private fun requestKugouPlaylistSongs(playlist: SourcePlaylist) {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        val playlistId = playlist.globalId.ifBlank { playlist.sourcePlaylistId }
        if (baseUrl.isEmpty() || session.isEmpty() || playlistId.isEmpty() || !hasKugouSession()) {
            renderKugouDiscoverPage()
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_playlist_songs", promptUser = true)) {
            return
        }
        kugouSelectedPlaylistId = playlist.sourcePlaylistId
        kugouDiscoverSongs = emptyList()
        kugouDiscoverLoading = true
        renderKugouDiscoverPage()
        backgroundExecutor.execute {
            val result = kugouWebApiClient.getPlaylistSongs(baseUrl, session, playlistId, pageSize = 30)
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
                    capabilities = setOf(
                        SourceCapability.PLAY,
                        SourceCapability.LIKE,
                        SourceCapability.REQUIRES_LOGIN
                    )
                )
            }
            runOnUiThread {
                kugouDiscoverLoading = false
                if (result == null) {
                    updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_playlist_songs_failed)) }
                    renderKugouDiscoverPage()
                    return@runOnUiThread
                }
                if (result.second.isNotBlank()) {
                    kugouSessionKey = result.second
                }
                kugouDiscoverSongs = mapped
                renderKugouDiscoverPage()
                updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_playlist_songs_success, mapped.size)) }
            }
        }
    }

    private fun renderKugouDiscoverPage() {
        if (!this::kugouDiscoverTagList.isInitialized) {
            return
        }
        kugouDiscoverTagList.removeAllViews()
        kugouDiscoverPlaylistList.removeAllViews()
        when {
            !hasKugouSession() -> {
                kugouDiscoverStatusValue.text = getString(R.string.kugou_login_required_state)
                return
            }
            kugouDiscoverLoading -> {
                kugouDiscoverStatusValue.text = getString(R.string.feedback_kugou_discover_loading)
            }
            kugouDiscoverTags.isEmpty() -> {
                kugouDiscoverStatusValue.text = getString(R.string.kugou_discover_category_state)
            }
            else -> {
                kugouDiscoverStatusValue.text = getString(R.string.feedback_kugou_discover_success, kugouDiscoverTags.size)
            }
        }
        kugouDiscoverTags.forEachIndexed { index, tag ->
            val row = buildSourceRow(
                titleText = tag.second,
                subtitleText = getString(R.string.kugou_discover_title),
                active = tag.first == kugouSelectedDiscoverTagId
            )
            row.setOnClickListener {
                requestKugouPlaylistsByTag(tag.first)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dpToPx(8)
            }
            kugouDiscoverTagList.addView(row, params)
        }
        kugouDiscoverPlaylists.forEachIndexed { index, playlist ->
            val row = buildSourceRow(
                titleText = playlist.title,
                subtitleText = playlist.subtitle.ifBlank { getString(R.string.kugou_discover_title) },
                active = playlist.sourcePlaylistId == kugouSelectedPlaylistId
            )
            row.setOnClickListener {
                requestKugouPlaylistSongs(playlist)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dpToPx(8)
            }
            kugouDiscoverPlaylistList.addView(row, params)
        }
        if (kugouDiscoverSongs.isNotEmpty()) {
            val title = TextView(this)
            title.text = getString(R.string.kugou_playlist_songs_title)
            title.setTextColor(resources.getColor(R.color.text_secondary))
            title.textSize = 16f
            title.setPadding(dpToPx(4), dpToPx(16), dpToPx(4), dpToPx(8))
            kugouDiscoverPlaylistList.addView(title)
            kugouDiscoverSongs.forEachIndexed { index, track ->
                val row = buildSourceRow(
                    titleText = track.title,
                    subtitleText = track.artist,
                    active = false
                )
                row.setOnClickListener {
                    appendRuntimeLog("kugou playlist song click index=$index hash=${track.playbackRef.hash}")
                    playKugouTrack(track)
                }
                row.addView(buildLikeButton(track))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                if (index > 0) {
                    params.topMargin = dpToPx(8)
                }
                kugouDiscoverPlaylistList.addView(row, params)
            }
        }
    }

    private fun refreshKugouLoginUi() {
        val hasSession = hasKugouSession()
        val userLabel = when {
            kugouDisplayName.isNotBlank() -> kugouDisplayName
            kugouLastUserId.isNotBlank() -> kugouLastUserId
            else -> "--"
        }
        val statusText = if (hasSession) {
            getString(R.string.kugou_status_logged_in_format, userLabel)
        } else {
            getString(R.string.kugou_status_not_logged_in)
        }
        setKugouStatusText(statusText)
        kugouHomeLoginPanel.visibility = if (hasSession) View.GONE else View.VISIBLE
        kugouRefreshQrButton.isEnabled = !hasSession
        kugouLogoutButton.isEnabled = hasSession
    }

    private fun setKugouStatusText(text: String) {
        kugouStatusValue.text = text
        kugouHomeStatusValue.text = text
    }

    private fun setKugouQrText(text: String) {
        kugouQrStatusValue.text = text
        if (kugouQrKey.isEmpty()) {
            kugouQrUrlValue.text = text
        }
    }

    private fun resolveAndPersistKugouBaseUrl(): String {
        val baseUrl = resolveKugouBaseUrl()
        if (baseUrl.isNotEmpty()) {
            kugouSessionStore.persistBaseUrl(baseUrl)
            kugouWebApiBaseUrl = baseUrl
        }
        return baseUrl
    }

    private fun resolveKugouBaseUrl(): String {
        val input = kugouWebApiBaseUrlInput.text?.toString()?.trim().orEmpty()
        val candidate = if (input.isNotEmpty()) input else kugouWebApiBaseUrl
        val normalized = kugouWebApiClient.normalizeBaseUrl(candidate)
        return if (kugouWebApiClient.isHttpUrl(normalized)) normalized else ""
    }

    private fun hasKugouSession(): Boolean {
        return kugouSessionKey.isNotBlank() && kugouLastUserId.isNotBlank() && kugouLastUserId != "0"
    }

    private fun updateNavigationVisualState() {
        val onSettingsArea = selectedPage == PAGE_SETTINGS || selectedPage == PAGE_EQ
        setNavButtonSelected(navHomeButton, selectedPage == PAGE_HOME, android.R.drawable.ic_menu_view)
        setNavButtonSelected(navKugouRadioButton, selectedPage == PAGE_KUGOU_RADIO, android.R.drawable.ic_menu_upload)
        setNavButtonSelected(navKugouDiscoverButton, selectedPage == PAGE_KUGOU_DISCOVER, android.R.drawable.ic_menu_search)
        setNavButtonSelected(navQueueButton, selectedPage == PAGE_QUEUE, android.R.drawable.ic_menu_sort_by_size)
        setNavButtonSelected(navLikeStatusButton, selectedPage == PAGE_LIKE_STATUS, android.R.drawable.ic_menu_save)
        setNavButtonSelected(navLibraryButton, selectedPage == PAGE_LIBRARY, android.R.drawable.ic_menu_agenda)
        setNavButtonSelected(navSettingsButton, onSettingsArea, android.R.drawable.ic_menu_manage)
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
        if (kugouRecommendedLoading) {
            val loading = TextView(this)
            loading.text = getString(R.string.feedback_kugou_recommend_loading)
            loading.setTextColor(resources.getColor(R.color.text_secondary))
            loading.textSize = 16f
            loading.gravity = Gravity.CENTER
            loading.setPadding(dpToPx(12), dpToPx(22), dpToPx(12), dpToPx(22))
            loading.setBackgroundResource(R.drawable.row_recommend_idle)
            homeRecommendList.addView(
                loading,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }
        if (hasKugouSession()) {
            renderKugouRecommendedSongs()
            return
        }
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

    private fun renderKugouRecommendedSongs() {
        if (kugouRecommendedTracks.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.kugou_login_required_state)
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
        kugouRecommendedTracks.take(DEFAULT_HOME_QUEUE_SIZE).forEachIndexed { index, track ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.minimumHeight = dpToPx(70)
            row.setBackgroundResource(R.drawable.row_recommend_idle)
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
            title.text = track.title
            title.setTextColor(resources.getColor(R.color.text_primary))
            title.textSize = 17f

            val artist = TextView(this)
            artist.text = buildString {
                append(track.artist.ifBlank { getString(R.string.track_artist_server) })
                if (track.album.isNotBlank()) {
                    append(" · ")
                    append(track.album)
                }
            }
            artist.setTextColor(resources.getColor(R.color.text_secondary))
            artist.textSize = 14f

            textBlock.addView(title)
            textBlock.addView(artist)
            row.addView(textBlock)
            row.setOnClickListener {
                appendRuntimeLog("kugou recommend click index=$index hash=${track.playbackRef.hash}")
                playKugouTrack(track)
            }
            row.addView(buildLikeButton(track))

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
        var activeStartOffset = -1
        var activeEndOffset = -1
        homeLyricsLines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line.text)
            val end = builder.length
            val isActive = index == activeIndex
            if (isActive) {
                activeStartOffset = start
                activeEndOffset = end
            }
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
        centerHomeLyricsLine(
            activeStartOffset = activeStartOffset,
            activeEndOffset = activeEndOffset
        )
    }

    private fun centerHomeLyricsLine(
        activeStartOffset: Int,
        activeEndOffset: Int
    ) {
        homeLyricsText.post {
            val layout = homeLyricsText.layout ?: return@post
            val viewportHeight = homeLyricsScroll.height
            if (viewportHeight <= 0) {
                return@post
            }
            if (activeStartOffset < 0 || activeEndOffset <= activeStartOffset) {
                return@post
            }
            val safeStart = activeStartOffset.coerceIn(0, layout.text.length)
            val safeEndExclusive = activeEndOffset.coerceIn(safeStart + 1, layout.text.length)
            val startLine = layout.getLineForOffset(safeStart)
            val endLine = layout.getLineForOffset((safeEndExclusive - 1).coerceAtLeast(0))
            val lyricTop = layout.getLineTop(startLine)
            val lyricBottom = layout.getLineBottom(endLine)
            val lineHeight = (lyricBottom - lyricTop)
                .coerceAtLeast(dpToPx(20))
            val dynamicVerticalPadding = (viewportHeight / 2 - lineHeight / 2).coerceAtLeast(0)
            if (homeLyricsText.paddingTop != dynamicVerticalPadding || homeLyricsText.paddingBottom != dynamicVerticalPadding) {
                homeLyricsText.setPadding(
                    homeLyricsText.paddingLeft,
                    dynamicVerticalPadding,
                    homeLyricsText.paddingRight,
                    dynamicVerticalPadding
                )
                homeLyricsText.post {
                    centerHomeLyricsLine(
                        activeStartOffset = activeStartOffset,
                        activeEndOffset = activeEndOffset
                    )
                }
                return@post
            }

            val lineCenter = homeLyricsText.paddingTop + (lyricTop + lyricBottom) / 2
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

    private fun buildLikeButton(track: SourceTrack): ImageButton {
        return ImageButton(this).apply {
            setImageResource(android.R.drawable.btn_star_big_off)
            setBackgroundResource(R.drawable.button_nav_icon_inactive)
            setColorFilter(resources.getColor(R.color.text_secondary))
            contentDescription = getString(R.string.action_kugou_like)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener {
                requestLikeTrack(track)
            }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                leftMargin = dpToPx(10)
            }
        }
    }

    private fun requestLikeTrack(track: SourceTrack) {
        if (track.source != MusicSource.KUGOU) {
            return
        }
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        if (baseUrl.isEmpty() || session.isEmpty() || !hasKugouSession()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_kugou)) }
            requestKugouQrLogin()
            return
        }
        val pending = buildLikeStatusItem(track, LikeStatusStore.REMOTE_PENDING, LikeStatusStore.INGEST_BLOCKED, "")
        likeStatusStore.upsert(pending)
        renderLikeStatusPage()
        updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_like_pending)) }
        backgroundExecutor.execute {
            val result = kugouWebApiClient.addSongToLikeList(
                baseUrl = baseUrl,
                sessionKey = session,
                name = track.title,
                hash = track.playbackRef.hash,
                albumId = track.playbackRef.albumId,
                mixSongId = track.playbackRef.albumAudioId
            )
            runOnUiThread {
                if (result.sessionKey.isNotBlank()) {
                    kugouSessionKey = result.sessionKey
                }
                val success = result.httpCode in 200..299 && result.kgStatus == 1
                val item = buildLikeStatusItem(
                    track = track,
                    remoteStatus = if (success) LikeStatusStore.REMOTE_LIKED else LikeStatusStore.REMOTE_FAILED,
                    ingestStatus = LikeStatusStore.INGEST_BLOCKED,
                    failureReason = if (success) "" else "HTTP ${result.httpCode} status=${result.kgStatus ?: -1}"
                )
                likeStatusStore.upsert(item)
                renderLikeStatusPage()
                updateState {
                    it.copy(
                        feedbackText = if (success) {
                            getString(R.string.feedback_kugou_like_success)
                        } else {
                            getString(R.string.feedback_kugou_like_failed)
                        }
                    )
                }
            }
        }
    }

    private fun playKugouTrack(track: SourceTrack) {
        val baseUrl = resolveKugouBaseUrl()
        val session = kugouSessionKey.trim()
        val ref = track.playbackRef
        if (baseUrl.isEmpty() || session.isEmpty() || !hasKugouSession()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_need_kugou)) }
            requestKugouQrLogin()
            return
        }
        if (ref.hash.isBlank()) {
            updateState { it.copy(feedbackText = getString(R.string.feedback_kugou_play_url_failed)) }
            return
        }
        if (!ensureWifiConnectedForNetworkRequest(requestTag = "kugou_play_url", promptUser = true)) {
            return
        }
        val requestId = ++playbackRequestId
        pauseRequestedRequestId = -1
        stopDownloadController()
        cancelNetworkRecoveryRetry()
        updateState {
            it.copy(
                currentTrack = track.title,
                isPlaying = false,
                playbackStatusRes = R.string.status_paused,
                playPauseLabelRes = R.string.action_play,
                playPauseEnabled = false,
                nextEnabled = false,
                feedbackText = getString(R.string.feedback_kugou_play_url_loading)
            )
        }
        backgroundExecutor.execute {
            val resolved = kugouWebApiClient.getPlayUrl(
                baseUrl = baseUrl,
                sessionKey = session,
                hash = ref.hash,
                quality = ref.quality.ifBlank { "128" },
                albumId = ref.albumId,
                albumAudioId = ref.albumAudioId,
                freePart = false
            )
            runOnUiThread {
                if (requestId != playbackRequestId) {
                    return@runOnUiThread
                }
                if (resolved == null) {
                    updateState {
                        it.copy(
                            playPauseEnabled = false,
                            feedbackText = getString(R.string.feedback_kugou_play_url_failed)
                        )
                    }
                    showToast(R.string.toast_kugou_play_failed)
                    return@runOnUiThread
                }
                if (resolved.sessionKey.isNotBlank()) {
                    kugouSessionKey = resolved.sessionKey
                }
                prepareAndPlayKugouUrl(track, resolved.url, requestId)
            }
        }
    }

    private fun prepareAndPlayKugouUrl(track: SourceTrack, playUrl: String, requestId: Int) {
        try {
            releasePlayer()
            val engine = ensurePlaybackEngine(OkHttpClient())
            engine.prepare(
                source = Uri.parse(playUrl),
                callback = object : PlaybackEngineCallback {
                    override fun onPrepared() {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        observePlaybackAudioSession(source = "kugou_on_prepared")
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "play_success",
                            properties = mapOf(
                                "track_id" to track.sourceTrackId,
                                "source" to "kugou",
                                "decoder" to "exo_remote_url"
                            )
                        )
                        engine.play()
                        runOnUiThread {
                            updateState {
                                it.copy(
                                    currentTrack = track.title,
                                    isPlaying = true,
                                    playbackStatusRes = R.string.status_playing,
                                    playPauseLabelRes = R.string.action_pause,
                                    playPauseEnabled = true,
                                    nextEnabled = false,
                                    feedbackText = getString(R.string.feedback_kugou_play_success, track.title)
                                )
                            }
                        }
                    }

                    override fun onCompletion() {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        runOnUiThread {
                            updateState {
                                it.copy(
                                    isPlaying = false,
                                    playbackStatusRes = R.string.status_paused,
                                    playPauseLabelRes = R.string.action_play,
                                    feedbackText = getString(R.string.feedback_end_of_queue)
                                )
                            }
                        }
                    }

                    override fun onError(code: Int, detail: String) {
                        if (requestId != playbackRequestId) {
                            return
                        }
                        val category = categorizePlaybackError(code, detail)
                        PostHogTracker.capture(
                            context = applicationContext,
                            eventName = "playback_failed",
                            properties = mapOf(
                                "track_id" to track.sourceTrackId,
                                "source" to "kugou",
                                "stage" to "kugou_remote_url",
                                "error_code" to mapPlaybackErrorCode(code, category),
                                "error_summary" to detail.take(160)
                            ),
                            priority = PostHogTracker.Priority.HIGH
                        )
                        runOnUiThread {
                            updateState {
                                it.copy(
                                    isPlaying = false,
                                    playbackStatusRes = R.string.status_paused,
                                    playPauseLabelRes = R.string.action_play,
                                    playPauseEnabled = true,
                                    feedbackText = getString(R.string.feedback_kugou_play_failed)
                                )
                            }
                        }
                    }

                    override fun onBufferingStart() {
                        appendRuntimeLog("kugou buffering start track=${track.title}")
                    }

                    override fun onBufferingEnd() {
                        appendRuntimeLog("kugou buffering end track=${track.title}")
                    }
                }
            )
            updateState {
                it.copy(
                    feedbackText = getString(R.string.feedback_kugou_play_buffering, track.title)
                )
            }
        } catch (e: Exception) {
            appendRuntimeLog("kugou playback exception type=${e.javaClass.simpleName} msg=${e.message}")
            releasePlayer()
            updateState {
                it.copy(
                    isPlaying = false,
                    playbackStatusRes = R.string.status_paused,
                    playPauseLabelRes = R.string.action_play,
                    playPauseEnabled = true,
                    feedbackText = getString(R.string.feedback_kugou_play_failed)
                )
            }
        }
    }

    private fun buildLikeStatusItem(
        track: SourceTrack,
        remoteStatus: String,
        ingestStatus: String,
        failureReason: String
    ): LikeStatusItem {
        return LikeStatusItem(
            source = track.source,
            sourceTrackId = track.sourceTrackId,
            title = track.title,
            artist = track.artist,
            hash = track.playbackRef.hash,
            actionAtMs = System.currentTimeMillis(),
            remoteStatus = remoteStatus,
            ingestStatus = ingestStatus,
            failureReason = failureReason
        )
    }

    private fun renderLikeStatusPage() {
        if (!this::likeStatusList.isInitialized) {
            return
        }
        likeStatusList.removeAllViews()
        val items = likeStatusStore.load()
        if (items.isEmpty()) {
            val empty = TextView(this)
            empty.text = getString(R.string.like_status_empty)
            empty.setBackgroundResource(R.drawable.row_recommend_idle)
            empty.setTextColor(resources.getColor(R.color.text_secondary))
            empty.textSize = 16f
            empty.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
            likeStatusList.addView(empty)
            return
        }
        items.forEachIndexed { index, item ->
            val subtitle = buildString {
                append(item.source.name)
                append(" · ")
                append(item.artist.ifBlank { getString(R.string.track_artist_server) })
                append(" · ")
                append(item.remoteStatus)
                append(" · ")
                append(item.ingestStatus)
                if (item.failureReason.isNotBlank()) {
                    append(" · ")
                    append(item.failureReason)
                }
            }
            val row = buildSourceRow(
                titleText = item.title,
                subtitleText = subtitle,
                active = false
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dpToPx(8)
            }
            likeStatusList.addView(row, params)
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

    private fun showToastMessage(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, dpToPx(56))
        toast.show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun refreshDspPlayButtonIndicator() {
        if (!this::playPauseButton.isInitialized || !this::hiFiDspController.isInitialized) {
            return
        }
        val status = hiFiDspController.runtimeSnapshot().status
        if (status == lastPlayButtonDspStatus) {
            return
        }
        lastPlayButtonDspStatus = status
        playPauseButton.background = buildPlayPauseButtonBackground(resolveDspIndicatorStrokeColor(status))
    }

    private fun buildPlayPauseButtonBackground(strokeColor: Int): StateListDrawable {
        return StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_pressed),
                buildPlayPauseButtonShape(
                    startColor = Color.parseColor("#55BDE6"),
                    endColor = Color.parseColor("#2A9FD8"),
                    strokeColor = strokeColor
                )
            )
            addState(
                intArrayOf(),
                buildPlayPauseButtonShape(
                    startColor = Color.parseColor("#5FD3FF"),
                    endColor = Color.parseColor("#2A9FD8"),
                    strokeColor = strokeColor
                )
            )
        }
    }

    private fun buildPlayPauseButtonShape(startColor: Int, endColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(startColor, endColor)
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dpToPx(3), strokeColor)
        }
    }

    private fun resolveDspIndicatorStrokeColor(status: HiFiDspController.RuntimeStatus): Int {
        return when (status) {
            HiFiDspController.RuntimeStatus.ACTIVE -> Color.parseColor("#78F09A")
            HiFiDspController.RuntimeStatus.DEGRADED -> Color.parseColor("#FFE17A")
            HiFiDspController.RuntimeStatus.FAIL_OPEN -> Color.parseColor("#FF6B6B")
            HiFiDspController.RuntimeStatus.UNKNOWN,
            HiFiDspController.RuntimeStatus.DISABLED -> Color.parseColor("#AEB7C2")
        }
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
        refreshDspPlayButtonIndicator()
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
        observePlaybackAudioSession(source = "progress_tick")
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

    private fun observePlaybackAudioSession(source: String) {
        val engine = playbackEngine ?: return
        val sessionId = engine.audioSessionId()
        if (sessionId <= 0) {
            return
        }
        val changed = sessionId != lastObservedAudioSessionId
        if (changed) {
            lastObservedAudioSessionId = sessionId
            appendRuntimeLog("audio session observed id=$sessionId source=$source")
        }
        applySoundEffectConfig("session:$source")
    }

    private fun openSystemEqualizerSessionIfPossible(sessionId: Int, source: String) {
        if (!USE_SYSTEM_EQ_INHERIT_MODE || eqEnabled || sessionId <= 0) {
            return
        }
        if (systemEqSessionId == sessionId) {
            return
        }
        closeSystemEqualizerSession("reopen:$source")
        val intent = Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
        }
        val hasReceiver = runCatching {
            packageManager.queryBroadcastReceivers(intent, 0).isNotEmpty()
        }.getOrDefault(false)
        if (!hasReceiver) {
            appendRuntimeLog("system-eq open skip no-receiver session=$sessionId source=$source")
            maybeShowSystemEqUnavailableHint()
            return
        }
        runCatching {
            sendBroadcast(intent)
            systemEqSessionId = sessionId
            systemEqFallbackHintShown = false
            appendRuntimeLog("system-eq open session=$sessionId source=$source")
        }.onFailure { error ->
            appendRuntimeLog(
                "system-eq open fail session=$sessionId source=$source type=${error.javaClass.simpleName} msg=${error.message}"
            )
            maybeShowSystemEqUnavailableHint()
        }
    }

    private fun closeSystemEqualizerSession(source: String) {
        val openedSession = systemEqSessionId
        if (openedSession <= 0) {
            return
        }
        runCatching {
            val intent = Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, openedSession)
            }
            sendBroadcast(intent)
            appendRuntimeLog("system-eq close session=$openedSession source=$source")
        }.onFailure { error ->
            appendRuntimeLog(
                "system-eq close fail session=$openedSession source=$source type=${error.javaClass.simpleName} msg=${error.message}"
            )
        }
        systemEqSessionId = -1
    }

    private fun maybeShowSystemEqUnavailableHint() {
        val now = SystemClock.elapsedRealtime()
        if (systemEqFallbackHintShown && now - lastSystemEqUnavailableToastAtMs < SYSTEM_EQ_HINT_TOAST_INTERVAL_MS) {
            return
        }
        systemEqFallbackHintShown = true
        lastSystemEqUnavailableToastAtMs = now
        showToast(R.string.toast_eq_system_unavailable_use_app_eq)
        if (this::uiState.isInitialized) {
            updateState {
                it.copy(feedbackText = getString(R.string.feedback_eq_system_unavailable_use_app_eq))
            }
        }
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
                        observePlaybackAudioSession(source = "download_on_prepared")
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
                        observePlaybackAudioSession(source = "cache_on_prepared")
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
                    enforcePlaybackCacheLimit(
                        reason = "emby-cache-download",
                        keepFileName = file.name
                    )
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
            hiFiDspController = hiFiDspController,
            log = { message -> appendRuntimeLog(message) },
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
        lastObservedAudioSessionId = -1
        closeSystemEqualizerSession("main_activity_release_player")
        if (this::equalizerManager.isInitialized) {
            equalizerManager.onPlayerReleased(source = "main_activity_release_player")
        }
        if (this::hiFiDspController.isInitialized) {
            hiFiDspController.resetRuntimeState(source = "main_activity_release_player")
            refreshDspPlayButtonIndicator()
        }
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
            enforcePlaybackCacheLimit(
                reason = "cold-start-limit",
                keepFileName = keepTrackId?.let { "emby_${it}.cache" }
            )
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
            it.isFile && isPlaybackCacheFile(it)
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

    private fun enforcePlaybackCacheLimit(reason: String, keepFileName: String? = null): Boolean {
        val files = cacheDir?.listFiles()?.filter {
            it.isFile && isPlaybackCacheFile(it)
        }?.sortedBy { it.lastModified() }.orEmpty()
        var totalBytes = files.sumOfCompat { it.length().coerceAtLeast(0L) }
        var deleted = 0
        var allDeleted = true
        files.forEach { file ->
            if (totalBytes <= PLAYBACK_CACHE_MAX_BYTES) {
                return@forEach
            }
            if (!keepFileName.isNullOrEmpty() && file.name == keepFileName) {
                return@forEach
            }
            val size = file.length().coerceAtLeast(0L)
            val ok = runCatching { file.delete() }.getOrDefault(false)
            if (ok) {
                totalBytes = (totalBytes - size).coerceAtLeast(0L)
                deleted += 1
            } else {
                allDeleted = false
            }
        }
        appendRuntimeLog(
            "playback-cache guard reason=$reason files=${files.size} deleted=$deleted bytes=$totalBytes limit=$PLAYBACK_CACHE_MAX_BYTES success=$allDeleted"
        )
        refreshDownloadCacheInfoUi()
        return allDeleted
    }

    private fun isPlaybackCacheFile(file: File): Boolean {
        return file.name.endsWith(".cache") &&
            (file.name.startsWith("emby_") || file.name.startsWith("kugou_"))
    }

    private inline fun Iterable<File>.sumOfCompat(selector: (File) -> Long): Long {
        var total = 0L
        forEach { total += selector(it) }
        return total
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
            it.isFile && isPlaybackCacheFile(it)
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
        val hasNewSoundConfig = prefs.contains(KEY_SOUND_EFFECT_ENABLED) || prefs.contains(KEY_SOUND_EFFECT_MODE)
        soundEffectEnabled = if (hasNewSoundConfig) {
            prefs.getBoolean(KEY_SOUND_EFFECT_ENABLED, false)
        } else {
            prefs.getBoolean(KEY_EQ_ENABLED, false)
        }
        soundEffectMode = HiFiDspMode.fromCode(
            raw = prefs.getString(KEY_SOUND_EFFECT_MODE, null),
            fallback = HiFiDspMode.FIDELITY
        )
        if (soundEffectEnabled && soundEffectMode == HiFiDspMode.ORIGINAL) {
            soundEffectMode = HiFiDspMode.FIDELITY
        }
        applySoundEffectConfig("prefs_load")
        persistSoundEffectConfig()
        refreshEqualizerSettingsUi()
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

    private fun persistSoundEffectConfig() {
        getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_EFFECT_ENABLED, soundEffectEnabled)
            .putString(KEY_SOUND_EFFECT_MODE, soundEffectMode.code)
            .apply()
    }

    private fun applySoundEffectConfig(source: String) {
        if (!this::hiFiDspController.isInitialized) {
            return
        }
        val dspEnabled = soundEffectEnabled && soundEffectMode != HiFiDspMode.ORIGINAL
        hiFiDspController.update(
            enabled = dspEnabled,
            mode = soundEffectMode,
            source = source
        )
        refreshDspPlayButtonIndicator()
    }

    private fun refreshEqualizerSettingsUi() {
        if (!this::eqEnableSwitch.isInitialized) {
            return
        }
        suppressEqSwitchListener = true
        eqEnableSwitch.isChecked = soundEffectEnabled
        suppressEqSwitchListener = false
        eqEnableSwitch.isEnabled = true
        eqEntryValue.text = if (soundEffectEnabled) {
            getString(R.string.sound_entry_value_enabled_format, resolveSoundModeName(soundEffectMode))
        } else {
            getString(R.string.sound_entry_value_disabled)
        }
        eqNoteValue.text = getString(R.string.sound_note_fail_open)
        eqNoteValue.visibility = View.VISIBLE
        eqEntryRow.alpha = if (soundEffectEnabled) 1f else 0.88f
        if (selectedPage == PAGE_EQ) {
            renderEqualizerFullscreenPage()
        }
    }

    private fun renderEqualizerFullscreenPage() {
        if (!this::eqBandsContainer.isInitialized || !this::eqPresetsContainer.isInitialized) {
            return
        }
        renderSoundModeDetails()
        renderSoundModeButtons()
        refreshEqualizerFullscreenHeader()
    }

    private fun renderSoundModeDetails() {
        eqBandsContainer.removeAllViews()
        eqBandsContainer.orientation = LinearLayout.VERTICAL
        eqBandsContainer.gravity = Gravity.CENTER_VERTICAL
        eqBandsContainer.minimumWidth = dpToPx(360)
        eqBandsContainer.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))

        val title = TextView(this).apply {
            text = resolveSoundModeName(soundEffectMode)
            setTextColor(resources.getColor(R.color.text_primary))
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        eqBandsContainer.addView(
            title,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val description = TextView(this).apply {
            text = getString(resolveSoundModeDescriptionRes(soundEffectMode))
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 18f
            setLineSpacing(0f, 1.16f)
            setPadding(0, dpToPx(12), 0, 0)
        }
        eqBandsContainer.addView(
            description,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        val chain = TextView(this).apply {
            text = getString(R.string.sound_page_chain_note)
            setTextColor(resources.getColor(R.color.text_muted))
            textSize = 15f
            setLineSpacing(0f, 1.14f)
            setPadding(0, dpToPx(18), 0, 0)
        }
        eqBandsContainer.addView(
            chain,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )
    }

    private fun renderSoundModeButtons() {
        eqPresetsContainer.removeAllViews()
        SOUND_MODE_OPTIONS.forEachIndexed { index, option ->
            val active = (soundEffectEnabled && soundEffectMode == option.mode) ||
                (!soundEffectEnabled && option.mode == HiFiDspMode.ORIGINAL)
            val button = Button(this).apply {
                text = getString(option.titleRes)
                isAllCaps = false
                textSize = 16f
                minHeight = dpToPx(46)
                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                setBackgroundResource(if (active) R.drawable.button_eq_preset_active else R.drawable.button_eq_preset_inactive)
                setTextColor(resources.getColor(if (active) R.color.white else R.color.text_primary))
                setOnClickListener {
                    applySoundModeSelection(option.mode)
                }
            }
            eqPresetsContainer.addView(
                button,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (index > 0) {
                        topMargin = dpToPx(8)
                    }
                }
            )
        }
    }

    private fun refreshEqualizerFullscreenHeader() {
        if (!this::eqPageStateValue.isInitialized) {
            return
        }
        if (soundEffectEnabled) {
            eqPageStateValue.text = getString(R.string.sound_page_state_on_format, resolveSoundModeName(soundEffectMode))
            eqPageStateValue.setTextColor(resources.getColor(R.color.text_primary))
        } else {
            eqPageStateValue.text = getString(R.string.sound_page_state_off)
            eqPageStateValue.setTextColor(resources.getColor(R.color.text_secondary))
        }
    }

    private fun applySoundModeSelection(mode: HiFiDspMode) {
        soundEffectMode = mode
        soundEffectEnabled = mode != HiFiDspMode.ORIGINAL
        applySoundEffectConfig("sound_page_mode")
        persistSoundEffectConfig()
        refreshEqualizerSettingsUi()
        renderEqualizerFullscreenPage()
        updateState {
            it.copy(feedbackText = getString(R.string.feedback_sound_mode_changed, resolveSoundModeName(mode)))
        }
        showToastMessage(getString(R.string.toast_sound_mode_changed, resolveSoundModeName(mode)))
    }

    private fun resolveSoundModeName(mode: HiFiDspMode): String {
        return getString(resolveSoundModeTitleRes(mode))
    }

    @StringRes
    private fun resolveSoundModeTitleRes(mode: HiFiDspMode): Int {
        return SOUND_MODE_OPTIONS.firstOrNull { it.mode == mode }?.titleRes ?: R.string.sound_mode_fidelity
    }

    @StringRes
    private fun resolveSoundModeDescriptionRes(mode: HiFiDspMode): Int {
        return SOUND_MODE_OPTIONS.firstOrNull { it.mode == mode }?.descriptionRes
            ?: R.string.sound_mode_fidelity_desc
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
        if (selectedPage == PAGE_EQ) {
            switchPage(PAGE_SETTINGS)
            return
        }
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

    private class VerticalSeekBar(context: android.content.Context) : SeekBar(context) {
        var onUserProgressChanged: ((Int) -> Unit)? = null
        var onUserStopTracking: (() -> Unit)? = null

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(heightMeasureSpec, widthMeasureSpec)
            setMeasuredDimension(measuredHeight, measuredWidth)
        }

        override fun onDraw(canvas: Canvas) {
            canvas.rotate(-90f)
            canvas.translate(-height.toFloat(), 0f)
            super.onDraw(canvas)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) {
                return false
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(event.action != MotionEvent.ACTION_UP)
                    if (height <= 0) {
                        return true
                    }
                    val nextProgress = (max - (max * event.y / height).toInt()).coerceIn(0, max)
                    if (progress != nextProgress) {
                        progress = nextProgress
                        onUserProgressChanged?.invoke(nextProgress)
                    }
                    onSizeChanged(width, height, 0, 0)
                    if (event.action == MotionEvent.ACTION_UP) {
                        onUserStopTracking?.invoke()
                        performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
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
        const val KEY_EQ_ENABLED = "eq_enabled"
        const val KEY_EQ_PRESET_INDEX = "eq_preset_index"
        const val KEY_EQ_MODE = "eq_mode"
        const val KEY_EQ_CUSTOM_LEVELS = "eq_custom_levels"
        const val KEY_SOUND_EFFECT_ENABLED = "sound_effect_enabled"
        const val KEY_SOUND_EFFECT_MODE = "sound_effect_mode"
        const val USE_SYSTEM_EQ_INHERIT_MODE = false
        // Start playback once playable duration is >=3s and rebuffer with >=1s.
        const val LOAD_CONTROL_MIN_BUFFER_MS = 8_000
        const val LOAD_CONTROL_MAX_BUFFER_MS = 50_000
        const val LOAD_CONTROL_PLAYBACK_MS = 3_000
        const val LOAD_CONTROL_REBUFFER_MS = 1_000
        const val DOWNLOAD_WINDOW_SEC = 30L
        const val PLAYBACK_CACHE_MAX_BYTES = 100L * 1024L * 1024L
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
        const val SYSTEM_EQ_HINT_TOAST_INTERVAL_MS = 8_000L
        const val KUGOU_QR_POLL_INTERVAL_MS = 2_000L
        const val DISCOVER_TAG_PREVIEW_LIMIT = 12
        const val PAGE_HOME = 0
        const val PAGE_KUGOU_RADIO = 1
        const val PAGE_KUGOU_DISCOVER = 2
        const val PAGE_QUEUE = 3
        const val PAGE_LIKE_STATUS = 4
        const val PAGE_SETTINGS = 5
        const val PAGE_EQ = 6
        const val PAGE_LIBRARY = 7
        const val DEFAULT_HOME_QUEUE_SIZE = 20
        const val LIBRARY_PAGE_SIZE = 40
        const val LYRICS_CACHE_MAX_TRACKS = 32
        const val AUTO_PLAY_FIRST_TRACK_ON_EMBY_LOAD = true
        const val EQ_LEVEL_MIN_MB = -1200
        const val EQ_LEVEL_MAX_MB = 1200
        const val EQ_LEVEL_RANGE_MB = EQ_LEVEL_MAX_MB - EQ_LEVEL_MIN_MB
        const val FIXED_EQ_CUSTOM_PRESET_INDEX = 9
        val FIXED_EQ_FLAT_LEVELS = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val FIXED_EQ_BANDS = listOf(
            FixedEqBand(0, "31", 31),
            FixedEqBand(1, "62", 62),
            FixedEqBand(2, "125", 125),
            FixedEqBand(3, "250", 250),
            FixedEqBand(4, "500", 500),
            FixedEqBand(5, "1k", 1000),
            FixedEqBand(6, "2k", 2000),
            FixedEqBand(7, "4k", 4000),
            FixedEqBand(8, "8k", 8000),
            FixedEqBand(9, "16k", 16000)
        )
        val FIXED_EQ_PRESETS = listOf(
            FixedEqPreset("默认", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
            FixedEqPreset("流行", intArrayOf(-100, 200, 400, 500, 300, -100, -100, 200, 300, 400)),
            FixedEqPreset("摇滚", intArrayOf(500, 400, 250, 0, -150, -100, 150, 350, 500, 550)),
            FixedEqPreset("爵士", intArrayOf(300, 250, 150, 250, -100, -100, 0, 150, 300, 350)),
            FixedEqPreset("古典", intArrayOf(350, 250, 150, 0, 0, 0, 100, 200, 300, 400)),
            FixedEqPreset("舞曲", intArrayOf(650, 550, 350, 100, 0, -100, 0, 250, 450, 500)),
            FixedEqPreset("人声", intArrayOf(-250, -150, 0, 250, 450, 500, 350, 150, 0, -100)),
            FixedEqPreset("低音增强", intArrayOf(800, 700, 500, 250, 0, -100, -100, 0, 100, 150)),
            FixedEqPreset("高音增强", intArrayOf(-150, -100, 0, 0, 100, 250, 400, 550, 700, 800)),
            FixedEqPreset("自定义", intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        )
        val SOUND_MODE_OPTIONS = listOf(
            SoundModeOption(HiFiDspMode.ORIGINAL, R.string.sound_mode_original, R.string.sound_mode_original_desc),
            SoundModeOption(HiFiDspMode.FIDELITY, R.string.sound_mode_fidelity, R.string.sound_mode_fidelity_desc),
            SoundModeOption(HiFiDspMode.CLARITY, R.string.sound_mode_clarity, R.string.sound_mode_clarity_desc),
            SoundModeOption(HiFiDspMode.DYNAMIC, R.string.sound_mode_dynamic, R.string.sound_mode_dynamic_desc),
            SoundModeOption(HiFiDspMode.SOFT, R.string.sound_mode_soft, R.string.sound_mode_soft_desc)
        )
        @Volatile var downloadCacheClearedAtColdStart = false
    }
}
