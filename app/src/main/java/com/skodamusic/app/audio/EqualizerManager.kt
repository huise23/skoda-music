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

    data class EqBandApplyResult(
        val index: Int,
        val requestedLevelMillibel: Int,
        val appliedLevelMillibel: Int?,
        val success: Boolean,
        val errorType: String? = null,
        val errorMessage: String? = null
    )

    data class EqApplyResult(
        val enabled: Boolean,
        val attempted: Boolean,
        val activeSessionId: Int,
        val successBands: List<EqBandApplyResult> = emptyList(),
        val failedBands: List<EqBandApplyResult> = emptyList(),
        val fatalErrorType: String? = null,
        val fatalErrorMessage: String? = null
    ) {
        val hasFailures: Boolean
            get() = fatalErrorType != null || failedBands.isNotEmpty()
    }

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
    private var lastApplyResult = EqApplyResult(enabled = false, attempted = false, activeSessionId = -1)

    fun capabilitiesSnapshot(): EqCapabilities {
        return EqCapabilities(
            presetNames = capabilityPresetNames.toList(),
            bands = capabilityBands.toList()
        )
    }

    fun lastKnownPresetCount(): Int = capabilityPresetNames.size

    fun lastApplyResult(): EqApplyResult = lastApplyResult

    fun updateConfig(
        enabled: Boolean,
        presetIndex: Int,
        mode: EqMode = EqMode.PRESET,
        customBandLevels: List<Int> = emptyList(),
        source: String
    ): EqApplyResult {
        val normalizedPreset = presetIndex.coerceAtLeast(0)
        val next = EqConfig(
            enabled = enabled,
            presetIndex = normalizedPreset,
            mode = mode,
            customBandLevels = customBandLevels.map { it }
        )
        if (next == config) {
            if (config.enabled && activeSessionId > 0) {
                lastApplyResult = applyToActiveSession("$source-retry")
                return lastApplyResult
            }
            return lastApplyResult
        }
        config = next
        log(
            "eq config update enabled=${config.enabled} preset=${config.presetIndex} mode=${config.mode} customSize=${config.customBandLevels.size} source=$source"
        )
        if (!config.enabled) {
            releaseInternal("disabled")
            lastApplyResult = EqApplyResult(enabled = false, attempted = false, activeSessionId = activeSessionId)
            return lastApplyResult
        }
        lastApplyResult = if (activeSessionId > 0) {
            applyToActiveSession("$source-reapply")
        } else {
            EqApplyResult(enabled = true, attempted = false, activeSessionId = activeSessionId)
        }
        return lastApplyResult
    }

    fun onSessionChanged(sessionId: Int, source: String): EqApplyResult {
        if (sessionId <= 0) {
            lastApplyResult = EqApplyResult(enabled = config.enabled, attempted = false, activeSessionId = sessionId)
            return lastApplyResult
        }
        val changed = sessionId != activeSessionId
        activeSessionId = sessionId
        if (changed) {
            log("eq session changed session=$sessionId source=$source")
        }
        if (!config.enabled) {
            releaseInternal("session-disabled")
            lastApplyResult = EqApplyResult(enabled = false, attempted = false, activeSessionId = sessionId)
            return lastApplyResult
        }
        lastApplyResult = if (changed || equalizer == null) {
            bindSession(sessionId, source)
        } else {
            applyToActiveSession(source)
        }
        return lastApplyResult
    }

    fun onPlayerReleased(source: String) {
        releaseInternal("player-released:$source")
        activeSessionId = -1
        lastApplyResult = EqApplyResult(enabled = config.enabled, attempted = false, activeSessionId = -1)
    }

    private fun bindSession(sessionId: Int, source: String): EqApplyResult {
        if (sessionId <= 0 || !config.enabled) {
            return EqApplyResult(enabled = config.enabled, attempted = false, activeSessionId = sessionId)
        }
        if (fusedSessionId == sessionId) {
            log("eq session fused session=$sessionId source=$source")
            return EqApplyResult(
                enabled = true,
                attempted = false,
                activeSessionId = sessionId,
                fatalErrorType = "FUSED_SESSION",
                fatalErrorMessage = "session fused"
            )
        }

        releaseInternal("rebind:$source")
        return try {
            val created = Equalizer(0, sessionId)
            equalizer = created
            refreshCapabilities(created, source)
            val result = applyConfigToEqualizer(created, source)
            created.enabled = true
            log("eq init ok session=$sessionId source=$source")
            result
        } catch (e: Exception) {
            fusedSessionId = sessionId
            releaseInternal("init-failed:$source")
            log("eq init fail session=$sessionId source=$source type=${e.javaClass.simpleName} msg=${e.message}")
            EqApplyResult(
                enabled = true,
                attempted = true,
                activeSessionId = sessionId,
                fatalErrorType = e.javaClass.simpleName,
                fatalErrorMessage = e.message
            )
        }
    }

    private fun applyToActiveSession(source: String): EqApplyResult {
        val sessionId = activeSessionId
        if (sessionId <= 0 || !config.enabled) {
            return EqApplyResult(enabled = config.enabled, attempted = false, activeSessionId = sessionId)
        }
        if (fusedSessionId == sessionId) {
            log("eq session fused session=$sessionId source=$source")
            return EqApplyResult(
                enabled = true,
                attempted = false,
                activeSessionId = sessionId,
                fatalErrorType = "FUSED_SESSION",
                fatalErrorMessage = "session fused"
            )
        }
        val current = equalizer
        if (current == null) {
            return bindSession(sessionId, source)
        }
        return try {
            val result = applyConfigToEqualizer(current, source)
            current.enabled = true
            log("eq apply ok session=$sessionId source=$source success=${result.successBands.size} fail=${result.failedBands.size}")
            result
        } catch (e: Exception) {
            fusedSessionId = sessionId
            releaseInternal("apply-failed:$source")
            log("eq apply fail session=$sessionId source=$source type=${e.javaClass.simpleName} msg=${e.message}")
            EqApplyResult(
                enabled = true,
                attempted = true,
                activeSessionId = sessionId,
                fatalErrorType = e.javaClass.simpleName,
                fatalErrorMessage = e.message
            )
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
            val minLevel = levelRange?.getOrNull(0)?.toInt() ?: DEFAULT_MIN_LEVEL_MB
            val maxLevel = levelRange?.getOrNull(1)?.toInt() ?: DEFAULT_MAX_LEVEL_MB
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

    private fun applyConfigToEqualizer(target: Equalizer, source: String): EqApplyResult {
        val levels = config.customBandLevels
        if (levels.isEmpty()) {
            log("eq apply fixed10 skip reason=no-levels source=$source")
            return EqApplyResult(enabled = true, attempted = false, activeSessionId = activeSessionId)
        }
        return applyFixedBands(target, levels, source)
    }

    private fun applyFixedBands(target: Equalizer, levels: List<Int>, source: String): EqApplyResult {
        val success = mutableListOf<EqBandApplyResult>()
        val failed = mutableListOf<EqBandApplyResult>()
        for (index in 0 until FIXED_BAND_COUNT) {
            val requested = (levels.getOrNull(index) ?: 0).coerceIn(DEFAULT_MIN_LEVEL_MB, DEFAULT_MAX_LEVEL_MB)
            try {
                target.setBandLevel(index.toShort(), requested.toShort())
                val applied = readBandLevel(target, index, DEFAULT_MIN_LEVEL_MB, DEFAULT_MAX_LEVEL_MB)
                success.add(
                    EqBandApplyResult(
                        index = index,
                        requestedLevelMillibel = requested,
                        appliedLevelMillibel = applied,
                        success = true
                    )
                )
            } catch (e: Exception) {
                failed.add(
                    EqBandApplyResult(
                        index = index,
                        requestedLevelMillibel = requested,
                        appliedLevelMillibel = null,
                        success = false,
                        errorType = e.javaClass.simpleName,
                        errorMessage = e.message
                    )
                )
                log("eq apply fixed10 band=$index fail source=$source type=${e.javaClass.simpleName} msg=${e.message}")
            }
        }
        log("eq apply fixed10 success=${success.size} fail=${failed.size} source=$source")
        return EqApplyResult(
            enabled = true,
            attempted = true,
            activeSessionId = activeSessionId,
            successBands = success,
            failedBands = failed
        )
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

    private companion object {
        const val FIXED_BAND_COUNT = 10
        const val DEFAULT_MIN_LEVEL_MB = -1200
        const val DEFAULT_MAX_LEVEL_MB = 1200
    }
}
