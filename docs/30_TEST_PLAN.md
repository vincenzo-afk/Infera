# 30 — Test Plan

## Testing Strategy Overview
A layered approach covering unit tests for pure DSP math, integration tests for the native audio pipeline and MethodChannel bridge, manual testing for real-device audio quality and call compatibility, stress testing for stability, and regression testing before each release.

## Unit Testing
- Target: `ChaosDSP.kt` individual stage functions (given known input, verify expected output within floating-point tolerance)
- Target: Dart `ChaosController` state transition logic
- Target: preset JSON serialization/deserialization

## Integration Testing
- Target: MethodChannel round-trip for every method in `16_METHOD_CHANNEL_PROTOCOL.md`
- Target: full pipeline start→process→stop cycle on an emulator or device, verifying no crashes and correct state reporting

## Manual Testing
- Target: subjective audio quality per preset, on multiple physical devices
- Target: real call tests across the VoIP compatibility matrix in `29_COMPATIBILITY.md`
- Target: permission flows on fresh installs (all grant/deny paths)

## Stress Testing
- Target: 60+ minute continuous active session, monitoring for memory growth, thermal throttling, or audio degradation over time
- Target: rapid toggle on/off cycling (20+ times in quick succession) to check for resource leaks

## Regression Testing
- Before each release, re-run the full manual test checklist from `32_BUILD_GUIDE.md` and confirm all previously-fixed issues in `KNOWN_ISSUES.md` remain fixed

## Test Environments
- Minimum two physical Android devices spanning different OEMs (per `29_COMPATIBILITY.md`)
- At least one Android 10 device and one Android 14 device to cover both ends of the supported range
