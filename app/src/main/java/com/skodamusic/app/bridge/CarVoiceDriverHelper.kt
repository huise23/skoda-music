package com.skodamusic.app.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File

/**
 * Manages the original vehicle steering wheel button driver adapter (SpeechAdapt / com.jsbd.vradapter).
 *
 * Architecture Root Cause:
 * On Skoda MQB (AutoChips AC8317 / Yecon), physical steering wheel voice key events are not directly
 * sent to third-party apps, but routed via the pre-installed system adapter service:
 * SpeechAdapt.apk (package: com.jsbd.vradapter).
 *
 * If com.jsbd.vradapter was frozen or disabled during system debloating, the hardware key event
 * is dropped by Android before any broadcast or service can receive it.
 *
 * This helper detects whether com.jsbd.vradapter is disabled, and provides an instant 1-click
 * root unfreeze function while keeping the noisy old iFlytek UI (SpeechClient) disabled.
 *
 * Fully compatible with Android API 17 (Jelly Bean 4.2.2).
 */
object CarVoiceDriverHelper {
    private const val TAG = "CarVoiceDriverHelper"
    const val PKG_SPEECH_ADAPT = "com.jsbd.vradapter"
    const val PKG_IFLYTEK_CLIENT = "com.iflytek.autofly"

    data class DriverStatus(
        val isReady: Boolean,
        val detail: String,
        val needsRestore: Boolean
    )

    fun checkStatus(context: Context): DriverStatus {
        val pm = context.packageManager

        // 1. Check if disabled via pm list packages -d
        val disabledList = getDisabledPackages()
        if (disabledList.contains(PKG_SPEECH_ADAPT)) {
            return DriverStatus(
                isReady = false,
                detail = "已停用 (被 pm disable 阻断，导致物理按键无反应)",
                needsRestore = true
            )
        }

        // 2. Check if .apk is renamed to .disabled or .bak
        val candidateFiles = listOf(
            File("/system/app/SpeechAdapt.apk.disabled"),
            File("/system/app/SpeechAdapt.apk.bak"),
            File("/system/app/speechadapt.apk.disabled"),
            File("/system/app/speechadapt.apk.bak")
        )
        val frozenFile = candidateFiles.firstOrNull { it.exists() }
        if (frozenFile != null) {
            return DriverStatus(
                isReady = false,
                detail = "已冻结 (文件被重命名为 ${frozenFile.name})",
                needsRestore = true
            )
        }

        // 3. Check component enabled setting
        return try {
            val state = pm.getApplicationEnabledSetting(PKG_SPEECH_ADAPT)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                DriverStatus(
                    isReady = false,
                    detail = "已停用 (系统组件禁用，按键信号被阻断)",
                    needsRestore = true
                )
            } else {
                DriverStatus(
                    isReady = true,
                    detail = "正常运行中 (方控物理按键信号已打通)",
                    needsRestore = false
                )
            }
        } catch (_: Exception) {
            // Check if /system/app/SpeechAdapt.apk exists
            if (File("/system/app/SpeechAdapt.apk").exists() || File("/system/app/speechadapt.apk").exists()) {
                DriverStatus(
                    isReady = true,
                    detail = "就绪 (系统驱动存在)",
                    needsRestore = false
                )
            } else {
                DriverStatus(
                    isReady = false,
                    detail = "未发现 SpeechAdapt.apk (可能在原厂精简中被删除)",
                    needsRestore = false
                )
            }
        }
    }

    fun restoreDriver(): Pair<Boolean, String> {
        val logs = mutableListOf<String>()
        var success = false

        // 1. Restore file if frozen
        val candidateFiles = listOf(
            File("/system/app/SpeechAdapt.apk.disabled"),
            File("/system/app/SpeechAdapt.apk.bak"),
            File("/system/app/speechadapt.apk.disabled"),
            File("/system/app/speechadapt.apk.bak")
        )
        val frozenFile = candidateFiles.firstOrNull { it.exists() }
        if (frozenFile != null) {
            val target = File("/system/app/SpeechAdapt.apk")
            val remountCmd = "mount -o remount,rw /system && mv ${frozenFile.absolutePath} ${target.absolutePath} && chmod 644 ${target.absolutePath} && mount -o remount,ro /system"
            runCatching {
                val p = Runtime.getRuntime().exec(arrayOf("su", "-c", remountCmd))
                p.waitFor()
                if (target.exists()) {
                    logs.add("已恢复 SpeechAdapt.apk 文件")
                    success = true
                }
            }
        }

        // 2. Enable package via pm
        runCatching {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm enable $PKG_SPEECH_ADAPT"))
            val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
            val exit = p.waitFor()
            if (exit == 0 || out.contains("new state") || out.contains("enabled")) {
                logs.add("已解冻启用 $PKG_SPEECH_ADAPT")
                success = true
            }
        }

        // 3. Keep the noisy old iFlytek UI (SpeechClient) disabled so it never pops up
        runCatching {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "pm disable $PKG_IFLYTEK_CLIENT"))
        }

        return if (success) {
            true to "驱动恢复成功: ${logs.joinToString("; ")}。原车方控按键信号已恢复！"
        } else {
            false to "执行恢复命令未见成效，请确认已授予应用 Root 权限。"
        }
    }

    private fun getDisabledPackages(): Set<String> {
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("pm", "list", "packages", "-d"))
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.waitFor()
            out.lines()
                .map { it.trim().removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }
}
