# 21 — Settings and Presets

## Built-In Presets

| Preset | Key Characteristics | Default Boost |
|---|---|---|
| Demon | Deep pitch drop, heavy saturation, tight harsh reverb | 200% |
| Robot | Strong ring mod + vocoder, no pitch wobble | 150% |
| Crushed Radio | Heavy bit crush + sample rate reduction + noise | 150% |
| Glitch | Frequent transient bursts, dropout, fast pitch wobble | 175% |

Full per-stage default values are defined in `11_DSP_ENGINE.md`.

## Custom Presets
- Users may save any tuned parameter combination as a named custom preset
- Stored locally as JSON (see `23_STORAGE_MODEL.md`)
- No limit enforced on number of custom presets beyond reasonable storage constraints

## Settings

| Setting | Default | Range/Options |
|---|---|---|
| Volume Boost | 150% | 100%–500% |
| Limiter | On | On/Off |
| Active preset | Demon | Any built-in or custom preset |
| Notification style | Standard | Standard only (no "silent" option, per transparency principle) |

## Limits
- Volume boost hard-capped at 500% in the UI and enforced server-side... (n/a, purely client-enforced since there is no server) — enforced in both the Dart UI layer and the native `ChaosDSP` gain stage as a safety bound
- Individual DSP parameters clamped to musically/technically sensible ranges defined per-stage in `11_DSP_ENGINE.md`
