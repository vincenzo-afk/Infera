# 08 — Class Specification

## `MainActivity.kt`
- **Purpose:** Entry point activity; bridges Flutter and native Android
- **Fields:** `methodChannel: MethodChannel`
- **Methods:** `configureFlutterEngine()`, `onActivityResult()`, `handleMethodCall()`
- **Relationships:** Registers `ChaosProjectionService`, `AudioFocusManager`
- **Lifecycle:** Standard Android Activity lifecycle; must survive permission dialog transitions (`singleInstance` launch mode)

## `ChaosProjectionService.kt`
- **Purpose:** Foreground service owning the live audio pipeline
- **Fields:** `state: ServiceState` (INIT/ACTIVE), `audioRecord`, `audioTrack`, `dsp: ChaosDSP`
- **Methods:** `onStartCommand()`, `startProcessingLoop()`, `stopProcessingLoop()`, `updateNotification()`
- **Relationships:** Uses `ChaosDSP`, `AudioFocusManager`
- **Lifecycle:** `START_STICKY`; transitions INIT → ACTIVE → STOPPED

## `ChaosVpnService.kt`
- **Purpose:** Optional background-survival helper service
- **Fields:** `vpnInterface: ParcelFileDescriptor`
- **Methods:** `establish()`, `teardown()`
- **Relationships:** Independent of audio pipeline; purely a survival aid
- **Lifecycle:** `START_STICKY`

## `ChaosDSP.kt`
- **Purpose:** The effects processing engine
- **Fields:** per-stage parameter sets, pre-allocated working buffers
- **Methods:** `process(buffer: ShortArray): ShortArray`, `setParameter(stage: Stage, value: Float)`, `loadPreset(preset: Preset)`
- **Relationships:** Used by `ChaosProjectionService`
- **Lifecycle:** Instantiated once per active session; stateful buffers reset on stop

## `BootReceiver.kt`
- **Purpose:** Re-arm services after device reboot, if previously enabled
- **Fields:** none (stateless receiver)
- **Methods:** `onReceive(context, intent)`
- **Relationships:** Starts `ChaosVpnService` if user preference indicates it was active
- **Lifecycle:** Fires once on `BOOT_COMPLETED`

## `AudioFocusManager.kt`
- **Purpose:** Manage system audio focus interactions
- **Fields:** `focusRequest: AudioFocusRequest`
- **Methods:** `requestFocus()`, `abandonFocus()`, `onAudioFocusChange(focusChange: Int)`
- **Relationships:** Used by `ChaosProjectionService`
- **Lifecycle:** Focus requested on session start, released on stop

## `ChaosController` (Dart)
- **Purpose:** App-level state holder
- **Fields:** `isActive: bool`, `activePreset: Preset`, `boostLevel: double`
- **Methods:** `toggleChaosMode()`, `selectPreset(preset)`, `setBoost(value)`
- **Relationships:** Consumed by `home_screen.dart` and effect widgets
- **Lifecycle:** Lives for the duration of the app session

## `EffectEngine` (Dart, `effect_engine.dart`)
- **Purpose:** Portable reference DSP implementation (iOS/preview path)
- **Fields:** mirrors `ChaosDSP.kt` parameter set
- **Methods:** `process(buffer)`, `setParameter(stage, value)`
- **Relationships:** Used on iOS in place of Kotlin `ChaosDSP`
- **Lifecycle:** Instantiated per session
