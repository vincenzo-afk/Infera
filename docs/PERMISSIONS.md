# ChaosVoice — Permissions

## 1. Required Permissions (AndroidManifest.xml)

```xml
<!-- Audio capture and control -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- Foreground service (required for continuous real-time audio processing) -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

<!-- Optional: background survival service -->
<uses-permission android:name="android.permission.BIND_VPN_SERVICE" />

<!-- Battery -->
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

<!-- Notifications (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

## 2. Why Each Permission Is Needed

| Permission | Reason |
|---|---|
| `RECORD_AUDIO` | Core requirement — capturing the user's mic input to process |
| `MODIFY_AUDIO_SETTINGS` | Adjusting output routing/volume for loudspeaker playback |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Required by Android to keep mic capture running while the app is not in the foreground |
| `BIND_VPN_SERVICE` | Only needed if using the optional fake-VPN survival technique to reduce background kill risk |
| `WAKE_LOCK` | Prevents CPU sleep from interrupting the audio thread during active sessions |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Reduces chance of Android's battery management killing the service mid-session |
| `POST_NOTIFICATIONS` | Required on Android 13+ to show the mandatory foreground-service notification |

## 3. Correct Request Order

1. `POST_NOTIFICATIONS` (Android 13+)
2. Start `ChaosProjectionService` in `INIT` state (foreground-only, no processing yet)
3. Request battery optimization exemption
4. Transition service to `ACTIVE` state and begin audio processing

This order matters because Android 14 requires an active foreground service before certain permission dialogs will behave correctly, and requesting battery exemption after the service is already running is more reliable than requesting it upfront.

## 4. Runtime Permission Handling

- If `RECORD_AUDIO` is denied, the app must disable Chaos Mode and show a clear explanation — never silently fail
- If notification permission is denied (Android 13+), the foreground service cannot legally start; show the user why the feature is unavailable
