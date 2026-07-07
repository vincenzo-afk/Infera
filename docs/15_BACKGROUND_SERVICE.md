# 15 — Background Service

## Foreground Service Overview
`ChaosProjectionService` runs as a foreground service whenever Chaos Mode is active, displaying a persistent, unmissable notification so the user always knows processing is running.

## Notification Requirements
- Must be shown before or immediately as processing begins (Android 14 requires the foreground service to already be running before certain permission dialogs)
- Must clearly state "ChaosVoice is active" or equivalent — no ambiguous or hidden notification text
- Tapping the notification returns the user to the app's home screen

## Lifecycle States
| State | Description |
|---|---|
| `INIT` | Foreground service started, notification shown, no audio processing yet (used while awaiting permission grants) |
| `ACTIVE` | Full capture → DSP → playback loop running |
| `STOPPED` | Service torn down, notification cleared |

## Restart Behavior
- `onStartCommand()` returns `START_STICKY` so Android attempts to restart the service if it's killed due to memory pressure
- On restart, the service should return to `STOPPED` state (not silently resume audio processing) and require the user to re-activate Chaos Mode, to preserve the transparency principle

## Battery Optimization
- App requests exemption via `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` once the service is already running
- Document OEM-specific extra steps (e.g., Xiaomi's "Autostart" toggle, Samsung's "unmonitored apps" list) in a user-facing help section, since these are outside standard Android APIs

## Recovery
- If the audio pipeline throws an unexpected error mid-session (e.g., device disconnect), the service should stop cleanly, clear the notification, and surface an error state to the UI rather than silently continuing in a broken state
