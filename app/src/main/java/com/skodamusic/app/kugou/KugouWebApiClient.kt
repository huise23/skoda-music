package com.skodamusic.app.kugou

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class KugouApiResult(
    val httpCode: Int,
    val payload: String,
    val sessionKey: String
) {
    val json: JSONObject?
        get() = runCatching { JSONObject(payload) }.getOrNull()

    val kgStatus: Int?
        get() = json?.optInt("status")
}

data class KugouQrCode(
    val key: String,
    val imageUrl: String,
    val sessionKey: String
)

data class KugouQrStatus(
    val status: Int,
    val userId: String,
    val nickname: String,
    val sessionKey: String
)

data class KugouLoginResult(
    val userId: String,
    val displayName: String,
    val sessionKey: String
)

data class KugouRecommendedSong(
    val name: String,
    val singerName: String,
    val hash: String,
    val durationSeconds: Int,
    val albumId: String,
    val albumName: String,
    val audioId: String,
    val mixSongId: String,
    val coverUrl: String,
    val recommendReason: String,
    val recommendSubReason: String
)

data class KugouRecommendedRadio(
    val fmId: String,
    val fmName: String,
    val classId: String,
    val className: String,
    val description: String,
    val banner: String,
    val imageUrl: String,
    val previewSong: String
)

data class KugouRadioSong(
    val rawName: String,
    val name: String,
    val singerName: String,
    val hash: String,
    val audioId: String,
    val albumId: String,
    val albumAudioId: String,
    val durationMs: Long,
    val coverUrl: String
)

data class KugouPlaylistTag(
    val categoryName: String,
    val tagId: Int,
    val tagName: String,
    val sort: Int
)

data class KugouPlaylist(
    val listId: String,
    val globalId: String,
    val name: String,
    val creatorName: String,
    val intro: String,
    val coverUrl: String,
    val playCount: Long
)

data class KugouPlaylistSong(
    val name: String,
    val hash: String,
    val durationMs: Long,
    val albumId: String,
    val albumName: String,
    val fileId: String,
    val mixSongId: String,
    val singers: String,
    val coverUrl: String
)

data class KugouPlayUrl(
    val url: String,
    val hash: String,
    val privStatus: Int,
    val errCode: Int,
    val sessionKey: String
)

class KugouWebApiClient(
    private val log: (String) -> Unit
) {
    private val jsonMediaType: MediaType = MediaType.parse("application/json; charset=utf-8")
        ?: throw IllegalStateException("json media type parse failed")
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6000L, TimeUnit.MILLISECONDS)
        .readTimeout(10000L, TimeUnit.MILLISECONDS)
        .build()

    fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/')
    }

    fun isHttpUrl(baseUrl: String): Boolean {
        val normalized = normalizeBaseUrl(baseUrl)
        return normalized.startsWith("http://") || normalized.startsWith("https://")
    }

    fun getQrCode(baseUrl: String, sessionKey: String?): KugouQrCode? {
        val result = execute(
            request = requestBuilder(baseUrl, "/login/qr/key", sessionKey).get().build(),
            label = "kugou qr key"
        )
        val json = result.json ?: return null
        val key = json.optString("qrcode").trim()
        val imageUrl = json.optString("qrcode_img").trim()
        if (key.isEmpty() || imageUrl.isEmpty()) {
            log("kugou qr key missing fields")
            return null
        }
        return KugouQrCode(key = key, imageUrl = imageUrl, sessionKey = result.sessionKey)
    }

    fun checkQrStatus(baseUrl: String, sessionKey: String, key: String): KugouQrStatus? {
        val encodedKey = urlEncode(key)
        val result = execute(
            request = requestBuilder(baseUrl, "/login/qr/check?key=$encodedKey", sessionKey).get().build(),
            label = "kugou qr check"
        )
        val json = result.json ?: return null
        return KugouQrStatus(
            status = json.optInt("status", -1),
            userId = json.optString("userid").trim(),
            nickname = json.optString("nickname").trim(),
            sessionKey = result.sessionKey.ifBlank { sessionKey }
        )
    }

    fun refreshSession(baseUrl: String, sessionKey: String): KugouLoginResult? {
        val result = execute(
            request = requestBuilder(baseUrl, "/login/token", sessionKey)
                .post(emptyJsonBody())
                .build(),
            label = "kugou refresh token"
        )
        if (!result.isKgSuccess()) {
            return null
        }
        val json = result.json ?: return null
        val userId = json.optString("userid").trim()
        val token = json.optString("token").trim()
        if (userId.isEmpty() || userId == "0" || token.isEmpty()) {
            log("kugou refresh unauthenticated user=$userId tokenBlank=${token.isEmpty()}")
            return null
        }
        return KugouLoginResult(
            userId = userId,
            displayName = "",
            sessionKey = result.sessionKey.ifBlank { sessionKey }
        )
    }

    fun sendSmsCode(baseUrl: String, sessionKey: String?, mobile: String): KugouApiResult {
        return execute(
            request = requestBuilder(baseUrl, "/captcha/sent?mobile=${urlEncode(mobile)}", sessionKey)
                .post(emptyJsonBody())
                .build(),
            label = "kugou sms sent"
        )
    }

    fun loginByMobile(baseUrl: String, sessionKey: String?, mobile: String, code: String): KugouLoginResult? {
        val body = JSONObject()
            .put("Mobile", mobile)
            .put("Code", code)
            .toString()
        val result = execute(
            request = requestBuilder(baseUrl, "/login/cellphone", sessionKey)
                .post(RequestBody.create(jsonMediaType, body))
                .build(),
            label = "kugou sms login"
        )
        if (!result.isKgSuccess()) {
            return null
        }
        val json = result.json ?: return null
        val userId = json.optString("userid").trim()
        val token = json.optString("token").trim()
        if (userId.isEmpty() || userId == "0" || token.isEmpty()) {
            log("kugou sms login missing auth user=$userId tokenBlank=${token.isEmpty()}")
            return null
        }
        return KugouLoginResult(
            userId = userId,
            displayName = "",
            sessionKey = result.sessionKey
        )
    }

    fun logout(baseUrl: String, sessionKey: String?) {
        execute(
            request = requestBuilder(baseUrl, "/login/logout", sessionKey)
                .post(emptyJsonBody())
                .build(),
            label = "kugou logout"
        )
    }

    fun getRecommendedSongs(baseUrl: String, sessionKey: String): Pair<List<KugouRecommendedSong>, String>? {
        val result = execute(
            request = requestBuilder(baseUrl, "/recommend/songs", sessionKey).get().build(),
            label = "kugou recommend songs"
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val json = result.json ?: return null
        val status = json.optInt("status", 1)
        if (status != 1) {
            return null
        }
        val songs = json.optJSONArray("song_list") ?: JSONArray()
        val mapped = ArrayList<KugouRecommendedSong>(songs.length())
        for (i in 0 until songs.length()) {
            val item = songs.optJSONObject(i) ?: continue
            val name = item.optString("songname").trim()
            val hash = item.optString("hash").trim()
            if (name.isEmpty() || hash.isEmpty()) {
                continue
            }
            mapped.add(
                KugouRecommendedSong(
                    name = name,
                    singerName = item.optString("author_name").trim(),
                    hash = hash,
                    durationSeconds = item.optInt("time_length", -1),
                    albumId = item.optString("album_id").trim(),
                    albumName = item.optString("album_name").trim(),
                    audioId = item.optString("songid").trim(),
                    mixSongId = item.optString("mixsongid").trim(),
                    coverUrl = item.optString("sizable_cover").replace("{size}", "400").trim(),
                    recommendReason = item.optString("rec_copy_write").trim(),
                    recommendSubReason = item.optString("rec_sub_copy_write").trim()
                )
            )
        }
        return mapped to result.sessionKey.ifBlank { sessionKey }
    }

    fun getRecommendedRadios(baseUrl: String, sessionKey: String): Pair<List<KugouRecommendedRadio>, String>? {
        val result = execute(
            request = requestBuilder(baseUrl, "/fm/recommend", sessionKey).get().build(),
            label = "kugou fm recommend"
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val json = result.json ?: return null
        val status = json.optInt("status", 1)
        if (status != 1) {
            return null
        }
        val data = json.optJSONArray("data") ?: JSONArray()
        val mapped = ArrayList<KugouRecommendedRadio>(data.length())
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val fmId = item.optString("fmid").trim()
            val name = item.optString("fmname").trim()
            if (fmId.isEmpty() || name.isEmpty()) {
                continue
            }
            val preview = item.optJSONArray("rcmdlist")
                ?.optJSONObject(0)
                ?.optString("name")
                .orEmpty()
                .trim()
            mapped.add(
                KugouRecommendedRadio(
                    fmId = fmId,
                    fmName = name,
                    classId = item.optString("classid").trim(),
                    className = item.optString("classname").trim(),
                    description = item.optString("description").trim(),
                    banner = item.optString("banner").trim(),
                    imageUrl = item.optString("imgurl").trim(),
                    previewSong = preview
                )
            )
        }
        return mapped to result.sessionKey.ifBlank { sessionKey }
    }

    fun getRadioSongs(
        baseUrl: String,
        sessionKey: String,
        fmId: String,
        type: Int = 2,
        offset: Int = -1,
        size: Int = 20
    ): Pair<List<KugouRadioSong>, String>? {
        val path = "/fm/songs?fmid=${urlEncode(fmId)}&type=$type&offset=$offset&size=$size"
        val result = execute(
            request = requestBuilder(baseUrl, path, sessionKey).get().build(),
            label = "kugou fm songs"
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val json = result.json ?: return null
        val status = json.optInt("status", 1)
        if (status != 1) {
            return null
        }
        val data = json.optJSONArray("data") ?: JSONArray()
        val mapped = ArrayList<KugouRadioSong>()
        for (i in 0 until data.length()) {
            val group = data.optJSONObject(i) ?: continue
            val songs = group.optJSONArray("songs") ?: continue
            for (j in 0 until songs.length()) {
                val item = songs.optJSONObject(j) ?: continue
                val rawName = item.optString("name").trim()
                val hash = item.optString("hash").trim()
                if (rawName.isEmpty() || hash.isEmpty()) {
                    continue
                }
                val split = splitFmSongName(rawName)
                val cover = item.optJSONObject("trans_param")
                    ?.optString("union_cover")
                    .orEmpty()
                    .replace("{size}", "400")
                    .trim()
                mapped.add(
                    KugouRadioSong(
                        rawName = rawName,
                        name = split.second,
                        singerName = split.first,
                        hash = hash,
                        audioId = item.optString("audio_id").trim(),
                        albumId = item.optString("album_id").trim(),
                        albumAudioId = item.optString("album_audio_id").trim(),
                        durationMs = item.optLong("time", -1L),
                        coverUrl = cover
                    )
                )
            }
        }
        return mapped to result.sessionKey.ifBlank { sessionKey }
    }

    fun getPlaylistTags(baseUrl: String, sessionKey: String): Pair<List<KugouPlaylistTag>, String>? {
        val result = execute(
            request = requestBuilder(baseUrl, "/playlist/tags", sessionKey).get().build(),
            label = "kugou playlist tags"
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val root = runCatching { JSONArray(result.payload) }.getOrNull() ?: return null
        val mapped = ArrayList<KugouPlaylistTag>()
        for (i in 0 until root.length()) {
            val category = root.optJSONObject(i) ?: continue
            val categoryName = category.optString("tag_name").trim()
            val children = category.optJSONArray("son") ?: continue
            for (j in 0 until children.length()) {
                val child = children.optJSONObject(j) ?: continue
                val tagId = child.optInt("tag_id", -1)
                val tagName = child.optString("tag_name").trim()
                if (tagId <= 0 || tagName.isEmpty()) {
                    continue
                }
                mapped.add(
                    KugouPlaylistTag(
                        categoryName = categoryName,
                        tagId = tagId,
                        tagName = tagName,
                        sort = child.optInt("sort", j)
                    )
                )
            }
        }
        return mapped to result.sessionKey.ifBlank { sessionKey }
    }

    fun getPlaylistsByTag(baseUrl: String, sessionKey: String, tagId: Int, page: Int = 1): Pair<List<KugouPlaylist>, String>? {
        val result = execute(
            request = requestBuilder(baseUrl, "/top/playlist?category_id=$tagId&page=$page", sessionKey).get().build(),
            label = "kugou top playlist"
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val json = result.json ?: return null
        val status = json.optInt("status", 1)
        if (status != 1) {
            return null
        }
        val lists = json.optJSONArray("special_list") ?: JSONArray()
        val mapped = ArrayList<KugouPlaylist>(lists.length())
        for (i in 0 until lists.length()) {
            val item = lists.optJSONObject(i) ?: continue
            val name = item.optString("specialname").trim()
            val globalId = item.optString("global_collection_id").trim()
            val listId = item.optString("specialid").trim()
            if (name.isEmpty() || (globalId.isEmpty() && listId.isEmpty())) {
                continue
            }
            mapped.add(
                KugouPlaylist(
                    listId = listId,
                    globalId = globalId,
                    name = name,
                    creatorName = item.optString("nickname").trim(),
                    intro = item.optString("intro").trim(),
                    coverUrl = item.optString("flexible_cover").replace("{size}", "400").trim(),
                    playCount = item.optLong("play_count", 0L)
                )
            )
        }
        return mapped to result.sessionKey.ifBlank { sessionKey }
    }

    fun getPlaylistSongs(
        baseUrl: String,
        sessionKey: String,
        playlistId: String,
        page: Int = 1,
        pageSize: Int = 30
    ): Pair<List<KugouPlaylistSong>, String>? {
        val path = "/playlist/track/all?id=${urlEncode(playlistId)}&page=$page&pagesize=$pageSize"
        val result = execute(
            request = requestBuilder(baseUrl, path, sessionKey).get().build(),
            label = "kugou playlist songs"
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val json = result.json ?: return null
        if (json.optInt("status", 1) != 1) {
            return null
        }
        val songs = json.optJSONArray("songs") ?: JSONArray()
        val mapped = ArrayList<KugouPlaylistSong>(songs.length())
        for (i in 0 until songs.length()) {
            val item = songs.optJSONObject(i) ?: continue
            val hash = item.optString("hash").trim()
            val rawName = item.optString("name").trim()
            if (hash.isEmpty() || rawName.isEmpty()) {
                continue
            }
            val singers = item.optJSONArray("singerinfo")
                ?.let { array ->
                    val names = ArrayList<String>(array.length())
                    for (j in 0 until array.length()) {
                        val name = array.optJSONObject(j)?.optString("name").orEmpty().trim()
                        if (name.isNotEmpty()) names.add(name)
                    }
                    names.joinToString("、")
                }
                .orEmpty()
            val album = item.optJSONObject("albuminfo")
            mapped.add(
                KugouPlaylistSong(
                    name = processPlaylistSongName(rawName, singers),
                    hash = hash,
                    durationMs = item.optLong("timelen", -1L),
                    albumId = item.optString("album_id").trim(),
                    albumName = album?.optString("name").orEmpty().trim(),
                    fileId = item.optString("fileid").trim(),
                    mixSongId = item.optString("mixsongid").trim(),
                    singers = singers.ifBlank { "未知" },
                    coverUrl = item.optString("cover").replace("{size}", "400").trim()
                )
            )
        }
        return mapped to result.sessionKey.ifBlank { sessionKey }
    }

    fun addSongToLikeList(
        baseUrl: String,
        sessionKey: String,
        name: String,
        hash: String,
        albumId: String,
        mixSongId: String
    ): KugouApiResult {
        val song = JSONObject()
            .put("Name", name)
            .put("Hash", hash)
            .put("AlbumId", albumId.ifBlank { "0" })
            .put("MixSongId", mixSongId.ifBlank { "0" })
        val body = JSONObject()
            .put("ListId", LIKE_LIST_ID_FOR_ACTION)
            .put("Songs", JSONArray().put(song))
            .toString()
        return execute(
            request = requestBuilder(baseUrl, "/playlist/tracks/add", sessionKey)
                .post(RequestBody.create(jsonMediaType, body))
                .build(),
            label = "kugou like add"
        )
    }

    fun getPlayUrl(
        baseUrl: String,
        sessionKey: String,
        hash: String,
        quality: String = "128",
        albumId: String = "",
        albumAudioId: String = "",
        freePart: Boolean = false
    ): KugouPlayUrl? {
        val path = buildString {
            append("/song/url?hash=")
            append(urlEncode(hash))
            append("&quality=")
            append(urlEncode(quality.ifBlank { "128" }))
            if (albumId.isNotBlank()) {
                append("&album_id=")
                append(urlEncode(albumId))
            }
            if (albumAudioId.isNotBlank()) {
                append("&album_audio_id=")
                append(urlEncode(albumAudioId))
            }
            append("&free_part=")
            append(freePart)
        }
        val result = execute(
            request = requestBuilder(baseUrl, path, sessionKey).get().build(),
            label = "kugou song url"
        )
        if (result.httpCode !in 200..299 || result.kgStatus != 1) {
            return null
        }
        val json = result.json ?: return null
        val urls = json.optJSONArray("url") ?: return null
        val firstUrl = urls.optString(0).trim()
        if (firstUrl.isEmpty()) {
            return null
        }
        return KugouPlayUrl(
            url = firstUrl,
            hash = json.optString("hash").trim(),
            privStatus = json.optInt("priv_status", 0),
            errCode = json.optInt("err_code", 0),
            sessionKey = result.sessionKey.ifBlank { sessionKey }
        )
    }

    fun downloadBitmap(url: String): Bitmap? {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "image/*")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val code = response.code()
                if (code !in 200..299) {
                    log("kugou qr image HTTP $code")
                    return null
                }
                response.body()?.byteStream()?.use { BitmapFactory.decodeStream(it) }
            }
        } catch (e: Exception) {
            log("kugou qr image exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            null
        }
    }

    private fun execute(request: Request, label: String): KugouApiResult {
        return try {
            log("$label ${request.method()} ${request.url()}")
            httpClient.newCall(request).execute().use { response ->
                val code = response.code()
                val payload = response.body()?.string().orEmpty()
                val responseSession = response.header(SESSION_HEADER).orEmpty().trim()
                log("$label -> HTTP $code status=${runCatching { JSONObject(payload).optString("status") }.getOrDefault("")}")
                KugouApiResult(
                    httpCode = code,
                    payload = payload,
                    sessionKey = responseSession
                )
            }
        } catch (e: Exception) {
            log("$label exception=${e.javaClass.simpleName}: ${e.message ?: "unknown"}")
            KugouApiResult(httpCode = -1, payload = "", sessionKey = "")
        }
    }

    private fun requestBuilder(baseUrl: String, pathWithQuery: String, sessionKey: String?): Request.Builder {
        val normalized = normalizeBaseUrl(baseUrl)
        val builder = Request.Builder()
            .url("$normalized$pathWithQuery")
            .header("Accept", "application/json")
        val cleanSession = sessionKey.orEmpty().trim()
        if (cleanSession.isNotEmpty()) {
            builder.header(SESSION_HEADER, cleanSession)
        }
        return builder
    }

    private fun KugouApiResult.isKgSuccess(): Boolean {
        return httpCode in 200..299 && kgStatus == 1
    }

    private fun emptyJsonBody(): RequestBody {
        return RequestBody.create(jsonMediaType, "{}")
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun splitFmSongName(rawName: String): Pair<String, String> {
        val dashIndex = rawName.indexOf(" - ")
        return if (dashIndex > 0) {
            rawName.substring(0, dashIndex).trim() to rawName.substring(dashIndex + 3).trim()
        } else {
            "未知歌手" to rawName.trim()
        }
    }

    private fun processPlaylistSongName(rawName: String, singers: String): String {
        val dashIndex = rawName.indexOf(" - ")
        if (dashIndex <= 0 || singers.isBlank()) {
            return rawName
        }
        val prefix = rawName.substring(0, dashIndex).trim()
        val songName = rawName.substring(dashIndex + 3).trim()
        val allPresent = singers.split("、").filter { it.isNotBlank() }.all { prefix.contains(it, ignoreCase = true) }
        return if (allPresent) songName else rawName
    }

    companion object {
        const val SESSION_HEADER = "X-Kg-Session-Id"
        const val QR_STATUS_EXPIRED = 0
        const val QR_STATUS_WAITING_FOR_SCAN = 1
        const val QR_STATUS_WAITING_FOR_CONFIRM = 2
        const val QR_STATUS_SUCCESS = 4
        private const val LIKE_LIST_ID_FOR_ACTION = "2"
    }
}
