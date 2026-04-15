package com.skodamusic.app

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

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
        val userId: String,
        val token: String,
        val apiKey: String,
        val username: String,
        val password: String
    )

    private data class ResolvedAuth(
        val mode: String,
        val userId: String,
        val token: String?,
        val apiKey: String?
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
        val tracks: List<String> = emptyList()
    )

    private lateinit var embyBaseUrlInput: EditText
    private lateinit var embyUserIdInput: EditText
    private lateinit var embyTokenInput: EditText
    private lateinit var embyApiKeyInput: EditText
    private lateinit var embyUsernameInput: EditText
    private lateinit var embyPasswordInput: EditText
    private lateinit var embyStatusValue: TextView
    private lateinit var trackValue: TextView
    private lateinit var playbackValue: TextView
    private lateinit var actionFeedback: TextView
    private lateinit var playPauseButton: Button
    private lateinit var nextButton: Button
    private lateinit var testEmbyButton: Button
    private lateinit var uiState: UiState

    private var loadedTracks: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        embyBaseUrlInput = findViewById(R.id.emby_base_url_input)
        embyUserIdInput = findViewById(R.id.emby_user_id_input)
        embyTokenInput = findViewById(R.id.emby_token_input)
        embyApiKeyInput = findViewById(R.id.emby_api_key_input)
        embyUsernameInput = findViewById(R.id.emby_username_input)
        embyPasswordInput = findViewById(R.id.emby_password_input)
        embyStatusValue = findViewById(R.id.emby_status_value)
        trackValue = findViewById(R.id.track_value)
        playbackValue = findViewById(R.id.playback_value)
        actionFeedback = findViewById(R.id.action_feedback)
        playPauseButton = findViewById(R.id.btn_play_pause)
        nextButton = findViewById(R.id.btn_next)
        testEmbyButton = findViewById(R.id.btn_test_emby)

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
    }

    private fun bindActions() {
        playPauseButton.setOnClickListener {
            if (loadedTracks.isEmpty()) {
                updateState {
                    it.copy(
                        feedbackText = getString(R.string.feedback_need_emby)
                    )
                }
                return@setOnClickListener
            }

            val nextPlaying = !uiState.isPlaying
            updateState {
                it.copy(
                    isPlaying = nextPlaying,
                    playbackStatusRes = if (nextPlaying) R.string.status_playing else R.string.status_paused,
                    playPauseLabelRes = if (nextPlaying) R.string.action_pause else R.string.action_play,
                    feedbackText = getString(R.string.feedback_play_pressed)
                )
            }
            if (nextPlaying) {
                Toast.makeText(this, R.string.toast_playing, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.toast_paused, Toast.LENGTH_SHORT).show()
            }
        }

        nextButton.setOnClickListener {
            if (loadedTracks.isEmpty()) {
                updateState {
                    it.copy(
                        feedbackText = getString(R.string.feedback_need_emby)
                    )
                }
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
        }

        testEmbyButton.setOnClickListener {
            val credentials = EmbyCredentials(
                baseUrl = embyBaseUrlInput.text.toString().trim(),
                userId = embyUserIdInput.text.toString().trim(),
                token = embyTokenInput.text.toString().trim(),
                apiKey = embyApiKeyInput.text.toString().trim(),
                username = embyUsernameInput.text.toString().trim(),
                password = embyPasswordInput.text.toString().trim()
            )

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

            val hasToken = credentials.token.isNotEmpty()
            val hasApiKey = credentials.apiKey.isNotEmpty()
            val hasUsernamePassword =
                credentials.username.isNotEmpty() && credentials.password.isNotEmpty()
            val hasPartialLogin =
                credentials.username.isNotEmpty().xor(credentials.password.isNotEmpty())

            if (hasPartialLogin) {
                updateState {
                    it.copy(
                        embyStatusText = getString(R.string.emby_status_failed),
                        feedbackText = "Action feedback: username and password must both be set"
                    )
                }
                Toast.makeText(this, R.string.toast_emby_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!hasToken && !hasApiKey && !hasUsernamePassword) {
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
                    loadedTracks = result.tracks
                    val nativeReady = NativePlaybackBridge.isAvailable()
                    val firstTrack = if (nativeReady) {
                        NativePlaybackBridge.initializeQueue(loadedTracks)
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
                    loadedTracks = emptyList()
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

    private fun fetchTracksFromEmby(credentials: EmbyCredentials): EmbyLoadResult {
        val logs = mutableListOf<String>()
        val logger: (String) -> Unit = { message ->
            logs.add(message)
            Log.d(LOG_TAG, message)
        }

        return try {
            val embyBase = normalizeEmbyBase(credentials.baseUrl)
            logger("base=$embyBase")

            val auth = resolveAuth(embyBase, credentials, logger)
                ?: return failedResult(
                    headline = "Action feedback: Emby authentication failed",
                    logs = logs
                )

            logger("auth=${auth.mode}, user=${shortId(auth.userId)}")

            val endpoint = buildEmbyItemsUrl(embyBase, auth.userId, auth.apiKey)
            val response = executeGet(
                endpoint = endpoint,
                token = auth.token,
                requestLabel = "GET /Users/{id}/Items",
                log = logger
            )

            if (response.code !in 200..299) {
                logger("items-request failed")
                return failedResult(
                    headline = "Action feedback: Emby items request failed (HTTP ${response.code})",
                    logs = logs
                )
            }

            val tracks = parseTrackNames(response.payload)
            logger("tracks=${tracks.size}")
            if (tracks.isEmpty()) {
                return failedResult(
                    headline = "Action feedback: no audio items returned",
                    logs = logs
                )
            }

            EmbyLoadResult(
                success = true,
                statusText = getString(R.string.emby_status_connected) + " (${tracks.size})",
                feedbackText = formatFeedback(
                    headline = getString(R.string.feedback_emby_connected),
                    logs = logs
                ),
                tracks = tracks
            )
        } catch (e: Exception) {
            logger("exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            failedResult(
                headline = getString(R.string.feedback_emby_failed),
                logs = logs
            )
        }
    }

    private fun resolveAuth(
        embyBase: String,
        credentials: EmbyCredentials,
        log: (String) -> Unit
    ): ResolvedAuth? {
        val explicitUserId = credentials.userId.trim()
        if (credentials.token.isNotEmpty()) {
            log("auth-mode=token")
            val resolvedUserId = if (explicitUserId.isNotEmpty()) {
                explicitUserId
            } else {
                queryUserId(embyBase, token = credentials.token, apiKey = null, log = log)
            } ?: return null
            return ResolvedAuth(
                mode = "token",
                userId = resolvedUserId,
                token = credentials.token,
                apiKey = null
            )
        }

        if (credentials.apiKey.isNotEmpty()) {
            log("auth-mode=api-key")
            val resolvedUserId = if (explicitUserId.isNotEmpty()) {
                explicitUserId
            } else {
                queryUserId(embyBase, token = null, apiKey = credentials.apiKey, log = log)
            } ?: return null
            return ResolvedAuth(
                mode = "api-key",
                userId = resolvedUserId,
                token = null,
                apiKey = credentials.apiKey
            )
        }

        if (credentials.username.isNotEmpty() && credentials.password.isNotEmpty()) {
            log("auth-mode=username-password")
            val auth = authenticateByName(
                embyBase = embyBase,
                username = credentials.username,
                password = credentials.password,
                log = log
            ) ?: return null
            return ResolvedAuth(
                mode = "username-password",
                userId = auth.userId,
                token = auth.accessToken,
                apiKey = null
            )
        }

        return null
    }

    private fun queryUserId(
        embyBase: String,
        token: String?,
        apiKey: String?,
        log: (String) -> Unit
    ): String? {
        val endpoint = buildUsersMeUrl(embyBase, apiKey)
        val response = executeGet(
            endpoint = endpoint,
            token = token,
            requestLabel = "GET /Users/Me",
            log = log
        )
        if (response.code !in 200..299) {
            log("users-me failed")
            return null
        }
        return try {
            val userId = JSONObject(response.payload).optString("Id").trim()
            if (userId.isEmpty()) {
                log("users-me missing Id")
                null
            } else {
                log("users-me resolved user=${shortId(userId)}")
                userId
            }
        } catch (e: Exception) {
            log("users-me parse error: ${e.javaClass.simpleName}")
            null
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
            val endpoint = "$embyBase/Users/AuthenticateByName"
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 10000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Emby-Client", EMBY_CLIENT_NAME)
                setRequestProperty("X-Emby-Device", EMBY_DEVICE_NAME)
                setRequestProperty("X-Emby-Device-Id", EMBY_DEVICE_ID)
                setRequestProperty("X-Emby-Version", EMBY_CLIENT_VERSION)
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
            log("POST /Users/AuthenticateByName -> HTTP $code")

            if (code !in 200..299) {
                log("auth body=${previewPayload(payload)}")
                return null
            }

            val root = JSONObject(payload)
            val token = root.optString("AccessToken").trim()
            val userId = root.optJSONObject("User")?.optString("Id")?.trim().orEmpty()
            if (token.isEmpty() || userId.isEmpty()) {
                log("auth response missing token/user")
                return null
            }
            log("auth success user=${shortId(userId)}")
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
        token: String?,
        requestLabel: String,
        log: (String) -> Unit
    ): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Emby-Client", EMBY_CLIENT_NAME)
                setRequestProperty("X-Emby-Device", EMBY_DEVICE_NAME)
                setRequestProperty("X-Emby-Device-Id", EMBY_DEVICE_ID)
                setRequestProperty("X-Emby-Version", EMBY_CLIENT_VERSION)
                if (!token.isNullOrEmpty()) {
                    setRequestProperty("X-Emby-Token", token)
                }
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

    private fun buildUsersMeUrl(embyBase: String, apiKey: String?): String {
        if (apiKey.isNullOrBlank()) {
            return "$embyBase/Users/Me"
        }
        return "$embyBase/Users/Me?api_key=${urlEncode(apiKey)}"
    }

    private fun buildEmbyItemsUrl(embyBase: String, userId: String, apiKey: String?): String {
        val query = mutableListOf(
            "IncludeItemTypes=Audio",
            "Recursive=true",
            "SortBy=SortName",
            "Limit=50"
        )
        if (!apiKey.isNullOrBlank()) {
            query.add("api_key=${urlEncode(apiKey)}")
        }
        return "$embyBase/Users/${urlEncode(userId)}/Items?${query.joinToString("&")}"
    }

    private fun parseTrackNames(jsonText: String): List<String> {
        val out = mutableListOf<String>()
        val root = JSONObject(jsonText)
        val items = root.optJSONArray("Items") ?: return out
        for (i in 0 until items.length()) {
            val name = items.optJSONObject(i)?.optString("Name")?.trim().orEmpty()
            if (name.isNotEmpty()) {
                out.add(name)
            }
        }
        return out
    }

    private fun readAll(stream: InputStream?): String {
        if (stream == null) {
            return ""
        }
        BufferedReader(InputStreamReader(stream)).use { reader ->
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
        return if (value.length <= 8) {
            value
        } else {
            value.take(4) + "..." + value.takeLast(4)
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, "UTF-8")

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
        const val EMBY_CLIENT_NAME = "SkodaMusic"
        const val EMBY_DEVICE_NAME = "AndroidShell"
        const val EMBY_DEVICE_ID = "skoda-music-shell"
        const val EMBY_CLIENT_VERSION = "0.1.0"
    }
}
