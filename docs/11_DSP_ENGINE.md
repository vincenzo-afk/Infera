# 11 — DSP Engine (Full Specification)

This is the most detailed document in the set — the complete definition of the ChaosVoice effects chain.

## Processing Order

| Stage | Effect | Default Parameters | CPU Cost (relative) |
|---|---|---|---|
| 1 | Resonant EQ (bandpass) | Center 2600Hz, Q=5.0 | Low |
| 2 | Noise Injection | 35% white noise mix | Low |
| 3 | Saturation (Stage 1) | Tanh distortion, 12x gain | Low |
| 4 | Feedback Delay | 25ms delay, 65% feedback | Medium (ring buffer) |
| 5 | Saturation (Stage 2) | Tanh distortion, 4x gain | Low |
| 6 | Ring Modulation | 800Hz carrier | Low |
| 7 | Bit Crusher | 4-bit depth | Low |
| 8 | Sample Rate Reduction | 8000Hz effective | Low |
| 9 | Pitch Drop / Wobble | Preset-dependent LFO rate/depth | Medium |
| 10 | Vocoder / Robotic Layer | Preset-dependent band count | Medium-High |
| 11 | Transient Burst (Scream/Screech) | Randomized, low per-buffer probability | Low |
| 12 | Hard Clip | 30% threshold | Low |
| 13 | Output Gain / Boost | User-adjustable, 100%–500% | Low |
| 14 | Limiter | Soft-knee | Low |

## Stage Details & Math

### 1. Resonant EQ
Second-order bandpass (biquad) filter centered at a configurable frequency with configurable Q (resonance sharpness). Implemented via standard biquad coefficients:
```
b0, b1, b2, a1, a2 derived from center frequency f0, Q, and sample rate fs
y[n] = b0*x[n] + b1*x[n-1] + b2*x[n-2] - a1*y[n-1] - a2*y[n-2]
```

### 2. Noise Injection
```
output[n] = (1 - mix) * input[n] + mix * whiteNoise()
```
where `whiteNoise()` is a uniformly distributed random value scaled to the signal range, and `mix` defaults to 0.35.

### 3 & 5. Saturation (Tanh Distortion)
```
output[n] = tanh(gain * input[n]) / tanh(gain)
```
Normalizing by `tanh(gain)` keeps output level roughly consistent across gain settings.

### 4. Feedback Delay
Circular buffer of length `delayTimeMs * sampleRate / 1000`:
```
delayed = ringBuffer[readIndex]
output[n] = input[n] + feedbackAmount * delayed
ringBuffer[writeIndex] = input[n] + feedbackAmount * delayed
```

### 6. Ring Modulation
```
output[n] = input[n] * sin(2π * carrierFreq * n / sampleRate)
```

### 7. Bit Crusher
```
steps = 2^bitDepth
output[n] = round(input[n] * steps) / steps
```

### 8. Sample Rate Reduction
Hold-and-repeat downsampling:
```
if (n % holdFactor == 0): heldSample = input[n]
output[n] = heldSample
```
where `holdFactor = originalSampleRate / targetSampleRate`.

### 9. Pitch Drop / Wobble
LFO-modulated resampling rate:
```
pitchFactor[n] = 1 + wobbleDepth * sin(2π * wobbleRate * n / sampleRate)
```
applied via variable-rate buffer read (linear interpolation between samples).

### 10. Vocoder / Robotic Layer
Simple multi-band envelope-follower vocoder: input signal split into N frequency bands (bandpass filter bank), each band's amplitude envelope extracted and applied to a fixed carrier tone (e.g., sawtooth or the ring-mod carrier), bands re-summed.

### 11. Transient Burst
Per-buffer, a low-probability random trigger (e.g., 1–3% chance per buffer depending on preset) inserts a short (~50-150ms) burst of high-gain distorted noise or a pitch spike, to create an unpredictable "scream/screech" character.

### 12. Hard Clip
```
output[n] = clamp(input[n], -threshold, +threshold)
```

### 13. Output Gain / Boost
```
output[n] = input[n] * (boostPercent / 100)
```
Range: 100–500.

### 14. Limiter (Soft-Knee)
Prevents destructive clipping/full signal collapse at high boost:
```
if abs(input[n]) > kneeThreshold:
    output[n] = sign(input[n]) * (kneeThreshold + (abs(input[n]) - kneeThreshold) / ratio)
else:
    output[n] = input[n]
```

## Presets — Parameter Sets

### Demon
- Pitch drop: deep, slow wobble
- Saturation stages: both near max
- Ring mod: moderate (400–600Hz carrier)
- Reverb/delay: tight, harsh decay

### Robot
- Ring modulation: strong (800–1000Hz)
- Vocoder layer: strong mix
- Pitch wobble: disabled
- Distortion: light

### Crushed Radio
- Bit crusher: heavy (3-bit)
- Sample rate reduction: aggressive (6000Hz effective)
- Noise injection: high (50%+)
- Distortion: minimal

### Glitch
- Transient burst: high probability
- Dropout stage (brief silence gaps): enabled
- Bit crusher: heavy
- Pitch wobble: fast, random depth

## Implementation Notes
- All buffers pre-allocated; no allocation in the hot path
- Kotlin (`ChaosDSP.kt`) is the source of truth for live Android processing
- Dart (`effect_engine.dart`) mirrors the same math for iOS and non-realtime preview rendering
