package com.skodamusic.app.kugou

import android.content.Context
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class KugouDirectContentClient(
    context: Context,
    private val log: (String) -> Unit
) {
    private val sessionStore = KugouDirectSessionStore(context.applicationContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6000L, TimeUnit.MILLISECONDS)
        .readTimeout(12000L, TimeUnit.MILLISECONDS)
        .build()

    fun getRecommendedSongs(): List<KugouRecommendedSong>? {
        val session = sessionStore.load() ?: return null
        val body = JSONObject()
            .put("platform", "android")
            .put("userid", session.userId.ifBlank { "0" })
            .toString()
        val json = execute(
            path = "/everyday_song_recommend",
            method = "POST",
            body = body,
            session = session,
            router = "everydayrec.service.kugou.com",
            label = "kugou direct recommend songs"
        ) ?: return null
        val data = responseData(json)
        val songs = data.optJSONArray("song_list") ?: JSONArray()
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
        return mapped
    }

    fun getRecommendedRadios(): List<KugouRecommendedRadio>? {
        val session = sessionStore.load() ?: return null
        val nowMs = System.currentTimeMillis()
        val body = signedBody(session, nowMs)
            .put("rcmdsongcount", 1)
            .put("level", 0)
            .put("area_code", 1)
            .put("get_tracker", 1)
            .put("uid", 0)
            .toString()
        val json = execute(
            path = "/v1/rcmd_list",
            method = "POST",
            body = body,
            session = session,
            router = "fm.service.kugou.com",
            label = "kugou direct fm recommend"
        ) ?: return null
        val data = json.optJSONArray("data") ?: responseData(json).optJSONArray("data") ?: JSONArray()
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
        return mapped
    }

    fun getRadioSongs(
        fmId: String,
        type: Int = 2,
        offset: Int = -1,
        size: Int = 20
    ): List<KugouRadioSong>? {
        val cleanFmId = fmId.trim()
        if (cleanFmId.isEmpty()) {
            return null
        }
        val session = sessionStore.load() ?: return null
        val nowMs = System.currentTimeMillis()
        val data = JSONArray().put(
            JSONObject()
                .put("fmid", cleanFmId)
                .put("fmtype", type)
                .put("offset", offset)
                .put("size", size)
                .put("singername", "")
        )
        val body = signedBody(session, nowMs)
            .put("area_code", 1)
            .put("data", data)
            .put("get_tracker", 1)
            .put("uid", session.userId.ifBlank { "0" })
            .toString()
        val json = execute(
            path = "/v1/app_song_list_offset",
            method = "POST",
            body = body,
            session = session,
            router = "fm.service.kugou.com",
            label = "kugou direct fm songs"
        ) ?: return null
        val groups = json.optJSONArray("data") ?: responseData(json).optJSONArray("data") ?: JSONArray()
        val mapped = ArrayList<KugouRadioSong>()
        for (i in 0 until groups.length()) {
            val group = groups.optJSONObject(i) ?: continue
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
        return mapped
    }

    fun getPlaylistTags(): List<KugouPlaylistTag>? {
        val session = sessionStore.load() ?: return null
        val body = JSONObject()
            .put("tag_type", "collection")
            .put("tag_id", 0)
            .put("source", 3)
            .toString()
        val result = executeResult(
            path = "/pubsongs/v1/get_tags_by_type",
            method = "POST",
            body = body,
            session = session,
            router = "",
            label = "kugou direct playlist tags"
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val json = result.json
        val root = runCatching { JSONArray(result.payload) }.getOrNull()
            ?: json?.optJSONArray("data")
            ?: json?.optJSONArray("list")
            ?: JSONArray()
        val mapped = ArrayList<KugouPlaylistTag>()
        for (i in 0 until root.length()) {
            val category = root.optJSONObject(i) ?: continue
            val categoryName = category.optString("tag_name").trim()
            val children = category.optJSONArray("son") ?: category.optJSONArray("children") ?: continue
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
        return mapped
    }

    fun getRecommendedPlaylists(categoryId: Int = 0, page: Int = 1, pageSize: Int = 30): List<KugouPlaylist>? {
        val session = sessionStore.load() ?: return null
        val clientTime = System.currentTimeMillis() / 1000L
        val body = JSONObject()
            .put("appid", KugouDirectSigner.APP_ID)
            .put("mid", KugouDirectSigner.md5(session.dfid.ifBlank { "-" }))
            .put("clientver", KugouDirectSigner.CLIENT_VER)
            .put("platform", "android")
            .put("clienttime", clientTime)
            .put("userid", session.userId.ifBlank { "0" })
            .put("module_id", 1)
            .put("page", page)
            .put("pagesize", pageSize)
            .put("key", KugouDirectSigner.calcLoginKey(clientTime))
            .put(
                "special_recommend",
                JSONObject()
                    .put("withtag", 1)
                    .put("withsong", 0)
                    .put("sort", 1)
                    .put("ugc", 1)
                    .put("is_selected", 0)
                    .put("withrecommend", 1)
                    .put("area_code", 1)
                    .put("categoryid", categoryId)
            )
            .put("req_multi", 1)
            .put("retrun_min", 5)
            .put("return_special_falg", 1)
            .toString()
        val json = execute(
            path = "/v2/special_recommend",
            method = "POST",
            body = body,
            session = session,
            router = "specialrec.service.kugou.com",
            label = "kugou direct playlists"
        ) ?: return null
        val data = responseData(json)
        val lists = data.optJSONArray("special_list")
            ?: data.optJSONArray("list")
            ?: data.optJSONArray("data")
            ?: JSONArray()
        return parsePlaylists(lists)
    }

    fun getPlaylistSongs(playlistId: String, page: Int = 1, pageSize: Int = 30): List<KugouPlaylistSong>? {
        val cleanPlaylistId = playlistId.trim()
        if (cleanPlaylistId.isEmpty()) {
            return null
        }
        val session = sessionStore.load() ?: return null
        val beginIdx = ((page.coerceAtLeast(1) - 1) * pageSize).coerceAtLeast(0)
        val params = linkedMapOf(
            "area_code" to "1",
            "begin_idx" to beginIdx.toString(),
            "plat" to "1",
            "type" to "1",
            "mode" to "1",
            "personal_switch" to "1",
            "extend_fields" to "abtags,hot_cmt,popularization",
            "pagesize" to pageSize.toString(),
            "global_collection_id" to cleanPlaylistId
        )
        val json = execute(
            path = "/pubsongs/v2/get_other_list_file_nofilt",
            method = "GET",
            body = "",
            session = session,
            router = "",
            label = "kugou direct playlist songs",
            params = params
        ) ?: return null
        val data = responseData(json)
        val songs = data.optJSONArray("songs")
            ?: data.optJSONArray("list")
            ?: data.optJSONArray("data")
            ?: JSONArray()
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
                        if (name.isNotEmpty()) {
                            names.add(name)
                        }
                    }
                    names.joinToString("、")
                }
                .orEmpty()
            val album = item.optJSONObject("albuminfo")
            mapped.add(
                KugouPlaylistSong(
                    name = processPlaylistSongName(rawName, singers),
                    hash = hash,
                    durationMs = item.optLong("timelen", item.optLong("time", -1L)),
                    albumId = item.optString("album_id").trim(),
                    albumName = album?.optString("name").orEmpty().trim(),
                    fileId = item.optString("fileid").trim(),
                    mixSongId = item.optString("mixsongid").ifBlank { item.optString("album_audio_id") }.trim(),
                    singers = singers.ifBlank { item.optString("singername").ifBlank { "未知" }.trim() },
                    coverUrl = item.optString("cover").ifBlank {
                        item.optJSONObject("trans_param")?.optString("union_cover").orEmpty()
                    }.replace("{size}", "400").trim()
                )
            )
        }
        return mapped
    }

    fun addSongToLikeList(
        name: String,
        hash: String,
        albumId: String,
        mixSongId: String
    ): KugouApiResult {
        val cleanHash = hash.trim()
        val session = sessionStore.load()
        if (session == null || cleanHash.isEmpty()) {
            return KugouApiResult(httpCode = -1, payload = "", sessionKey = "")
        }
        val clientTime = System.currentTimeMillis() / 1000L
        val data = JSONArray().put(
            JSONObject()
                .put("number", 1)
                .put("name", name)
                .put("hash", cleanHash)
                .put("size", 0)
                .put("sort", 0)
                .put("timelen", 0)
                .put("bitrate", 0)
                .put("album_id", albumId.toLongOrNull() ?: 0L)
                .put("mixsongid", mixSongId.toLongOrNull() ?: 0L)
        )
        val body = JSONObject()
            .put("userid", session.userId)
            .put("token", session.token)
            .put("listid", LIKE_LIST_ID_FOR_ACTION)
            .put("list_ver", 0)
            .put("type", 0)
            .put("slow_upload", 1)
            .put("scene", "false;null")
            .put("data", data)
            .toString()
        return executeResult(
            path = "/cloudlist.service/v6/add_song",
            method = "POST",
            body = body,
            session = session,
            router = "",
            label = "kugou direct like add",
            params = linkedMapOf(
                "last_time" to clientTime.toString(),
                "last_area" to "gztx",
                "userid" to session.userId,
                "token" to session.token
            )
        )
    }

    fun getPlayUrl(
        hash: String,
        quality: String,
        albumAudioId: String
    ): KugouPlayUrl? {
        val cleanHash = hash.trim().lowercase()
        if (cleanHash.isEmpty()) {
            return null
        }
        val session = sessionStore.load() ?: return null
        val params = linkedMapOf(
            "album_id" to "0",
            "area_code" to "1",
            "hash" to cleanHash,
            "ssa_flag" to "is_fromtrack",
            "version" to KugouDirectSigner.CLIENT_VER,
            "page_id" to "967177915",
            "quality" to quality.ifBlank { "128" },
            "album_audio_id" to albumAudioId.ifBlank { "0" },
            "behavior" to "play",
            "pid" to "411",
            "cmd" to "26",
            "pidversion" to "3001",
            "IsFreePart" to "0",
            "ppage_id" to "356753938,823673182,967485191",
            "cdnBackup" to "1",
            "kcard" to "0",
            "module" to ""
        )
        val dfidOverride = KugouDirectCrypto.randomString(24).lowercase()
        val json = execute(
            path = "/v5/url",
            method = "GET",
            body = "",
            session = session,
            router = "trackercdn.kugou.com",
            label = "kugou direct song url",
            params = params,
            signatureType = SignatureType.V5,
            dfidOverride = dfidOverride
        ) ?: return null
        val data = responseData(json)
        val urls = data.optJSONArray("url") ?: return null
        val firstUrl = urls.optString(0).trim()
        if (firstUrl.isEmpty()) {
            return null
        }
        return KugouPlayUrl(
            url = firstUrl,
            hash = data.optString("hash", cleanHash).trim(),
            privStatus = data.optInt("priv_status", 0),
            errCode = data.optInt("err_code", 0),
            sessionKey = session.token
        )
    }

    private fun execute(
        path: String,
        method: String,
        body: String,
        session: KugouDirectSessionSnapshot,
        router: String,
        label: String,
        params: LinkedHashMap<String, String> = linkedMapOf(),
        signatureType: SignatureType = SignatureType.DEFAULT,
        dfidOverride: String? = null
    ): JSONObject? {
        val result = executeResult(
            path = path,
            method = method,
            body = body,
            session = session,
            router = router,
            label = label,
            params = params,
            signatureType = signatureType,
            dfidOverride = dfidOverride
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        return result.json
    }

    private fun executeResult(
        path: String,
        method: String,
        body: String,
        session: KugouDirectSessionSnapshot,
        router: String,
        label: String,
        params: LinkedHashMap<String, String> = linkedMapOf(),
        signatureType: SignatureType = SignatureType.DEFAULT,
        dfidOverride: String? = null
    ): KugouApiResult {
        val dfid = dfidOverride ?: session.dfid.ifBlank { "-" }
        val clientTimeSeconds = System.currentTimeMillis() / 1000L
        val signedParams = defaultParams(params, session, dfid, clientTimeSeconds)
        if (signatureType == SignatureType.V5 && signedParams.containsKey("hash")) {
            signedParams["key"] = KugouDirectSigner.calcV5Key(
                hash = signedParams["hash"].orEmpty(),
                userId = signedParams["userid"].orEmpty(),
                mid = signedParams["mid"].orEmpty()
            )
        }
        signedParams["signature"] = KugouDirectSigner.calcPostSignature(signedParams, body)
        val requestBuilder = Request.Builder()
            .url("$GATEWAY_HOST$path?${queryString(signedParams)}")
            .header("Accept", "application/json")
            .header("User-Agent", KugouDirectSigner.USER_AGENT)
            .header("dfid", dfid)
            .header("mid", signedParams["mid"].orEmpty())
            .header("clienttime", signedParams["clienttime"].orEmpty())
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
        if (router.isNotBlank()) {
            requestBuilder.header("x-router", router)
        }
        val request = if (method == "POST") {
            requestBuilder.post(RequestBody.create(JSON_MEDIA_TYPE, body)).build()
        } else {
            requestBuilder.get().build()
        }
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                val payload = response.body()?.string().orEmpty()
                if (!response.isSuccessful) {
                    log("$label http=${response.code()}")
                    return KugouApiResult(response.code(), payload, session.token)
                }
                KugouApiResult(response.code(), payload, session.token)
            }
        }.onFailure { error ->
            log("$label failed ${error.javaClass.simpleName}")
        }.getOrElse { KugouApiResult(httpCode = -1, payload = "", sessionKey = "") }
    }

    private fun defaultParams(
        params: LinkedHashMap<String, String>,
        session: KugouDirectSessionSnapshot,
        dfid: String,
        clientTimeSeconds: Long
    ): LinkedHashMap<String, String> {
        val merged = LinkedHashMap(params)
        val mid = KugouDirectSigner.calcNewMid(dfid)
        putIfMissing(merged, "appid", KugouDirectSigner.APP_ID)
        putIfMissing(merged, "clientver", KugouDirectSigner.CLIENT_VER)
        putIfMissing(merged, "dfid", dfid)
        putIfMissing(merged, "mid", mid)
        putIfMissing(merged, "uuid", KugouDirectSigner.md5(dfid + mid))
        putIfMissing(merged, "userid", session.userId)
        putIfMissing(merged, "clienttime", clientTimeSeconds.toString())
        if (session.token.isNotBlank() && !merged.containsKey("token")) {
            merged["token"] = session.token
        }
        return merged
    }

    private fun responseData(json: JSONObject): JSONObject {
        val data = json.opt("data")
        return if (json.optInt("status", -1) == 1 && data is JSONObject) data else json
    }

    private fun signedBody(session: KugouDirectSessionSnapshot, clientTimeMs: Long): JSONObject {
        return JSONObject()
            .put("appid", KugouDirectSigner.APP_ID)
            .put("clienttime", clientTimeMs)
            .put("clientver", KugouDirectSigner.CLIENT_VER)
            .put("key", KugouDirectSigner.calcLoginKey(clientTimeMs))
            .put("mid", KugouDirectSigner.calcNewMid(session.dfid.ifBlank { "-" }))
    }

    private fun parsePlaylists(lists: JSONArray): List<KugouPlaylist> {
        val mapped = ArrayList<KugouPlaylist>(lists.length())
        for (i in 0 until lists.length()) {
            val item = lists.optJSONObject(i) ?: continue
            val name = item.optString("specialname").ifBlank { item.optString("name") }.trim()
            val globalId = item.optString("global_collection_id").trim()
            val listId = item.optString("specialid").ifBlank { item.optString("listid") }.trim()
            if (name.isEmpty() || (globalId.isEmpty() && listId.isEmpty())) {
                continue
            }
            mapped.add(
                KugouPlaylist(
                    listId = listId,
                    globalId = globalId,
                    name = name,
                    creatorName = item.optString("nickname").ifBlank { item.optString("username") }.trim(),
                    intro = item.optString("intro").trim(),
                    coverUrl = item.optString("flexible_cover").ifBlank {
                        item.optString("imgurl").ifBlank { item.optString("cover") }
                    }.replace("{size}", "400").trim(),
                    playCount = item.optLong("play_count", item.optLong("playcount", 0L))
                )
            )
        }
        return mapped
    }

    private fun splitFmSongName(rawName: String): Pair<String, String> {
        val marker = " - "
        val index = rawName.indexOf(marker)
        return if (index > 0) {
            rawName.substring(0, index).trim() to rawName.substring(index + marker.length).trim()
        } else {
            "未知歌手" to rawName.trim()
        }
    }

    private fun processPlaylistSongName(rawName: String, singers: String): String {
        if (singers.isBlank()) {
            return rawName.trim()
        }
        val prefix = "$singers - "
        return if (rawName.startsWith(prefix)) rawName.substring(prefix.length).trim() else rawName.trim()
    }

    private fun queryString(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun putIfMissing(target: MutableMap<String, String>, key: String, value: String) {
        if (!target.containsKey(key)) {
            target[key] = value
        }
    }

    private enum class SignatureType {
        DEFAULT,
        V5
    }

    companion object {
        private const val GATEWAY_HOST = "https://gateway.kugou.com"
        private const val LIKE_LIST_ID_FOR_ACTION = "2"
        private val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
    }
}
