# 13 — Threading Model

## Thread Map

| Thread | Responsibility |
|---|---|
| Dart UI Isolate | UI rendering, state management, MethodChannel calls out |
| Android Main/UI Thread | Activity lifecycle, permission dialogs, receiving MethodChannel calls |
| Audio Processing Thread | `AudioRecord` read → `ChaosDSP.process()` → `AudioTrack` write loop; runs at `THREAD_PRIORITY_URGENT_AUDIO` |
| Service/Binder Thread | Foreground service lifecycle callbacks (`onStartCommand`, etc.) |
| Background Worker (optional) | Preset save/load I/O, non-time-critical tasks |

## Synchronization Rules
- DSP parameter updates (from UI thread) must be written using a thread-safe mechanism (e.g., `AtomicReference` per parameter, or a lock-free double-buffer swap) since the audio thread reads them on every buffer cycle
- No blocking calls (I/O, locks with contention) are permitted inside the audio processing loop
- MethodChannel calls must always be dispatched back to the platform's main thread per Flutter's threading requirements; any heavy native work triggered by a MethodChannel call must be offloaded to a background/audio thread rather than blocking the channel invocation

## MethodChannel Threading
- Flutter's `MethodChannel` invokes handlers on the platform main thread by default
- Long-running or real-time-sensitive native operations (starting the audio loop) must hand off to the dedicated audio thread immediately rather than executing inline

## Audio Thread Priority
- Set via `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)` on the dedicated capture/process/playback thread to minimize scheduling-related glitches
