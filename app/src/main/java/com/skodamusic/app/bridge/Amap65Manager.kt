package com.skodamusic.app.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Manager for downloading, checking and installing AutoNavi Car Edition (Amap Auto)
 * Version 6.6.0 (Auto_V6.6.0.602552_release_signed.apk).
 *
 * This version is verified to run on Android 4.0+ (minSdkVersion=14), features
 * enhanced real-time traffic light countdown, lane guidance, and optimized stability for 1GB RAM.
 * Fully compatible with Android API 17 (Jelly Bean 4.2.2).
 */
object Amap65Manager {
    private const val TAG = "AmapAutoManager"

    const val PACKAGE_NAME = "com.autonavi.amapauto"
    const val TARGET_VERSION_NAME = "6.6.0.602552"
    const val OFFICIAL_DOWNLOAD_URL = "https://mapdownload.autonavi.com/apps/auto/manual/V660/Auto_V6.6.0.602552_release_signed.apk"
    const val EXPECTED_SIZE_BYTES = 85429137L
    const val TARGET_APK_FILENAME = "Auto_V6.6.0.602552_release_signed.apk"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getInstalledVersion(context: Context): String? {
        return try {
            val pi = context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            pi.versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun launchAmap(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch Amap: ${e.message}")
            false
        }
    }

    /**
     * Scans internal and USB external storage paths for existing Gaode 6.6 or 6.5 APK.
     */
    fun findLocalApk(context: Context): File? {
        val candidateDirs = listOfNotNull(
            context.externalCacheDir,
            context.cacheDir,
            File("/sdcard"),
            File("/sdcard/Download"),
            File("/storage/udisk"),
            File("/storage/udisk0"),
            File("/storage/udisk1"),
            File("/storage/sdcard1"),
            File("/storage/usbdisk"),
            File("/mnt/udisk"),
            File("/mnt/sdcard")
        )

        for (dir in candidateDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val direct66 = File(dir, TARGET_APK_FILENAME)
            if (direct66.exists() && direct66.length() > 50_000_000L) {
                return direct66
            }
            val direct65 = File(dir, "Auto_V6.5.0.601571_release.apk")
            if (direct65.exists() && direct65.length() > 50_000_000L) {
                return direct65
            }
            // Search other matching apk files
            val matches = dir.listFiles { f ->
                f.isFile && f.name.endsWith(".apk", ignoreCase = true) &&
                        (f.name.contains("Auto_V6.6", ignoreCase = true) ||
                                f.name.contains("Auto_V6.5", ignoreCase = true) ||
                                (f.name.contains("amap", ignoreCase = true) && f.length() > 70_000_000L))
            }
            if (!matches.isNullOrEmpty()) {
                return matches.first()
            }
        }
        return null
    }

    /**
     * Executes root silent install via pm install -r.
     */
    fun installSilentViaRoot(apkFile: File): Pair<Boolean, String> {
        val path = apkFile.absolutePath
        if (!apkFile.exists()) {
            return false to "安装包文件不存在: $path"
        }

        try {
            // Ensure settings allows non-market apps
            Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure install_non_market_apps 1")).waitFor()
            Runtime.getRuntime().exec(arrayOf("su", "-c", "chmod 777 \"$path\"")).waitFor()

            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm install -r \"$path\""))
            val stdout = proc.inputStream.bufferedReader().use { it.readText() }
            val stderr = proc.errorStream.bufferedReader().use { it.readText() }
            val exitCode = proc.waitFor()

            Log.i(TAG, "Root pm install exitCode=$exitCode, out=$stdout, err=$stderr")
            val output = "$stdout $stderr".trim()
            val isSuccess = exitCode == 0 && output.contains("Success", ignoreCase = true)

            return if (isSuccess) {
                true to "安装成功！"
            } else {
                false to "pm install 退出码 $exitCode: $output"
            }
        } catch (e: Exception) {
            Log.e(TAG, "installSilentViaRoot failed: ${e.message}", e)
            return false to (e.message ?: "执行 Root 命令失败")
        }
    }

    /**
     * Downloads Gaode Auto 6.6.0 directly from AutoNavi official server and installs via Root.
     */
    fun downloadAndInstall(
        context: Context,
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long) -> Unit,
        onComplete: (success: Boolean, message: String) -> Unit
    ) {
        val targetDir = context.externalCacheDir ?: context.cacheDir
        val targetFile = File(targetDir, TARGET_APK_FILENAME)
        val tmpFile = File(targetDir, "$TARGET_APK_FILENAME.tmp")

        executor.execute {
            // 1. Check if targetFile is already valid
            if (targetFile.exists() && targetFile.length() == EXPECTED_SIZE_BYTES) {
                mainHandler.post {
                    onProgress(100, EXPECTED_SIZE_BYTES, EXPECTED_SIZE_BYTES)
                }
                val (ok, msg) = installSilentViaRoot(targetFile)
                mainHandler.post { onComplete(ok, msg) }
                return@execute
            }

            // 2. Download from official link
            try {
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }

                val request = Request.Builder()
                    .url(OFFICIAL_DOWNLOAD_URL)
                    .header("User-Agent", "SkodaMusic-HeadUnit/1.0 (Android 4.2.2)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    mainHandler.post {
                        onComplete(false, "官方服务器连接失败: HTTP ${response.code()}")
                    }
                    return@execute
                }

                val body = response.body() ?: throw IllegalStateException("Empty response body")
                val contentLength = if (body.contentLength() > 0) body.contentLength() else EXPECTED_SIZE_BYTES

                val input: InputStream = body.byteStream()
                val output = FileOutputStream(tmpFile)
                val buffer = ByteArray(16384)
                var bytesReadTotal = 0L
                var lastReportPercent = -1

                input.use { inStream ->
                    output.use { outStream ->
                        var read: Int
                        while (inStream.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            bytesReadTotal += read
                            val percent = ((bytesReadTotal * 100) / contentLength).toInt().coerceIn(0, 100)
                            if (percent != lastReportPercent) {
                                lastReportPercent = percent
                                mainHandler.post {
                                    onProgress(percent, bytesReadTotal, contentLength)
                                }
                            }
                        }
                        outStream.flush()
                    }
                }

                // Rename temp file to target file
                if (tmpFile.renameTo(targetFile)) {
                    Log.i(TAG, "Download finished successfully: ${targetFile.length()} bytes")
                    mainHandler.post {
                        onProgress(100, targetFile.length(), targetFile.length())
                    }
                    val (ok, msg) = installSilentViaRoot(targetFile)
                    mainHandler.post { onComplete(ok, msg) }
                } else {
                    mainHandler.post {
                        onComplete(false, "临时安装包重命名失败")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                mainHandler.post {
                    onComplete(false, "下载失败: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
    }
}
