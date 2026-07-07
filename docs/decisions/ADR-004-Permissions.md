# ADR-004 — Permission Request Sequencing

## Status
Accepted

## Context
Android 13 and 14 introduced stricter requirements around notification permissions and foreground-service-start timing relative to sensitive permission dialogs (e.g., microphone). Requesting permissions in the wrong order causes dialogs to be skipped, denied unexpectedly, or the foreground service to fail to start.

## Decision
Permissions are requested in the fixed sequence documented in `14_PERMISSION_MODEL.md`: notifications first, then the foreground service is started in an `INIT` (non-processing) state, then microphone permission, then battery optimization exemption, and only then does the service transition to `ACTIVE` processing state.

## Consequences
- Adds a small amount of complexity to the startup flow (multiple sequenced steps rather than a single permission request)
- Ensures reliable behavior across Android 10 through 14+, and provides a stable pattern to extend if future Android versions add further sequencing requirements
