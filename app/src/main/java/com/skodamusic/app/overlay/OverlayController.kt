package com.skodamusic.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackStateStore
import kotlin.math.abs

class OverlayController(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onCommand(action: String)
        fun onDismissByUser()
        fun onTrackTitleClicked()
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences(PREFS_OVERLAY, Context.MODE_PRIVATE)
    private var rootView: FrameLayout? = null
    private var titleView: TextView? = null
    private var progressBar: ProgressBar? = null
    private var playPauseButton: ImageButton? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null
    private var dragDownRawX: Float = 0f
    private var dragDownRawY: Float = 0f
    private var dragStartX: Int = 0
    private var dragStartY: Int = 0
    private var titleDragging: Boolean = false

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
            val params = buildLayoutParams()
            currentLayoutParams = params
            windowManager.addView(view, params)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun update(snapshot: PlaybackStateStore.Snapshot) {
        titleView?.text = if (snapshot.trackTitle.isNotBlank()) snapshot.trackTitle else "未加载曲目"
        val durationMs = snapshot.durationMs.coerceAtLeast(0L)
        val positionMs = snapshot.positionMs.coerceAtLeast(0L)
        val percent = if (durationMs <= 0L) 0 else ((positionMs * PROGRESS_MAX) / durationMs).toInt()
        progressBar?.progress = percent.coerceIn(0, PROGRESS_MAX)
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

        val container = FrameLayout(context)
        container.setBackgroundColor(Color.parseColor("#CC162431"))
        val padding = dp(12)
        container.setPadding(padding, padding, padding, padding)

        val content = LinearLayout(context)
        content.orientation = LinearLayout.VERTICAL
        content.gravity = Gravity.CENTER_HORIZONTAL
        container.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val title = TextView(context)
        title.setTextColor(Color.WHITE)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        title.setTypeface(Typeface.DEFAULT_BOLD)
        title.maxLines = 2
        title.gravity = Gravity.CENTER
        bindTitleTouch(title)

        val header = FrameLayout(context)
        val headerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(8)
        }
        content.addView(header, headerParams)

        header.addView(
            title,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                marginStart = dp(32)
                marginEnd = dp(32)
            }
        )

        val close = createTopCloseButton {
            hide()
            listener.onDismissByUser()
        }
        header.addView(
            close,
            FrameLayout.LayoutParams(dp(32), dp(32), Gravity.END or Gravity.CENTER_VERTICAL)
        )

        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = PROGRESS_MAX
            progress = 0
        }
        val progressParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(3)
        ).apply {
            bottomMargin = dp(10)
        }
        content.addView(progress, progressParams)

        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val rowParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        content.addView(actionRow, rowParams)

        val prev = createControlButton(android.R.drawable.ic_media_previous) {
            listener.onCommand(PlaybackActions.ACTION_CMD_PREV)
        }
        val playPause = createControlButton(android.R.drawable.ic_media_play) {
            listener.onCommand(PlaybackActions.ACTION_CMD_PLAY_PAUSE)
        }
        val next = createControlButton(android.R.drawable.ic_media_next) {
            listener.onCommand(PlaybackActions.ACTION_CMD_NEXT)
        }
        actionRow.addView(prev, buildControlButtonParams(rightMargin = dp(4)))
        actionRow.addView(playPause, buildControlButtonParams(rightMargin = dp(4)))
        actionRow.addView(next, buildControlButtonParams())

        rootView = container
        titleView = title
        progressBar = progress
        playPauseButton = playPause
    }

    private fun createControlButton(iconRes: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }
    }

    private fun createTopCloseButton(onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(com.skodamusic.app.R.drawable.ic_overlay_close)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }
    }

    private fun buildControlButtonParams(rightMargin: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(68), 1f).apply {
            this.rightMargin = rightMargin
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = when {
            Build.VERSION.SDK_INT >= 26 -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            dp(240),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = prefs.getInt(KEY_POS_X, dp(8))
            y = prefs.getInt(KEY_POS_Y, dp(44))
        }
    }

    private fun bindTitleTouch(title: TextView) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        title.setOnTouchListener { _, event ->
            val params = currentLayoutParams ?: return@setOnTouchListener false
            val view = rootView ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragDownRawX = event.rawX
                    dragDownRawY = event.rawY
                    dragStartX = params.x
                    dragStartY = params.y
                    titleDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragDownRawX).toInt()
                    val dy = (event.rawY - dragDownRawY).toInt()
                    if (!titleDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        titleDragging = true
                    }
                    if (!titleDragging) {
                        return@setOnTouchListener true
                    }
                    params.x = dragStartX - dx
                    params.y = (dragStartY + dy).coerceAtLeast(0)
                    runCatching {
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (titleDragging) {
                        persistOverlayPosition(params.x, params.y)
                    } else {
                        listener.onTrackTitleClicked()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (titleDragging) {
                        persistOverlayPosition(params.x, params.y)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun persistOverlayPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_POS_X, x)
            .putInt(KEY_POS_Y, y.coerceAtLeast(0))
            .apply()
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val PROGRESS_MAX = 1000
        const val PREFS_OVERLAY = "overlay_ui_state"
        const val KEY_POS_X = "pos_x"
        const val KEY_POS_Y = "pos_y"
    }
}
