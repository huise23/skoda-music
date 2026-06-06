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
            .header("x-router", router)
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
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
                    return null
                }
                JSONObject(payload)
            }
        }.onFailure { error ->
            log("$label failed ${error.javaClass.simpleName}")
        }.getOrNull()
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
        private val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
    }
}
