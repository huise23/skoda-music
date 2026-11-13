package com.skodamusic.app.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Robust Root Shell Helper for Android 4.2.2 (API 17) car head units.
 *
 * Avoids the pitfalls of `arrayOf("su", "-c", "...")` which fails on many OEM Toybox/custom su binaries.
 * Uses standard stream piping (`DataOutputStream`) to feed shell commands directly into `su`,
 * handling stdout/stderr concurrently without deadlocking.
 */
object RootShellHelper {
    private const val TAG = "RootShellHelper"

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean
            get() = exitCode == 0
    }

    fun isRootAvailable(): Boolean {
        val res = runCommand("id", 5000L)
        return res.isSuccess && (res.stdout.contains("uid=0") || res.stdout.contains("root"))
    }

    fun runCommand(cmd: String, timeoutMs: Long = 30000L): CommandResult {
        return runCommands(listOf(cmd), timeoutMs)
    }

    fun runCommands(commands: List<String>, timeoutMs: Long = 45000L): CommandResult {
        var proc: Process? = null
        try {
            proc = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(proc.outputStream)
            for (cmd in commands) {
                os.writeBytes(cmd + "\n")
            }
            os.writeBytes("exit\n")
            os.flush()

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val outThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stdoutBuilder.append(line).append("\n")
                        }
                    }
                }
            }

            val errThread = Thread {
                runCatching {
                    BufferedReader(InputStreamReader(proc.errorStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            stderrBuilder.append(line).append("\n")
                        }
                    }
                }
            }

            outThread.start()
            errThread.start()

            val exitCode = proc.waitFor()
            outThread.join(2000L)
            errThread.join(2000L)

            return CommandResult(
                exitCode = exitCode,
                stdout = stdoutBuilder.toString().trim(),
                stderr = stderrBuilder.toString().trim()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Root execution failed: ${e.message}", e)
            return CommandResult(-1, "", e.message ?: "su execution exception")
        } finally {
            proc?.destroy()
        }
    }

    /**
     * Installs an APK silently via Root with full UID 1000 / PM access bypass.
     * Copies the APK to /data/local/tmp/silent_install_temp.apk, sets 777, and runs pm install.
     */
    fun installApkViaRoot(apkFile: File): Pair<Boolean, String> {
        if (!apkFile.exists()) {
            return false to "安装包文件不存在: ${apkFile.absolutePath}"
        }
        val tmpPath = "/data/local/tmp/silent_install_temp.apk"
        val commands = listOf(
            "settings put secure install_non_market_apps 1",
            "cat \"${apkFile.absolutePath}\" > \"$tmpPath\"",
            "chmod 777 \"$tmpPath\"",
            "pm install -r \"$tmpPath\"",
            "rm -f \"$tmpPath\""
        )
        val result = runCommands(commands, 60000L)
        val combined = "${result.stdout} ${result.stderr}".trim()
        val ok = result.exitCode == 0 && combined.contains("Success", ignoreCase = true)
        return if (ok) {
            true to "安装成功！"
        } else {
            false to "Root pm install 退出码 ${result.exitCode}: $combined"
        }
    }

    /**
     * Fallback to system package installer intent.
     * Ensures unknown sources are unlocked via Root if possible before dispatching.
     */
    fun installViaSystemPackageInstaller(context: Context, apkFile: File): Boolean {
        return try {
            // Proactively unlock unknown sources if root is accessible
            runCommand("settings put secure install_non_market_apps 1", 3000L)
            runCommand("chmod 777 \"${apkFile.absolutePath}\"", 3000L)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            false
        }
    }

    /**
     * Converts current app into a privileged system app (/system/app/SkodaMusic.apk) via Root.
     * System apps cannot be killed by background low-memory killers, have guaranteed boot broadcasts,
     * and bypass stopped-state restrictions.
     */
    fun convertToSystemApp(context: Context): Pair<Boolean, String> {
        val apkPath = context.packageCodePath
        val targetPath = "/system/app/SkodaMusic.apk"
        val commands = listOf(
            "mount -o remount,rw /system",
            "cat \"$apkPath\" > \"$targetPath\"",
            "chmod 644 \"$targetPath\"",
            "chown 0.0 \"$targetPath\"",
            "mount -o remount,ro /system"
        )
        val result = runCommands(commands, 30000L)
        val targetFile = File(targetPath)
        val success = result.isSuccess && targetFile.exists() && targetFile.length() > 0L
        return if (success) {
            true to "已成功固化到 $targetPath！\n\n应用已升级为车机原生系统应用，重启车机后生效，开机将拥有系统级最高自启优先级与防杀保护！"
        } else {
            false to "固化失败 (退出码 ${result.exitCode}): ${result.stderr.ifBlank { result.stdout }}"
        }
    }
}
