# 07 — Module Specification

## Module: Audio Capture (Android/Kotlin)
- **Purpose:** Capture raw microphone input
- **Responsibilities:** Configure and manage `AudioRecord`, deliver raw PCM buffers to the DSP module
- **Dependencies:** `RECORD_AUDIO` permission
- **Interfaces:** exposes `startCapture()`, `stopCapture()`, `onBufferAvailable(callback)`

## Module: DSP Engine (Android/Kotlin, mirrored in Dart)
- **Purpose:** Transform raw audio buffers according to the active effect chain
- **Responsibilities:** Apply the 14-stage effect chain (see `11_DSP_ENGINE.md`), expose live parameter updates
- **Dependencies:** none external; pure signal processing
- **Interfaces:** `process(buffer: ShortArray): ShortArray`, `setParameter(stage, value)`, `loadPreset(preset)`

## Module: Audio Output (Android/Kotlin)
- **Purpose:** Play processed audio to the device loudspeaker
- **Responsibilities:** Configure `AudioTrack`, manage low-latency playback buffer
- **Dependencies:** `MODIFY_AUDIO_SETTINGS`
- **Interfaces:** `write(buffer: ShortArray)`, `setVolume(gain)`

## Module: Service Lifecycle (Android/Kotlin)
- **Purpose:** Keep the audio pipeline running as a foreground service
- **Responsibilities:** Manage notification, `START_STICKY` restart behavior, state transitions (INIT → ACTIVE)
- **Dependencies:** `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`
- **Interfaces:** `startInit()`, `startActive(config)`, `stop()`

## Module: Audio Focus Management (Android/Kotlin)
- **Purpose:** Respond correctly to system audio focus changes
- **Responsibilities:** Request/release focus, pause/resume processing on focus loss/gain
- **Dependencies:** Android `AudioManager`
- **Interfaces:** `requestFocus()`, `onFocusChange(state)`

## Module: MethodChannel Bridge (Flutter↔Kotlin)
- **Purpose:** Pass commands and data between Dart and native code
- **Responsibilities:** Serialize/deserialize calls; route to the correct native handler
- **Dependencies:** Flutter `MethodChannel` API
- **Interfaces:** see `16_METHOD_CHANNEL_PROTOCOL.md`

## Module: App State (Dart)
- **Purpose:** Track and expose current app state to the UI
- **Responsibilities:** Hold active preset, boost level, on/off state; notify UI on change
- **Dependencies:** Flutter state management approach (see `17_STATE_MANAGEMENT.md`)
- **Interfaces:** `ChaosController` class API

## Module: UI (Dart)
- **Purpose:** Present controls and status to the user
- **Responsibilities:** Render home screen, advanced effects screen, volume boost screen
- **Dependencies:** `ChaosController`, `native_audio_bridge.dart`
- **Interfaces:** Widget tree per `19_UI_COMPONENTS.md`

## Module: Local Storage (Dart)
- **Purpose:** Persist custom presets and settings
- **Responsibilities:** Read/write preset JSON, read/write app settings
- **Dependencies:** SharedPreferences or file storage
- **Interfaces:** `savePreset(preset)`, `loadPresets()`, `deletePreset(id)`
