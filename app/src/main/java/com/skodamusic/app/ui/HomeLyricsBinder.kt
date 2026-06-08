package com.skodamusic.app.ui

import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.widget.ScrollView
import android.widget.TextView
import com.skodamusic.app.R
import com.skodamusic.app.kugou.KugouLyricClient
import com.skodamusic.app.model.LyricLine
import com.skodamusic.app.model.MusicSource
import com.skodamusic.app.playback.SourcePlaybackSnapshot
import com.skodamusic.app.core.concurrent.AppBackgroundExecutor

class HomeLyricsBinder(
    private val lyricClient: KugouLyricClient,
    private val backgroundExecutor: AppBackgroundExecutor,
    private val appendRuntimeLog: (String) -> Unit,
    private val switchToLyricsTab: () -> Unit
) {
    private lateinit var scrollView: ScrollView
    private lateinit var textView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val cache = LinkedHashMap<String, List<LyricLine>>()
    private var currentKey = ""
    private var requestKey: String? = null
    private var lines: List<LyricLine> = emptyList()
    private var showingQueueTab = true
    private var playing = false

    private val idleSwitchRunnable = Runnable {
        if (showingQueueTab && playing && lines.isNotEmpty()) {
            appendRuntimeLog("home lyrics idle-switch")
            switchToLyricsTab()
        }
    }

    fun bind(scrollView: ScrollView, textView: TextView) {
        this.scrollView = scrollView
        this.textView = textView
    }

    fun setQueueTabVisible(visible: Boolean) {
        showingQueueTab = visible
        if (visible) {
            markQueueInteraction()
        } else {
            handler.removeCallbacks(idleSwitchRunnable)
        }
    }

    fun markQueueInteraction() {
        handler.removeCallbacks(idleSwitchRunnable)
        if (showingQueueTab && playing) {
            handler.postDelayed(idleSwitchRunnable, IDLE_SWITCH_DELAY_MS)
        }
    }

    fun updateTrack(snapshot: SourcePlaybackSnapshot?, isPlaying: Boolean, positionMs: Long) {
        playing = isPlaying && snapshot?.hasTrack == true
        if (snapshot == null || !snapshot.hasTrack) {
            currentKey = ""
            requestKey = null
            lines = emptyList()
            renderEmpty()
            handler.removeCallbacks(idleSwitchRunnable)
            return
        }
        val key = buildKey(snapshot)
        if (key != currentKey) {
            currentKey = key
            lines = cache[key] ?: listOf(LyricLine(0L, textView.context.getString(R.string.home_lyrics_placeholder)))
            render(positionMs)
            if (snapshot.source == MusicSource.KUGOU) {
                requestLyrics(snapshot, key)
            }
        } else {
            render(positionMs)
        }
        if (showingQueueTab) {
            markQueueInteraction()
        }
    }

    fun renderPosition(positionMs: Long) {
        render(positionMs)
    }

    fun destroy() {
        handler.removeCallbacks(idleSwitchRunnable)
    }

    private fun requestLyrics(snapshot: SourcePlaybackSnapshot, key: String) {
        if (requestKey == key) {
            return
        }
        requestKey = key
        val keyword = listOf(snapshot.artist, snapshot.title)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
            .ifBlank { snapshot.title }
        backgroundExecutor.execute {
            val fetched = lyricClient.loadLyrics(
                hash = snapshot.playbackRef?.hash.orEmpty(),
                albumAudioId = snapshot.playbackRef?.albumAudioId.orEmpty(),
                keyword = keyword
            )
            textView.post {
                if (requestKey == key) {
                    requestKey = null
                }
                if (currentKey != key) {
                    return@post
                }
                if (fetched.isEmpty()) {
                    appendRuntimeLog("home lyrics empty source=kugou key=${shortId(key)}")
                    return@post
                }
                cache[key] = fetched
                trimCache()
                lines = fetched
                render(0L)
                appendRuntimeLog("home lyrics loaded source=kugou lines=${fetched.size}")
            }
        }
    }

    private fun render(positionMs: Long) {
        if (!this::textView.isInitialized) {
            return
        }
        if (lines.isEmpty()) {
            renderEmpty()
            return
        }
        val activeIndex = findActiveIndex(positionMs)
        val builder = SpannableStringBuilder()
        var activeStart = -1
        var activeEnd = -1
        lines.forEachIndexed { index, line ->
            val start = builder.length
            builder.append(line.text)
            val end = builder.length
            val active = index == activeIndex
            if (active) {
                activeStart = start
                activeEnd = end
            }
            builder.setSpan(
                ForegroundColorSpan(textView.resources.getColor(if (active) R.color.white else R.color.text_secondary)),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.setSpan(AbsoluteSizeSpan(if (active) 20 else 19, true), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (active) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (index < lines.lastIndex) {
                builder.append('\n')
            }
        }
        textView.text = builder
        centerActiveLine(activeStart, activeEnd)
    }

    private fun renderEmpty() {
        if (this::textView.isInitialized) {
            textView.text = textView.context.getString(R.string.home_lyrics_placeholder)
        }
    }

    private fun centerActiveLine(activeStart: Int, activeEnd: Int) {
        textView.post {
            val layout = textView.layout ?: return@post
            val viewportHeight = scrollView.height
            if (viewportHeight <= 0 || activeStart < 0 || activeEnd <= activeStart) {
                return@post
            }
            val safeStart = activeStart.coerceIn(0, layout.text.length)
            val safeEnd = activeEnd.coerceIn(safeStart + 1, layout.text.length)
            val startLine = layout.getLineForOffset(safeStart)
            val endLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(0))
            val lyricTop = layout.getLineTop(startLine)
            val lyricBottom = layout.getLineBottom(endLine)
            val lineHeight = (lyricBottom - lyricTop).coerceAtLeast(dpToPx(20))
            val verticalPadding = (viewportHeight / 2 - lineHeight / 2).coerceAtLeast(0)
            if (textView.paddingTop != verticalPadding || textView.paddingBottom != verticalPadding) {
                textView.setPadding(textView.paddingLeft, verticalPadding, textView.paddingRight, verticalPadding)
                textView.post { centerActiveLine(activeStart, activeEnd) }
                return@post
            }
            val lineCenter = textView.paddingTop + (lyricTop + lyricBottom) / 2
            val maxScroll = (textView.height - scrollView.height).coerceAtLeast(0)
            scrollView.scrollTo(0, (lineCenter - viewportHeight / 2).coerceIn(0, maxScroll))
        }
    }

    private fun findActiveIndex(positionMs: Long): Int {
        var active = 0
        lines.forEachIndexed { index, line ->
            if (positionMs >= line.timeMs) {
                active = index
            }
        }
        return active
    }

    private fun buildKey(snapshot: SourcePlaybackSnapshot): String {
        val playbackRef = snapshot.playbackRef
        return listOf(snapshot.source.name, snapshot.title, snapshot.artist, playbackRef?.hash.orEmpty(), playbackRef?.albumAudioId.orEmpty())
            .joinToString("\u0001")
    }

    private fun trimCache() {
        while (cache.size > CACHE_MAX_TRACKS) {
            val iterator = cache.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * textView.resources.displayMetrics.density).toInt()
    }

    private fun shortId(value: String): String {
        return if (value.length <= 8) value else value.take(4) + "..." + value.takeLast(4)
    }

    companion object {
        private const val IDLE_SWITCH_DELAY_MS = 10_000L
        private const val CACHE_MAX_TRACKS = 12
    }
}
