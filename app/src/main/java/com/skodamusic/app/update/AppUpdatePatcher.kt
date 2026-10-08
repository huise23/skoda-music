package com.skodamusic.app.update

import android.content.Context
import java.io.File

class AppUpdatePatcher(
    context: Context,
    private val log: (String) -> Unit = {}
) {
    data class PatchResult(
        val success: Boolean,
        val synthesizedFile: File? = null,
        val errorCode: String = "",
        val message: String = "",
        val durationMs: Long = 0L
    )

    private val appContext = context.applicationContext

    fun getInstalledBaseApk(): File? {
        val path = appContext.applicationInfo.sourceDir
        if (path.isNullOrBlank()) {
            return null
        }
        val file = File(path)
        return if (file.exists() && file.canRead() && file.length() > 0L) file else null
    }

    fun isPatchingSupported(): Boolean {
        return NativeUpdatePatcher.isAvailable() && getInstalledBaseApk() != null
    }

    fun synthesizeNewApk(
        patchFile: File,
        targetFile: File
    ): PatchResult {
        if (!NativeUpdatePatcher.isAvailable()) {
            return PatchResult(
                success = false,
                errorCode = "PATCHER_NATIVE_UNAVAILABLE",
                message = "native patcher library not loaded"
            )
        }
        val baseApk = getInstalledBaseApk() ?: return PatchResult(
            success = false,
            errorCode = "BASE_APK_NOT_FOUND",
            message = "installed base apk sourceDir not readable"
        )
        if (!patchFile.exists() || patchFile.length() <= 0L) {
            return PatchResult(
                success = false,
                errorCode = "PATCH_FILE_EMPTY",
                message = "patch file does not exist or empty"
            )
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }

        val startTime = System.currentTimeMillis()
        log("start synthesizing apk base=${baseApk.name}(${baseApk.length()}B) patch=${patchFile.name}(${patchFile.length()}B)")
        val ret = NativeUpdatePatcher.applyPatch(
            oldApkPath = baseApk.absolutePath,
            newApkPath = targetFile.absolutePath,
            patchPath = patchFile.absolutePath
        )
        val elapsed = System.currentTimeMillis() - startTime

        if (ret != 0 || !targetFile.exists() || targetFile.length() <= 0L) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            log("synthesize apk failed code=$ret elapsed=${elapsed}ms")
            return PatchResult(
                success = false,
                errorCode = "PATCH_APPLY_FAILED_$ret",
                message = "bspatch native failed with code $ret",
                durationMs = elapsed
            )
        }

        log("synthesize apk success output=${targetFile.name}(${targetFile.length()}B) elapsed=${elapsed}ms")
        return PatchResult(
            success = true,
            synthesizedFile = targetFile,
            durationMs = elapsed
        )
    }
}
