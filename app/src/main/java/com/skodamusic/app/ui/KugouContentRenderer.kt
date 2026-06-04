package com.skodamusic.app.ui

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.skodamusic.app.R
import com.skodamusic.app.model.SourcePlaylist
import com.skodamusic.app.model.SourceRadio
import com.skodamusic.app.model.SourceTrack

class KugouContentRenderer(
    private val context: Context,
    private val rowRenderer: SourceRowRenderer
) {
    fun renderRecommendedSongs(
        container: LinearLayout,
        tracks: List<SourceTrack>,
        limit: Int,
        onTrackClick: (index: Int, track: SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit
    ) {
        if (tracks.isEmpty()) {
            container.addView(
                rowRenderer.buildEmptyRow(
                    text = context.getString(R.string.kugou_login_required_state),
                    centered = true,
                    horizontalPaddingDp = 12,
                    verticalPaddingDp = 22
                ),
                wrapContentParams()
            )
            return
        }
        tracks.take(limit).forEachIndexed { index, track ->
            val row = rowRenderer.buildKugouTrackRow(
                track = track,
                subtitleText = buildTrackSubtitle(track),
                onClick = { onTrackClick(index, track) },
                onLike = { onLike(track) }
            )
            rowRenderer.addRow(container, row, index)
        }
    }

    fun renderRadioPage(
        statusView: TextView,
        list: LinearLayout,
        hasSession: Boolean,
        loading: Boolean,
        radios: List<SourceRadio>,
        selectedRadioId: String,
        radioSongs: List<SourceTrack>,
        onRadioClick: (SourceRadio) -> Unit,
        onTrackClick: (index: Int, track: SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit
    ) {
        list.removeAllViews()
        when {
            !hasSession -> {
                statusView.text = context.getString(R.string.kugou_login_required_state)
                return
            }
            loading -> statusView.text = context.getString(R.string.feedback_kugou_radio_loading)
            radios.isEmpty() -> statusView.text = context.getString(R.string.feedback_kugou_radio_empty)
            else -> statusView.text = context.getString(R.string.feedback_kugou_radio_success, radios.size)
        }
        radios.forEachIndexed { index, radio ->
            val row = rowRenderer.buildSourceRow(
                titleText = radio.title,
                subtitleText = radio.subtitle.ifBlank { context.getString(R.string.kugou_radio_title) },
                active = radio.sourceRadioId == selectedRadioId
            )
            row.setOnClickListener { onRadioClick(radio) }
            rowRenderer.addRow(list, row, index)
        }
        if (radioSongs.isNotEmpty()) {
            list.addView(rowRenderer.buildSectionTitle(context.getString(R.string.kugou_radio_songs_title)))
            radioSongs.forEachIndexed { index, track ->
                val row = rowRenderer.buildKugouTrackRow(
                    track = track,
                    subtitleText = track.artist,
                    onClick = { onTrackClick(index, track) },
                    onLike = { onLike(track) }
                )
                rowRenderer.addRow(list, row, index)
            }
        }
    }

    fun renderDiscoverPage(
        statusView: TextView,
        tagList: LinearLayout,
        playlistList: LinearLayout,
        hasSession: Boolean,
        loading: Boolean,
        tags: List<Pair<Int, String>>,
        selectedTagId: Int,
        playlists: List<SourcePlaylist>,
        selectedPlaylistId: String,
        songs: List<SourceTrack>,
        onTagClick: (Int) -> Unit,
        onPlaylistClick: (SourcePlaylist) -> Unit,
        onTrackClick: (index: Int, track: SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit
    ) {
        tagList.removeAllViews()
        playlistList.removeAllViews()
        when {
            !hasSession -> {
                statusView.text = context.getString(R.string.kugou_login_required_state)
                return
            }
            loading -> statusView.text = context.getString(R.string.feedback_kugou_discover_loading)
            tags.isEmpty() -> statusView.text = context.getString(R.string.kugou_discover_category_state)
            else -> statusView.text = context.getString(R.string.feedback_kugou_discover_success, tags.size)
        }
        tags.forEachIndexed { index, tag ->
            val row = rowRenderer.buildSourceRow(
                titleText = tag.second,
                subtitleText = context.getString(R.string.kugou_discover_title),
                active = tag.first == selectedTagId
            )
            row.setOnClickListener { onTagClick(tag.first) }
            rowRenderer.addRow(tagList, row, index)
        }
        playlists.forEachIndexed { index, playlist ->
            val row = rowRenderer.buildSourceRow(
                titleText = playlist.title,
                subtitleText = playlist.subtitle.ifBlank { context.getString(R.string.kugou_discover_title) },
                active = playlist.sourcePlaylistId == selectedPlaylistId
            )
            row.setOnClickListener { onPlaylistClick(playlist) }
            rowRenderer.addRow(playlistList, row, index)
        }
        if (songs.isNotEmpty()) {
            playlistList.addView(rowRenderer.buildSectionTitle(context.getString(R.string.kugou_playlist_songs_title)))
            songs.forEachIndexed { index, track ->
                val row = rowRenderer.buildKugouTrackRow(
                    track = track,
                    subtitleText = track.artist,
                    onClick = { onTrackClick(index, track) },
                    onLike = { onLike(track) }
                )
                rowRenderer.addRow(playlistList, row, index)
            }
        }
    }

    fun renderQueue(
        container: LinearLayout,
        tracks: List<SourceTrack>,
        currentTrackId: String?,
        onTrackClick: (SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit,
        titleRes: Int = R.string.queue_kugou_title,
        emptyRes: Int = R.string.queue_kugou_empty
    ) {
        container.removeAllViews()
        if (tracks.isEmpty()) {
            container.addView(
                rowRenderer.buildEmptyRow(
                    text = context.getString(emptyRes),
                    textSize = 14f
                ),
                wrapContentParams()
            )
            return
        }
        container.addView(rowRenderer.buildSectionTitle(context.getString(titleRes)))
        tracks.forEachIndexed { index, track ->
            val row = rowRenderer.buildKugouTrackRow(
                track = track,
                subtitleText = track.artist,
                active = currentTrackId == track.sourceTrackId,
                onClick = { onTrackClick(track) },
                onLike = { onLike(track) }
            )
            rowRenderer.addRow(container, row, index)
        }
    }

    private fun buildTrackSubtitle(track: SourceTrack): CharSequence {
        return buildString {
            append(track.artist.ifBlank { context.getString(R.string.track_artist_server) })
            if (track.album.isNotBlank()) {
                append(" · ")
                append(track.album)
            }
        }
    }

    private fun wrapContentParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
}
