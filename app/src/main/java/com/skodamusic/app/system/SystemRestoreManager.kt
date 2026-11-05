package com.skodamusic.app.system

import java.io.File

object SystemRestoreManager {

    data class RestoreCheckResult(
        val isRootAvailable: Boolean,
        val speechClientBackupExists: Boolean,
        val speechAdaptBackupExists: Boolean
    )

    fun checkStatus(): RestoreCheckResult {
        val suFile = File("/system/xbin/su")
        val clientBak = File("/system/app/SpeechClient.apk.bak")
        val adaptBak = File("/system/app/SpeechAdapt.apk.bak")

        var rootAvailable = suFile.exists() && suFile.canExecute()
        if (!rootAvailable) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                val out = proc.inputStream.bufferedReader().use { it.readText() }
                proc.waitFor()
                if (out.contains("uid=0")) {
                    rootAvailable = true
                }
            } catch (_: Exception) {
                rootAvailable = false
            }
        }

        return RestoreCheckResult(
            isRootAvailable = rootAvailable,
            speechClientBackupExists = clientBak.exists(),
            speechAdaptBackupExists = adaptBak.exists()
        )
    }

    sealed class RestoreResult {
        object Success : RestoreResult()
        data class Failure(val reason: String) : RestoreResult()
    }

    fun executeRestoreAndReboot(log: (String) -> Unit): RestoreResult {
        log("SystemRestoreManager: starting factory restore sequence")
        val script = """
            mount -o remount,rw /system
            if [ -f /system/app/SpeechClient.apk.bak ]; then
                mv /system/app/SpeechClient.apk.bak /system/app/SpeechClient.apk
                chmod 644 /system/app/SpeechClient.apk
            fi
            if [ -f /system/app/SpeechAdapt.apk.bak ]; then
                mv /system/app/SpeechAdapt.apk.bak /system/app/SpeechAdapt.apk
                chmod 644 /system/app/SpeechAdapt.apk
            fi
            rm -f /system/xbin/su
            sync
            mount -o remount,ro /system
            reboot
        """.trimIndent()

        return try {
            val proc = Runtime.getRuntime().exec("su")
            proc.outputStream.bufferedWriter().use { writer ->
                writer.write(script)
                writer.newLine()
                writer.flush()
            }
            val err = proc.errorStream.bufferedReader().use { it.readText() }
            val exitCode = proc.waitFor()
            log("SystemRestoreManager: su finished exitCode=$exitCode err=$err")
            if (exitCode == 0) {
                RestoreResult.Success
            } else {
                RestoreResult.Failure("exitCode=$exitCode: ${err.take(120)}")
            }
        } catch (e: Exception) {
            log("SystemRestoreManager: exception=${e.javaClass.simpleName}:${e.message}")
            RestoreResult.Failure(e.message ?: "Unknown error")
        }
    }
}
