package com.skodamusic.app.kugou

import android.content.Context
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class KugouVipRecord(
    val day: String,
    val isReceived: Boolean,
    val vipType: String
)

data class KugouVipRecordResult(
    val status: Int,
    val records: List<KugouVipRecord>
)

data class KugouVipActionResult(
    val status: Int,
    val errorCode: String,
    val vipType: String
) {
    val success: Boolean
        get() = status == 1
}

class KugouDirectUserClient(
    context: Context,
    private val log: (String) -> Unit
) {
    private val sessionStore = KugouDirectSessionStore(context.applicationContext)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6000L, TimeUnit.MILLISECONDS)
        .readTimeout(12000L, TimeUnit.MILLISECONDS)
        .build()

    fun getVipRecords(): KugouVipRecordResult? {
        val session = sessionStore.load() ?: return null
        val result = executeResult(
            path = "/youth/v1/activity/get_month_vip_record",
            method = "GET",
            session = session,
            label = "kugou direct vip record",
            params = linkedMapOf("latest_limit" to "100")
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val json = result.json ?: return null
        val data = responseData(json)
        val list = data.optJSONArray("list")
            ?: data.optJSONArray("items")
            ?: json.optJSONArray("list")
            ?: JSONArray()
        val records = ArrayList<KugouVipRecord>(list.length())
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val day = item.optString("day").trim()
            if (day.isEmpty()) {
                continue
            }
            records.add(
                KugouVipRecord(
                    day = day,
                    isReceived = item.optInt("receive_vip", 0) == 1,
                    vipType = item.optString("vip_type").trim()
                )
            )
        }
        return KugouVipRecordResult(
            status = json.optInt("status", data.optInt("status", -1)),
            records = records
        )
    }

    fun receiveOneDayVip(receiveDay: String = todayString()): KugouVipActionResult? {
        val session = sessionStore.load() ?: return null
        val result = executeResult(
            path = "/youth/v1/recharge/receive_vip_listen_song",
            method = "POST",
            session = session,
            label = "kugou direct vip receive",
            params = linkedMapOf(
                "source_id" to "90139",
                "receive_day" to receiveDay
            )
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        return parseActionResult(result.json)
    }

    fun upgradeVipReward(): KugouVipActionResult? {
        val session = sessionStore.load() ?: return null
        val result = executeResult(
            path = "/youth/v1/listen_song/upgrade_vip_reward",
            method = "POST",
            session = session,
            label = "kugou direct vip upgrade",
            params = linkedMapOf(
                "kugouid" to session.userId,
                "ad_type" to "1"
            )
        )
        if (result.httpCode !in 200..299) {
            return null
        }
        val parsed = parseActionResult(result.json)
        val vipType = parsed?.vipType.orEmpty()
        if (parsed?.success == true && vipType.isNotBlank()) {
            sessionStore.persistVipType(vipType)
        }
        return parsed
    }

    private fun executeResult(
        path: String,
        method: String,
        session: KugouDirectSessionSnapshot,
        label: String,
        params: LinkedHashMap<String, String> = linkedMapOf()
    ): KugouApiResult {
        val body = ""
        val dfid = session.dfid.ifBlank { "-" }
        val clientTimeSeconds = System.currentTimeMillis() / 1000L
        val signedParams = defaultParams(params, session, dfid, clientTimeSeconds)
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
                }
                KugouApiResult(response.code(), payload, session.token)
            }
        }.onFailure { error ->
            log("$label failed ${error.javaClass.simpleName}")
        }.getOrElse { KugouApiResult(httpCode = -1, payload = "", sessionKey = "") }
    }

    private fun parseActionResult(json: JSONObject?): KugouVipActionResult? {
        if (json == null) {
            return null
        }
        val data = responseData(json)
        return KugouVipActionResult(
            status = json.optInt("status", data.optInt("status", -1)),
            errorCode = json.optString("error_code")
                .ifBlank { data.optString("error_code") }
                .ifBlank { data.optString("errcode") }
                .trim(),
            vipType = data.optString("vip_type")
                .ifBlank { data.optString("vipType") }
                .ifBlank { json.optString("vip_type") }
                .trim()
        )
    }

    private fun responseData(json: JSONObject): JSONObject {
        val data = json.opt("data")
        return if (data is JSONObject) data else json
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

        fun todayString(nowMs: Long = System.currentTimeMillis()): String {
            return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
        }
    }
}
