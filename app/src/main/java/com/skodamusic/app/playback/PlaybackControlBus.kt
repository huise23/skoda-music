package com.skodamusic.app.playback

import java.lang.ref.WeakReference

object PlaybackControlBus {
    data class DispatchResult(
        val handled: Boolean,
        val detail: String
    )

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

    fun dispatch(
        action: String,
        source: String = PlaybackActions.CMD_SOURCE_SERVICE,
        allowToast: Boolean = false
    ): Boolean {
        return dispatchWithResult(action, source, allowToast).handled
    }

    fun dispatchWithResult(
        action: String,
        source: String = PlaybackActions.CMD_SOURCE_SERVICE,
        allowToast: Boolean = false
    ): DispatchResult {
        if (action.isBlank()) {
            return DispatchResult(handled = false, detail = "invalid_action")
        }
        val controller = synchronized(lock) {
            controllerRef?.get()
        } ?: return DispatchResult(handled = false, detail = "controller_unavailable")
        return dispatchTo(controller, action, source, allowToast)
    }

    private fun dispatchTo(
        controller: Controller,
        action: String,
        source: String,
        allowToast: Boolean
    ): DispatchResult {
        return try {
            if (controller.onPlaybackCommand(action, source, allowToast)) {
                DispatchResult(handled = true, detail = "handled")
            } else {
                DispatchResult(handled = false, detail = "controller_rejected")
            }
        } catch (e: Exception) {
            DispatchResult(handled = false, detail = "controller_exception:${e.javaClass.simpleName}")
        }
    }

}
