package com.skodamusic.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
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
    private var artistView: TextView? = null
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
        artistView?.text = if (snapshot.artist.isNotBlank()) snapshot.artist else "车载高保真"
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
        root.addView(pill, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START))

        rootView = root
        dockView = dock
        pillView = pill
    }

    private fun buildDockView(): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#550A0F1D"), Color.parseColor("#F50A0F1D"))
            )
        }

        // Top accent progress line (2dp thin line)
        val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = PROGRESS_MAX
            progress = 0
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
            }
            val fgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
            }
            val clipFg = ClipDrawable(fgDrawable, Gravity.START, ClipDrawable.HORIZONTAL)
            val progressLayer = LayerDrawable(arrayOf(bgDrawable, clipFg)).apply {
                setId(0, android.R.id.background)
                setId(1, android.R.id.progress)
            }
            progressDrawable = progressLayer
        }
        container.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(2)))

        // Dock content row (66dp height - slim, compact, non-intrusive on map navigation)
        val contentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(8), 0)
        }
        container.addView(contentRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(66)))

        // Leftmost: Manual collapse button ("收起 ▾") on driver's side
        val collapseBtn = TextView(context).apply {
            text = "收起 ▾"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33334155"))
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener { setCollapsed(true) }
        }
        val collapseParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = dp(2)
            rightMargin = dp(8)
        }
        contentRow.addView(collapseBtn, collapseParams)

        // Left section: Track title & Artist (musicIcon removed)
        val leftSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(6), 0)
            isClickable = true
            setOnClickListener { listener.onTrackTitleClicked() }
        }
        contentRow.addView(leftSection, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.0f))

        val title = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = "未加载曲目"
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        leftSection.addView(title, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val artist = TextView(context).apply {
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = "车载高保真"
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
        }
        leftSection.addView(artist, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(2)
        })

        // Center section: Floating lyric placed in the middle
        val centerLyricSection = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(6), 0, dp(6), 0)
            isClickable = true
            setOnClickListener { listener.onTrackTitleClicked() }
        }
        contentRow.addView(centerLyricSection, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.8f))

        val lyric = TextView(context).apply {
            setTextColor(Color.parseColor("#4ADE80"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            text = "Skoda Music 随车播控"
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        centerLyricSection.addView(lyric, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Right controls (Prev, Play/Pause, Next, Close) - compact & finger-friendly
        val rightRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        contentRow.addView(rightRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))

        val prevBtn = createControlButton(android.R.drawable.ic_media_previous, dp(46), dp(46)) {
            listener.onCommand(PlaybackActions.ACTION_CMD_PREV)
        }
        val playBtn = createControlButton(android.R.drawable.ic_media_play, dp(54), dp(54)) {
            listener.onCommand(PlaybackActions.ACTION_CMD_PLAY_PAUSE)
        }
        val nextBtn = createControlButton(android.R.drawable.ic_media_next, dp(46), dp(46)) {
            listener.onCommand(PlaybackActions.ACTION_CMD_NEXT)
        }
        rightRow.addView(prevBtn)
        rightRow.addView(playBtn)
        rightRow.addView(nextBtn)

        // Close button (X)
        val closeBtn = createTopCloseButton {
            hide()
            listener.onDismissByUser()
        }
        rightRow.addView(closeBtn, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            leftMargin = dp(6)
        })

        titleView = title
        artistView = artist
        lyricView = lyric
        progressBar = progress
        playPauseButton = playBtn

        return container
    }

    private fun buildPillView(): View {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60A0F1D"))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
        }

        val pillText = TextView(context).apply {
            text = "🎵 展开 ▴"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
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
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(6), dp(6), dp(6), dp(6))
            layoutParams = LinearLayout.LayoutParams(width, height)
            setOnClickListener { onClick() }
        }
    }

    private fun createTopCloseButton(onClick: () -> Unit): ImageButton {
        return ImageButton(context).apply {
            setImageResource(R.drawable.ic_overlay_close)
            setColorFilter(Color.parseColor("#94A3B8"))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#26FFFFFF"))
            }
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(7), dp(7), dp(7), dp(7))
            minimumWidth = 0
            minimumHeight = 0
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
                dp(112),
                dp(48),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = dp(8)
                y = prefs.getInt(KEY_PILL_Y, dp(14)).coerceAtLeast(0)
            }
        } else {
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dp(68),
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
