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
        val installFile: File? = null
    )

    private val appContext = context.applicationContext

    fun dispatchInstallIntent(apkFile: File): Result {
        val prepared = prepareInstallFile(apkFile)
        if (!prepared.success || prepared.installFile == null) {
            return prepared
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

        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            PUBLIC_UPDATE_DIR_NAME
        )
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
            // API17 package installer cannot reliably read this app's private cache path.
            publicDir.setReadable(true, false)
            publicDir.setExecutable(true, false)
            target.setReadable(true, false)
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
