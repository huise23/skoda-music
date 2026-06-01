package com.skodamusic.app.audio.dsp

enum class HiFiDspMode(val code: String) {
    ORIGINAL("original"),
    FIDELITY("fidelity"),
    CLARITY("clarity"),
    DYNAMIC("dynamic"),
    SOFT("soft");

    companion object {
        fun fromCode(raw: String?, fallback: HiFiDspMode = FIDELITY): HiFiDspMode {
            val normalized = raw?.trim().orEmpty()
            if (normalized.isEmpty()) {
                return fallback
            }
            return values().firstOrNull { it.code == normalized || it.name == normalized } ?: fallback
        }
    }
}
