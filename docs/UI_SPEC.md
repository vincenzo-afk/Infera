# ChaosVoice — UI Specification

## 1. Screens

### Home Screen (`home_screen.dart`)
- **Big toggle button**: Chaos Mode ON/OFF, with clear visual state change (color + icon + label)
- **Live status indicator**: shows "ACTIVE — your voice is being processed" whenever Chaos Mode is on, persistent and unmissable
- **Preset picker**: horizontal scrollable cards — Demon, Robot, Crushed Radio, Glitch, + user's saved custom presets
- **Quick volume boost slider**: 100%–500%, with a warning label above ~300% ("Heavy distortion territory")
- **Settings gear icon**: opens advanced effect tuning

### Advanced Effects Screen (`effect_sliders_widget.dart`)
- One slider/control per DSP stage:
  - Resonant EQ (frequency + Q)
  - Noise injection amount
  - Saturation stage 1 & 2 gain
  - Feedback delay time + feedback %
  - Ring modulation frequency
  - Bit crusher depth
  - Sample rate reduction
  - Pitch drop/wobble amount
  - Vocoder/robotic mix
  - Transient burst frequency
  - Hard clip threshold
- "Reset to preset defaults" button
- "Save as custom preset" button with name input

### Volume Boost Screen (`volume_boost_widget.dart`)
- Large slider, 100%–500%
- Real-time small waveform/level meter showing clipping when boost is aggressive
- Toggle for limiter on/off (default: on)

## 2. Interaction Flow

1. App launch → permission sequence (see `PERMISSIONS.md`) if first run
2. Home screen loads with Chaos Mode OFF by default
3. User picks a preset (or leaves default)
4. User taps the toggle → persistent notification appears → processing begins
5. User can adjust sliders in real time while active — changes should apply within one buffer cycle, no restart required
6. User taps toggle again → processing stops, notification clears

## 3. Visual/Tone Guidance

- Dark theme, red/black color scheme fitting the "demonic/chaos" branding
- Iconography: flame, skull, or glitch-styled icons for presets
- Avoid any UI pattern that could hide or obscure the fact that Chaos Mode is active — the active-state indicator should always be visible when processing is running, by design

## 4. Accessibility Notes

- All sliders must have text-readable value labels (not just visual position)
- Toggle button state must be conveyed via more than color alone (icon + text)
