# ADR-003 — State Management Approach

## Status
Accepted

## Context
The app needs a single, consistent source of truth for UI-facing state (active/inactive, current preset, boost level) that stays in sync with native-side service state reported asynchronously over the MethodChannel/EventChannel boundary.

## Decision
A single `ChaosController` class (Dart, using `ChangeNotifier`) acts as the sole owner of app-level state as defined in `17_STATE_MANAGEMENT.md`. It listens for native state-change events and exposes a simple, observable state surface to the UI layer. No additional state management framework (e.g., Bloc, Riverpod) is mandated by this architecture, though one could be substituted without changing the underlying state model.

## Consequences
- Keeps the state model simple and easy to reason about for a relatively small app surface
- If the app grows significantly in complexity, migrating to a more structured state management framework remains straightforward since the state shape itself is already clearly defined
