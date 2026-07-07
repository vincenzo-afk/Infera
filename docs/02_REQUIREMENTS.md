# 02 — Requirements

## Functional Requirements

| ID | Requirement |
|---|---|
| FR-1 | User can toggle voice processing on/off with a single control |
| FR-2 | User can select from preset voice effects (Demon, Robot, Crushed Radio, Glitch) |
| FR-3 | User can manually adjust individual effect parameters |
| FR-4 | User can adjust an overall output volume boost (up to 500% of source gain) |
| FR-5 | Processed audio plays back through the device loudspeaker in real time (<150ms target) |
| FR-6 | App runs as a foreground service with a persistent notification while active |
| FR-7 | App requests all permissions in the correct sequence |
| FR-8 | App handles audio focus changes gracefully (e.g., incoming call) |
| FR-9 | User can save/reuse custom effect presets |
| FR-10 | App works without root on stock Android 10+ |
| FR-11 | User can preview an effect before activating live processing |
| FR-12 | App shows a persistent, unmissable "active" indicator whenever processing is running |
| FR-13 | User can reset any preset to its default parameters |

## Non-Functional Requirements

### Performance
- CPU usage under typical load should stay within a range sustainable for continuous real-time use without overheating
- No dropped audio buffers under normal operating conditions

### Latency
- End-to-end latency (mic in → speaker out) target: under 150ms
- DSP processing latency budget: under 40ms per buffer cycle

### Battery
- App should not cause disproportionate battery drain relative to a typical voice/streaming app
- Wake locks held only while actively processing

### Memory
- No unbounded buffer growth; all audio buffers pre-allocated and reused
- No memory leaks across repeated start/stop cycles

### Reliability
- Foreground service should recover automatically (`START_STICKY`) if killed by the OS
- Graceful degradation if a permission is revoked mid-session (stop processing, notify user)

### Compatibility
- Android 10 (API 29) through Android 14+ (API 34)
- Should function on major OEM skins (Samsung One UI, Xiaomi MIUI/HyperOS, OnePlus OxygenOS) accounting for their background-process policies
