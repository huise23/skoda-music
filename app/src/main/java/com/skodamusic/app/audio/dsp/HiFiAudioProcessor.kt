package com.skodamusic.app.audio.dsp

import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.audio.AudioProcessor
import com.google.android.exoplayer2.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class HiFiAudioProcessor(
    private val controller: HiFiDspController,
    private val log: (String) -> Unit
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channelCount = 0
    private var appliedVersion = -1
    private var appliedMode = HiFiDspMode.ORIGINAL
    private var appliedEnabled = false
    private var preamp = 1.0f
    private var channelFilters: Array<Array<Biquad>> = emptyArray()
    private var unsupportedLogged = false
    private var activeLogged = false
    private var bypassLogged = false

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
            return AudioProcessor.AudioFormat.NOT_SET
        }
        unsupportedLogged = false
        if (sampleRate != inputAudioFormat.sampleRate || channelCount != inputAudioFormat.channelCount) {
            sampleRate = inputAudioFormat.sampleRate
            channelCount = inputAudioFormat.channelCount
            appliedVersion = -1
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
        try {
            ensureMode(config)
            processPcm16(inputBuffer, output)
            output.flip()
        } catch (e: Exception) {
            log("hifi-dsp process fail type=${e.javaClass.simpleName} msg=${e.message}; bypass frame")
            output.clear()
            inputBuffer.position(inputStart)
            output.put(inputBuffer)
            output.flip()
            resetFilters()
        }
    }

    override fun onFlush() {
        resetFilters()
    }

    override fun onReset() {
        resetFilters()
        sampleRate = 0
        channelCount = 0
        appliedVersion = -1
        activeLogged = false
        bypassLogged = false
        unsupportedLogged = false
    }

    private fun ensureMode(config: HiFiDspController.Config) {
        if (appliedVersion == config.version && appliedMode == config.mode && appliedEnabled == config.enabled) {
            return
        }
        val spec = ModeSpec.forMode(config.mode)
        preamp = spec.preamp
        channelFilters = Array(channelCount) {
            spec.filters.map { it.create(sampleRate) }.toTypedArray()
        }
        appliedVersion = config.version
        appliedMode = config.mode
        appliedEnabled = config.enabled
        bypassLogged = false
        if (!activeLogged) {
            activeLogged = true
        }
        log("hifi-dsp active mode=${config.mode.code} sr=$sampleRate ch=$channelCount filters=${spec.filters.size}")
    }

    private fun processPcm16(input: ByteBuffer, output: ByteBuffer) {
        val frameBytes = channelCount * 2
        while (input.remaining() >= frameBytes) {
            for (channel in 0 until channelCount) {
                val lo = input.get().toInt() and 0xFF
                val hi = input.get().toInt()
                val raw = ((hi shl 8) or lo).toShort()
                var sample = raw.toFloat() / 32768f
                sample *= preamp
                val filters = channelFilters.getOrNull(channel).orEmpty()
                for (filter in filters) {
                    sample = filter.process(sample)
                }
                sample = softLimit(sample)
                val out = (sample * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                output.put((out and 0xFF).toByte())
                output.put(((out shr 8) and 0xFF).toByte())
            }
        }
        while (input.hasRemaining()) {
            output.put(input.get())
        }
    }

    private fun resetFilters() {
        channelFilters.forEach { filters -> filters.forEach { it.reset() } }
    }

    private fun softLimit(value: Float): Float {
        val threshold = 0.92f
        val absolute = abs(value)
        if (absolute <= threshold) {
            return value
        }
        val sign = if (value < 0f) -1f else 1f
        val compressed = threshold + (1f - threshold) * (1f - (1f / (1f + (absolute - threshold) * 8f)))
        return (sign * compressed).coerceIn(-0.99f, 0.99f)
    }

    private data class ModeSpec(
        val preamp: Float,
        val filters: List<FilterSpec>
    ) {
        companion object {
            fun forMode(mode: HiFiDspMode): ModeSpec {
                return when (mode) {
                    HiFiDspMode.ORIGINAL -> ModeSpec(1.0f, emptyList())
                    HiFiDspMode.FIDELITY -> ModeSpec(
                        preamp = 0.86f,
                        filters = listOf(
                            FilterSpec.lowShelf(85f, 0.7f, 0.8f),
                            FilterSpec.peak(220f, 0.85f, -1.4f),
                            FilterSpec.peak(2500f, 0.9f, 1.1f),
                            FilterSpec.highShelf(9200f, 0.7f, 0.9f)
                        )
                    )
                    HiFiDspMode.CLARITY -> ModeSpec(
                        preamp = 0.84f,
                        filters = listOf(
                            FilterSpec.peak(260f, 0.9f, -1.2f),
                            FilterSpec.peak(2100f, 0.85f, 1.8f),
                            FilterSpec.highShelf(8800f, 0.7f, 1.2f)
                        )
                    )
                    HiFiDspMode.DYNAMIC -> ModeSpec(
                        preamp = 0.82f,
                        filters = listOf(
                            FilterSpec.lowShelf(75f, 0.75f, 1.6f),
                            FilterSpec.peak(180f, 0.9f, -0.9f),
                            FilterSpec.peak(3600f, 1.0f, 0.7f)
                        )
                    )
                    HiFiDspMode.SOFT -> ModeSpec(
                        preamp = 0.9f,
                        filters = listOf(
                            FilterSpec.lowShelf(100f, 0.75f, 0.3f),
                            FilterSpec.peak(3000f, 0.9f, -1.1f),
                            FilterSpec.highShelf(7200f, 0.7f, -1.5f)
                        )
                    )
                }
            }
        }
    }

    private data class FilterSpec(
        val type: Type,
        val frequencyHz: Float,
        val q: Float,
        val gainDb: Float
    ) {
        enum class Type { PEAK, LOW_SHELF, HIGH_SHELF }

        fun create(sampleRate: Int): Biquad {
            val safeFrequency = frequencyHz.coerceIn(20f, sampleRate * 0.45f)
            val a = 10.0.pow(gainDb / 40.0)
            val omega = 2.0 * PI * safeFrequency / sampleRate
            val sinW = sin(omega)
            val cosW = cos(omega)
            return when (type) {
                Type.PEAK -> {
                    val alpha = sinW / (2.0 * q.coerceAtLeast(0.1f))
                    Biquad.fromRaw(
                        b0 = 1.0 + alpha * a,
                        b1 = -2.0 * cosW,
                        b2 = 1.0 - alpha * a,
                        a0 = 1.0 + alpha / a,
                        a1 = -2.0 * cosW,
                        a2 = 1.0 - alpha / a
                    )
                }
                Type.LOW_SHELF -> shelf(a, sinW, cosW, low = true)
                Type.HIGH_SHELF -> shelf(a, sinW, cosW, low = false)
            }
        }

        private fun shelf(a: Double, sinW: Double, cosW: Double, low: Boolean): Biquad {
            val sqrtA = sqrt(a)
            val alpha = sinW / 2.0 * sqrt(2.0)
            return if (low) {
                Biquad.fromRaw(
                    b0 = a * ((a + 1.0) - (a - 1.0) * cosW + 2.0 * sqrtA * alpha),
                    b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW),
                    b2 = a * ((a + 1.0) - (a - 1.0) * cosW - 2.0 * sqrtA * alpha),
                    a0 = (a + 1.0) + (a - 1.0) * cosW + 2.0 * sqrtA * alpha,
                    a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW),
                    a2 = (a + 1.0) + (a - 1.0) * cosW - 2.0 * sqrtA * alpha
                )
            } else {
                Biquad.fromRaw(
                    b0 = a * ((a + 1.0) + (a - 1.0) * cosW + 2.0 * sqrtA * alpha),
                    b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW),
                    b2 = a * ((a + 1.0) + (a - 1.0) * cosW - 2.0 * sqrtA * alpha),
                    a0 = (a + 1.0) - (a - 1.0) * cosW + 2.0 * sqrtA * alpha,
                    a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW),
                    a2 = (a + 1.0) - (a - 1.0) * cosW - 2.0 * sqrtA * alpha
                )
            }
        }

        companion object {
            fun peak(frequencyHz: Float, q: Float, gainDb: Float): FilterSpec {
                return FilterSpec(Type.PEAK, frequencyHz, q, gainDb)
            }

            fun lowShelf(frequencyHz: Float, q: Float, gainDb: Float): FilterSpec {
                return FilterSpec(Type.LOW_SHELF, frequencyHz, q, gainDb)
            }

            fun highShelf(frequencyHz: Float, q: Float, gainDb: Float): FilterSpec {
                return FilterSpec(Type.HIGH_SHELF, frequencyHz, q, gainDb)
            }
        }
    }

    private class Biquad(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(input: Float): Float {
            val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = input
            y2 = y1
            y1 = output
            return output
        }

        fun reset() {
            x1 = 0f
            x2 = 0f
            y1 = 0f
            y2 = 0f
        }

        companion object {
            fun fromRaw(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double): Biquad {
                val safeA0 = if (abs(a0) < 1.0e-9) 1.0 else a0
                return Biquad(
                    b0 = (b0 / safeA0).toFloat(),
                    b1 = (b1 / safeA0).toFloat(),
                    b2 = (b2 / safeA0).toFloat(),
                    a1 = (a1 / safeA0).toFloat(),
                    a2 = (a2 / safeA0).toFloat()
                )
            }
        }
    }
}
