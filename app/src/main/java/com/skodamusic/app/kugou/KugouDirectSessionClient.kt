package com.skodamusic.app.kugou

import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class KugouDirectSessionValidationResult(
    val snapshot: KugouDirectSessionSnapshot?,
    val message: String
) {
    val isSuccess: Boolean
        get() = snapshot != null
}

class KugouDirectSessionClient(
    private val log: (String) -> Unit
) {
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(6000L, TimeUnit.MILLISECONDS)
        .readTimeout(12000L, TimeUnit.MILLISECONDS)
        .build()

    fun validateQrSession(input: KugouDirectSessionSnapshot): KugouDirectSessionValidationResult {
        if (input.token.isBlank() || input.userId == "0") {
            return blocked("qr_session_missing_auth")
        }
        val withDevice = ensureDevice(input) ?: run {
            log("kugou direct session validation deferred stage=device_register")
            return validated(input, "device_register_deferred")
        }
        val refreshed = refreshToken(withDevice) ?: run {
            log("kugou direct session validation deferred stage=token_refresh")
            return validated(withDevice, "token_refresh_deferred")
        }
        log("kugou direct session validation refreshed")
        return validated(refreshed, "direct_refresh_ok")
    }

    private fun validated(
        snapshot: KugouDirectSessionSnapshot,
        reason: String
    ): KugouDirectSessionValidationResult {
        return KugouDirectSessionValidationResult(
            snapshot = snapshot.copy(
                validationState = KugouDirectSessionState.VALID,
                validationReason = reason,
                validatedAtMs = System.currentTimeMillis()
            ),
            message = reason
        )
    }

    private fun ensureDevice(snapshot: KugouDirectSessionSnapshot): KugouDirectSessionSnapshot? {
        if (snapshot.dfid.isNotBlank() && snapshot.dfid != "-") {
            return snapshot
        }
        val hardwareJson = hardwareInfoJson(snapshot)
        val aes = KugouDirectCrypto.playlistAesEncrypt(hardwareJson)
        val pJson = JSONObject()
            .put("aes", aes.key)
            .put("uid", snapshot.userId)
            .put("token", snapshot.token)
            .toString()
        val encryptedP = KugouDirectCrypto.rsaEncryptPkcs1(pJson).uppercase()
        val clientTime = System.currentTimeMillis() / 1000L
        val params = defaultParams(
            currentDfid = "-",
            userId = snapshot.userId,
            token = snapshot.token,
            clientTimeSeconds = clientTime,
            extra = linkedMapOf(
                "part" to "1",
                "platid" to "1",
                "p" to encryptedP,
                "clientver" to KugouDirectSigner.CLIENT_VER,
                "clienttime" to clientTime.toString(),
                "appid" to KugouDirectSigner.APP_ID
            )
        )
        val json = executePost(
            baseUrl = DEVICE_HOST,
            path = "/risk/v2/r_register_dev",
            params = params,
            body = aes.encrypted,
            contentType = "text/plain",
            dfid = "-",
            label = "kugou direct device register"
        ) ?: return null
        val decrypted = decryptDeviceResponse(json, aes.key)
        val data = responseData(decrypted)
        val dfid = data.optString("dfid").trim()
        if (decrypted.optInt("status", -1) != 1 || dfid.isEmpty()) {
            log("kugou direct device register rejected status=${decrypted.optInt("status", -1)}")
            return null
        }
        val mid = KugouDirectSigner.calcNewMid(dfid)
        return snapshot.copy(
            dfid = dfid,
            mid = mid,
            uuid = KugouDirectSigner.md5(dfid + mid)
        )
    }

    private fun refreshToken(snapshot: KugouDirectSessionSnapshot): KugouDirectSessionSnapshot? {
        if (snapshot.token.isBlank() || snapshot.userId == "0") {
            return null
        }
        val dateNow = System.currentTimeMillis()
        val clienttimeSec = dateNow / 1000L
        val t1Raw = if (snapshot.t1.isBlank()) "|$dateNow" else "${snapshot.t1}|$dateNow"
        val t1Enc = KugouDirectCrypto.aesEncrypt(t1Raw, LITE_T1_KEY, LITE_T1_IV).encrypted
        val t2Raw = "${snapshot.installGuid}|$T2_FIXED_HASH|${snapshot.installMac}|${snapshot.installDev}|$dateNow"
        val t2Enc = KugouDirectCrypto.aesEncrypt(t2Raw, LITE_T2_KEY, LITE_T2_IV).encrypted
        val p3Json = JSONObject()
            .put("clienttime", clienttimeSec)
            .put("token", snapshot.token)
            .toString()
        val p3 = KugouDirectCrypto.aesEncrypt(p3Json, LITE_APP_KEY, LITE_APP_IV).encrypted
        val paramsAes = KugouDirectCrypto.aesEncrypt("{}")
        val pkJson = JSONObject()
            .put("clienttime_ms", dateNow)
            .put("key", paramsAes.key)
            .toString()
        val pk = KugouDirectCrypto.rsaEncryptNoPadding(pkJson).uppercase()

        // Mirrors RawLoginApi.RefreshTokenAsync, including its Lite dfid body behavior.
        val body = JSONObject()
            .put("dfid", if (snapshot.dfid.isEmpty()) snapshot.dfid else "-")
            .put("p3", p3)
            .put("plat", 1)
            .put("t1", t1Enc)
            .put("t2", t2Enc)
            .put("t3", "MCwwLDAsMCwwLDAsMCwwLDA=")
            .put("pk", pk)
            .put("params", paramsAes.encrypted)
            .put("userid", snapshot.userId)
            .put("clienttime_ms", dateNow)
            .put("dev", snapshot.installDev)
            .toString()
        val queryParams = defaultParams(
            currentDfid = snapshot.dfid,
            userId = snapshot.userId,
            token = snapshot.token,
            clientTimeSeconds = clienttimeSec
        )
        val json = executePost(
            baseUrl = LOGIN_HOST,
            path = "/v5/login_by_token",
            params = queryParams,
            body = body,
            contentType = "application/json",
            dfid = snapshot.dfid,
            router = LOGIN_ROUTER,
            label = "kugou direct token refresh"
        ) ?: return null
        val merged = decryptRefreshResponse(json, paramsAes.key)
        val data = responseData(merged)
        val status = merged.optInt("status", data.optInt("status", -1))
        val token = data.optString("token").trim()
        val userId = data.optString("userid", snapshot.userId).trim()
        if (status != 1 || token.isEmpty()) {
            log("kugou direct token refresh rejected status=$status")
            return null
        }
        return snapshot.copy(
            userId = userId.ifEmpty { snapshot.userId },
            token = token,
            vipType = data.optString("is_vip", snapshot.vipType).ifEmpty { snapshot.vipType },
            t1 = data.optString("t1", snapshot.t1).trim(),
            savedAtMs = System.currentTimeMillis()
        )
    }

    private fun executePost(
        baseUrl: String,
        path: String,
        params: LinkedHashMap<String, String>,
        body: String,
        contentType: String,
        dfid: String,
        router: String? = null,
        label: String
    ): JSONObject? {
        params["signature"] = KugouDirectSigner.calcPostSignature(params, body)
        val requestBuilder = Request.Builder()
            .url("$baseUrl$path?${queryString(params)}")
            .post(RequestBody.create(MediaType.parse(contentType), body))
            .header("Accept", "application/json")
            .header("User-Agent", KugouDirectSigner.USER_AGENT)
            .header("dfid", dfid.ifBlank { "-" })
            .header("mid", KugouDirectSigner.calcNewMid(dfid.ifBlank { "-" }))
            .header("clienttime", params["clienttime"].orEmpty())
            .header("kg-rc", "1")
            .header("kg-thash", "5d816a0")
            .header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
        if (!router.isNullOrBlank()) {
            requestBuilder.header("x-router", router)
        }
        return runCatching {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val payload = response.body()?.string().orEmpty()
                if (!response.isSuccessful) {
                    log("$label http=${response.code()}")
                    return null
                }
                if (payload.trim().startsWith("{")) {
                    JSONObject(payload)
                } else {
                    JSONObject().put("__raw_base64__", payload.trim())
                }
            }
        }.onFailure { error ->
            log("$label failed ${error.javaClass.simpleName}")
        }.getOrNull()
    }

    private fun defaultParams(
        currentDfid: String,
        userId: String,
        token: String,
        clientTimeSeconds: Long,
        extra: LinkedHashMap<String, String> = linkedMapOf()
    ): LinkedHashMap<String, String> {
        val dfid = currentDfid.ifBlank { "-" }
        val mid = KugouDirectSigner.calcNewMid(dfid)
        val merged = LinkedHashMap(extra)
        putIfMissing(merged, "appid", KugouDirectSigner.APP_ID)
        putIfMissing(merged, "clientver", KugouDirectSigner.CLIENT_VER)
        putIfMissing(merged, "dfid", dfid)
        putIfMissing(merged, "mid", mid)
        putIfMissing(merged, "uuid", KugouDirectSigner.md5(dfid + mid))
        putIfMissing(merged, "userid", userId)
        putIfMissing(merged, "clienttime", clientTimeSeconds.toString())
        if (token.isNotBlank() && !merged.containsKey("token")) {
            merged["token"] = token
        }
        return merged
    }

    private fun decryptDeviceResponse(response: JSONObject, aesKey: String): JSONObject {
        val encrypted = response.optString("__raw_base64__", "").ifBlank {
            if (response.length() == 1) response.optString("data", "") else ""
        }
        if (encrypted.isBlank()) {
            return response
        }
        return runCatching {
            JSONObject(KugouDirectCrypto.playlistAesDecrypt(encrypted, aesKey))
        }.onFailure { error ->
            log("kugou direct device decrypt failed ${error.javaClass.simpleName}")
        }.getOrDefault(response)
    }

    private fun decryptRefreshResponse(response: JSONObject, aesKey: String): JSONObject {
        val data = response.optJSONObject("data") ?: return response
        val secuParams = data.optString("secu_params").trim()
        if (response.optInt("status", -1) != 1 || secuParams.isEmpty()) {
            return response
        }
        return runCatching {
            val decrypted = JSONObject(KugouDirectCrypto.aesDecrypt(secuParams, aesKey))
            val mergedData = JSONObject(data.toString())
            val keys = decrypted.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                mergedData.put(key, decrypted.opt(key))
            }
            JSONObject(response.toString()).put("data", mergedData)
        }.onFailure { error ->
            log("kugou direct refresh decrypt failed ${error.javaClass.simpleName}")
        }.getOrDefault(response)
    }

    private fun responseData(json: JSONObject): JSONObject {
        val data = json.opt("data")
        return if (data is JSONObject) data else json
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

    private fun blocked(reason: String): KugouDirectSessionValidationResult {
        log("kugou direct session validation blocked reason=$reason")
        return KugouDirectSessionValidationResult(snapshot = null, message = reason)
    }

    private fun hardwareInfoJson(snapshot: KugouDirectSessionSnapshot): String {
        return JSONObject()
            .put("availableRamSize", 4983533568L)
            .put("availableRomSize", 48114719L)
            .put("availableSDSize", 48114717L)
            .put("basebandVer", "")
            .put("batteryLevel", 100)
            .put("batteryStatus", 3)
            .put("brand", "Redmi")
            .put("buildSerial", "unknown")
            .put("device", "marble")
            .put("imei", snapshot.installGuid)
            .put("imsi", "")
            .put("manufacturer", "Xiaomi")
            .put("uuid", snapshot.installGuid)
            .put("accelerometer", false)
            .put("accelerometerValue", "")
            .put("gravity", false)
            .put("gravityValue", "")
            .put("gyroscope", false)
            .put("gyroscopeValue", "")
            .put("light", false)
            .put("lightValue", "")
            .put("magnetic", false)
            .put("magneticValue", "")
            .put("orientation", false)
            .put("orientationValue", "")
            .put("pressure", false)
            .put("pressureValue", "")
            .put("step_counter", false)
            .put("step_counterValue", "")
            .put("temperature", false)
            .put("temperatureValue", "")
            .toString()
    }

    companion object {
        private const val DEVICE_HOST = "https://userservice.kugou.com"
        private const val LOGIN_HOST = "http://login.user.kugou.com"
        private const val LOGIN_ROUTER = "login.user.kugou.com"
        private const val LITE_T1_KEY = "5e4ef500e9597fe004bd09a46d8add98"
        private const val LITE_T1_IV = "04bd09a46d8add98"
        private const val LITE_T2_KEY = "fd14b35e3f81af3817a20ae7adae7020"
        private const val LITE_T2_IV = "17a20ae7adae7020"
        private const val T2_FIXED_HASH = "0f607264fc6318a92b9e13c65db7cd3c"
        private const val LITE_APP_KEY = "c24f74ca2820225badc01946dba4fdf7"
        private const val LITE_APP_IV = "adc01946dba4fdf7"
    }
}
