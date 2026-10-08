package com.skodamusic.app.bridge

import android.content.Context
import android.content.Intent
import android.util.Log
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackControlBus
import com.skodamusic.app.playback.PlaybackStateStore
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight, zero-dependency embedded HTTP server running on head unit.
 * Listens on port 8088 for incoming control commands from phone (Yiyu App).
 * Fully compatible with Android API 17 (Jelly Bean 4.2.2).
 */
class CarBridgeServer(
    private val context: Context,
    private val port: Int = 8088
) {
    companion object {
        private const val TAG = "CarBridgeServer"
        const val ACTION_VOICE_SEARCH_PLAY = "com.skodamusic.app.action.VOICE_SEARCH_PLAY"
        const val EXTRA_SEARCH_KEYWORD = "extra_search_keyword"
    }

    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val threadPool = Executors.newFixedThreadPool(4)
    private val stateStore = PlaybackStateStore(context)

    fun start() {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "CarBridgeServer already running")
            return
        }

        Executors.newSingleThreadExecutor().execute {
            try {
                val server = ServerSocket(port)
                serverSocket = server
                Log.i(TAG, "CarBridgeServer started on port $port")

                while (isRunning.get() && !server.isClosed) {
                    try {
                        val client = server.accept()
                        threadPool.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (!isRunning.get()) break
                        Log.e(TAG, "Error accepting client: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CarBridgeServer failed to bind port $port: ${e.message}", e)
                isRunning.set(false)
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        threadPool.shutdownNow()
        Log.i(TAG, "CarBridgeServer stopped")
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]

            // Read headers to get content-length
            var contentLength = 0
            var headerLine = reader.readLine()
            while (!headerLine.isNullOrBlank()) {
                val headerLower = headerLine.lowercase()
                if (headerLower.startsWith("content-length:")) {
                    contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                }
                headerLine = reader.readLine()
            }

            // Read body if present
            var body = ""
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val r = reader.read(buf, readTotal, contentLength - readTotal)
                    if (r == -1) break
                    readTotal += r
                }
                body = String(buf, 0, readTotal)
            }

            val out = socket.getOutputStream()
            val (statusCode, responseJson) = processRequest(method, path, body)
            sendResponse(out, statusCode, responseJson)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun processRequest(method: String, path: String, body: String): Pair<Int, JSONObject> {
        return when {
            path.startsWith("/api/ping") -> {
                200 to JSONObject().apply {
                    put("status", "ok")
                    put("app", "skoda-music")
                    put("api", 17)
                }
            }
            path.startsWith("/api/status") && method == "GET" -> {
                val snapshot = stateStore.readSnapshot()
                200 to JSONObject().apply {
                    put("status", "ok")
                    put("isPlaying", snapshot.isPlaying)
                    put("trackTitle", snapshot.trackTitle)
                    put("trackId", snapshot.trackId)
                    put("positionMs", snapshot.positionMs)
                    put("durationMs", snapshot.durationMs)
                }
            }
            path.startsWith("/api/music/control") && method == "POST" -> {
                handleMusicControl(body)
            }
            path.startsWith("/api/navi") && method == "POST" -> {
                handleNavi(body)
            }
            else -> {
                404 to JSONObject().apply {
                    put("error", "Not Found")
                    put("path", path)
                }
            }
        }
    }

    private fun handleMusicControl(body: String): Pair<Int, JSONObject> {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return 400 to JSONObject().apply { put("error", "Invalid JSON") }
        }

        val action = json.optString("action").uppercase()
        val keyword = json.optString("keyword").trim()

        return when (action) {
            "PLAY" -> {
                val res = PlaybackControlBus.dispatchWithResult(
                    PlaybackActions.ACTION_CMD_PLAY,
                    source = "yiyu_voice"
                )
                200 to JSONObject().apply { put("handled", res.handled); put("detail", res.detail) }
            }
            "PAUSE" -> {
                val res = PlaybackControlBus.dispatchWithResult(
                    PlaybackActions.ACTION_CMD_PAUSE,
                    source = "yiyu_voice"
                )
                200 to JSONObject().apply { put("handled", res.handled); put("detail", res.detail) }
            }
            "PLAY_PAUSE" -> {
                val res = PlaybackControlBus.dispatchWithResult(
                    PlaybackActions.ACTION_CMD_PLAY_PAUSE,
                    source = "yiyu_voice"
                )
                200 to JSONObject().apply { put("handled", res.handled); put("detail", res.detail) }
            }
            "NEXT" -> {
                val res = PlaybackControlBus.dispatchWithResult(
                    PlaybackActions.ACTION_CMD_NEXT,
                    source = "yiyu_voice"
                )
                200 to JSONObject().apply { put("handled", res.handled); put("detail", res.detail) }
            }
            "PREV" -> {
                val res = PlaybackControlBus.dispatchWithResult(
                    PlaybackActions.ACTION_CMD_PREV,
                    source = "yiyu_voice"
                )
                200 to JSONObject().apply { put("handled", res.handled); put("detail", res.detail) }
            }
            "SEARCH_PLAY" -> {
                if (keyword.isEmpty()) {
                    400 to JSONObject().apply { put("error", "Missing keyword") }
                } else {
                    // Send broadcast to MainActivity or search handler to search and play
                    val searchIntent = Intent(ACTION_VOICE_SEARCH_PLAY).apply {
                        putExtra(EXTRA_SEARCH_KEYWORD, keyword)
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(searchIntent)
                    Log.i(TAG, "Dispatched SEARCH_PLAY broadcast for keyword: $keyword")
                    200 to JSONObject().apply {
                        put("handled", true)
                        put("action", "SEARCH_PLAY")
                        put("keyword", keyword)
                    }
                }
            }
            else -> {
                400 to JSONObject().apply { put("error", "Unsupported action: $action") }
            }
        }
    }

    private fun handleNavi(body: String): Pair<Int, JSONObject> {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return 400 to JSONObject().apply { put("error", "Invalid JSON") }
        }

        val type = json.optString("type", "DEST").uppercase()
        val dest = json.optString("dest").ifEmpty { json.optString("keyword") }.trim()

        val success = when (type) {
            "HOME" -> AmapAutoBridge.navigateSpecialDest(context, "HOME")
            "CORP", "WORK" -> AmapAutoBridge.navigateSpecialDest(context, "CORP")
            "EXIT" -> AmapAutoBridge.exitNavi(context)
            "NEARBY" -> AmapAutoBridge.searchNearby(context, dest)
            else -> {
                if (dest.isNotEmpty()) {
                    AmapAutoBridge.startNavi(context, dest)
                } else {
                    false
                }
            }
        }

        return 200 to JSONObject().apply {
            put("handled", success)
            put("type", type)
            put("dest", dest)
        }
    }

    private fun sendResponse(out: OutputStream, statusCode: Int, json: JSONObject) {
        val statusText = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Internal Server Error"
        }
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }
}
