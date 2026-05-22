package com.skodamusic.app.audio

import android.media.audiofx.Equalizer

class EqualizerManager(
    private val log: (String) -> Unit
) {
    private data class EqConfig(
        val enabled: Boolean,
        val presetIndex: Int
    )

    private var config = EqConfig(enabled = false, presetIndex = 0)
    private var activeSessionId: Int = -1
    private var fusedSessionId: Int = -1
    private var lastDiscoveredPresetCount: Int = 0
    private var equalizer: Equalizer? = null

    fun lastKnownPresetCount(): Int = lastDiscoveredPresetCount

    fun updateConfig(enabled: Boolean, presetIndex: Int, source: String) {
        val normalizedPreset = presetIndex.coerceAtLeast(0)
        val next = EqConfig(enabled = enabled, presetIndex = normalizedPreset)
        if (next == config) {
            return
        }
        config = next
        log("eq config update enabled=${config.enabled} preset=${config.presetIndex} source=$source")
        if (!config.enabled) {
            releaseInternal("disabled")
            return
        }
        if (activeSessionId > 0) {
            bindSession(activeSessionId, "$source-reapply")
        }
    }

    fun onSessionChanged(sessionId: Int, source: String) {
        if (sessionId <= 0) {
            return
        }
        val changed = sessionId != activeSessionId
        activeSessionId = sessionId
        if (changed) {
            log("eq session changed session=$sessionId source=$source")
        }
        if (!config.enabled) {
            releaseInternal("session-disabled")
            return
        }
        bindSession(sessionId, source)
    }

    fun onPlayerReleased(source: String) {
        releaseInternal("player-released:$source")
        activeSessionId = -1
    }

    private fun bindSession(sessionId: Int, source: String) {
        if (sessionId <= 0 || !config.enabled) {
            return
        }
        if (fusedSessionId == sessionId) {
            log("eq session fused session=$sessionId source=$source")
            return
        }

        releaseInternal("rebind:$source")
        try {
            val created = Equalizer(0, sessionId)
            equalizer = created
            applyPreset(created, source)
            created.enabled = true
            log("eq init ok session=$sessionId source=$source")
        } catch (e: Exception) {
            fusedSessionId = sessionId
            releaseInternal("init-failed:$source")
            log("eq init fail session=$sessionId source=$source type=${e.javaClass.simpleName} msg=${e.message}")
        }
    }

    private fun applyPreset(target: Equalizer, source: String) {
        try {
            val count = target.numberOfPresets.toInt()
            lastDiscoveredPresetCount = count.coerceAtLeast(0)
            if (count <= 0) {
                log("eq apply preset skip reason=no-presets source=$source")
                return
            }
            val preset = config.presetIndex.coerceIn(0, count - 1).toShort()
            target.usePreset(preset)
            log("eq apply preset=$preset source=$source")
        } catch (e: Exception) {
            fusedSessionId = activeSessionId
            throw e
        }
    }

    private fun releaseInternal(source: String) {
        val current = equalizer ?: return
        try {
            current.release()
            log("eq release source=$source")
        } catch (e: Exception) {
            log("eq release fail source=$source type=${e.javaClass.simpleName} msg=${e.message}")
        } finally {
            equalizer = null
        }
    }
}
