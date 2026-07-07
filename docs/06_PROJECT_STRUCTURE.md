# 06 — Project Structure

```
chaosvoice/
│
├── android/
│   ├── app/
│   │   └── src/main/
│   │       ├── kotlin/com/chaosvoice/app/
│   │       │   ├── MainActivity.kt
│   │       │   ├── ChaosProjectionService.kt
│   │       │   ├── ChaosVpnService.kt
│   │       │   ├── ChaosDSP.kt
│   │       │   ├── BootReceiver.kt
│   │       │   └── AudioFocusManager.kt
│   │       └── AndroidManifest.xml
│   ├── build.gradle
│   └── settings.gradle
│
├── lib/
│   ├── main.dart
│   ├── native_audio_bridge.dart
│   ├── chaos_controller.dart
│   ├── effect_engine.dart
│   ├── screens/
│   │   └── home_screen.dart
│   └── widgets/
│       ├── effect_sliders_widget.dart
│       └── volume_boost_widget.dart
│
├── ios/
│   └── Runner/
│       └── AudioEngineManager.swift
│
├── assets/
│   ├── icons/
│   └── presets/
│
├── docs/
│   ├── 00_PROJECT_OVERVIEW.md ... 40_GLOSSARY.md
│   ├── diagrams/
│   ├── mockups/
│   ├── assets/
│   └── decisions/
│
└── pubspec.yaml
```

## Folder Explanations

- **`android/`** — native Android project; contains all Kotlin services and the manifest defining permissions and foreground service declarations
- **`lib/`** — all Flutter/Dart application code: UI, state management, and the MethodChannel bridge to native code
- **`ios/`** — Swift audio engine for the secondary iOS platform
- **`assets/`** — bundled static assets (icons, default preset JSON files)
- **`docs/`** — this complete documentation set, including diagrams, mockups, and architecture decision records
