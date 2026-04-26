package com.skodamusic.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackStateStore

class OverlayController(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onCommand(action: String)
        fun onDismissByUser()
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var titleView: TextView? = null
    private var playPauseButton: ImageButton? = null

    fun canDraw(): Boolean {
        return if (Build.VERSION.SDK_INT >= 23) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun show(snapshot: PlaybackStateStore.Snapshot): Boolean {
        if (!canDraw()) {
            return false
        }
        ensureView()
        update(snapshot)
        val view = rootView ?: return false
        if (view.parent != null) {
            return true
        }
        return try {
            windowManager.addView(view, buildLayoutParams())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun update(snapshot: PlaybackStateStore.Snapshot) {
        titleView?.text = if (snapshot.trackTitle.isNotBlank()) snapshot.trackTitle else "未加载曲目"
        playPauseButton?.setImageResource(
            if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
    }

    fun hide() {
        val view = rootView ?: return
        if (view.parent == null) {
            return
        }
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
        }
    }

    fun isShowing(): Boolean {
        return rootView?.parent != null
    }

    private fun ensureView() {
        if (rootView != null) {
            return
        }

        val container = LinearLayout(context)
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setBackgroundColor(Color.parseColor("#CC162431"))
        val padding = dp(10)
        container.setPadding(padding, padding, padding, padding)

        val title = TextView(context)
        title.setTextColor(Color.WHITE)
        title.textSize = 14f
        title.maxLines = 1
        val titleParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        container.addView(title, titleParams)

        val prev = ImageButton(context)
        prev.setImageResource(android.R.drawable.ic_media_previous)
        prev.setBackgroundColor(Color.TRANSPARENT)
        prev.setOnClickListener { listener.onCommand(PlaybackActions.ACTION_CMD_PREV) }
        container.addView(prev, LinearLayout.LayoutParams(dp(36), dp(36)))

        val playPause = ImageButton(context)
        playPause.setImageResource(android.R.drawable.ic_media_play)
        playPause.setBackgroundColor(Color.TRANSPARENT)
        playPause.setOnClickListener { listener.onCommand(PlaybackActions.ACTION_CMD_PLAY_PAUSE) }
        container.addView(playPause, LinearLayout.LayoutParams(dp(40), dp(40)))

        val next = ImageButton(context)
        next.setImageResource(android.R.drawable.ic_media_next)
        next.setBackgroundColor(Color.TRANSPARENT)
        next.setOnClickListener { listener.onCommand(PlaybackActions.ACTION_CMD_NEXT) }
        container.addView(next, LinearLayout.LayoutParams(dp(36), dp(36)))

        val close = ImageButton(context)
        close.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        close.setBackgroundColor(Color.TRANSPARENT)
        close.setOnClickListener {
            hide()
            listener.onDismissByUser()
        }
        container.addView(close, LinearLayout.LayoutParams(dp(34), dp(34)))

        rootView = container
        titleView = title
        playPauseButton = playPause
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = when {
            Build.VERSION.SDK_INT >= 26 -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            x = 0
            y = dp(32)
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
