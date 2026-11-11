package com.skodamusic.app.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) {
            return
        }
        val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent ?: return
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0) {
            return
        }

        // Intercept hardware voice / search buttons if routed via MEDIA_BUTTON
        if (event.keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
            event.keyCode == KeyEvent.KEYCODE_SEARCH ||
            event.keyCode == KeyEvent.KEYCODE_ASSIST) {
            com.skodamusic.app.bridge.CarVoiceFeedbackManager.onVoiceTriggered(context, "MediaButton(${event.keyCode})")
            if (isOrderedBroadcast) {
                abortBroadcast()
            }
            return
        }

        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlaybackActions.ACTION_CMD_PREV
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlaybackActions.ACTION_CMD_NEXT
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlaybackActions.ACTION_CMD_PLAY_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlaybackActions.ACTION_CMD_PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlaybackActions.ACTION_CMD_PAUSE
            else -> null
        } ?: return

        val commandIntent = Intent(context, PlaybackService::class.java)
            .setAction(action)
            .putExtra(PlaybackActions.EXTRA_CMD_SOURCE, PlaybackActions.CMD_SOURCE_MEDIA_BUTTON)
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(commandIntent)
        } else {
            context.startService(commandIntent)
        }
        if (isOrderedBroadcast) {
            abortBroadcast()
        }
    }
}
