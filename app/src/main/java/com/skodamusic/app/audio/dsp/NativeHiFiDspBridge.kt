package com.skodamusic.app.audio.dsp

import java.nio.ByteBuffer

object NativeHiFiDspBridge {
    const val STATUS_OK = 0
    const val STATUS_BYPASS = 1
    const val STATUS_ERROR = 2

    const val TIER_QUALITY = 0
    const val TIER_BALANCED = 1
    const val TIER_SAFE = 2

    const val FLAG_ACTIVE = 1
    const val FLAG_BYPASS = 1 shl 1
    const val FLAG_DEGRADED = 1 shl 2
    const val FLAG_OVER_BUDGET = 1 shl 3
    const val FLAG_UNSUPPORTED = 1 shl 4
    const val FLAG_ERROR = 1 shl 5

    @Volatile
    private var nativeAvailable: Boolean = try {
        System.loadLibrary("native-playback")
        true
    } catch (_: Throwable) {
        false
    }

    fun isAvailable(): Boolean = nativeAvailable

    fun create(): Long {
        if (!nativeAvailable) {
            return 0L
        }
        return callNative(defaultValue = 0L) { nativeCreate() }
    }

    fun release(handle: Long) {
        if (!nativeAvailable || handle == 0L) {
            return
        }
        callNative(defaultValue = Unit) { nativeRelease(handle) }
    }

    fun configure(handle: Long, sampleRate: Int, channelCount: Int): Int {
        if (!nativeAvailable || handle == 0L) {
            return STATUS_ERROR
        }
        return callNative(defaultValue = STATUS_ERROR) { nativeConfigure(handle, sampleRate, channelCount) }
    }

    fun setMode(handle: Long, enabled: Boolean, mode: HiFiDspMode, version: Int): Int {
        if (!nativeAvailable || handle == 0L) {
            return STATUS_ERROR
        }
        return callNative(defaultValue = STATUS_ERROR) {
            nativeSetMode(handle, enabled, mode.ordinal, version)
        }
    }

    fun flush(handle: Long): Int {
        if (!nativeAvailable || handle == 0L) {
            return STATUS_ERROR
        }
        return callNative(defaultValue = STATUS_ERROR) { nativeFlush(handle) }
    }

    fun processPcm16(handle: Long, input: ByteBuffer, output: ByteBuffer, byteCount: Int): Long {
        if (!nativeAvailable || handle == 0L) {
            return packStatus(STATUS_ERROR, TIER_SAFE, FLAG_ERROR, 0L)
        }
        return callNative(defaultValue = packStatus(STATUS_ERROR, TIER_SAFE, FLAG_ERROR, 0L)) {
            nativeProcessPcm16(handle, input, output, byteCount)
        }
    }

    fun status(packed: Long): Int = (packed and 0xFFL).toInt()

    fun tier(packed: Long): Int = ((packed ushr 8) and 0xFFL).toInt()

    fun flags(packed: Long): Int = ((packed ushr 16) and 0xFFFFL).toInt()

    fun costUs(packed: Long): Long = packed ushr 32

    fun tierName(tier: Int): String {
        return when (tier) {
            TIER_QUALITY -> "quality"
            TIER_BALANCED -> "balanced"
            TIER_SAFE -> "safe"
            else -> "unknown"
        }
    }

    private fun packStatus(status: Int, tier: Int, flags: Int, costUs: Long): Long {
        return (costUs.coerceAtLeast(0L) shl 32) or
            ((flags.toLong() and 0xFFFFL) shl 16) or
            ((tier.toLong() and 0xFFL) shl 8) or
            (status.toLong() and 0xFFL)
    }

    private inline fun <T> callNative(defaultValue: T, block: () -> T): T {
        return try {
            block()
        } catch (_: Throwable) {
            nativeAvailable = false
            defaultValue
        }
    }

    private external fun nativeCreate(): Long
    private external fun nativeRelease(handle: Long)
    private external fun nativeConfigure(handle: Long, sampleRate: Int, channelCount: Int): Int
    private external fun nativeSetMode(handle: Long, enabled: Boolean, modeOrdinal: Int, version: Int): Int
    private external fun nativeFlush(handle: Long): Int
    private external fun nativeProcessPcm16(
        handle: Long,
        input: ByteBuffer,
        output: ByteBuffer,
        byteCount: Int
    ): Long
}
