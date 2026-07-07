# 18 — UI Specification

## Layout Principles
- Single-column, thumb-reachable layout on the home screen — primary toggle within easy reach
- Generous spacing (minimum 16dp between major sections) to avoid accidental taps on a dense effects screen
- Active-state indicator always visible above the fold whenever Chaos Mode is running

## Typography
- Display font for the app name/branding: bold, condensed style fitting the "chaos" theme
- Body font: standard system font for readability of labels/values
- Slider value labels: monospace or tabular figures for consistent width as numbers change

## Colors
- Base theme: dark background (near-black), red/orange accent for active states, muted gray for inactive/disabled controls
- Warning states (e.g., boost above 300%): amber/red highlight
- Active-state indicator: high-contrast red banner, impossible to miss

## Buttons
- Primary toggle: large, circular or pill-shaped, fills a significant portion of the screen width, state conveyed via color + icon + text label (not color alone)
- Secondary buttons (save preset, reset): standard outlined style, secondary priority visually

## Sliders
- Continuous sliders for all DSP parameters and volume boost
- Each slider paired with a numeric value label
- Boost slider includes a visually marked "danger zone" starting around 300%

## Animations
- Toggle activation: brief pulse/glow animation to reinforce state change
- Active-state indicator: subtle persistent pulse while running (not distracting, but clearly alive)
- Screen transitions: standard platform transitions (no custom heavy animation needed)

## Reference
See `mockups/home.png`, `mockups/settings.png`, `mockups/waveform.png`, and `mockups/permissions.png` for visual reference, and `diagrams/ui_flow.drawio` for the full navigation diagram.
