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

data class KugouSceneItem(
    val sceneId: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val tag: String = ""
)

data class KugouSceneModule(
    val moduleId: String,
    val sceneId: String,
    val title: String,
    val subtitle: String = ""
)

data class KugouSceneSong(
    val name: String,
    val singerName: String,
    val hash: String,
    val audioId: String,
    val albumId: String,
    val albumAudioId: String,
    val durationMs: Long,
    val coverUrl: String
)

class KugouSceneContentClient(
    context: Context,
    private val log: (String) -> Unit
) {
    private val sessionStore = KugouDirectSessionStore(context.applicationContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6000L, TimeUnit.MILLISECONDS)
        .readTimeout(12000L, TimeUnit.MILLISECONDS)
        .build()

    fun getSceneLists(): List<KugouSceneItem>? {
        val session = sessionStore.load() ?: return null
        val json = execute(
            path = "/scene/v1/scene/list",
            method = "GET",
            body = "",
            session = session,
            label = "kugou direct scene list"
        ) ?: return null
        return parseScenes(resolveArray(json, "data", "list", "scene_list", "scenes"))
    }

    fun getSceneModules(sceneId: String): List<KugouSceneModule>? {
        val cleanSceneId = sceneId.trim()
        if (cleanSceneId.isEmpty()) {
            return null
        }
        val session = sessionStore.load() ?: return null
        val json = execute(
            path = "/scene/v1/scene/module",
            method = "POST",
            body = "",
            session = session,
            label = "kugou direct scene modules",
            params = linkedMapOf("scene_id" to cleanSceneId)
        ) ?: return null
        val modules = resolveArray(json, "data", "list", "module_list", "modules")
        val mapped = ArrayList<KugouSceneModule>(modules.length())
        for (i in 0 until modules.length()) {
            val item = modules.optJSONObject(i) ?: continue
            val moduleId = firstString(item, "module_id", "moduleid", "id")
            val title = firstString(item, "module_name", "name", "title")
            if (moduleId.isEmpty() || title.isEmpty()) {
                continue
            }
            mapped.add(
                KugouSceneModule(
                    moduleId = moduleId,
                    sceneId = cleanSceneId,
                    title = title,
                    subtitle = firstString(item, "desc", "description", "subtitle")
                )
            )
        }
        return mapped
    }

    fun getSceneAudios(
        sceneId: String,
        moduleId: String = "",
        tag: String = "",
        page: Int = 1,
        pageSize: Int = 30
    ): List<KugouSceneSong>? {
        val cleanSceneId = sceneId.trim()
        if (cleanSceneId.isEmpty()) {
            return null
        }
        val session = sessionStore.load() ?: return null
        // Mirrors RawMediaCatalogApi.GetSceneAudiosAsync in KugouMusic.NET.
        val body = JSONObject()
            .put("appid", KugouDirectSigner.APP_ID)
            .put("clientver", KugouDirectSigner.CLIENT_VER)
            .put("token", session.token)
            .put("userid", session.userId)
            .toString()
        val json = execute(
            path = "/scene/v1/scene/audio_list",
            method = "POST",
            body = body,
            session = session,
            label = "kugou direct scene audios",
            params = linkedMapOf(
                "scene_id" to cleanSceneId,
                "module_id" to moduleId.trim(),
                "tag" to tag.trim(),
                "page" to page.coerceAtLeast(1).toString(),
                "page_size" to pageSize.coerceAtLeast(1).toString()
            )
        ) ?: return null
        return parseSongs(resolveArray(json, "songs", "list", "audio_list", "data"))
    }

    fun getSceneMusic(sceneId: String, page: Int = 1, pageSize: Int = 30): List<KugouSceneSong>? {
        val cleanSceneId = sceneId.trim()
        if (cleanSceneId.isEmpty()) {
            return null
        }
        val session = sessionStore.load() ?: return null
        val body = JSONObject()
            .put("exposure", JSONArray())
            .toString()
        val json = execute(
            path = "/genesisapi/v1/scene_music/rec_music",
            method = "POST",
            body = body,
            session = session,
            label = "kugou direct scene music",
            params = linkedMapOf(
                "scene_id" to cleanSceneId,
                "page" to page.coerceAtLeast(1).toString(),
                "pagesize" to pageSize.coerceAtLeast(1).toString()
            )
        ) ?: return null
        return parseSongs(resolveArray(json, "songs", "list", "audio_list", "data"))
    }

    private fun execute(
        path: String,
        method: String,
        body: String,
        session: KugouDirectSessionSnapshot,
        label: String,
        params: LinkedHashMap<String, String> = linkedMapOf()
    ): JSONObject? {
        val clientTimeSeconds = System.currentTimeMillis() / 1000L
        val signedParams = defaultParams(params, session, clientTimeSeconds)
        signedParams["signature"] = KugouDirectSigner.calcPostSignature(signedParams, body)
        val requestBuilder = Request.Builder()
            .url("$GATEWAY_HOST$path?${queryString(signedParams)}")
            .header("Accept", "application/json")
            .header("User-Agent", KugouDirectSigner.USER_AGENT)
            .header("dfid", session.dfid.ifBlank { "-" })
            .header("mid", signedParams["mid"].orEmpty())
            .header("clienttime", signedParams["clienttime"].orEmpty())
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
        clientTimeSeconds: Long
    ): LinkedHashMap<String, String> {
        val dfid = session.dfid.ifBlank { "-" }
        val mid = KugouDirectSigner.calcNewMid(dfid)
        val merged = LinkedHashMap(params)
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

    private fun parseScenes(items: JSONArray): List<KugouSceneItem> {
        val mapped = ArrayList<KugouSceneItem>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val sceneId = firstString(item, "scene_id", "sceneid", "id", "tag_id")
            val title = firstString(item, "scene_name", "name", "title", "tag_name")
            if (sceneId.isEmpty() || title.isEmpty()) {
                continue
            }
            mapped.add(
                KugouSceneItem(
                    sceneId = sceneId,
                    title = title,
                    subtitle = firstString(item, "desc", "description", "subtitle"),
                    coverUrl = firstString(item, "img", "image", "image_url", "cover", "pic").replace("{size}", "400"),
                    tag = firstString(item, "tag", "tag_name")
                )
            )
        }
        return mapped
    }

    private fun parseSongs(items: JSONArray): List<KugouSceneSong> {
        val mapped = ArrayList<KugouSceneSong>(items.length())
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val hash = firstString(item, "hash", "file_hash")
            val rawName = firstString(item, "songname", "name", "filename", "audio_name")
            if (hash.isEmpty() || rawName.isEmpty()) {
                continue
            }
            mapped.add(
                KugouSceneSong(
                    name = processSongName(rawName, firstString(item, "singername", "author_name", "singer")),
                    singerName = firstString(item, "singername", "author_name", "singer", "artist").ifBlank { "未知歌手" },
                    hash = hash,
                    audioId = firstString(item, "audio_id", "songid", "fileid"),
                    albumId = firstString(item, "album_id"),
                    albumAudioId = firstString(item, "album_audio_id", "mixsongid", "mix_song_id"),
                    durationMs = firstLong(item, "duration_ms", "timelen", "time", "duration"),
                    coverUrl = firstString(item, "cover", "sizable_cover", "img", "image_url")
                        .ifBlank { item.optJSONObject("trans_param")?.optString("union_cover").orEmpty() }
                        .replace("{size}", "400")
                )
            )
        }
        return mapped
    }

    private fun resolveArray(json: JSONObject, vararg keys: String): JSONArray {
        val root = responseData(json)
        keys.forEach { key ->
            val direct = root.optJSONArray(key)
            if (direct != null) {
                return direct
            }
            val nested = root.optJSONObject(key)
            if (nested != null) {
                val nestedList = nested.optJSONArray("list") ?: nested.optJSONArray("data") ?: nested.optJSONArray("songs")
                if (nestedList != null) {
                    return nestedList
                }
            }
        }
        return JSONArray()
    }

    private fun responseData(json: JSONObject): JSONObject {
        val data = json.opt("data")
        return if (data is JSONObject) data else json
    }

    private fun firstString(item: JSONObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = item.optString(key).trim()
            if (value.isNotEmpty() && value != "null") {
                return value
            }
        }
        return ""
    }

    private fun firstLong(item: JSONObject, vararg keys: String): Long {
        keys.forEach { key ->
            if (item.has(key)) {
                val value = item.optLong(key, -1L)
                if (value > 0L) {
                    return value
                }
            }
        }
        return -1L
    }

    private fun processSongName(rawName: String, singer: String): String {
        val prefix = "$singer - "
        return if (singer.isNotBlank() && rawName.startsWith(prefix)) {
            rawName.substring(prefix.length).trim()
        } else {
            rawName.trim()
        }
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

    companion object {
        private const val GATEWAY_HOST = "https://gateway.kugou.com"
        private val JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
    }
}
