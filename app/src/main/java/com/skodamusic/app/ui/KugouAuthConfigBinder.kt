package com.skodamusic.app.ui

import android.graphics.Bitmap
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.skodamusic.app.R
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor
import com.skodamusic.app.kugou.KugouDirectAuthClient
import com.skodamusic.app.kugou.KugouDirectQrStatus
import com.skodamusic.app.kugou.KugouDirectSessionClient
import com.skodamusic.app.kugou.KugouDirectSessionSnapshot
import com.skodamusic.app.kugou.KugouDirectSessionState
import com.skodamusic.app.kugou.KugouDirectSessionStore
import com.skodamusic.app.kugou.KugouDirectSigner
import com.skodamusic.app.kugou.KugouSessionStore
import com.skodamusic.app.kugou.KugouWebApiClient
import com.skodamusic.app.observability.PostHogTracker

class KugouAuthConfigBinder(
    private val activity: AppCompatActivity,
    private val uiProgressHandler: Handler,
    private val backgroundExecutor: AppBackgroundExecutor,
    private val pollIntervalMs: Long,
    private val ensureWifiConnectedForNetworkRequest: (String, Boolean) -> Boolean,
    private val setFeedbackText: (String) -> Unit,
    private val showToast: (Int) -> Unit,
    private val appendRuntimeLog: (String) -> Unit,
    private val onSessionCleared: () -> Unit
) {
    val webApiClient = KugouWebApiClient { message -> appendRuntimeLog(message) }

    private val sessionStore = KugouSessionStore(activity.applicationContext)
    private val directSessionStore = KugouDirectSessionStore(activity.applicationContext)
    private val directAuthClient = KugouDirectAuthClient { message -> appendRuntimeLog(message) }
    private val directSessionClient = KugouDirectSessionClient { message -> appendRuntimeLog(message) }
    private lateinit var statusValue: TextView
    private lateinit var homeStatusValue: TextView
    private lateinit var qrStatusValue: TextView
    private lateinit var qrUrlValue: TextView
    private lateinit var qrImage: ImageView
    private lateinit var refreshQrButton: Button
    private lateinit var mobileInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var sendSmsButton: Button
    private lateinit var smsLoginButton: Button
    private lateinit var logoutButton: Button
    private lateinit var homeLoginPanel: View

    var sessionKey: String = ""
        private set
    var lastUserId: String = ""
        private set
    var displayName: String = ""
        private set
    private var qrKey: String = ""
    private var qrPollingActive: Boolean = false
    private var qrPollGeneration: Int = 0
    private var qrRequestGeneration: Int = 0
    private var sessionValidationGeneration: Int = 0
    private var directValidationState: String = KugouDirectSessionState.PENDING_VALIDATION

    fun bindViews() {
        statusValue = activity.findViewById(R.id.kugou_status_value)
        homeStatusValue = activity.findViewById(R.id.kugou_home_status_value)
        qrStatusValue = activity.findViewById(R.id.kugou_qr_status_value)
        qrUrlValue = activity.findViewById(R.id.kugou_qr_url_value)
        qrImage = activity.findViewById(R.id.kugou_qr_image)
        refreshQrButton = activity.findViewById(R.id.btn_kugou_refresh_qr)
        mobileInput = activity.findViewById(R.id.kugou_mobile_input)
        codeInput = activity.findViewById(R.id.kugou_code_input)
        sendSmsButton = activity.findViewById(R.id.btn_kugou_send_sms)
        smsLoginButton = activity.findViewById(R.id.btn_kugou_sms_login)
        logoutButton = activity.findViewById(R.id.btn_kugou_logout)
        homeLoginPanel = activity.findViewById(R.id.kugou_home_login_panel)

        refreshQrButton.setOnClickListener { requestQrLogin() }
        sendSmsButton.setOnClickListener { requestSmsCode() }
        smsLoginButton.setOnClickListener { requestSmsLogin() }
        logoutButton.setOnClickListener { requestLogout() }
    }

    fun restoreFromCache() {
        sessionStore.clearLegacyWebApiState()
        val snapshot = directSessionStore.load()
        if (snapshot == null) {
            clearSessionState(clearStored = false)
            refreshLoginUi()
            setQrText(activity.getString(R.string.kugou_qr_waiting))
            return
        }
        sessionKey = snapshot.token
        lastUserId = snapshot.userId
        displayName = snapshot.nickname
        directValidationState = snapshot.validationState
        if (snapshot.validationState == KugouDirectSessionState.VALID) {
            refreshLoginUi()
            setQrText(activity.getString(R.string.kugou_qr_success))
            setFeedbackText(activity.getString(R.string.feedback_kugou_login_success))
        } else {
            refreshLoginUi()
            setQrText(activity.getString(R.string.kugou_session_validating))
            setFeedbackText(activity.getString(R.string.feedback_kugou_session_validating))
            validateDirectSession(snapshot)
        }
    }

    fun requestQrLogin() {
        if (!ensureWifiConnectedForNetworkRequest("kugou_direct_qr", true)) {
            captureAuthEvent(
                eventName = "kugou_qr_refresh_failed",
                stage = "network_gate",
                errorCode = "WIFI_NOT_CONNECTED",
                priority = PostHogTracker.Priority.HIGH
            )
            return
        }
        stopQrPolling()
        clearSessionState(clearStored = true)
        val generation = ++qrRequestGeneration
        val startedAtMs = System.currentTimeMillis()
        setStatusText(activity.getString(R.string.kugou_status_not_logged_in))
        setQrText(activity.getString(R.string.kugou_qr_loading))
        setFeedbackText(activity.getString(R.string.feedback_kugou_qr_loading))
        qrImage.setImageDrawable(null)
        refreshQrButton.isEnabled = false
        captureAuthEvent(
            eventName = "kugou_qr_refresh_start",
            stage = "qr_key"
        )
        backgroundExecutor.execute {
            val result = loadQrRefreshResult()
            activity.runOnUiThread {
                if (!isCurrentQrRequest(generation)) {
                    return@runOnUiThread
                }
                refreshQrButton.isEnabled = !hasSession()
                val qr = result.qr
                val bitmap = result.bitmap
                if (qr == null || bitmap == null) {
                    handleQrRefreshFailure(result)
                    return@runOnUiThread
                }
                qrKey = qr.key
                qrUrlValue.text = qr.imageUrl
                qrImage.setImageBitmap(bitmap)
                setQrText(activity.getString(R.string.kugou_qr_scan))
                captureAuthEvent(
                    eventName = "kugou_qr_refresh_success",
                    stage = "qr_image",
                    elapsedMs = System.currentTimeMillis() - startedAtMs
                )
                startQrPolling(qr.key)
            }
        }
    }

    fun stop() {
        stopQrPolling()
        qrRequestGeneration += 1
        sessionValidationGeneration += 1
    }

    fun clearSessionState(clearStored: Boolean) {
        stopQrPolling()
        sessionKey = ""
        lastUserId = ""
        displayName = ""
        qrKey = ""
        qrRequestGeneration += 1
        directValidationState = KugouDirectSessionState.PENDING_VALIDATION
        sessionValidationGeneration += 1
        if (this::qrImage.isInitialized) {
            qrImage.setImageDrawable(null)
        }
        if (this::qrUrlValue.isInitialized) {
            qrUrlValue.text = activity.getString(R.string.kugou_qr_waiting)
        }
        if (clearStored) {
            sessionStore.clearLegacyWebApiState()
            directSessionStore.clear()
        }
        refreshLoginUi()
        onSessionCleared()
    }

    fun refreshLoginUi() {
        val hasSession = hasSession()
        val userLabel = when {
            displayName.isNotBlank() -> displayName
            lastUserId.isNotBlank() -> lastUserId
            else -> "--"
        }
        val statusText = when {
            hasSession -> activity.getString(R.string.kugou_status_logged_in_format, userLabel)
            sessionKey.isNotBlank() && directValidationState == KugouDirectSessionState.BLOCKED ->
                activity.getString(R.string.kugou_status_failed)
            sessionKey.isNotBlank() -> activity.getString(R.string.kugou_status_checking)
            else -> activity.getString(R.string.kugou_status_not_logged_in)
        }
        setStatusText(statusText)
        homeLoginPanel.visibility = if (hasSession) View.GONE else View.VISIBLE
        refreshQrButton.isEnabled = !hasSession
        logoutButton.isEnabled = sessionKey.isNotBlank()
    }

    fun resolveBaseUrl(): String {
        return ""
    }

    fun hasSession(): Boolean {
        return sessionKey.isNotBlank() &&
            lastUserId.isNotBlank() &&
            lastUserId != "0" &&
            directValidationState == KugouDirectSessionState.VALID
    }

    fun updateSessionKey(newSessionKey: String) {
        val clean = newSessionKey.trim()
        if (clean.isEmpty()) {
            return
        }
        appendRuntimeLog("kugou session update ignored reason=direct-api-pending")
    }

    private fun stopQrPolling() {
        qrPollingActive = false
        qrPollGeneration += 1
    }

    private fun startQrPolling(qrKey: String) {
        val generation = ++qrPollGeneration
        qrPollingActive = true
        uiProgressHandler.postDelayed(object : Runnable {
            override fun run() {
                if (!qrPollingActive || generation != qrPollGeneration) {
                    return
                }
                backgroundExecutor.execute {
                    val status = runCatching {
                        directAuthClient.checkQrStatus(qrKey)
                    }.onFailure { error ->
                        appendRuntimeLog("kugou qr poll exception type=${error.javaClass.simpleName}")
                        captureAuthEvent(
                            eventName = "kugou_qr_poll_failed",
                            stage = "qr_poll",
                            errorCode = "QR_POLL_EXCEPTION",
                            errorType = error.javaClass.simpleName,
                            priority = PostHogTracker.Priority.HIGH
                        )
                    }.getOrNull()
                    activity.runOnUiThread {
                        if (!qrPollingActive || generation != qrPollGeneration) {
                            return@runOnUiThread
                        }
                        when {
                            status == null -> {
                                captureAuthEvent(
                                    eventName = "kugou_qr_poll_failed",
                                    stage = "qr_poll",
                                    errorCode = "QR_POLL_EMPTY_OR_FAILED",
                                    priority = PostHogTracker.Priority.HIGH
                                )
                                uiProgressHandler.postDelayed(this, pollIntervalMs)
                            }
                            status.status == KugouDirectQrStatus.STATUS_WAITING_FOR_SCAN -> {
                                setQrText(activity.getString(R.string.kugou_qr_scan))
                                uiProgressHandler.postDelayed(this, pollIntervalMs)
                            }
                            status.status == KugouDirectQrStatus.STATUS_WAITING_FOR_CONFIRM -> {
                                setQrText(activity.getString(R.string.kugou_qr_confirm))
                                uiProgressHandler.postDelayed(this, pollIntervalMs)
                            }
                            status.isSuccess -> applyDirectLogin(status)
                            status.status == KugouDirectQrStatus.STATUS_EXPIRED -> {
                                stopQrPolling()
                                setQrText(activity.getString(R.string.kugou_qr_expired))
                                captureAuthEvent(
                                    eventName = "kugou_qr_poll_failed",
                                    stage = "qr_poll",
                                    errorCode = "QR_EXPIRED"
                                )
                            }
                            else -> uiProgressHandler.postDelayed(this, pollIntervalMs)
                        }
                    }
                }
            }
        }, pollIntervalMs)
    }

    private fun requestSmsCode() {
        showSmsDirectPending()
        showToast(R.string.toast_kugou_failed)
    }

    private fun requestSmsLogin() {
        showSmsDirectPending()
        showToast(R.string.toast_kugou_failed)
    }

    private fun requestLogout() {
        stopQrPolling()
        clearSessionState(clearStored = true)
        setFeedbackText(activity.getString(R.string.feedback_kugou_logged_out))
        showToast(R.string.toast_kugou_logged_out)
    }

    private fun applyDirectLogin(status: KugouDirectQrStatus) {
        stopQrPolling()
        val pendingSession = directSessionStore.newPendingSession(
            userId = status.userId.ifBlank { "0" },
            token = status.token,
            nickname = status.nickname
        )
        directSessionStore.persist(pendingSession)
        sessionKey = pendingSession.token
        lastUserId = pendingSession.userId
        displayName = pendingSession.nickname
        directValidationState = pendingSession.validationState
        setQrText(activity.getString(R.string.kugou_session_validating))
        refreshLoginUi()
        setFeedbackText(activity.getString(R.string.feedback_kugou_session_validating))
        appendRuntimeLog("kugou direct qr login success userHash=${safeHash(lastUserId)} validation=pending")
        captureAuthEvent(
            eventName = "kugou_qr_login_success",
            stage = "qr_poll"
        )
        validateDirectSession(pendingSession)
    }

    private fun validateDirectSession(snapshot: KugouDirectSessionSnapshot) {
        val generation = ++sessionValidationGeneration
        directValidationState = KugouDirectSessionState.PENDING_VALIDATION
        refreshLoginUi()
        backgroundExecutor.execute {
            val result = directSessionClient.validateQrSession(snapshot)
            activity.runOnUiThread {
                if (generation != sessionValidationGeneration) {
                    return@runOnUiThread
                }
                val validated = result.snapshot
                if (validated == null) {
                    val blocked = snapshot.copy(
                        validationState = KugouDirectSessionState.BLOCKED,
                        validationReason = result.message,
                        validatedAtMs = System.currentTimeMillis()
                    )
                    directSessionStore.persist(blocked)
                    sessionKey = blocked.token
                    lastUserId = blocked.userId
                    displayName = blocked.nickname
                    directValidationState = blocked.validationState
                    setQrText(activity.getString(R.string.kugou_session_blocked))
                    refreshLoginUi()
                    setFeedbackText(activity.getString(R.string.feedback_kugou_session_blocked))
                    appendRuntimeLog("kugou direct session validation blocked reason=${result.message}")
                    captureAuthEvent(
                        eventName = "kugou_session_validation_failed",
                        stage = "session_validation",
                        errorCode = result.message.ifBlank { "SESSION_VALIDATION_FAILED" },
                        priority = PostHogTracker.Priority.HIGH
                    )
                    showToast(R.string.toast_kugou_failed)
                    return@runOnUiThread
                }
                directSessionStore.persist(validated)
                sessionKey = validated.token
                lastUserId = validated.userId
                displayName = validated.nickname
                directValidationState = validated.validationState
                setQrText(activity.getString(R.string.kugou_qr_success))
                refreshLoginUi()
                setFeedbackText(activity.getString(R.string.feedback_kugou_login_success))
                appendRuntimeLog("kugou direct session validated userHash=${safeHash(lastUserId)}")
                captureAuthEvent(
                    eventName = "kugou_session_validation_success",
                    stage = "session_validation"
                )
                showToast(R.string.toast_kugou_success)
            }
        }
    }

    private fun setStatusText(text: String) {
        statusValue.text = text
        homeStatusValue.text = text
    }

    private fun setQrText(text: String) {
        qrStatusValue.text = text
        if (qrKey.isEmpty()) {
            qrUrlValue.text = text
        }
    }

    private fun showSmsDirectPending() {
        setFeedbackText(activity.getString(R.string.feedback_kugou_sms_direct_pending))
        appendRuntimeLog("kugou direct sms login pending: RawLoginApi LoginByMobile requires AES/RSA port")
    }

    private fun loadQrRefreshResult(): QrRefreshResult {
        val qr = runCatching {
            directAuthClient.getQrCode()
        }.onFailure { error ->
            appendRuntimeLog("kugou qr refresh exception stage=qr_key type=${error.javaClass.simpleName}")
        }.getOrNull() ?: return QrRefreshResult(
            qr = null,
            bitmap = null,
            failureStage = "qr_key",
            errorCode = "QR_KEY_UNAVAILABLE"
        )
        val bitmap = runCatching {
            directAuthClient.downloadBitmap(qr.imageUrl)
        }.onFailure { error ->
            appendRuntimeLog("kugou qr refresh exception stage=qr_image type=${error.javaClass.simpleName}")
        }.getOrNull() ?: return QrRefreshResult(
            qr = qr,
            bitmap = null,
            failureStage = "qr_image",
            errorCode = "QR_IMAGE_UNAVAILABLE"
        )
        return QrRefreshResult(
            qr = qr,
            bitmap = bitmap,
            failureStage = "",
            errorCode = ""
        )
    }

    private fun handleQrRefreshFailure(result: QrRefreshResult) {
        setStatusText(activity.getString(R.string.kugou_status_failed))
        setQrText(activity.getString(R.string.kugou_qr_expired))
        setFeedbackText(activity.getString(R.string.feedback_kugou_login_failed))
        qrImage.setImageDrawable(null)
        appendRuntimeLog(
            "kugou qr refresh failed stage=${result.failureStage} code=${result.errorCode}"
        )
        captureAuthEvent(
            eventName = "kugou_qr_refresh_failed",
            stage = result.failureStage.ifBlank { "qr_refresh" },
            errorCode = result.errorCode.ifBlank { "QR_REFRESH_FAILED" },
            priority = PostHogTracker.Priority.HIGH
        )
        showToast(R.string.toast_kugou_failed)
    }

    private fun isCurrentQrRequest(generation: Int): Boolean {
        // Async QR results can outlive a refresh/logout/background transition; stale UI writes are ignored.
        return generation == qrRequestGeneration && !activity.isFinishing && !activity.isDestroyed
    }

    private fun captureAuthEvent(
        eventName: String,
        stage: String,
        errorCode: String? = null,
        errorType: String? = null,
        elapsedMs: Long? = null,
        priority: PostHogTracker.Priority = PostHogTracker.Priority.NORMAL
    ) {
        val properties = linkedMapOf<String, Any?>(
            "source" to "kugou",
            "feature" to "kugou_auth",
            "stage" to stage
        )
        if (!errorCode.isNullOrBlank()) {
            properties["error_code"] = errorCode
        }
        if (!errorType.isNullOrBlank()) {
            properties["error_type"] = errorType
        }
        if (elapsedMs != null) {
            properties["elapsed_ms"] = elapsedMs
        }
        PostHogTracker.capture(
            context = activity.applicationContext,
            eventName = eventName,
            properties = properties,
            priority = priority
        )
    }

    private fun safeHash(value: String): String {
        return KugouDirectSigner.md5(value).take(8).ifBlank { "unknown" }
    }

    private data class QrRefreshResult(
        val qr: com.skodamusic.app.kugou.KugouDirectQrCode?,
        val bitmap: Bitmap?,
        val failureStage: String,
        val errorCode: String
    )
}
