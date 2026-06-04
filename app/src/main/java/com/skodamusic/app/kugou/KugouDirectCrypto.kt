package com.skodamusic.app.kugou

import android.util.Base64
import java.math.BigInteger
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class KugouAesPayload(
    val encrypted: String,
    val key: String
)

object KugouDirectCrypto {
    private const val RANDOM_CHARS = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val PUBLIC_LITE_RSA_KEY =
        "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB"

    private val random = SecureRandom()

    fun randomString(length: Int = 16): String {
        val out = StringBuilder(length)
        repeat(length) {
            out.append(RANDOM_CHARS[random.nextInt(RANDOM_CHARS.length)])
        }
        return out.toString()
    }

    fun aesEncrypt(data: String, key: String? = null, iv: String? = null): KugouAesPayload {
        val tempKey = key ?: randomString().lowercase()
        val actualKey: String
        val actualIv: String
        if (!key.isNullOrEmpty() && !iv.isNullOrEmpty()) {
            actualKey = key
            actualIv = iv
        } else {
            val md5 = KugouDirectSigner.md5(tempKey)
            actualKey = md5
            actualIv = md5.substring(md5.length - 16)
        }
        val encrypted = aesCbc(
            data = data.toByteArray(Charsets.UTF_8),
            key = actualKey,
            iv = actualIv,
            mode = Cipher.ENCRYPT_MODE
        )
        return KugouAesPayload(encrypted = toHex(encrypted), key = tempKey)
    }

    fun aesDecrypt(hexData: String, key: String): String {
        val md5 = KugouDirectSigner.md5(key)
        val decrypted = aesCbc(
            data = fromHex(hexData),
            key = md5,
            iv = md5.substring(md5.length - 16),
            mode = Cipher.DECRYPT_MODE
        )
        return String(decrypted, Charsets.UTF_8)
    }

    fun playlistAesEncrypt(json: String): KugouAesPayload {
        val key = randomString(6).lowercase()
        val md5 = KugouDirectSigner.md5(key)
        val encrypted = aesCbc(
            data = json.toByteArray(Charsets.UTF_8),
            key = md5.substring(0, 16),
            iv = md5.substring(16, 32),
            mode = Cipher.ENCRYPT_MODE
        )
        return KugouAesPayload(
            encrypted = Base64.encodeToString(encrypted, Base64.NO_WRAP),
            key = key
        )
    }

    fun playlistAesDecrypt(base64Data: String, key: String): String {
        val md5 = KugouDirectSigner.md5(key)
        val decrypted = aesCbc(
            data = Base64.decode(base64Data, Base64.NO_WRAP),
            key = md5.substring(0, 16),
            iv = md5.substring(16, 32),
            mode = Cipher.DECRYPT_MODE
        )
        return String(decrypted, Charsets.UTF_8)
    }

    fun rsaEncryptNoPadding(data: String): String {
        val publicKey = publicLiteRsaKey()
        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val padded = ByteArray(128)
        System.arraycopy(dataBytes, 0, padded, 0, dataBytes.size.coerceAtMost(padded.size))
        val modulus = (publicKey as java.security.interfaces.RSAPublicKey).modulus
        val exponent = publicKey.publicExponent
        val encrypted = BigInteger(1, padded).modPow(exponent, modulus).toByteArray()
        return toHex(fitRsaBlock(encrypted))
    }

    fun rsaEncryptPkcs1(data: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicLiteRsaKey())
        return toHex(cipher.doFinal(data.toByteArray(Charsets.UTF_8)))
    }

    private fun aesCbc(data: ByteArray, key: String, iv: String, mode: Int): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(mode, secretKey, IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return cipher.doFinal(data)
    }

    private fun publicLiteRsaKey(): java.security.PublicKey {
        val bytes = Base64.decode(PUBLIC_LITE_RSA_KEY, Base64.NO_WRAP)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun fitRsaBlock(bytes: ByteArray): ByteArray {
        val normalized = if (bytes.size > 1 && bytes[0].toInt() == 0) {
            bytes.copyOfRange(1, bytes.size)
        } else {
            bytes
        }
        if (normalized.size == 128) {
            return normalized
        }
        val out = ByteArray(128)
        if (normalized.size < 128) {
            System.arraycopy(normalized, 0, out, 128 - normalized.size, normalized.size)
        } else {
            System.arraycopy(normalized, normalized.size - 128, out, 0, 128)
        }
        return out
    }

    private fun toHex(bytes: ByteArray): String {
        val hex = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val item = Integer.toHexString(byte.toInt() and 0xff)
            if (item.length == 1) {
                hex.append('0')
            }
            hex.append(item)
        }
        return hex.toString()
    }

    private fun fromHex(value: String): ByteArray {
        val out = ByteArray(value.length / 2)
        var i = 0
        while (i < value.length) {
            out[i / 2] = Integer.parseInt(value.substring(i, i + 2), 16).toByte()
            i += 2
        }
        return out
    }
}
