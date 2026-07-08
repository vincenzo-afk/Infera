# ADR-004 — Permission Request Sequencing

## Status
Superseded (corrected — see amendment below)

## Context
Android 13 and 14 introduced stricter requirements around notification permissions and foreground-service-start timing relative to sensitive permission dialogs (e.g., microphone). Requesting permissions in the wrong order causes dialogs to be skipped, denied unexpectedly, or the foreground service to fail to start.

## Original Decision (INCORRECT for microphone-typed FGS)
Permissions were requested in the sequence: notifications first, then the foreground service
is started in an `INIT` (non-processing) state, then microphone permission, then battery
optimization exemption, and only then does the service transition to `ACTIVE` processing state.

## Amendment — Android 14 Microphone FGS Constraint

**Root cause discovered:** On API 34 (targetSdk), `startForeground()` on a service declared
with `foregroundServiceType="microphone"` throws `SecurityException` /
`MissingForegroundServiceTypeException` if `RECORD_AUDIO` is **not already granted** at the
call site. This exception is thrown inside `Service.onStartCommand()` \u2014 a call stack outside
`MainActivity`'s try-catch \u2014 killing the entire process uncaught. This produced the
"crashes to home screen" symptom on every START CHAOS tap on Android 14 devices.

The guidance "Android 14 requires an active foreground service before permission dialogs
behave correctly" is true for notification and other permission types, but explicitly **does
not apply** to microphone-typed foreground services. For those, the mic grant must precede
the service start.

## Corrected Decision
Permissions are requested in the following fixed sequence (see `14_PERMISSION_MODEL.md`):

1. `POST_NOTIFICATIONS` (Android 13+)
2. `RECORD_AUDIO` \u2014 **must precede service start on Android 14+**
3. Start `ChaosProjectionService` in `INIT` state (foreground-only, no processing)
4. Battery optimization exemption
5. Transition service to `ACTIVE` processing state

## Consequences
- The corrected sequence fixes the Android 14 crash without regressing Android 10\u201313 behavior
- `requestRecordAudioPermission` MethodChannel call now precedes `startForegroundServiceOnly`
  in both `chaos_controller.dart` and `MainActivity.kt`
- Documentation in `PERMISSIONS.md` and `14_PERMISSION_MODEL.md` updated to match
- Adds a small regression risk on pre-API 34 devices (sequence change): mitigated by the fact
  that requesting mic before or after service start has no observable behavioral difference
  below API 34
