package com.skodamusic.app.kugou

import java.math.BigInteger
import java.security.MessageDigest
import java.util.TreeMap

object KugouDirectSigner {
    const val APP_ID = "3116"
    const val CLIENT_VER = "11440"
    const val USER_AGENT = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi"

    private const val LITE_SIGNATURE_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
    private const val WEB_SIGNATURE_SALT = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    private const val V5_KEY_SALT = "185672dd44712f60bb1736df5a377e82"

    fun withDefaultParams(params: Map<String, String>, clientTimeSeconds: Long): LinkedHashMap<String, String> {
        val merged = LinkedHashMap(params)
        val dfid = "-"
        val mid = calcNewMid(dfid)
        putIfMissing(merged, "appid", APP_ID)
        putIfMissing(merged, "clientver", CLIENT_VER)
        putIfMissing(merged, "dfid", dfid)
        putIfMissing(merged, "mid", mid)
        putIfMissing(merged, "uuid", md5(dfid + mid))
        putIfMissing(merged, "userid", "0")
        putIfMissing(merged, "clienttime", clientTimeSeconds.toString())
        return merged
    }

    fun calcWebQrSignature(params: Map<String, String>): String {
        val sorted = TreeMap<String, String>(params)
        val raw = StringBuilder(WEB_SIGNATURE_SALT)
        for ((key, value) in sorted) {
            raw.append(key).append('=').append(value)
        }
        raw.append(WEB_SIGNATURE_SALT)
        return md5(raw.toString())
    }

    fun calcPostSignature(params: Map<String, String>, body: String): String {
        val sorted = TreeMap<String, String>(params)
        val raw = StringBuilder(LITE_SIGNATURE_SALT)
        for ((key, value) in sorted) {
            raw.append(key).append('=').append(value)
        }
        if (body.isNotEmpty()) {
            raw.append(body)
        }
        raw.append(LITE_SIGNATURE_SALT)
        return md5(raw.toString())
    }

    fun calcV5Key(hash: String, userId: String, mid: String): String {
        return md5(hash.lowercase() + V5_KEY_SALT + APP_ID + mid + userId)
    }

    fun calcNewMid(value: String): String {
        val md5Hex = md5(value)
        if (md5Hex.isEmpty()) {
            return ""
        }
        return BigInteger("0$md5Hex", 16).toString()
    }

    fun md5(value: String): String {
        if (value.isEmpty()) {
            return ""
        }
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(digest.size * 2)
        for (byte in digest) {
            val item = Integer.toHexString(byte.toInt() and 0xff)
            if (item.length == 1) {
                hex.append('0')
            }
            hex.append(item)
        }
        return hex.toString()
    }

    private fun putIfMissing(target: MutableMap<String, String>, key: String, value: String) {
        if (!target.containsKey(key)) {
            target[key] = value
        }
    }
}
