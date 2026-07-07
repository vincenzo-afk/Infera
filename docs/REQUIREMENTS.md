# ChaosVoice — Requirements

## 1. Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | User can toggle voice processing on/off with a single control |
| FR-2 | User can select from preset voice effects (Demon, Robot, Crushed Radio, Glitch) |
| FR-3 | User can manually adjust individual effect parameters (distortion amount, pitch, bit-crush depth, reverb, etc.) |
| FR-4 | User can adjust an overall output volume boost (up to 500% of source gain) |
| FR-5 | Processed audio must play back through the device loudspeaker in real time (target latency < 150ms) |
| FR-6 | App must run as a foreground service with a persistent notification while active, per Android policy |
| FR-7 | App must request all required permissions in the correct sequence (see `PERMISSIONS.md`) |
| FR-8 | App must gracefully handle another app claiming audio focus (e.g., an incoming call) via `AudioFocusManager` |
| FR-9 | App must allow the user to save and reuse custom effect presets |
| FR-10 | App must work without root access on stock Android 10+ (API 29+) |

## 2. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-1 | Audio processing must not introduce more than ~150ms of latency end-to-end |
| NFR-2 | The hot audio-processing path must be allocation-free to avoid GC-induced glitches/dropouts |
| NFR-3 | App must survive being backgrounded for reasonable session lengths without the OS killing the service |
| NFR-4 | UI must clearly indicate when Chaos Mode is active (so the user always knows their voice is currently being altered) |
| NFR-5 | Volume boosting must include a limiter stage to avoid destructive clipping or complete audio dropout at high boost levels |
| NFR-6 | App must not require special/system-signed permissions or root |

## 3. Explicit Non-Goals

- This app does not intercept, modify, or inject into another person's device or audio stream
- This app does not attempt system-wide virtual microphone replacement without user awareness
- This app is not designed to disguise the user's identity for fraudulent or harassing purposes — it is a voice-effects entertainment tool used consensually by the person speaking, on their own device

## 4. Target Platforms

- Android 10+ (API 29+), primary platform
- iOS, secondary/future platform (via `AudioEngineManager.swift`)

## 5. Out of Scope (For Now)

- Root-based system-wide mic replacement
- Cross-platform desktop version
- Cloud-based voice model processing (all effects are local DSP, not ML voice conversion)
