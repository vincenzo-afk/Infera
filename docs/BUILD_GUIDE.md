# ChaosVoice — Build Guide

## 1. Prerequisites
- Flutter SDK (stable channel)
- Android Studio with Android SDK 34 installed
- Kotlin plugin enabled
- A physical Android device recommended for testing (audio loopback behavior doesn't simulate well on emulators)

## 2. SDK Versions
- **Target SDK:** 34 (Android 14)
- **Min SDK:** 26 (Android 8.0)
- **Compile SDK:** 34

## 3. Project Structure

```
/android          → native Kotlin services, manifest
/lib              → Flutter/Dart app code
/ios              → Swift audio engine (secondary platform)
/docs             → this documentation set
```

## 4. Build Commands

```bash
# Get dependencies
flutter pub get

# Run in debug mode on connected Android device
flutter run -d android

# Build release APK
flutter build apk --release

# Build release App Bundle (for Play Store)
flutter build appbundle --release
```

## 5. Testing Checklist

- [ ] App requests permissions in the correct order (see `PERMISSIONS.md`)
- [ ] Toggling Chaos Mode ON starts the foreground notification immediately
- [ ] Processed audio is audible through the loudspeaker within ~150ms of speaking
- [ ] Adjusting sliders while active updates the sound without needing a restart
- [ ] Volume boost above 300% shows the UI warning and audibly increases distortion, not silence
- [ ] Service survives at least 10 minutes backgrounded
- [ ] Service restarts automatically if force-killed by the OS
- [ ] No double-processing artifacts (confirms only one DSP path is live on Android)
- [ ] All four presets sound distinctly different from each other

## 6. Known Device-Specific Considerations
- Some OEMs (Xiaomi, Samsung, OnePlus) apply aggressive background app killing — confirm battery optimization exemption is granted during testing
- Speaker/mic acoustic coupling varies by device; test on multiple physical devices where possible
