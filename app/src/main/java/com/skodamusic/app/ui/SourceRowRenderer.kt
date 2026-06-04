package com.skodamusic.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.skodamusic.app.R
import com.skodamusic.app.model.EmbyTrack
import com.skodamusic.app.model.ListSource
import com.skodamusic.app.model.SourceTrack

class SourceRowRenderer(private val context: Context) {
    private val resources = context.resources

    fun addRow(container: LinearLayout, row: View, index: Int, topMarginDp: Int = 8) {
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        if (index > 0) {
            params.topMargin = dpToPx(topMarginDp)
        }
        container.addView(row, params)
    }

    fun buildEmptyRow(
        text: CharSequence,
        textSize: Float = 16f,
        centered: Boolean = false,
        horizontalPaddingDp: Int = 14,
        verticalPaddingDp: Int = 14
    ): TextView {
        return TextView(context).apply {
            this.text = text
            setBackgroundResource(R.drawable.row_recommend_idle)
            setTextColor(resources.getColor(R.color.text_secondary))
            this.textSize = textSize
            if (centered) {
                gravity = Gravity.CENTER
            }
            setPadding(
                dpToPx(horizontalPaddingDp),
                dpToPx(verticalPaddingDp),
                dpToPx(horizontalPaddingDp),
                dpToPx(verticalPaddingDp)
            )
        }
    }

    fun buildSectionTitle(text: CharSequence): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(resources.getColor(R.color.text_secondary))
            textSize = 16f
            setPadding(dpToPx(4), dpToPx(16), dpToPx(4), dpToPx(8))
        }
    }

    fun buildSourceRow(titleText: CharSequence, subtitleText: CharSequence, active: Boolean): LinearLayout {
        return buildBaseRow(active).apply {
            addView(buildTextBlock(titleText, subtitleText, titleTextSize = 17f, subtitleTextSize = 14f))
        }
    }

    fun buildKugouTrackRow(
        track: SourceTrack,
        subtitleText: CharSequence,
        active: Boolean = false,
        onClick: () -> Unit,
        onLike: () -> Unit
    ): LinearLayout {
        return buildSourceRow(track.title, subtitleText, active = active).apply {
            setOnClickListener { onClick() }
            addView(buildLikeButton(onLike))
        }
    }

    fun buildEmbyTrackRow(
        track: EmbyTrack,
        isCurrent: Boolean,
        source: ListSource,
        onClick: () -> Unit,
        onDelete: (() -> Unit)? = null
    ): LinearLayout {
        val title = if (isCurrent) "\u25B6 ${track.title}" else track.title
        val artist = track.artist.ifBlank { context.getString(R.string.track_artist_server) }
        return buildBaseRow(active = isCurrent).apply {
            addView(
                buildTextBlock(
                    titleText = title,
                    subtitleText = artist,
                    titleTextSize = if (source == ListSource.LIBRARY) 17f else 16f,
                    subtitleTextSize = if (source == ListSource.LIBRARY) 14f else 13f,
                    boldTitle = isCurrent
                )
            )
            if (source == ListSource.LIBRARY && onDelete != null) {
                addView(buildDeleteButton(onDelete))
            }
            setOnClickListener { onClick() }
        }
    }

    fun buildDeleteButton(onClick: () -> Unit): ImageButton {
        return buildIconButton(
            imageRes = android.R.drawable.ic_menu_delete,
            contentDescription = context.getString(R.string.action_delete_source),
            paddingDp = 10,
            onClick = onClick
        )
    }

    fun buildLikeButton(onClick: () -> Unit): ImageButton {
        return buildIconButton(
            imageRes = android.R.drawable.btn_star_big_off,
            contentDescription = context.getString(R.string.action_kugou_like),
            paddingDp = 8,
            onClick = onClick
        )
    }

    private fun buildBaseRow(active: Boolean): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(70)
            setBackgroundResource(if (active) R.drawable.row_recommend_active else R.drawable.row_recommend_idle)
            setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10))
        }
    }

    private fun buildTextBlock(
        titleText: CharSequence,
        subtitleText: CharSequence,
        titleTextSize: Float,
        subtitleTextSize: Float,
        boldTitle: Boolean = false
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            addView(TextView(context).apply {
                text = titleText
                setTextColor(resources.getColor(R.color.text_primary))
                textSize = titleTextSize
                setTypeface(
                    null,
                    if (boldTitle) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
                )
            })
            addView(TextView(context).apply {
                text = subtitleText
                setTextColor(resources.getColor(R.color.text_secondary))
                textSize = subtitleTextSize
            })
        }
    }

    private fun buildIconButton(
        imageRes: Int,
        contentDescription: String,
        paddingDp: Int,
        onClick: () -> Unit
    ): ImageButton {
        return ImageButton(context).apply {
            setImageResource(imageRes)
            setBackgroundResource(R.drawable.button_nav_icon_inactive)
            setColorFilter(resources.getColor(R.color.text_secondary))
            this.contentDescription = contentDescription
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            setPadding(dpToPx(paddingDp), dpToPx(paddingDp), dpToPx(paddingDp), dpToPx(paddingDp))
            isFocusable = false
            isFocusableInTouchMode = false
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                leftMargin = dpToPx(10)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
