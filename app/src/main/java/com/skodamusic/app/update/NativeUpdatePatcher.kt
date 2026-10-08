package com.skodamusic.app.update

object NativeUpdatePatcher {
    private var nativeLoaded = false

    init {
        try {
            System.loadLibrary("native-playback")
            nativeLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            nativeLoaded = false
        }
    }

    fun isAvailable(): Boolean = nativeLoaded

    fun applyPatch(oldApkPath: String, newApkPath: String, patchPath: String): Int {
        if (!nativeLoaded) {
            return -999
        }
        return nativeApplyPatch(oldApkPath, newApkPath, patchPath)
    }

    @JvmStatic
    private external fun nativeApplyPatch(
        oldApkPath: String,
        newApkPath: String,
        patchPath: String
    ): Int
}
