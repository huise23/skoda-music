package com.skodamusic.app

object NativePlaybackBridge {
    private val nativeAvailable: Boolean = try {
        System.loadLibrary("native-playback")
        true
    } catch (_: Throwable) {
        false
    }

    fun isAvailable(): Boolean = nativeAvailable

    fun initializeQueue(trackTitles: List<String>): String? {
        if (!nativeAvailable || trackTitles.isEmpty()) {
            return null
        }
        val hasTrack = nativeInitQueue(trackTitles.toTypedArray())
        if (!hasTrack) {
            return null
        }
        return nativeCurrentTitle()
    }

    fun nextTitle(): String? {
        if (!nativeAvailable) {
            return null
        }
        return nativeMoveNextAndGetTitle()
    }

    fun hasNext(): Boolean {
        if (!nativeAvailable) {
            return false
        }
        return nativeHasNext()
    }

    private external fun nativeInitQueue(trackTitles: Array<String>): Boolean
    private external fun nativeCurrentTitle(): String?
    private external fun nativeMoveNextAndGetTitle(): String?
    private external fun nativeHasNext(): Boolean
}
