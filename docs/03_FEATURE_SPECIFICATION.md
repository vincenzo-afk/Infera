# 03 — Feature Specification

## Feature: Chaos Mode Toggle
- **Purpose:** Start/stop real-time voice processing
- **Inputs:** User tap on toggle button
- **Outputs:** Foreground service starts/stops; notification appears/disappears; audio pipeline begins/ends
- **Dependencies:** RECORD_AUDIO permission, notification permission (Android 13+)
- **Failure Cases:** Permission denied → show explanation, do not start; audio focus unavailable → queue/retry or notify user
- **Success Criteria:** Toggling ON begins audible processed output within 150ms of speaking; toggling OFF immediately stops processing and clears notification

## Feature: Preset Selection
- **Purpose:** Quickly apply a curated combination of DSP parameters
- **Inputs:** User selects Demon / Robot / Crushed Radio / Glitch / custom preset
- **Outputs:** DSP engine parameters updated in real time
- **Dependencies:** DSP engine must support live parameter updates without pipeline restart
- **Failure Cases:** Corrupted custom preset data → fall back to nearest built-in default
- **Success Criteria:** Switching preset takes effect within one buffer cycle, audibly distinct per preset

## Feature: Manual Effect Tuning
- **Purpose:** Fine-grained control over each DSP stage
- **Inputs:** Slider/control adjustments on the Advanced Effects screen
- **Outputs:** Real-time parameter updates to the active DSP chain
- **Dependencies:** `effect_sliders_widget.dart`, `ChaosDSP.kt` parameter API
- **Failure Cases:** Out-of-range values → clamp to valid range
- **Success Criteria:** Every slider audibly changes output within one buffer cycle

## Feature: Volume Boost
- **Purpose:** Increase output loudness up to 500% of source gain
- **Inputs:** Boost slider (100%–500%)
- **Outputs:** Gain-multiplied, limited output signal
- **Dependencies:** Limiter stage must be active to prevent destructive clipping
- **Failure Cases:** Boost without limiter → signal collapse/distortion beyond intended character
- **Success Criteria:** Output loudness scales with slider; audio remains audible (not fully clipped to silence) even at 500%

## Feature: Custom Preset Save/Load
- **Purpose:** Persist user-tuned parameter sets
- **Inputs:** "Save as preset" action with a name
- **Outputs:** Preset stored locally (see `23_STORAGE_MODEL.md`)
- **Dependencies:** Local storage (SharedPreferences or file-based)
- **Failure Cases:** Storage write failure → show error, do not lose in-session settings
- **Success Criteria:** Saved presets persist across app restarts and can be reloaded exactly

## Feature: Foreground Service & Notification
- **Purpose:** Keep audio processing alive while backgrounded, per Android policy
- **Inputs:** Chaos Mode activation
- **Outputs:** Persistent notification, active foreground service
- **Dependencies:** `FOREGROUND_SERVICE_MICROPHONE` permission
- **Failure Cases:** Service killed by OS → auto-restart via `START_STICKY`
- **Success Criteria:** Session survives at least 10 minutes backgrounded under normal conditions

## Feature: Audio Focus Handling
- **Purpose:** Behave correctly when another app (e.g., an incoming call) requests audio focus
- **Inputs:** System `AudioFocusRequest` callbacks
- **Outputs:** Pause/resume or duck processing appropriately
- **Dependencies:** `AudioFocusManager.kt`
- **Failure Cases:** Focus not released properly → app may block other audio; must be tested and fixed
- **Success Criteria:** No audio conflicts or crashes when focus changes
