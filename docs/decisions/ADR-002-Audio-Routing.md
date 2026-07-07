# ADR-002 — Acoustic Loopback Instead of Direct Audio Injection

## Status
Accepted

## Context
The product goal is to alter the user's voice as heard by the other party on a VoIP call. Android does not permit one app to override another app's live microphone stream without root access or a system-signed build — this is an OS-level security boundary, not an implementation gap.

## Decision
Rather than attempting virtual microphone injection (which is unreliable or impossible without root on most consumer devices), ChaosVoice plays processed audio through the device's own loudspeaker at a boosted volume. Because the call app's microphone is already active during a call, it naturally picks up this processed sound acoustically, and it is relayed into the call. `AudioTrack` is configured with `USAGE_MEDIA` (not `USAGE_VOICE_COMMUNICATION`) to ensure the output is routed to the loudspeaker rather than into any call-specific audio path.

## Consequences
- Works reliably without root on stock Android
- Introduces some unavoidable latency and audio-quality variance due to the physical acoustic coupling step
- Requires the user to be in speakerphone mode / have adequate acoustic coupling between the loudspeaker and mic for the effect to be clearly heard
- Does not work on regular cellular/GSM calls, since those use a hardware-isolated audio path (documented in `37_LIMITATIONS.md`)
