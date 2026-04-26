package com.skodamusic.app.playback

import java.lang.ref.WeakReference
import java.util.ArrayDeque

object PlaybackControlBus {
    interface Controller {
        fun onPlaybackCommand(action: String): Boolean
    }

    private val lock = Any()
    private var controllerRef: WeakReference<Controller>? = null
    private val pendingActions = ArrayDeque<String>()

    fun attach(controller: Controller) {
        val queuedActions: List<String>
        synchronized(lock) {
            controllerRef = WeakReference(controller)
            queuedActions = pendingActions.toList()
            pendingActions.clear()
        }
        if (queuedActions.isNotEmpty()) {
            for (action in queuedActions) {
                dispatchTo(controller, action)
            }
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

    fun dispatch(action: String, queueWhenUnavailable: Boolean = true): Boolean {
        val controller = synchronized(lock) {
            controllerRef?.get()
        }
        if (controller == null) {
            if (queueWhenUnavailable) {
                synchronized(lock) {
                    enqueuePending(action)
                }
            }
            return false
        }
        val handled = dispatchTo(controller, action)
        if (!handled && queueWhenUnavailable) {
            synchronized(lock) {
                enqueuePending(action)
            }
        }
        return handled
    }

    private fun dispatchTo(controller: Controller, action: String): Boolean {
        return try {
            controller.onPlaybackCommand(action)
        } catch (_: Exception) {
            false
        }
    }

    private fun enqueuePending(action: String) {
        if (pendingActions.size >= MAX_PENDING_ACTIONS) {
            pendingActions.removeFirst()
        }
        pendingActions.addLast(action)
    }

    private const val MAX_PENDING_ACTIONS = 12
}
