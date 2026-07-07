# 25 — Error Handling

## Error Categories & Handling

| Error | Detection Point | Recovery/Fallback | User-Facing Message |
|---|---|---|---|
| `RECORD_AUDIO` denied | Permission request result | Disable Chaos Mode, offer retry | "Microphone access is needed to use ChaosVoice. Tap to grant." |
| Notification permission denied (Android 13+) | Permission request result | Cannot start foreground service; disable feature | "Notification permission is required for ChaosVoice to run in the background." |
| `AudioRecord` init failure | Service startup | Stop service cleanly, surface error state | "Couldn't access the microphone. Try restarting the app." |
| `AudioTrack` init failure | Service startup | Stop service cleanly, surface error state | "Couldn't start audio playback. Please try again." |
| Audio focus denied | Focus request | Do not start processing; inform user | "Another app is using audio right now." |
| Audio focus lost (transient) | Runtime focus callback | Pause processing, auto-resume on regain | Banner shows "Paused" state, no blocking dialog |
| Audio focus lost (permanent) | Runtime focus callback | Stop processing cleanly | Banner returns to OFF state |
| Preset load failure (corrupted data) | Preset load call | Fall back to nearest built-in default | "Couldn't load that preset — reverted to default." |
| Preset save failure (storage error) | Save call | Preserve in-session settings, show error | "Couldn't save preset. Please try again." |
| Foreground service killed unexpectedly | `START_STICKY` restart | Return to `Stopped` state, do not silently resume audio | (Silent — user must manually reactivate, per transparency principle) |
| Buffer underrun/overrun | Audio thread | Skip/pad gracefully, log at DEBUG level | No user-facing message unless persistent |

## General Principles
- No silent failures for anything that affects whether the user's voice is currently being processed
- Every user-facing error message should include a clear next action (retry, grant permission, restart)
- Errors during the real-time audio loop must never crash the entire app — always fail down to a clean `Stopped` state
