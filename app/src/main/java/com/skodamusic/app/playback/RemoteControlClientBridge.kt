package com.skodamusic.app.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RemoteControlClient
import android.os.Build

class RemoteControlClientBridge(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val receiverComponent = ComponentName(context, MediaButtonReceiver::class.java)
    private var remoteControlClient: RemoteControlClient? = null

    fun register() {
        val manager = audioManager ?: return
        if (remoteControlClient != null) {
            return
        }
        try {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(receiverComponent)
            val flags = if (Build.VERSION.SDK_INT >= 23) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 9901, intent, flags)
            val client = RemoteControlClient(pendingIntent)
            client.setTransportControlFlags(
                RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE or
                    RemoteControlClient.FLAG_KEY_MEDIA_PLAY or
                    RemoteControlClient.FLAG_KEY_MEDIA_PAUSE or
                    RemoteControlClient.FLAG_KEY_MEDIA_NEXT or
                    RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
            )
            manager.registerMediaButtonEventReceiver(receiverComponent)
            manager.registerRemoteControlClient(client)
            remoteControlClient = client
        } catch (_: Exception) {
        }
    }

    fun updatePlaybackState(isPlaying: Boolean) {
        val client = remoteControlClient ?: return
        try {
            client.setPlaybackState(if (isPlaying) {
                RemoteControlClient.PLAYSTATE_PLAYING
            } else {
                RemoteControlClient.PLAYSTATE_PAUSED
            })
        } catch (_: Exception) {
        }
    }

    fun updateMetadata(trackTitle: String) {
        val client = remoteControlClient ?: return
        try {
            val editor = client.editMetadata(true)
            editor.putString(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE, trackTitle)
            editor.apply()
        } catch (_: Exception) {
        }
    }

    fun release() {
        val manager = audioManager ?: return
        val client = remoteControlClient
        if (client != null) {
            try {
                manager.unregisterRemoteControlClient(client)
            } catch (_: Exception) {
            }
        }
        try {
            manager.unregisterMediaButtonEventReceiver(receiverComponent)
        } catch (_: Exception) {
        }
        remoteControlClient = null
    }
}
