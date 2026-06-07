package com.skodamusic.app.ui

import android.content.Context
import com.skodamusic.app.observability.PostHogTracker

class KugouLoginRecoveryCoordinator(
    private val context: Context,
    private val showLoginDialog: (reason: String) -> Unit,
    private val appendRuntimeLog: (String) -> Unit
) {
    enum class PendingAction(val eventValue: String) {
        NONE("none"),
        HOME_RECOMMEND("home_recommend"),
        RADIO_PAGE("radio_page"),
        DISCOVER_PAGE("discover_page"),
        LIKE_ACTION("like_action"),
        PLAY_TRACK("play_track")
    }

    private var pendingAction: PendingAction = PendingAction.NONE

    fun requestLogin(reason: String, action: PendingAction) {
        pendingAction = action
        appendRuntimeLog("kugou auth dialog request reason=$reason action=${action.eventValue}")
        capture(
            eventName = "kugou_auth_dialog_shown",
            stage = reason,
            action = action
        )
        showLoginDialog(reason)
    }

    fun consumeLoginSuccessAction(): PendingAction {
        val action = pendingAction
        pendingAction = PendingAction.NONE
        capture(
            eventName = "kugou_auth_recovery_resume",
            stage = "login_success",
            action = action
        )
        appendRuntimeLog("kugou auth recovery resume action=${action.eventValue}")
        return action
    }

    fun clear() {
        pendingAction = PendingAction.NONE
    }

    private fun capture(eventName: String, stage: String, action: PendingAction) {
        PostHogTracker.capture(
            context = context.applicationContext,
            eventName = eventName,
            properties = mapOf(
                "source" to "kugou",
                "feature" to "kugou_auth_recovery",
                "stage" to stage,
                "pending_action" to action.eventValue
            )
        )
    }
}
