package com.skodamusic.app.audio.dsp

class HiFiDspController(
    private val log: (String) -> Unit
) {
    data class Config(
        val enabled: Boolean,
        val mode: HiFiDspMode,
        val version: Int = 0
    )

    @Volatile
    private var config = Config(enabled = false, mode = HiFiDspMode.FIDELITY, version = 0)

    fun snapshot(): Config = config

    fun update(enabled: Boolean, mode: HiFiDspMode, source: String) {
        val normalizedMode = if (enabled && mode == HiFiDspMode.ORIGINAL) HiFiDspMode.FIDELITY else mode
        val nextEnabled = enabled && normalizedMode != HiFiDspMode.ORIGINAL
        val current = config
        if (current.enabled == nextEnabled && current.mode == normalizedMode) {
            return
        }
        val next = Config(
            enabled = nextEnabled,
            mode = normalizedMode,
            version = current.version + 1
        )
        config = next
        log("hifi-dsp config enabled=${next.enabled} mode=${next.mode.code} source=$source version=${next.version}")
    }

    fun bypass(source: String) {
        update(enabled = false, mode = config.mode, source = source)
    }
}
