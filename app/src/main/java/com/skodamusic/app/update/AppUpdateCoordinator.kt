package com.skodamusic.app.update

import android.os.Handler
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.skodamusic.app.R
import com.skodamusic.app.observability.PostHogTracker
import java.util.Locale

class AppUpdateCoordinator(
    private val activity: AppCompatActivity,
    private val appUpdateManager: AppUpdateManager,
    private val checkUpdateButton: Button,
    private val updateStatusValue: TextView,
    private val ensureWifiConnectedForNetworkRequest: (requestTag: String, promptUser: Boolean) -> Boolean,
    private val setFeedbackText: (String) -> Unit,
    private val showToast: (Int) -> Unit,
    private val appendRuntimeLog: (String) -> Unit,
    private val pauseTrackDownloadController: () -> Unit,
    private val resumeTrackDownloadControllerIfNeeded: () -> Unit
) {
    @Volatile
    private var updateCheckInFlight: Boolean = false

    @Volatile
    private var updateInstallInFlight: Boolean = false

    private var latestReleaseInfo: AppUpdateManager.ReleaseInfo? = null
    private var updateProgressDialog: AlertDialog? = null
    private var updateProgressTextView: TextView? = null

    fun restoreLastUpdateState() {
        val cached = appUpdateManager.readCachedState()
        if (cached.lastStatus.isBlank()) {
            updateStatusValue.text = activity.getString(R.string.update_status_not_checked)
            checkUpdateButton.text = activity.getString(R.string.action_check_update)
            checkUpdateButton.isEnabled = true
            latestReleaseInfo = null
            return
        }
        when (cached.lastStatus) {
            AppUpdateManager.UpdateCheckStatus.UPDATE_AVAILABLE.name -> {
                updateStatusValue.text = activity.getString(
                    R.string.update_status_available,
                    cached.remoteVersionName.ifBlank { cached.remoteTag.ifBlank { "unknown" } },
                    cached.remoteTag.ifBlank { "-" }
                )
                checkUpdateButton.text = activity.getString(R.string.action_check_update)
                checkUpdateButton.isEnabled = true
            }
            AppUpdateManager.UpdateCheckStatus.UP_TO_DATE.name -> {
                updateStatusValue.text = activity.getString(R.string.update_status_up_to_date)
                checkUpdateButton.text = activity.getString(R.string.action_check_update)
                checkUpdateButton.isEnabled = true
            }
            AppUpdateManager.UpdateCheckStatus.SKIPPED_THROTTLED.name -> {
                updateStatusValue.text = activity.getString(R.string.update_status_throttled)
                checkUpdateButton.text = activity.getString(R.string.action_check_update)
                checkUpdateButton.isEnabled = true
            }
            else -> {
                val code = cached.lastErrorCode.ifBlank { cached.lastStatus }
                updateStatusValue.text = activity.getString(R.string.update_status_failed, code)
                checkUpdateButton.text = activity.getString(R.string.action_check_update)
                checkUpdateButton.isEnabled = true
            }
        }
        latestReleaseInfo = null
    }

    fun scheduleAutoUpdateCheck(handler: Handler, delayMs: Long) {
        handler.postDelayed(
            {
                if (!activity.isFinishing) {
                    requestUpdateCheck(force = false, trigger = "cold_start")
                }
            },
            delayMs
        )
    }

    fun onCheckButtonClicked() {
        val pending = latestReleaseInfo
        if (pending != null) {
            startUpdateDownloadAndInstall(releaseInfo = pending, trigger = "settings_manual")
        } else {
            requestUpdateCheck(force = true, trigger = "settings_manual")
        }
    }

    fun requestUpdateCheck(force: Boolean, trigger: String) {
        if (updateCheckInFlight || updateInstallInFlight) {
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("update_check", trigger != "cold_start")) {
            if (trigger != "cold_start") {
                setFeedbackText(activity.getString(R.string.feedback_network_wifi_required))
                showToast(R.string.toast_network_wifi_required)
            }
            return
        }

        updateCheckInFlight = true
        activity.runOnUiThread {
            checkUpdateButton.isEnabled = false
            checkUpdateButton.text = activity.getString(R.string.action_check_update)
            updateStatusValue.text = activity.getString(R.string.update_status_checking)
        }
        setFeedbackText(activity.getString(R.string.feedback_update_checking))
        appendRuntimeLog("update check start trigger=$trigger force=$force")
        PostHogTracker.capture(
            context = activity.applicationContext,
            eventName = "update_check_start",
            properties = mapOf(
                "trigger" to trigger,
                "force" to force
            )
        )

        appUpdateManager.checkForUpdates(force = force, trigger = trigger) { result ->
            activity.runOnUiThread {
                updateCheckInFlight = false
                applyUpdateCheckResultUi(result)
            }
            captureUpdateCheckResult(result)
            appendRuntimeLog(
                "update check done trigger=$trigger status=${result.status.name} remoteTag=${result.remoteTag} code=${result.errorCode} stage=${result.failedStage} url=${result.failedUrl}"
            )
        }
    }

    private fun applyUpdateCheckResultUi(result: AppUpdateManager.UpdateCheckResult) {
        checkUpdateButton.isEnabled = true
        when (result.status) {
            AppUpdateManager.UpdateCheckStatus.UPDATE_AVAILABLE -> {
                latestReleaseInfo = result.releaseInfo
                checkUpdateButton.text = activity.getString(R.string.action_install_update)
                val versionLabel = result.remoteVersionName.ifBlank { result.remoteTag.ifBlank { "unknown" } }
                val tagLabel = result.remoteTag.ifBlank { "-" }
                updateStatusValue.text = activity.getString(R.string.update_status_available, versionLabel, tagLabel)
                setFeedbackText(activity.getString(R.string.feedback_update_available, versionLabel))
                showToast(R.string.toast_update_available)
                val release = result.releaseInfo
                if (release != null) {
                    startUpdateDownloadAndInstall(releaseInfo = release, trigger = "${result.trigger}_auto")
                }
            }
            AppUpdateManager.UpdateCheckStatus.UP_TO_DATE -> {
                latestReleaseInfo = null
                checkUpdateButton.text = activity.getString(R.string.action_check_update)
                updateStatusValue.text = activity.getString(R.string.update_status_up_to_date)
                setFeedbackText(activity.getString(R.string.feedback_update_up_to_date))
                if (result.trigger != "cold_start") {
                    showToast(R.string.toast_update_up_to_date)
                }
            }
            AppUpdateManager.UpdateCheckStatus.SKIPPED_THROTTLED -> {
                latestReleaseInfo = null
                checkUpdateButton.text = activity.getString(R.string.action_check_update)
                updateStatusValue.text = activity.getString(R.string.update_status_throttled)
                setFeedbackText(activity.getString(R.string.feedback_update_up_to_date))
            }
            else -> {
                latestReleaseInfo = null
                checkUpdateButton.text = activity.getString(R.string.action_check_update)
                val code = result.errorCode.ifBlank { result.status.name }
                updateStatusValue.text = activity.getString(R.string.update_status_failed, code)
                setFeedbackText(activity.getString(R.string.feedback_update_check_failed, code))
                if (result.trigger != "cold_start") {
                    showToast(R.string.toast_update_check_failed)
                }
            }
        }
    }

    private fun captureUpdateCheckResult(result: AppUpdateManager.UpdateCheckResult) {
        val props = mutableMapOf<String, Any?>(
            "trigger" to result.trigger,
            "status" to result.status.name.lowercase(Locale.US),
            "local_version_code" to result.localVersion.versionCode,
            "local_version_name" to result.localVersion.versionName,
            "remote_version_code" to result.remoteVersionCode,
            "remote_version_name" to result.remoteVersionName,
            "remote_tag" to result.remoteTag,
            "http_status" to result.httpStatus,
            "failed_stage" to result.failedStage,
            "failed_url" to result.failedUrl,
            "attempt_count" to result.attemptedUrls.size
        )
        if (result.attemptedUrls.isNotEmpty()) {
            props["attempt_urls"] = result.attemptedUrls.joinToString(" -> ").take(220)
        }
        if (result.errorCode.isNotBlank()) {
            props["error_code"] = result.errorCode
        }
        if (result.message.isNotBlank()) {
            props["message"] = result.message.take(220)
        }
        val eventName = when (result.status) {
            AppUpdateManager.UpdateCheckStatus.UPDATE_AVAILABLE -> "update_check_available"
            AppUpdateManager.UpdateCheckStatus.UP_TO_DATE -> "update_check_up_to_date"
            AppUpdateManager.UpdateCheckStatus.SKIPPED_THROTTLED -> "update_check_skipped"
            else -> "update_check_failed"
        }
        PostHogTracker.capture(
            context = activity.applicationContext,
            eventName = eventName,
            properties = props,
            priority = if (eventName == "update_check_failed") PostHogTracker.Priority.HIGH else PostHogTracker.Priority.NORMAL
        )
    }

    private fun startUpdateDownloadAndInstall(
        releaseInfo: AppUpdateManager.ReleaseInfo,
        trigger: String
    ) {
        if (updateInstallInFlight || updateCheckInFlight) {
            return
        }
        if (!ensureWifiConnectedForNetworkRequest("update_download_install", true)) {
            setFeedbackText(activity.getString(R.string.feedback_network_wifi_required))
            showToast(R.string.toast_network_wifi_required)
            return
        }

        updateInstallInFlight = true
        pauseTrackDownloadController()
        checkUpdateButton.isEnabled = false
        checkUpdateButton.text = activity.getString(R.string.action_install_update)
        val versionLabel = releaseInfo.versionName.ifBlank { releaseInfo.tagName.ifBlank { "unknown" } }
        updateStatusValue.text = activity.getString(R.string.update_status_downloading, versionLabel)
        showUpdateProgressDialog(versionLabel)
        setFeedbackText(activity.getString(R.string.feedback_update_downloading, versionLabel))
        appendRuntimeLog("update download start tag=${releaseInfo.tagName} trigger=$trigger")
        PostHogTracker.capture(
            context = activity.applicationContext,
            eventName = "update_download_start",
            properties = mapOf(
                "trigger" to trigger,
                "remote_tag" to releaseInfo.tagName,
                "remote_version_code" to releaseInfo.versionCode,
                "remote_version_name" to releaseInfo.versionName
            )
        )

        appUpdateManager.downloadAndInstall(
            releaseInfo = releaseInfo,
            trigger = trigger,
            onProgress = { progress ->
                activity.runOnUiThread {
                    renderDownloadProgress(versionLabel, progress)
                }
            }
        ) { result ->
            activity.runOnUiThread {
                updateInstallInFlight = false
                checkUpdateButton.isEnabled = true
                dismissUpdateProgressDialog()
                when (result.status) {
                    AppUpdateManager.UpdateInstallStatus.INSTALL_LAUNCHED -> {
                        latestReleaseInfo = null
                        checkUpdateButton.text = activity.getString(R.string.action_check_update)
                        val version = result.remoteVersionName.ifBlank { result.releaseTag.ifBlank { "unknown" } }
                        updateStatusValue.text = activity.getString(R.string.update_status_installing, version)
                        setFeedbackText(activity.getString(R.string.feedback_update_installing))
                        showToast(R.string.toast_update_installing)
                    }
                    AppUpdateManager.UpdateInstallStatus.DOWNLOAD_FAILED -> {
                        checkUpdateButton.text = activity.getString(R.string.action_install_update)
                        val code = result.errorCode.ifBlank { result.status.name }
                        updateStatusValue.text = activity.getString(R.string.update_status_download_failed, code)
                        setFeedbackText(activity.getString(R.string.feedback_update_download_failed, code))
                        showToast(R.string.toast_update_download_failed)
                        resumeTrackDownloadControllerIfNeeded()
                    }
                    AppUpdateManager.UpdateInstallStatus.INSTALL_FAILED -> {
                        checkUpdateButton.text = activity.getString(R.string.action_install_update)
                        val code = result.errorCode.ifBlank { result.status.name }
                        updateStatusValue.text = activity.getString(R.string.update_status_install_failed, code)
                        setFeedbackText(activity.getString(R.string.feedback_update_install_failed, code))
                        showToast(R.string.toast_update_install_failed)
                        resumeTrackDownloadControllerIfNeeded()
                    }
                }
            }
            captureUpdateInstallResult(result)
            appendRuntimeLog(
                "update install result status=${result.status.name} tag=${result.releaseTag} used=${result.usedUrl} error=${result.errorCode}"
            )
        }
    }

    private fun captureUpdateInstallResult(result: AppUpdateManager.UpdateInstallResult) {
        val props = mutableMapOf<String, Any?>(
            "trigger" to result.trigger,
            "remote_tag" to result.releaseTag,
            "remote_version_code" to result.remoteVersionCode,
            "remote_version_name" to result.remoteVersionName,
            "apk_bytes" to result.apkBytes,
            "used_url" to result.usedUrl,
            "attempt_count" to result.attemptedUrls.size
        )
        if (result.errorCode.isNotBlank()) {
            props["error_code"] = result.errorCode
        }
        if (result.message.isNotBlank()) {
            props["message"] = result.message.take(96)
        }
        val eventName = when (result.status) {
            AppUpdateManager.UpdateInstallStatus.INSTALL_LAUNCHED -> "update_install_triggered"
            AppUpdateManager.UpdateInstallStatus.DOWNLOAD_FAILED -> "update_download_failed"
            AppUpdateManager.UpdateInstallStatus.INSTALL_FAILED -> "update_install_failed"
        }
        PostHogTracker.capture(
            context = activity.applicationContext,
            eventName = eventName,
            properties = props,
            priority = if (result.status == AppUpdateManager.UpdateInstallStatus.INSTALL_LAUNCHED) {
                PostHogTracker.Priority.NORMAL
            } else {
                PostHogTracker.Priority.HIGH
            }
        )
    }

    private fun showUpdateProgressDialog(versionLabel: String) {
        dismissUpdateProgressDialog()
        val view = TextView(activity).apply {
            setPadding(dp(24), dp(18), dp(24), dp(10))
            textSize = 16f
            text = activity.getString(R.string.update_progress_pending, versionLabel)
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_progress_title))
            .setView(view)
            .setCancelable(false)
            .create()
        dialog.show()
        updateProgressDialog = dialog
        updateProgressTextView = view
    }

    private fun dismissUpdateProgressDialog() {
        updateProgressDialog?.dismiss()
        updateProgressDialog = null
        updateProgressTextView = null
    }

    private fun renderDownloadProgress(versionLabel: String, progress: AppUpdateManager.DownloadProgress) {
        val downloadedText = Formatter.formatShortFileSize(activity, progress.downloadedBytes.coerceAtLeast(0L))
        val totalText = if (progress.totalBytes > 0L) {
            Formatter.formatShortFileSize(activity, progress.totalBytes)
        } else {
            "?"
        }
        val progressText = if (progress.percent >= 0) {
            activity.getString(
                R.string.update_progress_with_percent,
                versionLabel,
                progress.percent,
                downloadedText,
                totalText
            )
        } else {
            activity.getString(
                R.string.update_progress_without_percent,
                versionLabel,
                downloadedText,
                totalText
            )
        }
        updateStatusValue.text = progressText
        updateProgressTextView?.text = progressText
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}
