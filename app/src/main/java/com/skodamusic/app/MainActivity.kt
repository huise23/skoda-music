package com.skodamusic.app

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
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
    private data class UiState(
        val currentTrack: String,
        @StringRes val playbackStatusRes: Int,
        @StringRes val playPauseLabelRes: Int,
        val feedbackText: String,
        val embyStatusText: String,
        val isPlaying: Boolean,
        val playPauseEnabled: Boolean,
        val nextEnabled: Boolean,
        val testEmbyEnabled: Boolean
    )

    private data class EmbyCredentials(
        val baseUrl: String,
        val username: String,
        val password: String
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

    private lateinit var embyBaseUrlInput: EditText
    private lateinit var embyUsernameInput: EditText
    private lateinit var embyPasswordInput: EditText
    private lateinit var embyStatusValue: TextView
    private lateinit var trackValue: TextView
    private lateinit var playbackValue: TextView
    private lateinit var actionFeedback: TextView
    private lateinit var runtimeLogPreview: TextView
    private lateinit var playPauseButton: Button
    private lateinit var nextButton: Button
    private lateinit var testEmbyButton: Button
    private lateinit var uiState: UiState

    private var loadedTracks: List<EmbyTrack> = emptyList()
    private var embySessionBaseUrl: String? = null
    private var embySessionUserId: String? = null
    private var embyAccessToken: String? = null
    private var currentTrackIndex: Int = 0
    private var mediaPlayer: MediaPlayer? = null
    private var playbackRequestId: Int = 0
    private val runtimeLogLines = mutableListOf<String>()
    private val runtimeLogLock = Any()
    private var runtimeLogDialog: Dialog? = null
    private var runtimeLogDialogText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        embyBaseUrlInput = findViewById(R.id.emby_base_url_input)
        embyUsernameInput = findViewById(R.id.emby_username_input)
        embyPasswordInput = findViewById(R.id.emby_password_input)
        embyStatusValue = findViewById(R.id.emby_status_value)
        trackValue = findViewById(R.id.track_value)
        playbackValue = findViewById(R.id.playback_value)
        actionFeedback = findViewById(R.id.action_feedback)
        runtimeLogPreview = findViewById(R.id.runtime_log_preview)
        playPauseButton = findViewById(R.id.btn_play_pause)
        nextButton = findViewById(R.id.btn_next)
        testEmbyButton = findViewById(R.id.btn_test_emby)
        loadSavedCredentials()

        uiState = UiState(
            currentTrack = getString(R.string.track_not_loaded),
            playbackStatusRes = R.string.status_paused,
            playPauseLabelRes = R.string.action_play,
            feedbackText = getString(R.string.feedback_need_emby),
            embyStatusText = getString(R.string.emby_status_not_tested),
            isPlaying = false,
            playPauseEnabled = false,
            nextEnabled = false,
            testEmbyEnabled = true
        )
        render(uiState)
        bindActions()
        appendRuntimeLog("app boot completed")
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

        playPauseButton.setOnClickListener {
            if (loadedTracks.isEmpty()) {
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
                return@setOnClickListener
            }

            if (uiState.isPlaying) {
                appendRuntimeLog("ui click play/pause -> pause")
                pausePlayback()
                Toast.makeText(this, R.string.toast_paused, Toast.LENGTH_SHORT).show()
            } else {
                appendRuntimeLog("ui click play/pause -> play")
                startOrResumePlayback()
                Toast.makeText(this, R.string.toast_playing, Toast.LENGTH_SHORT).show()
            }
        }

        nextButton.setOnClickListener {
            appendRuntimeLog("ui click next")
            if (loadedTracks.isEmpty()) {
                updateState { it.copy(feedbackText = getString(R.string.feedback_need_emby)) }
                return@setOnClickListener
            }
            if (!NativePlaybackBridge.isAvailable()) {
                updateState {
                    it.copy(
                        feedbackText = getString(R.string.feedback_native_unavailable),
                        nextEnabled = false
                    )
                }
                Toast.makeText(this, R.string.toast_emby_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val wasPlaying = uiState.isPlaying
            val nextTitle = NativePlaybackBridge.nextTitle()
            if (nextTitle == null) {
                updateState {
                    it.copy(
                        feedbackText = getString(R.string.feedback_end_of_queue),
                        nextEnabled = false
                    )
                }
                Toast.makeText(this, R.string.toast_end_of_queue, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            updateState {
                it.copy(
                    currentTrack = nextTitle,
                    feedbackText = getString(R.string.feedback_next_pressed),
                    nextEnabled = NativePlaybackBridge.hasNext()
                )
            }
            Toast.makeText(this, R.string.toast_next, Toast.LENGTH_SHORT).show()
            currentTrackIndex = (currentTrackIndex + 1).coerceAtMost(loadedTracks.lastIndex)
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
            persistCredentials(credentials)

            if (credentials.baseUrl.isEmpty()) {
                updateState {
                    it.copy(
                        embyStatusText = getString(R.string.emby_status_failed),
                        feedbackText = getString(R.string.feedback_emby_missing)
                    )
                }
                Toast.makeText(this, R.string.toast_emby_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (credentials.username.isEmpty() || credentials.password.isEmpty()) {
                updateState {
                    it.copy(
                        embyStatusText = getString(R.string.emby_status_failed),
                        feedbackText = getString(R.string.feedback_emby_need_auth)
                    )
                }
                Toast.makeText(this, R.string.toast_emby_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            requestTracksFromEmby(credentials)
        }
    }

    private fun requestTracksFromEmby(credentials: EmbyCredentials) {
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
                if (result.success) {
                    appendRuntimeLog("emby load success tracks=${result.tracks.size}")
                    loadedTracks = result.tracks
                    embySessionBaseUrl = result.embyBase
                    embySessionUserId = result.embyUserId
                    embyAccessToken = result.accessToken
                    currentTrackIndex = 0
                    playbackRequestId += 1
                    releasePlayer()
                    val nativeReady = NativePlaybackBridge.isAvailable()
                    val firstTrack = if (nativeReady) {
                        NativePlaybackBridge.initializeQueue(loadedTracks.map { it.title })
                    } else {
                        null
                    }
                    if (!nativeReady || firstTrack == null) {
                        updateState {
                            it.copy(
                                currentTrack = getString(R.string.track_not_loaded),
                                playbackStatusRes = R.string.status_paused,
                                playPauseLabelRes = R.string.action_play,
                                isPlaying = false,
                                playPauseEnabled = false,
                                nextEnabled = false,
                                testEmbyEnabled = true,
                                embyStatusText = getString(R.string.emby_status_failed),
                                feedbackText = result.feedbackText + "\n- native playback unavailable"
                            )
                        }
                        embySessionBaseUrl = null
                        embySessionUserId = null
                        embyAccessToken = null
                        loadedTracks = emptyList()
                        currentTrackIndex = 0
                        playbackRequestId += 1
                        releasePlayer()
                        Toast.makeText(this, R.string.toast_emby_failed, Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    updateState {
                        it.copy(
                            currentTrack = firstTrack,
                            playbackStatusRes = R.string.status_paused,
                            playPauseLabelRes = R.string.action_play,
                            isPlaying = false,
                            playPauseEnabled = true,
                            nextEnabled = NativePlaybackBridge.hasNext(),
                            testEmbyEnabled = true,
                            embyStatusText = result.statusText,
                            feedbackText = result.feedbackText
                        )
                    }
                    Toast.makeText(this, R.string.toast_emby_success, Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, R.string.toast_emby_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun startOrResumePlayback() {
        val existing = mediaPlayer
        if (existing != null) {
            try {
                existing.start()
                updateState {
                    it.copy(
                        isPlaying = true,
                        playbackStatusRes = R.string.status_playing,
                        playPauseLabelRes = R.string.action_pause,
                        feedbackText = getString(R.string.feedback_play_pressed)
                    )
                }
                return
            } catch (_: Exception) {
                releasePlayer()
            }
        }
        playTrackAtCurrentIndex()
    }

    private fun pausePlayback() {
        playbackRequestId += 1
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {
            releasePlayer()
        }
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
        val streamUrl = buildEmbyStreamUrl(base, track.id, token)
        val requestId = ++playbackRequestId
        val streamPrepared = AtomicBoolean(false)
        val fallbackTriggered = AtomicBoolean(false)
        appendRuntimeLog("play request track=${track.title} streamUrl=$streamUrl requestId=$requestId")
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
            val player = MediaPlayer()
            mediaPlayer = player
            player.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player.setOnPreparedListener { prepared ->
                if (requestId != playbackRequestId) {
                    prepared.release()
                    return@setOnPreparedListener
                }
                streamPrepared.set(true)
                appendRuntimeLog("play prepared requestId=$requestId track=${track.title}")
                prepared.start()
                runOnUiThread {
                    updateState {
                        it.copy(
                            isPlaying = true,
                            playbackStatusRes = R.string.status_playing,
                            playPauseLabelRes = R.string.action_pause,
                            feedbackText = "Action feedback: streaming ${track.title}"
                        )
                    }
                }
            }
            player.setOnCompletionListener {
                if (requestId != playbackRequestId) {
                    return@setOnCompletionListener
                }
                runOnUiThread {
                    if (NativePlaybackBridge.hasNext()) {
                        NativePlaybackBridge.nextTitle()
                        currentTrackIndex = (currentTrackIndex + 1).coerceAtMost(loadedTracks.lastIndex)
                        updateState { s ->
                            s.copy(
                                currentTrack = loadedTracks[currentTrackIndex].title,
                                nextEnabled = NativePlaybackBridge.hasNext()
                            )
                        }
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
                    }
                }
            }
            player.setOnErrorListener { _, what, extra ->
                if (requestId != playbackRequestId) {
                    return@setOnErrorListener true
                }
                appendRuntimeLog("play error requestId=$requestId what=$what extra=$extra")
                triggerCachedFallback("Action feedback: stream error ($what/$extra), trying cached file")
                true
            }
            player.setOnInfoListener { _, what, _ ->
                if (requestId != playbackRequestId) {
                    return@setOnInfoListener true
                }
                when (what) {
                    MediaPlayer.MEDIA_INFO_BUFFERING_START -> {
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
                        true
                    }
                    MediaPlayer.MEDIA_INFO_BUFFERING_END -> {
                        appendRuntimeLog("play buffering end requestId=$requestId track=${track.title}")
                        runOnUiThread {
                            updateState {
                                it.copy(
                                    isPlaying = true,
                                    playbackStatusRes = R.string.status_playing,
                                    playPauseLabelRes = R.string.action_pause,
                                    feedbackText = "Action feedback: streaming ${track.title}"
                                )
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            appendRuntimeLog("play stream mode=request-only-url requestId=$requestId")
            player.setDataSource(this, Uri.parse(streamUrl))
            player.prepareAsync()
            appendRuntimeLog("play prepareAsync requestId=$requestId")
            updateState {
                it.copy(
                    isPlaying = true,
                    playbackStatusRes = R.string.status_playing,
                    playPauseLabelRes = R.string.action_pause,
                    feedbackText = "Action feedback: starting stream ${track.title}"
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
                appendRuntimeLog("play timeout requestId=$requestId after=${STREAM_PREPARE_TIMEOUT_MS}ms")
                triggerCachedFallback("Action feedback: stream timeout, trying cached file")
            }.start()
        } catch (e: Exception) {
            appendRuntimeLog("play setup exception requestId=$requestId type=${e.javaClass.simpleName} msg=${e.message}")
            triggerCachedFallback(
                "Action feedback: stream setup failed (${e.javaClass.simpleName}), trying cached file"
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
                    val player = MediaPlayer()
                    mediaPlayer = player
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC)
                    player.setDataSource(cacheFile.absolutePath)
                    player.setOnPreparedListener { prepared ->
                        if (requestId != playbackRequestId) {
                            prepared.release()
                            return@setOnPreparedListener
                        }
                        appendRuntimeLog("cache playback prepared requestId=$requestId")
                        prepared.start()
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
                    player.setOnCompletionListener {
                        if (requestId != playbackRequestId) {
                            return@setOnCompletionListener
                        }
                        runOnUiThread {
                            if (NativePlaybackBridge.hasNext()) {
                                NativePlaybackBridge.nextTitle()
                                currentTrackIndex = (currentTrackIndex + 1).coerceAtMost(loadedTracks.lastIndex)
                                updateState { s ->
                                    s.copy(
                                        currentTrack = loadedTracks[currentTrackIndex].title,
                                        nextEnabled = NativePlaybackBridge.hasNext()
                                    )
                                }
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
                            }
                        }
                    }
                    player.prepareAsync()
                    appendRuntimeLog("cache playback prepareAsync requestId=$requestId")
                    updateState {
                        it.copy(
                            isPlaying = true,
                            playbackStatusRes = R.string.status_playing,
                            playPauseLabelRes = R.string.action_pause,
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
            buildEmbyDownloadUrl(embyBase, track.id, token),
            buildEmbyStreamUrl(embyBase, track.id, token),
            buildEmbyTranscodeStreamUrl(embyBase, track.id, token)
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

    private fun releasePlayer() {
        val player = mediaPlayer ?: return
        try {
            player.stop()
        } catch (_: Exception) {
        }
        try {
            player.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
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

    private fun buildEmbyStreamUrl(
        embyBase: String,
        trackId: String,
        token: String
    ): String {
        val params = mutableListOf(
            "api_key=${urlEncode(token)}",
            "static=true"
        )
        return "$embyBase/Audio/${urlEncode(trackId)}/stream?${params.joinToString("&")}"
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

    private fun buildEmbyTranscodeStreamUrl(
        embyBase: String,
        trackId: String,
        token: String
    ): String {
        val params = mutableListOf(
            "api_key=${urlEncode(token)}",
            "static=true"
        )
        params.addAll(commonEmbyQueryParams())
        return "$embyBase/Audio/${urlEncode(trackId)}/stream.mp3?${params.joinToString("&")}"
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

    private fun loadSavedCredentials() {
        val prefs = getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
        embyBaseUrlInput.setText(prefs.getString(KEY_BASE_URL, "").orEmpty())
        embyUsernameInput.setText(prefs.getString(KEY_USERNAME, "").orEmpty())
        embyPasswordInput.setText(prefs.getString(KEY_PASSWORD, "").orEmpty())
    }

    private fun persistCredentials(credentials: EmbyCredentials) {
        getSharedPreferences(PREFS_EMBY, MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, credentials.baseUrl)
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
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
        val preview = if (snapshot.isBlank()) {
            getString(R.string.runtime_logs_empty)
        } else {
            snapshot
                .split('\n')
                .takeLast(RUNTIME_LOG_PREVIEW_LINES)
                .joinToString("\n")
        }
        runOnUiThread {
            runtimeLogPreview.text = preview
            runtimeLogDialogText?.text = if (snapshot.isBlank()) {
                getString(R.string.runtime_logs_empty)
            } else {
                snapshot
            }
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

    private fun showRuntimeLogsFullscreen() {
        if (runtimeLogDialog?.isShowing == true) {
            return
        }
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_runtime_logs)
        val fullText = dialog.findViewById<TextView>(R.id.runtime_log_fullscreen_text)
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
        trackValue.text = state.currentTrack
        playbackValue.setText(state.playbackStatusRes)
        actionFeedback.text = state.feedbackText
        playPauseButton.setText(state.playPauseLabelRes)
        playPauseButton.isEnabled = state.playPauseEnabled
        nextButton.isEnabled = state.nextEnabled
        testEmbyButton.isEnabled = state.testEmbyEnabled
    }

    private companion object {
        const val LOG_TAG = "SkodaMusicEmby"
        const val PREFS_EMBY = "emby_credentials"
        const val KEY_BASE_URL = "base_url"
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val EMBY_QUERY_CLIENT = "Emby Web"
        const val EMBY_QUERY_DEVICE_NAME = "Google Chrome Windows"
        const val EMBY_QUERY_DEVICE_ID = "6ec2a066-66a2-49af-bd97-6302ee307eaf"
        const val EMBY_QUERY_CLIENT_VERSION = "4.9.1.90"
        const val EMBY_QUERY_LANGUAGE = "zh-cn"
        const val STREAM_PREPARE_TIMEOUT_MS = 30_000L
        const val MAX_RUNTIME_LOG_LINES = 800
        const val RUNTIME_LOG_PREVIEW_LINES = 2
    }
}
