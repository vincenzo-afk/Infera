package com.chaosvoice.app

/**
 * A complete set of DSP parameters describing a single voice-effect preset.
 *
 * Built-in presets are defined as companion-object constants per
 * 11_DSP_ENGINE.md and 21_SETTINGS_AND_PRESETS.md.
 * Custom presets are deserialized from JSON (23_STORAGE_MODEL.md).
 */
data class Preset(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = true,

    // Stage 1 — Resonant EQ (bandpass biquad)
    val eqFrequency: Float         = 2600f,   // Hz, centre frequency
    val eqQ: Float                 = 5.0f,    // Q / resonance sharpness

    // Stage 2 — Noise Injection
    val noiseMix: Float            = 0.35f,   // 0.0–1.0 white-noise blend

    // Stage 3 — Saturation 1
    val saturation1Gain: Float     = 12.0f,   // tanh distortion input gain

    // Stage 4 — Feedback Delay (22_CONFIGURATION.md defaults)
    val delayTimeMs: Float         = 25f,     // ms
    val delayFeedback: Float       = 0.65f,   // 0.0–0.95

    // Stage 5 — Saturation 2
    val saturation2Gain: Float     = 4.0f,

    // Stage 6 — Ring Modulation
    val ringModHz: Float           = 800f,    // carrier frequency Hz

    // Stage 7 — Bit Crusher
    val bitCrushDepth: Float       = 4f,      // effective bit depth (1–16)

    // Stage 8 — Sample Rate Reduction
    val sampleRateTargetHz: Float  = 8000f,   // effective target sample rate

    // Stage 9 — Pitch Wobble
    val pitchWobbleRateHz: Float   = 4.0f,    // LFO rate Hz (0 = disabled)
    val pitchWobbleDepth: Float    = 0.15f,   // LFO depth (0.0–0.5)

    // Stage 10 — Vocoder
    val vocoderMix: Float          = 0.5f,    // 0.0 = dry, 1.0 = full vocoder

    // Stage 11 — Transient Burst
    val burstProbability: Float    = 0.02f,   // per-buffer probability (2%)

    // Stage 12 — Hard Clip
    val hardClipThreshold: Float   = 0.30f,   // normalised amplitude 0.05–1.0

    // Stage 13 — Output Boost
    val boostPercent: Float        = 150f     // 100–500
) {
    companion object {

        // ── Demon — deep pitch drop, heavy saturation, tight harsh reverb ─
        // Default boost 200% per 21_SETTINGS_AND_PRESETS.md
        val DEMON = Preset(
            id                  = "demon",
            name                = "Demon",
            eqFrequency         = 1500f,
            eqQ                 = 6.0f,
            noiseMix            = 0.20f,
            saturation1Gain     = 20.0f,   // "near max" per 11_DSP_ENGINE.md
            delayTimeMs         = 25f,
            delayFeedback       = 0.75f,   // "tight, harsh decay"
            saturation2Gain     = 8.0f,    // "near max"
            ringModHz           = 500f,    // "moderate (400–600 Hz)"
            bitCrushDepth       = 4f,
            sampleRateTargetHz  = 8000f,
            pitchWobbleRateHz   = 1.5f,    // "deep, slow wobble"
            pitchWobbleDepth    = 0.30f,
            vocoderMix          = 0.30f,
            burstProbability    = 0.02f,
            hardClipThreshold   = 0.30f,
            boostPercent        = 200f
        )

        // ── Robot — strong ring mod + vocoder, no pitch wobble ───────────
        // Default boost 150% per 21_SETTINGS_AND_PRESETS.md
        val ROBOT = Preset(
            id                  = "robot",
            name                = "Robot",
            eqFrequency         = 2600f,
            eqQ                 = 5.0f,
            noiseMix            = 0.10f,
            saturation1Gain     = 3.0f,    // "light distortion"
            delayTimeMs         = 10f,
            delayFeedback       = 0.30f,
            saturation2Gain     = 1.5f,
            ringModHz           = 900f,    // "strong (800–1000 Hz)"
            bitCrushDepth       = 8f,
            sampleRateTargetHz  = 16000f,
            pitchWobbleRateHz   = 0f,      // "pitch wobble: disabled"
            pitchWobbleDepth    = 0f,
            vocoderMix          = 0.80f,   // "strong mix"
            burstProbability    = 0.01f,
            hardClipThreshold   = 0.50f,
            boostPercent        = 150f
        )

        // ── Crushed Radio — heavy bit crush + sample rate reduction + noise ─
        // Default boost 150% per 21_SETTINGS_AND_PRESETS.md
        val CRUSHED_RADIO = Preset(
            id                  = "crushed_radio",
            name                = "Crushed Radio",
            eqFrequency         = 2000f,
            eqQ                 = 3.0f,
            noiseMix            = 0.50f,   // "high (50%+)"
            saturation1Gain     = 2.0f,    // "distortion: minimal"
            delayTimeMs         = 15f,
            delayFeedback       = 0.40f,
            saturation2Gain     = 1.5f,
            ringModHz           = 400f,
            bitCrushDepth       = 3f,      // "heavy (3-bit)"
            sampleRateTargetHz  = 6000f,   // "aggressive (6000 Hz effective)"
            pitchWobbleRateHz   = 0f,
            pitchWobbleDepth    = 0f,
            vocoderMix          = 0.10f,
            burstProbability    = 0.01f,
            hardClipThreshold   = 0.40f,
            boostPercent        = 150f
        )

        // ── Glitch — frequent transient bursts, fast pitch wobble ─────────
        // Default boost 175% per 21_SETTINGS_AND_PRESETS.md
        val GLITCH = Preset(
            id                  = "glitch",
            name                = "Glitch",
            eqFrequency         = 3000f,
            eqQ                 = 4.0f,
            noiseMix            = 0.40f,
            saturation1Gain     = 8.0f,
            delayTimeMs         = 20f,
            delayFeedback       = 0.50f,
            saturation2Gain     = 4.0f,
            ringModHz           = 600f,
            bitCrushDepth       = 3f,      // "heavy bit crusher"
            sampleRateTargetHz  = 8000f,
            pitchWobbleRateHz   = 8.0f,    // "fast, random depth"
            pitchWobbleDepth    = 0.20f,
            vocoderMix          = 0.20f,
            burstProbability    = 0.10f,   // "high probability"
            hardClipThreshold   = 0.25f,
            boostPercent        = 175f
        )

        /** All built-in presets in display order. */
        val ALL_BUILT_IN: List<Preset> = listOf(DEMON, ROBOT, CRUSHED_RADIO, GLITCH)

        /**
         * Returns the built-in preset for [id], falling back to [DEMON] if
         * the id is unknown (handles corrupted preset data — Known Issue #10 /
         * 25_ERROR_HANDLING.md "Preset load failure").
         */
        fun builtInById(id: String): Preset =
            ALL_BUILT_IN.find { it.id == id } ?: DEMON
    }
}
