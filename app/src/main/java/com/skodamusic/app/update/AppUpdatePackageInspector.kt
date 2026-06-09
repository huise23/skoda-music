package com.skodamusic.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class AppUpdatePackageInspector(context: Context) {
    data class ArchiveInfo(
        val packageName: String,
        val versionCode: Long,
        val signerDigest: String
    )

    private val appContext = context.applicationContext

    fun readArchive(apkFile: File): ArchiveInfo? {
        val pkg = resolveArchivePackageInfo(apkFile) ?: return null
        return ArchiveInfo(
            packageName = pkg.packageName.orEmpty(),
            versionCode = resolveVersionCode(pkg),
            signerDigest = resolveArchiveSignerDigest(pkg)
        )
    }

    fun installedSignerDigest(): String {
        return try {
            @Suppress("DEPRECATION")
            val pkg = appContext.packageManager.getPackageInfo(appContext.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            val sig = pkg.signatures?.firstOrNull()?.toByteArray() ?: return ""
            sha256Hex(sig)
        } catch (_: Exception) {
            ""
        }
    }

    fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(FILE_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) {
                    break
                }
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun resolveArchivePackageInfo(apkFile: File): PackageInfo? {
        return try {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveVersionCode(pkg: PackageInfo): Long {
        return try {
            if (Build.VERSION.SDK_INT >= 28) {
                val field = pkg.javaClass.getField("longVersionCode")
                field.getLong(pkg)
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun resolveArchiveSignerDigest(pkg: PackageInfo): String {
        return try {
            @Suppress("DEPRECATION")
            val sig = pkg.signatures?.firstOrNull()?.toByteArray() ?: return ""
            sha256Hex(sig)
        } catch (_: Exception) {
            ""
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val value = b.toInt() and 0xff
            if (value < 16) {
                out.append('0')
            }
            out.append(value.toString(16))
        }
        return out.toString().lowercase(Locale.US)
    }

    private companion object {
        const val FILE_BUFFER_BYTES = 8 * 1024
    }
}
