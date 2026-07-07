# ChaosVoice — DSP Effects Specification

## 1. Processing Order

Effects are applied in this order, buffer by buffer, in `ChaosDSP.process()`:

| Stage | Effect | Default Parameters | Purpose |
|---|---|---|---|
| 1 | Resonant EQ | Center 2600Hz, Q=5.0 | Emphasizes harsh/nasal frequency band |
| 2 | Noise Injection | 35% white noise mix | Adds static/radio-broken texture |
| 3 | Saturation (Stage 1) | Tanh distortion, 12x gain | Warm-to-harsh distortion pass |
| 4 | Feedback Delay | 25ms delay, 65% feedback | Adds a metallic slap/echo character |
| 5 | Saturation (Stage 2) | Tanh distortion, 4x gain | Second distortion pass for grit |
| 6 | Ring Modulation | 800Hz carrier | Robotic/metallic tone |
| 7 | Bit Crusher | 4-bit depth | Lo-fi digital crunch |
| 8 | Sample Rate Reduction | 8000Hz effective | Adds aliasing/crush artifacts |
| 9 | Pitch Drop / Wobble | Variable, preset-dependent | Demonic pitch instability |
| 10 | Vocoder / Robotic Layer | Preset-dependent | Mechanical harshness layer |
| 11 | Transient Burst (Scream/Screech) | Randomized, low probability per buffer | Occasional harsh transient spikes |
| 12 | Hard Clip | 30% threshold | Aggressive clipping for crunch |
| 13 | Output Gain / Boost | User-adjustable, up to 500% | Overall loudness boost |
| 14 | Limiter | Soft-knee | Prevents destructive clipping/silence at high boost; keeps output audible |

## 2. Presets

### Demon
- Heavy pitch drop, strong reverb, saturation stages 1 & 2 at full, moderate ring mod

### Robot
- Strong ring modulation + vocoder layer, light distortion, no pitch wobble

### Crushed Radio
- Heavy bit crush + sample rate reduction + noise injection, minimal distortion

### Glitch
- Frequent transient bursts, dropout stage, aggressive bit crush, random pitch wobble

## 3. Volume Boost Behavior

- Range: 100% (unity) to 500%
- Implemented as a linear gain multiplier applied at Stage 13, before the limiter
- Above ~300-400%, natural clipping/distortion will occur even with the limiter — this is expected and part of the intended aggressive sound character at high settings
- The limiter's job is to prevent total signal collapse (full clipping to a flat line) or destructive digital overflow artifacts, not to make high boost levels "clean"

## 4. Echo/Reverb Notes

- Echo buffer must be pre-warmed (filled with a short ramp-up) to avoid a silent gap in the first ~500ms of playback
- Reverb decay times should be tuned shorter/harsher than a "large room" preset — aim for a tight, aggressive decay that reads as "demonic" rather than spacious

## 5. Implementation Notes for the Building Agent

- All effect stages must operate on pre-allocated buffers — no per-buffer heap allocation in the hot path
- Kotlin native implementation (`ChaosDSP.kt`) is the source of truth for the live Android audio path
- Dart implementation (`effect_engine.dart`) should mirror the same effect order/parameters for iOS and for any in-app preview/non-realtime rendering, but should not run simultaneously with the Kotlin path during live Android processing (avoid double-processing)
- Each effect should be independently toggleable and parameterized so new presets can be composed without code changes
