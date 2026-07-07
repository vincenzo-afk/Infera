# 32 — Build Guide

## Prerequisites
- Flutter SDK (stable channel)
- Android Studio with Android SDK 34
- Kotlin plugin enabled
- Physical Android device recommended (acoustic loopback behavior does not simulate meaningfully on emulators)

## SDK Versions
- **Target SDK:** 34 (Android 14)
- **Min SDK:** 29 (Android 10)
- **Compile SDK:** 34

## Build Commands

```bash
# Fetch dependencies
flutter pub get

# Run in debug mode
flutter run -d android

# Build release APK
flutter build apk --release

# Build release App Bundle (Play Store)
flutter build appbundle --release
```

## Gradle Notes
- Ensure `minSdkVersion 29`, `targetSdkVersion 34`, `compileSdkVersion 34` are set in `android/app/build.gradle`
- Foreground service type declarations must be present in `AndroidManifest.xml` for the `ChaosProjectionService`

## Signing
- Standard Android app signing via a release keystore; keystore path and credentials should be supplied via local `key.properties` (not committed to version control)

## Release Checklist
- Run full manual test pass per `31_TEST_CASES.md`
- Confirm `KNOWN_ISSUES.md` items relevant to the release are resolved or explicitly deferred
- Verify version code/name bump per `35_GIT_WORKFLOW.md`

## CI (Optional/Future)
A CI pipeline (e.g., GitHub Actions) can run `flutter analyze`, unit tests, and a debug build on each push; real-device audio/call testing remains a manual step outside CI.
