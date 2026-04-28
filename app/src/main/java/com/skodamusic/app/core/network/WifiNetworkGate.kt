package com.skodamusic.app.core.network

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.skodamusic.app.R
import com.skodamusic.app.observability.PostHogTracker
import java.util.Locale

class WifiNetworkGate(
    private val activity: AppCompatActivity,
    private val log: (String) -> Unit
) {
    @Volatile
    private var networkPromptInFlight: Boolean = false

    fun ensureWifiConnectedForNetworkRequest(requestTag: String, promptUser: Boolean): Boolean {
        val networkType = resolveActiveNetworkType()
        val connected = networkType == "wifi"
        log("network gate request=$requestTag connected=$connected type=$networkType")
        if (connected) {
            return true
        }
        PostHogTracker.capture(
            context = activity.applicationContext,
            eventName = "network_gate_blocked",
            properties = mapOf(
                "request" to requestTag,
                "network_type" to networkType,
                "error_code" to "WIFI_NOT_CONNECTED"
            ),
            priority = PostHogTracker.Priority.HIGH
        )
        if (promptUser) {
            activity.runOnUiThread {
                showNoNetworkDialogOnce()
            }
        }
        return false
    }

    fun resolveActiveNetworkType(): String {
        return try {
            val manager = activity.getSystemService(AppCompatActivity.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "unknown"
            val info: NetworkInfo = manager.activeNetworkInfo ?: return "offline"
            if (!info.isConnected) {
                return "offline"
            }
            when (info.type) {
                ConnectivityManager.TYPE_WIFI -> "wifi"
                ConnectivityManager.TYPE_MOBILE -> "mobile"
                else -> (info.typeName ?: "unknown").lowercase(Locale.US)
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun showNoNetworkDialogOnce() {
        if (networkPromptInFlight || activity.isFinishing) {
            return
        }
        networkPromptInFlight = true
        AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_no_network_title)
            .setMessage(R.string.dialog_no_network_message)
            .setCancelable(true)
            .setPositiveButton(R.string.action_open_wifi_settings) { dialog, _ ->
                dialog.dismiss()
                openWifiSettings()
            }
            .setNegativeButton(R.string.action_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setOnDismissListener {
                networkPromptInFlight = false
            }
            .show()
    }

    private fun openWifiSettings() {
        val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
        val canOpenWifi = wifiIntent.resolveActivity(activity.packageManager) != null
        if (canOpenWifi) {
            runCatching {
                activity.startActivity(wifiIntent)
            }.onFailure {
                showWifiSettingsUnavailableToast()
            }
            return
        }
        val wirelessIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        val canOpenWireless = wirelessIntent.resolveActivity(activity.packageManager) != null
        if (canOpenWireless) {
            runCatching {
                activity.startActivity(wirelessIntent)
            }.onFailure {
                showWifiSettingsUnavailableToast()
            }
            return
        }
        showWifiSettingsUnavailableToast()
        log("wifi settings unavailable")
    }

    private fun showWifiSettingsUnavailableToast() {
        Toast.makeText(
            activity,
            activity.getString(R.string.toast_wifi_settings_unavailable),
            Toast.LENGTH_SHORT
        ).show()
    }
}
