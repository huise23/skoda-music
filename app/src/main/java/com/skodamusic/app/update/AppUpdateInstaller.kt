package com.skodamusic.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class AppUpdateInstaller(
    context: Context,
    private val log: (String) -> Unit = {}
) {
    data class Result(
        val success: Boolean,
        val errorCode: String = "",
        val message: String = "",
        val failedStage: String = "",
        val pathKind: String = "",
        val uriKind: String = "",
        val mimeType: String = APK_MIME_TYPE,
        val installerResolved: Boolean = false,
        val apkReadable: Boolean = false,
        val parentReadable: Boolean = false,
        val parentExecutable: Boolean = false,
        val silentInstalled: Boolean = false,
        val installFile: File? = null
    )

    private val appContext = context.applicationContext

    fun dispatchInstallIntent(apkFile: File): Result {
        val prepared = prepareInstallFile(apkFile)
        if (!prepared.success || prepared.installFile == null) {
            return prepared
        }

        // Fast-path: Check if root / shell silent install is available (common on car head units).
        val silentResult = trySilentInstall(prepared.installFile)
        if (silentResult) {
            log("silent install succeeded via su/pm")
            return prepared.copy(
                success = true,
                silentInstalled = true,
                installerResolved = true,
                message = "silent install succeeded"
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
                    prepared.copy(
                        success = false,
                        errorCode = "UNKNOWN_SOURCES_PERMISSION_REQUIRED",
                        message = "open unknown sources settings",
                        failedStage = "unknown_sources"
                    )
                } catch (e: Exception) {
                    prepared.copy(
                        success = false,
                        errorCode = "UNKNOWN_SOURCES_SETTINGS_FAILED",
                        message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                        failedStage = "unknown_sources"
                    )
                }
            }
        }

        val uri = if (Build.VERSION.SDK_INT >= 24) {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                prepared.installFile
            )
        } else {
            Uri.fromFile(prepared.installFile)
        }
        val uriKind = if (Build.VERSION.SDK_INT >= 24) "content" else "file"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setDataAndType(uri, APK_MIME_TYPE)
        }

        return try {
            val canHandle = intent.resolveActivity(appContext.packageManager) != null
            if (!canHandle) {
                prepared.copy(
                    success = false,
                    errorCode = "INSTALLER_NOT_FOUND",
                    message = "no package installer found",
                    failedStage = "install_resolve",
                    uriKind = uriKind,
                    installerResolved = false
                )
            } else {
                appContext.startActivity(intent)
                prepared.copy(
                    success = true,
                    uriKind = uriKind,
                    installerResolved = true
                )
            }
        } catch (e: Exception) {
            prepared.copy(
                success = false,
                errorCode = "INSTALL_INTENT_EXCEPTION",
                message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                failedStage = "install_intent",
                uriKind = uriKind,
                installerResolved = true
            )
        }
    }

    private fun trySilentInstall(targetApk: File): Boolean {
        val apkPath = targetApk.absolutePath
        val candidateCommands = listOf(
            arrayOf("su", "-c", "pm install -r \"$apkPath\""),
            arrayOf("pm", "install", "-r", apkPath)
        )

        for (cmd in candidateCommands) {
            try {
                val proc = Runtime.getRuntime().exec(cmd)
                val out = proc.inputStream.bufferedReader().use { it.readText() }
                val err = proc.errorStream.bufferedReader().use { it.readText() }
                val exitCode = proc.waitFor()
                log("trySilentInstall cmd=${cmd.first()} exit=$exitCode out=${out.trim()} err=${err.trim()}")
                if (exitCode == 0 && (out.contains("Success", ignoreCase = true) || err.contains("Success", ignoreCase = true))) {
                    return true
                }
            } catch (e: Exception) {
                log("trySilentInstall cmd=${cmd.first()} exception=${e.javaClass.simpleName}:${e.message}")
            }
        }
        return false
    }

    private fun prepareInstallFile(apkFile: File): Result {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return Result(
                success = false,
                errorCode = "UPDATE_APK_MISSING",
                message = "apk file missing",
                failedStage = "install_prepare",
                pathKind = "missing"
            )
        }

        if (Build.VERSION.SDK_INT >= 24) {
            return fileResult(apkFile, pathKind = "private_cache_fileprovider", uriKind = "content")
        }

        // On Android 4.2.2 (API 17), PackageInstaller runs as an unprivileged process.
        // We prepare the install file with explicit chmod penetration to prevent "解析包失败" (EACCES).
        val externalResult = prepareExternalStorageApk(apkFile)
        if (externalResult.success && externalResult.installFile != null) {
            return externalResult
        }

        // Fallback: If external storage is unavailable or unmounted, prepare internal world-readable APK.
        val internalResult = prepareInternalWorldReadableApk(apkFile)
        if (internalResult.success && internalResult.installFile != null) {
            return internalResult
        }

        return externalResult
    }

    private fun prepareExternalStorageApk(apkFile: File): Result {
        val state = Environment.getExternalStorageState()
        if (state != Environment.MEDIA_MOUNTED) {
            return Result(
                success = false,
                errorCode = "UPDATE_PUBLIC_STORAGE_UNAVAILABLE",
                message = "external storage state=$state",
                failedStage = "install_prepare_storage",
                pathKind = "public_downloads"
            )
        }

        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val publicDir = File(downloadsDir, PUBLIC_UPDATE_DIR_NAME)
        if (!publicDir.exists() && !publicDir.mkdirs()) {
            return Result(
                success = false,
                errorCode = "UPDATE_PUBLIC_DIR_CREATE_FAILED",
                message = "failed to create public update dir",
                failedStage = "install_prepare_dir",
                pathKind = "public_downloads"
            )
        }

        val target = File(publicDir, apkFile.name)
        return try {
            if (target.absolutePath != apkFile.absolutePath) {
                apkFile.copyTo(target, overwrite = true)
            }
            // Ensure world permissions on external directory tree down to the APK.
            execChmod("777", downloadsDir)
            execChmod("777", publicDir)
            execChmod("777", target)
            log("update install prepared public apk bytes=${target.length()} readable=${target.canRead()}")
            fileResult(target, pathKind = "public_downloads", uriKind = "file")
        } catch (e: Exception) {
            Result(
                success = false,
                errorCode = "UPDATE_PUBLIC_APK_COPY_FAILED",
                message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                failedStage = "install_prepare_copy",
                pathKind = "public_downloads"
            )
        }
    }

    private fun prepareInternalWorldReadableApk(apkFile: File): Result {
        return try {
            val internalDir = File(appContext.cacheDir, "installer")
            if (!internalDir.exists() && !internalDir.mkdirs()) {
                return Result(
                    success = false,
                    errorCode = "UPDATE_INTERNAL_DIR_CREATE_FAILED",
                    message = "failed to create internal installer dir",
                    failedStage = "install_prepare_internal_dir",
                    pathKind = "internal_cache"
                )
            }

            val target = File(internalDir, apkFile.name)
            if (target.absolutePath != apkFile.absolutePath) {
                apkFile.copyTo(target, overwrite = true)
            }

            // On ext4 internal storage, chmod 755 on app dataDir allows external processes (PackageInstaller)
            // to traverse into cacheDir and read world-readable APK.
            val dataDir = File(appContext.applicationInfo.dataDir)
            execChmod("755", dataDir)
            execChmod("755", appContext.cacheDir)
            execChmod("755", internalDir)
            execChmod("777", target)

            log("update install prepared internal apk bytes=${target.length()} readable=${target.canRead()}")
            fileResult(target, pathKind = "internal_cache_world_readable", uriKind = "file")
        } catch (e: Exception) {
            Result(
                success = false,
                errorCode = "UPDATE_INTERNAL_APK_PREPARE_FAILED",
                message = "${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                failedStage = "install_prepare_internal_copy",
                pathKind = "internal_cache"
            )
        }
    }

    private fun execChmod(mode: String, file: File) {
        file.setReadable(true, false)
        file.setExecutable(true, false)
        runCatching {
            Runtime.getRuntime().exec(arrayOf("chmod", mode, file.absolutePath)).waitFor()
        }
    }

    private fun fileResult(file: File, pathKind: String, uriKind: String): Result {
        val parent = file.parentFile
        return Result(
            success = true,
            pathKind = pathKind,
            uriKind = uriKind,
            apkReadable = file.canRead(),
            parentReadable = parent?.canRead() ?: false,
            parentExecutable = parent?.canExecute() ?: false,
            installFile = file
        )
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val PUBLIC_UPDATE_DIR_NAME = "SkodaMusicUpdates"
    }
}
