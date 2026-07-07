# 05 — System Architecture

## Complete Architecture Flow

```
Flutter (UI, state)
      │
      ▼  MethodChannel
Kotlin (Android native layer)
      │
      ▼
ChaosDSP (effects engine)
      │
      ▼
AudioTrack (playback)
      │
      ▼
Device Loudspeaker
      │
      ▼  (acoustic coupling)
Device Microphone
      │
      ▼
VoIP App (WhatsApp / Discord / Telegram / etc.)
```

## Layer Responsibilities

### Flutter Layer
Owns UI, app-level state, preset management, and settings persistence. Communicates with native Android code exclusively through a defined MethodChannel protocol (see `16_METHOD_CHANNEL_PROTOCOL.md`).

### Kotlin Native Layer
Owns the actual real-time audio pipeline: `AudioRecord` capture, `ChaosDSP` processing, `AudioTrack` output, foreground service lifecycle, and permission/audio-focus handling.

### DSP Engine
A stateless-per-call, buffer-in/buffer-out processing chain applying the effects defined in `11_DSP_ENGINE.md`, in a fixed order, entirely on-device.

### Acoustic Loopback
Because Android does not allow one app to override another app's live microphone stream without root, ChaosVoice plays processed audio through the loudspeaker at a boosted volume; the device's own microphone (already in use by the active VoIP app) naturally picks up this processed sound and relays it into the call. This is the core architectural decision documented in `decisions/ADR-002-Audio-Routing.md`.

## Diagram Reference
See `diagrams/architecture.drawio` for the full visual diagram, and `diagrams/audio_pipeline.drawio` for the detailed signal path.

## Cross-Cutting Concerns
- **Threading:** see `13_THREADING_MODEL.md`
- **State management:** see `17_STATE_MANAGEMENT.md`
- **Permissions:** see `14_PERMISSION_MODEL.md`
- **Background survival:** see `15_BACKGROUND_SERVICE.md`
