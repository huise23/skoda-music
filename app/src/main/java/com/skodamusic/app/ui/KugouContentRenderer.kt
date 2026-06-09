package com.skodamusic.app.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.skodamusic.app.R
import com.skodamusic.app.kugou.KugouSceneItem
import com.skodamusic.app.model.SourcePlaylist
import com.skodamusic.app.model.SourceRadio
import com.skodamusic.app.model.SourceTrack

class KugouContentRenderer(
    private val context: Context,
    private val rowRenderer: SourceRowRenderer,
    private val thumbnailLoader: RemoteThumbnailLoader? = null
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
                    text = context.getString(R.string.home_daily_recommend_empty),
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
        loadFailed: Boolean,
        selectedRadioId: String,
        radioSongs: List<SourceTrack>,
        onRetry: () -> Unit,
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
            loadFailed -> {
                statusView.text = context.getString(R.string.feedback_kugou_radio_failed)
                val row = rowRenderer.buildEmptyRow(
                    text = context.getString(R.string.action_kugou_retry_load),
                    centered = true,
                    horizontalPaddingDp = 12,
                    verticalPaddingDp = 22
                )
                row.setOnClickListener { onRetry() }
                list.addView(row, wrapContentParams())
                return
            }
            radios.isEmpty() -> statusView.text = context.getString(R.string.feedback_kugou_radio_empty)
            else -> statusView.text = context.getString(R.string.feedback_kugou_radio_success, radios.size)
        }
        renderGrid(
            container = list,
            items = radios,
            selectedId = selectedRadioId,
            idOf = { it.sourceRadioId },
            titleOf = { it.title },
            subtitleOf = { it.subtitle.ifBlank { context.getString(R.string.kugou_radio_title) } },
            coverOf = { it.coverUrl },
            onClick = onRadioClick
        )
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

    fun renderScenePage(
        statusView: TextView,
        tabList: LinearLayout,
        songList: LinearLayout,
        hasSession: Boolean,
        loading: Boolean,
        loadFailed: Boolean,
        scenes: List<KugouSceneItem>,
        selectedSceneId: String,
        expanded: Boolean,
        songs: List<SourceTrack>,
        onRetry: () -> Unit,
        onToggleExpanded: () -> Unit,
        onSceneClick: (KugouSceneItem) -> Unit,
        onTrackClick: (index: Int, track: SourceTrack) -> Unit,
        onLike: (SourceTrack) -> Unit
    ) {
        tabList.removeAllViews()
        songList.removeAllViews()
        when {
            !hasSession -> {
                statusView.text = context.getString(R.string.kugou_login_required_state)
                return
            }
            loading -> statusView.text = context.getString(R.string.feedback_kugou_scene_loading)
            loadFailed -> {
                statusView.text = context.getString(R.string.feedback_kugou_scene_failed)
                val row = rowRenderer.buildEmptyRow(
                    text = context.getString(R.string.action_kugou_retry_load),
                    centered = true,
                    horizontalPaddingDp = 12,
                    verticalPaddingDp = 22
                )
                row.setOnClickListener { onRetry() }
                tabList.addView(row, wrapContentParams())
                return
            }
            scenes.isEmpty() -> statusView.text = context.getString(R.string.feedback_kugou_scene_empty)
            else -> statusView.text = context.getString(R.string.feedback_kugou_scene_success, scenes.size)
        }
        renderSceneTabs(tabList, scenes, selectedSceneId, expanded, onToggleExpanded, onSceneClick)
        if (songs.isNotEmpty()) {
            songList.addView(rowRenderer.buildSectionTitle(context.getString(R.string.kugou_scene_songs_title)))
            songs.forEachIndexed { index, track ->
                val row = rowRenderer.buildKugouTrackRow(
                    track = track,
                    subtitleText = track.artist,
                    onClick = { onTrackClick(index, track) },
                    onLike = { onLike(track) }
                )
                rowRenderer.addRow(songList, row, index)
            }
        }
    }

    private fun renderSceneTabs(
        container: LinearLayout,
        scenes: List<KugouSceneItem>,
        selectedSceneId: String,
        expanded: Boolean,
        onToggleExpanded: () -> Unit,
        onSceneClick: (KugouSceneItem) -> Unit
    ) {
        val visible = if (expanded) scenes else scenes.take(SCENE_COLLAPSED_COUNT)
        renderGrid(
            container = container,
            items = visible,
            selectedId = selectedSceneId,
            idOf = { it.sceneId },
            titleOf = { it.title },
            subtitleOf = { scene -> scene.subtitle.ifBlank { scene.tag } },
            coverOf = { it.coverUrl },
            onClick = onSceneClick
        )
        if (scenes.size > SCENE_COLLAPSED_COUNT) {
            val action = rowRenderer.buildEmptyRow(
                text = context.getString(if (expanded) R.string.action_scene_collapse else R.string.action_scene_expand),
                centered = true,
                horizontalPaddingDp = 12,
                verticalPaddingDp = 12
            )
            action.setOnClickListener { onToggleExpanded() }
            container.addView(action, wrapContentParams())
        }
    }

    private fun <T> renderGrid(
        container: LinearLayout,
        items: List<T>,
        selectedId: String,
        idOf: (T) -> String,
        titleOf: (T) -> CharSequence,
        subtitleOf: (T) -> CharSequence,
        coverOf: (T) -> String,
        onClick: (T) -> Unit
    ) {
        var row: LinearLayout? = null
        items.forEachIndexed { index, item ->
            if (index % GRID_COLUMNS == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                }
                container.addView(row, wrapContentParams().apply {
                    if (container.childCount > 0) {
                        topMargin = dpToPx(10)
                    }
                })
            }
            val card = buildGridCard(
                title = titleOf(item),
                subtitle = subtitleOf(item),
                coverUrl = coverOf(item),
                active = selectedId.isNotBlank() && selectedId == idOf(item),
                onClick = { onClick(item) }
            )
            row?.addView(card, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index % GRID_COLUMNS > 0) {
                    leftMargin = dpToPx(10)
                }
            })
        }
        if (items.size % GRID_COLUMNS == 1) {
            row?.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply {
                leftMargin = dpToPx(10)
            })
        }
    }

    private fun buildGridCard(
        title: CharSequence,
        subtitle: CharSequence,
        coverUrl: String,
        active: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dpToPx(92)
            setBackgroundResource(if (active) R.drawable.row_recommend_active else R.drawable.row_recommend_idle)
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            setOnClickListener { onClick() }
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(context.resources.getColor(R.color.surface_field))
            }
            addView(image, LinearLayout.LayoutParams(dpToPx(72), dpToPx(72)))
            thumbnailLoader?.load(image, coverUrl, android.R.drawable.ic_menu_gallery)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpToPx(10), 0, 0, 0)
                addView(TextView(context).apply {
                    text = title
                    setTextColor(context.resources.getColor(R.color.text_primary))
                    textSize = 16f
                    maxLines = 2
                })
                addView(TextView(context).apply {
                    text = subtitle
                    setTextColor(context.resources.getColor(R.color.text_secondary))
                    textSize = 13f
                    maxLines = 2
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    fun renderDiscoverPage(
        statusView: TextView,
        tagList: LinearLayout,
        playlistList: LinearLayout,
        hasSession: Boolean,
        loading: Boolean,
        categories: List<DiscoverCategory>,
        loadFailed: Boolean,
        selectedCategoryName: String,
        selectedTagId: Int,
        categoryExpanded: Boolean,
        tagExpanded: Boolean,
        playlists: List<SourcePlaylist>,
        selectedPlaylistId: String,
        songs: List<SourceTrack>,
        onRetry: () -> Unit,
        onToggleCategoryExpanded: () -> Unit,
        onToggleTagExpanded: () -> Unit,
        onCategoryClick: (String) -> Unit,
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
            loadFailed -> {
                statusView.text = context.getString(R.string.feedback_kugou_discover_failed)
                val row = rowRenderer.buildEmptyRow(
                    text = context.getString(R.string.action_kugou_retry_load),
                    centered = true,
                    horizontalPaddingDp = 12,
                    verticalPaddingDp = 22
                )
                row.setOnClickListener { onRetry() }
                tagList.addView(row, wrapContentParams())
                return
            }
            categories.isEmpty() -> statusView.text = context.getString(R.string.kugou_discover_category_state)
            else -> statusView.text = context.getString(R.string.feedback_kugou_discover_success, categories.size)
        }
        renderTextChips(
            container = tagList,
            items = categories,
            collapsedRows = DISCOVER_CATEGORY_COLLAPSED_ROWS,
            columns = DISCOVER_CATEGORY_COLUMNS,
            style = DiscoverChipStyle.PRIMARY,
            expanded = categoryExpanded,
            selected = { it.name == selectedCategoryName },
            titleOf = { it.name },
            onToggleExpanded = onToggleCategoryExpanded,
            onClick = { category -> onCategoryClick(category.name) }
        )
        val selectedCategory = categories.firstOrNull { it.name == selectedCategoryName } ?: categories.firstOrNull()
        val tags = selectedCategory?.tags.orEmpty()
        renderTextChips(
            container = tagList,
            items = tags,
            collapsedRows = DISCOVER_TAG_COLLAPSED_ROWS,
            columns = DISCOVER_TAG_COLUMNS,
            style = DiscoverChipStyle.SECONDARY,
            expanded = tagExpanded,
            selected = { it.tagId == selectedTagId },
            titleOf = { it.name },
            onToggleExpanded = onToggleTagExpanded,
            onClick = { tag -> onTagClick(tag.tagId) }
        )
        renderGrid(
            container = playlistList,
            items = playlists,
            selectedId = selectedPlaylistId,
            idOf = { it.sourcePlaylistId },
            titleOf = { it.title },
            subtitleOf = { it.subtitle.ifBlank { context.getString(R.string.kugou_discover_title) } },
            coverOf = { it.coverUrl },
            onClick = onPlaylistClick
        )
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

    private fun <T> renderTextChips(
        container: LinearLayout,
        items: List<T>,
        collapsedRows: Int,
        columns: Int,
        style: DiscoverChipStyle,
        expanded: Boolean,
        selected: (T) -> Boolean,
        titleOf: (T) -> CharSequence,
        onToggleExpanded: () -> Unit,
        onClick: (T) -> Unit
    ) {
        if (items.isEmpty()) {
            return
        }
        val visibleCount = (collapsedRows * columns).coerceAtLeast(columns)
        val visible = if (expanded) items else items.take(visibleCount)
        var row: LinearLayout? = null
        visible.forEachIndexed { index, item ->
            if (index % columns == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                container.addView(row, wrapContentParams().apply {
                    if (container.childCount > 0) {
                        topMargin = dpToPx(8)
                    }
                })
            }
            val chip = TextView(context).apply {
                text = titleOf(item)
                gravity = Gravity.CENTER
                maxLines = 1
                textSize = if (style == DiscoverChipStyle.PRIMARY) 18f else 17f
                val active = selected(item)
                setTextColor(context.resources.getColor(chipTextColor(style, active)))
                setBackgroundResource(chipBackground(style, active))
                setPadding(
                    dpToPx(if (style == DiscoverChipStyle.PRIMARY) 4 else 7),
                    dpToPx(if (style == DiscoverChipStyle.PRIMARY) 7 else 8),
                    dpToPx(if (style == DiscoverChipStyle.PRIMARY) 4 else 7),
                    dpToPx(if (style == DiscoverChipStyle.PRIMARY) 7 else 8)
                )
                setOnClickListener { onClick(item) }
            }
            row?.addView(chip, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index % columns > 0) {
                    leftMargin = dpToPx(if (style == DiscoverChipStyle.PRIMARY) 4 else 6)
                }
            })
        }
        val remainder = visible.size % columns
        if (remainder > 0) {
            for (index in remainder until columns) {
                row?.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply {
                    leftMargin = dpToPx(if (style == DiscoverChipStyle.PRIMARY) 4 else 6)
                })
            }
        }
        if (items.size > visibleCount) {
            val action = rowRenderer.buildEmptyRow(
                text = context.getString(if (expanded) R.string.action_scene_collapse else R.string.action_scene_expand),
                centered = true,
                horizontalPaddingDp = 12,
                verticalPaddingDp = 10
            )
            action.setOnClickListener { onToggleExpanded() }
            container.addView(action, wrapContentParams().apply {
                topMargin = dpToPx(8)
            })
        }
    }

    private fun chipTextColor(style: DiscoverChipStyle, active: Boolean): Int {
        return when {
            active -> R.color.white
            style == DiscoverChipStyle.PRIMARY -> R.color.text_primary
            else -> R.color.text_secondary
        }
    }

    private fun chipBackground(style: DiscoverChipStyle, active: Boolean): Int {
        return when (style) {
            DiscoverChipStyle.PRIMARY -> if (active) R.drawable.discover_primary_tab_active else R.drawable.discover_primary_tab_idle
            DiscoverChipStyle.SECONDARY -> if (active) R.drawable.discover_secondary_tab_active else R.drawable.discover_secondary_tab_idle
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

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val GRID_COLUMNS = 2
        private const val SCENE_COLLAPSED_COUNT = 6
        private const val DISCOVER_CATEGORY_COLUMNS = 6
        private const val DISCOVER_TAG_COLUMNS = 4
        private const val DISCOVER_CATEGORY_COLLAPSED_ROWS = 1
        private const val DISCOVER_TAG_COLLAPSED_ROWS = 3
    }

    private enum class DiscoverChipStyle {
        PRIMARY,
        SECONDARY
    }
}
