# 38 — Roadmap

## Current Version (v1.0 — In Development)
- Core audio pipeline (capture → DSP → loudspeaker playback)
- Four built-in presets: Demon, Robot, Crushed Radio, Glitch
- Volume boost up to 500% with limiter
- Basic home screen UI with toggle and preset picker

## Next Version (v1.1)
- Advanced Effects screen with full per-stage sliders
- Custom preset save/load
- Audio focus handling refinements
- Reverb/echo tuning fixes (pre-warm buffer, harsher decay) — see `KNOWN_ISSUES.md`

## Future Ideas (Unscheduled)
- iOS release via `AudioEngineManager.swift`
- Community preset sharing (would require a backend — currently out of architectural scope)
- Optional ML-based voice conversion as a separate, clearly-labeled advanced mode
- Root-only "advanced mode" for true system-wide processing on rooted devices, kept fully separate from the default no-root experience

## Milestones
| Milestone | Target |
|---|---|
| M1 — Core pipeline stable | Fix routing, add audio focus handling |
| M2 — UI complete | All screens per `18_UI_SPECIFICATION.md` implemented |
| M3 — v1.0 release | Full manual test pass per `31_TEST_CASES.md` |
| M4 — v1.1 release | Custom presets + advanced effects screen |
