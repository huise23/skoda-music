package com.skodamusic.app.playback

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.skodamusic.app.MainActivity
import com.skodamusic.app.R
import com.skodamusic.app.overlay.OverlayController

class PlaybackService : Service(), OverlayController.Listener {
    private lateinit var stateStore: PlaybackStateStore
    private lateinit var overlayController: OverlayController
    private lateinit var remoteControlBridge: RemoteControlClientBridge
    private var audioManager: AudioManager? = null
    private var hasAudioFocus: Boolean = false

    private var appInForeground: Boolean = true
    private var snapshot: PlaybackStateStore.Snapshot = PlaybackStateStore.Snapshot(
        trackTitle = "",
        trackId = "",
        isPlaying = false,
        hasActiveTrack = false,
        positionMs = 0L
    )
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (snapshot.isPlaying) {
                    val pauseIntent = Intent(this, PlaybackService::class.java)
                        .setAction(PlaybackActions.ACTION_CMD_PAUSE)
                        .putExtra(
                            PlaybackActions.EXTRA_CMD_SOURCE,
                            PlaybackActions.CMD_SOURCE_AUDIO_FOCUS
                        )
                    startService(pauseIntent)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Head-unit implementations may dispatch transient focus changes during normal playback.
                // Avoid pausing on transient changes to prevent one-second stop regressions.
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = PlaybackStateStore(applicationContext)
        overlayController = OverlayController(applicationContext, this)
        remoteControlBridge = RemoteControlClientBridge(applicationContext)
        audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager
        remoteControlBridge.register()
        snapshot = stateStore.readSnapshot()
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            null,
            PlaybackActions.ACTION_SERVICE_INIT -> {
                updatePresentation()
            }
            PlaybackActions.ACTION_APP_FOREGROUND -> {
                appInForeground = true
                stateStore.setOverlayDismissedByUser(false)
                overlayController.hide()
                updatePresentation()
            }
            PlaybackActions.ACTION_APP_BACKGROUND -> {
                appInForeground = false
                updatePresentation()
            }
            PlaybackActions.ACTION_STATE_UPDATE -> {
                val title = intent.getStringExtra(PlaybackActions.EXTRA_TRACK_TITLE).orEmpty()
                snapshot = PlaybackStateStore.Snapshot(
                    trackTitle = title,
                    trackId = intent.getStringExtra(PlaybackActions.EXTRA_TRACK_ID).orEmpty(),
                    isPlaying = intent.getBooleanExtra(PlaybackActions.EXTRA_IS_PLAYING, false),
                    hasActiveTrack = intent.getBooleanExtra(PlaybackActions.EXTRA_HAS_ACTIVE_TRACK, false),
                    positionMs = intent.getLongExtra(PlaybackActions.EXTRA_POSITION_MS, 0L).coerceAtLeast(0L)
                )
                stateStore.saveSnapshot(snapshot)
                updatePresentation()
            }
            PlaybackActions.ACTION_CMD_PLAY_PAUSE,
            PlaybackActions.ACTION_CMD_PLAY,
            PlaybackActions.ACTION_CMD_PAUSE,
            PlaybackActions.ACTION_CMD_NEXT,
            PlaybackActions.ACTION_CMD_PREV -> {
                dispatchCommand(intent)
                updatePresentation()
            }
            else -> {
                updatePresentation()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        abandonAudioFocusIfHeld()
        overlayController.hide()
        remoteControlBridge.release()
        super.onDestroy()
    }

    override fun onCommand(action: String) {
        val cmdIntent = Intent(this, PlaybackService::class.java)
            .setAction(action)
            .putExtra(PlaybackActions.EXTRA_CMD_SOURCE, PlaybackActions.CMD_SOURCE_OVERLAY)
        startService(cmdIntent)
    }

    override fun onDismissByUser() {
        stateStore.setOverlayDismissedByUser(true)
    }

    private fun updatePresentation() {
        remoteControlBridge.updatePlaybackState(snapshot.isPlaying)
        remoteControlBridge.updateMetadata(snapshot.trackTitle)
        updateAudioFocus(snapshot.isPlaying && snapshot.hasActiveTrack)
        startForeground(NOTIFICATION_ID, buildNotification())
        if (appInForeground || !snapshot.hasActiveTrack) {
            overlayController.hide()
            return
        }
        if (stateStore.isOverlayDismissedByUser()) {
            overlayController.hide()
            return
        }
        if (!overlayController.show(snapshot)) {
            overlayController.hide()
        }
    }

    private fun dispatchCommand(intent: Intent) {
        val action = intent.action.orEmpty()
        if (action.isBlank()) {
            return
        }
        val source = intent.getStringExtra(PlaybackActions.EXTRA_CMD_SOURCE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: PlaybackActions.CMD_SOURCE_SERVICE
        PlaybackControlBus.dispatch(action, source = source, allowToast = false)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            REQ_CONTENT,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingIntentFlags()
        )
        val title = snapshot.trackTitle.ifBlank { getString(R.string.notification_playback_idle) }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.notification_playback_title))
            .setContentText(title)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_media_previous,
                getString(R.string.action_prev),
                commandPendingIntent(REQ_PREV, PlaybackActions.ACTION_CMD_PREV)
            )
            .addAction(
                if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (snapshot.isPlaying) getString(R.string.action_pause) else getString(R.string.action_play),
                commandPendingIntent(REQ_PLAY_PAUSE, PlaybackActions.ACTION_CMD_PLAY_PAUSE)
            )
            .addAction(
                android.R.drawable.ic_media_next,
                getString(R.string.action_next),
                commandPendingIntent(REQ_NEXT, PlaybackActions.ACTION_CMD_NEXT)
            )
            .build()
    }

    private fun commandPendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java)
            .setAction(action)
            .putExtra(PlaybackActions.EXTRA_CMD_SOURCE, PlaybackActions.CMD_SOURCE_NOTIFICATION)
        return PendingIntent.getService(this, requestCode, intent, pendingIntentFlags())
    }

    private fun pendingIntentFlags(): Int {
        val base = PendingIntent.FLAG_UPDATE_CURRENT
        return if (Build.VERSION.SDK_INT >= 23) {
            base or PendingIntent.FLAG_IMMUTABLE
        } else {
            base
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            val channelClass = Class.forName("android.app.NotificationChannel")
            val getChannelMethod = NotificationManager::class.java.getMethod(
                "getNotificationChannel",
                String::class.java
            )
            val existing = getChannelMethod.invoke(manager, NOTIFICATION_CHANNEL_ID)
            if (existing != null) {
                return@runCatching
            }
            val ctor = channelClass.getConstructor(
                String::class.java,
                CharSequence::class.java,
                Int::class.javaPrimitiveType
            )
            val channel = ctor.newInstance(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_playback),
                NotificationManager.IMPORTANCE_LOW
            )
            val createChannelMethod = NotificationManager::class.java.getMethod(
                "createNotificationChannel",
                channelClass
            )
            createChannelMethod.invoke(manager, channel)
        }
    }

    private fun updateAudioFocus(shouldHoldFocus: Boolean) {
        if (shouldHoldFocus) {
            requestAudioFocusIfNeeded()
        } else {
            abandonAudioFocusIfHeld()
        }
    }

    private fun requestAudioFocusIfNeeded() {
        if (hasAudioFocus) {
            return
        }
        val manager = audioManager ?: return
        try {
            val result = manager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocusIfHeld() {
        if (!hasAudioFocus) {
            return
        }
        val manager = audioManager ?: return
        try {
            manager.abandonAudioFocus(audioFocusListener)
        } catch (_: Exception) {
        }
        hasAudioFocus = false
    }

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "playback_service_channel"
        const val NOTIFICATION_ID = 8201
        const val REQ_CONTENT = 1
        const val REQ_PREV = 2
        const val REQ_PLAY_PAUSE = 3
        const val REQ_NEXT = 4
    }
}
