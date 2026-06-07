package com.skodamusic.app.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import com.skodamusic.app.R
import com.skodamusic.app.model.EmbyTrack
import com.skodamusic.app.model.SourceTrack

class HomeQueuePanelBinder(
    private val context: Context,
    private val rowRenderer: SourceRowRenderer
) {
    sealed class QueueState {
        object LoginRequired : QueueState()
        data class Emby(
            val tracks: List<EmbyTrack>,
            val currentIndex: Int
        ) : QueueState()
        data class Kugou(
            val tracks: List<SourceTrack>,
            val currentTrackId: String?
        ) : QueueState()
        data class Radio(
            val history: List<SourceTrack>,
            val current: SourceTrack?,
            val upcoming: List<SourceTrack>
        ) : QueueState()
    }

    fun render(
        scrollView: ScrollView,
        container: LinearLayout,
        state: QueueState,
        onEmbyTrackClick: (Int) -> Unit,
        onKugouTrackClick: (SourceTrack, List<SourceTrack>) -> Unit,
        onRadioTrackClick: (SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit
    ) {
        container.removeAllViews()
        val currentRowIndex = when (state) {
            QueueState.LoginRequired -> renderEmpty(container, context.getString(R.string.kugou_login_required_state))
            is QueueState.Emby -> renderEmby(container, state, onEmbyTrackClick)
            is QueueState.Kugou -> renderKugou(container, state, onKugouTrackClick, onLike)
            is QueueState.Radio -> renderRadio(container, state, onRadioTrackClick, onLike)
        }
        scrollToRow(scrollView, container, currentRowIndex)
    }

    private fun renderEmpty(container: LinearLayout, text: String): Int {
        container.addView(
            rowRenderer.buildEmptyRow(
                text = text,
                centered = true,
                horizontalPaddingDp = 12,
                verticalPaddingDp = 22
            ),
            wrapContentParams()
        )
        return -1
    }

    private fun renderEmby(
        container: LinearLayout,
        state: QueueState.Emby,
        onTrackClick: (Int) -> Unit
    ): Int {
        if (state.tracks.isEmpty()) {
            return renderEmpty(container, context.getString(R.string.queue_empty))
        }
        container.addView(rowRenderer.buildSectionTitle(context.getString(R.string.label_queue)))
        var currentRowIndex = -1
        state.tracks.forEachIndexed { index, track ->
            val isCurrent = index == state.currentIndex
            val row = rowRenderer.buildEmbyTrackRow(
                track = track,
                isCurrent = isCurrent,
                source = com.skodamusic.app.model.ListSource.QUEUE,
                onClick = { onTrackClick(index) }
            )
            rowRenderer.addRow(container, row, index)
            if (isCurrent) {
                currentRowIndex = container.childCount - 1
            }
        }
        return currentRowIndex
    }

    private fun renderKugou(
        container: LinearLayout,
        state: QueueState.Kugou,
        onTrackClick: (SourceTrack, List<SourceTrack>) -> Unit,
        onLike: (SourceTrack) -> Unit
    ): Int {
        if (state.tracks.isEmpty()) {
            return renderEmpty(container, context.getString(R.string.queue_kugou_empty))
        }
        container.addView(rowRenderer.buildSectionTitle(context.getString(R.string.queue_kugou_title)))
        var currentRowIndex = -1
        state.tracks.forEachIndexed { index, track ->
            val isCurrent = state.currentTrackId == track.sourceTrackId
            val row = rowRenderer.buildKugouTrackRow(
                track = track,
                subtitleText = track.artist.ifBlank { context.getString(R.string.track_artist_server) },
                active = isCurrent,
                onClick = { onTrackClick(track, state.tracks) },
                onLike = { onLike(track) }
            )
            rowRenderer.addRow(container, row, index)
            if (isCurrent) {
                currentRowIndex = container.childCount - 1
            }
        }
        return currentRowIndex
    }

    private fun renderRadio(
        container: LinearLayout,
        state: QueueState.Radio,
        onTrackClick: (SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit
    ): Int {
        val allTracks = state.history + listOfNotNull(state.current) + state.upcoming
        if (allTracks.isEmpty()) {
            return renderEmpty(container, context.getString(R.string.queue_kugou_radio_empty))
        }
        var currentRowIndex = -1
        if (state.history.isNotEmpty()) {
            container.addView(rowRenderer.buildSectionTitle(context.getString(R.string.queue_kugou_radio_history_title)))
            state.history.forEachIndexed { index, track ->
                addRadioRow(container, track, active = false, sectionIndex = index, onTrackClick, onLike)
            }
        }
        state.current?.let { current ->
            container.addView(rowRenderer.buildSectionTitle(context.getString(R.string.queue_kugou_radio_current_title)))
            addRadioRow(container, current, active = true, sectionIndex = 0, onTrackClick, onLike)
            currentRowIndex = container.childCount - 1
        }
        if (state.upcoming.isNotEmpty()) {
            container.addView(rowRenderer.buildSectionTitle(context.getString(R.string.queue_kugou_radio_upcoming_title)))
            state.upcoming.forEachIndexed { index, track ->
                addRadioRow(container, track, active = false, sectionIndex = index, onTrackClick, onLike)
            }
        }
        return currentRowIndex
    }

    private fun addRadioRow(
        container: LinearLayout,
        track: SourceTrack,
        active: Boolean,
        sectionIndex: Int,
        onTrackClick: (SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit
    ) {
        val row = rowRenderer.buildKugouTrackRow(
            track = track,
            subtitleText = track.artist.ifBlank { context.getString(R.string.track_artist_server) },
            active = active,
            onClick = { onTrackClick(track) },
            onLike = { onLike(track) }
        )
        rowRenderer.addRow(container, row, sectionIndex)
    }

    private fun scrollToRow(scrollView: ScrollView, container: LinearLayout, rowIndex: Int) {
        if (rowIndex < 0) {
            return
        }
        scrollView.post {
            val row = container.getChildAt(rowIndex) ?: return@post
            val target = (row.top - (scrollView.height - row.height) / 2).coerceAtLeast(0)
            scrollView.smoothScrollTo(0, target)
        }
    }

    private fun wrapContentParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
}
