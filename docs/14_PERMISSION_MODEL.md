# 14 — Permission Model

## Required Permissions

| Permission | Why Needed | Runtime Request | Fallback on Denial |
|---|---|---|---|
| `RECORD_AUDIO` | Core mic capture | Yes, at first Chaos Mode activation | Disable Chaos Mode, show explanation, offer re-request |
| `MODIFY_AUDIO_SETTINGS` | Adjust output routing/volume | Granted automatically (normal permission) | N/A |
| `FOREGROUND_SERVICE` | Required to keep processing alive while backgrounded | Manifest-declared | N/A |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14+ requirement for mic-using foreground services | Manifest-declared | Service cannot start on Android 14+ without it; block feature with a clear message |
| `BIND_VPN_SERVICE` | Only if using the optional survival service | System-managed VPN consent dialog | Skip survival service, rely on standard foreground service persistence |
| `WAKE_LOCK` | Prevent CPU sleep during active session | Manifest-declared (normal permission) | N/A |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Reduce chance of OS killing the service | Runtime request via system dialog | Session may be less stable in background; document as a known limitation |
| `POST_NOTIFICATIONS` | Required Android 13+ for the foreground service notification | Runtime request | Foreground service cannot legally start; disable feature with explanation |

## Correct Request Sequence
1. Request `POST_NOTIFICATIONS` (Android 13+)
2. Start `ChaosProjectionService` in `INIT` state (foreground-only, no processing)
3. Request `RECORD_AUDIO` if not already granted
4. Request battery optimization exemption
5. Transition service to `ACTIVE` state, begin processing

## Failure Handling
- Every permission denial must produce a clear, specific in-app message — never a silent failure
- The user must always be able to retry the permission request from within the app (e.g., a "Grant Permission" button linking to system settings if permanently denied)
