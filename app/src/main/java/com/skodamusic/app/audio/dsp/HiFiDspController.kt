package com.skodamusic.app.audio.dsp

class HiFiDspController(
    private val log: (String) -> Unit
) {
    enum class RuntimeStatus {
        UNKNOWN,
        DISABLED,
        ACTIVE,
        DEGRADED,
        FAIL_OPEN
    }

    data class Config(
        val enabled: Boolean,
        val mode: HiFiDspMode,
        val version: Int = 0
    )

    data class RuntimeState(
        val status: RuntimeStatus,
        val mode: HiFiDspMode,
        val tier: String,
        val flags: Int,
        val reason: String
    )

    @Volatile
    private var config = Config(enabled = false, mode = HiFiDspMode.FIDELITY, version = 0)

    @Volatile
    private var runtimeState = RuntimeState(
        status = RuntimeStatus.UNKNOWN,
        mode = HiFiDspMode.ORIGINAL,
        tier = "unknown",
        flags = 0,
        reason = "init"
    )

    fun snapshot(): Config = config

    fun runtimeSnapshot(): RuntimeState = runtimeState

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
        runtimeState = if (next.enabled) {
            RuntimeState(
                status = RuntimeStatus.UNKNOWN,
                mode = next.mode,
                tier = "unknown",
                flags = 0,
                reason = source
            )
        } else {
            RuntimeState(
                status = RuntimeStatus.DISABLED,
                mode = next.mode,
                tier = "none",
                flags = 0,
                reason = source
            )
        }
        log("hifi-dsp config enabled=${next.enabled} mode=${next.mode.code} source=$source version=${next.version}")
    }

    fun publishRuntimeState(
        status: RuntimeStatus,
        mode: HiFiDspMode,
        tier: String,
        flags: Int,
        reason: String
    ) {
        val current = runtimeState
        if (
            current.status == status &&
            current.mode == mode &&
            current.tier == tier &&
            current.flags == flags &&
            current.reason == reason
        ) {
            return
        }
        runtimeState = RuntimeState(
            status = status,
            mode = mode,
            tier = tier,
            flags = flags,
            reason = reason
        )
    }

    fun resetRuntimeState(source: String) {
        runtimeState = RuntimeState(
            status = RuntimeStatus.UNKNOWN,
            mode = config.mode,
            tier = "unknown",
            flags = 0,
            reason = source
        )
    }

    fun bypass(source: String) {
        update(enabled = false, mode = config.mode, source = source)
    }
}
