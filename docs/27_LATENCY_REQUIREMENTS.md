# 27 — Latency Requirements

## Maximum End-to-End Latency
Target: under 150ms from mic input to loudspeaker output. Latencies above this become noticeably disruptive to natural conversation, even for an intentionally exaggerated effect.

## Latency Budget Breakdown

| Stage | Budget |
|---|---|
| Mic capture buffering | ~20ms |
| DSP processing (all 14 stages) | ~40ms |
| Playback buffering | ~20ms |
| Acoustic coupling delay (speaker→mic, physical) | ~10–30ms (device-dependent, not directly controllable) |
| System/OS scheduling overhead | ~20–40ms |
| **Total target** | **~110–150ms** |

## DSP Latency
- Each stage processes one buffer (1920 samples, ~40ms) at a time; the chain as a whole must complete within the buffer period to avoid falling behind real-time
- Feedback delay and pitch-wobble stages use small internal buffers and must not introduce additional latency beyond their configured delay time being part of the intended effect (not pipeline lag)

## Startup Latency
- Time from toggle tap to first processed audio output: under 500ms (excluding first-run permission dialogs)

## Buffer Latency
- Buffer size of 1920 samples at 48kHz (~40ms) is the default balance point between latency and underrun safety; may be tuned smaller on devices/APIs that reliably support `PERFORMANCE_MODE_LOW_LATENCY` with smaller buffers
