package com.skodamusic.app.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.util.Log
import java.util.Calendar

/**
 * Coordinates theme detection (Day/Night, Light/Dark) from:
 * 1. AutoNavi (Amap Auto) standard broadcast protocol (KEY_TYPE=10019, EXTRA_DAY_NIGHT_MODE)
 * 2. Android system Configuration.uiMode (UI_MODE_NIGHT_YES / NO)
 * 3. Car head-unit headlight/illumination broadcasts (HEADLIGHT_ON / OFF, ILLUMINATION_CHANGED)
 * 4. Time-based fallback (night window 18:30 - 06:30)
 *
 * Implements the user's requirement:
 * "高德地图跟随变暗后，浮窗暗色不好看，应和系统色相反"
 * (Invert system/Amap color: when Amap/system is Dark/Night, floating window is LIGHT;
 * when Amap/system is Light/Day, floating window is DARK).
 */
class OverlayThemeCoordinator(private val context: Context) {

    enum class ThemeMode {
        DARK,  // 深色浮窗 (用于白天亮色地图，高对比防反光)
        LIGHT  // 浅色微透浮窗 (用于夜间暗色地图，高亮通透防沉闷)
    }

    enum class ThemeConfig {
        AUTO,        // 自动跟随高德/系统反色（高德暗则浮窗亮，高德亮则浮窗暗）
        FORCE_DARK,  // 强制深色
        FORCE_LIGHT  // 强制浅色
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var themeConfig: ThemeConfig = loadThemeConfig()
        private set

    var isSystemDark: Boolean = initialSystemDarkGuess()
        private set

    var onThemeChanged: ((ThemeMode) -> Unit)? = null

    private var isRegistered = false

    private val themeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            handleBroadcast(action, intent)
        }
    }

    fun register() {
        if (isRegistered) return
        val filter = IntentFilter().apply {
            // AutoNavi AmapAuto status broadcasts
            addAction("AUTONAVI_STANDARD_BROADCAST_SEND")
            addAction("AUTONAVI_STANDARD_BROADCAST_RECV")

            // Android system configuration changes (night mode toggle)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)

            // Car head-unit headlight & illumination signals across various head-unit vendors
            addAction("android.intent.action.HEADLIGHT_ON")
            addAction("android.intent.action.HEADLIGHT_OFF")
            addAction("com.yecon.action.HEADLIGHT_ON")
            addAction("com.yecon.action.HEADLIGHT_OFF")
            addAction("android.intent.action.ILLUMINATION_CHANGED")
            addAction("fyt.action.ILL_STATE")
            addAction("com.android.server.service.headlight")
        }
        try {
            context.registerReceiver(themeReceiver, filter)
            isRegistered = true
            Log.i(TAG, "OverlayThemeCoordinator registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register theme receiver: ${e.message}")
        }
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(themeReceiver)
        } catch (_: Exception) {
        } finally {
            isRegistered = false
        }
    }

    fun resolveCurrentTheme(): ThemeMode {
        return when (themeConfig) {
            ThemeConfig.FORCE_DARK -> ThemeMode.DARK
            ThemeConfig.FORCE_LIGHT -> ThemeMode.LIGHT
            ThemeConfig.AUTO -> {
                // Invert system color:
                // When system / Amap is dark (night mode) -> floating window is LIGHT!
                // When system / Amap is light (day mode) -> floating window is DARK!
                if (isSystemDark) ThemeMode.LIGHT else ThemeMode.DARK
            }
        }
    }

    fun setThemeConfig(config: ThemeConfig) {
        themeConfig = config
        prefs.edit().putString(KEY_THEME_CONFIG, config.name).apply()
        notifyThemeChanged("config_set_${config.name}")
    }

    fun cycleThemeConfig(): ThemeConfig {
        val next = when (themeConfig) {
            ThemeConfig.AUTO -> ThemeConfig.FORCE_DARK
            ThemeConfig.FORCE_DARK -> ThemeConfig.FORCE_LIGHT
            ThemeConfig.FORCE_LIGHT -> ThemeConfig.AUTO
        }
        setThemeConfig(next)
        return next
    }

    private fun handleBroadcast(action: String, intent: Intent) {
        when (action) {
            "AUTONAVI_STANDARD_BROADCAST_SEND",
            "AUTONAVI_STANDARD_BROADCAST_RECV" -> {
                val keyType = intent.getIntExtra("KEY_TYPE", -1)
                if (keyType == 10019) {
                    val mode = intent.getIntExtra("EXTRA_DAY_NIGHT_MODE", -1)
                    val extraType = intent.getIntExtra("EXTRA_TYPE", -1)
                    val value = if (mode != -1) mode else extraType
                    if (value == 38 || value == 2) {
                        updateSystemDark(true, "amap_10019_night($value)")
                    } else if (value == 37 || value == 1 || value == 0) {
                        updateSystemDark(false, "amap_10019_day($value)")
                    }
                } else if (intent.hasExtra("EXTRA_DAY_NIGHT_MODE")) {
                    val mode = intent.getIntExtra("EXTRA_DAY_NIGHT_MODE", -1)
                    if (mode == 38 || mode == 2) {
                        updateSystemDark(true, "amap_extra_night($mode)")
                    } else if (mode == 37 || mode == 1) {
                        updateSystemDark(false, "amap_extra_day($mode)")
                    }
                }
            }
            Intent.ACTION_CONFIGURATION_CHANGED -> {
                val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                val isDark = uiMode == Configuration.UI_MODE_NIGHT_YES
                updateSystemDark(isDark, "ui_mode_config_changed")
            }
            "android.intent.action.HEADLIGHT_ON",
            "com.yecon.action.HEADLIGHT_ON" -> {
                updateSystemDark(true, action)
            }
            "android.intent.action.HEADLIGHT_OFF",
            "com.yecon.action.HEADLIGHT_OFF" -> {
                updateSystemDark(false, action)
            }
            "android.intent.action.ILLUMINATION_CHANGED",
            "fyt.action.ILL_STATE" -> {
                val state = intent.getIntExtra("state", -1)
                if (state == 1) {
                    updateSystemDark(true, "$action=1")
                } else if (state == 0) {
                    updateSystemDark(false, "$action=0")
                }
            }
        }
    }

    private fun updateSystemDark(dark: Boolean, source: String) {
        if (isSystemDark == dark) return
        Log.i(TAG, "isSystemDark changed to $dark by source=$source")
        isSystemDark = dark
        notifyThemeChanged(source)
    }

    private fun notifyThemeChanged(source: String) {
        val resolved = resolveCurrentTheme()
        Log.i(TAG, "notifyThemeChanged: resolved=$resolved source=$source config=$themeConfig systemDark=$isSystemDark")
        onThemeChanged?.invoke(resolved)
    }

    private fun loadThemeConfig(): ThemeConfig {
        val saved = prefs.getString(KEY_THEME_CONFIG, ThemeConfig.AUTO.name).orEmpty()
        return try {
            ThemeConfig.valueOf(saved)
        } catch (_: Exception) {
            ThemeConfig.AUTO
        }
    }

    private fun initialSystemDarkGuess(): Boolean {
        // 1. Check system Configuration
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (uiMode == Configuration.UI_MODE_NIGHT_YES) {
            return true
        }
        if (uiMode == Configuration.UI_MODE_NIGHT_NO) {
            return false
        }
        // 2. Fallback to time-of-day
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return hour >= 18 || hour < 7
    }

    companion object {
        private const val TAG = "OverlayThemeCoord"
        private const val PREFS_NAME = "overlay_theme_settings"
        private const val KEY_THEME_CONFIG = "overlay_theme_config"
    }
}
