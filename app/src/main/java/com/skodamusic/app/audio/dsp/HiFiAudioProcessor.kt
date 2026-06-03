package com.skodamusic.app.audio.dsp

import android.os.SystemClock
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class HiFiAudioProcessor(
    private val controller: HiFiDspController,
    private val log: (String) -> Unit
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channelCount = 0
    private var nativeConfiguredSampleRate = 0
    private var nativeConfiguredChannelCount = 0
    private var nativeHandle = 0L
    private var appliedVersion = -1
    private var appliedMode = HiFiDspMode.ORIGINAL
    private var appliedEnabled = false
    private var unsupportedLogged = false
    private var activeLogged = false
    private var bypassLogged = false
    private var nativeUnavailableLogged = false
    private var directBufferBypassLogged = false
    private var nativeConfigureFailLogged = false
    private var lastRuntimeLogAtMs = 0L
    private var lastRuntimeTier = -1
    private var lastRuntimeFlags = -1
    private var lastRuntimeStatus = -1

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supported = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount in 1..2 &&
            inputAudioFormat.sampleRate > 0
        if (!supported) {
            if (!unsupportedLogged) {
                unsupportedLogged = true
                log(
                    "hifi-dsp bypass unsupported format sr=${inputAudioFormat.sampleRate} " +
                        "ch=${inputAudioFormat.channelCount} enc=${inputAudioFormat.encoding}"
                )
            }
            val config = controller.snapshot()
            if (config.enabled) {
                controller.publishRuntimeState(
                    status = HiFiDspController.RuntimeStatus.FAIL_OPEN,
                    mode = config.mode,
                    tier = "unsupported",
                    flags = NativeHiFiDspBridge.FLAG_UNSUPPORTED,
                    reason = "unsupported-format"
                )
            }
            return AudioProcessor.AudioFormat.NOT_SET
        }
        unsupportedLogged = false
        if (sampleRate != inputAudioFormat.sampleRate || channelCount != inputAudioFormat.channelCount) {
            sampleRate = inputAudioFormat.sampleRate
            channelCount = inputAudioFormat.channelCount
            appliedVersion = -1
            nativeConfiguredSampleRate = 0
            nativeConfiguredChannelCount = 0
            directBufferBypassLogged = false
            nativeConfigureFailLogged = false
            log("hifi-dsp format sr=$sampleRate ch=$channelCount enc=${inputAudioFormat.encoding}")
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        if (byteCount <= 0) {
            return
        }
        val output = replaceOutputBuffer(byteCount)
        val config = controller.snapshot()
        val shouldProcess = config.enabled && config.mode != HiFiDspMode.ORIGINAL && sampleRate > 0 && channelCount > 0
        if (!shouldProcess) {
            controller.publishRuntimeState(
                status = HiFiDspController.RuntimeStatus.DISABLED,
                mode = config.mode,
                tier = "none",
                flags = 0,
                reason = "disabled-or-original"
            )
            if (!bypassLogged) {
                bypassLogged = true
                activeLogged = false
                log("hifi-dsp bypass mode=${config.mode.code} enabled=${config.enabled}")
            }
            output.put(inputBuffer)
            output.flip()
            return
        }

        val inputStart = inputBuffer.position()
        if (!ensureNativeReady() || !ensureNativeMode(config)) {
            bypassFrame(inputBuffer, output, inputStart, "native-not-ready")
            return
        }
        if (!inputBuffer.isDirect || !output.isDirect) {
            if (!directBufferBypassLogged) {
                directBufferBypassLogged = true
                log("hifi-dsp native bypass directBuffer=false input=${inputBuffer.isDirect} output=${output.isDirect}")
            }
            bypassFrame(inputBuffer, output, inputStart, "non-direct-buffer")
            return
        }

        val inputView = inputBuffer.slice()
        val outputView = output.slice()
        val packed = NativeHiFiDspBridge.processPcm16(nativeHandle, inputView, outputView, byteCount)
        val status = NativeHiFiDspBridge.status(packed)
        if (status == NativeHiFiDspBridge.STATUS_ERROR) {
            NativeHiFiDspBridge.flush(nativeHandle)
            bypassFrame(inputBuffer, output, inputStart, "native-process-error")
            logRuntimeStatus(config, packed, forced = true)
            return
        }

        inputBuffer.position(inputStart + byteCount)
        output.position(byteCount)
        output.flip()
        logRuntimeStatus(config, packed, forced = false)
    }

    override fun onFlush() {
        if (nativeHandle != 0L) {
            NativeHiFiDspBridge.flush(nativeHandle)
        }
    }

    override fun onReset() {
        if (nativeHandle != 0L) {
            NativeHiFiDspBridge.release(nativeHandle)
            nativeHandle = 0L
        }
        controller.resetRuntimeState(source = "audio_processor_reset")
        sampleRate = 0
        channelCount = 0
        nativeConfiguredSampleRate = 0
        nativeConfiguredChannelCount = 0
        appliedVersion = -1
        activeLogged = false
        bypassLogged = false
        unsupportedLogged = false
        nativeUnavailableLogged = false
        directBufferBypassLogged = false
        nativeConfigureFailLogged = false
        lastRuntimeLogAtMs = 0L
        lastRuntimeTier = -1
        lastRuntimeFlags = -1
        lastRuntimeStatus = -1
    }

    private fun ensureNativeReady(): Boolean {
        if (!NativeHiFiDspBridge.isAvailable()) {
            if (!nativeUnavailableLogged) {
                nativeUnavailableLogged = true
                log("hifi-dsp native unavailable; bypass")
            }
            return false
        }
        if (nativeHandle == 0L) {
            nativeHandle = NativeHiFiDspBridge.create()
            if (nativeHandle == 0L) {
                if (!nativeUnavailableLogged) {
                    nativeUnavailableLogged = true
                    log("hifi-dsp native create fail; bypass")
                }
                return false
            }
        }
        if (nativeConfiguredSampleRate == sampleRate && nativeConfiguredChannelCount == channelCount) {
            return true
        }
        val status = NativeHiFiDspBridge.configure(nativeHandle, sampleRate, channelCount)
        if (status == NativeHiFiDspBridge.STATUS_ERROR) {
            if (!nativeConfigureFailLogged) {
                nativeConfigureFailLogged = true
                log("hifi-dsp native configure fail sr=$sampleRate ch=$channelCount; bypass")
            }
            return false
        }
        nativeConfiguredSampleRate = sampleRate
        nativeConfiguredChannelCount = channelCount
        nativeConfigureFailLogged = false
        log("hifi-dsp native configured sr=$sampleRate ch=$channelCount")
        return true
    }

    private fun ensureNativeMode(config: HiFiDspController.Config): Boolean {
        if (appliedVersion == config.version && appliedMode == config.mode && appliedEnabled == config.enabled) {
            return true
        }
        val status = NativeHiFiDspBridge.setMode(
            nativeHandle,
            enabled = config.enabled,
            mode = config.mode,
            version = config.version
        )
        if (status == NativeHiFiDspBridge.STATUS_ERROR) {
            log("hifi-dsp native setMode fail mode=${config.mode.code} enabled=${config.enabled}; bypass frame")
            return false
        }
        appliedVersion = config.version
        appliedMode = config.mode
        appliedEnabled = config.enabled
        bypassLogged = false
        activeLogged = true
        lastRuntimeTier = -1
        lastRuntimeFlags = -1
        lastRuntimeStatus = -1
        log("hifi-dsp native active mode=${config.mode.code} sr=$sampleRate ch=$channelCount tier=quality")
        return true
    }

    private fun bypassFrame(input: ByteBuffer, output: ByteBuffer, inputStart: Int, reason: String) {
        output.clear()
        input.position(inputStart)
        output.put(input)
        output.flip()
        val config = controller.snapshot()
        controller.publishRuntimeState(
            status = HiFiDspController.RuntimeStatus.FAIL_OPEN,
            mode = config.mode,
            tier = "bypass",
            flags = NativeHiFiDspBridge.FLAG_BYPASS,
            reason = reason
        )
        if (!bypassLogged) {
            bypassLogged = true
            activeLogged = false
            log("hifi-dsp bypass reason=$reason")
        }
    }

    private fun logRuntimeStatus(config: HiFiDspController.Config, packed: Long, forced: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val status = NativeHiFiDspBridge.status(packed)
        val tier = NativeHiFiDspBridge.tier(packed)
        val flags = NativeHiFiDspBridge.flags(packed)
        val costUs = NativeHiFiDspBridge.costUs(packed)
        controller.publishRuntimeState(
            status = resolveRuntimeStatus(status, tier, flags),
            mode = config.mode,
            tier = NativeHiFiDspBridge.tierName(tier),
            flags = flags,
            reason = statusName(status)
        )
        val important = (flags and (
            NativeHiFiDspBridge.FLAG_DEGRADED or
                NativeHiFiDspBridge.FLAG_OVER_BUDGET or
                NativeHiFiDspBridge.FLAG_BYPASS or
                NativeHiFiDspBridge.FLAG_ERROR
            )) != 0
        val changed = status != lastRuntimeStatus || tier != lastRuntimeTier || flags != lastRuntimeFlags
        if (!forced && !changed && !important && now - lastRuntimeLogAtMs < RUNTIME_LOG_INTERVAL_MS) {
            return
        }
        if (!forced && !changed && important && now - lastRuntimeLogAtMs < IMPORTANT_LOG_INTERVAL_MS) {
            return
        }
        lastRuntimeLogAtMs = now
        lastRuntimeStatus = status
        lastRuntimeTier = tier
        lastRuntimeFlags = flags
        log(
            "hifi-dsp native status=${statusName(status)} mode=${config.mode.code} " +
                "tier=${NativeHiFiDspBridge.tierName(tier)} costUs=$costUs flags=${flagsText(flags)}"
        )
    }

    private fun statusName(status: Int): String {
        return when (status) {
            NativeHiFiDspBridge.STATUS_OK -> "ok"
            NativeHiFiDspBridge.STATUS_BYPASS -> "bypass"
            NativeHiFiDspBridge.STATUS_ERROR -> "error"
            else -> "unknown"
        }
    }

    private fun resolveRuntimeStatus(status: Int, tier: Int, flags: Int): HiFiDspController.RuntimeStatus {
        val failed = status == NativeHiFiDspBridge.STATUS_ERROR ||
            status == NativeHiFiDspBridge.STATUS_BYPASS ||
            (flags and (NativeHiFiDspBridge.FLAG_BYPASS or NativeHiFiDspBridge.FLAG_ERROR)) != 0
        if (failed) {
            return HiFiDspController.RuntimeStatus.FAIL_OPEN
        }
        val degraded = (flags and (
            NativeHiFiDspBridge.FLAG_DEGRADED or
                NativeHiFiDspBridge.FLAG_OVER_BUDGET
            )) != 0 ||
            tier == NativeHiFiDspBridge.TIER_BALANCED ||
            tier == NativeHiFiDspBridge.TIER_SAFE
        if (degraded) {
            return HiFiDspController.RuntimeStatus.DEGRADED
        }
        return if ((flags and NativeHiFiDspBridge.FLAG_ACTIVE) != 0) {
            HiFiDspController.RuntimeStatus.ACTIVE
        } else {
            HiFiDspController.RuntimeStatus.UNKNOWN
        }
    }

    private fun flagsText(flags: Int): String {
        val values = ArrayList<String>(5)
        if ((flags and NativeHiFiDspBridge.FLAG_ACTIVE) != 0) values.add("active")
        if ((flags and NativeHiFiDspBridge.FLAG_BYPASS) != 0) values.add("bypass")
        if ((flags and NativeHiFiDspBridge.FLAG_DEGRADED) != 0) values.add("degraded")
        if ((flags and NativeHiFiDspBridge.FLAG_OVER_BUDGET) != 0) values.add("over_budget")
        if ((flags and NativeHiFiDspBridge.FLAG_UNSUPPORTED) != 0) values.add("unsupported")
        if ((flags and NativeHiFiDspBridge.FLAG_ERROR) != 0) values.add("error")
        return if (values.isEmpty()) "none" else values.joinToString(separator = ",")
    }

    companion object {
        private const val RUNTIME_LOG_INTERVAL_MS = 5000L
        private const val IMPORTANT_LOG_INTERVAL_MS = 1500L
    }
}
