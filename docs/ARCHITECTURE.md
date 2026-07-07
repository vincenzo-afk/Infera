# ChaosVoice — Architecture

## 1. High-Level Overview

ChaosVoice captures the user's own microphone input, runs it through a real-time DSP effects chain, and plays the processed audio back through the device's own loudspeaker at high volume. If the user is on a VoIP call at the time, the phone's own microphone naturally re-captures that processed loudspeaker output and sends it into the call — this is called the **acoustic loopback method**. No root access, system audio injection, or modification of other apps is required.

```
User's Mic Input
      │
      ▼
DSP Chaos Engine (ChaosDSP.kt)
      │
      ▼
AudioTrack → Device Loudspeaker (boosted volume)
      │
      ▼
(If a call is active) Phone's own mic re-captures the processed sound
      │
      ▼
Call app sends it to the other participant
```

## 2. Why This Approach (No Root)

Android does not allow one app to override another app's live microphone stream — this is an OS-level security restriction. Rather than fighting this restriction, ChaosVoice works *with* it: it processes audio and plays it back acoustically, the same way a physical voice-changer megaphone would work in the room. This keeps the app root-free and compliant with standard Android security models.

## 3. Native Layer (Kotlin)

| File | Responsibility |
|---|---|
| `MainActivity.kt` | Flutter↔Android bridge; orchestrates permission requests and activity results |
| `ChaosProjectionService.kt` | Foreground service; owns the AudioRecord→DSP→AudioTrack pipeline |
| `ChaosVpnService.kt` | Optional background-survival service (fake local VPN) to reduce the chance of Android killing the audio service while backgrounded |
| `ChaosDSP.kt` | The effects processing engine — see `DSP_EFFECTS_SPEC.md` |
| `BootReceiver.kt` | Re-registers services after device reboot if the user had it enabled |
| `AudioFocusManager.kt` | Requests and manages `AudioFocusRequest` so the service behaves correctly when another app (e.g. a call) also wants the mic/speaker |

## 4. Flutter Layer (Dart)

| File | Responsibility |
|---|---|
| `main.dart` | App entry point, initial permission sequencing |
| `native_audio_bridge.dart` | MethodChannel wrapper for calling into Kotlin |
| `chaos_controller.dart` | App state: active effect preset, boost level, on/off state |
| `home_screen.dart` | Main screen: toggle, preset picker, sliders |
| `effect_engine.dart` | Reference/portable Dart implementation of the DSP chain (used for iOS or preview/testing; Kotlin native is used for the live low-latency Android path) |
| `effect_sliders_widget.dart` | UI sliders for tuning individual effect parameters |
| `volume_boost_widget.dart` | UI control for the loudspeaker output boost level |

## 5. iOS Layer (Swift, secondary platform)

| File | Responsibility |
|---|---|
| `AudioEngineManager.swift` | `AVAudioEngine`-based equivalent pipeline for iOS |

## 6. Audio Pipeline Detail

### Capture
- `AudioRecord` captures raw mic input at 48kHz, 16-bit PCM, mono
- Buffer size tuned for low latency (~20-40ms window)

### Processing
- Buffers are passed through `ChaosDSP.process()`, which runs the effect chain in the order defined in `DSP_EFFECTS_SPEC.md`

### Output
- Processed buffers are written to an `AudioTrack` configured for low-latency playback
- Output is routed to the device's main loudspeaker (not the earpiece/call routing), at a boosted gain level set by the user

## 7. Service Lifecycle

- `ChaosProjectionService` starts in `INIT` state (foreground, no processing) before requesting any sensitive permission, per Android 14 foreground-service requirements
- On successful permission grant, it transitions to `ACTIVE` state and begins the capture→process→playback loop
- `START_STICKY` lifecycle so the service attempts to restart if killed by the system

## 8. Threading & Performance

- Audio processing runs on a dedicated thread with `THREAD_PRIORITY_URGENT_AUDIO`
- Target end-to-end latency: under 150ms
- DSP chain must be kept allocation-free in the hot path (pre-allocate all buffers) to avoid GC-related audio glitches
