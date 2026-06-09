package com.skodamusic.app.ui

import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import com.skodamusic.app.R

class HomeTabsBinder(
    private val homeLyricsBinder: HomeLyricsBinder,
    private val currentPositionMs: () -> Long
) {
    private lateinit var recommendPanel: View
    private lateinit var lyricsPanel: View
    private lateinit var recommendButton: TextView
    private lateinit var lyricsButton: TextView

    fun bind(
        recommendPanel: View,
        lyricsPanel: View,
        recommendButton: TextView,
        lyricsButton: TextView
    ) {
        this.recommendPanel = recommendPanel
        this.lyricsPanel = lyricsPanel
        this.recommendButton = recommendButton
        this.lyricsButton = lyricsButton
    }

    fun switch(showRecommend: Boolean) {
        recommendPanel.visibility = if (showRecommend) View.VISIBLE else View.GONE
        lyricsPanel.visibility = if (showRecommend) View.GONE else View.VISIBLE
        recommendButton.setBackgroundResource(if (showRecommend) R.drawable.tab_home_selected else R.drawable.tab_home_unselected)
        lyricsButton.setBackgroundResource(if (showRecommend) R.drawable.tab_home_unselected else R.drawable.tab_home_selected)
        recommendButton.setTextColor(recommendButton.resources.getColor(if (showRecommend) R.color.white else R.color.text_secondary))
        lyricsButton.setTextColor(lyricsButton.resources.getColor(if (showRecommend) R.color.text_secondary else R.color.white))
        recommendButton.setTypeface(null, if (showRecommend) Typeface.BOLD else Typeface.NORMAL)
        lyricsButton.setTypeface(null, if (showRecommend) Typeface.NORMAL else Typeface.BOLD)
        if (!showRecommend) {
            homeLyricsBinder.renderPosition(currentPositionMs().coerceAtLeast(0L))
        }
        homeLyricsBinder.setQueueTabVisible(showRecommend)
    }
}
