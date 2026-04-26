package com.skodamusic.app.playback

object PlaybackActions {
    const val ACTION_SERVICE_INIT = "com.skodamusic.app.action.SERVICE_INIT"
    const val ACTION_APP_FOREGROUND = "com.skodamusic.app.action.APP_FOREGROUND"
    const val ACTION_APP_BACKGROUND = "com.skodamusic.app.action.APP_BACKGROUND"
    const val ACTION_STATE_UPDATE = "com.skodamusic.app.action.STATE_UPDATE"

    const val ACTION_CMD_PLAY_PAUSE = "com.skodamusic.app.action.CMD_PLAY_PAUSE"
    const val ACTION_CMD_PLAY = "com.skodamusic.app.action.CMD_PLAY"
    const val ACTION_CMD_PAUSE = "com.skodamusic.app.action.CMD_PAUSE"
    const val ACTION_CMD_NEXT = "com.skodamusic.app.action.CMD_NEXT"
    const val ACTION_CMD_PREV = "com.skodamusic.app.action.CMD_PREV"
    const val EXTRA_CMD_SOURCE = "extra_cmd_source"
    const val CMD_SOURCE_SERVICE = "service"
    const val CMD_SOURCE_NOTIFICATION = "notification"
    const val CMD_SOURCE_OVERLAY = "overlay"
    const val CMD_SOURCE_MEDIA_BUTTON = "media_button"
    const val CMD_SOURCE_AUDIO_FOCUS = "audio_focus"

    const val EXTRA_TRACK_TITLE = "extra_track_title"
    const val EXTRA_TRACK_ID = "extra_track_id"
    const val EXTRA_IS_PLAYING = "extra_is_playing"
    const val EXTRA_HAS_ACTIVE_TRACK = "extra_has_active_track"
    const val EXTRA_POSITION_MS = "extra_position_ms"
}
