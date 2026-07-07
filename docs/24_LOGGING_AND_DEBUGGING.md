# 24 — Logging and Debugging

## Log Format
```
[ChaosVoice][<TAG>][<LEVEL>] <message>
```
Example: `[ChaosVoice][DSP][DEBUG] Applied preset: Demon`

## Tags
- `SERVICE` — foreground service lifecycle events
- `DSP` — effect chain parameter changes, preset loads
- `AUDIO` — AudioRecord/AudioTrack init, buffer underrun/overrun events
- `PERMS` — permission request/grant/deny events
- `FOCUS` — audio focus state changes
- `UI` — significant UI state transitions (for debug builds only)

## Debug Levels
| Level | Use |
|---|---|
| VERBOSE | Per-buffer diagnostic info (debug builds only, disabled in release) |
| DEBUG | State transitions, parameter changes |
| INFO | Session start/stop, preset selection |
| WARN | Recoverable issues (e.g., transient focus loss) |
| ERROR | Unrecoverable issues (e.g., AudioTrack init failure) |

## Crash Logging
- Uncaught exceptions on the native side should be caught at the service boundary, logged with full stack trace, and result in a clean service stop rather than a hard crash where possible
- Flutter-side crash reporting can use standard Flutter error handling (`FlutterError.onError`) to log to the console in debug builds; no third-party crash reporting SDK is assumed by default in this spec

## Release vs Debug Behavior
- VERBOSE and per-buffer logs must be fully disabled in release builds to avoid performance impact on the real-time audio thread
- Only WARN/ERROR level logs should be retained in release builds, and only via standard system log, not written to persistent user-visible files unless a "debug mode" is explicitly enabled by the user
