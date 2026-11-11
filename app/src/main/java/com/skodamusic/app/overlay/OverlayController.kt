package com.skodamusic.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.skodamusic.app.R
import com.skodamusic.app.playback.PlaybackActions
import com.skodamusic.app.playback.PlaybackStateStore
import kotlin.math.abs

/**
 * Bottom-docked immersive overlay for car head-units.
 * Stays docked at the bottom without blocking map guidance, displays single-line lyrics,
 * and supports manual collapse into a compact edge pill (no auto-timer).
 */
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

    private var isCollapsed: Boolean = prefs.getBoolean(KEY_IS_COLLAPSED, false)
    private var rootView: FrameLayout? = null
    private var dockView: View? = null
    private var pillView: View? = null
    private var titleView: TextView? = null
    private var lyricView: TextView? = null
    private var progressBar: ProgressBar? = null
    private var playPauseButton: ImageButton? = null
    private var currentLayoutParams: WindowManager.LayoutParams? = null

    private var pillDragDownY: Float = 0f
    private var pillDragStartY: Int = 0
    private var pillDragging: Boolean = false

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
        lyricView?.text = when {
            snapshot.currentLyric.isNotBlank() -> snapshot.currentLyric
            snapshot.trackTitle.isNotBlank() -> "Skoda Music 随车播控"
            else -> "点击开启音乐"
        }
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

    fun setCollapsed(collapsed: Boolean) {
        if (isCollapsed == collapsed && rootView?.parent != null) {
            return
        }
        isCollapsed = collapsed
        prefs.edit().putBoolean(KEY_IS_COLLAPSED, collapsed).apply()
        dockView?.visibility = if (collapsed) View.GONE else View.VISIBLE
        pillView?.visibility = if (collapsed) View.VISIBLE else View.GONE

        val view = rootView ?: return
        if (view.parent != null) {
            val params = buildLayoutParams()
            currentLayoutParams = params
            runCatching {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun ensureView() {
        if (rootView != null) {
            return
        }

        val root = FrameLayout(context)
        val dock = buildDockView()
        val pill = buildPillView()

        dock.visibility = if (isCollapsed) View.GONE else View.VISIBLE
        pill.visibility = if (isCollapsed) View.VISIBLE else View.GONE

        root.addView(dock, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        root.addView(pill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        rootView = root
        dockView = dock
        pillView = pill
    }

    private fun buildDockView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE101720"))
        }

        // Top 2dp accent progress line
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = PROGRESS_MAX
            progress = 0
            val progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
            }
            setProgressDrawable(progressDrawable)
        }
        container.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)))

        // Dock content row (42dp height)
        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(6), 0)
        }
        container.addView(contentRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))

        // Left clickable info section (Cover/Icon + Title + Single-line Lyric)
        val leftSection = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { listener.onTrackTitleClicked() }
        }
        contentRow.addView(leftSection, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

        val musicIcon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_media_play)
            setColorFilter(Color.parseColor("#38BDF8"))
        }
        leftSection.addView(musicIcon, LinearLayout.LayoutParams(dp(18), dp(18)))

        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        leftSection.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = "未加载曲目"
        }
        textCol.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val lyric = TextView(context).apply {
            setTextColor(Color.parseColor("#4ADE80"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = "Skoda Music 随车播控"
        }
        textCol.addView(lyric, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Right controls (Prev, Play/Pause, Next, Manual Collapse, Close)
        val rightRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        contentRow.addView(rightRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))

        val prevBtn = createControlButton(android.R.drawable.ic_media_previous, dp(34), dp(34)) {
            listener.onCommand(PlaybackActions.ACTION_CMD_PREV)
        }
        val playBtn = createControlButton(android.R.drawable.ic_media_play, dp(38), dp(38)) {
            listener.onCommand(PlaybackActions.ACTION_CMD_PLAY_PAUSE)
        }
        val nextBtn = createControlButton(android.R.drawable.ic_media_next, dp(34), dp(34)) {
            listener.onCommand(PlaybackActions.ACTION_CMD_NEXT)
        }
        rightRow.addView(prevBtn)
        rightRow.addView(playBtn)
        rightRow.addView(nextBtn)

        // Manual collapse button ("贴边 ▾")
        val collapseBtn = TextView(context).apply {
            text = "贴边 ▾"
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#263342"))
                cornerRadius = dp(4).toFloat()
            }
            setOnClickListener { setCollapsed(true) }
        }
        val collapseParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        }
        rightRow.addView(collapseBtn, collapseParams)

        // Close button (X)
        val closeBtn = createTopCloseButton {
            hide()
            listener.onDismissByUser()
        }
        rightRow.addView(closeBtn, LinearLayout.LayoutParams(dp(26), dp(26)))

        titleView = title
        lyricView = lyric
        progressBar = progress
        playPauseButton = playBtn

        return container
    }

    private fun buildPillView(): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0101720"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
        }

        val pillText = TextView(context).apply {
            text = "🎵 展开 ▴"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
        }
        pill.addView(pillText)

        bindPillTouch(pill)
        return pill
    }

    private fun bindPillTouch(pill: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        pill.setOnTouchListener { _, event ->
            val params = currentLayoutParams ?: return@setOnTouchListener false
            val view = rootView ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pillDragDownY = event.rawY
                    pillDragStartY = params.y
                    pillDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (pillDragDownY - event.rawY).toInt()
                    if (!pillDragging && abs(dy) > touchSlop) {
                        pillDragging = true
                    }
                    if (pillDragging) {
                        params.y = (pillDragStartY + dy).coerceAtLeast(0)
                        runCatching {
                            windowManager.updateViewLayout(view, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (pillDragging) {
                        prefs.edit().putInt(KEY_PILL_Y, params.y).apply()
                    } else {
                        // User simply tapped the pill -> expand back to dock!
                        setCollapsed(false)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (pillDragging) {
                        prefs.edit().putInt(KEY_PILL_Y, params.y).apply()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun createControlButton(iconRes: Int, width: Int, height: Int, onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(iconRes)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(width, height)
            setOnClickListener { onClick() }
        }
    }

    private fun createTopCloseButton(onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_overlay_close)
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.CENTER
            setPadding(0, 0, 0, 0)
            minimumWidth = 0
            minimumHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = when {
            Build.VERSION.SDK_INT >= 26 -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else -> WindowManager.LayoutParams.TYPE_PHONE
        }
        return if (isCollapsed) {
            WindowManager.LayoutParams(
                dp(78),
                dp(36),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = dp(8)
                y = prefs.getInt(KEY_PILL_Y, dp(12)).coerceAtLeast(0)
            }
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dp(44),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                x = 0
                y = 0
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private companion object {
        const val PROGRESS_MAX = 1000
        const val PREFS_OVERLAY = "overlay_ui_state"
        const val KEY_IS_COLLAPSED = "is_collapsed"
        const val KEY_PILL_Y = "pill_y"
    }
}
