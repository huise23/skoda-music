package com.skodamusic.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AppUpdateManager(
    context: Context,
    private val owner: String,
    private val repo: String,
    private val log: (String) -> Unit = {}
) {
    data class LocalVersion(
        val versionCode: Long,
        val versionName: String
    )

    data class ReleaseAsset(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long
    )

    data class ReleaseInfo(
        val tagName: String,
        val releaseName: String,
        val htmlUrl: String,
        val publishedAt: String,
        val versionCode: Long,
        val versionName: String,
        val asset: ReleaseAsset
    )

    data class CachedState(
        val lastCheckAtMs: Long,
        val lastStatus: String,
        val lastErrorCode: String,
        val remoteTag: String,
        val remoteVersionCode: Long,
        val remoteVersionName: String
    )

    enum class UpdateCheckStatus {
        SKIPPED_THROTTLED,
        UP_TO_DATE,
        UPDATE_AVAILABLE,
        NO_RELEASE,
        NO_APK_ASSET,
        NETWORK_ERROR,
        PARSE_ERROR
    }

    data class UpdateCheckResult(
        val status: UpdateCheckStatus,
        val trigger: String,
        val localVersion: LocalVersion,
        val remoteVersionCode: Long = -1L,
        val remoteVersionName: String = "",
        val remoteTag: String = "",
        val releaseInfo: ReleaseInfo? = null,
        val httpStatus: Int = -1,
        val errorCode: String = "",
        val message: String = "",
        val failedStage: String = "",
        val failedUrl: String = "",
        val attemptedUrls: List<String> = emptyList(),
        val checkedAtMs: Long = System.currentTimeMillis(),
        val cooldownMs: Long = 0L
    )

    enum class UpdateInstallStatus {
        INSTALL_LAUNCHED,
        DOWNLOAD_FAILED,
        INSTALL_FAILED
    }

    data class UpdateInstallResult(
        val status: UpdateInstallStatus,
        val trigger: String,
        val releaseTag: String,
        val remoteVersionCode: Long,
        val remoteVersionName: String,
        val attemptedUrls: List<String> = emptyList(),
        val usedUrl: String = "",
        val apkPath: String = "",
        val apkBytes: Long = -1L,
        val errorCode: String = "",
        val message: String = ""
    )

    private data class ReleaseFetchResult(
        val releaseInfo: ReleaseInfo? = null,
        val httpStatus: Int = -1,
        val errorCode: String = "",
        val message: String = "",
        val hasStableReleaseWithoutApk: Boolean = false,
        val failedStage: String = "",
        val failedUrl: String = "",
        val attemptedUrls: List<String> = emptyList()
    )

    private data class DownloadResult(
        val success: Boolean,
        val file: File? = null,
        val attemptedUrls: List<String> = emptyList(),
        val usedUrl: String = "",
        val errorCode: String = "",
        val message: String = ""
    )

    private data class InstallDispatchResult(
        val success: Boolean,
        val errorCode: String = "",
        val message: String = ""
    )

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CLIENT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(CLIENT_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(CLIENT_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    fun readCachedState(): CachedState {
        return CachedState(
            lastCheckAtMs = prefs.getLong(KEY_LAST_CHECK_AT_MS, 0L),
            lastStatus = prefs.getString(KEY_LAST_STATUS, "").orEmpty(),
            lastErrorCode = prefs.getString(KEY_LAST_ERROR_CODE, "").orEmpty(),
            remoteTag = prefs.getString(KEY_REMOTE_TAG, "").orEmpty(),
            remoteVersionCode = prefs.getLong(KEY_REMOTE_VERSION_CODE, -1L),
            remoteVersionName = prefs.getString(KEY_REMOTE_VERSION_NAME, "").orEmpty()
        )
    }

    fun checkForUpdates(force: Boolean, trigger: String, callback: (UpdateCheckResult) -> Unit) {
        val localVersion = resolveLocalVersion()
        val nowMs = System.currentTimeMillis()
        val lastCheckAtMs = prefs.getLong(KEY_LAST_CHECK_AT_MS, 0L)
        val lastStatus = prefs.getString(KEY_LAST_STATUS, "").orEmpty()
        val cooldownMs = resolveCooldownMs(lastStatus)

        if (!force && lastCheckAtMs > 0L) {
            val ageMs = nowMs - lastCheckAtMs
            if (ageMs in 0 until cooldownMs) {
                callback(
                    UpdateCheckResult(
                        status = UpdateCheckStatus.SKIPPED_THROTTLED,
                        trigger = trigger,
                        localVersion = localVersion,
                        message = "skip by cooldown",
                        cooldownMs = cooldownMs,
                        checkedAtMs = lastCheckAtMs
                    )
                )
                return
            }
        }

        worker.execute {
            val result = runCatching {
                performCheck(trigger = trigger, localVersion = localVersion)
            }.getOrElse { e ->
                UpdateCheckResult(
                    status = UpdateCheckStatus.NETWORK_ERROR,
                    trigger = trigger,
                    localVersion = localVersion,
                    errorCode = "CHECK_EXCEPTION",
                    message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                    failedStage = "check_for_updates",
                    checkedAtMs = System.currentTimeMillis()
                )
            }

            persistCheckResult(result)
            callback(result)
        }
    }

    fun downloadAndInstall(
        releaseInfo: ReleaseInfo,
        trigger: String,
        callback: (UpdateInstallResult) -> Unit
    ) {
        worker.execute {
            val downloadResult = downloadReleaseApk(releaseInfo)
            if (!downloadResult.success || downloadResult.file == null) {
                callback(
                    UpdateInstallResult(
                        status = UpdateInstallStatus.DOWNLOAD_FAILED,
                        trigger = trigger,
                        releaseTag = releaseInfo.tagName,
                        remoteVersionCode = releaseInfo.versionCode,
                        remoteVersionName = releaseInfo.versionName,
                        attemptedUrls = downloadResult.attemptedUrls,
                        usedUrl = downloadResult.usedUrl,
                        errorCode = downloadResult.errorCode,
                        message = downloadResult.message
                    )
                )
                return@execute
            }

            val dispatchResult = dispatchInstallIntent(downloadResult.file)
            if (!dispatchResult.success) {
                callback(
                    UpdateInstallResult(
                        status = UpdateInstallStatus.INSTALL_FAILED,
                        trigger = trigger,
                        releaseTag = releaseInfo.tagName,
                        remoteVersionCode = releaseInfo.versionCode,
                        remoteVersionName = releaseInfo.versionName,
                        attemptedUrls = downloadResult.attemptedUrls,
                        usedUrl = downloadResult.usedUrl,
                        apkPath = downloadResult.file.absolutePath,
                        apkBytes = downloadResult.file.length(),
                        errorCode = dispatchResult.errorCode,
                        message = dispatchResult.message
                    )
                )
                return@execute
            }

            callback(
                UpdateInstallResult(
                    status = UpdateInstallStatus.INSTALL_LAUNCHED,
                    trigger = trigger,
                    releaseTag = releaseInfo.tagName,
                    remoteVersionCode = releaseInfo.versionCode,
                    remoteVersionName = releaseInfo.versionName,
                    attemptedUrls = downloadResult.attemptedUrls,
                    usedUrl = downloadResult.usedUrl,
                    apkPath = downloadResult.file.absolutePath,
                    apkBytes = downloadResult.file.length()
                )
            )
        }
    }

    private fun performCheck(trigger: String, localVersion: LocalVersion): UpdateCheckResult {
        val fetchResult = fetchLatestRelease()
        val nowMs = System.currentTimeMillis()
        val release = fetchResult.releaseInfo

        if (release == null) {
            val status = when {
                fetchResult.httpStatus > 0 -> UpdateCheckStatus.NETWORK_ERROR
                fetchResult.errorCode == "PARSE_RELEASE_PAYLOAD" -> UpdateCheckStatus.PARSE_ERROR
                fetchResult.hasStableReleaseWithoutApk -> UpdateCheckStatus.NO_APK_ASSET
                else -> UpdateCheckStatus.NO_RELEASE
            }
            return UpdateCheckResult(
                status = status,
                trigger = trigger,
                localVersion = localVersion,
                httpStatus = fetchResult.httpStatus,
                errorCode = fetchResult.errorCode,
                message = fetchResult.message,
                failedStage = fetchResult.failedStage,
                failedUrl = fetchResult.failedUrl,
                attemptedUrls = fetchResult.attemptedUrls,
                checkedAtMs = nowMs
            )
        }

        val hasUpdate = isRemoteNewer(localVersion, release)
        return if (hasUpdate) {
            UpdateCheckResult(
                status = UpdateCheckStatus.UPDATE_AVAILABLE,
                trigger = trigger,
                localVersion = localVersion,
                remoteVersionCode = release.versionCode,
                remoteVersionName = release.versionName,
                remoteTag = release.tagName,
                releaseInfo = release,
                checkedAtMs = nowMs
            )
        } else {
            UpdateCheckResult(
                status = UpdateCheckStatus.UP_TO_DATE,
                trigger = trigger,
                localVersion = localVersion,
                remoteVersionCode = release.versionCode,
                remoteVersionName = release.versionName,
                remoteTag = release.tagName,
                checkedAtMs = nowMs
            )
        }
    }

    private fun fetchLatestRelease(): ReleaseFetchResult {
        val apiUrl = "$GITHUB_API_BASE/repos/$owner/$repo/releases?per_page=8"
        // Force CF proxy only for metadata check (no direct GitHub fallback).
        val requestUrls = listOf(buildCfProxyUrl(apiUrl))

        var lastFailure = ReleaseFetchResult(
            errorCode = "GITHUB_RELEASE_REQUEST_NOT_EXECUTED",
            message = "no request executed",
            failedStage = "fetch_release_prepare",
            attemptedUrls = requestUrls
        )
        for (url in requestUrls) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", UPDATE_USER_AGENT)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (response.code() !in 200..299) {
                        lastFailure = ReleaseFetchResult(
                            httpStatus = response.code(),
                            errorCode = "GITHUB_RELEASE_HTTP_${response.code()}",
                            message = "github releases http ${response.code()} via ${shortenUrlForLog(url)}",
                            failedStage = "fetch_release_http",
                            failedUrl = url,
                            attemptedUrls = requestUrls
                        )
                    } else {
                        val body = response.body()?.string().orEmpty()
                        if (body.isBlank()) {
                            lastFailure = ReleaseFetchResult(
                                errorCode = "GITHUB_RELEASE_EMPTY_BODY",
                                message = "github releases empty body via ${shortenUrlForLog(url)}",
                                failedStage = "fetch_release_empty_body",
                                failedUrl = url,
                                attemptedUrls = requestUrls
                            )
                        } else {
                            return parseReleasePayload(body)
                        }
                    }
                }
            } catch (e: Exception) {
                lastFailure = ReleaseFetchResult(
                    errorCode = "GITHUB_RELEASE_EXCEPTION",
                    message = "${e.javaClass.simpleName}: ${e.message.orEmpty()} via ${shortenUrlForLog(url)}",
                    failedStage = "fetch_release_exception",
                    failedUrl = url,
                    attemptedUrls = requestUrls
                )
            }
        }
        return lastFailure
    }

    private fun parseReleasePayload(payload: String): ReleaseFetchResult {
        return try {
            val arr = JSONArray(payload)
            var hasReleaseWithoutApk = false
            for (index in 0 until arr.length()) {
                val item = arr.optJSONObject(index) ?: continue
                val draft = item.optBoolean("draft", false)
                if (draft) {
                    continue
                }
                val tagName = item.optString("tag_name").orEmpty().trim()
                val releaseName = item.optString("name").orEmpty().trim()
                val htmlUrl = item.optString("html_url").orEmpty().trim()
                val publishedAt = item.optString("published_at").orEmpty().trim()
                val asset = pickPreferredApkAsset(item.optJSONArray("assets"))
                if (asset == null) {
                    hasReleaseWithoutApk = true
                    continue
                }
                val resolvedVersionCode = resolveRemoteVersionCode(tagName, asset.name)
                val resolvedVersionName = resolveRemoteVersionName(tagName, releaseName, asset.name)
                val releaseInfo = ReleaseInfo(
                    tagName = tagName,
                    releaseName = releaseName,
                    htmlUrl = htmlUrl,
                    publishedAt = publishedAt,
                    versionCode = resolvedVersionCode,
                    versionName = resolvedVersionName,
                    asset = asset
                )
                return ReleaseFetchResult(
                    releaseInfo = releaseInfo
                )
            }

            ReleaseFetchResult(
                hasStableReleaseWithoutApk = hasReleaseWithoutApk,
                errorCode = if (hasReleaseWithoutApk) "RELEASE_NO_APK" else "NO_RELEASE",
                failedStage = "parse_release_filter"
            )
        } catch (e: Exception) {
            ReleaseFetchResult(
                errorCode = "PARSE_RELEASE_PAYLOAD",
                message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                failedStage = "parse_release_payload"
            )
        }
    }

    private fun pickPreferredApkAsset(assets: JSONArray?): ReleaseAsset? {
        if (assets == null) {
            return null
        }
        var bestScore = Int.MIN_VALUE
        var bestAsset: ReleaseAsset? = null

        for (index in 0 until assets.length()) {
            val item = assets.optJSONObject(index) ?: continue
            val name = item.optString("name").orEmpty().trim()
            if (!name.lowercase(Locale.US).endsWith(".apk")) {
                continue
            }
            val downloadUrl = item.optString("browser_download_url").orEmpty().trim()
            if (downloadUrl.isBlank()) {
                continue
            }
            val contentType = item.optString("content_type").orEmpty().trim().lowercase(Locale.US)
            var score = 0
            val lowerName = name.lowercase(Locale.US)
            if (lowerName.contains("signed")) score += 100
            if (lowerName.contains("release")) score += 40
            if (lowerName.contains("unsigned")) score -= 30
            if (lowerName.contains("debug")) score -= 80
            if (contentType.contains("android.package-archive")) score += 10
            if (downloadUrl.contains("/releases/download/")) score += 5
            if (score > bestScore) {
                bestScore = score
                bestAsset = ReleaseAsset(
                    name = name,
                    downloadUrl = downloadUrl,
                    sizeBytes = item.optLong("size", -1L)
                )
            }
        }
        return bestAsset
    }

    private fun resolveLocalVersion(): LocalVersion {
        return try {
            val info = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) {
                val field = info.javaClass.getField("longVersionCode")
                field.getLong(info)
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            LocalVersion(
                versionCode = code,
                versionName = info.versionName.orEmpty().ifBlank { "unknown" }
            )
        } catch (_: Exception) {
            LocalVersion(versionCode = -1L, versionName = "unknown")
        }
    }

    private fun resolveRemoteVersionCode(tagName: String, assetName: String): Long {
        val joined = "$assetName|$tagName"
        val runMatch = RUN_NUMBER_REGEX.find(joined)
        if (runMatch != null) {
            return runMatch.groupValues[1].toLongOrNull() ?: -1L
        }

        val semver = parseSemver(tagName)
        if (semver != null) {
            val (major, minor, patch) = semver
            return major * 1_000_000L + minor * 1_000L + patch
        }
        return -1L
    }

    private fun resolveRemoteVersionName(tagName: String, releaseName: String, assetName: String): String {
        if (tagName.isNotBlank()) {
            return tagName
        }
        if (releaseName.isNotBlank()) {
            return releaseName
        }
        return assetName.ifBlank { "unknown" }
    }

    private fun isRemoteNewer(localVersion: LocalVersion, release: ReleaseInfo): Boolean {
        if (release.versionCode > 0L && localVersion.versionCode > 0L) {
            return release.versionCode > localVersion.versionCode
        }

        val remoteSemver = parseSemver(release.versionName)
        val localSemver = parseSemver(localVersion.versionName)
        if (remoteSemver != null && localSemver != null) {
            if (remoteSemver.first != localSemver.first) {
                return remoteSemver.first > localSemver.first
            }
            if (remoteSemver.second != localSemver.second) {
                return remoteSemver.second > localSemver.second
            }
            return remoteSemver.third > localSemver.third
        }

        return release.tagName.isNotBlank() && !release.tagName.equals(localVersion.versionName, ignoreCase = true)
    }

    private fun parseSemver(input: String): Triple<Long, Long, Long>? {
        val match = SEMVER_REGEX.find(input) ?: return null
        val major = match.groupValues[1].toLongOrNull() ?: return null
        val minor = match.groupValues[2].toLongOrNull() ?: return null
        val patch = match.groupValues[3].toLongOrNull() ?: return null
        return Triple(major, minor, patch)
    }

    private fun downloadReleaseApk(releaseInfo: ReleaseInfo): DownloadResult {
        val officialUrl = releaseInfo.asset.downloadUrl
        val candidates = buildMirrorCandidates(officialUrl)
        val updatesDir = File(appContext.cacheDir, UPDATE_CACHE_DIR_NAME)
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            return DownloadResult(
                success = false,
                attemptedUrls = candidates,
                errorCode = "UPDATE_CACHE_DIR_CREATE_FAILED",
                message = "failed to create update cache dir"
            )
        }

        val finalName = sanitizeApkFileName(releaseInfo.asset.name)
        val finalFile = File(updatesDir, finalName)
        val tmpFile = File(updatesDir, "$finalName.download")

        for (candidate in candidates) {
            runCatching {
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                val request = Request.Builder()
                    .url(candidate)
                    .get()
                    .header("User-Agent", UPDATE_USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code() !in 200..299) {
                        throw IllegalStateException("http ${response.code()}")
                    }
                    val body = response.body() ?: throw IllegalStateException("empty body")
                    FileOutputStream(tmpFile, false).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) {
                                    break
                                }
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                }

                if (!tmpFile.exists() || tmpFile.length() <= 0L) {
                    throw IllegalStateException("download file empty")
                }

                if (finalFile.exists()) {
                    finalFile.delete()
                }
                val renamed = tmpFile.renameTo(finalFile)
                if (!renamed) {
                    tmpFile.copyTo(finalFile, overwrite = true)
                    tmpFile.delete()
                }
                log("update download success url=$candidate bytes=${finalFile.length()}")
                return DownloadResult(
                    success = true,
                    file = finalFile,
                    attemptedUrls = candidates,
                    usedUrl = candidate
                )
            }.onFailure { e ->
                log("update download fail url=$candidate reason=${e.javaClass.simpleName}:${e.message.orEmpty()}")
            }
        }

        return DownloadResult(
            success = false,
            attemptedUrls = candidates,
            errorCode = "UPDATE_DOWNLOAD_ALL_FAILED",
            message = "all mirrors and official url failed"
        )
    }

    private fun dispatchInstallIntent(apkFile: File): InstallDispatchResult {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return InstallDispatchResult(
                success = false,
                errorCode = "UPDATE_APK_MISSING",
                message = "apk file missing"
            )
        }

        if (Build.VERSION.SDK_INT >= 26) {
            val allowInstall = runCatching {
                appContext.packageManager.canRequestPackageInstalls()
            }.getOrDefault(true)
            if (!allowInstall) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    appContext.startActivity(settingsIntent)
                    InstallDispatchResult(
                        success = false,
                        errorCode = "UNKNOWN_SOURCES_PERMISSION_REQUIRED",
                        message = "open unknown sources settings"
                    )
                } catch (e: Exception) {
                    InstallDispatchResult(
                        success = false,
                        errorCode = "UNKNOWN_SOURCES_SETTINGS_FAILED",
                        message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                    )
                }
            }
        }

        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, APK_MIME_TYPE)
        }

        return try {
            val canHandle = intent.resolveActivity(appContext.packageManager) != null
            if (!canHandle) {
                InstallDispatchResult(
                    success = false,
                    errorCode = "INSTALLER_NOT_FOUND",
                    message = "no package installer found"
                )
            } else {
                appContext.startActivity(intent)
                InstallDispatchResult(success = true)
            }
        } catch (e: Exception) {
            InstallDispatchResult(
                success = false,
                errorCode = "INSTALL_INTENT_EXCEPTION",
                message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
            )
        }
    }

    private fun buildMirrorCandidates(officialUrl: String): List<String> {
        val normalized = officialUrl.trim()
        if (normalized.isBlank()) {
            return emptyList()
        }
        val directCandidates = listOf(
            "https://ghfast.top/$normalized",
            "https://mirror.ghproxy.com/$normalized",
            "https://ghproxy.net/$normalized",
            normalized
        ).distinct()
        // Force CF proxy only for APK download candidates (no direct fallback).
        return directCandidates.map { buildCfProxyUrl(it) }.distinct()
    }

    private fun buildCfProxyUrl(rawUrl: String): String {
        val normalized = rawUrl.trim()
        if (normalized.isEmpty()) {
            return normalized
        }
        val encoded = URLEncoder.encode(normalized, "UTF-8")
        return "$CF_ACCEL_PROXY_PREFIX$encoded"
    }

    private fun shortenUrlForLog(rawUrl: String): String {
        val text = rawUrl.trim()
        return if (text.length <= 96) text else text.take(96) + "..."
    }

    private fun sanitizeApkFileName(raw: String): String {
        val source = raw.ifBlank { "skoda-music-update.apk" }
        val safe = source.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (safe.lowercase(Locale.US).endsWith(".apk")) safe else "$safe.apk"
    }

    private fun persistCheckResult(result: UpdateCheckResult) {
        prefs.edit()
            .putLong(KEY_LAST_CHECK_AT_MS, result.checkedAtMs)
            .putString(KEY_LAST_STATUS, result.status.name)
            .putString(KEY_LAST_ERROR_CODE, result.errorCode)
            .putString(KEY_REMOTE_TAG, result.remoteTag)
            .putLong(KEY_REMOTE_VERSION_CODE, result.remoteVersionCode)
            .putString(KEY_REMOTE_VERSION_NAME, result.remoteVersionName)
            .apply()
    }

    private fun resolveCooldownMs(lastStatus: String): Long {
        return when (lastStatus.uppercase(Locale.US)) {
            UpdateCheckStatus.UPDATE_AVAILABLE.name,
            UpdateCheckStatus.UP_TO_DATE.name -> AUTO_CHECK_SUCCESS_COOLDOWN_MS
            else -> AUTO_CHECK_RETRY_COOLDOWN_MS
        }
    }

    private companion object {
        const val PREFS_NAME = "app_update"
        const val KEY_LAST_CHECK_AT_MS = "last_check_at_ms"
        const val KEY_LAST_STATUS = "last_status"
        const val KEY_LAST_ERROR_CODE = "last_error_code"
        const val KEY_REMOTE_TAG = "remote_tag"
        const val KEY_REMOTE_VERSION_CODE = "remote_version_code"
        const val KEY_REMOTE_VERSION_NAME = "remote_version_name"

        const val GITHUB_API_BASE = "https://api.github.com"
        const val CF_ACCEL_PROXY_PREFIX = "https://config-ui.52mn.ru/api/fetch-url?SECRET_TOKEN=id93UYra8P0E1I&url="
        const val UPDATE_USER_AGENT = "skoda-music-update-checker"

        const val AUTO_CHECK_SUCCESS_COOLDOWN_MS = 24L * 60L * 60L * 1000L
        const val AUTO_CHECK_RETRY_COOLDOWN_MS = 30L * 60L * 1000L

        const val CLIENT_CONNECT_TIMEOUT_MS = 6000L
        const val CLIENT_READ_TIMEOUT_MS = 15000L
        const val CLIENT_WRITE_TIMEOUT_MS = 15000L

        const val UPDATE_CACHE_DIR_NAME = "updates"
        const val DOWNLOAD_BUFFER_BYTES = 8 * 1024
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        val RUN_NUMBER_REGEX = Regex("(?:^|\\D)r(\\d{1,10})(?:\\D|$)", RegexOption.IGNORE_CASE)
        val SEMVER_REGEX = Regex("v?(\\d+)\\.(\\d+)\\.(\\d+)", RegexOption.IGNORE_CASE)
    }
}
