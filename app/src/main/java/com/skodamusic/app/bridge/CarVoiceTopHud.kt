package com.skodamusic.app.bridge

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.skodamusic.app.R

/**
 * OEM-style global top floating voice HUD banner.
 * Displays a single-line prominent banner docked at the top of the screen:
 * "🎙️ 正在倾听，请对手机说话..."
 *
 * Appears above all applications (including Gaode Map and third-party launchers)
 * using WindowManager TYPE_PHONE (API 17 compatible).
 */
object CarVoiceTopHud {
    private const val TAG = "CarVoiceTopHud"
    private const val DEFAULT_DISMISS_DELAY_MS = 6000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var hudView: View? = null
    private var tvIcon: TextView? = null
    private var tvText: TextView? = null
    private var isShowing = false

    private val dismissRunnable = Runnable {
        hide()
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    @Synchronized
    fun show(
        context: Context,
        icon: String = "🎙️",
        text: String = "正在倾听，请对手机说话...",
        autoDismissMs: Long = DEFAULT_DISMISS_DELAY_MS
    ) {
        mainHandler.post {
            try {
                mainHandler.removeCallbacks(dismissRunnable)
                val appContext = context.applicationContext
                val wm = windowManager ?: (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also {
                    windowManager = it
                }

                if (hudView == null) {
                    val inflater = LayoutInflater.from(appContext)
                    val view = inflater.inflate(R.layout.layout_car_voice_top_hud, null)
                    tvIcon = view.findViewById(R.id.tv_voice_top_hud_icon)
                    tvText = view.findViewById(R.id.tv_voice_top_hud_text)

                    view.setOnClickListener {
                        hide()
                    }
                    hudView = view
                }

                tvIcon?.text = icon
                tvText?.text = text

                if (!isShowing && hudView != null) {
                    val type = when {
                        Build.VERSION.SDK_INT >= 26 -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        else -> WindowManager.LayoutParams.TYPE_PHONE
                    }

                    val params = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        dpToPx(appContext, 46),
                        type,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                        x = 0
                        y = 0
                    }

                    try {
                        wm.addView(hudView, params)
                        isShowing = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to add HUD view to window manager: ${e.message}")
                    }
                }

                if (autoDismissMs > 0) {
                    mainHandler.postDelayed(dismissRunnable, autoDismissMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "show error: ${e.message}", e)
            }
        }
    }

    fun showListening(context: Context, text: String = "正在倾听，请对手机说话...") {
        show(context, icon = "🎙️", text = text, autoDismissMs = 8000L)
    }

    fun showSuccess(context: Context, text: String = "手机助手已就绪，请对手机说话", autoDismissMs: Long = 5000L) {
        show(context, icon = "✅", text = text, autoDismissMs = autoDismissMs)
    }

    fun showError(context: Context, text: String, autoDismissMs: Long = 4000L) {
        show(context, icon = "⚠️", text = text, autoDismissMs = autoDismissMs)
    }

    @Synchronized
    fun hide() {
        mainHandler.post {
            mainHandler.removeCallbacks(dismissRunnable)
            if (isShowing && hudView != null && windowManager != null) {
                try {
                    windowManager?.removeView(hudView)
                } catch (e: Exception) {
                    Log.w(TAG, "removeView error: ${e.message}")
                }
                isShowing = false
            }
        }
    }
}
