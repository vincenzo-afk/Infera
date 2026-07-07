# 17 — State Management

## App States

| State | Description |
|---|---|
| `Idle` | App launched, Chaos Mode has never been activated this session |
| `Initializing` | Permissions/service startup in progress |
| `Running` | Audio pipeline active, processed output playing |
| `Paused` | Temporarily suspended due to audio focus loss (transient) |
| `Stopped` | User deactivated Chaos Mode, or session ended cleanly |
| `Failure` | An unrecoverable error occurred (permission revoked mid-session, audio init failure, etc.) |

## State Transitions

```
Idle
  │ user taps toggle
  ▼
Initializing
  │ permissions granted + service started
  ▼
Running ──────► Paused ──────► Running
  │ (focus lost)      (focus regained)
  │
  │ user taps toggle / error
  ▼
Stopped
  │
  ▼ (on unrecoverable error)
Failure ──► Stopped (after user acknowledges)
```

## State Ownership
- `ChaosController` (Dart) is the single source of truth for UI-facing state
- Native-side `ChaosProjectionService` state (`INIT`/`ACTIVE`/`STOPPED`) is reported up to `ChaosController` via the MethodChannel/EventChannel and mapped to the app-level states above

## UI Behavior Per State
- `Idle` / `Stopped`: toggle shows OFF, no notification
- `Initializing`: toggle shows a loading indicator, disabled to prevent double-taps
- `Running`: toggle shows ON, active-state indicator visible, sliders enabled
- `Paused`: active-state indicator shows "Paused" (e.g., due to an incoming call), sliders disabled
- `Failure`: clear error message with a retry action, toggle reset to OFF
