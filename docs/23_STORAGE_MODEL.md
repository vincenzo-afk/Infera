# 23 — Storage Model

## SharedPreferences (Dart: `shared_preferences` package)
Used for lightweight app settings:
- Last active preset ID
- Last boost level
- Limiter on/off
- Whether onboarding/permissions flow has been completed

## Files (JSON)
Custom presets stored as individual JSON files (or a single JSON array file) in app-local storage:
```json
{
  "id": "custom-001",
  "name": "My Demon Mix",
  "parameters": {
    "eqFrequency": 2600,
    "eqQ": 5.0,
    "noiseMix": 0.35,
    "saturation1Gain": 12.0,
    "delayTimeMs": 25,
    "delayFeedback": 0.65,
    "saturation2Gain": 4.0,
    "ringModHz": 800,
    "bitCrushDepth": 4,
    "sampleRateTargetHz": 8000,
    "pitchWobbleRateHz": 4.0,
    "pitchWobbleDepth": 0.15,
    "boostPercent": 200
  }
}
```

## Cache
No audio data is cached to disk during normal operation — all processing is streamed in-memory buffer-to-buffer. Cache directory reserved only for any future temporary export/recording feature.

## Logs
Debug logs written to the standard platform log (`Logcat` on Android), not persisted to disk by default. See `24_LOGGING_AND_DEBUGGING.md`.

## Future Database
If preset libraries grow large or sharing features are added, a local SQLite database (via `sqflite` or similar) may replace flat JSON files — this is a documented future consideration, not part of the current build.
