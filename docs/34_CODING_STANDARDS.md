# 34 — Coding Standards

## Naming
- Kotlin: `PascalCase` for classes, `camelCase` for functions/variables, `UPPER_SNAKE_CASE` for constants
- Dart: `PascalCase` for classes/widgets, `camelCase` for functions/variables, `lowerCamelCase` for file-local constants, `SCREAMING_SNAKE_CASE` avoided in favor of `const` camelCase per Dart convention

## Formatting
- Kotlin: standard Android Kotlin style (4-space indentation), formatted via `ktlint` or Android Studio's default formatter
- Dart: formatted via `dart format` (standard Flutter tooling)

## Architecture
- Native audio pipeline logic stays in Kotlin; Flutter layer never attempts direct low-level audio API calls
- All cross-layer communication goes through the defined MethodChannel protocol (`16_METHOD_CHANNEL_PROTOCOL.md`) — no ad hoc side channels
- DSP stage implementations kept as small, independently testable functions/methods rather than one monolithic processing block

## Documentation
- Every public class and method should have a brief doc comment describing purpose, inputs, and outputs, consistent with `08_CLASS_SPECIFICATION.md` and `09_METHOD_SPECIFICATION.md`
- Non-obvious DSP math should be commented with the formula being implemented

## Comments
- Prefer clear naming over excessive comments, but always comment "why" for non-obvious architectural decisions (e.g., why loudspeaker routing is used instead of call-routed audio)

## Lint Rules
- Kotlin: standard Android lint, no suppressed warnings without a comment explaining why
- Dart: `flutter analyze` should pass with zero warnings before merging to main
