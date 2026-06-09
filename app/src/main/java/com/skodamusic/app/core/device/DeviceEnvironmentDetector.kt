package com.skodamusic.app.core.device

import android.os.Build

object DeviceEnvironmentDetector {
    fun isLikelyEmulator(): Boolean {
        val buildSignalCount = listOf(
            containsAny(Build.FINGERPRINT, "generic", "unknown"),
            containsAny(Build.MODEL, "google_sdk", "emulator", "android sdk built for"),
            containsAny(Build.MANUFACTURER, "genymotion"),
            startsWith(Build.BRAND, "generic") && startsWith(Build.DEVICE, "generic"),
            containsAny(Build.PRODUCT, "sdk", "google_sdk", "sdk_x86", "vbox86p", "emulator"),
            containsAny(Build.HARDWARE, "goldfish", "ranchu", "vbox86")
        ).count { it }

        return isQemuBacked() || buildSignalCount >= 2
    }

    private fun isQemuBacked(): Boolean {
        return try {
            val systemProperties = Class.forName("android.os.SystemProperties")
            val get = systemProperties.getMethod("get", String::class.java)
            get.invoke(null, "ro.kernel.qemu") == "1"
        } catch (_: Exception) {
            false
        }
    }

    private fun containsAny(value: String?, vararg needles: String): Boolean {
        val normalized = value?.lowercase() ?: return false
        return needles.any { normalized.contains(it) }
    }

    private fun startsWith(value: String?, prefix: String): Boolean {
        return value?.lowercase()?.startsWith(prefix) == true
    }
}
