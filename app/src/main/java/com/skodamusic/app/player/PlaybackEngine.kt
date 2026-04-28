package com.skodamusic.app.player

import android.content.Context
import android.net.Uri
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.audio.AudioAttributes as ExoAudioAttributes
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.upstream.DataSource

interface PlaybackEngineCallback {
    fun onPrepared()
    fun onCompletion()
    fun onError(code: Int, detail: String)
    fun onBufferingStart()
    fun onBufferingEnd()
}

interface PlaybackEngine {
    fun prepare(source: Uri, callback: PlaybackEngineCallback)
    fun play(): Boolean
    fun pause()
    fun seekTo(positionMs: Long): Boolean
    fun isPlaying(): Boolean
    fun currentPositionMs(): Long
    fun durationMs(): Long
    fun release()
}

class ExoPlaybackEngine(
    private val context: Context,
    private val dataSourceFactory: DataSource.Factory,
    private val minBufferMs: Int = 8_000,
    private val maxBufferMs: Int = 50_000,
    private val playbackBufferMs: Int = 3_000,
    private val rebufferMs: Int = 1_000
) : PlaybackEngine {
    private var exoPlayer: SimpleExoPlayer? = null

    override fun prepare(source: Uri, callback: PlaybackEngineCallback) {
        releaseInternal()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                playbackBufferMs,
                rebufferMs
            )
            .build()
        val player = SimpleExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        exoPlayer = player

        var preparedNotified = false
        var buffering = false

        player.setAudioAttributes(
            ExoAudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        if (!buffering) {
                            buffering = true
                            callback.onBufferingStart()
                        }
                    }
                    Player.STATE_READY -> {
                        if (!preparedNotified) {
                            preparedNotified = true
                            callback.onPrepared()
                        }
                        if (buffering) {
                            buffering = false
                            callback.onBufferingEnd()
                        }
                    }
                    Player.STATE_ENDED -> {
                        callback.onCompletion()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                callback.onError(error.errorCode, error.message ?: error.javaClass.simpleName)
            }
        })
        player.setMediaItem(MediaItem.fromUri(source))
        player.prepare()
    }

    override fun play(): Boolean {
        val player = exoPlayer ?: return false
        return try {
            player.playWhenReady = true
            player.play()
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun pause() {
        try {
            exoPlayer?.pause()
        } catch (_: Exception) {
        }
    }

    override fun seekTo(positionMs: Long): Boolean {
        val player = exoPlayer ?: return false
        return try {
            player.seekTo(positionMs.coerceAtLeast(0L))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun isPlaying(): Boolean {
        return try {
            exoPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    override fun currentPositionMs(): Long {
        return try {
            exoPlayer?.currentPosition ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    override fun durationMs(): Long {
        return try {
            val value = exoPlayer?.duration ?: -1L
            if (value <= 0L) -1L else value
        } catch (_: Exception) {
            -1L
        }
    }

    override fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        val player = exoPlayer ?: return
        try {
            player.stop()
        } catch (_: Exception) {
        }
        try {
            player.release()
        } catch (_: Exception) {
        }
        exoPlayer = null
    }
}
