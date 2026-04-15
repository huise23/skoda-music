package com.skodamusic.app

import android.os.Bundle
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

    private data class EmbyLoadResult(
        val success: Boolean,
        val statusText: String,
        val feedbackText: String,
        val tracks: List<String> = emptyList()
    )

    private lateinit var embyBaseUrlInput: EditText
    private lateinit var embyUserIdInput: EditText
    private lateinit var embyTokenInput: EditText
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
            val baseUrl = embyBaseUrlInput.text.toString().trim()
            val userId = embyUserIdInput.text.toString().trim()
            val token = embyTokenInput.text.toString().trim()
            if (baseUrl.isEmpty() || userId.isEmpty() || token.isEmpty()) {
                updateState {
                    it.copy(
                        embyStatusText = getString(R.string.emby_status_failed),
                        feedbackText = getString(R.string.feedback_emby_missing)
                    )
                }
                Toast.makeText(this, R.string.toast_emby_failed, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestTracksFromEmby(baseUrl, userId, token)
        }
    }

    private fun requestTracksFromEmby(baseUrl: String, userId: String, token: String) {
        updateState {
            it.copy(
                testEmbyEnabled = false,
                embyStatusText = getString(R.string.emby_status_testing),
                feedbackText = getString(R.string.feedback_emby_testing)
            )
        }

        Thread {
            val result = fetchTracksFromEmby(baseUrl, userId, token)
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
                                feedbackText = getString(R.string.feedback_native_unavailable)
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

    private fun fetchTracksFromEmby(baseUrl: String, userId: String, token: String): EmbyLoadResult {
        var connection: HttpURLConnection? = null
        return try {
            val endpoint = buildEmbyItemsUrl(baseUrl, userId)
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Emby-Token", token)
                setRequestProperty("X-Emby-Client", "SkodaMusic")
                setRequestProperty("X-Emby-Device", "AndroidShell")
                setRequestProperty("X-Emby-Device-Id", "skoda-music-shell")
                setRequestProperty("X-Emby-Version", "0.1.0")
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val payload = readAll(stream)
            if (code !in 200..299) {
                EmbyLoadResult(
                    success = false,
                    statusText = getString(R.string.emby_status_failed),
                    feedbackText = "Action feedback: Emby HTTP $code"
                )
            } else {
                val tracks = parseTrackNames(payload)
                if (tracks.isEmpty()) {
                    EmbyLoadResult(
                        success = false,
                        statusText = getString(R.string.emby_status_failed),
                        feedbackText = getString(R.string.feedback_emby_failed)
                    )
                } else {
                    EmbyLoadResult(
                        success = true,
                        statusText = getString(R.string.emby_status_connected) + " (${tracks.size})",
                        feedbackText = getString(R.string.feedback_emby_connected),
                        tracks = tracks
                    )
                }
            }
        } catch (_: Exception) {
            EmbyLoadResult(
                success = false,
                statusText = getString(R.string.emby_status_failed),
                feedbackText = getString(R.string.feedback_emby_failed)
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun buildEmbyItemsUrl(baseUrl: String, userId: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val prefix = if (normalized.endsWith("/emby", ignoreCase = true)) {
            normalized
        } else {
            "$normalized/emby"
        }
        val encodedUserId = URLEncoder.encode(userId, "UTF-8")
        return "$prefix/Users/$encodedUserId/Items?IncludeItemTypes=Audio&Recursive=true&SortBy=SortName&Limit=50"
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
}
