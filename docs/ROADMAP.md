# ChaosVoice — Roadmap

## Phase 1 — Core Audio Pipeline (Priority)
- [ ] Fix output routing: ensure processed audio plays to device loudspeaker (not call-routed audio path)
- [ ] Implement `AudioFocusManager.kt` and wire up `AudioFocusRequest` handling
- [ ] Remove duplicate processing — confirm Kotlin (`ChaosDSP.kt`) is the only live path on Android; Dart (`effect_engine.dart`) used for iOS/preview only
- [ ] Pre-warm echo/reverb buffers to remove silent startup gap
- [ ] Re-tune reverb decay for a tighter, harsher "demonic" character

## Phase 2 — UI Build (Currently 0%)
- [ ] Home screen: toggle, active-state indicator, preset picker, quick boost slider
- [ ] Advanced effects screen with per-stage sliders
- [ ] Volume boost screen with limiter toggle and level meter
- [ ] Custom preset save/load

## Phase 3 — New Effects
- [ ] Vocoder/robotic layer
- [ ] Transient scream/screech burst stage
- [ ] Additional presets composed from existing stages

## Phase 4 — Volume Boost System
- [ ] Implement linear gain stage up to 500%
- [ ] Implement soft-knee limiter after boost stage
- [ ] Add UI warning threshold and live level meter

## Phase 5 — Stability & Testing
- [ ] Foreground service survives 10+ minute background sessions
- [ ] Service auto-restarts if killed (`START_STICKY` verified)
- [ ] Test across Android 10 through Android 14
- [ ] Test effect quality across several device speaker/mic hardware combos

## Phase 6 — iOS Port
- [ ] Port full effects chain to `AudioEngineManager.swift`
- [ ] Match Android UI/UX on iOS

## Phase 7 (Future) — Advanced/Optional
- [ ] Investigate ML-based voice conversion as an alternative to pure DSP (bigger scope, separate spec)
- [ ] Explore root-only system-wide processing as an opt-in advanced mode for rooted-device users, clearly separated from the default no-root experience
