package com.chaosvoice.app

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*
import kotlin.random.Random

/**
 * ChaosVoice DSP engine — the full 14-stage effect chain.
 *
 * All audio buffers are pre-allocated at construction; no heap allocation occurs
 * in [process] to keep the real-time audio thread allocation-free (13_THREADING_MODEL.md).
 *
 * DSP parameters are wrapped in [AtomicReference] / [AtomicInteger] so the UI thread
 * can call [setParameter] safely while the audio thread calls [process] (13_THREADING_MODEL.md).
 *
 * Kotlin is the source of truth for live Android processing; Dart's EffectEngine mirrors
 * the same math for iOS and non-realtime preview only (ADR-001).
 *
 * Processing order (11_DSP_ENGINE.md):
 *  1. Resonant EQ (bandpass biquad)
 *  2. Noise Injection
 *  3. Saturation 1 (tanh, high gain)
 *  4. Feedback Delay
 *  5. Saturation 2 (tanh, moderate gain)
 *  6. Ring Modulation
 *  7. Bit Crusher
 *  8. Sample Rate Reduction
 *  9. Pitch Drop / Wobble
 * 10. Vocoder / Robotic Layer
 * 11. Transient Burst (Scream/Screech)
 * 12. Hard Clip
 * 13. Output Gain / Boost
 * 14. Limiter (soft-knee) — MUST be final stage; see Known Issue #7
 */
class ChaosDSP(private val sampleRate: Int = SAMPLE_RATE) {

    // ─────────────────────────────────────────────────────────────────────────
    // Companion — constants from 22_CONFIGURATION.md
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "[ChaosVoice][DSP]"

        const val SAMPLE_RATE              = 48_000
        const val BUFFER_SIZE_SAMPLES      = 1_920        // ~40 ms at 48 kHz
        const val BOOST_MIN_PERCENT        = 100f
        const val BOOST_MAX_PERCENT        = 500f
        const val BOOST_DEFAULT_PERCENT    = 150f
        const val BOOST_WARNING_THRESHOLD  = 300f
        const val LIMITER_KNEE_THRESHOLD   = 0.8f
        const val LIMITER_RATIO            = 4.0f
        const val ECHO_PREWARM_MS          = 500          // Known Issue #4 fix
        const val BURST_DUR_MIN_MS         = 50
        const val BURST_DUR_MAX_MS         = 150

        // Bug 7 fix: IIR-style delay smoothing coefficient (1-pole smoother).
        // At 0.002f convergence per sample, a 500 ms parameter step (24 000 samples
        // at 48 kHz) converges within ~10 ms — fast enough to feel immediate,
        // slow enough to produce zero audible click.
        private const val DELAY_SMOOTH_RATE = 0.002f
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stage enum — used as key for setParameter() calls from UI / MethodChannel
    // ─────────────────────────────────────────────────────────────────────────
    enum class Stage(val key: String) {
        EQ_FREQUENCY("eqFrequency"),
        EQ_Q("eqQ"),
        NOISE_MIX("noiseMix"),
        SAT1_GAIN("saturation1Gain"),
        DELAY_TIME_MS("delayTimeMs"),
        DELAY_FEEDBACK("delayFeedback"),
        SAT2_GAIN("saturation2Gain"),
        RING_MOD_HZ("ringModHz"),
        BIT_CRUSH_DEPTH("bitCrushDepth"),
        SAMPLE_RATE_TARGET_HZ("sampleRateTargetHz"),
        PITCH_WOBBLE_RATE_HZ("pitchWobbleRateHz"),
        PITCH_WOBBLE_DEPTH("pitchWobbleDepth"),
        VOCODER_MIX("vocoderMix"),
        BURST_PROBABILITY("burstProbability"),
        HARD_CLIP_THRESHOLD("hardClipThreshold"),
        BOOST_PERCENT("boostPercent"),
        LIMITER_ENABLED("limiterEnabled");

        companion object {
            /** Resolve a string key (from MethodChannel) to a Stage enum. */
            fun fromKey(key: String): Stage? = values().find { it.key == key }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Live parameters — AtomicReference writes from UI thread, reads on audio thread
    // ─────────────────────────────────────────────────────────────────────────
    private val pEqFrequency     = AtomicReference(2600f)
    private val pEqQ             = AtomicReference(5.0f)
    private val pNoiseMix        = AtomicReference(0.35f)
    private val pSat1Gain        = AtomicReference(12.0f)
    private val pDelayTimeMs     = AtomicReference(25f)
    private val pDelayFeedback   = AtomicReference(0.65f)
    private val pSat2Gain        = AtomicReference(4.0f)
    private val pRingModHz       = AtomicReference(800f)
    private val pBitCrushDepth   = AtomicReference(4f)
    private val pSampleRateTarget = AtomicReference(8000f)
    private val pWobbleRateHz    = AtomicReference(4.0f)
    private val pWobbleDepth     = AtomicReference(0.15f)
    private val pVocoderMix      = AtomicReference(0.5f)
    private val pBurstProb       = AtomicReference(0.02f)
    private val pHardClipThresh  = AtomicReference(0.30f)
    private val pBoostPercent    = AtomicReference(BOOST_DEFAULT_PERCENT)
    private val pLimiterEnabled  = AtomicBoolean(true)

    // ─────────────────────────────────────────────────────────────────────────
    // Pre-allocated working buffers (no allocation in the hot path)
    // ─────────────────────────────────────────────────────────────────────────
    private val workBuf      = FloatArray(BUFFER_SIZE_SAMPLES)
    private val pitchTmpBuf  = FloatArray(BUFFER_SIZE_SAMPLES)
    private val outBuf       = ShortArray(BUFFER_SIZE_SAMPLES)

    // ── Stage 1: Biquad state ────────────────────────────────────────────────
    private var bqB0 = 0.0; private var bqB1 = 0.0; private var bqB2 = 0.0
    private var bqA1 = 0.0; private var bqA2 = 0.0
    private var bqX1 = 0.0; private var bqX2 = 0.0
    private var bqY1 = 0.0; private var bqY2 = 0.0
    private var lastEqFreq = -1f; private var lastEqQ = -1f

    // ── Stage 4: Feedback delay ring buffer ─────────────────────────────────
    private val maxDelaySamples = sampleRate * 2   // 2-second ring buffer max
    private val delayBuf        = FloatArray(maxDelaySamples)
    private var delayWriteIdx   = 0
    // Bug 7 fix: track delay length as a Float and ramp it smoothly towards the
    // target value at DELAY_SMOOTH_RATE per sample. An instant jump in delaySamples
    // causes the read head to skip, producing a transient click when the slider moves.
    // At 0.002 samples/sample convergence, a max change of 500 ms (24000 samples) takes
    // ~24000 / (24000 * 0.002) = 500 ms worst-case, but typical slider moves are small
    // (<1000 sample delta) and converge in under 10 ms — imperceptible.
    private var currentDelaySamples = -1f   // -1 sentinel: snap on first process() call

    // ── Stage 6: Ring mod phase accumulator ─────────────────────────────────
    private var ringModPhase = 0.0

    // ── Stage 8: Sample-rate-reduction hold state ────────────────────────────
    private var srHeldSample  = 0f
    private var srHoldCounter = 0

    // ── Stage 9: Pitch wobble state ──────────────────────────────────────────
    private var wobblePhase  = 0.0
    private var pitchReadPos = 0.0    // fractional read position into pitchTmpBuf

    // ── Stage 10: Vocoder state (3-band) ─────────────────────────────────────
    private val NUM_VBANDS       = 3
    private val vBandFreqs       = floatArrayOf(500f, 1500f, 3500f)
    private val vCarrierFreqs    = floatArrayOf(500f, 1500f, 3500f)
    private val vBandQ           = 4.0
    private val vB0              = DoubleArray(NUM_VBANDS)
    private val vB1              = DoubleArray(NUM_VBANDS)
    private val vB2              = DoubleArray(NUM_VBANDS)
    private val vA1              = DoubleArray(NUM_VBANDS)
    private val vA2              = DoubleArray(NUM_VBANDS)
    private val vBpX1            = DoubleArray(NUM_VBANDS)
    private val vBpX2            = DoubleArray(NUM_VBANDS)
    private val vBpY1            = DoubleArray(NUM_VBANDS)
    private val vBpY2            = DoubleArray(NUM_VBANDS)
    private val vEnvelope        = FloatArray(NUM_VBANDS)
    private val vCarrierPhase    = DoubleArray(NUM_VBANDS)
    private val vBandOut         = FloatArray(BUFFER_SIZE_SAMPLES)

    // ── Stage 11: Transient burst state ──────────────────────────────────────
    private var burstRemaining = 0
    private val rng = Random(System.nanoTime())

    // ─────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────────────────
    init {
        recomputeBiquadCoeffs()
        recomputeVocoderCoeffs()
        prewarmDelayBuffer()
        Log.d(TAG, "ChaosDSP initialised at ${sampleRate} Hz, buffer=${BUFFER_SIZE_SAMPLES} samples")
    }

    /**
     * Pre-warms the echo ring buffer to eliminate the silent first ~500 ms
     * that would otherwise occur on activation (Known Issue #4).
     * Fills with a low-level linear ramp to avoid an activation pop.
     */
    private fun prewarmDelayBuffer() {
        val prewarmSamples = (sampleRate.toLong() * ECHO_PREWARM_MS / 1000L)
            .toInt().coerceAtMost(maxDelaySamples)
        for (i in 0 until prewarmSamples) {
            delayBuf[i] = (i.toFloat() / prewarmSamples.toFloat()) * 0.001f
        }
        Log.d(TAG, "Echo buffer pre-warmed (${ECHO_PREWARM_MS} ms, $prewarmSamples samples)")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Coefficient computation helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recomputes Stage 1 biquad bandpass coefficients when EQ parameters change.
     *
     * Formula (11_DSP_ENGINE.md §1):
     *   w0 = 2π·f0/fs,  α = sin(w0)/(2·Q)
     *   b0 =  α,  b1 = 0,  b2 = -α,  a0 = 1+α,  a1 = -2·cos(w0),  a2 = 1-α
     * (all divided by a0)
     */
    private fun recomputeBiquadCoeffs() {
        val f0 = pEqFrequency.get().toDouble()
        val q  = pEqQ.get().toDouble()
        lastEqFreq = pEqFrequency.get()
        lastEqQ    = pEqQ.get()

        val w0    = 2.0 * PI * f0 / sampleRate.toDouble()
        val alpha = sin(w0) / (2.0 * q)
        val cosW0 = cos(w0)
        val a0    = 1.0 + alpha

        bqB0 = alpha / a0
        bqB1 = 0.0
        bqB2 = -alpha / a0
        bqA1 = (-2.0 * cosW0) / a0
        bqA2 = (1.0 - alpha) / a0
    }

    /**
     * Computes biquad bandpass coefficients for each vocoder band (Stage 10).
     */
    private fun recomputeVocoderCoeffs() {
        for (b in 0 until NUM_VBANDS) {
            val f0    = vBandFreqs[b].toDouble()
            val w0    = 2.0 * PI * f0 / sampleRate.toDouble()
            val alpha = sin(w0) / (2.0 * vBandQ)
            val cosW0 = cos(w0)
            val a0    = 1.0 + alpha
            vB0[b] =  alpha / a0
            vB1[b] =  0.0
            vB2[b] = -alpha / a0
            vA1[b] = (-2.0 * cosW0) / a0
            vA2[b] = (1.0 - alpha) / a0
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies the full 14-stage DSP chain to [buffer].
     * Called on the audio thread at [Process.THREAD_PRIORITY_URGENT_AUDIO].
     * Zero heap allocation in this method.
     *
     * @param buffer Raw 16-bit PCM mono input samples
     * @return Processed 16-bit PCM output samples (same length, reused buffer)
     */
    fun process(buffer: ShortArray): ShortArray {
        val len = buffer.size

        // ── Snapshot parameters (single read per cycle for consistency) ──────
        val sEqFreq    = pEqFrequency.get()
        val sEqQ       = pEqQ.get()
        val sNoiseMix  = pNoiseMix.get().coerceIn(0f, 1f)
        val sSat1      = pSat1Gain.get().coerceIn(0.1f, 50f)
        val sDelayMs   = pDelayTimeMs.get().coerceIn(1f, 500f)
        val sDelayFb   = pDelayFeedback.get().coerceIn(0f, 0.95f)
        val sSat2      = pSat2Gain.get().coerceIn(0.1f, 20f)
        val sRingHz    = pRingModHz.get().coerceIn(10f, 4000f)
        val sBitDepth  = pBitCrushDepth.get().coerceIn(1f, 16f)
        val sSrTarget  = pSampleRateTarget.get().coerceIn(1000f, sampleRate.toFloat())
        val sWobRate   = pWobbleRateHz.get().coerceIn(0f, 20f)
        val sWobDepth  = pWobbleDepth.get().coerceIn(0f, 0.5f)
        val sVocMix    = pVocoderMix.get().coerceIn(0f, 1f)
        val sBurstProb = pBurstProb.get().coerceIn(0f, 0.3f)
        val sHardClip  = pHardClipThresh.get().coerceIn(0.05f, 1f)
        val sBoost     = pBoostPercent.get().coerceIn(BOOST_MIN_PERCENT, BOOST_MAX_PERCENT)
        val sLimiter   = pLimiterEnabled.get()

        // Recompute biquad if EQ params have changed since last cycle
        if (sEqFreq != lastEqFreq || sEqQ != lastEqQ) {
            recomputeBiquadCoeffs()
        }

        // ── Normalise input shorts → float [-1, 1] ────────────────────────
        val inv16 = 1f / Short.MAX_VALUE.toFloat()
        for (i in 0 until len) workBuf[i] = buffer[i] * inv16

        // ════════════════════════════════════════════════════════════════════
        // STAGE 1 — Resonant EQ (second-order bandpass biquad)
        // y[n] = b0·x[n] + b1·x[n-1] + b2·x[n-2] − a1·y[n-1] − a2·y[n-2]
        // ════════════════════════════════════════════════════════════════════
        for (i in 0 until len) {
            val x = workBuf[i].toDouble()
            val y = bqB0 * x + bqB1 * bqX1 + bqB2 * bqX2 - bqA1 * bqY1 - bqA2 * bqY2
            bqX2 = bqX1; bqX1 = x
            bqY2 = bqY1; bqY1 = y
            workBuf[i] = y.toFloat()
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 2 — Noise Injection
        // output[n] = (1 - mix) · input[n] + mix · whiteNoise()
        // ════════════════════════════════════════════════════════════════════
        if (sNoiseMix > 0f) {
            for (i in 0 until len) {
                val noise = rng.nextFloat() * 2f - 1f
                workBuf[i] = (1f - sNoiseMix) * workBuf[i] + sNoiseMix * noise
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 3 — Saturation 1 (tanh distortion)
        // output[n] = tanh(gain · input[n]) / tanh(gain)
        // Dividing by tanh(gain) keeps output level consistent across gain settings.
        // ════════════════════════════════════════════════════════════════════
        val tanhSat1 = tanh(sSat1.toDouble()).toFloat().coerceAtLeast(1e-9f)
        for (i in 0 until len) {
            workBuf[i] = tanh(sSat1 * workBuf[i].toDouble()).toFloat() / tanhSat1
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 4 — Feedback Delay
        // delayed = ringBuffer[readIndex]
        // output[n] = input[n] + feedback · delayed
        // ringBuffer[writeIndex] = output[n]  (self-feedback)
        //
        // Bug 7 fix: delaySamples is smoothed per-sample toward the target value
        // (1-pole IIR at DELAY_SMOOTH_RATE) so live slider changes produce no click.
        // Linear interpolation between adjacent ring-buffer slots handles the fractional
        // read position, preventing stale/garbage reads when delay changes mid-buffer.
        // ════════════════════════════════════════════════════════════════════
        val targetDelaySamples = (sDelayMs * sampleRate / 1000f).coerceIn(1f, (maxDelaySamples - 1).toFloat())
        // Snap to target on the very first process() call (sentinel -1f) to avoid
        // an invalid ramp from 0 at session start.
        if (currentDelaySamples < 0f) currentDelaySamples = targetDelaySamples

        for (i in 0 until len) {
            // Smooth: advance currentDelaySamples toward target by DELAY_SMOOTH_RATE each sample.
            currentDelaySamples += (targetDelaySamples - currentDelaySamples) * DELAY_SMOOTH_RATE

            // Fractional read position in the ring buffer.
            val readPosF  = (delayWriteIdx - currentDelaySamples + maxDelaySamples) % maxDelaySamples
            val readIdx0  = readPosF.toInt() % maxDelaySamples
            val readIdx1  = (readIdx0 + 1) % maxDelaySamples
            val frac      = readPosF - readIdx0
            // Linear interpolation between the two adjacent ring-buffer samples.
            val delayed   = delayBuf[readIdx0] + frac * (delayBuf[readIdx1] - delayBuf[readIdx0])

            val out = workBuf[i] + sDelayFb * delayed
            delayBuf[delayWriteIdx] = out.coerceIn(-1f, 1f)
            delayWriteIdx = (delayWriteIdx + 1) % maxDelaySamples
            workBuf[i] = out
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 5 — Saturation 2 (tanh distortion, moderate gain)
        // output[n] = tanh(gain · input[n]) / tanh(gain)
        // ════════════════════════════════════════════════════════════════════
        val tanhSat2 = tanh(sSat2.toDouble()).toFloat().coerceAtLeast(1e-9f)
        for (i in 0 until len) {
            workBuf[i] = tanh(sSat2 * workBuf[i].toDouble()).toFloat() / tanhSat2
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 6 — Ring Modulation
        // output[n] = input[n] · sin(2π · carrierFreq · n / sampleRate)
        // ════════════════════════════════════════════════════════════════════
        val ringInc = 2.0 * PI * sRingHz / sampleRate.toDouble()
        for (i in 0 until len) {
            workBuf[i] = (workBuf[i] * sin(ringModPhase)).toFloat()
            ringModPhase += ringInc
            if (ringModPhase > 2.0 * PI) ringModPhase -= 2.0 * PI
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 7 — Bit Crusher
        // steps = 2^bitDepth
        // output[n] = round(input[n] · steps) / steps
        // ════════════════════════════════════════════════════════════════════
        val steps = (1 shl sBitDepth.toInt()).toFloat()
        for (i in 0 until len) {
            workBuf[i] = kotlin.math.round(workBuf[i] * steps).toFloat() / steps
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 8 — Sample Rate Reduction (hold-and-repeat)
        // holdFactor = originalSampleRate / targetSampleRate
        // if (n % holdFactor == 0): heldSample = input[n]
        // output[n] = heldSample
        // ════════════════════════════════════════════════════════════════════
        val holdFactor = (sampleRate.toFloat() / sSrTarget).toInt().coerceAtLeast(1)
        if (holdFactor > 1) {
            for (i in 0 until len) {
                if (srHoldCounter == 0) srHeldSample = workBuf[i]
                workBuf[i] = srHeldSample
                srHoldCounter = (srHoldCounter + 1) % holdFactor
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 9 — Pitch Drop / Wobble
        // pitchFactor[n] = 1 + wobbleDepth · sin(2π · wobbleRate · n / sampleRate)
        // Applied via variable-rate buffer read with linear interpolation.
        // ════════════════════════════════════════════════════════════════════
        if (sWobDepth > 0f && sWobRate > 0f) {
            // Copy current buffer for variable-rate reading
            for (i in 0 until len) pitchTmpBuf[i] = workBuf[i]
            val wobInc = 2.0 * PI * sWobRate / sampleRate.toDouble()
            pitchReadPos = 0.0
            for (i in 0 until len) {
                val pf = 1.0 + sWobDepth * sin(wobblePhase)
                wobblePhase += wobInc
                if (wobblePhase > 2.0 * PI) wobblePhase -= 2.0 * PI

                // Linear interpolation between adjacent samples
                val ri   = pitchReadPos.toInt().coerceIn(0, len - 1)
                val frac = pitchReadPos - ri
                val s0   = pitchTmpBuf[ri]
                val s1   = if (ri + 1 < len) pitchTmpBuf[ri + 1] else pitchTmpBuf[len - 1]
                workBuf[i] = (s0 + frac * (s1 - s0)).toFloat()

                pitchReadPos += pf
                if (pitchReadPos >= len) pitchReadPos = (len - 1).toDouble()
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 10 — Vocoder / Robotic Layer
        // Multi-band envelope-follower vocoder: bandpass → envelope → carrier · env → re-sum
        // ════════════════════════════════════════════════════════════════════
        if (sVocMix > 0f) {
            vBandOut.fill(0f, 0, len)
            val envDecay = 0.992f   // one-pole envelope follower decay
            val carIncs  = DoubleArray(NUM_VBANDS) { b ->
                2.0 * PI * vCarrierFreqs[b] / sampleRate.toDouble()
            }
            for (b in 0 until NUM_VBANDS) {
                for (i in 0 until len) {
                    // Bandpass filter
                    val x = workBuf[i].toDouble()
                    val y = vB0[b]*x + vB1[b]*vBpX1[b] + vB2[b]*vBpX2[b] -
                            vA1[b]*vBpY1[b] - vA2[b]*vBpY2[b]
                    vBpX2[b] = vBpX1[b]; vBpX1[b] = x
                    vBpY2[b] = vBpY1[b]; vBpY1[b] = y

                    // Envelope follower
                    val mag = abs(y).toFloat()
                    vEnvelope[b] = if (mag > vEnvelope[b]) mag else vEnvelope[b] * envDecay

                    // Carrier tone (sine) shaped by envelope
                    val carrier = sin(vCarrierPhase[b]).toFloat()
                    vCarrierPhase[b] += carIncs[b]
                    if (vCarrierPhase[b] > 2.0 * PI) vCarrierPhase[b] -= 2.0 * PI

                    vBandOut[i] += vEnvelope[b] * carrier
                }
            }
            // Blend vocoder output with dry signal
            val vGain = 1f / NUM_VBANDS.toFloat()
            for (i in 0 until len) {
                workBuf[i] = (1f - sVocMix) * workBuf[i] + sVocMix * vBandOut[i] * vGain
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 11 — Transient Burst (Scream / Screech)
        // Per-buffer low-probability random trigger inserts a short burst of
        // high-gain distorted noise — creates unpredictable "chaos" character.
        // Probability is applied per sample (burstProbability / bufferLen) for
        // deterministic average occurrence rate.
        // ════════════════════════════════════════════════════════════════════
        val burstProbPerSample = sBurstProb / len.toFloat()
        for (i in 0 until len) {
            if (burstRemaining > 0) {
                val burstNoise = (rng.nextFloat() * 2f - 1f) * 2f
                workBuf[i] = tanh((workBuf[i] + burstNoise).toDouble() * 6.0).toFloat()
                burstRemaining--
            } else if (rng.nextFloat() < burstProbPerSample) {
                val durMs = BURST_DUR_MIN_MS + rng.nextInt(BURST_DUR_MAX_MS - BURST_DUR_MIN_MS)
                burstRemaining = durMs * sampleRate / 1000
            }
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 12 — Hard Clip
        // output[n] = clamp(input[n], −threshold, +threshold)
        // ════════════════════════════════════════════════════════════════════
        for (i in 0 until len) {
            workBuf[i] = workBuf[i].coerceIn(-sHardClip, sHardClip)
        }

        // ════════════════════════════════════════════════════════════════════
        // STAGE 13 — Output Gain / Boost
        // output[n] = input[n] · (boostPercent / 100)
        // Range: 100%–500% (22_CONFIGURATION.md, 21_SETTINGS_AND_PRESETS.md)
        // ════════════════════════════════════════════════════════════════════
        val gain = sBoost / 100f
        for (i in 0 until len) workBuf[i] *= gain

        // ════════════════════════════════════════════════════════════════════
        // STAGE 14 — Limiter (soft-knee) — MUST remain the final DSP stage
        // (Known Issue #7 fix; 21_SETTINGS_AND_PRESETS.md Limiter setting)
        //
        // if |input[n]| > kneeThreshold:
        //   output[n] = sign(input[n]) · (knee + (|input[n]| − knee) / ratio)
        // else:
        //   output[n] = input[n]
        // ════════════════════════════════════════════════════════════════════
        if (sLimiter) {
            for (i in 0 until len) {
                val s   = workBuf[i]
                val abs = abs(s)
                if (abs > LIMITER_KNEE_THRESHOLD) {
                    workBuf[i] = sign(s) *
                        (LIMITER_KNEE_THRESHOLD + (abs - LIMITER_KNEE_THRESHOLD) / LIMITER_RATIO)
                }
            }
        }

        // ── Denormalise float → short ──────────────────────────────────────
        val maxS = Short.MAX_VALUE.toFloat()
        for (i in 0 until len) {
            outBuf[i] = (workBuf[i] * maxS).coerceIn(-maxS, maxS).toInt().toShort()
        }

        return outBuf
    }

    /**
     * Updates a single DSP parameter from the UI thread.
     * Thread-safe: uses AtomicReference/AtomicBoolean writes.
     * Out-of-range values are silently clamped rather than thrown (09_METHOD_SPECIFICATION.md).
     *
     * @param stage  Which stage parameter to update
     * @param value  New parameter value
     */
    fun setParameter(stage: Stage, value: Float) {
        Log.d(TAG, "setParameter ${stage.key}=$value")
        when (stage) {
            Stage.EQ_FREQUENCY        -> pEqFrequency.set(value.coerceIn(100f, 8000f))
            Stage.EQ_Q                -> pEqQ.set(value.coerceIn(0.5f, 20f))
            Stage.NOISE_MIX           -> pNoiseMix.set(value.coerceIn(0f, 1f))
            Stage.SAT1_GAIN           -> pSat1Gain.set(value.coerceIn(0.1f, 50f))
            Stage.DELAY_TIME_MS       -> pDelayTimeMs.set(value.coerceIn(1f, 500f))
            Stage.DELAY_FEEDBACK      -> pDelayFeedback.set(value.coerceIn(0f, 0.95f))
            Stage.SAT2_GAIN           -> pSat2Gain.set(value.coerceIn(0.1f, 20f))
            Stage.RING_MOD_HZ         -> pRingModHz.set(value.coerceIn(10f, 4000f))
            Stage.BIT_CRUSH_DEPTH     -> pBitCrushDepth.set(value.coerceIn(1f, 16f))
            Stage.SAMPLE_RATE_TARGET_HZ -> pSampleRateTarget.set(value.coerceIn(1000f, sampleRate.toFloat()))
            Stage.PITCH_WOBBLE_RATE_HZ -> pWobbleRateHz.set(value.coerceIn(0f, 20f))
            Stage.PITCH_WOBBLE_DEPTH  -> pWobbleDepth.set(value.coerceIn(0f, 0.5f))
            Stage.VOCODER_MIX         -> pVocoderMix.set(value.coerceIn(0f, 1f))
            Stage.BURST_PROBABILITY   -> pBurstProb.set(value.coerceIn(0f, 0.3f))
            Stage.HARD_CLIP_THRESHOLD -> pHardClipThresh.set(value.coerceIn(0.05f, 1f))
            Stage.BOOST_PERCENT       -> pBoostPercent.set(value.coerceIn(BOOST_MIN_PERCENT, BOOST_MAX_PERCENT))
            Stage.LIMITER_ENABLED     -> pLimiterEnabled.set(value != 0f)
        }
    }

    /**
     * Loads a complete preset, replacing all parameters atomically.
     *
     * @param preset Preset to apply
     */
    fun loadPreset(preset: Preset) {
        Log.i(TAG, "Applied preset: ${preset.name}")
        pEqFrequency.set(preset.eqFrequency)
        pEqQ.set(preset.eqQ)
        pNoiseMix.set(preset.noiseMix)
        pSat1Gain.set(preset.saturation1Gain)
        pDelayTimeMs.set(preset.delayTimeMs)
        pDelayFeedback.set(preset.delayFeedback)
        pSat2Gain.set(preset.saturation2Gain)
        pRingModHz.set(preset.ringModHz)
        pBitCrushDepth.set(preset.bitCrushDepth)
        pSampleRateTarget.set(preset.sampleRateTargetHz)
        pWobbleRateHz.set(preset.pitchWobbleRateHz)
        pWobbleDepth.set(preset.pitchWobbleDepth)
        pVocoderMix.set(preset.vocoderMix)
        pBurstProb.set(preset.burstProbability)
        pHardClipThresh.set(preset.hardClipThreshold)
        pBoostPercent.set(preset.boostPercent)
    }

    /**
     * Resets all stateful DSP buffers (delay line, biquad state, etc.).
     * Called when a session ends so state doesn't bleed into the next session.
     */
    fun reset() {
        bqX1 = 0.0; bqX2 = 0.0; bqY1 = 0.0; bqY2 = 0.0
        delayBuf.fill(0f)
        delayWriteIdx = 0
        currentDelaySamples = -1f  // sentinel: snap to target on next process() call
        ringModPhase = 0.0
        wobblePhase = 0.0; pitchReadPos = 0.0
        for (b in 0 until NUM_VBANDS) {
            vBpX1[b] = 0.0; vBpX2[b] = 0.0
            vBpY1[b] = 0.0; vBpY2[b] = 0.0
            vEnvelope[b] = 0f; vCarrierPhase[b] = 0.0
        }
        srHeldSample = 0f; srHoldCounter = 0
        burstRemaining = 0
        prewarmDelayBuffer()
        Log.d(TAG, "DSP state reset and echo buffer re-warmed")
    }
}
