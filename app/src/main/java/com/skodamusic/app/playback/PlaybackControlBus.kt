package com.skodamusic.app.playback

import java.lang.ref.WeakReference

object PlaybackControlBus {
    interface Controller {
        fun onPlaybackCommand(action: String): Boolean
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

    fun dispatch(action: String): Boolean {
        val controller = synchronized(lock) {
            controllerRef?.get()
        } ?: return false
        return dispatchTo(controller, action)
    }

    private fun dispatchTo(controller: Controller, action: String): Boolean {
        return try {
            controller.onPlaybackCommand(action)
        } catch (_: Exception) {
            false
        }
    }

}
