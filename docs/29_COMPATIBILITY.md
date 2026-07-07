# 29 — Compatibility

## Android Version Support
- **Minimum:** Android 10 (API 29) — required for `AudioPlaybackCaptureConfiguration`-adjacent APIs and modern audio focus handling
- **Target:** Android 14 (API 34)
- Behavior differences to account for: Android 13+ requires `POST_NOTIFICATIONS` runtime permission; Android 14 requires `FOREGROUND_SERVICE_MICROPHONE` and stricter foreground-service-start timing rules

## Manufacturer/OEM Considerations
| OEM | Known Consideration |
|---|---|
| Samsung (One UI) | "Put unused apps to sleep" setting can kill background service; document opt-out steps |
| Xiaomi (MIUI/HyperOS) | Requires manual "Autostart" permission for reliable background survival |
| OnePlus (OxygenOS) | Aggressive battery optimization defaults; battery exemption request is especially important |
| Stock Android (Pixel) | Generally most predictable/compliant behavior |

## Chipset Considerations
- Low-latency audio performance (`PERFORMANCE_MODE_LOW_LATENCY`) support varies by chipset/driver; app should gracefully fall back to standard performance mode if low-latency mode is unavailable, with a correspondingly adjusted latency expectation

## Known Issues
- Acoustic loopback quality (how well the loudspeaker output couples into the mic) varies significantly by physical device design; some phones may require higher boost levels than others for the effect to be clearly audible on a call

## VoIP App Compatibility Matrix

| App | Compatibility (Acoustic Loopback Method) | Notes |
|---|---|---|
| WhatsApp | Compatible | Standard mic/speaker usage |
| Discord | Compatible | Standard mic/speaker usage |
| Telegram | Compatible | Standard mic/speaker usage |
| Game voice chat (e.g., Free Fire) | Compatible | May require higher boost due to in-game audio ducking |
| Regular GSM/cellular calls | Not compatible | Hardware-level call audio path does not couple with app-level loudspeaker output in the same way; out of scope for this app |
