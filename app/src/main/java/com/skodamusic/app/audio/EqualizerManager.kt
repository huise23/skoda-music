package com.skodamusic.app.audio

import android.media.audiofx.Equalizer

class EqualizerManager(
    private val log: (String) -> Unit
) {
    enum class EqMode {
        PRESET,
        CUSTOM
    }

    data class EqBandInfo(
        val index: Int,
        val centerFreqHz: Int,
        val minLevelMillibel: Int,
        val maxLevelMillibel: Int,
        val currentLevelMillibel: Int
    )

    data class EqCapabilities(
        val presetNames: List<String>,
        val bands: List<EqBandInfo>
    )

    private data class EqConfig(
        val enabled: Boolean,
        val presetIndex: Int,
        val mode: EqMode,
        val customBandLevels: List<Int>
    )

    private var config = EqConfig(
        enabled = false,
        presetIndex = 0,
        mode = EqMode.PRESET,
        customBandLevels = emptyList()
    )
    private var activeSessionId: Int = -1
    private var fusedSessionId: Int = -1
    private var equalizer: Equalizer? = null

    private var capabilityPresetNames: List<String> = emptyList()
    private var capabilityBands: List<EqBandInfo> = emptyList()

    fun capabilitiesSnapshot(): EqCapabilities {
        return EqCapabilities(
            presetNames = capabilityPresetNames.toList(),
            bands = capabilityBands.toList()
        )
    }

    fun lastKnownPresetCount(): Int = capabilityPresetNames.size

    fun updateConfig(
        enabled: Boolean,
        presetIndex: Int,
        mode: EqMode = EqMode.PRESET,
        customBandLevels: List<Int> = emptyList(),
        source: String
    ) {
        val normalizedPreset = presetIndex.coerceAtLeast(0)
        val next = EqConfig(
            enabled = enabled,
            presetIndex = normalizedPreset,
            mode = mode,
            customBandLevels = customBandLevels.map { it }
        )
        if (next == config) {
            return
        }
        config = next
        log(
            "eq config update enabled=${config.enabled} preset=${config.presetIndex} mode=${config.mode} customSize=${config.customBandLevels.size} source=$source"
        )
        if (!config.enabled) {
            releaseInternal("disabled")
            return
        }
        if (activeSessionId > 0) {
            applyToActiveSession("$source-reapply")
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
        if (changed || equalizer == null) {
            bindSession(sessionId, source)
        } else {
            applyToActiveSession(source)
        }
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
            refreshCapabilities(created, source)
            applyConfigToEqualizer(created, source)
            created.enabled = true
            log("eq init ok session=$sessionId source=$source")
        } catch (e: Exception) {
            fusedSessionId = sessionId
            releaseInternal("init-failed:$source")
            log("eq init fail session=$sessionId source=$source type=${e.javaClass.simpleName} msg=${e.message}")
        }
    }

    private fun applyToActiveSession(source: String) {
        val sessionId = activeSessionId
        if (sessionId <= 0 || !config.enabled) {
            return
        }
        if (fusedSessionId == sessionId) {
            log("eq session fused session=$sessionId source=$source")
            return
        }
        val current = equalizer
        if (current == null) {
            bindSession(sessionId, source)
            return
        }
        try {
            applyConfigToEqualizer(current, source)
            current.enabled = true
            log("eq apply ok session=$sessionId source=$source")
        } catch (e: Exception) {
            fusedSessionId = sessionId
            releaseInternal("apply-failed:$source")
            log("eq apply fail session=$sessionId source=$source type=${e.javaClass.simpleName} msg=${e.message}")
        }
    }

    private fun refreshCapabilities(target: Equalizer, source: String) {
        try {
            val presetCount = target.numberOfPresets.toInt().coerceAtLeast(0)
            val presetNames = mutableListOf<String>()
            for (i in 0 until presetCount) {
                val name = runCatching { target.getPresetName(i.toShort()) }.getOrNull().orEmpty().trim()
                presetNames.add(name)
            }

            val bandCount = target.numberOfBands.toInt().coerceAtLeast(0)
            val levelRange = runCatching { target.bandLevelRange }.getOrNull()
            val minLevel = levelRange?.getOrNull(0)?.toInt() ?: -1500
            val maxLevel = levelRange?.getOrNull(1)?.toInt() ?: 1500
            val bands = mutableListOf<EqBandInfo>()
            for (i in 0 until bandCount) {
                val centerMilliHz = runCatching { target.getCenterFreq(i.toShort()) }.getOrDefault(0)
                bands.add(
                    EqBandInfo(
                        index = i,
                        centerFreqHz = (centerMilliHz / 1000).coerceAtLeast(0),
                        minLevelMillibel = minLevel,
                        maxLevelMillibel = maxLevel,
                        currentLevelMillibel = readBandLevel(target, i, minLevel, maxLevel)
                    )
                )
            }
            capabilityPresetNames = presetNames
            capabilityBands = bands
            log("eq capabilities refresh presets=${presetNames.size} bands=${bands.size} source=$source")
        } catch (e: Exception) {
            capabilityPresetNames = emptyList()
            capabilityBands = emptyList()
            log("eq capabilities refresh fail source=$source type=${e.javaClass.simpleName} msg=${e.message}")
        }
    }

    private fun applyConfigToEqualizer(target: Equalizer, source: String) {
        when (config.mode) {
            EqMode.CUSTOM -> applyCustomBands(target, source)
            EqMode.PRESET -> applyPreset(target, source)
        }
    }

    private fun applyPreset(target: Equalizer, source: String) {
        val count = capabilityPresetNames.size.coerceAtLeast(0)
        if (count <= 0) {
            log("eq apply preset skip reason=no-presets source=$source")
            return
        }
        val preset = config.presetIndex.coerceIn(0, count - 1).toShort()
        target.usePreset(preset)
        syncCurrentBandLevels(target)
        log("eq apply preset=$preset source=$source")
    }

    private fun applyCustomBands(target: Equalizer, source: String) {
        if (capabilityBands.isEmpty()) {
            log("eq apply custom skip reason=no-bands source=$source")
            return
        }
        val levels = config.customBandLevels
        capabilityBands.forEach { band ->
            val raw = levels.getOrNull(band.index) ?: 0
            val clamped = raw.coerceIn(band.minLevelMillibel, band.maxLevelMillibel)
            target.setBandLevel(band.index.toShort(), clamped.toShort())
        }
        syncCurrentBandLevels(target)
        log("eq apply custom bands=${capabilityBands.size} source=$source")
    }

    private fun syncCurrentBandLevels(target: Equalizer) {
        if (capabilityBands.isEmpty()) {
            return
        }
        capabilityBands = capabilityBands.map { band ->
            band.copy(
                currentLevelMillibel = readBandLevel(
                    target = target,
                    bandIndex = band.index,
                    minLevel = band.minLevelMillibel,
                    maxLevel = band.maxLevelMillibel
                )
            )
        }
    }

    private fun readBandLevel(
        target: Equalizer,
        bandIndex: Int,
        minLevel: Int,
        maxLevel: Int
    ): Int {
        return runCatching {
            target.getBandLevel(bandIndex.toShort()).toInt()
        }.getOrDefault(0).coerceIn(minLevel, maxLevel)
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
