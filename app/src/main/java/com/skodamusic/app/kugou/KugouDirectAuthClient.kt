package com.skodamusic.app.kugou

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class KugouDirectQrCode(
    val key: String,
    val imageUrl: String
)

data class KugouDirectQrStatus(
    val status: Int,
    val userId: String,
    val nickname: String,
    val token: String
) {
    val isSuccess: Boolean
        get() = status == STATUS_SUCCESS && token.isNotBlank()

    companion object {
        const val STATUS_EXPIRED = 0
        const val STATUS_WAITING_FOR_SCAN = 1
        const val STATUS_WAITING_FOR_CONFIRM = 2
        const val STATUS_SUCCESS = 4
    }
}

class KugouDirectAuthClient(
    private val log: (String) -> Unit
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6000L, TimeUnit.MILLISECONDS)
        .readTimeout(10000L, TimeUnit.MILLISECONDS)
        .build()

    fun getQrCode(): KugouDirectQrCode? {
        val params = linkedMapOf(
            "appid" to "1001",
            "clientver" to "11040",
            "type" to "1",
            "plat" to "4",
            "srcappid" to "2919",
            "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=3116&"
        )
        val json = executeGet(WEB_HOST, "/v2/qrcode", params, "kugou direct qr key") ?: return null
        val data = responseData(json)
        val key = data.optString("qrcode").trim()
        val imageUrl = data.optString("qrcode_img").trim()
        if (key.isEmpty() || imageUrl.isEmpty()) {
            log("kugou direct qr key missing fields")
            return null
        }
        return KugouDirectQrCode(key = key, imageUrl = imageUrl)
    }

    fun checkQrStatus(key: String): KugouDirectQrStatus? {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            return null
        }
        val params = linkedMapOf(
            "plat" to "4",
            "appid" to KugouDirectSigner.APP_ID,
            "srcappid" to "2919",
            "qrcode" to cleanKey
        )
        val json = executeGet(WEB_HOST, "/v2/get_userinfo_qrcode", params, "kugou direct qr check")
            ?: return null
        val data = responseData(json)
        return KugouDirectQrStatus(
            status = data.optInt("status", json.optInt("status", -1)),
            userId = data.optString("userid").trim(),
            nickname = data.optString("nickname").trim(),
            token = data.optString("token").trim()
        )
    }

    fun downloadBitmap(url: String): Bitmap? {
        val clean = url.trim()
        decodeEmbeddedBitmap(clean)?.let { return it }
        val normalizedUrl = normalizeImageUrl(clean)
        if (normalizedUrl.isNullOrBlank()) {
            log("kugou direct qr image unsupported ref kind=${imageRefKind(clean)}")
            return null
        }
        return runCatching {
            val request = Request.Builder()
                .url(normalizedUrl)
                .header("User-Agent", KugouDirectSigner.USER_AGENT)
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    log("kugou direct qr image http=${response.code()}")
                    return null
                }
                response.body()?.byteStream()?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        }.onFailure { error ->
            log("kugou direct qr image failed ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun normalizeImageUrl(value: String): String? {
        return when {
            value.startsWith("https://") || value.startsWith("http://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "$WEB_HOST$value"
            value.contains("://") -> null
            value.contains(".") && !value.contains(" ") -> "https://$value"
            else -> null
        }
    }

    private fun decodeEmbeddedBitmap(value: String): Bitmap? {
        if (!value.startsWith("data:image")) {
            return null
        }
        val payload = value.substringAfter(',', missingDelimiterValue = "").trim()
        if (payload.isEmpty()) {
            return null
        }
        return runCatching {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.onFailure { error ->
            log("kugou direct qr image data decode failed ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun imageRefKind(value: String): String {
        return when {
            value.isBlank() -> "empty"
            value.startsWith("data:image") -> "data-image"
            value.startsWith("//") -> "protocol-relative"
            value.startsWith("/") -> "relative-path"
            value.contains("://") -> "unsupported-scheme"
            else -> "unknown"
        }
    }

    private fun executeGet(
        baseUrl: String,
        path: String,
        params: Map<String, String>,
        label: String
    ): JSONObject? {
        val signedParams = signedParams(params)
        val request = Request.Builder()
            .url("$baseUrl$path?${queryString(signedParams)}")
            .header("Accept", "application/json")
            .header("User-Agent", KugouDirectSigner.USER_AGENT)
            .header("dfid", signedParams["dfid"].orEmpty())
            .header("mid", signedParams["mid"].orEmpty())
            .header("clienttime", signedParams["clienttime"].orEmpty())
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .build()
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

    private fun signedParams(params: Map<String, String>): LinkedHashMap<String, String> {
        val clientTimeSeconds = System.currentTimeMillis() / 1000L
        val merged = KugouDirectSigner.withDefaultParams(params, clientTimeSeconds)
        merged["signature"] = KugouDirectSigner.calcWebQrSignature(merged)
        return merged
    }

    private fun responseData(json: JSONObject): JSONObject {
        val status = json.optInt("status", -1)
        val data = json.opt("data")
        return if (status == 1 && data is JSONObject) data else json
    }

    private fun queryString(params: Map<String, String>): String {
        return params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    companion object {
        private const val WEB_HOST = "https://login-user.kugou.com"
    }
}
