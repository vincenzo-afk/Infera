# 09 — Method Specification

## `ChaosDSP.process(buffer: ShortArray): ShortArray`
- **Input:** Raw or partially-processed PCM 16-bit buffer
- **Output:** Fully processed PCM 16-bit buffer of the same length
- **Exceptions:** None thrown; invalid parameter values are clamped internally rather than raising
- **Thread:** Audio processing thread (`THREAD_PRIORITY_URGENT_AUDIO`)
- **Complexity:** O(n) in buffer length per stage; 14 stages applied sequentially
- **Purpose:** Core transformation entry point for one buffer cycle

## `ChaosDSP.setParameter(stage: Stage, value: Float)`
- **Input:** Stage enum + normalized parameter value
- **Output:** None (mutates internal state)
- **Exceptions:** None; out-of-range values clamped
- **Thread:** Can be called from UI thread; must use thread-safe write (e.g., atomic reference or lock-free swap) since read happens on audio thread
- **Complexity:** O(1)
- **Purpose:** Allow real-time parameter tuning without restarting the pipeline

## `ChaosProjectionService.onStartCommand(intent, flags, startId): Int`
- **Input:** Android service start intent, containing target state (INIT or ACTIVE)
- **Output:** `START_STICKY`
- **Exceptions:** Should catch and log audio initialization failures, fall back to stopped state
- **Thread:** Main/service thread; spawns dedicated audio thread for the processing loop
- **Complexity:** O(1) setup cost
- **Purpose:** Entry point for starting/transitioning the foreground service

## `AudioFocusManager.requestFocus(): Boolean`
- **Input:** None
- **Output:** `true` if focus granted, `false` otherwise
- **Exceptions:** None
- **Thread:** Main thread
- **Complexity:** O(1)
- **Purpose:** Acquire audio focus before starting playback

## `native_audio_bridge.startChaosMode(preset, boostLevel): Future<bool>` (Dart)
- **Input:** Preset identifier, boost level (100–500)
- **Output:** Future resolving true/false for success
- **Exceptions:** Throws `PlatformException` on native-side failure, caught and surfaced to UI as an error state
- **Thread:** Dart UI isolate; native call dispatched via MethodChannel to native thread
- **Complexity:** O(1) dispatch cost
- **Purpose:** UI-facing entry point to begin processing

## `ChaosController.toggleChaosMode()` (Dart)
- **Input:** None (reads current state)
- **Output:** Updates `isActive`, triggers UI rebuild
- **Exceptions:** Surfaces permission/native errors as a UI-visible error state
- **Thread:** Dart UI isolate
- **Complexity:** O(1)
- **Purpose:** Primary toggle handler bound to the home screen button

## `savePreset(preset: Preset): Future<void>` (Dart)
- **Input:** Preset object (name + parameter map)
- **Output:** Writes to local storage
- **Exceptions:** Storage write failure surfaced as UI error, in-session state preserved
- **Thread:** Dart UI isolate (async I/O)
- **Complexity:** O(1)
- **Purpose:** Persist a user-tuned preset
