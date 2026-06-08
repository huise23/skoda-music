package com.skodamusic.app.kugou

import android.util.Base64
import com.skodamusic.app.model.LyricLine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.InflaterInputStream

class KugouLyricClient(
    private val log: (String) -> Unit
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6000L, TimeUnit.MILLISECONDS)
        .readTimeout(12000L, TimeUnit.MILLISECONDS)
        .build()

    fun loadLyrics(hash: String, albumAudioId: String, keyword: String): List<LyricLine> {
        val candidate = searchLyric(hash, albumAudioId, keyword) ?: return emptyList()
        val content = downloadLyric(candidate) ?: return emptyList()
        return parseLyricLines(content)
    }

    private fun searchLyric(hash: String, albumAudioId: String, keyword: String): LyricCandidate? {
        val params = linkedMapOf(
            "album_audio_id" to albumAudioId.ifBlank { "0" },
            "appid" to KugouDirectSigner.APP_ID,
            "clientver" to KugouDirectSigner.CLIENT_VER,
            "duration" to "0",
            "hash" to hash,
            "keyword" to keyword,
            "lrctxt" to "1",
            "man" to "no"
        )
        val json = executeGet("https://lyrics.kugou.com/v1/search?${queryString(params)}", "kugou lyric search")
            ?: return null
        val candidates = json.optJSONArray("candidates") ?: return null
        for (index in 0 until candidates.length()) {
            val item = candidates.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val accessKey = item.optString("accesskey").trim()
            if (id.isNotEmpty() && accessKey.isNotEmpty()) {
                return LyricCandidate(
                    id = id,
                    accessKey = accessKey,
                    fmt = item.optString("fmt").ifBlank { "krc" }.trim()
                )
            }
        }
        return null
    }

    private fun downloadLyric(candidate: LyricCandidate): String? {
        val fmt = candidate.fmt.ifBlank { "krc" }
        val params = linkedMapOf(
            "ver" to "1",
            "client" to "android",
            "id" to candidate.id,
            "accesskey" to candidate.accessKey,
            "fmt" to fmt,
            "charset" to "utf8"
        )
        val json = executeGet("https://lyrics.kugou.com/download?${queryString(params)}", "kugou lyric download")
            ?: return null
        val encoded = json.optString("content").trim()
        if (encoded.isEmpty()) {
            return null
        }
        val contentType = json.optInt("contenttype", 0)
        return if (fmt.equals("lrc", ignoreCase = true) || contentType != 0) {
            runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }
                .onFailure { log("kugou lyric lrc decode failed ${it.javaClass.simpleName}") }
                .getOrNull()
        } else {
            decodeKrc(encoded)
        }
    }

    private fun executeGet(url: String, label: String): JSONObject? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", KugouDirectSigner.USER_AGENT)
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val payload = response.body()?.string().orEmpty()
                if (!response.isSuccessful || payload.isBlank()) {
                    log("$label http=${response.code()}")
                    return null
                }
                JSONObject(payload)
            }
        }.onFailure { error ->
            log("$label failed ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun decodeKrc(base64: String): String? {
        return runCatching {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            if (bytes.size <= KRC_HEADER_BYTES) {
                return null
            }
            val encrypted = bytes.copyOfRange(KRC_HEADER_BYTES, bytes.size)
            for (index in encrypted.indices) {
                encrypted[index] = (encrypted[index].toInt() xor KRC_KEY[index % KRC_KEY.size].toInt()).toByte()
            }
            InflaterInputStream(ByteArrayInputStream(encrypted)).use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) {
                        break
                    }
                    output.write(buffer, 0, read)
                }
                String(output.toByteArray(), Charsets.UTF_8)
            }
        }.onFailure { error ->
            log("kugou lyric krc decode failed ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun parseLyricLines(raw: String): List<LyricLine> {
        val krcLines = parseKrcLines(raw)
        if (krcLines.isNotEmpty()) {
            return krcLines
        }
        return parseLrcLines(raw)
    }

    private fun parseKrcLines(raw: String): List<LyricLine> {
        val lines = ArrayList<LyricLine>()
        val pattern = Regex("^\\[(\\d+),(\\d+)](.*)")
        raw.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { line ->
            val match = pattern.find(line.trim()) ?: return@forEach
            val timeMs = match.groupValues[1].toLongOrNull() ?: return@forEach
            val content = match.groupValues[3]
                .replace(Regex("<\\d+,\\d+,\\d+>"), "")
                .trim()
            if (content.isNotEmpty()) {
                lines.add(LyricLine(timeMs, content))
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun parseLrcLines(raw: String): List<LyricLine> {
        val expanded = raw.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("(?<!\\n)\\[(\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?)]"), "\n[$1]")
        val pattern = Regex("\\[(\\d{1,2}:\\d{2}(?:\\.\\d{1,3})?)]\\s*([^\\[]*)")
        return pattern.findAll(expanded).mapNotNull { match ->
            val timeMs = parseTimeTag(match.groupValues[1])
            val text = match.groupValues[2].replace('\n', ' ').trim()
            if (timeMs >= 0L && text.isNotEmpty()) LyricLine(timeMs, text) else null
        }.sortedBy { it.timeMs }.toList()
    }

    private fun parseTimeTag(value: String): Long {
        val parts = value.split(":")
        if (parts.size != 2) return -1L
        val minute = parts[0].toLongOrNull() ?: return -1L
        val secondParts = parts[1].split(".", limit = 2)
        val second = secondParts[0].toLongOrNull() ?: return -1L
        val millis = if (secondParts.size == 2) {
            secondParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return -1L
        } else {
            0L
        }
        return minute * 60_000L + second * 1_000L + millis
    }

    private fun queryString(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private data class LyricCandidate(
        val id: String,
        val accessKey: String,
        val fmt: String
    )

    companion object {
        private const val KRC_HEADER_BYTES = 4
        private val KRC_KEY = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, -50, -46, 110, 105)
    }
}
