package com.skodamusic.app.playback

import java.lang.ref.WeakReference

object PlaybackControlBus {
    interface Controller {
        fun onPlaybackCommand(action: String, source: String, allowToast: Boolean): Boolean
    }

    private val lock = Any()
    private var controllerRef: WeakReference<Controller>? = null

    fun attach(controller: Controller) {
        synchronized(lock) {
            controllerRef = WeakReference(controller)
        }
    }

    fun detach(controller: Controller) {
        synchronized(lock) {
            val active = controllerRef?.get()
            if (active === controller) {
                controllerRef = null
            }
        }
    }

    fun dispatch(action: String, source: String = "service cmd", allowToast: Boolean = false): Boolean {
        val controller = synchronized(lock) {
            controllerRef?.get()
        } ?: return false
        return dispatchTo(controller, action, source, allowToast)
    }

    private fun dispatchTo(
        controller: Controller,
        action: String,
        source: String,
        allowToast: Boolean
    ): Boolean {
        return try {
            controller.onPlaybackCommand(action, source, allowToast)
        } catch (_: Exception) {
            false
        }
    }

}
