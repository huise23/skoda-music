package com.skodamusic.app.ui

import android.content.Context
import android.widget.ImageButton
import com.skodamusic.app.R
import com.skodamusic.app.like.LikeStatusStore
import com.skodamusic.app.model.MusicSource
import com.skodamusic.app.model.SourceTrack

class HomePlaybackActionsBinder(private val context: Context) {
    enum class LikeState {
        DISABLED,
        READY,
        PENDING,
        LIKED,
        FAILED
    }

    private lateinit var likeButton: ImageButton

    fun bind(likeButton: ImageButton) {
        this.likeButton = likeButton
    }

    fun renderLikeState(state: LikeState) {
        if (!this::likeButton.isInitialized) {
            return
        }
        val enabled = state == LikeState.READY || state == LikeState.FAILED
        likeButton.isEnabled = enabled
        likeButton.isSelected = state == LikeState.LIKED || state == LikeState.PENDING
        likeButton.alpha = when (state) {
            LikeState.DISABLED -> 0.45f
            LikeState.PENDING -> 0.72f
            else -> 1f
        }
        likeButton.setImageResource(
            when (state) {
                LikeState.LIKED, LikeState.PENDING -> android.R.drawable.btn_star_big_on
                else -> android.R.drawable.btn_star_big_off
            }
        )
        likeButton.setBackgroundResource(
            when (state) {
                LikeState.LIKED -> R.drawable.button_nav_icon_active
                LikeState.PENDING -> R.drawable.button_control_secondary
                LikeState.FAILED -> R.drawable.button_eq_preset_inactive
                else -> R.drawable.button_nav_icon_inactive
            }
        )
        likeButton.setColorFilter(
            context.resources.getColor(
                when (state) {
                    LikeState.DISABLED -> R.color.text_muted
                    LikeState.FAILED -> R.color.text_secondary
                    else -> R.color.white
                }
            )
        )
    }

    fun renderLikeState(track: SourceTrack?, store: LikeStatusStore) {
        val state = when {
            track == null -> LikeState.DISABLED
            else -> when (remoteStatus(track, store)) {
                LikeStatusStore.REMOTE_PENDING -> LikeState.PENDING
                LikeStatusStore.REMOTE_LIKED -> LikeState.LIKED
                LikeStatusStore.REMOTE_FAILED -> LikeState.FAILED
                else -> LikeState.READY
            }
        }
        renderLikeState(state)
    }

    private fun remoteStatus(track: SourceTrack, store: LikeStatusStore): String {
        val hash = track.playbackRef.hash
        if (hash.isBlank()) {
            return ""
        }
        return store.load()
            .firstOrNull { it.source == MusicSource.KUGOU && it.hash.equals(hash, ignoreCase = true) }
            ?.remoteStatus
            .orEmpty()
    }
}
