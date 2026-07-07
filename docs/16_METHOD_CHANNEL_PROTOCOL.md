# 16 — MethodChannel Protocol

Channel name: `com.chaosvoice/audio`

## Methods (Dart → Kotlin)

### `startForegroundServiceOnly`
- **Arguments:** none
- **Return:** `bool` success
- **Errors:** `SERVICE_START_FAILED`

### `requestRecordAudioPermission`
- **Arguments:** none
- **Return:** `bool` granted
- **Errors:** none (result conveys outcome)

### `startChaosMode`
- **Arguments:** `{ "preset": String, "boostLevel": double }`
- **Return:** `bool` success
- **Errors:** `PERMISSION_DENIED`, `AUDIO_INIT_FAILED`, `FOCUS_DENIED`

### `stopChaosMode`
- **Arguments:** none
- **Return:** `bool` success
- **Errors:** `SERVICE_NOT_RUNNING`

### `updateParameter`
- **Arguments:** `{ "stage": String, "value": double }`
- **Return:** `bool` success
- **Errors:** `INVALID_STAGE`, `VALUE_OUT_OF_RANGE` (clamped, not typically thrown)

### `loadPreset`
- **Arguments:** `{ "presetId": String }`
- **Return:** `bool` success
- **Errors:** `PRESET_NOT_FOUND`

### `requestBatteryOptimizationExemption`
- **Arguments:** none
- **Return:** `bool` granted

## Methods (Kotlin → Dart, via EventChannel or callback)

### `onServiceStateChanged`
- **Payload:** `{ "state": "INIT" | "ACTIVE" | "STOPPED" }`

### `onAudioFocusChanged`
- **Payload:** `{ "focusState": "GAINED" | "LOST_TRANSIENT" | "LOST" }`

### `onError`
- **Payload:** `{ "code": String, "message": String }`

## Error Code Reference

| Code | Meaning |
|---|---|
| `SERVICE_START_FAILED` | Foreground service could not start |
| `PERMISSION_DENIED` | Required permission not granted |
| `AUDIO_INIT_FAILED` | `AudioRecord`/`AudioTrack` initialization failed |
| `FOCUS_DENIED` | Audio focus request denied |
| `PRESET_NOT_FOUND` | Requested preset ID does not exist |
| `SERVICE_NOT_RUNNING` | Stop requested but service wasn't active |
