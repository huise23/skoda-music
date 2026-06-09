package com.skodamusic.app.update

import android.content.Context
import java.io.File

class AppUpdateApkVerifier(context: Context) {
    data class Result(
        val success: Boolean,
        val errorCode: String = "",
        val message: String = "",
        val failedStage: String = "",
        val expectedBytes: Long = -1L,
        val apkBytes: Long = -1L,
        val preparseResult: String = "",
        val preparsePackage: String = "",
        val preparseVersionCode: Long = -1L,
        val archiveSignerDigest: String = ""
    )

    private val appContext = context.applicationContext
    private val packageInspector = AppUpdatePackageInspector(appContext)

    fun validateDownloadFile(
        apkFile: File,
        expectedBytes: Long,
        expectedSha256Digest: String
    ): Result {
        val fileCheck = checkFileReadable(apkFile, expectedBytes)
        if (!fileCheck.success) {
            return fileCheck
        }

        val archiveInfo = packageInspector.readArchive(apkFile) ?: return Result(
            success = false,
            errorCode = "UPDATE_APK_PARSE_FAILED",
            message = "cannot parse archive package info",
            failedStage = "apk_preparse",
            expectedBytes = expectedBytes,
            apkBytes = apkFile.length(),
            preparseResult = "failed"
        )
        val packageResult = checkPackageName(archiveInfo, apkFile, expectedBytes)
        if (!packageResult.success) {
            return packageResult
        }
        if (expectedSha256Digest.isNotBlank()) {
            val actualDigest = runCatching { packageInspector.sha256File(apkFile) }.getOrDefault("")
            if (actualDigest.isBlank() || !actualDigest.equals(expectedSha256Digest, ignoreCase = true)) {
                return Result(
                    success = false,
                    errorCode = "UPDATE_APK_DIGEST_MISMATCH",
                    message = "downloaded apk digest mismatch",
                    failedStage = "apk_digest",
                    expectedBytes = expectedBytes,
                    apkBytes = apkFile.length(),
                    preparseResult = "ok",
                    preparsePackage = archiveInfo.packageName,
                    preparseVersionCode = archiveInfo.versionCode,
                    archiveSignerDigest = archiveInfo.signerDigest
                )
            }
        }
        return parsedSuccess(apkFile, expectedBytes, archiveInfo)
    }

    fun verifyInstallCompatibility(apkFile: File, localVersionCode: Long): Result {
        val fileCheck = checkFileReadable(apkFile, expectedBytes = -1L)
        if (!fileCheck.success) {
            return fileCheck
        }
        val archiveInfo = packageInspector.readArchive(apkFile) ?: return Result(
            success = false,
            errorCode = "UPDATE_APK_PARSE_FAILED",
            message = "cannot parse archive package info",
            failedStage = "apk_preparse",
            apkBytes = apkFile.length(),
            preparseResult = "failed"
        )
        val packageResult = checkPackageName(archiveInfo, apkFile, expectedBytes = -1L)
        if (!packageResult.success) {
            return packageResult
        }
        val apkVersion = archiveInfo.versionCode
        if (localVersionCode > 0L && apkVersion > 0L && apkVersion <= localVersionCode) {
            return Result(
                success = false,
                errorCode = "UPDATE_APK_NOT_NEWER",
                message = "apk versionCode=$apkVersion <= local versionCode=$localVersionCode",
                failedStage = "apk_version",
                apkBytes = apkFile.length(),
                preparseResult = "ok",
                preparsePackage = archiveInfo.packageName,
                preparseVersionCode = apkVersion,
                archiveSignerDigest = archiveInfo.signerDigest
            )
        }

        val installedSigner = packageInspector.installedSignerDigest()
        val apkSigner = archiveInfo.signerDigest
        if (installedSigner.isNotBlank() && apkSigner.isNotBlank() &&
            !installedSigner.equals(apkSigner, ignoreCase = true)
        ) {
            return Result(
                success = false,
                errorCode = "UPDATE_SIGNATURE_MISMATCH",
                message = "installed/apk signer mismatch installed=$installedSigner apk=$apkSigner",
                failedStage = "apk_signature",
                apkBytes = apkFile.length(),
                preparseResult = "ok",
                preparsePackage = archiveInfo.packageName,
                preparseVersionCode = apkVersion,
                archiveSignerDigest = apkSigner
            )
        }
        return parsedSuccess(apkFile, expectedBytes = -1L, archiveInfo = archiveInfo)
    }

    private fun checkFileReadable(apkFile: File, expectedBytes: Long): Result {
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return Result(
                success = false,
                errorCode = "UPDATE_APK_FILE_EMPTY",
                message = "downloaded apk is empty",
                failedStage = "apk_file",
                expectedBytes = expectedBytes,
                apkBytes = if (apkFile.exists()) apkFile.length() else -1L
            )
        }
        if (expectedBytes > 0L && apkFile.length() != expectedBytes) {
            return Result(
                success = false,
                errorCode = "UPDATE_APK_SIZE_MISMATCH",
                message = "downloaded apk size=${apkFile.length()} expected=$expectedBytes",
                failedStage = "apk_size",
                expectedBytes = expectedBytes,
                apkBytes = apkFile.length()
            )
        }
        return Result(success = true, expectedBytes = expectedBytes, apkBytes = apkFile.length())
    }

    private fun checkPackageName(
        archiveInfo: AppUpdatePackageInspector.ArchiveInfo,
        apkFile: File,
        expectedBytes: Long
    ): Result {
        if (!archiveInfo.packageName.equals(appContext.packageName, ignoreCase = false)) {
            return Result(
                success = false,
                errorCode = "UPDATE_PACKAGE_NAME_MISMATCH",
                message = "archive package=${archiveInfo.packageName} local=${appContext.packageName}",
                failedStage = "apk_package",
                expectedBytes = expectedBytes,
                apkBytes = apkFile.length(),
                preparseResult = "ok",
                preparsePackage = archiveInfo.packageName,
                preparseVersionCode = archiveInfo.versionCode,
                archiveSignerDigest = archiveInfo.signerDigest
            )
        }
        return Result(success = true)
    }

    private fun parsedSuccess(
        apkFile: File,
        expectedBytes: Long,
        archiveInfo: AppUpdatePackageInspector.ArchiveInfo
    ): Result {
        return Result(
            success = true,
            expectedBytes = expectedBytes,
            apkBytes = apkFile.length(),
            preparseResult = "ok",
            preparsePackage = archiveInfo.packageName,
            preparseVersionCode = archiveInfo.versionCode,
            archiveSignerDigest = archiveInfo.signerDigest
        )
    }
}
