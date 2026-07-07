# ChaosVoice — Known Issues

| # | Issue | Impact | Fix Direction |
|---|---|---|---|
| 1 | Output routing assumes `VOICE_COMMUNICATION` usage will be picked up by other apps as a virtual mic | Effect doesn't reliably reach other callers | Switch `AudioTrack` routing to standard loudspeaker media playback; rely on acoustic loopback, not virtual mic injection |
| 2 | No `AudioFocusRequest` handling | Service may crash or silently fail when a call app takes audio focus | Implement `AudioFocusManager.kt`, handle focus loss/gain gracefully |
| 3 | DSP chain runs in both Dart and Kotlin simultaneously on Android | Wasted CPU, potential double-processing artifacts | Kotlin native path only for live Android processing; Dart path reserved for iOS/preview |
| 4 | Echo buffer starts empty | First ~500ms of echo/reverb effect is missing on activation | Pre-fill buffer with a short ramp before playback starts |
| 5 | Reverb tuned for spacious "large room" | Doesn't match intended harsh/demonic tone | Shorten decay time, increase early reflections density |
| 6 | UI not implemented | No way for users to actually control the app | Build out screens per `UI_SPEC.md` |
| 7 | No limiter after volume boost stage | Risk of destructive clipping/full silence at high boost | Add soft-knee limiter as final DSP stage |
| 8 | Foreground service may be killed in background on aggressive OEM battery managers (e.g. some Xiaomi/Samsung configs) | Session ends unexpectedly | Ensure battery optimization exemption flow is completed; document OEM-specific settings in user-facing help |
