# 19 — UI Components

## `ChaosToggleButton`
- **Purpose:** Primary control to start/stop Chaos Mode
- **Inputs:** current `isActive` state from `ChaosController`
- **Outputs:** calls `toggleChaosMode()` on tap
- **States:** OFF, Loading (Initializing), ON, Error

## `PresetCard`
- **Purpose:** Represents a single preset (built-in or custom) in the horizontal picker
- **Inputs:** preset name, icon, selected state
- **Outputs:** calls `selectPreset(preset)` on tap
- **States:** Selected, Unselected

## `ActiveStateBanner`
- **Purpose:** Persistent, unmissable indicator shown whenever processing is running
- **Inputs:** current app state (`Running`, `Paused`, etc.)
- **Outputs:** none (display only)
- **States:** Running (pulsing), Paused (static, dimmed)

## `EffectSlider`
- **Purpose:** Single parameter control on the Advanced Effects screen
- **Inputs:** stage name, current value, min/max range
- **Outputs:** calls `updateParameter(stage, value)` on change
- **States:** Default, Dragging, Disabled (when Chaos Mode is off, if applicable)

## `BoostSlider`
- **Purpose:** Volume boost control (100%–500%)
- **Inputs:** current boost value
- **Outputs:** calls `setBoost(value)` on change
- **States:** Normal (<300%), Warning (≥300%)

## `LevelMeter`
- **Purpose:** Real-time visual feedback of output level/clipping
- **Inputs:** live amplitude data stream from the audio thread
- **Outputs:** none (display only)
- **States:** Normal, Clipping (visual flash)

## `PresetSaveDialog`
- **Purpose:** Capture a name and confirm saving current settings as a custom preset
- **Inputs:** current parameter set
- **Outputs:** calls `savePreset(preset)` on confirm
- **States:** Editing, Saving, Error (e.g., duplicate name)
