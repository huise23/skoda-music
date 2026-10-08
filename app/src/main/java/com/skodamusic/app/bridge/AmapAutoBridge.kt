package com.skodamusic.app.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Bridge for controlling AutoNavi (Amap) Car Edition (com.autonavi.amapauto).
 * Uses official AutoNavi standard broadcast protocol and androidauto:// URI scheme.
 * Compatible with Android API 17+.
 */
object AmapAutoBridge {
    private const val TAG = "AmapAutoBridge"

    // Standard broadcast action exported by Amap Auto
    const val ACTION_AUTONAVI_BROADCAST = "AUTONAVI_STANDARD_BROADCAST_RECV"

    // Key types for AUTONAVI_STANDARD_BROADCAST_RECV
    private const val KEY_TYPE_SEARCH_NAVI = 10007
    private const val KEY_TYPE_SPECIAL_DEST = 10038
    private const val KEY_TYPE_EXIT_NAVI = 10021

    /**
     * Start navigation to a destination by keyword.
     * Tries the standard broadcast first, then falls back to androidauto:// URI scheme.
     */
    fun startNavi(context: Context, destination: String): Boolean {
        if (destination.isBlank()) return false
        val trimmedDest = destination.trim()

        try {
            // 1. Try sending standard broadcast first
            val broadcastIntent = Intent(ACTION_AUTONAVI_BROADCAST).apply {
                putExtra("KEY_TYPE", KEY_TYPE_SEARCH_NAVI)
                putExtra("DEST", trimmedDest)
                putExtra("IS_START_NAVI", 1) // 1 = start navigation directly
            }
            context.sendBroadcast(broadcastIntent)
            Log.i(TAG, "Sent standard broadcast navigation for destination: $trimmedDest")

            // 2. Also send explicit URI scheme Intent to ensure the map activity is brought to foreground
            val uriIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("androidauto://keywordNavi?keyword=" + Uri.encode(trimmedDest))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(uriIntent)
            Log.i(TAG, "Started keywordNavi activity for destination: $trimmedDest")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Amap navigation: ${e.message}", e)
            return false
        }
    }

    /**
     * Search POI around current location (e.g., gas station, parking).
     */
    fun searchNearby(context: Context, keyword: String): Boolean {
        if (keyword.isBlank()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("androidauto://arroundpoi?keywords=" + Uri.encode(keyword.trim()))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Started arroundpoi for keyword: $keyword")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search nearby POI: ${e.message}", e)
            false
        }
    }

    /**
     * Navigate to special destination: "HOME" or "CORP".
     */
    fun navigateSpecialDest(context: Context, destType: String): Boolean {
        val target = if (destType.equals("HOME", ignoreCase = true)) "HOME" else "CORP"
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("androidauto://navi2SpecialDest?dest=$target")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to special dest $target: ${e.message}", e)
            false
        }
    }

    /**
     * Exit active navigation.
     */
    fun exitNavi(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("androidauto://naviExit")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to exit navigation: ${e.message}", e)
            false
        }
    }
}
