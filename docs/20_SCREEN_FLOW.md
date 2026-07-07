# 20 — Screen Flow

```
Splash Screen
      │
      ▼
Permissions Screen (first run only)
      │  grant mic + notifications
      ▼
Home Screen
      │  select preset / adjust boost / tap toggle
      ▼
Running State (overlay/banner on Home Screen)
      │  tap settings gear
      ▼
Advanced Effects Screen
      │  back
      ▼
Home Screen
```

## Screen Descriptions

### Splash Screen
Brief branding display while the app initializes local storage and reads saved presets/settings.

### Permissions Screen
Shown only if required permissions are not yet granted. Explains why microphone and notification access are needed before requesting them, in the sequence defined in `14_PERMISSION_MODEL.md`.

### Home Screen
Primary interaction surface: toggle, preset picker, quick boost slider, active-state banner when running.

### Advanced Effects Screen
Full per-stage parameter sliders, preset save/reset controls.

### Volume Boost Screen (may be a modal/sheet from Home rather than a separate full screen)
Dedicated boost slider with level meter and limiter toggle.

## Back Navigation
- From any sub-screen, back navigation returns to Home Screen without interrupting an active Chaos Mode session
- Chaos Mode continues running in the background regardless of which screen is currently displayed, governed entirely by the foreground service state, not the UI navigation stack
