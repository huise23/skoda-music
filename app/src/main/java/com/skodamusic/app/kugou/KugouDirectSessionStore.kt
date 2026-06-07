package com.skodamusic.app.kugou

import android.content.Context
import java.util.UUID

object KugouDirectSessionState {
    const val PENDING_VALIDATION = "pending_validation"
    const val VALID = "valid"
    const val BLOCKED = "blocked"
}

data class KugouDirectSessionSnapshot(
    val userId: String,
    val token: String,
    val nickname: String,
    val savedAtMs: Long,
    val dfid: String = "-",
    val mid: String = "-",
    val uuid: String = "-",
    val installGuid: String = "",
    val installMac: String = "",
    val installDev: String = "",
    val vipType: String = "0",
    val t1: String = "",
    val validationState: String = KugouDirectSessionState.PENDING_VALIDATION,
    val validationReason: String = "",
    val validatedAtMs: Long = 0L
)

class KugouDirectSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): KugouDirectSessionSnapshot? {
        val userId = prefs.getString(KEY_USER_ID, "").orEmpty().trim()
        val token = prefs.getString(KEY_TOKEN, "").orEmpty().trim()
        if (userId.isEmpty() || userId == "0" || token.isEmpty()) {
            return null
        }
        val installGuid = prefs.getString(KEY_INSTALL_GUID, "").orEmpty().trim().ifEmpty { newInstallId() }
        val installMac = prefs.getString(KEY_INSTALL_MAC, "").orEmpty().trim().ifEmpty { newInstallId() }
        val installDev = prefs.getString(KEY_INSTALL_DEV, "").orEmpty().trim().ifEmpty {
            KugouDirectCrypto.randomString()
        }
        val dfid = prefs.getString(KEY_DFID, "-").orEmpty().trim().ifEmpty { "-" }
        val mid = prefs.getString(KEY_MID, "").orEmpty().trim().ifEmpty {
            if (dfid != "-") KugouDirectSigner.calcNewMid(dfid) else KugouDirectSigner.calcNewMid(installGuid)
        }
        return KugouDirectSessionSnapshot(
            userId = userId,
            token = token,
            nickname = prefs.getString(KEY_NICKNAME, "").orEmpty().trim(),
            savedAtMs = prefs.getLong(KEY_SAVED_AT_MS, 0L),
            dfid = dfid,
            mid = mid,
            uuid = prefs.getString(KEY_UUID, "-").orEmpty().trim().ifEmpty { "-" },
            installGuid = installGuid,
            installMac = installMac,
            installDev = installDev,
            vipType = prefs.getString(KEY_VIP_TYPE, "0").orEmpty().trim().ifEmpty { "0" },
            t1 = prefs.getString(KEY_T1, "").orEmpty().trim(),
            validationState = prefs.getString(
                KEY_VALIDATION_STATE,
                KugouDirectSessionState.PENDING_VALIDATION
            ).orEmpty().trim().ifEmpty { KugouDirectSessionState.PENDING_VALIDATION },
            validationReason = prefs.getString(KEY_VALIDATION_REASON, "").orEmpty().trim(),
            validatedAtMs = prefs.getLong(KEY_VALIDATED_AT_MS, 0L)
        )
    }

    fun newPendingSession(userId: String, token: String, nickname: String): KugouDirectSessionSnapshot {
        val installGuid = existingInstallValue(KEY_INSTALL_GUID) ?: newInstallId()
        val installMac = existingInstallValue(KEY_INSTALL_MAC) ?: newInstallId()
        val installDev = existingInstallValue(KEY_INSTALL_DEV) ?: KugouDirectCrypto.randomString()
        return KugouDirectSessionSnapshot(
            userId = userId.trim(),
            token = token.trim(),
            nickname = nickname.trim(),
            savedAtMs = System.currentTimeMillis(),
            dfid = "-",
            mid = KugouDirectSigner.calcNewMid(installGuid),
            uuid = "-",
            installGuid = installGuid,
            installMac = installMac,
            installDev = installDev,
            validationState = KugouDirectSessionState.PENDING_VALIDATION,
            validationReason = "qr_token_pending_refresh"
        )
    }

    fun newValidatedQrSession(userId: String, token: String, nickname: String): KugouDirectSessionSnapshot {
        return newPendingSession(userId, token, nickname).copy(
            validationState = KugouDirectSessionState.VALID,
            validationReason = "qr_token_ok",
            validatedAtMs = System.currentTimeMillis()
        )
    }

    fun persist(session: KugouDirectSessionSnapshot) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId.trim())
            .putString(KEY_TOKEN, session.token.trim())
            .putString(KEY_NICKNAME, session.nickname.trim())
            .putLong(KEY_SAVED_AT_MS, session.savedAtMs)
            .putString(KEY_DFID, session.dfid.trim().ifEmpty { "-" })
            .putString(KEY_MID, session.mid.trim())
            .putString(KEY_UUID, session.uuid.trim().ifEmpty { "-" })
            .putString(KEY_INSTALL_GUID, session.installGuid.trim())
            .putString(KEY_INSTALL_MAC, session.installMac.trim())
            .putString(KEY_INSTALL_DEV, session.installDev.trim())
            .putString(KEY_VIP_TYPE, session.vipType.trim().ifEmpty { "0" })
            .putString(KEY_T1, session.t1.trim())
            .putString(KEY_VALIDATION_STATE, session.validationState.trim())
            .putString(KEY_VALIDATION_REASON, session.validationReason.trim())
            .putLong(KEY_VALIDATED_AT_MS, session.validatedAtMs)
            .apply()
    }

    fun persistVipType(vipType: String) {
        val clean = vipType.trim()
        if (clean.isEmpty()) {
            return
        }
        prefs.edit()
            .putString(KEY_VIP_TYPE, clean)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun existingInstallValue(key: String): String? {
        return prefs.getString(key, "").orEmpty().trim().ifEmpty { null }
    }

    private fun newInstallId(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    companion object {
        private const val PREFS_NAME = "kugou_direct_session"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_TOKEN = "token"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_SAVED_AT_MS = "saved_at_ms"
        private const val KEY_DFID = "dfid"
        private const val KEY_MID = "mid"
        private const val KEY_UUID = "uuid"
        private const val KEY_INSTALL_GUID = "install_guid"
        private const val KEY_INSTALL_MAC = "install_mac"
        private const val KEY_INSTALL_DEV = "install_dev"
        private const val KEY_VIP_TYPE = "vip_type"
        private const val KEY_T1 = "t1"
        private const val KEY_VALIDATION_STATE = "validation_state"
        private const val KEY_VALIDATION_REASON = "validation_reason"
        private const val KEY_VALIDATED_AT_MS = "validated_at_ms"
    }
}
