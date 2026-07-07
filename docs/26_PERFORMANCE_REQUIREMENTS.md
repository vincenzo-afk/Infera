# 26 — Performance Requirements

## CPU
- Sustained audio-thread CPU usage should remain low enough to avoid triggering thermal throttling during extended (30+ minute) sessions on mid-range hardware
- No stage in the DSP chain should individually dominate CPU cost disproportionately (see relative cost table in `11_DSP_ENGINE.md`)

## RAM
- All audio buffers pre-allocated at session start; no growth in memory usage over the duration of a session
- Preset data (JSON) kept small (well under 10KB per preset)

## Battery
- Battery drain during active use should be comparable to a typical voice-call or streaming app, not disproportionately higher
- No polling loops running when Chaos Mode is inactive

## Startup
- Time from tapping the toggle to audible processed output: target under 500ms (excluding any first-run permission dialogs)

## Frame Time (UI)
- UI thread must maintain smooth rendering (target 60fps) even while the audio thread is under load — no jank from parameter slider updates

## Audio Glitches
- Zero audible dropouts/underruns under normal operating conditions on supported devices
- Any transient glitch (e.g., due to a brief system hiccup) should be inaudible or minimal, not a full-second dropout
