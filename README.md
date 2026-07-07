# ChaosVoice (Infera)

ChaosVoice is a mobile voice-changer app that transforms the user's voice in real time into a demonic, distorted, chaotic sound during VoIP calls (WhatsApp, Discord, Telegram, game voice chat) or standalone recording/playback. This is a consensual, user-facing effects tool the user activates it on their own device to change their own voice sounds.

> Real-time voice effects via 14-stage DSP and acoustic loopback — no root required.

## Quick Start

```bash
flutter pub get
flutter run -d android   # Requires a physical Android device (API 29+)
```

See [docs/32_BUILD_GUIDE.md](docs/32_BUILD_GUIDE.md) for signing and release build instructions.

## Architecture

```
Flutter (Dart)           Android Native (Kotlin)
──────────────           ──────────────────────
ChaosController   ←→    MainActivity (MethodChannel + EventChannel)
NativeAudioBridge  ────→ ChaosProjectionService (foreground)
                              └── ChaosDSP (14-stage engine)
                              └── AudioRecord → AudioTrack (loudspeaker)
                         ChaosVpnService (opt-in background anchor)
                         BootReceiver (re-arms VPN after reboot)
```

## Key Design Decisions

| Decision | Choice | Reason |
|---|---|---|
| Live DSP | Kotlin only (ADR-001) | Deterministic audio-thread priority |
| Output routing | `USAGE_MEDIA` loudspeaker (ADR-002) | Acoustic loopback into call app mic |
| State management | `ChangeNotifier` / `Provider` (ADR-003) | Minimal, spec-compliant |
| Permission sequence | Notification → Service → Mic → Battery (ADR-004) | Required for foreground mic service |

## SDK Requirements

- **Min SDK:** 29 (Android 10)
- **Target SDK:** 34 (Android 14)
- **Kotlin:** 1.9.22

## Known Issues Resolved

- **#1** — `USAGE_MEDIA` (not `VOICE_COMMUNICATION`) for loudspeaker routing
- **#2** — `AudioFocusRequest` properly handled (gain/transient loss/permanent loss)
- **#4** — 500 ms echo buffer pre-warm at DSP init
- **#7** — Limiter is always the final DSP stage (Stage 14)
- **#8** — `ChaosVpnService` available as opt-in survival anchor (default OFF)

## Presets

| Preset | Boost | Character |
|---|---|---|
| Demon | 200% | Deep, slow wobble, heavy saturation |
| Robot | 150% | Strong vocoder + ring mod, no wobble |
| Crushed Radio | 150% | Heavy bit crush, aggressive sample rate reduction |
| Glitch | 175% | Frequent bursts, fast wobble |

## Repository

https://github.com/vincenzo-afk/Infera.git
