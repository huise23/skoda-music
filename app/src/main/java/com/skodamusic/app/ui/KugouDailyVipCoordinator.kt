package com.skodamusic.app.ui

import android.content.Context
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.kugou.KugouDirectUserClient
import com.skodamusic.app.kugou.KugouVipRecord
import com.skodamusic.app.observability.PostHogTracker

class KugouDailyVipCoordinator(
    context: Context,
    private val backgroundExecutor: AppBackgroundExecutor,
    private val userClient: () -> KugouDirectUserClient,
    private val appendRuntimeLog: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private var inFlight: Boolean = false

    fun trigger(reason: String, userId: String) {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty() || cleanUserId == "0") {
            return
        }
        val today = KugouDirectUserClient.todayString()
        synchronized(lock) {
            if (inFlight || isCoolingDownLocked(cleanUserId, today)) {
                return
            }
            inFlight = true
        }
        captureVipEvent("kugou_daily_vip_start", reason, "start", null, 0)
        backgroundExecutor.execute {
            try {
                runVipFlow(reason = reason, userId = cleanUserId, today = today)
            } finally {
                synchronized(lock) {
                    inFlight = false
                }
            }
        }
    }

    fun reset() {
        synchronized(lock) {
            inFlight = false
        }
    }

    private fun runVipFlow(reason: String, userId: String, today: String) {
        if (prefs.getLong(lastSuccessKey(userId, today), 0L) > 0L) {
            captureVipEvent("kugou_daily_vip_success", reason, "local_success_skip", null, 0)
            return
        }
        val attempt = incrementAttempt(userId, today)
        val records = userClient().getVipRecords()
        if (records == null || records.status != 1) {
            appendRuntimeLog("kugou daily vip record unavailable attempt=$attempt")
            captureVipEvent(
                eventName = "kugou_daily_vip_failed",
                reason = reason,
                stage = "record",
                errorCode = "KUGOU_VIP_RECORD_UNAVAILABLE",
                attempt = attempt
            )
            markFallbackAttempt(userId, today)
            receiveThenUpgrade(reason, userId, today, attempt)
            return
        }
        val todayRecord = records.records.firstOrNull { it.day == today }
        when {
            todayRecord == null -> receiveThenUpgrade(reason, userId, today, attempt)
            todayRecord.vipType == "tvip" -> upgrade(reason, userId, today, attempt, "record_tvip")
            else -> {
                markReceived(userId, today, todayRecord)
                appendRuntimeLog("kugou daily vip already received type=${todayRecord.vipType.ifBlank { "unknown" }}")
                captureVipEvent("kugou_daily_vip_success", reason, "already_received", null, attempt)
            }
        }
    }

    private fun receiveThenUpgrade(reason: String, userId: String, today: String, attempt: Int) {
        val localKey = localReceivedKey(userId, today)
        if (prefs.getLong(lastSuccessKey(userId, today), 0L) > 0L) {
            captureVipEvent("kugou_daily_vip_success", reason, "local_success_skip", null, attempt)
            return
        }
        if (prefs.getBoolean(localKey, false)) {
            appendRuntimeLog("kugou daily vip local fallback already received, retry upgrade")
            upgrade(reason, userId, today, attempt, "local_fallback")
            return
        }
        val receive = userClient().receiveOneDayVip(today)
        if (receive?.success != true) {
            appendRuntimeLog("kugou daily vip receive failed attempt=$attempt code=${receive?.errorCode.orEmpty()}")
            captureVipEvent(
                eventName = "kugou_daily_vip_failed",
                reason = reason,
                stage = "receive",
                errorCode = receive?.errorCode?.ifBlank { null } ?: "KUGOU_VIP_RECEIVE_FAILED",
                attempt = attempt
            )
            scheduleRetryWindow(userId, today, attempt)
            return
        }
        prefs.edit()
            .putBoolean(localKey, true)
            .apply()
        sleepBeforeUpgrade()
        upgrade(reason, userId, today, attempt, "receive")
    }

    private fun upgrade(reason: String, userId: String, today: String, attempt: Int, stage: String) {
        val upgrade = userClient().upgradeVipReward()
        if (upgrade?.success == true) {
            prefs.edit()
                .putBoolean(localReceivedKey(userId, today), true)
                .putLong(lastSuccessKey(userId, today), System.currentTimeMillis())
                .remove(nextRetryKey(userId, today))
                .apply()
            appendRuntimeLog("kugou daily vip upgrade success stage=$stage")
            captureVipEvent("kugou_daily_vip_success", reason, "upgrade", null, attempt)
            return
        }
        appendRuntimeLog("kugou daily vip upgrade failed attempt=$attempt stage=$stage code=${upgrade?.errorCode.orEmpty()}")
        captureVipEvent(
            eventName = "kugou_daily_vip_failed",
            reason = reason,
            stage = "upgrade",
            errorCode = upgrade?.errorCode?.ifBlank { null } ?: "KUGOU_VIP_UPGRADE_FAILED",
            attempt = attempt
        )
        scheduleRetryWindow(userId, today, attempt)
    }

    private fun markReceived(userId: String, today: String, record: KugouVipRecord) {
        prefs.edit()
            .putBoolean(localReceivedKey(userId, today), true)
            .putString(localVipTypeKey(userId, today), record.vipType)
            .putLong(lastSuccessKey(userId, today), System.currentTimeMillis())
            .remove(nextRetryKey(userId, today))
            .apply()
    }

    private fun markFallbackAttempt(userId: String, today: String) {
        prefs.edit()
            .putBoolean(localRecordFallbackKey(userId, today), true)
            .apply()
    }

    private fun incrementAttempt(userId: String, today: String): Int {
        val key = attemptKey(userId, today)
        val current = prefs.getInt(key, 0) + 1
        prefs.edit()
            .putInt(key, current)
            .apply()
        return current
    }

    private fun scheduleRetryWindow(userId: String, today: String, attempt: Int) {
        val retryMs = retryDelayMs(attempt)
        prefs.edit()
            .putLong(nextRetryKey(userId, today), System.currentTimeMillis() + retryMs)
            .apply()
    }

    private fun retryDelayMs(attempt: Int): Long {
        return when {
            attempt <= 1 -> 60_000L
            attempt == 2 -> 5 * 60_000L
            else -> 30 * 60_000L
        }
    }

    private fun isCoolingDownLocked(userId: String, today: String): Boolean {
        val attempts = prefs.getInt(attemptKey(userId, today), 0)
        if (attempts >= MAX_ATTEMPTS_PER_DAY) {
            return true
        }
        val nextRetryAt = prefs.getLong(nextRetryKey(userId, today), 0L)
        return nextRetryAt > System.currentTimeMillis()
    }

    private fun sleepBeforeUpgrade() {
        try {
            Thread.sleep(1000L)
        } catch (ignored: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun captureVipEvent(
        eventName: String,
        reason: String,
        stage: String,
        errorCode: String?,
        attempt: Int
    ) {
        val properties = linkedMapOf<String, Any>(
            "source" to "kugou",
            "feature" to "kugou_daily_vip",
            "stage" to stage,
            "reason" to reason,
            "attempt" to attempt
        )
        if (!errorCode.isNullOrBlank()) {
            properties["error_code"] = normalizeErrorCode(errorCode)
        }
        PostHogTracker.capture(
            context = appContext,
            eventName = eventName,
            properties = properties,
            priority = if (errorCode.isNullOrBlank()) {
                PostHogTracker.Priority.NORMAL
            } else {
                PostHogTracker.Priority.HIGH
            }
        )
    }

    private fun normalizeErrorCode(value: String): String {
        val clean = value.trim()
        return if (clean.isEmpty()) "UNKNOWN" else clean.take(64)
    }

    private fun localReceivedKey(userId: String, day: String): String = "received_${safeUserKey(userId)}_$day"
    private fun localVipTypeKey(userId: String, day: String): String = "vip_type_${safeUserKey(userId)}_$day"
    private fun localRecordFallbackKey(userId: String, day: String): String = "fallback_${safeUserKey(userId)}_$day"
    private fun attemptKey(userId: String, day: String): String = "attempt_${safeUserKey(userId)}_$day"
    private fun nextRetryKey(userId: String, day: String): String = "next_retry_${safeUserKey(userId)}_$day"
    private fun lastSuccessKey(userId: String, day: String): String = "success_${safeUserKey(userId)}_$day"

    private fun safeUserKey(userId: String): String {
        return userId.trim().takeLast(6).replace(Regex("[^A-Za-z0-9_]"), "_")
    }

    companion object {
        private const val PREFS_NAME = "kugou_daily_vip"
        private const val MAX_ATTEMPTS_PER_DAY = 5
    }
}
